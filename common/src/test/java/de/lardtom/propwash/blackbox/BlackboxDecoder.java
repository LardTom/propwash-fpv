package de.lardtom.propwash.blackbox;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BlackboxDecoder {
    static final String MARKER = "H Product:Blackbox flight data recorder by Nicholas Sherlock\n";

    private static final int ENC_SIGNED_VB = 0;
    private static final int ENC_UNSIGNED_VB = 1;
    private static final int ENC_NEG_14BIT = 3;
    private static final int ENC_TAG8_8SVB = 6;
    private static final int ENC_TAG2_3S32 = 7;
    private static final int ENC_TAG8_4S16 = 8;
    private static final int ENC_NULL = 9;

    private static final int PRED_ZERO = 0;
    private static final int PRED_PREVIOUS = 1;
    private static final int PRED_STRAIGHT_LINE = 2;
    private static final int PRED_AVERAGE_2 = 3;
    private static final int PRED_MOTOR_0 = 5;
    private static final int PRED_INCREMENT = 6;
    private static final int PRED_HOME_COORD = 7;
    private static final int PRED_VBATREF = 9;
    private static final int PRED_LAST_MAIN_TIME = 10;
    private static final int PRED_MINMOTOR = 11;

    private static final String LOG_END_TEXT = "End of log\0";

    static final class Def {
        String[] names = new String[0];
        int[] signed = new int[0];
        int[] predictor = new int[0];
        int[] encoding = new int[0];
        private List<Chunk> chunks;

        int index(String name) {
            for (int i = 0; i < this.names.length; i++) {
                if (this.names[i].equals(name)) {
                    return i;
                }
            }
            return -1;
        }

        private boolean isSigned(int field) {
            return field < this.signed.length && this.signed[field] == 1;
        }

        private List<Chunk> chunks() {
            if (this.chunks != null) {
                return this.chunks;
            }
            List<Chunk> list = new ArrayList<>();
            int field = 0;
            while (field < this.names.length) {
                if (this.predictor[field] == PRED_INCREMENT || this.encoding[field] == ENC_NULL) {
                    field++;
                    continue;
                }
                int enc = this.encoding[field];
                int size = switch (enc) {
                    case ENC_TAG2_3S32 -> 3;
                    case ENC_TAG8_4S16 -> 4;
                    case ENC_TAG8_8SVB -> {
                        int run = 1;
                        while (run < 8 && field + run < this.names.length && this.encoding[field + run] == ENC_TAG8_8SVB) {
                            run++;
                        }
                        yield run;
                    }
                    default -> 1;
                };
                list.add(new Chunk(enc, field, Math.min(size, this.names.length - field)));
                field += size;
            }
            this.chunks = List.copyOf(list);
            return this.chunks;
        }
    }

    private record Chunk(int encoding, int first, int size) {
    }

    record Event(int type, long a, long b, int afterMainFrames) {
    }

    final Map<String, String> headers = new LinkedHashMap<>();
    final Def intra = new Def();
    final Def inter = new Def();
    final Def gps = new Def();
    final Def home = new Def();
    final Def slow = new Def();
    final List<long[]> main = new ArrayList<>();
    final List<Character> mainKinds = new ArrayList<>();
    final List<long[]> slowFrames = new ArrayList<>();
    final List<Integer> slowBefore = new ArrayList<>();
    final List<long[]> gpsFrames = new ArrayList<>();
    final List<long[]> homeFrames = new ArrayList<>();
    final List<Event> events = new ArrayList<>();
    boolean ended;
    int invalid;
    int frameIntervalI = 32;
    int pNum = 1;
    int pDenom = 1;
    long vbatRef;
    long motorLow;

    private final byte[] bytes;
    private final int limit;
    private int cursor;
    private int heldNibble = -1;
    private long[] newest;
    private long[] older;
    private long[] homePoint;

    private BlackboxDecoder(byte[] bytes, int from, int to) {
        this.bytes = bytes;
        this.cursor = from;
        this.limit = to;
    }

    static BlackboxDecoder primitives(byte[] data) {
        return new BlackboxDecoder(data, 0, data.length);
    }

    static List<BlackboxDecoder> decodeAll(byte[] data) {
        String text = new String(data, StandardCharsets.ISO_8859_1);
        List<Integer> starts = new ArrayList<>();
        for (int at = text.indexOf(MARKER); at >= 0; at = text.indexOf(MARKER, at + MARKER.length())) {
            starts.add(at);
        }
        List<BlackboxDecoder> logs = new ArrayList<>();
        for (int n = 0; n < starts.size(); n++) {
            int to = n + 1 < starts.size() ? starts.get(n + 1) : data.length;
            BlackboxDecoder log = new BlackboxDecoder(data, starts.get(n), to);
            log.readHeaders();
            log.readBody();
            logs.add(log);
        }
        return logs;
    }

    boolean atEnd() {
        return this.cursor == this.limit;
    }

    long field(long[] frame, String name) {
        int at = this.intra.index(name);
        if (at < 0) {
            throw new IllegalArgumentException(name);
        }
        return frame[at];
    }

    private int nextByte() {
        if (this.cursor >= this.limit) {
            throw new IllegalStateException("Ende der Daten");
        }
        return this.bytes[this.cursor++] & 0xFF;
    }

    long uvb() {
        long value = 0;
        for (int group = 0; group < 5; group++) {
            int octet = this.nextByte();
            value |= (long) (octet & 0x7F) << (7 * group);
            if ((octet & 0x80) == 0) {
                return value & 0xFFFFFFFFL;
            }
        }
        throw new IllegalStateException("VB zu lang");
    }

    long svb() {
        long zigzag = this.uvb();
        long magnitude = zigzag >>> 1;
        return (int) ((zigzag & 1) == 0 ? magnitude : ~magnitude);
    }

    private static long signed(long raw, int bits) {
        long shift = 64 - bits;
        return (raw << shift) >> shift;
    }

    void tag2x3s32(long[] out) {
        int head = this.nextByte();
        int mode = head >>> 6;
        if (mode == 0) {
            for (int k = 0; k < 3; k++) {
                out[k] = signed(head >>> (4 - 2 * k), 2);
            }
        } else if (mode == 1) {
            int tail = this.nextByte();
            out[0] = signed(head, 4);
            out[1] = signed(tail >>> 4, 4);
            out[2] = signed(tail, 4);
        } else if (mode == 2) {
            out[0] = signed(head, 6);
            out[1] = signed(this.nextByte(), 6);
            out[2] = signed(this.nextByte(), 6);
        } else {
            for (int k = 0; k < 3; k++) {
                int width = ((head >>> (2 * k)) & 3) + 1;
                long raw = 0;
                for (int octet = 0; octet < width; octet++) {
                    raw |= (long) this.nextByte() << (8 * octet);
                }
                out[k] = signed(raw, 8 * width);
            }
        }
    }

    private int nibble() {
        if (this.heldNibble >= 0) {
            int low = this.heldNibble;
            this.heldNibble = -1;
            return low;
        }
        int octet = this.nextByte();
        this.heldNibble = octet & 0x0F;
        return octet >>> 4;
    }

    void tag8x4s16(long[] out) {
        int sizes = this.nextByte();
        this.heldNibble = -1;
        for (int k = 0; k < 4; k++) {
            int nibbles = switch ((sizes >>> (2 * k)) & 3) {
                case 0 -> 0;
                case 1 -> 1;
                case 2 -> 2;
                default -> 4;
            };
            long raw = 0;
            for (int n = 0; n < nibbles; n++) {
                raw = raw << 4 | this.nibble();
            }
            out[k] = nibbles == 0 ? 0 : signed(raw, 4 * nibbles);
        }
        this.heldNibble = -1;
    }

    private void tag8x8svb(long[] out, int count) {
        if (count == 1) {
            out[0] = this.svb();
            return;
        }
        int present = this.nextByte();
        for (int k = 0; k < count; k++) {
            out[k] = (present & (1 << k)) == 0 ? 0 : this.svb();
        }
    }

    private void readHeaders() {
        while (this.cursor < this.limit && this.bytes[this.cursor] == 'H' && this.cursor + 1 < this.limit && this.bytes[this.cursor + 1] == ' ') {
            int newline = this.cursor;
            while (newline < this.limit && this.bytes[newline] != '\n') {
                newline++;
            }
            String line = new String(this.bytes, this.cursor + 2, newline - this.cursor - 2, StandardCharsets.ISO_8859_1);
            this.cursor = Math.min(this.limit, newline + 1);
            int split = line.indexOf(':');
            if (split < 0) {
                continue;
            }
            String key = line.substring(0, split);
            String value = line.substring(split + 1);
            this.headers.put(key, value);
            this.applyHeader(key, value);
        }
    }

    private void applyHeader(String key, String value) {
        if (key.startsWith("Field ") && key.length() > 7) {
            char kind = key.charAt(6);
            String property = key.substring(8);
            Def target = this.defFor(kind);
            switch (property) {
                case "name" -> target.names = value.split(",");
                case "signed" -> target.signed = numbers(value);
                case "predictor" -> target.predictor = numbers(value);
                case "encoding" -> target.encoding = numbers(value);
                default -> {
                }
            }
            if (kind == 'I') {
                this.inter.names = this.intra.names;
                this.inter.signed = this.intra.signed;
            }
            return;
        }
        switch (key) {
            case "I interval" -> this.frameIntervalI = Math.max(1, Integer.parseInt(value.trim()));
            case "P interval" -> {
                String[] ratio = value.split("/");
                if (ratio.length == 2) {
                    this.pNum = Integer.parseInt(ratio[0].trim());
                    this.pDenom = Integer.parseInt(ratio[1].trim());
                }
            }
            case "vbatref" -> this.vbatRef = Long.parseLong(value.trim());
            case "motorOutput" -> this.motorLow = Long.parseLong(value.split(",")[0].trim());
            default -> {
            }
        }
    }

    private Def defFor(char kind) {
        return switch (kind) {
            case 'I' -> this.intra;
            case 'P' -> this.inter;
            case 'G' -> this.gps;
            case 'H' -> this.home;
            case 'S' -> this.slow;
            default -> throw new IllegalStateException("Feldtyp " + kind);
        };
    }

    private static int[] numbers(String csv) {
        String[] items = csv.split(",");
        int[] out = new int[items.length];
        for (int k = 0; k < items.length; k++) {
            out[k] = Integer.parseInt(items[k].trim());
        }
        return out;
    }

    private void readBody() {
        while (!this.ended && this.cursor < this.limit) {
            int marker = this.nextByte();
            switch (marker) {
                case 'I', 'P' -> this.readMain(marker == 'I');
                case 'S' -> {
                    this.slowFrames.add(this.readStandalone(this.slow));
                    this.slowBefore.add(this.main.size());
                }
                case 'G' -> this.gpsFrames.add(this.readStandalone(this.gps));
                case 'H' -> {
                    this.homePoint = this.readStandalone(this.home);
                    this.homeFrames.add(this.homePoint);
                }
                case 'E' -> this.readEvent();
                default -> throw new IllegalStateException("Unbekannter Frame-Typ " + marker + " bei " + (this.cursor - 1));
            }
        }
    }

    private boolean logged(long iteration) {
        long phase = iteration % this.frameIntervalI;
        return Math.floorMod(phase + this.pNum - 1, (long) this.pDenom) < this.pNum;
    }

    private void readMain(boolean keyFrame) {
        long gap = 0;
        if (!keyFrame) {
            if (this.newest == null) {
                throw new IllegalStateException("P-Frame ohne I-Frame");
            }
            long expected = this.newest[0] + 1;
            while (!this.logged(expected)) {
                expected++;
                gap++;
            }
        }
        long[] frame = this.decode(keyFrame ? this.intra : this.inter, keyFrame ? null : this.newest, keyFrame ? null : this.older, gap);
        if (!this.main.isEmpty()) {
            long[] last = this.main.get(this.main.size() - 1);
            if (frame[0] < last[0] || frame[1] < last[1]) {
                this.invalid++;
            }
        }
        this.main.add(frame);
        this.mainKinds.add(keyFrame ? 'I' : 'P');
        this.older = keyFrame ? frame : this.newest;
        this.newest = frame;
    }

    private long[] readStandalone(Def def) {
        return this.decode(def, null, null, 0);
    }

    private long[] decode(Def def, long[] previous, long[] beforePrevious, long gap) {
        int count = def.names.length;
        long[] deltas = new long[count];
        long[] scratch = new long[8];
        for (Chunk chunk : def.chunks()) {
            switch (chunk.encoding()) {
                case ENC_SIGNED_VB -> deltas[chunk.first()] = this.svb();
                case ENC_UNSIGNED_VB -> deltas[chunk.first()] = this.uvb();
                case ENC_NEG_14BIT -> deltas[chunk.first()] = -signed(this.uvb(), 14);
                case ENC_TAG2_3S32 -> {
                    this.tag2x3s32(scratch);
                    System.arraycopy(scratch, 0, deltas, chunk.first(), chunk.size());
                }
                case ENC_TAG8_4S16 -> {
                    this.tag8x4s16(scratch);
                    System.arraycopy(scratch, 0, deltas, chunk.first(), chunk.size());
                }
                case ENC_TAG8_8SVB -> {
                    this.tag8x8svb(scratch, chunk.size());
                    System.arraycopy(scratch, 0, deltas, chunk.first(), chunk.size());
                }
                default -> throw new IllegalStateException("Kodierung " + chunk.encoding());
            }
        }
        long[] frame = new long[count];
        for (int f = 0; f < count; f++) {
            long value = deltas[f] + this.base(def, f, frame, previous, beforePrevious, gap);
            frame[f] = def.isSigned(f) ? (int) value : value & 0xFFFFFFFFL;
        }
        return frame;
    }

    private long base(Def def, int f, long[] frame, long[] previous, long[] beforePrevious, long gap) {
        int predictor = def.predictor[f];
        return switch (predictor) {
            case PRED_ZERO -> 0;
            case PRED_INCREMENT -> (previous == null ? 0 : previous[f]) + gap + 1;
            case PRED_PREVIOUS -> previous == null ? 0 : previous[f];
            case PRED_STRAIGHT_LINE -> previous == null ? 0 : previous[f] + (previous[f] - beforePrevious[f]);
            case PRED_AVERAGE_2 -> previous == null ? 0 : (previous[f] + beforePrevious[f]) / 2;
            case PRED_MOTOR_0 -> frame[this.intra.index("motor[0]")];
            case PRED_HOME_COORD -> this.homePoint == null ? 0 : this.homePoint[def.names[f].endsWith("[1]") ? 1 : 0];
            case PRED_VBATREF -> this.vbatRef;
            case PRED_LAST_MAIN_TIME -> this.main.isEmpty() ? 0 : this.main.get(this.main.size() - 1)[1];
            case PRED_MINMOTOR -> this.motorLow;
            default -> throw new IllegalStateException("Prädiktor " + predictor);
        };
    }

    private void readEvent() {
        int type = this.nextByte();
        switch (type) {
            case 0 -> this.events.add(new Event(type, this.uvb(), 0, this.main.size()));
            case 14 -> {
                long iteration = this.uvb();
                long time = this.uvb();
                this.events.add(new Event(type, iteration, time, this.main.size()));
            }
            case 255 -> {
                int length = LOG_END_TEXT.length();
                String text = new String(this.bytes, this.cursor, Math.min(length, this.limit - this.cursor), StandardCharsets.ISO_8859_1);
                if (!text.equals(LOG_END_TEXT)) {
                    throw new IllegalStateException("Log-Ende kaputt: " + text);
                }
                this.cursor += length;
                this.ended = true;
                this.events.add(new Event(type, 0, 0, this.main.size()));
            }
            default -> throw new IllegalStateException("Unbekanntes Ereignis " + type);
        }
    }
}
