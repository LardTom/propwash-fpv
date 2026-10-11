package de.lardtom.propwash.server;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.server.rules.RulePreset;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;

public record WorldRulePresetC2S(String preset) implements CustomPacketPayload {
    public static final Type<WorldRulePresetC2S> TYPE = new Type<>(Constants.id("world_rule_preset"));
    public static final StreamCodec<ByteBuf, WorldRulePresetC2S> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(RulePreset.MAX_ID_LENGTH), WorldRulePresetC2S::preset,
            WorldRulePresetC2S::new);

    public WorldRulePresetC2S {
        Objects.requireNonNull(preset, "preset");
    }

    public WorldRulePresetC2S(RulePreset preset) {
        this(preset.id());
    }

    @Override
    public Type<WorldRulePresetC2S> type() {
        return TYPE;
    }
}
