# Pathways of the Beyond - Sound Bible

All 56 sounds in the mod are **synthesised from scratch** by `tools/gen_sounds.py` (numpy, then
encoded to Ogg Vorbis). There are no samples and no borrowed assets: every sound is a small
physical model - filtered noise, resonant bodies, formants, and a shared reverb bus. That keeps
the palette sonically coherent and makes the whole bank reproducible from source.

## 1. Rules

1. **Diegetic or ambiguous, never a sting.** No orchestral hit on a jump scare. The player should
   not be able to tell whether a sound was triggered by the game or by their own mind - that
   ambiguity is the entire horror engine.
2. **Low, wide, slow.** Most sounds sit under 4 kHz with energy below 500 Hz. High-frequency
   content is reserved for the two moments that must cut through: ritual failure and the
   Unblinking's gaze.
3. **Every sound has a body.** Sounds are built from a resonator (bell, drum, glass, throat) plus
   an air component (breath, wind, whisper). Nothing is a raw sine blip.
4. **Reverb is part of the design.** The shared Schroeder-style bus is clamped (comb feedback
   < 0.95, allpass feedback = g) so it can never ring or destabilise. Big rooms for ritual and
   the Beyond, dry for threads and daggers.
5. **Direction matters.** Whispers, footsteps and breathing are played as positional sounds at
   the *hallucinated* position, so the player turns toward nothing.

## 2. The bank by category

### Ritual (7)
`ritual_start` (chalk scratching into a low drone), `ritual_loop` (the circle breathing),
`ritual_pulse` (a single brass ring per beat), `ritual_chant` (many voices that never resolve
into a chord), `ritual_complete` (a resolved bell and a room exhale), `ritual_fail` (the drone
inverts, glass stresses, the room loses its reverb), `chalk_draw` (dry scraping on stone).

### Potions (3)
`potion_brew` (slow bubbling under a held breath), `potion_drink` (a wet swallow with a bell
tail), `potion_finish` (glass stopper, then a sigh that is not yours).

### Artifacts (2)
`artifact_activate` (brass mechanism, glass turning, a thin tone), `artifact_curse` (that tone
arriving late and wrong), plus `bell_ring` (cracked brass bell) and `bell_answer` (something
replying from far away, in the same pitch, one octave down).

### Threads (3)
`thread_bind` (a fibre pulled taut), `thread_tension` (the rattle of a binding that is being
resisted - this is the warning before it snaps), `thread_sever` (a clean cut with an ugly tail).

### Codex and knowledge (2)
`codex_open` (leather creak, brass clasp, a page turn in another room), `codex_page` (ink
scratching over a whisper).

### Creatures (14)
`hollow_step`, `hollow_idle`, `husk_whisper`, `husk_attack`, `marionette_joint` (wood and wire
under tension), `tenant_possess`, `watcher_stare` (a held breath that never releases),
`watcher_vanish` (the breath ending in the wrong direction), `choirmaster_chant`,
`choirmaster_scream`, `unblinking_gaze`, `unblinking_hurt`, `unblinking_death`,
`spirit_ambient`.

### Low sanity and mind (9)
`whisper_near`, `whisper_far`, `heartbeat`, `breathing`, `knock`, `rumble`, `static`,
`memory_echo` (a voice playing backwards at half speed), `faceless_shift` (a face rearranging).

### World events (5)
`red_moon` (a low brass swell under the sky), `veil_thins` (reverb opening where there was none),
`whispering_night` (a crowd of whispers at conversational volume), `beyond_call`,
`beyond_arrival` (the Beyond: sub-bass, an interval that is not in the scale, and a long decay
that does not reach zero).

### System (7)
`sequence_advance` (a body changing pitch), `sequence_digest` (something being metabolised),
`corruption_pulse` (a slow heartbeat of blood and iron), `spirit_enter`, `spirit_exit`,
`spirit_strain` (the tether under load), `spirit_snap` (the tether failing: a whip-crack that
opens into silence), `authority_to_forbid` (a command, in a voice with no mouth).

### Blocks and ritual objects (4)
`dagger_cut`, plus the lantern/candle and archive ambience folded into `ember`-class noises
reused from `ritual_pulse` and `codex_page` at low volume and randomised pitch.

## 3. Mixing and space

* Ambient and ritual sounds ride a long reverb (2.5-4.5 s tail); creature sounds ride a short one
  (0.6-1.2 s); system and thread sounds are nearly dry so they feel like they are happening to
  *you* rather than in the room.
* Subtitles are generated for every sound (`sound.pathwaysofthebeyond.<id>`), because a horror
  mod that is unplayable without hearing is a horror mod that excludes players.
* Silence is a tool: after `spirit_snap`, after a boss death, and for the first two seconds of
  The Noticing, the mod deliberately plays nothing.

## 4. Implementation notes

* Generator self-checks: it aborts if any produced buffer is non-finite or has RMS below 0.01,
  which is how the earlier "silent Ogg" bug (unstable IIR feedback producing NaN) was caught.
* `tools/verify_assets.py` re-checks every Ogg for a plausible file size and every `sounds.json`
  entry for a real file, so a missing or silent sound fails the build rather than shipping.
