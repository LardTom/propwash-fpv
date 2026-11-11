package de.lardtom.propwash.trick.tutorial;

import de.lardtom.propwash.trick.Trick;
import de.lardtom.propwash.trick.TrickSession;
import de.lardtom.propwash.trick.TrickType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrickChoreographyTest {
    private static List<TrickType> detect(TrickChoreography choreography) {
        TrickSession session = new TrickSession();
        session.keepHistory(64);
        TrickFlight flight = TrickFlight.fly(choreography, session::update);
        assertEquals(0, flight.crashes(), choreography.trick() + ": Absturz in der Choreografie");
        return session.history().stream().map(Trick::type).toList();
    }

    @Test
    void everyTrickOfTheRepertoireHasItsChoreography() {
        List<Executable> checks = new ArrayList<>();
        for (TrickType type : TrickType.REPERTOIRE) {
            checks.add(() -> {
                TrickChoreography choreography = TrickChoreographies.of(type);
                assertNotNull(choreography, type + ": keine Choreografie");
                assertEquals(type, choreography.trick());
                assertFalse(choreography.moves().isEmpty(), type + ": Choreografie ohne Phasen");
            });
        }
        assertAll(checks);
    }

    @Test
    void trickDetectionRecognisesEachChoreographyAsExactlyItsTrick() {
        List<Executable> checks = new ArrayList<>();
        for (TrickType type : TrickType.REPERTOIRE) {
            checks.add(() -> {
                TrickChoreography choreography = TrickChoreographies.of(type);
                List<TrickType> found = detect(choreography);
                if (type.line()) {
                    assertTrue(found.size() > 1 && found.getLast() == type, type + ": erkannt " + found);
                    assertEquals(choreography.parts(), found.subList(0, found.size() - 1), type + ": Teile der Linie");
                } else {
                    assertEquals(List.of(type), found, type + ": erkannt " + found);
                }
            });
        }
        assertAll(checks);
    }

    @Test
    void mirroredChoreographiesStayTheSameTrick() {
        List<Executable> checks = new ArrayList<>();
        for (TrickType type : TrickType.REPERTOIRE) {
            checks.add(() -> {
                TrickChoreography choreography = TrickChoreographies.of(type);
                assertEquals(detect(choreography), detect(choreography.mirrored()), type + " gespiegelt");
            });
        }
        assertAll(checks);
    }

    @Test
    void variantsShareTheChoreographyOfTheirBaseTrick() {
        for (TrickType type : TrickType.REPERTOIRE) {
            TrickChoreography choreography = TrickChoreographies.of(type);
            if (choreography.sharedWith() != null) {
                assertEquals(type.variantOf(), choreography.sharedWith().variantOf(), type + " teilt mit " + choreography.sharedWith());
            }
        }
        assertEquals(TrickType.MATTY_FLIP, TrickChoreographies.of(TrickType.ANTI_MATTY).sharedWith());
        assertEquals(TrickType.FLIP, TrickChoreographies.of(TrickType.DOUBLE_FLIP).sharedWith());
        assertEquals(TrickType.ROLL, TrickChoreographies.of(TrickType.TRIPLE_ROLL).sharedWith());
        assertEquals(TrickType.DIVE, TrickChoreographies.of(TrickType.BACKWARDS_DIVE).sharedWith());
    }

    @Test
    void flightRecordsSticksAttitudeAndStages() {
        TrickChoreography choreography = TrickChoreographies.of(TrickType.FLIP);
        TrickFlight flight = TrickFlight.fly(choreography, null);
        assertTrue(flight.frames().size() > 100, "zu wenige Bilder: " + flight.frames().size());
        double maxPitch = 0.0;
        double minUp = 1.0;
        for (TrickFlight.Frame frame : flight.frames()) {
            maxPitch = Math.max(maxPitch, Math.abs(frame.pitch()));
            double up = 1.0 - 2.0 * (frame.qx() * frame.qx() + frame.qz() * frame.qz());
            minUp = Math.min(minUp, up);
        }
        assertTrue(maxPitch > 0.5, "Nick-Knüppel bewegt sich kaum: " + maxPitch);
        assertTrue(minUp < -0.9, "Drohne steht nie kopfüber: " + minUp);
        assertEquals(choreography.stages(), flight.stages());
    }
}
