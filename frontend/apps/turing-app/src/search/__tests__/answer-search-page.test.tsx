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
