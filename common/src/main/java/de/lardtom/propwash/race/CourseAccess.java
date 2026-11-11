package de.lardtom.propwash.race;

public enum CourseAccess {
    ALLOWED,
    NO_BUILD,
    OTHER_DIMENSION,
    NOT_OWNER,
    TOO_FAR,
    WORLD_COURSE,
    OWNERLESS,
    ADMIN_ONLY;

    public boolean allowed() {
        return this == ALLOWED;
    }

    public static boolean mayChange(boolean owner, boolean operator) {
        return owner || operator;
    }

    public static CourseAccess edit(boolean build, boolean sameDimension, boolean ownerless, boolean owner, boolean operator) {
        if (!build) {
            return NO_BUILD;
        }
        if (!sameDimension) {
            return OTHER_DIMENSION;
        }
        if (mayChange(owner, operator)) {
            return ALLOWED;
        }
        return ownerless ? OWNERLESS : NOT_OWNER;
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

    public static CourseAccess creativeFc(boolean admin) {
        return admin ? ALLOWED : ADMIN_ONLY;
    }

    public static boolean near(double distanceSquared, double range) {
        return distanceSquared <= range * range;
    }
}
