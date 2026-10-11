package de.lardtom.propwash.parts;

import de.lardtom.propwash.api.registry.PartItems;
import de.lardtom.propwash.apiimpl.ExtensionTabs;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.content.BatteryItem;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.PartItem;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.core.PartRef;
import de.lardtom.propwash.registry.PropwashRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

public final class AddonItems implements PartItems {
    public static final AddonItems INSTANCE = new AddonItems();

    private final Object lock = new Object();
    private final Map<Identifier, UnaryOperator<Item.Properties>> queued = new LinkedHashMap<>();
    private final Map<Identifier, Item> registered = new ConcurrentHashMap<>();
    private @Nullable BiConsumer<ResourceKey<Item>, Item> directSink;
    private boolean closed;

    private AddonItems() {
    }

    @Override
    public int registerItems(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        int count = 0;
        synchronized (this.lock) {
            for (PartInfo part : PartIndex.registered()) {
                Identifier id = part.id();
                if (!namespace.equals(id.getNamespace()) || part.builtin() || this.queued.containsKey(id) || this.registered.containsKey(id)) {
                    continue;
                }
                this.enqueue(part, UnaryOperator.identity());
                count++;
            }
        }
        return count;
    }

    @Override
    public void registerItem(Identifier partId, UnaryOperator<Item.Properties> properties) {
        Objects.requireNonNull(properties, "properties");
        PartInfo part = PartIndex.registeredById(partId).orElseThrow(() -> new IllegalArgumentException("Teil-Item für " + partId
                + ": Bauteil ist nicht angemeldet (zuerst PropwashApi.parts().register oder loadBundled)"));
        if (part.builtin()) {
            throw new IllegalArgumentException("Teil-Item für " + partId + ": eingebaute Teile haben ihr Item schon");
        }
        synchronized (this.lock) {
            if (this.queued.containsKey(partId) || this.registered.containsKey(partId)) {
                throw new IllegalArgumentException("Teil-Item für " + partId + " ist schon angemeldet");
            }
            this.enqueue(part, properties);
        }
    }

    private void enqueue(PartInfo part, UnaryOperator<Item.Properties> properties) {
        if (this.closed) {
            throw new IllegalStateException("Teil-Item für " + part.id() + " kommt zu spät: Items nur während der Mod-Initialisierung anmelden"
                    + " (Fabric onInitialize, NeoForge Mod-Konstruktor)");
        }
        BiConsumer<ResourceKey<Item>, Item> sink = this.directSink;
        if (sink != null) {
            this.create(part, properties, sink);
        } else {
            this.queued.put(part.id(), properties);
        }
    }

    public void flush(BiConsumer<ResourceKey<Item>, Item> sink, boolean stayOpen) {
        synchronized (this.lock) {
            for (Map.Entry<Identifier, UnaryOperator<Item.Properties>> entry : new ArrayList<>(this.queued.entrySet())) {
                PartInfo part = PartIndex.registeredById(entry.getKey()).orElseThrow();
                this.create(part, entry.getValue(), sink);
            }
            this.queued.clear();
            if (stayOpen) {
                this.directSink = sink;
            } else {
                this.directSink = null;
                this.closed = true;
            }
        }
    }

    public void close() {
        synchronized (this.lock) {
            this.directSink = null;
            this.closed = true;
        }
    }

    public List<Identifier> pending() {
        synchronized (this.lock) {
            return List.copyOf(this.queued.keySet());
        }
    }

    public Map<Identifier, Item> registeredItems() {
        return Map.copyOf(this.registered);
    }

    private void create(PartInfo part, UnaryOperator<Item.Properties> custom, BiConsumer<ResourceKey<Item>, Item> sink) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, part.id());
        Item item = createItem(part, custom, key);
        sink.accept(key, item);
        this.registered.put(part.id(), item);
        ExtensionTabs.INSTANCE.touch(part.id().getNamespace());
    }

    public static int stackSize(PartInfo part) {
        int own = part.definition().basics().stackSize();
        return own > 0 ? own : ContentItems.stackSize(PartCategory.of(part));
    }

    static Item createItem(PartInfo part, UnaryOperator<Item.Properties> custom, ResourceKey<Item> key) {
        Item.Properties properties = new Item.Properties()
                .component(DataComponents.TOOLTIP_STYLE, PropwashRegistries.TOOLTIP_STYLE)
                .stacksTo(stackSize(part))
                .overrideDescription(part.translationKey())
                .component(CoreComponents.PART.get(), PartRef.of(part));
        if (part instanceof BatteryType) {
            properties.component(CoreComponents.BATTERY_CHARGE.get(), Batteries.FULL);
        }
        part.definition().basics().rarity().ifPresent(properties::rarity);
        Item.Properties finished = custom.apply(properties).setId(key);
        return part instanceof BatteryType ? new BatteryItem(finished) : new PartItem(finished);
    }

    @Override
    public Optional<Item> item(Identifier partId) {
        if (partId == null || PartIndex.byId(partId).isEmpty()) {
            return Optional.empty();
        }
        Item own = this.registered.get(partId);
        if (own != null) {
            return Optional.of(own);
        }
        return BuiltInRegistries.ITEM.getOptional(partId);
    }

    @Override
    public Optional<Identifier> partOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        PartRef ref = stack.get(CoreComponents.PART.get());
        return ref == null ? Optional.empty() : ref.resolve().map(PartInfo::id);
    }

    @Override
    public ItemStack stack(Identifier partId) {
        return this.item(partId).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    @Override
    public ItemStack stack(Identifier partId, float charge) {
        Optional<PartInfo> part = partId == null ? Optional.empty() : PartIndex.byId(partId);
        if (part.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!(part.get() instanceof BatteryType)) {
            throw new IllegalArgumentException("Ladestand gibt es nur für Akkus: " + partId);
        }
        if (!(charge >= 0.0F && charge <= Batteries.FULL)) {
            throw new IllegalArgumentException("Ladestand muss zwischen 0 und 1 liegen: " + charge);
        }
        ItemStack stack = this.stack(partId);
        if (!stack.isEmpty()) {
            Batteries.storeCharge(stack, charge);
        }
        return stack;
    }
}
