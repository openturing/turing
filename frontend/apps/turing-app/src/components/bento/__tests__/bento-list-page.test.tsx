import { render, screen } from "@testing-library/react";
import { IconCpu2 } from "@tabler/icons-react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { BentoEntityTile, BentoListPage } from "../bento-list-page";

interface Row {
  id: string;
  title: string;
}

function renderList(items: Row[] | undefined) {
  return render(
    <MemoryRouter>
      <BentoListPage<Row>
        items={items}
        error={null}
        tryAgainUrl="/bento/llm/instance"
        eyebrow="AI"
        heroIcon={IconCpu2}
        title="Language Models"
        subtitle="Configure providers"
        newRoute="/bento/llm/instance/new"
        newLabel="New Model"
        itemKey={(r) => r.id}
        emptyTitle="Nothing yet"
        emptyDescription="Add your first model"
        renderTile={(row, featured) => (
          <div data-testid={`tile-${row.id}`} data-featured={featured}>
            {row.title}
          </div>
        )}
      />
    </MemoryRouter>,
  );
}

describe("BentoListPage", () => {
  it("renders the New tile, item tiles, and marks the first item featured", () => {
    renderList([
      { id: "a", title: "Alpha" },
      { id: "b", title: "Beta" },
    ]);

    const newTile = screen.getByRole("link", { name: /New Model/i });
    expect(newTile).toHaveAttribute("href", "/bento/llm/instance/new");

    expect(screen.getByTestId("tile-a")).toHaveAttribute("data-featured", "true");
    expect(screen.getByTestId("tile-b")).toHaveAttribute("data-featured", "false");
    // Empty hint must not show when there are items.
    expect(screen.queryByText("Nothing yet")).not.toBeInTheDocument();
  });

  it("shows the empty hint when the list is empty", () => {
    renderList([]);
    expect(screen.getByText("Nothing yet")).toBeInTheDocument();
    // The New tile is always available, even when empty.
    expect(screen.getByRole("link", { name: /New Model/i })).toBeInTheDocument();
  });
});

describe("BentoEntityTile", () => {
  it("links to the entity and shows an Active pill + meta when enabled", () => {
    render(
      <MemoryRouter>
        <BentoEntityTile
          to="/bento/llm/instance/42"
          featured={false}
          defaultIcon={IconCpu2}
          title="GPT"
          hasStatus
          enabled={1}
          meta={<span>OpenAI</span>}
        />
      </MemoryRouter>,
    );

    const link = screen.getByRole("link", { name: /GPT/i });
    expect(link).toHaveAttribute("href", "/bento/llm/instance/42");
    expect(screen.getByText("Active")).toBeInTheDocument();
    expect(screen.getByText("OpenAI")).toBeInTheDocument();
  });

  it("shows an Idle pill when disabled", () => {
    render(
      <MemoryRouter>
        <BentoEntityTile to="/x" featured defaultIcon={IconCpu2} title="Off" hasStatus enabled={0} />
      </MemoryRouter>,
    );
    expect(screen.getByText("Idle")).toBeInTheDocument();
  });
});
