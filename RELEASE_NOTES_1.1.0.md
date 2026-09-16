# Doll Mod 1.1.0 — Fabric

Update targeting Minecraft 26.3. The mod version moves from 1.0.1 to 1.1.0; dependencies move to Fabric Loader 0.19.5+ and Fabric API 0.160.5+26.3.

## Changes

- **Minecraft 26.3 support.** `fabric.mod.json` now depends on `minecraft: ~26.3` and `fabricloader: >=0.19.5`. Fabric API moved from 0.157.0+26.2 to 0.160.5+26.3.
- **Fixed mouse clicks in custom screens.** Minecraft 26.3 replaced GLFW with SDL3 for input, which renumbered mouse buttons (left 0 → 1, right 1 → 3) while the event class and method signatures stayed identical, so signature-level checks and compilation could not detect the change. Hardcoded `button == 0` left-click checks therefore never matched, and clicks were ignored in the doll control panel, the guide book, the guide search screen and the doll inventory. All checks now use `InputConstants.MOUSE_BUTTON_LEFT` (6 occurrences across 4 screens).
- **Search catalog covers the 26.3 additions.** Minecraft 26.3 adds 18 `abandoned_camp_*` structures (one shared `abandoned_camp` structure set, mutually exclusive biome tags per member) and one biome, `dappled_forest`. The guide doll search lists targets from the structure/biome registries by sorted registry key, so the new entries appear automatically; Chinese and English display names were added for all 19 (structure table 29 → 47, biome table 66 → 67). Because the camp biome tags do not overlap, the new structures use the normal pre-index path.
- **GeoIndex on-disk format version 4 → 5.** Structure and biome target indices are positions in the registry-key-sorted list and are used as on-disk bucket keys. New entries sorting before existing ones shift all subsequent indices, so index files written under 26.2 would map buckets to the wrong targets, and the built flag would skip rebuilding. The version bump invalidates old index files; existing worlds rebuild the index once on first launch (measured 32–129 s per dimension), new worlds are unaffected.
- **Removed two unused enums.** `StructureSearchType` and `BiomeSearchType` had no references outside their own files after the search rework, and one referenced a nonexistent structure id (`jungle_temple`; the vanilla id is `jungle_pyramid`).
- **Other 26.3 migration changes.** Removed codec overrides from 9 block classes (the only compile-breaking API change in 26.3), adapted 4 renderer classes and the thorns shield special renderer, replaced the removed climate sampler overload with `createClimateSampler(SamplerContext.EMPTY_UNCACHED)`, regenerated recipe and advancement JSON, and removed the swing animation hook that no longer applies.

## Notes

- Existing worlds: the GeoIndex rebuild on first launch after updating is expected and reported in the log (`GeoIndex 忽略陈旧索引 ... 将重建`).
- Code is AI-generated, art by hand. Licensed under CC0-1.0.
