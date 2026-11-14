package app.taleszin.diagnostico;

import android.os.Build;
import android.os.SystemClock;
import android.widget.LinearLayout;
import android.widget.TextView;

final class SummaryPage extends Page {
    SummaryPage(MainActivity a) { super(a, "Resumo", Charts.I_SUM); }

    private static final class C { TextView big, sub, grade; }

    private C model, android, rom, cpu, ram, batt, disk, up;
    private int ticks;

    private C card(Ui.Grid g, String title) {
        LinearLayout k = Ui.card(g, title);
        C c = new C();
        c.big = Ui.text(a, "…", 24, Ui.TEXT, Ui.LIGHT);
        k.addView(c.big);
        c.sub = Ui.explain(a, "");
        k.addView(c.sub);
        c.grade = Ui.grade(k);
        return c;
    }

    private static void set(C c, String big, String sub, Data.Grade g) {
        c.big.setText(big);
        c.sub.setText(sub);
        Ui.setGrade(c.grade, g.g, g.why);
    }

    @Override void build(LinearLayout body) {
        Ui.header(body, "Resumo", "Um panorama do tablet. Cada cartão traz uma nota: verde está bom, amarelo pede atenção, vermelho é problema.");
        Ui.Grid g = new Ui.Grid(a);
        body.addView(g, new LinearLayout.LayoutParams(-1, -2));
        model = card(g, "Modelo");
        android = card(g, "Android");
        rom = card(g, "ROM");
        cpu = card(g, "Processador (CPU)");
        ram = card(g, "Memória RAM");
        batt = card(g, "Bateria");
        disk = card(g, "Armazenamento");
        up = card(g, "Tempo ligado");
        fillStatic();
        tick();
    }

    private void fillStatic() {
        String[] m = modelInfo();
        set(model, m[0], m[1], gradeModel());
        set(android, "Android " + Build.VERSION.RELEASE, "API " + Build.VERSION.SDK_INT + androidName(), gradeAndroid());
        String v = Sys.prop("ro.m7s.version");
        set(rom, v != null ? "m7s " + v : "Original", v != null ? "ROM personalizada com ajustes de memória, bateria e CPU." : "Propriedade ro.m7s.version não encontrada.",
                v != null ? new Data.Grade(Ui.OK, "ROM otimizada para este tablet") : new Data.Grade(Ui.WARN, "ROM de fábrica, sem as otimizações m7s"));
    }

    static String[] modelInfo() {
        String hw = Sys.cpuinfo().get("Hardware");
        Data.Mem mem = Data.Mem.read();
        return new String[]{Build.MODEL, Build.MANUFACTURER + " · chip " + (hw != null ? hw : Build.HARDWARE) + " · " + Ui.kb(mem.total) + " de RAM"};
    }

    static Data.Grade gradeModel() {
        long t = Data.Mem.read().total;
        if (t <= 0) return new Data.Grade(Ui.NA, "RAM total indisponível");
        if (t < 700 * 1024) return new Data.Grade(Ui.WARN, "aparelho modesto: com 512 MB, poucos apps abertos por vez");
        if (t < 1500 * 1024) return new Data.Grade(Ui.WARN, "aparelho de entrada");
        return new Data.Grade(Ui.OK, "memória suficiente para uso comum");
    }

    static String androidName() {
        int s = Build.VERSION.SDK_INT;
        if (s == 19) return " (KitKat, lançado em 2013)";
        if (s >= 21 && s <= 22) return " (Lollipop)";
        return "";
    }

    static Data.Grade gradeAndroid() {
        if (Build.VERSION.SDK_INT < 23)
            return new Data.Grade(Ui.WARN, "versão antiga sem correções de segurança: evite banco e senhas importantes");
        return new Data.Grade(Ui.OK, "versão aceitável");
    }

    static String cpuLine() {
        int n = Sys.cores();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(" · ");
            long f = Sys.coreOnline(i) ? Sys.coreFreq(i, "scaling_cur_freq") : -1;
            sb.append(f > 0 ? String.valueOf(f / 1000) : "desl.");
        }
        return sb.append(" MHz").toString();
    }

    @Override boolean live() { return true; }

    @Override void tick() {
        if (cpu == null) return;
        int[] l = Sys.LOAD.sample();
        String gov = Sys.cpuFile("scaling_governor");
        long max = Sys.coreFreq(0, "scaling_max_freq");
        set(cpu, cpuLine(), "uso " + (l[0] < 0 ? "medindo…" : l[0] + "%") + " · teto " + Ui.mhz(max) + " · modo " + Sys.or(gov), Data.gradeCpu(l[0]));

        Data.Mem m = Data.Mem.read();
        set(ram, Ui.kb(m.used()) + " de " + Ui.kb(m.total), "disponível " + Ui.kb(m.avail()) + " (livre + cache)", Data.gradeMem(m));

        Data.Batt b = Data.Batt.read(a);
        set(batt, (b.pct >= 0 ? b.pct + "%" : "?") + " · " + b.volts(), b.statusPt() + " · fonte: " + b.source(), Data.gradeBatt(b));

        if (ticks++ % 10 == 0) {
            Data.Disk d = Data.Disk.of("/data");
            set(disk, Ui.bytes(d.free) + " livres", "de " + Ui.bytes(d.total) + " em /data (apps e seus dados)", Data.gradeDisk(d));
        }
        long el = SystemClock.elapsedRealtime() / 1000, aw = SystemClock.uptimeMillis() / 1000;
        set(up, Ui.dur(el), "acordado " + (el > 0 ? Math.round(100.0 * aw / el) : 0) + "% do tempo; o resto em sono profundo", Data.gradeUptime(el));
    }

    @Override void report(StringBuilder sb) {
        h(sb, "Resumo");
        String[] m = modelInfo();
        line(sb, "Modelo", m[0] + " (" + m[1] + ")", gradeModel());
        line(sb, "Android", Build.VERSION.RELEASE + " API " + Build.VERSION.SDK_INT, gradeAndroid());
        String v = Sys.prop("ro.m7s.version");
        kv(sb, "ROM", v != null ? "m7s " + v : "original");
        line(sb, "CPU", cpuLine(), Data.gradeCpu(Sys.LOAD.last[0]));
        Data.Mem mm = Data.Mem.read();
        line(sb, "RAM", Ui.kb(mm.used()) + " usados de " + Ui.kb(mm.total), Data.gradeMem(mm));
        Data.Batt b = Data.Batt.read(a);
        line(sb, "Bateria", b.pct + "% " + b.volts() + " " + b.statusPt(), Data.gradeBatt(b));
        Data.Disk d = Data.Disk.of("/data");
        line(sb, "/data livre", Ui.bytes(d.free) + " de " + Ui.bytes(d.total), Data.gradeDisk(d));
        long el = SystemClock.elapsedRealtime() / 1000;
        line(sb, "Ligado há", Ui.dur(el), Data.gradeUptime(el));
    }

    static void line(StringBuilder sb, String k, String v, Data.Grade g) {
        kv(sb, k, v + "  [" + Ui.gradeWord(g.g) + ": " + g.why + "]");
    }
}
