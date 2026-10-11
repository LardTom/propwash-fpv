package de.lardtom.propwash.content.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.PaintPreset;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.build.PaintSwatch;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ContentBlocks;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.DroneWorkbenchMenu;
import de.lardtom.propwash.content.client.paint.PaintScreen;
import de.lardtom.propwash.content.paint.PaintComponents;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.content.paint.PaintService;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.render.DroneRenderResources;
import de.lardtom.propwash.drone.client.render.DroneRenderState;
import de.lardtom.propwash.drone.client.render.DroneVisuals;
import de.lardtom.propwash.drone.client.render.PaintPicker;
import de.lardtom.propwash.drone.client.render.PartModel;
import de.lardtom.propwash.ui.client.theme.ThemeStats;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class PaintSelfTests {
    private static final String FUTURE = "future";
    private static final String OPENED = "opened";
    private static final String APPLIED = "applied";
    private static final String CLOSED = "closed";
    private static final DronePaint SAMPLE = PaintPreset.NEON.paint().with(PaintSlot.PROP_REAR_RIGHT, 0x123456);
    private static final AtomicInteger DRONE = new AtomicInteger(-1);

    private PaintSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("content: Lackierung – Texte, Item und Speicherformat", 60, PaintSelfTests::itemStep);
        SelfTest.clientStep("content: Lackierung – Farbstoffe im Überlebensmodus, kostenlos im Kreativmodus", 100, PaintSelfTests::costStep);
        SelfTest.clientStep("content: Lackierung – Screen, Klick-Auswahl und Übernehmen", 400, PaintSelfTests::screenStep);
        SelfTest.clientStep("drone-render: Lackierung – andere Spieler sehen die Farben", 200, PaintSelfTests::worldStep);
        SelfTest.clientStep("drone-render: Lackierung – alle Lack-Modelle laden", 40, ctx -> modelsStep());
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static BlockPos benchPos(ServerPlayer player) {
        return player.blockPosition().offset(-2, 0, 2);
    }

    private static StepResult itemStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            List<String> errors = new ArrayList<>();
            Language language = Language.getInstance();
            for (PaintSlot slot : PaintSlot.values()) {
                for (String key : List.of("propwash.paint.slot." + slot.key(), "propwash.paint.slot_hint." + slot.key())) {
                    if (!language.has(key)) {
                        errors.add("Text fehlt: " + key);
                    }
                }
            }
            for (PaintSwatch swatch : PaintSwatch.values()) {
                if (!language.has(swatch.translationKey()) || !language.has("item.minecraft." + swatch.dye() + "_dye")) {
                    errors.add("Text fehlt für Farbe " + swatch.key());
                }
            }
            for (PaintPreset preset : PaintPreset.values()) {
                if (!language.has(preset.translationKey())) {
                    errors.add("Text fehlt: " + preset.translationKey());
                }
            }
            if (!errors.isEmpty()) {
                return StepResult.fail(String.join("; ", errors));
            }
            AtomicReference<String> problem = new AtomicReference<>("");
            ctx.state().put(APPLIED, problem);
            ctx.state().put(FUTURE, ctx.onServer(server -> problem.set(itemChecks(server))));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server: " + future.exceptionNow().getMessage());
        }
        String problem = ((AtomicReference<?>) ctx.state().get(APPLIED)).get().toString();
        return problem.isEmpty() ? StepResult.pass() : StepResult.fail(problem);
    }

    private static String itemChecks(MinecraftServer server) {
        ItemStack drone = DroneAssembly.preset(DroneBuild.FREESTYLE);
        PaintItems.store(drone, SAMPLE);
        CustomModelData data = drone.get(DataComponents.CUSTOM_MODEL_DATA);
        if (!SAMPLE.equals(drone.get(PaintComponents.DRONE_PAINT.get())) || data == null || data.colors().size() != PaintSlot.COUNT) {
            return "Drohnen-Item trägt Lackierung/Icon-Farben nicht: " + drone.getComponents();
        }
        if (data.colors().get(PaintSlot.PROP_REAR_RIGHT.ordinal()) != PaintSlot.PROP_REAR_RIGHT.channel().tint(0x123456)) {
            return "Icon-Farbe des Props hinten rechts falsch";
        }
        Tag tag = ItemStack.CODEC.encodeStart(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), drone).getOrThrow();
        ItemStack loaded = ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
        if (!SAMPLE.equals(PaintItems.paint(loaded))) {
            return "Lackierung ging beim Speichern verloren: " + PaintItems.paint(loaded);
        }
        List<ItemStack> parts = DroneAssembly.disassemble(loaded);
        ItemStack frame = parts.get(BuildSlots.FRAME);
        if (!SAMPLE.equals(PaintItems.paint(frame)) || frame.has(DataComponents.CUSTOM_MODEL_DATA)) {
            return "Zerlegen legt die Lackierung nicht (nur) auf den Rahmen";
        }
        ItemStack again = DroneAssembly.createDrone(DroneBuild.FREESTYLE, parts.get(BuildSlots.STACK), frame, parts.get(BuildSlots.BATTERY));
        if (!SAMPLE.equals(PaintItems.paint(again)) || !again.has(DataComponents.CUSTOM_MODEL_DATA)) {
            return "Zusammenbauen übernimmt die Lackierung des Rahmens nicht";
        }
        PaintItems.store(again, DronePaint.DEFAULT);
        if (again.has(PaintComponents.DRONE_PAINT.get()) || again.has(DataComponents.CUSTOM_MODEL_DATA)) {
            return "Standard-Lackierung entfernt die Komponenten nicht";
        }
        ServerLevel level = player(server).level();
        QuadcopterEntity entity = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            return "Drohne nicht erzeugt";
        }
        entity.loadFromStack(loaded);
        if (!SAMPLE.equals(entity.paint()) || !SAMPLE.equals(PaintItems.paint(entity.toStack()))) {
            return "Entity übernimmt die Lackierung nicht (loadFromStack/toStack)";
        }
        ItemStack old = DroneAssembly.preset(DroneBuild.WHOOP);
        entity.loadFromStack(old);
        if (!entity.paint().isDefault()) {
            return "alte Drohne ohne Lackierung lädt nicht mit Standardfarben";
        }
        entity.discard();
        return "";
    }

    private static StepResult costStep(SelfTest.StepContext ctx) {
        if (ctx.tick() == 0) {
            AtomicReference<String> problem = new AtomicReference<>("");
            ctx.state().put(APPLIED, problem);
            ctx.state().put(FUTURE, ctx.onServer(server -> problem.set(costChecks(server))));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server: " + future.exceptionNow().getMessage());
        }
        String problem = ((AtomicReference<?>) ctx.state().get(APPLIED)).get().toString();
        return problem.isEmpty() ? StepResult.pass() : StepResult.fail(problem);
    }

    private static String costChecks(MinecraftServer server) {
        ServerPlayer player = player(server);
        GameType mode = player.gameMode();
        List<ItemStack> backup = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            backup.add(player.getInventory().getItem(i).copy());
        }
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            DroneWorkbenchMenu menu = new DroneWorkbenchMenu(0, player.getInventory());
            put(menu, player, BuildSlots.FRAME, ContentItems.stack(DroneBuild.FREESTYLE.frame()));
            DronePaint twoRed = DronePaint.DEFAULT.with(PaintSlot.FRAME, PaintSwatch.RED.rgb()).with(PaintSlot.MOTORS, PaintSwatch.RED.rgb());
            player.getInventory().setItem(0, new ItemStack(Items.DYE.red(), 1));
            if (menu.applyPaint(player, twoRed) != PaintService.Result.MISSING_DYES || !menu.paint().isDefault()
                    || player.getInventory().getItem(0).getCount() != 1) {
                return "mit nur einem roten Farbstoff wurden zwei Teile lackiert";
            }
            player.getInventory().setItem(0, new ItemStack(Items.DYE.red(), 3));
            if (menu.applyPaint(player, twoRed) != PaintService.Result.APPLIED || !twoRed.equals(menu.paint())
                    || player.getInventory().getItem(0).getCount() != 1) {
                return "zwei rote Teile verbrauchten nicht genau zwei rote Farbstoffe: " + player.getInventory().getItem(0);
            }
            if (menu.applyPaint(player, twoRed) != PaintService.Result.UNCHANGED) {
                return "unveränderte Lackierung wurde erneut berechnet";
            }
            player.getInventory().setItem(1, new ItemStack(Items.DYE.blue(), 1));
            DronePaint custom = twoRed.with(PaintSlot.CAMERA, 0x2040C0);
            if (menu.applyPaint(player, custom) != PaintService.Result.APPLIED || !player.getInventory().getItem(1).isEmpty()
                    || player.getInventory().getItem(0).getCount() != 1) {
                return "eigene Farbe verbrauchte nicht den ähnlichsten Farbstoff (blau)";
            }
            if (menu.applyPaint(player, DronePaint.DEFAULT) != PaintService.Result.APPLIED || !menu.paint().isDefault()
                    || player.getInventory().getItem(0).getCount() != 1) {
                return "Zurücksetzen auf Standard war nicht kostenlos";
            }
            player.setGameMode(GameType.CREATIVE);
            if (menu.applyPaint(player, PaintPreset.RACE_TEAM.paint()) != PaintService.Result.APPLIED
                    || !PaintPreset.RACE_TEAM.paint().equals(menu.paint()) || player.getInventory().getItem(0).getCount() != 1) {
                return "Kreativmodus lackiert nicht kostenlos";
            }
            menu.setCarried(ItemStack.EMPTY);
            return "";
        } finally {
            player.getInventory().clearContent();
            for (int i = 0; i < backup.size(); i++) {
                player.getInventory().setItem(i, backup.get(i));
            }
            player.setGameMode(mode);
        }
    }

    private static void put(DroneWorkbenchMenu menu, ServerPlayer player, int slot, ItemStack stack) {
        menu.setCarried(stack);
        menu.clicked(slot, 0, ContainerInput.PICKUP, player);
        if (!menu.getCarried().isEmpty()) {
            throw new IllegalStateException("Slot " + slot + " nahm " + stack + " nicht an");
        }
    }

    private static StepResult screenStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                BlockPos pos = benchPos(player);
                BlockState bench = ContentBlocks.DRONE_WORKBENCH.block().get().defaultBlockState();
                level.setBlockAndUpdate(pos, bench);
                player.setGameMode(GameType.CREATIVE);
                if (player.openMenu(bench.getMenuProvider(level, pos)).isEmpty() || !(player.containerMenu instanceof DroneWorkbenchMenu menu)) {
                    throw new IllegalStateException("Werkbank-Menü nicht geöffnet");
                }
                DroneBuild build = DroneBuild.FREESTYLE;
                put(menu, player, BuildSlots.FRAME, ContentItems.stack(build.frame()));
                put(menu, player, BuildSlots.STACK, ContentItems.stack(build.stack()));
                put(menu, player, BuildSlots.VIDEO, ContentItems.stack(build.video()));
                put(menu, player, BuildSlots.BATTERY, ContentItems.stack(build.battery()));
                for (int i = 0; i < DroneBuild.MOTOR_COUNT; i++) {
                    put(menu, player, BuildSlots.MOTOR + i, ContentItems.stack(build.motor()));
                    put(menu, player, BuildSlots.PROP + i, ContentItems.stack(build.prop()));
                }
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            closeBench(ctx);
            return StepResult.fail("Werkbank: " + future.exceptionNow().getMessage());
        }
        if (ctx.state().containsKey(CLOSED)) {
            if (mc.gui.screen() instanceof DroneWorkbenchScreen screen) {
                return checkResultPaint(ctx, screen);
            }
            return ctx.tick() > 380 ? fail(ctx, "nach „Zurück“ kam die Werkbank nicht wieder: " + mc.gui.screen()) : StepResult.pending();
        }
        if (mc.gui.screen() instanceof DroneWorkbenchScreen workbench && !ctx.state().containsKey(OPENED)) {
            if (workbench.getMenu().resultPreview().isEmpty()) {
                return ctx.tick() > 150 ? fail(ctx, "Werkbank zeigt keine fertige Drohne") : StepResult.pending();
            }
            if (workbench.openPaint() == null) {
                return fail(ctx, "Knopf „Lackierung“ öffnet den Screen nicht");
            }
            ctx.state().put(OPENED, ctx.tick());
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof PaintScreen paint)) {
            return ctx.tick() > 200 ? fail(ctx, "Lackier-Screen fehlt: " + mc.gui.screen()) : StepResult.pending();
        }
        int opened = (Integer) ctx.state().get(OPENED);
        if (ctx.tick() - opened < 5) {
            return StepResult.pending();
        }
        Object applied = ctx.state().get(APPLIED);
        if (applied == null) {
            if (paint.frames() == 0L || ThemeStats.frames(PaintScreen.class) == 0L || !ThemedScreen.active()) {
                return fail(ctx, "Lackier-Screen zeichnet nicht im Propwash-Theme");
            }
            if (!paint.availableSlots().contains(PaintSlot.MOTORS) || !paint.availableSlots().contains(PaintSlot.PROP_REAR_LEFT)
                    || paint.availableSlots().contains(PaintSlot.ACCESSORIES)) {
                return fail(ctx, "verfügbare Teile falsch: " + paint.availableSlots());
            }
            String picked = pickChecks(paint);
            if (!picked.isEmpty()) {
                return fail(ctx, picked);
            }
            paint.select(PaintSlot.PROP_FRONT_LEFT);
            paint.paintSelected(PaintSwatch.LIME.rgb());
            paint.applyToAllProps();
            paint.select(PaintSlot.PROP_REAR_RIGHT);
            paint.paintSelected(PaintSwatch.MAGENTA.rgb());
            paint.select(PaintSlot.FRAME);
            paint.paintSelected(PaintSwatch.CARBON.rgb());
            DronePaint expected = DronePaint.DEFAULT.withAllProps(PaintSwatch.LIME.rgb()).with(PaintSlot.PROP_REAR_RIGHT, PaintSwatch.MAGENTA.rgb())
                    .with(PaintSlot.FRAME, PaintSwatch.CARBON.rgb());
            if (!expected.equals(paint.draft())) {
                return fail(ctx, "Entwurf falsch: " + paint.draft());
            }
            String copied = paint.draft().toText();
            paint.copy();
            paint.setDraft(DronePaint.DEFAULT);
            if (!paint.paste() || !expected.equals(paint.draft()) || !copied.equals(mc.keyboardHandler.getClipboard())) {
                return fail(ctx, "Kopieren/Einfügen stellt die Lackierung nicht wieder her");
            }
            if (!paint.costLine().getString().equals(Component.translatable("propwash.paint.cost.creative").getString())) {
                return fail(ctx, "Kreativmodus zeigt Kosten an: " + paint.costLine().getString());
            }
            if (!paint.apply()) {
                return fail(ctx, "Übernehmen ließ sich nicht senden");
            }
            ctx.state().put(APPLIED, expected);
            ctx.state().put(FUTURE + "_at", ctx.tick());
            return StepResult.pending();
        }
        DronePaint expected = (DronePaint) applied;
        if (!expected.equals(paint.menu().paint())) {
            int since = (Integer) ctx.state().get(FUTURE + "_at");
            return ctx.tick() - since > 60 ? fail(ctx, "Server übernahm die Lackierung nicht: " + paint.menu().paint()) : StepResult.pending();
        }
        if (paint.changed()) {
            return fail(ctx, "Screen meldet nach dem Übernehmen noch Änderungen");
        }
        DroneRenderState state = DroneVisuals.previewState(DroneBuild.FREESTYLE, PartWear.PRISTINE, expected);
        DroneRenderResources res = DroneRenderResources.INSTANCE;
        if (state == null || state.props[0].item == res.item(PartModel.prop(DroneBuild.FREESTYLE.prop(), false))
                || state.frame.item == res.item(PartModel.of(DroneBuild.FREESTYLE.frame()))
                || state.motors[0].item != res.item(PartModel.of(DroneBuild.FREESTYLE.motor()))) {
            return fail(ctx, "Vorschau zeichnet lackierte Teile nicht mit Lack-Modellen (oder unlackierte Motoren mit ihnen)");
        }
        paint.onClose();
        ctx.state().put(CLOSED, Boolean.TRUE);
        return StepResult.pending();
    }

    private static StepResult checkResultPaint(SelfTest.StepContext ctx, DroneWorkbenchScreen screen) {
        DronePaint expected = (DronePaint) ctx.state().get(APPLIED);
        ItemStack result = screen.getMenu().resultPreview();
        if (!expected.equals(PaintItems.paint(result)) || !result.has(DataComponents.CUSTOM_MODEL_DATA)) {
            return ctx.tick() > 380 ? fail(ctx, "Ergebnis-Drohne trägt die Lackierung nicht: " + result.getComponents()) : StepResult.pending();
        }
        closeBench(ctx);
        return StepResult.pass();
    }

    private static String pickChecks(PaintScreen paint) {
        PaintPicker.View view = PaintPicker.view(paint.previewBuild(), PartWear.PRISTINE, paint.draft(), 0, 0, 200, 150, 30.0F);
        if (view == null) {
            return "keine Vorschau für die Klick-Auswahl";
        }
        for (PaintSlot slot : List.of(PaintSlot.MOTORS, PaintSlot.PROP_FRONT_LEFT, PaintSlot.PROP_REAR_RIGHT, PaintSlot.BATTERY)) {
            boolean hit = false;
            List<String> seen = new ArrayList<>();
            for (Vector3fc anchor : PaintPicker.anchors(view, slot)) {
                Vector2f p = PaintPicker.project(view, anchor);
                Optional<PaintSlot> picked = PaintPicker.pick(view, p.x, p.y);
                if (slot.isProp()) {
                    Vector2f blade = PaintPicker.project(view, new Vector3f(anchor).add(
                            view.state().build.prop().diameterMm * 0.3F * Math.signum(anchor.x()), 0.0F, 0.0F));
                    picked = PaintPicker.pick(view, blade.x, blade.y);
                }
                if (picked.isPresent() && picked.get() == slot) {
                    hit = true;
                }
                seen.add(picked.map(PaintSlot::key).orElse("-") + "@" + anchor);
            }
            if (!hit) {
                return "Klick auf " + slot.key() + " wählt es nicht aus: " + seen;
            }
        }
        if (PaintPicker.pick(view, -500.0, -500.0).isPresent()) {
            return "Klick neben die Drohne wählt ein Teil aus";
        }
        return "";
    }

    private static StepResult fail(SelfTest.StepContext ctx, String reason) {
        closeBench(ctx);
        return StepResult.fail(reason);
    }

    private static void closeBench(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof PaintScreen) {
            mc.gui.setScreen(null);
        }
        ctx.onServer(server -> {
            ServerPlayer player = player(server);
            if (player.containerMenu instanceof DroneWorkbenchMenu menu) {
                for (int slot = 0; slot < DroneWorkbenchMenu.PART_SLOTS; slot++) {
                    menu.getSlot(slot).set(ItemStack.EMPTY);
                }
            }
            player.closeContainer();
            player.getInventory().clearContent();
            player.level().removeBlock(benchPos(player), false);
        });
    }

    private static StepResult worldStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (ctx.tick() == 0) {
            DRONE.set(-1);
            ctx.state().put(FUTURE, ctx.onServer(server -> {
                ServerPlayer player = player(server);
                ServerLevel level = player.level();
                QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
                if (drone == null) {
                    throw new IllegalStateException("Drohne nicht erzeugt");
                }
                ItemStack stack = DroneAssembly.preset(DroneBuild.RACER);
                PaintItems.store(stack, SAMPLE);
                drone.loadFromStack(stack);
                drone.snapTo(player.getX() + 2.5, player.getY(), player.getZ() - 1.5, 0.0F, 0.0F);
                level.addFreshEntity(drone);
                DRONE.set(drone.getId());
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(FUTURE);
        if (!future.isDone()) {
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Server: " + future.exceptionNow().getMessage());
        }
        if (!ctx.state().containsKey(APPLIED)) {
            if (mc.level == null || !(mc.level.getEntity(DRONE.get()) instanceof QuadcopterEntity drone) || !SAMPLE.equals(drone.paint())) {
                return ctx.tick() > 150 ? StepResult.fail("Client sieht die Lackierung der Drohne nicht") : StepResult.pending();
            }
            DroneRenderState state = new DroneRenderState();
            DroneVisuals.fillFromEntity(state, drone, 0.0F);
            DroneRenderResources res = DroneRenderResources.INSTANCE;
            if (state.frame.item == res.item(PartModel.of(DroneBuild.RACER.frame()))
                    || state.props[3].item == state.props[0].item
                    || state.battery.item == res.item(PartModel.of(DroneBuild.RACER.battery()))) {
                return StepResult.fail("Welt-Drohne zeichnet die Lackierung nicht (Rahmen/Akku ungelackt oder Props gleich)");
            }
            ctx.state().put(APPLIED, Boolean.TRUE);
            ctx.state().put(CLOSED, ctx.onServer(server -> {
                if (player(server).level().getEntity(DRONE.get()) instanceof QuadcopterEntity drone2) {
                    drone2.setPaint(DronePaint.DEFAULT.with(PaintSlot.MOTORS, PaintSwatch.PINK.rgb()));
                }
            }));
            return StepResult.pending();
        }
        if (mc.level == null || !(mc.level.getEntity(DRONE.get()) instanceof QuadcopterEntity drone)) {
            return StepResult.fail("Drohne verschwand");
        }
        if (drone.paint().count() != 1) {
            return ctx.tick() > 190 ? StepResult.fail("Umlackieren wurde nicht an den Client geschickt") : StepResult.pending();
        }
        ctx.onServer(server -> {
            if (player(server).level().getEntity(DRONE.get()) instanceof QuadcopterEntity drone2) {
                drone2.discard();
            }
        });
        return StepResult.pass();
    }

    private static StepResult modelsStep() {
        DroneRenderResources res = DroneRenderResources.INSTANCE;
        List<String> missing = new ArrayList<>();
        int painted = 0;
        for (PartModel model : PartModel.all()) {
            List<DroneRenderResources.TintGroup> groups = res.paintGroups(model);
            if (groups.isEmpty()) {
                continue;
            }
            int[] tints = new int[groups.size()];
            for (int i = 0; i < tints.length; i++) {
                tints[i] = groups.get(i).channel().tint(0xFF00FF);
            }
            if (res.paintedItem(model, tints) == null) {
                missing.add(model.toString());
            } else {
                painted++;
            }
        }
        if (painted < 50) {
            return StepResult.fail("nur " + painted + " Lack-Modelle bekannt (drone_paint.json fehlt?)");
        }
        return missing.isEmpty() ? StepResult.pass() : StepResult.fail("Lack-Modelle fehlen: " + missing);
    }
}
