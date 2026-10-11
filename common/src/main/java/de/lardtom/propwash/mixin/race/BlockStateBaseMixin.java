package de.lardtom.propwash.mixin.race;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import de.lardtom.propwash.race.GateSignals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
    @ModifyReturnValue(method = "getSignal", at = @At("RETURN"))
    private int propwash$gateSignal(int original, @Local(argsOnly = true) BlockGetter level, @Local(argsOnly = true) BlockPos pos,
                                    @Local(argsOnly = true) Direction direction) {
        return GateSignals.signal(level, pos, direction, original);
    }

    @ModifyReturnValue(method = "getDirectSignal", at = @At("RETURN"))
    private int propwash$gateDirectSignal(int original, @Local(argsOnly = true) BlockGetter level, @Local(argsOnly = true) BlockPos pos,
                                          @Local(argsOnly = true) Direction direction) {
        return GateSignals.signal(level, pos, direction, original);
    }
}
