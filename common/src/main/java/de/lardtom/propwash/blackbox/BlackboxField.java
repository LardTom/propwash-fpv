package de.lardtom.propwash.blackbox;

public record BlackboxField(String name, boolean signed, int iPredictor, int iEncoding, int pPredictor, int pEncoding) {
    public static final int PREDICT_ZERO = 0;
    public static final int PREDICT_PREVIOUS = 1;
    public static final int PREDICT_STRAIGHT_LINE = 2;
    public static final int PREDICT_AVERAGE_2 = 3;
    public static final int PREDICT_MOTOR_0 = 5;
    public static final int PREDICT_INC = 6;
    public static final int PREDICT_HOME_COORD = 7;
    public static final int PREDICT_VBATREF = 9;
    public static final int PREDICT_LAST_MAIN_FRAME_TIME = 10;
    public static final int PREDICT_MINMOTOR = 11;

    public static final int SIGNED_VB = 0;
    public static final int UNSIGNED_VB = 1;
    public static final int NEG_14BIT = 3;
    public static final int TAG8_8SVB = 6;
    public static final int TAG2_3S32 = 7;
    public static final int TAG8_4S16 = 8;
    public static final int NULL = 9;

    public static BlackboxField simple(String name, boolean signed, int predictor, int encoding) {
        return new BlackboxField(name, signed, predictor, encoding, predictor, encoding);
    }
}
