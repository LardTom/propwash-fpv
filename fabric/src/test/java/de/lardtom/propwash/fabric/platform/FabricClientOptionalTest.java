package de.lardtom.propwash.fabric.platform;

import de.lardtom.propwash.platform.ModMeta;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricClientOptionalTest {
    private static ModMetadata metadata(ModEnvironment environment, @Nullable CustomValue custom) {
        return (ModMetadata) Proxy.newProxyInstance(ModMetadata.class.getClassLoader(), new Class<?>[]{ModMetadata.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getEnvironment" -> environment;
            case "getCustomValue" -> ModMeta.CLIENT_KEY.equals(args[0]) ? custom : null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static CustomValue text(String value) {
        return (CustomValue) Proxy.newProxyInstance(CustomValue.class.getClassLoader(), new Class<?>[]{CustomValue.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getType" -> CustomValue.CvType.STRING;
            case "getAsString" -> value;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    @Test
    void serverEnvironmentNeedsNoClient() {
        assertTrue(FabricPlatformHelper.clientOptional(metadata(ModEnvironment.SERVER, null)));
    }

    @Test
    void customMarkNeedsNoClient() {
        assertTrue(FabricPlatformHelper.clientOptional(metadata(ModEnvironment.UNIVERSAL, text(ModMeta.CLIENT_OPTIONAL))));
    }

    @Test
    void otherAddonsStayRequired() {
        assertFalse(FabricPlatformHelper.clientOptional(metadata(ModEnvironment.UNIVERSAL, null)));
        assertFalse(FabricPlatformHelper.clientOptional(metadata(ModEnvironment.CLIENT, null)));
        assertFalse(FabricPlatformHelper.clientOptional(metadata(ModEnvironment.UNIVERSAL, text("required"))));
    }
}
