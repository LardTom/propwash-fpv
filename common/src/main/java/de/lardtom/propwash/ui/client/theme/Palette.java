package de.lardtom.propwash.ui.client.theme;

public final class Palette {
    public static final int BACKDROP = 0xC8060606;
    public static final int SHADOW = 0xFF0B0B0B;
    public static final int BACKGROUND = 0xFF1A1A1A;
    public static final int PANEL = 0xFF222222;
    public static final int PANEL_HEADER = 0xFF2B2B2B;
    public static final int HEADER = 0xFF252525;
    public static final int SIDEBAR = 0xFF202020;
    public static final int FOOTER = 0xFF252525;
    public static final int BORDER = 0xFF383838;
    public static final int BORDER_STRONG = 0xFF4C4C4C;
    public static final int FIELD = 0xFF121212;
    public static final int FIELD_HOVER = 0xFF1C1C1C;
    public static final int GRID = 0xFF242424;
    public static final int TOOLTIP = 0xFF161616;

    public static final int BUTTON = 0xFF353535;
    public static final int BUTTON_HOVER = 0xFF434343;
    public static final int BUTTON_PRESSED = 0xFF2A2A2A;
    public static final int DISABLED = 0xFF262626;
    public static final int DANGER = 0xFF5A2320;
    public static final int DANGER_HOVER = 0xFF7A2E2A;
    public static final int DANGER_PRESSED = 0xFF4A1C1A;

    public static final int ACCENT = 0xFFFFBB00;
    public static final int ACCENT_HOVER = 0xFFFFCB3D;
    public static final int ACCENT_PRESSED = 0xFFD99F00;
    public static final int ACCENT_DIM = 0xFF6B5210;
    public static final int ACCENT_FOCUS = 0xFF7D6014;
    public static final int ACCENT_TEXT = 0xFF161616;

    public static final int TEXT = 0xFFE8E8E8;
    public static final int TEXT_MUTED = 0xFFB0B0B0;
    public static final int TEXT_FAINT = 0xFF969696;
    public static final int TEXT_DISABLED = 0xFF5E5E5E;

    public static final int SUCCESS = 0xFF5BC85B;
    public static final int SUCCESS_BG = 0xFF1F3A1F;
    public static final int SUCCESS_BORDER = 0xFF9ADB9A;
    public static final int LED_OFF = 0xFF1E2E1E;
    public static final int LED_OFF_GLINT = 0xFF34463A;
    public static final int LED_BEZEL = 0xFF5A5A5A;
    public static final int WARNING = 0xFFF0A030;
    public static final int WARNING_BG = 0xFF3D2C12;
    public static final int ERROR = 0xFFF2665E;
    public static final int ERROR_BG = 0xFF3A1A18;
    public static final int INFO = 0xFF4EA1F3;

    public static final int HOVER_TINT = 0x1EFFFFFF;
    public static final int HOVER_SOFT = 0x10FFFFFF;
    public static final int ROW_STRIPE = 0x0DFFFFFF;
    public static final int SELECTION = 0x24FFFFFF;
    public static final int CROSSHAIR = 0x66FFFFFF;
    public static final int HOVER_OUTLINE = 0xA0FFFFFF;
    public static final int OVERLAY_GRID = 0x28FFFFFF;
    public static final int STAGE_TOP = 0xFF252525;
    public static final int STAGE_BOTTOM = 0xFF0F0F0F;
    public static final int STAGE_GRID = 0xFF262626;
    public static final int STAGE_EDGE_DARK = 0xFF0B0B0B;
    public static final int STAGE_EDGE_LIGHT = 0xFF383838;
    public static final int ICON_TILE_TOP = 0xFF6E6E6E;
    public static final int ICON_TILE_BOTTOM = 0xFF505050;
    public static final int ICON_TILE_HIGHLIGHT = 0xFF858585;
    public static final int PREVIEW_SKY_TOP = 0xFF3A5268;
    public static final int PREVIEW_SKY_BOTTOM = 0xFF1C242B;

    public static final int ROLL = 0xFFF06B63;
    public static final int PITCH = 0xFF5BC85B;
    public static final int YAW = 0xFF4EA1F3;
    public static final int THROTTLE = 0xFFC792EA;
    public static final int AUX = 0xFF8FA1B3;

    private Palette() {
    }

    public static int axisColor(int axis) {
        return switch (axis) {
            case 0 -> ROLL;
            case 1 -> PITCH;
            case 2 -> YAW;
            default -> THROTTLE;
        };
    }

    public static int withAlpha(int color, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (color & 0xFFFFFF);
    }

    public static int alpha(int color) {
        return color >>> 24;
    }

    public static int mix(int from, int to, float t) {
        float f = Math.clamp(t, 0.0F, 1.0F);
        int a = Math.round(alpha(from) + (alpha(to) - alpha(from)) * f);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * f);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * f);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
