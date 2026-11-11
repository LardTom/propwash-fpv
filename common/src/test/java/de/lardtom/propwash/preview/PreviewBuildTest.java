package de.lardtom.propwash.preview;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class PreviewBuildTest {
    private static final String EXPECTED_PROPERTY = "propwash.preview.expected";
    private static final String FLAG_FILE = "/META-INF/propwash-build.properties";

    @Test
    void enabledIsTheBuildConstant() {
        boolean expected = Boolean.parseBoolean(System.getProperty(EXPECTED_PROPERTY, "false"));
        assertEquals(PreviewSwitch.ENABLED, PreviewBuild.ENABLED);
        assertEquals(PreviewBuild.ENABLED, PreviewBuild.enabled());
        assertEquals(expected, PreviewBuild.enabled(), "Konstante passt nicht zum Build-Schalter propwash_preview=" + expected);
    }

    @Test
    void textIsFixedEnglishWithTheBuildVersion() {
        assertEquals(PreviewBuild.watermark(PreviewSwitch.VERSION), PreviewBuild.TEXT);
        assertEquals("Propwash Preview 0.6.0-rc.1 · not final", PreviewBuild.watermark("0.6.0-rc.1"));
        assertEquals("Propwash Preview 0.6.0-rc.12 · not final", PreviewBuild.watermark(" 0.6.0-rc.12 "));
        assertEquals("Propwash Preview ? · not final", PreviewBuild.watermark(""));
    }

    @Test
    void windowTitleIsFixedEnglishWithTheBuildVersion() throws ReflectiveOperationException {
        Method title;
        Field constant;
        try {
            title = PreviewBuild.class.getMethod("windowTitle", String.class);
            constant = PreviewBuild.class.getField("TITLE");
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            fail("PreviewBuild hat keinen festen Fenstertitel (windowTitle(String), TITLE): " + e);
            return;
        }
        int modifiers = constant.getModifiers();
        assertTrue(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers),
                "PreviewBuild.TITLE nicht public static final");
        assertEquals("Propwash Preview 0.6.0-rc.1", title.invoke(null, "0.6.0-rc.1"));
        assertEquals("Propwash Preview 0.6.0-rc.12", title.invoke(null, " 0.6.0-rc.12 "));
        assertEquals("Propwash Preview ?", title.invoke(null, ""));
        assertEquals(title.invoke(null, PreviewSwitch.VERSION), constant.get(null));
        assertEquals(PreviewBuild.TEXT, constant.get(null) + " · not final", "Fenstertitel und Wasserzeichen nennen nicht dieselbe Version");
    }

    @Test
    void informationalJarFlagMatchesTheConstant() throws IOException {
        try (InputStream in = PreviewBuildTest.class.getResourceAsStream(FLAG_FILE)) {
            if (in == null) {
                return;
            }
            Properties flag = new Properties();
            flag.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            String value = flag.getProperty("preview", "").trim();
            if (value.equals("${propwash_preview}")) {
                return;
            }
            assertEquals(Boolean.toString(PreviewBuild.ENABLED), value, "Kennzeichen " + FLAG_FILE + " im Jar widerspricht der Konstante");
        }
    }
}
