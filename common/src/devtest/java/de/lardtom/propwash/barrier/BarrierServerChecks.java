package de.lardtom.propwash.barrier;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.server.WorldRulesRights;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BarrierServerChecks {
    private static final int WALL_DX = 4;
    private static final double START_DX = 1.5;
    private static final double STEP = 4.0;
    private static String summary = "";

    private BarrierServerChecks() {
    }

    public static String summary() {
        return summary;
    }

    public static @Nullable String check(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getRespawnData().pos();
        BlockPos o = spawn.offset(-40, 24, 40);
        level.getChunk(o.getX() >> 4, o.getZ() >> 4);
        level.getChunk((o.getX() + 16) >> 4, (o.getZ() + 8) >> 4);
        Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        List<Entity> made = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        try {
            for (BarrierKind kind : BarrierKind.values()) {
                int z = kind == BarrierKind.DRONE_PASS ? 0 : 6;
                BlockState barrier = BarrierModule.of(kind).block().get().defaultBlockState();
                for (int dy = -1; dy <= 3; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dx = -1; dx <= WALL_DX + 6; dx++) {
                            BlockPos pos = o.offset(dx, dy, z + dz);
                            original.putIfAbsent(pos.immutable(), level.getBlockState(pos));
                            BlockState state = dy == -1 ? Blocks.STONE.defaultBlockState() : dx == WALL_DX ? barrier : Blocks.AIR.defaultBlockState();
                            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                        }
                    }
                }
                String problem = lane(level, o.offset(0, 0, z), kind, made, notes);
                if (problem != null) {
                    return problem;
                }
            }
            if (!BarrierModule.DRONE_PASS.block().get().defaultBlockState().getShape(level, o, CollisionContext.empty()).isEmpty()) {
                return "Durchlass mit Umriss ohne Spieler";
            }
            for (BarrierKind kind : BarrierKind.values()) {
                Block block = BarrierModule.of(kind).block().get();
                if (!(block.defaultBlockState().getDestroySpeed(level, o) < 0.0F) || block.getLootTable().isPresent()) {
                    return kind.path() + ": abbaubar oder mit Beute";
                }
            }
            summary = String.join(", ", notes);
            return null;
        } finally {
            for (Entity entity : made) {
                entity.discard();
            }
            List<Map.Entry<BlockPos, BlockState>> entries = List.copyOf(original.entrySet());
            for (int i = entries.size() - 1; i >= 0; i--) {
                level.setBlock(entries.get(i).getKey(), entries.get(i).getValue(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static @Nullable String lane(ServerLevel level, BlockPos o, BarrierKind kind, List<Entity> made, List<String> notes) {
        String name = kind == BarrierKind.DRONE_PASS ? "Durchlass" : "Sperre";
        double wall = o.getX() + WALL_DX;
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        Entity stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
        Entity pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
        Entity zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (drone == null || stand == null || pig == null || zombie == null) {
            return name + ": Entity nicht erzeugt";
        }
        drone.setBuild(DroneBuild.FREESTYLE);
        Entity item = new ItemEntity(level, 0.0, 0.0, 0.0, new ItemStack(Items.APPLE));
        Entity arrow = new Arrow(level, 0.0, 0.0, 0.0, new ItemStack(Items.ARROW), null);
        Entity guest = WorldRulesRights.guest(level.getServer());
        List<Entity> entities = List.of(drone, guest, stand, pig, zombie, item, arrow);
        made.addAll(entities);
        int stopped = 0;
        int passed = 0;
        for (Entity entity : entities) {
            boolean solid = kind.solidFor(entity instanceof QuadcopterEntity ? Collider.DRONE : Collider.ENTITY);
            String who = entity == guest ? "Spieler" : entity.getType().getDescription().getString();
            entity.snapTo(o.getX() + START_DX, o.getY(), o.getZ() + 0.5, -90.0F, 0.0F);
            entity.setDeltaMovement(Vec3.ZERO);
            double half = entity.getBbWidth() / 2.0;
            AABB inside = entity.getBoundingBox().move(wall + 0.5 - entity.getX(), 0.0, 0.0);
            boolean free = level.noCollision(entity, inside);
            if (free == solid) {
                return String.format(Locale.ROOT, "%s: %s %s in der Wand frei", name, who, free ? "ist" : "ist nicht");
            }
            if (entity instanceof Arrow) {
                Vec3 from = new Vec3(o.getX() + START_DX, o.getY() + 1.5, o.getZ() + 0.5);
                HitResult hit = level.clip(new ClipContext(from, from.add(STEP, 0.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
                boolean blocked = hit.getType() == HitResult.Type.BLOCK;
                if (blocked != solid) {
                    return name + ": Pfeil-Strahl " + (blocked ? "trifft" : "verfehlt") + " die Wand";
                }
            } else {
                entity.move(MoverType.SELF, new Vec3(STEP, 0.0, 0.0));
                double front = entity.getX() + half;
                if (solid && Math.abs(front - wall) > 0.02) {
                    return String.format(Locale.ROOT, "%s: %s nicht an der Wand aufgehalten, Front %.3f, Wand %.0f", name, who, front, wall);
                }
                if (!solid && Math.abs(entity.getX() - (o.getX() + START_DX + STEP)) > 1.0E-6) {
                    return String.format(Locale.ROOT, "%s: %s nicht durchgegangen, x %.3f", name, who, entity.getX() - o.getX());
                }
            }
            if (solid) {
                stopped++;
            } else {
                passed++;
            }
        }
        Vec3 from = new Vec3(o.getX() + START_DX, o.getY() + 1.5, o.getZ() + 0.5);
        Vec3 to = from.add(STEP, 0.0, 0.0);
        boolean sight = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, DroneBarriers.DRONE_SIGHT))
                .getType() == HitResult.Type.BLOCK;
        boolean empty = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
                .getType() == HitResult.Type.BLOCK;
        BlockState state = level.getBlockState(o.offset(WALL_DX, 1, 0));
        boolean droneShape = !DroneBarriers.droneShape(state, level, o.offset(WALL_DX, 1, 0)).isEmpty();
        if (sight != kind.solidFor(Collider.DRONE) || droneShape != sight || empty != kind.solidFor(Collider.NONE)) {
            return String.format(Locale.ROOT, "%s: Drohnen-Sicht %s, Form für die Drohne %s, ohne Entity %s", name, sight, droneShape, empty);
        }
        notes.add(String.format(Locale.ROOT, "%s hält %d auf, lässt %d durch", name, stopped, passed));
        return null;
    }
}
