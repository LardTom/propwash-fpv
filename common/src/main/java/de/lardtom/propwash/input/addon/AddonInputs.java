package de.lardtom.propwash.input.addon;

import de.lardtom.propwash.api.input.AddonAxis;
import de.lardtom.propwash.api.input.AddonSwitch;
import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.apiimpl.Api17Registries;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class AddonInputs {
    private static volatile List<SwitchSpec> switches = List.of();
    private static volatile List<AxisSpec> axes = List.of();

    private AddonInputs() {
    }

    public static List<SwitchSpec> switches() {
        List<SwitchSpec> cached = switches;
        List<AddonSwitch> registered = Api17Registries.SWITCHES.switches();
        if (cached.size() != registered.size()) {
            List<SwitchSpec> specs = new ArrayList<>(registered.size());
            for (AddonSwitch handle : registered) {
                specs.add(handle.spec());
            }
            cached = List.copyOf(specs);
            switches = cached;
        }
        return cached;
    }

    public static List<AxisSpec> axes() {
        List<AxisSpec> cached = axes;
        List<AddonAxis> registered = Api17Registries.SWITCHES.axes();
        if (cached.size() != registered.size()) {
            List<AxisSpec> specs = new ArrayList<>(registered.size());
            for (AddonAxis handle : registered) {
                specs.add(handle.spec());
            }
            cached = List.copyOf(specs);
            axes = cached;
        }
        return cached;
    }

    public static boolean any() {
        return !switches().isEmpty() || !axes().isEmpty();
    }

    public static int switchIndex(@Nullable Identifier id) {
        List<SwitchSpec> specs = switches();
        for (int i = 0; i < specs.size(); i++) {
            if (specs.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    public static int axisIndex(@Nullable Identifier id) {
        List<AxisSpec> specs = axes();
        for (int i = 0; i < specs.size(); i++) {
            if (specs.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    public static @Nullable SwitchSpec switchSpec(@Nullable Identifier id) {
        int index = switchIndex(id);
        return index < 0 ? null : switches().get(index);
    }

    public static @Nullable AxisSpec axisSpec(@Nullable Identifier id) {
        int index = axisIndex(id);
        return index < 0 ? null : axes().get(index);
    }

    public static String key(Identifier id) {
        return id.toString();
    }
}
