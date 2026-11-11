package de.lardtom.propwash.share.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.BlockSpec;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.DronePart;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;

public final class ShareScene {
    public static final String ID = "sharing";
    public static final String WORKBENCH = "propwash:drone_workbench";
    public static final String DRONE_ITEM = "propwash:drone";
    public static final String GOGGLES = "propwash:goggles_analog";
    public static final String SOURCE = "source";
    public static final String TARGET = "target";
    public static final String PILOT = "pilot";
    public static final String MISSING = "missing";
    public static final String BARRIER = "barrier";
    public static final ScenePos SOURCE_POS = new ScenePos(0.5F, 2.0F, 0.5F);
    public static final ScenePos TARGET_POS = new ScenePos(3.5F, 2.0F, 0.5F);
    public static final ScenePos PILOT_POS = new ScenePos(1.9F, 1.0F, 2.3F);

    private static final BlockSpec GROUND = BlockSpec.of("minecraft:gray_concrete");
    private static final BlockSpec BENCH = BlockSpec.of(WORKBENCH);
    private static final ScenePos OVERVIEW = new ScenePos(2.0F, 1.6F, 0.5F);

    private ShareScene() {
    }

    public static BuildSpec freestyle() {
        for (DronePreset preset : PropwashApi.builds().presets()) {
            if (preset.id().equals(Constants.id("freestyle"))) {
                return preset.data().build();
            }
        }
        return BuildSpec.of(Constants.id("freestyle_5"), Constants.id("stack_30"), Constants.id("m2207_1750"),
                Constants.id("p51_3b"), Constants.id("analog_race"), Constants.id("b6s_1300"));
    }

    public static Scene create() {
        BuildSpec build = freestyle();
        ActorSpec.Drone source = PropwashClientApi.flightSchoolDrone(build, true, ActorSpec.Drone.STAGE_SCALE);
        ActorSpec.Drone target = PropwashClientApi.flightSchoolDrone(build, false, ActorSpec.Drone.STAGE_SCALE);
        String motorItem = build.motor().toString();
        return SceneBuilder.scene(ID)
                .icon(WORKBENCH)
                .items(DRONE_ITEM)
                .view(3.6F)
                .camera(OVERVIEW, 24.0F, 30.0F, 1.0F)
                .step("export", 120.0F, s -> {
                    s.at(0.0F).floor(-2, -2, 5, 2, 0, GROUND, 0.8F, 10.0F);
                    s.at(18.0F).drop(0, 1, 0, BENCH, 10.0F);
                    s.at(30.0F).spawn(SOURCE, source, SOURCE_POS, 200.0F, Cue.Appear.POP, 10.0F);
                    s.at(34.0F).camera(SOURCE_POS.add(0.0F, 0.2F, 0.0F), 30.0F, 32.0F, 1.6F, 20.0F);
                    s.at(46.0F).callout("export", Anchor.on(SOURCE, 0.0F, 0.2F, 0.0F), Cue.Side.LEFT, 0.0F);
                })
                .step("import", 150.0F, s -> {
                    s.at(0.0F).drop(3, 1, 0, BENCH, 10.0F);
                    s.at(6.0F).camera(TARGET_POS.add(0.0F, 0.2F, 0.0F), 30.0F, 34.0F, 1.6F, 22.0F);
                    s.at(12.0F).spawn(TARGET, target, TARGET_POS, 200.0F, Cue.Appear.INSTANT, 0.0F);
                    s.at(16.0F).attach(TARGET, DronePart.FRAME, new ScenePos(-1.0F, 0.6F, 0.4F), 20.0F, 0.0F);
                    s.at(24.0F).callout("import", Anchor.on(TARGET, 0.0F, 0.2F, 0.0F), Cue.Side.RIGHT, 0.0F);
                    s.at(36.0F).attach(TARGET, DronePart.STACK, new ScenePos(0.0F, 0.7F, 0.0F), 14.0F, 0.0F);
                    s.at(50.0F).attach(TARGET, DronePart.MOTORS, new ScenePos(0.8F, 0.4F, 0.8F), 30.0F, 5.0F);
                    s.at(80.0F).attach(TARGET, DronePart.PROPS, new ScenePos(0.0F, 0.6F, 0.0F), 28.0F, 4.0F);
                    s.at(108.0F).attach(TARGET, DronePart.VIDEO, new ScenePos(0.0F, 0.3F, -0.9F), 14.0F, 0.0F);
                    s.at(122.0F).attach(TARGET, DronePart.BATTERY, new ScenePos(0.0F, 0.8F, 0.0F), 14.0F, 0.0F);
                })
                .step("shopping", 110.0F, s -> {
                    ScenePos item = TARGET_POS.add(0.9F, 0.2F, 0.6F);
                    s.at(0.0F).camera(TARGET_POS.add(0.4F, 0.1F, 0.3F), 36.0F, 30.0F, 1.5F, 20.0F);
                    s.at(6.0F).spawn(MISSING, new ActorSpec.Item(motorItem, 1.2F, true), item, 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(12.0F).spawn(BARRIER, new ActorSpec.Item("minecraft:barrier", 0.7F, false), item.add(0.0F, 0.22F, 0.0F), 0.0F,
                            Cue.Appear.POP, 8.0F);
                    s.at(14.0F).callout("shopping", Anchor.on(MISSING, 0.0F, 0.1F, 0.0F), Cue.Side.RIGHT, 0.0F);
                    s.at(96.0F).despawn(MISSING, 10.0F);
                    s.at(96.0F).despawn(BARRIER, 10.0F);
                })
                .step("held", 120.0F, s -> {
                    s.at(0.0F).camera(PILOT_POS.add(0.0F, 0.9F, 0.0F), 20.0F, 18.0F, 1.3F, 20.0F);
                    s.at(4.0F).spawn(PILOT, new ActorSpec.Pilot(GOGGLES, DRONE_ITEM), PILOT_POS, 200.0F, Cue.Appear.POP, 10.0F);
                    s.at(20.0F).callout("held", Anchor.on(PILOT, 0.0F, 2.1F, 0.0F), Cue.Side.ABOVE, 0.0F);
                })
                .build();
    }
}
