package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.PartCondition;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class DroneRenderSelfTests {
    private static final String DRONE_ID = "drone_render_id";
    private static final String SPAWN = "drone_render_spawn";

    private DroneRenderSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("drone-render: Renderer registriert", 1, ctx -> {
            DroneRenderState state = new DroneRenderState();
            state.entityType = DroneModule.DRONE.get();
            Object renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(state);
            if (!(renderer instanceof QuadcopterRenderer)) {
                return StepResult.fail("Renderer für propwash:drone ist " + (renderer == null ? "null" : renderer.getClass().getName()));
            }
            return StepResult.check(DroneRenderResources.INSTANCE.loaded(), "Render-Ressourcen wurden nie geladen");
        });

        SelfTest.clientStep("drone-render: Anbaupunkte", 1, ctx -> {
            DroneRenderResources res = DroneRenderResources.INSTANCE;
            if (!res.layoutFileFound()) {
                ctx.log("WARNUNG: assets/propwash/drone_layout.json fehlt noch, eingebaute Anbaupunkte aktiv");
                return StepResult.pass();
            }
            List<String> missing = new ArrayList<>();
            for (FrameType frame : FrameType.values()) {
                if (frame.builtin() && !res.layoutFromFile(frame)) {
                    missing.add(frame.key());
                }
            }
            return StepResult.check(missing.isEmpty(), "drone_layout.json ohne gültigen Eintrag für " + missing);
        });

        SelfTest.clientStep("drone-render: Bauteil-Modelle", 1, ctx -> {
            var manager = Minecraft.getInstance().getResourceManager();
            List<String> noDefinition = new ArrayList<>();
            for (PartModel part : PartModel.all()) {
                if (manager.getResource(part.itemModelFile()).isEmpty()) {
                    noDefinition.add(part.toString());
                }
            }
            if (!noDefinition.isEmpty()) {
                return StepResult.fail("Render-Definitionen fehlen: " + noDefinition);
            }
            int missing = DroneRenderResources.INSTANCE.missingModels().size();
            if (missing > 0) {
                ctx.log("WARNUNG: " + missing + " Bauteil-Modelle fehlen, Ersatzformen aktiv");
            }
            return StepResult.pass();
        });

        SelfTest.clientStep("drone-render: Presets zeichnen", 1, ctx -> {
            PoseStack poseStack = new PoseStack();
            for (DroneBuild build : DroneBuild.PRESETS) {
                for (int variant = 0; variant < 3; variant++) {
                    DroneRenderState state = new DroneRenderState();
                    DroneVisuals.fillPreview(state, build);
                    if (variant > 0) {
                        float[] outputs = {0.8F, 0.6F, 0.4F, 0.2F};
                        float[] angles = {0.1F, 1.2F, 2.3F, 3.4F};
                        state.orientation.set(new Quaternionf().rotationXYZ(0.3F * variant, 0.7F, -0.2F * variant));
                        DroneVisuals.fill(state, build, true, false, angles, outputs, 10.0F);
                        DroneVisuals.placeInWorld(state);
                    }
                    String problem = checkState(build, state);
                    if (problem != null) {
                        return StepResult.fail(problem);
                    }
                    SubmitNodeStorage storage = new SubmitNodeStorage();
                    DroneVisuals.submit(state, poseStack, storage);
                    if (storage.order(0).solid.isEmpty()) {
                        return StepResult.fail(build.frame().key() + ": nichts gezeichnet");
                    }
                    if (!poseStack.isEmpty()) {
                        return StepResult.fail("PoseStack nach dem Zeichnen nicht leer");
                    }
                    float scale = DroneVisuals.previewScale(state, 120, 80);
                    if (!Float.isFinite(scale) || scale <= 1.0F) {
                        return StepResult.fail(build.frame().key() + ": Vorschau-Skalierung " + scale);
                    }
                }
            }
            return StepResult.pass();
        });

        SelfTest.clientStep("drone-render: Prop-Scheiben, LED-Schein und Geister nach dem Wasser", 1, ctx -> afterWaterStep());

        SelfTest.clientStep("drone-render: Zubehör an den Halterungen", 1, ctx -> accessoryStep());

        SelfTest.clientStep("drone-render: ccw-Props nutzen das gespiegelte Modell", 1, ctx -> {
            DroneRenderResources res = DroneRenderResources.INSTANCE;
            for (DroneBuild build : DroneBuild.PRESETS) {
                DroneRenderState state = new DroneRenderState();
                DroneVisuals.fillPreview(state, build);
                DroneLayout layout = res.layout(build.frame());
                ItemStackRenderState cw = res.item(PartModel.prop(build.prop(), false));
                ItemStackRenderState ccw = res.item(PartModel.prop(build.prop(), true));
                if (cw == null || ccw == null) {
                    ctx.log("WARNUNG: Prop-Modell " + build.prop().key() + " fehlt, Ersatzform aktiv");
                    continue;
                }
                if (cw == ccw) {
                    return StepResult.fail(build.prop().key() + ": cw- und ccw-Modell sind identisch");
                }
                for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
                    ItemStackRenderState expected = layout.counterClockwise(slot) ? ccw : cw;
                    if (state.props[slot].item != expected) {
                        return StepResult.fail(build.frame().key() + ": Prop " + slot + " zeigt die falsche Drehrichtung");
                    }
                }
            }
            return StepResult.pass();
        });

        SelfTest.clientStep("drone-render: beschädigte Props zeigen eigene Modelle", 1, ctx -> {
            DroneRenderResources res = DroneRenderResources.INSTANCE;
            PartWear wear = PartWear.PRISTINE.withProp(DroneLayout.FRONT_LEFT, 0.0F).withProp(DroneLayout.FRONT_RIGHT, 0.3F)
                    .withProp(DroneLayout.REAR_LEFT, 0.8F);
            for (DroneBuild build : DroneBuild.PRESETS) {
                DroneLayout layout = res.layout(build.frame());
                DroneRenderState fresh = new DroneRenderState();
                DroneVisuals.fillPreview(fresh, build);
                DroneRenderState damaged = new DroneRenderState();
                DroneVisuals.fillPreview(damaged, build, wear);
                PropDamage[] expected = {PropDamage.BROKEN, PropDamage.DAMAGED, PropDamage.WORN, PropDamage.NONE};
                for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
                    ItemStackRenderState model = res.item(PartModel.prop(build.prop(), layout.counterClockwise(slot), expected[slot]));
                    if (model == null) {
                        ctx.log("WARNUNG: Schadensmodell für " + build.prop().key() + " fehlt, unbeschädigte Form aktiv");
                        continue;
                    }
                    if (damaged.props[slot].item != model) {
                        return StepResult.fail(build.frame().key() + ": Prop " + slot + " zeigt nicht die Stufe " + expected[slot]);
                    }
                    if (expected[slot] != PropDamage.NONE && damaged.props[slot].item == fresh.props[slot].item) {
                        return StepResult.fail(build.frame().key() + ": Prop " + slot + " sieht trotz Schaden neu aus");
                    }
                }
                DroneVisuals.fill(damaged, build, true, false, new float[]{0.1F, 0.2F, 0.3F, 0.4F}, new float[]{1.0F, 1.0F, 1.0F, 1.0F}, 0.0F);
                boolean blurs = 1.0F > DroneRenderConfig.get().propBlurThreshold;
                if (damaged.propBlur[DroneLayout.FRONT_LEFT] || damaged.propBlur[DroneLayout.REAR_RIGHT] != blurs) {
                    return StepResult.fail(build.frame().key() + ": gebrochener Prop wird als Scheibe gezeichnet oder intakter nicht");
                }
            }
            float[] conditions = {1.0F, 0.83F, 0.32F, 0.0F};
            for (int i = 0; i < conditions.length; i++) {
                ItemStack stack = PartCondition.with(ContentItems.stack(PropType.P51_3B), conditions[i]);
                if (PropDamageProperty.stage(stack) != PropDamage.values()[i]) {
                    return StepResult.fail("Item-Symbol eines Props mit Zustand " + conditions[i] + " zeigt " + PropDamageProperty.stage(stack));
                }
            }
            return StepResult.pass();
        });

        SelfTest.clientStep("drone-render: Bauteile werden je Bauplan zwischengespeichert", 1, ctx -> {
            DroneRenderResources res = DroneRenderResources.INSTANCE;
            for (DroneBuild build : DroneBuild.PRESETS) {
                if (res.assembly(build) != res.assembly(build)) {
                    return StepResult.fail(build.frame().key() + ": Bauplan wird bei jedem Aufruf neu zusammengesetzt");
                }
                DroneRenderState first = new DroneRenderState();
                DroneRenderState second = new DroneRenderState();
                DroneVisuals.fill(first, build, true, false, new float[]{0.1F, 0.2F, 0.3F, 0.4F}, new float[]{0.0F, 0.0F, 0.0F, 0.0F}, 0.0F);
                DroneVisuals.fill(second, build, true, false, new float[]{1.1F, 1.2F, 1.3F, 1.4F}, new float[]{0.0F, 0.0F, 0.0F, 0.0F}, 3.0F);
                if (first.frame.item != second.frame.item || first.motors[0].item != second.motors[0].item
                        || first.props[1].item != second.props[1].item) {
                    return StepResult.fail(build.frame().key() + ": Modelle werden pro Bild neu aufgelöst");
                }
                if (first.frame.useItem && first.frame.item == DroneRenderState.PartSlot.NO_ITEM) {
                    return StepResult.fail(build.frame().key() + ": Rahmen-Modell fehlt trotz useItem");
                }
                if (first.rotorAngle[2] != 0.3F || second.rotorAngle[2] != 1.3F) {
                    return StepResult.fail(build.frame().key() + ": Prop-Winkel werden zwischen Zuständen geteilt");
                }
            }
            return StepResult.pass();
        });

        SelfTest.clientStep("drone-render: alte Standardgröße 1,75× wird einmal 1,0× mit Hinweis", 60, DroneRenderSelfTests::sizeMigrationStep);
        SelfTest.clientStep("drone-render: Einstellungs-Tab ändert erst beim Speichern", 1, DroneRenderSelfTests::tabDraftStep);

        SelfTest.clientStep("drone-render: Config und Einstellungs-Tab", 1, ctx -> {
            if (!DroneRenderConfig.CONFIG.isLoaded() || !Files.isRegularFile(DroneRenderConfig.CONFIG.path())) {
                return StepResult.fail("config/propwash/drone_render.json fehlt");
            }
            boolean tab = ConfigTabs.create().stream().anyMatch(t -> "drone_render".equals(t.id()));
            return StepResult.check(tab, "Einstellungs-Tab drone_render nicht angemeldet");
        });

        SelfTest.clientStep("drone-render: Drohne in der Welt", 200, DroneRenderSelfTests::worldStep);
        DroneItemSelfTests.register();
        LedSelfTests.register();
        PartsRenderSelfTests.register();
        RaceBatterySelfTests.register();
    }

    private static StepResult afterWaterStep() {
        List<RenderType> overlays = List.of(AfterWaterRenderTypes.blur(), AfterWaterRenderTypes.blurItems(DroneRenderResources.WHITE_TEXTURE),
                AfterWaterRenderTypes.halo());
        for (RenderType type : overlays) {
            DepthStencilState depth = type.pipeline().getDepthStencilState();
            if (depth == null || depth.writeDepth()) {
                return StepResult.fail(type + " schreibt Tiefe, Wasser dahinter würde verschwinden");
            }
            if (!AfterWaterRenderTypes.drawsAfterWater(type)) {
                return StepResult.fail(type + " wird nicht nach dem Wasser gezeichnet");
            }
        }
        if (!AfterWaterRenderTypes.drawsAfterWater(AfterWaterRenderTypes.fpvDisc())) {
            return StepResult.fail("Prop-Scheiben vor dem Objektiv werden nicht nach dem Wasser gezeichnet");
        }
        DepthStencilState fpvDepth = AfterWaterRenderTypes.fpvDisc().pipeline().getDepthStencilState();
        if (fpvDepth == null || !fpvDepth.writeDepth()) {
            return StepResult.fail("Prop-Scheiben vor dem Objektiv schreiben keine Tiefe, Wolken und Partikel würden sie überdecken");
        }
        if (!AfterWaterRenderTypes.drawsAfterWater(AfterWaterRenderTypes.ghost(DroneRenderResources.WHITE_TEXTURE))) {
            return StepResult.fail("Geister werden nicht nach dem Wasser gezeichnet");
        }
        if (AfterWaterRenderTypes.drawsAfterWater(RenderTypes.entityTranslucent(DroneRenderResources.WHITE_TEXTURE))) {
            return StepResult.fail("fremde durchscheinende Render-Typen werden umgeleitet");
        }
        PoseStack poseStack = new PoseStack();
        DroneBuild build = DroneBuild.FREESTYLE;
        DroneRenderState state = new DroneRenderState();
        float[] outputs = {0.6F, 0.6F, 0.6F, 0.6F};
        float[] angles = {0.1F, 1.2F, 2.3F, 3.4F};
        DroneVisuals.fill(state, build, true, false, angles, outputs, 10.0F);
        DroneVisuals.placeInWorld(state);
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            if (!state.propBlur[slot]) {
                return StepResult.fail("Prop " + slot + " dreht bei 60 % Motorleistung nicht als Scheibe");
            }
        }
        SubmitNodeStorage storage = new SubmitNodeStorage();
        DroneVisuals.submit(state, poseStack, storage);
        if (storage.order(0).afterTerrain.isEmpty()) {
            return StepResult.fail("Prop-Scheiben der Drohne in der Welt werden nicht nach dem Wasser gezeichnet");
        }
        if (!storage.order(0).translucentCustomGeometry.isEmpty()) {
            return StepResult.fail("durchscheinende Drohnen-Teile werden vor dem Wasser gezeichnet");
        }
        state.fpvGeometry = FpvProps.geometry(build, 20.0F);
        if (state.fpvGeometry == null) {
            return StepResult.fail("keine FPV-Geometrie für " + build.frame().key());
        }
        CameraRenderState camera = new CameraRenderState();
        camera.projectionMatrix.setPerspective((float) Math.toRadians(120.0), 16.0F / 9.0F, 0.05F, 256.0F);
        if (!state.fpvGeometry.anyVisible(1.05F / camera.projectionMatrix.m00(), 1.05F / camera.projectionMatrix.m11())) {
            return StepResult.fail("Props im Test-Sichtfeld nicht sichtbar");
        }
        SubmitNodeStorage fpv = new SubmitNodeStorage();
        FpvProps.submit(state, poseStack, fpv, camera);
        if (!poseStack.isEmpty()) {
            return StepResult.fail("PoseStack nach den FPV-Props nicht leer");
        }
        if (fpv.order(0).afterTerrain.isEmpty()) {
            return StepResult.fail("Prop-Scheiben im FPV-Bild werden nicht nach dem Wasser gezeichnet");
        }
        if (!fpv.order(0).translucentCustomGeometry.isEmpty()) {
            return StepResult.fail("Prop-Scheiben im FPV-Bild werden vor dem Wasser gezeichnet (Wasser dahinter verschwindet)");
        }
        return StepResult.pass();
    }

    private static StepResult accessoryStep() {
        DroneRenderResources res = DroneRenderResources.INSTANCE;
        List<String> missing = new ArrayList<>();
        for (AccessoryType type : AccessoryType.values()) {
            if (type.builtin() && !res.hasModel(PartModel.of(type))) {
                missing.add(type.key());
            }
        }
        for (AccessoryMount mount : AccessoryMount.values()) {
            PartModel.base(mount).filter(model -> !res.hasModel(model)).ifPresent(model -> missing.add(model.toString()));
        }
        if (!missing.isEmpty()) {
            return StepResult.fail("Zubehör-Modelle fehlen: " + missing);
        }
        PoseStack poseStack = new PoseStack();
        for (DroneBuild preset : DroneBuild.PRESETS) {
            DroneLayout layout = res.layout(preset.frame());
            if (!layout.accessories().keySet().equals(preset.frame().accessoryMounts)) {
                return StepResult.fail(preset.frame().key() + ": Halterungen " + layout.accessories().keySet());
            }
            float bareTop = res.assembly(preset).hull().max().y();
            for (AccessoryType type : AccessoryType.values()) {
                if (!type.builtin() || !preset.frame().accessoryMounts.contains(type.mount)) {
                    continue;
                }
                DroneBuild build = preset.withAccessory(type);
                String where = preset.frame().key() + " + " + type.key();
                DroneRenderState state = new DroneRenderState();
                DroneVisuals.fillPreview(state, build);
                int i = type.mount.ordinal();
                if (state.accessoryType[i] != type || !state.accessories[i].useItem) {
                    return StepResult.fail(where + ": Zubehör wird nicht als Modell gezeichnet");
                }
                AccessoryAnchor anchor = layout.accessory(type.mount).orElseThrow();
                if (state.accessories[i].position.distance((float) anchor.x(), (float) anchor.y(), (float) anchor.z()) > 0.01F
                        || Math.abs(state.accessoryTilt[i] - (float) anchor.tiltFor(type)) > 0.01F) {
                    return StepResult.fail(where + ": sitzt nicht an der Halterung");
                }
                if (type.mount.hasBase() != state.accessoryBases[i].useItem) {
                    return StepResult.fail(where + ": Halterungssockel falsch");
                }
                if (type == AccessoryType.ACTION_CAM_HEAVY && res.assembly(build).hull().max().y() < bareTop + 10.0F) {
                    return StepResult.fail(where + ": Kamera ragt nicht über die Drohne");
                }
                String problem = checkState(build, state);
                if (problem != null) {
                    return StepResult.fail(where + ": " + problem);
                }
                SubmitNodeStorage storage = new SubmitNodeStorage();
                DroneVisuals.submit(state, poseStack, storage);
                if (!poseStack.isEmpty()) {
                    return StepResult.fail(where + ": PoseStack nach dem Zeichnen nicht leer");
                }
            }
        }
        return StepResult.pass();
    }

    private static String checkState(DroneBuild build, DroneRenderState state) {
        String key = build.frame().key();
        if (!state.frame.useItem && state.frame.solid.isEmpty()) {
            return key + ": Rahmen fehlt";
        }
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            DroneRenderState.PartSlot motor = state.motors[slot];
            DroneRenderState.PartSlot prop = state.props[slot];
            if (!motor.useItem && motor.solid.isEmpty()) {
                return key + ": Motor " + slot + " fehlt";
            }
            if (!prop.useItem && prop.solid.isEmpty() && prop.translucent.isEmpty()) {
                return key + ": Prop " + slot + " fehlt";
            }
            if (prop.position.y() <= motor.position.y()) {
                return key + ": Prop " + slot + " sitzt nicht über dem Motor";
            }
        }
        if (!Float.isFinite(state.originY) || !Float.isFinite(state.pivot.y) || state.visualRadius <= 0.0F || state.visualRadius > 2.0F) {
            return key + ": ungültige Lage (origin=" + state.originY + ", radius=" + state.visualRadius + ")";
        }
        return null;
    }

    private static StepResult worldStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            AtomicInteger id = new AtomicInteger(-1);
            ctx.state().put(DRONE_ID, id);
            ctx.state().put(SPAWN, ctx.onServer(server -> {
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                if (players.isEmpty()) {
                    throw new IllegalStateException("kein Spieler auf dem Server");
                }
                ServerPlayer player = players.getFirst();
                ServerLevel level = player.level();
                QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
                if (drone == null) {
                    throw new IllegalStateException("EntityType.create lieferte null");
                }
                Vec3 pos = player.position().add(2.0, 0.0, 2.0);
                drone.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
                drone.setBuild(DroneBuild.RACER);
                level.addFreshEntity(drone);
                id.set(drone.getId());
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> spawn = (CompletableFuture<?>) ctx.state().get(SPAWN);
        if (spawn.isCompletedExceptionally()) {
            return StepResult.fail("Spawn fehlgeschlagen");
        }
        int id = ((AtomicInteger) ctx.state().get(DRONE_ID)).get();
        if (!spawn.isDone() || id < 0 || mc.level == null) {
            return StepResult.pending();
        }
        Entity entity = mc.level.getEntity(id);
        if (!(entity instanceof QuadcopterEntity drone) || !DroneBuild.RACER.equals(drone.build())) {
            if (ctx.tick() >= 199) {
                discard(ctx, id);
                return StepResult.fail("Drohne kam nicht synchronisiert beim Client an");
            }
            return StepResult.pending();
        }
        EntityRenderState extracted;
        try {
            drone.setArmed(true);
            drone.setMotorOutputs(0.7F, 0.7F, 0.7F, 0.7F);
            extracted = mc.getEntityRenderDispatcher().extractEntity(drone, 1.0F);
        } finally {
            discard(ctx, id);
        }
        if (!(extracted instanceof DroneRenderState state)) {
            return StepResult.fail("Render-State ist " + extracted.getClass().getName());
        }
        String problem = checkState(DroneBuild.RACER, state);
        if (problem != null) {
            return StepResult.fail(problem);
        }
        if (!state.armed || state.hidden) {
            return StepResult.fail("armed=" + state.armed + " hidden=" + state.hidden);
        }
        float threshold = DroneRenderConfig.get().propBlurThreshold;
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            if (state.propBlur[slot] != state.motorOutput[slot] > threshold) {
                return StepResult.fail("Prop " + slot + ": Unschärfe passt nicht zur Motorleistung " + state.motorOutput[slot]);
            }
        }
        ctx.log("Motorleistung je Motor: " + Arrays.toString(state.motorOutput));
        return StepResult.pass();
    }

    private static DroneRenderConfig sizeBefore;

    private static StepResult sizeMigrationStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            sizeBefore = DroneRenderConfig.get().copy();
            DroneRenderConfig custom = sizeBefore.copy();
            custom.version = 0;
            custom.visualScale = 2.0F;
            custom.sizeNotice = false;
            DroneRenderConfig.CONFIG.set(custom);
            if (DroneRenderConfig.get().visualScale != 2.0F || DroneRenderConfig.get().sizeNotice
                    || DroneRenderConfig.get().version != DroneRenderConfig.VERSION) {
                return restoreSize(StepResult.fail("eigene Größe 2,0× aus 0.4.0 wurde verändert"));
            }
            DroneRenderConfig legacy = sizeBefore.copy();
            legacy.version = 0;
            legacy.visualScale = DroneRenderConfig.LEGACY_DEFAULT_VISUAL_SCALE;
            legacy.sizeNotice = false;
            DroneRenderConfig.CONFIG.set(legacy);
            DroneRenderConfig migrated = DroneRenderConfig.get();
            if (migrated.visualScale != DroneRenderConfig.DEFAULT_VISUAL_SCALE || !migrated.sizeNotice
                    || migrated.version != DroneRenderConfig.VERSION) {
                return restoreSize(StepResult.fail("1,75× aus 0.4.0 nicht auf 1,0× umgestellt (" + migrated.visualScale + "×)"));
            }
            return StepResult.pending();
        }
        ThemedToast toast = ThemedToast.find(minecraft.gui.toastManager(), DroneSizeNotice.TOAST_TOKEN);
        if (toast == null || DroneRenderConfig.get().sizeNotice) {
            return StepResult.pending();
        }
        toast.hide();
        DroneRenderConfig again = DroneRenderConfig.get().copy();
        again.visualScale = DroneRenderConfig.LEGACY_DEFAULT_VISUAL_SCALE;
        DroneRenderConfig.CONFIG.set(again);
        if (DroneRenderConfig.get().visualScale != DroneRenderConfig.LEGACY_DEFAULT_VISUAL_SCALE || DroneRenderConfig.get().sizeNotice) {
            return restoreSize(StepResult.fail("1,75× nach der Umstellung bewusst gewählt, aber erneut umgestellt"));
        }
        return restoreSize(StepResult.pass());
    }

    private static StepResult restoreSize(StepResult result) {
        if (sizeBefore != null) {
            DroneRenderConfig restore = sizeBefore.copy();
            restore.sizeNotice = false;
            DroneRenderConfig.CONFIG.set(restore);
            sizeBefore = null;
        }
        return result;
    }

    private static StepResult tabDraftStep(SelfTest.StepContext ctx) {
        DroneRenderConfigTab tab = null;
        for (ConfigTab candidate : ConfigTabs.create()) {
            if (candidate instanceof DroneRenderConfigTab found) {
                tab = found;
            }
        }
        if (tab == null) {
            return StepResult.fail("Einstellungs-Tab drone_render nicht angemeldet");
        }
        DroneRenderConfig live = DroneRenderConfig.get();
        DroneRenderConfig before = live.copy();
        tab.attach(new SelfTestHost(), 0, 0, 320, 200);
        float changedScale = before.visualScale >= 2.0F ? 1.25F : 2.5F;
        tab.draft().visualScale = changedScale;
        tab.draft().armLeds = !before.armLeds;
        tab.draft().renderDistance = before.renderDistance == 64 ? 96 : 64;
        if (DroneRenderConfig.get() != live || live.visualScale != before.visualScale || live.armLeds != before.armLeds
                || live.renderDistance != before.renderDistance) {
            return StepResult.fail("Tab verändert die aktive Config vor dem Speichern");
        }
        tab.resetDefaults();
        if (live.visualScale != before.visualScale || live.armLeds != before.armLeds) {
            return StepResult.fail("Standardwerte wirken ohne Speichern auf die aktive Config");
        }
        tab.draft().visualScale = changedScale;
        try {
            tab.save();
            if (DroneRenderConfig.get().visualScale != changedScale) {
                return StepResult.fail("Speichern übernimmt die Drohnengröße nicht (" + DroneRenderConfig.get().visualScale + ")");
            }
        } finally {
            DroneRenderConfig.CONFIG.set(before);
        }
        return StepResult.pass();
    }

    private static final class SelfTestHost implements ConfigTabHost {
        @Override
        public <T extends AbstractWidget> T addWidget(T widget) {
            return widget;
        }

        @Override
        public Font font() {
            return Minecraft.getInstance().font;
        }

        @Override
        public Minecraft minecraft() {
            return Minecraft.getInstance();
        }

        @Override
        public Screen screen() {
            return Minecraft.getInstance().gui.screen();
        }

        @Override
        public void rebuild() {
        }

        @Override
        public void changed() {
        }
    }

    private static void discard(SelfTest.StepContext ctx, int id) {
        ctx.onServer(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntity(id);
                if (e != null) {
                    e.discard();
                }
            }
        });
    }
}
