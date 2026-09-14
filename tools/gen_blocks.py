"""
Block texture generator.

Two families:
  * BUILDING BLOCKS - 16x16, tileable, wear + dirt detail, muted Victorian palette.
  * MACHINES - 32x32 atlases made of 8px cells; the matching JSON models (gen_models.py)
    address cells with uv = cell * 4 (Minecraft UV space is 0-16 regardless of texture size).

Run:  python3 tools/gen_blocks.py
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from pixelart import Canvas, mix, darker, lighter  # noqa: E402
from content import BLOCKS  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "pathwaysofthebeyond",
                   "textures", "block")

# 8px cells in the 32x32 machine atlases -> named regions
ATLAS = {
    "stone": (0, 0), "stone_dark": (1, 0), "stone_light": (2, 0), "carved": (3, 0),
    "brass": (0, 1), "brass_dark": (1, 1), "brass_worn": (2, 1), "gem_socket": (3, 1),
    "wood": (0, 2), "wood_dark": (1, 2), "leather": (2, 2), "parchment": (3, 2),
    "candle_wax": (0, 3), "flame": (1, 3), "blood": (2, 3), "void": (3, 3),
}


def cell(cv: Canvas, name: str, painter, seed=0):
    col, row = ATLAS[name]
    sub = Canvas(8, 8, seed=f"{name}{seed}")
    painter(sub)
    cv.blend(sub, col * 8, row * 8)


# =======================================================================================
# building block textures (16x16, tileable)
# =======================================================================================
def run_bond(cv, brick, mortar, accent, bw=8, bh=4, seed=1, variance=0.18):
    """Running-bond brickwork with per-brick value variance and mortar shadow."""
    cv.fill(mortar)
    rng_shift = 0
    y = 0
    row = 0
    while y < cv.h:
        off = 0 if row % 2 == 0 else bw // 2
        x = -off
        while x < cv.w:
            v = ((x * 7 + y * 13 + seed * 31) % 11) / 11.0
            tone = mix(darker(brick, variance + 0.30), lighter(brick, 0.22), v)
            for by in range(bh):
                for bx in range(bw - 1):
                    xx, yy = x + bx + off, y + by
                    if 0 <= xx < cv.w and 0 <= yy < cv.h:
                        cv.set(xx, yy, tone)
            # top-left light edge, bottom shadow edge
            for bx in range(bw - 1):
                xx = x + bx + off
                if 0 <= xx < cv.w and 0 <= y < cv.h:
                    cv.set(xx, y, lighter(tone, 0.14))
            for bx in range(bw - 1):
                xx = x + bx + off
                if 0 <= xx < cv.w and 0 <= y + bh - 1 < cv.h:
                    cv.set(xx, y + bh - 1, darker(tone, 0.22))
            x += bw
        y += bh
        row += 1
    cv.noise(accent, 0.08, 0.30, seed=seed + 5)


def tex_gothic_bricks(variant=0):
    def paint(cv):
        base = "slate" if variant == 0 else "ash"
        run_bond(cv, base, "stone" if variant == 0 else "grey", "moss_lo" if variant == 0 else "brown",
                 seed=variant + 3)
        # damp patches and a little moss in the mortar
        for (x, y, w, h, c, a) in ((2, 4, 5, 3, "moss_lo", 0.25),
                                   (9, 10, 4, 4, "charcoal", 0.35),
                                   (11, 2, 4, 3, "moss", 0.18)):
            cv.rect(x, y, x + w, y + h, c, a)
        cv.wear(0.14, seed=variant + 17)
    return paint


def tex_desecrated_stone(variant=0):
    def paint(cv):
        cv.value_noise("charcoal", "slate", scale=4.5, alpha=0.9, seed=11 + variant)
        cv.noise("stone", 0.14, 0.35, seed=21)
        cv.noise("void", 0.20, 0.30, seed=31 + variant)
        # hairline cracks
        for (x0, y0, x1, y1) in ((1, 3, 6, 8), (9, 1, 14, 6), (3, 12, 8, 15), (11, 11, 15, 15)):
            cv.line(x0, y0, x1, y1, "void", 0.55)
            cv.set((x0 + x1) // 2, (y0 + y1) // 2, "stone", 0.35)
        # something was carved here and then chiselled off
        cv.rect(5, 5, 10, 9, "pitch", 0.45)
        cv.line(6, 6, 9, 8, "stone", 0.30)
        cv.line(9, 6, 6, 8, "stone", 0.30)
        for (bx, by, bw2, bh2) in ((0, 0, 7, 6), (8, 2, 8, 5), (2, 9, 6, 7), (10, 10, 6, 6)):
            tone = mix("ash", "stone", ((bx + by + variant) % 5) / 5.0)
            for yy in range(by, min(16, by + bh2)):
                for xx in range(bx, min(16, bx + bw2)):
                    cv.set(xx, yy, tone, 0.35)
                    if yy == by:
                        cv.set(xx, yy, lighter(tone, 0.25), 0.30)
                    if yy == by + bh2 - 1:
                        cv.set(xx, yy, darker(tone, 0.4), 0.35)
        cv.wear(0.20, seed=variant + 41)
    return paint


def tex_weathered_plaster(cv):
    """Victorian lime plaster: chalky off-white, a patch where it has fallen away,
    damp creeping up from the bottom, and hairline cracks."""
    cv.value_noise("parch", "parch_hi", scale=6.0, alpha=0.75, seed=7)
    cv.noise("bone", 0.07, 0.25, seed=8)
    # plaster that has fallen off, showing the brickwork under it
    cv.rect(2, 3, 7, 8, "void", 0.9)
    for by in range(2):
        for bx in range(3):
            tone = mix(darker("brown", 0.15), lighter("brown", 0.18), ((bx * 5 + by * 3) % 7) / 7.0)
            for yy in range(3 + by * 3, 3 + by * 3 + 2):
                for xx in range(2 + bx * 2, 2 + bx * 2 + 2):
                    cv.set(xx, yy, tone)
    cv.frame(2, 3, 7, 8, "parch_lo", 0.85)
    cv.set(2, 3, "bone", 0.5)
    # damp rising from the floor
    for y in range(cv.h):
        t = max(0.0, (y - 9) / 7.0)
        if t > 0:
            cv.rect(0, y, cv.w, y, "moss_lo", t * 0.35)
    for (x, y, r) in ((12, 12, 2), (4, 13, 2), (10, 5, 1)):
        cv.ellipse(x, y, r + 1, r, "moss", 0.25)
    # hairline cracks
    cv.line(9, 1, 11, 6, "parch_lo", 0.8)
    cv.line(11, 6, 10, 10, "parch_lo", 0.6)
    cv.poly([(13, 8), (15, 12), (13, 15)], "parch_lo", 0.35)
    cv.wear(0.10, seed=57)


def tex_dark_planks(cv):
    cv.fill("brown")
    for y in range(cv.h):
        board = y // 4
        tone = mix(darker("leather", 0.35), lighter("leather", 0.08), ((board * 5) % 7) / 7)
        cv.rect(0, y, cv.w, y, tone)
        if y % 4 == 0:
            cv.rect(0, y, cv.w, y, lighter(tone, 0.18))     # board top edge
        if y % 4 == 3:
            cv.rect(0, y, cv.w, y, darker(tone, 0.35))      # gap under the board
    # grain + plank end joints (staggered)
    joints = {0: (5, 13), 1: (2, 10), 2: (7, 15), 3: (4, 12)}
    for board, (j1, j2) in joints.items():
        for j in (j1, j2):
            cv.line(j, board * 4, j, board * 4 + 3, "umber", 0.7)
    for (x0, y0, x1, y1) in ((2, 1, 9, 1), (6, 5, 14, 5), (1, 9, 7, 9), (9, 13, 15, 13),
                             (3, 2, 8, 2), (7, 10, 12, 10)):
        cv.line(x0, y0, x1, y1, "umber", 0.45)
    cv.noise("umber", 0.10, 0.30, seed=64)
    # nail heads
    for (x, y) in ((3, 2), (11, 6), (5, 10), (13, 14)):
        cv.set(x, y, "grey")
        cv.set(x, y + 1, "void", 0.5)
    cv.wear(0.10, seed=66)


def tex_slate_tiles(cv):
    cv.fill("pitch")
    size = 8
    for ty in range(cv.h // size):
        for tx in range(cv.w // size):
            tone = mix("ash", "slate", ((tx * 3 + ty * 5) % 5) / 5.0)
            ox, oy = tx * size, ty * size
            cv.rect(ox, oy, ox + size - 1, oy + size - 1, tone)
            cv.rect(ox, oy, ox + size - 1, oy, lighter(tone, 0.22))
            cv.rect(ox, oy + size - 1, ox + size - 1, oy + size - 1, darker(tone, 0.3))
            cv.line(ox, oy, ox, oy + size - 1, darker(tone, 0.15))
            # slate grain
            cv.line(ox + 1, oy + 2, ox + size - 2, oy + 2, darker(tone, 0.12))
            cv.line(ox + 2, oy + 4, ox + size - 3, oy + 5, darker(tone, 0.10))
    cv.noise("charcoal", 0.12, 0.30, seed=71)
    for (x, y) in ((6, 6), (13, 11), (4, 13)):
        cv.rect(x, y, x + 2, y + 1, "moss_lo", 0.35)
    cv.wear(0.12, seed=73)


def tex_iron_grate(cv):
    cv.fill("void")
    for x in range(cv.w):
        for y in range(cv.h):
            bar = (x % 5 in (0, 1)) or (y % 5 in (0, 1))
            if not bar:
                continue
            t = ((x * 3 + y * 7) % 6) / 6.0
            c = mix("greys" if False else "slate" if False else "stone", "grey", t)
            cv.set(x, y, c)
            if x % 5 == 0 and y % 5 != 0:
                cv.set(x, y, lighter(c, 0.18))
            if y % 5 == 0:
                cv.set(x, y, darker(c, 0.25))
    for (x, y) in ((0, 0), (5, 0), (10, 0), (15, 0), (0, 5), (5, 5), (10, 5), (15, 5),
                   (0, 10), (5, 10), (10, 10), (15, 10), (0, 15), (5, 15), (10, 15), (15, 15)):
        cv.set(x, y, "brass_lo", 0.6)
    cv.noise("rust", 0.06, 0.35)
    cv.wear(0.10, seed=81)


def tex_veil_glass(cv):
    cv.fill("cyan_mut", 0.06)
    # lead cames: a diamond lattice with a faint engraved sigil
    cv.line(0, 8, 8, 0, "slate", 0.9)
    cv.line(8, 0, 15, 7, "slate", 0.9)
    cv.line(15, 7, 8, 15, "slate", 0.9)
    cv.line(8, 15, 0, 8, "slate", 0.9)
    cv.line(0, 0, 15, 15, "void", 0.35)
    cv.line(15, 0, 0, 15, "void", 0.25)
    cv.ellipse(8, 8, 4.5, 4.5, "spirit", 0.20, filled=False)
    for (x, y) in ((8, 3), (13, 8), (8, 13), (3, 8)):
        cv.set(x, y, "spirit", 0.7)
    cv.noise("spirit", 0.05, 0.25)
    return cv


def tex_void_stone(cv):
    cv.value_noise("void", "charcoal", scale=3.5, alpha=0.95, seed=91)
    cv.noise("pitch", 0.22, 0.45, seed=92)
    # violet faults running through the rock
    for (x0, y0, x1, y1) in ((0, 4, 6, 9), (6, 9, 11, 3), (11, 3, 15, 8), (3, 14, 9, 11), (9, 11, 15, 14)):
        cv.line(x0, y0, x1, y1, "violet_lo", 0.85)
        cv.line(x0, min(15, y0 + 1), x1, min(15, y1 + 1), "violet", 0.35)
    cv.set(6, 9, "orchid", 0.8)
    cv.set(11, 3, "orchid", 0.6)
    cv.wear(0.16, seed=93)
    return cv


def tex_spirit_flower(cv):
    cv.fill(0x000000, 0.0)
    for y in range(cv.h):
        for x in range(cv.w):
            cv.px[y * cv.w + x] = (0, 0, 0, 0)
    cv.line(7, 15, 7, 8, "moss")
    cv.line(8, 15, 8, 8, "moss_hi")
    cv.line(7, 11, 4, 9, "moss")
    cv.line(7, 12, 11, 10, "moss")
    for (x, y) in ((7, 5), (4, 7), (10, 7), (6, 9), (9, 9)):
        cv.ellipse(x, y, 1.7, 1.7, "spirit")
    cv.ellipse(7, 7, 1.3, 1.3, "spirit_hi")
    cv.set(7, 5, "white")
    cv.glow("spirit", radius=1, alpha=0.28)
    return cv


def tex_moonlit_fungus(cv):
    for y in range(cv.h):
        for x in range(cv.w):
            cv.px[y * cv.w + x] = (0, 0, 0, 0)
    cv.rect(7, 11, 8, 15, "bone")
    cv.rect(7, 11, 7, 15, "pale")
    cv.poly([(1, 12), (4, 5), (11, 5), (14, 12)], "blue")
    cv.poly([(2, 11), (5, 7), (10, 7), (12, 11)], "blue_lo")
    cv.poly([(4, 10), (6, 6), (9, 6), (10, 10)], "cyan_mut")
    for (x, y) in ((5, 9), (9, 10), (11, 9), (6, 7), (9, 7)):
        cv.set(x, y, "spirit")
    cv.set(5, 6, "pale")
    cv.glow("cyan_mut", radius=1, alpha=0.24)
    return cv


def tex_memory_shard(cv):
    cv.value_noise("abyss", "navy", scale=5.0, alpha=0.9, seed=101)
    cv.noise("blue_lo", 0.16, 0.4, seed=102)
    # a shard of remembered light frozen in the rock
    cv.poly([(8, 3), (12, 8), (8, 13), (4, 8)], "blue_lo")
    cv.poly([(8, 4), (11, 8), (8, 12), (5, 8)], "cyan_mut")
    cv.ellipse(8, 8, 1.4, 1.8, "spirit_hi")
    cv.glow("cyan_mut", radius=1, alpha=0.22)
    cv.wear(0.12, seed=103)
    return cv


# =======================================================================================
# machine atlases (32x32, 8px cells)
# =======================================================================================
def stone_cell(variant="stone"):
    def paint(cv):
        cv.value_noise("charcoal", "stone", scale=3.0, alpha=0.95, seed=hash(variant) % 99)
        if variant == "stone_light":
            cv.value_noise("slate", "grey", scale=3.0, alpha=0.85, seed=hash(variant) % 99)
        if variant == "stone_dark":
            cv.value_noise("void", "charcoal", scale=3.0, alpha=0.9, seed=hash(variant) % 99)
        cv.noise("void", 0.18, 0.3)
        cv.wear(0.16, seed=hash(variant) % 57)
    return paint


def carved_cell(cv):
    """Engraved ritual sealing mark - the mod's signature glyph, reused on many blocks."""
    cv.value_noise("charcoal", "slate", scale=3.0, alpha=0.9, seed=3)
    for (x, y) in [(4, 0), (3, 1), (2, 2), (2, 3), (2, 4), (2, 5), (2, 6), (3, 7),
                   (5, 0), (6, 1), (6, 2), (5, 3), (5, 4), (5, 5), (6, 6), (6, 7)]:
        cv.set(x, y, "brass_lo", 0.85)
    cv.ellipse(4, 4, 2.6, 3.0, "brass_lo", 0.30, filled=False)
    cv.set(4, 4, "brass", 0.9)
    cv.wear(0.14, seed=9)


def brass_cell(variant="brass"):
    def paint(cv):
        base = {"brass": "brass", "brass_dark": "brass_lo", "brass_worn": "brass_hi"}[variant]
        cv.value_noise(darker(base, 0.35), base, scale=3.4, alpha=0.9, seed=hash(variant) % 31)
        cv.rect(0, 0, 7, 0, lighter(base, 0.35))
        cv.rect(0, 7, 7, 7, darker(base, 0.4))
        for (x, y) in ((1, 1), (6, 1), (1, 6), (6, 6)):
            cv.set(x, y, "gold" if variant == "brass" else "brass_lo", 0.8)
        # pin-striped engraving
        for x in range(0, 8, 3):
            cv.line(x, 2, x, 5, darker(base, 0.3), 0.5)
        cv.noise("void", 0.10, 0.25)
        if variant == "brass_worn":
            cv.noise("moss_lo", 0.12, 0.35)   # verdigris
    return paint


def gem_socket(cv):
    cv.value_noise("void", "charcoal", scale=2.6, alpha=0.95, seed=13)
    cv.ellipse(4, 4, 3.4, 3.4, "brass_lo")
    cv.ellipse(4, 4, 2.6, 2.6, "brass")
    cv.ellipse(4, 4, 1.8, 1.8, "violet_lo")
    cv.ellipse(4, 4, 1.2, 1.2, "orchid")
    cv.set(4, 3, "veil")
    cv.glow("violet", radius=1, alpha=0.30)


def wood_cell(variant="wood"):
    def paint(cv):
        if variant == "parchment":
            cv.value_noise("parch_lo", "parch_hi", scale=3.0, alpha=0.85, seed=17)
            for y in range(1, 7, 2):
                cv.line(1, y, 6, y, "parch_lo", 0.45)
            cv.set(1, 1, "brown", 0.5)
            cv.set(6, 6, "brown", 0.4)
            return
        if variant == "leather":
            cv.value_noise("umber", "leather", scale=3.0, alpha=0.9, seed=19)
            cv.noise("brown", 0.20, 0.30)
            cv.rect(0, 0, 7, 0, "leather")
            cv.rect(0, 7, 7, 7, "umber")
            return
        cv.value_noise("umber", "tan", scale=3.2, alpha=0.9, seed=23)
        for y in (1, 4, 6):
            cv.line(0, y, 7, y, darker("tan", 0.45), 0.75)
        for y in (0, 3, 5):
            cv.line(0, y, 7, y, lighter("tan", 0.25), 0.5)
        cv.noise("umber", 0.12, 0.3)
    return paint


def blood_cell(cv):
    cv.value_noise("blood_lo", "blood", scale=3.0, alpha=0.9, seed=29)
    cv.ellipse(4, 4, 3.0, 3.0, "blood")
    cv.ellipse(4, 4, 2.0, 2.0, "crimson")
    cv.set(4, 4, "rust", 0.8)
    cv.line(0, 2, 7, 3, "blood_lo", 0.8)
    cv.line(1, 6, 7, 5, "blood_lo", 0.7)


def candle_cell(cv):
    cv.value_noise("parch", "bone", scale=3.0, alpha=0.9, seed=37)
    cv.rect(0, 0, 7, 0, "pale")
    cv.rect(7, 0, 7, 7, "parch_lo")
    cv.rect(0, 7, 7, 7, "parch_lo")


def flame_cell(cv):
    cv.fill("void", 0.9)
    cv.ellipse(4, 5, 2.2, 3.0, "brass")
    cv.ellipse(4, 4, 1.4, 2.2, "gold")
    cv.ellipse(4, 5, 0.9, 1.4, "white")


def void_cell(cv):
    cv.value_noise("void", "pitch", scale=3.0, alpha=1.0, seed=41)
    cv.noise("violet_lo", 0.14, 0.5, seed=43)
    cv.set(3, 3, "orchid", 0.7)
    cv.set(5, 5, "violet", 0.5)


def make_atlas(cells):
    cv = Canvas(32, 32, seed=sum(hash(k) for k in cells) % 9999)
    painters = {
        "stone": stone_cell("stone"), "stone_dark": stone_cell("stone_dark"),
        "stone_light": stone_cell("stone_light"), "carved": carved_cell,
        "brass": brass_cell("brass"), "brass_dark": brass_cell("brass_dark"),
        "brass_worn": brass_cell("brass_worn"), "gem_socket": gem_socket,
        "wood": wood_cell("wood"), "wood_dark": wood_cell("wood"),
        "leather": wood_cell("leather"), "parchment": wood_cell("parchment"),
        "candle_wax": candle_cell, "flame": flame_cell, "blood": blood_cell, "void": void_cell,
    }
    for name in cells:
        if name == "wood_dark":
            cell(cv, name, lambda c: (wood_cell("wood")(c), c.rect(0, 0, 7, 7, "void", 0.45)))
        else:
            cell(cv, name, painters[name])
    return cv


# machine -> which atlas cells it needs
MACHINE_ATLAS = {
    "ritual_altar": ["stone", "stone_dark", "stone_light", "carved", "brass", "brass_worn",
                     "gem_socket", "blood", "void"],
    "ritual_pedestal": ["stone", "stone_dark", "carved", "brass", "gem_socket", "brass_worn"],
    "ritual_candle": ["candle_wax", "flame", "brass", "blood", "stone_dark"],
    "blood_basin": ["stone_dark", "stone", "carved", "blood", "brass", "void"],
    "spirit_lantern": ["brass_worn", "brass", "stone_dark", "gem_socket", "void"],
    "occult_archive": ["wood", "wood_dark", "leather", "parchment", "brass", "carved"],
    "occult_table": ["wood", "leather", "parchment", "brass", "carved", "stone_dark"],
}

TILEABLE = {
    "gothic_bricks": tex_gothic_bricks(0),
    "gothic_brick_alternate": tex_gothic_bricks(1),
    "desecrated_stone": tex_desecrated_stone(0),
    "desecrated_stone_alternate": tex_desecrated_stone(1),
    "weathered_plaster": tex_weathered_plaster,
    "dark_planks": tex_dark_planks,
    "slate_tiles": tex_slate_tiles,
    "iron_grate": tex_iron_grate,
    "veil_glass": tex_veil_glass,
    "void_stone": tex_void_stone,
    "spirit_flower": tex_spirit_flower,
    "moonlit_fungus": tex_moonlit_fungus,
    "memory_shard_block_face": tex_memory_shard,
}


def main():
    os.makedirs(OUT, exist_ok=True)
    n = 0
    for name, painter in TILEABLE.items():
        cv = Canvas(16, 16, seed=name)
        painter(cv)
        cv.save(os.path.join(OUT, f"{name}.png"))
        n += 1
    for name, cells in MACHINE_ATLAS.items():
        cv = make_atlas(cells)
        cv.save(os.path.join(OUT, f"{name}.png"))
        n += 1
    # chalk circle decal: transparent 32x32, hand-drawn chalk occult circle
    cv = Canvas(32, 32, seed="chalk")
    for y in range(32):
        for x in range(32):
            cv.px[y * 32 + x] = (0, 0, 0, 0)
    cx = cy = 15.5
    # two rings, drawn as thin bands so the chalk reads as a line, not a donut
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - cx, y - cy)
            if 13.6 <= d <= 14.6:
                cv.set(x, y, "pale", 0.95)
            elif 12.6 <= d <= 13.2:
                cv.set(x, y, "bone", 0.6)
    # pentagram: five vertices, connecting in star order
    star = [(cx + math.cos(math.radians(-90 + i * 144)) * 11.4,
             cy + math.sin(math.radians(-90 + i * 144)) * 11.4) for i in range(5)]
    for i in range(5):
        x0, y0 = star[i]
        x1, y1 = star[(i + 1) % 5]
        cv.line(int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1)), "pale", 0.9)
    # vertex glyphs and the sealed mark at the centre
    for (vx, vy) in star:
        cv.ellipse(int(round(vx)), int(round(vy)), 1.2, 1.2, "bone", 0.9)
    cv.ellipse(cx, cy, 2.4, 2.4, "pale", 0.75, filled=False)
    for a in range(0, 360, 72):
        cv.set(int(round(cx + math.cos(math.radians(a)) * 3.6)),
               int(round(cy + math.sin(math.radians(a)) * 3.6)), "pale", 0.9)
    # tick marks between the points
    for a in range(36, 360, 72):
        cv.line(int(round(cx + math.cos(math.radians(a)) * 13.9)),
                int(round(cy + math.sin(math.radians(a)) * 13.9)),
                int(round(cx + math.cos(math.radians(a)) * 11.8)),
                int(round(cy + math.sin(math.radians(a)) * 11.8)), "bone", 0.7)
    cv.noise("pale", 0.06, 0.35, seed=5)
    cv.save(os.path.join(OUT, "chalk_circle.png"))
    n += 1
    print(f"[blocks] wrote {n} block textures -> {OUT}")


if __name__ == "__main__":
    main()
