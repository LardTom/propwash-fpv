package de.lardtom.propwash.guide.training;

import de.lardtom.propwash.trick.TrickType;

import java.util.List;
import java.util.Set;

public sealed interface Goal {
    String key();

    default boolean acceptsDisarm() {
        return false;
    }

    record Arm(double seconds) implements Goal {
        @Override
        public String key() {
            return "arm";
        }

        @Override
        public boolean acceptsDisarm() {
            return true;
        }
    }

    record Hover(int zone, double seconds) implements Goal {
        @Override
        public String key() {
            return "hover";
        }
    }

    record Heading(int zone, List<Integer> markers, double hold, double tolerance) implements Goal {
        public Heading {
            markers = List.copyOf(markers);
        }

        @Override
        public String key() {
            return "heading";
        }
    }

    record Rings(int from, int to, boolean strict) implements Goal {
        @Override
        public String key() {
            return "rings";
        }

        public int count() {
            return this.to - this.from;
        }
    }

    record Laps(int from, int to, int laps) implements Goal {
        @Override
        public String key() {
            return "laps";
        }

        public int count() {
            return this.to - this.from;
        }
    }

    record Land(int pad) implements Goal {
        @Override
        public String key() {
            return "land";
        }

        @Override
        public boolean acceptsDisarm() {
            return true;
        }
    }

    record Tricks(Set<TrickType> types, int count, int zone) implements Goal {
        public Tricks {
            types = Set.copyOf(types);
        }

        @Override
        public String key() {
            return "tricks";
        }

        public boolean accepts(TrickType type) {
            return this.types.isEmpty() || this.types.contains(type);
        }
    }

    record Combo(int tricks, int zone) implements Goal {
        @Override
        public String key() {
            return "combo";
        }
    }
}
