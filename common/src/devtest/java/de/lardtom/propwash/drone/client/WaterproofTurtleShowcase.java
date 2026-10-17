package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.server.client.WorldRulesTab;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class WaterproofTurtleShowcase {
    private WaterproofTurtleShowcase() {
    }

    public static void register() {
        Screenshots.step("bildschirmfotos: Turtle-Modus im Tab Modi", "configurator_modes_turtle", 20, new TabScene(ModesTab.ID));
        Screenshots.step("bildschirmfotos: Nach Absturz abschalten im Tab Welt-Regeln", "configurator_crash_disarm", 20,
                new TabScene(WorldRulesTab.ID));
        SelfTest.switchStep("bildschirmfotos: Turtle-Modus – Sprache Deutsch", "bildschirmfotos: Turtle-Modus – Sprache zurücksetzen", 1200,
                ctx -> Screenshots.language(ctx, "de_de"));
        Screenshots.step("bildschirmfotos: Turtle-Modus im Tab Modi deutsch", "configurator_modes_turtle_de", 20, new TabScene(ModesTab.ID));
        Screenshots.step("bildschirmfotos: Nach Absturz abschalten im Tab Welt-Regeln deutsch", "configurator_crash_disarm_de", 20,
                new TabScene(WorldRulesTab.ID));
        SelfTest.clientStep("bildschirmfotos: Turtle-Modus – Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
    }

    private static final class TabScene implements Screenshots.Scene {
        private final String tabId;
        private boolean revealed;

        private TabScene(String tabId) {
            this.tabId = tabId;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                ConfigTabs.open(null, this.tabId);
                return ctx.tick() > 200 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
            }
            ConfigTab active = screen.activeTab();
            if (active == null || !this.tabId.equals(active.id())) {
                screen.selectTab(this.tabId);
                return StepResult.pending();
            }
            if (active instanceof ModesTab modes && modes.selected() != SwitchFunction.TURTLE) {
                modes.select(SwitchFunction.TURTLE);
                return StepResult.pending();
            }
            if (active instanceof WorldRulesTab rules && !this.revealed) {
                rules.row(Identifier.parse(WorldRuleSpecs.CRASH_DISARM.id())).ifPresent(rules::reveal);
                this.revealed = true;
                return StepResult.pending();
            }
            return ctx.tick() > 20 ? StepResult.pass() : StepResult.pending();
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
        }
    }
}
