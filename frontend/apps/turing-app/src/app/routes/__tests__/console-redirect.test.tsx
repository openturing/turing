import { describe, expect, it } from "vitest";
import { consoleSuffixToBento } from "../console-redirect";

/**
 * T569 cutover redirect logic (T571 smoke). `consoleSuffixToBento` maps a
 * retired console path suffix (everything after the console context path, no
 * leading slash) to its bento equivalent. It must survive as the contract that
 * keeps old `/admin/...` deep-links resolving after the console was deleted.
 */
describe("consoleSuffixToBento", () => {
  it("sends the console root and home to the bento home", () => {
    expect(consoleSuffixToBento("")).toBe("/bento/home");
    expect(consoleSuffixToBento("home")).toBe("/bento/home");
  });

  it("passes surfaces that mirror 1:1 straight through under /bento", () => {
    expect(consoleSuffixToBento("se/instance/123")).toBe("/bento/se/instance/123");
    expect(consoleSuffixToBento("sn/instance/1/field/2")).toBe("/bento/sn/instance/1/field/2");
    expect(consoleSuffixToBento("ai-agent/instance/x/tools")).toBe("/bento/ai-agent/instance/x/tools");
    expect(consoleSuffixToBento("chat")).toBe("/bento/chat");
  });

  it("renames embedding-model to embedding", () => {
    expect(consoleSuffixToBento("embedding-model/instance")).toBe("/bento/embedding/instance");
    expect(consoleSuffixToBento("embedding-model/instance/5")).toBe("/bento/embedding/instance/5");
  });

  it("flattens the admin-settings tree onto its bento surfaces", () => {
    expect(consoleSuffixToBento("admin-settings")).toBe("/bento/admin");
    expect(consoleSuffixToBento("admin-settings/users")).toBe("/bento/admin/users");
    expect(consoleSuffixToBento("admin-settings/users/bob")).toBe("/bento/admin/users/bob");
    expect(consoleSuffixToBento("admin-settings/groups")).toBe("/bento/admin/groups");
    expect(consoleSuffixToBento("admin-settings/roles")).toBe("/bento/admin/roles");
    expect(consoleSuffixToBento("admin-settings/tokens")).toBe("/bento/token/instance");
    expect(consoleSuffixToBento("admin-settings/settings")).toBe("/bento/global-settings");
    expect(consoleSuffixToBento("admin-settings/genai")).toBe("/bento/global-settings");
    expect(consoleSuffixToBento("admin-settings/system-info")).toBe("/bento/system-info");
    expect(consoleSuffixToBento("admin-settings/import")).toBe("/bento/import");
    expect(consoleSuffixToBento("admin-settings/logging")).toBe("/bento/logging");
    expect(consoleSuffixToBento("admin-settings/logging/server")).toBe("/bento/logging/server");
  });

  it("does not confuse the flattened prefixes with other surfaces", () => {
    // `admin-settings` remap must not swallow an unrelated `admin`-prefixed path.
    expect(consoleSuffixToBento("ai-analytics/cost-governance")).toBe(
      "/bento/ai-analytics/cost-governance",
    );
  });
});
