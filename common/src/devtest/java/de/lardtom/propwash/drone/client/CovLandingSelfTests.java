package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class CovLandingSelfTests {
    public static final String GROUP = "drone: Landung bei 30 FPS und unbegrenzt";
    private static final int[] RATES = {30, 260};
    private static final int SETTLE_TICKS = 40;
    private static final AtomicInteger SAVED = new AtomicInteger(-1);
    private static final List<String> FPS = new ArrayList<>();

    private CovLandingSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + " – Vorlauf: eigene Drohne im Sichtflug", 300, LandingSelfTests::arenaStep);
        SelfTest.clientStep(GROUP + " – Vorlauf: Sitzung per Stopp beendet, Testfeld abgebaut", 200, LandingSelfTests::cleanupStep);
        SelfTest.clientStep(GROUP + " – Testfeld mit Stein, Gras und Stufe", 300, LandingSelfTests::arenaStep);
        for (int rate : RATES) {
            for (LandingSelfTests.Case c : LandingSelfTests.CASES) {
                SelfTest.clientStep(GROUP + " – " + label(rate) + ": " + c.name() + " bleibt aufrecht", 600, ctx -> landStep(ctx, rate, c));
            }
        }
        SelfTest.clientStep(GROUP + " – Bildrate zurück, Testfeld abbauen", 200, CovLandingSelfTests::cleanupStep);
    }

    private static String label(int rate) {
        return rate >= 260 ? "unbegrenzt" : rate + " FPS";
    }

    private static StepResult landStep(SelfTest.StepContext ctx, int rate, LandingSelfTests.Case c) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (SAVED.get() < 0) {
                SAVED.set(mc.options.framerateLimit().get());
            }
            if (mc.options.framerateLimit().get() != rate) {
                mc.options.framerateLimit().set(rate);
            }
            ctx.state().put("cov_fps_since", 0);
            return StepResult.pending();
        }
        if (ctx.tick() < SETTLE_TICKS) {
            return StepResult.pending();
        }
        if (!ctx.state().containsKey("cov_fps_measured")) {
            ctx.state().put("cov_fps_measured", mc.getFps());
        }
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (drone == null) {
            return StepResult.fail("keine Sitzung für die Landung");
        }
        StepResult result = LandingSelfTests.landStep(new Shifted(ctx, SETTLE_TICKS), drone.getId(), c);
        if (result.status() == StepResult.Status.PASS) {
            String line = String.format(Locale.ROOT, "%s (gemessen %d FPS, Grenze %d): %s aufrecht", label(rate), (int) ctx.state().get("cov_fps_measured"),
                    mc.options.framerateLimit().get(), c.name());
            synchronized (FPS) {
                FPS.add(line);
            }
            ctx.log(line);
        }
        return result;
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        int saved = SAVED.getAndSet(-1);
        if (ctx.tick() == 0 && saved >= 0) {
            mc.options.framerateLimit().set(saved);
        }
        StepResult result = LandingSelfTests.cleanupStep(ctx);
        if (result.status() == StepResult.Status.PASS) {
            synchronized (FPS) {
                ctx.log("Landungen bei " + FPS.size() + " Fällen aufrecht, Bildrate zurück auf " + mc.options.framerateLimit().get());
                FPS.clear();
            }
        }
        return result;
    }

    private record Shifted(SelfTest.StepContext inner, int offset) implements SelfTest.StepContext {
        @Override
        public int tick() {
            return this.inner.tick() - this.offset;
        }

        @Override
        public @Nullable MinecraftServer server() {
            return this.inner.server();
        }

        @Override
        public CompletableFuture<Void> onServer(Consumer<MinecraftServer> action) {
            return this.inner.onServer(action);
        }

        @Override
        public Map<String, Object> state() {
            return this.inner.state();
        }

        @Override
        public void log(String message) {
            this.inner.log(message);
        }
    }
}
