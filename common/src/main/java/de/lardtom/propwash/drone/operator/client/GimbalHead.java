package de.lardtom.propwash.drone.operator.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.client.AddonCamera;
import de.lardtom.propwash.api.client.camera.GimbalProvider;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.render.DroneAssembly;
import de.lardtom.propwash.drone.client.render.DroneRenderResources;
import de.lardtom.propwash.drone.client.render.DroneRenderState;
import de.lardtom.propwash.drone.client.render.PartModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class GimbalHead {
    public static final String ELEMENT = "gimbal_head";
    private static final int MAX_PARENTS = 8;
    private static final float PIXELS = 16.0F;
    private static final long STALE_NANOS = 250_000_000L;
    private static final Map<PartModel, Optional<Head>> HEADS = new HashMap<>();
    private static final Map<Integer, Long> LAST_NANOS = new HashMap<>();
    private static final Quaternionf POSE = new Quaternionf();
    private static int generation = Integer.MIN_VALUE;
    private static int posed;
    private static int splits;
    private static boolean failureLogged;

    private GimbalHead() {
    }

    public record Head(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float pivotX, float pivotY, float pivotZ) {
        private static final float SLACK = 0.5F / PIXELS;

        public boolean contains(float x, float y, float z) {
            return x >= this.minX - SLACK && x <= this.maxX + SLACK && y >= this.minY - SLACK && y <= this.maxY + SLACK && z >= this.minZ - SLACK
                    && z <= this.maxZ + SLACK;
        }
    }

    public static int posedFrames() {
        return posed;
    }

    public static int splitSubmits() {
        return splits;
    }

    public static int headQuads() {
        return HeadCollector.headQuads();
    }

    public static void extract(QuadcopterEntity drone, DroneRenderState state) {
        if (state.hidden) {
            return;
        }
        GimbalProvider provider = AddonCamera.provider(drone);
        if (provider == null) {
            return;
        }
        int mount = headMount(state);
        if (mount < 0) {
            return;
        }
        long now = System.nanoTime();
        Long last = LAST_NANOS.put(drone.getId(), now);
        double dt = last == null || now - last > STALE_NANOS ? 0.0 : (now - last) / 1.0E9;
        if (LAST_NANOS.size() > 512) {
            LAST_NANOS.clear();
        }
        POSE.identity();
        boolean moved;
        try {
            moved = provider.modelPose(AddonCamera.context(drone, state.orientation, CameraMount.uptilt(state.build, drone.tune().values()), dt, false),
                    POSE);
        } catch (RuntimeException e) {
            logOnce(e);
            return;
        }
        if (!moved || !Float.isFinite(POSE.x + POSE.y + POSE.z + POSE.w) || POSE.lengthSquared() < 1.0E-6F) {
            return;
        }
        state.accessoryDisplay.gimbal[mount].set(POSE).normalize();
        posed++;
    }

    public static SubmitNodeCollector items(DroneRenderState state, int mount, PoseStack poseStack, SubmitNodeCollector collector) {
        if (mount < 0 || mount >= state.accessoryDisplay.gimbal.length) {
            return collector;
        }
        Quaternionfc pose = state.accessoryDisplay.gimbal[mount];
        if (identity(pose)) {
            return collector;
        }
        Head head = head(state, mount);
        if (head == null) {
            return collector;
        }
        splits++;
        return new HeadCollector(collector, poseStack.last().copy(), head, pose, state.accessoryTilt[mount]);
    }

    public static @Nullable Head head(DroneRenderState state, int mount) {
        AccessoryMount[] mounts = AccessoryMount.values();
        if (mount < 0 || mount >= mounts.length || state.accessoryType[mount] == null) {
            return null;
        }
        DroneAssembly.Accessory accessory = DroneRenderResources.INSTANCE.assembly(state.build).accessory(mounts[mount]);
        PartModel model = accessory.body().model();
        return model == null ? null : head(model);
    }

    public static synchronized @Nullable Head head(PartModel model) {
        int current = DroneRenderResources.INSTANCE.generation();
        if (current != generation) {
            generation = current;
            HEADS.clear();
            HeadCollector.clear();
        }
        Optional<Head> known = HEADS.get(model);
        if (known == null) {
            known = load(Minecraft.getInstance().getResourceManager(), model);
            HEADS.put(model, known);
        }
        return known.orElse(null);
    }

    static int headMount(DroneRenderState state) {
        for (int i = 0; i < state.accessoryType.length; i++) {
            if (state.accessoryType[i] != null && head(state, i) != null) {
                return i;
            }
        }
        return -1;
    }

    static boolean identity(Quaternionfc q) {
        return Math.abs(q.x()) < 1.0E-5F && Math.abs(q.y()) < 1.0E-5F && Math.abs(q.z()) < 1.0E-5F;
    }

    public static Optional<Head> parse(JsonObject model) {
        JsonElement elements = model.get("elements");
        if (elements == null || !elements.isJsonArray()) {
            return Optional.empty();
        }
        for (JsonElement entry : elements.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject element = entry.getAsJsonObject();
            JsonElement name = element.get("name");
            if (name == null || !name.isJsonPrimitive() || !ELEMENT.equals(name.getAsString())) {
                continue;
            }
            float[] from = vector(element.get("from"));
            float[] to = vector(element.get("to"));
            if (from == null || to == null) {
                return Optional.empty();
            }
            float minX = Math.min(from[0], to[0]);
            float minY = Math.min(from[1], to[1]);
            float minZ = Math.min(from[2], to[2]);
            float maxX = Math.max(from[0], to[0]);
            float maxY = Math.max(from[1], to[1]);
            float maxZ = Math.max(from[2], to[2]);
            float[] pivot = null;
            if (element.get("rotation") instanceof JsonObject rotation) {
                pivot = vector(rotation.get("origin"));
            }
            if (pivot == null) {
                pivot = new float[]{(minX + maxX) * 0.5F, (minY + maxY) * 0.5F, (minZ + maxZ) * 0.5F};
            }
            return Optional.of(new Head(minX / PIXELS, minY / PIXELS, minZ / PIXELS, maxX / PIXELS, maxY / PIXELS, maxZ / PIXELS, pivot[0] / PIXELS,
                    pivot[1] / PIXELS, pivot[2] / PIXELS));
        }
        return Optional.empty();
    }

    private static Optional<Head> load(ResourceManager manager, PartModel part) {
        Identifier file = modelFile(manager, part);
        for (int depth = 0; depth < MAX_PARENTS && file != null; depth++) {
            JsonObject model = json(manager, file);
            if (model == null) {
                return Optional.empty();
            }
            if (model.has("elements")) {
                return parse(model);
            }
            JsonElement parent = model.get("parent");
            Identifier id = parent != null && parent.isJsonPrimitive() ? Identifier.tryParse(parent.getAsString()) : null;
            file = id == null ? null : id.withPath(path -> "models/" + path + ".json");
        }
        return Optional.empty();
    }

    private static @Nullable Identifier modelFile(ResourceManager manager, PartModel part) {
        JsonObject item = json(manager, part.itemModelFile());
        Identifier referenced = item == null ? null : referenced(item.get("model"));
        if (referenced != null) {
            return referenced.withPath(path -> "models/" + path + ".json");
        }
        return part.modelFile();
    }

    private static @Nullable Identifier referenced(@Nullable JsonElement node) {
        if (node instanceof JsonObject object) {
            JsonElement type = object.get("type");
            JsonElement model = object.get("model");
            if (type != null && type.isJsonPrimitive() && "minecraft:model".equals(type.getAsString()) && model != null && model.isJsonPrimitive()) {
                return Identifier.tryParse(model.getAsString());
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                Identifier found = referenced(entry.getValue());
                if (found != null) {
                    return found;
                }
            }
        } else if (node instanceof JsonArray array) {
            for (JsonElement element : array) {
                Identifier found = referenced(element);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static @Nullable JsonObject json(ResourceManager manager, Identifier file) {
        Optional<Resource> resource = manager.getResource(file);
        if (resource.isEmpty()) {
            return null;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonElement root = JsonParser.parseReader(reader);
            return root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static float @Nullable [] vector(@Nullable JsonElement element) {
        if (!(element instanceof JsonArray array) || array.size() != 3) {
            return null;
        }
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            JsonElement value = array.get(i);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                return null;
            }
            out[i] = value.getAsFloat();
        }
        return out;
    }

    private static void logOnce(RuntimeException e) {
        if (!failureLogged) {
            failureLogged = true;
            Constants.LOG.warn("Gimbal-Kopf eines Add-ons fehlgeschlagen", e);
        }
    }
}
