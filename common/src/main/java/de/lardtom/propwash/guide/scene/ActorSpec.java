package de.lardtom.propwash.guide.scene;

import de.lardtom.propwash.build.DroneBuild;

import java.util.Objects;

public sealed interface ActorSpec permits ActorSpec.Drone, ActorSpec.Pilot, ActorSpec.Gate, ActorSpec.Item, ActorSpec.Block, ActorSpec.Remote {
    record Drone(DroneBuild build, boolean assembled, float scale) implements ActorSpec {
        public static final float STAGE_SCALE = 1.6F;

        public Drone {
            Objects.requireNonNull(build, "build");
            if (!(scale > 0.0F) || !Float.isFinite(scale)) {
                throw new IllegalArgumentException("scale must be positive");
            }
        }

        public Drone(DroneBuild build, boolean assembled) {
            this(build, assembled, 1.0F);
        }
    }

    record Pilot(String headItem, String handItem) implements ActorSpec {
        public Pilot {
            headItem = headItem == null ? "" : headItem;
            handItem = handItem == null ? "" : handItem;
        }
    }

    record Gate(String size, String mount, boolean flipped, float openingWidth, float openingHeight, String element, int variant, String region)
            implements ActorSpec {
        public static final String STANDING = "standing";
        public static final String GATE = "gate";
        public static final String FREEFORM = "freeform";
        public static final float FRAME = 0.5F;

        public Gate {
            region = region == null ? "" : region;
            Objects.requireNonNull(size, "size");
            mount = mount == null || mount.isBlank() ? STANDING : mount;
            openingWidth = Float.isFinite(openingWidth) && openingWidth > 0.0F ? openingWidth : 0.0F;
            openingHeight = Float.isFinite(openingHeight) && openingHeight > 0.0F ? openingHeight : 0.0F;
            element = element == null || element.isBlank() ? GATE : element;
            if (!GATE.equals(element)) {
                mount = STANDING;
                flipped = false;
            }
            if (!FREEFORM.equals(element)) {
                region = "";
            }
        }

        public Gate(String size, String mount, boolean flipped, float openingWidth, float openingHeight, String element, int variant) {
            this(size, mount, flipped, openingWidth, openingHeight, element, variant, "");
        }

        public static Gate freeform(String region, int variant) {
            return new Gate("medium", STANDING, false, 0.0F, 0.0F, FREEFORM, variant, region);
        }

        public boolean isFreeform() {
            return FREEFORM.equals(this.element);
        }

        public Gate(String size, String mount, boolean flipped, float openingWidth, float openingHeight) {
            this(size, mount, flipped, openingWidth, openingHeight, GATE, 0);
        }

        public Gate(String size, String mount, boolean flipped) {
            this(size, mount, flipped, 0.0F, 0.0F);
        }

        public static Gate element(String element, int variant, float openingWidth, float openingHeight) {
            return new Gate("medium", STANDING, false, openingWidth, openingHeight, element, variant);
        }

        public boolean isElement() {
            return !GATE.equals(this.element);
        }

        public Gate(String size) {
            this(size, STANDING, false);
        }

        public boolean customSize() {
            return this.openingWidth > 0.0F || this.openingHeight > 0.0F;
        }

        public float outerWidth(float standard) {
            return this.openingWidth > 0.0F ? this.openingWidth + 2.0F * FRAME : standard;
        }

        public float outerHeight(float standard) {
            return this.openingHeight > 0.0F ? this.openingHeight + 2.0F * FRAME : standard;
        }

        public boolean standing() {
            return STANDING.equals(this.mount);
        }
    }

    record Item(String itemId, float scale, boolean spin) implements ActorSpec {
        public Item {
            Objects.requireNonNull(itemId, "itemId");
        }
    }

    record Block(BlockSpec block) implements ActorSpec {
        public Block {
            Objects.requireNonNull(block, "block");
        }
    }

    record Remote(float scale) implements ActorSpec {
        public Remote {
            if (!(scale > 0.0F) || !Float.isFinite(scale)) {
                throw new IllegalArgumentException("scale must be positive");
            }
        }
    }
}
