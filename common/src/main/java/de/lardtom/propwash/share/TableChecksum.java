package de.lardtom.propwash.share;

import de.lardtom.propwash.api.part.PartKind;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.zip.CRC32;

public final class TableChecksum {
    public static final int MASK = (1 << Pw3Params.TABLE_CHECKSUM_BITS) - 1;

    private TableChecksum() {
    }

    public record Line(PartKind kind, int number, String path) {
        public Line {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(path, "path");
            if (number < 1) {
                throw new IllegalArgumentException("Nummer ab 1: " + number);
            }
        }

        public String text() {
            return this.kind.key() + ":" + this.number + ":" + this.path + "\n";
        }
    }

    public static List<Line> canonical(Collection<Line> lines) {
        List<Line> sorted = new ArrayList<>(new LinkedHashSet<>(lines));
        sorted.sort(Comparator.<Line>comparingInt(l -> Pw3Params.KINDS.indexOf(l.kind())).thenComparingInt(Line::number).thenComparing(Line::path));
        return sorted;
    }

    public static String text(Collection<Line> lines) {
        StringBuilder out = new StringBuilder();
        for (Line line : canonical(lines)) {
            out.append(line.text());
        }
        return out.toString();
    }

    public static int of(Collection<Line> lines) {
        CRC32 crc = new CRC32();
        crc.update(text(lines).getBytes(StandardCharsets.UTF_8));
        return (int) (crc.getValue() & MASK);
    }
}
