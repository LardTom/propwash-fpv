package de.lardtom.propwash.webexport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.ActionCamDefinition;
import de.lardtom.propwash.api.part.AirframeGeometry;
import de.lardtom.propwash.api.part.AnchorSpec;
import de.lardtom.propwash.api.part.BatteryDefinition;
import de.lardtom.propwash.api.part.CellChemistry;
import de.lardtom.propwash.api.part.DuctSpec;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.MotorArm;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.MotorThermal;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.StackDefinition;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.api.part.VideoAntenna;
import de.lardtom.propwash.api.part.VideoDefinition;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.AntennaRules;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static de.lardtom.propwash.webexport.ExportJson.id;
import static de.lardtom.propwash.webexport.ExportJson.num;

public final class PartData {
    public static final double NOMINAL_CELL_V = 3.7;
    public static final List<String> ARM_KEYS = List.of("front_left", "front_right", "rear_left", "rear_right");

    private PartData() {
    }

    public static JsonObject basics(PartBasics basics) {
        JsonObject out = new JsonObject();
        out.add("mass_g", num(basics.massGrams()));
        out.addProperty("stack_size", basics.stackSize());
        out.addProperty("sort", basics.sortOrder());
        out.addProperty("creative", basics.creativeListed());
        out.add("durability", num(basics.durability()));
        if (basics.recycle().isEmpty()) {
            out.add("recycle", JsonNull.INSTANCE);
        } else if (basics.recycle().get().none()) {
            out.addProperty("recycle", "none");
        } else {
            JsonObject recycle = new JsonObject();
            recycle.addProperty("item", basics.recycle().get().item().toString());
            recycle.addProperty("count", basics.recycle().get().count());
            out.add("recycle", recycle);
        }
        out.add("accent", basics.visual().accentRgb().isPresent()
                ? ExportJson.str(ExportJson.hex(basics.visual().accentRgb().getAsInt())) : JsonNull.INSTANCE);
        out.add("render_model", basics.visual().renderModel().<JsonElement>map(ExportJson::id).orElse(JsonNull.INSTANCE));
        return out;
    }

    public static JsonObject data(PartDefinition definition) {
        return switch (definition) {
            case FrameDefinition frame -> frame(frame);
            case StackDefinition stack -> stack(stack);
            case MotorDefinition motor -> motor(motor);
            case PropDefinition prop -> prop(prop);
            case BatteryDefinition battery -> battery(battery);
            case VideoDefinition video -> video(video);
            case AccessoryDefinition accessory -> accessory(accessory);
        };
    }

    public static JsonObject derived(PartDefinition definition) {
        JsonObject out = new JsonObject();
        switch (definition) {
            case FrameDefinition frame -> {
                out.add("size_class_in", num(frame.nominalPropInches()));
                out.addProperty("ducted", frame.ducted());
                out.add("shape_extent_mm", num(shapeExtent(frame)));
            }
            case StackDefinition stack -> out.add("max_power_w", num(stack.escContinuousAmps() * 4 * stack.maxCells() * NOMINAL_CELL_V));
            case MotorDefinition motor -> {
                out.addProperty("no_load_rpm_per_cell_v", motor.kv());
                out.add("max_power_w_at_nominal", num(motor.maxCurrentAmps() * motor.maxCells() * NOMINAL_CELL_V));
            }
            case PropDefinition prop -> {
                double inches = prop.diameterMm() / 25.4;
                double radius = prop.diameterMm() / 2000.0;
                out.add("diameter_in", num(inches));
                out.add("disc_area_m2", num(Math.PI * radius * radius));
                out.add("pitch_to_diameter", num(prop.pitchInch() / inches));
            }
            case BatteryDefinition battery -> {
                double nominal = PropwashApi.parts().chemistry(battery.chemistry()).map(CellChemistry::nominalCellVoltage).orElse(Double.NaN);
                double energy = battery.cells() * nominal * battery.capacityMah() / 1000.0;
                out.add("energy_wh", num(energy));
                out.add("max_continuous_a", num(battery.maxContinuousCurrent()));
                out.add("wh_per_kg", num(energy / (battery.massGrams() / 1000.0)));
            }
            case VideoDefinition video -> {
            }
            case AccessoryDefinition accessory -> {
            }
        }
        return out;
    }

    public static double shapeExtent(FrameDefinition frame) {
        double radius = frame.maxPropMm() / 2.0;
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (MotorArm arm : frame.geometry().armsOrTrueX(frame.wheelbaseMm())) {
            minX = Math.min(minX, arm.position().x() - radius);
            maxX = Math.max(maxX, arm.position().x() + radius);
            minZ = Math.min(minZ, arm.position().z() - radius);
            maxZ = Math.max(maxZ, arm.position().z() + radius);
        }
        return Math.max(maxX - minX, maxZ - minZ);
    }

    public static List<String> paintSlots(PartDefinition definition) {
        return switch (definition.kind()) {
            case FRAME -> List.of("frame", "tpu");
            case MOTOR -> List.of("motors");
            case PROP -> List.of("prop_fl", "prop_fr", "prop_rl", "prop_rr");
            case STACK -> List.of("stack");
            case BATTERY -> List.of("battery");
            case VIDEO -> List.of("camera", "antenna");
            case ACCESSORY -> List.of("accessories");
            default -> List.of();
        };
    }

    public static JsonArray vec(Vec3Mm v) {
        return ExportJson.nums(v.x(), v.y(), v.z());
    }

    private static JsonArray sortedIds(Collection<Identifier> ids) {
        List<String> out = new ArrayList<>();
        for (Identifier value : ids) {
            out.add(value.toString());
        }
        out.sort(String::compareTo);
        return ExportJson.strings(out);
    }

    private static JsonObject frame(FrameDefinition frame) {
        JsonObject out = new JsonObject();
        out.addProperty("role", frame.role().key());
        out.addProperty("shape", frame.shape().key());
        out.addProperty("wheelbase_mm", frame.wheelbaseMm());
        out.add("props_mm", ExportJson.ints(frame.minPropMm(), frame.maxPropMm()));
        out.add("motor_mounts", sortedIds(frame.motorMounts()));
        out.add("stack_mounts", sortedIds(frame.stackMounts()));
        JsonArray accessoryMounts = new JsonArray();
        for (AccessorySlot slot : AccessorySlot.values()) {
            if (frame.accessoryMounts().contains(slot)) {
                accessoryMounts.add(slot.key());
            }
        }
        out.add("accessory_mounts", accessoryMounts);
        AirframeGeometry geometry = frame.geometry();
        JsonObject arms = new JsonObject();
        List<MotorArm> list = geometry.armsOrTrueX(frame.wheelbaseMm());
        for (int i = 0; i < list.size() && i < ARM_KEYS.size(); i++) {
            JsonObject arm = new JsonObject();
            arm.add("pos", vec(list.get(i).position()));
            arm.addProperty("spin", list.get(i).spin().key());
            arms.add(ARM_KEYS.get(i), arm);
        }
        out.add("arms", arms);
        out.addProperty("arms_explicit", geometry.hasExplicitArms());
        JsonObject battery = new JsonObject();
        battery.addProperty("on_top", geometry.batteryOnTop());
        battery.add("plate_mm", num(geometry.batteryPlateMm()));
        out.add("battery", battery);
        out.add("electronics_mm", num(geometry.electronicsMm()));
        JsonObject camera = new JsonObject();
        camera.add("pos", geometry.cameraMm().<JsonElement>map(PartData::vec).orElse(JsonNull.INSTANCE));
        camera.add("tilt_deg", num(geometry.cameraTiltDeg()));
        PropwashApi.builds().uptiltRange(frame.id()).ifPresent(range -> {
            camera.add("tilt_min_deg", num(range.minDeg()));
            camera.add("tilt_max_deg", num(range.maxDeg()));
        });
        out.add("camera", camera);
        out.add("center_of_mass_mm", vec(geometry.frameCenterOfMassMm()));
        JsonObject split = new JsonObject();
        split.add("arms", num(geometry.massSplit().arms()));
        split.add("plate", num(geometry.massSplit().plate()));
        split.add("duct", num(geometry.massSplit().duct()));
        out.add("mass_split", split);
        JsonObject anchors = new JsonObject();
        for (Map.Entry<AccessorySlot, AnchorSpec> entry : geometry.accessoryAnchors().entrySet()) {
            JsonObject anchor = new JsonObject();
            anchor.add("pos", ExportJson.nums(entry.getValue().x(), entry.getValue().y(), entry.getValue().z()));
            anchor.add("tilt_deg", num(entry.getValue().tiltDeg()));
            anchor.add("length_mm", num(entry.getValue().lengthMm()));
            anchors.add(entry.getKey().key(), anchor);
        }
        out.add("accessory_anchors", anchors);
        AnchorSpec holder = AntennaRules.anchor(frame);
        JsonObject antenna = new JsonObject();
        antenna.add("pos", ExportJson.nums(holder.x(), holder.y(), holder.z()));
        antenna.add("tilt_deg", num(holder.tiltDeg()));
        antenna.addProperty("explicit", AntennaRules.explicitAnchor(frame));
        antenna.add("max_length_mm", num(AntennaRules.maxLengthMm(frame)));
        antenna.add("pair_lean_deg", num(AntennaRules.pairLeanDeg(frame)));
        out.add("antenna_anchor", antenna);
        JsonObject aero = new JsonObject();
        aero.add("frontal_area_m2", num(frame.aero().frontalAreaM2()));
        aero.add("drag_coefficient", num(frame.aero().dragCoefficient()));
        aero.add("side_area_m2", num(frame.aero().sideAreaM2()));
        aero.add("top_area_m2", num(frame.aero().topAreaM2()));
        out.add("aero", aero);
        out.add("duct", frame.duct().<JsonElement>map(PartData::duct).orElse(JsonNull.INSTANCE));
        out.add("impact_tolerance", num(frame.impactTolerance()));
        JsonObject fit = new JsonObject();
        fit.add("cells", ExportJson.ints(frame.batteryFit().minCells(), frame.batteryFit().maxCells()));
        fit.add("max_mass_g", num(frame.batteryFit().maxMassGrams()));
        fit.addProperty("preferred_min_cells", frame.batteryFit().preferredMinCells());
        fit.addProperty("preferred_min_capacity_mah", frame.batteryFit().preferredMinCapacityMah());
        out.add("battery_fit", fit);
        return out;
    }

    private static JsonObject duct(DuctSpec duct) {
        JsonObject out = new JsonObject();
        out.add("thrust_gain", num(duct.thrustGain()));
        out.add("clearance_mm", num(duct.clearanceMm()));
        out.add("translational_lift_gain", num(duct.translationalLiftGain()));
        out.add("wash_loss", num(duct.washLoss()));
        out.add("wash_noise", num(duct.washNoise()));
        out.add("restitution", num(duct.restitution()));
        out.add("friction", num(duct.friction()));
        return out;
    }

    private static JsonObject stack(StackDefinition stack) {
        JsonObject out = new JsonObject();
        out.add("mount", id(stack.mount()));
        out.add("esc_continuous_a", num(stack.escContinuousAmps()));
        out.add("cells", ExportJson.ints(stack.minCells(), stack.maxCells()));
        out.addProperty("all_in_one", stack.allInOne());
        out.add("esc_burst_a", num(stack.escBurstAmps()));
        out.add("esc_resistance_ohm", num(stack.escResistanceOhm()));
        out.add("brownout_v", num(stack.brownoutVoltage()));
        out.add("board_size_mm", num(stack.boardSizeMm()));
        return out;
    }

    private static JsonObject motor(MotorDefinition motor) {
        JsonObject out = new JsonObject();
        out.addProperty("stator", motor.statorLabel());
        out.add("stator_mm", ExportJson.nums(motor.statorDiameterMm(), motor.statorHeightMm()));
        out.addProperty("kv", motor.kv());
        out.add("mount", id(motor.mount()));
        out.add("max_current_a", num(motor.maxCurrentAmps()));
        out.add("resistance_ohm", num(motor.resistanceOhm()));
        out.add("cells", ExportJson.ints(motor.minCells(), motor.maxCells()));
        out.add("prop_mount", id(motor.propMount()));
        MotorThermal thermal = motor.thermal();
        JsonObject heat = new JsonObject();
        heat.add("heat_capacity_j_per_k", num(thermal.heatCapacityJPerK()));
        heat.add("cooling_w_per_k", num(thermal.coolingWPerK()));
        heat.add("cooling_w_per_k_per_rad_s", num(thermal.coolingWPerKPerRadS()));
        heat.add("derate_start_c", num(thermal.derateStartC()));
        heat.add("max_temp_c", num(thermal.maxTempC()));
        heat.add("wear_per_s_at_max", num(thermal.wearPerSecondAtMax()));
        out.add("thermal", heat);
        return out;
    }

    private static JsonObject prop(PropDefinition prop) {
        JsonObject out = new JsonObject();
        out.addProperty("diameter_mm", prop.diameterMm());
        out.add("pitch_in", num(prop.pitchInch()));
        out.addProperty("blades", prop.blades());
        out.add("ct", num(prop.thrustCoefficient()));
        out.add("cp", num(prop.powerCoefficient()));
        out.addProperty("material", prop.material().key());
        out.add("stiffness", num(prop.stiffness()));
        out.add("mount", id(prop.mount()));
        return out;
    }

    private static JsonObject battery(BatteryDefinition battery) {
        JsonObject out = new JsonObject();
        out.addProperty("cells", battery.cells());
        out.addProperty("capacity_mah", battery.capacityMah());
        out.add("cell_resistance_ohm", num(battery.cellResistanceOhm()));
        out.addProperty("c_rating", battery.cRating());
        out.add("chemistry", id(battery.chemistry()));
        out.add("pack_mm", battery.packSize().<JsonElement>map(p -> ExportJson.nums(p.lengthMm(), p.widthMm(), p.heightMm()))
                .orElse(JsonNull.INSTANCE));
        out.addProperty("connector", battery.connector());
        return out;
    }

    private static JsonObject video(VideoDefinition video) {
        JsonObject out = new JsonObject();
        out.addProperty("link", video.link().key());
        out.addProperty("latency_ms", video.latencyMs());
        out.addProperty("range_blocks", video.rangeBlocks());
        out.add("image_quality", num(video.imageQuality()));
        out.add("camera_width_mm", num(video.cameraWidthMm()));
        out.add("camera_fov_deg", num(video.cameraFovDeg()));
        out.addProperty("vtx_power_mw", video.vtxPowerMw());
        out.add("power_w", video.powerW().isPresent() ? num(video.powerW().getAsDouble()) : JsonNull.INSTANCE);
        VideoAntenna spec = AntennaRules.antenna(video);
        JsonObject antenna = new JsonObject();
        antenna.addProperty("count", spec.count());
        antenna.add("length_mm", num(spec.lengthMm()));
        antenna.addProperty("cap", spec.cap().key());
        antenna.addProperty("kind", AntennaRules.kind(video).key());
        antenna.addProperty("derived", video.antenna().isEmpty());
        out.add("antenna", antenna);
        return out;
    }

    private static JsonObject accessory(AccessoryDefinition accessory) {
        JsonObject out = new JsonObject();
        out.addProperty("slot", accessory.slot().key());
        out.add("size_mm", ExportJson.nums(accessory.widthMm(), accessory.heightMm(), accessory.depthMm()));
        out.add("flight_battery_w", num(accessory.flightBatteryWatts()));
        out.add("frontal_area_m2", num(accessory.frontalAreaM2()));
        out.addProperty("leds", accessory.ledCount());
        JsonObject lens = new JsonObject();
        lens.add("x_mm", num(accessory.lensXMm()));
        lens.add("length_mm", num(accessory.lensLengthMm()));
        out.add("lens", lens);
        out.add("action_cam", accessory.actionCam().<JsonElement>map(PartData::actionCam).orElse(JsonNull.INSTANCE));
        JsonArray ledPath = new JsonArray();
        accessory.ledPathMm().forEach(point -> ledPath.add(vec(point)));
        out.add("led_path", ledPath);
        if (!accessory.frames().isEmpty()) {
            JsonArray frames = new JsonArray();
            accessory.frames().forEach(frame -> frames.add(frame.toString()));
            out.add("frames", frames);
        }
        if (accessory.recessMm() != 0.0) {
            out.add("recess_mm", num(accessory.recessMm()));
        }
        if (accessory.dropMm() != 0.0) {
            out.add("drop_mm", num(accessory.dropMm()));
        }
        if (AccessoryType.hangsStraight(accessory)) {
            out.addProperty("hangs_straight", true);
        }
        return out;
    }

    private static JsonObject actionCam(ActionCamDefinition cam) {
        JsonObject out = new JsonObject();
        out.addProperty("max_height", cam.maxHeight());
        out.addProperty("max_fps", cam.maxFps());
        out.addProperty("stabilization", cam.stabilization());
        out.addProperty("horizon_levelling", cam.horizonLevelling());
        out.add("own_battery_wh", num(cam.ownBatteryWh()));
        out.add("record_w", num(cam.recordWatts()));
        out.add("max_record_w", num(cam.maxRecordWatts()));
        out.add("fov_deg", num(cam.fovDegrees()));
        out.add("image_quality", num(cam.imageQuality()));
        out.add("low_light_noise", num(cam.lowLightNoise()));
        out.add("sensor_in", num(cam.sensorInches()));
        return out;
    }

    public static JsonObject chemistry(CellChemistry chemistry) {
        JsonObject out = new JsonObject();
        out.add("full_v", num(chemistry.fullCellVoltage()));
        out.add("nominal_v", num(chemistry.nominalCellVoltage()));
        out.add("empty_v", num(chemistry.emptyCellVoltage()));
        out.add("cutoff_v", num(chemistry.cutoffCellVoltage()));
        out.add("storage_v", num(chemistry.storageCellVoltage()));
        out.add("density_g_cm3", num(chemistry.densityGramsPerCm3()));
        JsonArray shape = new JsonArray();
        chemistry.ocvShape().forEach(value -> shape.add(num(value)));
        out.add("ocv_shape", shape);
        out.add("usable_fraction", num(chemistry.effectiveUsableFraction()));
        out.add("landing_v", num(chemistry.landingCellVoltage()));
        JsonArray grid = new JsonArray();
        CellChemistry.SOC_GRID.forEach(value -> grid.add(num(value)));
        out.add("soc_grid", grid);
        return out;
    }
}
