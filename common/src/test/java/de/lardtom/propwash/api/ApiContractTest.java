package de.lardtom.propwash.api;

import com.mojang.serialization.Codec;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.accessory.AccessoryContext;
import de.lardtom.propwash.api.accessory.AccessoryRegistry;
import de.lardtom.propwash.api.accessory.DispenseOptions;
import de.lardtom.propwash.api.accessory.DispenseResult;
import de.lardtom.propwash.api.accessory.ItemDisplay;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.TuneGroup;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.client.ClientInput;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.RecordingControl;
import de.lardtom.propwash.api.client.ServiceExtension;
import de.lardtom.propwash.api.client.camera.GimbalContext;
import de.lardtom.propwash.api.client.camera.GimbalProvider;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.client.osd.OsdGroup;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.client.osd.OsdTone;
import de.lardtom.propwash.api.client.osd.OsdWarning;
import de.lardtom.propwash.api.drone.AutonomyAccess;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.drone.AutonomyRequest;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.drone.CameraPose;
import de.lardtom.propwash.api.drone.CollisionFlags;
import de.lardtom.propwash.api.drone.CollisionRule;
import de.lardtom.propwash.api.drone.DroneAccess;
import de.lardtom.propwash.api.drone.DroneDataAccess;
import de.lardtom.propwash.api.drone.DroneDataKey;
import de.lardtom.propwash.api.drone.ExitHandler;
import de.lardtom.propwash.api.drone.OperatorAccess;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.input.AddonAxis;
import de.lardtom.propwash.api.input.AddonSwitch;
import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.api.input.SwitchRegistry;
import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.PartBasics;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.Vec3Mm;
import de.lardtom.propwash.api.race.RaceData;
import de.lardtom.propwash.api.race.RaceStandardInfo;
import de.lardtom.propwash.api.share.ShareCodes;
import de.lardtom.propwash.api.share.ShareOptions;
import de.lardtom.propwash.api.share.ShareResult;
import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.api.worldrule.WorldRules;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.ui.TuneKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiContractTest {
    private static final String SIGNATURES = "api/api-1.6.0-signatures.txt";
    private static final String PUBLISHED_17 = "api/api-1.7.0-published.txt";
    private static final String TEST_NAMESPACE = "apicontract";
    private static final List<String> API17_TYPES = List.of(
            "de.lardtom.propwash.api.input.SwitchRegistry",
            "de.lardtom.propwash.api.input.SwitchSpec",
            "de.lardtom.propwash.api.input.SwitchSpec$Builder",
            "de.lardtom.propwash.api.input.AxisSpec",
            "de.lardtom.propwash.api.input.AxisSpec$Builder",
            "de.lardtom.propwash.api.input.AddonSwitch",
            "de.lardtom.propwash.api.input.AddonAxis",
            "de.lardtom.propwash.api.client.osd.OsdElementSpec",
            "de.lardtom.propwash.api.client.osd.OsdElementSpec$Builder",
            "de.lardtom.propwash.api.client.osd.OsdAlign",
            "de.lardtom.propwash.api.client.osd.OsdGroup",
            "de.lardtom.propwash.api.client.osd.OsdTone",
            "de.lardtom.propwash.api.client.osd.OsdText",
            "de.lardtom.propwash.api.client.osd.OsdContent",
            "de.lardtom.propwash.api.client.osd.OsdContext",
            "de.lardtom.propwash.api.client.osd.OsdWarning",
            "de.lardtom.propwash.api.client.ServiceExtension",
            "de.lardtom.propwash.api.client.RecordingControl",
            "de.lardtom.propwash.api.drone.CollisionRule",
            "de.lardtom.propwash.api.drone.CollisionFlags",
            "de.lardtom.propwash.api.drone.CollisionQuery",
            "de.lardtom.propwash.api.drone.CollisionQuery$Side",
            "de.lardtom.propwash.api.drone.BlockContact",
            "de.lardtom.propwash.api.drone.CameraPose",
            "de.lardtom.propwash.api.drone.DroneDataAccess",
            "de.lardtom.propwash.api.drone.DroneDataKey",
            "de.lardtom.propwash.api.drone.DroneDataKey$Sync",
            "de.lardtom.propwash.api.drone.AutonomyAccess",
            "de.lardtom.propwash.api.drone.AutonomyOptions",
            "de.lardtom.propwash.api.drone.AutonomyRequest",
            "de.lardtom.propwash.api.drone.AutonomyResult",
            "de.lardtom.propwash.api.drone.AutonomyEnd",
            "de.lardtom.propwash.api.drone.ExitHandler",
            "de.lardtom.propwash.api.drone.OperatorAccess",
            "de.lardtom.propwash.api.accessory.DispenseOptions",
            "de.lardtom.propwash.api.accessory.DispenseResult",
            "de.lardtom.propwash.api.accessory.DispenseResult$Status",
            "de.lardtom.propwash.api.accessory.ItemDisplay",
            "de.lardtom.propwash.api.event.DroneKnockback",
            "de.lardtom.propwash.api.event.DroneKnockback$Cause",
            "de.lardtom.propwash.api.event.PropwashEvents$AddonSwitchChanged",
            "de.lardtom.propwash.api.event.PropwashEvents$DroneKnockedBack",
            "de.lardtom.propwash.api.event.PropwashEvents$AutonomyEnded",
            "de.lardtom.propwash.api.event.PropwashEvents$PilotChanged",
            "de.lardtom.propwash.api.worldrule.WorldRules",
            "de.lardtom.propwash.api.worldrule.WorldRule",
            "de.lardtom.propwash.api.worldrule.WorldRule$Type",
            "de.lardtom.propwash.api.share.ShareCodes",
            "de.lardtom.propwash.api.share.ShareResult",
            "de.lardtom.propwash.api.share.ShareResult$Status",
            "de.lardtom.propwash.api.share.ShareResult$Format",
            "de.lardtom.propwash.api.share.ShareResult$Check",
            "de.lardtom.propwash.api.share.ShareResult$NamespaceInfo",
            "de.lardtom.propwash.api.share.ShareOptions",
            "de.lardtom.propwash.api.share.ShareOptions$Layer",
            "de.lardtom.propwash.api.part.VideoAntenna",
            "de.lardtom.propwash.api.part.AntennaCap");

    @Test
    void semanticVersionRules() {
        assertTrue(PropwashApi.isCompatible("1.0"));
        assertTrue(PropwashApi.isCompatible("1.0.0"));
        assertTrue(PropwashApi.isCompatible("1.1"));
        assertTrue(PropwashApi.isCompatible("1.2"));
        assertTrue(PropwashApi.isCompatible("1.3"));
        assertTrue(PropwashApi.isCompatible("1.4"));
        assertTrue(PropwashApi.isCompatible("1.5"));
        assertTrue(PropwashApi.isCompatible("1.6"));
        assertTrue(PropwashApi.isCompatible("1.7"));
        assertTrue(PropwashApi.isCompatible("1.7.0"));
        assertTrue(PropwashApi.isCompatible("1.7.1"));
        assertTrue(PropwashApi.isCompatible("1.8"));
        assertTrue(PropwashApi.isCompatible("1.8.0"));
        assertFalse(PropwashApi.isCompatible("1.8.1"));
        assertFalse(PropwashApi.isCompatible("1.9"));
        assertEquals("1.8.0", PropwashApi.API_VERSION);
        assertEquals(new ApiVersion(1, 8, 0), PropwashApi.VERSION);
        assertFalse(PropwashApi.isCompatible("2.0"));
        assertFalse(PropwashApi.isCompatible("0.9"));
        assertTrue(ApiVersion.parse("1.4.2").satisfies(ApiVersion.parse("1.3")));
        assertFalse(ApiVersion.parse("1.2.9").satisfies(ApiVersion.parse("1.3")));
        assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse("eins"));
        assertThrows(IllegalStateException.class, () -> PropwashApi.requireVersion("test", "9.0"));
        PropwashApi.requireVersion("test", "1.7");
        PropwashApi.requireVersion("test", "1.8");
    }

    @Test
    void everyKnownTuneKeyHasAStableGroup() {
        assertEquals(TuneGroup.RATES, TuneGroup.of("rates.roll.rcRate"));
        assertEquals(TuneGroup.PID, TuneGroup.of("pid.roll.p"));
        assertEquals(TuneGroup.FEEDFORWARD, TuneGroup.of("pid.yaw.ff"));
        assertEquals(TuneGroup.TPA, TuneGroup.of("pid.tpaBreakpoint"));
        assertEquals(TuneGroup.THROTTLE, TuneGroup.of("pid.throttleMid"));
        assertEquals(TuneGroup.THROTTLE, TuneGroup.of("pid.motorIdlePercent"));
        assertEquals(TuneGroup.FILTERS, TuneGroup.of("filters.gyroLpfHz"));
        assertEquals(TuneGroup.HOLD, TuneGroup.of("hold.altP"));
        assertEquals(TuneGroup.CAMERA, TuneGroup.of("camera.uptiltDeg"));
        assertEquals(TuneGroup.LED, TuneGroup.of("led.mode"));
        assertEquals(TuneGroup.VTX, TuneGroup.of("vtx.channel"));
        for (String key : TuneKeys.params().keySet()) {
            assertNotEquals(TuneGroup.OTHER, TuneGroup.of(key), key);
        }
    }

    @Test
    void overlayReplacesOnlyTheChosenGroups() {
        TuneSpec base = new TuneSpec(Map.of("rates.roll.rcRate", 1.0F, "pid.roll.p", 40.0F, "vtx.channel", 3.0F));
        TuneSpec imported = new TuneSpec(Map.of("rates.roll.rcRate", 1.5F, "pid.roll.p", 55.0F, "vtx.channel", 7.0F));
        TuneSpec merged = base.overlay(imported, EnumSet.of(TuneGroup.RATES));
        assertEquals(1.5F, merged.values().get("rates.roll.rcRate"));
        assertEquals(40.0F, merged.values().get("pid.roll.p"));
        assertEquals(3.0F, merged.values().get("vtx.channel"));
        assertEquals(55.0F, base.overlay(imported, TuneGroup.FLIGHT).values().get("pid.roll.p"));
        assertEquals(3.0F, base.overlay(imported, TuneGroup.FLIGHT).values().get("vtx.channel"));
    }

    @Test
    void builtinDefinitionsPassTheirOwnValidation() {
        for (PartKind kind : PartKind.values()) {
            for (PartInfo part : PartIndex.all(kind)) {
                assertEquals(kind, part.definition().kind());
            }
        }
        for (PartInfo part : PartIndex.all(PartKind.FRAME)) {
            FrameDefinition frame = (FrameDefinition) part.definition();
            assertTrue(frame.geometry().arms().isEmpty(), frame.id() + " nutzt das alte True-X-Modell");
        }
    }

    @Test
    void everySignatureOfApi16IsUnchanged() {
        Api16 api16 = Api16.load();
        assertTrue(api16.types.size() >= 100, "Liste der 1.6-Signaturen unvollständig: " + api16.types.size());
        List<String> problems = new ArrayList<>();
        Map<String, Current> cache = new HashMap<>();
        for (Map.Entry<String, String> type : api16.types.entrySet()) {
            Current current = cache.computeIfAbsent(type.getKey(), ApiContractTest::current);
            if (current == null) {
                problems.add("Typ fehlt: " + type.getKey());
                continue;
            }
            if (!current.isPublic) {
                problems.add("Typ nicht mehr öffentlich: " + type.getKey());
            }
            if (!type.getValue().equals(current.kind)) {
                problems.add("Art geändert: " + type.getKey() + " " + type.getValue() + " → " + current.kind);
            }
            if (!current.permitted.isEmpty() && !api16.sealed.contains(type.getKey())) {
                problems.add("Typ versiegelt: " + type.getKey());
            }
        }
        for (String[] line : api16.permitted) {
            Current current = cache.computeIfAbsent(line[1], ApiContractTest::current);
            if (current != null && !current.permitted.isEmpty() && !current.permitted.contains(line[2])) {
                problems.add("Erlaubter Untertyp fehlt: " + line[1] + " " + line[2]);
            }
        }
        for (String[] line : api16.supertypes) {
            Current current = cache.computeIfAbsent(line[1], ApiContractTest::current);
            if (current != null && !current.supertypes.contains(line[2])) {
                problems.add("Obertyp fehlt: " + line[1] + " " + line[2]);
            }
        }
        for (String[] line : api16.methods) {
            Current current = cache.computeIfAbsent(line[1], ApiContractTest::current);
            if (current == null) {
                continue;
            }
            Member member = current.methods.get(line[2]);
            if (member == null) {
                problems.add("Methode fehlt: " + line[1] + "#" + line[2]);
                continue;
            }
            if (!member.visible) {
                problems.add("Methode nicht mehr sichtbar: " + line[1] + "#" + line[2]);
            }
            if (!line[3].equals(member.scope)) {
                problems.add("Methode " + line[1] + "#" + line[2] + " jetzt " + member.scope);
            }
            if (line[4].equals("concrete") && member.abstractMethod) {
                problems.add("Methode " + line[1] + "#" + line[2] + " ist jetzt abstrakt");
            }
            if (!line[5].equals(member.signature)) {
                problems.add("Generische Signatur geändert: " + line[1] + "#" + line[2] + " " + line[5] + " → " + member.signature);
            }
        }
        for (String[] line : api16.fields) {
            Current current = cache.computeIfAbsent(line[1], ApiContractTest::current);
            if (current == null) {
                continue;
            }
            Member member = current.fields.get(line[2] + ":" + line[3]);
            if (member == null) {
                problems.add("Feld fehlt: " + line[1] + "#" + line[2] + " " + line[3]);
                continue;
            }
            if (!member.visible || !line[4].equals(member.scope) || !line[5].equals(member.finality) || !line[6].equals(member.signature)) {
                problems.add("Feld geändert: " + line[1] + "#" + line[2]);
            }
        }
        assertTrue(problems.isEmpty(), "1.6-Signaturen verändert:\n" + String.join("\n", problems));
    }

    @Test
    void everyNewMethodOnAnApi16InterfaceIsDefault() throws ClassNotFoundException {
        Api16 api16 = Api16.load();
        List<String> problems = new ArrayList<>();
        int added = 0;
        for (Map.Entry<String, String> type : api16.types.entrySet()) {
            boolean isInterface = type.getValue().equals("interface");
            if (!isInterface && !type.getValue().equals("abstract-class")) {
                continue;
            }
            Class<?> owner = Class.forName(type.getKey(), false, ApiContractTest.class.getClassLoader());
            for (Method method : owner.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                if (method.isSynthetic() || Modifier.isStatic(modifiers) || !(Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers))) {
                    continue;
                }
                String key = method.getName() + MethodType.methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString();
                if (api16.hasMethod(type.getKey(), key)) {
                    continue;
                }
                added++;
                boolean ok = isInterface ? method.isDefault() : !Modifier.isAbstract(modifiers);
                if (!ok) {
                    problems.add(type.getKey() + "#" + key);
                }
            }
        }
        assertTrue(added >= 20, "zu wenige neue Methoden an 1.6-Schnittstellen gefunden: " + added);
        assertTrue(problems.isEmpty(), "neue abstrakte Methoden an 1.6-Typen:\n" + String.join("\n", problems));
    }

    @Test
    void publishedApi17InterfacesOnlyGainDefaultMethods() throws ClassNotFoundException {
        Map<String, Set<String>> published = new LinkedHashMap<>();
        for (String line : resourceLines(PUBLISHED_17)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split(" ");
            switch (parts[0]) {
                case "T" -> published.put(parts[1], new HashSet<>());
                case "A" -> published.get(parts[1]).add(parts[2]);
                default -> throw new IllegalStateException("Unbekannte Zeile: " + line);
            }
        }
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Set<String>> type : published.entrySet()) {
            Class<?> owner = Class.forName(type.getKey(), false, ApiContractTest.class.getClassLoader());
            Set<String> present = new HashSet<>();
            for (Method method : owner.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                if (method.isSynthetic() || Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers)) {
                    continue;
                }
                String key = method.getName() + MethodType.methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString();
                present.add(key);
                if (Modifier.isAbstract(modifiers) && !type.getValue().contains(key)) {
                    problems.add("neu und abstrakt: " + type.getKey() + "#" + key);
                }
            }
            for (String key : type.getValue()) {
                if (!present.contains(key)) {
                    problems.add("fehlt: " + type.getKey() + "#" + key);
                }
            }
        }
        assertTrue(published.size() >= 30, "zu wenige veröffentlichte 1.7-Schnittstellen: " + published.size());
        assertTrue(published.containsKey(WorldRules.class.getName()) && published.containsKey(RaceData.class.getName()), PUBLISHED_17);
        assertTrue(problems.isEmpty(), "1.7 seit der Veröffentlichung nicht additiv:\n" + String.join("\n", problems));
    }

    @Test
    void api17TypesArePublic() throws ClassNotFoundException {
        for (String name : API17_TYPES) {
            Class<?> type = Class.forName(name, false, ApiContractTest.class.getClassLoader());
            assertTrue(Modifier.isPublic(type.getModifiers()), name);
            assertNotNull(current(name), name);
        }
        assertEquals(List.of(AutonomyResult.STARTED, AutonomyResult.NO_BUDGET, AutonomyResult.PILOTED, AutonomyResult.NOT_READY,
                AutonomyResult.DENIED, AutonomyResult.NOT_ARMABLE), List.of(AutonomyResult.values()));
        assertEquals(List.of(AutonomyEnd.PILOT, AutonomyEnd.LANDED, AutonomyEnd.BATTERY, AutonomyEnd.OWNER_OFFLINE, AutonomyEnd.OUT_OF_RANGE,
                AutonomyEnd.STOPPED, AutonomyEnd.TIMEOUT, AutonomyEnd.ERROR, AutonomyEnd.REPLACED, AutonomyEnd.TERRAIN_LIMIT),
                List.of(AutonomyEnd.values()));
        assertEquals("DENIED", DispenseResult.Status.DENIED.name());
        assertEquals(DispenseResult.Status.DENIED, DispenseResult.Status.values()[DispenseResult.Status.values().length - 1]);
        assertEquals(OsdGroup.ADDON, OsdGroup.values()[OsdGroup.values().length - 1]);
    }

    @Test
    void signaturesFromTheAddonDraftsExist() throws NoSuchMethodException, NoSuchFieldException {
        assertDefault(AccessoryBehavior.class.getMethod("actionNameKey", int.class), Optional.class);
        assertDefault(AccessoryBehavior.class.getMethod("display", int.class), Optional.class);
        assertDefault(AccessoryRegistry.class.getMethod("triggerAction", Entity.class, int.class, boolean.class, ServerPlayer.class), boolean.class);
        assertDefault(AccessoryContext.class.getMethod("dispense", int.class, DispenseOptions.class), DispenseResult.class);
        assertEquals(DispenseOptions.class, DispenseOptions.class.getMethod("consume", boolean.class).getReturnType());
        assertDefault(DroneAccess.class.getMethod("cameraPose", Entity.class), Optional.class);
        assertDefault(DroneAccess.class.getMethod("loaded", ServerLevel.class), List.class);
        assertDefault(DroneAccess.class.getMethod("registerCollisionRule", Identifier.class, CollisionRule.class), void.class);
        assertDefault(DroneAccess.class.getMethod("autonomy"), AutonomyAccess.class);
        assertDefault(DroneAccess.class.getMethod("operators"), OperatorAccess.class);
        assertTrue(CameraPose.class.isRecord());
        assertEquals(List.of("eye", "look", "up", "verticalFovDegrees"), components(CameraPose.class));
        assertEquals(WorldRules.class.getMethod("presetValue", WorldRule.class, String.class, Object.class).getReturnType(), void.class);
        assertDefault(WorldRules.class.getMethod("raceNeutral", WorldRule.class), void.class);
        assertDefault(WorldRules.class.getMethod("raceRelevant", WorldRule.class), boolean.class);
        assertDefault(RaceData.class.getMethod("standard", MinecraftServer.class), RaceStandardInfo.class);
        assertEquals(AutonomyOptions.class, AutonomyOptions.class.getMethod("maxDuration", Duration.class).getReturnType());
        assertEquals(AutonomyOptions.class, AutonomyOptions.class.getMethod("takeoff", double.class).getReturnType());
        assertEquals(AutonomyOptions.class, AutonomyOptions.class.getMethod("label", String.class).getReturnType());
        assertEquals(AutonomyOptions.class, AutonomyOptions.class.getMethod("of", Identifier.class).getReturnType());
        assertEquals(void.class, AutonomyAccess.class.getMethod("registerExitHandler", Identifier.class, ExitHandler.class).getReturnType());
        assertEquals(Optional.class, AutonomyAccess.class.getMethod("active", Entity.class).getReturnType());
        assertEquals(AutonomyResult.class, AutonomyAccess.class.getMethod("start", Entity.class, ServerPlayer.class, FlightAssist.class,
                AutonomyOptions.class).getReturnType());
        assertEquals(Optional.class, ExitHandler.class.getMethod("onPilotExit", Entity.class, ServerPlayer.class).getReturnType());
        assertEquals(AutonomyRequest.class, AutonomyRequest.class.getMethod("of", FlightAssist.class, AutonomyOptions.class).getReturnType());
        assertDefault(AssistOutput.class.getMethod("land"), void.class);
        assertDefault(GimbalProvider.class.getMethod("modelPose", GimbalContext.class, Quaternionf.class), boolean.class);
        assertDefault(GimbalProvider.class.getMethod("fovDegrees", GimbalContext.class), double.class);
        assertDefault(GimbalProvider.class.getMethod("stabilizesRecording"), boolean.class);
        assertDefault(GimbalProvider.class.getMethod("showsProps", GimbalContext.class), boolean.class);
        assertDefault(GimbalProvider.class.getMethod("gimbalRotation", GimbalContext.class, Quaternionf.class), boolean.class);
        assertDefault(GimbalProvider.class.getMethod("gimbalFovDegrees", GimbalContext.class), double.class);
        assertDefault(GimbalContext.class.getMethod("axis", String.class), double.class);
        assertDefault(GimbalContext.class.getMethod("operatorAxis", int.class), double.class);
        assertDefault(GimbalContext.class.getMethod("operatorAction", int.class), boolean.class);
        assertDefault(FlightState.class.getMethod("addonSwitch", String.class), boolean.class);
        assertDefault(FlightState.class.getMethod("addonAxis", String.class), double.class);
        assertDefault(FlightState.class.getMethod("operatorAxis", int.class), double.class);
        assertDefault(FlightState.class.getMethod("terrainLimit"), boolean.class);
        assertDefault(ClientInput.class.getMethod("switchActive", Identifier.class), boolean.class);
        assertDefault(ClientInput.class.getMethod("axis", Identifier.class), double.class);
        assertDefault(ClientInput.class.getMethod("operating"), boolean.class);
        assertStatic(PropwashClientApi.class.getMethod("recording"), RecordingControl.class);
        assertStatic(PropwashClientApi.class.getMethod("spectate", Entity.class), boolean.class);
        assertStatic(PropwashClientApi.class.getMethod("registerOsdElement", OsdElementSpec.class), void.class);
        assertStatic(PropwashClientApi.class.getMethod("registerOsdWarning", Identifier.class, OsdWarning.class), void.class);
        assertStatic(PropwashClientApi.class.getMethod("bindSwitchKey", Identifier.class, KeyMapping.class), void.class);
        assertStatic(PropwashClientApi.class.getMethod("bindAxisKeys", Identifier.class, KeyMapping.class, KeyMapping.class), void.class);
        assertStatic(PropwashClientApi.class.getMethod("registerServiceExtension", ServiceExtension.class), void.class);
        assertStatic(PropwashApi.class.getMethod("switches"), SwitchRegistry.class);
        assertStatic(PropwashApi.class.getMethod("worldRules"), WorldRules.class);
        assertStatic(PropwashApi.class.getMethod("droneData"), DroneDataAccess.class);
        assertStatic(PropwashApi.class.getMethod("shareCodes"), ShareCodes.class);
        for (String event : List.of("ADDON_SWITCH_CHANGED", "DRONE_KNOCKBACK", "AUTONOMY_ENDED", "PILOT_CHANGED")) {
            assertTrue(Modifier.isStatic(PropwashEvents.class.getField(event).getModifiers()), event);
        }
        assertEquals(void.class, PropwashEvents.PilotChanged.class.getMethod("onPilotChanged", Entity.class, Optional.class, Optional.class)
                .getReturnType());
        assertEquals(void.class, PropwashEvents.AutonomyEnded.class.getMethod("onAutonomyEnded", Entity.class, UUID.class, Identifier.class,
                AutonomyEnd.class).getReturnType());
    }

    @Test
    void api18CargoAndRecessExist() throws NoSuchMethodException {
        assertDefault(AccessoryRegistry.class.getMethod("cargo", Entity.class), List.class);
        assertDefault(AccessoryRegistry.class.getMethod("grabCargo", Entity.class, ServerPlayer.class), boolean.class);
        assertDefault(AccessoryRegistry.class.getMethod("releaseCargo", Entity.class, ServerPlayer.class), int.class);
        List<String> names = components(AccessoryDefinition.class);
        assertEquals(List.of("recessMm", "dropMm"), names.subList(names.size() - 2, names.size()));
        assertEquals(double.class, AccessoryDefinition.class.getMethod("recessMm").getReturnType());
        assertEquals(double.class, AccessoryDefinition.class.getMethod("dropMm").getReturnType());
        assertEquals(AccessoryDefinition.Builder.class, AccessoryDefinition.Builder.class.getMethod("recess", double.class).getReturnType());
        assertEquals(AccessoryDefinition.Builder.class, AccessoryDefinition.Builder.class.getMethod("drop", double.class).getReturnType());
    }

    @Test
    void ledPathAndTheOldConstructorOfAccessoryDefinitionStay() throws NoSuchMethodException {
        assertEquals(List.class, AccessoryDefinition.class.getMethod("ledPathMm").getReturnType());
        AccessoryDefinition.class.getConstructor(Identifier.class, PartBasics.class, AccessorySlot.class, double.class, double.class, double.class,
                double.class, double.class, int.class, Optional.class, double.class, double.class);
        AccessoryDefinition.class.getConstructor(Identifier.class, PartBasics.class, AccessorySlot.class, double.class, double.class, double.class,
                double.class, double.class, int.class, Optional.class, double.class, double.class, List.class);
    }

    @Test
    void defaultMethodsGiveNeutralResults() {
        AccessoryBehavior behavior = proxy(AccessoryBehavior.class);
        assertEquals(Optional.empty(), behavior.actionNameKey(1));
        assertEquals(Optional.empty(), behavior.display(0));
        assertFalse(proxy(AccessoryRegistry.class).triggerAction(null, 1, true, null));
        DispenseResult dispensed = proxy(AccessoryContext.class).dispense(0, DispenseOptions.defaults());
        assertEquals(DispenseResult.Status.REFUSED, dispensed.status());
        assertFalse(dispensed.success());
        assertEquals(Optional.empty(), dispensed.entity());
        assertEquals(0.0, dispensed.recoilNs());
        DroneAccess drones = proxy(DroneAccess.class);
        assertEquals(List.of(), drones.loaded(null));
        assertEquals(Optional.empty(), drones.cameraPose(null));
        assertSame(Api17Registries.AUTONOMY, drones.autonomy());
        assertSame(Api17Registries.OPERATORS, drones.operators());
        proxy(AssistOutput.class).land();
        FlightState state = proxy(FlightState.class);
        assertFalse(state.addonSwitch("x:y"));
        assertEquals(0.0, state.addonAxis("x:y"));
        assertEquals(0.0, state.operatorAxis(0));
        assertFalse(state.terrainLimit());
        GimbalContext gimbal = proxy(GimbalContext.class);
        assertEquals(0.0, gimbal.axis("x:y"));
        assertEquals(0.0, gimbal.operatorAxis(3));
        assertFalse(gimbal.operatorAction(1));
        GimbalProvider provider = proxy(GimbalProvider.class);
        assertFalse(provider.stabilizesRecording());
        assertFalse(provider.modelPose(gimbal, new Quaternionf()));
        assertTrue(Double.isNaN(provider.fovDegrees(gimbal)));
        assertTrue(provider.suppressesShake());
        assertTrue(provider.showsProps(gimbal));
        Quaternionf untouched = new Quaternionf(0.1F, 0.2F, 0.3F, 0.9F);
        assertFalse(provider.gimbalRotation(gimbal, untouched));
        assertEquals(new Quaternionf(0.1F, 0.2F, 0.3F, 0.9F), untouched);
        assertTrue(Double.isNaN(provider.gimbalFovDegrees(gimbal)));
        ClientInput input = proxy(ClientInput.class);
        assertFalse(input.switchActive(id("ghost")));
        assertEquals(0.0, input.axis(id("tilt")));
        assertFalse(input.operating());
        WorldRules rules = proxy(WorldRules.class);
        WorldRule<?> rule = proxy(WorldRule.class);
        assertTrue(rules.raceRelevant(rule));
        rules.raceNeutral(rule);
        assertTrue(rules.raceRelevant(rule));
        assertFalse(rules.raceRelevant(null));
        assertSame(RaceStandardInfo.DEFAULTS, proxy(RaceData.class).standard(null));
    }

    @Test
    void registriesStoreWithoutBehaviour() {
        AddonSwitch ghost = PropwashApi.switches().register(SwitchSpec.builder(id("ghost")).serverSync(true).latchesOnGamepad(true).build());
        assertEquals(id("ghost"), ghost.id());
        assertTrue(ghost.spec().toggle());
        assertEquals(TEST_NAMESPACE + ".switch.ghost", ghost.spec().nameKey());
        assertFalse(ghost.active(null));
        assertSame(ghost, PropwashApi.switches().find(id("ghost")).orElseThrow());
        assertTrue(PropwashApi.switches().switches().contains(ghost));
        assertThrows(IllegalArgumentException.class, () -> PropwashApi.switches().register(SwitchSpec.builder(id("ghost")).build()));
        assertThrows(IllegalArgumentException.class, () -> SwitchSpec.builder(id("x".repeat(60))).build());
        AddonAxis tilt = PropwashApi.switches().registerAxis(AxisSpec.builder(id("tilt")).center(0.25).keyRateDps(60).build());
        assertEquals(0.25, tilt.value(null));
        assertEquals(TEST_NAMESPACE + ".axis.tilt", tilt.spec().nameKey());
        assertThrows(IllegalArgumentException.class, () -> AxisSpec.builder(id("bad")).center(1.5).build());
        assertEquals(64, SwitchRegistry.MAX_SWITCHES);
        assertEquals(16, SwitchRegistry.MAX_AXES);

        WorldRule<Boolean> buckets = PropwashApi.worldRules().registerBoolean(id("allow_buckets"), false);
        WorldRule<Integer> cooldown = PropwashApi.worldRules().registerInt(id("cooldown_ticks"), 4, 0, 100);
        assertFalse(buckets.get(null));
        assertEquals(4, cooldown.get(null));
        assertEquals("gamerule." + TEST_NAMESPACE + ".cooldown_ticks", cooldown.nameKey());
        assertEquals("gamerule." + TEST_NAMESPACE + ".cooldown_ticks.description", cooldown.descriptionKey());
        PropwashApi.worldRules().presetValue(cooldown, WorldRules.PRESET_TRAINING, 0);
        PropwashApi.worldRules().presetValue(buckets, WorldRules.PRESET_ARCADE, true);
        assertEquals(Map.of(WorldRules.PRESET_TRAINING, 0), Api17Registries.WORLD_RULES.presetValues(cooldown.id()));
        assertThrows(IllegalArgumentException.class, () -> PropwashApi.worldRules().presetValue(cooldown, "hardcore", 1));
        assertThrows(IllegalArgumentException.class, () -> PropwashApi.worldRules().presetValue(cooldown, WorldRules.PRESET_ARCADE, 101));
        WorldRule<Boolean> foreign = new WorldRule<>() {
            public Identifier id() {
                return ApiContractTest.id("foreign");
            }

            public Type type() {
                return Type.BOOLEAN;
            }

            public Boolean defaultValue() {
                return false;
            }

            public int min() {
                return 0;
            }

            public int max() {
                return 1;
            }

            public Boolean get(Level level) {
                return false;
            }
        };
        assertThrows(IllegalArgumentException.class, () -> PropwashApi.worldRules().presetValue(foreign, WorldRules.PRESET_TRAINING, true));
        assertThrows(IllegalArgumentException.class, () -> PropwashApi.worldRules().registerInt(id("bad_range"), 5, 10, 0));

        DroneDataKey<Integer> mission = PropwashApi.droneData().register(id("mission"), Codec.INT, 7, DroneDataKey.Sync.TRACKING);
        assertEquals(7, mission.defaultValue());
        assertSame(mission, PropwashApi.droneData().find(id("mission")).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> PropwashApi.droneData().register(id("mission"), Codec.INT, 1, DroneDataKey.Sync.NONE));

        PropwashApi.drones().registerCollisionRule(id("ghost"), query -> CollisionFlags.GHOST);
        assertTrue(Api17Registries.collisionRules().stream().anyMatch(entry -> entry.id().equals(id("ghost"))));
        assertThrows(IllegalArgumentException.class, () -> PropwashApi.drones().registerCollisionRule(id("ghost"), query -> CollisionFlags.SOLID));
        PropwashApi.drones().autonomy().registerExitHandler(id("follow"), (drone, pilot) -> Optional.empty());
        assertEquals(1, Api17Registries.AUTONOMY.exitHandlers().stream().filter(entry -> entry.id().equals(id("follow"))).count());

        ShareResult decoded = PropwashApi.shareCodes().decode("PW3-abc");
        assertFalse(decoded.ok());
        assertEquals(ShareResult.Status.EMPTY, PropwashApi.shareCodes().decode(" ").status());
        String encoded = PropwashApi.shareCodes().encode(DroneData.of(BuildSpec.of(id("f"), id("s"), id("m"), id("p"), id("v"), id("b"))),
                ShareOptions.defaults());
        assertTrue(encoded.startsWith("PW3-"), encoded);
        assertTrue(PropwashApi.shareCodes().decode(encoded).ok());
        assertFalse(PropwashApi.shareCodes().convert("PW2-abc").ok());
    }

    @Test
    void valueTypesValidateTheirRanges() {
        assertEquals(CollisionFlags.GHOST, CollisionFlags.SOLID.and(CollisionFlags.GHOST));
        assertEquals(new CollisionFlags(false, true, true, true), CollisionFlags.SOLID.withBlocks(false));
        assertTrue(CollisionFlags.SOLID.solid());
        AutonomyOptions options = AutonomyOptions.of(id("follow")).label("FOLLOW").maxDuration(Duration.ofMinutes(10)).takeoff(5.0);
        assertEquals("FOLLOW", options.label());
        assertEquals(OptionalDouble.of(5.0), options.takeoff());
        assertEquals(AutonomyOptions.MAX_DURATION, AutonomyOptions.of(id("x")).maxDuration());
        assertThrows(IllegalArgumentException.class, () -> AutonomyOptions.of(id("x")).maxDuration(Duration.ofMinutes(61)));
        assertThrows(IllegalArgumentException.class, () -> AutonomyOptions.of(id("x")).takeoff(31.0));
        assertThrows(IllegalArgumentException.class, () -> AutonomyOptions.of(id("x")).label("ZU_LANGES"));
        DispenseOptions defaults = DispenseOptions.defaults();
        assertFalse(defaults.allowFluids());
        assertTrue(defaults.applyRecoil());
        assertTrue(defaults.consume());
        assertFalse(defaults.consume(false).consume());
        assertThrows(IllegalArgumentException.class, () -> defaults.speedScale(-1.0));
        assertEquals(new Vec3Mm(1.0, 2.0, 3.0), ItemDisplay.at(1.0, 2.0, 3.0).offsetMm());
        assertThrows(IllegalArgumentException.class, () -> ItemDisplay.at(0.0, 0.0, 0.0).scaled(0.0F));
        OsdElementSpec ammo = OsdElementSpec.builder(id("ammo")).position(1, 3).maxChars(8).preview("AMMO 12")
                .content(context -> OsdText.of("AMMO", OsdTone.NORMAL)).build();
        assertEquals(OsdGroup.ADDON, ammo.group());
        assertFalse(ammo.enabledByDefault());
        assertEquals(TEST_NAMESPACE + ".osd.ammo", ammo.nameKey());
        assertThrows(IllegalArgumentException.class, () -> OsdElementSpec.builder(id("far")).position(30, 0).content(context -> null).build());
        assertThrows(IllegalArgumentException.class, () -> OsdElementSpec.builder(id("none")).build());
        assertEquals(OsdTone.NORMAL, OsdText.of("x").tone());
        assertEquals(ShareOptions.DEFAULT_LAYERS, ShareOptions.defaults().layers());
        assertTrue(ShareOptions.defaults().withOsd("{}").osdJson().isPresent());
        assertEquals(Set.of(ShareOptions.Layer.OSD), ShareOptions.defaults().withLayers(Set.of(ShareOptions.Layer.OSD)).layers());
        assertEquals(DispenseResult.Status.FIRED, DispenseResult.of(DispenseResult.Status.FIRED).status());
        assertTrue(DispenseResult.Status.PLACED.success());
        assertFalse(DispenseResult.Status.DENIED.success());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TEST_NAMESPACE, path);
    }

    private static void assertDefault(Method method, Class<?> returnType) {
        assertTrue(method.isDefault(), method + " ist keine default-Methode");
        assertEquals(returnType, method.getReturnType(), method.toString());
    }

    private static void assertStatic(Method method, Class<?> returnType) {
        assertTrue(Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers()), method.toString());
        assertEquals(returnType, method.getReturnType(), method.toString());
    }

    private static List<String> components(Class<?> record) {
        List<String> names = new ArrayList<>();
        for (var component : record.getRecordComponents()) {
            names.add(component.getName());
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            if (method.getName().equals("toString") && method.getParameterCount() == 0) {
                return type.getSimpleName();
            }
            throw new UnsupportedOperationException(method.getName());
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Current current(String binaryName) {
        String resource = binaryName.replace('.', '/') + ".class";
        try (InputStream in = ApiContractTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return Current.of(ClassFile.of().parse(in.readAllBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Member(boolean visible, String scope, boolean abstractMethod, String finality, String signature) {
    }

    private record Current(String kind, boolean isPublic, Set<String> permitted, Set<String> supertypes, Map<String, Member> methods,
                           Map<String, Member> fields) {
        static Current of(ClassModel model) {
            int flags = model.flags().flagsMask();
            boolean isFinal = (flags & ClassFile.ACC_FINAL) != 0;
            String kind;
            if ((flags & ClassFile.ACC_ANNOTATION) != 0) {
                kind = "annotation";
            } else if ((flags & ClassFile.ACC_INTERFACE) != 0) {
                kind = "interface";
            } else if ((flags & ClassFile.ACC_ENUM) != 0) {
                kind = "enum";
            } else if (model.findAttribute(Attributes.record()).isPresent()) {
                kind = "record";
            } else if ((flags & ClassFile.ACC_ABSTRACT) != 0) {
                kind = "abstract-class";
            } else if (isFinal) {
                kind = "final-class";
            } else {
                kind = "class";
            }
            Set<String> supertypes = new HashSet<>();
            model.superclass().ifPresent(type -> supertypes.add(type.asInternalName()));
            model.interfaces().forEach(type -> supertypes.add(type.asInternalName()));
            Map<String, Member> methods = new HashMap<>();
            for (MethodModel method : model.methods()) {
                int mf = method.flags().flagsMask();
                methods.put(method.methodName().stringValue() + method.methodType().stringValue(), new Member(
                        (mf & (ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED)) != 0,
                        (mf & ClassFile.ACC_STATIC) != 0 ? "static" : "instance",
                        (mf & ClassFile.ACC_ABSTRACT) != 0,
                        "",
                        method.findAttribute(Attributes.signature()).map(s -> s.signature().stringValue()).orElse("-")));
            }
            Map<String, Member> fields = new HashMap<>();
            for (FieldModel field : model.fields()) {
                int ff = field.flags().flagsMask();
                fields.put(field.fieldName().stringValue() + ":" + field.fieldType().stringValue(), new Member(
                        (ff & (ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED)) != 0,
                        (ff & ClassFile.ACC_STATIC) != 0 ? "static" : "instance",
                        false,
                        (ff & ClassFile.ACC_FINAL) != 0 ? "final" : "mutable",
                        field.findAttribute(Attributes.signature()).map(s -> s.signature().stringValue()).orElse("-")));
            }
            Set<String> permitted = new HashSet<>();
            model.findAttribute(Attributes.permittedSubclasses())
                    .ifPresent(attribute -> attribute.permittedSubclasses().forEach(type -> permitted.add(type.asInternalName())));
            return new Current(kind, (flags & ClassFile.ACC_PUBLIC) != 0, permitted, supertypes, methods, fields);
        }
    }

    private record Api16(Map<String, String> types, List<String[]> supertypes, List<String[]> permitted, Set<String> sealed,
                         List<String[]> methods, List<String[]> fields, Set<String> methodKeys) {
        static Api16 load() {
            Map<String, String> types = new LinkedHashMap<>();
            List<String[]> supertypes = new ArrayList<>();
            List<String[]> permitted = new ArrayList<>();
            Set<String> sealed = new HashSet<>();
            List<String[]> methods = new ArrayList<>();
            List<String[]> fields = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            for (String line : resourceLines(SIGNATURES)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(" ");
                switch (parts[0]) {
                    case "T" -> types.put(parts[1], parts[2]);
                    case "S" -> supertypes.add(parts);
                    case "P" -> {
                        permitted.add(parts);
                        sealed.add(parts[1]);
                    }
                    case "M" -> {
                        methods.add(parts);
                        keys.add(parts[1] + "#" + parts[2]);
                    }
                    case "F" -> fields.add(parts);
                    default -> throw new IllegalStateException("Unbekannte Zeile: " + line);
                }
            }
            return new Api16(types, supertypes, permitted, sealed, methods, fields, keys);
        }

        boolean hasMethod(String type, String key) {
            return this.methodKeys.contains(type + "#" + key);
        }
    }

    private static List<String> resourceLines(String resource) {
        try (InputStream in = ApiContractTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            }
            for (Path candidate : List.of(Path.of("common/src/test/resources", resource), Path.of("src/test/resources", resource))) {
                if (Files.exists(candidate)) {
                    return Files.readAllLines(candidate, StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new IllegalStateException(resource + " nicht gefunden");
    }
}
