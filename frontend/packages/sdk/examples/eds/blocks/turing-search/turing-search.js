/*
 * Adobe Edge Delivery Services (EDS) block — Turing semantic search.
 *
 * Demonstrates consuming `@viglet/turing-sdk` from a vanilla EDS block. The
 * SDK is vendored into the project's `scripts/` folder as `turing-sdk.js`
 * (the self-contained ESM build output) — adjust the import path / CDN URL to
 * match your project layout.
 *
 * T714 — the block runs **POST-native** (the SDK default): search goes out as
 * a JSON body and comes back link-free, so facets and pagination are rendered
 * from structured state (`item.selected`, `page.current`) and a click is a call
 * to `toggleFacet` / `goToPage` — never a URL to parse. A facet value
 * containing `&`, `=`, `+`, `#`, `%` or a space therefore cannot be mis-read as
 * query structure.
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
    <div class="turing-search__body">
      <aside class="turing-search__facets"></aside>
      <ul class="turing-search__results"></ul>
    </div>
    <nav class="turing-search__pagination"></nav>
  `;
  block.append(root);

  const form = root.querySelector(".turing-search__form");
  const input = root.querySelector(".turing-search__input");
  const suggestionsEl = root.querySelector(".turing-search__suggestions");
  const statusEl = root.querySelector(".turing-search__status");
  const facetsEl = root.querySelector(".turing-search__facets");
  const resultsEl = root.querySelector(".turing-search__results");
  const paginationEl = root.querySelector(".turing-search__pagination");

  /** The structured facet item / page a rendered button stands for. */
  const facetItemByButton = new WeakMap();
  const pageByButton = new WeakMap();

  // ── Render whenever search state changes. ──
  search.subscribe((state) => {
    // POST-native: the count lives on the structured response, not `state.data`.
    const count = state.structured?.queryContext?.count ?? state.documents.length;
    statusEl.textContent =
      state.status === "loading"
        ? "Searching…"
        : state.status === "error"
          ? `Error: ${state.error ?? "unknown"}`
          : state.documents.length
            ? `${count} result(s)`
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

    renderFacets(state.structured);
    renderPagination(state.structured);
  });

  /**
   * Facets straight from structured state: `item.selected` drives the checkbox
   * and `toggleFacet` applies the item's own ADD / REMOVE / REPLACE action, so
   * single-select fields behave correctly without the block knowing the rule.
   */
  function renderFacets(structured) {
    facetsEl.innerHTML = "";
    const facets = structured?.facets ?? [];
    if (!facets.length) return;

    if (structured.selectedFilterQueries?.length) {
      const clearAll = document.createElement("button");
      clearAll.type = "button";
      clearAll.className = "turing-search__clear-all";
      clearAll.textContent = `Clear all filters (${structured.selectedFilterQueries.length})`;
      clearAll.addEventListener("click", () => search.clearAllFilters());
      facetsEl.append(clearAll);
    }

    for (const facet of facets) {
      const group = document.createElement("section");
      group.className = "turing-search__facet";
      const title = document.createElement("h3");
      title.textContent = facet.label || facet.name;
      group.append(title);

      const list = document.createElement("ul");
      for (const item of facet.items) {
        const li = document.createElement("li");
        const button = document.createElement("button");
        button.type = "button";
        button.className = "turing-search__facet-item";
        button.setAttribute("aria-pressed", String(item.selected));
        button.textContent = `${item.label || item.value} (${item.count})`;
        facetItemByButton.set(button, item);
        li.append(button);
        list.append(li);
      }
      group.append(list);
      facetsEl.append(group);
    }
  }

  /** Pagination as data: `page.page` goes on the request, there is no href. */
  function renderPagination(structured) {
    paginationEl.innerHTML = "";
    for (const page of structured?.pagination ?? []) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "turing-search__page";
      button.textContent = page.text;
      button.disabled = page.current;
      button.setAttribute("aria-current", page.current ? "page" : "false");
      pageByButton.set(button, page);
      paginationEl.append(button);
    }
  }

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

  // Facet / pagination navigation: structured state in, controller call out.
  facetsEl.addEventListener("click", (e) => {
    const button = e.target.closest(".turing-search__facet-item");
    const item = button && facetItemByButton.get(button);
    if (item) search.toggleFacet(item);
  });
  paginationEl.addEventListener("click", (e) => {
    const button = e.target.closest(".turing-search__page");
    const page = button && pageByButton.get(button);
    if (page) search.goToPage(page.page);
  });

  // Initial "show all" load.
  search.searchRequest({ q: "*", page: 1, sort: "relevance", locale: "en_US" });
}
