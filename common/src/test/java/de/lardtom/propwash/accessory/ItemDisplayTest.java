package de.lardtom.propwash.accessory;

import de.lardtom.propwash.accessory.display.ItemDisplays;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.accessory.ItemDisplay;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemDisplayTest {
    private static final float UNITS_PER_MM = 1.0F / 128.0F;
    private static final float EPS = 1.0E-5F;

    @Test
    void anchorAndOffsetLandInModelUnits() {
        Matrix4f m = ItemDisplays.model(0.0F, 0.0F, -72.0F, 0.0F, new Quaternionf(), ItemDisplay.at(0.0, -40.0, 12.8), UNITS_PER_MM, new Matrix4f());
        Vector3f origin = m.transformPosition(new Vector3f());
        assertEquals(0.0F, origin.x, EPS);
        assertEquals(-40.0F / 128.0F, origin.y, EPS);
        assertEquals((-72.0F + 12.8F) / 128.0F, origin.z, EPS);
    }

    @Test
    void scaleOneIsTheItemSize() {
        Matrix4f m = ItemDisplays.model(0.0F, 0.0F, 0.0F, 0.0F, new Quaternionf(), ItemDisplay.at(0.0, 0.0, 0.0), UNITS_PER_MM, new Matrix4f());
        Vector3f edge = m.transformPosition(new Vector3f(1.0F, 0.0F, 0.0F));
        assertEquals(ItemDisplays.ITEM_SIZE_MM / 128.0F, edge.x, EPS);
        Matrix4f twice = ItemDisplays.model(0.0F, 0.0F, 0.0F, 0.0F, new Quaternionf(), ItemDisplay.at(0.0, 0.0, 0.0).scaled(2.0F), UNITS_PER_MM,
                new Matrix4f());
        assertEquals(2.0F * edge.x, twice.transformPosition(new Vector3f(1.0F, 0.0F, 0.0F)).x, EPS);
    }

    @Test
    void mountTiltTurnsTheOffset() {
        Matrix4f m = ItemDisplays.model(0.0F, 10.0F, 0.0F, 90.0F, new Quaternionf(), ItemDisplay.at(0.0, 0.0, -128.0), UNITS_PER_MM, new Matrix4f());
        Vector3f origin = m.transformPosition(new Vector3f());
        assertEquals(0.0F, origin.x, EPS);
        assertEquals(10.0F / 128.0F + 1.0F, origin.y, EPS);
        assertEquals(0.0F, origin.z, EPS);
    }

    @Test
    void yawPitchRollTurnTheItemAroundItsOwnPoint() {
        ItemDisplay display = ItemDisplay.at(0.0, -64.0, 0.0).rotated(90.0F, 0.0F, 0.0F);
        Matrix4f m = ItemDisplays.model(0.0F, 0.0F, 0.0F, 0.0F, new Quaternionf(), display, UNITS_PER_MM, new Matrix4f());
        Vector3f origin = m.transformPosition(new Vector3f());
        assertEquals(-0.5F, origin.y, EPS);
        Vector3f tip = m.transformPosition(new Vector3f(1.0F, 0.0F, 0.0F)).sub(origin);
        float size = ItemDisplays.ITEM_SIZE_MM / 128.0F;
        assertEquals(0.0F, tip.x, EPS);
        assertEquals(-size, tip.z, EPS);
        Matrix4f pitched = ItemDisplays.model(0.0F, 0.0F, 0.0F, 0.0F, new Quaternionf(), ItemDisplay.at(0.0, 0.0, 0.0).rotated(0.0F, 90.0F, 0.0F),
                UNITS_PER_MM, new Matrix4f());
        Vector3f up = pitched.transformPosition(new Vector3f(0.0F, 1.0F, 0.0F));
        assertEquals(size, up.z, EPS);
        Matrix4f rolled = ItemDisplays.model(0.0F, 0.0F, 0.0F, 0.0F, new Quaternionf(), ItemDisplay.at(0.0, 0.0, 0.0).rotated(0.0F, 0.0F, 90.0F),
                UNITS_PER_MM, new Matrix4f());
        Vector3f right = rolled.transformPosition(new Vector3f(1.0F, 0.0F, 0.0F));
        assertEquals(size, right.y, EPS);
    }

    @Test
    void gimbalPoseTurnsEverythingOnTheMount() {
        Quaternionf gimbal = new Quaternionf().rotateY((float) Math.toRadians(90.0));
        Matrix4f m = ItemDisplays.model(0.0F, 0.0F, 0.0F, 0.0F, gimbal, ItemDisplay.at(0.0, 0.0, -128.0), UNITS_PER_MM, new Matrix4f());
        Vector3f origin = m.transformPosition(new Vector3f());
        assertEquals(-1.0F, origin.x, EPS);
        assertEquals(0.0F, origin.z, EPS);
    }

    @Test
    void displayCallIsGuarded() {
        Identifier id = Identifier.fromNamespaceAndPath("propwash_test", "display");
        AccessoryBehavior shown = new AccessoryBehavior() {
            @Override
            public Optional<ItemDisplay> display(int slot) {
                return slot == 0 ? Optional.of(ItemDisplay.at(0.0, -30.0, 0.0)) : Optional.empty();
            }
        };
        assertTrue(ItemDisplays.display(id, shown, 0).isPresent());
        assertEquals(Optional.empty(), ItemDisplays.display(id, shown, 1));
        assertEquals(Optional.empty(), ItemDisplays.display(id, null, 0));
        assertEquals(Optional.empty(), ItemDisplays.display(id, new AccessoryBehavior() {
        }, 0));
        AccessoryBehavior broken = new AccessoryBehavior() {
            @Override
            public Optional<ItemDisplay> display(int slot) {
                throw new IllegalStateException("kaputt");
            }
        };
        assertEquals(Optional.empty(), ItemDisplays.display(id, broken, 0));
        assertThrows(IllegalArgumentException.class, () -> ItemDisplay.at(0.0, 600.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> ItemDisplay.at(0.0, 0.0, 0.0).scaled(0.0F));
    }
}
