package de.lardtom.propwash.drone.client.render;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.BatteryFit;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.PartCondition;
import de.lardtom.propwash.content.client.ItemSizeRule;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.video.FovMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

public final class PartsRenderSelfTests {
    static final String EXAMPLE_NAMESPACE = "propwash_example";

    private PartsRenderSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("drone-render: fremde Teile ohne Modell zeigen die Form aus der Definition", 1, ctx -> proceduralStep());
        SelfTest.clientStep("drone-render: Modelle aus einem fremden Namensraum", 1, ctx -> namespaceModelStep());
        SelfTest.clientStep("drone-render: skalierte Drohnen-Modelle (transformation) in echter Größe, Hülle ungekappt", 1,
                ctx -> scaledModelStep(ctx));
        SelfTest.clientStep("drone-render: Lackierung erreicht Teile mit Form aus der Definition", 1, ctx -> fallbackPaintStep());
        SelfTest.clientStep("drone-render: verkleinerte Drohnen-Items zeichnen lesbare Formen", 1, ctx -> readableItemStep());
        SelfTest.clientStep("drone-render: Teile des Beispiel-Addons", 1, PartsRenderSelfTests::exampleAddonStep);
        SelfTest.clientStep("drone-render: Sonder-Renderer propwash:part", 1, ctx -> partIconStep());
        SelfTest.clientStep("drone-render: Props im FPV-Bild (Geometrie und Schalter)", 1, ctx -> fpvGeometryStep());
        FpvCoverageSelfTests.register();
        FpvGuardSelfTests.register();
        PartsRenderShowcase.register();
    }

    static String drawAll(DroneBuild build, boolean expectModels) {
        String key = build.frame().key();
        PoseStack poseStack = new PoseStack();
        DroneRenderState state = new DroneRenderState();
        DroneVisuals.fillPreview(state, build);
        if (state.frame.useItem != expectModels) {
            return key + ": Rahmen " + (expectModels ? "ohne Modell" : "als Modell statt als Form");
        }
        if (!state.frame.useItem && state.frame.solid.isEmpty()) {
            return key + ": keine Rahmenform";
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
            Vector3fc expected = DroneVisuals.resources().layout(build.frame()).motor(slot);
            if (motor.position.distance(expected) > 0.01F) {
                return key + ": Motor " + slot + " sitzt nicht am Arm";
            }
        }
        if (!Float.isFinite(state.visualRadius) || state.visualRadius <= 0.0F || !Float.isFinite(state.pivot.y)) {
            return key + ": ungültige Hülle " + state.visualRadius;
        }
        for (int variant = 0; variant < 2; variant++) {
            DroneRenderState flying = new DroneRenderState();
            flying.orientation.set(new Quaternionf().rotationXYZ(0.2F, 0.8F, -0.3F));
            DroneVisuals.fill(flying, build, variant == 1, false, new float[]{0.1F, 1.2F, 2.3F, 3.4F}, new float[]{0.8F, 0.7F, 0.6F, 0.5F}, 5.0F);
            DroneVisuals.placeInWorld(flying);
            if (!Float.isFinite(flying.originY)) {
                return key + ": Lage in der Welt ungültig";
            }
            SubmitNodeStorage storage = new SubmitNodeStorage();
            DroneVisuals.submit(flying, poseStack, storage);
            if (storage.order(0).solid.isEmpty()) {
                return key + ": nichts gezeichnet";
            }
            if (!poseStack.isEmpty()) {
                return key + ": PoseStack nach dem Zeichnen nicht leer";
            }
        }
        float scale = DroneVisuals.previewScale(state, 120, 80);
        if (!Float.isFinite(scale) || scale <= 1.0F) {
            return key + ": Werkbank-Vorschau-Skalierung " + scale;
        }
        DroneItemRenderer.Look look = new DroneItemRenderer.Look(build, PartWear.PRISTINE, DronePaint.DEFAULT, true);
        DroneItemRenderer.Fitted fitted = DroneItemRenderer.fitted(look);
        if (!Float.isFinite(fitted.scale()) || fitted.fit() < DroneItemRenderer.MIN_FIT - 1.0E-4F || fitted.fit() > DroneItemRenderer.MAX_FIT + 1.0E-4F) {
            return key + ": Drohnen-Item passt nicht in den Slot (" + fitted.fit() + ")";
        }
        SubmitNodeStorage itemStorage = new SubmitNodeStorage();
        new DroneItemRenderer(false).submit(look, poseStack, itemStorage, 0xF000F0, 0, false, 0);
        if (itemStorage.order(0).solid.isEmpty() || !poseStack.isEmpty()) {
            return key + ": Drohnen-Item zeichnet nichts";
        }
        if (!DroneGhost.submit(build, DronePaint.DEFAULT, new Quaternionf(), 0x9966AAFF, poseStack, new SubmitNodeStorage())) {
            return key + ": Geist konnte nicht gezeichnet werden";
        }
        if (!poseStack.isEmpty()) {
            return key + ": PoseStack nach dem Geist nicht leer";
        }
        float fit = DroneVisuals.sceneFit(build);
        if (!(fit > 0.0F && fit <= 1.0F)) {
            return key + ": Flugschul-Skalierung " + fit;
        }
        return null;
    }

    static StepResult proceduralStep() {
        DroneRenderResources res = DroneVisuals.resources();
        for (PartInfo part : RenderExampleParts.WITHOUT_MODELS) {
            if (res.hasModel(PartModel.of(part))) {
                return StepResult.fail(part.id() + " hat unerwartet ein Modell");
            }
        }
        for (DroneBuild build : RenderExampleParts.PROCEDURAL_BUILDS) {
            String problem = drawAll(build, false);
            if (problem != null) {
                return StepResult.fail(problem);
            }
        }
        for (PartInfo part : RenderExampleParts.WITHOUT_MODELS) {
            if (PartItemRenderer.fit(part, PropDamage.NONE, res).isEmpty()) {
                return StepResult.fail(part.id() + ": kein Inventar-Symbol aus der Form");
            }
        }
        return StepResult.pass();
    }

    static boolean hasColor(Shape shape, int rgb) {
        for (int v = 0; v < shape.vertexCount(); v++) {
            if ((shape.color(v) & 0xFFFFFF) == (rgb & 0xFFFFFF)) {
                return true;
            }
        }
        return false;
    }

    static StepResult fallbackPaintStep() {
        int frame = 0x2266DD;
        DroneRenderState stretched = new DroneRenderState();
        DroneVisuals.fillPreview(stretched, RenderExampleParts.STRETCHED_BUILD, PartWear.PRISTINE, DronePaint.DEFAULT.with(PaintSlot.FRAME, frame));
        if (stretched.frame.useItem || stretched.frame.solid.isEmpty()) {
            return StepResult.fail("Stretched-X ohne Modell zeichnet keine Form aus der Definition");
        }
        if (!hasColor(stretched.frame.solid, frame)) {
            return StepResult.fail("Rahmen-Lack erreicht die Form aus der Definition nicht");
        }
        DroneData data = ExampleAddon.xclass();
        DroneBuild build = Conversions.build(data.build()).orElse(null);
        if (build == null) {
            return StepResult.fail("X-Class des Beispiel-Addons nicht baubar");
        }
        DronePaint paint = Conversions.paint(data.paint());
        int yellow = paint.color(PaintSlot.PROP_FRONT_LEFT).orElse(-1);
        int motor = paint.color(PaintSlot.MOTORS).orElse(-1);
        DroneRenderState state = new DroneRenderState();
        DroneVisuals.fillPreview(state, build, PartWear.PRISTINE, paint);
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            DroneRenderState.PartSlot prop = state.props[slot];
            if (prop.useItem) {
                return StepResult.fail("Beispiel-Prop hat unerwartet ein Modell");
            }
            boolean front = slot < 2;
            if (hasColor(prop.solid, yellow) != front) {
                return StepResult.fail("X-Class des Beispiels: Prop " + slot + (front ? " erscheint nicht gelb" : " ist fälschlich gelb"));
            }
            if (!state.motors[slot].useItem && !hasColor(state.motors[slot].solid, motor)) {
                return StepResult.fail("X-Class des Beispiels: Motor-Lack erreicht Motor " + slot + " nicht");
            }
        }
        return StepResult.pass();
    }

    static StepResult readableItemStep() {
        DroneBuild exampleXClass = Conversions.build(ExampleAddon.xclass().build())
                .orElse(null);
        if (exampleXClass == null) {
            return StepResult.fail("X-Class des Beispiel-Addons nicht baubar");
        }
        DroneItemRenderer.Fitted fitted = DroneItemRenderer.fitted(new DroneItemRenderer.Look(exampleXClass, PartWear.PRISTINE, DronePaint.DEFAULT,
                true));
        if (!(fitted.scale() < DroneItemRenderer.ICON_SHAPES_BELOW)) {
            return StepResult.fail("X-Class wird im Slot nicht verkleinert: " + fitted.scale());
        }
        DroneRenderResources res = DroneVisuals.resources();
        if (!fitted.state().frame.useItem && fitted.state().frame.solid != FallbackShapes.frameIcon(exampleXClass.frame(), res.layout(exampleXClass.frame()))) {
            return StepResult.fail("verkleinerte X-Class zeichnet im Slot nicht die kräftigere Rahmenform");
        }
        if (!fitted.state().props[0].useItem && fitted.state().props[0].solid != FallbackShapes.propWide(exampleXClass.prop(),
                res.layout(exampleXClass.frame()).counterClockwise(0))) {
            return StepResult.fail("verkleinerte X-Class zeichnet im Slot nicht die breiteren Prop-Blätter");
        }
        DroneItemRenderer.Fitted freestyle = DroneItemRenderer.fitted(new DroneItemRenderer.Look(DroneBuild.FREESTYLE, PartWear.PRISTINE,
                DronePaint.DEFAULT, true));
        if (freestyle.state().frame.solid == FallbackShapes.frameIcon(DroneBuild.FREESTYLE.frame(), res.layout(DroneBuild.FREESTYLE.frame()))) {
            return StepResult.fail("Freestyle-Item benutzt die Symbolform statt des Modells");
        }
        return StepResult.pass();
    }

    static StepResult scaledModelStep(SelfTest.StepContext ctx) {
        DroneRenderResources res = DroneVisuals.resources();
        PartModel model = PartModel.of(RenderExampleParts.X_CLASS_SCALED);
        if (!res.hasModel(model)) {
            return StepResult.fail("skaliertes Modell " + model + " fehlt oder wurde abgelehnt");
        }
        ItemStackRenderState item = res.item(model);
        if (item == null) {
            return StepResult.fail("skaliertes Modell " + model + " lässt sich nicht auflösen");
        }
        AABB box = item.getModelBoundingBox();
        double widthMm = Math.max(box.getXsize(), box.getZsize()) * DroneVisualScale.MILLIMETRES_PER_MODEL_BLOCK;
        double expected = 2.0 * (820.0 / (2.0 * Math.sqrt(2.0)) + 30.0);
        if (Math.abs(widthMm - expected) > expected * 0.1) {
            return StepResult.fail(String.format(Locale.ROOT, "transformation nicht angewandt: Rahmen %.0f mm breit statt etwa %.0f mm", widthMm, expected));
        }
        if (!VisualHull.clampPartBounds(box).equals(box)) {
            return StepResult.fail("Hülle des skalierten Rahmens wird gekappt: " + box);
        }
        String problem = drawAll(RenderExampleParts.X_CLASS_SCALED_BUILD, true);
        if (problem != null) {
            return StepResult.fail(problem);
        }
        DroneAssembly scaled = res.assembly(RenderExampleParts.X_CLASS_SCALED_BUILD);
        if (scaled.frame().item() == null) {
            return StepResult.fail("Baugruppe zeichnet den skalierten Rahmen nicht als Modell");
        }
        VisualHull hull = scaled.hull();
        float frameX = Math.max(Math.abs(hull.min().x()), Math.abs(hull.max().x()));
        if (frameX * 2.0F < widthMm * 0.95F) {
            return StepResult.fail(String.format(Locale.ROOT, "Hülle der Drohne kleiner als der Rahmen: %.0f mm gegen %.0f mm", frameX * 2.0F, widthMm));
        }
        DroneRenderState state = DroneVisuals.previewState(RenderExampleParts.X_CLASS_SCALED_BUILD, PartWear.PRISTINE, DronePaint.DEFAULT);
        DroneRenderState procedural = DroneVisuals.previewState(RenderExampleParts.X_CLASS_BUILD, PartWear.PRISTINE, DronePaint.DEFAULT);
        if (state == null || procedural == null) {
            return StepResult.fail("keine Vorschau für die X-Class");
        }
        float fitScaled = DroneVisuals.previewScale(state, 120, 80);
        float fitProcedural = DroneVisuals.previewScale(procedural, 120, 80);
        if (Math.abs(fitScaled - fitProcedural) > fitProcedural * 0.12F) {
            return StepResult.fail(String.format(Locale.ROOT, "Vorschau passt die skalierte X-Class anders ein (%.2f gegen %.2f)", fitScaled, fitProcedural));
        }
        ctx.log(String.format(Locale.ROOT, "skalierter X-Class-Rahmen %.0f mm (erwartet %.0f), Vorschau-Maßstab %.2f gegen %.2f prozedural", widthMm,
                expected, fitScaled, fitProcedural));
        return StepResult.pass();
    }

    static StepResult namespaceModelStep() {
        DroneRenderResources res = DroneVisuals.resources();
        if (!Minecraft.getInstance().getResourceManager().getNamespaces().contains(RenderExampleParts.NS)) {
            return StepResult.fail("Namensraum " + RenderExampleParts.NS + " fehlt: common/src/devtest/resources wird nicht mitgeladen "
                    + "(Loader-Quell-Set devtest braucht die Ressourcen aus common)");
        }
        List<String> missing = new ArrayList<>();
        for (PartInfo part : RenderExampleParts.WITH_MODELS) {
            for (PartModel model : PartModel.variants(part)) {
                if (!res.hasModel(model)) {
                    missing.add(model.toString());
                }
            }
        }
        if (!missing.isEmpty()) {
            return StepResult.fail("Modelle aus propwash_rendertest fehlen: " + missing);
        }
        for (DroneBuild build : RenderExampleParts.MODEL_BUILDS) {
            String problem = drawAll(build, true);
            if (problem != null) {
                return StepResult.fail(problem);
            }
        }
        DroneRenderState state = new DroneRenderState();
        DroneVisuals.fillPreview(state, RenderExampleParts.X_CLASS_BUILD);
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            if (!state.motors[slot].useItem || !state.props[slot].useItem) {
                return StepResult.fail("X-Class: Motor/Prop " + slot + " ohne Modell");
            }
        }
        if (!state.battery.useItem || !state.stack.useItem || !state.video.useItem) {
            return StepResult.fail("X-Class: Akku, Stack oder Video ohne Modell");
        }
        ItemStackRenderState cw = res.item(PartModel.prop(RenderExampleParts.P13, false));
        ItemStackRenderState ccw = res.item(PartModel.prop(RenderExampleParts.P13, true));
        if (cw == null || ccw == null || cw == ccw) {
            return StepResult.fail("13-Zoll-Prop: cw/ccw-Modelle fehlen oder sind gleich");
        }
        float seat = res.motorSeat(RenderExampleParts.M2806);
        if (!(seat > 5.0F && seat < 40.0F)) {
            return StepResult.fail("motor_seat aus propwash_rendertest/drone_layout.json nicht gelesen: " + seat);
        }
        float hub = DroneAssembly.propHeight(res, res.layout(RenderExampleParts.X_CLASS), RenderExampleParts.X_CLASS_BUILD, true);
        if (Math.abs(hub - res.motorSeat(RenderExampleParts.M4214)) > 0.01F) {
            return StepResult.fail("Prop sitzt nicht auf dem Motorsitz: " + hub);
        }
        if (res.paintGroups(PartModel.of(RenderExampleParts.DEADCAT)).isEmpty()) {
            return StepResult.fail("drone_paint.json aus propwash_rendertest nicht gelesen");
        }
        DronePaint paint = DronePaint.DEFAULT.with(PaintSlot.FRAME, 0x3355AA).with(PaintSlot.MOTORS, 0xAA3355);
        DroneRenderState painted = new DroneRenderState();
        DroneVisuals.fillPreview(painted, RenderExampleParts.DEADCAT_BUILD, PartWear.PRISTINE, paint);
        DroneRenderState plain = new DroneRenderState();
        DroneVisuals.fillPreview(plain, RenderExampleParts.DEADCAT_BUILD);
        if (painted.frame.item == plain.frame.item) {
            return StepResult.fail("Lack-Variante des fremden Rahmens wird nicht benutzt");
        }
        return StepResult.pass();
    }

    static StepResult exampleAddonStep(SelfTest.StepContext ctx) {
        List<PartInfo> parts = new ArrayList<>();
        for (PartInfo part : PartIndex.all()) {
            if (EXAMPLE_NAMESPACE.equals(part.id().getNamespace())) {
                parts.add(part);
            }
        }
        if (parts.isEmpty()) {
            ctx.log("WARNUNG: keine Teile im Namensraum " + EXAMPLE_NAMESPACE + " angemeldet, Schritt übersprungen");
            return StepResult.pass();
        }
        DroneRenderResources res = DroneVisuals.resources();
        int models = 0;
        for (PartInfo part : parts) {
            if (res.hasModel(PartModel.of(part))) {
                models++;
            }
            if (PartItemRenderer.fit(part, PropDamage.NONE, res).isEmpty()) {
                return StepResult.fail(part.id() + ": weder Modell noch Form fürs Inventar-Symbol");
            }
        }
        int drones = 0;
        for (PartInfo part : parts) {
            if (!(part instanceof FrameType frame)) {
                continue;
            }
            Optional<DroneBuild> build = buildFor(frame);
            if (build.isEmpty()) {
                ctx.log("WARNUNG: kein passender Bauplan für " + frame.id());
                continue;
            }
            String problem = drawAll(build.get(), res.hasModel(PartModel.of(frame)));
            if (problem != null) {
                return StepResult.fail(problem);
            }
            drones++;
        }
        ctx.log(parts.size() + " Beispiel-Teile, " + models + " mit eigenem Modell, " + drones + " Drohnen gezeichnet");
        return StepResult.pass();
    }

    static Optional<DroneBuild> buildFor(FrameType frame) {
        StackType stack = first(StackType.values(), s -> frame.stackMounts.contains(s.mount), frame);
        MotorType motor = first(MotorType.values(), m -> frame.motorMounts.contains(m.mount), frame);
        PropType prop = null;
        for (PropType candidate : PropType.values()) {
            if (candidate.diameterMm <= frame.maxPropMm && candidate.diameterMm >= frame.minPropMm
                    && (prop == null || preferred(candidate, prop, frame))) {
                prop = candidate;
            }
        }
        BatteryType battery = null;
        for (BatteryType candidate : BatteryType.values()) {
            if (BatteryFit.allowed(BatteryFit.check(frame, candidate))) {
                battery = candidate;
                if (candidate.id().getNamespace().equals(frame.id().getNamespace())) {
                    break;
                }
            }
        }
        VideoType video = first(VideoType.values(), v -> true, frame);
        if (stack == null || motor == null || prop == null || battery == null || video == null) {
            return Optional.empty();
        }
        return Optional.of(new DroneBuild(frame, stack, motor, prop, video, battery));
    }

    private static boolean preferred(PropType candidate, PropType current, FrameType frame) {
        boolean sameNs = candidate.id().getNamespace().equals(frame.id().getNamespace());
        boolean currentNs = current.id().getNamespace().equals(frame.id().getNamespace());
        if (sameNs != currentNs) {
            return sameNs;
        }
        return candidate.diameterMm > current.diameterMm;
    }

    private static <T extends PartInfo> T first(T[] values, Predicate<T> accepts, FrameType frame) {
        T fallback = null;
        for (T value : values) {
            if (!accepts.test(value)) {
                continue;
            }
            if (value.id().getNamespace().equals(frame.id().getNamespace())) {
                return value;
            }
            if (fallback == null) {
                fallback = value;
            }
        }
        return fallback;
    }

    static StepResult partIconStep() {
        PartItemRenderer.Unbaked unbaked = PartItemRenderer.Unbaked.MAP_CODEC.codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"part\": \"propwash:m2207_1750\"}")).result().orElse(null);
        if (unbaked == null || unbaked.part().isEmpty()) {
            return StepResult.fail("Codec von propwash:part liest das Feld part nicht");
        }
        PartItemRenderer.Unbaked boxed = PartItemRenderer.Unbaked.MAP_CODEC.codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"box\": true}")).result().orElse(null);
        if (boxed == null || !boxed.box() || boxed.wall() || boxed.ground()) {
            return StepResult.fail("Codec von propwash:part liest das Feld box nicht");
        }
        PartItemRenderer.Unbaked grounded = PartItemRenderer.Unbaked.MAP_CODEC.codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"ground\": true}")).result().orElse(null);
        if (grounded == null || !grounded.ground() || grounded.box() || grounded.wall()) {
            return StepResult.fail("Codec von propwash:part liest das Feld ground nicht");
        }
        PartItemRenderer.Unbaked handed = PartItemRenderer.Unbaked.MAP_CODEC.codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"hand\": true}")).result().orElse(null);
        if (handed == null || !handed.hand() || handed.box() || handed.ground() || handed.wall()) {
            return StepResult.fail("Codec von propwash:part liest das Feld hand nicht");
        }
        PartItemRenderer free = (PartItemRenderer) new PartItemRenderer.Unbaked(Optional.empty(), false, false, false, false).bake(null);
        PartItemRenderer.Icon motor = free.extractArgument(ContentItems.stack(MotorType.M2207_1750));
        if (motor == null || !MotorType.M2207_1750.id().equals(motor.part())) {
            return StepResult.fail("Argument eines Motor-Items: " + motor);
        }
        ItemStack broken = PartCondition.with(ContentItems.stack(PropType.P51_3B), 0.0F);
        PartItemRenderer.Icon prop = free.extractArgument(broken);
        if (prop == null || prop.damage() != PropDamage.BROKEN) {
            return StepResult.fail("Argument eines kaputten Props: " + prop);
        }
        PartItemRenderer fixed = (PartItemRenderer) unbaked.bake(null);
        PartItemRenderer.Icon forced = fixed.extractArgument(new ItemStack(Items.STICK));
        if (forced == null || !MotorType.M2207_1750.id().equals(forced.part())) {
            return StepResult.fail("fest eingestelltes Teil wird ignoriert: " + forced);
        }
        List<Vector3fc> extents = new ArrayList<>();
        free.getExtents(extents::add);
        if (extents.size() != 8) {
            return StepResult.fail("getExtents liefert " + extents.size() + " Ecken");
        }
        PoseStack poseStack = new PoseStack();
        for (PartKind kind : PartKind.values()) {
            for (PartInfo part : PartIndex.all(kind)) {
                PartItemRenderer.Fitted fitted = PartItemRenderer.fitted(new PartItemRenderer.Icon(part.id(), PropDamage.NONE));
                if (fitted.isEmpty() || !Float.isFinite(fitted.scale()) || fitted.scale() <= 0.0F) {
                    return StepResult.fail(part.id() + ": Symbol leer oder Skalierung " + fitted.scale());
                }
                PartItemRenderer.Fitted box = PartItemRenderer.boxFitted(new PartItemRenderer.Icon(part.id(), PropDamage.NONE));
                if (box.isEmpty() || !Float.isFinite(box.scale()) || box.scale() <= 0.0F) {
                    return StepResult.fail(part.id() + ": Symbol mit \"box\" leer oder Skalierung " + box.scale());
                }
                PartItemRenderer.Fitted ground = PartItemRenderer.groundFitted(new PartItemRenderer.Icon(part.id(), PropDamage.NONE));
                if (ground.isEmpty() || !Float.isFinite(ground.scale()) || ground.scale() <= 0.0F || ground.scale() > box.scale() + 1.0E-6F) {
                    return StepResult.fail(part.id() + ": Symbol mit \"ground\" leer oder Skalierung " + ground.scale() + " (box " + box.scale() + ")");
                }
                PartItemRenderer.Fitted hand = PartItemRenderer.handFitted(new PartItemRenderer.Icon(part.id(), PropDamage.NONE));
                float factor = ItemSizeRule.handFactor(PartItemRenderer.lengthMm(part.id()));
                float ratio = hand.scale() / (fitted.scale() * factor);
                if (hand.isEmpty() || !Float.isFinite(hand.scale()) || ratio < 0.5F || ratio > 2.0F) {
                    return StepResult.fail(part.id() + ": Symbol mit \"hand\" leer oder Skalierung " + hand.scale() + " (GUI " + fitted.scale() + ", Faktor "
                            + factor + ")");
                }
                int before = PartItemRenderer.submitted;
                SubmitNodeStorage storage = new SubmitNodeStorage();
                free.submit(new PartItemRenderer.Icon(part.id(), PropDamage.NONE), poseStack, storage, 0xF000F0, 0, false, 0);
                if (PartItemRenderer.submitted != before + 1 || !poseStack.isEmpty()) {
                    return StepResult.fail(part.id() + ": Symbol nicht gezeichnet");
                }
            }
        }
        for (PartInfo part : RenderExampleParts.WITH_MODELS) {
            PartItemRenderer.Fitted fitted = PartItemRenderer.fit(part, PropDamage.NONE, DroneVisuals.resources());
            if (fitted.item() == null) {
                return StepResult.fail(part.id() + ": Symbol benutzt das vorhandene Modell nicht");
            }
        }
        return StepResult.pass();
    }

    static StepResult fpvGeometryStep() {
        if (!new DroneRenderConfig().fpvProps) {
            return StepResult.fail("Props im FPV-Bild sind standardmäßig aus");
        }
        float wideX = FpvPropGeometry.tanHalfFromDegrees(130.0F);
        float wideY = FpvPropGeometry.tanHalfFromDegrees(FovMath.verticalFromHorizontal(130.0, 16.0 / 9.0));
        FpvPropGeometry freestyle = FpvProps.geometry(DroneBuild.FREESTYLE, CameraMount.defaultUptilt(DroneBuild.FREESTYLE.frame()));
        if (freestyle == null || !freestyle.anyVisible(wideX, wideY)) {
            return StepResult.fail("5-Zoll: keine Props im 130°-Bild");
        }
        float maxX = FpvPropGeometry.tanHalfFromDegrees(FovMath.MAX_HORIZONTAL_DEGREES);
        float maxY = FpvPropGeometry.tanHalfFromDegrees(FovMath.verticalFromHorizontal(FovMath.MAX_HORIZONTAL_DEGREES, 16.0 / 9.0));
        FpvPropGeometry deadcat = FpvProps.geometry(RenderExampleParts.DEADCAT_BUILD, CameraMount.defaultUptilt(RenderExampleParts.DEADCAT_BUILD.frame()));
        if (deadcat == null || deadcat.anyVisible(maxX, maxY)) {
            return StepResult.fail("Deadcat zeigt Props im Bild");
        }
        if (Math.abs(CameraMount.defaultUptilt(DroneBuild.FREESTYLE.frame()) - DroneVisuals.resources().layout(FrameType.FREESTYLE_5).cameraTiltDegrees()) > 0.01F) {
            return StepResult.fail("Standard-Uptilt aus der Definition weicht von drone_layout.json ab");
        }
        return StepResult.pass();
    }
}
