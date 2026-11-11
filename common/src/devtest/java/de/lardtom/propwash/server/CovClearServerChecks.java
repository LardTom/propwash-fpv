package de.lardtom.propwash.server;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.tag.DroneTagRules;
import de.lardtom.propwash.interact.DroneCargo;
import de.lardtom.propwash.race.CourseData;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CourseToolItem;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceModule;
import de.lardtom.propwash.race.RaceRecords;
import de.lardtom.propwash.rx.RxBlock;
import de.lardtom.propwash.rx.RxBlockEntity;
import de.lardtom.propwash.rx.RxLinkState;
import de.lardtom.propwash.rx.RxModule;
import de.lardtom.propwash.rx.RxSettings;
import de.lardtom.propwash.rx.RxSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class CovClearServerChecks {
    public static final String DIM_TAG = "propwash_cov_clear_dim";
    public static final String CARGO_TAG = "propwash_cov_clear_cargo";
    public static final String FAR_TAG = "propwash_cov_clear_far";
    public static final String OWNER_A = "CovOwnerA";
    public static final String OWNER_B = "CovOwnerB";
    public static final String OPERATOR = "CovClearOp";
    public static final String OTHER_OPERATOR = "CovClearOp2";
    public static final String GUEST = "CovClearGuest";
    private static final UUID OWNER_A_ID = UUID.fromString("00000000-0000-4000-8000-0000000c0a01");
    private static final UUID OWNER_B_ID = UUID.fromString("00000000-0000-4000-8000-0000000c0a02");
    private static final UUID OPERATOR_ID = UUID.fromString("00000000-0000-4000-8000-0000000c0a03");
    private static final UUID OTHER_OPERATOR_ID = UUID.fromString("00000000-0000-4000-8000-0000000c0a04");
    private static final UUID GUEST_ID = UUID.fromString("00000000-0000-4000-8000-0000000c0a05");
    private static final int BASE_CHUNKS = -4;
    private static final int FAR_CHUNKS = 24;
    private static final int LOAD_TICKS = 200;
    private static final int UNLOAD_TICKS = 600;
    private static final int RELOAD_TICKS = 300;
    private static final int CARGO_COUNT = 5;
    private static final int NOT_RUN = Integer.MIN_VALUE;

    private static final List<Entity> SPAWNED = new ArrayList<>();
    private static final List<String> NOTES = new ArrayList<>();
    private static int stage;
    private static int since;
    private static @Nullable String failure;
    private static String summary = "";
    private static @Nullable BlockPos base;
    private static @Nullable BlockPos far;
    private static @Nullable BlockPos netherPos;
    private static @Nullable UUID farDrone;
    private static @Nullable UUID netherDrone;
    private static @Nullable Snapshot before;
    private static @Nullable Capturing inventoryHolder;
    private static int course = GateEntity.NO_COURSE;
    private static @Nullable BlockPos rxPos;

    private CovClearServerChecks() {
    }

    public static @Nullable String failure() {
        return failure;
    }

    public static String summary() {
        return summary;
    }

    public static boolean tick(MinecraftServer server) {
        if (stage >= 4) {
            return true;
        }
        since++;
        try {
            switch (stage) {
                case 0 -> {
                    setup(server);
                    next(1);
                }
                case 1 -> {
                    if (!loaded(server)) {
                        return since > LOAD_TICKS && done(server, "Nether-Chunk oder ferner Chunk nach " + since + " Ticks nicht geladen");
                    }
                    BlockPos farPos = Objects.requireNonNull(far);
                    server.overworld().setChunkForced(farPos.getX() >> 4, farPos.getZ() >> 4, false);
                    next(2);
                }
                case 2 -> {
                    ServerLevel level = server.overworld();
                    BlockPos farPos = Objects.requireNonNull(far);
                    boolean gone = level.getEntity(Objects.requireNonNull(farDrone)) == null && !level.getChunkSource().hasChunk(farPos.getX() >> 4,
                            farPos.getZ() >> 4);
                    if (!gone) {
                        return since > UNLOAD_TICKS && done(server, "ferner Chunk entlädt nicht in " + since + " Ticks");
                    }
                    NOTES.add("ferner Chunk nach " + since + " Ticks entladen");
                    String problem = run(server);
                    if (problem != null) {
                        return done(server, problem);
                    }
                    level.setChunkForced(farPos.getX() >> 4, farPos.getZ() >> 4, true);
                    next(3);
                }
                case 3 -> {
                    ServerLevel level = server.overworld();
                    Entity drone = level.getEntity(Objects.requireNonNull(farDrone));
                    if (!(drone instanceof QuadcopterEntity quad) || quad.isRemoved()) {
                        return since > RELOAD_TICKS && done(server, "ferne Drohne nach dem Wiederladen nicht da (" + since + " Ticks)");
                    }
                    NOTES.add("ferne Drohne nach dem Wiederladen noch da (" + since + " Ticks)");
                    quad.discard();
                    summary = String.join(", ", NOTES);
                    return done(server, null);
                }
                default -> {
                    return true;
                }
            }
        } catch (RuntimeException e) {
            return done(server, "Ausnahme: " + e);
        }
        return false;
    }

    private static void next(int next) {
        stage = next;
        since = 0;
    }

    private static boolean loaded(MinecraftServer server) {
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) {
            throw new IllegalStateException("kein Nether");
        }
        BlockPos pos = Objects.requireNonNull(netherPos);
        if (netherDrone == null) {
            if (!nether.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                return false;
            }
            QuadcopterEntity drone = spawn(nether, Vec3.atBottomCenterOf(pos), DroneBuild.FREESTYLE, DIM_TAG);
            netherDrone = drone.getUUID();
        }
        return nether.getEntity(netherDrone) instanceof QuadcopterEntity drone && !drone.isRemoved()
                && server.overworld().getEntity(Objects.requireNonNull(farDrone)) != null;
    }

    private static void setup(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getRespawnData().pos();
        int cx = (spawn.getX() >> 4) + BASE_CHUNKS;
        int cz = (spawn.getZ() >> 4) + BASE_CHUNKS;
        level.setChunkForced(cx, cz, true);
        level.getChunk(cx, cz);
        int x0 = cx * 16;
        int z0 = cz * 16;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x0 + 8, z0 + 8) + 2;
        base = new BlockPos(x0, y, z0);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                level.setBlock(new BlockPos(x0 + x, y - 1, z0 + z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int h = 0; h < 4; h++) {
                    level.setBlock(new BlockPos(x0 + x, y + h, z0 + z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        int fx = (spawn.getX() >> 4) + FAR_CHUNKS;
        int fz = (spawn.getZ() >> 4) - FAR_CHUNKS;
        if (level.getChunkSource().hasChunk(fx, fz)) {
            throw new IllegalStateException("ferner Chunk " + fx + "," + fz + " ist schon geladen");
        }
        level.setChunkForced(fx, fz, true);
        level.getChunk(fx, fz);
        int farY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, fx * 16 + 8, fz * 16 + 8);
        far = new BlockPos(fx * 16 + 8, farY, fz * 16 + 8);
        farDrone = spawn(level, Vec3.atBottomCenterOf(far), DroneBuild.FREESTYLE, FAR_TAG).getUUID();
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) {
            throw new IllegalStateException("kein Nether");
        }
        netherPos = new BlockPos(8, 70, 8);
        nether.setChunkForced(0, 0, true);
        nether.getChunk(0, 0);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                nether.setBlock(netherPos.offset(dx, -1, dz), Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int h = 0; h < 3; h++) {
                    nether.setBlock(netherPos.offset(dx, h, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static @Nullable String run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos at = Objects.requireNonNull(base);
        Capturing guest = player(server, GUEST_ID, GUEST);
        Capturing operator = player(server, OPERATOR_ID, OPERATOR);
        Capturing other = player(server, OTHER_OPERATOR_ID, OTHER_OPERATOR);
        Capturing ownerA = player(server, OWNER_A_ID, OWNER_A);
        Capturing ownerB = player(server, OWNER_B_ID, OWNER_B);
        for (Capturing p : List.of(guest, operator, other, ownerA, ownerB)) {
            p.snapTo(at.getX() + 8.5, at.getY(), at.getZ() + 8.5);
        }
        String problem = suggestions(server, guest, operator);
        if (problem != null) {
            return problem;
        }
        List<QuadcopterEntity> ofA = new ArrayList<>();
        List<QuadcopterEntity> ofB = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            QuadcopterEntity placed = place(level, ownerA, at.offset(1 + 3 * i, -1, 1));
            if (placed == null) {
                return "Drohne von " + OWNER_A + " nicht platziert";
            }
            ofA.add(placed);
        }
        QuadcopterEntity placedB = place(level, ownerB, at.offset(1, -1, 4));
        if (placedB == null) {
            return "Drohne von " + OWNER_B + " nicht platziert";
        }
        ofB.add(placedB);
        String tagA = DroneTagRules.ownerTag(OWNER_A);
        for (QuadcopterEntity drone : ofA) {
            if (!drone.entityTags().contains(tagA)) {
                return "Drohne von " + OWNER_A + " ohne Tag " + tagA + ": " + drone.entityTags();
            }
        }
        QuadcopterEntity overworldDim = spawn(level, Vec3.atBottomCenterOf(at.offset(12, 0, 1)), DroneBuild.FREESTYLE, DIM_TAG);
        QuadcopterEntity cargo = spawn(level, Vec3.atBottomCenterOf(at.offset(12, 0, 12)), DroneBuild.FREESTYLE.withAccessory(AccessoryType.GRIPPER),
                CARGO_TAG);
        DroneCargo.load(cargo, List.of(new ItemStack(Items.COBBLESTONE, CARGO_COUNT)));
        if (cargo.cargoHold().itemCount() != CARGO_COUNT) {
            return "Greifer nicht beladen: " + cargo.cargoHold().itemCount();
        }
        problem = buildUntouched(server, level, operator, at);
        if (problem != null) {
            return problem;
        }
        Snapshot start = snapshot(server, level);
        before = start;
        int chunksBefore = level.getChunkSource().getLoadedChunksCount();
        List<QuadcopterEntity> loadedBefore = DroneClear.loaded(server);
        UUID farId = Objects.requireNonNull(farDrone);
        if (loadedBefore.stream().anyMatch(drone -> drone.getUUID().equals(farId))) {
            return "ferne Drohne zählt als geladen";
        }
        server.getPlayerList().op(operator.nameAndId());
        server.getPlayerList().op(other.nameAndId());
        CovPlayerListAccess.online(server, other, true, false);
        int owners;
        int dims;
        int cargoResult;
        int farResult;
        List<String> log;
        try (CovLogCapture capture = CovLogCapture.propwash()) {
            owners = run(server, operator.createCommandSourceStack(), "propwash clear @e[tag=" + tagA + "]");
            dims = run(server, operator.createCommandSourceStack(), "propwash clear @e[tag=" + DIM_TAG + "]");
            cargoResult = run(server, operator.createCommandSourceStack(), "propwash clear @e[tag=" + CARGO_TAG + "]");
            farResult = run(server, operator.createCommandSourceStack(), "propwash clear @e[tag=" + FAR_TAG + "]");
            log = capture.matching("/propwash clear von " + OPERATOR + ": entfernt");
        } finally {
            CovPlayerListAccess.online(server, other, false, false);
            server.getPlayerList().deop(operator.nameAndId());
            server.getPlayerList().deop(other.nameAndId());
        }
        if (owners != 2) {
            return "Selektor pw_owner_ von " + OWNER_A + " meldet " + owners + " statt 2";
        }
        for (QuadcopterEntity drone : ofA) {
            if (!drone.isRemoved()) {
                return "Drohne von " + OWNER_A + " nicht entfernt";
            }
        }
        if (placedB.isRemoved() || level.getEntity(placedB.getUUID()) == null) {
            return "Drohne von " + OWNER_B + " mit entfernt";
        }
        NOTES.add("@e[tag=" + tagA + "] entfernt nur die 2 Drohnen von " + OWNER_A + ", die von " + OWNER_B + " bleibt");
        problem = dimensions(server, operator, dims, overworldDim);
        if (problem != null) {
            return problem;
        }
        problem = cargo(level, cargo, cargoResult);
        if (problem != null) {
            return problem;
        }
        int chunksAfter = level.getChunkSource().getLoadedChunksCount();
        BlockPos farPos = Objects.requireNonNull(far);
        if (farResult != 0 || level.getChunkSource().hasChunk(farPos.getX() >> 4, farPos.getZ() >> 4) || chunksAfter != chunksBefore) {
            return "entladener Bereich: Rückgabe " + farResult + ", Chunk geladen " + level.getChunkSource().hasChunk(farPos.getX() >> 4,
                    farPos.getZ() >> 4) + ", Chunks " + chunksBefore + " → " + chunksAfter;
        }
        NOTES.add("Drohne im entladenen Chunk nicht gezählt (Rückgabe 0), Chunks " + chunksBefore + " → " + chunksAfter);
        problem = broadcast(other, operator);
        if (problem != null) {
            return problem;
        }
        if (log.size() != 3 || !log.stream().allMatch(line -> line.startsWith("/propwash clear von " + OPERATOR + ": entfernt"))) {
            return "Log-Zeilen „/propwash clear von " + OPERATOR + ": entfernt …“ fehlen: " + log;
        }
        NOTES.add("Log: " + log.getFirst());
        String changed = start.differs(snapshot(server, level));
        if (changed != null) {
            return "nach /propwash clear verändert: " + changed;
        }
        NOTES.add("Gates, Kurs " + course + ", Bestenliste, RX-Block und Drohnen-Items im Inventar unverändert");
        return null;
    }

    private static @Nullable String suggestions(MinecraftServer server, Capturing guest, Capturing operator) {
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        List<String> raw = suggest(dispatcher, guest.createCommandSourceStack());
        List<String> guestTexts = usable(dispatcher, guest.createCommandSourceStack());
        if (guestTexts.contains("clear")) {
            return "Gast ohne Recht bekommt „clear“ im Befehlsbaum: " + guestTexts;
        }
        int guestRun = run(server, guest.createCommandSourceStack(), "propwash clear");
        if (guestRun > 0) {
            return "Gast ohne Recht führt /propwash clear aus: " + guestRun;
        }
        server.getPlayerList().op(operator.nameAndId());
        List<String> operatorTexts;
        try {
            operatorTexts = usable(dispatcher, operator.createCommandSourceStack());
        } finally {
            server.getPlayerList().deop(operator.nameAndId());
        }
        if (!operatorTexts.contains("clear")) {
            return "Gegenprobe: Operator bekommt kein „clear“: " + operatorTexts;
        }
        NOTES.add("Vorschläge für „propwash “ aus dem Befehlsbaum des Gastes " + guestTexts + " ohne clear, als Operator mit (ungefilterte "
                + "getCompletionSuggestions am Server: " + raw + ")");
        return null;
    }

    private static List<String> suggest(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source) {
        ParseResults<CommandSourceStack> parse = dispatcher.parse("propwash ", source);
        Suggestions suggestions = dispatcher.getCompletionSuggestions(parse).join();
        List<String> out = new ArrayList<>();
        for (Suggestion suggestion : suggestions.getList()) {
            out.add(suggestion.getText());
        }
        return out;
    }

    private static List<String> usable(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source) {
        List<String> out = new ArrayList<>();
        CommandNode<CommandSourceStack> propwash = dispatcher.getRoot().getChild("propwash");
        if (propwash == null || !propwash.canUse(source)) {
            return out;
        }
        for (CommandNode<CommandSourceStack> child : propwash.getChildren()) {
            if (child.canUse(source)) {
                out.add(child.getName());
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    private static @Nullable String dimensions(MinecraftServer server, Capturing operator, int result, QuadcopterEntity overworldDim) {
        if (result != 2) {
            return "Zählung je Dimension: Rückgabe " + result + " statt 2";
        }
        ServerLevel nether = Objects.requireNonNull(server.getLevel(Level.NETHER));
        if (!overworldDim.isRemoved() || nether.getEntity(Objects.requireNonNull(netherDrone)) != null) {
            return "Zählung je Dimension: Drohnen nicht entfernt";
        }
        Component done = null;
        for (Component message : operator.messages) {
            if (message.getContents() instanceof TranslatableContents t && t.getKey().equals(DroneClear.DONE) && t.getArgs().length == 2
                    && String.valueOf(t.getArgs()[1]).contains("the_nether")) {
                done = message;
            }
        }
        if (done == null) {
            return "Zählung je Dimension: keine Meldung " + DroneClear.DONE + " mit the_nether: " + texts(operator.messages);
        }
        TranslatableContents contents = (TranslatableContents) done.getContents();
        String detail = String.valueOf(contents.getArgs()[1]);
        if (!"2".equals(String.valueOf(contents.getArgs()[0])) || !"overworld: 1, the_nether: 1".equals(detail)) {
            return "Zählung je Dimension: Meldung " + contents.getArgs()[0] + " / „" + detail + "“ statt 2 / „overworld: 1, the_nether: 1“";
        }
        NOTES.add("Nether-Drohne mit Forceload-Ticket mitgezählt: Removed 2 drones (" + detail + ")");
        nether.setChunkForced(0, 0, false);
        return null;
    }

    private static @Nullable String cargo(ServerLevel level, QuadcopterEntity cargo, int result) {
        if (result != 1 || !cargo.isRemoved()) {
            return "Fracht: Rückgabe " + result + ", Drohne entfernt " + cargo.isRemoved();
        }
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(cargo.blockPosition()).inflate(4.0));
        int cobble = 0;
        List<String> seen = new ArrayList<>();
        for (ItemEntity item : items) {
            ItemStack stack = item.getItem();
            seen.add(stack.getCount() + "× " + BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (droneOrPart(stack)) {
                return "Fracht: Drohnen- oder Teile-Item liegt herum: " + seen;
            }
            if (stack.is(Items.COBBLESTONE)) {
                cobble += stack.getCount();
            }
            SPAWNED.add(item);
        }
        if (cobble != CARGO_COUNT || items.size() != 1) {
            return "Fracht: statt genau " + CARGO_COUNT + " Bruchstein liegen " + seen;
        }
        NOTES.add("Fracht: genau " + seen + " fällt, kein Drohnen- oder Teile-Item");
        return null;
    }

    private static @Nullable String broadcast(Capturing other, Capturing operator) {
        List<Component> admin = new ArrayList<>();
        for (Component message : other.messages) {
            if (message.getContents() instanceof TranslatableContents t && t.getKey().equals("chat.type.admin")) {
                admin.add(message);
            }
        }
        if (admin.size() != 3) {
            return "zweiter Operator bekommt " + admin.size() + " statt 3 graue Admin-Zeilen: " + texts(other.messages);
        }
        Component first = admin.getFirst();
        TranslatableContents contents = (TranslatableContents) first.getContents();
        Object inner = contents.getArgs().length > 1 ? contents.getArgs()[1] : null;
        boolean gray = Objects.equals(first.getStyle().getColor(), net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.GRAY))
                && first.getStyle().isItalic();
        if (!gray || !(inner instanceof Component message) || !(message.getContents() instanceof TranslatableContents t)
                || !t.getKey().startsWith("propwash.server.command.clear.done")) {
            return "Admin-Zeile nicht grau/kursiv oder ohne Meldung: " + first + " (Stil " + first.getStyle() + ")";
        }
        if (operator.messages.stream().anyMatch(m -> m.getContents() instanceof TranslatableContents c && c.getKey().equals("chat.type.admin"))) {
            return "ausführender Operator bekommt die Admin-Zeile selbst";
        }
        NOTES.add("zweiter Operator bekommt 3 graue Admin-Zeilen (chat.type.admin, kursiv)");
        return null;
    }

    private static String texts(List<Component> messages) {
        List<String> out = new ArrayList<>();
        for (Component message : messages) {
            out.add(message.getContents() instanceof TranslatableContents t ? t.getKey() + List.of(t.getArgs()) : message.getString());
        }
        return out.toString();
    }

    private static boolean droneOrPart(ItemStack stack) {
        return stack.is(DroneModule.DRONE_ITEM.get()) || stack.has(CoreComponents.PART.get())
                || PartIndex.registeredById(BuiltInRegistries.ITEM.getKey(stack.getItem())).isPresent();
    }

    private static @Nullable String buildUntouched(MinecraftServer server, ServerLevel level, Capturing operator, BlockPos at) {
        CourseRegistry registry = CourseRegistry.get();
        if (registry == null) {
            return "CourseRegistry läuft nicht";
        }
        operator.setGameMode(GameType.CREATIVE);
        server.getPlayerList().op(operator.nameAndId());
        List<GateEntity> gates = new ArrayList<>();
        try {
            ItemStack stack = new ItemStack(RaceModule.GATE_MEDIUM.get(), 2);
            operator.setItemInHand(InteractionHand.MAIN_HAND, stack);
            operator.setYRot(0.0F);
            for (int i = 0; i < 2; i++) {
                BlockPos ground = at.offset(5 + 5 * i, -1, 8);
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false);
                InteractionResult result = stack.getItem().useOn(new UseOnContext(operator, InteractionHand.MAIN_HAND, hit));
                if (!result.consumesAction()) {
                    return "Gate " + i + " nicht gesetzt: " + result;
                }
                List<GateEntity> found = level.getEntitiesOfClass(GateEntity.class, new AABB(ground.above()).inflate(0.6));
                if (found.size() != 1) {
                    return "Gate " + i + " nicht gefunden: " + found.size();
                }
                gates.add(found.getFirst());
                SPAWNED.add(found.getFirst());
            }
            ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
            for (GateEntity gate : gates) {
                CourseToolItem.useOnGate(tool, operator, gate);
            }
            course = CourseToolItem.courseOf(tool);
        } finally {
            operator.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            server.getPlayerList().deop(operator.nameAndId());
        }
        CourseRegistry.Course data = registry.course(course);
        if (data == null || data.gates.size() != 2) {
            return "Kurs " + course + " hat nicht 2 Gates";
        }
        RaceRecords.get(server).board(course).submit(OWNER_B_ID, OWNER_B, DroneBuild.RACER.frame().key(), 12_345_000_000L, 1_700_000_000_000L);
        RaceRecords.get(server).changed();
        BlockPos rx = at.offset(14, 0, 4);
        level.setBlock(rx, RxModule.RX_BLOCK.block().get().defaultBlockState().setValue(RxBlock.POWER, 9).setValue(RxBlock.LINK, RxLinkState.LINKED),
                Block.UPDATE_ALL);
        if (!(level.getBlockEntity(rx) instanceof RxBlockEntity entity)) {
            return "RX-Block ohne Block-Entity";
        }
        entity.apply(new RxSettings(OWNER_B_ID, RxSource.axis(9), -0.5F, 0.75F, 40));
        rxPos = rx;
        Capturing holder = player(server, UUID.fromString("00000000-0000-4000-8000-0000000c0a06"), "CovClearHolder");
        holder.getInventory().setItem(0, new ItemStack(DroneModule.DRONE_ITEM.get()));
        ItemStack built = new ItemStack(DroneModule.DRONE_ITEM.get());
        built.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.WHOOP);
        holder.getInventory().setItem(1, built);
        holder.getInventory().setItem(2, new ItemStack(RxModule.RX_BLOCK.item().get(), 3));
        holder.snapTo(at.getX() + 3.5, at.getY(), at.getZ() + 12.5);
        inventoryHolder = holder;
        return null;
    }

    private record Snapshot(Set<UUID> gates, @Nullable CourseData course, List<Leaderboard.Entry> board, @Nullable BlockState rxState,
                            @Nullable RxSettings rxSettings, List<String> inventory) {
        @Nullable String differs(Snapshot after) {
            if (!this.gates.equals(after.gates)) {
                return "Gates " + this.gates.size() + " → " + after.gates.size();
            }
            if (!Objects.equals(this.course, after.course)) {
                return "Kurs " + this.course + " → " + after.course;
            }
            if (!this.board.equals(after.board)) {
                return "Bestenliste " + this.board + " → " + after.board;
            }
            if (!Objects.equals(this.rxState, after.rxState) || !Objects.equals(this.rxSettings, after.rxSettings)) {
                return "RX-Block " + this.rxState + " " + this.rxSettings + " → " + after.rxState + " " + after.rxSettings;
            }
            if (!this.inventory.equals(after.inventory)) {
                return "Inventar " + this.inventory + " → " + after.inventory;
            }
            return null;
        }
    }

    private static Snapshot snapshot(MinecraftServer server, ServerLevel level) {
        Set<UUID> gates = new HashSet<>();
        for (Entity entity : SPAWNED) {
            if (entity instanceof GateEntity gate && !gate.isRemoved() && level.getEntity(gate.getUUID()) != null) {
                gates.add(gate.getUUID());
            }
        }
        CourseRegistry registry = CourseRegistry.get();
        CourseRegistry.Course data = registry == null ? null : registry.course(course);
        List<Leaderboard.Entry> board = RaceRecords.get(server).board(course).entries();
        BlockState state = rxPos == null ? null : level.getBlockState(rxPos);
        RxSettings settings = rxPos != null && level.getBlockEntity(rxPos) instanceof RxBlockEntity entity ? entity.settings() : null;
        List<String> inventory = new ArrayList<>();
        Capturing holder = inventoryHolder;
        if (holder != null) {
            for (int slot = 0; slot < holder.getInventory().getContainerSize(); slot++) {
                ItemStack stack = holder.getInventory().getItem(slot);
                if (!stack.isEmpty()) {
                    inventory.add(slot + ":" + stack.getCount() + "×" + BuiltInRegistries.ITEM.getKey(stack.getItem()) + stack.getComponentsPatch());
                }
            }
        }
        return new Snapshot(gates, data == null ? null : data.toData(), board, state, settings, inventory);
    }

    private static @Nullable QuadcopterEntity place(ServerLevel level, ServerPlayer player, BlockPos click) {
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), DroneBuild.FREESTYLE);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.snapTo(click.getX() + 0.5, click.getY() + 1.0, click.getZ() + 2.5);
        Vec3 hit = new Vec3(click.getX() + 0.5, click.getY() + 1.0, click.getZ() + 0.5);
        InteractionResult result = player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, click, false)));
        if (!result.consumesAction()) {
            return null;
        }
        List<QuadcopterEntity> found = level.getEntitiesOfClass(QuadcopterEntity.class, new AABB(click.above()).inflate(1.0),
                drone -> !drone.isRemoved());
        if (found.size() != 1) {
            return null;
        }
        SPAWNED.add(found.getFirst());
        return found.getFirst();
    }

    private static QuadcopterEntity spawn(ServerLevel level, Vec3 at, DroneBuild build, String tag) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("Drohne nicht erzeugt");
        }
        ItemStack stack = new ItemStack(DroneModule.DRONE_ITEM.get());
        stack.set(CoreComponents.DRONE_BUILD.get(), build);
        stack.set(CoreComponents.BATTERY_CHARGE.get(), 1.0F);
        drone.loadFromStack(stack);
        drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        drone.addTag(tag);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("Drohne nicht gespawnt");
        }
        SPAWNED.add(drone);
        return drone;
    }

    private static int run(MinecraftServer server, CommandSourceStack source, String command) {
        AtomicInteger result = new AtomicInteger(NOT_RUN);
        server.getCommands().performPrefixedCommand(source.withCallback((success, value) -> result.set(success ? value : 0)), command);
        return result.get();
    }

    private static Capturing player(MinecraftServer server, UUID id, String name) {
        GameProfile profile = new GameProfile(id, name);
        Capturing player = new Capturing(server, profile);
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        return player;
    }

    private static boolean done(MinecraftServer server, @Nullable String problem) {
        failure = problem;
        stage = 4;
        for (Entity entity : SPAWNED) {
            if (!entity.isRemoved()) {
                if (entity instanceof QuadcopterEntity drone && drone.pilot().isPresent()) {
                    DronePiloting.release(drone);
                }
                entity.discard();
            }
        }
        SPAWNED.clear();
        ServerLevel level = server.overworld();
        CourseRegistry registry = CourseRegistry.get();
        if (registry != null && course != GateEntity.NO_COURSE) {
            registry.deleteCourse(course);
            RaceRecords.get(server).remove(course);
        }
        if (rxPos != null && level.getBlockState(rxPos).getBlock() instanceof RxBlock) {
            level.setBlock(rxPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (far != null) {
            level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
        }
        if (base != null) {
            level.setChunkForced(base.getX() >> 4, base.getZ() >> 4, false);
        }
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether != null) {
            if (netherDrone != null && nether.getEntity(netherDrone) instanceof QuadcopterEntity drone && !drone.isRemoved()) {
                drone.discard();
            }
            nether.setChunkForced(0, 0, false);
        }
        return true;
    }

    static final class Capturing extends ServerPlayer {
        final List<Component> messages = new CopyOnWriteArrayList<>();

        Capturing(MinecraftServer server, GameProfile profile) {
            super(server, server.overworld(), profile, ClientInformation.createDefault());
        }

        @Override
        public void sendSystemMessage(Component message, boolean overlay) {
            this.messages.add(message);
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "Capturing[%s]", this.getPlainTextName());
        }
    }
}
