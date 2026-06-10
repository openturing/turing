Run a bash command inside a skill's sandboxed container to actually operate the skill.

Load the skill first with `load_skill`, then call `skill_bash` with the skill `name` and a `command`.

The container layout:
- `/skill` — the skill folder, mounted **read-only**. Also exposed as `$SKILL_DIR`. Read its `references/`, run its `scripts/`, inspect its `assets/` here.
- `/workspace` — **writable** scratch space and the working directory. It **persists across turns** in the same conversation, so files you write in one command are still there in the next. Write drafts, outputs, and intermediate files here.

Constraints (a hardened sandbox): no network by default, limited CPU/memory/processes, a read-only root filesystem apart from `/workspace` and a small `/tmp`. Commands have a wall-clock timeout.

Tips:
- Quote paths and prefer absolute paths (`/skill/...`, `/workspace/...`).
- To run a bundled script: `python "$SKILL_DIR/scripts/build.py" --out /workspace/result.json`.
- The tool returns the command's combined stdout/stderr plus an exit footer when it fails — read it before deciding the next command.
