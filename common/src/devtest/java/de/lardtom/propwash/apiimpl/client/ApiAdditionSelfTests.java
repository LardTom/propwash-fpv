package de.lardtom.propwash.apiimpl.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.registry.PartItems;
import de.lardtom.propwash.content.Batteries;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.dev.example.client.ExampleConfigTab;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ApiAdditionSelfTests {
    private static final String RESULT = "result";
    private static final String FUTURE = "future";
    private static final String COUNT = "count";

    private ApiAdditionSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("api: Seltenheit, Ladestand, Teilezustand, Lackkosten, Kennwerte und Mod-Texte (Parts-API 1.4)", 400,
                ApiAdditionSelfTests::serverStep);
        SelfTest.clientStep("api: neue Drohne über sendCreateDrone (Parts-API 1.4)", 600, ApiAdditionSelfTests::createStep);
    }

    @SuppressWarnings("unchecked")
    private static StepResult serverStep(SelfTest.StepContext ctx) {
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
        PartItems items = PropwashApi.partItems();
        Rarity motor = items.stack(ExampleAddon.MOTOR_2806).getRarity();
        if (motor != Rarity.EPIC) {
            problems.add("eigene Item.Properties schlagen die Seltenheit aus den Teiledaten nicht: " + motor);
        }
        Rarity frame = items.stack(ExampleAddon.FRAME_XCLASS).getRarity();
        if (frame != Rarity.RARE) {
            problems.add("JSON-Feld rarity nicht übernommen: " + frame);
        }
        Rarity plain = items.stack(ExampleAddon.STACK_8S).getRarity();
        if (plain != Rarity.COMMON) {
            problems.add("Teil ohne rarity nicht gewöhnlich: " + plain);
        }
        ItemStack half = items.stack(ExampleAddon.BATTERY_8S, 0.5F);
        if (Math.abs(Batteries.charge(half) - 0.5F) > 0.002F) {
            problems.add("stack(id, 0.5) lädt den Akku auf " + Batteries.charge(half));
        }
        try {
            items.stack(ExampleAddon.MOTOR_4214, 0.5F);
            problems.add("Ladestand für einen Motor nicht abgelehnt");
        } catch (IllegalArgumentException expected) {
            ctx.log("Ladestand für Nicht-Akkus abgelehnt: " + expected.getMessage());
        }
        DroneData xclass = ExampleAddon.xclass();
        ItemStack drone = PropwashApi.builds().createDrone(xclass);
        for (PartKind kind : List.of(PartKind.FRAME, PartKind.MOTOR, PartKind.PROP, PartKind.BATTERY)) {
            OptionalDouble condition = PropwashApi.builds().partCondition(drone, kind);
            if (condition.isEmpty() || condition.getAsDouble() != 1.0) {
                problems.add("neue Drohne meldet Zustand " + condition + " für " + kind);
            }
        }
        if (PropwashApi.builds().partCondition(drone, PartKind.ACCESSORY).isPresent()
                || PropwashApi.builds().partCondition(ItemStack.EMPTY, PartKind.FRAME).isPresent()) {
            problems.add("Zustand für Zubehör oder Nicht-Drohnen nicht leer");
        }
        Map<String, Integer> same = PropwashApi.builds().paintCost(drone, xclass.paint());
        Map<String, Integer> repaint = PropwashApi.builds().paintCost(drone, new PaintSpec(Map.of("frame", 0xD53F30, "motors", 0x3AA0FF)));
        if (!same.isEmpty() || repaint.isEmpty()) {
            problems.add("Lackkosten falsch: gleich " + same + ", neu " + repaint);
        } else {
            ctx.log("Lackkosten X-Class → rot/blau: " + repaint);
        }
        Optional<BuildStats> stats = PropwashApi.builds().analyze(xclass.build());
        if (stats.isEmpty()) {
            problems.add("analyze(xclass) leer");
        } else {
            BuildStats s = stats.get();
            double[] values = {s.propwashNoise(), s.fullThrottleCellVoltage(), s.rollAuthorityRadS2(), s.pitchAuthorityRadS2(),
                    s.yawAuthorityRadS2()};
            for (double value : values) {
                if (!Double.isFinite(value) || value <= 0.0) {
                    problems.add("neuer Kennwert nicht positiv: " + value);
                }
            }
            ctx.log(String.format(Locale.ROOT, "X-Class: Prop-Wash-Rauschen %.2f, Zelle bei Vollgas %.2f V, Achsen %.0f/%.0f/%.0f rad/s²",
                    s.propwashNoise(), s.fullThrottleCellVoltage(), s.rollAuthorityRadS2(), s.pitchAuthorityRadS2(), s.yawAuthorityRadS2()));
        }
        Optional<PartDefinition> definition = PropwashApi.parts().get(ExampleAddon.FRAME_XCLASS);
        if (definition.isEmpty()) {
            problems.add("X-Class-Rahmen nicht registriert");
        } else {
            PartDefinition copy = PropwashApi.parts().fromJson(PartKind.FRAME, ExampleAddon.FRAME_XCLASS,
                    PropwashApi.parts().toJson(definition.get()));
            if (!copy.equals(definition.get())) {
                problems.add("toJson/fromJson verändert den X-Class-Rahmen");
            }
        }
        Optional<String> german = PropwashApi.text("de_de", "propwash.api.import.layer.paint");
        Optional<String> english = PropwashApi.text("en_us", "propwash.api.import.layer.paint");
        if (!german.equals(Optional.of("Lackierung")) || !english.equals(Optional.of("Paint"))) {
            problems.add("PropwashApi.text liest die Sprachdateien nicht: " + german + " / " + english);
        }
        if (PropwashApi.text("de_de", "propwash.api.does_not_exist").isPresent()) {
            problems.add("unbekannter Schlüssel liefert einen Text");
        }
        return problems;
    }

    private static StepResult createStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            AtomicInteger before = new AtomicInteger(-1);
            ctx.state().put(COUNT, before);
            ctx.state().put(ApiSelfTests.SERVER, ctx.onServer(server -> before.set(count(server))));
            return StepResult.pending();
        }
        if (!ctx.state().containsKey(ApiSelfTests.IMPORT)) {
            if (!((CompletableFuture<?>) ctx.state().get(ApiSelfTests.SERVER)).isDone() || !ApiSelfTests.requestReady()) {
                return StepResult.pending();
            }
            if (mc.player == null) {
                return StepResult.fail("kein Spieler");
            }
            ctx.state().put(ApiSelfTests.IMPORT, ApiSelfTests.send(new ExampleConfigTab().createDrone()));
            return StepResult.pending();
        }
        ImportResult result = ApiSelfTests.result(ctx.state());
        if (result == null) {
            return ApiSelfTests.timeout(ctx, 400, "keine Antwort auf sendCreateDrone");
        }
        if (result.status() != ImportStatus.APPLIED) {
            return StepResult.fail("sendCreateDrone im Kreativmodus: " + result.status());
        }
        if (!ctx.state().containsKey(RESULT)) {
            AtomicInteger after = new AtomicInteger(-1);
            ctx.state().put(RESULT, after);
            ctx.state().put(FUTURE, ctx.onServer(server -> after.set(count(server))));
            return StepResult.pending();
        }
        if (!((CompletableFuture<?>) ctx.state().get(FUTURE)).isDone()) {
            return StepResult.pending();
        }
        int before = ((AtomicInteger) ctx.state().get(COUNT)).get();
        int after = ((AtomicInteger) ctx.state().get(RESULT)).get();
        ctx.log("X-Class-Drohnen im Inventar: " + before + " → " + after);
        return StepResult.check(after == before + 1, "sendCreateDrone legte keine Drohne ins Inventar (" + before + " → " + after + ")");
    }

    private static int count(MinecraftServer server) {
        ServerPlayer player = ApiSelfTests.player(server);
        int count = 0;
        for (ItemStack stack : player.getInventory()) {
            Optional<DroneData> data = PropwashApi.builds().read(stack);
            if (data.isPresent() && data.get().build().frame().equals(ExampleAddon.FRAME_XCLASS)) {
                count++;
            }
        }
        return count;
    }
}
