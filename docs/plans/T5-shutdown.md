# T5 shutdown race

## Cause

Checked with `javap -c -p` on Minecraft `26.3` and Fantasy `0.8.4+26.3`.

| Method | Observed behavior |
|---|---|
| `WorldCleanup.clean` | Calls `RuntimeLevelHandle.delete` and discards the handle. |
| `RuntimeLevelHandle.delete` | Queues deletion in Fantasy. |
| `Fantasy.onServerStopping` | Deletes all remaining temporary levels through `RuntimeLevelManager.delete`. |
| `RuntimeLevelManager.delete` | Removes the level from the server map, emits UNLOAD, unregisters it, and deletes its directory. |
| `RuntimeLevelManager.unregister` | Only removes the registry entry. It does not close the level. |
| `MinecraftServer.stopServer` | Saves and closes only levels still in the server map. |
| `DedicatedServer.stopServer` | Calls the base stop method, then `Util.shutdownExecutors`. |
| `ServerLevel.close` | Closes the chunk source and entity manager. |
| `ServerChunkCache.close` | Saves chunks, closes saved data, light engine, and chunk map. |
| `ChunkMap.close` | Closes its dispatchers, POI storage, and chunk region storage. |
| `IOWorker.close` | Sets `shutdownRequested`, waits for its shutdown task, and closes storage. |

Fantasy removes temporary levels before vanilla reaches its close loop.
No component then closes those removed levels.
Their chunk workers remain open when the dedicated server shuts down the shared I/O pool.
An active worker can then reschedule work into that stopped pool, producing the recorded `RejectedExecutionException`.
Portal generation adds enough pending Nether work to expose this missing close operation.

The original trace and repeated failures are in `tools/evidence/T10b-*` and `3-portal-rejoin-immediate-*-error.txt`.
The trace identifies vanilla I/O scheduling, not the caller that removed the level earlier.
The missing close is visible in Fantasy's deletion path and vanilla's level-map close loop.

## Fix design

Track the exact game level instances until they unload.
Use `ServerLevelEvents.UNLOAD` to close a tracked level after Fantasy removes it from the server map.
Do not close a level that remains in the map; vanilla still owns its close operation.
Close before Fantasy deletes the directory and before vanilla shuts down the shared I/O pool.
Keep the existing startup removal of owned crash folders.

## Checks

- Before the fix: run case 4, portal/rejoin plus a ten-second wait, twice.
- Before the fix: run case 5, portal without death/rejoin plus immediate shutdown, twice.
- Add an isolated GameTest that observes the actual Nether chunk worker shutdown flag.
- Confirm that the regression fails before the fix and passes after it.
- After the fix: run case 3 three times, with no cleanup delay or exception allowance.
- Check that no owned level folders remain after every next startup.
- Run the full build through the shared lock.

## Results

Paused before the production fix.
Case 4 passed twice before the fix, with clean logs.
The next-start check after the first case 4 run passed.
Case 5 and the final cleanup startup have not run.
The comparison script and its queued lock request were stopped for the restart.
No other agent process was stopped.

The new regression compiled, but the filter `*removednethercloseschunkworker` selected no tests.
It has not yet demonstrated the expected failure. Correct the selection before rerunning it.
The selection failure is saved in `tools/evidence/T5-shutdown-regression-selection.txt`.

Resume with case 5 and the regression selection.
Then implement the unload close, apply the requested tool fixes, and run the build and three case 3 checks.
