package com.ohtoai.grandteleport.teleport;

import com.ohtoai.grandteleport.GrandTeleportPlugin;
import com.ohtoai.grandteleport.config.TransitionSettings;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Listens for PlayerCommandPreprocessEvent and handles coordinates-type commands.
 * Only intercepts commands where we can parse the destination from the command itself
 * (e.g., /tp x y z). All other teleport commands (TPA, warp, home, etc.) are handled
 * by the global PlayerTeleportEvent listener in ModTeleportListener.
 */
public class CommandInterceptor implements Listener {

    private final GrandTeleportPlugin plugin;
    private final Map<Pattern, String> commandPatterns = new ConcurrentHashMap<>();
    private final List<Pattern> filterPatterns = new ArrayList<>();
    private volatile String filterMode = "blacklist";

    public CommandInterceptor(GrandTeleportPlugin plugin) {
        this.plugin = plugin;
        loadPatterns();
    }

    private boolean isFallbackEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("fallback.enabled", false);
    }

    public void loadPatterns() {
        commandPatterns.clear();
        List<Map<?, ?>> commands = plugin.getConfigManager().getConfig().getMapList("commands");
        if (commands == null || commands.isEmpty()) {
            plugin.getLogger().warning("No command patterns configured!");
            return;
        }

        for (Map<?, ?> entry : commands) {
            String patternStr = String.valueOf(entry.get("pattern"));
            String type = String.valueOf(entry.get("type"));
            Object enabledObj = entry.get("enabled");
            boolean enabled = enabledObj == null || Boolean.parseBoolean(String.valueOf(enabledObj));

            if (!enabled) continue;
            if (!"coordinates".equals(type)) continue; // only coordinates type

            try {
                Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
                commandPatterns.put(pattern, type);
                plugin.getLogger().info("Loaded coordinates pattern: " + patternStr);
            } catch (PatternSyntaxException e) {
                plugin.getLogger().log(Level.WARNING, "Invalid regex: " + patternStr, e);
            }
        }

        plugin.getLogger().info("Loaded " + commandPatterns.size() + " coordinates command patterns.");

        // Load filter patterns
        filterPatterns.clear();
        filterMode = plugin.getConfigManager().getConfig().getString("filter_mode", "blacklist");
        if (!"none".equals(filterMode)) {
            List<String> filters = plugin.getConfigManager().getConfig().getStringList("filter_patterns");
            if (filters != null) {
                for (String patternStr : filters) {
                    try {
                        filterPatterns.add(Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE));
                        plugin.getLogger().info("Loaded filter pattern: " + patternStr);
                    } catch (PatternSyntaxException e) {
                        plugin.getLogger().log(Level.WARNING, "Invalid filter pattern: " + patternStr, e);
                    }
                }
            }
        }
        plugin.getLogger().info("Filter mode: " + filterMode + " (" + filterPatterns.size() + " patterns)");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage();

        // Check filter patterns
        if (!filterPatterns.isEmpty() && !"none".equals(filterMode)) {
            boolean matched = false;
            for (Pattern p : filterPatterns) {
                if (p.matcher(command).matches()) { matched = true; break; }
            }
            if ("blacklist".equals(filterMode) && matched) return;  // skip this command
            if ("whitelist".equals(filterMode) && !matched) return; // only allow matched commands
        }

        if (plugin.shouldBypassNextCommand(player)) return;
        if (player.hasPermission("grandteleport.bypass")) return;
        if (!player.hasPermission("grandteleport.use")) return;

        TransitionSettings settings = plugin.getTransitionSettings();
        if (!settings.enabled) return;
        if (!plugin.isEffectEnabled(player)) return;
        if (plugin.isInTransition(player)) return;

        String lowerCmd = command.toLowerCase().stripLeading();
        if (lowerCmd.startsWith("/grandtp") || lowerCmd.startsWith("/gtp")) return;

        for (Map.Entry<Pattern, String> entry : commandPatterns.entrySet()) {
            if (entry.getKey().matcher(command).matches()) {
                event.setCancelled(true);
                handleCoordinates(player, command, settings);
                return;
            }
        }
    }

    private void handleCoordinates(Player player, String command, TransitionSettings settings) {
        // No mod — check fallback
        if (!plugin.hasMod(player)) {
            if (isFallbackEnabled()) {
                Location src = player.getLocation();
                Location dst = DestinationParser.parse(command, player);
                if (dst != null && plugin.startTransition(player)) {
                    if (settings.freezeEnabled) plugin.freezePlayer(player);
                    new VanillaCameraAnimation(plugin).start(player, src, dst, settings);
                    return;
                }
            }
            plugin.setBypassNextCommand(player);
            plugin.getServer().dispatchCommand(player,
                    command.startsWith("/") ? command.substring(1) : command);
            return;
        }

        Location source = player.getLocation();
        Location target = DestinationParser.parse(command, player);

        if (target == null) {
            plugin.setBypassNextCommand(player);
            plugin.getServer().dispatchCommand(player,
                    command.startsWith("/") ? command.substring(1) : command);
            return;
        }

        if (!plugin.startTransition(player)) return;
        if (settings.freezeEnabled) plugin.freezePlayer(player);

        plugin.getLogger().info("Coordinates tp: " + player.getName() + " → " +
                (int)target.getX() + "," + (int)target.getY() + "," + (int)target.getZ());

        ModCameraBridge modBridge = new ModCameraBridge(plugin);
        modBridge.start(player, source, target, command, settings);
    }
}
