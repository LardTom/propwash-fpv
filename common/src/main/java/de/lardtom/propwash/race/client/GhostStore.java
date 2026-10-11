package de.lardtom.propwash.race.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.race.GhostCodec;
import de.lardtom.propwash.race.GhostTrack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class GhostStore {
    private static final String EXTENSION = ".pwghost";
    private static final String ASSISTED = "assisted";
    private static final int MAX_KEY = 64;
    private static @Nullable Path override;

    private GhostStore() {
    }

    public static void overrideRoot(@Nullable Path root) {
        override = root;
    }

    static @Nullable String worldKey(Minecraft minecraft) {
        IntegratedServer local = minecraft.getSingleplayerServer();
        if (local != null) {
            Path root = local.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path name = root.getFileName();
            return name == null ? null : "sp_" + sanitize(name.toString());
        }
        ServerData server = minecraft.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return "mp_" + sanitize(server.ip);
        }
        return null;
    }

    static String sanitize(String value) {
        StringBuilder out = new StringBuilder(Math.min(value.length(), MAX_KEY));
        for (char c : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if (out.length() >= MAX_KEY) {
                break;
            }
            out.append(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-' || c == '_' || c == '.' ? c : '_');
        }
        return out.isEmpty() ? "_" : out.toString();
    }

    static Path root(Minecraft minecraft) {
        Path root = override;
        return root != null ? root : minecraft.gameDirectory.toPath().resolve(Constants.MOD_ID).resolve("ghosts");
    }

    static @Nullable Path file(Minecraft minecraft, int courseId, String frame) {
        return file(minecraft, courseId, frame, false);
    }

    static @Nullable Path file(Minecraft minecraft, int courseId, String frame, boolean assisted) {
        String world = worldKey(minecraft);
        if (world == null) {
            return null;
        }
        Path folder = root(minecraft).resolve(world);
        if (assisted) {
            folder = folder.resolve(ASSISTED);
        }
        return folder.resolve("course_" + courseId + "_" + frameFileKey(frame) + EXTENSION);
    }

    static String frameFileKey(String frame) {
        if (frame.isEmpty()) {
            return "any";
        }
        int colon = frame.indexOf(':');
        if (colon < 0) {
            return sanitize(frame);
        }
        return sanitize(frame.substring(0, colon)) + "~" + sanitize(frame.substring(colon + 1));
    }

    static @Nullable GhostTrack load(Minecraft minecraft, int courseId, String frame) {
        return load(minecraft, courseId, frame, false);
    }

    static @Nullable GhostTrack load(Minecraft minecraft, int courseId, String frame, boolean assisted) {
        Path file = file(minecraft, courseId, frame, assisted);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            if (Files.size(file) > GhostCodec.MAX_ENCODED_BYTES) {
                return null;
            }
            return GhostCodec.decode(Files.readAllBytes(file));
        } catch (IOException e) {
            Constants.LOG.warn("Geisterspur {} nicht lesbar: {}", file, e.getMessage());
            return null;
        }
    }

    static void save(Minecraft minecraft, int courseId, String frame, GhostTrack track) {
        save(minecraft, courseId, frame, track, false);
    }

    static void save(Minecraft minecraft, int courseId, String frame, GhostTrack track, boolean assisted) {
        Path file = file(minecraft, courseId, frame, assisted);
        if (file == null) {
            return;
        }
        byte[] data = GhostCodec.encode(track);
        Util.ioPool().execute(() -> write(file, data));
    }

    static void write(Path file, byte[] data) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp, data);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Constants.LOG.warn("Geisterspur {} nicht gespeichert: {}", file, e.getMessage());
        }
    }
}
