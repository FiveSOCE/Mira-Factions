# MiraFactions

Modern GUI-first factions for **Paper 1.21.11 / Java 21**.

## Download

Current release target: **v0.1.0**

[Download MiraFactions v0.1.0](https://github.com/FiveSOCE/Mira-Factions/releases/download/v0.1.0/MiraFactions-0.1.0.jar)

[View all releases](https://github.com/FiveSOCE/Mira-Factions/releases)

## v0.1.0 core

- Create, join, leave and disband factions
- Invite, kick, promote, demote and transfer leadership
- Member / Officer / Coleader / Leader ranks
- Chunk-based claims
- Player power and faction claim capacity
- Power loss on death and timed regeneration
- Claimed-land build, container, interaction and explosion protection
- Faction home with movement-cancelled warmup and cooldown
- Faction chat
- Ally, Enemy and Neutral diplomacy
- Mutual alliance requests
- Friendly-fire and ally-fire protection
- Territory entry/exit action-bar messages
- GUI-first `/f` dashboard
- Persistent `factions.yml` storage
- Admin reload/save commands

## Power

Default per-player power:

- Start: 10
- Minimum: -10
- Maximum: 10
- Death: -2
- Regen: +1 every 5 minutes

Faction claim capacity is the floor of the faction's summed positive player power.

## Claims

Claims are chunk based. By default all worlds can be claimed. Set `claims.worlds` in `config.yml` to restrict claiming to named worlds.

Claimed territory protects:

- block breaking
- block placing
- buckets
- containers
- right-click interactions
- explosions

Faction members can build normally in their own claims. `mirafactions.bypass` bypasses claim protection.

## Commands

`/f` opens the GUI.

Core direct commands:

```text
/f create <name>
/f invite <player>
/f join <faction>
/f leave
/f disband
/f kick <player>
/f promote <player>
/f demote <player>
/f transfer <player>
/f claim
/f unclaim
/f sethome
/f home
/f chat
/f ally <faction>
/f enemy <faction>
/f neutral <faction>
/f power
/f info [faction]
```

Admin:

```text
/fadmin reload
/fadmin save
```

## Rank rules

- Member: normal faction member
- Officer: invite, kick lower ranks, claim/unclaim, set faction home
- Coleader: Officer abilities plus diplomacy and rank management
- Leader: full control, leadership transfer and disband

## Diplomacy

- Ally requests must be accepted by the other faction issuing `/f ally <faction>` back
- Enemy status is immediate and mutual
- Neutral clears existing relations and pending alliance requests
- Friendly fire inside the same faction is disabled by default
- Ally friendly fire is disabled by default

## Permissions

- `mirafactions.use` default everyone
- `mirafactions.admin` default OP
- `mirafactions.bypass` default OP

## Storage

Faction state is stored in:

```text
plugins/MiraFactions/factions.yml
```

This includes members, ranks, power, claims, relations, invites and faction homes.

## Building

```bash
gradle clean build
```

Output:

```text
build/libs/MiraFactions-0.1.0.jar
```
