# Pathways of the Beyond 1.0.0

**Minecraft 1.21.1 · NeoForge 21.1.172+ · Java 21**

Victorian occult, forbidden knowledge and cosmic horror. Pathways and Sequences replace vanilla
enchanting as the power ladder; pathway potions replace experience as the currency; every step
upward costs Sanity and buys Corruption.

## In this release

**Pathways & Sequences.** Ten Sequences per Pathway, 9 → 0, with the Fool Pathway implemented in
full: per-Sequence name, colour, lore, passives, actives, ritual, potion, corruption and visual
transformation. 40 abilities, each with its own sanity and corruption cost, cooldown and VFX
category. Sequence 0 is a transformation, not a cast.

**Sanity, Corruption, delusion.** Six sanity tiers (Stable → Lost Control), six corruption stages
(Clean → Monstrous) with a visible long-term transformation, and 19 kinds of hallucination:
fake mobs and players, footsteps, whispers, doors, false blocks and torches, silhouettes, item
distortion, text glitch, camera shake, fog and light shifts, directional audio. Hallucinated
creatures drop nothing and grant no experience.

**Rituals.** A positional visual system: chalk circles, ritual candles, blood basin, altar,
pedestal and archive. Live stability simulation, seven-step activation, and a failure table that
costs sanity, corruption, curses, hostile spawns, explosion and world distortion. 13 recipes.

**Spirit World & Soul Projection.** A real dimension with its own chunk generator, desaturated
fog, motes and vanishing silhouettes. Projection leaves your body behind as a physical entity
with a 48-block soft tether, a 160-block hard limit, strain, possession rolls, wards and a snap.

**Soul Threads.** Bind, command and sever: animated glowing fibres with per-entity colour, sag,
lag and a rattle when the bound creature resists. Puppets move, stand and look differently.

**Sealed artifacts.** Eye of Solomon, Black Book, Whispering Bell and the Brass Key. Found
sealed, unsealed at an altar, each with a passive, an active and a drawback that costs attention.

**Bosses.** The Choirmaster of the Veil (invulnerable while singing — silence the choir first,
with six phases and telegraphed strikes) and The Unblinking (sees through decoys, attacks through
walls, cannot be killed into a victory).

**World.** 8 structures with hidden rooms and traps, 7 world events, spirit-world ruins, and the
endgame: The Beyond, where the player is not victorious, merely noticed.

**Content.** 45 items, 28 blocks, 11 entities, 10 effects, 10 particles, 56 sounds, 2 bosses,
8 structures, 7 world events, 1 dimension — all assets generated from source scripts, no
placeholders.

## Installing

1. Install NeoForge 21.1.172 or newer for Minecraft 1.21.1.
2. Drop `pathwaysofthebeyond-1.0.0+mc1.21.1.jar` into `mods/`.
3. Install on the server too: the supernatural simulation is server-authoritative.

## Notes

* Structures generate procedurally out of the box (walls, roofs, loot, hidden rooms, traps) and
  will prefer authored NBT templates when those are added.
* Seven of the eight Pathways have names, lore, colours and Sequence ladders defined; they are
  the next content pass.
* Command reference and full documentation: see the repository README.
