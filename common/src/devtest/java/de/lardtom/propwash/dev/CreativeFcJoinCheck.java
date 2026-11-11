package de.lardtom.propwash.dev;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.core.event.ServerBus;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.CreativeFcRule;
import de.lardtom.propwash.race.GateEntity;
import de.lardtom.propwash.race.LapAssist;
import de.lardtom.propwash.race.Leaderboard;
import de.lardtom.propwash.race.RaceStandard;
import de.lardtom.propwash.race.RaceStandards;
import de.lardtom.propwash.server.WorldRules;
import de.lardtom.propwash.server.rules.WorldRuleSpecs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CreativeFcJoinCheck {
    public static final String RESULT = "[creative-fc] ";
    public static final String READY = "ready course=";
    public static final String OPPED = "opped";
    public static final String DONE = "result ";
    public static final int SETTLE_TICKS = 60;
    public static final int DENY_TICKS = 20 * 10;
    public static final int GRANT_TICKS = 20 * 45;
    public static final int CLEANUP_AFTER_TICKS = 20 * 30;
    private static final long S = 1_000_000_000L;
    private static final String LEGAL = "clean+fc";
    private static final List<GateEntity> SPAWNED = new ArrayList<>();
    private static final List<String> OUT = new ArrayList<>();
    private static int joined = -1;
    private static int readyAt = -1;
    private static int oppedAt = -1;
    private static int doneAt = -1;
    private static boolean cleaned;
    private static boolean deopped;
    private static boolean initialized;
    private static @Nullable NameAndId opped;
    private static @Nullable Integer course;
    private static @Nullable Vec3 origin;
    private static @Nullable String guest;

    private CreativeFcJoinCheck() {
    }

    public static DroneBuild creativeBuild() {
        DroneBuild base = DroneBuild.FREESTYLE;
        return new DroneBuild(base.frame(), StackType.CREATIVE_FC, base.motor(), base.prop(), base.video(), base.battery());
    }

    static synchronized void init() {
        if (initialized || JoinTestEnv.holdSeconds() <= 0 || !JoinTestEnv.creativeFc()) {
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
        Constants.LOG.info("[Beitrittstest] creative-fc: {} ist kein Operator mehr ({})", id.name(), when);
    }

    private static CourseRegistry.@Nullable Course built() {
        CourseRegistry registry = CourseRegistry.get();
        Integer id = course;
        return registry == null || id == null ? null : registry.course(id);
    }

    private static void say(ServerPlayer player, String line) {
        Constants.LOG.info("[Beitrittstest] creative-fc: {}", line);
        player.sendSystemMessage(Component.literal(RESULT + line).withStyle(ChatFormatting.GRAY));
    }

    public static String rated(Leaderboard board, UUID player, long nanos) {
        for (Leaderboard.Entry entry : board.storedTimes()) {
            if (entry.player().equals(player) && entry.nanos() == nanos) {
                if (board.scoring().clean(entry)) {
                    return entry.creativeFc() ? LEGAL : "clean";
                }
                return "assisted" + LapAssist.list(entry.assist());
            }
        }
        return "missing";
    }

    static void tick(MinecraftServer server, int tick) {
        ServerPlayer player = guest == null ? null : server.getPlayerList().getPlayerByName(guest);
        if (doneAt >= 0) {
            if (!cleaned && (player == null || tick - doneAt >= CLEANUP_AFTER_TICKS)) {
                cleaned = true;
                deop(server, "beim Aufräumen");
                cleanup(server);
            }
            return;
        }
        if (readyAt < 0) {
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
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
            ServerPlayer first = players.getFirst();
            guest = first.getGameProfile().name();
            String problem = setup(server, first);
            if (problem != null) {
                finish(server, first, tick, "fail " + problem);
                return;
            }
            readyAt = tick;
            say(first, READY + course);
            return;
        }
        if (player == null) {
            doneAt = tick;
            return;
        }
        CourseRegistry.Course current = built();
        if (current == null) {
            finish(server, player, tick, "fail Kurs verschwunden");
            return;
        }
        if (oppedAt < 0) {
            if (tick - readyAt < DENY_TICKS) {
                return;
            }
            OUT.add("owner_denied=" + (current.creativeFc() == CreativeFcRule.WORLD ? "yes" : "no:" + current.creativeFc().key()));
            opped = player.nameAndId();
            server.getPlayerList().op(opped);
            oppedAt = tick;
            say(player, OPPED);
            return;
        }
        if (current.creativeFc() != CreativeFcRule.LEGAL && tick - oppedAt < GRANT_TICKS) {
            return;
        }
        OUT.add("admin_set=" + (current.creativeFc() == CreativeFcRule.LEGAL ? "yes" : "no:" + current.creativeFc().key()));
        deop(server, "nach dem Umstellen");
        String line;
        try {
            line = flights(server, player, current);
        } catch (RuntimeException e) {
            Constants.LOG.error("[Beitrittstest] creative-fc: Flüge fehlgeschlagen", e);
            line = "fail " + e;
        }
        finish(server, player, tick, line);
    }

    private static void finish(MinecraftServer server, ServerPlayer player, int tick, String line) {
        doneAt = tick;
        deop(server, "am Ende");
        say(player, DONE + line);
    }

    private static @Nullable String setup(MinecraftServer server, ServerPlayer player) {
        if (CourseRegistry.isOperator(player)) {
            return player.getGameProfile().name() + " ist schon Operator";
        }
        WorldRules.set(server, WorldRuleSpecs.CREATIVE_FC_RACE_LEGAL, 0);
        WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, 1);
        RaceStandards.set(server, RaceStandard.DEFAULTS, null, "Beitrittstest");
        PersonalRulesDev.clear(server);
        Vec3 at = RaceDataJoinCheck.origin(player).add(0.0, 0.0, 40.0);
        CourseRegistry.Course created = RaceDataJoinCheck.build(player, at, SPAWNED);
        if (created == null) {
            return "Kurs nicht angelegt";
        }
        if (!created.ownedBy(player.getUUID()) || created.creativeFc() != CreativeFcRule.WORLD) {
            return "Kurs falsch angelegt";
        }
        course = created.id;
        origin = at;
        return null;
    }

    private static String flights(MinecraftServer server, ServerPlayer player, CourseRegistry.Course built) {
        CourseRegistry registry = CourseRegistry.get();
        Vec3 at = origin;
        if (registry == null || at == null) {
            return "fail kein Kurs";
        }
        DroneBuild build = creativeBuild();
        Leaderboard board = registry.board(built);
        UUID id = player.getUUID();
        boolean[] worlds = {false, false, true, false};
        CreativeFcRule[] rules = {CreativeFcRule.LEGAL, CreativeFcRule.ASSIST, CreativeFcRule.WORLD, CreativeFcRule.WORLD};
        String[] names = {"legal", "assist", "world_on", "world_off"};
        long lap = 12 * S;
        List<String> expected = new ArrayList<>(List.of("owner_denied=yes", "admin_set=yes"));
        for (int i = 0; i < rules.length; i++) {
            WorldRules.set(server, WorldRuleSpecs.CREATIVE_FC_RACE_LEGAL, worlds[i] ? 1 : 0);
            registry.setCreativeFc(built, rules[i]);
            String flown = RaceRulesJoinCheck.fly(built, player, at, lap, () -> {
            }, build);
            if (flown != null) {
                return "fail " + names[i] + ": " + flown;
            }
            OUT.add(names[i] + "=" + rated(board, id, lap));
            expected.add(names[i] + "=" + (rules[i].legal(worlds[i]) ? LEGAL : "assisted[CREATIVE_DRONE]"));
            lap -= S;
        }
        OUT.add("kept=" + rated(board, id, 10 * S));
        expected.add("kept=" + LEGAL);
        registry.setCreativeFc(built, CreativeFcRule.LEGAL);
        boolean ok = OUT.equals(expected);
        return (ok ? "ok " : "fail ") + String.join(" ", OUT);
    }

    private static void cleanup(MinecraftServer server) {
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
        WorldRules.set(server, WorldRuleSpecs.CREATIVE_FC_RACE_LEGAL, WorldRuleSpecs.CREATIVE_FC_RACE_LEGAL.defaultValue());
        WorldRules.set(server, WorldRuleSpecs.SAVE_ASSISTED_LAPS, WorldRuleSpecs.SAVE_ASSISTED_LAPS.defaultValue());
        Constants.LOG.info("[Beitrittstest] creative-fc: aufgeräumt");
    }
}
