package de.lardtom.propwash.api;

public record ApiVersion(int major, int minor, int patch) implements Comparable<ApiVersion> {
    public ApiVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("API-Version darf nicht negativ sein: " + major + "." + minor + "." + patch);
        }
    }

    public static ApiVersion parse(String text) {
        String trimmed = text == null ? "" : text.trim();
        String[] parts = trimmed.split("\\.", -1);
        if (trimmed.isEmpty() || parts.length > 3) {
            throw new IllegalArgumentException("Ungültige API-Version: " + text);
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return new ApiVersion(major, minor, patch);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ungültige API-Version: " + text, e);
        }
    }

    public boolean satisfies(ApiVersion required) {
        return this.major == required.major && this.compareTo(required) >= 0;
    }

    @Override
    public int compareTo(ApiVersion other) {
        if (this.major != other.major) {
            return Integer.compare(this.major, other.major);
        }
        if (this.minor != other.minor) {
            return Integer.compare(this.minor, other.minor);
        }
        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public String toString() {
        return this.major + "." + this.minor + "." + this.patch;
    }
}
