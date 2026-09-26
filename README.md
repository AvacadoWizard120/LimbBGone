# Mob Amputation

Mob Amputation lets you chop the heads and arms off zombies, skeletons, Creepers, and other players. The limbs do not just disappear. They fly off, hit things, slide around, and leave blood behind.

This is my port of [iChun's original Mob Amputation](https://github.com/iChun/Mob-Amputation) for Minecraft 1.21.1. iChun gave me permission to port and adapt it.

## Features

- Zombies, skeletons, and players can lose their heads and arms. Creepers can lose their heads too.
- Blood collides with the world and other entities. It can stick, drip, and fall when its support is broken.
- Players bleed after losing an arm. Bandages stop the bleeding, and a Potion of Limb Regrowth restores every missing part.
- Decapitated players can see through their flying head.
- Armor, enchantments, weapon chances, blood behavior, and player amputation can all be changed in the config.
- Optional death dismemberment can split zombies, skeletons, and Creepers into the parts they still have left.

## Versions

The current port is for Minecraft 1.21.1 on Fabric, Quilt, Forge, and NeoForge.

Install it on both the client and server for player amputation and multiplayer syncing. Client-only installs still work for normal mob amputations.

## Config

Open the mod settings screen in game. Fabric and Quilt use Mod Menu. Forge and NeoForge use their normal Mods screen.

The config file is at `config/mobamputation.properties`. More detail is in the [config guide](docs/CONFIGURATION.md).

Found a bug? [Report it here](https://github.com/AvacadoWizard120/LimbBGone/issues).

## Credits and license

iChun made the original mod. AvacadoWizard120 maintains this port.

The project is licensed under LGPL-3.0-only. See [NOTICE](NOTICE) for the full attribution and modification notice.
