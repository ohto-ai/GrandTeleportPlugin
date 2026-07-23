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
    private final List<Pattern> skipPatterns = new ArrayList<>();

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

        // Load skip patterns
        skipPatterns.clear();
        List<String> skips = plugin.getConfigManager().getConfig().getStringList("skip_patterns");
        if (skips != null) {
            for (String patternStr : skips) {
                try {
                    skipPatterns.add(Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE));
                    plugin.getLogger().info("Loaded skip pattern: " + patternStr);
                } catch (PatternSyntaxException e) {
                    plugin.getLogger().log(Level.WARNING, "Invalid skip pattern: " + patternStr, e);
                }
            }
        }
        plugin.getLogger().info("Loaded " + skipPatterns.size() + " skip patterns.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage();

        // Check skip patterns first — these commands bypass ALL effects
        for (Pattern skip : skipPatterns) {
            if (skip.matcher(command).matches()) {
                plugin.getLogger().fine("Skip pattern matched: " + command);
                return; // let it pass through normally
            }
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
