package de.lardtom.propwash.guide.scenes;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;

import static de.lardtom.propwash.guide.scenes.SceneKit.BARRIER;
import static de.lardtom.propwash.guide.scenes.SceneKit.GOGGLES_ANALOG;
import static de.lardtom.propwash.guide.scenes.SceneKit.GOGGLES_CREATIVE;
import static de.lardtom.propwash.guide.scenes.SceneKit.GOGGLES_DIGITAL;
import static de.lardtom.propwash.guide.scenes.SceneKit.GRASS;

public final class VideoScene {
    public static final String ID = "video";
    public static final String ANALOG = "analog";
    public static final String DIGITAL = "digital";
    public static final String PILOT = "pilot";
    public static final DroneBuild ANALOG_BUILD = DroneBuild.RACER;
    public static final DroneBuild DIGITAL_BUILD = DroneBuild.FREESTYLE;
    public static final ScenePos ANALOG_POS = new ScenePos(-1.4F, 1.0F, 0.4F);
    public static final ScenePos DIGITAL_POS = new ScenePos(2.4F, 1.0F, 0.4F);
    public static final ScenePos PILOT_POS = new ScenePos(0.5F, 1.0F, -1.2F);

    private static final ScenePos OVERVIEW = new ScenePos(0.5F, 1.7F, 0.0F);
    private static final float LIFT = 0.95F;

    private VideoScene() {
    }

    public static Scene create() {
        Anchor overHead = Anchor.on(PILOT, 0.0F, 2.15F, 0.0F);
        Anchor onAnalog = Anchor.on(ANALOG, 0.0F, 0.12F, 0.0F);
        Anchor onDigital = Anchor.on(DIGITAL, 0.0F, 0.12F, 0.0F);
        ScenePos analogIcon = ANALOG_POS.add(0.0F, LIFT, 0.0F);
        ScenePos digitalIcon = DIGITAL_POS.add(0.0F, LIFT, 0.0F);
        return SceneBuilder.scene(ID)
                .icon(SceneKit.part(VideoType.ANALOG_NANO))
                .items(SceneKit.videos())
                .items(GOGGLES_ANALOG, GOGGLES_DIGITAL, GOGGLES_CREATIVE)
                .view(2.2F)
                .camera(OVERVIEW, 18.0F, 22.0F, 1.0F)
                .step("analog", 130.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -3, -2, 4, 2, GRASS, GRASS, 1.2F, 10.0F);
                    s.at(14.0F).spawn(ANALOG, new ActorSpec.Drone(ANALOG_BUILD, true), ANALOG_POS, 160.0F, Cue.Appear.POP, 10.0F);
                    s.at(18.0F).spawn(DIGITAL, new ActorSpec.Drone(DIGITAL_BUILD, true), DIGITAL_POS, 200.0F, Cue.Appear.POP, 10.0F);
                    s.at(20.0F).spawn(PILOT, new ActorSpec.Pilot("", SceneKit.REMOTE), PILOT_POS, 0.0F, Cue.Appear.POP, 10.0F);
                    s.at(24.0F).camera(ANALOG_POS.add(1.1F, 0.9F, 0.0F), 18.0F, 18.0F, 1.3F, 20.0F);
                    s.at(30.0F).spawn("analog_goggles", new ActorSpec.Item(GOGGLES_ANALOG, 1.6F, true), analogIcon, 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(34.0F).callout("analog", onAnalog, Cue.Side.BELOW, 0.0F);
                    s.at(60.0F).callout("analog_goggles", Anchor.on("analog_goggles", 0.0F, 0.2F, 0.0F), Cue.Side.LEFT, 0.0F);
                })
                .step("digital", 130.0F, s -> {
                    s.at(0.0F).camera(DIGITAL_POS.add(-1.1F, 0.9F, 0.0F), 18.0F, 18.0F, 1.3F, 24.0F);
                    s.at(10.0F).spawn("digital_goggles", new ActorSpec.Item(GOGGLES_DIGITAL, 1.6F, true), digitalIcon, 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(14.0F).callout("digital", onDigital, Cue.Side.BELOW, 0.0F);
                    s.at(44.0F).callout("digital_goggles", Anchor.on("digital_goggles", 0.0F, 0.2F, 0.0F), Cue.Side.RIGHT, 0.0F);
                })
                .step("mismatch", 140.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW, 18.0F, 22.0F, 1.0F, 24.0F);
                    s.at(0.0F).despawn("analog_goggles", 8.0F);
                    s.at(0.0F).despawn("digital_goggles", 8.0F);
                    s.at(8.0F).equip(PILOT, Cue.Slot.HAND, GOGGLES_ANALOG);
                    s.at(8.0F).turn(PILOT, SceneKit.pilotYaw(PILOT_POS, DIGITAL_POS), 0.0F, 0.0F, 12.0F);
                    s.at(8.0F).look(PILOT, SceneKit.headPitch(PILOT_POS, DIGITAL_POS), 12.0F);
                    s.at(20.0F).keysAt("bind", overHead, 44.0F, KeyToken.use());
                    s.at(30.0F).spawn("no", new ActorSpec.Item(BARRIER, 1.1F, false), digitalIcon, 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(32.0F).callout("mismatch", onDigital, Cue.Side.BELOW, 44.0F);
                    s.at(74.0F).despawn("no", 8.0F);
                    s.at(76.0F).turn(PILOT, SceneKit.pilotYaw(PILOT_POS, ANALOG_POS), 0.0F, 0.0F, 14.0F);
                    s.at(76.0F).look(PILOT, SceneKit.headPitch(PILOT_POS, ANALOG_POS), 14.0F);
                    s.at(90.0F).keysAt("bind_ok", overHead, 0.0F, KeyToken.use());
                    s.at(96.0F).highlight(ANALOG_POS.add(-0.32F, 0.0F, -0.32F), ANALOG_POS.add(0.32F, 0.3F, 0.32F), 26.0F);
                    s.at(98.0F).callout("match", onAnalog, Cue.Side.BELOW, 0.0F);
                })
                .step("creative", 130.0F, s -> {
                    s.at(0.0F).equip(PILOT, Cue.Slot.HAND, SceneKit.REMOTE);
                    s.at(0.0F).turn(PILOT, 0.0F, 0.0F, 0.0F, 12.0F);
                    s.at(0.0F).look(PILOT, 0.0F, 12.0F);
                    ScenePos middle = new ScenePos(-0.4F, 1.8F, 1.0F);
                    s.at(6.0F).spawn("creative_goggles", new ActorSpec.Item(GOGGLES_CREATIVE, 1.8F, true), middle, 0.0F, Cue.Appear.POP, 10.0F);
                    s.at(10.0F).equip(PILOT, Cue.Slot.HEAD, GOGGLES_CREATIVE);
                    s.at(12.0F).callout("creative", Anchor.on(PILOT, 0.0F, 1.95F, 0.0F), Cue.Side.ABOVE, 0.0F);
                    s.at(16.0F).highlight(ANALOG_POS.add(-0.32F, 0.0F, -0.32F), ANALOG_POS.add(0.32F, 0.3F, 0.32F), 30.0F);
                    s.at(16.0F).highlight(DIGITAL_POS.add(-0.32F, 0.0F, -0.32F), DIGITAL_POS.add(0.32F, 0.3F, 0.32F), 30.0F);
                    s.at(50.0F).callout("creative_only", onDigital, Cue.Side.BELOW, 0.0F);
                    s.at(60.0F).move("creative_goggles", 30.0F, Ease.IN_OUT, middle.add(0.0F, 0.15F, 0.0F));
                    s.at(70.0F).keys("configurator", 0.0F, KeyToken.mapping(SceneKit.KEY_CONFIGURATOR));
                })
                .build();
    }
}
