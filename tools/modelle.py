#!/usr/bin/env python3
"""Modelle und Texturen von TwoGG Markt (seit 0.3.0): detaillierte Vanilla-Blockmodelle im Blockbench-Stil.

Jedes Modell besteht aus Quadern („box“) mit einem Material je Fläche. Der Generator
  * malt jede sichtbare Fläche einzeln in einen Atlas je Modell (textures/block/<name>.png, 32 Texel je Block,
    also 2 Texel je Modellpixel). Gemalt wird in Modellkoordinaten: Bretterfugen, Streifen, Geschenkband und das
    Losrad laufen über Flächen- und Elementgrenzen hinweg weiter,
  * entfernt Flächen, die man nie sieht (im Inneren eines anderen Quaders oder Rücken an Rücken),
  * prüft das Ergebnis (kein Z-Fighting, UVs auf ganzen Texeln, keine doppelten Elemente, Rotationen nur in
    22,5°-Schritten, alle Koordinaten in −16…32) und bricht bei Fehlern ab,
  * schreibt models/block/<name>.json mit Item-Ansichten (display, GUI passend eingepasst), eine Partikeltextur
    <name>_partikel.png, die Item-Textur des Marktplaners und das Mod-Icon.

Alles ist deterministisch (keine Zufallszahlen ohne feste Saat). Aufruf aus dem Projektordner:

    python3 tools/modelle.py                 # alles erzeugen und prüfen
    python3 tools/modelle.py --vorschau DIR  # zusätzlich Vorschaubilder (tools/modell_vorschau.py) nach DIR
"""
import argparse
import json
import math
import os
import random
import sys

import numpy as np
from PIL import Image, ImageDraw

HIER = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HIER, "..", "src", "main", "resources", "assets", "twogg_markt")
MODELS = os.path.join(ROOT, "models", "block")
BLOCK_TEX = os.path.join(ROOT, "textures", "block")
ITEM_TEX = os.path.join(ROOT, "textures", "item")
NS = "twogg_markt"

D = 2  # Texel je Modellpixel (32 × 32 je Block)
FACES = ("north", "east", "south", "west", "up", "down")
NORMAL = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
AX = {"x": 0, "y": 1, "z": 2}

# ---------------------------------------------------------------- Farben (dunkel → hell)

PAL = {
    "fichte": [(78, 55, 32), (95, 68, 40), (110, 80, 47), (124, 91, 54), (138, 103, 62)],
    "balken": [(46, 32, 19), (58, 41, 24), (70, 50, 29), (83, 60, 35), (96, 70, 41)],
    "hell": [(150, 112, 68), (168, 127, 78), (184, 141, 88), (198, 155, 98), (212, 170, 112)],
    "kiste": [(120, 86, 50), (138, 100, 59), (154, 113, 68), (168, 126, 77), (182, 139, 87)],
    "rot": [(104, 18, 24), (136, 26, 32), (164, 36, 40), (190, 50, 50), (214, 74, 68)],
    "weiss": [(196, 188, 172), (214, 207, 192), (230, 224, 210), (242, 237, 225), (252, 249, 240)],
    "gruen": [(22, 58, 36), (30, 74, 45), (38, 90, 54), (50, 108, 64), (66, 126, 76)],
    "blau": [(30, 52, 100), (40, 68, 124), (52, 86, 148), (70, 106, 170), (96, 132, 192)],
    "gold": [(122, 80, 22), (166, 114, 30), (206, 152, 48), (236, 190, 78), (252, 228, 140)],
    "eisen": [(34, 34, 38), (46, 46, 52), (60, 60, 68), (78, 78, 88), (104, 104, 116)],
    "schnee": [(176, 194, 220), (204, 218, 238), (226, 236, 248), (240, 246, 253), (255, 255, 255)],
    "tanne": [(14, 44, 28), (22, 60, 36), (30, 78, 44), (44, 98, 54), (64, 120, 64)],
    "papier": [(200, 186, 150), (220, 208, 174), (234, 224, 196), (244, 237, 214), (252, 248, 232)],
    "kork": [(128, 88, 52), (146, 102, 62), (162, 116, 72), (178, 130, 84), (194, 146, 98)],
    "tafel": [(26, 34, 30), (32, 42, 37), (38, 50, 44), (44, 58, 50)],
    "lebkuchen": [(92, 46, 20), (112, 58, 26), (132, 72, 34), (150, 86, 42)],
    "apfel": [(110, 16, 20), (150, 24, 28), (190, 38, 36), (220, 70, 56), (246, 140, 110)],
    "sack": [(150, 128, 92), (168, 146, 106), (184, 162, 120), (198, 178, 134)],
    "glas": [(150, 176, 186), (172, 198, 206), (196, 218, 224), (224, 238, 242)],
    "stein": [(70, 70, 74), (86, 86, 90), (100, 100, 104), (116, 116, 120)],
    "borke": [(40, 28, 16), (52, 37, 21), (64, 46, 27), (76, 56, 33)],
}
CHALK = (226, 230, 222)
INK = (70, 60, 52)
LICHTFARBEN = [(255, 214, 110), (255, 120, 100), (130, 220, 255), (150, 255, 150), (255, 170, 230)]


def rgb(name, i):
    return PAL[name][i]


# ---------------------------------------------------------------- Pixelschrift 3 × 5

FONT = {
    "A": [".#.", "#.#", "###", "#.#", "#.#"], "B": ["##.", "#.#", "##.", "#.#", "##."],
    "C": [".##", "#..", "#..", "#..", ".##"], "D": ["##.", "#.#", "#.#", "#.#", "##."],
    "E": ["###", "#..", "##.", "#..", "###"], "F": ["###", "#..", "##.", "#..", "#.."],
    "G": [".##", "#..", "#.#", "#.#", ".##"], "H": ["#.#", "#.#", "###", "#.#", "#.#"],
    "I": ["###", ".#.", ".#.", ".#.", "###"], "K": ["#.#", "#.#", "##.", "#.#", "#.#"],
    "L": ["#..", "#..", "#..", "#..", "###"], "M": ["#.#", "###", "###", "#.#", "#.#"],
    "N": ["##.", "#.#", "#.#", "#.#", "#.#"], "O": [".#.", "#.#", "#.#", "#.#", ".#."],
    "P": ["##.", "#.#", "##.", "#..", "#.."], "R": ["##.", "#.#", "##.", "#.#", "#.#"],
    "S": [".##", "#..", ".#.", "..#", "##."], "T": ["###", ".#.", ".#.", ".#.", ".#."],
    "U": ["#.#", "#.#", "#.#", "#.#", "###"], "Z": ["###", "..#", ".#.", "#..", "###"],
    "Ä": ["#.#", ".#.", "#.#", "###", "#.#"], "€": [".##", "###", "#..", "###", ".##"],
    "0": ["###", "#.#", "#.#", "#.#", "###"], "1": ["#", "#", "#", "#", "#"],
    "2": ["##.", "..#", ".#.", "#..", "###"], "3": ["##.", "..#", ".#.", "..#", "##."],
    "4": ["#.#", "#.#", "###", "..#", "..#"], "5": ["###", "#..", "##.", "..#", "##."],
    "6": [".##", "#..", "###", "#.#", "###"], "7": ["###", "..#", ".#.", ".#.", ".#."],
    "8": ["###", "#.#", "###", "#.#", "###"], "9": ["###", "#.#", "###", "..#", "##."],
    " ": ["..", "..", "..", "..", ".."], ".": [".", ".", ".", ".", "#"], ",": [".", ".", ".", "#", "#"],
    "-": ["..", "..", "##", "..", ".."], "+": ["...", ".#.", "###", ".#.", "..."],
}
STERN = ["..#..", ".###.", "#####", ".###.", "#...#"]


def text_width(s):
    return sum(len(FONT[c][0]) for c in s) + max(0, len(s) - 1)


def draw_glyphs(img, rows, x0, y0, color):
    h, w = img.shape[:2]
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch == "#" and 0 <= y0 + dy < h and 0 <= x0 + dx < w:
                img[y0 + dy, x0 + dx] = color


def draw_text(img, s, x0, y0, color, shadow=None):
    x = x0
    for c in s:
        g = FONT[c]
        if shadow is not None:
            draw_glyphs(img, g, x + 1, y0 + 1, shadow)
        x += len(g[0]) + 1
    x = x0
    for c in s:
        g = FONT[c]
        draw_glyphs(img, g, x, y0, color)
        x += len(g[0]) + 1


def text_centered(img, s, color, y=None, shadow=None, dx=0):
    h, w = img.shape[:2]
    tw = text_width(s)
    y0 = (h - 5) // 2 if y is None else y
    draw_text(img, s, (w - tw) // 2 + dx, y0, color, shadow)


# ---------------------------------------------------------------- Rauschen

def hsh(*vals, seed=0):
    """Deterministischer Hash beliebig vieler Ganzzahl-Arrays → Gleitkomma in [0, 1)."""
    with np.errstate(over="ignore"):
        h = np.uint64((seed * 0x9E3779B1 + 0x7F4A7C15) & 0xFFFFFFFFFFFFFFFF)
        for v in vals:
            a = np.asarray(np.floor(v), dtype=np.int64).astype(np.uint64)
            h = h ^ (a + np.uint64(0x9E3779B97F4A7C15) + (h << np.uint64(6)) + (h >> np.uint64(2)))
            h = h * np.uint64(0xBF58476D1CE4E5B9)
            h = h ^ (h >> np.uint64(31))
            h = h * np.uint64(0x94D049BB133111EB)
            h = h ^ (h >> np.uint64(29))
    return (h >> np.uint64(11)).astype(np.float64) / float(1 << 53)


def quant(pal, t):
    p = np.array(PAL[pal] if isinstance(pal, str) else pal, dtype=np.float64)
    idx = np.clip(np.floor(np.asarray(t) * len(p)), 0, len(p) - 1).astype(int)
    return p[idx]


# ---------------------------------------------------------------- Flächen-Kontext

class Flaeche:
    """Eine Fläche eines Elements, wie sie gemalt wird: Texel-Raster und dazu die Modellkoordinaten (Elementraum)."""

    def __init__(self, el, face, seed):
        self.el, self.face, self.seed = el, face, seed
        (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
        fw, fh = face_size(el, face)
        self.w, self.h = int(round(fw * D)), int(round(fh * D))
        v, u = np.mgrid[0:self.h, 0:self.w]
        self.U, self.V = u, v
        du, dv = (u + 0.5) / D, (v + 0.5) / D
        one = np.ones_like(du)
        if face == "north":
            X, Y, Z, self.au, self.av = x1 - du, y1 - dv, z0 * one, "x", "y"
        elif face == "south":
            X, Y, Z, self.au, self.av = x0 + du, y1 - dv, z1 * one, "x", "y"
        elif face == "east":
            X, Y, Z, self.au, self.av = x1 * one, y1 - dv, z1 - du, "z", "y"
        elif face == "west":
            X, Y, Z, self.au, self.av = x0 * one, y1 - dv, z0 + du, "z", "y"
        elif face == "up":
            X, Y, Z, self.au, self.av = x0 + du, y1 * one, z0 + dv, "x", "z"
        else:
            X, Y, Z, self.au, self.av = x0 + du, y0 * one, z1 - dv, "x", "z"
        self.c = {"x": X, "y": Y, "z": Z}
        self.X, self.Y, self.Z = X, Y, Z
        self.n = {"north": "z", "south": "z", "east": "x", "west": "x", "up": "y", "down": "y"}[face]
        self.size = [x1 - x0, y1 - y0, z1 - z0]

    def long_inplane(self):
        a, b = self.au, self.av
        return a if self.size[AX[a]] >= self.size[AX[b]] else b

    def long_axis(self):
        return "xyz"[int(np.argmax(self.size))]


def face_size(el, face):
    (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
    dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
    return {"north": (dx, dy), "south": (dx, dy), "east": (dz, dy), "west": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]


# ---------------------------------------------------------------- Materialien

def bevel(img, light=1.12, dark=0.78):
    h, w = img.shape[:2]
    if w < 3 or h < 3:
        return img
    out = img.copy()
    out[0, :] *= light
    out[:, 0] *= light
    out[-1, :] *= dark
    out[:, -1] *= dark
    return out


def m_holz(F, pal="fichte", achse=None, brett=4.0, fugen=True, staerke=1.0, stoss=13.0, ton=0.0):
    a = achse or F.long_inplane()
    if a == F.n and max(F.w, F.h) > 4.5 * D:  # große Fläche quer zur Faser: Bretter in der Fläche
        a = F.long_inplane()
    if a == F.n:  # Hirnholz (Balkenende) mit Jahresringen
        o = [ax for ax in "xyz" if ax != a]
        cu = (F.el["from"][AX[o[0]]] + F.el["to"][AX[o[0]]]) / 2
        cv = (F.el["from"][AX[o[1]]] + F.el["to"][AX[o[1]]]) / 2
        r = np.hypot(F.c[o[0]] - cu, F.c[o[1]] - cv)
        t = 0.55 + 0.22 * np.sin(r * 4.2) + (hsh(F.U, F.V, seed=F.seed) - 0.5) * 0.15 + ton
        return quant(pal, np.clip(t, 0, 0.999))
    s = F.c[a]
    tax = F.av if a == F.au else F.au
    t = F.c[tax]
    b = np.floor(t / brett)
    row = np.floor(t * D)
    tone = 0.42 + 0.28 * (hsh(b, seed=F.seed % 7 + 11) - 0.5) + ton
    tone = tone + (hsh(b, row, seed=13) - 0.5) * 0.42 * staerke
    tone = tone + (hsh(b, row, np.floor(s * D / 5), seed=17) - 0.5) * 0.22 * staerke
    # Astlöcher
    kn = hsh(b, np.floor(s / 5), seed=19)
    tone = np.where((kn < 0.06) & (np.abs((s % 5) - 2.5) < 0.6) & (np.abs((t % brett) - brett / 2) < 0.6), 0.05, tone)
    if fugen:
        off = hsh(b, seed=23) * stoss
        tone = np.where(((t - b * brett) * D) < 1, -1, tone)
        tone = np.where((((s + off) % stoss) * D) < 1, 0.02, tone)
    return quant(pal, np.clip(tone, 0, 0.999))


def m_balken(F, pal="balken", achse=None, ton=0.0):
    return m_holz(F, pal=pal, achse=achse or F.long_axis(), brett=99, fugen=False, staerke=1.1, ton=ton)


def m_lack(F, pal="rot", achse=None, fugen=False, brett=4.0, ton=0.0):
    return m_holz(F, pal=pal, achse=achse, brett=brett, fugen=fugen, staerke=0.45, ton=0.12 + ton)


def m_markise(F, achse="x", breite=2.0, a="rot", b="weiss", haengend=False):
    s = F.c[achse]
    k = np.floor(s / breite) % 2
    n = (hsh(F.U, F.V, seed=F.seed) - 0.5) * 0.12
    tone = 0.62 + n
    pos = (s % breite) / breite
    tone = tone - np.where(pos > 0.75, 0.18, 0) + np.where(pos < 0.25, 0.06, 0)
    if haengend:  # Volant: unten Bogenschatten
        y0, y1 = F.el["from"][1], F.el["to"][1]
        rel = (F.Y - y0) / max(1e-6, y1 - y0)
        tone = tone - np.where(rel < 0.3, 0.22, 0)
        bog = np.abs(np.sin(s / breite * math.pi))
        tone = tone - np.where((rel < 0.55) & (bog < 0.35), 0.15, 0)
    ca, cb = quant(a, np.clip(tone, 0, 0.999)), quant(b, np.clip(tone, 0, 0.999))
    return np.where(k[..., None] == 0, ca, cb)


def m_metall(F, pal="gold", ton=0.0):
    t = 0.55 + (hsh(F.U // 2, F.V, seed=F.seed + 5) - 0.5) * 0.22 + ton
    t = np.where(hsh(F.U, F.V, seed=F.seed) < 0.05, 0.95, t)
    return quant(pal, np.clip(t, 0, 0.999))


def m_schnee(F):
    t = 0.62 + (hsh(F.U, F.V, seed=F.seed) - 0.5) * 0.3
    if F.face not in ("up", "down"):
        y0, y1 = F.el["from"][1], F.el["to"][1]
        rel = (F.Y - y0) / max(1e-6, y1 - y0)
        t = t - (1 - rel) * 0.35
    elif F.face == "down":
        t = t - 0.3
    t = np.where(hsh(F.U, F.V, seed=F.seed + 3) < 0.04, 0.99, t)
    return quant("schnee", np.clip(t, 0, 0.999))


def m_tanne(F, schnee=True):
    t = 0.12 + 0.75 * hsh(F.U, F.V, seed=F.seed)
    t = np.where(((F.U + F.V) % 3) == 0, t - 0.25, t)
    t = np.where(((F.U - F.V) % 4) == 1, t + 0.15, t)
    c = quant("tanne", np.clip(t, 0, 0.999))
    if schnee and F.face == "up":
        sn = hsh(F.U, F.V, seed=F.seed + 9) < 0.28
        c = np.where(sn[..., None], np.array(PAL["schnee"][3], float), c)
    return c


def m_papier(F, pal="papier", linien=True, tinte=INK):
    t = 0.6 + (hsh(F.U, F.V, seed=F.seed) - 0.5) * 0.25
    c = quant(pal, np.clip(t, 0, 0.999))
    if linien and F.face in ("north", "south") and F.w >= 5 and F.h >= 6:
        ln = (F.V % 2 == 0) & (F.V >= 2) & (F.V <= F.h - 2) & (F.U >= 1) & (F.U <= F.w - 2)
        lang = hsh(F.V, seed=F.seed + 4) * (F.w - 2)
        ln = ln & (F.U <= 1 + lang) & (hsh(F.U // 2, F.V, seed=F.seed + 6) > 0.18)
        c = np.where(ln[..., None], np.array(tinte, float) * 0.75 + c * 0.25, c)
    return c


def m_kork(F):
    t = hsh(F.U, F.V, seed=F.seed)
    t = np.where(hsh(F.U, F.V, seed=F.seed + 1) < 0.08, 0.02, t)
    return quant("kork", np.clip(0.2 + t * 0.75, 0, 0.999))


def m_stoff(F, pal="rot", muster="punkte", band=None, bandpal="gold", musterpal="gold"):
    t = 0.55 + (hsh(F.U, F.V, seed=F.seed) - 0.5) * 0.18
    c = quant(pal, np.clip(t, 0, 0.999))
    pu, pv = F.c[F.au], F.c[F.av]
    if muster == "punkte":
        m = ((np.floor(pu * D) % 6 == 2) & (np.floor(pv * D) % 6 == 2)) | ((np.floor(pu * D) % 6 == 5) & (np.floor(pv * D) % 6 == 5))
        c = np.where(m[..., None], np.array(PAL[musterpal][3], float), c)
    elif muster == "sterne":
        a, b = np.floor(pu * D) % 7, np.floor(pv * D) % 7
        m = ((a == 3) & (np.abs(b - 3) <= 1)) | ((b == 3) & (np.abs(a - 3) <= 1))
        c = np.where(m[..., None], np.array(PAL[musterpal][4], float), c)
    if band is not None:
        cx, cz, hw = band
        inx = np.abs(F.X - cx) < hw
        inz = np.abs(F.Z - cz) < hw
        if F.face in ("north", "south"):
            m = inx
        elif F.face in ("east", "west"):
            m = inz
        else:
            m = inx | inz
        bt = 0.6 + (hsh(F.U, F.V, seed=F.seed + 2) - 0.5) * 0.12
        edge = (np.abs(F.X - cx) > hw - 0.5 / D * 1.01) & inx & ~inz | (np.abs(F.Z - cz) > hw - 0.5 / D * 1.01) & inz & ~inx
        bt = np.where(edge, bt - 0.3, bt)
        cb = quant(bandpal, np.clip(bt, 0, 0.999))
        c = np.where(m[..., None], cb, c)
    return c


def m_licht(F, farbe=(255, 214, 110)):
    c = np.ones((F.h, F.w, 3)) * np.array(farbe, float)
    if F.w >= 2 and F.h >= 2:
        edge = (F.U == 0) | (F.V == 0) | (F.U == F.w - 1) | (F.V == F.h - 1)
        c = np.where(edge[..., None], c * 0.88, np.minimum(255, c * 1.0 + 30))
    return c


def m_apfelkiste(F):
    """Kiste voller Äpfel von oben: runde Äpfel mit Glanzpunkt."""
    a, b = (np.floor(F.X * D)) % 4, (np.floor(F.Z * D)) % 4
    cell = hsh(np.floor(F.X * D / 4), np.floor(F.Z * D / 4), seed=F.seed)
    ring = ((a == 0) | (a == 3)) & ((b == 0) | (b == 3))
    t = np.where(ring, 0.05, 0.4 + 0.25 * cell)
    t = np.where((a == 1) & (b == 1), 0.85, t)
    c = quant("apfel", np.clip(t, 0, 0.999))
    gruen = cell > 0.8
    return np.where((gruen & ~ring)[..., None], quant("gruen", np.clip(t + 0.2, 0, 0.999)), c)


def m_ware(F, pal="apfel"):
    t = 0.45 + (hsh(F.U, F.V, seed=F.seed) - 0.5) * 0.2
    t = np.where((F.U == 0) & (F.V == 0), 0.95, t)
    return quant(pal, np.clip(t, 0, 0.999))


def m_lebkuchen(F):
    t = 0.5 + (hsh(F.U, F.V, seed=F.seed) - 0.5) * 0.35
    c = quant("lebkuchen", np.clip(t, 0, 0.999))
    if F.face == "up" and F.w >= 6 and F.h >= 6:
        # Zuckerguss: Wellenrand und Punkte
        wave = ((F.U == 1) | (F.U == F.w - 2) | (F.V == 1) | (F.V == F.h - 2)) & (((F.U + F.V) % 2) == 0)
        dots = (F.U % 4 == 3) & (F.V % 4 == 3) & (F.U < F.w - 2) & (F.V < F.h - 2)
        c = np.where((wave | dots)[..., None], np.array((246, 242, 236), float), c)
        rd = (F.U == F.w // 2) & (F.V == F.h // 2)
        c = np.where(rd[..., None], np.array(PAL["rot"][3], float), c)
    return c


def m_sack(F):
    t = 0.5 + np.where((F.U + F.V) % 2 == 0, 0.12, -0.12) + (hsh(F.U, F.V, seed=F.seed) - 0.5) * 0.15
    if F.face not in ("up", "down"):
        y0, y1 = F.el["from"][1], F.el["to"][1]
        t = t - (1 - (F.Y - y0) / max(1e-6, y1 - y0)) * 0.2
    return quant("sack", np.clip(t, 0, 0.999))


def m_scheite(F):
    """Holzscheite von der Stirnseite (Kisteninhalt)."""
    a, b = (np.floor(F.X * D)) % 5, (np.floor(F.Z * D)) % 5
    r = np.hypot(a - 2, b - 2)
    t = np.where(r < 1.6, 0.55 + 0.25 * (r < 0.8), 0.1)
    return quant("hell", np.clip(t, 0, 0.999)) * np.where(r[..., None] < 1.6, 1.0, 0.0) + \
        quant("borke", np.full(r.shape, 0.4)) * np.where(r[..., None] < 1.6, 0.0, 1.0)


def m_glas(F, inhalt=True):
    t = 0.45 + (hsh(F.U, F.V, seed=F.seed) - 0.5) * 0.1
    t = np.where((F.U == 1) & (F.face in ("north", "south", "east", "west")), 0.95, t)
    c = quant("glas", np.clip(t, 0, 0.999))
    if inhalt and F.face != "down":
        y0, y1 = F.el["from"][1], F.el["to"][1]
        voll = F.Y < y0 + (y1 - y0) * 0.7
        farbe = np.floor(hsh(F.U // 1, F.V // 1, seed=F.seed + 3) * 5)
        cols = np.array([PAL["rot"][3], PAL["gold"][3], PAL["weiss"][3], PAL["gruen"][3], PAL["blau"][3]], float)
        z = cols[farbe.astype(int)]
        mix = voll & (F.U != 1) & (F.U != F.w - 1) if F.face != "up" else voll
        c = np.where(mix[..., None], z * 0.85 + c * 0.15, c)
    return c


# ---------------------------------------------------------------- Bilder (Decals)

def d_text(F, grund=("lack", {"pal": "gruen"}), text="", farbe=None, rahmen=None, schatten=False, y=None):
    img = bevel(paint(grund, F), 1.08, 0.8)
    if rahmen is not None:
        img[0, :] = img[-1, :] = img[:, 0] = img[:, -1] = rahmen
    col = farbe if farbe is not None else rgb("gold", 3)
    sh = tuple(int(v * 0.45) for v in img[F.h // 2, F.w // 2]) if schatten else None
    text_centered(img, text, col, y=y, shadow=sh)
    return img


def d_schild(F):
    """Standschild: rotes Feld, Goldrahmen, Stern und „MARKT“."""
    img = paint(("lack", {"pal": "rot"}), F)
    g, gd = np.array(rgb("gold", 3), float), np.array(rgb("gold", 1), float)
    img[0, :] = img[-1, :] = img[:, 0] = img[:, -1] = gd
    img[1, 1:-1] = img[-2, 1:-1] = g
    img[1:-1, 1] = img[1:-1, -2] = g
    sx = (F.w - 5) // 2
    draw_glyphs(img, STERN, sx + 1, 3, np.array(rgb("gold", 0), float))
    draw_glyphs(img, STERN, sx, 2, g)
    text_centered(img, "MARKT", np.array(rgb("weiss", 4), float), y=F.h - 7, shadow=np.array(rgb("rot", 0), float))
    return img


def d_tuer(F, zahl=1, pal="rot", ziffer=None, gross=False):
    """Türchen des Adventskalenders: Farbe, Schattenkante, Zahl (beim 24. Türchen größer mit Knauf)."""
    img = paint(("lack", {"pal": pal}), F)
    rahmen = np.array(rgb(pal, 0), float)
    img[-1, :] = rahmen
    img[0, :] = img[0, :] * 1.15
    zc = np.array(ziffer if ziffer is not None else rgb("gold", 4), float)
    s = str(zahl)
    if gross:
        img[F.h - 3, F.w - 3] = np.array(rgb("gold", 4), float)
        img[8, 1:-1] = img[8, 1:-1] * 0.8
        text_centered(img, s, zc, y=2, shadow=rahmen)
    else:
        text_centered(img, s, zc, y=max(0, (F.h - 5) // 2))
    return np.clip(img, 0, 255)


def d_losrad(F, cx=8.0, cy=21.0, r=6.0):
    """Losrad von vorn: acht farbige Felder, Goldrand, Speichen, Nabe – in Modellkoordinaten gemalt."""
    dx, dy = F.X - cx, F.Y - cy
    d = np.hypot(dx, dy)
    ang = (np.degrees(np.arctan2(dy, dx)) + 360 + 22.5) % 360
    seg = np.floor(ang / 45).astype(int)
    cols = np.array([rgb("rot", 3), rgb("weiss", 3), rgb("gruen", 3), rgb("gold", 3),
                     rgb("rot", 3), rgb("weiss", 3), rgb("blau", 3), rgb("gold", 3)], float)
    c = cols[seg % 8]
    # Speichen (Grenzen der Felder)
    rest = ang % 45
    c = np.where(((rest < 4) | (rest > 41))[..., None] & (d > 1.2)[..., None], np.array(rgb("gold", 1), float), c)
    # Zahlen-Punkte am Rand
    c = np.where(((d > r - 1.6) & (d < r - 1.0) & (np.abs(rest - 22.5) < 6))[..., None], np.array(rgb("weiss", 4), float), c)
    c = np.where((d > r - 0.75)[..., None], np.array(rgb("gold", 2), float), c)
    c = np.where((d > r - 0.3)[..., None], np.array(rgb("gold", 1), float), c)
    c = np.where((d < 1.25)[..., None], np.array(rgb("gold", 3), float), c)
    return c


def d_tafel(F, titel="PREISE", zeilen=()):
    """Schiefertafel mit Kreide: Überschrift, Strich, Zeilen mit je zwei Posten (Bildchen + Zahl)."""
    t = 0.45 + (hsh(F.U, F.V, seed=F.seed) - 0.5) * 0.4
    img = quant("tafel", np.clip(t, 0, 0.999))
    sm = hsh(F.U // 3, F.V // 2, seed=F.seed + 1) < 0.07
    img = np.where(sm[..., None], img * 0.8 + np.array(CHALK) * 0.2, img)
    ch = np.array(CHALK, float)
    text_centered(img, titel, ch, y=1)
    img[7, 3:-3] = ch * 0.7
    y = 9
    for zeile in zeilen:
        if y + 5 > F.h - 1:
            break
        for k, (icon, txt) in enumerate(zeile):
            x = 2 + k * (F.w // 2)
            draw_glyphs(img, ICONS[icon][0], x, y, np.array(ICONS[icon][1], float))
            draw_text(img, txt, x + 7, y, ch)
        y += 6
    return img


ICONS = {
    "holz": ([".##..", "#..#.", "#..##", ".##.#", "..##."], (176, 132, 84)),
    "weizen": (["#.#.#", ".###.", "..#..", "..#..", "..#.."], (226, 196, 96)),
    "apfel": (["..#..", ".##..", "####.", "####.", ".##.."], (214, 70, 60)),
    "keks": ([".###.", "#.#.#", "#####", "#.#.#", ".###."], (196, 140, 80)),
}


def d_stern(F, grund=("metall", {"pal": "gold"})):
    img = paint(grund, F)
    sx, sy = (F.w - 5) // 2, (F.h - 5) // 2
    draw_glyphs(img, STERN, sx, sy, np.array(rgb("gold", 4), float))
    return img


def d_zettel(F, pal="papier", bild=None, tinte=INK):
    img = paint(("papier", {"pal": pal, "linien": bild is None, "tinte": tinte}), F)
    if bild:
        g, col = ICONS[bild]
        draw_glyphs(img, g, (F.w - 5) // 2, 2, np.array(col, float))
        y = 9
        while y < F.h - 2:
            img[y, 2:F.w - 2] = np.array(tinte, float) * 0.7 + img[y, 2:F.w - 2] * 0.3
            y += 2
    img[0, :] *= 0.92
    img[-1, :] *= 0.85
    return img


MATERIALS = {
    "holz": (m_holz, True), "balken": (m_balken, True), "lack": (m_lack, True), "markise": (m_markise, False),
    "metall": (m_metall, True), "schnee": (m_schnee, False), "tanne": (m_tanne, False), "papier": (m_papier, False),
    "kork": (m_kork, False), "stoff": (m_stoff, True), "licht": (m_licht, False), "apfelkiste": (m_apfelkiste, False),
    "ware": (m_ware, False), "lebkuchen": (m_lebkuchen, True), "sack": (m_sack, False), "scheite": (m_scheite, False),
    "glas": (m_glas, True),
    "text": (d_text, False), "schild": (d_schild, False), "tuer": (d_tuer, False), "losrad": (d_losrad, False),
    "tafel": (d_tafel, False), "stern": (d_stern, False), "zettel": (d_zettel, False),
}


def M(name, **kw):
    return (name, kw)


def paint(spec, F):
    name, kw = (spec, {}) if isinstance(spec, str) else spec
    fn, _ = MATERIALS[name]
    return np.asarray(fn(F, **kw), dtype=np.float64)


def paint_face(spec, F, kante):
    name = spec if isinstance(spec, str) else spec[0]
    img = paint(spec, F)
    if kante is None:
        kante = MATERIALS[name][1]
    if kante:
        img = bevel(img, 1.14 if name == "metall" else 1.1, 0.72 if name == "metall" else 0.8)
    return np.clip(img, 0, 255)


# ---------------------------------------------------------------- Geometrie

def rot_matrix(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def to_world(el, p):
    p = np.asarray(p, float)
    r = el.get("rot")
    if not r:
        return p
    axis, ang, org = r
    o = np.asarray(org, float)
    return (rot_matrix(axis, ang) @ (p - o).T).T + o


def to_local(el, p):
    p = np.asarray(p, float)
    r = el.get("rot")
    if not r:
        return p
    axis, ang, org = r
    o = np.asarray(org, float)
    return (rot_matrix(axis, -ang) @ (p - o).T).T + o


def face_corners(el, face):
    (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
    c = {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]
    return to_world(el, c)


def world_normal(el, face):
    n = np.array(NORMAL[face], float)
    r = el.get("rot")
    if r:
        n = rot_matrix(r[0], r[1]) @ n
    return n


def clip_poly(poly, a, b):
    """Konvexes Polygon an der Kante a→b abschneiden (Sutherland–Hodgman, links bleibt)."""
    def side(p):
        return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])
    out = []
    n = len(poly)
    for i in range(n):
        p, q = poly[i], poly[(i + 1) % n]
        sp, sq = side(p), side(q)
        if sp >= -1e-9:
            out.append(p)
        if (sp >= -1e-9) != (sq >= -1e-9):
            t = sp / (sp - sq)
            out.append((p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t))
    return out


def area(poly):
    s = 0.0
    for i in range(len(poly)):
        x0, y0 = poly[i]
        x1, y1 = poly[(i + 1) % len(poly)]
        s += x0 * y1 - x1 * y0
    return s / 2


def overlap_area(pa, pb):
    if area(pa) < 0:
        pa = pa[::-1]
    if area(pb) < 0:
        pb = pb[::-1]
    poly = pa
    for i in range(len(pb)):
        if not poly:
            return 0.0
        poly = clip_poly(poly, pb[i], pb[(i + 1) % len(pb)])
    return abs(area(poly)) if len(poly) >= 3 else 0.0


# ---------------------------------------------------------------- Modell

class Modell:
    def __init__(self, name, partikel=("holz", {"pal": "fichte"})):
        self.name = name
        self.els = []
        self.partikel = partikel

    def box(self, frm, to, mat, rot=None, emit=0, kante=None, name="", nur=None, ohne=()):
        """Quader von frm bis to (Pixel). mat: Material oder {Fläche|"*": Material}; rot: (Achse, Winkel, Drehpunkt).
        nur/ohne: nur diese bzw. diese Flächen nicht erzeugen."""
        mats = mat if isinstance(mat, dict) else {"*": mat}
        fm = {}
        for f in FACES:
            m = mats.get(f, mats.get("*"))
            if nur is not None and f not in nur:
                m = None
            if f in ohne:
                m = None
            fm[f] = m
        el = {"from": [float(v) for v in frm], "to": [float(v) for v in to], "mats": fm, "rot": rot, "emit": emit,
              "kante": kante, "name": name or "teil"}
        self.els.append(el)
        return el

    # ------------------------------------------------------------ Prüfen und Aufräumen

    def check_basics(self):
        errs = []
        seen = set()
        for i, e in enumerate(self.els):
            f, t = e["from"], e["to"]
            for v in f + t:
                if v < -16 or v > 32:
                    errs.append(f"{e['name']}#{i}: Koordinate {v} außerhalb −16…32")
                if abs(v * D - round(v * D)) > 1e-9:
                    errs.append(f"{e['name']}#{i}: Koordinate {v} nicht auf dem Texelraster")
            for k in range(3):
                if t[k] - f[k] < 0.5 - 1e-9:
                    errs.append(f"{e['name']}#{i}: Ausdehnung {t[k] - f[k]} < 0,5 (Achse {'xyz'[k]})")
            r = e["rot"]
            if r:
                if r[0] not in ("x", "y", "z") or r[1] not in (-45, -22.5, 22.5, 45):
                    errs.append(f"{e['name']}#{i}: Rotation {r[:2]} nicht in 22,5°-Schritten")
            key = (tuple(f), tuple(t), (r[0], r[1], tuple(r[2])) if r else None)
            if key in seen:
                errs.append(f"{e['name']}#{i}: doppeltes Element")
            seen.add(key)
        return errs

    def cull(self):
        """Flächen entfernen, deren Punkte (knapp vor der Fläche) alle in anderen Quadern liegen."""
        removed = 0
        for i, e in enumerate(self.els):
            for f in FACES:
                if not e["mats"][f]:
                    continue
                fw, fh = face_size(e, f)
                nu, nv = max(2, int(round(fw * D)) * 2), max(2, int(round(fh * D)) * 2)
                F = Flaeche(e, f, 0)
                # Abtastpunkte: halbe Texel, an den Rändern nach innen gerückt
                (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
                us = (np.arange(nu) + 0.5) / nu
                vs = (np.arange(nv) + 0.5) / nv
                uu, vv = np.meshgrid(us, vs)
                cs = face_corners({"from": e["from"], "to": e["to"], "rot": None}, f)
                p0, p1, p3 = np.array(cs[0]), np.array(cs[1]), np.array(cs[3])
                pts = p0 + uu[..., None] * (p1 - p0) + vv[..., None] * (p3 - p0)
                pts = pts.reshape(-1, 3) + np.array(NORMAL[f]) * 0.01
                wpts = to_world(e, pts)
                covered = np.zeros(len(wpts), bool)
                for j, o in enumerate(self.els):
                    if j == i:
                        continue
                    lp = to_local(o, wpts)
                    lo, hi = np.array(o["from"]) + 1e-4, np.array(o["to"]) - 1e-4
                    covered |= np.all((lp > lo) & (lp < hi), axis=1)
                    if covered.all():
                        break
                if covered.all():
                    e["mats"][f] = None
                    removed += 1
        return removed

    def zfight(self):
        faces = []
        for i, e in enumerate(self.els):
            for f in FACES:
                if e["mats"][f]:
                    faces.append((i, f, face_corners(e, f), world_normal(e, f)))
        errs = []
        for a in range(len(faces)):
            ia, fa, ca, na = faces[a]
            for b in range(a + 1, len(faces)):
                ib, fb, cb, nb = faces[b]
                if ia == ib or np.dot(na, nb) < 0.9999:
                    continue
                if abs(np.dot(na, cb[0] - ca[0])) > 1e-3:
                    continue
                # in die Ebene projizieren
                u = ca[1] - ca[0]
                u = u / np.linalg.norm(u)
                v = np.cross(na, u)
                pa = [(float(np.dot(p - ca[0], u)), float(np.dot(p - ca[0], v))) for p in ca]
                pb = [(float(np.dot(p - ca[0], u)), float(np.dot(p - ca[0], v))) for p in cb]
                ov = overlap_area(pa, pb)
                if ov > 1e-3:
                    errs.append(f"Z-Fighting: {self.els[ia]['name']}#{ia}.{fa} ↔ {self.els[ib]['name']}#{ib}.{fb} ({ov:.2f} px²)")
        return errs

    # ------------------------------------------------------------ Atlas und JSON

    def build(self):
        errs = self.check_basics()
        if errs:
            raise SystemExit(f"{self.name}: " + "; ".join(errs))
        removed = self.cull()
        errs = self.zfight()
        if errs:
            raise SystemExit(f"{self.name}:\n  " + "\n  ".join(errs))
        jobs = []
        for i, e in enumerate(self.els):
            for f in FACES:
                if e["mats"][f]:
                    fw, fh = face_size(e, f)
                    jobs.append((i, f, int(round(fw * D)), int(round(fh * D))))
        order = sorted(range(len(jobs)), key=lambda k: (-jobs[k][3], -jobs[k][2], k))
        size = 64
        while True:
            pos, x, y, row, ok = {}, 0, 0, 0, True
            for k in order:
                _, _, w, h = jobs[k]
                if w > size:
                    ok = False
                    break
                if x + w > size:
                    x, y, row = 0, y + row, 0
                if y + h > size:
                    ok = False
                    break
                pos[k] = (x, y)
                x += w
                row = max(row, h)
            if ok:
                break
            size *= 2
        img = np.zeros((size, size, 3))
        used = np.zeros((size, size), bool)
        seed0 = sum(map(ord, self.name))
        for k, (i, f, w, h) in enumerate(jobs):
            e = self.els[i]
            F = Flaeche(e, f, seed0 * 31 + i * 7 + FACES.index(f))
            px, py = pos[k]
            img[py:py + h, px:px + w] = paint_face(e["mats"][f], F, e["kante"])
            used[py:py + h, px:px + w] = True
        img = dilate(img, used)
        self.atlas = Image.fromarray(np.round(img).astype(np.uint8), "RGB").convert("RGBA")
        self.size = size
        self.uv = {(jobs[k][0], jobs[k][1]): (pos[k][0], pos[k][1], jobs[k][2], jobs[k][3]) for k in range(len(jobs))}
        return removed

    def model_json(self):
        s = 16.0 / self.size
        out = []
        for i, e in enumerate(self.els):
            faces = {}
            for f in FACES:
                if not e["mats"][f]:
                    continue
                px, py, w, h = self.uv[(i, f)]
                fj = {"uv": [num(px * s), num(py * s), num((px + w) * s), num((py + h) * s)], "texture": "#0"}
                cf = self.cullface(e, f)
                if cf:
                    fj["cullface"] = cf
                faces[f] = fj
            if not faces:
                continue
            ej = {"name": e["name"], "from": [num(v) for v in e["from"]], "to": [num(v) for v in e["to"]]}
            if e["rot"]:
                ej["rotation"] = {"angle": num(e["rot"][1]), "axis": e["rot"][0], "origin": [num(v) for v in e["rot"][2]]}
            if e["emit"]:
                ej["light_emission"] = e["emit"]
                ej["shade"] = False
            ej["faces"] = faces
            out.append(ej)
        return {
            "credit": "TwoGG Markt – erzeugt von tools/modelle.py",
            "texture_size": [self.size, self.size],
            "textures": {"0": f"{NS}:block/{self.name}", "particle": f"{NS}:block/{self.name}_partikel"},
            "elements": out,
            "display": self.display(),
        }

    @staticmethod
    def cullface(e, f):
        if e["rot"]:
            return None
        (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
        inside = lambda a0, a1: a0 >= 0 and a1 <= 16
        if f == "down" and y0 == 0 and inside(x0, x1) and inside(z0, z1):
            return "down"
        if f == "up" and y1 == 16 and inside(x0, x1) and inside(z0, z1):
            return "up"
        if f == "north" and z0 == 0 and inside(x0, x1) and inside(y0, y1):
            return "north"
        if f == "south" and z1 == 16 and inside(x0, x1) and inside(y0, y1):
            return "south"
        if f == "west" and x0 == 0 and inside(z0, z1) and inside(y0, y1):
            return "west"
        if f == "east" and x1 == 16 and inside(z0, z1) and inside(y0, y1):
            return "east"
        return None

    def corners(self):
        pts = []
        for e in self.els:
            if not any(e["mats"].values()):
                continue
            (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
            pts.extend(to_world(e, [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]))
        return np.array(pts)

    def display(self):
        pts = self.corners() - 8.0
        lo, hi = pts.min(0) + 8, pts.max(0) + 8
        ext = float(max(hi - lo))
        k = min(1.0, 16.0 / ext)

        def fit(rx, ry, target_w, target_h, smax):
            r = rot_matrix("x", rx) @ rot_matrix("y", ry)
            p = (r @ pts.T).T
            w, h = p[:, 0].max() - p[:, 0].min(), p[:, 1].max() - p[:, 1].min()
            s = min(target_w / w, target_h / h, smax)
            cx, cy = (p[:, 0].max() + p[:, 0].min()) / 2, (p[:, 1].max() + p[:, 1].min()) / 2
            return round(s, 3), [round(-cx * s, 2), round(-cy * s, 2), 0]

        # 210° statt 225° wie bei Vanilla-Blöcken: die Vorderseite (Norden) zeigt mehr zum Betrachter
        gs, gt = fit(30, 210, 14.6, 15.4, 0.9)
        fs, ft = fit(0, 180, 15.0, 15.0, 0.9)
        cy = (lo[1] + hi[1]) / 2 - 8
        return {
            "gui": {"rotation": [30, 210, 0], "translation": gt, "scale": [gs] * 3},
            "fixed": {"rotation": [0, 180, 0], "translation": ft, "scale": [fs] * 3},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [round(0.25 * k, 3)] * 3},
            "head": {"rotation": [0, 180, 0], "translation": [0, round(-cy * 0.6 * k, 2), 0], "scale": [round(0.6 * k, 3)] * 3},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [round(0.375 * k, 3)] * 3},
            "thirdperson_lefthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [round(0.375 * k, 3)] * 3},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, round(-cy * 0.4 * k, 2), 0], "scale": [round(0.4 * k, 3)] * 3},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, round(-cy * 0.4 * k, 2), 0], "scale": [round(0.4 * k, 3)] * 3},
        }

    def partikel_bild(self):
        e = {"from": [0, 0, 0], "to": [16, 16, 16], "rot": None}
        F = Flaeche(e, "north", 5)
        return Image.fromarray(np.round(paint_face(self.partikel, F, False)).astype(np.uint8), "RGB").convert("RGBA")

    def write(self):
        removed = self.build()
        os.makedirs(MODELS, exist_ok=True)
        os.makedirs(BLOCK_TEX, exist_ok=True)
        with open(os.path.join(MODELS, self.name + ".json"), "w", encoding="utf-8") as fh:
            json.dump(self.model_json(), fh, ensure_ascii=False, indent=1)
            fh.write("\n")
        self.atlas.save(os.path.join(BLOCK_TEX, self.name + ".png"))
        self.partikel_bild().save(os.path.join(BLOCK_TEX, self.name + "_partikel.png"))
        n_el = sum(1 for e in self.els if any(e["mats"].values()))
        n_f = sum(1 for e in self.els for f in FACES if e["mats"][f])
        print(f"{self.name:16s} {n_el:3d} Elemente, {n_f:4d} Flächen ({removed} verdeckte entfernt), Atlas {self.size}²")


def num(v):
    v = round(float(v), 4)
    return int(v) if v == int(v) else v


def dilate(img, used):
    """Unbenutzte Atlasflächen mit der Nachbarfarbe füllen (kein Farbsaum durch Mipmaps)."""
    img, used = img.copy(), used.copy()
    if not used.any():
        return img
    for _ in range(img.shape[0]):
        if used.all():
            break
        acc = np.zeros_like(img)
        cnt = np.zeros(used.shape)
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            sh_u = np.roll(used, (dy, dx), (0, 1))
            sh_i = np.roll(img, (dy, dx), (0, 1))
            acc += sh_i * sh_u[..., None]
            cnt += sh_u
        new = (~used) & (cnt > 0)
        img[new] = acc[new] / cnt[new][:, None]
        used |= new
    return img


# ---------------------------------------------------------------- Bausteine

HOLZ = M("holz", pal="fichte")
BALKEN = M("balken")
HELL = M("holz", pal="hell")
SCHNEE = M("schnee")
TANNE = M("tanne")
GOLD = M("metall", pal="gold")
EISEN = M("metall", pal="eisen")


def licht(m, frm, to, i, name="licht", rot=None):
    return m.box(frm, to, M("licht", farbe=LICHTFARBEN[i % len(LICHTFARBEN)]), emit=15, kante=False, name=name, rot=rot)


def snap(v):
    return round(v * D) / D


# ---------------------------------------------------------------- Verkaufstheke

def verkaufstheke():
    m = Modell("market_counter", partikel=HOLZ)
    # Korpus: Fichtenbretter (senkrecht), vorn zurückgesetzt zwischen Eckpfosten
    m.box((0.5, 1, 3), (15.5, 11, 15.5), {"*": M("holz", pal="fichte", achse="y"), "up": None}, name="korpus")
    m.box((1.5, 0, 2.5), (14.5, 1, 15), BALKEN, name="sockel")
    for x0, x1 in ((0, 1.5), (14.5, 16)):
        m.box((x0, 0, 2), (x1, 11, 4), BALKEN, name="eckpfosten_vorn")
        m.box((x0, 0, 14), (x1, 11, 16), BALKEN, name="eckpfosten_hinten")
    # Querleiste und Goldplakette mit Stern
    m.box((1.5, 2, 2.5), (14.5, 3, 3), BALKEN, name="leiste")
    m.box((6, 4, 2), (10, 7.5, 3), M("stern", grund=M("lack", pal="rot")), name="plakette")
    # Arbeitsplatte mit Überstand vorn
    m.box((0, 11, 1.5), (16, 13, 16), M("holz", pal="hell", achse="x"), name="platte")
    # Tannengirlande unter der Platte, durchhängend, mit Kugeln
    for (x0, x1, y0) in ((1.5, 5.5, 9.5), (5.5, 10.5, 8.5), (10.5, 14.5, 9.5)):
        m.box((x0, y0, 1.5), (x1, y0 + 1.5, 2.5), TANNE, name="girlande")
    for i, (x, y) in enumerate(((3, 8.5), (7.5, 7.5), (12.5, 8.5))):
        m.box((x, y, 1), (x + 1, y + 1, 2), M("metall", pal=("rot", "gold", "rot")[i]), name="kugel")
    # Auslage: Apfelkiste
    m.box((9, 13, 3), (15, 16, 3.5), M("holz", pal="kiste", achse="x"), name="kiste")
    m.box((9, 13, 7.5), (15, 16, 8), M("holz", pal="kiste", achse="x"), name="kiste")
    m.box((9, 13, 3.5), (9.5, 16, 7.5), M("holz", pal="kiste", achse="z"), name="kiste")
    m.box((14.5, 13, 3.5), (15, 16, 7.5), M("holz", pal="kiste", achse="z"), name="kiste")
    m.box((9.5, 13, 3.5), (14.5, 15.5, 7.5), {"*": M("ware", pal="apfel"), "up": M("apfelkiste")}, name="aepfel")
    for (x, z) in ((10, 4), (12.5, 5.5), (11, 6.5)):
        m.box((x, 15.5, z), (x + 1, 16.5, z + 1), M("ware", pal="apfel"), name="apfel")
    # Lebkuchen gestapelt
    m.box((2, 13, 4), (7, 14, 8), M("lebkuchen"), name="lebkuchen")
    m.box((2.5, 14, 4.5), (6.5, 15, 7.5), M("lebkuchen"), name="lebkuchen")
    m.box((3, 15, 5), (6, 16, 7), M("lebkuchen"), name="lebkuchen")
    # Preisschild
    m.box((6.5, 13, 2), (8.5, 16, 2.5), M("text", grund=M("lack", pal="weiss"), text="2", farbe=rgb("rot", 1), schatten=False), name="preis")
    # Kleine Markise auf zwei Stangen, schräg nach vorn, mit Volant und Lichterkette
    for x0 in (0.5, 14.5):
        m.box((x0, 13, 14.5), (x0 + 1, 30.5, 15.5), BALKEN, name="stange")
    piv = (8, 31, 16)
    rot = ("x", -22.5, piv)
    m.box((0, 30.5, 0), (16, 31, 16), M("markise", achse="x", breite=2), rot=rot, kante=False, name="markise")
    # Vorderkante der Markise (unten) berechnen und den Volant dort aufhängen
    front = to_world({"rot": rot}, [(0, 30.5, 0)])[0]
    zf, yf = snap(front[2]), snap(front[1])
    m.box((0.5, yf - 2, zf - 0.5), (15.5, yf, zf), M("markise", achse="x", breite=2, haengend=True), kante=False, name="volant")
    for i, x in enumerate(np.arange(1, 16, 2.5)):
        licht(m, (x, yf - 2.5, zf - 0.5), (x + 0.5, yf - 2, zf), i)
    return m


# ---------------------------------------------------------------- Standschild

def standschild():
    m = Modell("stall_sign", partikel=M("lack", pal="rot"))
    m.box((7, 0, 7), (9, 5.5, 9), BALKEN, name="pfosten")
    for frm, to in (((4, 0, 7), (7, 1, 9)), ((9, 0, 7), (12, 1, 9)), ((7, 0, 4), (9, 1, 7)), ((7, 0, 9), (9, 1, 12))):
        m.box(frm, to, BALKEN, name="fuss")
    m.box((4.5, 1, 7.5), (6, 1.5, 8.5), SCHNEE, name="schnee")
    m.box((7.5, 1, 9.5), (8.5, 1.5, 11), SCHNEE, name="schnee")
    m.box((0.5, 5.5, 6.5), (15.5, 13, 9.5), {"*": BALKEN, "north": M("schild"), "south": M("schild")}, name="schild")
    m.box((0, 13, 6), (16, 14, 10), BALKEN, name="dach")
    m.box((0.5, 14, 6.5), (15.5, 15, 9.5), SCHNEE, name="schnee")
    m.box((2, 15, 7), (6.5, 15.5, 9), SCHNEE, name="schnee")
    m.box((8, 15, 7.5), (10.5, 16, 9), SCHNEE, name="schnee")
    # Tannenzweig mit Beeren
    m.box((1, 15, 6.5), (4, 16, 8), TANNE, name="zweig")
    m.box((2, 16, 7), (3, 16.5, 7.5), M("metall", pal="rot"), name="beere")
    m.box((3, 15.5, 6), (3.5, 16, 6.5), M("metall", pal="rot"), name="beere")
    # Laterne auf dem Dach
    m.box((11.5, 15, 7), (14, 15.5, 9.5), EISEN, name="laterne_boden")
    for (x, z) in ((11.5, 7), (13.5, 7), (11.5, 9), (13.5, 9)):
        m.box((x, 15.5, z), (x + 0.5, 18, z + 0.5), EISEN, name="laterne_stab")
    m.box((12, 15.5, 7.5), (13.5, 18, 9), M("licht", farbe=(255, 200, 96)), emit=15, kante=False, name="laterne_glas")
    m.box((11.5, 18, 7), (14, 18.5, 9.5), EISEN, name="laterne_deckel")
    m.box((12.5, 18.5, 8), (13, 19.5, 8.5), EISEN, name="laterne_griff")
    return m


# ---------------------------------------------------------------- Ankaufstelle

def ankaufstelle():
    m = Modell("buyer_station", partikel=M("holz", pal="kiste"))
    m.box((0.5, 1, 2), (15.5, 11, 15), {"*": M("holz", pal="kiste", achse="x"), "up": None}, name="korpus")
    m.box((1.5, 0, 2.5), (14.5, 1, 14.5), BALKEN, name="sockel")
    for x0, x1 in ((0, 1.5), (14.5, 16)):
        m.box((x0, 0, 1.5), (x1, 11, 3), BALKEN, name="pfosten")
        m.box((x0, 0, 14), (x1, 11, 15.5), BALKEN, name="pfosten")
    m.box((1.5, 5, 1), (14.5, 9, 2), M("text", grund=M("lack", pal="gruen"), text="ANKAUF", farbe=rgb("weiss", 4), rahmen=rgb("gold", 2)), name="schild")
    m.box((1.5, 2, 1.5), (14.5, 3, 2), BALKEN, name="leiste")
    m.box((0, 11, 1), (16, 12, 16), M("holz", pal="hell", achse="x"), name="platte")
    # Waage (Messing)
    m.box((9, 12, 5), (13, 13, 9), GOLD, name="waage_fuss")
    m.box((10.5, 13, 6.5), (11.5, 20, 7.5), GOLD, name="waage_saeule")
    m.box((7, 20, 6), (15, 21, 8), GOLD, name="waage_balken")
    m.box((10, 21, 6.5), (12, 22, 7.5), GOLD, name="waage_knauf")
    for x in (7.5, 14):
        m.box((x, 16.5, 7), (x + 0.5, 20, 7.5), EISEN, name="waage_kette")
    m.box((6, 16, 5.5), (9.5, 16.5, 9), GOLD, name="waage_schale")
    m.box((12.5, 16, 5.5), (16, 16.5, 9), GOLD, name="waage_schale")
    m.box((7, 16.5, 6.5), (8.5, 17.5, 8), GOLD, name="muenzen")
    m.box((6.5, 17.5, 7.5), (8, 18, 8.5), GOLD, name="muenzen")
    m.box((13.5, 16.5, 6.5), (15, 18, 8), M("ware", pal="apfel"), name="apfel")
    # Kiste mit Holzscheiten
    k = M("holz", pal="kiste")
    m.box((0.5, 12, 2.5), (6.5, 15, 3), k, name="kiste")
    m.box((0.5, 12, 8), (6.5, 15, 8.5), k, name="kiste")
    m.box((0.5, 12, 3), (1, 15, 8), k, name="kiste")
    m.box((6, 12, 3), (6.5, 15, 8), k, name="kiste")
    m.box((1, 12, 3), (6, 14.5, 8), {"*": M("balken", pal="borke"), "up": M("scheite")}, name="scheite")
    m.box((1, 15, 3.5), (3, 17, 8), {"*": M("balken", pal="borke"), "north": M("holz", pal="hell", achse="z"),
                                    "south": M("holz", pal="hell", achse="z")}, name="scheit")
    # Getreidesack
    m.box((1, 12, 9.5), (6, 18, 13.5), M("sack"), name="sack")
    m.box((2, 18, 10.5), (5, 18.5, 12.5), M("sack"), name="sack")
    m.box((3, 18.5, 11), (4, 19.5, 12), M("ware", pal="gold"), name="sack_band")
    # Preistafel hinten mit kleinem Dach
    for x0, x1 in ((0.5, 2), (14, 15.5)):
        m.box((x0, 12, 14.5), (x1, 28, 16), BALKEN, name="tafel_pfosten")
    m.box((2, 14, 14.5), (14, 15, 16), BALKEN, name="tafel_leiste")
    m.box((2, 26, 14.5), (14, 27, 16), BALKEN, name="tafel_leiste")
    m.box((2, 15, 15), (14, 26, 15.5), {"*": BALKEN, "north": M("tafel", titel="PREISE",
                                                                    zeilen=((("holz", "2"), ("weizen", "1")), (("apfel", "3"), ("keks", "4"))))},
          name="tafel")
    rot = ("x", -22.5, (8, 29, 16))
    m.box((0, 28, 10.5), (16, 29, 16), BALKEN, rot=rot, name="dach")
    m.box((0.5, 29, 11), (15.5, 30, 16), SCHNEE, rot=rot, name="dach_schnee")
    front = to_world({"rot": rot}, [(0, 28, 10.5)])[0]
    for i, x in enumerate(np.arange(1.5, 15, 3)):
        licht(m, (x, snap(front[1]) - 0.5, snap(front[2]) + 0.5), (x + 0.5, snap(front[1]), snap(front[2]) + 1), i + 1)
    return m


# ---------------------------------------------------------------- Auftragsbrett

def auftragsbrett():
    m = Modell("job_board", partikel=M("kork"))
    for x0, x1 in ((0, 2), (14, 16)):
        m.box((x0, 0, 7), (x1, 29, 9), BALKEN, name="pfosten")
        m.box((x0, 0, 5), (x1, 1, 7), BALKEN, name="fuss")
        m.box((x0, 0, 9), (x1, 1, 11), BALKEN, name="fuss")
    m.box((2, 6, 7), (14, 7, 9), BALKEN, name="leiste")
    m.box((2, 23, 7), (14, 24, 9), BALKEN, name="leiste")
    m.box((2, 7, 7.5), (14, 23, 8.5), M("kork"), name="kork")
    m.box((0, 24.5, 6), (16, 28, 7), {"*": BALKEN, "north": M("text", grund=M("lack", pal="rot"), text="AUFTRÄGE",
                                                             farbe=rgb("weiss", 4), y=1)}, name="kopf")
    m.box((0, 23.5, 6), (16, 24.5, 7), TANNE, name="girlande")
    for i, x in enumerate((1.5, 5, 8.5, 12, 14.5)):
        m.box((x, 23, 5.5), (x + 0.5, 23.5, 6), M("metall", pal=("rot", "gold")[i % 2]), name="kugel")
    # Zettel vorn (mit Nadeln) und hinten
    zettel = (
        ((2.5, 16, 6.5), M("zettel"), "rot"),
        ((7, 17, 10.5), M("zettel", pal="papier", bild="holz"), "gruen"),
        ((11, 12.5, 13.5), M("zettel", pal=[(150, 190, 220), (170, 206, 232), (188, 220, 240), (206, 232, 248)]), "gold"),
        ((3, 8, 7.5), M("zettel", pal=[(226, 206, 120), (238, 222, 140), (246, 234, 162), (252, 244, 190)]), "blau"),
        ((8, 8, 13), M("zettel", bild="apfel"), "rot"),
    )
    yy = ((16, 21.5), (16.5, 22), (12.5, 16), (8, 13.5), (7.5, 12))
    for ((x0, _, x1), mat, pin), (y0, y1) in zip(zettel, yy):
        m.box((x0, y0, 7), (x1, y1, 7.5), {"*": M("papier", linien=False), "north": mat}, kante=False, name="zettel")
        cx = snap((x0 + x1) / 2 - 0.25)
        m.box((cx, y1 - 1, 6.5), (cx + 0.5, y1 - 0.5, 7), M("metall", pal=pin), name="nadel")
    m.box((3, 9, 8.5), (8, 15, 9), {"*": M("papier", linien=False), "south": M("zettel")}, kante=False, name="zettel")
    m.box((9, 13, 8.5), (13, 19, 9), {"*": M("papier", linien=False), "south": M("zettel", bild="keks")}, kante=False, name="zettel")
    m.box((5, 14, 9), (5.5, 14.5, 9.5), M("metall", pal="rot"), name="nadel")
    m.box((10.5, 18, 9), (11, 18.5, 9.5), M("metall", pal="gold"), name="nadel")
    # Satteldach mit Schnee
    # Die hintere Dachhälfte ist an den Giebeln 0,5 px schmaler: sonst lägen die Giebelflächen beider Hälften am First
    # in derselben Ebene übereinander (Z-Fighting)
    for ang, z0, z1, ein in ((-22.5, 2, 8, 0), (22.5, 8, 14, 0.5)):
        rot = ("x", ang, (8, 31, 8))
        m.box((ein, 30, z0), (16 - ein, 31, z1), {"*": BALKEN, "up": M("holz", pal="fichte", achse="z")}, rot=rot, name="dach")
        m.box((0.5 + ein, 31, z0 + 0.5 if ang < 0 else z0), (15.5 - ein, 31.5, z1 if ang < 0 else z1 - 0.5), SCHNEE, rot=rot,
              name="dach_schnee")
    m.box((1, 29, 7), (15, 30, 9), BALKEN, name="firstbalken")
    return m


# ---------------------------------------------------------------- Losbude

def losbude():
    m = Modell("lottery_booth", partikel=M("lack", pal="rot"))
    m.box((0.5, 1, 3), (15.5, 12, 15.5), {"*": M("lack", pal="rot", achse="y", fugen=True), "up": None}, name="korpus")
    m.box((1.5, 0, 2.5), (14.5, 1, 15.5), BALKEN, name="sockel")
    for x0, x1 in ((0, 1.5), (14.5, 16)):
        m.box((x0, 0, 2), (x1, 12, 3.5), M("lack", pal="weiss"), name="eckpfosten")
        m.box((x0, 0, 14), (x1, 12, 16), M("lack", pal="weiss"), name="eckpfosten")
    m.box((3.5, 4.5, 2), (12.5, 9.5, 3), M("text", grund=M("lack", pal="weiss"), text="LOSE", farbe=rgb("rot", 2),
                                          rahmen=rgb("gold", 2)), name="schild")
    for x in (2, 13):
        m.box((x, 6.5, 2.5), (x + 1, 7.5, 3), GOLD, name="stern")
    m.box((0, 12, 1.5), (16, 13, 16), M("holz", pal="hell", achse="x"), name="platte")
    # Losrad: Scheibe aus Zeilen (Pixelkreis), vorn in Modellkoordinaten bemalt
    cx, cy, r = 8.0, 21.0, 6.0
    z0, z1 = 9.5, 10.5
    y = cy - r
    while y < cy + r - 1e-9:
        dy = (y + 0.5) - cy
        hw = snap(math.sqrt(max(0.0, r * r - dy * dy)))
        if hw >= 0.5:
            m.box((cx - hw, y, z0), (cx + hw, y + 1, z1), {"*": GOLD, "north": M("losrad", cx=cx, cy=cy, r=r)},
                  kante=False, name="losrad")
        y += 1
    for k in range(8):
        a = math.radians(k * 45)
        px, py = snap(cx + 4.75 * math.cos(a) - 0.25), snap(cy + 4.75 * math.sin(a) - 0.25)
        m.box((px, py, 9), (px + 0.5, py + 0.5, 9.5), GOLD, name="stift")
    m.box((7.5, 20.5, 8.5), (8.5, 21.5, 9.5), GOLD, name="nabe")
    m.box((7.5, 13, 10.5), (8.5, 29, 11.5), BALKEN, name="radstaender")
    m.box((5, 13, 9.5), (11, 14, 12.5), BALKEN, name="radfuss")
    m.box((7.5, 28, 9), (8.5, 29, 10.5), BALKEN, name="zeiger_arm")
    m.box((7.5, 27, 9), (8.5, 28, 9.5), M("metall", pal="rot"), name="zeiger")
    # Losglas
    m.box((11, 13, 3.5), (14, 16.5, 6.5), M("glas"), name="glas")
    m.box((10.5, 16.5, 3), (14.5, 17, 7), GOLD, name="glas_deckel")
    m.box((12, 17, 4.5), (13, 17.5, 5.5), GOLD, name="glas_knauf")
    # Lose (gestapelt)
    m.box((1.5, 13, 3), (4.5, 13.5, 5.5), M("lack", pal="rot"), name="lose")
    m.box((2, 13.5, 3.5), (5, 14, 6), M("papier", linien=False), name="lose")
    # Stangen und Satteldach (First längs) aus Markisenstoff
    for x0 in (0.5, 14.5):
        for z0 in (2, 14):
            m.box((x0, 13, z0), (x0 + 1, 28, z0 + 1), M("lack", pal="weiss"), name="stange")
    piv_y = 31.5
    for ang, x0, x1, ein in ((22.5, -1, 8, 0), (-22.5, 8, 17, 0.5)):
        rot = ("z", ang, (8, piv_y, 8))
        m.box((x0, 30.5, ein), (x1, 31.5, 16 - ein), M("markise", achse="z", breite=2), rot=rot, kante=False, name="dach")
        m.box((x0 + (0.5 if ang > 0 else 0), 31.5, 0.5 + ein), (x1 - (0.5 if ang < 0 else 0), 32, 15.5 - ein), SCHNEE, rot=rot,
              name="dach_schnee")
    # Volants an den Traufen (senkrecht), darunter Lichter
    for ang, xs in ((22.5, -1), (-22.5, 17)):
        e = to_world({"rot": ("z", ang, (8, piv_y, 8))}, [(xs, 30.5, 0)])[0]
        xe, ye = snap(e[0]), snap(e[1])
        xa, xb = (xe, xe + 0.5) if ang > 0 else (xe - 0.5, xe)
        m.box((xa, ye - 2.5, 0), (xb, ye, 16), M("markise", achse="z", breite=2, haengend=True), kante=False, name="volant")
        for i, z in enumerate(np.arange(1, 16, 2.5)):
            licht(m, (xa, ye - 3, z), (xb, ye - 2.5, z + 0.5), i + (2 if ang > 0 else 0))
    # Giebelstern vorn
    m.box((6.5, 27, 1), (9.5, 30, 1.5), M("stern", grund=M("lack", pal="rot")), name="giebelstern")
    m.box((7.5, 29, 1.5), (8.5, 30.5, 2), M("lack", pal="weiss"), name="giebelstern_halter")
    return m


# ---------------------------------------------------------------- Adventskalender

def adventskalender():
    """Holzhäuschen (an der Wand, Rückseite z = 16) mit 24 nummerierten Türchen, Giebel, Schneedach, Kamin."""
    m = Modell("advent_calendar", partikel=M("lack", pal="gruen"))
    m.box((-4.5, 0, 11.5), (20.5, 1, 16), M("balken", pal="stein"), name="sockel")
    m.box((-4, 1, 12), (20, 20, 16), {"*": M("holz", pal="fichte", achse="y", brett=3), "north": M("holz", pal="fichte", achse="y", brett=3)},
          name="haus")
    for x0 in (-4.5, 19.5):
        m.box((x0, 1, 11.5), (x0 + 1, 19.5, 12), BALKEN, name="eckbalken")
    farben = ["rot", "gruen", "weiss", "rot", "gold", "gruen", "blau", "rot", "weiss", "gruen", "gold", "rot",
              "gruen", "rot", "blau", "weiss", "rot", "gold"]
    zahlen = [7, 15, 2, 11, 18, 4, 13, 1, 9, 16, 6, 12, 3, 17, 8, 14, 10, 5]
    zc = {"weiss": rgb("rot", 1), "gold": rgb("rot", 0)}
    k = 0
    for row, y0 in enumerate((15.5, 11.5, 7.5)):
        for col in range(6):
            x0 = -3.5 + 4 * col
            p = farben[k]
            m.box((x0, y0, 11.5), (x0 + 3, y0 + 3.5, 12), {"*": M("lack", pal=p), "north": M("tuer", zahl=zahlen[k], pal=p,
                                                                                             ziffer=zc.get(p))},
                  kante=False, name=f"tuer_{zahlen[k]}")
            k += 1
    # Fensterbänke unter den Reihen
    for y0 in (7, 11, 15):
        m.box((-3.5, y0, 11), (19.5, y0 + 0.5, 12), M("lack", pal="weiss"), name="sims")
    # Untere Reihe: 20, 19 | Tür 24 | 21, 22
    unten = (((-3.5, 0.5), 20, "gruen"), ((1, 4.5), 19, "rot"), ((11.5, 15), 21, "gold"), ((15.5, 19.5), 22, "blau"))
    for (x0, x1), z, p in unten:
        m.box((x0, 2, 11.5), (x1, 5.5, 12), {"*": M("lack", pal=p), "north": M("tuer", zahl=z, pal=p, ziffer=zc.get(p))},
              kante=False, name=f"tuer_{z}")
    m.box((5.5, 1, 11.5), (10.5, 6.5, 12), {"*": M("lack", pal="rot"), "north": M("tuer", zahl=24, pal="rot", gross=True)},
          kante=False, name="tuer_24")
    m.box((5, 6.5, 11), (11, 7, 12), M("metall", pal="gold"), name="tuer_24_bogen")
    m.box((5, 1, 11), (5.5, 6.5, 12), M("lack", pal="weiss"), name="tuer_24_rahmen")
    m.box((10.5, 1, 11), (11, 6.5, 12), M("lack", pal="weiss"), name="tuer_24_rahmen")
    # Giebel (Stufen) und Tür 23
    piv = (8, 25, 14)
    t = math.tan(math.radians(22.5))
    stufen = []
    for kk in range(5):
        y0 = 20 + kk
        hw = (25 - (y0 + 1)) / t
        hw = math.floor(hw * 2) / 2
        if hw >= 0.5:
            stufen.append((y0, hw))
            m.box((8 - hw, y0, 12), (8 + hw, y0 + 1, 16), M("holz", pal="fichte", achse="y", brett=3), name="giebel")
    m.box((6, 20.5, 11.5), (10, 23.5, 12), {"*": M("lack", pal="gold"), "north": M("tuer", zahl=23, pal="gold", ziffer=rgb("rot", 0))},
          kante=False, name="tuer_23")
    # Dach (22,5°) mit Schnee, Eiszapfen und Lichterkette unter der Traufe
    for ang, x0, x1, ein in ((22.5, -6.5, 8, 0), (-22.5, 8, 22.5, 0.5)):
        rot = ("z", ang, piv)
        m.box((x0, 25, 10 + ein), (x1, 26, 16), {"*": M("lack", pal="rot", achse="x"), "up": M("holz", pal="balken", achse="x", brett=2)},
              rot=rot, name="dach")
        m.box((x0 + (0.5 if ang > 0 else 0), 26, 10.5 + ein), (x1 - (0.5 if ang < 0 else 0), 27, 16 - ein), SCHNEE, rot=rot,
              name="dach_schnee")
        n = 0
        for xi in np.arange(x0 + 1, x1 - 0.5, 2):
            licht(m, (xi, 24.5, 10.5), (xi + 0.5, 25, 11), n + (1 if ang > 0 else 3), name="lichterkette", rot=rot)
            n += 1
        tip = to_world({"rot": rot}, [((x0 if ang > 0 else x1), 25, 10)])[0]
        xt = snap(tip[0]) + (0 if ang > 0 else -1)
        yt = snap(tip[1])
        m.box((xt, yt - 2, 11), (xt + 0.5, yt, 11.5), M("glas", inhalt=False), name="eiszapfen")
        m.box((xt + 0.5, yt - 1, 11), (xt + 1, yt, 11.5), M("glas", inhalt=False), name="eiszapfen")
    # Kamin
    m.box((13, 23, 12.5), (15.5, 29, 15), M("balken", pal="stein"), name="kamin")
    m.box((12.5, 29, 12.5), (16, 30, 16), SCHNEE, name="kamin_schnee")
    # Tannengirlande unter dem Giebel mit Schleife
    m.box((-4, 19.5, 11), (20, 20.5, 12), TANNE, name="girlande")
    m.box((7, 19, 10.5), (9, 20.5, 11), M("metall", pal="rot"), name="schleife")
    return m


# ---------------------------------------------------------------- Verstecktes Geschenk

def geschenk():
    m = Modell("hidden_gift", partikel=M("stoff", pal="rot", muster="punkte"))
    band = (8.0, 8.0, 1.0)
    m.box((4, 0, 4), (12, 7, 12), {"*": M("stoff", pal="rot", muster="punkte", band=band), "up": None}, name="paket")
    m.box((3.5, 7, 3.5), (12.5, 8.5, 12.5), M("stoff", pal="rot", muster="punkte", band=band), name="deckel")
    # Schleife: Knoten, vier Schlaufen (22,5° angehoben), zwei Enden
    m.box((7, 8.5, 7), (9, 9.5, 9), GOLD, name="knoten")
    for ang, frm, to, piv in ((-22.5, (4.5, 8.5, 7.5), (7, 10.5, 8.5), (7, 8.5, 8)),
                              (22.5, (9, 8.5, 7.5), (11.5, 10.5, 8.5), (9, 8.5, 8))):
        m.box(frm, to, GOLD, rot=("z", ang, piv), name="schlaufe")
    for ang, frm, to, piv in ((22.5, (7.5, 8.5, 4.5), (8.5, 10.5, 7), (8, 8.5, 7)),
                              (-22.5, (7.5, 8.5, 9), (8.5, 10.5, 11.5), (8, 8.5, 9))):
        m.box(frm, to, GOLD, rot=("x", ang, piv), name="schlaufe")
    m.box((9, 8.5, 9), (10.5, 9, 10), GOLD, name="bandende")
    # Anhänger
    m.box((9.5, 3, 3.5), (11.5, 5.5, 4), {"*": M("papier", linien=False), "north": M("stern", grund=M("papier", linien=False))},
          kante=False, name="anhaenger")
    m.box((10, 5.5, 3.5), (10.5, 7, 4), GOLD, name="anhaenger_band")
    # Schneehäubchen
    m.box((3.5, 8.5, 9.5), (6.5, 9, 12.5), SCHNEE, name="schnee")
    m.box((4, 9, 10.5), (5.5, 9.5, 12), SCHNEE, name="schnee")
    # Zweites, kleines Geschenk (gedreht)
    rot = ("y", 22.5, (13.5, 1.5, 2.5))
    m.box((12, 0, 1), (15, 3, 4), M("stoff", pal="gruen", muster="sterne", band=(13.5, 2.5, 0.5), bandpal="rot", musterpal="weiss"),
          rot=rot, name="paket_klein")
    m.box((13, 3, 2), (14, 3.5, 3), M("metall", pal="rot"), rot=rot, name="knoten_klein")
    return m


# ---------------------------------------------------------------- Item: Marktplaner, Mod-Icon

def planer():
    """Marktplaner (16 × 16): Marktplan (Papier mit eingezeichneten Ständen) und rot-weiße Messlatte mit Goldstern."""
    W = 16
    im = np.zeros((W, W, 4), np.uint8)

    def put(x, y, c):
        if 0 <= x < W and 0 <= y < W:
            im[y, x] = tuple(int(v) for v in c) + (255,)

    # Plan: Papierbogen mit Rand, Gasse und drei Ständen
    for y in range(6, 15):
        for x in range(0, 10):
            edge = x in (0, 9) or y in (6, 14)
            put(x, y, rgb("papier", 0) if edge else rgb("papier", 3 if (x + y) % 3 else 2))
    for x in range(1, 9):
        put(x, 10, rgb("papier", 1))
    for (x0, y0, c) in ((1, 7, rgb("rot", 3)), (4, 7, rgb("gruen", 3)), (6, 11, rgb("gold", 2)), (2, 11, rgb("blau", 3))):
        for dx in range(2):
            for dy in range(2):
                put(x0 + dx, y0 + dy, c)
    # Messlatte diagonal, rot-weiß mit dunkler Kante
    for i in range(12):
        x, y = 3 + i, 14 - i
        rot_ = (i // 2) % 2 == 0
        put(x, y, rgb("rot", 3) if rot_ else rgb("weiss", 4))
        put(x + 1, y, rgb("rot", 1) if rot_ else rgb("weiss", 1))
    put(2, 15, rgb("balken", 2)); put(3, 15, rgb("balken", 1)); put(2, 14, rgb("balken", 3))
    # Stern an der Spitze
    stern = {(13, 0): 3, (12, 1): 3, (13, 1): 4, (14, 1): 3, (11, 2): 3, (12, 2): 4, (13, 2): 4, (14, 2): 4, (15, 2): 3,
             (12, 3): 3, (14, 3): 3, (11, 4): 1, (15, 4): 1}
    for (x, y), k in stern.items():
        put(x, y, rgb("gold", k))
    os.makedirs(ITEM_TEX, exist_ok=True)
    Image.fromarray(im, "RGBA").save(os.path.join(ITEM_TEX, "market_planner.png"))


def icon():
    """Mod-Icon 128 × 128: Bude mit Satteldach, Schnee, Lichterkette, Stern und Münze."""
    s = 128
    im = Image.new("RGBA", (s, s), (24, 30, 52, 255))
    d = ImageDraw.Draw(im)
    rnd = random.Random(5)
    for _ in range(40):
        d.point((rnd.randint(0, s - 1), rnd.randint(0, 70)), fill=(220, 230, 255))
    snow = (246, 250, 255)
    d.rectangle([0, 104, s, s], fill=snow)
    d.rectangle([22, 56, 106, 104], fill=rgb("fichte", 2))
    for y in range(60, 104, 10):
        d.line([(22, y), (106, y)], fill=rgb("balken", 0), width=2)
    d.rectangle([30, 70, 98, 92], fill=(40, 28, 18))
    d.rectangle([22, 86, 106, 94], fill=rgb("fichte", 3))
    d.polygon([(14, 58), (64, 22), (114, 58)], fill=rgb("rot", 3))
    for i in range(0, 100, 16):
        if i % 32 == 0:
            d.polygon([(14 + i, 58), (22 + i, 58), (64, 26)], fill=rgb("weiss", 3))
    d.polygon([(10, 60), (64, 20), (118, 60), (112, 62), (64, 28), (16, 62)], fill=snow)
    for i, x in enumerate(range(26, 104, 8)):
        d.ellipse([x - 2, 62, x + 2, 66], fill=LICHTFARBEN[i % 4])
    d.polygon([(64, 6), (68, 16), (78, 16), (70, 22), (73, 32), (64, 26), (55, 32), (58, 22), (50, 16), (60, 16)], fill=rgb("gold", 3))
    d.ellipse([78, 74, 98, 94], fill=rgb("gold", 3), outline=rgb("gold", 1), width=2)
    d.line([(88, 78), (88, 90)], fill=rgb("gold", 1), width=2)
    im.save(os.path.join(ROOT, "icon.png"))


ALLE = [verkaufstheke, standschild, ankaufstelle, auftragsbrett, losbude, adventskalender, geschenk]
ALT = ["market_counter_front", "market_counter_side", "market_counter_top", "stall_sign_post", "buyer_station_front",
       "buyer_station_side", "buyer_station_top", "job_board_post", "lottery_booth_front", "lottery_booth_side",
       "lottery_booth_top"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--vorschau", help="Ordner für Vorschaubilder")
    ap.add_argument("nur", nargs="*", help="nur diese Modelle (Name)")
    a = ap.parse_args()
    for fn in ALLE:
        m = fn()
        if a.nur and m.name not in a.nur:
            continue
        m.write()
    planer()
    icon()
    for n in ALT:
        p = os.path.join(BLOCK_TEX, n + ".png")
        if os.path.exists(p):
            os.remove(p)
    if a.vorschau:
        sys.path.insert(0, HIER)
        import modell_vorschau
        modell_vorschau.blatt([fn().name for fn in ALLE if not a.nur or fn().name in a.nur], a.vorschau)


if __name__ == "__main__":
    main()
