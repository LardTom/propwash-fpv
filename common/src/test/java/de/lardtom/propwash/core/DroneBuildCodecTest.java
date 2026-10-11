package de.lardtom.propwash.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.build.Accessories;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneBuildCodecTest {
    private static List<DroneBuild> samples() {
        List<DroneBuild> builds = new ArrayList<>(DroneBuild.PRESETS);
        builds.add(DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY));
        builds.add(DroneBuild.CINEWHOOP.withAccessory(AccessoryType.ACTION_CAM_LIGHT).withAccessory(AccessoryType.LED_STRIP));
        builds.add(DroneBuild.WHOOP.withAccessory(AccessoryType.LED_STRIP));
        return builds;
    }

    @Test
    void jsonRoundTripKeepsAccessories() {
        for (DroneBuild build : samples()) {
            JsonElement json = DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, build).getOrThrow();
            assertEquals(build, DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(), json.toString());
            assertEquals(!build.accessories().isEmpty(), json.getAsJsonObject().has("accessories"), json.toString());
        }
        JsonObject json = DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE,
                DroneBuild.FREESTYLE.withAccessory(AccessoryType.ACTION_CAM_HEAVY)).getOrThrow().getAsJsonObject();
        assertEquals("action_cam_heavy", json.getAsJsonObject("accessories").get("top").getAsString());
    }

    @Test
    void oldSavesWithoutAccessoriesStillLoad() {
        JsonObject json = new JsonObject();
        json.addProperty("frame", "freestyle_5");
        json.addProperty("stack", "stack_30");
        json.addProperty("motor", "m2207_1750");
        json.addProperty("prop", "p51_3b");
        json.addProperty("video", "digital_hd");
        json.addProperty("battery", "b6s_1300");
        assertEquals(DroneBuild.FREESTYLE, DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void brokenAccessoriesFallBackToNone() {
        JsonObject json = DroneBuildCodecs.CODEC.encodeStart(JsonOps.INSTANCE, DroneBuild.RACER).getOrThrow().getAsJsonObject();
        JsonObject accessories = new JsonObject();
        accessories.addProperty("bottom", "action_cam_heavy");
        json.add("accessories", accessories);
        assertEquals(DroneBuild.RACER, DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        accessories.addProperty("bottom", "laser_turret");
        assertEquals(DroneBuild.RACER, DroneBuildCodecs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertTrue(DroneBuildCodecs.ACCESSORIES.parse(JsonOps.INSTANCE, accessories).error().isPresent());
    }

    @Test
    void networkRoundTripKeepsAccessories() {
        for (DroneBuild build : samples()) {
            ByteBuf buffer = Unpooled.buffer();
            DroneBuildCodecs.STREAM_CODEC.encode(buffer, build);
            assertEquals(build, DroneBuildCodecs.STREAM_CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        }
        ByteBuf empty = Unpooled.buffer();
        DroneBuildCodecs.ACCESSORIES_STREAM.encode(empty, Accessories.NONE);
        assertEquals(Accessories.NONE, DroneBuildCodecs.ACCESSORIES_STREAM.decode(empty));
    }
}
