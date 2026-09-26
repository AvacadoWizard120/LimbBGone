# Custom mob support

Custom mob registration is postponed. This release does not provide a supported config, data-pack format, or add-on API for adding entity types.

## Current support

- Live amputation has built-in profiles for Zombies, Skeletons, players, and Creeper heads.
- Death splitting has fixed render support for `minecraft:zombie`, `minecraft:skeleton`, and `minecraft:creeper`.
- The config controls when those supported corpses split and how their parts behave. It does not contain an entity list.
- An old `deathDismembermentEntities` property is ignored and removed when the config is rewritten.

The profile classes currently in the source tree are implementation details used by the built-in entities. Their presence is not a compatibility promise for third-party entity registration.

## Later work

Faithful support for Villagers, Cows, Sheep, Chickens, and modded entities needs more than a registry ID. It needs per-part geometry and hitboxes, correct textures and render layers, living-model hide rules, networking, and death-gib renderers. That work will be designed as one coherent extension system in a later release.
