package de.lardtom.propwash.video;

public record ImageArea(int x, int y, int width, int height, int screenWidth, int screenHeight) {
    public ImageArea {
        if (width < 1 || height < 1 || screenWidth < 1 || screenHeight < 1 || x < 0 || y < 0 || x + width > screenWidth
                || y + height > screenHeight) {
            throw new IllegalArgumentException("Bildbereich " + x + "," + y + " " + width + "x" + height + " passt nicht in "
                    + screenWidth + "x" + screenHeight);
        }
    }

    public static ImageArea full(int screenWidth, int screenHeight) {
        int w = Math.max(1, screenWidth);
        int h = Math.max(1, screenHeight);
        return new ImageArea(0, 0, w, h, w, h);
    }

    public static ImageArea of(ImageFormat resolved, int screenWidth, int screenHeight) {
        int w = Math.max(1, screenWidth);
        int h = Math.max(1, screenHeight);
        if (!resolved.boxed()) {
            return new ImageArea(0, 0, w, h, w, h);
        }
        double aspect = ImageFormat.FOUR_THREE_ASPECT;
        if (w >= h * aspect) {
            int width = fit(w, h * aspect);
            return new ImageArea((w - width) / 2, 0, width, h, w, h);
        }
        int height = fit(h, w / aspect);
        return new ImageArea(0, (h - height) / 2, w, height, w, h);
    }

    private static int fit(int whole, double exact) {
        long margin = Math.max(0L, Math.round((whole - exact) * 0.5));
        return (int) Math.max(1L, whole - 2L * margin);
    }

    public boolean full() {
        return this.width == this.screenWidth && this.height == this.screenHeight;
    }

    public boolean pillarboxed() {
        return this.width < this.screenWidth;
    }

    public boolean letterboxed() {
        return this.height < this.screenHeight;
    }

    public double aspect() {
        return (double) this.width / this.height;
    }

    public double spanAspect() {
        return (double) this.width / this.screenHeight;
    }

    public double widthShare() {
        return (double) this.width / this.screenWidth;
    }

    public double heightShare() {
        return (double) this.height / this.screenHeight;
    }

    public int right() {
        return this.x + this.width;
    }

    public int bottom() {
        return this.y + this.height;
    }

    public boolean contains(int px, int py) {
        return px >= this.x && py >= this.y && px < this.right() && py < this.bottom();
    }

    public double imageTangentX(double screenTangentX) {
        return this.width == this.screenWidth ? screenTangentX : screenTangentX * this.widthShare();
    }

    public double wideTangentY(double screenTangentX, double screenTangentY) {
        return this.full() ? screenTangentY : this.imageTangentX(screenTangentX) * this.screenHeight / this.screenWidth;
    }

    public double imageTangentY(double screenTangentY) {
        return this.height == this.screenHeight ? screenTangentY : screenTangentY * this.heightShare();
    }
}
