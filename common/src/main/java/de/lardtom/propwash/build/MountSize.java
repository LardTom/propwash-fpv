package de.lardtom.propwash.build;

import de.lardtom.propwash.api.part.Mounts;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class MountSize {
    private MountSize() {
    }

    public static final class Motor {
        private static final Map<Identifier, Motor> INTERNED = new LinkedHashMap<>();

        public static final Motor M08 = of(Mounts.MOTOR_M08);
        public static final Motor M14 = of(Mounts.MOTOR_M14);
        public static final Motor M22 = of(Mounts.MOTOR_M22);
        public static final Motor M28 = of(Mounts.MOTOR_M28);

        private final Identifier id;

        private Motor(Identifier id) {
            this.id = id;
        }

        public static Motor of(Identifier id) {
            Objects.requireNonNull(id, "id");
            synchronized (INTERNED) {
                return INTERNED.computeIfAbsent(id, Motor::new);
            }
        }

        public static Motor[] values() {
            synchronized (INTERNED) {
                return new ArrayList<>(INTERNED.values()).toArray(new Motor[0]);
            }
        }

        public Identifier id() {
            return this.id;
        }

        public String key() {
            return PartIndex.keyOf(this.id);
        }

        public String name() {
            return this.id.getPath().toUpperCase(Locale.ROOT);
        }

        @Override
        public String toString() {
            return this.name();
        }
    }

    public static final class Stack {
        private static final Map<Identifier, Stack> INTERNED = new LinkedHashMap<>();

        public static final Stack WHOOP_AIO = of(Mounts.STACK_WHOOP_AIO);
        public static final Stack S20 = of(Mounts.STACK_S20);
        public static final Stack S30 = of(Mounts.STACK_S30);

        private final Identifier id;

        private Stack(Identifier id) {
            this.id = id;
        }

        public static Stack of(Identifier id) {
            Objects.requireNonNull(id, "id");
            synchronized (INTERNED) {
                return INTERNED.computeIfAbsent(id, Stack::new);
            }
        }

        public static Stack[] values() {
            synchronized (INTERNED) {
                return new ArrayList<>(INTERNED.values()).toArray(new Stack[0]);
            }
        }

        public Identifier id() {
            return this.id;
        }

        public String key() {
            return PartIndex.keyOf(this.id);
        }

        public String name() {
            return this.id.getPath().toUpperCase(Locale.ROOT);
        }

        @Override
        public String toString() {
            return this.name();
        }
    }

    static List<Motor> motors(Iterable<Identifier> ids) {
        List<Motor> out = new ArrayList<>();
        for (Identifier id : ids) {
            out.add(Motor.of(id));
        }
        return out;
    }

    static List<Stack> stacks(Iterable<Identifier> ids) {
        List<Stack> out = new ArrayList<>();
        for (Identifier id : ids) {
            out.add(Stack.of(id));
        }
        return out;
    }
}
