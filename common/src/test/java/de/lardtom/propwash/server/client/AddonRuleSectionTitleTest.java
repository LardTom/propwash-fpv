package de.lardtom.propwash.server.client;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AddonRuleSectionTitleTest {
    private static final Function<String, String> NO_MOD_NAME = namespace -> {
        throw new AssertionError("Mod-Name für " + namespace + " gefragt, obwohl es eine Übersetzung gibt");
    };

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    @Test
    void keyIsTheNamespaceWithWorldRulesSection() {
        assertEquals("camdrones.world_rules.section", AddonRuleSections.sectionKey("camdrones"));
        assertEquals("mobdefense.world_rules.section", AddonRuleSections.sectionKey("mobdefense"));
    }

    @Test
    void translationWinsOverTheModName() {
        Map<String, String> texts = Map.of("camdrones.world_rules.section", "Kamera-Drohnen");
        assertEquals("Kamera-Drohnen", AddonRuleSections.sectionTitle("camdrones", texts::get, NO_MOD_NAME));
    }

    @Test
    void withoutTranslationTheModNameStays() {
        Map<String, String> texts = Map.of("camdrones.world_rules.section", "Kamera-Drohnen", "blank.world_rules.section", "  ");
        assertEquals("Mob Defense", AddonRuleSections.sectionTitle("mobdefense", texts::get, namespace -> "Mob Defense"));
        assertEquals("blank", AddonRuleSections.sectionTitle("blank", texts::get, namespace -> namespace));
        assertEquals("Kamera-Drohnen", AddonRuleSections.sectionTitle("camdrones", key -> texts.get(key) == null ? null : " " + texts.get(key) + " ",
                NO_MOD_NAME));
    }

    @Test
    void translatedTitlesDecideTheOrderAndKeepPercentSigns() {
        Map<String, String> texts = Map.of("mobdefense.world_rules.section", "Abwehr", "hundred.world_rules.section", "100% Drohnen");
        Map<String, String> mods = Map.of("mobdefense", "Mob Defense", "camdrones", "Camera Drones", "hundred", "Hundred");
        List<Identifier> ids = List.of(id("camdrones", "max_waypoints"), id("mobdefense", "cooldown_ticks"), id("hundred", "x"));
        Map<String, List<Identifier>> sections = AddonRuleSections.sections(ids,
                namespace -> AddonRuleSections.sectionTitle(namespace, texts::get, mods::get), Set.of(WorldRulesTab.OTHER_GROUP));
        assertEquals(List.of("100%% Drohnen", "Abwehr", "Camera Drones"), List.copyOf(sections.keySet()));
        assertEquals(List.of(id("mobdefense", "cooldown_ticks")), sections.get("Abwehr"));
    }

    @Test
    void gameLanguageSuppliesTheTitle() {
        Language previous = Language.getInstance();
        Map<String, String> texts = Map.of("camdrones.world_rules.section", "Kamera-Drohnen");
        Language.inject(new Language() {
            @Override
            public String getOrDefault(String key, String fallback) {
                return texts.getOrDefault(key, fallback);
            }

            @Override
            public boolean has(String key) {
                return texts.containsKey(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return previous.getVisualOrder(text);
            }
        });
        try {
            assertEquals("Kamera-Drohnen", AddonRuleSections.sectionTitle("camdrones"));
        } finally {
            Language.inject(previous);
        }
    }
}
