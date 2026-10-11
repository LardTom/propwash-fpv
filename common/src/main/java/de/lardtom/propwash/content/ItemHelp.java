package de.lardtom.propwash.content;

import de.lardtom.propwash.core.BoundDrone;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class ItemHelp {
    public static final String PREFIX = "propwash.help.";
    public static final String SHIFT_KEY = PREFIX + "shift";
    public static final String SECTION_USE = PREFIX + "section.use";
    public static final String SECTION_FITS = PREFIX + "section.fits";
    public static final String SECTION_SPECS = PREFIX + "section.specs";
    public static final int MAX_LINES = 8;

    private ItemHelp() {
    }

    public static void append(String id, Consumer<Component> out, List<Component> status, Consumer<Consumer<Component>> fits,
                              Consumer<Consumer<Component>> specs) {
        builder(id).status(status).fits(fits).specs(specs).appendTo(out);
    }

    public static Builder builder(String id) {
        return new Builder(PREFIX + id + ".summary").what(PREFIX + id).use(PREFIX + id);
    }

    public static Builder summary(String summaryKey, Object... args) {
        return new Builder(summaryKey, args);
    }

    public static List<String> numbered(String prefix, String section) {
        List<String> keys = new ArrayList<>();
        Language language = Language.getInstance();
        for (int i = 1; i <= MAX_LINES; i++) {
            String key = prefix + "." + section + "." + i;
            if (!language.has(key)) {
                break;
            }
            keys.add(key);
        }
        return keys;
    }

    public static Component joined(List<? extends Component> parts) {
        MutableComponent joined = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                joined.append(", ");
            }
            joined.append(parts.get(i));
        }
        return joined;
    }

    public static Component boundStatus(ItemStack stack) {
        Optional<BoundDrone> bound = ContentIds.boundDrone(stack);
        if (bound.isEmpty()) {
            return Component.translatable(PREFIX + "status.unbound").withStyle(ChatFormatting.DARK_GRAY);
        }
        BoundDrone drone = bound.get();
        MutableComponent name = drone.displayName().copy().withStyle(ChatFormatting.WHITE);
        if (!drone.name().isEmpty()) {
            name.append(Component.literal(" (").append(PartTooltips.frameShortName(drone.frame())).append(")").withStyle(ChatFormatting.GRAY));
        }
        return Component.translatable(PREFIX + "status.bound", name).withStyle(ChatFormatting.AQUA);
    }

    public static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GRAY);
    }

    public static final class Builder {
        private final String summaryKey;
        private final Object[] summaryArgs;
        private final List<Component> status = new ArrayList<>();
        private final List<String> whatPrefixes = new ArrayList<>();
        private final List<Component> extraWhat = new ArrayList<>();
        private final List<String> usePrefixes = new ArrayList<>();
        private final List<Component> useLines = new ArrayList<>();
        private final Map<String, Object[]> args = new HashMap<>();
        private Consumer<Consumer<Component>> fits = out -> {
        };
        private Consumer<Consumer<Component>> specs = out -> {
        };
        private String fitsHeader = SECTION_FITS;

        private Builder(String summaryKey, Object... summaryArgs) {
            this.summaryKey = summaryKey;
            this.summaryArgs = summaryArgs;
        }

        public Builder status(Component line) {
            this.status.add(line);
            return this;
        }

        public Builder status(List<Component> lines) {
            this.status.addAll(lines);
            return this;
        }

        public Builder what(String prefix) {
            this.whatPrefixes.add(prefix);
            return this;
        }

        public Builder clearWhat() {
            this.whatPrefixes.clear();
            return this;
        }

        public Builder whatLine(Component line) {
            this.extraWhat.add(line);
            return this;
        }

        public Builder use(String prefix) {
            this.usePrefixes.add(prefix);
            return this;
        }

        public Builder clearUse() {
            this.usePrefixes.clear();
            return this;
        }

        public Builder useLine(Component line) {
            this.useLines.add(line);
            return this;
        }

        public Builder args(String key, Object... values) {
            this.args.put(key, values);
            return this;
        }

        public Builder fits(Consumer<Consumer<Component>> fits) {
            this.fits = fits;
            return this;
        }

        public Builder fitsHeader(String headerKey) {
            this.fitsHeader = headerKey;
            return this;
        }

        public Builder specs(Consumer<Consumer<Component>> specs) {
            this.specs = specs;
            return this;
        }

        public void appendTo(Consumer<Component> out) {
            out.accept(Component.translatable(this.summaryKey, this.summaryArgs).withStyle(ChatFormatting.GRAY));
            for (Component line : this.status) {
                out.accept(line);
            }
            if (!TooltipShift.down()) {
                out.accept(Component.translatable(SHIFT_KEY).withStyle(ChatFormatting.DARK_GRAY));
                return;
            }
            List<Component> what = new ArrayList<>();
            for (String prefix : this.whatPrefixes) {
                for (String key : numbered(prefix, "what")) {
                    what.add(this.text(key));
                }
            }
            what.addAll(this.extraWhat);
            List<Component> use = new ArrayList<>();
            for (String prefix : this.usePrefixes) {
                for (String key : numbered(prefix, "use")) {
                    use.add(this.text(key));
                }
            }
            use.addAll(this.useLines);
            List<Component> fitsLines = new ArrayList<>();
            this.fits.accept(fitsLines::add);
            List<Component> specLines = new ArrayList<>();
            this.specs.accept(specLines::add);
            int budget = TooltipShift.lineBudget() - 1 - this.status.size();
            int whatSize = what.isEmpty() ? 0 : what.size() + 1;
            int useSize = sectionSize(use);
            int fitsSize = sectionSize(fitsLines);
            int specSize = sectionSize(specLines);
            if (whatSize + useSize + fitsSize + specSize > budget) {
                whatSize = 0;
                what.clear();
            }
            if (useSize + fitsSize + specSize > budget) {
                fitsLines.clear();
                fitsSize = 0;
            }
            if (useSize + fitsSize + specSize > budget) {
                use.clear();
            }
            if (!what.isEmpty()) {
                out.accept(Component.empty());
                what.forEach(out);
            }
            section(out, SECTION_USE, use);
            section(out, this.fitsHeader, fitsLines);
            section(out, SECTION_SPECS, specLines);
        }

        private static int sectionSize(List<Component> lines) {
            return lines.isEmpty() ? 0 : lines.size() + 2;
        }

        private Component text(String key) {
            Object[] values = this.args.get(key);
            return Component.translatable(key, values == null ? new Object[0] : values).withStyle(ChatFormatting.GRAY);
        }

        private static void section(Consumer<Component> out, String header, List<Component> lines) {
            if (lines.isEmpty()) {
                return;
            }
            out.accept(Component.empty());
            out.accept(Component.translatable(header).withStyle(ChatFormatting.GOLD));
            for (Component line : lines) {
                out.accept(line);
            }
        }
    }
}
