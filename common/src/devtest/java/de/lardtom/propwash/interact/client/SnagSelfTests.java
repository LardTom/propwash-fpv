package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.interact.DroneBells;
import de.lardtom.propwash.interact.SnagBlocks;
import de.lardtom.propwash.interact.SnagMaterial;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class SnagSelfTests {
    private static final String GROUP = "verfangen: ";
    private static final String BELLS = "glocke: ";
    private static final double AHEAD = 3.0;
    private static final int HALF = 2;
    private static final int HEIGHT = 4;
    private static final double HOLD_GAIN = 0.3;
    private static final double HOLD_DAMPING = 0.1;
    private static final double ABOVE = HEIGHT + 1.5;
    private static final double VINE_HOLD = 0.82;
    private static final String SUB = "snag_sub";
    private static final String SUB_MARK = "snag_sub_mark";
    private static final String CRASHES = "snag_crashes";
    private static final String TOUCHES = "snag_touches";
    private static final String CATCHES = "snag_catches";
    private static final String WEAR = "snag_wear";
    private static final String PARTICLES = "snag_particles";
    private static final String SENT = "snag_sent";
    private static final String BEFORE = "snag_before";
    private static final String AFTER = "snag_after";
    private static final String MAX_X = "snag_max_x";
    private static final String CONTACT = "snag_contact";
    private static final String GLOW_ASK = "snag_glow_ask";
    private static final String CLOSE_UP = "snag_close_up";
    private static final String CLOSE_UP_TICK = "snag_close_up_tick";
    private static final List<BlockPos> RUNG = new CopyOnWriteArrayList<>();
    private static final AtomicInteger RAIDER = new AtomicInteger(-1);
    private static final AtomicInteger STAT_BEFORE = new AtomicInteger();
    private static final AtomicBoolean GLOWING = new AtomicBoolean();
    private static final AtomicReference<@Nullable Difficulty> DIFFICULTY = new AtomicReference<>();

    private SnagSelfTests() {
    }

    public static void register() {
        DroneBells.RUNG.register((drone, pilot, pos) -> RUNG.add(pos.immutable()));
        SelfTest.clientStep(GROUP + "Block-Tags für Spinnennetz, Faden und Ranken geladen (Server und Client)", 100, SnagSelfTests::tagStep);
        if (Screenshots.enabled()) {
            SelfTest.clientStep(GROUP + "Fenster für Bildschirmfotos", 100, ctx -> {
                StepResult result = Screenshots.resizeWindow(ctx);
                if (result.status() == StepResult.Status.PASS) {
                    Screenshots.hideMarker(true);
                }
                return result;
            });
        }
        for (Kind kind : Kind.values()) {
            SelfTest.clientStep(kind.what, 1600, ctx -> flight(ctx, kind));
        }
        if (Screenshots.enabled()) {
            SelfTest.clientStep(BELLS + "Fenster zurück", 5, ctx -> {
                Screenshots.hideMarker(false);
                return Screenshots.restoreWindow(ctx);
            });
        }
    }

    private static StepResult tagStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put(InteractSelfTests.FUTURE, ctx.onServer(server -> InteractSelfTests.SERVER_REPORT.set(checkTags())));
            return StepResult.pending();
        }
        StepResult server = InteractSelfTests.await(ctx, "Block-Tags");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        String client;
        try {
            client = checkTags();
        } catch (IllegalStateException e) {
            return StepResult.fail("Client: " + e.getMessage());
        }
        ctx.log("Server: " + InteractSelfTests.SERVER_REPORT.get() + "; Client: " + client);
        return StepResult.pass();
    }

    private static String checkTags() {
        Map<BlockState, SnagMaterial> expected = Map.ofEntries(
                Map.entry(Blocks.COBWEB.defaultBlockState(), SnagMaterial.WEB),
                Map.entry(Blocks.TRIPWIRE.defaultBlockState(), SnagMaterial.STRING),
                Map.entry(Blocks.VINE.defaultBlockState(), SnagMaterial.FOLIAGE),
                Map.entry(Blocks.GLOW_LICHEN.defaultBlockState(), SnagMaterial.FOLIAGE),
                Map.entry(Blocks.WEEPING_VINES.defaultBlockState(), SnagMaterial.FOLIAGE),
                Map.entry(Blocks.WEEPING_VINES_PLANT.defaultBlockState(), SnagMaterial.FOLIAGE),
                Map.entry(Blocks.TWISTING_VINES.defaultBlockState(), SnagMaterial.FOLIAGE),
                Map.entry(Blocks.TWISTING_VINES_PLANT.defaultBlockState(), SnagMaterial.FOLIAGE),
                Map.entry(Blocks.CAVE_VINES.defaultBlockState(), SnagMaterial.FOLIAGE),
                Map.entry(Blocks.CAVE_VINES_PLANT.defaultBlockState(), SnagMaterial.FOLIAGE),
                Map.entry(Blocks.HANGING_ROOTS.defaultBlockState(), SnagMaterial.FOLIAGE),
                Map.entry(Blocks.PALE_HANGING_MOSS.defaultBlockState(), SnagMaterial.FOLIAGE));
        for (Map.Entry<BlockState, SnagMaterial> entry : expected.entrySet()) {
            if (SnagBlocks.material(entry.getKey()) != entry.getValue()) {
                throw new IllegalStateException(entry.getKey() + " ist " + SnagBlocks.material(entry.getKey()) + " statt " + entry.getValue());
            }
        }
        for (BlockState state : List.of(Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState(),
                Blocks.SCAFFOLDING.defaultBlockState(), Blocks.BELL.defaultBlockState(), Blocks.TRIPWIRE_HOOK.defaultBlockState(),
                Blocks.SWEET_BERRY_BUSH.defaultBlockState())) {
            if (SnagBlocks.material(state) != null) {
                throw new IllegalStateException("fälschlich verfangend: " + state);
            }
        }
        return expected.size() + " Blöcke richtig eingeordnet, Laub, Gerüst, Glocke, Haken und Beerenbusch nicht";
    }

    private static StepResult flight(SelfTest.StepContext ctx, Kind kind) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        switch (InteractSelfTests.phase(ctx)) {
            case 0 -> {
                if (ctx.tick() < 12) {
                    return StepResult.pending();
                }
                RUNG.clear();
                GLOWING.set(false);
                state.put(InteractSelfTests.FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = InteractSelfTests.player(server);
                    int[] arena = InteractSelfTests.arena(player);
                    build(server, player.level(), arena, kind);
                    STAT_BEFORE.set(player.getStats().getValue(Stats.CUSTOM.get(Stats.BELL_RING)));
                    InteractSelfTests.fly(player, kind.build, InteractSelfTests.start(arena));
                }));
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                return InteractSelfTests.started(ctx, kind.what, null);
            }
            case 2, 3 -> {
                return InteractSelfTests.climb(ctx, kind.what);
            }
            case 4 -> {
                InteractSelfTests.hover(session);
                if (!InteractSelfTests.settled(session, ctx)) {
                    return InteractSelfTests.since(ctx) > 240 ? StepResult.fail(kind.what + ": schwebt nicht ruhig") : StepResult.pending();
                }
                state.put(CRASHES, session.crashCount());
                state.put(TOUCHES, PilotSnags.INSTANCE.touchSlices());
                state.put(CATCHES, PilotSnags.INSTANCE.catches());
                state.put(WEAR, PilotSnags.INSTANCE.wearPackets());
                state.put(PARTICLES, SnagEffects.particles());
                state.put(SENT, PilotBells.INSTANCE.sent());
                state.put(SUB, 0);
                state.put(SUB_MARK, ctx.tick());
                InteractSelfTests.push(session.sim(), kind.speed, 0.0, 0.0);
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            case 5 -> {
                return switch (kind) {
                    case WEB -> web(ctx);
                    case VINES -> vines(ctx);
                    case BELL -> bell(ctx);
                    case BELL_SLOW -> slowBell(ctx);
                };
            }
            case 6 -> {
                hoverAfter(session, kind);
                StepResult server = InteractSelfTests.await(ctx, kind.what);
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                ctx.log(InteractSelfTests.SERVER_REPORT.get());
                InteractSelfTests.next(ctx);
                return StepResult.pending();
            }
            default -> {
                return InteractSelfTests.stop(ctx, server -> cleanup(server));
            }
        }
    }

    private static StepResult web(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        int[] arena = InteractSelfTests.ARENA.get();
        if (drone == null || sim == null || arena == null) {
            return StepResult.fail("Spinnennetz: Sitzung verloren");
        }
        int wx = wallX(arena);
        AABB box = drone.getBoundingBox();
        double speed = speed(sim);
        int sub = (int) state.get(SUB);
        int since = ctx.tick() - (int) state.get(SUB_MARK);
        state.merge(MAX_X, box.maxX, (a, b) -> Math.max((double) a, (double) b));
        switch (sub) {
            case 0 -> {
                InteractSelfTests.hover(session);
                boolean inWeb = box.maxX > wx && box.minX < wx + 1.0;
                if (since < 20 || !inWeb || speed > 0.5) {
                    return since > 160 ? StepResult.fail(String.format(Locale.ROOT, "Spinnennetz: nicht gefangen (x %.2f über der Netzkante, %.2f m/s)",
                            box.maxX - wx, speed)) : StepResult.pending();
                }
                int crashes = session.crashCount() - (int) state.get(CRASHES);
                double center = (box.minX + box.maxX) * 0.5 - wx;
                if (crashes != 0 || !sim.telemetry().armed) {
                    return StepResult.fail("Spinnennetz: Absturz gezählt (" + crashes + ") oder entschärft");
                }
                if (!(center > 0.0 && center < 1.0)) {
                    return StepResult.fail("Spinnennetz: Mitte nicht im Netz: " + InteractSelfTests.fmt(center));
                }
                ctx.log(String.format(Locale.ROOT, "gefangen: Mitte %.2f m tief im Netz, vorderste Kante höchstens %.2f m, %.2f m/s, Haltekraft %.1f m/s²",
                        center, (double) state.get(MAX_X) - wx, speed, PilotSnags.INSTANCE.peakHold()));
                sub(ctx, 1);
            }
            case 1 -> {
                InteractSelfTests.hover(session);
                boolean hinted = PilotSnags.INSTANCE.catches() > (int) state.get(CATCHES);
                boolean worn = PilotSnags.INSTANCE.wearPackets() > (int) state.get(WEAR);
                if (!hinted || !worn) {
                    return since > 120 ? StepResult.fail("Spinnennetz: Hinweis " + hinted + ", Verschleiß gesendet " + worn) : StepResult.pending();
                }
                sub(ctx, 2);
            }
            case 2 -> {
                InteractSelfTests.hover(session);
                StepResult shot = shot(ctx, "snag_cobweb", 2.4, 1.4);
                if (shot.status() != StepResult.Status.PASS) {
                    return shot;
                }
                sub(ctx, 3);
            }
            case 3 -> {
                session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 1.0F), true, StabMode.ANGLE);
                if (drone.getY() - arena[1] < HEIGHT + 0.3) {
                    return since > 300 ? StepResult.fail("Spinnennetz: kommt mit Vollgas nicht frei (Höhe "
                            + InteractSelfTests.fmt(drone.getY() - arena[1]) + ")") : StepResult.pending();
                }
                ctx.log(String.format(Locale.ROOT, "mit Vollgas frei nach %.2f s", since / 20.0));
                state.put(InteractSelfTests.FUTURE, ctx.onServer(server -> serverCheck(server, Kind.WEB)));
                InteractSelfTests.next(ctx);
            }
            default -> {
                return StepResult.fail("Spinnennetz: unbekannter Schritt " + sub);
            }
        }
        return StepResult.pending();
    }

    private static StepResult vines(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        int[] arena = InteractSelfTests.ARENA.get();
        if (drone == null || sim == null || arena == null) {
            return StepResult.fail("Ranken: Sitzung verloren");
        }
        int wx = wallX(arena);
        AABB box = drone.getBoundingBox();
        double speed = speed(sim);
        int sub = (int) state.get(SUB);
        int since = ctx.tick() - (int) state.get(SUB_MARK);
        switch (sub) {
            case 0 -> {
                InteractSelfTests.hover(session);
                if (!state.containsKey(BEFORE) && box.maxX > wx - 0.4) {
                    state.put(BEFORE, speed);
                }
                if (!(box.minX > wx + 0.9)) {
                    return since > 120 ? StepResult.fail("Ranken: nicht durchgeflogen (x " + InteractSelfTests.fmt(box.maxX - wx) + ")")
                            : StepResult.pending();
                }
                if (!state.containsKey(AFTER)) {
                    state.put(AFTER, speed);
                }
                double before = (double) state.getOrDefault(BEFORE, 0.0);
                double after = (double) state.get(AFTER);
                int touches = PilotSnags.INSTANCE.touchSlices() - (int) state.get(TOUCHES);
                int crashes = session.crashCount() - (int) state.get(CRASHES);
                if (touches <= 0 || crashes != 0) {
                    return StepResult.fail("Ranken: Berührungen " + touches + ", Abstürze " + crashes);
                }
                if (!(after < before * 0.995 && after > before * 0.5)) {
                    return StepResult.fail(String.format(Locale.ROOT, "Ranken: Tempo %.2f → %.2f m/s, erwartet leicht gebremst", before, after));
                }
                ctx.log(String.format(Locale.ROOT, "durch die Wirrranken: %.2f → %.2f m/s (−%.0f %%), %d Abschnitte mit Berührung, kein Absturz",
                        before, after, 100.0 * (1.0 - after / before), touches));
                InteractSelfTests.push(sim, 0.0, 0.0, 0.0);
                sub(ctx, 1);
            }
            case 1 -> {
                InteractSelfTests.hover(session);
                double center = (box.minX + box.maxX) * 0.5;
                if (since < 20) {
                    return StepResult.pending();
                }
                if (center > wx + VINE_HOLD) {
                    InteractSelfTests.push(sim, -1.0, 0.0, 0.0);
                    return since > 220 ? StepResult.fail("Ranken: kommt nicht zurück in den Vorhang") : StepResult.pending();
                }
                InteractSelfTests.push(sim, 0.0, 0.0, 0.0);
                state.put(PARTICLES, SnagEffects.particles());
                ctx.log(String.format(Locale.ROOT, "zurück in der Ranke: Mitte %.3f hinter der Vorderkante der Zelle", center - wx));
                sub(ctx, 2);
            }
            case 2 -> {
                InteractSelfTests.hover(session);
                double center = (box.minX + box.maxX) * 0.5;
                InteractSelfTests.push(sim, Math.max(-0.5, Math.min(0.5, 2.0 * (wx + VINE_HOLD - center))), 0.0, 0.0);
                int particles = SnagEffects.particles() - (int) state.get(PARTICLES);
                if (!state.containsKey(CONTACT)) {
                    if (particles < 6 || !PilotSnags.INSTANCE.touching()) {
                        Minecraft mc = Minecraft.getInstance();
                        boolean contact = mc.level != null && SnagEffects.contact(mc.level, box.inflate(SnagEffects.REACH)) != null;
                        return since > 100 ? StepResult.fail(String.format(Locale.ROOT,
                                "Ranken: keine Blätter an den Props (%d, Berührung %s, Kontakt %s, Mitte %.3f hinter der Zellkante, Höhe %.2f, Motor %.2f)",
                                particles, PilotSnags.INSTANCE.touching(), contact, center - wx, drone.getY() - arena[1], drone.motorOutput()))
                                : StepResult.pending();
                    }
                    state.put(CONTACT, particles);
                    ctx.log("Props in der Ranke: " + particles + " Blattstückchen, Klang " + SnagEffects.sounds());
                }
                StepResult shot = shot(ctx, "snag_vines", -2.2, 1.3);
                if (shot.status() != StepResult.Status.PASS) {
                    return shot;
                }
                state.put(InteractSelfTests.FUTURE, ctx.onServer(server -> serverCheck(server, Kind.VINES)));
                InteractSelfTests.next(ctx);
            }
            default -> {
                return StepResult.fail("Ranken: unbekannter Schritt " + sub);
            }
        }
        return StepResult.pending();
    }

    private static StepResult bell(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        int[] arena = InteractSelfTests.ARENA.get();
        if (arena == null) {
            return StepResult.fail("Glocke: Arena fehlt");
        }
        hoverBell(session);
        int sub = (int) state.get(SUB);
        int since = ctx.tick() - (int) state.get(SUB_MARK);
        BlockPos bell = bellPos(arena);
        switch (sub) {
            case 0 -> {
                if (RUNG.isEmpty()) {
                    return since > 140 ? StepResult.fail("Glocke: nicht geläutet (gesendet " + (PilotBells.INSTANCE.sent() - (int) state.get(SENT)) + ")")
                            : StepResult.pending();
                }
                if (!RUNG.getFirst().equals(bell) || PilotBells.INSTANCE.sent() <= (int) state.get(SENT)) {
                    return StepResult.fail("Glocke: falsche Glocke " + RUNG.getFirst().toShortString() + " statt " + bell.toShortString());
                }
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null || !(mc.level.getBlockEntity(bell) instanceof BellBlockEntity entity)
                        || !entity.shaking) {
                    return since > 160 ? StepResult.fail("Glocke: schwingt beim Client nicht") : StepResult.pending();
                }
                ctx.log("Glocke geläutet nach " + since + " Ticks, schwingt beim Client");
                sub(ctx, 1);
            }
            case 1 -> {
                StepResult shot = shotFrom(ctx, "drone_bell", bell.getX() - 3.5, bell.getZ() - 1.3);
                if (shot.status() != StepResult.Status.PASS) {
                    return shot;
                }
                sub(ctx, 2);
            }
            case 2 -> {
                if (!GLOWING.get()) {
                    if (!state.containsKey(GLOW_ASK) || ((CompletableFuture<?>) state.get(GLOW_ASK)).isDone()) {
                        state.put(GLOW_ASK, ctx.onServer(server -> {
                            Entity raider = InteractSelfTests.player(server).level().getEntity(RAIDER.get());
                            GLOWING.set(raider instanceof Pillager pillager && pillager.hasEffect(MobEffects.GLOWING));
                        }));
                    }
                    return since > 200 ? StepResult.fail("Glocke: Plünderer leuchtet nicht") : StepResult.pending();
                }
                ctx.log("Plünderer leuchtet " + since + " Ticks nach dem Bild");
                sub(ctx, 3);
            }
            case 3 -> {
                StepResult shot = shotFrom(ctx, "drone_bell_raider", bell.getX() - 7.0, bell.getZ() - 2.5);
                if (shot.status() != StepResult.Status.PASS) {
                    return shot;
                }
                state.put(InteractSelfTests.FUTURE, ctx.onServer(server -> serverCheck(server, Kind.BELL)));
                InteractSelfTests.next(ctx);
            }
            default -> {
                return StepResult.fail("Glocke: unbekannter Schritt " + sub);
            }
        }
        return StepResult.pending();
    }

    private static StepResult slowBell(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        Map<String, Object> state = ctx.state();
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        int[] arena = InteractSelfTests.ARENA.get();
        if (drone == null || sim == null || arena == null) {
            return StepResult.fail("Glocke langsam: Sitzung verloren");
        }
        hoverBell(session);
        double face = bellPos(arena).getX() + 0.25;
        AABB box = drone.getBoundingBox();
        int since = ctx.tick() - (int) state.get(SUB_MARK);
        if (!state.containsKey(CONTACT)) {
            if (box.maxX < face - 0.03) {
                InteractSelfTests.push(sim, Kind.BELL_SLOW.speed, 0.0, 0.0);
                return since > 300 ? StepResult.fail("Glocke langsam: erreicht die Glocke nicht") : StepResult.pending();
            }
            state.put(CONTACT, ctx.tick());
            return StepResult.pending();
        }
        InteractSelfTests.push(sim, Kind.BELL_SLOW.speed, 0.0, 0.0);
        if (ctx.tick() - (int) state.get(CONTACT) < 40) {
            return StepResult.pending();
        }
        InteractSelfTests.push(sim, 0.0, 0.0, 0.0);
        int sent = PilotBells.INSTANCE.sent() - (int) state.get(SENT);
        if (!RUNG.isEmpty() || sent != 0) {
            return StepResult.fail("Glocke langsam: geläutet (" + RUNG.size() + ", gesendet " + sent + ")");
        }
        ctx.log(String.format(Locale.ROOT, "2 s mit %.1f m/s an der Glocke, kein Läuten", Kind.BELL_SLOW.speed));
        state.put(InteractSelfTests.FUTURE, ctx.onServer(server -> serverCheck(server, Kind.BELL_SLOW)));
        InteractSelfTests.next(ctx);
        return StepResult.pending();
    }

    private static void serverCheck(MinecraftServer server, Kind kind) {
        ServerPlayer player = InteractSelfTests.player(server);
        ServerLevel level = player.level();
        int[] arena = InteractSelfTests.ARENA.get();
        if (arena == null) {
            throw new IllegalStateException("Arena fehlt");
        }
        switch (kind) {
            case WEB, VINES -> {
                SnagMaterial expected = kind == Kind.WEB ? SnagMaterial.WEB : SnagMaterial.FOLIAGE;
                int missing = 0;
                int wx = wallX(arena);
                for (int dz = -HALF; dz <= HALF; dz++) {
                    for (int dy = 0; dy < HEIGHT; dy++) {
                        if (SnagBlocks.material(level.getBlockState(new BlockPos(wx, arena[1] + dy, arena[2] + dz))) != expected) {
                            missing++;
                        }
                    }
                }
                if (missing != 0) {
                    throw new IllegalStateException(missing + " Blöcke fehlen am Server");
                }
                PartWear wear = InteractSelfTests.serverDrone(server).wear();
                float lost = 0.0F;
                for (int i = 0; i < PartWear.POSITIONS; i++) {
                    lost += PartWear.PRISTINE.prop(i) - wear.prop(i);
                }
                if (kind == Kind.WEB && !(lost > 0.0F)) {
                    throw new IllegalStateException("Props ohne Verschleiß nach dem Netz");
                }
                InteractSelfTests.SERVER_REPORT.set(String.format(Locale.ROOT, "Server: alle %d Blöcke da, Props zusammen %.1f %% abgenutzt",
                        (2 * HALF + 1) * HEIGHT, lost * 100.0F));
            }
            case BELL -> {
                int stat = player.getStats().getValue(Stats.CUSTOM.get(Stats.BELL_RING)) - STAT_BEFORE.get();
                if (stat < 1) {
                    throw new IllegalStateException("Statistik „Glocke geläutet“ nicht gezählt");
                }
                InteractSelfTests.SERVER_REPORT.set("Server: Glocke " + RUNG.size() + "× geläutet, Statistik +" + stat + ", Plünderer leuchtet");
            }
            case BELL_SLOW -> {
                if (level.getBlockEntity(bellPos(arena)) instanceof BellBlockEntity entity && entity.shaking) {
                    throw new IllegalStateException("Glocke schwingt am Server");
                }
                InteractSelfTests.SERVER_REPORT.set("Server: Glocke still");
            }
        }
    }

    private static void build(MinecraftServer server, ServerLevel level, int[] arena, Kind kind) {
        int wx = wallX(arena);
        int y0 = arena[1];
        int z0 = arena[2];
        switch (kind) {
            case WEB -> {
                for (int dz = -HALF; dz <= HALF; dz++) {
                    for (int dy = 0; dy < HEIGHT; dy++) {
                        level.setBlockAndUpdate(new BlockPos(wx, y0 + dy, z0 + dz), Blocks.COBWEB.defaultBlockState());
                    }
                }
            }
            case VINES -> {
                for (int dz = -HALF; dz <= HALF; dz++) {
                    for (int dy = 0; dy < HEIGHT; dy++) {
                        Block block = dy == HEIGHT - 1 ? Blocks.TWISTING_VINES : Blocks.TWISTING_VINES_PLANT;
                        level.setBlockAndUpdate(new BlockPos(wx, y0 + dy, z0 + dz), block.defaultBlockState());
                    }
                }
            }
            case BELL, BELL_SLOW -> {
                level.setBlockAndUpdate(bellPos(arena), Blocks.BELL.defaultBlockState().setValue(BellBlock.FACING, Direction.EAST)
                        .setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
                if (kind == Kind.BELL) {
                    if (level.getDifficulty() == Difficulty.PEACEFUL) {
                        DIFFICULTY.set(Difficulty.PEACEFUL);
                        server.setDifficulty(Difficulty.EASY, true);
                    }
                    Pillager pillager = EntityTypes.PILLAGER.create(level, EntitySpawnReason.COMMAND);
                    if (pillager == null) {
                        throw new IllegalStateException("Plünderer nicht erzeugt");
                    }
                    pillager.setNoAi(true);
                    pillager.setPersistenceRequired();
                    pillager.snapTo(wx + 6.5, y0, z0 + 3.5, 120.0F, 0.0F);
                    level.addFreshEntity(pillager);
                    RAIDER.set(pillager.getId());
                }
            }
        }
    }

    private static void cleanup(MinecraftServer server) {
        ServerPlayer player = InteractSelfTests.player(server);
        ServerLevel level = player.level();
        int[] arena = InteractSelfTests.ARENA.get();
        if (arena != null) {
            int wx = wallX(arena);
            for (int dz = -HALF - 1; dz <= HALF + 1; dz++) {
                for (int dy = 0; dy <= HEIGHT; dy++) {
                    level.setBlock(new BlockPos(wx, arena[1] + dy, arena[2] + dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        int raider = RAIDER.getAndSet(-1);
        if (raider >= 0 && level.getEntity(raider) != null) {
            level.getEntity(raider).discard();
        }
        Difficulty previous = DIFFICULTY.getAndSet(null);
        if (previous != null) {
            server.setDifficulty(previous, true);
        }
    }

    private static StepResult shot(SelfTest.StepContext ctx, String file, double back, double side) {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (drone == null) {
            return StepResult.fail(file + ": Sitzung verloren");
        }
        return shotFrom(ctx, file, drone.getX() - back, drone.getZ() - side);
    }

    private static StepResult shotFrom(SelfTest.StepContext ctx, String file, double x, double z) {
        if (!Screenshots.enabled()) {
            return StepResult.pass();
        }
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(CLOSE_UP)) {
            int[] arena = InteractSelfTests.ARENA.get();
            if (arena == null) {
                return StepResult.fail(file + ": Arena fehlt");
            }
            state.put(CLOSE_UP, ctx.onServer(server -> {
                ServerPlayer player = InteractSelfTests.player(server);
                player.teleportTo(player.level(), x, arena[1], z, Set.of(), -60.0F, 15.0F, true);
            }));
            state.put(CLOSE_UP_TICK, ctx.tick());
            return StepResult.pending();
        }
        CompletableFuture<?> moved = (CompletableFuture<?>) state.get(CLOSE_UP);
        if (moved.isCompletedExceptionally()) {
            return StepResult.fail(file + ": " + moved.exceptionNow().getMessage());
        }
        if (!moved.isDone() || ctx.tick() - (int) state.get(CLOSE_UP_TICK) < 6) {
            return StepResult.pending();
        }
        StepResult shot = InteractSelfTests.shot(ctx, file);
        if (shot.status() == StepResult.Status.PASS) {
            state.remove("fov");
            state.remove("shot");
            state.remove(CLOSE_UP);
            state.remove(CLOSE_UP_TICK);
        }
        return shot;
    }

    private static void sub(SelfTest.StepContext ctx, int next) {
        ctx.state().put(SUB, next);
        ctx.state().put(SUB_MARK, ctx.tick());
    }

    private static void hoverAfter(PilotSession session, Kind kind) {
        if (kind == Kind.WEB) {
            hoverAt(session, ABOVE);
        } else if (kind == Kind.BELL || kind == Kind.BELL_SLOW) {
            hoverBell(session);
        } else {
            InteractSelfTests.hover(session);
        }
    }

    private static void hoverBell(PilotSession session) {
        InteractSelfTests.hover(session);
    }

    private static void hoverAt(PilotSession session, double height) {
        DroneSim sim = session.sim();
        QuadcopterEntity drone = session.drone();
        int[] arena = InteractSelfTests.ARENA.get();
        if (sim == null || drone == null || arena == null) {
            return;
        }
        Vector3d velocity = new Vector3d();
        sim.getVelocity(velocity);
        double error = height - (drone.getY() - arena[1]);
        double throttle = sim.telemetry().hoverThrottleStick + HOLD_GAIN * error - HOLD_DAMPING * velocity.y;
        session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, (float) Math.max(0.0, Math.min(1.0, throttle))), true, StabMode.ANGLE);
    }

    private static double speed(DroneSim sim) {
        Vector3d velocity = new Vector3d();
        sim.getVelocity(velocity);
        return velocity.length();
    }

    private static int wallX(int[] arena) {
        return (int) Math.floor(InteractSelfTests.start(arena).x + AHEAD);
    }

    private static BlockPos bellPos(int[] arena) {
        return new BlockPos(wallX(arena), arena[1], arena[2]);
    }

    private enum Kind {
        WEB(GROUP + "5-Zoll fliegt mit 18 m/s in Spinnweben, bleibt hängen und kommt mit Vollgas frei", DroneBuild.FREESTYLE, 18.0),
        VINES(GROUP + "Wirrranken bremsen den 5-Zoll leicht, die Props reißen Blätter ab", DroneBuild.FREESTYLE, 12.0),
        BELL(BELLS + "Drohne läutet die Glocke, der Plünderer leuchtet", DroneBuild.WHOOP, 3.0),
        BELL_SLOW(BELLS + "Langsames Anstupsen läutet nicht", DroneBuild.WHOOP, 0.6);

        private final String what;
        private final DroneBuild build;
        private final double speed;

        Kind(String what, DroneBuild build, double speed) {
            this.what = what;
            this.build = build;
            this.speed = speed;
        }
    }
}
