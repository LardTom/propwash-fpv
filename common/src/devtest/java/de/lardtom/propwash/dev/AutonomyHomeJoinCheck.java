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
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.RadioLink;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.rescue.AddonAutonomy;
import de.lardtom.propwash.rescue.AutonomousReturns;
import de.lardtom.propwash.rescue.HomePoint;
import de.lardtom.propwash.rescue.HomePointCodecs;
import de.lardtom.propwash.server.RangeManager;
import de.lardtom.propwash.server.ServerRules;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
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

public final class AutonomyHomeJoinCheck {
    public static final Identifier RANGE = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "home_range");
    public static final int SETTLE_TICKS = 40;
    public static final double AWAY = 40.0;
    public static final double CRUISE_SPEED = 6.0;
    public static final double REACH_TOLERANCE = 1.5;
    public static final int HOLD_TICKS = 40;
    public static final int CRUISE_TIMEOUT_TICKS = 20 * 40;
    public static final double OWNER_BEYOND = 40.0;
    public static final int END_TIMEOUT_TICKS = 40;
    public static final int LAND_TIMEOUT_TICKS = 20 * 90;
    public static final double HOME_TOLERANCE = 2.5;
    public static final double SPOT_TOLERANCE = 1.5;
    public static final int ROOF_HEIGHT = 6;
    public static final int ROOF_RADIUS = 12;
    public static final int RELEASE_TICKS = 40;
    public static final int SITE_X = 8408;
    public static final int SITE_Z = 8408;
    public static final int SITE_SPACING = 512;
    private static final UUID SIM_OWNER = UUID.fromString("00000000-0000-4000-8000-0000000a0f03");
    private static final String SIM_NAME = "AutonomyHomeOwner";
    private static final List<String> FAILURES = new ArrayList<>();
    private static boolean initialized;
    private static int cycle;
    private static int stage;
    private static int mark;
    private static int heldSince = -1;
    private static int passed;
    private static boolean done;
    private static boolean sawReturn;
    private static @Nullable UUID droneId;
    private static @Nullable AutonomyEnd ended;
    private static Vec3 home = Vec3.ZERO;
    private static Vec3 target = Vec3.ZERO;
    private static Vec3 leftAt = Vec3.ZERO;

    private AutonomyHomeJoinCheck() {
    }

    enum Kind {
        OPEN("Heimflug unter freiem Himmel", false, 10.0),
        ROOF("Steindecke ohne GPS", true, 3.0);

        final String label;
        final boolean roof;
        final double height;

        Kind(String label, boolean roof, double height) {
            this.label = label;
            this.roof = roof;
            this.height = height;
        }
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashEvents.AUTONOMY_ENDED.register((drone, owner, id, reason) -> {
            if (RANGE.equals(id) && drone.getUUID().equals(droneId)) {
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
            Constants.LOG.error("[Beitrittstest] Heimflug außer Reichweite: Durchgang {} Stufe {} fehlgeschlagen", cycle + 1, stage, e);
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
                ServerLevel level = server.overworld();
                int x = SITE_X;
                int z = SITE_Z + SITE_SPACING * cycle;
                level.getChunk(x >> 4, z >> 4);
                level.getChunk((x + (int) AWAY) >> 4, z >> 4);
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
                ServerLevel level = owner.level();
                Kind kind = kind();
                BlockPos base = BlockPos.containing(owner.getX(), owner.getY(), owner.getZ() + 3.0);
                home = new Vec3(base.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, base.getX(), base.getZ()), base.getZ() + 0.5);
                BlockPos at = base.offset((int) AWAY, 0, 0);
                double groundY = kind.roof ? shelter(level, at) : level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ());
                Vec3 spawnAt = kind.roof ? new Vec3(at.getX() + 0.5, groundY, at.getZ() + 0.5) : home;
                QuadcopterEntity drone = spawn(level, spawnAt);
                drone.setHomePoint(new HomePoint(home.x, home.y, home.z, HomePointCodecs.dimension(level), level.getGameTime(), HomePoint.Source.MANUAL));
                droneId = drone.getUUID();
                ended = null;
                sawReturn = false;
                heldSince = -1;
                target = new Vec3(at.getX() + 0.5, Math.max(groundY, spawnAt.y) + kind.height, at.getZ() + 0.5);
                AutonomyResult result = PropwashApi.drones().autonomy().start(drone, owner, new Cruise(target.x, target.z, target.y),
                        AutonomyOptions.of(RANGE).label("RANGE").takeoff(kind.height));
                Constants.LOG.info("[Beitrittstest] Heimflug außer Reichweite {}/{} ({}): Drohne {} bei {}, Homepoint {}, Ziel {}, Flug {}",
                        cycle + 1, Kind.values().length, kind.label, drone.getId(), format(drone.position()), format(home), format(target), result);
                if (!result.started()) {
                    fail(server, tick, "Flug startet nicht: " + result);
                    return;
                }
                stage = 2;
                mark = tick;
            }
            case 2 -> cruise(server, tick, waited);
            case 3 -> observe(server, tick, waited);
            case 4 -> {
                QuadcopterEntity drone = drone(server);
                if (drone != null) {
                    drone.discard();
                }
                if (RangeManager.ticketCount() == 0) {
                    pass(server, tick);
                } else if (waited > RELEASE_TICKS) {
                    fail(server, tick, RangeManager.ticketCount() + " Chunk-Tickets " + RELEASE_TICKS + " Ticks nach der Landung noch gesetzt");
                }
            }
            default -> {
            }
        }
    }

    private static void cruise(MinecraftServer server, int tick, int waited) {
        ServerPlayer owner = requireOwner(server);
        QuadcopterEntity drone = drone(server);
        if (drone == null) {
            fail(server, tick, "Drohne vor dem Verlassen der Reichweite verschwunden");
            return;
        }
        if (ended != null) {
            fail(server, tick, "Flug endet vor dem Verlassen der Reichweite mit " + ended + " bei " + format(drone.position()));
            return;
        }
        double off = Math.hypot(drone.getX() - target.x, drone.getZ() - target.z);
        boolean there = off <= REACH_TOLERANCE && Math.abs(drone.getY() - target.y) <= REACH_TOLERANCE && AddonAutonomy.active(drone).isPresent();
        if (!there) {
            heldSince = -1;
            if (waited > CRUISE_TIMEOUT_TICKS) {
                fail(server, tick, "Ziel nach " + CRUISE_TIMEOUT_TICKS / 20 + " s nicht erreicht (" + format(drone.position()) + ", "
                        + String.format(Locale.ROOT, "%.1f", off) + " m daneben, Phase " + AddonAutonomy.phase(drone).orElse("keine") + ")");
            }
            return;
        }
        if (heldSince < 0) {
            heldSince = tick;
        }
        if (tick - heldSince < HOLD_TICKS) {
            return;
        }
        double range = RadioLink.releaseRange(ServerRules.maxPilotDistance(owner), DronePiloting.freeRange(owner, drone));
        if (!Double.isFinite(range)) {
            fail(server, tick, "Funkreichweite unbegrenzt, Besitzer kann sie nicht verlassen");
            return;
        }
        ServerLevel level = owner.level();
        BlockPos away = BlockPos.containing(home.x - range - OWNER_BEYOND, home.y, home.z);
        level.getChunk(away.getX() >> 4, away.getZ() >> 4);
        double awayY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, away.getX(), away.getZ());
        owner.teleportTo(level, away.getX() + 0.5, awayY, away.getZ() + 0.5, Set.<Relative>of(), -90.0F, 10.0F, true);
        level.getChunkSource().move(owner);
        leftAt = drone.position();
        Constants.LOG.info("[Beitrittstest] Heimflug außer Reichweite {}/{} ({}): Drohne hält bei {} ({} m vom Homepoint), Besitzer geht {} m weit weg (Funkreichweite {} m)",
                cycle + 1, Kind.values().length, kind().label, format(leftAt), String.format(Locale.ROOT, "%.1f", horizontal(leftAt, home)),
                Math.round(owner.position().distanceTo(drone.position())), Math.round(range));
        stage = 3;
        mark = tick;
    }

    private static void observe(MinecraftServer server, int tick, int waited) {
        QuadcopterEntity drone = drone(server);
        if (drone == null) {
            fail(server, tick, "Drohne " + String.format(Locale.ROOT, "%.1f", waited / 20.0) + " s nach dem Verlassen der Reichweite entladen");
            return;
        }
        Kind kind = kind();
        if (ended == null) {
            if (waited > END_TIMEOUT_TICKS) {
                fail(server, tick, "Flug endet " + END_TIMEOUT_TICKS + " Ticks nach dem Verlassen der Reichweite nicht (" + format(drone.position()) + ")");
            }
            return;
        }
        if (ended != AutonomyEnd.OUT_OF_RANGE) {
            fail(server, tick, "Ende " + ended + " statt " + AutonomyEnd.OUT_OF_RANGE);
            return;
        }
        boolean returning = drone.returnPlan() != null || AutonomousReturns.phase(drone) != null;
        if (returning && !sawReturn) {
            sawReturn = true;
            Constants.LOG.info("[Beitrittstest] Heimflug außer Reichweite {}/{} ({}): {} nach {} s, Heimflug zum Homepoint {} beginnt",
                    cycle + 1, Kind.values().length, kind.label, ended, String.format(Locale.ROOT, "%.1f", waited / 20.0), format(home));
        }
        if (kind.roof && sawReturn) {
            fail(server, tick, "fliegt ohne GPS-Empfang unter der Steindecke heim (" + format(drone.position()) + ")");
            return;
        }
        if (waited % 20 == 0) {
            Constants.LOG.info("[Beitrittstest] Heimflug außer Reichweite {}/{} ({}): t={}s Drohne {} ({} m vom Homepoint, {} m von der Stelle), {}, scharf {}",
                    cycle + 1, Kind.values().length, kind.label, waited / 20, format(drone.position()),
                    String.format(Locale.ROOT, "%.1f", horizontal(drone.position(), home)), String.format(Locale.ROOT, "%.1f", horizontal(drone.position(), leftAt)),
                    returning ? "Heimflug " + AutonomousReturns.phase(drone) : "Phase " + AddonAutonomy.phase(drone).orElse("keine"), drone.isArmed());
        }
        boolean landed = !AutonomousReturns.flying(drone) && drone.returnPlan() == null && !drone.isArmed() && drone.onGround();
        if (landed) {
            double fromHome = horizontal(drone.position(), home);
            double fromSpot = horizontal(drone.position(), leftAt);
            if (!kind.roof && !sawReturn) {
                fail(server, tick, "landet an Ort und Stelle statt heimzufliegen (" + String.format(Locale.ROOT, "%.1f", fromSpot) + " m von der Stelle, "
                        + String.format(Locale.ROOT, "%.1f", fromHome) + " m vom Homepoint)");
                return;
            }
            if (!kind.roof && fromHome > HOME_TOLERANCE) {
                fail(server, tick, "Heimflug landet " + String.format(Locale.ROOT, "%.1f", fromHome) + " m vom Homepoint (erlaubt " + HOME_TOLERANCE + " m)");
                return;
            }
            if (kind.roof && fromSpot > SPOT_TOLERANCE) {
                fail(server, tick, "landet " + String.format(Locale.ROOT, "%.1f", fromSpot) + " m von der Stelle (erlaubt " + SPOT_TOLERANCE + " m)");
                return;
            }
            Constants.LOG.info("[Beitrittstest] Heimflug außer Reichweite {}/{} ({}): {}, gelandet nach {} s {} ({} m vom Homepoint, {} m von der Stelle)",
                    cycle + 1, Kind.values().length, kind.label, ended, String.format(Locale.ROOT, "%.1f", waited / 20.0),
                    kind.roof ? "an Ort und Stelle" : "am Homepoint", String.format(Locale.ROOT, "%.1f", fromHome), String.format(Locale.ROOT, "%.1f", fromSpot));
            stage = 4;
            mark = tick;
            return;
        }
        if (waited > LAND_TIMEOUT_TICKS) {
            fail(server, tick, "keine Landung nach " + LAND_TIMEOUT_TICKS / 20 + " s (" + format(drone.position()) + ", "
                    + String.format(Locale.ROOT, "%.1f", horizontal(drone.position(), home)) + " m vom Homepoint)");
        }
    }

    private static double shelter(ServerLevel level, BlockPos at) {
        int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()) - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -ROOF_RADIUS; dx <= ROOF_RADIUS; dx++) {
            for (int dz = -ROOF_RADIUS; dz <= ROOF_RADIUS; dz++) {
                int x = at.getX() + dx;
                int z = at.getZ() + dz;
                level.setBlock(cursor.set(x, floor, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = floor + 1; y < floor + ROOF_HEIGHT; y++) {
                    level.setBlock(cursor.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
                level.setBlock(cursor.set(x, floor + ROOF_HEIGHT, z), Blocks.STONE.defaultBlockState(), 2);
            }
        }
        return floor + 1.0;
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static void pass(MinecraftServer server, int tick) {
        passed++;
        next(server, tick);
    }

    private static void fail(MinecraftServer server, int tick, String reason) {
        FAILURES.add((cycle + 1) + " (" + kind().label + "): " + reason);
        Constants.LOG.error("[Beitrittstest] Heimflug außer Reichweite {}/{} ({}): FAIL {}", cycle + 1, Kind.values().length, kind().label, reason);
        QuadcopterEntity drone = drone(server);
        if (drone != null) {
            PropwashApi.drones().autonomy().stop(drone);
            drone.setReturnPlan(null);
            drone.discard();
        }
        next(server, tick);
    }

    private static void next(MinecraftServer server, int tick) {
        droneId = null;
        ended = null;
        cycle++;
        stage = cycle < Kind.values().length ? 0 : 99;
        mark = tick;
        if (cycle < Kind.values().length) {
            return;
        }
        done = true;
        ServerPlayer owner = owner(server);
        if (owner != null) {
            owner.connection.onDisconnect(new DisconnectionDetails(Component.literal("Beitrittstest Heimflug außer Reichweite")));
        }
        Constants.LOG.info("[Beitrittstest] Heimflug außer Reichweite: {} von {} Durchgängen ohne Ausfall{}", passed, Kind.values().length,
                FAILURES.isEmpty() ? "" : ": " + String.join(" | ", FAILURES));
        try {
            Files.writeString(Services.PLATFORM.getGameDir().resolve(JoinTestEnv.STOP_FILE), passed + "/" + Kind.values().length + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            Constants.LOG.error("[Beitrittstest] Heimflug außer Reichweite: {} nicht geschrieben", JoinTestEnv.STOP_FILE, e);
        }
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
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
        return player;
    }

    private static QuadcopterEntity spawn(ServerLevel level, Vec3 pos) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        drone.snapTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
        level.addFreshEntity(drone);
        drone.resetTo(pos, new Quaternionf());
        return drone;
    }

    private static @Nullable QuadcopterEntity drone(MinecraftServer server) {
        UUID id = droneId;
        return id != null && server.overworld().getEntity(id) instanceof QuadcopterEntity quad && !quad.isRemoved() ? quad : null;
    }

    private static String format(Vec3 v) {
        return String.format(Locale.ROOT, "%.1f/%.1f/%.1f", v.x, v.y, v.z);
    }

    static final class Cruise implements FlightAssist {
        private final double targetX;
        private final double targetZ;
        private final double altitude;

        Cruise(double targetX, double targetZ, double altitude) {
            this.targetX = targetX;
            this.targetZ = targetZ;
            this.altitude = altitude;
        }

        @Override
        public void update(FlightState state, AssistOutput output) {
            output.holdAltitude(this.altitude);
            output.holdPosition(this.targetX, this.targetZ);
            output.maxSpeed(CRUISE_SPEED);
            output.label("RANGE");
        }
    }
}
