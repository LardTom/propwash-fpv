package de.lardtom.propwash.guard;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import de.lardtom.propwash.platform.Services;

import java.util.Optional;

public final class CommandScan {
    public static final String ROOT = "propwash";

    private CommandScan() {
    }

    public static <S> void run(GuardReport report, CommandNode<S> root) {
        CommandNode<S> propwash = root.getChild(ROOT);
        if (propwash == null) {
            return;
        }
        for (CommandNode<S> child : propwash.getChildren()) {
            walk(report, child, child.getName(), "/" + ROOT + " " + label(child));
        }
    }

    private static <S> void walk(GuardReport report, CommandNode<S> node, String subcommand, String path) {
        Command<S> command = node.getCommand();
        if (command != null) {
            Optional<String> mod = NamespaceGuard.modOfClass(command.getClass());
            if (NamespaceGuard.attributedForeign(mod) && !OfficialAddons.allowed(subcommand, mod.get(), loaded(mod.get()))) {
                report.add(mod.get(), GuardCategory.COMMAND, path);
                return;
            }
        }
        for (CommandNode<S> child : node.getChildren()) {
            walk(report, child, subcommand, path + " " + label(child));
        }
    }

    private static String label(CommandNode<?> node) {
        return node instanceof ArgumentCommandNode<?, ?> ? "<" + node.getName() + ">" : node.getName();
    }

    private static boolean loaded(String mod) {
        try {
            return Services.PLATFORM.isModLoaded(mod);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
