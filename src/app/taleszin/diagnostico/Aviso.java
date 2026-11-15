package app.taleszin.diagnostico;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Aviso do sistema (v1.2): janela curta que o m7s-powerguard abre por shell para explicar o que a
 * proteção de bateria fez ("modo economia", "fechei o app X", "desligando em 15 s").
 *   am start -n app.taleszin.diagnostico/.Aviso --es t "Título" --es m "Texto" --ei s 8
 * s = segundos até fechar sozinho (0 = fica até tocar em OK).
 */
public class Aviso extends Activity {
    private final Handler h = new Handler();
    private TextView titulo, texto;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Ui.init(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(24), Ui.dp(20), Ui.dp(24), Ui.dp(12));
        box.setBackground(Ui.round(Ui.CARD, 16));
        titulo = new TextView(this);
        titulo.setTextColor(Ui.ACCENT); titulo.setTextSize(20); titulo.setTypeface(Ui.BOLD);
        texto = new TextView(this);
        texto.setTextColor(Ui.TEXT); texto.setTextSize(16);
        texto.setPadding(0, Ui.dp(10), 0, Ui.dp(14)); texto.setLineSpacing(0, 1.15f);
        TextView ok = new TextView(this);
        ok.setText("OK"); ok.setTextColor(Ui.ACCENT); ok.setTextSize(16); ok.setTypeface(Ui.BOLD);
        ok.setGravity(Gravity.END); ok.setPadding(Ui.dp(16), Ui.dp(10), 0, Ui.dp(6));
        ok.setOnClickListener(new android.view.View.OnClickListener() { public void onClick(android.view.View v) { finish(); } });
        box.addView(titulo); box.addView(texto); box.addView(ok);
        setContentView(box, new android.view.ViewGroup.LayoutParams(Ui.dp(400), -2));
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        mostrar(getIntent());
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); mostrar(i); }

    private void mostrar(Intent i) {
        String t = i.getStringExtra("t"), m = i.getStringExtra("m");
        titulo.setText(t != null ? t : "Taleszin OS");
        texto.setText(m != null ? m : "");
        h.removeCallbacksAndMessages(null);
        int s = i.getIntExtra("s", 0);
        if (s > 0) h.postDelayed(new Runnable() { public void run() { finish(); } }, s * 1000L);
    }

    @Override protected void onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy(); }
}
