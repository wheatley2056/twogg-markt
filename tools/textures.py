#!/usr/bin/env python3
"""Erzeugt die 16×16-Texturen und das Mod-Icon von TwoGG Markt (Pixel-Art, deterministisch).

Aufruf aus dem Projektordner:  python3 tools/textures.py
"""
import os
import random
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "twogg_markt")
BLOCK = os.path.join(ROOT, "textures", "block")
ITEM = os.path.join(ROOT, "textures", "item")

# Palette
SPRUCE = [(84, 58, 36), (98, 68, 42), (110, 78, 48), (73, 50, 31)]
DARK = (52, 36, 22)
RED = (196, 48, 48)
RED_D = (150, 32, 36)
WHITE = (236, 232, 222)
GOLD = (232, 182, 72)
GOLD_D = (180, 130, 40)
GREEN = (52, 120, 70)
GREEN_D = (34, 86, 50)
CORK = [(176, 128, 82), (164, 116, 72), (186, 140, 92)]
PAPER = (240, 234, 214)
SNOW = (246, 250, 255)


def planks(rnd, base=SPRUCE):
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = base[(x * 7 + y * 3 + rnd.randint(0, 2)) % len(base)] if rnd.random() < 0.35 else base[1]
            px[x, y] = c + (255,)
        if y % 4 == 3:
            for x in range(16):
                px[x, y] = DARK + (255,)
    for y in range(16):
        off = (y // 4) * 5
        px[(off + 3) % 16, y] = DARK + (255,) if y % 4 != 3 else px[(off + 3) % 16, y]
    return im


def awning(im, rows=5):
    px = im.load()
    for y in range(rows):
        for x in range(16):
            px[x, y] = (RED if (x // 2) % 2 == 0 else WHITE) + (255,)
    # Zacken
    for x in range(16):
        if (x // 2) % 2 == 0:
            px[x, rows] = RED_D + (255,)
    for x in range(16):
        px[x, 0] = (RED_D if (x // 2) % 2 == 0 else (200, 196, 188)) + (255,)
    return im


def snowcap(im, rows=2):
    px = im.load()
    for x in range(16):
        h = rows + (1 if x % 5 in (1, 2) else 0)
        for y in range(h):
            px[x, y] = SNOW + (255,)
    return im


def save(im, *path):
    p = os.path.join(*path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    im.save(p)


def counter():
    rnd = random.Random(1)
    front = awning(planks(rnd))
    d = ImageDraw.Draw(front)
    # Tresen-Kante und kleine Lichterkette
    d.line([(0, 9), (15, 9)], fill=GOLD_D)
    for x in range(1, 16, 3):
        front.putpixel((x, 7), [(255, 220, 90), (120, 220, 255), (255, 110, 110)][x % 3] + (255,))
    side = awning(planks(rnd))
    d = ImageDraw.Draw(side)
    d.line([(0, 9), (15, 9)], fill=GOLD_D)
    top = snowcap(planks(rnd), 1)
    save(front, BLOCK, "market_counter_front.png")
    save(side, BLOCK, "market_counter_side.png")
    save(top, BLOCK, "market_counter_top.png")


def sign():
    rnd = random.Random(2)
    im = Image.new("RGBA", (16, 16), RED + (255,))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 15], outline=GOLD_D)
    d.rectangle([1, 1, 14, 14], outline=GOLD)
    # Stern
    for x, y in [(7, 3), (8, 3), (6, 4), (7, 4), (8, 4), (9, 4), (7, 5), (8, 5), (6, 6), (9, 6)]:
        im.putpixel((x, y), GOLD + (255,))
    # Schrift-Andeutung
    for x in range(4, 12):
        if x % 2 == 0:
            im.putpixel((x, 9), WHITE + (255,))
        im.putpixel((x, 11), (230, 200, 190, 255) if x % 3 else WHITE + (255,))
    im = snowcap(im, 1)
    save(im, BLOCK, "stall_sign.png")
    post = planks(rnd, [(70, 48, 30), (62, 42, 26), (78, 54, 34)])
    save(post, BLOCK, "stall_sign_post.png")


def buyer():
    rnd = random.Random(3)
    side = planks(rnd, [(122, 88, 54), (134, 98, 60), (112, 80, 48)])
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=DARK)
    d.line([(0, 0), (15, 15)], fill=DARK)
    d.line([(15, 0), (0, 15)], fill=DARK)
    front = planks(rnd, [(122, 88, 54), (134, 98, 60), (112, 80, 48)])
    d = ImageDraw.Draw(front)
    d.rectangle([0, 0, 15, 15], outline=DARK)
    # Waage
    d.line([(7, 3), (8, 3)], fill=GOLD)
    d.line([(7, 3), (7, 11)], fill=GOLD_D)
    d.line([(3, 5), (12, 5)], fill=GOLD)
    d.line([(2, 8), (5, 8)], fill=GOLD)
    d.line([(10, 8), (13, 8)], fill=GOLD)
    d.point([(3, 6), (4, 7), (12, 6), (11, 7)], fill=GOLD_D)
    d.line([(5, 12), (10, 12)], fill=GOLD)
    top = snowcap(planks(rnd, [(122, 88, 54), (134, 98, 60), (112, 80, 48)]), 2)
    save(front, BLOCK, "buyer_station_front.png")
    save(side, BLOCK, "buyer_station_side.png")
    save(top, BLOCK, "buyer_station_top.png")


def board():
    rnd = random.Random(4)
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = CORK[rnd.randint(0, 2)] + (255,)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 15], outline=(96, 64, 36))
    # Zettel
    for (x0, y0, w, h, pin) in [(2, 2, 5, 6, RED), (9, 3, 5, 5, GREEN), (3, 9, 6, 5, GOLD), (10, 10, 4, 4, (90, 140, 220))]:
        d.rectangle([x0, y0, x0 + w - 1, y0 + h - 1], fill=PAPER)
        for yy in range(y0 + 2, y0 + h - 1, 2):
            d.line([(x0 + 1, yy), (x0 + w - 2, yy)], fill=(170, 160, 140))
        d.point([(x0 + w // 2, y0)], fill=pin)
    save(im, BLOCK, "job_board.png")
    post = planks(rnd, [(70, 48, 30), (62, 42, 26), (78, 54, 34)])
    save(post, BLOCK, "job_board_post.png")


def planner():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # Messlatte diagonal, rot/weiß, goldene Spitze
    for i in range(12):
        x, y = 2 + i, 13 - i
        c = RED if (i // 2) % 2 == 0 else WHITE
        im.putpixel((x, y), c + (255,))
        if x + 1 < 16:
            im.putpixel((x + 1, y), (RED_D if (i // 2) % 2 == 0 else (190, 186, 178)) + (255,))
    for x, y in [(13, 1), (14, 1), (14, 2), (13, 2), (12, 0), (15, 3)]:
        im.putpixel((x, y), GOLD + (255,))
    for x, y in [(1, 14), (2, 14), (1, 13)]:
        im.putpixel((x, y), DARK + (255,))
    save(im, ITEM, "market_planner.png")


def icon():
    s = 128
    im = Image.new("RGBA", (s, s), (24, 30, 52, 255))
    d = ImageDraw.Draw(im)
    # Schnee am Boden, Sterne
    rnd = random.Random(5)
    for _ in range(40):
        x, y = rnd.randint(0, s - 1), rnd.randint(0, 70)
        d.point((x, y), fill=(220, 230, 255))
    d.rectangle([0, 104, s, s], fill=SNOW)
    # Bude
    d.rectangle([22, 56, 106, 104], fill=SPRUCE[1])
    for y in range(60, 104, 10):
        d.line([(22, y), (106, y)], fill=DARK, width=2)
    d.rectangle([30, 70, 98, 92], fill=(40, 28, 18))
    d.rectangle([22, 86, 106, 94], fill=SPRUCE[2])
    # Dach (Satteldach) mit Schnee
    d.polygon([(14, 58), (64, 22), (114, 58)], fill=RED)
    for i in range(0, 100, 16):
        d.polygon([(14 + i, 58), (22 + i, 58), (64, 26)], fill=WHITE) if i % 32 == 0 else None
    d.polygon([(10, 60), (64, 20), (118, 60), (112, 62), (64, 28), (16, 62)], fill=SNOW)
    # Lichterkette
    colors = [(255, 220, 90), (120, 220, 255), (255, 110, 110), (140, 255, 140)]
    for i, x in enumerate(range(26, 104, 8)):
        d.ellipse([x - 2, 62, x + 2, 66], fill=colors[i % 4])
    # Stern oben
    d.polygon([(64, 6), (68, 16), (78, 16), (70, 22), (73, 32), (64, 26), (55, 32), (58, 22), (50, 16), (60, 16)], fill=GOLD)
    # Münze
    d.ellipse([78, 74, 98, 94], fill=GOLD, outline=GOLD_D, width=2)
    d.line([(88, 78), (88, 90)], fill=GOLD_D, width=2)
    save(im, ROOT, "icon.png")


if __name__ == "__main__":
    counter()
    sign()
    buyer()
    board()
    planner()
    icon()
    print("Texturen geschrieben nach", os.path.normpath(ROOT))
