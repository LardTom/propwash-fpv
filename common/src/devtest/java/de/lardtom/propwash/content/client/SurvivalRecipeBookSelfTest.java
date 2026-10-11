package de.lardtom.propwash.content.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.CraftingMaterial;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class SurvivalRecipeBookSelfTest {
    private static final String PHASE = "phase";
    private static final String FUTURE = "future";
    private static final String SINCE = "since";
    private static final String EXPECTED = "expected";
    private static final int CLIENT_SETTLE_TICKS = 10;
    private static final int PHASE_TICKS = 160;
    private static final String SEARCH_MOD = Constants.MOD_ID;
    private static final String SEARCH_PREFIX = Constants.MOD_ID.substring(0, 5);
    private static final Set<Identifier> BUILDING = Set.of(ContentIds.GATE_SMALL, ContentIds.GATE_MEDIUM, ContentIds.GATE_LARGE, ContentIds.RACE_FLAG,
            ContentIds.SLALOM_POLE, ContentIds.DIVE_CUBE, ContentIds.FREEFORM_GATE, ContentIds.GATE_POLE, ContentIds.SIGNAL_REPEATER);
    private static final Set<Identifier> MISC = Set.of(ContentIds.DRONE_WORKBENCH, ContentIds.BATTERY_CHARGER);
    private static final AtomicReference<Snapshot> SNAPSHOT = new AtomicReference<>();
    private static final AtomicReference<Backup> BACKUP = new AtomicReference<>();

    private enum Phase { RESET, CLIENT_EMPTY, COPPER, WORKBENCH, CLIENT_ALL }

    private record Snapshot(Set<Identifier> known, boolean holdsCopper, Set<Identifier> copperRecipes, Set<Identifier> all) {
    }

    private record Backup(GameType mode, List<ItemStack> items) {
    }

    private SurvivalRecipeBookSelfTest() {
    }

    public static void register() {
        SelfTest.clientStep("survival: Rezeptbuch im Überlebensmodus – Zutaten, Werkbank, Reiter, Suche", 900,
                SurvivalRecipeBookSelfTest::tick);
        SelfTest.clientStep("survival: Rezeptbuch-Test aufräumen", 100, SurvivalRecipeBookSelfTest::cleanup);
    }

    private static StepResult tick(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        Phase phase = (Phase) state.getOrDefault(PHASE, Phase.RESET);
        int since = (Integer) state.getOrDefault(SINCE, 0);
        if (ctx.tick() - since > PHASE_TICKS) {
            return StepResult.fail("Zeitüberschreitung in Phase " + phase + ": " + describe(SNAPSHOT.get()));
        }
        return switch (phase) {
            case RESET -> reset(ctx);
            case CLIENT_EMPTY -> clientEmpty(ctx);
            case COPPER -> copper(ctx);
            case WORKBENCH -> workbench(ctx);
            case CLIENT_ALL -> clientAll(ctx);
        };
    }

    private static void next(SelfTest.StepContext ctx, Phase phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        ctx.state().remove(FUTURE);
    }

    private static StepResult reset(SelfTest.StepContext ctx) {
        if (!ctx.state().containsKey(FUTURE)) {
            SNAPSHOT.set(null);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                Inventory inventory = player.getInventory();
                if (BACKUP.get() == null) {
                    List<ItemStack> items = new ArrayList<>();
                    for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                        items.add(inventory.getItem(slot).copy());
                    }
                    BACKUP.set(new Backup(player.gameMode(), items));
                }
                player.setGameMode(GameType.SURVIVAL);
                inventory.clearContent();
                player.containerMenu.setCarried(ItemStack.EMPTY);
                for (AdvancementHolder advancement : server.getAdvancements().getAllAdvancements()) {
                    if (isRecipeAdvancement(advancement)) {
                        for (String criterion : advancement.value().criteria().keySet()) {
                            player.getAdvancements().revoke(advancement, criterion);
                        }
                    }
                }
                player.resetRecipes(propwashRecipes(server));
                SNAPSHOT.set(snapshot(server, player));
            }));
            return StepResult.pending();
        }
        StepResult server = await(ctx);
        if (server != null) {
            return server;
        }
        Snapshot snapshot = SNAPSHOT.get();
        if (!snapshot.known().isEmpty()) {
            return StepResult.fail("frischer Spieler kennt schon Propwash-Rezepte: " + snapshot.known());
        }
        next(ctx, Phase.CLIENT_EMPTY);
        return StepResult.pending();
    }

    private static StepResult clientEmpty(SelfTest.StepContext ctx) {
        if (ctx.tick() - (Integer) ctx.state().get(SINCE) < CLIENT_SETTLE_TICKS) {
            return StepResult.pending();
        }
        Set<Identifier> shown = clientResults();
        if (!shown.isEmpty()) {
            return StepResult.fail("Rezeptbuch des Clients zeigt nach dem Zurücksetzen noch " + shown);
        }
        next(ctx, Phase.COPPER);
        ctx.state().put(FUTURE, ctx.onServer(server -> {
            ServerPlayer player = player(server);
            ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.COPPER_INGOT));
            drop.setNoPickUpDelay();
            player.level().addFreshEntity(drop);
        }));
        return StepResult.pending();
    }

    private static StepResult copper(SelfTest.StepContext ctx) {
        StepResult server = poll(ctx);
        if (server != null) {
            return server;
        }
        Snapshot snapshot = SNAPSHOT.get();
        if (snapshot == null || !snapshot.holdsCopper() || !snapshot.known().containsAll(snapshot.copperRecipes())) {
            return StepResult.pending();
        }
        if (snapshot.copperRecipes().isEmpty()) {
            return StepResult.fail("kein Propwash-Rezept nutzt Kupferbarren");
        }
        Set<Identifier> extra = new TreeSet<>(snapshot.known());
        extra.removeAll(snapshot.copperRecipes());
        if (!extra.isEmpty()) {
            return StepResult.fail("Kupferbarren schaltet fremde Rezepte frei: " + extra);
        }
        if (snapshot.known().contains(Constants.id(CraftingMaterial.HD_CHIP.key()))) {
            return StepResult.fail("HD-Videochip ohne passende Zutat freigeschaltet");
        }
        ctx.state().put(EXPECTED, snapshot.all());
        next(ctx, Phase.WORKBENCH);
        ctx.state().put(FUTURE, ctx.onServer(server2 -> {
            ServerPlayer player = player(server2);
            ContentIds.item(ContentIds.DRONE_WORKBENCH).ifPresent(item -> player.getInventory().add(new ItemStack(item)));
        }));
        return StepResult.pending();
    }

    private static StepResult workbench(SelfTest.StepContext ctx) {
        StepResult server = poll(ctx);
        if (server != null) {
            return server;
        }
        Snapshot snapshot = SNAPSHOT.get();
        if (snapshot == null || !snapshot.known().containsAll(snapshot.all())) {
            return StepResult.pending();
        }
        Set<Identifier> expected = craftable();
        if (!snapshot.all().equals(expected)) {
            Set<Identifier> missing = new TreeSet<>(expected);
            missing.removeAll(snapshot.all());
            return StepResult.fail("Propwash-Rezepte fehlen im Spiel: " + missing);
        }
        next(ctx, Phase.CLIENT_ALL);
        return StepResult.pending();
    }

    private static StepResult clientAll(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Set<Identifier> expected = craftable();
        Set<Identifier> shown = clientResults();
        if (!shown.containsAll(expected)) {
            return StepResult.pending();
        }
        List<String> problems = new ArrayList<>();
        ContextMap context = SlotDisplayContext.fromLevel(mc.level);
        for (RecipeDisplayEntry entry : clientEntries()) {
            for (ItemStack stack : entry.resultItems(context)) {
                Identifier id = itemId(stack);
                if (id != null && Constants.MOD_ID.equals(id.getNamespace()) && !entry.category().equals(expectedCategory(id))) {
                    problems.add(id + " im Reiter " + entry.category());
                }
            }
        }
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            return StepResult.fail("keine Verbindung");
        }
        for (String query : List.of(SEARCH_MOD, SEARCH_PREFIX, Constants.MOD_ID + ":")) {
            Set<Identifier> found = results(connection.searchTrees().recipes().search(query));
            if (!found.containsAll(expected)) {
                Set<Identifier> missing = new TreeSet<>(expected);
                missing.removeAll(found);
                problems.add("Suche „" + query + "“ findet nicht " + missing);
            }
        }
        Identifier coil = Constants.id(CraftingMaterial.COPPER_COIL.key());
        String name = Component.translatable("item." + Constants.MOD_ID + "." + coil.getPath()).getString().toLowerCase(Locale.ROOT);
        if (!results(connection.searchTrees().recipes().search(name)).contains(coil)) {
            problems.add("Suche nach dem Namen „" + name + "“ findet die Kupferspule nicht");
        }
        return StepResult.check(problems.isEmpty(), String.join("; ", problems));
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                Backup backup = BACKUP.getAndSet(null);
                player.getInventory().clearContent();
                player.awardRecipes(propwashRecipes(server));
                if (backup != null) {
                    for (int slot = 0; slot < backup.items().size(); slot++) {
                        player.getInventory().setItem(slot, backup.items().get(slot));
                    }
                    player.setGameMode(backup.mode());
                } else {
                    player.setGameMode(GameType.CREATIVE);
                }
                for (ItemEntity drop : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8.0),
                        entity -> entity.getItem().is(Items.COPPER_INGOT))) {
                    drop.discard();
                }
            }));
            return StepResult.pending();
        }
        StepResult server = await(ctx);
        return server != null ? server : StepResult.pass();
    }

    private static StepResult await(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future == null || !future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Schritt fehlgeschlagen: " + future.exceptionNow());
        }
        return null;
    }

    private static StepResult poll(SelfTest.StepContext ctx) {
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (future != null && !future.isDone()) {
            return StepResult.pending();
        }
        if (future != null && future.isCompletedExceptionally()) {
            return StepResult.fail("Server-Schritt fehlgeschlagen: " + future.exceptionNow());
        }
        ctx.state().put(FUTURE, ctx.onServer(server -> SNAPSHOT.set(snapshot(server, player(server)))));
        return null;
    }

    private static Snapshot snapshot(MinecraftServer server, ServerPlayer player) {
        Set<Identifier> known = new TreeSet<>();
        Set<Identifier> copper = new TreeSet<>();
        Set<Identifier> all = new TreeSet<>();
        ItemStack copperIngot = new ItemStack(Items.COPPER_INGOT);
        for (RecipeHolder<?> holder : propwashRecipes(server)) {
            Identifier id = holder.id().identifier();
            all.add(id);
            if (player.getRecipeBook().contains(holder.id())) {
                known.add(id);
            }
            for (Ingredient ingredient : holder.value().placementInfo().ingredients()) {
                if (ingredient.test(copperIngot)) {
                    copper.add(id);
                }
            }
        }
        return new Snapshot(known, player.getInventory().contains(copperIngot), copper, all);
    }

    private static List<RecipeHolder<?>> propwashRecipes(MinecraftServer server) {
        return server.getRecipeManager().getRecipes().stream()
                .filter(holder -> Constants.MOD_ID.equals(holder.id().identifier().getNamespace()) && !holder.value().isSpecial())
                .toList();
    }

    private static boolean isRecipeAdvancement(AdvancementHolder advancement) {
        Identifier id = advancement.id();
        return Constants.MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith("recipes/");
    }

    private static Set<Identifier> craftable() {
        return new TreeSet<>(SurvivalSelfTests.craftableIds());
    }

    private static RecipeBookCategory expectedCategory(Identifier id) {
        for (CraftingMaterial material : CraftingMaterial.values()) {
            if (Constants.id(material.key()).equals(id)) {
                return RecipeBookCategories.CRAFTING_MISC;
            }
        }
        if (MISC.contains(id)) {
            return RecipeBookCategories.CRAFTING_MISC;
        }
        return BUILDING.contains(id) ? RecipeBookCategories.CRAFTING_BUILDING_BLOCKS : RecipeBookCategories.CRAFTING_EQUIPMENT;
    }

    private static List<RecipeDisplayEntry> clientEntries() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return List.of();
        }
        List<RecipeDisplayEntry> entries = new ArrayList<>();
        for (RecipeCollection collection : mc.player.getRecipeBook().getCollections()) {
            entries.addAll(collection.getRecipes());
        }
        return entries;
    }

    private static Set<Identifier> clientResults() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return Set.of();
        }
        Set<Identifier> out = new TreeSet<>();
        ContextMap context = SlotDisplayContext.fromLevel(mc.level);
        for (RecipeDisplayEntry entry : clientEntries()) {
            for (ItemStack stack : entry.resultItems(context)) {
                Identifier id = itemId(stack);
                if (id != null && Constants.MOD_ID.equals(id.getNamespace())) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    private static Set<Identifier> results(List<RecipeCollection> collections) {
        Minecraft mc = Minecraft.getInstance();
        Set<Identifier> out = new HashSet<>();
        if (mc.level == null) {
            return out;
        }
        ContextMap context = SlotDisplayContext.fromLevel(mc.level);
        for (RecipeCollection collection : collections) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                for (ItemStack stack : entry.resultItems(context)) {
                    Identifier id = itemId(stack);
                    if (id != null) {
                        out.add(id);
                    }
                }
            }
        }
        return out;
    }

    private static @Nullable Identifier itemId(ItemStack stack) {
        return stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private static String describe(Snapshot snapshot) {
        if (snapshot == null) {
            return "kein Server-Stand";
        }
        Map<String, Object> out = new HashMap<>();
        out.put("bekannt", snapshot.known().size());
        out.put("Kupfer im Inventar", snapshot.holdsCopper());
        out.put("Kupfer-Rezepte", snapshot.copperRecipes());
        Set<Identifier> missing = new TreeSet<>(snapshot.all());
        missing.removeAll(snapshot.known());
        out.put("unbekannt", missing);
        return out.toString();
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }
}
