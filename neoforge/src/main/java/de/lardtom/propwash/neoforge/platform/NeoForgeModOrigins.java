package de.lardtom.propwash.neoforge.platform;

import de.lardtom.propwash.platform.services.ModOrigins;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.neoforged.fml.CrashReportCallables;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileParser;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.resource.ResourcePackLoader;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class NeoForgeModOrigins implements ModOrigins {
    private static final Set<String> PLATFORM = Set.of("minecraft", "neoforge", "java", "mixinextras", "fml");
    private final Map<Class<?>, Optional<String>> classes = new ConcurrentHashMap<>();
    private volatile @Nullable Index index;

    private record Index(Map<String, String> modules, Map<Path, String> paths, Map<String, String> packs, Map<String, String> mixins) {
    }

    @Override
    public Optional<String> modOfClass(Class<?> type) {
        Class<?> host = host(type);
        if (host.getClassLoader() == null) {
            return Optional.of("java");
        }
        return this.classes.computeIfAbsent(host, this::resolve);
    }

    private static Class<?> host(Class<?> type) {
        Class<?> current = type;
        while (current.isArray()) {
            current = current.getComponentType();
        }
        try {
            return current.getNestHost();
        } catch (RuntimeException | LinkageError e) {
            return current;
        }
    }

    private Optional<String> resolve(Class<?> type) {
        Index known = this.index();
        Module module = type.getModule();
        if (module.isNamed()) {
            String mod = known.modules().get(module.getName());
            if (mod != null) {
                return Optional.of(mod);
            }
        }
        Path location = location(type);
        if (location != null) {
            String mod = known.paths().get(location);
            if (mod != null) {
                return Optional.of(mod);
            }
        }
        String resource = type.getName().replace('.', '/') + ".class";
        for (IModFileInfo info : ModList.get().getModFiles()) {
            try {
                if (info.getFile().getContents().containsFile(resource) && !info.getMods().isEmpty()) {
                    return Optional.of(info.getMods().getFirst().getModId());
                }
            } catch (RuntimeException ignored) {
            }
        }
        return Optional.empty();
    }

    private static @Nullable Path location(Class<?> type) {
        try {
            CodeSource source = type.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            return Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
    }

    private Index index() {
        Index known = this.index;
        if (known != null) {
            return known;
        }
        Map<String, String> modules = new HashMap<>();
        Map<Path, String> paths = new HashMap<>();
        Map<String, String> packs = new HashMap<>();
        Map<String, String> mixins = new HashMap<>();
        for (IModFileInfo info : ModList.get().getModFiles()) {
            if (info.getMods().isEmpty()) {
                continue;
            }
            String id = info.getMods().getFirst().getModId();
            IModFile file = info.getFile();
            try {
                paths.putIfAbsent(file.getFilePath().toAbsolutePath().normalize(), id);
                for (Path root : file.getContents().getContentRoots()) {
                    paths.putIfAbsent(root.toAbsolutePath().normalize(), id);
                }
            } catch (RuntimeException ignored) {
            }
            try {
                packs.putIfAbsent(ResourcePackLoader.getPackName(file), id);
            } catch (RuntimeException | LinkageError ignored) {
            }
            if (file instanceof ModFile modFile) {
                try {
                    if (modFile.getModuleDescriptor() != null) {
                        modules.putIfAbsent(modFile.getModuleDescriptor().name(), id);
                    }
                } catch (RuntimeException | LinkageError ignored) {
                }
                try {
                    for (ModFileParser.MixinConfig config : modFile.getMixinConfigs()) {
                        mixins.putIfAbsent(config.config(), id);
                    }
                } catch (RuntimeException | LinkageError ignored) {
                }
            }
        }
        known = new Index(Map.copyOf(modules), Map.copyOf(paths), Map.copyOf(packs), Map.copyOf(mixins));
        this.index = known;
        return known;
    }

    @Override
    public Optional<String> modOfPack(PackResources pack) {
        return Optional.ofNullable(this.index().packs().get(pack.packId()));
    }

    @Override
    public Optional<String> modOfMixinConfig(String config) {
        return Optional.ofNullable(this.index().mixins().get(config));
    }

    @Override
    public Optional<String> activeMod() {
        try {
            ModContainer container = ModLoadingContext.get().getActiveContainer();
            return container == null || this.platform(container.getModId()) ? Optional.empty() : Optional.of(container.getModId());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean platform(String modId) {
        return PLATFORM.contains(modId);
    }

    @Override
    public List<String> modIds() {
        List<String> ids = new ArrayList<>();
        for (IModInfo info : ModList.get().getMods()) {
            ids.add(info.getModId());
        }
        ids.sort(String::compareTo);
        return List.copyOf(ids);
    }

    @Override
    public void visitFiles(String modId, String folder, FileVisitor visitor) {
        IModFileInfo info = ModList.get().getModFileById(modId);
        if (info == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        info.getFile().getContents().visitContent(folder, (path, resource) -> {
            if (seen.add(path)) {
                visitor.visit(path, resource::open);
            }
        });
    }

    @Override
    public Map<String, Optional<String>> networkChannels(boolean client) {
        Map<String, Optional<String>> out = new TreeMap<>();
        this.collect(out, handlers("SERVERBOUND_HANDLERS"));
        if (client) {
            this.collect(out, handlers("CLIENTBOUND_HANDLERS"));
        }
        return out;
    }

    private void collect(Map<String, Optional<String>> out, Map<?, ?> byProtocol) {
        for (Object protocol : byProtocol.values()) {
            if (!(protocol instanceof Map<?, ?> handlers)) {
                continue;
            }
            for (Map.Entry<?, ?> entry : handlers.entrySet()) {
                if (!(entry.getKey() instanceof Identifier id)) {
                    continue;
                }
                out.merge(id.toString(), this.handlerMod(entry.getValue(), 2), this::preferred);
            }
        }
    }

    private Optional<String> handlerMod(@Nullable Object handler, int depth) {
        if (handler == null) {
            return Optional.empty();
        }
        Optional<String> mod = this.modOfClass(handler.getClass());
        if (mod.isPresent() && !this.platform(mod.get()) || depth <= 0) {
            return mod;
        }
        for (Object inner : components(handler)) {
            Optional<String> found = this.handlerMod(inner, depth - 1);
            if (found.isPresent() && !this.platform(found.get())) {
                return found;
            }
        }
        return mod;
    }

    private static List<Object> components(Object owner) {
        List<Object> out = new ArrayList<>();
        Class<?> type = owner.getClass();
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                try {
                    Object value = component.getAccessor().invoke(owner);
                    if (value != null) {
                        out.add(value);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            return out;
        }
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(owner);
                if (value != null) {
                    out.add(value);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return out;
    }

    private static Map<?, ?> handlers(String name) {
        try {
            Field field = NetworkRegistry.class.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map<?, ?> map) {
                return map;
            }
            throw new IllegalStateException(name + " is not a map");
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("NetworkRegistry." + name + " not readable: " + e, e);
        }
    }

    private Optional<String> preferred(Optional<String> old, Optional<String> added) {
        if (old.isPresent() && !this.platform(old.get())) {
            return old;
        }
        return added.isPresent() ? added : old;
    }

    @Override
    public boolean addCrashSection(String title, Supplier<String> body) {
        CrashReportCallables.registerCrashCallable(title, body);
        return true;
    }
}
