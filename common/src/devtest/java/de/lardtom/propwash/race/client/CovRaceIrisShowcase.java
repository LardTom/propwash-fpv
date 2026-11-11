package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.platform.Services;

import java.util.Map;

public final class CovRaceIrisShowcase {
    public static final String PREFIX = "bildschirmfotos: Iris Rennsystem – ";
    private static final String API = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String CONFIG = "net.irisshaders.iris.api.v0.IrisApiConfig";
    private static final String SWITCHED = "cov_race_iris_switched";
    private static final int SETTLE_TICKS = 40;
    private static boolean startedWithShaders;
    private static boolean remembered;

    private CovRaceIrisShowcase() {
    }

    static void register(Screenshots.Scene fpv, Screenshots.Scene thirdPerson) {
        if (!Screenshots.enabled() || !(Services.PLATFORM.isModLoaded("iris") || Services.PLATFORM.isModLoaded("oculus"))) {
            return;
        }
        SelfTest.clientStep(PREFIX + "Shaderpack an", 600, ctx -> shaders(ctx, true));
        Screenshots.step(PREFIX + "Wertungsflächen im FPV mit Shaderpack", "race_cov_areas_fpv_shader", 60, fpv);
        Screenshots.step(PREFIX + "Wertungsflächen in der dritten Person mit Shaderpack", "race_cov_areas_third_person_shader", 60, thirdPerson);
        SelfTest.clientStep(PREFIX + "Shaderpack aus", 600, ctx -> shaders(ctx, false));
        Screenshots.step(PREFIX + "Wertungsflächen im FPV ohne Shaderpack", "race_cov_areas_fpv_vanilla", 40, fpv);
        Screenshots.step(PREFIX + "Wertungsflächen in der dritten Person ohne Shaderpack", "race_cov_areas_third_person_vanilla", 40, thirdPerson);
        SelfTest.clientStep(PREFIX + "Ausgangszustand", 600, CovRaceIrisShowcase::restore);
    }

    private static Object config() throws ReflectiveOperationException {
        Object api = Class.forName(API).getMethod("getInstance").invoke(null);
        return Class.forName(API).getMethod("getConfig").invoke(api);
    }

    private static boolean shadersEnabled() {
        try {
            return Boolean.TRUE.equals(Class.forName(CONFIG).getMethod("areShadersEnabled").invoke(config()));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static boolean packInUse() {
        try {
            Object api = Class.forName(API).getMethod("getInstance").invoke(null);
            return Boolean.TRUE.equals(Class.forName(API).getMethod("isShaderPackInUse").invoke(api));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    private static void enable(boolean enabled) throws ReflectiveOperationException {
        Class.forName(CONFIG).getMethod("setShadersEnabledAndApply", boolean.class).invoke(config(), enabled);
    }

    private static StepResult shaders(SelfTest.StepContext ctx, boolean enabled) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(SWITCHED)) {
            if (!remembered) {
                startedWithShaders = shadersEnabled();
                remembered = true;
            }
            if (shadersEnabled() != enabled) {
                try {
                    enable(enabled);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return StepResult.fail("Iris-Shader nicht umschaltbar: " + e);
                }
            }
            state.put(SWITCHED, ctx.tick());
            return StepResult.pending();
        }
        if (ctx.tick() - (int) state.get(SWITCHED) < SETTLE_TICKS) {
            return StepResult.pending();
        }
        if (enabled != packInUse()) {
            return StepResult.fail(enabled ? "kein Shaderpack aktiv" : "Shaderpack bleibt aktiv");
        }
        ctx.log("Iris: Shaderpack " + (enabled ? "an" : "aus"));
        return StepResult.pass();
    }

    private static StepResult restore(SelfTest.StepContext ctx) {
        if (remembered && startedWithShaders != shadersEnabled()) {
            try {
                enable(startedWithShaders);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return StepResult.fail("Iris-Shader nicht zurückgestellt: " + e);
            }
        }
        remembered = false;
        return StepResult.pass();
    }
}
