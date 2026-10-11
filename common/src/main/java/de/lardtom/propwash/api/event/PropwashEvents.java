package de.lardtom.propwash.api.event;

import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class PropwashEvents {
    public static final PropwashEvent<PartRegistered> PART_REGISTERED = new PropwashEvent<>("part_registered");
    public static final PropwashEvent<RegistryFrozen> REGISTRY_FROZEN = new PropwashEvent<>("registry_frozen");
    public static final PropwashEvent<DroneAssembled> DRONE_ASSEMBLED = new PropwashEvent<>("drone_assembled");
    public static final PropwashEvent<DroneDisassembled> DRONE_DISASSEMBLED = new PropwashEvent<>("drone_disassembled");
    public static final PropwashEvent<BuildImported> BUILD_IMPORTED = new PropwashEvent<>("build_imported");
    public static final PropwashEvent<EscBurned> ESC_BURNED = new PropwashEvent<>("esc_burned");

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
}
