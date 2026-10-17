package de.lardtom.propwash.bando.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.bando.BandoMapItem;
import de.lardtom.propwash.bando.BandoModule;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.AtlasIds;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

final class BandoMapTests {
    static final String PREFIX = "bando: Karte – ";
    private static final String FUTURE = "map_future";
    private static final String RESULT = "map_result";
    private static final String OK = "ok";
    private static final int SEARCH_CELLS = 16;
    private static final int MAX_TARGET_GAP = 32;
    private static final int LOOT_ROLLS = 200;
    private static final int CARTOGRAPHER_LEVEL = 3;
    private static final ResourceKey<TradeSet> CARTOGRAPHER_SET = ResourceKey.create(Registries.TRADE_SET,
            Identifier.withDefaultNamespace("cartographer/level_" + CARTOGRAPHER_LEVEL));
    private static final ResourceKey<LootTable> CARTOGRAPHER_CHEST = ResourceKey.create(Registries.LOOT_TABLE,
            Constants.id("inject/village_cartographer"));
    private static final AtomicReference<@Nullable Sold> SOLD = new AtomicReference<>();
    private static final int MERCHANT_WIDTH = 276;
    private static final int MERCHANT_HEIGHT = 166;
    private static final int FIRST_RESULT_X = 5 + 68 + 8;
    private static final int FIRST_RESULT_Y = 16 + 2 + 10;

    record Sold(ItemStack map, MerchantOffer offer, BlockPos target, BoundingBox site) {
    }

    private record Found(Identifier structure, StructureStart start) {
    }

    private BandoMapTests() {
    }

    static void registerClient() {
        SelfTest.clientStep(PREFIX + "Item, Kartensymbol, Handel, Tags und Beute angemeldet", 200, BandoMapTests::registryStep);
    }

    static void registerWithoutStructures() {
        SelfTest.afterLeaveStep(PREFIX + "ohne Bauwerke kein Kartenhandel und keine Karte in Truhen", 1200, BandoMapTests::offStep);
    }

    static void registerWithStructures() {
        SelfTest.afterLeaveStep(PREFIX + "Kartograf verkauft eine Karte zum nächsten unerkundeten Bando", 2400, BandoMapTests::onStep);
        if (Screenshots.enabled()) {
            SelfTest.afterLeaveStep(PREFIX + "Bildschirmfotos Karte in der Hand und Handel", 20 * 60 * 3, new ShotTour()::tick);
        }
    }

    private static StepResult onServer(SelfTest.StepContext ctx, Function<MinecraftServer, String> action) {
        if (!ctx.state().containsKey(FUTURE)) {
            if (ctx.server() == null) {
                return StepResult.fail("keine Welt");
            }
            AtomicReference<String> result = new AtomicReference<>();
            ctx.state().put(RESULT, result);
            ctx.state().put(FUTURE, ctx.onServer(server -> result.set(action.apply(server))));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Aktion fehlgeschlagen: " + future.exceptionNow());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        String text = ((AtomicReference<String>) ctx.state().get(RESULT)).get();
        if (text == null || !text.startsWith(OK)) {
            return StepResult.fail(text == null ? "kein Ergebnis" : text);
        }
        ctx.log(text.substring(OK.length()).trim());
        return StepResult.pass();
    }

    private static StepResult registryStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            List<String> problems = new ArrayList<>();
            if (!(BandoModule.MAP.get() instanceof MapItem)) {
                problems.add("Bando-Karte ist kein MapItem");
            }
            ItemStack blank = new ItemStack(BandoModule.MAP.get());
            if (!blank.has(DataComponents.MAP_DECORATIONS)) {
                problems.add("Bando-Karte ohne Kartensymbol-Komponente");
            }
            Identifier decoration = BuiltInRegistries.MAP_DECORATION_TYPE.getKey(BandoModule.MAP_DECORATION.get());
            if (!Constants.id("bando").equals(decoration) || !Constants.id("bando").equals(BandoModule.MAP_DECORATION.get().assetId())) {
                problems.add("Kartensymbol falsch angemeldet: " + decoration);
            }
            if (!BandoModule.MAP_DECORATION.get().showOnItemFrame()) {
                problems.add("Kartensymbol fehlt im Item-Rahmen");
            }
            TextureAtlas decorations = mc.getAtlasManager().getAtlasOrThrow(AtlasIds.MAP_DECORATIONS);
            if (decorations.getSprite(Constants.id("bando")) == decorations.missingSprite()) {
                problems.add("Kartensymbol propwash:bando fehlt im Atlas map_decorations");
            }
            TextureAtlas items = mc.getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS);
            if (items.getSprite(Constants.id("item/" + BandoModule.MAP_NAME)) == items.missingSprite()) {
                problems.add("Textur der Bando-Karte fehlt im Item-Atlas");
            }
            if (mc.getModelManager().getItemModel(BandoModule.MAP.id()) instanceof MissingItemModel) {
                problems.add("Bando-Karte ohne Item-Modell");
            }
            Language language = Language.getInstance();
            for (String key : List.of("item.propwash." + BandoModule.MAP_NAME, BandoMapItem.BLANK_KEY,
                    "propwash.help." + BandoMapItem.HELP + ".summary")) {
                if (!language.has(key)) {
                    problems.add("Text fehlt: " + key);
                }
            }
            if (ContentTab.contents().stream().anyMatch(stack -> stack.is(BandoModule.MAP.get()))) {
                problems.add("leere Bando-Karte im Kreativ-Tab");
            }
            if (!problems.isEmpty()) {
                return StepResult.fail(String.join("; ", problems));
            }
        }
        return onServer(ctx, BandoMapTests::checkRegistries);
    }

    private static String checkRegistries(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        Registry<VillagerTrade> trades = server.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
        Optional<Holder.Reference<VillagerTrade>> trade = trades.get(BandoModule.MAP_TRADE);
        if (trade.isEmpty()) {
            problems.add("Handel " + BandoModule.MAP_TRADE.identifier() + " fehlt");
        } else {
            Optional<TradeSet> set = server.registryAccess().lookupOrThrow(Registries.TRADE_SET).getOptional(CARTOGRAPHER_SET);
            if (set.isEmpty() || !set.get().trades().contains(trade.get())) {
                problems.add("Handel fehlt im Handelsset des Kartografen (Geselle)");
            }
        }
        if (server.reloadableRegistries().lookup().lookupOrThrow(Registries.ITEM_MODIFIER).get(BandoModule.MAP_MODIFIER).isEmpty()) {
            problems.add("Item-Modifikator " + BandoModule.MAP_MODIFIER.identifier() + " fehlt");
        }
        if (!new ItemStack(BandoModule.MAP.get()).is(ItemTags.CLONABLE_MAPS)) {
            problems.add("Bando-Karte nicht in #minecraft:clonable_maps");
        }
        for (ResourceKey<LootTable> table : List.of(BandoModule.LOOT, CARTOGRAPHER_CHEST)) {
            if (server.reloadableRegistries().getLootTable(table) == LootTable.EMPTY) {
                problems.add("Beutetabelle " + table.identifier() + " fehlt");
            }
        }
        return problems.isEmpty() ? OK + " Item, Kartensymbol im Atlas, Handel im Set des Gesellen, Modifikator, Kopier-Tag, Beutetabellen"
                : String.join("; ", problems);
    }

    private static LootContext tradeContext(ServerLevel level, Villager villager) {
        return new LootContext.Builder(new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, villager.position())
                .withParameter(LootContextParams.THIS_ENTITY, villager)
                .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
                .create(LootContextParamSets.VILLAGER_TRADE)).create(Optional.empty());
    }

    private static LootContext chestContext(ServerLevel level, BlockPos at) {
        return new LootContext.Builder(new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(at))
                .create(LootContextParamSets.CHEST)).create(Optional.empty());
    }

    static Villager cartographer(ServerLevel level, Vec3 at) {
        Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        if (villager == null) {
            throw new IllegalStateException("Dorfbewohner nicht erzeugt");
        }
        villager.setPos(at);
        villager.setVillagerData(villager.getVillagerData()
                .withProfession(level.registryAccess(), VillagerProfession.CARTOGRAPHER)
                .withLevel(CARTOGRAPHER_LEVEL));
        return villager;
    }

    private static VillagerTrade trade(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE).getValueOrThrow(BandoModule.MAP_TRADE);
    }

    private static LootItemFunction modifier(MinecraftServer server) {
        return server.reloadableRegistries().lookup().lookupOrThrow(Registries.ITEM_MODIFIER).getOrThrow(BandoModule.MAP_MODIFIER).value();
    }

    private static int countMaps(ServerLevel level, ResourceKey<LootTable> key, BlockPos at) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(at)).create(LootContextParamSets.CHEST);
        int maps = 0;
        for (int seed = 1; seed <= LOOT_ROLLS; seed++) {
            for (ItemStack stack : table.getRandomItems(params, seed)) {
                if (stack.is(BandoModule.MAP.get())) {
                    maps++;
                }
            }
        }
        return maps;
    }

    private static StepResult offStep(SelfTest.StepContext ctx) {
        return onServer(ctx, server -> {
            ServerLevel level = server.overworld();
            if (server.getWorldGenSettings().options().generateStructures()) {
                return "Welt erzeugt Bauwerke";
            }
            BlockPos origin = level.getRespawnData().pos();
            List<String> problems = new ArrayList<>();
            MerchantOffer offer = trade(server).getOffer(tradeContext(level, cartographer(level, Vec3.atCenterOf(origin))));
            if (offer != null) {
                problems.add("Kartograf bietet trotz generate-structures=false eine Karte an: " + offer.getResult());
            }
            ItemStack map = modifier(server).apply(new ItemStack(BandoModule.MAP.get()), chestContext(level, origin));
            if (!map.isEmpty()) {
                problems.add("Modifikator liefert ohne Bauwerke eine Karte: " + map.getComponents());
            }
            int bando = countMaps(level, BandoModule.LOOT, origin);
            int cartographer = countMaps(level, CARTOGRAPHER_CHEST, origin);
            if (bando + cartographer > 0) {
                problems.add(bando + " Karten in Bando-Truhen, " + cartographer + " in Kartografen-Truhen ohne Bauwerke");
            }
            return problems.isEmpty() ? OK + " kein Angebot, Modifikator verwirft die Karte, " + LOOT_ROLLS
                    + " Würfe je Bando- und Kartografen-Truhe ohne Karte" : String.join("; ", problems);
        });
    }

    private static @Nullable Found bandoAt(ServerLevel level, BlockPos target) {
        ChunkPos chunk = ChunkPos.containing(target);
        ChunkAccess access = level.getChunk(chunk.x(), chunk.z(), ChunkStatus.STRUCTURE_STARTS, true);
        Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (Map.Entry<Structure, StructureStart> entry : access.getAllStarts().entrySet()) {
            if (entry.getValue().isValid() && structures.wrapAsHolder(entry.getKey()).is(BandoModule.TAG)) {
                return new Found(structures.getKey(entry.getKey()), entry.getValue());
            }
        }
        return null;
    }

    private static int gap(BoundingBox box, BlockPos at) {
        int dx = Math.max(0, Math.max(box.minX() - at.getX(), at.getX() - box.maxX()));
        int dz = Math.max(0, Math.max(box.minZ() - at.getZ(), at.getZ() - box.maxZ()));
        return (int) Math.ceil(Math.sqrt((double) dx * dx + (double) dz * dz));
    }

    private static @Nullable BlockPos target(ItemStack stack) {
        MapDecorations decorations = stack.get(DataComponents.MAP_DECORATIONS);
        if (decorations == null) {
            return null;
        }
        MapDecorations.Entry entry = decorations.decorations().get("+");
        if (entry == null || !entry.type().is(BandoModule.MAP_DECORATION.key().identifier())) {
            return null;
        }
        return BlockPos.containing(entry.x(), 0.0, entry.z());
    }

    private static String checkMap(ServerLevel level, ItemStack stack, @Nullable BlockPos expected, List<BlockPos> earlier, String what,
                                   List<String> problems, @Nullable List<Found> found) {
        if (stack.isEmpty() || !stack.is(BandoModule.MAP.get())) {
            problems.add(what + ": keine Bando-Karte (" + stack + ")");
            return "";
        }
        if (!stack.has(DataComponents.MAP_ID)) {
            problems.add(what + ": Karte ohne Karten-ID");
        }
        BlockPos target = target(stack);
        if (target == null) {
            problems.add(what + ": kein Bando-Symbol auf der Karte (" + stack.get(DataComponents.MAP_DECORATIONS) + ")");
            return "";
        }
        if (expected != null && (expected.getX() != target.getX() || expected.getZ() != target.getZ())) {
            problems.add(what + ": zeigt auf " + target.toShortString() + " statt auf den nächsten Bando " + expected.toShortString());
        }
        for (BlockPos old : earlier) {
            if (old.getX() == target.getX() && old.getZ() == target.getZ()) {
                problems.add(what + ": zeigt wieder auf den schon kartierten Bando " + target.toShortString());
            }
        }
        Found bando = bandoAt(level, target);
        if (bando == null) {
            problems.add(what + ": an " + target.toShortString() + " steht kein Bando");
            return "";
        }
        int gap = gap(bando.start().getBoundingBox(), target);
        if (gap > MAX_TARGET_GAP) {
            problems.add(what + ": Symbol " + gap + " Blöcke neben dem Bando");
        }
        MapItemSavedData data = MapItem.getSavedData(stack, level);
        if (data == null) {
            problems.add(what + ": Kartendaten fehlen");
        } else {
            int half = 64 << data.scale;
            if (Math.abs(target.getX() - data.centerX) > half || Math.abs(target.getZ() - data.centerZ) > half) {
                problems.add(what + ": Bando liegt außerhalb der Karte");
            }
        }
        if (found != null) {
            found.add(bando);
        }
        earlier.add(target);
        BlockPos origin = level.getRespawnData().pos();
        double distance = Math.sqrt(origin.distSqr(new BlockPos(target.getX(), origin.getY(), target.getZ())));
        return String.format(Locale.ROOT, "%s → %s bei %s (%.0f Blöcke, Symbol %d Blöcke vom Bauwerk)", what,
                bando.structure().getPath(), target.toShortString(), distance, gap);
    }

    private static StepResult onStep(SelfTest.StepContext ctx) {
        return onServer(ctx, server -> {
            ServerLevel level = server.overworld();
            if (!server.getWorldGenSettings().options().generateStructures() || level.getSeed() != BandoSelfTests.SEED) {
                return "Welt falsch angelegt";
            }
            BlockPos origin = level.getRespawnData().pos();
            List<String> problems = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            List<BlockPos> targets = new ArrayList<>();
            List<Found> found = new ArrayList<>();
            BlockPos nearest = level.findNearestMapStructure(BandoModule.TAG, origin, SEARCH_CELLS, false);
            if (nearest == null) {
                return "kein Bando im Suchradius von " + SEARCH_CELLS + " Zellen um " + origin.toShortString();
            }
            Villager villager = cartographer(level, Vec3.atCenterOf(origin));
            VillagerTrade trade = trade(server);
            long started = System.nanoTime();
            MerchantOffer offer = trade.getOffer(tradeContext(level, villager));
            double firstMs = (System.nanoTime() - started) / 1.0E6;
            if (offer == null) {
                return "Kartograf bietet keine Bando-Karte an";
            }
            if (!offer.getCostA().is(Items.EMERALD) || offer.getCostA().getCount() < 5 || !offer.getCostB().is(Items.COMPASS)) {
                problems.add("Preis " + offer.getCostA() + " + " + offer.getCostB() + " statt Smaragde + Kompass");
            }
            ItemStack first = offer.getResult().copy();
            notes.add(checkMap(level, first, nearest, targets, "Handel 1", problems, found));
            if (!found.isEmpty() && found.getFirst().start().getReferences() < 1) {
                problems.add("Handel 1 hat den Bando nicht als kartiert markiert");
            }
            MerchantOffer second = trade.getOffer(tradeContext(level, villager));
            if (second == null) {
                problems.add("zweiter Kartograf bietet keine Karte an");
            } else {
                notes.add(checkMap(level, second.getResult(), null, targets, "Handel 2", problems, null));
            }
            started = System.nanoTime();
            ItemStack loot = modifier(server).apply(new ItemStack(BandoModule.MAP.get()), chestContext(level, origin));
            double lootMs = (System.nanoTime() - started) / 1.0E6;
            notes.add(checkMap(level, loot, null, targets, "Truhe", problems, null));
            if (!problems.isEmpty()) {
                return String.join("; ", problems);
            }
            SOLD.set(new Sold(first, offer, targets.getFirst(), found.getFirst().start().getBoundingBox()));
            return OK + String.format(Locale.ROOT, " Seed %d, Ursprung %s: %s; Suche %.1f ms (Handel), %.1f ms (Truhe)",
                    level.getSeed(), origin.toShortString(), String.join("; ", notes), firstMs, lootMs);
        });
    }

    private static void pointMouse(Minecraft mc, int guiX, int guiY) {
        double x = (guiX + 0.5) * mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
        double y = (guiY + 0.5) * mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
        mc.mouseHandler.onMove(mc.getWindow().handle(), x, y, 0.0, 0.0);
    }

    private static final class ShotTour {
        private String phase = "resize";
        private int since;
        private int placements;
        private @Nullable CompletableFuture<?> pending;
        private @Nullable CompletableFuture<String> shot;
        private volatile @Nullable Vec3 eye;
        private volatile float yaw;
        private volatile float pitch;
        private final AtomicReference<@Nullable Villager> villager = new AtomicReference<>();

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
                    if (SOLD.get() == null) {
                        return this.fail(ctx, "keine verkaufte Karte (Schritt '" + PREFIX + "Kartograf …' fehlt?)");
                    }
                    Screenshots.hideMarker(true);
                    this.pending = ctx.onServer(this::placeWithMap);
                    this.next("hand_wait", ctx);
                }
                case "hand_wait" -> {
                    if (this.pending == null || !this.pending.isDone()) {
                        return StepResult.pending();
                    }
                    Screenshots.clearOverlays(mc);
                    Screenshots.parkMouse(mc);
                    boolean posed = this.posed(mc);
                    boolean holding = mc.player != null && mc.player.getMainHandItem().is(BandoModule.MAP.get());
                    if (!posed && ctx.tick() - this.since > 100) {
                        if (++this.placements > 5) {
                            return this.fail(ctx, "Spieler steht nicht am Bando: " + this.where(mc));
                        }
                        this.pending = ctx.onServer(this::placeWithMap);
                        this.next("hand_wait", ctx);
                        return StepResult.pending();
                    }
                    if (posed && holding && mc.levelRenderer.hasRenderedAllSections() && ctx.tick() - this.since > 200
                            || ctx.tick() - this.since > 900) {
                        if (!posed || !holding) {
                            return this.fail(ctx, "Spieler steht nicht mit der Karte am Bando: " + this.where(mc) + ", Karte in der Hand " + holding);
                        }
                        this.shot = Screenshots.capture("bando_map_hand");
                        this.next("hand_shot", ctx);
                    }
                }
                case "hand_shot" -> {
                    StepResult done = this.saved(ctx, "bando_map_hand");
                    if (done != null) {
                        return done;
                    }
                    if (this.shot == null) {
                        this.pending = ctx.onServer(this::openTrade);
                        this.next("trade_wait", ctx);
                    }
                }
                case "trade_wait" -> {
                    if (this.pending == null || !this.pending.isDone()) {
                        return StepResult.pending();
                    }
                    Screenshots.clearOverlays(mc);
                    if (mc.gui.screen() instanceof MerchantScreen screen) {
                        int left = (screen.width - MERCHANT_WIDTH) / 2;
                        int top = (screen.height - MERCHANT_HEIGHT) / 2;
                        pointMouse(mc, left + FIRST_RESULT_X, top + FIRST_RESULT_Y);
                    } else {
                        Screenshots.parkMouse(mc);
                    }
                    if (mc.gui.screen() instanceof MerchantScreen && ctx.tick() - this.since > 40) {
                        this.shot = Screenshots.capture("bando_map_trade");
                        this.next("trade_shot", ctx);
                    } else if (ctx.tick() - this.since > 400) {
                        return this.fail(ctx, "Handelsfenster des Kartografen öffnet nicht: " + mc.gui.screen());
                    }
                }
                case "trade_shot" -> {
                    StepResult done = this.saved(ctx, "bando_map_trade");
                    if (done != null) {
                        return done;
                    }
                    if (this.shot == null) {
                        this.cleanUp(ctx);
                        this.next("restore", ctx);
                    }
                }
                case "restore" -> {
                    if (this.pending != null && !this.pending.isDone()) {
                        return StepResult.pending();
                    }
                    return Screenshots.restoreWindow(ctx);
                }
                default -> {
                    return this.fail(ctx, "unbekannte Phase " + this.phase);
                }
            }
            return StepResult.pending();
        }

        private @Nullable StepResult saved(SelfTest.StepContext ctx, String file) {
            CompletableFuture<String> current = this.shot;
            if (current == null || !current.isDone()) {
                return current == null ? null : StepResult.pending();
            }
            if (current.isCompletedExceptionally()) {
                return this.fail(ctx, "Bildschirmfoto " + file + ": " + current.exceptionNow());
            }
            ctx.log("Bildschirmfoto " + Screenshots.dir().resolve(file + ".png") + " (" + current.join() + ")");
            this.shot = null;
            return null;
        }

        private void next(String phase, SelfTest.StepContext ctx) {
            this.phase = phase;
            this.since = ctx.tick();
        }

        private String where(Minecraft mc) {
            Vec3 at = this.eye;
            if (mc.player == null || at == null) {
                return "kein Spieler oder keine Pose";
            }
            return String.format(Locale.ROOT, "Auge %.1f/%.1f/%.1f statt %.1f/%.1f/%.1f, Blick %.1f/%.1f statt %.1f/%.1f",
                    mc.player.getEyePosition().x, mc.player.getEyePosition().y, mc.player.getEyePosition().z, at.x, at.y, at.z,
                    mc.player.getYRot(), mc.player.getXRot(), this.yaw, this.pitch);
        }

        private boolean posed(Minecraft mc) {
            Vec3 at = this.eye;
            if (at == null || mc.player == null || mc.getCameraEntity() != mc.player) {
                return false;
            }
            return mc.player.getEyePosition().distanceToSqr(at) < 1.0 && Math.abs(Mth.wrapDegrees(mc.player.getYRot() - this.yaw)) < 1.0F
                    && Math.abs(mc.player.getXRot() - this.pitch) < 1.0F;
        }

        private void placeWithMap(MinecraftServer server) {
            Sold sold = SOLD.get();
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            CommandSourceStack quiet = server.createCommandSourceStack().withSource(CommandSource.NULL).withLevel(level);
            server.getCommands().performPrefixedCommand(quiet, "time set noon");
            server.getCommands().performPrefixedCommand(quiet, "weather clear");
            player.setItemInHand(InteractionHand.MAIN_HAND, sold.map().copy());
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            BoundingBox box = sold.site();
            int size = Math.max(box.getXSpan(), box.getZSpan());
            Vec3 center = new Vec3((box.minX() + box.maxX() + 1) / 2.0, box.minY() + box.getYSpan() * 0.5, (box.minZ() + box.maxZ() + 1) / 2.0);
            BandoCamera.Pose pose = BandoCamera.aerial(level, box, center, Math.toRadians(40.0), size * 0.8 + 14.0, 2.0);
            player.teleportTo(level, pose.eye().x, pose.eye().y - player.getEyeHeight(), pose.eye().z, Set.of(), pose.yaw(), pose.pitch(), true);
            this.yaw = pose.yaw();
            this.pitch = pose.pitch();
            this.eye = pose.eye();
        }

        private void openTrade(MinecraftServer server) {
            Sold sold = SOLD.get();
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            Vec3 behind = player.getEyePosition().subtract(player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(1.5)).subtract(0.0, 1.6, 0.0);
            Villager trader = cartographer(level, behind);
            trader.setNoAi(true);
            trader.setNoGravity(true);
            MerchantOffers offers = new MerchantOffers();
            offers.add(sold.offer().copy());
            for (MerchantOffer natural : trader.getOffers()) {
                if (!natural.getResult().is(BandoModule.MAP.get()) && offers.size() < 3) {
                    offers.add(natural);
                }
            }
            trader.setOffers(offers);
            level.addFreshEntity(trader);
            this.villager.set(trader);
            trader.setTradingPlayer(player);
            trader.openTradingScreen(player, trader.getDisplayName(), CARTOGRAPHER_LEVEL);
        }

        private void cleanUp(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(null);
            Screenshots.hideMarker(false);
            this.pending = ctx.onServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.closeContainer();
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                Villager trader = this.villager.getAndSet(null);
                if (trader != null) {
                    trader.setTradingPlayer(null);
                    trader.discard();
                }
            });
        }

        private StepResult fail(SelfTest.StepContext ctx, String reason) {
            this.cleanUp(ctx);
            Screenshots.restoreWindow(ctx);
            return StepResult.fail(reason);
        }
    }
}
