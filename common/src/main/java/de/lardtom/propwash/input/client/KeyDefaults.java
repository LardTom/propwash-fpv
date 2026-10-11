package de.lardtom.propwash.input.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.input.SwitchFunction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class KeyDefaults {
    public static final int UNBOUND = 0;
    public static final String VANILLA_PICK_ITEM = "key.pickItem";
    public static final String VANILLA_SPECTATOR_HOTBAR = "key.spectatorHotbar";

    public static final Entry THROTTLE_UP = own("kb_throttle_up", UNBOUND, true);
    public static final Entry THROTTLE_DOWN = own("kb_throttle_down", UNBOUND, true);
    public static final Entry YAW_LEFT = own("kb_yaw_left", UNBOUND, true);
    public static final Entry YAW_RIGHT = own("kb_yaw_right", UNBOUND, true);
    public static final Entry PITCH_FORWARD = own("kb_pitch_forward", InputConstants.KEY_UP, true);
    public static final Entry PITCH_BACK = own("kb_pitch_back", InputConstants.KEY_DOWN, true);
    public static final Entry ROLL_LEFT = own("kb_roll_left", InputConstants.KEY_LEFT, true);
    public static final Entry ROLL_RIGHT = own("kb_roll_right", InputConstants.KEY_RIGHT, true);
    public static final Entry ARM = own("drone_arm", InputConstants.KEY_Z, false);
    public static final Entry FLIGHT_MODE = own("drone_flight_mode", InputConstants.KEY_R, false);
    public static final Entry MODE_ACRO = own("drone_mode_acro", InputConstants.KEY_NUMPAD1, false);
    public static final Entry MODE_ANGLE = own("drone_mode_angle", InputConstants.KEY_NUMPAD2, false);
    public static final Entry MODE_HORIZON = own("drone_mode_horizon", InputConstants.KEY_NUMPAD3, false);
    public static final Entry ALT_HOLD = own("drone_alt_hold", InputConstants.KEY_NUMPAD4, false);
    public static final Entry POS_HOLD = own("drone_pos_hold", InputConstants.KEY_NUMPAD5, false);
    public static final Entry MODE_3D = own("drone_mode_3d", InputConstants.KEY_NUMPAD6, false);
    public static final Entry FLIP_UPRIGHT = own("drone_flip_upright", InputConstants.KEY_U, false);
    public static final Entry EXIT = own("drone_exit", InputConstants.KEY_Y, false);
    public static final Entry BEEPER = own("beeper", InputConstants.KEY_PERIOD, false);
    public static final Entry LEDS = own("drone_leds", InputConstants.KEY_F8, false);
    public static final Entry RECEIVER_WIZARD = own("receiver_wizard", UNBOUND, false);
    public static final Entry MOUSE_CENTER = new Entry("kb_mouse_center", InputConstants.MOUSE_BUTTON_MIDDLE, true, true, true);

    public static final Entry RESCUE = other("rescue", InputConstants.KEY_J);
    public static final Entry SET_HOME = other("set_home", InputConstants.KEY_COMMA);
    public static final Entry RACE_RESTART = other("race_restart", InputConstants.KEY_BACKSPACE);
    public static final Entry CONFIGURATOR = other("configurator", InputConstants.KEY_K);
    public static final Entry FLIGHT_SCHOOL = other("flight_school", InputConstants.KEY_M);
    public static final Entry SELFTEST = other("selftest", UNBOUND);
    public static final Entry ACTION_CAM_RECORD = other("action_cam_record", InputConstants.KEY_F9);
    public static final Entry VIDEO_SPECTATE = other("video_spectate", InputConstants.KEY_F7);
    public static final Entry GATE_ROTATE = other("gate_rotate", InputConstants.KEY_R);
    public static final Entry GATE_EDIT_CURSOR = other("gate_edit_cursor", InputConstants.KEY_LALT);
    public static final Entry CARGO_DROP = other("drone_cargo_drop", InputConstants.KEY_RETURN);
    public static final Entry BLACKBOX = other("blackbox", InputConstants.KEY_NUMPAD7);
    public static final Entry DRONE_TRAILS = other("drone_trails", UNBOUND);

    public static final List<Entry> OWN = List.of(THROTTLE_UP, THROTTLE_DOWN, YAW_LEFT, YAW_RIGHT, PITCH_FORWARD, PITCH_BACK, ROLL_LEFT,
            ROLL_RIGHT, MOUSE_CENTER, ARM, FLIGHT_MODE, MODE_ACRO, MODE_ANGLE, MODE_HORIZON, ALT_HOLD, POS_HOLD, MODE_3D, FLIP_UPRIGHT, EXIT, BEEPER,
            LEDS, RECEIVER_WIZARD);
    public static final List<Entry> OTHER_MODULES = List.of(RESCUE, SET_HOME, RACE_RESTART, CONFIGURATOR, FLIGHT_SCHOOL, SELFTEST,
            ACTION_CAM_RECORD, VIDEO_SPECTATE, GATE_ROTATE, GATE_EDIT_CURSOR, CARGO_DROP, BLACKBOX, DRONE_TRAILS);
    public static final List<Entry> COMMANDS = commands();
    public static final List<String> REMOVED = List.of("kb_arm", "kb_angle");
    public static final List<Set<String>> SHARED = List.of(Set.of(FLIGHT_MODE.mappingName(), GATE_ROTATE.mappingName()),
            Set.of(MOUSE_CENTER.mappingName(), VANILLA_PICK_ITEM), Set.of(MOUSE_CENTER.mappingName(), VANILLA_SPECTATOR_HOTBAR));

    private KeyDefaults() {
    }

    public static List<Entry> all() {
        List<Entry> all = new ArrayList<>(OWN);
        all.addAll(OTHER_MODULES);
        all.addAll(COMMANDS);
        return all;
    }

    public static Entry command(SwitchFunction function) {
        return COMMANDS.get(function.commandIndex());
    }

    public static boolean isCommand(String mappingName) {
        for (Entry entry : COMMANDS) {
            if (entry.mappingName().equals(mappingName)) {
                return true;
            }
        }
        return false;
    }

    private static List<Entry> commands() {
        List<Entry> out = new ArrayList<>();
        for (int i = 1; i <= SwitchFunction.COMMAND_COUNT; i++) {
            out.add(new Entry("command_" + i, UNBOUND, false, false));
        }
        return List.copyOf(out);
    }

    public static String mappingName(String name) {
        return "key." + Constants.MOD_ID + "." + name;
    }

    public static boolean sharesKey(String mappingName, String otherMappingName) {
        if (mappingName.equals(otherMappingName)) {
            return false;
        }
        for (Set<String> group : SHARED) {
            if (group.contains(mappingName) && group.contains(otherMappingName)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> duplicates(List<Entry> entries) {
        Map<String, List<Entry>> byKey = new LinkedHashMap<>();
        for (Entry entry : entries) {
            if (entry.key() != UNBOUND) {
                byKey.computeIfAbsent((entry.mouse() ? "mouse." : "") + entry.key(), k -> new ArrayList<>()).add(entry);
            }
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> e : byKey.entrySet()) {
            List<Entry> group = e.getValue();
            if (group.size() > 1 && !shared(group)) {
                out.add(e.getKey() + "=" + group.stream().map(Entry::name).toList());
            }
        }
        return out;
    }

    private static boolean shared(List<Entry> group) {
        for (int i = 0; i < group.size(); i++) {
            for (int j = i + 1; j < group.size(); j++) {
                if (!sharesKey(group.get(i).mappingName(), group.get(j).mappingName())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Entry own(String name, int key, boolean flightStick) {
        return new Entry(name, key, true, flightStick);
    }

    private static Entry other(String name, int key) {
        return new Entry(name, key, false, false);
    }

    public record Entry(String name, int key, boolean own, boolean flightStick, boolean mouse) {
        public Entry(String name, int key, boolean own, boolean flightStick) {
            this(name, key, own, flightStick, false);
        }

        public String mappingName() {
            return KeyDefaults.mappingName(this.name);
        }
    }
}
