package de.lardtom.propwash.input.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.CommandTrigger;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchCommand;
import de.lardtom.propwash.input.SwitchCommands;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.SwitchSource;
import de.lardtom.propwash.input.SwitchSources;
import de.lardtom.propwash.input.client.screen.CommandEditScreen;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class SwitchCommandSelfTests {
    static final String GUID = "propwash-selftest-command-radio";
    private static final String SHOT_GUID = "propwash-selftest-command-shot";
    private static final int AXES = 10;
    private static final int BUTTONS = 8;
    private static final int SWITCH_AXIS = 8;
    private static final int HOLD_BUTTON = 5;
    private static final int UNKNOWN_BUTTON = 6;
    private static final int KEY_SLOT = 3;
    private static final String TAG_SWITCH = "propwash_cmd_switch";
    private static final String TAG_BUTTON = "propwash_cmd_button";
    private static final String TAG_KEY = "propwash_cmd_key";
    private static final String UNKNOWN = "/propwash_selftest_no_such_command";
    private static final int QUIET_TICKS = 30;
    private static final int WAIT_TICKS = 120;
    private static final int MAX_TICKS = 2400;

    private SwitchCommandSelfTests() {
    }

    static void register() {
        SelfTest.clientStep("input: Modi-Tab mit Seite „Befehle“ und Fenster „Befehl einrichten“", 600, SwitchCommandSelfTests::tabStep);
        SelfTest.clientStep("input: eigene Befehle per Funkenschalter, Taster und Taste im Flug", MAX_TICKS + 100, SwitchCommandSelfTests::flightStep);
    }

    public static void registerScreenshots(boolean german) {
        String suffix = german ? " deutsch" : "";
        String file = german ? "_de" : "";
        Screenshots.step("bildschirmfotos: Modi-Tab, Seite Befehle" + suffix, "configurator_modes_commands" + file, 20, new TabScene(false, false));
        Screenshots.step("bildschirmfotos: Fenster Befehl einrichten" + suffix, "command_editor" + file, 10, new TabScene(true, false));
        if (!german) {
            Screenshots.step("bildschirmfotos: Fenster Befehl einrichten, Befehl zu lang", "command_editor_too_long", 10, new TabScene(true, true));
            Screenshots.step("bildschirmfotos: Modi-Tab, Seite Befehle bei GUI-Skala 4", "configurator_modes_commands_scale4", 20,
                    Screenshots.guiScale(4, new TabScene(false, false)));
            Screenshots.step("bildschirmfotos: eigener Befehl im FPV-Flug ausgeführt", "switch_command_fpv", 20, new FlightScene());
        }
    }

    static SimulatedRadio radio(String guid, String name) {
        DeviceProfile profile = ProfileDefaults.radio(name, AXES);
        profile.setBinding(SwitchFunction.COMMAND_1, SwitchSources.apply(SwitchAssignment.none(), new SwitchSources.Choice(SwitchSource.AXIS, SWITCH_AXIS),
                SwitchFunction.COMMAND_1, false));
        profile.setBinding(SwitchFunction.COMMAND_2, SwitchAssignment.button(HOLD_BUTTON, false));
        profile.setBinding(SwitchFunction.COMMAND_3, SwitchAssignment.button(UNKNOWN_BUTTON, false));
        SimulatedRadio radio = new SimulatedRadio(guid, name, profile, AXES, BUTTONS);
        for (int axis = 4; axis < AXES; axis++) {
            radio.setMicros(axis, 1000);
        }
        return radio;
    }

    private static StepResult tabStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        TabCheck s = (TabCheck) ctx.state().computeIfAbsent("tab", key -> new TabCheck());
        try {
            StepResult result = tabRun(ctx, mc, s);
            if (result.status() != StepResult.Status.PENDING) {
                s.cleanup(mc);
            }
            return result;
        } catch (RuntimeException e) {
            s.cleanup(mc);
            throw e;
        }
    }

    private static StepResult tabRun(SelfTest.StepContext ctx, Minecraft mc, TabCheck s) {
        InputConfig config = InputConfig.get();
        switch (s.stage) {
            case 0 -> {
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                SimulatedRadio radio = radio(GUID, "Propwash-Testfunke");
                radio.profile().setCommand(SwitchFunction.COMMAND_1, new SwitchCommand("/time set night", "/time set day"));
                config.profiles.put(GUID, radio.profile().copy());
                InputConfig.commit(config);
                TransmitterInput.INSTANCE.simulate(radio);
                s.radio = radio;
                ConfigTabs.open(null, ModesTab.ID);
                s.stage = 1;
                return StepResult.pending();
            }
            case 1 -> {
                ModesTab tab = modesTab(mc);
                if (tab == null) {
                    return ctx.tick() > 200 ? StepResult.fail("Modi-Tab öffnet nicht") : StepResult.pending();
                }
                if (tab.commandsShown()) {
                    return StepResult.fail("Modi-Tab startet nicht auf der Seite Funktionen");
                }
                if (!(findWidget(mc, Component.translatable("propwash.input.modes.page.flight").getString()) instanceof FlatButton)) {
                    return StepResult.fail("Seite „Funktionen“ fehlt im Kopf des Modi-Tabs");
                }
                if (findWidget(mc, Component.translatable("propwash.input.command.edit").getString()) != null) {
                    return StepResult.fail("„Befehl einrichten …“ steht schon auf der Seite Funktionen");
                }
                AbstractWidget pageTab = findWidget(mc, Component.translatable("propwash.input.modes.page.commands").getString());
                if (!(pageTab instanceof FlatButton commands)) {
                    return StepResult.fail("Seite „Befehle“ ist kein Knopf: " + pageTab);
                }
                commands.press();
                if (!tab.commandsShown()) {
                    return StepResult.fail("Knopf „Befehle“ wechselt die Seite nicht");
                }
                List<String> expected = new ArrayList<>();
                for (int i = 1; i <= SwitchFunction.COMMAND_COUNT; i++) {
                    expected.add("command_" + i);
                }
                if (!tab.rowNames().equals(expected)) {
                    return StepResult.fail("Seite Befehle zeigt " + tab.rowNames());
                }
                if (tab.selected() != SwitchFunction.COMMAND_1) {
                    return StepResult.fail("Seite Befehle wählt nicht Eigener Befehl 1: " + tab.selected());
                }
                tab.select(SwitchFunction.COMMAND_2);
                s.stage = 2;
                return StepResult.pending();
            }
            case 2 -> {
                ModesTab tab = modesTab(mc);
                if (tab == null || tab.selected() != SwitchFunction.COMMAND_2) {
                    return StepResult.fail("Auswahl Eigener Befehl 2 geht verloren");
                }
                AbstractWidget edit = findWidget(mc, Component.translatable("propwash.input.command.edit").getString());
                if (edit == null || !edit.active) {
                    return StepResult.fail("Knopf „Befehl einrichten …“ fehlt oder ist gesperrt");
                }
                tab.editCommand(SwitchFunction.COMMAND_2);
                if (!(mc.gui.screen() instanceof CommandEditScreen editor) || editor.function() != SwitchFunction.COMMAND_2) {
                    return StepResult.fail("„Befehl einrichten“ öffnet nicht das Fenster: " + mc.gui.screen());
                }
                editor.setTexts("x".repeat(SwitchCommands.MAX_LENGTH), "");
                if (editor.canApply() || editor.status(true).color() != Palette.ERROR || editor.apply()) {
                    return StepResult.fail("zu langer Befehl wird nicht abgelehnt (" + editor.status(true).text().getString() + ")");
                }
                editor.setTexts("  tag @s   add " + TAG_BUTTON, "/tag @s remove " + TAG_BUTTON);
                if (!editor.canApply()) {
                    return StepResult.fail("gültiger Befehl lässt sich nicht übernehmen: " + editor.status(true).text().getString());
                }
                if (!editor.apply() || !(mc.gui.screen() instanceof ConfiguratorScreen)) {
                    return StepResult.fail("Übernehmen kehrt nicht in den Konfigurator zurück: " + mc.gui.screen());
                }
                s.stage = 3;
                return StepResult.pending();
            }
            case 3 -> {
                if (!(mc.gui.screen() instanceof ConfiguratorScreen screen) || !(screen.activeTab() instanceof ModesTab tab)) {
                    return StepResult.fail("Konfigurator nach dem Fenster nicht mehr auf dem Modi-Tab");
                }
                if (!tab.commandsShown() || tab.selected() != SwitchFunction.COMMAND_2) {
                    return StepResult.fail("Seite oder Auswahl nach dem Fenster verloren");
                }
                if (!screen.hasUnsavedChanges()) {
                    return StepResult.fail("übernommener Befehl zählt nicht als Änderung");
                }
                screen.saveAndClose();
                DeviceProfile saved = InputConfig.get().profile(GUID);
                SwitchCommand command = saved == null ? new SwitchCommand() : saved.command(SwitchFunction.COMMAND_2);
                if (!command.equals(new SwitchCommand("/tag @s add " + TAG_BUTTON, "/tag @s remove " + TAG_BUTTON))) {
                    return StepResult.fail("gespeicherter Befehl: " + command);
                }
                if (!saved.command(SwitchFunction.COMMAND_1).hasOn()) {
                    return StepResult.fail("Befehl 1 geht beim Speichern verloren");
                }
                ConfigTabs.open(null, ModesTab.ID);
                s.stage = 4;
                return StepResult.pending();
            }
            case 4 -> {
                ModesTab tab = modesTab(mc);
                if (tab == null) {
                    return ctx.tick() > 400 ? StepResult.fail("Modi-Tab öffnet nicht erneut") : StepResult.pending();
                }
                if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                    return StepResult.pending();
                }
                tab.resetDefaults();
                screen.saveAndClose();
                DeviceProfile saved = InputConfig.get().profile(GUID);
                if (saved == null || !saved.command(SwitchFunction.COMMAND_2).hasOn() || !saved.binding(SwitchFunction.COMMAND_2).bound()) {
                    return StepResult.fail("„Standard“ auf der Seite Funktionen löscht eigene Befehle");
                }
                ConfigTabs.open(null, ModesTab.ID);
                s.stage = 5;
                return StepResult.pending();
            }
            case 5 -> {
                ModesTab tab = modesTab(mc);
                if (tab == null || !(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                    return ctx.tick() > 500 ? StepResult.fail("Modi-Tab öffnet nicht ein drittes Mal") : StepResult.pending();
                }
                tab.showCommands(true);
                tab.resetDefaults();
                screen.saveAndClose();
                DeviceProfile saved = InputConfig.get().profile(GUID);
                if (saved == null || !saved.commands.isEmpty() || saved.binding(SwitchFunction.COMMAND_1).bound()) {
                    return StepResult.fail("„Standard“ auf der Seite Befehle leert die Befehle nicht");
                }
                if (!saved.binding(SwitchFunction.ARM).bound()) {
                    return StepResult.fail("„Standard“ auf der Seite Befehle löscht den Arm-Schalter");
                }
                if (KeyMapping.get(KeyDefaults.COMMANDS.getFirst().mappingName()) == null) {
                    return StepResult.fail("Taste „Eigener Befehl 1“ nicht registriert");
                }
                ctx.log("Modi-Tab: Seite Befehle, Fenster mit Längenprüfung, Speichern und Standard je Seite geprüft");
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + s.stage);
            }
        }
    }

    private static @Nullable ModesTab modesTab(Minecraft mc) {
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            return null;
        }
        ConfigTab active = screen.activeTab();
        if (!(active instanceof ModesTab tab)) {
            screen.selectTab(ModesTab.ID);
            return null;
        }
        return tab;
    }

    private static @Nullable AbstractWidget findWidget(Minecraft mc, String text) {
        if (mc.gui.screen() == null) {
            return null;
        }
        for (GuiEventListener child : mc.gui.screen().children()) {
            if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals(text)) {
                return widget;
            }
        }
        return null;
    }

    private static StepResult flightStep(SelfTest.StepContext ctx) {
        Flight s = (Flight) ctx.state().computeIfAbsent("flight", key -> new Flight());
        try {
            StepResult result = flightRun(ctx, s);
            if (result.status() != StepResult.Status.PENDING) {
                s.cleanup(ctx);
            }
            return result;
        } catch (RuntimeException e) {
            s.cleanup(ctx);
            throw e;
        }
    }

    private static StepResult flightRun(SelfTest.StepContext ctx, Flight s) {
        if (s.pending != null) {
            if (s.pending.isCompletedExceptionally()) {
                return StepResult.fail(s.phase + ": Server-Aktion fehlgeschlagen: " + s.pending.exceptionNow().getMessage());
            }
            if (!s.pending.isDone()) {
                return StepResult.pending();
            }
            s.pending = null;
        }
        if (ctx.tick() > MAX_TICKS) {
            return StepResult.fail("Zeit abgelaufen in Phase " + s.phase + " (Tags " + s.tags + ", gesendet " + SwitchCommandRunner.INSTANCE.sent() + ")");
        }
        Minecraft mc = Minecraft.getInstance();
        SwitchCommandRunner runner = SwitchCommandRunner.INSTANCE;
        int waited = ctx.tick() - s.since;
        switch (s.phase) {
            case SPAWN -> {
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                s.pending = ctx.onServer(server -> s.spawnAndFly(server, DroneBuild.FREESTYLE));
                s.enter(Phase.SESSION, ctx.tick());
                return StepResult.pending();
            }
            case SESSION -> {
                if (!s.flying()) {
                    return waited > 200 ? StepResult.fail("keine FPV-Sitzung") : StepResult.pending();
                }
                PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
                s.startSent = runner.sent();
                s.sent = s.startSent;
                SimulatedRadio radio = radio(GUID, "Propwash-Testfunke");
                DeviceProfile profile = radio.profile();
                profile.setCommand(SwitchFunction.COMMAND_1, new SwitchCommand("/tag @s add " + TAG_SWITCH, "/tag @s remove " + TAG_SWITCH));
                profile.setCommand(SwitchFunction.COMMAND_2, new SwitchCommand("tag @s add " + TAG_BUTTON, ""));
                profile.setCommand(SwitchFunction.COMMAND_3, new SwitchCommand(UNKNOWN, ""));
                profile.setCommand(SwitchFunction.command(KEY_SLOT), new SwitchCommand("/tag @s add " + TAG_KEY, "/tag @s remove " + TAG_KEY));
                radio.setMicros(SWITCH_AXIS, 2000);
                s.radio = radio;
                TransmitterInput.INSTANCE.simulate(radio);
                s.enter(Phase.PRIMED, ctx.tick());
                return StepResult.pending();
            }
            case PRIMED -> {
                if (!runner.primed() || waited < 10) {
                    return waited > 60 ? StepResult.fail("eigene Befehle werden im Flug nicht scharf") : StepResult.pending();
                }
                if (runner.sent() != s.startSent) {
                    return StepResult.fail("Schalter, der beim Flugstart schon an war, hat einen Befehl ausgelöst: " + runner.lastSent());
                }
                s.radio.setMicros(SWITCH_AXIS, 1000);
                s.enter(Phase.SWITCH_OFF_FIRST, ctx.tick());
                return StepResult.pending();
            }
            case SWITCH_OFF_FIRST -> {
                return s.expectTags(ctx, Set.of(), "Schalter aus (Aus-Befehl ohne Tag)", 1, () -> {
                    s.radio.setMicros(SWITCH_AXIS, 2000);
                    s.enter(Phase.SWITCH_ON, ctx.tick());
                });
            }
            case SWITCH_ON -> {
                return s.expectTags(ctx, Set.of(TAG_SWITCH), "Schalter an", 1, () -> {
                    s.radio.setMicros(SWITCH_AXIS, 1000);
                    s.enter(Phase.SWITCH_OFF, ctx.tick());
                });
            }
            case SWITCH_OFF -> {
                return s.expectTags(ctx, Set.of(), "Schalter wieder aus", 1, () -> {
                    s.radio.setButton(HOLD_BUTTON, true);
                    s.enter(Phase.BUTTON_DOWN, ctx.tick());
                });
            }
            case BUTTON_DOWN -> {
                return s.expectTags(ctx, Set.of(TAG_BUTTON), "Taster gedrückt (ohne Schrägstrich eingetragen)", 1, () -> {
                    s.radio.setButton(HOLD_BUTTON, false);
                    s.enter(Phase.BUTTON_UP, ctx.tick());
                });
            }
            case BUTTON_UP -> {
                if (waited < QUIET_TICKS) {
                    return StepResult.pending();
                }
                if (runner.sent() != s.sent) {
                    return StepResult.fail("Loslassen ohne Aus-Befehl sendet trotzdem: " + runner.lastSent());
                }
                mc.gui.setScreen(new ChatScreen("", false));
                s.enter(Phase.SCREEN_OPEN, ctx.tick());
                return StepResult.pending();
            }
            case SCREEN_OPEN -> {
                if (!(mc.gui.screen() instanceof ChatScreen)) {
                    return waited > 40 ? StepResult.fail("Chat öffnet nicht") : StepResult.pending();
                }
                if (waited == 5) {
                    s.radio.setMicros(SWITCH_AXIS, 2000);
                }
                if (waited < 5 + QUIET_TICKS) {
                    return StepResult.pending();
                }
                if (runner.sent() != s.sent || runner.lastOutcome() != CommandTrigger.Outcome.BLOCKED) {
                    return StepResult.fail("Befehl läuft trotz offenem Fenster (gesendet " + runner.sent() + ", " + runner.lastOutcome() + ")");
                }
                mc.gui.setScreen(null);
                s.enter(Phase.SCREEN_CLOSED, ctx.tick());
                return StepResult.pending();
            }
            case SCREEN_CLOSED -> {
                if (waited < QUIET_TICKS) {
                    return StepResult.pending();
                }
                if (runner.sent() != s.sent) {
                    return StepResult.fail("nach dem Schließen läuft der blockierte Befehl nach: " + runner.lastSent());
                }
                return s.expectTags(ctx, Set.of(TAG_BUTTON), "nach dem Schließen ohne Nachholen", 0, () -> {
                    s.radio.setMicros(SWITCH_AXIS, 1000);
                    s.enter(Phase.AFTER_SCREEN_OFF, ctx.tick());
                });
            }
            case AFTER_SCREEN_OFF -> {
                return s.expectSent(ctx, 1, "Schalter nach dem Fenster aus", () -> {
                    s.radio.setMicros(SWITCH_AXIS, 2000);
                    s.enter(Phase.AFTER_SCREEN_ON, ctx.tick());
                });
            }
            case AFTER_SCREEN_ON -> {
                return s.expectTags(ctx, Set.of(TAG_BUTTON, TAG_SWITCH), "Schalter nach dem Fenster wieder an", 1, () -> {
                    s.radio.setButton(UNKNOWN_BUTTON, true);
                    s.enter(Phase.UNKNOWN, ctx.tick());
                });
            }
            case UNKNOWN -> {
                return s.expectSent(ctx, 1, "unbekannter Befehl", () -> {
                    ClientPacketListener connection = mc.getConnection();
                    if (!UNKNOWN.equals(runner.lastSent())) {
                        s.failure = "zuletzt gesendet: " + runner.lastSent();
                    } else if (connection == null || SwitchCommandRunner.known(connection, SwitchCommands.body(UNKNOWN))) {
                        s.failure = "unbekannter Befehl gilt als bekannt";
                    } else if (!SwitchCommandRunner.known(connection, "tag @s add " + TAG_KEY)) {
                        s.failure = "„/tag“ gilt im Kreativmodus als unbekannt";
                    }
                    s.radio.setButton(UNKNOWN_BUTTON, false);
                    KeyMapping key = InputKeys.COMMANDS.get(KEY_SLOT);
                    s.keyBefore = InputConstants.getKey(key.saveString());
                    key.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F12));
                    KeyMapping.resetMapping();
                    KeyMapping.click(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F12));
                    s.enter(Phase.KEY_ON, ctx.tick());
                });
            }
            case KEY_ON -> {
                if (s.failure != null) {
                    return StepResult.fail(s.failure);
                }
                return s.expectTags(ctx, Set.of(TAG_BUTTON, TAG_SWITCH, TAG_KEY), "Taste erster Druck", 1, () -> {
                    s.keyAgainAt = ctx.tick() + 14;
                    s.enter(Phase.KEY_OFF, ctx.tick());
                });
            }
            case KEY_OFF -> {
                if (ctx.tick() == s.keyAgainAt) {
                    KeyMapping.click(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F12));
                }
                if (ctx.tick() <= s.keyAgainAt) {
                    return StepResult.pending();
                }
                return s.expectTags(ctx, Set.of(TAG_BUTTON, TAG_SWITCH), "Taste zweiter Druck (Aus-Befehl)", 1, () -> {
                    s.pending = ctx.onServer(server -> DronePiloting.pilotedBy(firstPlayer(server).getUUID()).ifPresent(DronePiloting::release));
                    s.enter(Phase.RELEASED, ctx.tick());
                });
            }
            case RELEASED -> {
                if (PilotSession.INSTANCE.isActive()) {
                    return waited > 100 ? StepResult.fail("Flug endet nicht") : StepResult.pending();
                }
                if (s.offFlightAt < 0) {
                    s.offFlightAt = ctx.tick();
                    s.radio.setMicros(SWITCH_AXIS, 1000);
                    s.radio.setButton(HOLD_BUTTON, true);
                    return StepResult.pending();
                }
                if (ctx.tick() - s.offFlightAt < QUIET_TICKS) {
                    return StepResult.pending();
                }
                if (runner.sent() != s.sent) {
                    return StepResult.fail("Befehl läuft ohne Flug: " + runner.lastSent());
                }
                ctx.log("Eigene Befehle: Schalter an/aus, Taster ohne Aus-Befehl, Fenster offen, unbekannter Befehl, Taste mit Wechsel und ohne Flug geprüft ("
                        + (runner.sent() - s.startSent) + " Befehle)");
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + s.phase);
            }
        }
    }

    static ServerPlayer firstPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private enum Phase {
        SPAWN,
        SESSION,
        PRIMED,
        SWITCH_OFF_FIRST,
        SWITCH_ON,
        SWITCH_OFF,
        BUTTON_DOWN,
        BUTTON_UP,
        SCREEN_OPEN,
        SCREEN_CLOSED,
        AFTER_SCREEN_OFF,
        AFTER_SCREEN_ON,
        UNKNOWN,
        KEY_ON,
        KEY_OFF,
        RELEASED
    }

    private static final class TabCheck {
        private int stage;
        private @Nullable SimulatedRadio radio;
        private boolean cleaned;

        private void cleanup(Minecraft mc) {
            if (this.cleaned) {
                return;
            }
            this.cleaned = true;
            if (mc.gui.screen() instanceof CommandEditScreen || mc.gui.screen() instanceof ConfiguratorScreen) {
                mc.gui.setScreen(null);
            }
            TransmitterInput.INSTANCE.simulate(null);
            InputConfig config = InputConfig.get();
            config.profiles.remove(GUID);
            InputConfig.commit(config);
        }
    }

    static class Flight {
        Phase phase = Phase.SPAWN;
        int since;
        volatile int droneId = -1;
        volatile @Nullable ItemStack previousHead;
        volatile @Nullable ItemStack previousHand;
        volatile Set<String> tags = Set.of();
        @Nullable SimulatedRadio radio;
        @Nullable CompletableFuture<Void> pending;
        @Nullable CompletableFuture<Void> probe;
        @Nullable String failure;
        InputConstants.@Nullable Key keyBefore;
        int sent;
        int startSent = -1;
        int keyAgainAt = -1;
        int offFlightAt = -1;
        boolean cleaned;

        void enter(Phase next, int tick) {
            this.phase = next;
            this.since = tick;
            this.probe = null;
        }

        boolean flying() {
            PilotSession session = PilotSession.INSTANCE;
            return session.isFpv() && session.drone() != null && session.drone().getId() == this.droneId;
        }

        StepResult expectSent(SelfTest.StepContext ctx, int more, String what, Runnable next) {
            int now = SwitchCommandRunner.INSTANCE.sent();
            if (now < this.sent + more) {
                return ctx.tick() - this.since > WAIT_TICKS ? StepResult.fail(what + ": nicht gesendet (" + SwitchCommandRunner.INSTANCE.lastOutcome() + ")")
                        : StepResult.pending();
            }
            if (now > this.sent + more) {
                return StepResult.fail(what + ": " + (now - this.sent) + " statt " + more + " Befehle gesendet");
            }
            this.sent = now;
            next.run();
            return StepResult.pending();
        }

        StepResult expectTags(SelfTest.StepContext ctx, Set<String> wanted, String what, int more, Runnable next) {
            int now = SwitchCommandRunner.INSTANCE.sent();
            if (now < this.sent + more) {
                return ctx.tick() - this.since > WAIT_TICKS ? StepResult.fail(what + ": nicht gesendet (" + SwitchCommandRunner.INSTANCE.lastOutcome() + ")")
                        : StepResult.pending();
            }
            if (now > this.sent + more) {
                return StepResult.fail(what + ": " + (now - this.sent) + " statt " + more + " Befehle gesendet, zuletzt " + SwitchCommandRunner.INSTANCE.lastSent());
            }
            if (this.probe == null || this.probe.isDone() && !this.tags.equals(wanted)) {
                if (this.probe != null && ctx.tick() - this.since > WAIT_TICKS) {
                    return StepResult.fail(what + ": Server-Tags " + this.tags + " statt " + wanted);
                }
                this.probe = ctx.onServer(server -> this.tags = Set.copyOf(ourTags(firstPlayer(server).entityTags())));
                return StepResult.pending();
            }
            if (!this.probe.isDone()) {
                return StepResult.pending();
            }
            this.sent = now;
            next.run();
            return StepResult.pending();
        }

        private static List<String> ourTags(Set<String> all) {
            List<String> out = new ArrayList<>();
            for (String tag : all) {
                if (tag.startsWith("propwash_cmd_")) {
                    out.add(tag);
                }
            }
            return out;
        }

        void spawnAndFly(MinecraftServer server, DroneBuild build) {
            ServerPlayer player = firstPlayer(server);
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            ServerLevel level = player.level();
            QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("EntityType.create lieferte null");
            }
            Vec3 feet = player.position();
            drone.snapTo(feet.x, feet.y, feet.z + 3.0, 0.0F, 0.0F);
            drone.setAttitude(new Quaternionf().rotationY((float) Math.PI));
            drone.setBuild(build);
            if (!level.addFreshEntity(drone)) {
                throw new IllegalStateException("addFreshEntity fehlgeschlagen");
            }
            this.droneId = drone.getId();
            ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
            if (goggles.isEmpty()) {
                throw new IllegalStateException("Kreativ-Brille nicht registriert");
            }
            goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            this.previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
            player.setItemSlot(EquipmentSlot.HEAD, goggles);
            ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
            remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            this.previousHand = player.getMainHandItem().copy();
            player.setItemSlot(EquipmentSlot.MAINHAND, remote);
            for (String tag : ourTags(player.entityTags())) {
                player.removeTag(tag);
            }
            DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
            if (result != DronePiloting.StartResult.STARTED) {
                throw new IllegalStateException("FPV-Start verweigert: " + result);
            }
        }

        void cleanup(SelfTest.StepContext ctx) {
            if (this.cleaned) {
                return;
            }
            this.cleaned = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof ChatScreen) {
                mc.gui.setScreen(null);
            }
            TransmitterInput.INSTANCE.simulate(null);
            PilotSession.INSTANCE.clearTestInput();
            KeyMapping key = InputKeys.COMMANDS.get(KEY_SLOT);
            if (this.keyBefore != null) {
                key.setKey(this.keyBefore);
                KeyMapping.resetMapping();
            }
            ctx.onServer(server -> {
                for (ServerLevel level : server.getAllLevels()) {
                    Entity entity = this.droneId >= 0 ? level.getEntity(this.droneId) : null;
                    if (entity instanceof QuadcopterEntity drone) {
                        DronePiloting.release(drone);
                        drone.discard();
                    }
                }
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                if (!players.isEmpty()) {
                    ServerPlayer player = players.getFirst();
                    player.setItemSlot(EquipmentSlot.HEAD, this.previousHead == null ? ItemStack.EMPTY : this.previousHead);
                    player.setItemSlot(EquipmentSlot.MAINHAND, this.previousHand == null ? ItemStack.EMPTY : this.previousHand);
                    for (String tag : ourTags(player.entityTags())) {
                        player.removeTag(tag);
                    }
                }
            });
        }
    }

    private static final class TabScene implements Screenshots.Scene {
        private final boolean editor;
        private final boolean tooLong;
        private @Nullable SimulatedRadio radio;

        private TabScene(boolean editor, boolean tooLong) {
            this.editor = editor;
            this.tooLong = tooLong;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.radio == null) {
                SimulatedRadio fake = radio(SHOT_GUID, "Radiomaster Pocket");
                DeviceProfile profile = fake.profile();
                profile.setCommand(SwitchFunction.COMMAND_1, new SwitchCommand("/time set night", "/time set day"));
                profile.setCommand(SwitchFunction.COMMAND_2, new SwitchCommand("/weather clear", ""));
                profile.setCommand(SwitchFunction.COMMAND_3, new SwitchCommand("/gamemode spectator", "/gamemode creative"));
                profile.setBinding(SwitchFunction.COMMAND_4, SwitchAssignment.axisMicros(9, 1700, 2100));
                profile.setCommand(SwitchFunction.COMMAND_4, new SwitchCommand("/effect give @s night_vision 60", "/effect clear @s night_vision"));
                fake.setMicros(SWITCH_AXIS, 2000);
                this.radio = fake;
                InputConfig.get().profiles.put(SHOT_GUID, profile.copy());
                TransmitterInput.INSTANCE.simulate(fake);
                ConfigTabs.open(null, ModesTab.ID);
                return StepResult.pending();
            }
            if (mc.gui.screen() instanceof CommandEditScreen editor) {
                if (this.tooLong) {
                    editor.setTexts("/tellraw @a " + "{\"text\":\"Propwash FPV – custom command test\"} ".repeat(7), "/time set day");
                }
                return ctx.tick() > 10 ? StepResult.pass() : StepResult.pending();
            }
            ModesTab tab = modesTab(mc);
            if (tab == null) {
                return ctx.tick() > 200 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
            }
            if (!tab.commandsShown()) {
                tab.showCommands(true);
                return StepResult.pending();
            }
            if (tab.selected() != SwitchFunction.COMMAND_1) {
                tab.select(SwitchFunction.COMMAND_1);
                return StepResult.pending();
            }
            if (this.editor) {
                tab.editCommand(SwitchFunction.COMMAND_1);
                return StepResult.pending();
            }
            return ctx.tick() > 20 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof CommandEditScreen) {
                mc.gui.setScreen(null);
            }
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            TransmitterInput.INSTANCE.simulate(null);
            InputConfig.get().profiles.remove(SHOT_GUID);
            this.radio = null;
        }
    }

    private static final class FlightScene implements Screenshots.Scene {
        private @Nullable Flight flight;
        private volatile long previousTime = -1L;
        private int stage;
        private boolean flipped;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Flight f = this.flight;
            if (f == null) {
                f = new Flight();
                this.flight = f;
                Flight started = f;
                f.pending = ctx.onServer(server -> {
                    this.previousTime = server.overworld().getOverworldClockTime();
                    started.spawnAndFly(server, DroneBuild.FREESTYLE);
                });
                f.since = ctx.tick();
                return StepResult.pending();
            }
            if (f.pending != null) {
                if (f.pending.isCompletedExceptionally()) {
                    return StepResult.fail("Flug-Szene: " + f.pending.exceptionNow());
                }
                if (!f.pending.isDone()) {
                    return StepResult.pending();
                }
                f.pending = null;
            }
            if (ctx.tick() - f.since > 600) {
                return StepResult.fail("Flug-Szene wird nicht fertig, Stufe " + this.stage);
            }
            if (!f.flying()) {
                return StepResult.pending();
            }
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            switch (this.stage) {
                case 0 -> {
                    SimulatedRadio radio = radio(SHOT_GUID, "Radiomaster Pocket");
                    radio.profile().setCommand(SwitchFunction.COMMAND_1, new SwitchCommand("/time set night", "/time set day"));
                    f.radio = radio;
                    TransmitterInput.INSTANCE.simulate(radio);
                    f.sent = SwitchCommandRunner.INSTANCE.sent();
                    this.stage = 1;
                    f.since = ctx.tick();
                    return StepResult.pending();
                }
                default -> {
                    return SwitchCommandRunner.INSTANCE.primed() && ctx.tick() - f.since >= 20 ? StepResult.pass() : StepResult.pending();
                }
            }
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Flight f = this.flight;
            PilotSession.INSTANCE.setTestInput(StickPositions.IDLE, false, StabMode.ANGLE);
            if (!this.flipped && f != null && f.radio != null) {
                f.radio.setMicros(SWITCH_AXIS, 2000);
                this.flipped = true;
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Flight f = this.flight;
            if (f != null) {
                f.cleanup(ctx);
            }
            long time = this.previousTime;
            if (time >= 0L) {
                ctx.onServer(server -> {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set " + time);
                    ctx.log("Tageszeit zurückgesetzt: " + time + " → " + server.overworld().getOverworldClockTime());
                });
            }
            this.flight = null;
            this.stage = 0;
            this.flipped = false;
            this.previousTime = -1L;
        }
    }
}
