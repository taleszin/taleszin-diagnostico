package app.taleszin.diagnostico;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê a telemetria da ROM m7s (/data/m7s-telemetry/AAAA-MM-DD.csv) em fluxo: só o arquivo de hoje e o
 * de ontem, guardando apenas as colunas usadas em vetores de int. Java puro (testável fora do Android).
 */
final class Telemetry {
    static final String DIR = "/data/m7s-telemetry";
    static String dir = DIR;

    /** Mensagem amigável quando não deu para ler; null se deu certo. */
    String error;
    final List<String> files = new ArrayList<String>();
    int n;
    int[] ts = new int[1024], up = new int[1024], pct = new int[1024], mv = new int[1024], plug = new int[1024],
            bl = new int[1024], khz = new int[1024], busy = new int[1024], load100 = new int[1024], fg = new int[1024];
    final List<String> apps = new ArrayList<String>();
    private final Map<String, Integer> appIdx = new HashMap<String, Integer>();
    long loadedAtMs;
    /** Fim da janela (segundos epoch) e se a janela foi deslocada para o último dado existente. */
    long refTs;
    boolean shifted;

    private static Telemetry cache;

    static synchronized Telemetry get() {
        long now = System.currentTimeMillis();
        if (cache == null || now - cache.loadedAtMs > 60000) cache = load(now / 1000);
        return cache;
    }

    static Telemetry load(long nowSec) {
        Telemetry t = new Telemetry();
        t.loadedAtMs = System.currentTimeMillis();
        File d = new File(dir);
        try {
            if (!d.exists()) {
                t.error = "A telemetria da ROM não foi encontrada (" + dir + "). Ela só existe na ROM m7s: "
                        + "em outra ROM, ou se o registrador estiver desligado, esta parte fica vazia.";
                return t;
            }
        } catch (Throwable e) {
            t.error = "Não deu para verificar a pasta da telemetria (" + e.getClass().getSimpleName() + ").";
            return t;
        }
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Ui.BR);
        String today = f.format(new Date(nowSec * 1000)), yest = f.format(new Date((nowSec - 86400) * 1000));
        List<String> cand = new ArrayList<String>();
        for (String day : new String[]{yest, today}) {
            File x = new File(d, day + ".csv");
            if (x.exists()) cand.add(x.getPath());
        }
        if (cand.isEmpty()) {
            // relógio errado ou dias sem registro: usa os 2 CSV mais recentes que existirem
            String[] names = null;
            try { names = d.list(); } catch (Throwable ignored) { }
            if (names != null) {
                List<String> csv = new ArrayList<String>();
                for (String s : names) if (s.matches("\\d{4}-\\d{2}-\\d{2}\\.csv")) csv.add(s);
                Collections.sort(csv);
                for (int i = Math.max(0, csv.size() - 2); i < csv.size(); i++) cand.add(new File(d, csv.get(i)).getPath());
            }
        }
        if (cand.isEmpty()) {
            t.error = d.canRead()
                    ? "A pasta da telemetria existe, mas não há arquivos de hoje nem de ontem. O registrador está rodando?"
                    : "A pasta da telemetria existe, mas o Android não deixou o app ler (" + dir
                      + "). Na ROM m7s ela deve ficar legível por todos (755/644).";
            return t;
        }
        boolean denied = false;
        for (String p : cand) {
            try {
                t.parse(p);
                t.files.add(p);
            } catch (SecurityException e) {
                denied = true;
            } catch (java.io.FileNotFoundException e) {
                denied = true; // no Android, "Permission denied" chega como FileNotFoundException
            } catch (Throwable ignored) { }
        }
        if (t.n == 0) {
            t.error = denied
                    ? "Os arquivos da telemetria existem, mas o app não tem permissão de leitura. Ajuste para 644."
                    : "Os arquivos da telemetria estão vazios ou num formato inesperado.";
            return t;
        }
        int lastTs = t.ts[t.n - 1];
        t.shifted = lastTs < nowSec - 86400 || lastTs > nowSec + 3600;
        t.refTs = t.shifted ? lastTs : nowSec;
        return t;
    }

    private void grow() {
        int m = ts.length * 2;
        ts = Arrays.copyOf(ts, m); up = Arrays.copyOf(up, m); pct = Arrays.copyOf(pct, m); mv = Arrays.copyOf(mv, m);
        plug = Arrays.copyOf(plug, m); bl = Arrays.copyOf(bl, m); khz = Arrays.copyOf(khz, m); busy = Arrays.copyOf(busy, m);
        load100 = Arrays.copyOf(load100, m); fg = Arrays.copyOf(fg, m);
    }

    private void parse(String path) throws Exception {
        BufferedReader r = new BufferedReader(new FileReader(path), 16384);
        try {
            String head = r.readLine();
            if (head == null) return;
            Map<String, Integer> col = new HashMap<String, Integer>();
            String[] hs = head.split(",");
            for (int i = 0; i < hs.length; i++) col.put(hs[i].trim(), i);
            int cTs = idx(col, "ts"), cUp = idx(col, "uptime_s"), cPct = idx(col, "batt_pct"), cMv = idx(col, "batt_mv"),
                    cUsb = idx(col, "usb"), cAc = idx(col, "ac"), cBl = idx(col, "backlight"), cKhz = idx(col, "cpu_khz"),
                    cBusy = idx(col, "cpu_busy_pct"), cLoad = idx(col, "load1"), cFg = idx(col, "fg_app");
            if (cTs < 0) return;
            String[] p = new String[Math.max(hs.length, 24) + 2];
            String line;
            int lastTs = n > 0 ? ts[n - 1] : 0;
            while ((line = r.readLine()) != null) {
                int k = split(line, p);
                int t = toInt(at(p, k, cTs), -1);
                if (t <= 0 || t < lastTs) continue; // linha quebrada ou fora de ordem
                if (n == ts.length) grow();
                ts[n] = t;
                up[n] = toInt(at(p, k, cUp), -1);
                pct[n] = toInt(at(p, k, cPct), -1);
                mv[n] = toInt(at(p, k, cMv), -1);
                plug[n] = (toInt(at(p, k, cUsb), 0) == 1 ? 1 : 0) | (toInt(at(p, k, cAc), 0) == 1 ? 2 : 0);
                bl[n] = toInt(at(p, k, cBl), 0);
                khz[n] = toInt(at(p, k, cKhz), -1);
                busy[n] = toInt(at(p, k, cBusy), -1);
                String ld = at(p, k, cLoad);
                try { load100[n] = ld == null ? -1 : Math.round(Float.parseFloat(ld) * 100); } catch (Throwable e) { load100[n] = -1; }
                String app = at(p, k, cFg);
                if (app == null || app.length() == 0) fg[n] = -1;
                else {
                    Integer id = appIdx.get(app);
                    if (id == null) { id = apps.size(); apps.add(app); appIdx.put(app, id); }
                    fg[n] = id;
                }
                lastTs = t;
                n++;
            }
        } finally {
            try { r.close(); } catch (Throwable ignored) { }
        }
    }

    private static int idx(Map<String, Integer> m, String k) { Integer i = m.get(k); return i == null ? -1 : i; }

    private static String at(String[] p, int k, int i) { return i >= 0 && i < k ? p[i] : null; }

    private static int split(String s, String[] out) {
        int k = 0, st = 0;
        for (int i = 0; i <= s.length() && k < out.length; i++) {
            if (i == s.length() || s.charAt(i) == ',') { out[k++] = s.substring(st, i).trim(); st = i + 1; }
        }
        return k;
    }

    private static int toInt(String s, int def) {
        if (s == null || s.length() == 0) return def;
        try { return Integer.parseInt(s); } catch (Throwable e) {
            try { return (int) Double.parseDouble(s); } catch (Throwable e2) { return def; }
        }
    }

    /** Primeiro índice com ts >= limite. */
    int from(long sinceTs) {
        int i = 0;
        while (i < n && ts[i] < sinceTs) i++;
        return i;
    }

    // ------------------------------------------------------------------ análise
    static final class Stats {
        long screenOn, screenOff, covered;
        double dropOn, hOn, dropOff, hOff;
        int maxBusy = -1, samples, busyHigh;
        double avgBusyOn = -1;
        int maxLoad100 = -1;
        final List<int[]> peaks = new ArrayList<int[]>();      // {indice}
        final List<Object[]> topApps = new ArrayList<Object[]>(); // {nome, segundos}
        final List<int[]> reboots = new ArrayList<int[]>();      // {indice do 1º ponto após o boot, indice anterior}
        int first, last;

        double rateOn() { return hOn >= 0.25 ? dropOn / hOn : Double.NaN; }
        double rateOff() { return hOff >= 0.25 ? dropOff / hOff : Double.NaN; }
        double rateAll() { double h = hOn + hOff; return h >= 0.25 ? (dropOn + dropOff) / h : Double.NaN; }
    }

    boolean sameBoot(int a, int b) {
        int dt = ts[b] - ts[a];
        if (dt <= 0) return false;
        if (up[a] < 0 || up[b] < 0) return dt < 3600;
        return up[b] >= up[a];
    }

    Stats stats(long sinceTs) {
        Stats s = new Stats();
        int st = from(sinceTs);
        s.first = st;
        s.last = n - 1;
        long[] appSec = new long[apps.size()];
        long busyOnSum = 0, busyOnN = 0;
        for (int i = st; i < n; i++) {
            if (busy[i] >= 0) {
                s.samples++;
                if (busy[i] > s.maxBusy) s.maxBusy = busy[i];
                if (busy[i] >= 85) s.busyHigh++;
                if (bl[i] > 0) { busyOnSum += busy[i]; busyOnN++; }
            }
            if (load100[i] > s.maxLoad100) s.maxLoad100 = load100[i];
            if (i == st) continue;
            int a = i - 1, b = i;
            if (!sameBoot(a, b)) { s.reboots.add(new int[]{b, a}); continue; }
            int dt = ts[b] - ts[a];
            if (dt > 12 * 3600) continue;
            s.covered += dt;
            boolean onA = bl[a] > 0;
            int onDt = onA ? Math.min(dt, 120) : 0;
            s.screenOn += onDt;
            s.screenOff += dt - onDt;
            if (onA && fg[a] >= 0) appSec[fg[a]] += onDt;
            if (plug[a] == 0 && plug[b] == 0 && pct[a] >= 0 && pct[b] >= 0) {
                if (onA && dt <= 120) { s.dropOn += pct[a] - pct[b]; s.hOn += dt / 3600.0; }
                else { s.dropOff += pct[a] - pct[b]; s.hOff += dt / 3600.0; }
            }
        }
        if (busyOnN > 0) s.avgBusyOn = (double) busyOnSum / busyOnN;
        // picos: maiores usos, separados por pelo menos 10 min
        Integer[] order = new Integer[Math.max(0, n - st)];
        for (int i = 0; i < order.length; i++) order[i] = st + i;
        Arrays.sort(order, new Comparator<Integer>() {
            public int compare(Integer x, Integer y) { return busy[y] - busy[x]; }
        });
        for (Integer i : order) {
            if (s.peaks.size() >= 5 || busy[i] < 50) break;
            boolean near = false;
            for (int[] p : s.peaks) if (Math.abs(ts[p[0]] - ts[i]) < 600) { near = true; break; }
            if (!near) s.peaks.add(new int[]{i});
        }
        List<Integer> ids = new ArrayList<Integer>();
        for (int i = 0; i < appSec.length; i++) if (appSec[i] > 0) ids.add(i);
        final long[] sec = appSec;
        Collections.sort(ids, new Comparator<Integer>() {
            public int compare(Integer x, Integer y) { return Long.compare(sec[y], sec[x]); }
        });
        for (int i = 0; i < ids.size() && i < 8; i++) s.topApps.add(new Object[]{apps.get(ids.get(i)), sec[ids.get(i)]});
        return s;
    }

    /** Pontos reduzidos (no máx. ~maxPts) de uma coluna, para desenhar. */
    int[] sampleIdx(long sinceTs, int maxPts) {
        int st = from(sinceTs), cnt = n - st;
        if (cnt <= 0) return new int[0];
        int step = Math.max(1, (cnt + maxPts - 1) / maxPts);
        int[] out = new int[(cnt + step - 1) / step];
        for (int k = 0; k < out.length; k++) out[k] = st + k * step;
        if (out.length > 0) out[out.length - 1] = n - 1;
        return out;
    }

    static String hm(long tsSec) {
        return new SimpleDateFormat("dd/MM HH:mm", Ui.BR).format(new Date(tsSec * 1000));
    }

    static String rebootReason(Telemetry t, int prev) {
        if (t.plug[prev] == 0 && t.mv[prev] > 0 && t.mv[prev] < 3560)
            return "provável queda por bateria fraca (última leitura " + Ui.f2(t.mv[prev] / 1000.0) + " V, " + t.pct[prev] + "%)";
        if (t.plug[prev] == 0 && t.mv[prev] > 0)
            return "reinício ou desligamento (última leitura na bateria, " + Ui.f2(t.mv[prev] / 1000.0) + " V)";
        return "reinício ou desligamento (estava na energia)";
    }
}
