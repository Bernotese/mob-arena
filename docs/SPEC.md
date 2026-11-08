# Mob Arena – Game Specification

This file is the source of truth for the game rules. If code and this file do not agree, ask `orchestrator`.
Items marked **[DEFAULT]** are decisions made by `orchestrator`. The user can change them.

## 1. Overview
- Two teams: `RED` and `BLUE`. A team has 1 or more players.
- Phases: `LOBBY` -> `PREP` -> `WAVE_CONFIG` -> `ARENA` -> (`SUDDEN_DEATH`) -> `END` -> `LOBBY`.
- In `PREP`, each team kills mobs in its own world. Each kill adds the mob to the team pool.
- In `WAVE_CONFIG`, each team puts its pool into 3 waves. The **enemy team** must fight these waves.
- In `ARENA`, each team fights in its own arena. Both arenas run at the same time.
- The mod is **server-side only**. Players join with vanilla Minecraft Java 26.3. No client mod.

## 2. Lobby
- New players spawn on a lobby platform in the default overworld. Game mode: adventure. No damage, no hunger.
- An admin (permission level 2) assigns teams and starts the game with commands (see section 10).
- A game can start only if both teams have at least 1 online player.
- Players that join during a running game become spectators in the lobby. **[DEFAULT]**
- A team player that disconnects and joins again goes back to the team position. If the player was in the arena, the player gets the respawn timer. **[DEFAULT]**

## 3. Preparation phase (`PREP`)
- Default duration: 45 min (`prepTimeSeconds = 2700`).
- At game start, the mod makes a new random seed. Both teams get a new world with this seed. One world per team.
- `/ma start` first prepares the worlds and replies "Preparing the game worlds. The game starts when they are ready." The players stay in the lobby. The chunks load in the background, so the server does not stop for long.
  - When the worlds are ready, the mod sends "Game started." to all players and `PREP` starts. The `PREP` timer starts at this moment.
  - If a team has no online player at this moment, the mod cancels the start and deletes the prepared worlds.
  - `/ma stop` during the preparation cancels it and deletes the prepared worlds.
- The worlds are new each game. The mod deletes the old worlds at game end.
- Team dimensions: each team has its own overworld, its own Nether **and its own End** (same seed for both teams).
  - A Nether portal in the team overworld links only to the team Nether, and back.
  - An End portal in the team overworld links only to the team End. The End exit portal goes back to the team overworld.
  - The Ender Dragon spawns in the team End as normal, but it does not count as a kill (section 3.1). **[DEFAULT]**
- Portals in the lobby and in the arenas do nothing.
- Players start at the world spawn with an empty inventory, full health, and full hunger. Game mode: survival.
- `keepInventory` is on in all game worlds.
- Death in `PREP`: the player respawns at the team world spawn (or at the bed). No timer. **[DEFAULT]**
- A boss bar shows the phase name and the remaining time to all players.

### 3.1 Mob collection
- A mob counts for team X if a player of team X kills it. Kills by a projectile, a tamed animal, or a trap (fire, lava, fall) after a hit by a team player in the last 10 s also count. **[DEFAULT]**
- All living mobs that have a spawn egg count, also passive mobs ("friendly creatures").
- Blacklist (config `mobBlacklist`, default: `ender_dragon`, `player`, `armor_stand`, `cod`, `salmon`, `tropical_fish`, `pufferfish`). Fish do not count. The Wither counts.
- Baby mobs count as the same type. Mob variants (for example sheep color) are not saved in version 1.
- Slime and magma cube splits: each killed slime counts with its own size as 1 slime. **[DEFAULT]**
- After each kill, the killer sees an action bar message: `+1 Zombie (total 14)`.
- A team cannot see the pool of the enemy team.

## 4. Item randomizer
- Config: `randomizerEnabled` (default `true`).
- At game start, the mod makes one item map per team: `Map<Item, Item>`. Map RED and map BLUE are different. Both are new each game.
- The map is not balanced. Any item can map to any item (for example `oak_log` -> `iron_ingot`).
- Pool: all items in the item registry, minus `air`, items that need operator permission (command blocks, structure blocks, jigsaw, debug stick, barrier, light), and `bedrock`. Spawn eggs are in the pool.
- The map applies to **all items that drop** in all team prep worlds (overworld, Nether and End):
  - Block drops, mob drops (loot and equipment), chest loot, fishing, shearing, piglin bartering, and all other loot tables.
  - The world tells the team, not the player. Thus explosions and other non-player breaks also use the team map.
- Exceptions (not randomized):
  - Items dropped by players (drop key, death, full inventory).
  - Villager and wandering trader trades.
- Each dropped stack keeps its count. The mod caps the count at the max stack size of the new item.
- Mobs from a randomized spawn egg count as normal kills when a team player kills them.

## 5. Team backpack
- At the start of `PREP`, each player gets one team backpack. The item is a vanilla `bundle` with a custom name, a custom data tag `mobarena:team_backpack`, and **no** `bundle_contents` component (so the vanilla bundle logic does not run).
- Right click (use) opens a 27-slot chest menu (`generic_9x3`). This inventory is shared by all players of the team. Changes show live to all viewers.
- The team inventory stays in server memory only (not saved to disk, because a server stop cancels the game). It is not lost on death.
- Right click on a block or an entity with the backpack opens the backpack. It never puts the backpack into an item frame, an armor stand, a pot, a shelf or an allay.
- The backpack cannot go into a bundle or a shulker box item.
- The player cannot drop the backpack, put it in a container, or put it into the backpack. If the backpack is missing (for example after a bug), the mod gives a new one.
- The backpack works in `PREP`, `WAVE_CONFIG`, `ARENA` and `SUDDEN_DEATH`. At game end, the mod clears it.

## 6. Wave configuration (`WAVE_CONFIG`)
- Default duration: 3 min (`waveConfigTimeSeconds = 180`). All players are in a safe holding area (or stay in place, frozen and invulnerable). **[DEFAULT: holding area in the lobby world]**
- Each team configures 3 waves from its own pool. The enemy team fights these waves.
- Menu: a chest menu (sgui). Any player of the team can open it with `/ma waves` or by the item in slot 9.
  - Top area: the pool. One spawn egg per mob type. The stack shows the count as lore (counts can be > 64).
  - Tabs: `Wave 1`, `Wave 2`, `Wave 3`.
  - Left click on an egg in the pool: move 1 mob to the selected wave. Shift + left click: move 10. Right click: move all.
  - Click on an egg in a wave: move it back to the pool (same click rules).
  - The menu updates live for all team players.
- Players can end the phase early: all players of both teams use `/ma ready`.
- At the end of the phase, mobs that are still in the pool go into wave 3. **[DEFAULT]**
- An empty wave is allowed. It counts as cleared at once.

## 7. Arena phase (`ARENA`)
### 7.1 Arena
- Each team gets its own copy of the arena template in its own void world. The copy is new each game.
- Template source: `config/mob-arena/arenas/<name>.nbt` (structure file). If no file exists, the mod builds a simple default arena from code. **[DEFAULT]**
- The arena has: a team spawn point at the center, a ring of mob spawn points around the arena, a barrier wall and a barrier roof as the outer border.
- Players and mobs can destroy the arena. There are no repairs between waves. Players can place blocks.
- The arena world has a fixed time (noon) and no weather. Undead mobs do not burn in the sun in arena worlds.

### 7.2 Flow
1. All team players teleport to the team spawn point. Inventory, health and items stay as they were at the end of `PREP` (keep inventory). Health and hunger are filled.
2. Preparation: 30 s (`arenaPrepSeconds`), only before wave 1. No mobs spawn. The boss bar shows the time left ("Get ready – 0:25"). Players can move, build and use items. The value 0 turns the preparation off. **[DEFAULT]**
3. Countdown: 10 s (`waveCountdownSeconds`). A title shows the numbers.
4. Wave N spawns. The mobs spawn spread over the spawn ring (around the arena).
5. All mobs of wave N must die. Then the wave is cleared.
6. Cooldown: 20 s (`waveCooldownSeconds`) for re-equip. Then wave N+1 starts with a countdown.
7. After wave 3 is cleared, the team is **finished**. The mod saves the finish time. The time starts with the countdown of wave 1 (the preparation does not count).

### 7.3 Mobs in waves
- Hostile mobs target the nearest living team player. The mod sets a large follow range.
- Neutral mobs (wolf, bee, iron golem, piglin, enderman, and others) are angry at the team. **[DEFAULT]**
- Passive mobs ("friendly creatures") do not attack. They must still die to clear the wave.
- Wave mobs are persistent (no despawn) and have a scoreboard tag `mobarena.wave.<team>.<n>`.
- Children of wave mobs (slime splits, and similar) get the same tag. They must also die.
- Mobs that leave the arena bounds teleport back to a spawn point.
- After 60 s in a wave (`mobGlowAfterSeconds`), all remaining wave mobs get the glowing effect.
- Max 150 wave mobs are alive at the same time (`maxAliveMobs`). The other mobs wait in a queue and spawn when mobs die. **[DEFAULT]**
- Players cannot use spawn eggs in the arena.
- Fish do not count (section 3.1), so they never spawn. Other water mobs (squid, dolphin, guardian, axolotl, and others) spawn in a water basin of the arena. If the arena has no basin, they spawn in a 1-block water source at the spawn point. **[DEFAULT]**

### 7.4 Wave time limit
- Each wave has a max time: 15 min (`waveMaxTimeSeconds = 900`).
- The max time starts when the wave spawns. The preparation and the countdown do not count.
- If the time ends before the wave is cleared, the team is **eliminated** at this wave. **[DEFAULT]**

### 7.5 Death and respawn
- If a team has more than 1 player: a dead player becomes a spectator in the arena for 10 s (`arenaRespawnSeconds`). Then the player respawns at the team spawn point. Keep inventory is on.
- If **all** team players are dead (or in the respawn timer) at the same time, the team is **eliminated** at once.
- A team with 1 player is eliminated when this player dies.
- Disconnected players count as dead for this rule. **[DEFAULT]**
- During the preparation (7.2 step 2), a team is not eliminated. A dead player gets the respawn timer. At the end of the preparation, all players in the respawn timer respawn at once at the team spawn.
- A player who rejoins during the preparation goes to the team spawn at once (no respawn timer). **[DEFAULT]**

### 7.6 Finished and eliminated teams
- A finished team stays in its arena in survival mode until the end.
- Players of an eliminated team become spectators and teleport to the arena of the other team to watch. **[DEFAULT]**

## 8. Win rules
Progress of a team = (highest cleared wave, then number of killed mobs in the current wave). Time is never a tie breaker.
1. If one team finishes all 3 waves and the other team is eliminated: the finished team wins.
2. If one team is eliminated, the other team continues. If it finishes, it wins. If it is also eliminated, the team with more progress wins.
3. If both teams are eliminated with equal progress (wave and kills): sudden death. **[DEFAULT]**
4. If both teams finish all 3 waves: **sudden death**.

### 8.1 Sudden death (`SUDDEN_DEATH`)
- The arenas are not repaired. All players are alive and at the team spawn. There is no preparation (`arenaPrepSeconds` does not apply). Countdown as normal.
- Each team gets all mobs of its 3 waves again (the waves from the enemy team), all at once.
- No cooldown. The wave max time applies (config `suddenDeathMaxTimeSeconds`, default 600).
- The first team that kills all mobs wins. If a team is eliminated, the other team wins.
- If both teams are eliminated or the time ends for both: the team with more killed mobs wins. If equal: draw. **[DEFAULT]**

## 9. End
- A title shows the winner to all players. Fireworks over the winner team.
- After 15 s (`endScreenSeconds`), all players go back to the lobby. Inventories are cleared. Game worlds and arena worlds are deleted.

## 10. Commands
Root: `/mobarena`, alias `/ma`.

| Command | Permission | Function |
|---|---|---|
| `/ma team assign <player> <red\|blue>` | 2 | Put a player in a team |
| `/ma team random` | 2 | Shuffle all lobby players into 2 teams (size difference max 1) |
| `/ma team remove <player>` | 2 | Remove a player from the team |
| `/ma team list` | 0 | Show the teams |
| `/ma start` | 2 | Prepare the game worlds, then start the game (section 3) |
| `/ma stop` | 2 | Cancel the game or the world preparation, back to lobby |
| `/ma skip` | 2 | End the current phase now (for tests) |
| `/ma config` | 2 | Open the config chest menu |
| `/ma config get <key>` / `set <key> <value>` / `reset` | 2 | Read or change one config value |
| `/ma setlobby` | 2 | Set the lobby spawn to the current position |
| `/ma backpack` | 0 | Open the team backpack |
| `/ma waves` | 0 | Open the wave menu (only in `WAVE_CONFIG`) |
| `/ma ready` | 0 | Mark the team as ready in `WAVE_CONFIG` |
| `/ma status` | 0 | Show phase, time, and own team progress |

## 11. Config
- File: `config/mob-arena.json`. In-game changes save to this file at once.
- Changes during a running game apply from the next phase. **[DEFAULT]**

| Key | Type | Default | Range |
|---|---|---|---|
| `prepTimeSeconds` | int | 2700 | 60–14400 |
| `waveConfigTimeSeconds` | int | 180 | 10–1800 |
| `arenaPrepSeconds` | int | 30 | 0–300 |
| `waveCountdownSeconds` | int | 10 | 3–60 |
| `waveCooldownSeconds` | int | 20 | 1–300 |
| `waveMaxTimeSeconds` | int | 900 | 60–3600 |
| `suddenDeathMaxTimeSeconds` | int | 600 | 60–3600 |
| `arenaRespawnSeconds` | int | 10 | 1–120 |
| `mobGlowAfterSeconds` | int | 60 | 1–900 |
| `maxAliveMobs` | int | 150 | 10–500 |
| `endScreenSeconds` | int | 15 | 5–120 |
| `randomizerEnabled` | bool | true | – |
| `mobBlacklist` | list of entity ids | `ender_dragon, player, armor_stand, cod, salmon, tropical_fish, pufferfish` | – |
| `arenaTemplate` | string | `default` | – |
