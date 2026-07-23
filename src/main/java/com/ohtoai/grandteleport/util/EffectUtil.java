package com.ohtoai.grandteleport.util;

import com.ohtoai.grandteleport.config.TransitionSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Static helpers for playing sounds, spawning particles, and sending titles.
 */
public final class EffectUtil {

    private EffectUtil() {
    }

    /**
     * Play a step sound (altitude transition) for a player.
     */
    public static void playStepSound(Player player, TransitionSettings settings) {
        if (!settings.soundsEnabled) return;
        player.playSound(
                player.getLocation(),
                settings.stepSound,
                SoundCategory.MASTER,
                settings.stepVolume,
                settings.stepPitch
        );
    }

    /**
     * Play the travel sound (horizontal movement phase) for a player.
     */
    public static void playTravelSound(Player player, TransitionSettings settings) {
        if (!settings.soundsEnabled) return;
        player.playSound(
                player.getLocation(),
                settings.travelSound,
                SoundCategory.MASTER,
                settings.travelVolume,
                settings.travelPitch
        );
    }

    /**
     * Play the arrival sound.
     */
    public static void playArriveSound(Player player, TransitionSettings settings) {
        if (!settings.soundsEnabled) return;
        player.playSound(
                player.getLocation(),
                settings.arriveSound,
                SoundCategory.MASTER,
                settings.arriveVolume,
                settings.arrivePitch
        );
    }

    /**
     * Spawn particles at a location visible to a specific player.
     */
    public static void spawnParticles(Player player, Location loc, TransitionSettings settings) {
        if (!settings.particlesEnabled) return;
        player.spawnParticle(
                settings.particleType,
                loc.getX(), loc.getY(), loc.getZ(),
                settings.particleCount,
                settings.particleSpread, settings.particleSpread, settings.particleSpread,
                0.0
        );
    }

    /**
     * Send a teleporting title to the player.
     */
    public static void sendTeleportingTitle(Player player, TransitionSettings settings) {
        if (!settings.titlesEnabled) return;
        Component text = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(settings.teleportingText);
        Title title = Title.title(
                Component.empty(),
                text,
                Title.Times.times(
                        Duration.ofMillis(settings.titleFadeInTicks * 50L),
                        Duration.ofMillis(settings.titleStayTicks * 50L),
                        Duration.ofMillis(settings.titleFadeOutTicks * 50L)
                )
        );
        player.showTitle(title);
    }

    /**
     * Send an arrival title to the player.
     */
    public static void sendArrivalTitle(Player player, TransitionSettings settings) {
        if (!settings.titlesEnabled) return;
        Component text = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(settings.arrivingText);
        Title title = Title.title(
                Component.empty(),
                text,
                Title.Times.times(
                        Duration.ofMillis(settings.titleFadeInTicks * 50L),
                        Duration.ofMillis(settings.titleStayTicks * 50L),
                        Duration.ofMillis(settings.titleFadeOutTicks * 50L)
                )
        );
        player.showTitle(title);
    }

    /**
     * Send a brief teleporting title with custom duration.
     */
    public static void sendTitle(Player player, String text, int fadeIn, int stay, int fadeOut) {
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        Title title = Title.title(
                Component.empty(),
                component,
                Title.Times.times(
                        Duration.ofMillis(fadeIn * 50L),
                        Duration.ofMillis(stay * 50L),
                        Duration.ofMillis(fadeOut * 50L)
                )
        );
        player.showTitle(title);
    }
}
