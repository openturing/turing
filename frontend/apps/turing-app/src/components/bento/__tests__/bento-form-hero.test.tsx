import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { BentoFormHero, type BentoFormHeroProps } from "../bento-form-hero";

function renderHero(props: Partial<BentoFormHeroProps> = {}) {
  return render(
    <MemoryRouter>
      <BentoFormHero title="Behavior" onCancel={() => undefined} {...props} />
    </MemoryRouter>,
  );
}

describe("BentoFormHero", () => {
  it("renders both halves of the morph — the hero Save/Cancel and the sticky twin", () => {
    renderHero({ dirty: true, stickyTitle: "Behavior" });

    // One copy in the hero `trailing`, one in the fixed scroll save bar.
    expect(screen.getAllByText("forms.formActions.saveChanges")).toHaveLength(2);
    expect(screen.getAllByText("forms.formActions.cancel")).toHaveLength(2);
  });

  it("submits the enclosing form — both Save buttons are type=submit", () => {
    renderHero({ dirty: true });

    const saves = screen
      .getAllByText("forms.formActions.saveChanges")
      .map((label) => label.closest("button"));
    expect(saves).toHaveLength(2);
    for (const save of saves) {
      expect(save).toHaveAttribute("type", "submit");
    }
  });

  it("renders the back-arrow breadcrumb from backTo/backLabel", () => {
    renderHero({ backTo: "/bento/sn/instance/1", backLabel: "Site" });

    const link = screen.getByRole("link", { name: "Site" });
    expect(link).toHaveAttribute("href", "/bento/sn/instance/1");
    expect(link.querySelector("svg")).toBeInTheDocument();
  });

  it("disables Save when there is nothing to save, and honours saveDisabled", () => {
    const { unmount } = renderHero();
    for (const label of screen.getAllByText("forms.formActions.saveChanges")) {
      expect(label.closest("button")).toBeDisabled();
    }
    unmount();

    // Forms that don't track dirty state opt out explicitly (T738).
    renderHero({ saveDisabled: false });
    for (const label of screen.getAllByText("forms.formActions.saveChanges")) {
      expect(label.closest("button")).not.toBeDisabled();
    }
  });

  it("replaces the default Save/Cancel in BOTH copies when `actions` is given", () => {
    renderHero({
      actions: (
        <button type="button" onClick={vi.fn()}>
          Apply
        </button>
      ),
    });

    expect(screen.getAllByRole("button", { name: "Apply" })).toHaveLength(2);
    expect(screen.queryByText("forms.formActions.saveChanges")).not.toBeInTheDocument();
    expect(screen.queryByText("forms.formActions.cancel")).not.toBeInTheDocument();
  });

  it("keeps caller `trailing` outside the fade-out group so destructive actions stay put", () => {
    renderHero({ trailing: <span data-testid="delete-dialog" /> });

    const trailing = screen.getByTestId("delete-dialog");
    expect(trailing.closest(".bento-fade-out")).toBeNull();
  });
});
