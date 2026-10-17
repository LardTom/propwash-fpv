package de.lardtom.propwash.osd.client;

import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.StickMode;
import de.lardtom.propwash.osd.FlightStats;
import de.lardtom.propwash.osd.OsdColors;
import de.lardtom.propwash.osd.OsdCondition;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdData;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.OsdFormat;
import de.lardtom.propwash.osd.OsdGrid;
import de.lardtom.propwash.osd.OsdInterference;
import de.lardtom.propwash.osd.OsdProjection;
import de.lardtom.propwash.osd.OsdStatsLayout;
import de.lardtom.propwash.osd.OsdWarnings;
import de.lardtom.propwash.osd.VoltageDisplay;
import de.lardtom.propwash.video.record.path.FlashbackBudget;
import de.lardtom.propwash.sim.BatteryState;
import de.lardtom.propwash.sim.HoldMode;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.trick.TrickType;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.ui.SpeedUnit;
import de.lardtom.propwash.ui.client.UiConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongFunction;

public final class OsdRenderer {
    public static final int AMBER = 0xFFB43C;
    public static final int GREEN = 0x55FF6A;
    public static final double SPLIT_VISIBLE_SECONDS = 4.0;
    public static final double ARMED_VISIBLE_SECONDS = 2.0;
    public static final float PREVIEW_FOV = 90.0F;
    public static final float PREVIEW_UPTILT = 25.0F;
    private static final float COMPASS_RANGE_DEG = 60.0F;
    private static final String[] COMPASS_KEYS = prefixed("propwash.osd.compass.", "n", "ne", "e", "se", "s", "sw", "w", "nw");
    private static final float NOISE_OPACITY_LIFT = 1.4F;
    private static final String[] STAT_KEYS = prefixed("propwash.osd.stats.", "flight_time", "max_speed", "max_altitude", "max_distance",
            "min_cell", "max_current", "used_mah", "max_g", "crashes", "condition", "trick_score", "best_combo");
    private static final String[] VIDEO_KEYS = enumKeys("propwash.osd.video.", VideoSystem.values());
    private static final String[] MODE_KEYS = enumKeys("propwash.osd.mode.", StabMode.values());
    private static final String[] MODE_3D_KEYS = enumKeys("propwash.osd.mode_3d.", StabMode.values());
    private static final long THROTTLE_3D_KEY = 1_000L;
    private static final String LABEL_HOME = "propwash.osd.label.home";
    private static final String LABEL_LQ = "propwash.osd.label.lq";
    private static final String LABEL_VIDEO = "propwash.osd.label.video";
    private static final String LABEL_RSSI = "propwash.osd.label.rssi";
    private static final String LABEL_GATE = "propwash.osd.label.gate";
    private static final String LABEL_PITCH = "propwash.osd.label.pitch";
    private static final String LABEL_ROLL = "propwash.osd.label.roll";
    private static final String LABEL_XYZ = "propwash.osd.label.xyz";
    private static final String LABEL_ON = "propwash.osd.label.on";
    private static final String LABEL_LAP = "propwash.osd.label.lap";
    private static final String LABEL_LAST = "propwash.osd.label.last";
    private static final String LABEL_BEST = "propwash.osd.label.best";
    private static final String LABEL_THROTTLE = "propwash.osd.label.throttle";
    private static final String LABEL_FLY = "propwash.osd.label.fly";
    private static final String LABEL_REC = "propwash.osd.label.rec";
    private static final String LABEL_FLASHBACK = "propwash.osd.label.flashback";
    private static final int THIN_MARK = 5;
    private static final long FLASHBACK_THINNING_KEY = 1L << 40;
    private static final String LABEL_SAVING = "propwash.osd.label.saving";
    private static final String LABEL_LEDS_OFF = "propwash.osd.label.leds_off";
    private static final String LABEL_SAT = "propwash.osd.label.sat";
    private static final String LABEL_VTX = "propwash.osd.label.vtx";
    private static final String LABEL_VTX_AUTO = "propwash.osd.label.vtx_auto";
    private static final String LABEL_RELAY = "propwash.osd.label.relay";
    private static final long SAVING_KEY = 1L << 40;
    private static final int REC_MARK = 5;
    private static final int REC_GAP = 3;
    private static final double REC_BLINK_PERIOD = 1.0;
    private static final double REC_BLINK_ON = 0.6;
    private static final String LABEL_TIME = "propwash.osd.label.time";
    private static final LongFunction<String> HEADING_TEXT = OsdRenderer::headingText;
    private static final LongFunction<String> AMPS_TEXT = key -> OsdFormat.fixed(key, 1) + "A";
    private static final LongFunction<String> WATTS_TEXT = key -> OsdFormat.fixed(key, 0) + "W";
    private static final LongFunction<String> MAH_TEXT = key -> OsdFormat.fixed(key, 0) + "mAh";
    private static final LongFunction<String> PACK_VOLTS_TEXT = key -> OsdFormat.fixed(key, 1) + "V";
    private static final LongFunction<String> CELL_VOLTS_TEXT = key -> OsdFormat.fixed(key, 2) + "V";
    private static final LongFunction<String> G_TEXT = key -> OsdFormat.fixed(key, 1) + "G";
    private static final int STAT_COUNT = 12;
    private static final int STAT_BASE_COUNT = 10;
    public static final double TRICK_VISIBLE_SECONDS = 2.5;
    public static final double TRICK_LOST_SECONDS = 1.8;
    public static final double TRICK_FADE_SECONDS = 0.5;
    private static final String LABEL_TRICK_COMBO = "propwash.osd.trick.combo";
    private static final String LABEL_TRICK_LOST = "propwash.osd.trick.lost";
    static final int TRICK_NAME_BUDGET = 28;
    static final String CHAIN_SEPARATOR = " + ";
    static final String CHAIN_MORE = ".. ";
    private static final String[] TRICK_KEYS = enumKeys("propwash.osd.trick.type.", TrickType.values());
    private static final OsdElement[] STATS_COMPANIONS = {OsdElement.ARMED_STATUS, OsdElement.WARNINGS, OsdElement.CRAFT_NAME};

    private final OsdProjection projection = new OsdProjection();
    private final OsdProjection.Horizon horizon = new OsdProjection.Horizon();
    private final Vector2f point = new Vector2f();
    private final Vector3f azimuth = new Vector3f();
    private final Quaternionf synthetic = new Quaternionf();
    private final List<OsdWarnings.Warning> warnings = new ArrayList<>();
    private final EnumMap<OsdElement, float[]> bounds = new EnumMap<>(OsdElement.class);
    private final EnumSet<OsdElement> drawn = EnumSet.noneOf(OsdElement.class);
    private final int[] shownSticks = new int[4];
    private final int[] rectBuffer = new int[96];
    private final long[] textKeys = new long[OsdElement.values().length];
    private final @Nullable String[] texts = new String[OsdElement.values().length];
    private final Map<String, String> translations = new HashMap<>();
    private final LongFunction<String> speedText = this::speedText;
    private final LongFunction<String> vtxPowerText = this::vtxPowerText;
    private final long[] statKeys = new long[STAT_COUNT];
    private final OsdStatsLayout statsLayout = new OsdStatsLayout();
    private final EnumMap<OsdElement, OsdConfig.Slot> statsSlots = new EnumMap<>(OsdElement.class);
    private final EnumSet<OsdElement> statsPlaced = EnumSet.noneOf(OsdElement.class);
    private final EnumMap<OsdElement, Float> statsScaleCap = new EnumMap<>(OsdElement.class);
    private boolean statsMode;
    private final @Nullable String[] statsText = new String[STAT_COUNT];
    private @Nullable Language language;
    private SpeedUnit unit = SpeedUnit.KMH;
    private @Nullable String craftNameSource;
    private String craftNameText = "";
    private @Nullable String coordinatesText;
    private @Nullable String vtxText;
    private String vtxSource = "";
    private @Nullable String relayText;
    private long relayKey = Long.MIN_VALUE;
    private long coordinateX;
    private long coordinateY;
    private long coordinateZ;
    private @Nullable String homeMarkerText;
    private long homeMarkerKey;
    private @Nullable String batteryPercentText;
    private long batteryPercentKey;
    private OsdWarnings.@Nullable Warning warningShown;
    private @Nullable String warningPhase;
    private String warningText = "";
    private final OsdDraw draw = new OsdDraw();

    private GuiGraphicsExtractor graphics;
    private Font font;
    private OsdData data;
    private OsdConfig config;
    private OsdGrid grid;
    private float baseScale;
    private float scale;
    private float alpha;
    private int color;
    private boolean editor;
    private boolean hasView;
    private long frame;
    private double pixelScale;
    private @Nullable OsdElement editorFocus;
    private double viewX;
    private double viewY;
    private double viewZ;

    public OsdRenderer() {
        for (OsdElement element : OsdElement.values()) {
            this.bounds.put(element, new float[4]);
        }
    }

    private static String[] prefixed(String prefix, String... keys) {
        String[] out = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            out[i] = prefix + keys[i];
        }
        return out;
    }

    private static String[] enumKeys(String prefix, Enum<?>[] values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = prefix + values[i].name().toLowerCase(Locale.ROOT);
        }
        return out;
    }

    public record View(Quaternionfc rotation, float fovDeg, double x, double y, double z, float fisheyeDeg, float fisheyeBend) {
    }

    public void setEditorFocus(@Nullable OsdElement focus) {
        this.editorFocus = focus;
    }

    public void setPixelScale(double pixelsPerUnit) {
        this.pixelScale = pixelsPerUnit;
    }

    public void render(GuiGraphicsExtractor graphics, Font font, OsdData data, OsdConfig config, OsdGrid grid, float textScale,
                       @Nullable View view, boolean editor) {
        this.graphics = graphics;
        this.font = font;
        this.data = data;
        this.config = config;
        this.grid = grid;
        this.baseScale = textScale;
        this.scale = textScale;
        this.alpha = config.opacityFraction();
        this.color = OsdColors.withAlpha(config.primaryRgb(), this.alpha);
        this.editor = editor;
        this.drawn.clear();
        this.refreshCaches();
        this.draw.begin(graphics, font, config.outline, config.background, this.pixelScale);
        this.setupView(view);
        OsdWarnings.collect(data, this.warnings);
        this.statsMode = !editor && data.showStats && data.stats.hasData();
        if (this.statsMode) {
            this.layoutStats();
        }
        this.frame++;
        boolean breakup = !editor && config.analogBreakup;
        float jitter = breakup ? OsdInterference.jitterAmplitude(data.videoSystem, data.videoSignal) : 0.0F;
        float shared = breakup ? OsdInterference.sharedShift(data.videoSystem, data.videoShift, Math.round(grid.width())) : 0.0F;
        for (OsdElement element : OsdElement.values()) {
            OsdConfig.Slot slot = config.slot(element);
            if (!slot.enabled || !this.visible(element)) {
                continue;
            }
            if (this.statsMode && element != OsdElement.POST_FLIGHT_STATS) {
                if (!this.statsPlaced.contains(element)) {
                    continue;
                }
                slot = this.statsSlots.get(element);
            }
            this.prepare(element);
            if (jitter > 0.0F || shared != 0.0F) {
                float offset = shared + (float) (OsdInterference.noise(element.ordinal() + 97, this.frame / 3L) * 2.0 - 1.0) * jitter;
                graphics.pose().pushMatrix();
                try {
                    graphics.pose().translate(offset, 0.0F);
                    this.drawElement(element, slot);
                } finally {
                    graphics.pose().popMatrix();
                }
            } else {
                this.drawElement(element, slot);
            }
        }
        this.draw.end();
        this.graphics = null;
    }

    private void prepare(OsdElement element) {
        this.alpha = this.config.elementAlpha(element);
        if (!this.editor && this.data.videoNoise > 0.0) {
            float lift = (float) Math.min(1.0, this.data.videoNoise * NOISE_OPACITY_LIFT);
            this.alpha += (1.0F - this.alpha) * lift;
        }
        this.color = OsdColors.withAlpha(this.config.elementRgb(element), this.alpha);
        this.scale = this.scaleOf(element);
        if (element == OsdElement.POST_FLIGHT_STATS) {
            this.scale = Math.min(this.scale, this.rowFitScale());
        }
        if (this.statsMode) {
            Float cap = this.statsScaleCap.get(element);
            if (cap != null) {
                this.scale = Math.min(this.scale, cap);
            }
        }
    }

    private float scaleOf(OsdElement element) {
        float elementScale = this.config.elementScale(element);
        if (elementScale == this.config.scale) {
            return this.baseScale;
        }
        float scaled = this.baseScale * elementScale / this.config.scale;
        return this.editor ? scaled : OsdGrid.snapToPixels(scaled, this.pixelScale);
    }

    private float rowsOf(float textScale) {
        return (OsdDraw.CELL_HEIGHT + 3) * textScale / this.grid.cellHeight();
    }

    private void layoutStats() {
        this.statsPlaced.clear();
        this.statsScaleCap.clear();
        OsdConfig.Slot stats = this.config.slot(OsdElement.POST_FLIGHT_STATS);
        int lines = this.statLines();
        int top = OsdStatsLayout.blockTop(stats.row, lines);
        float rowFit = this.rowFitScale();
        int count = STATS_COMPANIONS.length;
        int[] desired = new int[count];
        float[] natural = new float[count];
        for (int i = 0; i < count; i++) {
            OsdConfig.Slot slot = this.config.slot(STATS_COMPANIONS[i]);
            desired[i] = slot.enabled ? slot.row : OsdStatsLayout.NONE;
            natural[i] = this.rowsOf(this.scaleOf(STATS_COMPANIONS[i]));
        }
        boolean[] shrunk = new boolean[count];
        int[] rows = this.statsLayout.arrange(top, stats.enabled ? lines : 0, desired, natural, shrunk);
        for (int i = 0; i < count; i++) {
            if (rows[i] == OsdStatsLayout.NONE) {
                continue;
            }
            OsdElement element = STATS_COMPANIONS[i];
            OsdConfig.Slot moved = this.statsSlots.computeIfAbsent(element, e -> new OsdConfig.Slot());
            moved.enabled = true;
            moved.col = this.config.slot(element).col;
            moved.row = rows[i];
            this.statsPlaced.add(element);
            if (shrunk[i]) {
                this.statsScaleCap.put(element, rowFit);
            }
        }
    }

    private int statCount() {
        return this.data.stats.tricksOn ? STAT_COUNT : STAT_BASE_COUNT;
    }

    private int statLines() {
        return this.statCount() + 1;
    }

    private float rowFitScale() {
        return this.grid.cellHeight() / (OsdDraw.CELL_HEIGHT + 3);
    }

    public boolean wasDrawn(OsdElement element) {
        return this.drawn.contains(element);
    }

    public float[] bounds(OsdElement element) {
        return this.bounds.get(element);
    }

    public @Nullable OsdElement movableAt(double x, double y) {
        OsdElement[] all = OsdElement.values();
        for (int i = all.length - 1; i >= 0; i--) {
            OsdElement element = all[i];
            if (!element.movable() || !this.drawn.contains(element)) {
                continue;
            }
            float[] b = this.bounds.get(element);
            if (x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3]) {
                return element;
            }
        }
        return null;
    }

    private void setupView(@Nullable View view) {
        float centerX = this.grid.centerX();
        float centerY = this.grid.centerY();
        if (view != null) {
            if (view.fisheyeDeg() > 0.0F) {
                this.projection.setupFisheye(view.rotation(), view.fisheyeDeg(), view.fisheyeBend(), centerX, centerY, this.grid.width());
            } else {
                this.projection.setup(view.rotation(), view.fovDeg(), centerX, centerY, this.grid.height());
            }
            this.viewX = view.x();
            this.viewY = view.y();
            this.viewZ = view.z();
            this.hasView = this.projection.valid();
            return;
        }
        double compass = Double.isFinite(this.data.compassDeg) ? this.data.compassDeg : 0.0;
        double pitch = Double.isFinite(this.data.pitchDeg) ? this.data.pitchDeg : 0.0;
        double roll = Double.isFinite(this.data.rollDeg) ? this.data.rollDeg : 0.0;
        OsdProjection.cameraRotation(compass - 180.0, pitch - PREVIEW_UPTILT, roll, this.synthetic);
        this.projection.setup(this.synthetic, PREVIEW_FOV, centerX, centerY, this.grid.height());
        this.viewX = this.data.x;
        this.viewY = this.data.y;
        this.viewZ = this.data.z;
        this.hasView = true;
    }

    private boolean visible(OsdElement element) {
        OsdData d = this.data;
        if (this.editor) {
            return element != OsdElement.POST_FLIGHT_STATS || this.editorFocus == element;
        }
        if (d.showStats && d.stats.hasData()) {
            return element == OsdElement.POST_FLIGHT_STATS || element == OsdElement.WARNINGS || element == OsdElement.ARMED_STATUS
                    || element == OsdElement.CRAFT_NAME;
        }
        if (d.videoLost && (element == OsdElement.CROSSHAIR || element == OsdElement.ARTIFICIAL_HORIZON
                || element == OsdElement.HORIZON_SIDEBARS || element == OsdElement.HOME_MARKER)) {
            return false;
        }
        return switch (element) {
            case POST_FLIGHT_STATS -> false;
            case HOME_MARKER, HOME_DIRECTION, HOME_DISTANCE -> d.hasHome && d.hasPosition;
            case GATE, LAP, LAP_TIME, LAST_LAP, BEST_LAP -> d.racing;
            case SPLIT_DELTA -> d.racing && d.splitDeltaNanos != 0L && d.splitAgeSeconds < SPLIT_VISIBLE_SECONDS;
            case VIDEO_SIGNAL, VIDEO_SYSTEM -> d.videoSystem != null;
            case CRAFT_NAME -> !d.craftName.isEmpty();
            case COORDINATES -> d.hasPosition;
            case ARMED_STATUS -> !d.armed || d.armedSeconds < ARMED_VISIBLE_SECONDS;
            case ARTIFICIAL_HORIZON -> this.hasView;
            case RECORDING -> d.recording != OsdData.REC_OFF;
            case FLASHBACK_BUDGET -> Double.isFinite(d.flashbackFill);
            case LEDS_OFF -> d.ledsOff;
            case GPS_SATS -> d.gpsKnown;
            case VTX_CHANNEL -> !d.vtxChannel.isEmpty();
            case VTX_POWER -> d.vtxPowerMw > 0;
            case RELAY -> d.relayHops > 0;
            case TRICKS -> d.tricksOn && d.trickType != null && d.trickPopup != OsdData.TRICK_NONE
                    && d.trickAgeSeconds < (d.trickPopup == OsdData.TRICK_LOST ? TRICK_LOST_SECONDS : TRICK_VISIBLE_SECONDS);
            default -> true;
        };
    }

    private void drawElement(OsdElement element, OsdConfig.Slot slot) {
        OsdData d = this.data;
        SpeedUnit unit = this.unit;
        switch (element) {
            case CROSSHAIR -> this.crosshair();
            case ARTIFICIAL_HORIZON -> this.artificialHorizon();
            case HORIZON_SIDEBARS -> this.sidebars();
            case HOME_MARKER -> this.homeMarker();
            case COMPASS_BAR -> this.compassBar(slot);
            case HEADING -> this.keyed(element, slot, null, OsdFormat.headingKey(d.compassDeg), HEADING_TEXT, this.color);
            case HOME_DIRECTION -> this.homeArrow(slot);
            case HOME_DISTANCE -> this.keyed(element, slot, LABEL_HOME, OsdFormat.distanceKey(d.homeDistance(), unit), OsdFormat::distanceText, this.color);
            case LINK_QUALITY -> this.keyed(element, slot, LABEL_LQ, OsdFormat.percent(d.linkStrength), Long::toString, this.qualityColor(d.linkStrength));
            case VIDEO_SIGNAL -> this.keyed(element, slot, LABEL_VIDEO, OsdFormat.percent(d.videoSignal), Long::toString, this.qualityColor(d.videoSignal));
            case RSSI_DBM -> this.keyed(element, slot, LABEL_RSSI, OsdFormat.rssiDbm(d.linkStrength, d.rssiDbm), Long::toString, this.qualityColor(d.linkStrength));
            case GATE -> this.keyed(element, slot, LABEL_GATE, gateKey(d), OsdRenderer::gateText, this.color);
            case SPEED -> this.keyed(element, slot, null, OsdFormat.quantize(OsdFormat.speed(d.speed, unit), 0), this.speedText, this.color);
            case PITCH_ANGLE -> this.keyed(element, slot, LABEL_PITCH, OsdFormat.angleKey(d.pitchDeg), OsdFormat::signedAngleText, this.color);
            case ROLL_ANGLE -> this.keyed(element, slot, LABEL_ROLL, OsdFormat.angleKey(d.rollDeg), OsdFormat::signedAngleText, this.color);
            case BATTERY_BAR -> this.batteryBar(slot);
            case CURRENT -> this.keyed(element, slot, null, OsdFormat.quantize(d.currentA, 1), AMPS_TEXT, this.color);
            case POWER -> this.keyed(element, slot, null, OsdFormat.quantize(d.powerW, 0), WATTS_TEXT, this.color);
            case MAH_DRAWN -> this.keyed(element, slot, null, OsdFormat.quantize(d.mAhUsed, 0), MAH_TEXT, this.color);
            case BATTERY_VOLTAGE -> this.voltage(element, slot, OsdFormat.quantize(d.packVoltage, 1), PACK_VOLTS_TEXT);
            case CELL_VOLTAGE -> this.voltage(element, slot, OsdFormat.quantize(d.cellVoltage, 2), CELL_VOLTS_TEXT);
            case COORDINATES -> this.coordinates(slot);
            case POST_FLIGHT_STATS -> this.stats(slot);
            case SPLIT_DELTA -> this.keyed(element, slot, null, OsdFormat.deltaKey(d.splitDeltaNanos), OsdFormat::deltaText,
                    d.splitDeltaNanos <= 0L ? this.good() : this.critical());
            case ARMED_STATUS -> this.armedStatus(slot);
            case WARNINGS -> this.warning(slot);
            case STICK_OVERLAY -> this.sticks(slot);
            case CRAFT_NAME -> this.text(element, slot, this.craftName(d), this.color);
            case VIDEO_SYSTEM -> this.text(element, slot, this.videoSystemText(d), this.color);
            case CLOCK -> this.keyed(element, slot, null, OsdFormat.clockKey(d.clockHour, d.clockMinute), OsdFormat::clockText, this.color);
            case ON_TIME -> this.keyed(element, slot, LABEL_ON, OsdFormat.durationKey(d.onSeconds), OsdFormat::durationText, this.color);
            case LAP -> this.keyed(element, slot, LABEL_LAP, Math.max(0, d.lap), Long::toString, this.color);
            case LAP_TIME -> this.keyed(element, slot, LABEL_TIME, OsdFormat.lapKey(d.racing ? d.currentLapNanos : -1L), OsdFormat::lapText, this.color);
            case LAST_LAP -> this.keyed(element, slot, LABEL_LAST, OsdFormat.lapKey(d.lastLapNanos), OsdFormat::lapText, this.color);
            case BEST_LAP -> this.keyed(element, slot, LABEL_BEST, OsdFormat.lapKey(d.bestLapNanos), OsdFormat::lapText, this.color);
            case ALTITUDE -> this.keyed(element, slot, null, OsdFormat.altitudeKey(d.altitude, unit), OsdFormat::altitudeText, this.color);
            case VARIO -> this.keyed(element, slot, null, OsdFormat.varioKey(d.verticalSpeed, unit), OsdFormat::varioText, this.color);
            case G_FORCE -> this.keyed(element, slot, null, OsdFormat.quantize(d.gLoad, 1), G_TEXT, this.color);
            case FLIGHT_MODE -> this.text(element, slot, this.modeText(d), d.rescueActive ? this.caution() : this.color);
            case THROTTLE -> {
                if (d.mode3d) {
                    this.keyed(element, slot, LABEL_THROTTLE, THROTTLE_3D_KEY + OsdFormat.signedPercent(d.throttle),
                            key -> OsdFormat.signedPercentText(key - THROTTLE_3D_KEY), this.color);
                } else {
                    this.keyed(element, slot, LABEL_THROTTLE, OsdFormat.percent(d.throttle), Long::toString, this.color);
                }
            }
            case FLIGHT_TIME -> this.keyed(element, slot, LABEL_FLY, OsdFormat.durationKey(d.flightSeconds), OsdFormat::durationText, this.color);
            case RECORDING -> this.recording(slot);
            case LEDS_OFF -> this.text(element, slot, this.tr(LABEL_LEDS_OFF), this.color);
            case GPS_SATS -> this.keyed(element, slot, LABEL_SAT, Math.max(0, d.gpsSatellites), Long::toString,
                    d.gpsFix ? this.color : this.caution());
            case VTX_CHANNEL -> this.text(element, slot, this.vtxText(d), this.color);
            case VTX_POWER -> this.keyed(element, slot, null, d.vtxPowerMw * 2L + (d.vtxPowerAuto ? 1L : 0L), this.vtxPowerText,
                    d.vtxThrottled ? this.caution() : this.color);
            case RELAY -> this.text(element, slot, this.relayText(d), this.qualityColor(weakestRelayHop(d)));
            case TRICKS -> this.tricks(slot);
            case FLASHBACK_BUDGET -> this.flashbackBudget(slot);
        }
    }

    private void refreshCaches() {
        Language current = Language.getInstance();
        SpeedUnit currentUnit = UiConfig.speedUnit();
        if (current == this.language && currentUnit == this.unit) {
            return;
        }
        this.language = current;
        this.unit = currentUnit;
        this.translations.clear();
        Arrays.fill(this.texts, null);
        this.craftNameSource = null;
        this.coordinatesText = null;
        this.vtxText = null;
        this.relayText = null;
        Arrays.fill(this.statsText, null);
        this.homeMarkerText = null;
        this.batteryPercentText = null;
        this.warningShown = null;
    }

    private String vtxText(OsdData d) {
        String channel = d.vtxChannel;
        if (!channel.equals(this.vtxSource) || this.vtxText == null) {
            this.vtxSource = channel;
            this.vtxText = this.tr(LABEL_VTX) + " " + channel;
        }
        return this.vtxText;
    }

    private String vtxPowerText(long key) {
        String power = (key / 2L) + "MW";
        return (key & 1L) != 0L ? this.tr(LABEL_VTX_AUTO) + " " + power : power;
    }

    private String relayText(OsdData d) {
        int hops = Math.clamp(d.relayHops, 0, OsdData.MAX_RELAY_HOPS - 1);
        long key = hops;
        for (int hop = 0; hop <= hops; hop++) {
            key = key * 101L + OsdFormat.percent(d.relayHopQuality[hop]);
        }
        if (key != this.relayKey || this.relayText == null) {
            StringBuilder text = new StringBuilder(this.tr(LABEL_RELAY)).append(hops);
            for (int hop = 0; hop <= hops; hop++) {
                text.append(' ').append(OsdFormat.percent(d.relayHopQuality[hop]));
            }
            this.relayKey = key;
            this.relayText = text.toString();
        }
        return this.relayText;
    }

    private static double weakestRelayHop(OsdData d) {
        double weakest = 1.0;
        int hops = Math.clamp(d.relayHops, 0, OsdData.MAX_RELAY_HOPS - 1);
        for (int hop = 0; hop <= hops; hop++) {
            weakest = Math.min(weakest, d.relayHopQuality[hop]);
        }
        return weakest;
    }

    private String tr(String key) {
        return this.translations.computeIfAbsent(key, I18n::get);
    }

    private String cachedText(OsdElement element, @Nullable String labelKey, long key, LongFunction<String> formatter) {
        int index = element.ordinal();
        String cached = this.texts[index];
        if (cached != null && this.textKeys[index] == key) {
            return cached;
        }
        String value = formatter.apply(key);
        String built = labelKey == null ? value : this.tr(labelKey) + " " + value;
        this.texts[index] = built;
        this.textKeys[index] = key;
        return built;
    }

    private void keyed(OsdElement element, OsdConfig.Slot slot, @Nullable String labelKey, long key, LongFunction<String> formatter, int textColor) {
        this.text(element, slot, this.cachedText(element, labelKey, key, formatter), textColor);
    }

    private void voltage(OsdElement element, OsdConfig.Slot slot, long key, LongFunction<String> formatter) {
        if (!this.editor && this.data.voltageAlert != VoltageDisplay.NONE && !OsdWarnings.blinkOn(this.data.blinkSeconds)) {
            String text = this.cachedText(element, null, key, formatter);
            float width = this.draw.width(text, this.scale);
            float anchorX = this.grid.anchorX(slot.col, element.align());
            float x = switch (element.align()) {
                case LEFT -> anchorX;
                case CENTER -> anchorX - width * 0.5F;
                case RIGHT -> anchorX - width;
            };
            float y = this.textTop(slot.row);
            this.record(element, x - this.scale, y - this.scale, x + width, y + (OsdDraw.GLYPH_HEIGHT + 2) * this.scale);
            return;
        }
        this.keyed(element, slot, null, key, formatter, this.voltageColor());
    }

    private int voltageColor() {
        int alert = this.data.voltageAlert;
        BatteryState state = this.data.batteryState;
        if (alert == VoltageDisplay.CRITICAL || state == BatteryState.CRITICAL || state == BatteryState.EMPTY) {
            return this.critical();
        }
        if (alert == VoltageDisplay.WARNING || state == BatteryState.LOW) {
            return this.caution();
        }
        return this.color;
    }

    private static String headingText(long key) {
        return OsdFormat.headingText(key) + "°";
    }

    private String speedText(long key) {
        return OsdFormat.fixed(key, 0) + OsdFormat.speedUnit(this.unit);
    }

    private static long gateKey(OsdData d) {
        if (d.gateCount <= 0 || d.nextGate < 0) {
            return OsdFormat.NONE;
        }
        return ((long) d.nextGate << 32) | (d.gateCount & 0xFFFFFFFFL);
    }

    static String gateText(long key) {
        if (key == OsdFormat.NONE) {
            return "-/-";
        }
        return ((key >>> 32) + 1) + "/" + (key & 0xFFFFFFFFL);
    }

    private String craftName(OsdData d) {
        if (d.craftName.isEmpty() && this.editor) {
            return "PROPWASH";
        }
        if (d.craftName != this.craftNameSource) {
            this.craftNameSource = d.craftName;
            this.craftNameText = d.craftName.toUpperCase(Locale.ROOT);
        }
        return this.craftNameText;
    }

    private String videoSystemText(OsdData d) {
        if (d.videoSystem == null) {
            return OsdFormat.UNKNOWN;
        }
        return this.tr(VIDEO_KEYS[d.videoSystem.ordinal()]);
    }

    private String modeText(OsdData d) {
        if (d.rescueActive) {
            return this.tr("propwash.osd.mode.rescue");
        }
        if (d.turtle) {
            return this.tr("propwash.osd.mode.turtle");
        }
        if (d.assistLabel != null) {
            return d.assistLabel.toUpperCase(Locale.ROOT);
        }
        if (d.holdMode == HoldMode.POSHOLD) {
            return this.tr("propwash.osd.mode.poshold");
        }
        if (d.holdMode == HoldMode.ALTHOLD) {
            return this.tr("propwash.osd.mode.althold");
        }
        StabMode mode = d.mode == null ? StabMode.ACRO : d.mode;
        return d.mode3d ? this.tr(MODE_3D_KEYS[mode.ordinal()]) : this.tr(MODE_KEYS[mode.ordinal()]);
    }

    private void coordinates(OsdConfig.Slot slot) {
        long x = Math.round(this.data.x);
        long y = Math.round(this.data.y);
        long z = Math.round(this.data.z);
        if (this.coordinatesText == null || x != this.coordinateX || y != this.coordinateY || z != this.coordinateZ) {
            this.coordinateX = x;
            this.coordinateY = y;
            this.coordinateZ = z;
            this.coordinatesText = this.tr(LABEL_XYZ) + " " + x + " " + y + " " + z;
        }
        this.text(OsdElement.COORDINATES, slot, this.coordinatesText, this.color);
    }

    private int alert(int rgb) {
        return this.config.coloredAlerts ? OsdColors.withAlpha(rgb, this.alpha) : this.color;
    }

    private int critical() {
        return this.alert(this.config.warningRgb());
    }

    private int caution() {
        return this.alert(AMBER);
    }

    private int good() {
        return this.alert(GREEN);
    }

    private int qualityColor(double quality) {
        if (quality < OsdWarnings.WEAK_LINK) {
            return this.critical();
        }
        if (quality < 0.6) {
            return this.caution();
        }
        return this.color;
    }

    private int batteryColor() {
        BatteryState state = this.data.batteryState;
        if (state == BatteryState.CRITICAL || state == BatteryState.EMPTY) {
            return this.critical();
        }
        if (state == BatteryState.LOW) {
            return this.caution();
        }
        return this.color;
    }

    private float textTop(int row) {
        return this.grid.rowCenterY(row) - OsdDraw.GLYPH_HEIGHT * this.scale * 0.5F;
    }

    private void text(OsdElement element, OsdConfig.Slot slot, String text, int textColor) {
        this.textAt(element, text, this.grid.anchorX(slot.col, element.align()), slot.row, element.align(), textColor);
    }

    private void textAt(OsdElement element, String text, float anchorX, int row, OsdElement.Align align, int textColor) {
        float width = this.draw.width(text, this.scale);
        float x = switch (align) {
            case LEFT -> anchorX;
            case CENTER -> anchorX - width * 0.5F;
            case RIGHT -> anchorX - width;
        };
        x = this.snap(x);
        float y = this.snap(this.textTop(row));
        this.textBox(x, y, width);
        this.draw.text(text, x, y, this.scale, textColor);
        this.record(element, x - this.scale, y - this.scale, x + width, y + (OsdDraw.GLYPH_HEIGHT + 2) * this.scale);
    }

    private void textBox(float x, float y, float width) {
        if (!this.draw.hasBackground()) {
            return;
        }
        float padX = 2.0F * this.scale;
        float padY = 1.5F * this.scale;
        this.draw.box(x - padX, y - padY, x + width + padX - this.scale, y + OsdDraw.CELL_HEIGHT * this.scale + padY, this.color);
    }

    private float snap(float value) {
        if (!(this.pixelScale > 0.0)) {
            return value;
        }
        return (float) (Math.round(value * this.pixelScale) / this.pixelScale);
    }

    private void record(OsdElement element, float x0, float y0, float x1, float y1) {
        float[] b = this.bounds.get(element);
        if (this.drawn.add(element)) {
            b[0] = x0;
            b[1] = y0;
            b[2] = x1;
            b[3] = y1;
        } else {
            b[0] = Math.min(b[0], x0);
            b[1] = Math.min(b[1], y0);
            b[2] = Math.max(b[2], x1);
            b[3] = Math.max(b[3], y1);
        }
    }

    private void recording(OsdConfig.Slot slot) {
        OsdData d = this.data;
        boolean saving = d.recording == OsdData.REC_SAVING;
        boolean paused = d.recording == OsdData.REC_PAUSED;
        String text = saving
                ? this.cachedText(OsdElement.RECORDING, LABEL_SAVING, SAVING_KEY + Math.clamp(d.recordingPercent, 0, 100), OsdRenderer::savingText)
                : this.cachedText(OsdElement.RECORDING, LABEL_REC, OsdFormat.durationKey(d.recordingSeconds), OsdFormat::recordingText);
        float textWidth = this.draw.width(text, this.scale);
        float mark = saving ? 0.0F : (REC_MARK + REC_GAP) * this.scale;
        float width = mark + textWidth;
        float anchor = this.grid.anchorX(slot.col, OsdElement.RECORDING.align());
        float x = switch (OsdElement.RECORDING.align()) {
            case LEFT -> anchor;
            case CENTER -> anchor - width * 0.5F;
            case RIGHT -> anchor - width;
        };
        x = this.snap(x);
        float y = this.snap(this.textTop(slot.row));
        this.textBox(x, y, width);
        if (!saving) {
            float markY = this.snap(y + (OsdDraw.GLYPH_HEIGHT - REC_MARK) * 0.5F * this.scale);
            this.draw.push(x, markY, 0.0F, this.scale);
            if (paused) {
                this.draw.rects(this.caution(), 0, 0, 2, REC_MARK, 3, 0, REC_MARK, REC_MARK);
            } else if (this.editor || recBlinkOn(d.recordingSeconds)) {
                this.draw.rects(this.recColor(), 1, 0, REC_MARK - 1, REC_MARK, 0, 1, REC_MARK, REC_MARK - 1);
            }
            this.draw.pop();
        }
        this.draw.text(text, x + mark, y, this.scale, paused ? this.caution() : saving ? this.color : this.recColor());
        this.record(OsdElement.RECORDING, x - this.scale, y - this.scale, x + width, y + (OsdDraw.GLYPH_HEIGHT + 2) * this.scale);
    }

    private void flashbackBudget(OsdConfig.Slot slot) {
        OsdData d = this.data;
        double fill = Math.max(0.0, d.flashbackFill);
        boolean over = fill > 1.0;
        boolean thinning = over && d.flashbackLimit;
        long percent = Math.round(Math.min(thinning ? 1.0 : 9.99, fill) * 100.0);
        int textColor = over && !thinning ? this.critical() : fill >= FlashbackBudget.CAUTION_FILL ? this.caution() : this.color;
        String text = this.cachedText(OsdElement.FLASHBACK_BUDGET, LABEL_FLASHBACK, percent + (thinning ? FLASHBACK_THINNING_KEY : 0L),
                key -> (key % FLASHBACK_THINNING_KEY) + "%");
        float textWidth = this.draw.width(text, this.scale);
        float mark = thinning ? (REC_GAP + THIN_MARK) * this.scale : 0.0F;
        float width = textWidth + mark;
        float anchor = this.grid.anchorX(slot.col, OsdElement.FLASHBACK_BUDGET.align());
        float x = switch (OsdElement.FLASHBACK_BUDGET.align()) {
            case LEFT -> anchor;
            case CENTER -> anchor - width * 0.5F;
            case RIGHT -> anchor - width;
        };
        x = this.snap(x);
        float y = this.snap(this.textTop(slot.row));
        this.textBox(x, y, width);
        boolean shown = this.editor || !over || thinning || OsdWarnings.blinkOn(d.blinkSeconds);
        if (shown) {
            this.draw.text(text, x, y, this.scale, textColor);
        }
        if (thinning) {
            float markX = this.snap(x + textWidth + REC_GAP * this.scale);
            float markY = this.snap(y + (OsdDraw.GLYPH_HEIGHT - THIN_MARK) * 0.5F * this.scale);
            this.draw.push(markX, markY, 0.0F, this.scale);
            this.draw.rects(textColor, 0, 0, THIN_MARK, 1, 1, 2, THIN_MARK - 1, 3, 2, 4, THIN_MARK - 2, THIN_MARK);
            this.draw.pop();
        }
        this.record(OsdElement.FLASHBACK_BUDGET, x - this.scale, y - this.scale, x + width, y + (OsdDraw.GLYPH_HEIGHT + 2) * this.scale);
    }

    private void tricks(OsdConfig.Slot slot) {
        OsdData d = this.data;
        if (d.trickType == null) {
            return;
        }
        double visible = d.trickPopup == OsdData.TRICK_LOST ? TRICK_LOST_SECONDS : TRICK_VISIBLE_SECONDS;
        double age = this.editor ? 0.0 : d.trickAgeSeconds;
        float fade = (float) Math.clamp((visible - age) / TRICK_FADE_SECONDS, 0.0, 1.0);
        float savedAlpha = this.alpha;
        int savedColor = this.color;
        this.alpha = savedAlpha * fade;
        this.color = OsdColors.withAlpha(this.config.elementRgb(OsdElement.TRICKS), this.alpha);
        try {
            int textColor = switch (d.trickPopup) {
                case OsdData.TRICK_COMBO -> this.good();
                case OsdData.TRICK_LOST -> this.caution();
                default -> this.color;
            };
            long key = trickKey(d);
            this.text(OsdElement.TRICKS, slot, this.cachedText(OsdElement.TRICKS, null, key, this::trickText), textColor);
        } finally {
            this.alpha = savedAlpha;
            this.color = savedColor;
        }
    }

    private static long trickKey(OsdData d) {
        long kind = d.trickPopup & 0x3L;
        long combo = Math.clamp(d.trickCombo, 0, 0x3FF);
        long points = Math.clamp(d.trickPoints, 0, 0xFFFFFF);
        long key = kind | combo << 2 | points << 12;
        int count = d.trickTypeCount > 0 ? Math.min(d.trickTypeCount, 3) : d.trickType == null ? 0 : 1;
        for (int i = 0; i < count; i++) {
            TrickType type = d.trickTypeCount > 0 ? d.trickTypes[i] : d.trickType;
            long code = type == null ? 0L : Math.min(type.ordinal() + 1, 0x7F);
            key |= code << (36 + 7 * i);
        }
        return key;
    }

    private String trickText(long key) {
        int kind = (int) (key & 0x3L);
        long combo = (key >>> 2) & 0x3FFL;
        long points = (key >>> 12) & 0xFFFFFFL;
        return switch (kind) {
            case OsdData.TRICK_COMBO -> this.tr(LABEL_TRICK_COMBO) + " X" + combo + " +" + points;
            case OsdData.TRICK_LOST -> this.tr(LABEL_TRICK_LOST);
            default -> this.trickNames(key) + " +" + points + (combo >= 2 ? "  X" + combo : "");
        };
    }

    private String trickNames(long key) {
        List<String> names = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            int code = (int) ((key >>> (36 + 7 * i)) & 0x7FL);
            if (code == 0) {
                break;
            }
            names.add(this.tr(TRICK_KEYS[Math.min(code - 1, TRICK_KEYS.length - 1)]));
        }
        return chainText(names);
    }

    static String chainText(List<String> names) {
        if (names.isEmpty()) {
            return "";
        }
        int first = names.size() - 1;
        int length = names.get(first).length();
        while (first > 0 && length + CHAIN_SEPARATOR.length() + names.get(first - 1).length() <= TRICK_NAME_BUDGET) {
            first--;
            length += CHAIN_SEPARATOR.length() + names.get(first).length();
        }
        String text = String.join(CHAIN_SEPARATOR, names.subList(first, names.size()));
        return first > 0 ? CHAIN_MORE + text : text;
    }

    private int recColor() {
        return OsdColors.withAlpha(this.config.warningRgb(), this.alpha);
    }

    static boolean recBlinkOn(double seconds) {
        return !Double.isFinite(seconds) || seconds % REC_BLINK_PERIOD < REC_BLINK_ON;
    }

    private static String savingText(long key) {
        return (key - SAVING_KEY) + "%";
    }

    private void crosshair() {
        float cx = this.grid.centerX();
        float cy = this.grid.centerY();
        this.draw.push(cx, cy, 0.0F, this.scale);
        this.draw.rects(this.color, -7, 0, -2, 1, 3, 0, 8, 1, 0, 0, 1, 1, 0, -4, 1, -2);
        this.draw.pop();
        this.record(OsdElement.CROSSHAIR, cx - 8 * this.scale, cy - 5 * this.scale, cx + 9 * this.scale, cy + 2 * this.scale);
    }

    private void artificialHorizon() {
        if (!this.projection.horizon(this.horizon)) {
            return;
        }
        float cx = this.grid.centerX();
        float cy = this.grid.centerY();
        float halfBoxW = this.grid.cellWidth() * 8.0F;
        float halfBoxH = this.grid.cellHeight() * 5.0F;
        int x0 = (int) Math.floor(cx - halfBoxW);
        int y0 = (int) Math.floor(cy - halfBoxH);
        int x1 = (int) Math.ceil(cx + halfBoxW);
        int y1 = (int) Math.ceil(cy + halfBoxH);
        this.record(OsdElement.ARTIFICIAL_HORIZON, x0, y0, x1, y1);
        this.graphics.enableScissor(x0, y0, x1, y1);
        try {
            int half = Math.max(8, Math.round(this.grid.cellWidth() * 4.5F / this.scale));
            int gap = 5;
            this.ladderLine(this.horizon.x, this.horizon.y, this.horizon.angle, half, gap, 0);
            if (this.config.pitchLadder && this.projection.horizonAzimuth(this.horizon, this.azimuth)) {
                int step = Math.round(this.config.ladderStepDeg);
                int rungHalf = Math.max(gap + 4, Math.round(half * 0.55F));
                for (int k = -3; k <= 3; k++) {
                    int elevation = k * step;
                    if (k == 0 || Math.abs(elevation) >= 90) {
                        continue;
                    }
                    if (this.projection.projectElevation(this.azimuth, elevation, this.point)) {
                        this.ladderLine(this.point.x, this.point.y, this.horizon.angle, rungHalf, gap, elevation);
                    }
                }
            }
        } finally {
            this.graphics.disableScissor();
        }
    }

    private void ladderLine(float x, float y, float angle, int half, int gap, int elevation) {
        this.draw.push(x, y, angle, this.scale);
        if (elevation == 0) {
            this.draw.rects(this.color,
                    -half, 0, -gap, 1, gap + 1, 0, half + 1, 1,
                    -half, 1, -half + 1, 3, half, 1, half + 1, 3);
        } else {
            int tickY0 = elevation > 0 ? 1 : -2;
            int tickY1 = elevation > 0 ? 3 : 0;
            if (elevation > 0) {
                this.draw.rects(this.color,
                        -half, 0, -gap, 1, gap + 1, 0, half + 1, 1,
                        -half, tickY0, -half + 1, tickY1, half, tickY0, half + 1, tickY1);
            } else {
                int dash = Math.max(1, (half - gap - 2) / 2);
                this.draw.rects(this.color,
                        -half, 0, -half + dash, 1, -gap - dash, 0, -gap, 1,
                        gap + 1, 0, gap + 1 + dash, 1, half + 1 - dash, 0, half + 1, 1,
                        -half, tickY0, -half + 1, tickY1, half, tickY0, half + 1, tickY1);
            }
            String number = Integer.toString(Math.abs(elevation));
            this.draw.text(number, half + 4, -3.5F * 0.7F, 0.7F, this.color);
        }
        this.draw.pop();
    }

    private void sidebars() {
        float cx = this.grid.centerX();
        float cy = this.grid.centerY();
        int offset = Math.round(this.grid.cellWidth() * 7.0F / this.scale);
        int rowStep = Math.max(3, Math.round(this.grid.cellHeight() / this.scale));
        this.draw.push(cx, cy, 0.0F, this.scale);
        int[] rects = this.rectBuffer;
        int n = 0;
        for (int i = -3; i <= 3; i++) {
            int y = i * rowStep;
            int length = i == 0 ? 5 : 3;
            rects[n++] = -offset - length;
            rects[n++] = y;
            rects[n++] = -offset;
            rects[n++] = y + 1;
            rects[n++] = offset + 1;
            rects[n++] = y;
            rects[n++] = offset + 1 + length;
            rects[n++] = y + 1;
        }
        this.draw.rects(this.color, rects, n / 4);
        this.draw.pop();
        this.record(OsdElement.HORIZON_SIDEBARS, cx - (offset + 6) * this.scale, cy - (3 * rowStep + 1) * this.scale,
                cx + (offset + 7) * this.scale, cy + (3 * rowStep + 2) * this.scale);
    }

    private void homeMarker() {
        if (!this.hasView) {
            return;
        }
        double dx = this.data.homeX - this.viewX;
        double dy = this.data.homeY + 0.5 - this.viewY;
        double dz = this.data.homeZ - this.viewZ;
        float margin = this.grid.cellHeight() * 1.5F;
        float left = this.grid.left() + margin;
        float right = this.grid.left() + this.grid.width() - margin;
        float top = this.grid.top() + margin;
        float bottom = this.grid.top() + this.grid.height() - margin;
        long distanceKey = OsdFormat.distanceKey(this.data.homeDistance(), this.unit);
        if (this.homeMarkerText == null || distanceKey != this.homeMarkerKey) {
            this.homeMarkerKey = distanceKey;
            this.homeMarkerText = OsdFormat.distanceText(distanceKey);
        }
        String distance = this.homeMarkerText;
        float smallScale = this.scale * 0.75F;
        boolean front = this.projection.project(dx, dy, dz, this.point);
        float x;
        float y;
        if (front && this.point.x >= left && this.point.x <= right && this.point.y >= top && this.point.y <= bottom) {
            x = this.point.x;
            y = this.point.y;
            this.draw.push(x, y, 0.0F, this.scale);
            this.draw.rects(this.color,
                    0, -6, 1, -5, -1, -5, 2, -4, -2, -4, 3, -3, -3, -3, 4, -2,
                    -2, -2, -1, 2, 2, -2, 3, 2, -2, 1, 3, 2);
            this.draw.pop();
        } else {
            float angle = this.projection.screenAngle(dx, dy, dz);
            float rx = (right - left) * 0.5F;
            float ry = (bottom - top) * 0.5F;
            x = this.grid.centerX() + (float) Math.cos(angle) * rx;
            y = this.grid.centerY() + (float) Math.sin(angle) * ry;
            this.draw.push(x, y, angle + (float) (Math.PI * 0.5), this.scale);
            this.arrowShape();
            this.draw.pop();
        }
        float textWidth = this.draw.width(distance, smallScale);
        this.draw.text(distance, x - textWidth * 0.5F, y + 4 * this.scale, smallScale, this.color);
        this.record(OsdElement.HOME_MARKER, x - Math.max(5 * this.scale, textWidth * 0.5F), y - 7 * this.scale,
                x + Math.max(5 * this.scale, textWidth * 0.5F), y + 4 * this.scale + 8 * smallScale);
    }

    private void arrowShape() {
        this.draw.rects(this.color,
                0, -4, 1, 4, -1, -3, 2, -2, -2, -2, 3, -1, -3, -1, 4, 0);
    }

    private void homeArrow(OsdConfig.Slot slot) {
        double relative = this.data.homeRelativeBearing();
        if (!Double.isFinite(relative)) {
            relative = 0.0;
        }
        float x = this.grid.anchorX(slot.col, OsdElement.Align.CENTER);
        float y = this.grid.rowCenterY(slot.row);
        this.draw.push(x, y, (float) Math.toRadians(relative), this.scale);
        this.arrowShape();
        this.draw.pop();
        this.record(OsdElement.HOME_DIRECTION, x - 5 * this.scale, y - 5 * this.scale, x + 5 * this.scale, y + 5 * this.scale);
    }

    private void compassBar(OsdConfig.Slot slot) {
        float cx = this.grid.anchorX(slot.col, OsdElement.Align.CENTER);
        float cy = this.grid.rowCenterY(slot.row) + this.scale;
        float halfWidth = this.grid.cellWidth() * 6.5F;
        double heading = this.data.compassDeg;
        this.record(OsdElement.COMPASS_BAR, cx - halfWidth, cy - 8 * this.scale, cx + halfWidth, cy + 3 * this.scale);
        this.draw.push(cx, cy, 0.0F, this.scale);
        float unitsPerDeg = halfWidth / this.scale / COMPASS_RANGE_DEG;
        if (Double.isFinite(heading)) {
            int first = (int) Math.ceil((heading - COMPASS_RANGE_DEG) / 15.0) * 15;
            int count = 0;
            for (int a = first; a <= heading + COMPASS_RANGE_DEG && count < this.rectBuffer.length / 4; a += 15) {
                int x = Math.round((float) ((a - heading) * unitsPerDeg));
                int wrapped = Math.floorMod(a, 360);
                if (wrapped % 45 == 0) {
                    String name = this.tr(COMPASS_KEYS[wrapped / 45]);
                    float labelScale = wrapped % 90 == 0 ? 1.0F : 0.7F;
                    float w = this.draw.width(name, labelScale);
                    this.draw.text(name, x - w * 0.5F + 0.5F, -OsdDraw.GLYPH_HEIGHT * labelScale, labelScale, this.color);
                } else {
                    int base = count * 4;
                    this.rectBuffer[base] = x;
                    this.rectBuffer[base + 1] = -3;
                    this.rectBuffer[base + 2] = x + 1;
                    this.rectBuffer[base + 3] = 0;
                    count++;
                }
            }
            this.draw.rects(this.color, this.rectBuffer, count);
        }
        this.draw.rects(this.color, 0, 1, 1, 3, -1, 2, 2, 3);
        this.draw.pop();
    }

    private void batteryBar(OsdConfig.Slot slot) {
        float x = this.grid.anchorX(slot.col, OsdElement.Align.LEFT);
        float y = this.grid.rowCenterY(slot.row) - 3.5F * this.scale;
        double charge = this.data.chargeFraction;
        int segments = OsdFormat.batteryBarSegments(charge, 10);
        int barColor = charge < 0.15 ? this.critical() : charge < 0.3 ? this.caution() : this.batteryColor();
        long percentKey = Double.isFinite(charge) ? OsdFormat.percent(charge) : OsdFormat.NONE;
        if (this.batteryPercentText == null || percentKey != this.batteryPercentKey) {
            this.batteryPercentKey = percentKey;
            this.batteryPercentText = percentKey == OsdFormat.NONE ? OsdFormat.UNKNOWN : percentKey + "%";
        }
        String percent = this.batteryPercentText;
        float textX = x + 40 * this.scale;
        float textWidth = this.draw.width(percent, this.scale);
        this.draw.box(x - 2.0F * this.scale, y - 2.0F * this.scale, textX + textWidth + this.scale, y + 9.0F * this.scale, this.color);
        this.draw.push(x, y, 0.0F, this.scale);
        this.draw.frame(0, 0, 35, 7, this.color);
        for (int i = 0; i < segments; i++) {
            this.draw.fill(2 + i * 3, 2, 4 + i * 3, 5, barColor);
        }
        this.draw.rects(this.color, 35, 2, 37, 5);
        this.draw.pop();
        this.draw.text(percent, textX, this.textTop(slot.row), this.scale, barColor);
        this.record(OsdElement.BATTERY_BAR, x - this.scale, y - this.scale, textX + textWidth, y + 8 * this.scale);
    }

    private void sticks(OsdConfig.Slot slot) {
        float x = this.grid.anchorX(slot.col, OsdElement.Align.CENTER);
        float y = this.grid.rowCenterY(slot.row);
        OsdData d = this.data;
        this.draw.push(x, y, 0.0F, this.scale);
        this.draw.frame(-19, -8, -3, 8, this.color);
        this.draw.frame(3, -8, 19, 8, this.color);
        int[] dots = stickDots(d, InputConfig.get().stickMode());
        System.arraycopy(dots, 0, this.shownSticks, 0, dots.length);
        int leftX = dots[0];
        int leftY = dots[1];
        int rightX = dots[2];
        int rightY = dots[3];
        this.draw.rects(this.color, leftX - 1, leftY - 1, leftX + 1, leftY + 1, rightX - 1, rightY - 1, rightX + 1, rightY + 1);
        this.draw.pop();
        this.record(OsdElement.STICK_OVERLAY, x - 20 * this.scale, y - 9 * this.scale, x + 20 * this.scale, y + 9 * this.scale);
    }

    static int[] stickDots(OsdData d, StickMode mode) {
        StickMode.Sticks sticks = StickMode.orDefault(mode).place(Math.clamp(d.stickRoll, -1.0F, 1.0F), Math.clamp(d.stickPitch, -1.0F, 1.0F),
                Math.clamp(d.stickYaw, -1.0F, 1.0F), Math.clamp(d.stickThrottle, 0.0F, 1.0F) * 2.0F - 1.0F);
        return new int[]{-11 + Math.round(sticks.leftX() * 6.0F), Math.round(-sticks.leftY() * 6.0F), 11 + Math.round(sticks.rightX() * 6.0F),
                Math.round(-sticks.rightY() * 6.0F)};
    }

    int[] shownSticks() {
        return this.shownSticks.clone();
    }

    private void armedStatus(OsdConfig.Slot slot) {
        boolean armed = this.data.armed;
        String key = armed ? "propwash.osd.status.armed" : "propwash.osd.status.disarmed";
        this.text(OsdElement.ARMED_STATUS, slot, this.tr(key), armed ? this.good() : this.color);
    }

    private void warning(OsdConfig.Slot slot) {
        OsdWarnings.Warning shown;
        int index = OsdWarnings.visibleIndex(this.warnings, this.data.blinkSeconds);
        if (index >= 0) {
            shown = this.warnings.get(index);
        } else if (this.editor) {
            shown = OsdWarnings.Warning.BATTERY_LOW;
        } else {
            return;
        }
        String phase = shown == OsdWarnings.Warning.RESCUE ? this.data.rescuePhase : "";
        if (shown != this.warningShown || phase != this.warningPhase) {
            this.warningShown = shown;
            this.warningPhase = phase;
            this.warningText = phase.isEmpty()
                    ? this.tr(shown.translationKey())
                    : this.tr(shown.translationKey()) + " " + phase.toUpperCase(Locale.ROOT);
        }
        String text = this.warningText;
        int warningColor = switch (shown.severity()) {
            case CRITICAL -> this.critical();
            case WARNING -> this.caution();
            case INFO -> this.color;
        };
        if (!this.editor && shown.blinks() && !OsdWarnings.blinkOn(this.data.blinkSeconds)) {
            float width = this.draw.width(text, this.scale);
            float x = this.grid.anchorX(slot.col, OsdElement.Align.CENTER) - width * 0.5F;
            float y = this.textTop(slot.row);
            this.record(OsdElement.WARNINGS, x, y, x + width, y + 9 * this.scale);
            return;
        }
        this.text(OsdElement.WARNINGS, slot, text, warningColor);
    }

    private void stats(OsdConfig.Slot slot) {
        FlightStats s = this.data.stats;
        SpeedUnit unit = this.unit;
        float anchor = this.grid.anchorX(slot.col, OsdElement.Align.CENTER);
        float halfWidth = this.grid.cellWidth() * 7.0F;
        int top = this.editor ? slot.row : OsdStatsLayout.blockTop(slot.row, this.statLines());
        this.textAt(OsdElement.POST_FLIGHT_STATS, this.tr("propwash.osd.stats.title"), anchor, top, OsdElement.Align.CENTER, this.color);
        this.stat(0, OsdFormat.durationKey(s.flightSeconds));
        this.stat(1, OsdFormat.quantize(OsdFormat.speed(s.maxSpeed, unit), 0));
        this.stat(2, OsdFormat.altitudeKey(s.maxAltitude, unit));
        this.stat(3, OsdFormat.distanceKey(s.maxDistance, unit));
        this.stat(4, OsdFormat.quantize(s.minCellVoltage, 2));
        this.stat(5, OsdFormat.quantize(s.maxCurrent, 1));
        this.stat(6, OsdFormat.quantize(s.mAhUsed, 0));
        this.stat(7, OsdFormat.quantize(s.maxG, 1));
        this.stat(8, s.crashes);
        this.stat(9, OsdCondition.key(this.data.wear));
        int count = this.statCount();
        if (count > STAT_BASE_COUNT) {
            this.stat(10, Math.max(0, s.trickScore));
            this.stat(11, s.bestCombo <= 0 ? -1L : ((long) s.bestCombo << 32) | (Math.max(0, s.bestComboPoints) & 0xFFFFFFFFL));
        }
        for (int i = 0; i < count; i++) {
            int row = top + 1 + i;
            if (row >= OsdGrid.ROWS) {
                break;
            }
            this.textAt(OsdElement.POST_FLIGHT_STATS, this.tr(STAT_KEYS[i]), anchor - halfWidth, row, OsdElement.Align.LEFT, this.color);
            this.textAt(OsdElement.POST_FLIGHT_STATS, this.statsText[i], anchor + halfWidth, row, OsdElement.Align.RIGHT, this.color);
        }
    }

    private void stat(int index, long key) {
        if (this.statsText[index] != null && this.statKeys[index] == key) {
            return;
        }
        this.statKeys[index] = key;
        this.statsText[index] = switch (index) {
            case 0 -> OsdFormat.durationText(key);
            case 1 -> this.speedText(key);
            case 2 -> OsdFormat.altitudeText(key);
            case 3 -> OsdFormat.distanceText(key);
            case 4 -> CELL_VOLTS_TEXT.apply(key);
            case 5 -> AMPS_TEXT.apply(key);
            case 6 -> MAH_TEXT.apply(key);
            case 7 -> G_TEXT.apply(key);
            case 8, 10 -> Long.toString(key);
            case 11 -> key < 0L ? "-" : "X" + (key >>> 32) + " · " + (key & 0xFFFFFFFFL);
            default -> this.conditionText(key);
        };
    }

    private String conditionText(long key) {
        if (key == OsdCondition.INTACT) {
            return this.tr("propwash.osd.stats.condition.ok");
        }
        return this.tr(OsdCondition.part(key).translationKey()) + " " + OsdCondition.percent(key) + "%";
    }
}
