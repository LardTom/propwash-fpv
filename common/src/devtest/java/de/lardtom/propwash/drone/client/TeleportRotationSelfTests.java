package de.lardtom.propwash.drone.client;

import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.content.ContentIds;
import de.lardtom.propwash.core.CoreComponents;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.client.Screenshots;
import de.lardtom.propwash.drone.DroneInteractions;
import de.lardtom.propwash.drone.DroneModule;
import de.lardtom.propwash.drone.DronePiloting;
import de.lardtom.propwash.drone.PilotMode;
import de.lardtom.propwash.drone.QuadcopterEntity;
import de.lardtom.propwash.input.StickPositions;
import de.lardtom.propwash.sim.DroneSim;
import de.lardtom.propwash.sim.SimSnapshot;
import de.lardtom.propwash.sim.StabMode;
import de.lardtom.propwash.sim.Telemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class TeleportRotationSelfTests {
    static final String GROUP = "drone: Lage per /tp und /rotate";
    private static final int SITE_DX = -14;
    private static final int SITE_DZ = -6;
    private static final double VIEW_DZ = 1.4;
    private static final float VIEW_PITCH = 45.0F;
    private static final double FLY_DX = -3.0;
    private static final double HOVER_DX = 2.5;
    private static final double HOVER = 3.0;
    private static final double SPEED = 6.0;
    private static final float ANGLE_EPS = 0.5F;
    private static final float FLIGHT_EPS = 3.0F;
    private static final float HOLD_EPS = 6.0F;
    private static final double SPEED_EPS = 1.0;
    private static final double TURN_JUMP = 1.0;
    private static final double MOVED_MIN = 1.5;
    private static final double MOVED_MAX = 3.5;
    private static final double PLACED = 0.3;
    private static final int SYNC_TICKS = 80;
    private static final int TURN_TICKS = 40;
    private static final int ARM_TICKS = 60;
    private static final int HOLD_TICKS = 10;
    private static final int PUSH_TICKS = 3;
    private static final int SHOT_SETTLE = 6;
    private static final String SHOT_STANDING = "drone_tp_rotate_standing";
    private static final String SHOT_FLYING = "drone_tp_rotate_flying";
    private static final String RUN = "tprot";
    private static final Turn[] TURNS = {
            new Turn("/rotate 90 0", "rotate <id> 90 0", 90.0F, false),
            new Turn("/tp ~ ~ ~ -90 0", "tp @e[type=propwash:drone,limit=1,sort=nearest] ~ ~ ~ -90 0", -90.0F, false),
            new Turn("/tp ~2 ~ ~ 180 0", "tp <id> ~2 ~ ~ 180 0", 180.0F, true)
    };
    private static final AtomicReference<@Nullable Rig> RIG = new AtomicReference<>();
    private static final AtomicReference<@Nullable String> RESULT = new AtomicReference<>();

    private TeleportRotationSelfTests() {
    }

    static void register() {
        SelfTest.clientStep(GROUP + " – stehende Drohne: /tp ~ ~ ~ -90 0, /rotate 45 0 und /rotate facing drehen sie, /tp ohne Rotation "
                + "lässt die Lage, der Client dreht ohne Rücksprung nach (Bildschirmfoto)", 800, TeleportRotationSelfTests::standingStep);
        SelfTest.clientStep(GROUP + " – schwebende Drohne ohne Schwerkraft: /rotate 30 20 senkt die Nase um 20°, ohne Rollen", 300,
                TeleportRotationSelfTests::pitchStep);
        SelfTest.clientStep(GROUP + " – fliegende Drohne im FPV: /rotate 90 0, /tp ~ ~ ~ -90 0 und /tp ~2 ~ ~ 180 0 drehen sie, das Tempo "
                + "im Weltsystem bleibt, kein Absturz (Bildschirmfoto)", 1200, TeleportRotationSelfTests::flyingStep);
        SelfTest.clientStep(GROUP + " – aufräumen", 200, TeleportRotationSelfTests::cleanupStep);
    }

    private record Turn(String label, String command, float yaw, boolean moves) {
    }

    private static final class Rig {
        final Vec3 site;
        final Vec3 savedPos;
        final float savedYaw;
        final float savedPitch;
        final List<Integer> drones = new ArrayList<>();
        int standing = -1;
        int hovering = -1;
        int flying = -1;
        @Nullable ItemStack head;
        @Nullable ItemStack hand;

        Rig(Vec3 site, Vec3 savedPos, float savedYaw, float savedPitch) {
            this.site = site;
            this.savedPos = savedPos;
            this.savedYaw = savedYaw;
            this.savedPitch = savedPitch;
        }
    }

    private static final class Run {
        int stage;
        int mark;
        @Nullable CompletableFuture<?> pending;
        float targetYaw;
        float targetPitch;
        float lastOff = Float.POSITIVE_INFINITY;
        int reachedAt = -1;
        boolean syncedReached;
        boolean syncedBack;
        float oldYaw;
        int crashes;
        @Nullable Vec3 lastPos;
        @Nullable Vec3 velocityBefore;
        Vec3 jump = Vec3.ZERO;
        int turn;
        @Nullable Vec3 velocityAfter;
        @Nullable CompletableFuture<String> shot;
        @Nullable String shotSize;
        final List<String> notes = new ArrayList<>();

        void enter(int next, int tick) {
            this.stage = next;
            this.mark = tick;
        }

        void track(float yaw, float pitch, int tick) {
            this.targetYaw = yaw;
            this.targetPitch = pitch;
            this.lastOff = Float.POSITIVE_INFINITY;
            this.reachedAt = -1;
            this.syncedReached = false;
            this.syncedBack = false;
            this.mark = tick;
        }
    }

    private static @Nullable StepResult awaitServer(Run run) {
        if (run.pending == null) {
            return null;
        }
        if (run.pending.isCompletedExceptionally()) {
            return StepResult.fail("Server-Aktion in Stufe " + run.stage + ": " + run.pending.exceptionNow());
        }
        if (!run.pending.isDone()) {
            return StepResult.pending();
        }
        run.pending = null;
        String result = RESULT.getAndSet(null);
        return result == null ? null : StepResult.fail(result);
    }

    private static void server(SelfTest.StepContext ctx, Run run, ServerCheck check) {
        RESULT.set(null);
        run.pending = ctx.onServer(server -> {
            try {
                RESULT.set(check.run(server));
            } catch (RuntimeException e) {
                RESULT.set(e.toString());
            }
        });
    }

    @FunctionalInterface
    private interface ServerCheck {
        @Nullable String run(MinecraftServer server);
    }

    private static StepResult standingStep(SelfTest.StepContext ctx) {
        Run run = (Run) ctx.state().computeIfAbsent(RUN, key -> new Run());
        StepResult waiting = awaitServer(run);
        if (waiting != null) {
            return waiting;
        }
        int waited = ctx.tick() - run.mark;
        Rig rig = RIG.get();
        if (run.stage == 0) {
            server(ctx, run, server -> {
                Rig r = rig(server);
                ServerLevel level = player(server).level();
                QuadcopterEntity drone = spawn(level, r, r.site, true);
                r.standing = drone.getId();
                Vec3 view = r.site.add(0.0, 0.0, VIEW_DZ);
                player(server).teleportTo(level, view.x, view.y, view.z, Set.of(), 180.0F, VIEW_PITCH, true);
                return null;
            });
            run.enter(1, ctx.tick());
            return StepResult.pending();
        }
        if (rig == null) {
            return StepResult.fail("kein Prüfplatz");
        }
        QuadcopterEntity drone = clientDrone(rig.standing);
        if (drone == null) {
            return waited > SYNC_TICKS ? StepResult.fail("stehende Drohne kommt beim Client nicht an") : StepResult.pending();
        }
        switch (run.stage) {
            case 1 -> {
                if (off(yaw(drone.orientation()), 0.0F) > ANGLE_EPS) {
                    return waited > SYNC_TICKS ? StepResult.fail("Drohne steht nicht mit Gier 0: " + describe(drone.orientation()))
                            : StepResult.pending();
                }
                command(ctx, run, rig.standing, "tp @e[type=propwash:drone,limit=1,sort=nearest] ~ ~ ~ -90 0", -90.0F, 0.0F);
                run.track(-90.0F, 0.0F, ctx.tick());
                run.enter(2, ctx.tick());
                return StepResult.pending();
            }
            case 2 -> {
                StepResult result = follow(ctx, run, drone, "/tp ~ ~ ~ -90 0");
                if (result != null) {
                    return result;
                }
                run.enter(3, ctx.tick());
                return StepResult.pending();
            }
            case 3 -> {
                StepResult shot = shoot(run, waited - SHOT_SETTLE, SHOT_STANDING);
                if (shot != null) {
                    return shot;
                }
                if (run.shotSize == null) {
                    return waited > SYNC_TICKS ? StepResult.fail("Bildschirmfoto " + SHOT_STANDING + " nicht fertig") : StepResult.pending();
                }
                run.notes.add("Bildschirmfoto " + shotPath(SHOT_STANDING) + " (" + run.shotSize + ")");
                command(ctx, run, rig.standing, "rotate " + uuid(drone) + " 45 0", 45.0F, 0.0F);
                run.track(45.0F, 0.0F, ctx.tick());
                run.enter(4, ctx.tick());
                return StepResult.pending();
            }
            case 4 -> {
                StepResult result = follow(ctx, run, drone, "/rotate 45 0");
                if (result != null) {
                    return result;
                }
                Quaternionf before = new Quaternionf(drone.orientation());
                server(ctx, run, server -> {
                    QuadcopterEntity target = serverDrone(server, rig.standing);
                    int done = execute(server, target.position(), "tp " + target.getStringUUID() + " ~ ~0.25 ~");
                    if (done <= 0) {
                        return "/tp ~ ~0.25 ~ abgelehnt (" + done + ")";
                    }
                    float angle = angle(target.orientation(), before);
                    return angle > ANGLE_EPS ? String.format(Locale.ROOT, "Server: /tp ohne Rotation dreht die Drohne um %.2f° (%s)", angle,
                            describe(target.orientation())) : null;
                });
                run.enter(5, ctx.tick());
                return StepResult.pending();
            }
            case 5 -> {
                if (waited < 20) {
                    Quaternionf render = drone.renderAttitude(1.0F, new Quaternionf());
                    if (off(yaw(render), 45.0F) > ANGLE_EPS || off(yaw(drone.orientation()), 45.0F) > ANGLE_EPS) {
                        return StepResult.fail("Client: /tp ohne Rotation dreht die Drohne: " + describe(drone.orientation()) + ", Bild "
                                + describe(render));
                    }
                    return StepResult.pending();
                }
                run.notes.add("/tp ~ ~0.25 ~ ohne Rotation: Lage 20 Ticks unverändert " + describe(drone.orientation()));
                command(ctx, run, rig.standing, "rotate " + uuid(drone) + " facing ~3 ~ ~", -90.0F, 0.0F);
                run.track(-90.0F, 0.0F, ctx.tick());
                run.enter(6, ctx.tick());
                return StepResult.pending();
            }
            case 6 -> {
                StepResult result = follow(ctx, run, drone, "/rotate facing ~3 ~ ~");
                if (result != null) {
                    return result;
                }
                ctx.log("stehende Drohne: " + String.join("; ", run.notes));
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + run.stage);
            }
        }
    }

    private static StepResult pitchStep(SelfTest.StepContext ctx) {
        Run run = (Run) ctx.state().computeIfAbsent(RUN, key -> new Run());
        StepResult waiting = awaitServer(run);
        if (waiting != null) {
            return waiting;
        }
        int waited = ctx.tick() - run.mark;
        Rig rig = RIG.get();
        if (run.stage == 0) {
            server(ctx, run, server -> {
                Rig r = rig(server);
                QuadcopterEntity drone = spawn(player(server).level(), r, r.site.add(HOVER_DX, HOVER, 0.0), false);
                r.hovering = drone.getId();
                return null;
            });
            run.enter(1, ctx.tick());
            return StepResult.pending();
        }
        if (rig == null) {
            return StepResult.fail("kein Prüfplatz");
        }
        QuadcopterEntity drone = clientDrone(rig.hovering);
        if (drone == null) {
            return waited > SYNC_TICKS ? StepResult.fail("schwebende Drohne kommt beim Client nicht an") : StepResult.pending();
        }
        switch (run.stage) {
            case 1 -> {
                if (off(yaw(drone.orientation()), 0.0F) > ANGLE_EPS) {
                    return waited > SYNC_TICKS ? StepResult.fail("Drohne schwebt nicht mit Gier 0: " + describe(drone.orientation()))
                            : StepResult.pending();
                }
                command(ctx, run, rig.hovering, "rotate " + uuid(drone) + " 30 20", 30.0F, 20.0F);
                run.track(30.0F, 20.0F, ctx.tick());
                run.enter(2, ctx.tick());
                return StepResult.pending();
            }
            case 2 -> {
                StepResult result = follow(ctx, run, drone, "/rotate 30 20");
                if (result != null) {
                    return result;
                }
                run.enter(3, ctx.tick());
                return StepResult.pending();
            }
            case 3 -> {
                Quaternionf render = drone.renderAttitude(1.0F, new Quaternionf());
                if (angle(render, attitude(30.0F, 20.0F)) > ANGLE_EPS) {
                    return StepResult.fail("Neigung hält nicht: " + describe(render));
                }
                if (waited < HOLD_TICKS) {
                    return StepResult.pending();
                }
                ctx.log("schwebende Drohne: " + String.join("; ", run.notes) + "; nach " + HOLD_TICKS + " Ticks weiter " + describe(render));
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + run.stage);
            }
        }
    }

    private static StepResult flyingStep(SelfTest.StepContext ctx) {
        Run run = (Run) ctx.state().computeIfAbsent(RUN, key -> new Run());
        StepResult waiting = awaitServer(run);
        if (waiting != null) {
            if (waiting.status() != StepResult.Status.PENDING) {
                input(0.0F, false);
            }
            return waiting;
        }
        StepResult result = flying(ctx, run);
        if (result.status() != StepResult.Status.PENDING) {
            input(0.0F, false);
        }
        return result;
    }

    private static StepResult flying(SelfTest.StepContext ctx, Run run) {
        int waited = ctx.tick() - run.mark;
        Rig rig = RIG.get();
        if (run.stage == 0) {
            input(0.0F, false);
            server(ctx, run, server -> {
                Rig r = rig(server);
                ServerPlayer player = player(server);
                QuadcopterEntity drone = spawn(player.level(), r, r.site.add(FLY_DX, 0.0, 0.0), true);
                r.flying = drone.getId();
                fly(player, drone, r);
                return null;
            });
            run.enter(1, ctx.tick());
            return StepResult.pending();
        }
        if (rig == null) {
            return StepResult.fail("kein Prüfplatz");
        }
        PilotSession session = PilotSession.INSTANCE;
        QuadcopterEntity drone = session.drone();
        DroneSim sim = session.sim();
        if (drone == null || sim == null || drone.getId() != rig.flying || !session.isFpv()) {
            return waited > SYNC_TICKS ? StepResult.fail("keine FPV-Sitzung mit der Testdrohne (Stufe " + run.stage + ")") : StepResult.pending();
        }
        Telemetry t = sim.telemetry();
        Vec3 air = rig.site.add(FLY_DX, HOVER, 0.0);
        switch (run.stage) {
            case 1 -> {
                boolean grounded = t.grounded || t.sleeping;
                input(0.0F, t.armed || grounded && waited >= 3);
                if (!t.armed) {
                    return waited > ARM_TICKS ? StepResult.fail("nicht scharf geschaltet, Sperren=" + t.armBlockers) : StepResult.pending();
                }
                server(ctx, run, server -> {
                    serverDrone(server, rig.flying).resetTo(air, attitude(0.0F, 0.0F));
                    return null;
                });
                run.enter(2, ctx.tick());
                return StepResult.pending();
            }
            case 2 -> {
                input((float) t.hoverThrottleStick, true);
                if (drone.position().distanceTo(air) > PLACED) {
                    return waited > SYNC_TICKS ? StepResult.fail("Drohne nicht in die Luft gesetzt: " + drone.position()) : StepResult.pending();
                }
                if (!t.armed) {
                    return StepResult.fail("beim Versetzen entschärft: " + t.lastDisarmReason);
                }
                push(sim, -SPEED);
                run.enter(3, ctx.tick());
                return StepResult.pending();
            }
            case 3 -> {
                input((float) t.hoverThrottleStick, true);
                if (waited < PUSH_TICKS) {
                    return StepResult.pending();
                }
                Turn turn = TURNS[run.turn];
                run.crashes = session.crashCount();
                run.oldYaw = yaw(drone.renderAttitude(1.0F, new Quaternionf()));
                run.velocityBefore = velocity(sim);
                run.lastPos = drone.position();
                run.jump = Vec3.ZERO;
                command(ctx, run, rig.flying, turn.command().replace("<id>", uuid(drone)), turn.yaw(), 0.0F);
                run.track(turn.yaw(), 0.0F, ctx.tick());
                run.enter(4, ctx.tick());
                return StepResult.pending();
            }
            case 4 -> {
                input((float) t.hoverThrottleStick, true);
                return flyingTurn(ctx, run, drone, sim, waited);
            }
            case 5 -> {
                input((float) t.hoverThrottleStick, true);
                StepResult shot = shoot(run, waited - SHOT_SETTLE, SHOT_FLYING);
                if (shot != null) {
                    return shot;
                }
                if (run.shotSize == null) {
                    return waited > SYNC_TICKS ? StepResult.fail("Bildschirmfoto " + SHOT_FLYING + " nicht fertig") : StepResult.pending();
                }
                run.notes.add("Bildschirmfoto " + shotPath(SHOT_FLYING) + " (" + run.shotSize + ")");
                float last = TURNS[TURNS.length - 1].yaw();
                server(ctx, run, server -> {
                    QuadcopterEntity flown = serverDrone(server, rig.flying);
                    float yaw = yaw(flown.orientation());
                    return off(yaw, last) > HOLD_EPS ? String.format(Locale.ROOT, "Server-Lage nach dem Flug Gier %.1f statt %.0f (%s)", yaw, last,
                            describe(flown.orientation())) : null;
                });
                run.enter(6, ctx.tick());
                return StepResult.pending();
            }
            case 6 -> {
                input((float) t.hoverThrottleStick, true);
                ctx.log("fliegende Drohne: " + String.join("; ", run.notes));
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + run.stage);
            }
        }
    }

    private static StepResult flyingTurn(SelfTest.StepContext ctx, Run run, QuadcopterEntity drone, DroneSim sim, int waited) {
        Turn turn = TURNS[run.turn];
        String what = turn.label();
        Vec3 pos = drone.position();
        Quaternionf render = drone.renderAttitude(1.0F, new Quaternionf());
        float renderOff = off(yaw(render), run.targetYaw);
        float syncedYaw = yaw(drone.orientation());
        if (off(syncedYaw, run.targetYaw) <= HOLD_EPS) {
            run.syncedReached = true;
        } else if (run.syncedReached && off(syncedYaw, run.oldYaw) <= HOLD_EPS) {
            run.syncedBack = true;
        }
        if (run.reachedAt < 0) {
            Vec3 last = run.lastPos;
            run.lastPos = pos;
            if (renderOff > FLIGHT_EPS) {
                return waited > TURN_TICKS ? StepResult.fail(String.format(Locale.ROOT, "Pilot: nach %s Gier %.1f statt %.0f (%s)", what,
                        yaw(render), run.targetYaw, describe(render))) : StepResult.pending();
            }
            run.reachedAt = ctx.tick();
            run.jump = last == null ? Vec3.ZERO : pos.subtract(last);
            run.velocityAfter = velocity(sim);
            return StepResult.pending();
        }
        if (renderOff > HOLD_EPS) {
            return StepResult.fail(String.format(Locale.ROOT, "Pilot: Lage springt nach %s zurück, Gier %.1f statt %.0f", what, yaw(render),
                    run.targetYaw));
        }
        if (ctx.tick() - run.reachedAt < HOLD_TICKS) {
            return StepResult.pending();
        }
        Telemetry t = sim.telemetry();
        Vec3 before = run.velocityBefore;
        Vec3 after = run.velocityAfter;
        if (before == null || after == null) {
            return StepResult.fail("kein Tempo gemessen");
        }
        double jump = run.jump.length();
        String summary = String.format(Locale.ROOT, "%s nach %d Ticks: Gier %.1f → %.1f, Tempo vorher (%.2f, %.2f, %.2f) nachher (%.2f, %.2f, %.2f) m/s, "
                        + "Sprung (%.2f, %.2f, %.2f) m, Server-Lage beim Client %.1f", what, run.reachedAt - run.mark, run.oldYaw, yaw(render), before.x,
                before.y, before.z, after.x, after.y, after.z, run.jump.x, run.jump.y, run.jump.z, syncedYaw);
        if (after.subtract(before).length() > SPEED_EPS || after.x > -SPEED + 2.0 * SPEED_EPS) {
            return StepResult.fail("Tempo im Weltsystem nicht erhalten: " + summary);
        }
        boolean jumpOk = turn.moves() ? run.jump.x > MOVED_MIN && jump < MOVED_MAX : jump < TURN_JUMP;
        if (!jumpOk) {
            return StepResult.fail((turn.moves() ? "Teleport nicht übernommen: " : "Position springt: ") + summary);
        }
        if (PilotSession.INSTANCE.crashCount() != run.crashes || !t.armed) {
            return StepResult.fail("Absturz oder entschärft (" + t.lastDisarmReason + "): " + summary);
        }
        if (!run.syncedReached || run.syncedBack) {
            return StepResult.fail("Server-Lage beim Client " + (run.syncedBack ? "springt zurück" : "nicht übernommen") + ": " + summary);
        }
        run.notes.add(summary);
        run.turn++;
        if (run.turn < TURNS.length) {
            push(sim, -SPEED);
            run.enter(3, ctx.tick());
        } else {
            run.enter(5, ctx.tick());
        }
        return StepResult.pending();
    }

    private static @Nullable StepResult follow(SelfTest.StepContext ctx, Run run, QuadcopterEntity drone, String what) {
        Quaternionf target = attitude(run.targetYaw, run.targetPitch);
        Quaternionf render = drone.renderAttitude(1.0F, new Quaternionf());
        float synced = angle(drone.orientation(), target);
        float drawn = angle(render, target);
        if (drawn > run.lastOff + 0.01F) {
            return StepResult.fail(String.format(Locale.ROOT, "Client: Drehung nach %s springt zurück (%.2f° → %.2f° vom Ziel)", what,
                    run.lastOff, drawn));
        }
        run.lastOff = drawn;
        if (synced > ANGLE_EPS || drawn > ANGLE_EPS) {
            return ctx.tick() - run.mark > SYNC_TICKS ? StepResult.fail(String.format(Locale.ROOT, "Client: nach %s Lage %s, Bild %s, Ziel %s",
                    what, describe(drone.orientation()), describe(render), describe(target))) : StepResult.pending();
        }
        run.notes.add(String.format(Locale.ROOT, "%s: Client nach %d Ticks bei %s", what, ctx.tick() - run.mark, describe(render)));
        return null;
    }

    private static void command(SelfTest.StepContext ctx, Run run, int id, String command, float yaw, float pitch) {
        server(ctx, run, server -> {
            QuadcopterEntity drone = serverDrone(server, id);
            int done = execute(server, drone.position(), command);
            if (done <= 0) {
                return command + " abgelehnt (" + done + ")";
            }
            float angle = angle(drone.orientation(), attitude(yaw, pitch));
            if (angle > ANGLE_EPS) {
                return String.format(Locale.ROOT, "Server: nach /%s Lage %s, Ziel Gier %.0f Nick %.0f (%.1f° daneben), Vanilla-Blick %.1f/%.1f",
                        command, describe(drone.orientation()), yaw, pitch, angle, drone.getYRot(), drone.getXRot());
            }
            return null;
        });
    }

    private static int execute(MinecraftServer server, Vec3 at, String command) {
        AtomicInteger result = new AtomicInteger(-1);
        CommandSourceStack source = server.createCommandSourceStack().withPosition(at).withSuppressedOutput()
                .withCallback((ok, value) -> result.set(ok ? value : 0));
        server.getCommands().performPrefixedCommand(source, command);
        return result.get();
    }

    private static @Nullable StepResult shoot(Run run, int fromShot, String name) {
        if (fromShot == -2) {
            Screenshots.clearOverlays(Minecraft.getInstance());
        }
        if (fromShot >= 0 && run.shot == null) {
            run.shot = Screenshots.capture(shotPath(name));
        }
        if (run.shot == null || !run.shot.isDone() || run.shotSize != null) {
            return null;
        }
        if (run.shot.isCompletedExceptionally()) {
            return StepResult.fail("Bildschirmfoto " + name + ": " + run.shot.exceptionNow());
        }
        run.shotSize = run.shot.join();
        return null;
    }

    private static String shotPath(String name) {
        return Screenshots.enabled() ? name : "screenshots/" + name;
    }

    static StepResult cleanupStep(SelfTest.StepContext ctx) {
        Rig rig = RIG.get();
        if (rig == null) {
            return StepResult.pass();
        }
        if (ctx.tick() == 0) {
            PilotSession.INSTANCE.clearTestInput();
            ctx.state().put(RUN, ctx.onServer(server -> {
                for (ServerLevel level : server.getAllLevels()) {
                    for (int id : rig.drones) {
                        Entity entity = level.getEntity(id);
                        if (entity instanceof QuadcopterEntity drone) {
                            DronePiloting.release(drone);
                            drone.discard();
                        }
                    }
                }
                ServerPlayer player = player(server);
                if (rig.head != null) {
                    player.setItemSlot(EquipmentSlot.HEAD, rig.head);
                }
                if (rig.hand != null) {
                    player.setItemSlot(EquipmentSlot.MAINHAND, rig.hand);
                }
                player.teleportTo(player.level(), rig.savedPos.x, rig.savedPos.y, rig.savedPos.z, Set.of(), rig.savedYaw, rig.savedPitch, true);
                RIG.compareAndSet(rig, null);
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) ctx.state().get(RUN);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Aufräumen: " + future.exceptionNow().getMessage());
        }
        if (!future.isDone()) {
            return StepResult.pending();
        }
        return PilotSession.INSTANCE.isActive() ? (ctx.tick() > SYNC_TICKS ? StepResult.fail("Sitzung läuft nach dem Aufräumen weiter")
                : StepResult.pending()) : StepResult.pass();
    }

    private static Rig rig(MinecraftServer server) {
        Rig known = RIG.get();
        if (known != null) {
            return known;
        }
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        int x = player.getBlockX() + SITE_DX;
        int z = player.getBlockZ() + SITE_DZ;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        Rig rig = new Rig(new Vec3(x + 0.5, y, z + 0.5), player.position(), player.getYRot(), player.getXRot());
        RIG.set(rig);
        return rig;
    }

    private static QuadcopterEntity spawn(ServerLevel level, Rig rig, Vec3 at, boolean gravity) {
        QuadcopterEntity drone = DroneModule.DRONE.get().create(level, EntitySpawnReason.COMMAND);
        if (drone == null) {
            throw new IllegalStateException("EntityType.create lieferte null");
        }
        drone.setBuild(DroneBuild.FREESTYLE);
        drone.setNoGravity(!gravity);
        drone.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        if (!level.addFreshEntity(drone)) {
            throw new IllegalStateException("addFreshEntity fehlgeschlagen");
        }
        rig.drones.add(drone.getId());
        drone.resetTo(at, attitude(0.0F, 0.0F));
        return drone;
    }

    private static void fly(ServerPlayer player, QuadcopterEntity drone, Rig rig) {
        DronePiloting.pilotedBy(player.getUUID()).ifPresent(DronePiloting::release);
        ItemStack goggles = ContentIds.item(ContentIds.GOGGLES_CREATIVE).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (goggles.isEmpty()) {
            throw new IllegalStateException("Kreativ-Brille nicht registriert");
        }
        goggles.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        if (rig.head == null) {
            rig.head = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        }
        player.setItemSlot(EquipmentSlot.HEAD, goggles);
        ItemStack remote = new ItemStack(DroneModule.REMOTE.get());
        remote.set(CoreComponents.BOUND_DRONE.get(), DroneInteractions.binding(drone));
        if (rig.hand == null) {
            rig.hand = player.getMainHandItem().copy();
        }
        player.setItemSlot(EquipmentSlot.MAINHAND, remote);
        DronePiloting.StartResult result = DronePiloting.tryStart(player, drone, PilotMode.FPV);
        if (result != DronePiloting.StartResult.STARTED) {
            throw new IllegalStateException("FPV-Start verweigert: " + result);
        }
    }

    private static void input(float throttle, boolean arm) {
        PilotSession.INSTANCE.setTestInput(new StickPositions(0.0F, 0.0F, 0.0F, throttle), arm, StabMode.ANGLE);
    }

    private static void push(DroneSim sim, double vx) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        snapshot.vx = vx;
        snapshot.vy = 0.0;
        snapshot.vz = 0.0;
        snapshot.sleeping = false;
        sim.copyStateFrom(snapshot);
    }

    private static Vec3 velocity(DroneSim sim) {
        SimSnapshot snapshot = new SimSnapshot();
        sim.copyStateTo(snapshot);
        return new Vec3(snapshot.vx, snapshot.vy, snapshot.vz);
    }

    static Quaternionf attitude(float yaw, float pitch) {
        return new Quaternionf().rotationYXZ((float) (Math.PI - Math.toRadians(yaw)), (float) -Math.toRadians(pitch), 0.0F);
    }

    static float yaw(Quaternionfc q) {
        return QuadcopterEntity.yawDegrees(q);
    }

    static float pitch(Quaternionfc q) {
        Vector3f forward = q.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        return (float) Math.toDegrees(Math.asin(Mth.clamp(-forward.y, -1.0F, 1.0F)));
    }

    static float roll(Quaternionfc q) {
        Vector3f right = q.transform(new Vector3f(1.0F, 0.0F, 0.0F));
        return (float) Math.toDegrees(Math.asin(Mth.clamp(right.y, -1.0F, 1.0F)));
    }

    static float angle(Quaternionfc a, Quaternionfc b) {
        float dot = Math.abs(a.x() * b.x() + a.y() * b.y() + a.z() * b.z() + a.w() * b.w());
        return (float) Math.toDegrees(2.0 * Math.acos(Math.min(1.0F, dot)));
    }

    static float off(float a, float b) {
        return Math.abs(Mth.wrapDegrees(a - b));
    }

    static String describe(Quaternionfc q) {
        return String.format(Locale.ROOT, "Gier %.1f Nick %.1f Rollen %.1f", yaw(q), pitch(q), roll(q));
    }

    private static String uuid(QuadcopterEntity drone) {
        return drone.getStringUUID();
    }

    private static @Nullable QuadcopterEntity clientDrone(int id) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && id >= 0 && mc.level.getEntity(id) instanceof QuadcopterEntity drone ? drone : null;
    }

    private static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler auf dem Server");
        }
        return players.getFirst();
    }

    private static QuadcopterEntity serverDrone(MinecraftServer server, int id) {
        if (!(player(server).level().getEntity(id) instanceof QuadcopterEntity drone)) {
            throw new IllegalStateException("Testdrohne auf dem Server verschwunden");
        }
        return drone;
    }
}
