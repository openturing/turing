// @vitest-environment node
import { describe, expect, it, vi } from "vitest";
import { createHostActions } from "../host-actions";

/**
 * T450 — embeddable action widget. Verifies the default host-action client-tool
 * handlers' contract without a DOM (navigate/add_to_cart overrides, validation,
 * and the safe "no DOM available" fallback for the DOM-driven actions).
 */
describe("createHostActions (T450)", () => {
  it("exposes the four host-action tools plus any extras", () => {
    const { clientTools } = createHostActions({
      extra: { custom_action: () => ({ ok: true }) },
    });
    expect(Object.keys(clientTools).sort((a, b) => a.localeCompare(b))).toEqual([
      "add_to_cart",
      "click_element",
      "custom_action",
      "fill_form",
      "navigate",
    ]);
  });

  it("navigate routes through onNavigate when provided", async () => {
    const onNavigate = vi.fn();
    const { clientTools } = createHostActions({ onNavigate });
    const res = await clientTools.navigate({ url: "/cart" });
    expect(onNavigate).toHaveBeenCalledWith("/cart");
    expect(res).toMatchObject({ success: true, url: "/cart" });
  });

  it("navigate is disabled unless the host opts in", async () => {
    const { clientTools } = createHostActions();
    expect(await clientTools.navigate({ url: "/cart" })).toMatchObject({ success: false });
  });

  it("navigate requires a url", async () => {
    const { clientTools } = createHostActions({ onNavigate: vi.fn() });
    expect(await clientTools.navigate({})).toMatchObject({ success: false });
  });

  it("add_to_cart calls the host hook with a parsed quantity", async () => {
    const onAddToCart = vi.fn().mockResolvedValue({ cartSize: 2 });
    const { clientTools } = createHostActions({ onAddToCart });
    const res = await clientTools.add_to_cart({ productId: "p1", quantity: "3" });
    expect(onAddToCart).toHaveBeenCalledWith({ productId: "p1", quantity: 3 });
    expect(res).toMatchObject({ success: true, productId: "p1", quantity: 3 });
  });

  it("add_to_cart no-ops without a host hook and requires a productId", async () => {
    const noHook = createHostActions();
    expect(await noHook.clientTools.add_to_cart({ productId: "p1" })).toMatchObject({
      success: false,
    });
    const withHook = createHostActions({ onAddToCart: vi.fn() });
    expect(await withHook.clientTools.add_to_cart({})).toMatchObject({ success: false });
  });

  it("DOM actions fail safely when there is no DOM", async () => {
    // No `root` and (in the node test env) no document → safe failure, never throws.
    const { clientTools } = createHostActions();
    expect(await clientTools.fill_form({ fields: [{ selector: "#x", value: "v" }] }))
      .toMatchObject({ success: false });
    expect(await clientTools.click_element({ selector: "#x" })).toMatchObject({
      success: false,
    });
  });
});
