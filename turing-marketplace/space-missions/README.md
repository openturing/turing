# 🚀 Space Missions — A Flight Manifest of the Cosmos

> _"That's one small query for a user, one giant index for mankind."_

Strap in. This is **Space Missions** — a **Viglet Turing ES** marketplace sample that turns the search bar into **Mission Control**. Apollo 11 is in here. So is Voyager 1, still whispering at the edge of the heliosphere. ISS Expedition 1. Chandrayaan. Perseverance. The triumphs, the losses, the ongoing.

The interface borrows from the glowing cyan of old HUD displays: starfields drift across the hero, cards scan like telemetry readouts, and every mission opens into a **Mission Telemetry** sidebar that reads like a flight plan ripped from a console at Houston.

---

## 🛰 What This Project Is

A plug-and-play **Turing marketplace bundle** that ships with:

- A **complete SN site** — schema, facets, locales, ready to install
- An **archive of real missions** — from the first footsteps on the Moon to the far side of the solar system
- A **React 19 + TypeScript SPA** template with starfield animations, scan-line cards, and a crew-manifest grid that lights up like a launch-day briefing

One import and your Turing instance becomes a launchpad: semantic search over mission briefs, faceted filtering by agency and decade, and detail pages that feel like classified dossiers.

---

## 🎯 Signature Features

| Feature | What It Does |
|--------|--------------|
| **Full-text + semantic search** | Autocomplete, search history, and suggestions that actually understand _"first spacewalk"_ vs _"first woman in space"_ |
| **Facet the void** | Filter by _Agency_ (NASA, ESA, Roscosmos, SpaceX, JAXA, ISRO, CNSA), _Program_ (Apollo, Voyager, ISS, Artemis…), _Outcome_ (Success / Failure / Partial / Ongoing / Planned) and _Decade_ |
| **Mission Telemetry sidebar** | Launch date, duration, destination, crew size, spacecraft, EVA flag — all in a single glance |
| **Crew manifest** | Astronaut names rendered as initials in colored circles, because every crew deserves a roll call |
| **HUD-dark mode by default** | Because command centers are never lit like offices |
| **Animated starfield** | Twinkle rate: ideal for late-night spelunking through orbital history |

---

## 📡 The Data Model

Each mission carries a full flight record:

- `title`, `description`, `text` — mission name, tagline, and briefing
- `launch_date`, `duration_days`, `decade` — when and how long
- `agency[]`, `program` — who flew it, and under which banner
- `crew[]`, `crew_size`, `spacecraft` — human factor + hardware
- `destination[]` — Moon, Mars, Jupiter, LEO, interstellar space…
- `outcome` — color-coded badge (Success / Failure / Partial / Ongoing / Planned)
- `has_eva` — did someone step outside?

---

## 🚀 Getting Started

```bash
# From turing-marketplace/space-missions/app/
npm install
npm run dev        # local preview
npm run compile    # production build
npm run package    # build + zip ready for Marketplace import
```

The generated `space-missions.zip` contains the SN site export, the content index, and the compiled SPA template. Import via **Turing → Marketplace → Install from file** and you are go for launch.

---

## 🔧 Tech Stack

- React 19 · TypeScript · Vite 8
- Tailwind CSS 4 with cyan/blue HUD gradients
- Tabler Icons
- `@viglet/turing-react-sdk` for the search primitives
- Viglet Turing ES at the core

---

## 🏛 License

Apache 2.0 — fork it, remix it, launch your own.

> _T-minus zero. Ignition. The index is go._
