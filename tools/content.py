"""
Pathways of the Beyond - CONTENT INDEX (single source of truth).

Every generator (textures, models, lang, data, docs, preview site) reads this file,
and the Java registries mirror it. If a name is not here it does not exist in the mod.
`tools/verify_assets.py` cross-checks Java registrations against this index and fails
the build if the two drift apart.

Naming: mod id `pathwaysofthebeyond`, Java package `com.pathways.beyond`.
"""
from __future__ import annotations

MOD_ID = "pathwaysofthebeyond"
PACKAGE = "com.pathways.beyond"

# =======================================================================================
# PATHWAYS - 8 ladders of 10 sequences. `fool` is implemented to Sequence 0.
# =======================================================================================
# sequence index: 9 = weakest, 0 = strongest (demigod / true supernatural being)
PATHWAYS = [
    {
        "id": "fool",
        "name": "The Fool",
        "epithet": "Pathway of The Fool",
        "color": "#7B5AA0",      # palette: orchid
        "accent": "#B49BD0",     # palette: veil
        "emblem": "the_fool",
        "implemented": 0,        # deepest implemented sequence (0 = complete)
        "lore": (
            "Not a god of trickery but of the space between: the Fool is the pathway of "
            "those who are not there, of masks worn so long they grow a face. Its church "
            "teaches that reality is a negotiation, and that the Fool is the only honest "
            "deity, because it never claims to be real."
        ),
        "sequences": [
            ("seer", "Seer", "Perceive what should not be perceived: auras, hidden veins of ore, "
                             "the shape of approaching danger, and the presence of things that have no "
                             "shadow."),
            ("clown", "Clown", "The body becomes a joke told to physics. Folding steps, dodging "
                              "before the blow exists, and reading an opponent's intent in their posture."),
            ("magician", "Magician", "Misdirection made literal: short blinks through space, decoys "
                                     "that bleed when struck, and perception rewritten for a few seconds."),
            ("faceless", "Faceless", "Wear a face like a glove. Mimic the gait, voice and outline of "
                                     "anything humanoid, and let your own features rest somewhere else."),
            ("marionettist", "Marionettist", "The Soul Thread becomes a leash. Bind the living to your "
                                             "fingers and make puppets of those who still believe they are free."),
            ("bizarro_sorcerer", "Bizarro Sorcerer", "Copies that argue with the original. Local reality "
                                                     "distortion, mirrored terrain, and illusions that keep moving after "
                                                     "you stop concentrating."),
            ("scholar_of_yore", "Scholar of Yore", "Read a place like a page: the memory of stone, the "
                                                   "echoes of what was said here, and the shaping of what nearly happened."),
            ("miracle_invoker", "Miracle Invoker", "Small violations of cause and effect, granted as "
                                                   "favours. Each miracle is answered by something, and that something keeps "
                                                   "a ledger."),
            ("attendant_of_mysteries", "Attendant of Mysteries", "You are no longer a person who knows "
                                                                 "secrets; you are the room the secrets are kept in. Spirit projection, advanced ritual, "
                                                                 "and the authority to forbid."),
            ("the_fool", "The Fool", "You become the space between two truths. Perception, distance and "
                                     "causality become matters of taste. Nothing about you is human any more, and the "
                                     "world has begun to notice."),
        ],
    },
    {
        "id": "sun",
        "name": "The Sun",
        "epithet": "Pathway of The Sun",
        "color": "#D4B265",
        "accent": "#EBD08A",
        "emblem": "the_sun",
        "implemented": 9,
        "lore": ("Light that burns what it blesses. The Sun's priests are healers and executioners "
                 "in the same vestment, and its higher sequences are remembered mostly as brightness "
                 "and then as absence."),
        "sequences": [
            ("bard", "Bard", "Voice, story and courage: bolster allies, and be believed."),
            ("light_supplicant", "Light Supplicant", "Channels daylight into small blessings and into burns."),
            ("solar_high_priest", "Solar High Priest", "Sanctifies ground; unholy things refuse to stand there."),
            ("notary", "Notary", "Binds agreements with real power: an oath broken costs the oathbreaker."),
            ("priest_of_light", "Priest of Light", "Judgement made visible; wounds close as sins are named."),
            ("unshadowed", "Unshadowed", "Casts no shadow, suffers no darkness; light calls you home."),
            ("justice_knight", "Knight of Justice", "Armour of conviction, sword of verdict."),
            ("sun_priest", "Sun Priest", "A walking noon; lesser supernatural things simply cease."),
            ("sun", "The Sun", "You are a small, terrible daylight. Do not look at me."),
            ("sun_god", "The Blazing Sun", "The throne of the Sun is not a seat. It is an orbit."),
        ],
    },
    {
        "id": "death",
        "name": "Death",
        "epithet": "Pathway of Death",
        "color": "#4C6749",
        "accent": "#6F8A63",
        "emblem": "death",
        "implemented": 9,
        "lore": ("The pathway keeps a ledger of everything that has stopped. Its believers are not "
                 "morbid; they are administrative. Nothing is lost, only filed."),
        "sequences": [
            ("corpse_collector", "Corpse Collector", "Speaks with the recently dead and keeps what they no longer need."),
            ("gravedigger", "Gravedigger", "Ground that accepts everything; summons the buried to hold you."),
            ("spirit_medium", "Spirit Medium", "Opens a door for the dead and closes it on time."),
            ("undertaker", "Undertaker", "Prepares vessels: curses travel, possessions break."),
            ("gatekeeper", "Gatekeeper", "Decides what may cross, and what may not come back."),
            ("carrion_king", "Carrion King", "Death feeds you; wounds rot shut."),
            ("pale_rider", "Pale Rider", "Your passage ages the living and gentles the dying."),
            ("death_priest", "Death Priest", "Perform the last rite on anything, including the immortal."),
            ("death", "Death", "Not a killer - a conclusion. You may end a thing completely."),
            ("pale_crown", "The Pale Crown", "Everything that lives feels the ledger shift when you move."),
        ],
    },
    {
        "id": "door",
        "name": "The Door",
        "epithet": "Pathway of The Door",
        "color": "#3A6C96",
        "accent": "#63A2B8",
        "emblem": "the_door",
        "implemented": 9,
        "lore": ("Space is a corridor with too many hinges. The Door's adepts do not travel; they "
                 "arrive, and they seal behind themselves in ways that should not hold."),
        "sequences": [
            ("apprentice", "Apprentice", "Learn any trade impossibly fast; your hands remember what you never practised."),
            ("traveller", "Traveller", "Short steps that cross long distances."),
            ("sleight_of_hand", "Sleight of Hand", "Steal from anywhere: pockets, vaults, memories."),
            ("dimensional_walker", "Dimensional Walker", "Step sideways into the Spirit World at will."),
            ("sealing_master", "Sealing Master", "A room that cannot be entered, a door that cannot be found."),
            ("gate", "Gate", "You are a threshold. What passes through you is changed."),
            ("key_of_doors", "Key of Doors", "Convince any locked thing that it was never locked."),
            ("doors_end", "End of Doors", "Remove the concept of 'here' from a place."),
            ("door", "The Door", "Standing open is the most dangerous thing a god can do."),
            ("the_hinge", "The Hinge", "Father of keys, mother of corridors; both sides obey."),
        ],
    },
    {
        "id": "visionary",
        "name": "The Visionary",
        "epithet": "Pathway of The Visionary",
        "color": "#63A2B8",
        "accent": "#A8DCE2",
        "emblem": "visionary",
        "implemented": 9,
        "lore": ("The mind is the largest country. Visionaries map it, tax it, and occasionally "
                 "declare war on a thought until it apologises."),
        "sequences": [
            ("spectator", "Spectator", "Watch a mind and see its weather."),
            ("telepathist", "Telepathist", "Read intent before language forms."),
            ("psychiatrist", "Psychiatrist", "Enter a mind, tidy it, leave a door open."),
            ("dream_walker", "Dream Walker", "Work in sleep; kill in sleep."),
            ("illusionist", "Illusionist", "A crowd believes your lie so hard it becomes architecture."),
            ("nighmare_lord", "Nightmare Lord", "Fear is a currency and you are the mint."),
            ("mind_city", "City of the Mind", "You keep a city inside your skull and it is populated."),
            ("collective_voice", "Collective Voice", "Every mind in earshot speaks with your accent."),
            ("visionary", "The Visionary", "You have seen the end of thought and did not look away."),
            ("dream_world", "The Dreaming World", "Waking is now the smaller of your two lives."),
        ],
    },
    {
        "id": "black_emperor",
        "name": "Black Emperor",
        "epithet": "Pathway of the Black Emperor",
        "color": "#9C7C3A",
        "accent": "#D4B265",
        "emblem": "black_emperor",
        "implemented": 9,
        "lore": ("Empire is a ritual performed continuously. Its adepts do not command people; they "
                 "command the arrangements that make people behave."),
        "sequences": [
            ("lawyer", "Lawyer", "Find the clause reality forgot, and use it."),
            ("briber", "Briber", "Every price is payable; you always find the coin."),
            ("mentor", "Mentor", "Your students carry your authority into rooms you never enter."),
            ("inventor", "Inventor", "Build the instrument that makes your claim true."),
            ("director", "Director", "Cast the world: roles, props, and a script nobody read."),
            ("politician", "Politician", "Two lies and a compromise; you profit from all three."),
            ("order_maker", "Order Maker", "Write a rule into the local fabric of cause and effect."),
            ("black_emperor_candidate", "Emperor Candidate", "An army follows you because it is elegant."),
            ("black_emperor", "Black Emperor", "You rule by being the thing everyone is arranged around."),
            ("iron_throne", "The Iron Throne", "Not a seat: an organising principle of the world."),
        ],
    },
    {
        "id": "error",
        "name": "Error",
        "epithet": "Pathway of Error",
        "color": "#0E0C10",
        "accent": "#B03A34",
        "emblem": "error",
        "implemented": 9,
        "lore": ("A flaw that propagates. The Error pathway has no doctrine, because doctrine is "
                 "something it can corrupt and it would rather keep its options open."),
        "sequences": [
            ("marauder", "Marauder", "Take what was not yours; leave a copy that rots."),
            ("swindler", "Swindler", "An honest face and a ledger written in someone else's hand."),
            ("assassin", "Assassin", "One movement, no witness, no sound but the after-image."),
            ("corruption_dealer", "Corruption Dealer", "Trade in what makes people less than they were."),
            ("thief_of_bodies", "Thief of Bodies", "Borrow a body; return it wrong."),
            ("shadow_of_the_veil", "Shadow of the Veil", "Your outline lies about where you are."),
            ("grand_thief", "Grand Thief", "Steal an ability, a name, or a season."),
            ("conceptual_marauder", "Conceptual Marauder", "Rob an idea of its meaning and pawn it."),
            ("error", "Error", "You are the typo in creation, and creation keeps failing to fix you."),
            ("the_mistake", "The Great Mistake", "Something went wrong at the beginning. You are it."),
        ],
    },
    {
        "id": "mother",
        "name": "Mother",
        "epithet": "Pathway of Mother",
        "color": "#6F8A63",
        "accent": "#E4D3A8",
        "emblem": "mother",
        "implemented": 9,
        "lore": ("Growth without permission. The Mother pathway is generous in the way a flood is "
                 "generous, and its gardens are full of things that should not flower."),
        "sequences": [
            ("planter", "Planter", "Everything you sow comes up, whether or not you sowed it."),
            ("harvester", "Harvester", "Take life in a wide arc and store it for lean years."),
            ("beast_tamer", "Beast Tamer", "The animal kingdom negotiates with you; you set the terms."),
            ("herbalist", "Herbalist", "Brews that rewrite a body's opinion of itself."),
            ("florist", "Florist", "Plant an instruction and watch the field obey it."),
            ("mother_of_maggots", "Mother of Maggots", "Your children are numberless and they are hungry."),
            ("blight_bringer", "Blight Bringer", "Growth as a weapon: years of rot in one afternoon."),
            ("green_hand", "Green Hand", "The soil answers to your name; the dead soil apologises."),
            ("mother", "Mother", "You are the garden and the gardener and the thing under the soil."),
            ("the_fertile_void", "The Fertile Void", "From nothing, something always grows. Always something."),
        ],
    },
]

PATHWAY_BY_ID = {p["id"]: p for p in PATHWAYS}
IMPLEMENTED_PATHWAYS = [p for p in PATHWAYS if p["implemented"] <= 9]

# =======================================================================================
# SANITY & CORRUPTION
# =======================================================================================
SANITY_TIERS = [
    ("stable", "Stable", 80, 100, "The world is exactly as boring as it claims to be."),
    ("uneasy", "Uneasy", 60, 80, "You are being looked at. Probably."),
    ("hallucinating", "Hallucinating", 40, 60, "Some of what you see is real, and it resents being doubted."),
    ("delusional", "Delusional", 20, 40, "The room is a little too pleased that you are in it."),
    ("insane", "Insane", 1, 20, "Something else is wearing your attention."),
    ("lost_control", "Lost Control", 0, 0, "Your body keeps walking and you are not doing it."),
]

CORRUPTION_STAGES = [
    ("clean", "Untouched", 0, 9, "No visible change. The eyes are still your own."),
    ("stained", "Stained", 10, 24, "Pupils a shade too wide; a mark under the collar."),
    ("marked", "Marked", 25, 44, "Occult sigils surface on the skin, faint and cool to the touch."),
    ("veined", "Veined", 45, 64, "Black veins, moving when not observed; the shadow lags."),
    ("tainted", "Tainted", 65, 84, "Skin turns waxy; voice gains a second tone; joints click wrong."),
    ("monstrous", "Monstrous", 85, 100, "You are no longer shaped like a person who belongs in daylight."),
]

# =======================================================================================
# ITEMS
# =======================================================================================
# --- pathway potions: one per sequence, per implemented sequence -----------------------
POTIONS = [
    # id, pathway, sequence, display name, liquid colour, glow, rarity
    ("potion_seer", "Seer", "fool", 9, "#4B3068", "veil", "occult"),
    ("potion_clown", "Clown", "fool", 8, "#5A0F17", "rose", "occult"),
    ("potion_magician", "Magician", "fool", 7, "#21456B", "cyan_mut", "rare"),
    ("potion_faceless", "Faceless", "fool", 6, "#CDB583", "bone", "rare"),
    ("potion_marionettist", "Marionettist", "fool", 5, "#2F4232", "moss_hi", "rare"),
    ("potion_bizarro_sorcerer", "Bizarro Sorcerer", "fool", 4, "#7B5AA0", "orchid", "epic"),
    ("potion_scholar", "Scholar of Yore", "fool", 3, "#7E5A34", "khaki", "epic"),
    ("potion_miracle_invoker", "Miracle Invoker", "fool", 2, "#EBD08A", "gold", "epic"),
    ("potion_attendant", "Attendant of Mysteries", "fool", 1, "#A8DCE2", "spirit", "legendary"),
    ("potion_the_fool", "The Fool", "fool", 0, "#F6F3F8", "white", "legendary"),
    ("potion_bard", "Bard", "sun", 9, "#9C7C3A", "gold", "common"),
    ("potion_corpse_collector", "Corpse Collector", "death", 9, "#2F4232", "sage", "common"),
    ("potion_apprentice", "Apprentice", "door", 9, "#13273F", "blue", "common"),
    ("potion_spectator", "Spectator", "visionary", 9, "#3A6C96", "cyan_mut", "common"),
    ("potion_lawyer", "Lawyer", "black_emperor", 9, "#3E2A1B", "brass", "common"),
    ("potion_marauder", "Marauder", "error", 9, "#191519", "rust", "common"),
    ("potion_planter", "Planter", "mother", 9, "#4C6749", "sage", "common"),
    ("potion_gravedigger", "Gravedigger", "death", 8, "#262128", "smoke", "occult"),
]

INGREDIENTS = [
    # id, name, tier, origin (short), lore
    ("spirit_flower", "Spirit Flower", 1, "Blooms on graves and battlefields at night",
     "It opens only when nobody is watching, and it closes the instant you look away. "
     "Collectors have learned to gather it with a mirror."),
    ("moonlit_fungus", "Moonlit Fungus", 1, "Caves and cellars, only under moonlight",
     "Fed on moonlight rather than rot. It glows with the colour of a decision you have already made."),
    ("veil_dust", "Veil Dust", 1, "Swept from ritual circles and haunted thresholds",
     "The residue of a door being opened nearby. Tastes of copper and sleep."),
    ("purified_salt", "Purified Salt", 1, "Bought, blessed, or stolen from a chapel",
     "The cheapest protection in the occult world, and the one most often skipped."),
    ("occult_chalk", "Occult Chalk", 2, "Crafted from salt, bone ash and grave earth",
     "White, cold, and slightly greasy. Drawing with it is a promise the ground remembers."),
    ("grave_earth", "Grave Earth", 2, "Dug from freshly turned soil",
     "Soil that has learned the shape of a body and refuses to forget it."),
    ("whispering_bone", "Whispering Bone", 2, "Rare drop from the Whispering Husk",
     "Hold it to your ear and it finishes sentences you were thinking."),
    ("silver_needle", "Silver Needle", 2, "Silversmithing workbench",
     "Threads use it; so do surgeons, and so does the Marionettist."),
    ("starless_crystal", "Starless Crystal", 3, "Deep underground, below y=0",
     "A crystal with no internal light. Under a red moon it glows anyway, which nobody has explained."),
    ("soul_fragment", "Soul Fragment", 3, "Severed Soul Threads and Spirit World motes",
     "A piece of continuity. Warm. It attempts, very faintly, to be the person it came from."),
    ("abyssal_eye", "Abyssal Eye", 3, "Fished out of the deep dark and from Hollow remains",
     "It does not blink, because it has no lids - only patience."),
    ("black_blood_vial", "Black Blood", 3, "Collected from tainted animals and corruption pools",
     "It flows upward when it thinks it is unobserved."),
    ("faceless_skin", "Faceless Skin", 4, "Shed by a Faceless in the middle of a change",
     "Soft as a glove, blank as a page. It fits almost anyone, which is the problem."),
    ("corrupted_heart", "Corrupted Heart", 4, "Pulled from a mob killed at Corruption 80+",
     "Still beating. It beats in your rhythm now, not its original owner's."),
    ("ancient_memory", "Ancient Memory", 4, "Harvested with Scholar of Yore from remembered places",
     "A year, folded small. Unfolding it makes the room remember being younger."),
    ("nightshade_ashes", "Nightshade Ashes", 4, "Burned offerings at a ritual altar",
     "What is left of a plant that was fed a secret. Very fine. Stains the fingers permanently."),
]

ARTIFACTS = [
    ("eye_of_solomon", "The Eye of Solomon", 3,
     "A brass-and-glass eye set in a sealed reliquary. The pupil tracks things with no bodies.",
     "Reveals supernatural entities, hidden ores and invisible marks in a wide radius.",
     "While worn, Sanity drains steadily; and every use makes the eye's pupil larger."),
    ("black_book", "The Black Book", 3,
     "Bound in something that was never an animal. The pages rearrange while you read them.",
     "Opens the Codex of Forbidden Knowledge: grants recipes, ritual knowledge and Sequence insight.",
     "Each reading risks a hallucination, and one page is always blank in a different way."),
    ("whispering_bell", "The Whispering Bell", 2,
     "A cracked brass bell. Its clapper was removed by someone who was careful.",
     "Ringing it reveals hidden entities, summons spirit entities and spikes nearby supernatural activity.",
     "Something specific hears the bell. It is already on its way; the bell simply tells it where you are."),
    ("brass_key", "The Brass Key", 3,
     "A key with no matching lock, warm to the touch, engraved 'FOR EMERGENCIES ONLY'.",
     "Opens a short blink through space - a Door that insists it was always there.",
     "Each use costs a small amount of maximum Sanity until you sleep. Some doors do not close."),
]

UTILITY_ITEMS = [
    ("pathway_codex", "Pathway Codex", "Victorian occult ledger: Pathway screen, Sequences, digestion and lore."),
    ("soul_thread_spool", "Spool of Soul Thread", "Crafting component; the raw material of binding."),
    ("thread_shears", "Thread Shears", "Cut a bound Soul Thread. The puppet remembers being cut."),
    ("ritual_dagger", "Ritual Dagger", "Open a palm for the circle; provides ritual blood."),
    ("occult_compass", "Occult Compass", "Points to the nearest supernatural structure or active ritual."),
    ("ward_charm", "Ward Charm", "Slows Sanity loss and blocks one possession attempt. Then it is spent."),
    ("spirit_tonic", "Spirit Tonic", "Restores Sanity at the cost of a temporary Corruption tick."),
]

# =======================================================================================
# BLOCKS
# =======================================================================================
BLOCKS = [
    # id, kind  (kind drives model/behaviour generation)
    ("ritual_altar", "altar"),
    ("ritual_pedestal", "pedestal"),
    ("ritual_candle", "candle"),
    ("chalk_circle", "chalk"),
    ("occult_table", "table"),
    ("blood_basin", "basin"),
    ("spirit_lantern", "lantern"),
    ("occult_archive", "archive"),
    ("gothic_bricks", "cube"),
    ("gothic_brick_stairs", "stairs"),
    ("gothic_brick_slab", "slab"),
    ("gothic_brick_wall", "wall"),
    ("desecrated_stone", "cube"),
    ("desecrated_stone_stairs", "stairs"),
    ("desecrated_stone_slab", "slab"),
    ("weathered_plaster", "cube"),
    ("dark_planks", "cube"),
    ("dark_plank_stairs", "stairs"),
    ("dark_plank_slab", "slab"),
    ("iron_grate", "grate"),
    ("slate_tiles", "cube"),
    ("slate_tile_stairs", "stairs"),
    ("slate_tile_slab", "slab"),
    ("veil_glass", "glass"),
    ("void_stone", "cube"),
    ("spirit_flower", "plant"),
    ("moonlit_fungus", "plant"),
    ("memory_shard_block", "shard"),
]

# =======================================================================================
# ENTITIES
# =======================================================================================
REGULAR_ENTITIES = [
    # id, category, behavior summary, threat, sanity drain
    ("watcher", "observer", "Never attacks. Stands at distance, leaves when looked at directly.", "none", 0.6),
    ("hollow", "stalker", "Humanoid silhouette that follows at fog distance and closes in when ignored.", "low", 0.9),
    ("whispering_husk", "hostile", "Fast, loud, whispers your own messages back at you; drops Whispering Bone.", "moderate", 1.4),
    ("veil_tenant", "possessor", "Spirit-world hunter that tries to inhabit an abandoned body.", "high", 2.0),
    ("spirit_wisp", "ambient", "Harmless drifting mote-light of the Spirit World.", "none", 0.2),
]

SUMMONED_ENTITIES = [
    ("player_body_shell", "shell",
     "What is left behind when the soul steps out: your own body, breathing, and not defending itself.", "none", 0),
    ("marionette", "puppet", "A mob bound by Soul Thread. Obeys the Marionettist, moves on strings.", "n/a", 0.0),
    ("bizarro_clone", "clone", "A Bizarro copy of the caster. Fights, then argues, then unravels.", "n/a", 0.0),
    ("historical_echo", "echo", "A replay of a creature that died here, summoned by Scholar of Yore.", "n/a", 0.0),
]

BOSSES = [
    ("choirmaster_of_the_veil", "The Choirmaster of the Veil", "choirmaster",
     "Once the precentor of a chapel that sang the wrong hymn. Three phases: Procession, "
     "Antiphon, Silence. Punishes players who stand still and players who never look up.",
     4),
    ("the_unblinking", "The Unblinking", "unblinking",
     "A pupil the size of a door, and behind it the Choir. Fought across the Overworld and the "
     "Spirit World at once; its phases are defined by which side of the Veil you are standing on.",
     5),
]

# =======================================================================================
# EFFECTS / PARTICLES / SOUNDS
# =======================================================================================
EFFECTS = [
    ("sanity_bleed", "Sanity Bleed", "bad", "Drains Sanity continuously."),
    ("hallucinating", "Hallucinating", "bad", "Forces hallucinations regardless of Sanity tier."),
    ("spirit_form", "Spirit Form", "neutral", "Soul projection: translucent spirit body."),
    ("corruption_surge", "Corruption Surge", "bad", "Corruption climbs quickly; power climbs with it."),
    ("thread_bond", "Thread Bond", "neutral", "A Soul Thread is attached to you."),
    ("unblinking_gaze", "Unblinking Gaze", "bad", "You are being observed; Sanity drains and mobs track you."),
    ("curse_of_the_veil", "Curse of the Veil", "bad", "Failed ritual backlash: random hallucinations + mob hostility."),
    ("clarity", "Clarity", "good", "A prepared mind: Sanity loss reduced, hallucination immunity."),
    ("digestion_quickened", "Digestion Quickened", "good", "Pathway potion digests noticeably faster."),
    ("cosmic_dread", "Cosmic Dread", "bad", "Endgame. Everything knows where you are."),
]

PARTICLES = [
    ("spirit_mote", "soft round mote that drifts upward and fades - spirit world ambience"),
    ("rune_dust", "square-edged glyph flake that spins as it falls - ritual circles"),
    ("ember_occult", "candle ember with a slow flicker - candles, altars, forbidden rooms"),
    ("black_wisp", "short-lived shadow wisp that smears - corruption"),
    ("chromatic_speck", "hard-edged speck with a violet/cyan split - sanity distortion"),
    ("soul_flow", "teardrop mote that travels along Soul Threads - threads, puppets"),
    ("veil_smoke", "low, wide smoke that hugs the ground - failures, veil thinning"),
    ("beyond_shard", "impossible polygon that rotates on two axes - The Beyond"),
    ("blood_drop", "small dark droplet with gravity - ritual blood"),
    ("shadow_move", "hard-edged dark smear that slides sideways - dodges, folds, failed teleports"),
]

SOUNDS = [
    # name, category, description used for docs + preview site
    ("potion_drink", "item", "wet, resonant swallow with a low bell tail"),
    ("potion_brew", "item", "slow bubbling under a held breath"),
    ("potion_finish", "item", "glass stopper, then a sigh that is not yours"),
    ("codex_open", "item", "leather creak, brass clasp, distant page-turn"),
    ("codex_page", "item", "dry page, ink scratch, whisper underneath"),
    ("bell_ring", "item", "cracked brass bell, no clapper"),
    ("bell_answer", "item", "something very large answers, from very far away"),
    ("artifact_activate", "item", "brass mechanism, breath intake, hum"),
    ("artifact_curse", "item", "deal closing: reversed chime and a soft laugh"),
    ("thread_bind", "item", "fibre tightening, wet click"),
    ("thread_sever", "item", "taut string snapping inside flesh"),
    ("thread_tension", "item", "rising string drone, off-key"),
    ("chalk_draw", "item", "gritty scrape with a faint glassy overtone"),
    ("dagger_cut", "item", "shallow cut, blood hitting stone"),
    ("ritual_start", "ritual", "candles catching one by one, then a chord"),
    ("ritual_loop", "ritual", "looping drone: organ, breathing, brittle metronome"),
    ("ritual_pulse", "ritual", "glyphs flash: filtered thump and reversed cymbal"),
    ("ritual_chant", "ritual", "layered wordless chant, no vowels"),
    ("ritual_complete", "ritual", "chord resolving upward into a closing door"),
    ("ritual_fail", "ritual", "a dropped instrument, then a room exhaling"),
    ("whisper_near", "sanity", "a voice at conversational distance, no words"),
    ("whisper_far", "sanity", "many voices in another room"),
    ("heartbeat", "sanity", "two-thump heartbeat, slowly becoming two heartbeats"),
    ("breathing", "sanity", "breath that lands slightly out of sync with yours"),
    ("knock", "sanity", "three flat knocks, wood and knuckle"),
    ("rumble", "sanity", "sub-bass pressure, felt more than heard"),
    ("static", "sanity", "tape hiss with a periodic buried tone"),
    ("watcher_stare", "entity", "attention focusing, high thin tone"),
    ("watcher_vanish", "entity", "air closing where something stood"),
    ("hollow_step", "entity", "one bare footstep, longer stride than a human's"),
    ("hollow_idle", "entity", "wet joint adjustments and a held breath"),
    ("husk_whisper", "entity", "several voices layered inside one throat"),
    ("husk_attack", "entity", "sudden lunge with a vocal crack"),
    ("marionette_joint", "entity", "wooden joint click on string tension"),
    ("choirmaster_chant", "entity", "liturgical melody sung slightly flat"),
    ("choirmaster_scream", "entity", "choir all inhaling at once then breaking"),
    ("unblinking_gaze", "entity", "an enormous lid opening in a small room"),
    ("unblinking_hurt", "entity", "glass and fluid cracking together"),
    ("unblinking_death", "entity", "the eye closes: pressure release and long decay"),
    ("tenant_possess", "entity", "a body being entered and disagreeing"),
    ("sequence_advance", "sequence", "upward surge, bells, then a heartbeat that is not yours"),
    ("sequence_digest", "sequence", "stomach settling in a body that has changed"),
    ("corruption_pulse", "corruption", "black vein crawling under skin, close mic"),
    ("spirit_enter", "spirit", "the veil parting: reverse reverb and cold"),
    ("spirit_exit", "spirit", "returning too fast, ringing in the ears"),
    ("spirit_ambient", "spirit", "looping desolate wind and distant impossibilities"),
    ("spirit_strain", "spirit", "soul tether stretching past tolerance"),
    ("spirit_snap", "spirit", "tether parting; a caught breath"),
    ("red_moon", "event", "sky shifting colour: low brass swell"),
    ("veil_thins", "event", "fabric tearing, very slowly, in every direction"),
    ("whispering_night", "event", "the whole world whispering in one long breath"),
    ("memory_echo", "item", "a place remembering: reversed ambience and one distant event"),
    ("faceless_shift", "item", "skin and features rearranging, close mic"),
    ("authority_forbid", "item", "a command given in a language with no vowels"),
    ("beyond_call", "beyond", "an inverted bell, a long way below"),
    ("beyond_arrival", "beyond", "the sound of being noticed by something patient"),
]

# =======================================================================================
# STRUCTURES / WORLDGEN / EVENTS
# =======================================================================================
STRUCTURES = [
    # id, display name, biome hint, pieces (nbt sizes), feature
    ("occult_mansion", "Abandoned Occult Mansion", "#minecraft:is_forest",
     "Grand Victorian house, collapsed east wing, séance parlour, sealed attic, servants' corridor."),
    ("ruined_chapel", "Ruined Chapel", "#minecraft:is_overworld",
     "Half-collapsed nave, intact crypt stair, the Choirmaster's lectern."),
    ("forbidden_library", "Forbidden Library", "#minecraft:is_taiga",
     "L-shaped stacks, restricted room behind a false shelf, reading circle, ink-stained floor."),
    ("ritual_chamber", "Underground Ritual Chamber", "#minecraft:is_overworld",
     "Carved chamber under y=30, candle ring, blood basin, four sealed alcoves."),
    ("catacombs", "Catacombs", "#minecraft:is_overworld",
     "Bone-lined corridor network, ossuary niche, one door that should not be a door."),
    ("witch_laboratory", "Witch Laboratory", "#minecraft:is_swamp",
     "Stilted hut over water, distillation apparatus, specimen jars, cellar hatch."),
    ("sealed_shrine", "Sealed Shrine", "#minecraft:is_mountain",
     "Shrine sealed with iron bands; opening it is a decision with a cost."),
    ("spirit_ruins", "Spirit World Ruins", "spirit_wastes",
     "Impossible geometry: stairs that lead nowhere, rooms inverted, doors set in open air."),
]

WORLD_EVENTS = [
    ("red_moon", "The Red Moon", "rare", 1.0,
     "The moon turns crimson; supernatural mobs gain aggression and Starless Crystal glows."),
    ("whispering_night", "Whispering Night", "uncommon", 0.7,
     "Directional whispers follow every player; Sanity drains slowly and grows back faster in candlelight."),
    ("veil_thins", "The Veil Thins", "uncommon", 0.6,
     "Spirit entities spawn in the Overworld and structures within 200 blocks 'echo'."),
    ("empty_village", "The Empty Village", "rare", 0.3,
     "A village's villagers are gone; door open, meals still warm, one entity left behind."),
    ("the_watcher", "The Watcher", "rare", 0.5,
     "A Watcher begins appearing at distance for every player. It is not hostile. That is the horror."),
    ("memory_echo", "Memory Echo", "uncommon", 0.8,
     "The location replays an event that happened there: ghost blocks, sounds, spectral actors."),
    ("the_noticing", "The Noticing", "beyond", 0.0,
     "Endgame only. The Beyond becomes aware of the player: sky warps, impossible structures form."),
]

# =======================================================================================
# CREATIVE TABS / GUI / SHADERS
# =======================================================================================
TABS = [
    ("pathways", "Pathways of the Beyond", "pathway_codex"),
    ("pathway_ritual", "Occult Ritual", "ritual_altar"),
    ("pathway_artifacts", "Sealed Artifacts", "eye_of_solomon"),
    ("pathway_beyond", "The Beyond", "void_stone"),
]

CORE_SHADERS = [
    ("soul_thread", "procedural glowing fibre ribbon: animated energy flow along the thread, "
                    "fresnel falloff, per-vertex colour, tension jitter"),
    ("spirit_body", "translucent spirit form: rim-light outline, vertical dissolve, "
                    "screen-space scanline displacement"),
    ("occult_aura", "corruption/sequence aura: black-veined fresnel shell, noise-driven "
                    "pulse, desaturated palette clamp"),
    ("rune_surface", "ritual circle glyph layer: animated glyph scroll, additive glow, "
                     "stability flicker"),
    ("sanity_distort", "full-screen sanity post pass: chromatic split, barrel warp, "
                       "vignette, film grain, silhouette bleed"),
]

DIMENSIONS = [
    ("spirit_world", "The Spirit World", "Desaturated, fog-bound supernatural layer of the Overworld.",
     "Fog-drowned wastes of grey-black stone, floating broken architecture, distant "
     "silhouettes that are always the same silhouette."),
]
