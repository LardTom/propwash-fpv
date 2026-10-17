package de.lardtom.propwash.accessory;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.accessory.AccessoryRegistry;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.interact.DroneCargo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AccessoryBehaviors implements AccessoryRegistry {
    public static final AccessoryBehaviors INSTANCE = new AccessoryBehaviors();

    private final Map<Identifier, AccessoryBehavior> behaviors = new ConcurrentHashMap<>();
    private final List<Identifier> order = new CopyOnWriteArrayList<>();

    private AccessoryBehaviors() {
    }

    @Override
    public void register(Identifier accessoryId, AccessoryBehavior behavior) {
        Objects.requireNonNull(accessoryId, "accessoryId");
        Objects.requireNonNull(behavior, "behavior");
        if (Constants.MOD_ID.equals(accessoryId.getNamespace())) {
            throw new IllegalArgumentException("Der Namensraum propwash gehört dem eingebauten Zubehör: " + accessoryId);
        }
        int slots = behavior.slots();
        if (slots < 0 || slots > AccessoryBehavior.MAX_SLOTS) {
            throw new IllegalArgumentException(accessoryId + ": Inventar mit " + slots + " Slots (erlaubt 0 bis " + AccessoryBehavior.MAX_SLOTS + ")");
        }
        if (this.behaviors.putIfAbsent(accessoryId, behavior) != null) {
            throw new IllegalArgumentException("Zubehör-Verhalten doppelt angemeldet: " + accessoryId);
        }
        this.order.add(accessoryId);
    }

    @Override
    public Optional<AccessoryBehavior> behavior(Identifier accessoryId) {
        return Optional.ofNullable(this.behaviors.get(accessoryId));
    }

    public @Nullable AccessoryBehavior of(@Nullable AccessoryType type) {
        return type == null ? null : this.behaviors.get(type.id());
    }

    public int slots(@Nullable AccessoryType type) {
        AccessoryBehavior behavior = this.of(type);
        if (behavior == null) {
            return 0;
        }
        try {
            return Math.max(0, Math.min(AccessoryBehavior.MAX_SLOTS, behavior.slots()));
        } catch (RuntimeException e) {
            Constants.LOG.warn("Zubehör {}: slots() fehlgeschlagen", type.id(), e);
            return 0;
        }
    }

    public boolean hasInventory(DroneBuild build) {
        for (AccessoryType type : build.accessories().byMount().values()) {
            if (this.slots(type) > 0) {
                return true;
            }
        }
        return false;
    }

    public boolean any() {
        return !this.behaviors.isEmpty();
    }

    @Override
    public List<Identifier> registered() {
        return List.copyOf(this.order);
    }

    @Override
    public Optional<Identifier> accessoryAt(Entity drone, AccessorySlot slot) {
        if (!(drone instanceof QuadcopterEntity quad) || slot == null) {
            return Optional.empty();
        }
        return quad.build().accessories().at(AccessoryMount.of(slot)).map(AccessoryType::id);
    }

    @Override
    public List<ItemStack> contents(Entity drone, AccessorySlot slot) {
        if (!(drone instanceof QuadcopterEntity quad) || slot == null) {
            return List.of();
        }
        return copies(quad.accessoryContents().at(AccessoryMount.of(slot)));
    }

    @Override
    public Optional<AccessoryInventory> inventory(Entity drone, AccessorySlot slot) {
        if (!(drone instanceof QuadcopterEntity quad) || slot == null || quad.level().isClientSide()) {
            return Optional.empty();
        }
        AccessoryMount mount = AccessoryMount.of(slot);
        Optional<AccessoryType> type = quad.build().accessories().at(mount);
        int size = type.map(this::slots).orElse(0);
        return size <= 0 ? Optional.empty() : Optional.of(new DroneInventory(quad, mount, type.get(), size));
    }

    @Override
    public List<ItemStack> contents(ItemStack droneItem, AccessorySlot slot) {
        if (droneItem == null || slot == null) {
            return List.of();
        }
        return copies(droneItem.getOrDefault(AccessoryComponents.CONTENTS.get(), AccessoryContents.EMPTY).at(AccessoryMount.of(slot)));
    }

    @Override
    public double itemGrams(ItemStack stack) {
        return stack == null || stack.isEmpty() ? 0.0 : DroneCargo.grams(stack);
    }

    public double payloadGrams(AccessoryType type, List<ItemStack> contents) {
        AccessoryBehavior behavior = this.of(type);
        if (behavior != null) {
            try {
                OptionalDouble custom = behavior.payloadGrams(List.copyOf(contents));
                if (custom.isPresent() && Double.isFinite(custom.getAsDouble())) {
                    return Math.max(0.0, Math.min(AccessoryPayload.MAX_PAYLOAD_GRAMS, custom.getAsDouble()));
                }
            } catch (RuntimeException e) {
                Constants.LOG.warn("Zubehör {}: payloadGrams() fehlgeschlagen", type.id(), e);
            }
        }
        double sum = 0.0;
        for (ItemStack stack : contents) {
            sum += this.itemGrams(stack);
        }
        return Math.min(AccessoryPayload.MAX_PAYLOAD_GRAMS, sum);
    }

    private static List<ItemStack> copies(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }
}
