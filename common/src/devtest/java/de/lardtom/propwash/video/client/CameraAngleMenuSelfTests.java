package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.UiScaleSelfTests;
import de.lardtom.propwash.drone.CameraMount;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.TuneAccess;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.LayoutAudit.Finding;
import de.lardtom.propwash.ui.SettingsSearch;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.SliderTypingSelfTests;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.config.StepSlider;
import de.lardtom.propwash.ui.client.search.SearchBar;
import de.lardtom.propwash.ui.client.tab.OverviewTab;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.InfoField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class CameraAngleMenuSelfTests {
    public static final String GROUP = "video: Kamerawinkel im K-Menü";
    public static final String SHOTS = "bildschirmfotos: Kamerawinkel im K-Menü";
    private static final String PHASE = "camangle_phase";
    private static final String SINCE = "camangle_since";
    private static final String FUTURE = "camangle_future";
    private static final String OPENED = "camangle_opened";
    private static final String RESULT = "camangle_result";
    private static final String SERVER = "camangle_server";
    private static final DroneBuild BUILD = DroneBuild.FREESTYLE;
    private static final float HELD_ANGLE = 35.0F;
    private static final float FLIGHT_ANGLE = 40.0F;
    private static final float DISCARDED_ANGLE = 42.0F;
    private static final float SHOT_ANGLE = 32.0F;
    private static final int TOP_HITS = 6;
    private static final int WAIT_TICKS = 120;
    private static final List<String> QUERIES = List.of("uptilt", "Kamerawinkel", "camera angle", "Neigung");
    private static final AtomicReference<List<ItemStack>> SAVED = new AtomicReference<>(List.of());

    private CameraAngleMenuSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + " – ohne Drohne ausgegraut mit Hinweis", 400, CameraAngleMenuSelfTests::noneStep);
        SelfTest.clientStep(GROUP + " – gehaltene Drohne: Regler, −/+, ↺, Tippen über dem Halter, Server begrenzt, Item-Tuning, Verwerfen", 1600,
                CameraAngleMenuSelfTests::heldStep);
        SelfTest.clientStep(GROUP + " – Suche nach uptilt, Kamerawinkel, camera angle und Neigung führt zum Regler", 600,
                CameraAngleMenuSelfTests::searchStep);
        SelfTest.clientStep(GROUP + " – geflogene Drohne: FPV-Kamera und Server-Drohne übernehmen den Winkel, FPV läuft weiter", 1200, CameraAngleMenuSelfTests::flightStep);
    }

    public static void registerScreenshots(boolean german) {
        String suffix = german ? " deutsch" : "";
        String file = german ? "_de" : "";
        Screenshots.step(SHOTS + " mit gehaltener Drohne" + suffix, "configurator_video_camera_angle" + file, 30,
                new MenuScene(true, ContentIds.GOGGLES_DIGITAL, SHOT_ANGLE));
        Screenshots.step(SHOTS + " ohne Drohne" + suffix, "configurator_video_camera_angle_none" + file, 30,
                new MenuScene(false, ContentIds.GOGGLES_DIGITAL, Float.NaN));
        Screenshots.step(SHOTS + " mit gehaltener Drohne ohne Brille" + suffix, "configurator_video_camera_angle_no_goggles" + file, 30,
                new MenuScene(true, null, Float.NaN));
    }

    public static List<UiScaleSelfTests.Case> uiScaleCases() {
        return List.of(new UiCase("Konfigurator Kamerawinkel mit Drohne und Brille", true, ContentIds.GOGGLES_ANALOG),
                new UiCase("Konfigurator Kamerawinkel mit Drohne ohne Brille", true, null),
                new UiCase("Konfigurator Kamerawinkel ohne Drohne", false, ContentIds.GOGGLES_ANALOG));
    }

    private static StepResult noneStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (phase(ctx) == 0) {
            ctx.state().put(FUTURE, equip(ctx, ItemStack.EMPTY, ContentIds.GOGGLES_ANALOG));
            return next(ctx, 1);
        }
        StepResult ready = ready(ctx, mc, false, VideoSystem.ANALOG);
        if (ready != null) {
            return ready;
        }
        VideoConfigTab tab = openVideo(ctx, mc);
        if (tab == null) {
            return since(ctx) > WAIT_TICKS * 2 ? end(ctx, "Tab Video öffnet nicht: " + mc.gui.screen()) : StepResult.pending();
        }
        CameraAngleSection section = tab.cameraSection();
        if (section == null) {
            return end(ctx, "Abschnitt „Kamera dieser Drohne“ fehlt ohne Drohne");
        }
        if (section.editable() || section.slider() != null || !(section.row() instanceof InfoField field) || field.active) {
            return end(ctx, "ohne Drohne nicht ausgegraut: bearbeitbar " + section.editable() + ", Zeile " + section.row());
        }
        if (!field.value().getString().equals("—")) {
            return end(ctx, "ausgegraute Zeile zeigt einen Wert: " + field.value().getString());
        }
        for (FlatButton button : buttons(section)) {
            if (button == null || button.active) {
                return end(ctx, "Knopf neben dem Kamerawinkel ohne Drohne aktiv: " + (button == null ? "fehlt" : button.getMessage().getString()));
            }
            button.press();
        }
        if (mc.gui.screen() instanceof ConfiguratorScreen screen && screen.tune().dirty()) {
            return end(ctx, "Knöpfe ohne Drohne ändern das Tuning");
        }
        if (tab.storage() != ConfigTab.Storage.GAME) {
            return end(ctx, "Fußzeile ohne Drohne nicht „Spieleinstellung“: " + tab.storage());
        }
        ctx.log("ohne Drohne: Zeile ausgegraut mit „" + Component.translatable(CameraAngleSection.KEY + ".none").getString() + "“");
        return end(ctx, null);
    }

    private static StepResult heldStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        int phase = phase(ctx);
        if (phase == 0) {
            ctx.state().put(FUTURE, equip(ctx, drone(), null));
            return next(ctx, 1);
        }
        StepResult ready = ready(ctx, mc, true, null);
        if (ready != null) {
            return ready;
        }
        CameraMount.Range range = CameraMount.range(BUILD);
        float up = HELD_ANGLE + 1.0F;
        float below = range.maxDeg() - 1.0F;
        switch (phase) {
            case 1 -> {
                return inMenu(ctx, mc, (screen, tab, section, slider) -> {
                    if (count(screen, "propwash.video.config.format") != 0) {
                        return "ohne Brille zeigt der Tab Brillen-Einstellungen";
                    }
                    if (tab.storage() != ConfigTab.Storage.MIXED || !CameraAngleSection.FOOTER_KEY.equals(tab.mixedStorageKey())) {
                        return "Fußzeile nennt nicht, dass der Kamerawinkel in der Drohne liegt: " + tab.storage();
                    }
                    if (slider.current() != range.defaultDeg() || itemAngle(mc) != range.defaultDeg() || stored(mc) != null) {
                        return "neue Drohne nicht mit dem Rahmen-Standard " + range.defaultDeg() + "°: Regler " + slider.current() + ", Item " + itemAngle(mc);
                    }
                    String typing = SliderTypingSelfTests.check(screen, tab, slider);
                    if (typing != null) {
                        return "Kamerawinkel im K-Menü tippen: " + typing;
                    }
                    slider.select(HELD_ANGLE);
                    return null;
                }, true, 2);
            }
            case 2 -> {
                if (itemAngle(mc) != HELD_ANGLE) {
                    return wait(ctx, "Regler auf " + HELD_ANGLE + "° kommt nicht im Item-Tuning an: " + itemAngle(mc));
                }
                return inMenu(ctx, mc, (screen, tab, section, slider) -> {
                    press(section.plusButton());
                    return slider.current() == up ? null : "„+“ zeigt " + slider.current() + "° statt " + up + "°";
                }, true, 3);
            }
            case 3 -> {
                if (itemAngle(mc) != up) {
                    return wait(ctx, "„+“ kommt nicht mit " + up + "° im Item an: " + itemAngle(mc));
                }
                return inMenu(ctx, mc, (screen, tab, section, slider) -> {
                    screen.setFocused(slider);
                    SliderTypingSelfTests.typeText(Integer.toString(Math.round(range.maxDeg() + 15.0F)));
                    SliderTypingSelfTests.pressEnter();
                    boolean notice = slider.editor().noticeActive();
                    screen.setFocused(null);
                    return notice && slider.current() == range.maxDeg() ? null
                            : "getippter Winkel über dem Halter nicht mit Hinweis auf " + range.maxDeg() + "° begrenzt: " + slider.current();
                }, true, 4);
            }
            case 4 -> {
                if (itemAngle(mc) != range.maxDeg()) {
                    return wait(ctx, "Begrenzung auf " + range.maxDeg() + "° kommt nicht im Item an: " + itemAngle(mc));
                }
                return inMenu(ctx, mc, (screen, tab, section, slider) -> {
                    press(section.minusButton());
                    return slider.current() == below ? null : "„−“ zeigt " + slider.current() + "° statt " + below + "°";
                }, true, 5);
            }
            case 5 -> {
                if (itemAngle(mc) != below) {
                    return wait(ctx, "„−“ kommt nicht mit " + below + "° im Item an: " + itemAngle(mc));
                }
                TuneAccess.INSTANCE.apply(new FlightTune(Map.of(TuneMapping.CAMERA_UPTILT, TuneMapping.CAMERA_UPTILT_MAX)));
                return next(ctx, 6);
            }
            case 6 -> {
                Float raw = stored(mc);
                if (raw == null || raw != range.maxDeg()) {
                    return wait(ctx, "Server begrenzt " + TuneMapping.CAMERA_UPTILT_MAX + "° nicht auf den Halter (" + range.maxDeg() + "°): " + raw);
                }
                return inMenu(ctx, mc, (screen, tab, section, slider) -> {
                    if (slider.current() != range.maxDeg()) {
                        return "K-Menü zeigt nicht den Winkel der Drohne (" + range.maxDeg() + "°): " + slider.current();
                    }
                    press(section.resetButton());
                    return slider.current() == range.defaultDeg() ? null : "↺ zeigt " + slider.current() + "° statt " + range.defaultDeg() + "°";
                }, true, 7);
            }
            case 7 -> {
                if (stored(mc) != null || itemAngle(mc) != range.defaultDeg()) {
                    return wait(ctx, "↺ setzt nicht auf den Standard " + range.defaultDeg() + "° zurück: " + stored(mc));
                }
                return inMenu(ctx, mc, (screen, tab, section, slider) -> {
                    section.change(DISCARDED_ANGLE);
                    return slider.current() == DISCARDED_ANGLE ? null : "Winkel " + DISCARDED_ANGLE + "° nicht eingestellt: " + slider.current();
                }, null, 8);
            }
            case 8 -> {
                if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                    return end(ctx, "K-Menü schließt sich vor dem Verwerfen");
                }
                if (since(ctx) < 10) {
                    return StepResult.pending();
                }
                if (!screen.tune().dirty()) {
                    return end(ctx, "Winkel " + DISCARDED_ANGLE + "° nicht als ungespeicherte Änderung markiert");
                }
                screen.discardAndClose();
                return next(ctx, 9);
            }
            default -> {
                if (mc.gui.screen() != null || since(ctx) < 40) {
                    return since(ctx) > WAIT_TICKS ? end(ctx, "K-Menü schließt beim Verwerfen nicht") : StepResult.pending();
                }
                if (itemAngle(mc) != range.defaultDeg()) {
                    return end(ctx, "Verwerfen stellt den Winkel beim Öffnen (" + range.defaultDeg() + "°) nicht wieder her: " + itemAngle(mc));
                }
                ctx.log("gehaltene Drohne " + BUILD.frame().key() + ": Halter " + range.minDeg() + "–" + range.maxDeg() + "°, Standard "
                        + range.defaultDeg() + "°; Regler " + HELD_ANGLE + "°, „+“, Tippen und Server auf " + range.maxDeg()
                        + "° begrenzt, „−“, ↺, Verwerfen; jeder Wert nach dem Schließen im Item-Tuning");
                return end(ctx, null);
            }
        }
    }

    @FunctionalInterface
    private interface MenuAction {
        @Nullable String run(ConfiguratorScreen screen, VideoConfigTab tab, CameraAngleSection section, StepSlider slider);
    }

    private static StepResult inMenu(SelfTest.StepContext ctx, Minecraft mc, MenuAction action, @Nullable Boolean save, int nextPhase) {
        VideoConfigTab tab = openVideo(ctx, mc);
        if (tab == null) {
            return since(ctx) > WAIT_TICKS * 2 ? end(ctx, "Tab Video öffnet nicht (Phase " + phase(ctx) + "): " + mc.gui.screen()) : StepResult.pending();
        }
        CameraAngleSection section = tab.cameraSection();
        StepSlider slider = section == null ? null : section.slider();
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || section == null || slider == null || !section.editable() || !slider.active
                || buttons(section).stream().anyMatch(button -> button == null || !button.active)) {
            return end(ctx, "Kamerawinkel mit gehaltener Drohne nicht bedienbar (Phase " + phase(ctx) + ")");
        }
        String problem = action.run(screen, tab, section, slider);
        if (problem != null) {
            return end(ctx, problem);
        }
        if (Boolean.TRUE.equals(save)) {
            screen.saveAndClose();
        } else if (Boolean.FALSE.equals(save)) {
            screen.discardAndClose();
        }
        return next(ctx, nextPhase);
    }

    private static StepResult searchStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        int phase = phase(ctx);
        if (phase == 0) {
            ctx.state().put(FUTURE, equip(ctx, drone(), ContentIds.GOGGLES_ANALOG));
            return next(ctx, 1);
        }
        StepResult ready = ready(ctx, mc, true, VideoSystem.ANALOG);
        if (ready != null) {
            return ready;
        }
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            if (phase == 1 && !ctx.state().containsKey(OPENED)) {
                ctx.state().put(OPENED, ctx.tick());
                ConfigTabs.open(null, OverviewTab.ID);
                return StepResult.pending();
            }
            return since(ctx) > WAIT_TICKS * 2 ? end(ctx, "Konfigurator geschlossen") : StepResult.pending();
        }
        if (since(ctx) < 3) {
            return StepResult.pending();
        }
        String label = Component.translatable(CameraAngleSection.KEY).getString();
        if (phase == 1) {
            SearchBar search = screen.search();
            if (search == null) {
                return end(ctx, "Konfigurator ohne Suchfeld");
            }
            search.focus();
            if (search.index() == null) {
                return since(ctx) > WAIT_TICKS * 2 ? end(ctx, "Suchindex fehlt") : StepResult.pending();
            }
            for (String query : QUERIES) {
                search.type(query);
                if (hitIndex(search.hits(), label, TOP_HITS) < 0) {
                    return end(ctx, "Suche „" + query + "“ findet „" + label + "“ im Tab Video nicht unter den ersten " + TOP_HITS + ": " + describe(search.hits()));
                }
            }
            search.type("");
            screen.selectTab(OverviewTab.ID);
            search.focus();
            search.type(QUERIES.getFirst());
            int pick = hitIndex(search.hits(), label, Integer.MAX_VALUE);
            if (pick < 0) {
                return end(ctx, "Treffer „" + label + "“ fehlt nach dem Tab-Wechsel");
            }
            search.pick(pick);
            return next(ctx, 2);
        }
        if (!(screen.activeTab() instanceof VideoConfigTab tab) || tab.cameraSection() == null || tab.cameraSection().slider() == null) {
            return end(ctx, "Suchtreffer öffnet nicht den Tab Video mit dem Kamerawinkel: " + screen.activeTab());
        }
        StepSlider slider = tab.cameraSection().slider();
        if (screen.getFocused() != slider || screen.highlighted() != slider) {
            return end(ctx, "Suchtreffer fokussiert und markiert nicht den Regler: " + screen.getFocused());
        }
        ScreenRectangle content = screen.contentArea();
        if (!slider.visible || slider.getY() < content.top() || slider.getY() + slider.getHeight() > content.bottom()) {
            return end(ctx, "Regler nach dem Suchtreffer nicht sichtbar: y " + slider.getY() + ", Inhalt " + content.top() + "–" + content.bottom());
        }
        ctx.log("Suche " + QUERIES + " findet „" + label + "“, Sprung fokussiert den Regler bei y " + slider.getY());
        return end(ctx, null);
    }

    private static StepResult flightStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        int phase = phase(ctx);
        CameraMount.Range range = CameraMount.range(BUILD);
        if (phase == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> VtxFixture.spawn(server, BUILD, ContentIds.GOGGLES_DIGITAL, Map.of(), 4.0, true)));
            return next(ctx, 1);
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future != null && future.isCompletedExceptionally()) {
            return endFlight(ctx, "Server: " + future.exceptionNow());
        }
        if (future != null && !future.isDone()) {
            return since(ctx) > WAIT_TICKS * 2 ? endFlight(ctx, "Server antwortet nicht") : StepResult.pending();
        }
        PilotSession session = PilotSession.INSTANCE;
        switch (phase) {
            case 1 -> {
                if (VtxFixture.piloted() == null && !(mc.gui.screen() instanceof ConfiguratorScreen)) {
                    return since(ctx) > WAIT_TICKS * 3 ? endFlight(ctx, "keine FPV-Sitzung: " + pilotState(mc)) : StepResult.pending();
                }
                VideoConfigTab tab = openVideo(ctx, mc);
                if (tab == null) {
                    return since(ctx) > WAIT_TICKS * 4 ? endFlight(ctx, "Tab Video öffnet im Flug nicht: " + pilotState(mc)) : StepResult.pending();
                }
                CameraAngleSection section = tab.cameraSection();
                if (section == null || !section.editable() || section.slider() == null || !(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                    return endFlight(ctx, "Kamerawinkel im Flug nicht bedienbar: " + pilotState(mc));
                }
                if (!screen.context().piloting() || session.cameraUptiltDeg() != range.defaultDeg() || section.slider().current() != range.defaultDeg()) {
                    return endFlight(ctx, "Start nicht beim Rahmen-Standard " + range.defaultDeg() + "° der geflogenen Drohne: " + pilotState(mc));
                }
                section.slider().select(FLIGHT_ANGLE);
                ctx.state().put(RESULT, mc.isPaused());
                screen.saveAndClose();
                return next(ctx, 2);
            }
            case 2 -> {
                if (mc.gui.screen() != null || session.cameraUptiltDeg() != FLIGHT_ANGLE) {
                    return since(ctx) > WAIT_TICKS ? endFlight(ctx, "FPV-Kamera kippt nicht auf " + FLIGHT_ANGLE + "°: " + pilotState(mc))
                            : StepResult.pending();
                }
                AtomicReference<Float> result = new AtomicReference<>(Float.NaN);
                ctx.state().put(FUTURE, ctx.onServer(server -> result.set(CameraMount.uptilt(BUILD, VtxFixture.serverTune(server)))));
                ctx.state().put(SERVER, result);
                return next(ctx, 3);
            }
            default -> {
                @SuppressWarnings("unchecked")
                float server = ((AtomicReference<Float>) ctx.state().get(SERVER)).get();
                if (server != FLIGHT_ANGLE) {
                    return endFlight(ctx, "Server-Drohne hat " + server + "° statt " + FLIGHT_ANGLE + "°");
                }
                if (VtxFixture.piloted() == null) {
                    return endFlight(ctx, "FPV-Sitzung endet mit dem K-Menü: " + pilotState(mc));
                }
                ctx.log("im Flug: K-Menü " + FLIGHT_ANGLE + "° (Spiel beim Einstellen pausiert: " + ctx.state().get(RESULT)
                        + "), FPV-Kamera und Server-Drohne " + FLIGHT_ANGLE + "°, FPV läuft weiter");
                return endFlight(ctx, null);
            }
        }
    }

    private static String pilotState(Minecraft mc) {
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        return "Sitzung " + session.isActive() + ", Modus " + session.mode() + ", Drohne " + (drone == null ? "keine" : drone.getId()) + " (Test "
                + VtxFixture.droneId() + ", Client " + (VtxFixture.clientDrone() != null) + "), Kamera " + session.cameraUptiltDeg() + "°, Bildschirm "
                + mc.gui.screen() + ", pausiert " + mc.isPaused();
    }

    private static int hitIndex(List<SettingsSearch.Hit> hits, String label, int limit) {
        for (int i = 0; i < Math.min(limit, hits.size()); i++) {
            SettingsSearch.Entry entry = hits.get(i).entry();
            if ("video".equals(entry.tabId()) && entry.label().equals(label)) {
                return i;
            }
        }
        return -1;
    }

    private static String describe(List<SettingsSearch.Hit> hits) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(TOP_HITS, hits.size()); i++) {
            out.add(hits.get(i).entry().tabId() + ": " + hits.get(i).entry().label());
        }
        return out.toString();
    }

    private static List<@Nullable FlatButton> buttons(CameraAngleSection section) {
        List<@Nullable FlatButton> out = new ArrayList<>();
        out.add(section.minusButton());
        out.add(section.plusButton());
        out.add(section.resetButton());
        return out;
    }

    private static void press(@Nullable FlatButton button) {
        if (button != null) {
            button.press();
        }
    }

    private static int count(Screen screen, String key) {
        String label = Component.translatable(key).getString();
        int found = 0;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible && widget.getMessage().getString().startsWith(label)) {
                found++;
            }
        }
        return found;
    }

    private static float itemAngle(Minecraft mc) {
        if (mc.player == null) {
            return Float.NaN;
        }
        ItemStack stack = mc.player.getMainHandItem();
        return CameraMount.uptilt(stack.getOrDefault(CoreComponents.DRONE_BUILD.get(), BUILD),
                stack.getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY).values());
    }

    private static @Nullable Float stored(Minecraft mc) {
        return mc.player == null ? null
                : mc.player.getMainHandItem().getOrDefault(CoreComponents.FLIGHT_TUNE.get(), FlightTune.EMPTY).values().get(TuneMapping.CAMERA_UPTILT);
    }

    private static ItemStack drone() {
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), BUILD);
        return stack;
    }

    static CompletableFuture<Void> equip(SelfTest.StepContext ctx, ItemStack hand, @Nullable Identifier goggles) {
        ItemStack held = hand.copy();
        return ctx.onServer(server -> {
            ServerPlayer player = player(server);
            if (SAVED.get().isEmpty()) {
                SAVED.set(List.of(player.getMainHandItem().copy(), player.getOffhandItem().copy(), player.getItemBySlot(EquipmentSlot.HEAD).copy()));
            }
            player.setItemSlot(EquipmentSlot.MAINHAND, held);
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.HEAD, goggles == null ? ItemStack.EMPTY : ContentIds.item(goggles).map(ItemStack::new).orElse(ItemStack.EMPTY));
        });
    }

    static void restore(SelfTest.StepContext ctx) {
        ctx.onServer(server -> {
            List<ItemStack> saved = SAVED.getAndSet(List.of());
            if (saved.size() == 3) {
                ServerPlayer player = player(server);
                player.setItemSlot(EquipmentSlot.MAINHAND, saved.get(0));
                player.setItemSlot(EquipmentSlot.OFFHAND, saved.get(1));
                player.setItemSlot(EquipmentSlot.HEAD, saved.get(2));
            }
        });
    }

    private static boolean equipped(Minecraft mc, boolean drone, @Nullable VideoSystem goggles) {
        return mc.player != null && ContentIds.is(mc.player.getMainHandItem(), ContentIds.DRONE) == drone
                && !ContentIds.is(mc.player.getOffhandItem(), ContentIds.DRONE) && ContentIds.wornGogglesSystem(mc.player).orElse(null) == goggles;
    }

    private static @Nullable StepResult ready(SelfTest.StepContext ctx, Minecraft mc, boolean drone, @Nullable VideoSystem goggles) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future != null && future.isCompletedExceptionally()) {
            return end(ctx, "Server: " + future.exceptionNow());
        }
        if (future != null && !future.isDone() || phase(ctx) == 1 && !equipped(mc, drone, goggles)) {
            return since(ctx) > WAIT_TICKS * 2 ? end(ctx, "Drohne oder Brille kommt beim Client nicht an") : StepResult.pending();
        }
        return null;
    }

    static @Nullable VideoConfigTab openVideo(SelfTest.StepContext ctx, Minecraft mc) {
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            if (!ctx.state().containsKey(OPENED)) {
                ctx.state().put(OPENED, ctx.tick());
                ConfigTabs.open(null, "video");
            }
            return null;
        }
        if (!(screen.activeTab() instanceof VideoConfigTab tab)) {
            screen.selectTab("video");
            ctx.state().put(OPENED, ctx.tick());
            return null;
        }
        return ctx.tick() - (int) ctx.state().getOrDefault(OPENED, 0) >= 3 ? tab : null;
    }

    private static void close() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
    }

    private static StepResult end(SelfTest.StepContext ctx, @Nullable String problem) {
        close();
        restore(ctx);
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    private static StepResult endFlight(SelfTest.StepContext ctx, @Nullable String problem) {
        close();
        ctx.onServer(VtxFixture::release);
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    private static StepResult wait(SelfTest.StepContext ctx, String problem) {
        return since(ctx) > WAIT_TICKS ? end(ctx, problem) : StepResult.pending();
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

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static @Nullable VideoSystem system(@Nullable Identifier goggles) {
        return goggles == null ? null : ContentIds.GOGGLES_ANALOG.equals(goggles) ? VideoSystem.ANALOG
                : ContentIds.GOGGLES_DIGITAL.equals(goggles) ? VideoSystem.DIGITAL : VideoSystem.CREATIVE;
    }

    private static final class UiCase implements UiScaleSelfTests.Case {
        private final String name;
        private final boolean drone;
        private final @Nullable Identifier goggles;

        private UiCase(String name, boolean drone, @Nullable Identifier goggles) {
            this.name = name;
            this.drone = drone;
            this.goggles = goggles;
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.tick() == 0) {
                ctx.state().put(FUTURE, equip(ctx, this.drone ? drone() : ItemStack.EMPTY, this.goggles));
                return StepResult.pending();
            }
            CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
            if (future != null && future.isCompletedExceptionally()) {
                return StepResult.fail("Server: " + future.exceptionNow());
            }
            if (future != null && !future.isDone() || !equipped(mc, this.drone, system(this.goggles))) {
                return StepResult.pending();
            }
            return openVideo(ctx, mc) == null ? StepResult.pending() : StepResult.pass();
        }

        @Override
        public List<Finding> audit(Minecraft mc, Screen screen) {
            List<Finding> findings = new ArrayList<>(LayoutProbe.audit(mc, screen));
            String subject = "Tab Video, Abschnitt Kamera dieser Drohne";
            if (!(screen instanceof ConfiguratorScreen configurator) || !(configurator.activeTab() instanceof VideoConfigTab tab)) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, subject, "Tab Video nicht offen"));
                return findings;
            }
            CameraAngleSection section = tab.cameraSection();
            if (section == null || section.row() == null) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, subject, "Abschnitt fehlt"));
                return findings;
            }
            if (section.editable() != this.drone) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, subject, "bearbeitbar " + section.editable() + " bei Drohne " + this.drone));
            }
            ScreenRectangle content = configurator.contentArea();
            List<@Nullable AbstractWidget> row = new ArrayList<>(buttons(section));
            row.add(section.row());
            for (AbstractWidget widget : row) {
                if (widget == null) {
                    findings.add(new Finding(LayoutAudit.Kind.HIDDEN, subject, "Knopf fehlt"));
                } else if (widget.getX() < content.left() || widget.getX() + widget.getWidth() > content.right()) {
                    findings.add(new Finding(LayoutAudit.Kind.OFFSCREEN, subject, "„" + widget.getMessage().getString() + "“ bei x " + widget.getX()
                            + "–" + (widget.getX() + widget.getWidth()) + " außerhalb " + content.left() + "–" + content.right()));
                }
            }
            String title = Component.translatable(CameraAngleSection.SECTION_KEY).getString();
            if (LayoutProbe.capture(mc, screen).texts().stream().noneMatch(text -> text.value().equals(title))) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, subject, "Überschrift „" + title + "“ fehlt"));
            }
            return findings;
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            CameraAngleMenuSelfTests.close();
            restore(ctx);
        }
    }

    private static final class MenuScene implements Screenshots.Scene {
        private final boolean drone;
        private final @Nullable Identifier goggles;
        private final float angle;

        private MenuScene(boolean drone, @Nullable Identifier goggles, float angle) {
            this.drone = drone;
            this.goggles = goggles;
            this.angle = angle;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE)) {
                ctx.state().put(FUTURE, equip(ctx, this.drone ? drone() : ItemStack.EMPTY, this.goggles));
                return StepResult.pending();
            }
            CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
            if (future.isCompletedExceptionally()) {
                return StepResult.fail("Server: " + future.exceptionNow());
            }
            if (!future.isDone() || !equipped(mc, this.drone, system(this.goggles))) {
                return ctx.tick() > 300 ? StepResult.fail("Drohne oder Brille kommt beim Client nicht an") : StepResult.pending();
            }
            Screenshots.parkMouse(mc);
            VideoConfigTab tab = openVideo(ctx, mc);
            if (tab == null) {
                return ctx.tick() > 400 ? StepResult.fail("Tab Video öffnet nicht") : StepResult.pending();
            }
            CameraAngleSection section = tab.cameraSection();
            if (section == null) {
                return StepResult.fail("Abschnitt Kamera dieser Drohne fehlt");
            }
            if (Float.isFinite(this.angle) && section.uptilt() != this.angle) {
                section.change(this.angle);
                return StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            CameraAngleMenuSelfTests.close();
            restore(ctx);
        }
    }
}
