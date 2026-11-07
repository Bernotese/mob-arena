# T5-shutdown-before shutdown comparison

| Case | Check | RejectedExecutionException | Folders clear on next start |
|---|---|---|---|
| 5-portal-only-immediate-1 | PASS | no | yes |
| 5-portal-only-immediate-2 | PASS | no | yes |

Final default smoke: PASS.

The checker rejects every exception and ERROR entry.
Each run uses the shared Gradle lock.
The final default smoke checks cleanup after the last comparison run.
