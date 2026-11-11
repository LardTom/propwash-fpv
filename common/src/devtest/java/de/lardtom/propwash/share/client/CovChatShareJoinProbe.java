package de.lardtom.propwash.share.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.dev.CovShareJoinCheck;
import de.lardtom.propwash.share.ShareDecoded;
import de.lardtom.propwash.share.ShareLayer;
import de.lardtom.propwash.share.ShareServer;
import de.lardtom.propwash.share.ShareService;
import de.lardtom.propwash.share.chat.ShareChatHarness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CovChatShareJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 150;
    public static final Pattern CODE = Pattern.compile("PW3-[0-9A-Za-z-]+");
    public static final String ACTIVE = "cov_chatshare_active";
    private static final String KEY = "cov_chatshare_probe";

    private CovChatShareJoinProbe() {
    }

    private static final class Probe {
        final StringBuilder out = new StringBuilder();
        int phase;
        int start = -1;
        int since;
        @Nullable GuiMessage baseline;
        @Nullable String sent;
        @Nullable String received;
        @Nullable CompletableFuture<ImportResult> pending;

        void line(String text) {
            this.out.append(text).append('\n');
            Constants.LOG.info("[Beitrittstest] Abdeckung cov-pw3 Chat: {}", text);
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
            probe.line("chatshare timeout in phase " + probe.phase);
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
                connection.sendCommand(CovShareJoinCheck.COMMAND + " give");
                probe.phase = 1;
                probe.since = tick;
                return null;
            }
            case 1 -> {
                if (PropwashClientApi.heldDrone().filter(d -> d.name().equals(Optional.of(CovShareJoinCheck.HELD_NAME))).isEmpty() || tick - probe.since < 30) {
                    return tick - probe.since > 400 ? fail(probe, mc, "drone") : null;
                }
                probe.baseline = CovShareJoinProbe.head(mc);
                connection.sendCommand("propwash code export");
                probe.phase = 2;
                probe.since = tick;
                return null;
            }
            case 2 -> {
                String code = null;
                for (GuiMessage message : CovShareJoinProbe.fresh(mc, probe.baseline)) {
                    String value = CovShareSelfTests.Capture.copyValue(message.content());
                    if (value != null && value.startsWith("PW3-")) {
                        code = value;
                    }
                }
                if (code == null) {
                    return tick - probe.since > 300 ? fail(probe, mc, "export") : null;
                }
                probe.sent = code;
                probe.line("chatshare export " + code.length() + " chars, layers " + layers(code));
                probe.baseline = CovShareJoinProbe.head(mc);
                connection.sendChat(code);
                probe.phase = 3;
                probe.since = tick;
                return null;
            }
            case 3 -> {
                String found = null;
                String line = null;
                for (GuiMessage message : CovShareJoinProbe.fresh(mc, probe.baseline)) {
                    Matcher matcher = CODE.matcher(message.content().getString());
                    if (matcher.find()) {
                        found = matcher.group();
                        line = message.content().getString();
                    }
                }
                if (found == null) {
                    return tick - probe.since > 300 ? fail(probe, mc, "incoming chat line") : null;
                }
                if (tick - probe.since < 20) {
                    return null;
                }
                probe.received = found;
                probe.line("chatshare line " + line);
                probe.line("chatshare same=" + (found.equals(probe.sent) ? "yes" : "no " + found));
                boolean clickable = ShareChatHarness.ours(ShareChatHarness.shownStyle(mc, found), found);
                probe.line("chatshare click=" + (clickable ? "yes" : "no"));
                connection.sendCommand(CovShareJoinCheck.COMMAND + " bench creative");
                probe.phase = 4;
                probe.since = tick;
                return null;
            }
            case 4, 7 -> {
                WorkbenchContext context = CovShareSelfTests.benchContext();
                boolean creative = probe.phase == 4;
                if (context == null || context.creative() != creative || tick - probe.since < 20) {
                    return tick - probe.since > 400 ? fail(probe, mc, "bench " + (creative ? "creative" : "survival")) : null;
                }
                ShareSettings.lastInput(probe.received);
                ShareScreen.open(context, ShareScreen.Mode.IMPORT, false);
                probe.phase++;
                probe.since = tick;
                return null;
            }
            case 5, 8 -> {
                ShareScreen screen = ShareScreen.active();
                if (screen == null || screen.frames() < 3) {
                    return tick - probe.since > 300 ? fail(probe, mc, "import window") : null;
                }
                screen.setInput(probe.received);
                ShareSettings.setApply(EnumSet.of(ShareLayer.PARTS, ShareLayer.PAINT, ShareLayer.TUNE));
                probe.pending = screen.apply();
                if (probe.pending == null) {
                    return tick - probe.since > 300 ? fail(probe, mc, "apply " + screen.chosenLayers()) : null;
                }
                probe.phase++;
                probe.since = tick;
                return null;
            }
            case 6 -> {
                ImportResult result = done(probe);
                if (result == null) {
                    return tick - probe.since > 300 ? fail(probe, mc, "creative answer") : null;
                }
                ShareScreen screen = ShareScreen.active();
                Optional<DroneData> built = screen == null ? Optional.empty() : screen.context().result();
                if (result.status() == ImportStatus.APPLIED && built.isEmpty() && tick - probe.since < 200) {
                    return null;
                }
                ShareDecoded decoded = ShareService.decode(probe.sent);
                boolean same = decoded.ok() && built.map(d -> d.build().equals(decoded.require().build())).orElse(false);
                probe.line(result.status() == ImportStatus.APPLIED ? "chatshare applied same_build=" + (same ? "yes" : "no") : "chatshare creative "
                        + result.status());
                if (screen != null) {
                    screen.onClose();
                }
                connection.sendCommand(CovShareJoinCheck.COMMAND + " bench survival");
                probe.phase = 7;
                probe.since = tick;
                return null;
            }
            default -> {
                ImportResult result = done(probe);
                if (result == null) {
                    return tick - probe.since > 300 ? fail(probe, mc, "survival answer") : null;
                }
                int pieces = 0;
                ShareDecoded decoded = ShareService.decode(probe.received);
                if (decoded.ok()) {
                    for (ShareServer.Slot slot : ShareServer.slots(decoded.require().build())) {
                        pieces += slot.count();
                    }
                }
                ShareScreen screen = ShareScreen.active();
                ShoppingList shopping = screen == null ? null : screen.shopping();
                probe.line(result.status() == ImportStatus.MISSING_PARTS ? "chatshare missing_parts " + result.missingParts().size() + " of " + pieces
                        + ", shopping " + (shopping == null ? "none" : shopping.missingPieces() + " missing") : "chatshare survival " + result.status());
                return finish(probe, mc);
            }
        }
    }

    private static @Nullable ImportResult done(Probe probe) {
        CompletableFuture<ImportResult> pending = probe.pending;
        return pending != null && pending.isDone() ? pending.getNow(null) : null;
    }

    private static List<ShareLayer> layers(String code) {
        return List.copyOf(CovShareSelfTests.layersOf(code));
    }

    private static String fail(Probe probe, Minecraft mc, String what) {
        probe.line("chatshare fail " + what);
        return finish(probe, mc);
    }

    private static String finish(Probe probe, Minecraft mc) {
        if (mc.player != null && mc.gui.screen() != null) {
            mc.player.closeContainer();
        }
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        ShareSettings.reset();
        return probe.out.toString();
    }
}
