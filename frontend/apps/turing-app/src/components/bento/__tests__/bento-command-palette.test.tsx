import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { BentoCommandPalette } from "../bento-command-palette";
import { useVisibleBentoNav } from "../bento-nav.config";

// Admin user so every nav item is visible in the palette.
vi.mock("@/contexts/user.context", () => ({
  useCurrentUser: () => ({ user: { admin: true, privileges: [] }, refreshUser: () => {} }),
  UserProvider: ({ children }: { children?: React.ReactNode }) => children,
}));

/*
 * T993 — the palette is the design system's and is covered there against a
 * fixture. What is asserted here is this product's half: that BENTO_NAV_ITEMS
 * reaches ⌘K, filters by the labels this product declares, and resolves to this
 * product's routes. So it is fed the real config, the way the shell feeds it.
 *
 * Navigation is read off a real MemoryRouter rather than a stubbed
 * `useNavigate`: the palette now arrives pre-bundled inside the package, which
 * reaches the router through its own resolved copy, and a module mock on
 * "react-router-dom" no longer intercepts that. Asserting the landing path is
 * also the stronger claim — it is the route a reader ends up on.
 */
function Landing() {
  return <span data-testid="landing">{useLocation().pathname}</span>;
}

function Palette({ onOpenChange }: { onOpenChange: (open: boolean) => void }) {
  return <BentoCommandPalette open onOpenChange={onOpenChange} items={useVisibleBentoNav()} />;
}

function openPalette(onOpenChange = vi.fn()) {
  render(
    <MemoryRouter initialEntries={["/bento/home"]}>
      <Palette onOpenChange={onOpenChange} />
      <Landing />
    </MemoryRouter>,
  );
  return onOpenChange;
}

function landedOn() {
  return screen.getByTestId("landing").textContent ?? "";
}

describe("BentoCommandPalette", () => {
  it("lists areas and filters as the user types", () => {
    openPalette();
    // Labels render as their i18n keys under the test passthrough `t`.
    expect(screen.getByText("home.title")).toBeInTheDocument();
    expect(screen.getByText("home.features.graphqlExplorer.title")).toBeInTheDocument();

    fireEvent.change(screen.getByRole("combobox"), { target: { value: "graphql" } });

    expect(screen.getByText("home.features.graphqlExplorer.title")).toBeInTheDocument();
    expect(screen.queryByText("home.title")).not.toBeInTheDocument();
  });

  it("shows a no-match message when nothing matches", () => {
    openPalette();
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "zzzznope" } });
    expect(screen.getByText("No matches")).toBeInTheDocument();
  });

  it("navigates to the first result on Enter and closes", () => {
    const onOpenChange = openPalette();
    fireEvent.keyDown(screen.getByRole("combobox"), { key: "Enter" });
    // First nav item is Home, a migrated surface → bento route.
    expect(landedOn()).toBe("/bento/home");
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("navigates on click", () => {
    openPalette();
    fireEvent.click(screen.getByText("home.features.graphqlExplorer.title"));
    // The console context path is env-driven, so assert on the stable suffix.
    expect(landedOn()).toContain("graphql");
  });

  it("routes every nav surface into the bento shell once the migration is complete", () => {
    // As of Block AG Phase 4 (T567) every nav-config surface is migrated, so the
    // console-fallback indicator no longer appears for any item. The palette's
    // "Opens in console" affordance (shown when an item lacks a `bentoRoute`)
    // stays in the component for any future surface added ahead of its bento
    // page, but no live nav item exercises it anymore.
    openPalette();
    expect(screen.queryByText("Opens in console")).not.toBeInTheDocument();
  });
});
