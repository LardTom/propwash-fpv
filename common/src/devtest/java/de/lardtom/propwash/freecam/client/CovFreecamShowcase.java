package de.lardtom.propwash.freecam.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.freecam.FreecamRules;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.client.CovFreecamOsdProbe;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;

public final class CovFreecamShowcase {
    private static final String PREFIX = "bildschirmfotos: Freecam Abdeckung – ";
    private static final String FUTURE = "cov_freecam_shot_future";

    private CovFreecamShowcase() {
    }

    static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        String reset = PREFIX + "aufräumen";
        SelfTest.switchStep(PREFIX + "Testplatz", reset, 900, FreecamSelfTests.flow(
                CovFreecamSelfTests.phase("Testplatz bauen", 600, (ctx, run) -> {
                    if (run.waited(ctx) == 0) {
                        FreecamSelfTests.resetConfig();
                    }
                    return FreecamSelfTests.server(ctx, run, FreecamSelfTests::prepare);
                }),
                CovFreecamSelfTests.phase("Spieler am Testplatz", 400, (ctx, run) -> FreecamSelfTests.until(FreecamSelfTests.atSite(), "Spieler"))));
        Screenshots.step(PREFIX + "Start mit F6 – Hand, HUD und OSD wie im FPV, Name FREECAM · 5″", "cov_freecam_start", 20, new StartScene());
        SelfTest.clientStep(reset, 900, FreecamSelfTests.flow(CovFreecamSelfTests.phase("Testplatz abbauen", 600, (ctx, run) -> {
            if (run.waited(ctx) == 0) {
                end();
                FreecamSelfTests.restoreConfig();
            }
            return FreecamSelfTests.server(ctx, run, FreecamSelfTests::tearDown);
        })));
    }

    private static void end() {
        FreecamSession.INSTANCE.clearTestInput();
        CovFreecamOsdProbe.restore();
        if (FreecamSession.active()) {
            FreecamSession.INSTANCE.end(Minecraft.getInstance(), FreecamRules.End.BUTTON);
        }
    }

    private static final class StartScene implements Screenshots.Scene {
        private int started = -1;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!FreecamSelfTests.ruleSynced(true)) {
                if (!ctx.state().containsKey(FUTURE)) {
                    ctx.state().put(FUTURE, ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.ALLOW_FREECAM, 1)));
                }
                CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
                return future.isCompletedExceptionally() ? StepResult.fail("Regel: " + future.exceptionNow()) : StepResult.pending();
            }
            if (!FreecamSession.active()) {
                if (this.started < 0) {
                    if (mc.gui.screen() != null) {
                        mc.gui.setScreen(null);
                    }
                    CovFreecamOsdProbe.show(OsdElement.CRAFT_NAME);
                    FreecamSelfTests.clickKey(FreecamClient.key());
                    this.started = ctx.tick();
                }
                return ctx.tick() - this.started > 100 ? StepResult.fail("Freecam startet nicht: " + FreecamSession.INSTANCE.lastDenial())
                        : StepResult.pending();
            }
            Screenshots.parkMouse(mc);
            return ctx.tick() - this.started > 30 && mc.levelRenderer.hasRenderedAllSections() ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            end();
            this.started = -1;
        }
    }
}
