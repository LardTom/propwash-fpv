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
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
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

public final class AutonomyOfflineJoinCheck {
    public static final String MARK = "[autonomy-offline]";
    public static final String LEAVE = MARK + " leave ";
    public static final String RESULT = MARK + " result ";
    public static final Identifier CRUISE = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "offline_cruise");
    public static final int DEFAULT_CYCLES = 20;
    public static final int SETTLE_TICKS = 40;
    public static final int MIN_CRUISE_TICKS = 30;
    public static final int MAX_CRUISE_TICKS = 20 * 20;
    public static final double HOME_AFTER_SECONDS = 2.5;
    public static final int HOME_LEAVE_TICKS = 10;
    public static final int LAND_TIMEOUT_TICKS = 20 * 45;
    public static final int FROZEN_TICKS = 10;
    public static final int RELEASE_TICKS = 10;
    public static final int REPORT_INTERVAL_TICKS = 40;
    public static final double HOME_TOLERANCE = 2.5;
    public static final int SITE_X = 2408;
    public static final int SITE_Z = 2406;
    public static final int SITE_SPACING = 160;
    private static final UUID SIM_OWNER = UUID.fromString("00000000-0000-4000-8000-0000000a0f01");
    private static final String SIM_NAME = "AutonomyOwner";
    private static final List<String> FAILURES = new ArrayList<>();
    private static final LongList UNFORCED = new LongArrayList();
    private static boolean initialized;
    private static int cycle;
    private static int stage;
    private static int mark;
    private static int lastTickCount;
    private static int stillTicks;
    private static int homeSince = -1;
    private static int passed;
    private static boolean done;
    private static @Nullable UUID ownerId;
    private static @Nullable UUID droneId;
    private static @Nullable AutonomyEnd ended;
    private static Kind kind = Kind.CROSS;
    private static Vec3 start = Vec3.ZERO;
    private static double groundY;

    private AutonomyOfflineJoinCheck() {
    }

    enum Kind {
        CROSS(6.0, 0.0, 6.0, 15, "Kante"),
        CORNER(6.0, 4.25, 4.25, 15, "Ecke"),
        LONG(24.0, 0.0, 2.0, 25, "lange Landung"),
        HOME(6.0, 0.0, 6.0, 25, "Heimflug");

        final double takeoff;
        final double vx;
        final double vz;
        final int awaySeconds;
        final String label;

        Kind(double takeoff, double vx, double vz, int awaySeconds, String label) {
            this.takeoff = takeoff;
            this.vx = vx;
            this.vz = vz;
            this.awaySeconds = awaySeconds;
            this.label = label;
        }
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashEvents.AUTONOMY_ENDED.register((drone, owner, id, reason) -> {
            if (CRUISE.equals(id) && drone.getUUID().equals(droneId)) {
                ended = reason;
            }
        });
    }

    public static int cycles() {
        int value = JoinTestEnv.autonomyOffline();
        return value > 1 ? value : DEFAULT_CYCLES;
    }

    static void tick(MinecraftServer server, int tick) {
        if (done) {
            ServerPlayer owner = owner(server);
            if (owner != null && !isSim(owner) && tick % REPORT_INTERVAL_TICKS == 0) {
                owner.sendSystemMessage(Component.literal(summary()));
            }
            return;
        }
        try {
            step(server, tick);
        } catch (RuntimeException e) {
            Constants.LOG.error("[Beitrittstest] Autonomie ohne Besitzer: Durchgang {} Stufe {} fehlgeschlagen", cycle + 1, stage, e);
            fail(server, tick, "Ausnahme " + e);
        }
    }

    private static void step(MinecraftServer server, int tick) {
        int waited = tick - mark;
        switch (stage) {
            case 0 -> {
                ServerPlayer owner = owner(server);
                if (owner == null) {
                    if (!JoinTestEnv.autonomyOfflineSim()) {
                        mark = tick;
                        return;
                    }
                    owner = joinSim(server);
                }
                if (!owner.getUUID().equals(ownerId)) {
                    ownerId = owner.getUUID();
                    mark = tick;
                    return;
                }
                if (waited < SETTLE_TICKS) {
                    return;
                }
                kind = Kind.values()[cycle % Kind.values().length];
                ServerLevel level = server.overworld();
                int x = SITE_X + SITE_SPACING * cycle;
                int z = SITE_Z + 16 * (cycle % 3);
                level.getChunk(x >> 4, z >> 4);
                double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                owner.teleportTo(level, x + 0.5, y, z + 0.5, Set.<Relative>of(), 0.0F, 10.0F, true);
                if (isSim(owner)) {
                    level.getChunkSource().move(owner);
                }
                stage = 1;
                mark = tick;
            }
            case 1 -> {
                ServerPlayer owner = requireOwner(server);
                if (waited < SETTLE_TICKS) {
                    return;
                }
                QuadcopterEntity drone = spawn(owner);
                droneId = drone.getUUID();
                ended = null;
                start = drone.position();
                groundY = drone.getY();
                Cruise cruise = new Cruise(drone.getY() + kind.takeoff, kind.vx, kind.vz, kind == Kind.HOME ? HOME_AFTER_SECONDS : -1.0);
                homeSince = -1;
                AutonomyResult result = PropwashApi.drones().autonomy().start(drone, owner, cruise,
                        AutonomyOptions.of(CRUISE).label("CRUISE").takeoff(kind.takeoff));
                Constants.LOG.info("[Beitrittstest] Autonomie ohne Besitzer {}/{} ({}): Drohne {} bei {}, Flug {} (Start {} m, {} m/s)",
                        cycle + 1, cycles(), kind.label, drone.getId(), format(drone.position()), result, kind.takeoff,
                        String.format(Locale.ROOT, "%.1f", Math.hypot(kind.vx, kind.vz)));
                if (!result.started()) {
                    fail(server, tick, "Flug startet nicht: " + result);
                    return;
                }
                stage = 2;
                mark = tick;
            }
            case 2 -> {
                ServerPlayer owner = requireOwner(server);
                QuadcopterEntity drone = drone(server);
                if (drone == null) {
                    fail(server, tick, "Drohne vor dem Trennen verschwunden");
                    return;
                }
                if (ended != null && !(kind == Kind.HOME && ended == AutonomyEnd.STOPPED)) {
                    fail(server, tick, "Flug endet vor dem Trennen: " + ended);
                    return;
                }
                if (!leaveNow(drone, tick, waited)) {
                    if (waited > MAX_CRUISE_TICKS) {
                        fail(server, tick, "kein Zeitpunkt zum Trennen nach " + waited + " Ticks (" + format(drone.position()) + ")");
                    }
                    return;
                }
                if (kind == Kind.LONG) {
                    unforce(server.overworld());
                }
                double leftHeight = drone.getY() - groundY;
                lastTickCount = drone.tickCount;
                stillTicks = 0;
                Constants.LOG.info("[Beitrittstest] Autonomie ohne Besitzer {}/{} ({}): Besitzer {} trennt, Drohne bei {} ({} m über Grund, {} m/s, {}), {}",
                        cycle + 1, cycles(), kind.label, owner.getPlainTextName(), format(drone.position()), String.format(Locale.ROOT, "%.2f", leftHeight),
                        String.format(Locale.ROOT, "%.2f", drone.getDeltaMovement().length() * 20.0),
                        AddonAutonomy.active(drone).isPresent() ? "Add-on-Flug" : "Heimflug " + AutonomousReturns.phase(drone), chunkState(drone));
                if (isSim(owner)) {
                    owner.connection.onDisconnect(new DisconnectionDetails(Component.literal("Beitrittstest Autonomie ohne Besitzer")));
                } else {
                    owner.sendSystemMessage(Component.literal(LEAVE + (cycle + 1) + "/" + cycles() + " away " + kind.awaySeconds));
                }
                stage = 3;
                mark = tick;
            }
            case 3 -> {
                if (owner(server) != null) {
                    if (waited > 20 * 20) {
                        fail(server, tick, "Besitzer trennt nicht");
                    }
                    return;
                }
                QuadcopterEntity drone = drone(server);
                if (drone != null) {
                    lastTickCount = drone.tickCount;
                }
                stage = 4;
                mark = tick;
            }
            case 4 -> observe(server, tick, waited);
            case 5 -> {
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

    private static boolean leaveNow(QuadcopterEntity drone, int tick, int waited) {
        if (waited < MIN_CRUISE_TICKS) {
            return false;
        }
        if (kind == Kind.HOME) {
            if (AutonomousReturns.phase(drone) == null) {
                return false;
            }
            if (homeSince < 0) {
                homeSince = tick;
            }
            return tick - homeSince >= HOME_LEAVE_TICKS;
        }
        if (kind == Kind.LONG) {
            return drone.getY() - groundY > kind.takeoff - 1.0 && AddonAutonomy.active(drone).isPresent();
        }
        if (!AddonAutonomy.active(drone).isPresent() || drone.getY() - groundY < kind.takeoff - 1.0) {
            return false;
        }
        double threshold = 16.0 - leadMetres();
        double xOff = drone.getX() - Math.floor(drone.getX() / 16.0) * 16.0;
        double zOff = drone.getZ() - Math.floor(drone.getZ() / 16.0) * 16.0;
        return kind == Kind.CORNER ? xOff >= threshold - 1.0 && zOff >= threshold - 1.0 : zOff >= threshold;
    }

    private static double leadMetres() {
        double lead = 0.3 + 0.3 * ((cycle / Kind.values().length) % 5);
        return JoinTestEnv.autonomyOfflineSim() ? lead : lead + 2.0;
    }

    private static void observe(MinecraftServer server, int tick, int waited) {
        QuadcopterEntity drone = drone(server);
        if (drone == null) {
            fail(server, tick, "Drohne " + String.format(Locale.ROOT, "%.2f", waited / 20.0) + " s nach dem Trennen entladen");
            return;
        }
        double height = drone.getY() - groundY;
        if (owner(server) != null) {
            fail(server, tick, "Besitzer " + String.format(Locale.ROOT, "%.1f", waited / 20.0) + " s nach dem Trennen vor der Landung wieder verbunden");
            return;
        }
        if (drone.tickCount == lastTickCount) {
            stillTicks++;
            if (stillTicks == 2) {
                Constants.LOG.warn("[Beitrittstest] Autonomie ohne Besitzer {}/{} ({}): Drohne tickt nicht ({} Ticks nach dem Trennen, Höhe {} m), {}",
                        cycle + 1, cycles(), kind.label, waited, String.format(Locale.ROOT, "%.2f", height), chunkState(drone));
            }
            if (stillTicks > FROZEN_TICKS) {
                fail(server, tick, "Drohne tickt " + stillTicks + " Ticks nicht (" + String.format(Locale.ROOT, "%.2f", waited / 20.0) + " s nach dem Trennen, "
                        + String.format(Locale.ROOT, "%.2f", height) + " m über Grund, " + chunkState(drone) + ")");
                return;
            }
        } else {
            stillTicks = 0;
            lastTickCount = drone.tickCount;
        }
        boolean flying = AutonomousReturns.flying(drone);
        if (!flying && drone.returnPlan() == null && !drone.isArmed() && drone.onGround()) {
            AutonomyEnd expected = kind == Kind.HOME ? AutonomyEnd.STOPPED : AutonomyEnd.OWNER_OFFLINE;
            if (ended != expected) {
                fail(server, tick, "Ende " + ended + " statt " + expected);
                return;
            }
            double home = Math.hypot(drone.getX() - start.x, drone.getZ() - start.z);
            if (kind == Kind.HOME && home > HOME_TOLERANCE) {
                fail(server, tick, "Heimflug landet " + String.format(Locale.ROOT, "%.1f", home) + " m vom Startplatz");
                return;
            }
            Constants.LOG.info("[Beitrittstest] Autonomie ohne Besitzer {}/{} ({}): {}, gelandet nach {} s {} ({} m vom Startplatz), Drohne tickte ohne Lücke, "
                            + "Dimension aktiv bis zur Landung", cycle + 1, cycles(), kind.label, ended, String.format(Locale.ROOT, "%.1f", waited / 20.0),
                    kind == Kind.HOME ? "am Startplatz" : "an Ort und Stelle", String.format(Locale.ROOT, "%.1f", home));
            restoreForced(server.overworld());
            stage = 5;
            mark = tick;
            return;
        }
        if (!flying && drone.isArmed() && waited > 40) {
            fail(server, tick, "Drohne scharf ohne Autopilot (" + String.format(Locale.ROOT, "%.2f", height) + " m über Grund)");
            return;
        }
        if (waited > LAND_TIMEOUT_TICKS) {
            fail(server, tick, "keine Landung nach " + LAND_TIMEOUT_TICKS / 20 + " s (" + String.format(Locale.ROOT, "%.2f", height)
                    + " m über Grund, Ende " + ended + ")");
        }
    }

    private static void pass(MinecraftServer server, int tick) {
        passed++;
        next(server, tick, drone(server));
    }

    private static void fail(MinecraftServer server, int tick, String reason) {
        FAILURES.add((cycle + 1) + " (" + kind.label + "): " + reason);
        Constants.LOG.error("[Beitrittstest] Autonomie ohne Besitzer {}/{} ({}): FAIL {}", cycle + 1, cycles(), kind.label, reason);
        restoreForced(server.overworld());
        QuadcopterEntity drone = drone(server);
        if (drone != null) {
            PropwashApi.drones().autonomy().stop(drone);
        }
        ServerPlayer owner = owner(server);
        if (owner != null && isSim(owner)) {
            owner.connection.onDisconnect(new DisconnectionDetails(Component.literal("Beitrittstest Autonomie ohne Besitzer")));
        }
        next(server, tick, drone);
    }

    private static void next(MinecraftServer server, int tick, @Nullable QuadcopterEntity drone) {
        if (drone != null && !drone.isRemoved()) {
            drone.discard();
        }
        droneId = null;
        ended = null;
        cycle++;
        stage = 0;
        mark = tick;
        ownerId = null;
        if (cycle < cycles()) {
            return;
        }
        done = true;
        Constants.LOG.info("[Beitrittstest] Autonomie ohne Besitzer: {} von {} Durchgängen ohne Ausfall{}", passed, cycles(),
                FAILURES.isEmpty() ? "" : ": " + String.join(" | ", FAILURES));
        if (JoinTestEnv.autonomyOfflineSim()) {
            try {
                Files.writeString(Services.PLATFORM.getGameDir().resolve(JoinTestEnv.STOP_FILE), summary() + "\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                Constants.LOG.error("[Beitrittstest] Autonomie ohne Besitzer: {} nicht geschrieben", JoinTestEnv.STOP_FILE, e);
            }
        }
    }

    public static String summary() {
        return RESULT + passed + "/" + cycles();
    }

    public static boolean done() {
        return done;
    }

    public static @Nullable String failure() {
        if (!done) {
            return "nach " + cycle + " von " + cycles() + " Durchgängen nicht fertig";
        }
        return FAILURES.isEmpty() ? null : String.join(" | ", FAILURES);
    }

    private static void unforce(ServerLevel level) {
        UNFORCED.clear();
        UNFORCED.addAll(level.getForceLoadedChunks());
        for (int i = 0; i < UNFORCED.size(); i++) {
            ChunkPos pos = ChunkPos.unpack(UNFORCED.getLong(i));
            level.setChunkForced(pos.x(), pos.z(), false);
        }
    }

    private static void restoreForced(ServerLevel level) {
        for (int i = 0; i < UNFORCED.size(); i++) {
            ChunkPos pos = ChunkPos.unpack(UNFORCED.getLong(i));
            level.setChunkForced(pos.x(), pos.z(), true);
        }
        UNFORCED.clear();
    }

    private static @Nullable ServerPlayer owner(MinecraftServer server) {
        if (JoinTestEnv.autonomyOfflineSim()) {
            ServerPlayer sim = server.getPlayerList().getPlayer(SIM_OWNER);
            return sim == null || sim.hasDisconnected() ? null : sim;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.hasDisconnected() && !isSim(player)) {
                return player;
            }
        }
        return null;
    }

    private static ServerPlayer requireOwner(MinecraftServer server) {
        ServerPlayer owner = owner(server);
        if (owner == null || !owner.getUUID().equals(ownerId)) {
            throw new IllegalStateException("Besitzer vor dem Trennen nicht mehr verbunden");
        }
        return owner;
    }

    private static boolean isSim(ServerPlayer player) {
        return SIM_OWNER.equals(player.getUUID());
    }

    private static ServerPlayer joinSim(MinecraftServer server) {
        GameProfile profile = new GameProfile(SIM_OWNER, SIM_NAME);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
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
        BlockPos at = BlockPos.containing(owner.getX(), owner.getY(), owner.getZ() + 2.0);
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
        return String.format(Locale.ROOT, "Chunk %d/%d bei %.1f/%.1f, Ladestufe %d, Simulation %d, tickend %s, Tickets %d, Dimension aktiv %s",
                chunk.x(), chunk.z(), drone.getX() - chunk.getMinBlockX(), drone.getZ() - chunk.getMinBlockZ(), distance.getChunkLevel(key, false),
                distance.getChunkLevel(key, true), level.isPositionEntityTicking(drone.blockPosition()), RangeManager.ticketCount(),
                level.getChunkSource().hasActiveTickets());
    }

    private static String format(Vec3 v) {
        return String.format(Locale.ROOT, "%.1f/%.1f/%.1f", v.x, v.y, v.z);
    }

    static final class Cruise implements FlightAssist {
        private final double altitude;
        private final double vx;
        private final double vz;
        private final double homeAfter;
        private double firstTime = Double.NaN;

        Cruise(double altitude, double vx, double vz, double homeAfter) {
            this.altitude = altitude;
            this.vx = vx;
            this.vz = vz;
            this.homeAfter = homeAfter;
        }

        @Override
        public void update(FlightState state, AssistOutput output) {
            if (Double.isNaN(this.firstTime)) {
                this.firstTime = state.time();
            }
            output.holdAltitude(this.altitude);
            output.velocity(this.vx, this.vz);
            output.maxSpeed(Math.hypot(this.vx, this.vz) + 1.0);
            output.label("CRUISE");
            if (this.homeAfter > 0.0 && state.time() - this.firstTime >= this.homeAfter) {
                output.requestReturnHome();
            }
        }
    }
}
