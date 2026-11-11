package de.lardtom.propwash.share;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ShareDecoded(ShareStatus status, String format, Optional<ShareContent> content, List<ShareLayer> layers,
                           List<NamespaceReport> namespaces, List<Identifier> unknownParts, List<Identifier> wrongKind, List<Rename> renamed,
                           List<String> skipped, List<Identifier> inactiveParts, Pw3Carry carry) {
    public ShareDecoded {
        Objects.requireNonNull(status, "status");
        format = Objects.requireNonNullElse(format, "");
        content = Objects.requireNonNullElse(content, Optional.empty());
        layers = List.copyOf(Objects.requireNonNullElse(layers, List.of()));
        namespaces = List.copyOf(Objects.requireNonNullElse(namespaces, List.of()));
        unknownParts = List.copyOf(Objects.requireNonNullElse(unknownParts, List.of()));
        wrongKind = List.copyOf(Objects.requireNonNullElse(wrongKind, List.of()));
        renamed = List.copyOf(Objects.requireNonNullElse(renamed, List.of()));
        skipped = List.copyOf(Objects.requireNonNullElse(skipped, List.of()));
        inactiveParts = List.copyOf(Objects.requireNonNullElse(inactiveParts, List.of()));
        carry = Objects.requireNonNullElse(carry, Pw3Carry.EMPTY);
        if (status.ok() != content.isPresent()) {
            throw new IllegalArgumentException("Inhalt genau dann, wenn der Status OK ist");
        }
    }

    public record Rename(Identifier from, Identifier to) {
        public Rename {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }
    }

    public static ShareDecoded failed(ShareStatus status) {
        return failed(status, "");
    }

    public static ShareDecoded failed(ShareStatus status, String format) {
        return new ShareDecoded(status, format, Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Pw3Carry.EMPTY);
    }

    public boolean ok() {
        return this.status.ok();
    }

    public boolean partsKnown() {
        return this.ok() && this.unknownParts.isEmpty() && this.wrongKind.isEmpty() && this.inactiveParts.isEmpty();
    }

    public boolean partsLocked() {
        for (NamespaceReport report : this.namespaces) {
            if (report.blocksParts()) {
                return true;
            }
        }
        return false;
    }

    public boolean legacy() {
        return "PW1".equals(this.format) || "PW2".equals(this.format) || ShareFormat.DRONE_EDITOR.equals(this.format);
    }

    public ShareContent require() {
        return this.content.orElseThrow(() -> new IllegalStateException("Sharecode nicht lesbar: " + this.status));
    }
}
