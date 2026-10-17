package de.lardtom.propwash.dev.example.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.camera.GimbalContext;
import de.lardtom.propwash.api.client.camera.GimbalProvider;
import de.lardtom.propwash.api.client.hud.FlightTelemetry;
import de.lardtom.propwash.api.client.hud.HudContext;
import de.lardtom.propwash.api.client.hud.HudElement;
import de.lardtom.propwash.api.flight.GimbalModel;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.dev.example.ExampleAccessories;
import de.lardtom.propwash.dev.example.ExampleAddon;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class ExampleAccessoriesClient {
    public static final String GIMBAL = ExampleAddon.NAMESPACE + ":gimbal";
    public static final String HUD = ExampleAddon.NAMESPACE + ":hud";
    public static final float GIMBAL_PITCH = -10.0F;
    public static final AtomicInteger HUD_FRAMES = new AtomicInteger();
    public static final Gimbal GIMBAL_PROVIDER = new Gimbal();
    private static boolean initialized;

    private ExampleAccessoriesClient() {
    }

    static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashClientApi.registerGimbal(GIMBAL, GIMBAL_PROVIDER);
        PropwashClientApi.registerHudElement(HUD, new Hud());
    }

    static boolean carries(Entity drone, AccessorySlot slot, Object id) {
        return PropwashApi.accessories().accessoryAt(drone, slot).equals(Optional.of(id));
    }

    public static final class Gimbal implements GimbalProvider {
        private final GimbalModel model = new GimbalModel().pitchRange(-90.0, 20.0).pitch(GIMBAL_PITCH);
        private boolean lastLock;

        @Override
        public boolean appliesTo(Entity drone) {
            return carries(drone, AccessorySlot.TOP, ExampleAccessories.GIMBAL_CAM);
        }

        @Override
        public void orient(GimbalContext context, Quaternionf rotation) {
            boolean lock = PropwashClientApi.input().action(4);
            if (lock && !this.lastLock) {
                this.model.mode(this.model.mode() == GimbalModel.Mode.LOCK ? GimbalModel.Mode.FOLLOW : GimbalModel.Mode.LOCK);
            }
            this.lastLock = lock;
            Quaternionfc frame = context.frame();
            this.model.update(context.deltaSeconds(), frame.x(), frame.y(), frame.z(), frame.w());
            rotation.set((float) this.model.qx(), (float) this.model.qy(), (float) this.model.qz(), (float) this.model.qw());
        }

        public GimbalModel model() {
            return this.model;
        }
    }

    static final class Hud implements HudElement {
        @Override
        public boolean active(HudContext context) {
            return (context.fpv() || context.lineOfSight()) && (carries(context.drone(), AccessorySlot.TOP, ExampleAccessories.GIMBAL_CAM)
                    || carries(context.drone(), AccessorySlot.FRONT, ExampleAccessories.LAUNCHER));
        }

        @Override
        public void render(GuiGraphicsExtractor graphics, HudContext context) {
            Font font = context.font();
            FlightTelemetry t = context.telemetry();
            int ammo = 0;
            for (ItemStack stack : PropwashApi.accessories().contents(context.drone(), AccessorySlot.FRONT)) {
                ammo += stack.getCount();
            }
            String mode = t.assistLabel().orElse(t.mode());
            String height = Double.isNaN(t.homeHeight()) ? "H –" : String.format(Locale.ROOT, "H %.1f m", t.homeHeight());
            String distance = Double.isNaN(t.homeDistance()) ? "D –" : String.format(Locale.ROOT, "D %.0f m", t.homeDistance());
            String speed = Double.isNaN(t.speed()) ? "S –" : String.format(Locale.ROOT, "S %.1f m/s", t.speed());
            String line = mode + "   " + height + "   " + distance + "   " + speed + "   Ammo " + ammo;
            int width = font.width(line) + 12;
            int x0 = (context.imageX0() + context.imageX1() - width) / 2;
            int y1 = context.imageY1() - 26;
            graphics.fill(x0, y1 - 14, x0 + width, y1, 0x90101418);
            graphics.fill(x0, y1 - 1, x0 + width, y1, 0xFFFFBB00);
            graphics.text(font, line, x0 + 6, y1 - 11, 0xFFE6E8EC, false);
            HUD_FRAMES.incrementAndGet();
        }
    }
}
