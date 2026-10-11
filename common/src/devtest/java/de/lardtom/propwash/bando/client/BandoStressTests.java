package de.lardtom.propwash.bando.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.bando.BandoModule;
import de.lardtom.propwash.bando.plan.BandoFamily;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class BandoStressTests {
    public static final String ENV = "PROPWASH_BANDO_STRESS";
    private static final String WORLD = "propwash_bando_stress";
    private static final String PACK = "propwash_stress";
    private static final int RADIUS = 7;
    private static final int VISIT_TICKS = 60;

    private BandoStressTests() {
    }

    static void register() {
        String mode = System.getenv(ENV);
        if (mode == null || mode.isBlank()) {
            return;
        }
        boolean bandos = !mode.trim().equalsIgnoreCase("off");
        String label = bandos ? "mit Bandos" : "ohne Bandos (Datenpaket)";
        SelfTest.afterLeaveStep("bandostress: Welt mit Bauwerken anlegen, " + label, 2400, ctx -> BandoWorldTests.createWorld(ctx, WORLD, true));
        SelfTest.afterLeaveStep("bandostress: Datenpaket aktiv, " + label, 2400, ctx -> packStep(ctx, bandos));
        SelfTest.afterLeaveStep("bandostress: dieselben Chunks um jeden Bando-Platz erzeugen und besuchen, " + label, 20 * 60 * 10,
                BandoStressTests::generateStep);
        SelfTest.afterLeaveStep("bandostress: Welt verlassen", 1200, BandoWorldTests::leaveStep);
    }

    private static TagKey<Biome> tag(BandoFamily family) {
        return TagKey.create(Registries.BIOME, Constants.id("has_structure/bando/" + family.name().toLowerCase(Locale.ROOT)));
    }

    private static void writePack(MinecraftServer server, boolean bandos) throws IOException {
        Path root = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK);
        Files.createDirectories(root);
        String format = "[" + SharedConstants.DATA_PACK_FORMAT_MAJOR + ", " + SharedConstants.DATA_PACK_FORMAT_MINOR + "]";
        Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\": {\"description\": \"Propwash stress\", \"min_format\": " + format
                + ", \"max_format\": " + format + "}}\n", StandardCharsets.UTF_8);
        if (bandos) {
            return;
        }
        Path tags = root.resolve("data/propwash/tags/worldgen/biome/has_structure/bando");
        Files.createDirectories(tags);
        for (BandoFamily family : BandoFamily.values()) {
            Files.writeString(tags.resolve(family.name().toLowerCase(Locale.ROOT) + ".json"), "{\"replace\": true, \"values\": []}\n",
                    StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private static StepResult packStep(SelfTest.StepContext ctx, boolean bandos) {
        Map<String, Object> state = ctx.state();
        if (!state.containsKey("future")) {
            AtomicReference<String> result = new AtomicReference<>();
            state.put("result", result);
            state.put("future", ctx.onServer(server -> {
                try {
                    writePack(server, bandos);
                } catch (IOException e) {
                    result.set("Datenpaket nicht geschrieben: " + e);
                    return;
                }
                server.getPackRepository().reload();
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "datapack enable \"file/" + PACK + "\"");
                result.set("enabled");
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) state.get("future");
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Aktion fehlgeschlagen: " + future.exceptionNow());
        }
        AtomicReference<String> result = (AtomicReference<String>) state.get("result");
        String text = result.get();
        if (text != null && text.startsWith("ok")) {
            ctx.log(text.substring(2).trim());
            return StepResult.pass();
        }
        if (!"enabled".equals(text)) {
            return StepResult.fail(String.valueOf(text));
        }
        if (ctx.tick() > 2000) {
            return StepResult.fail("Datenpaket nach 100 s nicht aktiv");
        }
        if (ctx.tick() % 20 != 0) {
            return StepResult.pending();
        }
        state.put("future", ctx.onServer(server -> {
            boolean selected = server.getPackRepository().getSelectedIds().contains("file/" + PACK);
            int biomes = 0;
            for (BandoFamily family : BandoFamily.values()) {
                biomes += server.registryAccess().lookupOrThrow(Registries.BIOME).get(tag(family)).map(set -> set.size()).orElse(0);
            }
            if (!selected || (bandos ? biomes == 0 : biomes != 0)) {
                return;
            }
            ServerLevel level = server.overworld();
            BlockPos found = level.findNearestMapStructure(BandoModule.TAG, level.getRespawnData().pos(), 48, false);
            result.set(bandos == (found != null) ? "ok Datenpaket aktiv, " + biomes + " Biome mit Bandos, /locate " + found
                    : "Bandos " + (bandos ? "fehlen" : "trotz Datenpaket") + ": /locate " + found);
        }));
        return StepResult.pending();
    }

    @SuppressWarnings("unchecked")
    private static StepResult generateStep(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        List<ChunkPos> sites = (List<ChunkPos>) state.computeIfAbsent("sites", key -> {
            List<ChunkPos> list = new ArrayList<>();
            for (List<ChunkPos> found : BandoSelfTests.FOUND.get().values()) {
                list.add(found.getFirst());
            }
            return list;
        });
        if (sites.size() < BandoFamily.values().length) {
            return StepResult.fail("Testgenerator hat nicht alle Familien gefunden (Schritt 'bando: Weltgenerator …' fehlt?)");
        }
        int index = (int) state.getOrDefault("index", 0);
        if (index >= sites.size()) {
            ctx.log(String.format(Locale.ROOT, "%d Gebiete mit je %d Chunks erzeugt und besucht in %.1f s", sites.size(), (2 * RADIUS + 1) * (2 * RADIUS + 1),
                    (System.nanoTime() - (long) state.get("start")) / 1.0E9));
            return StepResult.pass();
        }
        state.putIfAbsent("start", System.nanoTime());
        List<CompletableFuture<?>> futures = (List<CompletableFuture<?>>) state.get("futures");
        if (futures == null) {
            ChunkPos center = sites.get(index);
            List<CompletableFuture<?>> requested = new ArrayList<>();
            state.put("futures", requested);
            state.put("request", ctx.onServer(server -> {
                ServerLevel level = server.overworld();
                for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                    for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                        synchronized (requested) {
                            requested.add(level.getChunkSource().getChunkFuture(center.x() + dx, center.z() + dz, ChunkStatus.FULL, true));
                        }
                    }
                }
            }));
            state.remove("visit");
            return StepResult.pending();
        }
        CompletableFuture<?> request = (CompletableFuture<?>) state.get("request");
        if (!request.isDone()) {
            return StepResult.pending();
        }
        if (request.isCompletedExceptionally()) {
            return StepResult.fail("Chunks nicht angefordert: " + request.exceptionNow());
        }
        synchronized (futures) {
            for (CompletableFuture<?> future : futures) {
                if (!future.isDone()) {
                    return StepResult.pending();
                }
            }
        }
        if (!state.containsKey("visit")) {
            ChunkPos center = sites.get(index);
            state.put("visit", ctx.tick());
            ctx.onServer(server -> {
                ServerLevel level = server.overworld();
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                int x = center.getMiddleBlockX();
                int z = center.getMiddleBlockZ();
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 12;
                player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), 0.0F, 30.0F, true);
            });
            return StepResult.pending();
        }
        if (ctx.tick() - (int) state.get("visit") < VISIT_TICKS) {
            return StepResult.pending();
        }
        state.put("index", index + 1);
        state.remove("futures");
        return StepResult.pending();
    }
}
