package de.lardtom.propwash.neoforge.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.network.fabricsync.FabricOpenScreenPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class FabricMenuScreens {
    private FabricMenuScreens() {
    }

    public static void handle(FabricOpenScreenPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        MenuType<?> type = BuiltInRegistries.MENU.getValue(payload.menuType());
        if (type == null) {
            Constants.LOG.warn("Propwash: Fabric-Server öffnet das unbekannte Menü {}", payload.menuType());
            return;
        }
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()), player.registryAccess(),
                context.listener().getConnectionType());
        try {
            open(mc, player, type, payload.containerId(), payload.title(), buf);
        } catch (RuntimeException e) {
            Constants.LOG.error("Propwash: Menü {} vom Fabric-Server nicht geöffnet", payload.menuType(), e);
        } finally {
            buf.release();
        }
    }

    private static <T extends AbstractContainerMenu> void open(Minecraft mc, LocalPlayer player, MenuType<T> type, int containerId, Component title,
                                                               RegistryFriendlyByteBuf buf) {
        MenuScreens.getScreenFactory(type).ifPresentOrElse(factory -> {
            Screen screen = factory.create(type.create(containerId, player.getInventory(), buf), player.getInventory(), title);
            player.containerMenu = ((MenuAccess<?>) screen).getMenu();
            mc.gui.setScreen(screen);
        }, () -> Constants.LOG.warn("Propwash: für das Menü {} vom Fabric-Server gibt es kein Fenster", BuiltInRegistries.MENU.getKey(type)));
    }
}
