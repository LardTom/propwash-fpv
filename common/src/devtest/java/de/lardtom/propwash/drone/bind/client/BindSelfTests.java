package de.lardtom.propwash.drone.bind.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.build.PartCompatibility;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.BuildSlots;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.content.DroneAssembly;
import de.lardtom.propwash.content.paint.PaintItems;
import de.lardtom.propwash.core.BoundDrone;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.core.FlightTune;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.DroneServiceMenu;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.PilotRules;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.bind.BindAccess;
import de.lardtom.propwash.drone.bind.BindCodecs;
import de.lardtom.propwash.drone.bind.BindComponents;
import de.lardtom.propwash.drone.bind.ReceiverBind;
import de.lardtom.propwash.drone.bind.ReceiverBinds;
import de.lardtom.propwash.drone.client.DroneServiceScreen;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.WearWeightSelfTests;
import de.lardtom.propwash.guide.client.GuideSelfTests;
import de.lardtom.propwash.guide.scenes.BindScene;
import de.lardtom.propwash.osd.client.RadioHud;
import de.lardtom.propwash.video.client.GogglesBlackout;
import de.lardtom.propwash.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class BindSelfTests {
    private static final String NAME = "Bindungstest";
    private static final int GAP_TICKS = DronePiloting.REMOTE_DEBOUNCE_TICKS + 4;
    private static final int TOOLTIP_SLOT = 0;
    private static final int LEGACY_SLOT = 20;
    private static final String PHASE = "bind_phase";
    private static final String FUTURE = "bind_future";
    private static final AtomicReference<@Nullable UUID> DRONE = new AtomicReference<>();
    private static final AtomicLong LAST_PHASE = new AtomicLong(Long.MIN_VALUE / 2);

    private BindSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep("bindung: Funke binden speichert den Piloten in der Drohne", 200, BindSelfTests::bindStep);
        SelfTest.clientStep("bindung: Aufheben, Werkbank-Umbau und Absetzen behalten die Bindung, Funke fliegt sofort", 300,
                BindSelfTests::workbenchStep);
        SelfTest.clientStep("bindung: Speicherstand der Drohne behält die Bindung, alte Phrasen-Daten werden überlesen", 100,
                BindSelfTests::saveStep);
        SelfTest.clientStep("bindung: fremder Pilot lehnt ab, Anklicken übernimmt", 300, BindSelfTests::otherPilotStep);
        SelfTest.clientStep("bindung: neuer Flugcontroller ohne Bindung muss neu binden", 300, BindSelfTests::newStackStep);
        SelfTest.clientStep("bindung: Funke ohne Ziel findet die eigene Drohne, passende Brille fliegt FPV ohne Binden", 300,
                BindSelfTests::searchStep);
        SelfTest.clientStep("bindung: Zurücksetzen im Wartungsmenü mit zwei Klicks", 400, BindSelfTests::resetStep);
        SelfTest.clientStep("bindung: falsche Brille – Funke lehnt Binden und Verbinden ab, passende fliegt FPV", 400,
                BindSelfTests::mismatchStep);
        SelfTest.clientStep("bindung: Brille an die Drohne halten prüft nur, alte Brillen-Bindung verschwindet", 300,
                BindSelfTests::gogglesCheckStep);
        SelfTest.clientStep("bindung: ohne Brille Sichtflug, Brille auf FPV, Brille ab Sichtflug", 500, BindSelfTests::switchStep);
        SelfTest.clientStep("bindung: falsche Brille im Flug – kein Bild statt Sichtflug", 600, BindSelfTests::blindStep);
        SelfTest.clientStep("bindung: keine Reste der Binding-Phrase", 100, BindSelfTests::legacyStep);
        SelfTest.clientStep("bindung: Aufräumen", 100, BindSelfTests::cleanupStep);
    }

    public static void registerScreenshots(boolean german) {
        String suffix = german ? " deutsch" : "";
        String file = german ? "_de" : "";
        Screenshots.step("bildschirmfotos: Bindung – Wartungsmenü mit gebundenem Empfänger" + suffix, "drone_service_receiver" + file, 30,
                new ServiceScene(false));
        Screenshots.step("bildschirmfotos: Bindung – Wartungsmenü, Zurücksetzen bestätigen" + suffix, "drone_service_receiver_reset" + file, 30,
                new ServiceScene(true));
        Screenshots.step("bildschirmfotos: Bindung – Shift-Tooltip gebundene Drohne" + suffix, "tooltip_drone_bound" + file, 30,
                WearWeightSelfTests.tooltipScene(BindSelfTests::boundDroneStack));
        Screenshots.step("bildschirmfotos: Bindung – Flugschule, Brille aufsetzen ohne Binden" + suffix, "flight_school_bind_wear" + file, 30,
                GuideSelfTests.sceneShot(BindScene.ID, 1, 70.0F));
        Screenshots.step("bildschirmfotos: Bindung – Flugschule, falsche Brille" + suffix, "flight_school_bind_mismatch" + file, 30,
                GuideSelfTests.sceneShot(BindScene.ID, 2, 86.0F));
        Screenshots.step("bildschirmfotos: Bindung – Flugschule, Bindung bleibt" + suffix, "flight_school_bind_keep" + file, 30,
                GuideSelfTests.sceneShot(BindScene.ID, 3, 96.0F));
        Screenshots.step("bildschirmfotos: Bindung – falsche Brille im Flug, analoge Brille rauscht" + suffix, "goggles_mismatch_analog" + file, 30,
                new BlindScene(ContentIds.GOGGLES_ANALOG, DroneBuild.FREESTYLE));
        if (!german) {
            Screenshots.step("bildschirmfotos: Bindung – falsche Brille im Flug, digitale Brille schwarz", "goggles_mismatch_digital", 30,
                    new BlindScene(ContentIds.GOGGLES_DIGITAL, DroneBuild.RACER));
        }
        Screenshots.step("bildschirmfotos: Bindung – Shift-Tooltip Brille ohne Bindung" + suffix, "tooltip_goggles_no_bind" + file, 30,
                WearWeightSelfTests.tooltipScene(() -> ContentIds.item(ContentIds.GOGGLES_ANALOG).map(ItemStack::new).orElse(ItemStack.EMPTY)));
    }

    private static ItemStack boundDroneStack() {
        ItemStack stack = ContentTab.presetDrone(DroneBuild.FREESTYLE);
        Minecraft mc = Minecraft.getInstance();
        UUID self = mc.player == null ? new UUID(0L, 1L) : mc.player.getUUID();
        BindComponents.store(stack, ReceiverBind.of(self));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Nighthawk"));
        return stack;
    }

    private static StepResult bindStep(SelfTest.StepContext ctx) {
        return phases(ctx, "Binden", List.of(server -> {
            ServerPlayer player = player(server);
            clearBound(player, null);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            ItemStack stack = ContentTab.presetDrone(DroneBuild.FREESTYLE);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(NAME));
            QuadcopterEntity drone = place(player, stack, 3);
            check(drone.receiverBind() == null, "neue Drohne hat schon eine Bindung: " + drone.receiverBind());
            ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, remote);
            player.setShiftKeyDown(false);
            drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
            BoundDrone bound = remote.get(CoreComponents.BOUND_DRONE.get());
            check(bound != null && bound.drone().equals(drone.getUUID()), "Funke nicht gebunden: " + bound);
            ReceiverBind bind = drone.receiverBind();
            check(bind != null && player.getUUID().equals(bind.pilot()), "Drohne merkt sich den Piloten nicht: " + bind);
            check(ReceiverBinds.access(player, drone) == BindAccess.PILOT, "Zugriff " + ReceiverBinds.access(player, drone));
            DRONE.set(drone.getUUID());
        }));
    }

    private static StepResult workbenchStep(SelfTest.StepContext ctx) {
        return phases(ctx, "Werkbank-Umbau", List.of(server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            UUID id = drone.getUUID();
            ReceiverBind bind = Objects.requireNonNull(drone.receiverBind());
            drone.setWear(PartWear.PRISTINE.withProp(PartWear.FRONT_LEFT, 0.2F));
            ItemStack remote = player.getMainHandItem();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
            check(drone.isRemoved(), "Drohne nicht aufgehoben");
            ItemStack picked = findDrone(player, id);
            check(bind.equals(BindComponents.of(picked)), "Drohnen-Item ohne Bindung: " + BindComponents.of(picked));
            List<ItemStack> parts = DroneAssembly.disassemble(picked);
            ItemStack stackPart = parts.get(BuildSlots.STACK);
            check(bind.equals(BindComponents.of(stackPart)), "Flugcontroller ohne Bindung: " + BindComponents.of(stackPart));
            check(id.equals(stackPart.get(CoreComponents.DRONE_ID.get())), "Flugcontroller ohne Drohnen-Id");
            for (int slot = BuildSlots.PROP; slot < BuildSlots.PROP + DroneBuild.MOTOR_COUNT; slot++) {
                parts.set(slot, ContentItems.stack(DroneBuild.FREESTYLE.prop()));
            }
            ItemStack frame = parts.get(BuildSlots.FRAME);
            PaintItems.store(frame, PaintItems.paint(frame).with(PaintSlot.FRAME, 0x2E8BFF));
            frame.set(DataComponents.CUSTOM_NAME, Component.literal(NAME + " 2"));
            stackPart.set(CoreComponents.FLIGHT_TUNE.get(), new FlightTune(Map.of("rates.rcDeadband", 4.0F)));
            SimpleContainer bench = new SimpleContainer(parts.size());
            for (int slot = 0; slot < parts.size(); slot++) {
                bench.setItem(slot, parts.get(slot));
            }
            ItemStack rebuilt = DroneAssembly.assemble(bench, PartCompatibility.ENFORCED);
            check(!rebuilt.isEmpty(), "Umbau nicht baubar");
            check(bind.equals(BindComponents.of(rebuilt)), "umgebaute Drohne ohne Bindung");
            check(!PaintItems.paint(rebuilt).isDefault(), "Lack nicht übernommen");
            removeDrone(player, id);
            QuadcopterEntity again = place(player, rebuilt, 3);
            check(again.getUUID().equals(id), "neue Id nach dem Absetzen");
            check(bind.equals(again.receiverBind()), "abgesetzte Drohne ohne Bindung: " + again.receiverBind());
            check(PartWear.NEW == again.wear().prop(PartWear.FRONT_LEFT), "neue Props nicht übernommen");
            player.setItemInHand(InteractionHand.MAIN_HAND, remote);
        }, server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            DronePiloting.useRemote(player, player.getMainHandItem());
            check(drone.isFlownBy(player.getUUID()), "Funke fliegt die umgebaute Drohne nicht ohne neues Binden");
            DronePiloting.release(drone);
        }));
    }

    private static StepResult saveStep(SelfTest.StepContext ctx) {
        return phases(ctx, "Speicherstand", List.of(server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, drone.registryAccess());
            drone.saveWithoutId(output);
            CompoundTag tag = output.buildResult();
            check(tag.contains(QuadcopterEntity.RECEIVER_BIND_KEY), "Bindung nicht gespeichert: " + tag.keySet());
            QuadcopterEntity copy = DroneModule.DRONE.get().create(player.level(), EntitySpawnReason.LOAD);
            check(copy != null, "keine Drohne erzeugt");
            copy.load(TagValueInput.create(ProblemReporter.DISCARDING, drone.registryAccess(), tag));
            check(Objects.equals(drone.receiverBind(), copy.receiverBind()), "geladene Bindung weicht ab: " + copy.receiverBind());
            tag.put(QuadcopterEntity.RECEIVER_BIND_KEY, BindCodecs.CODEC.encodeStart(NbtOps.INSTANCE, ReceiverBind.CLEARED).getOrThrow());
            copy.load(TagValueInput.create(ProblemReporter.DISCARDING, drone.registryAccess(), tag));
            check(copy.receiverBind() != null && copy.receiverBind().cleared(), "zurückgesetzte Bindung nicht geladen");
            tag.remove(QuadcopterEntity.RECEIVER_BIND_KEY);
            copy.load(TagValueInput.create(ProblemReporter.DISCARDING, drone.registryAccess(), tag));
            check(copy.receiverBind() == null, "alte Drohne ohne Bindung bekommt eine");
            CompoundTag legacy = (CompoundTag) BindCodecs.CODEC.encodeStart(NbtOps.INSTANCE, ReceiverBind.of(player.getUUID())).getOrThrow();
            legacy.putLong("lock", 0x5A5A5A5AL);
            tag.put(QuadcopterEntity.RECEIVER_BIND_KEY, legacy);
            copy.load(TagValueInput.create(ProblemReporter.DISCARDING, drone.registryAccess(), tag));
            check(ReceiverBind.of(player.getUUID()).equals(copy.receiverBind()), "Bindung mit altem Phrasen-Schloss nicht gelesen: "
                    + copy.receiverBind());
            TagValueOutput again = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, drone.registryAccess());
            copy.saveWithoutId(again);
            check(!again.buildResult().getCompoundOrEmpty(QuadcopterEntity.RECEIVER_BIND_KEY).contains("lock"),
                    "altes Phrasen-Schloss wird wieder gespeichert");
        }));
    }

    private static StepResult otherPilotStep(SelfTest.StepContext ctx) {
        UUID stranger = UUID.randomUUID();
        return phases(ctx, "Fremder Pilot", List.of(server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            drone.setReceiverBind(ReceiverBind.of(stranger));
            check(ReceiverBinds.access(player, drone) == BindAccess.OTHER, "fremde Bindung nicht erkannt");
            DronePiloting.useRemote(player, player.getMainHandItem());
            check(drone.pilot().isEmpty(), "fremd gebundene Drohne fliegt mit alter Funke");
            check(ReceiverBinds.nearestBound(player).isEmpty(), "Suche findet eine fremd gebundene Drohne");
        }, server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            ItemStack remote = player.getMainHandItem();
            player.setShiftKeyDown(false);
            drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
            check(ReceiverBinds.access(player, drone) == BindAccess.PILOT, "Neu binden übernimmt die Drohne nicht");
            check(ContentIds.boundDrone(remote).isPresent(), "Funke verliert die Bindung");
        }, server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            DronePiloting.useRemote(player, player.getMainHandItem());
            check(drone.isFlownBy(player.getUUID()), "übernommene Drohne fliegt nicht");
            DronePiloting.release(drone);
        }));
    }

    private static StepResult newStackStep(SelfTest.StepContext ctx) {
        return phases(ctx, "Neuer Flugcontroller", List.of(server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            UUID id = drone.getUUID();
            ItemStack remote = player.getMainHandItem();
            ItemStack stack = drone.toStack();
            removeDrone(player, id);
            List<ItemStack> parts = DroneAssembly.disassemble(stack);
            parts.set(BuildSlots.STACK, ContentItems.stack(DroneBuild.FREESTYLE.stack()));
            SimpleContainer bench = new SimpleContainer(parts.size());
            for (int slot = 0; slot < parts.size(); slot++) {
                bench.setItem(slot, parts.get(slot));
            }
            ItemStack rebuilt = DroneAssembly.assemble(bench, PartCompatibility.ENFORCED);
            check(BindComponents.of(rebuilt) == null, "neuer Flugcontroller bringt eine Bindung mit");
            check(!rebuilt.has(CoreComponents.DRONE_ID.get()), "neuer Flugcontroller bringt eine Id mit");
            QuadcopterEntity fresh = place(player, rebuilt, 3);
            DRONE.set(fresh.getUUID());
            player.setItemInHand(InteractionHand.MAIN_HAND, remote);
            clearBound(player, null);
            DronePiloting.useRemote(player, remote);
            check(fresh.pilot().isEmpty(), "Drohne mit neuem Flugcontroller fliegt ohne Binden");
        }, server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            player.setShiftKeyDown(false);
            drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
            check(ReceiverBinds.access(player, drone) == BindAccess.PILOT, "Binden des neuen Flugcontrollers fehlgeschlagen");
        }, server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            DronePiloting.useRemote(player, player.getMainHandItem());
            check(drone.isFlownBy(player.getUUID()), "neu gebundene Drohne fliegt nicht");
            DronePiloting.release(drone);
        }));
    }

    private static StepResult searchStep(SelfTest.StepContext ctx) {
        return phases(ctx, "Suche nach der eigenen Drohne", List.of(server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            ItemStack copy = Objects.requireNonNull(drone.getPickResult());
            check(!copy.has(CoreComponents.DRONE_ID.get()), "Kopie behält die Id");
            check(BindComponents.of(copy) != null, "Kopie verliert die Bindung");
            removeDrone(player, drone.getUUID());
            QuadcopterEntity twin = place(player, copy, 4);
            DRONE.set(twin.getUUID());
            ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, remote);
            player.setItemSlot(EquipmentSlot.HEAD, goggles(ContentIds.GOGGLES_CREATIVE));
            DronePiloting.useRemote(player, remote);
            check(twin.isFlownBy(player.getUUID()), "unbenutzte Funke findet die eigene Drohne nicht");
            BoundDrone bound = remote.get(CoreComponents.BOUND_DRONE.get());
            check(bound != null && bound.drone().equals(twin.getUUID()), "Funke zeigt nicht auf die verbundene Drohne: " + bound);
            check(!ContentIds.wornGoggles(player).has(CoreComponents.BOUND_DRONE.get()), "Brille wird wieder gebunden");
            check(twin.pilotMode() == PilotMode.FPV, "kein FPV mit passender Brille: " + twin.pilotMode());
            DronePiloting.release(twin);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }));
    }

    private static StepResult resetStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        int stage = (int) ctx.state().getOrDefault("reset_stage", 0);
        if (stage == 0) {
            StepResult opened = phases(ctx, "Wartungsmenü öffnen", List.of(server -> {
                ServerPlayer player = player(server);
                QuadcopterEntity drone = drone(player);
                openService(player, drone);
            }));
            if (opened.status() == SelfTest.StepResult.Status.PASS) {
                ctx.state().put("reset_stage", 1);
                ctx.state().put("reset_since", ctx.tick());
                return StepResult.pending();
            }
            return opened;
        }
        if (stage == 1) {
            if (!(mc.gui.screen() instanceof DroneServiceScreen screen) || screen.getMenu().bindAccess() != BindAccess.PILOT) {
                return timeout(ctx, "reset_since", 200, "Wartungsmenü zeigt keinen gebundenen Empfänger: " + mc.gui.screen());
            }
            screen.confirmBindReset();
            if (screen.getMenu().bindAccess() != BindAccess.PILOT) {
                return StepResult.fail("ein Klick setzt schon zurück");
            }
            screen.armBindReset();
            if (!screen.bindResetArmed()) {
                return StepResult.fail("erster Klick fragt nicht nach");
            }
            screen.confirmBindReset();
            ctx.state().put("reset_stage", 2);
            ctx.state().put("reset_since", ctx.tick());
            return StepResult.pending();
        }
        if (stage == 2) {
            if (!(mc.gui.screen() instanceof DroneServiceScreen screen) || screen.getMenu().bindAccess() != BindAccess.CLEARED) {
                return timeout(ctx, "reset_since", 100, "Zurücksetzen kommt nicht an");
            }
            ctx.state().put("reset_stage", 3);
            ctx.state().remove(PHASE);
            ctx.state().remove(FUTURE);
            return StepResult.pending();
        }
        return phases(ctx, "Nach dem Zurücksetzen", List.of(server -> {
            ServerPlayer player = player(server);
            player.closeContainer();
            QuadcopterEntity drone = drone(player);
            check(drone.receiverBind() != null && drone.receiverBind().cleared(), "Server hat nicht zurückgesetzt: " + drone.receiverBind());
            DronePiloting.useRemote(player, player.getMainHandItem());
            check(drone.pilot().isEmpty(), "zurückgesetzte Drohne fliegt ohne neues Binden");
        }, server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            player.setShiftKeyDown(false);
            drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
            check(ReceiverBinds.access(player, drone) == BindAccess.PILOT, "neu binden nach dem Zurücksetzen klappt nicht");
        }));
    }

    private static StepResult mismatchStep(SelfTest.StepContext ctx) {
        return phases(ctx, "Falsche Brille", List.of(server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            check(drone.build().video().system == VideoSystem.DIGITAL, "Testdrohne sendet nicht digital");
            ReceiverBind before = drone.receiverBind();
            ItemStack fresh = new ItemStack(DroneModule.REMOTE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, fresh);
            player.setItemSlot(EquipmentSlot.HEAD, goggles(ContentIds.GOGGLES_ANALOG));
            check(DronePiloting.decideMode(player, drone) == PilotRules.Decision.NO_VIDEO, "analoge Brille gilt als passend");
            player.setShiftKeyDown(false);
            drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
            check(!fresh.has(CoreComponents.BOUND_DRONE.get()), "Funke bindet trotz falscher Brille");
            check(Objects.equals(before, drone.receiverBind()), "Empfänger ändert sich trotz Ablehnung: " + drone.receiverBind());
        }, server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            ItemStack remote = player.getMainHandItem();
            ReceiverBinds.point(remote, drone);
            DronePiloting.useRemote(player, remote);
            check(drone.pilot().isEmpty(), "Funke verbindet trotz falscher Brille");
        }, server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, remote);
            DronePiloting.useRemote(player, remote);
            check(drone.pilot().isEmpty(), "Suche verbindet trotz falscher Brille");
            check(!remote.has(CoreComponents.BOUND_DRONE.get()), "abgelehnte Suche biegt die Funke um");
        }, server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            ItemStack remote = player.getMainHandItem();
            player.setItemSlot(EquipmentSlot.HEAD, goggles(ContentIds.GOGGLES_DIGITAL));
            player.setShiftKeyDown(false);
            drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
            check(remote.has(CoreComponents.BOUND_DRONE.get()), "Funke bindet mit passender Brille nicht");
            check(ReceiverBinds.access(player, drone) == BindAccess.PILOT, "Empfänger nach dem Binden nicht beim Piloten");
        }, server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            DronePiloting.useRemote(player, player.getMainHandItem());
            check(drone.isFlownBy(player.getUUID()), "passende Brille verbindet nicht");
            check(drone.pilotMode() == PilotMode.FPV, "passende Brille fliegt nicht FPV: " + drone.pilotMode());
            check(!ContentIds.wornGoggles(player).has(CoreComponents.BOUND_DRONE.get()), "Brille wird beim Start gebunden");
            DronePiloting.release(drone);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }));
    }

    private static StepResult gogglesCheckStep(SelfTest.StepContext ctx) {
        return phases(ctx, "Brille prüfen", List.of(server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            ItemStack remote = player.getMainHandItem();
            ReceiverBind before = drone.receiverBind();
            for (Identifier id : List.of(ContentIds.GOGGLES_ANALOG, ContentIds.GOGGLES_DIGITAL, ContentIds.GOGGLES_CREATIVE)) {
                ItemStack held = goggles(id);
                player.setItemInHand(InteractionHand.MAIN_HAND, held);
                player.setShiftKeyDown(false);
                InteractionResult result = drone.interact(player, InteractionHand.MAIN_HAND, drone.position());
                check(result.consumesAction(), "Klick mit der Brille läuft durch: " + result);
                check(!held.has(CoreComponents.BOUND_DRONE.get()), "Brille wird noch gebunden: " + id);
                check(Objects.equals(before, drone.receiverBind()), "Brille ändert den Empfänger");
                check(!drone.isRemoved(), "Klick mit der Brille hebt die Drohne auf");
            }
            ItemStack legacy = goggles(ContentIds.GOGGLES_ANALOG);
            legacy.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.getInventory().setItem(LEGACY_SLOT, legacy);
            ItemStack worn = goggles(ContentIds.GOGGLES_DIGITAL);
            worn.set(CoreComponents.BOUND_DRONE.get(), new BoundDrone(UUID.randomUUID(), "", drone.build().frame(), drone.build().video().system));
            player.setItemSlot(EquipmentSlot.HEAD, worn);
            player.setItemInHand(InteractionHand.MAIN_HAND, remote);
        }, server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            ItemStack legacy = player.getInventory().getItem(LEGACY_SLOT);
            check(ContentIds.isGoggles(legacy), "Brille aus dem Inventar verschwunden");
            check(!legacy.has(CoreComponents.BOUND_DRONE.get()), "alte Brillen-Bindung bleibt im Inventar stehen");
            player.getInventory().setItem(LEGACY_SLOT, ItemStack.EMPTY);
            DronePiloting.useRemote(player, player.getMainHandItem());
            check(drone.isFlownBy(player.getUUID()), "Brille mit alter Bindung an eine andere Drohne verhindert den Start");
            check(drone.pilotMode() == PilotMode.FPV, "Brille mit alter Bindung fliegt nicht FPV: " + drone.pilotMode());
            check(!ContentIds.wornGoggles(player).has(CoreComponents.BOUND_DRONE.get()), "alte Brillen-Bindung bleibt auf dem Kopf stehen");
            DronePiloting.release(drone);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }));
    }

    private static StepResult switchStep(SelfTest.StepContext ctx) {
        return phases(ctx, "Brille auf und ab", List.of(server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            DronePiloting.useRemote(player, player.getMainHandItem());
            check(drone.isFlownBy(player.getUUID()), "Sichtflug startet nicht");
            check(drone.pilotMode() == PilotMode.LINE_OF_SIGHT, "ohne Brille kein Sichtflug: " + drone.pilotMode());
        }, server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            check(drone.pilotMode() == PilotMode.LINE_OF_SIGHT, "Sichtflug hält nicht: " + drone.pilotMode());
            check(DronePiloting.viewOf(player.getUUID()).orElse(null) == PilotRules.Decision.LINE_OF_SIGHT, "Server prüft die Sicht nicht");
            player.setItemSlot(EquipmentSlot.HEAD, goggles(ContentIds.GOGGLES_DIGITAL));
        }, server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            check(drone.isFlownBy(player.getUUID()), "Aufsetzen der Brille beendet den Flug");
            check(drone.pilotMode() == PilotMode.FPV, "passende Brille im Flug schaltet nicht auf FPV: " + drone.pilotMode());
            player.setItemSlot(EquipmentSlot.HEAD, goggles(ContentIds.GOGGLES_CREATIVE));
        }, server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            check(drone.pilotMode() == PilotMode.FPV, "Kreativ-Brille fliegt nicht FPV: " + drone.pilotMode());
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }, server -> {
            ServerPlayer player = player(server);
            QuadcopterEntity drone = drone(player);
            check(drone.isFlownBy(player.getUUID()), "Absetzen der Brille beendet den Flug");
            check(drone.pilotMode() == PilotMode.LINE_OF_SIGHT, "ohne Brille kein Sichtflug mehr: " + drone.pilotMode());
            DronePiloting.release(drone);
        }));
    }

    private static StepResult blindStep(SelfTest.StepContext ctx) {
        if (ctx.tick() < GAP_TICKS) {
            return StepResult.pending();
        }
        int stage = (int) ctx.state().getOrDefault("blind_stage", 0);
        switch (stage) {
            case 0 -> {
                StepResult result = server(ctx, "blind_start", server -> {
                    ServerPlayer player = player(server);
                    QuadcopterEntity drone = drone(player);
                    player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    DronePiloting.useRemote(player, player.getMainHandItem());
                    check(drone.isFlownBy(player.getUUID()) && drone.pilotMode() == PilotMode.LINE_OF_SIGHT, "Sichtflug startet nicht");
                });
                if (result != null) {
                    return result;
                }
                if (!PilotSession.INSTANCE.isLineOfSight()) {
                    return waitFor(ctx, "blind_los", 200, "Client kommt nicht in den Sichtflug: " + clientState());
                }
                if (GogglesBlackout.active()) {
                    return StepResult.fail("kein Bild schon ohne Brille");
                }
                return blindNext(ctx, 1);
            }
            case 1 -> {
                StepResult result = server(ctx, "blind_goggles",
                        server -> player(server).setItemSlot(EquipmentSlot.HEAD, goggles(ContentIds.GOGGLES_ANALOG)));
                if (result != null) {
                    return result;
                }
                GogglesBlackout.Mismatch mismatch = GogglesBlackout.current();
                if (mismatch == null) {
                    return waitFor(ctx, "blind_on", 200, "falsche Brille im Flug zeigt weiter die Welt: " + clientState());
                }
                if (mismatch.goggles() != VideoSystem.ANALOG || mismatch.video() != VideoSystem.DIGITAL) {
                    return StepResult.fail("falsche Systeme im Hinweis: " + mismatch);
                }
                long first = (long) ctx.state().computeIfAbsent("blind_frames", k -> GogglesBlackout.frames());
                if (GogglesBlackout.frames() < first + 5) {
                    return waitFor(ctx, "blind_draw", 200, "Hinweis „kein Bild“ wird nicht gezeichnet");
                }
                ctx.state().put("blind_radio", RadioHud.renderedFrames());
                return blindNext(ctx, 2);
            }
            case 2 -> {
                if (ctx.tick() - (int) ctx.state().get("blind_since") < 10) {
                    return StepResult.pending();
                }
                if (RadioHud.renderedFrames() != (int) ctx.state().get("blind_radio")) {
                    return StepResult.fail("Funk-HUD liegt über dem Hinweis „kein Bild“");
                }
                if (!PilotSession.INSTANCE.isLineOfSight()) {
                    return StepResult.fail("falsche Brille im Flug beendet die Sitzung beim Client: " + clientState());
                }
                StepResult result = server(ctx, "blind_check", server -> {
                    ServerPlayer player = player(server);
                    QuadcopterEntity drone = drone(player);
                    check(drone.isFlownBy(player.getUUID()), "falsche Brille im Flug trennt die Funke");
                    check(drone.pilotMode() == PilotMode.LINE_OF_SIGHT, "falsche Brille im Flug gibt FPV: " + drone.pilotMode());
                    check(DronePiloting.viewOf(player.getUUID()).orElse(null) == PilotRules.Decision.NO_VIDEO,
                            "Server kennt „kein Bild“ nicht: " + DronePiloting.viewOf(player.getUUID()));
                });
                return result != null ? result : blindNext(ctx, 3);
            }
            case 3 -> {
                StepResult result = server(ctx, "blind_off", server -> player(server).setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY));
                if (result != null) {
                    return result;
                }
                if (GogglesBlackout.active()) {
                    return waitFor(ctx, "blind_clear", 200, "Hinweis „kein Bild“ bleibt nach dem Absetzen stehen");
                }
                return blindNext(ctx, 4);
            }
            default -> {
                StepResult result = server(ctx, "blind_end", server -> {
                    ServerPlayer player = player(server);
                    QuadcopterEntity drone = drone(player);
                    check(drone.isFlownBy(player.getUUID()) && drone.pilotMode() == PilotMode.LINE_OF_SIGHT, "nach dem Absetzen kein Sichtflug");
                    check(DronePiloting.viewOf(player.getUUID()).orElse(null) == PilotRules.Decision.LINE_OF_SIGHT,
                            "Server bleibt bei „kein Bild“: " + DronePiloting.viewOf(player.getUUID()));
                    DronePiloting.release(drone);
                });
                return result != null ? result : StepResult.pass();
            }
        }
    }

    private static StepResult blindNext(SelfTest.StepContext ctx, int stage) {
        ctx.state().put("blind_stage", stage);
        ctx.state().put("blind_since", ctx.tick());
        return StepResult.pending();
    }

    private static StepResult legacyStep(SelfTest.StepContext ctx) {
        if (Files.exists(BindClient.legacyConfigFile())) {
            return StepResult.fail("alte Datei mit der Binding-Phrase liegt noch im Config-Ordner");
        }
        for (String key : List.of("propwash.drone.bind.phrase_button", "propwash.drone.bind.screen.title", "propwash.drone.bind.via_phrase",
                "propwash.drone.bind.goggles", "propwash.drone.bind.hint_goggles", "propwash.video.config.goggles")) {
            if (Language.getInstance().has(key)) {
                return StepResult.fail("Text der Binding-Phrase noch vorhanden: " + key);
            }
        }
        StepResult result = server(ctx, "legacy_world", server -> check(
                !Files.exists(ReceiverBinds.legacySecretFile(server.getWorldPath(LevelResource.DATA))),
                "Welt-Geheimnis der Binding-Phrase liegt noch in den Weltdaten"));
        return result != null ? result : StepResult.pass();
    }

    private static StepResult cleanupStep(SelfTest.StepContext ctx) {
        return phases(ctx, "Aufräumen", List.of(server -> {
            ServerPlayer player = player(server);
            player.closeContainer();
            UUID id = DRONE.getAndSet(null);
            if (id != null) {
                removeDrone(player, id);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (ContentIds.is(stack, ContentIds.DRONE) && stack.has(DataComponents.CUSTOM_NAME)
                        && stack.get(DataComponents.CUSTOM_NAME).getString().startsWith(NAME)) {
                    player.getInventory().setItem(slot, ItemStack.EMPTY);
                }
            }
        }));
    }

    private static StepResult phases(SelfTest.StepContext ctx, String what, List<Consumer<MinecraftServer>> phases) {
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(PHASE, 0);
        if (phase >= phases.size()) {
            return StepResult.pass();
        }
        Object value = state.get(FUTURE);
        if (!(value instanceof CompletableFuture<?> future)) {
            Minecraft mc = Minecraft.getInstance();
            long now = mc.level == null ? Long.MAX_VALUE / 2 : mc.level.getGameTime();
            long last = LAST_PHASE.get();
            if (now >= last && now - last < GAP_TICKS) {
                return StepResult.pending();
            }
            state.put(FUTURE, ctx.onServer(phases.get(phase)));
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            Throwable cause = future.exceptionNow();
            return StepResult.fail(what + " (Teil " + (phase + 1) + "): " + cause.getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        state.remove(FUTURE);
        state.put(PHASE, phase + 1);
        Minecraft mc = Minecraft.getInstance();
        LAST_PHASE.set(mc.level == null ? Long.MIN_VALUE / 2 : mc.level.getGameTime());
        return phase + 1 >= phases.size() ? StepResult.pass() : StepResult.pending();
    }

    private static @Nullable StepResult server(SelfTest.StepContext ctx, String key, Consumer<MinecraftServer> action) {
        Object value = ctx.state().get(key);
        if (!(value instanceof CompletableFuture<?> future)) {
            ctx.state().put(key, ctx.onServer(action));
            return StepResult.pending();
        }
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(future.exceptionNow().getMessage());
        }
        return future.isDone() ? null : StepResult.pending();
    }

    private static StepResult waitFor(SelfTest.StepContext ctx, String key, int ticks, String message) {
        int since = (int) ctx.state().computeIfAbsent(key, k -> ctx.tick());
        return ctx.tick() - since > ticks ? StepResult.fail(message) : StepResult.pending();
    }

    private static String clientState() {
        Minecraft mc = Minecraft.getInstance();
        PilotSession session = PilotSession.INSTANCE;
        StringBuilder out = new StringBuilder("Sitzung " + session.mode() + (session.isActive() ? " aktiv" : " inaktiv"));
        UUID id = DRONE.get();
        if (mc.level != null && id != null && mc.level.getEntity(id) instanceof QuadcopterEntity drone) {
            out.append(", Drohne beim Client: Pilot ").append(drone.pilot().isPresent()).append(", Modus ").append(drone.pilotMode());
        } else {
            out.append(", Drohne beim Client unbekannt");
        }
        if (mc.player != null) {
            out.append(", Hand ").append(mc.player.getMainHandItem().getItem()).append(", Bildschirm ").append(mc.gui.screen());
        }
        return out.toString();
    }

    private static ItemStack goggles(Identifier id) {
        return ContentIds.item(id).map(ItemStack::new).orElseThrow(() -> new IllegalStateException("Brille fehlt: " + id));
    }

    private static StepResult timeout(SelfTest.StepContext ctx, String key, int ticks, String message) {
        int since = (int) ctx.state().getOrDefault(key, ctx.tick());
        return ctx.tick() - since > ticks ? StepResult.fail(message) : StepResult.pending();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static QuadcopterEntity drone(ServerPlayer player) {
        UUID id = DRONE.get();
        if (id == null || !(player.level().getEntity(id) instanceof QuadcopterEntity drone) || drone.isRemoved()) {
            throw new IllegalStateException("Testdrohne fehlt (" + id + ")");
        }
        return drone;
    }

    private static QuadcopterEntity place(ServerPlayer player, ItemStack stack, int dx) {
        ServerLevel level = player.level();
        int x = player.getBlockX() + dx;
        int z = player.getBlockZ();
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos ground = new BlockPos(x, top - 1, z);
        ItemStack previous = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 click = new Vec3(x + 0.5, top, z + 0.5);
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(click, Direction.UP, ground, false)));
        player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        check(result.consumesAction(), "Absetzen lieferte " + result);
        UUID wanted = stack.get(CoreComponents.DRONE_ID.get());
        List<QuadcopterEntity> drones = level.getEntitiesOfClass(QuadcopterEntity.class, new AABB(ground).inflate(1.5),
                drone -> !drone.isRemoved() && (wanted == null || wanted.equals(drone.getUUID()) || drone.tickCount == 0));
        check(!drones.isEmpty(), "keine Drohne nach dem Absetzen");
        return drones.stream().filter(drone -> drone.tickCount == 0).findFirst().orElse(drones.getFirst());
    }

    private static ItemStack findDrone(ServerPlayer player, UUID id) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ContentIds.is(stack, ContentIds.DRONE) && id.equals(stack.get(CoreComponents.DRONE_ID.get()))) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                return stack;
            }
        }
        throw new IllegalStateException("aufgehobene Drohne nicht im Inventar");
    }

    private static void removeDrone(ServerPlayer player, UUID id) {
        Entity entity = player.level().getEntity(id);
        if (entity instanceof QuadcopterEntity drone && !drone.isRemoved()) {
            if (drone.pilot().isPresent()) {
                DronePiloting.release(drone);
            }
            drone.discard();
        }
    }

    private static void clearBound(ServerPlayer player, @Nullable UUID keep) {
        List<QuadcopterEntity> drones = player.level().getEntitiesOfClass(QuadcopterEntity.class,
                player.getBoundingBox().inflate(ReceiverBinds.SEARCH_RANGE + 8.0),
                drone -> !drone.isRemoved() && !drone.getUUID().equals(keep) && !drone.getUUID().equals(DRONE.get())
                        && ReceiverBinds.access(player, drone).matches());
        for (QuadcopterEntity drone : drones) {
            removeDrone(player, drone.getUUID());
        }
    }

    private static void openService(ServerPlayer player, QuadcopterEntity drone) {
        Services.PLATFORM.openMenu(player, new SimpleMenuProvider((containerId, inventory, p) -> new DroneServiceMenu(containerId, inventory, drone),
                DroneServiceMenu.TITLE), ByteBufCodecs.VAR_INT, drone.getId());
    }

    private static final class BlindScene implements Screenshots.Scene {
        private final Identifier goggles;
        private final DroneBuild build;
        private volatile @Nullable UUID drone;

        private BlindScene(Identifier goggles, DroneBuild build) {
            this.goggles = goggles;
            this.build = build;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            StepResult result = server(ctx, "blind_scene_start", server -> {
                ServerPlayer player = player(server);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                ItemStack stack = ContentTab.presetDrone(this.build);
                stack.set(DataComponents.CUSTOM_NAME, Component.literal("Nighthawk"));
                QuadcopterEntity placed = place(player, stack, 3);
                this.drone = placed.getUUID();
                ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
                ReceiverBinds.point(remote, placed);
                player.setItemInHand(InteractionHand.MAIN_HAND, remote);
                check(DronePiloting.tryStart(player, placed, PilotMode.LINE_OF_SIGHT).success(), "Sichtflug startet nicht");
            });
            if (result != null) {
                return result;
            }
            if (!PilotSession.INSTANCE.isLineOfSight()) {
                return waitFor(ctx, "blind_scene_los", 200, "Client kommt nicht in den Sichtflug");
            }
            result = server(ctx, "blind_scene_goggles", server -> player(server).setItemSlot(EquipmentSlot.HEAD, goggles(this.goggles)));
            if (result != null) {
                return result;
            }
            return GogglesBlackout.active() ? StepResult.pass() : waitFor(ctx, "blind_scene_on", 200, "Hinweis „kein Bild“ erscheint nicht");
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            UUID id = this.drone;
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                if (id != null) {
                    removeDrone(player, id);
                }
            });
        }
    }

    private static final class ServiceScene implements Screenshots.Scene {
        private final boolean armed;
        private volatile @Nullable UUID drone;

        private ServiceScene(boolean armed) {
            this.armed = armed;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Object value = ctx.state().get(FUTURE);
            if (!(value instanceof CompletableFuture<?> future)) {
                ctx.state().put(FUTURE, ctx.onServer(server -> {
                    ServerPlayer player = player(server);
                    ItemStack stack = ContentTab.presetDrone(DroneBuild.FREESTYLE);
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal("Nighthawk"));
                    QuadcopterEntity placed = place(player, stack, 3);
                    placed.setReceiverBind(ReceiverBind.of(player.getUUID()));
                    this.drone = placed.getUUID();
                    openService(player, placed);
                }));
                return StepResult.pending();
            }
            if (future.isCompletedExceptionally()) {
                return StepResult.fail("Wartungsmenü: " + future.exceptionNow());
            }
            if (!future.isDone()) {
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof DroneServiceScreen screen) || screen.getMenu().bindAccess() != BindAccess.PILOT) {
                return ctx.tick() > 200 ? StepResult.fail("Wartungsmenü ohne Empfänger: " + mc.gui.screen()) : StepResult.pending();
            }
            if (this.armed && !screen.bindResetArmed()) {
                screen.armBindReset();
            }
            return StepResult.pass();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof DroneServiceScreen screen) {
                double x = screen.receiverChipCenterX() * (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
                double y = screen.receiverChipCenterY() * (double) mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
                mc.mouseHandler.onMove(mc.getWindow().handle(), x, y, 0.0, 0.0);
                if (this.armed && !screen.bindResetArmed()) {
                    screen.armBindReset();
                }
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            UUID id = this.drone;
            ctx.onServer(server -> {
                ServerPlayer player = player(server);
                player.closeContainer();
                if (id != null) {
                    removeDrone(player, id);
                }
            });
        }
    }
}
