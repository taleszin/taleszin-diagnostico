package app.taleszin.diagnostico;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Temperatura: procura qualquer sensor exposto pelo kernel (thermal_zone, hwmon, bateria).
 * O RK3026 deste tablet não tem sensor no chip (o TSADC só chegou no RK3288) e a bateria
 * (rk30_adc_battery) não informa temperatura; então, sem sensor, o app mostra uma estimativa
 * de aquecimento a partir do uso × velocidade do CPU, sempre rotulada como estimativa.
 */
final class Thermal {
    private Thermal() {}

    static final class Reading { final String name; final float c; Reading(String n, float c) { name = n; this.c = c; } }

    static List<Reading> sensors() {
        List<Reading> out = new ArrayList<>();
        File[] tz = new File("/sys/class/thermal").listFiles();
        if (tz != null) for (File z : tz) {
            if (!z.getName().startsWith("thermal_zone")) continue;
            add(out, Sys.read(z + "/type"), z + "/temp", 1000);
        }
        File[] hw = new File("/sys/class/hwmon").listFiles();
        if (hw != null) for (File h : hw) {
            for (String base : new String[]{h.getPath(), h.getPath() + "/device"}) {
                for (int i = 1; i <= 4; i++) add(out, Sys.read(base + "/name"), base + "/temp" + i + "_input", 1000);
            }
        }
        File[] ps = new File("/sys/class/power_supply").listFiles();
        if (ps != null) for (File p : ps) add(out, "bateria (" + p.getName() + ")", p + "/temp", 10);
        return out;
    }

    private static void add(List<Reading> out, String name, String path, int div) {
        String s = Sys.read(path);
        if (s == null) return;
        try {
            long v = Long.parseLong(s.trim());
            float c = v > 1000 ? v / 1000f : (div == 10 ? v / 10f : v);   // milésimos, décimos ou °C direto
            if (c > -30 && c < 150 && v != 0) out.add(new Reading(name == null ? path : name.trim(), c));
        } catch (NumberFormatException ignored) { }
    }

    /** Média móvel (~1 min) de uso × velocidade relativa: 0 = parado, 1 = 100% no máximo do chip. */
    static final class Effort {
        private double ema = -1;
        double push(int busyPct, long khz, long hwMax) {
            if (busyPct < 0 || hwMax <= 0) return ema < 0 ? 0 : ema;
            double rel = Math.min(1, Math.max(0, (double) khz / hwMax));
            double x = busyPct / 100.0 * rel * rel;          // potência cresce mais que linear com a velocidade
            ema = ema < 0 ? x : ema + (x - ema) / 60.0;       // amostra por segundo, constante de ~60 s
            return ema;
        }
    }
    static final Effort EFFORT = new Effort();

    static String effortWord(double e) {
        if (e < 0.15) return "frio";
        if (e < 0.35) return "morno";
        if (e < 0.6) return "aquecendo";
        return "quente";
    }
    static int effortGrade(double e) { return e < 0.35 ? Ui.OK : e < 0.6 ? Ui.WARN : Ui.BAD; }
}
