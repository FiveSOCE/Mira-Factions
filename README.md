# MiraFactions

Power, territory, raiding and faction-management system for the Mira Paper server suite.

MiraFactions follows the classic FactionsUUID-style gameplay loop while adding faction ranks, granular permissions, diplomacy, economy, TNT, upgrades, zones, SafeZone/WarZone protection, seasonal FTop data and deep integration with the wider Mira ecosystem.

## Downloads and compatibility

| Server target | Java | Final JAR name | Download |
| --- | --- | --- | --- |
| **Paper 1.18.2** | 17 | `MiraFactions_1.18.2.jar` | [Download 1.18.2 JAR](https://github.com/FiveSOCE/Mira-Factions/releases/download/dev-1.18.2/MiraFactions_1.18.2.jar) |
| **Paper 1.21.11–26.2** | 21 baseline; modern server may require Java 25 | `MiraFactions_1.21.11-26.2.jar` | [Download modern JAR](https://github.com/FiveSOCE/Mira-Factions/releases/download/compat-modern-1.21.11-26.2/MiraFactions_1.21.11-26.2.jar) |

**Latest Paper 1.18.2 testing build:** updated with the upgrade, faction profile, online-sorted list and claim-announcement refinements. The direct 1.18.2 download above points to the newly rebuilt and published `MiraFactions_1.18.2.jar` (verified [Java 17 build](https://github.com/FiveSOCE/Mira-Factions/actions/runs/46168088964)). Back up faction data before replacing your JAR.

### What's new in the Paper 1.18.2 build

- `/f upgrades` purchases now use the **player's Vault balance**, not faction funds. Eleven upgrades remain available; Power, Member Limit, Shield, Power Regen, Power Loss, Zone Limit, Home Warmup and Warp Warmup are removed from new purchases.
- Configure exact per-level upgrade costs in `plugins/MiraFactions/upgrade-prices.yml`, created automatically. The GUI displays current levels, costs and descriptions.
- `/f show [faction]` now displays FTop ranking, wealth, leader and co-leaders, faction age, land/power, enemies and online/offline rosters.
- `/f list [page]` lists factions in descending order of **online players**, ten per page, as `FactionName [online/total]`.
- Successful manual and automatic chunk claims notify online faction members of the claimant and chunk coordinates.
- Prior refinements include the per-faction `/f map` legend, removal of Truce, explicit `/f c f|p|a` channel selection, `/f bank` money/XP GUI, and clickable `/f top` wealth breakdown.

These refinements are **compiled and published but still require in-game retesting**. [Full legacy configuration and documentation](https://github.com/FiveSOCE/Mira-Factions/blob/compat/1.18.2/README.md).

Both versioned JARs have passed their respective GitHub Actions compilation checks and are available as verified GitHub release assets. In-game integration and functionality testing remains outstanding. The modern binary is compiled against Paper 1.21.11 and checked against 26.2. Compatibility with intermediate versions 1.19–1.21.10 is **not yet verified**.

**Build status:** Version-specific direct links are provided where published. A GitHub Actions or Releases link means the corresponding versioned JAR has not been verified. Compilation alone does not establish in-game compatibility; intermediate versions 1.19–1.21.10 remain unverified unless specifically documented.

**Installation:** Back up plugin data, remove older JAR copies and install only the build matching your server. Never install both builds together.

[Legacy source](https://github.com/FiveSOCE/Mira-Factions/tree/compat/1.18.2) · [Modern source](https://github.com/FiveSOCE/Mira-Factions) · [All releases](https://github.com/FiveSOCE/Mira-Factions/releases)

## Dependencies and installation

- **Required:** Paper matching the downloaded JAR's server target, appropriate Java runtime, **Vault** and a Vault-compatible economy provider.
- **Optional integrations:** PlaceholderAPI, LuckPerms, EssentialsX, MiraFly, MiraTab, MiraTags, MiraShop, MiraSpawners and MiraCosmetics.
- **Faction flight:** MiraFly is needed for actual player flight in the integrated setup.
- **FTop valuation:** MiraShop and MiraSpawners add live item/spawner valuation where compatible; fallback material overrides and configured worth values remain available.
- **Install:** Back up `plugins/MiraFactions/`; remove the old MiraFactions JAR before adding the version-specific one, then restart. **Never install both builds together.**

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `mirafactions.use` | Everyone | Normal faction commands |
| `mirafactions.admin` | OP | Administrative `/fa` commands |
| `mirafactions.bypass` | OP | Bypass ordinary faction protection |
| `mirafactions.protectedzone.bypass` | OP | Bypass SafeZone/WarZone restrictions |

Inside each faction, player actions are also controlled by configurable faction roles: Recruit, Member, Officer, CoLeader and Leader.

## Faction Model

Faction ranks are:

```text
Recruit → Member → Officer → CoLeader → Leader
```

Faction actions are controlled by configurable minimum faction rank rather than requiring a separate Bukkit permission node for every internal action.

Default player power is bounded and persistent. Faction claim capacity is derived from faction power. When a non-peaceful faction owns more chunks than its current claim capacity it becomes **RAIDABLE**, allowing enemy overclaim mechanics where protection rules permit.

## Claims and Territory

Claims are chunk based.

Player flows include:

- `/f claim`
- `/f claim radius <radius>`
- `/f autoclaim`
- `/f unclaim`
- `/f unclaim all`
- `/f map`
- `/f seechunk`

Administrators can manage special territory with SafeZone, WarZone and Wilderness claim tools, plus force-claim/autoclaim support for normal factions.

## SafeZone and WarZone

### SafeZone

SafeZone is the strict protected area:

- no normal player damage
- no building/breaking
- no hostile projectile/potion behavior
- no terrain-changing buckets, pistons, fire or explosions
- normal creature spawning blocked
- selected utility blocks such as chests, barrels, ender chests, anvils and enchanting tables remain usable

### WarZone

WarZone is the PvP arena with terrain protection:

- PvP allowed
- melee/projectiles/combat throwables allowed
- combat explosions may damage entities
- normal creature spawning allowed
- terrain placement/breaking and protected block changes remain blocked

Creeper spawn eggs are intentionally allowed in Wilderness and normal faction territory as a raiding mechanic, while SafeZone and WarZone block them.

## MiraNPC Protected-Zone Integration — v0.2.32

Managed MiraNPC Villagers are explicitly allowed to spawn/restore inside SafeZone and remain clickable there.

This exception applies only to managed MiraNPC entities; normal mobs/entities continue to follow SafeZone protection rules.

## Member Login Notifications — v0.2.33

Faction-member login/quit notifications now default to **disabled** so MiraCore remains the sole public server join-message authority.

Existing servers can disable the old faction-only chatter with:

```yaml
members:
  login-notifications: false
```

then run:

```text
/fa reload
```

## Economy, TNT and Upgrades

MiraFactions includes:

- faction bank
- persistent transaction history
- member dues foundation
- TNT storage
- faction shield system
- faction homes and warps
- faction vault
- configurable upgrades
- internal faction zones
- diplomacy/relations

MiraFly owns actual Bukkit flight state. MiraFactions owns faction-flight entitlement and delegates live flight to MiraFly after checking membership, faction upgrade and internal permissions.

## FTop and Valuation

Faction wealth is calculated from:

```text
Claimed Asset Value + Faction Bank = Total Wealth
```

Claimed asset valuation can include:

- placed MiraSpawners stacks
- typed spawner items in containers
- configured valuable blocks
- priced items in containers
- nested shulker contents up to configured depth
- Essentials `worth.yml` fallback values where applicable

FTop uses a shared persistent per-chunk cache. Normal block/container changes invalidate only affected chunks; `/fa top update` performs the deliberate full rebuild.

A persistent native FTop TextDisplay can be created with:

```text
/fa hologram top
```

and removed with:

```text
/fa hologram remove
```

## Relations and Chat

Supported relations:

```text
ALLY
TRUCE
NEUTRAL
ENEMY
```

Faction chat supports public, faction, ally and truce channels. Relations can also participate in configured territory permissions.

## Common Player Commands

| Command | Purpose |
| --- | --- |
| `/f create <name>` | Creates a faction. |
| `/f invite <player>` | Invites a player. |
| `/f join <faction>` | Joins an eligible faction. |
| `/f leave` | Leaves the current faction. |
| `/f disband` | Disbands the faction when permitted. |
| `/f kick <player>` | Removes a lower-ranked member. |
| `/f promote <player>` / `/f demote <player>` | Changes member rank. |
| `/f transfer <player>` | Transfers leadership. |
| `/f claim` / `/f unclaim` | Manages territory. |
| `/f home` / `/f sethome` | Uses/manages faction home. |
| `/f warp <name>` / `/f setwarp <name>` | Uses/manages faction warps. |
| `/f chat [mode]` | Changes faction chat channel. |
| `/f ally|truce|enemy|neutral <faction>` | Manages relations. |
| `/f power [player]` | Shows player/faction power state. |
| `/f info [faction]` | Shows faction information. |
| `/f perms` | Opens faction permission management. |
| `/f money` | Faction bank/economy flow. |
| `/f tnt` | Faction TNT storage. |
| `/f shield` | Shield control/status. |
| `/f fly` | Requests faction flight through MiraFly. |
| `/f upgrades` | Opens faction upgrades. |
| `/f vault` | Opens the faction vault. |
| `/f zone` | Manages internal faction zones. |

Normal `/f` use requires `mirafactions.use`; individual actions are additionally governed by faction rank/internal permission configuration.

## Administration

`/fa` provides administrative tools for territory, factions, power, FTop cache rebuilds, special-zone management, holograms and diagnostics.

Global bypass permissions include:

```text
mirafactions.bypass
mirafactions.protectedzone.bypass
```

## Persistence

Primary data files live under:

```text
plugins/MiraFactions/
```

including faction state, history, seasons, FTop cache and hologram state.
