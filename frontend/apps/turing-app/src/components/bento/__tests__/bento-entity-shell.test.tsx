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

  it("in existing mode reveals Save only after the form reports dirty", () => {
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
    // Not dirty yet → no Save button.
    expect(screen.queryByText("forms.formActions.saveChanges")).not.toBeInTheDocument();

    fireEvent.click(screen.getByText("make-dirty"));

    expect(screen.getByText("forms.formActions.saveChanges")).toBeInTheDocument();
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
