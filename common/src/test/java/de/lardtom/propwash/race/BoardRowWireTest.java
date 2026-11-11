package de.lardtom.propwash.race;

import de.lardtom.propwash.Constants;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BoardRowWireTest {
    private static final Map<Integer, String> BY_PROTOCOL = Map.of(
            17, "04416e6e6106726163655f3580d88ee16f00000199c82cc000010103800401090477696e64000100000000000012340000000000005678046162313201",
            18, "04416e6e6106726163655f3580d88ee16f00000199c82cc000010103800401090477696e6400010000000000001234000000000000567804616231320101010d"
                    + "70726f70776173683a77696e6400",
            19, "04416e6e6106726163655f3580d88ee16f00000199c82cc000010103800401090477696e6400010000000000001234000000000000567804616231320101010d"
                    + "70726f70776173683a77696e6400",
            20, "04416e6e6106726163655f3580d88ee16f00000199c82cc000010103800401090477696e6400010000000000001234000000000000567804616231320101010d"
                    + "70726f70776173683a77696e6400");

    private static RacePayloads.BoardRow sample() {
        return new RacePayloads.BoardRow("Anna", "race_5", 30_000_000_000L, 1_760_000_000_000L, true, 0, 2, LapAssist.PERSONAL_RULES.bit(),
                List.of(new AssistDetail(LapAssist.PERSONAL_RULES, "wind", 0, 1)), new UUID(0x1234L, 0x5678L), "ab12", true,
                RaceStandard.DEFAULTS.with("wind", 0));
    }

    private static String bytes(RacePayloads.BoardRow row) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RacePayloads.BoardRow.write(buf, row);
        return ByteBufUtil.hexDump(buf);
    }

    @Test
    void sameProtocolNumberMeansSameBoardRowBytes() {
        String published = BY_PROTOCOL.get(Constants.PROTOCOL_VERSION);
        assertNotNull(published, "keine Vergleichszeile für Netzprotokoll " + Constants.PROTOCOL_VERSION);
        assertEquals(published, bytes(sample()), "BoardRow hat ein anderes Format als bei Netzprotokoll " + Constants.PROTOCOL_VERSION
                + " (0.6.0-rc.2: ohne Serverregeln) – ein Client dieser Nummer würde beim Öffnen der Bestenliste mit einem Paketfehler getrennt");
    }

    @Test
    void boardRowWithServerRulesSurvivesTheWire() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        RacePayloads.BoardRow.write(buf, sample());
        assertEquals(sample(), RacePayloads.BoardRow.read(buf));
        assertEquals(0, buf.readableBytes());
    }
}
