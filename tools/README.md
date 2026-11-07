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
