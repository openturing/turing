# Space Missions

A searchable archive of humanity's greatest space exploration missions, powered by **Viglet Turing ES**.

The interface features a cyberpunk/HUD aesthetic with starfield animations, scan-line cards, and a cyan-blue color palette inspired by mission control displays.

## Features

- **Full-text search** with autocomplete suggestions and search history
- **Faceted navigation** — filter by Agency (NASA, ESA, Roscosmos, SpaceX, JAXA, ISRO, CNSA), Program (Apollo, Voyager, ISS...), Outcome (Success, Failure, Partial, Ongoing, Planned), and Decade
- **Detail page** with mission telemetry sidebar, crew manifest grid, and full mission brief
- **Dark/Light mode** toggle with HUD-themed dark mode as default
- **Starfield animation** with twinkling stars and scan-line card effects

## Data Model

| Field | Description |
|-------|-------------|
| `title` | Mission name |
| `description` | Short summary |
| `text` | Full mission brief |
| `launch_date` | Launch date (ISO) |
| `agency` | Space agency (array) |
| `program` | Mission program (e.g. Apollo, Voyager) |
| `crew` | Astronaut names (array) |
| `crew_size` | Number of crew members |
| `spacecraft` | Vehicle name |
| `outcome` | Mission result — color-coded badge |
| `destination` | Target (array — Moon, Mars, Jupiter...) |
| `duration_days` | Mission duration in days |
| `decade` | Decade grouping |
| `has_eva` | Whether a spacewalk occurred |

## Screenshots

### Search Results
Cards display the mission image with an outcome badge overlay, agency and program badges, and meta icons for launch date, duration, and crew size.

### Detail Page
Full hero image with HUD corner brackets, a "Mission Telemetry" sidebar with all metadata, and a crew manifest showing initials in colored circles.

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
