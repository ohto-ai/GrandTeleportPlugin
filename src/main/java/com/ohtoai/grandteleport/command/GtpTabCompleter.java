package com.ohtoai.grandteleport.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class GtpTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "on", "off", "default", "status", "reload", "toggle", "clear", "test"
    );

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String label,
                                                 String @NotNull [] args) {
        if (!sender.hasPermission("grandteleport.admin")) return List.of();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> results = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(partial)) results.add(sub);
            }
            return results;
        }

        // default needs on/off
        if (args.length == 2 && "default".equalsIgnoreCase(args[0])) {
            String partial = args[1].toLowerCase();
            List<String> results = new ArrayList<>();
            for (String s : List.of("on", "off")) {
                if (s.startsWith(partial)) results.add(s);
            }
            return results;
        }

        // toggle / clear / status need player names
        if (args.length == 2 && ("toggle".equalsIgnoreCase(args[0])
                || "clear".equalsIgnoreCase(args[0])
                || "status".equalsIgnoreCase(args[0]))) {
            String partial = args[1].toLowerCase();
            List<String> results = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) results.add(p.getName());
            }
            return results;
        }

        // test mod
        if (args.length == 2 && "test".equalsIgnoreCase(args[0])) {
            return List.of("mod");
        }

        return List.of();
    }
}
