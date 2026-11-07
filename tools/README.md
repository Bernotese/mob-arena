# Local smoke test

Run `python3 tools/smoke.py` from the checkout.
Run `python3 tools/smoke.py --spike` to also check death, rejoin, and a RED Nether portal.

The script starts `flock ~/.gradle/mobarena-build.lock ./gradlew runServer` with Java 25.
It waits for the shared build lock.
It downloads Carpet `26.3+v260915` from Modrinth and checks its SHA-512 hash.
It stores Carpet in ignored `run/mods/`.
It writes local server settings in ignored `run/server.properties`.
RCON uses `127.0.0.1:25575` and password `mobarena-local-smoke`.
The game server uses `127.0.0.1:25565`.
Both ports must be free before the test.
The script accepts the Minecraft EULA for this local test.

The script sets the short times from T10 section 3 through `/ma config set`.
It uses 60 seconds for sudden death, as required by the current configuration bounds.
It spawns two Carpet players, assigns teams, starts PREP, and stops the game.
It stops the server in a cleanup block after success or failure.
It rejects exceptions and ERROR entries in `run/smoke-console.log`.
The spike also checks rejoin with `/player red1 spawn` and a portal to the RED Nether.
Run this test in a dedicated checkout; it changes the local world and mod configuration.

Carpet is not a Gradle dependency and is never included by the release packaging tasks.

S7 respawn timers and elimination stay in GameTests.
The Carpet death and spawn check covers the S8 rejoin path.
Carpet applies its spawn position after the JOIN callback.
The default spawn command overrides the mod's rejoin position.
This check confirms rejoin events and health, but does not confirm normal client position restoration.
The spike checks the portal before death.

Both commands stop the server immediately after `/ma stop` and the LOBBY check.
They do not wait for world cleanup or ignore shutdown exceptions.
A spike run found a known shutdown race in vanilla chunk I/O.
See [the saved trace](evidence/T10b-shutdown.txt) and T10 Spike results.
The script returns failure if this race occurs.

Run `python3 tools/shutdown_compare.py` for the shutdown comparison.
It runs each case twice: no game, game stop with a ten-second wait, and portal/rejoin with immediate shutdown.
It checks level folder cleanup after every next startup.
It runs the default smoke once more to check cleanup after the last comparison.
It saves the result table and failure traces in `tools/evidence/`.
Full server and driver logs remain in ignored `run/`.

## T10e scenarios (`--scenario`)
`python3 tools/smoke.py --scenario s1,s15,s16` (or `all`) runs the T10 section 4 scenarios on `runServer`.
They use `/ma debug` (the server run config sets `-Dmobarena.debug=true`) and the `MOBARENA_STATE` JSON.
- S1: PREP kills by `/ma debug kill`, waves by `/ma debug waves`, RED clears 3 waves, BLUE is down in wave 2.
  Checks: phase order, RED wins by "All waves cleared", END -> LOBBY, no open game level, empty inventories, folders deleted.
- S15: BLUE fights 300 zombies with `maxAliveMobs` 20 for 40 s while RCON kills 10 per 0.5 s.
  Checks: never more than 20 wave mobs alive (mod count and `@e[tag=...]`), the queue refills, `/tick query` average < 50 ms.
- S16: the server stops while both teams fight; the next start must find no Mob Arena level folders.
The fake players get Resistance V in ARENA, because Carpet players do not fight back.
Each scenario writes its server log to `run/smoke-<name>.log`.
