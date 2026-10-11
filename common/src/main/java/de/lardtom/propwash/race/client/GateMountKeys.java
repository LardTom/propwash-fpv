package de.lardtom.propwash.race.client;

import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.client.InputKeys;
import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.platform.client.ClientServices;
import de.lardtom.propwash.race.CourseElement;
import de.lardtom.propwash.race.CourseElementItem;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.FreeformGateItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.GateItem;
import de.lardtom.propwash.race.GateMount;
import de.lardtom.propwash.race.GateMounting;
import de.lardtom.propwash.race.GatePlacement;
import de.lardtom.propwash.race.GateTilt;
import de.lardtom.propwash.race.RacePayloads;
import de.lardtom.propwash.registry.client.ClientRegistrar;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

public final class GateMountKeys {
    public static final String NAME = "gate_rotate";
    public static final String MAPPING = GateItem.ROTATE_KEY;
    public static final int DEFAULT_KEY = KeyDefaults.GATE_ROTATE.key();

    private static @Nullable KeyMapping rotate;
    private static GatePlacement.Preference preference = GatePlacement.Preference.DEFAULT;
    private static GateMount.Surface lastSurface = GateMount.Surface.FLOOR;

    private GateMountKeys() {
    }

    static void register() {
        if (rotate == null) {
            rotate = ClientRegistrar.keyMapping(NAME, DEFAULT_KEY);
        }
        GateMounting.setClientPreference(player -> preference);
    }

    public static @Nullable KeyMapping mapping() {
        return rotate;
    }

    public static Component keyName() {
        return Component.keybind(MAPPING);
    }

    public static GatePlacement.Preference preference() {
        return preference;
    }

    public static void setPreference(GatePlacement.Preference next) {
        preference = next;
        ClientNet.sendToServer(new RacePayloads.GatePreferenceC2S(next));
    }

    public static GateMount.Surface lastSurface() {
        return lastSurface;
    }

    static void reset() {
        preference = GatePlacement.Preference.DEFAULT;
        lastSurface = GateMount.Surface.FLOOR;
    }

    public static @Nullable InteractionHand gateHand(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof GateItem) {
            return InteractionHand.MAIN_HAND;
        }
        return player.getOffhandItem().getItem() instanceof GateItem ? InteractionHand.OFF_HAND : null;
    }

    public static @Nullable CourseElement heldElement(LocalPlayer player) {
        InteractionHand hand = gateHand(player);
        return hand != null && player.getItemInHand(hand).getItem() instanceof CourseElementItem item ? item.kind() : null;
    }

    public static boolean holdingFreeform(LocalPlayer player) {
        InteractionHand hand = gateHand(player);
        return hand != null && player.getItemInHand(hand).getItem() instanceof FreeformGateItem;
    }

    public static boolean holdingCourseTool(LocalPlayer player) {
        return CourseToolItem.isCourseTool(player.getMainHandItem()) || CourseToolItem.isCourseTool(player.getOffhandItem());
    }

    static void tick(Minecraft minecraft) {
        KeyMapping key = rotate;
        if (key == null) {
            return;
        }
        boolean pressed = false;
        while (key.consumeClick()) {
            pressed = true;
        }
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        GateEditor editor = GateEditor.current();
        if (editor != null) {
            if (pressed && minecraft.gui.screen() == null) {
                editor.rotateKey(minecraft.options.keyShift.isDown() || player.isShiftKeyDown());
            }
            return;
        }
        HitResult hit = minecraft.hitResult;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            GatePlacement.Click click = GatePreview.click(player, block);
            lastSurface = click != null ? click.surface() : GatePlacement.surface(block.getDirection().getStepY());
        }
        if (!pressed) {
            return;
        }
        GateEntity gate = hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof GateEntity found ? found : null;
        boolean sneaking = minecraft.options.keyShift.isDown() || player.isShiftKeyDown();
        GateKeyRoute.Context context = new GateKeyRoute.Context(minecraft.gui.screen() != null, PilotSession.INSTANCE.isActive(), hands(player),
                gate != null, sneaking, sharedWithFlightMode());
        switch (GateKeyRoute.route(context)) {
            case ROTATE_GATE, NEXT_MOUNT_GATE -> {
                if (gate != null) {
                    ClientNet.sendToServer(new RacePayloads.GateRotateC2S(gate.getId(), sneaking, step()));
                }
            }
            case NEED_TOOL -> RaceHud.message(Component.translatable("propwash.race.mount.need_tool"), RaceHud.WHITE);
            case TURN, NEXT_MOUNT -> {
                if (holdingFreeform(player)) {
                    setPreference(preference.nextVariant(CourseElement.FREEFORM));
                    if (!GatePreview.visible()) {
                        RaceHud.message(Component.translatable("propwash.race.freeform.preference",
                                GateMounting.variantName(CourseElement.FREEFORM, preference.variant(CourseElement.FREEFORM))), RaceHud.WHITE);
                    }
                    return;
                }
                CourseElement element = heldElement(player);
                if (element != null) {
                    setPreference(sneaking ? preference.nextVariant(element) : preference.turnedBy(GateMount.Surface.FLOOR, step()));
                    if (!GatePreview.visible()) {
                        RaceHud.message(Component.translatable("propwash.race.element.pose", GateMounting.elementName(element),
                                GateMounting.variantName(element, preference.variant(element)),
                                Component.translatable("propwash.race.element.turn", GateMounting.signedDegrees(GateTilt.wrap(
                                        preference.floorTurn() * GatePlacement.QUARTER + preference.floorFine())))), RaceHud.WHITE);
                    }
                    return;
                }
                GateMount.Surface surface = lastSurface;
                setPreference(sneaking ? preference.nextMount(surface) : preference.turnedBy(surface, step()));
                if (!GatePreview.visible()) {
                    RaceHud.message(GateMounting.describe(preference.mount(surface), preference.turn(surface), preference.fine(surface)),
                            RaceHud.WHITE);
                }
            }
            case NONE, FLIGHT_MODE -> {
            }
        }
    }

    public static float step() {
        return GatePlacement.validStep(RaceConfig.CONFIG.get().gateRotateStep);
    }

    public static GateKeyRoute.Hands hands(LocalPlayer player) {
        return new GateKeyRoute.Hands(gateHand(player) != null, holdingCourseTool(player),
                ContentIds.isRemote(player.getMainHandItem()) || ContentIds.isRemote(player.getOffhandItem()));
    }

    public static boolean sharedWithFlightMode() {
        KeyMapping key = rotate;
        KeyMapping flightMode = InputKeys.FLIGHT_MODE;
        return key != null && !key.isUnbound() && !flightMode.isUnbound() && key.saveString().equals(flightMode.saveString())
                && ClientServices.PLATFORM.sameKeyModifier(key, flightMode);
    }
}
