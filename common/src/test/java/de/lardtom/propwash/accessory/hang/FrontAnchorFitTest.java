package de.lardtom.propwash.accessory.hang;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.build.AccessoryAnchor;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.drone.client.render.AccessoryFit;
import de.lardtom.propwash.testutil.TestPaths;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontAnchorFitTest {
    private static final double[][] SIZES = {{62.0, 58.0, 64.0}, {34.0, 26.0, 40.0}, {12.0, 10.0, 20.0}, {44.0, 44.0, 46.0}};

    private static Path assets() {
        return TestPaths.common().resolve("src/main/resources/assets/propwash");
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    static float[] frameQuads(FrameType frame) throws IOException {
        JsonObject item = json(assets().resolve("items/drone_render/frame/" + frame.key() + ".json")).getAsJsonObject("model");
        assertFalse(item.has("transformation"), frame.key() + ": Rahmenmodell mit transformation");
        Identifier model = Identifier.parse(item.get("model").getAsString());
        return AccessoryFit.elementQuads(json(assets().resolve("models/" + model.getPath() + ".json")));
    }

    static AccessoryType front(double width, double height, double depth) {
        Identifier id = Identifier.fromNamespaceAndPath("fittest", "front_" + (int) width + "_" + (int) height + "_" + (int) depth);
        return (AccessoryType) PartIndex.detached(AccessoryDefinition.builder(id).basics(PartBasics.of(100.0)).slot(AccessorySlot.FRONT)
                .size(width, height, depth).frontalArea(0.001).build());
    }

    static List<FrameType> frontFrames() {
        List<FrameType> out = new ArrayList<>();
        for (FrameType frame : FrameType.values()) {
            if (frame.builtin() && frame.accessoryMounts.contains(AccessoryMount.FRONT)) {
                out.add(frame);
            }
        }
        return out;
    }

    @Test
    void frontAccessoriesTouchEveryBuiltinFrameWithoutDeepOverlap() throws IOException {
        List<String> misses = new ArrayList<>();
        List<FrameType> frames = frontFrames();
        assertEquals(4, frames.size(), frames.toString());
        for (FrameType frame : frames) {
            float[] body = frameQuads(frame);
            AccessoryAnchor anchor = AccessoryAnchor.of(frame, AccessoryMount.FRONT);
            for (double[] size : SIZES) {
                AccessoryType type = front(size[0], size[1], size[2]);
                AccessoryFit.Fit fit = AccessoryFit.measure(body, AccessoryFit.boxQuads(anchor, type), anchor, type);
                if (!fit.ok()) {
                    misses.add(frame.key() + " " + (int) size[0] + "×" + (int) size[1] + "×" + (int) size[2] + ": " + fit.describe(anchor));
                }
            }
        }
        assertTrue(misses.isEmpty(), "Zubehör vorne nicht am Rahmen:\n" + String.join("\n", misses));
    }

    @Test
    void theMeasureSeesGapsAndOverlapsOfABox() {
        AccessoryType type = front(20.0, 10.0, 20.0);
        AccessoryAnchor touching = new AccessoryAnchor(0.0, 0.0, -10.0, 0.0, 0.0);
        float[] plate = AccessoryFit.boxQuads(new AccessoryAnchor(0.0, 0.0, 10.0, 0.0, 0.0), type);
        AccessoryFit.Fit flush = AccessoryFit.measure(plate, AccessoryFit.boxQuads(touching, type), touching, type);
        assertEquals(0.0F, flush.gapMm(), 1.0E-4F);
        assertEquals(0.0F, flush.overlapMm(), 1.0E-4F);
        AccessoryAnchor apart = new AccessoryAnchor(0.0, 0.0, -17.0, 0.0, 0.0);
        AccessoryFit.Fit gap = AccessoryFit.measure(plate, AccessoryFit.boxQuads(apart, type), apart, type);
        assertEquals(7.0F, gap.gapMm(), 1.0E-3F);
        assertFalse(gap.ok());
        assertEquals(-10.0F, gap.targetMm()[2], 1.0E-3F);
        AccessoryAnchor deep = new AccessoryAnchor(0.0, 0.0, -5.0, 0.0, 0.0);
        AccessoryFit.Fit inside = AccessoryFit.measure(plate, AccessoryFit.boxQuads(deep, type), deep, type);
        assertEquals(0.0F, inside.gapMm(), 1.0E-4F);
        assertEquals(5.0F, inside.overlapMm(), 0.26F);
        assertFalse(inside.ok());
        AccessoryAnchor below = new AccessoryAnchor(0.0, -14.0, -10.0, 0.0, 0.0);
        float[] roof = AccessoryFit.boxQuads(new AccessoryAnchor(0.0, 0.0, 0.0, 0.0, 0.0), front(20.0, 10.0, 40.0));
        AccessoryFit.Fit hanging = AccessoryFit.measure(roof, AccessoryFit.boxQuads(below, type), below, type);
        assertEquals(4.0F, hanging.gapMm(), 1.0E-3F);
        assertEquals(-10.0F, hanging.targetMm()[1], 1.0E-3F);
        assertEquals(-10.0F, hanging.targetMm()[2], 1.0E-3F);
    }
}
