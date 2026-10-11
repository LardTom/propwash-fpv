package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.ImportTarget;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportWireTest {
    private static final Identifier ADDON_FRAME = Identifier.fromNamespaceAndPath("justmoreparts", "xclass_13");

    private static ImportRequest sample() {
        BuildSpec build = Conversions.spec(DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_LIGHT))
                .withAccessory(AccessorySlot.BOTTOM, AccessoryType.LED_STRIP.id());
        DroneData data = new DroneData(build.withPart(PartKind.FRAME, ADDON_FRAME),
                new PaintSpec(Map.of("frame", 0x1B1D22, "prop_fl", 0xFFC23D)),
                new TuneSpec(Map.of("rates.roll.rcRate", 1.1F, "pid.roll.p", 50.0F, "vtx.channel", 4.0F)), Optional.of("Bärenstark 13″"));
        return new ImportRequest(data, EnumSet.of(ImportLayer.PARTS, ImportLayer.TUNE), EnumSet.of(TuneGroup.RATES, TuneGroup.PID),
                ImportTarget.WORKBENCH);
    }

    private static <T> T viaBuffer(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buffer = Unpooled.buffer();
        codec.encode(buffer, value);
        T decoded = codec.decode(buffer);
        assertEquals(0, buffer.readableBytes());
        return decoded;
    }

    @Test
    void requestSurvivesTheWire() {
        ImportRequest request = sample();
        ImportWire wire = ImportWire.of(request);
        assertEquals(request, wire.toRequest());
        ImportWire decoded = viaBuffer(ImportWire.CODEC, wire);
        assertEquals(wire, decoded);
        assertEquals(request, decoded.toRequest());
        ImportRequest held = ImportRequest.tuneOnHeldDrone(DroneData.of(Conversions.spec(DroneBuild.WHOOP)));
        assertEquals(held, viaBuffer(ImportWire.CODEC, ImportWire.of(held)).toRequest());
    }

    @Test
    void payloadsSurviveTheWire() {
        ImportPayloads.ImportC2S c2s = new ImportPayloads.ImportC2S(17, 3, ImportWire.of(sample()));
        assertEquals(c2s, viaBuffer(ImportPayloads.ImportC2S.CODEC, c2s));
        ImportResult result = new ImportResult(ImportStatus.MISSING_PARTS, EnumSet.of(ImportLayer.PAINT), List.of(ADDON_FRAME),
                Map.of("minecraft:red_dye", 2));
        ImportPayloads.ImportResultS2C s2c = ImportPayloads.ImportResultS2C.of(17, result);
        ImportPayloads.ImportResultS2C back = viaBuffer(ImportPayloads.ImportResultS2C.CODEC, s2c);
        assertEquals(17, back.requestId());
        assertEquals(result, back.result());
        assertEquals(ImportResult.of(ImportStatus.BUSY), ImportPayloads.ImportResultS2C.of(1, ImportResult.of(ImportStatus.BUSY)).result());
    }

    @Test
    void corruptWireDataIsInvalidNotFatal() {
        ImportWire good = ImportWire.of(sample());
        assertThrows(IllegalArgumentException.class, () -> new ImportWire(9, good.layers(), good.tuneGroups(), good.parts(), good.accessories(),
                good.paint(), good.tune(), good.name()).toRequest());
        assertThrows(IllegalArgumentException.class, () -> new ImportWire(good.target(), 0, good.tuneGroups(), good.parts(), good.accessories(),
                good.paint(), good.tune(), good.name()).toRequest());
        assertThrows(IllegalArgumentException.class, () -> new ImportWire(good.target(), 1 << 20, good.tuneGroups(), good.parts(),
                good.accessories(), good.paint(), good.tune(), good.name()).toRequest());
        assertThrows(IllegalArgumentException.class, () -> new ImportWire(good.target(), good.layers(), good.tuneGroups(), good.parts().subList(0, 5),
                good.accessories(), good.paint(), good.tune(), good.name()).toRequest());
        assertThrows(IllegalArgumentException.class, () -> new ImportWire(good.target(), good.layers(), good.tuneGroups(),
                List.of("Groß:Buchstaben", "a", "b", "c", "d", "e"), good.accessories(), good.paint(), good.tune(), good.name()).toRequest());
        assertThrows(IllegalArgumentException.class, () -> new ImportWire(good.target(), good.layers(), good.tuneGroups(), good.parts(),
                Map.of("side", "propwash:led_strip"), good.paint(), good.tune(), good.name()).toRequest());
        assertThrows(IllegalArgumentException.class, () -> new ImportWire(good.target(), good.layers(), good.tuneGroups(), good.parts(),
                good.accessories(), Map.of("wings", 0xFFFFFF), good.tune(), good.name()).toRequest());
        assertThrows(IllegalArgumentException.class, () -> new ImportWire(good.target(), good.layers(), good.tuneGroups(), good.parts(),
                good.accessories(), good.paint(), Map.of("rates.roll.rcRate", Float.NaN), good.name()).toRequest());
        assertThrows(IllegalArgumentException.class, () -> new ImportWire(good.target(), good.layers(), good.tuneGroups(), good.parts(),
                good.accessories(), good.paint(), good.tune(), "x".repeat(60)).toRequest());
        assertThrows(IllegalArgumentException.class, () -> new ImportWire(ImportTarget.HELD_DRONE.ordinal(), good.layers(), good.tuneGroups(),
                good.parts(), good.accessories(), good.paint(), good.tune(), good.name()).toRequest());
    }

    @Test
    void typicalRequestsStayFarBelowThePayloadLimit() {
        TreeMap<String, Float> big = new TreeMap<>();
        for (int i = 0; i < TuneSpec.MAX_ENTRIES; i++) {
            big.put("pid.custom.key.number." + i, (float) i);
        }
        ImportRequest request = new ImportRequest(sample().data().withTune(new TuneSpec(big)), sample().layers(), sample().tuneGroups(),
                ImportTarget.WORKBENCH);
        int bytes = ImportWire.of(request).encodedBytes();
        assertTrue(bytes < ImportWire.MAX_ENCODED_BYTES, bytes + " Bytes");
        assertTrue(ImportWire.of(sample()).encodedBytes() < 1000);
    }

    @Test
    void bitmasksFollowTheEnumOrder() {
        assertEquals(0b101, ImportWire.mask(EnumSet.of(ImportLayer.PARTS, ImportLayer.TUNE)));
        assertEquals(EnumSet.of(ImportLayer.PAINT), ImportWire.unmask(ImportLayer.class, 0b010));
        assertEquals(TuneGroup.FLIGHT, ImportWire.unmask(TuneGroup.class, ImportWire.mask(TuneGroup.FLIGHT)));
        assertThrows(IllegalArgumentException.class, () -> ImportWire.unmask(ImportLayer.class, 0b10000));
        assertThrows(IllegalArgumentException.class, () -> ImportWire.unmask(ImportLayer.class, -1));
    }
}
