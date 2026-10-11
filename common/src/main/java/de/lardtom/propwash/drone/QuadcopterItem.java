package de.lardtom.propwash.drone;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.ActionCamSpec;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.BatteryItem;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.ItemHelp;
import de.lardtom.propwash.content.PartCondition;
import de.lardtom.propwash.content.PartItem;
import de.lardtom.propwash.content.PartTooltips;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.drone.bind.BindTooltips;
import de.lardtom.propwash.join.JoinText;
import de.lardtom.propwash.join.SessionItems;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.sim.BuildAnalysis;
import de.lardtom.propwash.video.FovMath;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

public class QuadcopterItem extends Item {
    private static final double SURFACE_GAP = 0.01;

    public QuadcopterItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
        if (drone == null) {
            return InteractionResult.FAIL;
        }
        drone.loadFromStack(stack);
        UUID id = stack.get(CoreComponents.DRONE_ID.get());
        if (id != null && serverLevel.getEntityInAnyDimension(id) == null) {
            drone.setUUID(id);
        }
        Player player = context.getPlayer();
        float yRot = player != null ? player.getYRot() : context.getRotation();
        Vec3 pos = placement(context, drone.getBbWidth(), drone.getBbHeight());
        drone.snapTo(pos.x, pos.y, pos.z, yRot, 0.0F);
        drone.setAttitude(new Quaternionf().rotationY((float) (Math.PI - Math.toRadians(yRot))));
        if (!level.noCollision(drone, drone.getBoundingBox())) {
            return InteractionResult.FAIL;
        }
        if (!level.addFreshEntity(drone)) {
            return InteractionResult.FAIL;
        }
        level.gameEvent(player, GameEvent.ENTITY_PLACE, BlockPos.containing(pos));
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    static Vec3 placement(UseOnContext context, double width, double height) {
        Vec3 click = context.getClickLocation();
        Direction face = context.getClickedFace();
        double half = width / 2.0 + SURFACE_GAP;
        return switch (face) {
            case UP -> new Vec3(click.x, click.y + SURFACE_GAP, click.z);
            case DOWN -> new Vec3(click.x, click.y - height - SURFACE_GAP, click.z);
            case NORTH -> new Vec3(click.x, click.y - height / 2.0, click.z - half);
            case SOUTH -> new Vec3(click.x, click.y - height / 2.0, click.z + half);
            case WEST -> new Vec3(click.x - half, click.y - height / 2.0, click.z);
            case EAST -> new Vec3(click.x + half, click.y - height / 2.0, click.z);
        };
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        DroneBuild build = stack.getOrDefault(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        boolean installed = stack.getOrDefault(CoreComponents.BATTERY_INSTALLED.get(), Boolean.TRUE);
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        Component name = custom != null ? custom : PartTooltips.frameShortName(build.frame());
        List<Component> status = new ArrayList<>();
        List<String> unavailable = SessionItems.unavailable(build);
        if (!unavailable.isEmpty()) {
            status.add(JoinText.unavailableParts(unavailable).copy().withStyle(ChatFormatting.RED));
        }
        if (installed) {
            float charge = Batteries.charge(stack);
            status.add(Component.translatable("propwash.drone.tooltip.battery_type", Component.translatable(build.battery().translationKey()),
                    Batteries.percent(charge)).withStyle(BatteryItem.chargeColor(charge)));
        } else {
            status.add(Component.translatable(ItemHelp.PREFIX + "status.no_battery").withStyle(ChatFormatting.RED));
        }
        build.accessories().camera().flatMap(AccessoryType::camera).filter(ActionCamSpec::ownBattery)
                .ifPresent(camera -> status.add(PartItem.cameraChargeLine(camera, DroneAssembly.cameraCharge(stack))));
        FlightTune tune = stack.getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY);
        int tuned = tune.values().size() - (tune.values().containsKey(TuneMapping.CAMERA_UPTILT) ? 1 : 0);
        if (tuned > 0) {
            status.add(Component.translatable("propwash.drone.tooltip.tuned", tuned).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        PartWear wear = stack.getOrDefault(CoreComponents.PART_WEAR.get(), PartWear.PRISTINE);
        if (!wear.isNew()) {
            status.add(PartCondition.summary(wear));
        }
        DronePaint paint = PaintItems.paint(stack);
        if (!paint.isDefault()) {
            status.add(Component.translatable("propwash.paint.tooltip.drone", paint.count()).withStyle(ChatFormatting.GOLD));
        }
        if (!build.liftedProblems(WorldRules.partCompatibility()).isEmpty()) {
            status.add(Component.translatable("propwash.drone.tooltip.unrealistic_status").withStyle(ChatFormatting.GOLD));
        }
        BindTooltips.line(stack).ifPresent(status::add);
        ItemHelp.summary(ItemHelp.PREFIX + "drone.summary", name)
                .status(status)
                .what(ItemHelp.PREFIX + "drone")
                .use(ItemHelp.PREFIX + "drone")
                .specs(out -> {
                    specs(build, installed, out);
                    camera(build, tune, out);
                    PartTooltips.frameBatteries(build.frame(), out);
                    PartCondition.detailLines(wear).forEach(out);
                })
                .appendTo(builder);
    }

    private static void specs(DroneBuild build, boolean installed, Consumer<Component> builder) {
        if (build.isCreative()) {
            int edits = build.creative().values().size() + build.creative().base().size();
            builder.accept((edits == 0 ? Component.translatable("propwash.creative.tooltip.drone_plain")
                    : Component.translatable("propwash.creative.tooltip.drone_edited", edits)).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        builder.accept(Component.translatable("propwash.drone.tooltip.frame", Component.translatable(build.frame().translationKey()))
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("propwash.drone.tooltip.power",
                Component.translatable(build.motor().translationKey()), Component.translatable(build.prop().translationKey()))
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("propwash.drone.tooltip.electronics",
                Component.translatable(build.stack().translationKey()), Component.translatable(build.battery().translationKey()))
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("propwash.drone.tooltip.video", Component.translatable(build.video().translationKey()),
                DronePiloting.videoSystemName(build.video().system)).withStyle(ChatFormatting.GRAY));
        PartCompatibility rule = WorldRules.partCompatibility();
        if (!build.buildable(rule)) {
            builder.accept(Component.translatable("propwash.drone.tooltip.not_buildable").withStyle(ChatFormatting.RED));
            for (DroneBuild.Issue issue : build.problems(rule)) {
                builder.accept(Component.literal(" - ").append(Component.translatable(issue.translationKey())).withStyle(ChatFormatting.RED));
            }
            return;
        }
        List<DroneBuild.Issue> lifted = build.liftedProblems(rule);
        if (!lifted.isEmpty()) {
            builder.accept(Component.translatable("propwash.drone.tooltip.unrealistic").withStyle(ChatFormatting.GOLD));
            for (DroneBuild.Issue issue : lifted) {
                builder.accept(Component.literal(" - ").append(Component.translatable(issue.translationKey())).withStyle(ChatFormatting.GOLD));
            }
        }
        BuildAnalysis analysis = BuildAnalysis.of(build);
        String dry = format("%.0f", build.flight().dryMassGrams());
        if (installed) {
            builder.accept(Component.translatable("propwash.drone.tooltip.weights", dry, format("%.0f", analysis.massGrams()))
                    .withStyle(ChatFormatting.DARK_AQUA));
            builder.accept(Component.translatable("propwash.drone.tooltip.stats", format("%.1f", analysis.thrustToWeight()),
                    format("%.1f", analysis.mixedFlightTimeMin())).withStyle(ChatFormatting.DARK_AQUA));
        } else {
            builder.accept(Component.translatable("propwash.drone.tooltip.mass_without_battery", dry).withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    private static void camera(DroneBuild build, FlightTune tune, Consumer<Component> builder) {
        float uptilt = CameraMount.uptilt(build, tune.values());
        builder.accept(Component.translatable("propwash.drone.tooltip.camera", format("%.0f", FovMath.cameraHorizontalDegrees(build.flight().video())),
                format("%.0f", uptilt)).withStyle(ChatFormatting.GRAY));
    }

    private static String format(String pattern, double value) {
        return String.format(Locale.ROOT, pattern, value);
    }
}
