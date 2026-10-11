package de.lardtom.propwash.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.VideoSystem;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundDroneCodecTest {
    private static BoundDrone roundTrip(BoundDrone bound) {
        JsonElement json = BoundDrone.CODEC.encodeStart(JsonOps.INSTANCE, bound).getOrThrow();
        return BoundDrone.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    @Test
    void roundTripsAllFramesAndSystems() {
        UUID id = UUID.fromString("1c2d3e4f-0000-4000-8000-00000000abcd");
        for (FrameType frame : FrameType.values()) {
            for (VideoSystem system : VideoSystem.values()) {
                BoundDrone empty = new BoundDrone(id, "", frame, system);
                assertEquals(empty, roundTrip(empty));
                BoundDrone named = new BoundDrone(id, "Käsekuchen-Express", frame, system);
                assertEquals(named, roundTrip(named));
            }
        }
    }

    @Test
    void emptyNameIsOptional() {
        JsonObject json = BoundDrone.CODEC.encodeStart(JsonOps.INSTANCE,
                new BoundDrone(new UUID(1L, 2L), "", FrameType.RACE_5, VideoSystem.ANALOG)).getOrThrow().getAsJsonObject();
        json.remove("name");
        BoundDrone parsed = BoundDrone.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals("", parsed.name());
        assertEquals("analog", json.get("video").getAsString());
        assertEquals("race_5", json.get("frame").getAsString());
    }

    @Test
    void unknownSystemIsAnError() {
        JsonObject json = BoundDrone.CODEC.encodeStart(JsonOps.INSTANCE,
                new BoundDrone(new UUID(1L, 2L), "x", FrameType.RACE_5, VideoSystem.DIGITAL)).getOrThrow().getAsJsonObject();
        json.addProperty("video", "hologram");
        DataResult<BoundDrone> result = BoundDrone.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
    }

    @Test
    void longNamesAreTrimmed() {
        String longName = "x".repeat(80);
        assertEquals(BoundDrone.MAX_NAME_LENGTH, new BoundDrone(new UUID(3L, 4L), longName, FrameType.WHOOP_65, VideoSystem.ANALOG).name().length());
    }
}
