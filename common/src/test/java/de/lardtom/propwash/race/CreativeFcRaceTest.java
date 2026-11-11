package de.lardtom.propwash.race;

import de.lardtom.propwash.api.race.CourseMode;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.HoldMode;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreativeFcRaceTest {
    private static final long S = 1_000_000_000L;
    private static final AssistDetail PERSONAL = AssistDetail.personal(WorldRuleSpecs.WIND, 0, 1);

    private static AssistCheck.LapVerdict flown(AssistReasons server, AssistReasons client) {
        AssistTimeline assist = new AssistTimeline();
        for (long t = 0L; t <= S; t += S / 20) {
            assist.observe(t, server);
        }
        return AssistCheck.serverLapVerdict(assist, new RuleTimeline(), S / 10, S * 9 / 10, AssistReasons.NONE, RaceRuleSet.DEFAULTS, client,
                RaceStandard.DEFAULTS);
    }

    private static AssistReasons creative() {
        return AssistReasons.of(List.of(AssistDetail.creative()));
    }

    @Test
    void worldRuleIsANeutralGameplaySwitchThatStartsOff() {
        RuleSpec spec = WorldRuleSpecs.byName("creative_fc_race_legal").orElseThrow();
        assertSame(WorldRuleSpecs.CREATIVE_FC_RACE_LEGAL, spec);
        assertEquals(RuleSpec.Kind.BOOL, spec.kind());
        assertEquals(RuleSpec.Group.GAMEPLAY, spec.group());
        assertEquals(0, spec.defaultValue(), "Standard: Kreativ-FC zählt als Flughilfe wie bisher");
        assertFalse(spec.raceRelevant(), "die Regel selbst ändert keine Rennwerte; ihr Wechsel ordnet keine alten Zeiten neu ein");
    }

    @Test
    void courseSettingOverridesTheWorldRule() {
        Map<CreativeFcRule, boolean[]> expected = Map.of(CreativeFcRule.WORLD, new boolean[]{false, true}, CreativeFcRule.ASSIST,
                new boolean[]{false, false}, CreativeFcRule.LEGAL, new boolean[]{true, true});
        for (CreativeFcRule rule : CreativeFcRule.values()) {
            for (int world = 0; world < 2; world++) {
                assertEquals(expected.get(rule)[world], rule.legal(world == 1), rule + " bei Welt-Regel " + (world == 1 ? "an" : "aus"));
            }
        }
        assertEquals(CreativeFcRule.WORLD, CreativeFcRule.byKey(null));
        assertEquals(CreativeFcRule.WORLD, CreativeFcRule.byKey("from_a_newer_version"));
        for (CreativeFcRule rule : CreativeFcRule.values()) {
            assertEquals(rule, CreativeFcRule.byKey(rule.key()));
            assertEquals(rule, CreativeFcRule.byOrdinal(rule.ordinal()));
        }
        assertNull(CreativeFcRule.WORLD.stored(), "Standard steht nicht in der Kursdatei");
        assertEquals(CreativeFcRule.WORLD, CreativeFcRule.byOrdinal(7));
    }

    @Test
    void everyCombinationOfWorldAndCourseJudgesTheLap() {
        for (CreativeFcRule rule : CreativeFcRule.values()) {
            for (boolean world : new boolean[]{false, true}) {
                boolean legal = rule.legal(world);
                AssistCheck.LapVerdict verdict = AssistCheck.creativeFc(flown(creative(), AssistReasons.NONE), legal);
                String label = rule + " bei Welt-Regel " + (world ? "an" : "aus");
                assertEquals(legal, verdict.clean(), label + ": " + verdict.reasons());
                assertEquals(legal, verdict.creativeFc(), label + ": Runde trägt den Stand");
                assertEquals(!legal, verdict.fixed().has(LapAssist.CREATIVE_DRONE), label + ": gespeicherter Grund");
            }
        }
    }

    @Test
    void raceLegalDropsOnlyTheCreativeFlightController() {
        AssistReasons server = AssistReasons.of(List.of(AssistDetail.creative(), AssistDetail.issue("prop_too_large"), AssistDetail.scale(0.5F)));
        AssistReasons client = AssistReasons.of(List.of(AssistDetail.hold(HoldMode.ALTHOLD), AssistDetail.goggles(10, 30)));
        AssistCheck.LapVerdict legal = AssistCheck.creativeFc(flown(server, client), true);
        assertFalse(legal.reasons().has(LapAssist.CREATIVE_DRONE));
        for (LapAssist kept : List.of(LapAssist.PARTS_MISMATCH, LapAssist.SCALE, LapAssist.HOLD_MODE, LapAssist.GOGGLES_LATENCY)) {
            assertTrue(legal.reasons().has(kept), kept + " bleibt: " + legal.reasons());
            assertTrue(legal.fixed().has(kept), kept + " bleibt gespeichert");
        }
        assertTrue(legal.creativeFc());
        AssistReasons personal = AssistCheck.creativeFc(AssistReasons.of(List.of(AssistDetail.creative(), PERSONAL)), true);
        assertEquals(List.of(PERSONAL), personal.details(), "persönliche Regeln bleiben eine Flughilfe");
        assertEquals(LapAssist.PERSONAL_RULES.bit(), personal.mask());
        AssistCheck.LapVerdict assisted = flown(server, client);
        assertSame(assisted, AssistCheck.creativeFc(assisted, false), "zählt als Flughilfe: Urteil unverändert");
        AssistCheck.LapVerdict plain = AssistCheck.creativeFc(flown(AssistReasons.NONE, AssistReasons.NONE), true);
        assertTrue(plain.clean());
        assertFalse(plain.creativeFc(), "ohne Kreativ-FC trägt die Runde keinen Vermerk");
        assertEquals(AssistReasons.NONE, AssistCheck.creativeFc(creative(), true));
        assertEquals(creative(), AssistCheck.creativeFc(creative(), false));
    }

    @Test
    void onlyAdminsMayChangeTheSwitch() {
        assertEquals(CourseAccess.ALLOWED, CourseAccess.creativeFc(true));
        assertEquals(CourseAccess.ADMIN_ONLY, CourseAccess.creativeFc(false));
        CourseMenu.Rights owner = CourseMenu.Rights.of(CourseAccess.ALLOWED, CourseAccess.ALLOWED, true, false, false);
        assertFalse(owner.admin());
        assertFalse(CourseMenu.active(CourseMenu.Row.CREATIVE_FC, owner), "der Ersteller des Kurses ändert den Schalter nicht");
        assertTrue(CourseMenu.active(CourseMenu.Row.NAME, owner));
        CourseMenu.Rights viewer = CourseMenu.Rights.of(CourseAccess.NOT_OWNER, CourseAccess.NOT_OWNER, false, false, false);
        assertFalse(CourseMenu.active(CourseMenu.Row.CREATIVE_FC, viewer));
        CourseMenu.Rights admin = CourseMenu.Rights.of(CourseAccess.NOT_OWNER, CourseAccess.NOT_OWNER, false, false, true);
        assertTrue(admin.admin());
        assertFalse(admin.readOnly());
        assertTrue(CourseMenu.active(CourseMenu.Row.CREATIVE_FC, admin), "wer /gamerule darf, ändert den Schalter auch an fremden Kursen");
        assertFalse(CourseMenu.active(CourseMenu.Row.NAME, admin));
        CourseMenu.Rights operator = CourseMenu.Rights.of(CourseAccess.ALLOWED, CourseAccess.ALLOWED, false, true);
        assertTrue(CourseMenu.active(CourseMenu.Row.CREATIVE_FC, operator), "Operatoren dürfen /gamerule");
        assertEquals(CourseMenu.Section.BOARD, CourseMenu.Row.CREATIVE_FC.section());
        assertEquals(CourseMenu.Need.ADMIN, CourseMenu.Row.CREATIVE_FC.need());
    }

    @Test
    void courseDataCarriesTheSettingWithoutAnExtraByte() {
        for (CreativeFcRule rule : CreativeFcRule.values()) {
            CourseData data = new CourseData(4, "minecraft:overworld", List.of(), "Kreativ", 3, UUID.randomUUID(), "Anna", UUID.randomUUID(),
                    CourseMode.POINT_TO_POINT, rule);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            CourseData.STREAM_CODEC.encode(buf, data);
            CourseData plain = data.withCreativeFc(CreativeFcRule.WORLD);
            FriendlyByteBuf old = new FriendlyByteBuf(Unpooled.buffer());
            CourseData.STREAM_CODEC.encode(old, plain);
            assertEquals(old.readableBytes(), buf.readableBytes(), "gleich lang wie bisher: " + rule);
            CourseData read = CourseData.STREAM_CODEC.decode(buf);
            assertEquals(data, read);
            assertEquals(rule, read.creativeFc());
            assertEquals(CourseMode.POINT_TO_POINT, read.mode());
        }
        CourseData legacy = new CourseData(1, "minecraft:overworld", List.of(), "Alt", 3, null, "", null, CourseMode.LAPS);
        assertEquals(CreativeFcRule.WORLD, legacy.creativeFc());
        assertEquals(legacy.layoutHash(), legacy.withCreativeFc(CreativeFcRule.LEGAL).layoutHash(), "Schalter ändert das Layout nicht");
    }
}
