import { describe, expect, it } from "vitest";

import {
  buildTargetTrends,
  fitTone,
  mediumFitFloor,
  seriesDelta,
  type MatchTrend,
} from "../persona-match.model";

const RUN_1 = "2026-07-26T03:30:00Z";
const RUN_2 = "2026-07-27T03:30:00Z";

function point(evaluatedAt: string, fitScore: number, extra: Partial<{ llmUsed: boolean; reused: boolean }> = {}) {
  return {
    evaluatedAt,
    fitScore,
    readabilityScore: fitScore,
    llmUsed: extra.llmUsed ?? true,
    reused: extra.reused ?? false,
  };
}

/** One content (s1) scored against two personas (a1, a2) across two runs. */
const TREND: MatchTrend = {
  projectId: "p1",
  runCount: 2,
  average: [],
  cells: [
    {
      sourceId: "s1",
      personaId: "a1",
      points: [point(RUN_1, 90), point(RUN_2, 70)],
    },
    {
      sourceId: "s1",
      personaId: "a2",
      points: [point(RUN_1, 70), point(RUN_2, 60)],
    },
  ],
};

describe("buildTargetTrends (T830)", () => {
  it("averages a content's cells per run", () => {
    const byContent = buildTargetTrends(TREND, "source");
    const series = byContent.get("s1")!;
    expect(series).toHaveLength(2);
    expect(series[0]).toMatchObject({ evaluatedAt: RUN_1, fitScore: 80 });
    expect(series[1]).toMatchObject({ evaluatedAt: RUN_2, fitScore: 65 });
  });

  it("keeps each persona's own series separate", () => {
    const byPersona = buildTargetTrends(TREND, "persona");
    expect(byPersona.get("a1")!.map((p) => p.fitScore)).toEqual([90, 70]);
    expect(byPersona.get("a2")!.map((p) => p.fitScore)).toEqual([70, 60]);
  });

  it("orders points oldest-first regardless of input order", () => {
    const reversed: MatchTrend = {
      ...TREND,
      cells: [{ sourceId: "s1", personaId: "a1", points: [point(RUN_2, 70), point(RUN_1, 90)] }],
    };
    expect(buildTargetTrends(reversed, "source").get("s1")!.map((p) => p.evaluatedAt)).toEqual([
      RUN_1,
      RUN_2,
    ]);
  });

  it("marks a run llmUsed when any cell used the LLM, reused only when all did", () => {
    const mixed: MatchTrend = {
      ...TREND,
      cells: [
        { sourceId: "s1", personaId: "a1", points: [point(RUN_1, 80, { llmUsed: true, reused: false })] },
        { sourceId: "s1", personaId: "a2", points: [point(RUN_1, 60, { llmUsed: false, reused: true })] },
      ],
    };
    const series = buildTargetTrends(mixed, "source").get("s1")!;
    expect(series[0]!.llmUsed).toBe(true);
    expect(series[0]!.reused).toBe(false);
    expect(series[0]!.fitScore).toBe(70);
  });

  it("is empty for an absent trend payload", () => {
    expect(buildTargetTrends(undefined, "source").size).toBe(0);
  });
});

describe("seriesDelta (T830)", () => {
  it("reports the movement between the last two runs", () => {
    expect(seriesDelta([point(RUN_1, 80), point(RUN_2, 71.44)])).toBe(-8.6);
  });

  it("is null with fewer than two runs on record", () => {
    expect(seriesDelta([point(RUN_1, 80)])).toBeNull();
    expect(seriesDelta([])).toBeNull();
    expect(seriesDelta(undefined)).toBeNull();
  });
});

describe("fitTone bands (T826)", () => {
  it("keeps the classic 75/50 bands at the platform default", () => {
    expect(fitTone(75).level).toBe("high");
    expect(fitTone(74).level).toBe("medium");
    expect(fitTone(50).level).toBe("medium");
    expect(fitTone(49).level).toBe("low");
  });

  it("moves both boundaries proportionally with the project bar", () => {
    expect(mediumFitFloor(60)).toBe(40);
    expect(fitTone(60, 60).level).toBe("high");
    expect(fitTone(59, 60).level).toBe("medium");
    expect(fitTone(40, 60).level).toBe("medium");
    expect(fitTone(39, 60).level).toBe("low");
  });
});
