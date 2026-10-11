package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.registry.LootInjections;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LootInjectionsImpl implements LootInjections {
    public static final LootInjectionsImpl INSTANCE = new LootInjectionsImpl();

    private final Map<ResourceKey<LootTable>, List<ResourceKey<LootTable>>> byTarget = new LinkedHashMap<>();
    private boolean closed;

    LootInjectionsImpl() {
    }

    @Override
    public synchronized void inject(ResourceKey<LootTable> target, ResourceKey<LootTable> addonTable) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(addonTable, "addonTable");
        if (this.closed) {
            throw new IllegalStateException("Truhen-Beute " + addonTable.identifier() + " für " + target.identifier()
                    + " kommt zu spät: nur während der Mod-Initialisierung anmelden (Fabric onInitialize, NeoForge Mod-Konstruktor)");
        }
        String namespace = addonTable.identifier().getNamespace();
        if ("minecraft".equals(namespace) || Constants.MOD_ID.equals(namespace)) {
            throw new IllegalArgumentException("Die Beutetabelle eines Addons braucht den eigenen Namensraum: " + addonTable.identifier());
        }
        if (target.equals(addonTable)) {
            throw new IllegalArgumentException("Eine Beutetabelle kann nicht in sich selbst eingehängt werden: " + target.identifier());
        }
        List<ResourceKey<LootTable>> tables = this.byTarget.computeIfAbsent(target, key -> new ArrayList<>());
        if (tables.contains(addonTable)) {
            throw new IllegalArgumentException("Truhen-Beute doppelt angemeldet: " + addonTable.identifier() + " für " + target.identifier());
        }
        tables.add(addonTable);
    }

    @Override
    public synchronized List<ResourceKey<LootTable>> tablesFor(ResourceKey<LootTable> target) {
        List<ResourceKey<LootTable>> tables = this.byTarget.get(Objects.requireNonNull(target, "target"));
        return tables == null ? List.of() : List.copyOf(tables);
    }

    public synchronized int size() {
        int count = 0;
        for (List<ResourceKey<LootTable>> tables : this.byTarget.values()) {
            count += tables.size();
        }
        return count;
    }

    public synchronized void close() {
        this.closed = true;
    }
}
