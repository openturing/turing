# Contributing to Viglet Turing ES

Thanks for your interest in contributing! Viglet Turing ES is open source under
the [Apache 2.0 license](LICENSE), and contributions of all kinds are welcome —
bug reports, documentation, and code.

By participating you agree to abide by our
[Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to contribute

- **Report a bug** — [open an issue](https://github.com/openviglet/turing-ce/issues)
  using the bug-report template; include version, steps to reproduce, and logs.
- **Request a feature** — open an issue with the feature-request template.
- **Ask a question** — use [Discussions](https://github.com/openviglet/turing-ce/discussions),
  not the issue tracker.
- **Submit code or docs** — open a pull request (see below).

## How releases work (and how your PR ships)

This repository is published as a **clean snapshot at each release**, so its
history is one commit per release rather than a full development log. That does
not change how you contribute: open your pull request here as normal. A
maintainer reviews it, and once accepted it is integrated and ships in a
following release. Because of the snapshot model, please don't be surprised if
your merged change appears as part of a squashed `Release vX.Y.Z` commit rather
than your original commit — your authorship and sign-off are preserved in the
PR record.

## Development setup

Requirements: **Java 21+**, **Maven 3.6+** (the `./mvnw` wrapper is included),
and **Node 20+ with pnpm** for the frontend.

```bash
# Clone
git clone https://github.com/openviglet/turing-ce.git
cd turing-ce

# Full build (backend + frontend)
./mvnw clean install

# Backend-only build/test (skips the frontend npm build)
./mvnw test -pl turing-app -Dskip.npm=true

# Frontend (from the monorepo)
cd frontend
pnpm install
pnpm --filter turing-app build
pnpm --filter turing-app test
```

Run the app locally:

```bash
java -jar turing-app/target/viglet-turing.jar
# console at http://localhost:2700/console
```

## Pull request guidelines

1. Keep PRs focused — one logical change per PR.
2. Match the surrounding code style; add tests where it makes sense.
3. Make sure the build passes (`./mvnw clean install`) before opening the PR.
4. Write a clear description of **what** changed and **why**.
5. **Sign off every commit** — see below.

## Developer Certificate of Origin (DCO)

This project requires a **DCO sign-off** on every commit instead of a separate
CLA. The DCO is a lightweight statement that you wrote the patch, or otherwise
have the right to submit it under the project's Apache 2.0 license. The full
text is in the [`DCO`](DCO) file.

To sign off, add the `-s` (or `--signoff`) flag when you commit:

```bash
git commit -s -m "fix: correct facet count on empty result set"
```

This appends a line to your commit message:

```
Signed-off-by: Your Name <your.email@example.com>
```

Use your real name and a valid email. If you forgot to sign off, fix the most
recent commit with:

```bash
git commit --amend -s --no-edit
```

…or, for several commits, rebase and sign off the range:

```bash
git rebase --signoff main
```

Pull requests whose commits are not signed off cannot be merged.

## License of contributions

By contributing, you agree that your contributions are licensed under the
project's **Apache 2.0** license, as certified by your DCO sign-off.

---

Questions about contributing? Start a
[Discussion](https://github.com/openviglet/turing-ce/discussions). For
commercial support and services, see [SUPPORT.md](SUPPORT.md).
