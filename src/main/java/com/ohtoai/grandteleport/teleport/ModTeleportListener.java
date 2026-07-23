package com.ohtoai.grandteleport.teleport;

import com.ohtoai.grandteleport.GrandTeleportPlugin;
import com.ohtoai.grandteleport.config.TransitionSettings;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Global listener for ALL PlayerTeleportEvents.
 * Catches teleports for mod-enabled players that weren't already handled
 * by CommandInterceptor (coordinates type).
 *
 * This handles TPA, warp, home, spawn, back, res tp, and any other
 * plugin-initiated teleports automatically — no command interception needed.
 */
public class ModTeleportListener implements Listener {

    private final GrandTeleportPlugin plugin;

    public ModTeleportListener(GrandTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        // Only handle mod-enabled players
        if (!plugin.hasMod(player)) return;

        // Don't interfere if already in a transition (handled by CommandInterceptor)
        if (plugin.isInTransition(player)) return;

        // Don't interfere with bypass
        if (plugin.shouldBypassNextCommand(player)) return;

        TransitionSettings settings = plugin.getTransitionSettings();
        if (!settings.enabled || !settings.modEnabled) return;

        Location to = event.getTo();
        if (to == null) return;

        // Only handle significant teleports (skip micro-adjustments)
        Location from = event.getFrom();
        if (from != null && from.getWorld() == to.getWorld()
                && from.distanceSquared(to) < 1.0) {
            return; // tiny movement, not a real teleport
        }

        // Cancel the teleport — we'll animate first
        event.setCancelled(true);

        if (!plugin.startTransition(player)) return;
        if (settings.freezeEnabled) plugin.freezePlayer(player);

        plugin.getLogger().info("Teleport intercepted: " + player.getName() + " → " +
                (int)to.getX() + "," + (int)to.getY() + "," + (int)to.getZ() +
                " (cause=" + event.getCause() + ")");

        ModCameraBridge modBridge = new ModCameraBridge(plugin);
        modBridge.startWithDirectTeleport(player, from, to, settings);
    }
}
