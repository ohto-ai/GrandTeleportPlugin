package com.ohtoai.grandteleport.teleport;

import com.ohtoai.grandteleport.GrandTeleportPlugin;
import com.ohtoai.grandteleport.config.TransitionSettings;
import com.ohtoai.grandteleport.network.CameraPackets;
import com.ohtoai.grandteleport.util.EffectUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ModCameraBridge implements PluginMessageListener {

    private final GrandTeleportPlugin plugin;
    private static final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();
    private static boolean registered = false;

    public ModCameraBridge(GrandTeleportPlugin plugin) {
        this.plugin = plugin;
        if (!registered) {
            register();
        }
    }

    private void register() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(
                plugin, CameraPackets.CHANNEL_CAMERA_READY, this);
        registered = true;
    }

    public void start(Player player, Location source, Location target,
                      String originalCommand, TransitionSettings settings) {
        plugin.getLogger().info("[DEBUG] ModCameraBridge.start() — player=" + player.getName()
                + " src=" + fmt(source) + " dst=" + fmt(target));

        EffectUtil.sendTeleportingTitle(player, settings);

        byte[] data = CameraPackets.buildCameraStart(source, target, settings);
        plugin.getLogger().info("[DEBUG] Sending CameraStartPacket — " + data.length + " bytes, packetId=" + data[0]);

        player.sendPluginMessage(plugin, CameraPackets.CHANNEL_CAMERA_START, data);
        plugin.getLogger().info("[DEBUG] CameraStartPacket sent to " + player.getName());

        pendingTeleports.put(player.getUniqueId(), new PendingTeleport(
                player, source, target, originalCommand, settings, false
        ));

        int totalTicks = settings.pullTicks + settings.holdTicks
                + settings.travelTicks + settings.holdTicks + settings.pushTicks;
        int timeoutTicks = totalTicks + 40;

        plugin.getLogger().info("[DEBUG] Timeout scheduled in " + timeoutTicks + " ticks (total=" + totalTicks + ")");

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PendingTeleport pending = pendingTeleports.remove(player.getUniqueId());
            if (pending != null) {
                plugin.getLogger().warning("[DEBUG] CameraReady TIMEOUT for " + player.getName()
                        + " — executing teleport directly");
                plugin.setBypassNextCommand(player);
                plugin.getServer().dispatchCommand(player,
                        pending.originalCommand.startsWith("/")
                                ? pending.originalCommand.substring(1)
                                : pending.originalCommand);
                plugin.unfreezePlayer(player);
                plugin.endTransition(player);
            }
        }, timeoutTicks);
    }

    public void startWithDirectTeleport(Player player, Location source, Location target,
                                        TransitionSettings settings) {
        plugin.getLogger().info("[DEBUG] ModCameraBridge.startWithDirectTeleport() — player=" + player.getName()
                + " src=" + fmt(source) + " dst=" + fmt(target));

        EffectUtil.sendTeleportingTitle(player, settings);

        byte[] data = CameraPackets.buildCameraStart(source, target, settings);
        plugin.getLogger().info("[DEBUG] Sending CameraStartPacket (direct) — " + data.length + " bytes");

        player.sendPluginMessage(plugin, CameraPackets.CHANNEL_CAMERA_START, data);

        pendingTeleports.put(player.getUniqueId(), new PendingTeleport(
                player, source, target, null, settings, true
        ));

        int totalTicks = settings.totalTicks();
        int timeoutTicks = totalTicks + 40;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PendingTeleport pending = pendingTeleports.remove(player.getUniqueId());
            if (pending != null) {
                plugin.getLogger().warning("[DEBUG] CameraReady TIMEOUT (direct) for " + player.getName());
                player.teleport(target);
                plugin.unfreezePlayer(player);
                plugin.endTransition(player);
            }
        }, timeoutTicks);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!CameraPackets.CHANNEL_CAMERA_READY.equals(channel)) return;
        // CameraReady may be empty (payload with no data) — that's valid
        plugin.getLogger().info("[DEBUG] CameraReady received — channel=" + channel
                + " player=" + player.getName());

        PendingTeleport pending = pendingTeleports.remove(player.getUniqueId());
        if (pending == null) {
            plugin.getLogger().warning("[DEBUG] CameraReady from " + player.getName() + " but no pending teleport!");
            return;
        }

        plugin.getLogger().info("[DEBUG] CameraReady received — executing teleport for " + player.getName());

        if (pending.directTeleport) {
            player.teleport(pending.target);
            EffectUtil.playArriveSound(player, pending.settings);
            EffectUtil.sendArrivalTitle(player, pending.settings);
            plugin.unfreezePlayer(player);
            plugin.endTransition(player);
        } else {
            EffectUtil.playArriveSound(player, pending.settings);
            EffectUtil.sendArrivalTitle(player, pending.settings);
            plugin.unfreezePlayer(player);
            plugin.endTransition(player);

            plugin.setBypassNextCommand(player);
            plugin.getServer().dispatchCommand(player,
                    pending.originalCommand.startsWith("/")
                            ? pending.originalCommand.substring(1)
                            : pending.originalCommand);
        }
    }

    private static String fmt(Location loc) {
        return String.format("(%.0f,%.0f,%.0f %s)", loc.getX(), loc.getY(), loc.getZ(),
                loc.getWorld() != null ? loc.getWorld().getName() : "?");
    }

    private static class PendingTeleport {
        final Player player;
        final Location source;
        final Location target;
        final String originalCommand;
        final TransitionSettings settings;
        final boolean directTeleport;

        PendingTeleport(Player player, Location source, Location target,
                        String originalCommand, TransitionSettings settings,
                        boolean directTeleport) {
            this.player = player;
            this.source = source;
            this.target = target;
            this.originalCommand = originalCommand;
            this.settings = settings;
            this.directTeleport = directTeleport;
        }
    }
}
