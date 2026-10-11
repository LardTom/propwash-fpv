package de.lardtom.propwash.video.client.record;

import com.mojang.blaze3d.platform.NativeImage;
import de.lardtom.propwash.Constants;
import net.minecraft.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

public final class RecordingThumbnails {
    private static CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

    private RecordingThumbnails() {
    }

    public static synchronized CompletableFuture<Void> write(int[] argb, int width, int height, Path target) {
        return enqueue(() -> {
            try (NativeImage image = new NativeImage(width, height, false)) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        image.setPixel(x, y, argb[y * width + x]);
                    }
                }
                Files.createDirectories(target.getParent());
                Path temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
                image.writeToFile(temp);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        });
    }

    public static CompletableFuture<Void> move(Path from, Path to) {
        if (from.equals(to)) {
            return enqueue(() -> {
            });
        }
        return enqueue(() -> {
            if (Files.exists(from)) {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            }
        });
    }

    public static CompletableFuture<Void> delete(Path file) {
        return enqueue(() -> Files.deleteIfExists(file));
    }

    public static CompletableFuture<Void> flushed() {
        synchronized (RecordingThumbnails.class) {
            return chain;
        }
    }

    public static CompletableFuture<NativeImage> read(Path file) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream in = Files.newInputStream(file)) {
                return NativeImage.read(in);
            } catch (IOException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }, Util.ioPool());
    }

    private static synchronized CompletableFuture<Void> enqueue(IoTask task) {
        chain = chain.handle((ignored, error) -> null).thenRunAsync(() -> {
            try {
                task.run();
            } catch (IOException | RuntimeException e) {
                Constants.LOG.warn("Action-Cam: Vorschaubild: {}", e.getMessage());
            }
        }, Util.ioPool());
        return chain;
    }

    @FunctionalInterface
    private interface IoTask {
        void run() throws IOException;
    }
}
