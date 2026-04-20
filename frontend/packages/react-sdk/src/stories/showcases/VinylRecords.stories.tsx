import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";
import {
  resolveDocsMock,
  vinylDocuments,
  vinylFacets,
} from "../__mocks__/fixtures";
import type { ResolvedDocument } from "../../core/types";

/**
 * # 🎵 Vinyl Records Showcase
 *
 * Full search page inspired by `turing-marketplace/vinyl-records` — a curated
 * record store with album art, star ratings, condition badges, and genre facets.
 *
 * ## SDK Components Used
 * - `TuringProvider` + `useTuringFacets` for genre/condition filtering
 * - `useTuringDocument` for typed field access (artist, genre, rating, price, etc.)
 * - `TuringSearchField` for search with autocomplete
 * - `TuringResultList` for album cards
 *
 * ## Theme Details
 * - Warm amber/gold palette on dark background
 * - Album cover art with aspect-ratio 1:1
 * - Star rating display (★ ☆)
 * - Condition badges (Mint → green, Good Plus → orange)
 * - Price badges and "1st Press" indicators
 */

function StarRating({ rating }: { rating: number }) {
  const stars = Math.round(rating);
  return (
    <div style={{ display: "flex", alignItems: "center", gap: "2px" }}>
      {Array.from({ length: 5 }, (_, i) => (
        <span
          key={i}
          style={{
            fontSize: "14px",
            color: i < stars ? "#f59e0b" : "#334155",
          }}
        >
          {i < stars ? "★" : "☆"}
        </span>
      ))}
      <span style={{ marginLeft: "4px", fontSize: "11px", fontFamily: "monospace", color: "#94a3b8" }}>
        {rating}
      </span>
    </div>
  );
}

const conditionColors: Record<string, { bg: string; text: string }> = {
  Mint: { bg: "#10b98120", text: "#10b981" },
  "Near Mint": { bg: "#10b98115", text: "#34d399" },
  "Very Good Plus": { bg: "#3b82f620", text: "#60a5fa" },
  "Very Good": { bg: "#eab30820", text: "#fbbf24" },
  "Good Plus": { bg: "#f9731620", text: "#fb923c" },
  Good: { bg: "#f9731615", text: "#fdba74" },
  Fair: { bg: "#ef444415", text: "#f87171" },
};

function AlbumCard({ doc }: { doc: ResolvedDocument }) {
  const fields = doc.raw.fields;
  const artist = (fields.artist as string) ?? "";
  const genres = Array.isArray(fields.genre) ? (fields.genre as string[]) : fields.genre ? [fields.genre as string] : [];
  const condition = (fields.condition as string) ?? "";
  const price = fields.price as number | undefined;
  const rating = (fields.rating as number) ?? 0;
  const isFirstPressing = fields.is_first_pressing === true || fields.is_first_pressing === "true";
  const cc = conditionColors[condition] ?? { bg: "#f59e0b20", text: "#fbbf24" };

  return (
    <div
      style={{
        borderRadius: "12px",
        border: "1px solid #2a2a2a",
        background: "#1a1a1a",
        overflow: "hidden",
        cursor: "pointer",
        transition: "all 0.3s ease",
      }}
    >
      {/* Cover */}
      <div style={{ position: "relative", aspectRatio: "1", overflow: "hidden", background: "#111" }}>
        {doc.image ? (
          <img src={doc.image} alt={doc.title} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
        ) : (
          <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: "100%", fontSize: "48px", color: "#33333380" }}>
            💿
          </div>
        )}
        {price != null && (
          <div
            style={{
              position: "absolute",
              top: "8px",
              right: "8px",
              padding: "2px 10px",
              borderRadius: "12px",
              background: "rgba(0,0,0,0.75)",
              backdropFilter: "blur(4px)",
              fontSize: "12px",
              fontWeight: 700,
              color: "#fbbf24",
              border: "1px solid rgba(245,158,11,0.3)",
            }}
          >
            ${price}
          </div>
        )}
        {isFirstPressing && (
          <div
            style={{
              position: "absolute",
              top: "8px",
              left: "8px",
              padding: "2px 8px",
              borderRadius: "10px",
              background: "rgba(217,119,6,0.9)",
              fontSize: "10px",
              fontWeight: 700,
              color: "white",
            }}
          >
            🏅 1st Press
          </div>
        )}
      </div>

      <div style={{ padding: "14px" }}>
        <h3 style={{ margin: "0 0 2px", fontSize: "15px", fontWeight: 600, color: "#f1f5f9" }}>
          {doc.title}
        </h3>
        {artist && (
          <p style={{ margin: "0 0 8px", fontSize: "13px", color: "#94a3b8", display: "flex", alignItems: "center", gap: "4px" }}>
            🎵 {artist}
          </p>
        )}
        {rating > 0 && <StarRating rating={rating} />}

        {/* Genre tags */}
        <div style={{ display: "flex", flexWrap: "wrap", gap: "4px", marginTop: "8px" }}>
          {genres.map((g) => (
            <span
              key={g}
              style={{
                padding: "2px 8px",
                borderRadius: "10px",
                fontSize: "10px",
                background: "#262626",
                color: "#a1a1aa",
              }}
            >
              {g}
            </span>
          ))}
          {condition && (
            <span
              style={{
                padding: "2px 8px",
                borderRadius: "10px",
                fontSize: "10px",
                fontWeight: 500,
                background: cc.bg,
                color: cc.text,
              }}
            >
              {condition}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

function VinylStorePage() {
  const allDocs = resolveDocsMock(vinylDocuments);
  const [query, setQuery] = useState("");
  const [selectedFacets, setSelectedFacets] = useState<Set<string>>(new Set());

  const filteredDocs = allDocs.filter((doc) => {
    if (query && !doc.title.toLowerCase().includes(query.toLowerCase()) &&
        !(doc.raw.fields.artist as string || "").toLowerCase().includes(query.toLowerCase())) {
      return false;
    }
    if (selectedFacets.size === 0) return true;
    for (const key of selectedFacets) {
      const [g, v] = key.split(":");
      const f = doc.raw.fields[g];
      if (Array.isArray(f) && f.includes(v)) return true;
      if (f === v) return true;
    }
    return false;
  });

  function toggleFacet(group: string, value: string) {
    const key = `${group}:${value}`;
    setSelectedFacets((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  }

  return (
    <div
      style={{
        minHeight: "100vh",
        background: "linear-gradient(180deg, #0a0a0a, #1a1a1a)",
        color: "#e2e8f0",
        fontFamily: "system-ui, sans-serif",
      }}
    >
      {/* Header */}
      <header
        style={{
          padding: "16px 24px",
          borderBottom: "1px solid #2a2a2a",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <span style={{ fontSize: "20px" }}>🎵</span>
          <span style={{ fontWeight: 700, fontSize: "14px", letterSpacing: "0.05em" }}>
            Vinyl Vault
          </span>
        </div>
        <span style={{ fontSize: "11px", color: "#525252" }}>Powered by Viglet Turing ES</span>
      </header>

      {/* Hero */}
      <section style={{ textAlign: "center", padding: "48px 24px 24px" }}>
        <div style={{ fontSize: "11px", color: "#d97706", textTransform: "uppercase", letterSpacing: "0.15em", marginBottom: "8px" }}>
          Curated Collection
        </div>
        <h1 style={{ margin: "0 0 8px", fontSize: "36px", fontWeight: 800 }}>
          Vinyl Vault
        </h1>
        <p style={{ color: "#737373", maxWidth: "460px", margin: "0 auto 24px", fontSize: "14px" }}>
          Browse rare pressings, classic albums, and hidden gems from every genre.
        </p>

        {/* Search */}
        <div style={{ maxWidth: "460px", margin: "0 auto" }}>
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "8px",
              padding: "10px 16px",
              borderRadius: "12px",
              border: "1px solid #2a2a2a",
              background: "#111111",
            }}
          >
            <span>🔍</span>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search albums, artists..."
              style={{
                flex: 1,
                background: "transparent",
                border: "none",
                outline: "none",
                fontSize: "15px",
                color: "#e2e8f0",
              }}
            />
          </div>
        </div>
      </section>

      {/* Content */}
      <main style={{ display: "flex", gap: "24px", maxWidth: "1000px", margin: "0 auto", padding: "0 24px 48px" }}>
        {/* Sidebar */}
        <aside style={{ width: "200px", flexShrink: 0 }}>
          <div style={{ fontSize: "12px", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.1em", color: "#a1a1aa", marginBottom: "16px" }}>
            Browse By
          </div>
          {vinylFacets.map((group) => (
            <div key={group.name} style={{ marginBottom: "16px" }}>
              <h4
                style={{
                  fontSize: "11px",
                  fontWeight: 700,
                  textTransform: "uppercase",
                  letterSpacing: "0.08em",
                  color: "rgba(217,119,6,0.7)",
                  borderBottom: "1px solid rgba(217,119,6,0.2)",
                  paddingBottom: "6px",
                  marginBottom: "6px",
                }}
              >
                {group.label.text}
              </h4>
              {group.facets.map((facet) => {
                const isSelected = selectedFacets.has(`${group.name}:${facet.label}`);
                return (
                  <button
                    key={facet.label}
                    onClick={() => toggleFacet(group.name, facet.label)}
                    style={{
                      display: "flex",
                      width: "100%",
                      justifyContent: "space-between",
                      padding: "5px 8px",
                      borderRadius: "6px",
                      border: "none",
                      background: isSelected ? "rgba(217,119,6,0.12)" : "transparent",
                      color: isSelected ? "#fbbf24" : "#a1a1aa",
                      fontSize: "13px",
                      cursor: "pointer",
                      marginBottom: "2px",
                      textAlign: "left",
                    }}
                  >
                    <span>{isSelected ? "✕ " : ""}{facet.label}</span>
                    <span style={{ fontSize: "10px", fontFamily: "monospace" }}>{facet.count}</span>
                  </button>
                );
              })}
            </div>
          ))}
        </aside>

        {/* Grid */}
        <div style={{ flex: 1 }}>
          <p style={{ fontSize: "13px", color: "#737373", marginBottom: "16px" }}>
            <strong style={{ color: "#e2e8f0" }}>{filteredDocs.length}</strong> records found
          </p>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))",
              gap: "16px",
            }}
          >
            {filteredDocs.map((doc) => (
              <AlbumCard key={doc.url} doc={doc} />
            ))}
          </div>
          {filteredDocs.length === 0 && (
            <div style={{ textAlign: "center", padding: "48px 0", color: "#525252" }}>
              💿 No records match your search. Try a different artist or genre.
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

const meta: Meta<typeof VinylStorePage> = {
  title: "Showcases/🎵 Vinyl Records",
  component: VinylStorePage,
  tags: ["autodocs"],
  parameters: {
    layout: "fullscreen",
    docs: {
      description: {
        component:
          "Full search page demo inspired by `turing-marketplace/vinyl-records`. Features a warm vinyl-store theme with album art, star ratings, condition grades, and genre/condition facets.",
      },
    },
  },
};

export default meta;
type Story = StoryObj<typeof VinylStorePage>;

export const VinylVault: Story = {
  name: "Vinyl Vault",
};
