package de.lardtom.propwash.video.client;

import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.Numbers;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.DroneContext;
import de.lardtom.propwash.ui.client.tab.UiTab;
import de.lardtom.propwash.ui.client.theme.Metrics;
import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.theme.ThemeText;
import de.lardtom.propwash.ui.client.theme.ThemedWidgets;
import de.lardtom.propwash.ui.client.widget.ChoiceButton;
import de.lardtom.propwash.ui.client.widget.FlatButton;
import de.lardtom.propwash.ui.client.widget.ScrollPanel;
import de.lardtom.propwash.video.VideoChannels;
import de.lardtom.propwash.video.VtxSettings;
import de.lardtom.propwash.video.VtxTables;
import de.lardtom.propwash.video.vtx.VtxHeat;
import de.lardtom.propwash.video.vtx.VtxPowerSettings;
import de.lardtom.propwash.video.vtx.VtxTable;
import de.lardtom.propwash.video.vtx.VtxUnit;
import de.lardtom.propwash.video.net.ChannelListS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChannelTab extends UiTab {
    public static final String ID = "video_channel";
    private static final int LIST_ROW = 20;
    private static final int TOGGLE_VALUE = 64;
    public static final int AUTO_POWER = -1;

    private final SpectateConfig working = SpectateConfig.get().copy();
    private final List<FlatButton> channelButtons = new ArrayList<>();
    private int column;
    private int vtxStatusTop;
    private int vtxInfoTop;
    private int powerTop;
    private int powerInfoTop;
    private int powerLiveTop = -1;
    private int watchTop;
    private int watchStatusTop;
    private int emptyTop = -1;
    private int hintTop;
    private int seenRevision = -1;
    private int seenPhase = -1;
    private @Nullable UUID seenTarget;
    private boolean seenGoggles;
    private @Nullable VideoSystem seenGogglesSystem;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("propwash.video.channel.tab");
    }

    @Override
    public int order() {
        return 505;
    }

    @Override
    public Storage storage() {
        return Storage.MIXED;
    }

    @Override
    public String mixedStorageKey() {
        return "propwash.video.channel.footer";
    }

    public SpectateConfig workingConfig() {
        return this.working;
    }

    public List<FlatButton> channelButtons() {
        return List.copyOf(this.channelButtons);
    }

    public boolean vtxEditable() {
        return this.context().hasDrone();
    }

    public VideoSystem droneSystem() {
        return this.context().build().video().system;
    }

    public int selectedChannel() {
        Map<String, Float> tune = this.tune().working();
        return VtxSettings.manual(tune) ? VtxSettings.channel(tune, this.droneSystem()) : VideoChannels.UNASSIGNED;
    }

    public int effectiveChannel() {
        return VtxSettings.channel(this.tune().working(), this.droneSystem());
    }

    public VtxTable vtxTable() {
        return VtxTables.of(this.context().build().video());
    }

    public int selectedPower() {
        VtxTable table = this.vtxTable();
        VtxPowerSettings.Selection selection = VtxPowerSettings.selection(this.tune().working(), table);
        return selection.auto() ? AUTO_POWER : table.levelMw(selection.index());
    }

    public void selectPower(int milliwatts) {
        this.tune().update(t -> milliwatts == AUTO_POWER ? VtxPowerSettings.withAuto(t) : VtxPowerSettings.withLevel(t, milliwatts), false);
    }

    public VtxPowerSettings.LowPower selectedLowPower() {
        return VtxPowerSettings.lowPower(this.tune().working());
    }

    public void selectLowPower(VtxPowerSettings.LowPower mode) {
        this.tune().update(t -> VtxPowerSettings.withLowPower(t, mode), false);
    }

    public void selectChannel(int channel) {
        VideoSystem system = this.droneSystem();
        this.tune().update(t -> channel < 0 ? VtxSettings.auto(t) : VtxSettings.with(t, VideoChannels.clamp(system, channel), true), false);
    }

    @Override
    protected void build() {
        this.channelButtons.clear();
        this.emptyTop = -1;
        ScrollPanel panel = this.beginScroll();
        int w = panel.innerWidth();
        this.column = Math.min(w, Metrics.COLUMN_MAX + 120);
        int left = this.x;
        int labelWidth = Math.min(this.column / 2, Math.max(Metrics.LABEL_MIN, Math.max(
                this.font().width(Component.translatable("propwash.video.channel.choice")),
                Math.max(this.font().width(Component.translatable("propwash.video.vtx.power")),
                        this.font().width(Component.translatable("propwash.video.vtx.low_power")))) + Metrics.PADDING));
        int cy = Metrics.SECTION_HEIGHT;
        this.vtxStatusTop = cy;
        cy += ThemeText.wrappedHeight(this.font(), this.vtxStatus(), this.column) + 6;
        VideoSystem system = this.droneSystem();
        List<Integer> values = new ArrayList<>();
        values.add(VideoChannels.UNASSIGNED);
        for (int i = 0; i < VideoChannels.count(system); i++) {
            values.add(i);
        }
        ChoiceButton<Integer> choice = this.add(new ChoiceButton<>(left, this.top() + cy, this.column, FIELD,
                Component.translatable("propwash.video.channel.choice"), labelWidth, values, this::selectedChannel,
                v -> {
                    this.selectChannel(v);
                    this.host.rebuild();
                },
                this::choiceName, () -> VideoChannels.UNASSIGNED));
        choice.setTooltip(Tooltip.create(Component.translatable("propwash.video.channel.choice.tip")));
        choice.active = this.vtxEditable();
        cy += ROW + 2;
        this.vtxInfoTop = cy;
        cy += ThemeText.wrappedHeight(this.font(), this.vtxInfo(), this.column) + 10;

        this.powerTop = cy;
        cy += Metrics.SECTION_HEIGHT;
        VtxTable table = this.vtxTable();
        List<Integer> powers = new ArrayList<>();
        if (table.supportsAuto()) {
            powers.add(AUTO_POWER);
        }
        for (int level : table.levels()) {
            powers.add(level);
        }
        int powerLabel = labelWidth;
        ChoiceButton<Integer> power = this.add(new ChoiceButton<>(left, this.top() + cy, this.column, FIELD,
                Component.translatable("propwash.video.vtx.power"), powerLabel, powers, this::selectedPower,
                v -> {
                    this.selectPower(v);
                    this.host.rebuild();
                },
                this::powerName, () -> table.supportsAuto() ? AUTO_POWER : table.ratedMw()));
        power.setTooltip(Tooltip.create(Component.translatable(table.digital() ? "propwash.video.vtx.power.tip_digital" : "propwash.video.vtx.power.tip_analog")));
        power.active = this.vtxEditable() && table.count() > 1;
        cy += ROW + 2;
        ChoiceButton<VtxPowerSettings.LowPower> low = this.add(new ChoiceButton<>(left, this.top() + cy, this.column, FIELD,
                Component.translatable("propwash.video.vtx.low_power"), powerLabel, List.of(VtxPowerSettings.LowPower.values()),
                this::selectedLowPower, v -> {
                    this.selectLowPower(v);
                    this.host.rebuild();
                },
                v -> Component.translatable("propwash.video.vtx.low_power." + v.name().toLowerCase(java.util.Locale.ROOT)),
                () -> VtxPowerSettings.LowPower.OFF));
        low.setTooltip(Tooltip.create(Component.translatable("propwash.video.vtx.low_power.tip")));
        low.active = this.vtxEditable() && table.count() > 1;
        cy += ROW + 2;
        this.powerInfoTop = cy;
        cy += ThemeText.wrappedHeight(this.font(), this.powerInfo(), this.column) + 4;
        if (this.liveUnit() != null) {
            this.powerLiveTop = cy;
            cy += this.font().lineHeight + 4;
        } else {
            this.powerLiveTop = -1;
        }
        cy += 6;

        this.watchTop = cy;
        cy += Metrics.SECTION_HEIGHT;
        this.watchStatusTop = cy;
        cy += ThemeText.wrappedHeight(this.font(), this.watchStatus(), this.column) + 6;
        Minecraft minecraft = this.host.minecraft();
        SpectateSession session = SpectateSession.INSTANCE;
        VideoSystem goggles = SpectateSession.goggles(minecraft);
        List<ChannelListS2C.Entry> entries = session.channels();
        if (entries.isEmpty()) {
            this.emptyTop = cy;
            cy += this.font().lineHeight + 8;
        }
        for (ChannelListS2C.Entry entry : entries) {
            boolean own = SpectateSession.ownFlight(entry, minecraft.player);
            boolean receivable = SpectateSession.receivable(entry, goggles);
            UUID target = session.target();
            boolean current = session.active() && entry.drone().equals(target);
            FlatButton button = this.add(new FlatButton(left, this.top() + cy, this.column, LIST_ROW - 2, this.rowText(entry, own, receivable),
                    current ? FlatButton.Style.PRIMARY : FlatButton.Style.NORMAL, () -> current, () -> this.watch(entry)));
            button.active = !own && receivable && session.allowed() && !current;
            button.setTooltip(ThemeText.tip(this.rowTooltip(entry, own, receivable, goggles)));
            this.channelButtons.add(button);
            cy += LIST_ROW;
        }
        if (session.active()) {
            this.add(new FlatButton(left, this.top() + cy, Math.min(this.column, 160), FIELD,
                    Component.translatable("propwash.video.spectate.stop"), FlatButton.Style.DANGER, () -> {
                SpectateSession.INSTANCE.stop(minecraft, true);
                this.host.rebuild();
            }));
            cy += ROW;
        }
        cy += 4;
        int toggleText = Math.max(this.font().width(Component.translatable("propwash.video.spectate.show_osd")),
                this.font().width(Component.translatable("propwash.video.spectate.show_banner")));
        int toggleLabel = Math.min(this.column - TOGGLE_VALUE, Math.max(Metrics.LABEL_MIN, toggleText + Metrics.PADDING * 2));
        ChoiceButton<Boolean> osd = this.add(ChoiceButton.toggle(left, this.top() + cy, this.column, FIELD,
                Component.translatable("propwash.video.spectate.show_osd"), toggleLabel, () -> this.working.showPilotOsd,
                v -> {
                    this.working.showPilotOsd = v;
                    this.host.changed();
                }, ThemedWidgets::onOff, () -> true));
        osd.setTooltip(Tooltip.create(Component.translatable("propwash.video.spectate.show_osd.tip")));
        cy += ROW;
        ChoiceButton<Boolean> banner = this.add(ChoiceButton.toggle(left, this.top() + cy, this.column, FIELD,
                Component.translatable("propwash.video.spectate.show_banner"), toggleLabel, () -> this.working.showBanner,
                v -> {
                    this.working.showBanner = v;
                    this.host.changed();
                }, ThemedWidgets::onOff, () -> true));
        banner.setTooltip(Tooltip.create(Component.translatable("propwash.video.spectate.show_banner.tip")));
        cy += ROW + 4;
        this.hintTop = cy;
        cy += ThemeText.wrappedHeight(this.font(), this.hint(), this.column) + 6;
        this.endScroll(cy);
        this.remember(minecraft);
    }

    private void remember(Minecraft minecraft) {
        SpectateSession session = SpectateSession.INSTANCE;
        this.seenRevision = session.listRevision();
        this.seenPhase = session.phase().ordinal();
        this.seenTarget = session.target();
        this.seenGogglesSystem = SpectateSession.goggles(minecraft);
        this.seenGoggles = true;
    }

    @Override
    public void tick() {
        SpectateSession session = SpectateSession.INSTANCE;
        Minecraft minecraft = this.host.minecraft();
        if (!this.seenGoggles) {
            return;
        }
        UUID target = session.target();
        boolean targetChanged = target == null ? this.seenTarget != null : !target.equals(this.seenTarget);
        if (session.listRevision() != this.seenRevision || session.phase().ordinal() != this.seenPhase || targetChanged
                || SpectateSession.goggles(minecraft) != this.seenGogglesSystem) {
            this.host.rebuild();
        }
    }

    private void watch(ChannelListS2C.Entry entry) {
        Minecraft minecraft = this.host.minecraft();
        if (this.host instanceof ConfiguratorScreen screen) {
            screen.saveAndClose();
            if (minecraft.gui.screen() == screen) {
                return;
            }
            if (minecraft.gui.screen() != null && minecraft.level != null) {
                minecraft.gui.setScreen(null);
            }
        }
        SpectateSession.INSTANCE.request(minecraft, entry);
    }

    private Component choiceName(Integer value) {
        VideoSystem system = this.droneSystem();
        if (value == null || value < 0) {
            int effective = this.effectiveChannel();
            return effective >= 0
                    ? Component.translatable("propwash.video.channel.auto_with", VideoChannels.label(system, effective))
                    : Component.translatable("propwash.video.channel.auto");
        }
        return Component.translatable("propwash.video.channel.label", VideoChannels.label(system, value),
                VideoChannels.frequencyMhz(system, value));
    }

    private Component rowText(ChannelListS2C.Entry entry, boolean own, boolean receivable) {
        MutableComponent text = Component.empty().append(SpectateSession.channelName(entry)).append(" · ")
                .append(SpectateSession.pilotName(entry)).append(" · ").append(SpectateSession.droneName(entry));
        if (own) {
            text.append(" ").append(Component.translatable("propwash.video.spectate.own"));
        } else if (!receivable) {
            text.append(" · ").append(Component.translatable("propwash.video.spectate.needs."
                    + VideoChannels.tableOf(entry.system()).name().toLowerCase(java.util.Locale.ROOT)));
        }
        return text;
    }

    private Component rowTooltip(ChannelListS2C.Entry entry, boolean own, boolean receivable, @Nullable VideoSystem goggles) {
        MutableComponent tip = Component.empty().append(ThemeText.accent(SpectateSession.channelName(entry))).append("\n")
                .append(Component.translatable("propwash.video.spectate.system." + VideoChannels.tableOf(entry.system()).name().toLowerCase(java.util.Locale.ROOT)));
        if (own) {
            tip.append("\n").append(ThemeText.muted(Component.translatable("propwash.video.spectate.own_tip")));
        } else if (goggles == null) {
            tip.append("\n").append(ThemeText.colored(Component.translatable("propwash.video.spectate.result.no_goggles"), Palette.WARNING));
        } else if (!receivable) {
            tip.append("\n").append(ThemeText.colored(Component.translatable("propwash.video.spectate.result.wrong_system"), Palette.WARNING));
        } else {
            tip.append("\n").append(ThemeText.muted(Component.translatable("propwash.video.spectate.click")));
        }
        return tip;
    }

    private Component vtxStatus() {
        DroneContext context = this.context();
        if (!context.hasDrone()) {
            return Component.translatable("propwash.video.channel.no_drone");
        }
        VideoSystem system = this.droneSystem();
        return Component.translatable("propwash.video.channel.status", context.name(),
                Component.translatable("propwash.video.spectate.system." + VideoChannels.tableOf(system).name().toLowerCase(java.util.Locale.ROOT)));
    }

    private Component vtxInfo() {
        if (this.selectedChannel() >= 0) {
            return Component.translatable("propwash.video.channel.info_manual");
        }
        boolean digital = this.context().hasDrone() && VideoChannels.tableOf(this.droneSystem()) == VideoSystem.DIGITAL;
        return Component.translatable(digital ? "propwash.video.channel.info_auto_digital" : "propwash.video.channel.info_auto");
    }

    private Component powerName(Integer value) {
        if (value == AUTO_POWER) {
            return Component.translatable("propwash.video.vtx.default", Component.translatable("propwash.video.vtx.auto"));
        }
        return Component.translatable("propwash.video.vtx.milliwatts", value);
    }

    private @Nullable VtxUnit liveUnit() {
        QuadcopterEntity drone = PilotSession.INSTANCE.drone();
        return this.context().hasDrone() ? VtxRuntime.INSTANCE.unit(drone) : null;
    }

    private Component powerInfo() {
        VtxTable table = this.vtxTable();
        if (!this.context().hasDrone()) {
            return Component.translatable("propwash.video.vtx.info_none");
        }
        Component key = VtxKeys.STEP.getTranslatedKeyMessage();
        if (table.count() < 2) {
            return Component.translatable("propwash.video.vtx.info_single", table.ratedMw());
        }
        int selected = this.selectedPower();
        if (selected == AUTO_POWER) {
            return Component.translatable(table.digital() ? "propwash.video.vtx.info_auto" : "propwash.video.vtx.info_auto_analog",
                    table.minMw(), table.maxMw(), key);
        }
        int index = table.nearestIndex(selected);
        int range = (int) Math.round(this.context().build().video().rangeBlocks * table.reachFactor(index));
        MutableComponent text = Component.translatable("propwash.video.vtx.info_level", selected, range,
                Numbers.of(table.supplyW(index), 1), key);
        VtxHeat heat = VtxHeat.of(table, VtxHeat.REFERENCE_AMBIENT_C);
        double seconds = heat.secondsToLimit(table.heatW(index), 0.0, VtxHeat.REFERENCE_AMBIENT_C);
        if (Double.isFinite(seconds)) {
            text.append(" ").append(Component.translatable("propwash.video.vtx.info_heat", Numbers.of(Math.max(0.5, seconds / 60.0), 1)));
        }
        if (!table.digital()) {
            text.append(" ").append(Component.translatable("propwash.video.vtx.info_analog"));
        }
        return text;
    }

    private Component powerLive(VtxUnit unit) {
        MutableComponent text = Component.translatable("propwash.video.vtx.live", unit.effectiveMw(), Numbers.of(unit.tempC(), 0),
                Numbers.of(unit.supplyW(), 1));
        if (unit.throttled()) {
            text.append(" · ").append(Component.translatable("propwash.video.vtx.live.throttled"));
        } else if (unit.lowPowerActive()) {
            text.append(" · ").append(Component.translatable("propwash.video.vtx.live.low_power"));
        } else if (unit.auto()) {
            text.append(" · ").append(Component.translatable("propwash.video.vtx.auto"));
        }
        return text;
    }

    private Component watchStatus() {
        Minecraft minecraft = this.host.minecraft();
        SpectateSession session = SpectateSession.INSTANCE;
        if (!session.allowed()) {
            return Component.translatable("propwash.video.spectate.result.rule_off");
        }
        VideoSystem goggles = SpectateSession.goggles(minecraft);
        if (goggles == null) {
            return Component.translatable("propwash.video.spectate.goggles_none");
        }
        return Component.translatable("propwash.video.spectate.goggles",
                Component.translatable("propwash.video.spectate.receives." + goggles.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private boolean watchWarning() {
        return !SpectateSession.INSTANCE.allowed() || SpectateSession.goggles(this.host.minecraft()) == null;
    }

    private Component hint() {
        return Component.translatable("propwash.video.spectate.tab_hint", SpectateKeys.CYCLE.getTranslatedKeyMessage());
    }

    @Override
    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int left = this.x;
        int top = this.top();
        this.section(g, Component.translatable("propwash.video.channel.section.vtx"), left, top, this.column);
        ThemeText.wrapped(g, this.font(), this.vtxStatus(), left, top + this.vtxStatusTop, this.column,
                this.vtxEditable() ? Palette.TEXT_MUTED : Palette.WARNING);
        ThemeText.wrapped(g, this.font(), this.vtxInfo(), left, top + this.vtxInfoTop, this.column, Palette.TEXT_FAINT);
        this.section(g, Component.translatable("propwash.video.vtx.section"), left, top + this.powerTop, this.column);
        ThemeText.wrapped(g, this.font(), this.powerInfo(), left, top + this.powerInfoTop, this.column, Palette.TEXT_FAINT);
        VtxUnit live = this.liveUnit();
        if (live != null && this.powerLiveTop >= 0) {
            ThemeText.text(g, this.font(), this.powerLive(live), left, top + this.powerLiveTop,
                    live.throttled() || live.hot() ? Palette.WARNING : Palette.TEXT_MUTED);
        }
        this.section(g, Component.translatable("propwash.video.channel.section.watch"), left, top + this.watchTop, this.column);
        ThemeText.wrapped(g, this.font(), this.watchStatus(), left, top + this.watchStatusTop, this.column,
                this.watchWarning() ? Palette.WARNING : Palette.TEXT_MUTED);
        if (this.emptyTop >= 0) {
            ThemeText.text(g, this.font(), Component.translatable("propwash.video.spectate.none"), left,
                    top + this.emptyTop, Palette.TEXT_FAINT);
        }
        ThemeText.wrapped(g, this.font(), this.hint(), left, top + this.hintTop, this.column, Palette.TEXT_FAINT);
        this.extractScrollbar(g);
    }

    @Override
    public void resetDefaults() {
        if (this.vtxEditable()) {
            this.tune().update(t -> VtxPowerSettings.withLowPower(VtxPowerSettings.withLevel(VtxSettings.auto(t), 0), VtxPowerSettings.LowPower.OFF), false);
        }
        this.working.showPilotOsd = true;
        this.working.showBanner = true;
        this.host.changed();
        this.host.rebuild();
    }

    @Override
    public void save() {
        SpectateConfig current = SpectateConfig.get();
        if (current.showPilotOsd != this.working.showPilotOsd || current.showBanner != this.working.showBanner) {
            SpectateConfig.CONFIG.set(this.working.copy());
        }
    }

    @Override
    public void discard() {
    }
}
