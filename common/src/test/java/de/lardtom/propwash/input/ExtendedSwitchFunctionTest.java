package de.lardtom.propwash.input;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtendedSwitchFunctionTest {
    private static final List<String> EXTENDED_BY_ADDONS = List.of("de/lardtom/propwash/input/SwitchFunction",
            "de/lardtom/propwash/osd/OsdElement", "de/lardtom/propwash/input/client/screen/ModesTab$Row");
    private static final String SWITCH_MAP = "$SwitchMap$";
    private static final String MATCH_EXCEPTION = "java/lang/MatchException";
    private static final String EXTRA = "ADDON_EXTRA";
    private static final String OWN_PACKAGE = "de.lardtom.propwash.";

    private record SwitchSite(String type, String where, boolean throwsOnUnknown) {
    }

    private static List<SwitchSite> switchSites() throws IOException, URISyntaxException {
        Path location = Path.of(SwitchFunction.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isDirectory(location)) {
            return switchSites(location);
        }
        try (FileSystem jar = FileSystems.newFileSystem(location)) {
            return switchSites(jar.getPath("/"));
        }
    }

    private static List<SwitchSite> switchSites(Path root) throws IOException {
        List<Path> classes;
        try (Stream<Path> walk = Files.walk(root)) {
            classes = walk.filter(p -> p.toString().endsWith(".class")).sorted().toList();
        }
        List<SwitchSite> out = new ArrayList<>();
        for (Path file : classes) {
            ClassModel model = ClassFile.of().parse(Files.readAllBytes(file));
            for (MethodModel method : model.methods()) {
                method.code().ifPresent(code -> out.addAll(switchSites(model, method, code)));
            }
        }
        return out;
    }

    private static List<SwitchSite> switchSites(ClassModel model, MethodModel method, CodeModel code) {
        List<CodeElement> elements = new ArrayList<>();
        code.forEach(elements::add);
        Map<Label, Integer> labels = new HashMap<>();
        for (int i = 0; i < elements.size(); i++) {
            if (elements.get(i) instanceof LabelTarget target) {
                labels.put(target.label(), i);
            }
        }
        List<SwitchSite> out = new ArrayList<>();
        String selector = null;
        int line = -1;
        for (CodeElement element : elements) {
            if (element instanceof LineNumber number) {
                line = number.line();
            }
            if (element instanceof FieldInstruction field && field.name().stringValue().startsWith(SWITCH_MAP)) {
                selector = extendedType(field.name().stringValue().substring(SWITCH_MAP.length()).replace('$', '/'));
            }
            if (element instanceof InvokeInstruction invoke && invoke.name().equalsString("ordinal")
                    && EXTENDED_BY_ADDONS.contains(invoke.owner().asInternalName())) {
                selector = invoke.owner().asInternalName();
            }
            Label fallback = element instanceof TableSwitchInstruction table ? table.defaultTarget()
                    : element instanceof LookupSwitchInstruction lookup ? lookup.defaultTarget() : null;
            if (fallback == null) {
                continue;
            }
            if (selector != null) {
                String where = model.thisClass().asInternalName() + "#" + method.methodName().stringValue() + " line " + line;
                out.add(new SwitchSite(selector, where, throwsAt(elements, labels.get(fallback))));
            }
            selector = null;
        }
        return out;
    }

    private static String extendedType(String flattened) {
        for (String type : EXTENDED_BY_ADDONS) {
            if (type.replace('$', '/').equals(flattened)) {
                return type;
            }
        }
        return null;
    }

    private static boolean throwsAt(List<CodeElement> elements, Integer start) {
        if (start == null) {
            return false;
        }
        for (int i = start + 1; i < elements.size(); i++) {
            CodeElement element = elements.get(i);
            if (element instanceof NewObjectInstruction created) {
                return created.className().asInternalName().equals(MATCH_EXCEPTION);
            }
            if (element instanceof Instruction) {
                return false;
            }
        }
        return false;
    }

    @Test
    void noSwitchOverAnEnumThatAddonsExtendThrowsForAnUnknownValue() throws IOException, URISyntaxException {
        List<SwitchSite> sites = switchSites();
        List<String> found = sites.stream().map(SwitchSite::where).toList();
        assertTrue(found.stream().anyMatch(where -> where.startsWith("de/lardtom/propwash/input/RemoteDemo#forSwitch")), found.toString());
        assertTrue(found.stream().anyMatch(where -> where.startsWith("de/lardtom/propwash/input/client/FunctionKeys#entry")), found.toString());
        assertTrue(found.stream().anyMatch(where -> where.startsWith("de/lardtom/propwash/input/SwitchFunction#supportsToggle")), found.toString());
        assertTrue(sites.stream().anyMatch(site -> site.type().endsWith("OsdElement")), found.toString());
        assertTrue(sites.stream().anyMatch(site -> site.type().endsWith("ModesTab$Row")), found.toString());
        List<String> throwing = sites.stream().filter(SwitchSite::throwsOnUnknown).map(site -> site.type() + " in " + site.where()).toList();
        assertEquals(List.of(), throwing, "switch ohne default über einen Enum, den Add-ons per Mixin erweitern");
    }

    private static final class ExtendedLoader extends ClassLoader {
        private final ClassLoader source;

        ExtendedLoader(ClassLoader source) {
            super(source);
            this.source = source;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(OWN_PACKAGE)) {
                return super.loadClass(name, resolve);
            }
            synchronized (this.getClassLoadingLock(name)) {
                Class<?> loaded = this.findLoadedClass(name);
                if (loaded == null) {
                    byte[] bytes = this.bytes(name);
                    if (name.equals(SwitchFunction.class.getName())) {
                        bytes = withExtraConstant(bytes);
                    }
                    loaded = this.defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) {
                    this.resolveClass(loaded);
                }
                return loaded;
            }
        }

        private byte[] bytes(String name) throws ClassNotFoundException {
            try (InputStream in = this.source.getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (in == null) {
                    throw new ClassNotFoundException(name);
                }
                return in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static byte[] withExtraConstant(byte[] original) {
        ClassFile files = ClassFile.of();
        ClassModel model = files.parse(original);
        assertTrue(model.methods().stream().anyMatch(m -> m.methodName().equalsString("$values")), "SwitchFunction ohne $values");
        ClassDesc owner = model.thisClass().asSymbol();
        ClassDesc array = owner.arrayType();
        MethodTypeDesc extendType = MethodTypeDesc.of(array, array);
        ClassDesc arrays = ClassDesc.of("java.util.Arrays");
        MethodTypeDesc copyOf = MethodTypeDesc.of(ConstantDescs.CD_Object.arrayType(), ConstantDescs.CD_Object.arrayType(), ConstantDescs.CD_int);
        ClassTransform values = ClassTransform.transformingMethodBodies(m -> m.methodName().equalsString("$values"), (code, element) -> {
            if (element instanceof ReturnInstruction) {
                code.invokestatic(owner, "test$extend", extendType);
            }
            code.with(element);
        });
        ClassTransform extend = ClassTransform.endHandler(builder -> builder.withMethodBody("test$extend", extendType,
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, code -> code
                        .aload(0).aload(0).arraylength().iconst_1().iadd()
                        .invokestatic(arrays, "copyOf", copyOf)
                        .checkcast(array).astore(1)
                        .aload(1).aload(0).arraylength()
                        .new_(owner).dup().ldc(EXTRA).aload(0).arraylength()
                        .invokespecial(owner, ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_int))
                        .aastore()
                        .aload(1).areturn()));
        return files.transformClass(model, values.andThen(extend));
    }

    private static final class Extended {
        private final ClassLoader loader = new ExtendedLoader(ExtendedSwitchFunctionTest.class.getClassLoader());
        private final Class<?> switchFunction;
        private final Object extra;

        Extended() throws ReflectiveOperationException {
            this.switchFunction = this.loader.loadClass(SwitchFunction.class.getName());
            Object[] values = (Object[]) this.switchFunction.getMethod("values").invoke(null);
            this.extra = values[values.length - 1];
        }

        Object call(String type, String method, Object... args) throws Throwable {
            Class<?> owner = this.loader.loadClass(OWN_PACKAGE + type);
            for (Method candidate : owner.getMethods()) {
                if (candidate.getName().equals(method) && candidate.getParameterCount() == args.length && Modifier.isStatic(candidate.getModifiers())) {
                    try {
                        return candidate.invoke(null, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
            }
            throw new NoSuchMethodException(type + "#" + method);
        }

        Object on(String method) throws Throwable {
            try {
                return this.switchFunction.getMethod(method).invoke(this.extra);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static Object component(Object record, String name) throws ReflectiveOperationException {
        return record.getClass().getMethod(name).invoke(record);
    }

    @Test
    void anAppendedFunctionIsAPlainFlightFunctionWithoutToggleOrKey() throws Throwable {
        Extended extended = new Extended();
        assertEquals(EXTRA, ((Enum<?>) extended.extra).name());
        assertEquals(SwitchFunction.values().length, ((Enum<?>) extended.extra).ordinal());
        assertFalse((Boolean) extended.on("command"));
        assertEquals(-1, extended.on("commandIndex"));
        assertFalse((Boolean) extended.on("accessoryAction"));
        assertFalse((Boolean) extended.on("supportsToggle"));
        assertFalse((Boolean) extended.on("latchesOnGamepad"));
        assertTrue(((List<?>) extended.switchFunction.getMethod("flightFunctions").invoke(null)).contains(extended.extra));
        List<?> commands = (List<?>) extended.switchFunction.getMethod("commands").invoke(null);
        assertEquals(SwitchFunction.COMMAND_COUNT, commands.size());
        assertFalse(commands.contains(extended.extra));
    }

    @Test
    void theRemoteDemoShowsAnAppendedFunctionOnAPlainSwitch() throws Throwable {
        Extended extended = new Extended();
        Object onRadio = extended.call("input.RemoteDemo", "forSwitch", extended.extra, false);
        assertEquals("SWITCH", component(onRadio, "motion").toString());
        assertEquals(2, component(onRadio, "index"));
        Object onGamepad = extended.call("input.RemoteDemo", "forSwitch", extended.extra, true);
        assertEquals("BUTTON", component(onGamepad, "motion").toString());
        assertEquals(RemoteDemo.MOMENTARY_BUTTON, component(onGamepad, "index"));
    }

    @Test
    void anAppendedFunctionHasAnUnboundKeyOfItsOwn() throws Throwable {
        Extended extended = new Extended();
        Object entry = extended.call("input.client.FunctionKeys", "entry", extended.extra);
        assertEquals("addon_extra", component(entry, "name"));
        assertEquals(0, component(entry, "key"));
        assertEquals("key.propwash.addon_extra", entry.getClass().getMethod("mappingName").invoke(entry));
    }

    @Test
    void aStoredBindingOfAnAppendedFunctionSurvivesSanitizingWithoutToggle() throws Throwable {
        Extended extended = new Extended();
        Class<?> profileType = extended.loader.loadClass(OWN_PACKAGE + "input.DeviceProfile");
        Class<?> assignmentType = extended.loader.loadClass(OWN_PACKAGE + "input.SwitchAssignment");
        Object profile = profileType.getConstructor().newInstance();
        Object binding = assignmentType.getMethod("button", int.class, boolean.class).invoke(null, 3, false);
        Field toggle = assignmentType.getField("toggle");
        toggle.setBoolean(binding, true);
        @SuppressWarnings("unchecked")
        Map<String, Object> switches = (Map<String, Object>) profileType.getField("switches").get(profile);
        switches.put("addon_extra", binding);
        try {
            profileType.getMethod("sanitize").invoke(profile);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> clean = (Map<String, Object>) profileType.getField("switches").get(profile);
        assertTrue(clean.containsKey("addon_extra"), clean.keySet().toString());
        assertFalse(toggle.getBoolean(clean.get("addon_extra")));
    }
}
