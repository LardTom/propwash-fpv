package de.lardtom.propwash.input.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.MfiHint;
import de.lardtom.propwash.input.client.screen.ReceiverTab;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class MacControllerSelfTests {
    private static final int SWITCH_TICKS = 100;

    private MacControllerSelfTests() {
    }

    static void register() {
        SelfTest.clientStep("input: macOS – SDL_JOYSTICK_MFI vor dem Joystick-Subsystem, Umschalten startet die Erkennung neu", 400,
                MacControllerSelfTests::step);
    }

    public static Screenshots.Scene receiverScene(boolean macOption) {
        return new ReceiverScene(macOption);
    }

    private static @Nullable String expected(MfiHint.Plan plan) {
        return switch (plan.source()) {
            case OPTION_OFF -> MfiHint.OFF;
            case OPTION_ON -> null;
            case ENVIRONMENT -> plan.environment();
            case NOT_MAC -> TransmitterInput.mfiHintValue();
        };
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        TransmitterInput input = TransmitterInput.INSTANCE;
        Scenario s = (Scenario) ctx.state().computeIfAbsent("mac_controllers", key -> new Scenario());
        MfiHint.Plan plan = input.mfiPlan();
        if (!input.available() || plan == null) {
            return ctx.tick() - s.since < SWITCH_TICKS ? StepResult.pending()
                    : StepResult.fail("SDL-Joystick-Subsystem läuft nicht (Phase " + s.phase + ", Plan " + plan + ")");
        }
        String hint = TransmitterInput.mfiHintValue();
        if (!Objects.equals(hint, expected(plan))) {
            return ctx.tick() - s.since < SWITCH_TICKS ? StepResult.pending()
                    : StepResult.fail("SDL_JOYSTICK_MFI ist " + hint + ", erwartet " + expected(plan) + " (" + plan.source() + ")");
        }
        if (!MacControllers.mac() || plan.source() == MfiHint.Source.ENVIRONMENT) {
            ctx.log("Controller-Erkennung: " + plan.source() + ", SDL_JOYSTICK_MFI=" + hint + ", SDL " + TransmitterInput.sdlVersion()
                    + ", Umschalten nur auf macOS ohne Umgebungsvariable");
            return StepResult.check(MacControllers.mac() || plan.source() == MfiHint.Source.NOT_MAC, "Plan außerhalb von macOS: " + plan);
        }
        if (!input.mfiHintBeforeInit()) {
            return StepResult.fail("SDL_JOYSTICK_MFI wurde erst nach dem Start des Joystick-Subsystems gesetzt");
        }
        switch (s.phase) {
            case START -> {
                s.original = InputConfig.get().appleGameController;
                s.starts = input.backendStarts();
                ctx.log("macOS: SDL_JOYSTICK_MFI=" + hint + " vor SDL_InitSubSystem gesetzt (" + plan.source() + "), SDL " + TransmitterInput.sdlVersion());
                commit(!s.original);
                s.enter(Phase.SWITCHED, ctx.tick());
                return StepResult.pending();
            }
            case SWITCHED -> {
                if (input.backendStarts() <= s.starts || plan.appleFramework() == s.original) {
                    return ctx.tick() - s.since < SWITCH_TICKS ? StepResult.pending()
                            : StepResult.fail("Umschalten startet die Erkennung nicht neu (Starts " + input.backendStarts() + ", Plan " + plan + ")");
                }
                ctx.log("Umgeschaltet: Apples GameController " + (plan.appleFramework() ? "an" : "aus") + ", SDL_JOYSTICK_MFI=" + hint + ", Neustart "
                        + input.backendStarts());
                s.starts = input.backendStarts();
                commit(s.original);
                s.enter(Phase.RESTORED, ctx.tick());
                return StepResult.pending();
            }
            case RESTORED -> {
                if (input.backendStarts() <= s.starts || plan.appleFramework() != s.original) {
                    return ctx.tick() - s.since < SWITCH_TICKS ? StepResult.pending()
                            : StepResult.fail("Zurückschalten startet die Erkennung nicht neu (Plan " + plan + ")");
                }
                ctx.log("Zurück: SDL_JOYSTICK_MFI=" + hint + ", Geräte " + input.devices().size());
                return StepResult.pass();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static void commit(boolean appleGameController) {
        InputConfig config = InputConfig.get().copy();
        config.appleGameController = appleGameController;
        InputConfig.commit(config);
    }

    private static final class ReceiverScene implements Screenshots.Scene {
        private final boolean macOption;
        private @Nullable Boolean keyboardFallback;

        private ReceiverScene(boolean macOption) {
            this.macOption = macOption;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.keyboardFallback == null) {
                InputConfig config = InputConfig.get();
                this.keyboardFallback = config.keyboardFallback;
                config.keyboardFallback = false;
                MacControllers.overrideOptionShown(this.macOption);
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                ConfigTabs.open(null, ReceiverTab.ID);
                return ctx.tick() > 200 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
            }
            ConfigTab active = screen.activeTab();
            if (active == null || !ReceiverTab.ID.equals(active.id())) {
                screen.selectTab(ReceiverTab.ID);
                return StepResult.pending();
            }
            return StepResult.pass();
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
            MacControllers.overrideOptionShown(null);
            if (this.keyboardFallback != null) {
                InputConfig.get().keyboardFallback = this.keyboardFallback;
                this.keyboardFallback = null;
            }
        }
    }

    private enum Phase {
        START,
        SWITCHED,
        RESTORED
    }

    private static final class Scenario {
        private Phase phase = Phase.START;
        private int since;
        private int starts;
        private boolean original;

        private void enter(Phase next, int tick) {
            this.phase = next;
            this.since = tick;
        }
    }
}
