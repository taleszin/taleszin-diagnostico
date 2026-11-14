package app.taleszin.diagnostico;

import android.os.Environment;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class StoragePage extends Page {
    StoragePage(MainActivity a) { super(a, "Armazenamento", Charts.I_STO); }

    private LinearLayout mounts, parts;
    private TextView rdTv, wrTv, ioNote;
    private int ticks;

    static String sdcard() {
        try { return Environment.getExternalStorageDirectory().getPath(); } catch (Throwable t) { return "/sdcard"; }
    }

    static String[][] mountList() {
        return new String[][]{
                {"/system", "Sistema", "O Android em si. Só leitura: não dá para apagar nem encher."},
                {"/data", "Apps e dados", "Onde ficam os apps instalados e o que eles guardam. Se encher, apps travam e não atualizam."},
                {"/cache", "Cache do sistema", "Área temporária para atualizações e arquivos de passagem. Pode estar quase cheia sem problema."},
                {sdcard(), "Seus arquivos (/sdcard)", "Fotos, downloads, músicas. Aparece no computador quando você liga o cabo USB."}
        };
    }

    @Override void build(LinearLayout body) {
        Ui.header(body, "Armazenamento", "A memória interna (NAND), dividida em partições, como gavetas de um armário, cada uma com uma função.");
        mounts = Ui.card(body, "Espaço livre");
        LinearLayout io = Ui.card(body, "Leitura e escrita na NAND (desde que ligou)");
        rdTv = Ui.kv(io, "Lido", "…");
        wrTv = Ui.kv(io, "Gravado", "…");
        ioNote = Ui.note(io, "A NAND deste tablet é lenta, principalmente para gravar: muita gravação ao mesmo tempo deixa tudo travado. Ela também se desgasta com escritas, então gravações enormes todo dia não são bom sinal.");
        parts = Ui.card(body, "Partições (/proc/mtd)");
        refresh();
    }

    @Override void onShow() { if (mounts != null) refresh(); }

    @Override boolean live() { return true; }

    @Override void tick() {
        if (ticks++ % 5 == 4) io();
    }

    private void refresh() {
        mounts.removeAllViews();
        TextView t = Ui.text(a, "Espaço livre", 16, Ui.ACCENT, Ui.BOLD);
        t.setPadding(0, 0, 0, Ui.dp(6));
        mounts.addView(t);
        for (String[] m : mountList()) {
            Data.Disk d = Data.Disk.of(m[0]);
            Ui.kv(mounts, m[1] + "  " + m[0], d.total > 0 ? Ui.bytes(d.free) + " livres de " + Ui.bytes(d.total) : "indisponível");
            Charts.Meter mt = new Charts.Meter(a);
            Data.Grade g = m[0].equals("/system") ? new Data.Grade(Ui.OK, "") : Data.gradeDisk(d);
            mt.set(d.total > 0 ? 1f - (float) d.free / d.total : 0, g.g == Ui.OK ? Ui.ACCENT : Ui.gradeColor(g.g));
            mounts.addView(mt, new LinearLayout.LayoutParams(-1, -2));
            TextView e = Ui.explain(a, m[2] + (m[0].equals("/system") || m[0].equals("/cache") || d.total <= 0 ? "" : " Nota: " + Ui.gradeWord(g.g).toLowerCase(Ui.BR) + ", " + g.why + "."));
            e.setPadding(0, Ui.dp(2), 0, Ui.dp(10));
            mounts.addView(e);
        }
        Ui.note(mounts, "A barra mostra quanto já está ocupado.");

        parts.removeAllViews();
        TextView pt = Ui.text(a, "Partições (/proc/mtd)", 16, Ui.ACCENT, Ui.BOLD);
        pt.setPadding(0, 0, 0, Ui.dp(6));
        parts.addView(pt);
        List<String[]> mtd = mtd();
        Map<String, long[]> ds = diskstats();
        if (mtd.isEmpty()) Ui.note(parts, "/proc/mtd indisponível (aparelhos com eMMC não usam MTD).");
        for (String[] p : mtd) {
            long[] io = ds.get("mtdblock" + p[0].replace("mtd", ""));
            String extra = io != null && (io[0] > 0 || io[1] > 0) ? "  ·  lido " + Ui.bytes(io[0] * 512) + ", gravado " + Ui.bytes(io[1] * 512) : "";
            Ui.kv(parts, p[2] + " (" + p[0] + ")", Ui.bytes(Long.parseLong(p[1])));
            TextView e = Ui.explain(a, partText(p[2]) + extra);
            e.setPadding(0, 0, 0, Ui.dp(8));
            parts.addView(e);
        }
        io();
    }

    private void io() {
        long[] s = nandTotals();
        rdTv.setText(s[0] < 0 ? "indisponível" : Ui.bytes(s[0] * 512));
        wrTv.setText(s[1] < 0 ? "indisponível" : Ui.bytes(s[1] * 512));
    }

    /** {mtdN, tamanho em bytes, nome} */
    static List<String[]> mtd() {
        List<String[]> out = new ArrayList<String[]>();
        List<String> ls = Sys.lines("/proc/mtd");
        if (ls == null) return out;
        for (String l : ls) {
            if (!l.startsWith("mtd")) continue;
            try {
                String[] p = l.split("\\s+", 4);
                String name = p.length > 3 ? p[3].replace("\"", "").trim() : "?";
                out.add(new String[]{p[0].replace(":", ""), String.valueOf(Long.parseLong(p[1], 16)), name});
            } catch (Throwable ignored) { }
        }
        return out;
    }

    /** nome → {setores lidos, setores gravados} */
    static Map<String, long[]> diskstats() {
        Map<String, long[]> m = new HashMap<String, long[]>();
        List<String> ls = Sys.lines("/proc/diskstats");
        if (ls == null) return m;
        for (String l : ls) {
            String[] p = l.trim().split("\\s+");
            if (p.length < 10) continue;
            try { m.put(p[2], new long[]{Long.parseLong(p[5]), Long.parseLong(p[9])}); } catch (Throwable ignored) { }
        }
        return m;
    }

    static long[] nandTotals() {
        Map<String, long[]> m = diskstats();
        if (m.isEmpty()) return new long[]{-1, -1};
        long r = 0, w = 0;
        boolean any = false;
        for (Map.Entry<String, long[]> e : m.entrySet()) {
            if (!e.getKey().startsWith("mtdblock")) continue;
            any = true;
            r += e.getValue()[0];
            w += e.getValue()[1];
        }
        return any ? new long[]{r, w} : new long[]{-1, -1};
    }

    static String partText(String n) {
        String k = n.toLowerCase(Ui.BR);
        if (k.equals("misc")) return "Recados entre o sistema e o modo de recuperação (ex.: “apague tudo no próximo boot”).";
        if (k.equals("kernel")) return "O kernel Linux, o núcleo que conversa com o hardware.";
        if (k.equals("boot")) return "O que o tablet carrega primeiro ao ligar (ramdisk de inicialização).";
        if (k.equals("recovery")) return "Modo de recuperação, usado para restaurar ou atualizar o sistema.";
        if (k.equals("backup")) return "Cópia de segurança da imagem de fábrica.";
        if (k.equals("cache")) return "Arquivos temporários do sistema (/cache).";
        if (k.equals("userdata")) return "Apps instalados e seus dados (/data).";
        if (k.equals("kpanic")) return "Guarda o registro de um travamento grave do kernel para análise.";
        if (k.equals("system")) return "O Android em si (/system).";
        if (k.equals("metadata")) return "Dados de controle (ex.: criptografia).";
        if (k.equals("user")) return "Seus arquivos (/sdcard), visíveis pelo cabo USB.";
        if (k.equals("parameter")) return "Parâmetros de inicialização da Rockchip (como dividir a NAND).";
        if (k.contains("uboot") || k.contains("loader")) return "Carregador de inicialização (bootloader).";
        return "Partição do fabricante.";
    }

    @Override void report(StringBuilder sb) {
        h(sb, "Armazenamento");
        for (String[] m : mountList()) {
            Data.Disk d = Data.Disk.of(m[0]);
            kv(sb, m[0], d.total > 0 ? Ui.bytes(d.free) + " livres de " + Ui.bytes(d.total) : "indisponível");
        }
        long[] s = nandTotals();
        kv(sb, "NAND lido/gravado desde o boot", s[0] < 0 ? "indisponível" : Ui.bytes(s[0] * 512) + " / " + Ui.bytes(s[1] * 512));
        Map<String, long[]> ds = diskstats();
        for (String[] p : mtd()) {
            long[] io = ds.get("mtdblock" + p[0].replace("mtd", ""));
            kv(sb, "  " + p[0] + " " + p[2], Ui.bytes(Long.parseLong(p[1])) + (io != null ? " (lido " + Ui.bytes(io[0] * 512) + ", gravado " + Ui.bytes(io[1] * 512) + ")" : ""));
        }
    }
}
