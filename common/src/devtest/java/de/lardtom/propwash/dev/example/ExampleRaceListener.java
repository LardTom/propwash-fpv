package de.lardtom.propwash.dev.example;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.race.BoardQuery;
import de.lardtom.propwash.api.race.BoardSnapshot;
import de.lardtom.propwash.api.race.CourseRecord;
import de.lardtom.propwash.api.race.LapResult;
import de.lardtom.propwash.api.race.PersonalBest;
import de.lardtom.propwash.api.race.RaceEvents;
import de.lardtom.propwash.api.race.RaceResult;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ExampleRaceListener {
    public static final String PREFIX = "[Beispiel Renndaten] ";
    public static final List<LapResult> LAPS = new CopyOnWriteArrayList<>();
    public static final List<RaceResult> RACES = new CopyOnWriteArrayList<>();
    public static final List<PersonalBest> BESTS = new CopyOnWriteArrayList<>();
    public static final List<CourseRecord> RECORDS = new CopyOnWriteArrayList<>();

    private static boolean initialized;

    private ExampleRaceListener() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        RaceEvents.LAP_COMPLETED.register(lap -> {
            LAPS.add(lap);
            Constants.LOG.info(PREFIX + "Runde {} von {} auf Kurs {} ({}): {} ms, {}{}", lap.lap(), lap.playerName(), lap.course(), lap.number(),
                    lap.timeMillis(), lap.category().key(), lap.personalBest() ? ", persönliche Bestzeit" : "");
        });
        RaceEvents.RACE_FINISHED.register(race -> {
            RACES.add(race);
            Constants.LOG.info(PREFIX + "Rennen beendet: {} auf Kurs {} ({}), {} Runden in {} ms, {}", race.playerName(), race.course(), race.number(),
                    race.laps(), race.timeMillis(), race.category().key());
        });
        RaceEvents.PERSONAL_BEST.register(best -> {
            BESTS.add(best);
            Constants.LOG.info(PREFIX + "Persönliche Bestzeit: {} auf Kurs {} ({} {}), {} ms, Platz {}", best.entry().name(), best.course(),
                    best.entry().kind().key(), best.entry().category().key(), best.entry().timeMillis(), best.entry().rank());
        });
        RaceEvents.COURSE_RECORD.register(record -> {
            RECORDS.add(record);
            Constants.LOG.info(PREFIX + "Neuer Kursrekord auf {} ({}): {} in {} ms", record.course(), record.courseName(), record.entry().name(),
                    record.entry().timeMillis());
        });
    }

    public static Optional<BoardSnapshot> topTen(MinecraftServer server, UUID course) {
        return PropwashApi.race().leaderboard(server, course, BoardQuery.laps().limit(10));
    }

    public static void clear() {
        LAPS.clear();
        RACES.clear();
        BESTS.clear();
        RECORDS.clear();
    }
}
