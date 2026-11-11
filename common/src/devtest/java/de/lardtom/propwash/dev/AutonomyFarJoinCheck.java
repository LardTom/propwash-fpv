package de.lardtom.propwash.dev;

import com.mojang.authlib.GameProfile;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.rescue.AddonAutonomy;
import de.lardtom.propwash.rescue.AutonomousReturns;
import de.lardtom.propwash.server.RangeManager;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.ServerRulesConfig;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AutonomyFarJoinCheck {
    public static final Identifier FAR = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "far_cruise");
    public static final int SETTLE_TICKS = 40;
    public static final int FAR_BLOCKS = 300;
    public static final double CRUISE_SPEED = 8.0;
    public static final double CRUISE_HEIGHT = 8.0;
    public static final int CORRIDOR_ROWS = 3;
    public static final int CRUISE_TIMEOUT_TICKS = 20 * 70;
    public static final int FROZEN_TICKS = 10;
    public static final int STILL_TICKS = 40;
    public static final double FENCE_GAP = 8.0;
    public static final int HOME_TIMEOUT_TICKS = 20 * 90;
    public static final double HOME_TOLERANCE = 4.0;
    public static final double SPOT_TOLERANCE = 2.0;
    public static final int ROOF_ABOVE = 4;
    public static final int ROOF_REACH = 20;
    public static final String LIMIT_HINT = "propwash.drone.autonomy.ended.terrain_limit";
    public static final double STILL_METRES = 0.25;
    public static final double DASH_SPEED = 30.0;
    public static final int DASH_TICKS = 20 * 20;
    public static final int AFTER_DASH_TICKS = 20 * 10;
    public static final double FOLLOW_SPEED = 20.0;
    public static final double FOLLOW_BEHIND = 6.0;
    public static final int RELEASE_TICKS = 40;
    public static final int SITE_X = 6408;
    public static final int SITE_Z = 6408;
    public static final int SITE_SPACING = 512;
    private static final UUID SIM_OWNER = UUID.fromString("00000000-0000-4000-8000-0000000a0f02");
    private static final String SIM_NAME = "AutonomyFarOwner";
    private static final List<String> FAILURES = new ArrayList<>();
    private static final List<String> HINTS = new CopyOnWriteArrayList<>();
    private static boolean initialized;
    private static int cycle;
    private static int stage;
    private static int mark;
    private static int lastTickCount;
    private static int frozen;
    private static int frozenWorst;
    private static int stillSince;
    private static int stillWorst;
    private static int endAt = -1;
    private static boolean homeSeen;
    private static boolean roofed;
    private static Vec3 endPos = Vec3.ZERO;
    private static int passed;
    private static boolean done;
    private static @Nullable UUID droneId;
    private static @Nullable AutonomyEnd ended;
    private static Vec3 start = Vec3.ZERO;
    private static Vec3 last = Vec3.ZERO;
    private static double groundY;
    private static @Nullable Follow follow;
    private static @Nullable Cruise cruise;
    private static @Nullable ServerRulesConfig savedRules;

    private AutonomyFarJoinCheck() {
    }

    enum Kind {
        CORRIDOR("Korridor", true),
        STRIP("Streifen, Regel an", true),
        STRIP_LIMITED("Streifen, Regel aus", false),
        FOLLOW("Folgen", true),
        STRIP_COVERED("Streifen, Regel aus, ohne GPS", false);

        final String label;
        final boolean generates;

        Kind(String label, boolean generates) {
            this.label = label;
            this.generates = generates;
        }

        boolean strip() {
            return this == STRIP || this.limited();
        }

        boolean limited() {
            return this == STRIP_LIMITED || this == STRIP_COVERED;
        }
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashEvents.AUTONOMY_ENDED.register((drone, owner, id, reason) -> {
            if (FAR.equals(id) && drone.getUUID().equals(droneId)) {
                ended = reason;
            }
        });
    }

    static Kind kind() {
        return Kind.values()[Math.min(cycle, Kind.values().length - 1)];
    }

    static void tick(MinecraftServer server, int tick) {
        if (done) {
            return;
        }
        try {
            step(server, tick);
        } catch (RuntimeException e) {
            Constants.LOG.error("[Beitrittstest] Weiter Flug ohne Pilot: Durchgang {} Stufe {} fehlgeschlagen", cycle + 1, stage, e);
            fail(server, tick, "Ausnahme " + e);
        }
    }

    private static void step(MinecraftServer server, int tick) {
        int waited = tick - mark;
        switch (stage) {
            case 0 -> {
                ServerPlayer owner = owner(server);
                if (owner == null) {
                    joinSim(server);
                    mark = tick;
                    return;
                }
                if (waited < SETTLE_TICKS) {
                    return;
                }
                useDefaultRules();
                ServerLevel level = server.overworld();
                WorldRules.set(server, WorldRuleSpecs.DRONES_GENERATE_CHUNKS, kind().generates ? 1 : 0);
                int x = SITE_X;
                int z = SITE_Z + SITE_SPACING * cycle;
                pregenerate(level, x, z, kind());
                level.getChunk(x >> 4, z >> 4);
                double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                owner.teleportTo(level, x + 0.5, y, z + 0.5, Set.<Relative>of(), -90.0F, 10.0F, true);
                level.getChunkSource().move(owner);
                stage = 1;
                mark = tick;
            }
            case 1 -> {
                ServerPlayer owner = requireOwner(server);
                if (waited < SETTLE_TICKS * 2) {
                    return;
                }
                boolean mayGenerate = ServerRules.mayGenerateChunks(owner);
                if (mayGenerate != kind().generates) {
                    fail(server, tick, "simulierter Besitzer darf Gelände erzeugen: " + mayGenerate + ", erwartet " + kind().generates + " (" + rulesText() + ")");
                    return;
                }
                QuadcopterEntity drone = spawn(owner);
                droneId = drone.getUUID();
                ended = null;
                start = drone.position();
                last = start;
                groundY = drone.getY();
                frozen = 0;
                frozenWorst = 0;
                stillSince = 0;
                stillWorst = 0;
                endAt = -1;
                homeSeen = false;
                roofed = false;
                endPos = Vec3.ZERO;
                HINTS.clear();
                lastTickCount = drone.tickCount;
                FlightAssist assist;
                if (kind() == Kind.FOLLOW) {
                    follow = new Follow(owner, groundY + CRUISE_HEIGHT);
                    cruise = null;
                    assist = follow;
                } else {
                    follow = null;
                    cruise = new Cruise(start.x + FAR_BLOCKS, start.z, groundY + CRUISE_HEIGHT);
                    assist = cruise;
                }
                AutonomyResult result = PropwashApi.drones().autonomy().start(drone, owner, assist,
                        AutonomyOptions.of(FAR).label("FAR").takeoff(CRUISE_HEIGHT));
                Constants.LOG.info("[Beitrittstest] Weiter Flug ohne Pilot {}/{} ({}): Drohne {} bei {}, Flug {}, Besitzer bei {}, Simulationsdistanz {}, Sichtweite {}, Regel drones_generate_chunks {}, Besitzer erzeugt Gelände {}",
                        cycle + 1, Kind.values().length, kind().label, drone.getId(), format(drone.position()), result, format(owner.position()),
                        server.getPlayerList().getSimulationDistance(), server.getPlayerList().getViewDistance(),
                        WorldRules.on(server, WorldRuleSpecs.DRONES_GENERATE_CHUNKS), mayGenerate);
                if (!result.started()) {
                    fail(server, tick, "Flug startet nicht: " + result);
                    return;
                }
                stage = 2;
                mark = tick;
            }
            case 2 -> observe(server, tick, waited);
            case 4 -> home(server, tick, waited);
            case 3 -> {
                QuadcopterEntity drone = drone(server);
                if (drone != null) {
                    drone.discard();
                }
                if (RangeManager.ticketCount() == 0) {
                    pass(server, tick);
                } else if (waited > RELEASE_TICKS) {
                    fail(server, tick, RangeManager.ticketCount() + " Chunk-Tickets " + RELEASE_TICKS + " Ticks nach dem Ende noch gesetzt");
                }
            }
            default -> {
            }
        }
    }

    private static void observe(MinecraftServer server, int tick, int waited) {
        ServerPlayer owner = requireOwner(server);
        Kind kind = kind();
        if (kind == Kind.FOLLOW && waited < DASH_TICKS) {
            Vec3 to = owner.position().add(DASH_SPEED / 20.0, 0.0, 0.0);
            owner.teleportTo(owner.level(), to.x, to.y, to.z, Set.<Relative>of(), -90.0F, 10.0F, false);
            owner.level().getChunkSource().move(owner);
        }
        QuadcopterEntity drone = drone(server);
        if (drone == null) {
            fail(server, tick, "Drohne " + String.format(Locale.ROOT, "%.1f", waited / 20.0) + " s nach dem Start entladen (zuletzt " + format(last) + ", "
                    + travelled(last) + " m geflogen)");
            return;
        }
        Vec3 now = drone.position();
        if (drone.tickCount == lastTickCount) {
            frozen++;
            frozenWorst = Math.max(frozenWorst, frozen);
            if (frozen == 2) {
                Constants.LOG.warn("[Beitrittstest] Weiter Flug ohne Pilot {}/{} ({}): Drohne tickt nicht ({} s, {} m geflogen), {}", cycle + 1,
                        Kind.values().length, kind.label, String.format(Locale.ROOT, "%.1f", waited / 20.0), travelled(now), chunkState(drone));
            }
        } else {
            frozen = 0;
            lastTickCount = drone.tickCount;
        }
        if (now.distanceTo(last) > STILL_METRES) {
            last = now;
            stillSince = waited;
        }
        int still = waited - stillSince;
        boolean moving = kind == Kind.FOLLOW ? waited < DASH_TICKS : travelled(now) < FAR_BLOCKS - 2.0;
        if (moving && waited > 20 * 3 && !kind.limited()) {
            stillWorst = Math.max(stillWorst, still);
        }
        if (waited % 20 == 0) {
            Constants.LOG.info("[Beitrittstest] Weiter Flug ohne Pilot {}/{} ({}): t={}s Drohne {} ({} m geflogen, {} m über Grund, Abstand zum Besitzer {} m), Flug {}, steht {} s, Grenze {}, {}",
                    cycle + 1, Kind.values().length, kind.label, waited / 20, format(now), travelled(now), String.format(Locale.ROOT, "%.1f", now.y - groundY),
                    Math.round(Math.hypot(now.x - owner.getX(), now.z - owner.getZ())), AddonAutonomy.phase(drone).orElse("keiner"),
                    String.format(Locale.ROOT, "%.1f", still / 20.0), limitSeen(), chunkState(drone));
        }
        if (frozen > FROZEN_TICKS) {
            fail(server, tick, "Drohne tickt " + frozen + " Ticks nicht (" + String.format(Locale.ROOT, "%.1f", waited / 20.0) + " s, " + travelled(now)
                    + " m geflogen, " + chunkState(drone) + ")");
            return;
        }
        if (kind == Kind.STRIP_COVERED && !roofed && limitSeen() && roof((ServerLevel) drone.level(), drone) == 0) {
            fail(server, tick, "keine Decke gebaut, kein Chunk über der Drohne geladen (" + chunkState(drone) + ")");
            return;
        }
        if (ended != null && kind.limited() && ended == AutonomyEnd.TERRAIN_LIMIT) {
            limited(server, tick, waited, drone, now);
            return;
        }
        if (ended != null) {
            fail(server, tick, "Flug endet nach " + String.format(Locale.ROOT, "%.1f", waited / 20.0) + " s mit " + ended + " (" + travelled(now) + " m geflogen)");
            return;
        }
        if (kind.generates && limitSeen()) {
            fail(server, tick, "Flughilfe sieht FlightState#terrainLimit() mit erlaubtem Erzeugen (" + travelled(now) + " m geflogen, " + chunkState(drone) + ")");
            return;
        }
        if (stillWorst > STILL_TICKS) {
            fail(server, tick, "Drohne steht " + String.format(Locale.ROOT, "%.1f", stillWorst / 20.0) + " s im Flug (" + travelled(now) + " m geflogen, "
                    + chunkState(drone) + ")");
            return;
        }
        if (kind.limited() && waited > CRUISE_TIMEOUT_TICKS) {
            fail(server, tick, "kein Ende mit TERRAIN_LIMIT nach " + CRUISE_TIMEOUT_TICKS / 20 + " s (" + travelled(now) + " m geflogen, steht "
                    + String.format(Locale.ROOT, "%.1f", still / 20.0) + " s, " + gapText(drone, now) + ", Flughilfe sieht die Grenze " + limitSeen() + ", "
                    + chunkState(drone) + ")");
            return;
        }
        boolean reached;
        if (kind == Kind.FOLLOW) {
            reached = waited >= DASH_TICKS + AFTER_DASH_TICKS;
        } else if (kind.limited()) {
            reached = false;
        } else {
            reached = travelled(now) >= FAR_BLOCKS - 2.0 && still >= 20;
        }
        if (reached) {
            Constants.LOG.info("[Beitrittstest] Weiter Flug ohne Pilot {}/{} ({}): nach {} s {} m geflogen, {} m vom Besitzer, längster Tick-Ausfall {} Ticks, längster Stillstand im Flug {} s, Flug aktiv, {}",
                    cycle + 1, Kind.values().length, kind.label, String.format(Locale.ROOT, "%.1f", waited / 20.0), travelled(now),
                    Math.round(Math.hypot(now.x - owner.getX(), now.z - owner.getZ())), frozenWorst, String.format(Locale.ROOT, "%.1f", stillWorst / 20.0),
                    chunkState(drone));
            PropwashApi.drones().autonomy().stop(drone);
            stage = 3;
            mark = tick;
            return;
        }
        if (waited > CRUISE_TIMEOUT_TICKS) {
            fail(server, tick, "Ziel nach " + CRUISE_TIMEOUT_TICKS / 20 + " s nicht erreicht (" + travelled(now) + " m geflogen, " + chunkState(drone) + ")");
        }
    }

    private static void limited(MinecraftServer server, int tick, int waited, QuadcopterEntity drone, Vec3 now) {
        ServerLevel level = (ServerLevel) drone.level();
        int edge = edgeChunk(level, drone.chunkPosition());
        double gap = edge * 16.0 - now.x;
        boolean homing = homing(drone);
        Constants.LOG.info("[Beitrittstest] Weiter Flug ohne Pilot {}/{} ({}): Flug endet nach {} s mit TERRAIN_LIMIT, {} m geflogen, {} m vor Chunk {} (erster Chunk ohne erzeugte Umgebung), Flughilfe sah die Grenze {}, Heimflug {}, Hinweis an den Besitzer {}, {}",
                cycle + 1, Kind.values().length, kind().label, String.format(Locale.ROOT, "%.1f", waited / 20.0), travelled(now),
                String.format(Locale.ROOT, "%.1f", gap), edge, limitSeen(), homing, HINTS.contains(LIMIT_HINT), chunkState(drone));
        if (!(gap > 0.0 && gap <= FENCE_GAP)) {
            fail(server, tick, "Ende " + String.format(Locale.ROOT, "%.1f", gap) + " m vor dem ersten Chunk ohne erzeugte Umgebung (erwartet 0 bis " + FENCE_GAP
                    + " m, " + travelled(now) + " m geflogen)");
            return;
        }
        if (!limitSeen()) {
            fail(server, tick, "Flughilfe sah FlightState#terrainLimit() vor dem Ende nie");
            return;
        }
        if (AddonAutonomy.active(drone).isPresent()) {
            fail(server, tick, "Add-on-Flug nach TERRAIN_LIMIT noch aktiv");
            return;
        }
        if (!HINTS.contains(LIMIT_HINT)) {
            fail(server, tick, "kein Hinweis " + LIMIT_HINT + " an den Besitzer nach TERRAIN_LIMIT (erhalten: " + HINTS + ")");
            return;
        }
        if (kind() == Kind.STRIP_COVERED && !roofed) {
            fail(server, tick, "Decke über der Drohne nicht gebaut, Flughilfe sah die Grenze " + limitSeen());
            return;
        }
        if (kind() == Kind.STRIP_COVERED && homing) {
            fail(server, tick, "Heimflug ohne GPS-Empfang unter der Decke statt Landung an Ort und Stelle");
            return;
        }
        endPos = now;
        endAt = waited;
        stage = 4;
    }

    private static void home(MinecraftServer server, int tick, int waited) {
        QuadcopterEntity drone = drone(server);
        if (drone == null) {
            fail(server, tick, "Drohne im Heimflug entladen (zuletzt " + format(last) + ")");
            return;
        }
        Vec3 now = drone.position();
        last = now;
        homeSeen |= homing(drone);
        double fromStart = Math.hypot(now.x - start.x, now.z - start.z);
        if ((waited - endAt) % 20 == 0) {
            Constants.LOG.info("[Beitrittstest] Weiter Flug ohne Pilot {}/{} ({}): Heimflug t={}s Drohne {} ({} m vom Start, {} m über Grund), Heimflug {}, scharf {}, {}",
                    cycle + 1, Kind.values().length, kind().label, (waited - endAt) / 20, format(now), Math.round(fromStart),
                    String.format(Locale.ROOT, "%.1f", now.y - groundY), AutonomousReturns.phase(drone), drone.isArmed(), chunkState(drone));
        }
        if (kind() == Kind.STRIP_COVERED && homeSeen) {
            fail(server, tick, "Heimflug ohne GPS-Empfang unter der Decke statt Landung an Ort und Stelle (" + Math.round(fromStart) + " m vom Start)");
            return;
        }
        if (!drone.isArmed() && drone.returnPlan() == null && !AutonomousReturns.flying(drone) && !AddonAutonomy.flying(drone)) {
            if (kind() == Kind.STRIP_COVERED) {
                landedInPlace(server, tick, waited, drone, now);
                return;
            }
            if (!homeSeen) {
                fail(server, tick, "nach TERRAIN_LIMIT kein Heimflug, Drohne liegt " + Math.round(fromStart) + " m vom Start");
                return;
            }
            if (!(fromStart <= HOME_TOLERANCE)) {
                fail(server, tick, "Heimflug landet " + String.format(Locale.ROOT, "%.1f", fromStart) + " m vom Start (erwartet höchstens " + HOME_TOLERANCE + " m)");
                return;
            }
            Constants.LOG.info("[Beitrittstest] Weiter Flug ohne Pilot {}/{} ({}): Heimflug nach {} s {} m vom Start gelandet, {}", cycle + 1,
                    Kind.values().length, kind().label, String.format(Locale.ROOT, "%.1f", (waited - endAt) / 20.0),
                    String.format(Locale.ROOT, "%.1f", fromStart), chunkState(drone));
            stage = 3;
            mark = tick;
            return;
        }
        if (waited - endAt > HOME_TIMEOUT_TICKS) {
            fail(server, tick, "Heimflug nach " + HOME_TIMEOUT_TICKS / 20 + " s nicht gelandet (" + Math.round(fromStart) + " m vom Start, Heimflug "
                    + AutonomousReturns.phase(drone) + ")");
        }
    }

    private static void landedInPlace(MinecraftServer server, int tick, int waited, QuadcopterEntity drone, Vec3 now) {
        double fromEnd = Math.hypot(now.x - endPos.x, now.z - endPos.z);
        if (!(fromEnd <= SPOT_TOLERANCE)) {
            fail(server, tick, "landet " + String.format(Locale.ROOT, "%.1f", fromEnd) + " m von der Stelle des Endes (erwartet höchstens " + SPOT_TOLERANCE + " m)");
            return;
        }
        if (!(now.y - groundY < 1.0)) {
            fail(server, tick, "liegt " + String.format(Locale.ROOT, "%.1f", now.y - groundY) + " m über Grund statt am Boden");
            return;
        }
        Constants.LOG.info("[Beitrittstest] Weiter Flug ohne Pilot {}/{} ({}): ohne GPS-Empfang kein Heimflug, nach {} s an Ort und Stelle gelandet, {} m von der Stelle des Endes, {} m vom Start, {}",
                cycle + 1, Kind.values().length, kind().label, String.format(Locale.ROOT, "%.1f", (waited - endAt) / 20.0),
                String.format(Locale.ROOT, "%.1f", fromEnd), Math.round(Math.hypot(now.x - start.x, now.z - start.z)), chunkState(drone));
        stage = 3;
        mark = tick;
    }

    private static int roof(ServerLevel level, QuadcopterEntity drone) {
        roofed = true;
        int edge = edgeChunk(level, drone.chunkPosition());
        int y = Mth.floor(drone.getY()) + ROOF_ABOVE;
        int atX = Mth.floor(drone.getX());
        int atZ = Mth.floor(drone.getZ());
        int toX = Math.min(atX + ROOF_REACH, edge * 16 - 1);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int x = atX - ROOF_REACH; x <= toX; x++) {
            for (int z = atZ - ROOF_REACH; z <= atZ + ROOF_REACH; z++) {
                if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
                    continue;
                }
                level.setBlock(cursor.set(x, y, z), Blocks.STONE.defaultBlockState(), 2);
                placed++;
            }
        }
        Constants.LOG.info("[Beitrittstest] Weiter Flug ohne Pilot {}/{} ({}): Flughilfe sieht die Grenze bei {}, Decke aus {} Steinen auf y={} von x={} bis {} ({} m vor Chunk {}), z={} bis {}, {}",
                cycle + 1, Kind.values().length, kind().label, format(drone.position()), placed, y, atX - ROOF_REACH, toX,
                String.format(Locale.ROOT, "%.1f", edge * 16.0 - drone.getX()), edge, atZ - ROOF_REACH, atZ + ROOF_REACH, chunkState(drone));
        return placed;
    }

    private static boolean homing(QuadcopterEntity drone) {
        return drone.returnPlan() != null || AutonomousReturns.phase(drone) != null;
    }

    private static boolean limitSeen() {
        Cruise current = cruise;
        return current != null && current.limitSeen;
    }

    private static String gapText(QuadcopterEntity drone, Vec3 now) {
        int edge = edgeChunk((ServerLevel) drone.level(), drone.chunkPosition());
        return String.format(Locale.ROOT, "%.1f m vor Chunk %d", edge * 16.0 - now.x, edge);
    }

    private static int edgeChunk(ServerLevel level, ChunkPos from) {
        int x = from.x();
        while (x < from.x() + 64 && RangeManager.generatedAround(level, x, from.z())) {
            x++;
        }
        return x;
    }

    private static void pregenerate(ServerLevel level, int x, int z, Kind kind) {
        int rows = kind.strip() ? 0 : CORRIDOR_ROWS;
        int fromX = (x >> 4) - 1;
        int toX = (x + FAR_BLOCKS + (kind == Kind.FOLLOW ? 700 : 64)) >> 4;
        int rowZ = (z + 2) >> 4;
        if (kind == Kind.FOLLOW) {
            return;
        }
        for (int cx = fromX; cx <= toX; cx++) {
            for (int cz = rowZ - rows; cz <= rowZ + rows; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    private static long travelled(Vec3 now) {
        return Math.round(Math.hypot(now.x - start.x, now.z - start.z));
    }

    private static void pass(MinecraftServer server, int tick) {
        passed++;
        next(server, tick);
    }

    private static void fail(MinecraftServer server, int tick, String reason) {
        FAILURES.add((cycle + 1) + " (" + kind().label + "): " + reason);
        Constants.LOG.error("[Beitrittstest] Weiter Flug ohne Pilot {}/{} ({}): FAIL {}", cycle + 1, Kind.values().length, kind().label, reason);
        QuadcopterEntity drone = drone(server);
        if (drone != null) {
            PropwashApi.drones().autonomy().stop(drone);
            drone.discard();
        }
        next(server, tick);
    }

    private static void next(MinecraftServer server, int tick) {
        droneId = null;
        ended = null;
        follow = null;
        cruise = null;
        WorldRules.set(server, WorldRuleSpecs.DRONES_GENERATE_CHUNKS, 1);
        cycle++;
        stage = cycle < Kind.values().length ? 0 : 99;
        mark = tick;
        if (cycle < Kind.values().length) {
            return;
        }
        done = true;
        restoreRules();
        ServerPlayer owner = owner(server);
        if (owner != null) {
            owner.connection.onDisconnect(new DisconnectionDetails(Component.literal("Beitrittstest weiter Flug ohne Pilot")));
        }
        Constants.LOG.info("[Beitrittstest] Weiter Flug ohne Pilot: {} von {} Durchgängen ohne Ausfall{}", passed, Kind.values().length,
                FAILURES.isEmpty() ? "" : ": " + String.join(" | ", FAILURES));
        try {
            Files.writeString(Services.PLATFORM.getGameDir().resolve(JoinTestEnv.STOP_FILE), passed + "/" + Kind.values().length + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            Constants.LOG.error("[Beitrittstest] Weiter Flug ohne Pilot: {} nicht geschrieben", JoinTestEnv.STOP_FILE, e);
        }
    }

    private static void useDefaultRules() {
        if (savedRules != null) {
            return;
        }
        ServerRulesConfig config = ServerRulesConfig.get();
        savedRules = config.copy();
        String before = rulesText();
        config.assign(new ServerRulesConfig());
        Constants.LOG.info("[Beitrittstest] Weiter Flug ohne Pilot: Server-Konfig für die Prüfung auf Standard ({}, vorher {})", rulesText(), before);
    }

    private static void restoreRules() {
        ServerRulesConfig saved = savedRules;
        if (saved != null) {
            ServerRulesConfig.get().assign(saved);
            savedRules = null;
        }
    }

    private static String rulesText() {
        ServerRulesConfig config = ServerRulesConfig.get();
        return "onlyGeneratedChunks " + config.onlyGeneratedChunks + ", generateChunksPermissionLevel " + config.generateChunksPermissionLevel
                + ", allowUnlimitedRange " + config.allowUnlimitedRange + ", unlimitedRangePermissionLevel " + config.unlimitedRangePermissionLevel;
    }

    public static boolean done() {
        return done;
    }

    public static @Nullable String failure() {
        if (!done) {
            return "nach " + cycle + " von " + Kind.values().length + " Durchgängen nicht fertig";
        }
        return FAILURES.isEmpty() ? null : String.join(" | ", FAILURES);
    }

    private static @Nullable ServerPlayer owner(MinecraftServer server) {
        ServerPlayer sim = server.getPlayerList().getPlayer(SIM_OWNER);
        return sim == null || sim.hasDisconnected() ? null : sim;
    }

    private static ServerPlayer requireOwner(MinecraftServer server) {
        ServerPlayer owner = owner(server);
        if (owner == null) {
            throw new IllegalStateException("simulierter Besitzer nicht verbunden");
        }
        return owner;
    }

    private static ServerPlayer joinSim(MinecraftServer server) {
        GameProfile profile = new GameProfile(SIM_OWNER, SIM_NAME);
        ServerPlayer player = new Owner(server, profile);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
        return player;
    }

    private static QuadcopterEntity spawn(ServerPlayer owner) {
        ServerLevel level = owner.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        BlockPos at = BlockPos.containing(owner.getX() + 3.0, owner.getY(), owner.getZ() + 2.0);
        Vec3 pos = new Vec3(at.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()), at.getZ() + 0.5);
        drone.snapTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, new Quaternionf());
        return drone;
    }

    private static @Nullable QuadcopterEntity drone(MinecraftServer server) {
        UUID id = droneId;
        return id != null && server.overworld().getEntity(id) instanceof QuadcopterEntity quad && !quad.isRemoved() ? quad : null;
    }

    private static String chunkState(QuadcopterEntity drone) {
        ServerLevel level = (ServerLevel) drone.level();
        ChunkPos chunk = drone.chunkPosition();
        DistanceManager distance = level.getChunkSource().chunkMap.getDistanceManager();
        long key = chunk.pack();
        return String.format(Locale.ROOT, "Chunk %d/%d, Ladestufe %d, Simulation %d, tickend %s, Tickets %d", chunk.x(), chunk.z(),
                distance.getChunkLevel(key, false), distance.getChunkLevel(key, true), level.isPositionEntityTicking(drone.blockPosition()),
                RangeManager.ticketCount());
    }

    private static String format(Vec3 v) {
        return String.format(Locale.ROOT, "%.1f/%.1f/%.1f", v.x, v.y, v.z);
    }

    static final class Owner extends ServerPlayer {
        Owner(MinecraftServer server, GameProfile profile) {
            super(server, server.overworld(), profile, ClientInformation.createDefault());
        }

        @Override
        public void sendSystemMessage(Component message, boolean overlay) {
            if (message.getContents() instanceof TranslatableContents translatable) {
                HINTS.add(translatable.getKey());
            }
            super.sendSystemMessage(message, overlay);
        }
    }

    static final class Cruise implements FlightAssist {
        private final double targetX;
        private final double targetZ;
        private final double altitude;
        boolean limitSeen;

        Cruise(double targetX, double targetZ, double altitude) {
            this.targetX = targetX;
            this.targetZ = targetZ;
            this.altitude = altitude;
        }

        @Override
        public void update(FlightState state, AssistOutput output) {
            this.limitSeen |= state.terrainLimit();
            output.holdAltitude(this.altitude);
            output.holdPosition(this.targetX, this.targetZ);
            output.maxSpeed(CRUISE_SPEED);
            output.label("FAR");
        }
    }

    static final class Follow implements FlightAssist {
        private final ServerPlayer target;
        private final double altitude;

        Follow(ServerPlayer target, double altitude) {
            this.target = target;
            this.altitude = altitude;
        }

        @Override
        public void update(FlightState state, AssistOutput output) {
            output.holdAltitude(this.altitude);
            output.holdPosition(this.target.getX() - FOLLOW_BEHIND, this.target.getZ() + 2.0);
            output.maxSpeed(FOLLOW_SPEED);
            output.label("FOLLOW");
        }
    }
}
