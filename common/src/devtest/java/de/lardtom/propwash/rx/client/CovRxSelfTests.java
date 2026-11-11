package de.lardtom.propwash.rx.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.content.ItemHelp;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.guide.client.TrainingSelfTests;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.RadioSource;
import de.lardtom.propwash.input.RadioState;
import de.lardtom.propwash.input.StickChannel;
import de.lardtom.propwash.input.StickMode;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.FunctionKeys;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.rx.RxBlock;
import de.lardtom.propwash.rx.RxBlockEntity;
import de.lardtom.propwash.rx.RxConfigC2S;
import de.lardtom.propwash.rx.RxLinkState;
import de.lardtom.propwash.rx.RxModule;
import de.lardtom.propwash.rx.RxServer;
import de.lardtom.propwash.rx.RxSettings;
import de.lardtom.propwash.rx.RxSource;
import de.lardtom.propwash.rx.RxText;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.client.LayoutProbe;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class CovRxSelfTests {
    public static final String GROUP = "rx: Abdeckung – ";
    static final int SITE_X = 1520;
    static final int SITE_Z = -1520;
    static final int AXES = 10;
    static final int THROTTLE = 2;
    static final int ROLL = 0;
    static final int FREE_AUX = 8;
    static final int SECOND_AUX = 9;
    static final int SETTLE = 20;
    static final String TOOLTIP_EN = "Turns one channel of your radio into a redstone signal (0–15).";
    static final String TOOLTIP_DE = "Macht einen Kanal deiner Funke zum Redstone-Signal (0–15).";
    static final int SMALLEST_GUI_WIDTH = 320;
    static final int TOOLTIP_FRAME = 8;
    static final AtomicReference<BlockPos> SITE = new AtomicReference<>();
    static final AtomicReference<Saved> SAVED = new AtomicReference<>();
    static final AtomicInteger DRONE = new AtomicInteger(-1);
    static final AtomicInteger INT = new AtomicInteger();
    static final AtomicReference<String> TEXT = new AtomicReference<>("");
    private static @Nullable SimulatedRadio radio;
    private static @Nullable SimulatedRadio pad;
    private static @Nullable Boolean keyboardFallback;

    private CovRxSelfTests() {
    }

    record Saved(Vec3 position, float yaw, float pitch, ItemStack head, ItemStack hand, GameType mode) {
    }

    static BlockPos site() {
        BlockPos site = SITE.get();
        if (site == null) {
            throw new IllegalStateException("Testplatz der Abdeckung fehlt");
        }
        return site;
    }

    static BlockPos main() {
        return site().east(2);
    }

    static BlockPos mainLamp() {
        return site().east(3);
    }

    static SimulatedRadio radio() {
        SimulatedRadio current = radio;
        if (current == null) {
            current = new SimulatedRadio("propwash-cov-rx", "Propwash-Testfunke (Abdeckung RX)", ProfileDefaults.radio("Propwash-Testfunke", AXES), AXES, 0);
            for (int axis = 0; axis < AXES; axis++) {
                current.setMicros(axis, axis == THROTTLE || axis >= ProfileDefaults.AUX_ARM ? 1000 : 1500);
            }
            radio = current;
        }
        TransmitterInput.INSTANCE.simulate(current);
        return current;
    }

    static void micros(int axis, int value) {
        radio().setMicros(axis, value);
    }

    static @Nullable RxBlockEntity entity(BlockPos pos) {
        return RxClient.blockEntity(pos);
    }

    static RxSelfTests.Phase[] fromSite(RxSelfTests.Phase... phases) {
        RxSelfTests.Phase[] all = new RxSelfTests.Phase[phases.length + 2];
        all[0] = RxSelfTests.phase("Ausgangslage am Testplatz der Abdeckung", 600, (ctx, run) -> {
            Minecraft mc = Minecraft.getInstance();
            if (run.waited(ctx) == 0) {
                release(mc);
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                radio();
                micros(THROTTLE, 1000);
                micros(ProfileDefaults.AUX_ARM, 1000);
                micros(ROLL, 1500);
                micros(FREE_AUX, 1000);
                micros(SECOND_AUX, 1000);
            }
            return RxSelfTests.server(ctx, run, CovRxSelfTests::baseline);
        });
        all[1] = RxSelfTests.phase("Spieler am Testplatz der Abdeckung", 600, (ctx, run) -> {
            Minecraft mc = Minecraft.getInstance();
            boolean ready = mc.player != null && mc.level != null && mc.level.dimension() == Level.OVERWORLD && !PilotSession.INSTANCE.isActive()
                    && entity(main()) != null && mc.player.position().distanceTo(Vec3.atBottomCenterOf(site())) < 0.3
                    && ContentIds.isRemote(mc.player.getMainHandItem());
            return RxSelfTests.until(ready && run.waited(ctx) > 10, "Spieler, Funke oder Block fehlt");
        });
        System.arraycopy(phases, 0, all, 2, phases.length);
        return all;
    }

    static SelfTest.ClientStep flow(RxSelfTests.Phase... phases) {
        return RxSelfTests.flow(fromSite(phases));
    }

    static void release(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyShift.setDown(false);
        KeyMapping reset = FunctionKeys.mapping(SwitchFunction.RESET);
        if (reset != null) {
            reset.setDown(false);
        }
    }

    static void baseline(MinecraftServer server) {
        ServerPlayer player = RxSelfTests.player(server);
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        discardDrone(server);
        ServerLevel level = server.overworld();
        if (SITE.get() == null) {
            SAVED.compareAndSet(null, new Saved(player.position(), player.getYRot(), player.getXRot(), player.getItemBySlot(EquipmentSlot.HEAD).copy(),
                    player.getMainHandItem().copy(), player.gameMode()));
            BlockPos spawn = level.getRespawnData().pos();
            BlockPos site = RxSelfTests.ground(level, spawn.getX() + SITE_X, spawn.getZ() + SITE_Z);
            SITE.set(site);
            for (int x = -8; x <= 12; x++) {
                for (int z = -8; z <= 8; z++) {
                    for (int y = 0; y <= 5; y++) {
                        level.setBlock(site.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                    level.setBlock(site.offset(x, -1, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        if (!(level.getBlockState(main()).getBlock() instanceof RxBlock)) {
            level.setBlock(main(), RxModule.RX_BLOCK.block().get().defaultBlockState(), Block.UPDATE_ALL);
        }
        if (!level.getBlockState(mainLamp()).is(Blocks.REDSTONE_LAMP)) {
            level.setBlock(mainLamp(), Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
        }
        RxSettings wanted = new RxSettings(player.getUUID(), RxSource.GAS, -1.0F, 1.0F, 64);
        if (level.getBlockEntity(main()) instanceof RxBlockEntity entity && !entity.settings().equals(wanted)) {
            entity.apply(wanted);
            RxServer.refreshDemand(player);
        }
        player.setGameMode(GameType.CREATIVE);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneModule.REMOTE.get()));
        teleport(player, level, Vec3.atBottomCenterOf(site()), -90.0F, 20.0F);
    }

    static void teleport(ServerPlayer player, ServerLevel level, Vec3 feet, float yaw, float pitch) {
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), yaw, pitch, true);
    }

    static void discardDrone(MinecraftServer server) {
        int id = DRONE.getAndSet(-1);
        if (id < 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof QuadcopterEntity drone) {
                if (drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                drone.discard();
            }
        }
    }

    static void place(ServerLevel level, BlockPos pos, @Nullable RxSettings settings) {
        level.setBlock(pos, RxModule.RX_BLOCK.block().get().defaultBlockState(), Block.UPDATE_ALL);
        if (settings != null && level.getBlockEntity(pos) instanceof RxBlockEntity entity) {
            entity.apply(settings);
        }
    }

    static void clear(ServerLevel level, BlockPos... positions) {
        for (BlockPos pos : positions) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    static int serverPower(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof RxBlock ? state.getValue(RxBlock.POWER) : -1;
    }

    static boolean lampLit(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.REDSTONE_LAMP) && state.getValue(RedstoneLampBlock.LIT);
    }

    static void startFpv(MinecraftServer server, Vec3 at) {
        ServerPlayer player = RxSelfTests.player(server);
        ServerLevel level = server.overworld();
        discardDrone(server);
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        drone.setBuild(DroneBuild.RACER);
        drone.setBatteryCharge(1.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht in die Welt gesetzt");
        }
        drone.resetTo(at, new Quaternionf());
        DRONE.set(drone.getId());
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_ANALOG).map(ItemStack::new).orElse(ItemStack.EMPTY);
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            RxSelfTests.SERVER.set("FPV-Start verweigert: " + result);
        }
    }

    static Vec3 droneSpot() {
        return Vec3.atBottomCenterOf(site().west(3)).add(0.0, 0.05, 0.0);
    }

    static RxSelfTests.Phase fpvStart(String name) {
        return RxSelfTests.phase(name, 400, (ctx, run) -> RxSelfTests.server(ctx, run, server -> startFpv(server, droneSpot())));
    }

    static RxSelfTests.Phase fpvActive() {
        return RxSelfTests.phase("FPV aktiv", 400, (ctx, run) -> RxSelfTests.until(PilotSession.INSTANCE.isFpv() && run.waited(ctx) > 10,
                "keine FPV-Sitzung"));
    }

    static RxSelfTests.Phase fpvEnd() {
        return RxSelfTests.phase("FPV beenden", 400, (ctx, run) -> {
            micros(THROTTLE, 1000);
            micros(ProfileDefaults.AUX_ARM, 1000);
            if (run.waited(ctx) == 5) {
                ctx.onServer(server -> DronePiloting.pilotedBy(RxSelfTests.player(server).getUUID()).ifPresent(DronePiloting::release));
            }
            if (PilotSession.INSTANCE.isActive() || run.waited(ctx) < 8) {
                return new StepResult(StepResult.Status.PENDING, "Sitzung läuft");
            }
            ctx.onServer(CovRxSelfTests::discardDrone);
            return StepResult.pass();
        });
    }

    static StepResult settle(SelfTest.StepContext ctx, RxSelfTests.Run run, BlockPos pos, int want, RxLinkState link) {
        int power = RxSelfTests.power(pos);
        if (power == want && RxSelfTests.link(pos) == link) {
            return RxSelfTests.stable(ctx, run, power, "Stärke");
        }
        return new StepResult(StepResult.Status.PENDING, RxSelfTests.describe(pos) + " statt " + want + ", " + link);
    }

    static String overlay() {
        try {
            Field field = Hud.class.getDeclaredField("overlayMessageString");
            field.setAccessible(true);
            Object message = field.get(Minecraft.getInstance().gui.hud);
            return message instanceof Component component ? component.getString() : "";
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "? " + e;
        }
    }

    static void clearOverlay() {
        try {
            Field field = Hud.class.getDeclaredField("overlayMessageString");
            field.setAccessible(true);
            field.set(Minecraft.getInstance().gui.hud, null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            TEXT.set("? " + e);
        }
    }

    static String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    static Map<SwitchFunction, Boolean> switches() {
        Map<SwitchFunction, Boolean> out = new EnumMap<>(SwitchFunction.class);
        for (SwitchFunction function : SwitchFunction.values()) {
            out.put(function, RadioState.INSTANCE.isActive(function));
        }
        return out;
    }

    static @Nullable String langValue(String language, String key) {
        Minecraft mc = Minecraft.getInstance();
        Optional<Resource> resource = mc.getResourceManager().getResource(Identifier.fromNamespaceAndPath("propwash", "lang/" + language + ".json"));
        if (resource.isEmpty()) {
            return null;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return json.has(key) ? json.get(key).getAsString() : null;
        } catch (IOException | RuntimeException e) {
            return "? " + e;
        }
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Kreativ-Tab: RX-Block direkt hinter dem Signal-Repeater, Text „… (0–15).“ in en_us und de_de", 40,
                CovRxSelfTests::tabStep);
        SelfTest.clientStep(GROUP + "Tooltip des RX-Block-Items ist propwash.rx.tooltip, direkt unter dem Namen und passend für 320 GUI-Punkte", 40,
                CovRxSelfTests::tooltipStep);
        SelfTest.clientStep(GROUP + "abbauen im Überleben mit der Spitzhacke: kurz, genau ein Item ohne Daten, neu gesetzt ungebunden und LED aus", 1200,
                flow(breakPhases()));
        SelfTest.clientStep(GROUP + "Status ohne Funke in der Aktionsleiste (gebunden und ungebunden), mit einem Block in der Hand wird gebaut", 1600,
                flow(statusPhases()));
        SelfTest.clientStep(GROUP + "Ausgabe an allen Seiten: Lampe, Tür, Kolben und Staub folgen, Lampe hinter dem Steinblock bleibt aus", 1600,
                flow(outputPhases()));
        SelfTest.clientStep(GROUP + "drei Blöcke auf Gas, Roll und AUX-Achse angelernt – jeder folgt nur seinem Kanal", 3600, flow(multiPhases()));
        SelfTest.clientStep(GROUP + "freie AUX-Achse im FPV schaltet Block und Lampe, Drohne bleibt scharf, Flugmodus und Schalter unverändert", 2400,
                flow(auxPhases()));
        SelfTest.clientStep(GROUP + "Tastatur: außerhalb des Flugs ändert W/S nichts, im FPV folgt der Block dem Tastatur-Gas", 2400, flow(keyboardPhases()));
        SelfTest.clientStep(GROUP + "simuliertes Gamepad: Gas anlernen, außerhalb des Flugs folgt der Block", 1600, flow(gamepadPhases()));
        SelfTest.clientStep(GROUP + "Reichweite zählt vom Körper: Drohne 100 Blöcke weg – Block folgt, Körper 30 Blöcke weg – LOST", 2400,
                flow(rangePhases()));
        SelfTest.clientStep(GROUP + "Funke abgemeldet: nach etwa 100 Ticks LOST, Stärke bleibt, Fenster meldet keine Funke, wieder angemeldet folgt", 1600,
                flow(radioLossPhases()));
        SelfTest.clientStep(GROUP + "Nether und zurück: Block in der Oberwelt hält die Stärke, danach LINKED und folgt", 2400, flow(dimensionPhases()));
        SelfTest.clientStep(GROUP + "Reset, /propwash clear und Drohnenwechsel lassen Besitzer, Kanal, Endpunkte und Stärke unverändert", 2400,
                flow(commandPhases()));
        SelfTest.clientStep(GROUP + "aufräumen", 600, RxSelfTests.flow(RxSelfTests.phase("aufräumen", 400, (ctx, run) -> {
            Minecraft mc = Minecraft.getInstance();
            if (run.waited(ctx) == 0) {
                release(mc);
                if (mc.gui.screen() instanceof RxScreen) {
                    mc.gui.setScreen(null);
                }
                TransmitterInput.INSTANCE.simulate(null);
                radio = null;
                pad = null;
                if (keyboardFallback != null) {
                    InputConfig.get().keyboardFallback = keyboardFallback;
                    keyboardFallback = null;
                }
            }
            return RxSelfTests.server(ctx, run, CovRxSelfTests::tearDown);
        })));
    }

    static StepResult tabStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return StepResult.pending();
        }
        CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
        List<ItemStack> items = new ArrayList<>(ContentTab.MAIN.get().getDisplayItems());
        Item repeater = ContentIds.item(ContentIds.SIGNAL_REPEATER).orElse(null);
        Item rx = RxModule.RX_BLOCK.item().get();
        int repeaterAt = -1;
        int rxAt = -1;
        for (int i = 0; i < items.size(); i++) {
            if (repeater != null && items.get(i).is(repeater)) {
                repeaterAt = i;
            }
            if (items.get(i).is(rx)) {
                rxAt = i;
            }
        }
        if (repeaterAt < 0 || rxAt != repeaterAt + 1) {
            return StepResult.fail("Kreativ-Tab: Signal-Repeater an " + repeaterAt + ", RX-Block an " + rxAt + " von " + items.size());
        }
        String en = langValue("en_us", RxText.TOOLTIP);
        String de = langValue("de_de", RxText.TOOLTIP);
        if (!TOOLTIP_EN.equals(en) || !TOOLTIP_DE.equals(de)) {
            return StepResult.fail("Text " + RxText.TOOLTIP + ": en „" + en + "“, de „" + de + "“");
        }
        ctx.log("Kreativ-Tab: Signal-Repeater an " + repeaterAt + ", RX-Block an " + rxAt + "; " + RxText.TOOLTIP + " en „" + en + "“, de „" + de + "“");
        return StepResult.pass();
    }

    static StepResult tooltipStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return StepResult.pending();
        }
        String wanted = Component.translatable(RxText.TOOLTIP).getString();
        String use = Component.translatable(ItemHelp.PREFIX + "rx_block.use.1").getString();
        String fits = Component.translatable(ItemHelp.PREFIX + "rx_block.fits").getString();
        for (boolean shift : new boolean[]{false, true}) {
            List<String> texts = new ArrayList<>();
            int widest = 0;
            TooltipShift.override(shift);
            try {
                ItemStack stack = new ItemStack(RxModule.RX_BLOCK.item().get());
                for (Component line : stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL)) {
                    texts.add(line.getString());
                    widest = Math.max(widest, mc.font.width(line));
                }
            } finally {
                TooltipShift.override(null);
            }
            String below = String.join(" ", texts.subList(Math.min(1, texts.size()), texts.size()));
            if (!below.startsWith(wanted)) {
                return StepResult.fail("Tooltip ohne „" + wanted + "“ direkt unter dem Namen: " + texts);
            }
            if (shift && !(texts.contains(use) && texts.contains(fits))) {
                return StepResult.fail("Tooltip mit Shift ohne „" + use + "“ und „" + fits + "“: " + texts);
            }
            if (widest > SMALLEST_GUI_WIDTH - TOOLTIP_FRAME) {
                return StepResult.fail("Tooltip " + widest + " Punkte breit, passt nicht in " + SMALLEST_GUI_WIDTH + " GUI-Punkte: " + texts);
            }
            ctx.log("Tooltip " + (shift ? "mit" : "ohne") + " Shift " + texts + ", breiteste Zeile " + widest + " Punkte");
        }
        return StepResult.pass();
    }

    static RxSelfTests.Phase[] breakPhases() {
        return new RxSelfTests.Phase[]{
                RxSelfTests.phase("gebundenen Block setzen und im Überleben abbauen", 200, (ctx, run) -> RxSelfTests.check(ctx, run, server -> {
                    ServerPlayer player = RxSelfTests.player(server);
                    ServerLevel level = server.overworld();
                    BlockPos pos = site().offset(5, 0, 3);
                    level.setBlock(pos, RxModule.RX_BLOCK.block().get().defaultBlockState().setValue(RxBlock.POWER, 7).setValue(RxBlock.LINK,
                            RxLinkState.LINKED), Block.UPDATE_ALL);
                    if (!(level.getBlockEntity(pos) instanceof RxBlockEntity entity)) {
                        return "kein Block-Entity";
                    }
                    entity.apply(new RxSettings(player.getUUID(), RxSource.axis(SECOND_AUX), -0.4F, 0.6F, 40));
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
                        return "RX-Block nicht im Tag mineable/pickaxe";
                    }
                    player.setGameMode(GameType.SURVIVAL);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
                    teleport(player, level, Vec3.atBottomCenterOf(pos.west(2)), -90.0F, 30.0F);
                    float progress = state.getDestroyProgress(player, level, pos);
                    int ticks = progress <= 0.0F ? Integer.MAX_VALUE : (int) Math.ceil(1.0F / progress);
                    INT.set(ticks);
                    for (ItemEntity old : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.0))) {
                        old.discard();
                    }
                    if (!player.gameMode.destroyBlock(pos)) {
                        return "destroyBlock lehnt ab";
                    }
                    if (!level.getBlockState(pos).isAir()) {
                        return "Block nach dem Abbau noch da: " + level.getBlockState(pos);
                    }
                    if (ticks > 10) {
                        return "Abbauzeit mit Eisenspitzhacke " + ticks + " Ticks";
                    }
                    return null;
                })),
                RxSelfTests.phase("Drop prüfen und neu setzen", 200, (ctx, run) -> RxSelfTests.check(ctx, run, server -> {
                    ServerPlayer player = RxSelfTests.player(server);
                    ServerLevel level = server.overworld();
                    BlockPos pos = site().offset(5, 0, 3);
                    List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.0));
                    int count = 0;
                    ItemStack dropped = ItemStack.EMPTY;
                    List<String> seen = new ArrayList<>();
                    for (ItemEntity drop : drops) {
                        ItemStack stack = drop.getItem();
                        seen.add(stack.getCount() + "× " + stack.getItem() + " " + stack.getComponentsPatch());
                        if (stack.is(RxModule.RX_BLOCK.item().get())) {
                            count += stack.getCount();
                            dropped = stack.copy();
                        }
                    }
                    if (count != 1 || drops.size() != 1) {
                        return "Drop statt genau einem RX-Block: " + seen;
                    }
                    if (dropped.has(DataComponents.BLOCK_ENTITY_DATA) || !dropped.getComponentsPatch().isEmpty()) {
                        return "Drop trägt Daten: " + dropped.getComponentsPatch();
                    }
                    for (ItemEntity drop : drops) {
                        drop.discard();
                    }
                    player.setItemInHand(InteractionHand.MAIN_HAND, dropped);
                    BlockPos support = pos.below();
                    InteractionResult result = dropped.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(support).add(0.0, 0.5, 0.0), Direction.UP, support, false)));
                    if (!result.consumesAction() || !(level.getBlockState(pos).getBlock() instanceof RxBlock)) {
                        return "neu setzen fehlgeschlagen: " + result;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (!(level.getBlockEntity(pos) instanceof RxBlockEntity entity) || entity.settings().bound() || entity.settings().source() != null
                            || state.getValue(RxBlock.LINK) != RxLinkState.NONE || state.getValue(RxBlock.POWER) != 0) {
                        return "neu gesetzt nicht leer: " + state + " " + (level.getBlockEntity(pos) instanceof RxBlockEntity e ? e.settings() : "-");
                    }
                    run.notes.add("Abbauzeit mit Eisenspitzhacke " + INT.get() + " Ticks, Drop " + seen + ", neu gesetzt ungebunden, LED aus");
                    clear(level, pos);
                    player.setGameMode(GameType.CREATIVE);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneModule.REMOTE.get()));
                    teleport(player, level, Vec3.atBottomCenterOf(site()), -90.0F, 20.0F);
                    return null;
                }))
        };
    }

    static BlockPos unboundPos() {
        return site().offset(-2, 0, -2);
    }

    static RxSelfTests.Phase[] statusPhases() {
        return new RxSelfTests.Phase[]{
                RxSelfTests.phase("ungebundenen Block setzen, leere Hand", 200, (ctx, run) -> RxSelfTests.server(ctx, run, server -> {
                    ServerPlayer player = RxSelfTests.player(server);
                    place(server.overworld(), unboundPos(), null);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                })),
                RxSelfTests.phase("Block beim Client, Gas 0", 300, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    micros(THROTTLE, 1000);
                    boolean ready = mc.player != null && mc.player.getMainHandItem().isEmpty() && entity(unboundPos()) != null
                            && RxSelfTests.power(main()) == 0 && RxSelfTests.link(main()) == RxLinkState.LINKED;
                    if (ready && run.waited(ctx) > 10) {
                        clearOverlay();
                        return StepResult.pass();
                    }
                    return new StepResult(StepResult.Status.PENDING, RxSelfTests.describe(main()));
                }),
                RxSelfTests.phase("Rechtsklick mit leerer Hand auf den gebundenen Block", 200, (ctx, run) -> RxSelfTests.useOn(Minecraft.getInstance(), run,
                        ctx, main())),
                RxSelfTests.phase("Aktionsleiste zeigt Kanal, Stärke, Besitzer und Reichweite", 200, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    RxBlockEntity entity = entity(main());
                    if (entity == null || mc.player == null) {
                        return StepResult.fail("Block fehlt");
                    }
                    String wanted = RxText.status(entity.settings(), RxSelfTests.power(main()), mc.player.getPlainTextName()).getString();
                    String shown = overlay();
                    if (!shown.equals(wanted)) {
                        return new StepResult(StepResult.Status.PENDING, "Aktionsleiste „" + shown + "“ statt „" + wanted + "“");
                    }
                    for (String part : List.of(RxText.source(RxSource.GAS).getString(), Component.translatable(RxText.OUTPUT, 0).getString(),
                            mc.player.getPlainTextName(), RxText.range(64).getString())) {
                        if (!shown.contains(part)) {
                            return StepResult.fail("Aktionsleiste ohne „" + part + "“: " + shown);
                        }
                    }
                    run.notes.add("gebunden: „" + shown + "“");
                    clearOverlay();
                    return StepResult.pass();
                }),
                RxSelfTests.phase("Rechtsklick mit leerer Hand auf den ungebundenen Block", 200, (ctx, run) -> RxSelfTests.useOn(Minecraft.getInstance(),
                        run, ctx, unboundPos())),
                RxSelfTests.phase("Aktionsleiste „nicht gebunden“", 200, (ctx, run) -> {
                    String wanted = Component.translatable(RxText.STATUS_UNBOUND).getString();
                    String shown = overlay();
                    if (!shown.equals(wanted)) {
                        return new StepResult(StepResult.Status.PENDING, "Aktionsleiste „" + shown + "“ statt „" + wanted + "“");
                    }
                    RxBlockEntity entity = entity(unboundPos());
                    if (entity == null || entity.settings().bound()) {
                        return StepResult.fail("Rechtsklick mit leerer Hand hat gebunden");
                    }
                    run.notes.add("ungebunden: „" + shown + "“");
                    return StepResult.pass();
                }),
                RxSelfTests.phase("Steinblock in die Hand", 200, (ctx, run) -> {
                    StepResult given = RxSelfTests.server(ctx, run, server -> RxSelfTests.player(server).setItemInHand(InteractionHand.MAIN_HAND,
                            new ItemStack(Items.STONE, 4)));
                    if (given.status() != StepResult.Status.PASS) {
                        return given;
                    }
                    Minecraft mc = Minecraft.getInstance();
                    return RxSelfTests.until(mc.player != null && mc.player.getMainHandItem().is(Items.STONE), "kein Stein in der Hand");
                }),
                RxSelfTests.phase("Rechtsklick mit Stein auf den Block", 200, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    LocalPlayer player = mc.player;
                    if (player == null) {
                        return StepResult.pending();
                    }
                    RxSelfTests.lookAt(player, Vec3.atCenterOf(unboundPos()).add(0.0, -0.2, 0.0));
                    if (!RxSelfTests.aimsAt(mc, unboundPos()) || run.waited(ctx) < 3) {
                        return new StepResult(StepResult.Status.PENDING, "Ziel " + mc.hitResult);
                    }
                    BlockHitResult hit = (BlockHitResult) mc.hitResult;
                    BlockPos target = unboundPos().relative(hit.getDirection());
                    ctx.state().put("cov_rx_target", target);
                    RxSelfTests.clickUse(mc);
                    return StepResult.pass();
                }),
                RxSelfTests.phase("Stein wurde normal gesetzt", 200, (ctx, run) -> {
                    BlockPos target = (BlockPos) ctx.state().get("cov_rx_target");
                    StepResult placed = RxSelfTests.check(ctx, run, server -> {
                        ServerLevel level = server.overworld();
                        if (!level.getBlockState(target).is(Blocks.STONE)) {
                            return "warte";
                        }
                        return level.getBlockState(unboundPos()).getBlock() instanceof RxBlock ? null : "RX-Block verschwunden";
                    });
                    if (placed.status() == StepResult.Status.FAIL && placed.message().equals("warte")) {
                        run.future = null;
                        return new StepResult(StepResult.Status.PENDING, "kein Stein bei " + target);
                    }
                    if (placed.status() == StepResult.Status.PASS) {
                        run.notes.add("mit Stein in der Hand: Stein bei " + target.subtract(unboundPos()) + " neben dem Block gesetzt");
                        ctx.onServer(server -> {
                            clear(server.overworld(), target, unboundPos());
                            RxSelfTests.player(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneModule.REMOTE.get()));
                        });
                    }
                    return placed;
                })
        };
    }

    record Output(BlockPos rx, BlockPos north, BlockPos east, BlockPos west, BlockPos south, BlockPos up, BlockPos down, BlockPos second, BlockPos stone,
                  BlockPos behind) {
        static Output of(BlockPos site) {
            BlockPos rx = site.offset(6, 1, -4);
            BlockPos second = site.offset(6, 0, 4);
            return new Output(rx, rx.north(), rx.east(), rx.west(), rx.south(), rx.above(), rx.below(), second, second.west(), second.west(2));
        }
    }

    static void buildOutputs(MinecraftServer server) {
        ServerPlayer player = RxSelfTests.player(server);
        ServerLevel level = server.overworld();
        Output o = Output.of(site());
        level.setBlock(o.down(), Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(o.north().below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(o.north(), Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(o.up(), Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(o.east().below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(o.east(), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(o.west().below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(o.west(), Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(o.south().below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        level.setBlock(o.south(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), flags);
        level.setBlock(o.south().above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), flags);
        level.setBlock(o.stone(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(o.behind(), Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
        RxSettings settings = new RxSettings(player.getUUID(), RxSource.GAS, -1.0F, 1.0F, 64);
        place(level, o.rx(), settings);
        place(level, o.second(), settings);
        RxServer.refreshDemand(player);
    }

    static @Nullable String outputs(MinecraftServer server, boolean on) {
        ServerLevel level = server.overworld();
        Output o = Output.of(site());
        int want = on ? 15 : 0;
        List<String> wrong = new ArrayList<>();
        if (serverPower(level, o.rx()) != want || serverPower(level, o.second()) != want) {
            wrong.add("Stärke " + serverPower(level, o.rx()) + "/" + serverPower(level, o.second()));
        }
        for (BlockPos lamp : List.of(o.north(), o.up(), o.down())) {
            if (lampLit(level, lamp) != on) {
                wrong.add("Lampe " + lamp.subtract(o.rx()) + " " + (on ? "aus" : "an"));
            }
        }
        BlockState door = level.getBlockState(o.south());
        if (!door.is(Blocks.OAK_DOOR) || door.getValue(DoorBlock.OPEN) != on) {
            wrong.add("Tür " + door);
        }
        BlockState piston = level.getBlockState(o.east());
        if (!piston.is(Blocks.PISTON) || piston.getValue(PistonBaseBlock.EXTENDED) != on) {
            wrong.add("Kolben " + piston);
        }
        BlockState dust = level.getBlockState(o.west());
        if (!dust.is(Blocks.REDSTONE_WIRE) || dust.getValue(RedstoneWireBlock.POWER) > 0 != on) {
            wrong.add("Staub " + dust);
        }
        if (lampLit(level, o.behind())) {
            wrong.add("Lampe hinter dem Steinblock an");
        }
        return wrong.isEmpty() ? null : String.join(", ", wrong);
    }

    static RxSelfTests.Phase[] outputPhases() {
        return new RxSelfTests.Phase[]{
                RxSelfTests.phase("Lampe, Tür, Kolben, Staub und Stein mit Lampe bauen", 200, (ctx, run) -> RxSelfTests.server(ctx, run,
                        CovRxSelfTests::buildOutputs)),
                RxSelfTests.phase("Gas 0 % – alles aus", 400, (ctx, run) -> {
                    micros(THROTTLE, 1000);
                    return waitOutputs(ctx, run, false);
                }),
                RxSelfTests.phase("Gas 100 % – alle direkten Nachbarn an, Lampe hinter dem Stein aus", 400, (ctx, run) -> {
                    micros(THROTTLE, 2000);
                    StepResult result = waitOutputs(ctx, run, true);
                    if (result.status() == StepResult.Status.PASS) {
                        run.notes.add("Gas 100 %: Lampen oben, unten und seitlich an, Tür offen, Kolben ausgefahren, Staub an; Lampe hinter dem Steinblock aus");
                    }
                    return result;
                }),
                RxSelfTests.phase("Gas 0 % – wieder alles aus", 400, (ctx, run) -> {
                    micros(THROTTLE, 1000);
                    StepResult result = waitOutputs(ctx, run, false);
                    if (result.status() == StepResult.Status.PASS) {
                        run.notes.add("Gas 0 %: alles aus");
                        ctx.onServer(server -> {
                            Output o = Output.of(site());
                            ServerLevel level = server.overworld();
                            clear(level, o.rx(), o.second(), o.north(), o.up(), o.east(), o.east().east(), o.west(), o.south().above(), o.south(),
                                    o.stone(), o.behind());
                            level.setBlock(o.down(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                            for (BlockPos below : List.of(o.north().below(), o.east().below(), o.west().below(), o.south().below())) {
                                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                            }
                        });
                    }
                    return result;
                })
        };
    }

    static StepResult waitOutputs(SelfTest.StepContext ctx, RxSelfTests.Run run, boolean on) {
        if (run.waited(ctx) < 12) {
            return StepResult.pending();
        }
        StepResult checked = RxSelfTests.check(ctx, run, server -> outputs(server, on));
        if (checked.status() == StepResult.Status.FAIL) {
            run.future = null;
            return new StepResult(StepResult.Status.PENDING, checked.message());
        }
        return checked;
    }

    static BlockPos multi(int index) {
        return site().offset(1 + 2 * index, 0, -2);
    }

    static RxSelfTests.Phase learn(int index, RxSource source, int axis, int from, int to) {
        return RxSelfTests.phase("anlernen: " + source, 400, (ctx, run) -> {
            Minecraft mc = Minecraft.getInstance();
            int waited = run.waited(ctx);
            if (waited == 0) {
                mc.gui.setScreen(new RxScreen(multi(index), false));
                micros(axis, from);
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof RxScreen screen)) {
                return StepResult.fail("Fenster zu");
            }
            if (waited == 4) {
                screen.startLearning();
            }
            if (waited >= 6) {
                micros(axis, waited < 9 ? from + (to - from) / 3 : waited < 12 ? from + 2 * (to - from) / 3 : to);
            }
            RxBlockEntity entity = entity(multi(index));
            if (entity != null && source.equals(entity.settings().source()) && !screen.learning()) {
                mc.gui.setScreen(null);
                micros(axis, from);
                run.notes.add("Block " + (index + 1) + " angelernt: " + RxText.source(source).getString());
                return StepResult.pass();
            }
            return new StepResult(StepResult.Status.PENDING, "Quelle " + (entity == null ? "-" : entity.settings().source()));
        });
    }

    static RxSelfTests.Phase move(String name, int axis, int value, int moved, int[] expected) {
        return RxSelfTests.phase(name, 400, (ctx, run) -> {
            micros(axis, value);
            int[] now = new int[3];
            for (int i = 0; i < 3; i++) {
                now[i] = RxSelfTests.power(multi(i));
            }
            if (run.waited(ctx) < SETTLE) {
                return StepResult.pending();
            }
            for (int i = 0; i < 3; i++) {
                boolean ok = expected[i] < 0 ? now[i] == 7 || now[i] == 8 : now[i] == expected[i];
                if (!ok) {
                    if (i != moved) {
                        return StepResult.fail("Block " + (i + 1) + " änderte sich mit Kanal von Block " + (moved + 1) + ": " + Arrays.toString(now));
                    }
                    return new StepResult(StepResult.Status.PENDING, "Stärken " + Arrays.toString(now));
                }
            }
            StepResult stable = RxSelfTests.stable(ctx, run, now[0] * 256 + now[1] * 16 + now[2], "Stärken");
            if (stable.status() == StepResult.Status.PASS) {
                run.notes.add(name + ": " + Arrays.toString(now));
            }
            return stable;
        });
    }

    static RxSelfTests.Phase[] multiPhases() {
        return new RxSelfTests.Phase[]{
                RxSelfTests.phase("drei gebundene Blöcke ohne Kanal", 200, (ctx, run) -> RxSelfTests.server(ctx, run, server -> {
                    ServerPlayer player = RxSelfTests.player(server);
                    ServerLevel level = server.overworld();
                    for (int i = 0; i < 3; i++) {
                        place(level, multi(i), new RxSettings(player.getUUID(), null, -1.0F, 1.0F, 64));
                    }
                    teleport(player, level, Vec3.atBottomCenterOf(site().offset(3, 0, 1)), 180.0F, 30.0F);
                })),
                RxSelfTests.phase("Blöcke beim Client", 300, (ctx, run) -> {
                    boolean ready = true;
                    for (int i = 0; i < 3; i++) {
                        RxBlockEntity entity = entity(multi(i));
                        ready &= entity != null && entity.settings().bound();
                    }
                    return RxSelfTests.until(ready && run.waited(ctx) > 10, "Blöcke fehlen");
                }),
                learn(0, RxSource.GAS, THROTTLE, 1000, 2000),
                learn(1, RxSource.stick(ROLL), ROLL, 1500, 2000),
                learn(2, RxSource.axis(FREE_AUX), FREE_AUX, 1000, 2000),
                move("Ruhe: Gas 0, Roll Mitte, AUX unten", THROTTLE, 1000, 0, new int[]{0, -1, 0}),
                move("Gas 100 %", THROTTLE, 2000, 0, new int[]{15, -1, 0}),
                move("Roll ganz rechts", ROLL, 2000, 1, new int[]{15, 15, 0}),
                move("AUX-Achse oben", FREE_AUX, 2000, 2, new int[]{15, 15, 15}),
                move("Gas 0 %", THROTTLE, 1000, 0, new int[]{0, 15, 15}),
                RxSelfTests.phase("aufräumen", 200, (ctx, run) -> {
                    micros(ROLL, 1500);
                    micros(FREE_AUX, 1000);
                    return RxSelfTests.server(ctx, run, server -> clear(server.overworld(), multi(0), multi(1), multi(2)));
                })
        };
    }

    static BlockPos auxBlock() {
        return site().offset(1, 0, 3);
    }

    static BlockPos auxLamp() {
        return site().offset(2, 0, 3);
    }

    static RxSelfTests.Phase[] auxPhases() {
        RxSelfTests.Phase[] toggles = new RxSelfTests.Phase[4];
        for (int i = 0; i < toggles.length; i++) {
            boolean up = i % 2 == 0;
            toggles[i] = RxSelfTests.phase("AUX " + (up ? "an" : "aus") + " (" + (i / 2 + 1) + ")", 300, (ctx, run) -> {
                micros(SECOND_AUX, up ? 2000 : 1000);
                String problem = droneUnchanged(ctx);
                if (problem != null) {
                    return StepResult.fail(problem);
                }
                int power = RxSelfTests.power(auxBlock());
                boolean lamp = RxSelfTests.lit(auxLamp());
                if (power == (up ? 15 : 0) && lamp == up && RxSelfTests.link(auxBlock()) == RxLinkState.LINKED) {
                    StepResult stable = RxSelfTests.stable(ctx, run, power, "Stärke");
                    if (stable.status() == StepResult.Status.PASS) {
                        run.notes.add("AUX " + (up ? "an" : "aus") + ": Stärke " + power + ", Lampe " + (lamp ? "an" : "aus"));
                    }
                    return stable;
                }
                return new StepResult(StepResult.Status.PENDING, RxSelfTests.describe(auxBlock()) + ", Lampe " + lamp);
            });
        }
        List<RxSelfTests.Phase> phases = new ArrayList<>();
        phases.add(RxSelfTests.phase("zweiter Block auf freie AUX-Achse " + (SECOND_AUX + 1), 200, (ctx, run) -> RxSelfTests.server(ctx, run, server -> {
            ServerPlayer player = RxSelfTests.player(server);
            ServerLevel level = server.overworld();
            place(level, auxBlock(), new RxSettings(player.getUUID(), RxSource.axis(SECOND_AUX), -1.0F, 1.0F, 64));
            level.setBlock(auxLamp(), Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
            RxServer.refreshDemand(player);
        })));
        phases.add(fpvStart("Drohne und FPV-Start"));
        phases.add(fpvActive());
        phases.add(RxSelfTests.phase("scharf schalten", 300, (ctx, run) -> {
            micros(THROTTLE, 1000);
            micros(ProfileDefaults.AUX_ARM, run.waited(ctx) < 10 ? 1000 : 2000);
            if (!PilotSession.INSTANCE.armed() || run.waited(ctx) < 30) {
                return new StepResult(StepResult.Status.PENDING, "nicht scharf");
            }
            ctx.state().put("cov_rx_mode", PilotSession.INSTANCE.flightMode());
            ctx.state().put("cov_rx_switches", switches());
            run.notes.add("scharf, Flugmodus " + PilotSession.INSTANCE.flightMode());
            return StepResult.pass();
        }));
        phases.addAll(List.of(toggles));
        phases.add(fpvEnd());
        phases.add(RxSelfTests.phase("aufräumen", 200, (ctx, run) -> RxSelfTests.server(ctx, run, server -> clear(server.overworld(), auxBlock(),
                auxLamp()))));
        return phases.toArray(new RxSelfTests.Phase[0]);
    }

    @SuppressWarnings("unchecked")
    static @Nullable String droneUnchanged(SelfTest.StepContext ctx) {
        PilotSession session = PilotSession.INSTANCE;
        if (!session.isFpv()) {
            return "FPV-Sitzung endete";
        }
        if (!session.armed()) {
            return "Drohne nicht mehr scharf";
        }
        StabMode mode = (StabMode) ctx.state().get("cov_rx_mode");
        if (session.flightMode() != mode) {
            return "Flugmodus " + mode + " → " + session.flightMode();
        }
        Map<SwitchFunction, Boolean> before = (Map<SwitchFunction, Boolean>) ctx.state().get("cov_rx_switches");
        Map<SwitchFunction, Boolean> now = switches();
        if (before != null && !before.equals(now)) {
            List<String> changed = new ArrayList<>();
            for (SwitchFunction function : SwitchFunction.values()) {
                if (!Objects.equals(before.get(function), now.get(function))) {
                    changed.add(function + " " + before.get(function) + " → " + now.get(function));
                }
            }
            return "Schalter-Funktionen geändert: " + changed;
        }
        return null;
    }

    static RxSelfTests.Phase[] keyboardPhases() {
        return new RxSelfTests.Phase[]{
                RxSelfTests.phase("Funke abmelden, Tastatur-Ersatz an", 300, (ctx, run) -> {
                    if (run.waited(ctx) == 0) {
                        InputConfig config = InputConfig.get();
                        if (keyboardFallback == null) {
                            keyboardFallback = config.keyboardFallback;
                        }
                        config.keyboardFallback = true;
                        TransmitterInput.INSTANCE.simulate(null);
                    }
                    RadioState state = RadioState.INSTANCE;
                    if (state.source() != RadioSource.KEYBOARD || !state.connected() || run.waited(ctx) < 10) {
                        return new StepResult(StepResult.Status.PENDING, "Quelle " + state.source());
                    }
                    ctx.state().put("cov_rx_power", RxSelfTests.power(main()));
                    ctx.state().put("cov_rx_sent", RxClient.sentCount());
                    return StepResult.pass();
                }),
                RxSelfTests.phase("außerhalb des Flugs W und S halten", 300, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    int waited = run.waited(ctx);
                    mc.options.keyUp.setDown(waited < 40);
                    mc.options.keyDown.setDown(waited >= 40 && waited < 80);
                    int before = (int) ctx.state().get("cov_rx_power");
                    if (RxSelfTests.power(main()) != before) {
                        release(mc);
                        return StepResult.fail("Stärke änderte sich beim Laufen: " + before + " → " + RxSelfTests.power(main()));
                    }
                    if (waited < 90) {
                        return StepResult.pending();
                    }
                    release(mc);
                    int sent = RxClient.sentCount() - (int) ctx.state().get("cov_rx_sent");
                    if (sent != 0) {
                        return StepResult.fail("beim Laufen " + sent + " RX-Pakete gesendet");
                    }
                    run.notes.add("außerhalb des Flugs: W/S 4 s gehalten, Stärke " + before + " unverändert, keine RX-Pakete");
                    return StepResult.pass();
                }),
                RxSelfTests.phase("zurück zum Platz", 200, (ctx, run) -> RxSelfTests.server(ctx, run, server -> teleport(RxSelfTests.player(server),
                        server.overworld(), Vec3.atBottomCenterOf(site()), -90.0F, 20.0F))),
                RxSelfTests.phase("Drohne und FPV-Start mit Tastatur", 400, (ctx, run) -> RxSelfTests.server(ctx, run, server -> startFpv(server,
                        droneSpot()))),
                RxSelfTests.phase("FPV aktiv mit Tastatur", 400, (ctx, run) -> RxSelfTests.until(PilotSession.INSTANCE.isFpv() && run.waited(ctx) > 10
                        && RadioState.INSTANCE.source() == RadioSource.KEYBOARD, "keine FPV-Sitzung mit Tastatur")),
                RxSelfTests.phase("im FPV Gas per Tastatur (W)", 400, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.options.keyUp.setDown(true);
                    int power = RxSelfTests.power(main());
                    if (power >= 12 && RxSelfTests.link(main()) == RxLinkState.LINKED) {
                        mc.options.keyUp.setDown(false);
                        run.notes.add("im FPV Tastatur-Gas: Stärke " + power + " nach " + run.waited(ctx) + " Ticks");
                        return StepResult.pass();
                    }
                    return new StepResult(StepResult.Status.PENDING, RxSelfTests.describe(main()) + ", Gas " + RadioState.INSTANCE.sticks().throttle());
                }),
                RxSelfTests.phase("im FPV Gas zurück (S)", 400, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.options.keyDown.setDown(true);
                    int power = RxSelfTests.power(main());
                    if (power <= 2 && RxSelfTests.link(main()) == RxLinkState.LINKED) {
                        mc.options.keyDown.setDown(false);
                        run.notes.add("im FPV Tastatur-Gas zurück: Stärke " + power);
                        return StepResult.pass();
                    }
                    return new StepResult(StepResult.Status.PENDING, RxSelfTests.describe(main()) + ", Gas " + RadioState.INSTANCE.sticks().throttle());
                }),
                RxSelfTests.phase("FPV beenden, Funke zurück", 400, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    release(mc);
                    if (run.waited(ctx) == 2) {
                        ctx.onServer(server -> DronePiloting.pilotedBy(RxSelfTests.player(server).getUUID()).ifPresent(DronePiloting::release));
                    }
                    if (PilotSession.INSTANCE.isActive() || run.waited(ctx) < 6) {
                        return new StepResult(StepResult.Status.PENDING, "Sitzung läuft");
                    }
                    if (keyboardFallback != null) {
                        InputConfig.get().keyboardFallback = keyboardFallback;
                        keyboardFallback = null;
                    }
                    radio();
                    ctx.onServer(CovRxSelfTests::discardDrone);
                    return StepResult.pass();
                })
        };
    }

    static BlockPos padBlock() {
        return site().offset(-2, 0, 2);
    }

    static SimulatedRadio pad() {
        SimulatedRadio current = pad;
        if (current == null) {
            current = new SimulatedRadio("propwash-cov-pad", "Propwash-Testpad (Abdeckung RX)", ProfileDefaults.gamepad("Selbsttest-Pad"), 6, 15);
            pad = current;
        }
        TransmitterInput.INSTANCE.simulate(current);
        return current;
    }

    static RxSelfTests.Phase[] gamepadPhases() {
        return new RxSelfTests.Phase[]{
                RxSelfTests.phase("gebundener Block ohne Kanal", 200, (ctx, run) -> RxSelfTests.server(ctx, run, server -> {
                    ServerPlayer player = RxSelfTests.player(server);
                    place(server.overworld(), padBlock(), new RxSettings(player.getUUID(), null, -1.0F, 1.0F, 64));
                })),
                RxSelfTests.phase("Gamepad statt Funke", 200, (ctx, run) -> {
                    SimulatedRadio gamepad = pad();
                    for (int axis = 0; axis < 6; axis++) {
                        gamepad.setAxis(axis, 0.0F);
                    }
                    RxBlockEntity entity = entity(padBlock());
                    boolean ready = entity != null && entity.settings().bound() && RadioState.INSTANCE.connected()
                            && RadioState.INSTANCE.deviceGuid().equals("propwash-cov-pad");
                    return RxSelfTests.until(ready && run.waited(ctx) > 10, "Gamepad oder Block fehlt: " + RadioState.INSTANCE.deviceGuid());
                }),
                RxSelfTests.phase("Gas mit dem linken Stick anlernen", 400, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    SimulatedRadio gamepad = pad();
                    int waited = run.waited(ctx);
                    if (waited == 0) {
                        mc.gui.setScreen(new RxScreen(padBlock(), false));
                        return StepResult.pending();
                    }
                    if (!(mc.gui.screen() instanceof RxScreen screen)) {
                        return StepResult.fail("Fenster zu");
                    }
                    if (waited == 4) {
                        screen.startLearning();
                    }
                    int axis = ProfileDefaults.padAxis(StickMode.DEFAULT, StickChannel.THROTTLE);
                    if (waited >= 6) {
                        gamepad.setAxis(axis, waited < 9 ? -0.3F : waited < 12 ? -0.7F : -1.0F);
                    }
                    RxBlockEntity entity = entity(padBlock());
                    if (entity != null && entity.settings().source() != null && !screen.learning()) {
                        mc.gui.setScreen(null);
                        ctx.state().put("cov_rx_pad_axis", axis);
                        run.notes.add("Gamepad angelernt: " + RxText.source(entity.settings().source()).getString() + " (Achse " + axis + ")");
                        return RxSource.GAS.equals(entity.settings().source()) ? StepResult.pass()
                                : StepResult.fail("Gamepad lernt " + entity.settings().source() + " statt Gas");
                    }
                    return new StepResult(StepResult.Status.PENDING, "Quelle " + (entity == null ? "-" : entity.settings().source()));
                }),
                RxSelfTests.phase("Stick unten – Stärke 0", 300, (ctx, run) -> {
                    pad().setAxis((int) ctx.state().get("cov_rx_pad_axis"), 0.0F);
                    return settle(ctx, run, padBlock(), 0, RxLinkState.LINKED);
                }),
                RxSelfTests.phase("Stick oben – Stärke 15, außerhalb des Flugs", 300, (ctx, run) -> {
                    pad().setAxis((int) ctx.state().get("cov_rx_pad_axis"), -1.0F);
                    if (PilotSession.INSTANCE.isActive()) {
                        return StepResult.fail("Flug-Sitzung aktiv");
                    }
                    StepResult result = settle(ctx, run, padBlock(), 15, RxLinkState.LINKED);
                    if (result.status() == StepResult.Status.PASS) {
                        run.notes.add("Gamepad außerhalb des Flugs: Stick unten 0, oben 15");
                    }
                    return result;
                }),
                RxSelfTests.phase("Funke zurück, aufräumen", 200, (ctx, run) -> {
                    if (run.waited(ctx) == 0) {
                        radio();
                    }
                    return RxSelfTests.server(ctx, run, server -> clear(server.overworld(), padBlock()));
                })
        };
    }

    static RxSelfTests.Phase[] rangePhases() {
        return new RxSelfTests.Phase[]{
                RxSelfTests.phase("Reichweite 16", 200, (ctx, run) -> {
                    if (run.waited(ctx) == 0) {
                        ClientNet.sendToServer(RxConfigC2S.range(main(), 16));
                    }
                    RxBlockEntity entity = entity(main());
                    return RxSelfTests.until(entity != null && entity.settings().range() == 16, "Reichweite " + (entity == null ? "-" : entity.settings().range()));
                }),
                fpvStart("Drohne und FPV-Start"),
                fpvActive(),
                RxSelfTests.phase("Drohne per Sim-Teleport 100 Blöcke weg (20 m/s)", 400, (ctx, run) -> {
                    DroneSim sim = PilotSession.INSTANCE.sim();
                    QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                    if (sim == null || drone == null) {
                        return StepResult.fail("keine Sim");
                    }
                    Vec3 from = Vec3.atBottomCenterOf(main()).add(0.0, 4.0, 0.0);
                    int step = Math.min(run.waited(ctx), 100);
                    Vec3 at = from.add(step, 0.0, 0.0);
                    ctx.state().put("cov_rx_far", from.add(100.0, 0.0, 0.0));
                    TrainingSelfTests.teleport(at.x, at.y, at.z, -90.0, step < 100 ? 20.0 : 0.0, 0.0, 0.0);
                    double distance = drone.position().distanceTo(Vec3.atBottomCenterOf(main()));
                    if (distance > 90.0 && step >= 100 && run.waited(ctx) > 110) {
                        run.notes.add(String.format(Locale.ROOT, "Drohne %.0f Blöcke vom Block", distance));
                        return StepResult.pass();
                    }
                    return new StepResult(StepResult.Status.PENDING, String.format(Locale.ROOT, "Drohne %.1f Blöcke vom Block", distance));
                }),
                RxSelfTests.phase("Gas 100 % – Block folgt trotz ferner Drohne", 400, (ctx, run) -> {
                    holdFar(ctx);
                    micros(THROTTLE, 2000);
                    if (!PilotSession.INSTANCE.isFpv()) {
                        return StepResult.fail("FPV-Sitzung endete");
                    }
                    QuadcopterEntity drone = PilotSession.INSTANCE.drone();
                    double distance = drone == null ? 0.0 : drone.position().distanceTo(Vec3.atBottomCenterOf(main()));
                    if (distance < 60.0) {
                        return StepResult.fail(String.format(Locale.ROOT, "Drohne nur noch %.1f Blöcke weg", distance));
                    }
                    StepResult result = settle(ctx, run, main(), 15, RxLinkState.LINKED);
                    if (result.status() == StepResult.Status.PASS) {
                        run.notes.add(String.format(Locale.ROOT, "Drohne %.0f Blöcke weg, Körper am Block: LINKED, Stärke 15", distance));
                    }
                    return result;
                }),
                RxSelfTests.phase("Gas 0 % – Block folgt", 300, (ctx, run) -> {
                    holdFar(ctx);
                    micros(THROTTLE, 1000);
                    return settle(ctx, run, main(), 0, RxLinkState.LINKED);
                }),
                RxSelfTests.phase("Körper 30 Blöcke weg", 200, (ctx, run) -> RxSelfTests.server(ctx, run, server -> teleport(RxSelfTests.player(server),
                        server.overworld(), Vec3.atBottomCenterOf(site().west(30)), -90.0F, 0.0F))),
                RxSelfTests.phase("Körper 30 Blöcke weg – LOST", 300, (ctx, run) -> {
                    micros(THROTTLE, 1000);
                    if (RxSelfTests.link(main()) == RxLinkState.LOST && RxSelfTests.power(main()) == 0) {
                        return StepResult.pass();
                    }
                    return new StepResult(StepResult.Status.PENDING, RxSelfTests.describe(main()));
                }),
                RxSelfTests.phase("Körper 30 Blöcke weg – Gas 100 % ändert nichts", 300, (ctx, run) -> {
                    micros(THROTTLE, 2000);
                    if (RxSelfTests.link(main()) != RxLinkState.LOST || RxSelfTests.power(main()) != 0) {
                        return StepResult.fail("hält nicht: " + RxSelfTests.describe(main()));
                    }
                    if (run.waited(ctx) < 60) {
                        return StepResult.pending();
                    }
                    run.notes.add("Körper 30 Blöcke weg (Reichweite 16): LOST, hält 0 bei Gas 100 %, FPV " + (PilotSession.INSTANCE.isFpv() ? "läuft"
                            : "beendet"));
                    return StepResult.pass();
                }),
                fpvEnd(),
                RxSelfTests.phase("Reichweite 64", 300, (ctx, run) -> {
                    if (run.waited(ctx) == 0) {
                        ctx.onServer(server -> teleport(RxSelfTests.player(server), server.overworld(), Vec3.atBottomCenterOf(site()), -90.0F, 20.0F));
                    }
                    if (run.waited(ctx) == 20) {
                        ClientNet.sendToServer(RxConfigC2S.range(main(), 64));
                    }
                    RxBlockEntity entity = entity(main());
                    return RxSelfTests.until(entity != null && entity.settings().range() == 64, "Reichweite " + (entity == null ? "-" : entity.settings().range()));
                })
        };
    }

    static void holdFar(SelfTest.StepContext ctx) {
        Object far = ctx.state().get("cov_rx_far");
        if (far instanceof Vec3 at && PilotSession.INSTANCE.isActive()) {
            TrainingSelfTests.teleport(at.x, at.y, at.z, -90.0, 0.0, 0.0, 0.0);
        }
    }

    static RxSelfTests.Phase[] radioLossPhases() {
        return new RxSelfTests.Phase[]{
                RxSelfTests.phase("Gas 50 %", 300, (ctx, run) -> {
                    micros(THROTTLE, 1500);
                    int power = RxSelfTests.power(main());
                    if ((power == 7 || power == 8) && RxSelfTests.link(main()) == RxLinkState.LINKED) {
                        return RxSelfTests.stable(ctx, run, power, "Stärke");
                    }
                    return new StepResult(StepResult.Status.PENDING, RxSelfTests.describe(main()));
                }),
                RxSelfTests.phase("Fenster öffnen und Funke abmelden", 100, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (run.waited(ctx) == 0) {
                        mc.gui.setScreen(new RxScreen(main(), false));
                        return StepResult.pending();
                    }
                    if (!(mc.gui.screen() instanceof RxScreen) || run.waited(ctx) < 5) {
                        return StepResult.pending();
                    }
                    ctx.state().put("cov_rx_power", RxSelfTests.power(main()));
                    ctx.state().put("cov_rx_lost_from", ctx.tick());
                    TransmitterInput.INSTANCE.simulate(null);
                    return StepResult.pass();
                }),
                RxSelfTests.phase("nach etwa 100 Ticks LOST, Stärke bleibt, Fenster meldet keine Funke", 400, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    int before = (int) ctx.state().get("cov_rx_power");
                    int waited = ctx.tick() - (int) ctx.state().get("cov_rx_lost_from");
                    if (RxSelfTests.power(main()) != before) {
                        return StepResult.fail("Stärke änderte sich ohne Funke: " + before + " → " + RxSelfTests.power(main()));
                    }
                    if (RxSelfTests.link(main()) != RxLinkState.LOST) {
                        return new StepResult(StepResult.Status.PENDING, RxSelfTests.describe(main()) + " nach " + waited + " Ticks");
                    }
                    if (waited < 60 || waited > 240) {
                        return StepResult.fail("LOST nach " + waited + " Ticks statt etwa 100");
                    }
                    if (!(mc.gui.screen() instanceof RxScreen screen)) {
                        return StepResult.fail("Fenster zu");
                    }
                    List<LayoutAudit.Text> texts = LayoutProbe.capture(mc, screen).texts();
                    StringBuilder joined = new StringBuilder();
                    for (LayoutAudit.Text text : texts) {
                        joined.append(text.value()).append(' ');
                    }
                    String wanted = normalize(Component.translatable(RxText.NO_RADIO).getString());
                    if (!normalize(joined.toString()).contains(wanted)) {
                        return StepResult.fail("Fenster ohne „" + wanted + "“: " + normalize(joined.toString()));
                    }
                    run.notes.add("ohne Funke nach " + waited + " Ticks LOST, Stärke " + before + " gehalten, Fenster: „" + wanted + "“");
                    return StepResult.pass();
                }),
                RxSelfTests.phase("Funke wieder angemeldet – Block folgt", 400, (ctx, run) -> {
                    radio();
                    micros(THROTTLE, 2000);
                    StepResult result = settle(ctx, run, main(), 15, RxLinkState.LINKED);
                    if (result.status() == StepResult.Status.PASS) {
                        Minecraft.getInstance().gui.setScreen(null);
                        run.notes.add("wieder angemeldet: LINKED, folgt mit 15");
                    }
                    return result;
                })
        };
    }

    static Vec3 netherSpot() {
        return new Vec3(24.5, 80.0, 24.5);
    }

    static RxSelfTests.Phase[] dimensionPhases() {
        return new RxSelfTests.Phase[]{
                RxSelfTests.phase("Gas 100 %", 300, (ctx, run) -> {
                    micros(THROTTLE, 2000);
                    return settle(ctx, run, main(), 15, RxLinkState.LINKED);
                }),
                RxSelfTests.phase("in den Nether", 300, (ctx, run) -> RxSelfTests.server(ctx, run, server -> {
                    ServerPlayer player = RxSelfTests.player(server);
                    ServerLevel overworld = server.overworld();
                    overworld.setChunkForced(main().getX() >> 4, main().getZ() >> 4, true);
                    ServerLevel nether = server.getLevel(Level.NETHER);
                    if (nether == null) {
                        RxSelfTests.SERVER.set("kein Nether");
                        return;
                    }
                    BlockPos feet = BlockPos.containing(netherSpot());
                    nether.getChunk(feet.getX() >> 4, feet.getZ() >> 4);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            nether.setBlock(feet.offset(dx, -1, dz), Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_ALL);
                            for (int h = 0; h < 3; h++) {
                                nether.setBlock(feet.offset(dx, h, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                            }
                        }
                    }
                    teleport(player, nether, netherSpot(), 0.0F, 0.0F);
                })),
                RxSelfTests.phase("im Nether, Gas 0 %", 600, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level == null || mc.level.dimension() != Level.NETHER) {
                        run.last = Integer.MIN_VALUE;
                        return new StepResult(StepResult.Status.PENDING, "noch nicht im Nether");
                    }
                    micros(THROTTLE, 1000);
                    if (ctx.state().get("cov_rx_nether_at") == null) {
                        ctx.state().put("cov_rx_nether_at", ctx.tick());
                    }
                    return RxSelfTests.until(ctx.tick() - (int) ctx.state().get("cov_rx_nether_at") > 80, "warten");
                }),
                RxSelfTests.phase("Server: Block in der Oberwelt hält 15", 200, (ctx, run) -> RxSelfTests.check(ctx, run, server -> {
                    ServerLevel overworld = server.overworld();
                    if (!RxServer.active(overworld, main())) {
                        return "Chunk des Blocks tickt nicht";
                    }
                    BlockState state = overworld.getBlockState(main());
                    if (!(state.getBlock() instanceof RxBlock) || state.getValue(RxBlock.POWER) != 15) {
                        return "Block in der Oberwelt änderte sich: " + state;
                    }
                    run.notes.add("im Nether mit Gas 0 %: Block in der Oberwelt hält 15 (" + state.getValue(RxBlock.LINK) + ")");
                    return null;
                })),
                RxSelfTests.phase("zurück in die Oberwelt", 300, (ctx, run) -> RxSelfTests.server(ctx, run, server -> {
                    ServerPlayer player = RxSelfTests.player(server);
                    teleport(player, server.overworld(), Vec3.atBottomCenterOf(site()), -90.0F, 20.0F);
                })),
                RxSelfTests.phase("nach der Rückkehr LINKED und folgt dem Gas 0 %", 600, (ctx, run) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) {
                        return new StepResult(StepResult.Status.PENDING, "noch nicht zurück");
                    }
                    micros(THROTTLE, 1000);
                    return settle(ctx, run, main(), 0, RxLinkState.LINKED);
                }),
                RxSelfTests.phase("folgt wieder mit Gas 100 %", 300, (ctx, run) -> {
                    micros(THROTTLE, 2000);
                    StepResult result = settle(ctx, run, main(), 15, RxLinkState.LINKED);
                    if (result.status() == StepResult.Status.PASS) {
                        run.notes.add("nach der Rückkehr LINKED, folgt 0 und 15");
                        ctx.onServer(server -> server.overworld().setChunkForced(main().getX() >> 4, main().getZ() >> 4, false));
                    }
                    return result;
                })
        };
    }

    record Snapshot(@Nullable UUID owner, @Nullable RxSource source, float low, float high, int range, int power) {
        static @Nullable Snapshot of(BlockPos pos) {
            RxBlockEntity entity = entity(pos);
            if (entity == null) {
                return null;
            }
            RxSettings s = entity.settings();
            return new Snapshot(s.owner(), s.source(), s.low(), s.high(), s.range(), RxSelfTests.power(pos));
        }
    }

    static RxSelfTests.Phase same(String name, int wait) {
        return RxSelfTests.phase(name, wait + 200, (ctx, run) -> {
            micros(THROTTLE, 1500);
            Snapshot before = (Snapshot) ctx.state().get("cov_rx_snapshot");
            Snapshot now = Snapshot.of(main());
            if (!Objects.equals(before, now)) {
                return StepResult.fail(name + ": " + before + " → " + now);
            }
            if (run.waited(ctx) < wait) {
                return StepResult.pending();
            }
            run.notes.add(name + ": unverändert");
            return StepResult.pass();
        });
    }

    static RxSelfTests.Phase[] commandPhases() {
        return new RxSelfTests.Phase[]{
                RxSelfTests.phase("Gas 50 % und Zustand merken", 300, (ctx, run) -> {
                    micros(THROTTLE, 1500);
                    int power = RxSelfTests.power(main());
                    if ((power == 7 || power == 8) && RxSelfTests.link(main()) == RxLinkState.LINKED) {
                        StepResult stable = RxSelfTests.stable(ctx, run, power, "Stärke");
                        if (stable.status() == StepResult.Status.PASS) {
                            ctx.state().put("cov_rx_snapshot", Snapshot.of(main()));
                            run.notes.add("Zustand " + Snapshot.of(main()));
                        }
                        return stable;
                    }
                    return new StepResult(StepResult.Status.PENDING, RxSelfTests.describe(main()));
                }),
                fpvStart("Drohne 1 und FPV-Start"),
                fpvActive(),
                same("FPV mit Drohne 1", 20),
                RxSelfTests.phase("Reset (Rücktaste)", 100, (ctx, run) -> {
                    KeyMapping reset = FunctionKeys.mapping(SwitchFunction.RESET);
                    if (reset == null || reset.isUnbound()) {
                        return StepResult.fail("keine Taste für Reset");
                    }
                    reset.setDown(run.waited(ctx) < 4);
                    if (run.waited(ctx) < 10) {
                        return StepResult.pending();
                    }
                    run.notes.add("Reset auf " + reset.saveString() + " gedrückt");
                    return StepResult.pass();
                }),
                same("nach Reset", 40),
                RxSelfTests.phase("/propwash clear", 200, (ctx, run) -> RxSelfTests.check(ctx, run, server -> {
                    AtomicInteger result = new AtomicInteger(Integer.MIN_VALUE);
                    server.getCommands().performPrefixedCommand(RxSelfTests.player(server).createCommandSourceStack()
                            .withCallback((success, value) -> result.set(success ? value : -1)), "propwash clear");
                    DRONE.set(-1);
                    return result.get() >= 1 ? null : "/propwash clear meldet " + result.get();
                })),
                RxSelfTests.phase("FPV nach dem Entfernen beendet", 300, (ctx, run) -> RxSelfTests.until(!PilotSession.INSTANCE.isActive()
                        && run.waited(ctx) > 10, "Sitzung läuft")),
                same("nach /propwash clear", 40),
                fpvStart("Drohnenwechsel: Drohne 2 und FPV-Start"),
                fpvActive(),
                same("nach dem Drohnenwechsel", 40),
                fpvEnd()
        };
    }

    static void tearDown(MinecraftServer server) {
        ServerPlayer player = RxSelfTests.player(server);
        discardDrone(server);
        ServerLevel level = server.overworld();
        BlockPos site = SITE.get();
        if (site != null) {
            for (int x = -8; x <= 12; x++) {
                for (int z = -8; z <= 8; z++) {
                    for (int y = 0; y <= 5; y++) {
                        BlockPos pos = site.offset(x, y, z);
                        if (!level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        }
                    }
                }
            }
            SITE.set(null);
        }
        Saved saved = SAVED.getAndSet(null);
        if (saved != null) {
            player.setItemSlot(EquipmentSlot.HEAD, saved.head());
            player.setItemInHand(InteractionHand.MAIN_HAND, saved.hand());
            player.setGameMode(saved.mode());
            teleport(player, level, saved.position(), saved.yaw(), saved.pitch());
        }
    }
}
