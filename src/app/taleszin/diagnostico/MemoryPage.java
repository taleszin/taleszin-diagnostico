package app.taleszin.diagnostico;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class MemoryPage extends Page {
    MemoryPage(MainActivity a) { super(a, "Memória", Charts.I_MEM); }

    static final String ZRAM = "/sys/block/zram0/", KSM = "/sys/kernel/mm/ksm/";

    private Charts.Donut donut;
    private TextView usedTv, cacheTv, freeTv, totalTv, grade, thrTv, lowTv, selfTv;
    private TextView zSize, zSwap, zOrig, zComp, zRatio, zSave, zGrade;
    private TextView kRun, kShared, kSharing, kSave, kScans, kGrade;
    private LinearLayout procBox;
    private TextView procNote;
    private boolean loadingProcs;
    private int ticks;

    @Override void build(LinearLayout body) {
        Ui.header(body, "Memória (RAM)", "A RAM guarda os apps abertos. Quando falta, o Android fecha apps em segundo plano e tudo fica mais lento.");

        LinearLayout now = Ui.card(body, "Uso agora (ao vivo)");
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        donut = new Charts.Donut(a);
        row.addView(donut);
        LinearLayout leg = new LinearLayout(a);
        leg.setOrientation(LinearLayout.VERTICAL);
        leg.setPadding(Ui.dp(14), 0, 0, 0);
        row.addView(leg, new LinearLayout.LayoutParams(0, -2, 1f));
        usedTv = legend(leg, "Usada pelos apps", Ui.ACCENT);
        cacheTv = legend(leg, "Cache (reaproveitável)", Ui.BLUE);
        freeTv = legend(leg, "Livre (sem uso)", Ui.GREEN);
        totalTv = Ui.kv(leg, "Total", "…");
        now.addView(row);
        grade = Ui.grade(now);
        thrTv = Ui.kv(now, "Android começa a fechar apps abaixo de", "…");
        lowTv = Ui.kv(now, "Memória baixa agora?", "…");
        Ui.note(now, "Cache não é memória perdida: são arquivos e apps lidos recentemente guardados na RAM para abrir mais rápido. Se um app precisar, o sistema libera o cache na hora. Por isso o que conta é o “disponível” (livre + cache), não só o livre.");

        LinearLayout z = Ui.card(body, "zRAM (memória comprimida)");
        zSize = Ui.kv(z, "Tamanho reservado", "…");
        zSwap = Ui.kv(z, "Em uso (swap)", "…");
        zOrig = Ui.kv(z, "Dados originais", "…");
        zComp = Ui.kv(z, "Depois de comprimir", "…");
        zRatio = Ui.kv(z, "Taxa de compressão", "…");
        zSave = Ui.kv(z, "RAM economizada", "…");
        zGrade = Ui.grade(z);
        Ui.note(z, "O zRAM é como “zipar” na própria RAM as partes de apps que estão paradas. Em vez de fechar o app, o sistema comprime o que ele não está usando: cabe mais coisa nos 512 MB. Custa um pouco de CPU para comprimir e descomprimir.");

        LinearLayout k = Ui.card(body, "KSM (páginas repetidas)");
        kRun = Ui.kv(k, "Ligado", "…");
        kShared = Ui.kv(k, "Páginas únicas guardadas", "…");
        kSharing = Ui.kv(k, "Cópias que viraram uma só", "…");
        kSave = Ui.kv(k, "RAM economizada", "…");
        kScans = Ui.kv(k, "Varreduras completas", "…");
        kGrade = Ui.grade(k);
        Ui.note(k, "O KSM procura pedaços de memória idênticos em apps diferentes (comum no Android, onde todo app carrega as mesmas bibliotecas) e guarda uma cópia só. Cada página tem 4 KB. Ele gasta um pouco de CPU enquanto o tablet está acordado.");

        LinearLayout p = Ui.card(body, "Os 8 processos que mais usam memória");
        procBox = new LinearLayout(a);
        procBox.setOrientation(LinearLayout.VERTICAL);
        p.addView(procBox);
        procNote = Ui.note(p, "PSS = memória que o processo realmente ocupa, dividindo a parte compartilhada entre quem a usa.");
        Ui.button(p, "Atualizar lista", new View.OnClickListener() {
            public void onClick(View v) { loadProcs(); }
        });
        selfTv = Ui.kv(p, "Este app (Diagnóstico)", "…");
        tick();
    }

    private TextView legend(LinearLayout parent, String label, int color) {
        TextView v = Ui.kv(parent, "■ " + label, "…");
        ((TextView) ((LinearLayout) v.getParent()).getChildAt(0)).setTextColor(color);
        return v;
    }

    @Override void onShow() { loadProcs(); }

    @Override boolean live() { return true; }

    @Override void tick() {
        if (donut == null) return;
        Data.Mem m = Data.Mem.read();
        if (m.total > 0) {
            donut.set(new float[]{m.used(), m.cache(), Math.max(0, m.free)}, new int[]{Ui.ACCENT, Ui.BLUE, Ui.GREEN},
                    Math.round(100.0 * m.used() / m.total) + "%", "em uso");
        }
        usedTv.setText(Ui.kb(m.used()));
        cacheTv.setText(Ui.kb(m.cache()));
        freeTv.setText(Ui.kb(m.free));
        totalTv.setText(Ui.kb(m.total));
        Data.Grade g = Data.gradeMem(m);
        Ui.setGrade(grade, g.g, g.why);
        try {
            ActivityManager am = (ActivityManager) a.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            thrTv.setText(Ui.bytes(mi.threshold));
            lowTv.setText(mi.lowMemory ? "sim: apps estão sendo fechados" : "não");
        } catch (Throwable t) {
            thrTv.setText("indisponível");
        }

        long[] z = zram(m);
        zSize.setText(z[0] > 0 ? Ui.bytes(z[0]) : "zRAM indisponível");
        zSwap.setText(m.swapTotal > 0 ? Ui.kb(m.swapTotal - m.swapFree) + " de " + Ui.kb(m.swapTotal) : "sem swap");
        zOrig.setText(Ui.bytes(z[1]));
        zComp.setText(Ui.bytes(z[2]));
        zRatio.setText(z[1] > 0 && z[2] > 0 ? Ui.f1((double) z[1] / z[2]) + "×" : "–");
        zSave.setText(z[1] > 0 && z[3] >= 0 ? Ui.bytes(Math.max(0, z[1] - z[3])) : "–");
        if (z[0] <= 0) Ui.setGrade(zGrade, Ui.NA, "zRAM não encontrado (/sys/block/zram0)");
        else if (z[1] <= 0) Ui.setGrade(zGrade, Ui.OK, "ligado, ainda sem uso: há RAM sobrando");
        else {
            double r = z[2] > 0 ? (double) z[1] / z[2] : 0;
            Ui.setGrade(zGrade, r >= 2 ? Ui.OK : Ui.WARN, r >= 2 ? "comprimindo bem" : "compressão fraca");
        }

        long run = Sys.num(KSM + "run"), sh = Sys.num(KSM + "pages_shared"), sg = Sys.num(KSM + "pages_sharing");
        kRun.setText(run < 0 ? "indisponível" : run == 1 ? "sim" : "não");
        kShared.setText(sh < 0 ? "indisponível" : sh + " páginas");
        kSharing.setText(sg < 0 ? "indisponível" : sg + " páginas");
        kSave.setText(sg < 0 ? "indisponível" : Ui.kb(sg * 4));
        long sc = Sys.num(KSM + "full_scans");
        kScans.setText(sc < 0 ? "indisponível" : String.valueOf(sc));
        if (run < 0) Ui.setGrade(kGrade, Ui.NA, "KSM não existe neste kernel");
        else if (run != 1) Ui.setGrade(kGrade, Ui.NA, "desligado");
        else if (sg >= 2000) Ui.setGrade(kGrade, Ui.OK, "economizando " + Ui.kb(sg * 4));
        else Ui.setGrade(kGrade, Ui.WARN, "economia pequena até agora (abaixo de 8 MB)");

        if (ticks++ % 5 != 0) return; // ler o próprio smaps custa caro: a cada 5 s basta
        try {
            Debug.MemoryInfo dm = new Debug.MemoryInfo();
            Debug.getMemoryInfo(dm);
            selfTv.setText(Ui.kb(dm.getTotalPss()) + " (PSS)");
        } catch (Throwable t) {
            selfTv.setText("indisponível");
        }
    }

    /** {disksize, orig_data_size, compr_data_size, mem_used_total} em bytes; -1 se faltar. */
    static long[] zram(Data.Mem m) {
        long size = Sys.num(ZRAM + "disksize"), orig = Sys.num(ZRAM + "orig_data_size"), comp = Sys.num(ZRAM + "compr_data_size");
        long used = Sys.num(ZRAM + "mem_used_total");
        if (orig < 0) {
            // kernels novos: tudo em mm_stat (orig compr mem_used …)
            String mm = Sys.read(ZRAM + "mm_stat");
            if (mm != null) {
                try {
                    String[] p = mm.split("\\s+");
                    orig = Long.parseLong(p[0]); comp = Long.parseLong(p[1]); used = Long.parseLong(p[2]);
                } catch (Throwable ignored) { }
            }
        }
        if (used < 0) used = comp;
        return new long[]{size, orig, comp, used};
    }

    private void loadProcs() {
        if (procBox == null || loadingProcs) return;
        loadingProcs = true;
        procNote.setText("Medindo processos…");
        Sys.bg(new Runnable() {
            public void run() {
                final List<Object[]> top = topProcs(a, 8);
                Sys.ui(new Runnable() {
                    public void run() {
                        loadingProcs = false;
                        showProcs(top);
                    }
                });
            }
        });
    }

    private void showProcs(List<Object[]> top) {
        procBox.removeAllViews();
        if (top == null || top.isEmpty()) {
            procNote.setText("Não deu para listar os processos.");
            return;
        }
        int max = Math.max(1, (Integer) top.get(0)[1]);
        for (Object[] o : top) {
            Ui.kv(procBox, shortName((String) o[0]), Ui.kb((Integer) o[1]));
            Charts.Meter mt = new Charts.Meter(a);
            mt.set((Integer) o[1] / (float) max, Ui.ACCENT);
            procBox.addView(mt, new LinearLayout.LayoutParams(-1, -2));
        }
        procNote.setText("PSS = memória que o processo realmente ocupa, dividindo a parte compartilhada entre quem a usa. Processos do sistema (system, launcher, teclado) sempre aparecem: são necessários.");
    }

    static String shortName(String s) {
        if (s == null) return "?";
        return s.length() > 34 ? "…" + s.substring(s.length() - 33) : s;
    }

    /** {nome, PSS em KB}, do maior para o menor. Lento (~0,5 s): chamar fora da UI thread. */
    static List<Object[]> topProcs(Context c, int max) {
        List<Object[]> out = new ArrayList<Object[]>();
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> ps = am.getRunningAppProcesses();
            if (ps == null) return out;
            int[] pids = new int[ps.size()];
            for (int i = 0; i < pids.length; i++) pids[i] = ps.get(i).pid;
            Debug.MemoryInfo[] mi = am.getProcessMemoryInfo(pids);
            for (int i = 0; i < pids.length && i < mi.length; i++)
                out.add(new Object[]{ps.get(i).processName, mi[i].getTotalPss()});
            Collections.sort(out, new Comparator<Object[]>() {
                public int compare(Object[] x, Object[] y) { return (Integer) y[1] - (Integer) x[1]; }
            });
            while (out.size() > max) out.remove(out.size() - 1);
        } catch (Throwable ignored) { }
        return out;
    }

    @Override void report(StringBuilder sb) {
        h(sb, "Memória");
        Data.Mem m = Data.Mem.read();
        kv(sb, "Total", Ui.kb(m.total));
        kv(sb, "Usada", Ui.kb(m.used()));
        kv(sb, "Cache", Ui.kb(m.cache()));
        kv(sb, "Livre", Ui.kb(m.free));
        Data.Grade g = Data.gradeMem(m);
        kv(sb, "Nota", Ui.gradeWord(g.g) + ": " + g.why);
        long[] z = zram(m);
        kv(sb, "zRAM tamanho", z[0] > 0 ? Ui.bytes(z[0]) : "indisponível");
        kv(sb, "Swap em uso", m.swapTotal > 0 ? Ui.kb(m.swapTotal - m.swapFree) + " de " + Ui.kb(m.swapTotal) : "sem swap");
        kv(sb, "zRAM original/comprimido", Ui.bytes(z[1]) + " / " + Ui.bytes(z[2]) + (z[1] > 0 && z[2] > 0 ? " (" + Ui.f1((double) z[1] / z[2]) + "×)" : ""));
        long sg = Sys.num(KSM + "pages_sharing");
        kv(sb, "KSM ligado", Sys.num(KSM + "run") == 1 ? "sim" : "não/indisponível");
        kv(sb, "KSM economia", sg < 0 ? "indisponível" : sg + " páginas = " + Ui.kb(sg * 4));
        sb.append("Processos que mais usam (PSS):\n");
        for (Object[] o : topProcs(a, 8)) sb.append("  ").append(Ui.kb((Integer) o[1])).append("  ").append(o[0]).append('\n');
    }
}
