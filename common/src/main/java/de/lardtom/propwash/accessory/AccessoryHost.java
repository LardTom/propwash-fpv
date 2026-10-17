package de.lardtom.propwash.accessory;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.net.AccessoryActionC2S;
import de.lardtom.propwash.api.accessory.AccessoryAction;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.accessory.MountPose;
import de.lardtom.propwash.api.drone.DronePhysics;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.rescue.AutonomousReturns;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class AccessoryHost {
    static final ActionGate GATE = new ActionGate();
    private static final Map<QuadcopterEntity, Integer> HELD = new WeakHashMap<>();

    private AccessoryHost() {
    }

    static synchronized int held(QuadcopterEntity drone) {
        return HELD.getOrDefault(drone, 0);
    }

    private static synchronized void setHeld(QuadcopterEntity drone, int bits) {
        if (bits == 0) {
            HELD.remove(drone);
        } else {
            HELD.put(drone, bits);
        }
    }

    static synchronized void clear() {
        HELD.clear();
        GATE.clear();
    }

    static void onPilotChanged(QuadcopterEntity drone) {
        setHeld(drone, 0);
    }

    static void onAction(AccessoryActionC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = player.level();
        if (!(level.getEntity(payload.droneId()) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            return;
        }
        if (!drone.isFlownBy(player.getUUID()) || payload.epoch() != drone.resetEpoch()) {
            return;
        }
        int bit = 1 << (payload.number() - 1);
        int before = held(drone);
        int after = payload.pressed() ? before | bit : before & ~bit;
        if (payload.pressed() ? !GATE.allow(player.getUUID(), level.getGameTime()) : (before & bit) == 0) {
            return;
        }
        setHeld(drone, after);
        for (Map.Entry<AccessoryMount, AccessoryType> entry : drone.build().accessories().byMount().entrySet()) {
            AccessoryBehavior behavior = AccessoryBehaviors.INSTANCE.of(entry.getValue().unladen());
            if (behavior == null) {
                continue;
            }
            Ctx ctx = new Ctx(level, drone, entry.getKey(), entry.getValue().unladen(), payload.number(), payload.pressed(), player);
            try {
                behavior.onAction(ctx);
            } catch (RuntimeException e) {
                Constants.LOG.warn("Zubehör {}: onAction fehlgeschlagen", entry.getValue().id(), e);
            }
        }
        if (PropwashEvents.ACCESSORY_ACTION.hasListeners()) {
            try {
                PropwashEvents.ACCESSORY_ACTION.fire(listener -> listener.onAccessoryAction(player, drone, payload.number(), payload.pressed()));
            } catch (RuntimeException e) {
                Constants.LOG.warn("Event accessory_action: Listener fehlgeschlagen", e);
            }
        }
    }

    static void tick(QuadcopterEntity drone) {
        if (!(drone.level() instanceof ServerLevel level)) {
            return;
        }
        DroneMotion.sample(drone, level.getGameTime());
        if (!AccessoryBehaviors.INSTANCE.any()) {
            return;
        }
        if (drone.pilot().isEmpty() && held(drone) != 0) {
            setHeld(drone, 0);
        }
        for (Map.Entry<AccessoryMount, AccessoryType> entry : drone.build().accessories().byMount().entrySet()) {
            AccessoryBehavior behavior = AccessoryBehaviors.INSTANCE.of(entry.getValue().unladen());
            if (behavior == null) {
                continue;
            }
            try {
                behavior.tick(new Ctx(level, drone, entry.getKey(), entry.getValue().unladen(), 0, false, null));
            } catch (RuntimeException e) {
                Constants.LOG.warn("Zubehör {}: tick fehlgeschlagen", entry.getValue().id(), e);
            }
        }
    }

    private static final class Ctx implements AccessoryAction {
        private final ServerLevel level;
        private final QuadcopterEntity drone;
        private final AccessoryMount mount;
        private final AccessoryType type;
        private final int number;
        private final boolean pressed;
        private final @Nullable ServerPlayer player;

        private Ctx(ServerLevel level, QuadcopterEntity drone, AccessoryMount mount, AccessoryType type, int number, boolean pressed,
                    @Nullable ServerPlayer player) {
            this.level = level;
            this.drone = drone;
            this.mount = mount;
            this.type = type;
            this.number = number;
            this.pressed = pressed;
            this.player = player;
        }

        @Override
        public ServerLevel level() {
            return this.level;
        }

        @Override
        public Entity drone() {
            return this.drone;
        }

        @Override
        public Identifier accessory() {
            return this.type.id();
        }

        @Override
        public AccessorySlot slot() {
            return this.mount.slot();
        }

        @Override
        public AccessoryInventory inventory() {
            return AccessoryBehaviors.INSTANCE.inventory(this.drone, this.mount.slot()).orElse(EmptyInventory.INSTANCE);
        }

        @Override
        public Optional<ServerPlayer> pilot() {
            return this.drone.pilotEntity() instanceof ServerPlayer pilot ? Optional.of(pilot) : Optional.empty();
        }

        @Override
        public boolean flying() {
            return this.drone.pilot().isPresent() || AutonomousReturns.flying(this.drone);
        }

        @Override
        public boolean armed() {
            return this.drone.isArmed();
        }

        @Override
        public boolean actionHeld(int number) {
            return number >= 1 && number <= AccessoryActionC2S.ACTIONS && (held(this.drone) & (1 << (number - 1))) != 0;
        }

        @Override
        public MountPose mount() {
            return MountPoses.of(this.drone, this.mount).orElseGet(() -> {
                Vec3 center = MountPoses.center(this.drone);
                return new MountPose(center, center, center, new Vec3(0.0, 0.0, -1.0), new Vec3(0.0, 1.0, 0.0), new Vec3(1.0, 0.0, 0.0));
            });
        }

        @Override
        public Vec3 velocity() {
            return DroneMotion.velocity(this.drone);
        }

        @Override
        public DronePhysics physics() {
            return DroneQueries.INSTANCE.physics(this.drone).orElseThrow();
        }

        @Override
        public int number() {
            return this.number;
        }

        @Override
        public boolean pressed() {
            return this.pressed;
        }

        @Override
        public ServerPlayer player() {
            if (this.player == null) {
                throw new IllegalStateException("Kein Spieler im Tick-Kontext");
            }
            return this.player;
        }
    }

    private enum EmptyInventory implements AccessoryInventory {
        INSTANCE;

        @Override
        public int size() {
            return 0;
        }

        @Override
        public ItemStack get(int slot) {
            throw new IndexOutOfBoundsException("Zubehör ohne Inventar");
        }

        @Override
        public void set(int slot, ItemStack stack) {
            throw new IndexOutOfBoundsException("Zubehör ohne Inventar");
        }

        @Override
        public ItemStack take(int slot, int count) {
            throw new IndexOutOfBoundsException("Zubehör ohne Inventar");
        }

        @Override
        public int firstFilled() {
            return -1;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public List<ItemStack> contents() {
            return List.of();
        }

        @Override
        public double payloadGrams() {
            return 0.0;
        }
    }
}
