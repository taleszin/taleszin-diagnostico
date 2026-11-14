package app.taleszin.diagnostico;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class NetworkPage extends Page {
    NetworkPage(MainActivity a) { super(a, "Rede", Charts.I_NET); }

    private TextView state, ssid, rssi, grade, speed, freq, ip, gw, dns, mask, mac, chip;
    private Charts.Meter sig;
    private TextView rxTot, txTot, rxNow, txNow, wlanTot;
    private long lastRx = -1, lastTx = -1, lastT;

    @Override void build(LinearLayout body) {
        Ui.header(body, "Rede", "A conexão Wi-Fi e quanto de internet o tablet já usou desde que ligou.");
        LinearLayout w = Ui.card(body, "Wi-Fi (ao vivo)");
        state = Ui.kv(w, "Estado", "…");
        ssid = Ui.kv(w, "Rede (SSID)", "…");
        rssi = Ui.kv(w, "Sinal", "…");
        sig = new Charts.Meter(a);
        w.addView(sig, new LinearLayout.LayoutParams(-1, -2));
        grade = Ui.grade(w);
        speed = Ui.kv(w, "Velocidade do link", "…");
        freq = Ui.kv(w, "Frequência", "…");
        ip = Ui.kv(w, "Endereço IP", "…");
        mask = Ui.kv(w, "Máscara", "…");
        gw = Ui.kv(w, "Gateway (roteador)", "…");
        dns = Ui.kv(w, "DNS", "…");
        mac = Ui.kv(w, "MAC", "…");
        chip = Ui.kv(w, "Placa", "…");
        Ui.note(w, "O sinal é medido em dBm, sempre negativo: quanto mais perto de zero, melhor. -50 é excelente, -67 é bom para vídeo, abaixo de -75 começa a cair. "
                + "A velocidade do link é o máximo combinado com o roteador, não a velocidade real da internet. O chip RTL8188EU só funciona em 2,4 GHz (até 150 Mbps).");

        LinearLayout t = Ui.card(body, "Tráfego desde que ligou");
        rxTot = Ui.kv(t, "Recebido (download)", "…");
        txTot = Ui.kv(t, "Enviado (upload)", "…");
        wlanTot = Ui.kv(t, "Só pelo Wi-Fi (wlan0)", "…");
        rxNow = Ui.kv(t, "Baixando agora", "…");
        txNow = Ui.kv(t, "Enviando agora", "…");
        Ui.note(t, "Conta tudo o que passou pela rede: páginas, vídeos, atualizações. Zera quando o tablet reinicia.");
        slow();
        tick();
    }

    @Override void onShow() {
        lastRx = -1;
        if (state != null) slow();
    }

    static String ipStr(int i) {
        if (i == 0) return "indisponível";
        return (i & 0xff) + "." + ((i >> 8) & 0xff) + "." + ((i >> 16) & 0xff) + "." + ((i >> 24) & 0xff);
    }

    static String freqStr(int f) {
        if (f <= 0) return "indisponível";
        if (f >= 2400 && f < 2500) return Ui.f1(f / 1000.0) + " GHz (canal " + (f == 2484 ? 14 : (f - 2407) / 5) + ")";
        if (f >= 5000) return Ui.f1(f / 1000.0) + " GHz (canal " + (f - 5000) / 5 + ")";
        return f + " MHz";
    }

    static Data.Grade gradeRssi(int r) {
        if (r <= -127 || r >= 0) return new Data.Grade(Ui.NA, "sem sinal");
        if (r >= -55) return new Data.Grade(Ui.OK, "excelente");
        if (r >= -67) return new Data.Grade(Ui.OK, "bom");
        if (r >= -75) return new Data.Grade(Ui.WARN, "razoável: pode travar em vídeo");
        return new Data.Grade(Ui.BAD, "fraco: chegue mais perto do roteador");
    }

    /** Dados que mudam pouco (IP, DNS, frequência). */
    private void slow() {
        List<String[]> rows = info(a);
        for (String[] r : rows) {
            if (r[0].equals("IP")) ip.setText(r[1]);
            else if (r[0].equals("Máscara")) mask.setText(r[1]);
            else if (r[0].equals("Gateway")) gw.setText(r[1]);
            else if (r[0].equals("DNS")) dns.setText(r[1]);
            else if (r[0].equals("MAC")) mac.setText(r[1]);
            else if (r[0].equals("Frequência")) freq.setText(r[1]);
            else if (r[0].equals("Estado")) state.setText(r[1]);
        }
        chip.setText(wifiChip());
    }

    static String wifiChip() {
        String drv = Sys.read("/sys/class/net/wlan0/device/uevent");
        if (drv != null) for (String l : drv.split("\n")) if (l.startsWith("DRIVER=")) return l.substring(7);
        return Sys.exists("/sys/class/net/wlan0") ? "wlan0" : "Wi-Fi desligado ou sem placa";
    }

    static List<String[]> info(Context c) {
        List<String[]> out = new ArrayList<String[]>();
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            boolean on = wm.isWifiEnabled();
            out.add(new String[]{"Estado", !on ? "Wi-Fi desligado" : ni != null && ni.isConnected() ? "conectado" : "ligado, sem conexão"});
            WifiInfo wi = wm.getConnectionInfo();
            String s = wi != null ? wi.getSSID() : null;
            if (s != null && s.length() > 1 && s.startsWith("\"")) s = s.substring(1, s.length() - 1);
            out.add(new String[]{"SSID", s == null || s.contains("unknown ssid") || s.equals("0x") ? "nenhuma" : s});
            int r = wi != null ? wi.getRssi() : -127;
            out.add(new String[]{"Sinal", r <= -127 || r >= 0 ? "sem sinal" : r + " dBm (" + gradeRssi(r).why + ")"});
            out.add(new String[]{"Link", wi != null && wi.getLinkSpeed() > 0 ? wi.getLinkSpeed() + " Mbps" : "indisponível"});
            int f = -1;
            String bssid = wi != null ? wi.getBSSID() : null;
            List<ScanResult> sr = bssid != null ? wm.getScanResults() : null;
            if (sr != null) for (ScanResult x : sr) if (bssid.equalsIgnoreCase(x.BSSID)) { f = x.frequency; break; }
            out.add(new String[]{"Frequência", f > 0 ? freqStr(f) : "indisponível (sem varredura recente)"});
            DhcpInfo d = wm.getDhcpInfo();
            out.add(new String[]{"IP", wi != null ? ipStr(wi.getIpAddress()) : "indisponível"});
            out.add(new String[]{"Máscara", d != null ? ipStr(d.netmask) : "indisponível"});
            out.add(new String[]{"Gateway", d != null ? ipStr(d.gateway) : "indisponível"});
            out.add(new String[]{"DNS", d != null ? ipStr(d.dns1) + (d.dns2 != 0 ? "  ·  " + ipStr(d.dns2) : "") : "indisponível"});
            String m = wi != null ? wi.getMacAddress() : null;
            out.add(new String[]{"MAC", Sys.or(m != null ? m : Sys.read("/sys/class/net/wlan0/address"))});
        } catch (Throwable t) {
            out.add(new String[]{"Estado", "indisponível (" + t.getClass().getSimpleName() + ")"});
        }
        return out;
    }

    @Override boolean live() { return true; }

    private int ticks;

    @Override void tick() {
        if (state == null) return;
        try {
            WifiManager wm = (WifiManager) a.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm.getConnectionInfo();
            String s = wi != null ? wi.getSSID() : null;
            if (s != null && s.length() > 1 && s.startsWith("\"")) s = s.substring(1, s.length() - 1);
            ssid.setText(s == null || s.contains("unknown ssid") || s.equals("0x") ? "nenhuma" : s);
            int r = wi != null ? wi.getRssi() : -127;
            Data.Grade g = gradeRssi(r);
            rssi.setText(g.g == Ui.NA ? "sem sinal" : r + " dBm");
            sig.set(g.g == Ui.NA ? 0 : Math.max(0, Math.min(1, (r + 95) / 60f)), Ui.gradeColor(g.g));
            Ui.setGrade(grade, g.g, g.why);
            speed.setText(wi != null && wi.getLinkSpeed() > 0 ? wi.getLinkSpeed() + " Mbps" : "indisponível");
        } catch (Throwable t) {
            ssid.setText("indisponível");
        }
        long rx = TrafficStats.getTotalRxBytes(), tx = TrafficStats.getTotalTxBytes();
        rxTot.setText(rx < 0 ? "indisponível" : Ui.bytes(rx));
        txTot.setText(tx < 0 ? "indisponível" : Ui.bytes(tx));
        long now = SystemClock.elapsedRealtime();
        if (lastRx >= 0 && rx >= 0 && now > lastT) {
            double sec = (now - lastT) / 1000.0;
            rxNow.setText(Ui.bytes((long) ((rx - lastRx) / sec)) + "/s");
            txNow.setText(Ui.bytes((long) ((tx - lastTx) / sec)) + "/s");
        }
        lastRx = rx; lastTx = tx; lastT = now;
        long[] w = wlan0();
        wlanTot.setText(w == null ? "indisponível" : "↓ " + Ui.bytes(w[0]) + "  ↑ " + Ui.bytes(w[1]));
        if (ticks++ % 10 == 9) slow();
    }

    /** {rx, tx} bytes da wlan0 em /proc/net/dev */
    static long[] wlan0() {
        List<String> ls = Sys.lines("/proc/net/dev");
        if (ls == null) return null;
        for (String l : ls) {
            int c = l.indexOf(':');
            if (c < 0 || !l.substring(0, c).trim().equals("wlan0")) continue;
            String[] p = l.substring(c + 1).trim().split("\\s+");
            try { return new long[]{Long.parseLong(p[0]), Long.parseLong(p[8])}; } catch (Throwable t) { return null; }
        }
        return null;
    }

    @Override void report(StringBuilder sb) {
        h(sb, "Rede");
        for (String[] r : info(a)) kv(sb, r[0], r[1]);
        kv(sb, "Placa", wifiChip());
        kv(sb, "Tráfego total", "↓ " + Ui.bytes(TrafficStats.getTotalRxBytes()) + "  ↑ " + Ui.bytes(TrafficStats.getTotalTxBytes()));
        long[] w = wlan0();
        kv(sb, "wlan0", w == null ? "indisponível" : "↓ " + Ui.bytes(w[0]) + "  ↑ " + Ui.bytes(w[1]));
    }
}
