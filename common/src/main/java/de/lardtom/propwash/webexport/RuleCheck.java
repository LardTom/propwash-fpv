package de.lardtom.propwash.webexport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.BatteryDefinition;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.StackDefinition;
import de.lardtom.propwash.api.part.VideoDefinition;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public final class RuleCheck {
    public static final double ESC_MARGIN = 1.15;
    public static final double BATTERY_MARGIN = 1.3;
    public static final int MOTOR_COUNT = 4;
    public static final int RANDOM_BUILDS = 5000;
    public static final long SEED = 1L;
    public static final List<String> ANALYSIS_WARNINGS = List.of("cannot_hover", "underpowered", "sluggish_motors", "heavy_sag",
            "esc_current_limited", "battery_overload", "motor_thermal", "props_in_view", "esc_overvoltage");

    public record Rule(String key, String kind, String formula) {
    }

    public static final List<Rule> RULES = List.of(
            new Rule("missing_part", "problem", "eine Id ist nicht in parts.json"),
            new Rule("motor_mount", "problem", "motor.mount ∉ frame.motor_mounts"),
            new Rule("prop_too_large", "problem", "prop.diameter_mm > frame.props_mm[1]"),
            new Rule("prop_too_small", "problem", "prop.diameter_mm < frame.props_mm[0]"),
            new Rule("prop_mount", "problem",
                    "nicht (motor.prop_mount = propwash:universal oder prop.mount = propwash:universal oder motor.prop_mount = prop.mount)"),
            new Rule("stack_mount", "problem", "stack.mount ∉ frame.stack_mounts"),
            new Rule("battery_frame", "problem", "battery.cells < fit.cells[0] oder battery.cells > fit.cells[1] oder battery.mass_g > fit.max_mass_g"),
            new Rule("accessory_mount", "problem", "ein Zubehör-Platz ∉ frame.accessory_mounts (und Zubehör nur im eigenen slot)"),
            new Rule("accessory_frame", "problem", "ein Zubehör hat frames und frame.id ∉ accessory.frames (ohne frames passt jeder Rahmen)"),
            new Rule("motor_overvoltage", "warning", "battery.cells > motor.cells[1]"),
            new Rule("motor_undervoltage", "warning", "battery.cells < motor.cells[0]"),
            new Rule("stack_voltage", "warning", "battery.cells ∉ [stack.cells[0], stack.cells[1]]"),
            new Rule("esc_undersized", "warning", "motor.max_current_a > stack.esc_continuous_a × 1,15"),
            new Rule("battery_weak", "warning", "4 × motor.max_current_a > battery.c_rating × battery.capacity_mah / 1000 × 1,3"),
            new Rule("battery_undersized", "warning",
                    "battery_frame nicht verletzt und (battery.cells < fit.preferred_min_cells oder battery.capacity_mah < fit.preferred_min_capacity_mah)"));

    public record Outcome(List<Identifier> unknown, List<String> problems, List<String> warnings) {
    }

    private RuleCheck() {
    }

    public static Outcome evaluate(BuildSpec spec) {
        List<Identifier> unknown = new ArrayList<>();
        Optional<FrameDefinition> frame = find(FrameDefinition.class, spec.frame(), unknown);
        Optional<StackDefinition> stack = find(StackDefinition.class, spec.stack(), unknown);
        Optional<MotorDefinition> motor = find(MotorDefinition.class, spec.motor(), unknown);
        Optional<PropDefinition> prop = find(PropDefinition.class, spec.prop(), unknown);
        find(VideoDefinition.class, spec.video(), unknown);
        Optional<BatteryDefinition> battery = find(BatteryDefinition.class, spec.battery(), unknown);
        EnumMap<AccessorySlot, AccessoryDefinition> accessories = new EnumMap<>(AccessorySlot.class);
        for (Map.Entry<AccessorySlot, Identifier> entry : spec.accessories().entrySet()) {
            find(AccessoryDefinition.class, entry.getValue(), unknown).ifPresent(a -> accessories.put(entry.getKey(), a));
        }
        if (!unknown.isEmpty()) {
            return new Outcome(List.copyOf(unknown), List.of(), List.of());
        }
        for (Map.Entry<AccessorySlot, AccessoryDefinition> entry : accessories.entrySet()) {
            if (entry.getValue().slot() != entry.getKey()) {
                return new Outcome(List.of(), List.of("accessory_mount"), List.of());
            }
        }
        FrameDefinition f = frame.orElseThrow();
        StackDefinition s = stack.orElseThrow();
        MotorDefinition m = motor.orElseThrow();
        PropDefinition p = prop.orElseThrow();
        BatteryDefinition b = battery.orElseThrow();
        List<String> problems = new ArrayList<>();
        if (!f.motorMounts().contains(m.mount())) {
            problems.add("motor_mount");
        }
        if (p.diameterMm() > f.maxPropMm()) {
            problems.add("prop_too_large");
        }
        if (p.diameterMm() < f.minPropMm()) {
            problems.add("prop_too_small");
        }
        if (!Mounts.propFits(m.propMount(), p.mount())) {
            problems.add("prop_mount");
        }
        if (!f.stackMounts().contains(s.mount())) {
            problems.add("stack_mount");
        }
        boolean allowed = b.cells() >= f.batteryFit().minCells() && b.cells() <= f.batteryFit().maxCells()
                && b.massGrams() <= f.batteryFit().maxMassGrams();
        if (!allowed) {
            problems.add("battery_frame");
        }
        for (AccessorySlot slot : accessories.keySet()) {
            if (!f.accessoryMounts().contains(slot)) {
                problems.add("accessory_mount");
                break;
            }
        }
        for (AccessoryDefinition accessory : accessories.values()) {
            if (!accessory.frames().isEmpty() && !accessory.frames().contains(f.id())) {
                problems.add("accessory_frame");
                break;
            }
        }
        List<String> warnings = new ArrayList<>();
        if (b.cells() > m.maxCells()) {
            warnings.add("motor_overvoltage");
        }
        if (b.cells() < m.minCells()) {
            warnings.add("motor_undervoltage");
        }
        if (b.cells() > s.maxCells() || b.cells() < s.minCells()) {
            warnings.add("stack_voltage");
        }
        if (m.maxCurrentAmps() > s.escContinuousAmps() * ESC_MARGIN) {
            warnings.add("esc_undersized");
        }
        if (m.maxCurrentAmps() * MOTOR_COUNT > b.maxContinuousCurrent() * BATTERY_MARGIN) {
            warnings.add("battery_weak");
        }
        if (allowed && (b.cells() < f.batteryFit().preferredMinCells() || b.capacityMah() < f.batteryFit().preferredMinCapacityMah())) {
            warnings.add("battery_undersized");
        }
        return new Outcome(List.of(), problems, warnings);
    }

    private static <D extends PartDefinition> Optional<D> find(Class<D> type, Identifier id, List<Identifier> unknown) {
        Optional<D> found = PropwashApi.parts().get(type, id);
        if (found.isEmpty()) {
            unknown.add(id);
        }
        return found;
    }

    public static int verify(Problems problems) {
        Random random = new Random(SEED);
        Map<PartKind, List<PartDefinition>> byKind = new EnumMap<>(PartKind.class);
        for (PartKind kind : PartKind.values()) {
            byKind.put(kind, PropwashApi.parts().all(kind).stream().filter(WebExport::exported).toList());
        }
        int mismatches = 0;
        for (int i = 0; i < RANDOM_BUILDS; i++) {
            BuildSpec spec = BuildSpec.of(pick(byKind, PartKind.FRAME, random), pick(byKind, PartKind.STACK, random),
                    pick(byKind, PartKind.MOTOR, random), pick(byKind, PartKind.PROP, random), pick(byKind, PartKind.VIDEO, random),
                    pick(byKind, PartKind.BATTERY, random));
            List<PartDefinition> accessories = byKind.get(PartKind.ACCESSORY);
            int count = accessories.isEmpty() ? 0 : random.nextInt(3);
            List<AccessorySlot> slots = new ArrayList<>(List.of(AccessorySlot.values()));
            if (random.nextBoolean()) {
                slots = List.of(slots.getLast(), slots.getFirst());
            }
            for (int a = 0; a < count && a < slots.size(); a++) {
                spec = spec.withAccessory(slots.get(a), accessories.get(random.nextInt(accessories.size())).id());
            }
            Outcome expected = evaluate(spec);
            BuildCheck actual = PropwashApi.builds().check(spec);
            if (!expected.problems().equals(actual.problems()) || !expected.warnings().equals(actual.warnings())
                    || !expected.unknown().equals(actual.unknownParts())) {
                mismatches++;
                if (mismatches <= 50) {
                    problems.error("rules.json", "Regel weicht ab bei " + spec + ": Formel " + expected + ", Propwash " + actual.problems()
                            + " / " + actual.warnings() + " / " + actual.unknownParts());
                }
            }
        }
        return mismatches;
    }

    private static Identifier pick(Map<PartKind, List<PartDefinition>> byKind, PartKind kind, Random random) {
        List<PartDefinition> list = byKind.get(kind);
        return list.get(random.nextInt(list.size())).id();
    }

    public static JsonObject toJson(LangTable lang, int mismatches) {
        JsonObject out = new JsonObject();
        JsonArray rules = new JsonArray();
        int order = 0;
        for (Rule rule : RULES) {
            JsonObject entry = new JsonObject();
            entry.addProperty("key", rule.key());
            entry.addProperty("kind", rule.kind());
            entry.add("text", lang.both(BuildCheck.translationKey(rule.key()), "rules.json#" + rule.key(), true));
            entry.addProperty("formula", rule.formula());
            entry.addProperty("order", order++);
            rules.add(entry);
        }
        out.add("rules", rules);
        JsonArray gates = new JsonArray();
        JsonObject unknown = new JsonObject();
        unknown.addProperty("when", "eine Id fehlt in parts.json oder gehört zu einer anderen Kategorie als ihr Feld");
        unknown.add("problems", ExportJson.strings(List.of("missing_part")));
        unknown.add("warnings", new JsonArray());
        unknown.addProperty("note", "Propwash meldet solche Ids in BuildCheck#unknownParts und prüft sonst nichts");
        gates.add(unknown);
        JsonObject wrongSlot = new JsonObject();
        wrongSlot.addProperty("when", "ein Zubehör steckt in einem anderen Platz als seinem slot");
        wrongSlot.add("problems", ExportJson.strings(List.of("accessory_mount")));
        wrongSlot.add("warnings", new JsonArray());
        wrongSlot.addProperty("note", "keine weitere Regel wird ausgewertet");
        gates.add(wrongSlot);
        out.add("gates", gates);
        JsonObject constants = new JsonObject();
        constants.addProperty("esc_margin", ESC_MARGIN);
        constants.addProperty("battery_margin", BATTERY_MARGIN);
        constants.addProperty("motor_count", MOTOR_COUNT);
        constants.addProperty("universal_prop_mount", Mounts.PROP_UNIVERSAL.toString());
        out.add("constants", constants);
        JsonArray warnings = new JsonArray();
        for (String key : ANALYSIS_WARNINGS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("key", key);
            entry.add("text", lang.both(BuildStats.warningTranslationKey(key), "rules.json#analysis_warnings", true));
            warnings.add(entry);
        }
        out.add("analysis_warnings", warnings);
        JsonObject verified = new JsonObject();
        verified.addProperty("builds", RANDOM_BUILDS);
        verified.addProperty("seed", SEED);
        verified.addProperty("mismatches", mismatches);
        out.add("verified", verified);
        return out;
    }
}
