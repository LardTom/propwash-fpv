package de.lardtom.propwash.drone.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.Mode3dSelector;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdWarnings;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.ArmBlocker;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Mode3dSelfTests {
    static final String RADIO_GUID = "propwash-selftest-3d-radio";
    static final int AXES = 9;
    static final int THROTTLE_AXIS = 2;
    static final int ARM_AXIS = ProfileDefaults.AUX_ARM;
    static final int MODE_3D_AXIS = 8;
    private static final String SCENARIO = "mode_3d";
    private static final double DEADBAND = 0.05;

    private Mode3dSelfTests() {
    }

    enum Kind {
        LOCKED,
        RADIO,
        INVERTED,
        KEYBOARD,
        RULE
    }

    static void register() {
        SelfTest.clientStep("drone: 3D-Modus gesperrt – Funkenschalter und Taste schalten nichts, Meldung nennt den Tab Modi", 1200,
                ctx -> step(ctx, Kind.LOCKED));
        SelfTest.clientStep("drone: 3D-Modus mit Funke – Schalter, scharf nur in der Gasmitte, Umkehrschub, Taste gegen Schalter", 2400,
                ctx -> step(ctx, Kind.RADIO));
        SelfTest.clientStep("drone: 3D-Modus kopfüber – kopfüber am Boden scharf, kopfüber abheben und schweben, Rolle zurück", 2400,
                ctx -> step(ctx, Kind.INVERTED));
        SelfTest.clientStep("drone: 3D-Modus nur mit Tastatur – Gas steht nach dem Einschalten in der Mitte", 1200, ctx -> step(ctx, Kind.KEYBOARD));
        SelfTest.clientStep("drone: Welt-Regel propwash:allow_3d_mode aus hält den 3D-Modus aus", 1200, ctx -> step(ctx, Kind.RULE));
    }

    private static StepResult step(SelfTest.StepContext ctx, Kind kind) {
        Script script = (Script) ctx.state().computeIfAbsent(SCENARIO, key -> new Script(kind, phases(kind)));
        try {
            StepResult result = script.run(ctx);
            if (result.status() != StepResult.Status.PENDING) {
                cleanup(ctx, script);
            }
            return result;
        } catch (RuntimeException exception) {
            cleanup(ctx, script);
            throw exception;
        }
    }

    private static List<Phase> phases(Kind kind) {
        List<Phase> out = new ArrayList<>();
        switch (kind) {
            case LOCKED -> {
                out.add(Phase.of("Schalter an, 3D gesperrt", s -> s.radio().setMicros(MODE_3D_AXIS, 2000), s -> {
                    String off = expect3d(false);
                    if (off != null) {
                        return off;
                    }
                    return toastIs(PilotSession.mode3dBlockText(Mode3dSelector.Block.LOCKED));
                }));
                out.add(Phase.of("Taste 3D, weiter gesperrt", s -> press(InputKeys.MODE_3D), s -> {
                    String off = expect3d(false);
                    if (off != null) {
                        return off;
                    }
                    if (PilotSession.INSTANCE.mode3dBlock() != Mode3dSelector.Block.LOCKED) {
                        return "Sperre " + PilotSession.INSTANCE.mode3dBlock();
                    }
                    return toastIs(PilotSession.mode3dBlockText(Mode3dSelector.Block.LOCKED));
                }));
            }
            case RADIO -> {
                out.add(Phase.of("Schalter an", s -> {
                    s.radio().setMicros(ProfileDefaults.AUX_MODE, 2000);
                    s.radio().setMicros(MODE_3D_AXIS, 2000);
                }, s -> {
                    String on = expect3d(true);
                    return on != null ? on : toastIs(Component.translatable("propwash.drone.mode3d.on"));
                }));
                out.add(Phase.of("Gas unten und Arm-Schalter: gesperrt", s -> {
                    s.radio().setMicros(THROTTLE_AXIS, 1000);
                    s.radio().setMicros(ARM_AXIS, 2000);
                }, s -> {
                    Telemetry t = telemetry();
                    if (t == null || t.armed) {
                        return "scharf trotz Gas unten";
                    }
                    if (!ArmBlocker.THROTTLE_NOT_CENTERED.isSet(t.armBlockers)) {
                        return "Blocker " + t.armBlockers;
                    }
                    List<OsdWarnings.Warning> warnings = new ArrayList<>();
                    OsdWarnings.collect(OsdHud.sampler().data(), warnings);
                    return warnings.contains(OsdWarnings.Warning.ARM_THROTTLE_3D) ? null : "OSD-Warnung fehlt: " + warnings;
                }));
                out.add(Phase.of("Gas Mitte, Arm-Schalter neu: scharf", s -> {
                    s.radio().setMicros(ARM_AXIS, 1000);
                    s.radio().setMicros(THROTTLE_AXIS, 1500);
                }, s -> {
                    if (s.ticks() == 6) {
                        s.radio().setMicros(ARM_AXIS, 2000);
                    }
                    Telemetry t = telemetry();
                    return t != null && t.armed ? null : "nicht scharf, Blocker " + (t == null ? -1 : t.armBlockers);
                }));
                out.add(Phase.of("Gas hoch: steigt", s -> {
                    s.startY = comY();
                    s.radio().setMicros(THROTTLE_AXIS, 1800);
                }, s -> comY() > s.startY + 9.0 ? null : "steigt nicht: " + fmt(comY() - s.startY)).stable(1).limit(300));
                out.add(Phase.of("Gas Mitte: kein Schub", s -> s.radio().setMicros(THROTTLE_AXIS, 1500), s -> {
                    Telemetry t = Objects.requireNonNull(telemetry());
                    OsdData osd = OsdHud.sampler().data();
                    if (Math.abs(t.throttleOut) > 0.2 || Math.abs(RadioState.INSTANCE.throttle3d() - 0.5F) > 0.01F) {
                        return "Gas " + fmt(t.throttleOut) + ", Knüppel " + RadioState.INSTANCE.throttle3d();
                    }
                    return osd.active && !osd.mode3d ? "OSD ohne 3D" : null;
                }).stable(3));
                out.add(Phase.of("Gas unten: Motoren rückwärts", s -> s.radio().setMicros(THROTTLE_AXIS, 1150), s -> {
                    Telemetry t = Objects.requireNonNull(telemetry());
                    for (int i = 0; i < 4; i++) {
                        if (!(t.motorRpm[i] < 0.0)) {
                            return "Motor " + i + " dreht " + fmt(t.motorRpm[i]) + " U/min";
                        }
                    }
                    OsdData osd = OsdHud.sampler().data();
                    if (t.totalThrustN >= 0.0 || osd.active && !(osd.throttle < -0.3)) {
                        return "Schub " + fmt(t.totalThrustN) + " N, OSD-Gas " + fmt(osd.throttle);
                    }
                    return null;
                }).stable(3).limit(200));
                out.add(Phase.of("Taste 3D gegen Schalter: aus, Motoren wieder vorwärts", s -> {
                    s.radio().setMicros(THROTTLE_AXIS, 1700);
                    press(InputKeys.MODE_3D);
                }, s -> {
                    String off = expect3d(false);
                    if (off != null) {
                        return off;
                    }
                    if (!PilotSession.INSTANCE.keyOverridesMode3dSwitch()) {
                        return "Taste überstimmt den Schalter nicht";
                    }
                    String toast = toastIs(Component.translatable("propwash.drone.mode3d.off_key_switch_on"));
                    if (toast != null) {
                        return toast;
                    }
                    Telemetry t = Objects.requireNonNull(telemetry());
                    for (int i = 0; i < 4; i++) {
                        if (!(t.motorRpm[i] > 0.0)) {
                            return "Motor " + i + " noch rückwärts: " + fmt(t.motorRpm[i]);
                        }
                    }
                    return null;
                }).limit(200));
                out.add(Phase.of("Schalter aus und wieder an: 3D an", s -> s.radio().setMicros(MODE_3D_AXIS, 1000), s -> {
                    if (s.ticks() == 8) {
                        s.radio().setMicros(MODE_3D_AXIS, 2000);
                    }
                    if (s.ticks() < 10) {
                        return "warte";
                    }
                    String on = expect3d(true);
                    if (on != null) {
                        return on;
                    }
                    return PilotSession.INSTANCE.mode3dSource() == Mode3dSelector.Source.SWITCH ? null : "Quelle " + PilotSession.INSTANCE.mode3dSource();
                }));
                out.add(Phase.of("Arm-Schalter aus", s -> {
                    s.radio().setMicros(THROTTLE_AXIS, 1500);
                    s.radio().setMicros(ARM_AXIS, 1000);
                }, s -> {
                    Telemetry t = telemetry();
                    return t != null && !t.armed ? null : "noch scharf";
                }));
            }
            case INVERTED -> {
                out.add(Phase.of("kopfüber ohne 3D: Scharfschalten gesperrt", s -> PilotSession.INSTANCE.setTestMode3d(false), s -> {
                    PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, s.ticks() % 20 >= 10, StabMode.ACRO);
                    Telemetry t = telemetry();
                    if (t == null || t.tiltDeg < 150.0) {
                        return "liegt nicht kopfüber: " + (t == null ? "-" : fmt(t.tiltDeg));
                    }
                    if (t.armed) {
                        return "scharf trotz Kopflage ohne 3D";
                    }
                    return s.ticks() > 25 && ArmBlocker.TILTED.isSet(t.armBlockers) ? null : "Blocker " + t.armBlockers;
                }));
                out.add(Phase.of("kopfüber mit 3D und Gas Mitte: scharf", s -> {
                    PilotSession.INSTANCE.setTestMode3d(true);
                    PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.5F), false, StabMode.ACRO);
                }, s -> {
                    PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.5F), s.ticks() >= 6, StabMode.ACRO);
                    Telemetry t = telemetry();
                    return t != null && t.armed && t.mode3d ? null : "nicht scharf, Blocker " + (t == null ? -1 : t.armBlockers);
                }));
                out.add(Phase.of("Umkehrschub hebt kopfüber ab", s -> {
                    s.startY = comY();
                    s.targetY = s.startY + 5.0;
                    s.integral = 0.0;
                }, s -> {
                    hover(s, true);
                    Telemetry t = Objects.requireNonNull(telemetry());
                    if (t.tiltDeg < 140.0) {
                        return "dreht sich beim Abheben: " + fmt(t.tiltDeg);
                    }
                    return comY() > s.startY + 3.0 ? null : "hebt nicht ab: " + fmt(comY() - s.startY);
                }).stable(1).limit(300));
                out.add(Phase.of("kopfüber schweben", s -> {
                }, s -> {
                    hover(s, true);
                    Telemetry t = Objects.requireNonNull(telemetry());
                    if (t.tiltDeg < 150.0) {
                        return "nicht kopfüber: " + fmt(t.tiltDeg);
                    }
                    if (Math.abs(comY() - s.targetY) > 1.0) {
                        return "Höhe " + fmt(comY() - s.targetY);
                    }
                    if (t.totalThrustN >= 0.0 || t.throttleOut >= 0.0) {
                        return "kein Umkehrschub: " + fmt(t.totalThrustN) + " N";
                    }
                    OsdData osd = OsdHud.sampler().data();
                    return osd.active && (!osd.mode3d || osd.throttle >= 0.0) ? "OSD " + osd.mode3d + " " + fmt(osd.throttle) : null;
                }).stable(40).limit(500));
                out.add(Phase.of("Rolle zurück: aufrecht mit Schub vorwärts", s -> s.integral = 0.0, s -> {
                    Telemetry t = Objects.requireNonNull(telemetry());
                    if (!s.flipped) {
                        PilotSession.INSTANCE.setTestInput(new StickPositions(0.8F, 0.0F, 0.0F, 0.5F), true, StabMode.ACRO);
                        if (t.tiltDeg < 25.0) {
                            s.flipped = true;
                        }
                        return "Rolle " + fmt(t.tiltDeg);
                    }
                    hover(s, false);
                    if (t.tiltDeg > 30.0) {
                        return "nicht aufrecht: " + fmt(t.tiltDeg);
                    }
                    return t.totalThrustN > 0.0 && comY() > s.startY + 1.0 ? null : "Schub " + fmt(t.totalThrustN) + ", Höhe " + fmt(comY() - s.startY);
                }).stable(20).limit(400));
                out.add(Phase.of("Gas Mitte und unscharf", s -> {
                }, s -> {
                    PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.5F), false, StabMode.ACRO);
                    Telemetry t = telemetry();
                    return t != null && !t.armed ? null : "noch scharf";
                }));
            }
            case KEYBOARD -> {
                out.add(Phase.of("Taste 3D an: Gas springt in die Mitte", s -> press(InputKeys.MODE_3D), s -> {
                    String on = expect3d(true);
                    if (on != null) {
                        return on;
                    }
                    float throttle = RadioState.INSTANCE.sticks().throttle();
                    return Math.abs(throttle - 0.5F) < 0.001F ? null : "Tastatur-Gas " + throttle;
                }));
                out.add(Phase.of("Arm-Taste: scharf in der Mitte", s -> press(InputKeys.ARM), s -> {
                    Telemetry t = telemetry();
                    return t != null && t.armed && t.mode3d ? null : "nicht scharf, Blocker " + (t == null ? -1 : t.armBlockers);
                }));
                out.add(Phase.of("Arm-Taste: unscharf", s -> press(InputKeys.ARM), s -> {
                    Telemetry t = telemetry();
                    return t != null && !t.armed ? null : "noch scharf";
                }));
                out.add(Phase.of("Taste 3D aus: Gas wieder unten", s -> press(InputKeys.MODE_3D), s -> {
                    String off = expect3d(false);
                    if (off != null) {
                        return off;
                    }
                    float throttle = RadioState.INSTANCE.sticks().throttle();
                    return throttle < 0.001F ? null : "Tastatur-Gas " + throttle;
                }));
            }
            case RULE -> {
                out.add(Phase.of("Taste 3D an", s -> press(InputKeys.MODE_3D), s -> expect3d(true)));
                out.add(Phase.of("Regel aus: 3D aus mit Meldung", s -> s.pending = s.ctx().onServer(server ->
                        WorldRules.set(server, WorldRuleSpecs.ALLOW_3D_MODE, 0)), s -> {
                    if (ClientWorldRules.INSTANCE.on(WorldRuleSpecs.ALLOW_3D_MODE)) {
                        return "Regel noch nicht am Client";
                    }
                    String off = expect3d(false);
                    if (off != null) {
                        return off;
                    }
                    if (!PilotSession.INSTANCE.mode3dSelected()) {
                        return "Auswahl verloren";
                    }
                    return toastIs(PilotSession.mode3dBlockText(Mode3dSelector.Block.FORBIDDEN));
                }));
                out.add(Phase.of("Regel wieder an: 3D zurück", s -> s.pending = s.ctx().onServer(server ->
                        WorldRules.set(server, WorldRuleSpecs.ALLOW_3D_MODE, 1)), s -> expect3d(true)));
            }
        }
        return out;
    }

    static void hover(Script s, boolean inverted) {
        double y = comY();
        double vy = (y - s.lastY) * 20.0;
        s.lastY = y;
        double error = s.targetY - y;
        s.integral = Math.max(-0.3, Math.min(0.3, s.integral + error * 0.006));
        double magnitude = Math.max(0.0, Math.min(0.85, (inverted ? 0.32 : 0.22) + s.integral + error * 0.12 - vy * 0.1));
        double span = 0.5 - DEADBAND;
        float stick = (float) (inverted ? 0.5 - DEADBAND - magnitude * span : 0.5 + DEADBAND + magnitude * span);
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, stick), true, StabMode.ACRO);
    }

    private static @Nullable String expect3d(boolean active) {
        PilotSession session = PilotSession.INSTANCE;
        if (session.mode3dActive() != active) {
            return "3D " + (active ? "aus" : "an") + " (" + describe() + ")";
        }
        Telemetry t = telemetry();
        if (t != null && t.mode3d != active) {
            return "Flugmodell 3D " + t.mode3d;
        }
        OsdData osd = OsdHud.sampler().data();
        if (osd.active && osd.mode3d != active) {
            return "OSD 3D " + osd.mode3d;
        }
        boolean device = RadioState.INSTANCE.source() != RadioSource.KEYBOARD && RadioState.INSTANCE.connected();
        if (ModesTab.mode3dLit(device, RadioState.INSTANCE.activeSwitches()) != active) {
            return "Lampe im Modi-Tab " + !active;
        }
        return null;
    }

    private static @Nullable String toastIs(Component wanted) {
        ThemedToast toast = ThemedToast.find(Minecraft.getInstance().gui.toastManager(), PilotSession.TOAST_TOKEN);
        String text = toast == null ? "(keine)" : toast.title().getString();
        return wanted.getString().equals(text) ? null : "Meldung „" + text + "“ statt „" + wanted.getString() + "“";
    }

    static @Nullable Telemetry telemetry() {
        DroneSim sim = PilotSession.INSTANCE.sim();
        return sim == null ? null : sim.telemetry();
    }

    static double comY() {
        return PilotSession.INSTANCE.comPosition(new Vector3d()).y;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static void press(KeyMapping mapping) {
        KeyMapping.click(InputConstants.getKey(mapping.saveString()));
    }

    private static String describe() {
        PilotSession session = PilotSession.INSTANCE;
        RadioState radio = RadioState.INSTANCE;
        return "gewählt " + session.mode3dSelected() + " (" + session.mode3dSource() + "), Sperre " + session.mode3dBlock() + ", Quelle "
                + radio.source() + ", Schalter " + Integer.toBinaryString(radio.activeSwitches());
    }

    static SimulatedRadio radio(String name) {
        DeviceProfile profile = ProfileDefaults.radio("Propwash-Testfunke (3D)", AXES);
        profile.setBinding(SwitchFunction.MODE_3D, SwitchAssignment.axisMicros(MODE_3D_AXIS, 1700, 2100));
        SimulatedRadio radio = new SimulatedRadio(RADIO_GUID, name, profile, AXES, 0);
        radio.setMicros(THROTTLE_AXIS, 1500);
        for (int axis = 4; axis < AXES; axis++) {
            radio.setMicros(axis, 1000);
        }
        return radio;
    }

    private static StepResult prepare(SelfTest.StepContext ctx, Script s) {
        InputConfig config = InputConfig.get();
        s.keyboardFallback = config.keyboardFallback;
        s.unlocked = config.mode3dUnlocked;
        s.suppressDevice = TransmitterInput.INSTANCE.suppressDevice();
        config.mode3dUnlocked = s.kind != Kind.LOCKED;
        if (s.kind == Kind.LOCKED || s.kind == Kind.RADIO) {
            SimulatedRadio radio = radio("Propwash-Testfunke (3D)");
            s.simulated = radio;
            TransmitterInput.INSTANCE.simulate(radio);
        } else if (s.kind == Kind.KEYBOARD || s.kind == Kind.RULE) {
            TransmitterInput.INSTANCE.simulate(null);
            TransmitterInput.INSTANCE.setSuppressDevice(true);
            config.keyboardFallback = true;
        }
        for (KeyMapping key : List.of(InputKeys.MODE_3D, InputKeys.ARM)) {
            if (key.isUnbound()) {
                return StepResult.fail("Taste nicht belegt: " + key.getName());
            }
        }
        PilotSession.INSTANCE.resetFlightModes();
        PilotSession.INSTANCE.clearTestInput();
        boolean inverted = s.kind == Kind.INVERTED;
        s.pending = ctx.onServer(server -> spawnAndFly(server, s, inverted, PilotMode.FPV));
        return StepResult.pending();
    }

    static void spawnAndFly(MinecraftServer server, Script s, boolean inverted, PilotMode mode) {
        Vec3 feet = firstPlayer(server).position();
        spawnAndFly(server, s, inverted, mode, new Vec3(feet.x + 1.5, feet.y, feet.z + 4.0), 0.0F);
    }

    static void spawnAndFly(MinecraftServer server, Script s, boolean inverted, PilotMode mode, Vec3 at, float facingYaw) {
        ServerPlayer player = firstPlayer(server);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        Quaternionf attitude = new Quaternionf().rotationY((float) Math.toRadians(-facingYaw) + (float) Math.PI);
        if (inverted) {
            attitude.rotateZ((float) Math.PI);
        }
        drone.setAttitude(attitude);
        drone.setBuild(DroneBuild.FREESTYLE);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        s.droneId = drone.getId();
        s.previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        s.previousHand = player.getMainHandItem().copy();
        if (mode == PilotMode.FPV) {
            ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
            if (goggles.isEmpty()) {
                throw new IllegalStateException("Kreativ-Brille nicht registriert");
            }
            goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.setItemSlot(EquipmentSlot.HEAD, goggles);
        } else {
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, mode);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("Start verweigert: " + result);
        }
    }

    static void cleanup(SelfTest.StepContext ctx, Script s) {
        if (s.cleaned) {
            return;
        }
        s.cleaned = true;
        InputKeys.drain(InputKeys.MODE_3D);
        InputKeys.drain(InputKeys.ARM);
        TransmitterInput.INSTANCE.simulate(null);
        PilotSession.INSTANCE.clearTestInput();
        PilotSession.INSTANCE.resetFlightModes();
        if (s.suppressDevice != null) {
            TransmitterInput.INSTANCE.setSuppressDevice(s.suppressDevice);
        }
        InputConfig config = InputConfig.get();
        if (s.keyboardFallback != null) {
            config.keyboardFallback = s.keyboardFallback;
        }
        if (s.unlocked != null) {
            config.mode3dUnlocked = s.unlocked;
        }
        ctx.onServer(server -> {
            if (s.kind == Kind.RULE) {
                WorldRules.set(server, WorldRuleSpecs.ALLOW_3D_MODE, 1);
            }
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = s.droneId >= 0 ? level.getEntity(s.droneId) : null;
                if (entity instanceof QuadcopterEntity drone) {
                    DronePiloting.release(drone);
                    drone.discard();
                }
            }
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (!players.isEmpty()) {
                ServerPlayer player = players.getFirst();
                if (s.previousHead != null) {
                    player.setItemSlot(EquipmentSlot.HEAD, s.previousHead);
                }
                if (s.previousHand != null) {
                    player.setItemSlot(EquipmentSlot.MAINHAND, s.previousHand);
                }
            }
        });
    }

    static ServerPlayer firstPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    record Phase(String name, Consumer<Script> enter, Function<Script, @Nullable String> check,
                 int stableTicks, int maxTicks) {
        static Phase of(String name, Consumer<Script> enter, Function<Script, @Nullable String> check) {
            return new Phase(name, enter, check, 8, 120);
        }

        Phase stable(int ticks) {
            return new Phase(this.name, this.enter, this.check, ticks, this.maxTicks);
        }

        Phase limit(int ticks) {
            return new Phase(this.name, this.enter, this.check, this.stableTicks, ticks);
        }
    }

    static final class Script {
        final Kind kind;
        final List<Phase> phases;
        int index = -2;
        int since;
        int stableSince = -1;
        int now;
        double startY;
        double targetY;
        double lastY;
        double integral;
        boolean flipped;
        @Nullable String lastProblem;
        SelfTest.@Nullable StepContext context;
        volatile int droneId = -1;
        volatile @Nullable ItemStack previousHead;
        volatile @Nullable ItemStack previousHand;
        @Nullable SimulatedRadio simulated;
        @Nullable Boolean keyboardFallback;
        @Nullable Boolean unlocked;
        @Nullable Boolean suppressDevice;
        @Nullable CompletableFuture<Void> pending;
        boolean cleaned;

        Script(Kind kind, List<Phase> phases) {
            this.kind = kind;
            this.phases = phases;
        }

        SimulatedRadio radio() {
            return Objects.requireNonNull(this.simulated, "keine simulierte Funke");
        }

        SelfTest.StepContext ctx() {
            return Objects.requireNonNull(this.context);
        }

        int ticks() {
            return this.now - this.since;
        }

        StepResult run(SelfTest.StepContext ctx) {
            this.context = ctx;
            this.now = ctx.tick();
            CompletableFuture<Void> waiting = this.pending;
            if (waiting != null) {
                if (waiting.isCompletedExceptionally()) {
                    return StepResult.fail(this.label() + ": Server-Aktion fehlgeschlagen: " + waiting.exceptionNow().getMessage());
                }
                if (!waiting.isDone()) {
                    return StepResult.pending();
                }
                this.pending = null;
            }
            if (this.index == -2) {
                this.index = -1;
                this.since = this.now;
                return prepare(ctx, this);
            }
            PilotSession session = PilotSession.INSTANCE;
            if (this.index == -1) {
                QuadcopterEntity drone = session.drone();
                if (drone == null || drone.getId() != this.droneId || !session.isFpv()) {
                    return this.now - this.since > 300 ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne") : StepResult.pending();
                }
                if (this.now - this.since < 20) {
                    return StepResult.pending();
                }
                this.lastY = comY();
                this.enter(0);
                return StepResult.pending();
            }
            if (this.index >= this.phases.size()) {
                return StepResult.pass();
            }
            if (!session.isActive()) {
                return StepResult.fail(this.label() + ": Sitzung verloren");
            }
            Phase phase = this.phases.get(this.index);
            String problem = phase.check().apply(this);
            this.lastProblem = problem;
            if (this.pending != null) {
                return StepResult.pending();
            }
            if (problem != null) {
                this.stableSince = -1;
                return this.ticks() > phase.maxTicks() ? StepResult.fail(this.label() + ": " + problem) : StepResult.pending();
            }
            if (this.stableSince < 0) {
                this.stableSince = this.now;
            }
            if (this.now - this.stableSince < phase.stableTicks()) {
                return StepResult.pending();
            }
            ctx.log(this.label() + " nach " + (this.stableSince - this.since) + " Ticks");
            this.enter(this.index + 1);
            return this.index >= this.phases.size() ? StepResult.pass() : StepResult.pending();
        }

        private void enter(int next) {
            this.index = next;
            this.since = this.now;
            this.stableSince = -1;
            if (next < this.phases.size()) {
                this.phases.get(next).enter().accept(this);
            }
        }

        private String label() {
            return this.kind + " " + (this.index >= 0 && this.index < this.phases.size() ? this.phases.get(this.index).name() : "Start");
        }
    }
}
