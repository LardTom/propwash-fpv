package de.lardtom.propwash.drone.client.render;

import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.core.PartCategory;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartModelTest {
    @Test
    void counterClockwisePropsUseTheMirroredModel() {
        for (PropType prop : PropType.values()) {
            PartModel cw = PartModel.prop(prop, false);
            PartModel ccw = PartModel.prop(prop, true);
            assertSame(PartModel.of(prop), cw);
            assertNotSame(cw, ccw);
            assertEquals(prop.key() + PartModel.CCW_SUFFIX, ccw.key());
            assertEquals("propwash:drone_render/prop/" + prop.key() + "_ccw", ccw.itemModelId().toString());
            assertEquals("propwash:models/part/prop/" + prop.key() + "_ccw.json", ccw.modelFile().toString());
            assertEquals("propwash:items/drone_render/prop/" + prop.key() + "_ccw.json", ccw.itemModelFile().toString());
            assertEquals(prop.key() + PartModel.BLUR_SUFFIX, PartModel.blur(prop).key());
        }
    }

    @Test
    void damagedPropsHaveTheirOwnModels() {
        for (PropType prop : PropType.values()) {
            assertSame(PartModel.prop(prop, false), PartModel.prop(prop, false, PropDamage.NONE));
            assertSame(PartModel.prop(prop, true), PartModel.prop(prop, true, PropDamage.NONE));
            assertEquals(prop.key() + PartModel.WORN_SUFFIX, PartModel.prop(prop, false, PropDamage.WORN).key());
            assertEquals(prop.key() + PartModel.WORN_SUFFIX + PartModel.CCW_SUFFIX, PartModel.prop(prop, true, PropDamage.WORN).key());
            assertEquals(prop.key() + "_damaged", PartModel.prop(prop, false, PropDamage.DAMAGED).key());
            assertEquals(prop.key() + "_damaged_ccw", PartModel.prop(prop, true, PropDamage.DAMAGED).key());
            assertEquals(prop.key() + PartModel.BROKEN_SUFFIX, PartModel.prop(prop, false, PropDamage.BROKEN).key());
            assertEquals("propwash:items/drone_render/prop/" + prop.key() + "_broken_ccw.json",
                    PartModel.prop(prop, true, PropDamage.BROKEN).itemModelFile().toString());
            assertTrue(PartModel.all().contains(PartModel.prop(prop, true, PropDamage.BROKEN)));
        }
        assertEquals(PropDamage.NONE, PropDamage.of(1.0F));
        assertEquals(PropDamage.NONE, PropDamage.of(0.9F));
        assertEquals(PropDamage.WORN, PropDamage.of(0.83F));
        assertEquals(PropDamage.WORN, PropDamage.of(0.4F));
        assertEquals(PropDamage.DAMAGED, PropDamage.of(0.32F));
        assertEquals(PropDamage.DAMAGED, PropDamage.of(0.001F));
        assertEquals(PropDamage.BROKEN, PropDamage.of(0.0F));
        for (PropDamage damage : PropDamage.values()) {
            assertEquals(damage, PropDamage.of(damage.representative()), damage.name());
        }
    }

    @Test
    void allListsEveryVariantExactlyOnce() {
        List<PartModel> all = PartModel.all();
        Set<String> names = new HashSet<>();
        for (PartModel part : all) {
            assertTrue(names.add(part.toString()), part.toString());
        }
        int expected = 0;
        for (PartCategory category : PartCategory.values()) {
            expected += category.parts().size();
        }
        expected += PropType.values().length * 8;
        for (AccessoryMount mount : AccessoryMount.values()) {
            if (mount.hasBase()) {
                expected++;
                PartModel base = PartModel.base(mount).orElseThrow();
                assertTrue(all.contains(base), mount.key());
                assertEquals("accessory/mount_" + mount.key(), base.toString());
            } else {
                assertTrue(PartModel.base(mount).isEmpty(), mount.key());
            }
        }
        assertEquals(expected, all.size());
        for (PropType prop : PropType.values()) {
            assertTrue(all.contains(PartModel.prop(prop, true)), prop.key());
            assertTrue(all.contains(PartModel.blur(prop)), prop.key());
        }
    }

    @Test
    void lookupsReturnSharedInstances() {
        assertSame(PartModel.of(FrameType.values()[0]), PartModel.of(FrameType.values()[0]));
        assertSame(PartModel.of(MotorType.values()[0]).itemModelId(), PartModel.of(MotorType.values()[0]).itemModelId());
        assertSame(PartModel.prop(PropType.values()[0], true), PartModel.prop(PropType.values()[0], true));
        assertEquals("frame/" + FrameType.values()[0].key(), PartModel.of(FrameType.values()[0]).toString());
    }

    @Test
    void scaleConstantsFollowTheAssetContract() {
        assertEquals(0.128F, DroneVisualScale.MODEL_UNIT_SCALE, 1.0E-7F);
        assertEquals(1.0F / 128.0F, DroneVisualScale.MM_TO_UNITS, 1.0E-9F);
        assertEquals(0.00175F, DroneVisualScale.blocksPerMillimetre(1.75F), 1.0E-8F);
        assertEquals(0.128F * 1.75F, DroneVisualScale.poseScale(1.75F), 1.0E-7F);
    }

    @Test
    void configCopyIsIndependent() {
        DroneRenderConfig original = new DroneRenderConfig();
        DroneRenderConfig copy = original.copy();
        copy.visualScale = 2.5F;
        copy.propBlurThreshold = 0.5F;
        copy.renderDistance = 64;
        copy.armLeds = !original.armLeds;
        copy.shadow = !original.shadow;
        assertEquals(DroneRenderConfig.DEFAULT_VISUAL_SCALE, original.visualScale);
        assertEquals(new DroneRenderConfig().propBlurThreshold, original.propBlurThreshold);
        assertEquals(new DroneRenderConfig().renderDistance, original.renderDistance);
        assertEquals(new DroneRenderConfig().armLeds, original.armLeds);
        assertEquals(new DroneRenderConfig().shadow, original.shadow);
        DroneRenderConfig again = copy.copy();
        assertEquals(2.5F, again.visualScale);
        assertEquals(0.5F, again.propBlurThreshold);
        assertEquals(64, again.renderDistance);
        assertEquals(copy.armLeds, again.armLeds);
        assertEquals(copy.shadow, again.shadow);
    }
}
