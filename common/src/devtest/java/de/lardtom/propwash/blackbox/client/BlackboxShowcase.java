package de.lardtom.propwash.blackbox.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.tab.OverviewTab;
import net.minecraft.client.Minecraft;

public final class BlackboxShowcase {
    private static final int FLIGHT_TICKS = 50;

    private BlackboxShowcase() {
    }

    public static void register() {
        Screenshots.step("bildschirmfotos: Blackbox – Übersicht mit Live-Telemetrie nach dem Flug", "configurator_overview_flying", 20,
                new FlownTabScene(OverviewTab.ID, true));
        Screenshots.step("bildschirmfotos: Blackbox – Übersicht mit Live-Telemetrie, GUI-Skala 2", "configurator_overview_flying_gui2", 20,
                Screenshots.guiScale(2, new FlownTabScene(OverviewTab.ID, true)));
        Screenshots.step("bildschirmfotos: Blackbox – Übersicht mit Live-Telemetrie, GUI-Skala 4", "configurator_overview_flying_gui4", 20,
                Screenshots.guiScale(4, new FlownTabScene(OverviewTab.ID, true)));
        Screenshots.step("bildschirmfotos: Blackbox – Tab mit Logs", "configurator_blackbox_logs", 20, new FlownTabScene(BlackboxTab.ID, false));
        SelfTest.switchStep("bildschirmfotos: Blackbox – Sprache Deutsch", "bildschirmfotos: Blackbox – Sprache zurücksetzen", 1200,
                ctx -> Screenshots.language(ctx, "de_de"));
        Screenshots.step("bildschirmfotos: Blackbox – Übersicht mit Live-Telemetrie nach dem Flug deutsch", "configurator_overview_flying_de", 20,
                new FlownTabScene(OverviewTab.ID, true));
        Screenshots.step("bildschirmfotos: Blackbox – Tab mit Logs deutsch", "configurator_blackbox_logs_de", 20, new FlownTabScene(BlackboxTab.ID, false));
        SelfTest.clientStep("bildschirmfotos: Blackbox – Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
        SelfTest.clientStep("bildschirmfotos: Blackbox – aufräumen", 300, BlackboxFlightFixture::cleanup);
    }

    private static final class FlownTabScene implements Screenshots.Scene {
        private final String tabId;
        private final boolean scrollDown;
        private int phase;
        private int since;

        private FlownTabScene(String tabId, boolean scrollDown) {
            this.tabId = tabId;
            this.scrollDown = scrollDown;
        }

        private void phase(SelfTest.StepContext ctx, int phase) {
            this.phase = phase;
            this.since = ctx.tick();
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.phase == 0) {
                if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                    screen.discardAndClose();
                }
                BlackboxFlightFixture.enableBlackbox();
                StepResult piloting = BlackboxFlightFixture.freshDrone(ctx);
                if (piloting.status() != StepResult.Status.PASS) {
                    return piloting;
                }
                this.phase(ctx, 1);
                return StepResult.pending();
            }
            if (this.phase == 1) {
                StepResult flown = BlackboxFlightFixture.fly(ctx, FLIGHT_TICKS);
                if (flown.status() != StepResult.Status.PASS) {
                    return flown;
                }
                this.phase(ctx, 2);
                return StepResult.pending();
            }
            if (this.phase == 2) {
                DroneSim sim = PilotSession.INSTANCE.sim();
                Telemetry t = sim == null ? null : sim.telemetry();
                boolean settled = t != null && !t.armed && (t.grounded || t.sleeping);
                if (!settled) {
                    return ctx.tick() - this.since > 300 ? StepResult.fail("Drohne landet nicht") : StepResult.pending();
                }
                if (ctx.tick() - this.since < 20) {
                    return StepResult.pending();
                }
                ConfigTabs.open(null, this.tabId);
                this.phase(ctx, 3);
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                return ctx.tick() - this.since > 100 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
            }
            ConfigTab active = screen.activeTab();
            if (active == null || !this.tabId.equals(active.id())) {
                screen.selectTab(this.tabId);
                return StepResult.pending();
            }
            if (this.phase == 3) {
                this.phase(ctx, 4);
                return StepResult.pending();
            }
            if (ctx.tick() - this.since < 4) {
                return StepResult.pending();
            }
            if (BlackboxTab.ID.equals(this.tabId) && !this.scrollDown && active.contentBelow()) {
                return StepResult.fail("Blackbox-Tab: Status und Hinweise nur mit Scrollen sichtbar");
            }
            if (this.scrollDown) {
                screen.mouseScrolled(screen.width - 40.0, screen.height / 2.0, 0.0, -100.0);
            }
            return StepResult.pass();
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
            this.phase = 0;
        }
    }
}
