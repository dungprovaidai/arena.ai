"""
Effect icons, particle sprites and GUI texture set.

GUI design language (docs/ART_BIBLE.md):
  * parchment page + dark oak + tarnished brass + leather
  * engraved double borders, corner brass bosses, restrained occult sigils
  * never neon, never flat modern UI: every panel is a composite of wood, leather,
    parchment and metal with a 1px dark keyline

Run:  python3 tools/gen_gui.py
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from pixelart import Canvas, mix, darker, lighter, PALETTE  # noqa: E402
from content import EFFECTS, PARTICLES  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "pathwaysofthebeyond")
EFF_OUT = os.path.join(ASSETS, "textures", "mob_effect")
PART_OUT = os.path.join(ASSETS, "textures", "particle")
GUI_OUT = os.path.join(ASSETS, "textures", "gui")


# =======================================================================================
# effect icons (18x18, vanilla mob_effect size, dark brass plaque + engraved glyph)
# =======================================================================================
def effect_plaque(cv, border, fill):
    cv.rect(0, 0, 17, 17, "void")
    cv.rect(1, 1, 16, 16, fill)
    for y in range(1, 17):
        for x in range(1, 17):
            t = (x + y) / 34.0
            cv.set(x, y, mix(lighter(fill, 0.16), darker(fill, 0.22), t))
    cv.frame(1, 1, 16, 16, border)
    cv.frame(0, 0, 17, 17, "void")
    for (x, y) in ((2, 2), (15, 2), (2, 15), (15, 15)):
        cv.set(x, y, "brass_hi")
        cv.set(x, y, "brass")
    cv.set(3, 3, "brass_lo")
    cv.set(14, 14, "brass_lo")


def glyph_sanity(cv, tint):
    """A cracked mind: concentric rings with a fault line."""
    cv.ellipse(9, 9, 5.4, 5.4, tint, 0.9, filled=False)
    cv.ellipse(9, 9, 3.4, 3.4, tint, 0.55, filled=False)
    cv.set(9, 9, "white")
    cv.line(4, 5, 14, 13, tint, 0.9)
    cv.line(14, 13, 9, 15, tint, 0.7)
    cv.set(5, 4, "white", 0.7)


def glyph_eye(cv, tint):
    cv.ellipse(9, 9, 6.0, 3.8, tint, 0.85, filled=False)
    cv.ellipse(9, 9, 2.4, 2.4, tint)
    cv.ellipse(9, 9, 1.1, 1.1, "void")
    cv.set(8, 8, "white")
    for a in (200, 250, 300, 340):
        x = int(9 + math.cos(math.radians(a)) * 6.4)
        y = int(9 + math.sin(math.radians(a)) * 4.2)
        cv.set(x, y, tint, 0.6)


def glyph_spirit(cv, tint):
    """A body leaving a body."""
    cv.ellipse(7, 7, 2.4, 2.4, tint)
    cv.poly([(7, 9), (4, 15), (10, 15)], tint, 0.75)
    cv.ellipse(12, 12, 1.6, 1.6, tint, 0.5)
    cv.poly([(12, 13), (10, 16), (14, 16)], tint, 0.4)


def glyph_veins(cv, tint):
    cv.line(3, 15, 7, 9, tint, 0.95)
    cv.line(7, 9, 5, 3, tint, 0.9)
    cv.line(7, 9, 12, 6, tint, 0.85)
    cv.line(12, 6, 15, 2, tint, 0.7)
    cv.line(12, 6, 15, 11, tint, 0.6)
    cv.set(7, 9, "white", 0.8)
    cv.set(5, 3, tint, 0.6)
    cv.set(3, 15, tint, 0.5)


def glyph_thread(cv, tint):
    for i in range(3):
        cv.line(3 + i * 2, 2, 5 + i * 2, 15, tint, 0.85 - i * 0.2)
    cv.ellipse(9, 15, 2.6, 1.2, tint, 0.7)
    cv.ellipse(9, 2, 2.6, 1.2, tint, 0.5)


def glyph_triangle(cv, tint):
    cv.poly([(9, 2), (16, 15), (2, 15)], tint, 0.85)
    cv.poly([(9, 5), (13, 14), (5, 14)], "void", 0.75)
    cv.ellipse(9, 11, 1.6, 1.6, tint)
    cv.set(9, 10, "white", 0.8)


def glyph_clarity(cv, tint):
    cv.poly([(9, 1), (13, 9), (9, 17), (5, 9)], tint, 0.9)
    cv.poly([(9, 4), (11, 9), (9, 14), (7, 9)], "void", 0.8)
    cv.set(9, 9, "white")


def glyph_digest(cv, tint):
    cv.ellipse(9, 10, 5.6, 5.0, tint, 0.8, filled=False)
    cv.poly([(9, 4), (12, 8), (6, 8)], tint, 0.9)
    cv.set(9, 11, "white", 0.7)


def glyph_dread(cv, tint):
    cv.ellipse(9, 8, 4.4, 6.0, tint, 0.8)
    cv.ellipse(7, 7, 0.9, 1.2, "void")
    cv.ellipse(11, 7, 0.9, 1.2, "void")
    cv.line(7, 12, 11, 12, "void", 0.8)
    for i in range(5):
        cv.set(4 + i * 3, 2 + (i % 2), tint, 0.45)


EFFECT_GLYPHS = {
    "sanity_bleed": (glyph_sanity, "violet_lo", "violet"),
    "hallucinating": (glyph_eye, "violet_lo", "orchid"),
    "spirit_form": (glyph_spirit, "navy", "spirit"),
    "corruption_surge": (glyph_veins, "blood_lo", "crimson"),
    "thread_bond": (glyph_thread, "moss_lo", "cyan_mut"),
    "unblinking_gaze": (glyph_eye, "pitch", "blue"),
    "curse_of_the_veil": (glyph_triangle, "charcoal", "blood"),
    "clarity": (glyph_clarity, "navy", "spirit_hi"),
    "digestion_quickened": (glyph_digest, "umber", "brass_hi"),
    "cosmic_dread": (glyph_dread, "void", "orchid"),
}


def gen_effects():
    os.makedirs(EFF_OUT, exist_ok=True)
    n = 0
    for eid, _name, _kind, _desc in EFFECTS:
        painter, fill, tint = EFFECT_GLYPHS[eid]
        cv = Canvas(18, 18, seed=eid)
        effect_plaque(cv, "brass_lo", fill)
        painter(cv, tint)
        cv.save(os.path.join(EFF_OUT, f"{eid}.png"))
        n += 1
    print(f"[effects] wrote {n} effect icons -> {EFF_OUT}")


# =======================================================================================
# particles (8x8 / 8x8, soft and hard-edged families kept visually distinct)
# =======================================================================================
def p_spirit_mote(cv):
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5) / 3.2
            if d < 1.0:
                cv.set(x, y, mix("spirit_hi", "blue_lo", d), (1 - d) ** 0.7)
    cv.set(3, 3, "white")
    cv.set(4, 4, "spirit")


def p_rune_dust(cv):
    """Hard-edged square glyph flake: no soft falloff, so it reads as a carved mark."""
    cv.rect(2, 2, 5, 5, "brass")
    cv.rect(2, 2, 5, 2, "brass_hi")
    cv.rect(2, 5, 5, 5, "brass_lo")
    cv.set(3, 3, "gold")
    cv.set(4, 4, "void", 0.6)
    cv.frame(2, 2, 5, 5, "void", 0.8)


def p_ember_occult(cv):
    cv.ellipse(3.5, 4, 2.4, 2.8, "brass")
    cv.ellipse(3.5, 3.6, 1.6, 1.8, "gold")
    cv.ellipse(3.5, 3.4, 0.9, 1.0, "white")
    cv.set(3, 6, "crimson", 0.5)


def p_black_wisp(cv):
    """A smudge, not a spark: irregular silhouette with a dark core."""
    for (x, y) in ((3, 1), (4, 1), (2, 2), (3, 2), (4, 2), (5, 3), (2, 3), (3, 3), (4, 3),
                   (2, 4), (3, 4), (4, 4), (3, 5), (4, 5), (4, 6), (2, 5)):
        cv.set(x, y, "charcoal")
    cv.set(3, 3, "void")
    cv.set(4, 4, "pitch")
    cv.set(2, 3, "violet_lo", 0.5)
    cv.set(4, 2, "ash", 0.4)


def p_chromatic_speck(cv):
    """Hard-edged with a violet/cyan split - reads as a rendering error, not magic."""
    cv.rect(2, 2, 4, 4, "veil", 0.75)
    cv.rect(3, 2, 5, 4, "cyan_mut", 0.5)
    cv.set(3, 3, "white")
    cv.set(4, 3, "orchid")


def p_soul_flow(cv):
    """Teardrop mote that travels along a thread."""
    cv.ellipse(3.5, 3, 1.8, 2.0, "cyan_mut")
    cv.ellipse(3.5, 3, 1.1, 1.2, "spirit_hi")
    cv.ellipse(3.5, 5.4, 1.0, 1.2, "spirit", 0.7)
    cv.set(3, 2, "white")


def p_veil_smoke(cv):
    """Low, wide, ground-hugging smoke."""
    cv.ellipse(3.5, 5.5, 3.6, 1.9, "smoke", 0.30)
    cv.ellipse(3.5, 5.0, 2.6, 1.5, "bone", 0.22)
    cv.ellipse(3.5, 4.6, 1.6, 0.9, "pale", 0.15)
    cv.set(2, 5, "veil", 0.35)
    cv.set(5, 5, "veil", 0.25)


def p_beyond_shard(cv):
    """An impossible polygon: two overlapping triangles that cannot both be flat."""
    cv.poly([(3.5, 0), (7, 7), (0, 7)], "void", 0.9)
    cv.poly([(3.5, 1), (6, 6), (1, 6)], "orchid", 0.8)
    cv.poly([(3.5, 3), (6, 5), (1, 5)], "void", 0.9)
    cv.set(3, 4, "white")
    cv.set(4, 6, "veil")


def p_blood_drop(cv):
    cv.ellipse(3.5, 4.5, 1.8, 2.2, "blood")
    cv.ellipse(3.5, 3.0, 1.0, 1.4, "crimson")
    cv.poly([(3.5, 0), (5, 3), (2, 3)], "crimson")
    cv.set(3, 4, "rust", 0.8)
    cv.set(4, 5, "blood_lo")


def p_shadow_move(cv):
    """A hard-edged dark smear that slides sideways - dodges, folds, failed teleports."""
    for (x, y) in ((2, 3), (3, 3), (4, 3), (5, 3), (3, 4), (4, 4), (5, 4), (6, 4),
                   (4, 5), (5, 5), (6, 5), (1, 2), (2, 2)):
        cv.set(x, y, "void")
    cv.rect(3, 3, 5, 4, "charcoal")
    cv.set(4, 3, "ash", 0.6)
    cv.set(5, 5, "violet_lo", 0.45)


PARTICLE_PAINTERS = {
    "spirit_mote": p_spirit_mote,
    "rune_dust": p_rune_dust,
    "ember_occult": p_ember_occult,
    "black_wisp": p_black_wisp,
    "chromatic_speck": p_chromatic_speck,
    "soul_flow": p_soul_flow,
    "veil_smoke": p_veil_smoke,
    "beyond_shard": p_beyond_shard,
    "blood_drop": p_blood_drop,
    "shadow_move": p_shadow_move,
}


def gen_particles():
    os.makedirs(PART_OUT, exist_ok=True)
    n = 0
    for pid, _desc in PARTICLES:
        cv = Canvas(8, 8, seed=pid)
        PARTICLE_PAINTERS[pid](cv)
        cv.save(os.path.join(PART_OUT, f"{pid}.png"))
        # glow variants for the ones that emit light in-world
        if pid in ("spirit_mote", "soul_flow", "ember_occult", "beyond_shard", "rune_dust"):
            g = Canvas(8, 8, seed=pid + "g")
            PARTICLE_PAINTERS[pid](g)
            g.glow("spirit_hi" if pid in ("spirit_mote", "soul_flow") else "gold", 1, 0.28)
            g.save(os.path.join(PART_OUT, f"{pid}_glow.png"))
            n += 1
        n += 1
    print(f"[particles] wrote {n} particle sprites -> {PART_OUT}")


# =======================================================================================
# GUI
# =======================================================================================
def gui_parchment_panel(cv, w, h, title=True):
    """Dark oak frame, tooled leather, parchment inlay, engraved keylines."""
    cv.fill("void")
    cv.rect(0, 0, w - 1, h - 1, "umber")
    # wood grain frame
    for y in range(h):
        for x in range(w):
            if x < 5 or x >= w - 5 or y < 5 or y >= h - 5:
                t = ((x * 5 + y * 3) % 9) / 9.0
                cv.set(x, y, mix("brown", "leather", t))
    cv.frame(0, 0, w - 1, h - 1, "void")
    cv.frame(1, 1, w - 2, h - 2, "tan", 0.55)
    cv.frame(4, 4, w - 5, h - 5, "void")
    # parchment inlay
    cv.rect(5, 5, w - 6, h - 6, "parch")
    cv.value_noise("parch_lo", "parch_hi", scale=6.0, alpha=0.35, seed=w * h)
    cv.noise("parch_lo", 0.10, 0.30)
    cv.frame(5, 5, w - 6, h - 6, "parch_lo")
    # brass corner bosses
    for (cx, cy) in ((2, 2), (w - 4, 2), (2, h - 4), (w - 4, h - 4)):
        cv.rect(cx, cy, cx + 1, cy + 1, "brass")
        cv.set(cx, cy, "brass_hi")
        cv.set(cx + 1, cy + 1, "brass_lo")
    if title:
        cv.rect(8, 6, w - 9, 7, "brass_lo")
        cv.rect(9, 6, w - 10, 6, "brass")
    return cv


def gui_pathway_background():
    """Pathway screen: 256x200, parchment folio with an occult diagram plate."""
    w, h = 256, 200
    cv = gui_parchment_panel(Canvas(w, h, seed="pathway"), w, h)
    # left plate: a brass-bordered occult diagram area
    cv.rect(12, 22, 116, 178, "void")
    cv.rect(13, 23, 115, 177, "charcoal")
    cv.rect(13, 23, 115, 177, "abyss", 0.55)
    cv.frame(13, 23, 115, 177, "brass_lo")
    cv.frame(14, 24, 114, 176, "brass", 0.5)
    # engraved concentric guide circles (the java layer draws the live emblem on top)
    cv.ellipse(64, 100, 44, 44, "brass_lo", 0.30, filled=False)
    cv.ellipse(64, 100, 34, 34, "brass_lo", 0.18, filled=False)
    for a in range(0, 360, 30):
        x0 = int(64 + math.cos(math.radians(a)) * 44)
        y0 = int(100 + math.sin(math.radians(a)) * 44)
        x1 = int(64 + math.cos(math.radians(a)) * 38)
        y1 = int(100 + math.sin(math.radians(a)) * 38)
        cv.line(x0, y0, x1, y1, "brass_lo", 0.4)
    # right side: ruled panels for text
    cv.rect(124, 22, 244, 178, "parch_hi", 0.35)
    cv.frame(124, 22, 244, 178, "parch_lo", 0.7)
    for y in range(34, 174, 6):
        cv.line(128, y, 240, y, "parch_lo", 0.35)
    # header rule + a small cipher at the top right
    cv.rect(12, 14, 116, 18, "umber")
    cv.rect(13, 15, 115, 17, "leather")
    cv.line(13, 15, 115, 15, "tan", 0.6)
    return cv


def gui_hud_bar():
    """Sanity/Corruption HUD: 128x24, split into two 60px gauges with brass endcaps."""
    w, h = 128, 24
    cv = Canvas(w, h, seed="hud")
    for i, (x0, label) in enumerate(((2, "sanity"), (66, "corruption"))):
        cv.rect(x0, 2, x0 + 58, 21, "void")
        cv.rect(x0 + 1, 3, x0 + 57, 20, "charcoal")
        cv.rect(x0 + 1, 3, x0 + 57, 20, "abyss", 0.4)
        cv.frame(x0, 2, x0 + 58, 21, "brass_lo")
        cv.frame(x0 + 1, 3, x0 + 57, 20, "brass", 0.35)
        # engraved tick marks every 20% so the player can read a value, not just a length
        for t in range(1, 5):
            tx = x0 + 2 + int((56 - 3) * t / 5)
            cv.line(tx, 5, tx, 8, "brass_lo", 0.5)
            cv.line(tx, 15, tx, 18, "brass_lo", 0.35)
        # endcaps
        for (ex, ey) in ((x0, 2), (x0 + 58, 2), (x0, 21), (x0 + 58, 21)):
            cv.set(ex, ey, "brass_hi")
    # separator: a small brass sigil between the two gauges
    cv.ellipse(63, 12, 3.0, 3.0, "brass_lo")
    cv.ellipse(63, 12, 1.4, 1.4, "void")
    cv.set(63, 12, "veil")
    return cv


def gui_ritual_background():
    """Ritual screen: 176x166, dark slate altar with a stability dial."""
    w, h = 176, 166
    cv = Canvas(w, h, seed="ritualgui")
    cv.fill("void")
    cv.rect(0, 0, w - 1, h - 1, "charcoal")
    cv.frame(0, 0, w - 1, h - 1, "void")
    cv.frame(2, 2, w - 3, h - 3, "slate")
    cv.frame(3, 3, w - 4, h - 4, "brass_lo", 0.7)
    cv.value_noise("charcoal", "ash", scale=7.0, alpha=0.25, seed=31)
    # title plate
    cv.rect(6, 6, w - 7, 16, "umber")
    cv.rect(7, 7, w - 8, 15, "leather")
    cv.line(7, 7, w - 8, 7, "tan", 0.6)
    cv.frame(6, 6, w - 7, 16, "void")
    # left: slot grid area, right: result + stability dial
    cv.rect(8, 22, 96, 104, "pitch")
    cv.frame(8, 22, 96, 104, "brass_lo")
    for i in range(4):
        for j in range(3):
            x, y = 12 + j * 22, 26 + i * 22
            cv.rect(x, y, x + 17, y + 17, "void")
            cv.rect(x + 1, y + 1, x + 16, y + 16, "charcoal")
            cv.frame(x, y, x + 17, y + 17, "brass_lo", 0.8)
            cv.set(x + 1, y + 1, "brass_hi", 0.6)
    # stability dial: brass gauge at the top right
    cv.ellipse(w - 40, 56, 26, 26, "pitch")
    cv.ellipse(w - 40, 56, 25, 25, "brass_lo", filled=False)
    cv.ellipse(w - 40, 56, 22, 22, "charcoal")
    for a in range(180, 361, 15):
        x0 = int(w - 40 + math.cos(math.radians(a)) * 20)
        y0 = int(56 + math.sin(math.radians(a)) * 20)
        x1 = int(w - 40 + math.cos(math.radians(a)) * 16)
        y1 = int(56 + math.sin(math.radians(a)) * 16)
        cv.line(x0, y0, x1, y1, "brass", 0.7)
    cv.ellipse(w - 40, 56, 3, 3, "brass")
    cv.set(w - 40, 56, "gold")
    # danger strip: a red lacquer band the java layer reveals when stability is low
    cv.rect(8, 110, 168, 120, "blood_lo")
    cv.rect(9, 111, 167, 119, "blood", 0.5)
    cv.line(9, 111, 167, 111, "crimson", 0.6)
    cv.rect(8, 124, w - 8, 160, "parch", 0.15)
    cv.frame(8, 124, w - 8, 160, "brass_lo", 0.6)
    return cv


def gui_book_page():
    """Codex page: 192x192 parchment with faint ruled lines and a watermark sigil."""
    w, h = 192, 192
    cv = gui_parchment_panel(Canvas(w, h, seed="book"), w, h, title=False)
    cv.ellipse(w // 2, h // 2, 52, 52, "parch_lo", 0.18, filled=False)
    for a in range(0, 360, 45):
        x0 = int(w // 2 + math.cos(math.radians(a)) * 52)
        y0 = int(h // 2 + math.sin(math.radians(a)) * 52)
        x1 = int(w // 2 + math.cos(math.radians(a)) * 44)
        y1 = int(h // 2 + math.sin(math.radians(a)) * 44)
        cv.line(x0, y0, x1, y1, "parch_lo", 0.18)
    for y in range(16, h - 14, 9):
        cv.line(14, y, w - 15, y, "parch_lo", 0.25)
    cv.line(w // 2, 12, w // 2, h - 12, "parch_lo", 0.25)
    return cv


def gen_widgets():
    """Buttons, slots, arrows, bars - the shared widget sheet."""
    widgets = {}

    for state, tone in (("normal", "leather"), ("hover", "tan"), ("disabled", "stone")):
        cv = Canvas(64, 16, seed=f"btn{state}")
        cv.fill("void")
        cv.rect(0, 0, 63, 15, "void")
        cv.rect(1, 1, 62, 14, tone)
        for y in range(1, 15):
            for x in range(1, 63):
                t = (x + y * 2) / 80.0
                cv.set(x, y, mix(lighter(tone, 0.14), darker(tone, 0.20), t))
        cv.frame(1, 1, 62, 14, "brass_lo")
        cv.frame(0, 0, 63, 15, "void")
        for (x, y) in ((2, 2), (61, 2), (2, 13), (61, 13)):
            cv.set(x, y, "brass_hi")
        cv.rect(3, 2, 60, 2, "brass", 0.35)
        widgets[f"button_{state}"] = cv

    # slot: recessed brass-lined niche
    cv = Canvas(20, 20, seed="slot")
    cv.fill("void")
    cv.rect(1, 1, 18, 18, "charcoal")
    cv.rect(2, 2, 17, 17, "pitch")
    cv.rect(2, 2, 17, 2, "void")
    cv.rect(2, 2, 2, 17, "void")
    cv.rect(17, 16, 17, 17, "ash", 0.5)
    cv.rect(16, 17, 17, 17, "ash", 0.35)
    widgets["slot"] = cv

    # arrow: engraved brass arrow with a thread motif
    cv = Canvas(24, 16, seed="arrow")
    cv.fill(0, 0)
    for y in range(16):
        for x in range(24):
            cv.px[y * 24 + x] = (0, 0, 0, 0)
    cv.poly([(1, 6), (14, 6), (14, 2), (22, 8), (14, 14), (14, 10), (1, 10)], "brass_lo")
    cv.poly([(2, 7), (14, 7), (14, 3), (20, 8), (14, 13), (14, 9), (2, 9)], "brass")
    cv.line(3, 8, 13, 8, "brass_hi", 0.6)
    widgets["arrow"] = cv

    # progress bar fills: sanity (cold spirit) and corruption (crimson)
    for name, top, bottom in (("bar_sanity", "cyan_mut", "blue_lo"),
                              ("bar_corruption", "crimson", "blood_lo"),
                              ("bar_digestion", "brass_hi", "brass_lo"),
                              ("bar_danger", "rust", "blood_lo")):
        cv = Canvas(56, 18, seed=name)
        for y in range(18):
            t = y / 17.0
            cv.rect(0, y, 55, y, mix(lighter(top, 0.25), darker(bottom, 0.25), t))
        # filigree highlight so the bar is not a flat gradient
        cv.line(0, 1, 55, 1, "white", 0.30)
        cv.line(0, 16, 55, 16, "void", 0.45)
        for x in range(0, 56, 7):
            cv.line(x, 2, x, 15, "void", 0.10)
        cv.noise("void", 0.06, 0.20)
        widgets[name] = cv

    # scrollbar / slider handle
    cv = Canvas(12, 15, seed="handle")
    cv.fill(0, 0)
    for y in range(15):
        for x in range(12):
            cv.px[y * 12 + x] = (0, 0, 0, 0)
    cv.rect(1, 1, 10, 13, "leather")
    cv.rect(1, 1, 10, 2, "tan")
    cv.rect(1, 12, 10, 13, "umber")
    cv.frame(0, 0, 11, 14, "void")
    cv.rect(3, 4, 8, 4, "brass_lo")
    cv.rect(3, 7, 8, 7, "brass_lo")
    cv.rect(3, 10, 8, 10, "brass_lo")
    widgets["handle"] = cv

    # sequence advancement glyph strip: 10 small brass medallions (sequence 9 -> 0)
    cv = Canvas(160, 16, seed="seqstrip")
    cv.fill(0, 0)
    for y in range(16):
        for x in range(160):
            cv.px[y * 16 + x] = (0, 0, 0, 0)
    for i in range(10):
        cx = 8 + i * 16
        cv.ellipse(cx, 8, 6.4, 6.4, "brass_lo")
        cv.ellipse(cx, 8, 5.0, 5.0, "void")
        cv.ellipse(cx, 8, 5.0, 5.0, "brass", 0.0, filled=False)
        cv.set(cx, 3, "brass_hi")
        cv.set(cx, 7, "veil", 0.5)
    widgets["sequence_strip"] = cv

    return widgets


def gui_parchment_slot():
    """Recipe-slot parchment card for the codex (ingredient + name + rarity pip)."""
    cv = Canvas(38, 38, seed="recipepad")
    cv.fill(0, 0)
    for y in range(38):
        for x in range(38):
            cv.px[y * 38 + x] = (0, 0, 0, 0)
    cv.rect(1, 1, 36, 36, "parch")
    cv.value_noise("parch_lo", "parch_hi", scale=5.0, alpha=0.3, seed=5)
    cv.frame(1, 1, 36, 36, "parch_lo")
    cv.frame(0, 0, 37, 37, "void", 0.9)
    cv.rect(2, 2, 35, 3, "parch_hi")
    return cv


def gen_gui():
    os.makedirs(GUI_OUT, exist_ok=True)
    cv = gui_pathway_background()
    cv.save(os.path.join(GUI_OUT, "pathway.png"))
    gui_hud_bar().save(os.path.join(GUI_OUT, "hud.png"))
    gui_ritual_background().save(os.path.join(GUI_OUT, "ritual.png"))
    gui_book_page().save(os.path.join(GUI_OUT, "codex_page.png"))
    gui_parchment_slot().save(os.path.join(GUI_OUT, "recipe_card.png"))
    # slot grid icons used by the ritual screen
    for name, cv in gen_widgets().items():
        cv.save(os.path.join(GUI_OUT, f"{name}.png"))
    # emblem medallions for each pathway (32x32), drawn by the Pathway screen
    emblems = {
        "the_fool": ("orchid", "veil"), "the_sun": ("brass", "gold"), "death": ("moss_hi", "sage"),
        "the_door": ("blue", "cyan_mut"), "visionary": ("cyan_mut", "spirit"),
        "black_emperor": ("brass_lo", "brass_hi"), "error": ("crimson", "rose"),
        "mother": ("sage", "parch_hi"),
    }
    for name, (mid, hi) in emblems.items():
        cv = Canvas(32, 32, seed=name)
        cv.ellipse(16, 16, 15, 15, "void")
        cv.ellipse(16, 16, 14, 14, "brass_lo")
        cv.ellipse(16, 16, 12.5, 12.5, "pitch")
        cv.ellipse(16, 16, 11.5, 11.5, mid, 0.35)
        cv.ellipse(16, 16, 12.5, 12.5, "brass", 0.5, filled=False)
        # each emblem gets a distinct inscrutable mark
        if name == "the_fool":
            cv.poly([(16, 6), (22, 22), (10, 22)], mid)
            cv.poly([(16, 10), (20, 20), (12, 20)], "void")
            cv.set(16, 15, hi)
        elif name == "the_sun":
            cv.ellipse(16, 16, 4.4, 4.4, hi)
            for a in range(0, 360, 45):
                cv.line(int(16 + math.cos(math.radians(a)) * 6), int(16 + math.sin(math.radians(a)) * 6),
                        int(16 + math.cos(math.radians(a)) * 10), int(16 + math.sin(math.radians(a)) * 10), mid)
        elif name == "death":
            cv.line(16, 8, 16, 24, mid, 0.9)
            cv.line(12, 12, 20, 12, mid, 0.9)
            cv.ellipse(16, 9, 3.0, 3.0, mid, 0.8, filled=False)
        elif name == "the_door":
            cv.rect(12, 8, 19, 24, mid, 0.8)
            cv.rect(14, 10, 17, 24, "void")
            cv.set(18, 16, hi)
        elif name == "visionary":
            cv.ellipse(16, 16, 8.0, 5.0, mid, 0.85, filled=False)
            cv.ellipse(16, 16, 3.0, 3.0, hi)
            cv.ellipse(16, 16, 1.2, 1.2, "void")
        elif name == "black_emperor":
            cv.rect(10, 12, 21, 22, mid)
            cv.poly([(8, 12), (16, 6), (23, 12)], hi)
            cv.rect(14, 16, 17, 22, "void")
        elif name == "error":
            cv.line(9, 9, 22, 22, mid, 0.9)
            cv.line(22, 9, 9, 22, mid, 0.9)
            cv.ellipse(16, 16, 5.0, 5.0, hi, 0.4, filled=False)
        elif name == "mother":
            cv.line(16, 24, 16, 14, mid, 0.9)
            for a in (200, 240, 300, 340):
                cv.line(16, 14, int(16 + math.cos(math.radians(a)) * 8),
                        int(14 + math.sin(math.radians(a)) * 8), mid, 0.8)
            cv.ellipse(16, 12, 3.0, 3.0, hi, 0.8)
        cv.glow(mid, 1, 0.18)
        cv.save(os.path.join(GUI_OUT, f"emblem_{name}.png"))
    # mod logo: 256x256, used by the mods list and the codex splash
    logo = Canvas(64, 64, seed="logo")
    logo.fill("void")
    logo.ellipse(32, 32, 30, 30, "pitch")
    logo.ellipse(32, 32, 28, 28, "void", 0.9)
    logo.ellipse(32, 32, 27, 27, "brass_lo", 0.9, filled=False)
    logo.ellipse(32, 32, 25, 25, "orchid", 0.10)
    for a in range(0, 360, 30):
        logo.set(int(32 + math.cos(math.radians(a)) * 27), int(32 + math.sin(math.radians(a)) * 27), "brass", 0.8)
    logo.poly([(32, 14), (42, 44), (22, 44)], "veil", 0.20)
    logo.poly([(32, 20), (39, 42), (25, 42)], "orchid", 0.15)
    logo.ellipse(32, 34, 6.0, 8.0, "void")
    logo.ellipse(32, 34, 5.0, 7.0, "violet_lo", 0.9)
    logo.set(32, 26, "veil")
    logo.set(30, 30, "orchid", 0.7)
    logo.ellipse(32, 34, 2.0, 3.0, "white", 0.55)
    logo.glow("orchid", 1, 0.22)
    logo.save(os.path.join(ROOT, "src", "main", "resources", "pathwaysofthebeyond.png"))
    print(f"[gui] wrote GUI textures -> {GUI_OUT}")


def main():
    gen_effects()
    gen_particles()
    gen_gui()


if __name__ == "__main__":
    main()
