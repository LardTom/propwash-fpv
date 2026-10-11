package de.lardtom.propwash.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.client.WorkbenchMoreProbe;
import de.lardtom.propwash.content.client.WorkbenchMoreScreen;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.client.UiScaleSelfTests.Case;
import de.lardtom.propwash.dev.client.UiScaleSelfTests.Combo;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.client.screen.SceneIndexScreen;
import de.lardtom.propwash.guide.client.screen.SceneScreen;
import de.lardtom.propwash.guide.client.training.TrainingHud;
import de.lardtom.propwash.guide.client.training.TrainingScreen;
import de.lardtom.propwash.guide.client.training.TrainingSession;
import de.lardtom.propwash.guide.training.Curriculum;
import de.lardtom.propwash.guide.training.Lesson;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.client.SimulatedRadio;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchCommand;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.DeviceInfo;
import de.lardtom.propwash.input.client.DeviceOnboarding;
import de.lardtom.propwash.input.client.RawInput;
import de.lardtom.propwash.input.client.FunctionKeys;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.input.client.screen.CommandEditScreen;
import de.lardtom.propwash.input.client.screen.DeviceSetupScreen;
import de.lardtom.propwash.input.client.screen.MouseSetupScreen;
import de.lardtom.propwash.input.client.screen.ReceiverWizardScreen;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.join.JoinReport;
import de.lardtom.propwash.join.JoinText;
import de.lardtom.propwash.join.client.JoinMismatchScreen;
import de.lardtom.propwash.join.client.JoinSelfTests;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.client.ClientCourses;
import de.lardtom.propwash.race.client.CourseEditScreen;
import de.lardtom.propwash.race.client.GateEditCursorScreen;
import de.lardtom.propwash.race.client.GateEditor;
import de.lardtom.propwash.race.client.GateStackSelfTests;
import de.lardtom.propwash.race.client.LeaderboardScreen;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.race.RaceServer;
import de.lardtom.propwash.race.client.BoardFixture;
import de.lardtom.propwash.race.client.ClientLeaderboards;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.ScrollArea;
import de.lardtom.propwash.input.client.screen.ReceiverTab;
import de.lardtom.propwash.osd.client.OsdConfigTab;
import de.lardtom.propwash.server.WorldRulesS2C;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.client.WorldRulesTab;
import de.lardtom.propwash.ui.client.tab.OverviewTab;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.LayoutAudit.Finding;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.HoverSweepSelfTests;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import de.lardtom.propwash.video.client.record.ActionCamRecorder;
import de.lardtom.propwash.video.client.record.FfmpegStates;
import de.lardtom.propwash.video.client.record.RecordingsScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

final class UiScaleCases {
    private UiScaleCases() {
    }

    static List<Case> all() {
        List<Case> out = new ArrayList<>();
        out.add(new Configurator(false, Configurator.Input.KEYBOARD));
        out.add(new Configurator(true, Configurator.Input.KEYBOARD));
        out.add(new Configurator(false, Configurator.Input.RADIO));
        out.add(new Configurator(false, Configurator.Input.MOUSE));
        out.add(new Configurator(true, Configurator.Input.RADIO, Configurator.LONG_RADIO, List.of(ReceiverTab.ID, OverviewTab.ID),
                "Konfigurator mit Drohne und langer Funke", false));
        out.add(new Configurator(true, Configurator.Input.MOUSE, Configurator.RADIO_NAME, List.of(ReceiverTab.ID), "Konfigurator mit Drohne und Maus", false));
        out.add(new Configurator(false, Configurator.Input.KEYBOARD, Configurator.RADIO_NAME, List.of(Configurator.OSD_OPTIONS),
                "Konfigurator OSD-Optionen", false));
        out.add(new Configurator(false, Configurator.Input.KEYBOARD, Configurator.RADIO_NAME, List.of(WorldRulesTab.ID),
                "Konfigurator mit gesperrten Welt-Regeln", true));
        out.addAll(UiScaleLiveCases.all());
        out.add(new ActionCamStates());
        out.add(new HotPlug(false, Configurator.LONG_RADIO, "Konfigurator, lange Funke angesteckt"));
        out.add(new HotPlug(true, Configurator.LONG_RADIO, "Konfigurator mit Drohne, lange Funke angesteckt"));
        out.add(new HotPlug(true, Configurator.RADIO_NAME, "Konfigurator mit Drohne, Funke angesteckt"));
        out.add(screen("Flugschule Übersicht", () -> new SceneIndexScreen(null, ItemStack.EMPTY)));
        for (Scene scene : GuideScenes.all()) {
            out.add(new SceneSteps(scene));
        }
        out.add(screen("Flugtraining", () -> new TrainingScreen(null)));
        out.add(screen("Aufnahmen leer", () -> new RecordingsScreen(null)));
        out.add(new Recordings());
        out.add(screen("Beitritts-Prüfung", () -> {
            JoinReport report = JoinSelfTests.sampleReport(false);
            return new JoinMismatchScreen(new TitleScreen(), report, JoinText.disconnect(report), true);
        }));
        out.add(screen("Beitritts-Prüfung Protokoll", () -> {
            JoinReport report = JoinSelfTests.sampleReport(true);
            return new JoinMismatchScreen(new TitleScreen(), report, JoinText.disconnect(report), true);
        }));
        out.add(screen("Bestenliste", () -> new LeaderboardScreen(null, LeaderboardScreen.defaultCourse(Minecraft.getInstance()))));
        out.add(new DeviceSetup());
        out.add(screen("Befehl bearbeiten", () -> new CommandEditScreen(null, SwitchFunction.COMMAND_1, new SwitchCommand("/time set day", "/time set night"),
                Component.translatable("propwash.input.keyboard"), Component.translatable("propwash.input.command.edit.keyboard"),
                FunctionKeys.keyLabel(SwitchFunction.COMMAND_1), edited -> { })));
        out.add(new Wizard(false));
        out.add(new Wizard(true));
        out.add(new WizardPrompts());
        out.add(scene("Werkbank", ShowcaseTour::workbenchScene));
        out.add(scene("Lackierung", ShowcaseTour::paintScene));
        out.add(scene("Ladegerät", ShowcaseTour::chargerScene));
        out.add(scene("Wartungsmenü", ShowcaseTour::serviceScene));
        out.add(new WorkbenchMore());
        out.add(screen("Maus-Einrichtung", () -> {
            ReceiverWizardScreen wizard = ReceiverWizardScreen.standalone(null);
            return new MouseSetupScreen(wizard, wizard.working());
        }));
        out.add(new GateEdit(false));
        out.add(new GateEdit(true));
        out.add(new CourseEdit(4, "Kurs bearbeiten"));
        out.add(new CourseEdit(14, "Kurs bearbeiten viele Gates"));
        out.add(new FullBoard());
        out.add(new EmptyBoard());
        out.add(new Toasts());
        out.add(new TrainingPanel());
        return out;
    }

    private static final List<String> SHOWCASE = List.of("Konfigurator / action_cam", "Konfigurator mit Drohne / osd", "Konfigurator / overview",
            "Konfigurator mit Drohne / receiver", "Flugtraining", "Flugschule Übersicht", "Werkbank weitere Einträge", "Gate-Editor",
            "Beitritts-Prüfung Protokoll", "Empfänger-Assistent mit Funke / switches", "Hinweise / addons", "Konfigurator mit Drohne / modes",
            "Konfigurator mit Drohne / pid", "Lackierung", "Flugtraining-Anzeige / *", "Konfigurator / modes mode_3d", "Konfigurator / modes command_1",
            "Gate-Editor / Seite 2", "Gate-Editor Anzeige", "Gate-Editor Anzeige / Seite 2", "Flugschule Szene repair / *", "Aufnahmen mit Liste",
            "Konfigurator mit Funke / receiver", "Konfigurator mit Funke / modes arm", "Konfigurator mit Funke / modes mode_3d",
            "Konfigurator mit Funke / modes command_1", "Konfigurator mit Funke / modes listening", "Konfigurator mit Maus / receiver",
            "Empfänger-Assistent mit Funke / device", "Konfigurator / tricks", "Kurs bearbeiten", "Kurs bearbeiten viele Gates", "Bestenliste voll",
            "Konfigurator mit Drohne und langer Funke / receiver", "Konfigurator OSD-Optionen / osd options", "Konfigurator / osd",
            "Konfigurator mit gesperrten Welt-Regeln / world_rules", "Empfänger-Assistent Rückfragen / ohne arm-schalter",
            "Bestenliste leer / archiv", "Konfigurator mit Drohne, lange Funke angesteckt / receiver");

    private static final List<String> DEFAULT_WINDOW = List.of("Konfigurator mit Funke / receiver", "Konfigurator mit Funke / modes arm",
            "Konfigurator mit Funke / modes mode_3d", "Konfigurator mit Maus / receiver", "Empfänger-Assistent mit Funke / device",
            "Konfigurator / modes", "Bestenliste voll", "Wartungsmenü", "Konfigurator / osd", "Empfänger-Assistent Rückfragen / ohne arm-schalter",
            "Bestenliste leer / archiv", "Konfigurator, lange Funke angesteckt / receiver");

    private static final List<String> LIVE = List.of(UiScaleLiveCases.FPV + " / " + OverviewTab.ID, UiScaleLiveCases.FPV + " / " + ReceiverTab.ID,
            UiScaleLiveCases.FPV + " / " + UiScaleLiveCases.VIDEO, UiScaleLiveCases.FPV_RADIO + " / " + ReceiverTab.ID,
            UiScaleLiveCases.FPV_RADIO + " / " + OverviewTab.ID, UiScaleLiveCases.SIGHT_LONG_RADIO + " / " + ReceiverTab.ID,
            UiScaleLiveCases.FPV_ANALOG + " / " + UiScaleLiveCases.VIDEO);

    private static final List<String> SMALL_WINDOW = List.of("Lackierung", "Bestenliste voll", "Bestenliste leer / archiv");

    private static final List<String> WIDE_WINDOW = List.of("Konfigurator mit Funke / receiver", "Empfänger-Assistent mit Funke / device");

    private static final List<String> TOOLTIP_SHOWCASE = List.of("Konfigurator mit Funke / modes", "Konfigurator / configuration",
            "Konfigurator / action_cam", "Konfigurator / osd", "Konfigurator / world_rules", "Werkbank", "Lackierung", "Ladegerät");

    static boolean tooltipShowcase(Combo combo, String name) {
        return combo.scale() == 2 && (combo.width() == 640 || combo.width() == 854) && TOOLTIP_SHOWCASE.contains(name);
    }

    static boolean showcase(Combo combo, String name) {
        if (combo.width() == 854 && DEFAULT_WINDOW.contains(name) || combo.width() == 1920 && combo.scale() == 2 && WIDE_WINDOW.contains(name)
                || combo.width() == 800 && SMALL_WINDOW.contains(name)) {
            return true;
        }
        boolean worst = combo.width() == 640 || combo.width() == 1920 && combo.scale() == 4;
        boolean between = combo.width() == 768 || combo.width() == 912 || combo.width() == 854 && combo.scale() == 2;
        if ((worst || between) && LIVE.contains(name)) {
            return true;
        }
        return worst && SHOWCASE.stream().anyMatch(entry -> entry.endsWith("*") ? name.startsWith(entry.substring(0, entry.length() - 1)) : entry.equals(name));
    }

    static Screen carrier() {
        return new Screen(Component.literal("ui-scale")) {
            @Override
            public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            }

            @Override
            public boolean isPauseScreen() {
                return false;
            }
        };
    }

    static Case screen(String name, Supplier<Screen> factory) {
        return new Case() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public StepResult open(SelfTest.StepContext ctx) {
                Minecraft mc = Minecraft.getInstance();
                if (ctx.tick() == 0) {
                    mc.gui.setScreen(factory.get());
                    return StepResult.pending();
                }
                return mc.gui.screen() != null && ctx.tick() >= 3 ? StepResult.pass() : StepResult.pending();
            }

            @Override
            public void close(SelfTest.StepContext ctx) {
                Minecraft.getInstance().gui.setScreen(null);
            }
        };
    }

    static Case scene(String name, Supplier<Screenshots.Scene> factory) {
        return new Case() {
            private final Screenshots.Scene scene = factory.get();

            @Override
            public String name() {
                return name;
            }

            @Override
            public StepResult open(SelfTest.StepContext ctx) {
                if (ctx.tick() == 0) {
                    ctx.onServer(ShowcaseTour::ensureBase);
                }
                if (ctx.tick() < 3) {
                    return StepResult.pending();
                }
                StepResult prepared = this.scene.prepare(ctx);
                if (prepared.status() == StepResult.Status.PASS && Minecraft.getInstance().gui.screen() == null) {
                    return StepResult.pending();
                }
                return prepared;
            }

            @Override
            public void close(SelfTest.StepContext ctx) {
                this.scene.finish(ctx);
            }
        };
    }

    private static final class Recordings implements Case {
        private static final int CLIPS = 14;
        private final List<Path> files = new ArrayList<>();

        @Override
        public String name() {
            return "Aufnahmen mit Liste";
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.tick() == 0) {
                try {
                    Path directory = ActionCamRecorder.directory(mc);
                    Files.createDirectories(directory);
                    for (int i = 0; i < CLIPS; i++) {
                        Path file = directory.resolve(String.format(Locale.ROOT, "propwash_2026-10-09_12-%02d-00.mp4", i));
                        if (!Files.exists(file)) {
                            Files.write(file, new byte[4096]);
                            this.files.add(file);
                        }
                    }
                } catch (IOException e) {
                    return StepResult.fail("Testaufnahmen: " + e);
                }
                mc.gui.setScreen(new RecordingsScreen(null));
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof RecordingsScreen screen)) {
                return StepResult.pending();
            }
            return screen.clips().size() >= this.files.size() && ctx.tick() >= 5 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public List<Finding> audit(Minecraft mc, Screen screen) {
            List<Finding> findings = new ArrayList<>(LayoutProbe.audit(mc, screen));
            if (screen instanceof RecordingsScreen list && list.scrollMax() > 0) {
                ScreenRectangle bar = list.scrollbarArea();
                MouseButtonInfo left = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
                double x = bar.left() + bar.width() / 2.0;
                list.mouseClicked(new MouseButtonEvent(x, bar.top() + 1, left), false);
                list.mouseDragged(new MouseButtonEvent(x, bar.bottom() - 1, left), 0.0, bar.height());
                double end = list.scrollOffset();
                list.mouseDragged(new MouseButtonEvent(x, bar.top() + 1, left), 0.0, -bar.height());
                double start = list.scrollOffset();
                list.mouseReleased(new MouseButtonEvent(x, bar.top() + 1, left));
                if (end < list.scrollMax() || start > 0.0) {
                    findings.add(new Finding(LayoutAudit.Kind.HIDDEN, "Scrollleiste der Aufnahmen",
                            "lässt sich nicht von " + start + " bis " + end + " von " + list.scrollMax() + " ziehen"));
                }
            }
            return findings;
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
            for (Path file : this.files) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    ctx.log("Testaufnahme " + file + ": " + e);
                }
            }
            this.files.clear();
            ActionCamRecorder.INSTANCE.library().refresh();
        }
    }

    private static final class DeviceSetup implements Case {
        private static final DeviceInfo DEVICE = new DeviceInfo(-91, "OpenTX Radiomaster Boxer Joystick", "0300f0f0091200004f540000000000a1", false,
                0x1209, 0x4F54);

        @Override
        public String name() {
            return "Gerät einrichten";
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.tick() == 0) {
                InputConfig config = InputConfig.get();
                config.profiles.remove(DEVICE.guid());
                config.neverAsk.remove(DEVICE.guid());
                InputConfig.commit(config);
                mc.gui.setScreen(null);
                DeviceOnboarding.INSTANCE.simulate(List.of(DEVICE));
                return StepResult.pending();
            }
            return mc.gui.screen() instanceof DeviceSetupScreen && ctx.tick() >= 5 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            DeviceOnboarding.INSTANCE.simulate(null);
            Minecraft.getInstance().gui.setScreen(null);
            Minecraft.getInstance().gui.toastManager().clear();
        }
    }

    private static final class Wizard implements Case {
        private final boolean radio;

        private Wizard(boolean radio) {
            this.radio = radio;
        }

        @Override
        public String name() {
            return this.radio ? "Empfänger-Assistent mit Funke" : "Empfänger-Assistent";
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.tick() == 0) {
                TransmitterInput.INSTANCE.simulate(this.radio ? HoverSweepSelfTests.radio() : null);
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ReceiverWizardScreen)) {
                if (!ctx.state().containsKey("opened")) {
                    ctx.state().put("opened", ctx.tick());
                    mc.gui.setScreen(ReceiverWizardScreen.standalone(null));
                }
                return StepResult.pending();
            }
            return ctx.tick() >= 5 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public int pages(Screen screen) {
            return this.radio ? ReceiverWizardScreen.Page.values().length : 1;
        }

        @Override
        public String page(Screen screen, int index) {
            if (!(screen instanceof ReceiverWizardScreen wizard) || !this.radio) {
                return "";
            }
            ReceiverWizardScreen.Page page = ReceiverWizardScreen.Page.values()[index];
            wizard.showPage(page);
            return page.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
            TransmitterInput.INSTANCE.simulate(null);
        }
    }

    private static final class ActionCamStates implements Case {
        private static final int SETTLE = 6;
        private final Configurator base = new Configurator(true, Configurator.Input.KEYBOARD, Configurator.RADIO_NAME, List.of(FfmpegStates.TAB),
                "Konfigurator Action-Cam ffmpeg", false);
        private boolean shown;

        @Override
        public String name() {
            return this.base.name();
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            return this.base.open(ctx);
        }

        @Override
        public int pages(Screen screen) {
            return FfmpegStates.count();
        }

        @Override
        public String page(Screen screen, int index) {
            this.base.page(screen, 0);
            this.shown = screen instanceof ConfiguratorScreen configurator && FfmpegStates.show(configurator.activeTab(), index);
            return FfmpegStates.TAB + " " + FfmpegStates.name(index);
        }

        @Override
        public int settleTicks() {
            return SETTLE;
        }

        @Override
        public List<Finding> audit(Minecraft mc, Screen screen) {
            List<Finding> findings = new ArrayList<>(LayoutProbe.audit(mc, screen));
            if (!this.shown) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, this.name(), "Tab „Action-Cam“ fehlt oder nimmt den Zustand nicht an"));
            }
            return findings;
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen configurator) {
                FfmpegStates.clear(configurator.activeTab());
            }
            this.base.close(ctx);
        }
    }

    private static final class HotPlug implements Case {
        private static final List<String> TABS = List.of(ReceiverTab.ID, OverviewTab.ID, OsdConfigTab.ID);
        private static final int SETTLE = 20;
        private final Configurator base;
        private final String radioName;
        private final String label;
        private boolean plugged;

        private HotPlug(boolean drone, String radioName, String label) {
            this.base = new Configurator(drone, Configurator.Input.KEYBOARD, radioName, TABS, label, false);
            this.radioName = radioName;
            this.label = label;
        }

        @Override
        public String name() {
            return this.label;
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            if (ctx.tick() == 0) {
                this.plugged = false;
            }
            return this.base.open(ctx);
        }

        @Override
        public int pages(Screen screen) {
            return TABS.size();
        }

        @Override
        public String page(Screen screen, int index) {
            String key = this.base.page(screen, index);
            if (!this.plugged) {
                this.plugged = true;
                TransmitterInput.INSTANCE.simulate(new SimulatedRadio(Configurator.RADIO_GUID, this.radioName,
                        ProfileDefaults.radio(this.radioName, Configurator.RADIO_AXES), Configurator.RADIO_AXES, Configurator.RADIO_BUTTONS));
            }
            return key;
        }

        @Override
        public int settleTicks() {
            return SETTLE;
        }

        @Override
        public List<Finding> audit(Minecraft mc, Screen screen) {
            List<Finding> findings = new ArrayList<>(this.base.audit(mc, screen));
            if (!RadioState.INSTANCE.connected()) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, this.label, "Funke nach dem Anstecken nicht verbunden"));
            }
            return findings;
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            this.base.close(ctx);
            TransmitterInput.INSTANCE.simulate(null);
        }
    }

    private static final class WizardPrompts implements Case {
        private static final String[] PAGES = {"ohne arm-schalter", "nicht bewegt"};
        private final Wizard base = new Wizard(true);
        private @Nullable String problem;

        @Override
        public String name() {
            return "Empfänger-Assistent Rückfragen";
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            return this.base.open(ctx);
        }

        @Override
        public int pages(Screen screen) {
            return PAGES.length;
        }

        private static boolean press(ReceiverWizardScreen wizard, String key) {
            String label = Component.translatable(key).getString();
            for (GuiEventListener child : wizard.children()) {
                if (child instanceof Button button && button.active && label.equals(button.getMessage().getString())) {
                    button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                    return true;
                }
            }
            return false;
        }

        @Override
        public String page(Screen screen, int index) {
            this.problem = null;
            if (!(screen instanceof ReceiverWizardScreen wizard)) {
                this.problem = "Assistent nicht offen";
                return PAGES[index];
            }
            if (index == 0) {
                wizard.showPage(ReceiverWizardScreen.Page.SWITCHES);
                RawInput raw = TransmitterInput.INSTANCE.raw();
                DeviceProfile profile = raw == null ? null : wizard.working().profile(raw.device().guid());
                if (profile == null) {
                    this.problem = "kein Profil für die simulierte Funke";
                    return PAGES[index];
                }
                profile.setBinding(SwitchFunction.ARM, SwitchAssignment.none());
                wizard.showPage(ReceiverWizardScreen.Page.SWITCHES);
                if (!press(wizard, "propwash.input.wizard.finish") || !wizard.confirmingNoArm()) {
                    this.problem = "Nachfrage ohne Arm-Schalter erscheint nicht";
                }
                return PAGES[index];
            }
            wizard.showPage(ReceiverWizardScreen.Page.CALIBRATE);
            if (!press(wizard, "propwash.input.wizard.capture_center") || !press(wizard, "propwash.input.wizard.apply_calibration")
                    || !wizard.statusShown()) {
                this.problem = "Meldung nach der Kalibrierung ohne Bewegung erscheint nicht";
            }
            return PAGES[index];
        }

        @Override
        public List<Finding> audit(Minecraft mc, Screen screen) {
            List<Finding> findings = new ArrayList<>(LayoutProbe.audit(mc, screen));
            String failed = this.problem;
            if (failed != null) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, this.name(), failed));
            }
            return findings;
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            this.base.close(ctx);
        }
    }

    private static final class Configurator implements Case {
        private enum Input {
            KEYBOARD,
            RADIO,
            MOUSE
        }

        private static final String RADIO_GUID = "propwash-ui-scale-radio";
        private static final String RADIO_NAME = "OpenTX Radiomaster Boxer Joystick";
        private static final int RADIO_AXES = 8;
        private static final int RADIO_BUTTONS = 24;
        private static final String LISTENING = "listening";
        private static final List<SwitchFunction> MODE_ROWS = List.of(SwitchFunction.MODE_3D, SwitchFunction.COMMAND_1);
        private static final List<SwitchFunction> RADIO_ROWS = List.of(SwitchFunction.ARM, SwitchFunction.ANGLE, SwitchFunction.MODE_3D,
                SwitchFunction.BLACKBOX, SwitchFunction.COMMAND_1, SwitchFunction.BEEPER);
        private static final String LONG_RADIO = "Team-BlackSheep TBS Tango 2 Pro Joystick";
        private static final String OSD_OPTIONS = OsdConfigTab.ID + " options";
        private final boolean drone;
        private final Input input;
        private final String radioName;
        private final List<String> only;
        private final @Nullable String label;
        private final boolean lockedRules;
        private @Nullable String selectedBefore;
        private boolean sidebarChecked;
        private boolean rulesLocked;

        private Configurator(boolean drone, Input input) {
            this(drone, input, RADIO_NAME, List.of(), null, false);
        }

        private Configurator(boolean drone, Input input, String radioName, List<String> only, @Nullable String label, boolean lockedRules) {
            this.drone = drone;
            this.input = input;
            this.radioName = radioName;
            this.only = only;
            this.label = label;
            this.lockedRules = lockedRules;
        }

        private static void lockRules(boolean locked) {
            ClientWorldRules rules = ClientWorldRules.INSTANCE;
            List<WorldRulesS2C.Entry> entries = new ArrayList<>();
            rules.raw().forEach((id, value) -> entries.add(new WorldRulesS2C.Entry(id, value)));
            rules.accept(new WorldRulesS2C(!locked, entries));
        }

        @Override
        public String name() {
            if (this.label != null) {
                return this.label;
            }
            return switch (this.input) {
                case RADIO -> "Konfigurator mit Funke";
                case MOUSE -> "Konfigurator mit Maus";
                case KEYBOARD -> this.drone ? "Konfigurator mit Drohne" : "Konfigurator";
            };
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.tick() == 0) {
                this.sidebarChecked = false;
                ItemStack stack = this.drone
                        ? ContentTab.presetDrone(DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY).withAccessory(AccessoryType.LED_STRIP))
                        : ItemStack.EMPTY;
                ctx.onServer(server -> hand(server.getPlayerList().getPlayers().getFirst(), stack));
                if (this.lockedRules && ClientWorldRules.INSTANCE.editable()) {
                    lockRules(true);
                    this.rulesLocked = true;
                }
                if (this.input == Input.RADIO) {
                    TransmitterInput.INSTANCE.simulate(new SimulatedRadio(RADIO_GUID, this.radioName, ProfileDefaults.radio(this.radioName, RADIO_AXES),
                            RADIO_AXES, RADIO_BUTTONS));
                } else if (this.input == Input.MOUSE) {
                    this.selectedBefore = InputConfig.get().selectedGuid;
                    InputConfig.get().selectedGuid = InputConfig.MOUSE_GUID;
                }
                return StepResult.pending();
            }
            if (mc.player == null || DroneAssembly.isDrone(mc.player.getMainHandItem()) != this.drone) {
                return StepResult.pending();
            }
            if (this.input == Input.RADIO && TransmitterInput.INSTANCE.raw() == null) {
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen)) {
                if (!ctx.state().containsKey("opened")) {
                    ctx.state().put("opened", ctx.tick());
                    ConfigTabs.open(null, null);
                }
                return StepResult.pending();
            }
            return ctx.tick() - (int) ctx.state().getOrDefault("opened", 0) >= 3 ? StepResult.pass() : StepResult.pending();
        }

        private List<SwitchFunction> rows() {
            return this.input == Input.RADIO ? RADIO_ROWS : this.drone || this.input == Input.MOUSE ? List.of() : MODE_ROWS;
        }

        @Override
        public int pages(Screen screen) {
            if (!this.only.isEmpty()) {
                return this.only.size();
            }
            int extra = this.rows().size() + (this.input == Input.RADIO ? 1 : 0);
            return screen instanceof ConfiguratorScreen configurator ? configurator.tabs().size() + extra : 1;
        }

        private static void osdPage(ConfiguratorScreen configurator, OsdConfigTab.Page page) {
            configurator.selectTab(OsdConfigTab.ID);
            if (configurator.activeTab() instanceof OsdConfigTab osd && osd.page() != page) {
                osd.switchPage();
            }
        }

        @Override
        public List<Finding> audit(Minecraft mc, Screen screen) {
            List<Finding> findings = new ArrayList<>(LayoutProbe.audit(mc, screen));
            if (this.sidebarChecked || !(screen instanceof ConfiguratorScreen configurator) || !configurator.sidebarScroll().scrollable()) {
                return findings;
            }
            this.sidebarChecked = true;
            String subject = "Tab-Leiste des Konfigurators";
            int before = configurator.sidebarFirst();
            int max = configurator.sidebarScroll().max();
            ScreenRectangle bar = configurator.sidebarScroll().track();
            MouseButtonInfo left = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
            double x = bar.left() + 1.0;
            configurator.mouseClicked(new MouseButtonEvent(x, bar.top() + 1, left), false);
            configurator.mouseDragged(new MouseButtonEvent(x, bar.bottom() - 1, left), 0.0, bar.height());
            int end = configurator.sidebarFirst();
            configurator.mouseDragged(new MouseButtonEvent(x, bar.top() + 1, left), 0.0, -bar.height());
            int start = configurator.sidebarFirst();
            configurator.mouseReleased(new MouseButtonEvent(x, bar.top() + 1, left));
            if (end != max || start != 0) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, subject, "Scrollleiste lässt sich nicht von 0 bis " + max + " ziehen: " + start + " bis " + end));
            }
            GuiEventListener focused = configurator.getFocused();
            configurator.setFocused(configurator.sidebarButtons().getFirst());
            configurator.keyPressed(new KeyEvent(InputConstants.KEY_PAGEDOWN, 0, 0));
            int paged = configurator.sidebarFirst();
            configurator.keyPressed(new KeyEvent(InputConstants.KEY_END, 0, 0));
            int last = configurator.sidebarFirst();
            configurator.keyPressed(new KeyEvent(InputConstants.KEY_HOME, 0, 0));
            if (paged <= 0 || last != max || configurator.sidebarFirst() != 0) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, subject, "Bild ab, Ende und Pos1 bewegen die Tab-Leiste nicht: " + paged + ", " + last
                        + " von " + max + ", " + configurator.sidebarFirst()));
            }
            configurator.setFocused(focused);
            configurator.scrollSidebar(before);
            return findings;
        }

        @Override
        public int settleTicks() {
            return this.input == Input.KEYBOARD ? Case.super.settleTicks() : 4;
        }

        @Override
        public boolean between() {
            return this.input == Input.RADIO;
        }

        @Override
        public String page(Screen screen, int index) {
            if (!(screen instanceof ConfiguratorScreen configurator)) {
                return "";
            }
            int tabs = configurator.tabs().size();
            if (configurator.activeTab() instanceof ModesTab modes) {
                modes.cancelAction();
            }
            if (!this.only.isEmpty()) {
                String key = this.only.get(index);
                if (key.equals(OSD_OPTIONS)) {
                    osdPage(configurator, OsdConfigTab.Page.OPTIONS);
                } else {
                    configurator.selectTab(key);
                }
                configurator.setFocused(null);
                configurator.keyPressed(new KeyEvent(InputConstants.KEY_HOME, 0, 0));
                return key;
            }
            if (index >= tabs) {
                List<SwitchFunction> rows = this.rows();
                boolean listen = index - tabs >= rows.size();
                SwitchFunction row = listen ? rows.getLast() : rows.get(index - tabs);
                configurator.selectTab(ModesTab.ID);
                configurator.setFocused(null);
                if (configurator.activeTab() instanceof ModesTab modes) {
                    modes.select(row);
                }
                if (listen) {
                    String assign = Component.translatable("propwash.input.modes.assign").getString();
                    for (GuiEventListener child : configurator.children()) {
                        if (child instanceof Button button && button.active && assign.equals(button.getMessage().getString())) {
                            button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                            break;
                        }
                    }
                }
                configurator.keyPressed(new KeyEvent(InputConstants.KEY_HOME, 0, 0));
                return ModesTab.ID + " " + (listen ? LISTENING : row.name().toLowerCase(Locale.ROOT));
            }
            configurator.selectTab(index);
            configurator.setFocused(null);
            ConfigTab tab = configurator.activeTab();
            return tab == null ? "?" : tab.id();
        }

        @Override
        public void frame(Screen screen, String page) {
            if (page.startsWith(ModesTab.ID + " ")) {
                screen.keyPressed(new KeyEvent(InputConstants.KEY_END, 0, 0));
            }
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen configurator) {
                if (configurator.activeTab() instanceof ModesTab modes) {
                    modes.cancelAction();
                }
                if (this.only.contains(OSD_OPTIONS)) {
                    osdPage(configurator, OsdConfigTab.Page.STYLE);
                }
                configurator.discardAndClose();
            }
            if (this.rulesLocked) {
                lockRules(false);
                this.rulesLocked = false;
            }
            if (this.drone) {
                ctx.onServer(server -> hand(server.getPlayerList().getPlayers().getFirst(), ItemStack.EMPTY));
            }
            if (this.input == Input.RADIO) {
                TransmitterInput.INSTANCE.simulate(null);
            }
            String before = this.selectedBefore;
            if (before != null) {
                InputConfig.get().selectedGuid = before;
                this.selectedBefore = null;
            }
        }

        private static void hand(ServerPlayer player, ItemStack stack) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        }
    }

    private static final class WorkbenchMore implements Case {
        private final Case workbench = scene("Werkbank", ShowcaseTour::workbenchScene);

        @Override
        public String name() {
            return "Werkbank weitere Einträge";
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof WorkbenchMoreScreen) {
                return ctx.tick() - (int) ctx.state().getOrDefault("opened", 0) >= 3 ? StepResult.pass() : StepResult.pending();
            }
            StepResult prepared = this.workbench.open(ctx);
            if (prepared.status() != StepResult.Status.PASS) {
                return prepared;
            }
            if (!WorkbenchMoreProbe.open(14)) {
                return StepResult.fail("Werkbank ist nicht offen");
            }
            ctx.state().put("opened", ctx.tick());
            return StepResult.pending();
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
            this.workbench.close(ctx);
        }
    }

    private static final class SceneSteps implements Case {
        private final Scene scene;
        private final Case screen;

        private SceneSteps(Scene scene) {
            this.scene = scene;
            this.screen = screen("Flugschule Szene " + scene.id(), () -> new SceneScreen(scene, null));
        }

        @Override
        public String name() {
            return this.screen.name();
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            return this.screen.open(ctx);
        }

        @Override
        public int pages(Screen screen) {
            return 2;
        }

        @Override
        public String page(Screen screen, int index) {
            if (index == 0 || !(screen instanceof SceneScreen player)) {
                return "";
            }
            Minecraft mc = Minecraft.getInstance();
            int width = player.captionWidth();
            int longest = 0;
            int most = 0;
            for (int step = 0; step < this.scene.stepCount(); step++) {
                int lines = mc.font.split(Component.translatable(this.scene.stepTextKey(step)), width).size();
                if (lines > most) {
                    most = lines;
                    longest = step;
                }
            }
            player.playback().pause();
            player.playback().jumpTo(longest);
            return "Schritt " + (longest + 1);
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            this.screen.close(ctx);
        }
    }

    private static final class GateEdit implements Case {
        private final boolean hud;
        private volatile int gateId = -1;
        private volatile @Nullable String problem;

        private GateEdit(boolean hud) {
            this.hud = hud;
        }

        @Override
        public String name() {
            return this.hud ? "Gate-Editor Anzeige" : "Gate-Editor";
        }

        @Override
        public int pages(Screen screen) {
            GateEditor editor = GateEditor.current();
            return editor == null ? 1 : editor.model().pages();
        }

        @Override
        public String page(Screen screen, int index) {
            GateEditor editor = GateEditor.current();
            if (editor == null || index == 0) {
                return "";
            }
            editor.showPage(index);
            return "Seite " + (index + 1);
        }

        @Override
        public List<Finding> audit(Minecraft mc, Screen screen) {
            if (!this.hud) {
                return LayoutProbe.audit(mc, screen);
            }
            return LayoutProbe.auditDrawing(mc, LayoutAudit.Box.of(0, 0, screen.width, screen.height), g -> GateEditor.extractHud(g, DeltaTracker.ZERO));
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.tick() == 0) {
                ctx.onServer(server -> {
                    try {
                        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                        BlockPos origin = player.blockPosition();
                        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(2, -1, -4), origin.offset(8, 6, 4))) {
                            player.level().setBlockAndUpdate(pos, pos.getY() < origin.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        }
                        GateEntity gate = GateStackSelfTests.place(player, GateSize.MEDIUM, origin.getX() + 5, origin.getY(), origin.getZ(), 90.0F);
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RaceModule.COURSE_TOOL.get()));
                        this.gateId = gate.getId();
                    } catch (RuntimeException e) {
                        this.problem = String.valueOf(e);
                    }
                });
                return StepResult.pending();
            }
            if (this.problem != null) {
                return StepResult.fail(this.problem);
            }
            if (this.hud ? ctx.state().containsKey("opened") && GateEditor.active() && mc.gui.screen() != null : mc.gui.screen() instanceof GateEditCursorScreen) {
                return ctx.tick() - (int) ctx.state().getOrDefault("opened", 0) >= 5 ? StepResult.pass() : StepResult.pending();
            }
            Entity entity = mc.level == null || this.gateId < 0 ? null : mc.level.getEntity(this.gateId);
            if (!(entity instanceof GateEntity gate) || mc.player == null || !GateMounting.holdsTool(mc.player)) {
                return StepResult.pending();
            }
            GateEditor.start(gate);
            if (this.hud) {
                mc.gui.setScreen(carrier());
                ctx.state().put("opened", ctx.tick());
            } else if (GateEditCursorScreen.open()) {
                ctx.state().put("opened", ctx.tick());
            }
            return StepResult.pending();
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            GateEditor.stop();
            Minecraft.getInstance().gui.setScreen(null);
            int id = this.gateId;
            this.gateId = -1;
            this.problem = null;
            if (id >= 0) {
                ctx.onServer(server -> {
                    server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    Entity gate = server.overworld().getEntity(id);
                    if (gate != null) {
                        gate.discard();
                    }
                });
            }
        }
    }

    private static final class CourseSite {
        private static final int ROW_LENGTH = 7;
        private final int gates;
        private final String title;
        private final List<Integer> gateIds = new ArrayList<>();
        private volatile int courseId = GateEntity.NO_COURSE;
        private volatile @Nullable String problem;

        private CourseSite(int gates, String title) {
            this.gates = gates;
            this.title = title;
        }

        void build(SelfTest.StepContext ctx, @Nullable BiConsumer<ServerPlayer, CourseRegistry.Course> seed) {
            ctx.onServer(server -> {
                try {
                    ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                    BlockPos origin = player.blockPosition();
                    int length = Math.min(this.gates, ROW_LENGTH);
                    int depth = 4 + 6 * ((this.gates - 1) / ROW_LENGTH);
                    for (BlockPos pos : BlockPos.betweenClosed(origin.offset(2, -1, -4), origin.offset(4 + 6 * length, 6, depth))) {
                        player.level().setBlockAndUpdate(pos, pos.getY() < origin.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                    ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
                    List<Integer> placed = new ArrayList<>();
                    for (int i = 0; i < this.gates; i++) {
                        GateEntity gate = GateStackSelfTests.place(player, GateSize.MEDIUM, origin.getX() + 5 + 6 * (i % ROW_LENGTH), origin.getY(),
                                origin.getZ() + 6 * (i / ROW_LENGTH), 90.0F);
                        placed.add(gate.getId());
                        CourseToolItem.useOnGate(tool, player, gate);
                    }
                    synchronized (this.gateIds) {
                        this.gateIds.addAll(placed);
                    }
                    CourseRegistry registry = CourseRegistry.get();
                    int id = CourseToolItem.courseOf(tool);
                    CourseRegistry.Course course = registry == null ? null : registry.course(id);
                    if (course == null || course.gates.size() != this.gates) {
                        this.problem = "Kurs für das Fenster nicht angelegt";
                        return;
                    }
                    registry.rename(course, this.title);
                    if (seed != null) {
                        seed.accept(player, course);
                    }
                    this.courseId = id;
                } catch (RuntimeException e) {
                    this.problem = String.valueOf(e);
                }
            });
        }

        @Nullable String problem() {
            return this.problem;
        }

        int courseId() {
            return this.courseId;
        }

        boolean ready() {
            int id = this.courseId;
            CourseData course = id == GateEntity.NO_COURSE ? null : ClientCourses.get(id);
            return course != null && course.gates().size() == this.gates && !course.name().isEmpty();
        }

        void remove(SelfTest.StepContext ctx) {
            int id = this.courseId;
            List<Integer> gates;
            synchronized (this.gateIds) {
                gates = new ArrayList<>(this.gateIds);
                this.gateIds.clear();
            }
            this.courseId = GateEntity.NO_COURSE;
            this.problem = null;
            ctx.onServer(server -> {
                CourseRegistry registry = CourseRegistry.get();
                if (registry != null && id != GateEntity.NO_COURSE) {
                    registry.deleteCourse(id);
                }
                for (int gateId : gates) {
                    Entity gate = server.overworld().getEntity(gateId);
                    if (gate != null) {
                        gate.discard();
                    }
                }
            });
        }
    }

    private static final class CourseEdit implements Case {
        private final CourseSite site;
        private final String label;

        private CourseEdit(int gates, String label) {
            this.site = new CourseSite(gates, "Riverside Sprint");
            this.label = label;
        }

        @Override
        public String name() {
            return this.label;
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.tick() == 0) {
                this.site.build(ctx, null);
                return StepResult.pending();
            }
            String problem = this.site.problem();
            if (problem != null) {
                return StepResult.fail(problem);
            }
            if (mc.gui.screen() instanceof CourseEditScreen) {
                return ctx.tick() - (int) ctx.state().getOrDefault("opened", 0) >= 5 ? StepResult.pass() : StepResult.pending();
            }
            if (!this.site.ready()) {
                return StepResult.pending();
            }
            mc.gui.setScreen(new CourseEditScreen(null, this.site.courseId()));
            ctx.state().put("opened", ctx.tick());
            return StepResult.pending();
        }

        @Override
        public List<Finding> audit(Minecraft mc, Screen screen) {
            List<Finding> findings = new ArrayList<>(LayoutProbe.audit(mc, screen));
            if (!(mc.gui.screen() instanceof CourseEditScreen edit) || !edit.gateScroll().scrollable()) {
                return findings;
            }
            String subject = "Gate-Liste in „Kurs bearbeiten“";
            int max = edit.gateScroll().max();
            ScreenRectangle bar = edit.gateScroll().track();
            MouseButtonInfo left = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
            double x = bar.left() + 1.0;
            edit.mouseClicked(new MouseButtonEvent(x, bar.top() + 1, left), false);
            edit.mouseDragged(new MouseButtonEvent(x, bar.bottom() - 1, left), 0.0, bar.height());
            int end = edit.firstRow();
            edit.mouseDragged(new MouseButtonEvent(x, bar.top() + 1, left), 0.0, -bar.height());
            int start = edit.firstRow();
            edit.mouseReleased(new MouseButtonEvent(x, bar.top() + 1, left));
            if (end != max || start != 0) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, subject, "Scrollleiste lässt sich nicht von 0 bis " + max + " ziehen: " + start + " bis " + end));
            }
            edit.keyPressed(new KeyEvent(InputConstants.KEY_PAGEDOWN, 0, 0));
            int paged = edit.firstRow();
            edit.keyPressed(new KeyEvent(InputConstants.KEY_END, 0, 0));
            int last = edit.firstRow();
            findings.addAll(LayoutProbe.audit(mc, edit));
            edit.keyPressed(new KeyEvent(InputConstants.KEY_HOME, 0, 0));
            if (paged <= 0 || last != max || edit.firstRow() != 0) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, subject, "Bild ab, Ende und Pos1 bewegen die Liste nicht: " + paged + ", " + last + " von " + max
                        + ", " + edit.firstRow()));
            }
            return findings;
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
            this.site.remove(ctx);
        }
    }

    private static final class FullBoard implements Case {
        private static final String[] PILOTS = {"WWWWWWWWWWWWWWWW", "Lotte", "Mika_FPV", "xX_Propwash_Xx", "Jonas", "Ava", "Kenji", "Nora", "Felix", "Ida",
                "Maximilian_2011", "Tomke", "Pilot_Nummer_13"};
        private static final String[] ASSISTED = {"Mit_Flughilfe_01", "Greta", "WWWWWWWWWWWWWWWW"};
        private static final int OWN_RANK = 12;
        private final CourseSite site = new CourseSite(4, "Riverside Sprint");
        private volatile RacePayloads.@Nullable LeaderboardS2C payload;

        @Override
        public String name() {
            return "Bestenliste voll";
        }

        private void seed(ServerPlayer player, CourseRegistry.Course course) {
            CourseRegistry registry = CourseRegistry.get();
            if (registry == null) {
                return;
            }
            Leaderboard board = registry.board(course);
            FrameType[] frames = FrameType.values();
            long now = System.currentTimeMillis();
            long hour = 3_600_000L;
            long lap = 81_000_000_000L;
            int other = 0;
            for (int rank = 1; rank <= PILOTS.length + 1; rank++) {
                long nanos = lap + rank * 730_000_000L;
                if (rank == OWN_RANK) {
                    board.submit(player.getUUID(), player.getGameProfile().name(), frames[rank % frames.length].key(), nanos, now - hour);
                } else {
                    board.submit(new UUID(0x51CAL, other), PILOTS[other], frames[rank % frames.length].key(), nanos, now - rank * 9L * hour);
                    other++;
                }
            }
            for (int i = 0; i < ASSISTED.length; i++) {
                board.submitAssisted(new UUID(0x51CBL, i), ASSISTED[i], frames[i % frames.length].key(), lap - (3 - i) * 410_000_000L, now - (i + 2) * hour,
                        LapAssist.values()[i % LapAssist.values().length].bit());
            }
            registry.records().changed();
            this.payload = RaceServer.leaderboardPayload(board, course, player.getUUID(), false, true);
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.tick() == 0) {
                this.payload = null;
                this.site.build(ctx, this::seed);
                return StepResult.pending();
            }
            String problem = this.site.problem();
            if (problem != null) {
                return StepResult.fail(problem);
            }
            RacePayloads.LeaderboardS2C board = this.payload;
            if (board == null || !this.site.ready()) {
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof LeaderboardScreen screen)) {
                mc.gui.setScreen(new LeaderboardScreen(null, this.site.courseId()));
                ctx.state().put("opened", ctx.tick());
                return StepResult.pending();
            }
            int since = ctx.tick() - (int) ctx.state().getOrDefault("opened", 0);
            if (since < 8) {
                return StepResult.pending();
            }
            if (ClientLeaderboards.get(this.site.courseId()) != board) {
                BoardFixture.show(board);
                ctx.state().put("opened", ctx.tick() - 4);
                return StepResult.pending();
            }
            return screen.shownRows().size() == ClientLeaderboards.TOP && screen.shownAssistedRows().size() == ASSISTED.length ? StepResult.pass()
                    : StepResult.fail("Bestenliste zeigt " + screen.shownRows().size() + " und " + screen.shownAssistedRows().size() + " Zeilen");
        }

        @Override
        public List<Finding> audit(Minecraft mc, Screen screen) {
            List<Finding> findings = new ArrayList<>(LayoutProbe.audit(mc, screen));
            if (!(screen instanceof LeaderboardScreen board)) {
                return findings;
            }
            String subject = "Bestenliste mit " + (PILOTS.length + 1) + " Zeiten";
            if (ClientLeaderboards.get(this.site.courseId()) != this.payload) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, subject, "Testdaten wurden überschrieben"));
                return findings;
            }
            LayoutProbe.capture(mc, board);
            ScrollArea table = board.table();
            if (table.scrollable()) {
                ScreenRectangle view = table.view();
                MouseButtonInfo left = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
                double x = view.right() - 2.0;
                board.mouseClicked(new MouseButtonEvent(x, view.top() + 1, left), false);
                board.mouseDragged(new MouseButtonEvent(x, view.bottom() - 1, left), 0.0, view.height());
                int end = table.offset();
                LayoutProbe.capture(mc, board);
                int lastRow = board.lastAssistedRowY();
                board.mouseDragged(new MouseButtonEvent(x, view.top() + 1, left), 0.0, -view.height());
                int start = table.offset();
                board.mouseReleased(new MouseButtonEvent(x, view.top() + 1, left));
                if (end != table.maxOffset() || start != 0) {
                    findings.add(new Finding(LayoutAudit.Kind.HIDDEN, subject,
                            "Scrollleiste lässt sich nicht von 0 bis " + table.maxOffset() + " ziehen: " + start + " bis " + end));
                }
                if (lastRow < view.top() || lastRow + 16 > view.bottom() + 1) {
                    findings.add(new Finding(LayoutAudit.Kind.HIDDEN, subject, "letzte Zeile mit Flughilfe am Ende nicht sichtbar: " + lastRow + " in "
                            + view.top() + " bis " + view.bottom()));
                }
                board.keyPressed(new KeyEvent(InputConstants.KEY_END, 0, 0));
                int keyed = table.offset();
                board.keyPressed(new KeyEvent(InputConstants.KEY_HOME, 0, 0));
                if (keyed != table.maxOffset() || table.offset() != 0) {
                    findings.add(new Finding(LayoutAudit.Kind.HIDDEN, subject, "Ende und Pos1 bewegen die Liste nicht: " + keyed + " von " + table.maxOffset()));
                }
            } else if (board.assistedRowsDrawn() != ASSISTED.length) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, subject, "nur " + board.assistedRowsDrawn() + " Zeilen mit Flughilfe gezeichnet"));
            }
            Set<String> offered = new LinkedHashSet<>();
            String firstChip = board.classButtons().isEmpty() ? "" : board.classButtons().getFirst().getMessage().getString();
            for (int page = 0; page <= 2 * FrameType.values().length; page++) {
                List<FlatButton> chips = board.classButtons();
                if (page > 0 && !chips.isEmpty() && chips.getFirst().getMessage().getString().equals(firstChip)) {
                    break;
                }
                for (FlatButton chip : chips) {
                    offered.add(chip.getMessage().getString());
                }
                FlatButton more = board.moreButton();
                if (more == null) {
                    break;
                }
                more.press();
                findings.addAll(LayoutProbe.audit(mc, board));
            }
            if (offered.size() != FrameType.values().length) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, subject, "Klassenfilter bietet nur " + offered + " an"));
            }
            return findings;
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
            this.payload = null;
            this.site.remove(ctx);
        }
    }

    private static final class EmptyBoard implements Case {
        private static final int COURSE = 4471;
        private static final String[] PAGES = {"kein kurs", "leer", "archiv", "nur flughilfe"};
        private static final String[] ASSISTED = {"Mit_Flughilfe_01", "WWWWWWWWWWWWWWWW"};
        private RacePayloads.@Nullable LeaderboardS2C payload;
        private int shown;

        @Override
        public String name() {
            return "Bestenliste leer";
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.tick() == 0) {
                this.payload = null;
                mc.gui.setScreen(new LeaderboardScreen(null, COURSE));
                return StepResult.pending();
            }
            return mc.gui.screen() instanceof LeaderboardScreen && ctx.tick() >= 8 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public int pages(Screen screen) {
            return PAGES.length;
        }

        private static RacePayloads.LeaderboardS2C board(int index) {
            List<RacePayloads.BoardRow> rows = new ArrayList<>();
            if (index == 3) {
                FrameType[] frames = FrameType.values();
                long now = System.currentTimeMillis();
                for (int i = 0; i < ASSISTED.length; i++) {
                    rows.add(new RacePayloads.BoardRow(ASSISTED[i], frames[i % frames.length].key(), 80_000_000_000L + i * 1_100_000_000L,
                            now - (i + 1) * 3_600_000L, false, i + 1, i + 1, LapAssist.values()[i % LapAssist.values().length].bit()));
                }
            }
            return new RacePayloads.LeaderboardS2C(COURSE, 3, index == 0 ? 0 : 12, false, 37, rows, 3, 251_000_000_000L, index == 2 ? 2 : 0);
        }

        @Override
        public String page(Screen screen, int index) {
            RacePayloads.LeaderboardS2C board = board(index);
            this.payload = board;
            this.shown = index;
            BoardFixture.show(board);
            return PAGES[index];
        }

        @Override
        public List<Finding> audit(Minecraft mc, Screen screen) {
            List<Finding> findings = new ArrayList<>(LayoutProbe.audit(mc, screen));
            if (ClientLeaderboards.get(COURSE) != this.payload) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, this.name(), "Testdaten wurden überschrieben"));
                return findings;
            }
            String key = switch (this.shown) {
                case 0 -> "propwash.race.board.no_course";
                case 1 -> "propwash.race.board.empty";
                case 2 -> "propwash.race.board.empty_archived.command";
                default -> "propwash.race.board.assisted_only";
            };
            String expected = Component.translatable(key, COURSE).getString();
            String wanted = this.shown == 2 ? expected.substring(expected.indexOf('/')) : expected;
            boolean visible = false;
            for (LayoutAudit.Text text : LayoutProbe.capture(mc, screen).texts()) {
                visible |= text.visible() != null && text.value().contains(wanted);
            }
            if (!visible) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, expected, "steht nicht in einer Zeile der leeren Bestenliste"));
            }
            return findings;
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.setScreen(null);
            this.payload = null;
            BoardFixture.clear();
        }
    }

    private static final class Toasts implements Case {
        private static final Object TOKEN = new Object();
        private int index;

        @Override
        public String name() {
            return "Hinweise";
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.tick() == 0) {
                mc.gui.setScreen(carrier());
                return StepResult.pending();
            }
            return mc.gui.screen() != null && ctx.tick() >= 3 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public int pages(Screen screen) {
            return 3;
        }

        @Override
        public int settleTicks() {
            return 30;
        }

        @Override
        public String page(Screen screen, int index) {
            this.index = index;
            this.toast();
            return List.of("addons", "gesperrt", "training").get(index);
        }

        private ThemedToast toast() {
            Minecraft mc = Minecraft.getInstance();
            List<String> names = List.of("Propwash: Just More Parts", "Ein Add-on mit sehr langem Namen", "Noch ein Add-on");
            return switch (this.index) {
                case 0 -> ThemedToast.show(mc.gui.toastManager(), TOKEN, JoinText.sessionTitle(names.size()), JoinText.sessionMessage(names),
                        Palette.WARNING, 60000L);
                case 1 -> ThemedToast.show(mc.gui.toastManager(), TOKEN, JoinText.unavailable(),
                        JoinText.blockedItem(Component.literal("Propwash: Just More Parts – 7″-Long-Range-Rahmen")), Palette.WARNING, 60000L);
                default -> ThemedToast.show(mc.gui.toastManager(), TOKEN, Component.translatable("propwash.guide.training.toast.chapter"),
                        Component.translatable("propwash.guide.training.toast.chapter.text", Component.translatable("propwash.guide.training.toast.done")),
                        Palette.SUCCESS, 60000L);
            };
        }

        @Override
        public List<Finding> audit(Minecraft mc, Screen screen) {
            ThemedToast toast = this.toast();
            return LayoutProbe.auditDrawing(mc, LayoutAudit.Box.of(0, 0, screen.width, screen.height), g -> {
                g.pose().pushMatrix();
                g.pose().translate(screen.width - toast.width(), 0.0F);
                toast.extractRenderState(g, mc.font, 0L);
                g.pose().popMatrix();
            });
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            Minecraft.getInstance().gui.toastManager().clear();
            Minecraft.getInstance().gui.setScreen(null);
        }
    }
    private static final class TrainingPanel implements Case {
        private final List<Lesson> lessons = Curriculum.lessons().stream()
                .sorted(Comparator.comparingInt((Lesson lesson) -> Component.translatable(lesson.titleKey()).getString().length()).reversed())
                .limit(3)
                .toList();

        @Override
        public String name() {
            return "Flugtraining-Anzeige";
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.tick() == 0) {
                mc.gui.setScreen(carrier());
                return StepResult.pending();
            }
            return mc.gui.screen() != null && ctx.tick() >= 3 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public int pages(Screen screen) {
            return this.lessons.size();
        }

        @Override
        public String page(Screen screen, int index) {
            Lesson lesson = this.lessons.get(index);
            TrainingSession.INSTANCE.select(lesson);
            Minecraft.getInstance().gui.toastManager().clear();
            return lesson.code();
        }

        @Override
        public List<Finding> audit(Minecraft mc, Screen screen) {
            return LayoutProbe.auditDrawing(mc, LayoutAudit.Box.of(0, 0, screen.width, screen.height), g -> TrainingHud.extract(g, DeltaTracker.ZERO));
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            TrainingSession.INSTANCE.stop();
            Minecraft.getInstance().gui.toastManager().clear();
            Minecraft.getInstance().gui.setScreen(null);
        }
    }
}
