package de.lardtom.propwash.content.client;

import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.PropRepair;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.PartCondition;
import de.lardtom.propwash.content.PropRepairRecipe;
import de.lardtom.propwash.content.PropRepairs;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.drone.client.render.PropDamage;
import de.lardtom.propwash.drone.client.render.PropDamageProperty;
import de.lardtom.propwash.guide.client.render.SceneAssets;
import de.lardtom.propwash.guide.scene.ActorState;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.SceneState;
import de.lardtom.propwash.guide.scenes.RepairScene;
import de.lardtom.propwash.guide.scenes.SceneKit;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class PropRepairSelfTests {
    public static final String GROUP = "props reparieren";
    private static final String FUTURE = "prop_repair_future";
    private static final String RESULT = "prop_repair_result";
    private static final float WORN_A = 0.35F;
    private static final float WORN_B = 0.45F;
    private static final float TOLERANCE = 1.0E-6F;

    private PropRepairSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + ": Sonderrezept im 2×2- und 3×3-Raster, auch für Addon-Props", 200,
                ctx -> serverStep(ctx, PropRepairSelfTests::checkRecipe));
        SelfTest.clientStep(GROUP + ": Crafting im Inventar nimmt je einen Prop und gibt einen reparierten", 200,
                ctx -> serverStep(ctx, PropRepairSelfTests::checkInventoryCrafting));
        SelfTest.clientStep(GROUP + ": Schleifstein nimmt nur beschädigte Props einzeln und repariert", 200,
                ctx -> serverStep(ctx, PropRepairSelfTests::checkGrindstone));
        SelfTest.clientStep(GROUP + ": Tooltip, Schadensstufe und Flugschul-Schritt", 40, ctx -> clientStep());
    }

    public static ItemStack prop(PartInfo part, float condition) {
        return PartCondition.with(ContentItems.stack(part), condition);
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

    private static List<String> checkRecipe(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        RecipeManager recipes = server.getRecipeManager();
        ServerLevel level = server.overworld();
        Optional<RecipeHolder<?>> holder = recipes.byKey(PropRepairRecipe.KEY);
        if (holder.isEmpty()) {
            problems.add("Rezept " + PropRepairRecipe.KEY.identifier() + " fehlt");
            return problems;
        }
        if (!(holder.get().value() instanceof PropRepairRecipe recipe) || !recipe.isSpecial()) {
            problems.add("Rezept " + PropRepairRecipe.KEY.identifier() + " ist kein Sonderrezept: " + holder.get().value());
            return problems;
        }
        ItemStack a = prop(PropType.P51_3B, WORN_A);
        ItemStack b = prop(PropType.P51_3B, WORN_B);
        float sum = PropRepair.combine(WORN_A, WORN_B);
        expect(problems, "2×2 nebeneinander", recipes, level, grid(2, a, 0, b, 1), sum, a.getItem());
        expect(problems, "2×2 diagonal", recipes, level, grid(2, b, 0, a, 3), sum, a.getItem());
        expect(problems, "3×3 Ecken", recipes, level, grid(3, a, 0, b, 8), sum, a.getItem());
        expect(problems, "Stapel", recipes, level, grid(3, a.copyWithCount(5), 2, b.copyWithCount(3), 6), sum, a.getItem());
        expect(problems, "zwei kaputte", recipes, level, grid(2, prop(PropType.P51_3B, 0.0F), 0, prop(PropType.P51_3B, 0.0F), 1),
                PropRepair.BONUS, a.getItem());
        expect(problems, "über voll", recipes, level, grid(2, prop(PropType.P51_3B, 0.6F), 0, prop(PropType.P51_3B, 0.5F), 1),
                PartWear.NEW, a.getItem());
        expect(problems, "verschiedene Props", recipes, level, grid(2, a, 0, prop(PropType.P31_3B, WORN_B), 1), null, null);
        expect(problems, "ein neuer Prop", recipes, level, grid(2, a, 0, prop(PropType.P51_3B, PartWear.NEW), 1), null, null);
        expect(problems, "zwei neue Props", recipes, level, grid(2, ContentItems.stack(PropType.P51_3B), 0, ContentItems.stack(PropType.P51_3B), 1),
                null, null);
        expect(problems, "drei Props", recipes, level, grid3(a, b, prop(PropType.P51_3B, 0.2F)), null, null);
        expect(problems, "Motoren", recipes, level, grid(2, prop(MotorType.M2207_1750, WORN_A), 0, prop(MotorType.M2207_1750, WORN_B), 1),
                null, null);
        expect(problems, "Prop und Stock", recipes, level, grid(2, a, 0, new ItemStack(Items.STICK), 1), null, null);
        ItemStack named = a.copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("A"));
        ItemStack other = b.copy();
        other.set(DataComponents.CUSTOM_NAME, Component.literal("B"));
        ItemStack kept = PropRepairs.combine(grid(2, named, 0, other, 1));
        Component name = kept.get(DataComponents.CUSTOM_NAME);
        if (name == null || !name.getString().equals("A")) {
            problems.add("Ergebnis behält nicht die Daten des ersten Props: " + name);
        }
        if (PropRepairs.combine(prop(PropType.P51_3B, 0.6F), prop(PropType.P51_3B, 0.5F)).has(CoreComponents.PART_CONDITION.get())) {
            problems.add("voll reparierter Prop trägt noch einen Zustand");
        }
        Optional<PartInfo> addon = PartIndex.byId(ExampleAddon.PROP_13);
        if (addon.isEmpty()) {
            problems.add("Addon-Prop " + ExampleAddon.PROP_13 + " fehlt");
        } else {
            ItemStack x = prop(addon.get(), 0.2F);
            ItemStack y = prop(addon.get(), 0.3F);
            if (x.isEmpty() || !PropRepairs.isProp(x)) {
                problems.add("Addon-Prop ohne Item: " + x);
            } else {
                expect(problems, "Addon-Prop", recipes, level, grid(2, x, 0, y, 2), PropRepair.combine(0.2F, 0.3F), x.getItem());
                expect(problems, "Addon-Prop mit eingebautem", recipes, level, grid(2, x, 0, b, 1), null, null);
            }
        }
        return problems;
    }

    private static void expect(List<String> problems, String what, RecipeManager recipes, ServerLevel level, CraftingInput input,
                               @Nullable Float condition, @Nullable Item item) {
        Optional<RecipeHolder<CraftingRecipe>> found = recipes.getRecipeFor(RecipeType.CRAFTING, input, level);
        if (condition == null) {
            found.ifPresent(holder -> problems.add(what + ": passt unerwartet zu " + holder.id().identifier()));
            return;
        }
        if (found.isEmpty() || !found.get().id().equals(PropRepairRecipe.KEY)) {
            problems.add(what + ": " + (found.isEmpty() ? "kein Rezept" : "Rezept " + found.get().id().identifier()));
            return;
        }
        ItemStack result = found.get().value().assemble(input);
        if (!result.is(item) || result.getCount() != 1) {
            problems.add(what + ": Ergebnis " + result);
        } else if (Math.abs(PartCondition.of(result) - condition) > TOLERANCE) {
            problems.add(String.format(Locale.ROOT, "%s: Zustand %.3f statt %.3f", what, PartCondition.of(result), condition));
        }
    }

    private static CraftingInput grid(int size, ItemStack first, int firstSlot, ItemStack second, int secondSlot) {
        ItemStack[] items = new ItemStack[size * size];
        Arrays.fill(items, ItemStack.EMPTY);
        items[firstSlot] = first;
        items[secondSlot] = second;
        return CraftingInput.of(size, size, Arrays.asList(items));
    }

    private static CraftingInput grid3(ItemStack a, ItemStack b, ItemStack c) {
        ItemStack[] items = new ItemStack[9];
        Arrays.fill(items, ItemStack.EMPTY);
        items[0] = a;
        items[4] = b;
        items[8] = c;
        return CraftingInput.of(3, 3, Arrays.asList(items));
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static List<String> checkInventoryCrafting(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        ServerPlayer player = player(server);
        InventoryMenu menu = player.inventoryMenu;
        ItemStack carried = menu.getCarried().copy();
        List<Slot> grid = menu.getInputGridSlots();
        List<ItemStack> saved = new ArrayList<>();
        for (Slot slot : grid) {
            saved.add(slot.getItem().copy());
            slot.set(ItemStack.EMPTY);
        }
        try {
            grid.get(0).set(prop(PropType.P51_3B, WORN_A).copyWithCount(2));
            grid.get(3).set(prop(PropType.P51_3B, WORN_B));
            ItemStack result = menu.getResultSlot().getItem();
            float sum = PropRepair.combine(WORN_A, WORN_B);
            if (!result.is(ContentItems.stack(PropType.P51_3B).getItem()) || Math.abs(PartCondition.of(result) - sum) > TOLERANCE) {
                problems.add("Ergebnis im Inventar-Raster: " + result + " (" + PartCondition.of(result) + ")");
                return problems;
            }
            menu.setCarried(ItemStack.EMPTY);
            menu.clicked(menu.getResultSlot().index, 0, ContainerInput.PICKUP, player);
            ItemStack taken = menu.getCarried();
            if (!taken.is(result.getItem()) || taken.getCount() != 1 || Math.abs(PartCondition.of(taken) - sum) > TOLERANCE) {
                problems.add("entnommen: " + taken);
            }
            if (grid.get(0).getItem().getCount() != 1 || !grid.get(3).getItem().isEmpty()) {
                problems.add("Raster nach dem Craften: " + grid.get(0).getItem() + ", " + grid.get(3).getItem());
            }
            if (!menu.getResultSlot().getItem().isEmpty()) {
                problems.add("ein Prop allein ergibt noch etwas: " + menu.getResultSlot().getItem());
            }
        } finally {
            for (int i = 0; i < grid.size(); i++) {
                grid.get(i).set(saved.get(i));
            }
            menu.setCarried(carried);
            menu.broadcastChanges();
        }
        return problems;
    }

    private static List<String> checkGrindstone(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        ServerPlayer player = player(server);
        GrindstoneMenu menu = new GrindstoneMenu(0, player.getInventory(), ContainerLevelAccess.create(player.level(), player.blockPosition()));
        Slot input = menu.getSlot(GrindstoneMenu.INPUT_SLOT);
        Slot additional = menu.getSlot(GrindstoneMenu.ADDITIONAL_SLOT);
        Slot result = menu.getSlot(GrindstoneMenu.RESULT_SLOT);
        ItemStack a = prop(PropType.P51_3B, WORN_A);
        ItemStack b = prop(PropType.P51_3B, WORN_B);
        for (Slot slot : List.of(input, additional)) {
            if (!slot.mayPlace(a) || !slot.mayPlace(prop(PropType.P51_3B, 0.0F))) {
                problems.add("Schleifstein nimmt keinen beschädigten Prop (Platz " + slot.index + ")");
            }
            if (slot.mayPlace(ContentItems.stack(PropType.P51_3B))) {
                problems.add("Schleifstein nimmt einen neuen Prop (Platz " + slot.index + ")");
            }
            if (slot.mayPlace(prop(MotorType.M2207_1750, WORN_A))) {
                problems.add("Schleifstein nimmt einen beschädigten Motor (Platz " + slot.index + ")");
            }
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.setDamageValue(10);
            if (!slot.mayPlace(sword)) {
                problems.add("Schleifstein nimmt kein beschädigtes Schwert mehr (Platz " + slot.index + ")");
            }
            if (slot.getMaxStackSize(a.copyWithCount(5)) != 1) {
                problems.add("Platz " + slot.index + " nimmt mehrere Props auf einmal");
            }
        }
        input.set(a.copy());
        additional.set(b.copy());
        float sum = PropRepair.combine(WORN_A, WORN_B);
        ItemStack combined = result.getItem();
        if (!combined.is(a.getItem()) || Math.abs(PartCondition.of(combined) - sum) > TOLERANCE) {
            problems.add("Schleifstein-Ergebnis: " + combined + " (" + PartCondition.of(combined) + ")");
        }
        if (!combined.getComponentsPatch().equals(PartCondition.with(a.copy(), sum).getComponentsPatch())) {
            problems.add("Schleifstein-Ergebnis mit fremden Daten: " + combined.getComponentsPatch());
        }
        additional.set(prop(PropType.P31_3B, WORN_B));
        if (!result.getItem().isEmpty()) {
            problems.add("verschiedene Props ergeben " + result.getItem());
        }
        additional.set(b.copyWithCount(2));
        if (!result.getItem().isEmpty()) {
            problems.add("zwei Props in einem Platz ergeben " + result.getItem());
        }
        additional.set(b.copy());
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(GrindstoneMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
        ItemStack taken = menu.getCarried();
        if (!taken.is(a.getItem()) || taken.getCount() != 1 || Math.abs(PartCondition.of(taken) - sum) > TOLERANCE) {
            problems.add("am Schleifstein entnommen: " + taken);
        }
        if (input.hasItem() || additional.hasItem()) {
            problems.add("Schleifstein behält Props: " + input.getItem() + ", " + additional.getItem());
        }
        menu.setCarried(ItemStack.EMPTY);
        input.set(ItemStack.EMPTY);
        additional.set(ItemStack.EMPTY);
        return problems;
    }

    private static StepResult clientStep() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return StepResult.pending();
        }
        List<String> problems = new ArrayList<>();
        Item.TooltipContext context = Item.TooltipContext.of(mc.level);
        ItemStack broken = prop(PropType.P51_3B, 0.0F);
        ItemStack worn = prop(PropType.P51_3B, WORN_A);
        if (!keys(broken, context, false).contains("propwash.content.tooltip.repair")) {
            problems.add("kaputter Prop ohne Reparatur-Hinweis");
        }
        if (keys(worn, context, false).contains("propwash.content.tooltip.repair")) {
            problems.add("Reparatur-Hinweis schon bei beschädigtem Prop");
        }
        if (keys(prop(MotorType.M2207_1750, 0.0F), context, false).contains("propwash.content.tooltip.repair")) {
            problems.add("kaputter Motor mit Prop-Reparatur-Hinweis");
        }
        List<String> shift = keys(worn, context, true);
        if (!shift.contains("propwash.help.category.prop.use.4") || !shift.contains("propwash.help.category.prop.use.5")) {
            problems.add("Shift-Tooltip ohne Reparatur-Zeilen: " + shift);
        }
        Optional<PartInfo> addon = PartIndex.byId(ExampleAddon.PROP_13);
        if (addon.isPresent() && !keys(prop(addon.get(), 0.4F), context, true).contains("propwash.help.category.prop.use.4")) {
            problems.add("Addon-Prop ohne Reparatur-Zeilen im Shift-Tooltip");
        }
        float sum = PropRepair.combine(WORN_A, WORN_B);
        ItemStack combined = PropRepairs.combine(worn, prop(PropType.P51_3B, WORN_B));
        if (PropDamageProperty.stage(worn) != PropDamage.DAMAGED || PropDamageProperty.stage(combined) != PropDamage.of(sum)) {
            problems.add("Schadensstufe nach dem Reparieren: " + PropDamageProperty.stage(worn) + " → " + PropDamageProperty.stage(combined));
        }
        if (PropDamageProperty.stage(PropRepairs.combine(prop(PropType.P51_3B, 0.5F), prop(PropType.P51_3B, 0.45F))) != PropDamage.NONE) {
            problems.add("voll reparierter Prop zeigt noch Schaden");
        }
        problems.addAll(sceneProblems());
        return StepResult.check(problems.isEmpty(), String.join("; ", problems));
    }

    private static List<String> sceneProblems() {
        List<String> problems = new ArrayList<>();
        Scene scene = RepairScene.create();
        int step = -1;
        for (int i = 0; i < scene.stepCount(); i++) {
            if (scene.steps().get(i).name().equals("combine")) {
                step = i;
            }
        }
        if (step < 0) {
            problems.add("Flugschule ohne Schritt „Alte Props reparieren“");
            return problems;
        }
        SceneState before = SceneEvaluator.evaluate(scene, scene.stepStart(step) + RepairScene.MERGE_AT - 20.0F);
        SceneState after = SceneEvaluator.evaluate(scene, scene.stepStart(step) + RepairScene.MERGE_AT + 20.0F);
        ActorState a = before.actors().get(RepairScene.OLD_PROP_A);
        ActorState fixed = after.actors().get(RepairScene.REPAIRED_PROP);
        if (a == null || fixed == null) {
            problems.add("Flugschule: Props fehlen im Schritt");
            return problems;
        }
        ItemStack shownOld = SceneAssets.item(RepairScene.PROPS, a.propCondition(0));
        ItemStack shownNew = SceneAssets.item(RepairScene.PROPS, fixed.propCondition(0));
        if (PropDamageProperty.stage(shownOld) == PropDamage.NONE || shownOld.get(CoreComponents.PART_CONDITION.get()) == null) {
            problems.add("Flugschule zeigt den alten Prop unbeschädigt");
        }
        if (PropDamageProperty.stage(shownNew) != PropDamage.NONE) {
            problems.add("Flugschule zeigt den reparierten Prop beschädigt: " + fixed.propCondition(0));
        }
        if (SceneAssets.item(SceneKit.REMOTE, 0.2F).has(CoreComponents.PART_CONDITION.get())) {
            problems.add("Flugschule setzt einen Zustand an ein Nicht-Teil");
        }
        return problems;
    }

    private static List<String> keys(ItemStack stack, Item.TooltipContext context, boolean shift) {
        List<Component> lines = new ArrayList<>();
        TooltipShift.override(shift);
        try {
            stack.getItem().appendHoverText(stack, context, TooltipDisplay.DEFAULT, lines::add, TooltipFlag.NORMAL);
        } finally {
            TooltipShift.override(null);
        }
        List<String> keys = new ArrayList<>();
        for (Component line : lines) {
            if (line.getContents() instanceof TranslatableContents translatable) {
                keys.add(translatable.getKey());
            }
        }
        return keys;
    }
}
