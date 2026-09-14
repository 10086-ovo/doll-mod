# Doll Mod 1.0.1 — Fabric

Minor update. Keeps the Fabric edition in lockstep with the Forge edition (1.0.0-r15) — both loaders now ship identical resources.

## Changes

- **Fishing treasure fixed.** Dolls could never obtain treasure items (enchanted books, name tags, saddles, nautilus shells, enchanted bows and fishing rods). The vanilla fishing loot table gates its treasure entry behind an "open water" check, and that check could never pass for a doll — dolls must stand on the shore to fish, while the check requires the surface around the bobber to be water in every direction. The condition silently removed the treasure entry from the roll, so Luck of the Sea had nothing to act on. Dolls now treat any water they are able to fish in as open water, and Luck of the Sea behaves as expected. Applies to every doll variant, not just the Sea Doll.
- **The Guide Book now scrolls long pages.** Body text that overflows the page area is clipped to the visible region, with a scrollbar and mouse-wheel support, instead of drawing past the bottom border and covering the page-turn buttons.
- **Fixed the Guide Book entry-list height.** The visible row count used a fixed budget, which could push the last row (and the scrollbar) outside the content area when a category description wrapped to three lines — most noticeable in English.
- **Updated the Guide Doll head textures** (block & item) from 8×8 to the new 16×16 art — clearer icons and block rendering, matching the Forge edition.
- Removed the obsolete `warden_doll_head` model files, which referenced textures that no longer exist.

## Notes

- Code is AI-generated, art by hand. Licensed under CC0-1.0.
