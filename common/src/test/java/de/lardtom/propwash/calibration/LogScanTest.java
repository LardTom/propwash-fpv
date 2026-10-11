package de.lardtom.propwash.calibration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LogScanTest {
    static final String ENV = "PROPWASH_BBCAL_SCAN";

    @Test
    void scanDecodedCandidates() throws IOException {
        String dir = System.getenv(ENV);
        assumeTrue(dir != null && !dir.isBlank(), ENV + " nicht gesetzt");
        List<String> lines = new ArrayList<>();
        lines.add("log\tFirmware\tCraft\tDauer_s\tRate\tAcc\tRPM\tSchweben_s\tDuty\tVbat\tRPM_h\tPunches\tCuts\tCutsLevel\tVerworfen");
        List<Path> csvs;
        try (Stream<Path> walk = Files.walk(Path.of(dir))) {
            csvs = walk.filter(p -> p.toString().matches(".*\\.\\d\\d\\.csv(\\.gz)?")).sorted().toList();
        }
        for (Path csv : csvs) {
            Path headers = Path.of(csv.toString().replaceAll("\\.csv(\\.gz)?$", ".headers.csv"));
            if (!Files.exists(headers)) {
                continue;
            }
            try {
                LogHeader h = LogHeader.readCsv(headers);
                Flight f = Flight.of(h, LogData.readCsv(csv));
                Maneuvers.Found m = Maneuvers.find(f);
                StringBuilder rej = new StringBuilder();
                m.rejected.forEach((k, v) -> rej.append(k).append('=').append(v).append("; "));
                lines.add(String.format(Locale.ROOT, "%s\t%s\t%s\t%.0f\t%.0f\t%s\t%s\t%.1f\t%.3f\t%.2f\t%.0f\t%d\t%d\t%d\t%s",
                        Path.of(dir).relativize(csv), h.versionText(), h.craft(), f.durationSeconds(), f.rate, f.hasAcc, f.hasRpm,
                        m.hoverSeconds, m.hoverDuty, m.hoverVbat, m.hoverRpm, m.punches.size(), m.cuts.size(), m.levelCuts.size(), rej));
            } catch (RuntimeException e) {
                lines.add(Path.of(dir).relativize(csv) + "\tFEHLER " + e);
            }
        }
        Files.write(Path.of(dir).resolve("scan.tsv"), lines);
    }
}
