package de.lardtom.propwash.video.client;

import de.lardtom.propwash.video.link.ObstructionScan;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CovFreecamVideoProbe {
    private CovFreecamVideoProbe() {
    }

    public static double lossDb(Level level, Vec3 from, Vec3 to) {
        ClientBlockLoss blocks = new ClientBlockLoss();
        blocks.bind(level);
        ObstructionScan scan = new ObstructionScan();
        scan.start(from.x, from.y, from.z, to.x, to.y, to.z);
        return scan.evaluate(blocks);
    }

    public static @Nullable String disturbance() {
        VideoState video = VideoState.INSTANCE;
        List<String> problems = new ArrayList<>();
        if (video.noiseLevel() > 0.0F) {
            problems.add(String.format(Locale.ROOT, "Rauschen %.3f", video.noiseLevel()));
        }
        if (video.videoLost()) {
            problems.add("Bild verloren");
        }
        if (video.noSignal()) {
            problems.add("kein Signal");
        }
        if (video.frozen()) {
            problems.add("Bild eingefroren");
        }
        if (video.osdShift() != 0.0F) {
            problems.add("OSD verschoben");
        }
        return problems.isEmpty() ? null : String.join(", ", problems) + " (Status " + video.status() + ")";
    }

    public static String status() {
        return VideoState.INSTANCE.status().name();
    }
}
