package de.lardtom.propwash.dev.client;

import com.mojang.blaze3d.platform.Window;
import de.lardtom.propwash.Constants;
import de.lardtom.propwash.dev.SelfTest;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLEvents;

public final class DetachedMouse {
    public static final String MIXIN_CONFIG = "propwash.dev.mixins.json";

    private static final int[] SYSTEM_EVENTS = {
            SDLEvents.SDL_EVENT_MOUSE_MOTION,
            SDLEvents.SDL_EVENT_MOUSE_BUTTON_DOWN,
            SDLEvents.SDL_EVENT_MOUSE_BUTTON_UP,
            SDLEvents.SDL_EVENT_MOUSE_WHEEL,
            SDLEvents.SDL_EVENT_WINDOW_MOUSE_ENTER,
            SDLEvents.SDL_EVENT_WINDOW_MOUSE_LEAVE,
            SDLEvents.SDL_EVENT_WINDOW_FOCUS_GAINED,
            SDLEvents.SDL_EVENT_WINDOW_FOCUS_LOST
    };

    private static boolean installed;
    private static boolean warped;
    private static double x;
    private static double y;
    private static int grabs;
    private static int releases;

    private DetachedMouse() {
    }

    public static boolean requested() {
        return SelfTest.enabled() || SelfTest.smokeEnabled();
    }

    public static void install() {
        for (int type : SYSTEM_EVENTS) {
            SDLEvents.SDL_SetEventEnabled(type, false);
        }
        installed = true;
        Constants.LOG.info("[Selbsttest] Testfenster ohne Mausgriff: Systemzeiger bleibt frei, Maus und Fokus des Systems erreichen das Spiel nicht");
    }

    public static boolean installed() {
        return installed;
    }

    public static boolean systemEventsIgnored() {
        for (int type : SYSTEM_EVENTS) {
            if (SDLEvents.SDL_EventEnabled(type)) {
                return false;
            }
        }
        return true;
    }

    public static String failure() {
        if (!installed) {
            return "Testfenster greift die Maus: " + MIXIN_CONFIG + " ist nicht geladen";
        }
        if (!systemEventsIgnored()) {
            return "Testfenster nimmt Maus oder Fokus des Systems an: SDL-Ereignisse wieder eingeschaltet";
        }
        return null;
    }

    public static SelfTest.StepResult check() {
        String failure = failure();
        if (failure != null) {
            return SelfTest.StepResult.fail(failure);
        }
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isWindowActive()) {
            return SelfTest.StepResult.fail("Testfenster hält sich für inaktiv, Eingaben der Schritte kämen nicht an");
        }
        if (mc.gui.screen() == null && !mc.mouseHandler.isMouseGrabbed()) {
            return SelfTest.StepResult.fail("Maus im Spiel nicht gegriffen, obwohl kein Bildschirm offen ist");
        }
        if (grabs == 0) {
            return SelfTest.StepResult.fail("kein Mausgriff abgefangen, der Systemzeiger wäre gesprungen");
        }
        return SelfTest.StepResult.pass();
    }

    public static String summary() {
        return grabs + " Mausgriffe und " + releases + " Freigaben ohne Systemzeiger";
    }

    public static void grab(double cursorX, double cursorY) {
        grabs++;
        warp(cursorX, cursorY);
    }

    public static void release(double cursorX, double cursorY) {
        releases++;
        warp(cursorX, cursorY);
    }

    public static Window window() {
        return Minecraft.getInstance().getWindow();
    }

    public static double x(Window window) {
        return warped ? x : window.getScreenWidth() / 2.0;
    }

    public static double y(Window window) {
        return warped ? y : window.getScreenHeight() / 2.0;
    }

    private static void warp(double cursorX, double cursorY) {
        warped = true;
        x = cursorX;
        y = cursorY;
    }
}
