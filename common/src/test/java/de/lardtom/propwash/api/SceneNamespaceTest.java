package de.lardtom.propwash.api;

import de.lardtom.propwash.guide.GuideScenes;
import de.lardtom.propwash.guide.scene.Anchor;
import de.lardtom.propwash.guide.scene.Cue;
import de.lardtom.propwash.guide.scene.KeyToken;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneNamespaceTest {
    private static Scene scene(String id) {
        return SceneBuilder.scene(id)
                .icon("justmoreparts:xclass_13")
                .step("intro", 40.0F, s -> {
                    s.at(0.0F).callout("frame", Anchor.at(0.0F, 1.0F, 0.0F), Cue.Side.ABOVE, 0.0F);
                    s.at(10.0F).keys("look", 0.0F, KeyToken.mapping("key.sneak"));
                })
                .build();
    }

    @Test
    void addonScenesUseTheirOwnNamespaceForTexts() {
        Scene scene = scene("justmoreparts:frames");
        assertEquals("justmoreparts:frames", scene.id());
        assertEquals("justmoreparts", scene.namespace());
        assertFalse(scene.builtin());
        assertEquals("justmoreparts.guide.scene.frames.title", scene.titleKey());
        assertEquals("justmoreparts.guide.scene.frames.description", scene.descriptionKey());
        assertEquals("justmoreparts.guide.scene.frames.step.intro.title", scene.stepTitleKey(0));
        assertTrue(scene.translationKeys().contains("justmoreparts.guide.scene.frames.callout.frame"));
        assertTrue(scene.translationKeys().contains("justmoreparts.guide.scene.frames.hint.look"));
        for (String key : scene.translationKeys()) {
            assertTrue(key.startsWith("justmoreparts.guide.scene.frames."), key);
        }
    }

    @Test
    void builtinScenesKeepTheirKeys() {
        Scene plain = scene("drone_build_demo");
        assertTrue(plain.builtin());
        assertEquals("propwash", plain.namespace());
        assertEquals("propwash.guide.scene.drone_build_demo.title", plain.titleKey());
        Scene prefixed = scene("propwash:drone_build_demo");
        assertEquals("drone_build_demo", prefixed.id());
        assertEquals(plain.translationKeys(), prefixed.translationKeys());
        for (Scene builtin : GuideScenes.all()) {
            assertTrue(builtin.builtin(), builtin.id());
            assertTrue(builtin.titleKey().startsWith(Scene.KEY_PREFIX + builtin.id() + "."), builtin.titleKey());
        }
    }

    @Test
    void invalidIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> scene("Just:Frames"));
        assertThrows(IllegalArgumentException.class, () -> scene("a:b:c"));
        assertThrows(IllegalArgumentException.class, () -> scene("mod:"));
        assertThrows(IllegalArgumentException.class, () -> scene(""));
        assertNull(GuideScenes.byId("Kein gültiger Name"));
        assertNotNull(GuideScenes.byId("propwash:" + GuideScenes.all().getFirst().id()));
    }
}
