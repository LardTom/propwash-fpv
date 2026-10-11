package de.lardtom.propwash.bando.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Pair;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.bando.BandoModule;
import de.lardtom.propwash.bando.BandoPiece;
import de.lardtom.propwash.bando.BandoStructure;
import de.lardtom.propwash.bando.plan.BandoFamily;
import de.lardtom.propwash.bando.plan.BandoPlan;
import de.lardtom.propwash.bando.plan.BandoTransform;
import de.lardtom.propwash.bando.plan.BandoVariant;
import de.lardtom.propwash.bando.plan.BandoViews;
import de.lardtom.propwash.bando.plan.Mat;
import de.lardtom.propwash.bando.plan.Meta;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.SelfTestRunner;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.TuneMapping;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.video.client.VideoState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

final class BandoWorldTests {
    static final String WORLD_ON = SelfTestRunner.WORLD_NAME + " Bandos";
    static final String WORLD_OFF = SelfTestRunner.WORLD_NAME + " Bandos aus";
    private static final String FUTURE = "future";
    private static final String PHASE = "phase";
    private static final int AREA_RADIUS = 3;
    private static final int WARMUP_RADIUS = 5;
    private static final int LOCATE_RADIUS = 160;
    private static final double MAX_RATIO = 3.0;
    private static final List<Site> SITES = new CopyOnWriteArrayList<>();
    private static final AtomicReference<String> TIMING = new AtomicReference<>("");

    record Site(BandoFamily family, BandoVariant variant, ChunkPos start, BlockPos center, BandoPiece piece) {
    }

    private BandoWorldTests() {
    }

    static void register() {
        SelfTest.afterLeaveStep("bando: Welt ohne Bauwerke anlegen (generate-structures aus)", 2400, ctx -> createWorld(ctx, WORLD_OFF, false));
        SelfTest.afterLeaveStep("bando: ohne Bauwerke entsteht kein Bando und /locate findet nichts", 2400, BandoWorldTests::noStructuresStep);
        SelfTest.afterLeaveStep("bando: Chunk-Erzeugung ohne Bauwerke gemessen", 12000, BandoWorldTests::timingWithout);
        SelfTest.afterLeaveStep("bando: Welt ohne Bauwerke verlassen", 1200, BandoWorldTests::leaveStep);
        SelfTest.afterLeaveStep("bando: Welt mit Bauwerken anlegen", 2400, ctx -> createWorld(ctx, WORLD_ON, true));
        SelfTest.afterLeaveStep("bando: /locate structure #propwash:bandos und jede Familie", 4800, BandoWorldTests::locateStep);
        SelfTest.afterLeaveStep("bando: Chunk-Erzeugung mit Bauwerken gemessen (gleiche Chunks)", 12000, BandoWorldTests::timingWith);
        SelfTest.afterLeaveStep("bando: jede Familie in der echten Welt vollständig erzeugt", 20 * 60 * 4, new VisitTour()::tick);
        BandoPlacementTests.register();
        if (Screenshots.enabled()) {
            SelfTest.afterLeaveStep("bando: Bildschirmfotos aus der Luft und im FPV", 20 * 60 * 8, new ShotTour()::tick);
        }
        SelfTest.afterLeaveStep("bando: Welt mit Bauwerken verlassen", 1200, BandoWorldTests::leaveStep);
        BandoStressTests.register();
    }

    private static boolean pressButton(Screen screen, String text) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && text.equals(button.getMessage().getString())) {
                button.onPress(new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                return true;
            }
        }
        return false;
    }

    static StepResult createWorld(SelfTest.StepContext ctx, String name, boolean structures) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            if (mc.level != null) {
                return StepResult.fail("noch in einer Welt");
            }
            CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(new TitleScreen()));
            if (!(mc.gui.screen() instanceof CreateWorldScreen screen)) {
                return StepResult.fail("CreateWorldScreen nicht geöffnet: " + mc.gui.screen());
            }
            WorldCreationUiState ui = screen.getUiState();
            ui.setName(name);
            Holder<WorldPreset> normal = ui.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL);
            ui.setWorldType(new WorldCreationUiState.WorldTypeEntry(normal));
            ui.setSeed(Long.toString(BandoSelfTests.SEED));
            ui.setGenerateStructures(structures);
            ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            ui.getGameRules().set(GameRules.ADVANCE_TIME, false, null);
            ui.getGameRules().set(GameRules.ADVANCE_WEATHER, false, null);
            ui.getGameRules().set(GameRules.SPAWN_MOBS, false, null);
            if (!pressButton(screen, Component.translatable("selectWorld.create").getString())) {
                return StepResult.fail("Knopf 'selectWorld.create' nicht gefunden");
            }
            return StepResult.pending();
        }
        Screen screen = mc.gui.screen();
        if (screen instanceof ConfirmScreen confirm && ctx.tick() % 20 == 0) {
            pressButton(confirm, CommonComponents.GUI_YES.getString());
        }
        if (mc.player != null && mc.level != null && screen == null && mc.getSingleplayerServer() != null) {
            if (!ctx.state().containsKey("ready")) {
                ctx.state().put("ready", ctx.tick());
            }
            if (ctx.tick() - (int) ctx.state().get("ready") > 40) {
                ctx.log(name + " geladen, Strukturen " + (structures ? "an" : "aus"));
                return StepResult.pass();
            }
        }
        return StepResult.pending();
    }

    static StepResult leaveStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            PilotSession.INSTANCE.clearTestInput();
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            if (mc.level != null) {
                mc.schedule(() -> mc.disconnectFromWorld(Component.literal("Propwash-Selbsttest Bandos beendet")));
            }
            return StepResult.pending();
        }
        if (mc.level == null && mc.player == null && mc.getSingleplayerServer() == null && ctx.tick() > 5) {
            return StepResult.pass();
        }
        return StepResult.pending();
    }

    private static @Nullable StepResult serverResult(SelfTest.StepContext ctx) {
        StepResult waiting = BandoSelfTests.waitFor(ctx);
        if (waiting != null) {
            return waiting;
        }
        @SuppressWarnings("unchecked")
        AtomicReference<String> result = (AtomicReference<String>) ctx.state().get("result");
        String text = result.get();
        if (text == null || !text.startsWith("ok")) {
            return StepResult.fail(text == null ? "kein Ergebnis" : text);
        }
        ctx.log(text.substring(2).trim());
        return StepResult.pass();
    }

    private static StepResult onServer(SelfTest.StepContext ctx, java.util.function.Function<MinecraftServer, String> action) {
        if (!ctx.state().containsKey(FUTURE)) {
            if (ctx.server() == null) {
                return StepResult.fail("keine Welt");
            }
            AtomicReference<String> result = new AtomicReference<>();
            ctx.state().put("result", result);
            ctx.state().put(FUTURE, ctx.onServer(server -> result.set(action.apply(server))));
            return StepResult.pending();
        }
        StepResult done = serverResult(ctx);
        return done == null ? StepResult.pending() : done;
    }

    private static StepResult noStructuresStep(SelfTest.StepContext ctx) {
        return onServer(ctx, server -> {
            ServerLevel level = server.overworld();
            if (server.getWorldGenSettings().options().generateStructures()) {
                return "Welt erzeugt Bauwerke";
            }
            if (level.getSeed() != BandoSelfTests.SEED) {
                return "falscher Seed " + level.getSeed();
            }
            BlockPos spawn = level.getRespawnData().pos();
            if (level.findNearestMapStructure(BandoModule.TAG, spawn, 64, false) != null) {
                return "/locate findet trotz generate-structures=false einen Bando";
            }
            List<ChunkPos> starts = new ArrayList<>();
            for (List<ChunkPos> list : expected(server).values()) {
                starts.add(list.getFirst());
                if (starts.size() >= 3) {
                    break;
                }
            }
            for (ChunkPos chunk : starts) {
                ChunkAccess access = level.getChunk(chunk.x(), chunk.z(), ChunkStatus.STRUCTURE_STARTS, true);
                for (BandoFamily family : BandoFamily.values()) {
                    StructureStart start = access.getStartForStructure(BandoSelfTests.holder(server, family).value());
                    if (start != null && start.isValid()) {
                        return "Bando " + family.key() + " bei " + chunk + " trotz generate-structures=false";
                    }
                }
            }
            return "ok /locate ohne Ergebnis, " + starts.size() + " Bando-Plätze aus dem Testgenerator ohne Strukturstart";
        });
    }

    static Map<BandoFamily, List<ChunkPos>> expected(MinecraftServer server) {
        Map<BandoFamily, List<ChunkPos>> found = BandoSelfTests.FOUND.get();
        if (found.isEmpty() || found.size() < BandoFamily.values().length && BandoSelfTests.SEED == BandoSelfTests.TEST_SEED) {
            throw new IllegalStateException("Testgenerator hat nicht alle Familien gefunden (Schritt 'bando: Weltgenerator …' fehlt?)");
        }
        return found;
    }

    private static StepResult locateStep(SelfTest.StepContext ctx) {
        return onServer(ctx, server -> {
            ServerLevel level = server.overworld();
            if (!server.getWorldGenSettings().options().generateStructures() || level.getSeed() != BandoSelfTests.SEED) {
                return "Welt falsch angelegt";
            }
            BlockPos spawn = level.getRespawnData().pos();
            List<String> out = new ArrayList<>();
            CommandSource capture = new CommandSource() {
                @Override
                public void sendSystemMessage(Component message) {
                    out.add(message.getString());
                }

                @Override
                public boolean acceptsSuccess() {
                    return true;
                }

                @Override
                public boolean acceptsFailure() {
                    return true;
                }

                @Override
                public boolean shouldInformAdmins() {
                    return false;
                }
            };
            CommandSourceStack source = server.createCommandSourceStack().withSource(capture).withLevel(level).withPosition(Vec3.atCenterOf(spawn));
            server.getCommands().performPrefixedCommand(source, "locate structure #propwash:bandos");
            String command = String.join(" ", out);
            if (!command.contains("propwash:bando/")) {
                return "/locate structure #propwash:bandos: " + command;
            }
            SITES.clear();
            StringBuilder log = new StringBuilder("ok ").append(command).append(';');
            for (BandoFamily family : BandoFamily.values()) {
                Holder.Reference<Structure> holder = BandoSelfTests.holder(server, family);
                Pair<BlockPos, Holder<Structure>> result = level.getChunkSource().getGenerator()
                        .findNearestMapStructure(level, HolderSet.direct(holder), spawn, LOCATE_RADIUS, false);
                if (result == null) {
                    return family.key() + " nicht gefunden (Radius " + LOCATE_RADIUS + " Chunks)";
                }
                ChunkPos chunk = ChunkPos.containing(result.getFirst());
                ChunkAccess access = level.getChunk(chunk.x(), chunk.z(), ChunkStatus.STRUCTURE_STARTS, true);
                StructureStart start = access.getStartForStructure(holder.value());
                BandoPiece piece = start == null ? null : piece(start);
                if (piece == null) {
                    return family.key() + ": kein Bando-Start bei " + chunk;
                }
                BandoPlan plan = piece.plan();
                BandoTransform t = piece.transform();
                BlockPos center = new BlockPos(t.originX() + t.widthX() / 2, t.worldY(plan.ground()), t.originZ() + t.widthZ() / 2);
                SITES.add(new Site(family, piece.variant(), chunk, center, piece));
                log.append(' ').append(family.key()).append('/').append(piece.variant().key()).append('@').append(center.getX()).append(',')
                        .append(center.getZ()).append(String.format(Locale.ROOT, " (%.0f)", Math.sqrt(center.distSqr(spawn))));
            }
            Map<BandoVariant, ChunkPos> variants = BandoSelfTests.VARIANTS.get();
            for (BandoVariant variant : BandoVariant.values()) {
                ChunkPos chunk = variants.get(variant);
                if (chunk == null || SITES.stream().anyMatch(site -> site.variant() == variant)) {
                    continue;
                }
                BandoFamily family = variant.family();
                StructureStart start = level.getChunk(chunk.x(), chunk.z(), ChunkStatus.STRUCTURE_STARTS, true)
                        .getStartForStructure(BandoSelfTests.holder(server, family).value());
                BandoPiece piece = start == null ? null : piece(start);
                if (piece == null || piece.variant() != variant) {
                    return variant.key() + ": Testgenerator und Welt stimmen bei " + chunk + " nicht überein";
                }
                BandoTransform t = piece.transform();
                BlockPos center = new BlockPos(t.originX() + t.widthX() / 2, t.worldY(piece.plan().ground()), t.originZ() + t.widthZ() / 2);
                SITES.add(new Site(family, piece.variant(), chunk, center, piece));
                log.append(" weitere ").append(family.key()).append('/').append(piece.variant().key()).append('@').append(center.getX()).append(',')
                        .append(center.getZ());
            }
            return log.toString();
        });
    }

    static @Nullable BandoPiece piece(StructureStart start) {
        if (!start.isValid()) {
            return null;
        }
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof BandoPiece bando) {
                return bando;
            }
        }
        return null;
    }

    private record Area(String label, ChunkPos center, List<CompletableFuture<?>> futures, long start) {
    }

    private static final Map<BandoFamily, Double> WITHOUT = new EnumMap<>(BandoFamily.class);

    private static StepResult timingWithout(SelfTest.StepContext ctx) {
        return timing(ctx, false);
    }

    private static StepResult timingWith(SelfTest.StepContext ctx) {
        return timing(ctx, true);
    }

    @SuppressWarnings("unchecked")
    private static StepResult timing(SelfTest.StepContext ctx, boolean with) {
        Map<String, Object> state = ctx.state();
        MinecraftServer server = ctx.server();
        if (server == null) {
            return StepResult.fail("keine Welt");
        }
        List<Object[]> plan = (List<Object[]>) state.computeIfAbsent("plan", k -> {
            List<Object[]> list = new ArrayList<>();
            Map<BandoFamily, List<ChunkPos>> found = expected(server);
            ChunkPos first = found.values().iterator().next().getFirst();
            list.add(new Object[]{null, new ChunkPos(first.x() - 40, first.z() - 40), WARMUP_RADIUS});
            for (Map.Entry<BandoFamily, List<ChunkPos>> entry : found.entrySet()) {
                list.add(new Object[]{entry.getKey(), entry.getValue().getFirst(), AREA_RADIUS});
            }
            return list;
        });
        Map<BandoFamily, Double> results = (Map<BandoFamily, Double>) state.computeIfAbsent("results", k -> new EnumMap<>(BandoFamily.class));
        int index = (int) state.getOrDefault("index", 0);
        AtomicReference<Area> current = (AtomicReference<Area>) state.computeIfAbsent("area", k -> new AtomicReference<Area>());
        if (index >= plan.size()) {
            return with ? summarize(ctx, results) : storeWithout(ctx, results);
        }
        Area area = current.get();
        if (area == null) {
            if (!state.containsKey(FUTURE)) {
                Object[] entry = plan.get(index);
                String label = entry[0] == null ? "Aufwärmen" : ((BandoFamily) entry[0]).key();
                state.put(FUTURE, ctx.onServer(s -> current.set(request(s.overworld(), label, (ChunkPos) entry[1], (Integer) entry[2]))));
            } else if (((CompletableFuture<?>) state.get(FUTURE)).isCompletedExceptionally()) {
                return StepResult.fail("Chunks nicht angefordert: " + ((CompletableFuture<?>) state.get(FUTURE)).exceptionNow());
            }
            return StepResult.pending();
        }
        for (CompletableFuture<?> future : area.futures()) {
            if (!future.isDone()) {
                return StepResult.pending();
            }
        }
        double ms = (System.nanoTime() - area.start()) / 1.0E6;
        int chunks = area.futures().size();
        Object[] entry = plan.get(index);
        if (entry[0] != null) {
            results.put((BandoFamily) entry[0], ms / chunks);
        }
        ctx.log(String.format(Locale.ROOT, "%s: %d Chunks in %.0f ms (%.1f ms je Chunk)", area.label(), chunks, ms, ms / chunks));
        state.put("index", index + 1);
        current.set(null);
        state.remove(FUTURE);
        return StepResult.pending();
    }

    private static Area request(ServerLevel level, String label, ChunkPos center, int radius) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        long start = System.nanoTime();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                futures.add(level.getChunkSource().getChunkFuture(center.x() + dx, center.z() + dz, ChunkStatus.FULL, true));
            }
        }
        return new Area(label, center, futures, start);
    }

    private static StepResult storeWithout(SelfTest.StepContext ctx, Map<BandoFamily, Double> results) {
        WITHOUT.clear();
        WITHOUT.putAll(results);
        double sum = 0.0;
        for (double value : results.values()) {
            sum += value;
        }
        ctx.log(String.format(Locale.ROOT, "ohne Bauwerke im Mittel %.2f ms je Chunk (%d Gebiete)", sum / Math.max(1, results.size()), results.size()));
        return StepResult.pass();
    }

    private static StepResult summarize(SelfTest.StepContext ctx, Map<BandoFamily, Double> results) {
        if (WITHOUT.size() < BandoFamily.values().length || results.size() < BandoFamily.values().length) {
            return StepResult.fail("Messung unvollständig (" + WITHOUT.size() + " ohne, " + results.size() + " mit)");
        }
        double with = 0.0;
        double without = 0.0;
        StringBuilder pairs = new StringBuilder();
        for (BandoFamily family : BandoFamily.values()) {
            double a = results.get(family);
            double b = WITHOUT.get(family);
            with += a;
            without += b;
            pairs.append(String.format(Locale.ROOT, " %s %.1f/%.1f", family.key(), a, b));
        }
        with /= BandoFamily.values().length;
        without /= BandoFamily.values().length;
        double ratio = with / without;
        double start = BandoSelfTests.START_MS.get();
        double place = BandoSelfTests.PLACE_MS.get();
        double spacing = 44.0 * 44.0;
        double amortized = (start * BandoFamily.values().length + place) / spacing;
        String summary = String.format(Locale.ROOT,
                "gleiche Chunks (7×7 um jeden Bando) mit Bauwerken %.2f ms je Chunk, ohne Bauwerke %.2f ms je Chunk, Verhältnis %.2f (mit/ohne in ms:%s);"
                        + " Bando-Code: Start %.2f ms je Versuch, Setzen %.1f ms je Bando, auf die Rasterzelle (44×44 Chunks) umgelegt %.4f ms je Chunk"
                        + " (%.3f %% der Chunk-Erzeugung)",
                with, without, ratio, pairs, start, place, amortized, 100.0 * amortized / without);
        TIMING.set(summary);
        ctx.log(summary);
        if (ratio > MAX_RATIO) {
            return StepResult.fail("Gebiete mit Bando zu langsam: " + summary);
        }
        if (amortized > without * 0.01) {
            return StepResult.fail("Bando-Code kostet mehr als 1 % der Chunk-Erzeugung: " + summary);
        }
        return StepResult.pass();
    }

    private static int[] fenceConnections(ServerLevel level, BandoPiece piece) {
        BandoPlan plan = piece.plan();
        BandoTransform t = piece.transform();
        int fences = 0;
        int connected = 0;
        for (int y = 0; y < plan.sizeY(); y++) {
            for (int z = 0; z < plan.sizeZ(); z++) {
                for (int x = 0; x < plan.sizeX(); x++) {
                    if (plan.mat(x, y, z) != Mat.RAIL || plan.mat(x + 1, y, z) != Mat.RAIL) {
                        continue;
                    }
                    BlockPos a = new BlockPos(t.worldX(x, z), t.worldY(y), t.worldZ(x, z));
                    BlockPos b = new BlockPos(t.worldX(x + 1, z), t.worldY(y), t.worldZ(x + 1, z));
                    BlockState state = level.getBlockState(a);
                    if (!(state.getBlock() instanceof CrossCollisionBlock) || !(level.getBlockState(b).getBlock() instanceof CrossCollisionBlock)) {
                        continue;
                    }
                    Direction toward = Direction.getNearest(b.getX() - a.getX(), 0, b.getZ() - a.getZ(), null);
                    if (toward == null) {
                        continue;
                    }
                    fences++;
                    if (state.getValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(toward))) {
                        connected++;
                    }
                }
            }
        }
        return new int[]{fences, connected};
    }

    static String timing() {
        return TIMING.get();
    }

    private static final class VisitTour {
        private int site;
        private String phase = "go";
        private int since;
        private @Nullable CompletableFuture<?> pending;
        private final AtomicReference<String> result = new AtomicReference<>();
        private final List<String> problems = new ArrayList<>();
        private final StringBuilder log = new StringBuilder();
        private int fences;
        private int connected;

        StepResult tick(SelfTest.StepContext ctx) {
            if (SITES.size() < BandoFamily.values().length) {
                return StepResult.fail("keine Bando-Plätze (Schritt /locate fehlgeschlagen)");
            }
            if (this.pending != null && this.pending.isCompletedExceptionally()) {
                return StepResult.fail("Server-Aktion: " + this.pending.exceptionNow());
            }
            if (this.pending != null && !this.pending.isDone()) {
                return StepResult.pending();
            }
            switch (this.phase) {
                case "go" -> {
                    if (this.site >= SITES.size()) {
                        if (this.fences > 0 && this.connected < this.fences * 0.9) {
                            this.problems.add("Geländer nicht verbunden: " + this.connected + "/" + this.fences);
                        }
                        if (!this.problems.isEmpty()) {
                            return StepResult.fail(String.join("; ", this.problems));
                        }
                        ctx.log(this.log.append(String.format(Locale.ROOT, "; Geländer verbunden %d/%d", this.connected, this.fences)).toString().trim());
                        return StepResult.pass();
                    }
                    Site s = SITES.get(this.site);
                    this.pending = ctx.onServer(server -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                        player.setGameMode(GameType.CREATIVE);
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        player.teleportTo(server.overworld(), s.center().getX() + 0.5, s.center().getY() + 30.0, s.center().getZ() + 0.5, Set.of(),
                                0.0F, 60.0F, true);
                    });
                    this.phase = "wait";
                    this.since = ctx.tick();
                }
                case "wait" -> {
                    if (ctx.tick() % 10 != 0) {
                        return StepResult.pending();
                    }
                    Site s = SITES.get(this.site);
                    if (ctx.tick() - this.since > 1200) {
                        return StepResult.fail(s.family().key() + ": Chunks werden nicht aktiv");
                    }
                    this.result.set(null);
                    this.pending = ctx.onServer(server -> this.result.set(ticking(server.overworld(), s) ? "ja" : "nein"));
                    this.phase = "check";
                }
                case "check" -> {
                    Site s = SITES.get(this.site);
                    if (!"ja".equals(this.result.get())) {
                        this.phase = "wait";
                        return StepResult.pending();
                    }
                    if (ctx.tick() - this.since < 40) {
                        this.phase = "wait";
                        return StepResult.pending();
                    }
                    this.pending = ctx.onServer(server -> this.verify(server, s));
                    this.site++;
                    this.phase = "go";
                }
                default -> {
                    return StepResult.fail("unbekannte Phase " + this.phase);
                }
            }
            return StepResult.pending();
        }

        private static boolean ticking(ServerLevel level, Site s) {
            BandoTransform t = s.piece().transform();
            for (int x = t.originX(); x < t.originX() + t.widthX(); x += 8) {
                for (int z = t.originZ(); z < t.originZ() + t.widthZ(); z += 8) {
                    if (!level.shouldTickBlocksAt(ChunkPos.pack(x >> 4, z >> 4))) {
                        return false;
                    }
                }
            }
            return true;
        }

        private void verify(MinecraftServer server, Site site) {
            ServerLevel level = server.overworld();
            Holder.Reference<Structure> holder = BandoSelfTests.holder(server, site.family());
            StructureStart start = level.getChunk(site.start().x(), site.start().z()).getStartForStructure(holder.value());
            BandoPiece piece = start == null ? null : piece(start);
            if (piece == null) {
                this.problems.add(site.family().key() + ": Start fehlt nach der Erzeugung");
                return;
            }
            String problem = BandoSelfTests.verify(level, piece);
            if (problem != null) {
                this.problems.add(site.family().key() + "/" + site.variant().key() + ": " + problem);
            }
            Holder<Biome> biome = level.getBiome(site.center());
            if (!holder.value().biomes().contains(biome)) {
                this.problems.add(site.family().key() + ": falsches Biom " + biome.getRegisteredName());
            }
            int[] counts = fenceConnections(level, piece);
            this.fences += counts[0];
            this.connected += counts[1];
            this.log.append(' ').append(site.family().key()).append('/').append(site.variant().key()).append(' ')
                    .append(biome.getRegisteredName().replace("minecraft:", "")).append(piece.chestPos() != null ? " mit Truhe" : "")
                    .append(String.format(Locale.ROOT, " (%d/%d Geländer)", counts[1], counts[0])).append(';');
        }
    }

    private static final class ShotTour {
        private static final double HOVER = 0.0;
        private int site;
        private String phase = "resize";
        private int since;
        private int stable;
        private @Nullable CompletableFuture<?> pending;
        private @Nullable CompletableFuture<String> shot;
        private int droneId = -1;
        private int armedAt = -1;
        private double targetY;
        private double lastY;
        private double integral;
        private int count;
        private volatile @Nullable Vec3 eye;
        private volatile float yaw;
        private volatile float pitch;
        private int poses;

        StepResult tick(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.pending != null && this.pending.isCompletedExceptionally()) {
                return this.fail(ctx, "Server-Aktion: " + this.pending.exceptionNow());
            }
            switch (this.phase) {
                case "resize" -> {
                    StepResult resized = Screenshots.resizeWindow(ctx);
                    if (resized.status() == StepResult.Status.PENDING) {
                        return resized;
                    }
                    this.next("aerial", ctx);
                }
                case "aerial" -> {
                    if (this.site >= SITES.size()) {
                        this.next("done", ctx);
                        return StepResult.pending();
                    }
                    PilotSession session = PilotSession.INSTANCE;
                    if (session.isActive() || mc.player == null || mc.getCameraEntity() != mc.player) {
                        if (session.isActive()) {
                            session.requestStop();
                        }
                        return ctx.tick() - this.since > 300 ? this.fail(ctx, "FPV-Sitzung endet nicht vor dem Luftbild") : StepResult.pending();
                    }
                    Site s = SITES.get(this.site);
                    this.eye = null;
                    this.poses = 0;
                    this.pending = ctx.onServer(server -> this.placeAerial(server, s));
                    this.next("aerial_wait", ctx);
                }
                case "aerial_wait", "fpv_wait" -> {
                    if (this.pending == null || !this.pending.isDone()) {
                        return StepResult.pending();
                    }
                    if (this.phase.equals("fpv_wait")) {
                        this.holdDrone();
                    }
                    if (!mc.gui.hud.isHidden() && this.phase.equals("aerial_wait")) {
                        mc.gui.hud.toggle();
                    }
                    Screenshots.clearOverlays(mc);
                    Screenshots.parkMouse(mc);
                    boolean posed = !this.phase.equals("aerial_wait") || this.posed(mc);
                    if (!posed && ctx.tick() - this.since > 100) {
                        if (++this.poses > 5) {
                            return this.fail(ctx, "Kamera steht nach dem Teleport nicht am Luftbild-Punkt");
                        }
                        Site s = SITES.get(this.site);
                        this.pending = ctx.onServer(server -> this.placeAerial(server, s));
                        this.next("aerial_wait", ctx);
                        return StepResult.pending();
                    }
                    if (posed && mc.levelRenderer.hasRenderedAllSections() && ctx.tick() - this.since > 60) {
                        this.stable++;
                    } else {
                        this.stable = 0;
                    }
                    if (this.stable >= 20 || posed && ctx.tick() - this.since > 600) {
                        Site s = SITES.get(this.site);
                        String kind = this.phase.equals("aerial_wait") ? "aerial" : "fpv";
                        if (kind.equals("fpv")) {
                            VideoState video = VideoState.INSTANCE;
                            QuadcopterEntity flown = PilotSession.INSTANCE.drone();
                            ctx.log(String.format(Locale.ROOT, "%s/%s FPV: Video %s, %.1f Blöcke, Hindernisse %.1f dB, %s, Pilot %s, Drohne %s",
                                    s.family().key(), s.variant().key(), video.status(), video.distanceBlocks(), video.obstructionDb(),
                                    video.noSignal() ? "kein Signal" : "Signal", mc.player == null ? "-" : mc.player.blockPosition().toShortString(),
                                    flown == null ? "-" : flown.blockPosition().toShortString()));
                        }
                        this.shot = Screenshots.capture("bando_" + s.family().key() + "_" + s.variant().key() + "_" + kind);
                        this.next(kind + "_shot", ctx);
                    }
                }
                case "aerial_shot", "fpv_shot" -> {
                    if (this.phase.equals("fpv_shot")) {
                        this.holdDrone();
                    }
                    if (this.shot == null || !this.shot.isDone()) {
                        return StepResult.pending();
                    }
                    if (this.shot.isCompletedExceptionally()) {
                        return this.fail(ctx, "Bildschirmfoto: " + this.shot.exceptionNow());
                    }
                    this.count++;
                    if (this.phase.equals("aerial_shot") && this.site >= BandoFamily.values().length) {
                        this.site++;
                        this.next("aerial", ctx);
                    } else if (this.phase.equals("aerial_shot")) {
                        if (mc.gui.hud.isHidden()) {
                            mc.gui.hud.toggle();
                        }
                        Site s = SITES.get(this.site);
                        this.pending = ctx.onServer(server -> this.placeDrone(server, s));
                        this.next("fpv_start", ctx);
                    } else {
                        PilotSession.INSTANCE.clearTestInput();
                        this.armedAt = -1;
                        int id = this.droneId;
                        this.pending = ctx.onServer(server -> discard(server, id));
                        this.site++;
                        this.next("aerial", ctx);
                    }
                }
                case "fpv_start" -> {
                    if (this.pending == null || !this.pending.isDone()) {
                        return StepResult.pending();
                    }
                    PilotSession session = PilotSession.INSTANCE;
                    DroneSim sim = session.sim();
                    if (!session.isFpv() || sim == null) {
                        return ctx.tick() - this.since > 300 ? this.fail(ctx, "keine FPV-Sitzung") : StepResult.pending();
                    }
                    int elapsed = ctx.tick() - this.since;
                    if (!sim.telemetry().armed) {
                        session.setTestInput(StickPositions.IDLE, elapsed >= 10 && sim.telemetry().armBlockers == 0 && ctx.tick() % 10 >= 5, StabMode.ANGLE);
                        return elapsed > 400 ? this.fail(ctx, "nicht scharf: " + sim.telemetry().armBlockers) : StepResult.pending();
                    }
                    if (this.armedAt < 0) {
                        this.armedAt = ctx.tick();
                    }
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.2F), true, StabMode.ANGLE);
                    if (ctx.tick() - this.armedAt > 20) {
                        Site s = SITES.get(this.site);
                        this.lastY = this.targetY;
                        this.integral = 0.0;
                        this.pending = ctx.onServer(server -> this.resetDrone(server, s));
                        this.next("fpv_wait", ctx);
                    }
                }
                case "done" -> {
                    StepResult restored = Screenshots.restoreWindow(ctx);
                    if (mc.gui.hud.isHidden()) {
                        mc.gui.hud.toggle();
                    }
                    ctx.log(this.count + " Bildschirmfotos in " + Screenshots.dir());
                    return restored;
                }
                default -> {
                    return StepResult.fail("unbekannte Phase " + this.phase);
                }
            }
            return StepResult.pending();
        }

        private StepResult fail(SelfTest.StepContext ctx, String reason) {
            PilotSession.INSTANCE.clearTestInput();
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
            Screenshots.restoreWindow(ctx);
            Site s = this.site < SITES.size() ? SITES.get(this.site) : null;
            return StepResult.fail((s == null ? "" : s.family().key() + "/" + s.variant().key() + ": ") + reason);
        }

        private void next(String phase, SelfTest.StepContext ctx) {
            this.phase = phase;
            this.since = ctx.tick();
            this.stable = 0;
        }

        private void holdDrone() {
            PilotSession session = PilotSession.INSTANCE;
            DroneSim sim = session.sim();
            if (sim == null) {
                return;
            }
            double y = session.comPosition(new Vector3d()).y;
            double vy = (y - this.lastY) * 20.0;
            double error = this.targetY - y;
            this.integral = Math.max(-0.2, Math.min(0.2, this.integral + error * 0.004));
            this.lastY = y;
            float throttle = (float) Math.max(0.05, Math.min(0.7, 0.28 + this.integral + error * 0.12 - vy * 0.08));
            session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), true, StabMode.ANGLE);
        }

        private boolean posed(Minecraft mc) {
            Vec3 at = this.eye;
            if (at == null || mc.player == null || mc.getCameraEntity() != mc.player || PilotSession.INSTANCE.isActive()) {
                return false;
            }
            return mc.player.getEyePosition().distanceToSqr(at) < 1.0 && Math.abs(Mth.wrapDegrees(mc.player.getYRot() - this.yaw)) < 1.0F
                    && Math.abs(mc.player.getXRot() - this.pitch) < 1.0F;
        }

        private void placeAerial(MinecraftServer server, Site s) {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            CommandSourceStack quiet = server.createCommandSourceStack().withSource(CommandSource.NULL).withLevel(level);
            server.getCommands().performPrefixedCommand(quiet, "time set noon");
            server.getCommands().performPrefixedCommand(quiet, "weather clear");
            BandoPlan plan = s.piece().plan();
            int size = Math.max(plan.sizeX(), plan.sizeZ());
            Vec3 target = Vec3.atCenterOf(s.center()).add(0.0, plan.sizeY() * 0.3, 0.0);
            double angle = Math.toRadians(35.0 + 90.0 * (this.site % 4));
            BandoCamera.Pose pose = BandoCamera.aerial(level, s.piece().getBoundingBox(), target, angle, size * 0.7 + 10.0, size * 0.8 + 14.0);
            player.teleportTo(level, pose.eye().x, pose.eye().y - player.getEyeHeight(), pose.eye().z, Set.of(), pose.yaw(), pose.pitch(), true);
            this.yaw = pose.yaw();
            this.pitch = pose.pitch();
            this.eye = pose.eye();
        }

        private void placeDrone(MinecraftServer server, Site s) {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            BandoPlan plan = s.piece().plan();
            BandoTransform t = s.piece().transform();
            BandoViews.View view = BandoViews.flyThrough(plan);
            BlockPos base = null;
            for (int r = 0; r <= 6 && base == null; r++) {
                for (int step = 0; step < Math.max(1, view.run()) && base == null; step++) {
                    for (int side = -r; side <= r && base == null; side++) {
                        int lx = view.x() + Meta.dx(view.facing()) * step + Meta.dz(view.facing()) * side;
                        int lz = view.z() + Meta.dz(view.facing()) * step - Meta.dx(view.facing()) * side;
                        BlockPos at = new BlockPos(t.worldX(lx, lz), t.worldY(view.y()), t.worldZ(lx, lz));
                        for (int i = 0; i < 24; i++) {
                            BlockState below = level.getBlockState(at.below());
                            if (!below.getFluidState().isEmpty()) {
                                break;
                            }
                            if (!below.getCollisionShape(level, at.below()).isEmpty()) {
                                if (level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at).getFluidState().isEmpty()) {
                                    base = at;
                                }
                                break;
                            }
                            at = at.below();
                        }
                    }
                }
            }
            if (base == null) {
                base = anyDryTop(level, plan, t, view);
            }
            if (base == null) {
                throw new IllegalStateException("kein fester, trockener Startplatz im Bando");
            }
            Vec3 start = Vec3.atBottomCenterOf(base).add(0.0, 0.05, 0.0);
            float facingYaw = yawOf(Meta.rotateFacing(view.facing(), t.turns()));
            QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("Drohne nicht erzeugt");
            }
            ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
            stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
            drone.loadFromStack(stack);
            drone.setBatteryCharge(1.0F);
            drone.setTune(new FlightTune(Map.of(TuneMapping.CAMERA_UPTILT, 8.0F)));
            drone.snapTo(start.x, start.y, start.z, facingYaw, 0.0F);
            level.addFreshEntity(drone);
            drone.resetTo(start, new Quaternionf().rotationY((float) Math.toRadians(-facingYaw) + (float) Math.PI));
            this.droneId = drone.getId();
            player.teleportTo(level, start.x - 3.0, start.y + 4.0, start.z - 3.0, Set.of(), facingYaw, 20.0F, true);
            ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
            remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.getInventory().setItem(0, remote);
            player.getInventory().setSelectedSlot(0);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(0));
            ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_DIGITAL).map(ItemStack::new).orElse(ItemStack.EMPTY);
            goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.setItemSlot(EquipmentSlot.HEAD, goggles);
            DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
            if (result != DronePiloting.StartResult.STARTED) {
                throw new IllegalStateException("Start verweigert: " + result);
            }
            this.targetY = t.worldY(view.y()) + 0.5 + HOVER;
            this.lastY = start.y;
            this.integral = 0.0;
        }

        private static Vec3 pilotSpot(ServerLevel level, ServerPlayer player, Vec3 drone, int facing) {
            int dx = Meta.dx(facing);
            int dz = Meta.dz(facing);
            Vec3 first = new Vec3(drone.x - dx * 8.0, drone.y + 2.0, drone.z - dz * 8.0);
            for (int back = 8; back >= 3; back--) {
                for (int up = 2; up >= 0; up--) {
                    for (int side = 0; side <= 3; side++) {
                        for (int sign = -1; sign <= 1; sign += 2) {
                            if (side == 0 && sign > 0) {
                                continue;
                            }
                            Vec3 feet = new Vec3(drone.x - dx * back + dz * side * sign, drone.y + up, drone.z - dz * back - dx * side * sign);
                            AABB body = player.getDimensions(player.getPose()).makeBoundingBox(feet);
                            Vec3 eye = feet.add(0.0, player.getEyeHeight(), 0.0);
                            if (level.noCollision(player, body) && level.clip(new ClipContext(eye, drone, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                                    .getType() == HitResult.Type.MISS) {
                                return feet;
                            }
                        }
                    }
                }
            }
            return first;
        }

        private static @Nullable BlockPos anyDryTop(ServerLevel level, BandoPlan plan, BandoTransform t, BandoViews.View view) {
            BlockPos best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (int z = 0; z < plan.sizeZ(); z++) {
                for (int x = 0; x < plan.sizeX(); x++) {
                    int distance = Math.abs(x - view.x()) + Math.abs(z - view.z());
                    if (distance >= bestDistance) {
                        continue;
                    }
                    for (int y = plan.sizeY() - 2; y >= 0; y--) {
                        Mat mat = plan.mat(x, y, z);
                        if (mat.kind() != Mat.Kind.STRUCTURE || mat == Mat.RAIL) {
                            continue;
                        }
                        BlockPos above = new BlockPos(t.worldX(x, z), t.worldY(y + 1), t.worldZ(x, z));
                        BlockState state = level.getBlockState(above);
                        BlockState head = level.getBlockState(above.above());
                        if (state.isAir() && head.isAir() && !level.getBlockState(above.below()).getCollisionShape(level, above.below()).isEmpty()) {
                            best = above;
                            bestDistance = distance;
                        }
                        break;
                    }
                }
            }
            return best;
        }

        private void resetDrone(MinecraftServer server, Site s) {
            ServerLevel level = server.overworld();
            if (!(level.getEntity(this.droneId) instanceof QuadcopterEntity drone)) {
                throw new IllegalStateException("Drohne fehlt");
            }
            BandoTransform t = s.piece().transform();
            BandoViews.View view = BandoViews.flyThrough(s.piece().plan());
            float facingYaw = yawOf(Meta.rotateFacing(view.facing(), t.turns()));
            Vec3 at = new Vec3(t.worldX(view.x(), view.z()) + 0.5, this.targetY, t.worldZ(view.x(), view.z()) + 0.5);
            drone.resetTo(at, new Quaternionf().rotationY((float) Math.toRadians(-facingYaw) + (float) Math.PI));
            int facing = Meta.rotateFacing(view.facing(), t.turns());
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            Vec3 pilot = pilotSpot(level, player, at, facing);
            player.teleportTo(level, pilot.x, pilot.y, pilot.z, Set.of(), facingYaw, 10.0F, true);
        }

        private static float yawOf(int facing) {
            return switch (facing & 3) {
                case Meta.EAST -> -90.0F;
                case Meta.SOUTH -> 0.0F;
                case Meta.WEST -> 90.0F;
                default -> 180.0F;
            };
        }

        private static void discard(MinecraftServer server, int id) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(id);
                if (entity instanceof QuadcopterEntity drone) {
                    if (drone.pilot().isPresent()) {
                        DronePiloting.release(drone);
                    }
                    drone.discard();
                }
            }
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.getInventory().setItem(0, ItemStack.EMPTY);
        }
    }
}
