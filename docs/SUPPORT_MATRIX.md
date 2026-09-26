# Support matrix

Support claims are exact Minecraft-version and mod-loader pairs. A build existing in the repository does not imply release readiness.

The port's public source and issue tracker are at [AvacadoWizard120/LimbBGone](https://github.com/AvacadoWizard120/LimbBGone). Upstream source and downloads remain linked below for attribution and excluded Minecraft versions.

## Current port slice

Minecraft 1.21.1 is the only current slice, and every artifact is **alpha**:

| Minecraft | Fabric | Quilt | Forge | NeoForge |
|---|---:|---:|---:|---:|
| 1.21.1 | Development | Development | Development | Development |

All four 1.21.1 nodes compile and their dedicated-server development runs reach startup. This is a development milestone, not a public support claim: client rendering, real packaged-jar installs, and multiplayer gameplay still need verification. No other Minecraft version or loader pair is currently claimed, supported, or release-ready. In particular, 1.21, 1.21.2, and later releases are not implied by the 1.21.1 row.

## Intentional upstream exclusions

To keep users on iChun's official page wherever an original release exists, this port will not publish for any loader on these Minecraft versions:

`1.2.5, 1.3.2, 1.4.2, 1.4.4, 1.4.5, 1.4.6, 1.5.1, 1.5.2, 1.6.2, 1.6.4, 1.7.2, 1.7.4, 1.7.10, 1.8, 1.10.2, 1.12, 1.12.1, 1.12.2`

The exclusion applies by Minecraft version across Fabric, Quilt, Forge, and NeoForge, even where the historical artifact itself used only ModLoader or Forge.

Evidence and download locations:

- [Official CurseForge file history](https://www.curseforge.com/minecraft/mc-mods/mob-amputation/files/all)
- [Official Mob Amputation feature/download page](https://ichun.me/mods/mob-amputation/)
- [Upstream GitHub source](https://github.com/iChun/Mob-Amputation)

Notes on historical metadata:

- Releases through 1.5.2 were ModLoader builds.
- Releases from 1.6.2 onward used Forge and iChunUtil.
- The 3.x artifact is associated with 1.7/1.7.2 but is also tagged 1.7.4 on CurseForge. The conservative policy excludes both 1.7.2 and 1.7.4.
- The 1.12.2 artifact is tagged compatible with 1.12, 1.12.1, and 1.12.2, so all three are excluded.

## Future target admission rule

A new Minecraft-version/loader pair may be added only when all of the following are true:

1. The exact Minecraft version is absent from the exclusion set above after rechecking iChun's official CurseForge files.
2. The target is represented explicitly in the build; compatibility is never inferred from a neighboring patch release.
3. Its loader adapter preserves client-local proxy attacks, optional networking, rendering, lifecycle cleanup, session configuration/ownership, server consequences, persisted player trauma, and dedicated-server-safe initialization.
4. The artifact builds reproducibly and passes the common behavior tests plus a loader-specific smoke test. Multiplayer admission requires a dedicated server and at least two clients to verify melee broadcasts, client-local projectiles/fishing, ordinary-mob state cleanup, player-trauma reconnect/dimension persistence, death/respawn clearing, fatal decapitation, and dimension scoping.
5. Publication metadata names this as a permitted port, credits and links iChun's original, carries the required license notices/source link, and directs excluded-version users to the original downloads.

Loader pairs are admitted independently. A future version does not need to wait for every loader, but only verified pairs may appear as supported; incomplete pairs remain unlisted rather than being marked compatible.

Every newly admitted pair starts at alpha. Promotion requires clean multiplayer regression results and parity checks for melee, projectiles, fishing hooks, model/armor visibility, AI changes, blood/gib behavior, player arm/head trauma and protection, Creeper rendering, death-dismemberment duplicate suppression, unload cleanup, and representative scaling/animation compatibility.

If iChun later publishes an original artifact for a version this port already targets, new uploads for that version are frozen and users are redirected to the upstream release. Existing files are retained only as needed for reproducibility and are marked superseded rather than silently deleted.
