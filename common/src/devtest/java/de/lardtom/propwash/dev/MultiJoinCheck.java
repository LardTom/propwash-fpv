package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.example.ExampleFollow;
import de.lardtom.propwash.dev.example.ExampleOperator;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.operator.OperatorServer;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RaceRecords;
import de.lardtom.propwash.rescue.AddonAutonomy;
import de.lardtom.propwash.server.SpectatorLinks;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.video.PilotFeedServer;
import de.lardtom.propwash.video.SpectateServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class MultiJoinCheck {
    public static final String PILOT = "PwPilotA";
    public static final String WATCHER = "PwPilotB";
    public static final String WATCHED_SIGNAL = "propwash-jointest-watched";
    public static final int SIGNAL_SLOT = 8;
    public static final int GATES = 3;
    public static final int SPACING = 10;
    public static final int SETTLE_TICKS = 60;
    public static final int REPORT_EVERY = 200;
    public static final String SOLO = "solo";
    public static final String WATCH_SOLO = "watchsolo";
    public static final double GIMBAL_TAKEOFF = 4.0;
    public static final double GIMBAL_AHEAD = 3.0;
    public static final int GIMBAL_BIND_AFTER = 80;
    public static final int GIMBAL_BIND_WAIT = 20 * 30;
    public static final int GIMBAL_END_WAIT = 10;
    public static final double MOB_RADIUS = 2.5;
    public static final double CAUSE_ITEM_RADIUS = 4.0;
    public static final double CAUSE_MOB_RADIUS = 8.0;
    private static int bothSince = -1;
    private static boolean built;
    private static int course = GateEntity.NO_COURSE;
    private static @Nullable UUID drone;
    private static @Nullable QuadcopterEntity raceDrone;
    private static boolean watchLogged;
    private static int entriesLogged = -1;
    private static boolean simulated;
    private static double loopX;
    private static double loopY;
    private static double loopZ;
    private static boolean opped;
    private static @Nullable NameAndId watcherId;
    private static boolean goneLogged;
    private static int gimbalStage;
    private static int gimbalMark;
    private static @Nullable UUID gimbal;
    private static double gimbalAxis;
    private static boolean gimbalByWatcher;
    private static int[] gateIndices = new int[0];

    private MultiJoinCheck() {
    }

    static void init() {
        if (MigrationJoinCheck.active()) {
            MigrationJoinCheck.init();
        }
    }

    static void tick(MinecraftServer server, int tick) {
        if (MigrationJoinCheck.active()) {
            MigrationJoinCheck.tick(server, tick);
            return;
        }
        ServerPlayer pilot = server.getPlayerList().getPlayerByName(PILOT);
        ServerPlayer watcher = server.getPlayerList().getPlayerByName(WATCHER);
        String mode = JoinTestEnv.multi();
        boolean solo = SOLO.equals(mode);
        boolean watchSolo = WATCH_SOLO.equals(mode);
        if (!built) {
            boolean ready = watchSolo ? watcher != null : pilot != null && (watcher != null || solo);
            if (!ready) {
                bothSince = -1;
                if (tick % REPORT_EVERY == 0) {
                    Constants.LOG.info("[Beitrittstest] Mehrspieler: warte auf {} ({}) und {} ({})", PILOT, pilot != null ? "da" : "fehlt",
                            WATCHER, watcher != null ? "da" : "fehlt");
                }
                return;
            }
            if (bothSince < 0) {
                bothSince = tick;
                Constants.LOG.info("[Beitrittstest] Mehrspieler: {} sind auf dem Server", solo ? PILOT + " allein (solo)"
                        : watchSolo ? WATCHER + " allein (watchsolo, simulierte Drohne von " + PILOT + ")" : PILOT + " und " + WATCHER);
                if (JoinTestEnv.multiMobs() > 0) {
                    placeMobs(watchSolo ? watcher : pilot, JoinTestEnv.multiMobs());
                }
            }
            if (tick - bothSince < SETTLE_TICKS) {
                return;
            }
            built = true;
            try {
                build(server, watchSolo ? watcher : pilot, watchSolo ? null : pilot, watcher);
            } catch (RuntimeException e) {
                Constants.LOG.error("[Beitrittstest] Mehrspieler: Aufbau fehlgeschlagen", e);
            }
            return;
        }
        if (simulated) {
            fly(server, tick);
        }
        if (watcher != null && watchLogged && !solo) {
            gimbal(server, watchSolo ? watcher : pilot, watcher, tick);
        }
        if (tick % 20 == 0) {
            observe(server, pilot, watcher, tick);
        }
        if (opped && (watcher == null || gimbalStage == 4 && goneLogged)) {
            deop(server);
        }
    }

    private static void fly(MinecraftServer server, int tick) {
        UUID id = drone;
        if (id == null || !(server.overworld().getEntity(id) instanceof QuadcopterEntity flown)) {
            return;
        }
        double t = tick / 20.0 * 12.0 / 72.0 * Math.PI * 2.0;
        double x = loopX + 4.0 + 6.0 * Math.sin(t);
        double z = loopZ + 10.0 - 14.0 * Math.cos(t);
        float yaw = (float) Math.toDegrees(Math.atan2(-6.0 * Math.cos(t), 14.0 * Math.sin(t)));
        flown.snapTo(x, loopY, z, yaw, 0.0F);
        float noseDown = CameraMount.uptilt(flown.build(), flown.tune().values());
        flown.setAttitude(new Quaternionf().rotationY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(-noseDown)));
        PilotFeedServer.simulate(flown, SimulatedPilotFeed.racing(tick, course, gateIndices, tick / 40));
    }

    private static void gimbal(MinecraftServer server, @Nullable ServerPlayer owner, ServerPlayer watcher, int tick) {
        switch (gimbalStage) {
            case 0 -> {
                if (owner == null || SpectateServer.watching(watcher) != null || SpectatorLinks.watched(watcher) != null) {
                    return;
                }
                QuadcopterEntity drone = spawnGimbal(watcher);
                gimbal = drone.getUUID();
                AutonomyResult result = ExampleFollow.takeoff(drone, owner, GIMBAL_TAKEOFF);
                Constants.LOG.info("[Beitrittstest] Mehrspieler: {} schaut nicht mehr zu, Drohne {} mit Gimbal vorn startet für {} ins autonome Halten ({} m): {}",
                        WATCHER, drone.getUUID(), owner.getPlainTextName(), String.format(Locale.ROOT, "%.0f", GIMBAL_TAKEOFF), result);
                gimbalStage = result.started() ? 1 : 99;
                gimbalMark = tick;
            }
            case 1 -> {
                QuadcopterEntity drone = gimbalDrone(server);
                int waited = tick - gimbalMark;
                if (drone == null) {
                    Constants.LOG.error("[Beitrittstest] Mehrspieler: Gimbal-Drohne vor dem Binden verschwunden");
                    gimbalStage = 99;
                    return;
                }
                if (waited < GIMBAL_BIND_AFTER || waited % 20 != 0) {
                    return;
                }
                boolean bound = ExampleOperator.bind(drone, watcher);
                Constants.LOG.info("[Beitrittstest] Mehrspieler: {} wird Operator am Gimbal der Drohne {} im autonomen Halten ({}, Flug {}, {} m über dem Boden)",
                        WATCHER, drone.getUUID(), bound ? "gebunden" : "verweigert: " + ExampleOperator.LAST.get(),
                        AddonAutonomy.active(drone).map(Object::toString).orElse("-"), String.format(Locale.ROOT, "%.1f", drone.getY() - watcher.getY()));
                if (bound) {
                    gimbalStage = 2;
                } else if (waited > GIMBAL_BIND_WAIT) {
                    gimbalStage = 99;
                }
            }
            case 2 -> {
                QuadcopterEntity drone = gimbalDrone(server);
                if (drone != null) {
                    DroneSim sim = AddonAutonomy.sim(drone);
                    double axis = sim == null ? Double.NaN : sim.operatorAxis(0);
                    if (Double.isFinite(axis)) {
                        gimbalAxis = Math.max(gimbalAxis, axis);
                    }
                    return;
                }
                gimbalByWatcher = opped;
                gimbalStage = 3;
                gimbalMark = tick;
            }
            case 3 -> {
                if (tick - gimbalMark < GIMBAL_END_WAIT) {
                    return;
                }
                UUID id = gimbal;
                String ended = id == null ? "-" : ExampleFollow.lastEnded(id).map(end -> end.reason().name()).orElse("kein Ende gemeldet");
                Constants.LOG.info("[Beitrittstest] Mehrspieler: Gimbal-Drohne {} entfernt{}, autonomer Flug beendet: {}, Operator-Bindungen {}, Add-on-Flüge {}, "
                                + "FlightState#operatorAxis(0) höchstens {}, {} Operator-Eingaben angenommen", id,
                        gimbalByWatcher ? " (/propwash clear durch " + WATCHER + ")" : "", ended, OperatorServer.bound(), AddonAutonomy.flights(),
                        String.format(Locale.ROOT, "%.2f", gimbalAxis), OperatorServer.accepted());
                gimbalStage = 4;
            }
            default -> {
            }
        }
    }

    private static @Nullable QuadcopterEntity gimbalDrone(MinecraftServer server) {
        UUID id = gimbal;
        return id != null && server.overworld().getEntity(id) instanceof QuadcopterEntity drone && !drone.isRemoved() ? drone : null;
    }

    private static QuadcopterEntity spawnGimbal(ServerPlayer watcher) {
        ServerLevel level = watcher.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        AccessoryType mount = AccessoryType.byId(ExampleOperator.GIMBAL_FRONT).orElseThrow(() -> new IllegalStateException("Beispiel-Gimbal fehlt"));
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE.withAccessory(mount));
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        int x = Mth.floor(watcher.getX() + GIMBAL_AHEAD);
        int z = Mth.floor(watcher.getZ());
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        Vec3 pos = new Vec3(x + 0.5, top, z + 0.5);
        drone.snapTo(pos.x, pos.y, pos.z, 90.0F, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, new Quaternionf().rotationY((float) Math.toRadians(-90.0)));
        return drone;
    }

    private static void build(MinecraftServer server, ServerPlayer builder, @Nullable ServerPlayer pilot, @Nullable ServerPlayer watcher) {
        ServerLevel level = builder.level();
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            throw new IllegalStateException("CourseRegistry läuft nicht");
        }
        BlockPos spawn = level.getRespawnData().pos();
        builder.setGameMode(GameType.CREATIVE);
        String enemies = calm(server, level);
        int x = spawn.getX() + 12;
        int z0 = spawn.getZ() + 6;
        AABB area = new AABB(x - 6, level.getMinY(), z0 - 10, x + 14, level.getMaxY(), z0 + SPACING * GATES + 10);
        for (GateEntity old : level.getEntitiesOfClass(GateEntity.class, area)) {
            old.discard();
        }
        for (QuadcopterEntity old : level.getEntitiesOfClass(QuadcopterEntity.class, area)) {
            old.discard();
        }
        ItemStack previous = builder.getMainHandItem().copy();
        float yaw = builder.getYRot();
        builder.setYRot(0.0F);
        builder.setShiftKeyDown(false);
        ItemStack gates = new ItemStack(RaceModule.GATE_MEDIUM.get(), GATES);
        builder.setItemInHand(InteractionHand.MAIN_HAND, gates);
        List<GateEntity> placed = new ArrayList<>();
        try {
            for (int i = 0; i < GATES; i++) {
                int z = z0 + i * SPACING;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                BlockPos ground = new BlockPos(x, top - 1, z);
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false);
                InteractionResult result = gates.getItem().useOn(new UseOnContext(builder, InteractionHand.MAIN_HAND, hit));
                if (!result.consumesAction()) {
                    throw new IllegalStateException("Gate " + i + " nicht platziert: " + result);
                }
                List<GateEntity> found = level.getEntitiesOfClass(GateEntity.class, new AABB(ground.above()).inflate(0.6));
                if (found.size() != 1) {
                    throw new IllegalStateException("Gate " + i + " nicht gefunden: " + found.size());
                }
                placed.add(found.getFirst());
            }
        } finally {
            builder.setItemInHand(InteractionHand.MAIN_HAND, previous);
            builder.setYRot(yaw);
        }
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        for (GateEntity gate : placed) {
            CourseToolItem.useOnGate(tool, builder, gate);
        }
        course = CourseToolItem.courseOf(tool);
        gateIndices = placed.stream().mapToInt(GateEntity::gateIndex).toArray();
        CourseRegistry.Course data = registry.course(course);
        if (data == null || data.gates.size() != GATES) {
            throw new IllegalStateException("Kurs " + course + " hat nicht " + GATES + " Gates");
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z0 - 6);
        QuadcopterEntity created = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (created == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        created.snapTo(x + 0.5, top, z0 - 6 + 0.5, 0.0F, 0.0F);
        created.setAttitude(new Quaternionf());
        created.setBuild(DroneBuild.RACER);
        created.setBatteryCharge(1.0F);
        level.addFreshEntity(created);
        drone = created.getUUID();
        raceDrone = created;
        String start;
        if (pilot != null) {
            ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_ANALOG).map(ItemStack::new)
                    .orElseThrow(() -> new IllegalStateException("Analog-Brille fehlt"));
            goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(created));
            pilot.setItemSlot(EquipmentSlot.HEAD, goggles);
            ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
            remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(created));
            pilot.getInventory().setItem(0, remote);
            pilot.getInventory().setItem(SIGNAL_SLOT, ItemStack.EMPTY);
            pilot.getInventory().setSelectedSlot(0);
            pilot.connection.send(new ClientboundSetHeldSlotPacket(0));
            pilot.teleportTo(level, x - 4.5, top, z0 - 8.5, Set.of(), 0.0F, 10.0F, true);
            start = "FPV für " + PILOT + ": " + DronePiloting.tryStart(pilot, created, PilotMode.FPV);
        } else {
            SpectateServer.simulateTransmitter(created, PILOT);
            SpectateServer.refreshSoon();
            PilotFeedServer.simulateLook(created, SimulatedPilotFeed.look());
            simulated = true;
            loopX = x;
            loopY = top + 1.5;
            loopZ = z0;
            start = "Sender simuliert als " + PILOT + ", fliegt eine Runde um den Kurs, schickt Zuschauern Renn-Zustand, Sticks und OSD-Layout "
                    + SimulatedPilotFeed.PRESET.key() + " (" + SimulatedPilotFeed.UNIT + ", Mode " + SimulatedPilotFeed.STICK_MODE.number() + ")";
        }
        if (watcher != null) {
            server.getPlayerList().op(watcher.nameAndId());
            watcherId = watcher.nameAndId();
            opped = true;
            watcher.setGameMode(GameType.CREATIVE);
            ItemStack watcherGoggles = ContentIds.item(ContentIds.GOGGLES_ANALOG).map(ItemStack::new)
                    .orElseThrow(() -> new IllegalStateException("Analog-Brille fehlt"));
            watcher.setItemSlot(EquipmentSlot.HEAD, watcherGoggles);
            int watcherTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x - 8, z0 + SPACING);
            watcher.teleportTo(level, x - 8.5, watcherTop, z0 + SPACING + 0.5, Set.of(), -90.0F, 10.0F, true);
        }
        Constants.LOG.info("[Beitrittstest] Mehrspieler: Kurs {} mit {} Gates bei x {} z {}–{}, Drohne {} ({}), {}, {}, Mob-Spawn aus, feindliche Mobs entfernt: {}",
                course, GATES, x, z0, z0 + SPACING * (GATES - 1), created.getUUID(), created.build().frame().key(), start,
                watcher == null ? "ohne Zuschauer" : WATCHER + " mit Analog-Brille, Operator für /propwash clear", enemies);
    }

    private static void placeMobs(@Nullable ServerPlayer anyone, int count) {
        if (anyone == null) {
            return;
        }
        ServerLevel level = anyone.level();
        BlockPos spawn = level.getRespawnData().pos();
        double cx = spawn.getX() + 12 + 0.5;
        double cz = spawn.getZ() + 6 - 3.5;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            int x = Mth.floor(cx + MOB_RADIUS * Math.cos(angle));
            int z = Mth.floor(cz + MOB_RADIUS * Math.sin(angle));
            Slime slime = EntityTypes.SLIME.create(level, EntitySpawnReason.COMMAND);
            if (slime == null) {
                continue;
            }
            slime.setSize(2, true);
            slime.snapTo(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5, 0.0F, 0.0F);
            slime.setPersistenceRequired();
            level.addFreshEntity(slime);
        }
        Constants.LOG.info("[Beitrittstest] Mehrspieler: {} Schleime (Größe 2) um {} {} gesetzt, wie natürlich gespawnte Mobs vor dem Aufbau ({})", count,
                String.format(Locale.ROOT, "%.1f", cx), String.format(Locale.ROOT, "%.1f", cz), JoinTestEnv.MULTI_MOBS);
    }

    private static String calm(MinecraftServer server, ServerLevel level) {
        level.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
        List<Entity> enemies = new ArrayList<>();
        Map<String, Integer> types = new TreeMap<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Enemy && entity instanceof Mob) {
                enemies.add(entity);
                types.merge(EntityType.getKey(entity.getType()).getPath(), 1, Integer::sum);
            }
        }
        enemies.forEach(Entity::discard);
        return enemies.size() + (types.isEmpty() ? "" : " " + types);
    }

    private static void deop(MinecraftServer server) {
        NameAndId id = watcherId;
        if (opped && id != null) {
            server.getPlayerList().deop(id);
        }
        opped = false;
    }

    private static boolean dropped(ServerLevel level, Vec3 at) {
        return !level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(CAUSE_ITEM_RADIUS),
                item -> item.getItem().is(DroneModule.DRONE_ITEM.get())).isEmpty();
    }

    private static boolean cleared(ServerLevel level) {
        QuadcopterEntity last = raceDrone;
        return last != null && last.getRemovalReason() == Entity.RemovalReason.DISCARDED && !dropped(level, last.position());
    }

    private static String cause(ServerLevel level) {
        QuadcopterEntity last = raceDrone;
        if (last == null) {
            return "keine Drohne gemerkt";
        }
        Vec3 at = last.position();
        boolean dropped = dropped(level, at);
        Map<String, Integer> mobs = new TreeMap<>();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(CAUSE_MOB_RADIUS))) {
            mobs.merge(EntityType.getKey(mob.getType()).getPath(), 1, Integer::sum);
        }
        return String.format(Locale.ROOT, "Grund %s bei %.1f %.1f %.1f, Drohnen-Item %s, Mobs in %.0f m: %s", last.getRemovalReason(), at.x, at.y, at.z,
                dropped ? "liegt da (zerbrochen)" : "keins", CAUSE_MOB_RADIUS, mobs.isEmpty() ? "keine" : mobs);
    }

    private static void observe(MinecraftServer server, @Nullable ServerPlayer pilot, @Nullable ServerPlayer watcher, int tick) {
        QuadcopterEntity flown = null;
        if (pilot != null) {
            flown = DronePiloting.pilotedBy(pilot.getUUID()).orElse(null);
        } else if (simulated && drone != null && server.overworld().getEntity(drone) instanceof QuadcopterEntity sim) {
            flown = sim;
        }
        if (!watchLogged && watcher != null && flown != null && SpectateServer.watching(watcher) == flown) {
            watchLogged = true;
            Constants.LOG.info("[Beitrittstest] Mehrspieler: {} schaut der Drohne von {} zu", WATCHER, PILOT);
            if (pilot != null) {
                ItemStack signal = new ItemStack(Items.PAPER);
                signal.set(DataComponents.CUSTOM_NAME, Component.literal(WATCHED_SIGNAL));
                pilot.getInventory().setItem(SIGNAL_SLOT, signal);
            }
        }
        UUID id = drone;
        if (built && !goneLogged && id != null && server.overworld().getEntity(id) == null) {
            goneLogged = true;
            boolean cleared = cleared(server.overworld());
            String how = watcherId == null ? "" : cleared ? " (/propwash clear durch " + WATCHER + ")" : " ohne /propwash clear";
            Constants.LOG.info("[Beitrittstest] Mehrspieler: Drohne {} entfernt{}, {} schaut {}", id, how,
                    WATCHER, watcher != null && SpectateServer.watching(watcher) != null ? "noch zu" : "nicht mehr zu");
            Constants.LOG.info("[Beitrittstest] Mehrspieler: Drohne {} weg: {}", id, cause(server.overworld()));
        }
        if (course != GateEntity.NO_COURSE) {
            Leaderboard board = RaceRecords.get(server).board(course);
            List<Leaderboard.Entry> entries = board.entries();
            List<Leaderboard.Entry> assisted = board.assistedEntries();
            if (entries.size() + 1000 * assisted.size() != entriesLogged) {
                entriesLogged = entries.size() + 1000 * assisted.size();
                for (Leaderboard.Entry entry : assisted) {
                    Constants.LOG.info("[Beitrittstest] Mehrspieler: Runde mit Flughilfe von {} ({} s): {}", entry.name(),
                            String.format(Locale.ROOT, "%.3f", entry.nanos() / 1.0E9), entry.reasons().list());
                }
                StringBuilder out = new StringBuilder();
                for (Leaderboard.Entry entry : entries) {
                    out.append(entry.name()).append(' ').append(entry.frame()).append(' ')
                            .append(String.format(Locale.ROOT, "%.3f s", entry.nanos() / 1.0E9)).append("; ");
                }
                Constants.LOG.info("[Beitrittstest] Mehrspieler: Bestenliste Kurs {}: {} Einträge {}", course, entries.size(), out);
            }
        }
        if (tick % REPORT_EVERY == 0) {
            Constants.LOG.info("[Beitrittstest] Mehrspieler: {} {}, {} {}, Drohne {}", PILOT, pilot == null ? "weg" : "da", WATCHER,
                    watcher == null ? "weg" : "da", flown == null ? "nicht gesteuert" : String.format(Locale.ROOT, "bei %.1f %.1f %.1f",
                            flown.getX(), flown.getY(), flown.getZ()));
        }
    }
}
