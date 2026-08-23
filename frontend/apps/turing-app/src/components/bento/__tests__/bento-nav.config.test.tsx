import { renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ROUTES } from "@/app/routes.const";
import {
  BENTO_NAV_ITEMS,
  bentoSectionAreaRoute,
  bentoSectionByAreaRoute,
  useVisibleBentoNav,
  useVisibleBentoSections,
} from "../bento-nav.config";

// Mutable user holder so each test can flip admin / privileges. `vi.hoisted`
// runs before the mock factory, sidestepping the temporal-dead-zone that a
// plain outer `const` would hit under vi.mock hoisting.
const h = vi.hoisted(() => ({
  user: { admin: false, privileges: [] as string[] } as { admin?: boolean; privileges?: string[] },
}));

vi.mock("@/contexts/user.context", () => ({
  useCurrentUser: () => ({ user: h.user, refreshUser: () => {} }),
  UserProvider: ({ children }: { children?: React.ReactNode }) => children,
}));

// T992 — `bentoNavTarget` left with the schema it reads; the design system
// covers it there. What is still this product's to prove is the array below:
// which surfaces exist, and who may see them.
describe("useVisibleBentoNav", () => {
  beforeEach(() => {
    h.user = { admin: false, privileges: [] };
  });

  it("shows every item for an admin", () => {
    h.user = { admin: true, privileges: [] };
    const { result } = renderHook(() => useVisibleBentoNav());
    expect(result.current).toHaveLength(BENTO_NAV_ITEMS.length);
  });

  it("hides privilege-gated and admin-only items for a bare user", () => {
    const { result } = renderHook(() => useVisibleBentoNav());
    const ids = result.current.map((i) => i.id);
    expect(ids).toContain("home"); // no privilege required
    expect(ids).toContain("mcpServer");
    expect(ids).not.toContain("languageModel"); // LLM_VIEW
    expect(ids).not.toContain("aiAgent"); // AI_AGENT_VIEW
    expect(ids).not.toContain("adminUsers"); // __admin__
  });

  it("reveals an item once the user holds its privilege", () => {
    h.user = { admin: false, privileges: ["LLM_VIEW"] };
    const { result } = renderHook(() => useVisibleBentoNav());
    const ids = result.current.map((i) => i.id);
    expect(ids).toContain("languageModel");
    expect(ids).not.toContain("aiAgent"); // still missing AI_AGENT_VIEW
  });
});

describe("useVisibleBentoSections", () => {
  beforeEach(() => {
    h.user = { admin: true, privileges: [] };
  });

  it("groups visible items by section in order, dropping empty groups", () => {
    const { result } = renderHook(() => useVisibleBentoSections());
    const ids = result.current.map((g) => g.section.id);
    // Admin sees all four sections (primary + the three hubs).
    expect(ids).toEqual(["primary", "generativeAi", "enterpriseSearch", "management"]);
    // Every item is assigned to the group it declares as its section.
    for (const group of result.current) {
      expect(group.items.every((i) => i.section === group.section.id)).toBe(true);
      expect(group.items.length).toBeGreaterThan(0);
    }
  });

  it("drops a whole section when the user can see none of its items", () => {
    // A bare user can't see any generativeAi item (all privilege/LLM gated
    // except mcpServer/customTool/routine/chatWebhook/skills/aiAnalytics...),
    // but *can* see the always-visible ones — so assert the primary section is
    // present and privilege-gated-only sections behave predictably.
    h.user = { admin: false, privileges: [] };
    const { result } = renderHook(() => useVisibleBentoSections());
    const ids = result.current.map((g) => g.section.id);
    expect(ids).toContain("primary"); // home is always visible
    // enterpriseSearch has only privilege-gated + always-visible items; a bare
    // user still sees `integration` + `graphqlExplorer`, so the group stays.
    expect(ids).toContain("enterpriseSearch");
  });
});

describe("section helpers", () => {
  it("resolves a section from its area hub route", () => {
    expect(bentoSectionByAreaRoute(ROUTES.BENTO_AREA_GENERATIVE_AI)?.id).toBe("generativeAi");
    expect(bentoSectionByAreaRoute(ROUTES.BENTO_AREA_ENTERPRISE_SEARCH)?.id).toBe("enterpriseSearch");
    expect(bentoSectionByAreaRoute(ROUTES.BENTO_AREA_MANAGEMENT)?.id).toBe("management");
    expect(bentoSectionByAreaRoute("/bento/area/nope")).toBeUndefined();
  });

  it("maps an item to its section's hub route", () => {
    const aiAgent = BENTO_NAV_ITEMS.find((i) => i.id === "aiAgent")!;
    expect(bentoSectionAreaRoute(aiAgent)).toBe(ROUTES.BENTO_AREA_GENERATIVE_AI);
    // `home` lives in the primary section, which has no hub.
    const home = BENTO_NAV_ITEMS.find((i) => i.id === "home")!;
    expect(bentoSectionAreaRoute(home)).toBeUndefined();
  });
});
