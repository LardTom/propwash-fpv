package de.lardtom.propwash.video.client.record;

import de.lardtom.propwash.ui.client.theme.Palette;
import de.lardtom.propwash.ui.client.widget.LineBatch;
import de.lardtom.propwash.video.record.path.PathPreview;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class PathPreviewDraw {
    public static final int ACTUAL = 0xD0E8E8E8;
    public static final int ACTUAL_STROKE = 0x90E8E8E8;
    public static final int SAVED = Palette.ACCENT;
    public static final int SAVED_STROKE = 0xC0FFBB00;
    public static final int KEYFRAME = 0xFFFFE08A;
    public static final int WORST = Palette.ERROR;
    public static final int MARK = Palette.INFO;
    public static final int STROKE_TICKS = 10;
    private static final double MIN_STEP_PIXELS = 0.9;
    private static final double KEYFRAME_SPACING = 4.0;
    private static final float STROKE_PIXELS = 8.0F;
    private static final double MIN_RING_PIXELS = 5.0;
    private static final double MAX_RING_PIXELS = 40.0;
    private static final double GAP_LINE_PIXELS = 3.0;

    private PathPreviewDraw() {
    }

    public enum View {
        TOP,
        SIDE;

        public String key() {
            return this == TOP ? "top" : "side";
        }
    }

    public record Projection(PathPreview preview, View view, double[] truth, double[] actual, double[] saved, double[] along, double[] truthHeading,
                             double[] savedHeading) {
        public static Projection of(PathPreview p, View view) {
            int n = p.ticks();
            int samples = p.saved().length / 3;
            double[] truth = new double[n * 2];
            double[] actual = new double[samples * 2];
            double[] saved = new double[samples * 2];
            double[] truthHeading = new double[n * 2];
            double[] savedHeading = new double[n * 2];
            double[] t = p.truth();
            double[] r = p.actual();
            double[] s = p.saved();
            if (view == View.TOP) {
                for (int i = 0; i < n; i++) {
                    truth[i * 2] = t[i * 3];
                    truth[i * 2 + 1] = t[i * 3 + 2];
                    truthHeading[i * 2] = p.truthForward()[i * 3];
                    truthHeading[i * 2 + 1] = p.truthForward()[i * 3 + 2];
                    savedHeading[i * 2] = p.savedForward()[i * 3];
                    savedHeading[i * 2 + 1] = p.savedForward()[i * 3 + 2];
                }
                for (int k = 0; k < samples; k++) {
                    actual[k * 2] = r[k * 3];
                    actual[k * 2 + 1] = r[k * 3 + 2];
                    saved[k * 2] = s[k * 3];
                    saved[k * 2 + 1] = s[k * 3 + 2];
                }
                return new Projection(p, view, truth, actual, saved, new double[0], truthHeading, savedHeading);
            }
            double[] along = new double[Math.max(1, n)];
            double dirX = 0.0;
            double dirZ = 1.0;
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    double dx = t[i * 3] - t[(i - 1) * 3];
                    double dz = t[i * 3 + 2] - t[(i - 1) * 3 + 2];
                    along[i] = along[i - 1] + Math.sqrt(dx * dx + dz * dz);
                }
                if (i + 1 < n) {
                    double dx = t[(i + 1) * 3] - t[i * 3];
                    double dz = t[(i + 1) * 3 + 2] - t[i * 3 + 2];
                    double length = Math.sqrt(dx * dx + dz * dz);
                    if (length > 1.0E-4) {
                        dirX = dx / length;
                        dirZ = dz / length;
                    }
                }
                truth[i * 2] = along[i];
                truth[i * 2 + 1] = -t[i * 3 + 1];
                truthHeading[i * 2] = p.truthForward()[i * 3] * dirX + p.truthForward()[i * 3 + 2] * dirZ;
                truthHeading[i * 2 + 1] = -p.truthForward()[i * 3 + 1];
                savedHeading[i * 2] = p.savedForward()[i * 3] * dirX + p.savedForward()[i * 3 + 2] * dirZ;
                savedHeading[i * 2 + 1] = -p.savedForward()[i * 3 + 1];
            }
            int sub = p.substeps();
            for (int k = 0; k < samples; k++) {
                int i = Math.min(n - 1, k / sub);
                int j = Math.min(n - 1, i + 1);
                double f = (k % sub) / (double) sub;
                saved[k * 2] = along[i] + (along[j] - along[i]) * f;
                saved[k * 2 + 1] = -s[k * 3 + 1];
                actual[k * 2] = saved[k * 2];
                actual[k * 2 + 1] = -r[k * 3 + 1];
            }
            return new Projection(p, view, truth, actual, saved, along, truthHeading, savedHeading);
        }

        public int ticks() {
            return this.truth.length / 2;
        }

        public PathPreview.Gap gap() {
            return this.view == View.TOP ? this.preview.topGap() : this.preview.sideGap();
        }

        public double[] gapPoints(PathPreview.Gap gap) {
            if (this.view == View.TOP) {
                return new double[]{gap.ax(), gap.az(), gap.bx(), gap.bz()};
            }
            int n = this.along.length;
            int i = Math.max(0, Math.min(n - 1, gap.index()));
            int j = Math.min(n - 1, i + 1);
            double u = this.along[i] + (this.along[j] - this.along[i]) * Math.max(0.0, Math.min(1.0, gap.time() - i));
            return new double[]{u, -gap.ay(), u, -gap.by()};
        }
    }

    public record Frame(double centerU, double centerV, double scale) {
        public double x(double u, double screenCenterX) {
            return screenCenterX + (u - this.centerU) * this.scale;
        }

        public double y(double v, double screenCenterY) {
            return screenCenterY + (v - this.centerV) * this.scale;
        }

        public double u(double x, double screenCenterX) {
            return this.centerU + (x - screenCenterX) / this.scale;
        }

        public double v(double y, double screenCenterY) {
            return this.centerV + (y - screenCenterY) / this.scale;
        }

        public Frame zoomed(double factor, double anchorU, double anchorV) {
            double scaled = Math.max(1.0E-3, Math.min(1.0E5, this.scale * factor));
            double real = scaled / this.scale;
            return new Frame(anchorU + (this.centerU - anchorU) / real, anchorV + (this.centerV - anchorV) / real, scaled);
        }

        public Frame moved(double du, double dv) {
            return new Frame(this.centerU + du, this.centerV + dv, this.scale);
        }
    }

    public static Frame fit(Projection projection, int from, int to, int width, int height, double minHalfSpan) {
        int n = projection.ticks();
        if (n == 0) {
            return new Frame(0.0, 0.0, 1.0);
        }
        int a = Math.max(0, from);
        int b = Math.min(n - 1, to);
        double minU = Double.POSITIVE_INFINITY;
        double maxU = Double.NEGATIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;
        for (int i = a; i <= b; i++) {
            double u = projection.truth[i * 2];
            double v = projection.truth[i * 2 + 1];
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }
        double halfU = Math.max(minHalfSpan, (maxU - minU) * 0.5);
        double halfV = Math.max(minHalfSpan, (maxV - minV) * 0.5);
        double scale = Math.min(Math.max(1, width - 12) / (2.0 * halfU), Math.max(1, height - 12) / (2.0 * halfV));
        return new Frame((minU + maxU) * 0.5, (minV + maxV) * 0.5, scale);
    }

    public static Frame around(Projection projection, int index, double halfSpan, int width, int height) {
        int i = Math.max(0, Math.min(projection.ticks() - 1, index));
        double scale = Math.min(width, height) / (2.0 * Math.max(1.0E-3, halfSpan));
        return new Frame(projection.truth[i * 2], projection.truth[i * 2 + 1], scale);
    }

    public static Frame aroundGap(Projection projection, int width, int height) {
        PathPreview.Gap gap = projection.gap();
        double halfSpan = magnifierHalfSpan(gap);
        if (!gap.visible()) {
            return around(projection, projection.preview().worstIndex(), halfSpan, width, height);
        }
        double[] points = projection.gapPoints(gap);
        double scale = Math.min(width, height) / (2.0 * Math.max(1.0E-3, halfSpan));
        return new Frame((points[0] + points[2]) * 0.5, (points[1] + points[3]) * 0.5, scale);
    }

    public static int gapIndex(Projection projection) {
        PathPreview.Gap gap = projection.gap();
        return gap.visible() ? Math.max(0, Math.min(projection.ticks() - 1, (int) Math.round(gap.time()))) : projection.preview().worstIndex();
    }

    public static double gapTime(Projection projection) {
        PathPreview.Gap gap = projection.gap();
        return gap.visible() && projection.ticks() >= 2 ? Math.max(0.0, Math.min(projection.ticks() - 1, gap.time())) : -1.0;
    }

    public static double magnifierHalfSpan(PathPreview.Gap gap) {
        return Math.max(0.04, gap.distance() * 1.8);
    }

    public static void draw(GuiGraphicsExtractor g, LineBatch batch, Projection projection, Frame frame, int x0, int y0, int x1, int y1, int from,
                            int to, boolean strokes, boolean worst, int mark) {
        int n = projection.ticks();
        if (n < 2) {
            return;
        }
        int a = Math.max(0, from);
        int b = Math.min(n - 1, to);
        double cx = (x0 + x1) * 0.5;
        double cy = (y0 + y1) * 0.5;
        g.enableScissor(x0, y0, x1, y1);
        batch.clear();
        int sub = projection.preview().substeps();
        polyline(batch, projection.actual, a * sub, b * sub, frame, cx, cy, x0, y0, x1, y1, 1.0F, ACTUAL);
        polyline(batch, projection.saved, a * sub, b * sub, frame, cx, cy, x0, y0, x1, y1, 1.5F, SAVED);
        double lastX = Double.NaN;
        double lastY = Double.NaN;
        for (int key : projection.preview().keyIndices()) {
            if (key < a || key > b) {
                continue;
            }
            double x = frame.x(projection.truth[key * 2], cx);
            double y = frame.y(projection.truth[key * 2 + 1], cy);
            if (!inside(x, y, x0, y0, x1, y1, 4.0)) {
                continue;
            }
            if (!Double.isNaN(lastX) && Math.hypot(x - lastX, y - lastY) < KEYFRAME_SPACING) {
                continue;
            }
            batch.dot((float) x, (float) y, 1.6F, KEYFRAME);
            lastX = x;
            lastY = y;
        }
        if (strokes) {
            for (int i = a - Math.floorMod(a, STROKE_TICKS); i <= b; i += STROKE_TICKS) {
                if (i < a) {
                    continue;
                }
                stroke(batch, projection.truth, projection.truthHeading, i, i, frame, cx, cy, x0, y0, x1, y1, ACTUAL_STROKE);
                stroke(batch, projection.saved, projection.savedHeading, i * sub, i, frame, cx, cy, x0, y0, x1, y1, SAVED_STROKE);
            }
        }
        if (worst) {
            PathPreview.Gap gap = projection.gap();
            int w = gap.index();
            if (gap.visible() && w >= a && w <= b) {
                double[] points = projection.gapPoints(gap);
                double ax = frame.x(points[0], cx);
                double ay = frame.y(points[1], cy);
                double bx = frame.x(points[2], cx);
                double by = frame.y(points[3], cy);
                double half = Math.hypot(bx - ax, by - ay) * 0.5;
                if (half > GAP_LINE_PIXELS) {
                    batch.line((float) ax, (float) ay, (float) bx, (float) by, 1.0F, WORST);
                }
                float radius = (float) Math.min(MAX_RING_PIXELS, Math.max(MIN_RING_PIXELS, half + 4.0));
                batch.ring((float) ((ax + bx) * 0.5), (float) ((ay + by) * 0.5), radius, 1.2F, WORST);
            }
        }
        if (mark >= a && mark <= b) {
            double tx = frame.x(projection.truth[mark * 2], cx);
            double ty = frame.y(projection.truth[mark * 2 + 1], cy);
            int k = Math.min(projection.saved.length / 2 - 1, mark * sub);
            double sx = frame.x(projection.saved[k * 2], cx);
            double sy = frame.y(projection.saved[k * 2 + 1], cy);
            batch.line((float) tx, (float) ty, (float) sx, (float) sy, 1.0F, MARK);
            batch.ring((float) tx, (float) ty, 3.5F, 1.2F, ACTUAL);
            batch.ring((float) sx, (float) sy, 3.5F, 1.2F, SAVED);
        }
        batch.submit(g);
        g.disableScissor();
    }

    private static void polyline(LineBatch batch, double[] points, int from, int to, Frame frame, double cx, double cy, int x0, int y0, int x1,
                                 int y1, float width, int color) {
        int count = points.length / 2;
        int a = Math.max(0, from);
        int b = Math.min(count - 1, to);
        if (b <= a) {
            return;
        }
        double px = frame.x(points[a * 2], cx);
        double py = frame.y(points[a * 2 + 1], cy);
        for (int i = a + 1; i <= b; i++) {
            double x = frame.x(points[i * 2], cx);
            double y = frame.y(points[i * 2 + 1], cy);
            if (i < b && Math.abs(x - px) + Math.abs(y - py) < MIN_STEP_PIXELS) {
                continue;
            }
            if (!outside(px, py, x, y, x0, y0, x1, y1)) {
                batch.line((float) px, (float) py, (float) x, (float) y, width, color);
            }
            px = x;
            py = y;
        }
    }

    private static void stroke(LineBatch batch, double[] points, double[] heading, int point, int index, Frame frame, double cx, double cy, int x0,
                               int y0, int x1, int y1, int color) {
        if (point * 2 + 1 >= points.length || index * 2 + 1 >= heading.length) {
            return;
        }
        double x = frame.x(points[point * 2], cx);
        double y = frame.y(points[point * 2 + 1], cy);
        if (!inside(x, y, x0, y0, x1, y1, STROKE_PIXELS)) {
            return;
        }
        double hx = heading[index * 2];
        double hy = heading[index * 2 + 1];
        batch.line((float) x, (float) y, (float) (x + hx * STROKE_PIXELS), (float) (y + hy * STROKE_PIXELS), 1.0F, color);
    }

    private static boolean inside(double x, double y, int x0, int y0, int x1, int y1, double margin) {
        return x >= x0 - margin && x <= x1 + margin && y >= y0 - margin && y <= y1 + margin;
    }

    private static boolean outside(double ax, double ay, double bx, double by, int x0, int y0, int x1, int y1) {
        return (ax < x0 && bx < x0) || (ax > x1 && bx > x1) || (ay < y0 && by < y0) || (ay > y1 && by > y1);
    }
}
