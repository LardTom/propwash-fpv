package de.lardtom.propwash.guide.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.guide.scene.SceneProjection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SceneFrame {
    private final SceneProjection projection;
    private final List<Drawable> drawables = new ArrayList<>();
    private @Nullable Rendered rendered;

    public SceneFrame(SceneProjection projection) {
        this.projection = projection;
    }

    public SceneProjection projection() {
        return this.projection;
    }

    public void add(Drawable drawable) {
        this.drawables.add(drawable);
    }

    public List<Drawable> drawables() {
        return Collections.unmodifiableList(this.drawables);
    }

    public @Nullable Rendered rendered() {
        return this.rendered;
    }

    void markRendered(Matrix4fc pose, int left, int top, float pixelScale, float textureWidth, float textureHeight) {
        this.rendered = new Rendered(new Matrix4f(pose), left, top, pixelScale, textureWidth, textureHeight);
    }

    public record Rendered(Matrix4f pose, int left, int top, float pixelScale, float textureWidth, float textureHeight) {
        public float[] toGui(float x, float y, float z) {
            return SceneProjection.rasterToGui(this.pose, this.left, this.top, this.pixelScale, x, y, z, new float[3]);
        }
    }

    @FunctionalInterface
    public interface Drawable {
        void submit(PoseStack poseStack, SubmitNodeCollector collector);
    }
}
