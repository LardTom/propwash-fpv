package de.lardtom.propwash.rescue.client;

import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.rescue.RescuePhase;
import de.lardtom.propwash.rescue.RescueSupervisor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public final class RescueState {
    public static final RescueState INSTANCE = new RescueState();

    private final Map<RescuePhase, Component> labels = new EnumMap<>(RescuePhase.class);

    private RescueState() {
    }

    public boolean active() {
        return RescueController.INSTANCE.active();
    }

    public Component phaseLabel() {
        if (!this.active()) {
            return Component.empty();
        }
        return this.labels.computeIfAbsent(this.phase(), p -> Component.translatable(p.translationKey()));
    }

    public @Nullable Vec3 home() {
        return RescueController.INSTANCE.home();
    }

    public StickPositions commandedSticks() {
        return this.active() ? RescueController.INSTANCE.sticks() : StickPositions.IDLE;
    }

    public boolean forceSelfLevel() {
        return this.active();
    }

    public RescuePhase phase() {
        return RescueController.INSTANCE.phase();
    }

    public boolean wantsDisarm() {
        return RescueController.INSTANCE.wantsDisarm();
    }

    public boolean failsafe() {
        return RescueController.INSTANCE.trigger() == RescueSupervisor.Trigger.FAILSAFE;
    }

    public double homeDistance() {
        return RescueController.INSTANCE.homeDistance();
    }
}
