"""
Pathways of the Beyond - pixel art toolkit.

A tiny deterministic pixel-art DSL used to generate every texture in the mod from
ONE locked palette, so the whole mod shares a single visual language:

    black / charcoal / dark brown / desaturated green / deep crimson /
    muted gold / cold blue / pale supernatural white

Design rules enforced by this toolkit (see docs/ART_BIBLE.md):
  * hard 1px silhouettes, no anti-aliasing inside a sprite
  * a sprite uses at most 12 palette entries (drop 2-3 darker ramps for edges)
  * top-left light source, bottom-right shadow, 1px dark outline on the outside
  * "wear" is always applied last so decorations look carved, not pasted
"""
from __future__ import annotations

import math
import os
import random
from PIL import Image, ImageDraw

# --------------------------------------------------------------------------------------
# Palette - THE visual identity of the mod. Every texture is quantised to this table.
# --------------------------------------------------------------------------------------
PALETTE: dict[str, int] = {
    # --- neutrals: black -> bone ---
    "void": 0x07060A,
    "pitch": 0x0E0C10,
    "charcoal": 0x191519,
    "ash": 0x262128,
    "slate": 0x35303A,
    "stone": 0x4A4550,
    "grey": 0x6B6570,
    "smoke": 0x928C98,
    "bone": 0xC9C3CE,
    "pale": 0xE8E3EC,
    "white": 0xF6F3F8,
    # --- dark brown / leather / wood ---
    "umber": 0x2A1D13,
    "brown": 0x3E2A1B,
    "leather": 0x5C3F26,
    "tan": 0x7E5A34,
    "khaki": 0xA07B4C,
    "parch_hi": 0xE4D3A8,
    "parch": 0xCDB583,
    "parch_lo": 0xA89059,
    # --- muted brass / gold ---
    "brass_lo": 0x6B5426,
    "brass": 0x9C7C3A,
    "brass_hi": 0xD4B265,
    "gold": 0xEBD08A,
    # --- desaturated green ---
    "moss_lo": 0x1E2A21,
    "moss": 0x2F4232,
    "moss_hi": 0x4C6749,
    "sage": 0x6F8A63,
    # --- deep crimson ---
    "blood_lo": 0x2B070D,
    "blood": 0x5A0F17,
    "crimson": 0x8C1A24,
    "rust": 0xB03A34,
    "rose": 0xD0706A,
    # --- cold blue / spirit ---
    "abyss": 0x0A1424,
    "navy": 0x13273F,
    "blue_lo": 0x21456B,
    "blue": 0x3A6C96,
    "cyan_mut": 0x63A2B8,
    "spirit": 0xA8DCE2,
    "spirit_hi": 0xDFF6F7,
    # --- supernatural accents (restrained) ---
    "violet_lo": 0x2A1B3C,
    "violet": 0x4B3068,
    "orchid": 0x7B5AA0,
    "veil": 0xB49BD0,
}


def rgb(name_or_int) -> tuple[int, int, int, int]:
    """Accepts a palette key ('spirit'), a packed int (0xRRGGBB), a '#rrggbb' string,
    or an existing (r, g, b[, a]) tuple."""
    if isinstance(name_or_int, str):
        if name_or_int.startswith("#"):
            v = int(name_or_int[1:].lstrip("#"), 16)
            return (v >> 16 & 255, v >> 8 & 255, v & 255, 255)
        return rgb(PALETTE[name_or_int])
    if isinstance(name_or_int, int):
        return (name_or_int >> 16 & 255, name_or_int >> 8 & 255, name_or_int & 255, 255)
    px = tuple(name_or_int)
    if len(px) == 4:
        return px
    return (px[0], px[1], px[2], 255)


def mix(a, b, t: float):
    """Linear blend between two palette colours (or packed ints)."""
    ca, cb = rgb(a), rgb(b)
    return tuple(int(round(ca[i] + (cb[i] - ca[i]) * t)) for i in range(4))


def lighter(c, t=0.3):
    return mix(c, "white", t)


def darker(c, t=0.3):
    return mix(c, "void", t)


# --------------------------------------------------------------------------------------
# Canvas
# --------------------------------------------------------------------------------------
class Canvas:
    """A tiny RGBA raster with pixel-art primitives."""

    def __init__(self, w: int, h: int, seed: int | str = 0):
        self.w, self.h = w, h
        self.px = [(0, 0, 0, 0)] * (w * h)
        self.rng = random.Random(seed)

    # -- basics ------------------------------------------------------------------
    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[y * self.w + x]
        return (0, 0, 0, 0)

    def set(self, x, y, c, alpha=1.0):
        if not (0 <= x < self.w and 0 <= y < self.h):
            return
        if isinstance(c, str) or isinstance(c, int):
            c = rgb(c)
        if alpha < 1.0:
            c = mix(self.get(x, y), c[:3], alpha)
            if c[3] == 0 and alpha < 1.0:
                c = (c[0], c[1], c[2], int(255 * alpha))
        self.px[y * self.w + x] = tuple(c)

    def fill(self, c, alpha=1.0):
        for y in range(self.h):
            for x in range(self.w):
                self.set(x, y, c, alpha)

    def blend(self, other: "Canvas", ox=0, oy=0, alpha=1.0, mask_alpha=True):
        for y in range(other.h):
            for x in range(other.w):
                p = other.get(x, y)
                if p[3] == 0:
                    continue
                a = alpha * (p[3] / 255.0)
                self.set(ox + x, oy + y, p, a)

    # -- shapes ------------------------------------------------------------------
    def rect(self, x0, y0, x1, y1, c, alpha=1.0):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for x in range(min(x0, x1), max(x0, x1) + 1):
                self.set(x, y, c, alpha)

    def frame(self, x0, y0, x1, y1, c, alpha=1.0):
        self.rect(x0, y0, x1, y0, c, alpha)
        self.rect(x0, y1, x1, y1, c, alpha)
        self.rect(x0, y0, x0, y1, c, alpha)
        self.rect(x1, y0, x1, y1, c, alpha)

    def line(self, x0, y0, x1, y1, c, alpha=1.0, thick=1):
        dx, dy = abs(x1 - x0), abs(y1 - y0)
        sx = 1 if x0 < x1 else -1
        sy = 1 if y0 < y1 else -1
        err = dx - dy
        while True:
            for t in range(thick):
                if dx > dy:
                    self.set(x0, y0 + t, c, alpha)
                else:
                    self.set(x0 + t, y0, c, alpha)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 > -dy:
                err -= dy
                x0 += sx
            if e2 < dx:
                err += dx
                y0 += sy

    def ellipse(self, cx, cy, rx, ry, c, alpha=1.0, filled=True):
        for y in range(int(cy - ry), int(cy + ry) + 1):
            for x in range(int(cx - rx), int(cx + rx) + 1):
                d = ((x - cx) / max(rx, 0.001)) ** 2 + ((y - cy) / max(ry, 0.001)) ** 2
                if filled and d <= 1.0:
                    self.set(x, y, c, alpha)
                elif not filled and 0.55 < d <= 1.15:
                    self.set(x, y, c, alpha)

    def poly(self, pts, c, alpha=1.0):
        if not pts:
            return
        ys = [p[1] for p in pts]
        for y in range(int(min(ys)), int(max(ys)) + 1):
            xs = []
            n = len(pts)
            for i in range(n):
                x0, y0 = pts[i]
                x1, y1 = pts[(i + 1) % n]
                if (y0 <= y < y1) or (y1 <= y < y0):
                    t = (y - y0) / (y1 - y0)
                    xs.append(x0 + t * (x1 - x0))
            xs.sort()
            for i in range(0, len(xs) - 1, 2):
                for x in range(int(math.floor(xs[i])), int(math.ceil(xs[i + 1])) + 1):
                    self.set(x, y, c, alpha)

    # -- texture helpers ---------------------------------------------------------
    def noise(self, c, density=0.25, alpha=1.0, mask=None, seed=None):
        rng = random.Random(seed if seed is not None else self.rng.random())
        for y in range(self.h):
            for x in range(self.w):
                if mask and not mask(x, y):
                    continue
                if rng.random() < density:
                    self.set(x, y, c, alpha)

    def value_noise(self, c_dark, c_light, scale=4.0, alpha=0.5, seed=7, mask=None):
        """Tileable multi-octave value noise - used for plaster, cloth and flesh."""
        rng = random.Random(seed)
        gw = max(2, int(self.w / scale) + 1)
        gh = max(2, int(self.h / scale) + 1)
        grid = [[rng.random() for _ in range(gw + 1)] for _ in range(gh + 1)]

        def sample(u, v):
            x0 = math.floor(u) % gw
            y0 = math.floor(v) % gh
            fx, fy = u - math.floor(u), v - math.floor(v)
            fx = fx * fx * (3 - 2 * fx)
            fy = fy * fy * (3 - 2 * fy)
            a = grid[y0][x0]
            b = grid[y0][x0 + 1]
            cc = grid[y0 + 1][x0]
            d = grid[y0 + 1][x0 + 1]
            return (a * (1 - fx) + b * fx) * (1 - fy) + (cc * (1 - fx) + d * fx) * fy

        for y in range(self.h):
            for x in range(self.w):
                if mask and not mask(x, y):
                    continue
                v = (sample(x / scale, y / scale) * 0.6
                     + sample(x / (scale / 2.2), y / (scale / 2.2)) * 0.4)
                self.set(x, y, mix(c_dark, c_light, min(1.0, max(0.0, v))), alpha)

    def wear(self, amount=0.16, seed=3, dark="void", light=None):
        """Erode the sprite: chips on the silhouette + speckled grime. Always last."""
        rng = random.Random(seed)
        light = light or lighter(dark, 0.25)
        edge = []
        for y in range(self.h):
            for x in range(self.w):
                if self.get(x, y)[3] == 0:
                    continue
                if any(self.get(x + dx, y + dy)[3] == 0 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    edge.append((x, y))
        for (x, y) in edge:
            if rng.random() < amount:
                self.set(x, y, dark, 0.55)
            elif rng.random() < amount * 0.5:
                self.set(x, y, light, 0.35)
        for _ in range(int(self.w * self.h * amount * 0.5)):
            x, y = rng.randrange(self.w), rng.randrange(self.h)
            if self.get(x, y)[3]:
                self.set(x, y, dark if rng.random() < 0.5 else light, 0.25)

    def outline(self, c="void", outside=True):
        """1px dark outline. If outside, grows the sprite by 1px (needed for icons)."""
        if outside:
            grown = Canvas(self.w, self.h, seed=11)
            grown.blend(self)
            for y in range(self.h):
                for x in range(self.w):
                    if self.get(x, y)[3] != 0:
                        continue
                    if any(self.get(x + dx, y + dy)[3] != 0 for dx, dy in
                           ((1, 0), (-1, 0), (0, 1), (0, -1))):
                        grown.set(x, y, c, 0.9)
            self.px = grown.px
        else:
            for y in range(self.h):
                for x in range(self.w):
                    if self.get(x, y)[3]:
                        continue
                    if any(self.get(x + dx, y + dy)[3] != 0 for dx, dy in
                           ((1, 0), (-1, 0), (0, 1), (0, -1))):
                        self.set(x, y, c, 0.85)

    def shade(self, dirx=1, diry=1, amount=0.25):
        """Directional shading pass: light top-left, shadow bottom-right."""
        for y in range(self.h):
            for x in range(self.w):
                p = self.get(x, y)
                if p[3] == 0:
                    continue
                t = (x * dirx + y * diry) / max(1, self.w + self.h)
                self.set(x, y, mix(p[:3], "void" if dirx + diry > 0 else "white", abs(t - 0.4) * amount))

    def glow(self, c="spirit", radius=1, alpha=0.5):
        """Cheap bloom baked into the sprite (used sparingly for supernatural items)."""
        out = Canvas(self.w, self.h, seed=5)
        out.blend(self)
        for y in range(self.h):
            for x in range(self.w):
                if self.get(x, y)[3] != 0:
                    continue
                near = 0
                for dy in range(-radius, radius + 1):
                    for dx in range(-radius, radius + 1):
                        if dx == dy == 0:
                            continue
                        if self.get(x + dx, y + dy)[3] != 0 and self.get(x + dx, y + dy)[3] > 40:
                            near += 1
                if near:
                    out.set(x, y, c, min(0.75, alpha * near / (radius * 4)))
        self.px = out.px

    # -- output ------------------------------------------------------------------
    def to_image(self) -> Image.Image:
        img = Image.new("RGBA", (self.w, self.h))
        img.putdata([tuple(p) for p in self.px])
        return img

    def save(self, path: str):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.to_image().save(path)


# --------------------------------------------------------------------------------------
# Small helpers shared by the generators
# --------------------------------------------------------------------------------------
def upscale(img: Image.Image, factor: int) -> Image.Image:
    return img.resize((img.width * factor, img.height * factor), Image.NEAREST)


def lathe(w, h, profile, c, shade_amt=0.35):
    """Symmetric 'turned on a lathe' silhouette - potions, altars, candles."""
    cv = Canvas(w, h, seed=profile)
    for y in range(h):
        t = y / max(1, h - 1)
        half = profile(t)
        cx = (w - 1) / 2.0
        for x in range(int(-half), int(half) + 1):
            px = int(round(cx + x))
            if 0 <= px < w:
                sh = 1.0 - abs(x) / max(0.5, half) * shade_amt - abs(t - 0.35) * 0.12
                cv.set(px, y, mix(darker(c, 0.55), lighter(c, 0.35), min(1.0, max(0.0, sh))))
    return cv
