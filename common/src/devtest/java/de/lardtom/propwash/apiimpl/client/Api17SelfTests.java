package de.lardtom.propwash.apiimpl.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.DispenseOptions;
import de.lardtom.propwash.api.accessory.DispenseResult;
import de.lardtom.propwash.api.accessory.ItemDisplay;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.RecordingControl;
import de.lardtom.propwash.api.client.ServiceExtension;
import de.lardtom.propwash.api.client.osd.OsdAlign;
import de.lardtom.propwash.api.client.osd.OsdContent;
import de.lardtom.propwash.api.client.osd.OsdContext;
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
import de.lardtom.propwash.api.drone.BlockContact;
import de.lardtom.propwash.api.drone.CameraPose;
import de.lardtom.propwash.api.drone.CollisionFlags;
import de.lardtom.propwash.api.drone.CollisionQuery;
import de.lardtom.propwash.api.drone.CollisionRule;
import de.lardtom.propwash.api.drone.DroneDataAccess;
import de.lardtom.propwash.api.drone.DroneDataKey;
import de.lardtom.propwash.api.drone.ExitHandler;
import de.lardtom.propwash.api.drone.OperatorAccess;
import de.lardtom.propwash.api.event.DroneKnockback;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.input.AddonAxis;
import de.lardtom.propwash.api.input.AddonSwitch;
import de.lardtom.propwash.api.input.AxisSpec;
import de.lardtom.propwash.api.input.SwitchRegistry;
import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.api.share.ShareCodes;
import de.lardtom.propwash.api.share.ShareOptions;
import de.lardtom.propwash.api.share.ShareResult;
import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.api.worldrule.WorldRules;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class Api17SelfTests {
    private static final String RESULT = "result";
    private static final String FUTURE = "future";
    private static final List<Class<?>> TYPES = List.of(SwitchRegistry.class, SwitchSpec.class, AxisSpec.class, AddonSwitch.class,
            AddonAxis.class, OsdElementSpec.class, OsdAlign.class, OsdGroup.class, OsdTone.class, OsdText.class, OsdContent.class,
            OsdContext.class, OsdWarning.class, ServiceExtension.class, RecordingControl.class, CollisionRule.class, CollisionFlags.class,
            CollisionQuery.class, BlockContact.class, CameraPose.class, DroneDataAccess.class, DroneDataKey.class, AutonomyAccess.class,
            AutonomyOptions.class, AutonomyRequest.class, AutonomyResult.class, AutonomyEnd.class, ExitHandler.class, OperatorAccess.class,
            DispenseOptions.class, DispenseResult.class, ItemDisplay.class, DroneKnockback.class, WorldRules.class, WorldRule.class,
            ShareCodes.class, ShareResult.class, ShareOptions.class);

    private Api17SelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("api: Vertrag 1.7 – neue Typen laden, Fassaden und Ereignisse erreichbar (Client und Server)", 200,
                Api17SelfTests::contractStep);
    }

    @SuppressWarnings("unchecked")
    private static StepResult contractStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            AtomicReference<List<String>> result = new AtomicReference<>();
            ctx.state().put(RESULT, result);
            ctx.state().put(FUTURE, ctx.onServer(server -> result.set(serverCheck(server))));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Prüfung fehlgeschlagen: " + future.exceptionNow());
        }
        List<String> errors = new ArrayList<>(((AtomicReference<List<String>>) ctx.state().get(RESULT)).get());
        errors.addAll(clientCheck());
        if (errors.isEmpty()) {
            ctx.log("API " + PropwashApi.API_VERSION + ": " + TYPES.size() + " neue Typen geladen, Fassaden und vier neue Ereignisse erreichbar");
        }
        return errors.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", errors));
    }

    private static List<String> serverCheck(MinecraftServer server) {
        List<String> errors = new ArrayList<>();
        ServerLevel level = server.overworld();
        if (PropwashApi.drones().loaded(level) == null) {
            errors.add("loaded(level) liefert null");
        }
        if (PropwashApi.drones().autonomy() == null || PropwashApi.drones().operators() == null) {
            errors.add("autonomy() oder operators() fehlt");
        }
        if (PropwashApi.switches() == null || PropwashApi.worldRules() == null || PropwashApi.droneData() == null
                || PropwashApi.shareCodes() == null) {
            errors.add("Fassade fehlt am Server");
        }
        if (PropwashEvents.PILOT_CHANGED == null || PropwashEvents.ADDON_SWITCH_CHANGED == null || PropwashEvents.DRONE_KNOCKBACK == null
                || PropwashEvents.AUTONOMY_ENDED == null) {
            errors.add("neues Ereignis fehlt");
        }
        return errors;
    }

    private static List<String> clientCheck() {
        List<String> errors = new ArrayList<>();
        if (!"1.8.0".equals(PropwashApi.API_VERSION) || !PropwashApi.isCompatible("1.7") || !PropwashApi.isCompatible("1.8")) {
            errors.add("API-Version " + PropwashApi.API_VERSION);
        }
        try {
            PropwashApi.requireVersion("propwash_example", "1.7");
        } catch (IllegalStateException e) {
            errors.add("requireVersion 1.7 wirft: " + e.getMessage());
        }
        for (Class<?> type : TYPES) {
            try {
                Class.forName(type.getName(), true, Api17SelfTests.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                errors.add(type.getSimpleName() + " lädt nicht: " + e);
            }
        }
        if (PropwashClientApi.recording() == null) {
            errors.add("recording() fehlt");
        }
        Identifier probe = Identifier.fromNamespaceAndPath("propwash_example", "api17_probe");
        PropwashClientApi.input().switchActive(probe);
        PropwashClientApi.input().axis(probe);
        PropwashClientApi.input().operating();
        if (CollisionFlags.SOLID.and(CollisionFlags.GHOST).blocks() || !DispenseOptions.defaults().consume()) {
            errors.add("Werte-Typen falsch");
        }
        return errors;
    }
}
