package de.lardtom.propwash.osd.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import de.lardtom.propwash.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class OsdFonts {
    static final Identifier RELOAD_LISTENER_ID = Constants.id("osd_glyphs");
    static final OsdGlyphs GLYPHS = new OsdGlyphs(OsdFonts::readCurrent, command -> Util.backgroundExecutor().execute(command));
    static final OsdTextCache CACHE = new OsdTextCache(GLYPHS, OsdTextCache.FRAME_BUDGET);
    private static final int BASELINE = 7;
    private static final int MAX_DEPTH = 8;
    private static @Nullable Object frame;
    private static int reloads;

    private OsdFonts() {
    }

    static void begin(Object frameToken) {
        if (frameToken == frame) {
            return;
        }
        frame = frameToken;
        GLYPHS.update(options());
        CACHE.nextFrame();
    }

    static int reloads() {
        return reloads;
    }

    static OsdGlyphs.Options options() {
        Minecraft minecraft = Minecraft.getInstance();
        Options options = minecraft == null ? null : minecraft.options;
        if (options == null) {
            return OsdGlyphs.Options.of(false, false);
        }
        return OsdGlyphs.Options.of(options.forceUnicodeFont().get(), options.japaneseGlyphVariants().get());
    }

    static final class ReloadListener extends SimplePreparableReloadListener<OsdGlyphs.Table> {
        @Override
        protected OsdGlyphs.Table prepare(ResourceManager manager, ProfilerFiller profiler) {
            return GLYPHS.build(options(), options -> read(manager, options));
        }

        @Override
        protected void apply(OsdGlyphs.Table table, ResourceManager manager, ProfilerFiller profiler) {
            reloads++;
            GLYPHS.install(table);
        }
    }

    private static Map<Integer, OsdGlyphs.Glyph> readCurrent(OsdGlyphs.Options options) {
        return read(Minecraft.getInstance().getResourceManager(), options);
    }

    static Map<Integer, OsdGlyphs.Glyph> read(ResourceManager manager, OsdGlyphs.Options options) {
        Map<Integer, OsdGlyphs.Glyph> map = new HashMap<>();
        collect(manager, options, Identifier.withDefaultNamespace("default"), map, 0);
        return map;
    }

    private static void collect(ResourceManager manager, OsdGlyphs.Options options, Identifier font, Map<Integer, OsdGlyphs.Glyph> out, int depth) {
        if (depth > MAX_DEPTH) {
            return;
        }
        List<Resource> stack = manager.getResourceStack(font.withPath(path -> "font/" + path + ".json"));
        for (int i = stack.size() - 1; i >= 0; i--) {
            JsonObject root;
            try (Reader reader = stack.get(i).openAsReader()) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                continue;
            }
            JsonArray providers = root.getAsJsonArray("providers");
            if (providers == null) {
                continue;
            }
            for (JsonElement element : providers) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject provider = element.getAsJsonObject();
                if (!accepted(provider, options)) {
                    continue;
                }
                String type = provider.has("type") ? provider.get("type").getAsString() : "";
                switch (type) {
                    case "reference" -> collect(manager, options, Identifier.parse(provider.get("id").getAsString()), out, depth + 1);
                    case "space" -> space(provider, out);
                    case "bitmap" -> bitmap(manager, provider, out);
                    default -> {
                    }
                }
            }
        }
    }

    private static boolean accepted(JsonObject provider, OsdGlyphs.Options options) {
        if (!provider.has("filter") || !provider.get("filter").isJsonObject()) {
            return true;
        }
        for (Map.Entry<String, JsonElement> entry : provider.getAsJsonObject("filter").entrySet()) {
            boolean value = switch (entry.getKey()) {
                case "uniform" -> options.uniform();
                case "jp" -> options.japanese();
                default -> false;
            };
            if (value != entry.getValue().getAsBoolean()) {
                return false;
            }
        }
        return true;
    }

    private static void space(JsonObject provider, Map<Integer, OsdGlyphs.Glyph> out) {
        JsonObject advances = provider.getAsJsonObject("advances");
        if (advances == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : advances.entrySet()) {
            int[] codepoints = entry.getKey().codePoints().toArray();
            float advance = entry.getValue().getAsFloat();
            if (codepoints.length == 1 && advance == Math.round(advance)) {
                out.putIfAbsent(codepoints[0], new OsdGlyphs.Glyph(0, 0, 0, new boolean[0], Math.round(advance)));
            }
        }
    }

    private static void bitmap(ResourceManager manager, JsonObject provider, Map<Integer, OsdGlyphs.Glyph> out) {
        JsonArray chars = provider.getAsJsonArray("chars");
        if (chars == null || chars.isEmpty() || !provider.has("file") || !provider.has("ascent")) {
            return;
        }
        int[][] grid = new int[chars.size()][];
        for (int row = 0; row < grid.length; row++) {
            grid[row] = chars.get(row).getAsString().codePoints().toArray();
        }
        int columns = grid[0].length;
        if (columns == 0) {
            return;
        }
        int ascent = provider.get("ascent").getAsInt();
        int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
        Identifier file = Identifier.parse(provider.get("file").getAsString()).withPrefix("textures/");
        try (InputStream stream = manager.open(file); NativeImage image = NativeImage.read(stream)) {
            int cellWidth = image.getWidth() / columns;
            int cellHeight = image.getHeight() / grid.length;
            boolean unitScale = cellHeight == height;
            for (int row = 0; row < grid.length; row++) {
                for (int column = 0; column < grid[row].length; column++) {
                    int codepoint = grid[row][column];
                    if (codepoint == 0 || out.containsKey(codepoint)) {
                        continue;
                    }
                    if (!unitScale) {
                        continue;
                    }
                    out.put(codepoint, glyph(image, column * cellWidth, row * cellHeight, cellWidth, cellHeight, ascent));
                }
            }
        } catch (Exception e) {
            Constants.LOG.debug("OSD-Schriftbild {} nicht lesbar", file, e);
        }
    }

    private static OsdGlyphs.Glyph glyph(NativeImage image, int x0, int y0, int cellWidth, int cellHeight, int ascent) {
        int width = 0;
        for (int x = cellWidth - 1; x >= 0 && width == 0; x--) {
            for (int y = 0; y < cellHeight; y++) {
                if (image.getLuminanceOrAlpha(x0 + x, y0 + y) != 0) {
                    width = x + 1;
                    break;
                }
            }
        }
        boolean[] mask = new boolean[width * cellHeight];
        for (int y = 0; y < cellHeight; y++) {
            for (int x = 0; x < width; x++) {
                mask[y * width + x] = image.getLuminanceOrAlpha(x0 + x, y0 + y) != 0;
            }
        }
        return new OsdGlyphs.Glyph(width, cellHeight, BASELINE - ascent, mask, width + 1);
    }
}
