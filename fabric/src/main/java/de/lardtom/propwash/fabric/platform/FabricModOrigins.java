package de.lardtom.propwash.fabric.platform;

import de.lardtom.propwash.platform.services.ModOrigins;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.pack.ModPackResources;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
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
import java.util.stream.Stream;

public final class FabricModOrigins implements ModOrigins {
    private static final String MOD_ID_DECORATION = "fabric-modId";
    private static final Set<String> PLATFORM = Set.of("minecraft", "java", "fabricloader", "fabric-api", "fabric", "mixinextras");
    private final Map<Class<?>, Optional<String>> classes = new ConcurrentHashMap<>();
    private volatile @Nullable Map<Path, String> roots;
    private volatile @Nullable Map<String, String> mixinConfigs;

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
        Path location = location(type);
        if (location != null) {
            String mod = this.roots().get(location);
            if (mod != null) {
                return Optional.of(mod);
            }
        }
        String resource = type.getName().replace('.', '/') + ".class";
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            try {
                if (container.findPath(resource).isPresent()) {
                    return Optional.of(container.getMetadata().getId());
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

    private Map<Path, String> roots() {
        Map<Path, String> known = this.roots;
        if (known != null) {
            return known;
        }
        Map<Path, String> built = new HashMap<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            String id = container.getMetadata().getId();
            try {
                ModOrigin origin = container.getOrigin();
                if (origin.getKind() == ModOrigin.Kind.PATH) {
                    for (Path path : origin.getPaths()) {
                        built.putIfAbsent(path.toAbsolutePath().normalize(), id);
                    }
                }
            } catch (RuntimeException ignored) {
            }
            try {
                for (Path root : container.getRootPaths()) {
                    if (root.getFileSystem() == FileSystems.getDefault()) {
                        built.putIfAbsent(root.toAbsolutePath().normalize(), id);
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
        this.roots = Map.copyOf(built);
        return this.roots;
    }

    @Override
    public Optional<String> modOfPack(PackResources pack) {
        if (pack instanceof ModPackResources mod) {
            return Optional.of(mod.getFabricModMetadata().getId());
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> modOfMixinConfig(String config) {
        for (Config candidate : Mixins.getConfigs()) {
            if (candidate.getName().equals(config)) {
                Object mod = candidate.getConfig().getDecoration(MOD_ID_DECORATION);
                if (mod instanceof String id && !id.isBlank()) {
                    return Optional.of(id);
                }
            }
        }
        return Optional.ofNullable(this.mixinConfigs().get(config));
    }

    private Map<String, String> mixinConfigs() {
        Map<String, String> known = this.mixinConfigs;
        if (known != null) {
            return known;
        }
        Map<String, String> built = new HashMap<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            String id = container.getMetadata().getId();
            try {
                Optional<Path> metadata = container.findPath("fabric.mod.json");
                if (metadata.isEmpty()) {
                    continue;
                }
                try (Reader reader = Files.newBufferedReader(metadata.get(), StandardCharsets.UTF_8)) {
                    JsonElement mixins = JsonParser.parseReader(reader).getAsJsonObject().get("mixins");
                    if (mixins == null || !mixins.isJsonArray()) {
                        continue;
                    }
                    for (JsonElement entry : mixins.getAsJsonArray()) {
                        String name = entry.isJsonObject() && entry.getAsJsonObject().has("config")
                                ? entry.getAsJsonObject().get("config").getAsString() : entry.isJsonPrimitive() ? entry.getAsString() : null;
                        if (name != null) {
                            built.putIfAbsent(name, id);
                        }
                    }
                }
            } catch (IOException | RuntimeException ignored) {
            }
        }
        this.mixinConfigs = Map.copyOf(built);
        return this.mixinConfigs;
    }

    @Override
    public boolean platform(String modId) {
        return PLATFORM.contains(modId) || modId.startsWith("fabric-");
    }

    @Override
    public List<String> modIds() {
        List<String> ids = new ArrayList<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            if (!"builtin".equals(container.getMetadata().getType())) {
                ids.add(container.getMetadata().getId());
            }
        }
        ids.sort(String::compareTo);
        return List.copyOf(ids);
    }

    @Override
    public void visitFiles(String modId, String folder, FileVisitor visitor) {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(modId);
        if (container.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (Path root : container.get().getRootPaths()) {
            Path start = root.resolve(folder);
            if (!Files.isDirectory(start)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(start)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    String relative = root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
                    if (seen.add(relative)) {
                        visitor.visit(relative, () -> Files.newInputStream(file));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Mod " + modId + ": " + folder, e);
            }
        }
    }

    @Override
    public Map<String, Optional<String>> networkChannels(boolean client) {
        Map<String, Optional<String>> out = new TreeMap<>();
        try {
            this.collect(out, ServerNetworkingImpl.PLAY.getHandlers());
            this.collect(out, ServerNetworkingImpl.CONFIGURATION.getHandlers());
        } catch (RuntimeException | LinkageError e) {
            for (Identifier id : ServerPlayNetworking.getGlobalReceivers()) {
                out.putIfAbsent(id.toString(), Optional.empty());
            }
            for (Identifier id : ServerConfigurationNetworking.getGlobalReceivers()) {
                out.putIfAbsent(id.toString(), Optional.empty());
            }
        }
        if (client && FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientChannels.collect(this, out);
        }
        return out;
    }

    void collect(Map<String, Optional<String>> out, Map<Identifier, ?> handlers) {
        for (Map.Entry<Identifier, ?> entry : handlers.entrySet()) {
            Object handler = entry.getValue();
            Optional<String> mod = handler == null ? Optional.empty() : this.modOfClass(handler.getClass());
            out.merge(entry.getKey().toString(), mod, this::preferred);
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
        return false;
    }

    private static final class ClientChannels {
        private ClientChannels() {
        }

        static void collect(FabricModOrigins origins, Map<String, Optional<String>> out) {
            try {
                origins.collect(out, net.fabricmc.fabric.impl.networking.client.ClientNetworkingImpl.PLAY.getHandlers());
                origins.collect(out, net.fabricmc.fabric.impl.networking.client.ClientNetworkingImpl.CONFIGURATION.getHandlers());
            } catch (RuntimeException | LinkageError e) {
                for (Identifier id : net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.getGlobalReceivers()) {
                    out.putIfAbsent(id.toString(), Optional.empty());
                }
            }
        }
    }
}
