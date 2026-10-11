package de.lardtom.propwash.compat.iris.client;

import de.lardtom.propwash.platform.Services;

final class IrisControl {
    private static final String API = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String CONFIG = "net.irisshaders.iris.api.v0.IrisApiConfig";

    private IrisControl() {
    }

    static boolean loaded() {
        return Services.PLATFORM.isModLoaded("iris") || Services.PLATFORM.isModLoaded("oculus");
    }

    static boolean packInUse() {
        try {
            return Boolean.TRUE.equals(Class.forName(API).getMethod("isShaderPackInUse").invoke(api()));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    static boolean shadersEnabled() {
        try {
            return Boolean.TRUE.equals(Class.forName(CONFIG).getMethod("areShadersEnabled").invoke(config()));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }

    static void enableShaders(boolean enabled) throws ReflectiveOperationException {
        Class.forName(CONFIG).getMethod("setShadersEnabledAndApply", boolean.class).invoke(config(), enabled);
    }

    private static Object api() throws ReflectiveOperationException {
        return Class.forName(API).getMethod("getInstance").invoke(null);
    }

    private static Object config() throws ReflectiveOperationException {
        return Class.forName(API).getMethod("getConfig").invoke(api());
    }
}
