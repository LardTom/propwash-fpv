package de.lardtom.propwash.guard;

import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.platform.services.ModOrigins;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.transformer.Config;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class MixinScan {
    public static final String PACKAGE = "de.lardtom.propwash.";
    private static final String OWN_CONFIG = "propwash.";
    private static final String PROCESSOR = "org.spongepowered.asm.mixin.transformer.MixinProcessor";

    private MixinScan() {
    }

    public static Map<String, Set<String>> foreignTargets() {
        ModOrigins origins = NamespaceGuard.origins();
        Map<String, Set<String>> out = new TreeMap<>();
        for (IMixinConfig config : configs()) {
            String name = config.getName();
            Optional<String> mod = origins.modOfMixinConfig(name);
            if (NamespaceGuard.own(mod) || mod.isEmpty() && name.startsWith(OWN_CONFIG)) {
                continue;
            }
            Set<String> targets = new TreeSet<>();
            for (String target : config.getTargets()) {
                String dotted = target.replace('/', '.');
                if (dotted.startsWith(PACKAGE)) {
                    targets.add(dotted);
                }
            }
            if (!targets.isEmpty()) {
                out.computeIfAbsent(NamespaceGuard.modOrUnknown(mod), key -> new TreeSet<>()).addAll(targets);
            }
        }
        return out;
    }

    static List<IMixinConfig> configs() {
        Map<String, IMixinConfig> out = new LinkedHashMap<>();
        Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        if (transformer != null) {
            for (Object processor : fields(transformer, PROCESSOR)) {
                for (Object list : fields(processor, null)) {
                    if (list instanceof List<?> configs) {
                        for (Object config : configs) {
                            if (config instanceof IMixinConfig mixinConfig) {
                                out.putIfAbsent(mixinConfig.getName(), mixinConfig);
                            }
                        }
                    }
                }
            }
        }
        for (Config config : Mixins.getConfigs()) {
            out.putIfAbsent(config.getName(), config.getConfig());
        }
        return List.copyOf(out.values());
    }

    private static List<Object> fields(Object owner, @Nullable String typeName) {
        List<Object> out = new ArrayList<>();
        for (Class<?> type = owner.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                boolean wanted = typeName == null ? List.class.isAssignableFrom(field.getType()) : field.getType().getName().equals(typeName);
                if (!wanted) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(owner);
                    if (value != null) {
                        out.add(value);
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    throw new IllegalStateException("mixin configs not readable: " + e, e);
                }
            }
        }
        return out;
    }

    public static void run(GuardReport report, boolean clientClasses) {
        for (Map.Entry<String, Set<String>> entry : foreignTargets().entrySet()) {
            report.mixins(entry.getKey(), entry.getValue());
            for (String target : entry.getValue()) {
                enumValues(report, entry.getKey(), target, clientClasses);
            }
        }
    }

    private static void enumValues(GuardReport report, String mod, String target, boolean clientClasses) {
        if (!clientClasses && target.contains(".client.")) {
            return;
        }
        byte[] original = original(target);
        if (original == null || !EnumConstants.isEnum(original)) {
            return;
        }
        Class<?> type;
        try {
            type = Class.forName(target, true, MixinScan.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return;
        }
        List<String> extra = EnumConstants.extra(EnumConstants.declared(original), type.getEnumConstants());
        for (String name : extra) {
            report.add(mod, GuardCategory.ENUM, shortName(target) + "." + name);
        }
    }

    public static String shortName(String target) {
        return target.startsWith(PACKAGE) ? target.substring(PACKAGE.length()) : target;
    }

    static byte @Nullable [] original(String target) {
        String resource = target.replace('.', '/') + ".class";
        try (InputStream in = MixinScan.class.getClassLoader().getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean clientDist() {
        try {
            return Services.PLATFORM.isClientDist();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
