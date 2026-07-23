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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Listens for PlayerCommandPreprocessEvent, matches commands against configured
 * regex patterns, and routes them to the appropriate transition handler.
 *
 * Two routing types:
 * - "coordinates": Parse destination from command → full camera animation
 * - "passthrough":  Unknown destination → re-dispatch with effect wrapper
 */
public class CommandInterceptor implements Listener {

    private final GrandTeleportPlugin plugin;
    private final Map<Pattern, String> commandPatterns = new ConcurrentHashMap<>();
    private final TeleportCapture teleportCapture;

    public CommandInterceptor(GrandTeleportPlugin plugin) {
        this.plugin = plugin;
        this.teleportCapture = new TeleportCapture(plugin);
        loadPatterns();
    }

    /**
     * Reload command patterns from config (called on /gtp reload).
     */
    public void loadPatterns() {
        commandPatterns.clear();
        List<Map<?, ?>> commands = plugin.getConfigManager().getConfig().getMapList("commands");
        if (commands == null || commands.isEmpty()) {
            plugin.getLogger().warning("No command patterns configured in config.yml!");
            return;
        }

        for (Map<?, ?> entry : commands) {
            String patternStr = String.valueOf(entry.get("pattern"));
            String type = String.valueOf(entry.get("type"));
            Object enabledObj = entry.get("enabled");
            boolean enabled = enabledObj == null || Boolean.parseBoolean(String.valueOf(enabledObj));

            if (!enabled) continue;

            try {
                Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
                commandPatterns.put(pattern, type);
                plugin.getLogger().info("Loaded command pattern: " + patternStr + " → " + type);
            } catch (PatternSyntaxException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Invalid regex pattern in config: " + patternStr, e);
            }
        }

        plugin.getLogger().info("Loaded " + commandPatterns.size() + " command patterns.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage();

        // Check bypass flag (set when we re-dispatch a command after animation)
        if (plugin.shouldBypassNextCommand(player)) {
            return;
        }

        // Check permissions
        if (player.hasPermission("grandteleport.bypass")) {
            return;
        }
        if (!player.hasPermission("grandteleport.use")) {
            return;
        }

        // Check master switch
        TransitionSettings settings = plugin.getTransitionSettings();
        if (!settings.enabled) {
            return;
        }

        // Check if player is already in a transition
        if (plugin.isInTransition(player)) {
            return;
        }

        // Skip our own admin command
        String lowerCmd = command.toLowerCase().stripLeading();
        if (lowerCmd.startsWith("/grandtp") || lowerCmd.startsWith("/gtp")) {
            return;
        }

        // Match against configured patterns
        for (Map.Entry<Pattern, String> entry : commandPatterns.entrySet()) {
            if (entry.getKey().matcher(command).matches()) {
                String type = entry.getValue();
                event.setCancelled(true);

                plugin.getLogger().info("Intercepted command from " + player.getName()
                        + ": " + command + " (type=" + type + ")");

                if ("coordinates".equals(type)) {
                    handleCoordinates(player, command, settings);
                } else {
                    handlePassthrough(player, command, settings);
                }
                return;
            }
        }
    }

    /**
     * Handle a coordinates-type command.
     * Has mod → full camera transition via ModCameraBridge.
     * No mod → just let it pass through as vanilla.
     */
    private void handleCoordinates(Player player, String command, TransitionSettings settings) {
        // No mod installed → don't interfere, let vanilla handle it
        if (!settings.modEnabled || !plugin.hasMod(player)) {
            plugin.setBypassNextCommand(player);
            plugin.getServer().dispatchCommand(player, command.startsWith("/")
                    ? command.substring(1) : command);
            return;
        }

        Location source = player.getLocation();
        Location target = DestinationParser.parse(command, player);

        if (target == null) {
            // Could not parse coordinates → let vanilla handle it
            plugin.setBypassNextCommand(player);
            plugin.getServer().dispatchCommand(player, command.startsWith("/")
                    ? command.substring(1) : command);
            return;
        }

        boolean started = plugin.startTransition(player);
        if (!started) return;

        if (settings.freezeEnabled) {
            plugin.freezePlayer(player);
        }

        ModCameraBridge modBridge = new ModCameraBridge(plugin);
        modBridge.start(player, source, target, command, settings);
    }

    /**
     * Handle a passthrough-type command.
     * Has mod → capture teleport destination, animate, then teleport.
     * No mod → let it pass through as vanilla.
     */
    private void handlePassthrough(Player player, String command, TransitionSettings settings) {
        // No mod installed → don't interfere
        if (!settings.modEnabled || !plugin.hasMod(player)) {
            plugin.setBypassNextCommand(player);
            plugin.getServer().dispatchCommand(player, command.startsWith("/")
                    ? command.substring(1) : command);
            return;
        }

        boolean started = plugin.startTransition(player);
        if (!started) return;

        if (settings.freezeEnabled) {
            plugin.freezePlayer(player);
        }

        String cleanCommand = command.startsWith("/") ? command.substring(1) : command;

        teleportCapture.startCapture(player);

        plugin.setBypassNextCommand(player);
        plugin.getServer().dispatchCommand(player, cleanCommand);

        // Schedule a check: if no teleport happened, clean up
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!teleportCapture.isCaptured(player)) {
                plugin.unfreezePlayer(player);
                plugin.endTransition(player);
            }
        }, settings.passthroughDelayTicks + 10);
    }
}
