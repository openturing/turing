import { act, render, renderHook, screen, waitFor } from "@testing-library/react";
import { useEffect, useState, type ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { TuringProvider } from "../provider";
import { useSearchStore } from "../use-search-store";
import type { SearchParams } from "../api";
import type {
  TurSearchResponse,
  TurStructuredSearchResponse,
} from "../types";

/**
 * T877 / §LXIX.5 — the provider/store contract.
 *
 * This is the surface T710 changed under every consumer: which transport runs,
 * which of `data` / `structured` is authoritative, and that facet and pagination
 * actions now write filter queries straight into the URL instead of parsing a
 * server-rendered href. Only the three network calls are mocked; the pure
 * helpers (`applyFacetAction`, `searchParamsToRequest`) stay real, because they
 * are the semantics under test rather than a boundary to stub.
 */
vi.mock("../api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../api")>();
  return {
    ...actual,
    fetchSearch: vi.fn(),
    postStructuredSearch: vi.fn(),
    fetchChat: vi.fn(),
  };
});

const api = await import("../api");
const fetchSearch = vi.mocked(api.fetchSearch);
const postStructuredSearch = vi.mocked(api.postStructuredSearch);
const fetchChat = vi.mocked(api.fetchChat);

/** The minimum a response needs for `resolveDocuments` to project a document. */
const QUERY_CONTEXT = {
  defaultFields: {
    url: "url",
    title: "title",
    description: "description",
    date: "publication_date",
    image: "image",
    text: "text",
  },
};

const DOCUMENT = {
  fields: { url: "/a", title: "Alpha", description: "First", text: "body" },
};

const STRUCTURED = {
  queryContext: QUERY_CONTEXT,
  results: { document: [DOCUMENT] },
} as unknown as TurStructuredSearchResponse;

const LEGACY = {
  queryContext: QUERY_CONTEXT,
  results: { document: [DOCUMENT] },
} as unknown as TurSearchResponse;

/**
 * Renders the provider against a URLSearchParams the test owns, and records
 * every URL write the store makes. `setSearchParams` receives a query string,
 * which is exactly what the store is contracted to produce.
 */
function makeHarness(initial = "", postNative?: boolean) {
  const writes: string[] = [];

  function Wrapper({ children }: { readonly children: ReactNode }) {
    // Held in state, like a router's useSearchParams: a URL write must feed
    // back into the provider, or the second search of a test never fires.
    const [searchParams, setSearchParams] = useState(
      () => new URLSearchParams(initial),
    );
    return (
      <TuringProvider
        config={{ site: "test-site", locale: "en_US" }}
        urlSync={{
          searchParams,
          setSearchParams: (params: string) => {
            writes.push(params);
            setSearchParams(new URLSearchParams(params));
          },
          ...(postNative === undefined ? {} : { postNative }),
        }}
      >
        {children}
      </TuringProvider>
    );
  }

  return { Wrapper, writes, lastWrite: () => writes[writes.length - 1] };
}

function renderStore(initial = "", postNative?: boolean) {
  const harness = makeHarness(initial, postNative);
  const view = renderHook(() => useSearchStore(), { wrapper: harness.Wrapper });
  return { ...harness, ...view };
}

beforeEach(() => {
  postStructuredSearch.mockResolvedValue(STRUCTURED);
  fetchSearch.mockResolvedValue(LEGACY);
  fetchChat.mockResolvedValue({} as never);
});

describe("useSearchStore outside a urlSync provider", () => {
  it("throws a message naming the missing prop", () => {
    function Bare({ children }: { readonly children: ReactNode }) {
      return (
        <TuringProvider config={{ site: "test-site" }}>{children}</TuringProvider>
      );
    }
    expect(() => renderHook(() => useSearchStore(), { wrapper: Bare })).toThrow(
      /urlSync/,
    );
  });
});

describe("transport selection (T710)", () => {
  it("is POST-native by default: structured is authoritative and data is null", async () => {
    const { result } = renderStore("?q=shoes");

    await waitFor(() => expect(result.current.status).toBe("success"));
    expect(postStructuredSearch).toHaveBeenCalledTimes(1);
    expect(fetchSearch).not.toHaveBeenCalled();
    expect(result.current.structured).toBe(STRUCTURED);
    expect(result.current.data).toBeNull();
    expect(result.current.documents).toEqual([
      expect.objectContaining({ url: "/a", title: "Alpha" }),
    ]);
  });

  it("sends the merged params as a request body, not a query string", async () => {
    const { result } = renderStore("?q=shoes&p=3&fq[]=type:Post&sort=newest");

    await waitFor(() => expect(result.current.status).toBe("success"));
    const [site, request] = postStructuredSearch.mock.calls[0];
    expect(site).toBe("test-site");
    expect(request).toMatchObject({
      q: "shoes",
      page: 3,
      sort: "newest",
      fq: ["type:Post"],
      locale: "en_US",
    });
  });

  it("postNative:false keeps the legacy GET: data is populated and structured is null", async () => {
    const { result } = renderStore("?q=shoes", false);

    await waitFor(() => expect(result.current.status).toBe("success"));
    expect(fetchSearch).toHaveBeenCalledTimes(1);
    expect(postStructuredSearch).not.toHaveBeenCalled();
    expect(result.current.data).toBe(LEGACY);
    expect(result.current.structured).toBeNull();
  });

  it("surfaces a failed search as status error with the thrown message", async () => {
    postStructuredSearch.mockRejectedValueOnce(new Error("boom"));
    const { result } = renderStore("?q=shoes");

    await waitFor(() => expect(result.current.status).toBe("error"));
    expect(result.current.error).toBe("boom");
  });
});

describe("chat companion request", () => {
  it("is skipped for the match-all query", async () => {
    const { result } = renderStore("");

    await waitFor(() => expect(result.current.status).toBe("success"));
    expect(fetchChat).not.toHaveBeenCalled();
    expect(result.current.chat).toBeNull();
  });

  it("runs for a real query, carrying the effective locale", async () => {
    const { result } = renderStore("?q=shoes");

    await waitFor(() => expect(result.current.status).toBe("success"));
    expect(fetchChat).toHaveBeenCalledWith("test-site", {
      q: "shoes",
      _setlocale: "en_US",
    });
  });
});

describe("URL writes", () => {
  it("toggleFacet appends the filter query and resets to page 1", async () => {
    const { result, lastWrite } = renderStore("?q=shoes&p=4");
    await waitFor(() => expect(result.current.status).toBe("success"));

    act(() =>
      result.current.toggleFacet({
        action: "ADD",
        field: "type",
        filterQuery: "type:Post",
      }),
    );

    const written = new URLSearchParams(lastWrite());
    expect(written.getAll("fq[]")).toEqual(["type:Post"]);
    expect(written.get("p")).toBeNull(); // page 1 is the default and is omitted
  });

  it("toggleFacet with REPLACE drops the field's other selections", async () => {
    const { result, lastWrite } = renderStore(
      "?q=shoes&fq[]=type:Post&fq[]=type:Page&fq[]=lang:en",
    );
    await waitFor(() => expect(result.current.status).toBe("success"));

    act(() =>
      result.current.toggleFacet({
        action: "REPLACE",
        field: "type",
        filterQuery: "type:News",
      }),
    );

    expect(new URLSearchParams(lastWrite()).getAll("fq[]")).toEqual([
      "lang:en",
      "type:News",
    ]);
  });

  it("clearFacet removes only that facet's selections", async () => {
    const { result, lastWrite } = renderStore(
      "?q=shoes&fq[]=type:Post&fq[]=lang:en",
    );
    await waitFor(() => expect(result.current.status).toBe("success"));

    act(() =>
      result.current.clearFacet({
        name: "type",
        multiValued: true,
        selectedFilterQueries: ["type:Post"],
        items: [],
      }),
    );

    expect(new URLSearchParams(lastWrite()).getAll("fq[]")).toEqual(["lang:en"]);
  });

  it("clearAllFilters drops every fq", async () => {
    const { result, lastWrite } = renderStore(
      "?q=shoes&fq[]=type:Post&fq[]=lang:en",
    );
    await waitFor(() => expect(result.current.status).toBe("success"));

    act(() => result.current.clearAllFilters());

    expect(new URLSearchParams(lastWrite()).getAll("fq[]")).toEqual([]);
  });

  it("goToPage, setSort and setLocale write their own param and keep the query", async () => {
    const { result, lastWrite } = renderStore("?q=shoes");
    await waitFor(() => expect(result.current.status).toBe("success"));

    act(() => result.current.goToPage(3));
    expect(new URLSearchParams(lastWrite()).get("p")).toBe("3");

    act(() => result.current.setSort("newest"));
    expect(new URLSearchParams(lastWrite()).get("sort")).toBe("newest");

    act(() => result.current.setLocale("pt_BR"));
    const written = new URLSearchParams(lastWrite());
    expect(written.get("_setlocale")).toBe("pt_BR");
    expect(written.get("q")).toBe("shoes");
  });

  it("showAll clears the query back to the match-all default", async () => {
    const { result, lastWrite } = renderStore("?q=shoes&p=4");
    await waitFor(() => expect(result.current.status).toBe("success"));

    act(() => result.current.showAll());

    const written = new URLSearchParams(lastWrite());
    expect(written.get("q")).toBeNull();
    expect(written.get("p")).toBeNull();
  });

  it("submitSearch promotes the input value to the query", async () => {
    const { result, lastWrite } = renderStore("?q=shoes&p=4");
    await waitFor(() => expect(result.current.status).toBe("success"));

    act(() => result.current.setInputValue("boots"));
    act(() => result.current.submitSearch());

    const written = new URLSearchParams(lastWrite());
    expect(written.get("q")).toBe("boots");
    expect(written.get("p")).toBeNull();
  });
});

describe("implicit params", () => {
  /** Registers implicit params once on mount, like useTuringTabs does. */
  function Register({
    keyName,
    params,
  }: {
    readonly keyName: string;
    readonly params: Partial<SearchParams> | null;
  }) {
    const store = useSearchStore();
    const { setImplicitParams } = store;
    useEffect(() => {
      setImplicitParams(keyName, params);
    }, [keyName, params, setImplicitParams]);
    return <span data-testid="status">{store.status}</span>;
  }

  it("fill gaps in the request but never reach the URL", async () => {
    const { Wrapper, writes } = makeHarness("?q=shoes");
    render(
      <Wrapper>
        <Register keyName="tabs" params={{ group: "news", rows: "5" }} />
      </Wrapper>,
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("success"));
    await waitFor(() => expect(postStructuredSearch).toHaveBeenCalledTimes(2));

    const [, request] = postStructuredSearch.mock.calls[1];
    expect(request).toMatchObject({ group: "news", rows: 5 });
    expect(writes).toEqual([]);
  });

  it("never override an explicit URL param", async () => {
    const { Wrapper } = makeHarness("?q=shoes&group=blog");
    render(
      <Wrapper>
        <Register keyName="tabs" params={{ group: "news" }} />
      </Wrapper>,
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("success"));
    const lastCall = postStructuredSearch.mock.calls.at(-1);
    expect(lastCall?.[1]).toMatchObject({ group: "blog" });
  });
});

describe("out-of-order responses", () => {
  it("a slow first search cannot overwrite a newer one", async () => {
    let resolveFirst: (value: TurStructuredSearchResponse) => void = () => {};
    const stale = { ...STRUCTURED, stale: true } as TurStructuredSearchResponse;
    const fresh = { ...STRUCTURED, stale: false } as TurStructuredSearchResponse;

    postStructuredSearch.mockImplementationOnce(
      () => new Promise((resolve) => { resolveFirst = resolve; }),
    );
    postStructuredSearch.mockResolvedValue(fresh);

    const { result } = renderStore("?q=shoes");

    act(() => result.current.goToPage(2));
    await waitFor(() => expect(result.current.structured).toBe(fresh));

    await act(async () => {
      resolveFirst(stale);
      await Promise.resolve();
    });

    expect(result.current.structured).toBe(fresh);
  });
});
