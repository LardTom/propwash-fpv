package de.lardtom.propwash.accessory.display;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.accessory.ItemDisplay;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemDisplays {
    public static final float ITEM_SIZE_MM = 160.0F;

    private static final Set<Identifier> FAILED = ConcurrentHashMap.newKeySet();

    private ItemDisplays() {
    }

    public static Optional<ItemDisplay> display(Identifier accessory, @Nullable AccessoryBehavior behavior, int slot) {
        if (behavior == null) {
            return Optional.empty();
        }
        try {
            Optional<ItemDisplay> display = behavior.display(slot);
            return display == null ? Optional.empty() : display;
        } catch (RuntimeException e) {
            if (FAILED.add(accessory)) {
                Constants.LOG.warn("Zubehör {}: display({}) fehlgeschlagen", accessory, slot, e);
            }
            return Optional.empty();
        }
    }

    public static Matrix4f model(float anchorXmm, float anchorYmm, float anchorZmm, float tiltDegrees, Quaternionfc gimbal, ItemDisplay display,
                                 float unitsPerMm, Matrix4f dest) {
        float size = display.scale() * ITEM_SIZE_MM * unitsPerMm;
        return dest.identity()
                .translate(anchorXmm * unitsPerMm, anchorYmm * unitsPerMm, anchorZmm * unitsPerMm)
                .rotateX((float) Math.toRadians(tiltDegrees))
                .rotate(gimbal)
                .translate((float) display.offsetMm().x() * unitsPerMm, (float) display.offsetMm().y() * unitsPerMm,
                        (float) display.offsetMm().z() * unitsPerMm)
                .rotateY((float) Math.toRadians(display.yawDegrees()))
                .rotateX((float) Math.toRadians(display.pitchDegrees()))
                .rotateZ((float) Math.toRadians(display.rollDegrees()))
                .scale(size);
    }
}
