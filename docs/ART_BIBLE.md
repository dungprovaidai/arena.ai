# Pathways of the Beyond - Art Bible

Every asset in this mod is generated from a locked palette by `tools/pixelart.py` and assembled
by the `tools/gen_*.py` scripts, so the look stays consistent across hundreds of textures. This
document is the contract those scripts implement. If a change to an asset would break a rule
here, the rule wins.

## 1. The locked palette

43 colours, defined once in `tools/pixelart.py::PALETTE`. Nothing else may appear in any
texture, with the sole exceptions of fully transparent pixels and the 1px outline rule below.

| Family | Colours | Use |
| --- | --- | --- |
| Void | `void #07060A`, `pitch #0E0C10` | deepest shadow, outlines, the Beyond |
| Greys | `charcoal #191519`, `ash #262128`, `slate #35303A`, `stone #4A4550`, `grey #6B6570`, `smoke #928C98` | stone, iron, cloth, fog |
| Bone | `bone #C9C3CE`, `pale #E8E3EC`, `white #F6F3F8` | chalk, bone, teeth, letters |
| Leather | `umber #2A1D13`, `brown #3E2A1B`, `leather #5C3F26`, `tan #7E5A34`, `khaki #A07B4C` | wood, brass patina, earth |
| Parchment | `parch_hi #E4D3A8`, `parch #CDB583`, `parch_lo #A89059` | books, codex, scrolls |
| Brass | `brass_lo #6B5426`, `brass #9C7C3A`, `brass_hi #D4B265`, `gold #EBD08A` | instruments, keys, seals, authority |
| Moss | `moss_lo #1E2A21`, `moss #2F4232`, `moss_hi #4C6749`, `sage #6F8A63` | overgrowth, desecrated ground |
| Blood | `blood_lo #2B070D`, `blood #5A0F17`, `crimson #8C1A24`, `rust #B03A34`, `rose #D0706A` | corruption, ritual blood, warning |
| Cold | `abyss #0A1424`, `navy #13273F`, `blue_lo #21456B`, `blue #3A6C96`, `cyan_mut #63A2B8` | moonlight, deep water, gaze |
| Spirit | `spirit #A8DCE2`, `spirit_hi #DFF6F7` | Spirit World, soul threads, wards |
| Violet | `violet_lo #2A1B3C`, `violet #4B3068`, `orchid #7B5AA0`, `veil #B49BD0` | the pathways themselves, occult light |

There is no neon, no pure saturated red/blue/green, and no colour outside this list. Mood comes
from *value structure* (how dark the darkest part is) rather than from saturation.

## 2. Pixel art rules

* **Scale.** Item icons 16x16. Block textures 16x16, with the machine-style blocks painted in a
  32x32 atlas of 8px cells. Entity textures use vanilla layouts (see §4). GUI panels up to 256px.
* **Readability first.** Every silhouette must be identifiable in one colour pass: a shape test
  is run by `tools/pixelart.py` before a texture is accepted.
* **Asymmetry.** Ritual objects and creatures are deliberately asymmetric: a candle is slightly
  bent, a shoulder sits higher, one sleeve is longer. Symmetry in this mod reads as "furniture".
* **Wear, not damage.** Blocks get tileable wear (chipped corners, grain, damp staining at the
  bottom edge) that repeats seamlessly. No random pockmarks, no noise for its own sake.
* **Outline.** Items carry a 1px outline in `void` so they read against any inventory slot.
* **Light direction.** Top-left key light; three-tone shading per material family
  (base / +1 step / -2 steps). Isometric block icons use face shades 1.18 / 0.88 / 0.62.
* **Rarity marks.** Potion bottles use three silhouettes - flask (common/occult), tall (rare),
  orb (epic/legendary) - plus a pip at (13,14); legendary items get a brass seal row.

## 3. Blocks

* Ritual furniture (altar, pedestal, candle, chalk circle, table, basin, lantern, archive) is
  sculpted rather than boxed: the altar has a stepped plinth, the basin has a lip and a visible
  bloodline, the lantern has a hood that throws light downward only.
* Chalk circle is a flat 1px-tall decal with a broken, hand-drawn ring, not a perfect circle -
  it should look like it was drawn by someone in a hurry.
* Building families (gothic bricks, desecrated stone, dark planks, slate tiles, weathered
  plaster) tile seamlessly and come as block + stairs + slab (+ wall for bricks), so a player
  can build in the mod's style rather than only visit it.

## 4. Entities

* Humanoid skins are **64x64 with vanilla part origins**: head (0,0), body (16,16), right arm
  (40,16), left arm (32,48), right leg (0,16), left leg (16,48), overlay parts +32 on x.
  Arms and legs occupy the same rows as vanilla; deviation breaks animation.
* Creature skins are painted per face (top, bottom, right, front, left, back) so that the seam
  between faces stays coherent.
* Bosses use hand-authored UV layouts recorded in `client/model/BossLayout.java`:
  * **Choirmaster**: head (0,0,12,12,12), mitre (48,0,14,10,14), body (0,32,16,24,8),
    right arm (56,0,8,24,8), left arm (88,0,8,24,8), right leg (0,64,8,24,8),
    left leg (32,64,8,24,8), book (64,64,10,14,2).
  * **Unblinking**: eyeball (0,0,64,48,64), iris (0,52,32,32,2), lid (96,0,64,16,64).
* Glow maps (`*_glow.png`) are separate textures at the same layout; they only ever contain the
  spirit/brass families so that emissive detail reads as "light, not paint".

## 5. Models

* Low-poly and clean: cubes, prisms and shallow bevels, no sub-pixel detail that will alias.
* Models are authored to be editable in Blockbench and interoperable with Blender (no scale
  hacks, no negative-scale parts, named parts, one material per logical object).
* Every creature has its own animation set; animation is gameplay information, not decoration:
  the soul projection has a recoil and a separation, the marionette moves on strings, the ritual
  gesture ends with a rune pulse, and the sequence transformation is a sequence of poses.

## 6. VFX categories

Particles are grouped so nothing is ever reused out of context:

| Category | Particle | Reserved for |
| --- | --- | --- |
| Ritual | `rune_dust`, `ember_occult`, `veil_smoke` | circles, altar work, failure smoke |
| Spirit | `spirit_mote`, `soul_flow` | Spirit World ambience, threads, projection |
| Corruption | `black_wisp`, `chromatic_speck` | corruption stages, delusion, chromatic error |
| Blood | `blood_drop` | rites, basin, damage VFX |
| Beyond | `beyond_shard` | Sequence 0, The Noticing, boss death |
| Fear | `shadow_move` | silhouettes, things at the edge of vision |

Every effect must have anticipation, impact and cleanup. A single particle-burst-per-ability is
forbidden: abilities show a wind-up (light gathering), an impact (shaped burst matching the
ability's geometry), and a dissipating tail.

## 7. Interface

Victorian instrument panel: parchment or aged brass plates over charcoal, thin rules, small-caps
serif type, deep inset shadows. The Sanity gauge is a tall glass tube with an engraved scale;
Corruption is a shorter, thicker tube divided into the six stages; the brass plate carries
pathway, Sequence and digestion. Nothing glows in the UI. At low sanity the HUD itself degrades
(the needle jitters, the glass shows hairlines, the tier name lies) - the interface is a
character in the horror, not a readout floating above it.

## 8. Non-negotiables (quality bar)

1. No placeholder textures, ever - if it exists, it is painted.
2. No rainbow particles; every particle belongs to one family above.
3. No sci-fi panels, no blue-glass rectangles, no vanilla GUI with a new title.
4. No recoloured vanilla mobs presented as original creatures.
5. No HP-only bosses: phases, telegraphs, arenas and death sequences are the design.
6. No textures that read as placeholders at 1x scale on a light background.
