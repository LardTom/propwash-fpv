package de.lardtom.propwash.input.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.client.GuideSelfTests;
import de.lardtom.propwash.guide.client.screen.SceneOverlay;
import de.lardtom.propwash.guide.client.screen.SceneScreen;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scenes.ConfiguratorScene;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.RemoteDemo;
import de.lardtom.propwash.input.StickChannel;
import de.lardtom.propwash.input.StickMode;
import de.lardtom.propwash.input.client.screen.ReceiverTab;
import de.lardtom.propwash.input.client.screen.ReceiverWizardScreen;
import de.lardtom.propwash.ui.SettingsSearch;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.search.SearchBar;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StickModeSelfTests {
    private static final String GUID = "05000000091200004f5400005e1f0007";
    private static final String NAME = "EdgeTX Propwash Mode-Funke Joystick";
    private static final int AXES = 8;
    private static final int BUTTONS = 8;
    private static final int THROTTLE_AXIS = 2;
    private static final int TIMEOUT = 120;
    private static final Map<StickMode, Set<StickChannel>> LEFT = new EnumMap<>(StickMode.class);
    private static final Pattern STICK_SIDE = Pattern.compile("(left|right) stick|(linke|rechte)[nr]? knüppel");
    private static final StickMode[] ORDER = {StickMode.MODE_1, StickMode.MODE_3, StickMode.MODE_4, StickMode.MODE_2};

    static {
        LEFT.put(StickMode.MODE_1, EnumSet.of(StickChannel.PITCH, StickChannel.YAW));
        LEFT.put(StickMode.MODE_2, EnumSet.of(StickChannel.THROTTLE, StickChannel.YAW));
        LEFT.put(StickMode.MODE_3, EnumSet.of(StickChannel.PITCH, StickChannel.ROLL));
        LEFT.put(StickMode.MODE_4, EnumSet.of(StickChannel.THROTTLE, StickChannel.ROLL));
    }

    private StickModeSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("stickmode: Assistent beginnt mit der Mode-Wahl, Ansagen und Funke folgen Mode 1 bis 4 (simuliert)", 2400, ctx -> {
            ModeTour tour = (ModeTour) ctx.state().computeIfAbsent("tour", k -> new ModeTour());
            StepResult result;
            try {
                result = tour.advance(ctx);
            } catch (RuntimeException e) {
                tour.walker.close();
                throw e;
            }
            if (result.status() != StepResult.Status.PENDING) {
                tour.walker.close();
            }
            return result;
        });
        SelfTest.clientStep("stickmode: Einstellung im Empfänger-Tab, auffindbar über die Suche, sofort wirksam", 600, StickModeSelfTests::receiverTabStep);
        SelfTest.clientStep("stickmode: Flugschule zeigt Knüppel-Hinweise auf dem Knüppel des Modes", 900, StickModeSelfTests::guideStep);
    }

    public static void registerScreenshots(boolean german) {
        String suffix = german ? " deutsch" : "";
        String file = german ? "_de" : "";
        Screenshots.step("bildschirmfotos: Stick-Mode, Assistent beginnt mit der Mode-Wahl" + suffix, "receiver_wizard_stick_mode" + file, 4,
                new WizardScene(StickMode.MODE_2, null, 0.9F));
        Screenshots.step("bildschirmfotos: Stick-Mode, Assistent in Mode 1 beim Gas" + suffix, "receiver_wizard_stick_mode1_throttle" + file, 4,
                new WizardScene(StickMode.MODE_1, StickChannel.THROTTLE, 1.3F));
        if (german) {
            return;
        }
        Screenshots.step("bildschirmfotos: Stick-Mode, Assistent Mode-Wahl mit Mode 3", "receiver_wizard_stick_mode3", 4,
                new WizardScene(StickMode.MODE_3, null, 0.9F));
        Screenshots.step("bildschirmfotos: Stick-Mode, Assistent in Mode 3 beim Roll", "receiver_wizard_stick_mode3_roll", 4,
                new WizardScene(StickMode.MODE_3, StickChannel.ROLL, 0.9F));
        Screenshots.step("bildschirmfotos: Stick-Mode, Assistent in Mode 4 beim Gier", "receiver_wizard_stick_mode4_yaw", 4,
                new WizardScene(StickMode.MODE_4, StickChannel.YAW, 0.9F));
        Screenshots.step("bildschirmfotos: Stick-Mode, Empfänger-Tab in Mode 1", "configurator_receiver_stick_mode1", 20, new TabScene(StickMode.MODE_1));
        Screenshots.step("bildschirmfotos: Stick-Mode, Flugschule Gas in Mode 1", "guide_configurator_sticks_mode1", 30,
                withMode(StickMode.MODE_1, guideShot(180.0F)));
        Screenshots.step("bildschirmfotos: Stick-Mode, Flugschule Roll in Mode 3", "guide_configurator_sticks_mode3", 30,
                withMode(StickMode.MODE_3, guideShot(80.0F)));
    }

    static boolean expectLeft(StickMode mode, StickChannel channel) {
        return LEFT.get(mode).contains(channel);
    }

    static @Nullable String key(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
    }

    private static final class ModeTour {
        private final Walker walker = new Walker();
        private int modeIndex;
        private int channelIndex = -1;
        private String phase = "open";
        private int since;
        private long framesAt;

        private void next(String phase, int tick) {
            this.phase = phase;
            this.since = tick;
        }

        StepResult advance(SelfTest.StepContext ctx) {
            int tick = ctx.tick();
            if ("open".equals(this.phase)) {
                this.walker.open(StickMode.MODE_2);
                this.next("first", tick);
                return StepResult.pending();
            }
            ReceiverWizardScreen w = this.walker.current();
            if (w == null) {
                return StepResult.fail("Assistent nicht mehr offen in Phase " + this.phase);
            }
            if (ThemeStats.frames(ReceiverWizardScreen.class) == 0L || w.modeCard(StickMode.MODE_1) == null && w.page() == ReceiverWizardScreen.Page.MODE) {
                return tick - this.since > TIMEOUT ? StepResult.fail("Mode-Wahl wird nicht gezeichnet") : StepResult.pending();
            }
            if (tick - this.since > TIMEOUT) {
                return StepResult.fail("Assistent hängt in Phase " + this.phase + " (" + w.page() + ", " + w.currentChannel() + ")");
            }
            StickMode mode = ORDER[this.modeIndex];
            switch (this.phase) {
                case "first" -> {
                    if (w.page() != ReceiverWizardScreen.Page.MODE) {
                        return StepResult.fail("Assistent beginnt nicht mit der Mode-Wahl: " + w.page());
                    }
                    if (w.chosenMode() != StickMode.MODE_2) {
                        return StepResult.fail("vorgewählt ist " + w.chosenMode() + " statt Mode 2");
                    }
                    for (StickMode m : StickMode.values()) {
                        if (w.modeCard(m) == null) {
                            return StepResult.fail("Karte für Mode " + m.number() + " fehlt");
                        }
                    }
                    if (button(w, "propwash.input.wizard.next") == null) {
                        return StepResult.fail("„Weiter“ fehlt auf der Mode-Seite");
                    }
                    this.next("choose", tick);
                }
                case "choose" -> {
                    if (w.page() != ReceiverWizardScreen.Page.MODE) {
                        w.showPage(ReceiverWizardScreen.Page.MODE);
                        return StepResult.pending();
                    }
                    String how = this.choose(w, mode);
                    if (w.chosenMode() != mode || w.working().stickMode() != mode || w.demo().mode() != mode) {
                        return StepResult.fail(how + " wählt nicht Mode " + mode.number() + " (gewählt " + w.chosenMode() + ", Funke " + w.demo().mode() + ")");
                    }
                    ctx.log("stickmode: Mode " + mode.number() + " gewählt per " + how);
                    this.next("to-device", tick);
                }
                case "to-device" -> {
                    if (w.page() == ReceiverWizardScreen.Page.MODE) {
                        press(w, "propwash.input.wizard.next");
                        return StepResult.pending();
                    }
                    if (w.page() != ReceiverWizardScreen.Page.DEVICE) {
                        return StepResult.fail("nach der Mode-Wahl folgt " + w.page() + " statt der Geräteseite");
                    }
                    this.next("to-channels", tick);
                }
                case "to-channels" -> {
                    if (w.page() == ReceiverWizardScreen.Page.DEVICE) {
                        if (tick - this.since >= 3) {
                            press(w, "propwash.input.wizard.next");
                        }
                        return StepResult.pending();
                    }
                    if (w.page() != ReceiverWizardScreen.Page.CHANNELS) {
                        return StepResult.fail("nach der Geräteseite folgt " + w.page());
                    }
                    this.channelIndex = 0;
                    this.framesAt = w.demo().frames();
                    this.next("channel", tick);
                }
                case "channel" -> {
                    StickChannel expected = StickChannel.values()[this.channelIndex];
                    if (w.currentChannel() != expected) {
                        return StepResult.fail("Kanal " + w.currentChannel() + " statt " + expected + " in Mode " + mode.number());
                    }
                    if (w.demo().frames() - this.framesAt < 3) {
                        return StepResult.pending();
                    }
                    String problem = checkChannel(w, mode, expected);
                    if (problem != null) {
                        return StepResult.fail(problem);
                    }
                    press(w, "propwash.input.wizard.skip_channel");
                    this.next("skipped", tick);
                }
                case "skipped" -> {
                    StickChannel was = StickChannel.values()[this.channelIndex];
                    if (w.currentChannel() == was) {
                        return StepResult.pending();
                    }
                    this.channelIndex++;
                    if (this.channelIndex < StickChannel.values().length) {
                        this.framesAt = w.demo().frames();
                        this.next("channel", tick);
                        return StepResult.pending();
                    }
                    if (w.currentChannel() != null) {
                        return StepResult.fail("nach dem letzten Kanal noch " + w.currentChannel());
                    }
                    ctx.log("stickmode: Mode " + mode.number() + " – vier Ansagen und Funke auf dem richtigen Knüppel");
                    this.modeIndex++;
                    if (this.modeIndex >= ORDER.length) {
                        return StepResult.pass();
                    }
                    w.showPage(ReceiverWizardScreen.Page.MODE);
                    this.next("choose", tick);
                }
                default -> {
                    return StepResult.fail("unbekannte Phase " + this.phase);
                }
            }
            return StepResult.pending();
        }

        private String choose(ReceiverWizardScreen w, StickMode mode) {
            switch (mode) {
                case MODE_1 -> {
                    int[] card = w.modeCard(StickMode.MODE_1);
                    if (card != null) {
                        MouseButtonInfo left = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
                        w.mouseClicked(new MouseButtonEvent((card[0] + card[2]) / 2.0, (card[1] + card[3]) / 2.0, left), false);
                    }
                    return "Klick auf die Karte";
                }
                case MODE_3 -> {
                    w.keyPressed(new KeyEvent(InputConstants.KEY_3, 0, 0));
                    return "Taste 3";
                }
                case MODE_4 -> {
                    w.keyPressed(new KeyEvent(InputConstants.KEY_RIGHT, 0, 0));
                    return "Pfeil rechts";
                }
                default -> {
                    w.keyPressed(new KeyEvent(InputConstants.KEY_2, 0, 0));
                    return "Taste 2";
                }
            }
        }
    }

    static @Nullable String checkChannel(ReceiverWizardScreen w, StickMode mode, StickChannel channel) {
        boolean left = expectLeft(mode, channel);
        String side = left ? "left" : "right";
        String prompt = key(w.channelPrompt(channel));
        String wanted = "propwash.input.wizard.move." + channel.name().toLowerCase(Locale.ROOT) + "." + side;
        if (!wanted.equals(prompt)) {
            return "Mode " + mode.number() + " " + channel + ": Ansage " + prompt + " statt " + wanted;
        }
        String text = Component.translatable(wanted).getString();
        Matcher named = STICK_SIDE.matcher(text.toLowerCase(Locale.ROOT));
        if (!named.find()) {
            return "Mode " + mode.number() + " " + channel + ": Ansage „" + text + "“ nennt keinen Knüppel";
        }
        boolean saysLeft = named.group().startsWith("left") || named.group().startsWith("link");
        if (saysLeft != left) {
            return "Mode " + mode.number() + " " + channel + ": Ansage „" + text + "“ nennt nicht den " + (left ? "linken" : "rechten") + " Knüppel";
        }
        RemoteDemo.Frame frame = w.demo().lastFrame();
        if (frame == null || frame.mode() != mode) {
            return "Mode " + mode.number() + " " + channel + ": Funke zeichnet " + (frame == null ? "nichts" : frame.mode());
        }
        RemoteDemo.Focus focus = left ? RemoteDemo.Focus.LEFT_STICK : RemoteDemo.Focus.RIGHT_STICK;
        if (frame.focus() != focus) {
            return "Mode " + mode.number() + " " + channel + ": Funke hebt " + frame.focus() + " hervor statt " + focus;
        }
        boolean vertical = channel == StickChannel.THROTTLE || channel == StickChannel.PITCH;
        float lo = Float.MAX_VALUE;
        float hi = -Float.MAX_VALUE;
        float otherLo = Float.MAX_VALUE;
        float otherHi = -Float.MAX_VALUE;
        for (int i = 0; i <= 120; i++) {
            RemoteDemo.Frame f = RemoteDemo.frame(w.demo().target(), i * 0.05F, w.demo().mode());
            float mine = vertical ? (left ? f.leftY() : f.rightY()) : (left ? f.leftX() : f.rightX());
            float theirs = vertical ? (left ? f.rightY() : f.leftY()) : (left ? f.rightX() : f.leftX());
            lo = Math.min(lo, mine);
            hi = Math.max(hi, mine);
            otherLo = Math.min(otherLo, theirs);
            otherHi = Math.max(otherHi, theirs);
        }
        float moved = hi - lo;
        float other = otherHi - otherLo;
        if (moved < 0.9F || other > moved * 0.5F) {
            return "Mode " + mode.number() + " " + channel + ": Animation bewegt " + side + " um " + moved + ", den anderen Knüppel um " + other;
        }
        return null;
    }

    static @Nullable Button button(ReceiverWizardScreen wizard, String key) {
        return WizardRemoteSelfTests.button(wizard, key);
    }

    static void press(ReceiverWizardScreen wizard, String key) {
        Button button = button(wizard, key);
        if (button != null && button.active) {
            button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
        }
    }

    static final class Walker {
        private @Nullable ReceiverWizardScreen wizard;
        private @Nullable StickMode saved;
        private boolean simulated;

        void open(StickMode mode) {
            this.saved = InputConfig.get().stickMode;
            InputConfig.get().stickMode = mode;
            SimulatedRadio fake = new SimulatedRadio(GUID, NAME, ProfileDefaults.radio(NAME, AXES), AXES, BUTTONS);
            for (int i = 4; i < AXES; i++) {
                fake.setAxis(i, -1.0F);
            }
            fake.setAxis(THROTTLE_AXIS, 1.0F);
            TransmitterInput.INSTANCE.simulate(fake);
            this.simulated = true;
            ReceiverWizardScreen opened = new ReceiverWizardScreen(null, InputConfig.get(), result -> {
            });
            this.wizard = opened;
            Minecraft.getInstance().gui.setScreen(opened);
        }

        @Nullable ReceiverWizardScreen current() {
            return Minecraft.getInstance().gui.screen() == this.wizard ? this.wizard : null;
        }

        void close() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() instanceof ReceiverWizardScreen) {
                minecraft.gui.setScreen(null);
            }
            if (this.simulated) {
                TransmitterInput.INSTANCE.simulate(null);
                TransmitterInput.INSTANCE.rescanSoon();
                this.simulated = false;
            }
            if (this.saved != null) {
                InputConfig.get().stickMode = this.saved;
                this.saved = null;
            }
            this.wizard = null;
        }
    }

    private static final class WizardScene implements Screenshots.Scene {
        private final StickMode mode;
        private final @Nullable StickChannel channel;
        private final float seconds;
        private final Walker walker = new Walker();
        private String phase = "open";
        private int since;

        private WizardScene(StickMode mode, @Nullable StickChannel channel, float seconds) {
            this.mode = mode;
            this.channel = channel;
            this.seconds = seconds;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            int tick = ctx.tick();
            if ("open".equals(this.phase)) {
                this.walker.open(StickMode.MODE_2);
                this.phase = "choose";
                this.since = tick;
                return StepResult.pending();
            }
            ReceiverWizardScreen w = this.walker.current();
            if (w == null) {
                return StepResult.fail("Assistent nicht offen");
            }
            if (tick - this.since > TIMEOUT) {
                return StepResult.fail("Assistent hängt bei " + w.page() + " " + w.currentChannel());
            }
            if (ThemeStats.frames(ReceiverWizardScreen.class) == 0L) {
                return StepResult.pending();
            }
            Screenshots.parkMouse(Minecraft.getInstance());
            if ("choose".equals(this.phase)) {
                if (w.chosenMode() != this.mode) {
                    w.chooseMode(this.mode);
                }
                this.phase = "walk";
                this.since = tick;
                return StepResult.pending();
            }
            if (this.channel == null) {
                return w.page() == ReceiverWizardScreen.Page.MODE && w.modeCard(this.mode) != null ? StepResult.pass() : StepResult.pending();
            }
            switch (w.page()) {
                case MODE, DEVICE -> {
                    if (tick % 4 == 0) {
                        press(w, "propwash.input.wizard.next");
                    }
                    return StepResult.pending();
                }
                case CHANNELS -> {
                    StickChannel at = w.currentChannel();
                    if (at == this.channel) {
                        w.demo().freezeAt(this.seconds);
                        w.clearStatus();
                        String problem = checkChannel(w, this.mode, at);
                        return problem == null ? StepResult.pass() : StepResult.fail(problem);
                    }
                    if (at != null && tick % 4 == 0) {
                        press(w, "propwash.input.wizard.skip_channel");
                    }
                    return at == null ? StepResult.fail("Kanal " + this.channel + " übersprungen") : StepResult.pending();
                }
                default -> {
                    return StepResult.fail("unerwartete Seite " + w.page());
                }
            }
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            ReceiverWizardScreen w = this.walker.current();
            if (w != null) {
                w.clearStatus();
                if (!Float.isNaN(this.seconds)) {
                    w.demo().freezeAt(this.seconds);
                }
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            this.walker.close();
            this.phase = "open";
        }
    }

    private static StepResult receiverTabStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (ctx.tick() == 0) {
            if (minecraft.gui.screen() != null) {
                minecraft.gui.setScreen(null);
            }
            state.put("saved", InputConfig.get().stickMode());
            ConfigTabs.open(null, ReceiverTab.ID);
            return StepResult.pending();
        }
        StickMode saved = (StickMode) state.get("saved");
        if (state.containsKey("committed")) {
            if (minecraft.gui.screen() instanceof ConfiguratorScreen) {
                return ctx.tick() > 400 ? restore(saved, StepResult.fail("Konfigurator schließt nach dem Speichern nicht")) : StepResult.pending();
            }
            StickMode now = InputConfig.get().stickMode();
            if (now != StickMode.MODE_3) {
                return restore(saved, StepResult.fail("nach dem Speichern gilt " + now + " statt Mode 3"));
            }
            if (StickModes.current() != StickMode.MODE_3) {
                return restore(saved, StepResult.fail("Anzeigen lesen den gespeicherten Mode nicht ohne Neustart: " + StickModes.current()));
            }
            ctx.log("stickmode: Mode 3 im Empfänger-Tab gespeichert, sofort wirksam");
            return restore(saved, StepResult.pass());
        }
        if (!(minecraft.gui.screen() instanceof ConfiguratorScreen screen)) {
            return ctx.tick() > 200 ? restore(saved, StepResult.fail("Konfigurator öffnet nicht")) : StepResult.pending();
        }
        ConfigTab active = screen.activeTab();
        if (!(active instanceof ReceiverTab tab)) {
            screen.selectTab(ReceiverTab.ID);
            return ctx.tick() > 200 ? close(screen, saved, StepResult.fail("Empfänger-Tab lässt sich nicht wählen")) : StepResult.pending();
        }
        CycleButton<StickMode> button = tab.stickModeButton();
        if (button == null) {
            return ctx.tick() > 200 ? close(screen, saved, StepResult.fail("Empfänger-Tab ohne Knüppel-Mode")) : StepResult.pending();
        }
        if (!state.containsKey("searched")) {
            if (button.getValue() != saved) {
                return close(screen, saved, StepResult.fail("Empfänger-Tab zeigt " + button.getValue() + " statt " + saved));
            }
            SearchBar search = screen.search();
            if (search == null) {
                return close(screen, saved, StepResult.fail("Konfigurator ohne Suchfeld"));
            }
            search.focus();
            if (search.index() == null) {
                return ctx.tick() > 300 ? close(screen, saved, StepResult.fail("Suchindex fehlt")) : StepResult.pending();
            }
            String label = Component.translatable("propwash.input.stick_mode").getString();
            for (String query : List.of("stick mode", "mode 3")) {
                search.type(query);
                List<SettingsSearch.Hit> hits = search.hits();
                boolean found = false;
                StringBuilder seen = new StringBuilder();
                for (int i = 0; i < Math.min(5, hits.size()); i++) {
                    SettingsSearch.Entry entry = hits.get(i).entry();
                    seen.append(" | ").append(entry.tabId()).append(": ").append(entry.label());
                    if (ReceiverTab.ID.equals(entry.tabId()) && entry.label().equals(label)) {
                        found = true;
                    }
                }
                if (!found) {
                    return close(screen, saved, StepResult.fail("Suche „" + query + "“ findet „" + label + "“ nicht unter den ersten fünf Treffern:" + seen));
                }
            }
            search.type("");
            state.put("searched", ctx.tick());
            return StepResult.pending();
        }
        if (!state.containsKey("pressed")) {
            while (button.getValue() != StickMode.MODE_2) {
                button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            }
            button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            if (tab.shownStickMode() != StickMode.MODE_3) {
                return close(screen, saved, StepResult.fail("Knopf schaltet von Mode 2 nicht auf Mode 3, sondern " + tab.shownStickMode()));
            }
            if (!screen.hasUnsavedChanges()) {
                return close(screen, saved, StepResult.fail("Mode-Wechsel gilt nicht als ungespeicherte Änderung"));
            }
            state.put("pressed", ctx.tick());
            return StepResult.pending();
        }
        if (ctx.tick() - (Integer) state.get("pressed") < 5) {
            return StepResult.pending();
        }
        state.put("committed", ctx.tick());
        screen.saveAndClose();
        return StepResult.pending();
    }

    private static StepResult close(ConfiguratorScreen screen, StickMode saved, StepResult result) {
        screen.discardAndClose();
        return restore(saved, result);
    }

    private static StepResult restore(StickMode saved, StepResult result) {
        if (InputConfig.get().stickMode() != saved) {
            InputConfig restored = InputConfig.get().copy();
            restored.changeStickMode(saved);
            InputConfig.commit(restored);
        }
        return result;
    }

    private static final class TabScene implements Screenshots.Scene {
        private final StickMode mode;
        private @Nullable StickMode saved;

        private TabScene(StickMode mode) {
            this.mode = mode;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft minecraft = Minecraft.getInstance();
            if (this.saved == null) {
                this.saved = InputConfig.get().stickMode;
                InputConfig.get().stickMode = this.mode;
                if (minecraft.gui.screen() != null) {
                    minecraft.gui.setScreen(null);
                }
                ConfigTabs.open(null, ReceiverTab.ID);
                return StepResult.pending();
            }
            if (!(minecraft.gui.screen() instanceof ConfiguratorScreen screen)) {
                return ctx.tick() > 200 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
            }
            if (!(screen.activeTab() instanceof ReceiverTab tab)) {
                screen.selectTab(ReceiverTab.ID);
                return StepResult.pending();
            }
            Screenshots.parkMouse(minecraft);
            return tab.stickModeButton() != null && tab.shownStickMode() == this.mode ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            if (this.saved != null) {
                InputConfig.get().stickMode = this.saved;
                this.saved = null;
            }
        }
    }

    private static StepResult guideStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        Scene scene = GuideScenes.byId(ConfiguratorScene.ID);
        if (scene == null) {
            return StepResult.fail("Szene „Konfigurator“ fehlt");
        }
        if (ctx.tick() == 0) {
            state.put("saved", InputConfig.get().stickMode);
            state.put("index", 0);
            InputConfig.get().stickMode = StickMode.MODE_2;
            minecraft.gui.setScreen(new SceneScreen(scene, null));
            return StepResult.pending();
        }
        StickMode saved = (StickMode) state.get("saved");
        if (!(minecraft.gui.screen() instanceof SceneScreen screen)) {
            return endGuide(saved, ctx.tick() > 200 ? StepResult.fail("Szenen-Player öffnet nicht") : StepResult.pending());
        }
        int index = (Integer) state.get("index");
        StickMode[] order = {StickMode.MODE_2, StickMode.MODE_1, StickMode.MODE_3, StickMode.MODE_4};
        StickMode mode = order[index];
        screen.playback().seek(scene.stepStart(0) + 180.0F);
        screen.playback().pause();
        if (!state.containsKey("frames" + index)) {
            InputConfig.get().stickMode = mode;
            state.put("frames" + index, screen.frames());
            return StepResult.pending();
        }
        if (screen.frames() - (Long) state.get("frames" + index) < 4) {
            return StepResult.pending();
        }
        SceneOverlay.Placed throttle = null;
        for (SceneOverlay.Placed placed : screen.lastPlaced()) {
            if (placed.textKey().endsWith("callout.stick_throttle")) {
                throttle = placed;
            }
        }
        if (throttle == null) {
            return ctx.tick() > 800 ? endGuide(saved, StepResult.fail("Gas-Hinweis am Knüppel fehlt in Mode " + mode.number())) : StepResult.pending();
        }
        String shown = StickModes.variant(throttle.textKey(), mode);
        String text = Component.translatable(shown).getString();
        boolean left = expectLeft(mode, StickChannel.THROTTLE);
        String stepKey = StickModes.variant(scene.stepTextKey(0), mode);
        if (mode == StickMode.MODE_2 ? !stepKey.equals(scene.stepTextKey(0)) : !stepKey.endsWith(".mode" + mode.number())) {
            return endGuide(saved, StepResult.fail("Schritttext in Mode " + mode.number() + " ist " + stepKey));
        }
        state.put("anchor" + index, throttle.anchorX());
        ctx.log("stickmode: Flugschule Mode " + mode.number() + " Gas-Hinweis „" + text + "“ bei x=" + throttle.anchorX() + ", Kasten " + throttle.x0() + "–"
                + throttle.x1());
        String lower = text.toLowerCase(Locale.ROOT);
        boolean mentionsRight = lower.contains("right") || lower.contains("recht");
        if (mentionsRight == left) {
            return endGuide(saved, StepResult.fail("Gas-Hinweis in Mode " + mode.number() + " nennt den falschen Knüppel: „" + text + "“"));
        }
        if (index + 1 < order.length) {
            state.put("index", index + 1);
            return StepResult.pending();
        }
        float mode2 = (Float) state.get("anchor0");
        float mode1 = (Float) state.get("anchor1");
        float mode3 = (Float) state.get("anchor2");
        float mode4 = (Float) state.get("anchor3");
        if (!(mode1 > mode2 + 8.0F && mode3 > mode4 + 8.0F && Math.abs(mode1 - mode3) < 2.0F && Math.abs(mode2 - mode4) < 2.0F)) {
            return endGuide(saved, StepResult.fail("Gas-Hinweis zeigt nicht auf den Gas-Knüppel: x Mode 1 " + mode1 + ", Mode 2 " + mode2 + ", Mode 3 " + mode3
                    + ", Mode 4 " + mode4));
        }
        return endGuide(saved, StepResult.pass());
    }

    private static StepResult endGuide(StickMode saved, StepResult result) {
        if (result.status() != StepResult.Status.PENDING) {
            InputConfig.get().stickMode = saved;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() instanceof SceneScreen) {
                minecraft.gui.setScreen(null);
            }
        }
        return result;
    }

    private static Screenshots.Scene guideShot(float offset) {
        return GuideSelfTests.sceneShot(ConfiguratorScene.ID, 0, offset);
    }

    private static Screenshots.Scene withMode(StickMode mode, Screenshots.Scene scene) {
        return new Screenshots.Scene() {
            private @Nullable StickMode saved;

            @Override
            public StepResult prepare(SelfTest.StepContext ctx) {
                if (this.saved == null) {
                    this.saved = InputConfig.get().stickMode;
                }
                InputConfig.get().stickMode = mode;
                return scene.prepare(ctx);
            }

            @Override
            public void hold(SelfTest.StepContext ctx) {
                scene.hold(ctx);
            }

            @Override
            public void finish(SelfTest.StepContext ctx) {
                scene.finish(ctx);
                if (this.saved != null) {
                    InputConfig.get().stickMode = this.saved;
                    this.saved = null;
                }
            }
        };
    }
}
