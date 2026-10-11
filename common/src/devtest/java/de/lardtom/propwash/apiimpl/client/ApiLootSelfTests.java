package de.lardtom.propwash.apiimpl.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAddon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class ApiLootSelfTests {
    private static final String RESULT = "result";
    private static final String FUTURE = "future";
    private static final int ROLLS = 200;
    private static final double MIN_RATE = 0.3;
    private static final double MAX_RATE = 0.7;

    private ApiLootSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("api: Addon-Beute über PropwashApi.loot() in Vanilla-Truhen", 400, ApiLootSelfTests::lootStep);
    }

    @SuppressWarnings("unchecked")
    private static StepResult lootStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            AtomicReference<List<String>> result = new AtomicReference<>();
            ctx.state().put(RESULT, result);
            ctx.state().put(FUTURE, ctx.onServer(server -> result.set(check(ctx, server))));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Prüfung fehlgeschlagen: " + future.exceptionNow());
        }
        List<String> problems = ((AtomicReference<List<String>>) ctx.state().get(RESULT)).get();
        return StepResult.check(problems.isEmpty(), String.join("; ", problems));
    }

    private static List<String> check(SelfTest.StepContext ctx, MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        if (!PropwashApi.loot().tablesFor(ExampleAddon.LOOT_TARGET).equals(List.of(ExampleAddon.LOOT_TABLE))) {
            problems.add("Einspeisung nicht angemeldet: " + PropwashApi.loot().tablesFor(ExampleAddon.LOOT_TARGET));
        }
        try {
            PropwashApi.loot().inject(ExampleAddon.LOOT_TARGET, ExampleAddon.LOOT_TABLE);
            problems.add("späte Anmeldung nach dem Einfrieren nicht abgelehnt");
        } catch (IllegalStateException expected) {
            ctx.log("späte Anmeldung abgelehnt: " + expected.getMessage());
        } catch (IllegalArgumentException wrong) {
            problems.add("späte Anmeldung nicht als zu spät abgelehnt: " + wrong.getMessage());
        }
        LootTable own = server.reloadableRegistries().getLootTable(ExampleAddon.LOOT_TABLE);
        LootTable target = server.reloadableRegistries().getLootTable(ExampleAddon.LOOT_TARGET);
        if (own == LootTable.EMPTY || target == LootTable.EMPTY) {
            problems.add("Beutetabelle fehlt: " + (own == LootTable.EMPTY ? ExampleAddon.LOOT_TABLE : ExampleAddon.LOOT_TARGET).identifier());
            return problems;
        }
        LootParams params = new LootParams.Builder(server.overworld()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
        Identifier motor = ExampleAddon.MOTOR_2806;
        int finds = 0;
        for (int seed = 1; seed <= ROLLS; seed++) {
            boolean found = false;
            for (ItemStack stack : target.getRandomItems(params, seed)) {
                Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (ExampleAddon.NAMESPACE.equals(id.getNamespace())) {
                    if (!id.equals(motor)) {
                        problems.add("fremde Beispiel-Beute " + id);
                    }
                    found = true;
                }
            }
            if (found) {
                finds++;
            }
        }
        double rate = finds / (double) ROLLS;
        ctx.log(String.format(Locale.ROOT, "%s: Beispiel-Motor in %d von %d Truhen (%.0f %%)", ExampleAddon.LOOT_TARGET.identifier(), finds,
                ROLLS, rate * 100.0));
        if (rate < MIN_RATE || rate > MAX_RATE) {
            problems.add(String.format(Locale.ROOT, "Fundrate %.1f %% außerhalb %.0f–%.0f %% (genau eine Einspeisung erwartet)", rate * 100.0,
                    MIN_RATE * 100.0, MAX_RATE * 100.0));
        }
        return problems;
    }
}
