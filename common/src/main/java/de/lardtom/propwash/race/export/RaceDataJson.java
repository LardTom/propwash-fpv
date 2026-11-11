package de.lardtom.propwash.race.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import de.lardtom.propwash.api.race.AssistInfo;
import de.lardtom.propwash.api.race.BoardEntry;
import de.lardtom.propwash.api.race.BoardQuery;
import de.lardtom.propwash.api.race.BoardSnapshot;
import de.lardtom.propwash.api.race.Category;
import de.lardtom.propwash.api.race.CourseInfo;
import de.lardtom.propwash.api.race.RaceStandardInfo;
import de.lardtom.propwash.api.race.RuleValue;
import de.lardtom.propwash.api.race.TimeKind;
import de.lardtom.propwash.api.race.ZoneHint;
import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.RaceFormat;
import de.lardtom.propwash.race.RaceRecords;
import de.lardtom.propwash.race.RaceStandards;
import net.minecraft.core.BlockPos;

import java.util.List;

public final class RaceDataJson {
    public static final int SCHEMA = 1;
    public static final String COURSES = "courses.json";
    public static final String LEADERBOARDS = "leaderboards";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

    private RaceDataJson() {
    }

    public static String leaderboardPath(CourseInfo course) {
        return LEADERBOARDS + "/" + course.id() + ".json";
    }

    public static String courses(CourseRegistry registry, List<CourseInfo> courses) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        root.add("raceStandard", standard(RaceSnapshots.standard(RaceStandards.current(), RaceStandards.changedAtNow(), RaceStandards.changedByNow())));
        root.add("serverRules", standard(RaceSnapshots.standard(RaceStandards.serverNow(), 0L, "")));
        JsonArray list = new JsonArray();
        for (CourseInfo course : courses) {
            CourseRegistry.Course stored = registry.course(course.number());
            list.add(course(course, RaceSnapshots.creativeFc(stored), RaceSnapshots.recordCreativeFc(stored, registry.records())));
        }
        root.add("courses", list);
        return GSON.toJson(root) + "\n";
    }

    public static JsonObject course(CourseInfo course, RaceSnapshots.CreativeFc creativeFc, boolean recordCreativeFc) {
        JsonObject out = new JsonObject();
        out.addProperty("id", course.id().toString());
        out.addProperty("number", course.number());
        out.addProperty("name", course.name());
        out.addProperty("owner", course.owner().map(Object::toString).orElse(null));
        out.addProperty("ownerName", course.ownerName());
        out.addProperty("dimension", course.dimension().toString());
        out.addProperty("layout", course.layout());
        out.add("layouts", ints(course.layouts()));
        out.addProperty("laps", course.laps());
        out.addProperty("mode", course.mode().key());
        out.add("creativeFc", creativeFc(creativeFc));
        out.addProperty("elements", course.elements());
        out.addProperty("checkpoints", course.checkpoints());
        out.addProperty("worldCourse", course.worldCourse());
        out.add("start", course.start().<JsonElement>map(RaceDataJson::position).orElse(JsonNull.INSTANCE));
        out.add("zones", zones(course.zones()));
        out.add("record", course.record().<JsonElement>map(record -> entry(record, recordCreativeFc)).orElse(JsonNull.INSTANCE));
        out.addProperty("leaderboard", leaderboardPath(course));
        return out;
    }

    private static JsonObject position(BlockPos pos) {
        JsonObject out = new JsonObject();
        out.addProperty("x", pos.getX());
        out.addProperty("y", pos.getY());
        out.addProperty("z", pos.getZ());
        return out;
    }

    private static JsonObject zones(ZoneHint hint) {
        JsonObject out = new JsonObject();
        out.addProperty("status", hint.status().key());
        JsonArray list = new JsonArray();
        for (ZoneHint.Zone zone : hint.zones()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", zone.id());
            item.addProperty("name", zone.name());
            item.addProperty("raceLegal", zone.raceLegal());
            item.addProperty("changesPhysics", zone.changesPhysics());
            list.add(item);
        }
        out.add("list", list);
        return out;
    }

    private static JsonArray ints(List<Integer> values) {
        JsonArray out = new JsonArray();
        for (Integer value : values) {
            out.add(value);
        }
        return out;
    }

    public static JsonObject creativeFc(RaceSnapshots.CreativeFc state) {
        JsonObject out = new JsonObject();
        out.addProperty("course", state.course().key());
        out.addProperty("worldRule", state.worldRule());
        out.addProperty("raceLegal", state.raceLegal());
        return out;
    }

    public static JsonObject entry(BoardEntry entry, boolean creativeFc) {
        JsonObject out = new JsonObject();
        out.addProperty("rank", entry.rank());
        out.addProperty("player", entry.player().toString());
        out.addProperty("name", entry.name());
        if (entry.kind() == TimeKind.RACE) {
            out.addProperty("laps", entry.laps());
        }
        out.addProperty("timeMs", entry.timeMillis());
        out.addProperty("time", RaceFormat.time(entry.timeNanos()));
        if (entry.dateKnown()) {
            out.addProperty("date", entry.dateMillis());
        } else {
            out.add("date", JsonNull.INSTANCE);
        }
        out.addProperty("frame", entry.frame());
        JsonArray reasons = new JsonArray();
        for (String reason : entry.assistReasons()) {
            reasons.add(reason);
        }
        out.add("assist", reasons);
        JsonArray details = new JsonArray();
        for (AssistInfo detail : entry.assistDetails()) {
            JsonObject item = new JsonObject();
            item.addProperty("reason", detail.reason());
            item.addProperty("subject", detail.subject());
            item.addProperty("value", detail.value());
            item.addProperty("reference", detail.reference());
            details.add(item);
        }
        out.add("assistDetails", details);
        JsonArray rules = new JsonArray();
        for (RuleValue rule : entry.rules()) {
            JsonObject item = new JsonObject();
            item.addProperty("rule", rule.rule());
            item.addProperty("value", rule.value());
            if (rule.world()) {
                item.add("zone", JsonNull.INSTANCE);
            } else {
                item.addProperty("zone", rule.zone());
            }
            item.addProperty("raceLegalZone", rule.raceLegalZone());
            rules.add(item);
        }
        out.add("rules", rules);
        if (entry.serverRules().isPresent()) {
            JsonObject server = new JsonObject();
            for (RuleValue rule : entry.serverRules().get()) {
                server.addProperty(rule.rule(), rule.value());
            }
            out.add("serverRules", server);
        } else {
            out.add("serverRules", JsonNull.INSTANCE);
        }
        out.addProperty("creativeFc", creativeFc);
        return out;
    }

    public static JsonObject standard(RaceStandardInfo standard) {
        JsonObject out = new JsonObject();
        out.addProperty("defaults", standard.defaults());
        JsonObject values = new JsonObject();
        for (RaceStandardInfo.Rule rule : standard.rules()) {
            if (!rule.atDefault()) {
                values.addProperty(rule.rule(), rule.value());
            }
        }
        out.add("values", values);
        if (standard.changedKnown()) {
            out.addProperty("changedAt", standard.changedAtMillis());
        } else {
            out.add("changedAt", JsonNull.INSTANCE);
        }
        out.addProperty("changedBy", standard.changedBy());
        return out;
    }

    public static String leaderboard(CourseInfo course, CourseRegistry.Course stored, RaceRecords records) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        root.addProperty("course", course.id().toString());
        root.addProperty("number", course.number());
        root.addProperty("name", course.name());
        root.addProperty("mode", course.mode().key());
        root.add("creativeFc", creativeFc(RaceSnapshots.creativeFc(stored)));
        root.addProperty("layout", course.layout());
        root.add("raceStandard", standard(RaceSnapshots.standard(RaceStandards.current(), RaceStandards.changedAtNow(), RaceStandards.changedByNow())));
        root.add("serverRules", standard(RaceSnapshots.standard(RaceStandards.serverNow(), 0L, "")));
        JsonArray layouts = new JsonArray();
        for (RaceSnapshots.Source source : RaceSnapshots.sources(stored, records)) {
            layouts.add(layout(stored, source));
        }
        root.add("layouts", layouts);
        return GSON.toJson(root) + "\n";
    }

    private static JsonObject layout(CourseRegistry.Course course, RaceSnapshots.Source source) {
        JsonObject out = new JsonObject();
        out.addProperty("layout", source.layout());
        out.addProperty("current", source.current());
        if (source.current()) {
            out.add("archivedAt", JsonNull.INSTANCE);
        } else {
            out.addProperty("archivedAt", source.archivedAt());
        }
        out.addProperty("elements", source.elements());
        JsonObject laps = new JsonObject();
        JsonObject classes = new JsonObject();
        JsonObject races = new JsonObject();
        BoardQuery base = BoardQuery.laps().layout(source.layout()).limit(BoardQuery.MAX_LIMIT);
        for (Category category : Category.values()) {
            BoardQuery query = base.category(category);
            BoardSnapshot snapshot = RaceSnapshots.board(course, source, query);
            laps.add(category.key(), entries(RaceSnapshots.rows(source, query)));
            JsonObject byFrame = new JsonObject();
            for (String frame : snapshot.frames()) {
                byFrame.add(frame, entries(RaceSnapshots.rows(source, query.frame(frame))));
            }
            classes.add(category.key(), byFrame);
            JsonObject byLaps = new JsonObject();
            for (int count : snapshot.raceLapCounts()) {
                BoardQuery race = BoardQuery.races(count).category(category).layout(source.layout()).limit(BoardQuery.MAX_LIMIT);
                byLaps.add(Integer.toString(count), entries(RaceSnapshots.rows(source, race)));
            }
            races.add(category.key(), byLaps);
        }
        out.add("laps", laps);
        out.add("classes", classes);
        out.add("races", races);
        return out;
    }

    private static JsonArray entries(List<RaceSnapshots.Row> rows) {
        JsonArray out = new JsonArray();
        for (RaceSnapshots.Row row : rows) {
            out.add(entry(row.entry(), row.creativeFc()));
        }
        return out;
    }
}
