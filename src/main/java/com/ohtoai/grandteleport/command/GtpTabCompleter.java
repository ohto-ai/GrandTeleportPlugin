package com.ohtoai.grandteleport.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab completion for /grandtp (alias /gtp).
 */
public class GtpTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "on", "off", "status", "reload", "test"
    );

    private static final List<String> TEST_MODES = List.of("mod");

    private static final List<String> EMPTY = List.of();

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String label,
                                                 String @NotNull [] args) {
        if (!sender.hasPermission("grandteleport.admin")) {
            return EMPTY;
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> results = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(partial)) {
                    results.add(sub);
                }
            }
            return results;
        }

        if (args.length == 2 && "test".equalsIgnoreCase(args[0])) {
            String partial = args[1].toLowerCase();
            List<String> results = new ArrayList<>();
            for (String mode : TEST_MODES) {
                if (mode.startsWith(partial)) {
                    results.add(mode);
                }
            }
            return results;
        }

        // For args.length >= 3 with "test camera|passthrough",
        // suggest teleport-like commands as hints
        if (args.length >= 3 && "test".equalsIgnoreCase(args[0])) {
            String partial = (args.length == 3) ? args[2].toLowerCase() : "";
            List<String> suggestions = List.of("tp", "teleport");
            List<String> results = new ArrayList<>();
            for (String s : suggestions) {
                if (s.startsWith(partial)) {
                    results.add(s);
                }
            }
            if (!results.isEmpty()) {
                return results;
            }
        }

        return EMPTY;
    }
}
