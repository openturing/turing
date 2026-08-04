import { render, screen } from "@testing-library/react";
import axios from "axios";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AnswerSharePage from "../pages/answer-share.page";

/**
 * T890 / §LXIV.6 (Block BQ) — the shared answer renders as it was produced, not
 * as a replay: the snapshot's prose and sources, plus an explicit link back into
 * the live lens carrying the original question.
 *
 * @since 2026.3.4
 */

function renderShare(id = "shr-42") {
  return render(
    <MemoryRouter initialEntries={[`/answer/Sample/share/${id}`]}>
      <Routes>
        <Route path="/answer/:siteName/share/:shareId" element={<AnswerSharePage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("AnswerSharePage", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("renders the snapshot's answer, sources and filter constraints", async () => {
    vi.mocked(axios.get).mockResolvedValue({
      data: {
        id: "shr-42",
        siteName: "Sample",
        query: "how long is the warranty",
        answerText: "Two years.",
        filtersJson: JSON.stringify({ brand: ["Acme"] }),
        sourcesJson: JSON.stringify([
          { sourceId: "doc-1", title: "Warranty policy", rank: 1, score: 0.84 },
        ]),
        answeredAt: "2026-08-04T12:00:00Z",
      },
    });

    renderShare();

    expect(await screen.findByText("how long is the warranty")).toBeInTheDocument();
    expect(screen.getByText(/Two years\./)).toBeInTheDocument();
    expect(screen.getByText("Warranty policy")).toBeInTheDocument();
    expect(screen.getByText("answer.score: 0.840")).toBeInTheDocument();
    expect(screen.getByText("Acme")).toBeInTheDocument();
  });

  it("links back into the live lens carrying the original question and filters", async () => {
    vi.mocked(axios.get).mockResolvedValue({
      data: {
        id: "shr-42",
        siteName: "Sample",
        query: "how long is the warranty",
        answerText: "Two years.",
        filtersJson: JSON.stringify({ brand: ["Acme"] }),
      },
    });

    renderShare();

    expect(await screen.findByText("answer.shareReplay")).toHaveAttribute(
      "href",
      "/answer/Sample?q=how+long+is+the+warranty&fq=brand%3AAcme",
    );
  });

  it("survives a snapshot whose opaque JSON cannot be parsed", async () => {
    vi.mocked(axios.get).mockResolvedValue({
      data: {
        id: "shr-42",
        siteName: "Sample",
        query: "how long is the warranty",
        answerText: "Two years.",
        sourcesJson: "{ not json",
        filtersJson: "{ not json",
      },
    });

    renderShare();

    // The prose still renders; only the unreadable halves are dropped.
    expect(await screen.findByText(/Two years\./)).toBeInTheDocument();
    expect(screen.queryByText("answer.sources")).not.toBeInTheDocument();
  });

  it("says so when the permalink no longer resolves", async () => {
    vi.mocked(axios.get).mockRejectedValue(new Error("404"));

    renderShare("gone");

    expect(await screen.findByText("answer.shareNotFound")).toBeInTheDocument();
  });
});
