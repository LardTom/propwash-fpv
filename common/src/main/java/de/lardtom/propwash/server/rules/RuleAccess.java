package de.lardtom.propwash.server.rules;

import com.mojang.brigadier.tree.CommandNode;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public final class RuleAccess {
    public static final String GAMERULE = "gamerule";
    public static final String VALUE = "value";
    public static final List<String> PRESET = List.of("propwash", "rules", "preset", "preset");

    private RuleAccess() {
    }

    public static <S> boolean mayEditAny(CommandNode<S> root, S source, Collection<? extends Collection<String>> rules) {
        CommandNode<S> gamerule = reach(root, source, List.of(GAMERULE));
        if (gamerule == null) {
            return false;
        }
        for (Collection<String> names : rules) {
            if (settable(gamerule, source, names)) {
                return true;
            }
        }
        return false;
    }

    public static <S> boolean maySet(CommandNode<S> root, S source, Collection<String> names) {
        CommandNode<S> gamerule = reach(root, source, List.of(GAMERULE));
        return gamerule != null && settable(gamerule, source, names);
    }

    public static <S> boolean mayApplyPreset(CommandNode<S> root, S source, Collection<? extends Collection<String>> rules) {
        if (runs(root, source, PRESET)) {
            return true;
        }
        CommandNode<S> gamerule = reach(root, source, List.of(GAMERULE));
        if (gamerule == null || rules.isEmpty()) {
            return false;
        }
        for (Collection<String> names : rules) {
            if (!settable(gamerule, source, names)) {
                return false;
            }
        }
        return true;
    }

    public static <S> boolean runs(CommandNode<S> root, S source, List<String> path) {
        CommandNode<S> node = reach(root, source, path);
        return node != null && node != root && node.getCommand() != null;
    }

    private static <S> boolean settable(CommandNode<S> gamerule, S source, Collection<String> names) {
        for (String name : names) {
            if (runs(gamerule, source, List.of(name, VALUE))) {
                return true;
            }
        }
        return false;
    }

    static <S> @Nullable CommandNode<S> reach(CommandNode<S> from, S source, List<String> path) {
        CommandNode<S> node = from;
        for (String name : path) {
            CommandNode<S> parent = node.getRedirect() != null ? node.getRedirect() : node;
            CommandNode<S> child = parent.getChild(name);
            if (child == null || !child.canUse(source)) {
                return null;
            }
            node = child;
        }
        return node;
    }
}
