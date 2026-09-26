# Mob Amputation

This is a direct modern port of [iChun's original Mob Amputation](https://github.com/iChun/Mob-Amputation), made with iChun's express permission. The port's first rule is behavioral fidelity: optimizations are acceptable only when they do not change functionality, timing, motion, appearance, targeting, or feel.

iChun is the original author. AvacadoWizard120 maintains this adaptation.

Source code and issue tracking are public at [AvacadoWizard120/LimbBGone](https://github.com/AvacadoWizard120/LimbBGone). This repository is a fork of the upstream project, preserving the original history and license relationship.

## Current scope

The first implementation slice targets Minecraft 1.21.1 on Fabric, Quilt, Forge, and NeoForge. It remains an alpha development slice while client visuals and multiplayer behavior are tested in game. See [implementation status](docs/IMPLEMENTATION_STATUS.md) for completed work and remaining verification.

This project will not publish for a Minecraft version supported by iChun's original releases. Users on those versions should use iChun's page. The exact exclusion list and the admission rule for future targets are in [support matrix](docs/SUPPORT_MATRIX.md).

## Fidelity baseline

The baseline is the latest upstream 1.12.2 source, commit [`fd5c0f8`](https://github.com/iChun/Mob-Amputation/commit/fd5c0f85390f379dd5baec3ca610f294f63d37a2). The 1.21.1 implementation keeps the original architecture and behavior where modern Minecraft permits it:

- Original Mob Amputation has exactly three live-amputation parts: head, left arm, and right arm. It never supported live leg amputation; remembered leg pieces came from the separate Mob Dismemberment mod after death.
- Each original eligible client-side mob receives three real, local gib proxy entities: head, left arm, and right arm. The built-in player profile uses those same three parts, while the built-in Creeper extension creates only a head.
- Attached proxies provide the original independently targetable limb hitboxes and shrink the parent's client hitbox to the original `0.4 x 1.5` torso.
- Direct limb attacks forward through the original parent-attack path. Projectile and fishing-hook interception remain client-side.
- Detachment retains the attached limb's real orientation and uses the original melee, projectile, and fishing launch/spin formulas.
- Detached parts use world-entity movement, collision, pushing, independent rendering and lighting, original model pivots/UVs, ground settling, fade, and lifetime rules.
- Blood uses the original particle color, sprite family, velocity, gravity, drag, collision, scale, and lifetime behavior.
- The original projectile-list grammar and odd fallback cases are retained. Melee tool chances are now explicitly configurable: the defaults keep sword/axe/pickaxe/shovel at `50/50/33/25%` and change the original any-other-item `100%` fall-through to `0%`; setting Other Held Item back to `100%` restores that quirk. The guided config screen adds lossless projectile-list editing and an additive `[namespace:projectile]: chance` form for exact modern registry-ID rules; its Advanced Raw view keeps hand-authored legacy strings available.
- The original thirteen options and the port's clearly separated extensions are available through the loader's in-game mod configuration screen on Forge and NeoForge, and through the standard optional Mod Menu integration on Fabric and Quilt. The same values remain editable in `config/mobamputation.properties` without a menu mod.
- A modded server receives the same optional limb event used for headless death, normal-skeleton bow-arm behavior, and other clients' melee visuals. A client-only installation still works on an unmodded server.
- Ordinary mob state remains runtime-only. Player trauma is authoritative and saved to player NBT: missing parts, active arm wounds, and a fatal decapitation countdown survive reconnects and dimension changes, then clear on death/respawn.

The detailed, line-item contract is in [upstream behavior](docs/UPSTREAM_BEHAVIOR.md).

## Configurable port extensions

- Other players may lose either arm as well as the upstream head. A missing arm hides the matching player model/sleeve, drops the held stack, and prevents that hand from being used while the arm is absent.
- Player arm wounds cause finite real damage and the Bleeding effect. By default one wound costs five hearts across ten seconds; a craftable Bandage immediately closes all arm wounds without restoring a limb. Player decapitation is inevitably fatal when enabled, and its first-person camera remains attached to the launched head until death unless the player changes perspective.
- An Awkward Potion brewed with a Golden Apple becomes a Potion of Limb Regrowth. Drinking it—or using its splash or lingering forms—restores the head and both arms, closes every wound, cancels fatal decapitation, and releases the detached-head camera.
- Armor and toughness reduce player amputation chance. Reinforcement I–III adds configurable protection on the covering helmet or chestplate, while Anatomical Integrity I can make its covered part immune. These protective enchantments and all reduction rules are configurable.
- Beheading, Dismemberment, and Severance I–III add head-, arm-, or general-purpose melee chance bonuses to supported tools.
- Living Creepers may be beheaded and have a dedicated thick, true-green blood profile.
- Dynamic blood surfaces are enabled by default: settled blood falls if its supporting block is broken and can drip down walls or release from ceilings. The client option can be disabled completely, and its check interval is configurable.
- Optional [Mob Dismemberment](https://github.com/iChun/Mob-Dismemberment)-style death gibs are separately configurable for Zombies, Skeletons, and Creepers. The default `Triggered` mode applies to configured explosion and iron-golem kills; `All` applies to every supported death, and `Disabled` turns the subsystem off. Its lifetime, grounded lifetime, pushing, blood, blood count, and green-blood settings are independent of live amputations. A part already severed while the mob was alive is not duplicated when the corpse splits. Legs and Creeper feet exist only as these death pieces, not as live-amputation targets.

Custom mob registration is postponed. The current release does not provide a supported config, data-pack, or add-on route for adding entity types; see [custom mob support](docs/ADDON_API.md).

## Project architecture

- `src/main/java` and `src/main/resources` contain shared implementation and Stonecutter templates.
- `versions/<minecraft>-<loader>/gradle.properties` pins each Minecraft/loader toolchain.
- `build.<loader>.gradle.kts` and `build.modernforge.gradle` contain loader-specific build adapters.
- Stonecutter generates Fabric, Quilt, Forge, and NeoForge sources without four drifting code copies.
- `MobAmputation` is the Fabric/Quilt initializer; `MobAmputationMod` is the Forge/NeoForge bootstrap.

The implementation deliberately keeps the original client/server split. Loader adapters handle lifecycle and optional networking; they do not redefine amputation behavior.

The [configuration guide](docs/CONFIGURATION.md) explains player trauma, armor protection, Creeper behavior, death dismemberment, the guided projectile-rule list, exact modded registry-ID matching, exclusions, Advanced Raw mode, and multiplayer setting ownership.

## Development

JDK 21 is required for Minecraft 1.21.1. The checked-in Gradle wrapper and project layout follow the Omnidirectional Movement multi-version setup.

```powershell
.\gradlew.bat :1.21.1-fabric:build
.\gradlew.bat :1.21.1-quilt:build
.\gradlew.bat :1.21.1-forge:build
.\gradlew.bat :1.21.1-neoforge:build
```

`buildAndCollect` on an individual node copies its distributable jar into the versioned root `build/libs` directory. Publishing credentials and service upload configuration are not stored in this repository.

The GitHub repository is the current project homepage and public source location; report bugs through its [issue tracker](https://github.com/AvacadoWizard120/LimbBGone/issues). CurseForge and Modrinth project IDs remain unset until those pages exist. No development artifact should be uploaded as a public release before the client and multiplayer parity checklist passes.

## License and attribution

This adaptation remains licensed under `LGPL-3.0-only`, matching the conservatively versioned LGPL terms of the upstream work. `COPYING` and `COPYING.LESSER` are retained from upstream and packaged with every loader artifact. See `NOTICE` for attribution, source locations, and the modification notice.

Original work: iChun. Port/adaptation: AvacadoWizard120.
