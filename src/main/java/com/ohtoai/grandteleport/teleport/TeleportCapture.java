package com.ohtoai.grandteleport.teleport;

import com.ohtoai.grandteleport.GrandTeleportPlugin;
import com.ohtoai.grandteleport.config.TransitionSettings;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures PlayerTeleportEvent for passthrough-type commands.
 *
 * When a passthrough command is dispatched (e.g., /res tp Home),
 * the target plugin resolves the destination and triggers a teleport.
 * We intercept that event to:
 * 1. Get the destination location
 * 2. Cancel the actual teleport
 * 3. Play the camera animation to that destination
 * 4. Complete the teleport after the animation
 */
public class TeleportCapture implements Listener {

    private final GrandTeleportPlugin plugin;

    // Players we're currently watching for teleport events
    private final Map<UUID, Long> captureTimestamps = new ConcurrentHashMap<>();

    // Captured destinations (before animation starts)
    private final Map<UUID, Location> capturedDestinations = new ConcurrentHashMap<>();

    public TeleportCapture(GrandTeleportPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Start watching a player for a teleport event.
     */
    public void startCapture(Player player) {
        captureTimestamps.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Check if a teleport was captured for this player.
     */
    public boolean isCaptured(Player player) {
        return capturedDestinations.containsKey(player.getUniqueId());
    }

    /**
     * Get and remove the captured destination.
     */
    public Location consumeCapture(Player player) {
        return capturedDestinations.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Only capture if we're watching this player
        if (!captureTimestamps.containsKey(uuid)) {
            return;
        }

        // Avoid capturing teleports triggered by our own bypass commands
        if (plugin.shouldBypassNextCommand(player)) {
            captureTimestamps.remove(uuid);
            return;
        }

        // Capture the destination
        Location to = event.getTo();
        if (to == null) {
            captureTimestamps.remove(uuid);
            return;
        }

        // No mod → don't interfere, let the teleport happen normally
        TransitionSettings settings = plugin.getTransitionSettings();
        if (!settings.modEnabled || !plugin.hasMod(player)) {
            captureTimestamps.remove(uuid);
            return;
        }

        // Cancel the original teleport — we'll do it after the camera animation
        event.setCancelled(true);
        captureTimestamps.remove(uuid);
        capturedDestinations.put(uuid, to.clone());

        Location source = event.getFrom();

        // Full mod camera transition, then teleport
        ModCameraBridge modBridge = new ModCameraBridge(plugin);
        modBridge.startWithDirectTeleport(player, source, to, settings);
    }
}
