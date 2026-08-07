import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { TuringDocumentChips, type TuringDocumentChip } from "../TuringDocumentChips";
import { TuringResultList, type TuringResolvedDocument } from "../TuringResultList";

/**
 * T882 / §LXX.4 — a result's facet chip is an action in both response shapes.
 *
 * <p>The bug this closes: the union said "narrow on `"href" in chip` if you act on
 * it" and the package offered nothing to act with, so a consumer rendered
 * `chip.text` in a badge — the same dead badge T867 removed from the admin app.
 */

const STRUCTURED_CHIP: TuringDocumentChip = {
  field: "author",
  value: "Ada",
  filterQuery: "author:Ada",
  text: "Ada",
  selected: false,
  action: "ADD",
};

const LEGACY_HREF = "/api/sn/wknd/search?q=*&fq[]=author:Ada";
const LEGACY_CHIP: TuringDocumentChip = { href: LEGACY_HREF, text: "Ada" };

describe("TuringDocumentChips", () => {
  it("toggles a structured chip through onToggleFacet, never through a URL", () => {
    const onToggleFacet = vi.fn();
    render(<TuringDocumentChips chips={[STRUCTURED_CHIP]} onToggleFacet={onToggleFacet} />);

    const chip = screen.getByRole("button", { name: "Ada" });
    expect(chip).toHaveAttribute("aria-pressed", "false");
    fireEvent.click(chip);

    expect(onToggleFacet).toHaveBeenCalledWith({
      field: "author",
      filterQuery: "author:Ada",
      action: "ADD",
    });
  });

  it("an applied chip reports itself pressed and removes its own filter", () => {
    const onToggleFacet = vi.fn();
    render(
      <TuringDocumentChips
        chips={[{ ...STRUCTURED_CHIP, selected: true, action: "REMOVE" }]}
        onToggleFacet={onToggleFacet}
      />,
    );

    expect(screen.getByRole("button", { name: "Ada" })).toHaveAttribute("aria-pressed", "true");
    fireEvent.click(screen.getByRole("button", { name: "Ada" }));

    expect(onToggleFacet).toHaveBeenCalledWith(
      expect.objectContaining({ action: "REMOVE" }),
    );
  });

  /** A pre-T869 server sent neither `selected` nor `action`. */
  it("resolves a missing action from the selection state", () => {
    const onToggleFacet = vi.fn();
    render(
      <TuringDocumentChips
        chips={[{ field: "author", value: "Ada", filterQuery: "author:Ada", text: "Ada" }]}
        onToggleFacet={onToggleFacet}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Ada" }));

    expect(onToggleFacet).toHaveBeenCalledWith(
      expect.objectContaining({ action: "ADD" }),
    );
  });

  it("keeps a legacy chip's href on the anchor but calls onNavigate instead", () => {
    const onNavigate = vi.fn();
    render(<TuringDocumentChips chips={[LEGACY_CHIP]} onNavigate={onNavigate} />);

    const link = screen.getByRole("link", { name: "Ada" });
    expect(link).toHaveAttribute("href", LEGACY_HREF);
    fireEvent.click(link);

    expect(onNavigate).toHaveBeenCalledWith(LEGACY_HREF);
  });

  /**
   * The whole point: with no handler there is no action, so the chip must not
   * present itself as a control. This is the assertion that fails if the dead
   * badge comes back.
   */
  it("renders an unhandled chip as text, not as a fake control", () => {
    render(<TuringDocumentChips chips={[STRUCTURED_CHIP, LEGACY_CHIP]} />);

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.getAllByText("Ada")).toHaveLength(2);
  });

  it("skips a chip with no text and renders nothing for an empty list", () => {
    const { container } = render(
      <TuringDocumentChips chips={[{ ...STRUCTURED_CHIP, text: "" }]} onToggleFacet={vi.fn()} />,
    );
    expect(container.querySelector("button")).toBeNull();

    const empty = render(<TuringDocumentChips chips={[]} />);
    expect(empty.container).toBeEmptyDOMElement();
  });

  it("hands the render prop everything the default renderer uses", () => {
    const itemComponent = vi.fn(() => <span data-testid="own-chip" />);
    render(
      <TuringDocumentChips
        chips={[STRUCTURED_CHIP]}
        onToggleFacet={vi.fn()}
        itemComponent={itemComponent}
      />,
    );

    expect(screen.getByTestId("own-chip")).toBeInTheDocument();
    expect(itemComponent).toHaveBeenCalledWith(
      expect.objectContaining({
        chip: STRUCTURED_CHIP,
        isActionable: true,
        isSelected: false,
        onClick: expect.any(Function),
      }),
    );
  });
});

describe("TuringResultList chip handlers (T882)", () => {
  function makeDoc(): TuringResolvedDocument {
    return {
      url: "/a",
      title: "Grounding a chat on your own content",
      description: "",
      date: "",
      image: "",
      text: "",
      raw: { elevate: false, fields: { id: "doc-1" }, metadata: [STRUCTURED_CHIP] },
    };
  }

  it("forwards onToggleFacet to the row component so its chips can act", () => {
    const onToggleFacet = vi.fn();
    render(
      <TuringResultList
        documents={[makeDoc()]}
        onToggleFacet={onToggleFacet}
        itemComponent={({ raw, onToggleFacet: toggle }) => (
          <TuringDocumentChips chips={raw.metadata} onToggleFacet={toggle} />
        )}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Ada" }));

    expect(onToggleFacet).toHaveBeenCalledWith(
      expect.objectContaining({ filterQuery: "author:Ada" }),
    );
  });

  it("leaves the handler undefined when the list was given none", () => {
    render(
      <TuringResultList
        documents={[makeDoc()]}
        itemComponent={({ raw, onToggleFacet: toggle }) => (
          <TuringDocumentChips chips={raw.metadata} onToggleFacet={toggle} />
        )}
      />,
    );

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.getByText("Ada")).toBeInTheDocument();
  });
});
