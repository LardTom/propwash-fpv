package de.lardtom.propwash.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabErrorReportTest {
    @Test
    void summaryNamesTheExceptionEvenWithoutMessage() {
        assertEquals("java.lang.NullPointerException", TabErrorReport.summary(new NullPointerException()));
        assertEquals("java.lang.IllegalStateException: kaputt", TabErrorReport.summary(new IllegalStateException("kaputt")));
    }

    @Test
    void reportStartsWithVersionLoaderAndTab() {
        String report = TabErrorReport.of("0.5.0", "Fabric", "receiver", new NullPointerException("text is null"));
        String first = report.lines().findFirst().orElseThrow();
        assertEquals("Propwash FPV 0.5.0 (Fabric), tab receiver", first);
        assertTrue(report.contains("java.lang.NullPointerException: text is null"), report);
        assertTrue(report.contains("\tat " + TabErrorReportTest.class.getName()), report);
        assertFalse(report.endsWith("\n"));
    }

    @Test
    void framesAreCappedAndCountedSoTheClipboardStaysSmall() {
        RuntimeException error = new RuntimeException("tief");
        StackTraceElement[] frames = new StackTraceElement[TabErrorReport.MAX_FRAMES + 10];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new StackTraceElement("de.lardtom.Probe", "m" + i, "Probe.java", i + 1);
        }
        error.setStackTrace(frames);
        String report = TabErrorReport.of("0.5.0", "NeoForge", "osd", error);
        assertEquals(TabErrorReport.MAX_FRAMES, report.lines().filter(line -> line.startsWith("\tat ")).count());
        assertTrue(report.contains("\t... 10 more"), report);
    }

    @Test
    void causesAreChainedAndLimited() {
        Throwable error = new IllegalArgumentException("0");
        for (int i = 1; i <= TabErrorReport.MAX_CAUSES + 3; i++) {
            error = new RuntimeException(String.valueOf(i), error);
        }
        String report = TabErrorReport.of("0.5.0", "Fabric", "modes", error);
        assertEquals(TabErrorReport.MAX_CAUSES, report.lines().filter(line -> line.startsWith("Caused by: ")).count());
    }

    @Test
    void selfReferencingCauseDoesNotLoop() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        String report = TabErrorReport.of("0.5.0", "Fabric", "rates", b);
        assertEquals(1, report.lines().filter(line -> line.startsWith("Caused by: ")).count());
    }
}
