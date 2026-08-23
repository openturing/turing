import { render, screen } from "@testing-library/react";
import { IconDatabase } from "@tabler/icons-react";
import { useRef } from "react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import {
  BentoCountTile,
  BentoHero,
  BentoSection,
  BentoTile,
  useBentoScrollFade,
} from "@/components/bento";

/**
 * T988 — the comparison every deletion in this block is justified by.
 *
 * The other suites in this folder import `../bento-<x>` and so assert the local
 * copy, which says nothing about the package. These import the barrel, and the
 * barrel now resolves these five names to
 * `@viglet/viglet-design-system/bento`. So this file is the one place that
 * renders the shared layer as the product receives it.
 *
 * It asserts the contract the 118 bento pages depend on — the frosted surface
 * and the hover lift come from `.bento-tile`/`.bento-glass`, the mosaic span is
 * the caller's to pass through, and the fade custom property the save-bar morph
 * reads is the hook's output. Not the styling itself: jsdom computes no CSS.
 */

const wrap = (ui: React.ReactNode) => render(<MemoryRouter>{ui}</MemoryRouter>);

describe("the bento layer, rendered from the package", () => {
  it("renders a tile with the classes the stylesheet targets", () => {
    const { container } = wrap(
      <BentoTile
        to="/console/store"
        icon={IconDatabase}
        tone="blue"
        eyebrow="Store"
        title="Instances"
        span="col-span-2"
      />,
    );

    expect(screen.getByText("Instances")).toBeInTheDocument();
    expect(screen.getByText("Store")).toBeInTheDocument();

    // The press/hover affordance and the frosted surface are class-driven, and
    // bento.css now comes from the package too — if either name drifted, the
    // pages would render unstyled rather than fail, which is the regression
    // this block exists to avoid.
    const tile = container.querySelector(".bento-tile");
    expect(tile).not.toBeNull();
    expect(tile?.className).toContain("bento-glass");

    // The consumer drives the mosaic; the package must pass the span through.
    expect(tile?.className).toContain("col-span-2");

    // Interactive because `to` is set: the tile is the link, not a wrapper.
    expect(screen.getByRole("link")).toHaveAttribute("href", "/console/store");
  });

  it("renders a non-interactive tile as a plain surface", () => {
    wrap(
      <BentoTile
        icon={IconDatabase}
        tone="slate"
        eyebrow="Read only"
        title="No destination"
        span="col-span-1"
      />,
    );

    expect(screen.getByText("No destination")).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("renders a count tile with its number", () => {
    wrap(
      <BentoCountTile
        to="/console/se"
        icon={IconDatabase}
        tone="emerald"
        label="Search engines"
        count={7}
        span="col-span-1"
      />,
    );

    expect(screen.getByText("Search engines")).toBeInTheDocument();
    expect(screen.getByText("7")).toBeInTheDocument();
  });

  it("renders a section around the tiles it is given", () => {
    wrap(
      <BentoSection eyebrow="Data" title="Store" description="Where content lands">
        <div data-testid="child" />
      </BentoSection>,
    );

    expect(screen.getByText("Store")).toBeInTheDocument();
    expect(screen.getByText("Data")).toBeInTheDocument();
    expect(screen.getByText("Where content lands")).toBeInTheDocument();
    expect(screen.getByTestId("child")).toBeInTheDocument();
  });

  it("renders a hero with its back link", () => {
    wrap(<BentoHero backTo="/console" backLabel="Console" title="Instances" subtitle="All of them" />);

    expect(screen.getByText("Instances")).toBeInTheDocument();
    expect(screen.getByText("All of them")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Console/ })).toHaveAttribute("href", "/console");
  });

  it("writes the fade custom property the save-bar morph reads, and clears it", () => {
    function Probe() {
      const sentinel = useRef<HTMLDivElement>(null);
      useBentoScrollFade(sentinel);
      return <div ref={sentinel} />;
    }

    const root = document.documentElement;
    expect(root.style.getPropertyValue("--bento-fade")).toBe("");

    const { unmount } = wrap(<Probe />);

    // The hook returns nothing: it drives the morph by writing `--bento-fade`
    // on <html>, and CSS inheritance distributes it to `bento-fade-out` and
    // `bento-fade-in`. jsdom's getBoundingClientRect is all zeros, so the
    // sentinel reads as fully scrolled and progress clamps to 1.
    expect(root.style.getPropertyValue("--bento-fade")).toBe("1");
    expect(root.classList.contains("bento-fade-half")).toBe(true);

    // Reset on unmount, or the variable leaks into the next page — every bento
    // form page mounts this, so a leak is a save bar stuck visible.
    unmount();
    expect(root.style.getPropertyValue("--bento-fade")).toBe("");
    expect(root.classList.contains("bento-fade-half")).toBe(false);
  });
});
