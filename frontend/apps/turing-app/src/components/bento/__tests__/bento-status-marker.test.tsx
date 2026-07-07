import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BentoStatusMarker } from "../bento-status-marker";

describe("BentoStatusMarker", () => {
  it("renders nothing when neither dirty nor titleMissing", () => {
    const { container } = render(<BentoStatusMarker />);
    expect(container).toBeEmptyDOMElement();
  });

  it("shows the unsaved cue when dirty", () => {
    render(<BentoStatusMarker dirty />);
    expect(screen.getByText("bento.saveBar.unsaved")).toBeInTheDocument();
    expect(screen.queryByText("bento.saveBar.titleRequired")).not.toBeInTheDocument();
  });

  it("shows the title-required blocker when the title is missing", () => {
    render(<BentoStatusMarker titleMissing />);
    expect(screen.getByText("bento.saveBar.titleRequired")).toBeInTheDocument();
  });

  it("prioritises the title-required blocker over the unsaved cue", () => {
    render(<BentoStatusMarker dirty titleMissing />);
    expect(screen.getByText("bento.saveBar.titleRequired")).toBeInTheDocument();
    expect(screen.queryByText("bento.saveBar.unsaved")).not.toBeInTheDocument();
  });
});
