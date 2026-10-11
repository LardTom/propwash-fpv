package de.lardtom.propwash.video.record.mp4;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public final class Mp4Writer implements Closeable {
    public static final int MOVIE_TIMESCALE = 1000;
    private static final long EPOCH_OFFSET = 2_082_844_800L;
    private static final int MDAT_HEADER = 16;
    private static final int VIDEO_TRACK = 1;
    private static final int AUDIO_TRACK = 2;
    private static final int OBJECT_TYPE_MPEG4_AUDIO = 0x40;
    private static final int STREAM_TYPE_AUDIO = 0x05;

    private final Path path;
    private final FileChannel channel;
    private final int width;
    private final int height;
    private final int timescale;
    private byte[] sps;
    private byte[] pps;
    private final String compressor;
    private final long created;
    private final long mdatStart;
    private final Track video = new Track();
    private Track audio;
    private int audioRate;
    private int audioChannels;
    private byte[] audioConfig = new byte[0];
    private long audioBitrate;
    private long audioMediaStart;
    private long audioPresented = -1L;
    private long position;
    private boolean finished;
    private boolean closed;

    public Mp4Writer(Path path, int width, int height, int timescale, byte[] sps, byte[] pps, String compressor) throws IOException {
        this.path = path;
        this.width = width;
        this.height = height;
        this.timescale = timescale;
        this.sps = sps.clone();
        this.pps = pps.clone();
        this.compressor = compressor;
        this.created = System.currentTimeMillis() / 1000L + EPOCH_OFFSET;
        this.channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        byte[] ftyp = new BoxBuilder().open("ftyp").fourCc("isom").u32(0x200).fourCc("isom").fourCc("iso2").fourCc("avc1").fourCc("mp41")
                .close().toByteArray();
        this.write(ftyp);
        this.mdatStart = this.position;
        byte[] mdatHeader = new BoxBuilder().u32(1).fourCc("mdat").u64(MDAT_HEADER).toByteArray();
        this.write(mdatHeader);
    }

    public Path path() {
        return this.path;
    }

    public int samples() {
        return this.video.samples;
    }

    public long durationTicks() {
        return this.video.durationTicks;
    }

    public int timescale() {
        return this.timescale;
    }

    public long bytesWritten() {
        return this.position;
    }

    public boolean hasAudio() {
        return this.audio != null;
    }

    public int audioSamples() {
        return this.audio == null ? 0 : this.audio.samples;
    }

    public long audioDurationTicks() {
        return this.audio == null ? 0L : this.audio.durationTicks;
    }

    public void addAudioTrack(int sampleRate, int channels, byte[] decoderConfig, long averageBitrate) {
        if (this.audio != null || this.finished) {
            throw new IllegalStateException("audio track already added or file finished");
        }
        if (sampleRate <= 0 || channels <= 0 || decoderConfig.length == 0) {
            throw new IllegalArgumentException("bad audio track " + sampleRate + " Hz, " + channels + " ch");
        }
        this.audio = new Track();
        this.audioRate = sampleRate;
        this.audioChannels = channels;
        this.audioConfig = decoderConfig.clone();
        this.audioBitrate = averageBitrate;
    }

    public boolean hasParameterSets() {
        return this.sps.length >= 4 && this.pps.length >= 1;
    }

    public void parameterSets(byte[] sps, byte[] pps) {
        if (this.finished) {
            throw new IllegalStateException("finished");
        }
        if (sps.length < 4 || pps.length < 1) {
            throw new IllegalArgumentException("bad parameter sets " + sps.length + "/" + pps.length);
        }
        this.sps = sps.clone();
        this.pps = pps.clone();
    }

    public void setAudioPresentation(long mediaStart, long presentedSamples) {
        if (mediaStart < 0L || presentedSamples < 0L) {
            throw new IllegalArgumentException("presentation " + mediaStart + "/" + presentedSamples);
        }
        this.audioMediaStart = mediaStart;
        this.audioPresented = presentedSamples;
    }

    public void writeSample(byte[] data, int duration, boolean sync) throws IOException {
        this.append(this.video, data, duration, sync);
    }

    public void writeAudioSample(byte[] data, int duration) throws IOException {
        if (this.audio == null) {
            throw new IllegalStateException("no audio track");
        }
        this.append(this.audio, data, duration, true);
    }

    private void append(Track track, byte[] data, int duration, boolean sync) throws IOException {
        if (this.finished) {
            throw new IllegalStateException("finished");
        }
        if (duration <= 0) {
            throw new IllegalArgumentException("duration " + duration);
        }
        track.add(this.position, data.length, duration, sync);
        this.write(data);
    }

    public void finish() throws IOException {
        if (this.finished) {
            return;
        }
        if (!this.hasParameterSets()) {
            throw new IOException("no SPS/PPS");
        }
        this.finished = true;
        long mdatSize = this.position - this.mdatStart;
        ByteBuffer size = ByteBuffer.allocate(8).putLong(0, mdatSize);
        this.channel.write(size, this.mdatStart + 8);
        this.write(this.moov());
        this.channel.force(true);
        this.channel.close();
        this.closed = true;
    }

    public void abort() {
        this.finished = true;
        try {
            this.close();
        } catch (IOException ignored) {
        }
        try {
            Files.deleteIfExists(this.path);
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() throws IOException {
        if (!this.closed) {
            this.closed = true;
            this.channel.close();
        }
    }

    private void write(byte[] data) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        while (buffer.hasRemaining()) {
            this.position += this.channel.write(buffer, this.position);
        }
    }

    private boolean audioPresent() {
        return this.audio != null && this.audio.samples > 0;
    }

    private long audioPresentedSamples() {
        if (this.audio == null) {
            return 0L;
        }
        long available = Math.max(0L, this.audio.durationTicks - this.audioMediaStart);
        return this.audioPresented < 0L ? available : Math.min(available, this.audioPresented);
    }

    private long audioMovieDuration() {
        return this.audioPresentedSamples() * MOVIE_TIMESCALE / Math.max(1, this.audioRate);
    }

    private byte[] moov() {
        long videoDuration = this.video.durationTicks * MOVIE_TIMESCALE / Math.max(1, this.timescale);
        boolean withAudio = this.audioPresent();
        long movieDuration = withAudio ? Math.max(videoDuration, this.audioMovieDuration()) : videoDuration;
        long created = this.created <= 0xFFFFFFFFL ? this.created : 0L;
        BoxBuilder b = new BoxBuilder();
        b.open("moov");
        b.full("mvhd", 0, 0).u32(created).u32(created).u32(MOVIE_TIMESCALE).u32(movieDuration)
                .u32(0x00010000).u16(0x0100).zeros(10).matrix().zeros(24).u32(withAudio ? AUDIO_TRACK + 1 : VIDEO_TRACK + 1).close();
        this.videoTrak(b, created, videoDuration);
        if (withAudio) {
            this.audioTrak(b, created);
        }
        b.close();
        return b.toByteArray();
    }

    private void videoTrak(BoxBuilder b, long created, long movieDuration) {
        b.open("trak");
        b.full("tkhd", 0, 3).u32(created).u32(created).u32(VIDEO_TRACK).u32(0).u32(movieDuration).zeros(8)
                .u16(0).u16(0).u16(0).u16(0).matrix().u32((long) this.width << 16).u32((long) this.height << 16).close();
        b.open("mdia");
        b.full("mdhd", 0, 0).u32(created).u32(created).u32(this.timescale).u32(this.video.durationTicks).u16(0x55C4).u16(0).close();
        b.full("hdlr", 0, 0).u32(0).fourCc("vide").zeros(12).bytes("Propwash Video\0".getBytes(StandardCharsets.US_ASCII)).close();
        b.open("minf");
        b.full("vmhd", 0, 1).u16(0).u16(0).u16(0).u16(0).close();
        dinf(b);
        b.open("stbl");
        this.videoStsd(b);
        tables(b, this.video, true);
        b.close();
        b.close();
        b.close();
        b.close();
    }

    private void audioTrak(BoxBuilder b, long created) {
        Track track = this.audio;
        long presented = this.audioMovieDuration();
        b.open("trak");
        b.full("tkhd", 0, 3).u32(created).u32(created).u32(AUDIO_TRACK).u32(0).u32(presented).zeros(8)
                .u16(0).u16(0).u16(0x0100).u16(0).matrix().u32(0).u32(0).close();
        b.open("edts");
        b.full("elst", 0, 0).u32(1).u32(presented).u32(this.audioMediaStart).u16(1).u16(0).close();
        b.close();
        b.open("mdia");
        b.full("mdhd", 0, 0).u32(created).u32(created).u32(this.audioRate).u32(track.durationTicks).u16(0x55C4).u16(0).close();
        b.full("hdlr", 0, 0).u32(0).fourCc("soun").zeros(12).bytes("Propwash Audio\0".getBytes(StandardCharsets.US_ASCII)).close();
        b.open("minf");
        b.full("smhd", 0, 0).u16(0).u16(0).close();
        dinf(b);
        b.open("stbl");
        this.audioStsd(b, track);
        tables(b, track, false);
        b.full("sgpd", 1, 0).fourCc("roll").u32(2).u32(1).u16(0xFFFF).close();
        b.full("sbgp", 0, 0).fourCc("roll").u32(1).u32(track.samples).u32(1).close();
        b.close();
        b.close();
        b.close();
        b.close();
    }

    private static void dinf(BoxBuilder b) {
        b.open("dinf");
        b.full("dref", 0, 0).u32(1);
        b.full("url ", 0, 1).close();
        b.close();
        b.close();
    }

    private static void tables(BoxBuilder b, Track track, boolean syncTable) {
        stts(b, track);
        if (syncTable && track.syncCount < track.samples) {
            b.full("stss", 0, 0).u32(track.syncCount);
            for (int i = 0; i < track.syncCount; i++) {
                b.u32(track.syncs[i]);
            }
            b.close();
        }
        b.full("stsc", 0, 0).u32(1).u32(1).u32(1).u32(1).close();
        b.full("stsz", 0, 0).u32(0).u32(track.samples);
        for (int i = 0; i < track.samples; i++) {
            b.u32(track.sizes[i]);
        }
        b.close();
        boolean wideOffsets = track.samples > 0 && track.offsets[track.samples - 1] > 0xFFFFFFFFL;
        if (wideOffsets) {
            b.full("co64", 0, 0).u32(track.samples);
            for (int i = 0; i < track.samples; i++) {
                b.u64(track.offsets[i]);
            }
        } else {
            b.full("stco", 0, 0).u32(track.samples);
            for (int i = 0; i < track.samples; i++) {
                b.u32(track.offsets[i]);
            }
        }
        b.close();
    }

    private void videoStsd(BoxBuilder b) {
        b.full("stsd", 0, 0).u32(1);
        b.open("avc1").zeros(6).u16(1).u16(0).u16(0).zeros(12).u16(this.width).u16(this.height)
                .u32(0x00480000).u32(0x00480000).u32(0).u16(1);
        byte[] name = this.compressor.getBytes(StandardCharsets.US_ASCII);
        int nameLength = Math.min(31, name.length);
        b.u8(nameLength).bytes(Arrays.copyOf(name, nameLength)).zeros(31 - nameLength);
        b.u16(0x0018).u16(0xFFFF);
        b.open("avcC").u8(1).u8(this.sps[1] & 0xFF).u8(this.sps[2] & 0xFF).u8(this.sps[3] & 0xFF).u8(0xFF).u8(0xE1)
                .u16(this.sps.length).bytes(this.sps).u8(1).u16(this.pps.length).bytes(this.pps);
        int profile = this.sps[1] & 0xFF;
        if (profile == 100 || profile == 110 || profile == 122 || profile == 144) {
            b.u8(0xFD).u8(0xF8).u8(0xF8).u8(0);
        }
        b.close();
        b.open("colr").fourCc("nclx").u16(1).u16(1).u16(1).u8(0).close();
        b.open("pasp").u32(1).u32(1).close();
        b.close();
        b.close();
    }

    private void audioStsd(BoxBuilder b, Track track) {
        b.full("stsd", 0, 0).u32(1);
        b.open("mp4a").zeros(6).u16(1).zeros(8).u16(this.audioChannels).u16(16).u16(0).u16(0).u32((long) this.audioRate << 16);
        int largest = 0;
        for (int i = 0; i < track.samples; i++) {
            largest = Math.max(largest, track.sizes[i]);
        }
        long seconds = Math.max(1L, track.durationTicks / Math.max(1, this.audioRate));
        long average = track.bytes * 8L / seconds;
        long peak = Math.max(average, this.audioBitrate);
        int specificLength = this.audioConfig.length;
        int decoderConfigLength = 13 + 5 + specificLength;
        int esLength = 3 + 5 + decoderConfigLength + 5 + 1;
        b.full("esds", 0, 0);
        descriptor(b, 0x03, esLength);
        b.u16(AUDIO_TRACK).u8(0);
        descriptor(b, 0x04, decoderConfigLength);
        b.u8(OBJECT_TYPE_MPEG4_AUDIO).u8(STREAM_TYPE_AUDIO << 2 | 1).u8(largest >>> 16).u16(largest & 0xFFFF).u32(peak).u32(average);
        descriptor(b, 0x05, specificLength);
        b.bytes(this.audioConfig);
        descriptor(b, 0x06, 1);
        b.u8(2);
        b.close();
        b.close();
        b.close();
    }

    private static void descriptor(BoxBuilder b, int tag, int length) {
        b.u8(tag).u8(0x80 | (length >>> 21) & 0x7F).u8(0x80 | (length >>> 14) & 0x7F).u8(0x80 | (length >>> 7) & 0x7F).u8(length & 0x7F);
    }

    private static void stts(BoxBuilder b, Track track) {
        int entries = 0;
        for (int i = 0; i < track.samples; i++) {
            if (i == 0 || track.durations[i] != track.durations[i - 1]) {
                entries++;
            }
        }
        b.full("stts", 0, 0).u32(entries);
        int i = 0;
        while (i < track.samples) {
            int j = i;
            while (j < track.samples && track.durations[j] == track.durations[i]) {
                j++;
            }
            b.u32(j - i).u32(track.durations[i]);
            i = j;
        }
        b.close();
    }

    private static final class Track {
        long[] offsets = new long[1024];
        int[] sizes = new int[1024];
        int[] durations = new int[1024];
        int[] syncs = new int[64];
        int samples;
        int syncCount;
        long durationTicks;
        long bytes;

        void add(long offset, int size, int duration, boolean sync) {
            if (this.samples == this.sizes.length) {
                int grown = this.sizes.length * 2;
                this.offsets = Arrays.copyOf(this.offsets, grown);
                this.sizes = Arrays.copyOf(this.sizes, grown);
                this.durations = Arrays.copyOf(this.durations, grown);
            }
            this.offsets[this.samples] = offset;
            this.sizes[this.samples] = size;
            this.durations[this.samples] = duration;
            this.samples++;
            this.durationTicks += duration;
            this.bytes += size;
            if (sync) {
                if (this.syncCount == this.syncs.length) {
                    this.syncs = Arrays.copyOf(this.syncs, this.syncs.length * 2);
                }
                this.syncs[this.syncCount++] = this.samples;
            }
        }
    }
}
