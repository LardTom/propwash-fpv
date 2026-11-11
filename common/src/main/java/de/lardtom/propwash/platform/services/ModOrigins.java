package de.lardtom.propwash.platform.services;

import net.minecraft.server.packs.PackResources;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public interface ModOrigins {
    Optional<String> modOfClass(Class<?> type);

    Optional<String> modOfPack(PackResources pack);

    Optional<String> modOfMixinConfig(String config);

    default Optional<String> activeMod() {
        return Optional.empty();
    }

    boolean platform(String modId);

    List<String> modIds();

    void visitFiles(String modId, String folder, FileVisitor visitor);

    Map<String, Optional<String>> networkChannels(boolean client);

    boolean addCrashSection(String title, Supplier<String> body);

    @FunctionalInterface
    interface FileVisitor {
        void visit(String path, Opener opener);
    }

    @FunctionalInterface
    interface Opener {
        InputStream open() throws IOException;
    }
}
