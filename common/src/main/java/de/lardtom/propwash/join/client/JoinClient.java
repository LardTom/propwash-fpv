package de.lardtom.propwash.join.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.core.client.ClientBus;
import de.lardtom.propwash.join.AddonSession;
import de.lardtom.propwash.join.ClientOnlyAddons;
import de.lardtom.propwash.join.JoinText;
import de.lardtom.propwash.join.SessionItems;
import de.lardtom.propwash.mixin.join.client.CreativeModeTabsAccessor;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemedToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class JoinClient {
    public static final Object SESSION_TOAST = new Object();
    public static final Object BLOCKED_TOAST = new Object();
    public static final long SESSION_TOAST_MILLIS = 10000L;
    public static final long BLOCKED_TOAST_MILLIS = 5000L;

    private static boolean initialized;

    private JoinClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientBus.LOGIN.register(JoinClient::announce);
        ClientBus.DISCONNECT.register(minecraft -> AddonSession.clear());
        AddonSession.onChange(JoinClient::refreshCreativeTabs);
    }

    public static void announce(Minecraft minecraft) {
        if (!AddonSession.takeAnnouncement()) {
            return;
        }
        ClientOnlyAddons addons = AddonSession.current();
        List<String> names = addons.names();
        ThemedToast.show(minecraft.gui.toastManager(), SESSION_TOAST, JoinText.sessionTitle(names.size()), JoinText.sessionMessage(names),
                Palette.ACCENT, SESSION_TOAST_MILLIS);
    }

    public static void refreshCreativeTabs() {
        CreativeModeTabsAccessor.propwash$setCachedParameters(null);
    }

    public static boolean blockCreativeAdd(Minecraft minecraft, @Nullable ClientPacketListener connection, ItemStack stack, int slot) {
        List<String> unavailable = SessionItems.unavailable(stack);
        if (unavailable.isEmpty()) {
            return false;
        }
        Constants.LOG.info("Propwash: {} nicht an den Server geschickt, auf diesem Server inaktiv: {}", stack.getHoverName().getString(), unavailable);
        if (minecraft.player != null && slot >= 0 && slot < minecraft.player.inventoryMenu.slots.size()) {
            minecraft.player.inventoryMenu.getSlot(slot).set(ItemStack.EMPTY);
            if (connection != null && minecraft.player.hasInfiniteMaterials()) {
                connection.send(new ServerboundSetCreativeModeSlotPacket(slot, ItemStack.EMPTY));
            }
        }
        showBlocked(minecraft, stack);
        return true;
    }

    public static boolean blockCreativeDrop(Minecraft minecraft, ItemStack stack) {
        if (SessionItems.available(stack)) {
            return false;
        }
        showBlocked(minecraft, stack);
        return true;
    }

    private static void showBlocked(Minecraft minecraft, ItemStack stack) {
        Component name = stack.getHoverName().copy();
        ThemedToast.show(minecraft.gui.toastManager(), BLOCKED_TOAST, JoinText.unavailable(), JoinText.blockedItem(name), Palette.WARNING,
                BLOCKED_TOAST_MILLIS);
    }
}
