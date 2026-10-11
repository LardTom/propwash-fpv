package de.lardtom.propwash.input;

import de.lardtom.propwash.config.JsonConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InputConfig {
    public static final int MAX_PROFILES = 64;
    public static final String KEYBOARD_GUID = "keyboard";
    public static final String MOUSE_GUID = "mouse";
    public static final int PROFILE_VERSION = 2;
    public static final JsonConfig<InputConfig> CONFIG = JsonConfig.create("input", InputConfig.class, InputConfig::new, InputConfig::sanitize);

    private static int revision;

    public String selectedGuid = "";
    public boolean keyboardFallback = true;
    public boolean appleGameController = false;
    public boolean mode3dUnlocked = false;
    public int profileVersion = 0;
    public KeyboardSettings keyboard = new KeyboardSettings();
    public MouseSettings mouse = new MouseSettings();
    public Map<String, DeviceProfile> profiles = new LinkedHashMap<>();
    public List<String> neverAsk = new ArrayList<>();

    public InputConfig() {
    }

    public static InputConfig get() {
        return CONFIG.get();
    }

    public static int revision() {
        return revision;
    }

    public static void commit(InputConfig config) {
        CONFIG.set(config);
        revision++;
    }

    public DeviceProfile profile(String guid) {
        return this.profiles.get(guid);
    }

    public boolean keyboardSelected() {
        return withoutDevice(this.selectedGuid);
    }

    public boolean mouseSelected() {
        return MOUSE_GUID.equals(this.selectedGuid);
    }

    public static boolean withoutDevice(String guid) {
        return KEYBOARD_GUID.equals(guid) || MOUSE_GUID.equals(guid);
    }

    public void touch(String guid) {
        DeviceProfile profile = this.profiles.remove(guid);
        if (profile != null) {
            this.profiles.put(guid, profile);
        }
    }

    public boolean isNewest(String guid) {
        String last = null;
        for (String key : this.profiles.keySet()) {
            last = key;
        }
        return guid.equals(last);
    }

    public SetupStatus setupStatus(String guid) {
        DeviceProfile profile = this.profiles.get(guid);
        if (profile != null && profile.setupDone) {
            return SetupStatus.COMPLETED;
        }
        if (this.neverAsk.contains(guid)) {
            return SetupStatus.NEVER;
        }
        return profile == null ? SetupStatus.NEW : SetupStatus.INCOMPLETE;
    }

    public int usageRank(String guid) {
        int rank = 0;
        for (Map.Entry<String, DeviceProfile> e : this.profiles.entrySet()) {
            if (e.getKey().equals(guid)) {
                return e.getValue().setupDone ? rank : -1;
            }
            rank++;
        }
        return -1;
    }

    public boolean markSetupDone(String guid) {
        DeviceProfile profile = this.profiles.get(guid);
        if (profile == null) {
            return false;
        }
        profile.setupDone = true;
        this.neverAsk.remove(guid);
        this.touch(guid);
        return true;
    }

    public void neverAskFor(String guid) {
        if (guid == null || guid.isEmpty() || guid.length() > 64 || withoutDevice(guid)) {
            return;
        }
        this.neverAsk.remove(guid);
        this.neverAsk.add(guid);
        while (this.neverAsk.size() > MAX_PROFILES) {
            this.neverAsk.removeFirst();
        }
    }

    public DeviceProfile profileOrCreate(String guid, String name, boolean gamepad, int axisCount) {
        DeviceProfile profile = this.profiles.get(guid);
        if (profile == null) {
            profile = ProfileDefaults.create(name, gamepad, axisCount);
            this.profiles.put(guid, profile);
        }
        return profile;
    }

    public InputConfig copy() {
        InputConfig copy = new InputConfig();
        copy.selectedGuid = this.selectedGuid;
        copy.keyboardFallback = this.keyboardFallback;
        copy.appleGameController = this.appleGameController;
        copy.mode3dUnlocked = this.mode3dUnlocked;
        copy.profileVersion = this.profileVersion;
        copy.keyboard = this.keyboard.copy();
        copy.mouse = this.mouse.copy();
        copy.neverAsk = new ArrayList<>(this.neverAsk);
        for (Map.Entry<String, DeviceProfile> e : this.profiles.entrySet()) {
            copy.profiles.put(e.getKey(), e.getValue().copy());
        }
        return copy;
    }

    public void mergeReceiverFrom(InputConfig source) {
        this.selectedGuid = source.selectedGuid;
        this.keyboardFallback = source.keyboardFallback;
        this.appleGameController = source.appleGameController;
        this.keyboard = source.keyboard.copy();
        this.mouse = source.mouse.copy();
        for (Map.Entry<String, DeviceProfile> e : source.profiles.entrySet()) {
            DeviceProfile target = this.profiles.get(e.getKey());
            if (target == null) {
                this.profiles.put(e.getKey(), e.getValue().copy());
            } else {
                target.name = e.getValue().name;
                target.copyReceiverFrom(e.getValue());
            }
        }
    }

    public void mergeSwitchesFrom(InputConfig source) {
        this.mode3dUnlocked = source.mode3dUnlocked;
        for (Map.Entry<String, DeviceProfile> e : source.profiles.entrySet()) {
            DeviceProfile target = this.profiles.get(e.getKey());
            if (target == null) {
                this.profiles.put(e.getKey(), e.getValue().copy());
            } else {
                target.copySwitchesFrom(e.getValue());
            }
        }
    }

    public void sanitize() {
        if (this.selectedGuid == null || this.selectedGuid.length() > 64) {
            this.selectedGuid = "";
        }
        if (this.keyboard == null) {
            this.keyboard = new KeyboardSettings();
        }
        this.keyboard.sanitize();
        if (this.mouse == null) {
            this.mouse = new MouseSettings();
        }
        this.mouse.sanitize();
        List<Map.Entry<String, DeviceProfile>> valid = new ArrayList<>();
        if (this.profiles != null) {
            for (Map.Entry<String, DeviceProfile> e : this.profiles.entrySet()) {
                if (e.getKey() == null || e.getKey().isEmpty() || e.getKey().length() > 64 || e.getValue() == null) {
                    continue;
                }
                valid.add(e);
            }
        }
        int excess = valid.size() - MAX_PROFILES;
        Map<String, DeviceProfile> clean = new LinkedHashMap<>();
        for (Map.Entry<String, DeviceProfile> e : valid) {
            if (excess > 0 && !e.getKey().equals(this.selectedGuid)) {
                excess--;
                continue;
            }
            e.getValue().sanitize();
            clean.put(e.getKey(), e.getValue());
        }
        if (this.profileVersion < PROFILE_VERSION) {
            for (DeviceProfile profile : clean.values()) {
                ProfileDefaults.migrate(profile, this.profileVersion);
            }
        }
        this.profileVersion = PROFILE_VERSION;
        this.profiles = clean;
        List<String> never = new ArrayList<>();
        if (this.neverAsk != null) {
            for (String guid : this.neverAsk) {
                if (guid != null && !guid.isEmpty() && guid.length() <= 64 && !withoutDevice(guid) && !never.contains(guid)) {
                    never.add(guid);
                }
            }
        }
        while (never.size() > MAX_PROFILES) {
            never.removeFirst();
        }
        this.neverAsk = never;
    }
}
