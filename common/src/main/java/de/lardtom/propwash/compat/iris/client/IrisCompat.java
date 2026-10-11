package de.lardtom.propwash.compat.iris.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.platform.Services;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;

public final class IrisCompat {
    private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String IRIS_PROGRAM = "net.irisshaders.iris.api.v0.IrisProgram";
    private static final String IRIS_SHADOW_PROGRAM = "net.irisshaders.iris.api.v0.IrisShadowProgram";
    private static final long CHECK_INTERVAL_NANOS = 1_000_000_000L;

    private static boolean loadedChecked;
    private static boolean irisPresent;
    private static boolean unavailable;
    private static @Nullable Object api;
    private static @Nullable Method inUseMethod;
    private static long nextCheckNanos;
    private static boolean inUse;
    private static boolean pipelineWarned;
    private static boolean shadowPassUnavailable;
    private static @Nullable Object shadowPassApi;
    private static @Nullable Method shadowPassMethod;

    private IrisCompat() {
    }

    private static boolean present() {
        if (!loadedChecked) {
            loadedChecked = true;
            irisPresent = Services.PLATFORM.isModLoaded("iris") || Services.PLATFORM.isModLoaded("oculus");
        }
        return irisPresent;
    }

    public static boolean shaderPackActive(long now) {
        if (!present() || unavailable) {
            return false;
        }
        if (now - nextCheckNanos < 0L) {
            return inUse;
        }
        nextCheckNanos = now + CHECK_INTERVAL_NANOS;
        try {
            if (api == null || inUseMethod == null) {
                Class<?> type = Class.forName(IRIS_API);
                api = type.getMethod("getInstance").invoke(null);
                inUseMethod = type.getMethod("isShaderPackInUse");
            }
            inUse = Boolean.TRUE.equals(inUseMethod.invoke(api));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            unavailable = true;
            inUse = false;
            Constants.LOG.warn("Video-Effekt: Shaderpack-Status von Iris nicht lesbar ({})", exception.toString());
        }
        return inUse;
    }

    public static boolean renderingShadowPass() {
        if (shadowPassUnavailable || !present()) {
            return false;
        }
        try {
            if (shadowPassApi == null || shadowPassMethod == null) {
                Class<?> type = Class.forName(IRIS_API);
                shadowPassApi = type.getMethod("getInstance").invoke(null);
                shadowPassMethod = type.getMethod("isRenderingShadowPass");
            }
            return Boolean.TRUE.equals(shadowPassMethod.invoke(shadowPassApi));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            shadowPassUnavailable = true;
            Constants.LOG.warn("Iris: Schattenpass nicht erkennbar ({})", exception.toString());
            return false;
        }
    }

    public static void assignPipeline(RenderPipeline pipeline, String program, String shadowProgram) {
        if (!present()) {
            return;
        }
        try {
            Class<?> type = Class.forName(IRIS_API);
            Object instance = type.getMethod("getInstance").invoke(null);
            Class<?> programType = Class.forName(IRIS_PROGRAM);
            type.getMethod("assignPipeline", RenderPipeline.class, programType).invoke(instance, pipeline, constant(programType, program));
            Class<?> shadowType = Class.forName(IRIS_SHADOW_PROGRAM);
            type.getMethod("assignPipelineShadow", RenderPipeline.class, shadowType).invoke(instance, pipeline, constant(shadowType, shadowProgram));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            if (!pipelineWarned) {
                pipelineWarned = true;
                Constants.LOG.warn("Iris: Shader für {} nicht zugeordnet ({})", pipeline.getLocation(), exception.toString());
            }
        }
    }

    private static Object constant(Class<?> type, String name) throws ReflectiveOperationException {
        return type.getMethod("valueOf", String.class).invoke(null, name);
    }
}
