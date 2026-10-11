package de.lardtom.propwash.join;

import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartAvailability;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.PartRef;
import de.lardtom.propwash.creative.CreativeSpec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class SessionItems {
    private SessionItems() {
    }

    public static boolean available(ItemStack stack) {
        return unavailable(stack).isEmpty();
    }

    public static List<String> unavailable(ItemStack stack) {
        if (!PartAvailability.filtering() || stack == null || stack.isEmpty()) {
            return List.of();
        }
        TreeSet<String> out = new TreeSet<>();
        Identifier item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!PartAvailability.active(item)) {
            out.add(item.toString());
        }
        PartRef ref = stack.get(CoreComponents.PART.get());
        if (ref != null) {
            addKey(out, ref.key());
        }
        DroneBuild build = stack.get(CoreComponents.DRONE_BUILD.get());
        if (build != null) {
            out.addAll(unavailable(build));
        }
        return List.copyOf(out);
    }

    public static List<String> unavailable(DroneBuild build) {
        if (!PartAvailability.filtering()) {
            return List.of();
        }
        TreeSet<String> out = new TreeSet<>();
        for (PartInfo part : build.parts()) {
            if (!PartAvailability.active(part.id())) {
                out.add(part.id().toString());
            }
        }
        CreativeSpec creative = build.creative();
        if (creative != null) {
            out.addAll(unavailableKeys(creative.base()));
        }
        return List.copyOf(out);
    }

    public static List<String> unavailableKeys(Map<PartKind, String> keys) {
        return unavailableKeys(keys.values());
    }

    public static List<String> unavailableKeys(Collection<String> keys) {
        if (!PartAvailability.filtering()) {
            return List.of();
        }
        TreeSet<String> out = new TreeSet<>();
        for (String key : keys) {
            addKey(out, key);
        }
        return List.copyOf(out);
    }

    public static List<String> unavailableIds(Collection<Identifier> ids) {
        if (!PartAvailability.filtering()) {
            return List.of();
        }
        TreeSet<String> out = new TreeSet<>();
        for (Identifier id : ids) {
            if (id != null && !PartAvailability.active(id)) {
                out.add(id.toString());
            }
        }
        return List.copyOf(out);
    }

    private static void addKey(TreeSet<String> out, String key) {
        Identifier id = PartIndex.idOfKey(key);
        if (id != null && !PartAvailability.active(id)) {
            out.add(id.toString());
        }
    }
}
