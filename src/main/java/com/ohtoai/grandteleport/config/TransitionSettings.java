package com.ohtoai.grandteleport.config;

import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

/**
 * Immutable POJO holding all transition settings parsed from config.yml.
 * Created fresh from config each time a transition starts, so /gtp reload
 * applies to new transitions immediately.
 */
public final class TransitionSettings {

    public final boolean enabled;
    public final boolean modEnabled;
    public final String modChannel;
    public final int handshakeTimeoutTicks;
    public final int peakAltitude;
    public final int pullTicks;
    public final int holdTicks;
    public final int travelTicks;
    public final int pushTicks;

    public final int passthroughDelayTicks;

    public final boolean soundsEnabled;
    public final Sound stepSound;
    public final float stepVolume;
    public final float stepPitch;
    public final Sound travelSound;
    public final float travelVolume;
    public final float travelPitch;
    public final Sound arriveSound;
    public final float arriveVolume;
    public final float arrivePitch;

    public final boolean particlesEnabled;
    public final Particle particleType;
    public final int particleCount;
    public final double particleSpread;

    public final boolean titlesEnabled;
    public final int titleFadeInTicks;
    public final int titleStayTicks;
    public final int titleFadeOutTicks;
    public final String teleportingText;
    public final String arrivingText;

    public final boolean freezeEnabled;
    public final boolean blockMovement;
    public final boolean blockDamage;
    public final boolean blockInteract;

    private TransitionSettings(Builder builder) {
        this.enabled = builder.enabled;
        this.modEnabled = builder.modEnabled;
        this.modChannel = builder.modChannel;
        this.handshakeTimeoutTicks = builder.handshakeTimeoutTicks;
        this.peakAltitude = builder.peakAltitude;
        this.pullTicks = builder.pullTicks;
        this.holdTicks = builder.holdTicks;
        this.travelTicks = builder.travelTicks;
        this.pushTicks = builder.pushTicks;
        this.passthroughDelayTicks = builder.passthroughDelayTicks;
        this.soundsEnabled = builder.soundsEnabled;
        this.stepSound = builder.stepSound;
        this.stepVolume = builder.stepVolume;
        this.stepPitch = builder.stepPitch;
        this.travelSound = builder.travelSound;
        this.travelVolume = builder.travelVolume;
        this.travelPitch = builder.travelPitch;
        this.arriveSound = builder.arriveSound;
        this.arriveVolume = builder.arriveVolume;
        this.arrivePitch = builder.arrivePitch;
        this.particlesEnabled = builder.particlesEnabled;
        this.particleType = builder.particleType;
        this.particleCount = builder.particleCount;
        this.particleSpread = builder.particleSpread;
        this.titlesEnabled = builder.titlesEnabled;
        this.titleFadeInTicks = builder.titleFadeInTicks;
        this.titleStayTicks = builder.titleStayTicks;
        this.titleFadeOutTicks = builder.titleFadeOutTicks;
        this.teleportingText = builder.teleportingText;
        this.arrivingText = builder.arrivingText;
        this.freezeEnabled = builder.freezeEnabled;
        this.blockMovement = builder.blockMovement;
        this.blockDamage = builder.blockDamage;
        this.blockInteract = builder.blockInteract;
    }

    public int totalTicks() {
        return pullTicks + holdTicks + travelTicks + holdTicks + pushTicks;
    }

    /**
     * Parse TransitionSettings from a FileConfiguration.
     */
    public static TransitionSettings fromConfig(FileConfiguration config) {
        Builder b = new Builder();

        b.enabled = config.getBoolean("enabled", true);

        b.modEnabled = config.getBoolean("mod.enabled", true);
        b.modChannel = config.getString("mod.channel", "grandteleport:camera");
        b.handshakeTimeoutTicks = config.getInt("mod.handshake_timeout_ticks", 60);

        b.peakAltitude = config.getInt("camera.peak_altitude", 100);
        b.pullTicks = config.getInt("camera.pull_ticks", 20);
        b.holdTicks = config.getInt("camera.hold_ticks", 10);
        b.travelTicks = config.getInt("camera.travel_ticks", 30);
        b.pushTicks = config.getInt("camera.push_ticks", 20);

        b.passthroughDelayTicks = config.getInt("passthrough.delay_ticks", 30);

        b.soundsEnabled = config.getBoolean("sounds.enabled", true);
        b.stepSound = parseSound(config.getString("sounds.step", "BLOCK_RESPAWN_ANCHOR_CHARGE"));
        b.stepVolume = (float) config.getDouble("sounds.step_volume", 0.7);
        b.stepPitch = (float) config.getDouble("sounds.step_pitch", 0.8);
        b.travelSound = parseSound(config.getString("sounds.travel", "BLOCK_PORTAL_TRIGGER"));
        b.travelVolume = (float) config.getDouble("sounds.travel_volume", 0.6);
        b.travelPitch = (float) config.getDouble("sounds.travel_pitch", 0.5);
        b.arriveSound = parseSound(config.getString("sounds.arrive", "ENTITY_ENDERMAN_TELEPORT"));
        b.arriveVolume = (float) config.getDouble("sounds.arrive_volume", 0.8);
        b.arrivePitch = (float) config.getDouble("sounds.arrive_pitch", 1.2);

        b.particlesEnabled = config.getBoolean("particles.enabled", true);
        b.particleType = parseParticle(config.getString("particles.type", "PORTAL"));
        b.particleCount = config.getInt("particles.count", 20);
        b.particleSpread = config.getDouble("particles.spread", 1.5);

        b.titlesEnabled = config.getBoolean("titles.enabled", true);
        b.titleFadeInTicks = config.getInt("titles.fade_in_ticks", 5);
        b.titleStayTicks = config.getInt("titles.stay_ticks", 10);
        b.titleFadeOutTicks = config.getInt("titles.fade_out_ticks", 10);
        b.teleportingText = config.getString("titles.teleporting_text", "&bTeleporting...");
        b.arrivingText = config.getString("titles.arriving_text", "&aArrived!");

        b.freezeEnabled = config.getBoolean("freeze.enabled", true);
        b.blockMovement = config.getBoolean("freeze.block_movement", true);
        b.blockDamage = config.getBoolean("freeze.block_damage", true);
        b.blockInteract = config.getBoolean("freeze.block_interact", true);

        return new TransitionSettings(b);
    }

    private static Sound parseSound(String name) {
        try {
            return Sound.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Sound.BLOCK_RESPAWN_ANCHOR_CHARGE;
        }
    }

    private static Particle parseParticle(String name) {
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Particle.PORTAL;
        }
    }

    private static class Builder {
        boolean enabled, modEnabled;
        String modChannel;
        int handshakeTimeoutTicks;
        int peakAltitude, pullTicks, holdTicks, travelTicks, pushTicks;
        int passthroughDelayTicks;
        boolean soundsEnabled;
        Sound stepSound, travelSound, arriveSound;
        float stepVolume, stepPitch, travelVolume, travelPitch, arriveVolume, arrivePitch;
        boolean particlesEnabled;
        Particle particleType;
        int particleCount;
        double particleSpread;
        boolean titlesEnabled;
        int titleFadeInTicks, titleStayTicks, titleFadeOutTicks;
        String teleportingText, arrivingText;
        boolean freezeEnabled, blockMovement, blockDamage, blockInteract;
    }
}
