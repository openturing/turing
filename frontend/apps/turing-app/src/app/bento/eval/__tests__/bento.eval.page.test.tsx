import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

const useEvalDatasets = vi.fn();
const useEvalGraderStacks = vi.fn();
const useEvalReviewInbox = vi.fn();
const useSubmitReview = vi.fn();
const usePromoteReview = vi.fn();
const useEvalAgreement = vi.fn();
const useEvalCalibration = vi.fn();
const useSampleAudit = vi.fn();
const useAiAgents = vi.fn();
const useGenerateRows = vi.fn();
const useAugmentDataset = vi.fn();
const useSaveReviewedRows = vi.fn();
const useEvalRunHistory = vi.fn();
const useRunEval = vi.fn();
const useOnlineEvalHistory = vi.fn();
const useRunOnlineEval = vi.fn();
const usePromoteOnlineBaseline = vi.fn();
const useNLFacetEvalAvailable = vi.fn();
const useNLFacetDatasets = vi.fn();
const useImportNLFacetDataset = vi.fn();
const useRunNLFacetDataset = vi.fn();
const useDeleteNLFacetDataset = vi.fn();
const useComparePlanningForNLFacetDataset = vi.fn();

vi.mock("@/api/queries/sn-nl-facet-eval.queries", () => ({
  useNLFacetEvalAvailable: () => useNLFacetEvalAvailable(),
  useNLFacetDatasets: () => useNLFacetDatasets(),
  useImportNLFacetDataset: () => useImportNLFacetDataset(),
  useRunNLFacetDataset: () => useRunNLFacetDataset(),
  useDeleteNLFacetDataset: () => useDeleteNLFacetDataset(),
  useComparePlanningForNLFacetDataset: () => useComparePlanningForNLFacetDataset(),
}));

vi.mock("@/api/queries/eval-studio.queries", () => ({
  useEvalDatasets: () => useEvalDatasets(),
  useEvalGraderStacks: () => useEvalGraderStacks(),
  useEvalReviewInbox: (agentId?: string) => useEvalReviewInbox(agentId),
  useSubmitReview: (agentId?: string) => useSubmitReview(agentId),
  usePromoteReview: (agentId?: string) => usePromoteReview(agentId),
  useEvalAgreement: (agentId?: string) => useEvalAgreement(agentId),
  useEvalCalibration: (agentId?: string) => useEvalCalibration(agentId),
  useSampleAudit: (agentId?: string) => useSampleAudit(agentId),
  useGenerateRows: () => useGenerateRows(),
  useAugmentDataset: () => useAugmentDataset(),
  useSaveReviewedRows: () => useSaveReviewedRows(),
  useEvalRunHistory: (agentId?: string) => useEvalRunHistory(agentId),
  useRunEval: (agentId?: string) => useRunEval(agentId),
  useOnlineEvalHistory: (agentId?: string) => useOnlineEvalHistory(agentId),
  useRunOnlineEval: (agentId?: string) => useRunOnlineEval(agentId),
  usePromoteOnlineBaseline: (agentId?: string) => usePromoteOnlineBaseline(agentId),
}));

vi.mock("@/api/queries/ai-agent.queries", () => ({
  useAiAgents: () => useAiAgents(),
}));

import BentoEvalPage from "../bento.eval.page";

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <BentoEvalPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("BentoEvalPage", () => {
  beforeEach(() => {
    useEvalDatasets.mockReturnValue({
      data: [{ id: "d1", name: "Lead capture QA", version: 2, rowCount: 12 }],
      isError: false,
    });
    useEvalGraderStacks.mockReturnValue({
      data: [{ id: "s1", name: "Strict stack", configs: [{ graderId: "exact" }] }],
      isError: false,
    });
    useAiAgents.mockReturnValue({ data: [{ id: "a1", title: "Lead Bot" }] });
    useEvalReviewInbox.mockReturnValue({ data: [], isError: false });
    useSubmitReview.mockReturnValue({ mutate: vi.fn(), isPending: false, isError: false });
    usePromoteReview.mockReturnValue({ mutate: vi.fn(), isPending: false, isError: false });
    useEvalAgreement.mockReturnValue({
      data: { items: 0, minRaters: 0, maxRaters: 0, kappa: null, interpretation: "n/a" },
    });
    useEvalCalibration.mockReturnValue({
      data: {
        audited: 0,
        kappa: null,
        interpretation: "n/a",
        modelPassHumanPass: 0,
        modelPassHumanFail: 0,
        modelFailHumanPass: 0,
        modelFailHumanFail: 0,
        suggestion: "",
      },
    });
    useSampleAudit.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: false,
      isSuccess: false,
    });
    useGenerateRows.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: false,
      isSuccess: false,
      data: undefined,
    });
    useAugmentDataset.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: false,
      isSuccess: false,
    });
    useSaveReviewedRows.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: false,
      isSuccess: false,
    });
    useEvalRunHistory.mockReturnValue({ data: [], isError: false });
    useRunEval.mockReturnValue({ mutate: vi.fn(), isPending: false, isError: false });
    useOnlineEvalHistory.mockReturnValue({ data: [], isError: false });
    useRunOnlineEval.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: false,
      data: undefined,
    });
    usePromoteOnlineBaseline.mockReturnValue({ mutate: vi.fn(), isPending: false });
    useNLFacetEvalAvailable.mockReturnValue({ data: true });
    useNLFacetDatasets.mockReturnValue({ data: [], isError: false });
    useImportNLFacetDataset.mockReturnValue({ mutate: vi.fn(), isPending: false });
    useRunNLFacetDataset.mockReturnValue({ mutate: vi.fn(), isPending: false, variables: undefined });
    useDeleteNLFacetDataset.mockReturnValue({ mutate: vi.fn(), isPending: false });
    useComparePlanningForNLFacetDataset.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      variables: undefined,
    });
  });

  it("renders the Eval Studio hero and the datasets tab by default", () => {
    renderPage();
    expect(screen.getByText("Eval Studio")).toBeInTheDocument();
    expect(screen.getByText("Lead capture QA")).toBeInTheDocument();
  });

  it("switches to the grader stacks tab", () => {
    renderPage();
    fireEvent.click(screen.getByText("Grader stacks"));
    expect(screen.getByText("Strict stack")).toBeInTheDocument();
  });

  it("shows an empty state when there are no datasets", () => {
    useEvalDatasets.mockReturnValue({ data: [], isError: false });
    renderPage();
    expect(screen.getByText("No datasets yet")).toBeInTheDocument();
  });

  it("switches to the review inbox and shows the agent selector", () => {
    renderPage();
    fireEvent.click(screen.getByText("Review inbox"));
    expect(screen.getByText("Select an agent…")).toBeInTheDocument();
    expect(screen.getByText("Lead Bot")).toBeInTheDocument();
  });

  it("lists pending review tasks once an agent is selected", () => {
    useEvalReviewInbox.mockReturnValue({
      data: [{ id: "t1", agentId: "a1", caseName: "greeting", graderId: "human-review", status: "PENDING" }],
      isError: false,
    });
    renderPage();
    fireEvent.click(screen.getByText("Review inbox"));
    fireEvent.change(screen.getByLabelText("Agent"), { target: { value: "a1" } });
    expect(screen.getByText("greeting")).toBeInTheDocument();
  });

  it("switches to the generate tab and shows the two source panels", () => {
    renderPage();
    fireEvent.click(screen.getByText("Generate"));
    expect(screen.getByText("Generate from an agent")).toBeInTheDocument();
    expect(screen.getByText("Augment a dataset")).toBeInTheDocument();
    expect(screen.getByText("No candidate rows yet")).toBeInTheDocument();
  });

  it("renders generated drafts in an editable review form and saves them", () => {
    // The generate mutation resolves with two candidate rows.
    useGenerateRows.mockReturnValue({
      mutate: (_vars: unknown, opts?: { onSuccess?: (rows: unknown[]) => void }) =>
        opts?.onSuccess?.([
          {
            id: null,
            name: "happy path",
            seedTurnsJson: '["I want to enroll"]',
            expectedOutcome: "CAPTURED",
            tags: "generated",
            sortOrder: 0,
          },
        ]),
      isPending: false,
      isError: false,
      isSuccess: true,
      data: [{}],
    });
    const saveMutate = vi.fn();
    useSaveReviewedRows.mockReturnValue({
      mutate: saveMutate,
      isPending: false,
      isError: false,
      isSuccess: false,
    });

    renderPage();
    fireEvent.click(screen.getByText("Generate"));
    fireEvent.change(screen.getByLabelText("Agent"), { target: { value: "a1" } });
    // Both the tab and the action button read "Generate"; the action is the last.
    const generateButtons = screen.getAllByRole("button", { name: /Generate$/ });
    fireEvent.click(generateButtons[generateButtons.length - 1]);

    // The draft appears in the review form...
    expect(screen.getByDisplayValue("happy path")).toBeInTheDocument();
    expect(screen.getByText(/candidate row\(s\)/)).toBeInTheDocument();

    // ...and saving hands the reviewed rows to the mutation.
    fireEvent.click(screen.getByText("Save reviewed rows"));
    expect(saveMutate).toHaveBeenCalled();
  });

  it("switches to the NL→Facet tab and shows its empty state", () => {
    renderPage();
    fireEvent.click(screen.getByText("NL→Facet"));
    expect(screen.getByText("No NL→facet datasets yet")).toBeInTheDocument();
  });

  it("lists an NL→facet dataset and runs it, showing the report", () => {
    useNLFacetDatasets.mockReturnValue({
      data: [{ id: "nf1", name: "catalog pack", version: 1, rowCount: 3 }],
      isError: false,
    });
    useRunNLFacetDataset.mockReturnValue({
      mutate: (_id: string, opts?: { onSuccess?: (r: unknown) => void }) =>
        opts?.onSuccess?.({
          packName: "catalog pack",
          passed: true,
          caseCount: 3,
          passedCount: 3,
          score: 1,
          results: [{ caseName: "cheap online", passed: true, score: 1, findings: [], ungroundedFields: [] }],
          error: null,
        }),
      isPending: false,
      variables: undefined,
    });
    renderPage();
    fireEvent.click(screen.getByText("NL→Facet"));
    expect(screen.getByText("catalog pack")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Run"));
    // The per-case breakdown renders once the run resolves (the summary line
    // uses i18n interpolation, which the test mock does not expand).
    expect(screen.getByText("cheap online")).toBeInTheDocument();
  });

  it("compares the planning strategies side by side and shows the plan-only caveats", () => {
    useNLFacetDatasets.mockReturnValue({
      data: [{ id: "nf1", name: "catalog pack", version: 1, rowCount: 3 }],
      isError: false,
    });
    const hybridCaveat =
      "HYBRID scores identically to DETERMINISTIC by construction: its escalation only fires on live retrieval.";
    useComparePlanningForNLFacetDataset.mockReturnValue({
      mutate: (_id: string, opts?: { onSuccess?: (c: unknown) => void }) =>
        opts?.onSuccess?.({
          packName: "catalog pack",
          caseCount: 3,
          maxPasses: 2,
          strategies: [
            {
              strategy: "DETERMINISTIC",
              passed: true,
              passedCount: 3,
              score: 1,
              llmPasses: 3,
              elapsedMillis: 120,
              note: "single facet parse (depth does not apply)",
              results: [],
            },
            {
              strategy: "LLM_ASSISTED",
              passed: false,
              passedCount: 2,
              score: 0.75,
              llmPasses: 9,
              elapsedMillis: 940,
              note: "depth 2 — parse + judge + refine",
              results: [],
            },
            {
              strategy: "HYBRID",
              passed: true,
              passedCount: 3,
              score: 1,
              llmPasses: 3,
              elapsedMillis: 118,
              note: "fast-path only: identical to DETERMINISTIC here",
              results: [],
            },
          ],
          caveats: [hybridCaveat],
          error: null,
        }),
      isPending: false,
      variables: undefined,
    });

    renderPage();
    fireEvent.click(screen.getByText("NL→Facet"));
    fireEvent.click(screen.getByText("Compare planners"));

    // All three strategies on the three axes the operator trades off…
    expect(screen.getByText("DETERMINISTIC")).toBeInTheDocument();
    expect(screen.getByText("LLM_ASSISTED")).toBeInTheDocument();
    expect(screen.getByText("HYBRID")).toBeInTheDocument();
    expect(screen.getByText("75%")).toBeInTheDocument();
    expect(screen.getByText("9")).toBeInTheDocument();
    expect(screen.getByText("940 ms")).toBeInTheDocument();
    // …and the honesty about what a plan-only run cannot measure.
    expect(screen.getByText(hybridCaveat)).toBeInTheDocument();
  });

  it("switches to the runs tab and shows the run-history empty state", () => {
    renderPage();
    fireEvent.click(screen.getByText("Runs"));
    fireEvent.change(screen.getByLabelText("Agent"), { target: { value: "a1" } });
    expect(screen.getByText("No eval runs yet")).toBeInTheDocument();
  });

  it("drills into a run and shows the per-case breakdown + a failing slot diff", () => {
    useEvalRunHistory.mockReturnValue({
      data: [
        {
          reportId: "r1",
          createdAt: "2026-07-07T10:00:00",
          passed: false,
          score: 0.5,
          caseCount: 2,
          passedCount: 1,
          baseline: false,
          regressed: true,
          pendingReview: false,
          results: [
            {
              caseId: "c1",
              caseName: "greeting",
              passed: false,
              score: 0,
              expectedOutcome: "CAPTURED",
              actualOutcome: "ABANDONED",
              finalNodeId: null,
              slotDiffs: [
                { slot: "email", expected: "a@b.com", actual: null, match: false },
              ],
              rubricVerdict: "pass",
              rubricRationale: null,
              error: null,
            },
          ],
          error: null,
        },
      ],
      isError: false,
    });
    renderPage();
    fireEvent.click(screen.getByText("Runs"));
    fireEvent.change(screen.getByLabelText("Agent"), { target: { value: "a1" } });
    expect(screen.getByText("regressed")).toBeInTheDocument();
    expect(screen.getByText("greeting")).toBeInTheDocument();
    expect(screen.getByText("email")).toBeInTheDocument();
  });

  it("switches to the online tab and shows the online-eval empty state", () => {
    renderPage();
    fireEvent.click(screen.getByText("Online"));
    fireEvent.change(screen.getByLabelText("Agent"), { target: { value: "a1" } });
    expect(screen.getByText("No online-eval snapshots yet")).toBeInTheDocument();
  });

  it("renders an online-eval snapshot with a drift badge + reason", () => {
    useOnlineEvalHistory.mockReturnValue({
      data: [
        {
          id: "snap-1",
          agentId: "a1",
          createdAt: "2026-07-08T10:00:00",
          windowStart: "2026-07-07T10:00:00",
          windowEnd: "2026-07-08T10:00:00",
          sampledSessions: 40,
          gradedSessions: 30,
          graderStackId: "s1",
          meanScore: 0.6,
          passRate: 0.7,
          negativeSentimentRate: 0.5,
          staleCitationRate: -1,
          failingSessionRate: 0.45,
          baseline: false,
          driftDetected: true,
          driftReason: "failing sessions 0.45 ≥ 0.40; negative sentiment 0.50 ≥ 0.40",
          note: null,
        },
      ],
      isError: false,
    });
    renderPage();
    fireEvent.click(screen.getByText("Online"));
    fireEvent.change(screen.getByLabelText("Agent"), { target: { value: "a1" } });
    expect(screen.getByText("drift")).toBeInTheDocument();
    expect(screen.getByText(/failing sessions 0.45/)).toBeInTheDocument();
    expect(screen.getByText("Set baseline")).toBeInTheDocument();
  });

  it("shows the calibration panel with kappa metrics once an agent is selected", () => {
    useEvalAgreement.mockReturnValue({
      data: { items: 3, minRaters: 2, maxRaters: 3, kappa: 0.72, interpretation: "substantial" },
    });
    useEvalCalibration.mockReturnValue({
      data: {
        audited: 5,
        agreementRate: 0.8,
        kappa: 0.55,
        interpretation: "moderate",
        modelPassHumanPass: 3,
        modelPassHumanFail: 1,
        modelFailHumanPass: 0,
        modelFailHumanFail: 1,
        suggestion: "The judge over-passes: 1 case(s) it passed were failed by reviewers.",
      },
    });
    renderPage();
    fireEvent.click(screen.getByText("Review inbox"));
    fireEvent.change(screen.getByLabelText("Agent"), { target: { value: "a1" } });
    expect(screen.getByText("Calibration & agreement")).toBeInTheDocument();
    expect(screen.getByText("κ 0.72")).toBeInTheDocument();
    expect(screen.getByText("κ 0.55")).toBeInTheDocument();
    expect(screen.getByText(/over-passes/)).toBeInTheDocument();
  });
});
