package de.lardtom.propwash.rescue;

import com.mojang.authlib.GameProfile;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.RadioLink;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.rescue.net.HomeResetC2S;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class HomeResetChecks {
    public static final UUID PILOT_ID = UUID.fromString("00000000-0000-4000-8000-0000000d7a73");
    public static final UUID OTHER_ID = UUID.fromString("00000000-0000-4000-8000-0000000d7a74");
    private static final double FAR = 1000.0;
    private static final double PRELOADED = 700.0;
    private static final int PRELOAD_RADIUS = HomeResets.TICKET_RADIUS + 1;
    public static final int FINISH_DELAY_TICKS = 10;

    private HomeResetChecks() {
    }

    public static @Nullable String check(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos base = level.getRespawnData().pos().above(40);
        ServerPlayer pilot = player(server, PILOT_ID, "Reset_Pilot");
        ServerPlayer other = player(server, OTHER_ID, "Reset_Other");
        int rule = WorldRules.get(server, WorldRuleSpecs.ALLOW_RESET);
        QuadcopterEntity drone = null;
        try {
            drone = spawn(level, base);
            return run(server, level, drone, pilot, other);
        } catch (RuntimeException e) {
            return "Ausnahme: " + e;
        } finally {
            WorldRules.set(server, WorldRuleSpecs.ALLOW_RESET, rule);
            if (drone != null) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                if (!drone.isRemoved()) {
                    drone.discard();
                }
            }
        }
    }

    public static String summary() {
        return "Reset nur für den Piloten, Welt-Regel aus verweigert, Paket setzt die Drohne gerade und entschärft auf den Homepoint,"
                + " Sperrzeit, ohne Punkt, mit Punkt in anderer Dimension und außer Funkreichweite abgelehnt, ungeladener ferner Homepoint ("
                + Math.round(FAR) + " Blöcke) wartet mit Ticket, Drohne bleibt sichtbar";
    }

    public static String finishSummary() {
        return "geladener ferner Homepoint (" + Math.round(PRELOADED) + " Blöcke): Drohne sofort dort, gerade, entschärft, im Level";
    }

    public static @Nullable String finish(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos base = level.getRespawnData().pos().above(40);
        ServerPlayer pilot = player(server, PILOT_ID, "Reset_Pilot");
        QuadcopterEntity drone = null;
        try {
            drone = spawn(level, base);
            pilot.snapTo(drone.getX() + 1.0, drone.getY(), drone.getZ());
            DronePiloting.StartResult started = DronePiloting.tryStart(pilot, drone, PilotMode.LINE_OF_SIGHT);
            if (started != DronePiloting.StartResult.STARTED) {
                return "Pilot darf nicht fliegen: " + started;
            }
            Vec3 far = preloaded(base);
            pilot.snapTo(far.x + 1.0, far.y, far.z);
            if (!level.isPositionEntityTicking(BlockPos.containing(far))) {
                return "vorgeladener Chunk nicht Entity-tickend";
            }
            drone.setHomePoint(new HomePoint(far.x, far.y, far.z, HomePointCodecs.dimension(level), level.getGameTime(), HomePoint.Source.MANUAL));
            HomeResetRules.Outcome outcome = HomeResets.reset(pilot, drone);
            String problem = atTarget(drone, far);
            if (outcome != HomeResetRules.Outcome.HOME || problem != null || level.getEntity(drone.getUUID()) != drone) {
                return "vorgeladener ferner Homepoint: " + outcome + ", " + problem + ", im Level " + (level.getEntity(drone.getUUID()) == drone);
            }
            return null;
        } catch (RuntimeException e) {
            return "Ausnahme: " + e;
        } finally {
            if (drone != null) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                if (!drone.isRemoved()) {
                    drone.discard();
                }
            }
        }
    }

    public static boolean finishReady(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos target = BlockPos.containing(preloaded(level.getRespawnData().pos().above(40)));
        if (!level.isPositionEntityTicking(target)) {
            return false;
        }
        ChunkPos center = ChunkPos.containing(target);
        for (int dx = -PRELOAD_RADIUS; dx <= PRELOAD_RADIUS; dx++) {
            for (int dz = -PRELOAD_RADIUS; dz <= PRELOAD_RADIUS; dz++) {
                if (level.getChunkSource().getChunkNow(center.x() + dx, center.z() + dz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Vec3 preloaded(BlockPos base) {
        return new Vec3(base.getX() + 0.5 + PRELOADED, base.getY() + 0.25, base.getZ() + 0.5);
    }

    private static @Nullable String run(MinecraftServer server, ServerLevel level, QuadcopterEntity drone, ServerPlayer pilot,
                                        ServerPlayer other) {
        String dimension = HomePointCodecs.dimension(level);
        pilot.snapTo(drone.getX() + 1.0, drone.getY(), drone.getZ());
        DronePiloting.StartResult started = DronePiloting.tryStart(pilot, drone, PilotMode.LINE_OF_SIGHT);
        if (started != DronePiloting.StartResult.STARTED) {
            return "Pilot darf nicht fliegen: " + started;
        }
        Vec3 home = drone.position();
        drone.setHomePoint(new HomePoint(home.x, home.y, home.z, dimension, level.getGameTime(), HomePoint.Source.ARM));
        Vec3 away = home.add(0.0, 6.0, 0.0);
        drone.resetTo(away, new Quaternionf().rotationY(0.8F).rotateX(2.6F));
        drone.setArmed(true);

        HomeResetRules.Outcome outcome = HomeResets.reset(other, drone);
        if (outcome != HomeResetRules.Outcome.NOT_PILOT || !drone.position().equals(away)) {
            return "fremder Spieler: " + outcome + ", Drohne " + drone.position();
        }
        WorldRules.set(server, WorldRuleSpecs.ALLOW_RESET, 0);
        outcome = HomeResets.reset(pilot, drone);
        if (outcome != HomeResetRules.Outcome.DISABLED || !drone.position().equals(away)) {
            return "Welt-Regel aus: " + outcome + ", Drohne " + drone.position();
        }
        WorldRules.set(server, WorldRuleSpecs.ALLOW_RESET, 1);
        int epoch = drone.resetEpoch();
        HomeResets.onRequest(new HomeResetC2S(drone.getId()), new ServerPayloadContext(pilot));
        String problem = atTarget(drone, home);
        if (problem != null || drone.resetEpoch() != epoch + 1) {
            return "Paket: " + (problem != null ? problem : "Epoche " + drone.resetEpoch() + " statt " + (epoch + 1));
        }
        drone.resetTo(away, new Quaternionf());
        outcome = HomeResets.reset(pilot, drone);
        if (outcome != HomeResetRules.Outcome.COOLDOWN || !drone.position().equals(away)) {
            return "Sperrzeit: " + outcome + ", Drohne " + drone.position();
        }

        drone.markHomeReset(Long.MIN_VALUE);
        drone.setHomePoint(null);
        outcome = HomeResets.reset(pilot, drone);
        if (outcome != HomeResetRules.Outcome.NO_HOME) {
            return "ohne Homepoint: " + outcome;
        }
        drone.setHomePoint(new HomePoint(0.0, 64.0, 0.0, "minecraft:the_nether", level.getGameTime(), HomePoint.Source.MANUAL));
        outcome = HomeResets.reset(pilot, drone);
        if (outcome != HomeResetRules.Outcome.OTHER_DIMENSION || !drone.position().equals(away)) {
            return "Homepoint im Nether: " + outcome + ", Drohne " + drone.position();
        }

        Vec3 beyond = home.add(RadioLink.RELEASE_RANGE * 2.0, 0.0, 0.0);
        drone.setHomePoint(new HomePoint(beyond.x, beyond.y, beyond.z, dimension, level.getGameTime(), HomePoint.Source.MANUAL));
        outcome = HomeResets.reset(pilot, drone);
        if (outcome != HomeResetRules.Outcome.OUT_OF_RANGE || !drone.position().equals(away)) {
            return "Homepoint außer Funkreichweite: " + outcome + ", Drohne " + drone.position();
        }

        Vec3 far = home.add(FAR, 0.0, 0.0);
        pilot.snapTo(far.x + 1.0, far.y, far.z);
        if (level.isPositionEntityTicking(BlockPos.containing(far))) {
            return "ferner Chunk vor dem Test schon Entity-tickend";
        }
        drone.setHomePoint(new HomePoint(far.x, far.y, far.z, dimension, level.getGameTime(), HomePoint.Source.MANUAL));
        drone.markHomeReset(Long.MIN_VALUE);
        outcome = HomeResets.reset(pilot, drone);
        if (outcome != HomeResetRules.Outcome.LOADING || !HomeResets.pending(drone) || !drone.position().equals(away)
                || level.getEntity(drone.getUUID()) != drone) {
            return "ungeladener ferner Homepoint: " + outcome + ", wartet " + HomeResets.pending(drone) + ", Drohne " + drone.position();
        }
        level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(BlockPos.containing(preloaded(
                level.getRespawnData().pos().above(40)))), PRELOAD_RADIUS);
        return null;
    }

    private static @Nullable String atTarget(QuadcopterEntity drone, Vec3 target) {
        Vector3f up = drone.orientation().transform(new Vector3f(0.0F, 1.0F, 0.0F));
        double dx = drone.getX() - target.x;
        double dz = drone.getZ() - target.z;
        if (dx * dx + dz * dz > 1.0E-6 || drone.getY() < target.y - 1.0E-6 || drone.getY() > target.y + HomeResetRules.CLEARANCE) {
            return "Drohne bei " + drone.position() + " statt " + target;
        }
        if (up.y < 0.999F) {
            return "Drohne nicht gerade: " + up;
        }
        if (drone.isArmed() || drone.getDeltaMovement().lengthSqr() > 0.0) {
            return "Drohne scharf oder in Bewegung";
        }
        return null;
    }

    private static QuadcopterEntity spawn(ServerLevel level, BlockPos base) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        drone.snapTo(base.getX() + 0.5, base.getY() + 0.25, base.getZ() + 0.5, 0.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        return drone;
    }

    private static ServerPlayer player(MinecraftServer server, UUID id, String name) {
        GameProfile profile = new GameProfile(id, name);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        return player;
    }
}
