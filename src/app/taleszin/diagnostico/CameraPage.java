package app.taleszin.diagnostico;

import android.hardware.Camera;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
final class CameraPage extends Page implements SurfaceHolder.Callback {
    CameraPage(MainActivity a) { super(a, "Câmera", Charts.I_CAM); }

    private static final Object LOCK = new Object();
    private static volatile List<List<String[]>> cache;

    private LinearLayout infoBox;
    private TextView status, testBtn, switchBtn;
    private FrameLayout previewBox;
    private SurfaceView surface;
    private Camera cam;
    private int camId;
    private boolean busy, wantPreview;

    @Override void build(LinearLayout body) {
        Ui.header(body, "Câmera", "As câmeras do tablet e o que elas conseguem fazer. Este modelo só tem a câmera da frente.");
        infoBox = new LinearLayout(a);
        infoBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(infoBox);

        LinearLayout t = Ui.card(body, "Testar câmera");
        status = Ui.note(t, "Toque em “Testar câmera” para ver a imagem ao vivo. A câmera só fica ligada enquanto esta aba estiver aberta.");
        previewBox = new FrameLayout(a);
        previewBox.setBackgroundDrawable(Ui.round(Ui.CARD2, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(240));
        lp.topMargin = Ui.dp(8);
        t.addView(previewBox, lp);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        t.addView(row);
        testBtn = Ui.button(row, "Testar câmera", new View.OnClickListener() {
            public void onClick(View v) { toggle(); }
        });
        switchBtn = Ui.button(row, "Trocar câmera", new View.OnClickListener() {
            public void onClick(View v) {
                int n = count();
                if (n < 2) return;
                stopPreview();
                camId = (camId + 1) % n;
                toggle();
            }
        });
        ((LinearLayout.LayoutParams) switchBtn.getLayoutParams()).leftMargin = Ui.dp(10);
        switchBtn.setVisibility(count() > 1 ? View.VISIBLE : View.GONE);

        LinearLayout e = Ui.card(body, "Entendendo os termos");
        Ui.note(e, "Resolução de foto: tamanho da imagem salva (MP = milhões de pontos). Preview: a imagem ao vivo na tela, sempre menor. "
                + "FPS: quadros por segundo; 30 é vídeo fluido, 15 parece “travado”. Foco fixo significa que a lente não ajusta: objetos muito perto ficam borrados. "
                + "Câmeras de tablets de 2014 costumam ter 0,3 MP (VGA) na frente: servem para videochamada, não para fotos boas.");
    }

    static int count() {
        try { return Camera.getNumberOfCameras(); } catch (Throwable t) { return 0; }
    }

    @Override void onShow() {
        if (infoBox == null) return;
        if (cache != null) { showInfo(cache); return; }
        busy = true;
        testBtn.setEnabled(false);
        infoBox.removeAllViews();
        Ui.note(infoBox, "Lendo as câmeras…");
        Sys.bg(new Runnable() {
            public void run() {
                final List<List<String[]>> r = collect();
                Sys.ui(new Runnable() {
                    public void run() {
                        busy = false;
                        if (testBtn != null) testBtn.setEnabled(true);
                        if (visible) showInfo(r);
                    }
                });
            }
        });
    }

    @Override void onHide() {
        stopPreview();
    }

    private void showInfo(List<List<String[]>> info) {
        infoBox.removeAllViews();
        if (info.isEmpty()) {
            LinearLayout k = Ui.card(infoBox, "Câmeras");
            Ui.note(k, "Nenhuma câmera encontrada.");
            return;
        }
        for (List<String[]> cam : info) {
            LinearLayout k = Ui.card(infoBox, cam.get(0)[1]);
            for (int i = 1; i < cam.size(); i++) Ui.kv(k, cam.get(i)[0], cam.get(i)[1]);
        }
    }

    /** Abre cada câmera rapidinho, lê os parâmetros e solta. Cada câmera = lista de {rótulo, valor}; o 1º é o título. */
    static List<List<String[]>> collect() {
        if (cache != null) return cache;
        List<List<String[]>> out = new ArrayList<List<String[]>>();
        int n = count();
        for (int i = 0; i < n; i++) {
            List<String[]> r = new ArrayList<String[]>();
            Camera.CameraInfo ci = new Camera.CameraInfo();
            String face = "?";
            try {
                Camera.getCameraInfo(i, ci);
                face = ci.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "frontal" : "traseira";
            } catch (Throwable ignored) { }
            r.add(new String[]{"", "Câmera " + i + " (" + face + ")"});
            r.add(new String[]{"Posição", face.equals("frontal") ? "frente (lado da tela)" : face.equals("traseira") ? "trás" : "indisponível"});
            r.add(new String[]{"Rotação do sensor", ci.orientation + "°"});
            Camera c = null;
            synchronized (LOCK) {
                try {
                    c = Camera.open(i);
                    Camera.Parameters p = c.getParameters();
                    List<Camera.Size> pic = p.getSupportedPictureSizes();
                    r.add(new String[]{"Maior foto", pic == null || pic.isEmpty() ? "indisponível" : mp(largest(pic))});
                    r.add(new String[]{"Resoluções de foto", sizes(pic)});
                    r.add(new String[]{"Resoluções de preview", sizes(p.getSupportedPreviewSizes())});
                    List<Camera.Size> vid = p.getSupportedVideoSizes();
                    if (vid != null) r.add(new String[]{"Resoluções de vídeo", sizes(vid)});
                    r.add(new String[]{"Foco", focus(p.getSupportedFocusModes())});
                    List<String> fl = p.getSupportedFlashModes();
                    r.add(new String[]{"Flash", fl == null || fl.size() <= 1 ? "não tem" : join(fl)});
                    r.add(new String[]{"Faixa de FPS", fps(p.getSupportedPreviewFpsRange())});
                    r.add(new String[]{"Zoom", p.isZoomSupported() ? "sim (até " + (p.getMaxZoom() + 1) + " passos)" : "não"});
                    r.add(new String[]{"Exposição", p.getMinExposureCompensation() + " a +" + p.getMaxExposureCompensation()});
                    r.add(new String[]{"Ângulo de visão", Math.round(p.getHorizontalViewAngle()) + "° × " + Math.round(p.getVerticalViewAngle()) + "°"});
                } catch (Throwable t) {
                    r.add(new String[]{"Detalhes", "não deu para abrir (outro app usando a câmera?)"});
                } finally {
                    if (c != null) try { c.release(); } catch (Throwable ignored) { }
                }
            }
            out.add(r);
        }
        cache = out;
        return out;
    }

    static Camera.Size largest(List<Camera.Size> l) {
        Camera.Size b = l.get(0);
        for (Camera.Size s : l) if ((long) s.width * s.height > (long) b.width * b.height) b = s;
        return b;
    }

    static String mp(Camera.Size s) {
        return s.width + "×" + s.height + " (" + Ui.f1(s.width * (double) s.height / 1e6) + " MP)";
    }

    static String sizes(List<Camera.Size> l) {
        if (l == null || l.isEmpty()) return "indisponível";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size() && i < 12; i++) {
            if (i > 0) sb.append(", ");
            sb.append(l.get(i).width).append('×').append(l.get(i).height);
        }
        if (l.size() > 12) sb.append("…");
        return sb.toString();
    }

    static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) { if (sb.length() > 0) sb.append(", "); sb.append(s); }
        return sb.toString();
    }

    static String focus(List<String> l) {
        if (l == null || l.isEmpty()) return "indisponível";
        if (l.size() == 1 && ("fixed".equals(l.get(0)) || "infinity".equals(l.get(0)))) return "fixo (" + l.get(0) + ")";
        return join(l);
    }

    static String fps(List<int[]> l) {
        if (l == null || l.isEmpty()) return "indisponível";
        StringBuilder sb = new StringBuilder();
        for (int[] r : l) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(r[0] / 1000).append('–').append(r[1] / 1000);
        }
        return sb.append(" fps").toString();
    }

    // ------------------------------------------------------------------ preview
    private void toggle() {
        if (busy) return;
        if (cam != null || wantPreview) { stopPreview(); return; }
        if (count() == 0) { status.setText("Nenhuma câmera para testar."); return; }
        wantPreview = true;
        surface = new SurfaceView(a);
        surface.getHolder().addCallback(this);
        surface.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        previewBox.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        testBtn.setText("Parar");
        status.setText("Abrindo a câmera " + camId + "…");
    }

    @Override public void surfaceCreated(SurfaceHolder h) {
        if (!wantPreview || !visible) return;
        synchronized (LOCK) {
            try {
                cam = Camera.open(camId);
                Camera.Parameters p = cam.getParameters();
                Camera.Size best = null;
                List<Camera.Size> ps = p.getSupportedPreviewSizes();
                if (ps != null) for (Camera.Size s : ps) {
                    // a menor resolução que ainda fica nítida: poupa CPU e memória
                    if (s.width >= 320 && (best == null || s.width * s.height < best.width * best.height)) best = s;
                }
                if (best != null) { p.setPreviewSize(best.width, best.height); cam.setParameters(p); }
                cam.setDisplayOrientation(displayOrientation(camId));
                cam.setPreviewDisplay(h);
                cam.startPreview();
                Camera.Size cur = cam.getParameters().getPreviewSize();
                status.setText("Câmera " + camId + " ao vivo" + (cur != null ? " em " + cur.width + "×" + cur.height : "") + ". Toque em “Parar” para soltar.");
            } catch (Throwable t) {
                status.setText("Não deu para abrir a câmera: " + t.getMessage() + ". Outro app pode estar usando.");
                releaseCam();
            }
        }
    }

    private int displayOrientation(int id) {
        int deg = 0;
        try {
            switch (a.getWindowManager().getDefaultDisplay().getRotation()) {
                case Surface.ROTATION_90: deg = 90; break;
                case Surface.ROTATION_180: deg = 180; break;
                case Surface.ROTATION_270: deg = 270; break;
                default: deg = 0;
            }
            Camera.CameraInfo ci = new Camera.CameraInfo();
            Camera.getCameraInfo(id, ci);
            if (ci.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return (360 - (ci.orientation + deg) % 360) % 360;
            return (ci.orientation - deg + 360) % 360;
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) { }

    @Override public void surfaceDestroyed(SurfaceHolder h) { releaseCam(); }

    private void releaseCam() {
        synchronized (LOCK) {
            if (cam != null) {
                try { cam.stopPreview(); } catch (Throwable ignored) { }
                try { cam.release(); } catch (Throwable ignored) { }
                cam = null;
            }
        }
    }

    private void stopPreview() {
        wantPreview = false;
        releaseCam();
        if (surface != null && previewBox != null) {
            previewBox.removeView(surface);
            surface = null;
        }
        if (testBtn != null) testBtn.setText("Testar câmera");
        if (status != null) status.setText("Câmera desligada.");
    }

    @Override void report(StringBuilder sb) {
        h(sb, "Câmera");
        if (cam != null || wantPreview) { kv(sb, "Câmera", "em uso pelo teste; dados da última leitura"); }
        List<List<String[]>> info = cache != null || cam != null || wantPreview ? cache : collect();
        if (info == null) { kv(sb, "Câmeras", "não lidas"); return; }
        if (info.isEmpty()) kv(sb, "Câmeras", "nenhuma");
        for (List<String[]> c : info) {
            sb.append(c.get(0)[1]).append('\n');
            for (int i = 1; i < c.size(); i++) kv(sb, "  " + c.get(i)[0], c.get(i)[1]);
        }
    }
}
