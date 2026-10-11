package de.lardtom.propwash.content;

import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.core.PartRef;
import de.lardtom.propwash.parts.AddonItems;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.Equippable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ContentItems {
    private static final Map<PartInfo, RegHolder<PartItem>> PARTS = new LinkedHashMap<>();

    static {
        for (PartCategory category : PartCategory.values()) {
            for (PartInfo part : category.parts()) {
                if (!part.builtin()) {
                    continue;
                }
                if (category == PartCategory.BATTERY) {
                    PARTS.put(part, PropwashRegistries.<PartItem>item(part.key(), BatteryItem::new, p -> p
                            .stacksTo(stackSize(category))
                            .overrideDescription(part.translationKey())
                            .component(CoreComponents.PART.get(), PartRef.of(part))
                            .component(CoreComponents.BATTERY_CHARGE.get(), Batteries.FULL)));
                } else {
                    PARTS.put(part, PropwashRegistries.item(part.key(), PartItem::new, p -> {
                        part.definition().basics().rarity().ifPresent(p::rarity);
                        return p.stacksTo(stackSize(category))
                                .overrideDescription(part.translationKey())
                                .component(CoreComponents.PART.get(), PartRef.of(part));
                    }));
                }
            }
        }
    }

    public static final RegHolder<FpvGogglesItem> GOGGLES_ANALOG = goggles(ContentIds.GOGGLES_ANALOG, VideoSystem.ANALOG, Rarity.COMMON);
    public static final RegHolder<FpvGogglesItem> GOGGLES_DIGITAL = goggles(ContentIds.GOGGLES_DIGITAL, VideoSystem.DIGITAL, Rarity.UNCOMMON);
    public static final RegHolder<FpvGogglesItem> GOGGLES_CREATIVE = goggles(ContentIds.GOGGLES_CREATIVE, VideoSystem.CREATIVE, Rarity.EPIC);

    private ContentItems() {
    }

    static void bootstrap() {
    }

    public static int stackSize(PartCategory category) {
        return switch (category) {
            case FRAME, STACK, MOTOR, VIDEO, BATTERY, ACCESSORY -> 16;
            case PROP -> 64;
        };
    }

    public static Map<PartInfo, RegHolder<PartItem>> parts() {
        return Collections.unmodifiableMap(PARTS);
    }

    public static RegHolder<PartItem> part(PartInfo part) {
        RegHolder<PartItem> holder = PARTS.get(part);
        if (holder == null) {
            throw new IllegalArgumentException("Kein Item für Bauteil " + part.key());
        }
        return holder;
    }

    public static ItemStack stack(PartInfo part) {
        if (part.missing()) {
            return ItemStack.EMPTY;
        }
        RegHolder<PartItem> holder = PARTS.get(part);
        if (holder != null) {
            return new ItemStack(holder.get());
        }
        return AddonItems.INSTANCE.stack(part.id());
    }

    private static RegHolder<FpvGogglesItem> goggles(Identifier id, VideoSystem system, Rarity rarity) {
        return PropwashRegistries.item(id.getPath(), p -> new FpvGogglesItem(system, p), p -> p
                .stacksTo(1)
                .rarity(rarity)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
                        .setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
                        .setDamageOnHurt(false)
                        .build()));
    }
}
