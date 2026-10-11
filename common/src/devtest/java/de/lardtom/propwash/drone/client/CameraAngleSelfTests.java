package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DroneServiceMenu;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.ui.client.SliderTypingSelfTests;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.video.client.SpectateSession;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class CameraAngleSelfTests {
    public static final String STEP = "drone: Kamerawinkel pro Drohne im Wartungsmenü (Bereich des Halters, Server, Item, Rahmen beim Zerlegen)";
    private static final String PHASE = "phase";
    private static final String SINCE = "since";
    private static final String FUTURE = "future";
    private static final String RESULT = "result";
    private static final DroneBuild BUILD = DroneBuild.RACER;
    private static final float CUSTOM = 52.0F;
    private static final AtomicInteger DRONE = new AtomicInteger(-1);

    private CameraAngleSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(STEP, 900, CameraAngleSelfTests::step);
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        CameraMount.Range range = CameraMount.range(BUILD.frame());
        if (phase == 0) {
            state.put(FUTURE, ctx.onServer(CameraAngleSelfTests::open));
            return next(ctx, 1);
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
        if (future != null && future.isCompletedExceptionally()) {
            return finish(ctx, "Server: " + future.exceptionNow());
        }
        if (future != null && !future.isDone()) {
            return since(ctx) > 200 ? finish(ctx, "Server antwortet nicht") : StepResult.pending();
        }
        QuadcopterEntity drone = clientDrone(mc);
        DroneServiceScreen screen = mc.gui.screen() instanceof DroneServiceScreen open ? open : null;
        StepSlider slider = screen == null ? null : screen.uptiltSlider();
        switch (phase) {
            case 1 -> {
                if (drone == null || screen == null || slider == null) {
                    return since(ctx) > 200 ? finish(ctx, "Wartungsmenü mit Kamerawinkel öffnet nicht: " + mc.gui.screen()) : StepResult.pending();
                }
                if (slider.current() != range.defaultDeg() || CameraMount.uptilt(drone.build(), drone.tune().values()) != range.defaultDeg()) {
                    return finish(ctx, "neue Drohne nicht mit dem Rahmen-Standard " + range.defaultDeg() + "°: Regler " + slider.current());
                }
                String typing = SliderTypingSelfTests.check(screen, null, slider);
                if (typing != null) {
                    return finish(ctx, "Kamerawinkel im Wartungsmenü tippen: " + typing);
                }
                screen.setFocused(slider);
                SliderTypingSelfTests.typeText(Integer.toString(Math.round(CUSTOM)));
                SliderTypingSelfTests.clickAt(screen, 0.0, 0.0);
                if (screen.getFocused() == slider || slider.isEditing() || slider.current() != CUSTOM || SliderTypingSelfTests.textInputActive()) {
                    return finish(ctx, "Klick daneben übernimmt den getippten Kamerawinkel " + CUSTOM + "° nicht: " + slider.current());
                }
                return next(ctx, 2);
            }
            case 2 -> {
                if (drone == null || CameraMount.uptilt(drone.build(), drone.tune().values()) != CUSTOM) {
                    return since(ctx) > 100 ? finish(ctx, "Kamerawinkel " + CUSTOM + "° kommt nicht über den Server zurück: "
                            + (drone == null ? "keine Drohne" : drone.tune().values().get(TuneMapping.CAMERA_UPTILT))) : StepResult.pending();
                }
                if (Math.abs(SpectateSession.INSTANCE.cameraUptiltDeg(drone) - CUSTOM) > 1.0E-4F) {
                    return finish(ctx, "Zuschauer sehen einen anderen Winkel: " + SpectateSession.INSTANCE.cameraUptiltDeg(drone));
                }
                if (screen != null && slider != null) {
                    screen.setFocused(slider);
                    SliderTypingSelfTests.typeText(Integer.toString(Math.round(range.maxDeg() + 15.0F)));
                    SliderTypingSelfTests.pressEnter();
                    boolean notice = slider.editor().noticeActive();
                    screen.setFocused(null);
                    if (!notice || slider.current() != range.maxDeg()) {
                        return finish(ctx, "getippter Kamerawinkel über dem Halter nicht mit Hinweis auf " + range.maxDeg() + "° begrenzt: "
                                + slider.current());
                    }
                }
                return next(ctx, 3);
            }
            case 3 -> {
                if (drone == null || CameraMount.uptilt(drone.build(), drone.tune().values()) != range.maxDeg()) {
                    return since(ctx) > 100 ? finish(ctx, "Kamerawinkel über dem Halter nicht auf " + range.maxDeg() + "° begrenzt") : StepResult.pending();
                }
                AtomicReference<String> result = new AtomicReference<>();
                state.put(RESULT, result);
                state.put(FUTURE, ctx.onServer(server -> result.set(serverChecks(server, range))));
                return next(ctx, 4);
            }
            case 4 -> {
                @SuppressWarnings("unchecked")
                String problem = ((AtomicReference<String>) state.get(RESULT)).get();
                if (problem != null && !problem.isEmpty()) {
                    return finish(ctx, problem);
                }
                if (screen != null) {
                    screen.changeUptilt(range.defaultDeg());
                }
                return next(ctx, 5);
            }
            default -> {
                boolean reset = drone != null && !drone.tune().values().containsKey(TuneMapping.CAMERA_UPTILT);
                if (!reset) {
                    return since(ctx) > 100 ? finish(ctx, "Standardwinkel wird gespeichert statt entfernt: " + (drone == null ? "keine Drohne"
                            : drone.tune().values())) : StepResult.pending();
                }
                ctx.log("Kamerawinkel Race 5″: Standard " + range.defaultDeg() + "°, Bereich " + range.minDeg() + "–" + range.maxDeg()
                        + "°, eigener Winkel über Server, Item und Rahmen erhalten");
                return finish(ctx, null);
            }
        }
    }

    private static String serverChecks(MinecraftServer server, CameraMount.Range range) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        QuadcopterEntity drone = serverDrone(server);
        if (drone == null) {
            return "keine Drohne auf dem Server";
        }
        if (!(player.containerMenu instanceof DroneServiceMenu menu)) {
            return "Wartungsmenü auf dem Server nicht offen";
        }
        if (menu.clickMenuButton(player, DroneServiceMenu.UPTILT_BUTTON - 1) || menu.clickMenuButton(player, DroneServiceMenu.UPTILT_BUTTON_MAX + 1)) {
            return "fremde Menü-Knöpfe ändern den Kamerawinkel";
        }
        float stored = CameraMount.uptilt(drone.build(), drone.tune().values());
        if (stored != range.maxDeg()) {
            return "Server speichert " + stored + "° statt " + range.maxDeg() + "°";
        }
        ItemStack item = drone.toStack();
        FlightTune itemTune = item.getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY);
        if (CameraMount.uptilt(BUILD, itemTune.values()) != range.maxDeg()) {
            return "Drohnen-Item verliert den Kamerawinkel: " + itemTune.values();
        }
        QuadcopterEntity copy = DroneModule.DRONE.get().create(drone.level(), EntitySpawnReason.COMMAND);
        if (copy == null) {
            return "Kopie nicht erzeugt";
        }
        copy.loadFromStack(item);
        if (CameraMount.uptilt(copy.build(), copy.tune().values()) != range.maxDeg()) {
            return "wieder aufgestellte Drohne verliert den Kamerawinkel";
        }
        copy.discard();
        List<ItemStack> parts = DroneAssembly.disassemble(item);
        ItemStack frame = parts.get(BuildSlots.FRAME);
        ItemStack stack = parts.get(BuildSlots.STACK);
        Float onFrame = DroneAssembly.frameUptilt(frame);
        FlightTune stackTune = stack.get(CoreComponents.FLIGHT_TUNE.get());
        if (onFrame == null || onFrame != range.maxDeg()) {
            return "zerlegter Rahmen trägt den Kamerawinkel nicht: " + onFrame;
        }
        if (stackTune != null && stackTune.values().containsKey(TuneMapping.CAMERA_UPTILT)) {
            return "Kamerawinkel landet beim Zerlegen am Stack statt am Rahmen";
        }
        ItemStack rebuilt = DroneAssembly.createDrone(BUILD, stack, frame, parts.get(BuildSlots.BATTERY));
        FlightTune rebuiltTune = rebuilt.getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY);
        if (CameraMount.uptilt(BUILD, rebuiltTune.values()) != range.maxDeg()) {
            return "neu gebaute Drohne verliert den Kamerawinkel des Rahmens: " + rebuiltTune.values();
        }
        ItemStack otherFrame = DroneAssembly.createDrone(DroneBuild.WHOOP, ItemStack.EMPTY, frame, ItemStack.EMPTY);
        float whoop = CameraMount.uptilt(DroneBuild.WHOOP, otherFrame.getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY).values());
        if (!CameraMount.range(DroneBuild.WHOOP.frame()).contains(whoop)) {
            return "Kamerawinkel außerhalb des Whoop-Halters: " + whoop;
        }
        return "";
    }

    private static void open(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), BUILD);
        drone.loadFromStack(stack);
        Vec3 pos = player.position().add(0.0, 0.0, 3.0);
        drone.snapTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
        level.addFreshEntity(drone);
        DRONE.set(drone.getId());
        Services.PLATFORM.openMenu(player, new SimpleMenuProvider((containerId, inventory, p) -> new DroneServiceMenu(containerId, inventory, drone),
                DroneServiceMenu.TITLE), ByteBufCodecs.VAR_INT, drone.getId());
    }

    private static @Nullable QuadcopterEntity clientDrone(Minecraft mc) {
        Entity entity = mc.level == null ? null : mc.level.getEntity(DRONE.get());
        return entity instanceof QuadcopterEntity drone ? drone : null;
    }

    private static @Nullable QuadcopterEntity serverDrone(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(DRONE.get()) instanceof QuadcopterEntity drone) {
                return drone;
            }
        }
        return null;
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }

    private static StepResult next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        return StepResult.pending();
    }

    private static StepResult finish(SelfTest.StepContext ctx, @Nullable String problem) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof DroneServiceScreen) {
            mc.gui.setScreen(null);
        }
        ctx.onServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.closeContainer();
            QuadcopterEntity drone = serverDrone(server);
            if (drone != null) {
                drone.discard();
            }
            DRONE.set(-1);
        });
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }
}
