#!/usr/bin/env python3
"""Asset and source verification for Pathways of the Beyond.

Checks the generated assets against the content index and the mod's art direction, and runs a
structural sanity pass over the hand-written Java (the sandbox has no JDK, so this is the
substitute for `javac`).

Run:  python3 tools/verify_assets.py
Exit code is non-zero when a hard check fails.
"""
from __future__ import annotations

import json
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/pathwaysofthebeyond"
DATA = ROOT / "src/main/resources/data/pathwaysofthebeyond"
JAVA = ROOT / "src/main/java/com/pathways/beyond"

sys.path.insert(0, str(ROOT / "tools"))
try:
    import content  # type: ignore
except Exception as exc:  # pragma: no cover
    print(f"!! could not import tools/content.py: {exc}")
    content = None

try:
    import pixelart  # type: ignore
except Exception:  # pragma: no cover
    pixelart = None

FAILURES: list[str] = []
WARNINGS: list[str] = []


def fail(message: str) -> None:
    FAILURES.append(message)


def warn(message: str) -> None:
    WARNINGS.append(message)


def pascal_case(name: str) -> str:
    return "".join(part.capitalize() for part in re.split(r"[_\-\s]+", name) if part)


def ids_of(attribute: str) -> list[str]:
    """Content index attributes are lists of tuples whose first element is the id."""
    if content is None:
        return []
    values = getattr(content, attribute, []) or []
    result = []
    for entry in values:
        if isinstance(entry, str):
            result.append(entry)
        elif isinstance(entry, dict):
            if "id" in entry:
                result.append(entry["id"])
        elif isinstance(entry, (tuple, list)) and entry:
            result.append(str(entry[0]))
    return result


# ---------------------------------------------------------------------------------------
# PNG helpers
# ---------------------------------------------------------------------------------------
def png_size(path: Path) -> tuple[int, int]:
    with path.open("rb") as handle:
        header = handle.read(24)
    if header[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a png")
    return struct.unpack(">II", header[16:24])


def png_colours(path: Path):
    try:
        from PIL import Image  # type: ignore
    except Exception:
        return None
    with Image.open(path) as image:
        image = image.convert("RGBA")
        colours = image.getcolors(maxcolors=1 << 24) or []
    return colours


# ---------------------------------------------------------------------------------------
# Checks
# ---------------------------------------------------------------------------------------
def check_textures() -> None:
    expectations = (("textures/item", 45), ("textures/block", 20), ("textures/entity", 11),
                    ("textures/particle", 12), ("textures/mob_effect", 10), ("textures/gui", 5))
    counts = {}
    for folder, expected in expectations:
        directory = ASSETS / folder
        if not directory.exists():
            fail(f"missing texture folder {folder}")
            continue
        files = sorted(directory.rglob("*.png"))
        counts[folder] = len(files)
        if len(files) < expected:
            fail(f"{folder}: only {len(files)} textures (expected >= {expected})")
        for path in files:
            try:
                width, height = png_size(path)
            except ValueError as exc:
                fail(f"{path.relative_to(ROOT)}: {exc}")
                continue
            if width < 8 or height < 8:
                fail(f"{path.relative_to(ROOT)}: suspiciously small ({width}x{height})")
            if max(width, height) > 256:
                warn(f"{path.relative_to(ROOT)}: larger than usual ({width}x{height})")
            colours = png_colours(path)
            if colours and len(colours) < 3 and folder.startswith(("textures/item", "textures/block")):
                fail(f"{path.relative_to(ROOT)}: flat colour image, looks like a placeholder")
    print("textures:", ", ".join(f"{k.split('/')[-1]}={v}" for k, v in counts.items()))


def check_sound_files() -> None:
    sounds_json = ASSETS / "sounds.json"
    if not sounds_json.exists():
        fail("sounds.json missing")
        return
    try:
        data = json.loads(sounds_json.read_text())
    except json.JSONDecodeError as exc:
        fail(f"sounds.json invalid: {exc}")
        return
    ogg_files = {path.stem for path in (ASSETS / "sounds").rglob("*.ogg")}
    print(f"sounds.json events: {len(data)}, ogg files: {len(ogg_files)}")

    # 1. every registered sound event must exist as an entry, or Minecraft cannot play it
    for sound_id in ids_of("SOUNDS"):
        if sound_id not in data:
            fail(f"sounds.json has no entry for registered sound '{sound_id}'")
    # 2. every entry must point at a real file
    for entry, definition in data.items():
        for sound in definition.get("sounds", []):
            name = sound if isinstance(sound, str) else sound.get("name", "")
            short = name.split(":")[-1]
            if short not in ogg_files:
                fail(f"sounds.json entry '{entry}' points at missing file {name}.ogg")
    # 3. no orphan files
    for orphan in sorted(ogg_files - set(data)):
        warn(f"orphan sound file without a sounds.json entry: {orphan}.ogg")
    # 4. files must be audible: a 1 KB ogg is almost always a broken render
    for path in sorted((ASSETS / "sounds").rglob("*.ogg")):
        size = path.stat().st_size
        if size < 1024:
            fail(f"{path.relative_to(ROOT)}: only {size} bytes (probably silent)")


def check_lang() -> None:
    keys = {}
    for locale in ("en_us", "vi_vn"):
        path = ASSETS / f"lang/{locale}.json"
        if not path.exists():
            fail(f"lang/{locale}.json missing")
            continue
        try:
            data = json.loads(path.read_text())
        except json.JSONDecodeError as exc:
            fail(f"lang/{locale}.json invalid: {exc}")
            continue
        keys[locale] = set(data)
        print(f"lang/{locale}.json: {len(data)} keys")
    if len(keys) == 2 and keys["en_us"] != keys["vi_vn"]:
        warn(f"lang key mismatch: {len(keys['en_us'] - keys['vi_vn'])} en-only, "
             f"{len(keys['vi_vn'] - keys['en_us'])} vi-only")
    if "en_us" in keys:
        for sound_id in ids_of("SOUNDS"):
            subtitle = f"sound.pathwaysofthebeyond.{sound_id}"
            if subtitle not in keys["en_us"]:
                warn(f"missing subtitle key {subtitle}")


def check_java_references() -> None:
    """Every ModX.Name referenced in Java must exist in the content index."""
    if content is None:
        return
    groups = {
        "ModItems": ids_of("POTIONS") + ids_of("INGREDIENTS") + ids_of("ARTIFACTS") + ids_of("UTILITY_ITEMS"),
        "ModBlocks": ids_of("BLOCKS"),
        "ModEntities": ids_of("REGULAR_ENTITIES") + ids_of("SUMMONED_ENTITIES") + ids_of("BOSSES"),
        "ModSounds": ids_of("SOUNDS"),
        "ModParticles": ids_of("PARTICLES"),
        "ModEffects": ids_of("EFFECTS"),
    }
    source = "\n".join(path.read_text() for path in JAVA.rglob("*.java"))
    unknown_total = 0
    for holder, ids in groups.items():
        if not ids:
            continue
        known = {pascal_case(i) for i in ids}
        used = set(re.findall(rf"{holder}\.([A-Z][A-Za-z0-9_]*)", source))
        # the DeferredRegister fields themselves are not content ids
        used -= {"ITEMS", "BLOCKS", "ENTITIES", "SOUNDS", "PARTICLES", "EFFECTS",
                 "BLOCK_ITEMS", "TABS", "CHUNK_GENERATORS", "COMPONENTS", "ATTACHMENTS"}
        unknown = sorted(used - known)
        for name in unknown:
            unknown_total += 1
            fail(f"{holder}.{name} referenced in Java but absent from content.py")
    if not unknown_total:
        print("java registry references: all resolve")


def check_java_structure() -> None:
    """Balance check for every Java source (the sandbox has no compiler)."""
    problems = 0
    for path in sorted(JAVA.rglob("*.java")):
        source = path.read_text()
        depth = {"{": 0, "(": 0, "[": 0}
        pairs = {"}": "{", ")": "(", "]": "["}
        index = 0
        state = "code"
        while index < len(source):
            char = source[index]
            nxt = source[index:index + 3]
            if state == "code":
                if nxt == '"""':
                    state = "textblock"
                    index += 3
                    continue
                if char == '"':
                    state = "string"
                elif char == "'":
                    state = "char"
                elif source[index:index + 2] == "//":
                    state = "line-comment"
                    index += 2
                    continue
                elif source[index:index + 2] == "/*":
                    state = "block-comment"
                    index += 2
                    continue
                elif char in depth:
                    depth[char] += 1
                elif char in pairs:
                    depth[pairs[char]] -= 1
                    if depth[pairs[char]] < 0:
                        fail(f"{path.relative_to(ROOT)}: unmatched '{char}' near char {index}")
                        problems += 1
                        depth[pairs[char]] = 0
            elif state == "string":
                if char == "\\":
                    index += 2
                    continue
                if char == '"':
                    state = "code"
            elif state == "textblock":
                if nxt == '"""':
                    state = "code"
                    index += 3
                    continue
            elif state == "char":
                if char == "\\":
                    index += 2
                    continue
                if char == "'":
                    state = "code"
            elif state == "line-comment":
                if char == "\n":
                    state = "code"
            elif state == "block-comment":
                if source[index:index + 2] == "*/":
                    state = "code"
                    index += 2
                    continue
            index += 1
        for opener, count in depth.items():
            if count != 0:
                fail(f"{path.relative_to(ROOT)}: unbalanced '{opener}' x{count}")
                problems += 1
    if not problems:
        print("java structure: all files balanced")


def check_models_and_resources() -> None:
    for folder in ("models", "blockstates"):
        files = sorted((ASSETS / folder).rglob("*.json"))
        for path in files:
            try:
                json.loads(path.read_text())
            except json.JSONDecodeError as exc:
                fail(f"{path.relative_to(ROOT)}: invalid JSON ({exc})")
        print(f"{folder}: {len(files)} json files")
    if DATA.exists():
        for path in sorted(DATA.rglob("*.json")):
            try:
                json.loads(path.read_text())
            except json.JSONDecodeError as exc:
                fail(f"{path.relative_to(ROOT)}: invalid JSON ({exc})")
        print(f"data: {len(list(DATA.rglob('*.json')))} json files")
    else:
        warn("no data/ directory yet: dimension, biome and structure jsons missing")


def check_palette() -> None:
    """Every texture must stay inside the locked palette (allows a few anti-alias shades)."""
    palette_source = getattr(pixelart, "PALETTE", None) if pixelart is not None else None
    if not palette_source:
        warn("locked palette not found: palette check skipped")
        return
    def normalise(colour):
        if isinstance(colour, str):
            text = colour.lstrip("#")
            if len(text) != 6:
                return None
            try:
                return (int(text[0:2], 16), int(text[2:4], 16), int(text[4:6], 16))
            except ValueError:
                return None
        if isinstance(colour, int):
            return ((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF)
        if isinstance(colour, (tuple, list)) and len(colour) >= 3:
            return (int(colour[0]) & 0xFF, int(colour[1]) & 0xFF, int(colour[2]) & 0xFF)
        return None
    palette_values = palette_source.values() if isinstance(palette_source, dict) else palette_source
    allowed = {normalise(colour) for colour in palette_values}
    allowed.discard(None)
    if not allowed:
        warn("palette empty: palette check skipped")
        return
    # Shading is produced by interpolating between palette entries, so textures legitimately
    # contain in-between values. What must never happen is a foreign hue, so we measure the
    # furthest any pixel sits from the palette and flag only genuine drift.
    tolerance = 60.0
    offenders = 0
    worst = 0.0
    for path in sorted((ASSETS / "textures").rglob("*.png")):
        colours = png_colours(path)
        if not colours:
            continue
        furthest = 0.0
        for entry in colours:
            rgb = entry[1][:3]
            if entry[1][3] <= 8:
                continue
            distance = min(sum((int(a) - int(b)) ** 2 for a, b in zip(rgb, allowed_colour)) ** 0.5
                           for allowed_colour in allowed)
            furthest = max(furthest, distance)
        worst = max(worst, furthest)
        if furthest > tolerance:
            warn(f"{path.relative_to(ROOT)}: colour {furthest:.0f} away from the palette "
                 f"(tolerance {tolerance:.0f})")
            offenders += 1
    print(f"palette: {len(allowed)} locked colours, worst drift {worst:.0f}, "
          f"{offenders} texture(s) outside tolerance")


def main() -> int:
    if not ASSETS.exists():
        print(f"!! asset root missing: {ASSETS}")
        return 2
    check_textures()
    check_sound_files()
    check_lang()
    check_models_and_resources()
    check_java_references()
    check_java_structure()
    check_palette()

    print()
    for message in WARNINGS:
        print(f"warn: {message}")
    for message in FAILURES:
        print(f"FAIL: {message}")
    print()
    if FAILURES:
        print(f"{len(FAILURES)} failure(s), {len(WARNINGS)} warning(s)")
        return 1
    print(f"all checks passed ({len(WARNINGS)} warning(s))")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
