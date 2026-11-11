package de.lardtom.propwash.accessory.dispense;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.AccessoryBehaviors;
import de.lardtom.propwash.accessory.DroneMotion;
import de.lardtom.propwash.accessory.DronePushes;
import de.lardtom.propwash.accessory.MountPoses;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.accessory.DispenseOptions;
import de.lardtom.propwash.api.accessory.DispenseResult;
import de.lardtom.propwash.api.accessory.MountPose;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.dispenser.EquipmentDispenseItemBehavior;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.core.dispenser.SpawnEggItemBehavior;
import net.minecraft.core.dispenser.SulfurCubeBlockDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.WindChargeItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public final class AccessoryDispenser {
    public static final double TNT_HALF_HEIGHT = 0.49;
    public static final double ITEM_DROP = 0.125;

    private AccessoryDispenser() {
    }

    public static DispenseResult dispense(ServerLevel level, QuadcopterEntity drone, AccessoryMount mount, @Nullable ServerPlayer actor, int slot,
                                          DispenseOptions options) {
        Objects.requireNonNull(options, "options");
        if (drone.isRemoved() || drone.level() != level || !level.getServer().isSameThread()) {
            return DispenseResult.of(DispenseResult.Status.REFUSED);
        }
        Optional<AccessoryInventory> found = AccessoryBehaviors.INSTANCE.inventory(drone, mount.slot());
        if (found.isEmpty() || slot < 0 || slot >= found.get().size()) {
            return DispenseResult.of(DispenseResult.Status.REFUSED);
        }
        AccessoryInventory inventory = found.get();
        ItemStack stack = inventory.get(slot);
        if (stack.isEmpty()) {
            return DispenseResult.of(DispenseResult.Status.EMPTY);
        }
        Optional<MountPose> pose = MountPoses.of(drone, mount);
        if (pose.isEmpty()) {
            return DispenseResult.of(DispenseResult.Status.REFUSED);
        }
        DispenseOrigin origin = DispenseOrigin.of(level, drone, mount, pose.get());
        Shot shot = new Shot(level, drone, mount, inventory, slot, stack, pose.get(), origin, owner(drone, actor), options);
        if (!level.isLoaded(shot.muzzleBlock) || inside(level, shot.muzzleBlock, shot.muzzle)) {
            return DispenseResult.of(DispenseResult.Status.BLOCKED);
        }
        try {
            return shot.run();
        } catch (RuntimeException e) {
            Constants.LOG.warn("Zubehör {}: Werfen von {} fehlgeschlagen", accessoryId(drone, mount), stack.getItem(), e);
            return DispenseResult.of(DispenseResult.Status.REFUSED);
        }
    }

    public static boolean fluidContainer(ItemStack stack) {
        return stack.getItem() instanceof DispensibleContainerItem && !stack.is(Items.BUCKET);
    }

    public static DispenseMath.Kind kind(Item item) {
        if (item instanceof ArrowItem) {
            return DispenseMath.Kind.ARROW;
        }
        if (item instanceof ThrowablePotionItem) {
            return DispenseMath.Kind.POTION;
        }
        if (item instanceof ExperienceBottleItem) {
            return DispenseMath.Kind.EXPERIENCE_BOTTLE;
        }
        if (item instanceof WindChargeItem) {
            return DispenseMath.Kind.WIND_CHARGE;
        }
        if (item instanceof SnowballItem) {
            return DispenseMath.Kind.SNOWBALL;
        }
        if (item instanceof EggItem) {
            return DispenseMath.Kind.EGG;
        }
        if (item instanceof FireChargeItem) {
            return DispenseMath.Kind.FIRE_CHARGE;
        }
        if (item instanceof FireworkRocketItem) {
            return DispenseMath.Kind.FIREWORK;
        }
        if (item instanceof TridentItem) {
            return DispenseMath.Kind.TRIDENT;
        }
        return DispenseMath.Kind.OTHER;
    }

    public static boolean denied(ServerLevel level, @Nullable ServerPlayer player, BlockPos pos) {
        if (player == null || player.isRemoved()) {
            return true;
        }
        return DispenseMath.denied(player.mayInteract(level, pos), player.blockActionRestricted(level, pos, player.gameMode()), player.mayBuild());
    }

    static @Nullable DispenseItemBehavior behavior(ServerLevel level, ItemStack stack) {
        if (!stack.isItemEnabled(level.enabledFeatures())) {
            return null;
        }
        DispenseItemBehavior registered = DispenserBlock.DISPENSER_REGISTRY.get(stack.getItem());
        if (registered != null) {
            return registered;
        }
        if (stack.has(DataComponents.EQUIPPABLE)) {
            return EquipmentDispenseItemBehavior.INSTANCE;
        }
        if (stack.is(ItemTags.SULFUR_CUBE_SWALLOWABLE)) {
            return SulfurCubeBlockDispenseItemBehavior.INSTANCE;
        }
        if (stack.getItem() instanceof SpawnEggItem && stack.has(DataComponents.ENTITY_DATA)) {
            return SpawnEggItemBehavior.INSTANCE;
        }
        return null;
    }

    private static @Nullable ServerPlayer owner(QuadcopterEntity drone, @Nullable ServerPlayer actor) {
        return drone.pilotEntity() instanceof ServerPlayer pilot ? pilot : actor;
    }

    private static String accessoryId(QuadcopterEntity drone, AccessoryMount mount) {
        return drone.build().accessories().at(mount).map(type -> type.id().toString()).orElse(mount.name());
    }

    private static boolean inside(ServerLevel level, BlockPos pos, Vec3 point) {
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        for (AABB box : shape.toAabbs()) {
            if (box.move(pos).contains(point)) {
                return true;
            }
        }
        return false;
    }

    private static final class Shot {
        private final ServerLevel level;
        private final QuadcopterEntity drone;
        private final AccessoryMount mount;
        private final AccessoryInventory inventory;
        private final int slot;
        private final ItemStack stack;
        private final Vec3 muzzle;
        private final DispenseOrigin origin;
        private final Vec3 forward;
        private final BlockPos muzzleBlock;
        private final Direction facing;
        private final @Nullable ServerPlayer owner;
        private final DispenseOptions options;
        private final Vec3 velocity;

        private Shot(ServerLevel level, QuadcopterEntity drone, AccessoryMount mount, AccessoryInventory inventory, int slot, ItemStack stack,
                     MountPose pose, DispenseOrigin origin, @Nullable ServerPlayer owner, DispenseOptions options) {
            this.level = level;
            this.drone = drone;
            this.mount = mount;
            this.inventory = inventory;
            this.slot = slot;
            this.stack = stack;
            this.muzzle = origin.muzzle();
            this.origin = origin;
            this.forward = pose.forward().lengthSqr() > 1.0E-12 ? pose.forward().normalize() : new Vec3(0.0, 0.0, -1.0);
            this.muzzleBlock = BlockPos.containing(this.muzzle);
            this.facing = DispenseMath.nearest(this.forward);
            this.owner = owner;
            this.options = options;
            this.velocity = DroneMotion.velocity(drone);
        }

        private DispenseResult run() {
            if (fluidContainer(this.stack)) {
                if (DispenseMath.refusesFluid(true, this.options.allowFluids())) {
                    return DispenseResult.of(DispenseResult.Status.REFUSED);
                }
                DispenseItemBehavior behavior = behavior(this.level, this.stack);
                return behavior == null ? DispenseResult.of(DispenseResult.Status.REFUSED) : this.vanilla(behavior);
            }
            if (this.stack.getItem() instanceof ProjectileItem item) {
                return this.fire(item);
            }
            if (this.stack.is(Items.TNT)) {
                return this.tnt();
            }
            DispenseItemBehavior behavior = behavior(this.level, this.stack);
            if (behavior == DispenseItemBehavior.NOOP) {
                return DispenseResult.of(DispenseResult.Status.REFUSED);
            }
            return behavior == null ? this.eject() : this.vanilla(behavior);
        }

        private DispenseResult fire(ProjectileItem item) {
            ProjectileItem.DispenseConfig config = item.createDispenseConfig();
            ItemStack ammo = this.stack.copyWithCount(1);
            Projectile projectile = item.asProjectile(this.level, this.muzzle, ammo, this.facing);
            float power = (float) (config.power() * this.options.speedScale());
            float uncertainty = (float) (config.uncertainty() * this.options.inaccuracy());
            projectile.shoot(this.forward.x, this.forward.y, this.forward.z, power, uncertainty);
            projectile.setDeltaMovement(DispenseMath.inherit(this.velocity, projectile.getDeltaMovement()));
            if (this.owner != null) {
                projectile.setOwner(this.owner);
            }
            OwnShots.protect(projectile, this.drone);
            Projectile.spawnProjectile(projectile, this.level, ammo);
            this.level.levelEvent(config.overrideDispenseEvent().orElse(LevelEvent.SOUND_DISPENSER_PROJECTILE_LAUNCH), this.muzzleBlock, 0);
            this.level.levelEvent(LevelEvent.PARTICLES_SHOOT_SMOKE, this.muzzleBlock, this.facing.get3DDataValue());
            this.takeOne();
            double recoil = DispenseMath.recoil(kind(this.stack.getItem()), config.power(), this.options.speedScale());
            this.recoil(recoil);
            return DispenseResult.of(DispenseResult.Status.FIRED, projectile, recoil);
        }

        private DispenseResult tnt() {
            if (!this.level.getGameRules().get(GameRules.TNT_EXPLODES)) {
                return DispenseResult.of(DispenseResult.Status.REFUSED);
            }
            if (denied(this.level, this.owner, this.muzzleBlock)) {
                return DispenseResult.of(DispenseResult.Status.DENIED);
            }
            PrimedTnt tnt = new PrimedTnt(this.level, this.muzzle.x, this.origin.above(this.muzzle.y - TNT_HALF_HEIGHT), this.muzzle.z, this.owner);
            tnt.setDeltaMovement(DispenseMath.inherit(this.velocity, tnt.getDeltaMovement()));
            this.level.addFreshEntity(tnt);
            this.level.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
            this.level.gameEvent(this.owner, GameEvent.ENTITY_PLACE, this.muzzleBlock);
            this.takeOne();
            return DispenseResult.of(DispenseResult.Status.PLACED, tnt, 0.0);
        }

        private DispenseResult eject() {
            RandomSource random = this.level.getRandom();
            ItemEntity entity = new ItemEntity(this.level, this.muzzle.x, this.origin.above(this.muzzle.y - ITEM_DROP), this.muzzle.z,
                    this.stack.copyWithCount(1));
            double speed = random.nextDouble() * DispenseMath.ITEM_EJECT_SPREAD + DispenseMath.ITEM_EJECT_SPEED;
            Vec3 base = DispenseMath.itemEject(this.forward, speed);
            double spread = DispenseMath.TRIANGLE_SCALE * DispenseMath.ITEM_ACCURACY;
            Vec3 thrown = new Vec3(random.triangle(base.x, spread), random.triangle(base.y, spread), random.triangle(base.z, spread));
            entity.setDeltaMovement(DispenseMath.inherit(this.velocity, thrown));
            this.level.addFreshEntity(entity);
            this.level.levelEvent(LevelEvent.SOUND_DISPENSER_DISPENSE, this.muzzleBlock, 0);
            this.level.levelEvent(LevelEvent.PARTICLES_SHOOT_SMOKE, this.muzzleBlock, this.facing.get3DDataValue());
            this.takeOne();
            return DispenseResult.of(DispenseResult.Status.FIRED, entity, 0.0);
        }

        private DispenseResult vanilla(DispenseItemBehavior behavior) {
            BlockPos target = this.muzzleBlock.relative(this.facing);
            if (denied(this.level, this.owner, this.muzzleBlock) || denied(this.level, this.owner, target)) {
                return DispenseResult.of(DispenseResult.Status.DENIED);
            }
            BlockState state = Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, this.facing);
            DispenserBlockEntity returns = new DispenserBlockEntity(this.muzzleBlock, state);
            BlockSource source = new BlockSource(this.level, this.muzzleBlock, state, returns);
            ItemStack input = this.stack.copy();
            ItemStack result = behavior.dispense(source, input);
            boolean returned = !returns.isEmpty();
            boolean worked = behavior instanceof OptionalDispenseItemBehavior optional ? optional.isSuccess()
                    : returned || !ItemStack.matches(result, this.stack);
            if (!worked) {
                return DispenseResult.of(DispenseResult.Status.BLOCKED);
            }
            if (this.options.consume()) {
                this.inventory.set(this.slot, result == null ? ItemStack.EMPTY : result);
                for (int i = 0; i < returns.getContainerSize(); i++) {
                    ItemStack back = returns.removeItemNoUpdate(i);
                    if (!back.isEmpty()) {
                        this.store(back);
                    }
                }
            }
            return DispenseResult.of(DispenseResult.Status.USED);
        }

        private void takeOne() {
            if (!this.options.consume()) {
                return;
            }
            ItemStack rest = this.stack.copy();
            rest.setCount(DispenseMath.remaining(this.stack.getCount(), 1, true));
            this.inventory.set(this.slot, rest.isEmpty() ? ItemStack.EMPTY : rest);
        }

        private void store(ItemStack back) {
            AccessoryType type = this.drone.build().accessories().at(this.mount).map(AccessoryType::unladen).orElse(null);
            AccessoryBehavior behavior = AccessoryBehaviors.INSTANCE.of(type);
            ItemStack left = back.copy();
            for (int pass = 0; pass < 2 && !left.isEmpty(); pass++) {
                for (int i = 0; i < this.inventory.size() && !left.isEmpty(); i++) {
                    ItemStack current = this.inventory.get(i);
                    boolean merge = pass == 0;
                    if (merge ? current.isEmpty() || !ItemStack.isSameItemSameComponents(current, left) : !current.isEmpty()) {
                        continue;
                    }
                    if (!accepts(behavior, i, left)) {
                        continue;
                    }
                    int limit = Math.min(left.getMaxStackSize(), maxStack(behavior, i, left));
                    int room = limit - (merge ? current.getCount() : 0);
                    if (room <= 0) {
                        continue;
                    }
                    int moved = Math.min(room, left.getCount());
                    ItemStack placed = merge ? current.copyWithCount(current.getCount() + moved) : left.copyWithCount(moved);
                    this.inventory.set(i, placed);
                    left.shrink(moved);
                }
            }
            if (!left.isEmpty()) {
                ItemEntity drop = new ItemEntity(this.level, this.muzzle.x, this.origin.above(this.muzzle.y - ITEM_DROP), this.muzzle.z, left);
                drop.setDeltaMovement(DispenseMath.inherit(this.velocity, Vec3.ZERO));
                this.level.addFreshEntity(drop);
            }
        }

        private void recoil(double recoil) {
            if (!this.options.applyRecoil() || !(recoil > 0.0)) {
                return;
            }
            Vec3 com = MountPoses.center(this.drone);
            Vec3 point = this.muzzle.subtract(0.0, this.origin.liftMetres(), 0.0);
            Vector3f arm = new Vector3f((float) (point.x - com.x), (float) (point.y - com.y), (float) (point.z - com.z));
            new Quaternionf(this.drone.orientation()).conjugate().transform(arm);
            DronePushes.impulse(this.forward.scale(-recoil), new Vec3(arm.x, arm.y, arm.z))
                    .ifPresent(push -> DronePushes.apply(this.drone, push));
        }

        private static boolean accepts(@Nullable AccessoryBehavior behavior, int slot, ItemStack stack) {
            if (behavior == null) {
                return true;
            }
            try {
                return behavior.accepts(slot, stack.copy());
            } catch (RuntimeException e) {
                return false;
            }
        }

        private static int maxStack(@Nullable AccessoryBehavior behavior, int slot, ItemStack stack) {
            if (behavior == null) {
                return stack.getMaxStackSize();
            }
            try {
                return Math.max(1, behavior.maxStackSize(slot, stack.copy()));
            } catch (RuntimeException e) {
                return stack.getMaxStackSize();
            }
        }
    }
}
