package de.lardtom.propwash.drone.bind;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public record ReceiverBind(@Nullable UUID pilot) {
    public static final ReceiverBind CLEARED = new ReceiverBind(null);

    public static ReceiverBind of(UUID pilot) {
        return new ReceiverBind(Objects.requireNonNull(pilot, "pilot"));
    }

    public boolean cleared() {
        return this.pilot == null;
    }
}
