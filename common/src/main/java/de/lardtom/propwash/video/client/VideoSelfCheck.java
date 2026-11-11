package de.lardtom.propwash.video.client;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.gpucompat.GpuPaths;
import de.lardtom.propwash.gpucompat.client.GpuPathCheck;
import de.lardtom.propwash.gpucompat.client.GuardedReadback;
import de.lardtom.propwash.video.EffectParams;
import de.lardtom.propwash.video.FeedLook;
import de.lardtom.propwash.video.FisheyeLens;
import de.lardtom.propwash.video.ImageArea;
import de.lardtom.propwash.video.ImageFormat;
import de.lardtom.propwash.video.VideoCheck;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

final class VideoSelfCheck {
    private static final String DEBUG_EXTENSION = "GL_KHR_debug";
    private static final long TIMEOUT_NANOS = 10_000_000_000L;
    private static final int SOURCE_USAGE = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC;
    private static final Vector4f BLACK = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
    private static boolean announced;

    private @Nullable CompiledRenderPipeline checkedFeed;
    private @Nullable CompiledRenderPipeline checkedLens;
    private @Nullable CompiledRenderPipeline checkedSimple;
    private VideoCheck.Outcome outcome = VideoCheck.Outcome.PENDING;
    private @Nullable String detail;
    private @Nullable Run run;
    private int runs;
    private int generation;
    private boolean requested;

    VideoCheck.Outcome outcome() {
        return this.outcome;
    }

    @Nullable String detail() {
        return this.detail;
    }

    int runs() {
        return this.runs;
    }

    boolean running() {
        return this.run != null;
    }

    static boolean announced() {
        return announced;
    }

    void restart() {
        this.generation++;
        this.requested = true;
        this.checkedFeed = null;
        this.checkedLens = null;
        this.checkedSimple = null;
        this.outcome = VideoCheck.Outcome.PENDING;
        this.detail = null;
        Run pending = this.run;
        if (pending != null) {
            pending.close();
            this.run = null;
        }
    }

    void maybeRun(VideoPass pass, long now) {
        Run pending = this.run;
        if (pending != null) {
            if (now - pending.started > TIMEOUT_NANOS) {
                pending.close();
                this.run = null;
                this.finish(VideoCheck.Outcome.UNAVAILABLE, "keine Antwort der Grafikkarte nach " + TIMEOUT_NANOS / 1_000_000_000L + " s", null);
            }
            return;
        }
        CompiledRenderPipeline feed = RenderSystem.getCompiledPipelineNullable(VideoPass.PIPELINE);
        CompiledRenderPipeline lens = RenderSystem.getCompiledPipelineNullable(VideoPass.LENS_PIPELINE);
        CompiledRenderPipeline simple = RenderSystem.getCompiledPipelineNullable(VideoPass.SIMPLE_PIPELINE);
        if (feed == null || lens == null || simple == null) {
            return;
        }
        if (feed == this.checkedFeed && lens == this.checkedLens && simple == this.checkedSimple) {
            return;
        }
        String reason = this.requested ? "erneut angestoßen" : this.checkedFeed != null ? "nach Neuladen der Shader" : "erstes FPV-Bild";
        this.requested = false;
        this.checkedFeed = feed;
        this.checkedLens = lens;
        this.checkedSimple = simple;
        this.start(pass, feed, lens, simple, now, reason);
    }

    private void start(VideoPass pass, CompiledRenderPipeline feed, CompiledRenderPipeline lensPipeline, CompiledRenderPipeline simple, long now,
                       String reason) {
        this.runs++;
        List<String> before = debugMessages();
        Run created = new Run(now, this.generation, before);
        try {
            created.prepare();
            GpuTextureView a = created.view(Run.A);
            GpuTextureView b = created.view(Run.B);
            pass.copy(simple, created.sourceView(), a);
            VideoPass.drawLens(lensPipeline, a, b, created.uniform(0));
            pass.drawFeed(feed, b, b, created.uniform(1), created.view(Run.FULL), true);
            pass.drawFeed(feed, b, b, created.uniform(2), created.view(Run.NOISY), true);
            pass.copy(simple, a, created.view(Run.SIMPLE));
            this.run = created;
            Constants.LOG.info("Video-Bildprüfung gestartet ({}, Fehlerausgabe {}, Grafik: {})", reason, debugState(), GpuInfo.describe());
            created.readBack(this);
        } catch (RuntimeException exception) {
            created.close();
            if (this.run == created) {
                this.run = null;
            }
            this.finish(VideoCheck.Outcome.UNAVAILABLE, "Fehler beim Prüfen: " + exception, before);
        }
    }

    private void arrived(Run done) {
        if (this.run != done) {
            done.close();
            return;
        }
        this.run = null;
        byte[][] data = done.data;
        done.close();
        if (done.generation != this.generation) {
            return;
        }
        for (byte[] bytes : data) {
            if (bytes == null) {
                this.finish(VideoCheck.Outcome.UNAVAILABLE, done.path == GpuPaths.ReadPath.NONE
                        ? "Rücklesen: kein Weg liefert ein Bild (" + GpuPathCheck.health().describe() + ")" : "Rücklesen fehlgeschlagen", done.before);
                return;
            }
        }
        VideoCheck.Result evaluated = VideoCheck.evaluate(data[0], data[1], data[2], data[3]);
        this.finish(evaluated.outcome(), done.path == GpuPaths.ReadPath.GPU ? evaluated.detail()
                : evaluated.detail() + "; Rücklesen über " + done.path.text() + " (" + GpuPathCheck.health().describe() + ")", done.before);
    }

    private void finish(VideoCheck.Outcome value, String text, @Nullable List<String> before) {
        this.outcome = value;
        this.detail = text;
        VideoConfig.EffectRenderer renderer = VideoSettings.get().renderer;
        VideoCheck.Level level = renderer.level(value);
        String messages = newDebugMessages(before);
        switch (value) {
            case PASS -> Constants.LOG.info("Video-Bildprüfung bestanden: {}{}", text, messages);
            case UNAVAILABLE -> Constants.LOG.warn("Video-Bildprüfung nicht auswertbar, Videoeffekte bleiben, wie eingestellt: {}{} (Grafik: {})", text, messages,
                    GpuInfo.describe());
            default -> {
                String shown = level == VideoCheck.Level.FULL ? "volle Effekte" : level == VideoCheck.Level.SIMPLE ? "einfacher Ersatz-Shader" : "ohne Shader";
                if (renderer == VideoConfig.EffectRenderer.AUTO) {
                    Constants.LOG.warn("Videoeffekte auf dieser Grafikkarte vereinfacht ({}): {}{} (Grafik: {})", shown, text, messages, GpuInfo.describe());
                    this.announce();
                } else {
                    Constants.LOG.warn("Video-Bildprüfung: {}{} – Einstellung „{}“, also {} (Grafik: {})", text, messages,
                            renderer == VideoConfig.EffectRenderer.FULL ? "Voll" : "Einfach", shown, GpuInfo.describe());
                }
            }
        }
    }

    private void announce() {
        if (announced) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        announced = true;
        minecraft.gui.hud.getChat().addClientSystemMessage(Component.translatable("propwash.video.simplified.chat",
                Component.translatable("propwash.video.config.renderer")));
    }

    static String debugState() {
        try {
            DeviceInfo info = RenderSystem.getDevice().getDeviceInfo();
            if (!info.underlyingExtensions().contains(DEBUG_EXTENSION)) {
                return DEBUG_EXTENSION + " nicht verfügbar (" + info.backendName() + ")";
            }
            int verbosity = Minecraft.getInstance().options.glDebugVerbosity;
            return DEBUG_EXTENSION + " an, Stufe " + verbosity + " (glDebugVerbosity)";
        } catch (RuntimeException exception) {
            return "unbekannt (" + exception.getClass().getSimpleName() + ")";
        }
    }

    static List<String> debugMessages() {
        try {
            return new ArrayList<>(RenderSystem.getDevice().getLastDebugMessages());
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private static String newDebugMessages(@Nullable List<String> before) {
        if (before == null) {
            return "";
        }
        List<String> after = debugMessages();
        List<String> fresh = new ArrayList<>(after);
        fresh.removeAll(before);
        return fresh.isEmpty() ? "" : "; OpenGL-Meldungen: " + String.join(" | ", fresh);
    }

    static EffectParams cleanParams() {
        EffectParams params = new EffectParams();
        params.clearEffects();
        params.mode = FeedLook.CLEAN.shaderMode();
        return params;
    }

    static EffectParams noisyParams() {
        EffectParams params = cleanParams();
        params.mode = FeedLook.ANALOG.shaderMode();
        params.noise = VideoCheck.NOISE;
        params.chromaNoise = VideoCheck.NOISE * 0.3F;
        params.seed = 7.0F;
        params.timeSeconds = 1.0F;
        return params;
    }

    private static final class Run {
        private static final int A = 0;
        private static final int B = 1;
        private static final int FULL = 2;
        private static final int NOISY = 3;
        private static final int SIMPLE = 4;
        private static final String[] NAMES = {"a", "b", "full", "noisy", "simple"};
        private final long started;
        private final int generation;
        private final List<String> before;
        private final byte[][] data = new byte[4][];
        private final List<AutoCloseable> owned = new ArrayList<>();
        private final GpuBuffer[] uniforms = new GpuBuffer[3];
        private @Nullable GpuTexture source;
        private @Nullable GpuTextureView sourceView;
        private final @Nullable TextureTarget[] targets = new TextureTarget[NAMES.length];
        private int pending = 4;
        private GpuPaths.ReadPath path = GpuPaths.ReadPath.GPU;
        private boolean closed;

        private Run(long started, int generation, List<String> before) {
            this.started = started;
            this.generation = generation;
            this.before = before;
        }

        private void prepare() {
            GpuDevice device = RenderSystem.getDevice();
            GpuTexture texture = device.createTexture(() -> "propwash video check source", SOURCE_USAGE, GpuFormat.RGBA8_UNORM, VideoCheck.WIDTH,
                    VideoCheck.HEIGHT, 1, 1);
            this.owned.add(texture);
            this.source = texture;
            GpuTextureView view = device.createTextureView(texture);
            this.owned.add(view);
            this.sourceView = view;
            try (NativeImage image = new NativeImage(VideoCheck.WIDTH, VideoCheck.HEIGHT, false)) {
                for (int y = 0; y < VideoCheck.HEIGHT; y++) {
                    for (int x = 0; x < VideoCheck.WIDTH; x++) {
                        image.setPixel(x, y, VideoCheck.expectedArgb(x, y));
                    }
                }
                device.createCommandEncoder().writeToTexture(texture, image);
            }
            for (int i = 0; i < NAMES.length; i++) {
                this.targets[i] = this.target(NAMES[i]);
            }
            FisheyeLens lens = VideoCheck.lens();
            ImageArea area = ImageArea.of(ImageFormat.WIDE, VideoCheck.WIDTH, VideoCheck.HEIGHT);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer lensData = VideoPass.lensUniforms(Std140Builder.onStack(stack, VideoPass.LENS_UBO_SIZE), lens, VideoCheck.WIDTH,
                        VideoCheck.HEIGHT, area, lens.tanX(), lens.tanY()).get();
                this.uniforms[0] = this.uniform(device, "lens", lensData);
                ByteBuffer cleanData = VideoPass.feedUniforms(Std140Builder.onStack(stack, VideoPass.UBO_SIZE), cleanParams(), VideoCheck.WIDTH,
                        VideoCheck.HEIGHT, area).get();
                this.uniforms[1] = this.uniform(device, "clean", cleanData);
                ByteBuffer noisyData = VideoPass.feedUniforms(Std140Builder.onStack(stack, VideoPass.UBO_SIZE), noisyParams(), VideoCheck.WIDTH,
                        VideoCheck.HEIGHT, area).get();
                this.uniforms[2] = this.uniform(device, "noisy", noisyData);
            }
        }

        private GpuBuffer uniform(GpuDevice device, String name, ByteBuffer data) {
            GpuBuffer buffer = device.createBuffer(() -> "propwash video check " + name, GpuBuffer.USAGE_UNIFORM, data);
            this.owned.add(buffer);
            return buffer;
        }

        private GpuBuffer uniform(int index) {
            return this.uniforms[index];
        }

        private GpuTextureView view(int index) {
            TextureTarget target = this.targets[index];
            GpuTextureView view = target == null ? null : target.getColorTextureView();
            if (view == null) {
                throw new IllegalStateException("Prüfziel " + NAMES[index] + " ohne Farbtextur");
            }
            return view;
        }

        private @Nullable GpuTexture texture(int index) {
            TextureTarget target = this.targets[index];
            return target == null ? null : target.getColorTexture();
        }

        private GpuTextureView sourceView() {
            GpuTextureView view = this.sourceView;
            if (view == null) {
                throw new IllegalStateException("Testbild fehlt");
            }
            return view;
        }

        private TextureTarget target(String name) {
            TextureTarget target = new TextureTarget("propwash video check " + name, VideoCheck.WIDTH, VideoCheck.HEIGHT, GpuFormat.RGBA8_UNORM, null);
            this.owned.add(target::destroyBuffers);
            GpuTexture texture = target.getColorTexture();
            if (texture != null) {
                RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture, BLACK);
            }
            return target;
        }

        private void readBack(VideoSelfCheck owner) {
            GpuTexture[] sources = {this.source, this.texture(FULL), this.texture(NOISY), this.texture(SIMPLE)};
            for (int i = 0; i < sources.length; i++) {
                GpuTexture texture = sources[i];
                if (texture == null) {
                    throw new IllegalStateException("Prüfziel " + i + " ohne Farbtextur");
                }
                int index = i;
                GuardedReadback.read(texture, 0, 0, VideoCheck.WIDTH, VideoCheck.HEIGHT, "Video-Bildprüfung", read -> this.read(owner, index, read));
            }
        }

        private void read(VideoSelfCheck owner, int index, GuardedReadback.Read read) {
            this.path = read.path();
            byte[] bytes = read.data();
            this.data[index] = !this.closed && bytes != null && bytes.length >= VideoCheck.bytes() ? bytes : null;
            this.pending--;
            if (this.pending == 0) {
                owner.arrived(this);
            }
        }

        private void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            for (int i = this.owned.size() - 1; i >= 0; i--) {
                try {
                    this.owned.get(i).close();
                } catch (Exception ignored) {
                }
            }
            this.owned.clear();
        }
    }
}
