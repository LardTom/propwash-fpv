package de.lardtom.propwash.input.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.input.DevExtraFunction;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.RemoteDemo;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.addon.AddonInputs;
import de.lardtom.propwash.input.addon.client.AddonSwitchSelfTests;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ExtraFunctionWizardSelfTests {
    public static final String NAME = AddonSwitchSelfTests.GROUP + "per Mixin angehängte Schalterfunktion (" + DevExtraFunction.ENV
            + "=1): Empfänger-Assistent ohne Absturz, Arm-Hinweis frei (simuliert)";
    private static final String RUN = "extra_function_run";
    private static final int SMALL_WIDTH = 1708;
    private static final int SMALL_HEIGHT = 960;
    private static final int SETTLE = 6;
    private static final int WINDOW_WAIT = 60;
    private static final Size[] SIZES = {new Size(1, false), new Size(2, false), new Size(3, false), new Size(4, false), new Size(4, true)};
    private static final List<String> GUIDE_KEYS = List.of("propwash.input.wizard.switches_arm", "propwash.input.wizard.switches_arm_short",
            "propwash.input.wizard.switches_arm_ok", "propwash.input.wizard.switches_arm_ok_short");

    private record Size(int scale, boolean small) {
    }

    private ExtraFunctionWizardSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(NAME, 2400, ExtraFunctionWizardSelfTests::step);
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        SwitchFunction extra = DevExtraFunction.present();
        if (extra == null) {
            ctx.log("kein angehängter Wert in SwitchFunction (" + DevExtraFunction.ENV + " nicht gesetzt) – übersprungen");
            return StepResult.pass();
        }
        Run run = (Run) ctx.state().computeIfAbsent(RUN, key -> new Run(extra));
        StepResult result;
        try {
            result = run.advance(ctx);
        } catch (ReflectiveOperationException | RuntimeException e) {
            result = StepResult.fail("Ausnahme: " + e);
        }
        if (result.status() != StepResult.Status.PENDING) {
            run.restore();
            ctx.log("Ablauf: " + run.walker.trail());
            for (String line : run.notes) {
                ctx.log(line);
            }
        }
        return result;
    }

    static String direct(List<SwitchFunction> functions) {
        List<String> failed = new ArrayList<>();
        for (SwitchFunction function : functions) {
            try {
                RemoteDemo.forSwitch(function, false);
                RemoteDemo.forSwitch(function, true);
                function.supportsToggle();
                function.commandIndex();
                FunctionKeys.entry(function);
                FunctionKeys.keyLabel(function);
                FunctionKeys.actionLabel(function);
                DeviceProfile.key(function);
            } catch (RuntimeException e) {
                failed.add(function.name() + " (" + e.getClass().getSimpleName() + ")");
            }
        }
        return failed.isEmpty() ? "" : String.join(", ", failed);
    }

    private static List<SwitchFunction> shown(ReceiverWizardScreen wizard) throws ReflectiveOperationException {
        Method functions = ReceiverWizardScreen.class.getDeclaredMethod("functions");
        functions.setAccessible(true);
        return List.of((SwitchFunction[]) functions.invoke(wizard));
    }

    private static List<Button> assignButtons(ReceiverWizardScreen wizard) {
        String assign = Component.translatable("propwash.input.wizard.assign").getString();
        String listening = Component.translatable("propwash.input.modes.listening_short").getString();
        Map<Integer, List<Button>> columns = new HashMap<>();
        for (GuiEventListener child : wizard.children()) {
            if (child instanceof Button button) {
                String label = button.getMessage().getString();
                if (assign.equals(label) || listening.equals(label)) {
                    columns.computeIfAbsent(button.getX(), key -> new ArrayList<>()).add(button);
                }
            }
        }
        List<Button> out = new ArrayList<>();
        for (List<Button> column : columns.values()) {
            if (column.size() > out.size()) {
                out = column;
            }
        }
        out.sort(Comparator.comparingInt(Button::getY));
        return out;
    }

    private static List<String> guideLines() {
        List<String> out = new ArrayList<>();
        for (String key : GUIDE_KEYS) {
            out.add(Component.translatable(key).getString());
        }
        return out;
    }

    private static boolean isGuide(String text, List<String> guides) {
        String value = text.strip();
        if (value.endsWith("…")) {
            value = value.substring(0, value.length() - 1).strip();
        }
        if (value.length() < 12) {
            return false;
        }
        for (String guide : guides) {
            if (guide.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static final class Run {
        private final SwitchFunction extra;
        private final WizardRemoteSelfTests.Walker walker = new WizardRemoteSelfTests.Walker(WizardRemoteSelfTests.Stop.SWITCHES, false);
        private final Minecraft mc = Minecraft.getInstance();
        private final int savedScale = this.mc.options.guiScale().get();
        private final int savedWidth;
        private final int savedHeight;
        private final List<String> notes = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();
        private String phase = "direct";
        private int index;
        private int since = -1;
        private boolean resized;
        private boolean restored;

        Run(SwitchFunction extra) {
            this.extra = extra;
            Window window = this.mc.getWindow();
            float density = Math.max(1.0F, window.getPixelDensity());
            this.savedWidth = Math.round(window.getWidth() / density);
            this.savedHeight = Math.round(window.getHeight() / density);
        }

        StepResult advance(SelfTest.StepContext ctx) throws ReflectiveOperationException {
            switch (this.phase) {
                case "direct" -> {
                    if (!AddonInputs.any()) {
                        return StepResult.fail("Beispiel-Add-on nicht geladen (PROPWASH_DEV_EXAMPLE), keine Add-on-Schalter angemeldet");
                    }
                    String failed = direct(List.of(SwitchFunction.values()));
                    if (!failed.isEmpty()) {
                        this.failures.add("Ausnahme für " + failed);
                    } else {
                        RemoteDemo.Target target = RemoteDemo.forSwitch(this.extra, false);
                        this.notes.add("angehängt: " + this.extra.name() + " (Ordinalzahl " + this.extra.ordinal() + "), Funke "
                                + target.motion() + " " + target.index() + ", Gamepad " + RemoteDemo.forSwitch(this.extra, true).motion()
                                + ", umschaltbar " + this.extra.supportsToggle() + ", Befehl " + this.extra.command() + ", Taste "
                                + FunctionKeys.entry(this.extra).mappingName() + "; Add-on-Schalter des Beispiels: " + AddonInputs.switches().size());
                    }
                    this.phase = "walk";
                    return StepResult.pending();
                }
                case "walk" -> {
                    StepResult walked = this.walker.advance(ctx.tick());
                    if (walked.status() != StepResult.Status.PASS) {
                        return walked;
                    }
                    this.phase = "size";
                    this.since = -1;
                    return StepResult.pending();
                }
                default -> {
                    return this.size(ctx);
                }
            }
        }

        private StepResult size(SelfTest.StepContext ctx) throws ReflectiveOperationException {
            Window window = this.mc.getWindow();
            float density = Math.max(1.0F, window.getPixelDensity());
            if (this.index >= SIZES.length) {
                return this.finish(ctx, window);
            }
            Size size = SIZES[this.index];
            if (this.since < 0) {
                this.since = ctx.tick();
                if (size.small()) {
                    window.setWindowed(Math.round(SMALL_WIDTH / density), Math.round(SMALL_HEIGHT / density));
                    this.resized = true;
                }
                this.mc.options.guiScale().set(size.scale());
                this.mc.resizeGui();
                return StepResult.pending();
            }
            if (size.small() && window.getHeight() > SMALL_HEIGHT && ctx.tick() - this.since < WINDOW_WAIT) {
                return StepResult.pending();
            }
            if (ctx.tick() - this.since < SETTLE) {
                return StepResult.pending();
            }
            if (window.getGuiScale() != size.scale()) {
                this.mc.options.guiScale().set(size.scale());
                this.mc.resizeGui();
            }
            if (!(this.mc.gui.screen() instanceof ReceiverWizardScreen wizard) || wizard != this.walker.wizard()) {
                return StepResult.fail("Assistent nicht mehr offen: " + this.mc.gui.screen());
            }
            if (wizard.page() != ReceiverWizardScreen.Page.SWITCHES) {
                return StepResult.fail("Assistent nicht auf der Seite Schalter: " + wizard.page());
            }
            String where = "GUI " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight() + " (Skala " + window.getGuiScale() + ")";
            StepResult checked = this.check(wizard, where);
            if (checked.status() == StepResult.Status.FAIL) {
                this.failures.add(checked.message());
            }
            this.index++;
            this.since = -1;
            return StepResult.pending();
        }

        private StepResult check(ReceiverWizardScreen wizard, String where) throws ReflectiveOperationException {
            List<SwitchFunction> shown = shown(wizard);
            List<Button> buttons = assignButtons(wizard);
            if (buttons.size() != shown.size()) {
                return StepResult.fail(where + ": " + buttons.size() + " Knöpfe „Zuweisen“ für " + shown.size() + " Zeilen");
            }
            wizard.clearStatus();
            List<String> problems = new ArrayList<>();
            for (int i = 0; i < buttons.size(); i++) {
                Button button = buttons.get(i);
                try {
                    LayoutProbe.capture(this.mc, wizard, button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2);
                } catch (RuntimeException e) {
                    problems.add("Maus über „Zuweisen“ von " + shown.get(i).name() + ": " + e);
                    continue;
                }
                if (shown.get(i) == this.extra && !wizard.switchDemoRoom()) {
                    RemoteDemo.Target target = wizard.demo().target();
                    if (target.motion() != RemoteDemo.Motion.SWITCH || target.index() != 2) {
                        problems.add("Funke zeigt für " + this.extra.name() + " " + target.motion() + " " + target.index());
                    }
                }
            }
            int row = shown.indexOf(this.extra);
            if (row >= 0) {
                StepResult listened = this.listen(wizard, buttons.get(row), where);
                if (listened.status() == StepResult.Status.FAIL) {
                    problems.add(listened.message());
                }
            }
            List<LayoutAudit.Finding> findings = LayoutProbe.audit(this.mc, wizard);
            if (!findings.isEmpty()) {
                List<String> described = new ArrayList<>();
                for (LayoutAudit.Finding finding : findings) {
                    described.add(finding.describe());
                }
                problems.add("Layout-Prüfung " + described);
            }
            List<String> guides = guideLines();
            int guideTexts = 0;
            List<LayoutAudit.Box> boxes = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (GuiEventListener child : wizard.children()) {
                if (child instanceof Button button) {
                    boxes.add(LayoutAudit.Box.of(button.getX(), button.getY(), button.getWidth(), button.getHeight()));
                    labels.add(button.getMessage().getString());
                }
            }
            for (LayoutAudit.Text text : LayoutProbe.capture(this.mc, wizard).texts()) {
                if (!isGuide(text.value(), guides) || boxes.stream().anyMatch(box -> box.contains(text.full(), 1))) {
                    continue;
                }
                guideTexts++;
                for (int i = 0; i < boxes.size(); i++) {
                    if (text.full().intersection(boxes.get(i)) != null) {
                        problems.add("Arm-Hinweis „" + text.value() + "“ " + text.full().describe() + " überdeckt von „"
                                + labels.get(i) + "“ " + boxes.get(i).describe());
                    }
                }
            }
            if (guideTexts == 0) {
                problems.add("Arm-Hinweis nicht gezeichnet");
            }
            Button last = buttons.get(buttons.size() - 1);
            String summary = where + ": " + shown.size() + " Zeilen, " + this.extra.name() + (row >= 0 ? " in Zeile " + (row + 1) : " weggelassen (kein Platz)")
                    + ", letzte Zeile endet bei y=" + (last.getY() + last.getHeight()) + ", Funke im Assistenten "
                    + (wizard.switchDemoRoom() ? "unter der Liste" : "auf Hover");
            if (!problems.isEmpty()) {
                return StepResult.fail(summary + ": " + String.join("; ", problems));
            }
            this.notes.add(summary + ", Arm-Hinweis frei, 0 Befunde");
            return StepResult.pass();
        }

        private StepResult listen(ReceiverWizardScreen wizard, Button assign, String where) {
            assign.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            try {
                if (wizard.listening() != this.extra) {
                    return StepResult.fail(where + ": „Zuweisen“ für " + this.extra.name() + " hört nicht zu: " + wizard.listening());
                }
                LayoutProbe.capture(this.mc, wizard);
                RemoteDemo.Target target = wizard.demo().target();
                if (target.motion() != RemoteDemo.Motion.SWITCH || target.index() != 2) {
                    return StepResult.fail(where + ": Funke zeigt beim Zuhören auf " + this.extra.name() + " " + target.motion() + " " + target.index());
                }
            } catch (RuntimeException e) {
                return StepResult.fail(where + ": Zuhören auf " + this.extra.name() + ": " + e);
            } finally {
                Button again = this.listeningButton(wizard);
                if (again != null) {
                    again.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                }
            }
            if (wizard.listening() != null) {
                return StepResult.fail(where + ": Zuhören auf " + this.extra.name() + " endet nicht");
            }
            return StepResult.pass();
        }

        private @Nullable Button listeningButton(ReceiverWizardScreen wizard) {
            String listening = Component.translatable("propwash.input.modes.listening_short").getString();
            for (GuiEventListener child : wizard.children()) {
                if (child instanceof Button button && listening.equals(button.getMessage().getString())) {
                    return button;
                }
            }
            return null;
        }

        private StepResult finish(SelfTest.StepContext ctx, Window window) {
            if (!this.restored) {
                this.restore();
                this.since = ctx.tick();
                return StepResult.pending();
            }
            boolean sized = !this.resized || Math.round(window.getHeight() / Math.max(1.0F, window.getPixelDensity())) >= this.savedHeight;
            if (!sized && ctx.tick() - this.since < WINDOW_WAIT) {
                return StepResult.pending();
            }
            if (!this.failures.isEmpty()) {
                return StepResult.fail(String.join(" | ", this.failures));
            }
            return StepResult.check(sized, SIZES.length + " Größen ohne Ausnahme und mit freiem Arm-Hinweis; Fenster zurück "
                    + window.getWidth() + "x" + window.getHeight());
        }

        void restore() {
            if (this.restored) {
                return;
            }
            this.restored = true;
            this.walker.close();
            if (this.resized) {
                this.mc.getWindow().setWindowed(this.savedWidth, this.savedHeight);
            }
            this.mc.options.guiScale().set(this.savedScale);
            this.mc.resizeGui();
        }
    }
}
