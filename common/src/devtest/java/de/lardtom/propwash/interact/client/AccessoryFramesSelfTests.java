package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.share.ShareOptions;
import de.lardtom.propwash.api.share.ShareResult;
import de.lardtom.propwash.apiimpl.Conversions;
import de.lardtom.propwash.build.AccessoryMount;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.content.ImportService;
import de.lardtom.propwash.content.PartTooltips;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.content.client.DroneWorkbenchScreen;
import de.lardtom.propwash.content.client.WorkbenchReport;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.server.ServerRules;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import de.lardtom.propwash.sim.AirframeSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class AccessoryFramesSelfTests {
    public static final Identifier CINE_GIMBAL = Identifier.fromNamespaceAndPath(ExampleAddon.NAMESPACE, "cine_gimbal");
    public static final Set<Identifier> FRAMES = Set.of(FrameType.CINEWHOOP_3.id(), FrameType.LONG_RANGE_7.id());
    private static final String GROUP = AccessoryFixSelfTests.GROUP + "nur an bestimmten Rahmen: ";
    private static final int SPARE = 11;
    private static final AtomicReference<String> REPORT = new AtomicReference<>("");
    private static final AtomicReference<ItemStack> SAVED = new AtomicReference<>(ItemStack.EMPTY);

    private AccessoryFramesSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Kino-Gimbal aus JSON mit frames, Bauprüfung der API, Werkbank nimmt es nur an Cinewhoop und 7 Zoll, "
                + "Hinweis und Tooltip nennen die Rahmen, mit „Teile müssen zusammenpassen“ aus nimmt sie es überall", 400,
                ctx -> serverStep(ctx, AccessoryFramesSelfTests::workbench, false));
        SelfTest.clientStep(GROUP + "gespeicherte Freestyle-Drohne mit Kino-Gimbal behält es nach dem Laden, darf nicht fliegen, "
                + "Zerlegen legt es in die Werkbank, PW3-Code und Preset sind nicht baubar", 400,
                ctx -> serverStep(ctx, AccessoryFramesSelfTests::savedDrone, true));
        SelfTest.clientStep(GROUP + "mit „Teile müssen zusammenpassen“ aus darf die Freestyle-Drohne mit Kino-Gimbal fliegen, "
                + "PW3-Code baubar mit Warnung, createDrone und Import einer neuen Drohne bauen sie, mit Regel an lehnt der Import ab", 400,
                ctx -> serverStep(ctx, AccessoryFramesSelfTests::ruleOff, false));
    }

    static boolean ready() {
        return AccessoryGapsSelfTests.ready() && AccessoryType.byId(CINE_GIMBAL).isPresent();
    }

    @FunctionalInterface
    private interface ServerCheck {
        String run(MinecraftServer server);
    }

    private static StepResult serverStep(SelfTest.StepContext ctx, ServerCheck check, boolean tooltip) {
        if (!ready()) {
            ctx.log("Beispiel-Add-on aus, übersprungen");
            return StepResult.pass();
        }
        if (InteractSelfTests.phase(ctx) == 0) {
            ctx.state().put(InteractSelfTests.FUTURE, ctx.onServer(server -> REPORT.set(check.run(server))));
            InteractSelfTests.next(ctx);
            return StepResult.pending();
        }
        StepResult server = InteractSelfTests.await(ctx, "Server");
        if (server.status() != StepResult.Status.PASS) {
            return server;
        }
        if (tooltip) {
            String problem = droneTooltip();
            if (problem != null) {
                return StepResult.fail(problem);
            }
        }
        ctx.log(REPORT.get());
        return server;
    }

    private static AccessoryType gimbal() {
        return AccessoryGapsSelfTests.type(CINE_GIMBAL);
    }

    private static String workbench(MinecraftServer server) {
        AccessoryType gimbal = gimbal();
        AccessoryDefinition definition = PropwashApi.parts().get(AccessoryDefinition.class, CINE_GIMBAL).orElseThrow();
        if (!FRAMES.equals(definition.frames()) || !FRAMES.equals(gimbal.frames()) || definition.slot() != AccessorySlot.FRONT) {
            throw new IllegalStateException("frames aus der JSON-Datei falsch gelesen: " + definition.frames());
        }
        List<String> checked = new ArrayList<>();
        for (DroneBuild preset : DroneBuild.PRESETS) {
            if (!preset.frame().accessoryMounts.contains(AccessoryMount.FRONT)) {
                continue;
            }
            BuildCheck check = PropwashApi.builds().check(Conversions.spec(preset.withAccessory(gimbal)));
            List<String> expected = FRAMES.contains(preset.frame().id()) ? List.of() : List.of("accessory_frame");
            if (!expected.equals(check.problems())) {
                throw new IllegalStateException("builds().check an " + preset.frame().key() + ": " + check.problems() + " statt " + expected);
            }
            checked.add(preset.frame().key() + (expected.isEmpty() ? " baubar" : " accessory_frame"));
        }
        List<Component> fits = new ArrayList<>();
        PartTooltips.fits(gimbal, fits::add);
        String fitsText = fits.isEmpty() ? "" : fits.getFirst().getString();
        if (!fitsText.contains(PartTooltips.frameShortName(FrameType.CINEWHOOP_3).getString())
                || !fitsText.contains(PartTooltips.frameShortName(FrameType.LONG_RANGE_7).getString())
                || fitsText.contains(PartTooltips.frameShortName(FrameType.FREESTYLE_5).getString())) {
            throw new IllegalStateException("Tooltip „Passt zu“ falsch: " + fitsText);
        }
        ServerPlayer player = InteractSelfTests.player(server);
        Inventory inventory = player.getInventory();
        int front = BuildSlots.accessorySlot(AccessoryMount.FRONT);
        String hintText;
        try {
            rule(server, true);
            DroneWorkbenchMenu freestyle = new DroneWorkbenchMenu(0, inventory);
            fill(freestyle, player, DroneBuild.FREESTYLE);
            freestyle.setCarried(ContentItems.stack(gimbal));
            freestyle.clicked(front, 0, ContainerInput.PICKUP, player);
            if (freestyle.getSlot(front).hasItem() || freestyle.getCarried().isEmpty()) {
                throw new IllegalStateException("Werkbank nahm das Kino-Gimbal an freestyle_5");
            }
            List<Component> hint = DroneWorkbenchScreen.otherFramesHint(freestyle, front, freestyle.getCarried());
            hintText = hint.size() == 2 ? hint.get(1).getString() : "";
            if (!hintText.contains(PartTooltips.frameShortName(FrameType.CINEWHOOP_3).getString())
                    || !hintText.contains(PartTooltips.frameShortName(FrameType.LONG_RANGE_7).getString())) {
                throw new IllegalStateException("Hinweis an der Halterung fehlt oder nennt die Rahmen nicht: " + hint);
            }
            freestyle.setCarried(ItemStack.EMPTY);
            inventory.setItem(SPARE, ContentItems.stack(gimbal));
            freestyle.clicked(inventorySlot(SPARE), 0, ContainerInput.QUICK_MOVE, player);
            if (inventory.getItem(SPARE).isEmpty() || freestyle.getSlot(front).hasItem()) {
                throw new IllegalStateException("Shift-Klick legte das Kino-Gimbal an freestyle_5");
            }
            inventory.setItem(SPARE, ItemStack.EMPTY);
            DroneWorkbenchMenu cine = new DroneWorkbenchMenu(0, inventory);
            fill(cine, player, DroneBuild.CINEWHOOP);
            if (!DroneWorkbenchScreen.otherFramesHint(cine, front, ContentItems.stack(gimbal)).isEmpty()) {
                throw new IllegalStateException("Hinweis erscheint auch am Cinewhoop");
            }
            place(cine, player, front, ContentItems.stack(gimbal));
            DroneBuild built = cine.getSlot(DroneWorkbenchMenu.RESULT_SLOT).getItem().get(CoreComponents.DRONE_BUILD.get());
            if (!DroneBuild.CINEWHOOP.withAccessory(gimbal).equals(built)) {
                throw new IllegalStateException("Cinewhoop mit Kino-Gimbal nicht gebaut: " + built + " / " + cine.check());
            }
            DroneWorkbenchMenu early = new DroneWorkbenchMenu(0, inventory);
            place(early, player, front, ContentItems.stack(gimbal));
            fill(early, player, DroneBuild.FREESTYLE);
            WorkbenchReport report = WorkbenchReport.of(early.check(), PartWear.PRISTINE, early.partCompatibility());
            if (!early.resultPreview().isEmpty() || report.buildable()
                    || !early.check().build().orElseThrow().problems().contains(DroneBuild.Issue.ACCESSORY_FRAME)) {
                throw new IllegalStateException("Gimbal vor dem Rahmen eingelegt: Freestyle wird trotzdem gebaut");
            }
            rule(server, false);
            DroneWorkbenchMenu free = new DroneWorkbenchMenu(0, inventory);
            fill(free, player, DroneBuild.FREESTYLE);
            place(free, player, front, ContentItems.stack(gimbal));
            DroneBuild freeBuild = free.getSlot(DroneWorkbenchMenu.RESULT_SLOT).getItem().get(CoreComponents.DRONE_BUILD.get());
            if (!DroneBuild.FREESTYLE.withAccessory(gimbal).equals(freeBuild)) {
                throw new IllegalStateException("Regel aus: Freestyle mit Kino-Gimbal nicht gebaut: " + freeBuild);
            }
        } finally {
            rule(server, true);
            inventory.setItem(SPARE, ItemStack.EMPTY);
        }
        return "Kino-Gimbal frames " + gimbal.frames() + "; builds().check: " + String.join(", ", checked) + "; Tooltip „" + fitsText
                + "“; Werkbank an freestyle_5 abgelehnt (Klick und Shift-Klick), Hinweis „" + hintText
                + "“; Cinewhoop gebaut; Gimbal vor dem Rahmen: nicht baubar; Regel aus: Freestyle mit Gimbal gebaut";
    }

    private static String savedDrone(MinecraftServer server) {
        AccessoryType gimbal = gimbal();
        DroneBuild wrong = DroneBuild.FREESTYLE.withAccessory(gimbal);
        ServerPlayer player = InteractSelfTests.player(server);
        ServerLevel level = player.level();
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne ließ sich nicht erzeugen");
        }
        drone.loadFromStack(DroneAssembly.preset(wrong));
        drone.setPos(player.getX(), player.getY(), player.getZ());
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        if (!drone.save(output)) {
            throw new IllegalStateException("Drohne nicht speicherbar");
        }
        CompoundTag saved = output.buildResult();
        drone.discard();
        Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved),
                level, EntitySpawnReason.LOAD, EntityProcessor.NOP);
        if (!(loaded instanceof QuadcopterEntity reloaded)) {
            throw new IllegalStateException("keine Drohne geladen: " + loaded);
        }
        DroneBuild build = reloaded.build();
        if (!wrong.equals(build) || build.accessories().at(AccessoryMount.FRONT).orElse(null) != gimbal) {
            throw new IllegalStateException("Kino-Gimbal nach dem Laden nicht mehr an der Drohne: " + build);
        }
        if (!build.problems().equals(List.of(DroneBuild.Issue.ACCESSORY_FRAME)) || AirframeSpec.of(build).buildable
                || !AirframeSpec.of(build).assemblable || !ServerRules.refusesParts(WorldRules.partCompatibility(server), build)) {
            throw new IllegalStateException("geladene Drohne darf fliegen: " + build.problems());
        }
        String denial = ServerRules.pilotDenialReason(player, reloaded).map(AccessoryFramesSelfTests::key).orElse("keine");
        if (!denial.equals("propwash.server.deny.parts_incompatible")) {
            throw new IllegalStateException("Steuern nicht verweigert: " + denial);
        }
        ItemStack back = reloaded.toStack();
        reloaded.discard();
        SAVED.set(back.copy());
        Inventory inventory = player.getInventory();
        int front = BuildSlots.accessorySlot(AccessoryMount.FRONT);
        String disassembled;
        try {
            DroneWorkbenchMenu menu = new DroneWorkbenchMenu(0, inventory);
            inventory.setItem(SPARE, back.copy());
            menu.clicked(inventorySlot(SPARE), 0, ContainerInput.QUICK_MOVE, player);
            if (DroneAssembly.isDrone(inventory.getItem(SPARE))) {
                throw new IllegalStateException("Drohne nicht im Zerlege-Slot");
            }
            if (DroneAssembly.part(menu.getSlot(front).getItem()).orElse(null) != gimbal) {
                throw new IllegalStateException("Zerlegen legt das Kino-Gimbal nicht in die Halterung: " + menu.getSlot(front).getItem());
            }
            if (!menu.resultPreview().isEmpty() || !menu.check().build().orElseThrow().problems().contains(DroneBuild.Issue.ACCESSORY_FRAME)) {
                throw new IllegalStateException("zerlegte Teile mit Kino-Gimbal gelten als baubar");
            }
            menu.clicked(front, 0, ContainerInput.QUICK_MOVE, player);
            int found = 0;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                if (DroneAssembly.part(inventory.getItem(i)).orElse(null) == gimbal) {
                    found += inventory.getItem(i).getCount();
                    inventory.setItem(i, ItemStack.EMPTY);
                }
            }
            if (found != 1 || menu.getSlot(front).hasItem()) {
                throw new IllegalStateException("Kino-Gimbal nicht ins Inventar zurück: " + found);
            }
            if (!DroneAssembly.isDrone(menu.getSlot(DroneWorkbenchMenu.RESULT_SLOT).getItem())) {
                throw new IllegalStateException("ohne Gimbal ist der Freestyle nicht baubar: " + menu.check());
            }
            disassembled = "Zerlegen: Gimbal in der Halterung, nicht baubar; herausgenommen ins Inventar, Freestyle baubar";
        } finally {
            inventory.setItem(SPARE, ItemStack.EMPTY);
        }
        DroneData data = PropwashApi.builds().read(back).orElseThrow();
        String code = PropwashApi.shareCodes().encode(data, ShareOptions.defaults());
        ShareResult result = PropwashApi.shareCodes().decode(code);
        BuildSpec spec = result.data().map(DroneData::build).orElseThrow(() -> new IllegalStateException("PW3-Code nicht lesbar: " + result));
        if (!spec.accessory(AccessorySlot.FRONT).equals(Optional.of(CINE_GIMBAL))) {
            throw new IllegalStateException("PW3-Code verliert das Kino-Gimbal: " + spec);
        }
        BuildCheck check = PropwashApi.builds().check(spec);
        if (!check.problems().equals(List.of("accessory_frame"))) {
            throw new IllegalStateException("PW3-Code: " + check.problems());
        }
        boolean refused;
        try {
            PropwashApi.builds().createDrone(result.data().orElseThrow());
            refused = false;
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        if (!refused) {
            throw new IllegalStateException("createDrone baute die Freestyle mit Kino-Gimbal");
        }
        return "geladen: Gimbal bleibt, Probleme " + build.problems() + ", Steuern verweigert (" + denial + "); " + disassembled
                + "; PW3 " + code.length() + " Zeichen: " + check.problems() + ", createDrone abgelehnt";
    }

    private static String ruleOff(MinecraftServer server) {
        AccessoryType gimbal = gimbal();
        DroneBuild wrong = DroneBuild.FREESTYLE.withAccessory(gimbal);
        ServerPlayer player = InteractSelfTests.player(server);
        Inventory inventory = player.getInventory();
        DroneData data = PropwashApi.builds().read(DroneAssembly.preset(wrong)).orElseThrow();
        String code = PropwashApi.shareCodes().encode(data, ShareOptions.defaults());
        DroneData shared = PropwashApi.shareCodes().decode(code).data()
                .orElseThrow(() -> new IllegalStateException("PW3-Code nicht lesbar: " + code));
        if (!shared.build().accessory(AccessorySlot.FRONT).equals(Optional.of(CINE_GIMBAL))) {
            throw new IllegalStateException("PW3-Code verliert das Kino-Gimbal: " + shared.build());
        }
        QuadcopterEntity drone = DroneModule.DRONE.get().create(player.level(), EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne ließ sich nicht erzeugen");
        }
        drone.loadFromStack(DroneAssembly.preset(wrong));
        drone.setPos(player.getX(), player.getY(), player.getZ());
        GameType mode = player.gameMode();
        int before = countDrones(inventory, wrong);
        try {
            player.setGameMode(GameType.CREATIVE);
            ImportResult strict = ImportService.apply(player, ImportRequest.createDrone(shared));
            if (strict.status() != ImportStatus.INVALID || countDrones(inventory, wrong) != before) {
                throw new IllegalStateException("Regel an: Import einer neuen Drohne mit Kino-Gimbal am Freestyle: " + strict);
            }
            rule(server, false);
            if (!drone.build().equals(wrong) || ServerRules.refusesParts(WorldRules.partCompatibility(server), drone.build())) {
                throw new IllegalStateException("Regel aus: Drohne gilt weiter als unpassend: " + drone.build().problems());
            }
            String denial = ServerRules.pilotDenialReason(player, drone).map(AccessoryFramesSelfTests::key).orElse("keine");
            if (!denial.equals("keine")) {
                throw new IllegalStateException("Regel aus: Steuern verweigert: " + denial);
            }
            BuildCheck check = PropwashApi.builds().check(shared.build());
            if (!check.buildable() || !check.warnings().contains("accessory_frame") || PropwashApi.builds().partCompatibility()) {
                throw new IllegalStateException("Regel aus: PW3-Code " + check);
            }
            ItemStack created = PropwashApi.builds().createDrone(shared);
            if (!wrong.equals(DroneAssembly.buildOf(created))) {
                throw new IllegalStateException("Regel aus: createDrone baute " + DroneAssembly.buildOf(created));
            }
            ImportResult imported = ImportService.apply(player, ImportRequest.createDrone(shared));
            if (!imported.status().success() || countDrones(inventory, wrong) != before + 1) {
                throw new IllegalStateException("Regel aus: Import einer neuen Drohne: " + imported + ", im Inventar "
                        + countDrones(inventory, wrong) + " statt " + (before + 1));
            }
            return "Regel an: Import " + strict.status() + "; Regel aus: Steuern erlaubt, PW3 " + code.length() + " Zeichen baubar mit Warnungen "
                    + check.warnings() + ", createDrone baut die Freestyle mit Kino-Gimbal, Import " + imported.status();
        } finally {
            rule(server, true);
            removeDrones(inventory, wrong, countDrones(inventory, wrong) - before);
            player.setGameMode(mode);
            drone.discard();
        }
    }

    private static int countDrones(Inventory inventory, DroneBuild build) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (DroneAssembly.isDrone(stack) && build.equals(DroneAssembly.buildOf(stack))) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void removeDrones(Inventory inventory, DroneBuild build, int count) {
        for (int i = 0; i < inventory.getContainerSize() && count > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (DroneAssembly.isDrone(stack) && build.equals(DroneAssembly.buildOf(stack))) {
                int taken = Math.min(count, stack.getCount());
                stack.shrink(taken);
                count -= taken;
            }
        }
        inventory.setChanged();
    }

    private static String droneTooltip() {
        Minecraft mc = Minecraft.getInstance();
        ItemStack stack = SAVED.get();
        if (mc.level == null || mc.player == null || stack.isEmpty()) {
            return "kein Drohnen-Item für den Tooltip";
        }
        String line = Component.translatable(DroneBuild.Issue.ACCESSORY_FRAME.translationKey()).getString();
        StringBuilder all = new StringBuilder();
        TooltipShift.override(true);
        try {
            for (Component part : stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)) {
                all.append(part.getString()).append(" | ");
            }
        } finally {
            TooltipShift.override(null);
        }
        if (!all.toString().contains(line)) {
            return "Drohnen-Item nennt „" + line + "“ nicht: " + all;
        }
        REPORT.set(REPORT.get() + "; Drohnen-Item mit Shift: „" + line + "“");
        return null;
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : component.getString();
    }

    private static void rule(MinecraftServer server, boolean enforced) {
        WorldRules.set(server, WorldRuleSpecs.PART_COMPATIBILITY, enforced ? 1 : 0);
        if (WorldRules.partCompatibility(server).enforced() != enforced) {
            throw new IllegalStateException("Welt-Regel part_compatibility liest sich falsch: " + WorldRules.partCompatibility(server));
        }
    }

    private static void fill(DroneWorkbenchMenu menu, ServerPlayer player, DroneBuild build) {
        place(menu, player, BuildSlots.FRAME, ContentItems.stack(build.frame()));
        place(menu, player, BuildSlots.STACK, ContentItems.stack(build.stack()));
        place(menu, player, BuildSlots.VIDEO, ContentItems.stack(build.video()));
        place(menu, player, BuildSlots.BATTERY, ContentItems.stack(build.battery()));
        for (int i = 0; i < DroneBuild.MOTOR_COUNT; i++) {
            place(menu, player, BuildSlots.MOTOR + i, ContentItems.stack(build.motor()));
            place(menu, player, BuildSlots.PROP + i, ContentItems.stack(build.prop()));
        }
    }

    private static void place(DroneWorkbenchMenu menu, ServerPlayer player, int slot, ItemStack stack) {
        menu.setCarried(stack);
        menu.clicked(slot, 0, ContainerInput.PICKUP, player);
        if (!menu.getCarried().isEmpty()) {
            throw new IllegalStateException("Slot " + slot + " nahm " + stack + " nicht an");
        }
    }

    private static int inventorySlot(int inventorySlot) {
        if (inventorySlot < Inventory.getSelectionSize()) {
            return DroneWorkbenchMenu.HOTBAR_START + inventorySlot;
        }
        return DroneWorkbenchMenu.INV_START + inventorySlot - Inventory.getSelectionSize();
    }
}
