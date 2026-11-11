package de.lardtom.propwash.dev;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSmokeStopTest {
    private static final String PASS = "PASS dedizierter Server, Beitrittstest beendet";

    private static ServerSmoke.ServerOnlyCheck check(String label, boolean enabled, String failure) {
        return new ServerSmoke.ServerOnlyCheck(label, enabled, () -> failure);
    }

    @Test
    void holdRunningOutBeforeTheServerOnlyCheckIsDoneFails() {
        List<ServerSmoke.ServerOnlyCheck> checks = List.of(
                check("Autonomie ohne Besitzer", false, "nach 0 von 20 Durchgängen nicht fertig"),
                check("Weiter Flug ohne Pilot", true, "nach 2 von 5 Durchgängen nicht fertig"),
                check("Heimflug außer Reichweite", false, "nach 0 von 2 Durchgängen nicht fertig"));
        assertEquals("FAIL Weiter Flug ohne Pilot: nach 2 von 5 Durchgängen nicht fertig", ServerSmoke.stopResult(null, checks));
    }

    @Test
    void finishedServerOnlyCheckWithFailuresFails() {
        List<ServerSmoke.ServerOnlyCheck> checks = List.of(
                check("Autonomie ohne Besitzer", true, "3 (Chunk-Kante): Drohne entladen"),
                check("Heimflug außer Reichweite", true, null));
        assertEquals("FAIL Autonomie ohne Besitzer: 3 (Chunk-Kante): Drohne entladen", ServerSmoke.stopResult(null, checks));
    }

    @Test
    void finishedChecksAndSwitchedOffChecksPass() {
        List<ServerSmoke.ServerOnlyCheck> checks = List.of(
                check("Autonomie ohne Besitzer", false, "nach 0 von 20 Durchgängen nicht fertig"),
                check("Weiter Flug ohne Pilot", true, null),
                check("Heimflug außer Reichweite", true, null));
        assertEquals(PASS, ServerSmoke.stopResult(null, checks));
        assertEquals(PASS, ServerSmoke.stopResult(null, List.of()));
    }

    @Test
    void earlierFailureStaysTheReason() {
        List<ServerSmoke.ServerOnlyCheck> checks = List.of(check("Heimflug außer Reichweite", true, "nach 1 von 2 Durchgängen nicht fertig"));
        assertEquals("FAIL Zonen: Zone fehlt", ServerSmoke.stopResult("Zonen: Zone fehlt", checks));
    }

    @Test
    void everyServerOnlySwitchIsAskedAtTheStop() {
        List<String> switches = List.of(JoinTestEnv.AUTONOMY_OFFLINE_SIM, JoinTestEnv.AUTONOMY_FAR, JoinTestEnv.AUTONOMY_HOME);
        List<String> labels = List.of("Autonomie ohne Besitzer", "Weiter Flug ohne Pilot", "Heimflug außer Reichweite");
        for (int i = 0; i < switches.size(); i++) {
            String property = switches.get(i).toLowerCase(Locale.ROOT).replace('_', '.');
            String before = System.getProperty(property);
            System.setProperty(property, "1");
            try {
                String result = ServerSmoke.stopResult(null, ServerSmoke.serverOnlyChecks());
                assertTrue(result.startsWith("FAIL " + labels.get(i) + ": nach 0 von "), result);
                assertTrue(result.endsWith(" Durchgängen nicht fertig"), result);
            } finally {
                if (before == null) {
                    System.clearProperty(property);
                } else {
                    System.setProperty(property, before);
                }
            }
        }
    }
}
