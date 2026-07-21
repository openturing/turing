# Reference materials

Interoperability artifacts kept as living contracts for planned work.

## `semantic-navigation-authority-file-sample.xml`

A real-world **controlled-vocabulary microthesaurus** (an *authority file*) used
to design and validate the **Turing Thesaurus Exchange** format — the 100% Turing
XML contract for Block AQ (Knowledge Base / microthesauri), namespace
`https://turing.viglet.org/xsd/thesaurus/1.0`. Classic enterprise
semantic-navigation systems were only a conceptual inspiration; this sample is a
generic thesaurus export whose *shape* the T673 XSD and XML importer are authored
against, and the format the T675 LLM-assisted generation emits.

Verified structure (253 terms): `authorityFile` → `name` + `terms`; each `term`
has `id`, `name` (preferred label), `enabled`, `variations` (surface forms with
`weight`/`case`/`accent`/`language`), `relations`, and an (empty) `attributes`
slot. Relation `type` values: `BT` broader · `NT` narrower · `RT` related ·
`UF` used-for · `U` use — with `BT`↔`NT` and `U`↔`UF` stored as reciprocal
inverse pairs. Formal contract: `turing-app/src/main/resources/kb/turing-thesaurus-1.0.xsd`.
See [ADR 0003](../adr/0003-knowledge-base-microthesaurus.md).

## `model-catalog-api.md` / `model-catalog-pipeline.md`

**Moved 2026-07-21** — the public model catalog API (Block BC) and its
regeneration pipeline (Block BD) now live in the dedicated
[`openviglet/model-catalog`](https://github.com/openviglet/model-catalog) repo.
These two files are now short pointer stubs. Turing consumes the published endpoint
remotely (`turing.model-catalog.url`, default
`https://openviglet.github.io/model-catalog/catalog.json`; remote-only, no bundled
copy). The full contract + maintainer workflow live in that repo's
`docs/references/{api,pipeline}.md`.
