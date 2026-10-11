package de.lardtom.propwash.blackbox;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.LockSupport;

public final class BlackboxWriter implements Runnable {
    static final int FLUSH_BYTES = 32 * 1024;
    static final long IDLE_PARK_NANOS = 50_000_000L;
    static final long FLUSH_INTERVAL_NANOS = 250_000_000L;

    public record Start(int logId, BlackboxSetup setup, String fileName, long maxBytes) {
    }

    public record Saved(Path path, long bytes, long frames, boolean full) {
    }

    private final BlackboxRing ring;
    private final BlackboxLibrary library;
    private final BlackboxBuffer buffer = new BlackboxBuffer(FLUSH_BYTES * 2);
    private final BlackboxEncoder encoder = new BlackboxEncoder(this.buffer);
    private OutputStream out;
    private Path path;
    private int logId = -1;
    private long written;
    private long maxBytes;
    private long othersBytes;
    private long lastFlushNanos;
    private volatile Thread thread;
    private volatile boolean running = true;
    private volatile int closedLogId = -1;
    private volatile String currentFile;
    private volatile long currentBytes;
    private volatile Saved lastSaved;
    private volatile String lastError;
    private volatile int pruned;

    public BlackboxWriter(BlackboxRing ring, BlackboxLibrary library) {
        this.ring = ring;
        this.library = library;
    }

    public BlackboxLibrary library() {
        return this.library;
    }

    public synchronized void ensureStarted() {
        if (this.thread == null || !this.thread.isAlive()) {
            Thread t = new Thread(this, "Propwash Blackbox");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            this.thread = t;
            t.start();
        }
    }

    public void wake() {
        Thread t = this.thread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    public boolean closed(int id) {
        return this.closedLogId == id;
    }

    public String currentFile() {
        return this.currentFile;
    }

    public long currentBytes() {
        return this.currentBytes;
    }

    public Saved lastSaved() {
        return this.lastSaved;
    }

    public String lastError() {
        return this.lastError;
    }

    public int pruned() {
        return this.pruned;
    }

    @Override
    public void run() {
        while (this.running) {
            boolean worked = this.drain(4096);
            if (!worked) {
                this.flushIfDue(false);
                LockSupport.parkNanos(this, IDLE_PARK_NANOS);
            }
        }
    }

    public boolean drain(int max) {
        BlackboxRing r = this.ring;
        int done = 0;
        while (done < max && !r.isEmpty()) {
            int kind = r.peekKind();
            switch (kind) {
                case BlackboxRing.KIND_START -> this.open((Start) r.peekPayload());
                case BlackboxRing.KIND_STOP -> this.close((Integer) r.peekPayload(), false);
                case BlackboxRing.KIND_MAIN, BlackboxRing.KIND_RESUME -> {
                    if (this.out != null) {
                        this.encoder.frame(r.data(), r.peekOffset(), kind == BlackboxRing.KIND_RESUME);
                    }
                }
                default -> {
                }
            }
            r.release();
            done++;
            if (this.out != null && this.buffer.size() >= FLUSH_BYTES) {
                this.flush(true);
            }
        }
        return done > 0;
    }

    public void drainAll() {
        while (this.drain(Integer.MAX_VALUE)) {
            Thread.onSpinWait();
        }
        this.flushIfDue(true);
    }

    public boolean awaitIdle(long timeoutNanos) {
        long end = System.nanoTime() + timeoutNanos;
        this.wake();
        while (!this.ring.isEmpty() && System.nanoTime() < end) {
            Thread.onSpinWait();
            LockSupport.parkNanos(1_000_000L);
        }
        return this.ring.isEmpty();
    }

    private void open(Start start) {
        if (this.out != null) {
            this.close(this.logId, false);
        }
        this.logId = start.logId();
        this.maxBytes = Math.max(1L << 20, start.maxBytes());
        this.written = 0L;
        this.buffer.clear();
        try {
            Files.createDirectories(this.library.directory());
            this.pruned += this.library.pruneTo(this.maxBytes * 3 / 4, null);
            this.othersBytes = this.library.totalBytes();
            Path target = this.library.uniquePath(start.fileName());
            this.out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            this.path = target;
            this.currentFile = target.getFileName().toString();
            this.currentBytes = 0L;
            this.encoder.begin(start.setup());
            this.flush(false);
        } catch (IOException | RuntimeException e) {
            this.fail(e);
        }
    }

    private void close(int id, boolean full) {
        if (this.out == null || id != this.logId) {
            return;
        }
        this.encoder.end();
        this.flush(false);
        try {
            if (this.out != null) {
                this.out.close();
            }
        } catch (IOException e) {
            this.lastError = String.valueOf(e.getMessage());
        }
        if (this.path != null) {
            this.lastSaved = new Saved(this.path, this.written, this.encoder.frames(), full);
        }
        this.out = null;
        this.path = null;
        this.currentFile = null;
        if (full) {
            this.closedLogId = id;
        }
    }

    private void flushIfDue(boolean force) {
        long now = System.nanoTime();
        if (this.out != null && this.buffer.size() > 0 && (force || now - this.lastFlushNanos >= FLUSH_INTERVAL_NANOS)) {
            this.flush(true);
        }
    }

    private void flush(boolean checkLimit) {
        this.lastFlushNanos = System.nanoTime();
        if (this.out == null) {
            this.buffer.clear();
            return;
        }
        try {
            int size = this.buffer.size();
            this.buffer.writeTo(this.out);
            this.out.flush();
            this.buffer.clear();
            this.written += size;
            this.currentBytes = this.written;
            if (checkLimit && this.written + this.othersBytes > this.maxBytes) {
                this.pruned += this.library.pruneTo(this.maxBytes - this.written, this.path);
                this.othersBytes = Math.max(0L, this.library.totalBytes() - this.written);
                if (this.written + this.othersBytes > this.maxBytes) {
                    this.close(this.logId, true);
                }
            }
        } catch (IOException | RuntimeException e) {
            this.fail(e);
        }
    }

    private void fail(Exception e) {
        this.lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
        this.closedLogId = this.logId;
        this.buffer.clear();
        try {
            if (this.out != null) {
                this.out.close();
            }
        } catch (IOException ignored) {
        }
        this.out = null;
        this.path = null;
        this.currentFile = null;
    }

    public void shutdown() {
        this.running = false;
        this.wake();
    }
}
