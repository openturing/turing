import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BentoStatusMarker } from "../bento-status-marker";

// T989 -- ../bento-status-marker is a shim now, so this renders the package's
// component. It carries its own strings (a defaultValue on every key), and the
// global `t` mock returns a defaultValue when there is one. So the cues assert
// as English here where the local copy, which passed no default, rendered the
// raw key. Same component, same behaviour; only the copy is no longer missing.
describe("BentoStatusMarker", () => {
  it("renders nothing when neither dirty nor titleMissing", () => {
    const { container } = render(<BentoStatusMarker />);
    expect(container).toBeEmptyDOMElement();
  });

  it("shows the unsaved cue when dirty", () => {
    render(<BentoStatusMarker dirty />);
    expect(screen.getByText("Unsaved changes")).toBeInTheDocument();
    expect(screen.queryByText("Title required")).not.toBeInTheDocument();
  });

  it("shows the title-required blocker when the title is missing", () => {
    render(<BentoStatusMarker titleMissing />);
    expect(screen.getByText("Title required")).toBeInTheDocument();
  });

  it("prioritises the title-required blocker over the unsaved cue", () => {
    render(<BentoStatusMarker dirty titleMissing />);
    expect(screen.getByText("Title required")).toBeInTheDocument();
    expect(screen.queryByText("Unsaved changes")).not.toBeInTheDocument();
  });
});
