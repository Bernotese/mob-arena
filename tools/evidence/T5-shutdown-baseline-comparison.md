# T5-shutdown-baseline shutdown comparison

| Case | Check | RejectedExecutionException | Folders clear on next start |
|---|---|---|---|
| 3-portal-rejoin-immediate-1 | FAIL | yes | yes |

Final default smoke: PASS.

The checker rejects every exception and ERROR entry.
Each run uses the shared Gradle lock.
The final default smoke checks cleanup after the last comparison run.
