package de.lardtom.propwash.guide.scenes;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.BlockSpec;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.DronePart;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.ScenePos;

public final class DroneBuildScene {
    public static final String ID = "drone_build";
    public static final String WORKBENCH = "propwash:drone_workbench";
    public static final String CHARGER = "propwash:battery_charger";
    public static final String DRONE_ITEM = "propwash:drone";
    public static final String REMOTE = "propwash:remote";
    public static final String GOGGLES = "propwash:goggles_analog";
    public static final DroneBuild BUILD = DroneBuild.FREESTYLE;
    public static final String DRONE = "drone";
    public static final String PILOT = "pilot";
    public static final String HEAVY_PACK = SceneKit.part(BatteryType.B6S_4000_LION);
    public static final String WEAK_PACK = SceneKit.part(BatteryType.B4S_650);

    private static final String HEAVY = "heavy_pack";
    private static final String WEAK = "weak_pack";
    private static final String NO = "no";

    private static final BlockSpec RIM = BlockSpec.of("minecraft:polished_deepslate");
    private static final BlockSpec PLATE = SceneKit.GRAY_CONCRETE;
    private static final BlockSpec BENCH = BlockSpec.of(WORKBENCH);
    private static final BlockSpec CHARGING = SceneKit.charger("south", "charging");
    private static final ScenePos BENCH_TOP = new ScenePos(0.5F, 2.0F, 0.5F);
    public static final ScenePos LANDING = new ScenePos(1.4F, 1.0F, 2.3F);
    public static final float HOVER_HEIGHT = 0.9F;
    private static final ScenePos PILOT_POS = new ScenePos(-1.3F, 1.0F, 2.2F);
    public static final int CHARGER_X = 1;
    public static final int CHARGER_Z = -1;
    public static final float CHARGER_DROP = 46.0F;
    public static final float MOTORS_LANDED = 42.0F;
    public static final float PROPS_START = 50.0F;
    public static final float PROPS_LANDED = 88.0F;
    public static final float DRONE_SCALE = 2.0F;
    private static final float BUILD_ZOOM = 2.6F;
    public static final float RULE_AT = 104.0F;

    private DroneBuildScene() {
    }

    private static Anchor onDrone(float dx, float dy, float dz) {
        float k = DRONE_SCALE / ActorSpec.Drone.STAGE_SCALE;
        return Anchor.on(DRONE, dx * k, dy * k, dz * k);
    }

    public static Scene create() {
        return SceneBuilder.scene(ID)
                .icon(WORKBENCH)
                .items(DRONE_ITEM)
                .anyPart()
                .view(3.3F)
                .camera(new ScenePos(0.5F, 1.1F, 0.5F), 38.0F, 30.0F, 1.35F)
                .step("bench", 90.0F, s -> {
                    for (int x = -2; x <= 2; x++) {
                        for (int z = -2; z <= 2; z++) {
                            boolean rim = Math.abs(x) == 2 || Math.abs(z) == 2;
                            s.at((x + 2 + z + 2) * 1.5F).drop(x, 0, z, rim ? RIM : PLATE, 10.0F);
                        }
                    }
                    s.at(22.0F).drop(0, 1, 0, BENCH, 10.0F);
                    s.at(36.0F).highlightBlock(0, 1, 0, 0.0F);
                    s.at(38.0F).callout("bench", Anchor.at(1.0F, 1.95F, 1.0F), Cue.Side.RIGHT, 0.0F);
                    s.at(54.0F).keys("open", 0.0F, KeyToken.use());
                })
                .step("frame", 100.0F, s -> {
                    s.at(0.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, false, DRONE_SCALE), BENCH_TOP, 0.0F, Cue.Appear.INSTANT, 0.0F);
                    s.at(0.0F).camera(new ScenePos(0.5F, 2.0F, 0.5F), 30.0F, 44.0F, BUILD_ZOOM, 26.0F);
                    s.at(6.0F).attach(DRONE, DronePart.FRAME, new ScenePos(-1.1F, 0.6F, 0.4F), 22.0F, 0.0F);
                    s.at(26.0F).callout("frame", onDrone(-0.05F, 0.03F, 0.05F), Cue.Side.LEFT, 30.0F);
                    s.at(50.0F).attach(DRONE, DronePart.STACK, new ScenePos(0.0F, 0.7F, 0.0F), 16.0F, 0.0F);
                    s.at(66.0F).callout("stack", onDrone(0.0F, 0.08F, 0.0F), Cue.Side.RIGHT, 0.0F);
                })
                .step("motors", 136.0F, s -> {
                    s.at(0.0F).attach(DRONE, DronePart.MOTORS, new ScenePos(0.8F, 0.4F, 0.8F), 40.0F, 6.0F);
                    s.at(MOTORS_LANDED).callout("motors", onDrone(0.16F, 0.05F, 0.16F), Cue.Side.RIGHT, 34.0F);
                    s.at(PROPS_START).attach(DRONE, DronePart.PROPS, new ScenePos(0.0F, 0.6F, 0.0F), 36.0F, 5.0F);
                    s.at(PROPS_LANDED).callout("props", onDrone(-0.18F, 0.07F, 0.18F), Cue.Side.LEFT, 0.0F);
                    s.at(30.0F).camera(new ScenePos(0.5F, 2.0F, 0.5F), 52.0F, 46.0F, BUILD_ZOOM, 60.0F);
                })
                .step("video", 100.0F, s -> {
                    s.at(0.0F).attach(DRONE, DronePart.VIDEO, new ScenePos(0.0F, 0.3F, -0.9F), 20.0F, 0.0F);
                    s.at(16.0F).callout("video", onDrone(0.0F, 0.05F, -0.14F), Cue.Side.RIGHT, 36.0F);
                    s.at(0.0F).camera(new ScenePos(0.5F, 2.0F, 0.5F), 14.0F, 40.0F, BUILD_ZOOM, 24.0F);
                    s.at(46.0F).attach(DRONE, DronePart.BATTERY, new ScenePos(0.0F, 0.8F, 0.0F), 18.0F, 0.0F);
                    s.at(CHARGER_DROP).drop(CHARGER_X, 1, CHARGER_Z, CHARGING, 10.0F);
                    s.at(62.0F).callout("battery", onDrone(0.0F, 0.1F, 0.0F), Cue.Side.LEFT, 0.0F);
                    s.at(62.0F).callout("charger", Anchor.at(CHARGER_X + 0.5F, 1.55F, CHARGER_Z + 0.5F), Cue.Side.ABOVE, 0.0F);
                    s.at(62.0F).highlightBlock(CHARGER_X, 1, CHARGER_Z, 0.0F);
                    s.at(46.0F).camera(new ScenePos(0.9F, 1.8F, 0.2F), 30.0F, 34.0F, 1.7F, 20.0F);
                })
                .step("stats", 170.0F, s -> {
                    ScenePos right = new ScenePos(0.866F, 0.0F, -0.5F);
                    ScenePos heavy = BENCH_TOP.add(right.scale(0.85F)).add(0.0F, 0.5F, 0.0F);
                    ScenePos weak = BENCH_TOP.add(right.scale(-0.85F)).add(0.0F, 0.5F, 0.0F);
                    s.at(0.0F).camera(new ScenePos(0.5F, 2.3F, 0.5F), 30.0F, 24.0F, 2.1F, 22.0F);
                    s.at(4.0F).callout("stats", onDrone(0.0F, 0.14F, 0.0F), Cue.Side.ABOVE, 40.0F);
                    s.at(46.0F).spawn(HEAVY, new ActorSpec.Item(HEAVY_PACK, 1.3F, true), heavy, 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(52.0F).spawn(NO, new ActorSpec.Item(SceneKit.BARRIER, 0.8F, false), heavy.add(0.0F, 0.22F, 0.0F), 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(52.0F).callout("error", Anchor.on(HEAVY, 0.0F, 0.1F, 0.0F), Cue.Side.RIGHT, RULE_AT - 52.0F);
                    s.at(80.0F).spawn(WEAK, new ActorSpec.Item(WEAK_PACK, 1.3F, true), weak, 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(84.0F).callout("warning", Anchor.on(WEAK, 0.0F, 0.1F, 0.0F), Cue.Side.LEFT, 0.0F);
                    s.at(RULE_AT).despawn(NO, 8.0F);
                    s.at(RULE_AT).callout("rule", Anchor.on(HEAVY, 0.0F, 0.1F, 0.0F), Cue.Side.RIGHT, 0.0F);
                    s.at(158.0F).despawn(HEAVY, 10.0F);
                    s.at(158.0F).despawn(WEAK, 10.0F);
                })
                .step("done", 110.0F, s -> {
                    s.at(0.0F).camera(new ScenePos(0.5F, 2.0F, 0.5F), 30.0F, 34.0F, BUILD_ZOOM, 20.0F);
                    s.at(20.0F).camera(new ScenePos(0.5F, 2.0F, 0.5F), 110.0F, 34.0F, BUILD_ZOOM, 90.0F);
                    s.at(4.0F).highlight(new ScenePos(0.02F, 2.0F, 0.02F), new ScenePos(0.98F, 2.34F, 0.98F), 50.0F);
                    s.at(6.0F).callout("done", onDrone(0.0F, 0.14F, 0.0F), Cue.Side.ABOVE, 0.0F);
                    s.at(56.0F).keys("take", 0.0F, KeyToken.mouse(KeyToken.MOUSE_LEFT));
                })
                .step("place", 120.0F, s -> {
                    s.at(0.0F).camera(new ScenePos(0.9F, 1.5F, 0.9F), 38.0F, 26.0F, 1.25F, 26.0F);
                    s.at(0.0F).spawn(PILOT, new ActorSpec.Pilot(GOGGLES, DRONE_ITEM), PILOT_POS, SceneKit.pilotYaw(PILOT_POS, LANDING), Cue.Appear.POP, 10.0F);
                    s.at(12.0F).move(DRONE, 24.0F, Ease.IN_OUT, new ScenePos(0.9F, 2.45F, 1.4F), LANDING);
                    s.at(12.0F).equip(PILOT, Cue.Slot.HAND, "");
                    s.at(12.0F).keys("place", 36.0F, KeyToken.use());
                    s.at(52.0F).equip(PILOT, Cue.Slot.HAND, REMOTE);
                    s.at(52.0F).look(PILOT, 28.0F, 12.0F);
                    s.at(56.0F).callout("bind", onDrone(0.0F, 0.08F, 0.0F), Cue.Side.LEFT, 0.0F);
                    s.at(60.0F).keysAt("bind", Anchor.on(PILOT, 0.0F, 2.25F, 0.0F), 0.0F, KeyToken.use());
                })
                .step("fly", 170.0F, s -> {
                    ScenePos hover = LANDING.add(0.0F, HOVER_HEIGHT, 0.0F);
                    ScenePos middle = new ScenePos((PILOT_POS.x() + LANDING.x()) * 0.5F, 1.75F, (PILOT_POS.z() + LANDING.z()) * 0.5F);
                    s.at(0.0F).camera(middle, 8.0F, 16.0F, 1.45F, 24.0F);
                    s.at(0.0F).look(PILOT, 4.0F, 16.0F);
                    ScenePos hands = SceneKit.handsOffset(PILOT_POS, LANDING);
                    s.at(2.0F).callout("fly", Anchor.on(PILOT, hands.x(), hands.y(), hands.z()), Cue.Side.LEFT, 44.0F);
                    s.at(2.0F).keys("fly", 40.0F, KeyToken.use());
                    s.at(4.0F).arm(DRONE, true);
                    s.at(4.0F).throttle(DRONE, 0.08F, 6.0F);
                    s.at(12.0F).throttle(DRONE, 0.6F, 8.0F);
                    s.at(14.0F).move(DRONE, 24.0F, Ease.IN_OUT, hover);
                    s.at(14.0F).look(PILOT, -12.0F, 24.0F);
                    s.at(50.0F).move(DRONE, 46.0F, Ease.IN_OUT, LANDING.add(0.7F, 1.3F, -0.9F), LANDING.add(-0.5F, 1.4F, -1.3F), hover);
                    s.at(50.0F).turn(DRONE, 120.0F, 6.0F, 10.0F, 22.0F);
                    s.at(72.0F).turn(DRONE, 0.0F, 0.0F, 0.0F, 24.0F);
                    s.at(100.0F).throttle(DRONE, 0.3F, 12.0F);
                    s.at(100.0F).move(DRONE, 26.0F, Ease.IN_OUT, LANDING);
                    s.at(100.0F).look(PILOT, 8.0F, 26.0F);
                    s.at(126.0F).throttle(DRONE, 0.0F, 6.0F);
                    s.at(132.0F).arm(DRONE, false);
                    s.at(124.0F).callout("exit", onDrone(0.0F, 0.12F, 0.0F), Cue.Side.RIGHT, 0.0F);
                    s.at(134.0F).keys("exit", 0.0F, KeyToken.mapping("key.propwash.drone_exit"));
                })
                .build();
    }
}
