package de.lardtom.propwash.rx.client;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.content.ItemHelp;
import de.lardtom.propwash.content.TooltipShift;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.rx.CreativeRxBlock;
import de.lardtom.propwash.rx.RxBlock;
import de.lardtom.propwash.rx.RxBlockEntity;
import de.lardtom.propwash.rx.RxLink;
import de.lardtom.propwash.rx.RxLinkState;
import de.lardtom.propwash.rx.RxModule;
import de.lardtom.propwash.rx.RxServer;
import de.lardtom.propwash.rx.RxSettings;
import de.lardtom.propwash.rx.RxSource;
import de.lardtom.propwash.rx.RxText;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.ui.client.widget.ValueSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static de.lardtom.propwash.rx.client.RxSelfTests.check;
import static de.lardtom.propwash.rx.client.RxSelfTests.flowFromSite;
import static de.lardtom.propwash.rx.client.RxSelfTests.ground;
import static de.lardtom.propwash.rx.client.RxSelfTests.phase;
import static de.lardtom.propwash.rx.client.RxSelfTests.player;
import static de.lardtom.propwash.rx.client.RxSelfTests.radio;
import static de.lardtom.propwash.rx.client.RxSelfTests.server;
import static de.lardtom.propwash.rx.client.RxSelfTests.site;
import static de.lardtom.propwash.rx.client.RxSelfTests.until;

public final class CreativeRxSelfTests {
    static final Identifier ID = Constants.id("creative_rx_block");
    static final int FAR_BLOCKS = 400;
    static final int AXIS = 7;
    private static final AtomicReference<BlockPos> FAR = new AtomicReference<>();
    private static final AtomicBoolean LOADED = new AtomicBoolean();

    private CreativeRxSelfTests() {
    }

    static @Nullable Block block() {
        Block block = BuiltInRegistries.BLOCK.getValue(ID);
        return block instanceof RxBlock ? block : null;
    }

    private static BlockPos creative() {
        BlockPos far = FAR.get();
        if (far == null) {
            throw new IllegalStateException("Platz des Kreativ-RX-Blocks fehlt");
        }
        return far;
    }

    private static BlockPos normal() {
        return creative().north(2);
    }

    private static void bind(ServerLevel level, BlockPos pos, UUID owner, int range) {
        if (level.getBlockEntity(pos) instanceof RxBlockEntity entity) {
            entity.apply(new RxSettings(owner, RxSource.axis(AXIS), -1.0F, 1.0F, range));
        }
    }

    private static String describe(BlockState state) {
        return state.getBlock() instanceof RxBlock ? state.getValue(RxBlock.LINK) + "/" + state.getValue(RxBlock.POWER) : state.toString();
    }

    private static StepResult retry(StepResult result, RxSelfTests.Run run, String waiting) {
        if (result.status() == StepResult.Status.FAIL && result.message().startsWith(waiting)) {
            run.future = null;
            return new StepResult(StepResult.Status.PENDING, result.message());
        }
        return result;
    }

    public static RxScreen previewScreen() {
        return RxScreen.preview(RxSelfTests.sampleView(), false, true);
    }

    private static int slot(List<ItemStack> tab, Item item) {
        for (int i = 0; i < tab.size(); i++) {
            if (tab.get(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean slider(RxScreen screen) {
        screen.init(427, 240);
        return screen.contentWidgets().stream().anyMatch(widget -> widget instanceof ValueSlider);
    }

    private static StepResult propertiesStep(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return StepResult.pending();
        }
        List<String> problems = new ArrayList<>();
        CreativeRxBlock block = RxModule.CREATIVE_RX_BLOCK.block().get();
        Item item = RxModule.CREATIVE_RX_BLOCK.item().get();
        BlockState state = block.defaultBlockState();
        Language language = Language.getInstance();
        List<ItemStack> tab = ContentTab.contents();
        int rx = slot(tab, RxModule.RX_BLOCK.item().get());
        int creative = slot(tab, item);
        if (rx < 0 || creative != rx + 1) {
            problems.add("im Kreativ-Tab nicht direkt nach dem RX-Block (RX " + rx + ", Kreativ " + creative + ")");
        }
        if (!RxBlock.unlimited(state) || RxBlock.unlimited(RxModule.RX_BLOCK.block().get().defaultBlockState())) {
            problems.add("Reichweite: Kreativ " + RxBlock.unlimited(state) + ", RX " + RxBlock.unlimited(RxModule.RX_BLOCK.block().get().defaultBlockState()));
        }
        if (!RxModule.RX_BLOCK_BE.get().isValid(state) || !(block.newBlockEntity(BlockPos.ZERO, state) instanceof RxBlockEntity)) {
            problems.add("ohne Block-Entity des RX-Blocks");
        }
        if (!(state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) < 0.0F) || block.getLootTable().isPresent()) {
            problems.add("im Überleben abbaubar oder mit Beute");
        }
        if (mc.getModelManager().getItemModel(BuiltInRegistries.ITEM.getKey(item)) instanceof MissingItemModel
                || mc.getModelManager().getBlockStateModelSet().getParticleMaterial(state).sprite().contents().name()
                .equals(MissingTextureAtlasSprite.getLocation())) {
            problems.add("Modell oder Partikel-Textur fehlt");
        }
        if (!language.has(block.getDescriptionId())) {
            problems.add("ohne Namen");
        }
        List<Component> brief = new ArrayList<>();
        List<Component> detail = new ArrayList<>();
        Item.TooltipContext context = Item.TooltipContext.of(mc.level);
        try {
            TooltipShift.override(false);
            item.appendHoverText(new ItemStack(item), context, TooltipDisplay.DEFAULT, brief::add, TooltipFlag.NORMAL);
            TooltipShift.override(true);
            item.appendHoverText(new ItemStack(item), context, TooltipDisplay.DEFAULT, detail::add, TooltipFlag.NORMAL);
        } finally {
            TooltipShift.override(null);
        }
        String first = RxText.tooltipLines(language.getOrDefault(RxText.CREATIVE_TOOLTIP)).getFirst();
        if (brief.isEmpty() || !brief.getFirst().getString().contains(first)) {
            problems.add("Kurzzeile „" + (brief.isEmpty() ? "" : brief.getFirst().getString()) + "“ statt „" + first + "“");
        }
        boolean range = false;
        for (Component line : detail) {
            if (line.getContents() instanceof TranslatableContents translatable) {
                if (!language.has(translatable.getKey())) {
                    problems.add("fehlende Übersetzung " + translatable.getKey());
                }
                range |= translatable.getKey().equals(ItemHelp.PREFIX + ID.getPath() + ".use.3");
            }
        }
        if (!range) {
            problems.add("Shift-Tooltip ohne die Zeile zur Reichweite");
        }
        RxScreen window = previewScreen();
        if (slider(window) || !slider(RxSelfTests.previewScreen()) || !window.unlimited()
                || !(window.getTitle().getContents() instanceof TranslatableContents title) || !title.getKey().equals(block.getDescriptionId())) {
            problems.add("Fenster: Kreativ mit Reichweiten-Regler oder falschem Titel „" + window.getTitle().getString() + "“");
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        ctx.log("Kreativ-Tab direkt nach dem RX-Block, unbegrenzt nur der Kreativ-RX-Block, Block-Entity des RX-Blocks, Härte −1, ohne Beute, "
                + "Modell, Name, Tooltip „" + first + " …“ mit Reichweiten-Zeile, Fenster „" + window.getTitle().getString()
                + "“ ohne Reichweiten-Regler");
        return StepResult.pass();
    }

    static void register() {
        SelfTest.clientStep(RxSelfTests.GROUP + "Kreativ-RX-Block Eigenschaften – Kreativ-Tab, unzerstörbar, ohne Beute, Tooltip, Fenster ohne "
                + "Reichweiten-Regler", 200, CreativeRxSelfTests::propertiesStep);
        SelfTest.clientStep(RxSelfTests.GROUP + "Kreativ-RX-Block – 400 Blöcke weg in derselben Dimension verbunden, der RX-Block daneben nicht, "
                + "lädt keine Chunks", 3600, flowFromSite(
                phase("Kreativ-RX-Block angemeldet", 20, (ctx, run) -> block() == null ? StepResult.fail(ID + " fehlt") : StepResult.pass()),
                phase("Chunk 400 Blöcke östlich laden", 200, (ctx, run) -> server(ctx, run, server -> {
                    ServerLevel level = player(server).level();
                    BlockPos far = ground(level, site().getX() + FAR_BLOCKS, site().getZ());
                    FAR.set(far);
                    level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
                })),
                phase("Kreativ-RX-Block mit Reichweite 1 und RX-Block mit 128 setzen und binden", 600, (ctx, run) -> retry(check(ctx, run, server -> {
                    ServerLevel level = player(server).level();
                    Block block = block();
                    if (block == null) {
                        return ID + " fehlt";
                    }
                    if (!RxServer.active(level, creative())) {
                        return "warte: noch nicht aktiv";
                    }
                    level.setBlock(creative(), block.defaultBlockState(), Block.UPDATE_ALL);
                    level.setBlock(normal(), RxModule.RX_BLOCK.block().get().defaultBlockState(), Block.UPDATE_ALL);
                    UUID owner = player(server).getUUID();
                    bind(level, creative(), owner, RxLink.MIN_RANGE);
                    bind(level, normal(), owner, RxLink.MAX_RANGE);
                    return level.getBlockEntity(creative()) instanceof RxBlockEntity ? null : "Kreativ-RX-Block ohne Block-Entity";
                }), run, "warte")),
                phase("AUX 8 auf 100 % – nur der Kreativ-RX-Block folgt", 600, (ctx, run) -> {
                    radio().setMicros(AXIS, 2000);
                    return retry(check(ctx, run, server -> {
                        ServerLevel level = player(server).level();
                        BlockState creative = level.getBlockState(creative());
                        BlockState normal = level.getBlockState(normal());
                        boolean followed = creative.getBlock() instanceof RxBlock && creative.getValue(RxBlock.POWER) == 15
                                && creative.getValue(RxBlock.LINK) == RxLinkState.LINKED;
                        boolean far = normal.getBlock() instanceof RxBlock && normal.getValue(RxBlock.POWER) == 0
                                && normal.getValue(RxBlock.LINK) == RxLinkState.LOST;
                        return followed && far ? null : "warte: Kreativ " + describe(creative) + ", RX " + describe(normal);
                    }), run, "warte");
                }),
                phase("AUX 8 auf 0 %", 400, (ctx, run) -> {
                    radio().setMicros(AXIS, 1000);
                    return retry(check(ctx, run, server -> {
                        BlockState creative = player(server).level().getBlockState(creative());
                        return creative.getBlock() instanceof RxBlock && creative.getValue(RxBlock.POWER) == 0
                                && creative.getValue(RxBlock.LINK) == RxLinkState.LINKED ? null : "warte: Kreativ " + describe(creative);
                    }), run, "warte");
                }),
                phase("Chunk freigeben und entladen", 800, (ctx, run) -> retry(check(ctx, run, server -> {
                    ServerLevel level = player(server).level();
                    BlockPos far = creative();
                    level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
                    return level.getChunkSource().hasChunk(far.getX() >> 4, far.getZ() >> 4) ? "warte: noch geladen" : null;
                }), run, "warte")),
                phase("AUX 8 bewegen – der Chunk bleibt entladen", 400, (ctx, run) -> {
                    int waited = run.waited(ctx);
                    if (waited == 0) {
                        LOADED.set(false);
                    }
                    radio().setMicros(AXIS, waited % 40 < 20 ? 2000 : 1000);
                    StepResult watched = server(ctx, run, server -> {
                        BlockPos far = creative();
                        if (player(server).level().getChunkSource().hasChunk(far.getX() >> 4, far.getZ() >> 4)) {
                            LOADED.set(true);
                        }
                    });
                    if (watched.status() == StepResult.Status.FAIL) {
                        return watched;
                    }
                    if (watched.status() == StepResult.Status.PASS) {
                        run.future = null;
                    }
                    if (LOADED.get()) {
                        return StepResult.fail("der Kreativ-RX-Block hat seinen Chunk geladen");
                    }
                    return until(waited > 120, "AUX 8 läuft");
                }),
                phase("aufräumen", 800, (ctx, run) -> {
                    radio().setMicros(AXIS, 1000);
                    StepResult done = retry(check(ctx, run, server -> {
                        ServerLevel level = player(server).level();
                        BlockPos far = creative();
                        level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
                        if (!RxServer.active(level, far)) {
                            return "warte: noch nicht aktiv";
                        }
                        BlockState kept = level.getBlockState(far);
                        level.setBlock(far, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        level.setBlock(normal(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        level.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
                        return kept.getBlock() instanceof RxBlock && kept.getValue(RxBlock.POWER) == 0 ? null
                                : "Zustand im entladenen Chunk geändert: " + describe(kept);
                    }), run, "warte");
                    if (done.status() == StepResult.Status.PASS) {
                        run.notes.add("400 Blöcke weg: Kreativ-RX-Block mit Reichweite 1 folgt AUX 8 (15 und 0), RX-Block mit 128 bleibt LOST bei 0; "
                                + "entladener Chunk blieb entladen, Stärke 0 gehalten");
                    }
                    return done;
                })));
    }
}
