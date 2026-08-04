import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AnswerSearchPage from "../pages/answer-search.page";

/**
 * T852 / §LXIV.1 (Block BQ) — the `/answer/{siteName}` route's contract: every
 * input that defines an answer is read from the URL (`q`, `_setlocale`, repeated
 * `fq=field:value`) and threaded into the shipped streaming answer endpoint, so
 * an answer's *input* is reproducible from the link alone.
 *
 * @since 2026.3.4
 */

/** Builds a named-event SSE payload — the shape `consumeAnswerStream` parses. */
function answerStream(events: Array<[string, string]>): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  const payload = events.map(([name, data]) => `event: ${name}\ndata: ${data}\n\n`).join("");
  return new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(encoder.encode(payload));
      controller.close();
    },
  });
}

function stubAnswerFetch(events: Array<[string, string]>) {
  const fetchMock = vi.fn(
    async (_input?: unknown, _init?: RequestInit) =>
      ({ ok: true, status: 200, statusText: "OK", body: answerStream(events) }) as unknown as Response,
  );
  globalThis.fetch = fetchMock as unknown as typeof fetch;
  return fetchMock;
}

function renderAt(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/answer/:siteName" element={<AnswerSearchPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("AnswerSearchPage", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("asks the answer endpoint with the query, locale and fq filters from the URL", async () => {
    const fetchMock = stubAnswerFetch([
      ["sources", JSON.stringify([{ sourceId: "doc-1", title: "Warranty policy", rank: 1 }])],
      ["token", "Two"],
      ["token", " years."],
      ["citations", JSON.stringify([{ rank: 1, sourceId: "doc-1", title: "Warranty policy" }])],
      ["followups", JSON.stringify(["Does it cover accidents?"])],
      ["done", "{}"],
    ]);

    renderAt("/answer/Sample?q=how+long+is+the+warranty&_setlocale=en_US&fq=brand:Acme");

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const requested = String(fetchMock.mock.calls[0][0]);
    expect(requested).toContain("/sn/Sample/answer?");
    expect(requested).toContain("q=how+long+is+the+warranty");
    expect(requested).toContain("locale=en_US");
    expect(requested).toContain("fq=brand%3AAcme");

    // The streamed answer, its footnoted source and the follow-up all render.
    await waitFor(() => expect(screen.getByText(/Two years\./)).toBeInTheDocument());
    expect(screen.getByText("Warranty policy")).toBeInTheDocument();
    expect(screen.getByText("Does it cover accidents?")).toBeInTheDocument();
    // The active filter is shown as a removable constraint chip.
    expect(screen.getByText("brand:")).toBeInTheDocument();
    expect(screen.getByText("Acme")).toBeInTheDocument();
    // The input is repopulated from the link, so the question stays editable.
    expect(screen.getByPlaceholderText("answer.placeholder")).toHaveValue(
      "how long is the warranty",
    );
  });

  it("does not call the endpoint when the URL carries no question", async () => {
    const fetchMock = stubAnswerFetch([]);

    renderAt("/answer/Sample");

    expect(await screen.findByText("answer.empty")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("ignores a query of `*` rather than asking for a browse-all answer", async () => {
    const fetchMock = stubAnswerFetch([]);

    renderAt("/answer/Sample?q=*");

    await waitFor(() => expect(fetchMock).not.toHaveBeenCalled());
  });
});

/**
 * T854 / §LXIV.3 — the operator affordances: the reader defaults are inside out
 * on this page, so the audit trail is visible without a click.
 */
describe("AnswerSearchPage — operator affordances (T854)", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    stubAnswerFetch([
      [
        "sources",
        JSON.stringify([
          {
            sourceId: "doc-1",
            title: "Warranty policy",
            rank: 1,
            score: 0.8421,
            chunkIndex: 3,
            keywordOnly: true,
          },
        ]),
      ],
      ["token", "Two years."],
      [
        "citations",
        JSON.stringify([
          {
            rank: 1,
            sourceId: "doc-1",
            title: "Warranty policy",
            rankingExplanation: { lexicalRank: 4, semanticRank: 1, rrfScore: 0.031 },
          },
        ]),
      ],
      ["trace_step", JSON.stringify({ callId: "c1", name: "sn_search", phase: "end", durationMs: 42, status: "ok" })],
      ["trace_step", JSON.stringify({ callId: "c2", name: "sn_search", phase: "start" })],
      ["trace", JSON.stringify({ sourceCount: 1, answerLength: 10, kbMode: "HYBRID", rerankEnabled: true, rerankStrategy: "CROSS_ENCODER" })],
      ["done", "{}"],
    ]);
    renderAt("/answer/Sample?q=how+long+is+the+warranty");
  });

  it("opens the provenance panel by default and shows the rank breakdown", async () => {
    const panel = await screen.findByText("answer.provenance");
    expect(panel.closest("details")).toHaveAttribute("open");
    expect(screen.getByText("HYBRID")).toBeInTheDocument();
    expect(screen.getByText("CROSS_ENCODER")).toBeInTheDocument();
    expect(screen.getByText("rrfScore")).toBeInTheDocument();
  });

  it("shows the research trace, counting only completed steps", async () => {
    expect(await screen.findByText("answer.trace")).toBeInTheDocument();
    expect(screen.getByText("42ms")).toBeInTheDocument();
    // The in-flight `start` step must not double the entry it will complete.
    expect(screen.getAllByText("sn_search")).toHaveLength(1);
  });

  it("carries the retrieval score, chunk and keyword-only cue on the source card", async () => {
    expect(await screen.findByText("answer.score: 0.842")).toBeInTheDocument();
    expect(screen.getByText("answer.chunk 3")).toBeInTheDocument();
    expect(screen.getByText("answer.keywordOnly")).toBeInTheDocument();
  });

  it("offers the raw JSON of the source the answer cited", async () => {
    expect(await screen.findByLabelText("answer.viewJson")).toBeInTheDocument();
  });
});

/** T855 / §LXIV.4 — the same question, one click away in the other two lenses. */
describe("AnswerSearchPage — cross-lens switcher (T855)", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    stubAnswerFetch([["token", "Two years."], ["done", "{}"]]);
  });

  it("carries the question, locale and filters into the keyword and vector lenses", () => {
    renderAt("/answer/Sample?q=warranty&_setlocale=en_US&fq=brand:Acme");

    expect(screen.getByText("answer.lens.keyword").closest("a")).toHaveAttribute(
      "href",
      "/sn/Sample?q=warranty&_setlocale=en_US&fq=brand%3AAcme",
    );
    // The vector lens spells filters as bare params, not `fq`.
    expect(screen.getByText("answer.lens.vector").closest("a")).toHaveAttribute(
      "href",
      "/ann/Sample?q=warranty&_setlocale=en_US&brand=Acme",
    );
  });

  it("marks the lens being viewed as current instead of linking to itself", () => {
    renderAt("/answer/Sample?q=warranty");

    const current = screen.getByText("answer.lens.answer");
    expect(current.closest("a")).toBeNull();
    expect(current.closest("[aria-current]")).toHaveAttribute("aria-current", "page");
  });
});
