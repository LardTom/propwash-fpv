package de.lardtom.propwash.guide.scenes;

import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.BlockSpec;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;

import static de.lardtom.propwash.guide.scenes.SceneKit.GRASS;
import static de.lardtom.propwash.guide.scenes.SceneKit.REMOTE;

public final class RxBlockScene {
    public static final String ID = "rx_block";
    public static final String RX_BLOCK = SceneKit.NAMESPACE + "rx_block";
    public static final String CREATIVE_RX_BLOCK = SceneKit.NAMESPACE + "creative_rx_block";
    public static final String PILOT = "pilot";
    public static final int RX_X = 0;
    public static final int LAMP_X = 3;
    public static final ScenePos PILOT_POS = new ScenePos(-1.3F, 1.0F, 1.7F);
    public static final ScenePos AWAY = new ScenePos(-3.6F, 1.0F, 3.9F);

    private static final String LAMP = "minecraft:redstone_lamp";
    private static final String WIRE = "minecraft:redstone_wire";
    private static final ScenePos OVERVIEW = new ScenePos(1.0F, 1.4F, 0.9F);

    private RxBlockScene() {
    }

    public static BlockSpec rx(String link, int power) {
        return BlockSpec.of(RX_BLOCK).with("link", link).with("power", Integer.toString(power)).with("facing", "east").with("output", "front");
    }

    private static BlockSpec wire(int power) {
        return BlockSpec.of(WIRE).with("east", "side").with("west", "side").with("power", Integer.toString(power));
    }

    private static BlockSpec lamp(boolean lit) {
        return BlockSpec.of(LAMP).with("lit", Boolean.toString(lit));
    }

    private static void signal(SceneBuilder.StepBuilder s, float at, String link, int power) {
        s.at(at).block(RX_X, 1, 0, rx(link, power));
        for (int x = RX_X + 1; x < LAMP_X; x++) {
            s.at(at).block(x, 1, 0, wire(Math.max(0, power - (x - RX_X - 1))));
        }
        s.at(at).block(LAMP_X, 1, 0, lamp(power > 0));
    }

    public static Scene create() {
        float facing = SceneKit.pilotYaw(PILOT_POS, new ScenePos(RX_X + 0.5F, 1.0F, 0.5F));
        float down = SceneKit.headPitch(PILOT_POS, new ScenePos(RX_X + 0.5F, 1.2F, 0.5F));
        Anchor onRx = Anchor.at(RX_X + 0.5F, 1.45F, 0.5F);
        Anchor onLamp = Anchor.at(LAMP_X + 0.5F, 2.05F, 0.5F);
        Anchor overHead = Anchor.on(PILOT, 0.0F, 2.15F, 0.0F);
        Anchor atPilot = Anchor.on(PILOT, 0.0F, 1.1F, 0.0F);
        return SceneBuilder.scene(ID)
                .icon(RX_BLOCK)
                .items(RX_BLOCK, CREATIVE_RX_BLOCK)
                .view(2.8F)
                .camera(OVERVIEW, 30.0F, 24.0F, 1.1F)
                .step("place", 110.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -4, -2, 4, 4, GRASS, GRASS, 1.0F, 10.0F);
                    s.at(16.0F).drop(RX_X, 1, 0, rx("none", 0), 8.0F);
                    for (int x = RX_X + 1; x < LAMP_X; x++) {
                        s.at(22.0F + 3.0F * x).drop(x, 1, 0, wire(0), 6.0F);
                    }
                    s.at(30.0F).drop(LAMP_X, 1, 0, lamp(false), 8.0F);
                    s.at(36.0F).callout("place", onRx, Cue.Side.LEFT, 0.0F);
                    s.at(60.0F).callout("output", onLamp, Cue.Side.RIGHT, 0.0F);
                })
                .step("bind", 130.0F, s -> {
                    s.at(0.0F).spawn(PILOT, new ActorSpec.Pilot("", REMOTE), PILOT_POS, facing, Cue.Appear.POP, 10.0F);
                    s.at(4.0F).look(PILOT, down, 12.0F);
                    s.at(16.0F).keysAt("bind", overHead, 34.0F, KeyToken.use());
                    signal(s, 42.0F, "lost", 0);
                    s.at(44.0F).callout("bind", onRx, Cue.Side.LEFT, 40.0F);
                    s.at(88.0F).callout("owner", atPilot, Cue.Side.LEFT, 0.0F);
                })
                .step("learn", 150.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW.add(-0.4F, 0.1F, 0.4F), 24.0F, 20.0F, 1.25F, 20.0F);
                    s.at(8.0F).callout("learn", atPilot, Cue.Side.LEFT, 56.0F);
                    signal(s, 40.0F, "linked", 0);
                    s.at(66.0F).callout("endpoints", onRx, Cue.Side.RIGHT, 0.0F);
                })
                .step("signal", 160.0F, s -> {
                    s.at(0.0F).camera(OVERVIEW, 30.0F, 24.0F, 1.1F, 20.0F);
                    signal(s, 10.0F, "linked", 8);
                    s.at(12.0F).callout("half", onRx, Cue.Side.LEFT, 56.0F);
                    signal(s, 72.0F, "linked", 15);
                    s.at(74.0F).callout("full", onLamp, Cue.Side.RIGHT, 0.0F);
                })
                .step("range", 210.0F, s -> {
                    s.at(4.0F).callout("range", atPilot, Cue.Side.LEFT, 50.0F);
                    s.at(58.0F).move(PILOT, 40.0F, Ease.IN_OUT, AWAY);
                    signal(s, 92.0F, "lost", 15);
                    s.at(96.0F).callout("hold", onRx, Cue.Side.RIGHT, 56.0F);
                    s.at(154.0F).callout("creative", onRx, Cue.Side.RIGHT, 0.0F);
                })
                .build();
    }
}
