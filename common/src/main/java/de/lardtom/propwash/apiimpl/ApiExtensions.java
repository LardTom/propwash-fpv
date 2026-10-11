package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.registry.CreativeEntries;
import de.lardtom.propwash.build.PartAvailability;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public final class ApiExtensions {
    public static final CreativeEntries CREATIVE = new Creative();

    private static final Map<Identifier, DronePreset> PRESETS = new LinkedHashMap<>();

    private ApiExtensions() {
    }

    public static synchronized DronePreset registerPreset(DronePreset preset) {
        Objects.requireNonNull(preset, "preset");
        if (Constants.MOD_ID.equals(preset.id().getNamespace())) {
            throw new IllegalArgumentException("Der Namensraum propwash gehört den eingebauten Presets: " + preset.id());
        }
        if (PRESETS.putIfAbsent(preset.id(), preset) != null) {
            throw new IllegalArgumentException("Preset doppelt angemeldet: " + preset.id());
        }
        if (preset.creativeListed()) {
            ExtensionTabs.INSTANCE.touch(preset.id().getNamespace());
        }
        return preset;
    }

    public static synchronized List<DronePreset> registeredPresets() {
        if (!PartAvailability.filtering()) {
            return List.copyOf(PRESETS.values());
        }
        List<DronePreset> active = new ArrayList<>();
        for (DronePreset preset : PRESETS.values()) {
            if (PartAvailability.active(preset.id()) && preset.data().build().allParts().stream().allMatch(PartAvailability::active)) {
                active.add(preset);
            }
        }
        return List.copyOf(active);
    }

    static final class Creative implements CreativeEntries {
        private final List<Supplier<? extends Collection<ItemStack>>> sources = new CopyOnWriteArrayList<>();

        Creative() {
        }

        @Override
        public void add(Supplier<? extends Collection<ItemStack>> entries) {
            this.sources.add(Objects.requireNonNull(entries, "entries"));
        }

        @Override
        public List<ItemStack> collect() {
            List<ItemStack> out = new ArrayList<>();
            for (Supplier<? extends Collection<ItemStack>> source : this.sources) {
                try {
                    Collection<ItemStack> stacks = source.get();
                    if (stacks != null) {
                        for (ItemStack stack : stacks) {
                            if (stack != null && !stack.isEmpty()) {
                                out.add(stack);
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    Constants.LOG.error("Kreativ-Einträge eines Addons konnten nicht erzeugt werden", e);
                }
            }
            return out;
        }
    }
}
