# Upstream behavior contract

This port adapts iChun's Mob Amputation with permission. Its parity baseline is the latest upstream source, commit [`fd5c0f8`](https://github.com/iChun/Mob-Amputation/commit/fd5c0f85390f379dd5baec3ca610f294f63d37a2), released as Mob Amputation 7.0.1 for Minecraft 1.12.2.

Primary references:

- [Upstream source repository](https://github.com/iChun/Mob-Amputation)
- [Upstream 7.0.1 source snapshot](https://github.com/iChun/Mob-Amputation/tree/fd5c0f85390f379dd5baec3ca610f294f63d37a2)
- [Official feature page](https://ichun.me/mods/mob-amputation/)
- [Official CurseForge files](https://www.curseforge.com/minecraft/mc-mods/mob-amputation/files/all)

The default rule is literal behavior preservation. A modern implementation detail may differ only when its observable result does not: hit regions, selection, timing, random chances, pose, motion, collision, render geometry, texture, particles, lifetime, networking, and server consequences are part of the contract.

Historical scope is important: Mob Amputation defines only `HEAD`, `LEFT_ARM`, and `RIGHT_ARM` as live targets. It never had live leg amputation. Legs breaking off after death were behavior from iChun's separate [Mob Dismemberment](https://github.com/iChun/Mob-Dismemberment), which could run alongside Mob Amputation.

## Client proxy lifecycle and targets

- Every eligible adult gets three real client-world gib proxies stored in a strong parent-to-array map.
- Eligible targets are zombie instances except zombie villagers, the standard skeleton class/type, and—when configured—other players. The local player and children are excluded.
- The parent becomes a `0.4 x 1.5` client-side torso. The proxies remain independently pickable, collidable, and pushable.
- The upstream proxies are not registered server entities and require no persistent state. They are recreated attached after reconnect/reload and work when only the client has the mod. This statement describes original/ordinary proxy state; this port's separately configured player trauma is authoritative and persistent.
- A dead parent or child conversion removes all three proxies and the map entry.

## Attached poses and direct hits

- The head follows the parent's head yaw/pitch. Arms use the parent's body orientation, sit exactly `0.350` blocks to either side, and start at `-90` degrees pitch.
- A player attacks the proxy itself. The proxy forwards the vanilla attack to its parent, applies the original hurt-time/reentrancy/ten-tick guards, rolls that limb's sever chance, and performs the same second local parent-hurt call.
- Upstream other players expose only the head. The port's optional player-arm extension is outside this contract. A normal skeleton's right arm cannot be cut when the server lacks the mod.
- Upstream tool mode is documented exactly: empty hand never; sword/axe 50%; pickaxe about 33.3%; shovel 25%; every other nonempty item succeeds. The port's separately documented per-tool extension changes only the configured defaults requested by the user. Global-chance mode uses `gibChance`.

## Detachment and physics

- Ordinary mob melee waits until the early hurt animation passes; players, projectiles, and dead fishing hooks detach immediately.
- Left-arm loss clears the client offhand and right-arm loss clears the client main hand.
- Base launch is parent velocity times `1.05`, plus only a random upward component. Fishing launches toward the angler; projectiles copy the captured projectile velocity times `0.8`.
- Melee spin uses two signed values in `[20, 75)`, scaled by the original parent-motion formulas. Projectile/fishing/remote detachment retain the original `15` degree pitch/yaw spin. Rotation begins at the attached pose, never at zero.
- Movement calls the world's normal entity collision resolver before applying the upstream gravity and drag. Ground pitch convergence, ground damping, airborne spin decay, and projectile collision stopping are preserved.
- Detached proxies use the original every-tick bilateral entity-pushing query. Ground and total lifetime use the same thresholds, strict comparisons, 20-tick fade, and global client clock.

## Rendering and blood

- A gib renders independently of its parent with no frustum rejection, no back-face culling, its own position/light, and the parent renderer's current texture.
- Head and arm boxes, texture dimensions, pivots, vertical offsets, rotation axes, and scale match `ModelGib`/`RenderGib`. Both arms deliberately use the same non-mirrored UV layout.
- Detached parts contain no hat, armor, or held-item geometry. Corresponding parent base/armor parts are hidden around rendering and restored afterward.
- Blood uses a custom particle with the upstream red/yellow-green rule, scale, initial velocity randomization, gravity, drag, collision, lifetime, and legacy particle sprites 19-22 (the modern splash sprite set).
- Initial sever blood, 10% per-gib wound splurts, and projectile-impact blood retain the original eligibility, alive checks, positions, direction, parent-motion addition, and counts.

## Projectiles, fishing, and configuration

- Projectile configuration is reparsed on each gib hit with the original comma/colon grammar: `Arrow: 100, Snowball: 50, class.name: 75`.
- The port additionally accepts `[namespace:projectile]: 75` for an exact modern entity-type ID. Brackets disambiguate the namespace colon without changing how any original-format entry is parsed. A chance of `-1` is the guided editor's guaranteed exclusion form.
- The default Arrow entry and the original first-loadable-class matching bug are retained. Damaging projectiles can fall back to the global chance when projectile gibbing is enabled.
- Fishing hooks are tracked from spawn. Once a hook catches any entity it stops being tracked; an eligible parent assigns the hook to one uniformly random proxy. The roll occurs when the hook dies.
- Defaults and ranges match upstream: `gibTime=1000`, `gibGroundTime=100`, blood on, `bloodCount=20` (1-1000), wound splurts on, green blood off, player gibs on, gib pushing on, headless death on, tool effect on, `gibChance=100`, empty projectile list, projectile-damage gibbing on.
- Upstream marks only `headlessDeath`, `toolEffect`, `gibChance`, `projectileList`, and `allowProjectileGibbing` as session settings. The port additionally syncs its per-tool chances, weapon-enchantment toggle, Player Gibs, player-trauma settings, armor-protection settings, and Creeper-amputation settings. Live-gib visuals/physics, the camera, and death-dismemberment settings stay client-owned.

## Optional server behavior

- A client with the mod continues to provide local amputations on an unmodded server.
- Upstream sends `(parent entity ID, limb type, non-player cause)`. The port's versioned request additionally identifies melee/projectile/fishing and the source entity so a server can match it to real interaction evidence, make the one configured roll, and return an explicit accept/reject result.
- Melee events are broadcast to modded clients in the dimension; projectile and fishing events are not. A projectile/fishing decapitation is sent only to its player victim when needed for the optional camera. Server-originated notices are not echoed back.
- A severed non-player head schedules 200 generic/player-attributed damage after 40-99 server ticks when enabled.
- Cutting the normal skeleton's right arm removes its bow goal and adds a new priority-4 melee goal. Upstream trusted and repeated every packet; the port accepts each server-observed interaction/limb only once so a modified client cannot replay headless death or AI changes.
- Ordinary-mob limb and headless countdown state are in-memory only and are not written to entity NBT. Player missing-part state, arm wound timers, fatal head countdown, pulse timers, and responsible attacker are a port extension written to player NBT; they survive reconnect/dimension transition and clear on death/respawn.

## Allowed modernization boundary

Loader APIs, packet codecs, namespaced identifiers, mappings, Java syntax, and internal lookup mechanisms may be modernized. Server packets are matched to short-lived authoritative melee/projectile/fishing evidence, rate-limited, consumed once, and rolled once using the server's active configuration. Client-only use keeps the immediate local roll. This hardening must not change legitimate upstream hit reach or per-client projectile/fishing visuals.

Performance work comes after parity and must be observationally neutral. No optimization may replace the three proxies, approximate their hitboxes, collapse their tick/render paths into the parent, cap configured effects below upstream limits, or alter timing/randomness/physics.

## User-requested port extensions

These behaviors are intentionally outside the 1.12.2 parity contract and are separately configurable:

- The upstream tool-effect fall-through gives every unrecognized nonempty held item a 100% sever chance. The port exposes each tool family as a percentage and defaults Other Held Item to 0%; setting it to 100% restores upstream behavior.
- Beheading, Dismemberment, and Severance add head-, arm-, or general-purpose melee chance bonuses to tagged tools.
- Other players can expose the left and right arm as real sever targets. A missing arm hides the corresponding wide/slim model arm and sleeve, drops the held stack, prevents that hand from remaining equipped, and persists as player trauma until death/respawn.
- An arm wound applies finite configurable real bleeding damage and a visible Bleeding effect. A Bandage closes every active arm wound immediately but does not restore a limb or cure decapitation.
- Player decapitation can be inevitably fatal after a configurable countdown and damage pulses. A local-only first-person camera may follow the player's own launched head until death; changing perspective opts out. The camera does not alter the gib's launch or server result.
- Player armor value, armor toughness, and the helmet/chestplate covering the targeted part can reduce amputation chance. Reinforcement I–III adds configurable reduction and Anatomical Integrity I can make its covered part immune.
- Adult Creepers can expose a live head target with configurable true-green blood and dedicated physical properties.
- An optional Mob Dismemberment-style death subsystem splits allowed Zombie, normal Skeleton, and Creeper corpses. It defaults to Triggered mode for configured explosions and iron-golem kills, while Disabled and All modes are also available. Its lifetime, grounded lifetime, pushing, zombie blood/count, and green-blood options are independent from the live-amputation settings.
- Death dismemberment checks the live-amputation state first: an already-detached head or arm remains in flight and is not created a second time from the corpse. Humanoid legs and Creeper feet are death-only pieces, not live targets.

## Add-on boundary

Internal `BloodProperties`, `GibProfile`, and `GibProfileRegistry` types support the built-in humanoid and Creeper paths. They are not a supported third-party registration API in this release.

There is not yet an arbitrary piece list, custom geometry/texture contract, general source-model hiding hook, synchronized data-pack definition, or a death-gib renderer registry. Custom mob work is postponed until those pieces can be designed together; see [custom mob support](ADDON_API.md).
