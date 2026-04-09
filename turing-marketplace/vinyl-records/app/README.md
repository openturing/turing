# Vinyl Records — The Crate

A marketplace-style browser for vintage vinyl records, powered by **Viglet Turing ES**.

The interface uses warm amber/brown tones and features a spinning vinyl record SVG animation on the landing page, evoking the tactile feel of digging through record crates.

## Features

- **Full-text search** with autocomplete suggestions and search history
- **Faceted navigation** — filter by Genre (Jazz, Rock, Electronic...), Decade (1950s–2000s), Condition (Mint to Fair), Format (LP, 2xLP), and Label (Columbia, Parlophone, ECM...)
- **Detail page** with cover art hero, star ratings, price display, and a "Record Info" sidebar
- **Condition grading** — color-coded badges from green (Mint) through yellow (Very Good) to red (Fair)
- **Dark/Light mode** toggle with warm dark theme as default
- **Spinning vinyl animation** with concentric grooves and gold label

## Data Model

| Field | Description |
|-------|-------------|
| `title` | Album name |
| `description` | Short summary |
| `text` | The album's story and history |
| `cover_art` | Cover artwork URL |
| `artist` | Artist name |
| `release_date` | Release date (ISO) |
| `label` | Record label |
| `genre` | Musical genres (array) |
| `format` | Vinyl format (LP, 2xLP...) |
| `condition` | Grading — Mint to Fair |
| `price` | Price in USD |
| `rating` | Star rating (0–5) |
| `num_tracks` | Track count |
| `is_first_pressing` | First pressing indicator |
| `decade` | Decade grouping |

## Screenshots

### Search Results
Cards show square cover art with a price badge (top-right) and optional "1st Press" badge (top-left), star ratings, genre and condition badges.

### Detail Page
Hero section with large cover art, star rating, price, and condition. A "Record Info" sidebar lists label, release date, format, tracks, and more. Below is "The Story" — a full narrative about the album.

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
