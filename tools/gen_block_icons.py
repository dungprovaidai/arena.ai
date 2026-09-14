"""
Inventory icons for machine / decorative blocks.

Building blocks use their block model as the item model (standard Minecraft behaviour),
but machines need hand-made 16x16 icons - a textured isometric cube loses all its
detail at inventory scale. Faces are texture-mapped from the real block atlas so an
icon always matches the block you place.

Run:  python3 tools/gen_block_icons.py
"""
from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image  # noqa: E402
from pixelart import Canvas, mix, darker, lighter  # noqa: E402
from gen_blocks import ATLAS  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK_TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "pathwaysofthebeyond",
                         "textures", "block")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "pathwaysofthebeyond",
                   "textures", "item")


def load(name):
    return Image.open(os.path.join(BLOCK_TEX, f"{name}.png")).convert("RGBA")


def atlas_px(img, cell):
    """8x8 pixel getter for a named cell of a 32x32 machine atlas."""
    col, row = ATLAS[cell]

    def get(u, v):
        return img.getpixel((col * 8 + (u % 8), row * 8 + (v % 8)))
    return get


def face_quad(cv, origin, du, dv, sample, size=16, shade=1.0, contrast=1.0):
    """Texture-map a square onto an isometric face.

    origin: screen position of texture uv (0,0); du/dv: screen delta for one full texture
    edge. Pixels are written with a 2x2 supersample to close isometric seams.
    """
    ox, oy = origin
    for v in range(size):
        for u in range(size):
            fx, fy = u / (size - 1), v / (size - 1)
            x = ox + du[0] * fx + dv[0] * fy
            y = oy + du[1] * fx + dv[1] * fy
            col = sample(u, v)
            cr, cg, cb = col[0], col[1], col[2]
            if contrast != 1.0:   # push value away from mid-grey so small icons read
                cr = 128 + (cr - 128) * contrast
                cg = 128 + (cg - 128) * contrast
                cb = 128 + (cb - 128) * contrast
            col = (max(0, min(255, int(cr * shade))),
                   max(0, min(255, int(cg * shade))),
                   max(0, min(255, int(cb * shade))), 255)
            cv.set(int(round(x)), int(round(y)), col)


def iso_cube(cv, top_img, left_img, right_img, size=16, top_cell=None, side_cell=None):
    """Standard Minecraft-style isometric block icon."""
    if top_cell:
        s_top = atlas_px(top_img, top_cell)
    else:
        def s_top(u, v):
            return top_img.getpixel((u, v))
    if side_cell:
        s_side = atlas_px(left_img, side_cell)
        s_right = atlas_px(right_img, side_cell)
    else:
        def s_side(u, v):
            return left_img.getpixel((u, v))

        def s_right(u, v):
            return right_img.getpixel((u, v))
    # top face: vertices (8,0) (16,4) (8,8) (0,4)
    face_quad(cv, (8, 0), (8, 4), (-8, 4), s_top, size, 1.18, 1.35)
    # left face: (0,4) (8,8) (8,16) (0,12)
    face_quad(cv, (0, 4), (8, 4), (0, 8), s_side, size, 0.88, 1.30)
    # right face: (8,8) (16,4) (16,12) (8,16)
    face_quad(cv, (8, 8), (8, -4), (0, 8), s_right, size, 0.62, 1.25)
    # crisp silhouette
    cv.line(8, 0, 16, 4, "void", 0.55)
    cv.line(16, 4, 16, 12, "void", 0.55)
    cv.line(16, 12, 8, 16, "void", 0.55)
    cv.line(8, 16, 0, 12, "void", 0.55)
    cv.line(0, 12, 0, 4, "void", 0.55)
    cv.line(0, 4, 8, 0, "void", 0.55)
    cv.line(8, 0, 8, 8, "void", 0.30)
    cv.line(0, 4, 8, 8, "void", 0.30)
    cv.line(16, 4, 8, 8, "void", 0.30)


def icon_gothic_bricks(cv):
    iso_cube(cv, load("gothic_bricks"), load("gothic_bricks"), load("gothic_bricks"))


def icon_ritual_altar(cv):
    """Stepped plinth, slab, gem socket, blood channel - readable in 16x16."""
    stone = load("ritual_altar")
    st = atlas_px(stone, "stone")
    st_l = atlas_px(stone, "stone_light")
    st_d = atlas_px(stone, "stone_dark")
    brass = atlas_px(stone, "brass")
    gem = atlas_px(stone, "gem_socket")
    blood = atlas_px(stone, "blood")
    carved = atlas_px(stone, "carved")

    # base plinth (a shorter iso box)
    face_quad(cv, (8, 5), (8, 4), (-8, 4), st, 16, 1.05)          # top
    face_quad(cv, (0, 9), (8, 4), (0, 4), carved, 16, 0.85)       # left
    face_quad(cv, (8, 13), (8, -4), (0, 4), carved, 16, 0.62)     # right
    # brass band
    face_quad(cv, (0, 10), (8, 4), (0, 1), brass, 16, 1.15)
    face_quad(cv, (8, 14), (8, -4), (0, 1), brass, 16, 0.85)
    # altar slab sitting on top
    face_quad(cv, (8, 2), (8, 4), (-8, 4), st_l, 16, 1.0)
    face_quad(cv, (0, 6), (8, 4), (0, 3), st, 16, 0.8)
    face_quad(cv, (8, 10), (8, -4), (0, 3), st_d, 16, 0.6)
    # gem socket + blood channel on the slab
    for i, (gx, gy) in enumerate([(7, 4), (8, 4), (7, 5), (8, 5)]):
        cv.set(gx, gy, gem(i, i))
    cv.set(8, 5, (255, 255, 255, 255))
    for i in range(4):
        cv.set(5 + i, 6 - i // 2, blood(i, i))
    cv.set(9, 4, (30, 30, 30, 255))
    cv.outline("void")


def icon_ritual_candle(cv):
    load_c = load("ritual_candle")
    wax = atlas_px(load_c, "candle_wax")
    brass = atlas_px(load_c, "brass")
    flame = atlas_px(load_c, "flame")
    # brass holder
    face_quad(cv, (8, 10), (8, 4), (-8, 4), brass, 16, 1.1)
    face_quad(cv, (4, 13), (4, 2), (0, 3), brass, 8, 0.8)
    face_quad(cv, (8, 15), (4, -2), (0, 3), brass, 8, 0.6)
    # wax column
    for y in range(3, 12):
        for x in range(6, 10):
            cv.set(x, y, wax((x - 6) * 2, y))
    cv.line(6, 3, 6, 11, "pale", 0.5)
    cv.line(9, 3, 9, 11, "parch_lo", 0.6)
    # wick + flame
    cv.set(7, 2, "charcoal")
    for (x, y) in ((7, 0), (8, 0), (7, 1), (8, 1), (6, 1), (9, 1)):
        cv.set(x, y, flame(3, 3))
    cv.set(7, 0, "white")
    cv.glow("gold", radius=1, alpha=0.45)
    cv.outline("void")


def icon_ritual_pedestal(cv):
    ped = load("ritual_pedestal")
    st = atlas_px(ped, "stone")
    st_l = atlas_px(ped, "stone_light")
    brass = atlas_px(ped, "brass")
    gem = atlas_px(ped, "gem_socket")
    face_quad(cv, (8, 8), (8, 4), (-8, 4), st_l, 16, 1.0)     # top
    face_quad(cv, (0, 12), (8, 4), (0, 4), st, 16, 0.8)
    face_quad(cv, (8, 16), (8, -4), (0, 4), st, 16, 0.55)
    for y in range(5, 12):                                     # column
        for x in range(6, 10):
            cv.set(x, y, st((x - 6) * 3, y))
    cv.rect(5, 10, 10, 11, brass(2, 2))
    cv.rect(6, 4, 9, 5, gem(2, 2))
    cv.set(7, 4, "veil")
    cv.line(6, 5, 6, 11, "stone", 0.45)
    cv.glow("violet", radius=1, alpha=0.22)
    cv.outline("void")


def icon_occult_table(cv):
    """Occult work table: wood top, leather inlay, brass drawer, open codex on top."""
    tab = load("occult_table")
    wood = atlas_px(tab, "wood")
    wood_d = atlas_px(tab, "wood_dark")
    leather = atlas_px(tab, "leather")
    parch = atlas_px(tab, "parchment")
    brass = atlas_px(tab, "brass")
    # legs first so the top overlaps them
    cv.rect(2, 10, 3, 15, wood_d(2, 2))
    cv.rect(12, 10, 13, 15, wood_d(5, 5))
    face_quad(cv, (8, 5), (8, 4), (-8, 4), wood, 16, 1.15, 1.4)
    face_quad(cv, (0, 9), (8, 4), (0, 2.5), leather, 16, 0.9, 1.3)
    face_quad(cv, (8, 13), (8, -4), (0, 2.5), leather, 16, 0.6, 1.25)
    # brass drawer front on the left face
    cv.rect(4, 9, 7, 11, brass(2, 2))
    cv.rect(4, 9, 7, 9, brass(5, 5))
    cv.set(6, 10, (240, 220, 150, 255))
    # open codex on the tabletop
    cv.rect(3, 1, 7, 4, parch(1, 1))
    cv.rect(8, 1, 12, 4, parch(4, 1))
    cv.rect(3, 1, 12, 1, (236, 224, 190, 255))
    cv.line(7, 1, 7, 4, (90, 60, 40, 255))
    cv.line(8, 2, 11, 2, (150, 130, 95, 255), 0.8)
    cv.line(3, 3, 6, 3, (150, 130, 95, 255), 0.8)
    cv.set(9, 3, "orchid", 0.9)
    cv.set(8, 0, "brass_hi", 0.8)
    cv.outline("void")


def icon_blood_basin(cv):
    """Stone cistern seen in isometric: rim, dark stone walls, a small pool of blood."""
    bb = load("blood_basin")
    st = atlas_px(bb, "stone")
    st_l = atlas_px(bb, "stone_light")
    st_d = atlas_px(bb, "stone_dark")
    blood = atlas_px(bb, "blood")
    brass = atlas_px(bb, "brass")
    # outer box: rim (top), left wall, right wall
    face_quad(cv, (8, 4), (8, 4), (-8, 4), st_l, 16, 1.15, 1.35)
    face_quad(cv, (0, 8), (8, 4), (0, 6), st, 16, 0.9, 1.3)
    face_quad(cv, (8, 12), (8, -4), (0, 6), st_d, 16, 0.62, 1.25)
    # inner recess, one step in from the rim
    face_quad(cv, (8, 6), (5.2, 2.6), (-5.2, 2.6), st_d, 16, 0.95, 1.3)
    # the pool: darker than everything, with a wet highlight
    face_quad(cv, (8, 7), (3.6, 1.8), (-3.6, 1.8), blood, 16, 1.0, 1.45)
    cv.set(7, 6, (214, 92, 88, 255), 0.75)
    cv.set(9, 7, (120, 26, 30, 255))
    for (x, y) in ((3, 6), (12, 6), (3, 9), (12, 9)):
        cv.set(x, y, brass(2, 2))
        cv.set(x, y + 1, brass(5, 5), 0.7)
    cv.outline("void")


def icon_spirit_lantern(cv):
    ln = load("spirit_lantern")
    brass = atlas_px(ln, "brass")
    worn = atlas_px(ln, "brass_worn")
    gem = atlas_px(ln, "gem_socket")
    cv.rect(6, 0, 9, 1, brass(3, 3))          # top handle
    cv.set(7, 2, worn(1, 1))
    cv.rect(4, 2, 11, 3, worn(2, 2))
    cv.rect(4, 3, 5, 11, worn(1, 1))
    cv.rect(10, 3, 11, 11, worn(5, 5))
    cv.rect(4, 11, 11, 12, brass(2, 2))
    for y in range(4, 11):                    # the light inside
        for x in range(6, 10):
            cv.set(x, y, gem(x - 6, y - 4))
    cv.ellipse(7, 7, 1.4, 1.9, "spirit_hi")
    cv.set(7, 6, "white")
    cv.line(4, 3, 4, 11, "gold", 0.5)
    cv.rect(6, 13, 9, 15, brass(4, 4))
    cv.glow("cyan_mut", radius=1, alpha=0.45)
    cv.outline("void")


def icon_occult_archive(cv):
    ar = load("occult_archive")
    wood = atlas_px(ar, "wood")
    wood_d = atlas_px(ar, "wood_dark")
    leather = atlas_px(ar, "leather")
    parch = atlas_px(ar, "parchment")
    brass = atlas_px(ar, "brass")
    face_quad(cv, (8, 6), (8, 4), (-8, 4), wood_d, 16, 1.05)
    face_quad(cv, (0, 10), (8, 4), (0, 5), wood, 16, 0.8)
    face_quad(cv, (8, 14), (8, -4), (0, 5), wood, 16, 0.55)
    cv.rect(2, 3, 13, 5, leather(3, 3))       # top slab
    cv.rect(4, 4, 6, 5, brass(2, 2))          # clasps
    cv.rect(9, 4, 11, 5, brass(2, 2))
    cv.rect(6, 1, 9, 3, parch(2, 2))          # parchment label
    cv.line(7, 1, 7, 3, "brown", 0.7)
    cv.set(11, 12, "brass_lo", 0.8)
    cv.outline("void")


def icon_memory_shard_block(cv):
    iso_cube(cv, load("void_stone"), load("void_stone"), load("void_stone"))
    # the memory shard set into the front face: a shard of remembered light
    for (x, y) in ((7, 9), (8, 8), (9, 8), (8, 9), (7, 10), (8, 10), (9, 10),
                   (8, 11), (7, 9), (10, 9), (8, 7)):
        cv.set(x, y, "cyan_mut", 0.9)
    cv.set(8, 9, "spirit_hi")
    cv.set(7, 8, "blue_lo", 0.7)
    cv.glow("cyan_mut", radius=1, alpha=0.35)
    cv.outline("void")


def icon_chalk_circle(cv):
    """The chalk circle seen face-on, as it is drawn on a floor."""
    import math
    cv.ellipse(8, 8, 7.0, 5.4, "pale", 0.95, filled=False)
    for a in range(0, 360, 30):
        cv.set(int(8 + math.cos(math.radians(a)) * 6.9),
               int(8 + math.sin(math.radians(a)) * 5.3), "bone", 0.55)
    # pentagram, drawn star-order, kept inside the ring
    pts = [(8 + math.cos(math.radians(-90 + i * 144)) * 4.2,
            8 + math.sin(math.radians(-90 + i * 144)) * 3.1) for i in range(5)]
    for i in range(5):
        x0, y0 = pts[i]
        x1, y1 = pts[(i + 1) % 5]
        cv.line(int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1)), "pale", 0.9)
    for (px, py) in pts:
        cv.set(int(round(px)), int(round(py)), "bone", 0.9)
    cv.set(8, 8, "bone", 0.9)
    cv.noise("pale", 0.05, 0.3)
    cv.outline("void")


def icon_spirit_flower(cv):
    src = load("spirit_flower")
    cv.blend(Canvas(16, 16, seed=1) if False else _canvas_from(src))
    cv.glow("spirit", radius=1, alpha=0.30)
    cv.outline("void")


def icon_moonlit_fungus(cv):
    src = load("moonlit_fungus")
    cv.blend(_canvas_from(src))
    cv.glow("cyan_mut", radius=1, alpha=0.25)
    cv.outline("void")


def _canvas_from(img):
    c = Canvas(16, 16, seed=9)
    for y in range(16):
        for x in range(16):
            c.px[y * 16 + x] = img.getpixel((x, y))
    return c


ICONS = {
    "block_ritual_altar": icon_ritual_altar,
    "block_ritual_candle": icon_ritual_candle,
    "block_ritual_pedestal": icon_ritual_pedestal,
    "block_occult_table": icon_occult_table,
    "block_blood_basin": icon_blood_basin,
    "block_spirit_lantern": icon_spirit_lantern,
    "block_occult_archive": icon_occult_archive,
    "block_memory_shard_block": icon_memory_shard_block,
    "block_chalk_circle": icon_chalk_circle,
    "block_spirit_flower": icon_spirit_flower,
    "block_moonlit_fungus": icon_moonlit_fungus,
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, painter in ICONS.items():
        cv = Canvas(16, 16, seed=name)
        painter(cv)
        cv.save(os.path.join(OUT, f"{name}.png"))
    print(f"[block icons] wrote {len(ICONS)} icons -> {OUT}")


if __name__ == "__main__":
    main()
