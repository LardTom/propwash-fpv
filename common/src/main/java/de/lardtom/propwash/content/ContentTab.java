package de.lardtom.propwash.content;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.registry.ExtensionTab;
import de.lardtom.propwash.bando.BandoModule;
import de.lardtom.propwash.api.registry.TabSection;
import de.lardtom.propwash.apiimpl.ApiExtensions;
import de.lardtom.propwash.apiimpl.ExtensionTabs;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartAvailability;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.join.SessionItems;
import de.lardtom.propwash.parts.AddonItems;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.registry.PropwashRegistries;
import de.lardtom.propwash.registry.RegHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ContentTab {
    public static final RegHolder<CreativeModeTab> MAIN = PropwashRegistries.creativeTab(ExtensionTabs.MAIN_PATH, b -> b
            .title(Component.translatable("itemGroup.propwash.main"))
            .icon(ContentTab::icon)
            .displayItems((parameters, output) -> {
                for (ItemStack stack : contents()) {
                    output.accept(stack);
                }
            }));

    private ContentTab() {
    }

    static void bootstrap() {
    }

    public static ItemStack presetDrone(DroneBuild build) {
        ItemStack drone = DroneAssembly.preset(build);
        if (!drone.isEmpty()) {
            drone.set(DataComponents.ITEM_NAME, Component.translatable(DronePresets.translationKey(build)));
        }
        return drone;
    }

    public static List<ItemStack> contents() {
        List<ItemStack> stacks = new ArrayList<>();
        Set<Item> listed = new HashSet<>();
        add(stacks, listed, new ItemStack(ContentBlocks.DRONE_WORKBENCH.item().get()));
        add(stacks, listed, new ItemStack(ContentBlocks.BATTERY_CHARGER.item().get()));
        add(stacks, listed, new ItemStack(ContentItems.GOGGLES_ANALOG.get()));
        add(stacks, listed, new ItemStack(ContentItems.GOGGLES_DIGITAL.get()));
        add(stacks, listed, new ItemStack(ContentItems.GOGGLES_CREATIVE.get()));
        ContentIds.item(ContentIds.REMOTE).ifPresent(item -> add(stacks, listed, new ItemStack(item)));
        for (DroneBuild preset : DroneBuild.PRESETS) {
            ItemStack drone = presetDrone(preset);
            if (!drone.isEmpty()) {
                stacks.add(drone);
                listed.add(drone.getItem());
            }
        }
        ContentIds.item(ContentIds.DRONE).ifPresent(listed::add);
        if (BandoModule.MAP.isBound()) {
            listed.add(BandoModule.MAP.get());
        }
        for (RegHolder<PartItem> part : ContentItems.parts().values()) {
            add(stacks, listed, new ItemStack(part.get()));
        }
        for (CraftingMaterial material : CraftingMaterial.values()) {
            add(stacks, listed, ContentMaterials.stack(material));
        }
        for (Identifier id : List.of(ContentIds.GATE_SMALL, ContentIds.GATE_MEDIUM, ContentIds.GATE_LARGE, ContentIds.GATE_POLE, ContentIds.RACE_FLAG,
                ContentIds.SLALOM_POLE, ContentIds.DIVE_CUBE, ContentIds.FREEFORM_GATE, ContentIds.COURSE_TOOL, ContentIds.SIGNAL_REPEATER)) {
            ContentIds.item(id).ifPresent(item -> add(stacks, listed, new ItemStack(item)));
        }
        for (Item item : BuiltInRegistries.ITEM) {
            if (Constants.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) {
                add(stacks, listed, new ItemStack(item));
            }
        }
        for (PartInfo part : addonParts(ContentTab::withoutOwnTab)) {
            AddonItems.INSTANCE.item(part.id()).ifPresent(item -> add(stacks, listed, new ItemStack(item)));
        }
        for (DronePreset preset : ApiExtensions.registeredPresets()) {
            if (preset.creativeListed() && withoutOwnTab(preset.id().getNamespace())) {
                ItemStack drone = addonPreset(preset);
                if (!drone.isEmpty()) {
                    stacks.add(drone);
                }
            }
        }
        for (ItemStack stack : PropwashApi.creativeTab().collect()) {
            if (!stack.isEmpty() && SessionItems.available(stack)) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    public static List<PartInfo> addonParts() {
        return addonParts(namespace -> true);
    }

    public static List<PartInfo> addonParts(Predicate<String> namespaces) {
        List<PartInfo> parts = new ArrayList<>();
        for (PartKind kind : PartKind.values()) {
            parts.addAll(addonParts(kind, namespaces));
        }
        return parts;
    }

    private static List<PartInfo> addonParts(PartKind kind, Predicate<String> namespaces) {
        List<PartInfo> ofKind = new ArrayList<>();
        for (PartInfo part : PartIndex.all(kind)) {
            if (!part.builtin() && part.definition().basics().creativeListed() && namespaces.test(part.id().getNamespace())) {
                ofKind.add(part);
            }
        }
        ofKind.sort(Comparator.comparingInt((PartInfo part) -> part.definition().basics().sortOrder())
                .thenComparing(part -> part.id().toString()));
        return ofKind;
    }

    private static boolean withoutOwnTab(String namespace) {
        return !ExtensionTabs.INSTANCE.hasTab(namespace);
    }

    public static CreativeModeTab extensionTab(ExtensionTab tab) {
        return Services.PLATFORM.creativeTabBuilderAfter(ExtensionTabs.MAIN_TAB)
                .title(extensionTitle(tab.namespace()))
                .icon(tab::iconStack)
                .displayItems((parameters, output) -> {
                    for (ItemStack stack : tab.contents()) {
                        output.accept(stack);
                    }
                })
                .build();
    }

    public static Component extensionTitle(String namespace) {
        String modName = Services.PLATFORM.getModName(namespace).orElse("");
        return Component.translatable(ExtensionTab.TITLE_KEY,
                Component.translatableWithFallback("itemGroup." + namespace, ExtensionTabs.displayName(modName, namespace)));
    }

    public static List<ItemStack> extensionContents(ExtensionTab tab) {
        String namespace = tab.namespace();
        if (!PartAvailability.active(namespace)) {
            return List.of();
        }
        Set<ItemStack> stacks = ItemStackLinkedSet.createTypeAndComponentsSet();
        for (TabSection section : tab.sections()) {
            switch (section) {
                case PRESETS -> {
                    for (DronePreset preset : ApiExtensions.registeredPresets()) {
                        if (preset.creativeListed() && namespace.equals(preset.id().getNamespace())) {
                            addSingle(stacks, addonPreset(preset));
                        }
                    }
                }
                case ENTRIES -> {
                    for (ItemStack stack : tab.collect()) {
                        if (SessionItems.available(stack)) {
                            addSingle(stacks, stack);
                        }
                    }
                }
                default -> section.kind().ifPresent(kind -> addParts(stacks, kind, namespace));
            }
        }
        for (PartKind kind : PartKind.values()) {
            if (TabSection.of(kind).isEmpty()) {
                addParts(stacks, kind, namespace);
            }
        }
        return List.copyOf(stacks);
    }

    public static ItemStack extensionIcon(ExtensionTab tab, @Nullable Supplier<ItemStack> icon) {
        if (icon != null) {
            try {
                ItemStack stack = icon.get();
                if (stack != null && !stack.isEmpty()) {
                    return stack;
                }
            } catch (RuntimeException e) {
                Constants.LOG.warn("Symbol des Kreativ-Tabs {} fehlt: {}", tab.tabId(), e.getMessage());
            }
        }
        try {
            List<ItemStack> contents = tab.contents();
            if (!contents.isEmpty()) {
                return contents.getFirst().copyWithCount(1);
            }
        } catch (RuntimeException e) {
            Constants.LOG.warn("Kreativ-Tab {} ohne Inhalt für das Symbol: {}", tab.tabId(), e.getMessage());
        }
        return new ItemStack(ContentItems.GOGGLES_ANALOG.get());
    }

    private static void addParts(Set<ItemStack> stacks, PartKind kind, String namespace) {
        for (PartInfo part : addonParts(kind, namespace::equals)) {
            AddonItems.INSTANCE.item(part.id()).ifPresent(item -> addSingle(stacks, new ItemStack(item)));
        }
    }

    private static void addSingle(Set<ItemStack> stacks, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            stacks.add(stack.getCount() == 1 ? stack : stack.copyWithCount(1));
        }
    }

    private static ItemStack addonPreset(DronePreset preset) {
        try {
            ItemStack drone = PropwashApi.builds().createDrone(preset.data());
            if (!drone.isEmpty() && preset.data().name().isEmpty()) {
                drone.set(DataComponents.ITEM_NAME, Component.translatable(preset.translationKey()));
            }
            return drone;
        } catch (RuntimeException e) {
            Constants.LOG.warn("Preset {} erscheint nicht im Kreativ-Tab: {}", preset.id(), e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    private static void add(List<ItemStack> stacks, Set<Item> listed, ItemStack stack) {
        if (!stack.isEmpty() && listed.add(stack.getItem())) {
            stacks.add(stack);
        }
    }

    private static ItemStack icon() {
        ItemStack drone = DroneAssembly.preset(DroneBuild.FREESTYLE);
        return drone.isEmpty() ? new ItemStack(ContentItems.GOGGLES_ANALOG.get()) : drone;
    }
}
