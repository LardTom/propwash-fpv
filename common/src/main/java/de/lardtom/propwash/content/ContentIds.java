package de.lardtom.propwash.content;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.core.BoundDrone;
import de.lardtom.propwash.core.CoreComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public final class ContentIds {
    public static final Identifier DRONE = Constants.id("drone");
    public static final Identifier DRONE_WORKBENCH = Constants.id("drone_workbench");
    public static final Identifier BATTERY_CHARGER = Constants.id("battery_charger");
    public static final Identifier REMOTE = Constants.id("remote");

    public static final Identifier GOGGLES_ANALOG = Constants.id("goggles_analog");
    public static final Identifier GOGGLES_DIGITAL = Constants.id("goggles_digital");
    public static final Identifier GOGGLES_CREATIVE = Constants.id("goggles_creative");

    public static final Identifier GATE_SMALL = Constants.id("gate_small");
    public static final Identifier GATE_MEDIUM = Constants.id("gate_medium");
    public static final Identifier GATE_LARGE = Constants.id("gate_large");
    public static final Identifier COURSE_TOOL = Constants.id("course_tool");
    public static final Identifier RACE_FLAG = Constants.id("race_flag");
    public static final Identifier SLALOM_POLE = Constants.id("slalom_pole");
    public static final Identifier DIVE_CUBE = Constants.id("dive_cube");
    public static final Identifier FREEFORM_GATE = Constants.id("freeform_gate");
    public static final Identifier GATE_POLE = Constants.id("gate_pole");
    public static final Identifier SIGNAL_REPEATER = Constants.id("signal_repeater");

    private ContentIds() {
    }

    public static Optional<Item> item(Identifier id) {
        return BuiltInRegistries.ITEM.getOptional(id);
    }

    public static boolean is(ItemStack stack, Identifier id) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
    }

    public static boolean isRemote(ItemStack stack) {
        return is(stack, REMOTE);
    }

    public static Optional<BoundDrone> boundDrone(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.get(CoreComponents.BOUND_DRONE.get()));
    }

    public static boolean isGoggles(ItemStack stack) {
        return gogglesSystem(stack).isPresent();
    }

    public static Optional<VideoSystem> gogglesSystem(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (GOGGLES_ANALOG.equals(id)) {
            return Optional.of(VideoSystem.ANALOG);
        }
        if (GOGGLES_DIGITAL.equals(id)) {
            return Optional.of(VideoSystem.DIGITAL);
        }
        if (GOGGLES_CREATIVE.equals(id)) {
            return Optional.of(VideoSystem.CREATIVE);
        }
        return Optional.empty();
    }

    public static ItemStack wornGoggles(LivingEntity entity) {
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        return isGoggles(head) ? head : ItemStack.EMPTY;
    }

    public static Optional<VideoSystem> wornGogglesSystem(LivingEntity entity) {
        return gogglesSystem(entity.getItemBySlot(EquipmentSlot.HEAD));
    }
}
