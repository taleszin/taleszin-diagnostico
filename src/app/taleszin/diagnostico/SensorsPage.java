package app.taleszin.diagnostico;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

final class SensorsPage extends Page implements SensorEventListener {
    SensorsPage(MainActivity a) { super(a, "Sensores", Charts.I_SEN); }

    private SensorManager sm;
    private Sensor acc;
    private TextView ax, ay, az, tilt;
    private Charts.Meter mx, my, mz;
    private long lastUi;
    private boolean registered;

    @Override void build(LinearLayout body) {
        Ui.header(body, "Sensores", "Peças que medem o mundo em volta: inclinação, luz, proximidade. Tablets simples costumam ter poucos.");
        sm = (SensorManager) a.getSystemService(Context.SENSOR_SERVICE);
        acc = sm != null ? sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;

        if (acc != null) {
            LinearLayout k = Ui.card(body, "Acelerômetro (ao vivo)");
            ax = Ui.kv(k, "X (esquerda ↔ direita)", "…");
            mx = meter(k);
            ay = Ui.kv(k, "Y (baixo ↔ cima)", "…");
            my = meter(k);
            az = Ui.kv(k, "Z (trás ↔ frente)", "…");
            mz = meter(k);
            tilt = Ui.kv(k, "Posição", "…");
            Ui.note(k, "Mede a força da gravidade em cada eixo, em m/s². Parado na mesa, o Z fica perto de 9,8 (a gravidade) e X e Y perto de 0. É ele que gira a tela quando você vira o tablet.");
        }

        LinearLayout l = Ui.card(body, "Sensores encontrados");
        List<Sensor> list = sm != null ? sm.getSensorList(Sensor.TYPE_ALL) : null;
        if (list == null || list.isEmpty()) {
            Ui.note(l, "Nenhum sensor informado pelo sistema.");
        } else {
            for (int i = 0; i < list.size(); i++) {
                Sensor s = list.get(i);
                if (i > 0) Ui.divider(l);
                TextView n = Ui.text(a, s.getName(), 15, Ui.TEXT, Ui.BOLD);
                l.addView(n);
                Ui.kv(l, "Tipo", typeName(s.getType()));
                Ui.kv(l, "Fabricante", Sys.or(s.getVendor()));
                Ui.kv(l, "Resolução", trim(s.getResolution()));
                Ui.kv(l, "Alcance máximo", trim(s.getMaximumRange()));
                Ui.kv(l, "Consumo", trim(s.getPower()) + " mA");
                Ui.note(l, typeHelp(s.getType()));
            }
        }
        Ui.note(body, "Consumo em mA é o quanto o sensor gasta enquanto está ligado; só fica ligado quando algum app pede.");
    }

    private Charts.Meter meter(LinearLayout k) {
        Charts.Meter m = new Charts.Meter(a);
        k.addView(m, new LinearLayout.LayoutParams(-1, -2));
        return m;
    }

    static String trim(float f) {
        if (f == (long) f) return String.valueOf((long) f);
        return String.format(Ui.BR, "%.4g", f);
    }

    static String typeName(int t) {
        switch (t) {
            case Sensor.TYPE_ACCELEROMETER: return "acelerômetro";
            case Sensor.TYPE_MAGNETIC_FIELD: return "bússola (campo magnético)";
            case Sensor.TYPE_GYROSCOPE: return "giroscópio";
            case Sensor.TYPE_LIGHT: return "luz ambiente";
            case Sensor.TYPE_PROXIMITY: return "proximidade";
            case Sensor.TYPE_PRESSURE: return "pressão (barômetro)";
            case Sensor.TYPE_GRAVITY: return "gravidade (virtual)";
            case Sensor.TYPE_LINEAR_ACCELERATION: return "aceleração linear (virtual)";
            case Sensor.TYPE_ROTATION_VECTOR: return "rotação (virtual)";
            case Sensor.TYPE_AMBIENT_TEMPERATURE: return "temperatura";
            case Sensor.TYPE_RELATIVE_HUMIDITY: return "umidade";
            case 3: return "orientação (antigo)";
            default: return "tipo " + t;
        }
    }

    static String typeHelp(int t) {
        switch (t) {
            case Sensor.TYPE_ACCELEROMETER: return "Sente inclinação e movimento; gira a tela automaticamente.";
            case Sensor.TYPE_MAGNETIC_FIELD: return "Sente o campo magnético da Terra, como uma bússola.";
            case Sensor.TYPE_GYROSCOPE: return "Sente a rotação com precisão (jogos, realidade virtual).";
            case Sensor.TYPE_LIGHT: return "Mede a luz do ambiente para o brilho automático.";
            case Sensor.TYPE_PROXIMITY: return "Percebe algo perto da tela (no celular, apaga a tela na ligação).";
            case Sensor.TYPE_GRAVITY: case Sensor.TYPE_LINEAR_ACCELERATION: case Sensor.TYPE_ROTATION_VECTOR: case 3:
                return "Sensor “virtual”: calculado pelo Android a partir dos sensores físicos.";
            default: return "Sensor extra informado pelo fabricante.";
        }
    }

    @Override void onShow() {
        if (sm != null && acc != null && !registered) {
            try { registered = sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI); } catch (Throwable ignored) { }
        }
    }

    @Override void onHide() {
        if (sm != null && registered) {
            try { sm.unregisterListener(this); } catch (Throwable ignored) { }
            registered = false;
        }
    }

    @Override public void onSensorChanged(SensorEvent e) {
        long now = SystemClock.uptimeMillis();
        if (now - lastUi < 200 || ax == null || !visible) return;
        lastUi = now;
        float x = e.values[0], y = e.values[1], z = e.values[2];
        ax.setText(Ui.f2(x));
        ay.setText(Ui.f2(y));
        az.setText(Ui.f2(z));
        mx.set(Math.abs(x) / 9.81f, Ui.ACCENT);
        my.set(Math.abs(y) / 9.81f, Ui.BLUE);
        mz.set(Math.abs(z) / 9.81f, Ui.GREEN);
        String pos;
        if (Math.abs(z) > 8.5f) pos = z > 0 ? "deitado com a tela para cima" : "de cabeça para baixo";
        else if (Math.abs(x) > Math.abs(y)) pos = "em pé, de lado (paisagem)";
        else pos = "em pé (retrato)";
        tilt.setText(pos);
    }

    @Override public void onAccuracyChanged(Sensor s, int acc) { }

    @Override void report(StringBuilder sb) {
        h(sb, "Sensores");
        try {
            SensorManager m = (SensorManager) a.getSystemService(Context.SENSOR_SERVICE);
            for (Sensor s : m.getSensorList(Sensor.TYPE_ALL))
                kv(sb, s.getName(), typeName(s.getType()) + ", " + s.getVendor() + ", resolução " + trim(s.getResolution()) + ", " + trim(s.getPower()) + " mA");
        } catch (Throwable t) {
            kv(sb, "Sensores", "indisponível");
        }
    }
}
