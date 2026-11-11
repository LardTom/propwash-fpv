package de.lardtom.propwash.drone.client.render;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class PlugMask {
    public static final PlugMask NONE = new PlugMask(List.of());
    private static final int MAX_CACHED = 16;

    private final List<PlugAnchors.Box> boxes;
    private final Map<ItemQuads, ItemQuads> cache = new IdentityHashMap<>();

    public PlugMask(List<PlugAnchors.Box> boxes) {
        this.boxes = List.copyOf(boxes);
    }

    public List<PlugAnchors.Box> boxes() {
        return this.boxes;
    }

    public boolean isEmpty() {
        return this.boxes.isEmpty();
    }

    public boolean hides(BakedQuad quad) {
        return this.covers(quad.position(0), quad.position(1), quad.position(2), quad.position(3));
    }

    boolean covers(Vector3fc... blockCorners) {
        for (PlugAnchors.Box box : this.boxes) {
            if (inside(blockCorners, box)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inside(Vector3fc[] blockCorners, PlugAnchors.Box box) {
        for (Vector3fc p : blockCorners) {
            float x = PlugAnchors.millimetres(p.x() * DroneVisualScale.MODEL_UNITS_PER_BLOCK);
            float y = PlugAnchors.millimetres(p.y() * DroneVisualScale.MODEL_UNITS_PER_BLOCK);
            float z = PlugAnchors.millimetres(p.z() * DroneVisualScale.MODEL_UNITS_PER_BLOCK);
            if (!box.contains(x, y, z, PlugAnchors.TOUCH_MM)) {
                return false;
            }
        }
        return true;
    }

    public ItemQuads apply(ItemQuads quads) {
        if (this.isEmpty() || quads.isEmpty()) {
            return quads;
        }
        synchronized (this.cache) {
            ItemQuads masked = this.cache.get(quads);
            if (masked == null) {
                List<BakedQuad> all = this.kept(quads.all());
                masked = all.size() == quads.all().size() ? quads : new ItemQuads(all, this.kept(quads.solid()), this.kept(quads.translucent()));
                if (this.cache.size() >= MAX_CACHED) {
                    this.cache.clear();
                }
                this.cache.put(quads, masked);
            }
            return masked;
        }
    }

    private List<BakedQuad> kept(List<BakedQuad> quads) {
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            if (!this.hides(quad)) {
                out.add(quad);
            }
        }
        return List.copyOf(out);
    }

    public SubmitNodeCollector wrap(SubmitNodeCollector collector) {
        return this.isEmpty() ? collector : new MaskedItemCollector(collector, this);
    }
}
