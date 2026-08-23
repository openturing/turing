import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { IconCpu2 } from "@tabler/icons-react";
import type { ReactElement } from "react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { BentoEntityTile, BentoListPage, BentoTileGrid } from "../bento-list-page";

interface Row {
  id: string;
  title: string;
}

function renderWithProviders(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  );
}

function renderList(items: Row[] | undefined, listId?: string) {
  return renderWithProviders(
    <BentoListPage<Row>
      items={items}
      error={null}
      tryAgainUrl="/bento/llm/instance"
      eyebrow="AI"
      heroIcon={IconCpu2}
      listId={listId}
      title="Language Models"
      subtitle="Configure providers"
      newRoute="/bento/llm/instance/new"
      newLabel="New Model"
      itemKey={(r) => r.id}
      emptyTitle="Nothing yet"
      emptyDescription="Add your first model"
      renderTile={(row, emphasis) => (
        <div data-testid={`tile-${row.id}`} data-emphasis={emphasis}>
          {row.title}
        </div>
      )}
    />,
  );
}

describe("BentoListPage", () => {
  it("renders the New tile, item tiles, and sizes the first item LARGE by default", () => {
    renderList([
      { id: "a", title: "Alpha" },
      { id: "b", title: "Beta" },
    ]);

    const newTile = screen.getByRole("link", { name: /New Model/i });
    expect(newTile).toHaveAttribute("href", "/bento/llm/instance/new");

    expect(screen.getByTestId("tile-a")).toHaveAttribute("data-emphasis", "LARGE");
    expect(screen.getByTestId("tile-b")).toHaveAttribute("data-emphasis", "MEDIUM");
    expect(screen.queryByText("Nothing yet")).not.toBeInTheDocument();
  });

  it("shows the empty hint when the list is empty", () => {
    renderList([]);
    expect(screen.getByText("Nothing yet")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /New Model/i })).toBeInTheDocument();
  });

  it("hides the Customize affordance without a listId", () => {
    renderList([{ id: "a", title: "Alpha" }]);
    expect(screen.queryByText("Customize layout")).not.toBeInTheDocument();
  });

  it("offers Customize with a listId and enters an edit mode with per-tile size + reorder controls", () => {
    renderList([
      { id: "a", title: "Alpha" },
      { id: "b", title: "Beta" },
    ], "languageModel");

    fireEvent.click(screen.getByText("Customize layout"));

    // Edit toolbar + per-tile affordances appear.
    expect(screen.getByText("Save layout")).toBeInTheDocument();
    expect(screen.getAllByLabelText("Drag to reorder")).toHaveLength(2);

    // Each tile gets a direct S/M/L size picker (3 buttons × 2 tiles).
    expect(screen.getAllByLabelText("Small")).toHaveLength(2);
    expect(screen.getAllByLabelText("Large")).toHaveLength(2);
    // First tile defaults to LARGE (its "Large" button is pressed).
    expect(screen.getAllByLabelText("Large")[0]).toHaveAttribute("aria-pressed", "true");
    // One click sets any size directly — no cycling.
    fireEvent.click(screen.getAllByLabelText("Small")[0]);
    expect(screen.getByTestId("tile-a")).toHaveAttribute("data-emphasis", "SMALL");
    expect(screen.getAllByLabelText("Small")[0]).toHaveAttribute("aria-pressed", "true");
  });
});

describe("BentoTileGrid", () => {
  function renderGrid(items: Row[] | undefined, opts?: { hideNew?: boolean }) {
    return renderWithProviders(
      <BentoTileGrid<Row>
        items={items}
        error={null}
        tryAgainUrl="/bento/admin/users"
        tone="slate"
        hideNew={opts?.hideNew}
        newRoute="/bento/admin/users/new"
        newLabel="New User"
        itemKey={(r) => r.id}
        emptyTitle="No users yet"
        emptyDescription="Add your first user"
        renderTile={(row) => <div data-testid={`tile-${row.id}`}>{row.title}</div>}
      />,
    );
  }

  it("renders a New tile plus one tile per item, without a hero", () => {
    renderGrid([
      { id: "a", title: "Alpha" },
      { id: "b", title: "Beta" },
    ]);
    expect(screen.getByRole("link", { name: /New User/i })).toHaveAttribute(
      "href",
      "/bento/admin/users/new",
    );
    expect(screen.getByTestId("tile-a")).toBeInTheDocument();
    expect(screen.getByTestId("tile-b")).toBeInTheDocument();
    // No customize affordance — BentoTileGrid is the hero-less static mosaic.
    expect(screen.queryByText("Customize layout")).not.toBeInTheDocument();
  });

  it("hides the New tile when hideNew is set (e.g. a read-only directory)", () => {
    renderGrid([{ id: "a", title: "Alpha" }], { hideNew: true });
    expect(screen.queryByRole("link", { name: /New User/i })).not.toBeInTheDocument();
    expect(screen.getByTestId("tile-a")).toBeInTheDocument();
  });

  it("shows the empty hint when the list is empty", () => {
    renderGrid([]);
    expect(screen.getByText("No users yet")).toBeInTheDocument();
  });
});

describe("BentoEntityTile", () => {
  it("links to the entity and shows an Active pill + meta when enabled", () => {
    renderWithProviders(
      <BentoEntityTile
        to="/bento/llm/instance/42"
        emphasis="MEDIUM"
        defaultIcon={IconCpu2}
        title="GPT"
        hasStatus
        enabled={1}
        meta={<span>OpenAI</span>}
      />,
    );

    const link = screen.getByRole("link", { name: /GPT/i });
    expect(link).toHaveAttribute("href", "/bento/llm/instance/42");
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.getByText("OpenAI")).toBeInTheDocument();
  });

  it("still honors the legacy featured flag when no emphasis is given", () => {
    renderWithProviders(
      <BentoEntityTile to="/x" featured defaultIcon={IconCpu2} title="Off" hasStatus enabled={0} />,
    );
    expect(screen.getByText("Idle")).toBeInTheDocument();
  });
});

// T1002 — the resolver and its round-trip left with `bento-layout`, which is now
// a re-export of the package's. Its suite there covers the same four cases plus
// the ones this one never had: a tie in displayOrder, an entry whose item is
// gone, and an empty item list. What stays above is this product's: that a page
// composing BentoListPage renders and enters its customise mode.
