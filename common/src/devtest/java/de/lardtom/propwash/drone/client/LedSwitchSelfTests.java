package de.lardtom.propwash.drone.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.AccessoryType;
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
import de.lardtom.propwash.drone.client.render.DroneRenderState;
import de.lardtom.propwash.drone.led.DroneLeds;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.SwitchSource;
import de.lardtom.propwash.input.SwitchSources;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.video.client.Headlight;
import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
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

import java.util.List;
import java.util.concurrent.CompletableFuture;

final class LedSwitchSelfTests {
    private static final String GUID = "propwash-selftest-led-radio";
    private static final int AXES = 10;
    private static final int BUTTONS = 8;
    private static final int LED_AXIS = 8;
    private static final int LED_BUTTON = 5;
    private static final int LOCAL_TICKS = 20;
    private static final int SERVER_TICKS = 100;
    private static final int PROBE_EVERY = 5;
    private static final int MAX_TICKS = 1200;

    private LedSwitchSelfTests() {
    }

    static void register() {
        SelfTest.clientStep("drone: LED-Schalter an der Funke schaltet Streifen, Scheinwerfer und Server-Zustand", MAX_TICKS + 100,
                LedSwitchSelfTests::step);
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        Scenario s = (Scenario) ctx.state().computeIfAbsent("led_switch", key -> new Scenario());
        try {
            StepResult result = run(ctx, s);
            if (result.status() != StepResult.Status.PENDING) {
                cleanup(ctx, s);
            }
            return result;
        } catch (RuntimeException exception) {
            cleanup(ctx, s);
            throw exception;
        }
    }

    private static StepResult run(SelfTest.StepContext ctx, Scenario s) {
        if (s.pending != null) {
            if (s.pending.isCompletedExceptionally()) {
                return StepResult.fail(s.phase + ": Server-Aktion fehlgeschlagen: " + s.pending.exceptionNow().getMessage());
            }
            if (!s.pending.isDone()) {
                return StepResult.pending();
            }
            s.pending = null;
        }
        if (ctx.tick() > MAX_TICKS) {
            return StepResult.fail("Zeit abgelaufen in Phase " + s.phase + " (" + describe(s) + ")");
        }
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = s.droneId >= 0 && mc.level != null && mc.level.getEntity(s.droneId) instanceof QuadcopterEntity found ? found : null;
        switch (s.phase) {
            case SPAWN -> {
                s.pending = ctx.onServer(server -> spawnAndFly(server, s));
                s.enter(Phase.SESSION, ctx.tick());
                return StepResult.pending();
            }
            case SESSION -> {
                if (drone == null || !session.isFpv() || session.drone() != drone) {
                    return ctx.tick() - s.since > 200 ? StepResult.fail("keine FPV-Sitzung mit der LED-Drohne") : StepResult.pending();
                }
                session.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                s.radio = radio();
                TransmitterInput.INSTANCE.simulate(s.radio);
                s.enter(Phase.ASSIGNED_ON, ctx.tick());
                return StepResult.pending();
            }
            case ASSIGNED_ON -> {
                return expect(ctx, s, drone, false, "Schalter unten", () -> {
                    s.radio.setMicros(LED_AXIS, 2000);
                    s.enter(Phase.SWITCH_OFF, ctx.tick());
                });
            }
            case SWITCH_OFF -> {
                return expect(ctx, s, drone, true, "Schalter oben", () -> {
                    s.radio.profile().binding(SwitchFunction.LEDS_OFF).invert = true;
                    s.enter(Phase.INVERTED_ON, ctx.tick());
                });
            }
            case INVERTED_ON -> {
                return expect(ctx, s, drone, false, "invertiert, Schalter oben", () -> {
                    s.radio.setMicros(LED_AXIS, 1000);
                    s.enter(Phase.INVERTED_OFF, ctx.tick());
                });
            }
            case INVERTED_OFF -> {
                return expect(ctx, s, drone, true, "invertiert, Schalter unten", () -> {
                    s.radio.profile().setBinding(SwitchFunction.LEDS_OFF, SwitchAssignment.toggleButton(LED_BUTTON));
                    s.enter(Phase.TOGGLE_IDLE, ctx.tick());
                });
            }
            case TOGGLE_IDLE -> {
                return expect(ctx, s, drone, false, "Taster als Umschalter, nicht gedrückt", () -> {
                    s.radio.setButton(LED_BUTTON, true);
                    s.enter(Phase.TOGGLE_PRESSED, ctx.tick());
                });
            }
            case TOGGLE_PRESSED -> {
                return expect(ctx, s, drone, true, "Taster gedrückt", () -> {
                    s.radio.setButton(LED_BUTTON, false);
                    s.enter(Phase.TOGGLE_RELEASED, ctx.tick());
                });
            }
            case TOGGLE_RELEASED -> {
                return expect(ctx, s, drone, true, "Taster losgelassen, bleibt eingerastet", () -> {
                    s.radio.setButton(LED_BUTTON, true);
                    s.enter(Phase.TOGGLE_AGAIN, ctx.tick());
                });
            }
            case TOGGLE_AGAIN -> {
                return expect(ctx, s, drone, false, "Taster erneut gedrückt", () -> {
                    s.radio.setButton(LED_BUTTON, false);
                    TransmitterInput.INSTANCE.simulate(null);
                    s.radio = null;
                    InputConfig config = InputConfig.get();
                    s.keyboardFallback = config.keyboardFallback;
                    config.keyboardFallback = true;
                    s.enter(Phase.KEYBOARD, ctx.tick());
                });
            }
            case KEYBOARD -> {
                RadioState state = RadioState.INSTANCE;
                if (!state.connected() || GUID.equals(state.deviceGuid()) || state.isActive(SwitchFunction.LEDS_OFF)) {
                    return ctx.tick() - s.since > 60 ? StepResult.fail("Tastatur übernimmt nicht nach der simulierten Funke: "
                            + state.source() + " " + state.deviceGuid()) : StepResult.pending();
                }
                if (state.source() != RadioSource.KEYBOARD) {
                    ctx.log("Echtes Gerät angeschlossen (" + state.source() + "), Taste wird über dieses Gerät geprüft");
                }
                if (InputKeys.LEDS.isUnbound()) {
                    return StepResult.fail("Taste „LEDs an/aus“ ist nicht belegt");
                }
                KeyMapping.click(InputConstants.getKey(InputKeys.LEDS.saveString()));
                s.enter(Phase.KEY_OFF, ctx.tick());
                return StepResult.pending();
            }
            case KEY_OFF -> {
                return expect(ctx, s, drone, true, "Taste gedrückt", () -> {
                    KeyMapping.click(InputConstants.getKey(InputKeys.LEDS.saveString()));
                    s.enter(Phase.KEY_ON, ctx.tick());
                });
            }
            case KEY_ON -> {
                return expect(ctx, s, drone, false, "Taste erneut gedrückt", () -> {
                    KeyMapping.click(InputConstants.getKey(InputKeys.LEDS.saveString()));
                    s.enter(Phase.KEY_OFF_BEFORE_RELEASE, ctx.tick());
                });
            }
            case KEY_OFF_BEFORE_RELEASE -> {
                return expect(ctx, s, drone, true, "vor dem Landen aus", () -> {
                    s.releasedOff = null;
                    s.pending = ctx.onServer(server -> {
                        QuadcopterEntity serverDrone = serverDrone(server, s);
                        DronePiloting.release(serverDrone);
                        s.releasedOff = serverDrone.ledsOff();
                    });
                    s.enter(Phase.RELEASED, ctx.tick());
                });
            }
            case RELEASED -> {
                if (Boolean.TRUE.equals(s.releasedOff)) {
                    return StepResult.fail("Server lässt die LEDs nach dem Ende des Flugs aus");
                }
                if (session.isActive() || drone == null) {
                    return ctx.tick() - s.since > 100 ? StepResult.fail("Sitzung endet nicht nach dem Lösen") : StepResult.pending();
                }
                if (drone.ledsOff() || DroneLeds.switchedOff(drone)) {
                    return ctx.tick() - s.since > 100 ? StepResult.fail("LEDs bleiben nach dem Flug auf dem Client aus") : StepResult.pending();
                }
                if ((TransmitterInput.INSTANCE.keySwitchBits() & (1 << SwitchFunction.LEDS_OFF.ordinal())) != 0) {
                    return ctx.tick() - s.since > 100 ? StepResult.fail("Tasten-Zustand „LEDs aus“ überlebt das Ende der Sitzung")
                            : StepResult.pending();
                }
                ctx.log("LED-Schalter: Funke (Bereich, invertiert, Umschalter), Taste und Server-Zustand geprüft");
                return StepResult.pass();
            }
            default -> {
                return StepResult.pass();
            }
        }
    }

    private static StepResult expect(SelfTest.StepContext ctx, Scenario s, @Nullable QuadcopterEntity drone, boolean off, String what, Runnable next) {
        PilotSession session = PilotSession.INSTANCE;
        if (drone == null || !session.isActive()) {
            return StepResult.fail(what + ": Sitzung oder Drohne verloren");
        }
        int waited = ctx.tick() - s.since;
        String local = localProblem(drone, off);
        if (local != null) {
            return waited > LOCAL_TICKS ? StepResult.fail(what + ": " + local) : StepResult.pending();
        }
        if (s.localTick < 0) {
            s.localTick = ctx.tick();
            s.osdFrames = OsdHud.renderedFrames();
        }
        String osd = osdProblem(s, off);
        if (osd != null) {
            return ctx.tick() - s.localTick > LOCAL_TICKS ? StepResult.fail(what + ": " + osd) : StepResult.pending();
        }
        Probe probe = s.probe;
        if (probe == null || (probe.done() && !probe.matches(off) && ctx.tick() - probe.tick >= PROBE_EVERY)) {
            if (probe != null && ctx.tick() - s.localTick > SERVER_TICKS) {
                return StepResult.fail(what + ": Server sieht LEDs " + (probe.ledsOff ? "aus" : "an") + " (Lichtstärke " + probe.luminance + ")");
            }
            Probe fresh = new Probe(ctx.tick());
            s.probe = fresh;
            ctx.onServer(server -> {
                QuadcopterEntity serverDrone = serverDrone(server, s);
                fresh.ledsOff = serverDrone.ledsOff();
                fresh.luminance = DroneLeds.luminance(serverDrone);
                fresh.finished = true;
            });
            return StepResult.pending();
        }
        if (!probe.done() || !probe.matches(off)) {
            return StepResult.pending();
        }
        ctx.log(what + ": LEDs " + (off ? "aus" : "an") + " nach " + (s.localTick - s.since) + " Ticks lokal, Server nach "
                + (probe.tick - s.since) + " Ticks");
        s.localTick = -1;
        s.probe = null;
        next.run();
        return StepResult.pending();
    }

    private static @Nullable String localProblem(QuadcopterEntity drone, boolean off) {
        if (RadioState.INSTANCE.isActive(SwitchFunction.LEDS_OFF) != off) {
            return "RadioState meldet LEDS_OFF=" + !off;
        }
        if (PilotSession.INSTANCE.ledsOff() != off || drone.ledsOff() != off) {
            return "Sitzung/Drohne zeigen LEDs " + (drone.ledsOff() ? "aus" : "an");
        }
        if (DroneLeds.input(drone, drone.tickCount / 20.0).powered() == off) {
            return "Streifen hat Strom=" + off;
        }
        EntityRenderState extracted = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(drone, 0.0F);
        if (!(extracted instanceof DroneRenderState state)) {
            return "Render-State ist " + extracted.getClass().getName();
        }
        if (state.stripLeds != AccessoryType.LED_STRIP.ledCount) {
            return "Render-State ohne LED-Streifen";
        }
        int lit = 0;
        for (int i = 0; i < state.stripLeds; i++) {
            if ((state.stripColor[i] >>> 24) != 0) {
                lit++;
            }
        }
        if (off ? lit != 0 : lit != state.stripLeds) {
            return lit + " von " + state.stripLeds + " LEDs leuchten";
        }
        if (!VideoState.INSTANCE.active()) {
            return "kein FPV-Bild für den Scheinwerfer";
        }
        Headlight light = Headlight.INSTANCE;
        if (!light.equipped()) {
            return "Scheinwerfer erkennt den Streifen nicht";
        }
        if (off && (light.active() || light.intensity() > 0.0F)) {
            return "Scheinwerfer leuchtet trotz LEDs aus (Stärke " + light.intensity() + ")";
        }
        if (!off && light.intensity() <= 0.0F) {
            return "Scheinwerfer bekommt kein Licht vom Streifen";
        }
        return null;
    }

    private static @Nullable String osdProblem(Scenario s, boolean off) {
        if (!OsdHud.config().slot(OsdElement.LEDS_OFF).enabled || OsdHud.renderedFrames() <= s.osdFrames + 1) {
            return null;
        }
        if (OsdHud.drewLastFrame(OsdElement.LEDS_OFF) != off) {
            return off ? "OSD zeigt „LED AUS“ nicht" : "OSD zeigt „LED AUS“ trotz leuchtender LEDs";
        }
        return null;
    }

    private static SimulatedRadio radio() {
        DeviceProfile profile = ProfileDefaults.radio("Propwash-Testfunke", AXES);
        profile.setBinding(SwitchFunction.LEDS_OFF, SwitchSources.apply(SwitchAssignment.none(), new SwitchSources.Choice(SwitchSource.AXIS, LED_AXIS),
                SwitchFunction.LEDS_OFF, false));
        SimulatedRadio radio = new SimulatedRadio(GUID, "Propwash-Testfunke", profile, AXES, BUTTONS);
        for (int axis = 4; axis < AXES; axis++) {
            radio.setMicros(axis, 1000);
        }
        return radio;
    }

    private static void spawnAndFly(MinecraftServer server, Scenario s) {
        ServerPlayer player = firstPlayer(server);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        Vec3 feet = player.position();
        drone.snapTo(feet.x, feet.y, feet.z + 3.0, 0.0F, 0.0F);
        drone.setAttitude(new Quaternionf().rotationY((float) Math.PI));
        drone.setBuild(DroneBuild.FREESTYLE.withAccessory(AccessoryType.LED_STRIP));
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        s.droneId = drone.getId();
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        s.previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        s.previousHand = player.getMainHandItem().copy();
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
        if (drone.ledsOff()) {
            throw new IllegalStateException("neue Drohne startet mit ausgeschalteten LEDs");
        }
    }

    private static void cleanup(SelfTest.StepContext ctx, Scenario s) {
        if (s.cleaned) {
            return;
        }
        s.cleaned = true;
        TransmitterInput.INSTANCE.simulate(null);
        PilotSession.INSTANCE.clearTestInput();
        if (s.keyboardFallback != null) {
            InputConfig.get().keyboardFallback = s.keyboardFallback;
        }
        ctx.onServer(server -> {
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
                player.setItemSlot(EquipmentSlot.HEAD, s.previousHead == null ? ItemStack.EMPTY : s.previousHead);
                player.setItemSlot(EquipmentSlot.MAINHAND, s.previousHand == null ? ItemStack.EMPTY : s.previousHand);
            }
        });
    }

    private static String describe(Scenario s) {
        Probe probe = s.probe;
        return "Funke " + RadioState.INSTANCE.source() + ", LEDS_OFF " + RadioState.INSTANCE.isActive(SwitchFunction.LEDS_OFF)
                + ", Sitzung " + PilotSession.INSTANCE.isActive() + ", Server " + (probe == null || !probe.done() ? "?" : probe.ledsOff);
    }

    private static ServerPlayer firstPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static QuadcopterEntity serverDrone(MinecraftServer server, Scenario s) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(s.droneId) instanceof QuadcopterEntity drone) {
                return drone;
            }
        }
        throw new IllegalStateException("LED-Drohne auf dem Server verschwunden");
    }

    private enum Phase {
        SPAWN,
        SESSION,
        ASSIGNED_ON,
        SWITCH_OFF,
        INVERTED_ON,
        INVERTED_OFF,
        TOGGLE_IDLE,
        TOGGLE_PRESSED,
        TOGGLE_RELEASED,
        TOGGLE_AGAIN,
        KEYBOARD,
        KEY_OFF,
        KEY_ON,
        KEY_OFF_BEFORE_RELEASE,
        RELEASED
    }

    private static final class Probe {
        private final int tick;
        private volatile boolean ledsOff;
        private volatile int luminance;
        private volatile boolean finished;

        private Probe(int tick) {
            this.tick = tick;
        }

        private boolean done() {
            return this.finished;
        }

        private boolean matches(boolean off) {
            return this.ledsOff == off && (off ? this.luminance == 0 : this.luminance > 0);
        }
    }

    private static final class Scenario {
        private Phase phase = Phase.SPAWN;
        private int since;
        private int localTick = -1;
        private int osdFrames;
        private volatile int droneId = -1;
        private volatile @Nullable ItemStack previousHead;
        private volatile @Nullable ItemStack previousHand;
        private volatile @Nullable Boolean releasedOff;
        private @Nullable SimulatedRadio radio;
        private @Nullable Boolean keyboardFallback;
        private @Nullable CompletableFuture<Void> pending;
        private @Nullable Probe probe;
        private boolean cleaned;

        private void enter(Phase next, int tick) {
            this.phase = next;
            this.since = tick;
            this.localTick = -1;
            this.probe = null;
        }
    }
}
