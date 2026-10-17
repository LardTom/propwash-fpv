package de.lardtom.propwash.video.client.record;

import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.video.record.path.CameraTrack;
import de.lardtom.propwash.video.record.path.PathPreview;
import de.lardtom.propwash.video.record.path.Simplification;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class SimplificationText {
    public static final String KEY = "propwash.video.flashback.simplify.";
    public static final String PREVIEW = "propwash.video.flashback.preview.";

    private SimplificationText() {
    }

    public static Component centimetres(double metres) {
        double cm = metres * 100.0;
        return Numbers.of(cm, cm < 10.0 ? 1 : 0);
    }

    public static Component degrees(double degrees) {
        return Numbers.of(degrees, degrees < 10.0 ? 1 : 0);
    }

    public static Component distance(double metres) {
        if (metres < 1.0) {
            return Component.translatable(KEY + "cm", centimetres(metres));
        }
        return Component.translatable(KEY + "blocks", Numbers.of(metres, metres < 10.0 ? 2 : 1));
    }

    public static Component value(Simplification simplification) {
        if (simplification.off()) {
            return Component.translatable(KEY + "off");
        }
        return Component.translatable(KEY + "value", centimetres(simplification.position()), degrees(simplification.angle()));
    }

    public static Component metrics(PathPreview preview) {
        return Component.translatable(PREVIEW + "metrics", preview.keyframes(), Math.round(preview.fill() * 100.0), distance(preview.maxOffPath()),
                distance(preview.maxSameTime()), degrees(preview.maxAngle()));
    }

    public static @Nullable Component cap(PathPreview preview) {
        CameraTrack.Built built = preview.built();
        if (built.capped()) {
            return Component.translatable(PREVIEW + "capped", preview.keyframes(), centimetres(built.used().position()), degrees(built.used().angle()));
        }
        if (!preview.fits()) {
            return Component.translatable(PREVIEW + "too_big", Math.round(preview.fill() * 100.0));
        }
        return null;
    }

    public static Component fits(PathPreview preview, boolean limit) {
        return Component.translatable(PREVIEW + (limit ? "fits_limit" : "fits"), duration(preview.secondsThatFit()));
    }

    public static Component duration(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.0) {
            return Component.translatable("propwash.content.number.none");
        }
        return Numbers.minutes(seconds / 60.0);
    }
}
