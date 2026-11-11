package de.lardtom.propwash.drone.client.render;

import java.util.Arrays;

interface FpvQuadSelector {
    FpvQuadSelector ALL = new FpvQuadSelector() {
        @Override
        public boolean contains(float[] quad) {
            return true;
        }

        @Override
        public boolean[] select(float[] quads) {
            boolean[] selected = new boolean[quads.length / FpvGuardZone.QUAD_FLOATS];
            Arrays.fill(selected, true);
            return selected;
        }
    };

    boolean contains(float[] quad);

    boolean[] select(float[] quads);
}
