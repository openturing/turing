import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { BentoNavRail } from "../bento-nav-rail";

// Admin so every section is visible on the rail.
vi.mock("@/contexts/user.context", () => ({
  useCurrentUser: () => ({ user: { admin: true, privileges: [] }, refreshUser: () => {} }),
  UserProvider: ({ children }: { children?: React.ReactNode }) => children,
}));

function renderRail(initial = "/bento/home") {
  return render(
    <MemoryRouter initialEntries={[initial]}>
      <BentoNavRail />
    </MemoryRouter>,
  );
}

describe("BentoNavRail", () => {
  it("lists Home + the three section hubs — never the individual leaves", () => {
    renderRail();
    // Labels render as their i18n keys under the global passthrough `t`.
    expect(screen.getByRole("link", { name: "home.title" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "home.sections.generativeAi.label" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "home.sections.enterpriseSearch.label" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "home.sections.management.label" })).toBeInTheDocument();

    // Leaf surfaces are reached from the hub or via ⌘K — not on the rail.
    expect(screen.queryByRole("link", { name: "home.features.aiAgent.title" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "home.features.languageModel.title" })).not.toBeInTheDocument();

    // Exactly four rail links (Home + 3 hubs) — the guard against re-inflating
    // the rail back into a flat 22-item strip.
    expect(screen.getAllByRole("link")).toHaveLength(4);
  });

  it("points each hub at its area route", () => {
    renderRail();
    expect(screen.getByRole("link", { name: "home.sections.generativeAi.label" }))
      .toHaveAttribute("href", "/bento/area/generative-ai");
    expect(screen.getByRole("link", { name: "home.sections.enterpriseSearch.label" }))
      .toHaveAttribute("href", "/bento/area/enterprise-search");
    expect(screen.getByRole("link", { name: "home.sections.management.label" }))
      .toHaveAttribute("href", "/bento/area/management");
  });

  it("marks the section hub active while inside one of its migrated leaves", () => {
    renderRail("/bento/ai-agent/instance/123");
    expect(screen.getByRole("link", { name: "home.sections.generativeAi.label" }))
      .toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "home.sections.management.label" }))
      .not.toHaveAttribute("aria-current");
  });

  it("marks Home active on the home route", () => {
    renderRail("/bento/home");
    expect(screen.getByRole("link", { name: "home.title" })).toHaveAttribute("aria-current", "page");
  });
});
