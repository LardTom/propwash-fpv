package de.lardtom.propwash.share;

import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.share.legacy.Pw2Tables;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class Pw3Params {
    public static final String FORMAT = "PW3";
    public static final String PREFIX = "PW3-";
    public static final String REVISION = "3.1";
    public static final String ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789_.-/";
    public static final String TEXT_ALPHABET = Pw2Tables.TEXT_ALPHABET;
    public static final String UNKNOWN_NAMESPACE = "pw3.unknown";
    public static final String RESERVED_PREFIX = "pw3.";
    public static final String SHORT_KEY_PREFIX = "pw3.short.";

    public static final int K_NAMESPACE_COUNT = 0;
    public static final int K_SHORT_ID = 1;
    public static final int K_PART = 3;
    public static final int K_ID_TEXT = 3;
    public static final int K_VERSION = 0;
    public static final int K_ACCESSORY_COUNT = 0;
    public static final int K_ACCESSORY_SLOT = 0;
    public static final int K_COLOR = Pw2Tables.K_COLOR;
    public static final int K_PAINT_SLOTS = Pw2Tables.K_PAINT_SLOTS;
    public static final int K_TUNE_COUNT = Pw2Tables.K_TUNE_COUNT;
    public static final int K_TUNE_GAP = Pw2Tables.K_TUNE_GAP;
    public static final int K_TUNE_LITERAL_COUNT = 0;
    public static final int K_VALUE = Pw2Tables.K_VALUE;
    public static final int K_TEXT = Pw2Tables.K_TEXT;
    public static final int K_OSD_VERSION = 0;
    public static final int K_OSD_BYTES = Pw2Tables.K_OSD_BYTES;
    public static final int K_EXTENSION_COUNT = 0;
    public static final int K_EXTENSION_LAYER = 0;
    public static final int K_EXTENSION_BITS = Pw2Tables.K_EXTENSION_BITS;
    public static final int K_CREATIVE_NAMESPACE = 0;
    public static final int K_CREATIVE_COUNT = 2;
    public static final int K_CREATIVE_GAP = 1;
    public static final int K_CREATIVE_LITERAL_COUNT = 0;
    public static final int K_CREATIVE_DECIMALS = 0;
    public static final int K_CREATIVE_VALUE = 4;

    public static final int CHECKSUM_BYTES = 2;
    public static final int TABLE_CHECKSUM_BITS = 10;
    public static final int MIN_FRAME_BYTES = 3;
    public static final int MAX_FRAME_BYTES = 2500;
    public static final int MAX_INPUT_CHARS = 4000;
    public static final int HARD_INPUT_LIMIT = 65536;
    public static final int MAX_NAMESPACES = 16;
    public static final int MAX_NAMESPACE_CHARS = 64;
    public static final int MAX_PATH_CHARS = 128;
    public static final int MAX_SLUG_CHARS = 64;
    public static final int MAX_LINK_CHARS = 200;
    public static final int MAX_VERSION_SUFFIX_CHARS = 32;
    public static final int MAX_ADDON_NAME_CHARS = 48;
    public static final int MAX_NAME_CHARS = 48;
    public static final int MAX_OSD_CHARS = 8192;
    public static final int MAX_OSD_INFLATED_BYTES = 16384;
    public static final int MAX_TUNE_ENTRIES = 256;
    public static final int MAX_TUNE_KEY_CHARS = 64;
    public static final int MAX_PAINT_SLOT_BITS = 64;
    public static final int MAX_ACCESSORIES = 255;
    public static final int MAX_EXTENSIONS = 32;
    public static final int FIRST_EXTENSION_LAYER = 5;
    public static final int CREATIVE_EXTENSION = 0;
    public static final int CREATIVE_BASE_BITS = 5;
    public static final int MAX_CREATIVE_VALUES = 96;
    public static final int MAX_CREATIVE_KEY_CHARS = 128;
    public static final int MAX_CREATIVE_DECIMALS = 15;
    public static final int MAX_CREATIVE_MANTISSA_BITS = 40;
    public static final long MAX_NUMBER = Integer.MAX_VALUE;

    public static final int LINK_NONE = 0;
    public static final int LINK_MODRINTH = 1;
    public static final int LINK_CURSEFORGE = 2;
    public static final int LINK_HTTPS = 3;

    public static final List<PartKind> KINDS = List.of(PartKind.FRAME, PartKind.STACK, PartKind.MOTOR, PartKind.PROP, PartKind.VIDEO,
            PartKind.BATTERY, PartKind.ACCESSORY);
    public static final List<AccessorySlot> ACCESSORY_SLOTS = List.of(AccessorySlot.TOP, AccessorySlot.BOTTOM, AccessorySlot.FRONT);
    public static final List<String> PAINT_SLOTS = List.of("frame", "tpu", "motors", "prop_fl", "prop_fr", "prop_rl", "prop_rr", "stack",
            "battery", "camera", "antenna", "accessories");
    public static final List<String> TUNE_KEYS = Pw2Tables.TUNE_KEYS;
    public static final List<String> CREATIVE_KEYS = List.of("frame.mass_g", "motor.mass_g", "prop.mass_g", "battery.mass_g", "stack.mass_g",
            "video.mass_g", "inertia.roll", "inertia.pitch", "inertia.yaw", "frame.wheelbase_mm", "arm.front_left.forward", "arm.front_left.right",
            "arm.front_left.up", "arm.front_right.forward", "arm.front_right.right", "arm.front_right.up", "arm.rear_left.forward",
            "arm.rear_left.right", "arm.rear_left.up", "arm.rear_right.forward", "arm.rear_right.right", "arm.rear_right.up", "prop.diameter_mm",
            "prop.pitch_in", "prop.blades", "prop.ct", "prop.cp", "motor.kv", "motor.resistance_mohm", "motor.max_current_a", "battery.cells",
            "battery.capacity_mah", "battery.resistance_mohm", "battery.c_rating", "stack.esc_current_a", "frame.frontal_area_cm2",
            "frame.drag_cd", "frame.side_area_cm2", "frame.top_area_cm2", "frame.ducts", "frame.impact_tolerance", "video.fov_deg",
            "camera.forward", "camera.right", "camera.up");
    public static final List<PartKind> CREATIVE_BASE_KINDS = List.of(PartKind.FRAME, PartKind.STACK, PartKind.MOTOR, PartKind.PROP, PartKind.BATTERY);
    public static final List<Integer> COLORS = Pw2Tables.COLORS;
    public static final Set<TuneGroup> TUNE_GROUPS = Collections.unmodifiableSet(EnumSet.of(TuneGroup.RATES, TuneGroup.PID, TuneGroup.FEEDFORWARD,
            TuneGroup.TPA, TuneGroup.FILTERS, TuneGroup.THROTTLE, TuneGroup.CAMERA));

    private Pw3Params() {
    }

    public static byte[] osdDictionary() {
        return Pw2Tables.OSD_DICTIONARY.clone();
    }

    public static int tuneIndex(String key) {
        return TUNE_KEYS.indexOf(key) + 1;
    }

    public static boolean sharedTuneKey(String key) {
        return TUNE_GROUPS.contains(TuneGroup.of(key));
    }

    public static int creativeIndex(String key) {
        return CREATIVE_KEYS.indexOf(key) + 1;
    }

    public static int paintBit(String slot) {
        return PAINT_SLOTS.indexOf(slot);
    }

    public static int accessoryIndex(AccessorySlot slot) {
        return ACCESSORY_SLOTS.indexOf(slot);
    }

    public static boolean validNamespace(String namespace) {
        return namespace != null && namespace.length() >= 1 && namespace.length() <= MAX_NAMESPACE_CHARS && onlyChars(namespace, false)
                && !namespace.equals(".") && !namespace.equals("..");
    }

    public static boolean writableNamespace(String namespace) {
        return validNamespace(namespace) && !namespace.startsWith(RESERVED_PREFIX) && !namespace.equals("pw2.unknown")
                && !namespace.startsWith("pw1.unknown.");
    }

    public static boolean validPath(String path) {
        return path != null && path.length() >= 1 && path.length() <= MAX_PATH_CHARS && onlyChars(path, true);
    }

    public static boolean validSlug(String slug) {
        return slug != null && slug.length() >= 1 && slug.length() <= MAX_SLUG_CHARS && onlyChars(slug, false);
    }

    static boolean onlyChars(String text, boolean slash) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ok = c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-' || slash && c == '/';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
