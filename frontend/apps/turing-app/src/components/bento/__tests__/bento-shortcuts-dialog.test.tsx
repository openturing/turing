import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BentoShortcutsDialog } from "../bento-shortcuts-dialog";

describe("BentoShortcutsDialog", () => {
  it("lists the global + palette shortcuts when open", () => {
    render(<BentoShortcutsDialog open onOpenChange={() => {}} isMac={false} />);
    expect(screen.getByText("Keyboard shortcuts")).toBeInTheDocument();
    expect(screen.getByText("Open command palette")).toBeInTheDocument();
    expect(screen.getByText("Show keyboard shortcuts")).toBeInTheDocument();
    expect(screen.getByText("Move between results")).toBeInTheDocument();
    expect(screen.getByText("Close the palette or a dialog")).toBeInTheDocument();
  });

  it("shows the Ctrl modifier off macOS", () => {
    render(<BentoShortcutsDialog open onOpenChange={() => {}} isMac={false} />);
    expect(screen.getByText("Ctrl")).toBeInTheDocument();
    expect(screen.queryByText("⌘")).not.toBeInTheDocument();
  });

  it("shows the ⌘ modifier on macOS", () => {
    render(<BentoShortcutsDialog open onOpenChange={() => {}} isMac />);
    expect(screen.getByText("⌘")).toBeInTheDocument();
  });

  it("renders nothing when closed", () => {
    render(<BentoShortcutsDialog open={false} onOpenChange={() => {}} isMac={false} />);
    expect(screen.queryByText("Open command palette")).not.toBeInTheDocument();
  });
});
