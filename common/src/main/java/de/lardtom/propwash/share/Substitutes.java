package de.lardtom.propwash.share;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Substitutes {
    private Substitutes() {
    }

    public enum Method {
        NOT_NEEDED,
        FALLBACK,
        PRESET,
        NONE
    }

    public record Swap(String position, Identifier from, Optional<Identifier> to) {
        public Swap {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(from, "from");
            to = Objects.requireNonNullElse(to, Optional.empty());
        }
    }

    public record Result(Method method, Optional<BuildSpec> build, List<Swap> swaps, Optional<Integer> preset) {
        public Result {
            Objects.requireNonNull(method, "method");
            build = Objects.requireNonNullElse(build, Optional.empty());
            swaps = List.copyOf(Objects.requireNonNullElse(swaps, List.of()));
            preset = Objects.requireNonNullElse(preset, Optional.empty());
        }

        public boolean found() {
            return this.build.isPresent();
        }
    }

    public interface Usable {
        boolean usable(Identifier id, PartKind kind);
    }

    public static Result find(BuildSpec build, ShareTables tables) {
        List<BuildSpec> presets = new ArrayList<>();
        for (DronePreset preset : PropwashApi.builds().presets()) {
            if (preset.id().getNamespace().equals(ShareTables.PROPWASH)) {
                presets.add(preset.data().build());
            }
        }
        Usable usable = (id, kind) -> {
            ShareTables.PartLookup lookup = tables.parts();
            return !Pw3Codec.unknownMarker(id) && lookup.kind(id).filter(k -> k == kind).isPresent() && lookup.active(id);
        };
        return find(build, usable, tables::fallback, spec -> PropwashApi.builds().check(spec).buildable(), presets);
    }

    public static Result find(BuildSpec build, Usable usable, Function<Identifier, Optional<Identifier>> fallback, Predicate<BuildSpec> check,
                              List<BuildSpec> presets) {
        List<String> positions = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            PartKind kind = Pw3Params.KINDS.get(i);
            if (!usable.usable(build.part(kind), kind)) {
                positions.add(kind.key());
            }
        }
        List<AccessorySlot> accessories = new ArrayList<>();
        BuildSpec bare = build;
        for (AccessorySlot slot : Pw3Params.ACCESSORY_SLOTS) {
            Optional<Identifier> id = build.accessory(slot);
            if (id.isPresent() && !usable.usable(id.get(), PartKind.ACCESSORY)) {
                accessories.add(slot);
                bare = bare.withoutAccessory(slot);
            }
        }
        if (positions.isEmpty() && accessories.isEmpty()) {
            return new Result(Method.NOT_NEEDED, Optional.of(build), List.of(), Optional.empty());
        }
        BuildSpec candidate = bare;
        List<Swap> swaps = new ArrayList<>();
        boolean complete = true;
        for (String position : positions) {
            Identifier from = at(build, position);
            Optional<Identifier> to = safe(fallback, from).filter(id -> usable.usable(id, kindOf(position)));
            if (to.isEmpty()) {
                complete = false;
                break;
            }
            candidate = with(candidate, position, to);
            swaps.add(new Swap(position, from, to));
        }
        if (complete && safeCheck(check, candidate)) {
            BuildSpec result = accessories(build, candidate, accessories, usable, fallback, check, swaps);
            return new Result(Method.FALLBACK, Optional.of(result), swaps, Optional.empty());
        }
        for (int p = 0; p < presets.size(); p++) {
            BuildSpec preset = presets.get(p);
            BuildSpec next = bare;
            List<Swap> presetSwaps = new ArrayList<>();
            boolean usableAll = true;
            for (String position : positions) {
                Identifier from = at(build, position);
                Identifier to = at(preset, position);
                if (!usable.usable(to, kindOf(position))) {
                    usableAll = false;
                    break;
                }
                next = with(next, position, Optional.of(to));
                presetSwaps.add(new Swap(position, from, Optional.of(to)));
            }
            if (usableAll && safeCheck(check, next)) {
                BuildSpec result = accessories(build, next, accessories, usable, fallback, check, presetSwaps);
                return new Result(Method.PRESET, Optional.of(result), presetSwaps, Optional.of(p));
            }
        }
        return new Result(Method.NONE, Optional.empty(), List.of(), Optional.empty());
    }

    private static BuildSpec accessories(BuildSpec build, BuildSpec base, List<AccessorySlot> slots, Usable usable,
                                         Function<Identifier, Optional<Identifier>> fallback, Predicate<BuildSpec> check, List<Swap> swaps) {
        BuildSpec out = base;
        for (AccessorySlot slot : slots) {
            Identifier from = build.accessory(slot).orElseThrow();
            Optional<Identifier> to = safe(fallback, from).filter(id -> usable.usable(id, PartKind.ACCESSORY));
            if (to.isPresent()) {
                BuildSpec with = out.withAccessory(slot, to.get());
                if (safeCheck(check, with)) {
                    out = with;
                } else {
                    to = Optional.empty();
                }
            }
            swaps.add(new Swap("accessory:" + slot.key(), from, to));
        }
        return out;
    }

    private static Optional<Identifier> safe(Function<Identifier, Optional<Identifier>> fallback, Identifier id) {
        try {
            return Objects.requireNonNullElse(fallback.apply(id), Optional.empty());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static boolean safeCheck(Predicate<BuildSpec> check, BuildSpec spec) {
        try {
            return check.test(spec);
        } catch (RuntimeException e) {
            return false;
        }
    }

    static PartKind kindOf(String position) {
        return position.startsWith("accessory:") ? PartKind.ACCESSORY : PartKind.byKey(position).orElseThrow();
    }

    static AccessorySlot slotOf(String position) {
        return AccessorySlot.byKey(position.substring("accessory:".length())).orElseThrow();
    }

    static Identifier at(BuildSpec build, String position) {
        return position.startsWith("accessory:") ? build.accessory(slotOf(position)).orElseThrow() : build.part(kindOf(position));
    }

    static BuildSpec with(BuildSpec build, String position, Optional<Identifier> id) {
        if (position.startsWith("accessory:")) {
            return id.map(value -> build.withAccessory(slotOf(position), value)).orElseGet(() -> build.withoutAccessory(slotOf(position)));
        }
        return build.withPart(kindOf(position), id.orElseThrow());
    }
}
