package de.lardtom.propwash.rescue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RescueSupervisorTest {
    private RescueSupervisor supervisor;
    private RescueSupervisor.Frame frame;
    private RescueSettings cfg;

    @BeforeEach
    void setUp() {
        this.supervisor = new RescueSupervisor();
        this.frame = new RescueSupervisor.Frame();
        this.cfg = new RescueSettings();
        this.supervisor.beginSession(false, false);
        this.frame.connected = true;
        this.frame.linkStrength = 1.0F;
    }

    private void tick() {
        this.supervisor.update(this.frame, this.cfg);
        this.frame.keyToggle = false;
        this.frame.keySetHome = false;
        if (this.supervisor.start() != null) {
            this.frame.rescueActive = true;
            this.frame.rescueFlying = true;
        }
        if (this.supervisor.abort() != null) {
            this.frame.rescueActive = false;
            this.frame.rescueFlying = false;
        }
        if (this.supervisor.setHomeOnArm() || this.supervisor.setHomeManual()) {
            this.frame.hasHome = true;
        }
    }

    private void flying() {
        this.frame.armed = true;
        this.frame.grounded = false;
        this.tick();
    }

    @Test
    void homeIsSetOnFirstArmOnlyInFirstArmMode() {
        this.frame.armed = true;
        this.tick();
        assertTrue(this.supervisor.setHomeOnArm());
        this.frame.armed = false;
        this.tick();
        this.frame.armed = true;
        this.tick();
        assertFalse(this.supervisor.setHomeOnArm());
    }

    @Test
    void homeIsSetOnEveryArmInEveryArmMode() {
        this.cfg.homeMode = HomeMode.EVERY_ARM;
        for (int i = 0; i < 3; i++) {
            this.frame.armed = true;
            this.tick();
            assertTrue(this.supervisor.setHomeOnArm());
            this.frame.armed = false;
            this.tick();
        }
    }

    @Test
    void manualAndPilotModesDoNotOverwriteHomeOnArm() {
        this.cfg.homeMode = HomeMode.MANUAL;
        this.frame.hasHome = true;
        this.frame.armed = true;
        this.tick();
        assertFalse(this.supervisor.setHomeOnArm());
        this.frame.armed = false;
        this.frame.hasHome = false;
        this.tick();
        this.frame.armed = true;
        this.tick();
        assertTrue(this.supervisor.setHomeOnArm(), "Manuell ohne Homepoint nimmt den Startpunkt");
        this.cfg.homeMode = HomeMode.PILOT;
        this.frame.armed = false;
        this.tick();
        this.frame.armed = true;
        this.tick();
        assertFalse(this.supervisor.setHomeOnArm());
    }

    @Test
    void armEdgeIsReportedOnceInEveryMode() {
        for (HomeMode mode : HomeMode.values()) {
            this.cfg.homeMode = mode;
            this.frame.armed = true;
            this.tick();
            assertTrue(this.supervisor.armedNow(), mode.name());
            this.tick();
            assertFalse(this.supervisor.armedNow(), mode + ": nur auf der Flanke");
            this.frame.armed = false;
            this.tick();
            assertFalse(this.supervisor.armedNow(), mode + ": Entschärfen ist keine Flanke");
        }
        this.supervisor.beginSession(true, true);
        this.frame.armed = true;
        this.tick();
        assertFalse(this.supervisor.armedNow(), "scharf übernommene Sitzung ist keine Flanke");
    }

    @Test
    void setHomeSwitchAndKeyRequestManualHome() {
        this.frame.setHomeSwitch = true;
        this.tick();
        assertTrue(this.supervisor.setHomeManual());
        this.tick();
        assertFalse(this.supervisor.setHomeManual(), "nur auf der Flanke");
        this.frame.keySetHome = true;
        this.tick();
        assertTrue(this.supervisor.setHomeManual());
    }

    @Test
    void rescueSwitchStartsOnRisingAndAbortsOnFallingEdge() {
        this.flying();
        this.frame.rescueSwitch = true;
        this.tick();
        assertEquals(RescueSupervisor.Trigger.SWITCH, this.supervisor.start());
        this.tick();
        assertNull(this.supervisor.start());
        this.frame.rescueSwitch = false;
        this.tick();
        assertEquals(RescueSupervisor.Abort.SWITCH, this.supervisor.abort());
    }

    @Test
    void keyTogglesRescue() {
        this.flying();
        this.frame.keyToggle = true;
        this.tick();
        assertEquals(RescueSupervisor.Trigger.KEY, this.supervisor.start());
        this.frame.keyToggle = true;
        this.tick();
        assertEquals(RescueSupervisor.Abort.KEY, this.supervisor.abort());
    }

    @Test
    void failsafeStartsReturnWhenLinkIsLostInFlight() {
        this.flying();
        this.frame.connected = false;
        this.tick();
        assertEquals(RescueSupervisor.Trigger.FAILSAFE, this.supervisor.start());
        assertTrue(this.supervisor.linkLost());
        this.tick();
        assertNull(this.supervisor.start(), "nur einmal pro Verbindungsverlust");
    }

    @Test
    void failsafeUsesLinkQualityToo() {
        this.flying();
        this.frame.linkStrength = 0.0F;
        this.tick();
        assertEquals(RescueSupervisor.Trigger.FAILSAFE, this.supervisor.start());
    }

    @Test
    void failsafeNeverTriggersWithoutPriorLinkOrOnTheGround() {
        this.supervisor.beginSession(false, false);
        this.frame.connected = false;
        this.frame.linkStrength = 0.0F;
        this.flying();
        assertNull(this.supervisor.start(), "nie verbunden: kein Failsafe");
        this.supervisor.beginSession(false, false);
        this.frame.connected = true;
        this.frame.linkStrength = 1.0F;
        this.frame.armed = true;
        this.frame.grounded = true;
        this.tick();
        this.frame.connected = false;
        this.tick();
        assertNull(this.supervisor.start(), "am Boden: kein Heimflug");
    }

    @Test
    void failsafeDropLeavesItToTheFlightController() {
        this.cfg.failsafeAction = FailsafeAction.DROP;
        this.flying();
        this.frame.connected = false;
        this.tick();
        assertNull(this.supervisor.start());
        assertTrue(this.supervisor.linkLost());
    }

    @Test
    void linkLossDoesNotFlipTheRescueSwitch() {
        this.flying();
        this.frame.rescueSwitch = true;
        this.tick();
        assertEquals(RescueSupervisor.Trigger.SWITCH, this.supervisor.start());
        this.frame.connected = false;
        this.frame.rescueSwitch = false;
        this.tick();
        assertNull(this.supervisor.abort());
        this.frame.connected = true;
        this.frame.rescueSwitch = true;
        this.tick();
        assertNull(this.supervisor.abort());
        assertNull(this.supervisor.start());
    }

    @Test
    void stickOverrideNeedsCenteredSticksFirst() {
        this.flying();
        this.frame.stickDeflection = 0.8;
        this.frame.keyToggle = true;
        this.tick();
        assertEquals(RescueSupervisor.Trigger.KEY, this.supervisor.start());
        this.tick();
        assertNull(this.supervisor.abort(), "Knüppel waren beim Start ausgelenkt");
        this.frame.stickDeflection = 0.0;
        this.tick();
        this.frame.stickDeflection = 0.5;
        this.tick();
        assertEquals(RescueSupervisor.Abort.OVERRIDE, this.supervisor.abort());
    }

    @Test
    void stickOverrideIgnoredWhileLinkIsLostOrDisabled() {
        this.flying();
        this.frame.connected = false;
        this.tick();
        assertEquals(RescueSupervisor.Trigger.FAILSAFE, this.supervisor.start());
        this.frame.stickDeflection = 1.0;
        this.tick();
        assertNull(this.supervisor.abort());
        this.cfg.stickOverride = false;
        this.frame.connected = true;
        this.frame.stickDeflection = 0.0;
        this.tick();
        this.frame.stickDeflection = 1.0;
        this.tick();
        assertNull(this.supervisor.abort());
    }

    @Test
    void downedPilotStartsTheFailsafeReturnEvenWithoutARadioEverSeen() {
        this.supervisor = new RescueSupervisor();
        this.supervisor.beginSession(true, false);
        this.frame.connected = false;
        this.frame.linkStrength = 0.0F;
        this.frame.hasHome = true;
        this.flying();
        assertNull(this.supervisor.start());
        this.frame.pilotDown = true;
        this.tick();
        assertEquals(RescueSupervisor.Trigger.FAILSAFE, this.supervisor.start());
        assertTrue(this.supervisor.linkLost());
    }

    @Test
    void downedPilotOnTheGroundOrWithDropFailsafeStartsNoReturn() {
        this.frame.hasHome = true;
        this.frame.armed = true;
        this.frame.grounded = true;
        this.tick();
        this.frame.pilotDown = true;
        this.tick();
        assertNull(this.supervisor.start());
        this.setUp();
        this.cfg.failsafeAction = FailsafeAction.DROP;
        this.frame.hasHome = true;
        this.flying();
        this.frame.pilotDown = true;
        this.tick();
        assertNull(this.supervisor.start());
    }

    @Test
    void downedPilotCannotOverrideTheReturnWithTheSticks() {
        this.frame.hasHome = true;
        this.flying();
        this.frame.pilotDown = true;
        this.tick();
        assertEquals(RescueSupervisor.Trigger.FAILSAFE, this.supervisor.start());
        this.frame.stickDeflection = 1.0;
        this.tick();
        this.frame.stickDeflection = 0.0;
        this.tick();
        this.frame.stickDeflection = 1.0;
        this.tick();
        assertNull(this.supervisor.abort());
    }
}
