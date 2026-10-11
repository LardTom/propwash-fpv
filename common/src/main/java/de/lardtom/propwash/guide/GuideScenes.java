package de.lardtom.propwash.guide;

import de.lardtom.propwash.build.PartAvailability;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scenes.ActionCamScene;
import de.lardtom.propwash.guide.scenes.BatteryScene;
import de.lardtom.propwash.guide.scenes.BindScene;
import de.lardtom.propwash.guide.scenes.ConfiguratorScene;
import de.lardtom.propwash.guide.scenes.DroneBuildScene;
import de.lardtom.propwash.guide.scenes.FlightViewScene;
import de.lardtom.propwash.guide.scenes.GripperScene;
import de.lardtom.propwash.guide.scenes.HoldScene;
import de.lardtom.propwash.guide.scenes.Mode3dScene;
import de.lardtom.propwash.guide.scenes.CourseElementsScene;
import de.lardtom.propwash.guide.scenes.GateFreeScene;
import de.lardtom.propwash.guide.scenes.RaceScene;
import de.lardtom.propwash.guide.scenes.RecoveryScene;
import de.lardtom.propwash.guide.scenes.RepairScene;
import de.lardtom.propwash.guide.scenes.RescueScene;
import de.lardtom.propwash.guide.scenes.SurvivalScene;
import de.lardtom.propwash.guide.scenes.TricksScene;
import de.lardtom.propwash.guide.scenes.VideoScene;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.core.PartCategory;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class GuideScenes {
    private static final BooleanSupplier ALWAYS = () -> true;
    private static final Map<String, Entry> SCENES = new LinkedHashMap<>();
    private static final Map<String, Set<String>> ITEM_LINKS = new LinkedHashMap<>();
    private static final List<PartLink> PART_LINKS = new ArrayList<>();
    private static boolean bootstrapped;

    private GuideScenes() {
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        add(DroneBuildScene::create, ALWAYS);
        add(BindScene::create, ALWAYS);
        add(FlightViewScene::create, ALWAYS);
        add(BatteryScene::create, ALWAYS);
        add(RaceScene::create, ALWAYS);
        add(GateFreeScene::create, ALWAYS);
        add(CourseElementsScene::create, ALWAYS);
        add(TricksScene::create, ALWAYS);
        add(VideoScene::create, ALWAYS);
        add(ActionCamScene::create, ALWAYS);
        add(GripperScene::create, ALWAYS);
        add(ConfiguratorScene::create, ALWAYS);
        add(RescueScene::create, ALWAYS);
        add(HoldScene::create, ALWAYS);
        add(Mode3dScene::create, ALWAYS);
        add(RecoveryScene::create, ALWAYS);
        add(RepairScene::create, ALWAYS);
        add(SurvivalScene::create, ALWAYS);
        PART_LINKS.add(new PartLink(BatteryScene.ID, null, EnumSet.of(PartKind.BATTERY)));
        PART_LINKS.add(new PartLink(VideoScene.ID, null, EnumSet.of(PartKind.VIDEO)));
        PART_LINKS.add(new PartLink(ConfiguratorScene.ID, null, EnumSet.of(PartKind.STACK)));
        PART_LINKS.add(new PartLink(RepairScene.ID, null, EnumSet.of(PartKind.PROP)));
    }

    public static synchronized Scene register(Supplier<Scene> factory) {
        return register(factory, ALWAYS);
    }

    public static synchronized Scene register(Supplier<Scene> factory, BooleanSupplier visible) {
        bootstrap();
        return add(factory, visible);
    }

    private static Scene add(Supplier<Scene> factory, BooleanSupplier visible) {
        Scene scene = Objects.requireNonNull(factory.get(), "scene");
        if (SCENES.putIfAbsent(scene.id(), new Entry(scene, Objects.requireNonNull(visible, "visible"))) != null) {
            throw new IllegalArgumentException("Doppelte Flugschul-Szene " + scene.id());
        }
        return scene;
    }

    public static synchronized List<Scene> all() {
        bootstrap();
        List<Scene> visible = new ArrayList<>(SCENES.size());
        for (Entry entry : SCENES.values()) {
            if (entry.visible()) {
                visible.add(entry.scene());
            }
        }
        return List.copyOf(visible);
    }

    public static synchronized List<Scene> registered() {
        bootstrap();
        List<Scene> scenes = new ArrayList<>(SCENES.size());
        for (Entry entry : SCENES.values()) {
            scenes.add(entry.scene());
        }
        return List.copyOf(scenes);
    }

    public static synchronized @Nullable Scene byId(String id) {
        bootstrap();
        Entry entry;
        try {
            entry = SCENES.get(Scene.canonicalId(id));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return entry == null || !PartAvailability.activeNamespacedId(entry.scene().id()) ? null : entry.scene();
    }

    public static synchronized void linkItems(String sceneId, Collection<String> itemIds) {
        bootstrap();
        String scene = Scene.canonicalId(sceneId);
        Set<String> ids = new LinkedHashSet<>();
        for (String itemId : Objects.requireNonNull(itemIds, "itemIds")) {
            Identifier id = itemId == null ? null : Identifier.tryParse(itemId);
            if (id == null) {
                throw new IllegalArgumentException("Ungültige Item-Id für die Flugschul-Szene " + sceneId + ": " + itemId);
            }
            ids.add(id.toString());
        }
        ITEM_LINKS.computeIfAbsent(scene, key -> new LinkedHashSet<>()).addAll(ids);
    }

    public static synchronized void linkParts(String sceneId, @Nullable String namespace, Set<PartKind> kinds) {
        bootstrap();
        String scene = Scene.canonicalId(sceneId);
        if (namespace != null && (namespace.isEmpty() || !Identifier.isValidNamespace(namespace))) {
            throw new IllegalArgumentException("Ungültiger Namensraum für die Flugschul-Szene " + sceneId + ": " + namespace);
        }
        Set<PartKind> copy = Objects.requireNonNull(kinds, "kinds").isEmpty() ? EnumSet.allOf(PartKind.class) : EnumSet.copyOf(kinds);
        PART_LINKS.add(new PartLink(scene, namespace, copy));
    }

    public static List<Scene> forItem(String itemId, boolean isPart) {
        return forItem(itemId, isPart, isPart ? kindOf(itemId) : null);
    }

    public static synchronized List<Scene> forItem(String itemId, boolean isPart, @Nullable PartKind kind) {
        if (itemId == null || itemId.isEmpty()) {
            return List.of();
        }
        String namespace = namespaceOf(itemId);
        List<Scene> matches = new ArrayList<>();
        for (Scene scene : all()) {
            if (scene.items().matches(itemId, isPart) || linked(scene.id(), itemId, namespace, kind)) {
                matches.add(scene);
            }
        }
        return matches;
    }

    private static boolean linked(String sceneId, String itemId, String namespace, @Nullable PartKind kind) {
        Set<String> items = ITEM_LINKS.get(sceneId);
        if (items != null && items.contains(itemId)) {
            return true;
        }
        if (kind == null) {
            return false;
        }
        for (PartLink link : PART_LINKS) {
            if (link.scene().equals(sceneId) && link.kinds().contains(kind) && (link.namespace() == null || link.namespace().equals(namespace))) {
                return true;
            }
        }
        return false;
    }

    private static String namespaceOf(String itemId) {
        int colon = itemId.indexOf(':');
        return colon < 0 ? Identifier.DEFAULT_NAMESPACE : itemId.substring(0, colon);
    }

    private static @Nullable PartKind kindOf(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        return id == null ? null : PartIndex.byId(id).map(part -> PartCategory.of(part).kind()).orElse(null);
    }

    private record PartLink(String scene, @Nullable String namespace, Set<PartKind> kinds) {
    }

    private record Entry(Scene scene, BooleanSupplier visibility) {
        boolean visible() {
            if (!PartAvailability.activeNamespacedId(this.scene.id())) {
                return false;
            }
            try {
                return this.visibility.getAsBoolean();
            } catch (RuntimeException e) {
                Constants.LOG.error("Sichtbarkeit der Flugschul-Szene {} konnte nicht geprüft werden", this.scene.id(), e);
                return false;
            }
        }
    }
}
