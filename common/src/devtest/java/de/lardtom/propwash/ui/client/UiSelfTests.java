package de.lardtom.propwash.ui.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.creative.client.CreativeEditorTab;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.client.TuneAccess;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.input.client.screen.KeysTab;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import de.lardtom.propwash.registry.client.ConfigScreenFactory;
import de.lardtom.propwash.ui.TuneEdits;
import de.lardtom.propwash.ui.TuneKeys;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.tab.OverviewTab;
import de.lardtom.propwash.ui.client.tab.LedTab;
import de.lardtom.propwash.ui.client.tab.PidTab;
import de.lardtom.propwash.ui.client.tab.RatesTab;
import de.lardtom.propwash.ui.client.widget.ChoiceButton;
import de.lardtom.propwash.ui.client.widget.ValueSlider;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class UiSelfTests {
    private static final String TAB_INDEX = "tabIndex";
    private static final String PASS = "pass";
    private static final String BASELINE = "baseline";
    private static final String PHASE = "phase";
    private static final String FUTURE = "future";
    private static final String ORIGINAL = "original";
    private static final String EXPECTED = "expected";
    private static final String WAIT = "wait";
    private static final String PID_KEY = "pid.roll.p";

    private UiSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("ui: Konfigurator öffnen", 100, UiSelfTests::openStep);
        SelfTest.clientStep("ui: alle Tabs aufbauen und zeichnen", 600, UiSelfTests::tabsStep);
        SelfTest.clientStep("ui: Beschriftungen passen bei 427x240", 200, UiSelfTests::labelsStep);
        SelfTest.clientStep("ui: Speicherort je Tab und Tasten-Tab im Theme", 200, UiSelfTests::keysTabStep);
        SelfTest.clientStep("ui: PID-Wert über TuneAccess", 400, UiSelfTests::tuneStep);
        ArmAngleSelfTests.register();
        ConfigInputSelfTests.register();
        SliderTypingSelfTests.register();
        ThemeSelfTests.register();
        HoverSweepSelfTests.register();
        SelfTest.afterLeaveStep("ui: Konfigurator nach dem Verlassen geschlossen", 5, ctx -> StepResult.check(
                ConfiguratorScreen.active() == null && !(Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen),
                "Konfigurator ist nach dem Verlassen noch offen"));
        SelfTest.afterLeaveStep("ui: Konfigurator aus der Mod-Liste (ohne Welt)", 600, UiSelfTests::modListStep);
    }

    private static StepResult modListStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            ConfigScreenFactory factory = ClientRegistrar.configScreenFactory();
            Screen parent = mc.gui.screen();
            if (factory == null || parent == null) {
                return StepResult.fail("keine Konfigurations-Screen-Fabrik oder kein Titelbildschirm");
            }
            Screen created = factory.create(parent);
            if (!(created instanceof ConfiguratorScreen)) {
                return StepResult.fail("Fabrik liefert " + created.getClass().getSimpleName());
            }
            ctx.state().put(ORIGINAL, parent);
            mc.gui.setScreen(created);
            return StepResult.pending();
        }
        Screen parent = (Screen) ctx.state().get(ORIGINAL);
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            return StepResult.check(ctx.state().containsKey(PASS) && mc.gui.screen() == parent,
                    "nach dem Schließen nicht zurück im Eltern-Screen, sondern " + (mc.gui.screen() == null ? "null" : mc.gui.screen().getClass().getSimpleName()));
        }
        if (!screen.tabErrors().isEmpty()) {
            Map<String, String> errors = screen.tabErrors();
            screen.discardAndClose();
            return StepResult.fail("Tab-Fehler ohne Welt: " + errors);
        }
        List<ConfigTab> tabs = screen.tabs();
        int index = (int) ctx.state().getOrDefault(TAB_INDEX, 0);
        if (index >= tabs.size()) {
            ctx.state().put(PASS, 1);
            screen.discardAndClose();
            return StepResult.pending();
        }
        ConfigTab tab = tabs.get(index);
        if (screen.activeTab() != tab) {
            screen.selectTab(index);
            ctx.state().remove(BASELINE);
            return StepResult.pending();
        }
        if (!ctx.state().containsKey(BASELINE)) {
            ctx.state().put(BASELINE, screen.extractedFrames(tab.id()));
            return StepResult.pending();
        }
        if (screen.extractedFrames(tab.id()) > (int) ctx.state().get(BASELINE)) {
            ctx.state().put(TAB_INDEX, index + 1);
            ctx.state().remove(BASELINE);
        }
        return StepResult.pending();
    }

    private static StepResult openStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ConfigTabs.open(null, OverviewTab.ID);
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            return StepResult.pending();
        }
        List<ConfigTab> tabs = screen.tabs();
        int expected = ConfigTabs.registeredCount() - (CreativeEditorTab.create() == null ? 1 : 0);
        if (tabs.size() != expected) {
            return StepResult.fail("Tabs " + tabs.size() + " von " + expected + " erzeugt");
        }
        if (tabs.size() < 4) {
            return StepResult.fail("nur " + tabs.size() + " Tabs registriert");
        }
        Set<Integer> orders = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (ConfigTab tab : tabs) {
            if (!orders.add(tab.order())) {
                return StepResult.fail("Tab-Reihenfolge " + tab.order() + " doppelt vergeben (" + tab.id() + ")");
            }
            if (!ids.add(tab.id())) {
                return StepResult.fail("Tab-ID doppelt: " + tab.id());
            }
        }
        if (screen.width < ConfiguratorScreen.MIN_WIDTH || screen.height < ConfiguratorScreen.MIN_HEIGHT) {
            ctx.log("GUI kleiner als 427x240: " + screen.width + "x" + screen.height);
        }
        return StepResult.pass();
    }

    private static StepResult tabsStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            if (ctx.tick() == 0) {
                ConfigTabs.open(null, null);
                return StepResult.pending();
            }
            return ctx.state().containsKey(PASS) && (int) ctx.state().get(PASS) >= 2 ? StepResult.pass() : StepResult.pending();
        }
        if (!screen.tabErrors().isEmpty()) {
            Map<String, String> errors = screen.tabErrors();
            screen.discardAndClose();
            return StepResult.fail("Tab-Fehler: " + errors);
        }
        List<ConfigTab> tabs = screen.tabs();
        int pass = (int) ctx.state().getOrDefault(PASS, 0);
        int index = (int) ctx.state().getOrDefault(TAB_INDEX, 0);
        if (index >= tabs.size()) {
            pass++;
            ctx.state().put(PASS, pass);
            ctx.state().put(TAB_INDEX, 0);
            ctx.state().remove(BASELINE);
            if (pass == 1) {
                screen.resize(ConfiguratorScreen.MIN_WIDTH, ConfiguratorScreen.MIN_HEIGHT);
                ctx.log("zweiter Durchlauf bei 427x240");
                return StepResult.pending();
            }
            screen.discardAndClose();
            return StepResult.pending();
        }
        ConfigTab tab = tabs.get(index);
        if (screen.activeTab() != tab) {
            screen.selectTab(index);
            ctx.state().remove(BASELINE);
            return StepResult.pending();
        }
        if (!ctx.state().containsKey(BASELINE)) {
            ctx.state().put(BASELINE, screen.extractedFrames(tab.id()));
            return StepResult.pending();
        }
        if (screen.extractedFrames(tab.id()) > (int) ctx.state().get(BASELINE)) {
            ctx.state().put(TAB_INDEX, index + 1);
            ctx.state().remove(BASELINE);
        }
        return StepResult.pending();
    }

    private static StepResult labelsStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ConfigTabs.open(null, PidTab.ID);
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            return StepResult.pending();
        }
        screen.resize(ConfiguratorScreen.MIN_WIDTH, ConfiguratorScreen.MIN_HEIGHT);
        List<String> cut = new ArrayList<>();
        for (String tabId : List.of(PidTab.ID, RatesTab.ID, LedTab.ID)) {
            screen.selectTab(tabId);
            for (GuiEventListener child : screen.children()) {
                boolean fits = switch (child) {
                    case ValueSlider slider -> slider.labelFits();
                    case ChoiceButton<?> choice -> choice.labelFits();
                    default -> true;
                };
                if (!fits && child instanceof AbstractWidget widget) {
                    cut.add(tabId + ": " + widget.getMessage().getString());
                }
            }
        }
        screen.discardAndClose();
        return StepResult.check(cut.isEmpty(), "Beschriftung abgeschnitten: " + cut);
    }

    private static StepResult keysTabStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            ConfigTabs.open(null, ModesTab.ID);
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            return StepResult.pending();
        }
        try {
            List<String> droneTabs = new ArrayList<>();
            for (ConfigTab tab : screen.tabs()) {
                if (tab.storage() == ConfigTab.Storage.DRONE) {
                    droneTabs.add(tab.id());
                }
            }
            if (!droneTabs.equals(List.of(PidTab.ID, RatesTab.ID, LedTab.ID))) {
                return StepResult.fail("Tabs mit Speicherort Drohne: " + droneTabs + " statt [pid, rates, led_strip]");
            }
            Button keysButton = null;
            for (GuiEventListener child : screen.children()) {
                if (child instanceof Button button && button.getMessage().getString().equals(
                        Component.translatable("propwash.input.modes.open_controls").getString())) {
                    keysButton = button;
                }
            }
            if (keysButton == null) {
                return StepResult.fail("Modi-Tab ohne Knopf „Tasten …“");
            }
            keysButton.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            if (!(mc.gui.screen() instanceof ConfiguratorScreen) || !(screen.activeTab() instanceof KeysTab keys)) {
                return StepResult.fail("„Tasten …“ verlässt den Konfigurator oder öffnet nicht den Tasten-Tab: " + mc.gui.screen());
            }
            KeyMapping horizon = InputKeys.MODE_HORIZON;
            InputConstants.Key before = keys.key(horizon);
            keys.startListening(horizon);
            if (!keys.capturesInput()) {
                return StepResult.fail("Tasten-Tab wartet nicht auf eine Taste");
            }
            screen.keyPressed(new KeyEvent(InputConstants.KEY_F7, 0, 0));
            if (keys.capturesInput() || !keys.key(horizon).equals(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F7))) {
                return StepResult.fail("Taste F7 nicht übernommen: " + keys.key(horizon).getName());
            }
            keys.startListening(horizon);
            screen.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
            if (!(mc.gui.screen() instanceof ConfiguratorScreen) || !keys.key(horizon).equals(InputConstants.UNKNOWN)) {
                return StepResult.fail("ESC beim Warten entfernt die Belegung nicht oder schließt den Konfigurator");
            }
            keys.assign(horizon, InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_UP));
            if (keys.clashes(horizon, keys.key(horizon)).isEmpty()) {
                return StepResult.fail("Doppelbelegung mit „Nase runter“ wird nicht erkannt");
            }
            screen.discardAndClose();
            if (!InputConstants.getKey(horizon.saveString()).equals(before)) {
                return StepResult.fail("Verwerfen ändert trotzdem die Taste");
            }
            return StepResult.pass();
        } finally {
            if (mc.gui.screen() instanceof ConfiguratorScreen open) {
                open.discardAndClose();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static StepResult tuneStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        TuneAccess access = TuneAccess.INSTANCE;
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (mc.player == null) {
            return StepResult.fail("kein Spieler");
        }
        switch (phase) {
            case 0 -> {
                Optional<Item> item = ContentIds.item(ContentIds.DRONE);
                if (item.isEmpty()) {
                    return StepResult.fail("Drohnen-Item propwash:drone ist nicht registriert");
                }
                UUID id = mc.player.getUUID();
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    if (player == null) {
                        throw new IllegalStateException("Spieler fehlt auf dem Server");
                    }
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item.get()));
                }));
                state.put(PHASE, 1);
                state.put(WAIT, ctx.tick());
                return StepResult.pending();
            }
            case 1 -> {
                CompletableFuture<Void> future = (CompletableFuture<Void>) state.get(FUTURE);
                if (future.isCompletedExceptionally()) {
                    return StepResult.fail("Item geben fehlgeschlagen: " + future.handle((v, e) -> String.valueOf(e)).join());
                }
                if (!future.isDone() || !access.available()) {
                    if (ctx.tick() - (int) state.get(WAIT) > 120) {
                        return StepResult.fail("TuneAccess.available() bleibt false mit Drohnen-Item in der Hand");
                    }
                    return StepResult.pending();
                }
                DroneContext context = DroneContext.resolve(mc);
                TuneModel model = TuneModel.open(context);
                if (!model.live()) {
                    return StepResult.fail("TuneModel nicht im Live-Modus");
                }
                Map<String, Float> original = access.current().values();
                state.put(ORIGINAL, original);
                double current = model.value(PID_KEY);
                double target = current + 7.0 <= 250.0 ? current + 7.0 : current - 7.0;
                model.set(PID_KEY, target);
                if (!model.manualPid()) {
                    return StepResult.fail("PID-Änderung hat pid.source nicht auf manuell gesetzt");
                }
                if (!model.save()) {
                    return StepResult.fail("TuneAccess.apply lieferte false");
                }
                state.put(EXPECTED, (float) target);
                state.put(PHASE, 2);
                state.put(WAIT, ctx.tick());
                return StepResult.pending();
            }
            case 2 -> {
                FlightTune now = access.current();
                float expected = (float) state.get(EXPECTED);
                boolean roundTrip = Math.abs(now.get(PID_KEY, Float.NaN) - expected) < 1e-3
                        && Math.rint(now.get(TuneKeys.PID_SOURCE, TuneEdits.PID_DERIVED)) == TuneEdits.PID_MANUAL;
                if (!roundTrip) {
                    if (ctx.tick() - (int) state.get(WAIT) > 120) {
                        return StepResult.fail("Tuning kam nicht zurück: " + now.values());
                    }
                    return StepResult.pending();
                }
                Map<String, Float> original = (Map<String, Float>) state.get(ORIGINAL);
                if (!TuneModel.applyNow(original)) {
                    return StepResult.fail("Zurücksetzen über TuneAccess.apply fehlgeschlagen");
                }
                state.put(PHASE, 3);
                state.put(WAIT, ctx.tick());
                return StepResult.pending();
            }
            case 3 -> {
                Map<String, Float> original = (Map<String, Float>) state.get(ORIGINAL);
                if (!sameOn(access.current(), original, PID_KEY, TuneKeys.PID_SOURCE, "pid.yaw.d")) {
                    if (ctx.tick() - (int) state.get(WAIT) > 120) {
                        return StepResult.fail("Ursprüngliches Tuning kam nicht zurück: " + access.current().values());
                    }
                    return StepResult.pending();
                }
                UUID id = mc.player.getUUID();
                state.put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    if (player != null) {
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    }
                }));
                state.put(PHASE, 4);
                return StepResult.pending();
            }
            default -> {
                CompletableFuture<Void> future = (CompletableFuture<Void>) state.get(FUTURE);
                return future.isDone() ? StepResult.pass() : StepResult.pending();
            }
        }
    }

    private static boolean sameOn(FlightTune now, Map<String, Float> original, String... keys) {
        for (String key : keys) {
            Float before = original.get(key);
            if (before == null ? now.has(key) : !now.has(key) || Math.abs(now.get(key, Float.NaN) - before) > 1e-3) {
                return false;
            }
        }
        return true;
    }
}
