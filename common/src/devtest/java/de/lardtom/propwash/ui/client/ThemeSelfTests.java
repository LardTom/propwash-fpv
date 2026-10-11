package de.lardtom.propwash.ui.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.input.client.InputClient;
import de.lardtom.propwash.input.client.screen.ReceiverWizardScreen;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

public final class ThemeSelfTests {
    private static final String FRAMES = "frames";
    private static final String THEMED = "themed";
    private static final String VANILLA = "vanilla";
    private static final String SEEN = "seen";

    private ThemeSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("ui: Theme im Konfigurator aktiv", 200, ThemeSelfTests::configuratorStep);
        SelfTest.clientStep("ui: Empfänger-Assistent im Theme", 200, ThemeSelfTests::wizardStep);
        SelfTest.afterLeaveStep("ui: Titelbildschirm bleibt Vanilla", 400, ThemeSelfTests::titleStep);
    }

    private static StepResult configuratorStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ctx.state().put(FRAMES, ThemeStats.frames(ConfiguratorScreen.class));
            ctx.state().put(THEMED, ThemeStats.themedTotal());
            ConfigTabs.open(null, "audio");
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            return ctx.tick() > 60 ? StepResult.fail("Konfigurator öffnete sich nicht: " + mc.gui.screen()) : StepResult.pending();
        }
        if (!ThemedScreen.active()) {
            screen.discardAndClose();
            return StepResult.fail("ThemedScreen.active() ist im Konfigurator falsch");
        }
        boolean drawn = ThemeStats.frames(ConfiguratorScreen.class) > (long) ctx.state().get(FRAMES);
        boolean themed = ThemeStats.themedTotal() > (long) ctx.state().get(THEMED);
        if (!drawn || !themed) {
            if (ctx.tick() > 150) {
                screen.discardAndClose();
                return StepResult.fail("Konfigurator ohne Theme-Zeichnung: Frames " + drawn + ", Widgets " + themed);
            }
            return StepResult.pending();
        }
        ctx.log("Theme-Widgets gezeichnet: " + (ThemeStats.themedTotal() - (long) ctx.state().get(THEMED)));
        screen.discardAndClose();
        return StepResult.pass();
    }

    private static StepResult wizardStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Screen current = mc.gui.screen();
        if (ctx.tick() == 0) {
            if (current != null) {
                mc.gui.setScreen(null);
            }
            ctx.state().put(FRAMES, ThemeStats.frames(ReceiverWizardScreen.class));
            ctx.state().put(THEMED, ThemeStats.themedTotal());
            InputClient.openReceiverWizard(mc);
            return StepResult.pending();
        }
        if (!(current instanceof ReceiverWizardScreen wizard)) {
            if (Boolean.TRUE.equals(ctx.state().get(SEEN))) {
                if (current != null) {
                    mc.gui.setScreen(null);
                }
                return StepResult.pass();
            }
            return ctx.tick() > 60 ? StepResult.fail("Assistent öffnete sich nicht: " + current) : StepResult.pending();
        }
        boolean drawn = ThemeStats.frames(ReceiverWizardScreen.class) > (long) ctx.state().get(FRAMES);
        boolean themed = ThemeStats.themedTotal() > (long) ctx.state().get(THEMED);
        if (!drawn || !themed || !ThemedScreen.active()) {
            if (ctx.tick() > 150) {
                wizard.onClose();
                return StepResult.fail("Assistent ohne Theme-Zeichnung: Frames " + drawn + ", Widgets " + themed);
            }
            return StepResult.pending();
        }
        ctx.state().put(SEEN, Boolean.TRUE);
        wizard.onClose();
        return StepResult.pending();
    }

    private static StepResult titleStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof TitleScreen)) {
            ctx.state().remove(VANILLA);
            return StepResult.pending();
        }
        if (ThemedScreen.active()) {
            return StepResult.fail("ThemedScreen.active() ist auf dem Titelbildschirm wahr");
        }
        if (!ctx.state().containsKey(VANILLA)) {
            ctx.state().put(VANILLA, ThemeStats.vanillaCount(ThemeStats.Kind.BUTTON));
            ctx.state().put(THEMED, ThemeStats.themedTotal());
            return StepResult.pending();
        }
        if (ThemeStats.themedTotal() != (long) ctx.state().get(THEMED)) {
            return StepResult.fail("Theme-Zeichnung auf dem Titelbildschirm aktiv");
        }
        return ThemeStats.vanillaCount(ThemeStats.Kind.BUTTON) > (long) ctx.state().get(VANILLA) + 5L
                ? StepResult.pass()
                : StepResult.pending();
    }
}
