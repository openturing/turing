# 🐉 Mythical Creatures — The Bestiary of Impossible Beasts

> _"Here there be dragons."_ — Every map-maker, eventually.

Welcome, traveler, to the **Bestiary of Impossible Beasts** — a living codex for the strange, the winged, the scaled, and the sleepless. This is a **Viglet Turing ES** marketplace sample that turns enterprise search into something altogether more _mythical_: a grimoire you can actually query.

Open the tome and you will find dragons hoarding their gold, krakens gnawing at the bones of lost ships, phoenixes smouldering in the corners of the index, and kitsune whose search results always seem to rearrange themselves when you aren't looking.

---

## ✨ What This Project Is

A ready-to-import **search experience** built on top of Viglet Turing ES. It ships with:

- A **complete SN site** (schema, facets, locale configuration)
- **A catalog of creatures** drawn from world folklore — Greek, Norse, Celtic, Chinese, Slavic, Mesoamerican and more
- A **React 19 + TypeScript SPA** template with a mystical purple palette, floating-particle hero, and the iconic **Danger Level bar** that pulses from emerald _(mostly harmless)_ through amber _(keep your distance)_ into blood-red _(flee while you still can)_

Drop it into your Turing instance through the **Marketplace**, click _Install_, and a fully-indexed bestiary springs to life — complete with facets, detail pages, and the unmistakable feeling that something is watching you from the search results.

---

## 🔮 Signature Features

| Feature | What It Does |
|--------|--------------|
| **Full-text + semantic search** | Autocomplete, recent-searches memory, and a query box that rewards the curious |
| **Facet the fantastic** | Filter by _Element_ (Fire, Water, Ice, Shadow, Spirit, Lightning, Poison…), _Origin_ (Greek, Norse, Celtic, Chinese…), and _Creature Type_ (Dragon, Sea Monster, Spirit, Leviathan…) |
| **Danger Level bar** | An animated threat meter scored 0–10, because some beasts deserve a warning in bold |
| **Lore + Abilities + Weaknesses** | Every entry reads like a bestiary page: habitat, era, abilities (amber badges), weaknesses (red badges), and booleans for _shapeshifter_ and _immortal_ |
| **Dark mode by default** | Because midnight is when the index truly speaks |
| **Floating particles** | A quiet, animated hint that the page is alive |

---

## 📖 The Data Model

Each creature carries its own dossier:

- `title`, `description`, `text` — name, tagline, and full lore
- `image` — public-domain artwork
- `first_recorded`, `era` — when humans first wrote about it
- `origin[]`, `creature_type[]`, `element[]` — cultural and taxonomic lineage
- `habitat` — mountains, deep ocean, forest, sky, underworld…
- `danger_level` (0–10), `is_shapeshifter`, `is_immortal`
- `abilities[]`, `weakness[]` — for the hero preparing to hunt it

---

## 🚀 Getting Started

```bash
# From turing-marketplace/mythical-creatures/app/
npm install
npm run dev        # local preview
npm run compile    # production build
npm run package    # build + zip ready for Marketplace import
```

The generated `mythical-creatures.zip` bundles the SN site export, the indexed content, and the compiled SPA template. Import it in **Turing → Marketplace → Install from file**, and the bestiary is yours.

---

## 🧪 Tech Stack

- React 19 · TypeScript · Vite 8
- Tailwind CSS 4 with mystical purple gradients
- Tabler Icons
- `@viglet/turing-react-sdk` for the search primitives
- Viglet Turing ES as the engine underneath it all

---

## 🏛 License

Apache 2.0 — copy, remix, ride a dragon, just don't blame us if the danger level reaches 10.

> _Somewhere in the index, a phoenix is waking up. Query carefully._
