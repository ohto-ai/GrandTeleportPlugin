package dev.ohtoai.grandteleportclient;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders step flash + HUD fade overlay during transitions.
 */
public final class TeleportStepEffectRenderer {

    private TeleportStepEffectRenderer() {}

    public static void render(GuiGraphics context, DeltaTracker deltaTracker) {
        float tickProgress = deltaTracker.getGameTimeDeltaPartialTick(false);

        float stepIntensity = CameraTransitionController.getStepEffectIntensity(tickProgress);
        float hudIntensity = CameraTransitionController.getHudFadeIntensity(tickProgress);
        float intensity = Math.max(stepIntensity, hudIntensity);

        if (intensity <= 0.0F) return;

        int a = Math.min(255, Math.max(0, (int) (68.0F * intensity)));
        int color = (a << 24) | (245 << 16) | (245 << 8) | 235;
        context.fill(0, 0, context.guiWidth(), context.guiHeight(), color);
    }
}
