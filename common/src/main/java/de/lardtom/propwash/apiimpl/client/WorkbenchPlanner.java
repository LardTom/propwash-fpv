package de.lardtom.propwash.apiimpl.client;

import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ImportService;
import de.lardtom.propwash.core.PartCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class WorkbenchPlanner {
    private WorkbenchPlanner() {
    }

    public static List<Identifier> missingParts(WorkbenchContext context, DroneData data) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(data, "data");
        List<Identifier> unknown = Conversions.unknownParts(data.build());
        if (!unknown.isEmpty()) {
            return List.copyOf(unknown);
        }
        Optional<DroneBuild> build = Conversions.build(data.build());
        if (build.isEmpty()) {
            return List.of();
        }
        LocalPlayer player = Minecraft.getInstance().player;
        List<ItemStack> inventory = player == null ? List.of() : player.getInventory().getNonEquipmentItems();
        return ImportService.missingParts(build.get(), current(context), inventory, context.creative());
    }

    static List<@Nullable PartInfo> current(WorkbenchContext context) {
        List<@Nullable PartInfo> slots = new ArrayList<>(BuildSlots.COUNT);
        for (int slot = 0; slot < BuildSlots.COUNT; slot++) {
            slots.add(null);
        }
        for (PartKind kind : PartKind.values()) {
            PartCategory category = PartCategory.of(kind);
            for (int position = 0; position < category.slotCount(); position++) {
                int index = BuildSlots.firstSlot(category) + position;
                if (index >= 0 && index < BuildSlots.COUNT) {
                    slots.set(index, context.slot(kind, position).flatMap(PartIndex::byId).orElse(null));
                }
            }
        }
        return slots;
    }
}
