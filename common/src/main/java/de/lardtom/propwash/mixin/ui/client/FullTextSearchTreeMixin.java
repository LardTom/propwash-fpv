package de.lardtom.propwash.mixin.ui.client;

import com.google.common.collect.ImmutableList;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.ui.client.ModSearch;
import net.minecraft.client.searchtree.FullTextSearchTree;
import net.minecraft.client.searchtree.IdSearchTree;
import net.minecraft.client.searchtree.MergingUniqueIterator;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

@Mixin(FullTextSearchTree.class)
public abstract class FullTextSearchTreeMixin<T> extends IdSearchTree<T> {
    private FullTextSearchTreeMixin(Function<T, Stream<Identifier>> idGetter, List<T> contents) {
        super(idGetter, contents);
    }

    @Inject(method = "searchPlainText", at = @At("RETURN"), cancellable = true)
    private void propwash$modItems(String text, CallbackInfoReturnable<List<T>> cir) {
        if (!ModSearch.wantsModItems(text)) {
            return;
        }
        List<T> modItems = this.identifierSearchTree.searchNamespace(Constants.MOD_ID);
        if (modItems.isEmpty()) {
            return;
        }
        cir.setReturnValue(ImmutableList.copyOf(
                new MergingUniqueIterator<>(cir.getReturnValue().iterator(), modItems.iterator(), this.additionOrder)));
    }
}
