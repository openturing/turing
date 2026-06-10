import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import {
  TuringWorkspacePanelView,
  formatBytes,
  type TuringWorkspaceArtifact,
} from "../TuringWorkspacePanelView";

/**
 * T308 — headless-contract render tests for the workspace-panel view split out
 * of `@viglet/turing-react-sdk`'s `TuringWorkspacePanel`. Structure + slots
 * only (no styling); the SSE subscription lives in the SDK wrapper.
 */

function makeArtifact(
  key: string,
  overrides: Partial<TuringWorkspaceArtifact> = {},
): TuringWorkspaceArtifact {
  return { key, contentType: "text/plain", size: 2048, signedUrl: `/dl/${key}`, ...overrides };
}

describe("TuringWorkspacePanelView", () => {
  it("renders the title and one listitem per artifact with default rows", () => {
    render(
      <TuringWorkspacePanelView
        artifacts={[makeArtifact("a.txt"), makeArtifact("b.json", { size: 0 })]}
        status="success"
        title="Artifacts"
      />,
    );
    expect(screen.getByRole("complementary", { name: "Artifacts" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Artifacts" })).toBeInTheDocument();
    const items = screen.getAllByRole("listitem");
    expect(items).toHaveLength(2);
    const link = screen.getByText("a.txt").closest("a")!;
    expect(link).toHaveAttribute("href", "/dl/a.txt");
    expect(link).toHaveAttribute("download");
    expect(screen.getByText("2 KB")).toBeInTheDocument();
  });

  it("uses itemComponent when provided", () => {
    render(
      <TuringWorkspacePanelView
        artifacts={[makeArtifact("x.bin")]}
        itemComponent={({ artifact, index }) => (
          <span data-testid="custom-row">
            {index}:{artifact.key}
          </span>
        )}
      />,
    );
    expect(screen.getByTestId("custom-row")).toHaveTextContent("0:x.bin");
  });

  it("shows the default empty message, and the error variant on status=error", () => {
    const { rerender } = render(
      <TuringWorkspacePanelView artifacts={[]} status="success" />,
    );
    expect(screen.getByText("No files yet.")).toBeInTheDocument();
    rerender(<TuringWorkspacePanelView artifacts={[]} status="error" />);
    expect(screen.getByText("Workspace unavailable.")).toBeInTheDocument();
  });

  it("honors a custom emptyComponent", () => {
    render(
      <TuringWorkspacePanelView
        artifacts={[]}
        emptyComponent={() => <p>Nothing here</p>}
      />,
    );
    expect(screen.getByText("Nothing here")).toBeInTheDocument();
  });
});

describe("formatBytes", () => {
  it("formats across units and guards non-positive input", () => {
    expect(formatBytes(0)).toBe("0 B");
    expect(formatBytes(-5)).toBe("0 B");
    expect(formatBytes(512)).toBe("512 B");
    expect(formatBytes(2048)).toBe("2 KB");
    expect(formatBytes(1024 * 1024 * 3)).toBe("3 MB");
  });
});
