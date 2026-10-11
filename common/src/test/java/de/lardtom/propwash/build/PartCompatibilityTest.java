package de.lardtom.propwash.build;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.part.BatteryDefinition;
import de.lardtom.propwash.api.part.BatteryFitRule;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.FrameRole;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.PropMaterial;
import de.lardtom.propwash.apiimpl.BuildAccessImpl;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.BuildTexts;
import de.lardtom.propwash.content.client.WorkbenchReport;
import de.lardtom.propwash.core.DroneBuildCodecs;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.drone.PropService;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleEffects;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleValues;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartCompatibilityTest {
    private static final String NAMESPACE = "propwash_freedomtest";
    private static final AtomicInteger SERIAL = new AtomicInteger();
    private static final Identifier SHAFT_M5 = Identifier.fromNamespaceAndPath(NAMESPACE, "m5");
    private static final Identifier HUB_T_MOUNT = Identifier.fromNamespaceAndPath(NAMESPACE, "t_mount");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path + "_" + SERIAL.incrementAndGet());
    }

    @SuppressWarnings("unchecked")
    private static <T extends PartInfo> T part(PartDefinition definition) {
        return (T) PartIndex.detached(definition);
    }

    static PropType prop13Inch() {
        return part(PropDefinition.builder(id("p13_7_2b")).mass(28.0).size(330, 7.0, 2).coefficients(0.095, 0.040)
                .material(PropMaterial.CARBON_FIBER_NYLON).build());
    }

    static BatteryType lipo8s() {
        return part(BatteryDefinition.builder(id("b8s_2200")).mass(450.0).cells(8).capacity(2200).cellResistance(0.008).cRating(75)
                .chemistry(BatteryDefinition.LIPO).build());
    }

    static FrameType xClass13() {
        return part(FrameDefinition.builder(id("xclass_13")).mass(1450.0).role(FrameRole.X_CLASS).wheelbase(820).props(250, 330)
                .motorMount(Mounts.MOTOR_M28).stackMount(Mounts.STACK_S30).battery(true, 40.0).electronics(30.0).cameraTilt(25.0)
                .aero(0.08, 1.0).impactTolerance(6.0).batteryFit(new BatteryFitRule(6, 8, 2500.0, 8, 2000)).build());
    }

    private static MotorType motorWithShaft() {
        return part(MotorDefinition.builder(id("m2207_m5")).mass(32.0).stator("2207").kv(1750).mount(Mounts.MOTOR_M22).maxCurrent(40.0)
                .resistance(0.06).cells(4, 6).propMount(SHAFT_M5).build());
    }

    private static PropType propWithTMount() {
        return part(PropDefinition.builder(id("p5_t_mount")).mass(4.2).size(130, 4.1, 3).coefficients(0.105, 0.062).mount(HUB_T_MOUNT).build());
    }

    static Map<DroneBuild.Issue, DroneBuild> refusedCombinations() {
        Map<DroneBuild.Issue, DroneBuild> out = new LinkedHashMap<>();
        DroneBuild freestyle = DroneBuild.FREESTYLE;
        out.put(DroneBuild.Issue.MOTOR_MOUNT, new DroneBuild(freestyle.frame(), freestyle.stack(), MotorType.M0802_22000, freestyle.prop(),
                freestyle.video(), freestyle.battery()));
        out.put(DroneBuild.Issue.PROP_TOO_LARGE, DroneBuild.WHOOP.withProp(PropType.P7_2B));
        out.put(DroneBuild.Issue.PROP_TOO_SMALL, new DroneBuild(xClass13(), StackType.STACK_30, MotorType.M2807_1300, PropType.P51_3B,
                VideoType.DIGITAL_HD, lipo8s()));
        out.put(DroneBuild.Issue.PROP_MOUNT, new DroneBuild(freestyle.frame(), freestyle.stack(), motorWithShaft(), propWithTMount(),
                freestyle.video(), freestyle.battery()));
        out.put(DroneBuild.Issue.STACK_MOUNT, new DroneBuild(freestyle.frame(), StackType.AIO_WHOOP, freestyle.motor(), freestyle.prop(),
                freestyle.video(), freestyle.battery()));
        out.put(DroneBuild.Issue.BATTERY_FRAME, DroneBuild.WHOOP.withBattery(BatteryType.B6S_1300));
        out.put(DroneBuild.Issue.ACCESSORY_MOUNT, DroneBuild.WHOOP.withAccessory(AccessoryType.ACTION_CAM_LIGHT));
        return out;
    }

    static DroneBuild thirteenInchWhoop() {
        return DroneBuild.WHOOP.withProp(prop13Inch());
    }

    static DroneBuild eightCellsOnWhoopAio() {
        return DroneBuild.WHOOP.withBattery(lipo8s());
    }

    private static DroneBuild missingMotor() {
        JsonObject json = DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, DroneBuild.FREESTYLE).getOrThrow().getAsJsonObject();
        json.addProperty("motor", "justmoreparts:not_installed");
        return DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static boolean key(Component text, String key) {
        return text.getContents() instanceof TranslatableContents contents && key.equals(contents.getKey());
    }

    private static boolean liftedLine(Component text, DroneBuild.Issue issue) {
        return text.getContents() instanceof TranslatableContents lifted && "propwash.content.workbench.lifted".equals(lifted.getKey())
                && lifted.getArgs().length == 1 && lifted.getArgs()[0] instanceof Component inner
                && inner.getContents() instanceof TranslatableContents name && issue.translationKey().equals(name.getKey());
    }

    @Test
    void everyPreviouslyRefusedCombinationIsRefusedWithTheRuleOnAndAllowedWithItOff() {
        Map<DroneBuild.Issue, DroneBuild> cases = refusedCombinations();
        for (DroneBuild.Issue issue : DroneBuild.Issue.values()) {
            if (issue.compatibility() && !cases.containsKey(issue) && !List.of(DroneBuild.Issue.MOTOR_OVERVOLTAGE,
                    DroneBuild.Issue.MOTOR_UNDERVOLTAGE, DroneBuild.Issue.STACK_VOLTAGE, DroneBuild.Issue.ESC_UNDERSIZED,
                    DroneBuild.Issue.BATTERY_WEAK, DroneBuild.Issue.BATTERY_UNDERSIZED).contains(issue)) {
                throw new AssertionError("kein Testfall für " + issue);
            }
        }
        cases.forEach((issue, build) -> {
            assertTrue(build.problems().contains(issue), issue + ": " + build.problems());
            assertTrue(build.problems(PartCompatibility.ENFORCED).contains(issue), issue.name());
            assertFalse(build.buildable(PartCompatibility.ENFORCED), issue.name());
            assertFalse(build.buildable(), issue.name());
            assertEquals(List.of(), build.liftedProblems(PartCompatibility.ENFORCED), issue.name());
            assertTrue(build.buildable(PartCompatibility.LIFTED), issue + ": " + build.problems(PartCompatibility.LIFTED));
            assertEquals(List.of(), build.problems(PartCompatibility.LIFTED), issue.name());
            assertTrue(build.liftedProblems(PartCompatibility.LIFTED).contains(issue), issue.name());
            assertEquals(build.problems(), build.liftedProblems(PartCompatibility.LIFTED), issue.name());
            BuildSlots.Check check = BuildSlots.check(BuildSlots.layout(build));
            assertTrue(check.complete(), issue.name());
            assertFalse(check.buildable(PartCompatibility.ENFORCED), issue.name());
            assertTrue(check.buildable(PartCompatibility.LIFTED), issue.name());
        });
    }

    @Test
    void absurdBuildsAreAllowedOnlyWithTheRuleOff() {
        DroneBuild whoop13 = thirteenInchWhoop();
        assertTrue(whoop13.problems().contains(DroneBuild.Issue.PROP_TOO_LARGE));
        assertFalse(whoop13.buildable(PartCompatibility.ENFORCED));
        assertTrue(whoop13.buildable(PartCompatibility.LIFTED));
        DroneBuild eightCells = eightCellsOnWhoopAio();
        assertTrue(eightCells.problems().contains(DroneBuild.Issue.BATTERY_FRAME));
        assertTrue(eightCells.warnings().contains(DroneBuild.Issue.STACK_VOLTAGE));
        assertTrue(eightCells.warnings().contains(DroneBuild.Issue.MOTOR_OVERVOLTAGE));
        assertFalse(eightCells.buildable(PartCompatibility.ENFORCED));
        assertTrue(eightCells.buildable(PartCompatibility.LIFTED));
        assertTrue(AirframeSpec.of(eightCells).escOvervoltage, "Überspannung bleibt Sache der Hardware-Grenzen");
        DroneBuild everything = new DroneBuild(FrameType.WHOOP_65, StackType.STACK_30, MotorType.M2807_1300, prop13Inch(), VideoType.DIGITAL_HD,
                lipo8s(), Accessories.NONE.with(AccessoryType.ACTION_CAM_HEAVY).with(AccessoryType.GRIPPER));
        assertTrue(everything.problems().size() >= 5, everything.problems().toString());
        assertTrue(everything.buildable(PartCompatibility.LIFTED));
    }

    @Test
    void missingPartsStayRefusedEvenWithTheRuleOff() {
        DroneBuild build = missingMotor();
        assertFalse(DroneBuild.Issue.MISSING_PART.compatibility());
        assertEquals(DroneBuild.Issue.MISSING_PART, build.problems().getFirst());
        assertFalse(build.buildable(PartCompatibility.ENFORCED));
        assertFalse(build.buildable(PartCompatibility.LIFTED));
        assertEquals(List.of(DroneBuild.Issue.MISSING_PART), build.problems(PartCompatibility.LIFTED));
        assertFalse(AirframeSpec.of(build).assemblable);
    }

    @Test
    void slotsStillOnlyTakeTheirOwnCategoryAndMount() {
        DroneBuild build = DroneBuild.WHOOP;
        assertFalse(BuildSlots.accepts(BuildSlots.FRAME, build.motor()));
        assertFalse(BuildSlots.accepts(BuildSlots.MOTOR, build.prop()));
        assertFalse(BuildSlots.accepts(BuildSlots.accessorySlot(AccessoryMount.BOTTOM), AccessoryType.ACTION_CAM_LIGHT));
        assertTrue(BuildSlots.accepts(BuildSlots.accessorySlot(AccessoryMount.TOP), AccessoryType.ACTION_CAM_LIGHT));
        List<PartInfo> mixed = new ArrayList<>(BuildSlots.layout(DroneBuild.FREESTYLE));
        mixed.set(BuildSlots.MOTOR, MotorType.M2306_1860);
        BuildSlots.Check check = BuildSlots.check(mixed);
        assertFalse(check.complete());
        assertFalse(check.buildable(PartCompatibility.LIFTED));
    }

    @Test
    void fieldServiceFollowsTheRule() {
        assertFalse(PartCompatibility.ENFORCED.batteryFits(FrameType.WHOOP_65, BatteryType.B6S_1300));
        assertTrue(PartCompatibility.LIFTED.batteryFits(FrameType.WHOOP_65, BatteryType.B6S_1300));
        assertTrue(PartCompatibility.ENFORCED.batteryFits(FrameType.WHOOP_65, BatteryType.B1S_300_HV));
        assertEquals(PropService.Fit.TOO_LARGE, PropService.fit(FrameType.WHOOP_65, PropType.P31_4B, PropType.P7_2B, 4));
        assertEquals(PropService.Fit.TOO_LARGE, PropService.fit(PartCompatibility.ENFORCED, FrameType.WHOOP_65, PropType.P31_4B, PropType.P7_2B, 4));
        assertEquals(PropService.Fit.SWAP_ALL, PropService.fit(PartCompatibility.LIFTED, FrameType.WHOOP_65, PropType.P31_4B, PropType.P7_2B, 4));
        assertEquals(PropService.Fit.NEED_FOUR, PropService.fit(PartCompatibility.LIFTED, FrameType.WHOOP_65, PropType.P31_4B, PropType.P7_2B, 2));
        DroneBuild xClass = new DroneBuild(xClass13(), StackType.STACK_30, MotorType.M2807_1300, prop13Inch(), VideoType.DIGITAL_HD, lipo8s());
        assertEquals(PropService.Fit.TOO_SMALL, PropService.fit(PartCompatibility.ENFORCED, xClass, PropType.P51_3B, 4));
        assertEquals(PropService.Fit.SWAP_ALL, PropService.fit(PartCompatibility.LIFTED, xClass, PropType.P51_3B, 4));
        DroneBuild freestyle = DroneBuild.FREESTYLE;
        DroneBuild shaft = new DroneBuild(freestyle.frame(), freestyle.stack(), motorWithShaft(), freestyle.prop(), freestyle.video(),
                freestyle.battery());
        PropType tMount = propWithTMount();
        assertEquals(PropService.Fit.WRONG_MOUNT, PropService.fit(PartCompatibility.ENFORCED, shaft, tMount, 4));
        assertEquals(PropService.Fit.SWAP_ALL, PropService.fit(PartCompatibility.LIFTED, shaft, tMount, 4));
        assertEquals(PropService.Fit.TOO_LARGE, PropService.fit(PartCompatibility.ENFORCED, DroneBuild.WHOOP, PropType.P7_2B, 4));
        assertEquals(PropService.Fit.SWAP_ONE, PropService.fit(PartCompatibility.ENFORCED, DroneBuild.FREESTYLE, DroneBuild.FREESTYLE.prop(), 1));
        for (DroneBuild preset : DroneBuild.PRESETS) {
            for (PropType prop : PropType.values()) {
                if (!prop.builtin()) {
                    continue;
                }
                boolean real = preset.withProp(prop).buildable();
                PropService.Fit fit = PropService.fit(PartCompatibility.ENFORCED, preset, prop, 4);
                assertEquals(real, PropService.allowed(fit), preset.frame().key() + " " + prop.key() + " " + fit);
                assertTrue(PropService.allowed(PropService.fit(PartCompatibility.LIFTED, preset, prop, 4)), preset.frame().key() + " " + prop.key());
            }
        }
    }

    @Test
    void worldRuleIsAGameplaySwitchThatDoesNotCountAsModifiedPhysics() {
        RuleSpec spec = WorldRuleSpecs.PART_COMPATIBILITY;
        assertEquals("propwash:part_compatibility", spec.id());
        assertEquals(RuleSpec.Kind.BOOL, spec.kind());
        assertTrue(spec.defaultOn());
        assertEquals(RuleSpec.Group.GAMEPLAY, spec.group());
        assertEquals(spec, WorldRuleSpecs.byName("part_compatibility").orElseThrow());
        assertEquals(PartCompatibility.ENFORCED, RuleEffects.partCompatibility(RuleValues.DEFAULTS));
        RuleValues off = RuleValues.DEFAULTS.with(spec, 0);
        assertEquals(PartCompatibility.LIFTED, RuleEffects.partCompatibility(off));
        assertFalse(PhysicsRules.of(off).modified());
        assertEquals(PhysicsRules.DEFAULT, PhysicsRules.of(off));
        assertEquals(PartCompatibility.ENFORCED, RuleEffects.partCompatibility(RuleValues.DEFAULTS.with(WorldRuleSpecs.HARDWARE_LIMITS, 0)));
        assertTrue(PhysicsRules.of(off.with(WorldRuleSpecs.HARDWARE_LIMITS, 0)).modified());
        assertFalse(PhysicsRules.of(off.with(WorldRuleSpecs.HARDWARE_LIMITS, 0)).toSimTuning().escOvervoltage);
        assertTrue(PhysicsRules.of(off).toSimTuning().escOvervoltage);
    }

    @Test
    void serverRefusesToFlyOnlyRealCompatibilityProblemsWhileTheRuleIsOn() {
        refusedCombinations().forEach((issue, build) -> {
            assertTrue(ServerRules.refusesParts(PartCompatibility.ENFORCED, build), issue.name());
            assertFalse(ServerRules.refusesParts(PartCompatibility.LIFTED, build), issue.name());
        });
        assertFalse(ServerRules.refusesParts(PartCompatibility.ENFORCED, DroneBuild.FREESTYLE));
        assertFalse(ServerRules.refusesParts(PartCompatibility.ENFORCED, missingMotor()));
    }

    @Test
    void apiCheckMovesLiftedProblemsToWarnings() {
        refusedCombinations().forEach((issue, build) -> {
            String key = issue.name().toLowerCase(Locale.ROOT);
            BuildCheck strict = BuildAccessImpl.check(Conversions.spec(build), build, PartCompatibility.ENFORCED);
            assertFalse(strict.buildable(), issue.name());
            assertTrue(strict.problems().contains(key), issue.name());
            assertFalse(strict.warnings().contains(key), issue.name());
            BuildCheck free = BuildAccessImpl.check(Conversions.spec(build), build, PartCompatibility.LIFTED);
            assertTrue(free.buildable(), issue + ": " + free.problems());
            assertTrue(free.warnings().contains(key), issue + ": " + free.warnings());
            assertEquals(BuildCheck.translationKey(key), issue.translationKey());
        });
        DroneBuild valid = DroneBuild.RACER;
        assertEquals(BuildAccessImpl.check(Conversions.spec(valid), valid, PartCompatibility.ENFORCED),
                BuildAccessImpl.check(Conversions.spec(valid), valid, PartCompatibility.LIFTED));
    }

    @Test
    void workbenchShowsLiftedProblemsAsWarningsAndTheResultIsAllowed() {
        refusedCombinations().forEach((issue, build) -> {
            BuildSlots.Check check = BuildSlots.check(BuildSlots.layout(build));
            WorkbenchReport strict = WorkbenchReport.of(check, true, PartCompatibility.ENFORCED);
            assertFalse(strict.buildable(), issue.name());
            assertTrue(strict.issues().stream().anyMatch(line -> line.color() == WorkbenchReport.COLOR_BAD), issue.name());
            assertEquals(List.of(), strict.liftedProblems(), issue.name());
            WorkbenchReport free = WorkbenchReport.of(check, true, PartCompatibility.LIFTED);
            assertTrue(free.buildable(), issue.name());
            assertTrue(free.liftedProblems().contains(issue), issue.name());
            assertTrue(free.issues().stream().anyMatch(line -> line.color() == WorkbenchReport.COLOR_WARN && key(line.text(), issue.translationKey())),
                    issue + ": " + free.issues());
            assertTrue(free.issues().stream().noneMatch(line -> line.color() == WorkbenchReport.COLOR_BAD && key(line.text(), issue.translationKey())),
                    issue.name());
            assertTrue(free.issues().stream().anyMatch(line -> line.color() == WorkbenchReport.COLOR_WARN && line.heading()
                    && key(line.text(), "propwash.content.workbench.lifted_header")), issue.name());
            assertEquals(1, free.issues().stream().filter(WorkbenchReport.Line::heading).count(), issue.name());
            assertTrue(strict.issues().stream().anyMatch(line -> line.color() == WorkbenchReport.COLOR_BAD && key(line.text(), issue.translationKey())),
                    issue.name());
            assertTrue(strict.issues().stream().noneMatch(line -> key(line.text(), "propwash.content.workbench.lifted_header")), issue.name());
            for (PartCategory category : BuildSlots.affectedBy(issue)) {
                assertEquals(WorkbenchReport.COLOR_BAD, strict.highlight(category), issue + " " + category);
                assertEquals(WorkbenchReport.COLOR_WARN, free.highlight(category), issue + " " + category);
            }
            List<BuildTexts.Line> lines = BuildTexts.lines(build, null, PartCompatibility.LIFTED);
            assertTrue(lines.stream().noneMatch(line -> line.severity() == BuildTexts.Severity.PROBLEM), issue.name());
            assertTrue(lines.stream().anyMatch(line -> line.severity() == BuildTexts.Severity.WARNING && liftedLine(line.text(), issue)), issue.name());
        });
        WorkbenchReport valid = WorkbenchReport.of(BuildSlots.check(BuildSlots.layout(DroneBuild.FREESTYLE)), true, PartCompatibility.LIFTED);
        WorkbenchReport validStrict = WorkbenchReport.of(BuildSlots.check(BuildSlots.layout(DroneBuild.FREESTYLE)), true, PartCompatibility.ENFORCED);
        assertEquals(validStrict.issues().size(), valid.issues().size());
    }
}
