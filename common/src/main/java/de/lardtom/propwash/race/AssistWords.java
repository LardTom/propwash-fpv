package de.lardtom.propwash.race;

import de.lardtom.propwash.server.WorldRuleText;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.zone.ZoneNames;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AssistWords {
    public static final String SEPARATOR = "propwash.race.assist.separator";
    public static final String LIST_SEPARATOR = ", ";
    public static final String NOW = ".now";
    public static final String STANDARD_VALUES = "propwash.race_rules.assist.world_values";

    private AssistWords() {
    }

    public static MutableComponent describe(AssistReasons reasons) {
        MutableComponent out = Component.empty();
        List<MutableComponent> lines = lines(reasons);
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out.append(Component.translatable(SEPARATOR));
            }
            out.append(lines.get(i));
        }
        return out;
    }

    public static List<MutableComponent> lines(AssistReasons reasons) {
        List<MutableComponent> out = new ArrayList<>();
        for (LapAssist reason : reasons.list()) {
            out.add(reason(reason, reasons.details(reason)));
        }
        return out;
    }

    public static MutableComponent reason(LapAssist reason, List<AssistDetail> details) {
        return reason(reason, details, false);
    }

    public static MutableComponent reason(LapAssist reason, List<AssistDetail> details, boolean now) {
        String key = reason.translationKey();
        String tense = now ? NOW : "";
        return switch (reason) {
            case HOLD_MODE -> {
                if (has(details, AssistDetail.POSITION)) {
                    yield Component.translatable(key + "." + AssistDetail.POSITION + tense);
                }
                yield has(details, AssistDetail.ALTITUDE) ? Component.translatable(key + "." + AssistDetail.ALTITUDE + tense)
                        : Component.translatable(key + tense);
            }
            case RESCUE -> {
                for (String trigger : List.of(AssistDetail.FAILSAFE, AssistDetail.SWITCH, AssistDetail.KEY)) {
                    if (has(details, trigger)) {
                        yield Component.translatable(key + "." + trigger + tense);
                    }
                }
                yield Component.translatable(key + tense);
            }
            case WORLD_PHYSICS -> {
                MutableComponent rules = Component.empty();
                int count = 0;
                for (AssistDetail detail : details) {
                    Optional<RaceRules.Rule> known = detail.raceRule();
                    if (known.isPresent()) {
                        if (count++ > 0) {
                            rules.append(LIST_SEPARATOR);
                        }
                        rules.append(rule(known.get(), detail.value(), detail.reference()));
                    }
                }
                if (count > 0) {
                    yield Component.translatable(STANDARD_VALUES, rules);
                }
                yield has(details, AssistDetail.CLIENT) ? Component.translatable(key + "." + AssistDetail.CLIENT) : Component.translatable(key);
            }
            case ZONE_PHYSICS -> {
                Map<Integer, MutableComponent> zones = new LinkedHashMap<>();
                RaceStandard standard = RaceStandards.current();
                for (AssistDetail detail : details) {
                    Optional<RaceRules.Rule> known = detail.raceRule();
                    if (known.isPresent()) {
                        MutableComponent rules = zones.get(detail.reference());
                        if (rules == null) {
                            rules = Component.empty();
                            zones.put(detail.reference(), rules);
                        } else {
                            rules.append(LIST_SEPARATOR);
                        }
                        RuleSpec spec = known.get().spec();
                        rules.append(rule(known.get(), detail.value(), spec != null ? WorldRules.current().get(spec) : standard.value(known.get())));
                    }
                }
                if (zones.isEmpty()) {
                    yield Component.translatable(key);
                }
                if (zones.size() == 1) {
                    Map.Entry<Integer, MutableComponent> only = zones.entrySet().iterator().next();
                    yield Component.translatable(key + ".values", zoneName(only.getKey()), only.getValue());
                }
                MutableComponent out = Component.empty();
                int count = 0;
                for (Map.Entry<Integer, MutableComponent> zone : zones.entrySet()) {
                    if (count++ > 0) {
                        out.append(Component.translatable(SEPARATOR));
                    }
                    out.append(Component.translatable(key + ".values", zoneName(zone.getKey()), zone.getValue()));
                }
                yield out;
            }
            case PERSONAL_RULES -> {
                MutableComponent rules = Component.empty();
                int count = 0;
                for (AssistDetail detail : details) {
                    Optional<RaceRules.Rule> known = detail.raceRule();
                    if (known.isPresent()) {
                        if (count++ > 0) {
                            rules.append(LIST_SEPARATOR);
                        }
                        rules.append(rule(known.get(), detail.value(), detail.reference()));
                    }
                }
                yield count > 0 ? Component.translatable(key + ".values", rules) : Component.translatable(key);
            }
            case PARTS_MISMATCH -> {
                MutableComponent issues = Component.empty();
                int count = 0;
                for (AssistDetail detail : details) {
                    if (!detail.subject().isEmpty()) {
                        if (count++ > 0) {
                            issues.append(LIST_SEPARATOR);
                        }
                        issues.append(Component.translatable("propwash.build.issue." + detail.subject()));
                    }
                }
                yield count > 0 ? Component.translatable(key + ".issues", issues) : Component.translatable(key);
            }
            case SCALE -> {
                for (AssistDetail detail : details) {
                    if (AssistDetail.SCALE_PERCENT.equals(detail.subject())) {
                        yield Component.translatable(key + "." + AssistDetail.SCALE_PERCENT + tense, detail.value());
                    }
                }
                yield Component.translatable(key + tense);
            }
            case GOGGLES_LATENCY -> {
                for (AssistDetail detail : details) {
                    if (AssistDetail.LATENCY.equals(detail.subject())) {
                        yield Component.translatable(key + ".ms", detail.value(), detail.reference());
                    }
                }
                yield Component.translatable(key);
            }
            case OTHER -> {
                MutableComponent ids = Component.empty();
                int count = 0;
                for (AssistDetail detail : details) {
                    if (!detail.subject().isEmpty()) {
                        if (count++ > 0) {
                            ids.append(LIST_SEPARATOR);
                        }
                        ids.append(Component.literal(detail.subject()));
                    }
                }
                yield count > 0 ? Component.translatable(key + ".addon" + tense, ids) : Component.translatable(key);
            }
            default -> Component.translatable(key);
        };
    }

    public static Component zoneName(int id) {
        return ZoneNames.name(id).<Component>map(Component::literal).orElseGet(() -> Component.translatable(LapAssist.ZONE_PHYSICS.translationKey()
                + ".unknown", id));
    }

    public static MutableComponent rule(RuleSpec spec, int value) {
        return Component.translatable("propwash.race.assist.rule_value", Component.translatable(spec.translationKey()),
                WorldRuleText.value(spec, value), WorldRuleText.value(spec, spec.defaultValue()));
    }

    public static MutableComponent rule(RaceRules.Rule rule, int value, int standard) {
        return Component.translatable("propwash.race.assist.rule_value", name(rule), value(rule, value), value(rule, standard));
    }

    public static MutableComponent name(RaceRules.Rule rule) {
        if (rule.spec() != null) {
            return Component.translatable(rule.spec().translationKey());
        }
        Identifier id = Identifier.tryParse(rule.id());
        return id == null ? Component.literal(rule.id()) : WorldRuleText.name(id).copy();
    }

    public static Component value(RaceRules.Rule rule, int value) {
        if (rule.spec() != null) {
            return WorldRuleText.value(rule.spec(), value);
        }
        Identifier id = Identifier.tryParse(rule.id());
        return id == null ? Component.literal(RaceRules.format(rule, value)) : WorldRuleText.value(id, RaceRules.format(rule, value));
    }

    private static boolean has(List<AssistDetail> details, String subject) {
        for (AssistDetail detail : details) {
            if (detail.subject().equals(subject)) {
                return true;
            }
        }
        return false;
    }
}
