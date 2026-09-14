# Pathways of the Beyond

A supernatural progression mod for **Minecraft 1.21.1 (NeoForge)** in the register of Western
Occult, Victorian dark fantasy and cosmic horror. It replaces vanilla enchanting and potion
brewing with a ladder of **Pathways** and **Sequences**: you drink what a Pathway demands, you
behave like the Pathway to digest it, and you attempt each new Sequence in a working ritual
circle. Every rung upward costs Sanity and buys Corruption.

> Power is never free. The deeper you go, the less human you look, sound and move. Sequence 0 is
> not a victory screen. It is the moment something notices you.

---

## Requirements

| | |
| --- | --- |
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.172+ |
| Java | 21 |
| Side | Client + server (server must also install it; there are no client-only shortcuts) |

## Building

```bash
./gradlew build            # jar in build/libs/
./gradlew runClient        # dev client
./gradlew runServer        # dev server
./gradlew runData          # regenerate data (recipes, tags, worldgen) where applicable
```

The project is Gradle + NeoForge ModDevGradle 2.0.78 with `mod_id = pathwaysofthebeyond`.

## Content overview

### Pathways and Sequences (§1-2, §23)
Ten Sequences per Pathway, 9 down to 0. The **Fool** Pathway is implemented in full depth, with
per-Sequence names, icons, colours, lore, passives, actives, transformation and colour shifts:
*Seer, Clown, Magician, Faceless, Marionettist, Bizarro Sorcerer, Scholar of Yore, Miracle
Invoker, Attendant of Mysteries, The Fool.* 40 abilities are registered
(`pathway/Abilities.java`), each with its own sanity cost, corruption cost, cooldown and VFX
category. Sequence 0 is not a spam button: it changes what the player is.

### Potions as progression (§3)
Potion of the Seer, of the Clown, and so on down the ladder. No vanilla experience is used
anywhere in progression. Potions are **brewed by infusion** (`item/PathwayInfusion.java`): a
brewing stand, a water bottle, a catalyst ingredient for the Sequence, fuel, and a lit ritual
candle within four blocks. Drinking is a real animation with distortion, particles and a
digestion meter; digesting requires *behaving* like the Pathway, and drinking or acting off your
Pathway reduces digestion, adds Corruption and starts the hallucinations.

### Sanity, Corruption and delusion (§4-5)
Six sanity tiers (Stable, Uneasy, Hallucinating, Delusional, Insane, Lost Control) and six
Corruption stages (Clean, Stained, Marked, Veined, Tainted, Monstrous). Hallucinations are
drawn from a pool of 19 kinds (`sanity/HallucinationDirector.java`): fake mobs and players,
footsteps, whispers, doors, false torches and blocks, silhouettes, item glitches, camera shake,
fog shifts and directional audio. Hallucinated creatures **drop nothing and give no experience**,
so psychosis is never farmable.

### Rituals (§6)
A visual, positional system: chalk circles, ritual candles, a blood basin, an altar, a pedestal
and an archive. Stability is simulated (participants, presence, pain, clarity, danger), and
failure is not a refund: sanity loss, corruption, hallucinations, a curse, hostile spawns, a
non-destructive explosion and world distortion. 13 recipes are defined in `ritual/RitualRecipes.java`.

### The Spirit World and Soul Projection (§7)
A real dimension (`pathwaysofthebeyond:spirit_world`) with its own chunk generator, desaturated
fog, motes, distant silhouettes that vanish when approached, and ruins that generate
procedurally. **Soul Projection** leaves your body behind as a physical entity: a soft tether at
48 blocks, a hard limit at 160, strain above 25% rolling for possession, wards that can block it,
and a snap that summons something to where the thread broke.

### Soul Threads (§8)
The Marionettist sees and works with threads. Bind a creature and an animated glowing fibre runs
from your hand to it: per-entity colour, sag, lag and a rattle when the creature resists. Threads
strain with distance and snap. Puppets change how they move, how their head sits and whether
their eyes track you.

### Sealed artifacts (§9)
*Eye of Solomon*, *Black Book*, *Whispering Bell* (with the *Brass Key*). Each is found **sealed**
and must be unsealed at an altar; each has a unique model, lore, passive, active, drawback and
sound. All four drawbacks are about attention: using them tells the world where you are.

### Ingredients and creatures (§10-11)
16 harvestable ingredients (Spirit Flower, Abyssal Eye, Whispering Bone, Black Blood, Moonlit
Fungus, Soul Fragment, Corrupted Heart, Faceless Skin, Ancient Memory, Starless Crystal and more),
several of which only appear at night, in the Spirit World, at low sanity, or after events.
Creatures: the Watcher (never attacks, only watches), Hollow, Whispering Husk, Veil Tenant,
Spirit Wisp, the Marionette, Bizarro Clone, Historical Echo, and the player's own body shell.

### Bosses (§12)
**The Choirmaster of the Veil** — invulnerable while singing; you must silence the choir first.
Phases: Intro, First Antiphon, Silence, Second Antiphon, Finale, Dying.
**The Unblinking** — an eye that cannot close. It sees through decoys, attacks through walls, and
cannot be killed into a victory: it ends by beginning **The Noticing**.

### Worldgen, events and the endgame (§21-22, §24)
8 structures (occult mansion, ruined chapel, forbidden library, witch laboratory, sealed shrine,
underground ritual chamber, catacombs, Spirit World ruins) each with a hidden space or a trap,
placed biome-aware with a persisted placed-set so nothing generates twice. 7 world events (Red
Moon, Whispering Night, Veil Thins, Empty Village, The Watcher, Memory Echo, The Noticing).
The endgame is The Beyond: impossible structures, strange stars, distorted physics - the player
is not victorious, merely *noticed*.

---

## Playing

* **Begin.** Find or brew a *Potion of the Seer* and drink it. That starts the Fool Pathway at
  Sequence 9 and installs the Sanity/Corruption HUD.
* **Digest.** Act like the Pathway: observe supernatural things, study with a Pathway Codex, take
  part in rituals, explore. The Pathway screen shows digestion.
* **Advance.** At 100% digestion, build a circle (chalk, candles, basin, altar) and perform the
  Sequence ritual. Advancement is attempted in the circle, never bought.
* **Cast.** Open the Pathway screen — unlocked abilities appear as buttons and request the cast
  from the server (`use_ability`), which re-validates cost, cooldown and range.
* **Screens.** Pathway (`Pathway Codex`), Codex (recipes and components), Ritual (right-click an
  altar), plus the HUD instrument for Sanity, Corruption, digestion and tether strain.

### Commands (`/pathways`)

| Command | Purpose |
| --- | --- |
| `/pathways info` | pathway, sequence, digestion, sanity tier, corruption stage, threads |
| `/pathways sanity\|corruption\|sequence <value>` | operator state control |
| `/pathways advance` | attempt advancement (fills digestion first) |
| `/pathways ritual list\|site` | known recipes, and the current ritual site readout |
| `/pathways event <id>` / `/pathways event noticing` | fire a world event / begin The Noticing |
| `/pathways spirit enter\|exit\|project <bool>` | Spirit World travel and projection |
| `/pathways structure list\|place <id> [pos]` | worldgen debug |

---

## Asset pipeline (no placeholders, everything reproducible)

All art and audio is generated from source scripts, so the mod ships zero placeholders:

```bash
python3 tools/gen_items.py        # 45 item icons + potion variants
python3 tools/gen_blocks.py       # 21 block textures
python3 tools/gen_block_icons.py  # isometric block icons for inventory
python3 tools/gen_models.py       # blockstates + item models (JSON)
python3 tools/gen_entities.py     # 11 entity skins + client/model/BossLayout.java
python3 tools/gen_gui.py          # GUI panels, logo, effect icons, 15 particle sprites
python3 tools/gen_sounds.py       # 56 procedurally synthesised Ogg Vorbis sounds
python3 tools/gen_java_registries.py  # registries, lang (en_us + vi_vn), sounds.json
python3 tools/contact_sheet.py preview sheets
python3 tools/verify_assets.py    # the gate: textures, sounds, lang, refs, palette, balance
```

`tools/content.py` is the single source of truth: 8 pathways, 28 blocks, 45 items, 10 entities,
10 effects, 10 particles, 56 sounds, 8 structures, 7 world events, 5 core shaders, 1 dimension.
Changing it and re-running the generators is how content is added.

Colour, pixel-art, entity-UV and VFX rules live in **[docs/ART_BIBLE.md](docs/ART_BIBLE.md)**;
sound design rules and the full sound bank live in **[docs/SOUND_BIBLE.md](docs/SOUND_BIBLE.md)**.

---

## Repository layout

```
src/main/java/com/pathways/beyond/
  PathwaysMod.java         mod entry, tick order, listener registration
  pathway/                 PathwayData (generated), PlayerPathway, SequenceLogic, Abilities
  sanity/                  OccultState, SanitySystem, HallucinationDirector
  ritual/                  RitualRecipes, RitualManager
  soul/                    SoulThreadManager
  spirit/                  SoulProjection, SpiritWorld, SpiritChunkGenerator
  artifact/                ArtifactEvents
  event/                   PathwayEvents, WorldEventManager, MemoryEchoRuntime, DelayedWorldActions
  entity/                  OccultEntities, EntityEvents, boss/
  item/ block/ effect/     OccultItems, PathwayInfusion, OccultBlocks, OccultEffects
  network/                 OccultMessages (protocol), ModNetwork (payloads + helpers)
  client/                  ClientPacketHandler, ClientEvents, ClientScreens, SanityHudRenderer,
                           ClientThreadRender, OccultRenderers
  worldgen/                StructurePlacer, ProceduralStructures
  command/                 PathwaysCommand
tools/                     pixel art DSL, content index, generators, verifier
docs/                      ART_BIBLE.md, SOUND_BIBLE.md
preview/                   generated contact sheets for art review
```

## Verification

`python3 tools/verify_assets.py` is the project gate. It checks texture presence and suspicious
placeholders, that every sound event in `sounds.json` maps to a real audible Ogg, language key
parity, JSON validity, that every `ModItems.*` / `ModBlocks.*` / `ModEntities.*` / `ModSounds.*` /
`ModParticles.*` / `ModEffects.*` name referenced in Java exists in the content index, brace
balance of every Java source, and palette drift across all textures.

There is no JDK in the authoring sandbox, so the mod is not compiled here: the Java is written
against the NeoForge 1.21.1 API and verified structurally. Build it with `./gradlew build`.

## Known limitations

* Structure NBT templates are optional: worldgen prefers an authored NBT and otherwise builds the
  procedural layout in `worldgen/ProceduralStructures.java`. The procedural layouts are complete
  places (walls, roofs, loot, hidden rooms, traps), intended to be replaced by art-authored
  templates over time.
* Only the Fool Pathway is deep; the other seven Pathways have names, lore, colours and Sequences
  defined in the data (`PathwayData`) and are the next content pass.
* Ability casting is driven from the Pathway screen; the server-side entry point
  (`use_ability` -> `Abilities.request`) is complete and validates everything.
