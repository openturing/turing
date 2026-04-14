# 🎵 Vinyl Records — The Crate

> _"The needle drops. The room changes."_

This is **Vinyl Records — The Crate**, a **Viglet Turing ES** marketplace sample that treats search the way a real record shop treats its customers: warm lighting, amber wood tones, and a stubborn belief that _how_ you find music matters as much as what you find.

Flip through **Kind of Blue**, **OK Computer**, **Discovery**, **The Köln Concert**, **Rumours**, **Back to Black**, **In Rainbows**. First pressings, reissues, near-mints and battle-worn Fairs. Every card is a crate you can dig through without smelling the cardboard.

---

## 💿 What This Project Is

A complete **Turing marketplace bundle** that ships with:

- A **full SN site** — schema, facets, locale settings, install-ready
- A **curated record catalog** spanning jazz, rock, electronic, folk, and everything in between, with cover art, artists, labels, and the stories behind each album
- A **React 19 + TypeScript SPA** template with a warm amber-brown palette, a **spinning vinyl** SVG animation on the landing page, condition-graded badges, and a record-shop ambience that makes _"browse"_ feel correct again

Import it into your Turing instance and you get a faceted record catalog, a detail view that reads like a gatefold sleeve, and a search bar that behaves like a knowledgeable clerk.

---

## 🎚 Signature Features

| Feature | What It Does |
|--------|--------------|
| **Full-text + semantic search** | Autocomplete, history, and suggestions that know the difference between _"Miles Davis"_ and _"modal jazz"_ |
| **Crate-dig facets** | Filter by _Genre_ (Jazz, Rock, Electronic, Folk, Soul…), _Decade_ (1950s → 2000s), _Condition_ (Mint → Fair), _Format_ (LP, 2xLP…) and _Label_ (Columbia, Parlophone, ECM, Virgin…) |
| **Condition grading** | Color-coded badges: green _Mint_ → yellow _Very Good_ → red _Fair_. Honesty, basically |
| **First pressing flag** | Because collectors care, and the data should too |
| **Spinning vinyl animation** | Concentric grooves, gold label, 33⅓ rpm of pure visual comfort |
| **Warm dark mode by default** | Because record shops are dimly lit on purpose |

---

## 📝 The Data Model

Each record on the shelf carries its full story:

- `title`, `artist`, `description`, `text` — album name, artist, sleeve-note summary, and the full story
- `cover_art` — the sleeve
- `release_date`, `decade`, `label` — when, and by whom
- `genre[]`, `format` — musical lineage and physical format
- `condition` — Mint / Near Mint / Very Good Plus / Very Good / Good / Fair
- `price`, `rating` (0–5), `num_tracks`, `is_first_pressing`

---

## 🛠 Getting Started

```bash
# From turing-marketplace/vinyl-records/app/
npm install
npm run dev        # local preview
npm run compile    # production build
npm run package    # build + zip ready for Marketplace import
```

The generated `vinyl-records.zip` packs the SN site export, the content index, and the compiled SPA template. Import via **Turing → Marketplace → Install from file** and you are behind the counter.

---

## 🎛 Tech Stack

- React 19 · TypeScript · Vite 8
- Tailwind CSS 4 with warm amber/brown gradients
- Tabler Icons
- `@viglet/turing-react-sdk` for the search primitives
- Viglet Turing ES spinning the whole thing at 33⅓

---

## 🏛 License

Apache 2.0 — copy it, remix it, burn a mixtape.

> _Side A ends. You flip the record. The index keeps playing._
