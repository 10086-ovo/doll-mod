# Doll Mod 1.0.0-r15 — Forge Edition

First release of the **Forge** edition. Full feature parity with the Fabric edition (1.0.1) — gameplay, content, textures, texts, recipes and loot are identical on both loaders.

**Requires:** Minecraft 26.2 · Forge 65.1.0+

## Highlights

- **The complete Doll system, ported to Forge.** Summon, command and equip your dolls: 8 behavior modes (melee / ranged / farming / woodcutting / mining / fishing / feeding / torch-placing), boss dolls, doll inventory (81 slots), egg recycling with inventory & configuration preservation, and the Guide Book.
- **All themed weapons & gear**: Rock Anvil (3-stage durability), Nether Sword, Ender Axe, Pale Bow, Thorn Shield, Sea Armor set (buried treasure loot), and the new **Guide Pickaxe**.
- **New structure search subsystem (GeoIndex).** Search for structures, villages and biomes from the Guide Search screen. Indexes grow on demand around the player instead of pre-building at server start — zero startup cost. Structure candidates are verified in parallel, and nearby villages are pre-confirmed so results skip re-validation.
- **Doll talents.** Per-variant passive effects, auras and combat abilities (Warden sonic boom, Nether fireballs, Ender teleport strikes & executions, Sea laser) implemented as strategy classes.

## Fixes & alignment work done during the port

- **Fishing treasure fixed.** Dolls could never obtain treasure items (enchanted books, name tags, saddles, nautilus shells, enchanted bows and fishing rods) — the vanilla loot table gates its treasure entry behind an "open water" check that could never pass for a doll, silently removing the entry from the roll and leaving Luck of the Sea with nothing to act on. Dolls now treat any water they can fish in as open water. Applies to every doll variant, not just the Sea Doll.
- **Sea Armor now drops as a full set from buried treasure.** The loot table referenced 15 sub-tables that were missing on Forge, and the top-level table was still an older version that could only ever yield a single piece. Both are now in sync with the Fabric edition: 2–4 pieces per chest, each with randomized durability and a 50% chance of a random enchantment.
- **Mining now uses an exposed-face check** instead of a plain line-of-sight check, so ores showing only one exposed face are mineable, and dolls no longer target buried ores they cannot reach — they mine what they can see and reach, and walk closer otherwise.
- **Sea Armor pieces now tick while worn by a doll.** Dolls have no inventory tick loop of their own, so the helmet's air supply, the chestplate's underwater night vision and the boots' water-walking never applied. Each armor slot is now ticked explicitly.
- **Nether flying sword** brought up to date with the Fabric edition: tip-based hit detection, dash wind-up, retreat timeout fallback, render interpolation between ticks, corrected model alignment angle and pose transform order, and idle behavior for swords summoned without an owner. It also no longer pushes or gets pushed by other entities — high-knockback-resistance mobs could previously shove it out of position, leaving it unable to deal damage until one side moved.
- **Dolls now level their pitch while moving**, instead of running to their owner while still staring at the ground or at the sky.
- **Warden dolls' sonic boom now originates from the doll**, travelling along the ray from the doll's eye position to the target's eye position, rather than appearing at the target's position.
- Fixed the doll inventory screen occasionally opening **empty** (items appeared only after clicking a slot) — a Forge networking race between the container-open and initial-content packets. A lightweight re-sync request now heals it automatically.
- Fixed a potential disconnection when switching from the doll inventory to other screens (container close packet handling, aligned with Fabric).
- Guide Book: long pages now scroll (scrollbar + mouse wheel) instead of drawing past the bottom border, and the entry-list height no longer pushes the last row outside the content area.
- Startup console noise reduced to match the Fabric edition (JVM native-access / Unsafe warnings suppressed; remaining Forge-side DNS-hack error is harmless).
- Resource parity audit against the Fabric edition: guide book texts, recipes, crafting advancements, tags and textures are now fully in sync.

## Notes

- The mod id on Forge is `dollmod` (Fabric uses `doll-mod`); resource/data namespaces are `doll-mod:` on both loaders, so data packs and commands work identically.
- Code is AI-generated, art by hand. Licensed under CC0-1.0.
