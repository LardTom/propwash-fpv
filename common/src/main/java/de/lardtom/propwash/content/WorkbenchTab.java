package de.lardtom.propwash.content;

public enum WorkbenchTab {
    BUILD,
    RECYCLE;

    public String key() {
        return this == BUILD ? "build" : "recycle";
    }

    public static WorkbenchTab of(int index) {
        return index == RECYCLE.ordinal() ? RECYCLE : BUILD;
    }
}
