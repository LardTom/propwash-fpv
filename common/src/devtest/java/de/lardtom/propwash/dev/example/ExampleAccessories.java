package de.lardtom.propwash.dev.example;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryAction;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.accessory.MountPose;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.SensorDirection;
import de.lardtom.propwash.api.flight.StabilizationMode;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartVisual;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ExampleAccessories {
    public static final Identifier LAUNCHER = id("pellet_launcher");
    public static final Identifier SPEAR = id("spear_mount");
    public static final Identifier GIMBAL_CAM = id("gimbal_cam");
    public static final String CINE = ExampleAddon.NAMESPACE + ":cine";
    public static final String REQUIRED_API = "1.6";
    public static final int LAUNCHER_SLOTS = 4;
    public static final double PELLET_RECOIL = 0.6;
    public static final double WIND_RECOIL = 2.5;
    public static final double SPEAR_DAMAGE_PER_SPEED = 0.6;
    public static final double ORBIT_RADIUS = 6.0;
    public static final double CINE_SPEED = 6.0;
    public static final double OBSTACLE_STOP = 3.0;

    public static final AtomicInteger FIRED = new AtomicInteger();
    public static final AtomicReference<String> LAST_SHOT = new AtomicReference<>("");
    public static final AtomicInteger ACTIONS = new AtomicInteger();
    public static final AtomicInteger SPEAR_HITS = new AtomicInteger();
    public static final AtomicReference<Float> LAST_SPEAR_DAMAGE = new AtomicReference<>(0.0F);
    public static final AtomicReference<String> LAST_HIT_SLOT = new AtomicReference<>("");
    public static final AtomicInteger DAMAGE_EVENTS = new AtomicInteger();
    public static final AtomicInteger ORBIT_TOGGLES = new AtomicInteger();
    public static final AtomicReference<Float> LAST_DAMAGE = new AtomicReference<>(0.0F);

    private ExampleAccessories() {
    }

    static void registerParts() {
        PropwashApi.parts().register(AccessoryDefinition.builder(LAUNCHER)
                .basics(PartBasics.of(38.0).withCreativeListed(false).withVisual(PartVisual.accent(0x6E6E6E)))
                .slot(AccessorySlot.FRONT).size(32.0, 24.0, 46.0).frontalArea(0.0008)
                .build());
        PropwashApi.parts().register(AccessoryDefinition.builder(SPEAR)
                .basics(PartBasics.of(24.0).withCreativeListed(false).withVisual(PartVisual.accent(0xB8BEC6)))
                .slot(AccessorySlot.BOTTOM).size(8.0, 120.0, 8.0).frontalArea(0.0006)
                .build());
        PropwashApi.parts().register(AccessoryDefinition.builder(GIMBAL_CAM)
                .basics(PartBasics.of(55.0).withCreativeListed(false).withVisual(PartVisual.accent(0x30343A)))
                .slot(AccessorySlot.TOP).size(40.0, 40.0, 40.0).flightBatteryWatts(3.0).frontalArea(0.0016)
                .build());
    }

    static void init() {
        PropwashApi.requireVersion(ExampleAddon.NAMESPACE, REQUIRED_API);
        PropwashApi.accessories().register(LAUNCHER, new Launcher());
        PropwashEvents.DRONE_HIT_ENTITY.register(hit -> {
            LAST_HIT_SLOT.set(hit.slot().map(AccessorySlot::key).orElse("-"));
            if (hit.slot().equals(Optional.of(AccessorySlot.BOTTOM)) && hit.accessory().equals(Optional.of(SPEAR))) {
                hit.addDamage((float) (SPEAR_DAMAGE_PER_SPEED * hit.closingSpeed()));
                SPEAR_HITS.incrementAndGet();
                LAST_SPEAR_DAMAGE.set(hit.damage());
            }
        });
        PropwashEvents.DRONE_DAMAGED.register(damage -> {
            DAMAGE_EVENTS.incrementAndGet();
            if (damage.source().is(DamageTypes.CACTUS)) {
                damage.cancel();
            } else if (PropwashApi.accessories().accessoryAt(damage.drone(), AccessorySlot.TOP).equals(Optional.of(GIMBAL_CAM))) {
                damage.setAmount(damage.amount() * 0.5F);
            }
            LAST_DAMAGE.set(damage.cancelled() ? -1.0F : damage.amount());
        });
        PropwashEvents.ACCESSORY_ACTION.register((player, drone, number, pressed) -> ACTIONS.incrementAndGet());
        PropwashApi.flight().registerAssist(CINE, profile -> profile.hasAccessory(GIMBAL_CAM.toString()) ? new CineAssist() : null);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, path);
    }

    static final class Launcher implements AccessoryBehavior {
        @Override
        public int slots() {
            return LAUNCHER_SLOTS;
        }

        @Override
        public boolean accepts(int slot, ItemStack stack) {
            return stack.is(Items.SNOWBALL) || stack.is(Items.ARROW) || stack.is(Items.WIND_CHARGE);
        }

        @Override
        public void onAction(AccessoryAction action) {
            if (action.number() != 1 || !action.pressed()) {
                return;
            }
            AccessoryInventory inventory = action.inventory();
            int slot = inventory.firstFilled();
            if (slot < 0) {
                return;
            }
            ItemStack ammo = inventory.take(slot, 1);
            ServerLevel level = action.level();
            MountPose mount = action.mount();
            Vec3 muzzle = mount.front().add(mount.forward().scale(0.15));
            Vec3 forward = mount.forward();
            Vec3 inherited = action.velocity().scale(1.0 / 20.0);
            Projectile projectile;
            double recoil;
            if (ammo.is(Items.WIND_CHARGE)) {
                projectile = new WindCharge(level, muzzle.x, muzzle.y, muzzle.z, forward);
                recoil = WIND_RECOIL;
            } else if (ammo.is(Items.ARROW)) {
                Arrow arrow = new Arrow(level, muzzle.x, muzzle.y, muzzle.z, ammo.copyWithCount(1), null);
                arrow.pickup = AbstractArrow.Pickup.ALLOWED;
                arrow.shoot(forward.x, forward.y, forward.z, 2.5F, 0.0F);
                projectile = arrow;
                recoil = PELLET_RECOIL * 2.0;
            } else {
                Snowball snowball = new Snowball(level, muzzle.x, muzzle.y, muzzle.z, ammo.copyWithCount(1));
                snowball.shoot(forward.x, forward.y, forward.z, 1.5F, 0.0F);
                projectile = snowball;
                recoil = PELLET_RECOIL;
            }
            projectile.setOwner(action.player());
            projectile.setDeltaMovement(projectile.getDeltaMovement().add(inherited));
            level.addFreshEntity(projectile);
            action.physics().impulse(forward.scale(-recoil), new Vec3(0.0, -0.03, -0.08));
            FIRED.incrementAndGet();
            LAST_SHOT.set(ammo.getItem().toString());
        }
    }

    static final class CineAssist implements FlightAssist {
        private boolean orbit;
        private boolean lastToggle;
        private double centerX;
        private double centerZ;
        private double altitude;
        private double roll;
        private double pitch;
        private double yaw;
        private double throttle = 0.5;

        @Override
        public void reset() {
            this.orbit = false;
            this.lastToggle = false;
        }

        @Override
        public double sensorRange() {
            return 12.0;
        }

        @Override
        public void update(FlightState state, AssistOutput out) {
            double k = 0.03;
            this.roll += k * (state.stickRoll() - this.roll);
            this.pitch += k * (state.stickPitch() - this.pitch);
            this.yaw += k * (state.stickYaw() - this.yaw);
            this.throttle += k * (state.stickThrottle() - this.throttle);
            boolean toggle = state.action(2);
            if (toggle && !this.lastToggle && state.positionValid()) {
                this.orbit = !this.orbit;
                ORBIT_TOGGLES.incrementAndGet();
                double fx = state.forwardX();
                double fz = state.forwardZ();
                double length = Math.max(1.0E-6, Math.hypot(fx, fz));
                this.centerX = state.estimatedX() + fx / length * ORBIT_RADIUS;
                this.centerZ = state.estimatedZ() + fz / length * ORBIT_RADIUS;
                this.altitude = state.estimatedY();
            }
            this.lastToggle = toggle;
            if (!state.armed()) {
                return;
            }
            if (!state.positionValid()) {
                out.sticks(this.roll, this.pitch, this.yaw, state.stickThrottle());
                out.mode(StabilizationMode.ANGLE);
                out.label("CINE");
                return;
            }
            if (this.orbit) {
                double dx = state.estimatedX() - this.centerX;
                double dz = state.estimatedZ() - this.centerZ;
                double r = Math.max(0.1, Math.hypot(dx, dz));
                double radial = 0.8 * (ORBIT_RADIUS - r);
                out.velocity(-dz / r * 3.0 + dx / r * radial, dx / r * 3.0 + dz / r * radial);
                out.maxSpeed(5.0);
                out.holdAltitude(this.altitude);
                out.heading(Math.toDegrees(Math.atan2(-dx, dz)));
                out.label("ORBIT");
                return;
            }
            double fx = state.forwardX();
            double fz = state.forwardZ();
            double length = Math.max(1.0E-6, Math.hypot(fx, fz));
            fx /= length;
            fz /= length;
            double ahead = this.pitch * CINE_SPEED;
            if (ahead > 0.0 && state.obstacleDistance(SensorDirection.FORWARD) < OBSTACLE_STOP) {
                ahead = 0.0;
                out.label("STOP");
            } else {
                out.label("CINE");
            }
            double side = this.roll * CINE_SPEED;
            out.velocity(fx * ahead - fz * side, fz * ahead + fx * side);
            out.maxSpeed(CINE_SPEED);
            out.climb((this.throttle - 0.5) * 4.0);
            out.yawRate(this.yaw * 45.0);
        }
    }
}
