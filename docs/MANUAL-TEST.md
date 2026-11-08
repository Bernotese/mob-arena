# Manual test guide

Use this guide to test Mob Arena on a real server with real clients.
The GameTests and the smoke test cannot check the client view. Section 5 lists these checks.

## 1. Build the jar
1. Go to the repository root.
2. Run `tools/gradle.sh build`. This also runs the unit tests and the GameTests.
3. Get the jar: `build/libs/mob-arena-0.1.0.jar`.

Fantasy and sgui are inside the jar. You do not install them separately.

## 2. Set up the server
Use these versions (from `gradle.properties`):

| Item | Version |
|---|---|
| Minecraft server | 26.3 |
| Fabric Loader | 0.20.1 or newer |
| Fabric API | 0.165.2+26.3 |
| Java | 25 |

1. Install a Fabric 26.3 server with the Fabric installer (loader 0.20.1).
2. Put the Fabric API jar and `mob-arena-0.1.0.jar` in `mods/`.
3. Start the server one time. Accept the EULA in `eula.txt`. Start the server again.
4. Make your test account an operator: `op <name>` in the server console.
5. Use a normal Minecraft 26.3 client. The client does not need Fabric or the mod.

## 3. Short times for a test
Send these commands as an operator in the lobby. The values apply at the next phase.

```
/ma config set prepTimeSeconds 300
/ma config set waveConfigTimeSeconds 60
/ma config set arenaPrepSeconds 10
/ma config set waveCountdownSeconds 3
/ma config set waveCooldownSeconds 5
/ma config set waveMaxTimeSeconds 120
/ma config set suddenDeathMaxTimeSeconds 120
/ma config set arenaRespawnSeconds 5
/ma config set mobGlowAfterSeconds 20
/ma config set endScreenSeconds 10
/ma config set maxAliveMobs 50
```

- Check a value: `/ma config get <key>`.
- Open the config menu: `/ma config` (permission level 2).
- Go back to the default values: `/ma config reset`.

## 4. Admin commands, step by step
1. Set the lobby spawn (optional): stand on the spawn point and use `/ma setlobby`.
2. Make the teams:
   - `/ma team assign <player> red` and `/ma team assign <player> blue`, or
   - `/ma team random` for random teams.
   - Check the teams: `/ma team list`. Remove a player: `/ma team remove <player>`.
3. Start the game: `/ma start`. The mod prepares the worlds. Then it sends "Game started." and `PREP` starts.
4. `PREP`: kill mobs in your team world. Each kill adds the mob to your team pool.
5. `WAVE_CONFIG`: open the wave menu (recovery compass "Wave menu", or `/ma waves`).
   - Click a mob in the pool to move it into the selected wave. Left: 1. Shift+Left: 10. Right: all.
   - Click a mob in the wave to move it back to the pool.
   - Select wave 1, 2 or 3 with the tabs.
   - Slot 33 is "Ready". `/ma ready` does the same. When all players are ready, the phase ends early.
6. `ARENA`: each team fights the waves of the other team.
7. Other commands:
   - `/ma status`: phase, time left, team, progress.
   - `/ma skip`: end the current phase now.
   - `/ma stop`: stop the game. All players go back to the lobby.
   - `/ma backpack`: open the team backpack.

## 5. Checklist for a real client
Mark each item. Write the problem and the time if an item fails.

**HUD and sound**
- [ ] Titles: countdown numbers and "Go" before each wave; the result title at game end.
- [ ] Boss bar: correct text, color and progress in each phase (`PREP`, `WAVE_CONFIG`, `ARENA`, `SUDDEN_DEATH`, `END`). Each team sees only its own bar. Lobby players see a neutral bar.
- [ ] Sounds: a sound for each countdown number, "Go", wave cleared, eliminated, sudden death, and the result. At game end, fireworks fly over the winner team, with the vanilla firework sound.

**Menus**
- [ ] Wave menu: all clicks (left, shift+left, right) move the correct count. The counts change for all team viewers at once.
- [ ] Wave menu: a click on slot 33 ("Ready") closes the menu with no error and no item in the cursor.
- [ ] Wave menu: no item can be taken out of the menu (also not with shift-click, number keys, or drag).
- [ ] Team backpack (bundle item): right-click opens it. Both team players see the same items. Items stay after death.
- [ ] Backpack: you cannot drop it, put it in a chest, or put it into the backpack.

**Arena**
- [ ] Preparation: at arena start, each team is at its spawn. The boss bar shows "Get ready – 0:10" and counts down. No mobs spawn.
- [ ] After the preparation, the countdown shows all numbers, also the first "3" (each with a sound). Then "Go" and wave 1.
- [ ] Die during the preparation (for example, fall into lava). The team is not eliminated. At the end of the preparation, you respawn at the team spawn.
- [ ] Leave during the preparation and join again before it ends. You are at the team spawn, without a respawn timer.
- [ ] The wave 1 boss bar starts with the full wave time (the preparation did not use wave time).
- [ ] Mobs glow after `mobGlowAfterSeconds`.
- [ ] A dead player is a spectator, then respawns at the team spawn after `arenaRespawnSeconds`.
- [ ] An eliminated team watches the other arena in spectator mode.
- [ ] Sudden death (both teams finish or both are eliminated with equal progress): all players are alive at their spawn; the chat message shows.
- [ ] Sudden death starts without a preparation. After an elimination, the spectators go back to their arena: check that they see the first countdown number "3". If not, write it in the report.

**Worlds**
- [ ] Randomizer: break the same block type in RED and in BLUE. The drops are different. Player drops are not changed.
- [ ] Nether portal and End portal: each team goes only to its own Nether and End, never to the other team or the vanilla worlds.
- [ ] Rejoin: leave in `PREP` and join again. You are at the same position. Leave in `ARENA` (after the preparation) and join again. You get the respawn timer.

**More players**
- [ ] If possible, test with 2 players per team: shared backpack, shared wave menu, respawn while the team mate is alive.
- [ ] A player who joins during a game is a lobby spectator.

At the end, use `/ma stop` or let the game end. Check that all players are in the lobby with empty inventories.
