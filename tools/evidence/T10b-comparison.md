# T10b shutdown comparison

| Case | Check | RejectedExecutionException | Folders clear on next start |
|---|---|---|---|
| 1-no-game-1 | PASS | no | yes |
| 1-no-game-2 | PASS | no | yes |
| 2-game-wait-10s-1 | PASS | no | yes |
| 2-game-wait-10s-2 | PASS | no | yes |
| 3-portal-rejoin-immediate-1 | FAIL | yes | yes |
| 3-portal-rejoin-immediate-2 | FAIL | yes | yes |

Final default smoke: PASS.

The checker rejects every exception and ERROR entry.
Each run uses the shared Gradle lock.
The final default smoke checks cleanup after the last comparison run.
