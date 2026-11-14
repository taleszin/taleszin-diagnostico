package app.taleszin.diagnostico;

import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

final class BatteryPage extends Page {
    BatteryPage(MainActivity a) { super(a, "Bateria", Charts.I_BAT); }

    static final String PG_LOG = "powerguard.log", PG_STATE = "powerguard.state";

    private TextView big, volt, status, health, temp, src, tech, grade;
    private TextView rateAll, rateOn, rateOff, remain, rateNote;
    private Charts.Line pctChart, mvChart;
    private TextView chartNote, pgState, pgLog;
    private boolean loading;

    @Override void build(LinearLayout body) {
        Ui.header(body, "Bateria", "Carga, tensão e quanto a bateria dura, com base na telemetria que a ROM grava a cada 30 s.");

        LinearLayout now = Ui.card(body, "Agora (ao vivo)");
        big = Ui.text(a, "…", 34, Ui.TEXT, Ui.LIGHT);
        now.addView(big);
        volt = Ui.kv(now, "Tensão", "…");
        status = Ui.kv(now, "Estado", "…");
        src = Ui.kv(now, "Fonte de energia", "…");
        health = Ui.kv(now, "Saúde (driver)", "…");
        temp = Ui.kv(now, "Temperatura", "…");
        tech = Ui.kv(now, "Tecnologia", "…");
        grade = Ui.grade(now);
        Ui.note(now, "A tensão (em volts) é o dado mais honesto desta bateria: cheia fica perto de 4,2 V, e abaixo de ~3,6 V ela está quase no fim.");

        LinearLayout c = Ui.card(body, "Consumo estimado (últimas 6 h)");
        rateAll = Ui.kv(c, "Na bateria, média", "…");
        rateOn = Ui.kv(c, "Com a tela ligada", "…");
        rateOff = Ui.kv(c, "Com a tela desligada", "…");
        remain = Ui.kv(c, "Duraria mais", "…");
        rateNote = Ui.note(c, "Calculado comparando a % registrada na telemetria nos trechos fora da tomada.");

        LinearLayout g = Ui.card(body, "Últimas 24 horas");
        Ui.note(g, "Carga (%)");
        pctChart = new Charts.Line(a);
        pctChart.setEmpty("carregando…");
        g.addView(pctChart, new LinearLayout.LayoutParams(-1, -2));
        Ui.note(g, "Tensão (V) — a linha vermelha é o corte do powerguard, 3,48 V");
        mvChart = new Charts.Line(a);
        mvChart.setEmpty("carregando…");
        g.addView(mvChart, new LinearLayout.LayoutParams(-1, -2));
        chartNote = Ui.note(g, "");

        LinearLayout w = Ui.card(body, "Por que a % “mente”?");
        Ui.note(w, "A bateria é de 2014 e já perdeu boa parte da capacidade: ela não segura mais a tensão quando o tablet pede força. "
                + "O medidor de % do chip RK30 não mede a carga de verdade, ele adivinha pela tensão. Sob carga (vídeo, navegador, CPU a 100%) a tensão cai e a % despenca; "
                + "em repouso a tensão sobe de novo e a % “volta”. Por isso a % pode pular de 60 para 30 e depois para 45. Confie mais na tensão e no gráfico.");

        LinearLayout p = Ui.card(body, "Powerguard (guarda da bateria)");
        Ui.note(p, "O powerguard é um vigia da ROM m7s: na bateria ele limita o CPU a 816 MHz e, se a tensão ficar abaixo de 3,48 V em 2 leituras seguidas, "
                + "desliga o tablet do jeito certo (fechando os apps e salvando os dados) antes que a bateria apague sozinha e corrompa algo.");
        pgState = Ui.kv(p, "Estado atual", "…");
        Ui.note(p, "Últimas linhas do registro:");
        pgLog = Ui.text(a, "…", 11, Ui.TEXT, Typeface.MONOSPACE);
        pgLog.setPadding(Ui.dp(8), Ui.dp(6), Ui.dp(8), Ui.dp(6));
        pgLog.setBackgroundDrawable(Ui.round(Ui.CARD2, 8));
        p.addView(pgLog, new LinearLayout.LayoutParams(-1, -2));
        tick();
    }

    @Override void onShow() { loadTelemetry(); }

    @Override boolean live() { return true; }

    @Override void tick() {
        if (big == null) return;
        Data.Batt b = Data.Batt.read(a);
        big.setText(b.pct >= 0 ? b.pct + "%" : "?");
        volt.setText(b.volts());
        status.setText(b.statusPt());
        src.setText(b.source());
        health.setText(b.healthPt());
        temp.setText(Float.isNaN(b.tempC) ? "sem sensor" : Ui.f1(b.tempC) + " °C");
        tech.setText(Sys.or(b.tech));
        Data.Grade g = Data.gradeBatt(b);
        Ui.setGrade(grade, g.g, g.why);
    }

    private void loadTelemetry() {
        if (loading || pctChart == null) return;
        loading = true;
        Sys.bg(new Runnable() {
            public void run() {
                final Telemetry t = Telemetry.get();
                final Telemetry.Stats s6 = t.error == null ? t.stats(t.refTs - 6 * 3600) : null;
                final List<String> log = Sys.tail(Telemetry.dir + "/" + PG_LOG, 12);
                final String st = Sys.read(Telemetry.dir + "/" + PG_STATE);
                Sys.ui(new Runnable() {
                    public void run() {
                        loading = false;
                        show(t, s6, log, st);
                    }
                });
            }
        });
    }

    static String rate(double r) {
        if (Double.isNaN(r)) return "dados insuficientes";
        if (r <= 0) return "não caiu (≈0 %/h)";
        return Ui.f1(r) + " %/h";
    }

    private void show(Telemetry t, Telemetry.Stats s, List<String> log, String st) {
        if (t.error != null) {
            rateAll.setText("indisponível"); rateOn.setText("–"); rateOff.setText("–"); remain.setText("–");
            rateNote.setText(t.error);
            pctChart.setEmpty("sem telemetria");
            mvChart.setEmpty("sem telemetria");
            chartNote.setText(t.error);
        } else {
            rateAll.setText(rate(s.rateAll()));
            rateOn.setText(rate(s.rateOn()));
            rateOff.setText(rate(s.rateOff()));
            Data.Batt b = Data.Batt.read(a);
            double r = s.rateAll();
            remain.setText(!Double.isNaN(r) && r > 0.2 && b.pct > 0 ? "≈ " + Ui.dur((long) (b.pct / r * 3600)) + " no ritmo médio" : "–");
            rateNote.setText("Calculado comparando a % registrada nos trechos fora da tomada (" + Ui.f1(s.hOn + s.hOff)
                    + " h de dados). Como a % desta bateria oscila com a tensão, trate como estimativa.");
            int[] idx = t.sampleIdx(t.refTs - 86400, 360);
            int[] xs = new int[idx.length];
            float[] p = new float[idx.length], v = new float[idx.length];
            float vmin = 3.3f, vmax = 4.3f;
            for (int i = 0; i < idx.length; i++) {
                xs[i] = t.ts[idx[i]];
                p[i] = t.pct[idx[i]];
                v[i] = t.mv[idx[i]] / 1000f;
                if (v[i] > 1) { vmin = Math.min(vmin, v[i]); vmax = Math.max(vmax, v[i]); }
            }
            pctChart.set(xs, p, idx.length, 0, 100, Ui.GREEN, "%", 0, Float.NaN);
            mvChart.set(xs, v, idx.length, (float) Math.floor(vmin * 10) / 10f, (float) Math.ceil(vmax * 10) / 10f, Ui.ACCENT, "", 2, 3.48f);
            chartNote.setText(idx.length < 2 ? "Ainda não há pontos suficientes nas últimas 24 h."
                    : (t.shifted ? "O relógio não bate com a telemetria: mostrando as 24 h até o último registro (" + Telemetry.hm(t.refTs) + "). " : "")
                    + "Trechos sem linha = tablet desligado ou em sono profundo.");
        }
        pgState.setText(stateText(st));
        if (log == null) pgLog.setText("powerguard.log indisponível (só existe na ROM m7s, ou sem permissão de leitura).");
        else if (log.isEmpty()) pgLog.setText("(registro vazio)");
        else {
            StringBuilder sb = new StringBuilder();
            for (String l : log) sb.append(l).append('\n');
            pgLog.setText(sb.toString().trim());
        }
    }

    /** powerguard.state: "<ts> <modo> <teto kHz> <mV> <pct> <leituras baixas>" */
    static String stateText(String st) {
        if (st == null) return "indisponível";
        try {
            String[] p = st.trim().split("\\s+");
            String age = "";
            long ts = Long.parseLong(p[0]);
            long d = System.currentTimeMillis() / 1000 - ts;
            if (d >= 0 && d < 86400) age = " · há " + Ui.dur(d);
            return p[1] + ", teto " + Ui.mhz(Long.parseLong(p[2])) + ", " + Ui.f2(Integer.parseInt(p[3]) / 1000.0) + " V" + age;
        } catch (Throwable t) {
            return st;
        }
    }

    @Override void report(StringBuilder sb) {
        h(sb, "Bateria");
        Data.Batt b = Data.Batt.read(a);
        kv(sb, "Carga", b.pct + "%");
        kv(sb, "Tensão", b.volts());
        kv(sb, "Estado", b.statusPt());
        kv(sb, "Fonte", b.source());
        kv(sb, "Saúde", b.healthPt());
        kv(sb, "Temperatura", Float.isNaN(b.tempC) ? "sem sensor" : Ui.f1(b.tempC) + " °C");
        Data.Grade g = Data.gradeBatt(b);
        kv(sb, "Nota", Ui.gradeWord(g.g) + ": " + g.why);
        Telemetry t = Telemetry.get();
        if (t.error != null) kv(sb, "Telemetria", t.error);
        else {
            Telemetry.Stats s = t.stats(t.refTs - 6 * 3600);
            kv(sb, "Consumo 6 h (média/tela ligada/desligada)", rate(s.rateAll()) + " / " + rate(s.rateOn()) + " / " + rate(s.rateOff()));
        }
        kv(sb, "Powerguard", stateText(Sys.read(Telemetry.dir + "/" + PG_STATE)));
        List<String> log = Sys.tail(Telemetry.dir + "/" + PG_LOG, 12);
        if (log != null) for (String l : log) sb.append("  ").append(l).append('\n');
    }
}
