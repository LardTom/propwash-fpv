package de.lardtom.propwash.ui.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.input.client.screen.KeysTab;
import de.lardtom.propwash.sim.Axis;
import de.lardtom.propwash.sim.RatesType;
import de.lardtom.propwash.ui.NumberEntry;
import de.lardtom.propwash.ui.RateGraph;
import de.lardtom.propwash.ui.RateTemplate;
import de.lardtom.propwash.ui.SettingsSearch;
import de.lardtom.propwash.ui.TuneKeys;
import de.lardtom.propwash.ui.TunePresetBook;
import de.lardtom.propwash.ui.TunePresets;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.search.SearchBar;
import de.lardtom.propwash.ui.client.search.SettingsIndex;
import de.lardtom.propwash.ui.client.tab.OverviewTab;
import de.lardtom.propwash.ui.client.tab.PidTab;
import de.lardtom.propwash.ui.client.tab.PresetPanel;
import de.lardtom.propwash.ui.client.tab.RatesTab;
import de.lardtom.propwash.ui.client.tab.UiTab;
import de.lardtom.propwash.ui.client.widget.ChoiceButton;
import de.lardtom.propwash.ui.client.widget.NumberSpinner;
import de.lardtom.propwash.ui.client.widget.ValueSlider;
import de.lardtom.propwash.video.client.VideoProbe;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLKeyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConfigInputSelfTests {
    private static final String PHASE = "phase";
    private static final String CENTER = TuneKeys.rateKey(Axis.ROLL, "centerRateDps");
    private static final String EXPO = TuneKeys.rateKey(Axis.ROLL, "actualExpo");
    private static final int KEYCODE_RETURN = 13;
    private static final int KEYCODE_ESCAPE = 27;
    private static final int KEYCODE_BACKSPACE = 8;
    private static final int KEYCODE_END = 0x4000004D;
    private static final int KEYCODE_DOWN = 0x40000051;
    private static final int KEYCODE_UP = 0x40000052;
    private static final int KEYCODE_KP_ENTER = 0x40000058;
    private static final int KEYCODE_KP_1 = 0x40000059;
    private static final int KEYCODE_KP_0 = 0x40000062;
    private static final String RATES_NAME = "Selbsttest Rates";
    private static final String RATES_RENAMED = "Selbsttest 2";
    private static final String PID_NAME = "Selbsttest PID";

    private ConfigInputSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("ui: Zahlen in Rates und PID tippen (Ziffern, Ziffernblock, Komma, Grenzen, Esc)", 400, ConfigInputSelfTests::typingStep);
        SelfTest.clientStep("ui: Einstellungssuche mit Vorschlägen, Sprung und Hervorhebung", 400, ConfigInputSelfTests::searchStep);
        SelfTest.clientStep("ui: eigene Presets für Rates und PID (merken, laden, umbenennen, löschen, Code)", 400, ConfigInputSelfTests::presetStep);
    }

    public static void registerScreenshots(boolean german) {
        String suffix = german ? " deutsch" : "";
        String file = german ? "_de" : "";
        Screenshots.step("bildschirmfotos: Rates-Feld mit getipptem Wert über der Grenze" + suffix, "configurator_number_typing" + file, 20,
                new TypingScene());
        Screenshots.step("bildschirmfotos: Einstellungssuche mit Vorschlägen" + suffix, "configurator_search" + file, 20,
                new SearchScene(german ? "totzone" : "deadband", false));
        if (!german) {
            Screenshots.step("bildschirmfotos: Sprung aus der Suche mit Hervorhebung", "configurator_search_jump", 6,
                    new SearchScene("min sat", true));
        }
        Screenshots.step("bildschirmfotos: Rates-Presets mit eigenen Presets" + suffix, "configurator_rate_presets" + file, 20,
                new PresetScene());
        SliderTypingSelfTests.registerScreenshots(german);
    }

    private static long window() {
        return Minecraft.getInstance().getWindow().handle();
    }

    static boolean textInputActive() {
        return SDLKeyboard.SDL_TextInputActive(window());
    }

    static void press(int scancode, int keycode, int modifiers) {
        Minecraft mc = Minecraft.getInstance();
        KeyEvent event = new KeyEvent(scancode, keycode, modifiers);
        mc.keyboardHandler.keyPress(window(), InputConstants.PRESS, event);
        mc.keyboardHandler.keyPress(window(), InputConstants.RELEASE, event);
    }

    static void enter() {
        press(InputConstants.KEY_RETURN, KEYCODE_RETURN, 0);
    }

    static void escape() {
        press(InputConstants.KEY_ESCAPE, KEYCODE_ESCAPE, 0);
    }

    static void type(String text) {
        Minecraft.getInstance().keyboardHandler.textInput(window(), text);
    }

    private static void keypad(String digits) {
        for (char c : digits.toCharArray()) {
            int index = c - '0';
            int scancode = index == 0 ? InputConstants.KEY_NUMPAD0 : InputConstants.KEY_NUMPAD1 + index - 1;
            int keycode = index == 0 ? KEYCODE_KP_0 : KEYCODE_KP_1 + index - 1;
            Minecraft mc = Minecraft.getInstance();
            mc.keyboardHandler.keyPress(window(), InputConstants.PRESS, new KeyEvent(scancode, keycode, InputConstants.MOD_NUM_LOCK));
            type(String.valueOf(c));
            mc.keyboardHandler.keyPress(window(), InputConstants.RELEASE, new KeyEvent(scancode, keycode, InputConstants.MOD_NUM_LOCK));
        }
    }

    static void click(ConfiguratorScreen screen, double x, double y) {
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    static void clickCenter(ConfiguratorScreen screen, AbstractWidget widget) {
        click(screen, widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0);
    }

    private static @Nullable NumberSpinner spinner(ConfiguratorScreen screen, String key) {
        String label = UiTab.label(key).getString();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof NumberSpinner spinner && spinner.getMessage().getString().equals(label)) {
                return spinner;
            }
        }
        return null;
    }

    private static @Nullable ValueSlider slider(ConfiguratorScreen screen, String key) {
        String label = UiTab.label(key).getString();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof ValueSlider slider && slider.getMessage().getString().equals(label)) {
                return slider;
            }
        }
        return null;
    }

    private static @Nullable ConfiguratorScreen open(SelfTest.StepContext ctx, String tabId) {
        Minecraft mc = Minecraft.getInstance();
        if (!ctx.state().containsKey("opened")) {
            ctx.state().put("opened", true);
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ConfigTabs.open(null, tabId);
            return null;
        }
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            return null;
        }
        if (ctx.state().containsKey("ready")) {
            return screen;
        }
        ConfigTab active = screen.activeTab();
        if (active == null || !tabId.equals(active.id())) {
            screen.selectTab(tabId);
            return null;
        }
        ctx.state().put("ready", true);
        return screen;
    }

    static void close() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static StepResult next(SelfTest.StepContext ctx) {
        ctx.state().put(PHASE, phase(ctx) + 1);
        return StepResult.pending();
    }

    private static StepResult fail(String reason) {
        close();
        return StepResult.fail(reason);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1.0E-4;
    }

    private static StepResult typingStep(SelfTest.StepContext ctx) {
        ConfiguratorScreen screen = open(ctx, RatesTab.ID);
        if (screen == null) {
            return ctx.tick() > 200 ? fail("Konfigurator mit Rates-Tab öffnet nicht") : StepResult.pending();
        }
        TuneModel tune = screen.tune();
        switch (phase(ctx)) {
            case 0 -> {
                tune.update(t -> RateTemplate.FREESTYLE.apply(t, tune.defaults()), false);
                screen.rebuild();
                return next(ctx);
            }
            case 1 -> {
                NumberSpinner center = spinner(screen, CENTER);
                if (center == null) {
                    return fail("Feld „" + UiTab.label(CENTER).getString() + "“ fehlt im Rates-Tab");
                }
                if (textInputActive()) {
                    return fail("Texteingabe ist schon vor dem Klick aktiv");
                }
                clickCenter(screen, center);
                if (screen.getFocused() != center || !center.isEditing()) {
                    return fail("Klick auf das Feld fokussiert nicht oder startet keine Eingabe");
                }
                if (!textInputActive()) {
                    return fail("Feld fokussiert, aber SDL-Texteingabe aus: getippte Ziffern kämen nie an");
                }
                type("450");
                if (!"450".equals(center.editText())) {
                    return fail("Ziffernreihe: Eingabe „" + center.editText() + "“ statt „450“");
                }
                enter();
                if (center.isEditing() || !near(tune.value(CENTER), 450.0)) {
                    return fail("Enter übernimmt 450 nicht: " + tune.value(CENTER));
                }
                keypad("720");
                if (!"720".equals(center.editText())) {
                    return fail("Ziffernblock: Eingabe „" + center.editText() + "“ statt „720“ (doppelt oder verschluckt)");
                }
                press(InputConstants.KEY_NUMPADENTER, KEYCODE_KP_ENTER, InputConstants.MOD_NUM_LOCK);
                if (!near(tune.value(CENTER), 720.0)) {
                    return fail("Enter auf dem Ziffernblock übernimmt 720 nicht: " + tune.value(CENTER));
                }
                enter();
                press(InputConstants.KEY_END, KEYCODE_END, 0);
                press(InputConstants.KEY_BACKSPACE, KEYCODE_BACKSPACE, 0);
                type("5");
                if (!"725".equals(center.editText())) {
                    return fail("Ende + Rücktaste + 5 ergibt „" + center.editText() + "“ statt „725“");
                }
                enter();
                if (!near(tune.value(CENTER), 725.0)) {
                    return fail("725 nicht übernommen: " + tune.value(CENTER));
                }
                type("5000");
                if (center.editor().result().status() != NumberEntry.Status.CLAMPED) {
                    return fail("5000 wird nicht als außerhalb der Grenzen erkannt");
                }
                enter();
                if (!near(tune.value(CENTER), 1998.0) || !center.editor().noticeActive()) {
                    return fail("5000 nicht auf 1998 begrenzt oder ohne Hinweis: " + tune.value(CENTER));
                }
                type("999");
                escape();
                if (center.isEditing() || !near(tune.value(CENTER), 1998.0) || !(Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen)) {
                    return fail("Esc verwirft die Eingabe nicht oder schließt den Konfigurator: " + tune.value(CENTER));
                }
                double lower = TuneModel.param(CENTER).stepBy(tune.value(CENTER), -1);
                click(screen, center.getX() + 3, center.getY() + center.getHeight() / 2.0);
                if (!near(tune.value(CENTER), lower) || lower >= 1998.0) {
                    return fail("Pfeil links senkt nicht um einen Schritt: " + tune.value(CENTER));
                }
                NumberSpinner expo = spinner(screen, EXPO);
                if (expo == null) {
                    return fail("Feld „" + UiTab.label(EXPO).getString() + "“ fehlt");
                }
                clickCenter(screen, expo);
                type("0,35");
                enter();
                if (!near(tune.value(EXPO), 0.35)) {
                    return fail("Komma als Dezimaltrenner: Expo " + tune.value(EXPO) + " statt 0,35");
                }
                type(".4");
                enter();
                if (!near(tune.value(EXPO), 0.4)) {
                    return fail("Punkt als Dezimaltrenner: Expo " + tune.value(EXPO) + " statt 0,4");
                }
                type(".");
                enter();
                if (!expo.isEditing() || expo.editor().result().status() != NumberEntry.Status.INVALID) {
                    return fail("Ungültige Eingabe „.“ wird mit Enter übernommen statt angezeigt");
                }
                escape();
                if (expo.isEditing() || !near(tune.value(EXPO), 0.4)) {
                    return fail("Esc nach ungültiger Eingabe stellt den Wert nicht wieder her");
                }
                screen.setFocused(null);
                if (textInputActive()) {
                    return fail("Texteingabe bleibt nach dem Verlassen des Felds aktiv");
                }
                screen.selectTab(PidTab.ID);
                return next(ctx);
            }
            case 2 -> {
                ValueSlider lpf = slider(screen, TuneKeys.GYRO_LPF);
                ValueSlider tpa = slider(screen, TuneKeys.TPA_BREAKPOINT);
                if (lpf == null || tpa == null) {
                    return fail("Regler Gyro-Tiefpass oder TPA-Einsatz fehlt im PID-Tab");
                }
                screen.setFocused(lpf);
                if (!textInputActive()) {
                    return fail("Regler fokussiert, aber SDL-Texteingabe aus");
                }
                type("150");
                enter();
                if (!near(tune.value(TuneKeys.GYRO_LPF), 150.0)) {
                    return fail("Gyro-Tiefpass getippt 150, ist " + tune.value(TuneKeys.GYRO_LPF));
                }
                screen.setFocused(tpa);
                type("65");
                enter();
                if (!near(tune.value(TuneKeys.TPA_BREAKPOINT), 0.65)) {
                    return fail("TPA-Einsatz getippt 65 %, ist " + tune.value(TuneKeys.TPA_BREAKPOINT));
                }
                screen.setFocused(null);
                close();
                return StepResult.check(!textInputActive(), "Texteingabe bleibt nach dem Schließen aktiv");
            }
            default -> {
                return fail("unbekannte Phase");
            }
        }
    }

    private static StepResult searchStep(SelfTest.StepContext ctx) {
        VideoProbe.systems(VideoSystem.ANALOG, null);
        StepResult result = searchWithGoggles(ctx);
        if (result.status() != StepResult.Status.PENDING) {
            VideoProbe.detach();
        }
        return result;
    }

    private static StepResult searchWithGoggles(SelfTest.StepContext ctx) {
        ConfiguratorScreen screen = open(ctx, OverviewTab.ID);
        if (screen == null) {
            return ctx.tick() > 200 ? fail("Konfigurator öffnet nicht") : StepResult.pending();
        }
        SearchBar search = screen.search();
        if (search == null) {
            return fail("Konfigurator ohne Suchfeld");
        }
        switch (phase(ctx)) {
            case 0 -> {
                press(InputConstants.KEY_F, 'f', InputConstants.MOD_CONTROL);
                if (!search.focused() || !textInputActive()) {
                    return fail("Strg+F fokussiert das Suchfeld nicht oder die Texteingabe bleibt aus");
                }
                type("deadband");
                List<SettingsSearch.Hit> hits = search.hits();
                if (!search.open() || hits.isEmpty()) {
                    return fail("keine Vorschläge für „deadband“");
                }
                SettingsSearch.Hit first = hits.getFirst();
                if (!RatesTab.ID.equals(first.entry().tabId()) || !first.entry().label().toLowerCase().contains("deadband")) {
                    return fail("erster Vorschlag für „deadband“: " + first.entry().label() + " (" + first.entry().tabId() + ")");
                }
                SettingsIndex index = search.index();
                if (index == null || !index.failedTabs().isEmpty()) {
                    return fail("Suchindex fehlt oder Tabs ließen sich nicht durchsuchen: " + (index == null ? "-" : index.failedTabs()));
                }
                ctx.log("Suchindex: " + index.entries().size() + " Einträge aus " + screen.tabs().size() + " Tabs in " + index.buildMillis() + " ms");
                search.type("totzone");
                if (search.hits().stream().noneMatch(h -> h.entry().label().equals(UiTab.label(TuneKeys.RC_DEADBAND).getString()))) {
                    return fail("deutsches Wort „totzone“ findet die Totzone im englischen Spiel nicht: " + labels(search.hits()));
                }
                search.type("fov");
                if (search.hits().isEmpty()) {
                    return fail("Synonym „fov“ findet nichts");
                }
                search.type("min sat");
                if (search.hits().isEmpty() || !search.hits().getFirst().entry().label().equals(UiTab.label(TuneKeys.HOLD_MIN_SATS).getString())) {
                    return fail("„min sat“ findet nicht zuerst " + UiTab.label(TuneKeys.HOLD_MIN_SATS).getString() + ": " + labels(search.hits()));
                }
                enter();
                ConfigTab active = screen.activeTab();
                AbstractWidget target = screen.highlighted();
                if (!(active instanceof PidTab) || target == null) {
                    return fail("Enter springt nicht in den PID-Tab oder hebt nichts hervor: " + (active == null ? "-" : active.id()));
                }
                if (!target.visible || target.getY() < screen.contentArea().top() || target.getY() + target.getHeight() > screen.contentArea().bottom()) {
                    return fail("hervorgehobene Einstellung ist nicht ins Bild gescrollt (y " + target.getY() + ")");
                }
                if (!active.contentAbove()) {
                    return fail("PID-Tab wurde für Min. Satelliten nicht gescrollt");
                }
                if (screen.getFocused() != target || search.focused()) {
                    return fail("nach dem Sprung hat die Einstellung nicht den Fokus");
                }
                return next(ctx);
            }
            case 1 -> {
                search.focus();
                KeyMapping mapping = KeysTab.mappings().getFirst();
                String name = Component.translatable(mapping.getName()).getString();
                search.type(name);
                List<SettingsSearch.Hit> hits = search.hits();
                int keyIndex = -1;
                for (int i = 0; i < hits.size(); i++) {
                    if (hits.get(i).entry().kind() == SettingsSearch.Kind.KEY && hits.get(i).entry().label().equals(name)) {
                        keyIndex = i;
                        break;
                    }
                }
                if (keyIndex < 0) {
                    return fail("Tastenbelegung „" + name + "“ nicht gefunden: " + labels(hits));
                }
                for (int i = 0; i < keyIndex; i++) {
                    press(InputConstants.KEY_DOWN, KEYCODE_DOWN, 0);
                }
                press(InputConstants.KEY_DOWN, KEYCODE_DOWN, 0);
                press(InputConstants.KEY_UP, KEYCODE_UP, 0);
                if (search.selected() != keyIndex) {
                    return fail("Pfeiltasten wählen Vorschlag " + search.selected() + " statt " + keyIndex);
                }
                enter();
                if (!(screen.activeTab() instanceof KeysTab) || screen.highlighted() == null) {
                    return fail("Sprung zur Tastenbelegung fehlgeschlagen");
                }
                search.focus();
                String worldRules = Component.translatable("propwash.server.world_rules.tab").getString();
                search.type(worldRules);
                if (search.hits().isEmpty() || search.hits().getFirst().entry().kind() != SettingsSearch.Kind.TAB) {
                    return fail("Tab „" + worldRules + "“ steht nicht oben: " + labels(search.hits()));
                }
                enter();
                ConfigTab active = screen.activeTab();
                if (active == null || !"world_rules".equals(active.id())) {
                    return fail("Sprung zum Tab Welt-Regeln fehlgeschlagen");
                }
                search.focus();
                search.type("x");
                escape();
                if (!search.text().isEmpty() || !search.focused()) {
                    return fail("erstes Esc leert das Suchfeld nicht");
                }
                escape();
                if (search.focused() || !(Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen)) {
                    return fail("zweites Esc verlässt das Suchfeld nicht oder schließt den Konfigurator");
                }
                close();
                return StepResult.pass();
            }
            default -> {
                return fail("unbekannte Phase");
            }
        }
    }

    private static String labels(List<SettingsSearch.Hit> hits) {
        List<String> out = new ArrayList<>();
        for (SettingsSearch.Hit hit : hits) {
            out.add(hit.entry().label() + "@" + hit.entry().tabId());
        }
        return out.toString();
    }

    private static boolean stored(TunePresets.Section section, String name) {
        return TunePresetStore.all(section).stream().anyMatch(p -> p.name().equals(name));
    }

    @SuppressWarnings("unchecked")
    private static StepResult presetStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey("backup")) {
            state.put("backup", List.of(TunePresetStore.all(TunePresets.Section.RATES), TunePresetStore.all(TunePresets.Section.PID)));
        }
        ConfiguratorScreen screen = open(ctx, RatesTab.ID);
        if (screen == null) {
            return ctx.tick() > 200 ? failPresets(state, "Konfigurator mit Rates-Tab öffnet nicht") : StepResult.pending();
        }
        TuneModel tune = screen.tune();
        ConfigTab active = screen.activeTab();
        switch (phase(ctx)) {
            case 0 -> {
                if (!(active instanceof RatesTab rates)) {
                    return failPresets(state, "Rates-Tab nicht aktiv");
                }
                tune.update(t -> RateTemplate.RACING.apply(t, tune.defaults()), false);
                tune.set(CENTER, 125.0);
                PresetPanel presets = rates.presets();
                presets.setName(RATES_NAME);
                presets.save();
                if (!stored(TunePresets.Section.RATES, RATES_NAME)) {
                    return failPresets(state, "Preset wurde nicht gemerkt");
                }
                return next(ctx);
            }
            case 1 -> {
                PresetPanel presets = ((RatesTab) active).presets();
                if (presets.choices().stream().noneMatch(c -> c.own() && c.id().equals("own:" + RATES_NAME))) {
                    return failPresets(state, "eigenes Preset fehlt in der Auswahl neben den eingebauten");
                }
                if (presets.selected() == null || !presets.selected().own()) {
                    return failPresets(state, "gemerktes Preset ist nicht ausgewählt");
                }
                presets.select("builtin:" + RateTemplate.FREESTYLE.name());
                presets.apply();
                if (!near(tune.value(CENTER), 70.0)) {
                    return failPresets(state, "eingebautes Freestyle nicht übernommen: " + tune.value(CENTER));
                }
                presets.select("own:" + RATES_NAME);
                presets.apply();
                if (!near(tune.value(CENTER), 125.0) || !near(tune.value(TuneKeys.rateKey(Axis.YAW, "maxRateDps")), 550.0)) {
                    return failPresets(state, "eigenes Preset nicht übernommen: Mitte " + tune.value(CENTER));
                }
                presets.setName(RATES_RENAMED);
                presets.rename();
                if (stored(TunePresets.Section.RATES, RATES_NAME) || !stored(TunePresets.Section.RATES, RATES_RENAMED)) {
                    return failPresets(state, "Umbenennen fehlgeschlagen");
                }
                return next(ctx);
            }
            case 2 -> {
                PresetPanel presets = ((RatesTab) active).presets();
                if (!presets.select("own:" + RATES_RENAMED)) {
                    return failPresets(state, "umbenanntes Preset fehlt in der Auswahl");
                }
                presets.copyCode();
                String code = Minecraft.getInstance().keyboardHandler.getClipboard();
                TunePresets.Decoded decoded = TunePresets.decode(code).orElse(null);
                if (decoded == null || decoded.section() != TunePresets.Section.RATES || !decoded.name().equals(RATES_RENAMED)
                        || !near(decoded.values().get(CENTER), 125.0)) {
                    return failPresets(state, "Code kopieren liefert keinen gültigen Rates-Code: " + code);
                }
                ctx.log("Rates-Code (" + code.length() + " Zeichen): " + code);
                presets.delete();
                if (!stored(TunePresets.Section.RATES, RATES_RENAMED)) {
                    return failPresets(state, "Löschen ohne Rückfrage");
                }
                presets.delete();
                if (stored(TunePresets.Section.RATES, RATES_RENAMED)) {
                    return failPresets(state, "Löschen nach Rückfrage fehlgeschlagen");
                }
                presets.importCode(code);
                if (!stored(TunePresets.Section.RATES, RATES_RENAMED)) {
                    return failPresets(state, "Code einfügen legt das Preset nicht wieder an");
                }
                if (RateGraph.type(tune.working(), tune.defaults()) != RatesType.ACTUAL) {
                    return failPresets(state, "Rates-Typ nach dem Preset nicht Actual");
                }
                screen.selectTab(PidTab.ID);
                return next(ctx);
            }
            case 3 -> {
                if (!(active instanceof PidTab pid)) {
                    return failPresets(state, "PID-Tab nicht aktiv");
                }
                tune.set(TuneKeys.GYRO_LPF, 150.0);
                PresetPanel presets = pid.presets();
                presets.setName(PID_NAME);
                presets.save();
                return next(ctx);
            }
            case 4 -> {
                PresetPanel presets = ((PidTab) active).presets();
                if (!stored(TunePresets.Section.PID, PID_NAME) || !presets.select("own:" + PID_NAME)) {
                    return failPresets(state, "PID-Preset nicht gemerkt");
                }
                double standard = tune.defaultValue(TuneKeys.GYRO_LPF);
                presets.select(presets.choices().getFirst().id());
                presets.apply();
                if (!near(tune.value(TuneKeys.GYRO_LPF), standard)) {
                    return failPresets(state, "PID-Standard setzt den Gyro-Tiefpass nicht zurück: " + tune.value(TuneKeys.GYRO_LPF));
                }
                presets.select("own:" + PID_NAME);
                presets.apply();
                if (!near(tune.value(TuneKeys.GYRO_LPF), 150.0)) {
                    return failPresets(state, "eigenes PID-Preset nicht übernommen: " + tune.value(TuneKeys.GYRO_LPF));
                }
                restorePresets(state);
                close();
                return StepResult.pass();
            }
            default -> {
                return failPresets(state, "unbekannte Phase");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void restorePresets(Map<String, Object> state) {
        if (state.get("backup") instanceof List<?> lists && lists.size() == 2) {
            restore((List<TunePresets.Preset>) lists.get(0), (List<TunePresets.Preset>) lists.get(1));
        }
    }

    private static void restore(List<TunePresets.Preset> rates, List<TunePresets.Preset> pid) {
        TunePresetStore.commit(TunePresets.Section.RATES, new TunePresetBook.Change(TunePresetBook.Outcome.SAVED, rates, ""));
        TunePresetStore.commit(TunePresets.Section.PID, new TunePresetBook.Change(TunePresetBook.Outcome.SAVED, pid, ""));
    }

    private static StepResult failPresets(Map<String, Object> state, String reason) {
        restorePresets(state);
        return fail(reason);
    }

    private static final class TypingScene implements Screenshots.Scene {
        private int phase;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.phase == 0) {
                close();
                ConfigTabs.open(null, RatesTab.ID);
                this.phase = 1;
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || !(screen.activeTab() instanceof RatesTab)) {
                return ctx.tick() > 200 ? StepResult.fail("Rates-Tab öffnet nicht") : StepResult.pending();
            }
            if (this.phase == 1) {
                screen.tune().update(t -> RateTemplate.FREESTYLE.apply(t, screen.tune().defaults()), false);
                screen.rebuild();
                this.phase = 2;
                return StepResult.pending();
            }
            NumberSpinner center = spinner(screen, CENTER);
            if (center == null) {
                return StepResult.fail("Feld Mittenrate fehlt");
            }
            if (!center.isEditing()) {
                clickCenter(screen, center);
                type("2500");
            }
            return StepResult.check(center.isEditing(), "Eingabe startet nicht");
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            close();
            this.phase = 0;
        }
    }

    private static final class SearchScene implements Screenshots.Scene {
        private final String query;
        private final boolean jump;
        private int phase;

        private SearchScene(String query, boolean jump) {
            this.query = query;
            this.jump = jump;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.phase == 0) {
                close();
                ConfigTabs.open(null, RatesTab.ID);
                this.phase = 1;
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || screen.search() == null) {
                return ctx.tick() > 200 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
            }
            SearchBar search = screen.search();
            if (this.phase == 1) {
                search.focus();
                type(this.query);
                if (search.hits().isEmpty()) {
                    return StepResult.fail("keine Vorschläge für „" + this.query + "“");
                }
                if (this.jump) {
                    enter();
                    return StepResult.check(screen.highlighted() != null, "Sprung ohne Hervorhebung");
                }
                this.phase = 2;
                return StepResult.pending();
            }
            return StepResult.check(search.open(), "Vorschlagsliste ist zu");
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            close();
            this.phase = 0;
        }
    }

    private static final class PresetScene implements Screenshots.Scene {
        private int phase;
        private @Nullable List<List<TunePresets.Preset>> backup;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.phase == 0) {
                this.backup = List.of(TunePresetStore.all(TunePresets.Section.RATES), TunePresetStore.all(TunePresets.Section.PID));
                close();
                ConfigTabs.open(null, RatesTab.ID);
                this.phase = 1;
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || !(screen.activeTab() instanceof RatesTab rates)) {
                return ctx.tick() > 200 ? StepResult.fail("Rates-Tab öffnet nicht") : StepResult.pending();
            }
            TuneModel tune = screen.tune();
            PresetPanel presets = rates.presets();
            if (this.phase == 1) {
                tune.update(t -> RateTemplate.CINE.apply(t, tune.defaults()), false);
                tune.set(CENTER, 35.0);
                presets.setName("Cinematic slow");
                presets.save();
                this.phase = 2;
                return StepResult.pending();
            }
            if (this.phase == 2) {
                tune.update(t -> RateTemplate.FREESTYLE.apply(t, tune.defaults()), false);
                tune.set(TuneKeys.rateKey(Axis.ROLL, "maxRateDps"), 850.0);
                tune.set(TuneKeys.rateKey(Axis.PITCH, "maxRateDps"), 850.0);
                presets.setName("Juicy freestyle");
                presets.save();
                this.phase = 3;
                return StepResult.pending();
            }
            if (this.phase == 3) {
                for (GuiEventListener child : screen.children()) {
                    if (child instanceof ChoiceButton<?> choice && choice.getMessage().getString()
                            .equals(Component.translatable("propwash.ui.rates.preset").getString())) {
                        rates.reveal(choice);
                    }
                }
                screen.setFocused(null);
                this.phase = 4;
                return StepResult.pending();
            }
            return StepResult.check(presets.selected() != null && presets.selected().own(), "eigenes Preset nicht ausgewählt");
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            close();
            if (this.backup != null) {
                restore(this.backup.get(0), this.backup.get(1));
            }
            this.backup = null;
            this.phase = 0;
        }
    }
}
