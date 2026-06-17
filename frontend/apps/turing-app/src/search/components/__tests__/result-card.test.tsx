import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import type { ResolvedDocument, TurDocument, TurDuplicateCluster } from "@viglet/turing-react-sdk";
import { ResultCard } from "../result-card";

// The card calls useTuringClickTracking on click; stub it so we don't boot the SDK.
vi.mock("@viglet/turing-react-sdk", () => ({
  useTuringClickTracking: () => ({ trackClick: vi.fn() }),
}));

function resolved(overrides: Partial<ResolvedDocument> = {}): ResolvedDocument {
  const raw: TurDocument = { elevate: false, fields: { id: "doc-1" }, metadata: [] };
  return {
    url: "https://portal-a.example/course/data-science",
    title: "Data Science MSc",
    description: "",
    date: "",
    image: "",
    text: "",
    raw,
    ...overrides,
  };
}

function cluster(): TurDuplicateCluster {
  return {
    canonicalId: "doc-1",
    size: 3,
    duplicate: true,
    members: [
      { id: "doc-1", title: "Data Science MSc", source: "portal-a", url: "https://portal-a.example/c", similarity: null },
      { id: "doc-2", title: "Data Science MSc", source: "portal-b", url: "https://portal-b.example/c", similarity: 0.97 },
      { id: "doc-3", title: "Data Science MSc", source: null, url: "https://portal-c.example/c", similarity: 0.93 },
    ],
  };
}

function renderCard(doc: ResolvedDocument) {
  return render(
    <MemoryRouter>
      <ResultCard
        document={doc}
        raw={doc.raw}
        siteName="catalog"
        position={1}
        onNavigate={() => {}}
        onViewJson={() => {}}
      />
    </MemoryRouter>,
  );
}

describe("ResultCard duplicate sources (T390)", () => {
  it("lists the sibling sources, excluding the current result", () => {
    const doc = resolved();
    doc.raw.duplicateCluster = cluster();

    renderCard(doc);

    // The seed (doc-1 / portal-a) is the current result and must not be listed.
    expect(screen.queryByText("portal-a")).not.toBeInTheDocument();
    // The other two sources are shown; doc-3 has no source so it falls back to its URL host.
    expect(screen.getByText("portal-b")).toBeInTheDocument();
    expect(screen.getByText("portal-c.example")).toBeInTheDocument();
  });

  it("renders nothing when the cluster has no duplicate", () => {
    const doc = resolved();
    doc.raw.duplicateCluster = { canonicalId: "doc-1", size: 1, duplicate: false, members: [] };

    renderCard(doc);

    expect(screen.queryByText("portal-b")).not.toBeInTheDocument();
  });

  it("renders nothing when there is no cluster at all", () => {
    renderCard(resolved());

    expect(screen.queryByText("portal-b")).not.toBeInTheDocument();
  });
});
