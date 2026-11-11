package de.lardtom.propwash.api.event;

import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

public final class PropwashEvents {
    public static final PropwashEvent<PartRegistered> PART_REGISTERED = new PropwashEvent<>("part_registered");
    public static final PropwashEvent<RegistryFrozen> REGISTRY_FROZEN = new PropwashEvent<>("registry_frozen");
    public static final PropwashEvent<DroneAssembled> DRONE_ASSEMBLED = new PropwashEvent<>("drone_assembled");
    public static final PropwashEvent<DroneDisassembled> DRONE_DISASSEMBLED = new PropwashEvent<>("drone_disassembled");
    public static final PropwashEvent<BuildImported> BUILD_IMPORTED = new PropwashEvent<>("build_imported");
    public static final PropwashEvent<EscBurned> ESC_BURNED = new PropwashEvent<>("esc_burned");
    public static final PropwashEvent<DroneHitEntity> DRONE_HIT_ENTITY = new PropwashEvent<>("drone_hit_entity");
    public static final PropwashEvent<DroneDamaged> DRONE_DAMAGED = new PropwashEvent<>("drone_damaged");
    public static final PropwashEvent<AccessoryActionUsed> ACCESSORY_ACTION = new PropwashEvent<>("accessory_action");
    public static final PropwashEvent<AddonSwitchChanged> ADDON_SWITCH_CHANGED = new PropwashEvent<>("addon_switch_changed");
    public static final PropwashEvent<DroneKnockedBack> DRONE_KNOCKBACK = new PropwashEvent<>("drone_knockback");
    public static final PropwashEvent<AutonomyEnded> AUTONOMY_ENDED = new PropwashEvent<>("autonomy_ended");
    public static final PropwashEvent<PilotChanged> PILOT_CHANGED = new PropwashEvent<>("pilot_changed");

    private PropwashEvents() {
    }

    @FunctionalInterface
    public interface PartRegistered {
        void onPartRegistered(PartDefinition definition);
    }

    @FunctionalInterface
    public interface RegistryFrozen {
        void onRegistryFrozen(PartRegistry registry);
    }

    @FunctionalInterface
    public interface DroneAssembled {
        void onDroneAssembled(ServerPlayer player, ItemStack drone, DroneData data);
    }

    @FunctionalInterface
    public interface DroneDisassembled {
        void onDroneDisassembled(ServerPlayer player, DroneData data);
    }

    @FunctionalInterface
    public interface BuildImported {
        void onBuildImported(ServerPlayer player, ImportRequest request, ImportResult result);
    }

    @FunctionalInterface
    public interface EscBurned {
        void onEscBurned(ServerPlayer player, ItemStack drone, DroneData data, int cells);
    }

    @FunctionalInterface
    public interface DroneHitEntity {
        void onDroneHitEntity(EntityHit hit);
    }

    @FunctionalInterface
    public interface DroneDamaged {
        void onDroneDamaged(DroneDamage damage);
    }

    @FunctionalInterface
    public interface AccessoryActionUsed {
        void onAccessoryAction(ServerPlayer player, Entity drone, int number, boolean pressed);
    }

    @FunctionalInterface
    public interface AddonSwitchChanged {
        void onAddonSwitchChanged(ServerPlayer player, Entity drone, Identifier id, boolean active);
    }

    @FunctionalInterface
    public interface DroneKnockedBack {
        void onDroneKnockback(DroneKnockback knockback);
    }

    @FunctionalInterface
    public interface AutonomyEnded {
        void onAutonomyEnded(Entity drone, UUID owner, Identifier id, AutonomyEnd reason);
    }

    @FunctionalInterface
    public interface PilotChanged {
        void onPilotChanged(Entity drone, Optional<UUID> previous, Optional<UUID> current);
    }
}
