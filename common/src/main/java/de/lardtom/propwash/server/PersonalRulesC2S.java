package de.lardtom.propwash.server;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.server.rules.PersonalRuleEdit;
import de.lardtom.propwash.server.rules.PersonalRules;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record PersonalRulesC2S(PersonalRuleEdit.Op op, Map<String, Integer> values) implements CustomPacketPayload {
    public static final Type<PersonalRulesC2S> TYPE = new Type<>(Constants.id("personal_rules_edit"));
    public static final StreamCodec<FriendlyByteBuf, PersonalRulesC2S> CODEC = StreamCodec.of((buf, payload) -> {
        buf.writeEnum(payload.op);
        buf.writeVarInt(payload.values.size());
        for (Map.Entry<String, Integer> entry : payload.values.entrySet()) {
            buf.writeUtf(entry.getKey(), PersonalRules.MAX_NAME);
            buf.writeVarInt(entry.getValue());
        }
    }, buf -> {
        PersonalRuleEdit.Op op = buf.readEnum(PersonalRuleEdit.Op.class);
        int count = buf.readVarInt();
        if (count < 0 || count > PersonalRules.MAX_ENTRIES) {
            throw new IllegalArgumentException("Persönliche Regeln mit " + count + " Einträgen");
        }
        Map<String, Integer> values = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            values.put(buf.readUtf(PersonalRules.MAX_NAME), buf.readVarInt());
        }
        return new PersonalRulesC2S(op, values);
    });

    public PersonalRulesC2S {
        Objects.requireNonNull(op, "op");
        values = Map.copyOf(values == null ? Map.of() : values);
        if (values.size() > PersonalRules.MAX_ENTRIES) {
            throw new IllegalArgumentException("Persönliche Regeln mit " + values.size() + " Einträgen");
        }
    }

    @Override
    public Type<PersonalRulesC2S> type() {
        return TYPE;
    }
}
