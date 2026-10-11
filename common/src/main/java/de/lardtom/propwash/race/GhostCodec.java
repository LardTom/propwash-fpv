package de.lardtom.propwash.race;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class GhostCodec {
    public static final int MAGIC = 0x50574731;
    public static final int VERSION = 2;
    public static final int VERSION_WITHOUT_LOOK = 1;
    public static final int POSITION_STEPS = 64;
    public static final int ROTATION_STEPS = 2047;
    public static final int MAX_ENCODED_BYTES = 512 * 1024;
    private static final int MAX_DECODED_BYTES = 4 << 20;
    private static final int MAX_FRAME_LENGTH = 64;

    private GhostCodec() {
    }

    public static byte[] encode(GhostTrack track) {
        return encode(track, VERSION);
    }

    static byte[] encode(GhostTrack track, int version) {
        if (version != VERSION && version != VERSION_WITHOUT_LOOK) {
            throw new IllegalArgumentException("Unbekannte Version " + version);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(track.samples() * 6 + 64);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes, deflater))) {
            out.writeInt(MAGIC);
            out.writeByte(version);
            byte[] frame = track.frame().getBytes(StandardCharsets.UTF_8);
            out.writeByte(Math.min(frame.length, MAX_FRAME_LENGTH));
            out.write(frame, 0, Math.min(frame.length, MAX_FRAME_LENGTH));
            out.writeInt(track.layout());
            if (version >= VERSION) {
                byte[] look = track.look() == null ? new byte[0] : track.look().encode();
                writeVarLong(out, look.length);
                out.write(look);
            }
            writeVarLong(out, track.lapNanos());
            writeVarLong(out, track.intervalNanos());
            writeVarLong(out, track.samples());
            double ox = track.x(0);
            double oy = track.y(0);
            double oz = track.z(0);
            out.writeDouble(ox);
            out.writeDouble(oy);
            out.writeDouble(oz);
            double[] origin = {ox, oy, oz};
            for (int axis = 0; axis < 3; axis++) {
                long previous = 0L;
                long beforePrevious = 0L;
                for (int i = 0; i < track.samples(); i++) {
                    long q = Math.round((track.positions()[i * 3 + axis] - origin[axis]) * POSITION_STEPS);
                    long predicted = i >= 2 ? 2L * previous - beforePrevious : previous;
                    writeVarLong(out, zigzag(q - predicted));
                    beforePrevious = previous;
                    previous = q;
                }
            }
            int[] last = new int[4];
            float[] rotation = new float[4];
            float[] reference = {0.0F, 0.0F, 0.0F, 1.0F};
            for (int i = 0; i < track.samples(); i++) {
                float dot = 0.0F;
                for (int c = 0; c < 4; c++) {
                    rotation[c] = track.rotation(i, c);
                    dot += rotation[c] * reference[c];
                }
                float sign = dot < 0.0F ? -1.0F : 1.0F;
                for (int c = 0; c < 4; c++) {
                    rotation[c] *= sign;
                    reference[c] = rotation[c];
                    int q = Math.round(Math.clamp(rotation[c], -1.0F, 1.0F) * ROTATION_STEPS);
                    writeVarLong(out, zigzag(q - last[c]));
                    last[c] = q;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Geisterspur nicht kodierbar", e);
        } finally {
            deflater.end();
        }
        return bytes.toByteArray();
    }

    public static GhostTrack decode(byte[] data) throws IOException {
        if (data.length == 0 || data.length > MAX_ENCODED_BYTES) {
            throw new IOException("Geisterspur mit " + data.length + " Bytes");
        }
        try (DataInputStream in = new DataInputStream(new LimitedStream(new InflaterInputStream(new ByteArrayInputStream(data)), MAX_DECODED_BYTES))) {
            if (in.readInt() != MAGIC) {
                throw new IOException("Keine Propwash-Geisterspur");
            }
            int version = in.readUnsignedByte();
            if (version != VERSION && version != VERSION_WITHOUT_LOOK) {
                throw new IOException("Unbekannte Version " + version);
            }
            int frameLength = in.readUnsignedByte();
            if (frameLength > MAX_FRAME_LENGTH) {
                throw new IOException("Klasse zu lang");
            }
            byte[] frame = new byte[frameLength];
            in.readFully(frame);
            int layout = in.readInt();
            GhostLook look = null;
            if (version >= VERSION) {
                long lookLength = readVarLong(in);
                if (lookLength < 0L || lookLength > GhostLook.MAX_BYTES) {
                    throw new IOException("Aussehen zu groß: " + lookLength);
                }
                byte[] lookData = new byte[(int) lookLength];
                in.readFully(lookData);
                look = lookLength == 0L ? null : GhostLook.decode(lookData).orElse(null);
            }
            long lapNanos = readVarLong(in);
            long interval = readVarLong(in);
            long count = readVarLong(in);
            if (interval <= 0L || interval > 1_000_000_000L || count < 2 || count > GhostTrack.MAX_SAMPLES) {
                throw new IOException("Ungültige Geisterspur: " + count + " Punkte, Abstand " + interval);
            }
            int samples = (int) count;
            double[] origin = {in.readDouble(), in.readDouble(), in.readDouble()};
            for (double value : origin) {
                if (!Double.isFinite(value)) {
                    throw new IOException("Ungültiger Ursprung");
                }
            }
            double[] positions = new double[samples * 3];
            for (int axis = 0; axis < 3; axis++) {
                long previous = 0L;
                long beforePrevious = 0L;
                for (int i = 0; i < samples; i++) {
                    long predicted = i >= 2 ? 2L * previous - beforePrevious : previous;
                    long q = predicted + unzigzag(readVarLong(in));
                    positions[i * 3 + axis] = origin[axis] + q / (double) POSITION_STEPS;
                    beforePrevious = previous;
                    previous = q;
                }
            }
            float[] rotations = new float[samples * 4];
            long[] last = new long[4];
            for (int i = 0; i < samples; i++) {
                double length = 0.0;
                for (int c = 0; c < 4; c++) {
                    last[c] += unzigzag(readVarLong(in));
                    if (Math.abs(last[c]) > ROTATION_STEPS) {
                        throw new IOException("Ungültige Lage");
                    }
                    rotations[i * 4 + c] = last[c] / (float) ROTATION_STEPS;
                    length += rotations[i * 4 + c] * rotations[i * 4 + c];
                }
                if (length < 1.0E-6) {
                    rotations[i * 4 + 3] = 1.0F;
                } else {
                    float scale = (float) (1.0 / Math.sqrt(length));
                    for (int c = 0; c < 4; c++) {
                        rotations[i * 4 + c] *= scale;
                    }
                }
            }
            return new GhostTrack(interval, positions, rotations, lapNanos, new String(frame, StandardCharsets.UTF_8), layout, look);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    static long zigzag(long value) {
        return (value << 1) ^ (value >> 63);
    }

    static long unzigzag(long value) {
        return (value >>> 1) ^ -(value & 1L);
    }

    private static void writeVarLong(DataOutputStream out, long value) throws IOException {
        long v = value;
        while ((v & ~0x7FL) != 0L) {
            out.writeByte((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        out.writeByte((int) v);
    }

    private static long readVarLong(DataInputStream in) throws IOException {
        long value = 0L;
        for (int shift = 0; shift < 64; shift += 7) {
            int b = in.readUnsignedByte();
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new IOException("VarLong zu lang");
    }

    private static final class LimitedStream extends InputStream {
        private final InputStream in;
        private long left;

        LimitedStream(InputStream in, long limit) {
            this.in = in;
            this.left = limit;
        }

        @Override
        public int read() throws IOException {
            if (this.left <= 0L) {
                throw new IOException("Geisterspur zu groß");
            }
            int b = this.in.read();
            if (b >= 0) {
                this.left--;
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (this.left <= 0L) {
                throw new IOException("Geisterspur zu groß");
            }
            int read = this.in.read(buffer, offset, (int) Math.min(length, this.left));
            if (read > 0) {
                this.left -= read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            this.in.close();
        }
    }
}
