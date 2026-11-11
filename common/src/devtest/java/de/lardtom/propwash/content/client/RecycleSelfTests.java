package de.lardtom.propwash.content.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ContentBlocks;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.content.PartCondition;
import de.lardtom.propwash.content.RecycleRecipes;
import de.lardtom.propwash.content.Recycling;
import de.lardtom.propwash.content.WorkbenchTab;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.ShowcaseTour;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class RecycleSelfTests {
    public static final String GROUP = "recyceln";
    private static final String FUTURE = "recycle_future";
    private static final String RESULT = "recycle_result";
    private static final String PHASE = "recycle_phase";
    private static final String SINCE = "recycle_since";
    private static final String GERMAN = "de_de";
    private static final long SEED = 20261029L;
    private static final int WAIT = 200;
    private static final long DAY = 6000L;
    private static final long NIGHT = 18000L;
    private static final int SCREEN_MOTORS = 4;
    private static final String PROBLEM = "recycle_problem";
    private static final int DRONE_SLOT = DroneWorkbenchMenu.INV_START + 1;
    private static final AtomicReference<@Nullable List<ItemStack>> INVENTORY = new AtomicReference<>();
    private static final String HOME_TARGET = "recycle_home";
    private static final int AWAY = 48;
    private static final AtomicReference<@Nullable Spot> HOME = new AtomicReference<>();
    private static final AtomicReference<@Nullable Vec3> AWAY_SPOT = new AtomicReference<>();
    private static final AtomicReference<@Nullable Spot> BACK = new AtomicReference<>();

    private record Spot(ResourceKey<Level> dimension, Vec3 position, float yRot, float xRot) {
        static Spot of(ServerPlayer player) {
            return new Spot(player.level().dimension(), player.position(), player.getYRot(), player.getXRot());
        }

        void apply(MinecraftServer server, ServerPlayer player) {
            ServerLevel level = server.getLevel(this.dimension);
            player.teleportTo(level == null ? player.level() : level, this.position.x, this.position.y, this.position.z, Set.of(),
                    this.yRot, this.xRot, true);
        }
    }

    private RecycleSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + ": Stufen und Materialien aus dem Rezept, recycelt wird nur per Knopf", 200,
                ctx -> serverStep(ctx, RecycleSelfTests::checkTiers));
        SelfTest.clientStep(GROUP + ": Ausgabe voll, nur so viele Teile wie passen, Schließen gibt alles zurück", 200,
                ctx -> serverStep(ctx, RecycleSelfTests::checkBuffer));
        SelfTest.clientStep(GROUP + ": Plätze des anderen Tabs inaktiv, Umschalt-Klick je Tab", 200,
                ctx -> serverStep(ctx, RecycleSelfTests::checkTabs));
        SelfTest.clientStep(GROUP + ": Rezeptwahl – erstes nach Rezept-ID, nie mit dem Teil selbst als Zutat, Tag-Zutat als erstes Item", 200,
                ctx -> serverStep(ctx, RecycleSelfTests::checkRecipeChoice));
        SelfTest.clientStep(GROUP + ": Teile aller geladenen Add-ons", 400, ctx -> serverStep(ctx, RecycleSelfTests::checkAddons));
        SelfTest.clientStep(GROUP + ": Spieler weit weg von der Stelle der Werkbank (im vollen Lauf nach den capture-Schritten)", 600,
                RecycleSelfTests::moveAway);
        SelfTest.clientStep(GROUP + ": Tab in der Werkbank – Wechsel per Paket, Vorschau, Knopf", 1200, RecycleSelfTests::checkScreen);
        SelfTest.clientStep(GROUP + ": Drohne im Tab Recyceln – Umschalt-Klick lässt sie liegen, Hinweis auf den Tab Bauen", 1200,
                RecycleSelfTests::checkDroneHint);
        SelfTest.clientStep(GROUP + ": Texte des Tabs und der Hinweise", 20, ctx -> checkTexts());
        if (Screenshots.enabled()) {
            Screenshots.step("bildschirmfotos: Werkbank Recyceln hell", "workbench_recycle", 40, new RecycleScene(DAY, 0.6F));
            Screenshots.step("bildschirmfotos: Werkbank Recyceln dunkel", "workbench_recycle_night", 40, new RecycleScene(NIGHT, 0.0F));
            Screenshots.step("bildschirmfotos: Werkbank Recyceln, GUI-Skala 2", "workbench_recycle_gui2", 40,
                    Screenshots.guiScale(2, new RecycleScene(DAY, 0.6F)));
            Screenshots.step("bildschirmfotos: Werkbank Recyceln, GUI-Skala 4", "workbench_recycle_gui4", 40,
                    Screenshots.guiScale(4, new RecycleScene(DAY, 0.6F)));
            String back = "bildschirmfotos: Werkbank Recyceln – Sprache zurückstellen";
            SelfTest.switchStep("bildschirmfotos: Werkbank Recyceln – Sprache Deutsch", back, 1200, ctx -> Screenshots.language(ctx, GERMAN));
            Screenshots.step("bildschirmfotos: Werkbank Recyceln deutsch", "workbench_recycle_de", 40, new RecycleScene(DAY, 0.6F));
            Screenshots.step("bildschirmfotos: Werkbank Recyceln deutsch, Tab Bauen", "workbench_build_tab_de", 40, new BuildScene());
            SelfTest.clientStep(back, 1200, Screenshots::restoreLanguage);
        }
        SelfTest.clientStep(GROUP + ": Spieler zurück an seine Stelle vor dem Test", 600, RecycleSelfTests::moveBack);
    }

    public static Screenshots.Scene scene() {
        return new RecycleScene(-1L, () -> part(MotorType.M2207_1750, 0.6F, 2));
    }

    public static Screenshots.Scene emptyScene() {
        return new RecycleScene(-1L, () -> ItemStack.EMPTY);
    }

    public static Screenshots.Scene propScene() {
        return new RecycleScene(-1L, () -> part(PropType.P51_3B, 0.4F, 16));
    }

    @FunctionalInterface
    private interface ServerCheck {
        List<String> run(MinecraftServer server);
    }

    @SuppressWarnings("unchecked")
    private static StepResult serverStep(SelfTest.StepContext ctx, ServerCheck check) {
        if (ctx.tick() == 0) {
            AtomicReference<List<String>> result = new AtomicReference<>();
            ctx.state().put(RESULT, result);
            ctx.state().put(FUTURE, ctx.onServer(server -> result.set(check.run(server))));
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

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static DroneWorkbenchMenu menu(ServerPlayer player, WorkbenchTab tab) {
        DroneWorkbenchMenu menu = new DroneWorkbenchMenu(0, player.getInventory(), ContainerLevelAccess.create(player.level(), player.blockPosition()));
        if (!menu.clickMenuButton(player, DroneWorkbenchMenu.tabButton(tab)) || menu.tab() != tab) {
            throw new IllegalStateException("Tab " + tab + " lässt sich nicht wählen");
        }
        return menu;
    }

    public static ItemStack part(PartInfo part, float condition, int count) {
        return PartCondition.with(ContentItems.stack(part), condition).copyWithCount(count);
    }

    private static Optional<Recycling.Materials> recipe(MinecraftServer server, ItemStack stack) {
        return RecycleRecipes.find(server.getRecipeManager(), stack.getItem());
    }

    private static List<String> checkTiers(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        ServerPlayer player = player(server);
        List<ItemStack> saved = snapshot(player.getInventory());
        try {
            recycles(problems, server, player, "neuer Motor", part(MotorType.M2207_1750, 1.0F, 1), Recycling.FULL);
            recycles(problems, server, player, "zwei Motoren mit 60 %", part(MotorType.M2207_1750, 0.6F, 2), Recycling.HALF);
            recycles(problems, server, player, "kaputter Motor", part(MotorType.M2207_1750, 0.0F, 1), Recycling.QUARTER);
            recycles(problems, server, player, "vier neue Props", part(PropType.P51_3B, 1.0F, 4), Recycling.FULL);
            recycles(problems, server, player, "kaputter Prop", part(PropType.P51_3B, 0.0F, 1), Recycling.QUARTER);
            recycles(problems, server, player, "Akku mit 30 %", part(BatteryType.B6S_1300, 0.3F, 1), Recycling.QUARTER);
            recycles(problems, server, player, "Rahmen mit 70 %", part(FrameType.FREESTYLE_5, 0.7F, 1), Recycling.HALF);
            recycles(problems, server, player, "kaputter Whoop-Rahmen", part(FrameType.WHOOP_65, 0.0F, 1), Recycling.QUARTER);
            expectPerPiece(problems, server, part(MotorType.M2207_1750, 1.0F, 1), Map.of(Items.IRON_INGOT, 1.0, Items.REDSTONE, 0.5));
            expectPerPiece(problems, server, part(PropType.P51_3B, 1.0F, 1), Map.of(Items.DRIED_KELP, 0.75, Items.IRON_NUGGET, 0.25));
            expectPerPiece(problems, server, part(FrameType.FREESTYLE_5, 1.0F, 1), Map.of(Items.IRON_INGOT, 1.0));
            refuses(problems, server, player, "Kreativ-Flugcontroller ohne Rezept und ohne Ausbeute", part(StackType.CREATIVE_FC, 0.0F, 1),
                    Recycling.Status.NOT_RECYCLABLE);
            refuses(problems, server, player, "Teil eines fehlenden Add-ons", SalvageSelfTests.missingPart(), Recycling.Status.MISSING);
            DroneWorkbenchMenu menu = menu(player, WorkbenchTab.RECYCLE);
            if (menu.getSlot(DroneWorkbenchMenu.RECYCLE_INPUT).mayPlace(new ItemStack(Items.DIRT))
                    || menu.getSlot(DroneWorkbenchMenu.RECYCLE_INPUT).mayPlace(DroneAssembly.preset(DroneBuild.FREESTYLE))) {
                problems.add("Recycle-Eingang nimmt Erde oder eine Drohne");
            }
        } finally {
            restore(player.getInventory(), saved);
        }
        return problems;
    }

    private static void recycles(List<String> problems, MinecraftServer server, ServerPlayer player, String what, ItemStack stack, int tier) {
        player.getInventory().clearContent();
        DroneWorkbenchMenu menu = menu(player, WorkbenchTab.RECYCLE);
        if (Recycling.tier(stack) != tier) {
            problems.add(what + ": Stufe " + Recycling.tier(stack) + " statt " + tier);
        }
        menu.setCarried(stack.copy());
        menu.clicked(DroneWorkbenchMenu.RECYCLE_INPUT, 0, ContainerInput.PICKUP, player);
        if (!menu.getCarried().isEmpty() || !ItemStack.matches(menu.recycleInput(), stack)) {
            problems.add(what + ": nicht im Recycle-Eingang (Maus " + menu.getCarried() + ")");
            return;
        }
        if (menu.recycleOutput().stream().anyMatch(item -> !item.isEmpty()) || !player.getInventory().isEmpty()) {
            problems.add(what + ": wird schon beim Einlegen recycelt");
        }
        Optional<Recycling.Materials> fromRecipe = recipe(server, stack);
        if (fromRecipe.isEmpty()) {
            problems.add(what + ": kein Crafting-Rezept gefunden");
            return;
        }
        if (menu.recycleStatus() != Recycling.Status.READY || !menu.recycleMaterials().equals(fromRecipe)) {
            problems.add(what + ": Vorschau " + menu.recycleStatus() + " " + menu.recycleMaterials() + " statt " + fromRecipe.get());
            return;
        }
        Recycling.Materials materials = fromRecipe.get();
        int pieces = stack.getCount();
        if (!menu.clickMenuButton(player, DroneWorkbenchMenu.BUTTON_RECYCLE)) {
            problems.add(what + ": Knopf Recyceln tut nichts");
            return;
        }
        if (!menu.recycleInput().isEmpty()) {
            problems.add(what + ": Eingang nicht leer (" + menu.recycleInput() + ")");
        }
        Map<Item, Integer> got = totals(menu.recycleOutput());
        for (int i = 0; i < materials.entries().size(); i++) {
            Item item = materials.item(i).orElseThrow();
            int amount = got.getOrDefault(item, 0);
            int least = materials.least(i, pieces, tier);
            int most = materials.most(i, pieces, tier);
            if (amount < least || amount > most) {
                problems.add(what + ": " + amount + " × " + BuiltInRegistries.ITEM.getKey(item) + " statt " + least + "–" + most);
            }
            got.remove(item);
        }
        got.entrySet().removeIf(entry -> entry.getValue() == 0);
        if (!got.isEmpty()) {
            problems.add(what + ": fremde Materialien " + got);
        }
    }

    private static void expectPerPiece(List<String> problems, MinecraftServer server, ItemStack stack, Map<Item, Double> expected) {
        Optional<Recycling.Materials> materials = recipe(server, stack);
        if (materials.isEmpty()) {
            problems.add(stack.getItem() + ": kein Rezept");
            return;
        }
        for (Map.Entry<Item, Double> entry : expected.entrySet()) {
            Identifier id = BuiltInRegistries.ITEM.getKey(entry.getKey());
            int index = indexOf(materials.get(), id);
            double perPiece = index < 0 ? 0.0 : materials.get().expected(index, 1, Recycling.FULL);
            if (Math.abs(perPiece - entry.getValue()) > 1.0E-9) {
                problems.add(BuiltInRegistries.ITEM.getKey(stack.getItem()) + ": " + perPiece + " × " + id + " je Stück statt " + entry.getValue());
            }
        }
    }

    private static int indexOf(Recycling.Materials materials, Identifier id) {
        for (int i = 0; i < materials.entries().size(); i++) {
            if (materials.entries().get(i).item().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static void refuses(List<String> problems, MinecraftServer server, ServerPlayer player, String what, ItemStack stack,
                                Recycling.Status expected) {
        player.getInventory().clearContent();
        DroneWorkbenchMenu menu = menu(player, WorkbenchTab.RECYCLE);
        menu.getSlot(DroneWorkbenchMenu.RECYCLE_INPUT).set(stack.copy());
        if (menu.recycleStatus() != expected) {
            problems.add(what + ": Status " + menu.recycleStatus() + " statt " + expected);
        }
        if (menu.clickMenuButton(player, DroneWorkbenchMenu.BUTTON_RECYCLE) || menu.recycleOutput().stream().anyMatch(item -> !item.isEmpty())
                || !ItemStack.matches(menu.recycleInput(), stack)) {
            problems.add(what + ": wird trotzdem recycelt");
        }
    }

    private static List<String> checkBuffer(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        ServerPlayer player = player(server);
        List<ItemStack> saved = snapshot(player.getInventory());
        try {
            player.getInventory().clearContent();
            DroneWorkbenchMenu full = menu(player, WorkbenchTab.RECYCLE);
            for (int slot = 0; slot < DroneWorkbenchMenu.RECYCLE_OUTPUT_SLOTS; slot++) {
                full.getSlot(DroneWorkbenchMenu.RECYCLE_OUTPUT + slot).set(new ItemStack(Items.DIRT, 64));
            }
            ItemStack broken = part(MotorType.M2207_1750, 0.0F, 1);
            full.getSlot(DroneWorkbenchMenu.RECYCLE_INPUT).set(broken.copy());
            if (full.recyclePieces() != 0 || full.clickMenuButton(player, DroneWorkbenchMenu.BUTTON_RECYCLE)
                    || !ItemStack.matches(full.recycleInput(), broken)) {
                problems.add("volle Ausgabe: Motor wird trotzdem recycelt");
            }
            player.getInventory().clearContent();
            DroneWorkbenchMenu partial = menu(player, WorkbenchTab.RECYCLE);
            for (int slot = 0; slot < 6; slot++) {
                partial.getSlot(DroneWorkbenchMenu.RECYCLE_OUTPUT + slot).set(new ItemStack(Items.DIRT, 64));
            }
            Item coil = BuiltInRegistries.ITEM.getValue(Constants.id("copper_coil"));
            partial.getSlot(DroneWorkbenchMenu.RECYCLE_OUTPUT + 6).set(new ItemStack(coil, 60));
            ItemStack motors = part(MotorType.M2207_1750, 1.0F, 16);
            partial.getSlot(DroneWorkbenchMenu.RECYCLE_INPUT).set(motors.copy());
            int pieces = partial.recyclePieces();
            if (pieces != 2) {
                problems.add("teilweise volle Ausgabe: " + pieces + " statt 2 Motoren passen");
            }
            if (!partial.recycle(RandomSource.create(SEED)) || partial.recycleInput().getCount() != 14) {
                problems.add("teilweise volle Ausgabe: im Eingang bleiben " + partial.recycleInput().getCount() + " statt 14 Motoren");
            }
            Map<Item, Integer> got = totals(partial.recycleOutput());
            if (got.getOrDefault(Items.IRON_INGOT, 0) != 2 || got.getOrDefault(Items.REDSTONE, 0) != 1 || got.getOrDefault(coil, 0) != 63) {
                problems.add("teilweise volle Ausgabe: Ausbeute " + got);
            }
            partial.removed(player);
            Inventory inventory = player.getInventory();
            if (inventory.countItem(motors.getItem()) != 14 || inventory.countItem(Items.IRON_INGOT) != 2 || inventory.countItem(coil) != 63
                    || inventory.countItem(Items.DIRT) != 6 * 64) {
                problems.add("Schließen gibt nicht alles zurück: Motoren " + inventory.countItem(motors.getItem()) + ", Eisen "
                        + inventory.countItem(Items.IRON_INGOT) + ", Spulen " + inventory.countItem(coil) + ", Erde " + inventory.countItem(Items.DIRT));
            }
        } finally {
            restore(player.getInventory(), saved);
        }
        return problems;
    }

    private static List<String> checkTabs(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        ServerPlayer player = player(server);
        List<ItemStack> saved = snapshot(player.getInventory());
        try {
            player.getInventory().clearContent();
            DroneWorkbenchMenu menu = menu(player, WorkbenchTab.BUILD);
            Slot input = menu.getSlot(DroneWorkbenchMenu.RECYCLE_INPUT);
            Slot output = menu.getSlot(DroneWorkbenchMenu.RECYCLE_OUTPUT);
            Slot motorSlot = menu.getSlot(BuildSlots.MOTOR);
            Slot salvage = menu.getSlot(DroneWorkbenchMenu.SALVAGE_SLOT);
            ItemStack motor = part(MotorType.M2207_1750, 0.0F, 1);
            if (input.isActive() || input.mayPlace(motor) || !motorSlot.isActive() || !motorSlot.mayPlace(motor)) {
                problems.add("Tab Bauen: Recycle-Eingang aktiv oder Motor-Platz inaktiv");
            }
            int inventorySlot = DroneWorkbenchMenu.INV_START;
            menu.getSlot(inventorySlot).set(motor.copy());
            menu.quickMoveStack(player, inventorySlot);
            if (!input.getItem().isEmpty() || !motorSlot.hasItem() || salvage.hasItem()) {
                problems.add("Tab Bauen: Umschalt-Klick legt einen kaputten Motor nicht auf den Motor-Platz");
            }
            menu.clickMenuButton(player, DroneWorkbenchMenu.BUTTON_RECYCLE_TAB);
            if (motorSlot.isActive() || motorSlot.mayPickup(player) || motorSlot.mayPlace(motor) || salvage.isActive()
                    || salvage.mayPlace(DroneAssembly.preset(DroneBuild.FREESTYLE)) || menu.getSlot(DroneWorkbenchMenu.RESULT_SLOT).mayPickup(player)) {
                problems.add("Tab Recyceln: Bau-Plätze, Ergebnis oder Zerlegen-Feld aktiv");
            }
            if (!input.isActive() || !input.mayPlace(motor) || !output.isActive() || output.mayPlace(new ItemStack(Items.DIRT))) {
                problems.add("Tab Recyceln: Eingang inaktiv oder Ausgabe nimmt Gegenstände an");
            }
            menu.clicked(BuildSlots.MOTOR, 0, ContainerInput.PICKUP, player);
            if (!menu.getCarried().isEmpty() || !motorSlot.hasItem()) {
                problems.add("Tab Recyceln: Motor lässt sich aus dem inaktiven Motor-Platz nehmen");
            }
            menu.setCarried(ItemStack.EMPTY);
            menu.getSlot(inventorySlot).set(part(MotorType.M2207_1750, 0.3F, 3));
            menu.quickMoveStack(player, inventorySlot);
            if (input.getItem().getCount() != 3 || menu.getSlot(inventorySlot).hasItem()) {
                problems.add("Tab Recyceln: Umschalt-Klick legt Teile nicht in den Recycle-Eingang (" + input.getItem() + ")");
            }
            menu.getSlot(inventorySlot + 1).set(DroneAssembly.preset(DroneBuild.FREESTYLE));
            menu.quickMoveStack(player, inventorySlot + 1);
            if (salvage.hasItem() || DroneAssembly.isDrone(input.getItem())) {
                problems.add("Tab Recyceln: Umschalt-Klick legt eine Drohne ins Zerlegen-Feld oder in den Eingang");
            }
            if (!DroneAssembly.isDrone(menu.getSlot(inventorySlot + 1).getItem())) {
                problems.add("Tab Recyceln: Umschalt-Klick verschiebt eine Drohne stumm im Inventar, statt sie liegen zu lassen");
            }
            menu.quickMoveStack(player, DroneWorkbenchMenu.RECYCLE_INPUT);
            if (input.hasItem()) {
                problems.add("Tab Recyceln: Umschalt-Klick auf den Eingang gibt die Teile nicht zurück");
            }
            menu.clickMenuButton(player, DroneWorkbenchMenu.BUTTON_BUILD);
            menu.getSlot(inventorySlot + 2).set(part(MotorType.M2207_1750, 0.0F, 1));
            menu.quickMoveStack(player, inventorySlot + 2);
            if (input.hasItem() || salvage.hasItem()) {
                problems.add("Tab Bauen: Umschalt-Klick legt einen kaputten Motor in den Recycle-Eingang oder ins Zerlegen-Feld");
            }
            if (menu.clickMenuButton(player, DroneWorkbenchMenu.BUTTON_RECYCLE)) {
                problems.add("Tab Bauen: Knopf Recyceln wirkt");
            }
        } finally {
            restore(player.getInventory(), saved);
        }
        return problems;
    }

    private static List<String> checkRecipeChoice(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        Item motor = ContentItems.stack(MotorType.M2207_1750).getItem();
        RecipeHolder<?> self = holder("recycletest:a_self", shapeless(motor, 1, Ingredient.of(motor), Ingredient.of(Items.DIAMOND)));
        RecipeHolder<?> normal = holder("recycletest:b_normal", shapeless(motor, 2, Ingredient.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS),
                Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT)));
        RecipeHolder<?> later = holder("recycletest:c_later", shapeless(motor, 1, Ingredient.of(Items.DIAMOND)));
        Optional<RecycleRecipes.Found> found = RecycleRecipes.scan(List.of(later, self, normal), motor);
        Recycling.Materials expected = new Recycling.Materials(List.of(new Recycling.Material(BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 1),
                new Recycling.Material(BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 2)), 2);
        if (found.isEmpty() || !found.get().recipe().equals(Identifier.parse("recycletest:b_normal")) || !found.get().materials().equals(expected)) {
            problems.add("Rezeptwahl: " + found + " statt recycletest:b_normal mit " + expected);
        }
        if (RecycleRecipes.scan(List.of(self), motor).isPresent()) {
            problems.add("Rezept mit dem Teil selbst als Zutat wird verwendet");
        }
        Item frame = ContentItems.stack(FrameType.FREESTYLE_5).getItem();
        if (RecycleRecipes.scan(List.of(normal), frame).isPresent()) {
            problems.add("Rezept für ein anderes Ergebnis wird verwendet");
        }
        return problems;
    }

    private static RecipeHolder<?> holder(String id, Recipe<?> recipe) {
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.parse(id)), recipe);
    }

    private static ShapelessRecipe shapeless(Item result, int count, Ingredient... ingredients) {
        return new ShapelessRecipe(new Recipe.CommonInfo(true), new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
                new ItemStackTemplate(result, count), List.of(ingredients));
    }

    private static List<String> checkAddons(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        ServerPlayer player = player(server);
        List<ItemStack> saved = snapshot(player.getInventory());
        Map<String, int[]> counts = new LinkedHashMap<>();
        try {
            for (PartInfo part : PartIndex.all()) {
                if (part.builtin()) {
                    continue;
                }
                ItemStack stack = ContentItems.stack(part);
                if (stack.isEmpty()) {
                    continue;
                }
                int[] count = counts.computeIfAbsent(part.id().getNamespace(), key -> new int[3]);
                count[0]++;
                for (float condition : new float[]{1.0F, 0.6F, 0.0F}) {
                    ItemStack worn = PartCondition.with(stack.copy(), condition);
                    Recycling.Plan plan = Recycling.plan(worn, item -> recipe(server, worn));
                    if (plan.status() != Recycling.Status.READY && plan.status() != Recycling.Status.NOT_RECYCLABLE) {
                        problems.add(part.id() + " bei " + condition + ": " + plan.status());
                    }
                    if (plan.status() == Recycling.Status.READY && !plan.materials().map(Recycling.Materials::registered).orElse(false)) {
                        problems.add(part.id() + ": Materialien ohne Item " + plan.materials());
                    }
                }
                if (recipe(server, stack).isPresent()) {
                    count[1]++;
                }
                player.getInventory().clearContent();
                DroneWorkbenchMenu menu = menu(player, WorkbenchTab.RECYCLE);
                ItemStack broken = PartCondition.with(stack.copy(), 0.0F);
                menu.getSlot(DroneWorkbenchMenu.RECYCLE_INPUT).set(broken.copy());
                if (menu.recycleStatus() == Recycling.Status.READY) {
                    if (!menu.clickMenuButton(player, DroneWorkbenchMenu.BUTTON_RECYCLE) || !menu.recycleInput().isEmpty()) {
                        problems.add(part.id() + ": lässt sich nicht recyceln");
                    } else {
                        count[2]++;
                    }
                } else if (menu.recycleStatus() != Recycling.Status.NOT_RECYCLABLE) {
                    problems.add(part.id() + ": Status " + menu.recycleStatus());
                }
            }
        } finally {
            restore(player.getInventory(), saved);
        }
        if (counts.isEmpty()) {
            problems.add("keine Add-on-Teile geladen");
        }
        StringBuilder log = new StringBuilder();
        counts.forEach((namespace, count) -> log.append(namespace).append(": ").append(count[0]).append(" Teile, ").append(count[1])
                .append(" mit Rezept, ").append(count[2]).append(" recycelt; "));
        Constants.LOG.info("recyceln-Selbsttest: {}", log);
        return problems;
    }

    private static StepResult checkScreen(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        int since = (int) state.getOrDefault(SINCE, 0);
        int waited = ctx.tick() - since;
        switch (phase) {
            case 0 -> {
                state.put(FUTURE, ctx.onServer(server -> {
                    INVENTORY.set(snapshot(player(server).getInventory()));
                    openBench(server, null, ItemStack.EMPTY, false);
                }));
                return next(ctx, 1);
            }
            case 1 -> {
                StepResult server = awaitServer(ctx);
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (!(mc.gui.screen() instanceof DroneWorkbenchScreen screen) || screen.tabButtons().size() != 2) {
                    return waited > WAIT ? StepResult.fail("Werkbank-Screen fehlt: " + mc.gui.screen()) : StepResult.pending();
                }
                if (screen.getMenu().tab() != WorkbenchTab.BUILD || screen.recycleButton() == null || screen.recycleButton().visible) {
                    return StepResult.fail("Werkbank öffnet nicht im Tab Bauen");
                }
                screen.tabButtons().get(WorkbenchTab.RECYCLE.ordinal()).press();
                state.remove(FUTURE);
                return next(ctx, 2);
            }
            case 2 -> {
                if (!(mc.gui.screen() instanceof DroneWorkbenchScreen screen)) {
                    return StepResult.fail("Werkbank geschlossen");
                }
                StepResult server = askServer(ctx, srv -> player(srv).containerMenu instanceof DroneWorkbenchMenu menu && menu.tab() == WorkbenchTab.RECYCLE);
                if (server.status() == StepResult.Status.PENDING) {
                    return server;
                }
                if (server.status() == StepResult.Status.FAIL) {
                    return waited > WAIT ? StepResult.fail("Server hat den Tab nicht gewechselt") : StepResult.pending();
                }
                DroneWorkbenchMenu menu = screen.getMenu();
                if (menu.tab() != WorkbenchTab.RECYCLE || menu.getSlot(BuildSlots.MOTOR).isActive() || menu.getSlot(DroneWorkbenchMenu.SALVAGE_SLOT).isActive()
                        || !menu.getSlot(DroneWorkbenchMenu.RECYCLE_INPUT).isActive() || screen.recycleButton() == null || !screen.recycleButton().visible) {
                    return StepResult.fail("Client: Plätze oder Knopf passen nicht zum Tab Recyceln");
                }
                ctx.onServer(srv -> {
                    if (player(srv).containerMenu instanceof DroneWorkbenchMenu open) {
                        open.getSlot(DroneWorkbenchMenu.RECYCLE_INPUT).set(part(MotorType.M2207_1750, 0.0F, SCREEN_MOTORS));
                        open.broadcastChanges();
                    }
                });
                return next(ctx, 3);
            }
            case 3 -> {
                if (!(mc.gui.screen() instanceof DroneWorkbenchScreen screen)) {
                    return StepResult.fail("Werkbank geschlossen");
                }
                DroneWorkbenchMenu menu = screen.getMenu();
                if (menu.recycleMaterials().isEmpty() || menu.recycleInput().getCount() != SCREEN_MOTORS) {
                    return waited > WAIT ? StepResult.fail("Client: keine Vorschau für vier kaputte Motoren (" + menu.recycleStatus() + ")")
                            : StepResult.pending();
                }
                if (waited < 10) {
                    return StepResult.pending();
                }
                if (menu.recycleOutput().stream().anyMatch(item -> !item.isEmpty())) {
                    return StepResult.fail("Client: schon beim Einlegen recycelt");
                }
                if (menu.recycleTier() != Recycling.QUARTER || menu.recyclePieces() != SCREEN_MOTORS || screen.recycleButton() == null
                        || !screen.recycleButton().active) {
                    return StepResult.fail("Client: Stufe " + menu.recycleTier() + ", " + menu.recyclePieces() + " Teile, Knopf aktiv "
                            + (screen.recycleButton() != null && screen.recycleButton().active));
                }
                if (!screen.pressRecycle()) {
                    return StepResult.fail("Knopf Recyceln lässt sich nicht drücken");
                }
                return next(ctx, 4);
            }
            case 4 -> {
                if (!(mc.gui.screen() instanceof DroneWorkbenchScreen screen)) {
                    return StepResult.fail("Werkbank geschlossen");
                }
                DroneWorkbenchMenu menu = screen.getMenu();
                boolean done = menu.recycleInput().isEmpty() && totals(menu.recycleOutput()).getOrDefault(Items.IRON_INGOT, 0) >= 1;
                if (!done) {
                    return waited > WAIT ? StepResult.fail("Client: nach dem Knopf keine Materialien (Eingang " + menu.recycleInput() + ")")
                            : StepResult.pending();
                }
                screen.tabButtons().get(WorkbenchTab.BUILD.ordinal()).press();
                return next(ctx, 5);
            }
            case 5 -> {
                if (!(mc.gui.screen() instanceof DroneWorkbenchScreen screen)) {
                    return StepResult.fail("Werkbank geschlossen");
                }
                if (screen.getMenu().tab() != WorkbenchTab.BUILD || screen.recycleButton() == null || screen.recycleButton().visible
                        || !screen.getMenu().getSlot(BuildSlots.MOTOR).isActive()) {
                    return StepResult.fail("Client: Tab Bauen nicht zurück");
                }
                ctx.onServer(server -> {
                    closeBench(server);
                    List<ItemStack> saved = INVENTORY.getAndSet(null);
                    if (saved != null) {
                        restore(player(server).getInventory(), saved);
                    }
                });
                return next(ctx, 6);
            }
            default -> {
                return mc.gui.screen() instanceof DroneWorkbenchScreen && waited < WAIT ? StepResult.pending() : StepResult.pass();
            }
        }
    }

    private static StepResult checkDroneHint(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        int waited = ctx.tick() - (int) state.getOrDefault(SINCE, 0);
        if (phase == 0) {
            state.put(FUTURE, ctx.onServer(server -> {
                INVENTORY.set(snapshot(player(server).getInventory()));
                player(server).getInventory().clearContent();
                DroneWorkbenchMenu menu = openBench(server, null, ItemStack.EMPTY, false);
                menu.getSlot(DRONE_SLOT).set(DroneAssembly.preset(DroneBuild.FREESTYLE));
                menu.broadcastChanges();
            }));
            return next(ctx, 1);
        }
        if (phase == 5) {
            Object problem = state.get(PROBLEM);
            if (problem != null) {
                return StepResult.fail(problem.toString());
            }
            return mc.gui.screen() instanceof DroneWorkbenchScreen && waited < WAIT ? StepResult.pending() : StepResult.pass();
        }
        if (phase == 4) {
            state.remove(FUTURE);
            ctx.onServer(server -> {
                closeBench(server);
                List<ItemStack> saved = INVENTORY.getAndSet(null);
                if (saved != null) {
                    restore(player(server).getInventory(), saved);
                }
            });
            return next(ctx, 5);
        }
        if (!(mc.gui.screen() instanceof DroneWorkbenchScreen screen)) {
            return phase == 1 && waited <= WAIT ? StepResult.pending() : giveUp(ctx, "Werkbank-Screen fehlt: " + mc.gui.screen());
        }
        DroneWorkbenchMenu menu = screen.getMenu();
        switch (phase) {
            case 1 -> {
                StepResult server = awaitServer(ctx);
                if (server.status() != StepResult.Status.PASS) {
                    return server;
                }
                if (screen.tabButtons().size() != 2 || !DroneAssembly.isDrone(menu.getSlot(DRONE_SLOT).getItem())) {
                    return waited > WAIT ? giveUp(ctx, "Client: keine Drohne im Inventar der Werkbank") : StepResult.pending();
                }
                screen.tabButtons().get(WorkbenchTab.RECYCLE.ordinal()).press();
                state.remove(FUTURE);
                return next(ctx, 2);
            }
            case 2 -> {
                StepResult server = askServer(ctx, srv -> player(srv).containerMenu instanceof DroneWorkbenchMenu open && open.tab() == WorkbenchTab.RECYCLE);
                if (server.status() == StepResult.Status.PENDING) {
                    return server;
                }
                if (server.status() == StepResult.Status.FAIL) {
                    return waited > WAIT ? giveUp(ctx, "Server hat den Tab nicht gewechselt") : StepResult.pending();
                }
                Slot slot = menu.getSlot(DRONE_SLOT);
                screen.mouseClicked(new MouseButtonEvent(screen.panelLeft() + slot.x + 8, screen.panelTop() + slot.y + 8,
                        new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, InputConstants.MOD_SHIFT)), false);
                RecycleView view = screen.recycleView();
                RecycleView.Status status = view == null ? null : view.statusLine();
                String key = status != null && status.text().getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "";
                if (status == null || !key.equals(RecycleView.DRONE) || status.color() != WorkbenchReport.COLOR_WARN) {
                    problem(ctx, "Umschalt-Klick auf eine Drohne zeigt keinen Hinweis auf den Tab Bauen (Statuszeile „"
                            + (status == null ? "" : status.text().getString()) + "“)");
                }
                return next(ctx, 3);
            }
            default -> {
                if (waited < 10) {
                    return StepResult.pending();
                }
                if (!state.containsKey(FUTURE) && (!DroneAssembly.isDrone(menu.getSlot(DRONE_SLOT).getItem())
                        || menu.slots.stream().anyMatch(other -> other.index != DRONE_SLOT && DroneAssembly.isDrone(other.getItem())))) {
                    problem(ctx, "Client: Umschalt-Klick im Tab Recyceln verschiebt die Drohne");
                }
                StepResult server = askServer(ctx, srv -> player(srv).containerMenu instanceof DroneWorkbenchMenu open
                        && DroneAssembly.isDrone(open.getSlot(DRONE_SLOT).getItem()) && open.recycleInput().isEmpty());
                if (server.status() == StepResult.Status.PENDING) {
                    return server;
                }
                if (server.status() == StepResult.Status.FAIL) {
                    problem(ctx, "Server: Umschalt-Klick im Tab Recyceln verschiebt die Drohne");
                }
                return next(ctx, 4);
            }
        }
    }

    private static void problem(SelfTest.StepContext ctx, String text) {
        ctx.state().merge(PROBLEM, text, (old, added) -> old + "; " + added);
    }

    private static StepResult giveUp(SelfTest.StepContext ctx, String text) {
        problem(ctx, text);
        return next(ctx, 4);
    }

    @SuppressWarnings("unchecked")
    private static StepResult askServer(SelfTest.StepContext ctx, Predicate<MinecraftServer> check) {
        Map<String, Object> state = ctx.state();
        Object pending = state.get(FUTURE);
        if (!(pending instanceof CompletableFuture<?> future)) {
            AtomicReference<Boolean> result = new AtomicReference<>(false);
            state.put(RESULT, result);
            state.put(FUTURE, ctx.onServer(server -> result.set(check.test(server))));
            return StepResult.pending();
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        state.remove(FUTURE);
        boolean ok = !future.isCompletedExceptionally() && ((AtomicReference<Boolean>) state.get(RESULT)).get();
        return ok ? StepResult.pass() : StepResult.fail("nein");
    }

    private static StepResult next(SelfTest.StepContext ctx, int phase) {
        ctx.state().put(PHASE, phase);
        ctx.state().put(SINCE, ctx.tick());
        return StepResult.pending();
    }

    private static StepResult awaitServer(SelfTest.StepContext ctx) {
        Object future = ctx.state().get(FUTURE);
        if (!(future instanceof CompletableFuture<?> pending)) {
            return StepResult.pass();
        }
        if (pending.isCompletedExceptionally()) {
            return StepResult.fail("Server: " + pending.exceptionNow());
        }
        return pending.isDone() ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult moveAway(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            AWAY_SPOT.set(null);
            state.put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                BlockPos bench = benchPos(server);
                HOME.compareAndSet(null, Spot.of(player));
                ServerLevel level = player.level();
                int x = bench.getX() + AWAY;
                int z = bench.getZ() - AWAY;
                level.getChunk(x >> 4, z >> 4);
                Vec3 at = new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
                player.teleportTo(level, at.x, at.y, at.z, Set.of(), player.getYRot(), player.getXRot(), true);
                AWAY_SPOT.set(at);
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx);
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        Vec3 at = AWAY_SPOT.get();
        if (at == null || mc.player == null || mc.level == null || Math.hypot(mc.player.getX() - at.x, mc.player.getZ() - at.z) > 1.0
                || !mc.level.isLoaded(BlockPos.containing(at))) {
            return ctx.tick() > 400 ? StepResult.fail("Spieler kommt nicht bei " + at + " an") : StepResult.pending();
        }
        BlockPos bench = ShowcaseTour.base().offset(0, 0, 2);
        ctx.log(String.format(Locale.ROOT, "Spieler bei %.1f %.1f %.1f, %.0f Blöcke von der Stelle der Werkbank %s", at.x, at.y, at.z,
                Math.sqrt(bench.distToCenterSqr(at)), bench.toShortString()));
        return StepResult.pass();
    }

    @SuppressWarnings("unchecked")
    private static StepResult moveBack(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(FUTURE)) {
            AtomicReference<@Nullable Spot> target = new AtomicReference<>();
            state.put(HOME_TARGET, target);
            state.put(FUTURE, ctx.onServer(server -> {
                Spot back = BACK.getAndSet(null);
                Spot home = HOME.getAndSet(null);
                Spot spot = home != null ? home : back;
                if (spot != null) {
                    spot.apply(server, player(server));
                    target.set(spot);
                }
            }));
            return StepResult.pending();
        }
        StepResult server = awaitServer(ctx);
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        Spot home = ((AtomicReference<@Nullable Spot>) state.get(HOME_TARGET)).get();
        if (home == null) {
            ctx.log("Spieler war nicht versetzt");
            return StepResult.pass();
        }
        Vec3 at = home.position();
        if (mc.player == null || mc.level == null || mc.level.dimension() != home.dimension()
                || Math.hypot(mc.player.getX() - at.x, mc.player.getZ() - at.z) > 1.0) {
            return ctx.tick() > 400 ? StepResult.fail("Spieler kommt nicht zurück nach " + at) : StepResult.pending();
        }
        ctx.log(String.format(Locale.ROOT, "Spieler zurück bei %.1f %.1f %.1f", at.x, at.y, at.z));
        return StepResult.pass();
    }

    private static BlockPos benchPos(MinecraftServer server) {
        ShowcaseTour.ensureBase(server);
        return ShowcaseTour.base().offset(0, 0, 2);
    }

    private static DroneWorkbenchMenu openBench(MinecraftServer server, @Nullable Long time, ItemStack input, boolean withOutput) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        if (time != null && time >= 0) {
            setTime(server, time);
        }
        BlockPos pos = benchPos(server);
        BlockState bench = ContentBlocks.DRONE_WORKBENCH.block().get().defaultBlockState();
        level.setBlockAndUpdate(pos, bench);
        standAtBench(player, level, pos);
        if (player.openMenu(bench.getMenuProvider(level, pos)).isEmpty() || !(player.containerMenu instanceof DroneWorkbenchMenu menu)) {
            throw new IllegalStateException("Werkbank-Menü nicht geöffnet");
        }
        if (!input.isEmpty() || withOutput) {
            menu.clickMenuButton(player, DroneWorkbenchMenu.BUTTON_RECYCLE_TAB);
            if (withOutput && !input.isEmpty()) {
                menu.getSlot(DroneWorkbenchMenu.RECYCLE_INPUT).set(part(MotorType.M2207_1750, 1.0F, 2));
                menu.recycle(RandomSource.create(SEED));
                menu.getSlot(DroneWorkbenchMenu.RECYCLE_INPUT).set(part(BatteryType.B6S_1300, 0.0F, 1));
                menu.recycle(RandomSource.create(SEED + 1));
            }
            menu.getSlot(DroneWorkbenchMenu.RECYCLE_INPUT).set(input);
        }
        menu.broadcastChanges();
        return menu;
    }

    private static void setTime(MinecraftServer server, long time) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set " + time);
    }

    private static void standAtBench(ServerPlayer player, ServerLevel level, BlockPos bench) {
        if (player.isWithinBlockInteractionRange(bench, 0.0)) {
            return;
        }
        BACK.compareAndSet(null, Spot.of(player));
        BlockPos base = ShowcaseTour.base();
        player.teleportTo(level, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
    }

    private static boolean benchArrived(Minecraft mc) {
        return mc.level != null
                && mc.level.getBlockState(ShowcaseTour.base().offset(0, 0, 2)).is(ContentBlocks.DRONE_WORKBENCH.block().get());
    }

    private static void closeBench(MinecraftServer server) {
        ServerPlayer player = player(server);
        player.closeContainer();
        BlockPos pos = benchPos(server);
        player.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        Spot back = BACK.getAndSet(null);
        if (back != null) {
            back.apply(server, player);
        }
    }

    private static StepResult checkTexts() {
        List<String> problems = new ArrayList<>();
        Language language = Language.getInstance();
        List<String> keys = List.of(RecycleView.BUTTON, RecycleView.INPUT, RecycleView.OUTPUT, RecycleView.TITLE, RecycleView.EMPTY,
                RecycleView.CONDITION, RecycleView.TIER, RecycleView.PERCENT, RecycleView.EXPECTED, RecycleView.AMOUNT, RecycleView.APPROX, RecycleView.MORE,
                RecycleView.NOT_RECYCLABLE, RecycleView.MISSING, RecycleView.NOT_A_PART, RecycleView.ONLY_BUTTON, RecycleView.FULL,
                RecycleView.PARTIAL, RecycleView.READY, RecycleView.TIERS, RecycleView.TIER_FULL, RecycleView.TIER_HALF, RecycleView.TIER_QUARTER,
                RecycleView.TIER_BROKEN, RecycleView.INPUT_TITLE, RecycleView.INPUT_HINT, RecycleView.OUTPUT_HINT, RecycleView.BUTTON_HINT,
                RecycleView.KEY + "range", RecycleView.REPAIR_PROPS, RecycleView.TAB + "build", RecycleView.TAB + "recycle",
                "propwash.help.drone_workbench.use.5", RecycleView.DRONE);
        for (String key : keys) {
            if (!language.has(key)) {
                problems.add("Text " + key + " fehlt");
            }
        }
        String tiers = language.getOrDefault(RecycleView.TIER_FULL) + language.getOrDefault(RecycleView.TIER_HALF)
                + language.getOrDefault(RecycleView.TIER_QUARTER);
        for (String part : List.of("100", "60", "99")) {
            if (!tiers.contains(part)) {
                problems.add("Stufen-Hinweis nennt " + part + " % nicht: " + tiers);
            }
        }
        boolean german = GERMAN.equals(Minecraft.getInstance().getLanguageManager().getSelected());
        String tab = language.getOrDefault(RecycleView.TAB + "recycle");
        if (!tab.equals(german ? "Recyceln" : "Recycle")) {
            problems.add("Tab-Name " + tab);
        }
        List<String> inputHint = List.of(RecycleView.INPUT_TITLE, RecycleView.INPUT_HINT, RecycleView.TIERS, RecycleView.TIER_FULL,
                RecycleView.TIER_HALF, RecycleView.TIER_QUARTER, RecycleView.TIER_BROKEN);
        inputHint(problems, "leere Hand", ItemStack.EMPTY, inputHint);
        inputHint(problems, "Motor mit 30 %", part(MotorType.M2207_1750, 0.3F, 1), inputHint);
        inputHint(problems, "Erde", new ItemStack(Items.DIRT), List.of(RecycleView.INPUT_TITLE, RecycleView.NOT_A_PART));
        inputHint(problems, "Drohne", DroneAssembly.preset(DroneBuild.FREESTYLE),
                List.of(RecycleView.INPUT_TITLE, RecycleView.NOT_A_PART, RecycleView.DRONE));
        String drone = language.getOrDefault(RecycleView.DRONE);
        if (!drone.contains(german ? "Tab „Bauen“" : "Build tab") || !drone.contains(german ? "Drohnen" : "drones")) {
            problems.add("Hinweis für Drohnen im Tab Recyceln nennt den Tab Bauen nicht: " + drone);
        }
        Component amount = RecycleView.amount(Recycling.Materials.single(BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 3), 0, 1, Recycling.QUARTER);
        if (!(amount.getContents() instanceof TranslatableContents translatable) || !translatable.getKey().equals(RecycleView.APPROX)) {
            problems.add("Menge 0,75 ohne ≈: " + amount.getString());
        }
        Component whole = RecycleView.amount(Recycling.Materials.single(BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT), 4), 0, 1, Recycling.QUARTER);
        if (!whole.getString().equals("1")) {
            problems.add("ganze Menge mit ≈: " + whole.getString());
        }
        return StepResult.check(problems.isEmpty(), String.join("; ", problems));
    }

    private static void inputHint(List<String> problems, String what, ItemStack carried, List<String> expected) {
        List<String> keys = new ArrayList<>();
        for (SalvageHint.Line line : RecycleView.inputLines(carried)) {
            String key = line.text().getContents() instanceof TranslatableContents translatable ? translatable.getKey() : line.text().getString();
            keys.add(key);
            SalvageHint.Kind kind = key.equals(RecycleView.INPUT_TITLE) ? SalvageHint.Kind.TITLE
                    : key.equals(RecycleView.NOT_A_PART) || key.equals(RecycleView.DRONE) ? SalvageHint.Kind.WARNING : SalvageHint.Kind.BODY;
            if (line.kind() != kind) {
                problems.add("Hinweis am Recycle-Eingang, " + what + ": " + key + " als " + line.kind());
            }
        }
        if (!keys.equals(expected)) {
            problems.add("Hinweis am Recycle-Eingang, " + what + ": " + keys + " statt " + expected);
        }
    }

    private static Map<Item, Integer> totals(List<ItemStack> stacks) {
        Map<Item, Integer> out = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                out.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return out;
    }

    private static List<ItemStack> snapshot(Inventory inventory) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            items.add(inventory.getItem(slot).copy());
        }
        return items;
    }

    private static void restore(Inventory inventory, List<ItemStack> items) {
        inventory.clearContent();
        for (int slot = 0; slot < items.size(); slot++) {
            inventory.setItem(slot, items.get(slot));
        }
    }

    private static final class RecycleScene implements Screenshots.Scene {
        private final long time;
        private final Supplier<ItemStack> input;

        private RecycleScene(long time, float condition) {
            this(time, () -> part(MotorType.M2207_1750, condition, 2));
        }

        private RecycleScene(long time, Supplier<ItemStack> input) {
            this.time = time;
            this.input = input;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE)) {
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    INVENTORY.compareAndSet(null, snapshot(player(server).getInventory()));
                    player(server).getInventory().clearContent();
                    DroneWorkbenchMenu menu = openBench(server, this.time, this.input.get(), true);
                    menu.clickMenuButton(player(server), DroneWorkbenchMenu.BUTTON_RECYCLE_TAB);
                    menu.broadcastChanges();
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx);
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (!(mc.gui.screen() instanceof DroneWorkbenchScreen screen) || screen.getMenu().tab() != WorkbenchTab.RECYCLE
                    || !screen.getMenu().recycleInput().isEmpty() && screen.getMenu().recycleMaterials().isEmpty()) {
                return ctx.tick() > WAIT ? StepResult.fail("Werkbank im Tab Recyceln fehlt: " + mc.gui.screen()) : StepResult.pending();
            }
            if (!benchArrived(mc)) {
                return ctx.tick() > WAIT ? StepResult.fail("Werkbank-Block beim Client fehlt") : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                if (player.containerMenu instanceof DroneWorkbenchMenu menu) {
                    for (int slot = DroneWorkbenchMenu.RECYCLE_INPUT; slot < DroneWorkbenchMenu.RECYCLE_PREVIEW; slot++) {
                        menu.getSlot(slot).set(ItemStack.EMPTY);
                    }
                }
                closeBench(server);
                List<ItemStack> saved = INVENTORY.getAndSet(null);
                if (saved != null) {
                    restore(player.getInventory(), saved);
                }
                if (this.time >= 0) {
                    setTime(server, DAY);
                }
            });
        }
    }

    private static final class BuildScene implements Screenshots.Scene {
        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (!ctx.state().containsKey(FUTURE)) {
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    DroneWorkbenchMenu menu = openBench(server, DAY, ItemStack.EMPTY, false);
                    menu.getSlot(DroneWorkbenchMenu.SALVAGE_SLOT).set(DroneAssembly.preset(DroneBuild.FREESTYLE));
                    menu.broadcastChanges();
                }));
                return StepResult.pending();
            }
            StepResult server = awaitServer(ctx);
            if (server.status() != StepResult.Status.PASS) {
                return server;
            }
            if (!(mc.gui.screen() instanceof DroneWorkbenchScreen screen) || screen.getMenu().resultPreview().isEmpty()) {
                return ctx.tick() > WAIT ? StepResult.fail("Werkbank im Tab Bauen fehlt: " + mc.gui.screen()) : StepResult.pending();
            }
            if (!benchArrived(mc)) {
                return ctx.tick() > WAIT ? StepResult.fail("Werkbank-Block beim Client fehlt") : StepResult.pending();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            ctx.onServer(server -> {
                if (player(server).containerMenu instanceof DroneWorkbenchMenu menu) {
                    for (int slot = 0; slot < DroneWorkbenchMenu.PART_SLOTS; slot++) {
                        menu.getSlot(slot).set(ItemStack.EMPTY);
                    }
                }
                closeBench(server);
            });
        }
    }
}
