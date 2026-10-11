package de.lardtom.propwash.ui.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.input.client.screen.ReceiverTab;
import de.lardtom.propwash.input.client.screen.ReceiverWizardScreen;
import de.lardtom.propwash.osd.client.OsdConfigTab;
import de.lardtom.propwash.osd.client.OsdTypingSupport;
import de.lardtom.propwash.rescue.client.RescueConfigTab;
import de.lardtom.propwash.ui.NumberEntry;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.ui.client.search.SearchBar;
import de.lardtom.propwash.ui.client.tab.OverviewTab;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.widget.NumberEditor;
import de.lardtom.propwash.ui.client.widget.NumberInput;
import de.lardtom.propwash.ui.client.widget.NumberSpinner;
import de.lardtom.propwash.ui.client.widget.ValueSlider;
import de.lardtom.propwash.video.client.VideoProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SliderTypingSelfTests {
    public static final String PREFIX = "ui: Regler tippen";
    private static final String GUID = "propwash-selftest-typing-radio";
    private static final String NAME = "EdgeTX Propwash Tipptest Joystick";
    private static final int AXES = 8;
    private static final int BUTTONS = 8;
    private static final int SETTLE_TICKS = 3;
    private static final int PREPARE_TRIES = 12;
    private static final double MID_TOLERANCE = 0.03;
    private static final int KEYCODE_TAB = 9;
    private static final String HIDDEN = "nicht sichtbar";
    private static final Map<String, Integer> REQUIRED_STEP_SLIDERS = Map.of(
            RescueConfigTab.ID, 6,
            "race", 3,
            "audio", 3,
            "video", 1,
            "drone_render", 4,
            OsdConfigTab.ID, 4,
            OsdConfigTab.ID + "+optionen", 2,
            ReceiverTab.ID + "+funke", 1);

    private SliderTypingSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(PREFIX + " – jeder Zahlenwert in allen Tabs (Fokus, Ziffern, Grenzen, Esc, auch mit simulierter Funke)", 6000,
                SliderTypingSelfTests::allTabsStep);
        SelfTest.clientStep(PREFIX + " – Sprung aus der Suche, Klick und Tab auf Regler", 400, SliderTypingSelfTests::searchStep);
        SelfTest.clientStep(PREFIX + " – Empfänger-Assistent mit simulierter Funke", 400, SliderTypingSelfTests::wizardStep);
    }

    public static void registerScreenshots(boolean german) {
        String suffix = german ? " deutsch" : "";
        String file = german ? "_de" : "";
        Screenshots.step("bildschirmfotos: Regler GPS-Rettung mit getipptem Wert über der Grenze" + suffix, "configurator_slider_typing" + file, 20,
                new TypingScene(RescueConfigTab.ID, Component.translatable("propwash.rescue.config.altitude"), "500"));
        Screenshots.step("bildschirmfotos: Lautstärke in Prozent getippt" + suffix, "configurator_percent_typing" + file, 20,
                new TypingScene("audio", Component.translatable("propwash.audio.motor_volume"), "85"));
    }

    private static final class Run {
        private final List<String> passes = new ArrayList<>();
        private final List<String> problems = new ArrayList<>();
        private final List<String> hidden = new ArrayList<>();
        private final Map<String, Integer> kinds = new LinkedHashMap<>();
        private final Map<String, Integer> stepSliders = new LinkedHashMap<>();
        private final Map<String, List<String>> seen = new LinkedHashMap<>();
        private final List<String> unprepared = new ArrayList<>();
        private OsdConfigTab.@Nullable Page osdPage;
        private boolean opened;
        private int pass = -1;
        private int widget;
        private int settle;
        private int prepareTries;
        private boolean prepared;
        private int total;
    }

    private static StepResult allTabsStep(SelfTest.StepContext ctx) {
        Run run = (Run) ctx.state().computeIfAbsent("run", k -> new Run());
        Minecraft mc = Minecraft.getInstance();
        if (!run.opened) {
            run.opened = true;
            TransmitterInput.INSTANCE.simulate(null);
            VideoProbe.systems(VideoSystem.CREATIVE, null);
            ConfigInputSelfTests.close();
            ConfigTabs.open(null, OverviewTab.ID);
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            if (ctx.tick() > 200 || run.pass >= 0) {
                return finish(run, ctx, "Konfigurator ist nicht offen (Durchgang " + run.pass + ")");
            }
            return StepResult.pending();
        }
        if (run.pass < 0) {
            for (ConfigTab tab : screen.tabs()) {
                run.passes.add(tab.id());
            }
            run.passes.add(OsdConfigTab.ID + "+optionen");
            run.passes.add(ReceiverTab.ID + "+funke");
            run.passes.add(ModesTab.ID + "+funke");
            startPass(screen, run, 0);
            return StepResult.pending();
        }
        if (run.settle > 0) {
            run.settle--;
            return StepResult.pending();
        }
        String pass = run.passes.get(run.pass);
        String tabId = tabId(pass);
        ConfigTab active = screen.activeTab();
        if (active == null || !tabId.equals(active.id())) {
            run.problems.add(pass + ": Tab öffnet nicht");
            return nextPass(screen, run, ctx);
        }
        if (!run.prepared) {
            if (active instanceof OsdConfigTab osd && run.osdPage == null) {
                run.osdPage = osd.page();
            }
            boolean ready = prepare(screen, active, pass);
            if (!ready && run.prepareTries++ < PREPARE_TRIES) {
                run.settle = 1;
                return StepResult.pending();
            }
            if (!ready) {
                run.unprepared.add(pass);
            }
            run.prepared = true;
            run.settle = 1;
            return StepResult.pending();
        }
        List<AbstractWidget> numeric = numeric(screen);
        if (run.widget >= numeric.size()) {
            return nextPass(screen, run, ctx);
        }
        AbstractWidget widget = numeric.get(run.widget++);
        String kind = kind(widget);
        run.seen.computeIfAbsent(pass, k -> new ArrayList<>()).add(describe(widget) + (widget.visible ? "" : " (verdeckt)"));
        String problem = check(screen, active, widget);
        if (HIDDEN.equals(problem)) {
            run.hidden.add(pass + "/" + describe(widget));
            return StepResult.pending();
        }
        run.total++;
        run.kinds.merge(kind, 1, Integer::sum);
        if (widget instanceof StepSlider) {
            run.stepSliders.merge(pass, 1, Integer::sum);
        }
        if (problem != null) {
            run.problems.add(pass + "/" + describe(widget) + ": " + problem);
        }
        return StepResult.pending();
    }

    private static String tabId(String pass) {
        int plus = pass.indexOf('+');
        return plus < 0 ? pass : pass.substring(0, plus);
    }

    private static void startPass(ConfiguratorScreen screen, Run run, int index) {
        run.pass = index;
        run.widget = 0;
        run.prepared = false;
        run.prepareTries = 0;
        run.settle = SETTLE_TICKS;
        String pass = run.passes.get(index);
        if (pass.endsWith("+funke") && !TransmitterInput.INSTANCE.simulating()) {
            simulateRadio();
        }
        screen.setFocused(null);
        screen.selectTab(tabId(pass));
        screen.rebuild();
    }

    private static StepResult nextPass(ConfiguratorScreen screen, Run run, SelfTest.StepContext ctx) {
        if (screen.activeTab() instanceof OsdConfigTab osd && run.osdPage != null && osd.page() != run.osdPage) {
            osd.switchPage();
        }
        if (run.pass + 1 >= run.passes.size()) {
            return finish(run, ctx, null);
        }
        startPass(screen, run, run.pass + 1);
        return StepResult.pending();
    }

    private static StepResult finish(Run run, SelfTest.StepContext ctx, @Nullable String fatal) {
        TransmitterInput.INSTANCE.simulate(null);
        VideoProbe.detach();
        ConfigInputSelfTests.close();
        ctx.log("getippt: " + run.total + " Zahlenwerte " + run.kinds + ", StepSlider je Tab " + run.stepSliders);
        run.seen.forEach((pass, widgets) -> ctx.log(pass + ": " + widgets));
        if (!run.hidden.isEmpty()) {
            ctx.log("nicht sichtbar (ausgelassen): " + run.hidden);
        }
        if (!run.unprepared.isEmpty()) {
            ctx.log("Vorbereitung unvollständig: " + run.unprepared);
        }
        if (fatal != null) {
            return StepResult.fail(fatal);
        }
        List<String> problems = new ArrayList<>(run.problems);
        for (Map.Entry<String, Integer> required : REQUIRED_STEP_SLIDERS.entrySet()) {
            int found = run.stepSliders.getOrDefault(required.getKey(), 0);
            if (found < required.getValue()) {
                problems.add(required.getKey() + ": nur " + found + " von " + required.getValue() + " Reglern geprüft");
            }
        }
        if (ConfigInputSelfTests.textInputActive()) {
            problems.add("Texteingabe bleibt nach dem Schließen aktiv");
        }
        if (problems.isEmpty()) {
            return StepResult.pass();
        }
        problems.forEach(p -> ctx.log("Problem: " + p));
        return StepResult.fail(problems.size() + " Probleme, erstes: " + problems.getFirst());
    }

    private static void simulateRadio() {
        DeviceProfile profile = ProfileDefaults.radio(NAME, AXES);
        SimulatedRadio radio = new SimulatedRadio(GUID, NAME, profile, AXES, BUTTONS);
        for (int i = 0; i < AXES; i++) {
            radio.setAxis(i, i == 2 ? 1.0F : 0.0F);
        }
        TransmitterInput.INSTANCE.simulate(radio);
    }

    private static boolean prepare(ConfiguratorScreen screen, ConfigTab tab, String pass) {
        if (tab instanceof OsdConfigTab osd) {
            return OsdTypingSupport.selectElement(osd, pass.endsWith("+optionen"));
        }
        if (!"video".equals(tab.id())) {
            return true;
        }
        Component fovSource = Component.translatable("propwash.video.config.fov_source");
        for (GuiEventListener child : screen.children()) {
            if (child instanceof CycleButton<?> button && Boolean.TRUE.equals(button.getValue())
                    && button.getMessage().getString().startsWith(fovSource.getString())) {
                button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                screen.rebuild();
                return false;
            }
        }
        return true;
    }

    static List<AbstractWidget> numeric(Screen screen) {
        List<AbstractWidget> out = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && (child instanceof NumberInput || child instanceof AbstractSliderButton)) {
                out.add(widget);
            }
        }
        return out;
    }

    static String kind(AbstractWidget widget) {
        return switch (widget) {
            case StepSlider s -> "StepSlider";
            case ValueSlider s -> "ValueSlider";
            case NumberSpinner s -> "NumberSpinner";
            default -> widget.getClass().getSimpleName();
        };
    }

    static String describe(AbstractWidget widget) {
        Component message = widget.getMessage();
        ThemeText.Split split = widget instanceof StepSlider ? ThemeText.splitLabel(message) : null;
        return kind(widget) + " „" + (split != null ? split.label().getString() : message.getString()) + "“";
    }

    public static @Nullable String check(Screen screen, @Nullable ConfigTab tab, AbstractWidget widget) {
        if (!(widget instanceof NumberInput input)) {
            return "Regler ohne Texteingabe";
        }
        if (!widget.visible && tab != null) {
            tab.reveal(widget);
        }
        if (!widget.visible) {
            return HIDDEN;
        }
        if (!input.typeable()) {
            return "nicht tippbar";
        }
        NumberEditor editor = input.editor();
        if (!editor.hasRange()) {
            return "ohne Bereich für die Eingabe";
        }
        Minecraft mc = Minecraft.getInstance();
        boolean wasActive = widget.active;
        widget.active = true;
        try {
            screen.setFocused(null);
            screen.setFocused(widget);
            if (screen.getFocused() != widget) {
                return "lässt sich nicht fokussieren";
            }
            if (!ConfigInputSelfTests.textInputActive()) {
                return "fokussiert, aber SDL-Texteingabe aus: getippte Ziffern kämen nie an";
            }
            double lo = editor.min();
            double hi = editor.max();
            boolean decimals = editor.allowsDecimals();
            double before = number(input.shownValue());
            StringBuilder tried = new StringBuilder();
            boolean applied = false;
            boolean hiApplied = false;
            for (double target : targets(lo, hi, before, decimals)) {
                String text = format(target, decimals);
                ConfigInputSelfTests.type(text);
                if (!input.isEditing()) {
                    return "Ziffern „" + text + "“ starten keine Eingabe";
                }
                NumberEntry.Result result = editor.result();
                if (result.status() != NumberEntry.Status.OK) {
                    input.cancelEdit();
                    return "„" + text + "“ gilt als " + result.status() + " (Bereich " + lo + " – " + hi + ")";
                }
                ConfigInputSelfTests.enter();
                if (input.isEditing()) {
                    return "Enter übernimmt „" + text + "“ nicht";
                }
                String shown = input.shownValue();
                double now = number(shown);
                double tolerance = displayTolerance(shown, target);
                if (target != lo && target != hi) {
                    tolerance = Math.max(tolerance, MID_TOLERANCE * (hi - lo));
                }
                if (Double.isFinite(now) && Math.abs(now - target) <= tolerance) {
                    applied = true;
                    hiApplied = target == hi;
                    break;
                }
                tried.append(text).append(" → „").append(shown).append("“; ");
            }
            if (!applied) {
                return "getippte Werte kommen nicht an: " + tried;
            }
            String over = format(hi + Math.max(1.0, hi - lo), decimals);
            ConfigInputSelfTests.type(over);
            NumberEntry.Status status = editor.result().status();
            if (status != NumberEntry.Status.CLAMPED) {
                input.cancelEdit();
                return "„" + over + "“ wird nicht als zu groß erkannt: " + status;
            }
            ConfigInputSelfTests.enter();
            if (input.isEditing() || !editor.noticeActive()) {
                return "Begrenzen von „" + over + "“ ohne Hinweis";
            }
            String clamped = input.shownValue();
            double clampedValue = number(clamped);
            if (hiApplied && Double.isFinite(clampedValue) && Math.abs(clampedValue - hi) > displayTolerance(clamped, hi)) {
                return "„" + over + "“ nicht auf " + hi + " begrenzt: „" + clamped + "“";
            }
            ConfigInputSelfTests.type(format(lo, decimals));
            ConfigInputSelfTests.escape();
            if (input.isEditing()) {
                return "Esc beendet die Eingabe nicht";
            }
            if (mc.gui.screen() != screen) {
                return "Esc schließt das Fenster statt die Eingabe zu verwerfen";
            }
            if (!clamped.equals(input.shownValue())) {
                return "Esc stellt den Wert nicht wieder her: „" + clamped + "“ → „" + input.shownValue() + "“";
            }
            return null;
        } finally {
            if (input.isEditing()) {
                input.cancelEdit();
            }
            if (mc.gui.screen() == screen) {
                screen.setFocused(null);
            }
            widget.active = wasActive;
        }
    }

    private static List<Double> targets(double lo, double hi, double before, boolean decimals) {
        double mid = (lo + hi) / 2.0;
        mid = decimals ? Math.round(mid * 100.0) / 100.0 : Math.round(mid);
        List<Double> out = new ArrayList<>();
        boolean highFirst = !Double.isFinite(before) || Math.abs(hi - before) >= Math.abs(lo - before);
        out.add(highFirst ? hi : lo);
        out.add(highFirst ? lo : hi);
        if (mid > lo && mid < hi) {
            out.add(mid);
        }
        return out;
    }

    static String format(double value, boolean decimals) {
        if (!decimals || value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        String plain = BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return plain.replace('.', NumberEditor.separator());
    }

    static double number(String shown) {
        String part = NumberEntry.numericPart(shown, '.');
        if (part.isEmpty()) {
            return Double.NaN;
        }
        return NumberEntry.parse(part).orElse(Double.NaN);
    }

    private static double displayTolerance(String shown, double target) {
        String part = NumberEntry.numericPart(shown, '.');
        int dot = part.indexOf('.');
        int digits = dot < 0 ? 0 : part.length() - dot - 1;
        return 0.5 * Math.pow(10.0, -digits) + 1.0E-6 * Math.max(1.0, Math.abs(target));
    }

    private static @Nullable AbstractWidget findStepSlider(Screen screen, Component label) {
        String wanted = label.getString();
        for (AbstractWidget widget : numeric(screen)) {
            ThemeText.Split split = ThemeText.splitLabel(widget.getMessage());
            if (widget instanceof StepSlider && split != null && split.label().getString().equals(wanted)) {
                return widget;
            }
        }
        return null;
    }

    private static StepResult searchStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        int phase = (int) ctx.state().getOrDefault("phase", 0);
        if (phase == 0) {
            ConfigInputSelfTests.close();
            ConfigTabs.open(null, OverviewTab.ID);
            ctx.state().put("phase", 1);
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || screen.search() == null) {
            return ctx.tick() > 200 ? fail("Konfigurator mit Suchfeld öffnet nicht") : StepResult.pending();
        }
        SearchBar search = screen.search();
        switch (phase) {
            case 1 -> {
                Component altitude = Component.translatable("propwash.rescue.config.altitude");
                search.focus();
                search.type(altitude.getString());
                if (search.hits().isEmpty() || !search.hits().getFirst().entry().label().equals(altitude.getString())) {
                    return fail("Suche findet „" + altitude.getString() + "“ nicht zuerst");
                }
                ConfigInputSelfTests.enter();
                ConfigTab active = screen.activeTab();
                if (!(active instanceof RescueConfigTab) || !(screen.getFocused() instanceof StepSlider slider)) {
                    return fail("Sprung fokussiert den Regler „" + altitude.getString() + "“ nicht: " + screen.getFocused());
                }
                if (!ConfigInputSelfTests.textInputActive()) {
                    return fail("nach dem Sprung ist die SDL-Texteingabe aus");
                }
                ConfigInputSelfTests.type("75");
                ConfigInputSelfTests.enter();
                if (slider.isEditing() || Math.abs(slider.current() - 75.0) > 1.0E-6) {
                    return fail("nach dem Sprung getippt 75, Regler steht auf " + slider.current());
                }
                ConfigInputSelfTests.type("60");
                ConfigInputSelfTests.press(InputConstants.KEY_TAB, KEYCODE_TAB, 0);
                if (slider.isEditing() || Math.abs(slider.current() - 60.0) > 1.0E-6) {
                    return fail("Tab übernimmt die Eingabe 60 nicht: " + slider.current());
                }
                screen.selectTab("audio");
                ctx.state().put("phase", 2);
                return StepResult.pending();
            }
            case 2 -> {
                AbstractWidget volume = findStepSlider(screen, Component.translatable("propwash.audio.motor_volume"));
                if (!(volume instanceof StepSlider slider)) {
                    return ctx.tick() > 300 ? fail("Regler Motor-Lautstärke fehlt im Audio-Tab") : StepResult.pending();
                }
                clickTrack(screen, slider);
                if (screen.getFocused() != slider || !ConfigInputSelfTests.textInputActive()) {
                    return fail("Klick auf den Regler fokussiert nicht oder die Texteingabe bleibt aus");
                }
                ConfigInputSelfTests.type("35");
                if (!"35".equals(slider.editText())) {
                    return fail("Eingabe „" + slider.editText() + "“ statt „35“");
                }
                ConfigInputSelfTests.enter();
                if (Math.abs(slider.current() - 0.35) > 1.0E-6 || !slider.shownValue().contains("35")) {
                    return fail("35 % nicht übernommen: " + slider.current() + " „" + slider.shownValue() + "“");
                }
                ConfigInputSelfTests.type("1");
                ConfigInputSelfTests.press(InputConstants.KEY_BACKSPACE, 8, 0);
                ConfigInputSelfTests.type("2");
                ConfigInputSelfTests.escape();
                if (slider.isEditing() || Math.abs(slider.current() - 0.35) > 1.0E-6 || !(mc.gui.screen() instanceof ConfiguratorScreen)) {
                    return fail("Esc verwirft die Eingabe nicht oder schließt den Konfigurator");
                }
                ConfigInputSelfTests.close();
                return StepResult.check(!ConfigInputSelfTests.textInputActive(), "Texteingabe bleibt nach dem Schließen aktiv");
            }
            default -> {
                return fail("unbekannte Phase");
            }
        }
    }

    public static void typeText(String text) {
        ConfigInputSelfTests.type(text);
    }

    public static void pressEnter() {
        ConfigInputSelfTests.enter();
    }

    public static boolean textInputActive() {
        return ConfigInputSelfTests.textInputActive();
    }

    public static void clickAt(Screen screen, double x, double y) {
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private static void clickTrack(Screen screen, AbstractWidget widget) {
        double x = widget.getX() + widget.getWidth() - 6.0;
        double y = widget.getY() + widget.getHeight() / 2.0;
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private static StepResult fail(String reason) {
        ConfigInputSelfTests.close();
        return StepResult.fail(reason);
    }

    private static StepResult wizardStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            ConfigInputSelfTests.close();
            simulateRadio();
            mc.gui.setScreen(new ReceiverWizardScreen(null, InputConfig.get(), result -> {
            }));
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof ReceiverWizardScreen wizard)) {
            return endWizard("Assistent ist nicht offen");
        }
        List<AbstractWidget> sliders = numeric(wizard);
        if (sliders.size() < 2) {
            return ctx.tick() > 100 ? endWizard("Geräteseite ohne Regler für Totzone und Gas-Tempo: " + sliders.size()) : StepResult.pending();
        }
        List<String> problems = new ArrayList<>();
        for (AbstractWidget slider : sliders) {
            String problem = check(wizard, null, slider);
            if (problem != null) {
                problems.add(describe(slider) + ": " + problem);
            }
        }
        if (!(mc.gui.screen() instanceof ReceiverWizardScreen)) {
            return endWizard("Assistent nach dem Tippen geschlossen");
        }
        ctx.log("Assistent: " + sliders.size() + " Regler getippt");
        return endWizard(problems.isEmpty() ? null : String.join("; ", problems));
    }

    private static StepResult endWizard(@Nullable String problem) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ReceiverWizardScreen) {
            mc.gui.setScreen(null);
        }
        TransmitterInput.INSTANCE.simulate(null);
        if (problem == null && ConfigInputSelfTests.textInputActive()) {
            problem = "Texteingabe bleibt nach dem Assistenten aktiv";
        }
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    private static final class TypingScene implements Screenshots.Scene {
        private final String tabId;
        private final Component label;
        private final String text;
        private int phase;

        private TypingScene(String tabId, Component label, String text) {
            this.tabId = tabId;
            this.label = label;
            this.text = text;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.phase == 0) {
                ConfigInputSelfTests.close();
                ConfigTabs.open(null, this.tabId);
                this.phase = 1;
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || screen.activeTab() == null
                    || !this.tabId.equals(screen.activeTab().id())) {
                return ctx.tick() > 200 ? StepResult.fail("Tab " + this.tabId + " öffnet nicht") : StepResult.pending();
            }
            AbstractWidget widget = findStepSlider(screen, this.label);
            if (!(widget instanceof StepSlider slider)) {
                return ctx.tick() > 200 ? StepResult.fail("Regler „" + this.label.getString() + "“ fehlt") : StepResult.pending();
            }
            if (!slider.isEditing()) {
                screen.setFocused(slider);
                ConfigInputSelfTests.type(this.text);
            }
            return StepResult.check(slider.isEditing() && this.text.equals(slider.editText()), "Eingabe startet nicht");
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            ConfigInputSelfTests.close();
            this.phase = 0;
        }
    }
}
