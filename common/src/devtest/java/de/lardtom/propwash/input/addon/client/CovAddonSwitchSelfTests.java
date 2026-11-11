package de.lardtom.propwash.input.addon.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.input.AddonSwitch;
import de.lardtom.propwash.api.input.SwitchSpec;
import de.lardtom.propwash.apiimpl.Api17Registries;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.dev.example.ExampleSwitches;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.SwitchAssignment;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.SwitchSource;
import de.lardtom.propwash.input.SwitchSources;
import de.lardtom.propwash.input.addon.AccessoryActionHints;
import de.lardtom.propwash.input.addon.AddonBindings;
import de.lardtom.propwash.input.addon.AddonInputs;
import de.lardtom.propwash.input.client.FunctionKeys;
import de.lardtom.propwash.input.client.KeyDefaults;
import de.lardtom.propwash.input.client.KeyNames;
import de.lardtom.propwash.input.client.RawInput;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.input.client.screen.AddonSwitchRows;
import de.lardtom.propwash.input.client.screen.InputLabels;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.FirstUseHints;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class CovAddonSwitchSelfTests {
    public static final String GROUP = "addon-api-17: A1 – Abdeckung: ";
    private static final String GERMAN = "de_de";
    private static final String GUID = "0300c0a1c0a1a1a1c0a1a1a100000000";
    private static final String PAD_GUID = "0300c0a1c0a1a1a1c0a1a1a1000000ad";
    private static final int RADIO_AXES = 8;
    private static final int RADIO_BUTTONS = 12;
    private static final int TASTER = 4;
    private static final int PAD_GHOST = 0;
    private static final int PAD_HOLD = 7;
    private static final int PRESSES = 3;
    private static final int WAIT_TICKS = 160;
    private static final Identifier HOLD = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "cov_hold");
    private static final String INDEX = "cov_a1_index";
    private static final String SINCE = "cov_a1_since";
    private static final String FUTURE = "cov_a1_future";
    private static @Nullable SimulatedRadio radio;
    private static InputConstants.@Nullable Key savedAction;

    private CovAddonSwitchSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "ohne Add-on nur die Reiter „Funktionen“ und „Befehle“ im Tab „Modi“, keine Seite „Add-ons“", 300,
                CovAddonSwitchSelfTests::noAddonStep);
        SelfTest.clientStep(GROUP + "Hinweis beim ersten Flug: jede Zeile mit Name und Belegung, mit Funke der Schaltername", 1600,
                CovAddonSwitchSelfTests::hintLinesStep);
        SelfTest.clientStep(GROUP + "unbelegte Zubehör-Aktion 1: genau ein Hinweis mit „nicht belegt“", 900, CovAddonSwitchSelfTests::unboundStep);
        SelfTest.clientStep(GROUP + "Zubehör-Aktion 1 auf Maustaste 4 und Taster der Funke, beide lösen aus, Zeile behält „– Blinken“", 1600,
                CovAddonSwitchSelfTests::otherTriggersStep);
        SelfTest.clientStep(GROUP + "Gamepad-Profil: Umschalter rastet mit Prellen je Druck einmal, ein ADDON_SWITCH_CHANGED je Wechsel, Taster nur gehalten",
                1600, CovAddonSwitchSelfTests::gamepadStep);
        String back = GROUP + "Sprache zurückstellen";
        SelfTest.switchStep(GROUP + "Sprache Deutsch", back, 2400, ctx -> Screenshots.language(ctx, GERMAN));
        SelfTest.clientStep(GROUP + "Deutsch: Hinweis beim ersten Flug: jede Zeile mit Name und Belegung, mit Funke der Schaltername", 1600,
                CovAddonSwitchSelfTests::hintLinesStep);
        SelfTest.clientStep(GROUP + "Deutsch: unbelegte Zubehör-Aktion 1: genau ein Hinweis mit „nicht belegt“", 900,
                CovAddonSwitchSelfTests::unboundStep);
        SelfTest.clientStep(back, 2400, Screenshots::restoreLanguage);
        SelfTest.clientStep(GROUP + "Aufräumen", 200, CovAddonSwitchSelfTests::cleanupStep);
    }

    private interface Phase {
        @Nullable StepResult run(SelfTest.StepContext ctx, int waited);
    }

    private static StepResult script(SelfTest.StepContext ctx, List<Phase> phases) {
        Map<String, Object> state = ctx.state();
        int index = (int) state.getOrDefault(INDEX, 0);
        state.putIfAbsent(SINCE, ctx.tick());
        while (index < phases.size()) {
            StepResult result;
            try {
                result = phases.get(index).run(ctx, ctx.tick() - (int) state.get(SINCE));
            } catch (RuntimeException e) {
                Constants.LOG.error("[Selbsttest] {}", GROUP, e);
                result = StepResult.fail(e.getMessage() != null ? e.getMessage() : e.toString());
            }
            if (result == null) {
                index++;
                state.put(INDEX, index);
                state.put(SINCE, ctx.tick());
                continue;
            }
            if (result.status() == StepResult.Status.FAIL) {
                restore(ctx);
            }
            return result;
        }
        return StepResult.pass();
    }

    private static Phase server(String what, java.util.function.Consumer<MinecraftServer> action) {
        return (ctx, waited) -> {
            CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
            if (future == null) {
                ctx.state().put(FUTURE, ctx.onServer(action));
                return StepResult.pending();
            }
            if (!future.isDone()) {
                return waited > WAIT_TICKS ? StepResult.fail(what + ": Server antwortet nicht") : StepResult.pending();
            }
            ctx.state().remove(FUTURE);
            if (future.isCompletedExceptionally()) {
                Throwable cause = future.exceptionNow();
                return StepResult.fail(what + ": " + (cause.getMessage() != null ? cause.getMessage() : cause.toString()));
            }
            return null;
        };
    }

    private static Phase await(String what, java.util.function.BooleanSupplier condition) {
        return (ctx, waited) -> condition.getAsBoolean() ? null : waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail(what);
    }

    private static Phase wait(int ticks) {
        return (ctx, waited) -> waited >= ticks ? null : StepResult.pending();
    }

    private static Phase run(Runnable action) {
        return (ctx, waited) -> {
            action.run();
            return null;
        };
    }

    private static Phase fly(DroneBuild build) {
        return (ctx, waited) -> {
            StepResult started = server("Flugstart", server -> {
                AddonSwitchSelfTests.land(server);
                AddonSwitchSelfTests.fly(AddonSwitchSelfTests.player(server), build);
            }).run(ctx, waited);
            if (started != null) {
                return started;
            }
            ctx.state().put("flying", true);
            return null;
        };
    }

    private static Phase flying() {
        return await("keine FPV-Sitzung", () -> PilotSession.INSTANCE.isActive() && PilotSession.INSTANCE.drone() != null);
    }

    private static Phase land() {
        return (ctx, waited) -> {
            StepResult landed = server("Landen", AddonSwitchSelfTests::land).run(ctx, waited);
            if (landed != null) {
                return landed;
            }
            ctx.state().remove("flying");
            return null;
        };
    }

    private static Phase landed() {
        return await("FPV-Sitzung endet nicht", () -> !PilotSession.INSTANCE.isActive());
    }

    @SuppressWarnings("unchecked")
    private static <T> AtomicReference<T> keep(SelfTest.StepContext ctx, String key) {
        return (AtomicReference<T>) ctx.state().computeIfAbsent(key, k -> new AtomicReference<T>());
    }

    private static boolean exampleLoaded() {
        return AddonInputs.switchSpec(ExampleSwitches.GHOST) != null && ExampleSwitches.ghost() != null;
    }

    private static KeyMapping action(int number) {
        KeyMapping mapping = FunctionKeys.mapping(SwitchFunction.accessory(number));
        if (mapping == null) {
            throw new IllegalStateException("Tastenbelegung der Zubehör-Aktion " + number + " fehlt");
        }
        return mapping;
    }

    private static void defaultKeys() {
        for (int number = 1; number <= 2; number++) {
            KeyMapping mapping = action(number);
            if (number == 1 && savedAction == null) {
                savedAction = InputConstants.getKey(mapping.saveString());
            }
            mapping.setKey(mapping.getDefaultKey());
        }
        KeyMapping.resetMapping();
    }

    private static SimulatedRadio installRadio(String guid, DeviceProfile profile, int axes, int buttons) {
        InputConfig next = InputConfig.get().copy();
        next.profiles.put(guid, profile);
        next.sanitize();
        InputConfig.commit(next);
        SimulatedRadio simulated = new SimulatedRadio(guid, "Abdeckung " + guid.substring(guid.length() - 2), profile, axes, buttons).useSavedProfile();
        for (int i = 0; i < axes; i++) {
            simulated.setAxis(i, -1.0F);
        }
        TransmitterInput.INSTANCE.simulate(simulated);
        radio = simulated;
        return simulated;
    }

    private static void removeRadio() {
        if (radio != null) {
            TransmitterInput.INSTANCE.simulate(null);
            radio = null;
        }
        InputConfig next = InputConfig.get().copy();
        boolean changed = next.profiles.remove(GUID) != null;
        changed |= next.profiles.remove(PAD_GUID) != null;
        if (changed) {
            InputConfig.commit(next);
        }
    }

    private static void restore(SelfTest.StepContext ctx) {
        PilotSession.INSTANCE.clearTestInput();
        removeRadio();
        removeHoldSwitch();
        KeyMapping mapping = FunctionKeys.mapping(SwitchFunction.ACCESSORY_1);
        if (mapping != null && savedAction != null) {
            mapping.setKey(savedAction);
            savedAction = null;
            KeyMapping.resetMapping();
        }
        KeyMapping.releaseAll();
        AccessoryHintClient.mute(false);
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ConfiguratorScreen configurator) {
            configurator.discardAndClose();
        }
        if (ctx.state().containsKey("flying")) {
            ctx.onServer(AddonSwitchSelfTests::land);
        }
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put("flying", true);
            restore(ctx);
        }
        return PilotSession.INSTANCE.isActive() && ctx.tick() < 150 ? StepResult.pending() : StepResult.pass();
    }

    private static StepResult noAddonStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (AddonInputs.any()) {
                ctx.log("Add-on-Schalter angemeldet (" + AddonInputs.switches().size() + " Schalter, " + AddonInputs.axes().size()
                        + " Achsen) – der Fall ohne Add-on läuft mit PROPWASH_DEV_EXAMPLE=off, hier nichts zu prüfen");
                return StepResult.pass();
            }
            if (AddonSwitchRows.available()) {
                return StepResult.fail("AddonSwitchRows.available() ist wahr ohne angemeldeten Schalter");
            }
            ConfigTabs.open(null, ModesTab.ID);
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof ConfiguratorScreen configurator) || !(configurator.activeTab() instanceof ModesTab modes)) {
            return ctx.tick() > 100 ? StepResult.fail("Tab „Modi“ öffnet nicht") : StepResult.pending();
        }
        if (!ctx.state().containsKey("tried")) {
            ctx.state().put("tried", ctx.tick());
            modes.showAddons();
            return StepResult.pending();
        }
        if (ctx.tick() - (int) ctx.state().get("tried") < 6) {
            return StepResult.pending();
        }
        List<String> problems = new ArrayList<>();
        if (modes.addonsShown()) {
            problems.add("Seite „Add-ons“ lässt sich ohne Add-on öffnen");
        }
        String flight = Component.translatable("propwash.input.modes.page.flight").getString();
        String flightShort = Component.translatable("propwash.input.modes.page.flight.short").getString();
        String commands = Component.translatable("propwash.input.modes.page.commands").getString();
        String addons = AddonSwitchRows.pageTitle().getString();
        Integer row = null;
        for (GuiEventListener child : configurator.children()) {
            if (child instanceof FlatButton button && (button.getMessage().getString().equals(flight) || button.getMessage().getString().equals(flightShort))) {
                row = button.getY();
            }
        }
        List<String> tabs = new ArrayList<>();
        for (GuiEventListener child : configurator.children()) {
            if (child instanceof FlatButton button && row != null && button.getY() == row) {
                tabs.add(button.getMessage().getString());
            }
            if (child instanceof Button button && button.getMessage().getString().equals(addons)) {
                problems.add("Knopf „" + addons + "“ ohne Add-on");
            }
        }
        if (row == null || !(tabs.equals(List.of(flight, commands)) || tabs.equals(List.of(flightShort, commands)))) {
            problems.add("Reiter im Kopf des Tabs " + tabs + " statt [" + flight + ", " + commands + "]");
        }
        List<LayoutAudit.Text> texts = LayoutProbe.capture(mc, configurator).texts();
        if (texts.stream().anyMatch(text -> text.value().equals(addons))) {
            problems.add("Text „" + addons + "“ im Tab „Modi“");
        }
        List<LayoutAudit.Finding> findings = LayoutProbe.audit(mc, configurator);
        if (!findings.isEmpty()) {
            problems.add(findings.size() + " Befunde der Layout-Prüfung, zuerst " + findings.getFirst().describe());
        }
        configurator.discardAndClose();
        ctx.log("ohne Add-on: AddonSwitchRows.available() " + AddonSwitchRows.available() + ", Reiter " + tabs + ", Seite „Add-ons“ geöffnet "
                + modes.addonsShown());
        return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
    }

    private static String title() {
        return Component.translatable(AccessoryHintClient.TITLE_KEY,
                Component.translatable("part." + ExampleSwitches.SIGNAL_LIGHT.getNamespace() + "." + ExampleSwitches.SIGNAL_LIGHT.getPath())).getString();
    }

    private static @Nullable List<String> hintLines(Minecraft mc, String title, int actions) {
        List<String> lines = AddonSwitchSelfTests.chat(mc);
        int at = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals(title)) {
                at = i;
                break;
            }
        }
        if (at < actions) {
            return null;
        }
        List<String> out = new ArrayList<>();
        out.add(lines.get(at));
        for (int i = 1; i <= actions; i++) {
            out.add(lines.get(at - i));
        }
        return out;
    }

    private static String actionName(int number) {
        return Component.translatable(ExampleSwitches.actionKey(number)).getString();
    }

    private static String row(int number, String binding) {
        return Component.translatable(AccessoryHintClient.ACTION_KEY, number, actionName(number), binding).getString();
    }

    private static String key(int number) {
        return Component.translatable(AccessoryHintClient.KEY_KEY, KeyNames.of(action(number))).getString();
    }

    private static String keypad(int digit) {
        return Component.translatable(AccessoryHintClient.KEY_KEY, Component.translatable("key.keyboard.keypad." + digit)).getString();
    }

    private static Phase checkHint(String label, java.util.function.Supplier<List<String>> expected) {
        return (ctx, waited) -> {
            Minecraft mc = Minecraft.getInstance();
            String title = title();
            int count = AddonSwitchSelfTests.chatLines(mc, title) - (int) ctx.state().get("chat");
            List<String> lines = hintLines(mc, title, 2);
            if (count < 1 || lines == null) {
                return waited > WAIT_TICKS ? StepResult.fail(label + ": Hinweis „" + title + "“ fehlt") : StepResult.pending();
            }
            if (waited < AccessoryHintClient.DELAY_TICKS + 20) {
                return StepResult.pending();
            }
            if (count != 1) {
                return StepResult.fail(label + ": " + count + " Hinweise statt genau einem");
            }
            List<String> want = expected.get();
            ctx.log(label + ": " + String.join(" | ", lines));
            for (int i = 0; i < want.size(); i++) {
                if (!lines.get(i + 1).equals(want.get(i))) {
                    return StepResult.fail(label + ": Zeile " + (i + 1) + " „" + lines.get(i + 1) + "“ statt „" + want.get(i) + "“");
                }
            }
            return null;
        };
    }

    private static Phase freshHint() {
        return run(() -> {
            AccessoryHintClient.forgetAll();
            AccessoryHintClient.mute(false);
            Minecraft.getInstance().gui.hud.getChat().clearMessages(false);
        });
    }

    private static Phase markChat() {
        return (ctx, waited) -> {
            ctx.state().put("chat", AddonSwitchSelfTests.chatLines(Minecraft.getInstance(), title()));
            return null;
        };
    }

    private static StepResult hintLinesStep(SelfTest.StepContext ctx) {
        if (!exampleLoaded()) {
            ctx.log("Beispiel-Add-on nicht geladen – übersprungen");
            return StepResult.pass();
        }
        boolean german = GERMAN.equals(Minecraft.getInstance().getLanguageManager().getSelected());
        AtomicReference<String> device = keep(ctx, "device");
        return script(ctx, List.of(
                run(() -> {
                    removeRadio();
                    defaultKeys();
                }),
                freshHint(),
                markChat(),
                fly(AddonSwitchSelfTests.signalBuild()),
                flying(),
                checkHint("Standardtasten", () -> List.of(row(1, keypad(9)), row(2, keypad(0)))),
                (ctx2, waited) -> {
                    String line = hintLines(Minecraft.getInstance(), title(), 2).get(1);
                    if (german) {
                        String keypad = Component.translatable("key.keyboard.keypad.9").getString();
                        if (!line.endsWith("Taste " + keypad)) {
                            return StepResult.fail("deutsche Zeile endet nicht mit „Taste " + keypad + "“: " + line);
                        }
                        ctx2.log("deutsche Belegung „Taste " + keypad + "“" + (line.endsWith("Taste Ziffernblock 9") ? ""
                                : " (Prüfplan nennt „Taste Ziffernblock 9“, Minecraft heißt die Taste auf Deutsch „" + keypad + "“)"));
                    }
                    return null;
                },
                land(),
                landed(),
                run(() -> {
                    DeviceProfile profile = ProfileDefaults.radio("Abdeckung Funke", RADIO_AXES);
                    profile.setBinding(SwitchFunction.ACCESSORY_1, SwitchAssignment.button(TASTER, false));
                    profile.setupDone = true;
                    SimulatedRadio simulated = installRadio(GUID, profile, RADIO_AXES, RADIO_BUTTONS);
                    DeviceProfile saved = InputConfig.get().profile(GUID);
                    RawInput raw = simulated.raw();
                    device.set(InputLabels.bindingFor(saved.binding(SwitchFunction.ACCESSORY_1), saved, raw).getString());
                }),
                await("simulierte Funke kommt nicht an", () -> TransmitterInput.INSTANCE.raw() != null),
                freshHint(),
                markChat(),
                fly(AddonSwitchSelfTests.signalBuild()),
                flying(),
                checkHint("Aktion 1 auf der Funke", () -> List.of(
                        row(1, Component.translatable(AccessoryHintClient.OR_KEY, device.get(), keypad(9)).getString()),
                        row(2, keypad(0)))),
                (ctx2, waited) -> {
                    String line = hintLines(Minecraft.getInstance(), title(), 2).get(1);
                    if (device.get() == null || device.get().isBlank() || !line.contains(device.get())) {
                        return StepResult.fail("Zeile nennt den Schalter „" + device.get() + "“ nicht: " + line);
                    }
                    ctx2.log("Schalter der Funke: „" + device.get() + "“, Taste: „" + key(1) + "“");
                    return null;
                },
                land(),
                landed(),
                run(CovAddonSwitchSelfTests::removeRadio)));
    }

    private static StepResult unboundStep(SelfTest.StepContext ctx) {
        if (!exampleLoaded()) {
            ctx.log("Beispiel-Add-on nicht geladen – übersprungen");
            return StepResult.pass();
        }
        boolean german = GERMAN.equals(Minecraft.getInstance().getLanguageManager().getSelected());
        Path file = AccessoryActionHints.CONFIG.path();
        return script(ctx, List.of(
                run(() -> {
                    removeRadio();
                    defaultKeys();
                    action(1).setKey(InputConstants.UNKNOWN);
                    KeyMapping.resetMapping();
                }),
                freshHint(),
                (ctx2, waited) -> {
                    List<String> saved = seenInFile(file);
                    if (saved.contains(ExampleSwitches.SIGNAL_LIGHT.toString())) {
                        return StepResult.fail(file.getFileName() + " nach dem Leeren für " + FirstUseHints.playerKey() + " noch mit " + saved);
                    }
                    return null;
                },
                markChat(),
                fly(AddonSwitchSelfTests.signalBuild()),
                flying(),
                checkHint("Aktion 1 unbelegt", () -> {
                    KeyMapping configurator = KeyMapping.get(KeyDefaults.CONFIGURATOR.mappingName());
                    Component open = configurator == null || configurator.isUnbound() ? Component.translatable("propwash.input.key.unbound")
                            : KeyNames.of(configurator);
                    return List.of(row(1, Component.translatable(AccessoryHintClient.UNBOUND_KEY, open).getString()), row(2, keypad(0)));
                }),
                (ctx2, waited) -> {
                    String line = hintLines(Minecraft.getInstance(), title(), 2).get(1);
                    String marker = german ? "nicht belegt – im Tab „Modi“" : "not assigned – set it in the Modes tab";
                    if (!line.contains(marker)) {
                        return StepResult.fail("Zeile ohne „" + marker + "“: " + line);
                    }
                    if (!seenInFile(file).contains(ExampleSwitches.SIGNAL_LIGHT.toString())) {
                        return StepResult.fail(file.getFileName() + " merkt sich den Hinweis nicht");
                    }
                    return null;
                },
                wait(40),
                (ctx2, waited) -> {
                    int count = AddonSwitchSelfTests.chatLines(Minecraft.getInstance(), title()) - (int) ctx2.state().get("chat");
                    return count == 1 ? null : StepResult.fail(count + " Hinweise statt genau einem");
                },
                land(),
                landed(),
                run(() -> {
                    action(1).setKey(action(1).getDefaultKey());
                    KeyMapping.resetMapping();
                })));
    }

    private static List<String> seenInFile(Path file) {
        String text = read(file);
        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(text).getAsJsonObject();
            com.google.gson.JsonObject players = json.getAsJsonObject("players");
            com.google.gson.JsonArray types = players == null ? null : players.getAsJsonArray(FirstUseHints.playerKey());
            List<String> out = new ArrayList<>();
            if (types != null) {
                types.forEach(type -> out.add(type.getAsString()));
            }
            return out;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static String read(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        } catch (java.io.IOException e) {
            return "? " + e;
        }
    }

    private static @Nullable Button button(ConfiguratorScreen configurator, String key) {
        String label = Component.translatable(key).getString();
        for (GuiEventListener child : configurator.children()) {
            if (child instanceof Button button && button.active && label.equals(button.getMessage().getString())) {
                return button;
            }
        }
        return null;
    }

    private static StepResult otherTriggersStep(SelfTest.StepContext ctx) {
        if (!exampleLoaded()) {
            ctx.log("Beispiel-Add-on nicht geladen – übersprungen");
            return StepResult.pass();
        }
        InputConstants.Key mouse = InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_4);
        Component base = Component.translatable(SwitchFunction.ACCESSORY_1.translationKey());
        String named = Component.translatable(AddonSwitchRows.NAMED_KEY, base, Component.translatable(ExampleSwitches.actionKey(1))).getString();
        Minecraft mc = Minecraft.getInstance();
        return script(ctx, List.of(
                run(() -> {
                    removeRadio();
                    defaultKeys();
                    action(1).setKey(mouse);
                    KeyMapping.resetMapping();
                    DeviceProfile profile = ProfileDefaults.radio("Abdeckung Funke", RADIO_AXES);
                    profile.setupDone = true;
                    installRadio(GUID, profile, RADIO_AXES, RADIO_BUTTONS);
                }),
                await("simulierte Funke kommt nicht an", () -> TransmitterInput.INSTANCE.raw() != null),
                run(() -> ConfigTabs.open(null, ModesTab.ID)),
                await("Tab „Modi“ öffnet nicht", () -> mc.gui.screen() instanceof ConfiguratorScreen c && c.activeTab() instanceof ModesTab),
                (ctx2, waited) -> {
                    ConfiguratorScreen configurator = (ConfiguratorScreen) mc.gui.screen();
                    ((ModesTab) configurator.activeTab()).select(SwitchFunction.ACCESSORY_1);
                    return null;
                },
                wait(5),
                (ctx2, waited) -> {
                    if (!(mc.gui.screen() instanceof ConfiguratorScreen configurator)) {
                        return StepResult.fail("Konfigurator geschlossen");
                    }
                    String shown = LayoutProbe.capture(mc, configurator).texts().stream().map(LayoutAudit.Text::value).reduce("", (a, b) -> a + "|" + b);
                    if (!shown.contains(actionName(1))) {
                        return StepResult.fail("Tab „Modi“ zeigt „– " + actionName(1) + "“ nicht: " + shown);
                    }
                    Button assign = button(configurator, "propwash.input.modes.assign");
                    if (assign == null) {
                        return StepResult.fail("Knopf „Schalter umlegen“ fehlt für Zubehör-Aktion 1");
                    }
                    assign.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                    return null;
                },
                wait(5),
                run(() -> radio.setButton(TASTER, true)),
                wait(20),
                (ctx2, waited) -> {
                    radio.setButton(TASTER, false);
                    if (!(mc.gui.screen() instanceof ConfiguratorScreen configurator)) {
                        return StepResult.fail("Konfigurator beim Erkennen geschlossen");
                    }
                    configurator.saveAndClose();
                    return null;
                },
                (ctx2, waited) -> {
                    DeviceProfile saved = InputConfig.get().profile(GUID);
                    SwitchAssignment binding = saved == null ? SwitchAssignment.none() : saved.binding(SwitchFunction.ACCESSORY_1);
                    if (binding.source != SwitchSource.BUTTON || binding.index != TASTER || binding.toggle) {
                        return StepResult.fail("gespeicherte Belegung " + binding + " statt Taster " + (TASTER + 1));
                    }
                    ctx2.log("Zubehör-Aktion 1 im Tab „Modi“ auf " + binding + " gelegt, Taste " + KeyNames.of(action(1)).getString());
                    AccessoryHintClient.mute(true);
                    return null;
                },
                fly(AddonSwitchSelfTests.signalBuild()),
                flying(),
                wait(10),
                (ctx2, waited) -> {
                    ctx2.state().put("signals", ExampleSwitches.SIGNALS.get());
                    KeyMapping.set(mouse, true);
                    KeyMapping.click(mouse);
                    return null;
                },
                await("Maustaste 4 löst Zubehör-Aktion 1 nicht aus", () -> {
                    KeyMapping.set(mouse, true);
                    return ExampleSwitches.SIGNALS.get() > (int) ctx.state().get("signals");
                }),
                run(() -> KeyMapping.set(mouse, false)),
                wait(10),
                (ctx2, waited) -> {
                    int after = ExampleSwitches.SIGNALS.get() - (int) ctx2.state().get("signals");
                    if (after != 1) {
                        return StepResult.fail("Maustaste 4 löst " + after + "-mal aus statt einmal");
                    }
                    radio.setButton(TASTER, true);
                    return null;
                },
                await("Taster der Funke löst Zubehör-Aktion 1 nicht aus", () -> ExampleSwitches.SIGNALS.get() > (int) ctx.state().get("signals") + 1),
                run(() -> radio.setButton(TASTER, false)),
                wait(10),
                (ctx2, waited) -> {
                    int total = ExampleSwitches.SIGNALS.get() - (int) ctx2.state().get("signals");
                    if (total != 2) {
                        return StepResult.fail("Signallicht zählt " + total + " statt 2 Auslösungen (Maustaste 4 und Taster)");
                    }
                    String row = AddonSwitchRows.title(SwitchFunction.ACCESSORY_1, base).getString();
                    if (!row.equals(named)) {
                        return StepResult.fail("Zubehör-Aktion 1 heißt im Flug „" + row + "“ statt „" + named + "“");
                    }
                    if (!PilotSession.INSTANCE.isActive()) {
                        return StepResult.fail("Maustaste 4 oder Taster beendet den Flug");
                    }
                    ctx2.log("Maustaste 4 und Taster " + (TASTER + 1) + " lösen je einmal aus (Signallicht +" + total + "), Zeile „" + row + "“");
                    return null;
                },
                land(),
                landed(),
                run(() -> ConfigTabs.open(null, ModesTab.ID)),
                await("Tab „Modi“ öffnet nicht", () -> mc.gui.screen() instanceof ConfiguratorScreen c && c.activeTab() instanceof ModesTab),
                (ctx2, waited) -> {
                    ConfiguratorScreen configurator = (ConfiguratorScreen) mc.gui.screen();
                    ((ModesTab) configurator.activeTab()).select(SwitchFunction.ACCESSORY_1);
                    return null;
                },
                wait(5),
                (ctx2, waited) -> {
                    ConfiguratorScreen configurator = (ConfiguratorScreen) mc.gui.screen();
                    String shown = LayoutProbe.capture(mc, configurator).texts().stream().map(LayoutAudit.Text::value).reduce("", (a, b) -> a + "|" + b);
                    configurator.discardAndClose();
                    if (!shown.contains(actionName(1))) {
                        return StepResult.fail("nach beiden Auslösern zeigt der Tab „Modi“ „– " + actionName(1) + "“ nicht mehr");
                    }
                    return null;
                },
                run(() -> {
                    removeRadio();
                    action(1).setKey(action(1).getDefaultKey());
                    KeyMapping.resetMapping();
                    AccessoryHintClient.mute(false);
                })));
    }

    @SuppressWarnings("unchecked")
    private static Map<Identifier, AddonSwitch> switchMap() throws ReflectiveOperationException {
        Field field = Api17Registries.Switches.class.getDeclaredField("switches");
        field.setAccessible(true);
        return (Map<Identifier, AddonSwitch>) field.get(Api17Registries.SWITCHES);
    }

    private static void removeHoldSwitch() {
        if (AddonInputs.switchSpec(HOLD) == null) {
            return;
        }
        try {
            Map<Identifier, AddonSwitch> map = switchMap();
            synchronized (Api17Registries.SWITCHES) {
                map.remove(HOLD);
            }
        } catch (ReflectiveOperationException e) {
            Constants.LOG.error("[Selbsttest] Taster {} nicht abgemeldet", HOLD, e);
        }
    }

    private static StepResult gamepadStep(SelfTest.StepContext ctx) {
        if (!exampleLoaded()) {
            ctx.log("Beispiel-Add-on nicht geladen – übersprungen");
            return StepResult.pass();
        }
        AtomicReference<String> problem = keep(ctx, "problem");
        SwitchSpec ghost = AddonInputs.switchSpec(ExampleSwitches.GHOST);
        List<Phase> phases = new ArrayList<>();
        phases.add(run(() -> {
            removeRadio();
            AccessoryHintClient.mute(true);
            if (AddonInputs.switchSpec(HOLD) == null) {
                Api17Registries.SWITCHES.register(SwitchSpec.builder(HOLD).toggle(false).latchesOnGamepad(true).serverSync(false).build());
            }
            DeviceProfile profile = ProfileDefaults.gamepad("Abdeckung Gamepad");
            SwitchSpec hold = AddonInputs.switchSpec(HOLD);
            profile.setupDone = true;
            SimulatedRadio simulated = installRadio(PAD_GUID, profile, ProfileDefaults.PAD_AXES, ProfileDefaults.PAD_BUTTONS).asGamepad();
            for (int i = 0; i < ProfileDefaults.PAD_AXES; i++) {
                simulated.setAxis(i, 0.0F);
            }
            RawInput raw = simulated.raw();
            boolean pad = raw.usesGamepadLayout(profile);
            profile.setAddonBinding(ExampleSwitches.GHOST,
                    AddonBindings.apply(SwitchAssignment.none(), new SwitchSources.Choice(SwitchSource.BUTTON, PAD_GHOST), ghost, pad));
            profile.setAddonBinding(HOLD, AddonBindings.apply(SwitchAssignment.none(), new SwitchSources.Choice(SwitchSource.BUTTON, PAD_HOLD), hold, pad));
            InputConfig next = InputConfig.get().copy();
            next.profiles.put(PAD_GUID, profile);
            next.sanitize();
            InputConfig.commit(next);
            DeviceProfile saved = InputConfig.get().profile(PAD_GUID);
            SwitchAssignment latched = saved == null ? SwitchAssignment.none() : saved.addonBinding(ExampleSwitches.GHOST);
            SwitchAssignment held = saved == null ? SwitchAssignment.none() : saved.addonBinding(HOLD);
            String gaps = "Gamepad-Profil (gamepadLayout " + (saved != null && saved.gamepadLayout) + "), simulierte Funke: DeviceInfo.gamepad "
                    + simulated.device().gamepad() + ", Gamepad-Knöpfe " + raw.gamepadCapable() + ", usesGamepadLayout " + (saved != null && raw.usesGamepadLayout(saved));
            Constants.LOG.info("[Selbsttest] {}{}", GROUP, gaps);
            if (saved == null || !raw.usesGamepadLayout(saved)) {
                problem.set("simulierte Funke ist kein Gamepad: " + gaps);
            } else if (!latched.toggle || held.toggle || latched.source != SwitchSource.BUTTON || held.source != SwitchSource.BUTTON) {
                problem.set("Belegung auf dem Gamepad: Umschalter rastet " + latched.toggle + ", Taster rastet " + held.toggle);
            }
        }));
        phases.add((c, w) -> problem.get() == null ? null : StepResult.fail(problem.get()));
        phases.add(await("simulierte Funke kommt nicht an", () -> TransmitterInput.INSTANCE.raw() != null));
        phases.add(fly(DroneBuild.FREESTYLE));
        phases.add(flying());
        phases.add(wait(10));
        phases.add((c, w) -> {
            c.state().put("changes", ExampleSwitches.GHOST_CHANGES.get());
            c.state().put("packets", AddonSwitchClient.switchPackets());
            if (PropwashClientApi.input().switchActive(ExampleSwitches.GHOST)) {
                return StepResult.fail("Geist vor dem ersten Druck an");
            }
            return null;
        });
        for (int press = 1; press <= PRESSES; press++) {
            boolean expected = press % 2 == 1;
            int number = press;
            phases.add((c, w) -> {
                radio.setButton(PAD_GHOST, true);
                radio.setButton(PAD_GHOST, false);
                radio.setButton(PAD_GHOST, true);
                return null;
            });
            phases.add(wait(6));
            phases.add(run(() -> radio.setButton(PAD_GHOST, false)));
            phases.add(wait(6));
            phases.add(await(number + ". Druck: Geist nicht " + (expected ? "an" : "aus"), () -> PropwashClientApi.input().switchActive(ExampleSwitches.GHOST) == expected));
            phases.add(server(number + ". Druck am Server", server -> {
                QuadcopterEntity drone = AddonSwitchSelfTests.serverDrone(server);
                if (drone == null || ExampleSwitches.ghost().active(drone) != expected) {
                    throw new IllegalStateException("Server sieht den Geist nach dem " + number + ". Druck " + (drone == null ? "ohne Drohne"
                            : ExampleSwitches.ghost().active(drone) ? "an" : "aus"));
                }
            }));
            phases.add((c, w) -> {
                int changes = ExampleSwitches.GHOST_CHANGES.get() - (int) c.state().get("changes");
                if (changes != number) {
                    return w > 40 ? StepResult.fail(changes + " ADDON_SWITCH_CHANGED nach " + number + " Drücken") : StepResult.pending();
                }
                return null;
            });
        }
        phases.add(wait(20));
        phases.add((c, w) -> {
            int changes = ExampleSwitches.GHOST_CHANGES.get() - (int) c.state().get("changes");
            if (changes != PRESSES) {
                return StepResult.fail(changes + " ADDON_SWITCH_CHANGED statt " + PRESSES);
            }
            if (PropwashClientApi.input().switchActive(HOLD)) {
                return StepResult.fail("Taster ohne Druck aktiv");
            }
            radio.setButton(PAD_HOLD, true);
            return null;
        });
        phases.add(await("Taster gehalten wirkt nicht", () -> PropwashClientApi.input().switchActive(HOLD)));
        phases.add(wait(10));
        phases.add((c, w) -> {
            if (!PropwashClientApi.input().switchActive(HOLD)) {
                return StepResult.fail("Taster wirkt nicht während er gehalten wird");
            }
            radio.setButton(PAD_HOLD, false);
            return null;
        });
        phases.add(await("Taster bleibt nach dem Loslassen aktiv", () -> !PropwashClientApi.input().switchActive(HOLD)));
        phases.add(wait(6));
        phases.add((c, w) -> {
            if (PropwashClientApi.input().switchActive(HOLD)) {
                return StepResult.fail("Taster rastet auf dem Gamepad ein");
            }
            if (!PropwashClientApi.input().switchActive(ExampleSwitches.GHOST)) {
                return StepResult.fail("Taster schaltet den Geist mit");
            }
            c.log(String.format(Locale.ROOT, "Gamepad-Profil: %d Drücke mit Prellen → Geist an/aus/an, %d ADDON_SWITCH_CHANGED, %d Schalter-Pakete, "
                            + "Taster nur gehalten aktiv", PRESSES, ExampleSwitches.GHOST_CHANGES.get() - (int) c.state().get("changes"),
                    AddonSwitchClient.switchPackets() - (int) c.state().get("packets")));
            return null;
        });
        phases.add(land());
        phases.add(landed());
        phases.add(run(() -> {
            removeRadio();
            removeHoldSwitch();
            AccessoryHintClient.mute(false);
        }));
        return script(ctx, phases);
    }
}
