package de.lardtom.propwash.guard;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class ResourcePaths {
    public static final String DATA = "data/propwash/";
    public static final String ASSETS = "assets/propwash/";
    public static final String LANG = ASSETS + "lang/";
    public static final List<String> ASSET_FOLDERS = List.of("atlases", "blockstates", "equipment", "font", "items", "models", "particles",
            "post_effect", "shaders", "sounds", "texts", "textures", "waypoint_style");
    public static final List<String> DATA_FOLDERS = List.of("advancement", "banner_pattern", "block_transformer", "cat_sound_variant",
            "cat_variant", "chat_type", "chicken_sound_variant", "chicken_variant", "context_float_provider", "context_int_provider",
            "cow_sound_variant", "cow_variant", "damage_type", "decorated_pot_pattern", "dialog", "dimension", "dimension_type", "enchantment",
            "enchantment_provider", "frog_variant", "function", "instrument", "item_modifier", "jukebox_song", "loot_table", "painting_variant",
            "pig_sound_variant", "pig_variant", "predicate", "recipe", "structure", "sulfur_cube_archetype", "tags", "test_environment",
            "test_instance", "timeline", "trade_set", "trial_spawner", "trim_material", "trim_pattern", "villager_trade", "wolf_sound_variant",
            "wolf_variant", "world_clock", "worldgen", "zombie_nautilus_variant");
    private static final Pattern FOLDER = Pattern.compile("[a-z0-9_-][a-z0-9_.-]*");

    public enum Kind {
        IGNORED,
        ALLOWED,
        FINDING,
        INFO
    }

    private ResourcePaths() {
    }

    public static boolean relevant(String path) {
        if (path == null || path.endsWith("/")) {
            return false;
        }
        return path.startsWith(DATA) && path.length() > DATA.length() || path.startsWith(ASSETS) && path.length() > ASSETS.length()
                && !path.startsWith(LANG);
    }

    public static SortedSet<String> folders(String root, Set<String> own) {
        SortedSet<String> out = new TreeSet<>(root.equals(DATA) ? DATA_FOLDERS : root.equals(ASSETS) ? ASSET_FOLDERS : List.of());
        for (String path : own) {
            int slash = path.startsWith(root) ? path.indexOf('/', root.length()) : -1;
            if (slash > root.length()) {
                out.add(path.substring(root.length(), slash));
            }
        }
        out.removeIf(folder -> !FOLDER.matcher(folder).matches() || !relevant(root + folder + "/x"));
        return out;
    }

    public static GuardCategory category(String path) {
        return path.startsWith(DATA) ? GuardCategory.DATA : GuardCategory.ASSET;
    }

    public static Kind classify(String path, boolean modPack, Set<String> own) {
        if (!relevant(path)) {
            return Kind.IGNORED;
        }
        if (own.contains(path)) {
            return Kind.ALLOWED;
        }
        return modPack ? Kind.FINDING : Kind.INFO;
    }
}
