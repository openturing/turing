Load a skill's full instructions (its `SKILL.md` body) so you can operate it.

The system prompt lists the available skills with only a short description each — that is a cheap hint, not the instructions. Call `load_skill` **only when** one of those skills, judged by its description, matches the task at hand. Loading a skill you don't need wastes context.

- Pass the skill `name` exactly as listed in the available skills.
- The tool returns the skill's complete instructions (the `SKILL.md` markdown).
- After loading, follow those instructions, and use `skill_bash` to run the skill's scripts, read its bundled reference files under `/skill`, and write output to `/workspace`.

If the name you pass is not among the available skills, the tool tells you which skills are available — pick one of those or proceed without a skill.
