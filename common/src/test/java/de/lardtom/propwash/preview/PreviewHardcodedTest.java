package de.lardtom.propwash.preview;

import de.lardtom.propwash.testutil.TestPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewHardcodedTest {
    private static final String EXPECTED_PROPERTY = "propwash.preview.expected";
    private static final String SWITCH = "de.lardtom.propwash.preview.PreviewSwitch";
    private static final String BUILD = "de.lardtom.propwash.preview.PreviewBuild";
    private static final String SWITCH_INTERNAL = "de/lardtom/propwash/preview/PreviewSwitch";
    private static final String BUILD_INTERNAL = "de/lardtom/propwash/preview/PreviewBuild";
    private static final String PACKAGE = "de/lardtom/propwash/preview/";
    private static final String MARK = "de/lardtom/propwash/preview/client/PreviewMark";
    private static final String MIXIN = "de/lardtom/propwash/mixin/core/client/GuiMixin";
    private static final String MIXIN_HANDLER = "propwash$previewMark";
    private static final String PIPELINE = "de/lardtom/propwash/preview/client/PreviewPipeline";
    private static final String PIPELINE_MIXIN = "de/lardtom/propwash/mixin/core/client/RenderSystemMixin";
    private static final String TITLE_MIXIN = "de/lardtom/propwash/mixin/core/client/MinecraftMixin";
    private static final List<Hook> HOOKS = List.of(
            new Hook(MIXIN, MIXIN_HANDLER, MARK + ".extract"),
            new Hook(PIPELINE_MIXIN, "propwash$previewPipeline", PIPELINE + ".lookup"),
            new Hook(TITLE_MIXIN, "propwash$previewTitle", BUILD_INTERNAL + "#TITLE"));
    private static final String GRAPHICS = "net/minecraft/client/gui/GuiGraphicsExtractor";
    private static final String DEFAULT_FILL = "(IIIII)V";
    private static final String PIPELINE_FILL = "(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;IIIII)V";
    private static final List<String> RESOURCE_SHADER_TYPES = List.of("net/minecraft/client/renderer/RenderPipelines",
            "net/minecraft/client/renderer/ShaderManager", "com/mojang/blaze3d/pipeline/PipelineCache");
    private static final String SWITCH_SOURCE = "src/main/java/de/lardtom/propwash/preview/PreviewSwitch.java";
    private static final List<String> MARK_ENTRY_POINTS = List.of("extract", "stampFrame", "stampImage");
    private static final List<String> FLAG_TOKENS = List.of("propwash-build", "Propwash-Preview", "propwash_preview", "PROPWASH_PREVIEW");
    private static final List<String> FORBIDDEN_TYPES = List.of(
            "java/util/Properties", "java/util/jar/", "java/util/zip/", "java/util/prefs/", "java/nio/file/", "java/io/File", "java/net/",
            "java/lang/Package", "java/lang/ProcessEnvironment",
            "net/minecraft/client/Options", "net/minecraft/client/OptionInstance", "net/minecraft/client/KeyMapping",
            "net/minecraft/client/gui/Font", "net/minecraft/client/gui/font/", "net/minecraft/network/chat/", "net/minecraft/locale/",
            "net/minecraft/client/resources/", "net/minecraft/resources/", "net/minecraft/client/renderer/texture/",
            "net/minecraft/server/packs/", "net/minecraft/commands/", "com/mojang/brigadier/",
            "de/lardtom/propwash/config/", "de/lardtom/propwash/platform/", "de/lardtom/propwash/input/", "de/lardtom/propwash/server/",
            "de/lardtom/propwash/ui/");
    private static final Map<String, List<String>> FORBIDDEN_MEMBERS = Map.of(
            "java/lang/System", List.of("getProperty", "getProperties", "getenv"),
            "java/lang/Boolean", List.of("getBoolean"),
            "java/lang/Integer", List.of("getInteger"),
            "java/lang/Long", List.of("getLong"),
            "java/lang/Class", List.of("getResource", "getResourceAsStream"),
            "java/lang/ClassLoader", List.of("getResource", "getResourceAsStream", "getResources", "getSystemResource",
                    "getSystemResourceAsStream", "getSystemResources"),
            "java/lang/Module", List.of("getResourceAsStream"));
    private static Map<String, ClassModel> mainClasses;

    private static boolean expected() {
        return Boolean.parseBoolean(System.getProperty(EXPECTED_PROPERTY, "false"));
    }

    private static Path classesRoot() {
        try {
            return Path.of(PreviewBuild.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static synchronized Map<String, ClassModel> mainClasses() {
        if (mainClasses != null) {
            return mainClasses;
        }
        Path root = classesRoot();
        assertTrue(Files.isDirectory(root), "Klassen von common liegen nicht in einem Verzeichnis: " + root);
        Path sources = TestPaths.common().resolve("src/main/java");
        Map<String, ClassModel> found = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                String name = root.relativize(file).toString().replace('\\', '/');
                name = name.substring(0, name.length() - ".class".length());
                int nested = name.indexOf('$');
                String outer = nested < 0 ? name : name.substring(0, nested);
                if (outer.endsWith("package-info") || outer.endsWith("module-info") || !Files.isRegularFile(sources.resolve(outer + ".java"))) {
                    continue;
                }
                found.put(name, ClassFile.of().parse(Files.readAllBytes(file)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertTrue(found.containsKey(BUILD_INTERNAL), "PreviewBuild nicht unter " + root);
        assertTrue(found.containsKey(MIXIN), "GuiMixin nicht unter " + root);
        mainClasses = found;
        return found;
    }

    private static ClassModel mainClass(String internalName) {
        ClassModel model = mainClasses().get(internalName);
        assertNotNull(model, "Klasse fehlt: " + internalName);
        return model;
    }

    private static boolean watermarkCode(String internalName) {
        return internalName.startsWith(PACKAGE) || internalName.equals(MIXIN) || internalName.equals(PIPELINE_MIXIN);
    }

    private static MethodModel method(ClassModel model, String name) {
        List<MethodModel> found = model.methods().stream().filter(method -> method.methodName().equalsString(name)).toList();
        assertEquals(1, found.size(), "Methode " + name + " in " + model.thisClass().asInternalName());
        return found.getFirst();
    }

    private static List<String> calls(MethodModel method) {
        List<String> calls = new ArrayList<>();
        CodeModel code = method.code().orElseThrow();
        for (CodeElement element : code) {
            if (element instanceof InvokeInstruction invoke) {
                calls.add(invoke.owner().asInternalName() + "." + invoke.name().stringValue());
            } else if (element instanceof FieldInstruction field) {
                calls.add(field.owner().asInternalName() + "#" + field.name().stringValue());
            }
        }
        return calls;
    }

    @Test
    void switchIsACompileTimeConstantInItsOwnClass() throws ReflectiveOperationException {
        Class<?> type = Class.forName(SWITCH);
        Field enabled = type.getField("ENABLED");
        Field version = type.getField("VERSION");
        for (Field field : List.of(enabled, version)) {
            int modifiers = field.getModifiers();
            assertTrue(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers), field + " nicht public static final");
        }
        assertEquals(boolean.class, enabled.getType());
        assertEquals(String.class, version.getType());
        ClassModel model = mainClass(SWITCH_INTERNAL);
        assertEquals(2, model.fields().size(), "PreviewSwitch soll nur ENABLED und VERSION enthalten");
        for (FieldModel field : model.fields()) {
            assertTrue(field.findAttribute(Attributes.constantValue()).isPresent(),
                    field.fieldName().stringValue() + " ist keine Konstante im Klassencode (ConstantValue fehlt)");
        }
        assertTrue(model.methods().stream().allMatch(method -> method.methodName().equalsString("<init>")),
                "PreviewSwitch darf keine Methoden haben");
    }

    @Test
    void noClassReadsTheSwitchAtRuntime() {
        for (Map.Entry<String, ClassModel> entry : mainClasses().entrySet()) {
            if (entry.getKey().equals(SWITCH_INTERNAL)) {
                continue;
            }
            for (PoolEntry pool : entry.getValue().constantPool()) {
                if (pool instanceof MemberRefEntry ref) {
                    String owner = ref.owner().asInternalName();
                    String member = ref.name().stringValue();
                    assertFalse(owner.equals(SWITCH_INTERNAL), entry.getKey() + " liest PreviewSwitch." + member + " zur Laufzeit statt als Konstante");
                    assertFalse(owner.equals(BUILD_INTERNAL) && member.equals("ENABLED"), entry.getKey() + " liest PreviewBuild.ENABLED zur Laufzeit");
                }
            }
        }
        for (FieldModel field : mainClass(BUILD_INTERNAL).fields()) {
            if (field.flags().has(AccessFlag.STATIC)) {
                assertTrue(field.flags().has(AccessFlag.FINAL), "PreviewBuild." + field.fieldName().stringValue() + " ist veränderlich");
            }
        }
        for (Method method : List.of(PreviewBuild.class.getDeclaredMethods())) {
            assertFalse(method.getName().equals("read") || method.getName().equals("fromClasspath"),
                    "PreviewBuild." + method.getName() + " liest ein Kennzeichen zur Laufzeit");
        }
    }

    @Test
    void noMainClassNamesAFlagFilePropertyOrVariable() {
        List<String> hits = new ArrayList<>();
        for (Map.Entry<String, ClassModel> entry : mainClasses().entrySet()) {
            for (PoolEntry pool : entry.getValue().constantPool()) {
                String text = pool instanceof Utf8Entry utf8 ? utf8.stringValue() : pool instanceof StringEntry string ? string.stringValue() : null;
                if (text == null) {
                    continue;
                }
                for (String token : FLAG_TOKENS) {
                    if (text.contains(token)) {
                        hits.add(entry.getKey() + ": „" + text + "“");
                    }
                }
                if (text.startsWith("propwash.preview")) {
                    hits.add(entry.getKey() + ": „" + text + "“");
                }
            }
        }
        assertTrue(hits.isEmpty(), "Hauptcode nennt Kennzeichen, Systemeigenschaft oder Umgebungsvariable des Vorschau-Schalters: " + hits);
    }

    @Test
    void watermarkCodeHasNoRuntimeSource() {
        List<String> hits = new ArrayList<>();
        for (Map.Entry<String, ClassModel> entry : mainClasses().entrySet()) {
            if (!watermarkCode(entry.getKey())) {
                continue;
            }
            for (PoolEntry pool : entry.getValue().constantPool()) {
                if (pool instanceof ClassEntry type) {
                    String name = type.asInternalName();
                    for (String forbidden : FORBIDDEN_TYPES) {
                        if (name.startsWith(forbidden) || name.startsWith("[L" + forbidden)) {
                            hits.add(entry.getKey() + " → " + name);
                        }
                    }
                } else if (pool instanceof MemberRefEntry ref) {
                    List<String> members = FORBIDDEN_MEMBERS.get(ref.owner().asInternalName());
                    if (members != null && members.contains(ref.name().stringValue())) {
                        hits.add(entry.getKey() + " → " + ref.owner().asInternalName() + "." + ref.name().stringValue());
                    }
                } else if (pool instanceof Utf8Entry utf8 && utf8.stringValue().contains("GameRule")) {
                    hits.add(entry.getKey() + " → " + utf8.stringValue());
                }
            }
        }
        assertTrue(hits.isEmpty(), "Wasserzeichen-Code fragt eine Laufzeitquelle ab (Datei, Systemeigenschaft, Umgebung, Einstellung, Taste, "
                + "Befehl, Spielregel, Sprachdatei, Schrift, Textur oder Mod-Metadaten): " + hits);
    }

    @Test
    void flagFilesAndSystemPropertiesCannotFlipTheSwitch() throws Exception {
        boolean expected = expected();
        String opposite = Boolean.toString(!expected);
        Path fake = Files.createTempDirectory("propwash-preview-flag");
        Path metaInf = Files.createDirectories(fake.resolve("META-INF"));
        Files.writeString(metaInf.resolve("propwash-build.properties"), "preview=" + opposite + "\n", StandardCharsets.UTF_8);
        Files.writeString(metaInf.resolve("MANIFEST.MF"), "Manifest-Version: 1.0\nPropwash-Preview: " + opposite + "\n", StandardCharsets.UTF_8);
        List<String> keys = List.of("propwash.preview", "propwash_preview", "propwash.preview.enabled", "propwash.selftest.preview", "PROPWASH_PREVIEW");
        Properties saved = new Properties();
        for (String key : keys) {
            String value = System.getProperty(key);
            if (value != null) {
                saved.setProperty(key, value);
            }
            System.setProperty(key, opposite);
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[]{fake.toUri().toURL(), classesRoot().toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            Class<?> build = Class.forName(BUILD, true, loader);
            assertTrue(build.getClassLoader() == loader, "PreviewBuild nicht frisch geladen");
            assertEquals(expected, build.getMethod("enabled").invoke(null),
                    "Kennzeichen-Datei, Manifest oder Systemeigenschaft mit " + opposite + " hat den Vorschau-Schalter umgestellt");
        } finally {
            for (String key : keys) {
                if (saved.containsKey(key)) {
                    System.setProperty(key, saved.getProperty(key));
                } else {
                    System.clearProperty(key);
                }
            }
            try (Stream<Path> files = Files.walk(fake)) {
                for (Path path : files.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    void releaseBuildLeavesTheWatermarkPathInactive() {
        boolean enabled = expected();
        ClassModel mark = mainClass(MARK);
        if (enabled) {
            for (Hook hook : HOOKS) {
                List<String> handler = calls(method(mainClass(hook.owner()), hook.handler()));
                assertTrue(handler.contains(hook.call()), "Vorschau-Build: " + hook.owner() + "." + hook.handler() + " ruft " + hook.call()
                        + " nicht auf: " + handler);
            }
            for (String name : MARK_ENTRY_POINTS) {
                assertFalse(calls(method(mark, name)).isEmpty(), "Vorschau-Build: PreviewMark." + name + " tut nichts");
            }
            return;
        }
        for (Hook hook : HOOKS) {
            List<String> handler = calls(method(mainClass(hook.owner()), hook.handler()));
            assertTrue(handler.isEmpty(), "Release-Build: " + hook.owner() + "." + hook.handler() + " ruft noch etwas auf: " + handler);
        }
        for (String name : MARK_ENTRY_POINTS) {
            List<String> body = calls(method(mark, name));
            assertTrue(body.isEmpty(), "Release-Build: PreviewMark." + name + " enthält noch Code: " + body);
        }
        List<String> callers = new ArrayList<>();
        for (Map.Entry<String, ClassModel> entry : mainClasses().entrySet()) {
            if (entry.getKey().startsWith(PACKAGE)) {
                continue;
            }
            for (PoolEntry pool : entry.getValue().constantPool()) {
                if (pool instanceof ClassEntry type && type.asInternalName().startsWith(PACKAGE) && !type.asInternalName().equals(BUILD_INTERNAL)) {
                    callers.add(entry.getKey() + " → " + type.asInternalName());
                }
            }
        }
        assertTrue(callers.isEmpty(), "Release-Build: Klassen außerhalb von preview verweisen noch auf das Wasserzeichen: " + callers);
    }

    @Test
    void watermarkIsDrawnOnlyWithItsOwnPipeline() {
        List<String> defaultFills = new ArrayList<>();
        int ownFills = 0;
        boolean usesOwnPipeline = false;
        for (MethodModel method : mainClass(MARK).methods()) {
            if (method.code().isEmpty()) {
                continue;
            }
            for (CodeElement element : method.code().orElseThrow()) {
                if (element instanceof InvokeInstruction invoke && invoke.owner().asInternalName().equals(GRAPHICS)) {
                    String name = invoke.name().stringValue();
                    String type = invoke.type().stringValue();
                    if (name.equals("fill") && type.equals(PIPELINE_FILL)) {
                        ownFills++;
                    } else if (name.startsWith("fill") || name.startsWith("text") || name.startsWith("blit")) {
                        defaultFills.add(method.methodName().stringValue() + " → " + name + type);
                    }
                } else if (element instanceof FieldInstruction field && field.owner().asInternalName().equals(PIPELINE)
                        && field.name().equalsString("PIPELINE")) {
                    usesOwnPipeline = true;
                }
            }
        }
        assertTrue(defaultFills.isEmpty(), "PreviewMark zeichnet mit der Vanilla-GUI-Pipeline, deren Shader ein Ressourcenpaket ersetzen kann: "
                + defaultFills);
        assertTrue(ownFills > 0 && usesOwnPipeline, "PreviewMark zeichnet nicht mit der eigenen Pipeline " + PIPELINE + ".PIPELINE");
        List<String> hits = new ArrayList<>();
        for (Map.Entry<String, ClassModel> entry : mainClasses().entrySet()) {
            if (!watermarkCode(entry.getKey())) {
                continue;
            }
            for (PoolEntry pool : entry.getValue().constantPool()) {
                if (pool instanceof ClassEntry type && RESOURCE_SHADER_TYPES.contains(type.asInternalName())) {
                    hits.add(entry.getKey() + " → " + type.asInternalName());
                }
            }
        }
        assertTrue(hits.isEmpty(), "Wasserzeichen-Code greift auf Pipelines oder Shader aus Ressourcen zu: " + hits);
    }

    @Test
    void committedSwitchIsTheReleaseDefault() throws IOException {
        Path source = TestPaths.common().resolve(SWITCH_SOURCE);
        assertTrue(Files.isRegularFile(source), "Vorgabe fehlt: " + source);
        String text = Files.readString(source, StandardCharsets.UTF_8);
        assertTrue(text.contains("    public static final boolean ENABLED = false;\n"), "Vorgabe ist nicht der Release (ENABLED = false): " + source);
        assertTrue(text.contains("    public static final String VERSION = \"\";\n"), "Vorgabe ohne leere VERSION: " + source);
    }

    @Test
    void switchMatchesTheBuild() throws ReflectiveOperationException, IOException {
        Class<?> type = Class.forName(SWITCH);
        boolean enabled = type.getField("ENABLED").getBoolean(null);
        String version = (String) type.getField("VERSION").get(null);
        assertEquals(expected(), enabled, "PreviewSwitch.ENABLED passt nicht zum Build-Schalter propwash_preview=" + expected());
        Properties gradle = new Properties();
        try (var in = Files.newBufferedReader(TestPaths.root().resolve("gradle.properties"), StandardCharsets.UTF_8)) {
            gradle.load(in);
        }
        String projectVersion = gradle.getProperty("version", "").trim();
        if (enabled) {
            assertEquals(projectVersion, version, "Vorschau-Build mit fremder Versionsnummer");
        } else {
            assertTrue(version.isEmpty() || version.equals(projectVersion), "Release mit fremder Versionsnummer: " + version);
        }
    }

    private record Hook(String owner, String handler, String call) {
    }
}
