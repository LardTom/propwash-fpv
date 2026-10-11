package de.lardtom.propwash.join;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinSafetyTest {
    @Test
    void textLosesFormattingControlAndBidiCharacters() {
        assertEquals("Evil Name", SafeText.clean("§cEvil\n‮Name\u0000", 64));
        assertEquals("abc", SafeText.clean("abcdef", 3));
        assertEquals("", SafeText.clean(null, 10));
        assertEquals("a b", SafeText.clean("a\t  b", 10));
    }

    @Test
    void onlyPlainWebLinksSurvive() {
        assertEquals("https://modrinth.com/mod/x", SafeText.url("https://modrinth.com/mod/x"));
        assertEquals("http://example.org/a?b=c", SafeText.url(" http://example.org/a?b=c "));
        assertEquals("", SafeText.url("javascript:alert(1)"));
        assertEquals("", SafeText.url("file:///etc/passwd"));
        assertEquals("", SafeText.url("https://user:pw@example.org/"));
        assertEquals("", SafeText.url("https://localhost/"));
        assertEquals("", SafeText.url("https://example.org/a b"));
        assertEquals("", SafeText.url("https://exämple.org/"));
        assertEquals("", SafeText.url("https://example.org/" + "a".repeat(300)));
        assertEquals("modrinth.com/mod/x", SafeText.shortUrl("https://www.modrinth.com/mod/x/"));
    }

    @Test
    void addonInfoIsSanitized() {
        AddonInfo addon = new AddonInfo("Bad Id", "§lName", "1.0\n", "ftp://x.org", List.of("ok_ns", "Bad NS", "ok_ns"));
        assertFalse(addon.valid());
        assertEquals("Name", addon.name());
        assertEquals("1.0", addon.version());
        assertEquals("", addon.homepage());
        assertEquals(List.of("ok_ns"), addon.namespaces());
        AddonInfo unnamed = new AddonInfo("plain", "", "1", "", List.of());
        assertEquals("plain", unnamed.displayName());
        assertTrue(unnamed.valid());
    }

    @Test
    void joinInfoDropsInvalidAndDuplicateAddons() {
        JoinInfo info = new JoinInfo("0.5.0", 13, "x", List.of(new AddonInfo("b_mod", "B", "1", "", List.of()),
                new AddonInfo("a_mod", "A", "1", "", List.of()), new AddonInfo("a_mod", "A2", "2", "", List.of()),
                new AddonInfo("", "nothing", "1", "", List.of())));
        assertEquals(2, info.addons().size());
        assertEquals("a_mod", info.addons().get(0).modId());
        assertEquals("A", info.addons().get(0).name());
    }

    @Test
    void versionsAreOrderedLikeSemver() {
        assertEquals(OptionalInt.of(-1), VersionOrder.compare("1.0.0", "1.1.0"));
        assertEquals(OptionalInt.of(1), VersionOrder.compare("1.10.0", "1.9.3"));
        assertEquals(OptionalInt.of(0), VersionOrder.compare("1.2", "1.2.0"));
        assertEquals(OptionalInt.of(-1), VersionOrder.compare("0.5.0-dev.3", "0.5.0"));
        assertEquals(OptionalInt.of(-1), VersionOrder.compare("0.5.0-dev.3", "0.5.0-dev.12"));
        assertEquals(OptionalInt.of(0), VersionOrder.compare("1.0.0+build.5", "1.0.0"));
        assertTrue(VersionOrder.compare("beta", "1.0").isEmpty());
    }
}
