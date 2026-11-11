package de.lardtom.propwash.api.race;

import java.util.Objects;

public record AssistInfo(String reason, String subject, int value, int reference) {
    public AssistInfo {
        Objects.requireNonNull(reason, "reason");
        subject = subject == null ? "" : subject;
    }
}
