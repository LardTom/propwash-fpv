package de.lardtom.propwash.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.propwash.build.PartWear;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

public final class WearCodecs {
    public static final Codec<Float> CONDITION = Codec.FLOAT.xmap(PartWear::quantize, value -> value);

    private static final List<Float> FULL = List.of(PartWear.NEW, PartWear.NEW, PartWear.NEW, PartWear.NEW);
    private static final Codec<List<Float>> POSITIONS = CONDITION.listOf().flatXmap(WearCodecs::checkPositions, WearCodecs::checkPositions);

    public static final Codec<PartWear> CODEC = RecordCodecBuilder.create(i -> i.group(
            POSITIONS.optionalFieldOf("frame", FULL).forGetter(w -> list(w.arms())),
            CONDITION.optionalFieldOf("stack", PartWear.NEW).forGetter(PartWear::stack),
            POSITIONS.optionalFieldOf("motors", FULL).forGetter(w -> list(w.motors())),
            POSITIONS.optionalFieldOf("props", FULL).forGetter(w -> list(w.props())),
            CONDITION.optionalFieldOf("video", PartWear.NEW).forGetter(PartWear::video),
            CONDITION.optionalFieldOf("battery", PartWear.NEW).forGetter(PartWear::battery)
    ).apply(i, (frame, stack, motors, props, video, battery) -> PartWear.of(array(frame), stack, array(motors), array(props), video, battery)));

    public static final StreamCodec<ByteBuf, PartWear> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PartWear decode(ByteBuf buffer) {
            if (!buffer.readBoolean()) {
                return PartWear.PRISTINE;
            }
            float[] values = new float[PartWear.SLOTS + PartWear.POSITIONS - 1];
            for (int i = 0; i < values.length; i++) {
                values[i] = PartWear.fromSteps(buffer.readShort());
            }
            float[] arms = new float[PartWear.POSITIONS];
            float[] motors = new float[PartWear.POSITIONS];
            float[] props = new float[PartWear.POSITIONS];
            System.arraycopy(values, 0, arms, 0, PartWear.POSITIONS);
            System.arraycopy(values, PartWear.POSITIONS + 1, motors, 0, PartWear.POSITIONS);
            System.arraycopy(values, 2 * PartWear.POSITIONS + 1, props, 0, PartWear.POSITIONS);
            return PartWear.of(arms, values[PartWear.POSITIONS], motors, props, values[3 * PartWear.POSITIONS + 1],
                    values[3 * PartWear.POSITIONS + 2]);
        }

        @Override
        public void encode(ByteBuf buffer, PartWear wear) {
            if (wear.isNew()) {
                buffer.writeBoolean(false);
                return;
            }
            buffer.writeBoolean(true);
            for (float arm : wear.arms()) {
                writeCondition(buffer, arm);
            }
            writeCondition(buffer, wear.stack());
            for (float motor : wear.motors()) {
                writeCondition(buffer, motor);
            }
            for (float prop : wear.props()) {
                writeCondition(buffer, prop);
            }
            writeCondition(buffer, wear.video());
            writeCondition(buffer, wear.battery());
        }
    };

    private WearCodecs() {
    }

    private static void writeCondition(ByteBuf buffer, float condition) {
        buffer.writeShort(PartWear.toSteps(condition));
    }

    private static DataResult<List<Float>> checkPositions(List<Float> values) {
        if (values.size() != PartWear.POSITIONS) {
            return DataResult.error(() -> "Erwartet " + PartWear.POSITIONS + " Werte, bekommen " + values.size());
        }
        return DataResult.success(values);
    }

    private static List<Float> list(float[] values) {
        List<Float> list = new ArrayList<>(values.length);
        for (float value : values) {
            list.add(value);
        }
        return List.copyOf(list);
    }

    private static float[] array(List<Float> values) {
        float[] array = new float[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i);
        }
        return array;
    }
}
