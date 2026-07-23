package com.ohtoai.grandteleport.teleport;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Parses destination coordinates from vanilla teleport commands.
 * Supports:
 * - /tp x y z
 * - /tp ~x ~y ~z (relative)
 * - /tp ^x ^y ^z (local/caret, parsed but treated as absolute for camera path)
 * - /tp <player>
 * - /tp <player> <targetPlayer>
 * - /tp <x> <y> <z> [yaw] [pitch]
 */
public final class DestinationParser {

    private DestinationParser() {
    }

    /**
     * Try to extract a destination Location from a command string.
     * Returns null if the command doesn't contain parseable coordinates.
     */
    public static Location parse(String command, Player player) {
        String argumentString = getArgumentString(command);
        if (argumentString == null || argumentString.isBlank()) {
            return null;
        }

        String[] args = argumentString.split("\\s+");

        // Case 1: /tp x y z [yaw] [pitch]
        if (args.length >= 3 && isCoordinateTriple(args, 0)) {
            return readCoordinateTriple(args, 0, player);
        }

        // Case 2: /tp <targetPlayer> <x> <y> <z> — we only care about coords
        if (args.length >= 4 && isCoordinateTriple(args, 1)) {
            return readCoordinateTriple(args, 1, player);
        }

        // Case 3: /tp <targetPlayer> — look up the player's location
        if (args.length == 1) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null && target.isOnline()) {
                return target.getLocation();
            }
        }

        // Case 4: /tp <player> <targetPlayer>
        if (args.length == 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null && target.isOnline()) {
                return target.getLocation();
            }
        }

        // Scan for coordinate triples anywhere in the args
        for (int i = 0; i <= args.length - 3; i++) {
            if (isCoordinateTriple(args, i)) {
                return readCoordinateTriple(args, i, player);
            }
        }

        return null;
    }

    /**
     * Extract just the arguments after the command name.
     * e.g., "/tp 100 64 200" → "100 64 200"
     * Also handles "minecraft:tp" prefix and "execute ... run tp ..." nesting.
     */
    public static String getArgumentString(String command) {
        String normalized = command.stripLeading();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).stripLeading();
        }

        String lowerCase = normalized.toLowerCase(Locale.ROOT);

        for (String name : new String[]{"minecraft:teleport", "minecraft:tp", "teleport", "tp"}) {
            if (lowerCase.equals(name)) {
                return "";
            }
            if (lowerCase.startsWith(name + " ")) {
                return normalized.substring(name.length()).stripLeading();
            }
        }

        // Handle /execute ... run tp ...
        if (lowerCase.startsWith("execute ")) {
            int runIndex = lowerCase.indexOf(" run ");
            if (runIndex >= 0) {
                return getArgumentString(normalized.substring(runIndex + 5).stripLeading());
            }
        }

        return null;
    }

    /**
     * Check if the command looks like a teleport command (has tp/teleport).
     */
    public static boolean isTeleportCommand(String command) {
        return getArgumentString(command) != null;
    }

    // --- Coordinate parsing helpers ---

    private static boolean isCoordinateTriple(String[] args, int start) {
        return isCoordinate(args[start])
                && isCoordinate(args[start + 1])
                && isCoordinate(args[start + 2]);
    }

    private static Location readCoordinateTriple(String[] args, int start, Player player) {
        Location base = player.getLocation();
        double x = readCoordinate(args[start], base.getX());
        double y = readCoordinate(args[start + 1], base.getY());
        double z = readCoordinate(args[start + 2], base.getZ());
        return new Location(player.getWorld(), x, y, z);
    }

    public static boolean isCoordinate(String token) {
        if (token.isEmpty()) return false;

        // Caret notation (^) — local coordinates; treat as parseable but not absolute
        if (token.charAt(0) == '^') {
            return true;
        }

        // Relative (~) coordinates
        if (token.charAt(0) == '~') {
            return token.length() == 1 || isDouble(token.substring(1));
        }

        // Absolute coordinates
        return isDouble(token);
    }

    public static double readCoordinate(String token, double base) {
        if (token.charAt(0) == '~') {
            if (token.length() == 1) {
                return base;
            }
            return base + Double.parseDouble(token.substring(1));
        }

        // Caret coordinates — we just return the base + offset for camera path
        if (token.charAt(0) == '^') {
            if (token.length() == 1) {
                return base;
            }
            return base + Double.parseDouble(token.substring(1));
        }

        return Double.parseDouble(token);
    }

    private static boolean isDouble(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
