package de.lardtom.propwash.video.record;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class FrameQueue implements AutoCloseable {
    public static final int SPILL_RESERVE = 4;
    public static final int MIN_MEMORY_FRAMES = 6;
    private static final long ALIGNMENT = 64L;

    private final int frameBytes;
    private final int maxBuffers;
    private final int keepInMemory;
    private final long spoolCapBytes;
    private final Path spoolFile;
    private final ArrayDeque<Entry> queue = new ArrayDeque<>();
    private final ArrayDeque<ByteBuffer> free = new ArrayDeque<>();
    private final Thread writer;
    private final PipelineStats stats;
    private final Arena arena = Arena.ofShared();
    private ByteBuffer readBuffer;
    private FileChannel spool;
    private long spoolWritten;
    private int allocated;
    private int borrowed;
    private int inMemory;
    private int onDisk;
    private long spilledTotal;
    private long dropped;
    private boolean closed;
    private boolean released;
    private boolean spoolFailed;

    public FrameQueue(int frameBytes, long memoryCapBytes, long spoolCapBytes, Path spoolFile) {
        this(frameBytes, memoryCapBytes, spoolCapBytes, spoolFile, new PipelineStats(1));
    }

    public FrameQueue(int frameBytes, long memoryCapBytes, long spoolCapBytes, Path spoolFile, PipelineStats stats) {
        this.stats = stats;
        this.frameBytes = frameBytes;
        this.maxBuffers = (int) Math.max(MIN_MEMORY_FRAMES + SPILL_RESERVE, Math.min(Integer.MAX_VALUE, memoryCapBytes / Math.max(1, frameBytes)));
        this.keepInMemory = this.maxBuffers - SPILL_RESERVE;
        this.spoolCapBytes = Math.max(0L, spoolCapBytes);
        this.spoolFile = spoolFile;
        this.writer = new Thread(this::spillLoop, "Propwash video spool");
        this.writer.setDaemon(true);
        this.writer.setPriority(Thread.NORM_PRIORITY - 1);
        this.writer.start();
    }

    public int frameBytes() {
        return this.frameBytes;
    }

    public int maxBuffers() {
        return this.maxBuffers;
    }

    public synchronized int allocated() {
        return this.allocated;
    }

    public synchronized ByteBuffer acquire() {
        if (this.closed) {
            return null;
        }
        ByteBuffer buffer = this.free.pollFirst();
        if (buffer == null) {
            if (this.allocated >= this.maxBuffers) {
                this.dropped++;
                return null;
            }
            buffer = this.allocate();
            this.allocated++;
            this.stats.pool((long) this.allocated * this.frameBytes);
        }
        this.borrowed++;
        return buffer.clear();
    }

    public synchronized void release(ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }
        this.borrowed = Math.max(0, this.borrowed - 1);
        if (this.closed) {
            this.freeArenaIfIdle();
            return;
        }
        this.free.addFirst(buffer);
        this.notifyAll();
    }

    public synchronized void submit(long slot, ByteBuffer buffer) {
        this.borrowed = Math.max(0, this.borrowed - 1);
        if (this.closed) {
            this.freeArenaIfIdle();
            return;
        }
        this.queue.addLast(new Entry(slot, buffer));
        this.inMemory++;
        this.notifyAll();
    }

    public synchronized int size() {
        return this.queue.size();
    }

    public synchronized int spilled() {
        return this.onDisk;
    }

    public synchronized long spilledTotal() {
        return this.spilledTotal;
    }

    public synchronized long dropped() {
        return this.dropped;
    }

    public synchronized boolean spoolFailed() {
        return this.spoolFailed;
    }

    public synchronized boolean spoolFull() {
        return this.spoolWritten + this.frameBytes > this.spoolCapBytes;
    }

    public synchronized boolean memoryFull() {
        return this.allocated >= this.maxBuffers && this.free.isEmpty();
    }

    public synchronized double fill() {
        long capacity = this.maxBuffers + (this.spoolFailed ? 0L : this.spoolCapBytes / Math.max(1, this.frameBytes));
        return capacity <= 0L ? 1.0 : Math.min(1.0, this.queue.size() / (double) capacity);
    }

    public synchronized void countDrop() {
        this.dropped++;
    }

    public Taken take(long timeoutMillis) throws IOException, InterruptedException {
        Entry entry;
        ByteBuffer target;
        synchronized (this) {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (this.queue.isEmpty() || this.queue.peekFirst().spilling) {
                long wait = deadline - System.currentTimeMillis();
                if (wait <= 0L || this.closed) {
                    return null;
                }
                this.wait(wait);
            }
            entry = this.queue.pollFirst();
            if (entry.data != null) {
                this.inMemory--;
                this.borrowed++;
                return new Taken(entry.slot, entry.data.clear(), true);
            }
            this.onDisk--;
            if (this.readBuffer == null) {
                this.readBuffer = this.allocate();
            }
            target = this.readBuffer.clear();
        }
        this.stats.spoolRead();
        long position = entry.offset;
        while (target.hasRemaining()) {
            int read = this.spool.read(target, position);
            if (read < 0) {
                throw new IOException("spool truncated");
            }
            position += read;
        }
        return new Taken(entry.slot, target.flip(), false);
    }

    private ByteBuffer allocate() {
        return this.arena.allocate(this.frameBytes, ALIGNMENT).asByteBuffer();
    }

    private void spillLoop() {
        try {
            while (true) {
                Entry victim;
                ByteBuffer data;
                synchronized (this) {
                    while (!this.closed && (this.inMemory <= this.keepInMemory || this.spoolFailed || this.spoolFull() || this.newestInMemory() == null)) {
                        this.wait(250L);
                    }
                    if (this.closed) {
                        return;
                    }
                    victim = this.newestInMemory();
                    victim.spilling = true;
                    data = victim.data;
                }
                long offset;
                long started = System.nanoTime();
                try {
                    offset = this.write(data);
                    this.stats.spoolWrite(System.nanoTime() - started);
                } catch (IOException | RuntimeException e) {
                    synchronized (this) {
                        victim.spilling = false;
                        this.spoolFailed = true;
                        this.notifyAll();
                    }
                    continue;
                }
                synchronized (this) {
                    victim.offset = offset;
                    victim.data = null;
                    victim.spilling = false;
                    this.inMemory--;
                    this.onDisk++;
                    this.spilledTotal++;
                    if (!this.closed) {
                        this.free.addFirst(data);
                    }
                    this.notifyAll();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Entry newestInMemory() {
        Iterator<Entry> it = this.queue.descendingIterator();
        int seen = 0;
        while (it.hasNext()) {
            Entry entry = it.next();
            seen++;
            if (seen == this.queue.size()) {
                return null;
            }
            if (entry.data != null && !entry.spilling) {
                return entry;
            }
        }
        return null;
    }

    private long write(ByteBuffer data) throws IOException {
        if (this.spool == null) {
            Files.createDirectories(this.spoolFile.getParent());
            this.spool = FileChannel.open(this.spoolFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
        }
        long offset;
        synchronized (this) {
            offset = this.spoolWritten;
            this.spoolWritten += this.frameBytes;
        }
        ByteBuffer buffer = data.duplicate().clear().limit(this.frameBytes);
        long position = offset;
        while (buffer.hasRemaining()) {
            position += this.spool.write(buffer, position);
        }
        return offset;
    }

    public List<Long> pendingSlots() {
        synchronized (this) {
            List<Long> out = new ArrayList<>(this.queue.size());
            for (Entry entry : this.queue) {
                out.add(entry.slot);
            }
            return out;
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            this.closed = true;
            this.queue.clear();
            this.free.clear();
            this.notifyAll();
        }
        this.writer.interrupt();
        try {
            this.writer.join(2000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (this.spool != null) {
                this.spool.close();
            }
        } catch (IOException ignored) {
        }
        try {
            Files.deleteIfExists(this.spoolFile);
        } catch (IOException ignored) {
        }
        synchronized (this) {
            this.freeArenaIfIdle();
        }
    }

    private void freeArenaIfIdle() {
        if (this.released || !this.closed || this.borrowed > 0 || this.writer.isAlive()) {
            return;
        }
        try {
            this.arena.close();
            this.released = true;
        } catch (IllegalStateException ignored) {
        }
    }

    public record Taken(long slot, ByteBuffer data, boolean pooled) {
    }

    private static final class Entry {
        final long slot;
        ByteBuffer data;
        long offset = -1L;
        boolean spilling;

        Entry(long slot, ByteBuffer data) {
            this.slot = slot;
            this.data = data;
        }
    }
}
