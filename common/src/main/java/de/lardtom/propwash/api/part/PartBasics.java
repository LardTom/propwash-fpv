package de.lardtom.propwash.api.part;

import net.minecraft.world.item.Rarity;

import java.util.Objects;
import java.util.Optional;

public record PartBasics(double massGrams, int stackSize, int sortOrder, boolean creativeListed, double durability,
                         Optional<RecycleYield> recycle, PartVisual visual, Optional<Rarity> rarity) {
    public static final int DEFAULT_SORT_ORDER = 1000;

    public PartBasics {
        if (!Double.isFinite(massGrams) || massGrams <= 0.0 || massGrams > 20000.0) {
            throw new IllegalArgumentException("Masse muss größer als 0 und höchstens 20000 g sein: " + massGrams);
        }
        if (stackSize < 0 || stackSize > 99) {
            throw new IllegalArgumentException("Stapelgröße muss zwischen 0 (Standard) und 99 liegen: " + stackSize);
        }
        if (!Double.isFinite(durability) || durability <= 0.0 || durability > 100.0) {
            throw new IllegalArgumentException("Haltbarkeit muss größer als 0 und höchstens 100 sein: " + durability);
        }
        recycle = Objects.requireNonNullElse(recycle, Optional.empty());
        visual = Objects.requireNonNullElse(visual, PartVisual.NONE);
        rarity = Objects.requireNonNullElse(rarity, Optional.empty());
    }

    public PartBasics(double massGrams, int stackSize, int sortOrder, boolean creativeListed, double durability, Optional<RecycleYield> recycle,
                      PartVisual visual) {
        this(massGrams, stackSize, sortOrder, creativeListed, durability, recycle, visual, Optional.empty());
    }

    public static PartBasics of(double massGrams) {
        return new PartBasics(massGrams, 0, DEFAULT_SORT_ORDER, true, 1.0, Optional.empty(), PartVisual.NONE, Optional.empty());
    }

    public static PartBasics withMass(PartBasics base, double massGrams) {
        return base == null ? of(massGrams)
                : new PartBasics(massGrams, base.stackSize, base.sortOrder, base.creativeListed, base.durability, base.recycle, base.visual,
                base.rarity);
    }

    public PartBasics withStackSize(int value) {
        return new PartBasics(this.massGrams, value, this.sortOrder, this.creativeListed, this.durability, this.recycle, this.visual, this.rarity);
    }

    public PartBasics withSortOrder(int value) {
        return new PartBasics(this.massGrams, this.stackSize, value, this.creativeListed, this.durability, this.recycle, this.visual, this.rarity);
    }

    public PartBasics withCreativeListed(boolean value) {
        return new PartBasics(this.massGrams, this.stackSize, this.sortOrder, value, this.durability, this.recycle, this.visual, this.rarity);
    }

    public PartBasics withDurability(double value) {
        return new PartBasics(this.massGrams, this.stackSize, this.sortOrder, this.creativeListed, value, this.recycle, this.visual, this.rarity);
    }

    public PartBasics withRecycle(RecycleYield value) {
        return new PartBasics(this.massGrams, this.stackSize, this.sortOrder, this.creativeListed, this.durability, Optional.of(value),
                this.visual, this.rarity);
    }

    public PartBasics withVisual(PartVisual value) {
        return new PartBasics(this.massGrams, this.stackSize, this.sortOrder, this.creativeListed, this.durability, this.recycle, value,
                this.rarity);
    }

    public PartBasics withRarity(Rarity value) {
        return new PartBasics(this.massGrams, this.stackSize, this.sortOrder, this.creativeListed, this.durability, this.recycle, this.visual,
                Optional.of(Objects.requireNonNull(value, "rarity")));
    }
}
