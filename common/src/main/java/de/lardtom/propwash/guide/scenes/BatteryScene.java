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

import java.util.List;

import static de.lardtom.propwash.guide.scenes.SceneKit.BARRIER;
import static de.lardtom.propwash.guide.scenes.SceneKit.CHARGER;
import static de.lardtom.propwash.guide.scenes.SceneKit.POLISHED_DEEPSLATE;
import static de.lardtom.propwash.guide.scenes.SceneKit.REMOTE;
import static de.lardtom.propwash.guide.scenes.SceneKit.GRAY_CONCRETE;

public final class BatteryScene {
    public static final String ID = "battery";
    public static final String DRONE = "drone";
    public static final String PILOT = "pilot";
    public static final DroneBuild BUILD = DroneBuild.FREESTYLE;
    public static final String PACK = SceneKit.part(BatteryType.B6S_1300);
    public static final String WEAK_PACK = SceneKit.part(BatteryType.B4S_650);
    public static final String HEAVY_PACK = SceneKit.part(BatteryType.B6S_4000_LION);
    public static final String HV_PACK = SceneKit.part(BatteryType.B1S_300_HV);
    public static final List<BatteryType> CHARGED = List.of(BatteryType.B6S_1300, BatteryType.B6S_4000_LION, BatteryType.B1S_300_HV);
    public static final ScenePos DRONE_POS = new ScenePos(0.4F, 1.0F, 0.5F);
    public static final ScenePos PILOT_POS = new ScenePos(-1.1F, 1.0F, 1.7F);
    public static final int CHARGER_X = 1;
    public static final int CHARGER_Z = -1;
    public static final String HEAVY = "fit_heavy";
    public static final String WEAK = "fit_weak";
    public static final String NO = "no";
    public static final float HEAVY_AT = 46.0F;
    public static final float PUSH_AT = 64.0F;
    public static final float REFUSED_AT = 84.0F;
    public static final float BOUNCE_AT = 124.0F;
    public static final float RULE_AT = 150.0F;
    public static final float FIT_LENGTH = 210.0F;
    public static final float LION_IN = 70.0F;
    public static final float HV_IN = 82.0F;
    public static final float CHEMISTRY_AT = 86.0F;
    public static final ScenePos FRAME_MIN = DRONE_POS.add(-0.21F, -0.01F, -0.21F);
    public static final ScenePos FRAME_MAX = DRONE_POS.add(0.21F, 0.11F, 0.21F);
    public static final List<String> SHOWN = List.of("show_lipo", "show_lion", "show_hv");

    private static final ScenePos CHARGER_TOP = new ScenePos(1.5F, 1.8F, -0.5F);
    private static final ScenePos OVERVIEW = new ScenePos(0.3F, 1.6F, 0.4F);
    private static final ScenePos LIFTED = new ScenePos(0.0F, 0.75F, 0.0F);
    private static final BlockSpec REDSTONE = BlockSpec.of("minecraft:redstone_block");

    private BatteryScene() {
    }

    public static Scene create() {
        Anchor onDrone = Anchor.on(DRONE, 0.0F, 0.16F, 0.0F);
        Anchor overHead = Anchor.on(PILOT, 0.0F, 2.15F, 0.0F);
        float toDrone = SceneKit.pilotYaw(PILOT_POS, DRONE_POS);
        float toCharger = SceneKit.pilotYaw(PILOT_POS, CHARGER_TOP);
        float down = SceneKit.headPitch(PILOT_POS, DRONE_POS);
        ScenePos right = new ScenePos(0.77F, 0.0F, -0.64F);
        return SceneBuilder.scene(ID)
                .icon(PACK)
                .items(SceneKit.batteries())
                .items(CHARGER, SceneKit.DRONE_ITEM)
                .view(2.3F)
                .camera(OVERVIEW, 40.0F, 28.0F, 1.0F)
                .step("land", 110.0F, s -> {
                    SceneKit.ground(s.at(0.0F), -2, -2, 2, 2, GRAY_CONCRETE, POLISHED_DEEPSLATE, 1.5F, 10.0F);
                    s.at(18.0F).drop(CHARGER_X, 1, CHARGER_Z, SceneKit.charger("south", "idle"), 10.0F);
                    s.at(18.0F).spawn(PILOT, new ActorSpec.Pilot(SceneKit.GOGGLES_DIGITAL, REMOTE), PILOT_POS, toDrone, Cue.Appear.POP, 10.0F);
                    s.at(16.0F).spawn(DRONE, new ActorSpec.Drone(BUILD, true), DRONE_POS.add(0.0F, 1.4F, 0.0F), 40.0F, Cue.Appear.INSTANT, 0.0F);
                    s.at(16.0F).arm(DRONE, true);
                    s.at(16.0F).throttle(DRONE, 0.45F, 0.0F);
                    s.at(20.0F).move(DRONE, 30.0F, Ease.IN_OUT, DRONE_POS);
                    s.at(20.0F).look(PILOT, down, 30.0F);
                    s.at(36.0F).throttle(DRONE, 0.1F, 14.0F);
                    s.at(50.0F).arm(DRONE, false);
                    s.at(30.0F).callout("empty", onDrone, Cue.Side.RIGHT, 0.0F);
                    s.at(54.0F).keys("stop", 0.0F, KeyToken.mapping(SceneKit.KEY_EXIT));
                })
                .step("menu", 120.0F, s -> {
                    s.at(0.0F).equip(PILOT, Cue.Slot.HAND, "");
                    s.at(0.0F).crouch(PILOT, true);
                    s.at(6.0F).keysAt("menu", overHead, 0.0F, KeyToken.sneak(), KeyToken.mouse(KeyToken.MOUSE_RIGHT));
                    s.at(10.0F).callout("menu", onDrone, Cue.Side.RIGHT, 48.0F);
                    s.at(34.0F).detach(DRONE, DronePart.BATTERY, LIFTED, 16.0F);
                    s.at(50.0F).spawn("old_pack", new ActorSpec.Item(PACK, 1.2F, false), DRONE_POS.add(LIFTED).add(0.0F, 0.1F, 0.0F), 40.0F,
                            Cue.Appear.INSTANT, 0.0F);
                    s.at(52.0F).move("old_pack", 18.0F, Ease.IN, PILOT_POS.add(0.0F, 1.2F, 0.0F));
                    s.at(68.0F).despawn("old_pack", 4.0F);
                    s.at(62.0F).callout("none", onDrone, Cue.Side.RIGHT, 0.0F);
                })
                .step("swap", 110.0F, s -> {
                    s.at(0.0F).equip(PILOT, Cue.Slot.HAND, PACK);
                    s.at(4.0F).keysAt("swap", overHead, 0.0F, KeyToken.sneak(), KeyToken.use());
                    s.at(8.0F).callout("quick", Anchor.on(PILOT, 0.0F, 1.1F, 0.0F), Cue.Side.LEFT, 0.0F);
                    s.at(26.0F).equip(PILOT, Cue.Slot.HAND, "");
                    s.at(26.0F).attach(DRONE, DronePart.BATTERY, LIFTED, 18.0F, 0.0F);
                    s.at(48.0F).callout("charge", onDrone, Cue.Side.RIGHT, 0.0F);
                })
                .step("fit", FIT_LENGTH, s -> {
                    ScenePos weakAt = DRONE_POS.add(right.scale(-0.55F)).add(0.0F, 0.42F, 0.0F);
                    ScenePos heavyAt = DRONE_POS.add(right.scale(0.55F)).add(0.0F, 0.42F, 0.0F);
                    ScenePos pressed = DRONE_POS.add(right.scale(0.04F)).add(0.0F, 0.5F, 0.0F);
                    s.at(0.0F).crouch(PILOT, false);
                    s.at(0.0F).remove(CHARGER_X, 1, CHARGER_Z, 8.0F);
                    s.at(0.0F).look(PILOT, SceneKit.headPitch(PILOT_POS, DRONE_POS.add(0.0F, 0.3F, 0.0F)), 16.0F);
                    s.at(0.0F).camera(DRONE_POS.add(right.scale(0.3F)).add(0.0F, 0.3F, 0.0F), 40.0F, 16.0F, 3.0F, 20.0F);
                    s.at(8.0F).callout("ok", Anchor.on(DRONE, 0.0F, 0.08F, 0.0F), Cue.Side.LEFT, 26.0F);
                    s.at(26.0F).spawn(WEAK, new ActorSpec.Item(WEAK_PACK, 0.9F, true), weakAt, 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(36.0F).callout("weak", Anchor.on(WEAK, 0.0F, 0.08F, 0.0F), Cue.Side.LEFT, 0.0F);
                    s.at(HEAVY_AT).spawn(HEAVY, new ActorSpec.Item(HEAVY_PACK, 0.9F, false), heavyAt, 0.0F, Cue.Appear.POP, 8.0F);
                    s.at(HEAVY_AT + 4.0F).callout("heavy", Anchor.on(HEAVY, 0.0F, 0.08F, 0.0F), Cue.Side.RIGHT, REFUSED_AT - HEAVY_AT - 4.0F);
                    s.at(PUSH_AT).move(HEAVY, REFUSED_AT - PUSH_AT, Ease.IN_OUT, pressed);
                    s.at(REFUSED_AT).highlight(FRAME_MIN, FRAME_MAX, RULE_AT - REFUSED_AT);
                    s.at(REFUSED_AT).spawn(NO, new ActorSpec.Item(BARRIER, 0.45F, false), DRONE_POS.add(0.0F, 0.24F, 0.0F), 40.0F,
                            Cue.Appear.POP, 6.0F);
                    s.at(REFUSED_AT + 2.0F).callout("frame", Anchor.on(DRONE, 0.0F, 0.04F, 0.0F), Cue.Side.RIGHT, RULE_AT - REFUSED_AT - 2.0F);
                    s.at(BOUNCE_AT).move(HEAVY, 18.0F, Ease.OUT, heavyAt);
                    s.at(RULE_AT).despawn(NO, 8.0F);
                    s.at(RULE_AT).callout("rule", Anchor.on(HEAVY, 0.0F, 0.08F, 0.0F), Cue.Side.RIGHT, 0.0F);
                    s.at(FIT_LENGTH - 14.0F).despawn(WEAK, 12.0F);
                    s.at(FIT_LENGTH - 14.0F).despawn(HEAVY, 12.0F);
                })
                .step("charger", 176.0F, s -> {
                    s.at(0.0F).drop(CHARGER_X, 1, CHARGER_Z, SceneKit.charger("south", "idle"), 10.0F);
                    s.at(0.0F).camera(new ScenePos(0.9F, 1.7F, 0.0F), 34.0F, 24.0F, 1.3F, 22.0F);
                    s.at(0.0F).turn(PILOT, toCharger, 0.0F, 0.0F, 16.0F);
                    s.at(0.0F).look(PILOT, SceneKit.headPitch(PILOT_POS, CHARGER_TOP), 16.0F);
                    s.at(4.0F).equip(PILOT, Cue.Slot.HAND, PACK);
                    s.at(12.0F).keysAt("insert", Anchor.at(CHARGER_TOP.add(0.0F, 0.2F, 0.0F)), 28.0F, KeyToken.use());
                    insert(s, "empty_pack", PACK, 18.0F);
                    s.at(40.0F).block(CHARGER_X, 1, CHARGER_Z, charger("charging", 1));
                    s.at(40.0F).highlightBlock(CHARGER_X, 1, CHARGER_Z, 40.0F);
                    s.at(42.0F).callout("charging", Anchor.at(CHARGER_TOP), Cue.Side.ABOVE, CHEMISTRY_AT - 46.0F);
                    s.at(44.0F).equip(PILOT, Cue.Slot.HAND, HEAVY_PACK);
                    insert(s, "lion_pack", HEAVY_PACK, LION_IN - 20.0F);
                    s.at(LION_IN).block(CHARGER_X, 1, CHARGER_Z, charger("charging", 2));
                    s.at(56.0F).equip(PILOT, Cue.Slot.HAND, HV_PACK);
                    insert(s, "hv_pack", HV_PACK, HV_IN - 20.0F);
                    s.at(HV_IN).block(CHARGER_X, 1, CHARGER_Z, charger("charging", 3));
                    for (int i = 0; i < SHOWN.size(); i++) {
                        ScenePos shown = CHARGER_TOP.add(-0.5F + 0.5F * i, 0.45F, 0.0F);
                        s.at(CHEMISTRY_AT + 2.0F * i).spawn(SHOWN.get(i), new ActorSpec.Item(SceneKit.part(CHARGED.get(i)), 1.1F, false), shown, 0.0F,
                                Cue.Appear.POP, 6.0F);
                        s.at(CHEMISTRY_AT + 46.0F).despawn(SHOWN.get(i), 8.0F);
                    }
                    s.at(CHEMISTRY_AT).highlightBlock(CHARGER_X, 1, CHARGER_Z, 46.0F);
                    s.at(CHEMISTRY_AT + 4.0F).callout("chemistry", Anchor.on(SHOWN.get(1), 0.0F, 0.12F, 0.0F), Cue.Side.ABOVE, 42.0F);
                    s.at(104.0F).pop(CHARGER_X + 1, 1, CHARGER_Z, REDSTONE, 10.0F);
                    s.at(110.0F).callout("redstone", Anchor.at(new ScenePos(3.0F, 1.5F, -1.0F)), Cue.Side.RIGHT, 0.0F);
                    s.at(136.0F).block(CHARGER_X, 1, CHARGER_Z, charger("done", 3));
                    s.at(136.0F).highlightBlock(CHARGER_X, 1, CHARGER_Z, 0.0F);
                    s.at(138.0F).callout("done", Anchor.at(CHARGER_TOP), Cue.Side.ABOVE, 0.0F);
                })
                .build();
    }

    private static void insert(SceneBuilder.StepBuilder s, String actor, String item, float at) {
        s.at(at).equip(PILOT, Cue.Slot.HAND, "");
        s.at(at).spawn(actor, new ActorSpec.Item(item, 1.2F, false), PILOT_POS.add(0.0F, 1.3F, 0.0F), 0.0F, Cue.Appear.INSTANT, 0.0F);
        s.at(at).move(actor, 20.0F, Ease.IN_OUT, new ScenePos(0.3F, 2.7F, 0.4F), CHARGER_TOP.add(0.0F, 0.15F, 0.0F));
        s.at(at + 20.0F).despawn(actor, 4.0F);
    }

    public static BlockSpec charger(String state, int packs) {
        String bay = "done".equals(state) ? "full" : "charging";
        BlockSpec spec = BlockSpec.of(CHARGER).with("facing", "south").with("state", state);
        for (int i = 0; i < packs; i++) {
            spec = spec.with("bay_" + (i + 1) % 4, bay);
        }
        return spec;
    }
}
