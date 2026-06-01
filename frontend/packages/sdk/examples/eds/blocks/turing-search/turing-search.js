/*
 * Adobe Edge Delivery Services (EDS) block — Turing semantic search.
 *
 * Demonstrates consuming `@viglet/turing-sdk` from a vanilla EDS block. The
 * SDK is vendored into the project's `scripts/` folder as `turing-sdk.js`
 * (the self-contained ESM build output) — adjust the import path / CDN URL to
 * match your project layout.
 *
 * Authoring: place a block named "turing-search" whose first row holds two
 * cells — the Turing API base URL and the SN site name:
 *
 *   | turing-search                                   |
 *   | https://turing.example.com/api | my-site        |
 *
 * @since 2026.3.1
 */
import {
  createTuringClient,
  createSearchController,
  createAutoComplete,
} from "../../scripts/turing-sdk.js";

export default function decorate(block) {
  // ── Read block config (baseURL + site) from the first authored row. ──
  const cells = block.querySelectorAll(":scope > div > div");
  const baseURL = cells[0]?.textContent.trim() || "http://localhost:2700/api";
  const site = cells[1]?.textContent.trim() || "sample";
  block.textContent = "";

  const client = createTuringClient({ baseURL });
  const search = createSearchController(client, { site, locale: "en_US" });
  const autocomplete = createAutoComplete(client, { site, locale: "en_US" }, 250);

  // ── Build the DOM skeleton. ──
  const root = document.createElement("div");
  root.className = "turing-search";
  root.innerHTML = `
    <form class="turing-search__form" role="search">
      <input class="turing-search__input" type="search" placeholder="Search…" autocomplete="off" />
      <ul class="turing-search__suggestions" hidden></ul>
    </form>
    <p class="turing-search__status" aria-live="polite"></p>
    <ul class="turing-search__results"></ul>
  `;
  block.append(root);

  const form = root.querySelector(".turing-search__form");
  const input = root.querySelector(".turing-search__input");
  const suggestionsEl = root.querySelector(".turing-search__suggestions");
  const statusEl = root.querySelector(".turing-search__status");
  const resultsEl = root.querySelector(".turing-search__results");

  // ── Render whenever search state changes. ──
  search.subscribe((state) => {
    statusEl.textContent =
      state.status === "loading"
        ? "Searching…"
        : state.status === "error"
          ? `Error: ${state.error ?? "unknown"}`
          : state.documents.length
            ? `${state.data?.queryContext.count ?? state.documents.length} result(s)`
            : state.status === "success"
              ? "No results"
              : "";

    resultsEl.innerHTML = "";
    for (const doc of state.documents) {
      const li = document.createElement("li");
      li.className = "turing-search__result";
      const link = document.createElement("a");
      link.href = doc.url || "#";
      link.textContent = doc.title || doc.url || "(untitled)";
      const desc = document.createElement("p");
      desc.textContent = doc.description || "";
      li.append(link, desc);
      resultsEl.append(li);
    }
  });

  // ── Render autocomplete suggestions. ──
  autocomplete.subscribe((state) => {
    suggestionsEl.innerHTML = "";
    suggestionsEl.hidden = state.suggestions.length === 0;
    for (const s of state.suggestions) {
      const li = document.createElement("li");
      li.textContent = s;
      li.addEventListener("mousedown", () => {
        input.value = s;
        autocomplete.clear();
        search.searchQuery(s);
      });
      suggestionsEl.append(li);
    }
  });

  // ── Wire events. ──
  input.addEventListener("input", () => autocomplete.fetch(input.value));
  input.addEventListener("blur", () => setTimeout(() => autocomplete.clear(), 150));
  form.addEventListener("submit", (e) => {
    e.preventDefault();
    autocomplete.clear();
    search.searchQuery(input.value || "*");
  });

  // ── Facet / pagination navigation: delegate Turing href links. ──
  resultsEl.addEventListener("click", (e) => {
    const a = e.target.closest("a[data-turing-href]");
    if (!a) return;
    e.preventDefault();
    search.navigate(a.getAttribute("data-turing-href"));
  });

  // Initial "show all" load.
  search.search({ q: "*", p: "1", sort: "relevance", _setlocale: "en_US" });
}
