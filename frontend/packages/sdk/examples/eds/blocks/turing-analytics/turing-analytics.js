/*
 * Adobe Edge Delivery Services (EDS) block — Turing client-side analytics (Block Z).
 *
 * Demonstrates the canonical analytics bus + GA4/GTM bridge from
 * `@viglet/turing-sdk`. The bus emits vendor-neutral events
 * (`turing_search`, `turing_chat_start`, `turing_chat_lead_captured`,
 * `turing_chat_abandoned`, …); `googleAnalyticsSink()` maps them to GA4
 * `gtag('event', …)` AND a GTM `dataLayer.push`, **auto-detecting** an existing
 * `window.gtag` / `window.dataLayer`. On a GTM-tagged or EDS site that already
 * loads analytics, this lights up with zero extra config — no measurement id
 * needed here.
 *
 * The same `analytics` bus is shared by the search and chat controllers, so a
 * visitor's `search → chat → lead` journey is one funnel in GA4 (stitched by
 * the `TUR_SESSION` cookie both controllers key off).
 *
 * Authoring: place a block named "turing-analytics" whose first row holds two
 * cells — the Turing API base URL and the SN site name:
 *
 *   | turing-analytics                                |
 *   | https://turing.example.com/api | my-site        |
 *
 * @since 2026.3.6
 */
import {
  createTuringClient,
  createTuringAnalytics,
  googleAnalyticsSink,
  debugSink,
  createSearchController,
  createChatController,
} from "../../scripts/turing-sdk.js";

export default function decorate(block) {
  const cells = block.querySelectorAll(":scope > div > div");
  const baseURL = cells[0]?.textContent.trim() || "http://localhost:2700/api";
  const site = cells[1]?.textContent.trim() || "sample";
  block.textContent = "";

  // One bus, two sinks: GA4/GTM (auto-detected) + a console tracer so you can
  // confirm events while integrating. Drop `debugSink()` in production.
  const analytics = createTuringAnalytics({
    sinks: [googleAnalyticsSink(), debugSink()],
    context: { site },
  });

  const client = createTuringClient({ baseURL });

  // Pass the SAME bus to both controllers. Search emits turing_search /
  // _no_results / _result_click; chat emits _start / _message_sent / _step /
  // _lead_captured / _abandoned. Goal slots mark a lead conversion.
  const search = createSearchController(client, { site, locale: "en_US" }, undefined, {
    analytics,
  });
  const chat = createChatController(client, {
    site,
    analytics,
    goalSlots: ["email", "phone"],
  });

  // Minimal UI to drive the events.
  const root = document.createElement("div");
  root.className = "turing-analytics";
  root.innerHTML = `
    <form class="turing-analytics__search" role="search">
      <input type="search" placeholder="Search (emits turing_search)…" />
    </form>
    <ul class="turing-analytics__results"></ul>
    <form class="turing-analytics__chat">
      <input type="text" placeholder="Ask the assistant…" />
    </form>
    <p class="turing-analytics__hint">Open the console — every canonical event is traced by <code>debugSink()</code>.</p>
  `;
  block.append(root);

  const results = root.querySelector(".turing-analytics__results");
  search.subscribe((state) => {
    results.innerHTML = "";
    state.documents.forEach((doc, i) => {
      const li = document.createElement("li");
      const a = document.createElement("a");
      a.href = doc.url;
      a.textContent = doc.title || doc.url;
      // One call → server CTR + turing_search_result_click (position is 1-based).
      a.addEventListener("click", () => search.trackResultClick(doc.raw?.metadata?.[0]?.href ?? doc.url, i + 1));
      li.append(a);
      results.append(li);
    });
  });

  root.querySelector(".turing-analytics__search").addEventListener("submit", (e) => {
    e.preventDefault();
    search.searchQuery(e.target.querySelector("input").value);
  });
  root.querySelector(".turing-analytics__chat").addEventListener("submit", (e) => {
    e.preventDefault();
    const input = e.target.querySelector("input");
    chat.send(input.value);
    input.value = "";
  });
}
