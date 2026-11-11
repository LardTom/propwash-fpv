package de.lardtom.propwash.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.creative.CreativeSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationGhostLookTest {
    private static final String BUILD = "DroneBuild[frame=RACE_5, stack=STACK_30, motor=M2306_1860, prop=P51_3B, video=ANALOG_RACE, battery=B6S_1100, "
            + "accessories=Accessories[byMount={}]";
    private static final String PAINT = ", paint=DronePaint[colors={FRAME=16742912, PROP_FRONT_LEFT=16777215}]]";
    private static final String BEFORE_050 = "GhostLook[build=" + BUILD + "]" + PAINT;
    private static final String TODAY = "GhostLook[build=" + BUILD + ", creative=" + CreativeSpec.EMPTY + "]" + PAINT;

    private static JsonObject world(String look) {
        JsonObject ghost = new JsonObject();
        ghost.addProperty("frame", "race_5");
        ghost.addProperty("samples", 121);
        ghost.addProperty("look", look);
        JsonArray ghosts = new JsonArray();
        ghosts.add(ghost);
        JsonObject course = new JsonObject();
        course.addProperty("id", 1);
        course.add("recordGhosts", ghosts);
        JsonArray courses = new JsonArray();
        courses.add(course);
        JsonObject root = new JsonObject();
        root.add("courses", courses);
        return root;
    }

    private static MigrationValues.Diff compare(String expected, String actual) {
        MigrationValues.Diff diff = new MigrationValues.Diff(false);
        MigrationValues.compare("", world(expected), world(actual), diff);
        return diff;
    }

    @Test
    void lookFromBeforeTheCreativeSpecIsTheSameBuildToday() {
        MigrationValues.Diff diff = compare(BEFORE_050, TODAY);
        assertEquals(List.of(), diff.failures);
        assertEquals(4, diff.compared);
    }

    @Test
    void lookWithEmptyCreativeSpecStaysEqual() {
        assertEquals(List.of(), compare(TODAY, TODAY).failures);
    }

    @Test
    void creativeValuesAreADifference() {
        String creative = TODAY.replace(String.valueOf(CreativeSpec.EMPTY), String.valueOf(new CreativeSpec(Map.of(PartKind.FRAME, "race_5"),
                Map.of("mass", 1.5))));
        MigrationValues.Diff diff = compare(BEFORE_050, creative);
        assertEquals(1, diff.failures.size(), String.valueOf(diff.failures));
        assertTrue(diff.failures.getFirst().startsWith("courses[id=1].recordGhosts[frame=race_5].look: "), diff.failures.getFirst());
    }

    @Test
    void otherPartsAreADifference() {
        MigrationValues.Diff diff = compare(BEFORE_050, TODAY.replace("prop=P51_3B", "prop=P51_29_3B"));
        assertEquals(1, diff.failures.size(), String.valueOf(diff.failures));
    }
}
