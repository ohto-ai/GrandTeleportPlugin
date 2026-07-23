package dev.ohtoai.grandteleportclient;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Full port of GrandTeleportMod's TeleportTransitionController.
 * 9-phase cinematic camera transition with exit/enter body, zoom stages,
 * shake effects, step flash pulses, and proper UI sounds.
 */
public final class CameraTransitionController {

    // Phase timing
    private static final int EXIT_BODY_TICKS = 12;
    private static final int BODY_HOLD_TICKS = 10;
    private static final int ZOOM_STAGE_TICKS = 13;   // per stage × 3 = 39 ticks zoom-out
    private static final int PRE_TRAVEL_WAIT_TICKS = 10;
    private static final int MIN_TRAVEL_TICKS = 20;
    private static final int MAX_TRAVEL_TICKS = 60;
    private static final double TRAVEL_BLOCKS_PER_TICK = 30.0;
    private static final int PRE_PUSH_WAIT_TICKS = 10;
    private static final int ENTER_BODY_TICKS = 12;
    private static final int POST_RELEASE_TICKS = 8;

    // Altitude stages
    private static final double ZOOM_OUT_STAGE_1 = 10.0;
    private static final double ZOOM_OUT_STAGE_2 = 50.0;
    private static final double ZOOM_OUT_STAGE_3 = 100.0;
    private static final double ZOOM_IN_STAGE_1 = 100.0;
    private static final double ZOOM_IN_STAGE_2 = 50.0;
    private static final double ZOOM_IN_STAGE_3 = 10.0;
    private static final double BODY_CAMERA_HEIGHT = 6.0;
    private static final double BODY_GLIDE_HEIGHT = 0.5;
    private static final double ZOOM_STAGE_GLIDE = 1.0;

    // Shake
    private static final float ZOOM_SHAKE_PITCH_AMP = 1.05F;
    private static final float ZOOM_SHAKE_YAW_AMP = 0.15F;
    private static final float BODY_LOOK_SPEED = 1.5F;
    private static final float TOP_DOWN_PITCH = 90.0F;

    // Sound volumes
    private static final float STEP_VOLUME = 0.86F;
    private static final float STEP_PITCH = 1.0F;
    private static final float TRAVEL_VOLUME = 0.48F;
    private static final float TRAVEL_PITCH = 1.0F;
    private static final float BODY_VOLUME = 1.0F;
    private static final float BODY_PITCH = 0.8F;

    // State
    private static Runnable pendingAction;
    private static Vec3 startFeet;
    private static Vec3 startEye;
    private static Vec3 plannedTargetFeet;
    private static Vec3 actualTargetFeet;
    private static Vec3 arrivalCameraFeet;
    private static Vec3 enterBodyTargetEye;
    private static float startYaw;
    private static float startPitch;
    private static float enterBodyTargetYaw;
    private static float enterBodyTargetPitch;
    private static CameraType previousCameraType;
    private static boolean previousHudHidden;
    private static int ticks;
    private static int travelTicks = 40;
    private static int totalTicks;
    private static boolean running;
    private static boolean commandSent;
    private static boolean cameraReleased;
    private static boolean hudSuppressed;

    private CameraTransitionController() {}

    static void start(double srcX, double srcY, double srcZ,
                      double dstX, double dstY, double dstZ,
                      int peakAlt, int pullT, int holdT, int travelT, int pushT) {

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || running) return;

        GrandTeleportClient.LOGGER.info("[CAM] start src=({},{},{}) dst=({},{},{})",
                (int) srcX, (int) srcY, (int) srcZ, (int) dstX, (int) dstY, (int) dstZ);

        Vec3 plannedTarget = new Vec3(dstX, dstY, dstZ);
        pendingAction = () -> sendCameraReady();
        startFeet = new Vec3(srcX, srcY, srcZ);
        startEye = player.getEyePosition();
        plannedTargetFeet = plannedTarget;
        actualTargetFeet = null;
        arrivalCameraFeet = plannedTarget;
        enterBodyTargetEye = null;
        startYaw = player.getYRot();
        startPitch = player.getXRot();
        enterBodyTargetYaw = 0;
        enterBodyTargetPitch = 0;

        double dx = dstX - srcX;
        double dz = dstZ - srcZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        travelTicks = Mth.clamp(Mth.ceil(dist / TRAVEL_BLOCKS_PER_TICK), MIN_TRAVEL_TICKS, MAX_TRAVEL_TICKS);
        totalTicks = EXIT_BODY_TICKS + BODY_HOLD_TICKS + ZOOM_STAGE_TICKS * 3
                + PRE_TRAVEL_WAIT_TICKS + travelTicks + PRE_PUSH_WAIT_TICKS
                + ZOOM_STAGE_TICKS * 3 + BODY_HOLD_TICKS + ENTER_BODY_TICKS;

        ticks = 0;
        running = true;
        commandSent = false;
        cameraReleased = false;
        hudSuppressed = false;

        previousCameraType = client.options.getCameraType();
        previousHudHidden = client.options.hideGui;

        if (client.screen != null) client.setScreen(null);
        client.options.setCameraType(CameraType.THIRD_PERSON_BACK);

        GrandTeleportClient.LOGGER.info("[CAM] Started — totalTicks={} travelTicks={}", totalTicks, travelTicks);
    }

    static void tick(Minecraft client) {
        if (!running) return;
        if (client.player == null || client.level == null || client.getConnection() == null) {
            clear(client); return;
        }

        ticks++;

        updateHudVisibility(client);

        // Body transition sounds
        if (ticks == 1) playUiSound(client, SoundEvents.UI_TOAST_OUT, BODY_VOLUME, BODY_PITCH);
        if (ticks == getEnterStartTick()) playUiSound(client, SoundEvents.UI_TOAST_OUT, BODY_VOLUME, BODY_PITCH);

        // Step sounds at stage boundaries (matching original GrandTeleportMod timing)
        int sp0 = getPullStartTick();           // body-hold → zoom-out
        int sp1 = getPullStageEnd(0);            // stage 0 → stage 1
        int sp2 = getPullStageEnd(1);            // stage 1 → stage 2
        int ss1 = getPushStageEnd(0);            // stage 2 → stage 1
        int ss2 = getPushStageEnd(1);            // stage 1 → stage 0
        int ss3 = getEnterHoldStartTick();       // zoom-in → body-hold
        if (ticks == sp0 || ticks == sp1 || ticks == sp2
                || ticks == ss1 || ticks == ss2 || ticks == ss3) {
            playUiSound(client, SoundEvents.RESPAWN_ANCHOR_CHARGE, STEP_VOLUME, STEP_PITCH);
        }

        // Travel sound
        if (ticks == getTravelStartTick()) {
            playUiSound(client, SoundEvents.PORTAL_TRIGGER, TRAVEL_VOLUME, TRAVEL_PITCH);
        }

        // Send CameraReady at travel midpoint
        if (!commandSent && ticks >= getCommandSendTick()) {
            commandSent = true;
            if (pendingAction != null) pendingAction.run();
        }

        // Release camera after totalTicks
        if (!cameraReleased && ticks >= totalTicks) {
            if (!commandSent) {
                commandSent = true;
                if (pendingAction != null) pendingAction.run();
            }
            cameraReleased = true;
            if (previousCameraType != null) client.options.setCameraType(previousCameraType);
        }

        if (ticks >= totalTicks + POST_RELEASE_TICKS) {
            clear(client);
        }
    }

    public static boolean isRunning() { return running; }

    public static CameraFrame getCameraFrame(float tickProgress) {
        if (!running || cameraReleased) return null;

        float frameTick = Math.min(ticks + tickProgress, totalTicks);

        if (frameTick <= EXIT_BODY_TICKS) {
            return exitBodyFrame(frameTick / EXIT_BODY_TICKS);
        }

        int bodyHoldEnd = EXIT_BODY_TICKS + BODY_HOLD_TICKS;
        if (frameTick <= bodyHoldEnd) {
            return bodyHoldFrame(startFeet, startYaw, bodyAltitude(frameTick, EXIT_BODY_TICKS));
        }

        int pullEnd = bodyHoldEnd + ZOOM_STAGE_TICKS * 3;
        if (frameTick <= pullEnd) {
            return pullFrame(frameTick);
        }

        int travelStart = pullEnd + PRE_TRAVEL_WAIT_TICKS;
        if (frameTick <= travelStart) {
            return topDownFrame(startFeet, startYaw, ZOOM_OUT_STAGE_3 + ZOOM_STAGE_GLIDE);
        }

        int travelEnd = travelStart + travelTicks;
        if (frameTick <= travelEnd) {
            return travelFrame((frameTick - travelStart) / travelTicks);
        }

        int pushStart = travelEnd + PRE_PUSH_WAIT_TICKS;
        if (frameTick <= pushStart) {
            return topDownFrame(getArrivalFeet(), startYaw, ZOOM_IN_STAGE_1 + ZOOM_STAGE_GLIDE);
        }

        int enterHoldStart = pushStart + ZOOM_STAGE_TICKS * 3;
        if (frameTick < enterHoldStart) {
            return pushFrame(frameTick);
        }

        int enterStart = enterHoldStart + BODY_HOLD_TICKS;
        if (frameTick <= enterStart) {
            return bodyHoldFrame(getArrivalFeet(), startYaw, bodyAltitude(frameTick, enterHoldStart));
        }

        return enterBodyFrame(frameTick);
    }

    public static boolean shouldBlockPlayerInput() { return running && !cameraReleased; }

    public static float getStepEffectIntensity(float tickProgress) {
        if (!running) return 0;
        float ft = ticks + tickProgress;
        float intensity = 0;
        intensity = Math.max(intensity, pulseAfter(ft, getPullStartTick()));      // body-hold → zoom-out
        intensity = Math.max(intensity, pulseAfter(ft, getPullStageEnd(0)));      // stage 0 → stage 1
        intensity = Math.max(intensity, pulseAfter(ft, getPullStageEnd(1)));      // stage 1 → stage 2
        intensity = Math.max(intensity, pulseAfter(ft, getPushStageEnd(0)));      // stage 2 → stage 1
        intensity = Math.max(intensity, pulseAfter(ft, getPushStageEnd(1)));      // stage 1 → stage 0
        intensity = Math.max(intensity, pulseAfter(ft, getEnterHoldStartTick())); // zoom-in → body-hold
        return intensity;
    }

    public static float getHudFadeIntensity(float tickProgress) {
        if (!running) return 0;
        float ft = ticks + tickProgress;
        if (ft < 8) return smoothStep(ft / 8) * 0.46F;
        if (ft < 16) return (1 - smoothStep((ft - 8) / 8)) * 0.46F;
        if (ft >= totalTicks && ft <= totalTicks + 8)
            return (1 - smoothStep((ft - totalTicks) / 8)) * 0.46F;
        return 0;
    }

    // --- Frame generators ---

    private static CameraFrame exitBodyFrame(float progress) {
        float hp = smoothStep(progress);
        float lp = bodyLookProgress(progress);
        Vec3 src = startEye != null ? startEye : startFeet.add(0, 1.6, 0);
        Vec3 dst = startFeet.add(0, BODY_CAMERA_HEIGHT, 0);
        return new CameraFrame(src.lerp(dst, hp), startYaw, lerp(startPitch, TOP_DOWN_PITCH, lp));
    }

    private static CameraFrame bodyHoldFrame(Vec3 feet, float yaw, double altitude) {
        return new CameraFrame(feet.add(0, altitude, 0), yaw, TOP_DOWN_PITCH);
    }

    private static CameraFrame pullFrame(float frameTick) {
        double alt = pullAltitude(frameTick);
        return applyShake(startFeet.add(0, alt, 0), startYaw, TOP_DOWN_PITCH, frameTick);
    }

    private static CameraFrame topDownFrame(Vec3 feet, float yaw, double altitude) {
        return applyShake(feet.add(0, altitude, 0), yaw, TOP_DOWN_PITCH, 0);
    }

    private static CameraFrame travelFrame(float progress) {
        Vec3 src = startFeet.add(0, ZOOM_OUT_STAGE_3 + ZOOM_STAGE_GLIDE, 0);
        Vec3 dst = getArrivalFeet().add(0, ZOOM_IN_STAGE_1 + ZOOM_STAGE_GLIDE, 0);
        float p = cubicBezierEase(progress);
        return new CameraFrame(src.lerp(dst, p), startYaw, TOP_DOWN_PITCH);
    }

    private static CameraFrame pushFrame(float frameTick) {
        double alt = pushAltitude(frameTick);
        return applyShake(getArrivalFeet().add(0, alt, 0), startYaw, TOP_DOWN_PITCH, frameTick);
    }

    private static CameraFrame enterBodyFrame(float frameTick) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return null;
        if (enterBodyTargetEye == null) {
            enterBodyTargetEye = player.getEyePosition();
            enterBodyTargetYaw = player.getYRot();
            enterBodyTargetPitch = player.getXRot();
        }
        int enterStart = getEnterStartTick();
        float progress = (frameTick - enterStart) / ENTER_BODY_TICKS;
        float hp = smoothStep(Mth.clamp(progress, 0, 1));
        float lp = bodyLookProgress(Mth.clamp(progress, 0, 1));
        Vec3 src = getArrivalFeet().add(0, BODY_CAMERA_HEIGHT, 0);
        return new CameraFrame(src.lerp(enterBodyTargetEye, hp),
                lerpDegrees(startYaw, enterBodyTargetYaw, lp),
                lerp(TOP_DOWN_PITCH, enterBodyTargetPitch, lp));
    }

    // --- Altitude helpers ---

    private static double pullAltitude(float frameTick) {
        int s0 = getPullStageEnd(0), s1 = getPullStageEnd(1), s2 = getPullStageEnd(2);
        int start = getPullStartTick();
        if (frameTick < s0) return ZOOM_OUT_STAGE_1 + glideFrac(frameTick, start, s0);
        if (frameTick < s1) return ZOOM_OUT_STAGE_2 + glideFrac(frameTick, s0, s1);
        return ZOOM_OUT_STAGE_3 + glideFrac(frameTick, s1, s2);
    }

    private static double pushAltitude(float frameTick) {
        int s0 = getPushStageEnd(0), s1 = getPushStageEnd(1), s2 = getPushStageEnd(2);
        int pushStart = getPushStartTick();
        if (frameTick < s0) return ZOOM_IN_STAGE_1 + (1 - glideFrac(frameTick, pushStart, s0));
        if (frameTick < s1) return ZOOM_IN_STAGE_2 + (1 - glideFrac(frameTick, s0, s1));
        return ZOOM_IN_STAGE_3 + (1 - glideFrac(frameTick, s1, s2));
    }

    private static double bodyAltitude(float frameTick, int baseTick) {
        float p = (frameTick - baseTick) / BODY_HOLD_TICKS;
        return BODY_CAMERA_HEIGHT + Mth.clamp(p, 0, 1) * BODY_GLIDE_HEIGHT;
    }

    private static double glideFrac(float frameTick, int start, int end) {
        float p = (frameTick - start) / Math.max(1, end - start);
        float x = Mth.clamp(p, 0, 1);
        return (x * x * (3 - 2 * x)) * ZOOM_STAGE_GLIDE; // smoothStep
    }

    // --- Shake ---

    private static CameraFrame applyShake(Vec3 pos, float yaw, float pitch, float frameTick) {
        double vw = Math.sin(frameTick * 0.105) * 0.74 + Math.sin(frameTick * 0.052 + 1.6) * 0.26;
        double hw = Math.sin(frameTick * 0.105 + 2.1) * 0.78 + Math.sin(frameTick * 0.15 + 0.35) * 0.22;
        float py = yaw + (float) (hw * ZOOM_SHAKE_YAW_AMP);
        float pp = Mth.clamp(pitch + (float) ((Mth.clamp(vw, -1, 1) - 1) * 0.5 * ZOOM_SHAKE_PITCH_AMP), -90, 90);
        return new CameraFrame(pos, py, pp);
    }

    // --- Tick helpers ---

    private static int getPullStartTick() { return EXIT_BODY_TICKS + BODY_HOLD_TICKS; }
    private static int getPullStageEnd(int i) { return getPullStartTick() + ZOOM_STAGE_TICKS * (i + 1); }
    private static int getTravelStartTick() { return getPullStartTick() + ZOOM_STAGE_TICKS * 3 + PRE_TRAVEL_WAIT_TICKS; }
    private static int getPushStartTick() { return getTravelStartTick() + travelTicks + PRE_PUSH_WAIT_TICKS; }
    private static int getPushStageEnd(int i) { return getPushStartTick() + ZOOM_STAGE_TICKS * (i + 1); }
    private static int getEnterHoldStartTick() { return getPushStartTick() + ZOOM_STAGE_TICKS * 3; }
    private static int getEnterStartTick() { return getEnterHoldStartTick() + BODY_HOLD_TICKS; }
    private static int getCommandSendTick() { return getTravelStartTick() + travelTicks / 2; }

    private static Vec3 getArrivalFeet() {
        if (actualTargetFeet != null) return actualTargetFeet;
        return plannedTargetFeet != null ? plannedTargetFeet : startFeet;
    }

    // --- Math ---

    private static float smoothStep(float x) { float xx = Mth.clamp(x, 0, 1); return xx * xx * (3 - 2 * xx); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * Mth.clamp(t, 0, 1); }
    private static float bodyLookProgress(float p) { return 1 - (1 - Mth.clamp(p * BODY_LOOK_SPEED, 0, 1)) * (1 - Mth.clamp(p * BODY_LOOK_SPEED, 0, 1)) * (1 - Mth.clamp(p * BODY_LOOK_SPEED, 0, 1)); }
    private static float lerpDegrees(float a, float b, float p) { return a + ((b - a) % 360 + 540) % 360 - 180 * Mth.clamp(p, 0, 1); }
    private static float pulseAfter(float tick, float start) { float age = tick - start; if (age < 0 || age > 7) return 0; float x = age / 7; return (1 - x) * (1 - x); }

    private static float cubicBezierEase(float progress) {
        float x = Mth.clamp(progress, 0, 1), t = x;
        for (int i = 0; i < 5; i++) {
            float cx = cb(t, 0.55F, 0.55F), d = cbd(t, 0.55F, 0.55F);
            if (Math.abs(d) < 0.0001F) break;
            t = Mth.clamp(t - (cx - x) / d, 0, 1);
        }
        return cb(t, 0, 1);
    }
    private static float cb(float t, float c1, float c2) { float u = 1 - t; return 3 * u * u * t * c1 + 3 * u * t * t * c2 + t * t * t; }
    private static float cbd(float t, float c1, float c2) { float u = 1 - t; return 3 * u * u * c1 + 6 * u * t * (c2 - c1) + 3 * t * t * (1 - c2); }

    // --- Sound & network ---

    private static void playUiSound(Minecraft client, net.minecraft.sounds.SoundEvent sound, float vol, float pitch) {
        client.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, vol));
    }

    private static void sendCameraReady() {
        try {
            if (ClientPlayNetworking.canSend(NetworkPayloads.CameraReadyPayload.TYPE)) {
                ClientPlayNetworking.send(new NetworkPayloads.CameraReadyPayload());
                GrandTeleportClient.LOGGER.info("[CAM] CameraReady sent");
            }
        } catch (Exception e) {
            GrandTeleportClient.LOGGER.error("[CAM] CameraReady failed", e);
        }
    }

    private static void updateHudVisibility(Minecraft client) {
        if (!hudSuppressed && ticks >= 8) {
            client.options.hideGui = true;
            hudSuppressed = true;
        }
        if (hudSuppressed && cameraReleased) {
            client.options.hideGui = previousHudHidden;
            hudSuppressed = false;
        }
    }

    private static void clear(Minecraft client) {
        if (previousCameraType != null) client.options.setCameraType(previousCameraType);
        client.options.hideGui = previousHudHidden;
        running = false;
        pendingAction = null;
        startFeet = null; startEye = null; plannedTargetFeet = null;
        actualTargetFeet = null; arrivalCameraFeet = null; enterBodyTargetEye = null;
        previousCameraType = null;
        ticks = 0; commandSent = false; cameraReleased = false; hudSuppressed = false;
    }

    public record CameraFrame(Vec3 pos, float yaw, float pitch) {}
}
