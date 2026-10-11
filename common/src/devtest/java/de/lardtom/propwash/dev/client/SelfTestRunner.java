package de.lardtom.propwash.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class SelfTestRunner {
    public static final String WORLD_NAME = "Propwash Selftest";
    private static final int GLOBAL_TIMEOUT_TICKS = 20 * 4800;
    private static final long WATCHDOG_MILLIS = 96L * 60L * 1000L;
    private static final long SMOKE_WATCHDOG_MILLIS = 5L * 60L * 1000L;
    private static final long EXIT_GRACE_MILLIS = 120L * 1000L;
    private static final long CRASH_GRACE_MILLIS = 30L * 1000L;
    private static final long HALT_GRACE_MILLIS = 5L * 60L * 1000L;
    private static final int SETTLE_TICKS = 40;
    private static final int TITLE_TIMEOUT_TICKS = 20 * 60;
    private static final String LOAD_WARNINGS_SCREEN = "net.neoforged.neoforge.client.gui.LoadingErrorScreen";

    private static final int LEAVE_TIMEOUT_TICKS = 20 * 60;

    private enum Phase { WAIT_TITLE, WAIT_WORLD, SETTLE, RUN, WAIT_LEFT, AFTER_LEAVE, FINISH, DONE }

    private static Phase phase = Phase.WAIT_TITLE;
    private static int totalTicks;
    private static int phaseTicks;
    private static List<SelfTest.Step> steps = List.of();
    private static boolean worldEntered;
    private static int stepIndex;
    private static int stepTicks;
    private static Map<String, Object> stepState = SelfTest.newState();
    private static final List<String> FAILURES = new ArrayList<>();
    private static int passed;
    private static volatile boolean finished;
    private static volatile long finishedAt;
    private static volatile @Nullable Thread gameThread;
    private static boolean smoke;
    private static boolean installed;

    private SelfTestRunner() {
    }

    public static void install() {
        if (!SelfTest.enabled()) {
            return;
        }
        start(false);
    }

    public static void installSmoke() {
        if (!SelfTest.smokeEnabled()) {
            return;
        }
        start(true);
    }

    private static void start(boolean smokeMode) {
        if (installed) {
            return;
        }
        installed = true;
        smoke = smokeMode;
        Constants.LOG.info("[Selbsttest] {} aktiv ({})", smoke ? "Rauchtest" : "Selbsttest", Services.PLATFORM.getPlatformName());
        ClientBus.TICK_END.register(SelfTestRunner::tick);
        long timeout = smoke ? SMOKE_WATCHDOG_MILLIS : WATCHDOG_MILLIS;
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(timeout);
            } catch (InterruptedException e) {
                return;
            }
            if (!finished) {
                Constants.LOG.error("{} FAIL Watchdog: Client hängt (Phase {})", marker(), phase);
                writeResult("FAIL Watchdog: Client hängt in Phase " + phase);
                Runtime.getRuntime().halt(3);
            }
        }, "Propwash-Selftest-Watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        Thread exitGuard = new Thread(SelfTestRunner::guardExit, "Propwash-Selftest-Exit");
        exitGuard.setDaemon(true);
        exitGuard.start();
    }

    private static void guardExit() {
        long stuckSince = 0L;
        String stuckIn = "";
        while (true) {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                return;
            }
            long now = System.currentTimeMillis();
            long done = finishedAt;
            if (done > 0L && now - done > EXIT_GRACE_MILLIS) {
                Constants.LOG.error("{} Spiel beendet sich {} s nach dem Ergebnis nicht, beende den Prozess", marker(), (now - done) / 1000L);
                Runtime.getRuntime().halt(0);
            }
            Thread game = gameThread;
            String blocked = game == null ? "" : blockedIn(game);
            if (blocked.isEmpty() || !blocked.equals(stuckIn)) {
                stuckSince = blocked.isEmpty() ? 0L : now;
                stuckIn = blocked;
                if (!blocked.isEmpty()) {
                    Constants.LOG.warn("{} Spiel-Thread in {} (Phase {})", marker(), blocked, phase);
                }
                continue;
            }
            long grace = blocked.equals("emergencySave") ? CRASH_GRACE_MILLIS : HALT_GRACE_MILLIS;
            if (now - stuckSince > grace) {
                Constants.LOG.error("{} FAIL Spiel-Thread hängt seit {} s in {} (Phase {})", marker(), (now - stuckSince) / 1000L, blocked, phase);
                if (done == 0L) {
                    finished = true;
                    writeResult("FAIL Spiel hängt in " + blocked + " (Phase " + phase + ", Server-Thread hält nicht an)");
                }
                Runtime.getRuntime().halt(3);
            }
        }
    }

    private static String blockedIn(Thread thread) {
        boolean halting = false;
        for (StackTraceElement frame : thread.getStackTrace()) {
            String owner = frame.getClassName();
            if (owner.equals(Minecraft.class.getName()) && frame.getMethodName().equals("emergencySave")) {
                return "emergencySave";
            }
            if (frame.getMethodName().equals("halt") && (owner.equals(MinecraftServer.class.getName()) || owner.equals(IntegratedServer.class.getName()))) {
                halting = true;
            }
        }
        return halting ? "halt" : "";
    }

    private static String marker() {
        return smoke ? "PROPWASH SMOKE" : "PROPWASH SELFTEST";
    }

    private static void tick(Minecraft mc) {
        if (gameThread == null) {
            gameThread = Thread.currentThread();
        }
        totalTicks++;
        phaseTicks++;
        try {
            if (totalTicks > GLOBAL_TIMEOUT_TICKS && phase.ordinal() < Phase.WAIT_LEFT.ordinal()) {
                FAILURES.add("Zeitüberschreitung in Phase " + phase + " (Screen: " + screenName(mc) + ")");
                leaveWorld(mc);
            }
            switch (phase) {
                case WAIT_TITLE -> {
                    if (mc.gui.screen() instanceof TitleScreen && phaseTicks > 10) {
                        String legacy = smoke ? LegacyWorldCheck.worldName() : null;
                        if (!smoke) {
                            writeSteps();
                        }
                        if (!smoke && SelfTest.listOnly()) {
                            listSteps(mc);
                        } else if (!smoke && SelfTest.filterMatchesNothing()) {
                            FAILURES.add("Filter '" + SelfTest.filter() + "' passt auf keinen Schritt (Liste: " + SelfTest.STEPS_FILE
                                    + ", Bildschirmfoto-Schritte nur mit -PpropwashScreenshots=true)");
                            setPhase(Phase.FINISH);
                        } else if (smoke && JoinTest.enabled()) {
                            steps = JoinTest.steps();
                            startStep(0);
                            setPhase(Phase.AFTER_LEAVE);
                        } else if (legacy != null) {
                            LegacyWorldCheck.open(mc, legacy);
                            setPhase(Phase.WAIT_WORLD);
                        } else {
                            createWorld(mc);
                            setPhase(Phase.WAIT_WORLD);
                        }
                    } else if (phaseTicks % 20 == 0 && continueAfterLoadWarnings(mc.gui.screen())) {
                        Constants.LOG.warn("[Selbsttest] Warnungen beim Laden der Mods übergangen (nur Warnungen, keine Fehler)");
                    } else if (phaseTicks > TITLE_TIMEOUT_TICKS) {
                        FAILURES.add("Titelbildschirm nicht erreicht, aktueller Screen: " + screenName(mc));
                        leaveWorld(mc);
                    }
                }
                case WAIT_WORLD -> {
                    Screen screen = mc.gui.screen();
                    if (screen instanceof ConfirmScreen confirm && phaseTicks % 20 == 0) {
                        Constants.LOG.info("[Selbsttest] bestätige {}", confirm.getTitle().getString());
                        pressButton(confirm, CommonComponents.GUI_YES.getString());
                    }
                    if (mc.player != null && mc.level != null && screen == null) {
                        Constants.LOG.info("[Selbsttest] Welt geladen nach {} Ticks", phaseTicks);
                        worldEntered = true;
                        setPhase(Phase.SETTLE);
                    }
                }
                case SETTLE -> {
                    if (phaseTicks >= SETTLE_TICKS && smoke && LegacyWorldCheck.worldName() != null) {
                        steps = LegacyWorldCheck.steps();
                        Constants.LOG.info("[Selbsttest] Rauchtest mit alter Welt: {} Prüfungen", steps.size());
                        startStep(0);
                        setPhase(Phase.RUN);
                    } else if (phaseTicks >= SETTLE_TICKS && smoke) {
                        Constants.LOG.info("[Selbsttest] Rauchtest: Welt betreten, keine Schritte");
                        leaveWorld(mc);
                    } else if (phaseTicks >= SETTLE_TICKS) {
                        steps = SelfTest.startClientSteps();
                        if (SelfTest.filter() != null) {
                            Constants.LOG.info("[Selbsttest] Filter '{}': {} von {} Schritten", SelfTest.filter(), SelfTest.selectedCount(),
                                    SelfTest.registeredCount());
                        }
                        Constants.LOG.info("[Selbsttest] starte {} Schritte", steps.size());
                        startStep(0);
                        setPhase(Phase.RUN);
                    }
                }
                case RUN -> {
                    if (runStep(mc)) {
                        leaveWorld(mc);
                    }
                }
                case WAIT_LEFT -> {
                    if (mc.level == null && mc.player == null && mc.getSingleplayerServer() == null && phaseTicks > 5) {
                        Constants.LOG.info("[Selbsttest] Welt verlassen nach {} Ticks", phaseTicks);
                        steps = worldEntered && !smoke ? SelfTest.startAfterLeaveSteps() : List.of();
                        if (!steps.isEmpty()) {
                            Constants.LOG.info("[Selbsttest] starte {} Schritte nach dem Verlassen", steps.size());
                        }
                        startStep(0);
                        setPhase(Phase.AFTER_LEAVE);
                    } else if (phaseTicks > LEAVE_TIMEOUT_TICKS) {
                        FAILURES.add("Welt nicht verlassen (Screen: " + screenName(mc) + ")");
                        setPhase(Phase.FINISH);
                    }
                }
                case AFTER_LEAVE -> {
                    if (runStep(mc)) {
                        setPhase(Phase.FINISH);
                    }
                }
                case FINISH -> finish(mc);
                case DONE -> {
                }
            }
        } catch (Throwable t) {
            Constants.LOG.error("[Selbsttest] Fehler im Runner (Phase {})", phase, t);
            if (phase.ordinal() < Phase.WAIT_LEFT.ordinal()) {
                FAILURES.add("Runner-Fehler in Phase " + phase + ": " + t);
                leaveWorld(mc);
            } else if (phase.ordinal() < Phase.FINISH.ordinal()) {
                FAILURES.add("Runner-Fehler in Phase " + phase + ": " + t);
                setPhase(Phase.FINISH);
            } else if (phase != Phase.DONE) {
                setPhase(Phase.DONE);
                mc.stop();
            }
        }
    }

    private static void leaveWorld(Minecraft mc) {
        setPhase(Phase.WAIT_LEFT);
        if (mc.level != null) {
            Constants.LOG.info("[Selbsttest] verlasse die Welt");
            mc.schedule(() -> mc.disconnectFromWorld(Component.literal("Propwash-Selbsttest beendet")));
        }
    }

    private static void createWorld(Minecraft mc) {
        deleteOldWorlds(mc);
        CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(new TitleScreen()));
        if (!(mc.gui.screen() instanceof CreateWorldScreen screen)) {
            throw new IllegalStateException("CreateWorldScreen wurde nicht geöffnet, sondern " + screenName(mc));
        }
        WorldCreationUiState ui = screen.getUiState();
        ui.setName(WORLD_NAME);
        Holder<WorldPreset> flat = ui.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT);
        ui.setWorldType(new WorldCreationUiState.WorldTypeEntry(flat));
        ui.setSeed("1");
        ui.setGenerateStructures(false);
        ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
        ui.getGameRules().set(GameRules.ADVANCE_TIME, false, null);
        ui.getGameRules().set(GameRules.ADVANCE_WEATHER, false, null);
        ui.getGameRules().set(GameRules.SPAWN_MOBS, false, null);
        Constants.LOG.info("[Selbsttest] lege Welt '{}' an", ui.getTargetFolder());
        if (!pressButton(screen, Component.translatable("selectWorld.create").getString())) {
            throw new IllegalStateException("Knopf 'selectWorld.create' nicht gefunden");
        }
    }

    private static void deleteOldWorlds(Minecraft mc) {
        Path saves = mc.getLevelSource().getBaseDir();
        if (!Files.isDirectory(saves)) {
            return;
        }
        try (Stream<Path> worlds = Files.list(saves)) {
            for (Path world : worlds.filter(p -> p.getFileName().toString().startsWith(WORLD_NAME)).toList()) {
                try (Stream<Path> files = Files.walk(world)) {
                    for (Path p : files.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(p);
                    }
                }
            }
        } catch (IOException e) {
            Constants.LOG.warn("[Selbsttest] alte Welten nicht gelöscht: {}", e.toString());
        }
    }

    private static boolean pressButton(Screen screen, String text) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && text.equals(button.getMessage().getString())) {
                button.onPress(new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                return true;
            }
        }
        return false;
    }

    private static void startStep(int index) {
        stepIndex = index;
        stepTicks = 0;
        stepState = SelfTest.newState();
        if (index < steps.size()) {
            Constants.LOG.info("[Selbsttest] Schritt {}/{}: {}", index + 1, steps.size(), steps.get(index).name());
        }
    }

    private static boolean runStep(Minecraft mc) {
        if (stepIndex >= steps.size()) {
            return true;
        }
        SelfTest.Step step = steps.get(stepIndex);
        SelfTest.StepResult result;
        try {
            result = step.body().tick(new Context(stepTicks, mc.getSingleplayerServer(), stepState, step.name()));
        } catch (Throwable t) {
            Constants.LOG.error("[Selbsttest] Schritt '{}' warf", step.name(), t);
            result = SelfTest.StepResult.fail("Ausnahme: " + t);
        }
        stepTicks++;
        switch (result.status()) {
            case PASS -> {
                passed++;
                Constants.LOG.info("[Selbsttest] PASS {} ({} Ticks)", step.name(), stepTicks);
                startStep(stepIndex + 1);
            }
            case FAIL -> {
                FAILURES.add(step.name() + ": " + result.message());
                Constants.LOG.error("[Selbsttest] FAIL {}: {}", step.name(), result.message());
                startStep(stepIndex + 1);
            }
            case PENDING -> {
                if (stepTicks >= step.maxTicks()) {
                    FAILURES.add(step.name() + ": Zeitüberschreitung nach " + step.maxTicks() + " Ticks");
                    Constants.LOG.error("[Selbsttest] FAIL {}: Zeitüberschreitung", step.name());
                    startStep(stepIndex + 1);
                }
            }
        }
        return stepIndex >= steps.size();
    }

    private static void finish(Minecraft mc) {
        finished = true;
        String mouse = DetachedMouse.failure();
        if (mouse != null) {
            FAILURES.add(mouse);
        }
        Constants.LOG.info("[Selbsttest] {}", DetachedMouse.summary());
        String result;
        if (smoke && FAILURES.isEmpty()) {
            result = "PASS";
            Constants.LOG.info("PROPWASH SMOKE PASS ({}, {})", JoinTest.enabled() ? "Beitrittstest" : "Welt angelegt, betreten und verlassen",
                    Services.PLATFORM.getPlatformName());
        } else if (!smoke && FAILURES.isEmpty() && passed > 0) {
            String filter = SelfTest.filter();
            result = filter == null ? "PASS" : "PASS gefiltert: " + passed + " von " + SelfTest.registeredCount() + " Schritten (" + filter + ")";
            Constants.LOG.info("PROPWASH SELFTEST PASS ({} Schritte, {})", passed, Services.PLATFORM.getPlatformName());
        } else {
            if (!smoke && passed == 0 && FAILURES.isEmpty()) {
                FAILURES.add("keine Schritte ausgeführt");
            }
            result = "FAIL " + String.join(" | ", FAILURES);
            Constants.LOG.error("{} {} ({})", marker(), result, Services.PLATFORM.getPlatformName());
        }
        writeResult(result);
        finishedAt = System.currentTimeMillis();
        setPhase(Phase.DONE);
        Constants.LOG.info("[Selbsttest] beende das Spiel");
        mc.stop();
    }

    private static void listSteps(Minecraft mc) {
        finished = true;
        Constants.LOG.info("[Selbsttest] Liste: {} Schritte in {}, keine Welt", SelfTest.registeredCount(), SelfTest.STEPS_FILE);
        writeResult("PASS Liste: " + SelfTest.registeredCount() + " Schritte in " + SelfTest.STEPS_FILE);
        finishedAt = System.currentTimeMillis();
        setPhase(Phase.DONE);
        mc.stop();
    }

    private static void writeSteps() {
        try {
            Files.writeString(Services.PLATFORM.getGameDir().resolve(SelfTest.STEPS_FILE), SelfTest.describe(Services.PLATFORM.getPlatformName()),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            Constants.LOG.warn("[Selbsttest] Schrittliste nicht geschrieben: {}", e.toString());
        }
    }

    private static void writeResult(String result) {
        try {
            Files.writeString(Services.PLATFORM.getGameDir().resolve(SelfTest.RESULT_FILE), result + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Constants.LOG.error("[Selbsttest] Ergebnisdatei nicht geschrieben", e);
        }
    }

    private static void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    private static boolean continueAfterLoadWarnings(@Nullable Screen screen) {
        if (screen == null || !LOAD_WARNINGS_SCREEN.equals(screen.getClass().getName())) {
            return false;
        }
        try {
            Field errors = screen.getClass().getDeclaredField("modLoadErrors");
            errors.setAccessible(true);
            Field next = screen.getClass().getDeclaredField("nextScreenTask");
            next.setAccessible(true);
            if (errors.get(screen) instanceof List<?> list && list.isEmpty() && next.get(screen) instanceof Runnable task) {
                task.run();
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Constants.LOG.warn("[Selbsttest] Lade-Warnungen nicht übergehbar ({})", e.toString());
        }
        return false;
    }

    private static String screenName(Minecraft mc) {
        Screen s = mc.gui.screen();
        return s == null ? "keiner" : s.getClass().getName();
    }

    private record Context(int tick, @Nullable MinecraftServer server, Map<String, Object> state, String name) implements SelfTest.StepContext {
        @Override
        public CompletableFuture<Void> onServer(Consumer<MinecraftServer> action) {
            MinecraftServer s = this.server;
            if (s == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("kein integrierter Server"));
            }
            return s.submit(() -> action.accept(s)).whenComplete((v, t) -> {
                if (t != null) {
                    Constants.LOG.error("[Selbsttest] Server-Aktion in '{}' fehlgeschlagen", this.name, t);
                }
            });
        }

        @Override
        public void log(String message) {
            Constants.LOG.info("[Selbsttest] {}: {}", this.name, message);
        }
    }
}
