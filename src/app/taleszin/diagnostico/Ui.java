package app.taleszin.diagnostico;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Cores, tipografia e blocos visuais reutilizados por todas as abas. */
final class Ui {
    private Ui() {}

    static final int BG = 0xFF101418, CARD = 0xFF1A2027, CARD2 = 0xFF232B34, LINE = 0xFF2C3540;
    static final int ACCENT = 0xFFFF7A1A, GREEN = 0xFF4CAF50, YELLOW = 0xFFFFC107, RED = 0xFFF44336;
    static final int TEXT = 0xFFE8ECEF, MUTED = 0xFF9AA4AE, BLUE = 0xFF4FA3FF, PURPLE = 0xFFB085F5;

    static final Locale BR = new Locale("pt", "BR");
    static float density = 1f;
    static Typeface SANS, LIGHT, BOLD;

    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        SANS = Typeface.create("sans-serif", Typeface.NORMAL);
        LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
        BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    }

    static int dp(float v) { return Math.round(v * density); }

    static GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    static int alpha(int color, int a) { return (color & 0x00FFFFFF) | (a << 24); }

    static TextView text(Context c, CharSequence s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        return t;
    }

    /** Título grande da aba + frase explicando para que ela serve. */
    static void header(LinearLayout body, String title, String sub) {
        Context c = body.getContext();
        TextView t = text(c, title, 26, TEXT, LIGHT);
        body.addView(t);
        if (sub != null) {
            TextView s = explain(c, sub);
            s.setPadding(0, dp(2), 0, dp(12));
            body.addView(s);
        }
    }

    static TextView explain(Context c, CharSequence s) {
        TextView t = text(c, s, 13, MUTED, SANS);
        t.setLineSpacing(0, 1.1f);
        return t;
    }

    /** Cartão: fundo #1A2027, cantos de 16 dp. */
    static LinearLayout card(android.view.ViewGroup parent, String title) {
        Context c = parent.getContext();
        LinearLayout k = new LinearLayout(c);
        k.setOrientation(LinearLayout.VERTICAL);
        k.setBackgroundDrawable(round(CARD, 16));
        k.setPadding(dp(14), dp(12), dp(14), dp(12));
        if (parent instanceof LinearLayout) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(10);
            parent.addView(k, lp);
        } else {
            parent.addView(k);
        }
        if (title != null) {
            TextView t = text(c, title, 16, ACCENT, BOLD);
            t.setPadding(0, 0, 0, dp(6));
            k.addView(t);
        }
        return k;
    }

    /** Linha rótulo → valor. Devolve o TextView do valor para atualizar ao vivo. */
    static TextView kv(LinearLayout parent, String label, String value) {
        Context c = parent.getContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(3), 0, dp(3));
        TextView l = text(c, label, 14, MUTED, SANS);
        TextView v = text(c, value == null ? "indisponível" : value, 14, TEXT, SANS);
        v.setGravity(Gravity.RIGHT);
        v.setTextIsSelectable(false);
        row.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(v, new LinearLayout.LayoutParams(0, -2, 1.4f));
        parent.addView(row);
        return v;
    }

    static TextView note(LinearLayout parent, String s) {
        TextView t = explain(parent.getContext(), s);
        t.setPadding(0, dp(6), 0, dp(2));
        parent.addView(t);
        return t;
    }

    static TextView big(LinearLayout parent, String s) {
        TextView t = text(parent.getContext(), s, 24, TEXT, LIGHT);
        parent.addView(t);
        return t;
    }

    static View divider(LinearLayout parent) {
        View v = new View(parent.getContext());
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(6);
        parent.addView(v, lp);
        return v;
    }

    static TextView button(LinearLayout parent, String label, View.OnClickListener l) {
        Context c = parent.getContext();
        TextView b = text(c, label, 15, 0xFF101418, BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundDrawable(round(ACCENT, 12));
        b.setPadding(dp(16), dp(10), dp(16), dp(10));
        b.setClickable(true);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(8);
        parent.addView(b, lp);
        return b;
    }

    // ---------- Notas (verde / amarelo / vermelho) ----------

    static final int OK = 0, WARN = 1, BAD = 2, NA = 3;

    static int gradeColor(int g) {
        switch (g) {
            case OK: return GREEN;
            case WARN: return YELLOW;
            case BAD: return RED;
            default: return MUTED;
        }
    }

    static String gradeWord(int g) {
        switch (g) {
            case OK: return "BOM";
            case WARN: return "ATENÇÃO";
            case BAD: return "RUIM";
            default: return "SEM DADO";
        }
    }

    /** Pílula colorida com a nota e o motivo. */
    static TextView grade(LinearLayout parent) {
        TextView t = text(parent.getContext(), "", 13, MUTED, SANS);
        t.setPadding(dp(10), dp(5), dp(10), dp(5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(6);
        parent.addView(t, lp);
        setGrade(t, NA, "medindo…");
        return t;
    }

    static void setGrade(TextView t, int g, String reason) {
        int col = gradeColor(g);
        t.setBackgroundDrawable(round(alpha(col, 0x2E), 10));
        t.setTextColor(col);
        t.setText("● " + gradeWord(g) + " — " + reason);
    }

    // ---------- Formatação ----------

    static String f1(double v) { return String.format(BR, "%.1f", v); }
    static String f2(double v) { return String.format(BR, "%.2f", v); }

    static String bytes(long b) {
        if (b < 0) return "indisponível";
        if (b < 1024) return b + " B";
        double kb = b / 1024.0;
        if (kb < 1024) return String.format(BR, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(BR, mb < 10 ? "%.1f MB" : "%.0f MB", mb);
        return String.format(BR, "%.2f GB", mb / 1024.0);
    }

    static String kb(long kb) { return kb < 0 ? "indisponível" : bytes(kb * 1024); }

    static String mhz(long khz) { return khz <= 0 ? "indisponível" : (khz / 1000) + " MHz"; }

    static String dur(long sec) {
        if (sec < 0) return "indisponível";
        long d = sec / 86400, h = (sec / 3600) % 24, m = (sec / 60) % 60;
        if (d > 0) return d + " d " + h + " h " + m + " min";
        if (h > 0) return h + " h " + m + " min";
        if (m > 0) return m + " min " + (sec % 60) + " s";
        return sec + " s";
    }

    static String pct(double p) { return String.format(BR, "%.0f%%", p); }

    /** Grade de cartões: 2 colunas quando há largura (deitado), 1 coluna em pé. */
    static final class Grid extends android.view.ViewGroup {
        private final int gap = dp(10);

        Grid(Context c) { super(c); }

        private int cols(int w) { return w >= dp(540) ? 2 : 1; }

        @Override protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws), cols = cols(w), cw = (w - gap * (cols - 1)) / cols;
            int h = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View ch = getChildAt(i);
                ch.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                rowH = Math.max(rowH, ch.getMeasuredHeight());
                if (i % cols == cols - 1 || i == getChildCount() - 1) { h += rowH + gap; rowH = 0; }
            }
            setMeasuredDimension(w, h);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int w = r - l, cols = cols(w), cw = (w - gap * (cols - 1)) / cols, y = 0;
            for (int i = 0; i < getChildCount(); i += cols) {
                int rowH = 0;
                for (int k = i; k < i + cols && k < getChildCount(); k++) rowH = Math.max(rowH, getChildAt(k).getMeasuredHeight());
                for (int k = i; k < i + cols && k < getChildCount(); k++) {
                    View ch = getChildAt(k);
                    int x = (k - i) * (cw + gap);
                    // estica para a altura da linha, para os cartões lado a lado ficarem alinhados
                    if (ch.getMeasuredHeight() != rowH)
                        ch.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(rowH, MeasureSpec.EXACTLY));
                    ch.layout(x, y, x + cw, y + rowH);
                }
                y += rowH + gap;
            }
        }
    }
}
