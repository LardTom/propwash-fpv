package de.lardtom.propwash.join;

public record PartRef(String kind, String id, String hash) {
    public String namespace() {
        int colon = this.id.indexOf(':');
        return colon < 0 ? "minecraft" : this.id.substring(0, colon);
    }

    public String key() {
        return this.kind + "|" + this.id;
    }
}
