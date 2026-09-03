# MiraFactions

Command-first faction warfare for **Paper 1.21.11 / Java 21**, built for the Mira plugin suite and modeled around the classic FactionsUUID power, territory and raiding loop.

## Download

Current release: **v0.2.0**

[**Download MiraFactions v0.2.0**](https://github.com/FiveSOCE/Mira-Factions/releases/download/v0.2.0/MiraFactions-0.2.0.jar)

[View all releases](https://github.com/FiveSOCE/Mira-Factions/releases)

## Requirements

- Paper 1.21.11
- Java 21
- Vault
- A Vault-compatible economy provider
- PlaceholderAPI optional

## v0.2.0

MiraFactions is now command-first. Inventory GUIs are retained only where inventory interaction is useful:

- `/f upgrades`
- `/f vault`

### Faction lifecycle and membership

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

### Power, claims and raiding

Default player power is 10, death removes 2 and power regenerates over time.

Faction claim capacity is based on faction power. When a faction owns more chunks than its current power supports it becomes **RAIDABLE**.

Enemy factions can then overclaim vulnerable chunks. Overclaiming is blocked while the defending faction is protected by grace, a faction shield, peaceful status or sufficient power.

Claim tools include:

- `/f claim`
- `/f claim radius <radius>`
- `/f auto`
- `/f unclaim`
- `/f unclaim all`
- `/f map`
- `/f seechunk`

Territory protection covers block breaking/placing, buckets, containers, interactions, explosions, pistons, liquid flow and cross-border hopper transfers.

### Special territory

Admins can create:

- SafeZone
- WarZone
- Wilderness

SafeZone blocks PvP and normal player territory modification. WarZone and Wilderness follow their configured warfare rules.

### Granular faction permissions

Faction leaders can define minimum ranks for individual actions instead of relying only on hardcoded role behavior.

Permissions include building, destroying, containers, doors, buttons, levers, pressure plates, inviting, kicking, banning, promotion, claiming, faction home, warps, economy, TNT, flight, shields, upgrades, vault access, zones, diplomacy, announcements and disbanding.

Relation-based access can also be granted or denied for Ally, Truce, Neutral and Enemy relations.

Examples:

```text
/f perms container officer
/f perms relation door ally allow
/f perms relation container ally deny
```

### Diplomacy and chat

Relations:

- Ally
- Truce
- Neutral
- Enemy

Ally and Truce require mutual agreement. Enemy and Neutral changes are immediate.

Chat channels:

```text
/f chat public
/f chat faction
/f chat ally
/f chat truce
```

Public chat can display a faction tag.

### Homes and warps

- faction home
- remove faction home
- configurable warmup/cooldown
- optional respawn at faction home
- multiple faction warps
- warp limits expanded through upgrades

### Faction economy

Vault-backed faction banking:

```text
/f money balance
/f money deposit <amount>
/f money withdraw <amount>
/f money pay <faction> <amount>
```

The faction bank funds upgrades.

Daily member dues and per-claim land-rent foundations are included and persisted.

### TNT bank

```text
/f tnt balance
/f tnt deposit <amount>
/f tnt withdraw <amount>
```

Capacity can be increased through faction upgrades.

### Shields and grace

Factions can purchase Shield upgrades and activate timed raid protection with `/f shield`.

Admins can enable or stop server-wide grace periods with `/fa grace`.

### Faction flight

Faction Flight is an upgrade. Authorized faction members can use `/f fly` in their own territory and allied territory. It automatically disables when the player leaves permitted land.

### Zones

Faction claims can be divided into named permission zones:

```text
/f zone create <name>
/f zone assign <name>
/f zone greeting <name> <message>
/f zone perm <name> <permission> <rank>
/f zone delete <name>
```

Zones can override the faction-wide minimum rank for protected actions.

### Native faction vault

`/f vault` opens a persistent shared faction inventory.

Vault capacity starts small and expands through the Vault upgrade. Locked slots cannot be used until upgraded.

### Faction upgrades

`/f upgrades` opens the upgrade GUI. Purchases use faction-bank funds.

Current upgrade families:

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

Gameplay upgrades are functional. Spawner Rate accelerates the actual spawner block delay instead of creating duplicate unmanaged mobs, so it remains compatible with the normal spawner event pipeline.

### Utility commands

- `/f info [faction]`
- `/f power [player]`
- `/f top`
- `/f near`
- `/f coords`
- `/f announce <message>`
- `/f stuck`

### PlaceholderAPI

When PlaceholderAPI is present, MiraFactions registers `%mirafactions_*%` placeholders including faction name/id, description, link, rank, title, player/faction power, claims, maximum claims, raidable state, bank balance, TNT, member counts and current territory/relation.

### Public API

MiraFactions registers a `MiraFactionsApi` with Bukkit's ServicesManager for other Mira plugins. It exposes player factions, territory ownership, relations, player/faction power, raidability, SafeZone/WarZone checks and build-permission queries.

### Admin commands

```text
/fa reload
/fa save
/fa bypass
/fa power <set|add> <player> <amount>
/fa disband <faction>
/fa claim <safezone|warzone|wilderness>
/fa grace <start <minutes>|stop>
/fa peaceful <faction>
/fa permanent <faction>
/fa money <set|add> <faction> <amount>
/fa tnt <set|add> <faction> <amount>
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

v0.2.0 persists factions, ranks, titles, power, claims, special territory, relations, requests, permissions, relation access, invites, bans, homes, warps, faction bank, TNT bank, dues/debt, shields, upgrades, vault contents, zones, peaceful/permanent state and grace state.

Existing v0.1.0 faction data is migrated where possible, including legacy homes, invites and alliance requests.

## Building

```bash
gradle clean build
```

Output:

```text
build/libs/MiraFactions-0.2.0.jar
```
