package de.lardtom.propwash.freecam.client;

import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.freecam.FreecamRules;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdPreset;
import de.lardtom.propwash.osd.client.OsdClient;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.tab.OverviewTab;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public final class FreecamShowcase {
    private static final String PREFIX = "bildschirmfotos: Freecam – ";
    private static final String FUTURE = "freecam_shot_future";
    private static final String SINCE = "freecam_shot_since";
    private static final int[] WIDE = {1708, 960, 2};
    private static final int[] SMALL = {960, 720, 3};

    private FreecamShowcase() {
    }

    static void register() {
        if (!Screenshots.enabled()) {
            return;
        }
        String reset = PREFIX + "aufräumen";
        SelfTest.switchStep(PREFIX + "Testplatz", reset, 900, ctx -> {
            if (!ctx.state().containsKey(FUTURE)) {
                FreecamSelfTests.resetConfig();
                ctx.state().put(FUTURE, ctx.onServer(FreecamSelfTests::prepare));
                return StepResult.pending();
            }
            StepResult waiting = waitFuture(ctx);
            if (waiting != null) {
                return waiting;
            }
            return FreecamSelfTests.atSite() ? StepResult.pass() : StepResult.pending();
        });
        Screenshots.step(PREFIX + "K-Menü Übersicht mit ausgegrautem Start", "freecam_start_denied", 10, new OverviewScene(false));
        Screenshots.step(PREFIX + "OSD vor der Wand", "freecam_osd", 20, new FlightScene(false));
        Screenshots.step(PREFIX + "Warnung an der Grenze der geladenen Chunks", "freecam_edge", 6, new FlightScene(true));
        Screenshots.step(PREFIX + "K-Menü", "freecam_menu", 10, new MenuScene(WIDE));
        Screenshots.step(PREFIX + "K-Menü bei 320 × 240", "freecam_menu_small", 10, new MenuScene(SMALL));
        SelfTest.switchStep(PREFIX + "Sprache Deutsch", PREFIX + "Sprache zurücksetzen", 1200, ctx -> Screenshots.language(ctx, "de_de"));
        Screenshots.step(PREFIX + "K-Menü Übersicht mit ausgegrautem Start deutsch", "freecam_start_denied_de", 10, new OverviewScene(false));
        Screenshots.step(PREFIX + "OSD vor der Wand deutsch", "freecam_osd_de", 20, new FlightScene(false));
        Screenshots.step(PREFIX + "Warnung an der Grenze deutsch", "freecam_edge_de", 6, new FlightScene(true));
        Screenshots.step(PREFIX + "K-Menü deutsch", "freecam_menu_de", 10, new MenuScene(WIDE));
        Screenshots.step(PREFIX + "K-Menü bei 320 × 240 deutsch", "freecam_menu_small_de", 10, new MenuScene(SMALL));
        SelfTest.clientStep(PREFIX + "Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
        SelfTest.clientStep(reset, 900, ctx -> {
            if (!ctx.state().containsKey(FUTURE)) {
                end();
                FreecamSelfTests.restoreConfig();
                ctx.state().put(FUTURE, ctx.onServer(FreecamSelfTests::tearDown));
                return StepResult.pending();
            }
            StepResult waiting = waitFuture(ctx);
            return waiting != null ? waiting : StepResult.pass();
        });
    }

    private static @Nullable StepResult waitFuture(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            return null;
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Szene: " + future.exceptionNow());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static @Nullable StepResult rule(SelfTest.StepContext ctx, boolean on) {
        if (FreecamSelfTests.ruleSynced(on)) {
            return null;
        }
        if (!ctx.state().containsKey(FUTURE)) {
            ctx.state().put(FUTURE, ctx.onServer(server -> WorldRules.set(server, WorldRuleSpecs.ALLOW_FREECAM, on ? 1 : 0)));
        }
        return StepResult.pending();
    }

    private static @Nullable StepResult running(SelfTest.StepContext ctx) {
        StepResult rule = rule(ctx, true);
        if (rule != null) {
            return rule;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!FreecamSession.active()) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            FreecamSession.INSTANCE.tryStart(mc);
            return StepResult.pending();
        }
        return null;
    }

    private static void end() {
        FreecamSession.INSTANCE.clearTestInput();
        if (FreecamSession.active()) {
            FreecamSession.INSTANCE.end(Minecraft.getInstance(), FreecamRules.End.BUTTON);
        }
    }

    private static final class OverviewScene implements Screenshots.Scene {
        private final boolean allowed;
        private int opened = -1;

        private OverviewScene(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            end();
            StepResult rule = rule(ctx, this.allowed);
            if (rule != null) {
                return rule;
            }
            if (this.opened < 0) {
                ConfigTabs.open(null, OverviewTab.ID);
                this.opened = ctx.tick();
                return StepResult.pending();
            }
            Screenshots.parkMouse(mc);
            return mc.gui.screen() instanceof ConfiguratorScreen screen && screen.freecamStartButton() != null && ctx.tick() - this.opened > 5
                    ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            this.opened = -1;
        }
    }

    private static final class FlightScene implements Screenshots.Scene {
        private final boolean edge;
        private int placed = -1;
        private @Nullable OsdConfig savedOsd;

        private FlightScene(boolean edge) {
            this.edge = edge;
        }

        private void place() {
            BlockPos site = FreecamSelfTests.site();
            double x = this.edge ? FreecamSelfTests.loadedEdgeX(Minecraft.getInstance()) - 0.4 : site.getX() + 2.5;
            FreecamSession.INSTANCE.testPlace(x, site.getY() + (this.edge ? 14.0 : 2.2), site.getZ() + 0.5, -90.0F);
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            StepResult running = running(ctx);
            if (running != null) {
                return running;
            }
            FreecamSession session = FreecamSession.INSTANCE;
            if (this.placed < 0) {
                if (this.savedOsd == null) {
                    this.savedOsd = OsdClient.CONFIG.get().copy();
                    OsdConfig shown = OsdClient.CONFIG.get().copy();
                    shown.applyPreset(OsdPreset.STANDARD);
                    shown.slot(OsdElement.CRAFT_NAME).enabled = true;
                    OsdClient.CONFIG.set(shown);
                }
                this.place();
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.0F), true, StabMode.ANGLE);
                this.placed = ctx.tick();
                return StepResult.pending();
            }
            if (!session.armed()) {
                return ctx.tick() - this.placed > 200 ? StepResult.fail("Freecam wird nicht scharf") : StepResult.pending();
            }
            this.hold(ctx);
            Minecraft mc = Minecraft.getInstance();
            boolean rendered = mc.levelRenderer.hasRenderedAllSections() && ctx.tick() - this.placed > 60;
            if (this.edge) {
                return session.edge() && ctx.tick() - this.placed > 80 ? StepResult.pass() : StepResult.pending();
            }
            return rendered ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            FreecamSession session = FreecamSession.INSTANCE;
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, FreecamSelfTests.hover()), true, StabMode.ANGLE);
            if (this.edge) {
                session.testPush(6.0, 0.0, 0.0);
            } else {
                this.place();
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            end();
            this.placed = -1;
            OsdConfig saved = this.savedOsd;
            if (saved != null) {
                OsdClient.CONFIG.set(saved);
                this.savedOsd = null;
            }
        }
    }

    private static final class MenuScene implements Screenshots.Scene {
        private final int[] size;
        private int @Nullable [] saved;
        private int opened = -1;

        private MenuScene(int[] size) {
            this.size = size;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Window window = mc.getWindow();
            if (this.saved == null) {
                this.saved = new int[]{window.getScreenWidth(), window.getScreenHeight(), mc.options.guiScale().get()};
                float density = Math.max(1.0F, window.getPixelDensity());
                window.setWindowed(Math.round(this.size[0] / density), Math.round(this.size[1] / density));
                return StepResult.pending();
            }
            mc.options.guiScale().set(this.size[2]);
            mc.resizeGui();
            int width = this.size[0] / this.size[2];
            int height = this.size[1] / this.size[2];
            if (window.getGuiScaledWidth() != width || window.getGuiScaledHeight() != height) {
                return ctx.tick() > 200 ? StepResult.fail("GUI " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight()) : StepResult.pending();
            }
            StepResult running = running(ctx);
            if (running != null) {
                return running;
            }
            if (this.opened < 0) {
                ConfigTabs.open(null, FreecamTab.ID);
                this.opened = ctx.tick();
                return StepResult.pending();
            }
            Screenshots.parkMouse(mc);
            return mc.gui.screen() instanceof ConfiguratorScreen screen && screen.freecam() && ctx.tick() - this.opened > 5
                    ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            end();
            int[] previous = this.saved;
            if (previous != null) {
                mc.options.guiScale().set(previous[2]);
                mc.getWindow().setWindowed(previous[0], previous[1]);
                mc.resizeGui();
                this.saved = null;
            }
            this.opened = -1;
        }
    }
}
