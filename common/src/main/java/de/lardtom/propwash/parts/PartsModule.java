package de.lardtom.propwash.parts;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.part.Mounts;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.RecycleYield;
import de.lardtom.propwash.apiimpl.ExtensionTabs;
import de.lardtom.propwash.apiimpl.LootInjectionsImpl;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

public final class PartsModule {
    private PartsModule() {
    }

    public static void init() {
        PartIndex.ensureBuiltins();
    }

    public static void freeze() {
        if (!PartIndex.freeze()) {
            return;
        }
        AddonItems.INSTANCE.close();
        ExtensionTabs.INSTANCE.close();
        LootInjectionsImpl.INSTANCE.close();
        for (String warning : crossCheck(id -> BuiltInRegistries.ITEM.containsKey(id))) {
            Constants.LOG.warn("Propwash-Bauteile: {}", warning);
        }
        Constants.LOG.info("Propwash-Bauteile eingefroren: {}", summary());
        try {
            PropwashEvents.REGISTRY_FROZEN.fire(listener -> listener.onRegistryFrozen(PartRegistryImpl.INSTANCE));
        } catch (RuntimeException e) {
            Constants.LOG.error("Ein Listener von REGISTRY_FROZEN ist fehlgeschlagen", e);
        }
    }

    public static String summary() {
        List<String> kinds = new ArrayList<>();
        for (PartKind kind : PartKind.values()) {
            kinds.add(PartIndex.all(kind).size() + " " + kind.key());
        }
        Map<String, Integer> addons = new TreeMap<>();
        for (PartInfo part : PartIndex.all()) {
            if (!part.builtin()) {
                addons.merge(part.id().getNamespace(), 1, Integer::sum);
            }
        }
        int chemistries = 0;
        for (BatteryType.Chemistry chemistry : PartIndex.chemistries()) {
            if (!PartIndex.isBuiltinChemistry(chemistry.id())) {
                chemistries++;
            }
        }
        String text = String.join(", ", kinds) + ", " + PartIndex.chemistries().size() + " chemistry";
        if (addons.isEmpty() && chemistries == 0) {
            return text + " (nur eingebaute Teile)";
        }
        String chemistryText = chemistries == 1 ? " Zellchemie" : " Zellchemien";
        return text + "; aus Addons: " + addons + (chemistries > 0 ? ", " + chemistries + chemistryText : "") + "; Fingerabdruck "
                + PartIndex.fingerprint().substring(0, 12);
    }

    public static List<String> crossCheck(Predicate<Identifier> itemExists) {
        List<String> warnings = new ArrayList<>();
        for (PartInfo part : PartIndex.all()) {
            if (part.builtin()) {
                continue;
            }
            Identifier id = part.id();
            switch (part) {
                case MotorType motor -> {
                    if (none(FrameType.values(), frame -> frame.motorMounts.contains(motor.mount))) {
                        warnings.add(id + ": kein Rahmen hat die Motorhalterung " + motor.mount.id());
                    }
                    Identifier propMount = motor.definition().propMount();
                    if (none(PropType.values(), prop -> Mounts.propFits(propMount, prop.definition().mount()))) {
                        warnings.add(id + ": kein Propeller passt auf die Prop-Aufnahme " + propMount);
                    }
                }
                case StackType stack -> {
                    if (none(FrameType.values(), stack::fits)) {
                        warnings.add(id + ": kein Rahmen hat die Stack-Halterung " + stack.mount.id());
                    }
                }
                case PropType prop -> {
                    if (none(MotorType.values(), motor -> Mounts.propFits(motor.definition().propMount(), prop.definition().mount()))) {
                        warnings.add(id + ": kein Motor hat die Prop-Aufnahme " + prop.definition().mount());
                    }
                    if (none(FrameType.values(), frame -> frame.maxPropMm >= prop.diameterMm && frame.minPropMm <= prop.diameterMm)) {
                        warnings.add(id + ": kein Rahmen erlaubt " + prop.diameterMm + " mm Propeller");
                    }
                }
                case FrameType frame -> {
                    for (var mount : frame.motorMounts) {
                        if (none(MotorType.values(), motor -> motor.mount == mount)) {
                            warnings.add(id + ": kein Motor passt auf die Halterung " + mount.id());
                        }
                    }
                    for (var mount : frame.stackMounts) {
                        if (none(StackType.values(), stack -> stack.mount == mount)) {
                            warnings.add(id + ": kein Stack passt in die Halterung " + mount.id());
                        }
                    }
                }
                default -> {
                }
            }
            Optional<RecycleYield> recycle = part.definition().basics().recycle();
            if (recycle.isPresent() && !recycle.get().none() && !itemExists.test(recycle.get().item())) {
                warnings.add(id + ": Recycling-Item " + recycle.get().item() + " gibt es nicht");
            }
            if (!itemExists.test(id)) {
                warnings.add(id + ": hat kein Item und erscheint nicht in der Werkbank (PropwashApi.partItems().registerItems(\""
                        + id.getNamespace() + "\"))");
            }
        }
        return warnings;
    }

    private static <T> boolean none(T[] values, Predicate<T> test) {
        for (T value : values) {
            if (test.test(value)) {
                return false;
            }
        }
        return true;
    }
}
