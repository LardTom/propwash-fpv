package de.lardtom.propwash.apiimpl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.build.BuildAccess;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportLayer;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportTarget;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.part.CellChemistry;
import de.lardtom.propwash.api.part.MotorThermal;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.PartVisual;
import de.lardtom.propwash.api.part.PropDefinition;
import de.lardtom.propwash.api.part.PropMaterial;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintCost;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.ImportService;
import de.lardtom.propwash.drone.DroneWear;
import de.lardtom.propwash.parts.PartJson;
import de.lardtom.propwash.parts.PartRegistryImpl;
import de.lardtom.propwash.sim.AirframeSpec;
import de.lardtom.propwash.sim.BuildAnalysis;
import de.lardtom.propwash.sim.BuildWarning;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiAdditionsTest {
    private static final PartRegistryImpl PARTS = PartRegistryImpl.INSTANCE;

    @Test
    void motorThermalBuilderMatchesTheRecord() {
        assertEquals(MotorThermal.DEFAULT, MotorThermal.builder().build());
        assertEquals(MotorThermal.limits(135.0, 155.0), MotorThermal.builder().limits(135.0, 155.0).build());
        MotorThermal full = MotorThermal.builder().heatCapacity(64.0).cooling(0.21).coolingPerRadS(4.0E-4).limits(140.0, 160.0).wearAtMax(0.001)
                .build();
        assertEquals(new MotorThermal(OptionalDouble.of(64.0), OptionalDouble.of(0.21), OptionalDouble.of(4.0E-4), 140.0, 160.0, 0.001), full);
        assertThrows(IllegalArgumentException.class, () -> MotorThermal.builder().limits(160.0, 140.0).build());
        assertThrows(IllegalArgumentException.class, () -> MotorThermal.builder().cooling(-1.0).build());
    }

    @Test
    void buildStatsCarryTheNewAnalysisFields() {
        for (DroneBuild preset : DroneBuild.PRESETS) {
            BuildStats stats = BuildAccessImpl.INSTANCE.analyze(Conversions.spec(preset)).orElseThrow();
            AirframeSpec airframe = AirframeSpec.of(preset);
            assertEquals(1.0, stats.propwashNoise());
            assertEquals(BuildAnalysis.of(preset).fullThrottleCellVoltage(), stats.fullThrottleCellVoltage());
            assertEquals(airframe.authorityRoll, stats.rollAuthorityRadS2());
            assertEquals(airframe.authorityPitch, stats.pitchAuthorityRadS2());
            assertEquals(airframe.authorityYawSteady, stats.yawAuthorityRadS2());
            assertTrue(stats.rollAuthorityRadS2() > 0.0 && stats.pitchAuthorityRadS2() > 0.0 && stats.yawAuthorityRadS2() > 0.0);
            assertTrue(stats.fullThrottleCellVoltage() > 2.5 && stats.fullThrottleCellVoltage() < 4.4);
        }
        BuildStats old = new BuildStats(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, List.of());
        assertTrue(Double.isNaN(old.propwashNoise()) && Double.isNaN(old.yawAuthorityRadS2()));
        BuildStats v12 = new BuildStats(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, List.of(), 1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
        assertTrue(Double.isNaN(v12.fullThrottleCellVoltage()) && Double.isNaN(v12.rollAuthorityRadS2()));
    }

    @Test
    void warningKeysAreTheAnalysisWarnings() {
        List<String> keys = new ArrayList<>();
        for (BuildWarning warning : BuildWarning.values()) {
            keys.add(warning.name().toLowerCase(Locale.ROOT));
        }
        assertEquals(keys, BuildStats.WARNINGS);
    }

    @Test
    void rarityIsOptionalAndSurvivesJson() {
        assertEquals(Optional.empty(), PartBasics.of(10.0).rarity());
        PartBasics basics = PartBasics.of(10.0).withRarity(Rarity.RARE).withCreativeListed(false).withVisual(PartVisual.accent(0x123456));
        assertEquals(Optional.of(Rarity.RARE), basics.rarity());
        assertEquals(new PartBasics(10.0, 0, PartBasics.DEFAULT_SORT_ORDER, true, 1.0, Optional.empty(), PartVisual.NONE), PartBasics.of(10.0));
        PropDefinition prop = PropDefinition.builder(Identifier.fromNamespaceAndPath("propwash_apitest", "rare_prop"))
                .basics(basics).size(130, 4.5, 3).coefficients(0.12, 0.06).material(PropMaterial.CARBON).build();
        JsonObject json = PARTS.toJson(prop);
        assertEquals("rare", json.get("rarity").getAsString());
        assertEquals(prop, PARTS.fromJson(PartKind.PROP, prop.id(), json));
        for (Rarity rarity : Rarity.values()) {
            assertEquals(Optional.of(rarity), PartJson.rarity(rarity.getSerializedName()));
        }
        JsonObject broken = json.deepCopy();
        broken.addProperty("rarity", "legendary");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> PARTS.fromJson(PartKind.PROP, prop.id(), broken));
        assertTrue(error.getMessage().contains("rarity"), error.getMessage());
    }

    @Test
    void publicJsonCodecIsPropwashsOwnCodec() {
        for (PartInfo part : PartIndex.all()) {
            PartDefinition definition = part.definition();
            JsonObject json = PARTS.toJson(definition);
            assertEquals(PartJson.encode(definition), json);
            assertEquals(definition, PARTS.fromJson(definition.kind(), definition.id(), json));
            assertEquals(PartJson.encode(definition), json, "fromJson darf das Objekt nicht verändern");
        }
        for (BatteryType.Chemistry chemistry : BatteryType.Chemistry.values()) {
            CellChemistry definition = chemistry.definition();
            assertEquals(definition, PARTS.chemistryFromJson(definition.id(), PARTS.toJson(definition)));
        }
        Identifier id = Identifier.fromNamespaceAndPath("propwash_apitest", "motor");
        JsonObject missing = JsonParser.parseString("{\"format\": 1, \"mass_g\": 30.0, \"stator\": \"2207\", \"mount\": \"propwash:m22\","
                + " \"max_current_a\": 40.0, \"resistance_ohm\": 0.05, \"cells\": [4, 6]}").getAsJsonObject();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> PARTS.fromJson(PartKind.MOTOR, id, missing));
        assertTrue(error.getMessage().contains("kv"), error.getMessage());
    }

    @Test
    void importLayersAndTargetsOnlyGrowAtTheEnd() {
        assertEquals(List.of(ImportLayer.PARTS, ImportLayer.PAINT, ImportLayer.TUNE, ImportLayer.NAME), List.of(ImportLayer.values()));
        assertEquals(List.of(ImportTarget.WORKBENCH, ImportTarget.HELD_DRONE, ImportTarget.NEW_DRONE), List.of(ImportTarget.values()));
        assertEquals("propwash.api.import.layer.name", ImportLayer.NAME.translationKey());
        DroneData data = DroneData.of(Conversions.spec(DroneBuild.FREESTYLE)).withName("Bärenstark");
        ImportRequest held = ImportRequest.onHeldDrone(data, EnumSet.of(ImportLayer.PAINT, ImportLayer.NAME));
        assertEquals(ImportTarget.HELD_DRONE, held.target());
        assertEquals(TuneGroup.FLIGHT, held.tuneGroups());
        assertThrows(IllegalArgumentException.class, () -> ImportRequest.onHeldDrone(data, EnumSet.of(ImportLayer.PARTS)));
        ImportRequest create = ImportRequest.createDrone(data);
        assertEquals(ImportTarget.NEW_DRONE, create.target());
        assertEquals(EnumSet.allOf(ImportLayer.class), create.layers());
        assertEquals(EnumSet.allOf(TuneGroup.class), create.tuneGroups());
        assertThrows(IllegalArgumentException.class, () -> new ImportRequest(data, EnumSet.of(ImportLayer.PAINT), null, ImportTarget.NEW_DRONE));
        assertEquals(create, ImportWire.of(create).toRequest());
        assertEquals(held, ImportWire.of(held).toRequest());
        assertTrue(ImportRequest.wholeBuild(data).layers().contains(ImportLayer.NAME));
    }

    @Test
    void paintCostListsDyesAndCustomColours() {
        DronePaint before = DronePaint.DEFAULT;
        DronePaint after = Conversions.paint(new PaintSpec(Map.of("frame", 0xB02E26, "motors", 0xB02E26, "stack", 0x123456)));
        Map<String, Integer> cost = BuildAccessImpl.costMap(PaintCost.of(before, after));
        assertEquals(Map.of("red", 2, ImportService.CUSTOM_DYES, 1), cost);
        assertEquals(Map.of(), BuildAccessImpl.costMap(PaintCost.of(after, after)));
        assertThrows(UnsupportedOperationException.class, () -> cost.put("blue", 1));
    }

    @Test
    void escBurnsOnlyOnceWhenTheStackBreaks() {
        PartWear burnt = PartWear.PRISTINE.withStack(0.0F);
        assertTrue(DroneWear.escBurnedNow(true, PartWear.PRISTINE, burnt));
        assertFalse(DroneWear.escBurnedNow(false, PartWear.PRISTINE, burnt));
        assertFalse(DroneWear.escBurnedNow(true, burnt, burnt));
        assertFalse(DroneWear.escBurnedNow(true, PartWear.PRISTINE, PartWear.PRISTINE));
    }

    @Test
    void modTextsMergeLanguageFilesFirstWins() {
        List<String> asked = new ArrayList<>();
        ModTexts texts = new ModTexts(code -> {
            asked.add(code);
            return List.of("{\"a\": \"eins\", \"n\": 3}".getBytes(StandardCharsets.UTF_8), "kaputt".getBytes(StandardCharsets.UTF_8),
                    "{\"a\": \"zwei\", \"b\": \"Bär\"}".getBytes(StandardCharsets.UTF_8));
        });
        assertEquals(Optional.of("eins"), texts.text("de_DE", "a"));
        assertEquals(Optional.of("Bär"), texts.text("de_de", "b"));
        assertEquals(Optional.empty(), texts.text("de_de", "n"));
        assertEquals(Optional.empty(), texts.text("de_de", "fehlt"));
        assertEquals(List.of("de_de"), asked);
        assertThrows(IllegalArgumentException.class, () -> texts.text("../de", "a"));
        assertTrue(ModTexts.isLanguageFile("assets/justmoreparts/lang/de_de.json", "de_de"));
        assertFalse(ModTexts.isLanguageFile("assets/justmoreparts/lang/sub/de_de.json", "de_de"));
        assertFalse(ModTexts.isLanguageFile("assets/justmoreparts/lang/en_us.json", "de_de"));
        assertFalse(ModTexts.isLanguageFile("data/justmoreparts/lang/de_de.json", "de_de"));
    }

    @Test
    void newBuildAccessMethodsHaveNeutralDefaults() {
        BuildAccess foreign = (BuildAccess) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{BuildAccess.class},
                (proxy, method, args) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        assertEquals(Map.of(), foreign.paintCost(null, PaintSpec.DEFAULT));
        assertEquals(OptionalDouble.empty(), foreign.partCondition(null, PartKind.MOTOR));
        assertEquals(Set.of(), Set.copyOf(foreign.paintCost(null, PaintSpec.DEFAULT).keySet()));
    }
}
