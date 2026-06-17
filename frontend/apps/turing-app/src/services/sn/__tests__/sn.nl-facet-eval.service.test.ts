import axios from "axios";
import { describe, expect, it, vi } from "vitest";

import { TurSNNLFacetEvalService } from "@/services/sn/sn.nl-facet-eval.service";
import type {
  TurNLFacetEvalPack,
  TurNLFacetEvalReport,
} from "@/models/sn/sn-nl-facet-eval.model.ts";

const mockGet = vi.mocked(axios.get);
const mockPost = vi.mocked(axios.post);

describe("TurSNNLFacetEvalService", () => {
  const service = new TurSNNLFacetEvalService();

  it("unwraps the availability flag from GET /sn/nl-facet-eval/available", async () => {
    mockGet.mockResolvedValueOnce({ data: { available: true } });

    await expect(service.available()).resolves.toBe(true);
    expect(mockGet).toHaveBeenCalledWith("/sn/nl-facet-eval/available");
  });

  it("posts the eval pack to /sn/nl-facet-eval and returns the report", async () => {
    const pack: TurNLFacetEvalPack = {
      name: "courses smoke test",
      index: "courses",
      locale: null,
      fields: [],
      cases: [
        {
          name: "filter by modality",
          query: "online courses",
          expect: { filters: [{ field: "modality", value: "online" }] },
        },
      ],
    };
    const report: TurNLFacetEvalReport = {
      packName: "courses smoke test",
      passed: true,
      caseCount: 1,
      passedCount: 1,
      score: 1,
      results: [
        {
          caseName: "filter by modality",
          passed: true,
          score: 1,
          findings: [],
          ungroundedFields: [],
        },
      ],
    };
    mockPost.mockResolvedValueOnce({ data: report });

    await expect(service.run(pack)).resolves.toEqual(report);
    expect(mockPost).toHaveBeenCalledWith("/sn/nl-facet-eval", pack);
  });
});
