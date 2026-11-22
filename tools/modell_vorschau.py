#!/usr/bin/env python3
"""Vorschau der Blockmodelle ohne Minecraft: rendert models/block/<name>.json mit Textur orthografisch
(Tiefenpuffer, Flächenhelligkeit wie im Spiel, leuchtende Elemente voll hell; „Nacht“ dunkelt den Rest ab).

    python3 tools/modell_vorschau.py market_counter -o bild.png [--yaw 35 --pitch 25 --px 12 --nacht]
    python3 tools/modell_vorschau.py --blatt DIR          # Übersicht aller Modelle aus mehreren Blickwinkeln
"""
import argparse
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw

HIER = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HIER, "..", "src", "main", "resources", "assets")
NAMEN = ["market_counter", "stall_sign", "buyer_station", "job_board", "lottery_booth", "advent_calendar", "hidden_gift"]


def rot(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


CORNERS = {
    "north": lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]), (a[0], a[1], a[2]), (b[0], a[1], a[2])],
    "south": lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], b[2])],
    "east": lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]), (b[0], a[1], a[2]), (b[0], a[1], b[2])],
    "west": lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]), (a[0], a[1], b[2]), (a[0], a[1], a[2])],
    "up": lambda a, b: [(a[0], b[1], a[2]), (b[0], b[1], a[2]), (b[0], b[1], b[2]), (a[0], b[1], b[2])],
    "down": lambda a, b: [(a[0], a[1], b[2]), (b[0], a[1], b[2]), (b[0], a[1], a[2]), (a[0], a[1], a[2])],
}
NORMAL = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def tex_path(ref):
    ns, p = ref.split(":", 1)
    return os.path.join(ASSETS, ns, "textures", p + ".png")


def quads(name, ox=0.0, oy=0.0, oz=0.0, facing_y=0):
    """Alle Flächen eines Modells: (Ecken 4×3 in Welt-Pixeln, UV 4×2 in Texeln, Bild, Helligkeit, leuchtet)."""
    m = json.load(open(os.path.join(ASSETS, "twogg_markt", "models", "block", name + ".json"), encoding="utf-8"))
    img = np.asarray(Image.open(tex_path(m["textures"]["0"])).convert("RGB"), dtype=np.float64)
    tw = img.shape[1]
    sc = tw / 16.0
    by = rot("y", -facing_y)
    out = []
    for e in m["elements"]:
        a, b = e["from"], e["to"]
        r = e.get("rotation")
        R = rot(r["axis"], r["angle"]) if r else np.eye(3)
        o = np.array(r["origin"], float) if r else np.zeros(3)
        for f, fj in e["faces"].items():
            c = np.array(CORNERS[f](a, b), float)
            c = (R @ (c - o).T).T + o
            c = (by @ (c - 8).T).T + 8 + np.array([ox, oy, oz])
            n = by @ (R @ np.array(NORMAL[f], float))
            u0, v0, u1, v1 = [v * sc for v in fj["uv"]]
            uv = np.array([(u0, v0), (u1, v0), (u1, v1), (u0, v1)])
            emit = e.get("light_emission", 0) > 0
            bright = 1.0 if emit else 0.6 * n[0] ** 2 + 0.8 * n[2] ** 2 + (1.0 if n[1] > 0 else 0.5) * n[1] ** 2
            out.append((c, uv, img, bright, emit, n))
    return out


def render(qs, yaw=35, pitch=25, px=12, nacht=False, bg=(150, 170, 200), size=None, center=None):
    R = rot("x", -pitch) @ rot("y", yaw)
    allc = np.concatenate([q[0] for q in qs])
    ctr = np.array(center) if center is not None else (allc.min(0) + allc.max(0)) / 2
    proj = [((R @ (q[0] - ctr).T).T) for q in qs]
    allp = np.concatenate(proj)
    if size is None:
        w = int((allp[:, 0].max() - allp[:, 0].min()) * px) + 40
        h = int((allp[:, 1].max() - allp[:, 1].min()) * px) + 40
    else:
        w, h = size
    col = np.zeros((h, w, 3)) + np.array(bg, float)
    if nacht:
        col = col * 0.0 + np.array((14, 18, 34), float)
    dep = np.full((h, w), np.inf)
    for q, p in zip(qs, proj):
        c, uv, img, bright, emit, n = q
        nv = R @ n
        if nv[2] > -1e-6:
            pass
        if nv[2] >= 0:  # Rückseite
            continue
        sx = -p[:, 0] * px + w / 2
        sy = -p[:, 1] * px + h / 2
        sz = p[:, 2]
        for tri in ((0, 1, 2), (0, 2, 3)):
            X, Y, Z, UV = sx[list(tri)], sy[list(tri)], sz[list(tri)], uv[list(tri)]
            x0, x1 = int(max(0, math.floor(X.min()))), int(min(w - 1, math.ceil(X.max())))
            y0, y1 = int(max(0, math.floor(Y.min()))), int(min(h - 1, math.ceil(Y.max())))
            if x1 < x0 or y1 < y0:
                continue
            gy, gx = np.mgrid[y0:y1 + 1, x0:x1 + 1] + 0.5
            d = (Y[1] - Y[2]) * (X[0] - X[2]) + (X[2] - X[1]) * (Y[0] - Y[2])
            if abs(d) < 1e-9:
                continue
            l0 = ((Y[1] - Y[2]) * (gx - X[2]) + (X[2] - X[1]) * (gy - Y[2])) / d
            l1 = ((Y[2] - Y[0]) * (gx - X[2]) + (X[0] - X[2]) * (gy - Y[2])) / d
            l2 = 1 - l0 - l1
            ins = (l0 >= -1e-6) & (l1 >= -1e-6) & (l2 >= -1e-6)
            if not ins.any():
                continue
            z = l0 * Z[0] + l1 * Z[1] + l2 * Z[2]
            u = l0 * UV[0, 0] + l1 * UV[1, 0] + l2 * UV[2, 0]
            v = l0 * UV[0, 1] + l1 * UV[1, 1] + l2 * UV[2, 1]
            sub = dep[y0:y1 + 1, x0:x1 + 1]
            ok = ins & (z < sub - 1e-4)
            if not ok.any():
                continue
            ui = np.clip(np.floor(u).astype(int), 0, img.shape[1] - 1)
            vi = np.clip(np.floor(v).astype(int), 0, img.shape[0] - 1)
            texel = img[vi, ui] * bright
            if nacht and not emit:
                texel = texel * np.array((0.22, 0.24, 0.36))
            cs = col[y0:y1 + 1, x0:x1 + 1]
            cs[ok] = texel[ok]
            sub[ok] = z[ok]
    return Image.fromarray(np.clip(col, 0, 255).astype(np.uint8), "RGB")


def blatt(namen, outdir, px=10):
    os.makedirs(outdir, exist_ok=True)
    views = [(35, 25, False), (0, 8, False), (-140, 25, False), (150, 30, True)]
    tiles = []
    for n in namen:
        qs = quads(n)
        row = [render(qs, y, p, px, nacht, size=(420, 420), center=(8, 12, 8)) for y, p, nacht in views]
        tiles.append((n, row))
        for k, im in enumerate(row):
            im.save(os.path.join(outdir, f"{n}_{k}.png"))
    W = 420 * len(views)
    sheet = Image.new("RGB", (W, 440 * len(tiles)), (40, 40, 40))
    d = ImageDraw.Draw(sheet)
    for i, (n, row) in enumerate(tiles):
        for k, im in enumerate(row):
            sheet.paste(im, (k * 420, i * 440 + 20))
        d.text((6, i * 440 + 4), n, fill=(255, 255, 255))
    sheet.save(os.path.join(outdir, "uebersicht.png"))
    print("Vorschau:", os.path.join(outdir, "uebersicht.png"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("name", nargs="?")
    ap.add_argument("-o", "--out")
    ap.add_argument("--yaw", type=float, default=35)
    ap.add_argument("--pitch", type=float, default=25)
    ap.add_argument("--px", type=float, default=12)
    ap.add_argument("--nacht", action="store_true")
    ap.add_argument("--blatt")
    a = ap.parse_args()
    if a.blatt:
        blatt(NAMEN if not a.name else [a.name], a.blatt)
        return
    render(quads(a.name), a.yaw, a.pitch, a.px, a.nacht).save(a.out)


if __name__ == "__main__":
    main()
