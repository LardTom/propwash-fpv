package de.lardtom.propwash.video.client.record;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.gpucompat.client.GpuFaults;
import de.lardtom.propwash.gpucompat.client.GpuPathCheck;
import de.lardtom.propwash.video.record.ImagePath;

import java.util.List;
import java.util.function.Supplier;

public final class CompatActionCamSelfTests {
    static final String PREFIX = "compat: Action-Cam";
    private static final String FAULTS = "compat_faults";

    private CompatActionCamSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(PREFIX + " mit GPU-Rücklesen mit Nullen (Treiber-Ebene) – Aufnahme mit Bild über den Ersatzweg", 2400,
                ctx -> withFaults(ctx, false, () -> ActionCamImageSelfTests.faultStep(ctx, ActionCamCapture.Fault.NONE, ImagePath.READ,
                        List.of(ImagePath.GPU))));
        SelfTest.clientStep(PREFIX + " ohne Rücklese-Weg (GPU und klassisch Nullen) – Abbruch mit Meldung statt grünem Video", 2400,
                ctx -> withFaults(ctx, true, () -> ActionCamImageSelfTests.abortStep(ctx, ActionCamCapture.Fault.NONE)));
    }

    private static StepResult withFaults(SelfTest.StepContext ctx, boolean classic, Supplier<StepResult> body) {
        if (!ctx.state().containsKey(FAULTS)) {
            ctx.state().put(FAULTS, Boolean.TRUE);
            GpuFaults.set(false, true, classic);
            ctx.log("Fehler-Injektion: " + GpuFaults.describe() + " → " + GpuPathCheck.recheck().describe());
        }
        StepResult result;
        try {
            result = body.get();
        } catch (RuntimeException exception) {
            reset();
            throw exception;
        }
        if (result.status() != StepResult.Status.PENDING) {
            ctx.log("abgefangene Rücklesungen " + GpuFaults.zeroedReads() + ", klassisch gelesen " + GpuFaults.classicReads());
            reset();
        }
        return result;
    }

    private static void reset() {
        GpuFaults.clear();
        GpuPathCheck.recheck();
    }
}
