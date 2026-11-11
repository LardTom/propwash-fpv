package de.lardtom.propwash.race.client;

import de.lardtom.propwash.network.client.ClientNet;
import de.lardtom.propwash.race.GhostCodec;
import de.lardtom.propwash.race.GhostTrack;
import de.lardtom.propwash.race.RacePayloads;

import java.util.ArrayDeque;
import java.util.Arrays;

public final class GhostUploader {
    public static final GhostUploader INSTANCE = new GhostUploader();
    public static final int MAX_JOBS = 2;
    public static final int RACING_TICKS_PER_CHUNK = 4;

    private final ArrayDeque<Job> jobs = new ArrayDeque<>();
    private int ticks;
    private int sentChunks;
    private int sentBytes;
    private int finished;
    private int declined;

    private static final class Job {
        final int token;
        final byte[] data;
        final int parts;
        int next;

        Job(int token, byte[] data) {
            this.token = token;
            this.data = data;
            this.parts = Math.max(1, (data.length + RacePayloads.GhostUploadC2S.CHUNK - 1) / RacePayloads.GhostUploadC2S.CHUNK);
        }

        RacePayloads.GhostUploadC2S chunk() {
            int from = this.next * RacePayloads.GhostUploadC2S.CHUNK;
            int to = Math.min(this.data.length, from + RacePayloads.GhostUploadC2S.CHUNK);
            return new RacePayloads.GhostUploadC2S(this.token, this.next++, this.parts, this.data.length, Arrays.copyOfRange(this.data, from, to));
        }
    }

    private GhostUploader() {
    }

    public void request(RacePayloads.GhostUploadS2C request) {
        GhostTrack track = ClientRaceTracker.INSTANCE.recorder().keptLap(request.courseId(), request.lapNanos());
        byte[] data = track == null ? new byte[0] : GhostCodec.encode(track);
        if (data.length == 0 || data.length > RacePayloads.GhostUploadC2S.MAX_BYTES || this.jobs.size() >= MAX_JOBS) {
            this.declined++;
            ClientNet.sendToServer(RacePayloads.GhostUploadC2S.decline(request.token()));
            return;
        }
        this.jobs.addLast(new Job(request.token(), data));
    }

    public void tick() {
        this.ticks++;
        Job job = this.jobs.peekFirst();
        if (job == null) {
            return;
        }
        if (ClientRaceTracker.INSTANCE.tracker().racing() && this.ticks % RACING_TICKS_PER_CHUNK != 0) {
            return;
        }
        RacePayloads.GhostUploadC2S chunk = job.chunk();
        if (!ClientNet.sendToServer(chunk)) {
            this.jobs.clear();
            return;
        }
        this.sentChunks++;
        this.sentBytes += chunk.data().length;
        if (job.next >= job.parts) {
            this.jobs.pollFirst();
            this.finished++;
        }
    }

    public void clear() {
        this.jobs.clear();
    }

    public int open() {
        return this.jobs.size();
    }

    public int sentChunks() {
        return this.sentChunks;
    }

    public int sentBytes() {
        return this.sentBytes;
    }

    public int finished() {
        return this.finished;
    }

    public int declined() {
        return this.declined;
    }
}
