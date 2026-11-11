package de.lardtom.propwash.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintPreset;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DroneItemBlurSelfTests {
    static final String GIVE = "propwash:drone[custom_model_data={strings:[\"blur\"]}]";
    static final ItemDisplayContext[] CONTEXTS = {ItemDisplayContext.FIXED, ItemDisplayContext.NONE, ItemDisplayContext.GROUND,
            ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.HEAD, ItemDisplayContext.ON_SHELF};

    private DroneItemBlurSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("drone-render: Deko-Blur: Drohnen-Item mit custom_model_data \"blur\" zeigt drehende Props in jeder Ansicht", 1,
                DroneItemBlurSelfTests::blurStep);
        DroneBlurShowcase.register();
    }

    record Sample(String name, ItemStack stack, boolean blur) {
    }

    static ItemStack withStrings(ItemStack stack, List<String> strings) {
        ItemStack copy = stack.copy();
        CustomModelData old = copy.get(DataComponents.CUSTOM_MODEL_DATA);
        copy.set(DataComponents.CUSTOM_MODEL_DATA, old == null ? new CustomModelData(List.of(), List.of(), strings, List.of())
                : new CustomModelData(old.floats(), old.flags(), strings, old.colors()));
        return copy;
    }

    static ItemStack blurred(DroneBuild build) {
        return withStrings(DroneItemSelfTests.stack(build), List.of("blur"));
    }

    static ItemStack given() throws CommandSyntaxException {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            throw new IllegalStateException("keine Welt");
        }
        return new ItemParser(mc.level.registryAccess()).parse(new StringReader(GIVE)).createItemStack(1);
    }

    static List<Sample> samples() throws CommandSyntaxException {
        List<Sample> samples = new ArrayList<>();
        samples.add(new Sample("/give " + GIVE, given(), true));
        samples.add(new Sample("Freestyle", blurred(DroneBuild.FREESTYLE), true));
        samples.add(new Sample("Race", blurred(DroneBuild.RACER), true));
        ItemStack whoop = blurred(DroneBuild.WHOOP);
        whoop.set(CoreComponents.PART_WEAR.get(), PartWear.PRISTINE.withProps(new float[]{0.0F, 0.3F, 1.0F, 1.0F}));
        PaintItems.store(whoop, PaintPreset.NEON.paint());
        samples.add(new Sample("Whoop lackiert, Prop 0 gebrochen", whoop, true));
        ItemStack unpainted = whoop.copy();
        PaintItems.store(unpainted, DronePaint.DEFAULT);
        samples.add(new Sample("Whoop, Lack wieder entfernt", unpainted, true));
        samples.add(new Sample("Add-on-Prop mit _blur-Modell", blurred(RenderExampleParts.X_CLASS_BUILD), true));
        samples.add(new Sample("Add-on-Prop ohne _blur-Modell", blurred(RenderExampleParts.STILL_PROP_BUILD), false));
        samples.add(new Sample("Freestyle ohne custom_model_data", DroneItemSelfTests.stack(DroneBuild.FREESTYLE), false));
        samples.add(new Sample("Freestyle mit anderem String", withStrings(DroneItemSelfTests.stack(DroneBuild.FREESTYLE), List.of("ccw")), false));
        return samples;
    }

    static boolean expectedBlur(DroneRenderState drawn, int slot, boolean requested) {
        DroneAssembly assembly = DroneVisuals.resources().assembly(drawn.build);
        return requested && assembly.blur(slot).item() != null && DroneVisuals.propDamage(drawn, slot) != PropDamage.BROKEN;
    }

    private static boolean sameModel(ItemStackRenderState a, @Nullable ItemStackRenderState b) {
        return b != null && (a == b || Objects.equals(a.getModelBoundingBox(), b.getModelBoundingBox()));
    }

    static @Nullable String check(Sample sample, ItemDisplayContext context) {
        Minecraft mc = Minecraft.getInstance();
        ItemStackRenderState state = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(state, sample.stack(), context, mc.level, null, 0);
        int before = DroneItemRenderer.submitted;
        DroneItemRenderer.lastFitted = null;
        SubmitNodeStorage storage = new SubmitNodeStorage();
        state.submit(new PoseStack(), storage, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        DroneItemRenderer.Fitted fitted = DroneItemRenderer.lastFitted;
        if (DroneItemRenderer.submitted == before || fitted == null) {
            return sample.name() + " in " + context + ": nicht über propwash:drone_build gezeichnet";
        }
        DroneRenderState drawn = fitted.state();
        DroneAssembly assembly = DroneVisuals.resources().assembly(drawn.build);
        int spinning = 0;
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            boolean expected = expectedBlur(drawn, slot, sample.blur());
            if (drawn.propBlur[slot] != expected) {
                return sample.name() + " in " + context + ": Prop " + slot + (expected ? " steht still statt zu drehen"
                        : " dreht, obwohl " + (sample.blur() ? "kein _blur-Modell da oder gebrochen" : "kein \"blur\" gesetzt"));
            }
            if (!expected) {
                continue;
            }
            DroneRenderState.PartSlot prop = drawn.props[slot];
            if (!prop.useItem || !sameModel(prop.item, assembly.blur(slot).item())) {
                return sample.name() + " in " + context + ": Prop " + slot + " zeichnet nicht das _blur-Modell";
            }
            spinning++;
        }
        if (spinning > 0 && storage.order(0).afterTerrain.isEmpty()) {
            return sample.name() + " in " + context + ": " + spinning + " drehende Props, aber keine durchscheinende Scheibe eingereicht";
        }
        return null;
    }

    private static StepResult blurStep(SelfTest.StepContext ctx) {
        DroneAssembly freestyle = DroneVisuals.resources().assembly(DroneBuild.FREESTYLE);
        for (int slot = 0; slot < DroneLayout.SLOTS; slot++) {
            if (freestyle.blur(slot).item() == null) {
                return StepResult.fail("Freestyle-Prop " + slot + " ohne _blur-Modell: Vorbedingung verletzt");
            }
        }
        if (DroneVisuals.resources().assembly(RenderExampleParts.STILL_PROP_BUILD).blur(0).item() != null) {
            return StepResult.fail("Testprop " + RenderExampleParts.P13_STILL.id() + " hat ein _blur-Modell: Vorbedingung verletzt");
        }
        List<Sample> samples;
        try {
            samples = samples();
        } catch (CommandSyntaxException | RuntimeException e) {
            return StepResult.fail("/give " + GIVE + " lässt sich nicht lesen: " + e.getMessage());
        }
        int views = 0;
        for (Sample sample : samples) {
            for (ItemDisplayContext context : CONTEXTS) {
                String problem = check(sample, context);
                if (problem != null) {
                    return StepResult.fail(problem);
                }
                views++;
            }
        }
        ctx.log(samples.size() + " Drohnen-Items in " + CONTEXTS.length + " Ansichten (" + views + " Bilder): drehende Props nur mit \"blur\" "
                + "und _blur-Modell, gebrochene Props und Add-on-Props ohne _blur-Modell statisch, Lack behält \"blur\"");
        return StepResult.pass();
    }
}
