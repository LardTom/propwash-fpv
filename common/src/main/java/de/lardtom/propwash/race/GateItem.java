package de.lardtom.propwash.race;

import de.lardtom.propwash.content.ItemHelp;
import de.lardtom.propwash.core.OrientedBox;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GateItem extends Item {
    public static final String ROTATE_KEY = "key.propwash.gate_rotate";
    public static final String FLIGHT_MODE_KEY = "key.propwash.drone_flight_mode";
    public static final double THROUGH_SOFT = 3.0;
    public static final double ATTACHED = 0.1;
    private final GateSize size;

    public GateItem(Item.Properties properties, GateSize size) {
        super(properties);
        this.size = size;
    }

    public GateSize size() {
        return this.size;
    }

    public static GatePlacement.Click click(BlockPlaceContext place, BlockPos clicked, Direction face, Vec3 hit, @Nullable Vec3 eye, float playerYaw) {
        Level level = place.getLevel();
        if (soft(level, clicked)) {
            BlockHitResult behind = behindSoft(level, clicked, face, hit, eye);
            if (behind == null) {
                Vec3 floor = Vec3.atBottomCenterOf(clicked);
                return new GatePlacement.Click(GateMount.Surface.FLOOR, clicked.getX(), clicked.getY(), clicked.getZ(), 0, 0, hit.x, hit.y, hit.z,
                        floor.x, floor.y, floor.z, playerYaw);
            }
            place = BlockPlaceContext.at(place, behind.getBlockPos(), behind.getDirection());
            clicked = behind.getBlockPos();
            face = behind.getDirection();
            hit = behind.getLocation();
        }
        BlockPos pos = place.getClickedPos();
        Vec3 base = Vec3.atBottomCenterOf(pos);
        GateMount.Surface surface = GatePlacement.surface(face.getStepY());
        double plane = GatePlacement.blockPlane(surface, clicked.getX(), clicked.getY(), clicked.getZ(), face.getStepX(), face.getStepZ(), base.y);
        if (surface != GateMount.Surface.FLOOR && !level.getBlockState(clicked).isFaceSturdy(level, clicked, face)) {
            plane = snapToPixel(surface == GateMount.Surface.CEILING ? hit.y : face.getStepX() != 0 ? hit.x : hit.z);
        }
        return new GatePlacement.Click(surface, clicked.getX(), clicked.getY(), clicked.getZ(), face.getStepX(), face.getStepZ(), hit.x, hit.y, hit.z,
                base.x, base.y, base.z, playerYaw, plane);
    }

    public static GatePlacement.Click click(UseOnContext context) {
        Player player = context.getPlayer();
        return click(new BlockPlaceContext(context), context.getClickedPos(), context.getClickedFace(), context.getClickLocation(),
                player == null ? null : player.getEyePosition(), context.getRotation());
    }

    public static boolean soft(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static @Nullable BlockHitResult behindSoft(Level level, BlockPos clicked, Direction face, Vec3 hit, @Nullable Vec3 eye) {
        BlockPos behind = clicked.relative(face.getOpposite());
        boolean wall = face != Direction.UP && level.getBlockState(behind).isFaceSturdy(level, behind, face);
        if (wall && attached(clicked, face, hit)) {
            return onFace(behind, face, hit);
        }
        BlockPos below = clicked.below();
        if (face != Direction.DOWN && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return onFace(below, Direction.UP, hit);
        }
        if (wall) {
            return onFace(behind, face, hit);
        }
        Vec3 direction = eye == null ? Vec3.ZERO : hit.subtract(eye);
        if (direction.lengthSqr() < 1.0E-8) {
            direction = new Vec3(-face.getStepX(), -face.getStepY(), -face.getStepZ());
        }
        Vec3 to = hit.add(direction.normalize().scale(THROUGH_SOFT));
        BlockHitResult result = level.clip(new ClipContext(hit, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return result.getType() == HitResult.Type.BLOCK && !soft(level, result.getBlockPos()) ? result : null;
    }

    private static boolean attached(BlockPos clicked, Direction face, Vec3 hit) {
        Direction.Axis axis = face.getAxis();
        double border = clicked.get(axis) + 0.5 - 0.5 * face.getAxisDirection().getStep();
        return Math.abs(hit.get(axis) - border) < ATTACHED;
    }

    private static BlockHitResult onFace(BlockPos support, Direction face, Vec3 hit) {
        Direction.Axis axis = face.getAxis();
        double plane = support.get(axis) + 0.5 + 0.5 * face.getAxisDirection().getStep();
        Vec3 location = new Vec3(axis == Direction.Axis.X ? plane : hit.x, axis == Direction.Axis.Y ? plane : hit.y, axis == Direction.Axis.Z ? plane : hit.z);
        return new BlockHitResult(location, face, support, false);
    }

    private static double snapToPixel(double value) {
        return Math.round(value * 16.0) / 16.0;
    }

    public static boolean insideWorld(Level level, GateGeometry geometry) {
        double[] range = new double[2];
        geometry.bounds((minX, minY, minZ, maxX, maxY, maxZ) -> {
            range[0] = minY;
            range[1] = maxY;
        });
        return range[0] >= level.getMinY() - 1.0E-6 && range[1] <= level.getMaxY() + 1 + 1.0E-6;
    }

    public GateGeometry placement(GatePlacement.Click click, GatePlacement.Preference preference) {
        return GatePlacement.resolve(click, preference, this.size);
    }

    public boolean allowed(GatePlacement.Click click) {
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        GateGeometry geometry = this.placement(click(context), GateMounting.preference(context.getPlayer(), level.isClientSide()));
        if (!insideWorld(level, geometry) || !free(level, geometry, GateStacking.placedBase(geometry), false)) {
            Player player = context.getPlayer();
            if (player != null && !level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("propwash.race.gate.blocked"));
            }
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        if (level instanceof ServerLevel serverLevel && this.spawn(serverLevel, geometry, stack, context.getPlayer(), 0.8F) == null) {
            return InteractionResult.FAIL;
        }
        stack.consume(1, context.getPlayer());
        return InteractionResult.SUCCESS;
    }

    public InteractionResult stackOn(GateEntity target, Player player, ItemStack stack, Vec3 location) {
        return this.stackOn(target, player, stack);
    }

    public InteractionResult stackOn(GateEntity target, Player player, ItemStack stack) {
        if (!player.mayBuild() || target.isRemoved()) {
            return InteractionResult.PASS;
        }
        Level level = target.level();
        if (!target.stackable()) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("propwash.race.gate.stack_standing_only"));
            }
            return InteractionResult.FAIL;
        }
        List<GateEntity> column = target.stack();
        GateEntity top = column.getLast();
        GateGeometry topGeometry = top.geometry();
        GateStacking.Refusal refusal = GateStacking.check(topGeometry, column.size(), this.size);
        if (refusal != GateStacking.Refusal.NONE) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(refusal == GateStacking.Refusal.TOO_WIDE
                        ? Component.translatable("propwash.race.gate.stack_too_wide")
                        : Component.translatable("propwash.race.gate.stack_too_high", GateStacking.MAX_STACK));
            }
            return InteractionResult.FAIL;
        }
        GateGeometry geometry = GateStacking.stackOn(topGeometry, this.size, GateStacking.stackYaw(top.facingYaw(), player.getYRot()));
        if (geometry.y() + geometry.height() > level.getMaxY() + 1 || !free(level, geometry, GateStacking.Base.STACKED, true)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("propwash.race.gate.blocked"));
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel) {
            GateEntity placed = this.spawn(serverLevel, geometry, stack, player, 1.0F + 0.1F * column.size());
            if (placed == null) {
                return InteractionResult.FAIL;
            }
            placed.refreshColumn();
        }
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    protected @Nullable GateEntity spawn(ServerLevel level, GateGeometry geometry, ItemStack stack, @Nullable Player player, float pitch) {
        GateEntity gate = RaceModule.GATE.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (gate == null) {
            return null;
        }
        gate.setSize(this.size);
        if (!geometry.gate()) {
            gate.setKind(geometry.kind(), geometry.variant());
        }
        gate.setCourse(GateEntity.NO_COURSE, GateEntity.NO_INDEX);
        gate.setMount(geometry.mount(), geometry.flipped());
        gate.setTilt(geometry.tilt());
        gate.snapTo(geometry.x(), geometry.y(), geometry.z(), geometry.yawDegrees(), 0.0F);
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            gate.setCustomName(stack.getHoverName());
        }
        level.addFreshEntity(gate);
        level.playSound(null, gate.getX(), gate.getY(), gate.getZ(), SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 0.75F, pitch);
        gate.gameEvent(GameEvent.ENTITY_PLACE, player);
        return gate;
    }

    public static AABB bounds(GateGeometry geometry, GateStacking.Base base) {
        if (geometry.gate() && geometry.mount() == GateMount.STANDING && geometry.tilt().none()) {
            double foot = base == GateStacking.Base.STACKED ? geometry.frame() : 0.0;
            return new AABB(geometry.x() - geometry.halfExtentX(), geometry.y() + foot, geometry.z() - geometry.halfExtentZ(),
                    geometry.x() + geometry.halfExtentX(), geometry.y() + geometry.height(), geometry.z() + geometry.halfExtentZ());
        }
        AABB[] out = new AABB[1];
        geometry.bounds((minX, minY, minZ, maxX, maxY, maxZ) -> out[0] = new AABB(minX, minY, minZ, maxX, maxY, maxZ));
        return out[0];
    }

    public static boolean free(Level level, GateGeometry geometry, GateStacking.Base base, boolean checkBodies) {
        return free(level, geometry, base, checkBodies, null);
    }

    public static boolean free(Level level, GateGeometry geometry, GateStacking.Base base, boolean checkBodies, @Nullable GateEntity ignore) {
        if (geometry.freeform()) {
            return true;
        }
        if (!geometry.gate()) {
            return freeElement(level, geometry, checkBodies, ignore);
        }
        if (!geometry.alignedFrame() || !geometry.tilt().none()) {
            return freeOriented(level, geometry, base, checkBodies, ignore);
        }
        AABB bounds = bounds(geometry, base).deflate(0.01);
        if (!level.noCollision(null, bounds)) {
            return false;
        }
        List<AABB> frame = new ArrayList<>(6);
        geometry.boxes((minX, minY, minZ, maxX, maxY, maxZ) -> frame.add(new AABB(minX, minY, minZ, maxX, maxY, maxZ).deflate(0.01)), base);
        List<AABB> others = new ArrayList<>();
        GateCollisions.collect(level, bounds, others, ignore);
        for (AABB other : others) {
            for (AABB part : frame) {
                if (other.intersects(part)) {
                    return false;
                }
            }
        }
        if (checkBodies) {
            for (AABB part : frame) {
                if (!level.getEntities((Entity) null, part, GateCollisions::blocks).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static AABB hull(OrientedBox box) {
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    private static boolean blockFree(Level level, OrientedBox box) {
        for (VoxelShape shape : level.getBlockCollisions(null, hull(box))) {
            for (AABB part : shape.toAabbs()) {
                if (box.intersects(part.minX, part.minY, part.minZ, part.maxX, part.maxY, part.maxZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean freeElement(Level level, GateGeometry geometry, boolean checkBodies, @Nullable GateEntity ignore) {
        List<OrientedBox> parts = new ArrayList<>(12);
        geometry.parts(part -> parts.add(part.deflated(0.01)), GateStacking.Base.MOUNTED);
        for (OrientedBox part : parts) {
            if (!blockFree(level, part)) {
                return false;
            }
        }
        AABB bounds = bounds(geometry, GateStacking.Base.MOUNTED).deflate(0.01);
        List<OrientedBox> others = new ArrayList<>();
        GateCollisions.collectOriented(level, bounds, others, ignore);
        for (OrientedBox other : others) {
            for (OrientedBox part : parts) {
                if (other.intersects(part)) {
                    return false;
                }
            }
        }
        if (checkBodies) {
            for (OrientedBox part : parts) {
                for (Entity body : level.getEntities((Entity) null, hull(part), GateCollisions::blocks)) {
                    AABB box = body.getBoundingBox();
                    if (part.intersects(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean freeOriented(Level level, GateGeometry geometry, GateStacking.Base base, boolean checkBodies,
                                        @Nullable GateEntity ignore) {
        double half = geometry.width() * 0.5;
        double n = geometry.depth() * 0.5;
        double foot = base == GateStacking.Base.STACKED ? geometry.frame() : 0.0;
        OrientedBox slab = geometry.oriented(-half, half, foot, geometry.height(), -n, n).deflated(0.01);
        if (!blockFree(level, slab)) {
            return false;
        }
        List<OrientedBox> parts = new ArrayList<>(6);
        geometry.parts(part -> parts.add(part.deflated(0.01)), base);
        if (geometry.mount() == GateMount.HANGING) {
            for (OrientedBox part : parts) {
                if (part.aligned() && !blockFree(level, part)) {
                    return false;
                }
            }
        }
        AABB bounds = bounds(geometry, base).deflate(0.01);
        List<OrientedBox> others = new ArrayList<>();
        GateCollisions.collectOriented(level, bounds, others, ignore);
        for (OrientedBox other : others) {
            for (OrientedBox part : parts) {
                if (other.intersects(part)) {
                    return false;
                }
            }
        }
        if (checkBodies) {
            for (OrientedBox part : parts) {
                for (Entity body : level.getEntities((Entity) null, hull(part), GateCollisions::blocks)) {
                    AABB box = body.getBoundingBox();
                    if (part.intersects(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        ItemHelp.summary(ItemHelp.PREFIX + "gate_" + this.size.key() + ".summary")
                .what(ItemHelp.PREFIX + "gate")
                .use(ItemHelp.PREFIX + "gate")
                .args(ItemHelp.PREFIX + "gate.use.3", Component.keybind(ROTATE_KEY), Component.keybind(ROTATE_KEY))
                .args(ItemHelp.PREFIX + "gate.use.8", Component.keybind(FLIGHT_MODE_KEY))
                .specs(out -> out.accept(Component.translatable("propwash.race.gate.tooltip.size", (int) this.size.width(),
                        (int) this.size.height()).withStyle(ChatFormatting.GRAY)))
                .appendTo(builder);
    }
}
