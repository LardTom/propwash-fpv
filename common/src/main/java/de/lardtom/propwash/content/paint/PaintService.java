package de.lardtom.propwash.content.paint;

import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintCost;
import de.lardtom.propwash.build.PaintSwatch;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.network.ServerPayloadContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PaintService {
    public enum Result {
        APPLIED,
        UNCHANGED,
        NO_FRAME,
        MISSING_DYES
    }

    private PaintService() {
    }

    public static void init() {
        PaintComponents.bootstrap();
        PropwashNet.registerC2S(PaintC2S.TYPE, PaintC2S.CODEC, PaintService::onPaint);
    }

    static void onPaint(PaintC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        if (!(player.containerMenu instanceof DroneWorkbenchMenu menu) || menu.containerId != payload.containerId() || !menu.stillValid(player)) {
            return;
        }
        menu.applyPaint(player, payload.paint());
    }

    public static boolean free(Player player) {
        return player.hasInfiniteMaterials();
    }

    public static Map<String, Integer> availableDyes(Player player) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (PaintSwatch swatch : PaintSwatch.dyes()) {
            out.put(swatch.dye(), 0);
        }
        for (ItemStack stack : dyeStacks(player)) {
            DyeColor color = stack.get(DataComponents.DYE);
            if (color != null) {
                out.merge(color.getSerializedName(), stack.getCount(), Integer::sum);
            }
        }
        return out;
    }

    public static boolean affordable(Player player, PaintCost cost) {
        return cost.free() || free(player) || cost.plan(availableDyes(player)).isPresent();
    }

    public static boolean pay(Player player, PaintCost cost) {
        if (cost.free() || free(player)) {
            return true;
        }
        Optional<Map<String, Integer>> plan = cost.plan(availableDyes(player));
        if (plan.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Integer> entry : plan.get().entrySet()) {
            int left = entry.getValue();
            for (ItemStack stack : dyeStacks(player)) {
                if (left <= 0) {
                    break;
                }
                DyeColor color = stack.get(DataComponents.DYE);
                if (color == null || !color.getSerializedName().equals(entry.getKey())) {
                    continue;
                }
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        player.getInventory().setChanged();
        return true;
    }

    public static Result check(Player player, ItemStack frame, DronePaint paint) {
        if (frame.isEmpty()) {
            return Result.NO_FRAME;
        }
        DronePaint current = PaintItems.paint(frame);
        if (current.equals(paint)) {
            return Result.UNCHANGED;
        }
        return affordable(player, PaintCost.of(current, paint)) ? Result.APPLIED : Result.MISSING_DYES;
    }

    private static List<ItemStack> dyeStacks(Player player) {
        return player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> !stack.isEmpty() && stack.is(ItemTags.DYES) && stack.get(DataComponents.DYE) != null)
                .toList();
    }
}
