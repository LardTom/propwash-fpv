package de.lardtom.propwash.build.placeholder;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.Accessories;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.core.DroneBuildCodecs;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderSlotTest {
    private static final String GONE = "gone_addon";

    private static Identifier gone(String path) {
        return Identifier.fromNamespaceAndPath(GONE, path);
    }

    private static JsonObject freestyle() {
        return DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, DroneBuild.FREESTYLE).getOrThrow().getAsJsonObject();
    }

    private static JsonObject withAccessories(JsonObject build, Map<String, String> accessories) {
        JsonObject json = new JsonObject();
        accessories.forEach(json::addProperty);
        build.add("accessories", json);
        return build;
    }

    private static DroneBuild parse(JsonObject json) {
        return DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static byte[] wire(DroneBuild build) {
        ByteBuf buffer = Unpooled.buffer();
        DroneBuildCodecs.STREAM_CODEC.encode(buffer, build);
        byte[] out = new byte[buffer.readableBytes()];
        buffer.readBytes(out);
        return out;
    }

    @ParameterizedTest
    @EnumSource(AccessorySlot.class)
    void everyMountGetsAPlaceholderThatFitsIt(AccessorySlot slot) {
        AccessoryMount mount = AccessoryMount.of(slot);
        AccessoryType placeholder = (AccessoryType) PartIndex.missing(PartKind.ACCESSORY, gone("part_" + slot.key()), slot);
        assertTrue(placeholder.missing());
        assertEquals(gone("part_" + slot.key()), placeholder.id());
        assertEquals(slot, placeholder.definition().slot());
        assertEquals(mount, placeholder.mount);
        assertTrue(placeholder.fits(mount));
        assertSame(placeholder, PartIndex.missing(PartKind.ACCESSORY, gone("part_" + slot.key()), slot));
        assertSame(placeholder, Accessories.NONE.with(placeholder).at(mount).orElseThrow());
        AccessoryType substitute = (AccessoryType) PartIndex.substitute(PartKind.ACCESSORY, slot);
        assertEquals(slot == AccessorySlot.TOP ? AccessoryType.ACTION_CAM_LIGHT : AccessoryType.LED_STRIP, substitute);
        assertEquals(substitute.widthMm, placeholder.widthMm);
        assertEquals(substitute.massGrams(), placeholder.massGrams());
    }

    @Test
    void theSameIdGetsOnePlaceholderPerMount() {
        Identifier id = gone("shared");
        AccessoryType front = (AccessoryType) PartIndex.missing(PartKind.ACCESSORY, id, AccessorySlot.FRONT);
        AccessoryType bottom = (AccessoryType) PartIndex.missing(PartKind.ACCESSORY, id, AccessorySlot.BOTTOM);
        assertEquals(AccessoryMount.FRONT, front.mount);
        assertEquals(AccessoryMount.BOTTOM, bottom.mount);
        assertSame(bottom, PartIndex.missing(PartKind.ACCESSORY, id));
    }

    @Test
    void foreignAccessoriesAtFrontTopAndBottomSurviveSaveAndWire() {
        JsonObject json = withAccessories(freestyle(), Map.of("front", GONE + ":dispenser", "top", GONE + ":booster",
                "bottom", GONE + ":spear"));
        DroneBuild build = parse(json);
        assertEquals(3, build.accessories().size());
        for (AccessoryMount mount : AccessoryMount.values()) {
            AccessoryType type = build.accessories().at(mount).orElseThrow();
            assertTrue(type.missing(), mount.key());
            assertEquals(mount.slot(), type.definition().slot(), mount.key());
        }
        assertEquals("gone_addon:dispenser", build.accessories().at(AccessoryMount.FRONT).orElseThrow().key());
        assertEquals(json, DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, build).getOrThrow());
        byte[] bytes = wire(build);
        DroneBuild decoded = DroneBuildCodecs.STREAM_CODEC.decode(Unpooled.wrappedBuffer(bytes));
        assertEquals(build, decoded);
        assertArrayEquals(bytes, wire(decoded));
        assertEquals(DroneBuild.Issue.MISSING_PART, build.problems().getFirst());
    }

    @ParameterizedTest
    @EnumSource(value = PartKind.class, names = "ACCESSORY", mode = EnumSource.Mode.EXCLUDE)
    void everyPartKindKeepsItsIdWithoutTheAddon(PartKind kind) {
        JsonObject json = withAccessories(freestyle(), Map.of("front", GONE + ":front_" + kind.key()));
        json.addProperty(kind.key(), GONE + ":" + kind.key());
        DroneBuild build = parse(json);
        PartInfo part = build.parts().stream().filter(p -> p.definition().kind() == kind).findFirst().orElseThrow();
        assertTrue(part.missing(), kind.key());
        assertEquals(gone(kind.key()), part.id());
        assertSame(PartIndex.missing(kind, gone(kind.key())), part);
        assertTrue(build.accessories().at(AccessoryMount.FRONT).orElseThrow().missing());
        assertEquals(json, DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, build).getOrThrow());
        assertEquals(build, DroneBuildCodecs.STREAM_CODEC.decode(Unpooled.wrappedBuffer(wire(build))));
    }

    @Test
    void oneBrokenAccessoryNeverTakesTheOthersWithIt() {
        for (String broken : new String[]{"action_cam_heavy", "laser_turret", "Kein Schlüssel"}) {
            JsonObject json = withAccessories(freestyle(), Map.of("front", GONE + ":dispenser", "top", "action_cam_light",
                    "bottom", broken));
            DroneBuild build = parse(json);
            assertEquals(2, build.accessories().size(), broken);
            assertTrue(build.accessories().at(AccessoryMount.FRONT).orElseThrow().missing(), broken);
            assertSame(AccessoryType.ACTION_CAM_LIGHT, build.accessories().at(AccessoryMount.TOP).orElseThrow(), broken);
            assertFalse(build.accessories().at(AccessoryMount.BOTTOM).isPresent(), broken);
            assertTrue(DroneBuildCodecs.ACCESSORIES.parse(JsonOps.INSTANCE, json.get("accessories")).error().isPresent(), broken);
        }
        JsonObject sideways = withAccessories(freestyle(), Map.of("side", GONE + ":wing", "front", GONE + ":dispenser"));
        assertEquals(1, parse(sideways).accessories().size());
    }

    @Test
    void oneBrokenAccessoryOnTheWireKeepsTheRest() {
        ByteBuf buffer = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buffer, 3);
        ByteBufCodecs.stringUtf8(16).encode(buffer, "front");
        ByteBufCodecs.stringUtf8(128).encode(buffer, GONE + ":dispenser");
        ByteBufCodecs.stringUtf8(16).encode(buffer, "top");
        ByteBufCodecs.stringUtf8(128).encode(buffer, "action_cam_light");
        ByteBufCodecs.stringUtf8(16).encode(buffer, "bottom");
        ByteBufCodecs.stringUtf8(128).encode(buffer, "action_cam_heavy");
        Accessories accessories = DroneBuildCodecs.ACCESSORIES_STREAM.decode(buffer);
        assertFalse(buffer.isReadable());
        assertEquals(2, accessories.size());
        assertTrue(accessories.at(AccessoryMount.FRONT).orElseThrow().missing());
        assertSame(AccessoryType.ACTION_CAM_LIGHT, accessories.at(AccessoryMount.TOP).orElseThrow());
    }
}
