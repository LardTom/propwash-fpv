package de.lardtom.propwash.content;

import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.ActionCamSpec;
import de.lardtom.propwash.build.BatteryFit;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.MountSize;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PartNames;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.interact.GripperSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class PartTooltips {
    private static final String PREFIX = "propwash.content.tooltip.";
    static final String PREFIX_UNIT = PREFIX + "unit.";

    private PartTooltips() {
    }

    public static void append(PartInfo part, Consumer<Component> out) {
        PartCategory category = PartCategory.of(part);
        Object detail = part instanceof AccessoryType accessory ? accessoryMountName(accessory.mount) : category.requiredCount();
        out.accept(Component.translatable(PREFIX + "category." + category.key(), detail)
                .withStyle(ChatFormatting.DARK_AQUA));
        specs(part, out);
        fits(part, out);
    }

    public static void specs(PartInfo part, Consumer<Component> out) {
        switch (part) {
            case FrameType frame -> frame(frame, out);
            case StackType stack -> stack(stack, out);
            case MotorType motor -> motor(motor, out);
            case PropType prop -> prop(prop, out);
            case BatteryType battery -> battery(battery, out);
            case VideoType video -> video(video, out);
            case AccessoryType accessory -> accessory(accessory, out);
            default -> {
            }
        }
        out.accept(line("weight", grams(part.massGrams())));
        PartDataLines.append(part, out);
    }

    public static void fits(PartInfo part, Consumer<Component> out) {
        switch (part) {
            case FrameType frame -> frameBatteries(frame, out);
            case StackType stack when stack.creative() -> out.accept(help("fits.frames", Component.translatable(ItemHelp.PREFIX + "fits.every_frame")));
            case StackType stack -> fits(stack::fits, out);
            case MotorType motor -> fits(frame -> frame.motorMounts.contains(motor.mount), out);
            case PropType prop -> fits(frame -> frame.maxPropMm >= prop.diameterMm && frame.minPropMm <= prop.diameterMm, out);
            case BatteryType battery -> batteryFrames(battery, out);
            case VideoType video -> videoGoggles(video, out);
            case AccessoryType accessory -> fits(frame -> frame.accessoryMounts.contains(accessory.mount), out);
            default -> {
            }
        }
    }

    public static void frameBatteries(FrameType frame, Consumer<Component> out) {
        List<Component> ok = new ArrayList<>();
        for (BatteryType battery : BatteryFit.fitting(frame, BatteryFit.Result.OK)) {
            ok.add(Component.translatable(battery.translationKey()));
        }
        List<Component> weak = new ArrayList<>();
        for (BatteryType battery : BatteryFit.fitting(frame, BatteryFit.Result.WEAK)) {
            weak.add(Component.translatable(battery.translationKey()));
        }
        if (!ok.isEmpty()) {
            out.accept(help("fits.batteries", ItemHelp.joined(ok)));
        }
        if (!weak.isEmpty()) {
            out.accept(help("fits.weak", ItemHelp.joined(weak)));
        }
    }

    public static void batteryFrames(BatteryType battery, Consumer<Component> out) {
        List<Component> ok = new ArrayList<>();
        for (FrameType frame : BatteryFit.framesFor(battery, BatteryFit.Result.OK)) {
            ok.add(frameShortName(frame));
        }
        List<Component> weak = new ArrayList<>();
        for (FrameType frame : BatteryFit.framesFor(battery, BatteryFit.Result.WEAK)) {
            weak.add(frameShortName(frame));
        }
        if (!ok.isEmpty()) {
            out.accept(help("fits.frames", ItemHelp.joined(ok)));
        }
        if (!weak.isEmpty()) {
            out.accept(help("fits.weak", ItemHelp.joined(weak)));
        }
    }

    public static void videoGoggles(VideoType video, Consumer<Component> out) {
        List<Component> goggles = new ArrayList<>();
        for (VideoSystem system : VideoSystem.values()) {
            if (system.receives(video.system)) {
                goggles.add(Component.translatable("item.propwash.goggles_" + system.name().toLowerCase(Locale.ROOT)));
            }
        }
        out.accept(help("fits.goggles", ItemHelp.joined(goggles)));
    }

    public static Component batteryFitReason(FrameType frame, BatteryType battery) {
        BatteryFit.Result result = BatteryFit.check(frame, battery);
        BatteryFit.Rule rule = BatteryFit.rule(frame);
        return switch (result) {
            case CELLS_LOW -> Component.translatable("propwash.build.battery_fit.cells_low", battery.cells, rule.minCells(), rule.maxCells());
            case CELLS_HIGH -> Component.translatable("propwash.build.battery_fit.cells_high", battery.cells, rule.maxCells());
            case TOO_HEAVY -> Component.translatable("propwash.build.battery_fit.too_heavy", Numbers.of(battery.massGrams(), 0),
                    Numbers.of(rule.maxMassGrams(), 0));
            case WEAK -> Component.translatable(BatteryFit.weakReason(frame, battery) == BatteryFit.WeakReason.CAPACITY
                    ? "propwash.build.battery_fit.weak_capacity" : "propwash.build.battery_fit.weak_cells");
            case OK -> Component.empty();
        };
    }

    private static Component help(String key, Component value) {
        return Component.translatable(ItemHelp.PREFIX + key, value.copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY);
    }

    public static Component charge(float charge) {
        float clamped = Math.max(0.0F, Math.min(1.0F, charge));
        ChatFormatting color = clamped > 0.5F ? ChatFormatting.GREEN : clamped > 0.2F ? ChatFormatting.YELLOW : ChatFormatting.RED;
        return Component.translatable(PREFIX + "charge",
                Component.translatable(PREFIX + "unit.percent", Math.round(clamped * 100.0F)).withStyle(color)).withStyle(ChatFormatting.GRAY);
    }

    public static Component videoSystemName(VideoSystem system) {
        return Component.translatable("propwash.content.video_system." + system.name().toLowerCase(Locale.ROOT));
    }

    public static Component frameShortName(FrameType frame) {
        return PartNames.frameShort(frame);
    }

    public static Component accessoryMountName(AccessoryMount mount) {
        return Component.translatable("propwash.content.mount.accessory." + mount.key());
    }

    public static Component motorMountName(MountSize.Motor mount) {
        return PartNames.motorMount(mount);
    }

    public static Component stackMountName(MountSize.Stack mount) {
        return PartNames.stackMount(mount);
    }

    public static Component cells(int min, int max) {
        return min == max
                ? Component.translatable(PREFIX + "cells_single", min)
                : Component.translatable(PREFIX + "cells_range", min, max);
    }

    public static Component grams(double grams) {
        return Component.translatable(PREFIX + "unit.grams", Numbers.of(grams, 1));
    }

    private static void frame(FrameType frame, Consumer<Component> out) {
        out.accept(line("wheelbase", Component.translatable(PREFIX + "unit.mm", frame.wheelbaseMm)));
        out.accept(line("max_prop", inches(frame.maxPropMm)));
        MutableComponent mounts = Component.empty();
        boolean first = true;
        for (MountSize.Motor mount : MountSize.Motor.values()) {
            if (frame.motorMounts.contains(mount)) {
                if (!first) {
                    mounts.append(", ");
                }
                mounts.append(motorMountName(mount));
                first = false;
            }
        }
        out.accept(line("motor_mount", mounts));
        MutableComponent stacks = Component.empty();
        boolean firstStack = true;
        for (MountSize.Stack mount : MountSize.Stack.values()) {
            if (frame.stackMounts.contains(mount)) {
                if (!firstStack) {
                    stacks.append(", ");
                }
                stacks.append(stackMountName(mount));
                firstStack = false;
            }
        }
        out.accept(line("stack_mount", stacks));
        if (frame.ducted) {
            out.accept(Component.translatable(PREFIX + "ducted").withStyle(ChatFormatting.GRAY));
        }
    }

    private static void stack(StackType stack, Consumer<Component> out) {
        out.accept(line("esc_current", Component.translatable(PREFIX + "unit.amps_per_motor", Numbers.of(stack.escCurrentPerMotor, 0))));
        out.accept(line("cells", cells(stack.minCells, stack.maxCells)));
        out.accept(line("format", stackMountName(stack.mount)));
    }

    private static void motor(MotorType motor, Consumer<Component> out) {
        out.accept(line("motor", Component.translatable(PREFIX + "motor_spec", motor.statorLabel, motor.kv)));
        out.accept(line("cells", cells(motor.minCells, motor.maxCells)));
        out.accept(line("max_current", Component.translatable(PREFIX + "unit.amps", Numbers.of(motor.maxCurrent, 0))));
    }

    private static void prop(PropType prop, Consumer<Component> out) {
        out.accept(line("diameter", Component.translatable(PREFIX + "unit.mm_inches", prop.diameterMm, Numbers.of(prop.diameterMm / 25.4, 1))));
        out.accept(line("pitch", Component.translatable(PREFIX + "unit.inches", Numbers.of(prop.pitchInch, 1))));
        out.accept(line("blades", Component.literal(String.valueOf(prop.blades))));
    }

    private static void battery(BatteryType battery, Consumer<Component> out) {
        out.accept(line("battery", Component.translatable(PREFIX + "battery_spec", battery.cells, battery.capacityMah, battery.cRating)));
        out.accept(line("chemistry", PartNames.chemistry(battery.chemistry)));
        out.accept(line("voltage", Component.translatable(PREFIX + "unit.volts",
                Numbers.of(battery.cells * battery.chemistry.fullCellVoltage, 1))));
        out.accept(line("continuous_current", Component.translatable(PREFIX + "unit.amps", Numbers.of(battery.maxContinuousCurrent(), 0))));
    }

    private static void video(VideoType video, Consumer<Component> out) {
        out.accept(line("video_system", videoSystemName(video.system)));
        out.accept(line("latency", Component.translatable(PREFIX + "unit.ms", video.latencyMs)));
        out.accept(line("range", Component.translatable(PREFIX + "unit.blocks", video.rangeBlocks)));
        out.accept(line("image_quality", Component.translatable(PREFIX + "unit.percent", Math.round(video.imageQuality * 100.0))));
    }

    private static void accessory(AccessoryType accessory, Consumer<Component> out) {
        out.accept(line("accessory_mount", accessoryMountName(accessory.mount)));
        if (accessory.isFlashbackCam()) {
            out.accept(line("recording", Component.translatable(PREFIX + "recording_flashback")));
        }
        accessory.camera().ifPresent(camera -> {
            out.accept(line("recording", Component.translatable(PREFIX + "recording_format", camera.maxHeight(), camera.maxFps())));
            out.accept(line("stabilization", Component.translatable(PREFIX + "stabilization."
                    + (camera.horizonLevelling() ? "horizon" : camera.stabilization() ? "electronic" : "none"))));
            out.accept(line("sensor", Component.translatable(PREFIX + "unit.sensor", Numbers.of(1.0 / camera.sensorInches(), 1))));
            out.accept(line("fov", Component.translatable(PREFIX + "unit.degrees", Numbers.of(camera.fovDegrees(), 0))));
            out.accept(line("image_quality", Component.translatable(PREFIX + "unit.percent", Math.round(camera.imageQuality() * 100.0))));
            out.accept(line("low_light_noise", Component.translatable(PREFIX + "unit.percent", Math.round(camera.lowLightNoise() * 100.0))));
            if (camera.ownBattery()) {
                out.accept(line("own_battery", Component.translatable(PREFIX + "record_time",
                        Numbers.of(camera.recordMinutes(ActionCamSpec.FULL_HD), 0), Numbers.of(camera.recordMinutes(camera.maxHeight()), 0),
                        camera.maxHeight())));
            }
        });
        if (accessory.isLight()) {
            out.accept(line("leds", Component.literal(String.valueOf(accessory.ledCount))));
        }
        if (accessory.isGripper()) {
            out.accept(line("payload", Component.translatable(PREFIX + "payload_value", Math.round(GripperSpec.MAX_PAYLOAD_GRAMS),
                    GripperSpec.MAX_STACKS)));
        }
        if (accessory.flightBatteryWatts > 0.0) {
            out.accept(line("flight_power", Component.translatable(PREFIX + "unit.watts", Numbers.of(accessory.flightBatteryWatts, 1))));
        }
    }

    public static void fits(Predicate<FrameType> predicate, Consumer<Component> out) {
        List<Component> names = new ArrayList<>();
        for (FrameType frame : FrameType.values()) {
            if (predicate.test(frame)) {
                names.add(frameShortName(frame));
            }
        }
        if (names.isEmpty()) {
            out.accept(Component.translatable(PREFIX + "fits_none").withStyle(ChatFormatting.RED));
            return;
        }
        MutableComponent joined = Component.empty();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                joined.append(", ");
            }
            joined.append(names.get(i));
        }
        out.accept(line("fits", joined));
    }

    private static Component inches(int mm) {
        return Component.translatable(PREFIX + "unit.inches", Numbers.of(mm / 25.4, 1));
    }

    private static Component line(String key, Component value) {
        return Component.translatable(PREFIX + key, value.copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY);
    }
}
