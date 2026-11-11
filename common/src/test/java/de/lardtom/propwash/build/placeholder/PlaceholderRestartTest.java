package de.lardtom.propwash.build.placeholder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaceholderRestartTest {
    @Test
    void addonRemovedAndBackKeepsEveryPartAtEveryMount() {
        String saved = Restart.run("withAddon", "");
        String withoutAddon = Restart.run("withoutAddon", saved);
        String twiceWithout = Restart.run("withoutAddon", withoutAddon);
        assertEquals("ok", Restart.run("addonBack", twiceWithout));
    }
}
