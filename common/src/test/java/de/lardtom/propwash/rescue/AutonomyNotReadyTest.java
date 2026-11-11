package de.lardtom.propwash.rescue;

import de.lardtom.propwash.api.drone.AutonomyAccess;
import de.lardtom.propwash.api.drone.AutonomyNotReady;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.drone.ExitHandler;
import de.lardtom.propwash.api.flight.FlightAssist;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutonomyNotReadyTest {
    private static final AutonomyOptions OPTIONS = AutonomyOptions.of(Identifier.fromNamespaceAndPath("propwash_example", "hold"));

    @Test
    void startFromTheGroundNamesWhyItIsNotReady() {
        assertEquals(Optional.empty(), AddonAutonomy.notReady(true, true, false, true));
        assertEquals(Optional.empty(), AddonAutonomy.notReady(true, true, false, false));
        assertEquals(Optional.of(AutonomyNotReady.NO_TAKEOFF), AddonAutonomy.notReady(true, false, false, true));
        assertEquals(Optional.of(AutonomyNotReady.NOT_ON_GROUND), AddonAutonomy.notReady(false, true, false, true));
        assertEquals(Optional.of(AutonomyNotReady.NOT_ON_GROUND), AddonAutonomy.notReady(false, false, false, true));
        assertEquals(Optional.of(AutonomyNotReady.NOT_ON_GROUND), AddonAutonomy.notReady(false, true, false, false));
    }

    @Test
    void handoverInTheAirNeedsABatteryAndOnTheGroundATakeoff() {
        assertEquals(Optional.empty(), AddonAutonomy.notReady(false, false, true, true));
        assertEquals(Optional.empty(), AddonAutonomy.notReady(false, true, true, true));
        assertEquals(Optional.of(AutonomyNotReady.NO_BATTERY), AddonAutonomy.notReady(false, false, true, false));
        assertEquals(Optional.of(AutonomyNotReady.NO_TAKEOFF), AddonAutonomy.notReady(true, false, true, true));
        assertEquals(Optional.empty(), AddonAutonomy.notReady(true, true, true, true));
    }

    @Test
    void reasonIsAdditiveForExistingAddons() throws NoSuchMethodException {
        Method method = AutonomyAccess.class.getMethod("notReadyReason", Entity.class, AutonomyOptions.class);
        assertTrue(method.isDefault());
        assertEquals(Optional.class, method.getReturnType());
        AutonomyAccess older = new AutonomyAccess() {
            @Override
            public AutonomyResult start(Entity drone, ServerPlayer owner, FlightAssist assist, AutonomyOptions options) {
                return AutonomyResult.NOT_READY;
            }

            @Override
            public boolean stop(Entity drone) {
                return false;
            }

            @Override
            public Optional<Identifier> active(Entity drone) {
                return Optional.empty();
            }

            @Override
            public void registerExitHandler(Identifier id, ExitHandler handler) {
            }
        };
        assertEquals(Optional.empty(), older.notReadyReason(null, OPTIONS));
        assertEquals(List.of(AutonomyNotReady.NOT_ON_GROUND, AutonomyNotReady.NO_TAKEOFF, AutonomyNotReady.NO_BATTERY, AutonomyNotReady.REMOVED),
                List.of(AutonomyNotReady.values()));
        assertEquals(List.of(AutonomyResult.STARTED, AutonomyResult.NO_BUDGET, AutonomyResult.PILOTED, AutonomyResult.NOT_READY,
                AutonomyResult.DENIED, AutonomyResult.NOT_ARMABLE), List.of(AutonomyResult.values()));
    }
}
