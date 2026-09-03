# MiraFactions

Command-first faction warfare for **Paper 1.21.11 / Java 21**, built for the Mira plugin suite and modeled around the classic FactionsUUID power, territory and raiding loop.

## Download

Current release: **v0.2.4**

[**Download MiraFactions v0.2.4**](https://github.com/FiveSOCE/Mira-Factions/releases/download/v0.2.4/MiraFactions-0.2.4.jar)

[View all releases](https://github.com/FiveSOCE/Mira-Factions/releases)

## Requirements

- Paper 1.21.11
- Java 21
- Vault
- A Vault-compatible economy provider
- PlaceholderAPI optional
- MiraShop and MiraSpawners recommended for typed-spawner faction land value

## v0.2.4 faction wealth

### Faction value breakdown

```text
/f value
/f value <faction>
```

Faction value is now presented as a real wealth breakdown:

```text
Total Wealth:        $12,450,000
Spawner Land Value:  $11,900,000
Faction Bank:           $550,000
Placed Spawners:               42
```

The command then lists the exact typed spawners found in claimed land, including MiraSpawners stack sizes:

```text
Zombie Spawner x64 = $11,200,000
Blaze Spawner x1   =    $650,000
```

Spawner value reads the typed BUY price from MiraShop and falls back to Essentials generic spawner worth when a typed price is unavailable.

This is a live scan of the faction's currently claimed chunks. There is no spawner-age or value-maturation mechanic.

### Wealth-based FTop

```text
/f top
```

Faction Top is now ranked by:

```text
Spawner Land Value + Faction Bank = Total Wealth
```

The top 10 output shows each faction's total wealth, land/spawner value and faction bank value.

This turns placed economic assets into the main competitive faction-value metric instead of ranking factions by raw power alone.

## v0.2.3 faction utilities

### Faction list

```text
/f list
/f list <page>
```

Lists every faction using **10 factions per page**. Entries show online/total members, faction power, claimed chunks and RAIDABLE state.

### Power

Normal player power is:

```text
Start: 25
Maximum: 25
Minimum: -10
Death loss: 2
```

Operators can directly set uncapped current power with:

```text
/fa power <player> <amount>
```

For example:

```text
/fa power FiveS 100
```

Operator-set power can exceed normal limits and remains stable through normal regeneration. Death still subtracts the configured power loss.

### Larger map and auto-map

`/f map` defaults to a **17 x 17 chunk** view centered on the player.

```text
/f map auto
/f automap
```

toggles automatic map redraws whenever that player enters a new chunk.

## Faction lifecycle and membership

- Create, join, leave and disband
- Recruit / Member / Officer / Coleader / Leader ranks
- Direct role assignment, promotion and demotion
- Leadership transfer
- Expiring invitations
- Revoke and clear invites
- Open or invite-only factions
- Player bans and unbans
- Configurable member limits
- Faction descriptions, links, member titles and rename support
- Login/logout faction notifications
- Permanent factions
- Peaceful factions

## Power, claims and raiding

Faction claim capacity is based on faction power. When a faction owns more chunks than its current power supports it becomes **RAIDABLE**.

Enemy factions can overclaim vulnerable chunks. Overclaiming is blocked while the defending faction is protected by grace, a faction shield, peaceful status or sufficient power.

Claim tools include:

```text
/f claim
/f claim radius <radius>
/f auto
/f unclaim
/f unclaim all
/f map
/f map auto
/f seechunk
```

Territory protection covers block breaking/placing, buckets, containers, interactions, explosions, pistons, liquid flow and cross-border hopper transfers.

## Special territory

Admins can create SafeZone, WarZone and Wilderness territory.

## Granular faction permissions

Faction leaders can define minimum ranks for individual actions and relation-based access for Ally, Truce, Neutral and Enemy factions.

Examples:

```text
/f perms container officer
/f perms relation door ally allow
/f perms relation container ally deny
```

## Diplomacy and chat

Relations:

- Ally
- Truce
- Neutral
- Enemy

Chat channels:

```text
/f chat public
/f chat faction
/f chat ally
/f chat truce
```

Operators can monitor private channels with `/fa chatspy`.

## Homes, warps and faction economy

MiraFactions includes faction homes, multiple faction warps, Vault-backed faction banking, daily dues/rent foundations, TNT banking, faction shields, grace periods, faction flight and zones.

## Native faction vault

`/f vault` opens the persistent shared faction inventory. Capacity expands through the Vault Size upgrade.

## Faction upgrades

`/f upgrades` opens the upgrade GUI. Current upgrade families include:

- Power
- Member Limit
- Warp Limit
- Vault Size
- TNT Capacity
- Shield
- Faction Flight
- Territory Damage
- Territory Defense
- Power Regeneration
- Power Loss Reduction
- Mob Drops
- Mob XP
- Crop Yield
- Crop Growth
- Spawner Rate
- Zone Limit

## Utility commands

```text
/f info [faction]
/f list [page]
/f value [faction]
/f power [player]
/f top
/f near
/f coords
/f announce <message>
/f stuck
```

## PlaceholderAPI and public API

When PlaceholderAPI is present, MiraFactions registers `%mirafactions_*%` placeholders for core faction/player/territory information.

MiraFactions also registers `MiraFactionsApi` through Bukkit's ServicesManager for other Mira plugins.

## `/fa` operator suite

`/fa help [1-3]` displays the operator command set. Major controls include:

```text
/fa bypass
/fa chatspy
/fa reload
/fa save
/fa info <faction>
/fa disband <faction>
/fa rename <faction> <newName>
/fa forcejoin <player> <faction>
/fa forcekick <player>
/fa forcerole <player> <rank>
/fa forcehome <player> <faction>
/fa power <player> <amount>
/fa power <set|add> <player> <amount>
/fa powerboost <set|add> <faction> <amount>
/fa permanentpower <set|clear> <faction> [amount]
/fa money <set|add> <faction> <amount>
/fa tnt <set|add> <faction> <amount>
/fa upgrade <set|add> <faction> <upgrade> <level>
/fa claim <safezone|warzone|wilderness>
/fa grace <status|start <minutes>|stop>
/fa peaceful <faction>
/fa permanent <faction>
/fa rentexempt <faction>
/fa shield <clear|reset> <faction>
```

## Permissions

- `mirafactions.use` default everyone
- `mirafactions.admin` default OP
- `mirafactions.bypass` default OP

## Persistence

Runtime faction state is stored at:

```text
plugins/MiraFactions/factions.yml
```

## Building

```bash
gradle clean build
```

Output:

```text
build/libs/MiraFactions-0.2.4.jar
```
