package de.lardtom.propwash.server;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record WorldRuleEditC2S(List<WorldRulesS2C.Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 64;
    public static final Type<WorldRuleEditC2S> TYPE = new Type<>(Constants.id("world_rule_edit"));
    public static final StreamCodec<ByteBuf, WorldRuleEditC2S> CODEC = StreamCodec.composite(
            WorldRulesS2C.Entry.CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), WorldRuleEditC2S::entries,
            WorldRuleEditC2S::new);

    public WorldRuleEditC2S {
        entries = List.copyOf(entries);
    }

    @Override
    public Type<WorldRuleEditC2S> type() {
        return TYPE;
    }
}
