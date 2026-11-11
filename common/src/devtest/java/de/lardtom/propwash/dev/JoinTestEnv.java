package de.lardtom.propwash.dev;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JoinTestEnv {
    public static final String EXAMPLE = "PROPWASH_DEV_EXAMPLE";
    public static final String PROTOCOL = "PROPWASH_DEV_PROTOCOL";
    public static final String TARGET = "PROPWASH_JOINTEST";
    public static final String LABEL = "PROPWASH_JOINTEST_LABEL";
    public static final String EXPECT = "PROPWASH_JOINTEST_EXPECT";
    public static final String HOLD = "PROPWASH_JOINTEST_HOLD";
    public static final String PILOT = "PROPWASH_JOINTEST_PILOT";
    public static final String TRAIL = "PROPWASH_JOINTEST_TRAIL";
    public static final String ASSIST = "PROPWASH_JOINTEST_ASSIST";
    public static final String ZONES = "PROPWASH_JOINTEST_ZONES";
    public static final String SPECTATE_FPM = "PROPWASH_JOINTEST_SPECTATE_FPM";
    public static final String RX = "PROPWASH_JOINTEST_RX";
    public static final String PLATES = "PROPWASH_JOINTEST_PLATES";
    public static final String STACKGATE = "PROPWASH_JOINTEST_STACKGATE";
    public static final String SIGNAL = "PROPWASH_JOINTEST_SIGNAL";
    public static final String GATEHINT = "PROPWASH_JOINTEST_GATEHINT";
    public static final String MULTI = "PROPWASH_JOINTEST_MULTI";
    public static final String ADDON17 = "PROPWASH_JOINTEST_ADDON17";
    public static final String ADDONDATA = "PROPWASH_JOINTEST_ADDONDATA";
    public static final String COLLISION = "PROPWASH_JOINTEST_COLLISION";
    public static final String SHARE = "PROPWASH_JOINTEST_SHARE";
    public static final String RACEHOME = "PROPWASH_JOINTEST_RACEHOME";
    public static final String SHARE_CHAT = "PROPWASH_JOINTEST_SHARE_CHAT";
    public static final String FREECAM = "PROPWASH_JOINTEST_FREECAM";
    public static final String RACELOOP = "PROPWASH_JOINTEST_RACELOOP";
    public static final String COV_FREECAM = "PROPWASH_JOINTEST_COV_FREECAM";
    public static final String RACEDATA = "PROPWASH_JOINTEST_RACEDATA";
    public static final String OPERATOR = "PROPWASH_JOINTEST_OPERATOR";
    public static final String ACCFIX = "PROPWASH_JOINTEST_ACCFIX";
    public static final String COV_SURFACE = "PROPWASH_JOINTEST_COV_SURFACE";
    public static final String COV_LANDING = "PROPWASH_JOINTEST_COV_LANDING";
    public static final String COV_HEAT = "PROPWASH_JOINTEST_COV_HEAT";
    public static final String COV_CAMANGLE = "PROPWASH_JOINTEST_COV_CAMANGLE";
    public static final String COV_RX = "PROPWASH_JOINTEST_COV_RX";
    public static final String COV_VERSIONS = "PROPWASH_JOINTEST_COV_VERSIONS";
    public static final String COV_WATCHRESCUE = "PROPWASH_JOINTEST_COV_WATCHRESCUE";
    public static final String COV_LANGUEST = "PROPWASH_JOINTEST_COV_LANGUEST";
    public static final String DRONESCALE = "PROPWASH_JOINTEST_DRONESCALE";
    public static final String BARRIERS = "PROPWASH_JOINTEST_BARRIERS";
    public static final String ZONES_OPEN = "PROPWASH_JOINTEST_ZONES_OPEN";
    public static final String COV_ADDONRULES = "PROPWASH_JOINTEST_COV_ADDONRULES";
    public static final String COV_NOADDON = "PROPWASH_JOINTEST_COV_NOADDON";
    public static final String COV_NSOP = "PROPWASH_JOINTEST_COV_NSOP";
    public static final String COV_NOCLIP = "PROPWASH_JOINTEST_COV_NOCLIP";
    public static final String COV_RACE = "PROPWASH_JOINTEST_COV_RACE";
    public static final String AUTOARM = "PROPWASH_JOINTEST_AUTOARM";
    public static final String RACE_RULES = "PROPWASH_JOINTEST_RACE_RULES";
    public static final String COV_SHARE = "PROPWASH_JOINTEST_COV_SHARE";
    public static final String COV_CHATSHARE = "PROPWASH_JOINTEST_COV_CHATSHARE";
    public static final String COV_WEBEXPORT = "PROPWASH_JOINTEST_COV_WEBEXPORT";
    public static final String RACE_P2P = "PROPWASH_JOINTEST_RACE_P2P";
    public static final String AUTONOMY_OFFLINE = "PROPWASH_JOINTEST_AUTONOMY_OFFLINE";
    public static final String AUTONOMY_OFFLINE_SIM = "PROPWASH_JOINTEST_AUTONOMY_OFFLINE_SIM";
    public static final String AUTONOMY_FAR = "PROPWASH_JOINTEST_AUTONOMY_FAR";
    public static final String DRONE_ROTATION = "PROPWASH_JOINTEST_DRONE_ROTATION";
    public static final String AUTONOMY_HOME = "PROPWASH_JOINTEST_AUTONOMY_HOME";
    public static final String GATEPUSH = "PROPWASH_JOINTEST_GATEPUSH";
    public static final String RACE_GHOSTS = "PROPWASH_JOINTEST_RACE_GHOSTS";
    public static final String STATS = "PROPWASH_JOINTEST_STATS";
    public static final String CAPTURE = "PROPWASH_JOINTEST_CAPTURE";
    public static final String COURSE_MENU = "PROPWASH_JOINTEST_COURSE_MENU";
    public static final String SPECCOUNT = "PROPWASH_JOINTEST_SPECCOUNT";
    public static final String CREATIVE_FC = "PROPWASH_JOINTEST_CREATIVE_FC";
    public static final String COV_CRASHRULES = "PROPWASH_JOINTEST_COV_CRASHRULES";
    public static final String COV_VINE = "PROPWASH_JOINTEST_COV_VINE";
    public static final String DRONE_SOUND = "PROPWASH_JOINTEST_DRONE_SOUND";
    public static final String COV_CRASHWALL = "PROPWASH_JOINTEST_COV_CRASHWALL";
    public static final String CROSSLOADER = "PROPWASH_JOINTEST_CROSSLOADER";
    public static final String MENUS = "PROPWASH_JOINTEST_MENUS";
    public static final String MULTI_MOBS = "PROPWASH_JOINTEST_MULTI_MOBS";
    public static final String STOP_FILE = "jointest.stop";
    public static final String JOINED = "joined";

    private JoinTestEnv() {
    }

    public static boolean exampleAddon() {
        String value = get(EXAMPLE);
        return value == null || !value.trim().toLowerCase(Locale.ROOT).matches("off|false|0|no");
    }

    public static int protocolOverride() {
        return integer(PROTOCOL, 0);
    }

    public static @Nullable String target() {
        String value = get(TARGET);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String label() {
        String value = get(LABEL);
        return value == null || value.isBlank() ? "join" : value.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    public static List<String> expectations() {
        String value = get(EXPECT);
        List<String> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        for (String part : value.split("\\|")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    public static int holdSeconds() {
        return integer(HOLD, 0);
    }

    public static int pilotSeconds() {
        return integer(PILOT, 0);
    }

    public static int trailSeconds() {
        return integer(TRAIL, 0);
    }

    public static boolean assist() {
        return integer(ASSIST, 0) > 0;
    }

    public static boolean zones() {
        return integer(ZONES, 0) > 0;
    }

    public static int spectateFpmSeconds() {
        return integer(SPECTATE_FPM, 0);
    }

    public static boolean rx() {
        return integer(RX, 0) > 0;
    }

    public static boolean plates() {
        return integer(PLATES, 0) > 0;
    }

    public static boolean stackGate() {
        return integer(STACKGATE, 0) > 0;
    }

    public static boolean signal() {
        return integer(SIGNAL, 0) > 0;
    }

    public static boolean gateHint() {
        return integer(GATEHINT, 0) > 0;
    }

    public static @Nullable String multi() {
        String value = get(MULTI);
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean addon17() {
        return integer(ADDON17, 0) > 0;
    }

    public static boolean addonData() {
        return integer(ADDONDATA, 0) > 0;
    }

    public static boolean collision() {
        return integer(COLLISION, 0) > 0;
    }

    public static boolean share() {
        return integer(SHARE, 0) > 0;
    }

    public static boolean raceHome() {
        return integer(RACEHOME, 0) > 0;
    }

    public static boolean shareChat() {
        return integer(SHARE_CHAT, 0) > 0;
    }

    public static boolean freecam() {
        return integer(FREECAM, 0) > 0;
    }

    public static boolean raceLoop() {
        return integer(RACELOOP, 0) > 0;
    }

    public static boolean covFreecam() {
        return integer(COV_FREECAM, 0) > 0;
    }

    public static boolean raceData() {
        return integer(RACEDATA, 0) > 0;
    }

    public static boolean operator() {
        return integer(OPERATOR, 0) > 0;
    }

    public static boolean accFix() {
        return integer(ACCFIX, 0) > 0;
    }

    public static boolean covSurface() {
        return integer(COV_SURFACE, 0) > 0;
    }

    public static boolean covLanding() {
        return integer(COV_LANDING, 0) > 0;
    }

    public static boolean covHeat() {
        return integer(COV_HEAT, 0) > 0;
    }

    public static boolean covCamangle() {
        return integer(COV_CAMANGLE, 0) > 0;
    }

    public static int covRx() {
        return integer(COV_RX, 0);
    }

    public static boolean covVersions() {
        return integer(COV_VERSIONS, 0) > 0;
    }

    public static boolean covWatchRescue() {
        return integer(COV_WATCHRESCUE, 0) > 0;
    }

    public static boolean covLanGuest() {
        return integer(COV_LANGUEST, 0) > 0;
    }

    public static boolean droneScale() {
        return integer(DRONESCALE, 0) > 0;
    }

    public static boolean barriers() {
        return integer(BARRIERS, 0) > 0;
    }

    public static boolean zonesOpen() {
        return integer(ZONES_OPEN, 0) > 0;
    }

    public static boolean covAddonRules() {
        return integer(COV_ADDONRULES, 0) > 0;
    }

    public static boolean covNoAddon() {
        return integer(COV_NOADDON, 0) > 0;
    }

    public static boolean covNsOp() {
        return integer(COV_NSOP, 0) > 0;
    }

    public static boolean covNoclip() {
        return integer(COV_NOCLIP, 0) > 0;
    }

    public static int covRace() {
        return integer(COV_RACE, 0);
    }

    public static boolean autoArm() {
        return integer(AUTOARM, 0) > 0;
    }

    public static boolean raceRules() {
        return integer(RACE_RULES, 0) > 0;
    }

    public static boolean covShare() {
        return integer(COV_SHARE, 0) > 0;
    }

    public static boolean covChatShare() {
        return integer(COV_CHATSHARE, 0) > 0;
    }

    public static boolean covWebexport() {
        return integer(COV_WEBEXPORT, 0) > 0;
    }

    public static boolean raceP2p() {
        return integer(RACE_P2P, 0) > 0;
    }

    public static int autonomyOffline() {
        return integer(AUTONOMY_OFFLINE, 0);
    }

    public static boolean autonomyOfflineSim() {
        return integer(AUTONOMY_OFFLINE_SIM, 0) > 0;
    }

    public static boolean autonomyFar() {
        return integer(AUTONOMY_FAR, 0) > 0;
    }

    public static boolean droneRotation() {
        return integer(DRONE_ROTATION, 0) > 0;
    }

    public static boolean autonomyHome() {
        return integer(AUTONOMY_HOME, 0) > 0;
    }

    public static boolean gatePush() {
        return integer(GATEPUSH, 0) > 0;
    }

    public static boolean raceGhosts() {
        return integer(RACE_GHOSTS, 0) > 0;
    }

    public static boolean stats() {
        return integer(STATS, 0) > 0;
    }

    public static boolean capture() {
        return integer(CAPTURE, 0) > 0;
    }

    public static boolean courseMenu() {
        return integer(COURSE_MENU, 0) > 0;
    }

    public static int specCount() {
        return integer(SPECCOUNT, 0);
    }

    public static boolean creativeFc() {
        return integer(CREATIVE_FC, 0) > 0;
    }

    public static int raceRulesMode() {
        return integer(RACE_RULES, 0);
    }

    public static boolean covCrashRules() {
        return integer(COV_CRASHRULES, 0) > 0;
    }

    public static boolean covVine() {
        return integer(COV_VINE, 0) > 0;
    }

    public static boolean droneSound() {
        return integer(DRONE_SOUND, 0) > 0;
    }

    public static boolean stackGateTurned() {
        return integer(STACKGATE, 0) == 2;
    }

    public static boolean covCrashWall() {
        return integer(COV_CRASHWALL, 0) > 0;
    }

    private static int integer(String name, int fallback) {
        String value = get(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static boolean crossLoader() {
        return integer(CROSSLOADER, 0) > 0;
    }

    public static boolean menus() {
        return integer(MENUS, 0) > 0;
    }

    public static int multiMobs() {
        return integer(MULTI_MOBS, 0);
    }

    private static @Nullable String get(String name) {
        String property = System.getProperty(name.toLowerCase(Locale.ROOT).replace('_', '.'));
        return property != null ? property : System.getenv(name);
    }
}
