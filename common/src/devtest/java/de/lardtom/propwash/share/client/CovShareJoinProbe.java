package de.lardtom.propwash.share.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.client.OsdLayoutSpec;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.dev.CovShareJoinCheck;
import de.lardtom.propwash.osd.OsdLayoutCodec;
import de.lardtom.propwash.osd.client.OsdClient;
import de.lardtom.propwash.share.ShareCommands;
import de.lardtom.propwash.share.ShareContent;
import de.lardtom.propwash.share.ShareOsd;
import de.lardtom.propwash.share.ShareVectors;
import de.lardtom.propwash.share.chat.ShareChatHarness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class CovShareJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 120;
    public static final String ACTIVE = "cov_share_active";
    private static final String KEY = "cov_share_probe";

    private CovShareJoinProbe() {
    }

    private static final class Probe {
        final StringBuilder out = new StringBuilder();
        int phase;
        int start = -1;
        int since;
        @Nullable GuiMessage baseline;
        int before = -1;
        int after = -1;
        @Nullable DroneData held;
        @Nullable OsdLayoutSpec saved;
        @Nullable CompletableFuture<ImportResult> pending;

        void line(String text) {
            this.out.append(text).append('\n');
            Constants.LOG.info("[Beitrittstest] Abdeckung cov-pw3: {}", text);
        }
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        Probe probe = (Probe) state.computeIfAbsent(KEY, key -> new Probe());
        if (probe.start < 0) {
            state.put(ACTIVE, true);
            probe.start = tick;
            probe.since = tick;
        }
        if (tick - probe.start > TIMEOUT_TICKS) {
            probe.line("covshare timeout in phase " + probe.phase);
            return finish(probe, mc);
        }
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || mc.player == null) {
            return null;
        }
        switch (probe.phase) {
            case 0 -> {
                if (tick - probe.since < 60) {
                    return null;
                }
                var root = connection.getCommands().getRoot().getChild(ShareCommands.ROOT);
                var code = root == null ? null : root.getChild(ShareCommands.NODE);
                if (code == null) {
                    if (tick - probe.since > 200) {
                        probe.line("covshare tree missing");
                        return finish(probe, mc);
                    }
                    return null;
                }
                boolean operator = mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
                probe.line("covshare op=" + (operator ? "yes" : "no"));
                probe.line("covshare give=" + (code.getChild("give") == null ? "absent" : "present"));
                List<String> present = new ArrayList<>();
                for (String child : List.of("export", "info", "convert", "import")) {
                    if (code.getChild(child) != null) {
                        present.add(child);
                    }
                }
                probe.line("covshare present=" + String.join(",", present));
                probe.baseline = head(mc);
                connection.sendCommand("propwash code info " + ShareVectors.P1);
                connection.sendCommand("propwash code convert " + ShareVectors.PW2_NAMED);
                probe.phase = 1;
                probe.since = tick;
                return null;
            }
            case 1 -> {
                List<GuiMessage> fresh = fresh(mc, probe.baseline);
                boolean info = fresh.stream().anyMatch(m -> keys(m.content()).contains(ShareCommands.KEY + "info.layers"));
                String converted = null;
                for (GuiMessage message : fresh) {
                    if (keys(message.content()).contains(ShareCommands.KEY + "converted")) {
                        converted = CovShareSelfTests.Capture.copyValue(message.content());
                    }
                }
                if ((!info || converted == null) && tick - probe.since < 300) {
                    return null;
                }
                probe.line(info ? "covshare info answered" : "covshare info missing");
                probe.line(ShareVectors.PW2_NAMED_PW3.equals(converted) ? "covshare convert answered" : "covshare convert missing " + converted);
                connection.sendCommand(CovShareJoinCheck.COMMAND + " give");
                probe.phase = 2;
                probe.since = tick;
                return null;
            }
            case 2 -> {
                Optional<DroneData> held = PropwashClientApi.heldDrone().filter(d -> d.name().equals(Optional.of(CovShareJoinCheck.HELD_NAME)));
                if (held.isEmpty()) {
                    if (tick - probe.since > 400) {
                        probe.line("covshare drone missing");
                        return finish(probe, mc);
                    }
                    return null;
                }
                if (tick - probe.since < 40) {
                    return null;
                }
                probe.held = held.get();
                probe.baseline = head(mc);
                connection.sendCommand(CovShareJoinCheck.COMMAND + " imports");
                probe.phase = 3;
                probe.since = tick;
                return null;
            }
            case 3 -> {
                Integer count = count(mc, probe.baseline);
                if (count == null) {
                    return tick - probe.since > 300 ? missing(probe, mc, "imports before") : null;
                }
                probe.before = count;
                probe.saved = PropwashClientApi.osd().current();
                ShareSettings.lastInput(CovShareSelfTests.P3);
                mc.gui.setScreen(new TabHarnessScreen(new ShareConfigTab()));
                probe.phase = 4;
                probe.since = tick;
                return null;
            }
            case 4 -> {
                ShareConfigTab tab = ShareConfigTab.active();
                if (tab == null || tab.frames() < 3) {
                    return tick - probe.since > 300 ? missing(probe, mc, "tab") : null;
                }
                Optional<ShareOsd> osd = tab.state().content().flatMap(ShareContent::osd);
                if (osd.isEmpty()) {
                    return missing(probe, mc, "osd layer");
                }
                String expected = OsdLayoutCodec.encode(OsdLayoutCodec.decode(osd.get().json(), OsdClient.CONFIG.get()));
                boolean applied = tab.applyOsd();
                boolean same = CovShareSelfTests.sameJson(OsdLayoutCodec.encode(OsdClient.CONFIG.get()), expected);
                probe.line("covshare osd applied=" + applied + " layout=" + (same ? "code" : "other"));
                probe.phase = 5;
                probe.since = tick;
                return null;
            }
            case 5 -> {
                if (tick - probe.since < 60) {
                    return null;
                }
                probe.baseline = head(mc);
                connection.sendCommand(CovShareJoinCheck.COMMAND + " imports");
                probe.phase = 6;
                probe.since = tick;
                return null;
            }
            case 6 -> {
                Integer count = count(mc, probe.baseline);
                if (count == null) {
                    return tick - probe.since > 300 ? missing(probe, mc, "imports after") : null;
                }
                probe.after = count;
                Optional<DroneData> held = PropwashClientApi.heldDrone();
                probe.line(count == probe.before ? "covshare osd no packet" : "covshare osd packets " + probe.before + "->" + count);
                probe.line(held.equals(Optional.ofNullable(probe.held)) ? "covshare osd drone unchanged" : "covshare osd drone changed");
                ShareConfigTab tab = ShareConfigTab.active();
                probe.pending = tab == null ? null : tab.sendTune();
                probe.phase = 7;
                probe.since = tick;
                return null;
            }
            case 7 -> {
                CompletableFuture<ImportResult> pending = probe.pending;
                if (pending == null) {
                    return missing(probe, mc, "control send");
                }
                if (!pending.isDone()) {
                    return tick - probe.since > 300 ? missing(probe, mc, "control answer") : null;
                }
                probe.line("covshare control " + pending.getNow(null));
                probe.baseline = head(mc);
                connection.sendCommand(CovShareJoinCheck.COMMAND + " imports");
                probe.phase = 8;
                probe.since = tick;
                return null;
            }
            default -> {
                Integer count = count(mc, probe.baseline);
                if (count == null) {
                    return tick - probe.since > 300 ? missing(probe, mc, "imports control") : null;
                }
                probe.line(count == probe.after + 1 ? "covshare control counted" : "covshare control not counted " + probe.after + "->" + count);
                return finish(probe, mc);
            }
        }
    }

    private static String missing(Probe probe, Minecraft mc, String what) {
        probe.line("covshare fail " + what);
        return finish(probe, mc);
    }

    static @Nullable GuiMessage head(Minecraft mc) {
        List<GuiMessage> all = ShareChatHarness.messages(mc);
        return all.isEmpty() ? null : all.getFirst();
    }

    static List<GuiMessage> fresh(Minecraft mc, @Nullable GuiMessage baseline) {
        List<GuiMessage> out = new ArrayList<>();
        for (GuiMessage message : ShareChatHarness.messages(mc)) {
            if (message == baseline) {
                break;
            }
            out.add(message);
        }
        return out;
    }

    static @Nullable Integer count(Minecraft mc, @Nullable GuiMessage baseline) {
        for (GuiMessage message : fresh(mc, baseline)) {
            String text = message.content().getString();
            if (text.startsWith(CovShareJoinCheck.IMPORTS)) {
                try {
                    return Integer.parseInt(text.substring(CovShareJoinCheck.IMPORTS.length()).trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    static List<String> keys(Component component) {
        List<String> out = new ArrayList<>();
        CovShareSelfTests.Capture capture = new CovShareSelfTests.Capture();
        capture.sendSystemMessage(component);
        out.addAll(capture.successKeys());
        return out;
    }

    private static String finish(Probe probe, Minecraft mc) {
        OsdLayoutSpec saved = probe.saved;
        if (saved != null) {
            PropwashClientApi.osd().apply(saved);
        }
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        ShareSettings.reset();
        return probe.out.toString();
    }
}
