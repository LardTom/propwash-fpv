package de.lardtom.propwash.zone.client;

import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneMarker;
import de.lardtom.propwash.zone.ZoneMarkerItem;
import de.lardtom.propwash.zone.ZoneOwner;
import de.lardtom.propwash.zone.ZoneResolver;
import de.lardtom.propwash.zone.ZoneShape;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

public final class ZoneMarkerClient {
    public static final String KEY = "propwash.zones_open.marker.";
    private static @Nullable BlockPos first;
    private static String firstDimension = "";

    private ZoneMarkerClient() {
    }

    public static @Nullable BlockPos first() {
        return first;
    }

    public static void clear() {
        first = null;
        firstDimension = "";
    }

    public static boolean holding(@Nullable LocalPlayer player) {
        return player != null && (ZoneMarkerItem.is(player.getMainHandItem()) || ZoneMarkerItem.is(player.getOffhandItem()));
    }

    public static void beforeKeybinds(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (ZoneEditor.active() || player == null || minecraft.level == null || minecraft.gui.screen() != null || !holding(player)
                || PilotSession.INSTANCE.isActive() || player.isSpectator()) {
            return;
        }
        Options options = minecraft.options;
        boolean used = false;
        while (options.keyUse.consumeClick()) {
            used = true;
        }
        if (!used) {
            return;
        }
        options.keyUse.setDown(false);
        click(minecraft, player.isShiftKeyDown());
    }

    static void tick(Minecraft minecraft) {
        if (first == null) {
            return;
        }
        if (minecraft.level == null || minecraft.player == null || !ClientZones.dimension(minecraft.level).equals(firstDimension)) {
            clear();
        }
    }

    public static boolean click(Minecraft minecraft, boolean sneak) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return false;
        }
        if (sneak) {
            if (first != null) {
                clear();
                notice(player, Component.translatable(KEY + "cleared").withStyle(ChatFormatting.GRAY));
                return true;
            }
            return openMenu(minecraft);
        }
        if (!ClientZones.INSTANCE.creatable()) {
            notice(player, Component.translatable(KEY + "denied").withStyle(ChatFormatting.RED));
            return false;
        }
        BlockPos target = target(minecraft);
        if (target == null) {
            notice(player, Component.translatable(KEY + "no_target").withStyle(ChatFormatting.GRAY));
            return false;
        }
        String dimension = ClientZones.dimension(minecraft.level);
        if (first == null || !firstDimension.equals(dimension)) {
            first = target;
            firstDimension = dimension;
            player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4F, 1.4F);
            notice(player, Component.translatable(KEY + "first", target.getX(), target.getY(), target.getZ()).withStyle(ChatFormatting.GREEN));
            return true;
        }
        BlockPos corner = first;
        clear();
        player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4F, 1.8F);
        Zone zone = draft(dimension, corner, target).withOwner(new ZoneOwner(player.getUUID(), player.getGameProfile().name()));
        if (!ZoneEditor.start(zone)) {
            notice(player, Component.translatable(KEY + "denied").withStyle(ChatFormatting.RED));
            return false;
        }
        ZoneEditScreen.openSticky();
        return true;
    }

    static Zone draft(String dimension, BlockPos a, BlockPos b) {
        String name = Component.translatable("propwash.zone.new_name", ZoneEditor.nextNumber()).getString();
        return Zone.create(dimension, name, ZoneShape.BOX, ZoneMarker.box(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()));
    }

    public static int zoneHere(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return 0;
        }
        BlockPos target = target(minecraft);
        Vec3 point = target == null ? player.position() : Vec3.atCenterOf(target);
        for (Vec3 at : new Vec3[]{point, player.position()}) {
            for (Zone zone : ZoneResolver.containing(ClientZones.INSTANCE.stored(), at.x, at.y, at.z)) {
                if (ClientZones.INSTANCE.listed(zone)) {
                    return zone.id();
                }
            }
        }
        return 0;
    }

    private static boolean openMenu(Minecraft minecraft) {
        return ZonesTab.open(zoneHere(minecraft));
    }

    public static @Nullable BlockPos target(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 to = eye.add(player.getLookAngle().scale(ZoneMarker.REACH));
        BlockHitResult hit = minecraft.level.clip(new ClipContext(eye, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return hit.getBlockPos().relative(hit.getDirection());
    }

    public static @Nullable Zone preview(Minecraft minecraft) {
        BlockPos corner = first;
        if (corner == null || minecraft.level == null || !holding(minecraft.player) || ZoneEditor.active()
                || !ClientZones.dimension(minecraft.level).equals(firstDimension)) {
            return null;
        }
        BlockPos target = target(minecraft);
        BlockPos other = target == null ? corner : target;
        return Zone.create(firstDimension, "", ZoneShape.BOX, ZoneMarker.box(corner.getX(), corner.getY(), corner.getZ(), other.getX(), other.getY(),
                other.getZ()));
    }

    private static void notice(LocalPlayer player, Component message) {
        player.sendOverlayMessage(message);
    }
}
