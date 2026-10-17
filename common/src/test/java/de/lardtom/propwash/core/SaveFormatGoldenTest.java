package de.lardtom.propwash.core;

import com.mojang.serialization.Codec;
import de.lardtom.propwash.build.AccessoryType;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.DronePaint;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PaintSlot;
import de.lardtom.propwash.build.PartInfo;
import de.lardtom.propwash.build.PartWear;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.content.paint.PaintCodecs;
import de.lardtom.propwash.race.GhostCodec;
import de.lardtom.propwash.race.GhostLook;
import de.lardtom.propwash.race.GhostTrack;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SaveFormatGoldenTest {
    private static final String RESOURCE = "de/lardtom/propwash/core/save_format_032.txt";
    private static final List<AccessoryType> ACCESSORIES_032 = List.of(AccessoryType.LED_STRIP, AccessoryType.ACTION_CAM_LIGHT,
            AccessoryType.ACTION_CAM_HEAVY);

    private final Map<String, StringBuilder> groups = new LinkedHashMap<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();

    @Test
    void savedAndNetworkFormatsStayAsIn032() throws IOException {
        this.dump();
        Map<String, String> actual = new LinkedHashMap<>();
        this.groups.forEach((group, text) -> actual.put(group, this.counts.get(group) + ":" + hash(text)));
        Map<String, String> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path(), StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                int space = line.indexOf(' ');
                expected.put(line.substring(0, space), line.substring(space + 1));
            }
        }
        assertEquals(expected, actual, "Speicher- oder Netzformat weicht von 0.3.2 ab");
    }

    private void dump() {
        List<DroneBuild> builds = new ArrayList<>();
        for (FrameType f : FrameType.values()) {
            for (StackType s : StackType.values()) {
                if (s.creative()) {
                    continue;
                }
                for (MotorType m : MotorType.values()) {
                    for (PropType p : PropType.values()) {
                        if (p == PropType.P51_29_3B) {
                            continue;
                        }
                        for (VideoType v : VideoType.values()) {
                            for (BatteryType b : BatteryType.values()) {
                                builds.add(new DroneBuild(f, s, m, p, v, b));
                            }
                        }
                    }
                }
            }
        }
        for (DroneBuild current : DroneBuild.PRESETS) {
            DroneBuild preset = current == DroneBuild.RACER ? current.withProp(PropType.P51_3B) : current;
            for (AccessoryType t : ACCESSORIES_032) {
                DroneBuild b = preset.withAccessory(t);
                builds.add(b);
                for (AccessoryType u : ACCESSORIES_032) {
                    if (u.mount != t.mount) {
                        builds.add(b.withAccessory(u));
                    }
                }
            }
        }
        for (DroneBuild b : builds) {
            this.both("build", b, DroneBuildCodecs.CODEC, DroneBuildCodecs.STREAM_CODEC);
        }
        for (PartCategory c : PartCategory.values()) {
            for (PartInfo p : c.parts()) {
                if (p == AccessoryType.GRIPPER || p == AccessoryType.ACTION_CAM_FLASHBACK || p == StackType.CREATIVE_FC
                        || p == PropType.P51_29_3B) {
                    continue;
                }
                this.both("part", PartRef.of(p), PartRef.CODEC, PartRef.STREAM_CODEC);
            }
        }
        UUID id = new UUID(0x1234567890abcdefL, 0x0fedcba987654321L);
        for (FrameType f : FrameType.values()) {
            for (VideoType v : VideoType.values()) {
                this.both("bound", new BoundDrone(id, "Renner " + f.key(), f, v.system), BoundDrone.CODEC, BoundDrone.STREAM_CODEC);
            }
        }
        Map<String, Float> tune = new TreeMap<>();
        tune.put("rates.roll.rc", 1.2F);
        tune.put("pid.roll.p", 45.0F);
        tune.put("camera.uptiltDeg", 25.0F);
        tune.put("led.mode", 3.0F);
        tune.put("vtx.channel", 4.0F);
        this.both("tune", new FlightTune(tune), FlightTune.CODEC, FlightTune.STREAM_CODEC);
        this.both("tune_empty", new FlightTune(Map.of()), FlightTune.CODEC, FlightTune.STREAM_CODEC);
        for (int k = 0; k < 4; k++) {
            float[] s = new float[12];
            for (int i = 0; i < s.length; i++) {
                s[i] = (float) ((i * 37 + k * 11) % 101) / 100.0F;
            }
            this.both("wear" + k, PartWear.fromSlots(s), WearCodecs.CODEC, WearCodecs.STREAM_CODEC);
        }
        this.both("wear_uniform", PartWear.uniform(0.9F, 0.8F, 0.7F, 0.6F, 0.5F, 0.4F), WearCodecs.CODEC, WearCodecs.STREAM_CODEC);
        DronePaint paint = DronePaint.of(Map.of());
        int rgb = 0x102030;
        for (PaintSlot slot : PaintSlot.values()) {
            paint = paint.with(slot, rgb);
            rgb += 0x0a0b0c;
            this.both("paint_" + slot.name(), paint, PaintCodecs.CODEC, PaintCodecs.STREAM_CODEC);
        }
        for (DroneBuild b : builds.subList(builds.size() - 30, builds.size())) {
            byte[] look = GhostLook.of(b, paint).encode();
            assertArrayEquals(look, GhostLook.decode(look).orElseThrow().encode());
            this.line("ghostlook", "ghostlook\t" + HexFormat.of().formatHex(look));
        }
        double[] pos = new double[3 * 40];
        float[] rot = new float[4 * 40];
        for (int i = 0; i < 40; i++) {
            pos[i * 3] = i * 0.37;
            pos[i * 3 + 1] = 64 + Math.sin(i);
            pos[i * 3 + 2] = -i * 0.21;
            rot[i * 4 + 3] = 1.0F;
        }
        GhostTrack track = new GhostTrack(GhostTrack.INTERVAL_NANOS, pos, rot, 39L * GhostTrack.INTERVAL_NANOS, "freestyle_5", 1,
                GhostLook.of(DroneBuild.FREESTYLE.withAccessory(AccessoryType.values()[0]), paint));
        byte[] ghost = GhostCodec.encode(track);
        try {
            assertArrayEquals(ghost, GhostCodec.encode(GhostCodec.decode(ghost)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        this.line("ghost", "ghost\t" + HexFormat.of().formatHex(ghost));
    }

    private <T> void both(String name, T value, Codec<T> codec, StreamCodec<ByteBuf, T> stream) {
        Tag tag = codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
        T back = codec.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertEquals(value, back, name);
        assertEquals(tag, codec.encodeStart(NbtOps.INSTANCE, back).getOrThrow(), name);
        ByteBuf buf = Unpooled.buffer();
        stream.encode(buf, value);
        String hex = ByteBufUtil.hexDump(buf);
        T netBack = stream.decode(buf.copy());
        assertEquals(value, netBack, name);
        ByteBuf again = Unpooled.buffer();
        stream.encode(again, netBack);
        assertEquals(hex, ByteBufUtil.hexDump(again), name);
        this.line(name.replaceAll("\\d+$", ""), name + "\t" + tag + "\t" + hex);
    }

    private void line(String group, String text) {
        this.groups.computeIfAbsent(group, key -> new StringBuilder()).append(text).append('\n');
        this.counts.merge(group, 1, Integer::sum);
    }

    private static String hash(CharSequence text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.toString().getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path path() {
        for (Path candidate : List.of(Path.of("common/src/test/resources", RESOURCE), Path.of("src/test/resources", RESOURCE))) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return Path.of("common/src/test/resources", RESOURCE);
    }
}
