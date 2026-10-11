package de.lardtom.propwash.content.client;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ItemSizeRule {
    public static final float TOLERANCE = 1.02F;
    public static final float GROUND_MAX = 0.5F;
    public static final float GROUND_MIN = 0.15F;
    public static final float GROUND_BULK = 0.3F;
    public static final float HAND_FULL_MM = 180.0F;
    public static final float HAND_EXPONENT = 0.6F;
    public static final float HAND_MIN_FACTOR = 0.25F;
    public static final float PART_FIRST_PERSON_VIEW = 0.6F;
    public static final float PART_THIRD_PERSON_VIEW = 0.47F;
    public static final float PART_HAND_BOX = 0.95F;

    public record Limit(float view, float depth, float min, float bulk) {
        public Limit(float view, float depth, float min) {
            this(view, depth, min, Float.POSITIVE_INFINITY);
        }

        public boolean fits(float x, float y, float z) {
            return this.fitsSize(x, y, z) && this.fitsBulk(x, y, z);
        }

        public boolean fitsSize(float x, float y, float z) {
            return Math.max(x, y) <= this.view * TOLERANCE && z <= this.depth * TOLERANCE && Math.max(Math.max(x, y), z) >= this.min;
        }

        public boolean fitsBulk(float x, float y, float z) {
            return bulkOf(x, y, z) <= this.bulk * TOLERANCE;
        }
    }

    private static final Map<String, Limit> LIMITS = Map.of(
            "gui", new Limit(1.04F, Float.POSITIVE_INFINITY, 0.5F),
            "ground", new Limit(GROUND_MAX, GROUND_MAX, GROUND_MIN, GROUND_BULK),
            "fixed", new Limit(1.5F, 1.0F, 0.3F),
            "on_shelf", new Limit(1.1F, 2.0F, 0.3F),
            "thirdperson_righthand", new Limit(1.2F, 1.2F, 0.1F),
            "thirdperson_lefthand", new Limit(1.2F, 1.2F, 0.1F),
            "firstperson_righthand", new Limit(0.9F, 0.9F, 0.1F),
            "firstperson_lefthand", new Limit(0.9F, 0.9F, 0.1F),
            "head", new Limit(1.0F, 1.3F, 0.1F));

    public static final List<String> CONTEXTS = List.of("gui", "ground", "fixed", "on_shelf", "thirdperson_righthand", "thirdperson_lefthand",
            "firstperson_righthand", "firstperson_lefthand", "head");

    private ItemSizeRule() {
    }

    public static Optional<Limit> limit(String context) {
        return Optional.ofNullable(LIMITS.get(context));
    }

    public static boolean isHand(String context) {
        return context.startsWith("firstperson_") || context.startsWith("thirdperson_");
    }

    public static float handFactor(float lengthMm) {
        if (!(lengthMm > 0.0F) || !Float.isFinite(lengthMm)) {
            return 1.0F;
        }
        return Math.clamp((float) Math.pow(lengthMm / HAND_FULL_MM, HAND_EXPONENT), HAND_MIN_FACTOR, 1.0F);
    }

    public static Optional<Limit> partLimit(String context, float lengthMm) {
        Limit general = LIMITS.get(context);
        if (general == null || !isHand(context)) {
            return Optional.ofNullable(general);
        }
        float factor = handFactor(lengthMm);
        float view = context.startsWith("firstperson_") ? PART_FIRST_PERSON_VIEW : PART_THIRD_PERSON_VIEW;
        return Optional.of(new Limit(Math.min(general.view(), view * factor), Math.min(general.depth(), PART_HAND_BOX * factor), general.min()));
    }

    public static float bulkOf(float x, float y, float z) {
        return (float) Math.cbrt(Math.max(0.0, (double) x * y * z));
    }
}
