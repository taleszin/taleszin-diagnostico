package app.taleszin.diagnostico;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Leitura tolerante de /proc e /sys: nunca lança exceção, devolve null/-1 quando não dá. */
final class Sys {
    private Sys() {}

    static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Uma única thread de trabalho, ociosa quando não há pedido (nada fica rodando em segundo plano). */
    static final ExecutorService BG = Executors.newSingleThreadExecutor();

    static void bg(final Runnable r) {
        BG.execute(new Runnable() {
            public void run() {
                try { r.run(); } catch (Throwable ignored) { }
            }
        });
    }

    static void ui(final Runnable r) {
        MAIN.post(new Runnable() {
            public void run() {
                try { r.run(); } catch (Throwable ignored) { }
            }
        });
    }

    /** Conteúdo do arquivo (até 64 KB), sem espaços nas pontas; null se não existe/sem permissão. */
    static String read(String path) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(path), 4096);
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[2048];
            int n;
            while ((n = r.read(buf)) > 0 && sb.length() < 65536) sb.append(buf, 0, n);
            return sb.toString().trim();
        } catch (Throwable t) {
            return null;
        } finally {
            close(r);
        }
    }

    static List<String> lines(String path) {
        List<String> out = new ArrayList<String>();
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(path), 4096);
            String l;
            while ((l = r.readLine()) != null && out.size() < 4000) out.add(l);
        } catch (Throwable t) {
            return null;
        } finally {
            close(r);
        }
        return out;
    }

    /** Últimas n linhas de um arquivo de texto (lido em fluxo). */
    static List<String> tail(String path, int n) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(path), 8192);
            String[] ring = new String[n];
            int count = 0;
            String l;
            while ((l = r.readLine()) != null) ring[count++ % n] = l;
            List<String> out = new ArrayList<String>();
            int start = Math.max(0, count - n);
            for (int i = start; i < count; i++) out.add(ring[i % n]);
            return out;
        } catch (Throwable t) {
            return null;
        } finally {
            close(r);
        }
    }

    static void close(java.io.Closeable c) {
        if (c != null) try { c.close(); } catch (Throwable ignored) { }
    }

    static long num(String path) {
        String s = read(path);
        if (s == null) return -1;
        try { return Long.parseLong(s.split("\\s+")[0]); } catch (Throwable t) { return -1; }
    }

    static boolean exists(String path) {
        try { return new File(path).exists(); } catch (Throwable t) { return false; }
    }

    static String or(String s) { return s == null || s.length() == 0 ? "indisponível" : s; }

    // ---------- Propriedades do sistema (android.os.SystemProperties via reflection) ----------

    private static Method propGet;

    static String prop(String key) {
        try {
            if (propGet == null) {
                Class<?> c = Class.forName("android.os.SystemProperties");
                propGet = c.getMethod("get", String.class, String.class);
            }
            String v = (String) propGet.invoke(null, key, "");
            return v == null || v.length() == 0 ? null : v;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------- /proc/meminfo ----------

    static Map<String, Long> meminfo() {
        Map<String, Long> m = new HashMap<String, Long>();
        List<String> ls = lines("/proc/meminfo");
        if (ls == null) return m;
        for (String l : ls) {
            int c = l.indexOf(':');
            if (c <= 0) continue;
            try {
                String v = l.substring(c + 1).trim().split("\\s+")[0];
                m.put(l.substring(0, c).trim(), Long.parseLong(v));
            } catch (Throwable ignored) { }
        }
        return m;
    }

    static long get(Map<String, Long> m, String k) {
        Long v = m.get(k);
        return v == null ? -1 : v;
    }

    // ---------- CPU ----------

    static final String CPU = "/sys/devices/system/cpu/";

    /** Quantos núcleos existem (possible/present), mesmo os desligados. */
    static int cores() {
        String s = read(CPU + "present");
        if (s == null) s = read(CPU + "possible");
        try {
            if (s != null) {
                int dash = s.lastIndexOf('-');
                return dash >= 0 ? Integer.parseInt(s.substring(dash + 1).trim()) + 1 : 1;
            }
        } catch (Throwable ignored) { }
        int n = 0;
        while (n < 16 && exists(CPU + "cpu" + n)) n++;
        return Math.max(1, n);
    }

    /** true/false; cpu0 normalmente não tem o arquivo "online" e está sempre ligado. */
    static boolean coreOnline(int i) {
        String s = read(CPU + "cpu" + i + "/online");
        if (s == null) return i == 0 || exists(CPU + "cpu" + i + "/cpufreq/scaling_cur_freq");
        return s.startsWith("1");
    }

    static long coreFreq(int i, String file) { return num(CPU + "cpu" + i + "/cpufreq/" + file); }

    static String cpuFile(String file) {
        String s = read(CPU + "cpu0/cpufreq/" + file);
        return s;
    }

    static Map<String, String> cpuinfo() {
        Map<String, String> m = new LinkedHashMap<String, String>();
        List<String> ls = lines("/proc/cpuinfo");
        if (ls == null) return m;
        for (String l : ls) {
            int c = l.indexOf(':');
            if (c <= 0) continue;
            String k = l.substring(0, c).trim();
            if (!m.containsKey(k)) m.put(k, l.substring(c + 1).trim());
        }
        return m;
    }

    /** Linhas "cpu", "cpu0", "cpu1"… do /proc/stat → {total, ocioso(idle+iowait)}. */
    static Map<String, long[]> procStat() {
        Map<String, long[]> m = new HashMap<String, long[]>();
        List<String> ls = lines("/proc/stat");
        if (ls == null) return m;
        for (String l : ls) {
            if (!l.startsWith("cpu")) continue;
            String[] p = l.trim().split("\\s+");
            long total = 0, idle = 0;
            for (int i = 1; i < p.length && i <= 8; i++) {
                long v = Long.parseLong(p[i]);
                total += v;
                if (i == 4 || i == 5) idle += v;
            }
            m.put(p[0], new long[]{total, idle});
        }
        return m;
    }

    /** Amostrador de uso de CPU: compara duas leituras do /proc/stat. */
    static final class CpuLoad {
        private Map<String, long[]> prev;
        /** -1 = ainda medindo. índice 0 = total; 1.. = núcleo 0.. */
        int[] last = new int[]{-1, -1, -1, -1, -1};
        int peak = -1;

        int[] sample() {
            Map<String, long[]> now = procStat();
            long[] pa = prev == null ? null : prev.get("cpu"), na = now.get("cpu");
            // intervalo curto demais (< ~0,2 s) dá 0% ou 100% por arredondamento: mantém a última medida
            if (pa != null && na != null && na[0] - pa[0] < 20) return last;
            int[] out = new int[last.length];
            for (int i = 0; i < out.length; i++) out[i] = -1;
            if (prev != null) {
                for (int i = 0; i < out.length; i++) {
                    String k = i == 0 ? "cpu" : "cpu" + (i - 1);
                    long[] a = prev.get(k), b = now.get(k);
                    if (a == null || b == null) continue;
                    long dt = b[0] - a[0], di = b[1] - a[1];
                    if (dt > 0) out[i] = (int) Math.max(0, Math.min(100, 100 * (dt - di) / dt));
                }
            }
            prev = now;
            last = out;
            if (out[0] > peak) peak = out[0];
            return out;
        }
    }

    static final CpuLoad LOAD = new CpuLoad();

    static long uptimeSec() {
        String s = read("/proc/uptime");
        try { return (long) Double.parseDouble(s.split("\\s+")[0]); } catch (Throwable t) { return -1; }
    }
}
