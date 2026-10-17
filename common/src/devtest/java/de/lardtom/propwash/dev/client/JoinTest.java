package de.lardtom.propwash.dev.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.dev.JoinTestEnv;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.drone.client.PilotSession;
import de.lardtom.propwash.drone.client.trail.RemoteTrailCheck;
import de.lardtom.propwash.join.client.AddonSessionSelfTests;
import de.lardtom.propwash.platform.Services;
import de.lardtom.propwash.race.client.AssistJoinProbe;
import de.lardtom.propwash.zone.client.ZoneJoinProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.GenericWaitingScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class JoinTest {
    private static final int MAX_TICKS = 20 * 150;
    private static final int STABLE_TICKS = 30;
    private static final int PILOT_WAIT_TICKS = 20 * 60;
    private static final String STEP = "Beitrittstest ";

    private JoinTest() {
    }

    public static boolean enabled() {
        return SelfTest.smokeEnabled() && JoinTestEnv.target() != null;
    }

    public static List<SelfTest.Step> steps() {
        String label = JoinTestEnv.label();
        int ticks = MAX_TICKS + 20 * (JoinTestEnv.trailSeconds() > 0 ? 180 + JoinTestEnv.trailSeconds() : 0) + (JoinTestEnv.assist() ? 20 * 90 : 0)
                + (JoinTestEnv.zones() ? ZoneJoinProbe.TIMEOUT_TICKS + 20 * 30 : 0);
        return List.of(new SelfTest.Step(STEP + label, ticks, JoinTest::tick));
    }

    private static SelfTest.StepResult tick(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        String target = JoinTestEnv.target();
        if (target == null) {
            return SelfTest.StepResult.fail("kein Ziel");
        }
        if (ctx.tick() == 0) {
            ctx.log("verbinde mit " + target + " (" + Services.PLATFORM.getPlatformName() + ")");
            ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(target),
                    new ServerData("Propwash join test", target, ServerData.Type.OTHER), false, null);
            return SelfTest.StepResult.pending();
        }
        if (state.containsKey("capture")) {
            @SuppressWarnings("unchecked")
            CompletableFuture<String> capture = (CompletableFuture<String>) state.get("capture");
            if (!capture.isDone()) {
                return SelfTest.StepResult.pending();
            }
            if (state.containsKey("leaving")) {
                if (mc.level != null) {
                    return SelfTest.StepResult.pending();
                }
            } else if (mc.level != null) {
                state.put("leaving", true);
                mc.disconnectFromWorld(Component.literal("Propwash join test"));
                return SelfTest.StepResult.pending();
            }
            return evaluate((String) state.get("text"));
        }
        Screen screen = mc.gui.screen();
        boolean joined = mc.level != null && mc.player != null && screen == null;
        boolean settled = joined || (mc.level == null && screen != null && !transientScreen(screen));
        String key = joined ? "world" : screen == null ? "none" : screen.getClass().getName();
        if (!key.equals(state.get("screen"))) {
            state.put("screen", key);
            state.put("since", ctx.tick());
            return SelfTest.StepResult.pending();
        }
        if (!settled || ctx.tick() - (int) state.get("since") < STABLE_TICKS) {
            return SelfTest.StepResult.pending();
        }
        if (joined && JoinTestEnv.zones() && !state.containsKey("zones")) {
            String zones = ZoneJoinProbe.observe(state, ctx.tick(), mc);
            if (zones == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("zones", zones);
        }
        if (joined && JoinTestEnv.pilotSeconds() > 0 && !state.containsKey("pilot")) {
            String pilot = pilotProbe(ctx, mc);
            if (pilot == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("pilot", pilot);
        }
        if (joined && JoinTestEnv.trailSeconds() > 0 && !state.containsKey("trail")) {
            String trail = RemoteTrailCheck.observe(state, ctx.tick(), JoinTestEnv.trailSeconds());
            if (trail == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("trail", trail);
        }
        if (joined && JoinTestEnv.assist() && !state.containsKey("assist")) {
            String assist = AssistJoinProbe.observe(state, ctx.tick(), mc);
            if (assist == null) {
                return SelfTest.StepResult.pending();
            }
            state.put("assist", assist);
        }
        String text = joined ? JoinTestEnv.JOINED + "\n" + AddonSessionSelfTests.sessionReport(mc) + state.getOrDefault("pilot", "") + state.getOrDefault("trail", "")
                + state.getOrDefault("assist", "") + state.getOrDefault("zones", "") : describe(screen);
        state.put("text", text);
        write(JoinTestEnv.label() + ".txt", text);
        Constants.LOG.info("[Beitrittstest] {}: {}", JoinTestEnv.label(), text.replace("\n", " | "));
        state.put("capture", capture(JoinTestEnv.label()));
        return SelfTest.StepResult.pending();
    }

    private static @Nullable String pilotProbe(SelfTest.StepContext ctx, Minecraft mc) {
        Map<String, Object> state = ctx.state();
        LocalPlayer player = mc.player;
        if (player == null) {
            return null;
        }
        if (!state.containsKey("fpvAt")) {
            if (PilotSession.INSTANCE.isFpv()) {
                state.put("fpvAt", ctx.tick());
                state.put("fpvY", player.getY());
                return null;
            }
            return ctx.tick() > PILOT_WAIT_TICKS ? "pilot no session\n" : null;
        }
        if (player.onGround()) {
            state.put("landed", true);
        }
        state.merge("minHealth", player.getHealth(), (a, b) -> Math.min((float) a, (float) b));
        if (ctx.tick() - (int) state.get("fpvAt") < JoinTestEnv.pilotSeconds() * 20) {
            return null;
        }
        double fell = (double) state.get("fpvY") - player.getY();
        return "pilot fpv " + (PilotSession.INSTANCE.isFpv() ? "held" : "ended") + "\nbody "
                + (state.containsKey("landed") && player.onGround() ? "landed" : "floating")
                + String.format(Locale.ROOT, " fell %.2f%n", fell)
                + ((float) state.get("minHealth") < player.getMaxHealth() - 0.5F ? "body hurt" : "body unhurt")
                + String.format(Locale.ROOT, " lowest %.1f now %.1f%n", (float) state.get("minHealth"), player.getHealth());
    }

    private static SelfTest.StepResult evaluate(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String expected : JoinTestEnv.expectations()) {
            boolean negate = expected.startsWith("!");
            String needle = (negate ? expected.substring(1) : expected).toLowerCase(Locale.ROOT);
            if (lower.contains(needle) == negate) {
                return SelfTest.StepResult.fail((negate ? "unerwartet: " : "fehlt: ") + needle);
            }
        }
        return SelfTest.StepResult.pass();
    }

    private static boolean transientScreen(Screen screen) {
        return screen instanceof ConnectScreen || screen instanceof GenericMessageScreen || screen instanceof GenericWaitingScreen
                || screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen;
    }

    public static String describe(@Nullable Screen screen) {
        if (screen == null) {
            return "kein Screen\n";
        }
        StringBuilder out = new StringBuilder();
        out.append("screen=").append(screen.getClass().getName()).append('\n');
        out.append("title=").append(screen.getTitle().getString()).append('\n');
        for (Method method : screen.getClass().getMethods()) {
            if (method.getName().equals("detailsText") && method.getParameterCount() == 0 && method.getReturnType() == String.class) {
                try {
                    out.append("details=\n").append(method.invoke(screen)).append('\n');
                } catch (ReflectiveOperationException e) {
                    out.append("details: ").append(e).append('\n');
                }
            }
        }
        for (Class<?> type = screen.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    appendValue(out, type.getSimpleName() + "." + field.getName(), field.get(screen));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    continue;
                }
            }
        }
        return out.toString();
    }

    private static void appendValue(StringBuilder out, String name, @Nullable Object value) {
        switch (value) {
            case null -> {
            }
            case Component component -> out.append(name).append('=').append(component.getString()).append('\n');
            case DisconnectionDetails details -> out.append(name).append(".reason=").append(details.reason().getString()).append('\n');
            case Map<?, ?> map -> {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getValue() instanceof Component component) {
                        out.append(name).append('[').append(entry.getKey()).append("]=").append(component.getString()).append('\n');
                    }
                }
            }
            case Collection<?> list -> {
                int index = 0;
                for (Object item : list) {
                    if (item instanceof Component component) {
                        out.append(name).append('[').append(index).append("]=").append(component.getString()).append('\n');
                    }
                    index++;
                }
            }
            default -> {
            }
        }
    }

    private static CompletableFuture<String> capture(String label) {
        CompletableFuture<String> done = new CompletableFuture<>();
        Path file = Services.PLATFORM.getGameDir().resolve("jointest").resolve(label + ".png");
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            done.complete("kein Ordner: " + e);
            return done;
        }
        RenderTarget renderTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        Screenshot.takeScreenshot(renderTarget, 1, image -> Util.ioPool().execute(() -> {
            try (image) {
                image.writeToFile(file);
                done.complete(file.toString());
            } catch (Exception e) {
                done.complete("Bildschirmfoto fehlgeschlagen: " + e);
            }
        }));
        return done;
    }

    private static void write(String name, String text) {
        try {
            Path dir = Services.PLATFORM.getGameDir().resolve("jointest");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Constants.LOG.error("[Beitrittstest] {} nicht geschrieben", name, e);
        }
    }
}
