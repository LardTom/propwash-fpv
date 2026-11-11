package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

public final class DroneItemViewSelfTests {
    static final ItemDisplayContext[] HELD = {ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.HEAD, ItemDisplayContext.ON_SHELF};
    private static final float TOLERANCE = 0.01F;

    private DroneItemViewSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("drone-render: Drohnen-Item in Hand, Kopf und Regal zeigt jede Drohne vollständig wie in der Welt", 1,
                DroneItemViewSelfTests::heldStep);
        SelfTest.clientStep("drone-render: Drohnen-Item im Slot behält Rahmen-Modelle und jedes Anbauteil an seinem Platz", 1,
                DroneItemViewSelfTests::slotStep);
        DroneItemSheet.register();
    }

    private static DroneItemRenderer.@Nullable Fitted draw(ItemStack stack, ItemDisplayContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStackRenderState state = new ItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(state, stack, context, minecraft.level, null, 0);
        int before = DroneItemRenderer.submitted;
        DroneItemRenderer.lastFitted = null;
        state.submit(new PoseStack(), new SubmitNodeStorage(), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        if (DroneItemRenderer.submitted == before || DroneItemRenderer.lastWall || DroneItemRenderer.lastGround) {
            return null;
        }
        return DroneItemRenderer.lastFitted;
    }

    static DroneRenderState world(DroneItemRenderer.Look look) {
        DroneRenderState state = new DroneRenderState();
        DroneVisuals.fillPreview(state, look.build(), look.wear(), look.paint(), look.blur());
        state.batteryMissing = !look.battery();
        return state;
    }

    private static boolean sameShape(Shape a, Shape b) {
        return a == b || a.vertexCount() == b.vertexCount() && a.min().equals(b.min()) && a.max().equals(b.max());
    }

    private static boolean sameItem(ItemStackRenderState a, ItemStackRenderState b) {
        return a == b || Objects.equals(a.getModelBoundingBox(), b.getModelBoundingBox());
    }

    static @Nullable String slot(String what, DroneRenderState.PartSlot expected, DroneRenderState.PartSlot actual, boolean shapeMayDiffer) {
        if (!expected.position.equals(actual.position)) {
            return what + " sitzt bei " + actual.position + " statt bei " + expected.position;
        }
        if (shapeMayDiffer) {
            return actual.useItem || !actual.solid.isEmpty() ? null : what + " fehlt";
        }
        if (expected.useItem != actual.useItem) {
            return what + (expected.useItem ? " ohne sein Modell (Ersatzform statt Modell)" : " mit Modell statt Form");
        }
        if (expected.useItem) {
            return sameItem(expected.item, actual.item) ? null : what + " mit anderem Modell";
        }
        return sameShape(expected.solid, actual.solid) && sameShape(expected.translucent, actual.translucent) ? null
                : what + " mit anderer Form (" + actual.solid.min() + "…" + actual.solid.max() + " statt " + expected.solid.min() + "…"
                + expected.solid.max() + ")";
    }

    static @Nullable String differences(DroneRenderState expected, DroneRenderState actual, boolean slotIcon) {
        String problem = slot("Rahmen", expected.frame, actual.frame, slotIcon && !expected.frame.useItem);
        if (problem != null) {
            return problem;
        }
        problem = slot("Stack", expected.stack, actual.stack, false);
        if (problem == null) {
            problem = slot("Stack mit Stecker", expected.powerLead.stack, actual.powerLead.stack, false);
        }
        if (problem == null && expected.batteryMissing == actual.batteryMissing && !expected.batteryMissing) {
            problem = slot("Akku", expected.battery, actual.battery, false);
        }
        if (problem == null) {
            problem = slot("Kamera", expected.video, actual.video, false);
        }
        if (problem == null) {
            problem = slot("Sender", expected.transmitter, actual.transmitter, false);
        }
        for (int i = 0; problem == null && i < DroneLayout.SLOTS; i++) {
            problem = slot("Motor " + i, expected.motors[i], actual.motors[i], false);
            if (problem == null) {
                problem = slot("Prop " + i, expected.props[i], actual.props[i], slotIcon);
            }
        }
        for (int i = 0; problem == null && i < DroneAssembly.MOUNTS; i++) {
            if (expected.accessoryType[i] != actual.accessoryType[i]) {
                problem = "Zubehör " + i + " fehlt";
            } else if (expected.accessoryType[i] != null) {
                problem = slot("Zubehör " + expected.accessoryType[i].id(), expected.accessories[i], actual.accessories[i], false);
                if (problem == null) {
                    problem = slot("Zubehör-Halter " + expected.accessoryType[i].id(), expected.accessoryBases[i], actual.accessoryBases[i], false);
                }
            }
        }
        return problem;
    }

    static float relativeSize(DroneItemRenderer.Look look, DroneItemRenderer.Fitted fitted) {
        VisualHull hull = DroneVisuals.resources().assembly(look.build()).hull();
        return hull.radius(hull.center(new Vector3f())) * fitted.scale() / DroneItemRenderer.reference().radius();
    }

    private static StepResult heldStep(SelfTest.StepContext ctx) {
        int checked = 0;
        float smallest = Float.POSITIVE_INFINITY;
        float largest = 0.0F;
        for (DroneItemSheet.Sample sample : DroneItemSheet.samples()) {
            DroneItemRenderer.Look look = DroneItemRenderer.Look.of(sample.stack());
            DroneRenderState expected = world(look);
            for (ItemDisplayContext context : HELD) {
                DroneItemRenderer.Fitted fitted = draw(sample.stack(), context);
                if (fitted == null) {
                    return StepResult.fail(sample.name() + " in " + context + ": nicht über propwash:drone_build gezeichnet");
                }
                if (!look.equals(DroneItemRenderer.lastSubmitted)) {
                    return StepResult.fail(sample.name() + " in " + context + ": zeichnet " + DroneItemRenderer.lastSubmitted);
                }
                if (DroneItemRenderer.lastIcon) {
                    return StepResult.fail(sample.name() + " in " + context + ": zeichnet die Symbolform des Slots");
                }
                String problem = differences(expected, fitted.state(), false);
                if (problem != null) {
                    return StepResult.fail(String.format(Locale.ROOT, "%s in %s (Skala %.3f): %s", sample.name(), context, fitted.scale(), problem));
                }
                float relative = relativeSize(look, fitted);
                if (!(relative >= DroneItemRenderer.MIN_FIT - TOLERANCE && relative <= DroneItemRenderer.MAX_FIT + TOLERANCE)) {
                    return StepResult.fail(String.format(Locale.ROOT, "%s in %s: Größe %.3f der Freestyle außerhalb %.2f…%.2f", sample.name(), context,
                            relative, DroneItemRenderer.MIN_FIT, DroneItemRenderer.MAX_FIT));
                }
                smallest = Math.min(smallest, relative);
                largest = Math.max(largest, relative);
                checked++;
            }
        }
        ctx.log(String.format(Locale.ROOT, "%d Ansichten in Hand, Kopf und Regal wie in der Welt, Größe %.2f…%.2f der Freestyle", checked, smallest,
                largest));
        return StepResult.pass();
    }

    private static StepResult slotStep(SelfTest.StepContext ctx) {
        int checked = 0;
        int icons = 0;
        for (DroneItemSheet.Sample sample : DroneItemSheet.samples()) {
            DroneItemRenderer.Look look = DroneItemRenderer.Look.of(sample.stack());
            DroneItemRenderer.Fitted fitted = draw(sample.stack(), ItemDisplayContext.GUI);
            if (fitted == null) {
                return StepResult.fail(sample.name() + " im Slot: nicht über propwash:drone_build gezeichnet");
            }
            if (!DroneItemRenderer.lastIcon) {
                return StepResult.fail(sample.name() + " im Slot: items/drone.json wählt für gui nicht \"icon\": true");
            }
            String problem = differences(world(look), fitted.state(), true);
            if (problem != null) {
                return StepResult.fail(String.format(Locale.ROOT, "%s im Slot (Skala %.3f): %s", sample.name(), fitted.scale(), problem));
            }
            if (fitted.scale() < DroneItemRenderer.ICON_SHAPES_BELOW) {
                icons++;
            }
            checked++;
        }
        ctx.log(checked + " Drohnen im Slot vollständig, " + icons + " davon mit Symbolformen");
        return StepResult.pass();
    }
}
