package com.ohtoai.grandteleport.command;

import com.ohtoai.grandteleport.GrandTeleportPlugin;
import com.ohtoai.grandteleport.config.TransitionSettings;
import com.ohtoai.grandteleport.teleport.DestinationParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /grandtp (alias /gtp) admin command handler.
 *
 * Subcommands:
 *   /gtp on               — Enable effects globally
 *   /gtp off              — Disable effects globally
 *   /gtp status           — Show current state
 *   /gtp reload           — Reload config.yml
 *   /gtp toggle <player>  — Toggle effects for a specific player (not yet implemented)
 *   /gtp test camera <cmd>    — Test coordinates-type transition
 *   /gtp test passthrough <cmd> — Test passthrough-type transition
 */
public class GtpCommand implements CommandExecutor {

    private final GrandTeleportPlugin plugin;

    public GtpCommand(GrandTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "on" -> handleOn(sender);
            case "off" -> handleOff(sender);
            case "status" -> handleStatus(sender);
            case "reload" -> handleReload(sender);
            case "test" -> handleTest(sender, args);
            default -> sendUsage(sender);
        }

        return true;
    }

    private void handleOn(CommandSender sender) {
        if (!sender.hasPermission("grandteleport.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        plugin.getConfigManager().getConfig().set("enabled", true);
        plugin.getConfigManager().save();
        sender.sendMessage(Component.text("GrandTeleport effects ENABLED.", NamedTextColor.GREEN));
    }

    private void handleOff(CommandSender sender) {
        if (!sender.hasPermission("grandteleport.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        plugin.getConfigManager().getConfig().set("enabled", false);
        plugin.getConfigManager().save();
        sender.sendMessage(Component.text("GrandTeleport effects DISABLED.", NamedTextColor.RED));
    }

    private void handleStatus(CommandSender sender) {
        if (!sender.hasPermission("grandteleport.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        boolean enabled = plugin.getConfigManager().getConfig().getBoolean("enabled", true);
        int modCount = plugin.getModPlayerCount();
        int activeCount = plugin.getActiveTransitionCount();

        sender.sendMessage(Component.text("=== GrandTeleport Status ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Effects: ", NamedTextColor.GRAY)
                .append(enabled
                        ? Component.text("ENABLED", NamedTextColor.GREEN)
                        : Component.text("DISABLED", NamedTextColor.RED)));
        sender.sendMessage(Component.text("Mod clients connected: ", NamedTextColor.GRAY)
                .append(Component.text(modCount, NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Active transitions: ", NamedTextColor.GRAY)
                .append(Component.text(activeCount, NamedTextColor.WHITE)));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("grandteleport.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        plugin.getConfigManager().reload();
        plugin.getCommandInterceptor().loadPatterns();
        sender.sendMessage(Component.text("Configuration reloaded.", NamedTextColor.GREEN));
    }

    /**
     * Test commands:
     *   /gtp test mod <cmd>      — Force camera transition via ModCameraBridge
     *   /gtp test passthrough <cmd> — Passthrough type (effect wrapper, re-dispatch)
     */
    private void handleTest(CommandSender sender, String[] args) {
        if (!sender.hasPermission("grandteleport.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command is player-only.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /gtp test <mod|passthrough> <command>", NamedTextColor.RED));
            sender.sendMessage(Component.text("  mod      — Camera transition via client mod", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  passthrough — Effect wrapper, re-dispatch command", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Example: /gtp test mod tp 100 64 200", NamedTextColor.GRAY));
            return;
        }

        String mode = args[1].toLowerCase();
        String tpCommand = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        if (!tpCommand.startsWith("/")) {
            tpCommand = "/" + tpCommand;
        }

        TransitionSettings settings = plugin.getTransitionSettings();
        if (!settings.enabled) {
            sender.sendMessage(Component.text("Effects are currently disabled. Use /gtp on first.", NamedTextColor.RED));
            return;
        }

        if (plugin.isInTransition(player)) {
            sender.sendMessage(Component.text("You are already in a transition!", NamedTextColor.RED));
            return;
        }

        switch (mode) {
            case "mod" -> {
                Location source = player.getLocation();
                Location target = DestinationParser.parse(tpCommand, player);

                if (target == null) {
                    sender.sendMessage(Component.text(
                            "Could not parse coordinates from: " + tpCommand
                            + "\nMake sure the command contains x y z coordinates.",
                            NamedTextColor.RED));
                    return;
                }

                sender.sendMessage(Component.text(
                        "[MOD] Starting camera transition to " + formatLoc(target) + "...",
                        NamedTextColor.AQUA));

                plugin.startTransition(player);
                if (settings.freezeEnabled) plugin.freezePlayer(player);

                com.ohtoai.grandteleport.teleport.ModCameraBridge modBridge =
                        new com.ohtoai.grandteleport.teleport.ModCameraBridge(plugin);
                modBridge.start(player, source, target, tpCommand, settings);
            }
            case "passthrough" -> {
                sender.sendMessage(Component.text(
                        "[PASSTHROUGH] Starting effect wrapper for: " + tpCommand,
                        NamedTextColor.YELLOW));

                plugin.startTransition(player);
                if (settings.freezeEnabled) plugin.freezePlayer(player);

                String cleanCmd = tpCommand.startsWith("/") ? tpCommand.substring(1) : tpCommand;

                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    plugin.unfreezePlayer(player);
                    plugin.endTransition(player);
                    plugin.setBypassNextCommand(player);
                    plugin.getServer().dispatchCommand(player, cleanCmd);
                }, settings.passthroughDelayTicks);
            }
            default -> sender.sendMessage(Component.text(
                    "Unknown test mode: " + mode + ". Use 'mod' or 'passthrough'.",
                    NamedTextColor.RED));
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("=== GrandTeleport Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/gtp on", NamedTextColor.YELLOW)
                .append(Component.text(" — Enable effects", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gtp off", NamedTextColor.YELLOW)
                .append(Component.text(" — Disable effects", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gtp status", NamedTextColor.YELLOW)
                .append(Component.text(" — Show status", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gtp reload", NamedTextColor.YELLOW)
                .append(Component.text(" — Reload config", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gtp test mod <command>", NamedTextColor.YELLOW)
                .append(Component.text(" — Test camera transition", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gtp test passthrough <command>", NamedTextColor.YELLOW)
                .append(Component.text(" — Test passthrough mode", NamedTextColor.GRAY)));
    }

    private String formatLoc(Location loc) {
        return String.format("%.0f, %.0f, %.0f in %s",
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getWorld() != null ? loc.getWorld().getName() : "unknown");
    }
}
