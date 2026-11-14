package app.taleszin.diagnostico;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Casca do app. Deitado: menu lateral à esquerda e conteúdo à direita.
 * Em pé: abas roláveis no topo. A troca de orientação não recria a Activity
 * (configChanges); só remonta a casca e reaproveita as abas já montadas.
 */
public class MainActivity extends Activity {
    private final Handler h = new Handler();
    Page[] pages;
    private int cur;
    private boolean resumed;
    private FrameLayout host;
    private View[] items;
    private Charts.Icon[] icons;
    private TextView[] labels;
    private HorizontalScrollView tabScroll;

    private final Runnable ticker = new Runnable() {
        public void run() {
            if (!resumed) return;
            Page p = pages[cur];
            if (p.live() && p.built()) {
                try { p.tick(); } catch (Throwable ignored) { }
            }
            h.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(Ui.BG));
        pages = new Page[]{
                new SummaryPage(this), new CpuPage(this), new MemoryPage(this), new BatteryPage(this),
                new ScreenPage(this), new StoragePage(this), new NetworkPage(this), new SensorsPage(this),
                new CameraPage(this), new SystemPage(this), new HealthPage(this)
        };
        if (b != null) cur = Math.max(0, Math.min(pages.length - 1, b.getInt("tab", 0)));
        buildShell();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", cur);
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        buildShell();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        safeShow(pages[cur]);
        h.removeCallbacks(ticker);
        h.post(ticker);
    }

    @Override protected void onPause() {
        super.onPause();
        resumed = false;
        h.removeCallbacks(ticker);
        safeHide(pages[cur]);
    }

    private void safeShow(Page p) {
        p.visible = true;
        try { p.onShow(); } catch (Throwable ignored) { }
    }

    private void safeHide(Page p) {
        p.visible = false;
        try { p.onHide(); } catch (Throwable ignored) { }
    }

    private boolean landscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void buildShell() {
        if (host != null) host.removeAllViews();
        boolean land = landscape();
        items = new View[pages.length];
        icons = new Charts.Icon[pages.length];
        labels = new TextView[pages.length];

        host = new FrameLayout(this);
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(land ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);

        for (int i = 0; i < pages.length; i++) {
            final int idx = i;
            LinearLayout it = new LinearLayout(this);
            it.setOrientation(LinearLayout.HORIZONTAL);
            it.setGravity(Gravity.CENTER_VERTICAL);
            it.setPadding(Ui.dp(12), Ui.dp(9), Ui.dp(14), Ui.dp(9));
            Charts.Icon ic = new Charts.Icon(this, pages[i].icon);
            TextView t = Ui.text(this, pages[i].name, 14, Ui.MUTED, Ui.SANS);
            t.setPadding(Ui.dp(10), 0, 0, 0);
            t.setSingleLine(true);
            it.addView(ic);
            it.addView(t);
            it.setClickable(true);
            it.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { select(idx); }
            });
            LinearLayout.LayoutParams lp = land ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(-2, -2);
            if (land) { lp.leftMargin = Ui.dp(8); lp.rightMargin = Ui.dp(8); lp.bottomMargin = Ui.dp(2); }
            else { lp.leftMargin = Ui.dp(4); lp.topMargin = Ui.dp(6); lp.bottomMargin = Ui.dp(6); }
            nav.addView(it, lp);
            items[i] = it; icons[i] = ic; labels[i] = t;
        }

        LinearLayout root = new LinearLayout(this);
        root.setBackgroundColor(Ui.BG);
        if (land) {
            root.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout side = new LinearLayout(this);
            side.setOrientation(LinearLayout.VERTICAL);
            side.setBackgroundColor(0xFF0B0E11);
            TextView brand = Ui.text(this, "Diagnóstico", 18, Ui.ACCENT, Ui.BOLD);
            brand.setPadding(Ui.dp(20), Ui.dp(12), Ui.dp(12), Ui.dp(8));
            side.addView(brand);
            ScrollView sc = new ScrollView(this);
            sc.setVerticalScrollBarEnabled(false);
            sc.addView(nav);
            side.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));
            root.addView(side, new LinearLayout.LayoutParams(Ui.dp(172), -1));
            root.addView(host, new LinearLayout.LayoutParams(0, -1, 1f));
            tabScroll = null;
        } else {
            root.setOrientation(LinearLayout.VERTICAL);
            tabScroll = new HorizontalScrollView(this);
            tabScroll.setHorizontalScrollBarEnabled(false);
            tabScroll.setBackgroundColor(0xFF0B0E11);
            tabScroll.addView(nav);
            root.addView(tabScroll, new LinearLayout.LayoutParams(-1, -2));
            root.addView(host, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        setContentView(root);
        attach(cur);
    }

    private void attach(int i) {
        View v = pages[i].view();
        ViewGroup parent = (ViewGroup) v.getParent();
        if (parent != null) parent.removeView(v);
        host.removeAllViews();
        host.addView(v, new FrameLayout.LayoutParams(-1, -1));
        for (int k = 0; k < items.length; k++) {
            boolean sel = k == i;
            items[k].setBackgroundDrawable(sel ? Ui.round(Ui.alpha(Ui.ACCENT, 0x26), 12) : null);
            icons[k].setColor(sel ? Ui.ACCENT : Ui.MUTED);
            labels[k].setTextColor(sel ? Ui.ACCENT : Ui.MUTED);
        }
        if (tabScroll != null) {
            final View it = items[i];
            tabScroll.post(new Runnable() {
                public void run() {
                    int x = it.getLeft() - (tabScroll.getWidth() - it.getWidth()) / 2;
                    tabScroll.smoothScrollTo(Math.max(0, x), 0);
                }
            });
        }
    }

    void select(int i) {
        if (i == cur && host.getChildCount() > 0) return;
        if (resumed) safeHide(pages[cur]);
        cur = i;
        attach(i);
        if (resumed) {
            safeShow(pages[i]);
            h.removeCallbacks(ticker);
            h.post(ticker);
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacks(ticker);
    }
}
