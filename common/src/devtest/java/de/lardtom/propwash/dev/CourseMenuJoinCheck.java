package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.race.CourseMode;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.RaceModule;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class CourseMenuJoinCheck {
    public static final String RESULT = "[course-menu] ";
    public static final String OWNER = "PwMenuOwner";
    public static final String NAME = "Menü Beitritt";
    public static final String STOLEN = "Geklaut";
    public static final String OPERATOR_NAME = "Vom Operator";
    public static final int LAPS = 3;
    public static final int SETTLE_TICKS = 60;
    public static final int OP_AFTER_TICKS = 20 * 30;
    public static final int CLEANUP_AFTER_TICKS = 20 * 90;
    private static final List<GateEntity> SPAWNED = new ArrayList<>();
    private static int joined = -1;
    private static int ranAt = -1;
    private static int renamedAt = -1;
    private static @Nullable NameAndId opped;
    private static boolean deopped;
    private static boolean cleaned;
    private static boolean initialized;
    private static @Nullable Integer course;
    private static @Nullable String guest;
    private static ItemStack held = ItemStack.EMPTY;

    private CourseMenuJoinCheck() {
    }

    static synchronized void init() {
        if (initialized || JoinTestEnv.holdSeconds() <= 0 || !JoinTestEnv.courseMenu()) {
            return;
        }
        initialized = true;
        ServerBus.STOPPED.register(server -> deop(server, "beim Stopp des Servers"));
    }

    private static void deop(MinecraftServer server, String when) {
        NameAndId id = opped;
        if (id == null || deopped) {
            return;
        }
        deopped = true;
        server.getPlayerList().deop(id);
        Constants.LOG.info("[Beitrittstest] course-menu: {} ist kein Operator mehr ({})", id.name(), when);
    }

    private static CourseRegistry.@Nullable Course built() {
        CourseRegistry registry = CourseRegistry.get();
        Integer id = course;
        return registry == null || id == null ? null : registry.course(id);
    }

    public static String state(CourseRegistry.@Nullable Course built) {
        if (built == null) {
            return "gone";
        }
        return "name=" + built.name + " laps=" + built.laps + " mode=" + built.mode().key() + " gates=" + built.gates.size();
    }

    static void tick(MinecraftServer server, int tick) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (ranAt >= 0) {
            ServerPlayer joinedPlayer = guest == null ? null : server.getPlayerList().getPlayerByName(guest);
            CourseRegistry.Course current = built();
            if (!cleaned && opped == null && tick - ranAt >= OP_AFTER_TICKS && joinedPlayer != null) {
                Constants.LOG.info("[Beitrittstest] course-menu: vor dem Operator {}", state(current));
                opped = joinedPlayer.nameAndId();
                server.getPlayerList().op(opped);
                Constants.LOG.info("[Beitrittstest] course-menu: {} ist Operator", guest);
            }
            if (renamedAt < 0 && opped != null && current != null && OPERATOR_NAME.equals(current.name)) {
                renamedAt = tick;
                Constants.LOG.info("[Beitrittstest] course-menu: Operator hat im Menü umbenannt: {}", state(current));
                deop(server, "nach dem Umbenennen");
            }
            boolean settledBack = renamedAt >= 0 && tick - renamedAt >= SETTLE_TICKS;
            if (!cleaned && (settledBack || joinedPlayer == null || tick - ranAt >= CLEANUP_AFTER_TICKS)) {
                cleaned = true;
                deop(server, joinedPlayer == null ? "nach dem Verlassen" : "beim Aufräumen");
                cleanup(joinedPlayer);
                Constants.LOG.info("[Beitrittstest] course-menu: aufgeräumt");
            }
            return;
        }
        if (players.isEmpty()) {
            joined = -1;
            return;
        }
        if (joined < 0) {
            joined = tick;
        }
        if (tick - joined < SETTLE_TICKS) {
            return;
        }
        ranAt = tick;
        ServerPlayer player = players.getFirst();
        guest = player.getGameProfile().name();
        String line;
        try {
            line = run(server, player);
        } catch (RuntimeException e) {
            Constants.LOG.error("[Beitrittstest] course-menu: Ablauf fehlgeschlagen", e);
            line = "fail " + e;
        }
        Constants.LOG.info("[Beitrittstest] course-menu: {}", line);
        player.sendSystemMessage(Component.literal(RESULT + line).withStyle(ChatFormatting.GRAY));
    }

    private static String run(MinecraftServer server, ServerPlayer player) {
        if (CourseRegistry.isOperator(player)) {
            return "fail " + player.getGameProfile().name() + " ist schon Operator";
        }
        ServerPlayer owner = RaceDataJoinCheck.secondPilot(server, player.level(), OWNER);
        Vec3 origin = RaceDataJoinCheck.origin(player).add(0.0, 0.0, -60.0);
        CourseRegistry.Course created = RaceP2pJoinCheck.build(owner, origin, SPAWNED);
        CourseRegistry registry = CourseRegistry.get();
        if (created == null || registry == null) {
            return "fail Kurs nicht angelegt";
        }
        course = created.id;
        registry.rename(created, NAME);
        registry.setLaps(created, LAPS);
        if (created.mode() != CourseMode.LAPS || created.gates.size() != 3 || !created.ownedBy(owner.getUUID())) {
            return "fail Kurs falsch angelegt: " + state(created);
        }
        held = player.getMainHandItem().copy();
        ItemStack tool = new ItemStack(RaceModule.COURSE_TOOL.get());
        tool.set(RaceModule.COURSE.get(), created.id);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        return "ok course=" + created.id + " owner=" + OWNER + " " + state(created);
    }

    private static void cleanup(@Nullable ServerPlayer player) {
        if (player != null) {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
        }
        for (GateEntity gate : SPAWNED) {
            gate.discard();
        }
        SPAWNED.clear();
        CourseRegistry registry = CourseRegistry.get();
        Integer id = course;
        if (registry != null && id != null) {
            registry.deleteCourse(id);
        }
        course = null;
    }
}
