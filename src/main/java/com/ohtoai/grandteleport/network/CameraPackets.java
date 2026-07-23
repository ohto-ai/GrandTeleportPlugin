package com.ohtoai.grandteleport.network;

import com.ohtoai.grandteleport.config.TransitionSettings;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Encodes/decodes packets for the grandteleport plugin channels.
 *
 * Each packet type uses its own sub-channel so Fabric's payload system
 * (one Type = one channel ID) can match them correctly:
 *
 *   S2C: grandteleport:camera_start  — camera transition start
 *   C2S: grandteleport:camera_ready  — client ACK
 *   C2S: grandteleport:client_hello  — handshake
 *   S2C: grandteleport:server_hello  — handshake response
 */
public final class CameraPackets {

    public static final String CHANNEL_CAMERA_START = "grandteleport:camera_start";
    public static final String CHANNEL_CAMERA_READY = "grandteleport:camera_ready";
    public static final String CHANNEL_CLIENT_HELLO = "grandteleport:client_hello";
    public static final String CHANNEL_SERVER_HELLO = "grandteleport:server_hello";

    /** All channels for plugin.yml and registration */
    public static final String[] ALL_CHANNELS = {
            CHANNEL_CAMERA_START, CHANNEL_CAMERA_READY,
            CHANNEL_CLIENT_HELLO, CHANNEL_SERVER_HELLO
    };

    private CameraPackets() {}

    /**
     * Build a CameraStart packet (no leading byte — channel implies type).
     */
    public static byte[] buildCameraStart(Location source, Location target, TransitionSettings settings) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        try {
            out.writeDouble(source.getX());
            out.writeDouble(source.getY());
            out.writeDouble(source.getZ());
            out.writeDouble(target.getX());
            out.writeDouble(target.getY());
            out.writeDouble(target.getZ());
            out.writeInt(settings.peakAltitude);
            out.writeInt(settings.pullTicks);
            out.writeInt(settings.holdTicks);
            out.writeInt(settings.travelTicks);
            out.writeInt(settings.pushTicks);
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode CameraStart packet", e);
        }
        return baos.toByteArray();
    }

    /**
     * Build a ServerHello packet.
     */
    public static byte[] buildServerHello(boolean enabled) {
        return new byte[]{(byte) (enabled ? 1 : 0)};
    }

    /**
     * Send a CameraStart packet through the camera_start sub-channel.
     */
    public static void sendCameraStart(Player player, Location source, Location target, TransitionSettings settings) {
        byte[] data = buildCameraStart(source, target, settings);
        player.sendPluginMessage(
                player.getServer().getPluginManager().getPlugin("GrandTeleportPlugin"),
                CHANNEL_CAMERA_START, data);
    }

    /**
     * Send a ServerHello packet through the server_hello sub-channel.
     */
    public static void sendServerHello(Player player, boolean enabled) {
        player.sendPluginMessage(
                player.getServer().getPluginManager().getPlugin("GrandTeleportPlugin"),
                CHANNEL_SERVER_HELLO, buildServerHello(enabled));
    }
}
