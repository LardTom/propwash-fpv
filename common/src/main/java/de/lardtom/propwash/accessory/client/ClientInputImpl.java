package de.lardtom.propwash.accessory.client;

import de.lardtom.propwash.api.client.ClientInput;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickPositions;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public final class ClientInputImpl implements ClientInput {
    public static final ClientInputImpl INSTANCE = new ClientInputImpl();

    private ClientInputImpl() {
    }

    @Override
    public boolean flying() {
        return PilotSession.INSTANCE.isActive() && PilotSession.INSTANCE.drone() != null;
    }

    @Override
    public Optional<Entity> drone() {
        QuadcopterEntity drone = PilotSession.INSTANCE.isActive() ? PilotSession.INSTANCE.drone() : null;
        return Optional.ofNullable(drone);
    }

    private static StickPositions sticks() {
        if (!PilotSession.INSTANCE.isActive()) {
            return StickPositions.IDLE;
        }
        StickPositions sticks = RadioState.INSTANCE.sticks();
        return sticks == null ? StickPositions.IDLE : sticks.clamped();
    }

    private static double stick(int axis) {
        StickPositions sticks = sticks();
        return switch (axis) {
            case 0 -> sticks.roll();
            case 1 -> sticks.pitch();
            case 2 -> sticks.yaw();
            default -> sticks.throttle();
        };
    }

    @Override
    public double roll() {
        return stick(0);
    }

    @Override
    public double pitch() {
        return stick(1);
    }

    @Override
    public double yaw() {
        return stick(2);
    }

    @Override
    public double throttle() {
        return stick(3);
    }

    @Override
    public boolean action(int number) {
        return AccessoryActionInput.INSTANCE.held(number);
    }
}
