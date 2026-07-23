package com.ohtoai.grandteleport.listener;

import com.ohtoai.grandteleport.GrandTeleportPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Handles client mod handshake on player join.
 * The client mod sends a ClientHelloPacket on join; the server response is
 * handled by GrandTeleportPlugin.onPluginMessageReceived().
 *
 * This listener tracks players and cleans up on quit (via PlayerFreezeListener).
 */
public class ModHandshakeListener implements Listener {

    private final GrandTeleportPlugin plugin;

    public ModHandshakeListener(GrandTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // If mod is disabled in config, no need to wait for handshake
        if (!plugin.getConfigManager().getConfig().getBoolean("mod.enabled", true)) {
            return;
        }

        // Set a timeout: if no ClientHello received within handshake_timeout_ticks,
        // the player is considered "no mod"
        int timeoutTicks = plugin.getConfigManager().getConfig()
                .getInt("mod.handshake_timeout_ticks", 60);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // If still online and hasn't sent ClientHello
            if (player.isOnline() && !plugin.hasMod(player)) {
                // Player doesn't have the mod — this is normal, no action needed
                plugin.getLogger().fine("Mod handshake timeout for " + player.getName()
                        + " — treating as vanilla client");
            }
        }, timeoutTicks);
    }
}
