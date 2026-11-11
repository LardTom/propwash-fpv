package de.lardtom.propwash.zone.client;

import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneMenu;
import de.lardtom.propwash.zone.ZoneText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public final class ZoneFocus {
    public static final String KEY = "propwash.zones_open.focus.";
    public static final long SHOW_NANOS = 12_000_000_000L;
    public static final int NOTICE_TICKS = 5;
    private static int id;
    private static String dimension = "";
    private static long until;
    private static int shown;
    private static int ticks;
    private static @Nullable Component last;

    private ZoneFocus() {
    }

    public static void show(Zone zone) {
        id = zone.id();
        dimension = zone.dimension();
        until = Util.getNanos() + SHOW_NANOS;
        shown++;
        ticks = 0;
        last = null;
    }

    public static void clear() {
        id = 0;
        dimension = "";
        until = 0L;
        last = null;
    }

    public static int shown() {
        return shown;
    }

    public static @Nullable Component last() {
        return last;
    }

    public static int focusedId() {
        Zone zone = current();
        return zone == null ? 0 : zone.id();
    }

    public static @Nullable Zone current() {
        if (id == 0 || until <= Util.getNanos()) {
            return null;
        }
        Zone zone = ClientZones.INSTANCE.get(id);
        if (zone == null || !zone.dimension().equals(dimension) || !ClientZones.INSTANCE.syncedDimension().equals(dimension)) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && ClientZones.dimension(minecraft.level).equals(dimension) ? zone : null;
    }

    public static Component message(Zone zone, double x, double y, double z, float yaw) {
        ZoneBox box = zone.box();
        double distance = Math.sqrt(box.distanceSq(zone.shape(), x, y, z));
        if (distance < 0.5) {
            return Component.translatable(KEY + "inside", zone.name()).withStyle(ChatFormatting.GREEN);
        }
        String arrow = ZoneMenu.arrow(box.centerX() - x, box.centerZ() - z, yaw);
        return Component.translatable(KEY + "distance", zone.name(), ZoneText.number(Math.round(distance)), arrow).withStyle(ChatFormatting.YELLOW);
    }

    static void tick(Minecraft minecraft) {
        if (id == 0) {
            return;
        }
        Zone zone = current();
        if (zone == null) {
            if (until <= Util.getNanos() || ClientZones.INSTANCE.get(id) == null) {
                clear();
            }
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gui.screen() != null || ticks++ % NOTICE_TICKS != 0) {
            return;
        }
        Component message = message(zone, player.getX(), player.getY(), player.getZ(), player.getYRot());
        last = message;
        player.sendOverlayMessage(message);
    }
}
