package de.lardtom.propwash.dev.example;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.osd.OsdAlign;
import de.lardtom.propwash.api.client.osd.OsdContext;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.client.osd.OsdTone;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.platform.Services;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public final class ExampleOsd {
    public static final Identifier AMMO = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "ammo");
    public static final Identifier GHOST = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "ghost");
    public static final int COLUMN = 1;
    public static final int ROW = 10;
    public static final int MAX_CHARS = 8;
    public static final int LOW = 3;
    public static final String LABEL = "AMMO ";
    public static final String PREVIEW = LABEL + 12;
    public static final String GHOST_TEXT = "GHOST";
    private static volatile boolean ghost;
    private static boolean initialized;

    private ExampleOsd() {
    }

    public static synchronized void init() {
        if (initialized || !Services.PLATFORM.isClientDist()) {
            return;
        }
        initialized = true;
        PropwashClientApi.registerOsdElement(OsdElementSpec.builder(AMMO)
                .position(COLUMN, ROW).align(OsdAlign.LEFT).maxChars(MAX_CHARS).preview(PREVIEW)
                .content(ExampleOsd::ammo).build());
        PropwashClientApi.registerOsdWarning(GHOST, ExampleOsd::ghostWarning);
    }

    public static void ghost(boolean active) {
        ghost = active;
    }

    public static boolean ghost() {
        return ghost;
    }

    public static OsdTone tone(int rounds) {
        return rounds <= 0 ? OsdTone.WARNING : rounds <= LOW ? OsdTone.CAUTION : OsdTone.NORMAL;
    }

    public static int rounds(Entity drone) {
        int rounds = 0;
        for (ItemStack stack : PropwashApi.accessories().contents(drone, AccessorySlot.FRONT)) {
            rounds += stack.getCount();
        }
        return rounds;
    }

    static @Nullable OsdText ammo(OsdContext context) {
        Entity drone = context.drone().orElse(null);
        if (drone == null || !PropwashApi.accessories().accessoryAt(drone, AccessorySlot.FRONT).equals(Optional.of(ExampleAccessories.LAUNCHER))) {
            return null;
        }
        int rounds = rounds(drone);
        return OsdText.of(LABEL + rounds, tone(rounds));
    }

    static @Nullable OsdText ghostWarning(OsdContext context) {
        return ghost || PropwashClientApi.input().switchActive(GHOST) ? OsdText.of(GHOST_TEXT, OsdTone.CAUTION) : null;
    }
}
