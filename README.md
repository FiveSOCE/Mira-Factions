# MiraFactions

MiraFactions is the power, territory, raiding and faction-management system for the Mira Paper server suite. It follows the classic FactionsUUID-style gameplay loop while adding faction ranks, granular permissions, diplomacy, economy, TNT, upgrades, zones, seasonal FTop data, protected SafeZone/WarZone territory and integrations with the wider Mira ecosystem.

## Download

[**Download MiraFactions v0.2.9**](https://github.com/FiveSOCE/Mira-Factions/releases/download/v0.2.9/MiraFactions-0.2.9.jar)

## Requirements / Dependencies

- Paper 1.21.11
- Java 21
- Vault
- A Vault-compatible economy provider
- PlaceholderAPI optional
- MiraFly recommended and required for `/f fly`
- MiraShop recommended for faction land-value pricing
- MiraSpawners recommended for typed-spawner faction land value
- LuckPerms, Essentials, MiraTab and MiraTags are optional integration/soft-dependency targets

## How MiraFactions Works

### Factions, ranks and power

Players create factions and progress through the rank hierarchy `Recruit -> Member -> Officer -> CoLeader -> Leader`. Each faction action has a configurable minimum faction rank rather than requiring a separate Bukkit permission node for every action.

Individual player power defaults to 25 maximum and -10 minimum. Death removes 2 power by default and online faction members regenerate 1 power every 5 minutes. Faction power is the sum of positive member power plus configured/admin power boosts and the faction POWER upgrade, unless an administrator has set a permanent-power override.

A faction's claim capacity is `floor(faction power)`. If a non-peaceful faction owns more chunks than its current claim capacity, it becomes **RAIDABLE**. Enemy factions can overclaim raidable territory when normal protection conditions allow it. Peaceful factions, active server grace and active faction shields prevent the relevant raid/overclaim actions.

### Claims and territory

Claims are chunk-based. Normal faction members claim land with `/f claim`, `/f claim radius <radius>` or `/f autoclaim` when their faction rank has the internal `CLAIM` permission. Players can unclaim individual chunks or all faction land when they have `UNCLAIM` access.

Administrators can create special territory with `/fa claim safezone`, `/fa claim warzone` and `/fa claim wilderness`. v0.2.9 also adds `/fa claim <Faction> <Amount>`, which force-claims the nearest requested number of chunks around the administrator for the selected faction, and `/fa autoclaim <Faction>`, which claims chunks for that faction as the administrator crosses chunk borders. Admin faction claiming can replace ordinary faction ownership but will not overwrite SafeZone or WarZone chunks.

### SafeZone and WarZone protection

SafeZone and WarZone have an absolute protection layer in addition to normal faction territory rules.

- Normal players cannot build or break in protected territory under the faction territory rules.
- Hostile and passive creature spawning is cancelled in both SafeZone and WarZone.
- Players inside SafeZone are immune to all normal `EntityDamageEvent` damage unless they have a bypass. This covers melee, arrows/projectiles, fall damage, fire, lava, explosions, potion/environmental damage and other Bukkit damage causes. Fire ticks are also cleared when damage protection triggers.
- SafeZone and WarZone block player projectile launches unless bypassed.
- SafeZone and WarZone block spawn eggs and protected launch/throw items including bows, crossbows, tridents, snowballs, eggs, ender pearls, XP bottles, splash potions, lingering potions, firework rockets and wind charges.
- Explosions cannot destroy blocks inside SafeZone or WarZone.
- WarZone intentionally remains a combat/damage-enabled area; SafeZone is the damage-safe territory.
- `mirafactions.protectedzone.bypass`, `mirafactions.bypass`, or the administrator's toggled faction bypass can override these protected-zone restrictions where applicable.

### Faction permissions and diplomacy

Faction permissions are controlled by minimum faction rank and can also expose relation-based access. Default internal faction permissions are:

| Faction permission | Default minimum rank | Purpose |
| --- | --- | --- |
| `BUILD` | Member | Place blocks in faction land. |
| `DESTROY` | Member | Break blocks in faction land. |
| `CONTAINER` | Member | Access containers. |
| `USE` | Recruit | General block use/interactions. |
| `DOOR` | Recruit | Use doors/trapdoors/gates. |
| `BUTTON` | Recruit | Use buttons. |
| `LEVER` | Recruit | Use levers. |
| `PRESSURE_PLATE` | Recruit | Use pressure plates. |
| `INVITE` | Officer | Invite/manage pending faction invites. |
| `KICK` | Officer | Kick faction members below the actor's rank. |
| `BAN` | Officer | Ban/unban players from the faction. |
| `PROMOTE` | CoLeader | Promote, demote and manage member ranks. |
| `CLAIM` | Officer | Claim faction territory. |
| `UNCLAIM` | Officer | Unclaim faction territory. |
| `SETHOME` | Officer | Set/delete faction home where allowed. |
| `HOME` | Recruit | Use faction home. |
| `SETWARP` | Officer | Create/delete faction warps. |
| `WARP` | Recruit | Use faction warps. |
| `ECONOMY` | CoLeader | Manage faction economy/bank actions. |
| `TNT_DEPOSIT` | Recruit | Deposit TNT. |
| `TNT_WITHDRAW` | Officer | Withdraw TNT. |
| `FLY` | Member | Use faction-flight entitlement when the FLIGHT upgrade and MiraFly requirements are met. |
| `SHIELD` | CoLeader | Activate the faction shield. |
| `UPGRADE` | CoLeader | Purchase faction upgrades. |
| `VAULT` | Member | Access the faction vault. |
| `ZONE` | CoLeader | Manage internal faction zones. |
| `DIPLOMACY` | CoLeader | Manage faction relations. |
| `ANNOUNCE` | Officer | Send faction announcements. |
| `DISBAND` | Leader | Disband the faction. |

Diplomatic relations are `ALLY`, `TRUCE`, `NEUTRAL` and `ENEMY`. Factions can separately control relation access for supported permissions. Faction chat supports public, faction, ally and truce channels.

### Economy, TNT, upgrades and value

MiraFactions includes a faction bank, daily member-dues foundation, claim-rent foundation, TNT storage, shields, faction homes/warps, upgrade levels, configurable internal faction zones and a persistent faction vault.

Faction wealth is calculated as:

`Spawner Land Value + Faction Bank = Total Wealth`

When MiraSpawners and MiraShop are installed, land value reads actual typed MiraSpawners stacks and their MiraShop buy prices. There is no spawner maturation mechanic.

MiraFactions keeps bank/value history and exact raid-value gain/loss records. Seasonal statistics include current/peak wealth, best seasonal FTop rank, raid wins/losses and value gained/lost. FTop data and seasonal records can be shown through commands, the podium GUI and PlaceholderAPI. Persistent data is stored primarily in `plugins/MiraFactions/factions.yml`, `faction-history.yml` and `seasons.yml`.

### Faction flight

MiraFactions owns faction-flight **entitlement**, while MiraFly owns the live Bukkit flight state. `/f fly` checks faction membership, the faction FLIGHT upgrade, the player's internal faction `FLY` permission and MiraFly availability. MiraFly then controls whether flight can remain active in the player's current territory. This prevents the two plugins from competing over `allowFlight`.

## Commands

All normal `/f` commands require the Bukkit permission `mirafactions.use`. Individual faction-management actions are additionally controlled by the faction's internal rank/permission settings described above.

### Player / faction commands

| Command | What it does |
| --- | --- |
| `/f help [page]` | Shows faction command help. |
| `/f create <name>` | Creates a faction and makes the creator Leader. |
| `/f invite <player>` | Invites a player to the faction. |
| `/f invite list` | Lists pending faction invites. |
| `/f invite clear` | Clears all pending invites. |
| `/f invite revoke <player>` | Revokes a pending invite. |
| `/f join <faction>` | Joins an open faction or a faction for which the player has a valid invite. |
| `/f leave` | Leaves the current faction; single-member non-permanent factions may disband through this flow. |
| `/f disband` | Disbands the faction when the player has `DISBAND` access. |
| `/f kick <player>` | Kicks a lower-ranked faction member. |
| `/f ban <player>` | Bans a player from the faction. |
| `/f unban <player>` | Removes a faction ban. |
| `/f bans` | Lists faction bans. |
| `/f promote <player>` | Promotes a faction member subject to rank hierarchy rules. |
| `/f demote <player>` | Demotes a faction member subject to rank hierarchy rules. |
| `/f role <player> <recruit|member|officer|coleader>` | Assigns a non-Leader faction rank directly when permitted. |
| `/f transfer <player>` | Transfers faction leadership to another member. |
| `/f claim` | Claims the current chunk for the player's faction. |
| `/f claim radius <radius>` | Attempts to claim a square radius of chunks around the player, capped by configuration and faction power. |
| `/f auto` / `/f autoclaim` | Toggles normal faction autoclaim as the player crosses chunk borders. |
| `/f unclaim` | Unclaims the current faction-owned chunk. |
| `/f unclaim all` | Removes all claims owned by the player's faction. |
| `/f map` | Displays a text territory map around the player. |
| `/f seechunk` / `/f sc` | Toggles visible particle chunk boundaries. |
| `/f sethome` | Sets the faction home inside owned territory. |
| `/f delhome` | Deletes the faction home. |
| `/f home` | Teleports to the faction home using configured warmup/cooldown rules. |
| `/f setwarp <name>` | Creates a named faction warp. |
| `/f delwarp <name>` | Deletes a faction warp. |
| `/f warp <name>` | Teleports to a faction warp. |
| `/f warps` | Lists faction warps and current warp-slot usage. |
| `/f chat [public|faction|ally|truce]` | Changes the player's chat channel; without an argument it toggles faction/public chat. |
| `/f c [mode]` | Alias for `/f chat`. |
| `/f ally <faction>` | Requests/sets an Ally relation through diplomacy rules. |
| `/f truce <faction>` | Requests/sets a Truce relation. |
| `/f enemy <faction>` | Sets Enemy relation where allowed. |
| `/f neutral <faction>` | Returns the relation toward Neutral where allowed. |
| `/f power [player]` | Shows player power and, when applicable, faction power/claim capacity and RAIDABLE/PROTECTED state. |
| `/f info [faction]` | Shows detailed faction information. |
| `/f show [faction]` | Alias for `/f info`. |
| `/f status [faction]` | Alias for `/f info`. |
| `/f set tag <name>` | Renames the faction when the player has the required leadership access. |
| `/f set name <name>` | Alias for the faction rename flow. |
| `/f set description <text>` | Changes the faction description. |
| `/f set link <text|url>` | Changes the faction link/text field. |
| `/f set open <true|false>` | Changes whether players can join without an invite. |
| `/f set title <player> <title|clear>` | Sets or clears a member title. |
| `/f set dues <amount>` | Sets daily member dues; Leader-only in the current implementation. |
| `/f perms` / `/f permissions` | Lists or manages faction minimum-rank permissions. |
| `/f perms relation <permission> <ally|truce|neutral|enemy> <allow|deny>` | Controls relation-based access for a faction permission. |
| `/f money` / `/f bank` | Views/manages faction-bank functions according to faction economy permissions. |
| `/f money history [page]` | Shows persistent faction bank transaction history. |
| `/f tnt` | Views/deposits/withdraws faction TNT according to TNT permissions. |
| `/f shield` | Activates the faction shield when the faction has the upgrade, permission and cooldown availability. |
| `/f fly` | Delegates faction flight to MiraFly after entitlement checks. |
| `/f upgrades` / `/f upgrade` | Opens the faction upgrade GUI. |
| `/f vault` / `/f fvault` | Opens the faction vault. |
| `/f zone` / `/f zones` | Creates/manages internal faction zones, greetings and zone-specific permissions. |
| `/f near` | Shows nearby faction members within the configured radius. |
| `/f coords` | Shows faction/member coordinate information provided by the command implementation. |
| `/f announce <message>` | Sends a faction announcement when the player has `ANNOUNCE` access. |
| `/f stuck` | Searches nearby chunks for Wilderness and teleports the player out when a suitable location is found. |
| `/f top` | Shows FTop ranking information. |
| `/f top gui` | Opens the FTop podium GUI. |
| `/f value` | Shows faction wealth/value information. |
| `/f value history [page]` | Shows persistent faction-value history. |
| `/f log [page]` | Shows the persistent faction audit log. |
| `/f audit [page]` | Alias/view for faction audit history. |
| `/f season [faction]` | Shows seasonal statistics for the player's or selected faction. |
| `/f podium` | Opens the seasonal/FTop podium display. |

### Administrator commands

All `/fa` commands require `mirafactions.admin`. Aliases for `/fa` are `/fadmin` and `/factionadmin`.

| Command | What it does |
| --- | --- |
| `/fa help [page]` | Shows administrator help. |
| `/fa reload` | Reloads MiraFactions configuration. |
| `/fa save` | Forces faction data to disk. |
| `/fa bypass` | Toggles the administrator's runtime faction-territory bypass. |
| `/fa chatspy` / `/fa spy` | Toggles faction chat spying for the administrator. |
| `/fa info <faction>` | Shows administrative faction details, UUID, members, claims, power, bank, TNT, flags and shield state. |
| `/fa power <set|add> <player> <amount>` | Sets or adds individual player power. |
| `/fa powerboost <set|add> <faction> <amount>` | Sets/adds the faction's power boost. |
| `/fa permanentpower set <faction> <amount>` | Sets a permanent faction-power override. |
| `/fa permanentpower clear <faction>` | Clears the permanent power override. |
| `/fa disband <faction>` | Force-disbands a faction. |
| `/fa forcejoin <player> <faction>` | Moves an online player into the selected faction. |
| `/fa forcekick <player>` | Force-removes an online player from their faction. |
| `/fa forcerole <player> <recruit|member|officer|coleader|leader>` | Force-sets a member's faction rank. |
| `/fa forcehome <player> <faction>` | Teleports an online player to the selected faction's home. |
| `/fa rename <faction> <newName>` | Force-renames a faction. |
| `/fa claim safezone` | Converts the current chunk to SafeZone. |
| `/fa claim warzone` | Converts the current chunk to WarZone. |
| `/fa claim wilderness` | Removes special/faction ownership from the current chunk and makes it Wilderness. |
| `/fa claim <Faction> <Amount>` | Force-claims 1-10,000 nearby chunks for the selected faction. Ordinary faction claims may be replaced, but SafeZone/WarZone are never overwritten. |
| `/fa autoclaim <Faction>` | Enables admin autoclaim for a selected faction and claims chunks as the administrator crosses boundaries. Running it again for the same faction toggles it off. |\n| `/fa autoclaim safezone` | Converts the current chunk to SafeZone immediately, then automatically converts each newly entered chunk to SafeZone. Run it again or use `off` to disable. |\n| `/fa autoclaim warzone` | Converts the current chunk to WarZone immediately, then automatically converts each newly entered chunk to WarZone. Run it again or use `off` to disable. |
| `/fa autoclaim off` | Disables any active admin faction, SafeZone or WarZone autoclaim mode. |
| `/fa grace status` | Shows whether server grace is active. |
| `/fa grace start <minutes>` | Starts server grace for the specified duration. |
| `/fa grace stop` | Stops server grace. |
| `/fa peaceful <faction>` | Toggles the faction's peaceful flag. |
| `/fa permanent <faction>` | Toggles the faction's permanent flag. |
| `/fa rentexempt <faction>` | Toggles rent exemption for a faction. |
| `/fa money <set|add> <faction> <amount>` | Sets or adds faction bank balance. |
| `/fa tnt <set|add> <faction> <amount>` | Sets or adds faction TNT balance. |
| `/fa shield clear <faction>` | Clears the faction's currently active shield while leaving cooldown state intact. |
| `/fa shield reset <faction>` | Clears both active shield and shield cooldown state. |
| `/fa upgrade <set|add> <faction> <upgrade> <level>` | Force-sets or adds an upgrade level, clamped to that upgrade's valid range. |

## Permissions

These are Bukkit/server permission nodes. Faction-rank permissions listed earlier are separate internal faction permissions configurable by faction leadership.

| Permission | Default | What it does |
| --- | --- | --- |
| `mirafactions.use` | Everyone | Allows the normal `/f`, `/faction` and `/factions` command surface. |
| `mirafactions.admin` | OP | Allows `/fa` administration, force-management and admin claim tools. |
| `mirafactions.bypass` | OP | Bypasses normal faction territory protection and is also accepted by protected-zone checks. |
| `mirafactions.protectedzone.bypass` | OP | Bypasses SafeZone/WarZone item/projectile restrictions and SafeZone damage protection. |
