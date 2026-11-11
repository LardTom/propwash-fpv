package de.lardtom.propwash.apiimpl;

import de.lardtom.propwash.api.race.BoardQuery;
import de.lardtom.propwash.api.race.BoardSnapshot;
import de.lardtom.propwash.api.race.CourseInfo;
import de.lardtom.propwash.api.race.RaceData;
import de.lardtom.propwash.api.race.RaceStandardInfo;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.RaceStandards;
import de.lardtom.propwash.race.export.RaceDataEvents;
import de.lardtom.propwash.race.export.RaceSnapshots;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RaceDataImpl implements RaceData {
    public static final RaceDataImpl INSTANCE = new RaceDataImpl();

    private RaceDataImpl() {
    }

    @Override
    public int version() {
        return VERSION;
    }

    private static @Nullable CourseRegistry registry(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) {
            throw new IllegalStateException("Renndaten nur auf dem Server-Thread abrufen (server.execute oder server.submit)");
        }
        CourseRegistry registry = CourseRegistry.get();
        return registry != null && registry.server() == server ? registry : null;
    }

    @Override
    public List<CourseInfo> courses(MinecraftServer server) {
        CourseRegistry registry = registry(server);
        if (registry == null) {
            return List.of();
        }
        List<CourseInfo> out = new ArrayList<>();
        for (CourseRegistry.Course course : registry.courses()) {
            out.add(RaceDataEvents.info(registry, course));
        }
        return List.copyOf(out);
    }

    @Override
    public List<CourseInfo> courses(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        CourseRegistry registry = registry(level.getServer());
        if (registry == null) {
            return List.of();
        }
        String dimension = CourseRegistry.dimensionOf(level);
        List<CourseInfo> out = new ArrayList<>();
        for (CourseRegistry.Course course : registry.courses()) {
            if (course.dimension.equals(dimension)) {
                out.add(RaceDataEvents.info(registry, course));
            }
        }
        return List.copyOf(out);
    }

    @Override
    public Optional<CourseInfo> course(MinecraftServer server, UUID id) {
        Objects.requireNonNull(id, "id");
        CourseRegistry registry = registry(server);
        CourseRegistry.Course course = registry == null ? null : registry.course(id);
        return course == null ? Optional.empty() : Optional.of(RaceDataEvents.info(registry, course));
    }

    @Override
    public Optional<CourseInfo> courseByNumber(MinecraftServer server, int number) {
        CourseRegistry registry = registry(server);
        CourseRegistry.Course course = registry == null ? null : registry.course(number);
        return course == null ? Optional.empty() : Optional.of(RaceDataEvents.info(registry, course));
    }

    @Override
    public RaceStandardInfo standard(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) {
            throw new IllegalStateException("Renndaten nur auf dem Server-Thread abrufen (server.execute oder server.submit)");
        }
        return RaceSnapshots.standard(RaceStandards.get(server));
    }

    @Override
    public RaceStandardInfo serverRules(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) {
            throw new IllegalStateException("Renndaten nur auf dem Server-Thread abrufen (server.execute oder server.submit)");
        }
        return RaceSnapshots.standard(RaceStandards.server(server), 0L, "");
    }

    @Override
    public Optional<BoardSnapshot> leaderboard(MinecraftServer server, UUID course, BoardQuery query) {
        Objects.requireNonNull(course, "course");
        Objects.requireNonNull(query, "query");
        CourseRegistry registry = registry(server);
        CourseRegistry.Course stored = registry == null ? null : registry.course(course);
        return stored == null ? Optional.empty() : RaceSnapshots.board(stored, registry.records(), query);
    }
}
