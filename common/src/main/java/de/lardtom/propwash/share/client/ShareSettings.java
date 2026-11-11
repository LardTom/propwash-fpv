package de.lardtom.propwash.share.client;

import de.lardtom.propwash.share.ShareLayer;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class ShareSettings {
    public static final Set<ShareLayer> DEFAULT_APPLY = Collections.unmodifiableSet(EnumSet.of(ShareLayer.PARTS, ShareLayer.PAINT, ShareLayer.TUNE));
    private static final EnumSet<ShareLayer> EXPORT = EnumSet.copyOf(ShareLayer.DEFAULT_EXPORT);
    private static final EnumSet<ShareLayer> APPLY = EnumSet.copyOf(DEFAULT_APPLY);
    private static boolean addonInfo = true;
    private static boolean substitutes;
    private static String lastInput = "";

    private ShareSettings() {
    }

    public static synchronized Set<ShareLayer> exportLayers() {
        return Collections.unmodifiableSet(EnumSet.copyOf(EXPORT));
    }

    public static synchronized boolean exports(ShareLayer layer) {
        return EXPORT.contains(layer);
    }

    public static synchronized void toggleExport(ShareLayer layer) {
        if (layer == ShareLayer.PARTS) {
            return;
        }
        if (!EXPORT.remove(layer)) {
            EXPORT.add(layer);
        }
    }

    public static synchronized void setExport(Set<ShareLayer> layers) {
        EXPORT.clear();
        EXPORT.addAll(layers);
        EXPORT.add(ShareLayer.PARTS);
    }

    public static synchronized Set<ShareLayer> applyLayers() {
        return Collections.unmodifiableSet(EnumSet.copyOf(APPLY));
    }

    public static synchronized boolean applies(ShareLayer layer) {
        return APPLY.contains(layer);
    }

    public static synchronized void toggleApply(ShareLayer layer) {
        if (layer == ShareLayer.NAME) {
            return;
        }
        if (!APPLY.remove(layer)) {
            APPLY.add(layer);
        }
    }

    public static synchronized void setApply(Set<ShareLayer> layers) {
        APPLY.clear();
        APPLY.addAll(layers);
        APPLY.remove(ShareLayer.NAME);
    }

    public static synchronized boolean addonInfo() {
        return addonInfo;
    }

    public static synchronized void addonInfo(boolean value) {
        addonInfo = value;
    }

    public static synchronized boolean substitutes() {
        return substitutes;
    }

    public static synchronized void substitutes(boolean value) {
        substitutes = value;
    }

    public static synchronized String lastInput() {
        return lastInput;
    }

    public static synchronized void lastInput(String text) {
        lastInput = text == null ? "" : text;
    }

    public static synchronized void reset() {
        EXPORT.clear();
        EXPORT.addAll(ShareLayer.DEFAULT_EXPORT);
        APPLY.clear();
        APPLY.addAll(DEFAULT_APPLY);
        addonInfo = true;
        substitutes = false;
        lastInput = "";
    }
}
