package com.ohtoai.grandteleport.listener;

import com.ohtoai.grandteleport.GrandTeleportPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Blocks movement, damage, and interaction for frozen players during a transition.
 */
public class PlayerFreezeListener implements Listener {

    private final GrandTeleportPlugin plugin;

    public PlayerFreezeListener(GrandTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.isFrozen(player) && plugin.getTransitionSettings().blockMovement) {
            // Only block actual position changes, not just head rotation
            if (event.hasChangedPosition()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.isFrozen(player) && plugin.getTransitionSettings().blockDamage) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (plugin.isFrozen(player) && plugin.getTransitionSettings().blockInteract) {
            event.setCancelled(true);
        }
    }

    /**
     * Clean up on quit: unfreeze, end transition, remove mod status.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.unfreezePlayer(player);
        plugin.endTransition(player);
        plugin.removeModPlayer(player.getUniqueId());
    }
}
