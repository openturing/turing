import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { IconDatabase } from "@tabler/icons-react";
import { BentoEmptyState } from "../bento-empty-state";

describe("BentoEmptyState", () => {
  it("renders the title, description, and action", () => {
    render(
      <BentoEmptyState
        icon={IconDatabase}
        title="No models yet"
        description="Add your first model to get started."
        action={<a href="/new">Create</a>}
      />,
    );
    expect(screen.getByText("No models yet")).toBeInTheDocument();
    expect(screen.getByText("Add your first model to get started.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Create" })).toBeInTheDocument();
  });

  it("carries the frosted bento surface classes", () => {
    const { container } = render(<BentoEmptyState title="Empty" />);
    const root = container.firstElementChild as HTMLElement;
    expect(root.className).toContain("bento-tile");
    expect(root.className).toContain("bento-glass");
  });

  it("aligns content left when align='start'", () => {
    const { container } = render(<BentoEmptyState title="Empty" align="start" />);
    const root = container.firstElementChild as HTMLElement;
    expect(root.className).toContain("items-start");
  });
});
