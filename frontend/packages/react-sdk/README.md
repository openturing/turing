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

### Hooks

#### useTuringSearch

Core search hook for programmatic control. Manages the full lifecycle: query, results, pagination, facets, locale/sort changes, and AI chat.

```tsx
const {
  status,        // "idle" | "loading" | "success" | "error"
  data,          // Raw TurSearchResponse
  chat,          // AI chat response (TurChatResponse | null)
  documents,     // ResolvedDocument[] with mapped default fields
  error,         // Error message string | null
  params,        // Current SearchParams
  search,        // (params: SearchParams) => Promise<void>
  searchQuery,   // (query: string) => Promise<void>
  navigate,      // (href: string) => Promise<void>
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
  if (!turing.data) return null;

  const { documents, data } = turing;

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

      {/* Facets */}
      {data.widget.facet.map((group) => (
        <div key={group.name}>
          <h4>{group.label.text}</h4>
          {group.facets.map((facet) => (
            <button
              key={facet.link}
              onClick={() => turing.navigate(facet.link)}
              style={{ fontWeight: facet.selected ? "bold" : "normal" }}
            >
              {facet.label} ({facet.count})
            </button>
          ))}
        </div>
      ))}

      {/* Pagination */}
      {data.pagination.map((item, i) => (
        <button
          key={i}
          disabled={!item.href}
          onClick={() => turing.navigate(item.href)}
        >
          {item.text}
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
  data,           // Raw TurSearchResponse
  chat,           // AI chat response
  documents,      // ResolvedDocument[]
  error,          // Error message
  params,         // Current SearchParams (derived from URL)
  inputValue,     // Controlled input value
  setInputValue,  // Update input without searching
  submitSearch,   // Submit current inputValue as search
  navigate,       // Navigate via Turing href (facets, pagination)
  setLocale,      // Change locale
  setSort,        // Change sort
  showAll,        // Reset to q=* (show all)
} = useTuringUrlSearch(searchParams, setSearchParams);
```

**Example: URL-synced search page**

```tsx
import { useSearchParams } from "react-router-dom";
import { TuringProvider, useTuringUrlSearch } from "@viglet/turing-react-sdk";

function SearchContent() {
  const [searchParams, setSearchParams] = useSearchParams();
  const turing = useTuringUrlSearch(searchParams, setSearchParams);

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
      {turing.data?.widget.facet.map((group) => (
        <div key={group.name}>
          <h4>{group.label.text}</h4>
          {group.facets.map((facet) => (
            <button key={facet.link} onClick={() => turing.navigate(facet.link)}>
              {facet.selected ? "✕ " : ""}{facet.label} ({facet.count})
            </button>
          ))}
        </div>
      ))}
    </div>
  );
}

export default function SearchPage() {
  return (
    <TuringProvider config={{ site: "my-site", locale: "en_US" }}>
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
import { TuringPagination } from "@viglet/turing-react-sdk";

<TuringPagination
  items={turing.data?.pagination ?? []}
  onNavigate={turing.navigate}
  renderItem={({ item, isActive, onClick }) => (
    <button onClick={onClick} disabled={isActive}>
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
