package de.lardtom.propwash.mixin.drone;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import de.lardtom.propwash.drone.QuadcopterEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.commands.AttributeCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AttributeCommand.class)
public abstract class AttributeCommandMixin {
    @Shadow
    @Final
    private static Dynamic2CommandExceptionType ERROR_NO_SUCH_ATTRIBUTE;

    @Shadow
    @Final
    private static Dynamic3CommandExceptionType ERROR_NO_SUCH_MODIFIER;

    @Shadow
    private static Component getAttributeDescription(Holder<Attribute> attribute) {
        throw new AssertionError();
    }

    @Inject(method = "getAttributeInstance", at = @At("HEAD"), cancellable = true)
    private static void propwash$droneInstance(Entity target, Holder<Attribute> attribute, CallbackInfoReturnable<AttributeInstance> cir)
            throws CommandSyntaxException {
        if (target instanceof QuadcopterEntity drone) {
            cir.setReturnValue(propwash$instance(drone, attribute));
        }
    }

    @Inject(method = "getAttributeValue", at = @At("HEAD"), cancellable = true)
    private static void propwash$droneValue(CommandSourceStack source, Entity target, Holder<Attribute> attribute, double scale,
                                            CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        if (target instanceof QuadcopterEntity drone) {
            double result = propwash$instance(drone, attribute).getValue();
            source.sendSuccess(() -> Component.translatable("commands.attribute.value.get.success", getAttributeDescription(attribute),
                    target.getDisplayName(), result), false);
            cir.setReturnValue((int) (result * scale));
        }
    }

    @Inject(method = "getAttributeBase", at = @At("HEAD"), cancellable = true)
    private static void propwash$droneBase(CommandSourceStack source, Entity target, Holder<Attribute> attribute, double scale,
                                           CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        if (target instanceof QuadcopterEntity drone) {
            double result = propwash$instance(drone, attribute).getBaseValue();
            source.sendSuccess(() -> Component.translatable("commands.attribute.base_value.get.success", getAttributeDescription(attribute),
                    target.getDisplayName(), result), false);
            cir.setReturnValue((int) (result * scale));
        }
    }

    @Inject(method = "getAttributeModifier", at = @At("HEAD"), cancellable = true)
    private static void propwash$droneModifier(CommandSourceStack source, Entity target, Holder<Attribute> attribute, Identifier id, double scale,
                                               CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
        if (target instanceof QuadcopterEntity drone) {
            propwash$instance(drone, attribute);
            AttributeMap attributes = drone.attributes().map();
            if (!attributes.hasModifier(attribute, id)) {
                throw ERROR_NO_SUCH_MODIFIER.create(target.getDisplayName(), getAttributeDescription(attribute), id);
            }
            double result = attributes.getModifierValue(attribute, id);
            source.sendSuccess(() -> Component.translatable("commands.attribute.modifier.value.get.success", Component.translationArg(id),
                    getAttributeDescription(attribute), target.getDisplayName(), result), false);
            cir.setReturnValue((int) (result * scale));
        }
    }

    @Inject(method = "resetAttributeBase", at = @At("HEAD"), cancellable = true)
    private static void propwash$droneReset(CommandSourceStack source, Entity target, Holder<Attribute> attribute, CallbackInfoReturnable<Integer> cir)
            throws CommandSyntaxException {
        if (target instanceof QuadcopterEntity drone) {
            AttributeMap attributes = drone.attributes().map();
            if (!attributes.resetBaseValue(attribute)) {
                throw ERROR_NO_SUCH_ATTRIBUTE.create(target.getDisplayName(), getAttributeDescription(attribute));
            }
            double value = attributes.getBaseValue(attribute);
            source.sendSuccess(() -> Component.translatable("commands.attribute.base_value.reset.success", getAttributeDescription(attribute),
                    target.getDisplayName(), value), false);
            cir.setReturnValue(1);
        }
    }

    @Unique
    private static AttributeInstance propwash$instance(QuadcopterEntity drone, Holder<Attribute> attribute) throws CommandSyntaxException {
        AttributeInstance instance = drone.attributes().instance(attribute);
        if (instance == null) {
            throw ERROR_NO_SUCH_ATTRIBUTE.create(drone.getDisplayName(), getAttributeDescription(attribute));
        }
        return instance;
    }
}
