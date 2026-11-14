package app.taleszin.diagnostico;

import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;

final class HealthPage extends Page {
    HealthPage(MainActivity a) { super(a, "Saúde", Charts.I_HEA); }

    private LinearLayout box;
    private TextView exportBtn, exportStatus;
    private boolean loading, exporting;

    @Override void build(LinearLayout body) {
        Ui.header(body, "Saúde (telemetria)", "Um resumo das últimas 24 h, tirado do registro que a ROM m7s grava a cada 30 s: tela, bateria, CPU, apps e reinícios.");
        LinearLayout e = Ui.card(body, "Exportar relatório");
        Ui.note(e, "Grava um arquivo de texto com tudo o que o app mostra (todas as abas) na pasta Download. Útil para mandar para quem cuida do tablet.");
        exportBtn = Ui.button(e, "Exportar relatório", new View.OnClickListener() {
            public void onClick(View v) { export(); }
        });
        exportStatus = Ui.note(e, "");
        box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        body.addView(box);
        Ui.note(box, "Carregando a telemetria…");
    }

    @Override void onShow() {
        if (box == null || loading) return;
        loading = true;
        Sys.bg(new Runnable() {
            public void run() {
                final Telemetry t = Telemetry.get();
                final Telemetry.Stats s = t.error == null ? t.stats(t.refTs - 86400) : null;
                Sys.ui(new Runnable() {
                    public void run() {
                        loading = false;
                        show(t, s);
                    }
                });
            }
        });
    }

    private void show(Telemetry t, Telemetry.Stats s) {
        box.removeAllViews();
        if (t.error != null) {
            LinearLayout k = Ui.card(box, "Telemetria indisponível");
            Ui.note(k, t.error);
            return;
        }
        LinearLayout p = Ui.card(box, "Período analisado");
        Ui.kv(p, "De", s.first < t.n ? Telemetry.hm(t.ts[s.first]) : "–");
        Ui.kv(p, "Até", Telemetry.hm(t.ts[t.n - 1]));
        Ui.kv(p, "Registros", String.valueOf(t.n - s.first));
        Ui.kv(p, "Tempo coberto", Ui.dur(s.covered));
        if (t.shifted) Ui.note(p, "O relógio do tablet não bate com a telemetria; mostrando as 24 h até o último registro.");
        Ui.note(p, "Buracos no registro = tablet desligado. Com o tablet dormindo o registrador também para, mas o intervalo entra na conta como tela desligada.");

        LinearLayout sc = Ui.card(box, "Tela");
        long tot = s.screenOn + s.screenOff;
        Ui.kv(sc, "Ligada", Ui.dur(s.screenOn) + (tot > 0 ? " (" + Ui.pct(100.0 * s.screenOn / tot) + ")" : ""));
        Charts.Meter m = new Charts.Meter(a);
        m.set(tot > 0 ? (float) s.screenOn / tot : 0, Ui.YELLOW);
        sc.addView(m, new LinearLayout.LayoutParams(-1, -2));
        Ui.kv(sc, "Desligada", Ui.dur(s.screenOff));

        LinearLayout c = Ui.card(box, "Consumo de bateria");
        Ui.kv(c, "Tela ligada", BatteryPage.rate(s.rateOn()));
        Ui.kv(c, "Tela desligada", BatteryPage.rate(s.rateOff()));
        Ui.kv(c, "Média fora da tomada", BatteryPage.rate(s.rateAll()));
        TextView g = Ui.grade(c);
        double off = s.rateOff();
        if (Double.isNaN(off)) Ui.setGrade(g, Ui.NA, "pouco tempo na bateria com a tela apagada");
        else if (off <= 2) Ui.setGrade(g, Ui.OK, "em repouso gasta pouco");
        else if (off <= 5) Ui.setGrade(g, Ui.WARN, "em repouso gasta um pouco além do ideal");
        else Ui.setGrade(g, Ui.BAD, "gasta muito mesmo apagado: algo impede o sono");
        Ui.note(c, "%/h = quantos por cento a bateria perde por hora. Com a tela apagada o ideal é ficar abaixo de 1–2 %/h. A % desta bateria oscila com a tensão, então são estimativas.");

        LinearLayout cp = Ui.card(box, "Picos de CPU");
        Ui.kv(cp, "Maior uso registrado", s.maxBusy < 0 ? "–" : s.maxBusy + "%");
        Ui.kv(cp, "Uso médio com a tela ligada", s.avgBusyOn < 0 ? "–" : Ui.pct(s.avgBusyOn));
        Ui.kv(cp, "Amostras acima de 85%", s.samples > 0 ? s.busyHigh + " de " + s.samples + " (" + Ui.pct(100.0 * s.busyHigh / s.samples) + ")" : "–");
        Ui.kv(cp, "Maior carga média (load)", s.maxLoad100 < 0 ? "–" : Ui.f2(s.maxLoad100 / 100.0));
        if (!s.peaks.isEmpty()) {
            Ui.note(cp, "Momentos de pico:");
            for (int[] pk : s.peaks) {
                int i = pk[0];
                String app = t.fg[i] >= 0 ? t.apps.get(t.fg[i]) : (t.bl[i] > 0 ? "app não identificado" : "tela apagada");
                Ui.kv(cp, Telemetry.hm(t.ts[i]), t.busy[i] + "% · " + Ui.mhz(t.khz[i]) + " · " + MemoryPage.shortName(app));
            }
        }
        Ui.note(cp, "Picos curtos são normais (abrir um app, carregar uma página). Muito tempo acima de 85% deixa o tablet lento, quente e gasta bateria rápido.");

        LinearLayout ap = Ui.card(box, "Apps mais usados (tela ligada)");
        if (s.topApps.isEmpty()) Ui.note(ap, "Nenhum app registrado no período (a coluna fg_app só é preenchida com a tela ligada).");
        else {
            long max = Math.max(1, (Long) s.topApps.get(0)[1]);
            for (Object[] o : s.topApps) {
                Ui.kv(ap, MemoryPage.shortName((String) o[0]), Ui.dur((Long) o[1]));
                Charts.Meter mt = new Charts.Meter(a);
                mt.set((Long) o[1] / (float) max, Ui.ACCENT);
                ap.addView(mt, new LinearLayout.LayoutParams(-1, -2));
            }
        }

        LinearLayout rb = Ui.card(box, "Reinícios e desligamentos");
        TextView rg = Ui.grade(rb);
        if (s.reboots.isEmpty()) Ui.setGrade(rg, Ui.OK, "nenhum no período");
        else Ui.setGrade(rg, s.reboots.size() >= 3 ? Ui.BAD : Ui.WARN, s.reboots.size() + " no período");
        for (int[] r : s.reboots) {
            Ui.kv(rb, "Voltou a ligar " + Telemetry.hm(t.ts[r[0]]), "");
            Ui.note(rb, "Antes: " + Telemetry.hm(t.ts[r[1]]) + " — " + Telemetry.rebootReason(t, r[1]));
        }
        Ui.note(rb, "Detectado quando o “tempo ligado” volta a zero no registro. Desligamentos com tensão baixa indicam a bateria gasta cedendo; o powerguard existe para que isso aconteça de forma limpa.");
    }

    // ------------------------------------------------------------------ exportação
    private void export() {
        if (exporting) return;
        exporting = true;
        exportBtn.setEnabled(false);
        exportStatus.setText("Gerando relatório…");
        final Page[] pages = a.pages;
        Sys.bg(new Runnable() {
            public void run() {
                String msg;
                try {
                    StringBuilder sb = new StringBuilder(16384);
                    Date now = new Date();
                    sb.append("RELATÓRIO DO DIAGNÓSTICO\n");
                    sb.append("Gerado em ").append(new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Ui.BR).format(now)).append('\n');
                    sb.append("Aparelho: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(", Android ").append(Build.VERSION.RELEASE).append('\n');
                    for (Page p : pages) {
                        try { p.report(sb); } catch (Throwable t) { sb.append("\n(").append(p.name).append(": erro ao coletar: ").append(t).append(")\n"); }
                    }
                    if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
                        throw new RuntimeException("o armazenamento /sdcard não está disponível (o cabo USB está em modo pendrive?)");
                    File dir = new File(Environment.getExternalStorageDirectory(), "Download");
                    if (!dir.isDirectory() && !dir.mkdirs()) throw new RuntimeException("não deu para criar " + dir);
                    File f = new File(dir, "diagnostico-" + new SimpleDateFormat("yyyyMMdd-HHmm", Ui.BR).format(now) + ".txt");
                    Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
                    try { w.write(sb.toString()); } finally { w.close(); }
                    try { MediaScannerConnection.scanFile(a.getApplicationContext(), new String[]{f.getPath()}, new String[]{"text/plain"}, null); } catch (Throwable ignored) { }
                    msg = "Relatório salvo em " + f.getPath();
                } catch (Throwable t) {
                    msg = "Não deu para salvar: " + t.getMessage();
                }
                final String m = msg;
                Sys.ui(new Runnable() {
                    public void run() {
                        exporting = false;
                        exportBtn.setEnabled(true);
                        exportStatus.setText(m);
                        Toast.makeText(a, m, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    @Override void report(StringBuilder sb) {
        h(sb, "Saúde (telemetria, últimas 24 h)");
        Telemetry t = Telemetry.get();
        if (t.error != null) { kv(sb, "Telemetria", t.error); return; }
        Telemetry.Stats s = t.stats(t.refTs - 86400);
        kv(sb, "Arquivos", t.files.toString());
        kv(sb, "Período", (s.first < t.n ? Telemetry.hm(t.ts[s.first]) : "–") + " a " + Telemetry.hm(t.ts[t.n - 1]) + (t.shifted ? " (relógio fora; janela deslocada)" : ""));
        kv(sb, "Tela ligada / desligada", Ui.dur(s.screenOn) + " / " + Ui.dur(s.screenOff));
        kv(sb, "Consumo tela ligada / desligada / média", BatteryPage.rate(s.rateOn()) + " / " + BatteryPage.rate(s.rateOff()) + " / " + BatteryPage.rate(s.rateAll()));
        kv(sb, "CPU máx / média com tela / amostras >85%", s.maxBusy + "% / " + (s.avgBusyOn < 0 ? "–" : Ui.pct(s.avgBusyOn)) + " / " + s.busyHigh + " de " + s.samples);
        for (int[] pk : s.peaks) {
            int i = pk[0];
            kv(sb, "  pico " + Telemetry.hm(t.ts[i]), t.busy[i] + "% " + (t.fg[i] >= 0 ? t.apps.get(t.fg[i]) : ""));
        }
        sb.append("Apps mais usados:\n");
        for (Object[] o : s.topApps) sb.append("  ").append(Ui.dur((Long) o[1])).append("  ").append(o[0]).append('\n');
        kv(sb, "Reinícios", String.valueOf(s.reboots.size()));
        for (int[] r : s.reboots) kv(sb, "  " + Telemetry.hm(t.ts[r[0]]), Telemetry.rebootReason(t, r[1]));
    }
}
