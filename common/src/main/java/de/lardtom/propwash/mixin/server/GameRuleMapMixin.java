package de.lardtom.propwash.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import de.lardtom.propwash.server.UnknownGameRules;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.gamerules.GameRuleMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(GameRuleMap.class)
public abstract class GameRuleMapMixin implements UnknownGameRules.Carrier {
    @Unique
    private Map<String, Tag> propwash$unknown = Map.of();

    @ModifyExpressionValue(method = "<clinit>",
            at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;xmap(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"))
    private static Codec<GameRuleMap> propwash$carryUnknown(Codec<GameRuleMap> original) {
        return UnknownGameRules.wrap(original);
    }

    @Inject(method = "copyOf", at = @At("RETURN"))
    private static void propwash$copyUnknown(GameRuleMap source, CallbackInfoReturnable<GameRuleMap> cir) {
        UnknownGameRules.copy(source, cir.getReturnValue());
    }

    @Override
    public Map<String, Tag> propwash$unknownGameRules() {
        return this.propwash$unknown;
    }

    @Override
    public void propwash$setUnknownGameRules(Map<String, Tag> rules) {
        this.propwash$unknown = rules == null ? Map.of() : rules;
    }
}
