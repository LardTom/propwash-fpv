package de.lardtom.propwash.race;

public enum CourseAccess {
    ALLOWED,
    NO_BUILD,
    OTHER_DIMENSION,
    NOT_OWNER,
    TOO_FAR,
    WORLD_COURSE;

    public boolean allowed() {
        return this == ALLOWED;
    }

    public static CourseAccess edit(boolean build, boolean sameDimension, boolean ownerless, boolean owner, boolean operator, boolean near) {
        if (!build) {
            return NO_BUILD;
        }
        if (!sameDimension) {
            return OTHER_DIMENSION;
        }
        if (owner || operator) {
            return ALLOWED;
        }
        if (!ownerless) {
            return NOT_OWNER;
        }
        return near ? ALLOWED : TOO_FAR;
    }

    public static CourseAccess delete(boolean build, boolean sameDimension, boolean owner, boolean operator, boolean worldCourse) {
        if (!build) {
            return NO_BUILD;
        }
        if (!sameDimension) {
            return OTHER_DIMENSION;
        }
        if (operator) {
            return ALLOWED;
        }
        if (!owner) {
            return NOT_OWNER;
        }
        return worldCourse ? WORLD_COURSE : ALLOWED;
    }

    public static boolean near(double distanceSquared, double range) {
        return distanceSquared <= range * range;
    }
}
