package de.lardtom.propwash.share.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class CovShareScript {
    static final String SERVER = "cov_server";
    private static final String INDEX = "cov_index";
    private static final String START = "cov_start";
    private static final String WHY = "cov_why";
    private static final String FUTURE = "cov_server_future";
    private static final String ERROR = "error";

    interface Body {
        StepResult run(SelfTest.StepContext ctx, Map<String, Object> state, int age);
    }

    private record Stage(String name, int limit, Body body) {
    }

    private final List<Stage> stages = new ArrayList<>();
    private Consumer<SelfTest.StepContext> cleanup = ctx -> {
    };

    static CovShareScript create() {
        return new CovShareScript();
    }

    CovShareScript then(String name, int limit, Body body) {
        this.stages.add(new Stage(name, limit, body));
        return this;
    }

    CovShareScript server(String name, BiConsumer<MinecraftServer, Map<String, Object>> action) {
        return this.then(name, 300, (ctx, state, age) -> {
            @SuppressWarnings("unchecked")
            CompletableFuture<Void> future = (CompletableFuture<Void>) state.get(FUTURE);
            Map<String, Object> shared = shared(state);
            if (future == null) {
                shared.remove(ERROR);
                state.put(FUTURE, ctx.onServer(server -> {
                    try {
                        action.accept(server, shared);
                    } catch (RuntimeException e) {
                        shared.put(ERROR, e.toString());
                    }
                }));
                return wait(state, "Server antwortet nicht");
            }
            if (!future.isDone()) {
                return wait(state, "Server antwortet nicht");
            }
            state.remove(FUTURE);
            Object error = shared.get(ERROR);
            return error == null ? StepResult.pass() : StepResult.fail(String.valueOf(error));
        });
    }

    CovShareScript pause(int ticks) {
        return this.then("warten", ticks + 20, (ctx, state, age) -> age >= ticks ? StepResult.pass() : StepResult.pending());
    }

    CovShareScript cleanup(Consumer<SelfTest.StepContext> value) {
        this.cleanup = value;
        return this;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> shared(Map<String, Object> state) {
        return (Map<String, Object>) state.computeIfAbsent(SERVER, key -> new ConcurrentHashMap<String, Object>());
    }

    static StepResult wait(Map<String, Object> state, String why) {
        state.put(WHY, why);
        return StepResult.pending();
    }

    StepResult tick(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        int index = (int) state.getOrDefault(INDEX, 0);
        if (index >= this.stages.size()) {
            return StepResult.pass();
        }
        int start = (int) state.computeIfAbsent(START, key -> ctx.tick());
        Stage stage = this.stages.get(index);
        StepResult result;
        try {
            result = stage.body().run(ctx, state, ctx.tick() - start);
        } catch (RuntimeException e) {
            result = StepResult.fail(e.toString());
        }
        if (result.status() == StepResult.Status.PENDING) {
            if (ctx.tick() - start <= stage.limit()) {
                return result;
            }
            result = StepResult.fail("Zeit abgelaufen: " + state.getOrDefault(WHY, "–"));
        }
        if (result.status() == StepResult.Status.FAIL) {
            this.finish(ctx);
            return StepResult.fail(stage.name() + ": " + result.message());
        }
        state.remove(WHY);
        state.put(INDEX, index + 1);
        state.put(START, ctx.tick() + 1);
        if (index + 1 >= this.stages.size()) {
            this.finish(ctx);
            return StepResult.pass();
        }
        return StepResult.pending();
    }

    private void finish(SelfTest.StepContext ctx) {
        try {
            this.cleanup.accept(ctx);
        } catch (RuntimeException e) {
            ctx.log("Aufräumen warf: " + e);
        }
    }
}
