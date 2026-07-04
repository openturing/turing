import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { BentoCommandPalette } from "../bento-command-palette";

const navigateMock = vi.fn();

vi.mock("react-router-dom", () => ({
  useNavigate: () => navigateMock,
}));

// Admin user so every nav item is visible in the palette.
vi.mock("@/contexts/user.context", () => ({
  useCurrentUser: () => ({ user: { admin: true, privileges: [] }, refreshUser: () => {} }),
  UserProvider: ({ children }: { children?: React.ReactNode }) => children,
}));

function openPalette(onOpenChange = vi.fn()) {
  render(<BentoCommandPalette open onOpenChange={onOpenChange} />);
  return onOpenChange;
}

describe("BentoCommandPalette", () => {
  beforeEach(() => navigateMock.mockClear());

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
    expect(navigateMock).toHaveBeenCalledWith("/bento/home");
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("navigates on click", () => {
    openPalette();
    fireEvent.click(screen.getByText("home.features.graphqlExplorer.title"));
    // GraphQL explorer is not yet migrated → console fallback route. The
    // console context path is env-driven, so assert on the stable suffix.
    expect(navigateMock).toHaveBeenCalledWith(expect.stringContaining("graphql"));
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
