#!/usr/bin/env python3
"""Grafiken des Markt-Handbuchs (eigenes Buch, seit 0.3.0).

1. Buch (textures/gui/handbuch/buch.png, 512 × 320): roter Ledereinband mit Goldecken, zwei Pergamentseiten mit
   Schatten am Bund – deterministisch erzeugt.
2. Bilder aus dem Spiel: aus den Screenshots des Spieltests (build/run/clientGameTest/screenshots, ohne Meldungen/Chat,
   Weltbilder ohne HUD – MarktClientTest.shot) werden 16:9-Ausschnitte in 384 × 216 (textures/gui/handbuch/<name>.png).

Aufruf aus dem Projektordner (nach ./gradlew runClientGameTest):  python3 tools/handbuch_bilder.py
"""
import glob
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "twogg_markt", "textures", "gui", "handbuch")
SHOTS = os.path.join(ROOT, "build", "run", "clientGameTest", "screenshots")
W, H = 384, 216

# Bildname im Handbuch → Screenshot des Spieltests (Name ohne laufende Nummer)
BILDER = {
    "markt": "markt-24-hologramme",
    "umrisse": "markt-17-umrisse",
    "staende": "markt-02-staende",
    "mein_stand": "markt-04-mein-stand",
    "theke_verwalten": "markt-08-theke-verwalten",
    "theke_kaufen": "markt-14b-theke-kaeufer",
    "ankauf": "markt-09-ankauf",
    "auftragsbrett": "markt-11-auftragsbrett",
    "aushaengen": "markt-12-aushaengen",
    "eroeffnung": "markt-18-event-eroeffnung",
    "losbude": "markt-19-losbude",
    "wichteln": "markt-21-wichteln",
    "advent": "markt-22-adventskalender",
    "event": "markt-23-event-seite",
}


def buch():
    rnd = random.Random(7)
    im = Image.new("RGBA", (512, 320), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # Einband: dunkelrotes Leder mit Körnung, abgerundet
    d.rounded_rectangle((0, 0, 511, 319), radius=14, fill=(92, 22, 26, 255))
    px = im.load()
    for y in range(320):
        for x in range(512):
            r, g, b, a = px[x, y]
            if a:
                n = rnd.randint(-9, 9)
                px[x, y] = (max(0, min(255, r + n)), max(0, min(255, g + n // 2)), max(0, min(255, b + n // 2)), a)
    d.rounded_rectangle((0, 0, 511, 319), radius=14, outline=(52, 10, 14, 255), width=3)
    d.rounded_rectangle((5, 5, 506, 314), radius=11, outline=(196, 150, 64, 255), width=1)
    # Goldecken
    for (cx, cy, sx, sy) in ((10, 10, 1, 1), (501, 10, -1, 1), (10, 309, 1, -1), (501, 309, -1, -1)):
        pts = [(cx, cy), (cx + 22 * sx, cy), (cx, cy + 22 * sy)]
        d.polygon(pts, fill=(214, 168, 72, 255), outline=(150, 108, 36, 255))
    # Seiten (links 16…254, rechts 258…496; oben 12, unten 306), Pergament mit Rauschen
    page = Image.new("RGBA", (512, 320), (0, 0, 0, 0))
    pd = ImageDraw.Draw(page)
    pd.rectangle((16, 12, 254, 306), fill=(243, 233, 210, 255))
    pd.rectangle((258, 12, 496, 306), fill=(243, 233, 210, 255))
    pp = page.load()
    for y in range(12, 307):
        for x in list(range(16, 255)) + list(range(258, 497)):
            r, g, b, a = pp[x, y]
            n = rnd.randint(-5, 5)
            # Schatten zum Bund (Mitte) und leicht zu den Rändern
            bund = max(0, 26 - abs(x - 256)) * 2.2
            rand = max(0, 6 - min(x - 16, 496 - x, y - 12, 306 - y)) * 3
            k = int(bund + rand)
            pp[x, y] = (max(0, r + n - k), max(0, g + n - k), max(0, b + n - int(k * 1.2)), a)
    im.alpha_composite(page)
    # Seitenkanten (Blattstapel) unten
    for i in range(3):
        d.line((18, 307 + i, 252, 307 + i), fill=(222, 210, 184 - i * 10, 255))
        d.line((260, 307 + i, 494, 307 + i), fill=(222, 210, 184 - i * 10, 255))
    # Bund
    d.line((255, 12, 255, 309), fill=(120, 96, 70, 255))
    d.line((257, 12, 257, 309), fill=(120, 96, 70, 255))
    d.line((256, 10, 256, 311), fill=(70, 50, 34, 255))
    os.makedirs(OUT, exist_ok=True)
    im.save(os.path.join(OUT, "buch.png"))
    print("buch.png")


def bilder():
    if not os.path.isdir(SHOTS):
        print("keine Screenshots – erst ./gradlew runClientGameTest")
        return
    for name, shot in BILDER.items():
        files = sorted(glob.glob(os.path.join(SHOTS, "*_" + shot + ".png")))
        if not files:
            print("fehlt:", shot)
            continue
        src = Image.open(files[-1]).convert("RGB")
        sw, sh = src.size
        # auf 16:9 zuschneiden (mittig), dann verkleinern
        tw = min(sw, int(sh * 16 / 9))
        th = int(tw * 9 / 16)
        x0, y0 = (sw - tw) // 2, (sh - th) // 2
        img = src.crop((x0, y0, x0 + tw, y0 + th)).resize((W, H), Image.LANCZOS)
        img = img.filter(ImageFilter.UnsharpMask(radius=1, percent=40, threshold=2))
        img.save(os.path.join(OUT, name + ".png"))
        print(name + ".png ←", os.path.basename(files[-1]))


if __name__ == "__main__":
    buch()
    bilder()
