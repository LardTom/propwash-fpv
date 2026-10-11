package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.drone.CraftNames;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.net.CraftNameC2S;
import de.lardtom.propwash.network.client.ClientNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import org.jspecify.annotations.Nullable;

public final class CraftNameAccess {
    public static final CraftNameAccess INSTANCE = new CraftNameAccess();

    private CraftNameAccess() {
    }

    public boolean available() {
        return PilotSession.INSTANCE.isActive() || this.heldHand() != null;
    }

    public String current() {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (drone != null) {
            return CraftNames.of(drone.getCustomName());
        }
        InteractionHand hand = this.heldHand();
        LocalPlayer player = Minecraft.getInstance().player;
        if (hand == null || player == null) {
            return "";
        }
        return CraftNames.of(player.getItemInHand(hand).get(DataComponents.CUSTOM_NAME));
    }

    public boolean apply(String name) {
        String clean = CraftNames.clean(name);
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        if (drone != null) {
            return ClientNet.sendToServer(new CraftNameC2S(drone.getId(), false, clean));
        }
        InteractionHand hand = this.heldHand();
        if (hand == null) {
            return false;
        }
        return ClientNet.sendToServer(new CraftNameC2S(CraftNameC2S.HELD_ITEM, hand == InteractionHand.OFF_HAND, clean));
    }

    private @Nullable InteractionHand heldHand() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        if (ContentIds.is(player.getMainHandItem(), ContentIds.DRONE)) {
            return InteractionHand.MAIN_HAND;
        }
        if (ContentIds.is(player.getOffhandItem(), ContentIds.DRONE)) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }
}
