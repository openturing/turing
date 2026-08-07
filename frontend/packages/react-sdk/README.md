# @viglet/turing-react-sdk

React SDK for **Viglet Turing ES** — headless hooks and UI components for building custom search experiences on top of the Turing Semantic Navigation API.

## Installation

```bash
npm install @viglet/turing-react-sdk
```

### Peer Dependencies

The SDK requires React 18+ and axios to be installed in your project:

```bash
npm install react react-dom axios
```

---

## Quick Start

Wrap your app with `TuringProvider` and use hooks to search:

```tsx
import { TuringProvider, useTuringSearch } from "@viglet/turing-react-sdk";

function App() {
  return (
    <TuringProvider config={{ site: "my-site", locale: "en_US" }}>
      <SearchPage />
    </TuringProvider>
  );
}

function SearchPage() {
  const { documents, searchQuery, status } = useTuringSearch();

  return (
    <div>
      <input
        placeholder="Search..."
        onKeyDown={(e) => {
          if (e.key === "Enter") searchQuery(e.currentTarget.value);
        }}
      />
      {status === "loading" && <p>Loading...</p>}
      {documents.map((doc) => (
        <div key={doc.url}>
          <h3>{doc.title}</h3>
          <p>{doc.description}</p>
        </div>
      ))}
    </div>
  );
}
```

---

## API Reference

### TuringProvider

Context provider that configures the Turing connection for all child hooks.

```tsx
<TuringProvider config={{
  site: "my-site",       // SN site name (required)
  locale: "en_US",       // Default locale (optional)
  sort: "relevance",     // Default sort (optional)
}}>
  {children}
</TuringProvider>
```

| Prop | Type | Description |
|------|------|-------------|
| `config.site` | `string` | Semantic Navigation site name (required) |
| `config.locale` | `string` | Default locale, e.g. `"en_US"`, `"pt_BR"` |
| `config.sort` | `string` | Default sort criteria, e.g. `"relevance"` |

> The SDK uses the globally configured `axios` instance from your app, inheriting `baseURL`, CSRF headers, and interceptors. Configure axios before rendering:
> ```ts
> import axios from "axios";
> axios.defaults.baseURL = "http://localhost:2700/api";
> ```

---

### Two response shapes — read `structured`, not `data`

Since **T710 the SDK is POST-native by default**: it POSTs a JSON request and the
server answers with the **link-free** (`STRUCTURED`) response. In that mode:

| Field | POST-native (default) | Legacy (`urlSync.postNative: false`) |
| ----- | --------------------- | ------------------------------------ |
| `structured` | the response | `null` |
| `data` | **`null`** | the response |
| `data.widget.facet` | **throws** — there is no `data` to reach through | the facet groups |

The two ways to write that read fail differently, and the difference is the
point:

- `data.widget.facet.map(…)` **throws** a `TypeError` on the first render.
  `data` is `null`, so the property access never gets as far as `widget`. Loud,
  and you find out immediately.
- `data?.widget?.facet.map(…)` (or `?? []`) returns `undefined`. The optional
  chain short-circuits the whole expression, so the page renders **an empty
  facet rail and no error** — this is the one that ships.

Two ways to avoid writing either:

- inside `<TuringProvider urlSync={…}>`, use the derived hooks
  (`useTuringFacets`, `useTuringPagination`, …) — they have resolved both shapes
  since T710, so your JSX never learns which arrived;
- in manual mode, read `structured` and act through `toggleFacet` / `clearFacet` /
  `goToPage`, which carry no URL. `navigate(href)` is the legacy path.

`toSearchView(store)` resolves the rest of the response (site icon, locales,
applied-filter chips, spell-check) the same way, for either shape.

---

### Hooks

#### useTuringSearch

Core search hook for programmatic control. Manages the full lifecycle: query, results, pagination, facets, locale/sort changes, and AI chat.

```tsx
const {
  status,        // "idle" | "loading" | "success" | "error"
  structured,    // TurStructuredSearchResponse | null — the default shape
  data,          // TurSearchResponse | null — the LEGACY (link-bearing) shape
  chat,          // AI chat response (TurChatResponse | null)
  documents,     // ResolvedDocument[] with mapped default fields
  error,         // Error message string | null
  params,        // Current SearchParams
  request,       // TurSearchRequest — POST-native, the source of truth
  search,        // (params: SearchParams) => Promise<void>
  searchRequest, // (request: TurSearchRequest) => Promise<void>
  searchQuery,   // (query: string) => Promise<void>
  toggleFacet,   // (item: TurStructuredFacetItem) => Promise<void>
  clearFacet,    // (facet: TurStructuredFacet) => Promise<void>
  clearAllFilters,   // () => Promise<void>
  applySpellCheck,   // (useOriginal?: boolean) => Promise<void>
  navigate,      // (href: string) => Promise<void> — legacy; parses a URL back
  changeLocale,  // (locale: string) => Promise<void>
  changeSort,    // (sort: string) => Promise<void>
  goToPage,      // (page: number) => Promise<void>
} = useTuringSearch(initialParams?);
```

**Example: Full search page with facets and pagination**

```tsx
function SearchPage() {
  const turing = useTuringSearch({ q: "*" });

  useEffect(() => {
    turing.search({ q: "*", _setlocale: "en_US" });
  }, []);

  if (turing.status === "loading") return <Spinner />;
  if (turing.status === "error") return <p>Error: {turing.error}</p>;
  // Gate on the shape you read. Gating on `data` alone renders nothing
  // POST-native, which is the default.
  if (!turing.structured) return null;

  const { documents, structured } = turing;

  return (
    <div>
      {/* Search bar */}
      <input
        placeholder="Search..."
        onKeyDown={(e) => {
          if (e.key === "Enter") turing.searchQuery(e.currentTarget.value);
        }}
      />

      {/* Results */}
      {documents.map((doc) => (
        <article key={doc.url}>
          <h3>{doc.title}</h3>
          <p>{doc.description}</p>
          {doc.image && <img src={doc.image} alt={doc.title} />}
        </article>
      ))}

      {/* Facets — the item carries its own action, so nothing is parsed
          out of a URL and a value containing `&` cannot break. */}
      {structured.facets?.map((group) => (
        <div key={group.name}>
          <h4>{group.label ?? group.name}</h4>
          {group.items.map((item) => (
            <button
              key={item.filterQuery}
              onClick={() => turing.toggleFacet(item)}
              style={{ fontWeight: item.selected ? "bold" : "normal" }}
            >
              {item.label} ({item.count})
            </button>
          ))}
        </div>
      ))}

      {/* Pagination — by page number, not by href */}
      {structured.pagination?.map((page) => (
        <button
          key={page.page}
          disabled={page.current}
          onClick={() => turing.goToPage(page.page)}
        >
          {page.text}
        </button>
      ))}

      {/* Sort */}
      <select onChange={(e) => turing.changeSort(e.target.value)}>
        <option value="relevance">Relevance</option>
        <option value="newest">Newest</option>
      </select>

      {/* Locale */}
      <button onClick={() => turing.changeLocale("pt_BR")}>Portugues</button>
      <button onClick={() => turing.changeLocale("en_US")}>English</button>
    </div>
  );
}
```

---

#### useTuringUrlSearch

All-in-one hook that **syncs search state with the browser URL**. The URL is the single source of truth — any change to query params triggers a search, and every action updates the URL automatically.

Works with `react-router-dom`'s `useSearchParams`, keeping the SDK router-agnostic.

```tsx
const {
  status,         // "idle" | "loading" | "success" | "error"
  structured,     // TurStructuredSearchResponse | null — the default shape
  data,           // TurSearchResponse | null — the LEGACY shape
  chat,           // AI chat response
  documents,      // ResolvedDocument[]
  error,          // Error message
  params,         // Current SearchParams (derived from URL)
  inputValue,     // Controlled input value
  setInputValue,  // Update input without searching
  submitSearch,   // Submit current inputValue as search
  toggleFacet,    // Toggle a facet value (no URL)
  clearFacet,     // Clear one facet's selections
  clearAllFilters,// Clear every filter
  goToPage,       // Go to a page by number
  navigate,       // Legacy: navigate a Turing href
  setLocale,      // Change locale
  setSort,        // Change sort
  showAll,        // Reset to q=* (show all)
} = useTuringUrlSearch(searchParams, setSearchParams);
```

> `useTuringUrlSearch` **is** the shared store, so it requires
> `<TuringProvider urlSync={{ searchParams, setSearchParams }}>` and every derived
> hook (`useTuringFacets`, `useTuringPagination`, …) reads the same state.

**Example: URL-synced search page**

```tsx
import { useSearchParams } from "react-router-dom";
import {
  TuringProvider,
  useTuringFacets,
  useTuringUrlSearch,
} from "@viglet/turing-react-sdk";

function SearchContent() {
  const turing = useTuringUrlSearch();
  // Resolved from whichever shape arrived: each item's `toggle` is a filter-query
  // change POST-native and an href navigation on the legacy transport.
  const { facetGroups, hasSelected, clearAll } = useTuringFacets();

  return (
    <div>
      <input
        value={turing.inputValue}
        onChange={(e) => turing.setInputValue(e.target.value)}
        onKeyDown={(e) => e.key === "Enter" && turing.submitSearch()}
      />
      <button onClick={turing.submitSearch}>Search</button>
      <button onClick={turing.showAll}>Show All</button>

      {turing.documents.map((doc) => (
        <div key={doc.url}>
          <h3>{doc.title}</h3>
          <p>{doc.description}</p>
        </div>
      ))}

      {/* Facets — clicking updates the URL automatically */}
      <button onClick={clearAll} disabled={!hasSelected}>Clear all</button>
      {facetGroups.map((group) => (
        <div key={group.name}>
          <h4>{group.label}</h4>
          {group.facets.map((facet) => (
            <button key={facet.label} onClick={facet.toggle}>
              {facet.selected ? "✕ " : ""}{facet.label} ({facet.count})
            </button>
          ))}
        </div>
      ))}
    </div>
  );
}

export default function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  return (
    <TuringProvider
      config={{ site: "my-site", locale: "en_US" }}
      // Required by useTuringUrlSearch and every derived hook: it is what creates
      // the shared store they read.
      urlSync={{ searchParams, setSearchParams: (s) => setSearchParams(s) }}
    >
      <SearchContent />
    </TuringProvider>
  );
}
```

When the user searches for "dragon", the URL becomes `?q=dragon`. Clicking a facet appends `&fq[]=element:Fire`. Bookmarkable, shareable, and back/forward compatible.

---

#### useTuringAutoComplete

Autocomplete hook with built-in debouncing.

```tsx
const {
  suggestions,  // string[]
  isLoading,    // boolean
  fetch,        // (query: string, extraParams?) => void
  clear,        // () => void
} = useTuringAutoComplete(debounceMs?);
```

**Example: Search input with suggestions dropdown**

```tsx
function SearchWithSuggestions() {
  const { suggestions, fetch, clear } = useTuringAutoComplete(300);
  const [query, setQuery] = useState("");

  return (
    <div>
      <input
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          fetch(e.target.value);
        }}
        onBlur={() => clear()}
      />
      {suggestions.length > 0 && (
        <ul>
          {suggestions.map((s) => (
            <li key={s} onMouseDown={() => setQuery(s)}>{s}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
```

---

#### useTuringSortOptions

Fetches available sort options for the current site.

```tsx
const { sortOptions, isLoading } = useTuringSortOptions();

// sortOptions: TurSortOption[] = [{ value: "relevance", label: "Relevance" }, ...]
```

---

### UI Components

Pre-built unstyled components for common search patterns. Use render props to customize the appearance.

#### TuringSearchBar

```tsx
import { TuringSearchBar } from "@viglet/turing-react-sdk";

<TuringSearchBar
  onSearch={(query) => turing.searchQuery(query)}
  placeholder="Search products..."
  renderInput={(props) => <input {...props} className="my-input" />}
  renderButton={({ onClick }) => <button onClick={onClick}>Go</button>}
/>
```

#### TuringResultList

```tsx
import { TuringResultList } from "@viglet/turing-react-sdk";

<TuringResultList
  documents={turing.documents}
  renderItem={({ document }) => (
    <div>
      <h3>{document.title}</h3>
      <p>{document.description}</p>
    </div>
  )}
/>
```

#### TuringPagination

```tsx
import { TuringPagination, useTuringPagination } from "@viglet/turing-react-sdk";

// `pages` resolves both response shapes; `data.pagination` is null POST-native.
const { pages } = useTuringPagination();

<TuringPagination
  items={pages.map((page) => page.raw)}
  onSelectPage={(page) => pages.find((p) => p.page === page)?.select()}
  itemComponent={({ item, isCurrent, onClick }) => (
    <button onClick={onClick} disabled={isCurrent}>
      {item.text}
    </button>
  )}
/>
```

---

### Low-Level API Functions

For advanced use cases, the SDK exports direct API functions:

```tsx
import { fetchSearch, fetchChat, fetchAutoComplete, fetchSortOptions } from "@viglet/turing-react-sdk";

// Search
const results = await fetchSearch("my-site", { q: "dragon", _setlocale: "en_US" });

// AI Chat
const chat = await fetchChat("my-site", { q: "what is a dragon?", _setlocale: "en_US" });

// Autocomplete
const suggestions = await fetchAutoComplete("my-site", { q: "dra" });

// Sort options
const sortOptions = await fetchSortOptions("my-site");
```

---

### Types

All TypeScript types are exported for full type safety:

```tsx
import type {
  // Config
  TuringConfig,

  // API Response
  TurSearchResponse,
  TurQueryContext,
  TurDefaultFields,
  TurSearchResults,
  TurDocument,
  TurPaginationItem,

  // Widgets
  TurWidget,
  TurFacetGroup,
  TurFacetItem,
  TurSpellCheck,
  TurLocaleItem,
  TurSortOption,

  // Chat
  TurChatResponse,

  // Helpers
  SearchStatus,
  SearchParams,
  ResolvedDocument,
} from "@viglet/turing-react-sdk";
```

#### ResolvedDocument

Documents returned by hooks have default fields pre-resolved:

```tsx
interface ResolvedDocument {
  url: string;         // Mapped from site's defaultURLField
  title: string;       // Mapped from defaultTitleField
  description: string; // Mapped from defaultDescriptionField
  date: string;        // Mapped from defaultDateField
  image: string;       // Mapped from defaultImageField
  text: string;        // Mapped from defaultTextField
  raw: TurDocument;    // Full raw document with all fields
}
```

Access custom fields via `doc.raw.fields`:

```tsx
const dangerLevel = doc.raw.fields.danger_level;
const elements = doc.raw.fields.element; // string | string[]
```

---

## Using with HashRouter

When using `HashRouter` (e.g. in SPA templates served from `/pages/` or `/sn/`), query params from the server URL need to be transferred into the hash. Add this before `createRoot`:

```tsx
// Transfer server query params into hash for HashRouter
if (window.location.search && !window.location.hash.includes("?")) {
  const params = window.location.search.substring(1);
  const hash = window.location.hash || "#/";
  const separator = hash.includes("?") ? "&" : "?";
  window.history.replaceState(null, "", window.location.pathname + hash + separator + params);
}
```

This allows URLs like `/sn/my-site/?_setlocale=en_US` to work correctly with `useTuringUrlSearch`.

---

## Dynamic Site Resolution

When building reusable SPA templates that can serve multiple sites, resolve the site name from the URL:

```tsx
function resolveSiteName(): string {
  const match = window.location.pathname.match(/^\/sn\/([^/]+)/);
  return match ? match[1] : import.meta.env.VITE_SN_SITE || "default";
}

export default function SearchPage() {
  const site = resolveSiteName();
  return (
    <TuringProvider config={{ site, locale: "en_US" }}>
      <SearchContent />
    </TuringProvider>
  );
}
```

- URL `/sn/mythical-creatures/` resolves site name `mythical-creatures`
- URL `/pages/my-app/` or dev mode falls back to `VITE_SN_SITE` env variable

---

## Examples

The [turing-marketplace](https://github.com/openviglet/turing/tree/2026.2/turing-marketplace) directory contains complete working apps:

| Example | Description |
|---------|-------------|
| **mythical-creatures** | Fantasy bestiary with multi-language, facets, danger ratings, and More Like This |
| **space-missions** | Space exploration with agency filters, starfield animation, and HUD-style UI |
| **vinyl-records** | Record store with album art, star ratings, genre facets, and condition grading |
| **education-portal** | Academic institution search with tabs, sidebar navigation, and Portuguese locale |

---

## License

Apache License 2.0
