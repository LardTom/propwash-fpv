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
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.client.osd.OsdWarning;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.apiimpl.BuildAccessImpl;
import de.lardtom.propwash.apiimpl.NamespaceRules;
import de.lardtom.propwash.apiimpl.client.ClientApiImpl;
import de.lardtom.propwash.apiimpl.client.WorkbenchExtensions;
import de.lardtom.propwash.build.PartAvailability;
import de.lardtom.propwash.capture.client.CaptureClient;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.client.GuideOpener;
import de.lardtom.propwash.guide.client.HoldToOpen;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.input.addon.client.AddonTestRadio;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
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
        Objects.requireNonNull(factory, "factory");
        String mod = NamespaceRules.callerMod();
        ConfigTabs.register(() -> {
            ConfigTab tab = factory.get();
            return tab != null && NamespaceRules.refuse(tab.id(), "config tab", mod) ? null : tab;
        });
    }

    public static boolean openConfigTab(String tabId, @Nullable Screen parent) {
        return ClientApiImpl.openConfigTab(Objects.requireNonNull(tabId, "tabId"), parent);
    }

    public static void registerWorkbenchExtension(WorkbenchExtension extension) {
        if (!NamespaceRules.refuse(Objects.requireNonNull(extension, "extension").id().toString(), "workbench extension")) {
            WorkbenchExtensions.register(extension);
        }
    }

    public static List<WorkbenchExtension> workbenchExtensions() {
        return WorkbenchExtensions.all();
    }

    public static void registerFlightSchoolScene(Supplier<Scene> scene) {
        registerFlightSchoolScene(scene, () -> true);
    }

    public static void registerFlightSchoolScene(Supplier<Scene> scene, BooleanSupplier visible) {
        Objects.requireNonNull(scene, "scene");
        Scene created = Objects.requireNonNull(scene.get(), "scene");
        if (!created.builtin() || !NamespaceRules.refuse(Scene.BUILTIN_NAMESPACE + ":" + created.id(), "flight school scene")) {
            GuideScenes.register(() -> created, visible);
        }
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

    public static boolean simulateFlightSchoolHold(int slot) {
        return HoldToOpen.simulate(slot);
    }

    public static void stopSimulatedFlightSchoolHold() {
        HoldToOpen.stopSimulation();
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

    public static void registerOsdElement(OsdElementSpec spec) {
        Api17Registries.Client.registerOsdElement(spec);
    }

    public static void registerOsdWarning(Identifier id, OsdWarning warning) {
        Api17Registries.Client.registerOsdWarning(id, warning);
    }

    public static void bindSwitchKey(Identifier id, KeyMapping key) {
        Api17Registries.Client.bindSwitchKey(id, key);
    }

    public static void bindAxisKeys(Identifier id, KeyMapping increase, KeyMapping decrease) {
        Api17Registries.Client.bindAxisKeys(id, increase, decrease);
    }

    public static void registerServiceExtension(ServiceExtension extension) {
        Api17Registries.Client.registerServiceExtension(extension);
    }

    public static RecordingControl recording() {
        return Api17Registries.Client.RECORDING;
    }

    public static CameraChunkRequests cameraChunks() {
        return CaptureClient.INSTANCE;
    }

    public static boolean spectate(Entity drone) {
        return Api17Registries.Client.spectate(drone);
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

    public static boolean simulateRadio(boolean gamepad) {
        return AddonTestRadio.start(gamepad);
    }

    public static void stopSimulatedRadio() {
        AddonTestRadio.stop();
    }

    public static boolean setSimulatedAxis(int axis, float value) {
        return AddonTestRadio.axis(axis, value);
    }

    public static boolean setSimulatedButton(int button, boolean down) {
        return AddonTestRadio.button(button, down);
    }

    public static boolean bindSimulatedSwitch(Identifier id, int button) {
        return AddonTestRadio.bindButton(Objects.requireNonNull(id, "id"), button);
    }

    public static boolean bindSimulatedSwitch(Identifier id, int channel, int lowMicros, int highMicros) {
        return AddonTestRadio.bindChannel(Objects.requireNonNull(id, "id"), channel, lowMicros, highMicros);
    }

    public static boolean bindSimulatedAction(int number, int button) {
        return AddonTestRadio.bindActionButton(number, button);
    }

    public static boolean bindSimulatedAction(int number, int channel, int lowMicros, int highMicros) {
        return AddonTestRadio.bindActionChannel(number, channel, lowMicros, highMicros);
    }

    public static void renderPreview(GuiGraphicsExtractor graphics, DroneData drone, int x0, int y0, int x1, int y1, float yawDegrees) {
        ClientApiImpl.renderPreview(graphics, drone, x0, y0, x1, y1, yawDegrees);
    }

    public static void renderPreview(GuiGraphicsExtractor graphics, DroneData drone, int x0, int y0, int x1, int y1, float yawDegrees,
                                     float pixelsPerMillimetre) {
        ClientApiImpl.renderPreview(graphics, drone, x0, y0, x1, y1, yawDegrees, pixelsPerMillimetre);
    }
}
