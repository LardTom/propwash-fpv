package de.lardtom.propwash.share;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public record ShareVersion(long major, long minor, long patch, Optional<String> suffix) implements Comparable<ShareVersion> {
    private static final Comparator<ShareVersion> ORDER = Comparator.comparingLong(ShareVersion::major).thenComparingLong(ShareVersion::minor)
            .thenComparingLong(ShareVersion::patch).thenComparing(v -> v.suffix().isEmpty()).thenComparing(v -> v.suffix().orElse(""));

    public ShareVersion {
        suffix = Objects.requireNonNullElse(suffix, Optional.<String>empty()).filter(s -> !s.isEmpty());
        if (major < 0 || minor < 0 || patch < 0 || major > Pw3Params.MAX_NUMBER || minor > Pw3Params.MAX_NUMBER || patch > Pw3Params.MAX_NUMBER) {
            throw new IllegalArgumentException("Version außerhalb des Bereichs");
        }
    }

    public static Optional<ShareVersion> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String rest = text.strip();
        int plus = rest.indexOf('+');
        if (plus >= 0) {
            rest = rest.substring(0, plus);
        }
        String suffix = null;
        int dash = rest.indexOf('-');
        if (dash >= 0) {
            suffix = rest.substring(dash + 1);
            rest = rest.substring(0, dash);
            if (!validSuffix(suffix)) {
                return Optional.empty();
            }
        }
        String[] parts = rest.split("\\.", -1);
        if (parts.length != 3) {
            return Optional.empty();
        }
        long[] numbers = new long[3];
        for (int i = 0; i < 3; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 10 || !parts[i].chars().allMatch(c -> c >= '0' && c <= '9')) {
                return Optional.empty();
            }
            numbers[i] = Long.parseLong(parts[i]);
            if (numbers[i] > Pw3Params.MAX_NUMBER) {
                return Optional.empty();
            }
        }
        return Optional.of(new ShareVersion(numbers[0], numbers[1], numbers[2], Optional.ofNullable(suffix)));
    }

    public static boolean validSuffix(String suffix) {
        if (suffix == null || suffix.isEmpty() || suffix.length() > Pw3Params.MAX_VERSION_SUFFIX_CHARS) {
            return false;
        }
        for (int i = 0; i < suffix.length(); i++) {
            char c = suffix.charAt(i);
            boolean ok = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '.' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    public ShareVersion release() {
        return new ShareVersion(this.major, this.minor, this.patch, Optional.empty());
    }

    @Override
    public int compareTo(ShareVersion other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return this.major + "." + this.minor + "." + this.patch + this.suffix.map(s -> "-" + s).orElse("");
    }
}
