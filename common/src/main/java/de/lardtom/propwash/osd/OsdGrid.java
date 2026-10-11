package de.lardtom.propwash.osd;

public record OsdGrid(float left, float top, float width, float height) {
    public static final int COLS = 30;
    public static final int ROWS = 16;
    public static final float FONT_LINE_HEIGHT = 9.0F;
    public static final float TEXT_ROW_FILL = 0.62F;

    public OsdGrid {
        if (!(width > 0.0F) || !(height > 0.0F)) {
            throw new IllegalArgumentException("OSD-Raster braucht eine positive Größe: " + width + " x " + height);
        }
    }

    public static OsdGrid screen(int guiWidth, int guiHeight) {
        return new OsdGrid(0.0F, 0.0F, Math.max(1, guiWidth), Math.max(1, guiHeight));
    }

    public float cellWidth() {
        return this.width / COLS;
    }

    public float cellHeight() {
        return this.height / ROWS;
    }

    public float cellLeft(int col) {
        return this.left + col * this.cellWidth();
    }

    public float cellTop(int row) {
        return this.top + row * this.cellHeight();
    }

    public float anchorX(int col, OsdElement.Align align) {
        return align == OsdElement.Align.RIGHT ? this.cellLeft(col + 1) : this.cellLeft(col);
    }

    public float rowCenterY(int row) {
        return this.cellTop(row) + this.cellHeight() * 0.5F;
    }

    public float centerX() {
        return this.left + this.width * 0.5F;
    }

    public float centerY() {
        return this.top + this.height * 0.5F;
    }

    public int colAt(double x) {
        return clampCol((int) Math.floor((x - this.left) / this.cellWidth()));
    }

    public int rowAt(double y) {
        return clampRow((int) Math.floor((y - this.top) / this.cellHeight()));
    }

    public int nearestCol(double x) {
        return clampCol((int) Math.round((x - this.left) / this.cellWidth()));
    }

    public boolean contains(double x, double y) {
        return x >= this.left && y >= this.top && x < this.left + this.width && y < this.top + this.height;
    }

    public float textScale(float userScale) {
        float scale = this.cellHeight() * TEXT_ROW_FILL / FONT_LINE_HEIGHT * userScale;
        return Float.isFinite(scale) && scale > 0.0F ? scale : 1.0F;
    }

    public static float snapToPixels(float scale, double guiScale) {
        if (!(guiScale > 0.0) || !Float.isFinite(scale)) {
            return scale;
        }
        double physical = Math.max(1.0, Math.round(scale * guiScale));
        return (float) (physical / guiScale);
    }

    public static int clampCol(int col) {
        return Math.clamp(col, 0, COLS - 1);
    }

    public static int clampRow(int row) {
        return Math.clamp(row, 0, ROWS - 1);
    }
}
