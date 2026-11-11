package de.lardtom.propwash.guard;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.api.client.WorkbenchExtension;
import de.lardtom.propwash.api.registry.ExtensionTab;
import de.lardtom.propwash.apiimpl.ExtensionTabs;
import de.lardtom.propwash.apiimpl.NamespaceRules;
import de.lardtom.propwash.platform.services.ModOrigins;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefusalOriginTest {
    private static final String FOREIGN = "fakeaddon";
    private static final String OWN_PREFIX = "de.lardtom.propwash.";
    private static final String FOREIGN_CALLER = "org.example.foreign.ForeignCaller";

    @BeforeEach
    void clean() {
        NamespaceRules.reset();
    }

    @AfterEach
    void restore() {
        NamespaceRules.reset();
        NamespaceGuard.overrideOrigins(null);
    }

    @Test
    void refusalsFromPropwashsOwnCodeAreNoFindings() {
        install(new FakeOrigins(true, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> PropwashApi.extensionTab("propwash"));
        assertThrows(IllegalArgumentException.class, () -> PropwashApi.accessories().register(pw("own_behavior"), new AccessoryBehavior() {
        }));
        PropwashClientApi.registerWorkbenchExtension(extension("own_extension"));
        assertEquals(3, NamespaceRules.refusals().size());
        for (NamespaceRules.Refusal refusal : NamespaceRules.refusals()) {
            assertEquals("propwash", refusal.mod(), refusal.id() + " (" + refusal.what() + ")");
        }
    }

    @Test
    void foreignCallerIsStillAFindingWithItsMod() throws Exception {
        install(new FakeOrigins(true, Map.of(FutureTask.class, FOREIGN)));
        FutureTask<String> task = new FutureTask<>(() -> NamespaceRules.requireForeign("propwash:foreign_direct", "osd element"));
        task.run();
        assertTrue(task.isDone());
        assertEquals(FOREIGN, NamespaceRules.refusals().getFirst().mod());
        assertTrue(refused(FOREIGN).contains("propwash:foreign_direct (osd element)"), refused(FOREIGN).toString());
    }

    @Test
    void foreignCodeDeeperInTheStackKeepsTheFinding() {
        install(new FakeOrigins(true, Map.of(ForeignAddon.class, FOREIGN)));
        ForeignAddon.run(() -> PropwashClientApi.registerWorkbenchExtension(extension("foreign_deep")));
        assertEquals(NamespaceRules.UNKNOWN_MOD, NamespaceRules.refusals().getFirst().mod());
        assertTrue(refused(NamespaceRules.UNKNOWN_MOD).contains("propwash:foreign_deep (workbench extension)"),
                refused(NamespaceRules.UNKNOWN_MOD).toString());
    }

    @Test
    void foreignCodeInAPropwashPackageCallingTheApiIsNoOwnCall() {
        install(new FakeOrigins(true, Map.of(ForeignAddon.class, FOREIGN)));
        ForeignAddon.register(extension("foreign_package"));
        assertEquals(NamespaceRules.UNKNOWN_MOD, NamespaceRules.refusals().getFirst().mod());
        assertTrue(refused(NamespaceRules.UNKNOWN_MOD).contains("propwash:foreign_package (workbench extension)"),
                refused(NamespaceRules.UNKNOWN_MOD).toString());
    }

    @Test
    void withoutKnownOriginsRefusalsStayFindingsOfAnUnknownMod() {
        install(new FakeOrigins(false, Map.of()));
        PropwashClientApi.registerWorkbenchExtension(extension("nobody"));
        assertEquals(NamespaceRules.UNKNOWN_MOD, NamespaceRules.refusals().getFirst().mod());
        assertTrue(refused(NamespaceRules.UNKNOWN_MOD).contains("propwash:nobody (workbench extension)"),
                refused(NamespaceRules.UNKNOWN_MOD).toString());
    }

    @Test
    void foreignCallerWithoutOriginIsAFindingOfAnUnknownMod() throws Exception {
        assertForeignCallerIsUnknown(null);
    }

    @Test
    void foreignCodeInAMinecraftClassIsAFindingOfAnUnknownMod() throws Exception {
        assertForeignCallerIsUnknown("minecraft");
    }

    private static void assertForeignCallerIsUnknown(@Nullable String mapped) throws Exception {
        Class<?> caller = foreignCaller();
        Map<Class<?>, String> mods = new HashMap<>();
        if (mapped != null) {
            mods.put(caller, mapped);
        }
        install(new FakeOrigins(true, mods));
        String internal = thrown(caller, "creativeTab");
        String api = thrown(caller, "extensionTab");
        assertEquals(Boolean.TRUE, caller.getMethod("configTab").invoke(null));
        String expected = "Mod '" + NamespaceRules.UNKNOWN_MOD + "' tried to register 'propwash:creative_tab' (creative tab)";
        assertTrue(internal.startsWith(expected), internal);
        assertTrue(api.startsWith(expected), api);
        assertEquals(2, NamespaceRules.refusals().size());
        for (NamespaceRules.Refusal refusal : NamespaceRules.refusals()) {
            assertEquals(NamespaceRules.UNKNOWN_MOD, refusal.mod(), refusal.id() + " (" + refusal.what() + ")");
        }
        List<String> refused = refused(NamespaceRules.UNKNOWN_MOD);
        assertTrue(refused.contains("propwash:creative_tab (creative tab)"), refused.toString());
        assertTrue(refused.contains("propwash:foreign_tab (config tab)"), refused.toString());
    }

    private static String thrown(Class<?> caller, String method) throws Exception {
        try {
            caller.getMethod(method).invoke(null);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IllegalArgumentException refused) {
                return String.valueOf(refused.getMessage());
            }
            throw e;
        }
        throw new AssertionError(method + " ohne Ablehnung");
    }

    private static Class<?> foreignCaller() {
        ClassDesc string = ConstantDescs.CD_String;
        ClassDesc tabs = ClassDesc.of(ExtensionTabs.class.getName());
        ClassDesc api = ClassDesc.of(PropwashApi.class.getName());
        ClassDesc rules = ClassDesc.of(NamespaceRules.class.getName());
        MethodTypeDesc none = MethodTypeDesc.of(ConstantDescs.CD_void);
        int flags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;
        byte[] bytes = ClassFile.of().build(ClassDesc.of(FOREIGN_CALLER), type -> type
                .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL)
                .withSuperclass(ConstantDescs.CD_Object)
                .withMethodBody("creativeTab", none, flags, code -> code
                        .ldc("propwash")
                        .invokestatic(tabs, "checkNamespace", MethodTypeDesc.of(string, string))
                        .pop()
                        .return_())
                .withMethodBody("extensionTab", none, flags, code -> code
                        .ldc("propwash")
                        .invokestatic(api, "extensionTab", MethodTypeDesc.of(ClassDesc.of(ExtensionTab.class.getName()), string))
                        .pop()
                        .return_())
                .withMethodBody("configTab", MethodTypeDesc.of(ConstantDescs.CD_boolean), flags, code -> code
                        .ldc("propwash:foreign_tab")
                        .ldc("config tab")
                        .invokestatic(rules, "refuse", MethodTypeDesc.of(ConstantDescs.CD_boolean, string, string))
                        .ireturn()));
        return new Loader(RefusalOriginTest.class.getClassLoader()).define(FOREIGN_CALLER, bytes);
    }

    private static void install(ModOrigins origins) {
        NamespaceGuard.overrideOrigins(origins);
        GuardModule.listen();
    }

    private static Identifier pw(String path) {
        return Identifier.fromNamespaceAndPath("propwash", path);
    }

    private static WorkbenchExtension extension(String path) {
        Identifier id = pw(path);
        return new WorkbenchExtension() {
            @Override
            public Identifier id() {
                return id;
            }

            @Override
            public void init(WorkbenchContext context) {
            }
        };
    }

    private static List<String> refused(String mod) {
        return NamespaceGuard.report().categories(mod).getOrDefault(GuardCategory.REFUSED, List.of());
    }

    private static final class Loader extends ClassLoader {
        Loader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    private static final class ForeignAddon {
        static void run(Runnable action) {
            action.run();
        }

        static void register(WorkbenchExtension extension) {
            PropwashClientApi.registerWorkbenchExtension(extension);
        }
    }

    private record FakeOrigins(boolean resolveOwn, Map<Class<?>, String> mods) implements ModOrigins {
        @Override
        public Optional<String> modOfClass(Class<?> type) {
            String mapped = this.mods.get(type);
            if (mapped != null) {
                return Optional.of(mapped);
            }
            if (type.getClassLoader() == null) {
                return Optional.of("java");
            }
            return this.resolveOwn && type.getName().startsWith(OWN_PREFIX) ? Optional.of("propwash") : Optional.empty();
        }

        @Override
        public Optional<String> modOfPack(PackResources pack) {
            return Optional.empty();
        }

        @Override
        public Optional<String> modOfMixinConfig(String config) {
            return Optional.empty();
        }

        @Override
        public boolean platform(String modId) {
            return "java".equals(modId) || "minecraft".equals(modId);
        }

        @Override
        public List<String> modIds() {
            return List.of();
        }

        @Override
        public void visitFiles(String modId, String folder, FileVisitor visitor) {
        }

        @Override
        public Map<String, Optional<String>> networkChannels(boolean client) {
            return Map.of();
        }

        @Override
        public boolean addCrashSection(String title, Supplier<String> body) {
            return false;
        }
    }
}
