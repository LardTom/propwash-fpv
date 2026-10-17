package de.lardtom.propwash.drone.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.FlightModeSelector;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.input.client.screen.MicrosField;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

final class ModeSwitchSelfTests {
    private static final String BOXER_GUID = "propwash-selftest-boxer";
    private static final String MATRIX_GUID = "propwash-selftest-switch-matrix";
    private static final int AXES = 8;
    private static final int BUTTONS = 4;
    private static final int AUX4 = 7;
    private static final int THROTTLE_AXIS = 2;
    private static final int SETTLE_TICKS = 40;
    private static final int STABLE_TICKS = 6;
    private static final int MAX_TICKS = 2400;
    private static final List<SwitchFunction> WATCHED = List.of(SwitchFunction.ARM, SwitchFunction.ANGLE, SwitchFunction.HORIZON,
            SwitchFunction.MODE_3D, SwitchFunction.BEEPER, SwitchFunction.RESCUE, SwitchFunction.ALT_HOLD);

    private ModeSwitchSelfTests() {
    }

    static void register() {
        SelfTest.clientStep("drone: Modusschalter – Angle in der Mitte und Horizon unten auf AUX 4 wie an der Boxer: Flug, OSD und Modi-Tab "
                + "(pausiert, ungespeichert, gespeichert, Taste gegen Schalter)", MAX_TICKS + 100, ModeSwitchSelfTests::boxer);
        SelfTest.clientStep("drone: Modusschalter – Scharf, Angle, Horizon, 3D, Piepser, Rescue auf einem Kanal und auf eigenen Kanälen "
                + "(Bereich, umgekehrt, Umschalter, Taste) im Modi-Tab und in der Funke", MAX_TICKS + 100, ModeSwitchSelfTests::matrix);
    }

    private static DeviceProfile boxerProfile() {
        DeviceProfile profile = new DeviceProfile();
        profile.name = "Radiomaster Boxer";
        profile.setupDone = true;
        profile.setBinding(SwitchFunction.ARM, SwitchAssignment.axisMicros(4, 1700, 2100));
        profile.setBinding(SwitchFunction.ANGLE, SwitchAssignment.axisMicros(AUX4, 1400, 1600));
        profile.setBinding(SwitchFunction.HORIZON, SwitchAssignment.axisMicros(AUX4, 1000, 1300));
        return profile;
    }

    private static StepResult boxer(SelfTest.StepContext ctx) {
        Script s = (Script) ctx.state().computeIfAbsent("boxer", key -> boxerScript());
        return s.run(ctx);
    }

    private static Script boxerScript() {
        Script s = new Script(BOXER_GUID, "Radiomaster Boxer (Test)", boxerProfile(), true);
        s.add("AUX 4 unten (1000 µs)", c -> c.set(AUX4, 1000), c -> c.flown(StabMode.HORIZON));
        s.add("AUX 4 Mitte (1500 µs)", c -> c.set(AUX4, 1500), c -> c.flown(StabMode.ANGLE));
        s.add("AUX 4 oben (2000 µs)", c -> c.set(AUX4, 2000), c -> c.flown(StabMode.ACRO));
        s.add("AUX 4 wieder unten", c -> c.set(AUX4, 1000), c -> c.flown(StabMode.HORIZON));
        s.add("Modi-Tab im Flug geöffnet", Script::openTab, c -> c.tab(StabMode.HORIZON, false));
        s.add("Modi-Tab: AUX 4 Mitte", c -> c.set(AUX4, 1500), c -> c.tab(StabMode.ANGLE, false));
        s.shot("modes_switch_boxer_angle");
        s.add("Modi-Tab: AUX 4 oben", c -> c.set(AUX4, 2000), c -> c.tab(StabMode.ACRO, false));
        s.add("Modi-Tab: AUX 4 unten", c -> c.set(AUX4, 1000), c -> c.tab(StabMode.HORIZON, false));
        swapRanges(s, StabMode.ACRO);
        s.add("Modi-Tab: getauschte Bereiche nicht gespeichert, AUX 4 Mitte", c -> c.set(AUX4, 1500), c -> c.tab(StabMode.HORIZON, false));
        s.add("Verwerfen und schließen: alte Belegung fliegt", c -> c.closeTab(false), c -> c.flown(StabMode.ANGLE));
        s.add("Modi-Tab wieder geöffnet", Script::openTab, c -> c.tab(StabMode.ANGLE, false));
        swapRanges(s, StabMode.HORIZON);
        s.add("Speichern und schließen: neue Belegung fliegt", c -> c.closeTab(true), c -> c.flown(StabMode.HORIZON));
        s.add("neue Belegung: AUX 4 oben", c -> c.set(AUX4, 2000), c -> c.flown(StabMode.ANGLE));
        s.add("neue Belegung: AUX 4 unten", c -> c.set(AUX4, 1000), c -> c.flown(StabMode.ACRO));
        s.add("AUX 4 oben, dann Acro-Taste", c -> c.set(AUX4, 2000), c -> c.flown(StabMode.ANGLE));
        s.add("Acro-Taste gegen den Schalter", c -> press(InputKeys.MODE_ACRO), c -> c.flown(StabMode.ACRO, true));
        s.add("Modi-Tab mit Tasten-Vorrang", Script::openTab, c -> c.tab(StabMode.ACRO, true));
        s.shot("modes_switch_key_override");
        s.add("Modi-Tab: Schalter bewegt", c -> c.set(AUX4, 1500), c -> c.tab(StabMode.HORIZON, false));
        s.add("Schließen: Schalter übernimmt", c -> c.closeTab(true), c -> c.flown(StabMode.HORIZON));
        return s;
    }

    private static void swapRanges(Script s, StabMode now) {
        s.add("Angle-Bereich auf 1700–2100 µs getippt", c -> c.typeRange(1700, 2100), c -> c.range(SwitchFunction.ANGLE, 1700, 2100));
        s.add("Zeile Horizon gewählt", c -> c.row = SwitchFunction.HORIZON, c -> c.range(SwitchFunction.HORIZON, 1000, 1300));
        s.add("Horizon-Bereich auf 1400–1600 µs getippt", c -> c.typeRange(1400, 1600), c -> c.range(SwitchFunction.HORIZON, 1400, 1600));
        s.add("Zeile Angle gewählt", c -> c.row = SwitchFunction.ANGLE, c -> c.tab(now, false));
    }

    private static StepResult matrix(SelfTest.StepContext ctx) {
        Script s = (Script) ctx.state().computeIfAbsent("matrix", key -> matrixScript());
        return s.run(ctx);
    }

    private static DeviceProfile sharedProfile() {
        DeviceProfile profile = new DeviceProfile();
        profile.name = "Testfunke mit einem Modus-Kanal";
        profile.setupDone = true;
        profile.setBinding(SwitchFunction.ANGLE, SwitchAssignment.axisMicros(AUX4, 900, 1050));
        profile.setBinding(SwitchFunction.HORIZON, SwitchAssignment.axisMicros(AUX4, 1100, 1250));
        profile.setBinding(SwitchFunction.MODE_3D, SwitchAssignment.axisMicros(AUX4, 1300, 1450));
        profile.setBinding(SwitchFunction.BEEPER, SwitchAssignment.axisMicros(AUX4, 1500, 1650));
        profile.setBinding(SwitchFunction.RESCUE, SwitchAssignment.axisMicros(AUX4, 1700, 1850));
        profile.setBinding(SwitchFunction.ARM, SwitchAssignment.axisMicros(AUX4, 1900, 2100));
        return profile;
    }

    private static DeviceProfile separateProfile() {
        DeviceProfile profile = new DeviceProfile();
        profile.name = "Testfunke mit eigenen Kanälen";
        profile.setupDone = true;
        SwitchAssignment angle = SwitchAssignment.axisMicros(4, 1700, 2100);
        angle.invert = true;
        profile.setBinding(SwitchFunction.ANGLE, angle);
        SwitchAssignment horizon = SwitchAssignment.axisMicros(5, 1700, 2100);
        horizon.toggle = true;
        profile.setBinding(SwitchFunction.HORIZON, horizon);
        profile.setBinding(SwitchFunction.MODE_3D, SwitchAssignment.toggleButton(0));
        profile.setBinding(SwitchFunction.BEEPER, SwitchAssignment.button(1, true));
        profile.setBinding(SwitchFunction.RESCUE, SwitchAssignment.axisMicros(6, 1400, 1600));
        SwitchAssignment arm = SwitchAssignment.axisMicros(AUX4, 1700, 2100);
        arm.toggle = true;
        arm.invert = true;
        profile.setBinding(SwitchFunction.ARM, arm);
        profile.setBinding(SwitchFunction.ALT_HOLD, SwitchAssignment.button(2, false));
        return profile;
    }

    private static Script matrixScript() {
        Script s = new Script(MATRIX_GUID, "Testfunke (Schalter-Matrix)", sharedProfile(), false);
        s.add("ein Kanal: Modi-Tab geöffnet", c -> {
            c.set(AUX4, 1075);
            Script.openTab(c);
        }, c -> c.lit());
        int[] at = {975, 1175, 1375, 1575, 1775, 2000, 1075, 1375, 975};
        SwitchFunction[] wanted = {SwitchFunction.ANGLE, SwitchFunction.HORIZON, SwitchFunction.MODE_3D, SwitchFunction.BEEPER,
                SwitchFunction.RESCUE, SwitchFunction.ARM, null, SwitchFunction.MODE_3D, SwitchFunction.ANGLE};
        for (int i = 0; i < at.length; i++) {
            int us = at[i];
            SwitchFunction function = wanted[i];
            s.add("ein Kanal bei " + us + " µs", c -> c.set(AUX4, us), function == null ? c -> c.lit() : c -> c.lit(function));
        }
        s.add("ein Kanal: Modi-Tab geschlossen", c -> c.closeTab(false), c -> c.closed());
        s.add("eigene Kanäle: Modi-Tab geöffnet", c -> {
            c.useProfile(separateProfile());
            c.set(4, 2000);
            c.set(5, 1000);
            c.set(6, 1000);
            c.set(AUX4, 1000);
            Script.openTab(c);
        }, c -> c.lit(SwitchFunction.BEEPER, SwitchFunction.ARM));
        s.add("Angle umgekehrt: Kanal 5 unten", c -> c.set(4, 1000), c -> c.lit(SwitchFunction.ANGLE, SwitchFunction.BEEPER, SwitchFunction.ARM));
        s.add("Horizon als Umschalter: Kanal 6 oben", c -> c.set(5, 2000),
                c -> c.lit(SwitchFunction.ANGLE, SwitchFunction.HORIZON, SwitchFunction.BEEPER, SwitchFunction.ARM));
        s.add("Horizon als Umschalter: Kanal 6 zurück, bleibt an", c -> c.set(5, 1000),
                c -> c.lit(SwitchFunction.ANGLE, SwitchFunction.HORIZON, SwitchFunction.BEEPER, SwitchFunction.ARM));
        s.add("3D als Umschalter-Taste: gedrückt", c -> c.button(0, true),
                c -> c.lit(SwitchFunction.ANGLE, SwitchFunction.HORIZON, SwitchFunction.MODE_3D, SwitchFunction.BEEPER, SwitchFunction.ARM));
        s.add("3D als Umschalter-Taste: losgelassen, bleibt an", c -> c.button(0, false),
                c -> c.lit(SwitchFunction.ANGLE, SwitchFunction.HORIZON, SwitchFunction.MODE_3D, SwitchFunction.BEEPER, SwitchFunction.ARM));
        s.add("Modi-Tab geschlossen, Umschalter bleiben an", c -> c.closeTab(false), c -> c.closed());
        s.add("Modi-Tab neu geöffnet: Umschalter zeigen den Zustand der Funke", Script::openTab, c -> c.lit(SwitchFunction.ANGLE, SwitchFunction.HORIZON, SwitchFunction.MODE_3D, SwitchFunction.BEEPER, SwitchFunction.ARM));
        s.add("Piepser umgekehrt: Taste gedrückt", c -> c.button(1, true),
                c -> c.lit(SwitchFunction.ANGLE, SwitchFunction.HORIZON, SwitchFunction.MODE_3D, SwitchFunction.ARM));
        s.add("Höhe halten auf Taste", c -> c.button(2, true),
                c -> c.lit(SwitchFunction.ANGLE, SwitchFunction.HORIZON, SwitchFunction.MODE_3D, SwitchFunction.ARM, SwitchFunction.ALT_HOLD));
        s.add("Rescue im Bereich", c -> c.set(6, 1500),
                c -> c.lit(SwitchFunction.ANGLE, SwitchFunction.HORIZON, SwitchFunction.MODE_3D, SwitchFunction.ARM, SwitchFunction.ALT_HOLD,
                        SwitchFunction.RESCUE));
        s.add("Scharf als umgekehrter Umschalter: AUX 4 oben", c -> c.set(AUX4, 2000),
                c -> c.lit(SwitchFunction.ANGLE, SwitchFunction.HORIZON, SwitchFunction.MODE_3D, SwitchFunction.ALT_HOLD, SwitchFunction.RESCUE));
        s.add("alles zurück", c -> {
            c.set(4, 2000);
            c.set(5, 2000);
            c.set(6, 1000);
            c.set(AUX4, 1000);
            c.button(0, true);
            c.button(1, false);
            c.button(2, false);
        }, c -> c.lit(SwitchFunction.BEEPER));
        s.add("eigene Kanäle: Modi-Tab geschlossen", c -> c.closeTab(false), c -> c.closed());
        return s;
    }

    private static void press(KeyMapping mapping) {
        KeyMapping.click(InputConstants.getKey(mapping.saveString()));
    }

    private record Action(String what, Consumer<Script> act, java.util.function.Function<Script, @Nullable String> expect, @Nullable String shot) {
    }

    private static final class Script {
        private final String guid;
        private final String name;
        private final boolean fly;
        private final List<Action> actions = new ArrayList<>();
        private DeviceProfile profile;
        private @Nullable SimulatedRadio radio;
        private int index = -1;
        private int since;
        private int stableSince = -1;
        private boolean preparing = true;
        private boolean spawned;
        private boolean sessionSeen;
        private boolean wantTab;
        private SwitchFunction row = SwitchFunction.ANGLE;
        private boolean saveOnClose;
        private boolean closing;
        private @Nullable CompletableFuture<Void> pending;
        private @Nullable CompletableFuture<String> shooting;
        private volatile int droneId = -1;
        private volatile @Nullable ItemStack previousHead;
        private volatile @Nullable ItemStack previousHand;
        private @Nullable Boolean keyboardFallback;
        private @Nullable Boolean mode3dUnlocked;
        private @Nullable Boolean suppressDevice;
        private @Nullable DeviceProfile previousProfile;
        private final List<String> log = new ArrayList<>();
        private boolean cleaned;

        private Script(String guid, String name, DeviceProfile profile, boolean fly) {
            this.guid = guid;
            this.name = name;
            this.profile = profile;
            this.fly = fly;
        }

        void add(String what, Consumer<Script> act, java.util.function.Function<Script, @Nullable String> expect) {
            this.actions.add(new Action(what, act, expect, null));
        }

        void shot(String file) {
            this.actions.add(new Action("Bildschirmfoto " + file, c -> {
            }, c -> null, file));
        }

        StepResult run(SelfTest.StepContext ctx) {
            try {
                StepResult result = this.advance(ctx);
                if (result.status() != StepResult.Status.PENDING) {
                    this.cleanup(ctx);
                }
                return result;
            } catch (RuntimeException e) {
                this.cleanup(ctx);
                throw e;
            }
        }

        private StepResult advance(SelfTest.StepContext ctx) {
            CompletableFuture<Void> waiting = this.pending;
            if (waiting != null) {
                if (waiting.isCompletedExceptionally()) {
                    return StepResult.fail("Server-Aktion fehlgeschlagen: " + waiting.exceptionNow().getMessage());
                }
                if (!waiting.isDone()) {
                    return StepResult.pending();
                }
                this.pending = null;
            }
            if (ctx.tick() > MAX_TICKS) {
                return StepResult.fail("Zeit abgelaufen bei " + this.current() + " (" + this.describe() + ")");
            }
            if (this.preparing) {
                return this.prepare(ctx);
            }
            String screen = this.syncScreen();
            if (screen != null) {
                return ctx.tick() - this.since > SETTLE_TICKS * 3 ? StepResult.fail(this.current() + ": " + screen) : StepResult.pending();
            }
            Action action = this.actions.get(this.index);
            if (action.shot() != null) {
                return this.shoot(ctx, action.shot());
            }
            if (this.fly && (!PilotSession.INSTANCE.isActive() || PilotSession.INSTANCE.drone() == null)) {
                return StepResult.fail(action.what() + ": Sitzung verloren");
            }
            String problem = action.expect().apply(this);
            int waited = ctx.tick() - this.since;
            if (problem != null) {
                this.stableSince = -1;
                return waited > SETTLE_TICKS ? StepResult.fail(action.what() + ": " + problem + " (" + this.describe() + ")") : StepResult.pending();
            }
            if (this.stableSince < 0) {
                this.stableSince = ctx.tick();
            }
            if (ctx.tick() - this.stableSince < STABLE_TICKS) {
                return StepResult.pending();
            }
            this.log.add(action.what() + " nach " + (this.stableSince - this.since) + " Ticks" + this.pauseNote());
            return this.next(ctx);
        }

        private StepResult next(SelfTest.StepContext ctx) {
            this.index++;
            this.since = ctx.tick();
            this.stableSince = -1;
            if (this.index >= this.actions.size()) {
                ctx.log(this.name + ": " + String.join("; ", this.log));
                return StepResult.pass();
            }
            this.actions.get(this.index).act().accept(this);
            return StepResult.pending();
        }

        private StepResult prepare(SelfTest.StepContext ctx) {
            if (this.radio == null) {
                InputConfig config = InputConfig.get();
                this.keyboardFallback = config.keyboardFallback;
                this.mode3dUnlocked = config.mode3dUnlocked;
                this.suppressDevice = TransmitterInput.INSTANCE.suppressDevice();
                this.previousProfile = config.profiles.get(this.guid);
                config.mode3dUnlocked = true;
                config.profiles.put(this.guid, this.profile.copy());
                SimulatedRadio fake = new SimulatedRadio(this.guid, this.name, this.profile.copy(), AXES, BUTTONS).useSavedProfile();
                fake.setMicros(THROTTLE_AXIS, 1000);
                for (int axis = 4; axis < AXES; axis++) {
                    fake.setMicros(axis, 1000);
                }
                this.radio = fake;
                TransmitterInput.INSTANCE.simulate(fake);
                PilotSession.INSTANCE.resetFlightModes();
                this.since = ctx.tick();
                return StepResult.pending();
            }
            if (!this.guid.equals(RadioState.INSTANCE.deviceGuid())) {
                return ctx.tick() - this.since > 60 ? StepResult.fail("Testfunke meldet sich nicht: " + this.describe()) : StepResult.pending();
            }
            if (!this.fly) {
                this.preparing = false;
                return this.next(ctx);
            }
            if (!this.spawned) {
                this.spawned = true;
                this.pending = ctx.onServer(this::spawnAndFly);
                this.since = ctx.tick();
                return StepResult.pending();
            }
            QuadcopterEntity drone = PilotSession.INSTANCE.drone();
            if (drone == null || drone.getId() != this.droneId || !PilotSession.INSTANCE.isFpv()) {
                return ctx.tick() - this.since > 200 ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne") : StepResult.pending();
            }
            PilotSession.INSTANCE.clearTestInput();
            this.sessionSeen = true;
            this.preparing = false;
            return this.next(ctx);
        }

        private @Nullable String syncScreen() {
            Minecraft mc = Minecraft.getInstance();
            if (!this.wantTab) {
                if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                    if (!this.closing) {
                        this.closing = true;
                        if (this.saveOnClose) {
                            screen.saveAndClose();
                        } else {
                            screen.discardAndClose();
                        }
                    }
                    return "Konfigurator schließt nicht";
                }
                this.closing = false;
                return null;
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                if (mc.gui.screen() == null) {
                    ConfigTabs.open(null, ModesTab.ID);
                }
                return "Konfigurator öffnet nicht";
            }
            ConfigTab active = screen.activeTab();
            if (!(active instanceof ModesTab tab)) {
                screen.selectTab(ModesTab.ID);
                return "Modi-Tab öffnet nicht";
            }
            if (tab.commandsShown()) {
                tab.showCommands(false);
                return "Seite „Flug“ fehlt";
            }
            if (tab.selected() != this.row) {
                tab.select(this.row);
                return "Zeile " + this.row + " nicht gewählt";
            }
            return null;
        }

        static void openTab(Script s) {
            s.wantTab = true;
        }

        void closeTab(boolean save) {
            this.wantTab = false;
            this.saveOnClose = save;
            this.closing = false;
        }

        void useProfile(DeviceProfile next) {
            this.profile = next;
            InputConfig.get().profiles.put(this.guid, next.copy());
        }

        void set(int axis, int micros) {
            Objects.requireNonNull(this.radio).setMicros(axis, micros);
        }

        void button(int index, boolean down) {
            Objects.requireNonNull(this.radio).setButton(index, down);
        }

        void typeRange(int low, int high) {
            List<MicrosField> fields = this.rangeFields();
            if (fields.size() != 2) {
                throw new IllegalStateException("Bereichsfelder: " + fields.size() + " statt 2");
            }
            MicrosField min = fields.get(0);
            MicrosField max = fields.get(1);
            boolean highFirst = Integer.parseInt(max.shownValue()) < high;
            type(highFirst ? max : min, highFirst ? high : low);
            type(highFirst ? min : max, highFirst ? low : high);
        }

        private List<MicrosField> rangeFields() {
            List<MicrosField> fields = new ArrayList<>();
            if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
                for (GuiEventListener child : screen.children()) {
                    if (child instanceof MicrosField field) {
                        fields.add(field);
                    }
                }
            }
            fields.sort(Comparator.comparingInt(MicrosField::getX));
            return fields;
        }

        @Nullable String range(SwitchFunction function, int low, int high) {
            ModesTab tab = this.tab();
            if (tab == null || tab.selected() != function) {
                return "Zeile " + function + " nicht gewählt";
            }
            List<MicrosField> fields = this.rangeFields();
            if (fields.size() != 2) {
                return "Bereichsfelder: " + fields.size() + " statt 2";
            }
            String shown = fields.get(0).shownValue() + "–" + fields.get(1).shownValue();
            return shown.equals(low + "–" + high) ? null : "Bereich " + shown + " statt " + low + "–" + high;
        }

        private static void type(MicrosField field, int value) {
            field.editor().start(Integer.toString(value), false);
            field.commitEdit();
        }

        @Nullable ModesTab tab() {
            return Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen && screen.activeTab() instanceof ModesTab tab ? tab : null;
        }

        @Nullable String flown(StabMode wanted) {
            return this.flown(wanted, false);
        }

        @Nullable String flown(StabMode wanted, boolean keyOverride) {
            PilotSession session = PilotSession.INSTANCE;
            if (Minecraft.getInstance().gui.screen() != null) {
                return "Bildschirm offen: " + Minecraft.getInstance().gui.screen().getClass().getSimpleName();
            }
            if (session.flightMode() != wanted) {
                return "Drohne fliegt " + session.flightMode() + " statt " + wanted;
            }
            OsdData osd = OsdHud.sampler().data();
            if (osd.active && osd.mode != wanted) {
                return "OSD zeigt " + osd.mode + " statt " + wanted;
            }
            if (session.keyOverridesModeSwitch() != keyOverride) {
                return "Tasten-Vorrang " + session.keyOverridesModeSwitch() + " statt " + keyOverride;
            }
            boolean device = RadioState.INSTANCE.source() != RadioSource.KEYBOARD && RadioState.INSTANCE.connected();
            for (StabMode mode : StabMode.values()) {
                if (ModesTab.modeLit(mode, device, RadioState.INSTANCE.activeSwitches()) != (mode == wanted)) {
                    return "Lampe " + mode + " ist " + (mode == wanted ? "aus" : "an");
                }
            }
            return null;
        }

        @Nullable String tab(StabMode wanted, boolean keyOverride) {
            ModesTab tab = this.tab();
            if (tab == null) {
                return "Modi-Tab nicht offen";
            }
            boolean[] lamps = {tab.acroLit(), tab.lit(SwitchFunction.ANGLE), tab.lit(SwitchFunction.HORIZON)};
            StabMode[] modes = {StabMode.ACRO, StabMode.ANGLE, StabMode.HORIZON};
            for (int i = 0; i < modes.length; i++) {
                if (lamps[i] != (modes[i] == wanted)) {
                    return "Modi-Tab: Lampe " + modes[i] + " ist " + (lamps[i] ? "an" : "aus") + ", erwartet " + wanted;
                }
            }
            if (tab.keyOverrideShown() != keyOverride) {
                return "Modi-Tab: Hinweis Tasten-Vorrang " + tab.keyOverrideShown() + " statt " + keyOverride;
            }
            return null;
        }

        @Nullable String lit(SwitchFunction... wanted) {
            ModesTab tab = this.tab();
            if (tab == null) {
                return "Modi-Tab nicht offen";
            }
            Set<SwitchFunction> on = wanted.length == 0 ? EnumSet.noneOf(SwitchFunction.class) : EnumSet.copyOf(List.of(wanted));
            for (SwitchFunction function : WATCHED) {
                boolean want = on.contains(function);
                if (tab.lit(function) != want) {
                    return "Modi-Tab: " + function + " ist " + (want ? "aus" : "an");
                }
                if (RadioState.INSTANCE.isActive(function) != want) {
                    return "Funke: " + function + " ist " + (want ? "aus" : "an");
                }
            }
            boolean acro = !on.contains(SwitchFunction.ANGLE) && !on.contains(SwitchFunction.HORIZON);
            if (tab.acroLit() != acro) {
                return "Modi-Tab: Acro ist " + (acro ? "aus" : "an");
            }
            return null;
        }

        @Nullable String closed() {
            return Minecraft.getInstance().gui.screen() == null ? null : "Konfigurator noch offen";
        }

        private StepResult shoot(SelfTest.StepContext ctx, String file) {
            if (!Screenshots.enabled()) {
                return this.next(ctx);
            }
            Minecraft mc = Minecraft.getInstance();
            CompletableFuture<String> shot = this.shooting;
            if (shot == null) {
                Screenshots.parkMouse(mc);
                if (ctx.tick() - this.since < 10) {
                    mc.getTutorial().setStep(TutorialSteps.NONE);
                    Screenshots.clearOverlays(mc);
                    return StepResult.pending();
                }
                this.shooting = Screenshots.capture(file);
                return StepResult.pending();
            }
            if (!shot.isDone()) {
                return StepResult.pending();
            }
            this.shooting = null;
            if (shot.isCompletedExceptionally()) {
                return StepResult.fail("Bildschirmfoto " + file + ": " + shot.exceptionNow());
            }
            ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(file + ".png") + " (" + shot.join() + ")");
            return this.next(ctx);
        }

        private String current() {
            return this.index < 0 ? "Vorbereitung" : this.index < this.actions.size() ? this.actions.get(this.index).what() : "Ende";
        }

        private String pauseNote() {
            return Minecraft.getInstance().isPaused() ? " (pausiert)" : "";
        }

        private String describe() {
            RadioState radio = RadioState.INSTANCE;
            PilotSession session = PilotSession.INSTANCE;
            Minecraft mc = Minecraft.getInstance();
            return "Quelle " + radio.source() + " " + radio.deviceGuid() + ", Schalter " + Integer.toBinaryString(radio.activeSwitches())
                    + ", Sitzung " + session.isActive() + ", Modus " + session.flightMode() + " (" + session.flightModeSource() + ")"
                    + ", Bildschirm " + (mc.gui.screen() == null ? "keiner" : mc.gui.screen().getClass().getSimpleName()) + ", pausiert " + mc.isPaused();
        }

        private void spawnAndFly(MinecraftServer server) {
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) {
                throw new IllegalStateException("kein Spieler auf dem Server");
            }
            ServerPlayer player = players.getFirst();
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            ServerLevel level = player.level();
            QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("EntityType.create lieferte null");
            }
            Vec3 feet = player.position();
            drone.snapTo(feet.x, feet.y, feet.z + 3.0, 0.0F, 0.0F);
            drone.setAttitude(new Quaternionf().rotationY((float) Math.PI));
            drone.setBuild(DroneBuild.FREESTYLE);
            if (!level.addFreshEntity(drone)) {
                throw new IllegalStateException("addFreshEntity fehlgeschlagen");
            }
            this.droneId = drone.getId();
            ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
            if (goggles.isEmpty()) {
                throw new IllegalStateException("Kreativ-Brille nicht registriert");
            }
            goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            this.previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
            player.setItemSlot(EquipmentSlot.HEAD, goggles);
            ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
            remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            this.previousHand = player.getMainHandItem().copy();
            player.setItemSlot(EquipmentSlot.MAINHAND, remote);
            DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
            if (result != DronePiloting.StartResult.STARTED) {
                throw new IllegalStateException("FPV-Start verweigert: " + result);
            }
        }

        private void cleanup(SelfTest.StepContext ctx) {
            if (this.cleaned) {
                return;
            }
            this.cleaned = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            InputKeys.drain(InputKeys.MODE_ACRO);
            TransmitterInput.INSTANCE.simulate(null);
            PilotSession.INSTANCE.clearTestInput();
            InputConfig config = InputConfig.get();
            if (this.previousProfile != null) {
                config.profiles.put(this.guid, this.previousProfile);
            } else {
                config.profiles.remove(this.guid);
            }
            if (this.keyboardFallback != null) {
                config.keyboardFallback = this.keyboardFallback;
            }
            if (this.mode3dUnlocked != null) {
                config.mode3dUnlocked = this.mode3dUnlocked;
            }
            if (this.suppressDevice != null) {
                TransmitterInput.INSTANCE.setSuppressDevice(this.suppressDevice);
            }
            InputConfig.commit(config);
            if (!this.spawned) {
                return;
            }
            ctx.onServer(server -> {
                for (ServerLevel level : server.getAllLevels()) {
                    Entity entity = this.droneId >= 0 ? level.getEntity(this.droneId) : null;
                    if (entity instanceof QuadcopterEntity drone) {
                        DronePiloting.release(drone);
                        drone.discard();
                    }
                }
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                if (!players.isEmpty()) {
                    ServerPlayer player = players.getFirst();
                    if (this.previousHead != null) {
                        player.setItemSlot(EquipmentSlot.HEAD, this.previousHead);
                    }
                    if (this.previousHand != null) {
                        player.setItemSlot(EquipmentSlot.MAINHAND, this.previousHand);
                    }
                }
            });
        }
    }
}
