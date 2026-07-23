package dev.ohtoai.grandteleportclient;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom packet payloads — each sub-channel maps to one Type.
 *
 * Channel mapping (must match server's CameraPackets.java):
 *   S2C: grandteleport:camera_start  → CameraStartPayload
 *   C2S: grandteleport:camera_ready  → CameraReadyPayload
 *   C2S: grandteleport:client_hello  → ClientHelloPayload
 *   S2C: grandteleport:server_hello  → ServerHelloPayload
 */
public final class NetworkPayloads {

    private static boolean registered;

    private NetworkPayloads() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("grandteleport", path);
    }

    public static synchronized void register() {
        if (registered) return;
        PayloadTypeRegistry.playS2C().register(CameraStartPayload.TYPE, CameraStartPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CameraReadyPayload.TYPE, CameraReadyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClientHelloPayload.TYPE, ClientHelloPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerHelloPayload.TYPE, ServerHelloPayload.CODEC);
        registered = true;
    }

    // --- S2C: CameraStart (channel: grandteleport:camera_start) ---

    public record CameraStartPayload(
            double srcX, double srcY, double srcZ,
            double dstX, double dstY, double dstZ,
            int peakAltitude, int pullTicks, int holdTicks,
            int travelTicks, int pushTicks
    ) implements CustomPacketPayload {

        public static final Type<CameraStartPayload> TYPE = new Type<>(id("camera_start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CameraStartPayload> CODEC =
                StreamCodec.ofMember(CameraStartPayload::write, CameraStartPayload::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeDouble(srcX); buf.writeDouble(srcY); buf.writeDouble(srcZ);
            buf.writeDouble(dstX); buf.writeDouble(dstY); buf.writeDouble(dstZ);
            buf.writeInt(peakAltitude);
            buf.writeInt(pullTicks); buf.writeInt(holdTicks);
            buf.writeInt(travelTicks); buf.writeInt(pushTicks);
        }

        private static CameraStartPayload read(RegistryFriendlyByteBuf buf) {
            return new CameraStartPayload(
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt()
            );
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // --- C2S: CameraReady (channel: grandteleport:camera_ready) ---

    public record CameraReadyPayload() implements CustomPacketPayload {

        public static final Type<CameraReadyPayload> TYPE = new Type<>(id("camera_ready"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CameraReadyPayload> CODEC =
                StreamCodec.ofMember((p, buf) -> {}, buf -> new CameraReadyPayload());

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // --- C2S: ClientHello (channel: grandteleport:client_hello) ---

    public record ClientHelloPayload(int protocolVersion) implements CustomPacketPayload {

        public static final Type<ClientHelloPayload> TYPE = new Type<>(id("client_hello"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClientHelloPayload> CODEC =
                StreamCodec.ofMember(ClientHelloPayload::write, ClientHelloPayload::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeInt(protocolVersion);
        }

        private static ClientHelloPayload read(RegistryFriendlyByteBuf buf) {
            return new ClientHelloPayload(buf.readInt());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // --- S2C: ServerHello (channel: grandteleport:server_hello) ---

    public record ServerHelloPayload(boolean enabled) implements CustomPacketPayload {

        public static final Type<ServerHelloPayload> TYPE = new Type<>(id("server_hello"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ServerHelloPayload> CODEC =
                StreamCodec.ofMember(ServerHelloPayload::write, ServerHelloPayload::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeBoolean(enabled);
        }

        private static ServerHelloPayload read(RegistryFriendlyByteBuf buf) {
            return new ServerHelloPayload(buf.readBoolean());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
