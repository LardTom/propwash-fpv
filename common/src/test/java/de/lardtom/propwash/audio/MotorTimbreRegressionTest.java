package de.lardtom.propwash.audio;

import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MotorTimbreRegressionTest {
    private static final String EXPECTED = "992b989ebe314acf89ca0b35";

    private static <T extends PartInfo> List<T> builtins(T[] values) {
        List<T> out = new ArrayList<>();
        for (T value : values) {
            if (value.builtin() && !(value instanceof StackType stack && stack.creative())) {
                out.add(value);
            }
        }
        return out;
    }

    @Test
    void builtinTimbresStayBitIdentical() throws Exception {
        StringBuilder dump = new StringBuilder();
        for (FrameType frame : builtins(FrameType.values())) {
            for (StackType stack : builtins(StackType.values())) {
                for (MotorType motor : builtins(MotorType.values())) {
                    for (PropType prop : builtins(PropType.values())) {
                        for (BatteryType battery : builtins(BatteryType.values())) {
                            DroneBuild build = new DroneBuild(frame, stack, motor, prop, VideoType.values()[0], battery);
                            if (build.buildable()) {
                                dump.append(MotorTimbre.derive(build)).append('\n');
                            }
                        }
                    }
                }
            }
        }
        for (DroneBuild preset : DroneBuild.PRESETS) {
            dump.append(MotorTimbre.derive(preset)).append('\n');
        }
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dump.toString().getBytes(StandardCharsets.UTF_8)))
                .substring(0, 24);
        assertEquals(EXPECTED, hash, "Motorklang eingebauter Teile hat sich geändert");
    }
}
