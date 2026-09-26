# Implementation status

This is the engineering status of the first Minecraft 1.21.1 slice. A compiling artifact is not automatically a supported public release.

## Faithful behavior implemented

- Fabric, Quilt, Forge, and NeoForge project nodes, metadata, and bootstraps.
- The original three live parts—head, left arm, and right arm—implemented as real client-local entity proxies with the upstream parent-to-gibs ownership map. Original Mob Amputation has no live leg amputation; legs belong only to the optional death-dismemberment extension.
- Literal upstream mob target family: adult zombie subclasses except zombie villagers and the normal skeleton type only. Original other-player head behavior remains available; separately configurable player arms, a camera-only local head proxy, and a Creeper head are port extensions.
- Attached head/arm transforms, independent target boxes, original parent hitbox mutation, and direct proxy hit interception.
- Original melee forwarding, hurt-time lockout, ten-tick limb timeout, recognized-tool defaults, and delayed mob detachment. The old 100% unknown-item fall-through is exposed as a configurable Other Held Item percentage and defaults to 0% by user request.
- Original projectile-list parsing and legacy names/class-name behavior, including the upstream class-matching quirk and arrow default, plus an additive bracketed registry-ID rule for reliable modern/modded projectile matching.
- Original fishing-hook tracking, random limb assignment, death-time roll, and launch toward the angler.
- Cause-specific launch and spin, inherited attached rotations, vanilla entity movement/collision, per-tick entity pushing, settling, fading, and lifetime rules.
- Independent gib rendering with the parent renderer's live texture, gib-position lighting, no frustum rejection, no culling, and original model boxes, pivots, offsets, and non-mirrored arm UVs.
- Base-model and armor-part hiding plus the upstream client-side main/offhand clearing behavior.
- Custom blood particles matching the upstream color, scale, splash sprites, velocity, gravity, drag, collision, and lifetime; initial, wound-splurt, and projectile-impact bursts use the upstream conditions and counts.
- Optional versioned mod-presence/session handshake carrying the five upstream session settings plus every server-owned gameplay extension. Server values are read-only for the connection while client visual settings remain editable without contaminating the saved local gameplay configuration.
- Original melee-only same-dimension detach broadcast; projectile and fishing visuals remain local except for a target-player camera notice. A modded server matches each candidate to recent real melee/projectile/fishing evidence, performs the single configured chance roll, rate-limits requests, and cannot replay a detached limb. Client-only play retains the immediate original local roll.
- In-memory 40–99 tick non-player headless-death scheduling and normal-skeleton right-arm melee fallback. Ordinary mob amputation state remains runtime-only.
- Existing property files migrate to the current settings without discarding user values. Boolean `0`/`1` values and the projectile-list syntax remain accepted.
- A four-category in-game screen registered from the Forge and NeoForge mod lists and from the optional Fabric/Quilt Mod Menu entrypoint. Focused advanced pages keep the overview uncluttered; every control has a concise hover tooltip. Guided projectile and melee-item lists support add/edit/remove/reorder plus raw import/export for advanced rules.
- Ordered melee rules accept exact `namespace:item` IDs, `#namespace:tag` item tags, and a final catch-all. Defaults use the vanilla sword, axe, pickaxe, and shovel tags, so correctly tagged modded tools work automatically. Data-driven Beheading, Dismemberment, and Severance enchantments remain supported. Accepted player melee uses server-owned exact-limb proof with a bounded transform history, avoiding the full player hitbox occluding its client-local head and arms.

## Port extensions implemented

- Authoritative player head and arm state. A severed arm hides the correct wide/slim model arm and sleeve, drops the held stack, prevents reuse of that hand, causes finite configurable real damage, and can be treated immediately with the craftable Bandage.
- Fatal player decapitation with independent pulse/countdown settings and an unavoidable final damage type. The optional first-person camera rides the actual launched head until death unless the player changes perspective.
- Player trauma stored in player NBT and copied across non-death player reconstruction, so reconnecting or changing dimensions cannot cancel bleeding. State clears on death/respawn and is synchronized to current and newly tracking modded clients.
- Player armor/toughness chance reduction captured at the actual hit, with configurable ordinary caps, Reinforcement I–III, and Anatomical Integrity I immunity on the covering helmet/chestplate.
- Living Creeper beheading with a dedicated head proxy/model-hide path and configurable thick true-green blood, including powered-Creeper render-layer handling.
- Optional Mob Dismemberment-style corpse splitting for Zombie, normal Skeleton, and Creeper model families. Modes are Disabled, Triggered, and All; default Triggered mode enables explosion and iron-golem kills. Death-only lifetime, grounded lifetime, pushing, zombie blood, blood count, and green-blood settings are independent of live-gib settings.
- Death splitting retains already-flying live gibs and skips their matching corpse head/arm, preventing duplicate pieces. Humanoid legs and Creeper feet exist only in this death-only subsystem.
- Internal `GibProfile` and `BloodProperties` types support the built-in entities. Third-party entity registration is postponed; see [custom mob support](ADDON_API.md).

## Verification still required before a public alpha

- Repeat real-client smoke tests on all four loaders after the extension pass, then inspect attached hitboxes, direct selection, all three original sever poses, both arm UVs, model/armor/item hiding, lighting, blood, collision, settling, and fade.
- Exercise melee, arrows, other projectiles, fishing hooks, fatal hits, moving parents, walls, water, and paused-game lifetime behavior.
- Run player/PvP regressions for wide and slim arms/sleeves, missing-hand enforcement, one- and two-arm bleeding, low-health bleed-out attribution, Bandage use, fatal head countdown/camera behavior, relog, dimension transfer, death, and respawn.
- Verify armor snapshots and chance math with partial/full armor, toughness, Reinforcement levels, Anatomical Integrity, modded tagged armor, projectiles, and equipment swaps on the hit tick.
- Exercise Creeper beheading with normal and powered render layers and both Creeper blood modes.
- Exercise Disabled, Triggered, and All death-dismemberment modes; explosion and iron-golem attribution; fixed supported-entity gating; normal/explosive physics; independent settings; corpse suppression; and no duplicate already-severed head/arms.
- Run two-client tests against a modded dedicated server for melee broadcast, evidence admission, session ownership, late-tracker state, player trauma synchronization, headless attribution, and the target-player camera.
- Verify a client-only installation against an unmodded server and a server-only installation with an unmodded client.
- Install each packaged jar in a clean launcher profile instead of relying only on development runs.
- Add regression coverage for exact chance boundaries, legacy projectile configuration, packet structure, ordinary-mob state cleanup, persisted player-trauma reconnect behavior, and death-gib duplicate suppression.
- Test representative shader, animation, model-replacement, reach, scaling, and performance mods without changing parity behavior to accommodate them.

## Verification completed for this milestone

- A clean JDK 21 build succeeds for all four 1.21.1 nodes, and the four collected binary jars match their node artifacts byte-for-byte.
- Fabric, Quilt, Forge, and NeoForge dedicated development servers each reached `Done`; Fabric, Quilt, and Forge accepted a clean `stop`, while NeoForge's development console required terminating the already-verified test process.
- All four development clients reached resource reload/title-screen startup without a Mob Amputation error.
- A Fabric single-player regression severed a zombie head through the attached proxy hitbox with blood enabled and no crash or invalid-rotation log storm.
- The four-category config overview, Combat, Players, Advanced Players, Effects, Death, and Tool Chances pages were visually checked at the default 240-pixel logical GUI height. Tool-list insertion, validation, scrolling, navigation, tooltips, cancel behavior, and exact/tag/fallback rows were exercised in a live Fabric client.

The checks above establish the earlier parity baseline. They do not replace the extension-specific regressions listed in the preceding section.

## Release posture

The `0.1.0-alpha.1` version is an internal development milestone. Generated jars must not be uploaded to CurseForge or Modrinth until the remaining client and multiplayer checks above are complete.
