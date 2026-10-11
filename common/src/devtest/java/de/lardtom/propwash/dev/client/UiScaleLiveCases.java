package de.lardtom.propwash.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.VideoSystem;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.UiScaleSelfTests.Case;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.input.ProfileDefaults;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.input.client.screen.ReceiverTab;
import de.lardtom.propwash.ui.LayoutAudit;
import de.lardtom.propwash.ui.LayoutAudit.Finding;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.LayoutProbe;
import de.lardtom.propwash.ui.client.PilotGuard;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import de.lardtom.propwash.ui.client.tab.OverviewTab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class UiScaleLiveCases {
    static final String VIDEO = "video";
    static final String FPV = "Konfigurator im Flug";
    static final String FPV_RADIO = "Konfigurator im Flug mit Funke";
    static final String SIGHT_LONG_RADIO = "Konfigurator im Sichtflug mit langer Funke";
    static final String FPV_ANALOG = "Konfigurator im Flug mit analoger Brille";
    private static final String RADIO_GUID = "propwash-ui-scale-live-radio";
    private static final String RADIO_NAME = "OpenTX Radiomaster Boxer Joystick";
    private static final String LONG_RADIO = "Team-BlackSheep TBS Tango 2 Pro Joystick";
    private static final int RADIO_AXES = 8;
    private static final int RADIO_BUTTONS = 24;

    private UiScaleLiveCases() {
    }

    static List<Case> all() {
        return List.of(
                new Live(FPV, PilotMode.FPV, DroneBuild.FREESTYLE, null, List.of(OverviewTab.ID, ReceiverTab.ID, VIDEO)),
                new Live(FPV_RADIO, PilotMode.FPV, DroneBuild.FREESTYLE, RADIO_NAME, List.of(ReceiverTab.ID, OverviewTab.ID)),
                new Live(SIGHT_LONG_RADIO, PilotMode.LINE_OF_SIGHT, DroneBuild.FREESTYLE, LONG_RADIO, List.of(ReceiverTab.ID, OverviewTab.ID)),
                new Live(FPV_ANALOG, PilotMode.FPV, DroneBuild.RACER, null, List.of(VIDEO)));
    }

    private static final class Live implements Case {
        private static final String START = "start";
        private static final String TRIED = "tried";
        private static final String OPENED = "opened";
        private static final int RETRY = 10;
        private static final double DISTANCE = 4.0;
        private final String name;
        private final PilotMode mode;
        private final DroneBuild build;
        private final @Nullable String radio;
        private final List<String> tabs;
        private volatile int droneId = -1;
        private volatile @Nullable ItemStack previousHead;
        private volatile @Nullable ItemStack previousHand;
        private String shown = "";

        private Live(String name, PilotMode mode, DroneBuild build, @Nullable String radio, List<String> tabs) {
            this.name = name;
            this.mode = mode;
            this.build = build;
            this.radio = radio;
            this.tabs = tabs;
        }

        @Override
        public String name() {
            return this.name;
        }

        @Override
        public StepResult open(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (ctx.tick() == 0) {
                PilotSession.INSTANCE.clearTestInput();
                String radioName = this.radio;
                if (radioName != null) {
                    TransmitterInput.INSTANCE.simulate(new SimulatedRadio(RADIO_GUID, radioName, ProfileDefaults.radio(radioName, RADIO_AXES), RADIO_AXES,
                            RADIO_BUTTONS));
                }
                ctx.state().put(START, ctx.onServer(this::start));
                return StepResult.pending();
            }
            CompletableFuture<?> start = (CompletableFuture<?>) ctx.state().get(START);
            if (start == null) {
                return StepResult.fail("Pilotensitzung nicht angefordert");
            }
            if (start.isCompletedExceptionally()) {
                return StepResult.fail("Pilotensitzung startet nicht: " + start.exceptionNow().getMessage());
            }
            PilotSession session = PilotSession.INSTANCE;
            QuadcopterEntity drone = session.drone();
            if (!start.isDone() || drone == null || drone.getId() != this.droneId || session.sim() == null) {
                return StepResult.pending();
            }
            if (this.radio != null && TransmitterInput.INSTANCE.raw() == null) {
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen)) {
                int tried = (int) ctx.state().getOrDefault(TRIED, -RETRY);
                if (ctx.tick() - tried >= RETRY && PilotGuard.evaluate().opens()) {
                    ctx.state().put(TRIED, ctx.tick());
                    ConfigTabs.open(null, null);
                }
                return StepResult.pending();
            }
            int opened = (int) ctx.state().computeIfAbsent(OPENED, key -> ctx.tick());
            return ctx.tick() - opened >= 3 ? StepResult.pass() : StepResult.pending();
        }

        private void start(MinecraftServer server) {
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) {
                throw new IllegalStateException("kein Spieler auf dem Server");
            }
            ServerPlayer player = players.getFirst();
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            ServerLevel level = player.level();
            QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
            if (drone == null) {
                throw new IllegalStateException("EntityType.create lieferte null");
            }
            Vec3 feet = player.position();
            drone.snapTo(feet.x, feet.y, feet.z + DISTANCE, 0.0F, 0.0F);
            drone.setAttitude(new Quaternionf());
            drone.setBuild(this.build);
            if (!level.addFreshEntity(drone)) {
                throw new IllegalStateException("addFreshEntity fehlgeschlagen");
            }
            this.droneId = drone.getId();
            this.previousHead = player.getItemBySlot(EquipmentSlot.HEAD).copy();
            this.previousHand = player.getMainHandItem().copy();
            if (this.mode == PilotMode.FPV) {
                ItemStack goggles = ContentIds.item(this.build.video().system == VideoSystem.DIGITAL ? ContentIds.GOGGLES_DIGITAL : ContentIds.GOGGLES_ANALOG)
                        .map(ItemStack::new).orElse(ItemStack.EMPTY);
                if (goggles.isEmpty()) {
                    throw new IllegalStateException("Brille nicht registriert");
                }
                goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
                player.setItemSlot(EquipmentSlot.HEAD, goggles);
            } else {
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            }
            ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
            remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
            player.setItemSlot(EquipmentSlot.MAINHAND, remote);
            DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, this.mode);
            if (result != DronePiloting.StartResult.STARTED) {
                throw new IllegalStateException("Start verweigert: " + result);
            }
        }

        private void stop(MinecraftServer server) {
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            int id = this.droneId;
            this.droneId = -1;
            if (players.isEmpty()) {
                return;
            }
            ServerPlayer player = players.getFirst();
            DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
            for (ServerLevel level : server.getAllLevels()) {
                if (id >= 0 && level.getEntity(id) instanceof QuadcopterEntity drone) {
                    drone.discard();
                }
            }
            ItemStack head = this.previousHead;
            if (head != null) {
                player.setItemSlot(EquipmentSlot.HEAD, head);
            }
            ItemStack hand = this.previousHand;
            if (hand != null) {
                player.setItemSlot(EquipmentSlot.MAINHAND, hand);
            }
            this.previousHead = null;
            this.previousHand = null;
        }

        @Override
        public int pages(Screen screen) {
            return this.tabs.size();
        }

        @Override
        public String page(Screen screen, int index) {
            String tab = this.tabs.get(index);
            this.shown = tab;
            if (screen instanceof ConfiguratorScreen configurator) {
                configurator.selectTab(tab);
                configurator.setFocused(null);
                configurator.keyPressed(new KeyEvent(InputConstants.KEY_HOME, 0, 0));
            }
            return tab;
        }

        @Override
        public int settleTicks() {
            return 4;
        }

        @Override
        public List<Finding> audit(Minecraft mc, Screen screen) {
            List<Finding> findings = new ArrayList<>(LayoutProbe.audit(mc, screen));
            String header = "Kopfzeile des Konfigurators";
            if (!PilotSession.INSTANCE.isActive() || PilotSession.INSTANCE.sim() == null) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, header, "Pilotensitzung endete vor der Prüfung"));
                return findings;
            }
            LayoutProbe.Frame frame = LayoutProbe.capture(mc, screen);
            for (String key : List.of("propwash.ui.header.live", "propwash.ui.header.lockout")) {
                this.expect(findings, frame, header, Component.translatable(key).getString());
            }
            if (this.shown.equals(OverviewTab.ID)) {
                for (String key : List.of("state", "battery", "consumption", "speed_height", "attitude")) {
                    this.expect(findings, frame, "Telemetrie in der Übersicht", Component.translatable("propwash.ui.overview." + key).getString());
                }
            }
            if (this.shown.equals(VIDEO)) {
                boolean digital = this.build.video().system == VideoSystem.DIGITAL;
                String last = Component.translatable("propwash.video.config.preview." + (digital ? "lost" : "static")).getString();
                if (frame.texts().stream().noneMatch(text -> text.value().endsWith(last))) {
                    findings.add(new Finding(LayoutAudit.Kind.HIDDEN, "Vorschau im Tab Video", "Beschriftung „" + last + "“ fehlt"));
                }
                if (digital) {
                    this.expect(findings, frame, "Vorschau im Tab Video", Component.translatable("propwash.video.no_video").getString());
                }
            }
            return findings;
        }

        private void expect(List<Finding> findings, LayoutProbe.Frame frame, String subject, String text) {
            if (frame.texts().stream().noneMatch(shownText -> shownText.value().equals(text))) {
                findings.add(new Finding(LayoutAudit.Kind.HIDDEN, subject, "„" + text + "“ fehlt in der Pilotensitzung"));
            }
        }

        @Override
        public void frame(Screen screen, String page) {
            Minecraft.getInstance().gui.toastManager().clear();
            if (page.equals(OverviewTab.ID) || page.equals(VIDEO)) {
                screen.keyPressed(new KeyEvent(InputConstants.KEY_END, 0, 0));
            }
        }

        @Override
        public boolean closed() {
            return !PilotSession.INSTANCE.isActive();
        }

        @Override
        public boolean between() {
            return true;
        }

        @Override
        public void close(SelfTest.StepContext ctx) {
            if (Minecraft.getInstance().gui.screen() instanceof ConfiguratorScreen configurator) {
                configurator.discardAndClose();
            }
            PilotSession.INSTANCE.clearTestInput();
            ctx.onServer(this::stop);
            if (this.radio != null) {
                TransmitterInput.INSTANCE.simulate(null);
            }
        }
    }
}
