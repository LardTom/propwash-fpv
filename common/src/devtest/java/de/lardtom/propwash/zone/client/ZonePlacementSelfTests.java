package de.lardtom.propwash.zone.client;

import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.network.PropwashNet;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.WorldRulesS2C;
import de.lardtom.propwash.server.client.ClientWorldRules;
import de.lardtom.propwash.ui.SettingsSearch;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.search.SearchBar;
import de.lardtom.propwash.ui.client.tab.OverviewTab;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.zone.ZoneServer;
import de.lardtom.propwash.zone.ZonesS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ZonePlacementSelfTests {
    static final String SEARCH = ZoneSelfTests.PREFIX + "Suche";
    private static final String PHASE = "phase";
    private static final String MARK = "mark";
    private static final String FUTURE = "future";
    private static final String SCALE = "scale";
    private static final int TOP_HITS = 5;
    private static final int NORMAL_SCALE = 2;
    private static final AtomicBoolean CREATED = new AtomicBoolean();

    private ZonePlacementSelfTests() {
    }

    static void register() {
        SelfTest.clientStep("zonen: Tab Zonen – Überschrift und „Neue Zone hier“ ohne Scrollen sichtbar, bei GUI-Skala 2 und der größten; Suche „zone“ führt hin; "
                + "ohne Operator-Recht keine fremden Zonen und nur lesbar ohne Knöpfe", 900, ZonePlacementSelfTests::step);
    }

    private static int phase(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(PHASE, 0);
    }

    private static void next(SelfTest.StepContext ctx) {
        ctx.state().put(PHASE, phase(ctx) + 1);
        ctx.state().put(MARK, ctx.tick());
    }

    private static int since(SelfTest.StepContext ctx) {
        return ctx.tick() - (int) ctx.state().getOrDefault(MARK, 0);
    }

    private static @Nullable ZonesTab tab() {
        if (!(Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen)) {
            return null;
        }
        ConfigTab active = screen.activeTab();
        return active instanceof ZonesTab tab ? tab : null;
    }

    private static StepResult end(SelfTest.StepContext ctx, StepResult result) {
        Minecraft mc = Minecraft.getInstance();
        Object saved = ctx.state().remove(SCALE);
        if (saved instanceof Integer scale && mc.options.guiScale().get() != scale) {
            mc.options.guiScale().set(scale);
            mc.resizeGui();
        }
        if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
        return result;
    }

    private static @Nullable String placement(ZonesTab tab, String where) {
        FlatButton button = tab.zones().newButton();
        if (button == null) {
            return where + ": kein Knopf „" + Component.translatable(ZoneSection.KEY + "new").getString() + "“ trotz Rechten";
        }
        if (!tab.atTop()) {
            return where + ": Tab ist beim Öffnen nicht ganz oben";
        }
        if (tab.zonesTop() < tab.viewTop() || button.getY() < tab.viewTop() || button.getY() + button.getHeight() > tab.clipBottom()) {
            return where + ": Zonen ohne Scrollen nicht sichtbar – Abschnitt y " + tab.zonesTop() + ", Knopf y " + button.getY() + "–"
                    + (button.getY() + button.getHeight()) + ", sichtbar " + tab.viewTop() + "–" + tab.clipBottom();
        }
        return null;
    }

    private static @Nullable String hit(SearchBar search, String query, String label) {
        search.type(query);
        List<SettingsSearch.Hit> hits = search.hits();
        StringBuilder seen = new StringBuilder();
        for (int i = 0; i < Math.min(TOP_HITS, hits.size()); i++) {
            SettingsSearch.Entry entry = hits.get(i).entry();
            seen.append(" | ").append(entry.tabId()).append(": ").append(entry.label());
            if (ZonesTab.ID.equals(entry.tabId()) && entry.label().equals(label)) {
                return null;
            }
        }
        return "Suche „" + query + "“ findet „" + label + "“ nicht unter den ersten " + TOP_HITS + " Treffern:" + seen;
    }

    private static int index(SearchBar search, String label) {
        List<SettingsSearch.Hit> hits = search.hits();
        for (int i = 0; i < hits.size(); i++) {
            SettingsSearch.Entry entry = hits.get(i).entry();
            if (ZonesTab.ID.equals(entry.tabId()) && entry.label().equals(label)) {
                return i;
            }
        }
        return -1;
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        String newLabel = Component.translatable(ZoneSection.KEY + "new").getString();
        switch (phase(ctx)) {
            case 0 -> {
                CREATED.set(false);
                String quoted = "\"" + SEARCH + "\"";
                state.put(FUTURE, ctx.onServer(server -> {
                    ZoneSelfTests.command(server, "propwash zone delete " + quoted);
                    CREATED.set(ZoneSelfTests.command(server, "propwash zone create " + quoted + " box ~-70 ~-1 ~-70 ~-60 ~5 ~-60") > 0);
                }));
                next(ctx);
                return StepResult.pending();
            }
            case 1 -> {
                CompletableFuture<?> future = (CompletableFuture<?>) state.get(FUTURE);
                if (future != null && !future.isDone()) {
                    return since(ctx) > 200 ? StepResult.fail("Server legt die Testzone nicht an") : StepResult.pending();
                }
                if (!CREATED.get()) {
                    return StepResult.fail("Befehl /propwash zone create abgelehnt");
                }
                if (ZoneSelfTests.client(SEARCH) == null) {
                    return since(ctx) > 200 ? StepResult.fail("Testzone kommt am Client nicht an") : StepResult.pending();
                }
                state.put(SCALE, mc.options.guiScale().get());
                mc.options.guiScale().set(NORMAL_SCALE);
                mc.resizeGui();
                ConfigTabs.open(null, ZonesTab.ID);
                next(ctx);
                return StepResult.pending();
            }
            case 2, 4 -> {
                ZonesTab tab = tab();
                boolean listed = tab != null && tab.zones().entries().stream().anyMatch(entry -> SEARCH.equals(entry.zone().name()));
                if (tab == null || !listed || since(ctx) < 3) {
                    return since(ctx) > 200 ? end(ctx, StepResult.fail("Tab Zonen zeigt die Testzone nicht")) : StepResult.pending();
                }
                Window window = mc.getWindow();
                String where = "GUI-Skala " + window.getGuiScale() + " (" + window.getGuiScaledWidth() + "×" + window.getGuiScaledHeight() + ")";
                String problem = placement(tab, where);
                if (problem != null) {
                    return end(ctx, StepResult.fail(problem));
                }
                ctx.log("Zonen oben sichtbar bei " + where + ": Überschrift y " + tab.zonesTop() + ", Knopf bis y "
                        + (tab.zones().newButton().getY() + tab.zones().newButton().getHeight()) + ", Ende der Ansicht " + tab.clipBottom());
                if (phase(ctx) == 2) {
                    mc.options.guiScale().set(0);
                    mc.resizeGui();
                } else {
                    mc.options.guiScale().set((Integer) state.get(SCALE));
                    mc.resizeGui();
                }
                next(ctx);
                return StepResult.pending();
            }
            case 3 -> {
                if (mc.getWindow().getGuiScaledHeight() > 270) {
                    return end(ctx, StepResult.fail("größte GUI-Skala nicht erreicht: GUI " + mc.getWindow().getGuiScaledWidth() + "×"
                            + mc.getWindow().getGuiScaledHeight()));
                }
                state.put(PHASE, 4);
                return StepResult.pending();
            }
            case 5 -> {
                if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                    return StepResult.fail("Konfigurator geschlossen");
                }
                if (since(ctx) < 3) {
                    return StepResult.pending();
                }
                SearchBar search = screen.search();
                if (search == null) {
                    return end(ctx, StepResult.fail("Konfigurator ohne Suchfeld"));
                }
                search.focus();
                if (search.index() == null) {
                    return since(ctx) > 300 ? end(ctx, StepResult.fail("Suchindex fehlt")) : StepResult.pending();
                }
                String editLabel = Component.translatable(ZoneSection.KEY + "edit_search", SEARCH).getString();
                for (String[] query : new String[][]{{"zone", newLabel}, {"Zone", newLabel}, {"zones", newLabel},
                        {SEARCH.toLowerCase(Locale.ROOT), editLabel}}) {
                    String problem = hit(search, query[0], query[1]);
                    if (problem != null) {
                        return end(ctx, StepResult.fail(problem));
                    }
                }
                search.type("");
                screen.selectTab(OverviewTab.ID);
                search.focus();
                search.type("zone");
                int pick = index(search, newLabel);
                if (pick < 0) {
                    return end(ctx, StepResult.fail("Treffer „" + newLabel + "“ fehlt nach Tab-Wechsel"));
                }
                search.pick(pick);
                next(ctx);
                return StepResult.pending();
            }
            case 6 -> {
                if (since(ctx) < 3) {
                    return StepResult.pending();
                }
                if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                    return StepResult.fail("Konfigurator nach dem Suchtreffer geschlossen");
                }
                ZonesTab tab = tab();
                if (tab == null) {
                    return end(ctx, StepResult.fail("Suchtreffer „" + newLabel + "“ öffnet nicht den Tab Zonen"));
                }
                FlatButton button = tab.zones().newButton();
                if (button == null || screen.getFocused() != button) {
                    return end(ctx, StepResult.fail("Suchtreffer fokussiert nicht den Knopf „" + newLabel + "“: " + screen.getFocused()));
                }
                if (button.getY() < tab.viewTop() || button.getY() + button.getHeight() > tab.clipBottom()) {
                    return end(ctx, StepResult.fail("Knopf nach dem Suchtreffer nicht sichtbar: y " + button.getY()));
                }
                List<WorldRulesS2C.Entry> entries = new ArrayList<>();
                ClientWorldRules.INSTANCE.raw().forEach((id, value) -> entries.add(new WorldRulesS2C.Entry(id, value)));
                ClientWorldRules.INSTANCE.accept(new WorldRulesS2C(false, entries));
                ClientZones.INSTANCE.accept(new ZonesS2C(false, false, ClientZones.INSTANCE.syncedDimension(), ClientZones.INSTANCE.stored(),
                        ClientZones.INSTANCE.release()));
                mc.options.guiScale().set(NORMAL_SCALE);
                mc.resizeGui();
                next(ctx);
                return StepResult.pending();
            }
            case 7, 8 -> {
                ZonesTab tab = tab();
                if (tab == null || !tab.readOnly() || since(ctx) < 3) {
                    return since(ctx) > 200 ? restore(ctx, "Tab Zonen wird nicht nur lesbar") : StepResult.pending();
                }
                Window window = mc.getWindow();
                String where = "nur lesbar, GUI-Skala " + window.getGuiScale() + " (" + window.getGuiScaledWidth() + "×" + window.getGuiScaledHeight() + ")";
                String problem = readOnly(tab, where);
                if (problem != null) {
                    return restore(ctx, problem);
                }
                ctx.log("Zonen oben sichtbar " + where + ": Überschrift y " + tab.zonesTop() + ", Ende der Ansicht " + tab.clipBottom()
                        + ", fremde Testzone nicht gelistet");
                if (phase(ctx) == 7) {
                    mc.options.guiScale().set(0);
                    mc.resizeGui();
                } else {
                    restore(ctx, null);
                }
                next(ctx);
                return StepResult.pending();
            }
            default -> {
                if (!ClientWorldRules.INSTANCE.editable() || !ClientZones.INSTANCE.editable()) {
                    return since(ctx) > 200 ? end(ctx, StepResult.fail("Operator-Rechte kommen nicht zurück")) : StepResult.pending();
                }
                return end(ctx, StepResult.pass());
            }
        }
    }

    private static StepResult restore(SelfTest.StepContext ctx, @Nullable String problem) {
        ctx.onServer(server -> {
            ServerPlayer player = ZoneSelfTests.player(server);
            PropwashNet.sendToPlayer(player, WorldRules.snapshotFor(player));
            PropwashNet.sendToPlayer(player, ZoneServer.snapshotFor(player));
        });
        return problem == null ? StepResult.pending() : end(ctx, StepResult.fail(problem));
    }

    private static @Nullable String readOnly(ZonesTab tab, String where) {
        if (tab.zones().newButton() != null) {
            return where + ": Knopf „Neue Zone hier“ sichtbar";
        }
        for (ZoneSection.Entry entry : tab.zones().entries()) {
            if (!entry.zone().ownedBy(ClientZones.me())) {
                return where + ": fremde Zone „" + entry.zone().name() + "“ ohne Operator-Recht gelistet";
            }
            if (entry.edit() != null || entry.delete() != null) {
                return where + ": Knopf „Bearbeiten“ oder „Löschen“ bei " + entry.zone().name();
            }
        }
        int bottom = tab.zonesTop() + Metrics.SECTION_HEIGHT;
        if (!tab.atTop() || tab.zonesTop() < tab.viewTop() || bottom > tab.clipBottom()) {
            return where + ": Überschrift Zonen ohne Scrollen nicht sichtbar (y " + tab.zonesTop() + ", unten " + bottom + ", sichtbar " + tab.viewTop()
                    + "–" + tab.clipBottom() + ")";
        }
        return null;
    }
}
