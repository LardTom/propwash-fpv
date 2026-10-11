package de.lardtom.propwash.dev.example.client;

import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.BlockSpec;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;

public final class ExampleScene {
    public static final String ID = ExampleAddon.NAMESPACE + ":frames";
    public static final String DEADCAT = "deadcat";
    public static final String STRETCHED = "stretched";
    public static final String XCLASS = "xclass";
    public static final ScenePos DEADCAT_POS = new ScenePos(-1.2F, 1.0F, 0.2F);
    public static final ScenePos STRETCHED_POS = new ScenePos(0.6F, 1.0F, 0.2F);
    public static final ScenePos XCLASS_POS = new ScenePos(2.9F, 1.0F, 0.0F);

    private static final BlockSpec GROUND = BlockSpec.of("minecraft:gray_concrete");
    private static final ScenePos OVERVIEW = new ScenePos(0.8F, 1.3F, 0.0F);

    private ExampleScene() {
    }

    public static Scene create() {
        ActorSpec.Drone deadcat = PropwashClientApi.flightSchoolDrone(ExampleAddon.deadcat().build(), true, ActorSpec.Drone.STAGE_SCALE);
        ActorSpec.Drone stretched = PropwashClientApi.flightSchoolDrone(ExampleAddon.stretched().build(), true, ActorSpec.Drone.STAGE_SCALE);
        ActorSpec.Drone xclass = PropwashClientApi.flightSchoolDrone(ExampleAddon.xclass().build(), true, 1.0F);
        return SceneBuilder.scene(ID)
                .icon(ExampleAddon.FRAME_DEADCAT.toString())
                .items(ExampleAddon.FRAME_STRETCH.toString(), ExampleAddon.FRAME_XCLASS.toString())
                .view(2.8F)
                .camera(OVERVIEW, 20.0F, 24.0F, 1.0F)
                .step(DEADCAT, 120.0F, s -> {
                    s.at(0.0F).floor(-3, -2, 4, 2, 0, GROUND, 1.2F, 10.0F);
                    s.at(14.0F).spawn(DEADCAT, deadcat, DEADCAT_POS, 200.0F, Cue.Appear.POP, 10.0F);
                    s.at(24.0F).camera(DEADCAT_POS.add(0.0F, 0.4F, 0.0F), 20.0F, 22.0F, 1.5F, 20.0F);
                    s.at(34.0F).callout(DEADCAT, Anchor.on(DEADCAT, 0.0F, 0.2F, 0.0F), Cue.Side.ABOVE, 0.0F);
                })
                .step(STRETCHED, 120.0F, s -> {
                    s.at(0.0F).spawn(STRETCHED, stretched, STRETCHED_POS, 200.0F, Cue.Appear.POP, 10.0F);
                    s.at(4.0F).camera(STRETCHED_POS.add(0.0F, 0.4F, 0.0F), 20.0F, 22.0F, 1.5F, 20.0F);
                    s.at(24.0F).callout(STRETCHED, Anchor.on(STRETCHED, 0.0F, 0.2F, 0.0F), Cue.Side.ABOVE, 0.0F);
                })
                .step(XCLASS, 140.0F, s -> {
                    s.at(0.0F).spawn(XCLASS, xclass, XCLASS_POS, 200.0F, Cue.Appear.POP, 12.0F);
                    s.at(6.0F).camera(OVERVIEW.add(0.8F, 0.2F, 0.0F), 20.0F, 26.0F, 0.9F, 24.0F);
                    s.at(30.0F).callout(XCLASS, Anchor.on(XCLASS, 0.0F, 0.3F, 0.0F), Cue.Side.ABOVE, 0.0F);
                })
                .build();
    }
}
