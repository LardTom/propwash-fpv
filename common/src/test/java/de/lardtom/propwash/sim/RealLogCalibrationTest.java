package de.lardtom.propwash.sim;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RealLogCalibrationTest {
    static final String DECODED_ENV = "PROPWASH_BBCAL_DECODED";
    static final String SOURCES_ENV = "PROPWASH_BBCAL_SOURCES";
    static final String OUT_ENV = "PROPWASH_BBCAL_OUT";

    @Test
    void compareRealLogsWithTheFlightModel() {
        String decoded = System.getenv(DECODED_ENV);
        String sources = System.getenv(SOURCES_ENV);
        String out = System.getenv(OUT_ENV);
        assumeTrue(decoded != null && sources != null && out != null, "tools/blackbox_calibration.sh setzt " + DECODED_ENV + ", " + SOURCES_ENV
                + " und " + OUT_ENV);
        List<RealLogCalibration.Row> rows = new ArrayList<>();
        for (RealLogCalibration.Source s : RealLogCalibration.sources(Path.of(sources))) {
            RealLogCalibration.Row r = RealLogCalibration.analyze(s, Path.of(decoded), Path.of(out));
            rows.add(r);
            System.out.println(String.format(Locale.ROOT, "%-28s %-10s %s", s.id(), r.status, String.join("; ", r.problems)));
        }
        RealLogReport.write(rows, Path.of(out), "Erzeugt von `tools/blackbox_calibration.sh` aus `tools/bbcal/sources.json`.");
        assertFalse(rows.isEmpty());
    }
}
