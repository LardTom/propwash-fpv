package de.lardtom.propwash.input.addon;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.accessory.AccessoryBehaviors;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.input.SwitchFunction;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class AccessoryActionHints {
    public static final String NAME = "accessory_hints";
    public static final int ACTIONS = SwitchFunction.ACCESSORY_COUNT;
    public static final int MAX_PLAYERS = 32;
    public static final int MAX_TYPES = 256;
    public static final int MAX_KEY_LENGTH = 128;
    public static final JsonConfig<Seen> CONFIG = JsonConfig.create(NAME, Seen.class, Seen::new, AccessoryActionHints::sanitize);

    public static final class Seen {
        public Map<String, List<String>> players = new LinkedHashMap<>();
    }

    public record Named(Identifier accessory, Map<Integer, String> actions) {
        public Named {
            actions = Map.copyOf(actions);
        }
    }

    private AccessoryActionHints() {
    }

    public static Optional<String> nameKey(@Nullable AccessoryBehavior behavior, int number) {
        if (behavior == null || number < 1 || number > ACTIONS) {
            return Optional.empty();
        }
        try {
            Optional<String> key = behavior.actionNameKey(number);
            return key == null ? Optional.empty() : key.map(String::strip).filter(text -> !text.isEmpty());
        } catch (RuntimeException e) {
            Constants.LOG.warn("Zubehör: actionNameKey({}) fehlgeschlagen", number, e);
            return Optional.empty();
        }
    }

    public static Optional<Named> named(Identifier accessory, @Nullable AccessoryBehavior behavior) {
        Map<Integer, String> actions = new LinkedHashMap<>();
        for (int number = 1; number <= ACTIONS; number++) {
            int action = number;
            nameKey(behavior, number).ifPresent(key -> actions.put(action, key));
        }
        return actions.isEmpty() ? Optional.empty() : Optional.of(new Named(accessory, actions));
    }

    public static List<Named> named(Collection<AccessoryType> mounted) {
        List<Named> out = new ArrayList<>();
        Set<Identifier> seen = new LinkedHashSet<>();
        for (AccessoryType type : mounted) {
            AccessoryType unladen = type.unladen();
            if (seen.add(unladen.id())) {
                named(unladen.id(), AccessoryBehaviors.INSTANCE.of(unladen)).ifPresent(out::add);
            }
        }
        return out;
    }

    public static List<String> names(Collection<AccessoryType> mounted, int number) {
        List<String> keys = new ArrayList<>();
        for (Named named : named(mounted)) {
            String key = named.actions().get(number);
            if (key != null && !keys.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    public static List<String> registeredNames(int number) {
        List<String> keys = new ArrayList<>();
        for (Identifier id : AccessoryBehaviors.INSTANCE.registered()) {
            nameKey(AccessoryBehaviors.INSTANCE.behavior(id).orElse(null), number).filter(key -> !keys.contains(key)).ifPresent(keys::add);
        }
        return keys;
    }

    public static boolean fresh(Seen seen, String player, Identifier accessory) {
        List<String> types = seen.players.get(player);
        return types == null || !types.contains(accessory.toString());
    }

    public static boolean shown(Seen seen, String player, Identifier accessory) {
        if (!valid(player) || !valid(accessory.toString()) || !fresh(seen, player, accessory)) {
            return false;
        }
        List<String> types = seen.players.remove(player);
        List<String> next = types == null ? new ArrayList<>() : new ArrayList<>(types);
        next.add(accessory.toString());
        seen.players.put(player, next);
        sanitize(seen);
        return true;
    }

    public static void sanitize(Seen seen) {
        Map<String, List<String>> clean = new LinkedHashMap<>();
        if (seen.players != null) {
            for (Map.Entry<String, List<String>> player : seen.players.entrySet()) {
                if (!valid(player.getKey()) || player.getValue() == null) {
                    continue;
                }
                List<String> types = new ArrayList<>();
                for (String type : player.getValue()) {
                    if (valid(type) && !types.contains(type)) {
                        types.add(type);
                    }
                }
                if (types.size() > MAX_TYPES) {
                    types = new ArrayList<>(types.subList(types.size() - MAX_TYPES, types.size()));
                }
                clean.put(player.getKey(), types);
            }
        }
        List<String> order = new ArrayList<>(clean.keySet());
        for (int i = 0; i < order.size() - MAX_PLAYERS; i++) {
            clean.remove(order.get(i));
        }
        seen.players = clean;
    }

    private static boolean valid(@Nullable String key) {
        return key != null && !key.isEmpty() && key.length() <= MAX_KEY_LENGTH;
    }
}
