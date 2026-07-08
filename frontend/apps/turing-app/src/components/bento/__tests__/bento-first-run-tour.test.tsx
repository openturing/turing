import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  BENTO_TOUR_SEEN_KEY,
  BentoFirstRunTour,
  hasSeenBentoTour,
} from "../bento-first-run-tour";

// The tour uses the TurLogo, which pulls the design system; stub it to a marker.
vi.mock("@/components/logo/tur-logo", () => ({
  TurLogo: () => <span data-testid="tur-logo" />,
}));

// jsdom in this project runs without a real localStorage; provide a tiny
// in-memory one so the tour's persistence path is exercised.
function installMemoryStorage() {
  const store = new Map<string, string>();
  const mock: Pick<Storage, "getItem" | "setItem" | "removeItem" | "clear"> = {
    getItem: (k) => store.get(k) ?? null,
    setItem: (k, v) => void store.set(k, String(v)),
    removeItem: (k) => void store.delete(k),
    clear: () => store.clear(),
  };
  vi.stubGlobal("localStorage", mock);
}

describe("BentoFirstRunTour", () => {
  beforeEach(() => installMemoryStorage());

  it("steps forward through the three cards and finishes", () => {
    const onOpenChange = vi.fn();
    render(<BentoFirstRunTour open onOpenChange={onOpenChange} />);

    // Step 1 — the rail.
    expect(screen.getByText("One rail, three hubs")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Next"));

    // Step 2 — the palette.
    expect(screen.getByText("Jump anywhere with the palette")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Next"));

    // Step 3 — customize; the last step shows "Get started".
    expect(screen.getByText("Make it yours")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Get started"));

    expect(onOpenChange).toHaveBeenCalledWith(false);
    expect(hasSeenBentoTour()).toBe(true);
  });

  it("can step back", () => {
    render(<BentoFirstRunTour open onOpenChange={() => {}} />);
    fireEvent.click(screen.getByText("Next"));
    expect(screen.getByText("Jump anywhere with the palette")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Back"));
    expect(screen.getByText("One rail, three hubs")).toBeInTheDocument();
  });

  it("marks the tour seen when skipped", () => {
    const onOpenChange = vi.fn();
    render(<BentoFirstRunTour open onOpenChange={onOpenChange} />);
    fireEvent.click(screen.getByText("Skip"));
    expect(localStorage.getItem(BENTO_TOUR_SEEN_KEY)).toBe("1");
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("hasSeenBentoTour reflects the stored flag", () => {
    expect(hasSeenBentoTour()).toBe(false);
    localStorage.setItem(BENTO_TOUR_SEEN_KEY, "1");
    expect(hasSeenBentoTour()).toBe(true);
  });
});
