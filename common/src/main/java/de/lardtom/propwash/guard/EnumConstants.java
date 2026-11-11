package de.lardtom.propwash.guard;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EnumConstants {
    private EnumConstants() {
    }

    public static boolean isEnum(byte[] classFile) {
        ClassModel model = ClassFile.of().parse(classFile);
        return model.flags().has(AccessFlag.ENUM);
    }

    public static List<String> declared(byte[] classFile) {
        ClassModel model = ClassFile.of().parse(classFile);
        List<String> out = new ArrayList<>();
        for (FieldModel field : model.fields()) {
            if (field.flags().has(AccessFlag.ENUM)) {
                out.add(field.fieldName().stringValue());
            }
        }
        return out;
    }

    public static List<String> extra(List<String> declared, Object[] runtime) {
        List<String> out = new ArrayList<>();
        if (runtime == null) {
            return out;
        }
        Set<String> known = new HashSet<>(declared);
        for (Object constant : runtime) {
            String name = constant instanceof Enum<?> value ? value.name() : String.valueOf(constant);
            if (!known.contains(name)) {
                out.add(name);
            }
        }
        return out;
    }
}
