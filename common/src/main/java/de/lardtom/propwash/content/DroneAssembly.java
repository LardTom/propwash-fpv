package de.lardtom.propwash.content;

import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.ActionCamSpec;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.core.PartRef;
import de.lardtom.propwash.creative.CreativeModule;
import de.lardtom.propwash.creative.CreativeSpec;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.bind.BindComponents;
import de.lardtom.propwash.drone.coating.CoatingComponents;
import de.lardtom.propwash.drone.TuneMapping;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

public final class DroneAssembly {
    public static final float FULL_CHARGE = 1.0F;

    private DroneAssembly() {
    }

    public static Optional<PartInfo> part(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        PartRef ref = stack.get(CoreComponents.PART.get());
        return ref == null ? Optional.empty() : ref.resolve();
    }

    public static Optional<PartCategory> category(ItemStack stack) {
        return part(stack).map(PartCategory::of);
    }

    public static boolean fits(ItemStack stack, PartCategory category) {
        return category(stack).filter(c -> c == category).isPresent();
    }

    public static boolean isDrone(ItemStack stack) {
        return ContentIds.is(stack, ContentIds.DRONE);
    }

    public static boolean droneItemAvailable() {
        return ContentIds.item(ContentIds.DRONE).isPresent();
    }

    public static DroneBuild buildOf(ItemStack drone) {
        return drone.getOrDefault(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
    }

    public static List<@Nullable PartInfo> read(Container parts) {
        List<@Nullable PartInfo> result = new ArrayList<>(BuildSlots.COUNT);
        for (int slot = 0; slot < BuildSlots.COUNT; slot++) {
            result.add(part(parts.getItem(slot)).orElse(null));
        }
        return result;
    }

    public static BuildSlots.Check check(Container parts) {
        BuildSlots.Check check = BuildSlots.check(read(parts));
        CreativeSpec spec = parts.getItem(BuildSlots.STACK).get(CreativeModule.SPEC.get());
        if (spec == null || check.build().isEmpty() || !check.build().get().isCreative()) {
            return check;
        }
        return new BuildSlots.Check(check.build().map(build -> build.withCreative(spec)), check.missing(), check.mixed(), check.filledSlots());
    }

    public static ItemStack assemble(Container parts) {
        return assemble(parts, PartCompatibility.ENFORCED);
    }

    public static ItemStack assemble(Container parts, PartCompatibility rule) {
        BuildSlots.Check check = check(parts);
        if (!check.buildable(rule)) {
            return ItemStack.EMPTY;
        }
        ItemStack drone = createDrone(check.build().orElseThrow(), parts.getItem(BuildSlots.STACK), parts.getItem(BuildSlots.FRAME),
                parts.getItem(BuildSlots.BATTERY), parts.getItem(BuildSlots.accessorySlot(AccessoryMount.TOP)));
        if (!drone.isEmpty()) {
            storeWear(drone, wearOf(parts));
        }
        return drone;
    }

    public static float cameraCharge(ItemStack stack) {
        Float stored = stack.get(CoreComponents.CAMERA_CHARGE.get());
        if (stored == null || stored.isNaN()) {
            return FULL_CHARGE;
        }
        return Math.max(0.0F, Math.min(FULL_CHARGE, stored));
    }

    public static void storeCameraCharge(ItemStack stack, float charge) {
        float quantized = Batteries.quantize(charge);
        if (quantized >= FULL_CHARGE) {
            stack.remove(CoreComponents.CAMERA_CHARGE.get());
        } else {
            stack.set(CoreComponents.CAMERA_CHARGE.get(), quantized);
        }
    }

    public static boolean hasOwnCameraBattery(DroneBuild build) {
        return build.accessories().camera().flatMap(AccessoryType::camera).map(ActionCamSpec::ownBattery).orElse(false);
    }

    public static float[] conditions(Container parts) {
        float[] conditions = new float[PartWear.SLOTS];
        for (int slot = 0; slot < PartWear.SLOTS; slot++) {
            ItemStack stack = parts.getItem(slot);
            conditions[slot] = stack.isEmpty() ? PartWear.NEW : PartCondition.of(stack);
        }
        return conditions;
    }

    public static PartWear wearOf(Container parts) {
        return PartWear.fromSlots(conditions(parts));
    }

    public static PartWear wear(ItemStack drone) {
        PartWear wear = drone.get(CoreComponents.PART_WEAR.get());
        return wear == null ? PartWear.PRISTINE : wear;
    }

    public static void storeWear(ItemStack drone, PartWear wear) {
        if (wear == null || wear.isNew()) {
            drone.remove(CoreComponents.PART_WEAR.get());
        } else {
            drone.set(CoreComponents.PART_WEAR.get(), wear);
        }
    }

    public static float charge(ItemStack stack) {
        Float stored = stack.get(CoreComponents.BATTERY_CHARGE.get());
        if (stored == null || stored.isNaN()) {
            return FULL_CHARGE;
        }
        return Math.max(0.0F, Math.min(FULL_CHARGE, stored));
    }

    public static boolean hasBattery(ItemStack drone) {
        return drone.getOrDefault(CoreComponents.BATTERY_INSTALLED.get(), Boolean.TRUE);
    }

    public static ItemStack createDrone(DroneBuild build, ItemStack stackPart, ItemStack framePart, ItemStack batteryPart) {
        return createDrone(build, stackPart, framePart, batteryPart, ItemStack.EMPTY);
    }

    public static ItemStack createDrone(DroneBuild build, ItemStack stackPart, ItemStack framePart, ItemStack batteryPart, ItemStack cameraPart) {
        Optional<Item> item = ContentIds.item(ContentIds.DRONE);
        if (item.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack drone = new ItemStack(item.get());
        drone.set(CoreComponents.DRONE_BUILD.get(), build);
        drone.set(CoreComponents.BATTERY_CHARGE.get(), charge(batteryPart));
        FlightTune tune = assembledTune(stackPart.get(CoreComponents.FLIGHT_TUNE.get()), framePart, build);
        if (!tune.equals(FlightTune.EMPTY)) {
            drone.set(CoreComponents.FLIGHT_TUNE.get(), tune);
        }
        UUID id = stackPart.get(CoreComponents.DRONE_ID.get());
        if (id != null) {
            drone.set(CoreComponents.DRONE_ID.get(), id);
        }
        BindComponents.copy(stackPart, drone);
        CoatingComponents.fromPart(stackPart, drone, build);
        Component name = framePart.get(DataComponents.CUSTOM_NAME);
        if (name != null) {
            drone.set(DataComponents.CUSTOM_NAME, name);
        }
        if (hasOwnCameraBattery(build)) {
            storeCameraCharge(drone, cameraCharge(cameraPart));
        }
        PaintItems.store(drone, PaintItems.paint(framePart));
        return drone;
    }

    public static FlightTune assembledTune(@Nullable FlightTune stackTune, ItemStack framePart, DroneBuild build) {
        Map<String, Float> values = new TreeMap<>(stackTune == null ? Map.of() : stackTune.values());
        Float uptilt = frameUptilt(framePart);
        if (uptilt != null) {
            values.put(TuneMapping.CAMERA_UPTILT, uptilt);
        }
        Map<String, Float> clean = CameraMount.sanitize(values, build);
        return clean.isEmpty() ? FlightTune.EMPTY : new FlightTune(clean);
    }

    public static @Nullable Float frameUptilt(ItemStack framePart) {
        FlightTune tune = framePart.isEmpty() ? null : framePart.get(CoreComponents.FLIGHT_TUNE.get());
        Float value = tune == null ? null : tune.values().get(TuneMapping.CAMERA_UPTILT);
        return value != null && Float.isFinite(value) ? value : null;
    }

    public static boolean onlyCameraUptilt(FlightTune tune) {
        return tune.values().size() == 1 && tune.values().containsKey(TuneMapping.CAMERA_UPTILT);
    }

    public static ItemStack preset(DroneBuild build) {
        Optional<Item> item = ContentIds.item(ContentIds.DRONE);
        if (item.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack drone = new ItemStack(item.get());
        drone.set(CoreComponents.DRONE_BUILD.get(), build);
        drone.set(CoreComponents.BATTERY_CHARGE.get(), FULL_CHARGE);
        return drone;
    }

    public static List<ItemStack> disassemble(ItemStack drone) {
        DroneBuild build = buildOf(drone);
        List<@Nullable PartInfo> layout = BuildSlots.layout(build);
        PartWear wear = wear(drone);
        List<ItemStack> stacks = new ArrayList<>(layout.size());
        for (int slot = 0; slot < layout.size(); slot++) {
            PartInfo part = layout.get(slot);
            if (part == null || part.missing()) {
                stacks.add(ItemStack.EMPTY);
            } else if (slot < PartWear.SLOTS) {
                ItemStack stack = ContentItems.stack(part);
                stacks.add(stack.isEmpty() ? stack : PartCondition.with(stack, wear.slot(slot)));
            } else {
                stacks.add(ContentItems.stack(part));
            }
        }
        ItemStack stackPart = stacks.get(BuildSlots.STACK);
        ItemStack framePart = stacks.get(BuildSlots.FRAME);
        FlightTune tune = drone.get(CoreComponents.FLIGHT_TUNE.get());
        if (tune != null && !tune.equals(FlightTune.EMPTY)) {
            Map<String, Float> rest = new TreeMap<>(tune.values());
            Float uptilt = framePart.isEmpty() ? null : rest.remove(TuneMapping.CAMERA_UPTILT);
            if (uptilt != null) {
                framePart.set(CoreComponents.FLIGHT_TUNE.get(), new FlightTune(Map.of(TuneMapping.CAMERA_UPTILT, uptilt)));
            }
            if (!rest.isEmpty() && !stackPart.isEmpty()) {
                stackPart.set(CoreComponents.FLIGHT_TUNE.get(), new FlightTune(rest));
            }
        }
        UUID id = drone.get(CoreComponents.DRONE_ID.get());
        if (id != null && !stackPart.isEmpty()) {
            stackPart.set(CoreComponents.DRONE_ID.get(), id);
        }
        BindComponents.copy(drone, stackPart);
        CoatingComponents.toPart(drone, stackPart);
        if (build.edited() && !stackPart.isEmpty()) {
            stackPart.set(CreativeModule.SPEC.get(), build.creative());
        }
        Component name = drone.get(DataComponents.CUSTOM_NAME);
        if (name != null && !framePart.isEmpty()) {
            framePart.set(DataComponents.CUSTOM_NAME, name);
        }
        if (!framePart.isEmpty()) {
            PaintItems.store(framePart, PaintItems.paint(drone));
        }
        if (hasOwnCameraBattery(build)) {
            ItemStack camera = stacks.get(BuildSlots.accessorySlot(AccessoryMount.TOP));
            if (!camera.isEmpty()) {
                storeCameraCharge(camera, cameraCharge(drone));
            }
        }
        if (!hasBattery(drone)) {
            stacks.set(BuildSlots.BATTERY, ItemStack.EMPTY);
            return stacks;
        }
        if (!stacks.get(BuildSlots.BATTERY).isEmpty()) {
            Batteries.storeCharge(stacks.get(BuildSlots.BATTERY), charge(drone));
        }
        return stacks;
    }
}
