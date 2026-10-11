package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonParser;
import de.lardtom.propwash.api.part.MotorDefinition;
import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartVisual;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PropType;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonModelsTest {
    @Test
    void foreignPartsLookForModelsInTheirOwnNamespace() {
        PartModel motor = PartModel.of(RenderTestParts.M2806);
        assertEquals("rendertest:drone_render/motor/m2806_1300", motor.itemModelId().toString());
        assertEquals("rendertest:models/part/motor/m2806_1300.json", String.valueOf(motor.modelFile()));
        assertEquals("rendertest:items/drone_render/motor/m2806_1300.json", motor.itemModelFile().toString());
        assertEquals("rendertest:motor/m2806_1300", motor.name());
        assertEquals("rendertest", motor.namespace());
        assertEquals("motor", motor.category());
        assertEquals("m2806_1300", motor.key());
        assertSame(motor, PartModel.of(RenderTestParts.M2806));
        assertEquals("rendertest:motor/m2806_1300_paint", motor.paintVariant().name());
        assertSame(motor.paintVariant(), motor.paintVariant().paintVariant());
        PropType prop = RenderTestParts.P13;
        assertEquals("rendertest:drone_render/prop/p13_2b_carbon_ccw", PartModel.prop(prop, true).itemModelId().toString());
        assertEquals("rendertest:prop/p13_2b_carbon_blur", PartModel.blur(prop).name());
        assertEquals("rendertest:prop/p13_2b_carbon_broken_ccw", PartModel.prop(prop, true, PropDamage.BROKEN).name());
        assertEquals(9, PartModel.variants(prop).size());
        assertEquals(1, PartModel.variants(RenderTestParts.X_CLASS).size());
        assertEquals("rendertest:frame/xclass_13", PartModel.of(RenderTestParts.X_CLASS).name());
    }

    @Test
    void shippedModelListOnlyHoldsBuiltinParts() {
        for (PartModel model : PartModel.all()) {
            assertEquals("propwash", model.namespace(), model.toString());
        }
        assertFalse(PartModel.all().contains(PartModel.of(RenderTestParts.M2806)));
        assertTrue(PartModel.known().containsAll(PartModel.all()));
    }

    @Test
    void renderModelOverrideReusesOtherModels() {
        MotorType borrowed = (MotorType) PartIndex.detached(MotorDefinition.builder(RenderTestParts.id("m2207_clone")).stator("2207").kv(1900)
                .mount(Mounts.MOTOR_M22).maxCurrent(45.0).resistance(0.05).cells(4, 6)
                .basics(PartBasics.of(33.0).withVisual(PartVisual.NONE.withRenderModel(Identifier.parse("propwash:drone_render/motor/m2207_1750"))))
                .build());
        assertSame(PartModel.of(MotorType.M2207_1750), PartModel.of(borrowed));
        MotorType custom = (MotorType) PartIndex.detached(MotorDefinition.builder(RenderTestParts.id("m_custom")).stator("2306").kv(2400)
                .mount(Mounts.MOTOR_M22).maxCurrent(45.0).resistance(0.05).cells(4, 6)
                .basics(PartBasics.of(30.0).withVisual(PartVisual.NONE.withRenderModel(RenderTestParts.id("special/big_motor"))))
                .build());
        PartModel model = PartModel.of(custom);
        assertNull(model.modelFile());
        assertEquals("rendertest:items/special/big_motor.json", model.itemModelFile().toString());
        assertEquals("rendertest:items/special/big_motor_ccw.json", model.variant(PartModel.CCW_SUFFIX).itemModelFile().toString());
    }

    @Test
    void layoutKeysResolveAgainstTheFileNamespace() {
        assertEquals("rendertest:deadcat_5", String.valueOf(DroneLayoutParser.resolve("deadcat_5", "rendertest")));
        assertEquals("propwash:freestyle_5", String.valueOf(DroneLayoutParser.resolve("propwash:freestyle_5", "rendertest")));
        assertEquals("propwash:race_5", String.valueOf(DroneLayoutParser.resolve("race_5", "propwash")));
        assertNull(DroneLayoutParser.resolve("Bad Key", "rendertest"));
        DroneLayoutParser.Result result = DroneLayoutParser.parse(JsonParser.parseString("""
                {"frames": {
                  "freestyle_5": {"motors": [[-70, 3, -80], [70, 3, -80], [-70, 3, 80], [70, 3, 80]]},
                  "propwash:race_5": {"motors": [[-70, 3, -80], [70, 3, -80], [-70, 3, 80], [70, 3, 80]], "prop_height": 17.0}
                },
                 "motor_seat": {"m2806_1300": 17.0, "propwash:m2207_1750": 18.5}}
                """), "rendertest");
        assertTrue(result.layouts().containsKey(FrameType.RACE_5));
        assertFalse(result.layouts().containsKey(FrameType.FREESTYLE_5));
        assertTrue(result.entries().containsKey(RenderTestParts.id("freestyle_5")));
        String problems = result.problems().toString();
        assertTrue(problems.contains("unbekannter Rahmen freestyle_5"), problems);
        assertFalse(problems.contains("kein Eintrag"), problems);
        assertTrue(DroneLayoutParser.parse(JsonParser.parseString("{\"motor_seat\": {\"m2806_1300\": 16.0}}"), "rendertest").problems().isEmpty());
        assertEquals(1, DroneLayoutParser.parse(JsonParser.parseString("{\"motor_seat\": {}}"), "propwash").problems().size());
        List<String> seatProblems = new ArrayList<>();
        Map<Identifier, Float> seats = DroneLayoutParser.motorSeats(JsonParser.parseString("""
                {"motor_seat": {"m2806_1300": 17.0, "propwash:m2207_1750": 18.5, "broken": "x"}}
                """), "rendertest", seatProblems);
        assertEquals(17.0F, seats.get(RenderTestParts.id("m2806_1300")));
        assertEquals(18.5F, seats.get(Identifier.parse("propwash:m2207_1750")));
        assertEquals(1, seatProblems.size());
    }

    @Test
    void partialEntriesOnlyAdjustForeignFrames() {
        List<String> problems = new ArrayList<>();
        DroneLayout adjusted = DroneLayoutParser.parseFrame(RenderTestParts.DEADCAT, JsonParser.parseString("""
                {"prop_height": 14.0, "battery": [0.0, 33.0, 5.0], "accessories": {"top": {"position": [0, 40, -70], "tilt": 25}}}
                """), problems);
        assertTrue(problems.isEmpty(), problems.toString());
        DroneLayout definition = DroneLayout.fromDefinition(RenderTestParts.DEADCAT);
        assertTrue(adjusted.fromFile());
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            assertEquals(definition.motor(slot), adjusted.motor(slot));
            assertEquals(definition.counterClockwise(slot), adjusted.counterClockwise(slot));
        }
        assertEquals(14.0F, adjusted.propHeight(), 0.0F);
        assertEquals(33.0F, adjusted.battery().y(), 0.0F);
        assertEquals(25.0, adjusted.accessory(AccessoryMount.TOP).orElseThrow().tiltDegrees(), 0.0);
        assertTrue(adjusted.accessory(AccessoryMount.BOTTOM).isPresent());
        List<String> builtinProblems = new ArrayList<>();
        assertNull(DroneLayoutParser.parseFrame(FrameType.FREESTYLE_5, JsonParser.parseString("{\"prop_height\": 14.0}"), builtinProblems));
        assertTrue(builtinProblems.toString().contains("motors"), builtinProblems.toString());
    }

    @Test
    void paintKeysAreNormalisedPerNamespace() {
        assertEquals("rendertest:motor/m2806_1300", DroneRenderResources.paintKey("motor/m2806_1300", "rendertest"));
        assertEquals("prop/p51_3b", DroneRenderResources.paintKey("propwash:prop/p51_3b", "rendertest"));
        assertEquals("prop/p51_3b", DroneRenderResources.paintKey("prop/p51_3b", "propwash"));
        assertEquals("other:frame/x", DroneRenderResources.paintKey("other:frame/x", "rendertest"));
        assertNull(DroneRenderResources.paintKey("", "rendertest"));
        Map<String, List<DroneRenderResources.TintGroup>> groups = DroneRenderResources.parsePaint(JsonParser.parseString("""
                {"models": {"motor/m2806_1300": [{"channel": "motor", "base": "#c8c8c8"}]}}
                """), "rendertest");
        assertEquals(Set.of("rendertest:motor/m2806_1300"), groups.keySet());
        assertEquals(PartModel.of(RenderTestParts.M2806).name(), groups.keySet().iterator().next());
        assertEquals(List.of("propwash", "alpha", "zeta"), DroneRenderResources.namespacesInOrder(Set.of("zeta", "propwash", "alpha")));
    }

    @Test
    void partIconsFitTheGuiView() {
        Vector3f min = new Vector3f(-0.5F, -0.5F, -0.5F);
        Vector3f max = new Vector3f(0.5F, 0.5F, 0.5F);
        float cube = PartItemRenderer.guiExtent(min, max);
        assertTrue(cube > 1.4F && cube < 1.8F, "Würfel " + cube);
        float flat = PartItemRenderer.guiExtent(new Vector3f(-1.0F, -0.01F, -1.0F), new Vector3f(1.0F, 0.01F, 1.0F));
        assertEquals(2.0F * (float) Math.sqrt(2.0), flat, 0.05F);
        assertEquals(PartItemRenderer.guiExtent(min, max) * 2.0F, PartItemRenderer.guiExtent(min.mul(2.0F, new Vector3f()),
                max.mul(2.0F, new Vector3f())), 1.0E-4F);
    }
}
