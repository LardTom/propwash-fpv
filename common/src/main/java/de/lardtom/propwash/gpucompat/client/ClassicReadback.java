package de.lardtom.propwash.gpucompat.client;

import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL33C;

import java.nio.ByteBuffer;

public final class ClassicReadback {
    static volatile Fault fault = Fault.NONE;
    private static long reads;

    private int framebuffer;
    private int texture;
    private boolean complete;
    private @Nullable String problem;

    public static boolean supported(@Nullable GpuTexture texture) {
        return texture instanceof GlTexture;
    }

    public static long reads() {
        return reads;
    }

    public boolean holds() {
        return this.framebuffer != 0;
    }

    public @Nullable String problem() {
        return this.problem;
    }

    public boolean read(GpuTexture source, int width, int height, ByteBuffer out) {
        return this.read(source, 0, 0, width, height, out);
    }

    public boolean read(GpuTexture source, int x, int y, int width, int height, ByteBuffer out) {
        if (!(source instanceof GlTexture gl)) {
            this.problem = "kein OpenGL";
            return false;
        }
        if (out.capacity() < width * height * 4) {
            this.problem = "Puffer zu klein";
            return false;
        }
        int previous = GlStateManager.getFrameBuffer(GL33C.GL_READ_FRAMEBUFFER);
        try {
            GlStateManager.clearGlErrors();
            if (!this.attach(gl.glId())) {
                return false;
            }
            GlStateManager._glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, this.framebuffer);
            GlStateManager._glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
            GlStateManager._pixelStore(GL33C.GL_PACK_ALIGNMENT, 4);
            GlStateManager._pixelStore(GL33C.GL_PACK_ROW_LENGTH, 0);
            reads++;
            ByteBuffer target = out.duplicate().clear();
            if (fault == Fault.ZERO) {
                for (int i = 0; i < width * height * 4; i++) {
                    target.put(i, (byte) 0);
                }
                return true;
            }
            GL33C.glReadPixels(x, y, width, height, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, target);
            int error = GlStateManager._getError();
            if (error != 0) {
                this.problem = "glReadPixels: GL-Fehler " + error;
                return false;
            }
            return true;
        } finally {
            GlStateManager._glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, previous);
        }
    }

    private boolean attach(int id) {
        if (this.framebuffer != 0 && this.texture == id) {
            return this.complete;
        }
        this.close();
        this.framebuffer = GlStateManager.glGenFramebuffers();
        this.texture = id;
        GlStateManager._glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, this.framebuffer);
        GlStateManager._glFramebufferTexture2D(GL33C.GL_READ_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, id, 0);
        GlStateManager._glReadBuffer(GL33C.GL_COLOR_ATTACHMENT0);
        int status = GL33C.glCheckFramebufferStatus(GL33C.GL_READ_FRAMEBUFFER);
        this.complete = status == GL33C.GL_FRAMEBUFFER_COMPLETE;
        if (!this.complete) {
            this.problem = "Framebuffer unvollständig: " + status;
        }
        return this.complete;
    }

    public void close() {
        if (this.framebuffer != 0) {
            GlStateManager._glDeleteFramebuffers(this.framebuffer);
        }
        this.framebuffer = 0;
        this.texture = 0;
        this.complete = false;
    }

    public enum Fault {
        NONE,
        ZERO
    }
}
