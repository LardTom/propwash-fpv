package de.lardtom.propwash.video;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.sim.AirframeSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoChannelsTest {
    @Test
    void analogUsesRacebandAndFatsharkLikeReal58GhzTables() {
        assertEquals(16, VideoChannels.count(VideoSystem.ANALOG));
        assertEquals("R1", VideoChannels.label(VideoSystem.ANALOG, 0));
        assertEquals(5658, VideoChannels.frequencyMhz(VideoSystem.ANALOG, 0));
        assertEquals("R8", VideoChannels.label(VideoSystem.ANALOG, 7));
        assertEquals(5917, VideoChannels.frequencyMhz(VideoSystem.ANALOG, 7));
        assertEquals("F1", VideoChannels.label(VideoSystem.ANALOG, 8));
        assertEquals(5740, VideoChannels.frequencyMhz(VideoSystem.ANALOG, 8));
        assertEquals("F8", VideoChannels.label(VideoSystem.ANALOG, 15));
        assertEquals(VideoChannels.frequencyMhz(VideoSystem.ANALOG, 6), VideoChannels.frequencyMhz(VideoSystem.ANALOG, 15));
    }

    @Test
    void digitalHasItsOwnChannelList() {
        assertEquals(8, VideoChannels.count(VideoSystem.DIGITAL));
        assertEquals("CH1", VideoChannels.label(VideoSystem.DIGITAL, 0));
        assertEquals("CH8", VideoChannels.label(VideoSystem.DIGITAL, 7));
        assertEquals("", VideoChannels.label(VideoSystem.DIGITAL, 8));
        assertEquals(VideoSystem.ANALOG, VideoChannels.tableOf(VideoSystem.CREATIVE));
        assertEquals(7, VideoChannels.clamp(VideoSystem.DIGITAL, 12));
        assertEquals(VideoChannels.UNASSIGNED, VideoChannels.clamp(VideoSystem.DIGITAL, -3));
    }

    @Test
    void autoAssignmentStartsWithTheRaceLineUpAndNeverSharesAFrequency() {
        List<Integer> preferred = new ArrayList<>();
        List<Boolean> manual = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            preferred.add(VideoChannels.UNASSIGNED);
            manual.add(false);
        }
        List<Integer> picks = VideoChannels.assignAll(VideoSystem.ANALOG, preferred, manual);
        List<String> labels = new ArrayList<>();
        for (int pick : picks) {
            labels.add(VideoChannels.label(VideoSystem.ANALOG, pick));
        }
        assertEquals(List.of("R1", "R2", "F2", "F4", "R7", "R8"), labels);
        for (int n = 1; n <= 20; n++) {
            List<Integer> many = new ArrayList<>();
            List<Boolean> auto = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                many.add(VideoChannels.UNASSIGNED);
                auto.add(false);
            }
            List<Integer> result = VideoChannels.assignAll(VideoSystem.ANALOG, many, auto);
            Set<Integer> frequencies = new HashSet<>();
            for (int pick : result) {
                assertTrue(VideoChannels.valid(VideoSystem.ANALOG, pick));
                frequencies.add(VideoChannels.frequencyMhz(VideoSystem.ANALOG, pick));
            }
            assertEquals(Math.min(n, 15), frequencies.size(), "Drohnen: " + n);
        }
    }

    @Test
    void manualChannelsStayAndAutomaticOnesMoveAway() {
        List<Integer> picks = VideoChannels.assignAll(VideoSystem.ANALOG, List.of(0, 0, 15), List.of(false, true, false));
        assertEquals(0, picks.get(1));
        assertFalse(picks.get(0) == 0);
        assertFalse(VideoChannels.frequencyMhz(VideoSystem.ANALOG, picks.get(2)) == VideoChannels.frequencyMhz(VideoSystem.ANALOG, 6)
                && picks.contains(6) && picks.indexOf(6) != 2);
        List<Integer> kept = VideoChannels.assignAll(VideoSystem.DIGITAL, List.of(3, 4), List.of(false, false));
        assertEquals(List.of(3, 4), kept);
        List<Integer> twoManual = VideoChannels.assignAll(VideoSystem.DIGITAL, List.of(2, 2), List.of(true, true));
        assertEquals(List.of(2, 2), twoManual);
    }

    @Test
    void sharedFrequencyCountsAsTakenAcrossBands() {
        int pick = VideoChannels.pickFree(VideoSystem.ANALOG, 15, mhz -> mhz == 5880);
        assertFalse(VideoChannels.frequencyMhz(VideoSystem.ANALOG, pick) == 5880);
        assertEquals(0, pick);
    }

    @Test
    void vtxKeysSurviveTheTuneMappingAndAreSanitized() {
        AirframeSpec spec = AirframeSpec.of(DroneBuild.FREESTYLE);
        Map<String, Float> edited = new TreeMap<>();
        edited.put(VtxSettings.CHANNEL, 11.4F);
        edited.put(VtxSettings.MANUAL, 1.0F);
        Map<String, Float> stored = TuneMapping.applyEdit(Map.of(), edited, spec);
        assertEquals(11.0F, stored.get(VtxSettings.CHANNEL));
        assertEquals(1.0F, stored.get(VtxSettings.MANUAL));
        assertTrue(VtxSettings.manual(stored));
        assertEquals("F4", VtxSettings.label(stored, VideoSystem.ANALOG));
        assertEquals("CH8", VtxSettings.label(stored, VideoSystem.DIGITAL));
        Map<String, Float> auto = TuneMapping.normalize(VtxSettings.auto(stored), spec);
        assertFalse(VtxSettings.manual(auto));
        assertEquals(11, VtxSettings.channel(auto, VideoSystem.ANALOG));
        Map<String, Float> cleared = TuneMapping.normalize(VtxSettings.with(stored, VideoChannels.UNASSIGNED, false), spec);
        assertFalse(cleared.containsKey(VtxSettings.CHANNEL));
        assertEquals(VideoChannels.UNASSIGNED, VtxSettings.channel(cleared, VideoSystem.ANALOG));
        Map<String, Float> wild = new TreeMap<>();
        wild.put(VtxSettings.CHANNEL, 99.0F);
        assertEquals(15, VtxSettings.channel(TuneMapping.normalize(wild, spec), VideoSystem.ANALOG));
        assertTrue(TuneMapping.isDroneSetting(VtxSettings.CHANNEL));
        assertTrue(TuneMapping.isKnownKey(VtxSettings.MANUAL));
    }
}
