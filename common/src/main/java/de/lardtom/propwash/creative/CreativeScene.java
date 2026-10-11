package de.lardtom.propwash.creative;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;
import de.lardtom.propwash.guide.scenes.SceneKit;

public final class CreativeScene {
    public static final String ID = "creative_drone";
    public static final String CONTROLLER = "controller";
    public static final String WHOOP = "whoop";
    public static final String FREESTYLE = "freestyle";
    public static final String CREATIVE_FC = SceneKit.part(StackType.CREATIVE_FC);
    public static final DroneBuild WHOOP_BUILD = withController(DroneBuild.WHOOP);
    public static final DroneBuild FREESTYLE_BUILD = withController(DroneBuild.FREESTYLE);
    public static final ScenePos WHOOP_POS = new ScenePos(-0.7F, 1.0F, 0.0F);
    public static final ScenePos FREESTYLE_POS = new ScenePos(0.8F, 1.0F, 0.0F);
    public static final ScenePos CONTROLLER_POS = new ScenePos(0.05F, 1.7F, 0.0F);

    private static final ScenePos OVERVIEW = new ScenePos(0.05F, 1.3F, 0.0F);

    private CreativeScene() {
    }

    private static DroneBuild withController(DroneBuild build) {
        return new DroneBuild(build.frame(), StackType.CREATIVE_FC, build.motor(), build.prop(), build.video(), build.battery());
    }

    public static Scene create() {
        Anchor onController = Anchor.on(CONTROLLER, 0.0F, 0.25F, 0.0F);
        Anchor onFreestyle = Anchor.on(FREESTYLE, 0.0F, 0.15F, 0.0F);
        Anchor onWhoop = Anchor.on(WHOOP, 0.0F, 0.1F, 0.0F);
        return SceneBuilder.scene(ID)
                .icon(CREATIVE_FC)
                .items(CREATIVE_FC)
                .view(1.6F)
                .camera(OVERVIEW, 20.0F, 24.0F, 1.0F)
                .step("controller", 130.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -2, -1, 2, 1, SceneKit.GRASS, SceneKit.GRASS, 1.2F, 10.0F);
                    s.at(10.0F).spawn(CONTROLLER, new ActorSpec.Item(CREATIVE_FC, 1.8F, true), CONTROLLER_POS, 0.0F, Cue.Appear.POP, 10.0F);
                    s.at(16.0F).callout("controller", onController, Cue.Side.ABOVE, 0.0F);
                    s.at(30.0F).spawn(WHOOP, new ActorSpec.Drone(WHOOP_BUILD, true), WHOOP_POS, 160.0F, Cue.Appear.POP, 10.0F);
                    s.at(36.0F).spawn(FREESTYLE, new ActorSpec.Drone(FREESTYLE_BUILD, true), FREESTYLE_POS, 200.0F, Cue.Appear.POP, 10.0F);
                    s.at(44.0F).highlight(WHOOP_POS.add(-0.2F, 0.0F, -0.2F), WHOOP_POS.add(0.2F, 0.15F, 0.2F), 70.0F);
                    s.at(44.0F).highlight(FREESTYLE_POS.add(-0.3F, 0.0F, -0.3F), FREESTYLE_POS.add(0.3F, 0.2F, 0.3F), 70.0F);
                    s.at(50.0F).callout("every_frame", onWhoop, Cue.Side.BELOW, 0.0F);
                    s.at(80.0F).callout("creative_only", onController, Cue.Side.RIGHT, 0.0F);
                })
                .step("editor", 140.0F, s -> {
                    s.at(0.0F).camera(FREESTYLE_POS.add(-0.2F, 0.35F, 0.0F), 20.0F, 22.0F, 1.9F, 22.0F);
                    s.at(0.0F).move(CONTROLLER, 20.0F, Ease.IN_OUT, CONTROLLER_POS, FREESTYLE_POS.add(-0.15F, 0.6F, 0.0F));
                    s.at(20.0F).keysAt("configurator", Anchor.at(FREESTYLE_POS.add(-0.75F, 0.15F, 0.0F)), 0.0F,
                            KeyToken.mapping(SceneKit.KEY_CONFIGURATOR));
                    s.at(24.0F).callout("editor", onFreestyle, Cue.Side.BELOW, 0.0F);
                    s.at(60.0F).callout("values", Anchor.on(CONTROLLER, 0.0F, 0.2F, 0.0F), Cue.Side.ABOVE, 0.0F);
                })
                .step("templates", 130.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW, 20.0F, 22.0F, 1.0F, 22.0F);
                    s.at(6.0F).callout("templates", onWhoop, Cue.Side.BELOW, 0.0F);
                    s.at(40.0F).callout("share", onController, Cue.Side.ABOVE, 0.0F);
                })
                .step("flight", 150.0F, s -> {
                    s.at(0.0F).despawn(CONTROLLER, 8.0F);
                    s.at(0.0F).camera(FREESTYLE_POS.add(-0.4F, 0.5F, 0.0F), 20.0F, 20.0F, 1.5F, 24.0F);
                    s.at(10.0F).arm(FREESTYLE, true);
                    s.at(10.0F).throttle(FREESTYLE, 0.6F, 10.0F);
                    s.at(16.0F).move(FREESTYLE, 50.0F, Ease.IN_OUT, FREESTYLE_POS.add(0.0F, 0.4F, 0.0F), FREESTYLE_POS.add(-0.3F, 0.7F, -0.1F));
                    s.at(20.0F).callout("physics", Anchor.on(FREESTYLE, 0.0F, 0.15F, 0.0F), Cue.Side.RIGHT, 0.0F);
                    s.at(76.0F).callout("survival", onWhoop, Cue.Side.BELOW, 0.0F);
                })
                .build();
    }
}
