package com.ohtoai.grandteleport.command;

import com.ohtoai.grandteleport.GrandTeleportPlugin;
import com.ohtoai.grandteleport.config.TransitionSettings;
import com.ohtoai.grandteleport.teleport.DestinationParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /grandtp (alias /gtp) admin command.
 *
 * Subcommands:
 *   /gtp on                    — Global enable
 *   /gtp off                   — Global disable
 *   /gtp status [player]       — Show global + optional per-player status
 *   /gtp reload                — Reload config
 *   /gtp toggle <player>       — Toggle effects for a specific player
 *   /gtp clear <player>        — Reset a player to default
 *   /gtp test mod <command>    — Test camera transition
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
            case "status" -> handleStatus(sender, args);
            case "reload" -> handleReload(sender);
            case "toggle" -> handleToggle(sender, args);
            case "clear" -> handleClear(sender, args);
            case "test" -> handleTest(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleOn(CommandSender sender) {
        if (!checkAdmin(sender)) return;
        plugin.getConfigManager().getConfig().set("enabled", true);
        plugin.getConfigManager().save();
        sender.sendMessage(Component.text("GrandTeleport effects globally ENABLED.", NamedTextColor.GREEN));
    }

    private void handleOff(CommandSender sender) {
        if (!checkAdmin(sender)) return;
        plugin.getConfigManager().getConfig().set("enabled", false);
        plugin.getConfigManager().save();
        sender.sendMessage(Component.text("GrandTeleport effects globally DISABLED.", NamedTextColor.RED));
    }

    private void handleStatus(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;

        boolean global = plugin.getConfigManager().getConfig().getBoolean("enabled", true);
        boolean def = plugin.getConfigManager().getConfig().getBoolean("default_enabled", true);
        int modCount = plugin.getModPlayerCount();
        int active = plugin.getActiveTransitionCount();

        sender.sendMessage(Component.text("=== GrandTeleport Status ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Global: ", NamedTextColor.GRAY)
                .append(global ? Component.text("ENABLED", NamedTextColor.GREEN)
                        : Component.text("DISABLED", NamedTextColor.RED)));
        sender.sendMessage(Component.text("Default: ", NamedTextColor.GRAY)
                .append(def ? Component.text("ENABLED", NamedTextColor.GREEN)
                        : Component.text("DISABLED", NamedTextColor.RED)));
        sender.sendMessage(Component.text("Mod clients: ", NamedTextColor.GRAY)
                .append(Component.text(modCount, NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Active transitions: ", NamedTextColor.GRAY)
                .append(Component.text(active, NamedTextColor.WHITE)));

        // If player specified, show their personal status
        if (args.length >= 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                return;
            }
            Boolean override = plugin.getPlayerOverride(target);
            boolean effective = plugin.isEffectEnabled(target);
            sender.sendMessage(Component.text(target.getName() + ": ", NamedTextColor.GRAY)
                    .append(effective ? Component.text("ENABLED", NamedTextColor.GREEN)
                            : Component.text("DISABLED", NamedTextColor.RED))
                    .append(Component.text(override == null ? " (default)" : " (override)", NamedTextColor.DARK_GRAY)));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!checkAdmin(sender)) return;
        plugin.getConfigManager().reload();
        plugin.getCommandInterceptor().loadPatterns();
        sender.sendMessage(Component.text("Configuration reloaded.", NamedTextColor.GREEN));
    }

    private void handleToggle(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /gtp toggle <player>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
            return;
        }
        boolean current = plugin.isEffectEnabled(target);
        plugin.setPlayerEnabled(target, !current);
        sender.sendMessage(Component.text(target.getName() + " effects: ", NamedTextColor.GRAY)
                .append((!current)
                        ? Component.text("ENABLED", NamedTextColor.GREEN)
                        : Component.text("DISABLED", NamedTextColor.RED)));
    }

    private void handleClear(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /gtp clear <player>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
            return;
        }
        plugin.setPlayerEnabled(target, null);
        boolean def = plugin.isEffectEnabled(target);
        sender.sendMessage(Component.text(target.getName() + " reset to default (", NamedTextColor.GRAY)
                .append(def ? Component.text("ENABLED", NamedTextColor.GREEN)
                        : Component.text("DISABLED", NamedTextColor.RED))
                .append(Component.text(")", NamedTextColor.GRAY)));
    }

    private void handleTest(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Player-only.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3 || !"mod".equals(args[1].toLowerCase())) {
            sender.sendMessage(Component.text("Usage: /gtp test mod <tp command>", NamedTextColor.RED));
            return;
        }

        String tpCommand = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        if (!tpCommand.startsWith("/")) tpCommand = "/" + tpCommand;

        TransitionSettings settings = plugin.getTransitionSettings();
        if (!settings.enabled) { sender.sendMessage(Component.text("Effects disabled.", NamedTextColor.RED)); return; }
        if (plugin.isInTransition(player)) { sender.sendMessage(Component.text("Already in transition.", NamedTextColor.RED)); return; }

        Location source = player.getLocation();
        Location target = DestinationParser.parse(tpCommand, player);
        if (target == null) {
            sender.sendMessage(Component.text("Could not parse coordinates.", NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("[MOD] Camera transition to " + formatLoc(target) + "...", NamedTextColor.AQUA));
        plugin.startTransition(player);
        if (settings.freezeEnabled) plugin.freezePlayer(player);
        new com.ohtoai.grandteleport.teleport.ModCameraBridge(plugin)
                .start(player, source, target, tpCommand, settings);
    }

    private boolean checkAdmin(CommandSender sender) {
        if (!sender.hasPermission("grandteleport.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("=== GrandTeleport ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/gtp on", NamedTextColor.YELLOW)
                .append(Component.text(" — Global enable", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gtp off", NamedTextColor.YELLOW)
                .append(Component.text(" — Global disable", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gtp status [player]", NamedTextColor.YELLOW)
                .append(Component.text(" — Show status", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gtp reload", NamedTextColor.YELLOW)
                .append(Component.text(" — Reload config", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gtp toggle <player>", NamedTextColor.YELLOW)
                .append(Component.text(" — Toggle per-player", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gtp clear <player>", NamedTextColor.YELLOW)
                .append(Component.text(" — Reset to default", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/gtp test mod <cmd>", NamedTextColor.YELLOW)
                .append(Component.text(" — Test transition", NamedTextColor.GRAY)));
    }

    private String formatLoc(Location loc) {
        return String.format("%.0f, %.0f, %.0f in %s",
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getWorld() != null ? loc.getWorld().getName() : "?");
    }
}
