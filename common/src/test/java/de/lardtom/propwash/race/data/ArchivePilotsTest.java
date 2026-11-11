package de.lardtom.propwash.race.data;

import de.lardtom.propwash.race.CourseRegistry;
import de.lardtom.propwash.race.RaceCommands;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchivePilotsTest {
    private static final String LINE = "propwash.race.command.archive_line";
    private static final long S = TestWorlds.S;
    private static final long DAY = TestWorlds.DAY;

    @TempDir
    Path temp;

    @Test
    void archiveLineCountsPilotsNotRows() throws Exception {
        CourseRegistry registry = TestWorlds.open(this.temp, TestWorlds.V052);
        CourseRegistry.Course course = registry.course(1);
        registry.submitLap(course, TestWorlds.ANNA, "Anna", "race_5", 30 * S, DAY);
        registry.submitLap(course, TestWorlds.ANNA, "Anna", "whoop_65", 25 * S, DAY + 10);
        registry.submitLap(course, TestWorlds.BEN, "Ben", "race_5", 32 * S, DAY + 20);
        assertEquals(3, registry.board(course).top(null, Integer.MAX_VALUE).size(), "„Alle“ mit einer Zeile je Pilot und Klasse");
        registry.records().archiveCurrent(course.id, 1, course.gates.size(), DAY + 100);
        registry.submitLap(course, TestWorlds.CARL, "Carl", "race_5", 29 * S, DAY + 200);
        registry.submitLap(course, TestWorlds.CARL, "Carl", "whoop_65", 27 * S, DAY + 210);
        registry.records().archiveCurrent(course.id, 2, course.gates.size(), DAY + 300);

        List<Object[]> lines = archiveLines(registry, course);

        assertEquals(2, lines.size());
        assertEquals(2, lines.getFirst()[0], "neuestes Layout zuerst");
        assertEquals(1, ((Number) lines.getFirst()[3]).intValue(), "Carl in zwei Klassen ist ein Pilot");
        assertEquals(1, lines.get(1)[0]);
        assertEquals(3, lines.get(1)[1]);
        assertEquals(2, ((Number) lines.get(1)[3]).intValue(), "Anna in zwei Klassen und Ben sind zwei Piloten");
    }

    @Test
    void pilotsWithOnlyOneClassStayCountedOnce() throws Exception {
        CourseRegistry registry = TestWorlds.open(this.temp, TestWorlds.V052);
        CourseRegistry.Course course = registry.course(1);
        registry.submitLap(course, TestWorlds.ANNA, "Anna", "race_5", 30 * S, DAY);
        registry.submitLap(course, TestWorlds.BEN, "Ben", "race_5", 32 * S, DAY + 20);
        registry.submitLap(course, TestWorlds.CARL, "Carl", "whoop_65", 27 * S, DAY + 30);
        registry.records().archiveCurrent(course.id, 1, course.gates.size(), DAY + 100);

        List<Object[]> lines = archiveLines(registry, course);

        assertEquals(1, lines.size());
        assertEquals(3, ((Number) lines.getFirst()[3]).intValue());
    }

    private static List<Object[]> archiveLines(CourseRegistry registry, CourseRegistry.Course course) throws Exception {
        List<Component> messages = new ArrayList<>();
        CommandSource sink = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                messages.add(message);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = new CommandSourceStack(sink, Vec3.ZERO, Vec2.ZERO, null, PermissionSet.ALL_PERMISSIONS, Component.literal("test"), null);
        Method archive = RaceCommands.class.getDeclaredMethod("archive", CommandSourceStack.class, CourseRegistry.class, CourseRegistry.Course.class);
        archive.setAccessible(true);
        int shown = (int) archive.invoke(null, source, registry, course);
        List<Object[]> lines = new ArrayList<>();
        for (Component message : messages) {
            if (message.getContents() instanceof TranslatableContents contents && contents.getKey().equals(LINE)) {
                lines.add(contents.getArgs());
            }
        }
        assertEquals(shown, lines.size());
        return lines;
    }
}
