package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.FreeformRegion;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GatePoles;
import de.lardtom.propwash.race.GateSize;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.server.CovLogCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class GatePushSelfTests {
    private static final int OFFSET_X = 80;
    private static final int OFFSET_Z = -90;
    private static final int[] BOUNDS = {-6, 48, -9, 9, 10};
    private static final Vec3 STAND = new Vec3(22.0, 0.0, -8.0);
    private static final double SAME = 1.0E-3;
    private static final int SETTLE = 8;
    private static final int WATCH = 70;
    private static final String GATE = "gate";
    private static final String FLAG = "flag";
    private static final String POLE = "pole";
    private static final String CUBE = "cube";
    private static final String GATE_POLE = "gate_pole";
    private static final String FREEFORM = "freeform";
    private static final String COURSE = "course";
    private static final String NAME = "Kolbenprobe";
    private static final List<String> ELEMENTS = List.of(FLAG, POLE, CUBE, GATE_POLE, FREEFORM);
    private static final List<String> ALL = List.of(GATE, FLAG, POLE, CUBE, GATE_POLE, FREEFORM);
    private static final AtomicReference<CovRaceSite.Site> SITE = new AtomicReference<>();
    private static final Map<String, Vec3> ORIGIN = new ConcurrentHashMap<>();
    private static final Map<String, BlockPos> MACHINE = new ConcurrentHashMap<>();
    private static final AtomicReference<String> REPORT = new AtomicReference<>("");
    private static final AtomicReference<@Nullable CovLogCapture> LOG = new AtomicReference<>();

    private GatePushSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("race-topo: Kolben – Aufbau: Gate, Flagge, Slalomstange, Tauchwürfel, Gate-Stange und Freiform-Gate in einem Kurs", 700,
                GatePushSelfTests::setupStep);
        SelfTest.clientStep("race-topo: Kolben – normaler Kolben von unten, klebriger Kolben mit Schleimblock und Kolben mit Honigblock: Gate bleibt", 300,
                GatePushSelfTests::gateStep);
        SelfTest.clientStep("race-topo: Kolben – Flagge, Slalomstange, Tauchwürfel, Gate-Stange und Freiform-Gate bleiben stehen", 300,
                GatePushSelfTests::elementStep);
        SelfTest.clientStep("race-topo: Kolben – Wasser, Lava, Explosion, Spieler, Schwein, Lore und Boot verschieben nichts", 300,
                GatePushSelfTests::forceStep);
        SelfTest.clientStep("race-topo: Kolben – vorab verschobenes Gate wird beim Laden auf die Kurslage gesetzt, Client sieht dieselbe Lage", 300,
                GatePushSelfTests::restoreStep);
        SelfTest.clientStep("race-topo: Kolben – aufräumen", 300, GatePushSelfTests::cleanupStep);
    }

    private static StepResult setupStep(SelfTest.StepContext ctx) {
        StepResult prepared = CovRaceSite.prepare(ctx, SITE, OFFSET_X, OFFSET_Z, BOUNDS, STAND);
        if (prepared.status() != StepResult.Status.PASS) {
            return prepared;
        }
        StepResult wait = CovRaceSite.once(ctx, "Aufbau", GatePushSelfTests::build);
        if (wait != null) {
            return wait;
        }
        CovRaceSite.Site site = SITE.get();
        for (String key : ALL) {
            if (CovRaceSite.clientEntity(site, key) == null) {
                return ctx.tick() > 650 ? StepResult.fail(key + " fehlt beim Client") : StepResult.pending();
            }
        }
        CourseData course = ClientCourses.get(site.id(COURSE));
        if (course == null || course.gates().size() != ALL.size()) {
            return ctx.tick() > 650 ? StepResult.fail("Kurs fehlt beim Client") : StepResult.pending();
        }
        ctx.log(REPORT.get());
        return StepResult.pass();
    }

    private static @Nullable String build(MinecraftServer server) {
        ServerPlayer player = CovRaceSite.player(server);
        ServerLevel level = player.level();
        CovRaceSite.Site site = SITE.get();
        ORIGIN.clear();
        MACHINE.clear();
        Map<String, GateEntity> made = new LinkedHashMap<>();
        Vec3 g = site.raw(6.5, 0.0, 0.5);
        made.put(GATE, CovRaceSite.spawn(level, GateGeometry.of(g.x, g.y, g.z, 0.0F, GateSize.MEDIUM), GateSize.MEDIUM));
        Vec3 f = site.raw(16.5, 0.0, 0.5);
        made.put(FLAG, CovRaceSite.spawn(level, GateGeometry.element(f.x, f.y, f.z, 0.0F, CourseElement.FLAG, CourseElement.LEFT, 5.0, 4.0,
                GateTilt.NONE), GateSize.MEDIUM));
        Vec3 p = site.raw(22.5, 0.0, 0.5);
        made.put(POLE, CovRaceSite.spawn(level, GateGeometry.element(p.x, p.y, p.z, 0.0F, CourseElement.POLE, CourseElement.LEFT, 4.0, 3.0,
                GateTilt.NONE), GateSize.MEDIUM));
        Vec3 c = site.raw(29.5, 0.0, 0.5);
        made.put(CUBE, CovRaceSite.spawn(level, GateGeometry.element(c.x, c.y, c.z, 0.0F, CourseElement.CUBE, CourseElement.EXIT_FRONT, 4.5, 4.5,
                GateTilt.NONE), GateSize.MEDIUM));
        Vec3 q = site.raw(36.5, 0.0, 0.5);
        made.put(GATE_POLE, CovRaceSite.spawn(level, GateGeometry.element(q.x, q.y, q.z, 0.0F, CourseElement.GATE_POLE, 4, GatePoles.THICKNESS, 4.0,
                GateTilt.NONE), GateSize.MEDIUM));
        Vec3 r = site.raw(42.0, 1.0, 0.5);
        made.put(FREEFORM, CovRaceSite.spawn(level, GateGeometry.freeform(r.x, r.y, r.z, FreeformRegion.single(FreeformRegion.AXIS_Z), 0),
                GateSize.MEDIUM));
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return "keine Kursdaten";
        }
        int id = registry.createCourse(CourseRegistry.dimensionOf(level), player);
        CourseRegistry.Course course = registry.course(id);
        if (course == null) {
            return "Kurs nicht angelegt";
        }
        registry.rename(course, NAME);
        site.ids().put(COURSE, id);
        StringBuilder log = new StringBuilder("Kurs " + id + ":");
        for (Map.Entry<String, GateEntity> entry : made.entrySet()) {
            GateEntity gate = entry.getValue();
            if (registry.appendGate(id, gate) < 0) {
                return entry.getKey() + " nicht im Kurs";
            }
            site.ids().put(entry.getKey(), gate.getId());
            ORIGIN.put(entry.getKey(), gate.position());
            AABB box = gate.getBoundingBox();
            log.append(' ').append(entry.getKey()).append(" bei ").append(text(gate.position())).append(" Box ")
                    .append(String.format(Locale.ROOT, "%.2f..%.2f/%.2f..%.2f/%.2f..%.2f", box.minX, box.maxX, box.minY, box.maxY, box.minZ, box.maxZ))
                    .append(';');
        }
        REPORT.set(log.toString());
        return null;
    }

    private static String text(Vec3 v) {
        return String.format(Locale.ROOT, "%.3f %.3f %.3f", v.x, v.y, v.z);
    }

    private static void remember(ServerLevel level, CovRaceSite.Site site, BlockPos pos) {
        site.saved().putIfAbsent(pos.immutable(), level.getBlockState(pos));
    }

    private static BlockState piston(boolean sticky, Direction facing) {
        return (sticky ? Blocks.STICKY_PISTON : Blocks.PISTON).defaultBlockState().setValue(PistonBaseBlock.FACING, facing);
    }

    private static @Nullable String lift(ServerLevel level, CovRaceSite.Site site, String key, GateEntity gate) {
        AABB box = gate.getBoundingBox();
        BlockPos head = BlockPos.containing((box.minX + box.maxX) * 0.5, box.minY + 1.0E-4, (box.minZ + box.maxZ) * 0.5);
        if (!new AABB(head).intersects(box)) {
            return key + ": Kolbenkopf träfe die Box nicht";
        }
        BlockPos base = head.below();
        BlockPos power = base.below();
        remember(level, site, head);
        CovRaceSite.set(level, site, base, piston(false, Direction.UP));
        CovRaceSite.set(level, site, power, Blocks.REDSTONE_BLOCK.defaultBlockState());
        MACHINE.put(key + ".lift", base);
        MACHINE.put(key + ".lift.power", power);
        return null;
    }

    private static @Nullable String sideways(ServerLevel level, CovRaceSite.Site site, GateEntity gate) {
        AABB box = gate.getBoundingBox();
        int y = (int) Math.floor(box.minY) + 1;
        int z = (int) Math.floor((box.minZ + box.maxZ) * 0.5);
        int slime = (int) Math.floor(box.minX) - 1;
        BlockPos slimeAt = new BlockPos(slime, y, z);
        if (!new AABB(slimeAt.east()).intersects(box)) {
            return "Schleimblock träfe das Gate nicht";
        }
        remember(level, site, slimeAt.east());
        CovRaceSite.set(level, site, slimeAt.west(), piston(true, Direction.EAST));
        CovRaceSite.set(level, site, slimeAt, Blocks.SLIME_BLOCK.defaultBlockState());
        CovRaceSite.set(level, site, slimeAt.west(2), Blocks.REDSTONE_BLOCK.defaultBlockState());
        MACHINE.put("slime", slimeAt);
        int honey = (int) Math.ceil(box.maxX);
        BlockPos honeyAt = new BlockPos(honey, y, z);
        if (!new AABB(honeyAt.west()).intersects(box)) {
            return "Honigblock träfe das Gate nicht";
        }
        remember(level, site, honeyAt.west());
        CovRaceSite.set(level, site, honeyAt.east(), piston(false, Direction.WEST));
        CovRaceSite.set(level, site, honeyAt, Blocks.HONEY_BLOCK.defaultBlockState());
        CovRaceSite.set(level, site, honeyAt.east(2), Blocks.REDSTONE_BLOCK.defaultBlockState());
        MACHINE.put("honey", honeyAt);
        return null;
    }

    private static boolean extended(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(PistonBaseBlock.EXTENDED) && state.getValue(PistonBaseBlock.EXTENDED);
    }

    private static @Nullable String unchanged(ServerLevel level, CovRaceSite.Site site, List<String> keys, String when) {
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course course = registry == null ? null : registry.course(site.id(COURSE));
        if (course == null) {
            return when + ": Kurs fehlt am Server";
        }
        for (String key : keys) {
            GateEntity gate = CovRaceSite.entity(level, site, key);
            Vec3 origin = ORIGIN.get(key);
            if (gate == null || origin == null) {
                return when + ": " + key + " fehlt am Server";
            }
            if (gate.position().distanceTo(origin) > SAME) {
                return when + ": " + key + " am Server verschoben von " + text(origin) + " nach " + text(gate.position());
            }
            if (gate.getDeltaMovement().lengthSqr() > 1.0E-12) {
                return when + ": " + key + " hat Schwung " + text(gate.getDeltaMovement());
            }
            CourseRegistry.GateRecord record = record(course, gate);
            if (record == null) {
                return when + ": " + key + " fehlt in den Kursdaten";
            }
            Vec3 stored = new Vec3(record.x, record.y, record.z);
            if (stored.distanceTo(origin) > SAME) {
                return when + ": " + key + " in den Kursdaten verschoben nach " + text(stored);
            }
        }
        return null;
    }

    private static CourseRegistry.@Nullable GateRecord record(CourseRegistry.Course course, GateEntity gate) {
        String uuid = gate.getUUID().toString();
        for (CourseRegistry.GateRecord record : course.gates) {
            if (record.uuid.equals(uuid)) {
                return record;
            }
        }
        return null;
    }

    private static @Nullable String clientUnchanged(List<String> keys) {
        CovRaceSite.Site site = SITE.get();
        CourseData course = ClientCourses.get(site.id(COURSE));
        if (course == null) {
            return "Kurs fehlt beim Client";
        }
        for (String key : keys) {
            GateEntity gate = CovRaceSite.clientEntity(site, key);
            Vec3 origin = ORIGIN.get(key);
            if (gate == null || origin == null) {
                return key + " fehlt beim Client";
            }
            if (gate.position().distanceTo(origin) > SAME) {
                return key + " beim Client bei " + text(gate.position()) + " statt " + text(origin);
            }
            CourseData.Gate entry = null;
            for (CourseData.Gate each : course.gates()) {
                if (Math.abs(each.x() - origin.x) < 0.5 && Math.abs(each.z() - origin.z) < 0.5 && Math.abs(each.y() - origin.y) < 2.0) {
                    entry = each;
                }
            }
            if (entry == null || new Vec3(entry.x(), entry.y(), entry.z()).distanceTo(origin) > SAME) {
                return key + " im Kurs beim Client " + (entry == null ? "nicht gefunden" : "bei " + entry.x() + " " + entry.y() + " " + entry.z());
            }
        }
        return null;
    }

    private static StepResult check(SelfTest.StepContext ctx, String key, List<String> keys, String when) {
        StepResult wait = CovRaceSite.once(ctx, key, server -> unchanged(CovRaceSite.player(server).level(), SITE.get(), keys, when));
        if (wait != null) {
            return wait;
        }
        String client = clientUnchanged(keys);
        return client == null ? StepResult.pass() : StepResult.fail(when + ": " + client);
    }

    private static boolean ready() {
        return SITE.get() != null && ORIGIN.size() == ALL.size();
    }

    private static StepResult gateStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.fail("Aufbau fehlt");
        }
        int phase = CovRaceSite.phase(ctx);
        if (phase == 0) {
            StepResult wait = CovRaceSite.once(ctx, "Kolben", server -> {
                ServerLevel level = CovRaceSite.player(server).level();
                CovRaceSite.Site site = SITE.get();
                GateEntity gate = CovRaceSite.require(level, site, GATE);
                String problem = lift(level, site, GATE, gate);
                return problem != null ? problem : sideways(level, site, gate);
            });
            if (wait != null) {
                return wait;
            }
            CovRaceSite.next(ctx, 1);
            return StepResult.pending();
        }
        if (phase == 1) {
            if (CovRaceSite.since(ctx) < SETTLE) {
                return StepResult.pending();
            }
            StepResult wait = CovRaceSite.once(ctx, "ausgefahren", server -> {
                ServerLevel level = CovRaceSite.player(server).level();
                if (!extended(level, MACHINE.get(GATE + ".lift"))) {
                    return "Kolben unter dem Gate nicht ausgefahren";
                }
                if (!extended(level, MACHINE.get("slime").west())) {
                    return "klebriger Kolben nicht ausgefahren";
                }
                if (!level.getBlockState(MACHINE.get("slime").east()).is(Blocks.SLIME_BLOCK)) {
                    return "Schleimblock nicht verschoben";
                }
                if (!level.getBlockState(MACHINE.get("honey").west()).is(Blocks.HONEY_BLOCK)) {
                    return "Honigblock nicht verschoben";
                }
                return null;
            });
            if (wait != null) {
                return wait;
            }
            StepResult result = check(ctx, "nach dem Ausfahren", List.of(GATE), "nach dem Ausfahren");
            if (result.status() != StepResult.Status.PASS) {
                return result;
            }
            CovRaceSite.next(ctx, 2);
            return StepResult.pending();
        }
        if (phase == 2) {
            StepResult wait = CovRaceSite.once(ctx, "einfahren", server -> {
                ServerLevel level = CovRaceSite.player(server).level();
                level.setBlockAndUpdate(MACHINE.get(GATE + ".lift.power"), Blocks.SMOOTH_STONE.defaultBlockState());
                level.setBlockAndUpdate(MACHINE.get("slime").west(2), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(MACHINE.get("honey").east(2), Blocks.AIR.defaultBlockState());
                return null;
            });
            if (wait != null) {
                return wait;
            }
            CovRaceSite.next(ctx, 3);
            return StepResult.pending();
        }
        if (CovRaceSite.since(ctx) < SETTLE) {
            return StepResult.pending();
        }
        StepResult wait = CovRaceSite.once(ctx, "eingefahren", server -> {
            ServerLevel level = CovRaceSite.player(server).level();
            if (extended(level, MACHINE.get(GATE + ".lift")) || extended(level, MACHINE.get("slime").west())) {
                return "Kolben nicht eingefahren";
            }
            return level.getBlockState(MACHINE.get("slime")).is(Blocks.SLIME_BLOCK) ? null : "Schleimblock nicht zurückgezogen";
        });
        if (wait != null) {
            return wait;
        }
        return check(ctx, "nach dem Einfahren", List.of(GATE), "nach dem Einfahren");
    }

    private static StepResult elementStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.fail("Aufbau fehlt");
        }
        if (CovRaceSite.phase(ctx) == 0) {
            StepResult wait = CovRaceSite.once(ctx, "Kolben", server -> {
                ServerLevel level = CovRaceSite.player(server).level();
                CovRaceSite.Site site = SITE.get();
                for (String key : ELEMENTS) {
                    String problem = lift(level, site, key, CovRaceSite.require(level, site, key));
                    if (problem != null) {
                        return problem;
                    }
                }
                return null;
            });
            if (wait != null) {
                return wait;
            }
            CovRaceSite.next(ctx, 1);
            return StepResult.pending();
        }
        if (CovRaceSite.since(ctx) < SETTLE) {
            return StepResult.pending();
        }
        StepResult wait = CovRaceSite.once(ctx, "ausgefahren", server -> {
            ServerLevel level = CovRaceSite.player(server).level();
            for (String key : ELEMENTS) {
                if (!extended(level, MACHINE.get(key + ".lift"))) {
                    return "Kolben unter " + key + " nicht ausgefahren";
                }
            }
            return null;
        });
        if (wait != null) {
            return wait;
        }
        return check(ctx, "Elemente", ELEMENTS, "Elemente nach dem Ausfahren");
    }

    private static StepResult forceStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.fail("Aufbau fehlt");
        }
        if (CovRaceSite.phase(ctx) == 0) {
            StepResult wait = CovRaceSite.once(ctx, "Kräfte", GatePushSelfTests::forces);
            if (wait != null) {
                return wait;
            }
            CovRaceSite.next(ctx, 1);
            return StepResult.pending();
        }
        if (CovRaceSite.phase(ctx) == 1) {
            if (CovRaceSite.since(ctx) < WATCH) {
                return StepResult.pending();
            }
            StepResult result = check(ctx, "nach den Kräften", ALL, "Wasser, Lava, Explosion, Spieler, Schwein, Lore und Boot");
            if (result.status() != StepResult.Status.PASS) {
                return result;
            }
            CovRaceSite.next(ctx, 2);
            return StepResult.pending();
        }
        StepResult wait = CovRaceSite.once(ctx, "Kräfte weg", server -> {
            ServerPlayer player = CovRaceSite.player(server);
            ServerLevel level = player.level();
            CovRaceSite.Site site = SITE.get();
            for (String key : List.of("water", "lava")) {
                BlockPos pos = MACHINE.get(key);
                if (pos != null) {
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
            }
            for (Entity entity : level.getEntities((Entity) null, CovRaceSite.area(site),
                    entity -> entity.getType() == EntityTypes.PIG || entity.getType() == EntityTypes.MINECART || entity.getType() == EntityTypes.OAK_BOAT)) {
                entity.discard();
            }
            Vec3 feet = site.raw(STAND.x, 0.0, STAND.z);
            player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), 0.0F, 0.0F, true);
            return null;
        });
        return wait != null ? wait : StepResult.pass();
    }

    private static @Nullable String forces(MinecraftServer server) {
        ServerPlayer player = CovRaceSite.player(server);
        ServerLevel level = player.level();
        CovRaceSite.Site site = SITE.get();
        GateEntity gate = CovRaceSite.require(level, site, GATE);
        GateEntity cube = CovRaceSite.require(level, site, CUBE);
        BlockPos water = BlockPos.containing(gate.getBoundingBox().minX - 1.0, gate.getY() + 1.0E-4, gate.getZ());
        CovRaceSite.set(level, site, water, Blocks.WATER.defaultBlockState());
        MACHINE.put("water", water);
        BlockPos lava = BlockPos.containing(cube.getBoundingBox().minX - 1.0, cube.getY() + 1.0E-4, cube.getZ());
        CovRaceSite.set(level, site, lava, Blocks.LAVA.defaultBlockState());
        MACHINE.put("lava", lava);
        for (String key : ALL) {
            GateEntity each = CovRaceSite.require(level, site, key);
            level.explode(null, each.getX() + 0.3, each.getY() + 1.0, each.getZ() - 0.6, 3.0F, Level.ExplosionInteraction.NONE);
        }
        GateEntity flag = CovRaceSite.require(level, site, FLAG);
        player.teleportTo(level, flag.getX(), flag.getY(), flag.getZ(), Set.of(), 90.0F, 0.0F, true);
        player.setDeltaMovement(0.6, 0.0, 0.0);
        List<String> missing = new ArrayList<>();
        spawnMoving(level, EntityTypes.PIG, gate.position().add(-0.6, 0.0, 0.0), new Vec3(0.5, 0.0, 0.0), missing);
        spawnMoving(level, EntityTypes.MINECART, CovRaceSite.require(level, site, POLE).position().add(-1.0, 0.0, 0.0), new Vec3(1.2, 0.0, 0.0), missing);
        spawnMoving(level, EntityTypes.OAK_BOAT, CovRaceSite.require(level, site, GATE_POLE).position().add(-1.0, 0.0, 0.0), new Vec3(1.2, 0.0, 0.0),
                missing);
        spawnMoving(level, EntityTypes.PIG, CovRaceSite.require(level, site, FREEFORM).position().add(0.0, -1.0, 0.0), new Vec3(-0.4, 0.0, 0.0), missing);
        return missing.isEmpty() ? null : "nicht gespawnt: " + missing;
    }

    private static void spawnMoving(ServerLevel level, EntityType<? extends Entity> type, Vec3 at, Vec3 motion, List<String> missing) {
        Entity entity = type.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            missing.add(type.toString());
            return;
        }
        entity.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        entity.setDeltaMovement(motion);
        if (!level.addFreshEntity(entity)) {
            missing.add(type.toString());
        }
    }

    private static StepResult restoreStep(SelfTest.StepContext ctx) {
        if (!ready()) {
            return StepResult.fail("Aufbau fehlt");
        }
        if (CovRaceSite.phase(ctx) == 0) {
            StepResult wait = CovRaceSite.once(ctx, "verschoben laden", GatePushSelfTests::reload);
            if (wait != null) {
                return wait;
            }
            CovRaceSite.next(ctx, 1);
            return StepResult.pending();
        }
        if (CovRaceSite.since(ctx) < SETTLE) {
            return StepResult.pending();
        }
        StepResult wait = CovRaceSite.once(ctx, "zurückgesetzt", server -> {
            String problem = unchanged(CovRaceSite.player(server).level(), SITE.get(), List.of(GATE), "nach dem Laden");
            CovLogCapture capture = LOG.getAndSet(null);
            if (capture == null) {
                return problem != null ? problem : "keine Log-Aufzeichnung";
            }
            try {
                if (problem != null) {
                    return problem;
                }
                GateEntity gate = CovRaceSite.require(CovRaceSite.player(server).level(), SITE.get(), GATE);
                String uuid = gate.getUUID().toString();
                long lines = capture.lines().stream().filter(line -> line.contains(uuid) && line.contains(NAME)).count();
                return lines == 1 ? null : lines + " Logzeilen zum zurückgesetzten Gate statt einer: " + capture.lines();
            } finally {
                capture.close();
            }
        });
        if (wait != null) {
            return wait;
        }
        String client = clientUnchanged(List.of(GATE));
        if (client != null) {
            if (CovRaceSite.since(ctx) > 120) {
                return StepResult.fail("nach dem Laden: " + client);
            }
            return StepResult.pending();
        }
        return StepResult.pass();
    }

    private static @Nullable String reload(MinecraftServer server) {
        ServerLevel level = CovRaceSite.player(server).level();
        CovRaceSite.Site site = SITE.get();
        GateEntity gate = CovRaceSite.require(level, site, GATE);
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        gate.saveWithoutId(output);
        CompoundTag tag = output.buildResult();
        Vec3 origin = ORIGIN.get(GATE);
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(origin.x + 1.25));
        pos.add(DoubleTag.valueOf(origin.y + 1.01));
        pos.add(DoubleTag.valueOf(origin.z - 0.5));
        tag.put("Pos", pos);
        gate.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        GateEntity loaded = EntityType.create(RaceModule.GATE.get(), TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag), level,
                        EntitySpawnReason.LOAD)
                .filter(GateEntity.class::isInstance).map(GateEntity.class::cast).orElse(null);
        if (loaded == null) {
            return "Gate nicht aus den Speicherdaten erzeugt";
        }
        if (loaded.position().distanceTo(origin) < 1.0) {
            return "geladenes Gate steht nicht verschoben: " + text(loaded.position());
        }
        CovLogCapture previous = LOG.getAndSet(CovLogCapture.propwash());
        if (previous != null) {
            previous.close();
        }
        if (!level.addFreshEntity(loaded)) {
            return "geladenes Gate nicht in die Welt gesetzt";
        }
        site.ids().put(GATE, loaded.getId());
        return null;
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        CovRaceSite.Site site = SITE.get();
        if (site == null) {
            return StepResult.pass();
        }
        StepResult wait = CovRaceSite.once(ctx, "Aufräumen", server -> {
            CovLogCapture capture = LOG.getAndSet(null);
            if (capture != null) {
                capture.close();
            }
            for (Entity entity : CovRaceSite.player(server).level().getEntities((Entity) null, CovRaceSite.area(site),
                    entity -> entity.getType() == EntityTypes.PIG || entity.getType() == EntityTypes.MINECART || entity.getType() == EntityTypes.OAK_BOAT)) {
                entity.discard();
            }
            return CovRaceSite.teardown(server, site);
        });
        if (wait != null) {
            return wait;
        }
        SITE.set(null);
        ORIGIN.clear();
        MACHINE.clear();
        return StepResult.pass();
    }
}
