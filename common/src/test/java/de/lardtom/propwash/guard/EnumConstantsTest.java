package de.lardtom.propwash.guard;

import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.osd.OsdElement;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumConstantsTest {
    private enum Probe {
        NSPROBE
    }

    private static byte[] classFile(Class<?> type) throws IOException {
        try (InputStream in = type.getResourceAsStream(type.getName().substring(type.getName().lastIndexOf('.') + 1) + ".class")) {
            assertNotNull(in, type.getName());
            return in.readAllBytes();
        }
    }

    private static List<String> names(Enum<?>[] values) {
        List<String> out = new ArrayList<>();
        for (Enum<?> value : values) {
            out.add(value.name());
        }
        return out;
    }

    @Test
    void originalConstantsMatchValuesWithoutMixins() throws IOException {
        assertEquals(names(SwitchFunction.values()), EnumConstants.declared(classFile(SwitchFunction.class)));
        assertEquals(names(OsdElement.values()), EnumConstants.declared(classFile(OsdElement.class)));
        assertTrue(EnumConstants.isEnum(classFile(SwitchFunction.class)));
        assertTrue(EnumConstants.isEnum(classFile(OsdElement.class)));
        assertFalse(EnumConstants.isEnum(classFile(GuardReport.class)));
        assertEquals(List.of(), EnumConstants.extra(EnumConstants.declared(classFile(SwitchFunction.class)), SwitchFunction.values()));
    }

    @Test
    void artificialExtraConstantIsAFinding() throws IOException {
        Object[] runtime = Arrays.copyOf(SwitchFunction.values(), SwitchFunction.values().length + 1, Object[].class);
        runtime[runtime.length - 1] = Probe.NSPROBE;
        assertEquals(List.of("NSPROBE"), EnumConstants.extra(EnumConstants.declared(classFile(SwitchFunction.class)), runtime));
        Object[] osd = Arrays.copyOf(OsdElement.values(), OsdElement.values().length + 1, Object[].class);
        osd[osd.length - 1] = Probe.NSPROBE;
        assertEquals(List.of("NSPROBE"), EnumConstants.extra(EnumConstants.declared(classFile(OsdElement.class)), osd));
    }

    @Test
    void nestedEnumConstantsAndMissingRuntimeAreHandled() throws IOException {
        assertEquals(List.of("NSPROBE"), EnumConstants.declared(classFile(Probe.class)));
        assertEquals(List.of(), EnumConstants.extra(List.of("A"), null));
    }
}
