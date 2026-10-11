package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.dev.client.ShowcaseTour;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.input.DeviceProfile;
import de.lardtom.propwash.input.InputConfig;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.input.SwitchFunction;
import de.lardtom.propwash.input.client.SimulatedRadio;
import de.lardtom.propwash.input.client.TransmitterInput;
import de.lardtom.propwash.input.client.screen.ModesTab;
import de.lardtom.propwash.osd.OsdConfig;
import de.lardtom.propwash.osd.OsdElement;
import de.lardtom.propwash.osd.client.OsdClient;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import de.lardtom.propwash.ui.client.ConfiguratorScreen;
import de.lardtom.propwash.ui.client.config.ConfigTab;
import de.lardtom.propwash.ui.client.config.ConfigTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

public final class Mode3dShowcase {
    private static final int OFFSET_X = -70;
    private static final int OFFSET_Z = 70;

    private Mode3dShowcase() {
    }

    public static void register() {
        Screenshots.step("bildschirmfotos: Modi-Tab mit freigeschaltetem 3D-Modus auf einem Funkenschalter", "configurator_modes_3d", 20, new ModesScene());
        Screenshots.step("bildschirmfotos: 3D-Modus – kopfüber schweben im Sichtflug mit Funk-HUD", "mode3d_inverted_los", 30,
                new FlightScene(PilotMode.LINE_OF_SIGHT, 2.0, 2.4));
        Screenshots.step("bildschirmfotos: 3D-Modus – kopfüber schweben im FPV mit OSD", "mode3d_inverted_fpv", 30,
                new FlightScene(PilotMode.FPV, 3.0, 4.0));
        SelfTest.switchStep("bildschirmfotos: 3D-Modus – Sprache Deutsch", "bildschirmfotos: 3D-Modus – Sprache zurücksetzen", 1200,
                ctx -> Screenshots.language(ctx, "de_de"));
        Screenshots.step("bildschirmfotos: Modi-Tab mit freigeschaltetem 3D-Modus deutsch", "configurator_modes_3d_de", 20, new ModesScene());
        Screenshots.step("bildschirmfotos: 3D-Modus – kopfüber im Sichtflug mit Funk-HUD deutsch", "mode3d_inverted_los_de", 30,
                new FlightScene(PilotMode.LINE_OF_SIGHT, 2.0, 2.4));
        SelfTest.clientStep("bildschirmfotos: 3D-Modus – Sprache zurücksetzen", 1200, Screenshots::restoreLanguage);
    }

    private static final class ModesScene implements Screenshots.Scene {
        private @Nullable SimulatedRadio radio;
        private @Nullable Boolean unlocked;

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (this.radio == null) {
                SimulatedRadio fake = Mode3dSelfTests.radio("Radiomaster Pocket");
                fake.setMicros(Mode3dSelfTests.MODE_3D_AXIS, 2000);
                fake.setMicros(Mode3dSelfTests.THROTTLE_AXIS, 1500);
                this.radio = fake;
                InputConfig config = InputConfig.get();
                this.unlocked = config.mode3dUnlocked;
                config.mode3dUnlocked = true;
                DeviceProfile profile = fake.profile().copy();
                config.profiles.put(Mode3dSelfTests.RADIO_GUID, profile);
                TransmitterInput.INSTANCE.simulate(fake);
                return StepResult.pending();
            }
            if (!(mc.gui.screen() instanceof ConfiguratorScreen screen)) {
                ConfigTabs.open(null, ModesTab.ID);
                return ctx.tick() > 200 ? StepResult.fail("Konfigurator öffnet nicht") : StepResult.pending();
            }
            ConfigTab active = screen.activeTab();
            if (!(active instanceof ModesTab tab)) {
                screen.selectTab(ModesTab.ID);
                return StepResult.pending();
            }
            if (tab.selected() != SwitchFunction.MODE_3D) {
                tab.select(SwitchFunction.MODE_3D);
                return StepResult.pending();
            }
            return ctx.tick() > 20 ? StepResult.pass() : StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Screenshots.parkMouse(Minecraft.getInstance());
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof ConfiguratorScreen screen) {
                screen.discardAndClose();
            }
            TransmitterInput.INSTANCE.simulate(null);
            InputConfig config = InputConfig.get();
            config.profiles.remove(Mode3dSelfTests.RADIO_GUID);
            if (this.unlocked != null) {
                config.mode3dUnlocked = this.unlocked;
            }
            this.radio = null;
            this.unlocked = null;
        }
    }

    private static final class FlightScene implements Screenshots.Scene {
        private final PilotMode mode;
        private final double height;
        private final double distance;
        private Mode3dSelfTests.@Nullable Script script;
        private @Nullable Boolean unlocked;
        private @Nullable Boolean throttleShown;
        private int stage;
        private int since;
        private double ground;
        private volatile @Nullable Vec3 returnTo;
        private volatile float returnYaw;
        private volatile float returnPitch;

        private FlightScene(PilotMode mode, double height, double distance) {
            this.mode = mode;
            this.height = height;
            this.distance = distance;
        }

        @Override
        public StepResult prepare(SelfTest.StepContext ctx) {
            PilotSession session = PilotSession.INSTANCE;
            Mode3dSelfTests.Script s = this.script;
            if (s == null) {
                s = new Mode3dSelfTests.Script(Mode3dSelfTests.Kind.INVERTED, List.of());
                this.script = s;
                this.stage = 0;
                this.since = ctx.tick();
                this.unlocked = InputConfig.get().mode3dUnlocked;
                InputConfig.get().mode3dUnlocked = true;
                OsdConfig.Slot throttle = OsdClient.CONFIG.get().slot(OsdElement.THROTTLE);
                this.throttleShown = throttle.enabled;
                throttle.enabled = true;
                session.clearTestInput();
                Mode3dSelfTests.Script script = s;
                BlockPos base = ShowcaseTour.base();
                s.pending = ctx.onServer(server -> {
                    ServerPlayer player = Mode3dSelfTests.firstPlayer(server);
                    ServerLevel level = player.level();
                    this.returnTo = player.position();
                    this.returnYaw = player.getYRot();
                    this.returnPitch = player.getXRot();
                    int x = base.getX() + OFFSET_X;
                    int z = base.getZ() + OFFSET_Z;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    for (int dx = -5; dx <= 5; dx++) {
                        for (int dz = -3; dz <= 10; dz++) {
                            for (int dy = 0; dy <= 12; dy++) {
                                BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
                                if (!level.getBlockState(pos).isAir()) {
                                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                                }
                            }
                        }
                    }
                    this.ground = y;
                    player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), 0.0F, -12.0F, true);
                    Mode3dSelfTests.spawnAndFly(server, script, true, this.mode, new Vec3(x + 0.5, y, z + 0.5 + this.distance), 180.0F);
                });
                return StepResult.pending();
            }
            if (s.pending != null) {
                if (s.pending.isCompletedExceptionally()) {
                    return StepResult.fail("Szene 3D: " + s.pending.exceptionNow());
                }
                if (!s.pending.isDone()) {
                    return StepResult.pending();
                }
                s.pending = null;
            }
            int elapsed = ctx.tick() - this.since;
            if (elapsed > 900) {
                return StepResult.fail("3D-Szene wird nicht fertig, Stufe " + this.stage);
            }
            boolean ready = this.mode == PilotMode.FPV ? session.isFpv() : session.isLineOfSight();
            if (!ready || session.drone() == null || session.drone().getId() != s.droneId) {
                return StepResult.pending();
            }
            session.setTestMode3d(true);
            Telemetry t = Mode3dSelfTests.telemetry();
            if (t == null) {
                return StepResult.pending();
            }
            switch (this.stage) {
                case 0 -> {
                    session.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.5F), elapsed % 20 >= 10, StabMode.ACRO);
                    if (t.armed) {
                        s.targetY = this.ground + this.height;
                        s.lastY = Mode3dSelfTests.comY();
                        s.integral = 0.0;
                        this.stage = 1;
                        this.since = ctx.tick();
                    }
                }
                case 1 -> {
                    Mode3dSelfTests.hover(s, true);
                    boolean close = Math.abs(Mode3dSelfTests.comY() - s.targetY) < 0.25 && Math.abs(Mode3dSelfTests.comY() - s.lastY) < 0.05;
                    if (close && t.tiltDeg > 160.0 && ctx.tick() - this.since > 60) {
                        return StepResult.pass();
                    }
                }
                default -> {
                }
            }
            return StepResult.pending();
        }

        @Override
        public void hold(SelfTest.StepContext ctx) {
            Mode3dSelfTests.Script s = this.script;
            if (s != null && this.stage == 1) {
                Mode3dSelfTests.hover(s, true);
            }
        }

        @Override
        public void finish(SelfTest.StepContext ctx) {
            Mode3dSelfTests.Script s = this.script;
            if (s != null) {
                PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, 0.5F), false, StabMode.ACRO);
                Mode3dSelfTests.cleanup(ctx, s);
            }
            Vec3 back = this.returnTo;
            if (back != null) {
                float yaw = this.returnYaw;
                float pitch = this.returnPitch;
                ctx.onServer(server -> {
                    ServerPlayer player = Mode3dSelfTests.firstPlayer(server);
                    player.teleportTo(player.level(), back.x, back.y, back.z, Set.of(), yaw, pitch, true);
                });
                this.returnTo = null;
            }
            if (this.unlocked != null) {
                InputConfig.get().mode3dUnlocked = this.unlocked;
            }
            if (this.throttleShown != null) {
                OsdClient.CONFIG.get().slot(OsdElement.THROTTLE).enabled = this.throttleShown;
            }
            this.script = null;
            this.unlocked = null;
            this.throttleShown = null;
        }
    }
}
