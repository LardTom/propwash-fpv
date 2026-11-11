package de.lardtom.propwash.race;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public record EntryGhost(Info info, byte[] track) {
    public static final int MAGIC = 0x50574531;
    public static final int VERSION = 1;
    public static final int MAX_CODE = 4096;
    public static final int MAX_BYTES = GhostCodec.MAX_ENCODED_BYTES + 16 * 1024;
    private static final int MAX_INFLATED = MAX_BYTES + 64 * 1024;

    public enum Source {
        SERVER,
        RECORD_GHOST,
        PILOT;

        static Source of(int index) {
            Source[] values = values();
            return index >= 0 && index < values.length ? values[index] : SERVER;
        }
    }

    public record Info(UUID player, String name, String frame, long nanos, long dateMillis, int assist, int layout, float uptilt, String code,
                       Source source) {
        public Info {
            name = clip(name, Leaderboard.MAX_NAME);
            frame = clip(frame, Leaderboard.MAX_FRAME);
            assist = assist & LapAssist.ALL;
            code = clip(code, MAX_CODE);
            source = source == null ? Source.SERVER : source;
        }

        public GhostKey key() {
            return new GhostKey(this.player, this.frame, this.nanos, this.dateMillis);
        }

        public boolean hasUptilt() {
            return Float.isFinite(this.uptilt);
        }

        public boolean assisted() {
            return this.assist != LapAssist.NONE;
        }
    }

    public EntryGhost {
        track = track == null ? new byte[0] : track;
    }

    public GhostKey key() {
        return this.info.key();
    }

    public GhostTrack decodeTrack() throws IOException {
        return GhostCodec.decode(this.track);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    public byte[] encode() {
        if (this.track.length == 0 || this.track.length > GhostCodec.MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Geisterspur mit " + this.track.length + " Bytes");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(this.track.length + 256);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes, deflater))) {
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            Info info = this.info;
            out.writeLong(info.player().getMostSignificantBits());
            out.writeLong(info.player().getLeastSignificantBits());
            out.writeUTF(info.name());
            out.writeUTF(info.frame());
            out.writeLong(info.nanos());
            out.writeLong(info.dateMillis());
            out.writeInt(info.assist());
            out.writeInt(info.layout());
            out.writeFloat(info.uptilt());
            out.writeUTF(info.code());
            out.writeByte(info.source().ordinal());
            out.writeInt(this.track.length);
            out.write(this.track);
        } catch (IOException e) {
            throw new IllegalStateException("Geist der Bestenliste nicht kodierbar", e);
        } finally {
            deflater.end();
        }
        return bytes.toByteArray();
    }

    public static EntryGhost decode(byte[] data) throws IOException {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) {
            throw new IOException("Geist der Bestenliste mit " + (data == null ? 0 : data.length) + " Bytes");
        }
        try (DataInputStream in = new DataInputStream(new Bounded(new InflaterInputStream(new ByteArrayInputStream(data)), MAX_INFLATED))) {
            Info info = readInfo(in);
            int length = in.readInt();
            if (length <= 0 || length > GhostCodec.MAX_ENCODED_BYTES) {
                throw new IOException("Geisterspur mit " + length + " Bytes");
            }
            byte[] track = new byte[length];
            in.readFully(track);
            return new EntryGhost(info, track);
        } catch (IllegalArgumentException | EOFException e) {
            throw new IOException("Geist der Bestenliste unvollständig", e);
        }
    }

    public static Info info(byte[] data) throws IOException {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) {
            throw new IOException("Geist der Bestenliste mit " + (data == null ? 0 : data.length) + " Bytes");
        }
        try (DataInputStream in = new DataInputStream(new Bounded(new InflaterInputStream(new ByteArrayInputStream(data)), MAX_INFLATED))) {
            return readInfo(in);
        } catch (IllegalArgumentException | EOFException e) {
            throw new IOException("Geist der Bestenliste unvollständig", e);
        }
    }

    private static Info readInfo(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) {
            throw new IOException("Kein Propwash-Geist der Bestenliste");
        }
        int version = in.readUnsignedByte();
        if (version != VERSION) {
            throw new IOException("Unbekannte Version " + version);
        }
        UUID player = new UUID(in.readLong(), in.readLong());
        String name = in.readUTF();
        String frame = in.readUTF();
        long nanos = in.readLong();
        long date = in.readLong();
        int assist = in.readInt();
        int layout = in.readInt();
        float uptilt = in.readFloat();
        String code = in.readUTF();
        Source source = Source.of(in.readUnsignedByte());
        if (nanos <= 0L || name.length() > Leaderboard.MAX_NAME || frame.length() > Leaderboard.MAX_FRAME || code.length() > MAX_CODE) {
            throw new IOException("Ungültiger Kopf");
        }
        return new Info(player, name, frame, nanos, date, assist, layout, uptilt, code, source);
    }

    private static final class Bounded extends InputStream {
        private final InputStream in;
        private long left;

        Bounded(InputStream in, long limit) {
            this.in = in;
            this.left = limit;
        }

        @Override
        public int read() throws IOException {
            if (this.left <= 0L) {
                throw new IOException("Geist der Bestenliste zu groß");
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
                throw new IOException("Geist der Bestenliste zu groß");
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
