package de.lardtom.propwash.race.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.api.race.CourseMode;
import de.lardtom.propwash.dev.RaceDataJoinCheck;
import de.lardtom.propwash.dev.RaceP2pJoinCheck;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseMenu;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.widget.ContentScroll;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.TextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class CourseMenuSelfTests {
    public static final String GROUP = "course-menu: ";
    public static final String TITLE_KEY = "propwash.race.course_menu.title";
    public static final String OPEN_KEY = "propwash.race.course_menu.open";
    public static final String NAME = "Menü-Kurs";
    public static final String ENGLISH_NAME = "Forest Loop";
    public static final String GERMAN_NAME = "Waldrunde";
    private static final String GERMAN = "de_de";
    private static final long MS = 1_000_000L;
    private static final long DAY = 6000L;
    private static final long NIGHT = 18000L;
    private static final String[] PILOTS = {"Lotte", "Mika_FPV", "Jonas"};
    private static final long[] TIMES = {31_240L, 32_870L, 35_010L};
    private static final AtomicReference<Site> SITE = new AtomicReference<>();
    private static final AtomicReference<String> RESULT = new AtomicReference<>("");

    private record Site(int course, Vec3 origin, List<Integer> gates, Vec3 home, ItemStack held, GameType mode, long time) {
    }

    private record Pending(CompletableFuture<Void> future, AtomicReference<String> result) {
    }

    private CourseMenuSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Kurs mit dem Kurs-Werkzeug bauen", 600, CourseMenuSelfTests::setupStep);
        SelfTest.clientStep(GROUP + "Kurs-Werkzeug: Rechtsklick in die Luft öffnet die Kurs-Einstellungen", 300, CourseMenuSelfTests::toolStep);
        SelfTest.clientStep(GROUP + "Inhalt: Name, ID, Typ, Runden, Gates, Werkzeug, Besitzer, Rechte, Bestenliste, Geister, Zeiten, Löschen in vier Gruppen",
                200, CourseMenuSelfTests::contentStep);
        SelfTest.clientStep(GROUP + "Ändern im Menü: Name, Runden, Typ und ID kopieren – der Server übernimmt es", 900, CourseMenuSelfTests::editStep);
        SelfTest.clientStep(GROUP + "Kurs-Fenster: Knopf „Einstellungen“ öffnet die Kurs-Einstellungen", 200, CourseMenuSelfTests::windowStep);
        SelfTest.clientStep(GROUP + "Wege: Kurs-Fenster und Bestenliste hin und zurück, alter Knopf und Befehle wirken weiter", 600,
                CourseMenuSelfTests::waysStep);
        SelfTest.clientStep(GROUP + "Mausrad, Scrollbalken und Bild auf/ab bei 320×240 – nichts abgeschnitten oder überlappt", 300,
                CourseMenuSelfTests::scrollStep);
        SelfTest.clientStep(GROUP + "Nur ansehen im Abenteuermodus – nichts lässt sich ändern", 600, CourseMenuSelfTests::viewStep);
        if (Screenshots.enabled()) {
            shots("", ENGLISH_NAME, "");
            String back = GROUP + "Bildschirmfotos Sprache zurückstellen";
            SelfTest.switchStep(GROUP + "Bildschirmfotos Sprache Deutsch", back, 2400, ctx -> Screenshots.language(ctx, GERMAN));
            Screenshots.step(GROUP + "Bildschirmfoto Kurs-Einstellungen hell deutsch", "course_menu_de", 30, new MenuScene(DAY, false, GERMAN_NAME));
            Screenshots.step(GROUP + "Bildschirmfoto Kurs-Einstellungen nur ansehen deutsch", "course_menu_view_de", 30,
                    new MenuScene(DAY, true, GERMAN_NAME));
            SelfTest.clientStep(back, 2400, Screenshots::restoreLanguage);
        }
        SelfTest.clientStep(GROUP + "Alle Zeiten löschen nur mit Rückfrage – wie /propwash race reset", 600, CourseMenuSelfTests::resetStep);
        SelfTest.clientStep(GROUP + "Kurs löschen nur mit Rückfrage – die Gates bleiben stehen", 400, CourseMenuSelfTests::deleteStep);
        SelfTest.clientStep(GROUP + "Aufräumen", 300, CourseMenuSelfTests::cleanupStep);
    }

    private static void shots(String suffix, String name, String label) {
        Screenshots.step(GROUP + "Bildschirmfoto Kurs-Einstellungen hell" + label, "course_menu" + suffix, 30, new MenuScene(DAY, false, name));
        Screenshots.step(GROUP + "Bildschirmfoto Kurs-Einstellungen dunkel" + label, "course_menu_night" + suffix, 30, new MenuScene(NIGHT, false, name));
        Screenshots.step(GROUP + "Bildschirmfoto Kurs-Einstellungen GUI-Skala 2" + label, "course_menu_gui2" + suffix, 30,
                Screenshots.guiScale(2, new MenuScene(DAY, false, name)));
        Screenshots.step(GROUP + "Bildschirmfoto Kurs-Einstellungen GUI-Skala 4" + label, "course_menu_gui4" + suffix, 30,
                Screenshots.guiScale(4, new MenuScene(DAY, false, name)));
        Screenshots.step(GROUP + "Bildschirmfoto Kurs-Einstellungen nur ansehen" + label, "course_menu_view" + suffix, 30, new MenuScene(DAY, true, name));
    }

    private static void setTime(MinecraftServer server, long time) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set " + time);
    }

    private static void seedTimes(CourseRegistry registry, CourseRegistry.Course course) {
        for (int i = 0; i < PILOTS.length; i++) {
            UUID pilot = UUID.nameUUIDFromBytes(("propwash-menu-" + PILOTS[i]).getBytes(StandardCharsets.UTF_8));
            registry.submitLap(course, pilot, PILOTS[i], i == 1 ? "race_7" : "race_5", TIMES[i] * MS, System.currentTimeMillis() - i * 86_400_000L);
        }
    }

    private static boolean press(CourseMenuScreen menu, @Nullable AbstractWidget widget) {
        if (widget == null) {
            return false;
        }
        menu.setFocused(widget);
        return click(menu, widget);
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

    private static String title() {
        return Component.translatable(TITLE_KEY).getString();
    }

    private static String shown(@Nullable Screen screen) {
        return screen == null ? "nichts" : "„" + screen.getTitle().getString() + "“ (" + screen.getClass().getSimpleName() + ")";
    }

    private static boolean click(Screen screen, @Nullable AbstractWidget button) {
        if (button == null) {
            return false;
        }
        double x = button.getX() + button.getWidth() / 2.0;
        double y = button.getY() + button.getHeight() / 2.0;
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        boolean taken = screen.mouseClicked(event, false);
        screen.mouseReleased(event);
        return taken;
    }

    private static @Nullable FlatButton button(Screen screen, String label) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof FlatButton button && label.equals(button.getMessage().getString())) {
                return button;
            }
        }
        return null;
    }

    private static @Nullable CourseMenuScreen menu(Site site) {
        return Minecraft.getInstance().gui.screen() instanceof CourseMenuScreen menu && menu.courseId() == site.course() ? menu : null;
    }

    private static CourseMenuScreen openMenu(Site site) {
        CourseMenuScreen menu = menu(site);
        if (menu == null) {
            CourseMenuScreen.open(null, site.course());
            menu = (CourseMenuScreen) Minecraft.getInstance().gui.screen();
        }
        return menu;
    }

    private static int stage(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault("stage", 0);
    }

    private static StepResult next(SelfTest.StepContext ctx) {
        ctx.state().put("stage", stage(ctx) + 1);
        ctx.state().put("since", ctx.tick());
        return StepResult.pending();
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault("since", 0);
    }

    private static StepResult waitFor(SelfTest.StepContext ctx, boolean done, int ticks, String problem) {
        if (done) {
            return next(ctx);
        }
        return since(ctx) > ticks ? StepResult.fail(problem) : StepResult.pending();
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        StepResult wait = once(ctx, "Aufbau", server -> {
            ServerPlayer player = player(server);
            Vec3 origin = RaceDataJoinCheck.origin(player).add(0.0, 0.0, 40.0);
            List<GateEntity> spawned = new ArrayList<>();
            CourseRegistry.Course course = RaceP2pJoinCheck.build(player, origin, spawned);
            CourseRegistry registry = CourseRegistry.get();
            if (course == null || registry == null) {
                return "Kurs nicht angelegt";
            }
            registry.rename(course, NAME);
            registry.setLaps(course, 3);
            seedTimes(registry, course);
            List<Integer> ids = new ArrayList<>();
            for (GateEntity gate : spawned) {
                ids.add(gate.getId());
            }
            SITE.set(new Site(course.id, origin, ids, player.position(), player.getMainHandItem().copy(), player.gameMode.getGameModeForPlayer(),
                    server.overworld().getOverworldClockTime()));
            return null;
        });
        if (wait != null) {
            return wait;
        }
        Site site = SITE.get();
        CourseData data = ClientCourses.get(site.course());
        if (data == null || data.gates().size() != 3 || !NAME.equals(data.name())) {
            return ctx.tick() > 400 ? StepResult.fail("Kurs kommt beim Client nicht an: " + data) : StepResult.pending();
        }
        ctx.log("Kurs " + site.course() + " „" + NAME + "“ mit drei Gates und " + PILOTS.length + " Zeiten");
        return StepResult.pass();
    }

    private static StepResult toolStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        StepResult wait = once(ctx, "Werkzeug", server -> {
            ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
            tool.set(RaceModule.COURSE.get(), site.course());
            ServerPlayer player = player(server);
            player.setShiftKeyDown(false);
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            return null;
        });
        if (wait != null) {
            return wait;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            return StepResult.pending();
        }
        if (!ctx.state().containsKey("geklickt")) {
            if (CourseToolItem.courseOf(player.getMainHandItem()) != site.course()) {
                return ctx.tick() > 100 ? StepResult.fail("Kurs-Werkzeug kommt nicht in die Hand") : StepResult.pending();
            }
            mc.gui.setScreen(null);
            player.setShiftKeyDown(false);
            player.setXRot(-90.0F);
            player.xRotO = -90.0F;
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            ctx.state().put("geklickt", ctx.tick());
            return StepResult.pending();
        }
        Screen screen = mc.gui.screen();
        if (screen == null || !title().equals(screen.getTitle().getString())) {
            if (ctx.tick() - (int) ctx.state().get("geklickt") < 80) {
                return StepResult.pending();
            }
            return StepResult.fail("Rechtsklick in die Luft öffnet " + shown(screen) + " statt „" + title() + "“");
        }
        if (!(screen instanceof CourseMenuScreen menu) || menu.courseId() != site.course() || menu.parent() != null) {
            return StepResult.fail("falsches Menü: " + shown(screen));
        }
        mc.gui.setScreen(null);
        ctx.log("Rechtsklick in die Luft: " + shown(screen) + " für Kurs " + site.course());
        return StepResult.pass();
    }

    private static StepResult contentStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        CourseMenuScreen menu = openMenu(site);
        if (ClientLeaderboards.get(site.course()) == null || menu.frames() < 3) {
            return ctx.tick() > 150 ? StepResult.fail("Bestenliste kommt nicht an") : StepResult.pending();
        }
        List<String> problems = new ArrayList<>();
        if (!menu.sections().equals(List.of(CourseMenu.Section.values()))) {
            problems.add("Gruppen " + menu.sections());
        }
        if (!menu.rows().equals(CourseMenu.rows(false))) {
            problems.add("Zeilen " + menu.rows());
        }
        CourseMenu.Rights rights = menu.rights();
        if (!rights.edit() || rights.role() != CourseMenu.Role.OWNER) {
            problems.add("Erbauer darf nicht ändern: " + rights);
        }
        for (CourseMenu.Row row : List.of(CourseMenu.Row.NAME, CourseMenu.Row.KEY, CourseMenu.Row.MODE, CourseMenu.Row.LAPS, CourseMenu.Row.GATES,
                CourseMenu.Row.BIND, CourseMenu.Row.BOARD, CourseMenu.Row.RESET, CourseMenu.Row.DELETE)) {
            AbstractWidget control = menu.control(row);
            if (control == null) {
                problems.add("kein Bedienelement für " + row);
            } else if (!(control instanceof TextField) && control.active != CourseMenu.active(row, rights)) {
                problems.add(row + " aktiv=" + control.active);
            }
        }
        TextField name = menu.nameField();
        if (name == null || !NAME.equals(name.getValue())) {
            problems.add("Name im Feld: " + (name == null ? "-" : name.getValue()));
        }
        CourseData course = ClientCourses.get(site.course());
        String key = course == null || course.key() == null ? "" : course.key().toString();
        if (key.isEmpty() || !key.equals(menu.courseKey())) {
            problems.add("ID fehlt");
        }
        if (menu.shownLaps() != 3) {
            problems.add("Runden " + menu.shownLaps());
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        List<LayoutAudit.Finding> findings = LayoutProbe.audit(mc, menu);
        if (!findings.isEmpty()) {
            return StepResult.fail(findings.size() + " Befunde in den Kurs-Einstellungen: " + findings.getFirst().describe());
        }
        mc.gui.setScreen(null);
        ctx.log("Gruppen " + menu.sections() + ", Zeilen " + menu.rows().size() + ", ID " + key + ", Rechte " + rights.role());
        return StepResult.pass();
    }

    private static StepResult editStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        CourseMenuScreen menu = openMenu(site);
        CourseData course = ClientCourses.get(site.course());
        if (course == null) {
            return StepResult.fail("Kurs fehlt");
        }
        switch (stage(ctx)) {
            case 0 -> {
                TextField name = menu.nameField();
                if (name == null || !name.active) {
                    return StepResult.fail("Namensfeld fehlt");
                }
                menu.setFocused(name);
                name.setValue("");
                for (char c : ENGLISH_NAME.toCharArray()) {
                    name.charTyped(new CharacterEvent(c));
                }
                menu.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                return next(ctx);
            }
            case 1 -> {
                return waitFor(ctx, ENGLISH_NAME.equals(course.name()), 100, "Name kommt nicht an: " + course.name());
            }
            case 2 -> {
                ctx.state().put("runden", course.laps());
                if (!press(menu, menu.lapsPlus())) {
                    return StepResult.fail("Klick auf + nicht angenommen");
                }
                return next(ctx);
            }
            case 3 -> {
                int before = (int) ctx.state().get("runden");
                return waitFor(ctx, course.laps() == before + 1 && menu.shownLaps() == before + 1, 120, "Runden " + course.laps() + " statt " + (before + 1));
            }
            case 4 -> {
                if (!press(menu, menu.pointModeButton())) {
                    return StepResult.fail("Klick auf „Punkt zu Punkt“ nicht angenommen");
                }
                return next(ctx);
            }
            case 5 -> {
                boolean switched = course.mode() == CourseMode.POINT_TO_POINT && menu.rows().contains(CourseMenu.Row.FINISH)
                        && !menu.rows().contains(CourseMenu.Row.LAPS) && menu.lapsPlus() == null;
                return waitFor(ctx, switched, 120, "Typ Punkt zu Punkt kommt nicht an: " + course.mode() + " " + menu.rows());
            }
            case 6 -> {
                if (!press(menu, menu.lapsModeButton())) {
                    return StepResult.fail("Klick auf „Runden“ nicht angenommen");
                }
                return next(ctx);
            }
            case 7 -> {
                return waitFor(ctx, course.mode() == CourseMode.LAPS && menu.rows().contains(CourseMenu.Row.LAPS), 120, "zurück auf Runden fehlt");
            }
            case 8 -> {
                mc.keyboardHandler.setClipboard("");
                if (!press(menu, menu.copyButton())) {
                    return StepResult.fail("Klick auf „Kopieren“ nicht angenommen");
                }
                String key = course.key() == null ? "" : course.key().toString();
                if (key.isEmpty() || !key.equals(mc.keyboardHandler.getClipboard()) || !menu.copied()) {
                    return StepResult.fail("ID nicht kopiert: " + mc.keyboardHandler.getClipboard());
                }
                return next(ctx);
            }
            default -> {
                StepResult wait = once(ctx, "Server", server -> {
                    CourseRegistry.Course built = course(site);
                    CourseRegistry registry = CourseRegistry.get();
                    if (registry == null || built == null || !ENGLISH_NAME.equals(built.name) || built.mode() != CourseMode.LAPS || built.laps != 4) {
                        return "Server hat " + (built == null ? "-" : built.name + " " + built.mode() + " " + built.laps);
                    }
                    seedTimes(registry, built);
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                mc.gui.setScreen(null);
                ctx.log("Name „" + ENGLISH_NAME + "“, Runden 3 → 4, Typ Punkt zu Punkt und zurück, ID kopiert");
                return StepResult.pass();
            }
        }
    }

    private static StepResult windowStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        Screen screen = mc.gui.screen();
        if (!ctx.state().containsKey("offen")) {
            ctx.state().put("offen", true);
            CourseEditScreen.open(null, site.course());
            return StepResult.pending();
        }
        if (!ctx.state().containsKey("geklickt")) {
            if (!(screen instanceof CourseEditScreen edit)) {
                return StepResult.fail("Kurs-Fenster öffnet nicht: " + shown(screen));
            }
            String label = Component.translatable(OPEN_KEY).getString();
            FlatButton open = button(edit, label);
            if (open == null || !open.active || open != edit.settingsButton()) {
                return StepResult.fail("Kurs-Fenster ohne aktiven Knopf „" + label + "“");
            }
            List<LayoutAudit.Finding> findings = LayoutProbe.audit(mc, edit);
            if (!findings.isEmpty()) {
                return StepResult.fail(findings.size() + " Befunde im Kurs-Fenster mit Knopf „" + label + "“: " + findings.getFirst().describe());
            }
            if (!click(edit, open)) {
                return StepResult.fail("Klick auf „" + label + "“ nicht angenommen");
            }
            ctx.state().put("geklickt", ctx.tick());
            return StepResult.pending();
        }
        if (screen == null || !title().equals(screen.getTitle().getString())) {
            return StepResult.fail("Knopf im Kurs-Fenster öffnet " + shown(screen) + " statt „" + title() + "“");
        }
        screen.onClose();
        if (!(mc.gui.screen() instanceof CourseEditScreen)) {
            return StepResult.fail("Schließen der Kurs-Einstellungen führt nicht zurück ins Kurs-Fenster: " + shown(mc.gui.screen()));
        }
        mc.gui.setScreen(null);
        ctx.log("Kurs-Fenster → " + shown(screen) + " → zurück ins Kurs-Fenster");
        return StepResult.pass();
    }

    private static StepResult waysStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        switch (stage(ctx)) {
            case 0 -> {
                mc.gui.setScreen(null);
                CourseMenuScreen menu = openMenu(site);
                ctx.state().put("menü", menu);
                if (!press(menu, menu.control(CourseMenu.Row.GATES))) {
                    return StepResult.fail("Klick auf „Reihenfolge“ nicht angenommen");
                }
                if (!(mc.gui.screen() instanceof CourseEditScreen edit) || edit.courseId() != site.course()) {
                    return StepResult.fail("„Reihenfolge“ öffnet " + shown(mc.gui.screen()));
                }
                if (!click(edit, edit.settingsButton()) || mc.gui.screen() != menu) {
                    return StepResult.fail("„Einstellungen“ im Kurs-Fenster führt nicht zurück in dasselbe Menü: " + shown(mc.gui.screen()));
                }
                if (!press(menu, menu.control(CourseMenu.Row.BOARD))) {
                    return StepResult.fail("Klick auf „Öffnen“ (Bestenliste) nicht angenommen");
                }
                if (!(mc.gui.screen() instanceof LeaderboardScreen board) || board.courseId() != site.course()) {
                    return StepResult.fail("„Öffnen“ zeigt " + shown(mc.gui.screen()) + " statt der Bestenliste");
                }
                board.onClose();
                if (mc.gui.screen() != menu) {
                    return StepResult.fail("Schließen der Bestenliste führt nicht zurück ins Menü: " + shown(mc.gui.screen()));
                }
                mc.gui.setScreen(null);
                LeaderboardScreen board2 = new LeaderboardScreen(null, site.course());
                mc.gui.setScreen(board2);
                if (!click(board2, board2.editButton()) || !(mc.gui.screen() instanceof CourseEditScreen edit2)) {
                    return StepResult.fail("alter Knopf „Kurs bearbeiten“ in der Bestenliste öffnet nicht das Kurs-Fenster: " + shown(mc.gui.screen()));
                }
                if (!click(edit2, edit2.settingsButton()) || !(mc.gui.screen() instanceof CourseMenuScreen menu2) || menu2.parent() != edit2) {
                    return StepResult.fail("Kurs-Fenster aus der Bestenliste führt nicht in die Kurs-Einstellungen: " + shown(mc.gui.screen()));
                }
                menu2.onClose();
                if (mc.gui.screen() != edit2) {
                    return StepResult.fail("Schließen führt nicht zurück ins Kurs-Fenster");
                }
                edit2.onClose();
                if (mc.gui.screen() != board2) {
                    return StepResult.fail("Schließen führt nicht zurück in die Bestenliste");
                }
                mc.gui.setScreen(null);
                openMenu(site);
                return next(ctx);
            }
            case 1 -> {
                StepResult wait = once(ctx, "Befehle", server -> {
                    ServerPlayer player = player(server);
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "propwash race laps 5 " + site.course());
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "propwash race name " + site.course() + " Befehl");
                    CourseRegistry.Course built = course(site);
                    return built != null && built.laps == 5 && "Befehl".equals(built.name) ? null : "Befehle wirken nicht";
                });
                if (wait != null) {
                    return wait;
                }
                return next(ctx);
            }
            case 2 -> {
                CourseMenuScreen menu = menu(site);
                TextField name = menu == null ? null : menu.nameField();
                boolean seen = menu != null && menu.shownLaps() == 5 && name != null && "Befehl".equals(name.getValue());
                return waitFor(ctx, seen, 100, "Menü zeigt die Befehle nicht: " + (menu == null ? shown(mc.gui.screen()) : menu.shownLaps()));
            }
            case 3 -> {
                StepResult wait = once(ctx, "Zurück", server -> {
                    ServerPlayer player = player(server);
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "propwash race laps 4 " + site.course());
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "propwash race name " + site.course() + " " + ENGLISH_NAME);
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                return next(ctx);
            }
            default -> {
                CourseData course = ClientCourses.get(site.course());
                if (course == null || !ENGLISH_NAME.equals(course.name()) || course.laps() != 4) {
                    return since(ctx) > 100 ? StepResult.fail("Kurs nicht zurückgesetzt") : StepResult.pending();
                }
                mc.gui.setScreen(null);
                ctx.log("Menü ↔ Kurs-Fenster ↔ Bestenliste ohne Stapeln, alter Knopf „Kurs bearbeiten“ und Befehle laps/name wirken weiter");
                return StepResult.pass();
            }
        }
    }

    private static StepResult scrollStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        CourseMenuScreen menu = openMenu(site);
        if (menu.frames() < 2) {
            return StepResult.pending();
        }
        try {
            menu.resize(320, 240);
            ContentScroll scroll = menu.scroll();
            if (scroll == null || !scroll.scrollable()) {
                return StepResult.fail("bei 320×240 kein Scrollbalken");
            }
            ScreenRectangle view = scroll.view();
            int x = view.left() + view.width() / 2;
            int y = view.top() + view.height() / 2;
            if (!menu.mouseScrolled(x, y, 0.0, -1.0) || scroll.offset() <= 0) {
                return StepResult.fail("Mausrad scrollt nicht: " + scroll.offset());
            }
            menu.keyPressed(new KeyEvent(InputConstants.KEY_END, 0, 0));
            int end = scroll.offset();
            AbstractWidget delete = menu.control(CourseMenu.Row.DELETE);
            boolean deleteVisible = delete != null && delete.getY() >= view.top() && delete.getY() + delete.getHeight() <= view.bottom();
            menu.keyPressed(new KeyEvent(InputConstants.KEY_HOME, 0, 0));
            int home = scroll.offset();
            menu.keyPressed(new KeyEvent(InputConstants.KEY_PAGEDOWN, 0, 0));
            int paged = scroll.offset();
            if (end != scroll.maxOffset() || home != 0 || paged <= 0 || !deleteVisible) {
                return StepResult.fail("Ende/Pos1/Bild ab: " + end + "/" + home + "/" + paged + " von " + scroll.maxOffset() + ", Löschen sichtbar " + deleteVisible);
            }
            menu.keyPressed(new KeyEvent(InputConstants.KEY_HOME, 0, 0));
            List<LayoutAudit.Finding> findings = new ArrayList<>(LayoutProbe.scrollbars(mc, menu));
            findings.addAll(LayoutProbe.audit(mc, menu));
            if (!findings.isEmpty()) {
                return StepResult.fail(findings.size() + " Befunde bei 320×240: " + findings.getFirst().describe());
            }
            int below = 0;
            for (AbstractWidget widget : menu.contentWidgets()) {
                if (widget.getY() >= view.bottom()) {
                    below++;
                }
            }
            if (!click(menu, menu.doneButton()) || mc.gui.screen() == menu) {
                return StepResult.fail("„Fertig“ nimmt den Klick nicht an, während " + below + " Bedienelemente unter der Fußleiste liegen");
            }
            ctx.log("320×240: Inhalt " + scroll.contentHeight() + " in " + view.height() + " Pixeln, Mausrad, Ende " + end + ", Bild ab " + paged
                    + ", Scrollbalken lässt sich ziehen, „Fertig“ schließt trotz " + below + " Bedienelementen unter der Fußleiste");
            return StepResult.pass();
        } finally {
            if (mc.gui.screen() == menu) {
                menu.resize(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
                mc.gui.setScreen(null);
            }
        }
    }

    private static StepResult viewStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null || mc.player == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        switch (stage(ctx)) {
            case 0 -> {
                StepResult wait = once(ctx, "Abenteuer", server -> {
                    player(server).setGameMode(GameType.ADVENTURE);
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                return waitFor(ctx, !mc.player.mayBuild(), 100, "Abenteuermodus kommt nicht an");
            }
            case 1 -> {
                CourseMenuScreen menu = openMenu(site);
                if (menu.frames() < 2) {
                    return StepResult.pending();
                }
                boolean gamemaster = mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
                CourseMenu.Rights expected = new CourseMenu.Rights(false, false, gamemaster, CourseMenu.Role.VIEWER);
                List<String> wrong = new ArrayList<>();
                if (!menu.readOnly() || !menu.rights().equals(expected)) {
                    wrong.add("Rechte " + menu.rights());
                }
                for (CourseMenu.Row row : menu.rows()) {
                    AbstractWidget control = menu.control(row);
                    if (control != null && !(control instanceof TextField) && control.active != CourseMenu.active(row, expected)) {
                        wrong.add(row + " aktiv=" + control.active);
                    }
                }
                if (!wrong.isEmpty()) {
                    return StepResult.fail("nicht schreibgeschützt: " + String.join(", ", wrong));
                }
                List<LayoutAudit.Finding> findings = LayoutProbe.audit(mc, menu);
                if (!findings.isEmpty()) {
                    return StepResult.fail(findings.size() + " Befunde beim Ansehen: " + findings.getFirst().describe());
                }
                TextField name = menu.nameField();
                if (name != null) {
                    menu.setFocused(name);
                    for (char c : "Geklaut".toCharArray()) {
                        name.charTyped(new CharacterEvent(c));
                    }
                    menu.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                    if (!ENGLISH_NAME.equals(name.getValue())) {
                        return StepResult.fail("Namensfeld nimmt Text an: " + name.getValue());
                    }
                }
                for (AbstractWidget widget : menu.contentWidgets()) {
                    if (!widget.active) {
                        click(menu, widget);
                    }
                }
                menu.rename("Geklaut");
                menu.stepLaps(1);
                menu.setMode(CourseMode.POINT_TO_POINT);
                menu.bindTool();
                menu.delete();
                menu.delete();
                menu.flush();
                if (menu(site) != menu) {
                    return StepResult.fail("Menü hat sich beim Ansehen geschlossen: " + shown(mc.gui.screen()));
                }
                return next(ctx);
            }
            case 2 -> {
                if (since(ctx) < 30) {
                    return StepResult.pending();
                }
                StepResult wait = once(ctx, "Unverändert", server -> {
                    CourseRegistry.Course built = course(site);
                    if (built == null || !ENGLISH_NAME.equals(built.name) || built.laps != 4 || built.mode() != CourseMode.LAPS) {
                        return "Kurs beim Ansehen geändert: " + (built == null ? "gelöscht" : built.name + " " + built.laps + " " + built.mode());
                    }
                    player(server).setGameMode(site.mode());
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                return next(ctx);
            }
            default -> {
                if (!mc.player.mayBuild()) {
                    return since(ctx) > 100 ? StepResult.fail("Spielmodus nicht zurückgestellt") : StepResult.pending();
                }
                CourseMenuScreen menu = menu(site);
                if (menu == null || menu.readOnly()) {
                    return since(ctx) > 100 ? StepResult.fail("Menü nach dem Zurückstellen noch schreibgeschützt") : StepResult.pending();
                }
                mc.gui.setScreen(null);
                ctx.log("Abenteuermodus: Menü schreibgeschützt (Hinweis oben, alle Änderungen gesperrt), Kurs unverändert; danach wieder änderbar");
                return StepResult.pass();
            }
        }
    }

    private static StepResult resetStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null || mc.player == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        if (!mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            CourseMenuScreen menu = openMenu(site);
            AbstractWidget reset = menu.control(CourseMenu.Row.RESET);
            mc.gui.setScreen(null);
            if (reset == null || reset.active) {
                return StepResult.fail("„Alle Zeiten löschen“ ohne Operator-Recht aktiv");
            }
            ctx.log("ohne Operator-Recht: „Alle Zeiten löschen“ gesperrt");
            return StepResult.pass();
        }
        switch (stage(ctx)) {
            case 0 -> {
                CourseMenuScreen menu = openMenu(site);
                AbstractWidget reset = menu.control(CourseMenu.Row.RESET);
                if (reset == null || !reset.active || !press(menu, reset) || !menu.resetArmed()) {
                    return StepResult.fail("erster Klick auf „Alle Zeiten löschen“ fragt nicht nach");
                }
                return next(ctx);
            }
            case 1 -> {
                if (since(ctx) < 20) {
                    return StepResult.pending();
                }
                StepResult wait = once(ctx, "Noch da", server -> {
                    CourseRegistry registry = CourseRegistry.get();
                    CourseRegistry.Course built = course(site);
                    return registry != null && built != null && registry.board(built).top(null, 10).size() == PILOTS.length ? null
                            : "Zeiten schon nach dem ersten Klick gelöscht";
                });
                if (wait != null) {
                    return wait;
                }
                CourseMenuScreen menu = menu(site);
                if (menu == null || !press(menu, menu.control(CourseMenu.Row.RESET))) {
                    return StepResult.fail("zweiter Klick nicht angenommen");
                }
                return next(ctx);
            }
            case 2 -> {
                BoardView board = BoardView.of(site.course());
                return waitFor(ctx, board.empty(), 120, "Bestenliste nach dem Löschen: " + board.rows() + " Zeilen");
            }
            default -> {
                StepResult wait = once(ctx, "Leer", server -> {
                    CourseRegistry registry = CourseRegistry.get();
                    CourseRegistry.Course built = course(site);
                    return registry != null && built != null && registry.board(built).top(null, 10).isEmpty() ? null : "Server hat noch Zeiten";
                });
                if (wait != null) {
                    return wait;
                }
                mc.gui.setScreen(null);
                ctx.log("Alle Zeiten löschen: erster Klick fragt nach, zweiter löscht " + PILOTS.length + " Zeiten über " + CourseMenu.resetCommand(site.course()));
                return StepResult.pass();
            }
        }
    }

    private record BoardView(boolean empty, int rows) {
        static BoardView of(int course) {
            RacePayloads.LeaderboardS2C board = ClientLeaderboards.get(course);
            return new BoardView(board != null && board.rows().isEmpty(), board == null ? -1 : board.rows().size());
        }
    }

    private static StepResult deleteStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        Minecraft mc = Minecraft.getInstance();
        if (site == null || mc.level == null) {
            return StepResult.fail("kein Kurs aus dem Aufbau");
        }
        switch (stage(ctx)) {
            case 0 -> {
                CourseMenuScreen menu = openMenu(site);
                AbstractWidget delete = menu.control(CourseMenu.Row.DELETE);
                if (delete == null || !delete.active || !press(menu, delete) || !menu.deleteArmed() || ClientCourses.get(site.course()) == null) {
                    return StepResult.fail("erster Klick auf „Kurs löschen“ fragt nicht nach");
                }
                if (!press(menu, menu.control(CourseMenu.Row.DELETE)) || mc.gui.screen() != null) {
                    return StepResult.fail("zweiter Klick schließt das Menü nicht: " + shown(mc.gui.screen()));
                }
                return next(ctx);
            }
            default -> {
                if (ClientCourses.get(site.course()) != null) {
                    return since(ctx) > 100 ? StepResult.fail("Kurs nach dem Löschen noch da") : StepResult.pending();
                }
                StepResult wait = once(ctx, "Gates", server -> {
                    CourseRegistry registry = CourseRegistry.get();
                    if (registry != null && registry.course(site.course()) != null) {
                        return "Kurs beim Server noch da";
                    }
                    ServerLevel level = player(server).level();
                    for (int id : site.gates()) {
                        if (!(level.getEntity(id) instanceof GateEntity gate) || gate.isRemoved()) {
                            return "Gate " + id + " mitgelöscht";
                        }
                    }
                    return null;
                });
                if (wait != null) {
                    return wait;
                }
                ctx.log("Kurs gelöscht (Server und Client), " + site.gates().size() + " Gates stehen noch");
                return StepResult.pass();
            }
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Site site = SITE.get();
        if (site == null) {
            return StepResult.pass();
        }
        Minecraft.getInstance().gui.setScreen(null);
        StepResult wait = once(ctx, "Aufräumen", server -> {
            CourseRegistry registry = CourseRegistry.get();
            if (registry != null) {
                registry.deleteCourse(site.course());
            }
            ServerPlayer player = player(server);
            ServerLevel level = player.level();
            for (int id : site.gates()) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    entity.discard();
                }
            }
            player.setGameMode(site.mode());
            player.setItemInHand(InteractionHand.MAIN_HAND, site.held());
            setTime(server, site.time());
            player.teleportTo(level, site.home().x, site.home().y, site.home().z, Set.of(), player.getYRot(), 0.0F, true);
            return null;
        });
        if (wait != null) {
            return wait;
        }
        SITE.set(null);
        return StepResult.pass();
    }

    private static final class MenuScene implements Screenshots.Scene {
        private final long time;
        private final boolean view;
        private final String name;

        private MenuScene(long time, boolean view, String name) {
            this.time = time;
            this.view = view;
            this.name = name;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Site site = SITE.get();
            Minecraft mc = Minecraft.getInstance();
            if (site == null || mc.player == null) {
                return StepResult.fail("kein Kurs für das Bildschirmfoto");
            }
            StepResult wait = once(ctx, "Szene", server -> {
                ServerPlayer player = player(server);
                CourseRegistry registry = CourseRegistry.get();
                CourseRegistry.Course built = course(site);
                if (registry == null || built == null) {
                    return "Kurs fehlt";
                }
                registry.rename(built, this.name);
                player.setGameMode(this.view ? GameType.ADVENTURE : site.mode());
                setTime(server, this.time);
                ServerLevel level = player.level();
                int x = Mth.floor(site.origin().x) - 6;
                int z = Mth.floor(site.origin().z) - 12;
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                player.teleportTo(level, x + 0.5, ground, z + 0.5, Set.of(), -26.0F, -50.0F, true);
                player.resetFallDistance();
                return null;
            });
            if (wait != null) {
                return wait;
            }
            CourseData course = ClientCourses.get(site.course());
            if (course == null || !this.name.equals(course.name()) || mc.player.mayBuild() == this.view) {
                return StepResult.pending();
            }
            CourseMenuScreen menu = menu(site);
            if (menu == null || menu.readOnly() != this.view) {
                mc.gui.setScreen(null);
                CourseMenuScreen.open(null, site.course());
                Screenshots.parkMouse(mc);
                return StepResult.pending();
            }
            return menu.frames() >= 3 && ClientLeaderboards.get(site.course()) != null ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
            Site site = SITE.get();
            if (site == null) {
                return;
            }
            ctx.onServer(server -> {
                CourseRegistry registry = CourseRegistry.get();
                CourseRegistry.Course built = course(site);
                if (registry != null && built != null) {
                    registry.rename(built, ENGLISH_NAME);
                }
                player(server).setGameMode(site.mode());
                setTime(server, site.time());
            });
        }
    }
}
