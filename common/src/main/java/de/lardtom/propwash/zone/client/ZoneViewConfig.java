package de.lardtom.propwash.zone.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.lardtom.propwash.config.JsonConfig;
import de.lardtom.propwash.zone.ZoneView;

public final class ZoneViewConfig {
    public static final JsonConfig<ZoneViewConfig> CONFIG = JsonConfig.create("zones", ZoneViewConfig.class, ZoneViewConfig::new, ZoneViewConfig::validate,
            ZoneViewConfig::migrate);
    private static final int VERSION = 2;

    public int version = VERSION;
    public ZoneView.Show show = ZoneView.DEFAULT_SHOW;

    public static ZoneViewConfig get() {
        return CONFIG.get();
    }

    public static ZoneView.Show show() {
        ZoneView.Show value = get().show;
        return value == null ? ZoneView.DEFAULT_SHOW : value;
    }

    public static void set(ZoneView.Show value) {
        ZoneViewConfig next = get().copy();
        next.show = value;
        CONFIG.set(next);
    }

    public static void migrate(JsonObject json) {
        if (json.has("version")) {
            return;
        }
        JsonElement show = json.get("show");
        if (show != null && show.isJsonPrimitive() && ZoneView.Show.TOOL.name().equals(show.getAsString())) {
            json.addProperty("show", ZoneView.DEFAULT_SHOW.name());
        }
    }

    static void validate(ZoneViewConfig config) {
        if (config.show == null) {
            config.show = ZoneView.DEFAULT_SHOW;
        }
    }

    public ZoneViewConfig copy() {
        ZoneViewConfig copy = new ZoneViewConfig();
        copy.version = this.version;
        copy.show = this.show;
        return copy;
    }
}
