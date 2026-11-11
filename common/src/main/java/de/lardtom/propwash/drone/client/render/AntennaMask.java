package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AntennaMask {
    public static final String PREFIX = "antenna";
    public static final AntennaMask NONE = new AntennaMask(List.of(), List.of(), 0);
    static final float TOUCH_UNITS = 0.002F;
    private static final int MAX_CACHED = 16;

    private final List<Solid> antennas;
    private final List<Solid> others;
    private final int faces;
    private final Map<ItemQuads, ItemQuads> cache = new IdentityHashMap<>();

    record Solid(Vector3f from, Vector3f to, @Nullable Matrix3f inverse, Vector3f origin, Vector3f rescale) {
        boolean contains(Vector3fc modelUnits) {
            Vector3f p = new Vector3f(modelUnits);
            if (this.inverse != null) {
                p.sub(this.origin).div(this.rescale);
                this.inverse.transform(p);
                p.add(this.origin);
            }
            return p.x >= this.from.x - TOUCH_UNITS && p.x <= this.to.x + TOUCH_UNITS && p.y >= this.from.y - TOUCH_UNITS
                    && p.y <= this.to.y + TOUCH_UNITS && p.z >= this.from.z - TOUCH_UNITS && p.z <= this.to.z + TOUCH_UNITS;
        }
    }

    private AntennaMask(List<Solid> antennas, List<Solid> others, int faces) {
        this.antennas = List.copyOf(antennas);
        this.others = List.copyOf(others);
        this.faces = faces;
    }

    public static boolean isAntenna(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).startsWith(PREFIX);
    }

    public static AntennaMask of(JsonObject model) {
        JsonElement elements = model.get("elements");
        if (elements == null || !elements.isJsonArray()) {
            return NONE;
        }
        List<Solid> antennas = new ArrayList<>();
        List<Solid> others = new ArrayList<>();
        int faces = 0;
        for (JsonElement entry : elements.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject element = entry.getAsJsonObject();
            Solid solid = solid(element);
            if (solid == null) {
                continue;
            }
            String name = element.get("name") instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : "";
            if (isAntenna(name)) {
                antennas.add(solid);
                faces += element.get("faces") instanceof JsonObject map ? map.size() : 0;
            } else {
                others.add(solid);
            }
        }
        return antennas.isEmpty() ? NONE : new AntennaMask(antennas, others, faces);
    }

    private static @Nullable Solid solid(JsonObject element) {
        Vector3f from = vector(element.get("from"));
        Vector3f to = vector(element.get("to"));
        if (from == null || to == null) {
            return null;
        }
        Vector3f lo = new Vector3f(from).min(to);
        Vector3f hi = new Vector3f(from).max(to);
        JsonElement rotation = element.get("rotation");
        Matrix3f matrix = PlugAnchors.rotation(rotation);
        if (matrix == null) {
            return new Solid(lo, hi, null, new Vector3f(), new Vector3f(1.0F));
        }
        JsonObject object = rotation.getAsJsonObject();
        Vector3f origin = vector(object.get("origin"));
        Vector3f rescale = new Vector3f(1.0F);
        if (object.get("rescale") instanceof JsonPrimitive flag && flag.isBoolean() && flag.getAsBoolean()
                && object.get("axis") instanceof JsonPrimitive axis && axis.isString() && object.get("angle") instanceof JsonPrimitive angle
                && angle.isNumber()) {
            float scale = (float) (1.0 / Math.cos(Math.toRadians(angle.getAsFloat())));
            switch (axis.getAsString()) {
                case "x" -> rescale.set(1.0F, scale, scale);
                case "y" -> rescale.set(scale, 1.0F, scale);
                case "z" -> rescale.set(scale, scale, 1.0F);
                default -> {
                }
            }
        }
        return new Solid(lo, hi, new Matrix3f(matrix).transpose(), origin == null ? new Vector3f(8.0F) : origin, rescale);
    }

    private static @Nullable Vector3f vector(@Nullable JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() != 3) {
            return null;
        }
        float[] values = new float[3];
        for (int i = 0; i < 3; i++) {
            if (!(array.get(i) instanceof JsonPrimitive primitive) || !primitive.isNumber() || !Float.isFinite(primitive.getAsFloat())) {
                return null;
            }
            values[i] = primitive.getAsFloat();
        }
        return new Vector3f(values[0], values[1], values[2]);
    }

    public boolean isEmpty() {
        return this.antennas.isEmpty();
    }

    public int antennaElements() {
        return this.antennas.size();
    }

    public int antennaFaces() {
        return this.faces;
    }

    public boolean hides(BakedQuad quad) {
        Vector3fc[] corners = new Vector3fc[4];
        for (int i = 0; i < 4; i++) {
            corners[i] = new Vector3f(quad.position(i)).mul(DroneVisualScale.MODEL_UNITS_PER_BLOCK);
        }
        return this.hides(corners);
    }

    boolean hides(Vector3fc... modelUnits) {
        return inAny(this.antennas, modelUnits) && !inAny(this.others, modelUnits);
    }

    private static boolean inAny(List<Solid> solids, Vector3fc[] points) {
        for (Solid solid : solids) {
            boolean all = true;
            for (Vector3fc point : points) {
                if (!solid.contains(point)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
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

    public ItemStackRenderState strip(ItemStackRenderState item) {
        if (this.isEmpty() || item.isEmpty()) {
            return item;
        }
        QuadCapture.Result captured = QuadCapture.of(item);
        if (captured.special() || captured.layers().isEmpty()) {
            return item;
        }
        ItemStackRenderState out = new ItemStackRenderState();
        boolean blockLight = item.usesBlockLight();
        for (QuadCapture.Layer layer : captured.layers()) {
            ItemQuads quads = this.apply(layer.quads());
            ItemStackRenderState.LayerRenderState target = out.newLayer();
            target.setQuads(quads);
            target.setLocalTransform(new Matrix4f().translation(0.5F, 0.5F, 0.5F).mul(layer.pose()));
            target.setUsesBlockLight(blockLight);
            for (int tint : layer.tints()) {
                target.tintLayers().add(tint);
            }
            Vector3fc[] extents = extents(quads);
            target.setExtents(() -> extents);
        }
        return out;
    }

    private static Vector3fc[] extents(ItemQuads quads) {
        List<Vector3fc> points = new ArrayList<>(quads.all().size() * 4);
        for (BakedQuad quad : quads.all()) {
            for (int i = 0; i < 4; i++) {
                points.add(new Vector3f(quad.position(i)));
            }
        }
        return points.toArray(new Vector3fc[0]);
    }
}
