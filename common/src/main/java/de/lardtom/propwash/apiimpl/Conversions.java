package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.build.Accessories;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.core.FlightTune;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Conversions {
    private Conversions() {
    }

    public static BuildSpec spec(DroneBuild build) {
        EnumMap<AccessorySlot, Identifier> accessories = new EnumMap<>(AccessorySlot.class);
        for (Map.Entry<AccessoryMount, AccessoryType> entry : build.accessories().byMount().entrySet()) {
            accessories.put(entry.getKey().slot(), entry.getValue().id());
        }
        return new BuildSpec(build.frame().id(), build.stack().id(), build.motor().id(), build.prop().id(), build.video().id(),
                build.battery().id(), accessories);
    }

    public static List<Identifier> unknownParts(BuildSpec spec) {
        List<Identifier> unknown = new ArrayList<>();
        check(unknown, spec.frame(), FrameType.class);
        check(unknown, spec.stack(), StackType.class);
        check(unknown, spec.motor(), MotorType.class);
        check(unknown, spec.prop(), PropType.class);
        check(unknown, spec.video(), VideoType.class);
        check(unknown, spec.battery(), BatteryType.class);
        for (Identifier accessory : spec.accessories().values()) {
            check(unknown, accessory, AccessoryType.class);
        }
        return List.copyOf(unknown);
    }

    public static Optional<DroneBuild> build(BuildSpec spec) {
        Optional<FrameType> frame = PartIndex.byId(FrameType.class, spec.frame());
        Optional<StackType> stack = PartIndex.byId(StackType.class, spec.stack());
        Optional<MotorType> motor = PartIndex.byId(MotorType.class, spec.motor());
        Optional<PropType> prop = PartIndex.byId(PropType.class, spec.prop());
        Optional<VideoType> video = PartIndex.byId(VideoType.class, spec.video());
        Optional<BatteryType> battery = PartIndex.byId(BatteryType.class, spec.battery());
        if (frame.isEmpty() || stack.isEmpty() || motor.isEmpty() || prop.isEmpty() || video.isEmpty() || battery.isEmpty()) {
            return Optional.empty();
        }
        Accessories accessories = Accessories.NONE;
        for (Map.Entry<AccessorySlot, Identifier> entry : spec.accessories().entrySet()) {
            Optional<AccessoryType> type = PartIndex.byId(AccessoryType.class, entry.getValue());
            if (type.isEmpty() || !type.get().fits(AccessoryMount.of(entry.getKey()))) {
                return Optional.empty();
            }
            accessories = accessories.with(type.get());
        }
        return Optional.of(new DroneBuild(frame.get(), stack.get(), motor.get(), prop.get(), video.get(), battery.get(), accessories));
    }

    public static PaintSpec paint(DronePaint paint) {
        LinkedHashMap<String, Integer> colors = new LinkedHashMap<>();
        for (Map.Entry<PaintSlot, Integer> entry : paint.colors().entrySet()) {
            colors.put(entry.getKey().key(), entry.getValue());
        }
        return new PaintSpec(colors);
    }

    public static DronePaint paint(PaintSpec paint) {
        EnumMap<PaintSlot, Integer> colors = new EnumMap<>(PaintSlot.class);
        for (Map.Entry<String, Integer> entry : paint.colors().entrySet()) {
            PaintSlot.byKey(entry.getKey()).ifPresent(slot -> colors.put(slot, entry.getValue()));
        }
        return DronePaint.of(colors);
    }

    public static TuneSpec tune(FlightTune tune) {
        return new TuneSpec(tune.values());
    }

    public static FlightTune tune(TuneSpec tune) {
        return new FlightTune(tune.values());
    }

    private static void check(List<Identifier> unknown, Identifier id, Class<? extends PartInfo> type) {
        if (PartIndex.byId(type, id).isEmpty()) {
            unknown.add(id);
        }
    }
}
