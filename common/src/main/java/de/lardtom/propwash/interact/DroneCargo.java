package de.lardtom.propwash.interact;

import com.mojang.serialization.Codec;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.PartRef;
import de.lardtom.propwash.core.event.Event;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.mixin.drone.ItemEntityAccessor;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.BuildAnalysis;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DroneCargo {
    public static final String SAVE_KEY = "Cargo";
    public static final Codec<List<ItemStack>> SAVE_CODEC = ItemStack.CODEC.listOf();
    public static final Event<CargoListener> CHANGED = new Event<>("drone_cargo_changed");
    public static final double MAX_DROP_SPEED = 1.5;
    public static final int FULL_NOTICE_TICKS = 60;
    private static final String PREFIX = "propwash.interact.cargo.";

    private DroneCargo() {
    }

    public static void tick(QuadcopterEntity drone) {
        if (!(drone.level() instanceof ServerLevel level)) {
            return;
        }
        Hold hold = drone.cargoHold();
        boolean gripper = CargoMass.hasGripper(drone.build());
        if (!hold.isEmpty()) {
            if (!gripper) {
                release(drone, CargoReleaseReason.REMOVED);
                return;
            }
            if (drone.isArmed()) {
                hold.disarmedTicks = 0;
            } else if (++hold.disarmedTicks >= GripperSpec.RELEASE_AFTER_DISARMED_TICKS) {
                release(drone, CargoReleaseReason.DISARMED);
                return;
            }
        } else {
            hold.disarmedTicks = 0;
        }
        if (!gripper || !drone.isArmed() || drone.pilot().isEmpty()) {
            return;
        }
        if ((drone.tickCount + drone.getId()) % GripperSpec.PICKUP_INTERVAL_TICKS != 0) {
            return;
        }
        pickUp(level, drone, hold);
    }

    public static AABB pickupBox(QuadcopterEntity drone) {
        double x = drone.getX();
        double y = drone.getY();
        double z = drone.getZ();
        return new AABB(x - GripperSpec.REACH_SIDE, y - GripperSpec.REACH_DOWN, z - GripperSpec.REACH_SIDE,
                x + GripperSpec.REACH_SIDE, y + GripperSpec.REACH_UP, z + GripperSpec.REACH_SIDE);
    }

    public static double capacityGrams(DroneBuild build) {
        return capacityGrams(build, 1.0);
    }

    public static double capacityGrams(DroneBuild build, double gravityScale) {
        BuildAnalysis analysis = BuildAnalysis.of(CargoMass.unladen(build));
        return GripperSpec.capacityGrams(analysis.massGrams(), 4.0 * analysis.maxThrustPerMotorGrams(), gravityScale);
    }

    public static double capacityGrams(ServerLevel level, DroneBuild build) {
        return capacityGrams(build, WorldRules.get(level.getServer(), WorldRuleSpecs.GRAVITY_SCALE) / 100.0);
    }

    public static double gramsEach(ItemStack stack) {
        return gramsEach(stack, 0);
    }

    private static double gramsEach(ItemStack stack, int depth) {
        return ItemWeights.gramsEach(stack.getMaxStackSize(), stack.getItem() instanceof BlockItem, knownGrams(stack)) + contentsGrams(stack, depth);
    }

    static double contentsGrams(ItemStack stack, int depth) {
        if (depth >= GripperSpec.MAX_NESTING) {
            return 0.0;
        }
        double grams = 0.0;
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            grams += container.nonEmptyItemCopyStream().mapToDouble(inner -> gramsEach(inner, depth + 1) * inner.getCount()).sum();
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            grams += bundle.itemCopies().filter(inner -> !inner.isEmpty()).mapToDouble(inner -> gramsEach(inner, depth + 1) * inner.getCount()).sum();
        }
        return grams;
    }

    public static double grams(ItemStack stack) {
        return stack.isEmpty() ? 0.0 : gramsEach(stack) * stack.getCount();
    }

    static double knownGrams(ItemStack stack) {
        DroneBuild build = stack.get(CoreComponents.DRONE_BUILD.get());
        if (build != null) {
            return build.totalMassGrams();
        }
        PartRef ref = stack.get(CoreComponents.PART.get());
        if (ref != null) {
            Optional<PartInfo> part = ref.resolve();
            if (part.isPresent()) {
                return part.get().massGrams();
            }
        }
        return 0.0;
    }

    private static void pickUp(ServerLevel level, QuadcopterEntity drone, Hold hold) {
        long now = level.getGameTime();
        Vec3 dropped = hold.droppedAt;
        if (dropped != null) {
            if (!GripperSpec.awayFromDrop(drone.getX() - dropped.x, drone.getY() - dropped.y, drone.getZ() - dropped.z)) {
                return;
            }
            hold.droppedAt = null;
        }
        AABB box = pickupBox(drone);
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box, item -> item.isAlive() && !item.getItem().isEmpty());
        Set<UUID> present = new HashSet<>();
        List<ItemEntity> items = new ArrayList<>(nearby.size());
        UUID pilot = drone.pilot().orElse(null);
        for (ItemEntity item : nearby) {
            present.add(item.getUUID());
            if (!item.hasPickUpDelay() && GripperSpec.mayTake(((ItemEntityAccessor) item).propwash$target(), pilot)) {
                items.add(item);
            }
        }
        hold.prune(present, now);
        if (items.isEmpty()) {
            return;
        }
        Vec3 center = drone.position();
        items.sort(Comparator.comparingDouble(item -> item.distanceToSqr(center)));
        double capacity = capacityGrams(level, drone.build());
        int picked = 0;
        ItemStack last = ItemStack.EMPTY;
        boolean full = false;
        for (ItemEntity item : items) {
            if (hold.released.containsKey(item.getUUID()) || !visible(level, drone, item)) {
                continue;
            }
            ItemStack offered = item.getItem();
            int take = CargoPlan.takeable(hold.loads(offered), load(offered, Hold.OFFER), capacity, GripperSpec.MAX_STACKS);
            if (take <= 0) {
                full = true;
                continue;
            }
            ItemStack rest = offered.copy();
            ItemStack taken = rest.split(take);
            if (rest.isEmpty()) {
                item.discard();
            } else {
                item.setItem(rest);
            }
            hold.add(taken);
            picked += take;
            last = taken;
        }
        if (picked > 0) {
            sync(drone);
            level.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4F, 1.6F);
            notifyPilot(level, drone, Component.translatable(PREFIX + "picked", picked, last.getHoverName(),
                    Math.round(hold.grams()), Math.round(capacity)), ChatFormatting.GREEN);
            CHANGED.fire(listener -> listener.onCargoChanged(drone, hold.grams(), null));
        } else if (full && (hold.fullNoticeTick == Long.MIN_VALUE || now - hold.fullNoticeTick >= FULL_NOTICE_TICKS)) {
            hold.fullNoticeTick = now;
            notifyPilot(level, drone, Component.translatable(PREFIX + "full", Math.round(hold.grams()), Math.round(capacity)),
                    ChatFormatting.GOLD);
        }
    }

    private static boolean visible(ServerLevel level, QuadcopterEntity drone, ItemEntity item) {
        Vec3 from = drone.position().add(0.0, drone.getBbHeight() * 0.5, 0.0);
        Vec3 to = item.position().add(0.0, item.getBbHeight() * 0.5, 0.0);
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, drone)).getType() == HitResult.Type.MISS;
    }

    static CargoPlan.Load load(ItemStack stack, String key) {
        return new CargoPlan.Load(key, gramsEach(stack), stack.getCount(), stack.getMaxStackSize());
    }

    public static void release(QuadcopterEntity drone, CargoReleaseReason reason) {
        Hold hold = drone.cargoHold();
        if (hold.isEmpty() || !(drone.level() instanceof ServerLevel level)) {
            return;
        }
        int count = 0;
        Vec3 velocity = reason == CargoReleaseReason.REMOVED ? Vec3.ZERO : drone.getDeltaMovement();
        if (velocity.length() > MAX_DROP_SPEED) {
            velocity = velocity.normalize().scale(MAX_DROP_SPEED);
        }
        double y = drone.getY() - 0.2;
        long now = level.getGameTime();
        hold.expire(now);
        for (ItemStack stack : hold.stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            count += stack.getCount();
            ItemEntity item = new ItemEntity(level, drone.getX(), y, drone.getZ(), stack.copy(), velocity.x, velocity.y, velocity.z);
            item.setDefaultPickUpDelay();
            if (level.addFreshEntity(item)) {
                hold.released.put(item.getUUID(), now);
            }
        }
        hold.stacks.clear();
        hold.disarmedTicks = 0;
        hold.droppedAt = reason == CargoReleaseReason.REMOVED ? null : drone.position();
        sync(drone);
        if (reason != CargoReleaseReason.REMOVED) {
            level.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SoundEvents.DISPENSER_DISPENSE, SoundSource.NEUTRAL, 0.5F, 1.8F);
            String key = switch (reason) {
                case CRASH -> "released_crash";
                case DISARMED -> "released_landed";
                default -> "released";
            };
            notifyPilot(level, drone, Component.translatable(PREFIX + key, count),
                    reason == CargoReleaseReason.CRASH ? ChatFormatting.RED : ChatFormatting.AQUA);
        }
        CHANGED.fire(listener -> listener.onCargoChanged(drone, 0.0, reason));
    }

    public static void onRemoved(QuadcopterEntity drone) {
        release(drone, CargoReleaseReason.REMOVED);
    }

    public static void load(QuadcopterEntity drone, List<ItemStack> stacks) {
        Hold hold = drone.cargoHold();
        hold.stacks.clear();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                hold.stacks.add(stack.copy());
            }
        }
        sync(drone);
    }

    public static void sync(QuadcopterEntity drone) {
        Hold hold = drone.cargoHold();
        ItemStack display = ItemStack.EMPTY;
        double heaviest = -1.0;
        for (ItemStack stack : hold.stacks) {
            double grams = grams(stack);
            if (grams > heaviest) {
                heaviest = grams;
                display = stack.copyWithCount(1);
            }
        }
        drone.setCargoSync((float) hold.grams(), display);
    }

    private static void notifyPilot(ServerLevel level, QuadcopterEntity drone, Component message, ChatFormatting color) {
        Optional<UUID> pilot = drone.pilot();
        if (pilot.isEmpty()) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(pilot.get());
        if (player != null) {
            player.sendOverlayMessage(message.copy().withStyle(color));
        }
    }

    @FunctionalInterface
    public interface CargoListener {
        void onCargoChanged(QuadcopterEntity drone, double grams, CargoReleaseReason released);
    }

    public static final class Hold {
        static final String OFFER = "offer";
        private final List<ItemStack> stacks = new ArrayList<>();
        private final Map<UUID, Long> released = new HashMap<>();
        private int disarmedTicks;
        private long fullNoticeTick = Long.MIN_VALUE;
        private @Nullable Vec3 droppedAt;

        public boolean isEmpty() {
            return this.stacks.isEmpty();
        }

        public List<ItemStack> stacks() {
            return List.copyOf(this.stacks);
        }

        public int itemCount() {
            int count = 0;
            for (ItemStack stack : this.stacks) {
                count += stack.getCount();
            }
            return count;
        }

        public double grams() {
            double sum = 0.0;
            for (ItemStack stack : this.stacks) {
                sum += DroneCargo.grams(stack);
            }
            return sum;
        }

        public boolean ignores(UUID item) {
            return this.released.containsKey(item);
        }

        List<CargoPlan.Load> loads(ItemStack offered) {
            List<CargoPlan.Load> loads = new ArrayList<>(this.stacks.size());
            for (int i = 0; i < this.stacks.size(); i++) {
                ItemStack stack = this.stacks.get(i);
                loads.add(load(stack, ItemStack.isSameItemSameComponents(stack, offered) ? OFFER : "slot" + i));
            }
            return loads;
        }

        void add(ItemStack taken) {
            ItemStack rest = taken.copy();
            for (ItemStack stack : this.stacks) {
                if (rest.isEmpty()) {
                    return;
                }
                if (ItemStack.isSameItemSameComponents(stack, rest) && stack.getCount() < stack.getMaxStackSize()) {
                    int move = Math.min(rest.getCount(), stack.getMaxStackSize() - stack.getCount());
                    stack.grow(move);
                    rest.shrink(move);
                }
            }
            while (!rest.isEmpty()) {
                this.stacks.add(rest.split(Math.min(rest.getCount(), rest.getMaxStackSize())));
            }
        }

        void expire(long now) {
            this.released.values().removeIf(tick -> now - tick > GripperSpec.RELEASED_IGNORE_TICKS);
        }

        void prune(Set<UUID> present, long now) {
            Iterator<Map.Entry<UUID, Long>> it = this.released.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                if (!present.contains(entry.getKey()) || now - entry.getValue() > GripperSpec.RELEASED_IGNORE_TICKS) {
                    it.remove();
                }
            }
        }
    }
}
