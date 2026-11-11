package de.lardtom.propwash.gpucompat.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.textures.GpuTexture;
import de.lardtom.propwash.gpucompat.GpuPaths;
import net.minecraft.client.Screenshot;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public final class GuardedScreenshot {
    private static GpuPaths.ReadPath lastPath = GpuPaths.ReadPath.GPU;

    private GuardedScreenshot() {
    }

    public static GpuPaths.ReadPath lastPath() {
        return lastPath;
    }

    public static void take(RenderTarget target, Consumer<NativeImage> image, Consumer<String> failed) {
        GpuTexture texture = target.getColorTexture();
        if (texture == null || target.width <= 0 || target.height <= 0) {
            failed.accept("Bild ohne Farbtextur");
            return;
        }
        GpuPaths.ReadPath path = GuardedReadback.path();
        lastPath = path;
        switch (path) {
            case GPU -> Screenshot.takeScreenshot(target, 1, image);
            case CLASSIC -> {
                NativeImage read = classic(texture, target.width, target.height);
                if (read == null) {
                    failed.accept("Ersatzweg glReadPixels fehlgeschlagen");
                } else {
                    image.accept(read);
                }
            }
            case NONE -> {
                GpuPathCheck.announceBroken();
                failed.accept("kein Rücklese-Weg liefert ein Bild (" + GpuPathCheck.health().describe() + ")");
            }
        }
    }

    private static @Nullable NativeImage classic(GpuTexture texture, int width, int height) {
        ClassicReadback reader = new ClassicReadback();
        ByteBuffer out = MemoryUtil.memAlloc(width * height * 4);
        try {
            if (!reader.read(texture, width, height, out)) {
                return null;
            }
            NativeImage image = new NativeImage(width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setPixelABGR(x, height - y - 1, out.getInt((x + y * width) * 4) | 0xFF000000);
                }
            }
            return image;
        } finally {
            reader.close();
            MemoryUtil.memFree(out);
        }
    }
}
