package de.lardtom.propwash.api.client;

import de.lardtom.propwash.accessory.client.AddonCamera;
import de.lardtom.propwash.accessory.client.AddonHud;
import de.lardtom.propwash.accessory.client.ClientInputImpl;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.client.camera.GimbalProvider;
import de.lardtom.propwash.api.client.hud.HudElement;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.apiimpl.BuildAccessImpl;
import de.lardtom.propwash.apiimpl.client.ClientApiImpl;
import de.lardtom.propwash.apiimpl.client.WorkbenchExtensions;
import de.lardtom.propwash.build.PartAvailability;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.client.GuideOpener;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class PropwashClientApi {
    private PropwashClientApi() {
    }

    public static boolean isAddonActive(String modId) {
        return PartAvailability.active(Objects.requireNonNull(modId, "modId"));
    }

    public static void registerConfigTab(Supplier<? extends ConfigTab> factory) {
        ConfigTabs.register(Objects.requireNonNull(factory, "factory"));
    }

    public static boolean openConfigTab(String tabId, @Nullable Screen parent) {
        return ClientApiImpl.openConfigTab(Objects.requireNonNull(tabId, "tabId"), parent);
    }

    public static void registerWorkbenchExtension(WorkbenchExtension extension) {
        WorkbenchExtensions.register(extension);
    }

    public static List<WorkbenchExtension> workbenchExtensions() {
        return WorkbenchExtensions.all();
    }

    public static void registerFlightSchoolScene(Supplier<Scene> scene) {
        registerFlightSchoolScene(scene, () -> true);
    }

    public static void registerFlightSchoolScene(Supplier<Scene> scene, BooleanSupplier visible) {
        Objects.requireNonNull(scene, "scene");
        GuideScenes.register(() -> requireAddonScene(scene.get()), visible);
    }

    public static ActorSpec.Drone flightSchoolDrone(BuildSpec build, boolean assembled, float scale) {
        return new ActorSpec.Drone(BuildAccessImpl.requireBuild(build), assembled, scale);
    }

    public static boolean openFlightSchoolScene(String sceneId, @Nullable Screen parent) {
        Scene scene = GuideScenes.byId(sceneId);
        if (scene == null) {
            return false;
        }
        GuideOpener.openScene(scene, parent);
        return true;
    }

    public static void linkFlightSchoolItems(String sceneId, String... itemIds) {
        Objects.requireNonNull(itemIds, "itemIds");
        GuideScenes.linkItems(sceneId, Arrays.asList(itemIds));
    }

    public static void linkFlightSchoolParts(String sceneId, String namespace, PartKind... kinds) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(kinds, "kinds");
        Set<PartKind> set = EnumSet.noneOf(PartKind.class);
        for (PartKind kind : kinds) {
            set.add(Objects.requireNonNull(kind, "kind"));
        }
        GuideScenes.linkParts(sceneId, namespace, set);
    }

    public static List<String> flightSchoolScenes(ItemStack stack) {
        List<String> ids = new ArrayList<>();
        for (Scene scene : GuideOpener.scenesFor(Objects.requireNonNull(stack, "stack"))) {
            ids.add(scene.builtin() ? Scene.BUILTIN_NAMESPACE + ":" + scene.id() : scene.id());
        }
        return List.copyOf(ids);
    }

    public static boolean openFlightSchool(ItemStack stack, @Nullable Screen parent) {
        if (GuideOpener.scenesFor(Objects.requireNonNull(stack, "stack")).isEmpty()) {
            return false;
        }
        GuideOpener.openFor(stack, parent);
        return true;
    }

    public static OsdLayoutAccess osd() {
        return ClientApiImpl.OSD;
    }

    public static void registerHudElement(String id, HudElement element) {
        AddonHud.register(id, element);
    }

    public static void registerGimbal(String id, GimbalProvider provider) {
        AddonCamera.register(id, provider);
    }

    public static ClientInput input() {
        return ClientInputImpl.INSTANCE;
    }

    public static ClientTune tune() {
        return ClientApiImpl.TUNE;
    }

    public static Optional<DroneData> heldDrone() {
        return ClientApiImpl.heldDrone();
    }

    public static CompletableFuture<ImportResult> sendImport(ImportRequest request) {
        return ClientApiImpl.sendImport(request);
    }

    public static CompletableFuture<ImportResult> sendCreateDrone(DroneData data) {
        return ClientApiImpl.sendImport(ImportRequest.createDrone(Objects.requireNonNull(data, "data")));
    }

    public static void forceShiftHelp(@Nullable Boolean shown) {
        TooltipShift.override(shown);
    }

    public static void renderPreview(GuiGraphicsExtractor graphics, DroneData drone, int x0, int y0, int x1, int y1, float yawDegrees) {
        ClientApiImpl.renderPreview(graphics, drone, x0, y0, x1, y1, yawDegrees);
    }

    public static void renderPreview(GuiGraphicsExtractor graphics, DroneData drone, int x0, int y0, int x1, int y1, float yawDegrees,
                                     float pixelsPerMillimetre) {
        ClientApiImpl.renderPreview(graphics, drone, x0, y0, x1, y1, yawDegrees, pixelsPerMillimetre);
    }

    private static Scene requireAddonScene(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        if (scene.builtin()) {
            throw new IllegalArgumentException("Flugschul-Szenen von Addons brauchen eine Id mit eigenem Namensraum (z. B. meinmod:rahmen): "
                    + scene.id());
        }
        return scene;
    }
}
