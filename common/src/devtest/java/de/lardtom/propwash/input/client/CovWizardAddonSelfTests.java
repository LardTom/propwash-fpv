package de.lardtom.propwash.input.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.addon.AddonInputs;
import de.lardtom.propwash.input.client.screen.ReceiverWizardScreen;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.LayoutProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public final class CovWizardAddonSelfTests {
    private static final String NAME = "input: Abdeckung – Empfänger-Assistent mit Add-on-Schaltern fragt nur Propwashs Funktionen ab (simuliert)";
    private static final String WALKER = "cov_wizard_walker";
    private static final String STAGE = "cov_wizard_stage";
    private static final String INDEX = "cov_wizard_index";
    private static final String ASKED = "cov_wizard_asked";
    private static final String MARK = "cov_wizard_mark";

    private CovWizardAddonSelfTests() {
    }

    public static final String NOCLIP_NAME = "input: Abdeckung – Empfänger-Assistent mit Noclip 0.1.x: Maus über jede Schalterzeile ohne Absturz";

    public static void register() {
        SelfTest.clientStep(NAME, 3000, CovWizardAddonSelfTests::step);
        SelfTest.clientStep(NOCLIP_NAME, 3000, CovWizardAddonSelfTests::noclipHoverStep);
    }

    private static String demoTargets() {
        List<String> failed = new ArrayList<>();
        for (SwitchFunction function : SwitchFunction.values()) {
            try {
                de.lardtom.propwash.input.RemoteDemo.forSwitch(function, false);
                function.supportsToggle();
            } catch (RuntimeException e) {
                failed.add(function.name() + " (" + e.getClass().getSimpleName() + ")");
            }
        }
        return failed.isEmpty() ? "alle " + SwitchFunction.values().length + " Werte ohne Ausnahme" : "Ausnahme bei " + failed;
    }

    @SuppressWarnings("unchecked")
    private static StepResult noclipHoverStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!de.lardtom.propwash.platform.Services.PLATFORM.isModLoaded("pwnoclip")) {
            ctx.log("Noclip nicht geladen – übersprungen (Lauf mit dem Noclip-Jar in mods/)");
            return StepResult.pass();
        }
        WizardRemoteSelfTests.Walker walker = (WizardRemoteSelfTests.Walker) state.computeIfAbsent(WALKER,
                key -> new WizardRemoteSelfTests.Walker(WizardRemoteSelfTests.Stop.SWITCHES, false));
        try {
            if (!state.containsKey("walked")) {
                StepResult walked = walker.advance(ctx.tick());
                if (walked.status() != StepResult.Status.PASS) {
                    if (walked.status() == StepResult.Status.FAIL) {
                        walker.close();
                    }
                    return walked;
                }
                state.put("walked", true);
                ctx.log("RemoteDemo.forSwitch und supportsToggle: " + demoTargets() + "; Zeilen des Assistenten " + shownFunctions(walker.wizard()));
                state.put(INDEX, 0);
                state.put(MARK, ctx.tick());
                return StepResult.pending();
            }
            ReceiverWizardScreen wizard = walker.wizard();
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() != wizard) {
                walker.close();
                return StepResult.fail("Assistent ist nicht mehr offen: " + mc.gui.screen());
            }
            if (ctx.tick() - (int) state.get(MARK) < 4) {
                return StepResult.pending();
            }
            List<Button> buttons = assignButtons(wizard);
            int index = (int) state.get(INDEX);
            if (index >= buttons.size()) {
                walker.close();
                ctx.log(buttons.size() + " Schalterzeilen mit der Maus berührt, kein Absturz");
                return StepResult.pass();
            }
            Button button = buttons.get(index);
            double gx = button.getX() + button.getWidth() / 2.0;
            double gy = button.getY() + button.getHeight() / 2.0;
            double x = gx * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            double y = gy * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
            mc.mouseHandler.onMove(mc.getWindow().handle(), x, y, 0.0, 0.0);
            state.put(INDEX, index + 1);
            state.put(MARK, ctx.tick());
            return StepResult.pending();
        } catch (ReflectiveOperationException | RuntimeException e) {
            walker.close();
            return StepResult.fail("Assistent mit Noclip: " + e);
        }
    }

    private static List<Button> assignButtons(ReceiverWizardScreen wizard) {
        String assign = Component.translatable("propwash.input.wizard.assign").getString();
        String listening = Component.translatable("propwash.input.modes.listening_short").getString();
        Integer column = null;
        for (GuiEventListener child : wizard.children()) {
            if (child instanceof Button button && assign.equals(button.getMessage().getString())) {
                column = button.getX();
                break;
            }
        }
        List<Button> out = new ArrayList<>();
        for (GuiEventListener child : wizard.children()) {
            if (child instanceof Button button && column != null && button.getX() == column
                    && (assign.equals(button.getMessage().getString()) || listening.equals(button.getMessage().getString()))) {
                out.add(button);
            }
        }
        out.sort(Comparator.comparingInt(Button::getY));
        return out;
    }

    private static List<SwitchFunction> shownFunctions(ReceiverWizardScreen wizard) throws ReflectiveOperationException {
        Method functions = ReceiverWizardScreen.class.getDeclaredMethod("functions");
        functions.setAccessible(true);
        return List.of((SwitchFunction[]) functions.invoke(wizard));
    }

    private static List<String> addonNames() {
        List<String> names = new ArrayList<>();
        for (SwitchSpec spec : AddonInputs.switches()) {
            names.add(spec.id().toString());
            names.add(Component.translatable(spec.nameKey()).getString());
        }
        for (AxisSpec spec : AddonInputs.axes()) {
            names.add(spec.id().toString());
            names.add(Component.translatable(spec.nameKey()).getString());
        }
        return names;
    }

    private static void press(Button button) {
        button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
    }

    @SuppressWarnings("unchecked")
    private static StepResult step(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!AddonInputs.any()) {
            ctx.log("kein Add-on-Schalter angemeldet (PROPWASH_DEV_EXAMPLE=off) – übersprungen");
            return StepResult.pass();
        }
        WizardRemoteSelfTests.Walker walker = (WizardRemoteSelfTests.Walker) state.computeIfAbsent(WALKER,
                key -> new WizardRemoteSelfTests.Walker(WizardRemoteSelfTests.Stop.SWITCHES, false));
        try {
            StepResult result = advance(ctx, walker, state);
            if (result.status() != StepResult.Status.PENDING) {
                walker.close();
                ctx.log("Ablauf: " + walker.trail());
            }
            return result;
        } catch (ReflectiveOperationException | RuntimeException e) {
            walker.close();
            return StepResult.fail("Assistent: " + e);
        }
    }

    @SuppressWarnings("unchecked")
    private static StepResult advance(SelfTest.StepContext ctx, WizardRemoteSelfTests.Walker walker, Map<String, Object> state)
            throws ReflectiveOperationException {
        String stage = (String) state.getOrDefault(STAGE, "walk");
        int tick = ctx.tick();
        List<SwitchFunction> asked = (List<SwitchFunction>) state.computeIfAbsent(ASKED, key -> new ArrayList<SwitchFunction>());
        switch (stage) {
            case "walk" -> {
                StepResult walked = walker.advance(tick);
                if (walked.status() != StepResult.Status.PASS) {
                    return walked;
                }
                ReceiverWizardScreen wizard = walker.wizard();
                List<String> names = addonNames();
                List<String> problems = new ArrayList<>();
                for (LayoutAudit.Text text : LayoutProbe.capture(Minecraft.getInstance(), wizard).texts()) {
                    for (String name : names) {
                        if (!name.isBlank() && text.value().contains(name)) {
                            problems.add("Seite Schalter zeigt „" + text.value() + "“ (Add-on " + name + ")");
                        }
                    }
                }
                if (!problems.isEmpty()) {
                    return StepResult.fail(String.join("; ", problems));
                }
                state.put(INDEX, 0);
                state.put(STAGE, "press");
                state.put(MARK, tick);
                return StepResult.pending();
            }
            case "press" -> {
                ReceiverWizardScreen wizard = walker.wizard();
                if (Minecraft.getInstance().gui.screen() != wizard) {
                    return StepResult.fail("Assistent ist nicht mehr offen: " + Minecraft.getInstance().gui.screen());
                }
                if (tick - (int) state.get(MARK) < 3) {
                    return StepResult.pending();
                }
                List<Button> buttons = assignButtons(wizard);
                int index = (int) state.get(INDEX);
                if (index >= buttons.size()) {
                    state.put(STAGE, "finish");
                    return StepResult.pending();
                }
                Button button = buttons.get(index);
                if (!button.active) {
                    if (tick - (int) state.get(MARK) < 40) {
                        return StepResult.pending();
                    }
                    List<String> labels = new ArrayList<>();
                    for (Button each : buttons) {
                        labels.add(each.getMessage().getString() + (each.active ? "" : " (aus)") + "@" + each.getY());
                    }
                    return StepResult.fail("Knopf „Zuweisen“ Nr. " + (index + 1) + " ist aus, Abfrage läuft für " + wizard.listening() + ", Knöpfe "
                            + labels + ", bisher " + asked);
                }
                press(button);
                state.put(STAGE, "listen");
                state.put(MARK, tick);
                return StepResult.pending();
            }
            case "listen" -> {
                ReceiverWizardScreen wizard = walker.wizard();
                if (tick - (int) state.get(MARK) < 2) {
                    return StepResult.pending();
                }
                SwitchFunction listening = wizard.listening();
                if (listening == null) {
                    return StepResult.fail("„Zuweisen“ Nr. " + ((int) state.get(INDEX) + 1) + " fragt keine Funktion ab");
                }
                asked.add(listening);
                String stop = Component.translatable("propwash.input.modes.listening_short").getString();
                Button active = null;
                for (Button button : assignButtons(wizard)) {
                    if (active == null && button.active && stop.equals(button.getMessage().getString())) {
                        active = button;
                    }
                }
                if (active == null) {
                    return StepResult.fail("Abbrechen der Abfrage für " + listening + " fehlt");
                }
                press(active);
                state.put(INDEX, (int) state.get(INDEX) + 1);
                state.put(STAGE, "press");
                state.put(MARK, tick);
                return StepResult.pending();
            }
            case "finish" -> {
                ReceiverWizardScreen wizard = walker.wizard();
                List<SwitchFunction> shown = shownFunctions(wizard);
                List<String> problems = new ArrayList<>();
                if (!asked.equals(shown)) {
                    problems.add("abgefragt " + asked + " statt " + shown);
                }
                if (asked.size() != EnumSet.copyOf(asked).size()) {
                    problems.add("Funktion doppelt abgefragt: " + asked);
                }
                for (SwitchFunction function : asked) {
                    if (function.name().contains(":") || function.accessoryAction() || function.command()) {
                        problems.add(function + " gehört nicht in den Assistenten");
                    }
                }
                List<String> names = new ArrayList<>();
                for (SwitchFunction function : asked) {
                    names.add(function.name());
                }
                ctx.log("abgefragte Funktionen (" + asked.size() + "): " + String.join(", ", names) + "; angemeldete Add-on-Schalter "
                        + AddonInputs.switches().size() + ", Achsen " + AddonInputs.axes().size() + " – nicht abgefragt");
                if (!problems.isEmpty()) {
                    return StepResult.fail(String.join("; ", problems));
                }
                Button finish = WizardRemoteSelfTests.button(wizard, "propwash.input.wizard.finish");
                if (finish == null) {
                    return StepResult.fail("„Fertig“ fehlt");
                }
                press(finish);
                state.put(STAGE, "confirm");
                state.put(MARK, tick);
                return StepResult.pending();
            }
            case "confirm" -> {
                if (tick - (int) state.get(MARK) < 3) {
                    return StepResult.pending();
                }
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() instanceof ReceiverWizardScreen wizard) {
                    @Nullable Button anyway = WizardRemoteSelfTests.button(wizard, "propwash.input.wizard.finish_no_arm");
                    if (anyway == null) {
                        return StepResult.fail("Assistent bleibt nach „Fertig“ offen ohne Rückfrage");
                    }
                    press(anyway);
                    state.put(STAGE, "closed");
                    state.put(MARK, tick);
                    return StepResult.pending();
                }
                return StepResult.pass();
            }
            case "closed" -> {
                if (tick - (int) state.get(MARK) < 3) {
                    return StepResult.pending();
                }
                return Minecraft.getInstance().gui.screen() instanceof ReceiverWizardScreen ? StepResult.fail("Assistent schließt nicht")
                        : StepResult.pass();
            }
            default -> {
                return StepResult.fail("Stufe " + stage);
            }
        }
    }
}
