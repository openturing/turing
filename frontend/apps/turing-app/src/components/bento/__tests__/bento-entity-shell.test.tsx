import { fireEvent, render, screen } from "@testing-library/react";
import { IconCpu2 } from "@tabler/icons-react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { BentoEntityShell, type BentoEntityShellRenderArgs } from "../bento-entity-shell";

interface Row {
  id?: string;
  title?: string;
  description?: string;
  icon?: string | null;
  enabled?: number;
}

function renderShell(props: {
  entity: Row;
  isNew: boolean;
  readOnly?: boolean;
  autosaveOnly?: boolean;
  badge?: React.ReactNode;
  notice?: React.ReactNode;
  child: (args: BentoEntityShellRenderArgs) => React.ReactNode;
}) {
  return render(
    <MemoryRouter>
      <BentoEntityShell<Row>
        entity={props.entity}
        isNew={props.isNew}
        headlineFallback="New Model"
        eyebrow="LLM"
        listRoute="/bento/llm/instance"
        icon={IconCpu2}
        tone="indigo"
        formId="f"
        feature="LLM"
        hasStatus
        readOnly={props.readOnly}
        autosaveOnly={props.autosaveOnly}
        badge={props.badge}
        notice={props.notice}
        onUpdate={async () => undefined}
        onDelete={async () => true}
      >
        {props.child}
      </BentoEntityShell>
    </MemoryRouter>,
  );
}

describe("BentoEntityShell", () => {
  it("in new mode shows the save action and passes empty staged identity to the form", () => {
    renderShell({
      entity: {},
      isNew: true,
      child: ({ staged }) => <div data-testid="child">{staged.title || "EMPTY"}</div>,
    });

    expect(screen.getByTestId("child")).toHaveTextContent("EMPTY");
    // New mode always shows the hero-anchored Save (label passes through as key).
    expect(screen.getByText("forms.formActions.saveChanges")).toBeInTheDocument();
  });

  it("in existing mode always shows the header Save, disabled until the form reports dirty", () => {
    renderShell({
      entity: { id: "1", title: "GPT", enabled: 1 },
      isNew: false,
      child: ({ onStateChange }) => (
        <button type="button" onClick={() => onStateChange({ isDirty: true, isSubmitting: false })}>
          make-dirty
        </button>
      ),
    });

    // Status pill reflects the entity's enabled flag.
    expect(screen.getByText("Active")).toBeInTheDocument();
    // The header Save is present even before any edit (parity with new mode /
    // the pre-redesign sticky header) but disabled — nothing pending yet — and
    // there is no unsaved marker.
    const saveBefore = screen.getByRole("button", { name: "forms.formActions.saveChanges" });
    expect(saveBefore).toBeInTheDocument();
    expect(saveBefore).toBeDisabled();
    expect(screen.queryByText("bento.saveBar.unsaved")).not.toBeInTheDocument();

    fireEvent.click(screen.getByText("make-dirty"));

    // Once the form reports dirty, Save enables and the eyebrow marks it unsaved.
    expect(screen.getByRole("button", { name: "forms.formActions.saveChanges" })).toBeEnabled();
    expect(screen.getByText("bento.saveBar.unsaved")).toBeInTheDocument();
  });

  it("autosaveOnly hides the inert Save on an existing entity but keeps it in new mode", () => {
    // Existing entity: identity auto-saves and sections self-commit, so the
    // header Save would sit permanently disabled — it must be hidden. Cancel
    // (back to list) stays.
    renderShell({
      entity: { id: "1", title: "Vocab", enabled: 1 },
      isNew: false,
      autosaveOnly: true,
      child: ({ onStateChange }) => (
        <button type="button" onClick={() => onStateChange({ isDirty: true, isSubmitting: false })}>
          make-dirty
        </button>
      ),
    });
    expect(screen.queryByRole("button", { name: "forms.formActions.saveChanges" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "forms.formActions.cancel" })).toBeInTheDocument();
    // Even reporting dirty must not resurrect it — there is nothing to submit.
    fireEvent.click(screen.getByText("make-dirty"));
    expect(screen.queryByRole("button", { name: "forms.formActions.saveChanges" })).not.toBeInTheDocument();
  });

  it("autosaveOnly still shows the Save in new mode — it is what creates the entity", () => {
    renderShell({
      entity: {},
      isNew: true,
      autosaveOnly: true,
      child: () => null,
    });
    expect(screen.getByRole("button", { name: "forms.formActions.saveChanges" })).toBeInTheDocument();
  });

  it("marks the title as required in new mode until one is entered", () => {
    renderShell({
      entity: {},
      isNew: true,
      child: () => null,
    });

    // Empty title in new mode → the required blocker is surfaced in the hero.
    expect(screen.getByText("bento.saveBar.titleRequired")).toBeInTheDocument();
  });

  it("in read-only mode suppresses Save even when the form reports dirty, and renders badge + notice", () => {
    renderShell({
      entity: { id: "1", title: "GLOBAL SE", enabled: 1 },
      isNew: false,
      readOnly: true,
      badge: <span>GLOBAL-BADGE</span>,
      notice: <div>READ-ONLY-NOTICE</div>,
      child: ({ onStateChange }) => (
        <button type="button" onClick={() => onStateChange({ isDirty: true, isSubmitting: false })}>
          make-dirty
        </button>
      ),
    });

    expect(screen.getByText("GLOBAL-BADGE")).toBeInTheDocument();
    expect(screen.getByText("READ-ONLY-NOTICE")).toBeInTheDocument();

    // Even after the form goes dirty, read-only never reveals a Save button.
    fireEvent.click(screen.getByText("make-dirty"));
    expect(screen.queryByText("forms.formActions.saveChanges")).not.toBeInTheDocument();
  });
});
