package de.lardtom.propwash.server.client;

import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.apiimpl.ExtensionTabs;
import de.lardtom.propwash.join.AddonInfo;
import de.lardtom.propwash.join.LocalJoinInfo;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.server.AddonWorldRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRule;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class AddonRuleSections {
    public static final String SECTION_KEY_SUFFIX = ".world_rules.section";

    private AddonRuleSections() {
    }

    public static void init() {
        AddonWorldRules.clientValues(ClientWorldRules.INSTANCE::raw);
    }

    public static void split(Map<String, List<GameRule<?>>> groups) {
        List<GameRule<?>> other = groups.get(WorldRulesTab.OTHER_GROUP);
        if (other == null || other.isEmpty()) {
            return;
        }
        Map<Identifier, GameRule<?>> addon = new HashMap<>();
        for (GameRule<?> rule : other) {
            Identifier id = BuiltInRegistries.GAME_RULE.getKey(rule);
            if (id != null && AddonWorldRules.isAddonRule(id)) {
                addon.put(id, rule);
            }
        }
        if (addon.isEmpty()) {
            return;
        }
        List<Identifier> order = new ArrayList<>();
        for (WorldRule<?> rule : AddonWorldRules.registered()) {
            if (addon.containsKey(rule.id())) {
                order.add(rule.id());
            }
        }
        other.removeAll(addon.values());
        for (Map.Entry<String, List<Identifier>> section : sections(order, AddonRuleSections::sectionTitle, groups.keySet()).entrySet()) {
            List<GameRule<?>> rules = new ArrayList<>();
            for (Identifier id : section.getValue()) {
                rules.add(addon.get(id));
            }
            groups.put(section.getKey(), rules);
        }
    }

    public static Map<String, List<Identifier>> sections(List<Identifier> ids, Function<String, String> names, Collection<String> taken) {
        Map<String, List<Identifier>> byNamespace = new LinkedHashMap<>();
        for (Identifier id : ids) {
            byNamespace.computeIfAbsent(id.getNamespace(), key -> new ArrayList<>()).add(id);
        }
        Map<String, String> titles = new HashMap<>();
        for (String namespace : byNamespace.keySet()) {
            titles.put(namespace, names.apply(namespace));
        }
        List<String> namespaces = new ArrayList<>(byNamespace.keySet());
        namespaces.sort(Comparator.comparing((String namespace) -> titles.get(namespace).toLowerCase(Locale.ROOT)).thenComparing(namespace -> namespace));
        Set<String> used = new HashSet<>(taken);
        Map<String, List<Identifier>> out = new LinkedHashMap<>();
        for (String namespace : namespaces) {
            String key = escape(titles.get(namespace));
            if (!used.add(key)) {
                key = escape(titles.get(namespace) + " (" + namespace + ")");
                used.add(key);
            }
            out.put(key, List.copyOf(byNamespace.get(namespace)));
        }
        return out;
    }

    static String escape(String title) {
        return title.replace("%", "%%");
    }

    public static String sectionKey(String namespace) {
        return namespace + SECTION_KEY_SUFFIX;
    }

    public static String sectionTitle(String namespace) {
        Language language = Language.getInstance();
        return sectionTitle(namespace, key -> language.has(key) ? language.getOrDefault(key) : null, AddonRuleSections::modName);
    }

    static String sectionTitle(String namespace, Function<String, @Nullable String> translations, Function<String, String> modNames) {
        String translated = translations.apply(sectionKey(namespace));
        return translated == null || translated.isBlank() ? modNames.apply(namespace) : translated.strip();
    }

    public static String modName(String namespace) {
        String name = Services.PLATFORM.getModName(namespace).orElse("");
        if (name.isBlank()) {
            for (AddonInfo addon : LocalJoinInfo.get().addons()) {
                if (addon.modId().equals(namespace) || addon.namespaces().contains(namespace)) {
                    name = addon.name();
                    break;
                }
            }
        }
        return ExtensionTabs.displayName(name, namespace);
    }
}
