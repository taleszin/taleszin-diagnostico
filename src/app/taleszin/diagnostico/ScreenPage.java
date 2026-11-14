package app.taleszin.diagnostico;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class ScreenPage extends Page {
    ScreenPage(MainActivity a) { super(a, "Tela e GPU", Charts.I_SCR); }

    static final String BL = "/sys/class/backlight/rk28_bl/";
    private static volatile String[] gl; // vendor, renderer, version, glsl, extensões, egl

    private TextView briTv, briSet, glVendor, glRenderer, glVersion, glsl, egl, extTv, maxTouch, nowTouch;
    private Charts.Meter briMeter;
    private Charts.Touch touch;

    @Override void build(LinearLayout body) {
        Ui.header(body, "Tela e GPU", "A tela, o brilho e o chip gráfico (GPU), que desenha tudo o que você vê.");

        LinearLayout s = Ui.card(body, "Tela");
        for (String[] r : screenRows(a)) Ui.kv(s, r[0], r[1]);
        Ui.note(s, "Densidade (dpi) é quantos pontos cabem em uma polegada: 160 dpi é o padrão “mdpi”, por isso os textos parecem grandes. A taxa de atualização é quantas vezes por segundo a imagem é redesenhada; 60 Hz é o normal. O tamanho é estimado pelo que o sistema informa e pode errar um pouco.");

        LinearLayout b = Ui.card(body, "Brilho (ao vivo)");
        briTv = Ui.kv(b, "Nível do backlight", "…");
        briMeter = new Charts.Meter(a);
        b.addView(briMeter, new LinearLayout.LayoutParams(-1, -2));
        briSet = Ui.kv(b, "Configuração do Android", "…");
        Ui.note(b, "O backlight é a luz atrás da tela e é o que mais gasta bateria com o tablet ligado. Baixar o brilho pela metade pode render bastante tempo a mais.");

        LinearLayout g = Ui.card(body, "GPU (chip gráfico)");
        glVendor = Ui.kv(g, "Fabricante (GL_VENDOR)", "lendo…");
        glRenderer = Ui.kv(g, "Modelo (GL_RENDERER)", "lendo…");
        glVersion = Ui.kv(g, "Versão OpenGL ES", "lendo…");
        glsl = Ui.kv(g, "Linguagem de shaders", "lendo…");
        egl = Ui.kv(g, "EGL", "lendo…");
        Ui.note(g, "A Mali-400 é uma GPU de 2008, com OpenGL ES 2.0: roda interfaces e jogos simples, mas não jogos 3D modernos (que pedem ES 3.0). Extensões são recursos extras que o driver oferece:");
        extTv = Ui.text(a, "…", 11, Ui.MUTED, Ui.SANS);
        g.addView(extTv);

        LinearLayout t = Ui.card(body, "Teste de toque (multitoque)");
        Ui.note(t, "Encoste vários dedos ao mesmo tempo. Cada dedo vira um círculo colorido: assim dá para ver se o touch responde em toda a tela e quantos toques simultâneos ele entende.");
        touch = new Charts.Touch(a);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(8);
        t.addView(touch, lp);
        nowTouch = Ui.kv(t, "Dedos agora", "0");
        maxTouch = Ui.kv(t, "Máximo ao mesmo tempo", "0");
        Ui.kv(t, "Controlador de toque", touchName());
        touch.onChange = new Runnable() {
            public void run() {
                nowTouch.setText(String.valueOf(touch.count()));
                maxTouch.setText(touch.max() + (touch.max() >= 5 ? " (ótimo)" : touch.max() >= 2 ? " (multitoque ok)" : ""));
            }
        };
        Ui.button(t, "Zerar", new View.OnClickListener() {
            public void onClick(View v) { touch.reset(); }
        });
        tick();
    }

    static List<String[]> screenRows(Context c) {
        List<String[]> out = new ArrayList<String[]>();
        try {
            Display d = ((WindowManager) c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            DisplayMetrics m = new DisplayMetrics();
            d.getRealMetrics(m);
            int w = Math.max(m.widthPixels, m.heightPixels), h = Math.min(m.widthPixels, m.heightPixels);
            out.add(new String[]{"Resolução", w + " × " + h + " pixels"});
            out.add(new String[]{"Densidade", m.densityDpi + " dpi (" + bucket(m.densityDpi) + ")"});
            out.add(new String[]{"DPI físico informado", Math.round(m.xdpi) + " × " + Math.round(m.ydpi)});
            double wi = m.widthPixels / m.xdpi, hi = m.heightPixels / m.ydpi, diag = Math.sqrt(wi * wi + hi * hi);
            out.add(new String[]{"Tamanho estimado", Ui.f1(diag) + "\" (" + Ui.f1(diag * 2.54) + " cm na diagonal)"});
            out.add(new String[]{"Taxa de atualização", Math.round(d.getRefreshRate()) + " Hz"});
            out.add(new String[]{"Área útil em dp", Math.round(m.widthPixels / m.density) + " × " + Math.round(m.heightPixels / m.density) + " dp"});
        } catch (Throwable t) {
            out.add(new String[]{"Tela", "indisponível"});
        }
        return out;
    }

    static String bucket(int dpi) {
        if (dpi <= 120) return "ldpi";
        if (dpi <= 160) return "mdpi";
        if (dpi <= 240) return "hdpi";
        if (dpi <= 320) return "xhdpi";
        if (dpi <= 480) return "xxhdpi";
        return "xxxhdpi";
    }

    /** Nomes dos dispositivos de entrada que parecem touchscreen (/proc/bus/input/devices). */
    static String touchName() {
        List<String> ls = Sys.lines("/proc/bus/input/devices");
        if (ls == null) return "indisponível";
        StringBuilder sb = new StringBuilder();
        for (String l : ls) {
            if (!l.startsWith("N: Name=")) continue;
            String n = l.substring(8).replace("\"", "");
            String k = n.toLowerCase();
            if (k.contains("touch") || k.matches(".*(^|[^a-z])ts([^a-z]|$).*") || k.contains("aw5") || k.contains("gsl") || k.contains("ft5") || k.contains("goodix")) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(n);
            }
        }
        return sb.length() == 0 ? "não identificado" : sb.toString();
    }

    @Override void onShow() {
        if (gl != null) { showGl(); return; }
        Sys.bg(new Runnable() {
            public void run() {
                glInfo();
                Sys.ui(new Runnable() { public void run() { showGl(); } });
            }
        });
    }

    private void showGl() {
        if (glVendor == null || gl == null) return;
        glVendor.setText(gl[0]);
        glRenderer.setText(gl[1]);
        glVersion.setText(gl[2]);
        glsl.setText(gl[3]);
        egl.setText(gl[5]);
        String ext = gl[4];
        int n = ext == null || ext.trim().length() == 0 ? 0 : ext.trim().split("\\s+").length;
        extTv.setText(n == 0 ? "nenhuma informada" : n + " extensões: " + ext.trim().replace(' ', '\n'));
    }

    /** Cria um contexto OpenGL ES 2 fora da tela (pbuffer de 1×1 pixel) uma única vez e lê as strings do driver. */
    static synchronized String[] glInfo() {
        if (gl != null) return gl;
        String[] r = new String[]{"indisponível", "indisponível", "indisponível", "indisponível", "", "indisponível"};
        EGLDisplay d = null;
        EGLContext ctx = null;
        EGLSurface s = null;
        try {
            d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] v = new int[2];
            if (!EGL14.eglInitialize(d, v, 0, v, 1)) throw new RuntimeException("eglInitialize");
            r[5] = Sys.or(EGL14.eglQueryString(d, EGL14.EGL_VENDOR)) + " " + Sys.or(EGL14.eglQueryString(d, EGL14.EGL_VERSION));
            int[] attr = {EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE};
            EGLConfig[] cfg = new EGLConfig[1];
            int[] num = new int[1];
            if (!EGL14.eglChooseConfig(d, attr, 0, cfg, 0, 1, num, 0) || num[0] < 1) throw new RuntimeException("config");
            ctx = EGL14.eglCreateContext(d, cfg[0], EGL14.EGL_NO_CONTEXT, new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            s = EGL14.eglCreatePbufferSurface(d, cfg[0], new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
            if (ctx == null || ctx == EGL14.EGL_NO_CONTEXT || s == null || s == EGL14.EGL_NO_SURFACE) throw new RuntimeException("ctx");
            if (!EGL14.eglMakeCurrent(d, s, s, ctx)) throw new RuntimeException("makeCurrent");
            r[0] = Sys.or(GLES20.glGetString(GLES20.GL_VENDOR));
            r[1] = Sys.or(GLES20.glGetString(GLES20.GL_RENDERER));
            r[2] = Sys.or(GLES20.glGetString(GLES20.GL_VERSION));
            r[3] = Sys.or(GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION));
            String e = GLES20.glGetString(GLES20.GL_EXTENSIONS);
            r[4] = e == null ? "" : e;
        } catch (Throwable t) {
            r[1] = "não deu para criar o contexto OpenGL (" + t.getMessage() + ")";
        } finally {
            try {
                if (d != null) {
                    EGL14.eglMakeCurrent(d, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                    if (s != null && s != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(d, s);
                    if (ctx != null && ctx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(d, ctx);
                    EGL14.eglReleaseThread();
                    // sem eglTerminate: o display padrão é o mesmo que a interface do app usa
                }
            } catch (Throwable ignored) { }
        }
        gl = r;
        return r;
    }

    @Override boolean live() { return true; }

    @Override void tick() {
        if (briTv == null) return;
        long v = Sys.num(BL + "brightness"), max = Sys.num(BL + "max_brightness");
        if (v < 0) {
            v = Sys.num(BL + "actual_brightness");
        }
        if (max <= 0) max = 255;
        briTv.setText(v < 0 ? "indisponível" : v + " de " + max + (v == 0 ? " (tela apagada)" : ""));
        briMeter.set(v < 0 ? 0 : (float) v / max, Ui.YELLOW);
        briSet.setText(androidBrightness(a));
    }

    static String androidBrightness(Context c) {
        try {
            int sb = Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            int mode = Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            return sb + "/255" + (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC ? " (automático)" : " (manual)");
        } catch (Throwable t) {
            return "indisponível";
        }
    }

    @Override void report(StringBuilder sb) {
        h(sb, "Tela e GPU");
        for (String[] r : screenRows(a)) kv(sb, r[0], r[1]);
        kv(sb, "Backlight", Sys.or(Sys.read(BL + "brightness")) + " de " + Sys.or(Sys.read(BL + "max_brightness")));
        kv(sb, "Brilho no Android", androidBrightness(a));
        kv(sb, "Toque", touchName());
        String[] g = glInfo();
        kv(sb, "GL_VENDOR", g[0]);
        kv(sb, "GL_RENDERER", g[1]);
        kv(sb, "GL_VERSION", g[2]);
        kv(sb, "GLSL", g[3]);
        kv(sb, "EGL", g[5]);
        kv(sb, "Extensões", g[4].trim());
    }
}
