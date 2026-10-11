package de.lardtom.propwash.join;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.network.ProtocolVersionPayload;
import de.lardtom.propwash.platform.ModMeta;
import de.lardtom.propwash.platform.Services;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalJoinInfo {
    private static final Map<String, String> BUNDLED = new ConcurrentHashMap<>();
    private static final Map<String, AddonInfo> DECLARED = new ConcurrentHashMap<>();
    private static volatile @Nullable JoinInfo cached;

    private LocalJoinInfo() {
    }

    public static void noteBundled(String modId, String namespace) {
        if (!Constants.MOD_ID.equals(modId)) {
            BUNDLED.putIfAbsent(namespace, modId);
            cached = null;
        }
    }

    public static void declare(AddonInfo addon) {
        if (addon.valid()) {
            DECLARED.put(addon.modId(), addon);
            cached = null;
        }
    }

    public static JoinInfo get() {
        JoinInfo info = cached;
        if (info != null) {
            return info;
        }
        info = build();
        if (PartIndex.isFrozen()) {
            cached = info;
        }
        return info;
    }

    public static void invalidate() {
        cached = null;
    }

    private static JoinInfo build() {
        TreeSet<String> namespaces = new TreeSet<>();
        for (BatteryType.Chemistry chemistry : PartIndex.registeredChemistries()) {
            if (!PartIndex.isBuiltinChemistry(chemistry.id())) {
                namespaces.add(chemistry.id().getNamespace());
            }
        }
        for (PartInfo part : PartIndex.registered()) {
            if (!part.builtin()) {
                namespaces.add(part.id().getNamespace());
            }
        }
        Map<String, TreeSet<String>> owned = new TreeMap<>();
        for (String namespace : namespaces) {
            String owner = owner(namespace);
            if (owner != null) {
                owned.computeIfAbsent(owner, key -> new TreeSet<>()).add(namespace);
            }
        }
        Map<String, AddonInfo> addons = new TreeMap<>();
        for (ModMeta meta : Services.PLATFORM.dependentMods(Constants.MOD_ID)) {
            addons.put(meta.id(), addon(meta, owned.get(meta.id())));
        }
        for (Map.Entry<String, TreeSet<String>> entry : owned.entrySet()) {
            if (addons.containsKey(entry.getKey())) {
                continue;
            }
            AddonInfo declared = DECLARED.get(entry.getKey());
            if (declared != null) {
                continue;
            }
            Optional<ModMeta> meta = Services.PLATFORM.modMeta(entry.getKey());
            meta.ifPresent(value -> addons.put(value.id(), addon(value, entry.getValue())));
        }
        for (AddonInfo declared : DECLARED.values()) {
            TreeSet<String> all = new TreeSet<>(declared.namespaces());
            TreeSet<String> extra = owned.get(declared.modId());
            if (extra != null) {
                all.addAll(extra);
            }
            addons.putIfAbsent(declared.modId(), new AddonInfo(declared.modId(), declared.name(), declared.version(), declared.homepage(),
                    new ArrayList<>(all)));
        }
        addons.remove(Constants.MOD_ID);
        return new JoinInfo(Services.PLATFORM.getModVersion(), ProtocolVersionPayload.localProtocol(), builtinFingerprint(),
                new ArrayList<>(addons.values()));
    }

    private static AddonInfo addon(ModMeta meta, @Nullable TreeSet<String> namespaces) {
        List<String> list = namespaces == null ? List.of() : new ArrayList<>(namespaces);
        return new AddonInfo(meta.id(), meta.name(), meta.version(), meta.homepage(), list);
    }

    private static @Nullable String owner(String namespace) {
        for (AddonInfo declared : DECLARED.values()) {
            if (declared.namespaces().contains(namespace) || declared.modId().equals(namespace)) {
                return declared.modId();
            }
        }
        String bundled = BUNDLED.get(namespace);
        if (bundled != null) {
            return bundled;
        }
        if (!Constants.MOD_ID.equals(namespace) && Services.PLATFORM.modMeta(namespace).isPresent()) {
            return namespace;
        }
        return null;
    }

    public static String builtinFingerprint() {
        StringBuilder text = new StringBuilder();
        for (BatteryType.Chemistry chemistry : PartIndex.registeredChemistries()) {
            if (PartIndex.isBuiltinChemistry(chemistry.id())) {
                text.append("chemistry|").append(chemistry.id()).append('|').append(PartIndex.hashOf(chemistry.definition())).append('\n');
            }
        }
        for (PartInfo part : PartIndex.registered()) {
            if (part.builtin()) {
                text.append(part.definition().kind().key()).append('|').append(part.id()).append('|').append(PartIndex.hashOf(part.definition()))
                        .append('\n');
            }
        }
        return PartIndex.hashOf(text.toString());
    }
}
