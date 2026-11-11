package de.lardtom.propwash.share;

public final class ShareFormatException extends Exception {
    private final ShareStatus status;

    public ShareFormatException(ShareStatus status) {
        super(status.name(), null, false, false);
        this.status = status;
    }

    public ShareStatus status() {
        return this.status;
    }
}
