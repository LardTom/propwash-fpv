package de.lardtom.propwash.video.record.mp4;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class Mp4Reader {
    private static final int MAX_META = 64 * 1024 * 1024;
    private static final int TAG_ES = 0x03;
    private static final int TAG_DECODER_CONFIG = 0x04;
    private static final int TAG_DECODER_SPECIFIC = 0x05;

    private Mp4Reader() {
    }

    public static Mp4Info read(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = channel.size();
            List<String> topLevel = new ArrayList<>();
            long mdatStart = -1L;
            long mdatEnd = -1L;
            ByteBuffer moov = null;
            long offset = 0L;
            while (offset < size) {
                ByteBuffer header = ByteBuffer.allocate(16);
                readFully(channel, header, offset, (int) Math.min(16, size - offset));
                if (header.limit() < 8) {
                    throw new IOException("truncated box header at " + offset);
                }
                long boxSize = header.getInt(0) & 0xFFFFFFFFL;
                String type = fourCc(header, 4);
                int headerSize = 8;
                if (boxSize == 1L) {
                    if (header.limit() < 16) {
                        throw new IOException("truncated large box at " + offset);
                    }
                    boxSize = header.getLong(8);
                    headerSize = 16;
                } else if (boxSize == 0L) {
                    boxSize = size - offset;
                }
                if (boxSize < headerSize || offset + boxSize > size) {
                    throw new IOException("box " + type + " at " + offset + " has invalid size " + boxSize);
                }
                topLevel.add(type);
                if (type.equals("mdat")) {
                    mdatStart = offset + headerSize;
                    mdatEnd = offset + boxSize;
                } else if (type.equals("moov")) {
                    if (boxSize > MAX_META) {
                        throw new IOException("moov too large");
                    }
                    moov = ByteBuffer.allocate((int) (boxSize - headerSize));
                    readFully(channel, moov, offset + headerSize, moov.capacity());
                }
                offset += boxSize;
            }
            if (moov == null) {
                throw new IOException("no moov box");
            }
            if (mdatStart < 0L) {
                throw new IOException("no mdat box");
            }
            Parsed parsed = new Parsed();
            parseChildren(moov, 0, moov.limit(), parsed, "moov");
            Track video = parsed.track("vide");
            if (video == null) {
                throw new IOException("no video track");
            }
            checkTables(video, mdatStart, mdatEnd);
            int nalErrors = 0;
            for (int i = 0; i < video.sizes.length; i++) {
                if (i < 8 || i == video.sizes.length - 1) {
                    nalErrors += checkNals(channel, video.offsets[i], video.sizes[i]);
                }
            }
            if (nalErrors > 0) {
                throw new IOException("broken NAL length prefixes: " + nalErrors);
            }
            Track audio = parsed.track("soun");
            if (audio != null) {
                checkTables(audio, mdatStart, mdatEnd);
            }
            return new Mp4Info(List.copyOf(topLevel), video.handler, video.codec, video.width, video.height, parsed.movieTimescale,
                    parsed.movieDuration, video.mediaTimescale, video.mediaDuration, video.sttsTotal, video.sizes.length,
                    video.syncSamples == null ? video.sizes.length : video.syncSamples.length,
                    video.syncSamples == null || (video.syncSamples.length > 0 && video.syncSamples[0] == 1),
                    video.profile, video.level, video.sps == null ? new byte[0] : video.sps,
                    audio == null ? "" : audio.codec, audio == null ? 0 : audio.mediaTimescale, audio == null ? 0 : audio.channels,
                    audio == null ? 0L : audio.mediaDuration, audio == null ? 0 : audio.sizes.length, audio == null ? 0 : audio.objectType,
                    audio == null ? 0L : audio.editMediaTime, audio == null ? 0L : audio.editDuration,
                    audio == null ? new byte[0] : audio.decoderConfig, parsed.tracks.size());
        }
    }

    private static void checkTables(Track track, long mdatStart, long mdatEnd) throws IOException {
        if (track.sizes == null || track.offsets == null) {
            throw new IOException("missing sample tables (" + track.handler + ")");
        }
        if (track.sizes.length != track.offsets.length) {
            throw new IOException("stsz/stco mismatch: " + track.sizes.length + " vs " + track.offsets.length);
        }
        if (track.sttsCount != track.sizes.length) {
            throw new IOException("stts count " + track.sttsCount + " vs " + track.sizes.length + " samples");
        }
        for (int i = 0; i < track.sizes.length; i++) {
            long start = track.offsets[i];
            long end = start + track.sizes[i];
            if (start < mdatStart || end > mdatEnd) {
                throw new IOException(track.handler + " sample " + i + " outside mdat");
            }
        }
    }

    private static int checkNals(FileChannel channel, long start, int size) throws IOException {
        ByteBuffer sample = ByteBuffer.allocate(size);
        readFully(channel, sample, start, size);
        int position = 0;
        while (position < size) {
            if (position + 4 > size) {
                return 1;
            }
            int length = sample.getInt(position);
            if (length <= 0 || position + 4 + length > size) {
                return 1;
            }
            int header = sample.get(position + 4) & 0xFF;
            int nalType = header & 31;
            if ((header & 0x80) != 0 || nalType < 1 || nalType > 12) {
                return 1;
            }
            position += 4 + length;
        }
        return 0;
    }

    private static void parseChildren(ByteBuffer buffer, int start, int end, Parsed parsed, String parent) throws IOException {
        int offset = start;
        while (offset < end) {
            if (offset + 8 > end) {
                throw new IOException("truncated child of " + parent);
            }
            long size = buffer.getInt(offset) & 0xFFFFFFFFL;
            String type = fourCc(buffer, offset + 4);
            int headerSize = 8;
            if (size == 1L) {
                size = buffer.getLong(offset + 8);
                headerSize = 16;
            }
            if (size < headerSize || offset + size > end) {
                throw new IOException("box " + parent + "/" + type + " has invalid size " + size);
            }
            int bodyStart = offset + headerSize;
            int bodyEnd = (int) (offset + size);
            Track track = parsed.current;
            switch (type) {
                case "trak" -> {
                    Track opened = new Track();
                    parsed.tracks.add(opened);
                    parsed.current = opened;
                    parseChildren(buffer, bodyStart, bodyEnd, parsed, type);
                    parsed.current = null;
                }
                case "mdia", "minf", "stbl", "dinf", "edts", "udta" -> parseChildren(buffer, bodyStart, bodyEnd, parsed, type);
                case "mvhd" -> {
                    int version = buffer.get(bodyStart) & 0xFF;
                    if (version == 1) {
                        parsed.movieTimescale = buffer.getInt(bodyStart + 20);
                        parsed.movieDuration = buffer.getLong(bodyStart + 24);
                    } else {
                        parsed.movieTimescale = buffer.getInt(bodyStart + 12);
                        parsed.movieDuration = buffer.getInt(bodyStart + 16) & 0xFFFFFFFFL;
                    }
                }
                default -> {
                    if (track != null) {
                        parseTrackBox(buffer, type, bodyStart, bodyEnd, track);
                    }
                }
            }
            offset = bodyEnd;
        }
    }

    private static void parseTrackBox(ByteBuffer buffer, String type, int bodyStart, int bodyEnd, Track track) throws IOException {
        switch (type) {
            case "tkhd" -> {
                int version = buffer.get(bodyStart) & 0xFF;
                int base = bodyStart + (version == 1 ? 88 : 76);
                track.width = buffer.getInt(base) >>> 16;
                track.height = buffer.getInt(base + 4) >>> 16;
            }
            case "mdhd" -> {
                int version = buffer.get(bodyStart) & 0xFF;
                if (version == 1) {
                    track.mediaTimescale = buffer.getInt(bodyStart + 20);
                    track.mediaDuration = buffer.getLong(bodyStart + 24);
                } else {
                    track.mediaTimescale = buffer.getInt(bodyStart + 12);
                    track.mediaDuration = buffer.getInt(bodyStart + 16) & 0xFFFFFFFFL;
                }
            }
            case "elst" -> {
                int version = buffer.get(bodyStart) & 0xFF;
                int entries = buffer.getInt(bodyStart + 4);
                if (entries > 0) {
                    if (version == 1) {
                        track.editDuration = buffer.getLong(bodyStart + 8);
                        track.editMediaTime = buffer.getLong(bodyStart + 16);
                    } else {
                        track.editDuration = buffer.getInt(bodyStart + 8) & 0xFFFFFFFFL;
                        track.editMediaTime = buffer.getInt(bodyStart + 12);
                    }
                }
            }
            case "hdlr" -> track.handler = fourCc(buffer, bodyStart + 8);
            case "stsd" -> parseStsd(buffer, bodyStart, bodyEnd, track);
            case "stts" -> {
                int entries = buffer.getInt(bodyStart + 4);
                long total = 0L;
                long count = 0L;
                for (int i = 0; i < entries; i++) {
                    long samples = buffer.getInt(bodyStart + 8 + i * 8) & 0xFFFFFFFFL;
                    long delta = buffer.getInt(bodyStart + 12 + i * 8) & 0xFFFFFFFFL;
                    total += samples * delta;
                    count += samples;
                }
                track.sttsTotal = total;
                track.sttsCount = count;
            }
            case "stss" -> {
                int entries = buffer.getInt(bodyStart + 4);
                track.syncSamples = new int[entries];
                for (int i = 0; i < entries; i++) {
                    track.syncSamples[i] = buffer.getInt(bodyStart + 8 + i * 4);
                }
            }
            case "stsz" -> {
                int fixed = buffer.getInt(bodyStart + 4);
                int count = buffer.getInt(bodyStart + 8);
                track.sizes = new int[count];
                for (int i = 0; i < count; i++) {
                    track.sizes[i] = fixed != 0 ? fixed : buffer.getInt(bodyStart + 12 + i * 4);
                }
            }
            case "stco" -> {
                int count = buffer.getInt(bodyStart + 4);
                track.offsets = new long[count];
                for (int i = 0; i < count; i++) {
                    track.offsets[i] = buffer.getInt(bodyStart + 8 + i * 4) & 0xFFFFFFFFL;
                }
            }
            case "co64" -> {
                int count = buffer.getInt(bodyStart + 4);
                track.offsets = new long[count];
                for (int i = 0; i < count; i++) {
                    track.offsets[i] = buffer.getLong(bodyStart + 8 + i * 8);
                }
            }
            case "stsc" -> {
                int entries = buffer.getInt(bodyStart + 4);
                for (int i = 0; i < entries; i++) {
                    if (buffer.getInt(bodyStart + 12 + i * 12) != 1) {
                        throw new IOException("only one sample per chunk is supported");
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void parseStsd(ByteBuffer buffer, int start, int end, Track track) throws IOException {
        int entries = buffer.getInt(start + 4);
        if (entries < 1) {
            throw new IOException("empty stsd");
        }
        int entry = start + 8;
        int size = buffer.getInt(entry);
        track.codec = fourCc(buffer, entry + 4);
        if (entry + size > end) {
            return;
        }
        if (track.codec.equals("mp4a")) {
            parseAudioEntry(buffer, entry, entry + size, track);
            return;
        }
        if (!track.codec.equals("avc1")) {
            return;
        }
        int child = entry + 8 + 78;
        int entryEnd = entry + size;
        while (child + 8 <= entryEnd) {
            int childSize = buffer.getInt(child);
            String type = fourCc(buffer, child + 4);
            if (childSize < 8 || child + childSize > entryEnd) {
                throw new IOException("bad sample entry child " + type);
            }
            if (type.equals("avcC")) {
                int body = child + 8;
                track.profile = buffer.get(body + 1) & 0xFF;
                track.level = buffer.get(body + 3) & 0xFF;
                int spsLength = buffer.getShort(body + 6) & 0xFFFF;
                track.sps = new byte[spsLength];
                buffer.get(body + 8, track.sps);
            }
            child += childSize;
        }
    }

    private static void parseAudioEntry(ByteBuffer buffer, int entry, int entryEnd, Track track) throws IOException {
        int fields = entry + 8 + 8;
        track.channels = buffer.getShort(fields + 8) & 0xFFFF;
        int child = fields + 20;
        while (child + 8 <= entryEnd) {
            int childSize = buffer.getInt(child);
            String type = fourCc(buffer, child + 4);
            if (childSize < 8 || child + childSize > entryEnd) {
                throw new IOException("bad audio entry child " + type);
            }
            if (type.equals("esds")) {
                parseDescriptors(buffer, child + 12, child + childSize, track);
            }
            child += childSize;
        }
    }

    private static void parseDescriptors(ByteBuffer buffer, int start, int end, Track track) throws IOException {
        int at = start;
        while (at + 2 <= end) {
            int tag = buffer.get(at++) & 0xFF;
            int length = 0;
            for (int i = 0; i < 4; i++) {
                if (at >= end) {
                    throw new IOException("truncated descriptor");
                }
                int b = buffer.get(at++) & 0xFF;
                length = length << 7 | b & 0x7F;
                if ((b & 0x80) == 0) {
                    break;
                }
            }
            int bodyEnd = at + length;
            if (bodyEnd > end) {
                throw new IOException("descriptor " + tag + " too long");
            }
            switch (tag) {
                case TAG_ES -> {
                    int flags = buffer.get(at + 2) & 0xFF;
                    int inner = at + 3;
                    if ((flags & 0x80) != 0) {
                        inner += 2;
                    }
                    if ((flags & 0x40) != 0) {
                        inner += 1 + (buffer.get(inner) & 0xFF);
                    }
                    if ((flags & 0x20) != 0) {
                        inner += 2;
                    }
                    parseDescriptors(buffer, inner, bodyEnd, track);
                }
                case TAG_DECODER_CONFIG -> {
                    track.objectTypeIndication = buffer.get(at) & 0xFF;
                    parseDescriptors(buffer, at + 13, bodyEnd, track);
                }
                case TAG_DECODER_SPECIFIC -> {
                    track.decoderConfig = new byte[length];
                    buffer.get(at, track.decoderConfig);
                    if (length > 0) {
                        track.objectType = (track.decoderConfig[0] & 0xFF) >>> 3;
                    }
                }
                default -> {
                }
            }
            at = bodyEnd;
        }
    }

    private static String fourCc(ByteBuffer buffer, int offset) {
        byte[] bytes = new byte[4];
        buffer.get(offset, bytes);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer, long position, int length) throws IOException {
        buffer.clear();
        buffer.limit(length);
        long at = position;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, at);
            if (read < 0) {
                break;
            }
            at += read;
        }
        buffer.flip();
    }

    private static final class Parsed {
        final List<Track> tracks = new ArrayList<>();
        Track current;
        int movieTimescale;
        long movieDuration;

        Track track(String handler) {
            for (Track track : this.tracks) {
                if (track.handler.equals(handler)) {
                    return track;
                }
            }
            return null;
        }
    }

    private static final class Track {
        String handler = "";
        String codec = "";
        int width;
        int height;
        int mediaTimescale;
        long mediaDuration;
        long sttsTotal;
        long sttsCount;
        int[] sizes;
        long[] offsets;
        int[] syncSamples;
        int profile;
        int level;
        byte[] sps;
        int channels;
        int objectTypeIndication;
        int objectType;
        byte[] decoderConfig = new byte[0];
        long editMediaTime;
        long editDuration;
    }
}
