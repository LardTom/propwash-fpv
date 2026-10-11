package de.lardtom.propwash.content.paint;

import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.content.ContentIds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public final class PaintItems {
    public static final int[] ICON_DEFAULT_TINTS = {
            0x222529, 0xE1363D, 0xFF4136, 0xBBFF31, 0xBBFF31, 0xBBFF31, 0xBBFF31, 0x1A1C20, 0x24252A, 0x40444B, 0xD53F30, 0xFFFFFF
    };

    private PaintItems() {
    }

    public static DronePaint paint(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return DronePaint.DEFAULT;
        }
        DronePaint paint = stack.get(PaintComponents.DRONE_PAINT.get());
        return paint == null ? DronePaint.DEFAULT : paint;
    }

    public static void store(ItemStack stack, DronePaint paint) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        boolean drone = ContentIds.is(stack, ContentIds.DRONE);
        if (paint == null || paint.isDefault()) {
            stack.remove(PaintComponents.DRONE_PAINT.get());
            if (drone) {
                stack.remove(DataComponents.CUSTOM_MODEL_DATA);
            }
            return;
        }
        stack.set(PaintComponents.DRONE_PAINT.get(), paint);
        if (drone) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), iconTints(paint)));
        }
    }

    public static List<Integer> iconTints(DronePaint paint) {
        List<Integer> tints = new ArrayList<>(PaintSlot.COUNT);
        for (PaintSlot slot : PaintSlot.values()) {
            OptionalInt color = paint.color(slot);
            tints.add(color.isPresent() ? slot.channel().tint(color.getAsInt()) : ICON_DEFAULT_TINTS[slot.ordinal()]);
        }
        return List.copyOf(tints);
    }
}
