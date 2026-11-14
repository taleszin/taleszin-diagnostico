package app.taleszin.diagnostico;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * Uma aba. O conteúdo é montado só na primeira visita (economiza memória e tempo de abertura).
 * tick() roda a cada 1 s apenas enquanto a aba está visível e o app em primeiro plano.
 */
abstract class Page {
    final MainActivity a;
    final String name;
    final int icon;
    private ScrollView root;
    boolean visible;

    Page(MainActivity a, String name, int icon) {
        this.a = a;
        this.name = name;
        this.icon = icon;
    }

    final View view() {
        if (root == null) {
            root = new ScrollView(a);
            root.setFillViewport(true);
            root.setVerticalFadingEdgeEnabled(false);
            LinearLayout body = new LinearLayout(a);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(24));
            root.addView(body, new ScrollView.LayoutParams(-1, -2));
            try {
                build(body);
            } catch (Throwable t) {
                Ui.note(body, "Não deu para montar esta tela: " + t);
            }
        }
        return root;
    }

    final boolean built() { return root != null; }

    abstract void build(LinearLayout body);

    /** Chamado quando a aba aparece (já montada). */
    void onShow() {}

    /** Chamado quando a aba sai de cena ou o app vai para segundo plano: soltar câmera, sensores etc. */
    void onHide() {}

    boolean live() { return false; }

    void tick() {}

    /** Texto para o relatório exportado. Roda fora da UI thread: não pode tocar em Views. */
    abstract void report(StringBuilder sb);

    // ---- ajudantes do relatório ----
    static void h(StringBuilder sb, String t) {
        sb.append("\n== ").append(t.toUpperCase(Ui.BR)).append(" ==\n");
    }

    static void kv(StringBuilder sb, String k, Object v) {
        sb.append(k).append(": ").append(v == null ? "indisponível" : v).append('\n');
    }
}
