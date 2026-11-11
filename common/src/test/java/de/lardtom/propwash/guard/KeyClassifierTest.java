package de.lardtom.propwash.guard;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyClassifierTest {
    private static final Set<String> OWN = Set.of("propwash.osd.title", "key.propwash.drone_arm", "item.propwash.goggles",
            "key.category.propwash.main", "death.attack.propwash.drone_strike", "tag.item.propwash.parts.frame");

    @Test
    void namespaceSegmentPropwashIsReserved() {
        for (String key : new String[]{"propwash.x", "key.propwash.x", "item.propwash.x", "key.category.propwash", "part.propwash.motor",
                "gamerule.propwash.allow_reset", "death.attack.propwash.x", "tag.item.propwash.parts.motor", "propwash"}) {
            assertTrue(KeyClassifier.reserved(key), key);
        }
    }

    @Test
    void otherKeysAreNotReserved() {
        for (String key : new String[]{"gui.propwashy", "propwashy.x", "item.minecraft.stick", "justmoreparts.part.frame", "key.justmoreparts.x",
                "a.b.c.propwash", "item.propwash_example.box", "", "key.category.propwash_example"}) {
            assertFalse(KeyClassifier.reserved(key), key);
        }
    }

    @Test
    void knownKeyIsAnOverrideAndUnknownKeyIsAFinding() {
        assertEquals(KeyClassifier.Result.OVERRIDE, KeyClassifier.classify("propwash.osd.title", OWN));
        assertEquals(KeyClassifier.Result.OVERRIDE, KeyClassifier.classify("key.propwash.drone_arm", OWN));
        assertEquals(KeyClassifier.Result.FINDING, KeyClassifier.classify("propwash.nsprobe.text", OWN));
        assertEquals(KeyClassifier.Result.FINDING, KeyClassifier.classify("key.propwash.nsprobe", OWN));
        assertEquals(KeyClassifier.Result.FINDING, KeyClassifier.classify("item.propwash.nsprobe_item", OWN));
        assertEquals(KeyClassifier.Result.OTHER, KeyClassifier.classify("gui.propwashy", OWN));
        assertEquals(KeyClassifier.Result.OTHER, KeyClassifier.classify("justmoreparts.share.title", OWN));
    }
}
