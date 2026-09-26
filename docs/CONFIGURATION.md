# Configuration guide

Open **Mob Amputation Settings** from your loader's mod list. The Overview is split into four categories: **Effects**, **Combat**, **Players**, and **Death**. Each category keeps its common controls on one page and links to a smaller advanced page when needed. Hover a control for a short explanation. Minecraft runs at 20 ticks per second.

The same values are stored in `config/mobamputation.properties`. The in-game lists are the recommended way to edit item and projectile rules.

## Effects

Effects contains local presentation and physics settings:

- Detached Part Lifetime controls the maximum time a severed part can exist.
- Grounded Part Lifetime controls how long a part can rest before its one-second fade.
- Blood toggles sever blood, while Blood Count controls each burst's particle count.
- Wound Blood Spray makes open wounds occasionally spray blood.
- Green Mob Blood changes the blood color used by non-player mobs.
- Parts Push Entities lets detached parts collide with nearby entities.

### Advanced Effects

Dynamic Blood Surfaces lets stopped blood react when nearby blocks change. If a supporting block is removed, the particle falls and can land elsewhere. Checks are staggered between particles and run every eight ticks by default; the feature does not scan the area around the player or maintain a global blood list.

Surface Dripping lets blood on a wall creep downward and lets blood under a ceiling drop. Turning it off still allows unsupported blood to fall after a block breaks. A custom blood type's `drippiness` controls its wall and ceiling behavior.

Blood Collides with Entities stops moving droplets at the collision boxes of mobs, players, dropped items, vehicles, and other solid entities. A blood profile's stickiness is also its chance to cling on impact: normal blood sticks about 30% of the time, while thicker Creeper blood sticks about 62% of the time. Attached drops follow the entity's movement and body rotation. With Dynamic Blood Surfaces and Surface Dripping enabled, they pause, creep downward, and eventually fall free; drippier blood moves and releases sooner, while stickier blood hangs on longer. Blood spawned inside its source is allowed to leave before that entity can block it.

The collision work is client-only. Each moving particle checks only its short swept path, each query accepts at most 16 nearby entities, and a drop that is already attached follows its entity without another spatial search. Turn Blood Collides with Entities off to restore block-only particle movement exactly. Blocks, including plants with a collision shape, continue to use normal block collision either way; short grass has no collision shape and is not treated as an invisible wall.

The backing properties are:

- `dynamicBloodSurfaces=1` — enable support checks and attached-blood movement.
- `bloodSurfaceDripping=1` — enable wall, ceiling, and entity-surface drips.
- `bloodEntityCollisions=1` — collide with nearby entities and allow some droplets to cling to them.
- `bloodSurfaceCheckIntervalTicks=8` — ticks between support checks and attached-blood movement, clamped to 2–40. Higher values update less often.

## Combat

Combat contains the settings that decide when a body part can be severed:

- Headless Death makes a headless mob die after a short delay.
- Unlisted Projectile Chance is the fallback chance for damaging projectiles without a matching rule.
- Fishing Hook Chance controls severing caused by a fishing hook.
- Unlisted Projectiles Can Sever enables the damaging-projectile fallback. Explicit exclusions still take priority.
- Amputation Enchantments enables the offensive enchantment bonuses.
- Detached-Head Camera follows your severed head in first person.

Tool Chances and Projectile Rules each open a dedicated list editor.

### Tool Chances

Each tool rule assigns a sever chance from 0% through 100% to an exact item, an item tag, or the fallback for unmatched held items.

- **Item** uses an exact registry ID in `namespace:path` form, such as `minecraft:diamond_sword` or `examplemod:claymore`.
- **Item Tag** uses a tag ID. The raw form begins with `#`, such as `#minecraft:swords`.
- **Unlisted Items** is the fallback used when no item or tag rule matches. The raw form is `*`.

Exact item rules take priority over tag rules. Within the same type, the first matching row wins. The first fallback row is used only when nothing else matches. Empty hands always have a 0% chance.

The default list is:

```text
#minecraft:swords=50
#minecraft:axes=50
#minecraft:pickaxes=33
#minecraft:shovels=25
*=0
```

These tags cover vanilla tools and any modded tools that add themselves to the matching tag. Add an exact `namespace:path` item row when a particular tool needs its own chance. The Advanced Raw editor exposes the stored `toolRules` value without deleting malformed or hand-authored rows. Existing configs with the retired fixed Sword, Axe, Pickaxe, Shovel, and Other Item fields are migrated automatically when `toolRules` is absent.

When Amputation Enchantments is enabled, three mutually exclusive level I–III enchantments add percentage points to a matching tool's base chance:

- **Beheading** adds 20 percentage points per level for heads.
- **Dismemberment** adds 20 percentage points per level for arms.
- **Severance** adds 15 percentage points per level for either target.

Enchantments support items in `#mobamputation:amputation_tools`. Armor reduces the base chance before the offensive bonus is added. Anatomical Integrity remains an absolute immunity for the body part covered by that armor piece. If commands combine incompatible enchantments, only the strongest applicable bonus is used.

### Projectile Rules

Each projectile row has a target and a chance mode:

- **Entity ID** is recommended for modern and modded projectiles. Enter `namespace:path`, such as `examplemod:bullet`.
- **Legacy Name** supports entries such as `Arrow` and `Snowball`.
- **Java Class** matches a fully qualified projectile class. Class rules can catch unrelated projectiles, so exact entity IDs are safer.
- **Raw** preserves an unusual or hand-authored token without guessing its meaning.

Chance modes are:

- **Default** — use Unlisted Projectile Chance.
- **Custom** — use a value from 0% through 100%.
- **Excluded** — never sever with this projectile. It is stored as `-1`, so it cannot sever even on an exact-zero random roll.

An exact registry-ID rule is stored in bracketed form so the namespace colon remains unambiguous:

```text
[examplemod:bullet]: 75, [examplemod:healing_dart]: -1
```

Bracketed IDs are checked as exact entity types before class rules. Existing comma-and-colon entries retain their format. When Unlisted Projectiles Can Sever is enabled, an unmatched damaging projectile uses Unlisted Projectile Chance.

The Advanced Raw screen edits the stored `projectileList` string directly. Saving an untouched list returns the same input text, and unchanged rows keep their spelling and separators. Raw mode also retains these parser details:

- Names and whitespace are case-sensitive.
- Duplicate keys and their order are retained.
- Missing or invalid chances use the default chance.
- Values outside 0–100 are accepted in Raw mode with literal roll behavior.
- A leading empty entry can disable the configured legacy list.
- A loadable Java-class rule can catch unrelated projectiles.

### Detached-head camera

When Detached-Head Camera is enabled and the local player is decapitated in first person, the camera follows the detached head's position and tumble. With Fatal Player Decapitation enabled, it follows the head until the player dies. Switching perspective exits immediately. Respawning, changing worlds, or another mod taking camera ownership also restores the normal camera safely.

The camera is client-owned. The server controls whether decapitation is fatal and controls its countdown and damage.

## Players

Player Amputation controls whether players can lose body parts. Player Arm Amputation enables the two arm targets. Live leg amputation is not supported.

A severed arm remains missing until death and respawn. The matching model arm and sleeve are hidden, the held stack is dropped, and the missing hand cannot keep an invisible item equipped. Player trauma is saved with the player, so logging out or changing dimensions does not close wounds or restore body parts.

The main Players page contains the feature toggles. Advanced Players contains the detailed bleed, bandage, and armor values.

### Bleeding and bandages

Arm Bleeding deals finite real damage. It can kill a player who is already low without making every arm wound fatal. The defaults are:

- `armBleedDurationTicks=200` — ten seconds.
- `armBleedIntervalTicks=20` — one pulse per second.
- `armBleedDamageTenths=10` — one health point, or half a heart, per wound and pulse.

One arm wound therefore removes ten health points, or five hearts, with the default values. Two wounds contribute independently. Bleeding bypasses armor, shields, effects, and enchantments and causes no impact or knockback.

A Bandage immediately closes all active arm wounds. It does not restore an arm and cannot cure decapitation. Its default cooldown is 20 ticks. The shaped recipe is `string + white wool + string` in one row and produces four Bandages. Bandages can be disabled without disabling arm amputation.

A Potion of Limb Regrowth restores the player's head and both arms immediately. It also closes every wound, cancels the fatal decapitation countdown, removes Bleeding, and returns a detached-head camera to the player. Brew one by adding a golden apple to Awkward Potions. Vanilla gunpowder and dragon's-breath brewing can turn it into splash and lingering versions for restoring other players.

Fatal Player Decapitation uses a random countdown of 40–99 ticks by default, pulses every 10 ticks for two health points, and ends in unavoidable death. The minimum, maximum, pulse interval, and pulse damage are configurable. Disabling fatal decapitation cancels an active countdown; a Bandage does not.

### Armor protection

Armor Protection snapshots the victim's equipment when the server observes the hit and applies a relative reduction to player amputation chance. With the default values:

- Every armor point contributes 1% reduction, so leggings and boots help protect both heads and arms.
- Every armor-toughness point contributes another 1%.
- The covering piece contributes an extra 5% per armor point: the helmet for a head and the chestplate for either arm.
- Armor and toughness are capped at 80% reduction.
- Reinforcement I–III on the covering piece adds 10% per level; the combined non-immunity reduction is capped at 95%.
- Anatomical Integrity I on the covering piece makes that body part's amputation chance zero.

These are relative reductions, not percentage points removed from the configured chance. For example, 50% protection changes a 50% base chance to 25%. Every coefficient, cap, armor-enchantment toggle, and Anatomical Integrity toggle is configurable. Reinforcement and Anatomical Integrity are mutually exclusive and support `#mobamputation:amputation_protective_armor`, which includes enchantable helmets and chestplates by default.

## Death

### Creeper beheading

Creeper Beheading lets living adult Creepers lose their heads and is enabled by default. Green Creeper Blood gives the wound a green, sticky, heavier blood profile; when disabled, Creepers use the normal mob blood color. Both settings are controlled by the server.

### Split bodies on death

Supported corpses can split into body parts after death. The modes are:

- **Disabled** — never split a corpse.
- **Triggered** — split a supported corpse only after an enabled explosion or iron-golem kill. This is the default mode, with both triggers enabled.
- **All** — split every supported corpse death.

Death splitting currently supports only `minecraft:zombie`, `minecraft:skeleton`, and `minecraft:creeper`. This set is fixed for now; there is no entity-list setting.

Advanced Death contains the independent presentation settings for parts created on death:

- Lifetime: 1000 ticks.
- Ground lifetime before the one-second fade: 100 ticks.
- Pushing: enabled.
- Zombie death blood: enabled, with 100 particles per normal burst. Explosions multiply the count by ten.
- Yellow-green death blood: disabled.

If a live head or arm was already detached, that part is skipped when the corpse splits. Attached proxies are retired, while parts that are already flying remain. Humanoid legs and Creeper feet are death-only parts and cannot be targeted while the entity is alive.

## Multiplayer ownership

A modded server controls gameplay and injury settings for the current connection:

- Headless Death, Unlisted Projectile Chance, Fishing Hook Chance, Unlisted Projectiles Can Sever, Tool Chances, Projectile Rules, and Amputation Enchantments.
- Player Amputation, player arm amputation, fatal decapitation, bleed timers and damage, Bandages, and the Bandage cooldown.
- Armor protection values and both protective-enchantment settings.
- Creeper Beheading and Green Creeper Blood.

These active server values are read-only on a connected client's screen. Saving local effects while connected does not overwrite server-owned values in `config/mobamputation.properties`.

The client controls detached-part lifetime and ground time, blood presentation, pushing, blood surface/entity physics, Detached-Head Camera, and the corpse-splitting mode, triggers, physics, and blood. Disconnecting removes the temporary server settings and restores the client's saved gameplay values.

## Add-on boundary

Custom mob registration is not supported in this release. The internal profile types remain implementation details for the built-in Zombies, Skeletons, players, and Creepers. See [custom mob support](ADDON_API.md) for the current status.
