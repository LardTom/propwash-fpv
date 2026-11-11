package de.lardtom.propwash.guard;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.platform.services.ModOrigins;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FileScan {
    public static final String DATA = "data/propwash";
    public static final String ASSETS = "assets/propwash";
    private static final int MAX_INFO = 20;
    private static final Map<String, Set<String>> OWN = new ConcurrentHashMap<>();
    private static final Set<String> INFO_LOGGED = ConcurrentHashMap.newKeySet();

    private FileScan() {
    }

    public static Set<String> own(String folder) {
        return OWN.computeIfAbsent(folder, key -> {
            Set<String> paths = new HashSet<>();
            ModOrigins origins = NamespaceGuard.origins();
            for (String mod : NamespaceGuard.OWN_MODS) {
                origins.visitFiles(mod, key, (path, opener) -> paths.add(path));
            }
            return Set.copyOf(paths);
        });
    }

    public static void mods(GuardReport report, String folder) {
        Set<String> own = own(folder);
        ModOrigins origins = NamespaceGuard.origins();
        for (String mod : origins.modIds()) {
            if (NamespaceGuard.own(mod) || origins.platform(mod)) {
                continue;
            }
            origins.visitFiles(mod, folder, (path, opener) -> {
                if (ResourcePaths.classify(path, true, own) == ResourcePaths.Kind.FINDING) {
                    report.add(mod, ResourcePaths.category(path), path);
                }
            });
        }
    }

    public static void playerPacks(ResourceManager manager, PackType type) {
        Set<String> own = own(type == PackType.SERVER_DATA ? DATA : ASSETS);
        ModOrigins origins = NamespaceGuard.origins();
        manager.listPacks().forEach(pack -> {
            if (!playerPack(origins, pack) || !pack.getNamespaces(type).contains(KeyClassifier.NAMESPACE)) {
                return;
            }
            List<String> fresh = new ArrayList<>();
            try {
                for (String path : packFiles(pack, type, own)) {
                    if (INFO_LOGGED.add(pack.packId() + " " + path)) {
                        fresh.add(path);
                    }
                }
            } catch (RuntimeException e) {
                return;
            }
            if (!fresh.isEmpty()) {
                Constants.LOG.info("Namespace check: pack '{}' adds {} new files in the namespace propwash (resource and data packs are allowed): {}",
                        pack.packId(), fresh.size(), GuardReport.capped(fresh.size() > MAX_INFO ? fresh.subList(0, MAX_INFO) : fresh));
            }
        });
    }

    static List<String> packFiles(PackResources pack, PackType type, Set<String> own) {
        String root = type.getDirectory() + "/" + KeyClassifier.NAMESPACE + "/";
        List<String> out = new ArrayList<>();
        for (String folder : ResourcePaths.folders(root, own)) {
            pack.listResources(type, KeyClassifier.NAMESPACE, folder, (id, supplier) -> {
                String path = root + id.getPath();
                if (ResourcePaths.classify(path, false, own) == ResourcePaths.Kind.INFO) {
                    out.add(path);
                }
            });
        }
        out.sort(null);
        return out;
    }

    static boolean playerPack(ModOrigins origins, PackResources pack) {
        String id = pack.packId();
        if (origins.modOfPack(pack).isPresent()) {
            return false;
        }
        return id.startsWith("file/") || id.startsWith("server/") || id.equals("server");
    }
}
