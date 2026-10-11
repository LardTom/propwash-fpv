package de.lardtom.propwash.video.client.record;

import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.video.record.external.FfmpegCodec;
import de.lardtom.propwash.video.record.external.FfmpegLocator;
import de.lardtom.propwash.video.record.external.HardwareEncoders;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

public final class FfmpegStates {
    public static final String TAB = "action_cam";
    private static final String[] NAMES = {"fehlt", "unbrauchbar", "keine hardware", "bereit vier encoder", "defekt", "eigener pfad fehlt",
            "eigener pfad nicht ausführbar", "prüft"};
    private static final Path WINDOWS = Path.of("D:\\Spiele\\Minecraft\\Profile mit langem Namen\\Fabric-26.3\\propwash\\ffmpeg\\ffmpeg-8.0-full_build\\bin\\ffmpeg.exe");
    private static final Path MAC = Path.of("/Applications/Spiele/Minecraft/Profile mit langem Namen/propwash/ffmpeg/ffmpeg-8.0-macos-arm64/bin/ffmpeg");
    private static final String VERSION = "8.0-full_build-www.gyan.dev";
    private static final String DETAIL = "Error while opening encoder for output stream #0:0 - maybe incorrect parameters such as bit_rate, rate, width or "
            + "height; h264_nvenc: OpenEncodeSessionEx failed: out of memory (10); h264_qsv: Error initializing an internal MFX session: unsupported (-3)";
    private static final List<HardwareEncoders.Trial> FOUR = List.of(new HardwareEncoders.Trial(FfmpegCodec.NVENC, 0, 412.0, ""),
            new HardwareEncoders.Trial(FfmpegCodec.QSV, 0, 388.0, ""), new HardwareEncoders.Trial(FfmpegCodec.AMF, 0, 301.0, ""),
            new HardwareEncoders.Trial(FfmpegCodec.MEDIA_FOUNDATION, 0, 144.0, "h264_mf: could not initialise the encoder session (0x80004005)"));

    private FfmpegStates() {
    }

    public static int count() {
        return NAMES.length;
    }

    public static String name(int index) {
        return NAMES[index];
    }

    public static boolean show(@Nullable ConfigTab tab, int index) {
        if (!(tab instanceof ActionCamTab cam)) {
            return false;
        }
        cam.preview(status(index));
        return true;
    }

    public static void clear(@Nullable ConfigTab tab) {
        if (tab instanceof ActionCamTab cam) {
            cam.preview(null);
        }
    }

    private static HardwareEncoders.Status status(int index) {
        FfmpegLocator.CustomPath none = FfmpegLocator.CustomPath.NONE;
        return switch (index) {
            case 0 -> new HardwareEncoders.Status(HardwareEncoders.State.NO_FFMPEG, "", null, null, 0, 0.0, "", null, none, List.of());
            case 1 -> new HardwareEncoders.Status(HardwareEncoders.State.UNUSABLE, DETAIL, WINDOWS, null, 0, 0.0, "", FfmpegLocator.Source.KNOWN, none,
                    List.of());
            case 2 -> new HardwareEncoders.Status(HardwareEncoders.State.NO_HARDWARE, DETAIL, MAC, null, 0, 0.0, VERSION, FfmpegLocator.Source.KNOWN, none,
                    List.of());
            case 3 -> new HardwareEncoders.Status(HardwareEncoders.State.READY, "NVENC", WINDOWS, FfmpegCodec.NVENC, 0, 412.0, VERSION,
                    FfmpegLocator.Source.GAME_FOLDER, none, FOUR);
            case 4 -> new HardwareEncoders.Status(HardwareEncoders.State.BROKEN, DETAIL, WINDOWS, FfmpegCodec.NVENC, 0, 0.0, "8.0",
                    FfmpegLocator.Source.PATH, none, List.of());
            case 5 -> new HardwareEncoders.Status(HardwareEncoders.State.NO_FFMPEG, "", null, null, 0, 0.0, "", null, FfmpegLocator.CustomPath.MISSING,
                    List.of());
            case 6 -> new HardwareEncoders.Status(HardwareEncoders.State.READY, "NVENC", MAC, FfmpegCodec.NVENC, 0, 412.0, "8.0",
                    FfmpegLocator.Source.CUSTOM, FfmpegLocator.CustomPath.NOT_EXECUTABLE, FOUR);
            default -> new HardwareEncoders.Status(HardwareEncoders.State.CHECKING, "", null, null, 0, 0.0, "", null, none, List.of());
        };
    }
}
