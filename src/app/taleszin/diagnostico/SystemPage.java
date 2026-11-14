package app.taleszin.diagnostico;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.SystemClock;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

final class SystemPage extends Page {
    SystemPage(MainActivity a) { super(a, "Sistema", Charts.I_SYS); }

    static final String XPOSED = "de.robv.android.xposed.installer";

    private TextView upTv, awakeTv, sleepTv, sleepGrade, clockTv, heapTv;

    @Override void build(LinearLayout body) {
        Ui.header(body, "Sistema", "A identidade do software: versão do Android, da ROM, do kernel e alguns ajustes internos.");

        LinearLayout b = Ui.card(body, "Build (identidade do sistema)");
        for (String[] r : buildRows()) {
            TextView v = Ui.kv(b, r[0], r[1]);
            if (r[1] != null && r[1].length() > 40) v.setTextSize(12);
        }
        Ui.note(b, "O fingerprint é a “impressão digital” do sistema: identifica exatamente qual build está instalado. O kernel é o núcleo Linux que conversa com o hardware.");

        LinearLayout r = Ui.card(body, "ROM m7s e Xposed");
        for (String[] x : romRows(a)) Ui.kv(r, x[0], x[1]);
        Ui.note(r, "A ROM m7s é a versão modificada do sistema para este tablet. O Xposed é uma ferramenta que permite módulos mudarem o comportamento do Android sem trocar a ROM; este app só verifica se ele está instalado.");

        LinearLayout t = Ui.card(body, "Tempo ligado e sono profundo (ao vivo)");
        upTv = Ui.kv(t, "Ligado há", "…");
        awakeTv = Ui.kv(t, "Acordado (CPU trabalhando)", "…");
        sleepTv = Ui.kv(t, "Em sono profundo", "…");
        sleepGrade = Ui.grade(t);
        Ui.note(t, "Com a tela apagada, o tablet deveria “dormir” de verdade: o CPU para quase por completo e o gasto de bateria cai muito. "
                + "O Android tem dois relógios: um que conta sempre (desde que ligou) e outro que só anda com o CPU acordado. A diferença entre eles é o tempo de sono profundo. "
                + "Se o tablet passa horas de tela apagada mas quase não dorme, algum app está segurando ele acordado (wakelock) e drenando a bateria.");

        LinearLayout ro = Ui.card(body, "Root (superusuário)");
        for (String[] x : rootRows()) Ui.kv(ro, x[0], x[1]);
        Ui.note(ro, "Root é o acesso total ao sistema. Ajuda a ajustar e consertar coisas, mas um app mal-intencionado com root pode fazer qualquer coisa. Este app só verifica se existe; nunca executa o su.");

        LinearLayout l = Ui.card(body, "Região e idioma");
        for (String[] x : localeRows()) Ui.kv(l, x[0], x[1]);
        clockTv = Ui.kv(l, "Hora agora", "…");
        Ui.note(l, "Se a hora estiver errada, sites com HTTPS dão erro de certificado. Ela acerta sozinha quando o Wi-Fi conecta (se “data e hora automáticas” estiver ligado).");

        LinearLayout ap = Ui.card(body, "Este app");
        heapTv = Ui.kv(ap, "Memória Java em uso", "…");
        Ui.kv(ap, "Limite por app (heap)", Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB");
        Ui.note(ap, "O Diagnóstico não roda nada em segundo plano: só lê os dados da aba aberta, uma vez por segundo, enquanto você está olhando.");
        tick();
    }

    static List<String[]> buildRows() {
        List<String[]> o = new ArrayList<String[]>();
        o.add(new String[]{"Modelo", Build.MODEL});
        o.add(new String[]{"Fabricante", Build.MANUFACTURER});
        o.add(new String[]{"Marca", Build.BRAND});
        o.add(new String[]{"Produto", Build.PRODUCT});
        o.add(new String[]{"Aparelho (device)", Build.DEVICE});
        o.add(new String[]{"Placa (board)", Build.BOARD});
        o.add(new String[]{"Hardware", Build.HARDWARE});
        o.add(new String[]{"Android", Build.VERSION.RELEASE});
        o.add(new String[]{"SDK (API)", String.valueOf(Build.VERSION.SDK_INT)});
        o.add(new String[]{"Build", Build.DISPLAY});
        o.add(new String[]{"Incremental", Build.VERSION.INCREMENTAL});
        o.add(new String[]{"Tipo / tags", Build.TYPE + " / " + Build.TAGS});
        o.add(new String[]{"Data do build", new SimpleDateFormat("dd/MM/yyyy HH:mm", Ui.BR).format(new Date(Build.TIME))});
        o.add(new String[]{"Bootloader", Build.BOOTLOADER});
        o.add(new String[]{"Fingerprint", Build.FINGERPRINT});
        String k = Sys.read("/proc/version");
        o.add(new String[]{"Kernel", k == null ? "indisponível" : k});
        String se = Sys.read("/sys/fs/selinux/enforce");
        o.add(new String[]{"SELinux", se == null ? "desativado ou indisponível" : se.startsWith("1") ? "impondo regras (enforcing)" : "permissivo"});
        return o;
    }

    static List<String[]> romRows(Context c) {
        List<String[]> o = new ArrayList<String[]>();
        String v = Sys.prop("ro.m7s.version");
        o.add(new String[]{"Versão da ROM m7s", v == null ? "não é a ROM m7s" : v});
        o.add(new String[]{"low_ram", Sys.or(Sys.prop("ro.config.low_ram"))});
        o.add(new String[]{"Heap máximo (largeHeap)", Sys.or(Sys.prop("dalvik.vm.heapsize"))});
        o.add(new String[]{"Heap padrão por app", Sys.or(Sys.prop("dalvik.vm.heapgrowthlimit"))});
        String x;
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(XPOSED, 0);
            x = "instalado (versão " + pi.versionName + ")";
        } catch (Throwable t) {
            x = Sys.exists("/data/data/" + XPOSED) ? "instalado" : "não instalado";
        }
        o.add(new String[]{"Xposed Installer", x});
        boolean bridge = Sys.exists("/data/data/" + XPOSED + "/bin/XposedBridge.jar") || Sys.exists("/system/framework/XposedBridge.jar");
        o.add(new String[]{"XposedBridge", bridge ? "presente" : "não encontrado (ou sem permissão para ver)"});
        return o;
    }

    static List<String[]> rootRows() {
        List<String[]> o = new ArrayList<String[]>();
        StringBuilder su = new StringBuilder();
        for (String p : new String[]{"/system/xbin/su", "/system/bin/su", "/sbin/su", "/system/xbin/daemonsu"})
            if (Sys.exists(p)) { if (su.length() > 0) su.append(", "); su.append(p); }
        String sec = Sys.prop("ro.secure");
        boolean root = su.length() > 0 || "0".equals(sec);
        o.add(new String[]{"Root disponível", root ? "sim" : "não detectado"});
        o.add(new String[]{"Binário su", su.length() == 0 ? "nenhum" : su.toString()});
        o.add(new String[]{"ro.secure", sec == null ? "indisponível" : sec + ("0".equals(sec) ? " (adb com root)" : " (padrão seguro)")});
        o.add(new String[]{"ro.debuggable", Sys.or(Sys.prop("ro.debuggable"))});
        return o;
    }

    static List<String[]> localeRows() {
        List<String[]> o = new ArrayList<String[]>();
        TimeZone tz = TimeZone.getDefault();
        int off = tz.getOffset(System.currentTimeMillis()) / 60000;
        o.add(new String[]{"Fuso horário", tz.getID() + " (UTC" + (off >= 0 ? "+" : "−") + Math.abs(off) / 60 + (Math.abs(off) % 60 != 0 ? ":" + Math.abs(off) % 60 : "") + ")"});
        o.add(new String[]{"Nome do fuso", tz.getDisplayName(Ui.BR)});
        Locale l = Locale.getDefault();
        o.add(new String[]{"Idioma", l.getDisplayName(Ui.BR) + " (" + l.toString() + ")"});
        return o;
    }

    static long[] times() {
        long el = SystemClock.elapsedRealtime() / 1000, aw = SystemClock.uptimeMillis() / 1000;
        return new long[]{el, aw, Math.max(0, el - aw)};
    }

    @Override boolean live() { return true; }

    @Override void tick() {
        if (upTv == null) return;
        long[] t = times();
        upTv.setText(Ui.dur(t[0]));
        awakeTv.setText(Ui.dur(t[1]) + (t[0] > 0 ? " (" + Math.round(100.0 * t[1] / t[0]) + "%)" : ""));
        sleepTv.setText(Ui.dur(t[2]) + (t[0] > 0 ? " (" + Math.round(100.0 * t[2] / t[0]) + "%)" : ""));
        if (t[0] < 1800) Ui.setGrade(sleepGrade, Ui.NA, "ligado há pouco: ainda sem conclusão");
        else if (t[2] * 100 >= t[0] * 50) Ui.setGrade(sleepGrade, Ui.OK, "dorme bem quando está parado");
        else if (t[2] * 100 >= t[0] * 15) Ui.setGrade(sleepGrade, Ui.WARN, "dorme pouco (normal se a tela ficou muito tempo ligada)");
        else Ui.setGrade(sleepGrade, Ui.BAD, "quase não dorme: se a tela ficou apagada, algo o mantém acordado");
        clockTv.setText(new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Ui.BR).format(new Date()));
        Runtime r = Runtime.getRuntime();
        heapTv.setText(Ui.bytes(r.totalMemory() - r.freeMemory()));
    }

    @Override void report(StringBuilder sb) {
        h(sb, "Sistema");
        for (String[] r : buildRows()) kv(sb, r[0], r[1]);
        for (String[] r : romRows(a)) kv(sb, r[0], r[1]);
        long[] t = times();
        kv(sb, "Ligado há", Ui.dur(t[0]));
        kv(sb, "Acordado", Ui.dur(t[1]));
        kv(sb, "Sono profundo", Ui.dur(t[2]));
        for (String[] r : rootRows()) kv(sb, r[0], r[1]);
        for (String[] r : localeRows()) kv(sb, r[0], r[1]);
    }
}
