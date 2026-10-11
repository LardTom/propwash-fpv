package de.lardtom.propwash.content;

import de.lardtom.propwash.api.part.CellChemistry;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.core.CoreComponents;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public final class Batteries {
    public static final float FULL = 1.0F;
    public static final float FULL_THRESHOLD = 0.999F;
    public static final int CHARGE_STEPS = 1000;

    private Batteries() {
    }

    public static int packColor(BatteryType type) {
        return type.definition().basics().visual().accentRgb().orElse(0x2F6FD6);
    }

    public static Optional<BatteryType> typeOf(ItemStack stack) {
        return DroneAssembly.part(stack).filter(BatteryType.class::isInstance).map(BatteryType.class::cast);
    }

    public static boolean isBattery(ItemStack stack) {
        return typeOf(stack).isPresent();
    }

    public static float charge(ItemStack stack) {
        Float stored = stack.get(CoreComponents.BATTERY_CHARGE.get());
        return clamp(stored == null ? FULL : stored);
    }

    public static ItemStack stackOf(BatteryType type, float charge) {
        ItemStack stack = ContentItems.stack(type);
        if (!stack.isEmpty()) {
            storeCharge(stack, charge);
        }
        return stack;
    }

    public static void storeCharge(ItemStack stack, float charge) {
        stack.set(CoreComponents.BATTERY_CHARGE.get(), quantize(charge));
    }

    public static float clamp(float charge) {
        if (Float.isNaN(charge)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(FULL, charge));
    }

    public static float quantize(float charge) {
        float clamped = clamp(charge);
        if (clamped >= FULL_THRESHOLD) {
            return FULL;
        }
        return Math.round(clamped * CHARGE_STEPS) / (float) CHARGE_STEPS;
    }

    public static boolean isFull(float charge) {
        return quantize(charge) >= FULL;
    }

    public static double openCircuitVoltage(BatteryType type, float charge) {
        BatteryType.Chemistry chemistry = type.chemistry;
        return type.cells * (chemistry.emptyCellVoltage + (chemistry.fullCellVoltage - chemistry.emptyCellVoltage) * shape(chemistry, clamp(charge)));
    }

    static double shape(BatteryType.Chemistry chemistry, float charge) {
        List<Double> curve = chemistry.definition().ocvShape();
        List<Double> soc = CellChemistry.SOC_GRID;
        int i = 1;
        while (i < soc.size() - 1 && charge > soc.get(i)) {
            i++;
        }
        double t = (charge - soc.get(i - 1)) / (soc.get(i) - soc.get(i - 1));
        return curve.get(i - 1) + (curve.get(i) - curve.get(i - 1)) * t;
    }

    public static int percent(float charge) {
        return Math.round(clamp(charge) * 100.0F);
    }

    public static int barColor(float charge) {
        float c = clamp(charge);
        if (c > 0.5F) {
            return 0x55FF55;
        }
        if (c > 0.2F) {
            return 0xFFFF55;
        }
        return 0xFF5555;
    }
}
