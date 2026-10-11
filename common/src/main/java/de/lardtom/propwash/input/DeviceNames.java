package de.lardtom.propwash.input;

import java.util.List;
import java.util.Locale;

public final class DeviceNames {
    public static final int OPENTX_VENDOR = 0x1209;
    public static final int OPENTX_PRODUCT = 0x4F54;
    public static final String FIRMWARE_BADGE = "EdgeTX/OpenTX";

    private static final List<String> FIRMWARE_PREFIXES = List.of("opentx ", "edgetx ");
    private static final List<String> FIRMWARE_WORDS = List.of("opentx", "edgetx");
    private static final String JOYSTICK = "joystick";

    private DeviceNames() {
    }

    public static boolean edgeTxOrOpenTx(String name, int vendor, int product) {
        if ((vendor & 0xFFFF) == OPENTX_VENDOR && (product & 0xFFFF) == OPENTX_PRODUCT) {
            return true;
        }
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String word : FIRMWARE_WORDS) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }

    public static String displayName(String raw) {
        return displayName(raw, -1, -1);
    }

    public static String displayName(String raw, int vendor, int product) {
        if (raw == null) {
            return "";
        }
        String name = raw.strip();
        boolean firmware = edgeTxOrOpenTx(name, vendor, product);
        boolean stripped = true;
        while (stripped) {
            stripped = false;
            String lower = name.toLowerCase(Locale.ROOT);
            if (FIRMWARE_WORDS.contains(lower)) {
                name = "";
                break;
            }
            for (String prefix : FIRMWARE_PREFIXES) {
                if (lower.startsWith(prefix)) {
                    name = name.substring(prefix.length()).strip();
                    stripped = true;
                    break;
                }
            }
        }
        if (firmware || ProfileDefaults.looksLikeRadio(raw)) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.equals(JOYSTICK)) {
                name = "";
            } else if (lower.endsWith(" " + JOYSTICK)) {
                name = name.substring(0, name.length() - JOYSTICK.length() - 1).strip();
            }
        }
        if (!name.isEmpty()) {
            return name;
        }
        return firmware ? FIRMWARE_BADGE : raw.strip();
    }
}
