# FAQ & Troubleshooting

### My new theme does not show up in `/at theme list`.
Datapack dimensions are only created on server start. Restart the server or reopen the world after adding a
dimension. Also check that the theme list file references the dimension id and that the log has no
"references a dimension that does not exist" error.

### The scroll does nothing / shows an error.
The scroll is never consumed on failure; the message tells the reason:
- *pool empty* — no structure is stored for that theme and tier (`/at structure list <theme>`),
- *rank too low* — raise it with `/at rank` (see [Scrolls](Scrolls#ranks)),
- *no space* — the portal needs 1 × 2 free blocks in front of the clicked face,
- *instance limit* — `maxConcurrentInstances` is reached,
- used inside an Architect's Trials dimension — not allowed.

### Saving says the structure needs a Player Spawn Marker / Exit Marker.
Every structure needs at least one of each; see [Markers](Markers).

### My chest in the editor won't open.
It has a loot table (pool loot). Opening it would roll the loot and remove the reference. Use
`/at marker info` to see it, `/at marker loot_table clear` to remove it.

### The completion bonus gives nothing.
Check the loot table path (`<ns>:architectstrials/completion/<theme>/tier_<n>`) or the override on the exit
(`/at marker info` while looking at the Exit Marker). In 26.3 loot functions must be written as
`"modifier": {…}`; the old `"functions": […]` is silently ignored.

### `/time set` does not work in my theme.
The default dimension type has a fixed time. See [Themes & Dimensions](Themes-and-Dimensions#time-of-day).

### Mobs from a Direct Spawn Marker fell into the void.
Several mobs of one marker are spread within 1.5 blocks around it. Give the marker a floor around it.

### Players kept a scroll effect after leaving.
Infinite player effects of a scroll are removed on every way out of a challenge, and the player's own effect of
the same type is given back. Finite scroll effects simply run out. If you still see a leftover effect, please
open an issue.

### Can I use `/at` in my datapack functions?
Yes, but if another mod in the pack registers `/at`, it may win. `/architectstrials` always works.

### The import says the selection is too large or too high.
The footprint may be at most 128 × 128 blocks, and the height has to fit into the editor above the placement
height (`structurePlacementY`). Select a smaller part, or lower `structurePlacementY` for very tall imports.

### Where are my saved structures?
In `<world>/datapacks/architectstrials_structures/`. Copy that folder into your modpack to ship them.
