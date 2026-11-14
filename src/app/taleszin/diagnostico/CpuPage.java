package app.taleszin.diagnostico;

import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class CpuPage extends Page {
    CpuPage(MainActivity a) { super(a, "CPU", Charts.I_CPU); }

    private int cores;
    private TextView[] freq;
    private Charts.Meter[] meters;
    private TextView useTotal, useCores, govTv, govExplain, minTv, maxTv, hwTv, onlineTv, srcTv, loadTv, loadGrade, tisNote;
    private Charts.Meter useMeter, heatMeter;
    private TextView heatTv, heatGrade;
    private LinearLayout heatCard;
    private boolean hasSensor;
    private Charts.HBars tis;
    private int ticks;

    @Override void build(LinearLayout body) {
        Ui.header(body, "Processador", "O CPU é o “cérebro” do tablet. Ele muda de velocidade o tempo todo para economizar bateria.");
        cores = Sys.cores();

        LinearLayout now = Ui.card(body, "Velocidade agora (ao vivo)");
        freq = new TextView[cores];
        meters = new Charts.Meter[cores];
        for (int i = 0; i < cores; i++) {
            freq[i] = Ui.kv(now, "Núcleo " + i, "…");
            meters[i] = new Charts.Meter(a);
            now.addView(meters[i], new LinearLayout.LayoutParams(-1, -2));
        }
        Ui.divider(now);
        useTotal = Ui.kv(now, "Uso total", "medindo…");
        useMeter = new Charts.Meter(a);
        now.addView(useMeter, new LinearLayout.LayoutParams(-1, -2));
        useCores = Ui.kv(now, "Uso por núcleo", "medindo…");
        Ui.note(now, "Uso = fração do último segundo em que o CPU esteve trabalhando (calculado do /proc/stat). Um núcleo “desligado” foi desativado pelo sistema para poupar energia e volta sozinho quando precisa.");

        heatCard = Ui.card(body, "Temperatura");
        hasSensor = !Thermal.sensors().isEmpty();
        heatTv = Ui.kv(heatCard, hasSensor ? "Sensor" : "Aquecimento estimado", "medindo…");
        heatMeter = new Charts.Meter(a);
        heatCard.addView(heatMeter, new LinearLayout.LayoutParams(-1, -2));
        heatGrade = Ui.grade(heatCard);
        Ui.note(heatCard, hasSensor
                ? "Leitura direta do sensor do kernel. Até ~60 °C é normal em uso; acima de 75 °C o chip começa a se proteger reduzindo a velocidade."
                : "O RK3026 não tem sensor de temperatura dentro do chip (só chips Rockchip mais novos, como o RK3288, trouxeram um) e a bateria deste tablet também não informa. "
                + "Por isso a Taleszin OS mostra uma ESTIMATIVA: a média do último minuto de uso do CPU × velocidade. Quanto mais tempo no máximo, mais o chip esquenta. "
                + "Não é °C, é um termômetro de esforço.");

        LinearLayout cfg = Ui.card(body, "Configuração");
        govTv = Ui.kv(cfg, "Governor", "…");
        govExplain = Ui.note(cfg, "");
        minTv = Ui.kv(cfg, "Mínimo permitido", "…");
        maxTv = Ui.kv(cfg, "Máximo permitido agora", "…");
        hwTv = Ui.kv(cfg, "Máximo do chip", "…");
        onlineTv = Ui.kv(cfg, "Núcleos ligados", "…");
        srcTv = Ui.kv(cfg, "Energia", "…");
        Ui.note(cfg, "O governor é a “regra” que decide a velocidade: ele olha o quanto o CPU está ocupado e sobe ou desce a frequência (MHz). Mais MHz = mais rápido, porém mais consumo e calor.");

        LinearLayout t = Ui.card(body, "Quanto tempo em cada velocidade");
        tis = new Charts.HBars(a);
        t.addView(tis, new LinearLayout.LayoutParams(-1, -2));
        tisNote = Ui.note(t, "Desde que o tablet ligou (núcleo 0). O ideal é passar a maior parte do tempo nas velocidades baixas: sinal de que o aparelho descansa quando não está sendo usado.");

        LinearLayout ld = Ui.card(body, "Carga média (load average)");
        loadTv = Ui.kv(ld, "1 / 5 / 15 min", "…");
        loadGrade = Ui.grade(ld);
        Ui.note(ld, "É a média de tarefas querendo usar o CPU ao mesmo tempo. Com " + cores + " núcleos, valores até " + cores + " estão ok; acima disso há fila e o tablet engasga. No Android, esperas pela memória (NAND) também entram nessa conta.");

        LinearLayout ci = Ui.card(body, "Detalhes do chip (/proc/cpuinfo)");
        Map<String, String> m = Sys.cpuinfo();
        Ui.kv(ci, "Hardware", Sys.or(m.get("Hardware")));
        Ui.kv(ci, "Processador", Sys.or(m.containsKey("Processor") ? m.get("Processor") : m.get("model name")));
        Ui.kv(ci, "Revisão do CPU", Sys.or(m.get("CPU revision")));
        Ui.kv(ci, "Peça (CPU part)", Sys.or(m.get("CPU part")) + partName(m.get("CPU part")));
        Ui.kv(ci, "BogoMIPS", Sys.or(m.containsKey("BogoMIPS") ? m.get("BogoMIPS") : m.get("bogomips")));
        Ui.kv(ci, "Revisão da placa", Sys.or(m.get("Revision")));
        TextView feat = Ui.kv(ci, "Recursos", Sys.or(m.get("Features")));
        feat.setTextSize(12);
        Ui.note(ci, "“Recursos” são instruções especiais do chip. neon acelera vídeo e imagem; vfp/vfpv3 fazem contas com vírgula rápido; thumb deixa o código menor. O RK3026 tem 2 núcleos Cortex-A9 até 1008 MHz e GPU Mali-400.");

        LinearLayout why = Ui.card(body, "Por que a velocidade cai na bateria?");
        Ui.note(why, "A bateria deste tablet é de 2014 e já está gasta: quando o CPU pede muita corrente de uma vez, a tensão despenca e o aparelho desliga do nada. Por isso a Taleszin OS limita o CPU a 816 MHz quando está na bateria e libera os 1008 MHz só na tomada. Fica um pouco mais lento, mas não apaga no meio do uso.");
        tick();
    }

    private void heat(int busy, long hwMax) {
        double e = Thermal.EFFORT.push(busy, Sys.coreFreq(0, "scaling_cur_freq"), hwMax);
        if (hasSensor) {
            List<Thermal.Reading> r = Thermal.sensors();
            if (!r.isEmpty()) {
                float mx = -99; StringBuilder sb = new StringBuilder();
                for (Thermal.Reading x : r) { if (sb.length() > 0) sb.append(" · "); sb.append(x.name).append(' ').append(Ui.f1(x.c)).append(" °C"); mx = Math.max(mx, x.c); }
                heatTv.setText(sb.toString());
                heatMeter.set(Math.min(1, mx / 90f), mx < 60 ? Ui.GREEN : mx < 75 ? Ui.YELLOW : Ui.RED);
                Ui.setGrade(heatGrade, mx < 60 ? Ui.OK : mx < 75 ? Ui.WARN : Ui.BAD, mx < 60 ? "temperatura normal" : mx < 75 ? "quente, mas dentro do limite" : "muito quente");
                return;
            }
        }
        heatTv.setText(Thermal.effortWord(e) + " · " + Math.round(e * 100) + "% do esforço máximo");
        int g = Thermal.effortGrade(e);
        heatMeter.set((float) e, Ui.gradeColor(g));
        Ui.setGrade(heatGrade, g, g == Ui.OK ? "chip descansado" : g == Ui.WARN ? "uso puxado no último minuto" : "CPU no talo há um tempo: deve estar quente");
    }

    static String partName(String p) {
        if (p == null) return "";
        p = p.toLowerCase();
        if (p.contains("c09")) return " (Cortex-A9)";
        if (p.contains("c07")) return " (Cortex-A7)";
        if (p.contains("c05")) return " (Cortex-A5)";
        if (p.contains("c0f")) return " (Cortex-A15)";
        if (p.contains("d03")) return " (Cortex-A53)";
        return "";
    }

    static String govText(String g) {
        if (g == null) return "Não deu para ler o governor.";
        if (g.equals("interactive")) return "interactive: sobe a velocidade assim que você toca na tela e desce logo depois. Bom equilíbrio para tablets.";
        if (g.equals("ondemand")) return "ondemand: acelera quando a carga passa de um limite e desacelera aos poucos.";
        if (g.equals("conservative")) return "conservative: sobe e desce devagar, economiza mas parece mais lento.";
        if (g.equals("performance")) return "performance: sempre na velocidade máxima. Rápido, mas gasta muita bateria.";
        if (g.equals("powersave")) return "powersave: sempre na velocidade mínima. Economiza, mas fica lento.";
        if (g.equals("userspace")) return "userspace: velocidade fixa definida por um programa.";
        if (g.contains("hotplug")) return g + ": além da velocidade, liga e desliga núcleos conforme a carga.";
        return g + ": regra própria do fabricante.";
    }

    @Override boolean live() { return true; }

    @Override void tick() {
        if (freq == null) return;
        long hwMax = Sys.coreFreq(0, "cpuinfo_max_freq");
        int on = 0;
        for (int i = 0; i < cores; i++) {
            boolean online = Sys.coreOnline(i);
            long f = online ? Sys.coreFreq(i, "scaling_cur_freq") : -1;
            if (online) on++;
            freq[i].setText(online ? Ui.mhz(f) : "desligado (economia)");
            meters[i].set(hwMax > 0 && f > 0 ? (float) f / hwMax : 0, f >= hwMax * 0.95 ? Ui.YELLOW : Ui.ACCENT);
        }
        int[] l = Sys.LOAD.sample();
        useTotal.setText(l[0] < 0 ? "medindo…" : l[0] + "%");
        useMeter.set(Math.max(0, l[0]) / 100f, Ui.gradeColor(Data.gradeCpu(l[0]).g));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cores && i < 4; i++) {
            if (i > 0) sb.append(" · ");
            sb.append(l[i + 1] < 0 ? "–" : l[i + 1] + "%");
        }
        useCores.setText(sb.toString());
        heat(l[0], hwMax);

        if (ticks++ % 5 == 0) slow(hwMax, on);
    }

    private void slow(long hwMax, int on) {
        String gov = Sys.cpuFile("scaling_governor");
        govTv.setText(Sys.or(gov));
        govExplain.setText(govText(gov));
        minTv.setText(Ui.mhz(Sys.coreFreq(0, "scaling_min_freq")));
        long max = Sys.coreFreq(0, "scaling_max_freq");
        maxTv.setText(Ui.mhz(max));
        hwTv.setText(Ui.mhz(hwMax));
        onlineTv.setText(on + " de " + cores);
        Data.Batt b = Data.Batt.read(a);
        srcTv.setText(b.source() + (max > 0 && hwMax > 0 && max < hwMax ? " (CPU limitado)" : ""));

        String la = Sys.read("/proc/loadavg");
        if (la != null) {
            String[] p = la.split("\\s+");
            loadTv.setText(p[0] + " / " + p[1] + " / " + p[2]);
            try {
                double v = Double.parseDouble(p[0]);
                Ui.setGrade(loadGrade, v <= cores ? Ui.OK : v <= cores * 2 ? Ui.WARN : Ui.BAD,
                        v <= cores ? "sem fila" : v <= cores * 2 ? "alguma fila de espera" : "muita fila: o tablet está sobrecarregado");
            } catch (Throwable ignored) { }
        } else {
            loadTv.setText("indisponível");
            Ui.setGrade(loadGrade, Ui.NA, "/proc/loadavg indisponível");
        }

        List<long[]> st = timeInState();
        if (st.isEmpty()) {
            tis.set(new String[0], new float[0], new String[0], Ui.ACCENT);
            tisNote.setText("time_in_state indisponível neste kernel.");
            return;
        }
        long total = 0;
        for (long[] r : st) total += r[1];
        String[] lab = new String[st.size()], rt = new String[st.size()];
        float[] fr = new float[st.size()];
        for (int i = 0; i < st.size(); i++) {
            long[] r = st.get(i);
            lab[i] = (r[0] / 1000) + " MHz";
            fr[i] = total > 0 ? (float) r[1] / total : 0;
            rt[i] = Ui.pct(fr[i] * 100) + " · " + shortDur(r[1] / 100);
        }
        tis.set(lab, fr, rt, Ui.ACCENT);
    }

    static String shortDur(long sec) {
        if (sec < 60) return sec + " s";
        if (sec < 3600) return sec / 60 + " min";
        return Ui.f1(sec / 3600.0) + " h";
    }

    /** {kHz, tempo em unidades de 10 ms}, da maior para a menor velocidade. */
    static List<long[]> timeInState() {
        List<long[]> out = new ArrayList<long[]>();
        List<String> ls = Sys.lines(Sys.CPU + "cpu0/cpufreq/stats/time_in_state");
        if (ls == null) return out;
        for (String l : ls) {
            String[] p = l.trim().split("\\s+");
            if (p.length < 2) continue;
            try { out.add(new long[]{Long.parseLong(p[0]), Long.parseLong(p[1])}); } catch (Throwable ignored) { }
        }
        java.util.Collections.sort(out, new java.util.Comparator<long[]>() {
            public int compare(long[] x, long[] y) { return Long.compare(y[0], x[0]); }
        });
        return out;
    }

    @Override void report(StringBuilder sb) {
        h(sb, "CPU");
        int n = Sys.cores();
        for (int i = 0; i < n; i++)
            kv(sb, "Núcleo " + i, Sys.coreOnline(i) ? Ui.mhz(Sys.coreFreq(i, "scaling_cur_freq")) : "desligado");
        kv(sb, "Governor", Sys.or(Sys.cpuFile("scaling_governor")));
        kv(sb, "Mín / máx / chip", Ui.mhz(Sys.coreFreq(0, "scaling_min_freq")) + " / " + Ui.mhz(Sys.coreFreq(0, "scaling_max_freq")) + " / " + Ui.mhz(Sys.coreFreq(0, "cpuinfo_max_freq")));
        kv(sb, "Uso (última medida)", Sys.LOAD.last[0] < 0 ? "não medido" : Sys.LOAD.last[0] + "%");
        kv(sb, "Load average", Sys.or(Sys.read("/proc/loadavg")));
        List<long[]> st = timeInState();
        long total = 0;
        for (long[] r : st) total += r[1];
        for (long[] r : st) kv(sb, "  tempo em " + r[0] / 1000 + " MHz", Ui.dur(r[1] / 100) + (total > 0 ? " (" + Ui.pct(100.0 * r[1] / total) + ")" : ""));
        Map<String, String> m = Sys.cpuinfo();
        for (String k : new String[]{"Hardware", "Processor", "CPU part", "CPU revision", "Features", "BogoMIPS", "Revision"})
            if (m.containsKey(k)) kv(sb, k, m.get(k));
    }
}
