# Changesets

This folder is managed by [changesets](https://github.com/changesets/changesets).
It drives **semver** for the publishable workspace packages — currently
`@viglet/turing-react-ui`, `@viglet/turing-react-sdk`, and `@viglet/turing-sdk`.
The apps and marketplace examples are `ignore`d in `config.json` (they are not
published).

## Workflow

1. **Describe a change.** After making a change that should ship, run from
   `frontend/`:

   ```bash
   pnpm changeset
   ```

   Pick the affected package(s), pick the bump (`patch` / `minor` / `major`),
   and write a one-line summary. This writes a markdown file into `.changeset/`
   — commit it with your PR.

2. **Version.** When ready to cut a release, run:

   ```bash
   pnpm version-packages
   ```

   This consumes the pending changeset files, bumps each package's `version`,
   and appends to its `CHANGELOG.md`. Commit the result.

3. **Publish.** `pnpm release` builds and publishes everything that changed to
   npm. In CI this is done by the **Publish react-ui** workflow
   (`.github/workflows/publish-react-ui.yml`).

> Note: the monorepo also has a legacy "bump every workspace by a patch"
> publish flow (`publish-js-sdk.yml`). Changesets is the going-forward,
> per-package semver path introduced with T303 packaging hardening — prefer it
> for the publishable packages.
