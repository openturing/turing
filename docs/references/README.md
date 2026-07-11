# Reference materials

External/interoperability artifacts kept as living contracts for planned work.

## `semantic-navigation-authority-file-sample.xml`

A real **nstein TME / OpenText Semantic Navigation** *authority file* (a
microthesaurus), namespace `http://dev.nstein.com/tme-authority-file/4.10`. This
is the reconstructed source contract for **Block AQ** (Knowledge Base /
microthesauri) — the old Turing importer for this format is unrecoverable (shallow
clone, below the git graft boundary), so this sample **is** the specification the
T673 XSD/DTD and XML importer are authored against, and the format the T675
LLM-assisted generation emits.

Verified structure (253 terms): `authorityFile` → `name` + `terms`; each `term`
has `id`, `name` (preferred label), `enabled`, `variations` (surface forms with
`weight`/`case`/`accent`/`language`), `relations`, and an (empty) `attributes`
slot. Relation `type` values: `BT` broader · `NT` narrower · `RT` related ·
`UF` used-for · `U` use — with `BT`↔`NT` and `U`↔`UF` stored as reciprocal
inverse pairs. See [ADR 0003](../adr/0003-knowledge-base-microthesaurus.md).
