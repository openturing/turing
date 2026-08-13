# `seed/` — platform defaults baked into the image

`seed/defaults/` is zipped at Docker build time into `/app/export/defaults.zip`.
On the very first boot of a fresh database, `TurExportImportOnStartup` (`@Order(2)`)
imports every ZIP under `/app/export/` and then writes the `EXPORT_AUTO_IMPORT`
config marker, so this runs exactly once per database.

## Why the bundle is JSON in git, not a committed `.zip`

The ZIP is a build artifact. Keeping the payload as `export.json` means a change to
the platform defaults shows up as a reviewable diff, and the image build owns the
packaging.

## What lands, and why it is GLOBAL

Both entries omit `tenantId` entirely. Import stamps an infra instance only when it
arrives with no owner (`VigletInfraTenantScope.stampOnImport`), and the startup
import has no tenant bound to the thread — so these land with `tenantId = null`,
which is what makes one seeded default visible to *every* auto-provisioned tenant
(visibility is `owner == null || owner.equals(current)`).

| Entry | Vendor | Path | Needs a secret? |
| ----- | ------ | ---- | --------------- |
| Default Search Engine | `LUCENE` | `./store/lucene-se` | no |
| Default Vector Store | `LUCENE` | `./store/lucene-vector` | no |

Both are embedded and live under `/app/store`, which the image declares as a
`VOLUME` — so the seeded defaults survive container replacement.

## What deliberately is *not* seeded here

**No LLM instance.** An exported LLM carries its API key encrypted with the
source install's `TurSecretCryptoService` key, so a key committed to this
repository would neither be safe nor decryptable anywhere else. The default LLM
is provisioned from the environment instead — `OPENAI_API_KEY` / `GEMINI_API_KEY`,
see `TurLLMInstanceOnStartup`.

## Adding to the seed

Edit `defaults/export.json` — it is a `TurExchange` envelope, the same shape the
admin export produces. Use a fixed UUID per entry: the import is find-or-create by
id, so a stable id keeps re-seeding idempotent. Never add a `tenantId` field.

Deployments that need a *different* seed do not have to rebuild: mount your own
ZIP into `/app/export/` before first boot. Re-seeding an existing database means
clearing the `EXPORT_AUTO_IMPORT` row in `config_var` first.
