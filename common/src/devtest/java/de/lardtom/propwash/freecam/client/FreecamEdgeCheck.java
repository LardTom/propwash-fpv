package de.lardtom.propwash.freecam.client;

import de.lardtom.propwash.freecam.FreecamBounds;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

final class FreecamEdgeCheck {
    static final int MAX_CHUNKS = 64;

    private FreecamEdgeCheck() {
    }

    static int edgeX(FreecamBounds.Columns columns, int chunkX, int chunkZ) {
        int edge = chunkX;
        while (columns.loaded(edge + 1, chunkZ) && edge - chunkX < MAX_CHUNKS) {
            edge++;
        }
        return (edge + 1) * 16;
    }

    static @Nullable String outside(FreecamBounds.Columns columns, int playerChunkX, int playerChunkZ, double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            return String.format(Locale.ROOT, "x %.2f z %.2f nicht endlich", x, z);
        }
        int cx = FreecamBounds.chunk(x);
        int cz = FreecamBounds.chunk(z);
        if (columns.loaded(cx, cz)) {
            return null;
        }
        return String.format(Locale.ROOT, "x %.2f z %.2f, Chunk %d/%d nicht geladen, Grenze der Reihe %d bei x %d, der Spieler-Reihe %d bei x %d", x, z,
                cx, cz, cz, edgeX(columns, playerChunkX, cz), playerChunkZ, edgeX(columns, playerChunkX, playerChunkZ));
    }

    static String rows(FreecamBounds.Columns columns, int playerChunkX, double fromZ, double toZ) {
        int from = FreecamBounds.chunk(fromZ);
        int to = FreecamBounds.chunk(toZ);
        String first = String.format(Locale.ROOT, "Chunk-Reihe %d mit Grenze x %d", from, edgeX(columns, playerChunkX, from));
        if (from == to) {
            return first;
        }
        return first + String.format(Locale.ROOT, ", Reihe %d mit Grenze x %d", to, edgeX(columns, playerChunkX, to));
    }
}
