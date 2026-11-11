package de.lardtom.propwash.content.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.content.PartCondition;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.PartCategory;
import de.lardtom.propwash.core.PartRef;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class SalvageSelfTests {
    public static final String GROUP = "zerlegen";
    private static final String FUTURE = "salvage_future";
    private static final String RESULT = "salvage_result";
    private static final String GERMAN = "de_de";
    private static final String LEGACY_MOTOR = "{\"id\": \"propwash:m2207_1750\", \"count\": 1, \"components\": {\"propwash:part_condition\": 0.35}}";

    private SalvageSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + ": Zerlegen-Feld nimmt nur Drohnen, Teile gehören in den Tab Recyceln", 200,
                ctx -> serverStep(ctx, SalvageSelfTests::checkSlot));
        SelfTest.clientStep(GROUP + ": Hinweis am Zerlegen-Feld nennt den Tab Recyceln", 20, ctx -> checkHints());
    }

    public static ItemStack missingPart() {
        ItemStack stack = ContentItems.stack(MotorType.M2207_1750);
        stack.set(CoreComponents.PART.get(), new PartRef(PartCategory.MOTOR, "propwash_salvage_gone:m9999"));
        return PartCondition.with(stack, 0.0F);
    }

    public static ItemStack part(PartInfo part, float condition) {
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

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static List<String> checkSlot(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        ServerPlayer player = player(server);
        List<ItemStack> saved = snapshot(player.getInventory());
        try {
            refuses(problems, player, "Motor mit 30 %", part(MotorType.M2207_1750, 0.3F));
            refuses(problems, player, "kaputter Motor", part(MotorType.M2207_1750, 0.0F));
            refuses(problems, player, "zwei Motoren mit 50 %", part(MotorType.M2207_1750, 0.5F).copyWithCount(2));
            refuses(problems, player, "neuer Motor", ContentItems.stack(MotorType.M2207_1750));
            refuses(problems, player, "kaputter Akku", part(BatteryType.B6S_1300, 0.0F));
            refuses(problems, player, "Rahmen mit 60 %", part(FrameType.FREESTYLE_5, 0.6F));
            refuses(problems, player, "Videoeinheit mit 10 %", part(VideoType.DIGITAL_HD, 0.1F));
            refuses(problems, player, "kaputter Prop", part(PropType.P51_3B, 0.0F));
            refuses(problems, player, "kaputtes Zubehör", part(AccessoryType.LED_STRIP, 0.0F));
            PartInfo addonMotor = PartIndex.byId(ExampleAddon.MOTOR_4214).orElse(null);
            if (addonMotor == null) {
                problems.add("Add-on-Motor " + ExampleAddon.MOTOR_4214 + " fehlt");
            } else {
                refuses(problems, player, "Add-on-Motor mit 20 %", part(addonMotor, 0.2F));
            }
            ItemStack legacy = ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                    JsonParser.parseString(LEGACY_MOTOR)).result().orElse(ItemStack.EMPTY);
            if (DroneAssembly.part(legacy).orElse(null) != MotorType.M2207_1750 || PartCondition.of(legacy) != 0.35F) {
                problems.add("Motor im Format von 0.5.2 nicht lesbar: " + legacy);
            } else {
                refuses(problems, player, "Motor aus 0.5.2 mit 35 %", legacy);
            }
            refuses(problems, player, "Teil eines fehlenden Add-ons", missingPart());
            refuses(problems, player, "Erde", new ItemStack(Items.DIRT));
            takesDroneApart(problems, player);
            shiftClick(problems, player);
        } finally {
            restore(player.getInventory(), saved);
        }
        return problems;
    }

    private static DroneWorkbenchMenu menu(ServerPlayer player) {
        return new DroneWorkbenchMenu(0, player.getInventory(), ContainerLevelAccess.create(player.level(), player.blockPosition()));
    }

    private static void refuses(List<String> problems, ServerPlayer player, String what, ItemStack stack) {
        player.getInventory().clearContent();
        DroneWorkbenchMenu menu = menu(player);
        if (menu.getSlot(DroneWorkbenchMenu.SALVAGE_SLOT).mayPlace(stack)) {
            problems.add(what + ": wird ins Zerlegen-Feld gelassen");
        }
        menu.setCarried(stack.copy());
        menu.clicked(DroneWorkbenchMenu.SALVAGE_SLOT, 0, ContainerInput.PICKUP, player);
        if (!ItemStack.matches(menu.getCarried(), stack)) {
            problems.add(what + ": an der Maus verändert (" + menu.getCarried() + ")");
        }
        if (menu.getSlot(DroneWorkbenchMenu.SALVAGE_SLOT).hasItem()) {
            problems.add(what + ": liegt im Zerlegen-Feld");
        }
        if (!player.getInventory().isEmpty()) {
            problems.add(what + ": ergibt Materialien");
        }
        menu.setCarried(ItemStack.EMPTY);
    }

    private static void takesDroneApart(List<String> problems, ServerPlayer player) {
        player.getInventory().clearContent();
        DroneWorkbenchMenu menu = menu(player);
        ItemStack drone = DroneAssembly.preset(DroneBuild.FREESTYLE);
        if (!menu.getSlot(DroneWorkbenchMenu.SALVAGE_SLOT).mayPlace(drone)) {
            problems.add("Drohne passt nicht ins Zerlegen-Feld");
            return;
        }
        menu.setCarried(drone.copy());
        menu.clicked(DroneWorkbenchMenu.SALVAGE_SLOT, 0, ContainerInput.PICKUP, player);
        if (!menu.getCarried().isEmpty() || menu.getSlot(DroneWorkbenchMenu.SALVAGE_SLOT).hasItem()) {
            problems.add("Drohne wird nicht zerlegt (Maus " + menu.getCarried() + ")");
        }
        if (DroneAssembly.part(menu.getSlot(BuildSlots.FRAME).getItem()).orElse(null) != DroneBuild.FREESTYLE.frame()
                || DroneAssembly.part(menu.getSlot(BuildSlots.MOTOR).getItem()).orElse(null) != DroneBuild.FREESTYLE.motor()) {
            problems.add("Zerlegen legt Rahmen und Motoren nicht in die Bau-Plätze");
        }
        menu.setCarried(ItemStack.EMPTY);
    }

    private static void shiftClick(List<String> problems, ServerPlayer player) {
        int inventorySlot = DroneWorkbenchMenu.INV_START;
        for (float condition : new float[]{0.0F, 0.3F}) {
            player.getInventory().clearContent();
            DroneWorkbenchMenu menu = menu(player);
            ItemStack motor = part(MotorType.M2207_1750, condition);
            menu.getSlot(inventorySlot).set(motor.copy());
            menu.quickMoveStack(player, inventorySlot);
            ItemStack inMotorSlot = menu.getSlot(BuildSlots.MOTOR).getItem();
            String what = condition == 0.0F ? "kaputter Motor" : "Motor mit 30 %";
            if (menu.getSlot(DroneWorkbenchMenu.SALVAGE_SLOT).hasItem() || inventoryCount(player, motor) == 0 && inMotorSlot.isEmpty()) {
                problems.add("Umschalt-Klick legt " + what + " ins Zerlegen-Feld statt auf den Motor-Platz");
            } else if (inMotorSlot.isEmpty() || PartCondition.of(inMotorSlot) != condition) {
                problems.add("Umschalt-Klick legt " + what + " nicht auf den Motor-Platz");
            }
            if (!player.getInventory().isEmpty() && inventoryCount(player, motor) == 0) {
                problems.add("Umschalt-Klick recycelt " + what);
            }
        }
        player.getInventory().clearContent();
        DroneWorkbenchMenu menu = menu(player);
        menu.getSlot(inventorySlot).set(DroneAssembly.preset(DroneBuild.FREESTYLE));
        menu.quickMoveStack(player, inventorySlot);
        if (menu.getSlot(inventorySlot).hasItem() || !menu.getSlot(BuildSlots.FRAME).hasItem()) {
            problems.add("Umschalt-Klick legt eine Drohne nicht ins Zerlegen-Feld");
        }
    }

    private static int inventoryCount(ServerPlayer player, ItemStack like) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (ItemStack.isSameItem(inventory.getItem(slot), like)) {
                count += inventory.getItem(slot).getCount();
            }
        }
        return count;
    }

    private static StepResult checkHints() {
        List<String> problems = new ArrayList<>();
        hint(problems, "leere Hand", ItemStack.EMPTY, true, List.of(SalvageHint.TITLE, SalvageHint.SALVAGE, SalvageHint.RECYCLE_TAB));
        hint(problems, "Drohne bei belegter Werkbank", DroneAssembly.preset(DroneBuild.FREESTYLE), false,
                List.of(SalvageHint.TITLE, SalvageHint.SALVAGE, SalvageHint.RECYCLE_TAB, SalvageHint.BLOCKED));
        hint(problems, "Motor mit 30 %", part(MotorType.M2207_1750, 0.3F), true, List.of(SalvageHint.TITLE, SalvageHint.REJECT_PART));
        hint(problems, "kaputter Prop", part(PropType.P51_3B, 0.0F), true, List.of(SalvageHint.TITLE, SalvageHint.REJECT_PART));
        hint(problems, "Teil eines fehlenden Add-ons", missingPart(), true, List.of(SalvageHint.TITLE, SalvageHint.REJECT_PART));
        hint(problems, "Erde", new ItemStack(Items.DIRT), true, List.of(SalvageHint.TITLE, SalvageHint.REJECT_OTHER));
        boolean german = GERMAN.equals(Minecraft.getInstance().getLanguageManager().getSelected());
        String recycleTab = Language.getInstance().getOrDefault(SalvageHint.RECYCLE_TAB);
        if (!recycleTab.contains(german ? "Recyceln" : "Recycle")) {
            problems.add("Hinweis am Zerlegen-Feld nennt den Tab Recyceln nicht: " + recycleTab);
        }
        String part = Language.getInstance().getOrDefault(SalvageHint.REJECT_PART);
        if (!part.contains(german ? "Recyceln" : "Recycle")) {
            problems.add("Ablehnung eines Teils nennt den Tab Recyceln nicht: " + part);
        }
        return StepResult.check(problems.isEmpty(), String.join("; ", problems));
    }

    private static void hint(List<String> problems, String what, ItemStack carried, boolean partsEmpty, List<String> expected) {
        List<SalvageHint.Line> lines = SalvageHint.lines(carried, partsEmpty);
        List<String> keys = new ArrayList<>();
        for (SalvageHint.Line line : lines) {
            String key = line.text().getContents() instanceof TranslatableContents translatable ? translatable.getKey() : line.text().getString();
            keys.add(key);
            if (line.text().getContents() instanceof TranslatableContents && !Language.getInstance().has(key)) {
                problems.add(what + ": Text " + key + " fehlt");
            }
            boolean rejection = key.startsWith(SalvageHint.REJECT) || key.equals(SalvageHint.BLOCKED);
            if (rejection != (line.kind() == SalvageHint.Kind.WARNING)) {
                problems.add(what + ": " + key + " als " + line.kind());
            }
        }
        if (!keys.equals(expected)) {
            problems.add(what + ": " + keys + " statt " + expected);
        }
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
}
