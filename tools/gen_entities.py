"""
Entity texture generator.

Skins use the vanilla humanoid UV layout (64x64) so the Java models - which extend
HumanoidModel with extra asymmetric parts - map correctly without custom UV authoring:

    head   (0,0)  right(0,8) front(8,8) left(16,8) back(24,8) top(8,0) bottom(16,0)
    body  (16,16) right(16,20) front(20,20) left(28,20) back(32,20) top(20,16) bottom(28,16)
    r.arm (40,16) right(40,20) front(44,20) left(48,20) back(52,20) top(44,16) bottom(48,16)
    l.arm (32,48) L.leg (16,48)   r.leg (0,16)
    hat layer / overlay: +32 in x for humanoid layer 1 (used for glowing eyes and wounds)

Bosses use 128x128 with an extended layout declared next to each model class.

Run:  python3 tools/gen_entities.py
"""
from __future__ import annotations

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from pixelart import Canvas, mix, darker, lighter, PALETTE  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "pathwaysofthebeyond",
                   "textures", "entity")


# ---------------------------------------------------------------------------------------
# UV layout helpers
# ---------------------------------------------------------------------------------------
# name -> (u, v, w, h, depth) of each cube; face offsets follow the vanilla layout
PARTS = {
    "head": (0, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4),
    "r_arm": (40, 16, 4, 12, 4),
    "l_arm": (32, 48, 4, 12, 4),
    "r_leg": (0, 16, 4, 12, 4),
    "l_leg": (16, 48, 4, 12, 4),
}


def part_faces(part, overlay=False):
    """Return {face: (x, y, w, h)} texture rectangles for a humanoid part."""
    u, v, w, h, d = PARTS[part]
    if overlay:
        u += 32  # layer-1 overlay region for head/body/etc is +32 in x for vanilla skins
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def fill_face(cv, part, face, colour, overlay=False, alpha=1.0):
    x, y, w, h = part_faces(part, overlay)[face]
    cv.rect(x, y, x + w - 1, y + h - 1, colour, alpha)


def shade_face(cv, part, face, base, overlay=False, amount=0.22, seed=0):
    """Fill a face with base colour plus vertical light falloff and grain."""
    x, y, w, h = part_faces(part, overlay)[face]
    for j in range(h):
        t = j / max(1, h - 1)
        tone = mix(lighter(base, amount * 0.7), darker(base, amount), t)
        for i in range(w):
            cv.set(x + i, y + j, tone)
    cv.noise(darker(base, 0.3), 0.10, 0.30, mask=lambda xx, yy: x <= xx < x + w and y <= yy < y + h,
             seed=seed + hash(face) % 100)


def punch(cv, px, py, colour, alpha=1.0):
    cv.set(px, py, colour, alpha)


# ---------------------------------------------------------------------------------------
# creatures
# ---------------------------------------------------------------------------------------
def skin_base(cv, base, overlay_base=None, seed=1, shade=0.24):
    for part in PARTS:
        for face in ("top", "bottom", "right", "front", "left", "back"):
            shade_face(cv, part, face, base, amount=shade, seed=seed)


def paint_head_front(cv, base, overlay=False, eyes=None):
    x, y, w, h = part_faces("head", overlay)["front"]
    if eyes:
        ex1, ex2, ey, colour, size = eyes
        for i in range(size):
            for j in range(size):
                cv.set(x + ex1 + i, y + ey + j, colour)
                cv.set(x + ex2 + i, y + ey + j, colour)
        # a dim rim so the eyes read at distance
        for i in range(size + 2):
            cv.set(x + ex1 - 1 + i, y + ey - 1, colour, 0.35)
            cv.set(x + ex2 - 1 + i, y + ey - 1, colour, 0.35)


def creature_watcher():
    """Very tall, very thin, symmetrical. A blank face and two pale points of attention."""
    cv = Canvas(64, 64, seed="watcher")
    cloth = PALETTE["charcoal"]
    skin_base(cv, cloth, seed=3, shade=0.20)
    # long coat: darker front panel with brass buttons
    x, y, w, h = part_faces("body")["front"]
    cv.rect(x, y, x + w - 1, y + h - 1, "pitch")
    for j in range(2, 10, 2):
        cv.set(x + 3, y + j, "brass_lo")
    # high collar on the head sides
    for face in ("right", "left", "back"):
        fill_face(cv, "head", face, "pitch")
    x, y, w, h = part_faces("head")["front"]
    cv.rect(x, y + 5, x + w - 1, y + h - 1, "pitch")     # mask/cloth over the lower face
    paint_head_front(cv, cloth, eyes=(2, 5, 2, "white", 1))
    # overlay layer: the eyes burn through, plus a thin vertical seam
    paint_head_front(cv, cloth, overlay=True, eyes=(2, 5, 2, "white", 1))
    for j in range(8):
        cv.set(40 + 3, j + 0, "spirit_hi", 0.5)
    # sleeves are lighter than the coat; hands are bare and too long
    for arm in ("r_arm", "l_arm"):
        for face in ("front", "left", "right", "back"):
            fill_face(cv, arm, face, "ash")
        x, y, w, h = part_faces(arm)["bottom"]
        cv.rect(x, y, x + w - 1, y + h - 1, PALETTE["pale"])
    cv.noise("stone", 0.08, 0.25, seed=9)
    return cv


def creature_hollow():
    """Humanoid silhouette, no face, hunched. Meant to be seen only in fog."""
    cv = Canvas(64, 64, seed="hollow")
    base = PALETTE["void"] if "void" in PALETTE else 0x07060A
    skin_base(cv, "pitch", seed=5, shade=0.14)
    # the body is a smudge of darker black with a smear where a face should be
    x, y, w, h = part_faces("head")["front"]
    cv.rect(x + 1, y + 1, x + w - 2, y + h - 2, "void")
    cv.rect(x + 2, y + 3, x + 5, y + 3, "ash", 0.30)     # where eyes were
    cv.rect(x + 2, y + 6, x + 5, y + 6, "ash", 0.22)
    for face in ("right", "left", "back", "top"):
        fill_face(cv, "head", face, "void")
    # ragged hem and long arms
    for arm in ("r_arm", "l_arm"):
        for face in ("front", "left", "right", "back"):
            fill_face(cv, arm, face, "charcoal")
    for leg in ("r_leg", "l_leg"):
        x, y, w, h = part_faces(leg)["front"]
        for i in range(w):
            cv.set(x + i, y + h - 1, "void")
            if i % 2:
                cv.set(x + i, y + h - 2, "void")
    cv.noise("charcoal", 0.14, 0.35, seed=11)
    return cv


def creature_whispering_husk():
    """A person whose jaw has come loose and whose throat is full of other voices."""
    cv = Canvas(64, 64, seed="husk")
    flesh = PALETTE["parch_lo"]
    skin_base(cv, flesh, seed=7, shade=0.24)
    # robe in desaturated green with crimson stains
    for part in ("body", "r_leg", "l_leg"):
        for face in ("front", "left", "right", "back"):
            fill_face(cv, part, face, "moss")
    x, y, w, h = part_faces("body")["front"]
    cv.rect(x, y, x + w - 1, y + h - 1, "moss_lo")
    for (sx, sy, r) in ((2, 3, 1), (5, 6, 1), (3, 9, 2)):
        cv.ellipse(x + sx, y + sy, r + 1, r, "blood", 0.7)
    # head: split jaw, no eyes - just sealed sockets
    x, y, w, h = part_faces("head")["front"]
    cv.rect(x, y, x + w - 1, y + h - 1, flesh)
    cv.rect(x + 2, y + 2, x + 3, y + 3, "blood_lo")
    cv.rect(x + 5, y + 2, x + 6, y + 3, "blood_lo")
    cv.line(x + 3, y + 4, x + 5, y + 4, "blood", 0.8)         # stitches over the eyes
    cv.rect(x + 1, y + 5, x + 6, y + 5, "blood_lo", 0.8)
    for i in range(7):                                        # a jaw hanging open
        cv.set(x + i, y + 6, "void")
        cv.set(x + i, y + 7, "blood_lo" if i % 2 else "void")
    fill_face(cv, "head", "bottom", "void")
    # throat overlay: the mouths of the voices
    for i in range(4):
        cv.set(32 + 8 + 1 + i * 2, 8 + 3, "rust", 0.8)
        cv.set(32 + 8 + 1 + i * 2, 8 + 4, "blood", 0.7)
    cv.noise("bone", 0.10, 0.25, seed=13)
    return cv


def creature_veil_tenant():
    """Spirit-world hunter: drawn-out neck, floating hands, translucent edges."""
    cv = Canvas(64, 64, seed="tenant")
    pale = PALETTE["bone"]
    skin_base(cv, mix(pale, "cyan_mut", 0.45), seed=15, shade=0.20)
    # a translucent shroud: bright core, dissolving hem
    for part in ("body", "r_leg", "l_leg", "r_arm", "l_arm"):
        for face in ("front", "left", "right", "back"):
            x, y, w, h = part_faces(part)[face]
            for j in range(h):
                t = j / max(1, h - 1)
                cv.rect(x, y + j, x + w - 1, y + j, mix("spirit", "abyss", t * 0.8), 1.0)
    # face: two vertical slits and a bright seam
    paint_head_front(cv, pale, eyes=(2, 5, 3, "spirit_hi", 1))
    paint_head_front(cv, pale, overlay=True, eyes=(2, 5, 3, "spirit_hi", 1))
    x, y, w, h = part_faces("head")["front"]
    cv.line(x + 3, y + 5, x + 3, y + 7, "abyss", 0.9)
    cv.line(x + 5, y + 5, x + 5, y + 7, "abyss", 0.9)
    for arm in ("r_arm", "l_arm"):
        x, y, w, h = part_faces(arm)["bottom"]
        cv.rect(x, y, x + w - 1, y + h - 1, "spirit_hi")
    cv.noise("spirit", 0.12, 0.35, seed=17)
    return cv


def creature_marionette():
    """A puppet: wooden ball joints, painted face, cut strings still attached."""
    cv = Canvas(64, 64, seed="marionette")
    wood = PALETTE["tan"]
    skin_base(cv, wood, seed=19, shade=0.28)
    # ball joints at every limb in darker wood
    for part in ("r_arm", "l_arm", "r_leg", "l_leg"):
        for face in ("top", "bottom"):
            fill_face(cv, part, face, darker(PALETTE["brown"], 0.2))
    # painted costume
    for part in ("body", "r_leg", "l_leg"):
        for face in ("front", "left", "right", "back"):
            fill_face(cv, part, face, "crimson")
    x, y, w, h = part_faces("body")["front"]
    cv.rect(x, y + 6, x + w - 1, y + 7, "gold")                # sash
    cv.rect(x, y, x + w - 1, y + 1, "brass_lo")
    # face: painted smile, x eyes
    x, y, w, h = part_faces("head")["front"]
    cv.rect(x, y, x + w - 1, y + h - 1, "parch")
    cv.line(x + 2, y + 2, x + 3, y + 3, "void")
    cv.line(x + 3, y + 2, x + 2, y + 3, "void")
    cv.line(x + 5, y + 2, x + 6, y + 3, "void")
    cv.line(x + 6, y + 2, x + 5, y + 3, "void")
    for i in range(5):
        cv.set(x + 1 + i, y + 5 + (0 if i in (0, 4) else 0), "blood", 0.9)
    cv.set(x + 2, y + 6, "blood", 0.7)
    cv.set(x + 5, y + 6, "blood", 0.7)
    # strings: two pale lines running up the back of the head and shoulders
    for face in ("back", "top"):
        x, y, w, h = part_faces("head")[face]
        cv.line(x + 1, y + h - 1, x + w - 2, y, "bone", 0.5)
    cv.noise("brown", 0.10, 0.3, seed=21)
    return cv


def creature_spirit_wisp():
    """Ambient mote: a 16x16 orb of cold light with a trailing shroud."""
    cv = Canvas(16, 16, seed="wisp")
    core = PALETTE["spirit_hi"]
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 8, y - 7) / 6.5
            if d < 1.0:
                cv.set(x, y, mix(core, "abyss", d), 1.0 - d * 0.35)
    cv.ellipse(8, 7, 2.4, 2.4, "white")
    cv.ellipse(8, 7, 1.2, 1.2, "spirit_hi")
    for i in range(4):     # trailing shroud below
        cv.ellipse(8, 11 + i, 3.2 - i * 0.6, 1.4, "cyan_mut", 0.5 - i * 0.1)
    return cv


# ---------------------------------------------------------------------------------------
# bosses - explicit layouts, shared with the Java models.
# gen_java_layout.py emits these same numbers as constants for the entity model classes,
# so texture and model can never disagree.
# ---------------------------------------------------------------------------------------
def cube_faces(origin, size):
    """Vanilla box-UV unfolding for a cube of size (w, h, d) at texture origin (u, v)."""
    u, v = origin
    w, h, d = size
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


# name -> (origin, size) in a 128x128 sheet
CHOIRMASTER_LAYOUT = {
    "head": ((0, 0), (12, 12, 12)),
    "mitre": ((48, 0), (14, 10, 14)),
    "body": ((0, 32), (16, 24, 8)),
    "right_arm": ((56, 0), (8, 24, 8)),
    "left_arm": ((88, 0), (8, 24, 8)),
    "right_leg": ((0, 64), (8, 24, 8)),
    "left_leg": ((32, 64), (8, 24, 8)),
    "book": ((64, 64), (10, 14, 2)),
}

UNBLINKING_LAYOUT = {
    # a single ellipsoid body, an iris disc, and a lid plate
    "eyeball": ((0, 0), (64, 48, 64)),
    "iris": ((0, 52), (32, 32, 2)),
    "lid": ((96, 0), (64, 16, 64)),
}


def paint_cube(cv, layout, part, painter):
    """Fill each face of a cube; painter(face_name, x, y, w, h) draws one face."""
    origin, size = layout[part]
    for face, (x, y, w, h) in cube_faces(origin, size).items():
        painter(face, x, y, w, h)



def boss_choirmaster():
    """128x128. The precentor of a chapel that sang the wrong hymn.

    Layout matches CHOIRMASTER_LAYOUT exactly (see java/com/pathways/beyond/client/model/BossLayout.java).
    """
    cv = Canvas(128, 128, seed="choirmaster")

    def cloth(face, x, y, w, h, base, accent=None, seed=0):
        for j in range(h):
            t = j / max(1, h - 1)
            for i in range(w):
                tone = mix(lighter(base, 0.18), darker(base, 0.30), t)
                cv.set(x + i, y + j, tone)
        if accent:
            for i in range(w):
                if (i + seed) % 3 == 0:
                    cv.set(x + i, y + h - 1, accent, 0.6)

    # vestments: alternating bands of black, crimson and brass
    def vest(face, x, y, w, h):
        for j in range(h):
            band = (j // 6) % 3
            base = ("pitch", "blood", "brass_lo")[band]
            for i in range(w):
                cv.set(x + i, y + j, mix(base, "void", ((i * 3 + j) % 7) / 14.0))
    paint_cube(cv, CHOIRMASTER_LAYOUT, "body", vest)
    for leg in ("right_leg", "left_leg"):
        paint_cube(cv, CHOIRMASTER_LAYOUT, leg,
                   lambda f, x, y, w, h: cloth(f, x, y, w, h, "blood", "brass_lo", 2))
    for arm in ("right_arm", "left_arm"):
        paint_cube(cv, CHOIRMASTER_LAYOUT, arm,
                   lambda f, x, y, w, h: cloth(f, x, y, w, h, "charcoal", None, 1))
        # pale cuffs at the bottom of each sleeve
        origin, size = CHOIRMASTER_LAYOUT[arm]
        for face, (x, y, w, h) in cube_faces(origin, size).items():
            if face in ("front", "left", "right", "back"):
                cv.rect(x, y + h - 4, x + w - 1, y + h - 1, "parch_lo")
    # head: sunken eyes, a vertical row of small mouths, forehead band
    def head(face, x, y, w, h):
        cloth(face, x, y, w, h, "parch", None, 0)
    paint_cube(cv, CHOIRMASTER_LAYOUT, "head", head)
    hx, hy, hw, hh = cube_faces(*CHOIRMASTER_LAYOUT["head"])["front"]
    cv.rect(hx, hy, hx + hw - 1, hy + 1, "brass_lo")
    cv.set(hx + hw // 2, hy, "gold")
    for i in range(2):
        cv.rect(hx + 2, hy + 3 + i * 4, hx + 3, hy + 4 + i * 4, "void")
        cv.rect(hx + hw - 4, hy + 3 + i * 4, hx + hw - 3, hy + 4 + i * 4, "void")
    for i in range(6):                     # the mouths
        cv.rect(hx + 4, hy + 3 + i, hx + hw - 5, hy + 3 + i, "blood" if i % 2 else "void")
    # mitre: crimson with gold ribs
    def mitre(face, x, y, w, h):
        cloth(face, x, y, w, h, "blood", None, 3)
        for i in range(0, w, 4):
            cv.line(x + i, y, x + i, y + h - 1, "gold", 0.45)
    paint_cube(cv, CHOIRMASTER_LAYOUT, "mitre", mitre)
    # the hymnal it holds open
    paint_cube(cv, CHOIRMASTER_LAYOUT, "book", lambda f, x, y, w, h: cv.rect(x, y, x + w - 1, y + h - 1, "pitch"))
    bx, by, bw, bh = cube_faces(*CHOIRMASTER_LAYOUT["book"])["front"]
    cv.rect(bx, by, bx + bw - 1, by + 1, "brass")
    cv.rect(bx + bw // 2, by, bx + bw // 2, by + bh - 1, "brass_lo")
    cv.ellipse(bx + bw // 2, by + bh // 2, 2.4, 3.0, "violet_lo")
    cv.set(bx + bw // 2, by + bh // 2, "veil")
    return cv


def boss_choirmaster_glow():
    """Emissive companion texture: the eyes, the mouths, the mitre ribs and the hymnal."""
    cv = Canvas(128, 128, seed="choirmaster_glow")
    hx, hy, hw, hh = cube_faces(*CHOIRMASTER_LAYOUT["head"])["front"]
    for i in range(2):
        cv.rect(hx + 2, hy + 3 + i * 4, hx + 3, hy + 4 + i * 4, "spirit")
        cv.rect(hx + hw - 4, hy + 3 + i * 4, hx + hw - 3, hy + 4 + i * 4, "spirit")
    for i in range(6):
        cv.rect(hx + 4, hy + 3 + i, hx + hw - 5, hy + 3 + i, "crimson" if i % 2 else "blood")
    mx, my, mw, mh = cube_faces(*CHOIRMASTER_LAYOUT["mitre"])["front"]
    for i in range(0, mw, 4):
        cv.line(mx + i, my, mx + i, my + mh - 1, "gold", 0.6)
    bx, by, bw, bh = cube_faces(*CHOIRMASTER_LAYOUT["book"])["front"]
    cv.ellipse(bx + bw // 2, by + bh // 2, 3.0, 3.6, "veil")
    cv.set(bx + bw // 2, by + bh // 2, "white")
    return cv


def boss_unblinking():
    """64x64. One enormous eye: sclera, iris, pupil, lashes, and a lid that closes."""
    cv = Canvas(64, 64, seed="unblinking")
    # sclera (whole texture is the eye)
    for y in range(64):
        for x in range(64):
            d = math.hypot((x - 32) / 30.0, (y - 32) / 26.0)
            if d <= 1.02:
                t = min(1.0, d)
                cv.set(x, y, mix("bone", "smoke", t * 0.35))
    # radial vessels
    for a in range(0, 360, 7):
        r0, r1 = 10 + (a % 3) * 4, 29
        for r in range(r0, r1):
            x = int(32 + math.cos(math.radians(a)) * r * 0.95)
            y = int(32 + math.sin(math.radians(a)) * r * 0.82)
            cv.set(x, y, "rose", 0.20 + 0.2 * ((a + r) % 5) / 5.0)
    # iris
    for y in range(64):
        for x in range(64):
            d = math.hypot(x - 32, y - 32)
            if d <= 16:
                tone = mix("navy", "blue", d / 16.0)
                cv.set(x, y, mix(tone, "abyss", (d / 16.0) ** 2 * 0.6))
            if 14 <= d <= 16:
                cv.set(x, y, "blue_lo")
    for a in range(0, 360, 24):     # iris fibres
        for r in range(5, 16):
            x = int(32 + math.cos(math.radians(a)) * r)
            y = int(32 + math.sin(math.radians(a)) * r)
            cv.set(x, y, "spirit", 0.18)
    # pupil: a void that does not reflect
    for y in range(64):
        for x in range(64):
            d = math.hypot((x - 32) / 1.0, (y - 32) / 1.15)
            if d <= 7:
                cv.set(x, y, "void")
    cv.ellipse(27, 26, 2.2, 2.2, "white", 0.6)      # single specular highlight
    # lids (top/bottom bands) and lashes
    for y in range(0, 12):
        for x in range(64):
            cv.set(x, y, mix("charcoal", "pitch", y / 12.0))
    for y in range(52, 64):
        for x in range(64):
            cv.set(x, y, mix("charcoal", "pitch", (64 - y) / 12.0))
    for i in range(0, 64, 3):
        cv.line(i, 12, i + 1, 7, "void")
        cv.line(i, 51, i + 1, 56, "void")
    cv.glow("blue", radius=1, alpha=0.25)
    return cv


def player_spirit_form():
    """Spirit-form overlay skin: bone-white with bright seams, used by the spirit render layer."""
    cv = Canvas(64, 64, seed="spiritform")
    for part in PARTS:
        for face in ("top", "bottom", "right", "front", "left", "back"):
            x, y, w, h = part_faces(part)[face]
            for j in range(h):
                t = j / max(1, h - 1)
                cv.rect(x, y + j, x + w - 1, y + j, mix("spirit_hi", "abyss", t * 0.75))
    paint_head_front(cv, "spirit_hi", eyes=(2, 5, 2, "white", 2))
    return cv


def boss_unblinking_glow():
    """Emissive companion: the iris and pupil burn; the sclera does not."""
    cv = Canvas(64, 64, seed="unblinking_glow")
    for y in range(64):
        for x in range(64):
            d = math.hypot(x - 32, y - 32)
            if d <= 7:
                cv.set(x, y, "veil" if d > 5 else "white")
            elif d <= 16:
                cv.set(x, y, "blue_lo" if d < 15 else "blue", 0.85)
    for a in range(0, 360, 24):
        for r in range(6, 16):
            cv.set(int(32 + math.cos(math.radians(a)) * r),
                   int(32 + math.sin(math.radians(a)) * r), "spirit", 0.35)
    return cv


TEXTURES = {
    "choirmaster_of_the_veil_glow": boss_choirmaster_glow,
    "the_unblinking_glow": boss_unblinking_glow,
    "watcher": creature_watcher,
    "hollow": creature_hollow,
    "whispering_husk": creature_whispering_husk,
    "veil_tenant": creature_veil_tenant,
    "marionette": creature_marionette,
    "spirit_wisp": creature_spirit_wisp,
    "choirmaster_of_the_veil": boss_choirmaster,
    "the_unblinking": boss_unblinking,
    "player_spirit_form": player_spirit_form,
}


def emit_java_layout():
    """Write the boss UV layouts as Java constants so models cannot drift from textures."""
    java_dir = os.path.join(ROOT, "src", "main", "java", "com", "pathways", "beyond",
                            "client", "model")
    os.makedirs(java_dir, exist_ok=True)
    lines = [
        "package com.pathways.beyond.client.model;",
        "",
        "/**",
        " * Boss skin UV layouts, in texture pixels. GENERATED by tools/gen_entities.py -",
        " * do not hand-edit. Each entry is {{u, v}, {{w, h, d}}} for a box-UV unfolded cube,",
        " * exactly as vanilla unfolds skins: top, bottom, right, front, left, back.",
        " */",
        "public final class BossLayout {",
        "    private BossLayout() {}",
        "",
        "    /** Choirmaster sheet: 128x128. */",
        "    public static final int[][] CHOIRMASTER = {",
    ]
    for part, (origin, size) in CHOIRMASTER_LAYOUT.items():
        lines.append(f"            // {part}")
        lines.append(f"            {{{origin[0]}, {origin[1]}, {size[0]}, {size[1]}, {size[2]}}},")
    lines += [
        "    };",
        "",
        "    /** The Unblinking sheet: 128x128 (eyeball 64x48x64, iris disc, lid plate). */",
        "    public static final int[][] UNBLINKING = {",
    ]
    for part, (origin, size) in UNBLINKING_LAYOUT.items():
        lines.append(f"            // {part}")
        lines.append(f"            {{{origin[0]}, {origin[1]}, {size[0]}, {size[1]}, {size[2]}}},")
    lines += [
        "    };",
        "",
        "    /** Index into the layout arrays. */",
        "    public static final int CHOIRMASTER_HEAD = 0;",
        "    public static final int CHOIRMASTER_MITRE = 1;",
        "    public static final int CHOIRMASTER_BODY = 2;",
        "    public static final int CHOIRMASTER_RIGHT_ARM = 3;",
        "    public static final int CHOIRMASTER_LEFT_ARM = 4;",
        "    public static final int CHOIRMASTER_RIGHT_LEG = 5;",
        "    public static final int CHOIRMASTER_LEFT_LEG = 6;",
        "    public static final int CHOIRMASTER_BOOK = 7;",
        "}",
        "",
    ]
    with open(os.path.join(java_dir, "BossLayout.java"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print("[entities] wrote client/model/BossLayout.java")


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, painter in TEXTURES.items():
        cv = painter()
        cv.save(os.path.join(OUT, f"{name}.png"))
    emit_java_layout()
    print(f"[entities] wrote {len(TEXTURES)} entity textures -> {OUT}")


if __name__ == "__main__":
    main()
