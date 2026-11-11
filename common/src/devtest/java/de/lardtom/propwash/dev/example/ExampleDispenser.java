package de.lardtom.propwash.dev.example;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryAction;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.accessory.DispenseOptions;
import de.lardtom.propwash.api.accessory.DispenseResult;
import de.lardtom.propwash.api.accessory.ItemDisplay;
import de.lardtom.propwash.api.event.DroneKnockback;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartVisual;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ExampleDispenser {
    public static final String REQUIRED_API = "1.7";
    public static final Identifier DISPENSER = id("dispenser");
    public static final Identifier SPEAR_HOLDER = id("spear_holder");
    public static final Identifier BOOSTER = id("booster");
    public static final Identifier RECESSED_DISPENSER = id("dispenser_recessed");
    public static final double RECESS_MM = 58.0;
    public static final double DROP_MM = 1.5;
    public static final int DISPENSER_SLOTS = 4;
    public static final int FIRE = 1;
    public static final int BOOST = 3;
    public static final double BOOST_NEWTONS = 12.0;
    public static final double BOOST_SECONDS = 0.5;
    public static final ItemDisplay SPEAR_DISPLAY = ItemDisplay.at(0.0, -18.0, 25.0).rotated(-90.0F, 0.0F, 45.0F);
    public static final ItemDisplay ROCKET_DISPLAY = ItemDisplay.at(0.0, 65.0, 53.5).rotated(-90.0F, 0.0F, 112.0F).scaled(0.8F);

    public enum KnockbackMode {
        OFF,
        SCALE,
        CANCEL
    }

    public static final AtomicInteger SHOTS = new AtomicInteger();
    public static final AtomicReference<DispenseResult> LAST_RESULT = new AtomicReference<>(DispenseResult.of(DispenseResult.Status.EMPTY));
    public static final AtomicReference<Vec3> LAST_ENTITY_MOTION = new AtomicReference<>(Vec3.ZERO);
    public static final AtomicReference<Vec3> LAST_DRONE_VELOCITY = new AtomicReference<>(Vec3.ZERO);
    public static final AtomicInteger BOOSTS = new AtomicInteger();
    public static final AtomicInteger KNOCKBACKS = new AtomicInteger();
    public static final AtomicReference<String> LAST_KNOCKBACK = new AtomicReference<>("");
    public static final AtomicInteger DAMAGES = new AtomicInteger();
    public static final AtomicReference<String> LAST_DAMAGE_CAUSE = new AtomicReference<>("");

    private static volatile boolean consume = true;
    private static volatile double speedScale = 1.0;
    private static volatile KnockbackMode knockback = KnockbackMode.SCALE;

    private ExampleDispenser() {
    }

    static void init() {
        PropwashApi.requireVersion(ExampleAddon.NAMESPACE, REQUIRED_API);
        PropwashApi.parts().register(AccessoryDefinition.builder(DISPENSER)
                .basics(PartBasics.of(42.0).withCreativeListed(false).withVisual(PartVisual.accent(0x7A7A7A)))
                .slot(AccessorySlot.FRONT).size(34.0, 26.0, 40.0).frontalArea(0.0009)
                .build());
        PropwashApi.parts().register(AccessoryDefinition.builder(SPEAR_HOLDER)
                .basics(PartBasics.of(18.0).withCreativeListed(false).withVisual(PartVisual.accent(0x8B6B3E)))
                .slot(AccessorySlot.BOTTOM).size(12.0, 10.0, 60.0).frontalArea(0.0004).hangsStraight(true)
                .build());
        PropwashApi.parts().register(AccessoryDefinition.builder(BOOSTER)
                .basics(PartBasics.of(16.0).withCreativeListed(false).withVisual(PartVisual.accent(0xB03A2E)))
                .slot(AccessorySlot.TOP).size(18.0, 14.0, 36.0).frontalArea(0.0003)
                .build());
        PropwashApi.parts().register(AccessoryDefinition.builder(RECESSED_DISPENSER)
                .basics(PartBasics.of(150.0).withCreativeListed(false).withVisual(PartVisual.accent(0x7A7A7A)))
                .slot(AccessorySlot.FRONT).size(62.0, 58.0, 64.0).frontalArea(0.0036).recess(RECESS_MM).drop(DROP_MM)
                .build());
        PropwashApi.partItems().registerItem(DISPENSER, properties -> properties);
        PropwashApi.partItems().registerItem(SPEAR_HOLDER, properties -> properties);
        PropwashApi.partItems().registerItem(BOOSTER, properties -> properties);
        PropwashApi.partItems().registerItem(RECESSED_DISPENSER, properties -> properties);
        PropwashApi.accessories().register(DISPENSER, new Dispenser());
        PropwashApi.accessories().register(SPEAR_HOLDER, new SpearHolder());
        PropwashApi.accessories().register(BOOSTER, new Booster());
        PropwashApi.accessories().register(RECESSED_DISPENSER, new Dispenser());
        PropwashEvents.DRONE_KNOCKBACK.register(ExampleDispenser::onKnockback);
        PropwashEvents.DRONE_DAMAGED.register(damage -> {
            Entity cause = damage.source().getEntity();
            if (cause != null && !(cause instanceof Player)) {
                DAMAGES.incrementAndGet();
                LAST_DAMAGE_CAUSE.set(EntityType.getKey(cause.getType()) + " " + cause.getUUID());
            }
        });
    }

    public static void consume(boolean value) {
        consume = value;
    }

    public static void speedScale(double value) {
        speedScale = value;
    }

    public static void knockback(KnockbackMode mode) {
        knockback = mode;
    }

    public static String actionKey(Identifier accessory, int number) {
        return ExampleAddon.NAMESPACE + ".accessory." + accessory.getPath() + ".action." + number;
    }

    private static void onKnockback(DroneKnockback event) {
        KNOCKBACKS.incrementAndGet();
        LAST_KNOCKBACK.set(event.cause() + " " + event.deltaVelocity());
        KnockbackMode mode = knockback;
        if (mode == KnockbackMode.OFF || event.cause() == DroneKnockback.Cause.OTHER
                || !PropwashApi.accessories().accessoryAt(event.drone(), AccessorySlot.FRONT).equals(Optional.of(DISPENSER))) {
            return;
        }
        event.setScale(1.0);
        if (mode == KnockbackMode.CANCEL) {
            event.cancel();
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, path);
    }

    static final class Dispenser implements AccessoryBehavior {
        @Override
        public int slots() {
            return DISPENSER_SLOTS;
        }

        @Override
        public Optional<String> actionNameKey(int number) {
            return number == FIRE ? Optional.of(actionKey(DISPENSER, FIRE)) : Optional.empty();
        }

        @Override
        public void onAction(AccessoryAction action) {
            if (action.number() != FIRE || !action.pressed()) {
                return;
            }
            int slot = action.inventory().firstFilled();
            DispenseResult result = slot < 0 ? DispenseResult.of(DispenseResult.Status.EMPTY)
                    : action.dispense(slot, DispenseOptions.defaults().consume(consume).speedScale(speedScale));
            LAST_RESULT.set(result);
            LAST_ENTITY_MOTION.set(result.entity().map(Entity::getDeltaMovement).orElse(Vec3.ZERO));
            LAST_DRONE_VELOCITY.set(action.velocity());
            SHOTS.incrementAndGet();
        }
    }

    static final class SpearHolder implements AccessoryBehavior {
        @Override
        public int slots() {
            return 1;
        }

        @Override
        public boolean accepts(int slot, ItemStack stack) {
            return stack.is(ItemTags.SPEARS);
        }

        @Override
        public int maxStackSize(int slot, ItemStack stack) {
            return 1;
        }

        @Override
        public Optional<ItemDisplay> display(int slot) {
            return Optional.of(SPEAR_DISPLAY);
        }
    }

    static final class Booster implements AccessoryBehavior {
        @Override
        public int slots() {
            return 1;
        }

        @Override
        public boolean accepts(int slot, ItemStack stack) {
            return stack.is(Items.FIREWORK_ROCKET);
        }

        @Override
        public int maxStackSize(int slot, ItemStack stack) {
            return 1;
        }

        @Override
        public Optional<String> actionNameKey(int number) {
            return number == BOOST ? Optional.of(actionKey(BOOSTER, BOOST)) : Optional.empty();
        }

        @Override
        public Optional<ItemDisplay> display(int slot) {
            return Optional.of(ROCKET_DISPLAY);
        }

        @Override
        public void onAction(AccessoryAction action) {
            if (action.number() != BOOST || !action.pressed()) {
                return;
            }
            AccessoryInventory inventory = action.inventory();
            if (inventory.size() < 1 || inventory.get(0).isEmpty()) {
                return;
            }
            inventory.take(0, 1);
            action.physics().push(new Vec3(0.0, 0.0, -BOOST_NEWTONS), true, Vec3.ZERO, BOOST_SECONDS);
            BOOSTS.incrementAndGet();
        }
    }
}
