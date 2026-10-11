package de.lardtom.propwash.ui.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.client.TuneAccess;
import de.lardtom.propwash.sim.FlightControllerConfig;
import de.lardtom.propwash.ui.TuneDefaults;
import de.lardtom.propwash.ui.TuneKeys;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.tab.SettingsTab;
import de.lardtom.propwash.ui.client.widget.ValueSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ArmAngleSelfTests {
    private static final String KEY = TuneKeys.ARM_MAX_ANGLE;

    private ArmAngleSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("ui: Scharf-Winkel 180° aus dem Tab Konfiguration kommt über das Tuning an die Drohne", 600, ArmAngleSelfTests::roundTrip);
    }

    public static Screenshots.Scene tabScene() {
        return new TabScene();
    }

    private static CompletableFuture<Void> giveInHand(SelfTest.StepContext ctx, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        UUID id = mc.player == null ? null : mc.player.getUUID();
        return ctx.onServer(server -> {
            ServerPlayer player = id == null ? null : server.getPlayerList().getPlayer(id);
            if (player == null) {
                throw new IllegalStateException("Spieler fehlt auf dem Server");
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        });
    }

    private static @Nullable ItemStack drone() {
        Optional<Item> item = ContentIds.item(ContentIds.DRONE);
        return item.map(ItemStack::new).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static StepResult roundTrip(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        TuneAccess access = TuneAccess.INSTANCE;
        Map<String, Object> state = ctx.state();
        String phase = (String) state.getOrDefault("phase", "give");
        if (mc.player == null) {
            return StepResult.fail("kein Spieler");
        }
        switch (phase) {
            case "give" -> {
                ItemStack drone = drone();
                if (drone == null) {
                    return StepResult.fail("Drohnen-Item fehlt");
                }
                state.put("future", giveInHand(ctx, drone));
                state.put("phase", "edit");
                state.put("since", ctx.tick());
            }
            case "edit" -> {
                if (!access.available()) {
                    return ctx.tick() - (int) state.get("since") > 120 ? StepResult.fail("TuneAccess nicht verfügbar") : StepResult.pending();
                }
                TuneModel model = TuneModel.open(DroneContext.resolve(mc));
                if (!model.live()) {
                    return StepResult.fail("Tuning nicht live");
                }
                if (Math.abs(model.value(KEY) - FlightControllerConfig.ARM_ANGLE_DEFAULT) > 1.0E-3) {
                    return StepResult.fail("Standard-Scharf-Winkel " + model.value(KEY) + " statt 25°");
                }
                model.set(KEY, 180.0);
                if (Math.abs(model.config().armMaxTiltDeg - 180.0) > 1.0E-3) {
                    return StepResult.fail("Regler erreicht den Flugcontroller nicht: " + model.config().armMaxTiltDeg);
                }
                if (!model.save()) {
                    return StepResult.fail("Speichern über TuneAccess fehlgeschlagen");
                }
                state.put("phase", "check");
                state.put("since", ctx.tick());
            }
            case "check" -> {
                FlightTune now = access.current();
                if (Math.abs(now.get(KEY, Float.NaN) - 180.0F) > 1.0E-3F) {
                    return ctx.tick() - (int) state.get("since") > 120 ? StepResult.fail("Server hat den Scharf-Winkel nicht übernommen: " + now.values())
                            : StepResult.pending();
                }
                DroneBuild build = DroneContext.resolve(mc).build();
                double applied = TuneDefaults.config(build, now.values()).armMaxTiltDeg;
                if (Math.abs(applied - 180.0) > 1.0E-3) {
                    return StepResult.fail("Flugcontroller der Drohne bekommt " + applied + "°");
                }
                TuneModel.applyNow(Map.of());
                state.put("phase", "reset");
                state.put("since", ctx.tick());
            }
            case "reset" -> {
                if (Math.abs(access.current().get(KEY, Float.NaN) - (float) FlightControllerConfig.ARM_ANGLE_DEFAULT) > 1.0E-3F) {
                    return ctx.tick() - (int) state.get("since") > 120 ? StepResult.fail("Zurücksetzen bleibt hängen") : StepResult.pending();
                }
                state.put("future", giveInHand(ctx, ItemStack.EMPTY));
                state.put("phase", "done");
            }
            default -> {
                CompletableFuture<Void> future = (CompletableFuture<Void>) state.get("future");
                return future.isDone() ? StepResult.pass() : StepResult.pending();
            }
        }
        return StepResult.pending();
    }

    private static final class TabScene implements Screenshots.Scene {
        private @Nullable CompletableFuture<Void> given;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.given == null) {
                ItemStack drone = drone();
                if (drone == null) {
                    return StepResult.fail("Drohnen-Item fehlt");
                }
                this.given = giveInHand(ctx, drone);
                return StepResult.pending();
            }
            if (!this.given.isDone() || !TuneAccess.INSTANCE.available()) {
                return ctx.tick() > 200 ? StepResult.fail("Drohne in der Hand fehlt") : StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                ConfigTabs.open(null, SettingsTab.ID);
                return StepResult.pending();
            }
            ConfigTab active = screen.activeTab();
            if (active == null || !SettingsTab.ID.equals(active.id())) {
                screen.selectTab(SettingsTab.ID);
                return StepResult.pending();
            }
            if (Math.abs(screen.tune().value(KEY) - 180.0) > 1.0E-3) {
                screen.tune().set(KEY, 180.0);
                screen.selectTab(SettingsTab.ID);
                return StepResult.pending();
            }
            String label = Component.translatable("propwash.ui.tune." + KEY).getString();
            for (GuiEventListener child : screen.children()) {
                if (child instanceof ValueSlider slider && slider.getMessage().getString().contains(label)) {
                    return StepResult.pass();
                }
            }
            return ctx.tick() > 200 ? StepResult.fail("Regler „" + label + "“ fehlt im Tab Konfiguration") : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            giveInHand(ctx, ItemStack.EMPTY);
            this.given = null;
        }
    }
}
