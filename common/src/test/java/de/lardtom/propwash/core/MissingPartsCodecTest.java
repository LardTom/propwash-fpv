package de.lardtom.propwash.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.Accessories;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PartNames;
import de.lardtom.propwash.build.VideoSystem;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissingPartsCodecTest {
    private static JsonObject freestyleWith(String field, String key) {
        JsonObject json = DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, DroneBuild.FREESTYLE).getOrThrow().getAsJsonObject();
        json.addProperty(field, key);
        return json;
    }

    private static byte[] bytes(ByteBuf buffer) {
        byte[] out = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), out);
        return out;
    }

    @Test
    void unknownAddonPartsBecomePlaceholdersThatKeepTheirId() {
        JsonObject json = freestyleWith("motor", "justmoreparts:m2806_1300");
        DroneBuild build = DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        MotorType motor = build.motor();
        assertTrue(motor.missing());
        assertEquals(PartIndex.MISSING_NAME, motor.name());
        assertEquals("justmoreparts:m2806_1300", motor.key());
        assertEquals(MotorType.M2207_1750.kv, motor.kv);
        assertFalse(motor.builtin());
        assertFalse(List.of(MotorType.values()).contains(motor));
        assertTrue(PartIndex.byKey("justmoreparts:m2806_1300").isEmpty());
        assertEquals(PartNames.MISSING_NAME, motor.translationKey());
        assertEquals(DroneBuild.Issue.MISSING_PART, build.problems().getFirst());
        assertFalse(build.buildable());
        assertEquals(List.of(motor), build.missingParts());
        JsonElement back = DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, build).getOrThrow();
        assertEquals(json, back);
        assertSame(motor, DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, back).getOrThrow().motor());
    }

    @Test
    void unknownKeysOfTheOwnNamespaceStayErrors() {
        assertTrue(DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, freestyleWith("motor", "m9999_1")).error().isPresent());
        assertTrue(DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, freestyleWith("frame", "m2207_1750")).error().isPresent());
        assertTrue(DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, freestyleWith("frame", "Kein Schlüssel")).error().isPresent());
        ByteBuf buffer = Unpooled.buffer();
        ByteBufCodecs.stringUtf8(DroneBuildCodecs.MAX_KEY_LENGTH).encode(buffer, "m9999_1");
        assertThrows(DecoderException.class, () -> DroneBuildCodecs.MOTOR_STREAM.decode(buffer));
    }

    @Test
    void accessoryPlaceholdersFollowTheirMount() {
        JsonObject json = DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, DroneBuild.CINEWHOOP).getOrThrow().getAsJsonObject();
        JsonObject accessories = new JsonObject();
        accessories.addProperty("top", "justmoreparts:gopro_13");
        accessories.addProperty("bottom", "justmoreparts:neon_strip");
        json.add("accessories", accessories);
        DroneBuild build = DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        AccessoryType top = build.accessories().at(AccessoryMount.TOP).orElseThrow();
        AccessoryType bottom = build.accessories().at(AccessoryMount.BOTTOM).orElseThrow();
        assertTrue(top.missing());
        assertTrue(bottom.missing());
        assertEquals(AccessorySlot.TOP, top.definition().slot());
        assertEquals(AccessoryType.ACTION_CAM_LIGHT.widthMm, top.widthMm);
        assertEquals(AccessoryType.LED_STRIP.ledCount, bottom.ledCount);
        assertEquals("justmoreparts:gopro_13", top.key());
        assertEquals(json, DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, build).getOrThrow());
        assertEquals(2, build.missingParts().size());
    }

    @Test
    void networkBytesStayTheSameAndPlaceholdersSurviveTheWire() {
        List<DroneBuild> builds = new ArrayList<>(DroneBuild.PRESETS);
        builds.add(DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY).withAccessory(AccessoryType.LED_STRIP));
        for (DroneBuild build : builds) {
            ByteBuf expected = Unpooled.buffer();
            ByteBufCodecs.stringUtf8(128).encode(expected, build.frame().key());
            ByteBufCodecs.stringUtf8(128).encode(expected, build.stack().key());
            ByteBufCodecs.stringUtf8(128).encode(expected, build.motor().key());
            ByteBufCodecs.stringUtf8(128).encode(expected, build.prop().key());
            ByteBufCodecs.stringUtf8(128).encode(expected, build.video().key());
            ByteBufCodecs.stringUtf8(128).encode(expected, build.battery().key());
            ByteBufCodecs.VAR_INT.encode(expected, build.accessories().size());
            for (var entry : build.accessories().byMount().entrySet()) {
                ByteBufCodecs.stringUtf8(16).encode(expected, entry.getKey().key());
                ByteBufCodecs.stringUtf8(128).encode(expected, entry.getValue().key());
            }
            ByteBuf actual = Unpooled.buffer();
            DroneBuildCodecs.STREAM_CODEC.encode(actual, build);
            assertArrayEquals(bytes(expected), bytes(actual), build.frame().key());
        }
        DroneBuild withMissing = new DroneBuild((FrameType) PartIndex.missing(PartKind.FRAME, Identifier.fromNamespaceAndPath("justmoreparts", "deadcat_5")),
                DroneBuild.FREESTYLE.stack(), DroneBuild.FREESTYLE.motor(), DroneBuild.FREESTYLE.prop(), DroneBuild.FREESTYLE.video(),
                DroneBuild.FREESTYLE.battery(), Accessories.NONE);
        ByteBuf buffer = Unpooled.buffer();
        DroneBuildCodecs.STREAM_CODEC.encode(buffer, withMissing);
        DroneBuild decoded = DroneBuildCodecs.STREAM_CODEC.decode(buffer);
        assertEquals(withMissing, decoded);
        assertTrue(decoded.frame().missing());
    }

    @Test
    void placeholdersAreInternedAndUseTheContractSubstitutes() {
        Identifier id = Identifier.fromNamespaceAndPath("gone", "thing");
        assertSame(PartIndex.missing(PartKind.PROP, id), PartIndex.missing(PartKind.PROP, id));
        for (PartKind kind : PartKind.values()) {
            PartInfo placeholder = PartIndex.missing(kind, id, AccessorySlot.TOP);
            assertTrue(placeholder.missing(), kind.key());
            assertEquals(id, placeholder.id());
            assertEquals(kind, placeholder.definition().kind());
        }
        assertEquals("freestyle_5", PartIndex.substitute(PartKind.FRAME, null).key());
        assertEquals("stack_30", PartIndex.substitute(PartKind.STACK, null).key());
        assertEquals("m2207_1750", PartIndex.substitute(PartKind.MOTOR, null).key());
        assertEquals("p51_3b", PartIndex.substitute(PartKind.PROP, null).key());
        assertEquals("analog_race", PartIndex.substitute(PartKind.VIDEO, null).key());
        assertEquals("b6s_1300", PartIndex.substitute(PartKind.BATTERY, null).key());
        assertEquals("action_cam_light", PartIndex.substitute(PartKind.ACCESSORY, AccessorySlot.TOP).key());
        assertEquals("led_strip", PartIndex.substitute(PartKind.ACCESSORY, AccessorySlot.BOTTOM).key());
    }

    @Test
    void boundDronesKeepMissingFrames() {
        JsonObject json = BoundDrone.CODEC.encodeStart(JsonOps.INSTANCE, new BoundDrone(new UUID(0L, 1L), "", FrameType.RACE_5, VideoSystem.DIGITAL))
                .getOrThrow().getAsJsonObject();
        json.addProperty("frame", "justmoreparts:xclass_13");
        BoundDrone bound = BoundDrone.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertTrue(bound.frame().missing());
        assertEquals("justmoreparts:xclass_13", BoundDrone.CODEC.encodeStart(JsonOps.INSTANCE, bound).getOrThrow().getAsJsonObject()
                .get("frame").getAsString());
    }

    @Test
    void partRefsResolvePlaceholdersOnlyOnRequest() {
        PartRef ref = new PartRef(PartCategory.MOTOR, "justmoreparts:m2806_1300");
        assertTrue(ref.resolve().isEmpty());
        assertTrue(ref.resolveOrMissing().orElseThrow().missing());
        assertEquals(MotorType.M2207_1750, new PartRef(PartCategory.MOTOR, "m2207_1750").resolveOrMissing().orElseThrow());
    }
}
