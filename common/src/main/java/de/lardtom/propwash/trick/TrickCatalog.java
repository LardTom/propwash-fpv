package de.lardtom.propwash.trick;

import org.jspecify.annotations.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class TrickCatalog {
    public static final int MAX_QUERY = 40;

    public record Section(TrickFamily family, List<TrickType> tricks) {
    }

    private TrickCatalog() {
    }

    public static String normalize(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String plain = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace("ß", "ss");
        return plain.replaceAll("[^a-z0-9]+", " ").trim();
    }

    public static String haystack(TrickType type, Function<String, String> text) {
        return " " + normalize(text.apply(type.translationKey()) + " " + type.key().replace('_', ' ') + " " + text.apply(type.aliasKey())) + " ";
    }

    public static boolean matches(TrickType type, String query, Function<String, String> text) {
        String q = normalize(query.length() > MAX_QUERY ? query.substring(0, MAX_QUERY) : query);
        if (q.isEmpty()) {
            return true;
        }
        String hay = haystack(type, text);
        String compact = hay.replace(" ", "");
        for (String token : q.split(" ")) {
            if (!hay.contains(token) && !compact.contains(token)) {
                return false;
            }
        }
        return true;
    }

    public static List<TrickType> filter(String query, @Nullable TrickFamily family, Function<String, String> text) {
        List<TrickType> out = new ArrayList<>();
        for (TrickType type : TrickType.REPERTOIRE) {
            if ((family == null || type.family() == family) && matches(type, query, text)) {
                out.add(type);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static List<Section> sections(List<TrickType> visible) {
        List<Section> out = new ArrayList<>();
        for (TrickFamily family : TrickFamily.values()) {
            List<TrickType> list = new ArrayList<>();
            for (TrickType type : visible) {
                if (type.family() == family) {
                    list.add(type);
                }
            }
            if (!list.isEmpty()) {
                out.add(new Section(family, Collections.unmodifiableList(list)));
            }
        }
        return Collections.unmodifiableList(out);
    }
}
