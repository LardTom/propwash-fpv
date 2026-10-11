package de.lardtom.propwash.drone.client.render;

import com.mojang.serialization.MapCodec;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.core.CoreComponents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public record PropDamageProperty() implements RangeSelectItemModelProperty {
    public static final Identifier ID = Constants.id("prop_damage");
    public static final MapCodec<PropDamageProperty> MAP_CODEC = MapCodec.unit(new PropDamageProperty());

    public static PropDamage stage(ItemStack stack) {
        Float stored = stack.get(CoreComponents.PART_CONDITION.get());
        return stored == null ? PropDamage.NONE : PropDamage.of(PartWear.quantize(stored));
    }

    @Override
    public float get(ItemStack itemStack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        return stage(itemStack).ordinal();
    }

    @Override
    public MapCodec<PropDamageProperty> type() {
        return MAP_CODEC;
    }
}
