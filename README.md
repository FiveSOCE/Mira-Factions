# MiraFactions

Command-first faction warfare for **Paper 1.21.11 / Java 21**, built for the Mira plugin suite and modeled around the classic FactionsUUID power, territory and raiding loop.

## Download

Current release: **v0.2.6**

[**Download MiraFactions v0.2.6**](https://github.com/FiveSOCE/Mira-Factions/releases/download/v0.2.6/MiraFactions-0.2.6.jar)

[View all releases](https://github.com/FiveSOCE/Mira-Factions/releases)

## Requirements

- Paper 1.21.11
- Java 21
- Vault
- Vault-compatible economy provider
- PlaceholderAPI optional
- MiraShop and MiraSpawners recommended for typed-spawner faction value

## v0.2.6 seasonal faction progression

- faction seasonal statistics
- current and peak seasonal wealth
- best seasonal FTop position
- seasonal raid wins/losses and value gained/lost
- all-time faction wealth peak records
- highest-value-ever PlaceholderAPI support
- `/f season [faction]`
- `/f podium`
- `/f top gui`
- FTop podium GUI

Season data persists separately in `plugins/MiraFactions/seasons.yml`.

## v0.2.5 faction intelligence

- persistent faction audit log: `/f log [page]`, `/f audit [page]`
- faction bank transaction history: `/f money history [page]`
- faction value history: `/f value history [page]`
- exact raid value gain/loss logging on overclaim
- FTop PlaceholderAPI ranks 1 through 10

History persists in `plugins/MiraFactions/faction-history.yml`.

## FTop placeholders

```text
%mirafactions_top_1_name%
%mirafactions_top_1_value%
%mirafactions_top_1_land%
%mirafactions_top_1_bank%
%mirafactions_top_1_power%
%mirafactions_top_1_members%
%mirafactions_top_1_peak%
%mirafactions_highest_value_ever%
%mirafactions_highest_value_faction%
%mirafactions_season%
%mirafactions_season_peak%
%mirafactions_season_best_rank%
%mirafactions_season_raids_won%
%mirafactions_season_raids_lost%
```

Replace `1` with ranks `1` through `10` where applicable. Static FTop placeholders work without a player context for holograms, scoreboards and MiraNPC.

## Core systems

MiraFactions includes faction lifecycle/ranks, power, claims, overclaiming/raiding, granular faction permissions, Ally/Truce/Neutral/Enemy relations, faction chat channels, SafeZone/WarZone territory, faction bank, dues/rent foundations, homes, warps, TNT bank, shields, grace, faction flight foundations, zones, upgrades, vaults, `/f top`, `/f list`, `/f value`, PlaceholderAPI and a public Bukkit ServicesManager API.

Normal power defaults to 25 max, -10 minimum, 2 loss on death and 1 regeneration every 5 minutes. Operators can set uncapped power with `/fa power <player> <amount>`.

Faction wealth is:

```text
Spawner Land Value + Faction Bank = Total Wealth
```

Spawner land value reads actual typed MiraSpawners stacks and MiraShop buy prices. There is no spawner maturation mechanic.

## Building

```bash
gradle clean build
```

Output:

```text
build/libs/MiraFactions-0.2.6.jar
```
