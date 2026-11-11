package de.lardtom.propwash.share.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.share.ShareServer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record ShoppingList(List<Entry> entries) {
    public ShoppingList {
        entries = List.copyOf(entries);
    }

    public record Entry(PartKind kind, Identifier id, int needed, int owned, boolean unknown) {
        public int missing() {
            return Math.max(0, this.needed - this.owned);
        }
    }

    private static LinkedHashMap<Identifier, Entry> needed(BuildSpec build, Collection<Identifier> unknown) {
        LinkedHashMap<Identifier, Entry> needed = new LinkedHashMap<>();
        for (ShareServer.Slot slot : ShareServer.slots(build)) {
            Entry previous = needed.get(slot.id());
            int count = slot.count() + (previous == null ? 0 : previous.needed());
            needed.put(slot.id(), new Entry(slot.kind(), slot.id(), count, 0, unknown.contains(slot.id())));
        }
        return needed;
    }

    public static ShoppingList of(BuildSpec build, @Nullable Inventory inventory, @Nullable WorkbenchContext bench, Collection<Identifier> unknown) {
        Map<Identifier, Integer> owned = new HashMap<>();
        if (inventory != null) {
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }
                Optional<Identifier> part = PropwashApi.partItems().partOf(stack);
                part.ifPresent(id -> owned.merge(id, stack.getCount(), Integer::sum));
            }
        }
        if (bench != null) {
            for (PartKind kind : PartKind.values()) {
                for (int position = 0; position < kind.slotCount(); position++) {
                    bench.slot(kind, position).ifPresent(id -> owned.merge(id, 1, Integer::sum));
                }
            }
        }
        List<Entry> out = new ArrayList<>();
        for (Entry entry : needed(build, unknown).values()) {
            int have = entry.unknown() ? 0 : owned.getOrDefault(entry.id(), 0);
            out.add(new Entry(entry.kind(), entry.id(), entry.needed(), have, entry.unknown()));
        }
        return new ShoppingList(out);
    }

    public static ShoppingList fromMissing(BuildSpec build, List<Identifier> missing) {
        Map<Identifier, Integer> lacking = count(missing);
        List<Entry> out = new ArrayList<>();
        for (Entry entry : needed(build, Set.of()).values()) {
            int gone = Math.min(entry.needed(), lacking.getOrDefault(entry.id(), 0));
            out.add(new Entry(entry.kind(), entry.id(), entry.needed(), entry.needed() - gone, false));
        }
        return new ShoppingList(out);
    }

    public int missingPieces() {
        int total = 0;
        for (Entry entry : this.entries) {
            total += entry.missing();
        }
        return total;
    }

    public boolean complete() {
        return this.missingPieces() == 0;
    }

    public boolean hasUnknown() {
        for (Entry entry : this.entries) {
            if (entry.unknown()) {
                return true;
            }
        }
        return false;
    }

    public static Map<Identifier, Integer> count(List<Identifier> pieces) {
        LinkedHashMap<Identifier, Integer> out = new LinkedHashMap<>();
        for (Identifier id : pieces) {
            out.merge(id, 1, Integer::sum);
        }
        return out;
    }
}
