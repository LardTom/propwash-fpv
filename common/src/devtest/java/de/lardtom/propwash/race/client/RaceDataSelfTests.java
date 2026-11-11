package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.race.BoardEntry;
import de.lardtom.propwash.api.race.BoardQuery;
import de.lardtom.propwash.api.race.BoardSnapshot;
import de.lardtom.propwash.api.race.CourseInfo;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.RaceDataJoinCheck;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleRaceListener;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.RaceCommands;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.export.RaceDataMirror;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class RaceDataSelfTests {
    private static final String GROUP = "race-data: ";
    private static final String GERMAN = "de_de";
    private static final String NAME = "Renndaten Test";
    private static final String REBUILT = "Renndaten Umbau";
    private static final String MISSING = "Gibt es nicht";
    private static final long MS = 1_000_000L;
    private static final AtomicReference<Site> SITE = new AtomicReference<>();
    private static final AtomicReference<String> RESULT = new AtomicReference<>("");
    private static final AtomicInteger ARCHIVED = new AtomicInteger();
    private static final List<List<String>> COURSE_ARGUMENTS = List.of(List.of("leaderboard"), List.of("archive"), List.of("delete"),
            List.of("laps", "count"), List.of("mode"), List.of("name"), List.of("reset"), List.of("remove"));

    private record Site(int course, UUID key, Vec3 origin, List<Integer> gates) {
    }

    private record Pending(CompletableFuture<Void> future, AtomicReference<String> result) {
    }

    private RaceDataSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Kurs mit dem Kurs-Werkzeug anlegen – neue Kurs-ID am Server und beim Client", 600, RaceDataSelfTests::setupStep);
        SelfTest.clientStep(GROUP + "Kurs-Fenster zeigt die ID, „Kopieren“ legt sie in die Zwischenablage", 300, RaceDataSelfTests::windowStep);
        SelfTest.clientStep(GROUP + "ID bleibt bei Umbenennen, Rundenzahl, Umbau, Verschieben und neuer Layout-Nummer", 600,
                RaceDataSelfTests::stableStep);
        SelfTest.clientStep(GROUP + "Befehl /propwash course id mit Nummer, Name und ohne Angabe – ID zum Anklicken", 400,
                RaceDataSelfTests::commandStep);
        SelfTest.clientStep(GROUP + "Befehle /propwash race … nehmen Kurs-ID und Name mit Vorschlägen, alte Nummer mit Hinweis auf die ID", 400,
                RaceDataSelfTests::raceCommandStep);
        SelfTest.clientStep(GROUP + "leere Bestenliste nennt den Archiv-Befehl mit der Kurs-ID, er läuft ohne Hinweis auf die Nummer", 400,
                RaceDataSelfTests::archiveHintStep);
        SelfTest.clientStep(GROUP + "zwei Piloten fliegen – Schnittstelle, Ereignisse im Beispiel und JSON-Spiegel unter der Kurs-ID", 900,
                RaceDataSelfTests::dataStep);
        RaceDataBoardSelfTests.register();
        if (Screenshots.enabled()) {
            shots("en", "");
            String back = GROUP + "Bildschirmfotos Sprache zurückstellen";
            SelfTest.switchStep(GROUP + "Bildschirmfotos Sprache Deutsch", back, 2400, ctx -> Screenshots.language(ctx, GERMAN));
            shots("de", " deutsch");
            SelfTest.clientStep(back, 2400, Screenshots::restoreLanguage);
        }
        SelfTest.clientStep(GROUP + "Bestenliste zurückstellen", 100, RaceDataBoardSelfTests::cleanupStep);
        SelfTest.clientStep(GROUP + "Aufräumen", 300, RaceDataSelfTests::cleanupStep);
    }

    static int siteCourse() {
        Site site = SITE.get();
        return site == null ? -1 : site.course();
    }

    private static void shots(String language, String label) {
        Screenshots.step(GROUP + "Bildschirmfoto Kurs-Fenster mit Kurs-ID" + label, "race_data_course_window_" + language, 20, new WindowScene());
        Screenshots.step(GROUP + "Bildschirmfoto Befehl /propwash course id" + label, "race_data_command_" + language, 30, new CommandScene());
        RaceDataBoardSelfTests.shots(language, label);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static @Nullable StepResult once(SelfTest.StepContext ctx, String key, Function<MinecraftServer, String> action) {
        Object stored = ctx.state().get(key);
        if (stored == null) {
            AtomicReference<String> result = new AtomicReference<>();
            CompletableFuture<Void> future = ctx.onServer(server -> result.set(action.apply(server)));
            ctx.state().put(key, new Pending(future, result));
            return StepResult.pending();
        }
        Pending pending = (Pending) stored;
        if (pending.future().isCompletedExceptionally()) {
            return StepResult.fail(key + ": " + pending.future().exceptionNow());
        }
        if (!pending.future().isDone()) {
            return StepResult.pending();
        }
        String problem = pending.result().get();
        return problem == null ? null : StepResult.fail(key + ": " + problem);
    }

    private static CourseRegistry.@Nullable Course course(Site site) {
        CourseRegistry registry = CourseRegistry.get();
        return registry == null ? null : registry.course(site.course());
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        StepResult wait = once(ctx, "Aufbau", server -> {
            ServerPlayer player = player(server);
            Vec3 origin = RaceDataJoinCheck.origin(player);
            List<GateEntity> spawned = new ArrayList<>();
            CourseRegistry.Course course = RaceDataJoinCheck.build(player, origin, spawned);
            if (course == null) {
                return "Kurs nicht angelegt";
            }
            CourseRegistry registry = CourseRegistry.get();
            registry.rename(course, NAME);
            UUID key = course.key();
            if (key.version() != 4 || registry.course(key) != course) {
                return "Kurs-ID " + key + " nicht zufällig oder nicht auffindbar";
            }
            List<Integer> ids = new ArrayList<>();
            for (GateEntity gate : spawned) {
                ids.add(gate.getId());
            }
            SITE.set(new Site(course.id, key, origin, ids));
            return null;
        });
        if (wait != null) {
            return wait;
        }
        Site site = SITE.get();
        CourseData data = ClientCourses.get(site.course());
        if (data == null || !site.key().equals(data.key()) || !NAME.equals(data.name())) {
            return ctx.tick() > 400 ? StepResult.fail("Kurs " + site.course() + " kommt beim Client nicht mit ID an: " + data) : StepResult.pending();
        }
        ctx.log("Kurs " + site.course() + " mit ID " + site.key());
        return StepResult.pass();
    }

    private static @Nullable CourseEditScreen screen() {
        return Minecraft.getInstance().gui.screen() instanceof CourseEditScreen screen ? screen : null;
    }

    private static boolean click(CourseEditScreen screen, FlatButton button) {
        double x = button.getX() + button.getWidth() / 2.0;
        double y = button.getY() + button.getHeight() / 2.0;
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        boolean taken = screen.mouseClicked(event, false);
        screen.mouseReleased(event);
        return taken;
    }

    private static StepResult windowStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        Minecraft mc = Minecraft.getInstance();
        CourseEditScreen screen = screen();
        if (screen == null) {
            if (ctx.state().containsKey("geöffnet")) {
                return ctx.tick() > 100 ? StepResult.fail("Kurs-Fenster öffnet nicht") : StepResult.pending();
            }
            ctx.state().put("geöffnet", true);
            CourseEditScreen.open(null, site.course());
            return StepResult.pending();
        }
        if (!ctx.state().containsKey("geklickt")) {
            if (!site.key().toString().equals(screen.courseKey())) {
                return StepResult.fail("Kurs-Fenster zeigt " + screen.courseKey() + " statt " + site.key());
            }
            FlatButton copy = screen.copyButton();
            if (copy == null || !copy.active) {
                return StepResult.fail("Knopf „Kopieren“ fehlt oder ist aus");
            }
            String label = Component.translatable("propwash.race.course_edit.id.copy").getString();
            if (!label.equals(copy.getMessage().getString())) {
                return StepResult.fail("Knopf heißt " + copy.getMessage().getString());
            }
            mc.keyboardHandler.setClipboard("leer");
            if (!click(screen, copy)) {
                return StepResult.fail("Klick auf „Kopieren“ nicht angenommen");
            }
            ctx.state().put("geklickt", ctx.tick());
            return StepResult.pending();
        }
        String clipboard = mc.keyboardHandler.getClipboard();
        FlatButton copy = screen.copyButton();
        String copied = Component.translatable("propwash.race.course_edit.id.copied").getString();
        if (!site.key().toString().equals(clipboard) || !screen.copied() || copy == null || !copied.equals(copy.getMessage().getString())) {
            mc.gui.setScreen(null);
            return StepResult.fail("Zwischenablage " + clipboard + ", Knopf " + (copy == null ? "-" : copy.getMessage().getString()));
        }
        ctx.log("Zwischenablage: " + clipboard);
        mc.gui.setScreen(null);
        return StepResult.pass();
    }

    private static StepResult stableStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        StepResult wait = once(ctx, "Umbau", server -> {
            CourseRegistry registry = CourseRegistry.get();
            CourseRegistry.Course course = course(site);
            if (registry == null || course == null) {
                return "Kurs fehlt";
            }
            List<String> problems = new ArrayList<>();
            registry.rename(course, REBUILT);
            check(problems, course, site, "Umbenennen");
            registry.setLaps(course, 2);
            check(problems, course, site, "Rundenzahl");
            ServerPlayer player = player(server);
            registry.submitLap(course, player.getUUID(), player.getGameProfile().name(), "race_5", 21_000L * MS, System.currentTimeMillis());
            int layout = course.layout;
            if (!registry.moveGate(course, 0, 1)) {
                problems.add("Umbau abgelehnt");
            }
            check(problems, course, site, "Umbau");
            if (course.layout != layout + 1) {
                problems.add("Layout " + course.layout + " statt " + (layout + 1));
            }
            registry.moveGate(course, 0, 1);
            ServerLevel level = player.level();
            Entity entity = level.getEntity(site.gates().get(1));
            if (entity instanceof GateEntity gate) {
                gate.applyGeometry(GateGeometry.of(gate.getX(), gate.getY() + 0.25, gate.getZ(), gate.facingYaw(), GateSize.MEDIUM));
                registry.gateRotated(gate);
            } else {
                problems.add("Gate B fehlt");
            }
            check(problems, course, site, "Verschieben");
            CourseInfo info = PropwashApi.race().course(server, site.key()).orElse(null);
            if (info == null || info.layout() != course.layout || !REBUILT.equals(info.name()) || info.laps() != 2) {
                problems.add("Schnittstelle meldet " + info);
            }
            BoardSnapshot archived = PropwashApi.race().leaderboard(server, site.key(), BoardQuery.laps().layout(layout)).orElse(null);
            if (archived == null || archived.current() || archived.entries().stream().noneMatch(e -> e.player().equals(player.getUUID()))) {
                problems.add("archivierte Bestenliste „Layout " + layout + "“ nicht unter der ID: " + archived);
            }
            RESULT.set("Layout " + layout + " → " + course.layout + ", ID " + course.key());
            return problems.isEmpty() ? null : String.join("; ", problems);
        });
        if (wait != null) {
            return wait;
        }
        CourseData data = ClientCourses.get(site.course());
        if (data == null || !site.key().equals(data.key()) || !REBUILT.equals(data.name())) {
            return ctx.tick() > 300 ? StepResult.fail("Client sieht " + data) : StepResult.pending();
        }
        ctx.log(RESULT.get());
        return StepResult.pass();
    }

    private static void check(List<String> problems, CourseRegistry.Course course, Site site, String what) {
        if (!site.key().equals(course.key())) {
            problems.add(what + " ändert die ID");
        }
    }

    private static StepResult commandStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null || mc.player == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        String key = site.key().toString();
        String missing = Component.translatable("propwash.race.course_id.not_found", MISSING).getString();
        if (!ctx.state().containsKey("vorher")) {
            UUID nearest = nearest(mc);
            if (nearest == null) {
                return StepResult.fail("kein Kurs in der Nähe des Spielers");
            }
            ctx.state().put("nächster", nearest.toString());
            ctx.state().put("vorher", RaceDataChat.copyLinks(mc, key));
            ctx.state().put("vorher nächster", RaceDataChat.copyLinks(mc, nearest.toString()));
            ctx.state().put("fehler", RaceDataChat.containing(mc, missing));
            mc.player.connection.sendCommand("propwash course id " + site.course());
            mc.player.connection.sendCommand("propwash course id " + REBUILT);
            mc.player.connection.sendCommand("propwash course id");
            mc.player.connection.sendCommand("propwash course id " + MISSING);
            return StepResult.pending();
        }
        String nearest = (String) ctx.state().get("nächster");
        int links = RaceDataChat.copyLinks(mc, key) - (int) ctx.state().get("vorher");
        int near = RaceDataChat.copyLinks(mc, nearest) - (int) ctx.state().get("vorher nächster");
        int errors = RaceDataChat.containing(mc, missing) - (int) ctx.state().get("fehler");
        int wanted = nearest.equals(key) ? 3 : 2;
        if (links < wanted || near < 1 || errors < 1) {
            return ctx.tick() > 300 ? StepResult.fail("Chat: " + links + " Zeilen mit anklickbarer ID (erwartet " + wanted + "), nächster Kurs " + near
                    + ", " + errors + " Fehlermeldungen") : StepResult.pending();
        }
        ctx.log(links + " Zeilen mit anklickbarer ID (Nummer, Name" + (wanted == 3 ? ", nächster Kurs" : "") + "), nächster Kurs " + nearest
                + ", unbekannter Name gemeldet");
        return StepResult.pass();
    }

    private static List<String> suggestions(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source, String input) {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, source)).join().getList().stream().map(Suggestion::getText).toList();
    }

    private static StepResult raceCommandStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null || mc.player == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        String key = site.key().toString();
        StepResult wait = once(ctx, "Befehlsbaum", server -> {
            CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
            List<String> problems = new ArrayList<>();
            for (List<String> path : COURSE_ARGUMENTS) {
                CommandNode<CommandSourceStack> node = dispatcher.getRoot().getChild("propwash");
                node = node == null ? null : node.getChild("race");
                for (String name : path) {
                    node = node == null ? null : node.getChild(name);
                }
                CommandNode<CommandSourceStack> course = node == null ? null : node.getChild("course");
                String label = "/propwash race " + String.join(" ", path) + " <course>";
                if (!(course instanceof ArgumentCommandNode<CommandSourceStack, ?> argument)) {
                    problems.add(label + " fehlt");
                    continue;
                }
                if (!(argument.getType() instanceof StringArgumentType)) {
                    problems.add(label + " erwartet " + argument.getType().getClass().getSimpleName() + " statt Kurs-ID oder Name");
                }
                if (argument.getCustomSuggestions() == null) {
                    problems.add(label + " ohne Vorschläge");
                }
            }
            CommandSourceStack source = server.createCommandSourceStack().withLevel(player(server).level());
            List<String> last = suggestions(dispatcher, source, "propwash race leaderboard ");
            if (!last.contains(key) || !last.contains(REBUILT)) {
                problems.add("Vorschläge zu leaderboard ohne Kurs-ID " + key + " oder Namen: " + last);
            }
            List<String> prefix = suggestions(dispatcher, source, "propwash race archive " + key.substring(0, 6));
            if (!prefix.contains(key)) {
                problems.add("Vorschläge zu archive mit Anfang der ID ohne die ID: " + prefix);
            }
            List<String> word = suggestions(dispatcher, source, "propwash race reset ");
            if (!word.contains(key) || !word.contains("\"" + REBUILT + "\"")) {
                problems.add("Vorschläge zu reset ohne Kurs-ID oder Namen in Anführungszeichen: " + word);
            }
            CourseRegistry registry = CourseRegistry.get();
            ARCHIVED.set(registry == null ? 0 : registry.records().archive(site.course()).size());
            return problems.isEmpty() ? null : String.join("; ", problems);
        });
        if (wait != null) {
            return wait;
        }
        CourseData data = ClientCourses.get(site.course());
        if (data == null) {
            return StepResult.fail("Kurs " + site.course() + " fehlt beim Client");
        }
        String header = Component.translatable("propwash.race.command.leaderboard_header", site.course(), data.gates().size()).getString();
        String archive = Component.translatable("propwash.race.command.archive_header", site.course(), ARCHIVED.get()).getString();
        String rewritten = "/propwash race leaderboard " + key;
        if (!ctx.state().containsKey("gesendet")) {
            ctx.state().put("gesendet", true);
            ctx.state().put("köpfe", RaceDataChat.containing(mc, header));
            ctx.state().put("archiv", RaceDataChat.containing(mc, archive));
            ctx.state().put("hinweise", RaceDataChat.copyLinks(mc, rewritten));
            mc.player.connection.sendCommand("propwash race leaderboard " + key);
            mc.player.connection.sendCommand("propwash race leaderboard " + REBUILT);
            mc.player.connection.sendCommand("propwash race leaderboard " + site.course());
            mc.player.connection.sendCommand("propwash race archive " + key);
            return StepResult.pending();
        }
        int heads = RaceDataChat.containing(mc, header) - (int) ctx.state().get("köpfe");
        int archives = RaceDataChat.containing(mc, archive) - (int) ctx.state().get("archiv");
        int hints = RaceDataChat.copyLinks(mc, rewritten) - (int) ctx.state().get("hinweise");
        if (heads < 3 || archives < 1 || hints < 1) {
            return ctx.tick() > 300 ? StepResult.fail("Chat: " + heads + " Bestenlisten (erwartet 3: ID, Name, Nummer), " + archives
                    + " Archive über die ID, " + hints + " Hinweise mit „" + rewritten + "“ zum Kopieren") : StepResult.pending();
        }
        if (hints != 1) {
            return StepResult.fail(hints + " Hinweise auf die Kurs-ID statt einem (nur die Angabe per Nummer bekommt ihn)");
        }
        ctx.log("Bestenliste über ID, Name und Nummer, Archiv über die ID, ein Hinweis „" + rewritten + "“; Vorschläge mit ID und Name");
        return StepResult.pass();
    }

    private static StepResult archiveHintStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null || mc.player == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        String key = site.key().toString();
        String expected = Component.translatable("propwash.race.board.empty_archived.command", key).getString();
        String command = expected.substring(expected.indexOf('/'));
        String header = Component.translatable("propwash.race.command.archive_header", site.course(), ARCHIVED.get()).getString();
        if (!ctx.state().containsKey("geöffnet")) {
            ctx.state().put("geöffnet", true);
            mc.gui.setScreen(new LeaderboardScreen(null, site.course()));
            return StepResult.pending();
        }
        if (!ctx.state().containsKey("gesendet")) {
            LeaderboardScreen screen = LeaderboardScreen.active();
            RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(site.course());
            if (screen == null || mc.gui.screen() != screen || screen.frames() < 2 || board == null || board.archived() == 0) {
                return ctx.tick() > 300 ? StepResult.fail("Bestenliste mit archiviertem Layout öffnet nicht: " + board) : StepResult.pending();
            }
            if (!board.rows().isEmpty()) {
                mc.gui.setScreen(null);
                return StepResult.fail("aktuelles Layout hat schon " + board.rows().size() + " Zeiten");
            }
            List<String> lines = new ArrayList<>();
            for (LayoutAudit.Text text : LayoutProbe.capture(mc, screen).texts()) {
                if (text.visible() != null) {
                    lines.add(text.value());
                }
            }
            mc.gui.setScreen(null);
            String shown = String.join(" ", lines);
            if (!shown.contains(command)) {
                String seen = lines.stream().filter(line -> line.contains("/propwash")).findFirst().orElse(shown);
                return StepResult.fail("leere Bestenliste zeigt „" + seen + "“ statt „" + command + "“");
            }
            ctx.state().put("gesendet", ctx.tick());
            ctx.state().put("archiv", RaceDataChat.containing(mc, header));
            ctx.state().put("hinweise", RaceDataChat.copyLinks(mc, command));
            mc.player.connection.sendCommand(command.substring(1));
            return StepResult.pending();
        }
        int archives = RaceDataChat.containing(mc, header) - (int) ctx.state().get("archiv");
        if (archives < 1) {
            return ctx.tick() > 300 ? StepResult.fail("„" + command + "“ zeigt das Archiv nicht") : StepResult.pending();
        }
        if (!ctx.state().containsKey("archiv da")) {
            ctx.state().put("archiv da", ctx.tick());
        }
        if (ctx.tick() - (int) ctx.state().get("archiv da") < 20) {
            return StepResult.pending();
        }
        int hints = RaceDataChat.copyLinks(mc, command) - (int) ctx.state().get("hinweise");
        if (hints != 0) {
            return StepResult.fail("„" + command + "“ aus der Bestenliste bringt " + hints + " Hinweise auf veraltete Nummern");
        }
        ctx.log("leere Bestenliste zeigt „" + command + "“, der Befehl zeigt das Archiv ohne Hinweis auf die Nummer");
        return StepResult.pass();
    }

    private static @Nullable UUID nearest(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        String dimension = mc.level.dimension().identifier().toString();
        double best = RaceCommands.NEAREST_COURSE_RANGE * RaceCommands.NEAREST_COURSE_RANGE;
        UUID found = null;
        for (CourseData course : ClientCourses.all()) {
            if (!course.dimension().equals(dimension) || course.key() == null) {
                continue;
            }
            for (CourseData.Gate gate : course.gates()) {
                double distance = mc.player.distanceToSqr(gate.x(), gate.y(), gate.z());
                if (distance < best) {
                    best = distance;
                    found = course.key();
                }
            }
        }
        return found;
    }

    private static StepResult dataStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        StepResult wait = once(ctx, "Runden", server -> {
            CourseRegistry.Course course = course(site);
            if (course == null) {
                return "Kurs fehlt";
            }
            CourseRegistry.get().setLaps(course, 1);
            boolean listener = JoinTestEnv.exampleAddon();
            int lapsBefore = (int) ExampleRaceListener.LAPS.stream().filter(lap -> lap.course().equals(site.key())).count();
            int racesBefore = (int) ExampleRaceListener.RACES.stream().filter(race -> race.course().equals(site.key())).count();
            ServerLevel level = player(server).level();
            List<String> names = List.of("PwDatenA", "PwDatenB");
            for (int i = 0; i < names.size(); i++) {
                ServerPlayer pilot = RaceDataJoinCheck.secondPilot(server, level, names.get(i));
                String problem = RaceDataJoinCheck.fly(course, pilot, site.origin(), (8_000L + 1_000L * i) * MS);
                if (problem != null) {
                    return names.get(i) + ": " + problem;
                }
            }
            String result;
            try {
                result = RaceDataJoinCheck.verify(server, course, names, lapsBefore, racesBefore, listener);
            } catch (IOException e) {
                return "JSON nicht lesbar: " + e;
            }
            RaceDataMirror mirror = RaceDataMirror.get();
            Path expected = server.getWorldPath(LevelResource.ROOT).normalize().resolve(RaceDataMirror.FOLDER);
            if (mirror == null || !mirror.root().equals(expected)) {
                return "JSON-Spiegel liegt nicht unter " + expected;
            }
            BoardSnapshot board = PropwashApi.race().leaderboard(server, site.key(), BoardQuery.laps()).orElse(null);
            List<String> top = board == null ? List.of() : board.entries().stream().map(BoardEntry::name).toList();
            RESULT.set(result + " top=" + top + " ordner=" + expected.getFileName());
            return result.startsWith("ok ") ? null : result;
        });
        if (wait != null) {
            return wait;
        }
        ctx.log(RESULT.get());
        return StepResult.pass();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.pass();
        }
        StepResult wait = once(ctx, "Aufräumen", server -> {
            CourseRegistry registry = CourseRegistry.get();
            if (registry != null) {
                registry.deleteCourse(site.course());
            }
            ServerLevel level = player(server).level();
            for (int id : site.gates()) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    entity.discard();
                }
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        SITE.set(null);
        return StepResult.pass();
    }

    private static final class WindowScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Site site = SITE.get();
            Minecraft mc = Minecraft.getInstance();
            if (site == null) {
                return StepResult.fail("kein Kurs für das Bildschirmfoto");
            }
            CourseEditScreen screen = screen();
            if (screen == null) {
                CourseEditScreen.open(null, site.course());
                return StepResult.pending();
            }
            if (!screen.copied()) {
                screen.copyKey();
                Screenshots.parkMouse(mc);
            }
            return StepResult.pass();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
        }
    }

    private static final class CommandScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (SITE.get() == null || mc.player == null) {
                return StepResult.fail("kein Kurs für das Bildschirmfoto");
            }
            mc.gui.setScreen(null);
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Site site = SITE.get();
            if (site == null || mc.player == null || ctx.state().containsKey("befehl")) {
                return;
            }
            ctx.state().put("befehl", true);
            mc.player.connection.sendCommand("propwash course id " + site.course());
            mc.gui.setScreen(new ChatScreen("", false));
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
        }
    }
}
