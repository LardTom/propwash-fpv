package de.lardtom.propwash.mixin.drone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.lardtom.propwash.drone.client.render.AfterWaterRenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionMixin {
    @Inject(method = "submitCustomGeometry", at = @At("HEAD"), cancellable = true)
    private void propwash$drawAfterWater(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer,
                                         CallbackInfo ci) {
        if (AfterWaterRenderTypes.drawsAfterWater(renderType)) {
            ((SubmitNodeCollection) (Object) this).afterTerrain
                    .submit(new CustomFeatureRenderer.Submit(poseStack.last().copy(), renderType, customGeometryRenderer));
            ci.cancel();
        }
    }
}
