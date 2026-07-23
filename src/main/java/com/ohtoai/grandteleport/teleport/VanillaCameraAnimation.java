package com.ohtoai.grandteleport.teleport;

import com.ohtoai.grandteleport.GrandTeleportPlugin;
import com.ohtoai.grandteleport.config.TransitionSettings;
import com.ohtoai.grandteleport.util.EffectUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Fallback for players without the client mod.
 * Directly teleports the player through waypoints each tick,
 * forcing pitch=90° (top-down) at each step.
 *
 * Not as smooth as the mod camera (player body moves with the camera),
 * but provides a "flying transition" feel without any client installation.
 */
public class VanillaCameraAnimation {

    private final GrandTeleportPlugin plugin;

    public VanillaCameraAnimation(GrandTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @param player   The player
     * @param source   Starting location
     * @param target   Destination (final teleport location)
     * @param settings Transition settings
     */
    public void start(Player player, Location source, Location target, TransitionSettings settings) {
        World world = player.getWorld();
        WaypointPath path = WaypointPath.generate(source, target, settings);

        final int[] tick = {0};
        final boolean[] stepPlayed = new boolean[8];
        final boolean[] travelPlayed = {false};
        int keyframes = path.getKeyframes().size();
        float yaw = source.getYaw();

        EffectUtil.sendTeleportingTitle(player, settings);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) { cleanup(); cancel(); return; }

                int elapsed = tick[0];
                if (elapsed > path.getTotalTicks()) {
                    Location dest = target.clone();
                    dest.setYaw(yaw);
                    dest.setPitch(90f);
                    player.teleport(dest);
                    EffectUtil.playArriveSound(player, settings);
                    EffectUtil.sendArrivalTitle(player, settings);
                    cleanup();
                    cancel();
                    return;
                }

                Location pos = path.getPositionAtTick(elapsed, world);
                pos.setYaw(yaw);
                pos.setPitch(90f);
                player.teleport(pos);

                if (elapsed % 2 == 0) EffectUtil.spawnParticles(player, pos.clone().add(0, -1, 0), settings);

                if (settings.soundsEnabled) {
                    for (int i = 0; i < keyframes && i < stepPlayed.length; i++) {
                        if (elapsed >= path.getKeyframes().get(i).tick && !stepPlayed[i]) {
                            stepPlayed[i] = true;
                            EffectUtil.playStepSound(player, settings);
                        }
                    }
                    int travelStart = settings.pullTicks + settings.holdTicks;
                    if (elapsed >= travelStart && !travelPlayed[0]) {
                        travelPlayed[0] = true;
                        EffectUtil.playTravelSound(player, settings);
                    }
                }

                tick[0]++;
            }

            void cleanup() {
                plugin.unfreezePlayer(player);
                plugin.endTransition(player);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
