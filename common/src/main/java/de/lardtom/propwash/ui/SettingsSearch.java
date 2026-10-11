package de.lardtom.propwash.ui;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SettingsSearch {
    public static final int DEFAULT_LIMIT = 8;
    private static final int MIN_SYNONYM_PREFIX = 3;
    private static final int MIN_FUZZY = 3;

    public enum Kind {
        SETTING,
        WORLD_RULE,
        KEY,
        TAB
    }

    public record Entry(String id, Kind kind, String tabId, String tabTitle, String label, List<String> aliases, List<String> details,
                        List<String> keywords) {
        public Entry {
            aliases = List.copyOf(aliases);
            details = List.copyOf(details);
            keywords = List.copyOf(keywords);
        }
    }

    public record Hit(Entry entry, int score, int matchStart, int matchEnd) {
    }

    public record Folded(String text, int[] origin) {
        public int originStart(int index) {
            if (this.origin.length == 0) {
                return 0;
            }
            return this.origin[Math.max(0, Math.min(this.origin.length - 1, index))];
        }

        public int originEnd(int index, int sourceLength) {
            if (index >= this.origin.length) {
                return sourceLength;
            }
            return this.origin[Math.max(0, index)];
        }
    }

    public static final class Synonyms {
        public static final Synonyms NONE = new Synonyms(List.of());

        private final List<List<String>> groups;

        public Synonyms(List<List<String>> groups) {
            List<List<String>> clean = new ArrayList<>();
            for (List<String> group : groups) {
                LinkedHashSet<String> words = new LinkedHashSet<>();
                for (String word : group) {
                    String folded = fold(word).text();
                    if (!folded.isEmpty()) {
                        words.add(folded);
                    }
                }
                if (words.size() > 1) {
                    clean.add(List.copyOf(words));
                }
            }
            this.groups = List.copyOf(clean);
        }

        public static Synonyms parse(String text) {
            List<List<String>> groups = new ArrayList<>();
            for (String line : text.split("\\R")) {
                String trimmed = line.strip();
                if (trimmed.isEmpty()) {
                    continue;
                }
                List<String> words = new ArrayList<>();
                for (String word : trimmed.split("\\|")) {
                    if (!word.isBlank()) {
                        words.add(word.strip());
                    }
                }
                groups.add(words);
            }
            return new Synonyms(groups);
        }

        public List<List<String>> groups() {
            return this.groups;
        }

        public Set<String> expand(String raw) {
            Set<String> out = new LinkedHashSet<>();
            String token = fold(raw).text();
            if (token.length() < MIN_SYNONYM_PREFIX) {
                return out;
            }
            for (List<String> group : this.groups) {
                boolean hit = false;
                for (String word : group) {
                    if (word.equals(token) || (word.startsWith(token) && token.length() >= MIN_SYNONYM_PREFIX)) {
                        hit = true;
                        break;
                    }
                }
                if (hit) {
                    for (String word : group) {
                        if (!word.equals(token)) {
                            out.add(word);
                        }
                    }
                }
            }
            return out;
        }
    }

    private SettingsSearch() {
    }

    public static Folded fold(String raw) {
        return fold(raw, false);
    }

    public static Folded fold(String raw, boolean transliterate) {
        StringBuilder out = new StringBuilder(raw.length());
        List<Integer> origin = new ArrayList<>(raw.length());
        boolean space = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            String mapped = foldChar(c, transliterate);
            for (int k = 0; k < mapped.length(); k++) {
                char m = mapped.charAt(k);
                if (m == ' ') {
                    if (!space) {
                        out.append(' ');
                        origin.add(i);
                        space = true;
                    }
                    continue;
                }
                out.append(m);
                origin.add(i);
                space = false;
            }
        }
        int length = out.length();
        if (length > 0 && out.charAt(length - 1) == ' ') {
            out.setLength(length - 1);
            origin.removeLast();
        }
        int[] map = new int[origin.size()];
        for (int i = 0; i < map.length; i++) {
            map[i] = origin.get(i);
        }
        return new Folded(out.toString(), map);
    }

    private static String foldChar(char c, boolean transliterate) {
        if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
            return String.valueOf(c);
        }
        if (c >= 'A' && c <= 'Z') {
            return String.valueOf((char) (c + 32));
        }
        switch (c) {
            case 'ß', 'ẞ' -> {
                return "ss";
            }
            case 'ä', 'Ä' -> {
                return transliterate ? "ae" : "a";
            }
            case 'ö', 'Ö' -> {
                return transliterate ? "oe" : "o";
            }
            case 'ü', 'Ü' -> {
                return transliterate ? "ue" : "u";
            }
            case '°' -> {
                return " ";
            }
            default -> {
            }
        }
        if (Character.isLetterOrDigit(c)) {
            String decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            StringBuilder base = new StringBuilder();
            for (int i = 0; i < decomposed.length(); i++) {
                char d = decomposed.charAt(i);
                if (Character.getType(d) != Character.NON_SPACING_MARK) {
                    base.append(Character.toLowerCase(d));
                }
            }
            return base.isEmpty() ? " " : base.toString().toLowerCase(Locale.ROOT);
        }
        return " ";
    }

    public static List<String> tokens(String query) {
        String folded = fold(query).text();
        if (folded.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>(List.of(folded.split(" ")));
        out.removeIf(String::isEmpty);
        return List.copyOf(out);
    }

    public static List<Hit> search(String query, List<Entry> entries, Synonyms synonyms, int limit) {
        List<String> tokens = tokens(query);
        if (tokens.isEmpty() || limit <= 0) {
            return List.of();
        }
        String phrase = String.join(" ", tokens);
        Map<String, Set<String>> expansions = new HashMap<>();
        for (String token : tokens) {
            expansions.put(token, synonyms.expand(token));
        }
        List<Hit> hits = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Entry entry : entries) {
            Hit hit = score(entry, tokens, phrase, expansions);
            if (hit != null) {
                hits.add(hit);
            }
        }
        hits.sort(Comparator.comparingInt(Hit::score).reversed()
                .thenComparingInt(h -> h.entry().label().length())
                .thenComparing(h -> h.entry().label(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(h -> h.entry().id()));
        List<Hit> out = new ArrayList<>();
        for (Hit hit : hits) {
            String key = hit.entry().tabId() + "\u0000" + fold(hit.entry().label()).text() + "\u0000" + hit.entry().kind();
            if (!seen.add(key)) {
                continue;
            }
            out.add(hit);
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    private static Hit score(Entry entry, List<String> tokens, String phrase, Map<String, Set<String>> expansions) {
        Folded label = fold(entry.label());
        String labelText = label.text();
        String labelTranslit = fold(entry.label(), true).text();
        List<String> aliases = foldAll(entry.aliases());
        List<String> keywords = foldAll(entry.keywords());
        List<String> details = foldAll(entry.details());
        String tab = fold(entry.tabTitle()).text();
        int total = 0;
        for (String token : tokens) {
            int best = 0;
            best = Math.max(best, field(labelText, token, 100, 60));
            best = Math.max(best, field(labelTranslit, token, 100, 60));
            for (String alias : aliases) {
                best = Math.max(best, field(alias, token, 75, 45));
            }
            for (String keyword : keywords) {
                best = Math.max(best, field(keyword, token, 60, 35));
            }
            best = Math.max(best, field(tab, token, 30, 15));
            for (String synonym : expansions.getOrDefault(token, Set.of())) {
                best = Math.max(best, field(labelText, synonym, 55, 0));
                for (String alias : aliases) {
                    best = Math.max(best, field(alias, synonym, 45, 0));
                }
                for (String keyword : keywords) {
                    best = Math.max(best, field(keyword, synonym, 40, 0));
                }
                best = Math.max(best, field(tab, synonym, 20, 0));
            }
            for (String detail : details) {
                best = Math.max(best, field(detail, token, 25, token.length() >= MIN_FUZZY ? 12 : 0));
            }
            if (best == 0 && token.length() >= MIN_FUZZY && subsequence(labelText, token)) {
                best = 8;
            }
            if (best == 0) {
                return null;
            }
            total += best;
        }
        if (labelText.equals(phrase) || aliases.contains(phrase)) {
            total += 1000;
        } else if (labelText.startsWith(phrase)) {
            total += 300;
        } else if (wordPrefix(labelText, phrase)) {
            total += 150;
        } else if (labelText.contains(phrase)) {
            total += 80;
        }
        if (entry.kind() == Kind.TAB && labelText.startsWith(phrase)) {
            total += 120;
        }
        int start = -1;
        int end = -1;
        String first = tokens.getFirst();
        int at = wordPrefixIndex(labelText, phrase);
        int length = phrase.length();
        if (at < 0) {
            at = labelText.indexOf(phrase);
        }
        if (at < 0) {
            at = wordPrefixIndex(labelText, first);
            length = first.length();
        }
        if (at < 0) {
            at = labelText.indexOf(first);
            length = first.length();
        }
        if (at >= 0) {
            start = label.originStart(at);
            end = label.originEnd(at + length, entry.label().length());
        }
        return new Hit(entry, total, start, end);
    }

    private static List<String> foldAll(List<String> raw) {
        List<String> out = new ArrayList<>(raw.size());
        for (String s : raw) {
            String folded = fold(s).text();
            if (!folded.isEmpty()) {
                out.add(folded);
            }
            String translit = fold(s, true).text();
            if (!translit.equals(folded)) {
                out.add(translit);
            }
        }
        return out;
    }

    private static int field(String text, String token, int prefixScore, int containsScore) {
        if (text.isEmpty() || token.isEmpty()) {
            return 0;
        }
        if (wordPrefix(text, token)) {
            return text.startsWith(token) ? prefixScore + 10 : prefixScore;
        }
        if (containsScore > 0 && token.length() >= 2 && text.contains(token)) {
            return containsScore;
        }
        return 0;
    }

    static boolean wordPrefix(String text, String token) {
        return wordPrefixIndex(text, token) >= 0;
    }

    private static int wordPrefixIndex(String text, String token) {
        int from = 0;
        while (from <= text.length() - token.length()) {
            int at = text.indexOf(token, from);
            if (at < 0) {
                return -1;
            }
            if (at == 0 || text.charAt(at - 1) == ' ') {
                return at;
            }
            from = at + 1;
        }
        return -1;
    }

    private static boolean subsequence(String text, String token) {
        for (String word : text.split(" ")) {
            if (word.isEmpty() || word.charAt(0) != token.charAt(0) || token.length() * 5 < word.length() * 3) {
                continue;
            }
            int t = 0;
            for (int i = 0; i < word.length() && t < token.length(); i++) {
                if (word.charAt(i) == token.charAt(t)) {
                    t++;
                }
            }
            if (t == token.length()) {
                return true;
            }
        }
        return false;
    }
}
