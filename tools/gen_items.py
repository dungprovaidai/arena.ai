"""
Item texture generator: pathway potions, supernatural ingredients, sealed artifacts.

All sprites are 16x16 on a single 1px grid (see docs/ART_BIBLE.md -> "one pixel scale"),
1px `void` outline, top-left key light, at most 12 palette entries per sprite.
Run:  python3 tools/gen_items.py
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from pixelart import Canvas, mix, darker, lighter, rgb, PALETTE  # noqa: E402
from content import ARTIFACTS, INGREDIENTS, POTIONS, UTILITY_ITEMS  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "main", "resources", "assets", "pathwaysofthebeyond", "textures", "item")


# ---------------------------------------------------------------------------------------
# Potions: Victorian apothecary glassware. Three silhouettes by rarity so a player can
# read a potion's weight from the inventory pixel block alone.
# ---------------------------------------------------------------------------------------
# rows: list of (first_x, last_x) inclusive for y = 0..15
FLASK = [(6, 9), (6, 9), (5, 10), (6, 9), (6, 9), (6, 9), (5, 10), (4, 11),
         (3, 12), (3, 12), (3, 12), (3, 12), (4, 11), (5, 10), (5, 10), (6, 9)]
ORB = [(7, 8), (7, 8), (6, 9), (7, 8), (7, 8), (7, 8), (6, 9), (5, 10),
       (4, 11), (3, 12), (3, 12), (4, 11), (4, 11), (5, 10), (6, 9), (7, 8)]
TALL = [(6, 9), (6, 9), (5, 10), (6, 9), (6, 9), (6, 9), (6, 9), (6, 9),
        (6, 9), (6, 9), (6, 9), (6, 9), (5, 10), (4, 11), (4, 11), (5, 10)]

# where the liquid surface sits, and where the cap/collar ends (rows above NECK_TOP are the cap)
LIQUID_TOP = {"flask": 7, "orb": 7, "tall": 7}
NECK_TOP = {"flask": 2, "orb": 2, "tall": 2}


def draw_potion(cv: Canvas, liquid, rarity="occult", style="flask", seal=False):
    """Paint one potion. `style` is 'flask' | 'orb' | 'tall'."""
    rows = {"flask": FLASK, "orb": ORB, "tall": TALL}[style]
    lum = sum(rgb(liquid)[:3]) / 3.0
    # glass tint: what the empty part of the bottle looks like
    glass = mix(liquid, "pale", 0.7) if lum < 130 else mix(liquid, "stone", 0.35)
    glass_dark = mix(glass, "void", 0.35)
    lt = LIQUID_TOP[style]
    nt = NECK_TOP[style]

    for y, (x0, x1) in enumerate(rows):
        for x in range(x0, x1 + 1):
            if y <= nt:
                # row 0-1 = stopper, row 2 = collar band (brass on anything but common)
                noble = rarity in ("rare", "epic", "legendary") or seal
                if y <= 1:
                    cv.set(x, y, "leather" if not noble else "brass_lo")
                    if y == 0:
                        cv.set(x, y, "brown" if not noble else "brass")
                else:
                    cv.set(x, y, "brass_lo" if noble else "tan")
                    if y == 2:
                        cv.set(x, y, "brass" if noble else "khaki")
            elif y < lt:
                # empty glass above the liquid: 1px darker at the silhouette edge
                cv.set(x, y, glass_dark if x in (x0, x1) else glass)
            else:
                if x in (x0, x1):
                    cv.set(x, y, mix(liquid, "void", 0.45))       # liquid edge/rim
                else:
                    t = (y - lt) / max(1, 15 - lt)
                    cv.set(x, y, mix(lighter(liquid, 0.30), darker(liquid, 0.30), t))
    # meniscus: bright surface line + one darker row of depth under it
    x0, x1 = rows[lt]
    cv.rect(x0 + 1, lt, x1 - 1, lt, lighter(liquid, 0.5))
    cv.rect(x0 + 1, lt + 1, x1 - 1, lt + 1, darker(liquid, 0.15))
    # shoulder shading so the glass reads as round
    for y in range(nt, lt):
        x0, x1 = rows[y]
        cv.set(x0, y, mix(glass, "void", 0.3))
        cv.set(x1, y, mix(glass, "void", 0.3))
    # left-edge highlight (key light from top-left)
    hl = [(y, rows[y][0] + 1) for y in range(nt + 1, 14) if rows[y][0] + 1 < rows[y][1]]
    for y, x in hl[1:-1]:
        cv.set(x, y, "bone", 0.42)
    # bubbles for anything past common
    if rarity in ("rare", "epic", "legendary"):
        for (bx, by) in ((5, 12), (9, 11), (6, 10)):
            if by >= lt:
                cv.set(bx, by, lighter(liquid, 0.55), 0.8)
    # base shadow
    by = 15
    cv.rect(rows[by][0], by, rows[by][1], by, mix(liquid, "void", 0.55))
    if seal:   # Sequence 0 / legendary: wax seal across the collar with a gold sigil
        cv.rect(4, nt + 1, 11, nt + 2, "blood")
        cv.rect(4, nt + 1, 11, nt + 1, "crimson")
        cv.set(7, nt + 1, "gold")
        cv.set(8, nt + 2, "gold", 0.7)


def potion_texture(entry):
    _id, name, pathway, seq, liquid, glow, rarity = entry
    style = {"common": "flask", "occult": "flask", "rare": "tall", "epic": "orb",
             "legendary": "orb"}[rarity]
    cv = Canvas(16, 16, seed=_id)
    draw_potion(cv, liquid, rarity=rarity, style=style, seal=(seq == 0))
    if glow:
        cv.glow(glow, radius=1, alpha=0.32 if rarity != "legendary" else 0.5)
    cv.outline("void")
    pip = {"common": None, "occult": "brass_lo", "rare": "brass", "epic": "gold",
           "legendary": "white"}[rarity]
    if pip:
        cv.set(13, 14, pip)
        cv.set(13, 15, darker(pip, 0.45))
    return cv


# ---------------------------------------------------------------------------------------
# ingredients
# ---------------------------------------------------------------------------------------
def ing_spirit_flower(cv):
    cv.line(7, 15, 7, 9, "moss")
    cv.line(8, 15, 8, 9, "moss_hi")
    cv.line(7, 12, 5, 10, "moss")
    cv.line(7, 13, 10, 11, "moss")
    for (x, y) in ((7, 5), (4, 7), (10, 7), (6, 9), (9, 9)):
        cv.ellipse(x, y, 1.6, 1.6, "spirit")
        cv.set(x, y - 1, "spirit_hi")
    cv.ellipse(7, 7, 1.2, 1.2, "spirit_hi")
    cv.glow("spirit", radius=1, alpha=0.30)


def ing_moonlit_fungus(cv):
    cv.rect(7, 11, 8, 15, "bone")
    cv.rect(7, 11, 7, 15, "pale")
    cv.poly([(2, 11), (5, 4), (10, 4), (13, 11)], "blue")
    cv.poly([(3, 10), (5, 6), (9, 6), (11, 10)], "blue_lo")
    cv.poly([(4, 9), (6, 5), (8, 5), (9, 9)], "cyan_mut")
    for (x, y) in ((5, 8), (8, 9), (10, 8), (6, 6), (9, 6)):
        cv.set(x, y, "spirit")
    cv.set(5, 5, "pale")
    cv.glow("cyan_mut", radius=1, alpha=0.25)


def ing_veil_dust(cv):
    cv.poly([(3, 14), (5, 7), (10, 6), (13, 14)], "charcoal")
    cv.poly([(4, 14), (5, 9), (9, 8), (11, 14)], "smoke")
    cv.poly([(5, 13), (9, 9), (10, 13)], "bone", 0.75)
    cv.noise("veil", 0.30, 0.5, mask=lambda x, y: 7 <= y <= 13)
    cv.set(8, 10, "veil")
    cv.glow("veil", radius=1, alpha=0.18)


def ing_purified_salt(cv):
    cv.poly([(4, 13), (5, 5), (9, 5), (11, 13)], "parch")
    cv.poly([(4, 13), (5, 8), (9, 8), (11, 13)], "parch_lo")
    cv.rect(5, 4, 9, 5, "parch_hi")
    cv.set(8, 3, "brass_lo")
    for (x, y) in ((6, 11), (8, 12), (9, 10), (6, 9), (10, 11)):
        cv.set(x, y, "white")
        cv.set(x, y + 1, "pale", 0.6)


def ing_occult_chalk(cv):
    cv.poly([(3, 12), (11, 4), (13, 6), (5, 14)], "pale")
    cv.poly([(4, 12), (11, 5), (12, 5), (5, 13)], "white")
    cv.line(6, 11, 11, 6, "bone", 0.6)
    cv.rect(9, 4, 12, 6, "parch_lo")   # wrapper band
    cv.rect(9, 4, 12, 4, "parch")
    cv.line(10, 5, 12, 5, "blood", 0.6)
    cv.noise("bone", 0.18, 0.4)


def ing_grave_earth(cv):
    cv.poly([(2, 15), (3, 9), (7, 6), (12, 9), (13, 15)], "brown")
    cv.poly([(3, 14), (4, 10), (8, 8), (11, 12), (12, 15)], "umber")
    cv.noise("leather", 0.22, 0.6, mask=lambda x, y: y >= 9)
    cv.line(5, 11, 8, 9, "khaki", 0.35)
    cv.rect(8, 10, 11, 11, "bone", 0.9)   # a bone in the soil
    cv.rect(9, 10, 10, 10, "pale", 0.9)


def ing_whispering_bone(cv):
    cv.poly([(3, 11), (11, 5), (13, 7), (5, 13)], "bone")
    cv.poly([(4, 11), (11, 6), (12, 6), (5, 12)], "pale")
    cv.ellipse(3, 11, 2.2, 2.2, "bone")
    cv.ellipse(3, 12, 1.4, 1.4, "pale", 0.8)
    cv.ellipse(12, 6, 2.0, 2.0, "bone")
    cv.ellipse(12, 6, 1.1, 1.1, "pale", 0.8)
    cv.line(6, 11, 10, 7, "void", 0.85, thick=1)   # the mouth
    for i in range(4):
        cv.set(7 + i, 10 - i, "void")
    cv.set(9, 8, "spirit")
    cv.glow("spirit", radius=1, alpha=0.16)


def ing_silver_needle(cv):
    """Silver needle, eye up, a loop of Soul Thread already threaded through it."""
    cv.line(11, 2, 4, 13, "pale", thick=1)
    cv.set(11, 2, "white")
    cv.set(10, 3, "white", 0.8)
    for i in range(1, 10):
        cv.set(11 - i * 0.75, 2 + i * 1.1, "bone", 0.6) if False else None
    cv.line(10, 3, 4, 14, "grey", 0.55)
    cv.set(4, 14, "smoke")
    cv.set(3, 15, "grey", 0.6)
    # eye of the needle
    cv.ellipse(12, 2, 1.6, 1.6, "pale")
    cv.set(12, 2, "stone")
    cv.set(12, 1, "white", 0.7)
    # thread: two long strands looping away
    cv.line(12, 1, 12, 0, "veil", 0.9)
    cv.line(11, 0, 6, 1, "veil", 0.85)
    cv.line(6, 1, 3, 4, "orchid", 0.8)
    cv.line(3, 4, 2, 8, "violet", 0.75)
    cv.set(2, 9, "veil", 0.7)
    cv.glow("veil", radius=1, alpha=0.20)


def ing_starless_crystal(cv):
    """A crystal with nothing inside it. Rim-lit so it still reads in a dark inventory."""
    cv.poly([(8, 0), (13, 7), (11, 14), (5, 14), (2, 7)], "void")
    cv.poly([(8, 1), (12, 7), (10, 13), (6, 13), (3, 7)], "charcoal")
    cv.poly([(8, 2), (11, 7), (9, 12), (7, 12), (4, 7)], "ash")
    cv.poly([(8, 4), (10, 8), (8, 11), (7, 8)], "pitch")
    # cold rim light on the left facet, dim violet bounce on the right
    cv.line(8, 1, 3, 7, "slate", 0.9)
    cv.line(3, 7, 6, 13, "stone", 0.7)
    cv.line(8, 1, 12, 7, "violet_lo", 0.9)
    cv.line(12, 7, 10, 13, "violet_lo", 0.6)
    cv.line(6, 13, 10, 13, "void")
    cv.set(7, 6, "orchid", 0.9)
    cv.set(8, 7, "veil", 0.5)
    cv.glow("violet", radius=1, alpha=0.30)


def ing_soul_fragment(cv):
    cv.poly([(8, 2), (12, 8), (8, 14), (4, 8)], "navy")
    cv.poly([(8, 3), (11, 8), (8, 13), (5, 8)], "blue")
    cv.poly([(8, 5), (10, 8), (8, 11), (6, 8)], "cyan_mut")
    cv.ellipse(8, 8, 1.4, 2.0, "spirit_hi")
    cv.noise("spirit", 0.12, 0.5, mask=lambda x, y: 5 <= y <= 11)
    cv.glow("spirit", radius=1, alpha=0.35)


def ing_abyssal_eye(cv):
    cv.ellipse(8, 8, 6.0, 5.0, "void")
    cv.ellipse(8, 8, 5.0, 4.2, "abyss")
    cv.ellipse(8, 8, 4.0, 4.0, "navy")
    cv.ellipse(8, 8, 3.1, 3.1, "blue_lo")
    cv.ellipse(8, 8, 2.1, 2.6, "void")
    cv.ellipse(7, 7, 0.9, 0.9, "spirit", 0.6)
    for a, r in ((200, 4.4), (230, 4.2), (150, 4.0), (110, 3.8)):
        x = int(8 + math.cos(math.radians(a)) * r)
        y = int(8 + math.sin(math.radians(a)) * r * 0.8)
        cv.line(8, 8, x, y, "blood", 0.45)
    cv.glow("blue", radius=1, alpha=0.22)


def ing_black_blood(cv):
    cv.rect(6, 4, 9, 6, "slate")
    cv.rect(6, 4, 9, 5, "grey")
    cv.rect(5, 7, 10, 13, mix("smoke", "pale", 0.5))
    for y in range(9, 14):
        cv.rect(6, y, 9, y, "pitch")
    cv.rect(6, 9, 9, 9, "ash")
    cv.set(7, 11, "void")
    cv.set(8, 12, "void")
    cv.line(6, 10, 6, 12, "bone", 0.4)
    cv.rect(5, 13, 10, 14, mix("smoke", "pale", 0.5))
    cv.rect(5, 14, 10, 14, "grey")
    cv.rect(6, 3, 9, 4, "brown")
    cv.glow("crimson", radius=1, alpha=0.12)


def ing_faceless_skin(cv):
    """A shed face, laid flat: blank mask, no features except where they were."""
    mask = [(3, 2), (12, 2), (14, 5), (13, 11), (8, 15), (3, 11), (2, 5)]
    cv.poly([(4, 3), (11, 3), (13, 6), (12, 11), (8, 14), (4, 11), (3, 6)], "parch_lo")
    cv.poly([(4, 4), (11, 4), (12, 6), (11, 10), (8, 13), (5, 10), (4, 6)], "parch")
    cv.poly([(5, 5), (10, 5), (11, 7), (10, 9), (8, 11), (6, 9), (5, 7)], "parch_hi")
    # where the eyes and mouth used to be: shallow dents, not holes
    cv.line(5, 6, 7, 7, "parch_lo", 0.95)
    cv.line(10, 6, 9, 7, "parch_lo", 0.95)
    cv.line(6, 10, 9, 10, "parch_lo", 0.9)
    cv.set(6, 6, "khaki", 0.8)
    cv.set(9, 6, "khaki", 0.8)
    # it is still slightly warm, and the edges have not set
    cv.line(3, 6, 4, 5, "tan", 0.5)
    cv.line(13, 7, 12, 9, "tan", 0.4)
    cv.noise("khaki", 0.10, 0.30)
    cv.set(8, 12, "bone", 0.35)


def ing_corrupted_heart(cv):
    cv.poly([(8, 14), (2, 8), (2, 5), (4, 3), (7, 3), (8, 5)], "blood_lo")
    cv.poly([(8, 14), (14, 8), (14, 5), (12, 3), (9, 3), (8, 5)], "blood_lo")
    cv.poly([(8, 13), (3, 8), (3, 5), (5, 4), (7, 4), (8, 6)], "blood")
    cv.poly([(8, 13), (13, 8), (13, 5), (11, 4), (9, 4), (8, 6)], "crimson")
    cv.poly([(8, 12), (4, 8), (4, 6), (6, 5), (8, 7)], "rust")
    cv.poly([(8, 12), (12, 8), (12, 6), (10, 5), (8, 7)], "crimson")
    for (x0, y0, x1, y1) in ((5, 5, 8, 11), (11, 6, 8, 11), (3, 7, 8, 9), (13, 8, 8, 10)):
        cv.line(x0, y0, x1, y1, "void", 0.8)
    cv.set(8, 11, "blood_lo")
    cv.rect(7, 14, 8, 15, "blood_lo")
    cv.glow("crimson", radius=1, alpha=0.20)


def ing_ancient_memory(cv):
    cv.poly([(2, 5), (9, 3), (14, 5), (14, 12), (7, 14), (2, 12)], "brass_lo")
    cv.poly([(3, 6), (9, 4), (13, 6), (13, 11), (7, 13), (3, 11)], "parch")
    cv.poly([(4, 7), (9, 5), (12, 7), (12, 10), (7, 12), (4, 10)], "parch_hi")
    cv.line(6, 6, 6, 12, "brass_lo", 0.9)
    for i, y in enumerate(range(6, 12, 2)):
        cv.line(7, y, 11 - i, y, "brown", 0.55)
    cv.set(8, 8, "gold")
    cv.set(9, 9, "gold", 0.7)
    cv.glow("gold", radius=1, alpha=0.18)


def ing_nightshade_ashes(cv):
    cv.poly([(3, 14), (4, 10), (8, 8), (12, 10), (13, 14)], "charcoal")
    cv.poly([(4, 13), (5, 11), (9, 9), (11, 12), (12, 13)], "ash")
    cv.noise("violet_lo", 0.25, 0.55, mask=lambda x, y: 9 <= y <= 13)
    for (x, y) in ((6, 11), (9, 10), (10, 12), (7, 12)):
        cv.set(x, y, "violet")
    cv.set(8, 9, "orchid", 0.8)
    cv.glow("violet", radius=1, alpha=0.15)


INGREDIENT_PAINTERS = {
    "spirit_flower": ing_spirit_flower,
    "moonlit_fungus": ing_moonlit_fungus,
    "veil_dust": ing_veil_dust,
    "purified_salt": ing_purified_salt,
    "occult_chalk": ing_occult_chalk,
    "grave_earth": ing_grave_earth,
    "whispering_bone": ing_whispering_bone,
    "silver_needle": ing_silver_needle,
    "starless_crystal": ing_starless_crystal,
    "soul_fragment": ing_soul_fragment,
    "abyssal_eye": ing_abyssal_eye,
    "black_blood_vial": ing_black_blood,
    "faceless_skin": ing_faceless_skin,
    "corrupted_heart": ing_corrupted_heart,
    "ancient_memory": ing_ancient_memory,
    "nightshade_ashes": ing_nightshade_ashes,
}


# ---------------------------------------------------------------------------------------
# sealed artifacts
# ---------------------------------------------------------------------------------------
def art_eye_of_solomon(cv):
    # brass reliquary setting
    cv.ellipse(8, 9, 6.6, 5.4, "brass_lo")
    cv.ellipse(8, 9, 5.6, 4.5, "brass")
    cv.ellipse(8, 8.6, 4.6, 3.6, "parch_lo")
    cv.ellipse(8, 9, 3.9, 3.1, "white")
    cv.ellipse(8, 9, 3.0, 2.6, "spirit")
    cv.ellipse(8, 9, 1.9, 1.9, "blue_lo")
    cv.ellipse(8, 9, 1.1, 1.6, "void")
    cv.ellipse(7, 8, 0.6, 0.6, "white")
    # radiating lashes
    for a in range(0, 360, 45):
        x0 = int(8 + math.cos(math.radians(a)) * 5.0)
        y0 = int(9 + math.sin(math.radians(a)) * 4.0)
        cv.set(x0, y0, "brass_hi")
    # sealed clasp
    cv.rect(6, 14, 10, 15, "brass_lo")
    cv.rect(6, 14, 10, 14, "brass_hi")
    cv.set(8, 15, "blood")
    # rim shadow
    cv.ellipse(8, 9, 6.6, 5.4, "void", 0.35, filled=False)
    cv.glow("cyan_mut", radius=1, alpha=0.30)


def art_black_book(cv):
    cv.poly([(2, 4), (8, 2), (14, 4), (14, 12), (8, 15), (2, 12)], "void")
    cv.poly([(3, 4), (8, 3), (13, 4), (13, 11), (8, 14), (3, 11)], "pitch")
    cv.line(8, 3, 8, 14, "charcoal", 0.9)
    # page edges
    for y in range(5, 13):
        cv.set(4, y, "ash", 0.7)
        cv.set(12, y, "ash", 0.7)
    # brass clasp + eye sigil
    cv.rect(7, 2, 9, 3, "brass")
    cv.ellipse(8, 8, 2.2, 1.4, "ash")
    cv.ellipse(8, 8, 1.4, 1.0, "violet")
    cv.set(8, 8, "veil")
    cv.set(6, 6, "brass_hi")
    cv.set(10, 10, "brass_lo")
    cv.glow("violet", radius=1, alpha=0.22)


def art_whispering_bell(cv):
    """Cracked hand-bell seen from the side: dome, waist rim, empty mouth."""
    dome = {2: (6, 9), 3: (5, 10), 4: (5, 10), 5: (4, 11),
            6: (4, 11), 7: (3, 12), 8: (3, 12)}
    for y, (x0, x1) in dome.items():
        for x in range(x0, x1 + 1):
            t = (x - x0) / max(1, x1 - x0)
            cv.set(x, y, mix("brass_hi", "brass_lo", t * 0.85))
    cv.rect(2, 9, 13, 9, "brass_hi")        # waist rim catches light
    cv.rect(2, 10, 13, 10, "brass_lo")
    cv.rect(4, 11, 11, 11, "charcoal", 0.9)  # the mouth, and it is empty
    cv.rect(5, 12, 10, 12, "void", 0.6)
    # crown loop
    cv.rect(7, 0, 8, 1, "brass_lo")
    cv.set(7, 0, "brass")
    # the crack: one continuous fault line through the casting
    for (x, y) in ((9, 3), (9, 4), (8, 5), (8, 6), (9, 7), (10, 8), (11, 9)):
        cv.set(x, y, "void", 0.95)
        cv.set(x + 1, y, "charcoal", 0.5)
    # left highlight down the dome
    for y in range(3, 9):
        x0, x1 = dome[y]
        cv.set(x0 + 1, y, "gold", 0.45)
    # clapper was removed: a stub of its chain remains
    cv.set(7, 11, "smoke", 0.5)
    cv.set(8, 11, "grey", 0.4)
    cv.glow("gold", radius=1, alpha=0.16)


def art_brass_key(cv):
    """Ornate brass key: ring bow, long shank, two teeth, engraved 'emergency' marks."""
    cv.ellipse(7.5, 3, 3.4, 2.8, "brass")
    cv.ellipse(7.5, 3, 2.1, 1.6, "void")
    cv.ellipse(7.5, 2.6, 3.4, 2.8, "brass_hi", 0.55, filled=False)
    cv.set(5, 1, "gold")
    cv.rect(7, 5, 8, 14, "brass")
    cv.rect(7, 5, 7, 14, "brass_hi")
    cv.rect(8, 5, 8, 14, "brass_lo")
    # collar
    cv.rect(6, 5, 9, 5, "brass_lo")
    # teeth on the right side
    cv.rect(9, 9, 11, 10, "brass")
    cv.rect(9, 9, 11, 9, "brass_hi")
    cv.rect(9, 12, 10, 13, "brass")
    cv.rect(9, 12, 10, 12, "brass_hi")
    cv.set(11, 10, "void", 0.7)
    cv.set(10, 13, "void", 0.7)
    # engraving: a tiny sigil that never quite resolves
    cv.set(4, 3, "void", 0.6)
    cv.set(11, 3, "void", 0.5)
    cv.rect(7, 14, 8, 15, "brass_lo")
    cv.glow("brass_hi", radius=1, alpha=0.16)


ARTIFACT_PAINTERS = {
    "eye_of_solomon": art_eye_of_solomon,
    "black_book": art_black_book,
    "whispering_bell": art_whispering_bell,
    "brass_key": art_brass_key,
}


# ---------------------------------------------------------------------------------------
# utility items
# ---------------------------------------------------------------------------------------
def util_pathway_codex(cv):
    cv.poly([(2, 3), (7, 2), (14, 4), (14, 12), (7, 14), (2, 12)], "leather")
    cv.poly([(3, 4), (7, 3), (13, 5), (13, 11), (7, 13), (3, 11)], "brown")
    cv.poly([(4, 5), (7, 4), (12, 6), (12, 10), (7, 12), (4, 10)], "parch")
    cv.poly([(4, 5), (7, 4), (12, 6), (12, 7), (7, 6), (4, 6)], "parch_hi")
    for y in range(7, 11):
        cv.line(5, y, 11, y + 1, "parch_lo", 0.6)
    cv.rect(7, 2, 8, 13, "brass_lo", 0.9)
    cv.set(7, 3, "brass")
    # pathway sigil stamped on the cover
    cv.ellipse(5, 8, 1.4, 1.4, "brass_hi", 0.9)
    cv.set(5, 8, "orchid")
    cv.set(6, 6, "orchid", 0.6)


def util_soul_thread_spool(cv):
    cv.ellipse(8, 8, 6.4, 6.4, "leather")
    cv.ellipse(8, 8, 5.2, 5.2, "brown")
    cv.ellipse(8, 8, 3.6, 3.6, "void")
    for i, y in enumerate(range(4, 13)):
        cv.line(4 + (i % 2), y, 11 - (i % 2), y, "cyan_mut" if i % 3 else "spirit", 0.85)
    cv.ellipse(8, 8, 2.2, 2.2, "spirit", 0.55)
    cv.set(6, 5, "spirit_hi")
    cv.glow("spirit", radius=1, alpha=0.25)


def util_thread_shears(cv):
    """Open thread-shears, blades up, finger rings down, a cut Soul Thread caught inside."""
    # blades
    cv.line(7, 8, 3, 1, "bone", thick=2)
    cv.line(9, 8, 13, 1, "bone", thick=2)
    cv.line(7, 8, 4, 2, "pale", 0.7)
    cv.line(9, 8, 12, 2, "grey", 0.7)
    cv.set(3, 1, "white")
    cv.set(13, 1, "white")
    # pivot screw
    cv.ellipse(8, 9, 2.2, 2.2, "brass")
    cv.ellipse(8, 9, 1.1, 1.1, "brass_lo")
    cv.set(7, 8, "gold")
    # handles + finger rings
    cv.line(7, 10, 5, 12, "smoke", thick=2)
    cv.line(9, 10, 11, 12, "smoke", thick=2)
    cv.ellipse(4, 13, 2.3, 2.0, "grey")
    cv.ellipse(4, 13, 1.2, 1.0, "void")
    cv.ellipse(12, 13, 2.3, 2.0, "grey")
    cv.ellipse(12, 13, 1.2, 1.0, "void")
    cv.set(3, 12, "bone", 0.6)
    cv.set(11, 12, "bone", 0.6)
    # the thread it just cut: severed, still glowing at both ends
    cv.line(2, 6, 6, 5, "cyan_mut", 0.85)
    cv.set(6, 5, "spirit")
    cv.line(10, 5, 14, 6, "cyan_mut", 0.85)
    cv.set(10, 5, "spirit")
    cv.glow("spirit", radius=1, alpha=0.22)


def util_ritual_dagger(cv):
    cv.poly([(8, 1), (10, 9), (8, 11), (6, 9)], "pale")
    cv.poly([(8, 1), (9, 9), (8, 11)], "white")
    cv.line(8, 2, 8, 10, "bone", 0.7)
    cv.rect(4, 10, 11, 11, "brass_lo")
    cv.rect(4, 10, 11, 10, "brass_hi")
    cv.rect(7, 11, 8, 15, "leather")
    cv.rect(7, 11, 7, 15, "brown")
    cv.set(8, 13, "brass")
    cv.set(6, 8, "crimson", 0.6)
    cv.set(9, 7, "blood", 0.5)
    cv.set(8, 15, "brass_lo")


def util_occult_compass(cv):
    cv.ellipse(8, 8, 6.6, 6.6, "brass_lo")
    cv.ellipse(8, 8, 5.6, 5.6, "brass")
    cv.ellipse(8, 8, 4.4, 4.4, "abyss")
    cv.ellipse(8, 8, 3.4, 3.4, "navy")
    for a in (0, 90, 180, 270):
        x = int(8 + math.cos(math.radians(a)) * 5.0)
        y = int(8 + math.sin(math.radians(a)) * 5.0)
        cv.set(x, y, "bone")
    cv.poly([(8, 3), (9, 8), (8, 11), (7, 8)], "spirit")
    cv.poly([(8, 13), (9, 8), (8, 5), (7, 8)], "blood")
    cv.set(8, 8, "gold")
    cv.set(6, 5, "brass_hi")
    cv.glow("spirit", radius=1, alpha=0.20)


def util_ward_charm(cv):
    cv.line(8, 1, 8, 4, "leather")
    cv.ellipse(8, 9, 6.0, 5.5, "moss")
    cv.ellipse(8, 9, 4.8, 4.4, "moss_hi")
    cv.ellipse(8, 9, 3.2, 3.0, "moss_lo")
    # carved protective sigil
    cv.line(8, 5, 8, 13, "sage", 0.9)
    cv.line(5, 8, 11, 8, "sage", 0.9)
    cv.line(6, 6, 10, 12, "sage", 0.7)
    cv.line(10, 6, 6, 12, "sage", 0.7)
    cv.ellipse(8, 9, 1.4, 1.4, "parch_hi")
    cv.set(8, 9, "gold")
    cv.set(11, 6, "bone", 0.5)
    cv.glow("sage", radius=1, alpha=0.14)


def util_spirit_tonic(cv):
    """Apothecary tincture: same glassware family as pathway potions."""
    draw_potion(cv, "cyan_mut", rarity="rare", style="tall")
    cv.set(7, 12, "white")
    cv.set(8, 11, "spirit")
    cv.set(6, 13, "spirit_hi", 0.7)
    cv.set(9, 10, "spirit", 0.6)
    return cv


UTILITY_PAINTERS = {
    "pathway_codex": util_pathway_codex,
    "soul_thread_spool": util_soul_thread_spool,
    "thread_shears": util_thread_shears,
    "ritual_dagger": util_ritual_dagger,
    "occult_compass": util_occult_compass,
    "ward_charm": util_ward_charm,
}


def finish(cv: Canvas, glow_accent=None):
    if glow_accent:
        cv.glow(glow_accent, radius=1, alpha=0.18)
    cv.outline("void")
    return cv


def main():
    os.makedirs(OUT, exist_ok=True)
    written = []

    for entry in POTIONS:
        cv = potion_texture(entry)
        cv.save(os.path.join(OUT, f"{entry[0]}.png"))
        written.append(f"item/{entry[0]}")

    for iid, _name, _tier, _origin, _lore in INGREDIENTS:
        painter = INGREDIENT_PAINTERS[iid]
        cv = Canvas(16, 16, seed=iid)
        painter(cv)
        finish(cv)
        cv.save(os.path.join(OUT, f"{iid}.png"))
        written.append(f"item/{iid}")

    for aid, _name, _rar, _desc, _passive, _drawback in ARTIFACTS:
        cv = Canvas(16, 16, seed=aid)
        ARTIFACT_PAINTERS[aid](cv)
        finish(cv)
        cv.save(os.path.join(OUT, f"{aid}.png"))
        written.append(f"item/{aid}")

    for uid, _name, _desc in UTILITY_ITEMS:
        if uid == "spirit_tonic":
            cv = Canvas(16, 16, seed=uid)
            painter = util_spirit_tonic
        else:
            cv = Canvas(16, 16, seed=uid)
            painter = UTILITY_PAINTERS[uid]
        painter(cv)
        finish(cv)
        cv.save(os.path.join(OUT, f"{uid}.png"))
        written.append(f"item/{uid}")

    print(f"[items] wrote {len(written)} item textures -> {OUT}")


if __name__ == "__main__":
    main()
