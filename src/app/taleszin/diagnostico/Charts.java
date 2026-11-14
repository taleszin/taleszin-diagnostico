package app.taleszin.diagnostico;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Gráficos e ícones desenhados à mão com Canvas (sem bibliotecas, sem VectorDrawable). */
final class Charts {
    private Charts() {}

    static Paint paint(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        return p;
    }

    // ------------------------------------------------------------------ barra simples
    /** Barra horizontal de progresso com cantos arredondados. */
    static final class Meter extends View {
        private float frac;
        private int color = Ui.ACCENT;
        private final Paint bg = paint(Ui.LINE), fg = paint(Ui.ACCENT);
        private final RectF r = new RectF();

        Meter(Context c) { super(c); }

        void set(float f, int col) {
            frac = Math.max(0, Math.min(1, f));
            color = col;
            invalidate();
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Ui.dp(10));
        }

        @Override protected void onDraw(Canvas c) {
            float h = getHeight(), rad = h / 2;
            r.set(0, 0, getWidth(), h);
            c.drawRoundRect(r, rad, rad, bg);
            if (frac > 0) {
                fg.setColor(color);
                r.set(0, 0, Math.max(h, getWidth() * frac), h);
                c.drawRoundRect(r, rad, rad, fg);
            }
        }
    }

    // ------------------------------------------------------------------ rosca
    static final class Donut extends View {
        private float[] vals = new float[0];
        private int[] cols = new int[0];
        private String center = "", sub = "";
        private final Paint arc = paint(0), txt = paint(Ui.TEXT), small = paint(Ui.MUTED);
        private final RectF r = new RectF();

        Donut(Context c) {
            super(c);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeCap(Paint.Cap.BUTT);
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTypeface(Ui.LIGHT);
            small.setTextAlign(Paint.Align.CENTER);
        }

        void set(float[] v, int[] c, String center, String sub) {
            vals = v; cols = c; this.center = center; this.sub = sub;
            invalidate();
        }

        @Override protected void onMeasure(int w, int h) {
            int s = Ui.dp(170);
            setMeasuredDimension(s, s);
        }

        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()), stroke = s * 0.14f;
            arc.setStrokeWidth(stroke);
            r.set(stroke / 2 + 2, stroke / 2 + 2, s - stroke / 2 - 2, s - stroke / 2 - 2);
            float sum = 0;
            for (float v : vals) sum += Math.max(0, v);
            arc.setColor(Ui.LINE);
            c.drawArc(r, 0, 360, false, arc);
            if (sum > 0) {
                float a = -90;
                for (int i = 0; i < vals.length; i++) {
                    float sweep = 360f * Math.max(0, vals[i]) / sum;
                    arc.setColor(cols[i]);
                    c.drawArc(r, a, Math.max(0, sweep - 1.5f), false, arc);
                    a += sweep;
                }
            }
            txt.setTextSize(s * 0.16f);
            c.drawText(center, s / 2, s / 2 + s * 0.04f, txt);
            small.setTextSize(s * 0.075f);
            c.drawText(sub, s / 2, s / 2 + s * 0.15f, small);
        }
    }

    // ------------------------------------------------------------------ barras horizontais
    static final class HBars extends View {
        private String[] labels = new String[0], right = new String[0];
        private float[] frac = new float[0];
        private int color = Ui.ACCENT;
        private final Paint lab = paint(Ui.TEXT), rt = paint(Ui.MUTED), bar = paint(Ui.ACCENT), bg = paint(Ui.CARD2);
        private final RectF r = new RectF();

        HBars(Context c) {
            super(c);
            lab.setTextSize(Ui.dp(12));
            rt.setTextSize(Ui.dp(12));
            rt.setTextAlign(Paint.Align.RIGHT);
        }

        void set(String[] labels, float[] frac, String[] right, int color) {
            this.labels = labels; this.frac = frac; this.right = right; this.color = color;
            requestLayout();
            invalidate();
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Math.max(Ui.dp(8), labels.length * Ui.dp(24)));
        }

        @Override protected void onDraw(Canvas c) {
            int row = Ui.dp(24), labW = Ui.dp(76), rightW = Ui.dp(110);
            float bw = getWidth() - labW - rightW;
            bar.setColor(color);
            for (int i = 0; i < labels.length; i++) {
                float y = i * row;
                c.drawText(labels[i], 0, y + row * 0.68f, lab);
                r.set(labW, y + row * 0.25f, labW + bw, y + row * 0.75f);
                c.drawRoundRect(r, Ui.dp(4), Ui.dp(4), bg);
                if (frac[i] > 0) {
                    r.right = labW + Math.max(Ui.dp(3), bw * Math.min(1, frac[i]));
                    c.drawRoundRect(r, Ui.dp(4), Ui.dp(4), bar);
                }
                if (right != null && i < right.length) c.drawText(right[i], getWidth(), y + row * 0.68f, rt);
            }
        }
    }

    // ------------------------------------------------------------------ linha (série temporal)
    static final class Line extends View {
        private int[] xs = new int[0];
        private float[] ys = new float[0];
        private int n;
        private float ymin, ymax;
        private int color = Ui.ACCENT;
        private String unit = "";
        private int decimals;
        private float refY = Float.NaN;
        private final Paint ln = paint(Ui.ACCENT), grid = paint(Ui.LINE), lab = paint(Ui.MUTED), ref = paint(Ui.RED);
        private final Path path = new Path();
        private String empty = "sem dados";
        private final java.text.SimpleDateFormat hhmm = new java.text.SimpleDateFormat("HH:mm", Ui.BR);
        private final java.util.Date date = new java.util.Date();

        Line(Context c) {
            super(c);
            ln.setStyle(Paint.Style.STROKE);
            ln.setStrokeWidth(Ui.dp(2));
            ln.setStrokeJoin(Paint.Join.ROUND);
            grid.setStrokeWidth(1);
            ref.setStrokeWidth(Ui.dp(1));
            lab.setTextSize(Ui.dp(10));
        }

        /** xs em segundos epoch; quebra a linha em lacunas maiores que 15 min. */
        void set(int[] xs, float[] ys, int n, float ymin, float ymax, int color, String unit, int decimals, float refY) {
            this.xs = xs; this.ys = ys; this.n = n; this.ymin = ymin; this.ymax = ymax;
            this.color = color; this.unit = unit; this.decimals = decimals; this.refY = refY;
            invalidate();
        }

        void setEmpty(String s) { empty = s; n = 0; invalidate(); }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Ui.dp(140));
        }

        private String fmt(float v) {
            return (decimals == 0 ? String.valueOf(Math.round(v)) : Ui.f2(v)) + unit;
        }

        @Override protected void onDraw(Canvas c) {
            float left = Ui.dp(44), top = Ui.dp(6), bottom = getHeight() - Ui.dp(18), right = getWidth() - Ui.dp(6);
            lab.setTextAlign(Paint.Align.RIGHT);
            for (int i = 0; i <= 2; i++) {
                float y = top + (bottom - top) * i / 2f;
                c.drawLine(left, y, right, y, grid);
                c.drawText(fmt(ymax - (ymax - ymin) * i / 2f), left - Ui.dp(4), y + Ui.dp(4), lab);
            }
            if (n < 2) {
                lab.setTextAlign(Paint.Align.CENTER);
                c.drawText(empty, (left + right) / 2, (top + bottom) / 2, lab);
                return;
            }
            float x0 = xs[0], x1 = xs[n - 1];
            if (x1 <= x0) x1 = x0 + 1;
            float span = ymax - ymin == 0 ? 1 : ymax - ymin;
            if (!Float.isNaN(refY) && refY > ymin && refY < ymax) {
                float y = bottom - (refY - ymin) / span * (bottom - top);
                c.drawLine(left, y, right, y, ref);
            }
            // rótulos de hora no eixo X
            lab.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i <= 3; i++) {
                float t = x0 + (x1 - x0) * i / 3f;
                float x = left + (right - left) * i / 3f;
                lab.setTextAlign(i == 0 ? Paint.Align.LEFT : i == 3 ? Paint.Align.RIGHT : Paint.Align.CENTER);
                date.setTime((long) t * 1000);
                c.drawText(hhmm.format(date), x, getHeight() - Ui.dp(4), lab);
            }
            path.reset();
            boolean pen = false;
            for (int i = 0; i < n; i++) {
                float x = left + (xs[i] - x0) / (x1 - x0) * (right - left);
                float y = bottom - (Math.max(ymin, Math.min(ymax, ys[i])) - ymin) / span * (bottom - top);
                if (!pen || (i > 0 && xs[i] - xs[i - 1] > 900)) path.moveTo(x, y); else path.lineTo(x, y);
                pen = true;
            }
            ln.setColor(color);
            c.drawPath(path, ln);
        }
    }

    // ------------------------------------------------------------------ ícones do menu
    static final int I_SUM = 0, I_CPU = 1, I_MEM = 2, I_BAT = 3, I_SCR = 4, I_STO = 5, I_NET = 6,
            I_SEN = 7, I_CAM = 8, I_SYS = 9, I_HEA = 10;

    static final class Icon extends View {
        private final int kind;
        private final Paint p = paint(Ui.MUTED);
        private final RectF r = new RectF();
        private final Path path = new Path();

        Icon(Context c, int kind) {
            super(c);
            this.kind = kind;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        void setColor(int col) { p.setColor(col); invalidate(); }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(Ui.dp(22), Ui.dp(22));
        }

        @Override protected void onDraw(Canvas c) {
            float s = getWidth(), u = s / 24f;
            p.setStrokeWidth(2 * u);
            p.setStyle(Paint.Style.STROKE);
            switch (kind) {
                case I_SUM:
                    for (int i = 0; i < 4; i++) {
                        float x = (i % 2) * 11 * u + 2 * u, y = (i / 2) * 11 * u + 2 * u;
                        r.set(x, y, x + 9 * u, y + 9 * u);
                        c.drawRoundRect(r, 2 * u, 2 * u, p);
                    }
                    break;
                case I_CPU:
                    r.set(6 * u, 6 * u, 18 * u, 18 * u);
                    c.drawRoundRect(r, 2 * u, 2 * u, p);
                    for (int i = 0; i < 3; i++) {
                        float k = (9 + 3 * i) * u;
                        c.drawLine(k, 2 * u, k, 5 * u, p); c.drawLine(k, 19 * u, k, 22 * u, p);
                        c.drawLine(2 * u, k, 5 * u, k, p); c.drawLine(19 * u, k, 22 * u, k, p);
                    }
                    break;
                case I_MEM:
                    r.set(2 * u, 7 * u, 22 * u, 16 * u);
                    c.drawRoundRect(r, 1.5f * u, 1.5f * u, p);
                    for (int i = 0; i < 4; i++) {
                        float x = (5 + i * 4.5f) * u;
                        c.drawLine(x, 16 * u, x, 19 * u, p);
                        r.set(x - u, 10 * u, x + 1.5f * u, 13 * u);
                        c.drawRect(r, p);
                    }
                    break;
                case I_BAT:
                    r.set(2 * u, 7 * u, 19 * u, 17 * u);
                    c.drawRoundRect(r, 2 * u, 2 * u, p);
                    c.drawLine(21 * u, 10 * u, 21 * u, 14 * u, p);
                    p.setStyle(Paint.Style.FILL);
                    r.set(4.5f * u, 9.5f * u, 12 * u, 14.5f * u);
                    c.drawRect(r, p);
                    break;
                case I_SCR:
                    r.set(2 * u, 4 * u, 22 * u, 17 * u);
                    c.drawRoundRect(r, 2 * u, 2 * u, p);
                    c.drawLine(12 * u, 17 * u, 12 * u, 20 * u, p);
                    c.drawLine(8 * u, 21 * u, 16 * u, 21 * u, p);
                    break;
                case I_STO:
                    r.set(4 * u, 3 * u, 20 * u, 9 * u);
                    c.drawOval(r, p);
                    c.drawLine(4 * u, 6 * u, 4 * u, 18 * u, p);
                    c.drawLine(20 * u, 6 * u, 20 * u, 18 * u, p);
                    r.set(4 * u, 15 * u, 20 * u, 21 * u);
                    c.drawArc(r, 0, 180, false, p);
                    r.set(4 * u, 9 * u, 20 * u, 15 * u);
                    c.drawArc(r, 0, 180, false, p);
                    break;
                case I_NET:
                    for (int i = 0; i < 3; i++) {
                        float rad = (4 + i * 5) * u;
                        r.set(12 * u - rad, 20 * u - rad, 12 * u + rad, 20 * u + rad);
                        c.drawArc(r, 225, 90, false, p);
                    }
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(12 * u, 20 * u, 1.6f * u, p);
                    break;
                case I_SEN:
                    c.drawCircle(12 * u, 12 * u, 9 * u, p);
                    path.reset();
                    path.moveTo(15 * u, 9 * u); path.lineTo(10.5f * u, 10.5f * u);
                    path.lineTo(9 * u, 15 * u); path.lineTo(13.5f * u, 13.5f * u); path.close();
                    c.drawPath(path, p);
                    break;
                case I_CAM:
                    r.set(2 * u, 7 * u, 22 * u, 20 * u);
                    c.drawRoundRect(r, 2 * u, 2 * u, p);
                    c.drawLine(8 * u, 7 * u, 10 * u, 4 * u, p);
                    c.drawLine(10 * u, 4 * u, 14 * u, 4 * u, p);
                    c.drawLine(14 * u, 4 * u, 16 * u, 7 * u, p);
                    c.drawCircle(12 * u, 13.5f * u, 3.6f * u, p);
                    break;
                case I_SYS:
                    c.drawCircle(12 * u, 12 * u, 3 * u, p);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI * i / 4;
                        c.drawLine((float) (12 * u + Math.cos(a) * 6.5f * u), (float) (12 * u + Math.sin(a) * 6.5f * u),
                                (float) (12 * u + Math.cos(a) * 9.5f * u), (float) (12 * u + Math.sin(a) * 9.5f * u), p);
                    }
                    c.drawCircle(12 * u, 12 * u, 6.5f * u, p);
                    break;
                case I_HEA:
                    path.reset();
                    path.moveTo(2 * u, 13 * u); path.lineTo(7 * u, 13 * u); path.lineTo(9.5f * u, 6 * u);
                    path.lineTo(13.5f * u, 19 * u); path.lineTo(16 * u, 13 * u); path.lineTo(22 * u, 13 * u);
                    c.drawPath(path, p);
                    break;
                default:
                    c.drawCircle(12 * u, 12 * u, 8 * u, p);
            }
        }
    }

    // ------------------------------------------------------------------ teste de toque
    static final class Touch extends View {
        private static final int[] COLS = {Ui.ACCENT, Ui.GREEN, Ui.BLUE, Ui.YELLOW, Ui.PURPLE, Ui.RED, 0xFF4DD0E1, 0xFFFF80AB, 0xFFAED581, 0xFFFFFFFF};
        private final float[] px = new float[10], py = new float[10];
        private final int[] pid = new int[10];
        private int count, max;
        private final Paint dot = paint(Ui.ACCENT), lab = paint(Ui.TEXT), bg = paint(Ui.CARD2), hint = paint(Ui.MUTED);
        private final RectF r = new RectF();
        Runnable onChange;

        Touch(Context c) {
            super(c);
            lab.setTextSize(Ui.dp(13));
            lab.setTextAlign(Paint.Align.CENTER);
            hint.setTextSize(Ui.dp(14));
            hint.setTextAlign(Paint.Align.CENTER);
        }

        int max() { return max; }
        int count() { return count; }
        void reset() { max = 0; count = 0; invalidate(); if (onChange != null) onChange.run(); }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Ui.dp(240));
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null)
                getParent().requestDisallowInterceptTouchEvent(true);
            int act = e.getActionMasked();
            count = 0;
            for (int i = 0; i < e.getPointerCount() && count < 10; i++) {
                if ((act == MotionEvent.ACTION_POINTER_UP) && i == e.getActionIndex()) continue;
                if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) continue;
                px[count] = e.getX(i); py[count] = e.getY(i); pid[count] = e.getPointerId(i);
                count++;
            }
            if (count > max) max = count;
            invalidate();
            if (onChange != null) onChange.run();
            return true;
        }

        @Override protected void onDraw(Canvas c) {
            r.set(0, 0, getWidth(), getHeight());
            c.drawRoundRect(r, Ui.dp(12), Ui.dp(12), bg);
            if (count == 0)
                c.drawText("Encoste vários dedos aqui ao mesmo tempo", getWidth() / 2f, getHeight() / 2f, hint);
            float rad = Ui.dp(34);
            for (int i = 0; i < count; i++) {
                int col = COLS[pid[i] % COLS.length];
                dot.setColor(Ui.alpha(col, 0x55));
                c.drawCircle(px[i], py[i], rad, dot);
                dot.setColor(col);
                c.drawCircle(px[i], py[i], rad * 0.35f, dot);
                c.drawText("dedo " + (pid[i] + 1), px[i], py[i] - rad - Ui.dp(4), lab);
            }
        }
    }
}
