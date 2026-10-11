package de.lardtom.propwash.blackbox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlackboxFirmwareRevisionTest {
    private static final Pattern EXPLORER_2025_12 = Pattern.compile("(.*flight).* (\\d+)\\.(\\d+)(\\.(\\d+))*", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLORER_MASTER = Pattern.compile("((?:Beta|Race|Clean|Base|Butter)flight)\\s+(\\d+)\\.(\\d+)(?:\\.(\\d+))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DECODE = Pattern.compile("^Betaflight (\\d+)\\.(\\d+)\\.(\\d+)");

    @AfterEach
    void reset() {
        BlackboxSetup.producerVersion(null);
    }

    @Test
    void everyDecoderReadsTheCompatibleBetaflightVersionNotOurs() {
        for (String version : new String[]{"0.5.0", "1.2.3", "10.20.30", "0.5.0-beta.1"}) {
            BlackboxSetup.producerVersion(version);
            String header = BlackboxSetup.firmwareRevision();
            assertEquals("Betaflight 4.5.1 compatible (Propwash v" + version + ")", header);
            Matcher released = EXPLORER_2025_12.matcher(header);
            assertTrue(released.find(), header);
            assertEquals("4.5", released.group(2) + "." + released.group(3), "Blackbox Explorer 2025.12.1: " + header);
            assertEquals("1", released.group(5), "Blackbox Explorer 2025.12.1: " + header);
            Matcher master = EXPLORER_MASTER.matcher(header);
            assertTrue(master.find(), header);
            assertEquals("4.5.1", master.group(2) + "." + master.group(3) + "." + master.group(4), "Blackbox Explorer master: " + header);
            Matcher decode = DECODE.matcher(header);
            assertTrue(decode.find(), "blackbox_decode: " + header);
            assertEquals("4.5.1", decode.group(1) + "." + decode.group(2) + "." + decode.group(3));
        }
        BlackboxSetup.producerVersion(" ");
        assertEquals("Betaflight 4.5.1 compatible (Propwash)", BlackboxSetup.firmwareRevision());
    }
}
