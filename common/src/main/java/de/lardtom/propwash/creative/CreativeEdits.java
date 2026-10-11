package de.lardtom.propwash.creative;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.creative.net.CreativeEditC2S;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.network.ServerPayloadContext;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class CreativeEdits {
    public static final String DENIED_KEY = "propwash.creative.denied";
    public static final String REJECTED_KEY = "propwash.creative.rejected";

    private CreativeEdits() {
    }

    public static boolean mayEdit(Player player) {
        return player != null && player.isCreative();
    }

    static void onEdit(CreativeEditC2S payload, ServerPayloadContext context) {
        ServerPlayer player = context.player();
        if (!mayEdit(player)) {
            player.sendOverlayMessage(Component.translatable(DENIED_KEY).withStyle(ChatFormatting.RED));
            return;
        }
        CreativeSpec spec = CreativeLimits.sanitize(payload.spec()).spec();
        if (payload.entityId() == CreativeEditC2S.HELD_ITEM) {
            ItemStack stack = player.getItemInHand(payload.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (!ContentIds.is(stack, ContentIds.DRONE)) {
                return;
            }
            DroneBuild build = stack.getOrDefault(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
            DroneBuild next = edited(player, build, spec);
            if (next == null) {
                return;
            }
            stack.set(CoreComponents.DRONE_BUILD.get(), next);
            if (Float.isFinite(payload.uptilt())) {
                FlightTune tune = stack.getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY);
                FlightTune result = new FlightTune(CameraMount.withUptilt(tune.values(), next, payload.uptilt()));
                if (result.values().isEmpty()) {
                    stack.remove(CoreComponents.FLIGHT_TUNE.get());
                } else {
                    stack.set(CoreComponents.FLIGHT_TUNE.get(), result);
                }
            }
            return;
        }
        if (!(player.level().getEntity(payload.entityId()) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            return;
        }
        boolean pilot = drone.isFlownBy(player.getUUID());
        boolean nearbyFree = drone.pilot().isEmpty() && player.position().distanceTo(drone.position()) <= DronePiloting.TUNE_REACH;
        if (!pilot && !nearbyFree) {
            return;
        }
        DroneBuild next = edited(player, drone.build(), spec);
        if (next == null) {
            return;
        }
        drone.setBuild(next);
        if (Float.isFinite(payload.uptilt())) {
            drone.setTune(new FlightTune(CameraMount.withUptilt(drone.tune().values(), next, payload.uptilt())));
        }
    }

    private static DroneBuild edited(ServerPlayer player, DroneBuild build, CreativeSpec spec) {
        if (!build.isCreative()) {
            return null;
        }
        DroneBuild next = build.withCreative(spec);
        if (!usable(next)) {
            player.sendOverlayMessage(Component.translatable(REJECTED_KEY).withStyle(ChatFormatting.RED));
            return null;
        }
        return next;
    }

    public static boolean usable(DroneBuild build) {
        AirframeSpec spec;
        try {
            spec = AirframeSpec.of(build);
        } catch (RuntimeException e) {
            return false;
        }
        if (!(spec.massKg > 0.0 && spec.inertiaXx > 0.0 && spec.inertiaYy > 0.0 && spec.inertiaZz > 0.0)) {
            return false;
        }
        for (Field field : AirframeSpec.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != double.class) {
                continue;
            }
            try {
                if (!Double.isFinite(field.getDouble(spec))) {
                    return false;
                }
            } catch (IllegalAccessException e) {
                return false;
            }
        }
        return true;
    }
}
