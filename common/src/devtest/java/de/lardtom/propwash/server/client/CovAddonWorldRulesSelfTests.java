package de.lardtom.propwash.server.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.dev.example.ExampleRules;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.ValueSlider;
import de.lardtom.propwash.zone.ZoneDraft;
import de.lardtom.propwash.zone.client.ZoneEditScreen;
import de.lardtom.propwash.zone.client.ZoneEditor;
import de.lardtom.propwash.zone.client.ZonesTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class CovAddonWorldRulesSelfTests {
    public static final String GROUP = AddonWorldRulesSelfTests.GROUP + "Abdeckung: ";
    private static final String PREFIX = "gamerule " + ExampleAddon.NAMESPACE + ":";
    private static final int VALUE = 12;
    private static final int WAIT_TICKS = 200;
    private static final String INDEX = "cov_b5_index";
    private static final String SINCE = "cov_b5_since";
    private static final String FUTURE = "cov_b5_future";

    private CovAddonWorldRulesSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "/gamerule " + ExampleAddon.NAMESPACE + ": vervollständigt beide Beispiel-Regeln, Rückmeldung im Chat, neuer Wert im Tab",
                1200, CovAddonWorldRulesSelfTests::commandStep);
        SelfTest.clientStep(GROUP + "Welt-Regeln → Tab Zonen → Neue Zone zeigt keine Beispiel-Regel", 900, CovAddonWorldRulesSelfTests::zoneStep);
    }

    private interface Phase {
        @Nullable StepResult run(SelfTest.StepContext ctx, int waited);
    }

    private static StepResult script(SelfTest.StepContext ctx, List<Phase> phases) {
        if (!ExampleRules.registered()) {
            ctx.log("Beispiel-Add-on nicht geladen – übersprungen");
            return StepResult.pass();
        }
        Map<String, Object> state = ctx.state();
        int index = (int) state.getOrDefault(INDEX, 0);
        state.putIfAbsent(SINCE, ctx.tick());
        while (index < phases.size()) {
            StepResult result;
            try {
                result = phases.get(index).run(ctx, ctx.tick() - (int) state.get(SINCE));
            } catch (RuntimeException e) {
                result = StepResult.fail(e.getMessage() != null ? e.getMessage() : e.toString());
            }
            if (result == null) {
                index++;
                state.put(INDEX, index);
                state.put(SINCE, ctx.tick());
                continue;
            }
            if (result.status() == StepResult.Status.FAIL) {
                ctx.onServer(AddonWorldRulesSelfTests::reset);
                Minecraft.getInstance().execute(CovAddonWorldRulesSelfTests::closeAll);
            }
            return result;
        }
        return StepResult.pass();
    }

    private static Phase server(String what, Consumer<MinecraftServer> action) {
        return (ctx, waited) -> {
            CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
            if (future == null) {
                ctx.state().put(FUTURE, ctx.onServer(action));
                return StepResult.pending();
            }
            if (!future.isDone()) {
                return StepResult.pending();
            }
            ctx.state().remove(FUTURE);
            if (future.isCompletedExceptionally()) {
                Throwable cause = future.exceptionNow();
                return StepResult.fail(what + ": " + (cause.getMessage() != null ? cause.getMessage() : cause.toString()));
            }
            return null;
        };
    }

    private static Phase await(String what, BooleanSupplier condition) {
        return (ctx, waited) -> condition.getAsBoolean() ? null : waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail(what);
    }

    private static Phase check(AtomicReference<String> problem) {
        return (ctx, waited) -> problem.get() == null ? null : StepResult.fail(problem.get());
    }

    @SuppressWarnings("unchecked")
    private static <T> AtomicReference<T> keep(SelfTest.StepContext ctx, String key) {
        return (AtomicReference<T>) ctx.state().computeIfAbsent(key, k -> new AtomicReference<T>());
    }

    private static void closeAll() {
        Minecraft mc = Minecraft.getInstance();
        if (ZoneEditor.active()) {
            ZoneEditor.stop();
        }
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        } else if (mc.gui.screen() instanceof ZoneEditScreen) {
            mc.gui.setScreen(null);
        }
    }

    private static @Nullable WorldRulesTab openTab() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
            ConfigTabs.open(null, WorldRulesTab.ID);
            return null;
        }
        ConfigTab active = screen.activeTab();
        if (active == null || !WorldRulesTab.ID.equals(active.id())) {
            screen.selectTab(WorldRulesTab.ID);
            return null;
        }
        return active instanceof WorldRulesTab tab && !tab.rows().isEmpty() ? tab : null;
    }

    private static List<String> suggestions(Suggestions suggestions) {
        List<String> out = new ArrayList<>();
        for (Suggestion suggestion : suggestions.getList()) {
            out.add(suggestion.getText());
        }
        return out;
    }

    private static @Nullable String completionProblem(List<String> offered, String where) {
        String cooldown = ExampleRules.LAUNCHER_COOLDOWN.toString();
        String refill = ExampleRules.LAUNCHER_REFILL.toString();
        if (!offered.contains(cooldown) || !offered.contains(refill)) {
            return where + " vervollständigt „/" + PREFIX + "“ zu " + offered + " statt " + cooldown + " und " + refill;
        }
        for (String text : offered) {
            if (!text.startsWith(ExampleAddon.NAMESPACE + ":")) {
                return where + " schlägt fremde Regel " + text + " vor";
            }
        }
        return null;
    }

    private static List<String> chat(Minecraft mc) {
        try {
            Field field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            List<?> messages = (List<?>) field.get(mc.gui.hud.getChat());
            List<String> out = new ArrayList<>();
            for (Object message : new ArrayList<>(messages)) {
                if (message instanceof GuiMessage gui) {
                    out.add(gui.content().getString());
                }
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of("? " + e);
        }
    }

    private static StepResult commandStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        AtomicReference<String> problem = keep(ctx, "problem");
        AtomicReference<CompletableFuture<Suggestions>> client = keep(ctx, "client");
        AtomicReference<Integer> chatBefore = keep(ctx, "chat");
        return script(ctx, List.of(
                server("Zurücksetzen", AddonWorldRulesSelfTests::reset),
                await("Standardwerte kamen nicht am Client an", () -> ClientWorldRules.INSTANCE.raw(ExampleRules.LAUNCHER_COOLDOWN)
                        .map(Integer.toString(ExampleRules.COOLDOWN_DEFAULT)::equals).orElse(false)),
                server("Vorschläge am Server", server -> {
                    CommandSourceStack source = server.getPlayerList().getPlayers().getFirst().createCommandSourceStack();
                    ParseResults<CommandSourceStack> parse = server.getCommands().getDispatcher().parse(PREFIX, source);
                    List<String> offered = suggestions(server.getCommands().getDispatcher().getCompletionSuggestions(parse).join());
                    problem.set(completionProblem(offered, "Befehlsbaum des Servers"));
                    if (problem.get() == null) {
                        Constants.LOG.info("[Selbsttest] {}Server schlägt {} vor", GROUP, offered);
                    }
                }),
                check(problem),
                (c, waited) -> {
                    ClientPacketListener connection = mc.getConnection();
                    if (connection == null) {
                        return StepResult.fail("keine Verbindung");
                    }
                    ClientSuggestionProvider provider = connection.getSuggestionsProvider();
                    ParseResults<ClientSuggestionProvider> parse = connection.getCommands().parse(PREFIX, provider);
                    client.set(connection.getCommands().getCompletionSuggestions(parse));
                    return null;
                },
                await("Vorschläge im Chat-Feld kommen nicht", () -> client.get() != null && client.get().isDone()),
                (c, waited) -> {
                    List<String> offered = suggestions(client.get().join());
                    String failure = completionProblem(offered, "Tab-Vervollständigung des Clients");
                    if (failure != null) {
                        return StepResult.fail(failure);
                    }
                    c.log("Tab-Vervollständigung „/" + PREFIX + "“ → " + offered);
                    chatBefore.set(chat(mc).size());
                    mc.getConnection().sendCommand("gamerule " + ExampleRules.LAUNCHER_COOLDOWN + " " + VALUE);
                    return null;
                },
                await("/gamerule kam nicht am Client an", () -> ClientWorldRules.INSTANCE.raw(ExampleRules.LAUNCHER_COOLDOWN)
                        .map(Integer.toString(VALUE)::equals).orElse(false)),
                await("keine Rückmeldung im Chat", () -> {
                    List<String> lines = chat(mc);
                    for (int i = 0; i < Math.min(lines.size(), Math.max(1, lines.size() - chatBefore.get() + 2)); i++) {
                        String line = lines.get(i);
                        if (line.contains(ExampleRules.LAUNCHER_COOLDOWN.toString()) && line.contains(Integer.toString(VALUE))) {
                            ctx.log("Chat: „" + line + "“");
                            return true;
                        }
                    }
                    return false;
                }),
                (c, waited) -> {
                    WorldRulesTab tab = openTab();
                    if (tab == null) {
                        return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail("Tab Welt-Regeln öffnet nicht");
                    }
                    WorldRulesTab.Row row = tab.row(ExampleRules.LAUNCHER_COOLDOWN).orElse(null);
                    AbstractWidget widget = row == null ? null : row.widget();
                    String shown = widget instanceof ValueSlider slider ? Long.toString(Math.round(slider.value()))
                            : widget == null ? "-" : widget.getMessage().getString();
                    if (!shown.contains(Integer.toString(VALUE))) {
                        return waited < 40 ? StepResult.pending() : StepResult.fail("Tab zeigt „" + shown + "“ statt " + VALUE);
                    }
                    c.log("Tab Welt-Regeln: „" + (row == null ? "-" : row.label().getString()) + "“ = „" + shown + "“");
                    closeAll();
                    return null;
                },
                server("Zurücksetzen", AddonWorldRulesSelfTests::reset),
                await("Standardwerte kamen nicht am Client an", () -> ClientWorldRules.INSTANCE.raw(ExampleRules.LAUNCHER_COOLDOWN)
                        .map(Integer.toString(ExampleRules.COOLDOWN_DEFAULT)::equals).orElse(false))));
    }

    private static @Nullable FlatButton button(Minecraft mc, String key) {
        String label = Component.translatable(key).getString();
        if (mc.gui.screen() == null) {
            return null;
        }
        for (GuiEventListener child : mc.gui.screen().children()) {
            if (child instanceof FlatButton button && label.equals(button.getMessage().getString())) {
                return button;
            }
        }
        return null;
    }

    private static StepResult zoneStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        List<String> names = List.of(I18n.get(ExampleRules.cooldown().nameKey()), I18n.get(ExampleRules.refill().nameKey()),
                ExampleRules.LAUNCHER_COOLDOWN.toString(), ExampleRules.LAUNCHER_REFILL.toString(), ExampleRules.LAUNCHER_COOLDOWN.getPath());
        return script(ctx, List.of(
                (c, waited) -> {
                    WorldRulesTab tab = openTab();
                    if (tab == null || tab.readOnly()) {
                        return waited < WAIT_TICKS ? StepResult.pending() : StepResult.fail("Tab Welt-Regeln öffnet nicht bearbeitbar");
                    }
                    if (tab.row(ExampleRules.LAUNCHER_COOLDOWN).isEmpty()) {
                        return StepResult.fail("Tab ohne Beispiel-Regel – der Fall prüft nichts");
                    }
                    if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                        screen.selectTab(ZonesTab.ID);
                    }
                    FlatButton create = button(mc, "propwash.zone.section.new");
                    if (create == null || !create.active) {
                        return waited < WAIT_TICKS ? StepResult.pending()
                                : StepResult.fail("Knopf „" + Component.translatable("propwash.zone.section.new").getString() + "“ fehlt");
                    }
                    create.press();
                    return null;
                },
                await("Zonen-Editor öffnet nicht", () -> mc.gui.screen() instanceof ZoneEditScreen screen && screen.panel() != null),
                (c, waited) -> waited < 5 ? StepResult.pending() : null,
                (c, waited) -> {
                    ZoneEditScreen screen = (ZoneEditScreen) mc.gui.screen();
                    List<String> problems = new ArrayList<>();
                    List<LayoutAudit.Text> texts = LayoutProbe.capture(mc, screen).texts();
                    for (LayoutAudit.Text text : texts) {
                        for (String name : names) {
                            if (text.value().contains(name)) {
                                problems.add("Zonen-Editor zeigt „" + text.value() + "“");
                            }
                        }
                    }
                    ZoneEditor editor = ZoneEditor.current();
                    long rules = editor == null ? -1 : editor.model().rows().stream().filter(row -> row.kind() == ZoneDraft.Kind.RULE).count();
                    if (editor == null || rules != PhysicsRules.SPECS.size()) {
                        problems.add("Zonen-Editor mit " + rules + " Regelzeilen statt " + PhysicsRules.SPECS.size());
                    } else {
                        for (ZoneDraft.Row row : editor.model().rows()) {
                            if (row.kind() == ZoneDraft.Kind.RULE && (row.spec() == null || !PhysicsRules.SPECS.contains(row.spec()))) {
                                problems.add("fremde Regelzeile " + row.key());
                            }
                        }
                    }
                    c.log("Zonen-Editor: " + texts.size() + " Texte, " + rules + " Regelzeilen (nur Physik-Regeln), keine Beispiel-Regel");
                    closeAll();
                    return problems.isEmpty() ? null : StepResult.fail(String.join("; ", problems));
                },
                await("Zonen-Editor schließt nicht", () -> !ZoneEditor.active() && !(mc.gui.screen() instanceof ZoneEditScreen))));
    }
}
