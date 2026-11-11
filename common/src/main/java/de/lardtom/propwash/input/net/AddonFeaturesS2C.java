package de.lardtom.propwash.input.net;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.input.SwitchRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

public record AddonFeaturesS2C(List<Identifier> switches, List<Identifier> axes, List<Identifier> collisionRules) implements CustomPacketPayload {
    public static final int MAX_COLLISION_RULES = 256;
    public static final Type<AddonFeaturesS2C> TYPE = new Type<>(Constants.id("addon_features"));
    public static final StreamCodec<ByteBuf, AddonFeaturesS2C> CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(SwitchRegistry.MAX_SWITCHES)), AddonFeaturesS2C::switches,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(SwitchRegistry.MAX_AXES)), AddonFeaturesS2C::axes,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_COLLISION_RULES)), AddonFeaturesS2C::collisionRules,
            AddonFeaturesS2C::new);

    public AddonFeaturesS2C {
        switches = List.copyOf(switches);
        axes = List.copyOf(axes);
        collisionRules = List.copyOf(collisionRules.size() > MAX_COLLISION_RULES ? collisionRules.subList(0, MAX_COLLISION_RULES) : collisionRules);
    }

    @Override
    public Type<AddonFeaturesS2C> type() {
        return TYPE;
    }
}
