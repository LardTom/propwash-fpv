package de.lardtom.propwash.dev.client;

import de.lardtom.propwash.dev.SelfTest.StepResult;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationLogTest {
    private static final String MC = "net.minecraft.server.MinecraftServer";
    private static final String PROPWASH = "Propwash FPV";
    private Level rootBefore = Level.ERROR;

    @BeforeEach
    void rootAtInfo() {
        this.rootBefore = LogManager.getRootLogger().getLevel();
        Configurator.setRootLevel(Level.INFO);
        MigrationLog.finish(false);
    }

    @AfterEach
    void restoreRoot() {
        MigrationLog.finish(false);
        Configurator.setRootLevel(this.rootBefore);
    }

    private static MigrationLog.Entry entry(String level, String logger, String thread, String message) {
        return new MigrationLog.Entry(level, logger, thread, message, null);
    }

    private static MigrationLog.Report check(boolean jmpMissing, MigrationLog.Entry... entries) {
        return MigrationLog.check(List.of(entries), jmpMissing, 100, 10, 10);
    }

    private static void onThread(String name, Runnable body) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(body, name);
        thread.setUncaughtExceptionHandler((t, e) -> failure.set(e));
        thread.start();
        thread.join();
        assertNull(failure.get());
    }

    @Test
    void captureSeesErrorsAndWarningsWithExceptionsFromEveryLogger() throws InterruptedException {
        MigrationLog.begin("Test");
        Logger server = LogManager.getLogger(MC);
        Logger propwash = LogManager.getLogger(PROPWASH);
        onThread("Server thread", () -> {
            server.info("Saving chunks for level 'ServerLevel[Migration]'/minecraft:overworld");
            server.error("Couldn't load chunk [3, 4]");
            server.warn("Skipping Entity with id propwash:drone", new IllegalStateException("kaputt"));
            server.warn("Ignoring chat session from PropwashFixture due to missing Services public key");
        });
        propwash.info("[Selbsttest] migration: neu laden: geöffnet");
        MigrationLog.Report report = MigrationLog.finish(false);
        assertNotNull(report);
        List<String> lines = new ArrayList<>();
        for (MigrationLog.Entry e : report.unexpected()) {
            lines.add(e.level() + " " + e.message());
        }
        assertEquals(List.of("ERROR Couldn't load chunk [3, 4]", "WARN Skipping Entity with id propwash:drone"), lines);
        assertTrue(report.unexpected().get(1).thrown().contains("IllegalStateException: kaputt"), report.unexpected().get(1).thrown());
        assertEquals("Server thread", report.unexpected().getFirst().thread());
        assertTrue(report.serverEvents() >= 4, "Zeilen vom Server-Thread: " + report.serverEvents());
        assertTrue(report.propwashEvents() >= 1, "Zeilen von Propwash: " + report.propwashEvents());
        assertEquals(StepResult.Status.FAIL, MigrationLog.result(report).status());
        assertTrue(MigrationLog.result(report).message().startsWith("2 unerwartete Fehlerzeilen"), MigrationLog.result(report).message());
    }

    @Test
    void afterFinishNothingIsCapturedAnyMore() throws InterruptedException {
        MigrationLog.begin("Test");
        onThread("Server thread", () -> LogManager.getLogger(MC).info("vorher"));
        LogManager.getLogger(PROPWASH).info("[Selbsttest] vorher");
        assertNotNull(MigrationLog.finish(false));
        LogManager.getLogger(MC).error("danach");
        assertNull(MigrationLog.finish(false));
        MigrationLog.begin("Test");
        MigrationLog.begin("zweimal");
        onThread("Server thread", () -> LogManager.getLogger(MC).error("einmal"));
        MigrationLog.Report again = MigrationLog.finish(false);
        assertNotNull(again);
        assertEquals("Test", again.since());
        assertEquals(1, again.unexpected().size());
        assertEquals("einmal", again.unexpected().getFirst().message());
    }

    @Test
    void cleanLogPassesOnlyWhenTheCaptureSawTheServerAndPropwash() {
        MigrationLog.Report clean = check(false, entry("WARN", MC, "Server thread", "Can't keep up! Is the server overloaded?"));
        assertEquals(0, clean.unexpected().size());
        assertEquals(StepResult.Status.PASS, MigrationLog.result(clean).status());
        MigrationLog.Report blind = MigrationLog.check(List.of(), false, 3, 0, 3);
        assertEquals(StepResult.Status.FAIL, MigrationLog.result(blind).status());
        assertTrue(MigrationLog.result(blind).message().contains("Server-Thread"), MigrationLog.result(blind).message());
        assertEquals(StepResult.Status.FAIL, MigrationLog.result(MigrationLog.check(List.of(), false, 3, 3, 0)).status());
        assertEquals(StepResult.Status.FAIL, MigrationLog.result(null).status());
    }

    @Test
    void accountAndRealmsLinesOfTheDevelopmentClientAreEnvironment() {
        MigrationLog.Report report = check(false,
                entry("ERROR", "net.minecraft.client.Minecraft", "Download-2", "Failed to fetch user properties"),
                entry("ERROR", "com.mojang.realmsclient.RealmsMainScreen", "Download-8", "Couldn't connect to realms"),
                entry("ERROR", "net.minecraft.client.Minecraft", "Download-1", "Failed to fetch Realms feature flags"),
                new MigrationLog.Entry("WARN", "com.mojang.authlib.yggdrasil.YggdrasilUserApiService", "Download-3", "Failed to fetch profile",
                        "com.mojang.authlib.exceptions.InvalidCredentialsException: Status: 401"));
        assertEquals(0, report.unexpected().size());
        assertEquals(4, report.environment().size());
        assertEquals(StepResult.Status.PASS, MigrationLog.result(report).status());
    }

    @Test
    void ownResultLinesOfTheSelfTestDoNotCountButThrownStepsDo() {
        MigrationLog.Report report = check(false,
                entry("ERROR", PROPWASH, "Render thread", "[Selbsttest] FAIL migration: alle Werte wie in der Erwartungsdatei: 2 Abweichungen"),
                entry("ERROR", PROPWASH, "Render thread", "PROPWASH SELFTEST FAIL migration: alle Werte wie in der Erwartungsdatei (Fabric)"),
                new MigrationLog.Entry("ERROR", PROPWASH, "Render thread", "[Selbsttest] Schritt 'migration: neu laden' warf",
                        "java.lang.NullPointerException"));
        assertEquals(1, report.unexpected().size());
        assertTrue(report.unexpected().getFirst().message().contains("warf"));
    }

    @Test
    void recipesOfJustMorePartsAreExpectedOnlyWhenTheAddonIsMissing() {
        MigrationLog.Entry recipe = entry("ERROR", "net.minecraft.world.item.crafting.RecipeManager", "Server thread",
                "Tried to load unrecognized recipe: ResourceKey[minecraft:recipe / justmoreparts:stack_5in_6s] removed now.");
        MigrationLog.Report without = check(true, recipe);
        assertEquals(0, without.unexpected().size());
        assertEquals(1, without.withoutJmp().size());
        assertEquals(StepResult.Status.PASS, MigrationLog.result(without).status());
        MigrationLog.Report with = check(false, recipe);
        assertEquals(1, with.unexpected().size());
        assertEquals(StepResult.Status.FAIL, MigrationLog.result(with).status());
    }

    @Test
    void errorsOfPropwashAlwaysCountAndTheReportListsEverything() {
        MigrationLog.Report report = check(true,
                entry("ERROR", PROPWASH, "Render thread",
                        "Sharecode-Tabellen: propwash: Tabelle data/propwash_example/propwash_share/table.json passt nicht zur Mod-Id und zählt nicht"),
                entry("ERROR", "net.minecraft.client.Minecraft", "Download-2", "Failed to fetch user properties"),
                entry("ERROR", "net.minecraft.world.item.crafting.RecipeManager", "Server thread",
                        "Tried to load unrecognized recipe: ResourceKey[minecraft:recipe / justmoreparts:xclass13h_8s]"),
                entry("FATAL", MC, "Server thread", "Encountered an unexpected exception"));
        assertEquals(2, report.unexpected().size());
        String text = report.report();
        assertTrue(text.contains("unerwartet (2):"), text);
        assertTrue(text.contains("[Render thread/ERROR] (Propwash FPV) Sharecode-Tabellen"), text);
        assertTrue(text.contains("Umgebung ohne Konto (1):"), text);
        assertTrue(text.contains("ohne Just More Parts (1):"), text);
        assertTrue(report.summary().contains("2 unerwartet"), report.summary());
    }
}
