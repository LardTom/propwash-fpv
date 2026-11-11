package de.lardtom.propwash.race.data;

import de.lardtom.propwash.api.race.BoardEntry;
import de.lardtom.propwash.api.race.BoardQuery;
import de.lardtom.propwash.api.race.BoardSnapshot;
import de.lardtom.propwash.api.race.Category;
import de.lardtom.propwash.api.race.CourseInfo;
import de.lardtom.propwash.api.race.TimeKind;
import de.lardtom.propwash.api.race.ZoneHint;
import de.lardtom.propwash.race.AssistDetail;
import de.lardtom.propwash.race.AssistReasons;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.export.RaceSnapshots;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.sim.HoldMode;
import de.lardtom.propwash.zone.Zone;
import de.lardtom.propwash.zone.ZoneBox;
import de.lardtom.propwash.zone.ZoneShape;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaceSnapshotsTest {
    private static final long S = TestWorlds.S;
    private static final long DAY = TestWorlds.DAY;

    @TempDir
    Path temp;
    private CourseRegistry registry;
    private CourseRegistry.Course course;

    @BeforeEach
    void setUp() throws IOException {
        this.registry = TestWorlds.open(this.temp, TestWorlds.V052);
        this.course = this.registry.course(1);
        this.registry.submitLap(this.course, TestWorlds.ANNA, "Anna", "race_5", 30_250_000_000L, DAY);
        this.registry.submitLap(this.course, TestWorlds.ANNA, "Anna", "whoop_65", 41 * S, DAY + 10);
        this.registry.submitLap(this.course, TestWorlds.BEN, "Ben", "race_5", 31_999_600_000L, DAY + 20);
        this.registry.submitLap(this.course, TestWorlds.CARL, "Carl", "whoop_65", 39 * S, DAY + 30);
        this.registry.submitAssistedLap(this.course, TestWorlds.BEN, "Ben", "race_5", 28 * S, DAY + 40,
                AssistReasons.of(List.of(AssistDetail.hold(HoldMode.ALTHOLD))));
        this.registry.submitRace(this.course, TestWorlds.ANNA, "Anna", 3, 95 * S, "race_5", DAY + 50);
        this.registry.submitRace(this.course, TestWorlds.BEN, "Ben", 3, 93 * S, "race_5", DAY + 60);
        this.registry.submitRace(this.course, TestWorlds.CARL, "Carl", 1, 40 * S, "whoop_65", DAY + 70);
    }

    @Test
    void courseInfoDescribesTheCourse() {
        CourseInfo info = RaceSnapshots.info(this.course, this.registry.records(), List.of());
        assertEquals(this.course.key(), info.id());
        assertEquals(1, info.number());
        assertEquals("Stapel-Kurs", info.name());
        assertEquals("Stapel-Kurs", info.label());
        assertEquals(Optional.of(TestWorlds.ANNA), info.owner());
        assertEquals("Anna", info.ownerName());
        assertEquals(Identifier.withDefaultNamespace("overworld"), info.dimension());
        assertEquals(1, info.layout());
        assertEquals(List.of(1), info.layouts());
        assertEquals(3, info.laps());
        assertEquals(3, info.elements());
        assertEquals(this.course.checkpoints(), info.checkpoints());
        assertFalse(info.worldCourse());
        assertEquals(Optional.of(new BlockPos(-49, -60, -40)), info.start());
        assertEquals(ZoneHint.NONE, info.zones());
        BoardEntry record = info.record().orElseThrow();
        assertEquals(TestWorlds.ANNA, record.player());
        assertEquals(30_250L, record.timeMillis());
        CourseInfo free = RaceSnapshots.info(this.registry.course(2), this.registry.records(), List.of());
        assertEquals(CourseInfo.LAPS_PILOT_CHOICE, free.laps());
        assertFalse(free.lapsSet());
        assertTrue(free.record().isEmpty());
        CourseInfo nether = RaceSnapshots.info(this.registry.course(3), this.registry.records(), List.of());
        assertEquals("#3", nether.label());
        assertTrue(nether.endless());
        assertEquals(Identifier.withDefaultNamespace("the_nether"), nether.dimension());
        assertThrows(UnsupportedOperationException.class, () -> info.layouts().add(5));
    }

    @Test
    void zoneHintNamesTouchedZones() {
        RuleSpec spec = PhysicsRules.SPECS.getFirst();
        Zone legal = new Zone(4, TestWorlds.OVERWORLD, "Schwerelos", ZoneShape.BOX, ZoneBox.blocks(-55, -62, -45, -42, -50, -35), 0, true, 5,
                Map.of(spec.name(), spec.defaultValue()));
        Zone assist = new Zone(5, TestWorlds.OVERWORLD, "Sturm", ZoneShape.BOX, ZoneBox.blocks(-45, -62, -35, -35, -50, -25), 0, false, 5,
                Map.of(spec.name(), spec.defaultValue() + 10));
        Zone plain = new Zone(6, TestWorlds.OVERWORLD, "Übungsplatz", ZoneShape.BOX, ZoneBox.blocks(-45, -62, -35, -35, -50, -25), 0, false, 5, Map.of());
        Zone elsewhere = new Zone(7, "minecraft:the_nether", "Nether", ZoneShape.BOX, ZoneBox.blocks(-55, -62, -45, -42, -50, -35), 0, false, 5,
                Map.of(spec.name(), spec.defaultValue()));
        Zone far = new Zone(8, TestWorlds.OVERWORLD, "Fern", ZoneShape.BOX, ZoneBox.blocks(100, 0, 100, 120, 20, 120), 0, false, 5,
                Map.of(spec.name(), spec.defaultValue()));
        ZoneHint onlyLegal = RaceSnapshots.zones(this.course, List.of(legal, elsewhere, far, plain));
        assertEquals(ZoneHint.Status.RACE_LEGAL, onlyLegal.status());
        assertEquals(List.of("Schwerelos", "Übungsplatz"), onlyLegal.zones().stream().map(ZoneHint.Zone::name).toList());
        ZoneHint withAssist = RaceSnapshots.zones(this.course, List.of(legal, assist));
        assertEquals(ZoneHint.Status.ASSIST, withAssist.status());
        assertTrue(withAssist.zones().get(1).countsAsAssist());
        assertEquals(ZoneHint.NONE, RaceSnapshots.zones(this.course, List.of(far, elsewhere)));
    }

    @Test
    void lapBoardsRankBestPerPlayerAndClass() {
        BoardSnapshot overall = RaceSnapshots.board(this.course, this.registry.records(), BoardQuery.laps()).orElseThrow();
        assertEquals(this.course.key(), overall.course());
        assertTrue(overall.current());
        assertEquals(4, overall.total());
        assertEquals(List.of(TestWorlds.ANNA, TestWorlds.BEN, TestWorlds.CARL, TestWorlds.ANNA),
                overall.entries().stream().map(BoardEntry::player).toList());
        assertEquals(List.of("race_5", "race_5", "whoop_65", "whoop_65"), overall.entries().stream().map(BoardEntry::frame).toList());
        assertEquals(List.of(1, 2, 3, 4), overall.entries().stream().map(BoardEntry::rank).toList());
        BoardEntry ben = overall.entries().get(1);
        assertEquals(32_000L, ben.timeMillis(), "Millisekunden gerundet wie die Anzeige");
        assertEquals(31_999_600_000L, ben.timeNanos());
        assertEquals(DAY + 20, ben.dateMillis());
        assertEquals("race_5", ben.frame());
        assertEquals(TimeKind.LAP, ben.kind());
        assertEquals(Category.CLEAN, ben.category());
        assertTrue(ben.assistReasons().isEmpty());
        assertEquals(List.of("race_5", "whoop_65"), overall.frames());
        assertEquals(List.of(1, 3), overall.raceLapCounts());
        BoardSnapshot limited = RaceSnapshots.board(this.course, this.registry.records(), BoardQuery.laps().limit(1)).orElseThrow();
        assertEquals(1, limited.entries().size());
        assertEquals(4, limited.total());
        BoardSnapshot whoop = RaceSnapshots.board(this.course, this.registry.records(), BoardQuery.laps().frame("whoop_65")).orElseThrow();
        assertEquals(List.of(TestWorlds.CARL, TestWorlds.ANNA), whoop.entries().stream().map(BoardEntry::player).toList());
        BoardSnapshot assisted = RaceSnapshots.board(this.course, this.registry.records(), BoardQuery.laps().assisted()).orElseThrow();
        assertEquals(1, assisted.entries().size());
        BoardEntry hold = assisted.entries().getFirst();
        assertEquals(Category.ASSISTED, hold.category());
        assertEquals(List.of("hold_mode"), hold.assistReasons());
        assertEquals("hold_mode", hold.assistDetails().getFirst().reason());
        assertEquals("altitude", hold.assistDetails().getFirst().subject());
        assertThrows(UnsupportedOperationException.class, () -> overall.entries().clear());
    }

    @Test
    void raceBoardsCarryLapsDateFrameAndName() {
        BoardSnapshot three = RaceSnapshots.board(this.course, this.registry.records(), BoardQuery.races(3)).orElseThrow();
        assertEquals(List.of(TestWorlds.BEN, TestWorlds.ANNA), three.entries().stream().map(BoardEntry::player).toList());
        BoardEntry ben = three.entries().getFirst();
        assertEquals(TimeKind.RACE, ben.kind());
        assertEquals(3, ben.laps());
        assertEquals(93_000L, ben.timeMillis());
        assertEquals("Ben", ben.name());
        assertEquals("race_5", ben.frame());
        assertEquals(DAY + 60, ben.dateMillis());
        assertTrue(RaceSnapshots.board(this.course, this.registry.records(), BoardQuery.races(5)).orElseThrow().entries().isEmpty());
        this.registry.board(this.course).submitRace(TestWorlds.CARL, 2, 70 * S);
        BoardEntry old = RaceSnapshots.board(this.course, this.registry.records(), BoardQuery.races(2)).orElseThrow().entries().getFirst();
        assertEquals("Carl", old.name(), "Name aus den Rundenzeiten, wenn das Rennen ihn nicht kennt");
        assertFalse(old.dateKnown(), "Rennzeiten aus älteren Welten haben kein Datum");
        assertEquals("", old.frame());
    }

    @Test
    void queriesAreCheckedAndLayoutsAddressable() {
        assertThrows(IllegalArgumentException.class, () -> BoardQuery.laps().limit(0));
        assertThrows(IllegalArgumentException.class, () -> BoardQuery.laps().limit(-1));
        assertEquals(BoardQuery.MAX_LIMIT, BoardQuery.laps().limit(BoardQuery.MAX_LIMIT).limit());
        assertThrows(IllegalArgumentException.class, () -> BoardQuery.races(0));
        assertThrows(IllegalArgumentException.class, () -> BoardQuery.laps().layout(-1));
        assertEquals(Optional.empty(), BoardQuery.laps().frame("").frame());
        assertTrue(RaceSnapshots.board(this.course, this.registry.records(), BoardQuery.laps().layout(1)).orElseThrow().current());
        assertTrue(RaceSnapshots.board(this.course, this.registry.records(), BoardQuery.laps().layout(2)).isEmpty());
        assertEquals(30_250L, BoardEntry.millis(30_249_500_000L));
        assertEquals(30_249L, BoardEntry.millis(30_249_499_999L));
    }
}
