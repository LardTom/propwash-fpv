package de.lardtom.propwash.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.HoldMode;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public record AssistDetail(LapAssist reason, String subject, int value, int reference) {
    public static final int MAX_SUBJECT = 48;
    public static final String ALTITUDE = "altitude";
    public static final String POSITION = "position";
    public static final String SWITCH = "switch";
    public static final String KEY = "key";
    public static final String FAILSAFE = "failsafe";
    public static final String CLIENT = "client";
    public static final String CREATIVE_FC = "creative_fc";
    public static final String LATENCY = "latency";

    public static final Codec<AssistDetail> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("reason").forGetter(detail -> detail.reason().key()),
            Codec.STRING.optionalFieldOf("subject", "").forGetter(AssistDetail::subject),
            Codec.INT.optionalFieldOf("value", 0).forGetter(AssistDetail::value),
            Codec.INT.optionalFieldOf("reference", 0).forGetter(AssistDetail::reference)
    ).apply(i, (reason, subject, value, reference) -> new AssistDetail(LapAssist.byKey(reason), subject, value, reference)));

    public AssistDetail {
        Objects.requireNonNull(reason, "reason");
        subject = reason == LapAssist.OTHER ? cleanId(subject) : clean(subject);
    }

    public static String cleanId(String subject) {
        if (subject == null || subject.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(subject.length(), MAX_SUBJECT));
        String lower = subject.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length() && out.length() < MAX_SUBJECT; i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == ':' || c == '.' || c == '-' || c == '/') {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static String clean(String subject) {
        if (subject == null || subject.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(subject.length(), MAX_SUBJECT));
        String lower = subject.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length() && out.length() < MAX_SUBJECT; i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_') {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static AssistDetail rule(RuleSpec spec, int value) {
        return new AssistDetail(LapAssist.WORLD_PHYSICS, spec.name(), value, spec.defaultValue());
    }

    public static AssistDetail zone(int zoneId, RuleSpec spec, int value) {
        return new AssistDetail(LapAssist.ZONE_PHYSICS, spec.name(), value, zoneId);
    }

    public static AssistDetail clientPhysics() {
        return new AssistDetail(LapAssist.WORLD_PHYSICS, CLIENT, 0, 0);
    }

    public static AssistDetail hold(HoldMode mode) {
        return new AssistDetail(LapAssist.HOLD_MODE, mode == HoldMode.POSHOLD ? POSITION : ALTITUDE, 0, 0);
    }

    public static AssistDetail rescue(String trigger) {
        return new AssistDetail(LapAssist.RESCUE, trigger, 0, 0);
    }

    public static AssistDetail creative() {
        return new AssistDetail(LapAssist.CREATIVE_DRONE, CREATIVE_FC, 0, 0);
    }

    public static AssistDetail issue(String issue) {
        return new AssistDetail(LapAssist.PARTS_MISMATCH, issue, 0, 0);
    }

    public static AssistDetail addon(String assistId) {
        return new AssistDetail(LapAssist.OTHER, assistId, 0, 0);
    }

    public static AssistDetail goggles(int ownMs, int transmitterMs) {
        return new AssistDetail(LapAssist.GOGGLES_LATENCY, LATENCY, ownMs, transmitterMs);
    }

    public String key() {
        String key = this.reason.key() + "/" + this.subject;
        return this.reason == LapAssist.ZONE_PHYSICS ? key + "/" + this.reference : key;
    }

    public Optional<RuleSpec> rule() {
        return this.reason == LapAssist.WORLD_PHYSICS || this.reason == LapAssist.ZONE_PHYSICS ? WorldRuleSpecs.byName(this.subject)
                : Optional.empty();
    }
}
