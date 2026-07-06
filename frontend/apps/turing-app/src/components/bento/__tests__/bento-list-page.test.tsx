import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { IconCpu2 } from "@tabler/icons-react";
import type { ReactElement } from "react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { resolveBentoLayout, toBentoLayoutEntries } from "../bento-layout";
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

describe("resolveBentoLayout", () => {
  const items = [
    { id: "a" },
    { id: "b" },
    { id: "c" },
  ];
  const key = (i: { id: string }) => i.id;

  it("applies the featured=idx0 default when there are no entries", () => {
    const resolved = resolveBentoLayout(items, key, undefined);
    expect(resolved.map((r) => r.emphasis)).toEqual(["LARGE", "MEDIUM", "MEDIUM"]);
    expect(resolved.map((r) => r.key)).toEqual(["a", "b", "c"]);
  });

  it("orders by displayOrder and sizes by persisted emphasis", () => {
    const resolved = resolveBentoLayout(items, key, [
      { itemId: "c", emphasis: "LARGE", displayOrder: 0 },
      { itemId: "a", emphasis: "SMALL", displayOrder: 1 },
      { itemId: "b", emphasis: "MEDIUM", displayOrder: 2 },
    ]);
    expect(resolved.map((r) => r.key)).toEqual(["c", "a", "b"]);
    expect(resolved.map((r) => r.emphasis)).toEqual(["LARGE", "SMALL", "MEDIUM"]);
  });

  it("appends items missing from the layout at the end as MEDIUM", () => {
    const resolved = resolveBentoLayout(items, key, [
      { itemId: "b", emphasis: "LARGE", displayOrder: 0 },
    ]);
    expect(resolved.map((r) => r.key)).toEqual(["b", "a", "c"]);
    expect(resolved[1].emphasis).toBe("MEDIUM");
    expect(resolved[2].emphasis).toBe("MEDIUM");
  });

  it("round-trips through toBentoLayoutEntries with sequential displayOrder", () => {
    const resolved = resolveBentoLayout(items, key, undefined);
    expect(toBentoLayoutEntries(resolved)).toEqual([
      { itemId: "a", emphasis: "LARGE", displayOrder: 0 },
      { itemId: "b", emphasis: "MEDIUM", displayOrder: 1 },
      { itemId: "c", emphasis: "MEDIUM", displayOrder: 2 },
    ]);
  });
});
