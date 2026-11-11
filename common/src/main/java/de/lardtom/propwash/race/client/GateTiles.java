package de.lardtom.propwash.race.client;

import de.lardtom.propwash.race.GateGeometry;
import de.lardtom.propwash.race.GateStacking;

public final class GateTiles {
    private static final double EPSILON = 1.0E-4;

    private GateTiles() {
    }

    public interface Sink {
        void piece(GateRenderer.Piece piece, float x, float y, boolean glow);
    }

    public static void layout(float width, float height, float frame, GateStacking.Base base, int joints, boolean banner, Sink sink) {
        float half = width * 0.5F;
        float inner = half - frame;
        boolean stacked = base == GateStacking.Base.STACKED;
        boolean left = (joints & GateGeometry.JOINT_LEFT) == 0;
        boolean right = (joints & GateGeometry.JOINT_RIGHT) == 0;
        boolean top = (joints & GateGeometry.JOINT_TOP) == 0;
        if (left) {
            sink.piece(stacked ? GateRenderer.Piece.TEE_LEFT : GateRenderer.Piece.CORNER_LEFT, -half, 0.0F, false);
        }
        if (right) {
            sink.piece(stacked ? GateRenderer.Piece.TEE_RIGHT : GateRenderer.Piece.CORNER_RIGHT, half, 0.0F, false);
        }
        if (left && top) {
            sink.piece(GateRenderer.Piece.CORNER_LEFT, -half, height - frame, false);
        }
        if (right && top) {
            sink.piece(GateRenderer.Piece.CORNER_RIGHT, half, height - frame, false);
        }
        for (float y = frame; y < height - frame - EPSILON; ) {
            boolean whole = height - frame - y >= 1.0F - EPSILON;
            if (left) {
                sink.piece(whole ? GateRenderer.Piece.POST_LEFT : GateRenderer.Piece.POST_LEFT_HALF, -half, y, false);
                sink.piece(whole ? GateRenderer.Piece.LED_POST_LEFT : GateRenderer.Piece.LED_POST_LEFT_HALF, -half, y, true);
            }
            if (right) {
                sink.piece(whole ? GateRenderer.Piece.POST_RIGHT : GateRenderer.Piece.POST_RIGHT_HALF, half, y, false);
                sink.piece(whole ? GateRenderer.Piece.LED_POST_RIGHT : GateRenderer.Piece.LED_POST_RIGHT_HALF, half, y, true);
            }
            y += whole ? 1.0F : 0.5F;
        }
        for (float x = -inner; x < inner - EPSILON; ) {
            boolean whole = inner - x >= 1.0F - EPSILON;
            if (base.bottomBar()) {
                sink.piece(whole ? GateRenderer.Piece.BAR_BOTTOM : GateRenderer.Piece.BAR_BOTTOM_HALF, x, 0.0F, false);
                sink.piece(whole ? GateRenderer.Piece.LED_BOTTOM_OUTER : GateRenderer.Piece.LED_BOTTOM_OUTER_HALF, x, 0.0F, true);
            }
            if (base != GateStacking.Base.LEGS) {
                sink.piece(whole ? GateRenderer.Piece.LED_BOTTOM_INNER : GateRenderer.Piece.LED_BOTTOM_INNER_HALF, x, 0.0F, true);
            }
            if (top) {
                sink.piece(whole ? GateRenderer.Piece.BAR_TOP : GateRenderer.Piece.BAR_TOP_HALF, x, height, false);
                sink.piece(whole ? GateRenderer.Piece.LED_TOP : GateRenderer.Piece.LED_TOP_HALF, x, height, true);
            }
            x += whole ? 1.0F : 0.5F;
        }
        if (base.feet()) {
            if (left) {
                sink.piece(GateRenderer.Piece.FOOT_LEFT, -half, 0.0F, false);
            }
            if (right) {
                sink.piece(GateRenderer.Piece.FOOT_RIGHT, half, 0.0F, false);
            }
        }
        if (banner && top) {
            sink.piece(GateRenderer.Piece.BANNER_CAP_LEFT, -half, height, false);
            sink.piece(GateRenderer.Piece.BANNER_CAP_RIGHT, half, height, false);
            for (float x = -half + GateRenderer.BANNER_CAP; x < half - GateRenderer.BANNER_CAP - EPSILON; ) {
                boolean whole = half - GateRenderer.BANNER_CAP - x >= 1.0F - EPSILON;
                sink.piece(whole ? GateRenderer.Piece.BANNER_PANEL : GateRenderer.Piece.BANNER_PANEL_HALF, x, height, false);
                x += whole ? 1.0F : 0.5F;
            }
        }
    }
}
