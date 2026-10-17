package de.lardtom.propwash.race;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AssistReasons(int mask, List<AssistDetail> details) {
    public static final int MAX_DETAILS = 16;
    public static final AssistReasons NONE = new AssistReasons(LapAssist.NONE, List.of());

    public AssistReasons {
        mask = mask & LapAssist.ALL;
        details = normalize(mask, details == null ? List.of() : details);
    }

    public static AssistReasons of(int mask) {
        return mask == LapAssist.NONE ? NONE : new AssistReasons(mask, List.of());
    }

    public static AssistReasons of(int mask, List<AssistDetail> details) {
        return new AssistReasons(mask, details);
    }

    public static AssistReasons of(List<AssistDetail> details) {
        int mask = LapAssist.NONE;
        for (AssistDetail detail : details) {
            mask |= detail.reason().bit();
        }
        return new AssistReasons(mask, details);
    }

    private static List<AssistDetail> normalize(int mask, List<AssistDetail> details) {
        if (details.isEmpty()) {
            return List.of();
        }
        Map<String, AssistDetail> unique = new LinkedHashMap<>();
        for (AssistDetail detail : details) {
            if (detail != null && detail.reason().in(mask)) {
                unique.put(detail.key(), detail);
            }
        }
        List<AssistDetail> out = new ArrayList<>(unique.values());
        out.sort(Comparator.comparingInt(detail -> detail.reason().ordinal()));
        return List.copyOf(out.size() > MAX_DETAILS ? out.subList(0, MAX_DETAILS) : out);
    }

    public boolean none() {
        return this.mask == LapAssist.NONE;
    }

    public boolean any() {
        return this.mask != LapAssist.NONE;
    }

    public boolean has(LapAssist reason) {
        return reason.in(this.mask);
    }

    public List<LapAssist> list() {
        return LapAssist.list(this.mask);
    }

    public List<AssistDetail> details(LapAssist reason) {
        List<AssistDetail> out = new ArrayList<>();
        for (AssistDetail detail : this.details) {
            if (detail.reason() == reason) {
                out.add(detail);
            }
        }
        return out;
    }

    public AssistReasons plus(AssistReasons other) {
        if (other.none()) {
            return this;
        }
        if (this.none()) {
            return other;
        }
        List<AssistDetail> merged = new ArrayList<>(this.details);
        merged.addAll(other.details);
        return new AssistReasons(this.mask | other.mask, merged);
    }

    public AssistReasons only(int keep) {
        int kept = this.mask & keep;
        if (kept == this.mask) {
            return this;
        }
        return kept == LapAssist.NONE ? NONE : new AssistReasons(kept, this.details);
    }

    public AssistReasons without(int drop) {
        return this.only(~drop);
    }

    public AssistReasons orOther() {
        return this.none() ? of(LapAssist.OTHER.bit()) : this;
    }
}
