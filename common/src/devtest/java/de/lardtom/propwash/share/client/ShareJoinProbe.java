package de.lardtom.propwash.share.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.ImportStatus;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.dev.ShareJoinCheck;
import de.lardtom.propwash.share.MissingAddons;
import de.lardtom.propwash.share.ShareDevTables;
import de.lardtom.propwash.share.ShareLayer;
import de.lardtom.propwash.share.ShareService;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class ShareJoinProbe {
    public static final int TIMEOUT_TICKS = 20 * 120;
    public static final String NAMED = "Night Ripper";
    public static final String ACTIVE = "share_active";
    private static final String KEY = "share_probe";
    private static final int GIFT_WAIT_TICKS = 30;

    private ShareJoinProbe() {
    }

    private static final class Probe {
        final StringBuilder out = new StringBuilder();
        int phase;
        int start = -1;
        int since;
        @Nullable CompletableFuture<ImportResult> pending;
        boolean substitutes;

        void line(String text) {
            this.out.append(text).append('\n');
            Constants.LOG.info("[Beitrittstest] Sharecode: {}", text);
        }
    }

    public static @Nullable String observe(Map<String, Object> state, int tick, Minecraft mc) {
        Probe probe = (Probe) state.computeIfAbsent(KEY, key -> new Probe());
        if (probe.start < 0) {
            state.put(ACTIVE, true);
            probe.start = tick;
            probe.since = tick;
            ShareDevTables.ensure();
            ShareSettings.reset();
        }
        if (tick - probe.start > TIMEOUT_TICKS) {
            probe.line("share timeout in phase " + probe.phase);
            return finish(probe, mc);
        }
        switch (probe.phase) {
            case 0 -> {
                Optional<DroneData> drone = ShareSelfTests.inventoryDrone(mc, data -> data.name().filter(NAMED::equals).isPresent());
                if (drone.isPresent()) {
                    probe.line("share give ok " + drone.get().build().frame());
                    probe.phase = 1;
                    probe.since = tick;
                } else if (tick - probe.since > 20 * 30) {
                    probe.line("share give missing");
                    probe.phase = 1;
                    probe.since = tick;
                }
                return null;
            }
            case 1 -> {
                WorkbenchContext context = ShareWorkbenchExtension.lastContext();
                if (context == null || context.screen() != mc.gui.screen() || context.creative()) {
                    return null;
                }
                ShareSettings.lastInput(code(false));
                ShareScreen.open(context, ShareScreen.Mode.IMPORT, false);
                probe.phase = 2;
                probe.since = tick;
                return null;
            }
            case 2, 5, 8 -> {
                ShareScreen screen = ShareScreen.active();
                if (screen == null || screen.frames() < 3 || tick - probe.since < 5) {
                    return null;
                }
                if (probe.phase == 5) {
                    screen.setInput(code(true));
                    boolean inactive = !screen.state().decoded().inactiveParts().isEmpty() && screen.state().addons().stream()
                            .anyMatch(a -> a.kind().filter(k -> k == MissingAddons.Kind.INACTIVE).isPresent());
                    probe.line(inactive ? "share inactive shown " + screen.state().decoded().inactiveParts()
                            : "share inactive missing " + screen.state().decoded().status() + " " + screen.state().addons());
                }
                if (probe.phase == 8 && !ShareSettings.substitutes()) {
                    ShareSettings.substitutes(true);
                    probe.substitutes = true;
                    if (!screen.state().usingSubstitutes()) {
                        probe.line("share substitute none " + screen.state().substitutes().method());
                        return finish(probe, mc);
                    }
                }
                ShareSettings.setApply(EnumSet.of(ShareLayer.PARTS));
                probe.pending = screen.apply();
                if (probe.pending == null) {
                    probe.line("share apply blocked in phase " + probe.phase + " " + screen.chosenLayers());
                    return finish(probe, mc);
                }
                probe.phase++;
                return null;
            }
            case 3, 6, 9 -> {
                CompletableFuture<ImportResult> pending = probe.pending;
                if (pending == null || !pending.isDone()) {
                    return null;
                }
                ImportResult result = pending.getNow(null);
                String status = result == null ? "none" : result.status().name().toLowerCase(Locale.ROOT);
                if (probe.phase == 3) {
                    ShareScreen screen = ShareScreen.active();
                    probe.line("share " + status + " " + (result == null ? 0 : result.missingParts().size()) + " parts, shopping "
                            + (screen == null || screen.shopping() == null ? "none" : screen.shopping().missingPieces() + " missing"));
                    if (result != null && result.status() == ImportStatus.MISSING_PARTS) {
                        probe.phase = 4;
                        probe.since = tick;
                        return null;
                    }
                    if (result != null && result.status() == ImportStatus.APPLIED) {
                        probe.line("share applied");
                        probe.phase = 5;
                        probe.since = tick;
                        return null;
                    }
                    return finish(probe, mc);
                }
                if (probe.phase == 6) {
                    probe.line("share inactive result " + status);
                    probe.phase = 8;
                    probe.since = tick;
                    return null;
                }
                if (result != null && result.status() == ImportStatus.MISSING_PARTS) {
                    probe.line("share substitute missing_parts " + result.missingParts().size());
                    probe.phase = 10;
                    probe.since = tick;
                    return null;
                }
                probe.line(result != null && result.status() == ImportStatus.APPLIED ? "share substitute applied" : "share substitute " + status);
                return finish(probe, mc);
            }
            case 4, 10 -> {
                if (tick - probe.since < GIFT_WAIT_TICKS) {
                    return null;
                }
                ShareScreen screen = ShareScreen.active();
                if (screen == null) {
                    probe.line("share window closed");
                    return finish(probe, mc);
                }
                probe.pending = screen.apply();
                if (probe.pending == null) {
                    return null;
                }
                probe.phase = probe.phase == 4 ? 7 : 11;
                return null;
            }
            case 7, 11 -> {
                CompletableFuture<ImportResult> pending = probe.pending;
                if (pending == null || !pending.isDone()) {
                    return null;
                }
                ImportResult result = pending.getNow(null);
                boolean applied = result != null && result.status() == ImportStatus.APPLIED;
                String status = result == null ? "none" : result.status().name().toLowerCase(Locale.ROOT);
                if (probe.phase == 7) {
                    probe.line(applied ? "share applied" : "share second " + status);
                    if (!applied) {
                        return finish(probe, mc);
                    }
                    probe.phase = 5;
                    probe.since = tick;
                    return null;
                }
                probe.line(applied ? "share substitute applied" : "share substitute second " + status);
                return finish(probe, mc);
            }
            default -> {
                return finish(probe, mc);
            }
        }
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

    static String code(boolean example) {
        DroneData data = DroneData.of(ShareScene.freestyle());
        if (example) {
            data = data.withBuild(data.build().withPart(PartKind.FRAME, Identifier.fromNamespaceAndPath(ShareDevTables.EXAMPLE, "race_stretch_5")));
        }
        return ShareService.encode(data, EnumSet.of(ShareLayer.PARTS), true, Optional.empty());
    }

    public static String giveCode() {
        return ShareJoinCheck.GIVE_CODE;
    }
}
