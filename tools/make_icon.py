#!/usr/bin/env python3
"""Gera o ícone (chip estilizado com acento laranja) em todas as densidades."""
import os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "res")
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ORANGE = (255, 122, 26, 255)
BG = (26, 32, 39, 255)
DARK = (16, 20, 24, 255)


def draw(size):
    s = 8  # supersampling
    W = size * s
    im = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    pad = W * 0.04
    d.rounded_rectangle([pad, pad, W - pad, W - pad], radius=W * 0.22, fill=BG)
    # pinos do chip
    body0, body1 = W * 0.30, W * 0.70
    pin_len, pin_w = W * 0.10, W * 0.045
    for i in range(4):
        c = body0 + (body1 - body0) * (i + 0.5) / 4
        for (x0, y0, x1, y1) in (
            (c - pin_w / 2, body0 - pin_len, c + pin_w / 2, body0),
            (c - pin_w / 2, body1, c + pin_w / 2, body1 + pin_len),
            (body0 - pin_len, c - pin_w / 2, body0, c + pin_w / 2),
            (body1, c - pin_w / 2, body1 + pin_len, c + pin_w / 2),
        ):
            d.rounded_rectangle([x0, y0, x1, y1], radius=pin_w / 2, fill=ORANGE)
    d.rounded_rectangle([body0, body0, body1, body1], radius=W * 0.06, fill=ORANGE)
    # núcleo + "pulso" de diagnóstico
    i0, i1 = W * 0.37, W * 0.63
    d.rounded_rectangle([i0, i0, i1, i1], radius=W * 0.035, fill=DARK)
    pts = [(i0 + W * 0.02, W * 0.5), (W * 0.45, W * 0.5), (W * 0.48, W * 0.42),
           (W * 0.52, W * 0.58), (W * 0.55, W * 0.5), (i1 - W * 0.02, W * 0.5)]
    d.line(pts, fill=ORANGE, width=int(W * 0.025), joint="curve")
    return im.resize((size, size), Image.LANCZOS)


for dens, px in SIZES.items():
    out = os.path.join(ROOT, "mipmap-" + dens)
    os.makedirs(out, exist_ok=True)
    draw(px).save(os.path.join(out, "ic_launcher.png"))
print("ok")
