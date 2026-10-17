package de.lardtom.propwash.content;

import de.lardtom.propwash.api.part.AirframeGeometry;
import de.lardtom.propwash.api.part.BatteryDefinition;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.MotorThermal;
import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.PropMaterial;
import de.lardtom.propwash.api.part.StackDefinition;
import de.lardtom.propwash.api.part.VideoDefinition;
import de.lardtom.propwash.api.part.VideoLink;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PartNames;
import de.lardtom.propwash.sim.PropFailure;
import de.lardtom.propwash.video.FovMath;
import de.lardtom.propwash.video.vtx.VtxTable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class PartDataLines {
    public static final String PREFIX = "propwash.parts.tooltip.";

    private PartDataLines() {
    }

    public static void append(PartInfo part, Consumer<Component> out) {
        if (part.missing()) {
            out.accept(Component.translatable(PartNames.MISSING_PART, part.id().toString()).withStyle(ChatFormatting.RED));
            return;
        }
        switch (part.definition()) {
            case FrameDefinition frame -> frame(frame, out);
            case StackDefinition stack -> stack.escBurstAmps().ifPresent(amps ->
                    out.accept(line("esc_burst", Component.translatable(PartTooltips.PREFIX_UNIT + "amps_per_motor", Numbers.of(amps, 0)))));
            case MotorDefinition motor -> motor(motor, out);
            case PropDefinition prop -> prop(prop, out);
            case BatteryDefinition battery -> battery(battery, out);
            case VideoDefinition video -> video(video, out);
            default -> {
            }
        }
        durability(part, out);
    }

    private static void frame(FrameDefinition frame, Consumer<Component> out) {
        if (frame.minPropMm() > 0) {
            out.accept(line("prop_range", Component.translatable(PREFIX + "range_inches", Numbers.of(frame.minPropMm() / 25.4, 1),
                    Numbers.of(frame.maxPropMm() / 25.4, 1))));
        }
        if (frame.geometry().hasExplicitArms()) {
            out.accept(line("shape", Component.translatable(PREFIX + "shape." + frame.shape().key())));
        }
        if (batteryUnderInsteadOfOnTop(frame)) {
            out.accept(line("battery_mount", Component.translatable(PREFIX + "battery_mount.under")));
        }
    }

    static boolean batteryUnderInsteadOfOnTop(FrameDefinition frame) {
        return !frame.geometry().batteryOnTop() && AirframeGeometry.defaultBatteryOnTop(frame.wheelbaseMm());
    }

    private static void motor(MotorDefinition motor, Consumer<Component> out) {
        if (!motor.propMount().equals(Mounts.PROP_UNIVERSAL)) {
            out.accept(line("prop_mount", PartNames.propMount(motor.propMount())));
        }
        MotorThermal thermal = motor.thermal();
        if (thermal.derateStartC() != MotorThermal.DEFAULT.derateStartC() || thermal.maxTempC() != MotorThermal.DEFAULT.maxTempC()) {
            out.accept(line("thermal", Component.translatable(PREFIX + "thermal_limits", Numbers.of(thermal.derateStartC(), 0),
                    Numbers.of(thermal.maxTempC(), 0))));
        }
    }

    private static void prop(PropDefinition prop, Consumer<Component> out) {
        if (prop.material() != PropMaterial.POLYCARBONATE) {
            out.accept(line("material", Component.translatable(PREFIX + "material." + prop.material().key())));
        }
        if (prop.stiffness() != prop.material().defaultStiffness()) {
            out.accept(line("stiffness", Component.translatable(PREFIX + "factor", Numbers.of(prop.stiffness(), 2))));
        }
        out.accept(line("failure", Component.translatable(PREFIX + "failure." + PropFailure.of(prop).mode().key())));
        if (!prop.mount().equals(Mounts.PROP_UNIVERSAL)) {
            out.accept(line("prop_mount", PartNames.propMount(prop.mount())));
        }
        prop.reverseThrust().ifPresent(ratio -> out.accept(line("reverse_thrust",
                Component.translatable(PREFIX + "percent", Math.round(ratio * 100.0)))));
    }

    private static void battery(BatteryDefinition battery, Consumer<Component> out) {
        if (!battery.connector().isEmpty()) {
            out.accept(line("connector", Component.literal(battery.connector())));
        }
        battery.packSize().ifPresent(size -> out.accept(line("pack_size", Component.translatable(PREFIX + "size_mm",
                Numbers.of(size.lengthMm(), 0), Numbers.of(size.widthMm(), 0), Numbers.of(size.heightMm(), 0)))));
    }

    private static void video(VideoDefinition video, Consumer<Component> out) {
        out.accept(line("camera_fov", Component.translatable(PartTooltips.PREFIX_UNIT + "degrees", Numbers.of(FovMath.declaredHorizontalDegrees(video), 0))));
        VtxTable table = VtxTable.of(video.link() == VideoLink.DIGITAL, video.vtxPowerMw(), video.vtxLevelsMw(), 1.0, video.basics().massGrams());
        if (table.count() > 1) {
            out.accept(line("vtx_power", Component.translatable(PREFIX + "vtx_levels", table.minMw(), table.maxMw(), table.ratedMw())));
        } else {
            out.accept(line("vtx_power", Component.translatable(PREFIX + "milliwatts", table.ratedMw())));
        }
    }

    private static void durability(PartInfo part, Consumer<Component> out) {
        PartBasics basics = part.definition().basics();
        double standard = part.definition() instanceof PropDefinition prop ? prop.material().defaultDurability() : 1.0;
        if (basics.durability() != standard) {
            out.accept(line("durability", Component.translatable(PREFIX + "factor", Numbers.of(basics.durability(), 2))));
        }
    }

    private static Component line(String key, Component value) {
        return Component.translatable(PREFIX + key, value.copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY);
    }
}
