package de.lardtom.propwash.dev.client;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.SurfaceMaterials;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.rx.RxBlockEntity;
import de.lardtom.propwash.rx.RxLink;
import de.lardtom.propwash.rx.RxServer;
import de.lardtom.propwash.rx.RxSettings;
import de.lardtom.propwash.rx.RxSource;
import de.lardtom.propwash.rx.client.RxClient;
import de.lardtom.propwash.sim.SurfaceResponse;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class DrehweltChecks {
    private static final String FUTURE = "future";
    private static final int THROTTLE_AXIS = 2;
    private static final int AXES = 8;
    private static final int LAMPS = 14;
    private static final int PHASE_TICKS = 300;
    private static final int POLL = 5;
    private static final int STABLE_POLLS = 3;
    private static final String TAG = "propwash_drehwelt_probe";
    static final int VIEW_DISTANCE = 6;
    static final int SIMULATION_DISTANCE = 5;
    private static @Nullable SimulatedRadio radio;

    private DrehweltChecks() {
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }

    static StepResult options(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        AtomicReference<String> result = cast(state.computeIfAbsent("result", k -> new AtomicReference<String>()));
        if (!state.containsKey(FUTURE)) {
            if (ctx.server() == null) {
                return StepResult.fail("keine Drehwelt geöffnet");
            }
            state.put(FUTURE, ctx.onServer(server -> result.set(server.getPlayerList().getViewDistance() + "/" + server.getPlayerList().getSimulationDistance())));
            return StepResult.pending();
        }
        StepResult waiting = DrehweltSelfTests.future(ctx);
        if (waiting != null) {
            return waiting;
        }
        Options options = Minecraft.getInstance().options;
        GraphicsPreset preset = options.graphicsPreset().get();
        String client = options.renderDistance().get() + "/" + options.simulationDistance().get();
        String want = VIEW_DISTANCE + "/" + SIMULATION_DISTANCE;
        String seen = "Grafik-Voreinstellung " + preset.getSerializedName() + ", Sichtweite/Simulationsdistanz Client " + client + ", Server " + result.get();
        if (preset != GraphicsPreset.CUSTOM || !want.equals(client) || !want.equals(result.get())) {
            return StepResult.fail(seen + ", verlangt custom und " + want + " (prepareRuns)");
        }
        ctx.log(seen);
        return StepResult.pass();
    }

    private static void throttle(int micros) {
        SimulatedRadio current = radio;
        if (current == null) {
            current = new SimulatedRadio("propwash-drehwelt-rx", "Propwash-Testfunke (Drehwelt)", ProfileDefaults.radio("Propwash-Testfunke", AXES),
                    AXES, 0);
            for (int axis = 0; axis < AXES; axis++) {
                current.setMicros(axis, axis == THROTTLE_AXIS || axis >= ProfileDefaults.AUX_ARM ? 1000 : 1500);
            }
            radio = current;
        }
        current.setMicros(THROTTLE_AXIS, micros);
        TransmitterInput.INSTANCE.simulate(current);
    }

    private static void release() {
        TransmitterInput.INSTANCE.simulate(null);
        radio = null;
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static String bind(MinecraftServer server, DrehweltSelfTests.Plot plot, boolean on) {
        ServerPlayer player = player(server);
        DrehweltSelfTests.Site site = DrehweltSelfTests.locate(player, plot);
        BlockPos pos = site.at(6, 1, 16);
        if (!(site.level.getBlockEntity(pos) instanceof RxBlockEntity entity)) {
            return "Fehler: kein RX-Block bei " + pos.toShortString();
        }
        entity.apply(on ? new RxSettings(player.getUUID(), RxSource.GAS, -1.0F, 1.0F, RxLink.DEFAULT_RANGE) : RxSettings.EMPTY);
        RxServer.refreshDemand(player);
        return on ? "gebunden" : "frei";
    }

    private static int lit(@Nullable String gate) {
        int at = gate == null ? -1 : gate.indexOf("lampen=");
        if (at < 0) {
            return -1;
        }
        int end = at + 7;
        while (end < gate.length() && Character.isDigit(gate.charAt(end))) {
            end++;
        }
        return end == at + 7 ? -1 : Integer.parseInt(gate.substring(at + 7, end));
    }

    private static String gate(String rest) {
        int comma = rest.indexOf(',');
        return comma < 0 ? rest : rest.substring(0, comma);
    }

    private static String part(BlockState state) {
        if (state.isAir()) {
            return "Luft";
        }
        if (state.is(Blocks.MOVING_PISTON)) {
            return "bewegt";
        }
        if (state.is(Blocks.PISTON_HEAD)) {
            return "Kopf";
        }
        if (state.is(Blocks.SMOOTH_STONE)) {
            return "Stein";
        }
        if (state.is(Blocks.STICKY_PISTON)) {
            return state.getValue(PistonBaseBlock.EXTENDED) ? "ausgefahren" : "eingefahren";
        }
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }

    static String rest(MinecraftServer server, DrehweltSelfTests.Plot plot, AtomicBoolean ready) {
        DrehweltSelfTests.Site site = DrehweltSelfTests.locate(player(server), plot);
        ServerLevel level = site.level;
        BlockState torch = level.getBlockState(site.at(12, 1, 16));
        boolean burning = torch.is(Blocks.REDSTONE_WALL_TORCH) && torch.getValue(RedstoneTorchBlock.LIT);
        boolean ticking = RxServer.active(level, site.at(6, 1, 16)) && level.isPositionTickingWithEntitiesLoaded(ChunkPos.pack(site.at(12, 1, 16)));
        String now = DrehweltSelfTests.gateState(server, plot, null);
        boolean still = burning && ticking && lit(now) == 0;
        List<String> rows = new ArrayList<>();
        for (int y = 0; y <= 1; y++) {
            String piston = part(level.getBlockState(site.at(13, y, 16)));
            String head = part(level.getBlockState(site.at(14, y, 16)));
            String gap = part(level.getBlockState(site.at(15, y, 16)));
            still &= "ausgefahren".equals(piston) && "Kopf".equals(head) && "Stein".equals(gap);
            rows.add(piston + "/" + head + "/" + gap);
        }
        ready.set(still);
        return now + ", Fackel " + (burning ? "an" : "aus") + ", Kolben/vorn/Tor unten " + rows.get(0) + ", oben " + rows.get(1) + ", Platz tickt "
                + (ticking ? "ja" : "nein");
    }

    private static StepResult abort(SelfTest.StepContext ctx, DrehweltSelfTests.Plot plot, String reason) {
        release();
        ctx.onServer(server -> bind(server, plot, false));
        return StepResult.fail(reason);
    }

    static StepResult rxDrive(SelfTest.StepContext ctx, DrehweltSelfTests.Plot plot) {
        Map<String, Object> state = ctx.state();
        AtomicReference<String> result = cast(state.computeIfAbsent("result", k -> new AtomicReference<String>()));
        AtomicBoolean ready = cast(state.computeIfAbsent("ready", k -> new AtomicBoolean()));
        boolean fresh = false;
        if (state.containsKey(FUTURE)) {
            StepResult waiting = DrehweltSelfTests.future(ctx);
            if (waiting != null) {
                return waiting;
            }
            state.remove(FUTURE);
            fresh = true;
        }
        int phase = (int) state.getOrDefault("phase", 0);
        int since = (int) state.getOrDefault("since", 0);
        String now = result.get();
        if (phase == 0) {
            throttle(1000);
            state.put("phase", 1);
            state.put("since", ctx.tick());
            state.put(FUTURE, ctx.onServer(server -> {
                DrehweltSelfTests.nearGate(server, plot);
                result.set(bind(server, plot, true));
            }));
            return StepResult.pending();
        }
        if (now != null && now.startsWith("Fehler")) {
            return abort(ctx, plot, now);
        }
        if (phase == 5) {
            ctx.log("Tor nach " + state.get("settled") + " Ticks in Ruhe zu; Gas 100 %: " + state.get("full") + "; Gas 50 %: " + state.get("half")
                    + "; Gas 0 %: " + state.get("zero") + "; danach wieder ungebunden (" + now + ")");
            return StepResult.pass();
        }
        if (ctx.tick() - since > PHASE_TICKS) {
            String what = phase == 1 ? "Phase 1 (vor Gas 100 %): Tor nicht in Ruhe zu: "
                    : "Phase " + phase + " (Gas " + (phase == 2 ? 100 : phase == 3 ? 50 : 0) + " %): ";
            return abort(ctx, plot, what + now + ", Bedarf " + RxClient.demand().describe());
        }
        if (phase == 1) {
            if (fresh && ready.get() && !RxClient.demand().empty()) {
                state.put("settled", ctx.tick() - since);
                throttle(2000);
                next(state, ctx, 2, result);
                return StepResult.pending();
            }
        } else if (fresh && now != null) {
            boolean open = now.startsWith("offen");
            boolean closed = now.startsWith("zu");
            int lamps = lit(now);
            int stable = lamps == (int) state.get("last") ? (int) state.get("stable") + 1 : 0;
            state.put("stable", stable);
            state.put("last", lamps);
            switch (phase) {
                case 2 -> {
                    if (open && lamps == LAMPS) {
                        state.put("full", gate(now));
                        throttle(1500);
                        next(state, ctx, 3, result);
                    }
                }
                case 3 -> {
                    if (open && lamps > 0 && lamps < LAMPS && stable >= STABLE_POLLS) {
                        state.put("half", gate(now));
                        throttle(1000);
                        next(state, ctx, 4, result);
                    }
                }
                case 4 -> {
                    if (closed && lamps == 0 && ready.get()) {
                        state.put("zero", gate(now));
                        release();
                        state.put("phase", 5);
                        state.put(FUTURE, ctx.onServer(server -> result.set(bind(server, plot, false))));
                        return StepResult.pending();
                    }
                }
                default -> {
                    return abort(ctx, plot, "unbekannte Phase " + phase);
                }
            }
        }
        if (!state.containsKey(FUTURE) && ctx.tick() % POLL == 0) {
            state.put(FUTURE, ctx.onServer(server -> result.set(rest(server, plot, ready))));
        }
        return StepResult.pending();
    }

    private static void next(Map<String, Object> state, SelfTest.StepContext ctx, int phase, AtomicReference<String> result) {
        state.put("phase", phase);
        state.put("since", ctx.tick());
        state.put("stable", 0);
        state.put("last", -2);
        result.set(null);
    }

    static StepResult surface(SelfTest.StepContext ctx, DrehweltSelfTests.@Nullable Built built) {
        if (built == null || !built.complete()) {
            ctx.log("Slime-/Wolle-Parcours nicht gebaut (Strang surface fehlt), übersprungen");
            return StepResult.pass();
        }
        Map<String, Object> state = ctx.state();
        AtomicReference<String> result = cast(state.computeIfAbsent("result", k -> new AtomicReference<String>()));
        if (!state.containsKey(FUTURE)) {
            state.put(FUTURE, ctx.onServer(server -> result.set(surfaces(server, built.plot()))));
            return StepResult.pending();
        }
        StepResult waiting = DrehweltSelfTests.future(ctx);
        if (waiting != null) {
            return waiting;
        }
        String now = result.get();
        if (now == null || !now.startsWith("ok")) {
            return StepResult.fail(String.valueOf(now));
        }
        ctx.log(now.substring(3));
        return StepResult.pass();
    }

    private static String surfaces(MinecraftServer server, DrehweltSelfTests.Plot plot) {
        DrehweltSelfTests.Site site = DrehweltSelfTests.locate(player(server), plot);
        record Probe(String what, int u, int y, int v, SurfaceResponse want) {
        }
        List<Probe> probes = List.of(new Probe("Slime-Boden", 14, -1, 12, SurfaceResponse.BOUNCY),
                new Probe("Slime-Wand", 24, 3, 24, SurfaceResponse.BOUNCY),
                new Probe("Wolle am Start", 6, 0, 4, SurfaceResponse.CUSHION),
                new Probe("rote Wolle", 34, 0, 10, SurfaceResponse.CUSHION),
                new Probe("hellblaue Wolle", 42, 0, 22, SurfaceResponse.CUSHION),
                new Probe("Heuballen", 39, 1, 33, SurfaceResponse.CUSHION),
                new Probe("Wolle auf dem Heu", 39, 3, 33, SurfaceResponse.CUSHION));
        List<String> wrong = new ArrayList<>();
        for (Probe probe : probes) {
            BlockState block = site.level.getBlockState(site.at(probe.u, probe.y, probe.v));
            SurfaceResponse response = SurfaceMaterials.response(block);
            if (!probe.want.equals(response)) {
                wrong.add(probe.what + " (" + block.getBlock().getName().getString() + "): " + response);
            }
        }
        if (!wrong.isEmpty()) {
            return "Flächen falsch: " + wrong;
        }
        return "ok " + probes.size() + " Flächen: Slime federt mit Rückprall " + SurfaceResponse.BOUNCY.restitution() + ", Wolle und Heu fangen ab mit "
                + SurfaceResponse.CUSHION.restitution();
    }

    static StepResult triggers(SelfTest.StepContext ctx, DrehweltSelfTests.@Nullable Built built) {
        if (built == null || !built.complete()) {
            ctx.log("Platte und Stolperdraht nicht gebaut (Strang block-triggers fehlt), übersprungen");
            return StepResult.pass();
        }
        DrehweltSelfTests.Plot plot = built.plot();
        Map<String, Object> state = ctx.state();
        AtomicReference<String> result = cast(state.computeIfAbsent("result", k -> new AtomicReference<String>()));
        if (state.containsKey(FUTURE)) {
            StepResult waiting = DrehweltSelfTests.future(ctx);
            if (waiting != null) {
                return waiting;
            }
            state.remove(FUTURE);
        }
        int phase = (int) state.getOrDefault("phase", 0);
        int since = (int) state.getOrDefault("since", 0);
        String now = result.get();
        if (phase == 0) {
            state.put("phase", 1);
            state.put("since", ctx.tick());
            state.put(FUTURE, ctx.onServer(server -> result.set(spawn(server, plot, 16, 0, 15, 0.05))));
            return StepResult.pending();
        }
        if (ctx.tick() - since > PHASE_TICKS) {
            ctx.onServer(server -> discard(server, plot));
            return StepResult.fail("Phase " + phase + ": " + now);
        }
        if (now != null && now.startsWith("Fehler")) {
            ctx.onServer(server -> discard(server, plot));
            return StepResult.fail(now);
        }
        if (phase == 6) {
            ctx.log("Holzplatte: " + state.get("plate") + "; Stolperdraht: " + state.get("wire") + "; danach Tür zu, Lampe aus");
            return StepResult.pass();
        }
        if (ctx.tick() % POLL == 0) {
            state.put(FUTURE, ctx.onServer(server -> result.set(redstone(server, plot))));
            return StepResult.pending();
        }
        boolean door = now != null && now.contains("tür=offen");
        boolean lamp = now != null && now.contains("lampe=an");
        boolean hook = now != null && now.contains("haken=an");
        switch (phase) {
            case 1 -> {
                if (door) {
                    state.put("plate", "Drohne auf der Platte, Tür offen");
                    state.put("phase", 2);
                    state.put("since", ctx.tick());
                    state.put(FUTURE, ctx.onServer(server -> {
                        discard(server, plot);
                        result.set(spawn(server, plot, 16, 0, 8, 0.0));
                    }));
                }
            }
            case 2 -> {
                if (lamp && hook) {
                    state.put("wire", "Drohne im Draht, Haken und Lampe an");
                    state.put("phase", 3);
                    state.put("since", ctx.tick());
                    state.put(FUTURE, ctx.onServer(server -> {
                        discard(server, plot);
                        result.set(redstone(server, plot));
                    }));
                }
            }
            case 3 -> {
                if (now != null && now.contains("tür=zu") && now.contains("lampe=aus")) {
                    state.put("phase", 6);
                }
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
        return StepResult.pending();
    }

    private static String spawn(MinecraftServer server, DrehweltSelfTests.Plot plot, int u, int y, int v, double lift) {
        ServerPlayer player = player(server);
        DrehweltSelfTests.Site site = DrehweltSelfTests.locate(player, plot);
        BlockPos stand = site.at(16, 0, -3);
        player.teleportTo(site.level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, Set.of(), 0.0F, 10.0F, true);
        BlockPos pos = site.at(u, y, v);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(site.level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            return "Fehler: Drohne nicht erzeugt";
        }
        drone.snapTo(pos.getX() + 0.5, pos.getY() + lift, pos.getZ() + 0.5, 0.0F, 0.0F);
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.addTag(TAG);
        if (!site.level.addFreshEntity(drone)) {
            return "Fehler: Drohne nicht gespawnt";
        }
        return "Drohne bei " + pos.toShortString();
    }

    private static void discard(MinecraftServer server, DrehweltSelfTests.Plot plot) {
        DrehweltSelfTests.Site site = DrehweltSelfTests.locate(player(server), plot);
        AABB area = new AABB(Vec3.atCenterOf(site.at(0, -2, 0)), Vec3.atCenterOf(site.at(plot.width() - 1, 8, plot.depth() - 1))).inflate(2.0);
        for (QuadcopterEntity drone : site.level.getEntitiesOfClass(QuadcopterEntity.class, area, d -> d.entityTags().contains(TAG))) {
            drone.discard();
        }
    }

    private static String redstone(MinecraftServer server, DrehweltSelfTests.Plot plot) {
        DrehweltSelfTests.Site site = DrehweltSelfTests.locate(player(server), plot);
        ServerLevel level = site.level;
        BlockState door = level.getBlockState(site.at(16, 0, 16));
        BlockState lamp = level.getBlockState(site.at(28, 1, 8));
        BlockState hook = level.getBlockState(site.at(5, 0, 8));
        boolean open = door.getBlock() instanceof DoorBlock && door.getValue(DoorBlock.OPEN);
        boolean lit = lamp.getBlock() instanceof RedstoneLampBlock && lamp.getValue(RedstoneLampBlock.LIT);
        boolean powered = hook.getBlock() instanceof TripWireHookBlock && hook.getValue(TripWireHookBlock.POWERED);
        boolean attached = hook.getBlock() instanceof TripWireHookBlock && hook.getValue(TripWireHookBlock.ATTACHED);
        if (!attached) {
            return "Fehler: Stolperdraht nicht gespannt (" + hook + ")";
        }
        return "tür=" + (open ? "offen" : "zu") + " lampe=" + (lit ? "an" : "aus") + " haken=" + (powered ? "an" : "aus");
    }

    static StepResult heat(SelfTest.StepContext ctx, DrehweltSelfTests.@Nullable Built built) {
        if (built == null || !built.complete()) {
            return StepResult.fail("Hitze-Strecke nicht gebaut");
        }
        Map<String, Object> state = ctx.state();
        AtomicReference<String> result = cast(state.computeIfAbsent("result", k -> new AtomicReference<String>()));
        if (!state.containsKey(FUTURE)) {
            state.put(FUTURE, ctx.onServer(server -> {
                DrehweltSelfTests.Site site = DrehweltSelfTests.locate(player(server), built.plot());
                AABB area = new AABB(Vec3.atCenterOf(site.at(0, -1, 0)), Vec3.atCenterOf(site.at(built.plot().width() - 1, 4, built.plot().depth() - 1)))
                        .inflate(1.0);
                int lipo = 0;
                int liIon = 0;
                for (QuadcopterEntity drone : site.level.getEntitiesOfClass(QuadcopterEntity.class, area)) {
                    DroneBuild build = drone.build();
                    if (build.frame().equals(DroneBuild.LONG_RANGE.frame()) && build.battery().equals(BatteryType.B6S_1300)) {
                        lipo++;
                    } else if (build.equals(DroneBuild.LONG_RANGE)) {
                        liIon++;
                    }
                }
                result.set(lipo == 1 && liIon == 1 ? "ok 7 Zoll mit LiPo 6S 1300 am Start (" + DrehweltSelfTests.HOT_SEVEN.motor().key()
                        + ", wird bei Dauervollgas heiß), 7 Zoll mit Li-Ion daneben" : "7 Zoll LiPo " + lipo + ", Li-Ion " + liIon);
            }));
            return StepResult.pending();
        }
        StepResult waiting = DrehweltSelfTests.future(ctx);
        if (waiting != null) {
            return waiting;
        }
        String now = result.get();
        if (now == null || !now.startsWith("ok")) {
            return StepResult.fail(String.valueOf(now));
        }
        ctx.log(now.substring(3));
        return StepResult.pass();
    }
}
