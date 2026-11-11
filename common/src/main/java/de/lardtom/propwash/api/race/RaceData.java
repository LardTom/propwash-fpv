package de.lardtom.propwash.api.race;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceData {
    int VERSION = 1;

    int version();

    List<CourseInfo> courses(MinecraftServer server);

    List<CourseInfo> courses(ServerLevel level);

    Optional<CourseInfo> course(MinecraftServer server, UUID id);

    Optional<CourseInfo> courseByNumber(MinecraftServer server, int number);

    Optional<BoardSnapshot> leaderboard(MinecraftServer server, UUID course, BoardQuery query);

    default RaceStandardInfo standard(MinecraftServer server) {
        return RaceStandardInfo.DEFAULTS;
    }

    default RaceStandardInfo serverRules(MinecraftServer server) {
        return RaceStandardInfo.DEFAULTS;
    }
}
