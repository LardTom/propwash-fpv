package de.lardtom.propwash.gpucompat.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.gpucompat.GpuCompatMode;
import de.lardtom.propwash.gpucompat.GpuPaths;
import de.lardtom.propwash.video.client.GpuInfo;
import de.lardtom.propwash.video.client.VideoPass;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Locale;

public final class GpuPathCheck {
    private static final int TEXTURE_USAGE = GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING
            | GpuTexture.USAGE_RENDER_ATTACHMENT;
    private static final int READ_USAGE = GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST;
    private static final Vector4fc CLEAR = new Vector4f(0.0F, 0.0F, 0.0F, 0.0F);
    private static GpuPaths.Health health = GpuPaths.Health.UNKNOWN;
    private static int runs;
    private static boolean announced;

    private GpuPathCheck() {
    }

    public static GpuPaths.Health health() {
        return health;
    }

    public static int runs() {
        return runs;
    }

    public static boolean announced() {
        return announced;
    }

    public static GpuPaths.Health ensure() {
        if (runs == 0) {
            run();
        }
        return health;
    }

    public static GpuPaths.Health recheck() {
        run();
        return health;
    }

    public static void announceBroken() {
        if (announced) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        announced = true;
        minecraft.gui.hud.getChat().addClientSystemMessage(Component.translatable("propwash.gpucompat.read.broken.chat",
                Component.translatable("propwash.gpucompat.setting"), Component.translatable(GpuCompatMode.ON.translationKey()))
                .withStyle(ChatFormatting.RED));
    }

    private static void run() {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }
        long start = Util.getNanos();
        GpuDevice device = RenderSystem.getDevice();
        boolean opengl = "OpenGL".equals(device.getDeviceInfo().backendName());
        byte[] expected = GpuPaths.pattern();
        int bytes = GpuPaths.bytes();
        GpuPaths.Result gpu = GpuPaths.Result.UNTESTED;
        GpuPaths.Result classic = GpuPaths.Result.UNTESTED;
        GpuPaths.Result blit = GpuPaths.Result.UNTESTED;
        GpuPaths.Result shader = GpuPaths.Result.UNTESTED;
        String detail = opengl ? null : "kein OpenGL";
        GpuTexture source = null;
        GpuTexture blitTarget = null;
        GpuTexture shaderTarget = null;
        GpuTextureView sourceView = null;
        GpuTextureView shaderView = null;
        ClassicReadback reader = new ClassicReadback();
        ByteBuffer upload = MemoryUtil.memAlloc(bytes);
        try {
            if (opengl) {
                CommandEncoder encoder = device.createCommandEncoder();
                source = texture(device, "source");
                upload.put(0, expected);
                encoder.writeToTexture(source, upload, 0, 0, 0, 0, GpuPaths.WIDTH, GpuPaths.HEIGHT);
                gpu = GpuPaths.compare(expected, gpuRead(device, source));
                classic = GpuPaths.compare(expected, classicRead(reader, source));
                blitTarget = texture(device, "blit");
                encoder.clearColorTexture(blitTarget, CLEAR);
                encoder.copyTextureToTexture(source, blitTarget, 0, 0, 0, 0, 0, GpuPaths.WIDTH, GpuPaths.HEIGHT);
                blit = verify(device, reader, blitTarget, expected, gpu, classic);
                CompiledRenderPipeline simple = RenderSystem.getCompiledPipelineNullable(VideoPass.SIMPLE_PIPELINE);
                if (simple != null) {
                    shaderTarget = texture(device, "shader");
                    encoder.clearColorTexture(shaderTarget, CLEAR);
                    sourceView = device.createTextureView(source);
                    shaderView = device.createTextureView(shaderTarget);
                    VideoPass.simple(simple, sourceView, shaderView, "propwash gpu path shader copy");
                    shader = verify(device, reader, shaderTarget, expected, gpu, classic);
                }
            }
        } catch (RuntimeException exception) {
            detail = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        } finally {
            reader.close();
            MemoryUtil.memFree(upload);
            close(shaderView);
            close(sourceView);
            close(shaderTarget);
            close(blitTarget);
            close(source);
        }
        health = new GpuPaths.Health(gpu, classic, blit, shader, detail);
        runs++;
        double millis = (Util.getNanos() - start) / 1.0E6;
        String line = String.format(Locale.ROOT, "Grafikwege geprüft (%.1f ms): %s; Grafik: %s; Grafik-Kompatibilität: %s", millis,
                health.describe(), GpuInfo.describe(), GpuCompatHooks.status().decision().describe());
        if (health.anyBroken()) {
            Constants.LOG.warn(line);
        } else {
            Constants.LOG.info(line);
        }
    }

    private static GpuTexture texture(GpuDevice device, String name) {
        return device.createTexture(() -> "propwash gpu path " + name, TEXTURE_USAGE, GpuFormat.RGBA8_UNORM, GpuPaths.WIDTH, GpuPaths.HEIGHT, 1, 1);
    }

    private static GpuPaths.Result verify(GpuDevice device, ClassicReadback reader, GpuTexture texture, byte[] expected, GpuPaths.Result gpu,
                                          GpuPaths.Result classic) {
        if (classic == GpuPaths.Result.OK) {
            return GpuPaths.compare(expected, classicRead(reader, texture));
        }
        if (gpu == GpuPaths.Result.OK) {
            return GpuPaths.compare(expected, gpuRead(device, texture));
        }
        return GpuPaths.Result.UNTESTED;
    }

    static byte @Nullable [] gpuRead(GpuDevice device, GpuTexture texture) {
        int bytes = GpuPaths.bytes();
        GpuBuffer buffer = device.createBuffer(() -> "propwash gpu path read", READ_USAGE, bytes);
        try {
            device.createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
            }, 0);
            GL11C.glFinish();
            try (GpuBufferSlice.MappedView view = buffer.map(0L, bytes, true, false)) {
                byte[] data = new byte[bytes];
                view.data().get(0, data);
                return data;
            }
        } finally {
            buffer.close();
        }
    }

    private static byte @Nullable [] classicRead(ClassicReadback reader, GpuTexture texture) {
        if (!ClassicReadback.supported(texture)) {
            return null;
        }
        ByteBuffer out = MemoryUtil.memAlloc(GpuPaths.bytes());
        try {
            if (!reader.read(texture, GpuPaths.WIDTH, GpuPaths.HEIGHT, out)) {
                return null;
            }
            byte[] data = new byte[GpuPaths.bytes()];
            out.get(0, data);
            return data;
        } finally {
            MemoryUtil.memFree(out);
        }
    }

    private static void close(@Nullable AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
