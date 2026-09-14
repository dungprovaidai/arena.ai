"""
Block / blockstate / item model generator.

Block models are hand-authored as JSON elements. Machine textures are 32x32 atlases of
8px cells, so every face UV is a cell times 4 (Minecraft UV space is 0-16 regardless of
the texture resolution).

Run:  python3 tools/gen_models.py
"""
from __future__ import annotations

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from content import ARTIFACTS, INGREDIENTS, MOD_ID, POTIONS, UTILITY_ITEMS  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", MOD_ID)
NS = MOD_ID

# ---------------------------------------------------------------------------------------
# helpers for authoring element JSON
# ---------------------------------------------------------------------------------------
def cell_uv(name: str, cells=(1, 1), inset=0.0, corner=0, mirror=False):
    """UV rect for an 8px cell region in a 32x32 atlas, in Minecraft UV space (0-16)."""
    from gen_blocks import ATLAS
    col, row = ATLAS[name]
    u0, v0 = col * 4, row * 4
    u1, v1 = u0 + 4 * cells[0], v0 + 4 * cells[1]
    if mirror:
        u0, u1 = u1, u0
    if inset:
        u0 += inset
        v0 += inset
        u1 -= inset
        v1 -= inset
    if corner:  # rotate which corner of the cell is used (0..3)
        pass
    return [u0, v0, u1, v1]


def cube(frm, to, faces, shade=True, rot=None):
    el = {"from": list(frm), "to": list(to), "faces": {}}
    if rot:
        el["rotation"] = rot
    if not shade:
        el["shade"] = False
    for d, uv in faces.items():
        face = {"uv": uv if uv else [0, 0, 16, 16], "texture": "#all"}
        el["faces"][d] = face
    return el


def cuboid(frm, to, tex="#all", uv=None, faces=("north", "south", "east", "west", "up", "down"),
           shade=True, rot=None):
    el = {"from": list(frm), "to": list(to), "faces": {}}
    if rot:
        el["rotation"] = rot
    if not shade:
        el["shade"] = False
    for d in faces:
        el["faces"][d] = {"uv": list(uv) if uv else [0, 0, 16, 16], "texture": tex}
    return el


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def tex_all(name):
    return f"{NS}:block/{name}"


# ---------------------------------------------------------------------------------------
# machine block models
# ---------------------------------------------------------------------------------------
def model_ritual_altar():
    t = {"all": tex_all("ritual_altar"), "particle": tex_all("ritual_altar")}
    S, D, L, B, C, W, G, BL, V = ("stone", "stone_dark", "stone_light", "carved",
                                  "brass", "brass_worn", "gem_socket", "blood", "void")
    els = [
        # plinth (2 block-high stepped base)
        cuboid([0, 0, 0], [16, 4, 16], uv=cell_uv(S)),
        cuboid([1, 4, 1], [15, 7, 15], uv=cell_uv(D)),
        # brass band around the plinth
        cuboid([0, 4, 0], [16, 5, 16], uv=cell_uv(C)),
        # four corner pillars with carved caps
        *[cuboid([x, 7, z], [x + 3, 15, z + 3], uv=cell_uv(W))
          for x, z in ((1, 1), (12, 1), (1, 12), (12, 12))],
        # altar slab
        cuboid([0, 15, 0], [16, 18, 16], uv=cell_uv(L)),
        cuboid([1, 18, 1], [15, 19, 15], uv=cell_uv(B)),
        # gem socket + blood channel on the slab top
        cuboid([5, 19, 5], [11, 20, 11], uv=cell_uv(G), tex="#gem"),
        cuboid([6, 20, 6], [10, 20, 10], uv=cell_uv("blood"), tex="#blood",
               faces=("up",)),
        # blood channel running off the slab front
        cuboid([7, 18, 15], [9, 19, 16], uv=cell_uv("blood"), tex="#blood"),
        # sealed iron bands across the pillars
        cuboid([0, 10, 0], [16, 11, 16], uv=cell_uv(C), tex="#brass"),
        cuboid([0, 12, 0], [16, 13, 16], uv=cell_uv(C), tex="#brass"),
    ]
    return {"parent": "minecraft:block/block", "render_type": "minecraft:solid",
            "textures": dict(t, gem=tex_all("ritual_altar"), brass=tex_all("ritual_altar"),
                             blood=tex_all("ritual_altar")),
            "elements": els}


def model_ritual_pedestal():
    S, D, L, B, C, G = "stone", "stone_dark", "stone_light", "carved", "brass", "gem_socket"
    els = [
        cuboid([4, 0, 4], [12, 2, 12], uv=cell_uv(S)),
        cuboid([5, 2, 5], [11, 3, 11], uv=cell_uv(D)),
        cuboid([6, 3, 6], [10, 12, 10], uv=cell_uv(L)),
        cuboid([5, 12, 5], [11, 14, 11], uv=cell_uv(B)),
        cuboid([6, 14, 6], [10, 16, 10], uv=cell_uv(G)),
        cuboid([5, 6, 5], [11, 7, 11], uv=cell_uv(C)),
    ]
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"all": tex_all("ritual_pedestal"), "particle": tex_all("ritual_pedestal")},
            "elements": els}


def model_ritual_candle():
    """Wax column, brass holder, and flame quads that only appear when lit."""
    els = [
        cuboid([5, 0, 5], [11, 2, 11], uv=cell_uv("brass")),
        cuboid([6, 2, 6], [10, 3, 10], uv=cell_uv("brass_dark")),
        cuboid([6, 3, 6], [10, 12, 10], uv=cell_uv("candle_wax")),
        cuboid([6, 12, 6], [10, 13, 10], uv=cell_uv("candle_wax")),
    ]
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"all": tex_all("ritual_candle"), "particle": tex_all("ritual_candle")},
            "elements": els}


def model_ritual_candle_lit():
    return {"parent": f"{NS}:block/ritual_candle"}


def model_chalk_circle():
    """Flat decal: four quads tiling one 32x32 chalk texture at y = 0.0625."""
    up = [0, 0, 16, 16]
    el = {"from": [0, 0.5, 0], "to": [16, 0.5, 16], "shade": False, "faces": {
        "up": {"uv": up, "texture": "#all"},
        "down": {"uv": up, "texture": "#all"},
    }}
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "ambientocclusion": False,
            "textures": {"all": tex_all("chalk_circle"), "particle": tex_all("chalk_circle")},
            "elements": [el]}


def model_occult_table():
    W, L, P, B, C = "wood", "leather", "parchment", "brass", "carved"
    els = [
        cuboid([0, 13, 0], [16, 16, 16], uv=cell_uv(W)),
        cuboid([0, 16, 0], [16, 17, 16], uv=cell_uv(L)),
        *[cuboid([x, 0, z], [x + 3, 13, z + 3], uv=cell_uv(W))
          for x, z in ((1, 1), (12, 1), (1, 12), (12, 12))],
        # brass drawer front + leather inlay
        cuboid([2, 8, 15], [14, 12, 16], uv=cell_uv(B)),
        cuboid([2, 9, 15], [6, 11, 16], uv=cell_uv(L)),
        # open codex and instruments on the tabletop
        cuboid([3, 17, 3], [7, 18, 11], uv=cell_uv(P)),
        cuboid([9, 17, 3], [13, 18, 11], uv=cell_uv(P)),
        cuboid([11, 17, 12], [14, 19, 14], uv=cell_uv(C)),
    ]
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"all": tex_all("occult_table"), "particle": tex_all("occult_table")},
            "elements": els}


def model_blood_basin():
    S, D, B, BL, C, L = "stone", "stone_dark", "carved", "blood", "brass", "stone_light"
    els = [
        cuboid([0, 0, 0], [16, 2, 16], uv=cell_uv(D)),
        # basin walls
        cuboid([0, 2, 0], [16, 9, 2], uv=cell_uv(S)),
        cuboid([0, 2, 14], [16, 9, 16], uv=cell_uv(S)),
        cuboid([0, 2, 2], [2, 9, 14], uv=cell_uv(S)),
        cuboid([14, 2, 2], [16, 9, 14], uv=cell_uv(S)),
        cuboid([0, 9, 0], [16, 10, 16], uv=cell_uv(L)),
        # blood (rendered always; the block state decides the level)
        cuboid([2, 6, 2], [14, 7, 14], uv=cell_uv(BL), tex="#blood", faces=("up",)),
        # brass spouts at the corners
        *[cuboid([x, 10, z], [x + 2, 12, z + 2], uv=cell_uv(C))
          for x, z in ((2, 2), (12, 2), (2, 12), (12, 12))],
        cuboid([2, 10, 2], [14, 11, 14], uv=cell_uv(B), tex="#carve"),
    ]
    return {"parent": "minecraft:block/block", "render_type": "minecraft:solid",
            "textures": {"all": tex_all("blood_basin"), "blood": tex_all("blood_basin"),
                         "carve": tex_all("blood_basin")},
            "elements": els}


def model_spirit_lantern():
    B, W, G, V = "brass", "brass_worn", "gem_socket", "void"
    els = [
        cuboid([6, 0, 6], [10, 2, 10], uv=cell_uv(B)),
        cuboid([5, 2, 5], [11, 3, 11], uv=cell_uv(W)),
        cuboid([5, 3, 5], [6, 11, 6], uv=cell_uv(W)),
        cuboid([10, 3, 5], [11, 11, 6], uv=cell_uv(W)),
        cuboid([5, 3, 10], [6, 11, 11], uv=cell_uv(W)),
        cuboid([10, 3, 10], [11, 11, 11], uv=cell_uv(W)),
        cuboid([4, 11, 4], [12, 13, 12], uv=cell_uv(B)),
        cuboid([6, 13, 6], [10, 15, 10], uv=cell_uv(W)),
        cuboid([7, 15, 7], [9, 16, 9], uv=cell_uv(B)),
        # the light inside: a gem cell that reads as a cold supernatural flame
        cuboid([6, 4, 6], [10, 10, 10], uv=cell_uv(G), tex="#gem", shade=False),
    ]
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"all": tex_all("spirit_lantern"), "gem": tex_all("spirit_lantern"),
                         "particle": tex_all("spirit_lantern")},
            "elements": els}


def model_occult_archive():
    W, D, L, P, B, C = "wood", "wood_dark", "leather", "parchment", "brass", "carved"
    els = [
        cuboid([0, 0, 0], [16, 3, 14], uv=cell_uv(D)),
        cuboid([0, 3, 0], [16, 16, 13], uv=cell_uv(W)),
        cuboid([1, 4, 12], [15, 15, 14], uv=cell_uv(L)),
        # brass clasps and a parchment label
        cuboid([2, 6, 12], [5, 9, 14], uv=cell_uv(B)),
        cuboid([11, 6, 12], [14, 9, 14], uv=cell_uv(B)),
        cuboid([6, 10, 12], [10, 14, 14], uv=cell_uv(P)),
        # carved sigil band across the top
        cuboid([0, 16, 0], [16, 18, 13], uv=cell_uv(C)),
        cuboid([0, 18, 0], [16, 19, 14], uv=cell_uv(D)),
        # small feet
        *[cuboid([x, 0, z], [x + 2, 3, z + 2], uv=cell_uv(D))
          for x, z in ((1, 1), (13, 1), (1, 11), (13, 11))],
    ]
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"all": tex_all("occult_archive"), "particle": tex_all("occult_archive")},
            "elements": els}


def model_memory_shard_block():
    """Void stone with a memory shard set into one face - use `facing` to orient it."""
    return {"parent": f"{NS}:block/void_stone_shard"}


# ---------------------------------------------------------------------------------------
# blockstates
# ---------------------------------------------------------------------------------------
def simple_state(model):
    return {"variants": {"": {"model": f"{NS}:block/{model}"}}}


def facing_state(model, extra_variants=None):
    variants = {}
    for i, (y, face) in enumerate([(0, "south"), (90, "west"), (180, "north"), (270, "east")]):
        variants[f"facing={face}"] = {"model": f"{NS}:block/{model}", "y": y}
    if extra_variants:
        variants.update(extra_variants)
    return {"variants": variants}


def axis_state(model):
    return {"variants": {
        "axis=y": {"model": f"{NS}:block/{model}"},
        "axis=x": {"model": f"{NS}:block/{model}", "x": 90, "y": 90},
        "axis=z": {"model": f"{NS}:block/{model}", "x": 90},
    }}


def slab_state(model, double_model):
    return {"variants": {
        "type=bottom": {"model": f"{NS}:block/{model}"},
        "type=top": {"model": f"{NS}:block/{model}_top"},
        "type=double": {"model": f"{NS}:block/{double_model}"},
    }}


def stairs_state(model):
    out = {}
    for y, face in [(0, "south"), (90, "west"), (180, "north"), (270, "east")]:
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                # straight stair geometry; inner/outer reuse the same model with rotation
                variant = f"facing={face},half={half},shape={shape}"
                entry = {"model": f"{NS}:block/{model}", "y": y}
                if half == "top":
                    entry.update({"x": 180, "uvlock": True})
                out[variant] = entry
    return {"variants": out}


def wall_state(model):
    out = {}
    for side in ("up", "none", "low", "tall"):
        for other in ("none", "low", "tall"):
            out[f"up={side == 'up'},north={side if side != 'up' else other},east={other},south={other},west={other}"] = {
                "model": f"{NS}:block/{model}_post"}
    # a full wall variant matrix is verbose; the multipart form below is what we actually use
    return {"multipart": [
        {"when": {"up": "true"}, "apply": {"model": f"{NS}:block/{model}_post"}},
        {"when": {"north": "low"}, "apply": {"model": f"{NS}:block/{model}_side"}},
        {"when": {"north": "tall"}, "apply": {"model": f"{NS}:block/{model}_side_tall"}},
        {"when": {"east": "low"}, "apply": {"model": f"{NS}:block/{model}_side", "y": 90}},
        {"when": {"east": "tall"}, "apply": {"model": f"{NS}:block/{model}_side_tall", "y": 90}},
        {"when": {"south": "low"}, "apply": {"model": f"{NS}:block/{model}_side", "y": 180}},
        {"when": {"south": "tall"}, "apply": {"model": f"{NS}:block/{model}_side_tall", "y": 180}},
        {"when": {"west": "low"}, "apply": {"model": f"{NS}:block/{model}_side", "y": 270}},
        {"when": {"west": "tall"}, "apply": {"model": f"{NS}:block/{model}_side_tall", "y": 270}},
    ]}


def candle_state():
    return {"variants": {
        "lit=false": {"model": f"{NS}:block/ritual_candle"},
        "lit=true": {"model": f"{NS}:block/ritual_candle_lit"},
    }}


def basin_state():
    return {"variants": {
        f"level={i}": {"model": f"{NS}:block/blood_basin"} for i in range(4)
    }}


# ---------------------------------------------------------------------------------------
# vanilla-family models we need to author by hand (stairs/slab/wall/cross/cutout)
# ---------------------------------------------------------------------------------------
def stairs_models(name, all_tex):
    """Stairs geometry approximating vanilla shape (4 boxes on the lower half)."""
    def box(frm, to, faces=("north", "south", "east", "west", "up", "down")):
        return cuboid(frm, to, uv=[0, 0, 16, 16], faces=faces)
    els = [
        box([0, 0, 0], [16, 8, 16]),
        box([8, 8, 0], [16, 16, 16]),
    ]
    base = {"parent": "minecraft:block/block", "render_type": "minecraft:solid",
            "textures": {"all": all_tex, "particle": all_tex}, "elements": els}
    inner = {"parent": "minecraft:block/block", "render_type": "minecraft:solid",
             "textures": {"all": all_tex, "particle": all_tex},
             "elements": [box([0, 0, 0], [16, 8, 16]), box([0, 8, 0], [16, 16, 8])]}
    outer = {"parent": "minecraft:block/block", "render_type": "minecraft:solid",
             "textures": {"all": all_tex, "particle": all_tex},
             "elements": [box([0, 0, 0], [16, 8, 16]), box([8, 8, 8], [16, 16, 16])]}
    return base, inner, outer


def slab_models(name, all_tex):
    box = cuboid([0, 0, 0], [16, 8, 16])
    bottom = {"parent": "minecraft:block/block", "render_type": "minecraft:solid",
              "textures": {"all": all_tex, "particle": all_tex}, "elements": [box]}
    top = {"parent": "minecraft:block/block", "render_type": "minecraft:solid",
           "textures": {"all": all_tex, "particle": all_tex},
           "elements": [cuboid([0, 8, 0], [16, 16, 16])]}
    return bottom, top


def wall_models(name, all_tex):
    post = {"parent": "minecraft:block/block", "render_type": "minecraft:solid",
            "textures": {"all": all_tex, "particle": all_tex},
            "elements": [cuboid([4, 0, 4], [12, 16, 12])]}
    side = {"parent": "minecraft:block/block", "render_type": "minecraft:solid",
            "textures": {"all": all_tex, "particle": all_tex},
            "elements": [cuboid([5, 0, 0], [11, 14, 8])]}
    side_tall = {"parent": "minecraft:block/block", "render_type": "minecraft:solid",
                 "textures": {"all": all_tex, "particle": all_tex},
                 "elements": [cuboid([5, 0, 0], [11, 16, 8])]}
    return post, side, side_tall


def cross_model(name, render_type="minecraft:cutout"):
    """Plants: two crossed planes."""
    a = {"from": [0.8, 0, 8], "to": [15.2, 16, 8], "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
         "shade": False, "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#all"},
                                   "south": {"uv": [0, 0, 16, 16], "texture": "#all"}}}
    b = {"from": [8, 0, 0.8], "to": [8, 16, 15.2], "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
         "shade": False, "faces": {"east": {"uv": [0, 0, 16, 16], "texture": "#all"},
                                   "west": {"uv": [0, 0, 16, 16], "texture": "#all"}}}
    return {"parent": "minecraft:block/block", "render_type": render_type,
            "ambientocclusion": False,
            "textures": {"all": tex_all(name), "particle": tex_all(name)},
            "elements": [a, b]}


def cube_all_model(name, render_type="minecraft:solid", tinted=False):
    return {"parent": "minecraft:block/cube_all", "render_type": render_type,
            "textures": {"all": tex_all(name)}}


def cube_column_model(side, top=None, bottom=None):
    top = top or side
    bottom = bottom or side
    return {"parent": "minecraft:block/cube_column",
            "textures": {"end": tex_all(top), "side": tex_all(side)}}


# ---------------------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------------------
def main():
    block_dir = os.path.join(ASSETS, "models", "block")
    state_dir = os.path.join(ASSETS, "blockstates")
    item_dir = os.path.join(ASSETS, "models", "item")

    blocks = {}
    states = {}

    # --- building blocks ---
    blocks["gothic_bricks"] = cube_all_model("gothic_bricks")
    blocks["desecrated_stone"] = cube_all_model("desecrated_stone")
    blocks["weathered_plaster"] = cube_all_model("weathered_plaster")
    blocks["dark_planks"] = cube_all_model("dark_planks")
    blocks["slate_tiles"] = cube_all_model("slate_tiles")
    blocks["void_stone"] = cube_all_model("void_stone")
    blocks["iron_grate"] = cube_all_model("iron_grate", "minecraft:cutout")
    blocks["veil_glass"] = cube_all_model("veil_glass", "minecraft:translucent")
    blocks["spirit_flower"] = cross_model("spirit_flower")
    blocks["moonlit_fungus"] = cross_model("moonlit_fungus")

    for name, model in blocks.items():
        states[name] = simple_state(name)
        write(os.path.join(block_dir, f"{name}.json"), model)

    # stairs / slabs / walls for the three building families
    families = {
        "gothic_brick": ("gothic_bricks", "slate"),
        "desecrated_stone": ("desecrated_stone", "ash"),
        "slate_tile": ("slate_tiles", "slate"),
        "dark_plank": ("dark_planks", "wood"),
    }
    for fam, (tex, _tone) in families.items():
        all_tex = tex_all(tex)
        base, inner, outer = stairs_models(fam, all_tex)
        write(os.path.join(block_dir, f"{fam}_stairs.json"), base)
        write(os.path.join(block_dir, f"{fam}_stairs_inner.json"), inner)
        write(os.path.join(block_dir, f"{fam}_stairs_outer.json"), outer)
        states[f"{fam}_stairs"] = stairs_state(f"{fam}_stairs")
        bottom, top = slab_models(fam, all_tex)
        write(os.path.join(block_dir, f"{fam}_slab.json"), bottom)
        write(os.path.join(block_dir, f"{fam}_slab_top.json"), top)
        states[f"{fam}_slab"] = slab_state(f"{fam}_slab", f"{fam}_slab_double")
        write(os.path.join(block_dir, f"{fam}_slab_double.json"), cube_all_model(tex))
        if fam == "gothic_brick":
            post, side, side_tall = wall_models(fam, all_tex)
            write(os.path.join(block_dir, f"{fam}_wall_post.json"), post)
            write(os.path.join(block_dir, f"{fam}_wall_side.json"), side)
            write(os.path.join(block_dir, f"{fam}_wall_side_tall.json"), side_tall)
            states[f"{fam}_wall"] = wall_state(f"{fam}_wall")

    # --- machines ---
    machines = {
        "ritual_altar": (model_ritual_altar(), facing_state("ritual_altar")),
        "ritual_pedestal": (model_ritual_pedestal(), simple_state("ritual_pedestal")),
        "ritual_candle": (model_ritual_candle(), candle_state()),
        "chalk_circle": (model_chalk_circle(), simple_state("chalk_circle")),
        "occult_table": (model_occult_table(), facing_state("occult_table")),
        "blood_basin": (model_blood_basin(), basin_state()),
        "spirit_lantern": (model_spirit_lantern(),
                           {"variants": {"lit=false": {"model": f"{NS}:block/spirit_lantern"},
                                         "lit=true": {"model": f"{NS}:block/spirit_lantern"}}}),
        "occult_archive": (model_occult_archive(), facing_state("occult_archive")),
        "memory_shard_block": ({"parent": "minecraft:block/block", "render_type": "minecraft:solid",
                                "textures": {"all": tex_all("void_stone"),
                                             "shard": tex_all("memory_shard_block_face")},
                                "elements": [cuboid([0, 0, 0], [16, 16, 16]),
                                             {"from": [3, 3, 15.9], "to": [13, 13, 16],
                                              "shade": False, "faces": {
                                                  "south": {"uv": [3, 3, 13, 13], "texture": "#shard"},
                                                  "up": {"uv": [0, 0, 0, 0], "texture": "#all",
                                                         "cullface": "up"}}}]},
                               facing_state("memory_shard_block")),
    }
    for name, (model, state) in machines.items():
        write(os.path.join(block_dir, f"{name}.json"), model)
        states[name] = state
        if name == "ritual_candle":
            write(os.path.join(block_dir, "ritual_candle_lit.json"), model_ritual_candle_lit())

    for name, state in states.items():
        write(os.path.join(state_dir, f"{name}.json"), state)

    # --- block items ---
    # decorative building blocks point at their block model (standard Minecraft look);
    # machines get hand-drawn 16x16 icons so they read at inventory scale
    icon_items = {"ritual_altar", "ritual_pedestal", "ritual_candle", "chalk_circle",
                  "occult_table", "blood_basin", "spirit_lantern", "occult_archive",
                  "memory_shard_block", "spirit_flower", "moonlit_fungus"}
    for name in states:
        if name.endswith("_wall_side") or name.endswith("_wall_post") or name.endswith("_wall_side_tall"):
            continue
        if name in icon_items:
            write(os.path.join(item_dir, f"{name}.json"),
                  {"parent": "minecraft:item/generated",
                   "textures": {"layer0": f"{NS}:item/block_{name}"}})
        else:
            write(os.path.join(item_dir, f"{name}.json"),
                  {"parent": f"{NS}:block/{name}"})

    # --- items ---
    item_ids = ([p[0] for p in POTIONS]
                + [i[0] for i in INGREDIENTS]
                + [a[0] for a in ARTIFACTS]
                + [u[0] for u in UTILITY_ITEMS]
                + ["corrupted_heart"])
    for iid in item_ids:
        write(os.path.join(item_dir, f"{iid}.json"),
              {"parent": "minecraft:item/generated",
               "textures": {"layer0": f"{NS}:item/{iid}"}})

    print(f"[models] {len(states)} blockstates, {len(item_ids)} item models -> {ASSETS}")


if __name__ == "__main__":
    main()
