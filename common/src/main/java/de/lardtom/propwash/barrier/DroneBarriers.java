package de.lardtom.propwash.barrier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public final class DroneBarriers {
    public static final CollisionContext DRONE_SIGHT = new Sight(Collider.DRONE);
    public static final CollisionContext COURSE = new Sight(Collider.COURSE);

    private DroneBarriers() {
    }

    public static boolean is(BlockState state) {
        return state.getBlock() instanceof DroneBarrierBlock;
    }

    public static VoxelShape droneShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getBlock() instanceof DroneBarrierBlock barrier ? barrier.droneShape() : state.getCollisionShape(level, pos);
    }

    public static boolean sturdyForCourse(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return is(state) || state.isFaceSturdy(level, pos, face);
    }

    public static CollisionContext sightOf(@Nullable Entity entity) {
        return Collider.ofType(entity == null ? null : entity.getClass()) == Collider.DRONE ? DRONE_SIGHT : CollisionContext.empty();
    }

    public static Collider collider(CollisionContext context) {
        if (context instanceof Sight sight) {
            return sight.collider;
        }
        if (context instanceof EntityCollisionContext entityContext) {
            Entity entity = entityContext.getEntity();
            return Collider.ofType(entity == null ? null : entity.getClass());
        }
        return Collider.NONE;
    }

    public static boolean outlines(CollisionContext context) {
        return context instanceof EntityCollisionContext entityContext && entityContext.getEntity() instanceof Player player && player.isCreative();
    }

    public static boolean reveals(Player player) {
        return player.isCreative() && player.isHolding(DroneBarriers::isItem);
    }

    public static boolean isItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof DroneBarrierBlock;
    }

    private static final class Sight extends EntityCollisionContext {
        private final Collider collider;

        private Sight(Collider collider) {
            super(false, false, -Double.MAX_VALUE, ItemStack.EMPTY, false, null);
            this.collider = collider;
        }

        @Override
        public boolean isAbove(VoxelShape shape, BlockPos pos, boolean defaultValue) {
            return defaultValue;
        }
    }
}
