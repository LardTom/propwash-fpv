package de.lardtom.propwash.server;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.server.rules.RuleTier;
import de.lardtom.propwash.server.rules.RuleTiers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;

public record RuleTierC2S(String rule, RuleTier tier) implements CustomPacketPayload {
    public static final Type<RuleTierC2S> TYPE = new Type<>(Constants.id("rule_tier"));
    public static final StreamCodec<FriendlyByteBuf, RuleTierC2S> CODEC = StreamCodec.of((buf, payload) -> {
        buf.writeUtf(payload.rule, RuleTiers.MAX_NAME);
        buf.writeEnum(payload.tier);
    }, buf -> new RuleTierC2S(buf.readUtf(RuleTiers.MAX_NAME), buf.readEnum(RuleTier.class)));

    public RuleTierC2S {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(tier, "tier");
    }

    @Override
    public Type<RuleTierC2S> type() {
        return TYPE;
    }
}
