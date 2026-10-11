package de.lardtom.propwash.content;

import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.PartCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PartCondition {
    public static final int BAR_SEGMENTS = 10;
    public static final String FILLED = "■";
    public static final String EMPTY = "□";
    private static final String KEY = ItemHelp.PREFIX + "status.condition";

    private PartCondition() {
    }

    public static float of(ItemStack stack) {
        Float stored = stack.get(CoreComponents.PART_CONDITION.get());
        return stored == null ? PartWear.NEW : PartWear.quantize(stored);
    }

    public static boolean worn(ItemStack stack) {
        return of(stack) < PartWear.NEW;
    }

    public static boolean broken(ItemStack stack) {
        return PartWear.broken(of(stack));
    }

    public static boolean critical(ItemStack stack) {
        return PartWear.critical(of(stack));
    }

    public static void store(ItemStack stack, float condition) {
        float value = PartWear.quantize(condition);
        if (value >= PartWear.NEW) {
            stack.remove(CoreComponents.PART_CONDITION.get());
        } else {
            stack.set(CoreComponents.PART_CONDITION.get(), value);
        }
    }

    public static ItemStack with(ItemStack stack, float condition) {
        store(stack, condition);
        return stack;
    }

    public static int percent(float condition) {
        if (!Float.isFinite(condition)) {
            return 100;
        }
        float clamped = Math.max(0.0F, Math.min(1.0F, condition));
        int percent = Math.round(clamped * 100.0F);
        if (clamped > 0.0F && percent == 0) {
            return 1;
        }
        if (clamped < 1.0F && percent == 100) {
            return 99;
        }
        return percent;
    }

    public static State state(float condition) {
        if (PartWear.broken(condition)) {
            return State.BROKEN;
        }
        if (PartWear.critical(condition)) {
            return State.CRITICAL;
        }
        if (PartWear.damaged(condition)) {
            return State.WORN;
        }
        return condition >= PartWear.NEW ? State.NEW : State.USED;
    }

    public static ChatFormatting color(float condition) {
        return switch (state(condition)) {
            case NEW, USED -> ChatFormatting.GREEN;
            case WORN -> ChatFormatting.YELLOW;
            case CRITICAL -> ChatFormatting.RED;
            case BROKEN -> ChatFormatting.DARK_RED;
        };
    }

    public static int barColor(float condition) {
        return switch (state(condition)) {
            case NEW, USED -> 0x55FF55;
            case WORN -> 0xFFFF55;
            case CRITICAL -> 0xFF5555;
            case BROKEN -> 0xAA0000;
        };
    }

    public static int filledSegments(float condition) {
        if (PartWear.broken(condition)) {
            return 0;
        }
        return Math.max(1, Math.min(BAR_SEGMENTS, Math.round(condition * BAR_SEGMENTS)));
    }

    public static Component bar(float condition) {
        int filled = filledSegments(condition);
        MutableComponent bar = Component.literal(FILLED.repeat(filled)).withStyle(color(condition));
        if (filled < BAR_SEGMENTS) {
            bar.append(Component.literal(EMPTY.repeat(BAR_SEGMENTS - filled)).withStyle(ChatFormatting.DARK_GRAY));
        }
        return bar;
    }

    public static Component value(float condition) {
        State state = state(condition);
        if (state == State.BROKEN) {
            return Component.translatable(KEY + ".broken").withStyle(ChatFormatting.DARK_RED);
        }
        return Component.translatable(KEY + ".percent", percent(condition)).withStyle(color(condition));
    }

    public static Component line(float condition) {
        return Component.translatable(KEY, bar(condition), value(condition)).withStyle(ChatFormatting.GRAY);
    }

    public static Component named(Component name, float condition) {
        return Component.translatable(KEY + ".named", name, bar(condition), value(condition)).withStyle(ChatFormatting.GRAY);
    }

    public static float worst(PartWear wear, PartCategory category) {
        return switch (category) {
            case FRAME -> wear.frame();
            case STACK -> wear.stack();
            case MOTOR -> wear.worstMotor();
            case PROP -> wear.worstProp();
            case VIDEO -> wear.video();
            case BATTERY -> wear.battery();
            case ACCESSORY -> PartWear.NEW;
        };
    }

    public static PartCategory worstCategory(PartWear wear) {
        PartCategory worst = PartCategory.PROP;
        float min = Float.MAX_VALUE;
        for (PartCategory category : PartCategory.values()) {
            float value = worst(wear, category);
            if (value < min) {
                min = value;
                worst = category;
            }
        }
        return worst;
    }

    public static Component categoryName(PartCategory category) {
        return Component.translatable(KEY + ".part." + category.key());
    }

    public static List<Component> categoryLines(PartWear wear) {
        return categoryLines(wear, null);
    }

    public static List<Component> detailLines(PartWear wear) {
        return categoryLines(wear, worstCategory(wear));
    }

    private static List<Component> categoryLines(PartWear wear, @Nullable PartCategory skip) {
        List<Component> lines = new ArrayList<>();
        for (PartCategory category : PartCategory.values()) {
            float value = worst(wear, category);
            if (category != skip && value < PartWear.NEW) {
                lines.add(named(categoryName(category), value));
            }
        }
        return lines;
    }

    public static Component summary(PartWear wear) {
        PartCategory category = worstCategory(wear);
        float value = worst(wear, category);
        return Component.translatable(KEY + ".summary", bar(value), value(value), categoryName(category)).withStyle(ChatFormatting.GRAY);
    }

    public enum State {
        NEW,
        USED,
        WORN,
        CRITICAL,
        BROKEN
    }
}
