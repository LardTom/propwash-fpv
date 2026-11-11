package de.lardtom.propwash.drone;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class DroneAttributes {
    public static final String SAVE_KEY = "attributes";
    private static @Nullable AttributeSupplier supplier;

    private final AttributeMap map = new AttributeMap(supplier());

    public static synchronized AttributeSupplier supplier() {
        AttributeSupplier current = supplier;
        if (current == null) {
            current = AttributeSupplier.builder().add(Attributes.SCALE).build();
            supplier = current;
        }
        return current;
    }

    public static boolean supports(Holder<Attribute> attribute) {
        return supplier().hasAttribute(attribute);
    }

    public AttributeMap map() {
        return this.map;
    }

    public @Nullable AttributeInstance instance(Holder<Attribute> attribute) {
        return supports(attribute) ? this.map.getInstance(attribute) : null;
    }

    public float scale() {
        return DroneScale.sanitize(this.map.getValue(Attributes.SCALE));
    }

    public boolean changed() {
        AttributeSupplier defaults = supplier();
        for (AttributeInstance.Packed packed : this.map.pack()) {
            if (packed.baseValue() != defaults.getBaseValue(packed.attribute()) || !packed.modifiers().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public List<AttributeInstance.Packed> pack() {
        return this.map.pack();
    }

    public void load(List<AttributeInstance.Packed> packed) {
        this.map.apply(packed);
        this.map.getAttributesToUpdate().clear();
        this.map.getAttributesToSync().clear();
    }
}
