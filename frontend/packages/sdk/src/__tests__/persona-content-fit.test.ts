import { describe, expect, it, vi } from "vitest";
import { fetchPersonaContentFit, postSiteFlowSelect } from "../api";
import type { TuringClient } from "../client";
import type { TurContentFit } from "../types";

/**
 * T635 / §XXVII.4 — the SDK persona surface: the `fetchPersonaContentFit`
 * content-fit call and the `personaId` seeded into `postSiteFlowSelect`.
 */
describe("fetchPersonaContentFit (T635)", () => {
  const verdict: TurContentFit = {
    personaId: "p1",
    personaName: "Skeptical Developer",
    fitScore: 66,
    summary: "Mostly fits, some jargon.",
    fits: ["Concrete examples"],
    misfits: [{ span: "leverage synergies", reason: "jargon", suggestion: "work together" }],
    llmUsed: true,
  };

  it("POSTs to the content-fit endpoint with content + sourceName", async () => {
    const post = vi.fn().mockResolvedValueOnce(verdict);
    const client = { post } as unknown as TuringClient;

    const result = await fetchPersonaContentFit(client, "site1", "p1", "some copy", "landing");

    expect(post).toHaveBeenCalledWith("/sn/site1/persona/p1/content-fit", {
      content: "some copy",
      sourceName: "landing",
    });
    expect(result).toEqual(verdict);
  });

  it("throws on an empty response", async () => {
    const post = vi.fn().mockResolvedValueOnce(undefined);
    const client = { post } as unknown as TuringClient;

    await expect(fetchPersonaContentFit(client, "site1", "p1", "x")).rejects.toThrow(
      /empty content-fit/i,
    );
  });

  it("postSiteFlowSelect forwards an optional personaId", async () => {
    const post = vi.fn().mockResolvedValueOnce({
      success: true,
      pinnedFlowId: "f1",
      pinnedFlowName: "Flow",
      reason: null,
    });
    const client = { post } as unknown as TuringClient;

    await postSiteFlowSelect(client, "site1", "conv1", "Flow", "p1");

    expect(post).toHaveBeenCalledWith("/sn/site1/chat/flow-select", {
      conversationId: "conv1",
      flow: "Flow",
      personaId: "p1",
    });
  });
});
