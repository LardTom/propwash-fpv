package de.lardtom.propwash.share.client.chat;

import de.lardtom.propwash.creative.CreativeParam;
import de.lardtom.propwash.creative.CreativeShareCode;
import de.lardtom.propwash.creative.CreativeSpec;
import de.lardtom.propwash.share.Base62;
import de.lardtom.propwash.share.LayerCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatCodeFinderTest {
    static final String PW3 = "PW3-02lVAldkcxCBH";
    static final String PW3_JMP = "PW3-012k1VwVtRUzVyI1ZQauW";
    static final String PW2 = "PW205zwQBrAS4";
    static final String PW2_CHECKSUM = "PW205zwQCrAS4";
    static final String PW1 = "PW1-04002-GR11D-K74SB-5EDT7-JV35B-WTG22-3KEHG-P6TTZ-6CR02-2KD68-S30DT-Z64VK-AC010-SR3AC-AZ6DH-022V1-DSGPR-VV7BX-S62RV-50446"
            + "-4DKKB-WRK6C-1G000-H23NQ";

    private static List<String> codes(String text) {
        return ChatCodeFinder.find(text).stream().map(ChatCodeFinder.Found::code).toList();
    }

    @Test
    void findsCodeInsideALine() {
        String line = "<Alex> look at this: " + PW3 + " nice";
        List<ChatCodeFinder.Found> found = ChatCodeFinder.find(line);
        assertEquals(1, found.size());
        ChatCodeFinder.Found code = found.getFirst();
        assertEquals(PW3, code.code());
        assertEquals("PW3", code.format());
        assertEquals(line.indexOf(PW3), code.start());
        assertEquals(line.indexOf(PW3) + PW3.length(), code.end());
    }

    @Test
    void edgesOfTheLine() {
        assertEquals(List.of(PW3), codes(PW3));
        assertEquals(List.of(PW3), codes(PW3 + "."));
        assertEquals(List.of(PW3), codes("(" + PW3 + ")"));
        assertEquals(List.of(PW3), codes("„" + PW3 + "“!"));
        assertEquals(List.of(PW3), codes("<" + PW3 + ">,"));
        assertEquals(List.of(PW3), codes("code:" + PW3));
        assertEquals(List.of(PW3), codes("[" + PW3 + "]-mehr"));
        assertEquals(List.of(PW3), codes("\n" + PW3 + "\t"));
    }

    @Test
    void codeGluedToAWordIsNotACode() {
        assertTrue(codes("x" + PW3).isEmpty());
        assertTrue(codes("ä" + PW3).isEmpty());
        assertTrue(codes("_" + PW3).isEmpty());
        assertTrue(codes(PW3 + "ä").isEmpty());
        assertTrue(codes(PW3 + "_x").isEmpty());
        assertTrue(codes(PW3 + "x").isEmpty());
    }

    @Test
    void prefixVariantsLikeTheImport() {
        assertEquals(List.of("pw3-02lVAldkcxCBH"), codes("pw3-02lVAldkcxCBH"));
        assertEquals(List.of("PW302lVAldkcxCBH"), codes("PW302lVAldkcxCBH"));
        assertTrue(codes("02lVAldkcxCBH").isEmpty());
        assertTrue(codes("PW3-").isEmpty());
        assertTrue(codes("PW4-02lVAldkcxCBH").isEmpty());
    }

    @Test
    void severalCodesInOneLine() {
        String line = "mine " + PW3 + ", old " + PW2 + " and older " + PW1 + " done";
        List<ChatCodeFinder.Found> found = ChatCodeFinder.find(line);
        assertEquals(List.of(PW3, PW2, PW1), found.stream().map(ChatCodeFinder.Found::code).toList());
        assertEquals(List.of("PW3", "PW2", "PW1"), found.stream().map(ChatCodeFinder.Found::format).toList());
    }

    @Test
    void atMostThreeCodesPerLine() {
        String line = PW3 + " " + PW3_JMP + " " + PW2 + " " + PW1;
        assertEquals(List.of(PW3, PW3_JMP, PW2), codes(line));
    }

    @Test
    void truncatedCodesAreIgnored() {
        assertTrue(codes(PW3.substring(0, PW3.length() - 1)).isEmpty());
        assertTrue(codes("PW3-" + PW3.substring(5)).isEmpty());
        assertTrue(codes(PW3_JMP.substring(0, 12) + " " + PW3_JMP.substring(12)).isEmpty());
        assertTrue(codes(PW2.substring(0, PW2.length() - 2)).isEmpty());
        assertTrue(codes(PW1.substring(0, PW1.length() - 6)).isEmpty());
        assertTrue(codes(PW1.substring(0, PW1.length() - 2)).isEmpty());
    }

    @Test
    void wrongChecksumIsIgnored() {
        assertTrue(codes(PW2_CHECKSUM).isEmpty());
        char last = PW3.charAt(PW3.length() - 1);
        assertTrue(codes(PW3.substring(0, PW3.length() - 1) + (last == 'H' ? 'G' : 'H')).isEmpty());
        assertTrue(codes(PW1.replace("GR11D", "GR12D")).isEmpty());
    }

    @Test
    void legacyCodes() {
        assertEquals(List.of(PW2), codes("alt: " + PW2));
        assertEquals(List.of("pw2" + PW2.substring(3)), codes("pw2" + PW2.substring(3)));
        assertEquals(List.of(PW1), codes(PW1));
        assertEquals(List.of(PW1.toLowerCase(java.util.Locale.ROOT)), codes(PW1.toLowerCase(java.util.Locale.ROOT)));
        assertEquals(List.of(PW1), codes(PW1 + "-danke"));
        assertEquals(List.of(PW1), codes(PW1 + "."));
        assertEquals(List.of(PW1.replace("PW1-", "PW1")), codes(PW1.replace("PW1-", "PW1")));
    }

    @Test
    void lengthAndCandidatesAreLimited() {
        StringBuilder huge = new StringBuilder("PW3-");
        for (int i = 0; i < ChatCodeFinder.MAX_CHARS; i++) {
            huge.append('a');
        }
        assertTrue(codes(huge.toString()).isEmpty());
        StringBuilder noise = new StringBuilder();
        for (int i = 0; i < ChatCodeFinder.MAX_CANDIDATES; i++) {
            noise.append("PW3-abc ");
        }
        assertTrue(codes(noise + PW3).isEmpty());
        assertEquals(List.of(PW3), codes("PW3-abc " + PW3));
    }

    @Test
    void framedButUnreadableCodeStillCounts() {
        String framed = "PW3-" + Base62.encode(LayerCodec.frame(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x13}));
        assertEquals(List.of(framed), codes("kaputt " + framed));
    }

    @Test
    void quickCheck() {
        assertTrue(ChatCodeFinder.mayContain("x pw2y"));
        assertTrue(ChatCodeFinder.mayContain(PW1));
        assertFalse(ChatCodeFinder.mayContain("PW4 PW0 P W3"));
        assertFalse(ChatCodeFinder.mayContain(null));
        assertTrue(codes(null).isEmpty());
    }

    @Test
    void oldDroneEditorCodesAreFoundToo() {
        String pwd1 = CreativeShareCode.encode(CreativeSpec.EMPTY.with(CreativeParam.MOTOR_KV, 2600.0).with(CreativeParam.PROP_THRUST_COEFFICIENT, 0.12));
        List<ChatCodeFinder.Found> found = ChatCodeFinder.find("<Sam> editor: " + pwd1 + " – try it");
        assertEquals(1, found.size());
        assertEquals(pwd1, found.getFirst().code());
        assertEquals("PWD1", found.getFirst().format());
        assertEquals(List.of(pwd1), codes(pwd1 + "-"));
        assertEquals(List.of(pwd1, PW3), codes(pwd1 + " " + PW3));
        assertTrue(ChatCodeFinder.mayContain("x " + pwd1));
        assertTrue(codes("PWD1-abc").isEmpty());
        assertTrue(codes("x" + pwd1).isEmpty());
        assertTrue(codes(pwd1.substring(0, pwd1.length() - 2)).isEmpty());
    }
}
