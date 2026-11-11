package de.lardtom.propwash.drone.operator.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.drone.operator.OperatorChannel;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public final class OperatorTestInput {
    public static final String PROPERTY = "propwash.test.operatorInput";
    private static volatile @Nullable Cached cached;
    private static volatile @Nullable String reported;

    private OperatorTestInput() {
    }

    public record Input(short[] axes, int actions) {
        public Input {
            axes = axes.clone();
        }

        @Override
        public short[] axes() {
            return this.axes.clone();
        }

        public double axis(int index) {
            return index >= 0 && index < this.axes.length ? OperatorChannel.axis(this.axes[index]) : 0.0;
        }
    }

    private record Cached(String raw, @Nullable Input input) {
    }

    public static @Nullable Input current() {
        String raw = System.getProperty(PROPERTY);
        if (raw == null) {
            return null;
        }
        Cached last = cached;
        if (last != null && last.raw().equals(raw)) {
            return last.input();
        }
        Input input = parse(raw);
        cached = new Cached(raw, input);
        report(raw, input);
        return input;
    }

    public static @Nullable Input parse(String raw) {
        String[] parts = raw.split(",", -1);
        if (parts.length < OperatorChannel.AXES) {
            return null;
        }
        short[] axes = new short[OperatorChannel.AXES];
        for (int i = 0; i < OperatorChannel.AXES; i++) {
            double value;
            try {
                value = Double.parseDouble(parts[i].strip());
            } catch (NumberFormatException e) {
                return null;
            }
            if (!Double.isFinite(value)) {
                return null;
            }
            double low = i == OperatorChannel.AXES - 1 ? 0.0 : -1.0;
            axes[i] = OperatorChannel.quantize(Math.max(low, Math.min(1.0, value)));
        }
        int actions = 0;
        for (int i = OperatorChannel.AXES; i < parts.length; i++) {
            int number;
            try {
                number = Integer.parseInt(parts[i].strip());
            } catch (NumberFormatException e) {
                return null;
            }
            if (number < 1 || number > OperatorChannel.ACTIONS) {
                return null;
            }
            actions |= 1 << (number - 1);
        }
        return new Input(axes, actions);
    }

    private static void report(String raw, @Nullable Input input) {
        if (raw.equals(reported)) {
            return;
        }
        reported = raw;
        if (input == null) {
            Constants.LOG.warn("Propwash: Test-Eigenschaft {}=\"{}\" ist ungültig (erwartet Roll, Nick, Gier, Gas und danach Aktionen 1–4, "
                    + "durch Kommas getrennt) und wird ignoriert", PROPERTY, raw);
        } else {
            Constants.LOG.warn("Propwash: Test-Eigenschaft {} – Operator-Eingaben dieses Clients kommen statt von Funke oder Gamepad aus {} "
                    + "(nur für Tests)", PROPERTY, describe(input));
        }
    }

    static String describe(Input input) {
        StringBuilder actions = new StringBuilder();
        for (int number = 1; number <= OperatorChannel.ACTIONS; number++) {
            if (OperatorChannel.action(input.actions(), number)) {
                actions.append(actions.isEmpty() ? "" : ", ").append(number);
            }
        }
        return String.format(Locale.ROOT, "Roll %.2f, Nick %.2f, Gier %.2f, Gas %.2f, Aktionen [%s]", input.axis(0), input.axis(1), input.axis(2),
                input.axis(3), actions);
    }
}
