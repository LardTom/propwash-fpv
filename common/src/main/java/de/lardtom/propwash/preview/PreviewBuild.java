package de.lardtom.propwash.preview;

public final class PreviewBuild {
    public static final boolean ENABLED = PreviewSwitch.ENABLED;
    private static final String PREFIX = "Propwash Preview ";
    private static final String SUFFIX = " · not final";
    private static final String UNKNOWN_VERSION = "?";
    public static final String TEXT = watermark(PreviewSwitch.VERSION);
    public static final String TITLE = windowTitle(PreviewSwitch.VERSION);

    private PreviewBuild() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static String watermark(String version) {
        return windowTitle(version) + SUFFIX;
    }

    public static String windowTitle(String version) {
        String shown = version.trim();
        return PREFIX + (shown.isEmpty() ? UNKNOWN_VERSION : shown);
    }
}
