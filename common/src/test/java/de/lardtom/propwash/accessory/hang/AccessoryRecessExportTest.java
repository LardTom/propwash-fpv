package de.lardtom.propwash.accessory.hang;

import com.google.gson.JsonObject;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.parts.PartJson;
import de.lardtom.propwash.webexport.PartData;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessoryRecessExportTest {
    private static AccessoryDefinition.Builder dispenser(String path) {
        return AccessoryDefinition.builder(Identifier.fromNamespaceAndPath("recesstest", path)).basics(PartBasics.of(150.0))
                .slot(AccessorySlot.FRONT).size(62.0, 58.0, 64.0).flightBatteryWatts(1.2).frontalArea(0.0036);
    }

    @Test
    void theWebExportCarriesRecessAndDropUnderTheirJsonNames() {
        JsonObject data = PartData.data(dispenser("recessed").recess(58.0).drop(1.5).build());
        assertTrue(data.has("recess_mm"), data.toString());
        assertTrue(data.has("drop_mm"), data.toString());
        assertEquals(58.0, data.get("recess_mm").getAsDouble(), 0.0);
        assertEquals(1.5, data.get("drop_mm").getAsDouble(), 0.0);
    }

    @Test
    void eachFieldOnlyWhenItIsSetLikeThePartFile() {
        JsonObject recessOnly = PartData.data(dispenser("recess_only").recess(23.0).build());
        assertEquals(23.0, recessOnly.get("recess_mm").getAsDouble(), 0.0);
        assertFalse(recessOnly.has("drop_mm"), recessOnly.toString());
        JsonObject dropOnly = PartData.data(dispenser("drop_only").drop(3.0).build());
        assertEquals(3.0, dropOnly.get("drop_mm").getAsDouble(), 0.0);
        assertFalse(dropOnly.has("recess_mm"), dropOnly.toString());
        AccessoryDefinition both = dispenser("both").recess(58.0).drop(1.5).build();
        JsonObject encoded = PartJson.encode(both);
        JsonObject exported = PartData.data(both);
        assertEquals(encoded.get("recess_mm"), exported.get("recess_mm"));
        assertEquals(encoded.get("drop_mm"), exported.get("drop_mm"));
    }

    @Test
    void withoutTheFieldsTheExportOfEveryAccessoryStaysAsBefore() {
        JsonObject plain = PartData.data(dispenser("plain").build());
        assertFalse(plain.has("recess_mm"), plain.toString());
        assertFalse(plain.has("drop_mm"), plain.toString());
        int checked = 0;
        for (PartDefinition builtin : PropwashApi.parts().all(PartKind.ACCESSORY)) {
            JsonObject data = PartData.data(builtin);
            assertFalse(data.has("recess_mm"), builtin.id().toString());
            assertFalse(data.has("drop_mm"), builtin.id().toString());
            checked++;
        }
        assertTrue(checked >= 5, "eingebautes Zubehör: " + checked);
    }
}
