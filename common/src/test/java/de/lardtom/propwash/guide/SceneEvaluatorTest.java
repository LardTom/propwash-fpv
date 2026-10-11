package de.lardtom.propwash.guide;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.BlockSpec;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.DronePart;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scene.SceneState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneEvaluatorTest {
    private static final BlockSpec STONE = BlockSpec.of("minecraft:stone");

    private static Scene scene() {
        return SceneBuilder.scene("test")
                .icon("propwash:drone_workbench")
                .camera(ScenePos.ZERO, 0.0F, 30.0F, 1.0F)
                .step("a", 40.0F, s -> {
                    s.at(0.0F).drop(0, 0, 0, STONE, 10.0F);
                    s.at(5.0F).pop(1, 0, 0, STONE, 10.0F);
                    s.at(10.0F).spawn("item", new ActorSpec.Item("propwash:remote", 1.0F, false), new ScenePos(0.0F, 1.0F, 0.0F), 0.0F,
                            Cue.Appear.INSTANT, 0.0F);
                    s.at(10.0F).move("item", 20.0F, Ease.LINEAR, new ScenePos(2.0F, 1.0F, 0.0F));
                    s.at(12.0F).callout("tip", Anchor.on("item", 0.0F, 0.5F, 0.0F), Cue.Side.RIGHT, 0.0F);
                    s.at(20.0F).highlightBlock(0, 0, 0, 10.0F);
                    s.at(20.0F).keys("use", 0.0F, KeyToken.use());
                })
                .step("b", 30.0F, s -> {
                    s.at(0.0F).remove(0, 0, 0, 10.0F);
                    s.at(0.0F).turn("item", 350.0F, 0.0F, 0.0F, 10.0F);
                    s.at(0.0F).spawn("drone", new ActorSpec.Drone(DroneBuild.FREESTYLE, false), ScenePos.ZERO, 0.0F, Cue.Appear.INSTANT, 0.0F);
                    s.at(2.0F).attach("drone", DronePart.MOTORS, new ScenePos(1.0F, 0.0F, 1.0F), 20.0F, 4.0F);
                    s.at(0.0F).camera(new ScenePos(1.0F, 1.0F, 1.0F), 90.0F, 45.0F, 2.0F, 10.0F);
                    s.at(20.0F).despawn("item", 10.0F);
                })
                .build();
    }

    @Test
    void stepTimingAndLookup() {
        Scene scene = scene();
        assertEquals(70.0F, scene.duration());
        assertEquals(0, scene.stepAt(0.0F));
        assertEquals(0, scene.stepAt(39.9F));
        assertEquals(1, scene.stepAt(40.0F));
        assertEquals(1, scene.stepAt(70.0F));
        assertEquals(40.0F, scene.stepStart(1));
        assertEquals(70.0F, scene.stepEnd(1));
    }

    @Test
    void blocksDropPopAndRemove() {
        Scene scene = scene();
        SceneState start = SceneEvaluator.evaluate(scene, 0.0F);
        SceneState.BlockView dropping = start.block(0, 0, 0);
        assertNotNull(dropping);
        assertEquals(SceneEvaluator.DROP_HEIGHT, dropping.lift(), 1.0E-4F);
        assertNull(start.block(1, 0, 0));
        SceneState landed = SceneEvaluator.evaluate(scene, 10.0F);
        assertEquals(0.0F, landed.block(0, 0, 0).lift(), 1.0E-4F);
        assertTrue(landed.block(1, 0, 0).scale() > 0.0F);
        assertEquals(1.0F, SceneEvaluator.evaluate(scene, 15.0F).block(1, 0, 0).scale(), 1.0E-4F);
        SceneState removing = SceneEvaluator.evaluate(scene, 45.0F);
        assertTrue(removing.block(0, 0, 0).scale() < 1.0F);
        assertNull(SceneEvaluator.evaluate(scene, 50.0F).block(0, 0, 0));
        assertNotNull(SceneEvaluator.evaluate(scene, 50.0F).block(1, 0, 0));
    }

    @Test
    void actorsMoveTurnAndDespawn() {
        Scene scene = scene();
        assertNull(SceneEvaluator.evaluate(scene, 9.0F).actor("item"));
        ActorState half = SceneEvaluator.evaluate(scene, 20.0F).actor("item");
        assertNotNull(half);
        assertEquals(1.0F, half.position().x(), 1.0E-4F);
        ActorState end = SceneEvaluator.evaluate(scene, 30.0F).actor("item");
        assertEquals(2.0F, end.position().x(), 1.0E-4F);
        ActorState turned = SceneEvaluator.evaluate(scene, 50.0F).actor("item");
        assertEquals(-10.0F, turned.yaw(), 1.0E-3F);
        ActorState fading = SceneEvaluator.evaluate(scene, 65.0F).actor("item");
        assertTrue(fading.presence() < 1.0F && fading.presence() > 0.0F);
        assertNull(SceneEvaluator.evaluate(scene, 70.0F).actor("item"));
    }

    @Test
    void transientCuesFadeAndFollowActors() {
        Scene scene = scene();
        SceneState early = SceneEvaluator.evaluate(scene, 12.0F);
        assertEquals(1, early.callouts().size());
        assertEquals(0.0F, early.callouts().getFirst().alpha(), 1.0E-4F);
        SceneState mid = SceneEvaluator.evaluate(scene, 25.0F);
        SceneState.CalloutView callout = mid.callouts().getFirst();
        assertEquals(1.0F, callout.alpha(), 1.0E-4F);
        assertEquals(mid.actor("item").position().x(), callout.anchor().x(), 1.0E-4F);
        assertEquals(1.5F, callout.anchor().y(), 1.0E-4F);
        assertEquals(1, mid.highlights().size());
        assertEquals(1, mid.keyHints().size());
        assertTrue(SceneEvaluator.evaluate(scene, 38.0F).callouts().getFirst().alpha() < 1.0F);
        SceneState next = SceneEvaluator.evaluate(scene, 40.0F);
        assertTrue(next.callouts().isEmpty());
        assertTrue(next.keyHints().isEmpty());
        assertTrue(SceneEvaluator.evaluate(scene, 31.0F).highlights().isEmpty());
    }

    @Test
    void partsAttachWithStagger() {
        Scene scene = scene();
        ActorState before = SceneEvaluator.evaluate(scene, 41.0F).actor("drone");
        assertNotNull(before);
        assertFalse(before.part(DronePart.MOTORS).visible());
        assertFalse(before.fullyAssembled());
        ActorState.PartState motors = SceneEvaluator.evaluate(scene, 43.0F).actor("drone").part(DronePart.MOTORS);
        assertTrue(motors.slotVisible(0));
        assertFalse(motors.slotVisible(3));
        assertTrue(motors.moving());
        ActorState.PartState landed = SceneEvaluator.evaluate(scene, 62.0F).actor("drone").part(DronePart.MOTORS);
        assertTrue(landed.attached());
        assertEquals(0.0F, landed.offset(2).x(), 1.0E-5F);
        assertFalse(SceneEvaluator.evaluate(scene, 62.0F).actor("drone").fullyAssembled());
    }

    @Test
    void cameraBlendsTowardsCue() {
        Scene scene = scene();
        assertEquals(0.0F, SceneEvaluator.evaluate(scene, 40.0F).camera().yaw(), 1.0E-4F);
        SceneState done = SceneEvaluator.evaluate(scene, 50.0F);
        assertEquals(90.0F, done.camera().yaw(), 1.0E-3F);
        assertEquals(2.0F, done.camera().zoom(), 1.0E-3F);
        float mid = SceneEvaluator.evaluate(scene, 45.0F).camera().zoom();
        assertTrue(mid > 1.0F && mid < 2.0F);
    }

    @Test
    void timeIsClamped() {
        Scene scene = scene();
        assertEquals(0.0F, SceneEvaluator.evaluate(scene, -5.0F).time());
        assertEquals(70.0F, SceneEvaluator.evaluate(scene, 500.0F).time());
        assertEquals(0.0F, SceneEvaluator.evaluate(scene, Float.NaN).time());
    }

    @Test
    void cuesMustEndInsideTheirStep() {
        assertThrows(IllegalArgumentException.class, () -> SceneBuilder.scene("bad").icon("propwash:drone")
                .step("a", 10.0F, s -> s.at(5.0F).drop(0, 0, 0, STONE, 10.0F)).build());
        assertThrows(IllegalArgumentException.class, () -> SceneBuilder.scene("bad").icon("propwash:drone")
                .step("a", 10.0F, s -> {
                })
                .step("a", 10.0F, s -> {
                }).build());
        assertThrows(IllegalArgumentException.class, () -> SceneBuilder.scene("Bad Id").icon("propwash:drone")
                .step("a", 10.0F, s -> {
                }).build());
    }

    @Test
    void catmullRomPassesThroughPoints() {
        ScenePos from = ScenePos.ZERO;
        List<ScenePos> path = List.of(new ScenePos(1.0F, 1.0F, 0.0F), new ScenePos(2.0F, 0.0F, 0.0F));
        assertEquals(from, SceneEvaluator.path(from, path, 0.0F));
        ScenePos middle = SceneEvaluator.path(from, path, 0.5F);
        assertEquals(1.0F, middle.x(), 1.0E-4F);
        assertEquals(1.0F, middle.y(), 1.0E-4F);
        ScenePos end = SceneEvaluator.path(from, path, 1.0F);
        assertEquals(2.0F, end.x(), 1.0E-4F);
        assertEquals(0.0F, end.y(), 1.0E-4F);
    }
}
