# Mythical Creatures — The Bestiary

An encyclopedic catalog of mythical creatures from world folklore, powered by **Viglet Turing ES**.

The interface uses a mystical purple palette with floating particle animations and a distinctive danger level bar, evoking the feel of an ancient bestiary brought to life.

## Features

- **Full-text search** with autocomplete suggestions and search history
- **Faceted navigation** — filter by Element (Fire, Water, Earth, Air, Lightning, Shadow, Spirit, Ice, Poison), Origin (Greek, Norse, Celtic, Chinese, East Asian...), and Creature Type (Dragon, Sea Monster, Spirit, Leviathan...)
- **Detail page** with creature lore, abilities, weaknesses, and a "Creature Data" sidebar
- **Danger Level bar** — animated horizontal bar from emerald (safe) through amber (dangerous) to red (catastrophic), scale 0–10
- **Element badges** — each element has its own color
- **Dark/Light mode** toggle with mystical dark theme as default
- **Floating particles** animation on the landing page

## Data Model

| Field | Description |
|-------|-------------|
| `title` | Creature name |
| `description` | Short summary |
| `text` | Full creature lore |
| `image` | Creature artwork URL |
| `first_recorded` | Earliest historical record (ISO) |
| `origin` | Cultural origin (array — Greek, Norse, Chinese...) |
| `creature_type` | Classification (array — Dragon, Spirit...) |
| `element` | Elemental affinity (array) |
| `habitat` | Where it lives (Mountains, Deep Ocean...) |
| `danger_level` | Threat scale 0–10 |
| `is_shapeshifter` | Can change form |
| `is_immortal` | Cannot be killed |
| `abilities` | Special powers (array — Fire Breath, Flight...) |
| `weakness` | Known vulnerabilities (array) |
| `era` | Historical era (Ancient, Medieval...) |

## Screenshots

### Search Results
Cards display creature artwork with a danger badge overlay, element and creature type badges, and a danger level bar at the bottom.

### Detail Page
Hero image with gradient overlay and element/type badges. The main section shows the danger level bar with a textual descriptor, full creature lore, and a "Creature Data" sidebar listing origin, habitat, era, abilities (amber badges), weaknesses (red badges), and boolean traits (shapeshifter, immortal). Creatures with danger level 8+ display a warning: _"Extreme danger. Approach only with divine protection."_

## Getting Started

```bash
# Install dependencies and start dev server
npm install
npm run dev

# Build for production
npm run compile

# Build + generate zip package
npm run package
```

The zip is ready to be imported into Viglet Turing ES via the Marketplace.

## Tech Stack

- React 19 + TypeScript
- Vite 8
- Tailwind CSS 4
- Tabler Icons
- `@viglet/turing-react-sdk`

## License

Apache 2.0
