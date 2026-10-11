package de.lardtom.propwash.drone;

import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.BoundDrone;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.drone.net.CraftNameC2S;
import de.lardtom.propwash.network.ServerPayloadContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class CraftNames {
    public static final int MAX_LENGTH = 24;

    private CraftNames() {
    }

    public static String clean(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String stripped = ChatFormatting.stripFormatting(raw);
        StringBuilder out = new StringBuilder();
        (stripped == null ? "" : stripped).codePoints().forEach(cp -> {
            if (cp != '§' && !Character.isISOControl(cp) && Character.isDefined(cp)) {
                out.appendCodePoint(cp);
            }
        });
        String trimmed = out.toString().strip().replaceAll("\\s+", " ");
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_LENGTH) {
            trimmed = trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_LENGTH)).strip();
        }
        return trimmed;
    }

    public static String of(@Nullable Component name) {
        return name == null ? "" : clean(name.getString());
    }

    static void onCraftName(CraftNameC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        String name = payload.name();
        if (payload.entityId() == CraftNameC2S.HELD_ITEM) {
            ItemStack stack = player.getItemInHand(payload.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (ContentIds.is(stack, ContentIds.DRONE)) {
                renameStack(player, stack, name);
            }
            return;
        }
        if (!(player.level().getEntity(payload.entityId()) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            return;
        }
        boolean pilot = drone.isFlownBy(player.getUUID());
        boolean nearbyFree = drone.pilot().isEmpty() && player.position().distanceTo(drone.position()) <= DronePiloting.TUNE_REACH;
        if (pilot || nearbyFree) {
            rename(player, drone, name);
        }
    }

    public static void renameStack(Player player, ItemStack stack, String raw) {
        String name = clean(raw);
        if (name.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_NAME);
        } else {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
        UUID id = stack.get(CoreComponents.DRONE_ID.get());
        if (id != null) {
            refreshBindings(player, id, name);
        }
    }

    public static void rename(Player player, QuadcopterEntity drone, String raw) {
        String name = clean(raw);
        drone.setCustomName(name.isEmpty() ? null : Component.literal(name));
        refreshBindings(player, drone.getUUID(), name);
    }

    public static boolean useNameTag(Player player, QuadcopterEntity drone, ItemStack held) {
        if (!held.is(Items.NAME_TAG)) {
            return false;
        }
        Component name = held.get(DataComponents.CUSTOM_NAME);
        if (name == null) {
            return false;
        }
        if (!player.level().isClientSide()) {
            rename(player, drone, of(name));
            held.shrink(1);
        }
        return true;
    }

    public static void refreshBindings(Player player, UUID drone, String name) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            BoundDrone bound = stack.get(CoreComponents.BOUND_DRONE.get());
            if (bound != null && bound.drone().equals(drone) && !bound.name().equals(BoundDrone.trimName(name))) {
                stack.set(CoreComponents.BOUND_DRONE.get(), new BoundDrone(bound.drone(), name, bound.frame(), bound.video()));
            }
        }
    }
}
