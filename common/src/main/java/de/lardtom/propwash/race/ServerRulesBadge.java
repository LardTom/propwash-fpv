package de.lardtom.propwash.race;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ServerRulesBadge {
    public static final String KEY = "propwash.race_rules.badge.";

    public record Line(RaceRules.Rule rule, int value, int reference) {
    }

    private ServerRulesBadge() {
    }

    public static List<Line> board(RaceStandard server) {
        List<Line> out = new ArrayList<>();
        for (RaceRules.Rule rule : server.differences()) {
            out.add(new Line(rule, server.value(rule), rule.defaultValue()));
        }
        return out;
    }

    public static List<Line> entry(@Nullable RaceStandard flown, RaceStandard current) {
        List<Line> out = new ArrayList<>();
        if (flown == null) {
            return out;
        }
        for (RaceRules.Rule rule : RaceRules.relevant()) {
            if (flown.value(rule) != current.value(rule)) {
                out.add(new Line(rule, flown.value(rule), current.value(rule)));
            }
        }
        return out;
    }
}
