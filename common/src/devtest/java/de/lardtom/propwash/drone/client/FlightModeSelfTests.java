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
import de.lardtom.propwash.input.FlightModeSelector;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.client.OsdHud;
import de.lardtom.propwash.sim.StabMode;
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
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

final class FlightModeSelfTests {
    private static final String RADIO_GUID = "propwash-selftest-mode-radio";
    private static final int AXES = 8;
    private static final int MODE_AXIS = ProfileDefaults.AUX_MODE;
    private static final int THROTTLE_AXIS = 2;
    private static final int SETTLE_TICKS = 40;
    private static final int STABLE_TICKS = 10;
    private static final int HOLD_TICKS = 16;
    private static final int MAX_TICKS = 1500;

    private FlightModeSelfTests() {
    }

    static void register() {
        SelfTest.clientStep("drone: Flugmodus mit Funke – Schalter aus heißt Acro, Modus-Tasten und R wirken trotz Schalter", MAX_TICKS + 100,
                ctx -> step(ctx, true));
        SelfTest.clientStep("drone: Flugmodus nur mit Tastatur – Start in Angle, R schaltet genau einen Schritt weiter", MAX_TICKS + 100,
                ctx -> step(ctx, false));
    }

    private static StepResult step(SelfTest.StepContext ctx, boolean radio) {
        Scenario s = (Scenario) ctx.state().computeIfAbsent("flight_mode", key -> new Scenario(radio));
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
                return StepResult.fail(s.stage + ": Server-Aktion fehlgeschlagen: " + s.pending.exceptionNow().getMessage());
            }
            if (!s.pending.isDone()) {
                return StepResult.pending();
            }
            s.pending = null;
        }
        if (ctx.tick() > MAX_TICKS) {
            return StepResult.fail("Zeit abgelaufen in " + s.stage + " (" + describe() + ")");
        }
        PilotSession session = PilotSession.INSTANCE;
        int waited = ctx.tick() - s.since;
        return switch (s.stage) {
            case PREPARE -> prepare(ctx, s);
            case SOURCE -> {
                boolean ready = s.radio ? RADIO_GUID.equals(RadioState.INSTANCE.deviceGuid()) : RadioState.INSTANCE.source() == RadioSource.KEYBOARD;
                if (!ready) {
                    yield waited > 60 ? StepResult.fail("Eingabequelle stellt sich nicht ein: " + describe()) : StepResult.pending();
                }
                s.pending = ctx.onServer(server -> spawnAndFly(server, s));
                s.enter(Stage.SESSION, ctx.tick());
                yield StepResult.pending();
            }
            case SESSION -> {
                QuadcopterEntity drone = session.drone();
                if (drone == null || drone.getId() != s.droneId || !session.isFpv()) {
                    yield waited > 200 ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne") : StepResult.pending();
                }
                session.clearTestInput();
                s.enter(Stage.START, ctx.tick());
                yield StepResult.pending();
            }
            case START -> expect(ctx, s, s.radio ? StabMode.ACRO : StabMode.ANGLE,
                    s.radio ? "Start mit Modusschalter unten" : "Start mit der Tastatur (Start in Angle)", () -> {
                        if (s.radio) {
                            Objects.requireNonNull(s.simulated).setMicros(MODE_AXIS, 1500);
                            s.enter(Stage.PARKED, ctx.tick());
                        } else {
                            press(InputKeys.FLIGHT_MODE);
                            s.enter(Stage.CYCLE_ONE, ctx.tick());
                        }
                    });
            case PARKED -> expect(ctx, s, StabMode.HORIZON, "Kanal 6 in der Mitte (Horizon-Bereich der Vorbelegung)", () -> {
                press(InputKeys.MODE_ACRO);
                s.enter(Stage.ACRO_KEY, ctx.tick());
            });
            case ACRO_KEY -> expect(ctx, s, StabMode.ACRO, "Acro-Taste, Schalter weiter auf Horizon", () -> {
                    String toast = toastTitle();
                    String wanted = Component.translatable("propwash.drone.mode.changed_switch", PilotSession.modeName(StabMode.ACRO),
                            PilotSession.modeName(StabMode.HORIZON)).getString();
                    if (!wanted.equals(toast)) {
                        s.failure = "Meldung nach der Acro-Taste: „" + toast + "“ statt „" + wanted + "“";
                        return;
                    }
                    if (session.flightModeSource() != FlightModeSelector.Source.KEY || !session.keyOverridesModeSwitch()) {
                        s.failure = "Quelle des Flugmodus ist " + session.flightModeSource() + " statt Taste gegen den Schalter";
                        return;
                    }
                    press(InputKeys.FLIGHT_MODE);
                    s.enter(Stage.CYCLE_ONE, ctx.tick());
                });
            case CYCLE_ONE -> expect(ctx, s, s.radio ? StabMode.ANGLE : StabMode.HORIZON, "R einmal gedrückt", () -> {
                if (s.radio) {
                    s.enter(Stage.HOLD, ctx.tick());
                } else {
                    press(InputKeys.FLIGHT_MODE);
                    s.enter(Stage.CYCLE_TWO, ctx.tick());
                }
            });
            case CYCLE_TWO -> expect(ctx, s, StabMode.ACRO, "R ein zweites Mal gedrückt", () -> s.enter(Stage.HOLD, ctx.tick()));
            case HOLD -> {
                KeyMapping cycle = InputKeys.FLIGHT_MODE;
                if (cycle.isUnbound()) {
                    yield StepResult.fail("Taste „Flugmodus wechseln“ ist nicht belegt");
                }
                InputConstants.Key key = InputConstants.getKey(cycle.saveString());
                if (!s.holding) {
                    s.beforeHold = session.flightMode();
                    s.holding = true;
                    s.holdTick = ctx.tick();
                    KeyMapping.set(key, true);
                    KeyMapping.click(key);
                } else if (ctx.tick() - s.holdTick < HOLD_TICKS) {
                    KeyMapping.click(key);
                } else {
                    KeyMapping.set(key, false);
                    s.holding = false;
                    s.enter(Stage.HELD, ctx.tick());
                }
                yield StepResult.pending();
            }
            case HELD -> expect(ctx, s, FlightModeSelector.next(s.beforeHold), "R " + HOLD_TICKS + " Ticks gehalten (mit Tastenwiederholung)", () -> {
                if (s.radio) {
                    Objects.requireNonNull(s.simulated).setMicros(MODE_AXIS, 2000);
                    s.enter(Stage.SWITCH_HIGH, ctx.tick());
                } else {
                    press(InputKeys.MODE_HORIZON);
                    s.enter(Stage.HORIZON_KEY, ctx.tick());
                }
            });
            case SWITCH_HIGH -> expect(ctx, s, StabMode.ANGLE, "Modusschalter oben übernimmt wieder", () -> {
                if (session.keyOverridesModeSwitch() || session.flightModeSource() != FlightModeSelector.Source.SWITCH) {
                    s.failure = "nach dem Umlegen gilt noch die Taste: " + session.flightModeSource();
                    return;
                }
                Objects.requireNonNull(s.simulated).setMicros(MODE_AXIS, 1000);
                s.enter(Stage.SWITCH_LOW, ctx.tick());
            });
            case SWITCH_LOW -> expect(ctx, s, StabMode.ACRO, "Angle und Horizon aus", () -> s.enter(Stage.DONE, ctx.tick()));
            case HORIZON_KEY -> expect(ctx, s, StabMode.HORIZON, "Horizon-Taste", () -> s.enter(Stage.DONE, ctx.tick()));
            case DONE -> {
                ctx.log(s.radio
                        ? "Funke: Start Acro, Kanal 6 Mitte Horizon, Acro-Taste gegen den Schalter, R, gehaltenes R ein Schritt, "
                                + "Schalter oben Angle, Schalter unten Acro"
                        : "Tastatur: Start Angle, R zweimal, gehaltenes R ein Schritt, Horizon-Taste");
                yield StepResult.pass();
            }
        };
    }

    private static StepResult prepare(SelfTest.StepContext ctx, Scenario s) {
        InputConfig config = InputConfig.get();
        s.keyboardFallback = config.keyboardFallback;
        s.startInAngle = config.keyboard.startInAngle;
        s.suppressDevice = TransmitterInput.INSTANCE.suppressDevice();
        if (s.radio) {
            SimulatedRadio radio = new SimulatedRadio(RADIO_GUID, "Propwash-Testfunke (Modi)", ProfileDefaults.radio("Propwash-Testfunke", AXES), AXES, 0);
            radio.setMicros(THROTTLE_AXIS, 1000);
            for (int axis = 4; axis < AXES; axis++) {
                radio.setMicros(axis, 1000);
            }
            s.simulated = radio;
            TransmitterInput.INSTANCE.simulate(radio);
        } else {
            TransmitterInput.INSTANCE.simulate(null);
            TransmitterInput.INSTANCE.setSuppressDevice(true);
            config.keyboardFallback = true;
            config.keyboard.startInAngle = true;
        }
        for (KeyMapping key : List.of(InputKeys.FLIGHT_MODE, InputKeys.MODE_ACRO, InputKeys.MODE_HORIZON)) {
            if (key.isUnbound()) {
                return StepResult.fail("Taste nicht belegt: " + key.getName());
            }
        }
        PilotSession.INSTANCE.resetFlightModes();
        s.enter(Stage.SOURCE, ctx.tick());
        return StepResult.pending();
    }

    private static StepResult expect(SelfTest.StepContext ctx, Scenario s, StabMode wanted, String what, Runnable next) {
        PilotSession session = PilotSession.INSTANCE;
        if (s.failure != null) {
            return StepResult.fail(s.failure);
        }
        if (!session.isActive() || session.drone() == null) {
            return StepResult.fail(what + ": Sitzung verloren");
        }
        int waited = ctx.tick() - s.since;
        String problem = problem(wanted);
        if (problem != null) {
            s.stableSince = -1;
            return waited > SETTLE_TICKS ? StepResult.fail(what + ": " + problem + " (" + describe() + ")") : StepResult.pending();
        }
        if (s.stableSince < 0) {
            s.stableSince = ctx.tick();
        }
        if (ctx.tick() - s.stableSince < STABLE_TICKS) {
            return StepResult.pending();
        }
        ctx.log(what + ": " + wanted + " (Quelle " + session.flightModeSource() + ") nach " + (s.stableSince - s.since) + " Ticks, OSD und Modi-Tab gleich");
        s.stableSince = -1;
        next.run();
        return s.failure != null ? StepResult.fail(s.failure) : StepResult.pending();
    }

    private static @Nullable String problem(StabMode wanted) {
        PilotSession session = PilotSession.INSTANCE;
        StabMode flown = session.flightMode();
        if (flown != wanted) {
            return "Flugmodus " + flown + " statt " + wanted;
        }
        OsdData osd = OsdHud.sampler().data();
        if (osd.active && osd.mode != wanted) {
            return "OSD zeigt " + osd.mode + " statt " + wanted;
        }
        boolean device = RadioState.INSTANCE.source() != RadioSource.KEYBOARD && RadioState.INSTANCE.connected();
        int bits = RadioState.INSTANCE.activeSwitches();
        for (StabMode mode : StabMode.values()) {
            if (ModesTab.modeLit(mode, device, bits) != (mode == wanted)) {
                return "Modi-Tab: Lampe " + mode + " ist " + (mode == wanted ? "aus" : "an");
            }
        }
        return null;
    }

    private static void press(KeyMapping mapping) {
        KeyMapping.click(InputConstants.getKey(mapping.saveString()));
    }

    private static String toastTitle() {
        ThemedToast toast = ThemedToast.find(Minecraft.getInstance().gui.toastManager(), PilotSession.TOAST_TOKEN);
        return toast == null ? "(keine)" : toast.title().getString();
    }

    private static String describe() {
        RadioState radio = RadioState.INSTANCE;
        PilotSession session = PilotSession.INSTANCE;
        return "Quelle " + radio.source() + " " + radio.deviceGuid() + ", Schalter " + Integer.toBinaryString(radio.activeSwitches())
                + ", Sitzung " + session.isActive() + ", Modus " + session.flightMode() + " (" + session.flightModeSource() + ")";
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
        drone.setBuild(DroneBuild.FREESTYLE);
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
    }

    private static void cleanup(SelfTest.StepContext ctx, Scenario s) {
        if (s.cleaned) {
            return;
        }
        s.cleaned = true;
        if (s.holding && !InputKeys.FLIGHT_MODE.isUnbound()) {
            KeyMapping.set(InputConstants.getKey(InputKeys.FLIGHT_MODE.saveString()), false);
        }
        InputKeys.drain(InputKeys.FLIGHT_MODE);
        InputKeys.drain(InputKeys.MODE_ACRO);
        InputKeys.drain(InputKeys.MODE_HORIZON);
        TransmitterInput.INSTANCE.simulate(null);
        PilotSession.INSTANCE.clearTestInput();
        if (s.suppressDevice != null) {
            TransmitterInput.INSTANCE.setSuppressDevice(s.suppressDevice);
        }
        if (s.keyboardFallback != null) {
            InputConfig.get().keyboardFallback = s.keyboardFallback;
        }
        if (s.startInAngle != null) {
            InputConfig.get().keyboard.startInAngle = s.startInAngle;
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
                if (s.previousHead != null) {
                    player.setItemSlot(EquipmentSlot.HEAD, s.previousHead);
                }
                if (s.previousHand != null) {
                    player.setItemSlot(EquipmentSlot.MAINHAND, s.previousHand);
                }
            }
        });
    }

    private static ServerPlayer firstPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private enum Stage {
        PREPARE,
        SOURCE,
        SESSION,
        START,
        PARKED,
        ACRO_KEY,
        CYCLE_ONE,
        CYCLE_TWO,
        HOLD,
        HELD,
        SWITCH_HIGH,
        SWITCH_LOW,
        HORIZON_KEY,
        DONE
    }

    private static final class Scenario {
        private final boolean radio;
        private Stage stage = Stage.PREPARE;
        private int since;
        private int stableSince = -1;
        private StabMode beforeHold = StabMode.ACRO;
        private boolean holding;
        private int holdTick;
        private @Nullable String failure;
        private volatile int droneId = -1;
        private volatile @Nullable ItemStack previousHead;
        private volatile @Nullable ItemStack previousHand;
        private @Nullable SimulatedRadio simulated;
        private @Nullable Boolean keyboardFallback;
        private @Nullable Boolean startInAngle;
        private @Nullable Boolean suppressDevice;
        private @Nullable CompletableFuture<Void> pending;
        private boolean cleaned;

        private Scenario(boolean radio) {
            this.radio = radio;
        }

        private void enter(Stage next, int tick) {
            this.stage = next;
            this.since = tick;
            this.stableSince = -1;
        }
    }
}
