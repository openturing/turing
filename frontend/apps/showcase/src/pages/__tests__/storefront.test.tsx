import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ThemeProvider } from "@/components/theme-provider";
import { CartProvider } from "@/contexts/cart";
import StorefrontPage from "../storefront";

/**
 * T979 / §LXXXVIII — the storefront renders.
 *
 * <p>CI proved this app compiles and `TurShowcaseSmokeIT` proves the backend
 * contract it reads. Between those sits the page a visitor meets, and until now
 * nothing looked at it — which matters here more than in most apps, because this
 * is the one people clone to decide whether the platform is worth an afternoon.
 *
 * <p>Deliberately a <b>smoke</b>, and meant to stay one. It stubs the single
 * network boundary the SDK uses — the shared `axios` singleton its `core/api`
 * imports — and asserts the four things whose absence means a blank page: the
 * search field, the product grid, a facet, and the Ask Atlas launcher. It does
 * not assert copy, layout or behaviour; those belong to whoever changes them.
 */
const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("axios", () => ({
  default: { get, post, defaults: { baseURL: "" } },
  get,
  post,
}));

/**
 * The minimum `resolveDocuments` needs to project a document, taken from the
 * SDK's own provider test rather than invented here: the default-field mapping
 * plus the raw fields a `ProductCard` reads.
 */
const SEARCH_RESPONSE = {
  queryContext: {
    defaultFields: {
      url: "url",
      title: "title",
      description: "description",
      date: "publication_date",
      image: "image",
      text: "text",
    },
    count: 2,
    page: 1,
    pageCount: 1,
  },
  // The POST-native transport carries facets here, not on `queryContext` —
  // `useTuringFacets` reads `structured.facets` and falls back to the legacy
  // widget shape only when this is absent.
  facets: [
    {
      name: "brand",
      label: "Brand",
      multiValued: true,
      items: [{ label: "Meridian", count: 1, selected: false }],
    },
  ],
  results: {
    document: [
      {
        fields: {
          url: "/p/aurora-lamp",
          title: "Aurora Desk Lamp",
          description: "Warm, dimmable, and quietly overengineered.",
          text: "Aurora Desk Lamp",
          brand: "Meridian",
          category: "Lighting",
          price: "129.00",
          currency: "USD",
        },
      },
      {
        fields: {
          url: "/p/tidal-speaker",
          title: "Tidal Bookshelf Speaker",
          description: "Two-way, ported, unreasonably loud for its size.",
          text: "Tidal Bookshelf Speaker",
          brand: "Harbour",
          category: "Audio",
          price: "349.00",
          currency: "USD",
        },
      },
    ],
  },
};

/** A fresh clone has no agent, so this is the state the demo actually boots in. */
const CHAT_DISABLED = { enabled: false, reason: "NO_GENAI" };

/**
 * The page issues several GETs through the same helper, and answering them all
 * with one body is how the first run of this test produced
 * `sortOptions.map is not a function` — a stub that lies about the shape fails
 * somewhere unrelated to what it stubbed.
 */
function getByUrl(url: string) {
  if (url.includes("/chat/enabled")) return { data: CHAT_DISABLED };
  if (url.includes("/search/sort-options")) {
    return { data: [{ label: "Relevance", sort: "relevance", selected: true }] };
  }
  return { data: {} };
}

/**
 * The same providers `App.tsx` puts above this page — theme and cart — so the
 * smoke exercises the page as the app mounts it rather than a version of it
 * that only exists in a test.
 */
function renderStorefront() {
  return render(
    <MemoryRouter>
      <ThemeProvider defaultTheme="light" storageKey="atlas-store-theme">
        <CartProvider>
          <StorefrontPage />
        </CartProvider>
      </ThemeProvider>
    </MemoryRouter>,
  );
}

describe("StorefrontPage", () => {
  beforeEach(() => {
    post.mockResolvedValue({ data: SEARCH_RESPONSE });
    get.mockImplementation((url: string) => Promise.resolve(getByUrl(url)));
  });

  it("renders the products the search returned", async () => {
    renderStorefront();

    expect(await screen.findByText("Aurora Desk Lamp")).toBeInTheDocument();
    expect(screen.getByText("Tidal Bookshelf Speaker")).toBeInTheDocument();
  });

  it("renders the facet the response declared", async () => {
    renderStorefront();

    // The page mounts two FacetSidebars — the desktop rail and the mobile
    // sheet — so the facet legitimately appears more than once.
    const facets = await screen.findAllByText("Meridian");
    expect(facets.length).toBeGreaterThan(0);
  });

  it("renders the search field", async () => {
    renderStorefront();

    await waitFor(() => expect(post).toHaveBeenCalled());
    expect(screen.getAllByPlaceholderText(/Search products/i).length).toBeGreaterThan(0);
  });

  /**
   * The chat is the one surface a fresh clone cannot light up, so the launcher
   * has to be there to say so rather than absent.
   */
  it("offers the Ask Atlas launcher even with no agent configured", async () => {
    renderStorefront();

    await waitFor(() => expect(post).toHaveBeenCalled());
    expect(screen.getByRole("button", { name: /atlas/i })).toBeInTheDocument();
  });

  /** A search that fails must degrade, not blank the page. */
  it("still renders its chrome when the search call fails", async () => {
    post.mockRejectedValue(new Error("backend down"));

    renderStorefront();

    await waitFor(() => expect(post).toHaveBeenCalled());
    expect(screen.getAllByPlaceholderText(/Search products/i).length).toBeGreaterThan(0);
  });
});
