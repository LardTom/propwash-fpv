package de.lardtom.propwash.join;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record JoinInfo(String propwashVersion, int protocol, String builtinFingerprint, List<AddonInfo> addons) {
    public static final int MAX_ADDONS = 256;
    public static final int MAX_VERSION = 64;
    public static final int MAX_FINGERPRINT = 128;

    public JoinInfo {
        propwashVersion = SafeText.clean(propwashVersion, MAX_VERSION);
        builtinFingerprint = SafeText.clean(builtinFingerprint, MAX_FINGERPRINT);
        Map<String, AddonInfo> unique = new LinkedHashMap<>();
        if (addons != null) {
            for (AddonInfo addon : addons) {
                if (addon != null && addon.valid() && unique.size() < MAX_ADDONS) {
                    unique.putIfAbsent(addon.modId(), addon);
                }
            }
        }
        List<AddonInfo> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparing(AddonInfo::modId));
        addons = List.copyOf(sorted);
    }

    public Optional<AddonInfo> addon(String modId) {
        for (AddonInfo addon : this.addons) {
            if (addon.modId().equals(modId)) {
                return Optional.of(addon);
            }
        }
        return Optional.empty();
    }

    public Optional<AddonInfo> owner(String namespace) {
        for (AddonInfo addon : this.addons) {
            if (addon.namespaces().contains(namespace)) {
                return Optional.of(addon);
            }
        }
        for (AddonInfo addon : this.addons) {
            if (addon.modId().equals(namespace)) {
                return Optional.of(addon);
            }
        }
        return Optional.empty();
    }
}
