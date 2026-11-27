#!/usr/bin/env python3
"""Item-Bild des Markt-Handbuchs (16 × 16, Pixel-Art, deterministisch): rotes Buch mit Goldecken, Tannenzweig und
Schneeflocke auf dem Deckel, helle Seiten unten. Aufruf: python3 tools/handbuch_icon.py"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "twogg_markt", "textures", "item",
                   "handbuch.png")
R, RD, RL = (178, 40, 44), (120, 24, 30), (206, 64, 60)
G, GD = (232, 182, 72), (176, 126, 40)
P, PD = (240, 232, 208), (200, 188, 160)
T, TD = (52, 120, 70), (30, 82, 46)
W = (246, 250, 255)
O = (58, 20, 22)

im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = im.load()
# Umriss und Deckel (leicht schräg wie Vanilla-Bücher: x 2…13, y 1…13)
for y in range(1, 14):
    for x in range(2, 14):
        px[x, y] = R
for y in range(1, 14):
    px[2, y] = RD          # Buchrücken links dunkel
    px[3, y] = RL
for x in range(2, 14):
    px[x, 1] = RL
    px[x, 13] = RD
# Seiten unten
for x in range(3, 14):
    px[x, 14] = P if x % 3 else PD
px[2, 14] = RD
# Umriss
for x in range(2, 14):
    px[x, 0] = O
    px[x, 15] = O
for y in range(0, 16):
    px[1, y] = O
    px[14, y] = O
# Goldecken
for (x, y) in ((12, 2), (13, 2), (13, 3), (12, 12), (13, 12), (13, 11), (4, 2), (4, 12)):
    px[x, y] = G
px[13, 13] = GD
# Titelschild
for x in range(6, 12):
    px[x, 4] = G
    px[x, 6] = GD
px[6, 5] = G
px[11, 5] = G
for x in range(7, 11):
    px[x, 5] = P
# Tannenzweig mit Beeren
for (x, y) in ((6, 9), (7, 8), (8, 9), (9, 8), (10, 9), (7, 10), (9, 10)):
    px[x, y] = T
px[8, 10] = TD
px[8, 8] = (200, 30, 40)
px[9, 9] = (200, 30, 40)
# Schneeflocke oben rechts
px[11, 8] = W
px[11, 10] = W
px[10, 9] = W
px[12, 9] = W
os.makedirs(os.path.dirname(OUT), exist_ok=True)
im.save(OUT)
print(OUT)
