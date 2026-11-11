package de.lardtom.propwash.sim;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoldenFileTest {
    private static Map<String, String> actual(String... pairs) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put(pairs[i], pairs[i + 1]);
        }
        return out;
    }

    @Test
    void architecturesAreNamedLikeTheJvmDoes() {
        assertEquals(GoldenFile.Arch.AARCH64, GoldenFile.Arch.of("aarch64"));
        assertEquals(GoldenFile.Arch.AARCH64, GoldenFile.Arch.of("arm64"));
        assertEquals(GoldenFile.Arch.X86_64, GoldenFile.Arch.of("x86_64"));
        assertEquals(GoldenFile.Arch.X86_64, GoldenFile.Arch.of("AMD64"));
        assertEquals(GoldenFile.Arch.OTHER, GoldenFile.Arch.of("riscv64"));
        assertEquals(GoldenFile.Arch.OTHER, GoldenFile.Arch.of(null));
    }

    @Test
    void secondColumnOnlyCountsOnX86() {
        Map<String, GoldenFile.Entry> golden = GoldenFile.parse(List.of("acro:a 111 222", "", "analysis:a  333"));
        assertEquals(List.of("acro:a", "analysis:a"), List.copyOf(golden.keySet()));
        assertEquals("111", golden.get("acro:a").expected(GoldenFile.Arch.AARCH64));
        assertEquals("222", golden.get("acro:a").expected(GoldenFile.Arch.X86_64));
        assertEquals("333", golden.get("analysis:a").expected(GoldenFile.Arch.X86_64));
        Map<String, String> arm = actual("acro:a", "111", "analysis:a", "333");
        Map<String, String> x86 = actual("acro:a", "222", "analysis:a", "333");
        assertEquals(List.of(), GoldenFile.changed(golden, arm, GoldenFile.Arch.AARCH64));
        assertEquals(List.of(), GoldenFile.changed(golden, x86, GoldenFile.Arch.X86_64));
        assertEquals(List.of("acro:a"), GoldenFile.changed(golden, x86, GoldenFile.Arch.AARCH64));
        assertEquals(List.of("acro:a"), GoldenFile.changed(golden, arm, GoldenFile.Arch.X86_64));
        assertThrows(IllegalStateException.class, () -> GoldenFile.parse(List.of("acro:a 1 2 3")));
        assertThrows(IllegalStateException.class, () -> GoldenFile.parse(List.of("acro:a")));
    }

    @Test
    void updatesKeepTheReferenceColumnOfAarch64() {
        Map<String, GoldenFile.Entry> golden = GoldenFile.parse(List.of("acro:a 111 222", "angle:a 444", "drop:a 555 666"));
        Map<String, GoldenFile.Entry> x86 = GoldenFile.updated(golden, actual("acro:a", "111", "angle:a", "777", "drop:a", "666"),
                GoldenFile.Arch.X86_64);
        assertEquals("acro:a 111\nangle:a 444 777\ndrop:a 555 666\n", GoldenFile.format(x86));
        Map<String, GoldenFile.Entry> arm = GoldenFile.updated(golden, actual("acro:a", "888", "drop:a", "555", "new:a", "999"),
                GoldenFile.Arch.AARCH64);
        assertEquals("acro:a 888\ndrop:a 555 666\nnew:a 999\n", GoldenFile.format(arm));
        assertNull(arm.get("acro:a").x86());
        assertEquals(golden, GoldenFile.parse(GoldenFile.format(golden).lines().toList()));
    }
}
