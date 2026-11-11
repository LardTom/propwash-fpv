package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.creative.client.CreativeEditorTab;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.NumberSpinner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class CovCameraAngleMenuSelfTests {
    public static final String DEFAULTS_STEP = CameraAngleMenuSelfTests.GROUP
            + " – gehaltene Drohne: „Standardwerte“ im Tab Video setzt den Kamerawinkel auf den Rahmenstandard";
    public static final String CREATIVE_STEP = CameraAngleMenuSelfTests.GROUP + " – Kreativ-Drohne: der Uptilt aus dem Drohnen-Editor gilt";
    private static final String PHASE = "cov_camangle_phase";
    private static final String SINCE = "cov_camangle_since";
    private static final String FUTURE = "cov_camangle_future";
    private static final String OPENED = "camangle_opened";
    private static final int WAIT_TICKS = 160;
    private static final float CHANGED = 35.0F;
    private static final float MENU_ANGLE = 30.0F;
    private static final float EDITOR_ANGLE = 60.0F;
    private static final DroneBuild FREESTYLE = DroneBuild.FREESTYLE;
    private static final DroneBuild CREATIVE = new DroneBuild(FREESTYLE.frame(), StackType.CREATIVE_FC, FREESTYLE.motor(), FREESTYLE.prop(),
            FREESTYLE.video(), FREESTYLE.battery());
    private static final AtomicReference<@Nullable Float> SERVER_ANGLE = new AtomicReference<>();

    private CovCameraAngleMenuSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(DEFAULTS_STEP, 1200, CovCameraAngleMenuSelfTests::defaultsStep);
        SelfTest.clientStep(CREATIVE_STEP, 1200, CovCameraAngleMenuSelfTests::creativeStep);
        if (CovCameraAngleLanCheck.hostEnabled()) {
            SelfTest.clientStep(CovCameraAngleLanCheck.HOST_STEP, CovCameraAngleLanCheck.HOST_TICKS, CovCameraAngleLanCheck::hostStep);
        }
    }

    private static ItemStack drone(DroneBuild build) {
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build);
        return stack;
    }

    private static float itemAngle(Minecraft mc, DroneBuild fallback) {
        if (mc.player == null) {
            return Float.NaN;
        }
        ItemStack stack = mc.player.getMainHandItem();
        return CameraMount.uptilt(stack.getOrDefault(CoreComponents.DRONE_BUILD.get(), fallback),
                stack.getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY).values());
    }

    private static @Nullable Float stored(Minecraft mc) {
        return mc.player == null ? null
                : mc.player.getMainHandItem().getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY).values().get(TuneMapping.CAMERA_UPTILT);
    }

    private static void readServer(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ItemStack stack = player.getMainHandItem();
        DroneBuild build = stack.getOrDefault(CoreComponents.DRONE_BUILD.get(), FREESTYLE);
        SERVER_ANGLE.set(CameraMount.uptilt(build, stack.getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY).values()));
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(SINCE, 0);
    }

    private static StepResult next(SelfTest.StepContext ctx, int phase) {
        ctx.state().remove(OPENED);
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        return StepResult.pending();
    }

    private static StepResult end(SelfTest.StepContext ctx, @Nullable String problem) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
        CameraAngleMenuSelfTests.restore(ctx);
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    private static @Nullable StepResult ready(SelfTest.StepContext ctx, Minecraft mc, DroneBuild build) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future != null && future.isCompletedExceptionally()) {
            return end(ctx, "Server: " + future.exceptionNow());
        }
        boolean held = mc.player != null && build.equals(mc.player.getMainHandItem().get(CoreComponents.DRONE_BUILD.get()));
        if (future != null && !future.isDone() || phase(ctx) == 1 && !held) {
            return since(ctx) > WAIT_TICKS ? end(ctx, "Drohne kommt beim Client nicht an") : StepResult.pending();
        }
        return null;
    }

    private static @Nullable FlatButton defaultsButton(ConfiguratorScreen screen) {
        String label = Component.translatable("propwash.ui.button.defaults").getString();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof FlatButton button && button.visible && button.getMessage().getString().equals(label)) {
                return button;
            }
        }
        return null;
    }

    private static StepResult defaultsStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        int phase = phase(ctx);
        if (phase == 0) {
            ctx.state().put(FUTURE, CameraAngleMenuSelfTests.equip(ctx, drone(FREESTYLE), null));
            return next(ctx, 1);
        }
        StepResult waiting = ready(ctx, mc, FREESTYLE);
        if (waiting != null) {
            return waiting;
        }
        CameraMount.Range range = CameraMount.range(FREESTYLE);
        switch (phase) {
            case 1 -> {
                VideoConfigTab tab = CameraAngleMenuSelfTests.openVideo(ctx, mc);
                if (tab == null) {
                    return since(ctx) > WAIT_TICKS ? end(ctx, "Tab Video öffnet nicht") : StepResult.pending();
                }
                CameraAngleSection section = tab.cameraSection();
                if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || section == null || !section.editable()) {
                    return end(ctx, "Kamerawinkel mit gehaltener Drohne nicht bedienbar");
                }
                section.change(CHANGED);
                screen.saveAndClose();
                return next(ctx, 2);
            }
            case 2 -> {
                if (mc.gui.screen() != null || itemAngle(mc, FREESTYLE) != CHANGED) {
                    return since(ctx) > WAIT_TICKS ? end(ctx, "Winkel " + CHANGED + "° kommt nicht im Item an: " + itemAngle(mc, FREESTYLE))
                            : StepResult.pending();
                }
                return next(ctx, 3);
            }
            case 3 -> {
                VideoConfigTab tab = CameraAngleMenuSelfTests.openVideo(ctx, mc);
                if (tab == null) {
                    return since(ctx) > WAIT_TICKS ? end(ctx, "Tab Video öffnet nicht (2)") : StepResult.pending();
                }
                CameraAngleSection section = tab.cameraSection();
                if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || section == null || section.slider() == null) {
                    return end(ctx, "Kamerawinkel beim zweiten Öffnen nicht bedienbar");
                }
                if (section.slider().current() != CHANGED) {
                    return end(ctx, "Regler zeigt " + section.slider().current() + "° statt " + CHANGED + "°");
                }
                FlatButton defaults = defaultsButton(screen);
                if (defaults == null || !defaults.active) {
                    return end(ctx, "Knopf „Standardwerte“ im Tab Video fehlt oder ist aus");
                }
                defaults.press();
                if (section.slider() == null || section.slider().current() != range.defaultDeg()) {
                    return end(ctx, "„Standardwerte“ zeigt " + (section.slider() == null ? "keinen Regler" : section.slider().current() + "°")
                            + " statt " + range.defaultDeg() + "°");
                }
                screen.saveAndClose();
                return next(ctx, 4);
            }
            case 4 -> {
                if (mc.gui.screen() != null || stored(mc) != null) {
                    return since(ctx) > WAIT_TICKS ? end(ctx, "nach „Standardwerte“ bleibt ein gespeicherter Winkel im Item: " + stored(mc))
                            : StepResult.pending();
                }
                if (itemAngle(mc, FREESTYLE) != range.defaultDeg()) {
                    return end(ctx, "Item zeigt " + itemAngle(mc, FREESTYLE) + "° statt Rahmenstandard " + range.defaultDeg() + "°");
                }
                ctx.log(String.format(Locale.ROOT, "gehaltene Freestyle-Drohne: %.0f° gespeichert, „Standardwerte“ im Tab Video → Item ohne gespeicherten "
                        + "Winkel, Rahmenstandard %.0f°", CHANGED, range.defaultDeg()));
                return end(ctx, null);
            }
            default -> {
                return end(ctx, "unbekannte Phase " + phase);
            }
        }
    }

    private static @Nullable NumberSpinner uptiltSpinner(List<? extends GuiEventListener> children, String label) {
        for (GuiEventListener child : children) {
            if (child instanceof NumberSpinner spinner && spinner.getMessage().getString().startsWith(label)) {
                return spinner;
            }
            if (child instanceof ContainerEventHandler container) {
                NumberSpinner found = uptiltSpinner(container.children(), label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static StepResult creativeStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        int phase = phase(ctx);
        if (phase == 0) {
            SERVER_ANGLE.set(null);
            ctx.state().put(FUTURE, CameraAngleMenuSelfTests.equip(ctx, drone(CREATIVE), null));
            return next(ctx, 1);
        }
        StepResult waiting = ready(ctx, mc, CREATIVE);
        if (waiting != null) {
            return waiting;
        }
        CameraMount.Range range = CameraMount.range(CREATIVE);
        switch (phase) {
            case 1 -> {
                if (range.minDeg() != 0.0F || range.maxDeg() != 80.0F) {
                    return end(ctx, "Kreativ-Drohne mit Bereich " + range.minDeg() + "–" + range.maxDeg() + "° statt 0–80°");
                }
                VideoConfigTab tab = CameraAngleMenuSelfTests.openVideo(ctx, mc);
                if (tab == null) {
                    return since(ctx) > WAIT_TICKS ? end(ctx, "Tab Video öffnet nicht") : StepResult.pending();
                }
                CameraAngleSection section = tab.cameraSection();
                if (!(mc.gui.screen() instanceof ConfiguratorScreen) || section == null || !section.editable()) {
                    return end(ctx, "Kamerawinkel der Kreativ-Drohne im Tab Video nicht bedienbar");
                }
                section.change(MENU_ANGLE);
                if (section.slider() == null || section.slider().current() != MENU_ANGLE) {
                    return end(ctx, "Tab Video nimmt " + MENU_ANGLE + "° nicht an");
                }
                return next(ctx, 2);
            }
            case 2 -> {
                if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                    return end(ctx, "K-Menü schließt sich vor dem Drohnen-Editor");
                }
                if (!(screen.activeTab() instanceof CreativeEditorTab)) {
                    screen.selectTab(CreativeEditorTab.ID);
                    return since(ctx) > WAIT_TICKS ? end(ctx, "Tab Drohnen-Editor öffnet nicht") : StepResult.pending();
                }
                if (since(ctx) < 3) {
                    return StepResult.pending();
                }
                NumberSpinner spinner = uptiltSpinner(screen.children(), Component.translatable(CreativeEditorTab.KEY + "uptilt").getString());
                if (spinner == null) {
                    return end(ctx, "Uptilt im Drohnen-Editor nicht gefunden");
                }
                spinner.startEdit(true);
                for (char c : Integer.toString(Math.round(EDITOR_ANGLE)).toCharArray()) {
                    spinner.editor().charTyped(c);
                }
                spinner.commitEdit();
                CreativeEditorTab editor = (CreativeEditorTab) screen.activeTab();
                if (editor.uptilt() != EDITOR_ANGLE) {
                    return end(ctx, "Drohnen-Editor zeigt Uptilt " + editor.uptilt() + "° statt " + EDITOR_ANGLE + "°");
                }
                screen.saveAndClose();
                return next(ctx, 3);
            }
            case 3 -> {
                if (mc.gui.screen() != null || itemAngle(mc, CREATIVE) != EDITOR_ANGLE) {
                    return since(ctx) > WAIT_TICKS ? end(ctx, String.format(Locale.ROOT, "nach dem Speichern zeigt das Item %.0f° statt %.0f° (Tab Video %.0f°)",
                            itemAngle(mc, CREATIVE), EDITOR_ANGLE, MENU_ANGLE)) : StepResult.pending();
                }
                ctx.state().put(FUTURE, ctx.onServer(CovCameraAngleMenuSelfTests::readServer));
                return next(ctx, 4);
            }
            case 4 -> {
                Float server = SERVER_ANGLE.get();
                if (server == null) {
                    return since(ctx) > WAIT_TICKS ? end(ctx, "Server-Winkel nicht gelesen") : StepResult.pending();
                }
                if (server != EDITOR_ANGLE) {
                    return end(ctx, "Server speichert " + server + "° statt " + EDITOR_ANGLE + "°");
                }
                ctx.log(String.format(Locale.ROOT, "Kreativ-Drohne (Bereich %.0f–%.0f°): Tab Video %.0f°, Drohnen-Editor %.0f° im selben Besuch, gespeichert → "
                        + "Item und Server %.0f°", range.minDeg(), range.maxDeg(), MENU_ANGLE, EDITOR_ANGLE, server));
                return end(ctx, null);
            }
            default -> {
                return end(ctx, "unbekannte Phase " + phase);
            }
        }
    }
}
