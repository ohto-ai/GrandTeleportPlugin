package dev.ohtoai.grandteleportclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrandTeleportClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("GrandTeleport");
    public static final int PROTOCOL_VERSION = 1;

    @Override
    public void onInitializeClient() {
        LOGGER.info("=== GrandTeleportClient initializing ===");

        NetworkPayloads.register();
        LOGGER.info("Payload types registered");

        // S2C: CameraStart
        ClientPlayNetworking.registerGlobalReceiver(
                NetworkPayloads.CameraStartPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    LOGGER.info("[PACKET] CameraStart: ({},{},{}) -> ({},{},{}) peak={}",
                            (int) payload.srcX(), (int) payload.srcY(), (int) payload.srcZ(),
                            (int) payload.dstX(), (int) payload.dstY(), (int) payload.dstZ(),
                            payload.peakAltitude());
                    CameraTransitionController.start(
                            payload.srcX(), payload.srcY(), payload.srcZ(),
                            payload.dstX(), payload.dstY(), payload.dstZ(),
                            payload.peakAltitude(),
                            payload.pullTicks(), payload.holdTicks(),
                            payload.travelTicks(), payload.pushTicks()
                    );
                })
        );

        // S2C: ServerHello
        ClientPlayNetworking.registerGlobalReceiver(
                NetworkPayloads.ServerHelloPayload.TYPE,
                (payload, context) -> {
                    LOGGER.info("[PACKET] ServerHello: enabled={}", payload.enabled());
                }
        );

        // Send handshake on join
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientPlayNetworking.canSend(NetworkPayloads.ClientHelloPayload.TYPE)) {
                ClientPlayNetworking.send(new NetworkPayloads.ClientHelloPayload(PROTOCOL_VERSION));
                LOGGER.info("[HANDSHAKE] Sent ClientHello v{}", PROTOCOL_VERSION);
            } else {
                LOGGER.warn("[HANDSHAKE] Cannot send ClientHello — channel not ready");
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(CameraTransitionController::tick);
        LOGGER.info("=== GrandTeleportClient ready ===");
    }
}
