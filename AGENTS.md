# AGENTS.md

Rules for all agents in this project: the main session and the background agents.

## Project
- Minecraft mod "mob-arena". Local setup only. No CI.

## Language
- Write all questions, feedback, and answers in ASD-STE100 Simplified Technical English (STE).
  - Use short sentences (max. 20 words for instructions, 25 for descriptions).
  - Write one instruction in one sentence. Use the active voice and the imperative.
  - Use one word for one meaning. Do not use slang.
- Code, comments, and commit messages: use normal English, but keep them short.

## Tools
- You can download and install missing tools and libraries. Do not ask first.
- Write the name and version of each new tool or library in your report.
- Java 25 is in `~/.local/jdks/jdk-25.0.5+8`. Before `./gradlew`, run:
  `export JAVA_HOME=$HOME/.local/jdks/jdk-25.0.5+8 PATH=$HOME/.local/jdks/jdk-25.0.5+8/bin:$PATH`
- `./gradlew build` also runs the GameTests. Report to `orchestrator` with `herdr agent prompt orchestrator "<report>"`.

## Tests
- Test all changes locally before you report "done".
- Use the best available test: unit tests, then `./gradlew build`, then GameTests or `runServer` (headless).
- If you cannot test a change, say why. Do not report an untested change as tested.

## Team setup
- Tool: herdr. Get the current agent names with `herdr agent list`.
- `orchestrator` (tab 1): the main Claude session. It is the only agent that speaks to the user.
- Tab "Agents": `claude-1..3` (top row) and `codex-1..3` (bottom row).
- Background agents do not ask the user. Send questions and blockers to `orchestrator`.

## Who does what
All agents are one shared pool. There are no fixed teams. Send each task to the next free agent of the correct type.
- `orchestrator`: Splits the work into independent tasks. Sends tasks. Merges the results. Speaks to the user.
- Claude agents: Plan unclear or cross-file work. Review all Codex changes. Do research.
- Codex agents: Implement clear tasks, fix bugs, write tests, and run builds.
- Flow for a feature: Claude plans -> Codex implements -> a Claude agent reviews the diff -> orchestrator merges.
- Flow for a small, clear task (one file, known fix): Codex implements -> Claude reviews. Do not plan first.
- Keep agents busy: a Claude agent can plan task B while a Codex agent implements task A.

## Git
- `main` is the merged, tested state. Only `orchestrator` merges into `main`.
- Each agent works in its own worktree and branch:
  `git worktree add ../mob-arena-wt/<agent> -b <agent>/<task>`
- Commit small steps on your branch. Do not edit files outside your worktree.
- Before you report "done", make sure the tests pass on your branch.
- After the merge, `orchestrator` removes the worktree: `git worktree remove ../mob-arena-wt/<agent>`.
