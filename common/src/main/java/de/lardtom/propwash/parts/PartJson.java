package de.lardtom.propwash.parts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.ActionCamDefinition;
import de.lardtom.propwash.api.part.AirframeGeometry;
import de.lardtom.propwash.api.part.AnchorSpec;
import de.lardtom.propwash.api.part.BatteryDefinition;
import de.lardtom.propwash.api.part.BatteryFitRule;
import de.lardtom.propwash.api.part.CellChemistry;
import de.lardtom.propwash.api.part.DuctSpec;
import de.lardtom.propwash.api.part.FrameAero;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.api.part.FrameShape;
import de.lardtom.propwash.api.part.MassSplit;
import de.lardtom.propwash.api.part.MotorArm;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.MotorThermal;
import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.api.part.PackSize;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.PartVisual;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.PropMaterial;
import de.lardtom.propwash.api.part.RecycleYield;
import de.lardtom.propwash.api.part.StackDefinition;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.api.part.VideoDefinition;
import de.lardtom.propwash.api.part.VideoLink;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Rarity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class PartJson {
    public static final int FORMAT = 1;
    public static final String CHEMISTRY = "chemistry";
    public static final List<String> ARM_KEYS = List.of("front_left", "front_right", "rear_left", "rear_right");

    private static final List<String> ROLES = JsonFields.keysOf(FrameRole.values(), FrameRole::key);
    private static final List<String> SHAPES = JsonFields.keysOf(FrameShape.values(), FrameShape::key);
    private static final List<String> SLOTS = JsonFields.keysOf(AccessorySlot.values(), AccessorySlot::key);
    private static final List<String> MATERIALS = JsonFields.keysOf(PropMaterial.values(), PropMaterial::key);
    private static final List<String> LINKS = JsonFields.keysOf(VideoLink.values(), VideoLink::key);
    private static final List<String> SPINS = JsonFields.keysOf(MotorArm.Spin.values(), MotorArm.Spin::key);
    private static final List<String> RARITIES = JsonFields.keysOf(Rarity.values(), Rarity::getSerializedName);

    private PartJson() {
    }

    public static @Nullable JsonObject parse(String text, PartDataProblems problems) {
        try {
            JsonElement element = JsonParser.parseString(text);
            if (!element.isJsonObject()) {
                problems.error("", "JSON-Objekt erwartet");
                return null;
            }
            return element.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            problems.error("", "ungültiges JSON: " + rootMessage(e));
            return null;
        }
    }

    public static @Nullable PartDefinition decode(PartKind kind, Identifier id, JsonObject json, PartDataProblems problems) {
        JsonFields fields = new JsonFields(json, "", problems);
        format(fields);
        Basics basics = basics(fields);
        PartDefinition result = null;
        try {
            result = switch (kind) {
                case FRAME -> frame(id, basics, fields);
                case STACK -> stack(id, basics, fields);
                case MOTOR -> motor(id, basics, fields);
                case PROP -> prop(id, basics, fields);
                case VIDEO -> video(id, basics, fields);
                case BATTERY -> battery(id, basics, fields);
                case ACCESSORY -> accessory(id, basics, fields);
            };
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException e) {
            if (!problems.hasErrors()) {
                problems.error("", rootMessage(e));
            }
            result = null;
        }
        fields.finish();
        return problems.hasErrors() ? null : result;
    }

    public static @Nullable CellChemistry decodeChemistry(Identifier id, JsonObject json, PartDataProblems problems) {
        JsonFields fields = new JsonFields(json, "", problems);
        format(fields);
        double full = fields.requireDouble("full_v");
        double nominal = fields.requireDouble("nominal_v");
        double empty = fields.requireDouble("empty_v");
        double cutoff = fields.requireDouble("cutoff_v");
        double storage = fields.requireDouble("storage_v");
        double density = fields.requireDouble("density_g_cm3");
        double[] shape = fields.requireVector("ocv_shape", CellChemistry.SOC_GRID.size());
        OptionalDouble usable = fields.optDouble("usable_fraction");
        fields.finish();
        if (problems.hasErrors()) {
            return null;
        }
        try {
            List<Double> curve = new ArrayList<>(shape.length);
            for (double value : shape) {
                curve.add(value);
            }
            return new CellChemistry(id, full, nominal, empty, cutoff, storage, density, curve, usable);
        } catch (IllegalArgumentException e) {
            problems.error("", rootMessage(e));
            return null;
        }
    }

    public static JsonObject encode(PartDefinition definition) {
        JsonObject json = new JsonObject();
        json.addProperty("format", FORMAT);
        json.addProperty("mass_g", definition.basics().massGrams());
        switch (definition) {
            case FrameDefinition frame -> encodeFrame(frame, json);
            case StackDefinition stack -> encodeStack(stack, json);
            case MotorDefinition motor -> encodeMotor(motor, json);
            case PropDefinition prop -> encodeProp(prop, json);
            case VideoDefinition video -> encodeVideo(video, json);
            case BatteryDefinition battery -> encodeBattery(battery, json);
            case AccessoryDefinition accessory -> encodeAccessory(accessory, json);
        }
        double defaultDurability = definition instanceof PropDefinition prop ? prop.material().defaultDurability() : 1.0;
        encodeBasics(definition.basics(), defaultDurability, json);
        return json;
    }

    public static JsonObject encode(CellChemistry chemistry) {
        JsonObject json = new JsonObject();
        json.addProperty("format", FORMAT);
        json.addProperty("full_v", chemistry.fullCellVoltage());
        json.addProperty("nominal_v", chemistry.nominalCellVoltage());
        json.addProperty("empty_v", chemistry.emptyCellVoltage());
        json.addProperty("cutoff_v", chemistry.cutoffCellVoltage());
        json.addProperty("storage_v", chemistry.storageCellVoltage());
        json.addProperty("density_g_cm3", chemistry.densityGramsPerCm3());
        JsonArray shape = new JsonArray();
        for (double value : chemistry.ocvShape()) {
            shape.add(value);
        }
        json.add("ocv_shape", shape);
        chemistry.usableFraction().ifPresent(v -> json.addProperty("usable_fraction", v));
        return json;
    }

    public static String folder(PartKind kind) {
        return kind.key();
    }

    private static void format(JsonFields fields) {
        OptionalInt format = fields.optInt("format");
        if (format.isPresent() && (format.getAsInt() < 1 || format.getAsInt() > FORMAT)) {
            fields.error("format", "Format " + format.getAsInt() + " wird nicht unterstützt (diese Propwash-Version liest bis Format " + FORMAT
                    + "; Propwash aktualisieren)");
        }
    }

    private record Basics(double mass, int stackSize, int sortOrder, boolean creative, OptionalDouble durability,
                          Optional<RecycleYield> recycle, PartVisual visual, Optional<Rarity> rarity) {
        PartBasics resolve(double defaultDurability) {
            return new PartBasics(this.mass, this.stackSize, this.sortOrder, this.creative, this.durability.orElse(defaultDurability),
                    this.recycle, this.visual, this.rarity);
        }
    }

    private static Basics basics(JsonFields fields) {
        double mass = fields.requireDouble("mass_g");
        int stackSize = fields.intOr("stack_size", 0);
        int sortOrder = fields.intOr("sort", PartBasics.DEFAULT_SORT_ORDER);
        boolean creative = fields.boolOr("creative", true);
        OptionalDouble durability = fields.optDouble("durability");
        Optional<RecycleYield> recycle = recycle(fields);
        PartVisual visual = fields.optObject("visual").map(PartJson::visual).orElse(PartVisual.NONE);
        Optional<Rarity> rarity = fields.optEnum("rarity", PartJson::rarity, RARITIES);
        return new Basics(mass, stackSize, sortOrder, creative, durability, recycle, visual, rarity);
    }

    public static Optional<Rarity> rarity(String key) {
        for (Rarity rarity : Rarity.values()) {
            if (rarity.getSerializedName().equals(key)) {
                return Optional.of(rarity);
            }
        }
        return Optional.empty();
    }

    private static Optional<RecycleYield> recycle(JsonFields fields) {
        Optional<JsonElement> raw = fields.raw("recycle");
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        JsonElement element = raw.get();
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() && "none".equals(element.getAsString())) {
            return Optional.of(RecycleYield.NONE);
        }
        if (!element.isJsonObject()) {
            fields.error("recycle", "\"none\" oder {\"item\": …, \"count\": …} erwartet, gefunden: " + element);
            return Optional.empty();
        }
        JsonFields yield = new JsonFields(element.getAsJsonObject(), fields.at("recycle"), fields.problems());
        Identifier item = yield.requireId("item");
        int count = yield.intOr("count", 1);
        yield.finish();
        if (item == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new RecycleYield(item, count));
        } catch (IllegalArgumentException e) {
            fields.error("recycle", rootMessage(e));
            return Optional.empty();
        }
    }

    private static PartVisual visual(JsonFields fields) {
        OptionalInt accent = OptionalInt.empty();
        Optional<String> text = fields.optString("accent");
        if (text.isPresent()) {
            OptionalInt parsed = parseColor(text.get());
            if (parsed.isEmpty()) {
                fields.error("accent", "Farbe als \"#rrggbb\" erwartet, gefunden: \"" + text.get() + "\"");
            }
            accent = parsed;
        }
        Optional<Identifier> model = fields.optId("render_model");
        fields.finish();
        return new PartVisual(accent, model);
    }

    private static void encodeBasics(PartBasics basics, double defaultDurability, JsonObject json) {
        if (basics.stackSize() != 0) {
            json.addProperty("stack_size", basics.stackSize());
        }
        if (basics.sortOrder() != PartBasics.DEFAULT_SORT_ORDER) {
            json.addProperty("sort", basics.sortOrder());
        }
        if (!basics.creativeListed()) {
            json.addProperty("creative", false);
        }
        if (basics.durability() != defaultDurability) {
            json.addProperty("durability", basics.durability());
        }
        basics.recycle().ifPresent(yield -> {
            if (yield.equals(RecycleYield.NONE)) {
                json.addProperty("recycle", "none");
            } else {
                JsonObject object = new JsonObject();
                object.addProperty("item", yield.item().toString());
                object.addProperty("count", yield.count());
                json.add("recycle", object);
            }
        });
        basics.rarity().ifPresent(rarity -> json.addProperty("rarity", rarity.getSerializedName()));
        PartVisual visual = basics.visual();
        if (!visual.equals(PartVisual.NONE)) {
            JsonObject object = new JsonObject();
            visual.accentRgb().ifPresent(rgb -> object.addProperty("accent", String.format(Locale.ROOT, "#%06x", rgb)));
            visual.renderModel().ifPresent(model -> object.addProperty("render_model", model.toString()));
            json.add("visual", object);
        }
    }

    private static FrameDefinition frame(Identifier id, Basics basics, JsonFields f) {
        FrameDefinition.Builder b = FrameDefinition.builder(id);
        f.optEnum("role", FrameRole::byKey, ROLES).ifPresent(b::role);
        f.optEnum("shape", FrameShape::byKey, SHAPES).ifPresent(b::shape);
        int wheelbase = f.requireInt("wheelbase_mm");
        b.wheelbase(wheelbase);
        boolean range = f.has("props_mm");
        boolean max = f.has("max_prop_mm");
        if (range && max) {
            f.error("props_mm", "entweder props_mm oder max_prop_mm angeben, nicht beides");
        }
        if (range) {
            f.optRange("props_mm").ifPresent(p -> b.props(p[0], p[1]));
        } else if (max) {
            b.maxProp(f.requireInt("max_prop_mm"));
        } else {
            f.error("max_prop_mm", "Pflichtfeld fehlt (oder props_mm: [min, max])");
        }
        b.motorMount(f.requireIdList("motor_mounts").toArray(new Identifier[0]));
        b.stackMount(f.requireIdList("stack_mounts").toArray(new Identifier[0]));
        b.accessoryMount(f.enumList("accessory_mounts", AccessorySlot::byKey, SLOTS).toArray(new AccessorySlot[0]));
        f.optObject("arms").ifPresent(arms -> b.arms(arms(arms)));
        f.optObject("battery").ifPresent(battery -> {
            boolean onTop = battery.optBool("on_top").orElse(AirframeGeometry.defaultBatteryOnTop(wheelbase));
            double plate = battery.optDouble("plate_mm").orElse(onTop ? 0.13 * wheelbase : -0.05 * wheelbase);
            battery.finish();
            b.battery(onTop, plate);
        });
        f.optDouble("electronics_mm").ifPresent(b::electronics);
        f.optObject("camera").ifPresent(camera -> {
            camera.optVector("pos", 3).ifPresent(p -> b.camera(new Vec3Mm(p[0], p[1], p[2])));
            camera.optDouble("tilt_deg").ifPresent(b::cameraTilt);
            camera.finish();
        });
        f.optVector("center_of_mass_mm", 3).ifPresent(p -> b.centerOfMass(new Vec3Mm(p[0], p[1], p[2])));
        f.optObject("mass_split").ifPresent(split -> {
            double arms = split.requireDouble("arms");
            double plate = split.requireDouble("plate");
            double duct = split.doubleOr("duct", 0.0);
            split.finish();
            if (!f.problems().hasErrors()) {
                b.massSplit(new MassSplit(arms, plate, duct));
            }
        });
        f.optObject("accessory_anchors").ifPresent(anchors -> {
            for (AccessorySlot slot : AccessorySlot.values()) {
                anchors.optObject(slot.key()).ifPresent(anchor -> {
                    double[] pos = anchor.requireVector("pos", 3);
                    double tilt = anchor.doubleOr("tilt_deg", 0.0);
                    double length = anchor.doubleOr("length_mm", 0.0);
                    anchor.finish();
                    if (pos != null) {
                        b.anchor(slot, new AnchorSpec(pos[0], pos[1], pos[2], tilt, length));
                    }
                });
            }
            anchors.finish();
        });
        JsonFields aero = f.requireObject("aero");
        if (aero != null) {
            double frontal = aero.requireDouble("frontal_area_m2");
            double drag = aero.requireDouble("drag_coefficient");
            aero.optDouble("side_area_m2").ifPresent(b::sideArea);
            aero.optDouble("top_area_m2").ifPresent(b::topArea);
            aero.finish();
            b.aero(frontal, drag);
        }
        f.raw("duct").ifPresent(element -> duct(element, f).ifPresent(b::duct));
        f.optDouble("impact_tolerance").ifPresent(b::impactTolerance);
        JsonFields fit = f.requireObject("battery_fit");
        if (fit != null) {
            int[] cells = fit.requireRange("cells");
            double maxMass = fit.requireDouble("max_mass_g");
            int preferredCells = fit.intOr("preferred_min_cells", cells == null ? 1 : cells[0]);
            int preferredCapacity = fit.intOr("preferred_min_capacity_mah", 0);
            fit.finish();
            if (cells != null && !f.problems().hasErrors()) {
                try {
                    b.batteryFit(new BatteryFitRule(cells[0], cells[1], maxMass, preferredCells, preferredCapacity));
                } catch (IllegalArgumentException e) {
                    f.error("battery_fit", rootMessage(e));
                }
            }
        }
        if (f.problems().hasErrors()) {
            return null;
        }
        return b.basics(basics.resolve(1.0)).build();
    }

    private static List<MotorArm> arms(JsonFields arms) {
        List<MotorArm> out = new ArrayList<>();
        for (int i = 0; i < ARM_KEYS.size(); i++) {
            String key = ARM_KEYS.get(i);
            JsonFields arm = arms.requireObject(key);
            if (arm == null) {
                continue;
            }
            double[] pos = arm.requireVector("pos", 3);
            MotorArm.Spin spin = arm.optEnum("spin", PartJson::spin, SPINS).orElse(MotorArm.PROPS_IN.get(i));
            arm.finish();
            if (pos != null) {
                out.add(MotorArm.of(pos[0], pos[1], pos[2], spin));
            }
        }
        arms.finish();
        return out;
    }

    private static Optional<MotorArm.Spin> spin(String key) {
        for (MotorArm.Spin spin : MotorArm.Spin.values()) {
            if (spin.key().equals(key)) {
                return Optional.of(spin);
            }
        }
        return Optional.empty();
    }

    private static Optional<DuctSpec> duct(JsonElement element, JsonFields parent) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean() ? Optional.of(DuctSpec.STANDARD) : Optional.empty();
        }
        if (!element.isJsonObject()) {
            parent.error("duct", "Objekt erwartet, gefunden: " + element);
            return Optional.empty();
        }
        JsonFields f = new JsonFields(element.getAsJsonObject(), parent.at("duct"), parent.problems());
        OptionalDouble thrust = f.optDouble("thrust_gain");
        OptionalDouble clearance = f.optDouble("clearance_mm");
        OptionalDouble lift = f.optDouble("translational_lift_gain");
        OptionalDouble loss = f.optDouble("wash_loss");
        OptionalDouble noise = f.optDouble("wash_noise");
        OptionalDouble restitution = f.optDouble("restitution");
        OptionalDouble friction = f.optDouble("friction");
        f.finish();
        try {
            return Optional.of(new DuctSpec(thrust, clearance, lift, loss, noise, restitution, friction));
        } catch (IllegalArgumentException e) {
            parent.error("duct", rootMessage(e));
            return Optional.empty();
        }
    }

    private static void encodeFrame(FrameDefinition frame, JsonObject json) {
        json.addProperty("role", frame.role().key());
        json.addProperty("shape", frame.shape().key());
        json.addProperty("wheelbase_mm", frame.wheelbaseMm());
        if (frame.minPropMm() == 0) {
            json.addProperty("max_prop_mm", frame.maxPropMm());
        } else {
            json.add("props_mm", ints(frame.minPropMm(), frame.maxPropMm()));
        }
        json.add("motor_mounts", ids(frame.motorMounts()));
        json.add("stack_mounts", ids(frame.stackMounts()));
        if (!frame.accessoryMounts().isEmpty()) {
            JsonArray slots = new JsonArray();
            for (AccessorySlot slot : frame.accessoryMounts()) {
                slots.add(slot.key());
            }
            json.add("accessory_mounts", slots);
        }
        AirframeGeometry geometry = frame.geometry();
        if (geometry.hasExplicitArms()) {
            JsonObject arms = new JsonObject();
            for (int i = 0; i < ARM_KEYS.size(); i++) {
                MotorArm arm = geometry.arms().get(i);
                JsonObject entry = new JsonObject();
                entry.add("pos", vector(arm.position()));
                entry.addProperty("spin", arm.spin().key());
                arms.add(ARM_KEYS.get(i), entry);
            }
            json.add("arms", arms);
        }
        JsonObject battery = new JsonObject();
        battery.addProperty("on_top", geometry.batteryOnTop());
        battery.addProperty("plate_mm", geometry.batteryPlateMm());
        json.add("battery", battery);
        json.addProperty("electronics_mm", geometry.electronicsMm());
        JsonObject camera = new JsonObject();
        geometry.cameraMm().ifPresent(pos -> camera.add("pos", vector(pos)));
        camera.addProperty("tilt_deg", geometry.cameraTiltDeg());
        json.add("camera", camera);
        if (!geometry.frameCenterOfMassMm().equals(Vec3Mm.ZERO)) {
            json.add("center_of_mass_mm", vector(geometry.frameCenterOfMassMm()));
        }
        MassSplit defaultSplit = frame.ducted() ? MassSplit.DUCTED : MassSplit.OPEN;
        if (!geometry.massSplit().equals(defaultSplit)) {
            JsonObject split = new JsonObject();
            split.addProperty("arms", geometry.massSplit().arms());
            split.addProperty("plate", geometry.massSplit().plate());
            split.addProperty("duct", geometry.massSplit().duct());
            json.add("mass_split", split);
        }
        if (!geometry.accessoryAnchors().isEmpty()) {
            JsonObject anchors = new JsonObject();
            for (Map.Entry<AccessorySlot, AnchorSpec> entry : geometry.accessoryAnchors().entrySet()) {
                AnchorSpec anchor = entry.getValue();
                JsonObject object = new JsonObject();
                object.add("pos", doubles(anchor.x(), anchor.y(), anchor.z()));
                object.addProperty("tilt_deg", anchor.tiltDeg());
                object.addProperty("length_mm", anchor.lengthMm());
                anchors.add(entry.getKey().key(), object);
            }
            json.add("accessory_anchors", anchors);
        }
        FrameAero aero = frame.aero();
        JsonObject aeroJson = new JsonObject();
        aeroJson.addProperty("frontal_area_m2", aero.frontalAreaM2());
        aeroJson.addProperty("drag_coefficient", aero.dragCoefficient());
        aero.sideAreaM2().ifPresent(v -> aeroJson.addProperty("side_area_m2", v));
        aero.topAreaM2().ifPresent(v -> aeroJson.addProperty("top_area_m2", v));
        json.add("aero", aeroJson);
        frame.duct().ifPresent(duct -> {
            JsonObject object = new JsonObject();
            duct.thrustGain().ifPresent(v -> object.addProperty("thrust_gain", v));
            duct.clearanceMm().ifPresent(v -> object.addProperty("clearance_mm", v));
            duct.translationalLiftGain().ifPresent(v -> object.addProperty("translational_lift_gain", v));
            duct.washLoss().ifPresent(v -> object.addProperty("wash_loss", v));
            duct.washNoise().ifPresent(v -> object.addProperty("wash_noise", v));
            duct.restitution().ifPresent(v -> object.addProperty("restitution", v));
            duct.friction().ifPresent(v -> object.addProperty("friction", v));
            json.add("duct", object);
        });
        json.addProperty("impact_tolerance", frame.impactTolerance());
        BatteryFitRule rule = frame.batteryFit();
        JsonObject fit = new JsonObject();
        fit.add("cells", ints(rule.minCells(), rule.maxCells()));
        fit.addProperty("max_mass_g", rule.maxMassGrams());
        fit.addProperty("preferred_min_cells", rule.preferredMinCells());
        fit.addProperty("preferred_min_capacity_mah", rule.preferredMinCapacityMah());
        json.add("battery_fit", fit);
    }

    private static StackDefinition stack(Identifier id, Basics basics, JsonFields f) {
        StackDefinition.Builder b = StackDefinition.builder(id);
        Identifier mount = f.requireId("mount");
        double continuous = f.requireDouble("esc_continuous_a");
        int[] cells = f.requireRange("cells");
        b.allInOne(f.boolOr("all_in_one", false));
        f.optDouble("esc_burst_a").ifPresent(b::escBurst);
        f.optDouble("esc_resistance_ohm").ifPresent(b::escResistance);
        f.optDouble("brownout_v").ifPresent(b::brownout);
        f.optDouble("board_size_mm").ifPresent(b::boardSize);
        if (f.problems().hasErrors()) {
            return null;
        }
        return b.basics(basics.resolve(1.0)).mount(mount).escContinuous(continuous).cells(cells[0], cells[1]).build();
    }

    private static void encodeStack(StackDefinition stack, JsonObject json) {
        json.addProperty("mount", stack.mount().toString());
        json.addProperty("esc_continuous_a", stack.escContinuousAmps());
        json.add("cells", ints(stack.minCells(), stack.maxCells()));
        json.addProperty("all_in_one", stack.allInOne());
        stack.escBurstAmps().ifPresent(v -> json.addProperty("esc_burst_a", v));
        stack.escResistanceOhm().ifPresent(v -> json.addProperty("esc_resistance_ohm", v));
        stack.brownoutVoltage().ifPresent(v -> json.addProperty("brownout_v", v));
        stack.boardSizeMm().ifPresent(v -> json.addProperty("board_size_mm", v));
    }

    private static MotorDefinition motor(Identifier id, Basics basics, JsonFields f) {
        MotorDefinition.Builder b = MotorDefinition.builder(id);
        String stator = f.requireString("stator");
        f.optVector("stator_mm", 2).ifPresent(size -> b.statorSize(size[0], size[1]));
        int kv = f.requireInt("kv");
        Identifier mount = f.requireId("mount");
        double current = f.requireDouble("max_current_a");
        double resistance = f.requireDouble("resistance_ohm");
        int[] cells = f.requireRange("cells");
        f.optId("prop_mount").ifPresent(b::propMount);
        f.optObject("thermal").ifPresent(thermal -> {
            OptionalDouble capacity = thermal.optDouble("heat_capacity_j_per_k");
            OptionalDouble cooling = thermal.optDouble("cooling_w_per_k");
            OptionalDouble coolingSpeed = thermal.optDouble("cooling_w_per_k_per_rad_s");
            double derate = thermal.doubleOr("derate_start_c", MotorThermal.DEFAULT.derateStartC());
            double maxTemp = thermal.doubleOr("max_temp_c", MotorThermal.DEFAULT.maxTempC());
            double wear = thermal.doubleOr("wear_per_s_at_max", MotorThermal.DEFAULT.wearPerSecondAtMax());
            thermal.finish();
            try {
                b.thermal(new MotorThermal(capacity, cooling, coolingSpeed, derate, maxTemp, wear));
            } catch (IllegalArgumentException e) {
                f.error("thermal", rootMessage(e));
            }
        });
        if (f.problems().hasErrors()) {
            return null;
        }
        return b.basics(basics.resolve(1.0)).stator(stator).kv(kv).mount(mount).maxCurrent(current).resistance(resistance).cells(cells[0], cells[1]).build();
    }

    private static void encodeMotor(MotorDefinition motor, JsonObject json) {
        json.addProperty("stator", motor.statorLabel());
        double[] parsed = MotorDefinition.parseStatorLabel(motor.statorLabel());
        if (parsed[0] != motor.statorDiameterMm() || parsed[1] != motor.statorHeightMm()) {
            json.add("stator_mm", doubles(motor.statorDiameterMm(), motor.statorHeightMm()));
        }
        json.addProperty("kv", motor.kv());
        json.addProperty("mount", motor.mount().toString());
        json.addProperty("max_current_a", motor.maxCurrentAmps());
        json.addProperty("resistance_ohm", motor.resistanceOhm());
        json.add("cells", ints(motor.minCells(), motor.maxCells()));
        if (!motor.propMount().equals(Mounts.PROP_UNIVERSAL)) {
            json.addProperty("prop_mount", motor.propMount().toString());
        }
        MotorThermal thermal = motor.thermal();
        if (!thermal.equals(MotorThermal.DEFAULT)) {
            JsonObject object = new JsonObject();
            thermal.heatCapacityJPerK().ifPresent(v -> object.addProperty("heat_capacity_j_per_k", v));
            thermal.coolingWPerK().ifPresent(v -> object.addProperty("cooling_w_per_k", v));
            thermal.coolingWPerKPerRadS().ifPresent(v -> object.addProperty("cooling_w_per_k_per_rad_s", v));
            object.addProperty("derate_start_c", thermal.derateStartC());
            object.addProperty("max_temp_c", thermal.maxTempC());
            object.addProperty("wear_per_s_at_max", thermal.wearPerSecondAtMax());
            json.add("thermal", object);
        }
    }

    private static PropDefinition prop(Identifier id, Basics basics, JsonFields f) {
        int diameter = f.requireInt("diameter_mm");
        double pitch = f.requireDouble("pitch_in");
        int blades = f.requireInt("blades");
        double ct = f.requireDouble("ct");
        double cp = f.requireDouble("cp");
        PropMaterial material = f.optEnum("material", PropMaterial::byKey, MATERIALS).orElse(PropMaterial.POLYCARBONATE);
        OptionalDouble stiffness = f.optDouble("stiffness");
        Optional<Identifier> mount = f.optId("mount");
        OptionalDouble reverse = f.optDouble("reverse_thrust");
        if (f.problems().hasErrors()) {
            return null;
        }
        PropDefinition.Builder b = PropDefinition.builder(id).basics(basics.resolve(material.defaultDurability()))
                .size(diameter, pitch, blades).coefficients(ct, cp).material(material);
        stiffness.ifPresent(b::stiffness);
        mount.ifPresent(b::mount);
        reverse.ifPresent(b::reverseThrust);
        return b.build();
    }

    private static void encodeProp(PropDefinition prop, JsonObject json) {
        json.addProperty("diameter_mm", prop.diameterMm());
        json.addProperty("pitch_in", prop.pitchInch());
        json.addProperty("blades", prop.blades());
        json.addProperty("ct", prop.thrustCoefficient());
        json.addProperty("cp", prop.powerCoefficient());
        json.addProperty("material", prop.material().key());
        if (prop.stiffness() != prop.material().defaultStiffness()) {
            json.addProperty("stiffness", prop.stiffness());
        }
        if (!prop.mount().equals(Mounts.PROP_UNIVERSAL)) {
            json.addProperty("mount", prop.mount().toString());
        }
        prop.reverseThrust().ifPresent(v -> json.addProperty("reverse_thrust", v));
    }

    private static BatteryDefinition battery(Identifier id, Basics basics, JsonFields f) {
        int cells = f.requireInt("cells");
        int capacity = f.requireInt("capacity_mah");
        double resistance = f.requireDouble("cell_resistance_ohm");
        int cRating = f.requireInt("c_rating");
        Identifier chemistry = f.optId("chemistry").orElse(BatteryDefinition.LIPO);
        Optional<double[]> pack = f.optVector("pack_mm", 3);
        String connector = f.optString("connector").orElse("");
        if (f.problems().hasErrors()) {
            return null;
        }
        BatteryDefinition.Builder b = BatteryDefinition.builder(id).basics(basics.resolve(1.0)).cells(cells).capacity(capacity)
                .cellResistance(resistance).cRating(cRating).chemistry(chemistry).connector(connector);
        pack.ifPresent(size -> b.packSize(new PackSize(size[0], size[1], size[2])));
        return b.build();
    }

    private static void encodeBattery(BatteryDefinition battery, JsonObject json) {
        json.addProperty("cells", battery.cells());
        json.addProperty("capacity_mah", battery.capacityMah());
        json.addProperty("cell_resistance_ohm", battery.cellResistanceOhm());
        json.addProperty("c_rating", battery.cRating());
        json.addProperty("chemistry", battery.chemistry().toString());
        battery.packSize().ifPresent(size -> json.add("pack_mm", doubles(size.lengthMm(), size.widthMm(), size.heightMm())));
        if (!battery.connector().isEmpty()) {
            json.addProperty("connector", battery.connector());
        }
    }

    private static VideoDefinition video(Identifier id, Basics basics, JsonFields f) {
        VideoLink link = f.requireEnum("link", VideoLink::byKey, LINKS);
        int latency = f.requireInt("latency_ms");
        int range = f.requireInt("range_blocks");
        double quality = f.requireDouble("image_quality");
        OptionalDouble width = f.optDouble("camera_width_mm");
        OptionalDouble fov = f.optDouble("camera_fov_deg");
        int power = f.intOr("vtx_power_mw", 0);
        OptionalDouble watts = f.optDouble("power_w");
        int[] levels = f.optIntList("vtx_levels_mw").orElse(new int[0]);
        if (f.problems().hasErrors()) {
            return null;
        }
        VideoDefinition.Builder b = VideoDefinition.builder(id).basics(basics.resolve(1.0)).link(link).latency(latency).range(range)
                .imageQuality(quality).vtxPower(power).vtxLevels(levels);
        width.ifPresent(b::cameraWidth);
        fov.ifPresent(b::cameraFov);
        watts.ifPresent(b::powerWatts);
        return b.build();
    }

    private static void encodeVideo(VideoDefinition video, JsonObject json) {
        json.addProperty("link", video.link().key());
        json.addProperty("latency_ms", video.latencyMs());
        json.addProperty("range_blocks", video.rangeBlocks());
        json.addProperty("image_quality", video.imageQuality());
        json.addProperty("camera_width_mm", video.cameraWidthMm());
        video.cameraFovDeg().ifPresent(v -> json.addProperty("camera_fov_deg", v));
        if (video.vtxPowerMw() != 0) {
            json.addProperty("vtx_power_mw", video.vtxPowerMw());
        }
        video.powerW().ifPresent(v -> json.addProperty("power_w", v));
        if (!video.vtxLevelsMw().isEmpty()) {
            JsonArray levels = new JsonArray();
            video.vtxLevelsMw().forEach(levels::add);
            json.add("vtx_levels_mw", levels);
        }
    }

    private static AccessoryDefinition accessory(Identifier id, Basics basics, JsonFields f) {
        AccessorySlot slot = f.requireEnum("slot", AccessorySlot::byKey, SLOTS);
        double[] size = f.requireVector("size_mm", 3);
        double watts = f.doubleOr("flight_battery_w", 0.0);
        double frontal = f.doubleOr("frontal_area_m2", 0.0);
        int leds = f.intOr("leds", 0);
        double[] lens = {0.0, 0.0};
        f.optObject("lens").ifPresent(object -> {
            lens[0] = object.doubleOr("x_mm", 0.0);
            lens[1] = object.doubleOr("length_mm", 0.0);
            object.finish();
        });
        Optional<ActionCamDefinition> camera = f.optObject("action_cam").flatMap(PartJson::actionCam);
        if (f.problems().hasErrors()) {
            return null;
        }
        AccessoryDefinition.Builder b = AccessoryDefinition.builder(id).basics(basics.resolve(1.0)).slot(slot).size(size[0], size[1], size[2])
                .flightBatteryWatts(watts).frontalArea(frontal).leds(leds).lens(lens[0], lens[1]);
        camera.ifPresent(b::actionCam);
        return b.build();
    }

    private static Optional<ActionCamDefinition> actionCam(JsonFields f) {
        int height = f.requireInt("max_height");
        int fps = f.requireInt("max_fps");
        boolean stabilization = f.boolOr("stabilization", false);
        boolean levelling = f.boolOr("horizon_levelling", false);
        double ownBattery = f.doubleOr("own_battery_wh", 0.0);
        double record = f.requireDouble("record_w");
        double maxRecord = f.optDouble("max_record_w").orElse(record);
        double fov = f.requireDouble("fov_deg");
        double quality = f.requireDouble("image_quality");
        double noise = f.requireDouble("low_light_noise");
        double sensor = sensorInches(f);
        f.finish();
        if (f.problems().hasErrors()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ActionCamDefinition(height, fps, stabilization, levelling, ownBattery, record, maxRecord, fov, quality, noise,
                    sensor));
        } catch (IllegalArgumentException e) {
            f.error("", rootMessage(e));
            return Optional.empty();
        }
    }

    private static double sensorInches(JsonFields f) {
        Optional<JsonElement> raw = f.raw("sensor_in");
        if (raw.isEmpty()) {
            f.error("sensor_in", "Pflichtfeld fehlt");
            return Double.NaN;
        }
        JsonElement element = raw.get();
        OptionalDouble number = JsonFields.number(element);
        if (number.isPresent()) {
            return number.getAsDouble();
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String text = element.getAsString().strip();
            if (text.startsWith("1/")) {
                try {
                    double divisor = Double.parseDouble(text.substring(2));
                    if (divisor > 0.0 && Double.isFinite(divisor)) {
                        return 1.0 / divisor;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        f.error("sensor_in", "Zahl oder Bruch wie \"1/2.8\" erwartet, gefunden: " + element);
        return Double.NaN;
    }

    private static void encodeAccessory(AccessoryDefinition accessory, JsonObject json) {
        json.addProperty("slot", accessory.slot().key());
        json.add("size_mm", doubles(accessory.widthMm(), accessory.heightMm(), accessory.depthMm()));
        json.addProperty("flight_battery_w", accessory.flightBatteryWatts());
        json.addProperty("frontal_area_m2", accessory.frontalAreaM2());
        if (accessory.ledCount() != 0) {
            json.addProperty("leds", accessory.ledCount());
        }
        if (accessory.lensXMm() != 0.0 || accessory.lensLengthMm() != 0.0) {
            JsonObject lens = new JsonObject();
            lens.addProperty("x_mm", accessory.lensXMm());
            lens.addProperty("length_mm", accessory.lensLengthMm());
            json.add("lens", lens);
        }
        accessory.actionCam().ifPresent(camera -> {
            JsonObject object = new JsonObject();
            object.addProperty("max_height", camera.maxHeight());
            object.addProperty("max_fps", camera.maxFps());
            object.addProperty("stabilization", camera.stabilization());
            object.addProperty("horizon_levelling", camera.horizonLevelling());
            object.addProperty("own_battery_wh", camera.ownBatteryWh());
            object.addProperty("record_w", camera.recordWatts());
            object.addProperty("max_record_w", camera.maxRecordWatts());
            object.addProperty("fov_deg", camera.fovDegrees());
            object.addProperty("image_quality", camera.imageQuality());
            object.addProperty("low_light_noise", camera.lowLightNoise());
            object.addProperty("sensor_in", camera.sensorInches());
            json.add("action_cam", object);
        });
    }

    static OptionalInt parseColor(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() != 6) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    private static JsonArray ids(Iterable<Identifier> ids) {
        JsonArray array = new JsonArray();
        for (Identifier id : ids) {
            array.add(id.toString());
        }
        return array;
    }

    private static JsonArray ints(int... values) {
        JsonArray array = new JsonArray();
        for (int value : values) {
            array.add(value);
        }
        return array;
    }

    private static JsonArray doubles(double... values) {
        JsonArray array = new JsonArray();
        for (double value : values) {
            array.add(new JsonPrimitive(value));
        }
        return array;
    }

    private static JsonArray vector(Vec3Mm vector) {
        return doubles(vector.x(), vector.y(), vector.z());
    }

    static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
