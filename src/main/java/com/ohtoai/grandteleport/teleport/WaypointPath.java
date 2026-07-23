package com.ohtoai.grandteleport.teleport;

import com.ohtoai.grandteleport.config.TransitionSettings;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates waypoint keyframes for the camera animation path.
 *
 * Path structure (matching GrandTeleportMod's 5-phase design):
 *   Phase 1: PULL  — camera rises in 3 stepped altitudes above source
 *   Phase 2: HOLD  — pause at peak above source
 *   Phase 3: TRAVEL — horizontal movement from source to target at peak altitude
 *   Phase 4: HOLD  — pause at peak above target
 *   Phase 5: PUSH  — camera descends in 3 stepped altitudes to target
 */
public final class WaypointPath {

    // Altitude steps used for pull-up and push-down, inspired by GrandTeleportMod
    private static final double[] ALTITUDE_STEPS = {10.0, 50.0, 1.0}; // 1.0 = use peakAltitude
    private static final int STEPS_PER_DIRECTION = 3;

    private final List<Keyframe> keyframes;
    private final int totalTicks;

    private WaypointPath(List<Keyframe> keyframes, int totalTicks) {
        this.keyframes = keyframes;
        this.totalTicks = totalTicks;
    }

    /**
     * Generate waypoints from source to target location using the configured timing.
     */
    public static WaypointPath generate(Location source, Location target, TransitionSettings settings) {
        List<Keyframe> frames = new ArrayList<>();
        World world = source.getWorld();
        if (world == null) world = target.getWorld();

        double srcX = source.getX();
        double srcY = source.getY();
        double srcZ = source.getZ();
        double dstX = target.getX();
        double dstY = target.getY();
        double dstZ = target.getZ();
        double peak = settings.peakAltitude;

        int tick = 0;

        // Phase 1: PULL-UP (3 stepped altitudes)
        for (int i = 1; i <= STEPS_PER_DIRECTION; i++) {
            tick += settings.pullTicks / STEPS_PER_DIRECTION;
            double altitude = (i < STEPS_PER_DIRECTION)
                    ? ALTITUDE_STEPS[i - 1]
                    : peak;
            frames.add(new Keyframe(tick, srcX, srcY + altitude, srcZ));
        }

        // Phase 2: HOLD above source
        tick += settings.holdTicks;
        frames.add(new Keyframe(tick, srcX, srcY + peak, srcZ));

        // Phase 3: TRAVEL (horizontal)
        tick += settings.travelTicks;
        frames.add(new Keyframe(tick, dstX, dstY + peak, dstZ));

        // Phase 4: HOLD above target
        tick += settings.holdTicks;
        frames.add(new Keyframe(tick, dstX, dstY + peak, dstZ));

        // Phase 5: PUSH-DOWN (3 stepped altitudes, descending)
        for (int i = STEPS_PER_DIRECTION; i >= 1; i--) {
            tick += settings.pushTicks / STEPS_PER_DIRECTION;
            double altitude = (i > 1)
                    ? ALTITUDE_STEPS[i - 2]
                    : 0;
            double targetY = dstY + altitude;
            frames.add(new Keyframe(tick, dstX, targetY, dstZ));
        }

        // Final: exact target position (at eye height)
        frames.add(new Keyframe(tick, dstX, dstY, dstZ));

        return new WaypointPath(frames, tick);
    }

    /**
     * Generate waypoints for a path where we only know the target at high altitude.
     * Used for the travel phase when we want to sweep from source-peak to target-peak
     * without push-down (the actual teleport will handle the descent).
     */
    public static WaypointPath generateSimple(Location source, Location target, TransitionSettings settings) {
        List<Keyframe> frames = new ArrayList<>();

        double srcX = source.getX();
        double srcY = source.getY();
        double srcZ = source.getZ();
        double dstX = target.getX();
        double dstY = target.getY();
        double dstZ = target.getZ();
        double peak = settings.peakAltitude;

        int tick = 0;

        // Pull-up
        tick += settings.pullTicks;
        frames.add(new Keyframe(tick, srcX, srcY + peak, srcZ));

        // Hold
        tick += settings.holdTicks;
        frames.add(new Keyframe(tick, srcX, srcY + peak, srcZ));

        // Travel
        tick += settings.travelTicks;
        frames.add(new Keyframe(tick, dstX, dstY + peak, dstZ));

        // Hold
        tick += settings.holdTicks;
        frames.add(new Keyframe(tick, dstX, dstY + peak, dstZ));

        // Push-down
        tick += settings.pushTicks;
        frames.add(new Keyframe(tick, dstX, dstY, dstZ));

        return new WaypointPath(frames, tick);
    }

    /**
     * Get the interpolated position at a given elapsed tick.
     * Performs linear interpolation between the surrounding keyframes.
     */
    public Location getPositionAtTick(float elapsedTicks, World world) {
        if (keyframes.isEmpty()) return new Location(world, 0, 0, 0);

        float clampedTick = Math.max(0, Math.min(elapsedTicks, totalTicks));

        // Find surrounding keyframes
        Keyframe prev = keyframes.get(0);
        Keyframe next = keyframes.get(keyframes.size() - 1);

        for (int i = 0; i < keyframes.size(); i++) {
            Keyframe kf = keyframes.get(i);
            if (kf.tick >= clampedTick) {
                next = kf;
                prev = (i > 0) ? keyframes.get(i - 1) : kf;
                break;
            }
        }

        // Linear interpolation
        float range = next.tick - prev.tick;
        float t = (range > 0) ? (clampedTick - prev.tick) / range : 1.0f;
        t = Math.max(0, Math.min(1, t));

        double x = lerp(prev.x, next.x, t);
        double y = lerp(prev.y, next.y, t);
        double z = lerp(prev.z, next.z, t);

        return new Location(world, x, y, z);
    }

    public int getTotalTicks() {
        return totalTicks;
    }

    public List<Keyframe> getKeyframes() {
        return keyframes;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * A single keyframe in the animation: position at a specific tick.
     */
    public static class Keyframe {
        public final int tick;
        public final double x;
        public final double y;
        public final double z;

        public Keyframe(int tick, double x, double y, double z) {
            this.tick = tick;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
