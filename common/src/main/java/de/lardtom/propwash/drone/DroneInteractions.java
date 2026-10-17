package de.lardtom.propwash.drone;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.PartCondition;
import de.lardtom.propwash.content.PartTooltips;
import de.lardtom.propwash.core.BoundDrone;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.drone.bind.BindAccess;
import de.lardtom.propwash.drone.bind.ReceiverBinds;
import de.lardtom.propwash.drone.coating.DroneCoating;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.server.WorldRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

public final class DroneInteractions {
    private DroneInteractions() {
    }

    public static DroneInteraction.HeldKind kindOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return DroneInteraction.HeldKind.EMPTY;
        }
        if (ContentIds.isRemote(stack)) {
            return DroneInteraction.HeldKind.REMOTE;
        }
        if (ContentIds.isGoggles(stack)) {
            return DroneInteraction.HeldKind.GOGGLES;
        }
        if (Batteries.isBattery(stack)) {
            return DroneInteraction.HeldKind.BATTERY;
        }
        if (propType(stack).isPresent()) {
            return DroneInteraction.HeldKind.PROP;
        }
        if (stack.is(DroneModule.WATERPROOF_SPRAY.get())) {
            return DroneInteraction.HeldKind.COATING;
        }
        return DroneInteraction.HeldKind.OTHER;
    }

    public static DroneInteraction.Action resolve(Player player, QuadcopterEntity drone, ItemStack held) {
        Optional<UUID> pilot = drone.pilot();
        boolean self = pilot.filter(player.getUUID()::equals).isPresent();
        boolean other = pilot.isPresent() && !self;
        PartCompatibility rule = WorldRules.partCompatibility(player.level());
        boolean fits = Batteries.typeOf(held).map(type -> rule.batteryFits(drone.build().frame(), type)).orElse(false);
        return DroneInteraction.resolve(kindOf(held), player.isSecondaryUseActive(), other, self, drone.batteryLocked(), fits);
    }

    public static Component droneName(QuadcopterEntity drone) {
        Component custom = drone.getCustomName();
        return custom != null ? custom : PartTooltips.frameShortName(drone.build().frame());
    }

    public static BoundDrone binding(QuadcopterEntity drone) {
        Component custom = drone.getCustomName();
        DroneBuild build = drone.build();
        return new BoundDrone(drone.getUUID(), custom == null ? "" : BoundDrone.trimName(custom.getString()), build.frame(), build.video().system);
    }

    public static void apply(ServerPlayer player, QuadcopterEntity drone, DroneInteraction.Action action, ItemStack held) {
        if (drone.isRemoved()) {
            return;
        }
        switch (action) {
            case BIND_REMOTE -> bindRemote(player, drone, held);
            case CHECK_GOGGLES -> checkGoggles(player, drone, held);
            case QUICK_SWAP -> quickSwap(player, drone, held);
            case REJECT_BATTERY -> Batteries.typeOf(held).ifPresent(type -> player.sendOverlayMessage(incompatible(drone, type)));
            case HINT_BATTERY -> player.sendOverlayMessage(Component.translatable("propwash.drone.battery.hint").withStyle(ChatFormatting.GRAY));
            case HINT_PROPS -> player.sendOverlayMessage(Component.translatable("propwash.drone.props.hint").withStyle(ChatFormatting.GRAY));
            case OPEN_SERVICE_MENU -> openServiceMenu(player, drone);
            case LOCKED -> player.sendOverlayMessage(Component.translatable("propwash.drone.battery.locked").withStyle(ChatFormatting.RED));
            case PICK_UP -> DronePiloting.pickUp(player, drone);
            case OCCUPIED -> player.sendOverlayMessage(Component.translatable("propwash.drone.pilot.occupied").withStyle(ChatFormatting.RED));
            case COAT -> DroneCoating.coat(player, drone, held);
            case PASS -> {
            }
        }
    }

    public static Component incompatible(QuadcopterEntity drone, BatteryType type) {
        return Component.translatable("propwash.drone.battery.incompatible", Component.translatable(type.translationKey()), droneName(drone),
                PartTooltips.batteryFitReason(drone.build().frame(), type)).withStyle(ChatFormatting.RED);
    }

    static boolean bindRemote(ServerPlayer player, QuadcopterEntity drone, ItemStack remote) {
        DronePiloting.dropLegacyBind(ContentIds.wornGoggles(player));
        PilotRules.Decision decision = DronePiloting.decideMode(player, drone);
        if (!decision.connects()) {
            player.sendOverlayMessage(DronePiloting.videoMismatch("propwash.drone.bind.refused_video", player, drone).withStyle(ChatFormatting.RED));
            return false;
        }
        remote.set(CoreComponents.BOUND_DRONE.get(), binding(drone));
        BindAccess before = ReceiverBinds.bind(player, drone);
        String key = before == BindAccess.OTHER ? "propwash.drone.bind.remote_taken" : "propwash.drone.bind.remote";
        String view = decision == PilotRules.Decision.FPV ? "propwash.drone.bind.view.fpv" : "propwash.drone.bind.view.los";
        player.sendOverlayMessage(Component.translatable(view, Component.translatable(key, droneName(drone))).withStyle(ChatFormatting.AQUA));
        return true;
    }

    static boolean checkGoggles(ServerPlayer player, QuadcopterEntity drone, ItemStack goggles) {
        Optional<VideoSystem> system = ContentIds.gogglesSystem(goggles);
        if (system.isEmpty()) {
            return false;
        }
        DronePiloting.dropLegacyBind(goggles);
        VideoSystem droneSystem = drone.build().video().system;
        boolean fits = system.get().receives(droneSystem);
        player.sendOverlayMessage(Component.translatable(fits ? "propwash.drone.goggles.fits" : "propwash.drone.goggles.no_fit",
                DronePiloting.videoSystemName(system.get()), droneName(drone), DronePiloting.videoSystemName(droneSystem))
                .withStyle(fits ? ChatFormatting.AQUA : ChatFormatting.GOLD));
        return fits;
    }

    static boolean quickSwap(ServerPlayer player, QuadcopterEntity drone, ItemStack held) {
        Optional<BatteryType> type = Batteries.typeOf(held);
        if (type.isEmpty() || drone.batteryLocked()) {
            return false;
        }
        if (!WorldRules.partCompatibility(player.level()).batteryFits(drone.build().frame(), type.get())) {
            player.sendOverlayMessage(incompatible(drone, type.get()));
            return false;
        }
        float charge = Batteries.charge(held);
        float condition = PartCondition.of(held);
        boolean hadBattery = drone.hasBattery();
        ItemStack old = drone.takeBattery();
        held.shrink(1);
        drone.insertBattery(type.get(), charge, condition);
        if (!old.isEmpty() && !player.getInventory().add(old) && !old.isEmpty()) {
            drone.spawnAtLocation(player.level(), old);
        }
        player.level().playSound(null, drone.getX(), drone.getY(), drone.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 0.8F, 1.2F);
        String key = hadBattery ? "propwash.drone.battery.swapped" : "propwash.drone.battery.inserted";
        player.sendOverlayMessage(Component.translatable(key, Component.translatable(type.get().translationKey()), Batteries.percent(charge))
                .withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static Optional<PropType> propType(ItemStack stack) {
        return DroneAssembly.part(stack).filter(PropType.class::isInstance).map(PropType.class::cast);
    }

    static void openServiceMenu(ServerPlayer player, QuadcopterEntity drone) {
        Services.PLATFORM.openMenu(player, new SimpleMenuProvider((containerId, inventory, p) -> new DroneServiceMenu(containerId, inventory, drone),
                DroneServiceMenu.TITLE), ByteBufCodecs.VAR_INT, drone.getId());
    }
}
