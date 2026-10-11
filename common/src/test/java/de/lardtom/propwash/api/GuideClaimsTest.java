package de.lardtom.propwash.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.parts.PartRegistryImpl;
import de.lardtom.propwash.sim.BuildAnalysis;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class GuideClaimsTest {
    private static Path repository() {
        for (Path candidate : List.of(Path.of(".."), Path.of("."))) {
            if (Files.isRegularFile(candidate.resolve("docs/design/parts-api.md"))) {
                return candidate;
            }
        }
        fail("Repository nicht gefunden (Arbeitsverzeichnis " + Path.of("").toAbsolutePath() + ")");
        return Path.of(".");
    }

    private static double propsInView(double cameraZ, VideoType video) throws IOException {
        Path file = repository().resolve("common/src/devtest/resources/data/propwash_example/propwash_parts/frame/deadcat_5.json");
        JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        json.getAsJsonObject("camera").getAsJsonArray("pos").set(2, new JsonPrimitive(cameraZ));
        FrameDefinition definition = (FrameDefinition) PartRegistryImpl.INSTANCE.fromJson(PartKind.FRAME,
                Identifier.fromNamespaceAndPath("propwash_guidetest", "deadcat_" + Math.round(-cameraZ)), json);
        FrameType frame = (FrameType) PartIndex.detached(definition);
        return BuildAnalysis.of(new DroneBuild(frame, StackType.STACK_30, MotorType.M2207_1750, PropType.P51_3B, video, BatteryType.B6S_1300))
                .propsInViewPercent();
    }

    @Test
    void exampleDeadcatKeepsThePropsOutOfTheCameraImage() throws IOException {
        assertEquals(0.0, propsInView(-100.0, VideoType.ANALOG_RACE));
        assertEquals(0.0, propsInView(-100.0, VideoType.DIGITAL_HD));
        assertEquals(4.4, propsInView(-58.0, VideoType.ANALOG_RACE), 0.05);
    }
}
