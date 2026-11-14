package app.taleszin.diagnostico;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.StatFs;

import java.util.Map;

/** Retratos de dados usados por mais de uma aba (e pelo relatório), com as regras das notas. */
final class Data {
    private Data() {}

    static final class Grade {
        final int g; final String why;
        Grade(int g, String why) { this.g = g; this.why = why; }
    }

    // ------------------------------------------------------------------ bateria
    static final String PS = "/sys/class/power_supply/";

    static final class Batt {
        int pct = -1, mv = -1;
        String status, health, tech;
        float tempC = Float.NaN;
        int usb = -1, ac = -1;

        static int adcVivo() {
            String bi = Sys.read("/sys/devices/platform/rk30-battery/batinfo");
            if (bi == null) return -1;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("cur adc value=(\\d+)").matcher(bi);
            if (!m.find()) return -1;
            int a = Integer.parseInt(m.group(1));
            return a > 300 ? a * 6060 / 1000 : -1;
        }

        static Batt read(Context c) {
            Batt b = new Batt();
            b.pct = (int) Sys.num(PS + "battery/capacity");
            long uv = Sys.num(PS + "battery/voltage_now");
            if (uv > 0) b.mv = (int) (uv > 100000 ? uv / 1000 : uv); // µV → mV (alguns kernels já dão mV)
            // RK30: voltage_now congela entre degraus de %; o ADC do batinfo é ao vivo
            // (6,06 mV por unidade, calibrado com os pares voltage/adc que o kernel loga)
            int vivo = adcVivo();
            if (vivo > 0) b.mv = vivo;
            b.status = Sys.read(PS + "battery/status");
            b.health = Sys.read(PS + "battery/health");
            b.tech = Sys.read(PS + "battery/technology");
            long t = Sys.num(PS + "battery/temp");
            if (t > -400 && t != -1 && t < 1500) b.tempC = t / 10f;
            b.usb = (int) Sys.num(PS + "usb/online");
            b.ac = (int) Sys.num(PS + "ac/online");
            // complemento pelo Android (intent "grudento", não registra receiver de verdade)
            try {
                Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (i != null) {
                    if (b.pct < 0) {
                        int l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                        if (l >= 0 && s > 0) b.pct = l * 100 / s;
                    }
                    if (b.mv <= 0) b.mv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                    if (Float.isNaN(b.tempC) && i.hasExtra(BatteryManager.EXTRA_TEMPERATURE)) {
                        int tt = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
                        if (tt > -400 && tt < 1500 && tt != 0) b.tempC = tt / 10f;
                    }
                    if (b.status == null) b.status = statusName(i.getIntExtra(BatteryManager.EXTRA_STATUS, -1));
                    if (b.health == null) b.health = healthName(i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1));
                    if (b.tech == null) b.tech = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
                    int plug = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                    if (b.usb < 0 && plug >= 0) b.usb = (plug & BatteryManager.BATTERY_PLUGGED_USB) != 0 ? 1 : 0;
                    if (b.ac < 0 && plug >= 0) b.ac = (plug & BatteryManager.BATTERY_PLUGGED_AC) != 0 ? 1 : 0;
                }
            } catch (Throwable ignored) { }
            return b;
        }

        boolean plugged() { return usb == 1 || ac == 1; }

        String source() {
            if (ac == 1) return "tomada (carregador)";
            if (usb == 1) return "USB";
            if (ac == 0 || usb == 0) return "bateria";
            return "indisponível";
        }

        String volts() { return mv > 0 ? Ui.f2(mv / 1000.0) + " V" : "indisponível"; }

        String statusPt() { return translate(status); }
        String healthPt() { return translate(health); }
    }

    static String statusName(int s) {
        switch (s) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL: return "Full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Not charging";
            default: return null;
        }
    }

    static String healthName(int h) {
        switch (h) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Over voltage";
            case BatteryManager.BATTERY_HEALTH_COLD: return "Cold";
            default: return null;
        }
    }

    static String translate(String s) {
        if (s == null) return "indisponível";
        String k = s.trim().toLowerCase(Ui.BR);
        if (k.equals("charging")) return "carregando";
        if (k.equals("discharging")) return "descarregando";
        if (k.equals("full")) return "cheia";
        if (k.equals("not charging")) return "não está carregando";
        if (k.equals("good")) return "boa (segundo o driver)";
        if (k.equals("overheat")) return "superaquecida";
        if (k.equals("dead")) return "morta";
        if (k.equals("cold")) return "fria demais";
        if (k.equals("over voltage")) return "tensão alta demais";
        if (k.equals("unknown")) return "desconhecido";
        return s;
    }

    static Grade gradeBatt(Batt b) {
        if (b.pct < 0 && b.mv <= 0) return new Grade(Ui.NA, "não deu para ler a bateria");
        String h = b.health == null ? "" : b.health.toLowerCase(Ui.BR);
        if (h.contains("dead") || h.contains("overheat") || h.contains("over"))
            return new Grade(Ui.BAD, "o driver acusa problema: " + b.healthPt());
        if (!Float.isNaN(b.tempC) && b.tempC >= 45) return new Grade(Ui.BAD, "quente demais (" + Ui.f1(b.tempC) + " °C)");
        if (b.plugged()) return new Grade(Ui.OK, "ligado na energia");
        if (b.mv > 0 && b.mv < 3600) return new Grade(Ui.BAD, "tensão baixa: perto do corte de 3,48 V");
        if (b.mv > 0 && b.mv < 3750) return new Grade(Ui.WARN, "tensão média: evite tarefas pesadas");
        if (b.pct >= 0 && b.pct < 20) return new Grade(Ui.WARN, "pouca carga");
        return new Grade(Ui.OK, "tensão confortável");
    }

    // ------------------------------------------------------------------ memória
    static final class Mem {
        long total = -1, free = -1, cached = -1, buffers = -1, swapTotal = -1, swapFree = -1;

        static Mem read() {
            Map<String, Long> m = Sys.meminfo();
            Mem r = new Mem();
            r.total = Sys.get(m, "MemTotal");
            r.free = Sys.get(m, "MemFree");
            r.cached = Sys.get(m, "Cached");
            r.buffers = Sys.get(m, "Buffers");
            r.swapTotal = Sys.get(m, "SwapTotal");
            r.swapFree = Sys.get(m, "SwapFree");
            return r;
        }

        long cache() { return Math.max(0, cached) + Math.max(0, buffers); }
        long avail() { return Math.max(0, free) + cache(); }
        long used() { return total < 0 ? -1 : Math.max(0, total - avail()); }
    }

    static Grade gradeMem(Mem m) {
        if (m.total <= 0) return new Grade(Ui.NA, "não deu para ler /proc/meminfo");
        double av = 100.0 * m.avail() / m.total;
        if (av >= 30) return new Grade(Ui.OK, Ui.pct(av) + " disponível (livre + cache)");
        if (av >= 15) return new Grade(Ui.WARN, "só " + Ui.pct(av) + " disponível: feche apps parados");
        return new Grade(Ui.BAD, "quase sem memória (" + Ui.pct(av) + "): o Android vai fechar apps");
    }

    // ------------------------------------------------------------------ CPU
    static Grade gradeCpu(int use) {
        if (use < 0) return new Grade(Ui.NA, "medindo…");
        if (use < 60) return new Grade(Ui.OK, "folgada (" + use + "% de uso)");
        if (use < 85) return new Grade(Ui.WARN, "ocupada (" + use + "%): pode engasgar");
        return new Grade(Ui.BAD, "no limite (" + use + "%): vai travar e gastar bateria");
    }

    // ------------------------------------------------------------------ armazenamento
    static final class Disk {
        long total = -1, free = -1;

        static Disk of(String path) {
            Disk d = new Disk();
            try {
                StatFs s = new StatFs(path);
                long bs = s.getBlockSizeLong();
                d.total = s.getBlockCountLong() * bs;
                d.free = s.getAvailableBlocksLong() * bs;
            } catch (Throwable ignored) { }
            return d;
        }
    }

    static Grade gradeDisk(Disk d) {
        if (d.total <= 0) return new Grade(Ui.NA, "indisponível");
        double p = 100.0 * d.free / d.total;
        long mb = d.free >> 20;
        if (p >= 20 && mb >= 300) return new Grade(Ui.OK, "espaço sobrando");
        if (p >= 10 && mb >= 150) return new Grade(Ui.WARN, "ficando cheio: apague o que não usa");
        return new Grade(Ui.BAD, "quase cheio: o sistema fica lento e apps falham");
    }

    static Grade gradeUptime(long sec) {
        if (sec < 0) return new Grade(Ui.NA, "indisponível");
        if (sec < 3 * 86400) return new Grade(Ui.OK, "reiniciado há pouco tempo");
        if (sec < 7 * 86400) return new Grade(Ui.WARN, "reiniciar de vez em quando ajuda a liberar memória");
        return new Grade(Ui.BAD, "mais de uma semana ligado: reinicie");
    }
}
