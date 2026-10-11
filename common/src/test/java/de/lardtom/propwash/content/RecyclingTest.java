package de.lardtom.propwash.content;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.core.PartCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecyclingTest {
    @Test
    void brokenPartsTurnBackIntoMaterials() {
        assertEquals(Constants.id(CraftingMaterial.CARBON_PLATE.key()), Recycling.yieldOf(FrameType.FREESTYLE_5).orElseThrow().itemId());
        assertEquals(Constants.id(CraftingMaterial.COPPER_COIL.key()), Recycling.yieldOf(MotorType.M2207_1750).orElseThrow().itemId());
        assertEquals(Constants.id(CraftingMaterial.CIRCUIT_BOARD.key()), Recycling.yieldOf(StackType.STACK_30).orElseThrow().itemId());
        assertEquals(Constants.id(CraftingMaterial.CIRCUIT_BOARD.key()), Recycling.yieldOf(VideoType.DIGITAL_HD).orElseThrow().itemId());
        assertEquals(Constants.id(CraftingMaterial.LIPO_CELL.key()), Recycling.yieldOf(BatteryType.B6S_1300).orElseThrow().itemId());
    }

    @Test
    void propsAndTheKelpWhoopFrameAreScrap() {
        for (PropType prop : PropType.values()) {
            assertFalse(Recycling.recyclable(prop), prop.key());
        }
        assertFalse(Recycling.recyclable(FrameType.WHOOP_65));
    }

    @Test
    void everyOtherCategoryYieldsOneMaterial() {
        for (PartCategory category : PartCategory.values()) {
            if (category == PartCategory.PROP || category == PartCategory.ACCESSORY) {
                continue;
            }
            category.parts().stream().filter(Recycling::recyclable)
                    .forEach(part -> assertEquals(1, Recycling.yieldOf(part).orElseThrow().count(), part.key()));
            assertTrue(category.parts().stream().anyMatch(Recycling::recyclable), category.key());
        }
    }
}
