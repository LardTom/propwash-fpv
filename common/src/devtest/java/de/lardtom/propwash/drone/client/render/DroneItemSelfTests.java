package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PaintPreset;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DroneItemSelfTests {
    private static final ItemDisplayContext[] CONTEXTS = {ItemDisplayContext.GUI, ItemDisplayContext.GROUND, ItemDisplayContext.FIXED,
            ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.ON_SHELF};
    private static final float ITEM_FRAME_HALF = 0.75F;
    private static final float WALL_LIFT = 0.5F / 16.0F;
    private static final float TOLERANCE = 1.0E-3F;

    private DroneItemSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("drone-render: Drohnen-Item zeigt den eigenen Bauplan", 1, ctx -> itemStep());
        SelfTest.clientStep("drone-render: Drohnen-Items passen in den Slot", 1, ctx -> fitStep(ctx));
        SelfTest.clientStep("drone-render: Geist zeichnet den Bauplan durchscheinend", 1, ctx -> ghostStep());
        SelfTest.clientStep("drone-render: Drohnen und Rahmen hängen im Item-Rahmen an der Wand", 1, DroneItemSelfTests::wallStep);
        ItemFrameShowcase.register();
    }

    static ItemStack stack(DroneBuild build) {
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        if (build != null) {
            stack.set(CoreComponents.DRONE_BUILD.get(), build);
        }
        return stack;
    }

    static List<ItemStack> samples() {
        List<ItemStack> stacks = new ArrayList<>();
        for (DroneBuild preset : DroneBuild.PRESETS) {
            stacks.add(stack(preset));
        }
        ItemStack custom = stack(DroneBuild.WHOOP.withAccessory(AccessoryType.LED_STRIP));
        custom.set(CoreComponents.PART_WEAR.get(), PartWear.PRISTINE.withProps(new float[]{0.0F, 0.3F, 1.0F, 1.0F}));
        custom.set(CoreComponents.BATTERY_INSTALLED.get(), Boolean.FALSE);
        PaintItems.store(custom, PaintPreset.NEON.paint());
        stacks.add(custom);
        stacks.add(stack(null));
        return stacks;
    }

    private static StepResult itemStep() {
        Minecraft minecraft = Minecraft.getInstance();
        for (ItemStack stack : samples()) {
            DroneItemRenderer.Look expected = DroneItemRenderer.Look.of(stack);
            DroneBuild build = stack.getOrDefault(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
            if (!expected.build().equals(build)) {
                return StepResult.fail("Item-Argument trägt nicht den Bauplan " + build.frame().key());
            }
            for (ItemDisplayContext context : CONTEXTS) {
                ItemStackRenderState state = new ItemStackRenderState();
                minecraft.getItemModelResolver().updateForTopItem(state, stack, context, minecraft.level, null, 0);
                if (state.isEmpty()) {
                    return StepResult.fail("items/drone.json liefert kein Modell für " + context);
                }
                int before = DroneItemRenderer.submitted;
                SubmitNodeStorage storage = new SubmitNodeStorage();
                PoseStack poseStack = new PoseStack();
                state.submit(poseStack, storage, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                if (DroneItemRenderer.submitted == before) {
                    return StepResult.fail("Drohnen-Item in " + context + " zeichnet nicht über propwash:drone_build");
                }
                if (!expected.equals(DroneItemRenderer.lastSubmitted)) {
                    return StepResult.fail("Drohnen-Item in " + context + " zeichnet " + DroneItemRenderer.lastSubmitted + " statt " + expected);
                }
                if (storage.order(0).solid.isEmpty()) {
                    return StepResult.fail(build.frame().key() + ": Drohnen-Item in " + context + " zeichnet nichts");
                }
                AABB box = state.getModelBoundingBox();
                if (!(box.getSize() > 0.0)) {
                    return StepResult.fail("Drohnen-Item ohne Maße in " + context);
                }
            }
        }
        DroneItemRenderer.Look whoop = DroneItemRenderer.Look.of(stack(DroneBuild.WHOOP));
        if (DroneItemRenderer.fitted(whoop) != DroneItemRenderer.fitted(whoop)) {
            return StepResult.fail("Drohnen-Item wird bei jedem Bild neu zusammengesetzt");
        }
        DroneItemRenderer.Look painted = new DroneItemRenderer.Look(DroneBuild.WHOOP, PartWear.PRISTINE, PaintPreset.NEON.paint(), true);
        if (painted.equals(whoop) || DroneItemRenderer.fitted(painted).state().paint.isDefault()) {
            return StepResult.fail("Lackierung fehlt im Drohnen-Item");
        }
        return StepResult.pass();
    }

    private static StepResult fitStep(SelfTest.StepContext ctx) {
        DroneItemRenderer.Reference ref = DroneItemRenderer.reference();
        DroneRenderResources res = DroneVisuals.resources();
        StringBuilder log = new StringBuilder("Item-Größen:");
        for (DroneBuild preset : DroneBuild.PRESETS) {
            DroneItemRenderer.Fitted fitted = DroneItemRenderer.fitted(new DroneItemRenderer.Look(preset, PartWear.PRISTINE, DronePaint.DEFAULT, true));
            VisualHull hull = res.assembly(preset).hull();
            float shown = hull.radius(hull.center(new Vector3f())) * fitted.scale();
            float relative = shown / ref.radius();
            log.append(' ').append(preset.frame().key()).append('=').append(Math.round(relative * 100.0F)).append('%');
            if (relative < DroneItemRenderer.MIN_FIT - 0.01F || relative > DroneItemRenderer.MAX_FIT + 0.01F) {
                return StepResult.fail(preset.frame().key() + ": Item-Größe " + relative + " außerhalb "
                        + DroneItemRenderer.MIN_FIT + "…" + DroneItemRenderer.MAX_FIT);
            }
            if (preset.equals(DroneItemRenderer.REFERENCE) && Math.abs(fitted.scale() - 1.0F) > 1.0E-4F) {
                return StepResult.fail("FREESTYLE-Item weicht vom bisherigen Symbol ab: Skalierung " + fitted.scale());
            }
        }
        float whoop = DroneItemRenderer.fitted(new DroneItemRenderer.Look(DroneBuild.WHOOP, PartWear.PRISTINE, DronePaint.DEFAULT, true)).fit();
        float longRange = DroneItemRenderer.fitted(new DroneItemRenderer.Look(DroneBuild.LONG_RANGE, PartWear.PRISTINE, DronePaint.DEFAULT, true)).fit();
        if (!(whoop < longRange)) {
            return StepResult.fail("Whoop erscheint nicht kleiner als die Long-Range: " + whoop + " / " + longRange);
        }
        ctx.log(log.toString());
        return StepResult.pass();
    }

    static List<ItemStack> wallSamples() {
        List<ItemStack> stacks = samples();
        for (FrameType frame : FrameType.values()) {
            if (!frame.builtin()) {
                PartsRenderSelfTests.buildFor(frame).ifPresent(build -> stacks.add(stack(build)));
            }
        }
        return stacks;
    }

    private static Optional<String> outsideTheFrame(ItemStackRenderState state, String what) {
        AABB box = state.getModelBoundingBox();
        if (box.maxZ > -WALL_LIFT + TOLERANCE) {
            return Optional.of(what + " ragt in die Wand (Tiefe bis " + box.maxZ + ")");
        }
        double half = Math.max(Math.max(-box.minX, box.maxX), Math.max(-box.minY, box.maxY));
        if (half > ITEM_FRAME_HALF + TOLERANCE) {
            return Optional.of(what + " größer als der Item-Rahmen (" + half + " > " + ITEM_FRAME_HALF + ")");
        }
        return Optional.empty();
    }

    private static StepResult wallStep(SelfTest.StepContext ctx) {
        Minecraft minecraft = Minecraft.getInstance();
        DroneItemRenderer.Reference ref = DroneItemRenderer.reference();
        DroneRenderResources res = DroneVisuals.resources();
        StringBuilder log = new StringBuilder("Item-Rahmen (Fußkreis relativ zum Freestyle):");
        float whoop = Float.NaN;
        float longRange = Float.NaN;
        for (ItemStack stack : wallSamples()) {
            DroneItemRenderer.Look look = DroneItemRenderer.Look.of(stack);
            String key = look.build().frame().key();
            for (ItemDisplayContext context : new ItemDisplayContext[]{ItemDisplayContext.FIXED, ItemDisplayContext.GUI, ItemDisplayContext.ON_SHELF}) {
                ItemStackRenderState state = new ItemStackRenderState();
                minecraft.getItemModelResolver().updateForTopItem(state, stack, context, minecraft.level, null, 0);
                int before = DroneItemRenderer.submitted;
                state.submit(new PoseStack(), new SubmitNodeStorage(), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                boolean wall = context == ItemDisplayContext.FIXED;
                if (DroneItemRenderer.submitted == before || DroneItemRenderer.lastWall != wall) {
                    return StepResult.fail(key + ": Drohnen-Item in " + context + (wall ? " hängt nicht an der Wand" : " hängt an der Wand"));
                }
                if (wall) {
                    Optional<String> problem = outsideTheFrame(state, key + ": Drohne");
                    if (problem.isPresent()) {
                        return StepResult.fail(problem.get());
                    }
                }
            }
            DroneItemRenderer.Fitted fitted = DroneItemRenderer.wallFitted(look);
            VisualHull hull = res.assembly(look.build()).hull();
            float footprint = hull.horizontalRadius(hull.center(new Vector3f()));
            float relative = footprint * fitted.scale() * DroneVisualScale.MM_TO_UNITS * 2.0F / DroneItemRenderer.WALL_DIAMETER;
            if (!Float.isFinite(relative) || relative < DroneItemRenderer.MIN_FIT - 0.01F || relative > DroneItemRenderer.MAX_FIT + 0.01F) {
                return StepResult.fail(key + ": Fußkreis im Item-Rahmen " + relative + " außerhalb " + DroneItemRenderer.MIN_FIT + "…"
                        + DroneItemRenderer.MAX_FIT);
            }
            if (look.build().equals(DroneItemRenderer.REFERENCE) && look.paint().isDefault() && Math.abs(relative - 1.0F) > 1.0E-3F) {
                return StepResult.fail("FREESTYLE füllt den Item-Rahmen nicht: " + relative);
            }
            if (look.build().equals(DroneBuild.WHOOP)) {
                whoop = relative;
            }
            if (look.build().equals(DroneBuild.LONG_RANGE)) {
                longRange = relative;
            }
            log.append(' ').append(key).append('=').append(Math.round(relative * 100.0F)).append('%');
        }
        if (!(whoop < longRange)) {
            return StepResult.fail("Whoop erscheint im Item-Rahmen nicht kleiner als die Long-Range: " + whoop + " / " + longRange);
        }
        if (!(ref.footprint() > 0.0F)) {
            return StepResult.fail("Fußkreis des Freestyle unbekannt");
        }
        List<PartInfo> flat = new ArrayList<>(List.of(FrameType.values()));
        flat.addAll(List.of(PropType.values()));
        int parts = 0;
        for (PartInfo info : flat) {
            ItemStack part = ContentItems.stack(info);
            if (part.isEmpty()) {
                continue;
            }
            ItemStackRenderState state = new ItemStackRenderState();
            minecraft.getItemModelResolver().updateForTopItem(state, part, ItemDisplayContext.FIXED, minecraft.level, null, 0);
            if (state.isEmpty()) {
                return StepResult.fail(info.id() + ": kein Modell im Item-Rahmen");
            }
            Optional<String> problem = outsideTheFrame(state, info.id() + ": Teil");
            if (problem.isPresent()) {
                return StepResult.fail(problem.get());
            }
            int before = PartItemRenderer.submitted;
            state.submit(new PoseStack(), new SubmitNodeStorage(), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            if (PartItemRenderer.submitted != before && !PartItemRenderer.lastWall) {
                return StepResult.fail(info.id() + ": Symbol ohne Modell hängt nicht an der Wand");
            }
            parts++;
        }
        ctx.log(log + ", " + parts + " Rahmen und Props");
        return StepResult.pass();
    }

    private static StepResult ghostStep() {
        Quaternionf tilt = new Quaternionf().rotationXYZ(-0.3F, 0.8F, 0.1F);
        for (DroneBuild preset : DroneBuild.PRESETS) {
            DronePaint paint = preset == DroneBuild.WHOOP ? PaintPreset.NEON.paint() : DronePaint.DEFAULT;
            SubmitNodeStorage storage = new SubmitNodeStorage();
            PoseStack poseStack = new PoseStack();
            if (!DroneGhost.submit(preset, paint, tilt, 0x9CB4E6FF, poseStack, storage)) {
                return StepResult.fail(preset.frame().key() + ": Geist nicht gezeichnet");
            }
            if (!poseStack.isEmpty()) {
                return StepResult.fail("PoseStack nach dem Geist nicht leer");
            }
            if (!storage.order(0).solid.isEmpty()) {
                return StepResult.fail(preset.frame().key() + ": Geist zeichnet deckende Teile");
            }
            if (storage.order(0).afterTerrain.isEmpty()) {
                return StepResult.fail(preset.frame().key() + ": Geist zeichnet nichts Durchscheinendes nach dem Wasser");
            }
            if (!storage.order(0).translucentCustomGeometry.isEmpty()) {
                return StepResult.fail(preset.frame().key() + ": Geist wird vor dem Wasser gezeichnet (Wasser dahinter verschwindet)");
            }
            DroneRenderState state = DroneGhost.state(preset, paint);
            if (!state.build.equals(preset) || !state.paint.equals(paint) || state != DroneGhost.state(preset, paint)) {
                return StepResult.fail(preset.frame().key() + ": Geist-Zustand nicht je Bauplan zwischengespeichert");
            }
        }
        return StepResult.pass();
    }
}
