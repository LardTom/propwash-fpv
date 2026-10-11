package de.lardtom.propwash.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Mode3dInputTest {
    private static final float EPS = 1.0E-4F;
    private static final int BIT = SwitchEvaluator.bit(SwitchFunction.MODE_3D);

    @Test
    void mode3dIsANewSwitchFunctionThatLatchesLikeTheOtherModes() {
        assertEquals("propwash.input.switch.mode_3d", SwitchFunction.MODE_3D.translationKey());
        assertTrue(SwitchFunction.MODE_3D.supportsToggle());
        assertTrue(SwitchFunction.MODE_3D.latchesOnGamepad());
        assertTrue(SwitchFunction.MODE_3D.ordinal() < SwitchFunction.COMMAND_1.ordinal());
        assertTrue(SwitchFunction.flightFunctions().contains(SwitchFunction.MODE_3D));
        assertFalse(ProfileDefaults.radio("Funke", 8).binding(SwitchFunction.MODE_3D).bound());
        assertFalse(ProfileDefaults.gamepad("Pad").binding(SwitchFunction.MODE_3D).bound());
    }

    @Test
    void lastInputWinsBetweenSwitchAndKey() {
        Mode3dSelector s = new Mode3dSelector();
        assertFalse(s.selected());
        assertNull(Mode3dSelector.fromSwitches(0, 0));
        assertFalse(s.observe(true, 0, 0));
        assertTrue(s.observe(true, BIT, BIT));
        assertTrue(s.selected());
        assertEquals(Mode3dSelector.Source.SWITCH, s.source());
        assertFalse(s.toggle());
        assertEquals(Mode3dSelector.Source.KEY, s.source());
        assertTrue(s.keyOverridesSwitch(true, BIT, BIT));
        assertFalse(s.observe(true, BIT, BIT), "unveränderter Schalter überstimmt die Taste nicht");
        assertFalse(s.selected());
        assertFalse(s.observe(true, 0, BIT));
        assertFalse(s.selected());
        assertEquals(Mode3dSelector.Source.SWITCH, s.source());
        assertTrue(s.observe(true, BIT, BIT));
        assertTrue(s.selected());
        assertTrue(s.observe(true, 0, 0), "Belegung gelöscht: vom Schalter gesetztes 3D fällt auf aus");
        assertFalse(s.selected());
        assertTrue(s.toggle());
        assertFalse(s.observe(false, 0, BIT), "ohne Gerät zählt nur die Taste");
        assertTrue(s.selected());
        s.reset();
        assertFalse(s.selected());
        assertEquals(Mode3dSelector.Source.DEFAULT, s.source());
    }

    @Test
    void activeOnlyWhenUnlockedAllowedAndNoRescue() {
        Mode3dSelector s = new Mode3dSelector();
        s.toggle();
        assertTrue(s.active(true, true, false));
        assertFalse(s.active(false, true, false));
        assertFalse(s.active(true, false, false));
        assertFalse(s.active(true, true, true));
        assertEquals(Mode3dSelector.Block.LOCKED, Mode3dSelector.block(false, false, true));
        assertEquals(Mode3dSelector.Block.FORBIDDEN, Mode3dSelector.block(true, false, false));
        assertEquals(Mode3dSelector.Block.RESCUE, Mode3dSelector.block(true, true, true));
        assertEquals(Mode3dSelector.Block.NONE, Mode3dSelector.block(true, true, false));
    }

    @Test
    void radioThrottleUsesTheCalibratedCenterIn3d() {
        DeviceProfile p = new DeviceProfile();
        p.deadband = 0.0F;
        p.throttle = new ChannelMapping(0, false);
        p.throttle.min = -0.9F;
        p.throttle.center = 0.1F;
        p.throttle.max = 0.95F;
        p.throttleMode = ThrottleMode.FULL_RANGE;
        assertEquals(0.5F, StickMapper.throttle3d(p, new float[]{0.1F}), EPS);
        assertEquals(0.0F, StickMapper.throttle3d(p, new float[]{-0.9F}), EPS);
        assertEquals(1.0F, StickMapper.throttle3d(p, new float[]{0.95F}), EPS);
        assertEquals(0.75F, StickMapper.throttle3d(p, new float[]{0.525F}), EPS);
        assertEquals(0.25F, StickMapper.throttle3d(p, new float[]{-0.4F}), EPS);
        float linearAtCenter = StickMapper.staticThrottle(p, new float[]{0.1F});
        assertTrue(Math.abs(linearAtCenter - 0.5F) > 0.01F, "normales Gas bleibt linear zwischen den Anschlägen");
        p.throttle.invert = true;
        assertEquals(0.0F, StickMapper.throttle3d(p, new float[]{0.95F}), EPS);
        assertEquals(0.5F, StickMapper.throttle3d(p, new float[]{0.1F}), EPS);
        p.throttle = new ChannelMapping(5, false);
        assertEquals(0.5F, StickMapper.throttle3d(p, new float[]{1.0F}), EPS, "nicht lesbares Gas steht in der Mitte (kein Schub)");
    }

    @Test
    void gamepadSelfCenteringStickUsesItsFullTravelIn3d() {
        DeviceProfile p = new DeviceProfile();
        p.deadband = 0.1F;
        p.throttle = new ChannelMapping(0, false);
        p.throttleMode = ThrottleMode.UPPER_HALF;
        assertEquals(0.5F, StickMapper.throttle3d(p, new float[]{0.05F}), EPS);
        assertEquals(0.0F, StickMapper.throttle3d(p, new float[]{-1.0F}), EPS);
        assertEquals(1.0F, StickMapper.throttle3d(p, new float[]{1.0F}), EPS);
        assertEquals(0.0F, StickMapper.staticThrottle(p, new float[]{-1.0F}), EPS);
    }

    @Test
    void incrementalThrottleStartsCenteredAndStopsInTheMiddle() {
        DeviceProfile p = new DeviceProfile();
        p.deadband = 0.0F;
        p.throttle = new ChannelMapping(0, false);
        p.throttleMode = ThrottleMode.INCREMENTAL;
        p.throttleRate = 1.0F;
        StickMapper mapper = new StickMapper();
        assertEquals(0.5F, mapper.map(p, new float[]{0.0F}, 0.02F, true, false).throttle(), EPS);
        assertEquals(0.5F, mapper.throttle3d(), EPS);
        for (int i = 0; i < 10; i++) {
            mapper.map(p, new float[]{-1.0F}, 0.02F, true, true);
        }
        float reverse = mapper.integratedThrottle();
        assertEquals(0.3F, reverse, 0.02F);
        for (int i = 0; i < 30; i++) {
            mapper.map(p, new float[]{1.0F}, 0.02F, true, true);
        }
        assertEquals(0.5F, mapper.integratedThrottle(), EPS, "Rastpunkt in der Mitte, solange der Knüppel gehalten wird");
        mapper.map(p, new float[]{0.0F}, 0.02F, true, true);
        for (int i = 0; i < 5; i++) {
            mapper.map(p, new float[]{1.0F}, 0.02F, true, true);
        }
        assertEquals(0.6F, mapper.integratedThrottle(), 0.02F, "nach dem Loslassen geht es über die Mitte hinaus");
        mapper.map(p, new float[]{0.0F}, 0.02F, false, false);
        assertEquals(0.0F, mapper.integratedThrottle(), EPS, "3D aus und unscharf: Gas springt zum Scharfschalten nach unten");
    }

    @Test
    void centeringIsInactiveOutside3d() {
        ThrottleCentering c = new ThrottleCentering();
        assertEquals(0.37F, c.apply(0.2F, 0.37F, true, false, false), 0.0F);
        assertEquals(0.5F, c.apply(0.37F, 0.6F, true, true, false), 0.0F);
        assertTrue(c.centered());
        assertEquals(0.55F, c.apply(0.5F, 0.55F, true, true, true), 0.0F, "aus der Mitte heraus geht es direkt weiter");
        assertEquals(0.55F, c.apply(0.55F, 0.55F, false, true, true), 0.0F);
        assertEquals(0.6F, c.apply(0.55F, 0.6F, true, true, true), 0.0F);
        assertEquals(0.5F, c.apply(0.6F, 0.4F, true, true, true), 0.0F);
        assertEquals(0.3F, c.apply(0.4F, 0.3F, true, false, true), 0.0F, "scharf behält 3D aus den Knüppelwert");
    }

    @Test
    void radioFrameCarriesThe3dThrottle() {
        RadioFrame frame = new RadioFrame(RadioSource.JOYSTICK, "Funke", "guid", new StickPositions(0, 0, 0, 0.1F), 0, 0, new float[0], new boolean[0],
                0.62F);
        assertEquals(0.62F, frame.throttle3d(), EPS);
        assertEquals(0.1F, new RadioFrame(RadioSource.KEYBOARD, "", "", new StickPositions(0, 0, 0, 0.1F), 0, 0, null, null).throttle3d(), EPS);
        assertEquals(0.5F, RadioFrame.NONE.throttle3d(), EPS);
        assertEquals(1.0F, new RadioFrame(RadioSource.JOYSTICK, "", "", null, 0, 0, null, null, 3.0F).throttle3d(), EPS);
    }

    @Test
    void wizardKeepsACapturedThrottleCenterNearTheMiddle() {
        CalibrationRecorder rec = new CalibrationRecorder(1);
        rec.captureCenter(new float[]{0.08F});
        rec.observe(new float[]{-0.96F});
        rec.observe(new float[]{0.98F});
        ChannelMapping throttle = new ChannelMapping(0, false);
        assertTrue(rec.apply(throttle, false));
        assertEquals(0.08F, throttle.center, 1.0E-3F);
        CalibrationRecorder low = new CalibrationRecorder(1);
        low.captureCenter(new float[]{-0.6F});
        low.observe(new float[]{-0.96F});
        low.observe(new float[]{0.98F});
        ChannelMapping skipped = new ChannelMapping(0, false);
        assertTrue(low.apply(skipped, false));
        assertEquals(0.01F, skipped.center, 1.0E-3F, "Gas nicht in der Mitte gehalten: Mittelpunkt der Anschläge");
    }

    @Test
    void personalUnlockIsCopiedAndSavedWithTheModes() {
        InputConfig a = new InputConfig();
        assertFalse(a.mode3dUnlocked);
        a.mode3dUnlocked = true;
        assertTrue(a.copy().mode3dUnlocked);
        InputConfig b = new InputConfig();
        b.mergeSwitchesFrom(a);
        assertTrue(b.mode3dUnlocked);
    }
}
