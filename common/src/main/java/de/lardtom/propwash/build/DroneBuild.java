package de.lardtom.propwash.build;

import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.creative.CreativeFlight;
import de.lardtom.propwash.creative.CreativeSpec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record DroneBuild(FrameType frame, StackType stack, MotorType motor, PropType prop, VideoType video, BatteryType battery,
                         Accessories accessories, CreativeSpec creative) {
    public static final int MOTOR_COUNT = 4;

    public DroneBuild {
        accessories = Objects.requireNonNullElse(accessories, Accessories.NONE);
        creative = stack != null && stack.creative() ? Objects.requireNonNullElse(creative, CreativeSpec.EMPTY) : CreativeSpec.EMPTY;
    }

    public DroneBuild(FrameType frame, StackType stack, MotorType motor, PropType prop, VideoType video, BatteryType battery,
                      Accessories accessories) {
        this(frame, stack, motor, prop, video, battery, accessories, CreativeSpec.EMPTY);
    }

    public DroneBuild(FrameType frame, StackType stack, MotorType motor, PropType prop, VideoType video, BatteryType battery) {
        this(frame, stack, motor, prop, video, battery, Accessories.NONE, CreativeSpec.EMPTY);
    }

    public static final DroneBuild WHOOP = new DroneBuild(FrameType.WHOOP_65, StackType.AIO_WHOOP, MotorType.M0802_22000,
            PropType.P31_4B, VideoType.ANALOG_NANO, BatteryType.B1S_300_HV);
    public static final DroneBuild CINEWHOOP = new DroneBuild(FrameType.CINEWHOOP_3, StackType.AIO_20, MotorType.M1404_4600,
            PropType.P3_3B, VideoType.DIGITAL_LITE, BatteryType.B4S_650);
    public static final DroneBuild FREESTYLE = new DroneBuild(FrameType.FREESTYLE_5, StackType.STACK_30, MotorType.M2207_1750,
            PropType.P51_3B, VideoType.DIGITAL_HD, BatteryType.B6S_1300);
    public static final DroneBuild RACER = new DroneBuild(FrameType.RACE_5, StackType.STACK_30, MotorType.M2306_1860,
            PropType.P51_3B, VideoType.ANALOG_RACE, BatteryType.B6S_1100);
    public static final DroneBuild LONG_RANGE = new DroneBuild(FrameType.LONG_RANGE_7, StackType.STACK_30, MotorType.M2807_1300,
            PropType.P7_2B, VideoType.DIGITAL_HD, BatteryType.B6S_4000_LION);

    public static final List<DroneBuild> PRESETS = List.of(WHOOP, CINEWHOOP, FREESTYLE, RACER, LONG_RANGE);

    public double totalMassGrams() {
        return this.frame.massGrams() + this.stack.massGrams() + this.video.massGrams() + this.battery.massGrams()
                + MOTOR_COUNT * (this.motor.massGrams() + this.prop.massGrams()) + this.accessories.massGrams();
    }

    public double dryMassGrams() {
        return this.totalMassGrams() - this.battery.massGrams();
    }

    public DroneBuild withBattery(BatteryType battery) {
        return new DroneBuild(this.frame, this.stack, this.motor, this.prop, this.video, battery, this.accessories, this.creative);
    }

    public DroneBuild withProp(PropType prop) {
        return new DroneBuild(this.frame, this.stack, this.motor, prop, this.video, this.battery, this.accessories, this.creative);
    }

    public DroneBuild withAccessories(Accessories accessories) {
        return new DroneBuild(this.frame, this.stack, this.motor, this.prop, this.video, this.battery, accessories, this.creative);
    }

    public DroneBuild withCreative(@Nullable CreativeSpec creative) {
        return new DroneBuild(this.frame, this.stack, this.motor, this.prop, this.video, this.battery, this.accessories, creative);
    }

    public boolean isCreative() {
        return this.stack.creative();
    }

    public boolean edited() {
        return !this.creative.isEmpty();
    }

    public DroneBuild flight() {
        return this.creative.isEmpty() ? this : CreativeFlight.flight(this);
    }

    public double @Nullable [] inertiaOverride() {
        return this.creative.isEmpty() ? null : CreativeFlight.inertia(this);
    }

    public DroneBuild withAccessory(AccessoryType accessory) {
        return this.withAccessories(this.accessories.with(accessory));
    }

    public DroneBuild withoutAccessories() {
        return this.accessories.isEmpty() ? this : this.withAccessories(Accessories.NONE);
    }

    public List<Issue> problems() {
        List<Issue> issues = new ArrayList<>();
        if (!this.missingParts().isEmpty()) {
            issues.add(Issue.MISSING_PART);
        }
        if (!this.frame.motorMounts.contains(this.motor.mount)) {
            issues.add(Issue.MOTOR_MOUNT);
        }
        if (this.prop.diameterMm > this.frame.maxPropMm) {
            issues.add(Issue.PROP_TOO_LARGE);
        }
        if (this.prop.diameterMm < this.frame.minPropMm) {
            issues.add(Issue.PROP_TOO_SMALL);
        }
        if (!Mounts.propFits(this.motor.definition().propMount(), this.prop.definition().mount())) {
            issues.add(Issue.PROP_MOUNT);
        }
        if (!this.stack.fits(this.frame)) {
            issues.add(Issue.STACK_MOUNT);
        }
        if (!BatteryFit.allowed(BatteryFit.check(this.frame, this.battery))) {
            issues.add(Issue.BATTERY_FRAME);
        }
        for (AccessoryMount mount : this.accessories.mounts()) {
            if (!this.frame.accessoryMounts.contains(mount)) {
                issues.add(Issue.ACCESSORY_MOUNT);
                break;
            }
        }
        return issues;
    }

    public List<Issue> warnings() {
        List<Issue> issues = new ArrayList<>();
        if (this.battery.cells > this.motor.maxCells) {
            issues.add(Issue.MOTOR_OVERVOLTAGE);
        }
        if (this.battery.cells < this.motor.minCells) {
            issues.add(Issue.MOTOR_UNDERVOLTAGE);
        }
        if (this.battery.cells > this.stack.maxCells || this.battery.cells < this.stack.minCells) {
            issues.add(Issue.STACK_VOLTAGE);
        }
        if (this.motor.maxCurrent > this.stack.escCurrentPerMotor * 1.15) {
            issues.add(Issue.ESC_UNDERSIZED);
        }
        if (this.motor.maxCurrent * MOTOR_COUNT > this.battery.maxContinuousCurrent() * 1.3) {
            issues.add(Issue.BATTERY_WEAK);
        }
        if (BatteryFit.check(this.frame, this.battery) == BatteryFit.Result.WEAK) {
            issues.add(Issue.BATTERY_UNDERSIZED);
        }
        return issues;
    }

    public boolean buildable() {
        return this.problems().isEmpty();
    }

    public List<Issue> problems(PartCompatibility rule) {
        return rule.blocking(this.problems());
    }

    public List<Issue> liftedProblems(PartCompatibility rule) {
        return rule.lifted(this.problems());
    }

    public boolean buildable(PartCompatibility rule) {
        return this.problems(rule).isEmpty();
    }

    public List<PartInfo> parts() {
        List<PartInfo> parts = new ArrayList<>(List.of(this.frame, this.stack, this.motor, this.prop, this.video, this.battery));
        parts.addAll(this.accessories.list());
        return parts;
    }

    public List<PartInfo> missingParts() {
        List<PartInfo> missing = new ArrayList<>();
        for (PartInfo part : this.parts()) {
            if (part.missing()) {
                missing.add(part);
            }
        }
        return missing;
    }

    public enum Issue {
        MOTOR_MOUNT,
        PROP_TOO_LARGE,
        STACK_MOUNT,
        MOTOR_OVERVOLTAGE,
        MOTOR_UNDERVOLTAGE,
        STACK_VOLTAGE,
        ESC_UNDERSIZED,
        BATTERY_WEAK,
        BATTERY_FRAME,
        BATTERY_UNDERSIZED,
        ACCESSORY_MOUNT,
        MISSING_PART,
        PROP_TOO_SMALL,
        PROP_MOUNT;

        public String translationKey() {
            return "propwash.build.issue." + this.name().toLowerCase(java.util.Locale.ROOT);
        }

        public boolean compatibility() {
            return this != MISSING_PART;
        }
    }
}
