package de.lardtom.propwash.server.rules;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.tree.CommandNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleAccessTest {
    private static final String GAMERULE_NODE = "minecraft.command.gamerule";
    private static final String PRESET_NODE = "propwash.command.rules.preset";
    private static final List<String> GRAVITY = List.of("propwash:gravity_scale");
    private static final List<String> CRASH = List.of("propwash:crash_damage");
    private static final List<List<String>> RULES = List.of(GRAVITY, CRASH);
    private static final Predicate<Source> GAMEMASTER = s -> s.level() >= 2;

    private static final Source PLAYER = new Source(0, Set.of(), Set.of());
    private static final Source MODERATOR = new Source(1, Set.of(), Set.of());
    private static final Source OPERATOR = new Source(2, Set.of(), Set.of());
    private static final Source ADMIN = new Source(0, Set.of(GAMERULE_NODE), Set.of());
    private static final Source PRESET_ONLY = new Source(0, Set.of(PRESET_NODE), Set.of());
    private static final Source ADMIN_WITHOUT_CRASH = new Source(0, Set.of(GAMERULE_NODE), Set.of(GAMERULE_NODE + ".propwash:crash_damage"));

    private record Source(int level, Set<String> granted, Set<String> denied) {
        boolean check(String node, boolean fallback) {
            if (denied.contains(node)) {
                return false;
            }
            return granted.contains(node) || fallback;
        }
    }

    private static Predicate<Source> permissionMod(String node, Predicate<Source> original) {
        return s -> s.check(node, original.test(s));
    }

    private static CommandDispatcher<Source> server(boolean permissionMod) {
        CommandDispatcher<Source> dispatcher = new CommandDispatcher<>();
        Predicate<Source> gamerule = permissionMod ? permissionMod(GAMERULE_NODE, GAMEMASTER) : GAMEMASTER;
        LiteralArgumentBuilder<Source> base = LiteralArgumentBuilder.<Source>literal(RuleAccess.GAMERULE).requires(gamerule);
        base.then(rule("minecraft:keep_inventory", BoolArgumentType.bool(), permissionMod));
        base.then(rule("keep_inventory", BoolArgumentType.bool(), permissionMod));
        base.then(rule(GRAVITY.getFirst(), IntegerArgumentType.integer(0, 1000), permissionMod));
        base.then(rule(CRASH.getFirst(), BoolArgumentType.bool(), permissionMod));
        dispatcher.register(base);
        Predicate<Source> preset = permissionMod ? permissionMod(PRESET_NODE, GAMEMASTER) : GAMEMASTER;
        dispatcher.register(LiteralArgumentBuilder.<Source>literal("propwash")
                .then(LiteralArgumentBuilder.<Source>literal("rules")
                        .executes(c -> 1)
                        .then(LiteralArgumentBuilder.<Source>literal("preset")
                                .requires(preset)
                                .then(RequiredArgumentBuilder.<Source, String>argument("preset", StringArgumentType.word()).executes(c -> 1)))));
        return dispatcher;
    }

    private static <T> LiteralArgumentBuilder<Source> rule(String name, ArgumentType<T> type, boolean permissionMod) {
        Predicate<Source> self = permissionMod ? permissionMod(GAMERULE_NODE + "." + name, s -> true) : s -> true;
        Predicate<Source> value = permissionMod ? permissionMod(GAMERULE_NODE + "." + name + ".value", s -> true) : s -> true;
        return LiteralArgumentBuilder.<Source>literal(name).requires(self).executes(c -> 1)
                .then(RequiredArgumentBuilder.<Source, T>argument(RuleAccess.VALUE, type).requires(value).executes(c -> 1));
    }

    private static boolean executes(CommandDispatcher<Source> dispatcher, String command, Source source) {
        ParseResults<Source> parse = dispatcher.parse(command, source);
        CommandContextBuilder<Source> last = parse.getContext();
        while (last.getChild() != null) {
            last = last.getChild();
        }
        return !parse.getReader().canRead() && parse.getExceptions().isEmpty() && last.getCommand() != null;
    }

    @Test
    void vanillaServerFollowsOperatorLevelOfGamerule() {
        CommandNode<Source> root = server(false).getRoot();
        assertTrue(RuleAccess.mayEditAny(root, OPERATOR, RULES));
        assertTrue(RuleAccess.maySet(root, OPERATOR, GRAVITY));
        assertTrue(RuleAccess.mayApplyPreset(root, OPERATOR, RULES));
        for (Source source : List.of(PLAYER, MODERATOR, ADMIN, PRESET_ONLY)) {
            assertFalse(RuleAccess.mayEditAny(root, source, RULES), source.toString());
            assertFalse(RuleAccess.maySet(root, source, GRAVITY), source.toString());
            assertFalse(RuleAccess.mayApplyPreset(root, source, RULES), source.toString());
        }
    }

    @Test
    void adminWithGamerulePermissionMayEditWithoutOperator() {
        CommandNode<Source> root = server(true).getRoot();
        assertTrue(RuleAccess.mayEditAny(root, ADMIN, RULES));
        assertTrue(RuleAccess.maySet(root, ADMIN, GRAVITY));
        assertTrue(RuleAccess.maySet(root, ADMIN, CRASH));
        assertTrue(RuleAccess.mayApplyPreset(root, ADMIN, RULES));
        assertTrue(RuleAccess.mayEditAny(root, OPERATOR, RULES));
        assertFalse(RuleAccess.mayEditAny(root, PLAYER, RULES));
        assertFalse(RuleAccess.maySet(root, PLAYER, GRAVITY));
        assertFalse(RuleAccess.mayApplyPreset(root, PLAYER, RULES));
    }

    @Test
    void permissionModCanTakeGameruleAwayFromOperator() {
        CommandNode<Source> root = server(true).getRoot();
        Source revoked = new Source(4, Set.of(), Set.of(GAMERULE_NODE, PRESET_NODE));
        assertFalse(RuleAccess.mayEditAny(root, revoked, RULES));
        assertFalse(RuleAccess.mayApplyPreset(root, revoked, RULES));
    }

    @Test
    void deniedSingleRuleStaysLockedButOthersAreEditable() {
        CommandNode<Source> root = server(true).getRoot();
        assertTrue(RuleAccess.mayEditAny(root, ADMIN_WITHOUT_CRASH, RULES));
        assertTrue(RuleAccess.maySet(root, ADMIN_WITHOUT_CRASH, GRAVITY));
        assertFalse(RuleAccess.maySet(root, ADMIN_WITHOUT_CRASH, CRASH));
        assertFalse(RuleAccess.mayApplyPreset(root, ADMIN_WITHOUT_CRASH, RULES));
        Source deniedValue = new Source(0, Set.of(GAMERULE_NODE), Set.of(GAMERULE_NODE + ".propwash:gravity_scale.value"));
        assertFalse(RuleAccess.maySet(root, deniedValue, GRAVITY));
        assertTrue(RuleAccess.maySet(root, deniedValue, CRASH));
    }

    @Test
    void presetCommandAloneAllowsPresetsButNoSingleRules() {
        CommandNode<Source> root = server(true).getRoot();
        assertTrue(RuleAccess.mayApplyPreset(root, PRESET_ONLY, RULES));
        assertFalse(RuleAccess.mayEditAny(root, PRESET_ONLY, RULES));
        assertFalse(RuleAccess.maySet(root, PRESET_ONLY, GRAVITY));
    }

    @Test
    void shortAndQualifiedNamesAreBothAccepted() {
        CommandNode<Source> root = server(false).getRoot();
        assertTrue(RuleAccess.maySet(root, OPERATOR, List.of("minecraft:keep_inventory", "keep_inventory")));
        assertTrue(RuleAccess.maySet(root, OPERATOR, List.of("unknown", "keep_inventory")));
        assertFalse(RuleAccess.maySet(root, OPERATOR, List.of("propwash:unknown")));
    }

    @Test
    void missingCommandOrQueryOnlyRuleIsNotEditable() {
        CommandDispatcher<Source> empty = new CommandDispatcher<>();
        assertFalse(RuleAccess.mayEditAny(empty.getRoot(), OPERATOR, RULES));
        assertFalse(RuleAccess.mayApplyPreset(empty.getRoot(), OPERATOR, RULES));
        CommandDispatcher<Source> queryOnly = new CommandDispatcher<>();
        queryOnly.register(LiteralArgumentBuilder.<Source>literal(RuleAccess.GAMERULE)
                .then(LiteralArgumentBuilder.<Source>literal(GRAVITY.getFirst()).executes(c -> 1)
                        .then(RequiredArgumentBuilder.argument(RuleAccess.VALUE, IntegerArgumentType.integer()))));
        assertFalse(RuleAccess.maySet(queryOnly.getRoot(), OPERATOR, GRAVITY));
        assertFalse(RuleAccess.mayEditAny(server(false).getRoot(), OPERATOR, List.of()));
        assertFalse(RuleAccess.mayApplyPreset(empty.getRoot(), OPERATOR, List.of()));
    }

    @Test
    void redirectedGameruleNodeIsFollowed() {
        CommandDispatcher<Source> dispatcher = new CommandDispatcher<>();
        CommandNode<Source> rules = dispatcher.register(LiteralArgumentBuilder.<Source>literal("rules")
                .then(LiteralArgumentBuilder.<Source>literal(GRAVITY.getFirst())
                        .then(RequiredArgumentBuilder.<Source, Integer>argument(RuleAccess.VALUE, IntegerArgumentType.integer()).executes(c -> 1))));
        dispatcher.register(LiteralArgumentBuilder.<Source>literal(RuleAccess.GAMERULE).requires(GAMEMASTER).redirect(rules));
        assertTrue(RuleAccess.maySet(dispatcher.getRoot(), OPERATOR, GRAVITY));
        assertFalse(RuleAccess.maySet(dispatcher.getRoot(), PLAYER, GRAVITY));
        assertEquals(executes(dispatcher, "gamerule propwash:gravity_scale 5", OPERATOR), RuleAccess.maySet(dispatcher.getRoot(), OPERATOR, GRAVITY));
    }

    @Test
    void decisionMatchesWhatTheDispatcherWouldExecute() {
        List<Source> sources = List.of(PLAYER, MODERATOR, OPERATOR, ADMIN, PRESET_ONLY, ADMIN_WITHOUT_CRASH,
                new Source(4, Set.of(), Set.of(GAMERULE_NODE)));
        for (boolean permissionMod : new boolean[]{false, true}) {
            CommandDispatcher<Source> dispatcher = server(permissionMod);
            for (Source source : sources) {
                String label = source + (permissionMod ? " mit Rechte-Mod" : " ohne Rechte-Mod");
                assertEquals(executes(dispatcher, "gamerule propwash:gravity_scale 150", source),
                        RuleAccess.maySet(dispatcher.getRoot(), source, GRAVITY), label);
                assertEquals(executes(dispatcher, "gamerule propwash:crash_damage true", source),
                        RuleAccess.maySet(dispatcher.getRoot(), source, CRASH), label);
                boolean anyRule = executes(dispatcher, "gamerule propwash:gravity_scale 150", source)
                        || executes(dispatcher, "gamerule propwash:crash_damage true", source);
                assertEquals(anyRule, RuleAccess.mayEditAny(dispatcher.getRoot(), source, RULES), label);
                boolean preset = executes(dispatcher, "propwash rules preset realistic", source)
                        || executes(dispatcher, "gamerule propwash:gravity_scale 150", source)
                        && executes(dispatcher, "gamerule propwash:crash_damage true", source);
                assertEquals(preset, RuleAccess.mayApplyPreset(dispatcher.getRoot(), source, RULES), label);
                assertEquals(executes(dispatcher, "propwash rules preset realistic", source),
                        RuleAccess.runs(dispatcher.getRoot(), source, RuleAccess.PRESET), label);
            }
        }
    }
}
