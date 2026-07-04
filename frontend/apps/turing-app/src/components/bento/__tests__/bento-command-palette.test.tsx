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

  it("marks unmigrated areas as opening in the console", () => {
    openPalette();
    // At least one item links out to the console (sr-only indicator text).
    expect(screen.getAllByText("Opens in console").length).toBeGreaterThan(0);
  });
});
