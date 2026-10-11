package de.lardtom.propwash.join;

import de.lardtom.propwash.network.JoinInfoPayload;
import de.lardtom.propwash.network.PartsManifestPayload;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;

public final class JoinClientState {
    private static final JoinInfo UNKNOWN_SERVER = new JoinInfo("", 0, "", List.of());

    private static volatile WeakReference<Object> listener = new WeakReference<>(null);
    private static volatile @Nullable JoinInfo serverInfo;
    private static volatile @Nullable PartsManifestPayload serverParts;

    private JoinClientState() {
    }

    public static void begin(Object configListener) {
        listener = new WeakReference<>(configListener);
        serverInfo = null;
        serverParts = null;
        AddonSession.clear();
    }

    public static boolean current(Object configListener) {
        return listener.get() == configListener;
    }

    public static JoinInfoPayload onServerInfo(JoinInfoPayload payload) {
        serverInfo = payload.info();
        return JoinCheck.localPayload();
    }

    public static PartsManifestPayload onServerParts(PartsManifestPayload payload) {
        serverParts = payload;
        PartsManifestPayload local = PartsManifestPayload.local();
        JoinInfo server = serverInfo;
        AddonSession.activate(ClientOnlyAddons.compute(server == null ? UNKNOWN_SERVER : server, LocalJoinInfo.get(), JoinCheck.parts(payload),
                JoinCheck.parts(local)));
        return local;
    }

    public static @Nullable JoinReport report() {
        JoinInfo server = serverInfo;
        if (server == null) {
            return null;
        }
        PartsManifestPayload parts = serverParts;
        return JoinReport.compute(server, LocalJoinInfo.get(), parts == null ? null : JoinCheck.parts(parts),
                parts == null ? null : JoinCheck.parts(PartsManifestPayload.local()));
    }
}
