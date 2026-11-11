package de.lardtom.propwash.race.client;

import de.lardtom.propwash.dev.PersonalRulesDev;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.UiScaleSelfTests;
import de.lardtom.propwash.server.PersonalRulesServer;
import de.lardtom.propwash.server.client.ClientPersonalRules;
import de.lardtom.propwash.server.client.MyRulesTab;
import de.lardtom.propwash.server.client.WorldRulesTab;
import de.lardtom.propwash.server.rules.PhysicsRules;
import de.lardtom.propwash.server.rules.RuleSpec;
import de.lardtom.propwash.server.rules.RuleTier;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class PersonalRulesUiScaleCase implements UiScaleSelfTests.Case {
    public static final String NAME = "Konfigurator mit persönlichen Regeln";
    private static final int OPEN_TICKS = 300;
    private static final List<String> TABS = List.of(WorldRulesTab.ID, MyRulesTab.ID, "race");
    private static final String SAVED = "personal_rules_saved";
    private static final String FUTURE = "personal_rules_future";
    private static final String RESULT = "personal_rules_result";

    public static UiScaleSelfTests.Case create() {
        return new PersonalRulesUiScaleCase();
    }

    @Override
    public String name() {
        return NAME;
    }

    private static boolean released() {
        ClientPersonalRules rules = ClientPersonalRules.INSTANCE;
        for (RuleSpec spec : PhysicsRules.SPECS) {
            if (spec != WorldRuleSpecs.WIND && rules.tier(spec) != RuleTier.PLAYERS) {
                return false;
            }
        }
        return rules.tier(WorldRuleSpecs.WIND) == RuleTier.FIXED && rules.effective(WorldRuleSpecs.GRAVITY_SCALE) == 130;
    }

    @Override
    public StepResult open(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            AtomicReference<PersonalRulesDev.@Nullable Snapshot> saved = new AtomicReference<>();
            ctx.state().put(SAVED, saved);
            ctx.onServer(server -> saved.set(setUp(server)));
            return StepResult.pending();
        }
        if (!released()) {
            return ctx.tick() > OPEN_TICKS ? StepResult.fail("Freigaben kommen nicht an: " + ClientPersonalRules.INSTANCE.tiers()) : StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof ConfiguratorScreen)) {
            if (!ctx.state().containsKey("personal_rules_opened")) {
                ctx.state().put("personal_rules_opened", ctx.tick());
                ConfigTabs.open(null, WorldRulesTab.ID);
            }
            return ctx.tick() > OPEN_TICKS ? StepResult.fail("K-Menü öffnet nicht") : StepResult.pending();
        }
        return ctx.tick() - (int) ctx.state().getOrDefault("personal_rules_opened", 0) >= 3 ? StepResult.pass() : StepResult.pending();
    }

    static PersonalRulesDev.Snapshot setUp(MinecraftServer server) {
        PersonalRulesDev.Snapshot before = PersonalRulesDev.snapshot(server);
        PersonalRulesDev.clear(server);
        for (RuleSpec spec : PhysicsRules.SPECS) {
            PersonalRulesServer.setTier(server, spec, RuleTier.PLAYERS, null, "Selbsttest");
        }
        PersonalRulesDev.personal(server, server.getPlayerList().getPlayers().getFirst().getUUID(),
                Map.of(WorldRuleSpecs.GRAVITY_SCALE, 130, WorldRuleSpecs.BATTERY_SCALE, 0, WorldRuleSpecs.WIND, 0));
        PersonalRulesServer.setTier(server, WorldRuleSpecs.WIND, RuleTier.FIXED, null, "Selbsttest");
        return before;
    }

    static void tearDown(MinecraftServer server, PersonalRulesDev.@Nullable Snapshot before) {
        if (before == null) {
            PersonalRulesDev.clear(server);
            return;
        }
        PersonalRulesDev.restore(server, before);
    }

    @Override
    public int pages(Screen screen) {
        return TABS.size();
    }

    @Override
    public String page(Screen screen, int index) {
        String id = TABS.get(index);
        if (screen instanceof ConfiguratorScreen configurator) {
            configurator.selectTab(id);
            if ("race".equals(id)) {
                AssistCheckSelfTests.scrollToCheck();
            }
        }
        return id;
    }

    @Override
    public void close(SelfTest.StepContext ctx) {
        if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen screen) {
            screen.discardAndClose();
        }
        @SuppressWarnings("unchecked")
        AtomicReference<PersonalRulesDev.@Nullable Snapshot> saved = (AtomicReference<PersonalRulesDev.@Nullable Snapshot>) ctx.state().get(SAVED);
        if (saved != null) {
            ctx.onServer(server -> tearDown(server, saved.get()));
        }
    }

    static StepResult restoreStep(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null) {
            AtomicReference<@Nullable String> result = new AtomicReference<>();
            ctx.state().put(RESULT, result);
            ctx.state().put(FUTURE, ctx.onServer(server -> result.set(restoreCheck(server))));
            return StepResult.pending();
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Schritt fehlgeschlagen: " + future.exceptionNow());
        }
        @SuppressWarnings("unchecked")
        String problem = ((AtomicReference<@Nullable String>) ctx.state().get(RESULT)).get();
        return problem == null ? StepResult.pass() : StepResult.fail(problem);
    }

    private static @Nullable String restoreCheck(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        PersonalRulesDev.clear(server);
        PersonalRulesDev.personal(server, player.getUUID(), Map.of(WorldRuleSpecs.GRAVITY_SCALE, 50));
        PersonalRulesServer.setTier(server, WorldRuleSpecs.PROP_WASH, RuleTier.FIXED, null, "Selbsttest");
        PersonalRulesDev.Snapshot before = PersonalRulesDev.snapshot(server);
        String tiersBefore = PersonalRulesServer.tiers(server).toString();
        try {
            PersonalRulesDev.Snapshot saved = setUp(server);
            if (!saved.equals(before)) {
                return "Aufbau merkt sich einen anderen Ausgangszustand: " + saved + " statt " + before;
            }
            if (PersonalRulesServer.tiers(server).tier(WorldRuleSpecs.WIND) != RuleTier.FIXED
                    || PersonalRulesServer.tiers(server).tier(WorldRuleSpecs.PROP_WASH) != RuleTier.PLAYERS) {
                return "Aufbau setzt die Freigaben nicht: " + PersonalRulesServer.tiers(server);
            }
            tearDown(server, saved);
            PersonalRulesDev.Snapshot after = PersonalRulesDev.snapshot(server);
            String tiersAfter = PersonalRulesServer.tiers(server).toString();
            if (!after.equals(before) || !tiersAfter.equals(tiersBefore)) {
                return "Schließen stellt den Ausgangszustand nicht her: Stufen " + tiersAfter + " statt " + tiersBefore + ", Zonen-Freigabe "
                        + after.release().explicit() + " statt " + before.release().explicit() + ", Liste Spieler " + after.playerRules() + " statt "
                        + before.playerRules() + ", Werte " + after.players() + " statt " + before.players();
            }
            return null;
        } finally {
            PersonalRulesDev.clear(server);
            PersonalRulesServer.setTier(server, WorldRuleSpecs.PROP_WASH, RuleTier.ZONES, null, "Selbsttest");
            PersonalRulesServer.setTier(server, WorldRuleSpecs.WIND, RuleTier.ZONES, null, "Selbsttest");
        }
    }
}
