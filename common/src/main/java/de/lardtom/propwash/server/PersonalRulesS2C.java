package de.lardtom.propwash.server;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.server.rules.PersonalRules;
import de.lardtom.propwash.server.rules.RuleTiers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;

public record PersonalRulesS2C(boolean editable, RuleTiers tiers, PersonalRules own) implements CustomPacketPayload {
    public static final Type<PersonalRulesS2C> TYPE = new Type<>(Constants.id("personal_rules"));
    public static final StreamCodec<FriendlyByteBuf, PersonalRulesS2C> CODEC = StreamCodec.of((buf, payload) -> {
        buf.writeBoolean(payload.editable);
        payload.tiers.write(buf);
        payload.own.write(buf);
    }, buf -> new PersonalRulesS2C(buf.readBoolean(), RuleTiers.read(buf), PersonalRules.read(buf)));

    public PersonalRulesS2C {
        Objects.requireNonNull(tiers, "tiers");
        Objects.requireNonNull(own, "own");
    }

    @Override
    public Type<PersonalRulesS2C> type() {
        return TYPE;
    }
}
