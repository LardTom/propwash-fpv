package de.lardtom.propwash.drone.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.StabilizationMode;
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
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.addon.AddonModeNames;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.sim.DroneSim;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class AddonModeNameSelfTests {
    public static final String PROBE_ID = "propwash_example:mode_name_probe";
    public static final String NORMAL = "propwash_example.flight_mode.n";
    public static final String CINE = "propwash_example.flight_mode.c";
    public static final String SPORT = "propwash_example.flight_mode.s";
    private static final String RADIO_GUID = "propwash-selftest-mode-name-radio";
    private static final int AXES = 8;
    private static final int THROTTLE_AXIS = 2;
    private static final int SETTLE_TICKS = 60;
    private static final int MAX_TICKS = 800;
    private static final Probe PROBE = new Probe();
    private static volatile boolean probing;

    private AddonModeNameSelfTests() {
    }

    private static final class Probe implements FlightAssist {
        @Override
        public void update(FlightState state, AssistOutput output) {
        }

        @Override
        public Optional<String> modeNameKey(StabilizationMode mode) {
            return Optional.of(switch (mode) {
                case ANGLE -> NORMAL;
                case HORIZON -> CINE;
                case ACRO -> SPORT;
            });
        }
    }

    public static void register(String group) {
        PropwashApi.flight().registerAssist(PROBE_ID, profile -> probing ? PROBE : null);
        SelfTest.clientStep(group + "Meldung beim Moduswechsel nennt die Flugmodi der Flughilfe (N/C/S wie eine Kamera-Drohne), auch gegen den "
                + "Modusschalter", MAX_TICKS + 100, AddonModeNameSelfTests::step);
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        Scenario s = (Scenario) ctx.state().computeIfAbsent("addon_mode_names", key -> new Scenario());
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
            case PREPARE -> {
                for (KeyMapping key : List.of(InputKeys.MODE_ACRO, InputKeys.MODE_HORIZON)) {
                    if (key.isUnbound()) {
                        yield StepResult.fail("Taste nicht belegt: " + key.getName());
                    }
                }
                SimulatedRadio radio = new SimulatedRadio(RADIO_GUID, "Propwash-Testfunke (Modusnamen)", ProfileDefaults.radio("Propwash-Testfunke",
                        AXES), AXES, 0);
                for (int axis = THROTTLE_AXIS; axis < AXES; axis++) {
                    radio.setMicros(axis, 1000);
                }
                radio.setMicros(ProfileDefaults.AUX_MODE, 1500);
                TransmitterInput.INSTANCE.simulate(radio);
                session.resetFlightModes();
                probing = true;
                s.enter(Stage.SOURCE, ctx.tick());
                yield StepResult.pending();
            }
            case SOURCE -> {
                if (!RADIO_GUID.equals(RadioState.INSTANCE.deviceGuid())) {
                    yield waited > 60 ? StepResult.fail("Testfunke stellt sich nicht ein: " + describe()) : StepResult.pending();
                }
                s.pending = ctx.onServer(server -> spawnAndFly(server, s));
                s.enter(Stage.SESSION, ctx.tick());
                yield StepResult.pending();
            }
            case SESSION -> {
                QuadcopterEntity drone = session.drone();
                DroneSim sim = session.sim();
                if (drone == null || drone.getId() != s.droneId || !session.isFpv() || sim == null || sim.assist() != PROBE) {
                    yield waited > 200 ? StepResult.fail("keine FPV-Sitzung mit der Flughilfe der Probe: " + describe()) : StepResult.pending();
                }
                session.clearTestInput();
                s.enter(Stage.SWITCH, ctx.tick());
                yield StepResult.pending();
            }
            case SWITCH -> {
                if (session.flightMode() != StabMode.HORIZON) {
                    yield waited > SETTLE_TICKS ? StepResult.fail("Modusschalter in der Mitte ergibt nicht Horizon: " + describe()) : StepResult.pending();
                }
                press(InputKeys.MODE_ACRO);
                s.enter(Stage.ACRO_KEY, ctx.tick());
                yield StepResult.pending();
            }
            case ACRO_KEY -> {
                String wanted = Component.translatable(AddonModeNames.CHANGED_SWITCH, Component.translatable(SPORT), Component.translatable(CINE))
                        .getString();
                String toast = toastTitle();
                if (session.flightMode() != StabMode.ACRO || !wanted.equals(toast)) {
                    yield waited > SETTLE_TICKS ? StepResult.fail("Acro-Taste gegen den Schalter auf Horizon: Meldung „" + toast + "“ statt „" + wanted
                            + "“ (" + describe() + ")") : StepResult.pending();
                }
                String name = session.flightModeName(StabMode.ACRO).getString();
                String sport = Component.translatable(SPORT).getString();
                if (!sport.equals(name)) {
                    yield StepResult.fail("Name für den Modi-Tab „" + name + "“ statt „" + sport + "“");
                }
                ctx.log("Acro-Taste gegen den Schalter: „" + toast + "“, Modi-Tab „" + name + "“");
                press(InputKeys.MODE_HORIZON);
                s.enter(Stage.HORIZON_KEY, ctx.tick());
                yield StepResult.pending();
            }
            case HORIZON_KEY -> {
                String wanted = Component.translatable(AddonModeNames.CHANGED, Component.translatable(CINE)).getString();
                String toast = toastTitle();
                if (session.flightMode() != StabMode.HORIZON || !wanted.equals(toast)) {
                    yield waited > SETTLE_TICKS ? StepResult.fail("Horizon-Taste wie der Schalter: Meldung „" + toast + "“ statt „" + wanted + "“ ("
                            + describe() + ")") : StepResult.pending();
                }
                ctx.log("Horizon-Taste wie der Schalter: „" + toast + "“");
                yield StepResult.pass();
            }
        };
    }

    private static void press(KeyMapping mapping) {
        KeyMapping.click(InputConstants.getKey(mapping.saveString()));
    }

    private static String toastTitle() {
        ThemedToast toast = ThemedToast.find(Minecraft.getInstance().gui.toastManager(), PilotSession.TOAST_TOKEN);
        return toast == null ? "(keine)" : toast.title().getString();
    }

    private static String describe() {
        PilotSession session = PilotSession.INSTANCE;
        DroneSim sim = session.sim();
        return "Quelle " + RadioState.INSTANCE.source() + " " + RadioState.INSTANCE.deviceGuid() + ", Sitzung " + session.isActive() + ", Modus "
                + session.flightMode() + " (" + session.flightModeSource() + "), Flughilfe " + (sim == null ? "-" : sim.assist() == PROBE ? "Probe"
                : String.valueOf(sim.assist()));
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
        probing = false;
        InputKeys.drain(InputKeys.MODE_ACRO);
        InputKeys.drain(InputKeys.MODE_HORIZON);
        TransmitterInput.INSTANCE.simulate(null);
        PilotSession.INSTANCE.clearTestInput();
        PilotSession.INSTANCE.resetFlightModes();
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
        SWITCH,
        ACRO_KEY,
        HORIZON_KEY
    }

    private static final class Scenario {
        private Stage stage = Stage.PREPARE;
        private int since;
        private volatile int droneId = -1;
        private volatile @Nullable ItemStack previousHead;
        private volatile @Nullable ItemStack previousHand;
        private @Nullable CompletableFuture<Void> pending;
        private boolean cleaned;

        private void enter(Stage next, int tick) {
            this.stage = next;
            this.since = tick;
        }
    }
}
