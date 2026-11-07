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

Fix: `WorldCleanup.onUnload` (Fabric `ServerLevelEvents.UNLOAD`) closes a tracked game level only after Fantasy removed it from the server map.
Fantasy fires UNLOAD after `Map.remove` and before `FileUtils.deleteDirectory` (checked in bytecode).
`IOWorker.close` waits for pending writes, then closes region storage, so no queued write can run after the delete.
Levels that are still in the map are not closed; vanilla closes them.

| Check | Before fix | After fix |
|---|---|---|
| Case 4, portal/rejoin + 10 s wait | PASS 2/2 | not run |
| Case 5, portal only + immediate stop | PASS 2/2 | not run |
| Case 3, portal/rejoin + immediate stop | FAIL 1/1, RejectedExecutionException | PASS 3/3, no exception, folders clean on next start |
| GameTest `removed_nether_closes_chunk_worker` | FAIL (worker open) | PASS |
| GameTest `removed_level_folder_stays_deleted` | PASS 2/2 | PASS |
| Full build | - | PASS (96 unit, all GameTests) |

Case 5 shows that the portal alone does not trigger the race; the death/rejoin step is necessary.
The folder test does not reproduce a late write before the fix. It is a guard only.
Evidence: `tools/evidence/T5-shutdown-{before,baseline,after}-*` and `T5-shutdown-regression-selection.txt`.
