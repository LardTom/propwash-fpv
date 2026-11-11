package de.lardtom.propwash.race.data;

import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import de.lardtom.propwash.race.CourseArgument;
import de.lardtom.propwash.race.CourseId;
import de.lardtom.propwash.race.CourseIdCommands;
import de.lardtom.propwash.race.CourseNames;
import de.lardtom.propwash.race.CourseRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseArgumentTest {
    private static final String NETHER = "minecraft:the_nether";

    @TempDir
    Path dir;

    private CourseRegistry registry() throws IOException {
        return TestWorlds.open(this.dir, TestWorlds.V052);
    }

    private static String id(CourseRegistry.Course course) {
        return CourseId.text(course.key());
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal("propwash").then(LiteralArgumentBuilder.<CommandSourceStack>literal("race")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("leaderboard").then(CourseArgument.last().executes(c -> 1)))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("laps")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("count", IntegerArgumentType.integer(0))
                                .then(CourseArgument.last().executes(c -> 1))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("mode")
                        .then(CourseArgument.word().then(LiteralArgumentBuilder.<CommandSourceStack>literal("laps").executes(c -> 1))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("name")
                        .then(CourseArgument.word().then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("name",
                                StringArgumentType.greedyString()).executes(c -> 1))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("reset").then(CourseArgument.word().executes(c -> 1)))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("delete").then(CourseArgument.last().executes(c -> 1)))));
        return dispatcher;
    }

    private static CommandContext<CommandSourceStack> parse(CommandDispatcher<CommandSourceStack> dispatcher, String command) {
        ParseResults<CommandSourceStack> parse = dispatcher.parse(command, null);
        assertTrue(parse.getExceptions().isEmpty(), command + ": " + parse.getExceptions());
        assertFalse(parse.getReader().canRead(), command + ": Rest „" + parse.getReader().getRemaining() + "“");
        assertNotNull(parse.getContext().getCommand(), command + ": nicht ausführbar");
        return parse.getContext().build(command);
    }

    @Test
    void findsCoursesByIdNameAndOldNumber() throws IOException {
        CourseRegistry registry = this.registry();
        CourseRegistry.Course turned = registry.course(2);
        CourseRegistry.Course nether = registry.course(3);
        CourseArgument.Match byId = CourseArgument.find(registry, id(turned), TestWorlds.OVERWORLD);
        assertEquals(List.of(turned), byId.courses());
        assertFalse(byId.number(), "Angabe per ID ist keine Nummer");
        assertEquals(List.of(turned), CourseArgument.find(registry, id(turned).replace("-", "").toUpperCase(), TestWorlds.OVERWORLD).courses());
        assertEquals(List.of(nether), CourseArgument.find(registry, id(nether), TestWorlds.OVERWORLD).courses(), "ID gilt in jeder Dimension");
        CourseArgument.Match byName = CourseArgument.find(registry, "  frei   GEDREHT ", TestWorlds.OVERWORLD);
        assertEquals(List.of(turned), byName.courses());
        assertFalse(byName.number());
        assertEquals(List.of(turned), CourseArgument.find(registry, "\"Frei gedreht\"", TestWorlds.OVERWORLD).courses(), "Name in Anführungszeichen");
        CourseArgument.Match byNumber = CourseArgument.find(registry, "2", TestWorlds.OVERWORLD);
        assertEquals(List.of(turned), byNumber.courses(), "alte Nummer wird noch angenommen");
        assertTrue(byNumber.number(), "alte Nummer bekommt den Hinweis");
        CourseArgument.Match unknownNumber = CourseArgument.find(registry, "7", TestWorlds.OVERWORLD);
        assertTrue(unknownNumber.courses().isEmpty());
        assertTrue(CourseArgument.find(registry, "Gibt es nicht", TestWorlds.OVERWORLD).courses().isEmpty());
        assertTrue(CourseArgument.find(registry, CourseId.text(UUID.fromString("9a7b3c2d-1e0f-4a5b-8c6d-7e8f9a0b1c2d")), TestWorlds.OVERWORLD).courses().isEmpty());
        assertTrue(CourseArgument.find(registry, "   ", TestWorlds.OVERWORLD).courses().isEmpty());
    }

    @Test
    void sameNamePrefersOwnDimensionAndOtherwiseListsAll() throws IOException {
        CourseRegistry registry = this.registry();
        CourseRegistry.Course stack = registry.course(1);
        CourseRegistry.Course turned = registry.course(2);
        CourseRegistry.Course nether = registry.course(3);
        nether.name = "Frei gedreht";
        assertEquals(List.of(turned), CourseArgument.find(registry, "Frei gedreht", TestWorlds.OVERWORLD).courses());
        assertEquals(List.of(nether), CourseArgument.find(registry, "Frei gedreht", NETHER).courses());
        stack.name = "Frei gedreht";
        CourseArgument.Match several = CourseArgument.find(registry, "Frei gedreht", TestWorlds.OVERWORLD);
        assertEquals(Set.of(stack, turned), Set.copyOf(several.courses()));
        assertEquals(null, several.single(), "gleiche Namen brauchen die ID");
        assertEquals(List.of(stack), CourseArgument.find(registry, id(stack), TestWorlds.OVERWORLD).courses());
    }

    @Test
    void suggestionsOfferIdsAndNamesButNoNumbers() throws IOException, CommandSyntaxException {
        CourseRegistry registry = this.registry();
        List<String> plain = CourseArgument.options(registry.courses(), TestWorlds.OVERWORLD, false).stream().map(CourseArgument.Option::text).toList();
        assertEquals(List.of(id(registry.course(1)), id(registry.course(2)), id(registry.course(3)), "Stapel-Kurs", "Frei gedreht"), plain);
        List<CourseArgument.Option> quoted = CourseArgument.options(registry.courses(), NETHER, true);
        assertEquals(List.of(id(registry.course(3)), id(registry.course(1)), id(registry.course(2)), "Stapel-Kurs", "\"Frei gedreht\""),
                quoted.stream().map(CourseArgument.Option::text).toList(), "eigene Dimension zuerst, Namen mit Leerzeichen in Anführungszeichen");
        for (CourseArgument.Option option : quoted) {
            String text = StringArgumentType.string().parse(new StringReader(option.text()));
            assertSame(option.course(), CourseArgument.find(registry, text, NETHER).single(), option.text());
            assertNotNull(CourseArgument.tooltip(option.course()));
        }
    }

    @Test
    void namesReadAsNumberOrIdAreNotSuggestedAndEverySuggestionHitsItsCourse() throws IOException, CommandSyntaxException {
        CourseRegistry registry = this.registry();
        CourseRegistry.Course stack = registry.course(1);
        CourseRegistry.Course turned = registry.course(2);
        CourseRegistry.Course nether = registry.course(3);
        String hex = "0123456789abcdef0123456789ABCDEF";
        stack.name = "2";
        turned.name = "07";
        nether.name = hex;
        for (String dimension : List.of(TestWorlds.OVERWORLD, NETHER)) {
            for (boolean quoted : List.of(false, true)) {
                List<CourseArgument.Option> options = CourseArgument.options(registry.courses(), dimension, quoted);
                List<String> texts = options.stream().map(CourseArgument.Option::text).toList();
                for (CourseArgument.Option option : options) {
                    String text = quoted ? StringArgumentType.string().parse(new StringReader(option.text())) : option.text();
                    CourseArgument.Match match = CourseArgument.find(registry, text, dimension);
                    assertSame(option.course(), match.single(), "Vorschlag „" + option.text() + "“ für Kurs " + option.course().id);
                    assertFalse(match.number(), "Vorschlag „" + option.text() + "“ wird als Nummer gelesen");
                }
                assertEquals(List.of(id(stack), id(turned), id(nether)).stream().sorted().toList(), texts.stream().sorted().toList(),
                        "nur die IDs, kein Name aus Ziffern oder in Form einer ID");
            }
        }
        CourseArgument.Match typed = CourseArgument.find(registry, "2", TestWorlds.OVERWORLD);
        assertSame(turned, typed.single(), "nur Ziffern bleiben eine Nummer, auch wenn ein Kurs so heißt");
        assertTrue(typed.number());
        assertSame(turned, CourseArgument.find(registry, "\"2\"", TestWorlds.OVERWORLD).single());
    }

    @Test
    void namesReadAsNumberOrIdAreNoNamesForCourseIdSuggestions() throws IOException {
        CourseRegistry registry = this.registry();
        CourseRegistry.Course stack = registry.course(1);
        CourseRegistry.Course turned = registry.course(2);
        CourseRegistry.Course nether = registry.course(3);
        String hex = "0123456789abcdef0123456789ABCDEF";
        stack.name = "2";
        turned.name = "07";
        nether.name = hex;
        assertFalse(CourseArgument.readsAsName("2"));
        assertFalse(CourseArgument.readsAsName("\"07\""));
        assertFalse(CourseArgument.readsAsName(hex));
        assertFalse(CourseArgument.readsAsName(id(turned)));
        assertTrue(CourseArgument.readsAsName("Kurs 2"));
        assertTrue(CourseArgument.readsAsName("2b"));
        List<String> idOptions = CourseIdCommands.options(registry.courses(), TestWorlds.OVERWORLD);
        assertEquals(List.of("1", "2"), idOptions, "/propwash course id schlägt die Nummern vor, aber keinen Namen, der als Nummer gelesen wird");
        stack.name = "Kurs 2";
        assertEquals(List.of("1", "Kurs 2", "2"), CourseIdCommands.options(registry.courses(), TestWorlds.OVERWORLD));
    }

    @Test
    void referenceInHelpTextsIsTheIdAndRunsWithoutTheNumberHint() throws IOException {
        CourseRegistry registry = this.registry();
        for (CourseRegistry.Course course : registry.courses()) {
            String reference = CourseArgument.reference(course.id, course.key());
            assertEquals(id(course), reference);
            CourseArgument.Match match = CourseArgument.find(registry, reference, TestWorlds.OVERWORLD);
            assertSame(course, match.single());
            assertFalse(match.number(), "Befehl aus dem Hilfetext bringt keinen Hinweis auf veraltete Nummern");
        }
        assertEquals("4", CourseArgument.reference(4, null), "ohne bekannte ID bleibt nur die Nummer");
        assertEquals("4", CourseArgument.reference(4, new UUID(0L, 0L)));
    }

    @Test
    void argumentNodesAreStringsWithSuggestions() {
        ArgumentCommandNode<CommandSourceStack, String> last = CourseArgument.last().build();
        ArgumentCommandNode<CommandSourceStack, String> word = CourseArgument.word().build();
        for (ArgumentCommandNode<CommandSourceStack, String> node : List.of(last, word)) {
            assertEquals(CourseArgument.NAME, node.getName());
            assertInstanceOf(StringArgumentType.class, node.getType());
            assertNotNull(node.getCustomSuggestions());
        }
        assertEquals(StringArgumentType.StringType.GREEDY_PHRASE, ((StringArgumentType) last.getType()).getType());
        assertEquals(StringArgumentType.StringType.QUOTABLE_PHRASE, ((StringArgumentType) word.getType()).getType());
    }

    @Test
    void commandsParseIdNameAndNumberAndTheHintNamesTheCommandWithTheId() throws IOException {
        CourseRegistry registry = this.registry();
        CourseRegistry.Course turned = registry.course(2);
        String id = id(turned);
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        for (String command : List.of("propwash race leaderboard " + id, "propwash race leaderboard Frei gedreht",
                "propwash race leaderboard \"Frei gedreht\"", "propwash race mode \"Frei gedreht\" laps", "propwash race name " + id + " Neu")) {
            CommandContext<CommandSourceStack> context = parse(dispatcher, command);
            assertSame(turned, CourseArgument.find(registry, StringArgumentType.getString(context, CourseArgument.NAME), TestWorlds.OVERWORLD).single(),
                    command);
        }
        assertEquals("/propwash race leaderboard " + id, rewrite(dispatcher, "propwash race leaderboard 2", id));
        assertEquals("/propwash race laps 3 " + id, rewrite(dispatcher, "propwash race laps 3 2", id));
        assertEquals("/propwash race mode " + id + " laps", rewrite(dispatcher, "propwash race mode 2 laps", id));
        assertEquals("/propwash race name " + id + " Neuer Name", rewrite(dispatcher, "propwash race name 2 Neuer Name", id));
        String command = "/propwash race leaderboard " + id;
        Component hint = CourseArgument.hint(command, turned);
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, hint.getContents());
        assertEquals(CourseArgument.KEY + "number_hint", contents.getKey());
        assertEquals(2, contents.getArgs().length);
        assertEquals(2, contents.getArgs()[0]);
        Component link = assertInstanceOf(Component.class, contents.getArgs()[1]);
        assertEquals(command, link.getString());
        ClickEvent click = link.getStyle().getClickEvent();
        assertEquals(new ClickEvent.CopyToClipboard(command), click, "Klick kopiert den Befehl mit der Kurs-ID");
    }

    @Test
    void nameCommandStoresTheNameWithoutSurroundingQuotes() throws IOException {
        CourseRegistry registry = this.registry();
        CourseRegistry.Course stack = registry.course(1);
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        List<List<String>> cases = List.of(
                List.of("propwash race name 1 \"Alpha Loop\"", "Alpha Loop"),
                List.of("propwash race name " + id(stack) + " 'Alpha Loop'", "Alpha Loop"),
                List.of("propwash race name \"Alpha Loop\"   \"  Beta   Run \"  ", "Beta Run"),
                List.of("propwash race name \"Beta Run\" '\"Gamma\"'", "Gamma"),
                List.of("propwash race name Gamma \"Al\\\"pha\"", "Al\"pha"),
                List.of("propwash race name " + id(stack) + " \"Alpha\" Loop", "\"Alpha\" Loop"),
                List.of("propwash race name " + id(stack) + " \"Delta", "\"Delta"));
        for (List<String> step : cases) {
            CommandContext<CommandSourceStack> context = parse(dispatcher, step.get(0));
            CourseRegistry.Course course = CourseArgument.find(registry, StringArgumentType.getString(context, CourseArgument.NAME), TestWorlds.OVERWORLD).single();
            assertSame(stack, course, step.get(0));
            registry.rename(course, StringArgumentType.getString(context, "name"));
            assertEquals(step.get(1), stack.name, step.get(0));
            assertSame(stack, CourseArgument.find(registry, StringArgumentType.getString(context, "name"), TestWorlds.OVERWORLD).single(),
                    "der Name, so wie er im Befehl stand, trifft den Kurs: " + step.get(0));
            assertSuggestionsHitTheirCourse(registry, TestWorlds.OVERWORLD);
        }
        assertEquals("Alpha Loop", CourseNames.clean("  \"Alpha   Loop\" "));
        assertEquals("x y", CourseNames.clean("\" 'x  y' \""));
        assertEquals("", CourseNames.clean("\"\""));
        assertEquals("\"", CourseNames.clean("\""));
        assertEquals("'Alpha\"", CourseNames.clean("'Alpha\""));
        assertEquals("x".repeat(CourseNames.MAX), CourseNames.clean("\"" + "x".repeat(40) + "\""));
        assertEquals("x".repeat(CourseNames.MAX - 2), CourseNames.clean("\"" + "x".repeat(CourseNames.MAX - 2) + "\"yyy"));
        for (String raw : List.of("\"Alpha Loop\"", "'\"x\"'", "\"" + "x".repeat(CourseNames.MAX - 2) + "\"yyy", "\"Al\\\"pha\"", " ' ' ")) {
            String once = CourseNames.clean(raw);
            assertEquals(once, CourseNames.clean(once), raw);
            assertEquals(once, CourseArgument.unquote(once), "gespeicherter Name steht nie in Anführungszeichen: " + raw);
        }
    }

    @Test
    void quotedNamesFromOldFilesAreCleanedOnLoad() throws IOException {
        Gson gson = new Gson();
        String json = TestWorlds.V052.replace("\"Stapel-Kurs\"", gson.toJson("\"Alpha Loop\"")).replace("\"Frei gedreht\"", gson.toJson("'Alpha'"));
        CourseRegistry registry = TestWorlds.open(this.dir, json);
        assertEquals("Alpha Loop", registry.course(1).name);
        assertEquals("Alpha", registry.course(2).name);
        assertSuggestionsHitTheirCourse(registry, TestWorlds.OVERWORLD);
    }

    @Test
    void quotedNamesAreSuggestedOnlyAsTheyAreReadAndDeleteOrResetNeverHitAnotherCourse() throws IOException, CommandSyntaxException {
        Gson gson = new Gson();
        List<List<String>> pairs = List.of(List.of("'Alpha'", "Alpha"), List.of("\"Alpha\"", "Alpha"), List.of("\"x y\"", "x y"),
                List.of("\"Alpha Loop\"", "Zwei"), List.of("'2'", "Zwei"));
        int run = 0;
        for (List<String> pair : pairs) {
            for (boolean fromFile : List.of(true, false)) {
                Path world = this.dir.resolve("w" + run++);
                CourseRegistry registry;
                if (fromFile) {
                    registry = TestWorlds.open(world, TestWorlds.V052.replace("\"Stapel-Kurs\"", gson.toJson(pair.get(0)))
                            .replace("\"Frei gedreht\"", gson.toJson(pair.get(1))));
                } else {
                    registry = TestWorlds.open(world, TestWorlds.V052);
                    registry.course(1).name = pair.get(0);
                    registry.course(2).name = pair.get(1);
                }
                String label = pair + (fromFile ? " aus der Datei" : " ungereinigt");
                CourseRegistry.Course first = registry.course(1);
                CourseRegistry.Course second = registry.course(2);
                assertSuggestionsHitTheirCourse(registry, TestWorlds.OVERWORLD);
                assertSuggestionsHitTheirCourse(registry, NETHER);
                for (String typed : List.of(pair.get(0), pair.get(1), StringArgumentType.escapeIfRequired(pair.get(0)))) {
                    for (String command : List.of("propwash race delete " + typed, "propwash race reset " + typed)) {
                        ParseResults<CommandSourceStack> parse = dispatcher().parse(command, null);
                        if (!parse.getExceptions().isEmpty() || parse.getReader().canRead() || parse.getContext().getCommand() == null) {
                            continue;
                        }
                        String argument = StringArgumentType.getString(parse.getContext().build(command), CourseArgument.NAME);
                        CourseArgument.Match match = CourseArgument.find(registry, argument, TestWorlds.OVERWORLD);
                        CourseRegistry.Course hit = match.single();
                        if (hit == null || match.number()) {
                            continue;
                        }
                        String wanted = CourseNames.clean(argument).toLowerCase(Locale.ROOT);
                        for (CourseRegistry.Course other : List.of(first, second)) {
                            if (other != hit) {
                                assertFalse(CourseNames.clean(other.name).toLowerCase(Locale.ROOT).equals(wanted),
                                        label + ": /" + command + " trifft Kurs " + hit.id + ", obwohl Kurs " + other.id + " genauso heißt");
                            }
                        }
                        assertEquals(wanted, CourseNames.clean(hit.name).toLowerCase(Locale.ROOT), label + ": /" + command + " trifft Kurs " + hit.id);
                    }
                }
                List<String> idOptions = CourseIdCommands.options(registry.courses(), TestWorlds.OVERWORLD);
                for (String option : idOptions) {
                    assertFalse(CourseIdCommands.find(registry, option, TestWorlds.OVERWORLD).isEmpty(),
                            label + ": /propwash course id " + option + " findet keinen Kurs");
                }
                if (CourseNames.clean(pair.get(0)).equalsIgnoreCase(CourseNames.clean(pair.get(1)))) {
                    for (String typed : List.of(pair.get(0), pair.get(1))) {
                        CourseArgument.Match match = CourseArgument.find(registry, typed, TestWorlds.OVERWORLD);
                        assertEquals(Set.of(first, second), Set.copyOf(match.courses()), label + ": „" + typed + "“ ist mehrdeutig");
                        assertEquals(null, match.single(), label + ": „" + typed + "“ braucht die ID");
                    }
                    assertEquals(List.of(id(first), id(second), id(registry.course(3))),
                            CourseArgument.options(registry.courses(), TestWorlds.OVERWORLD, true).stream().map(CourseArgument.Option::text).toList(),
                            label + ": ein mehrdeutiger Name wird nicht vorgeschlagen");
                    assertEquals(List.of(id(first), id(second), id(registry.course(3))),
                            CourseArgument.options(registry.courses(), TestWorlds.OVERWORLD, false).stream().map(CourseArgument.Option::text).toList(),
                            label);
                    assertEquals(Set.of(first, second), Set.copyOf(CourseIdCommands.find(registry, pair.get(1), TestWorlds.OVERWORLD)),
                            label + ": /propwash course id listet beide");
                    assertEquals(1, idOptions.stream().filter(option -> option.equalsIgnoreCase(CourseNames.clean(pair.get(1)))).count(),
                            label + ": /propwash course id schlägt den Namen einmal vor");
                }
                if (fromFile) {
                    assertEquals(CourseNames.clean(pair.get(0)), first.name, label);
                    assertEquals(first.name, CourseArgument.unquote(first.name), label);
                }
            }
        }
        assertFalse(CourseArgument.readsAsName("\"Alpha Loop\""));
        assertFalse(CourseArgument.readsAsName("'Alpha'"));
        assertTrue(CourseArgument.readsAsName("Alpha Loop"));
        assertTrue(CourseArgument.readsAsName("\"Alpha\" Loop"));
    }

    private static void assertSuggestionsHitTheirCourse(CourseRegistry registry, String dimension) {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        for (boolean quoted : List.of(false, true)) {
            for (CourseArgument.Option option : CourseArgument.options(registry.courses(), dimension, quoted)) {
                String command = (quoted ? "propwash race reset " : "propwash race delete ") + option.text();
                String argument = StringArgumentType.getString(parse(dispatcher, command), CourseArgument.NAME);
                CourseArgument.Match match = CourseArgument.find(registry, argument, dimension);
                assertSame(option.course(), match.single(), "/" + command + " für Kurs " + option.course().id + " („" + option.course().name + "“)");
                assertFalse(match.number(), "/" + command + " wird als Nummer gelesen");
            }
        }
        for (String option : CourseIdCommands.options(registry.courses(), dimension)) {
            assertFalse(CourseIdCommands.find(registry, option, dimension).isEmpty(), "/propwash course id " + option + " findet keinen Kurs");
        }
    }

    private static String rewrite(CommandDispatcher<CommandSourceStack> dispatcher, String command, String id) {
        CommandContext<CommandSourceStack> context = parse(dispatcher, command);
        return CourseArgument.command(context.getInput(), CourseArgument.range(context), id);
    }
}
