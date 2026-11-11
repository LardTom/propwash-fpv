package de.lardtom.propwash.audio;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.accessory.DispenseResult;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.example.ExampleDispenser;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.server.ServerModule;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import java.util.Optional;

public final class DroneSoundStage {
    public static final int FAR_BLOCKS = 200;
    public static final int NEAR_BLOCKS = 6;
    public static final int CHUNK_HOLD = 3;
    public static final double LIFT = 1.0;
    public static final double EXPLOSION_AHEAD = 8.0;
    public static final double EXPLOSION_UP = 2.0;
    public static final float EXPLOSION_POWER = 0.5F;
    public static final SoundEvent MARK = SoundEvents.EXPERIENCE_ORB_PICKUP;
    public static final int DISPENSER_EVENT = LevelEvent.SOUND_DISPENSER_PROJECTILE_LAUNCH;
    private static final Quaternionfc FACING_EAST = new Quaternionf().rotationY((float) (-Math.PI / 2.0));

    private DroneSoundStage() {
    }

    public enum Phase {
        FAR(1.7F, 1.6F, 0.6F),
        NEAR(1.3F, 1.2F, 0.7F);

        public final float soundPitch;
        public final float entityPitch;
        public final float excludedPitch;

        Phase(float soundPitch, float entityPitch, float excludedPitch) {
            this.soundPitch = soundPitch;
            this.entityPitch = entityPitch;
            this.excludedPitch = excludedPitch;
        }
    }

    public record Emission(Phase phase, Vec3 drone, Vec3 body, DispenseResult.Status fired, Vec3 explosion) {
        public double distance() {
            return this.drone.distanceTo(this.body);
        }
    }

    public static boolean available() {
        return AccessoryType.byId(ExampleDispenser.DISPENSER).isPresent();
    }

    public static QuadcopterEntity start(ServerPlayer player) {
        ServerLevel level = player.level();
        AccessoryType dispenser = AccessoryType.byId(ExampleDispenser.DISPENSER).orElseThrow(() -> new IllegalStateException("Beispiel-Werfer fehlt"));
        player.setGameMode(GameType.CREATIVE);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        hold(level, spot(player, Phase.FAR));
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        Vec3 near = spot(player, Phase.NEAR);
        drone.snapTo(near.x, near.y, near.z, -90.0F, 0.0F);
        drone.setAttitude(FACING_EAST);
        drone.setBuild(DroneBuild.FREESTYLE.withAccessory(dispenser));
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.getInventory().setItem(0, remote);
        player.getInventory().setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (!result.success()) {
            drone.discard();
            throw new IllegalStateException("FPV verweigert: " + result);
        }
        return drone;
    }

    public static Vec3 spot(ServerPlayer player, Phase phase) {
        return ground(player.level(), player.getBlockX() + (phase == Phase.FAR ? FAR_BLOCKS : NEAR_BLOCKS), player.getBlockZ());
    }

    public static boolean stepToward(QuadcopterEntity drone, Vec3 target, double step) {
        Vec3 from = drone.position();
        double dx = target.x - from.x;
        double dz = target.z - from.z;
        double distance = Math.hypot(dx, dz);
        if (distance <= step) {
            drone.resetTo(target, FACING_EAST);
            return true;
        }
        Vec3 next = ground((ServerLevel) drone.level(), (int) Math.floor(from.x + dx / distance * step), (int) Math.floor(from.z + dz / distance * step));
        drone.resetTo(next, FACING_EAST);
        return false;
    }

    private static Vec3 ground(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + LIFT, z + 0.5);
    }

    public static Emission emit(ServerPlayer player, QuadcopterEntity drone, Phase phase) {
        ServerLevel level = player.level();
        Vec3 at = drone.position();
        DispenseResult.Status fired = fire(player, drone);
        level.playSound(null, at.x, at.y + 0.5, at.z, MARK, SoundSource.BLOCKS, 1.0F, phase.soundPitch);
        level.playSound(null, drone, MARK, SoundSource.NEUTRAL, 1.0F, phase.entityPitch);
        level.playSound(player, at.x, at.y + 0.5, at.z, MARK, SoundSource.BLOCKS, 1.0F, phase.excludedPitch);
        Vec3 blast = at.add(EXPLOSION_AHEAD, EXPLOSION_UP, 0.0);
        level.explode(null, blast.x, blast.y, blast.z, EXPLOSION_POWER, Level.ExplosionInteraction.NONE);
        return new Emission(phase, at, player.position(), fired, blast);
    }

    public static void release(ServerPlayer player, QuadcopterEntity drone) {
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.getInventory().setItem(0, ItemStack.EMPTY);
        drone.discard();
    }

    private static DispenseResult.Status fire(ServerPlayer player, QuadcopterEntity drone) {
        Optional<AccessoryInventory> inventory = PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT);
        if (inventory.isEmpty()) {
            return DispenseResult.Status.REFUSED;
        }
        inventory.get().set(0, new ItemStack(Items.ARROW));
        int before = ExampleDispenser.SHOTS.get();
        boolean pressed = PropwashApi.accessories().triggerAction(drone, ExampleDispenser.FIRE, true, player);
        PropwashApi.accessories().triggerAction(drone, ExampleDispenser.FIRE, false, player);
        if (!pressed || ExampleDispenser.SHOTS.get() == before) {
            return DispenseResult.Status.REFUSED;
        }
        DispenseResult result = ExampleDispenser.LAST_RESULT.get();
        result.entity().ifPresent(Entity::discard);
        return result.status();
    }

    private static void hold(ServerLevel level, Vec3 at) {
        BlockPos block = BlockPos.containing(at);
        ChunkPos chunk = new ChunkPos(block.getX() >> 4, block.getZ() >> 4);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.getChunk(chunk.x() + dx, chunk.z() + dz);
            }
        }
        level.getChunkSource().addTicketWithRadius(ServerModule.DRONE_VIEW_TICKET.get(), chunk, CHUNK_HOLD);
    }
}
