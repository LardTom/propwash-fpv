package de.lardtom.propwash.drone.collision;

import de.lardtom.propwash.accessory.DroneQueries;
import de.lardtom.propwash.api.drone.CollisionQuery;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public final class DroneCollisionQuery implements CollisionQuery {
    private final Entity drone;
    private final @Nullable Player pilot;
    private final Side side;
    private final boolean autonomous;
    private final Predicate<Identifier> switches;
    private final ToDoubleFunction<Identifier> axes;
    private @Nullable DroneView view;

    public DroneCollisionQuery(Entity drone, @Nullable Player pilot, Side side, boolean autonomous, Predicate<Identifier> switches,
                               ToDoubleFunction<Identifier> axes) {
        this.drone = drone;
        this.pilot = pilot;
        this.side = side;
        this.autonomous = autonomous;
        this.switches = switches;
        this.axes = axes;
    }

    @Override
    public Entity drone() {
        return this.drone;
    }

    @Override
    public DroneView view() {
        DroneView current = this.view;
        if (current == null) {
            current = DroneQueries.INSTANCE.view(this.drone).orElseThrow(() -> new IllegalStateException("keine Drohne: " + this.drone));
            this.view = current;
        }
        return current;
    }

    @Override
    public Optional<Player> pilot() {
        return Optional.ofNullable(this.pilot);
    }

    @Override
    public Side side() {
        return this.side;
    }

    @Override
    public boolean autonomous() {
        return this.autonomous;
    }

    @Override
    public boolean switchActive(Identifier id) {
        return id != null && this.switches.test(id);
    }

    @Override
    public double axis(Identifier id) {
        if (id == null) {
            return 0.0;
        }
        double value = this.axes.applyAsDouble(id);
        return Double.isFinite(value) ? value : 0.0;
    }
}
