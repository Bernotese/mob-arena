# T5-start-perf shutdown comparison

| Case | Check | RejectedExecutionException | Folders clear on next start |
|---|---|---|---|
| 3-portal-rejoin-immediate-1 | PASS | no | yes |

Final default smoke: PASS.

The checker rejects every exception and ERROR entry.
Each run uses a shared Gradle build slot (tools/gradle.sh).
The final default smoke checks cleanup after the last comparison run.
