import { renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  BENTO_NAV_ITEMS,
  bentoNavTarget,
  useVisibleBentoNav,
  type BentoNavItem,
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

describe("bentoNavTarget", () => {
  it("prefers the bento route when the surface is migrated", () => {
    const migrated = { bentoRoute: "/bento/llm/instance", consoleRoute: "/admin/llm/instance" } as BentoNavItem;
    expect(bentoNavTarget(migrated)).toBe("/bento/llm/instance");
  });

  it("falls back to the console route when not yet migrated", () => {
    const unmigrated = { consoleRoute: "/admin/se/instance" } as BentoNavItem;
    expect(bentoNavTarget(unmigrated)).toBe("/admin/se/instance");
  });
});

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
    expect(ids).not.toContain("administration"); // __admin__
  });

  it("reveals an item once the user holds its privilege", () => {
    h.user = { admin: false, privileges: ["LLM_VIEW"] };
    const { result } = renderHook(() => useVisibleBentoNav());
    const ids = result.current.map((i) => i.id);
    expect(ids).toContain("languageModel");
    expect(ids).not.toContain("aiAgent"); // still missing AI_AGENT_VIEW
  });
});
