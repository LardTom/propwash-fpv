package de.lardtom.propwash.server;

import de.lardtom.propwash.server.rules.RulePreset;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRulesPayloadTest {
    static List<WorldRulesS2C.Entry> defaults() {
        List<WorldRulesS2C.Entry> entries = new ArrayList<>();
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            entries.add(new WorldRulesS2C.Entry(Identifier.parse(spec.id()), spec.serialize(spec.defaultValue())));
        }
        return entries;
    }

    @Test
    void rulesTravelToTheClient() {
        WorldRulesS2C payload = new WorldRulesS2C(true, defaults());
        ByteBuf buffer = Unpooled.buffer();
        WorldRulesS2C.CODEC.encode(buffer, payload);
        WorldRulesS2C decoded = WorldRulesS2C.CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes());
        assertEquals(payload, decoded);
        assertTrue(decoded.editable());
        assertEquals(WorldRuleSpecs.all().size(), decoded.entries().size());
    }

    @Test
    void editsTravelToTheServerWithinTheLimit() {
        WorldRuleEditC2S payload = new WorldRuleEditC2S(List.of(new WorldRulesS2C.Entry(Identifier.parse("propwash:wind"), "3")));
        ByteBuf buffer = Unpooled.buffer();
        WorldRuleEditC2S.CODEC.encode(buffer, payload);
        assertEquals(payload, WorldRuleEditC2S.CODEC.decode(buffer));
        List<WorldRulesS2C.Entry> tooMany = new ArrayList<>();
        for (int i = 0; i <= WorldRuleEditC2S.MAX_ENTRIES; i++) {
            tooMany.add(new WorldRulesS2C.Entry(Identifier.parse("propwash:rule_" + i), "1"));
        }
        ByteBuf big = Unpooled.buffer();
        assertThrows(RuntimeException.class, () -> WorldRuleEditC2S.CODEC.encode(big, new WorldRuleEditC2S(tooMany)));
    }

    @Test
    void presetsTravelToTheServerByName() {
        for (RulePreset preset : RulePreset.values()) {
            WorldRulePresetC2S payload = new WorldRulePresetC2S(preset);
            ByteBuf buffer = Unpooled.buffer();
            WorldRulePresetC2S.CODEC.encode(buffer, payload);
            WorldRulePresetC2S decoded = WorldRulePresetC2S.CODEC.decode(buffer);
            assertEquals(0, buffer.readableBytes());
            assertEquals(preset.id(), decoded.preset());
        }
        ByteBuf big = Unpooled.buffer();
        assertThrows(RuntimeException.class, () -> WorldRulePresetC2S.CODEC.encode(big,
                new WorldRulePresetC2S("x".repeat(RulePreset.MAX_ID_LENGTH + 1))));
    }

    @Test
    void textValuesMatchTheGameruleCommand() {
        for (RuleSpec spec : WorldRuleSpecs.all()) {
            String text = spec.serialize(spec.defaultValue());
            assertEquals(spec.defaultValue(), spec.parse(text), spec.name());
            assertTrue(text.length() <= WorldRulesS2C.Entry.MAX_VALUE_LENGTH);
        }
    }
}
