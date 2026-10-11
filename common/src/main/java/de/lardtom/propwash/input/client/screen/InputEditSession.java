package de.lardtom.propwash.input.client.screen;

import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.ui.client.config.ConfigTabHost;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

final class InputEditSession {
    private static final Map<ConfigTabHost, InputEditSession> SESSIONS = new WeakHashMap<>();

    private InputConfig working = InputConfig.get().copy();
    private int revision = InputConfig.revision();
    private boolean dirty;

    private InputEditSession() {
    }

    static InputEditSession of(@Nullable ConfigTabHost host) {
        if (host == null) {
            return new InputEditSession();
        }
        return SESSIONS.computeIfAbsent(host, h -> new InputEditSession());
    }

    InputConfig working() {
        return this.working;
    }

    boolean dirty() {
        return this.dirty;
    }

    void markDirty() {
        this.dirty = true;
    }

    boolean reloadIfStale() {
        if (this.dirty || this.revision == InputConfig.revision()) {
            return false;
        }
        this.working = InputConfig.get().copy();
        this.revision = InputConfig.revision();
        return true;
    }

    void save() {
        if (!this.dirty) {
            return;
        }
        InputConfig merged = InputConfig.get().copy();
        merged.mergeReceiverFrom(this.working);
        merged.mergeSwitchesFrom(this.working);
        InputConfig.commit(merged);
        this.dirty = false;
        this.revision = InputConfig.revision();
        TransmitterInput.INSTANCE.rescanSoon();
    }
}
