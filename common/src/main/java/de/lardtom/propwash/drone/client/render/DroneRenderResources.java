package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PaintChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public final class DroneRenderResources implements ResourceManagerReloadListener {
    static final float MODEL_MIN = -16.0F;
    static final float MODEL_MAX = 32.0F;
    public static final DroneRenderResources INSTANCE = new DroneRenderResources();
    public static final Identifier LAYOUT_FILE = Constants.id("drone_layout.json");
    public static final Identifier PAINT_FILE = Constants.id("drone_paint.json");
    public static final Identifier WHITE_TEXTURE = Constants.id("textures/entity/drone_render/white.png");

    private static final int MAX_ASSEMBLIES = 64;
    private static final int MAX_PAINTED = 512;
    private static final float MAX_PART_HEIGHT_MM = 400.0F;

    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private final Map<FrameType, DroneLayout> layouts = new ConcurrentHashMap<>();
    private final Map<PartModel, Boolean> availability = new ConcurrentHashMap<>();
    private volatile Map<Identifier, JsonElement> layoutEntries = Map.of();
    private volatile Map<Identifier, Float> motorSeats = Map.of();
    private volatile Map<String, List<TintGroup>> paintGroups = Map.of();
    private volatile Set<PartModel> missingModels = Set.of();
    private volatile @Nullable ResourceManager manager;
    private volatile boolean layoutFileFound;
    private volatile boolean loaded;
    private volatile int generation;
    private final Map<PartModel, ResolvedPart> resolvedParts = new HashMap<>();
    private final Map<DroneBuild, DroneAssembly> assemblies = new HashMap<>();
    private final Map<PaintedKey, ResolvedPart> paintedParts = new HashMap<>();

    private DroneRenderResources() {
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        this.manager = manager;
        this.layouts.clear();
        this.availability.clear();
        try {
            this.reloadLayouts(manager);
            this.reloadModels(manager);
            this.reloadPaint(manager);
        } catch (RuntimeException e) {
            Constants.LOG.error("Drohnen-Renderer: Ressourcen konnten nicht geladen werden, benutze eingebaute Maße und Ersatzformen", e);
            this.layoutEntries = Map.of();
            this.layouts.clear();
            this.missingModels = Set.copyOf(PartModel.all());
            for (PartModel model : PartModel.known()) {
                this.availability.put(model, Boolean.FALSE);
            }
        }
        this.warned.clear();
        synchronized (this) {
            this.resolvedParts.clear();
            this.assemblies.clear();
            this.paintedParts.clear();
            this.loaded = true;
            this.generation++;
        }
    }

    public static List<String> namespacesInOrder(Set<String> namespaces) {
        List<String> ordered = new ArrayList<>();
        if (namespaces.contains(Constants.MOD_ID)) {
            ordered.add(Constants.MOD_ID);
        }
        namespaces.stream().filter(ns -> !Constants.MOD_ID.equals(ns)).sorted().forEach(ordered::add);
        return ordered;
    }

    private void reloadLayouts(ResourceManager manager) {
        Map<Identifier, JsonElement> entries = new LinkedHashMap<>();
        Map<Identifier, Float> seats = new LinkedHashMap<>();
        boolean builtinFile = false;
        for (String namespace : namespacesInOrder(manager.getNamespaces())) {
            Identifier file = Identifier.fromNamespaceAndPath(namespace, LAYOUT_FILE.getPath());
            Optional<Resource> resource = manager.getResource(file);
            if (resource.isEmpty()) {
                continue;
            }
            try (Reader reader = resource.get().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                DroneLayoutParser.Result result = DroneLayoutParser.parse(json, namespace);
                List<String> problems = new ArrayList<>(result.problems());
                seats.putAll(DroneLayoutParser.motorSeats(json, namespace, problems));
                entries.putAll(result.entries());
                for (String problem : problems) {
                    Constants.LOG.warn("Drohnen-Renderer: {}: {}", file, problem);
                }
                builtinFile |= Constants.MOD_ID.equals(namespace);
            } catch (Exception e) {
                Constants.LOG.warn("Drohnen-Renderer: {} ist nicht lesbar ({}), benutze Anbaupunkte aus den Rahmen-Definitionen", file, e.toString());
            }
        }
        if (!builtinFile) {
            Constants.LOG.warn("Drohnen-Renderer: {} fehlt, benutze eingebaute Anbaupunkte aus den Rahmenmaßen", LAYOUT_FILE);
        }
        this.layoutFileFound = builtinFile;
        this.layoutEntries = Collections.unmodifiableMap(entries);
        this.motorSeats = Collections.unmodifiableMap(seats);
    }

    private void reloadModels(ResourceManager manager) {
        Set<PartModel> shipped = new HashSet<>(PartModel.all());
        Set<PartModel> missing = new HashSet<>();
        Map<String, int[]> foreign = new TreeMap<>();
        for (PartModel part : PartModel.known()) {
            boolean present = checkModel(manager, part);
            this.availability.put(part, present);
            if (shipped.contains(part)) {
                if (!present) {
                    missing.add(part);
                }
            } else {
                foreign.computeIfAbsent(part.namespace(), ns -> new int[2])[present ? 0 : 1]++;
            }
        }
        this.missingModels = Set.copyOf(missing);
        if (!missing.isEmpty()) {
            List<String> names = missing.stream().map(PartModel::toString).sorted().toList();
            Constants.LOG.warn("Drohnen-Renderer: {} von {} Bauteil-Modellen fehlen, zeichne dafür einfache Formen: {}",
                    missing.size(), shipped.size(), names);
        }
        for (Map.Entry<String, int[]> entry : foreign.entrySet()) {
            Constants.LOG.info("Drohnen-Renderer: Namensraum {}: {} Bauteil-Modelle vorhanden, {} ohne Modell (Form aus der Definition)",
                    entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
        }
    }

    static boolean checkModel(ResourceManager manager, PartModel part) {
        if (manager.getResource(part.itemModelFile()).isEmpty()) {
            return false;
        }
        Identifier model = part.modelFile();
        if (model == null) {
            return true;
        }
        Optional<Resource> resource = manager.getResource(model);
        if (resource.isEmpty()) {
            return false;
        }
        return Constants.MOD_ID.equals(model.getNamespace()) || withinModelBounds(resource.get(), model);
    }

    static boolean withinModelBounds(Resource resource, Identifier model) {
        try (Reader reader = resource.openAsReader()) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("elements")) {
                return true;
            }
            for (JsonElement element : root.getAsJsonObject().getAsJsonArray("elements")) {
                JsonObject cuboid = element.getAsJsonObject();
                for (String key : List.of("from", "to")) {
                    for (JsonElement value : cuboid.getAsJsonArray(key)) {
                        float coordinate = value.getAsFloat();
                        if (!(coordinate >= MODEL_MIN && coordinate <= MODEL_MAX)) {
                            Constants.LOG.warn("Drohnen-Renderer: Modell {} liegt außerhalb der Minecraft-Grenzen ({}…{}), zeichne die Form aus der Definition",
                                    model, MODEL_MIN, MODEL_MAX);
                            return false;
                        }
                    }
                }
            }
            return true;
        } catch (Exception e) {
            Constants.LOG.warn("Drohnen-Renderer: Modell {} ist nicht lesbar ({}), zeichne die Form aus der Definition", model, e.toString());
            return false;
        }
    }

    private void reloadPaint(ResourceManager manager) {
        Map<String, List<TintGroup>> groups = new HashMap<>();
        boolean builtinFile = false;
        for (String namespace : namespacesInOrder(manager.getNamespaces())) {
            Identifier file = Identifier.fromNamespaceAndPath(namespace, PAINT_FILE.getPath());
            Optional<Resource> resource = manager.getResource(file);
            if (resource.isEmpty()) {
                continue;
            }
            try (Reader reader = resource.get().openAsReader()) {
                groups.putAll(parsePaint(JsonParser.parseReader(reader), namespace));
                builtinFile |= Constants.MOD_ID.equals(namespace);
            } catch (Exception e) {
                Constants.LOG.warn("Drohnen-Renderer: {} ist nicht lesbar ({}), Lackierungen dieses Namensraums werden nicht gezeichnet", file,
                        e.toString());
            }
        }
        if (!builtinFile) {
            Constants.LOG.warn("Drohnen-Renderer: {} fehlt, Lackierungen werden nicht gezeichnet", PAINT_FILE);
        }
        this.paintGroups = Map.copyOf(groups);
    }

    public static Map<String, List<TintGroup>> parsePaint(JsonElement json, String namespace) {
        Map<String, List<TintGroup>> out = new HashMap<>();
        for (Map.Entry<String, List<TintGroup>> entry : parsePaint(json).entrySet()) {
            String key = paintKey(entry.getKey(), namespace);
            if (key != null) {
                out.put(key, entry.getValue());
            }
        }
        return Map.copyOf(out);
    }

    static @Nullable String paintKey(String key, @Nullable String namespace) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        int colon = key.indexOf(':');
        String ns = colon >= 0 ? key.substring(0, colon) : namespace == null ? Constants.MOD_ID : namespace;
        String local = colon >= 0 ? key.substring(colon + 1) : key;
        if (ns.isEmpty() || local.isEmpty()) {
            return null;
        }
        return Constants.MOD_ID.equals(ns) ? local : ns + ":" + local;
    }

    public static Map<String, List<TintGroup>> parsePaint(JsonElement json) {
        Map<String, List<TintGroup>> out = new HashMap<>();
        JsonObject models = json.getAsJsonObject().getAsJsonObject("models");
        if (models == null) {
            return Map.of();
        }
        for (Map.Entry<String, JsonElement> entry : models.entrySet()) {
            JsonArray array = entry.getValue().getAsJsonArray();
            List<TintGroup> groups = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                JsonObject group = element.getAsJsonObject();
                Optional<PaintChannel> channel = PaintChannel.byKey(group.get("channel").getAsString());
                OptionalInt base = DronePaint.parseHex(group.get("base").getAsString());
                if (channel.isEmpty() || base.isEmpty()) {
                    groups = null;
                    break;
                }
                groups.add(new TintGroup(channel.get(), base.getAsInt()));
            }
            if (groups != null && !groups.isEmpty()) {
                out.put(entry.getKey(), List.copyOf(groups));
            }
        }
        return Map.copyOf(out);
    }

    public List<TintGroup> paintGroups(PartModel model) {
        List<TintGroup> groups = this.paintGroups.get(model.name());
        return groups == null ? List.of() : groups;
    }

    public synchronized @Nullable ItemStackRenderState paintedItem(PartModel model, int[] tints) {
        PaintedKey key = new PaintedKey(model, tints.clone());
        ResolvedPart resolved = this.paintedParts.get(key);
        if (resolved == null) {
            resolved = this.resolvePainted(model, key.tints());
            if (this.paintedParts.size() >= MAX_PAINTED) {
                this.paintedParts.clear();
            }
            this.paintedParts.put(key, resolved);
        }
        return resolved.item();
    }

    private ResolvedPart resolvePainted(PartModel model, int[] tints) {
        PartModel variant = model.paintVariant();
        if (variant == model) {
            return ResolvedPart.MISSING;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getResourceManager().getResource(variant.itemModelFile()).isEmpty()) {
                this.warnOnce("paint-missing:" + variant, "Drohnen-Renderer: Lack-Modell {} fehlt, zeichne ungelackt", variant);
                return ResolvedPart.MISSING;
            }
            ItemModel itemModel = minecraft.getModelManager().getItemModel(variant.itemModelId());
            List<Integer> colors = new ArrayList<>(tints.length);
            for (int tint : tints) {
                colors.add(tint);
            }
            ItemStack carrier = new ItemStack(Items.PAPER);
            carrier.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), colors));
            ItemStackRenderState item = new ItemStackRenderState();
            itemModel.update(item, carrier, minecraft.getItemModelResolver(), ItemDisplayContext.NONE, null, null, 0);
            if (item.isEmpty()) {
                return ResolvedPart.MISSING;
            }
            return new ResolvedPart(item, Float.NaN, Float.NaN);
        } catch (RuntimeException e) {
            this.warnOnce("paint:" + variant, "Drohnen-Renderer: Lack-Modell {} konnte nicht vorbereitet werden ({})", variant, e.toString());
            return ResolvedPart.MISSING;
        }
    }

    public float motorSeat(MotorType motor) {
        Float seat = this.motorSeats.get(motor.id());
        return seat == null ? Float.NaN : seat;
    }

    public DroneLayout layout(FrameType frame) {
        DroneLayout layout = this.layouts.get(frame);
        if (layout == null) {
            layout = this.computeLayout(frame);
            this.layouts.put(frame, layout);
        }
        return layout;
    }

    private DroneLayout computeLayout(FrameType frame) {
        JsonElement entry = this.layoutEntries.get(frame.id());
        if (entry != null) {
            List<String> problems = new ArrayList<>();
            try {
                DroneLayout parsed = DroneLayoutParser.parseFrame(frame, entry, problems);
                for (String problem : problems) {
                    this.warnOnce("layout:" + problem, "Drohnen-Renderer: {}: {}", LAYOUT_FILE.getPath(), problem);
                }
                if (parsed != null) {
                    return parsed;
                }
            } catch (RuntimeException e) {
                this.warnOnce("layout:" + frame.id(), "Drohnen-Renderer: Anbaupunkte für {} nicht lesbar ({})", frame.id(), e.toString());
            }
        }
        return DroneLayout.fromDefinition(frame);
    }

    public boolean hasModel(PartModel part) {
        Boolean known = this.availability.get(part);
        if (known != null) {
            return known;
        }
        ResourceManager current = this.manager;
        if (current == null) {
            return false;
        }
        boolean present;
        try {
            present = checkModel(current, part);
        } catch (RuntimeException e) {
            present = false;
        }
        this.availability.put(part, present);
        return present;
    }

    public synchronized DroneAssembly assembly(DroneBuild build) {
        DroneAssembly assembly = this.assemblies.get(build);
        if (assembly == null) {
            assembly = DroneAssembly.create(build, this);
            if (this.assemblies.size() >= MAX_ASSEMBLIES) {
                this.assemblies.clear();
            }
            this.assemblies.put(build, assembly);
        }
        return assembly;
    }

    public synchronized @Nullable ItemStackRenderState item(PartModel part) {
        return this.resolved(part).item();
    }

    public synchronized float top(PartModel part, float fallback) {
        float top = this.resolved(part).top();
        return Float.isFinite(top) ? top : fallback;
    }

    public synchronized float bottom(PartModel part, float fallback) {
        float bottom = this.resolved(part).bottom();
        return Float.isFinite(bottom) ? bottom : fallback;
    }

    private ResolvedPart resolved(PartModel part) {
        ResolvedPart resolved = this.resolvedParts.get(part);
        if (resolved == null) {
            resolved = this.resolve(part);
            this.resolvedParts.put(part, resolved);
        }
        return resolved;
    }

    private ResolvedPart resolve(PartModel part) {
        if (!this.hasModel(part)) {
            return ResolvedPart.MISSING;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            ItemModel model = minecraft.getModelManager().getItemModel(part.itemModelId());
            ItemStackRenderState item = new ItemStackRenderState();
            model.update(item, ItemStack.EMPTY, minecraft.getItemModelResolver(), ItemDisplayContext.NONE, null, null, 0);
            if (item.isEmpty()) {
                this.warnOnce("empty:" + part, "Drohnen-Renderer: Modell {} ist leer, zeichne Ersatzform", part);
                return ResolvedPart.MISSING;
            }
            AABB box = item.getModelBoundingBox();
            float bottom = (float) box.minY * DroneVisualScale.MILLIMETRES_PER_MODEL_BLOCK;
            float top = (float) box.maxY * DroneVisualScale.MILLIMETRES_PER_MODEL_BLOCK;
            if (!Float.isFinite(bottom) || !Float.isFinite(top) || top - bottom > MAX_PART_HEIGHT_MM) {
                bottom = Float.NaN;
                top = Float.NaN;
            }
            return new ResolvedPart(item, bottom, top);
        } catch (RuntimeException e) {
            this.warnOnce("item:" + part, "Drohnen-Renderer: Modell {} konnte nicht vorbereitet werden ({}), zeichne Ersatzform",
                    part, e.toString());
            return ResolvedPart.MISSING;
        }
    }

    public Set<PartModel> missingModels() {
        return this.missingModels;
    }

    public boolean layoutFileFound() {
        return this.layoutFileFound;
    }

    public boolean layoutFromFile(FrameType frame) {
        return this.layout(frame).fromFile();
    }

    public boolean hasLayoutEntry(FrameType frame) {
        return this.layoutEntries.containsKey(frame.id());
    }

    public boolean loaded() {
        return this.loaded;
    }

    public int generation() {
        return this.generation;
    }

    public void warnOnce(String key, String message, Object... args) {
        if (this.warned.add(key)) {
            Constants.LOG.warn(message, args);
        }
    }

    public record TintGroup(PaintChannel channel, int base) {
    }

    private record PaintedKey(PartModel model, int[] tints) {
        @Override
        public boolean equals(Object other) {
            return other instanceof PaintedKey key && key.model == this.model && Arrays.equals(key.tints, this.tints);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(this.model) + Arrays.hashCode(this.tints);
        }
    }

    private record ResolvedPart(@Nullable ItemStackRenderState item, float bottom, float top) {
        static final ResolvedPart MISSING = new ResolvedPart(null, Float.NaN, Float.NaN);
    }
}
