package de.lardtom.propwash.content;

import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.ActionCamSpec;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PartAvailability;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PartNames;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.core.PartRef;
import de.lardtom.propwash.creative.CreativeModule;
import de.lardtom.propwash.creative.CreativeSpec;
import de.lardtom.propwash.drone.bind.BindTooltips;
import de.lardtom.propwash.join.JoinText;
import de.lardtom.propwash.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class PartItem extends Item {
    public static final String ADDON_SUMMARY = "propwash.parts.help.addon_summary";

    public PartItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag tooltipFlag) {
        PartRef ref = itemStack.get(CoreComponents.PART.get());
        if (ref == null) {
            return;
        }
        if (!PartAvailability.activeKey(ref.key())) {
            builder.accept(JoinText.unavailable().copy().withStyle(ChatFormatting.RED));
            return;
        }
        ref.resolve().ifPresentOrElse(
                part -> help(part, this.status(itemStack, part)).appendTo(builder),
                () -> builder.accept(Component.translatable(PartNames.MISSING_PART, ref.key()).withStyle(ChatFormatting.RED)));
    }

    protected List<Component> status(ItemStack stack, PartInfo part) {
        List<Component> status = new ArrayList<>();
        if (PartCondition.worn(stack)) {
            status.add(PartCondition.line(PartCondition.of(stack)));
            if (PartCondition.broken(stack) && Recycling.recyclable(part)) {
                status.add(Component.translatable("propwash.content.tooltip.recycle").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (PartCondition.broken(stack) && PropRepairs.isProp(stack)) {
                status.add(Component.translatable("propwash.content.tooltip.repair").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        FlightTune tune = stack.get(CoreComponents.FLIGHT_TUNE.get());
        Float uptilt = DroneAssembly.frameUptilt(stack);
        if (uptilt != null) {
            status.add(Component.translatable("propwash.content.tooltip.camera_angle_stored", Numbers.of(uptilt, 0)).withStyle(ChatFormatting.AQUA));
        }
        if (tune != null && !tune.equals(FlightTune.EMPTY) && !DroneAssembly.onlyCameraUptilt(tune)) {
            status.add(Component.translatable("propwash.content.tooltip.tune_stored").withStyle(ChatFormatting.AQUA));
        }
        if (part instanceof StackType controller && controller.creative()) {
            CreativeSpec spec = stack.get(CreativeModule.SPEC.get());
            status.add((spec == null || spec.isEmpty() ? Component.translatable("propwash.creative.tooltip.controller_plain")
                    : Component.translatable("propwash.creative.tooltip.controller_stored", spec.values().size() + spec.base().size()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        Optional<Component> bind = BindTooltips.line(stack);
        if (bind.isPresent()) {
            status.add(bind.get());
        } else if (stack.has(CoreComponents.DRONE_ID.get())) {
            status.add(Component.translatable("propwash.content.tooltip.binding_stored").withStyle(ChatFormatting.AQUA));
        }
        DronePaint paint = PaintItems.paint(stack);
        if (!paint.isDefault()) {
            status.add(Component.translatable("propwash.paint.tooltip.frame", paint.count()).withStyle(ChatFormatting.GOLD));
        }
        if (part instanceof AccessoryType accessory && accessory.isFlashbackCam()) {
            status.add(flashbackLine());
        }
        if (part instanceof AccessoryType accessory && accessory.camera().map(ActionCamSpec::ownBattery).orElse(false)) {
            float charge = DroneAssembly.cameraCharge(stack);
            status.add(cameraChargeLine(accessory.camera().get(), charge));
        }
        return status;
    }

    public static Component flashbackLine() {
        return Services.PLATFORM.isModLoaded(AccessoryType.FLASHBACK_MOD_ID)
                ? Component.translatable("propwash.content.tooltip.flashback_found").withStyle(ChatFormatting.GREEN)
                : Component.translatable("propwash.content.tooltip.flashback_missing").withStyle(ChatFormatting.GOLD);
    }

    public static Component cameraChargeLine(ActionCamSpec camera, float charge) {
        long minutes = Math.round(camera.recordMinutes(ActionCamSpec.FULL_HD) * Math.max(0.0F, Math.min(1.0F, charge)));
        return Component.translatable("propwash.content.tooltip.camera_charge", Batteries.percent(charge), minutes)
                .withStyle(BatteryItem.chargeColor(charge));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return PartCondition.worn(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Mth.clamp(Math.round(13.0F * PartCondition.of(stack)), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return PartCondition.barColor(PartCondition.of(stack));
    }

    public static ItemHelp.Builder help(PartInfo part, List<Component> status) {
        PartCategory category = PartCategory.of(part);
        String categoryPrefix = ItemHelp.PREFIX + "category." + category.key();
        String partPrefix = PartNames.helpPrefix(part.id());
        String summaryKey = partPrefix + ".summary";
        ItemHelp.Builder builder = PartNames.own(part.id()) || Language.getInstance().has(summaryKey)
                ? ItemHelp.summary(summaryKey)
                : ItemHelp.summary(ADDON_SUMMARY, Component.translatable(category.translationKey()), part.id().getNamespace());
        if (!(part instanceof StackType controller && controller.creative())) {
            builder.what(categoryPrefix);
        }
        return builder
                .status(status)
                .what(partPrefix)
                .use(categoryPrefix)
                .use(partPrefix)
                .fits(out -> PartTooltips.fits(part, out))
                .specs(out -> PartTooltips.specs(part, out));
    }
}
