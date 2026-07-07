import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

const useEvalDatasets = vi.fn();
const useEvalGraderStacks = vi.fn();
const useEvalReviewInbox = vi.fn();
const useSubmitReview = vi.fn();
const usePromoteReview = vi.fn();
const useAiAgents = vi.fn();

vi.mock("@/api/queries/eval-studio.queries", () => ({
  useEvalDatasets: () => useEvalDatasets(),
  useEvalGraderStacks: () => useEvalGraderStacks(),
  useEvalReviewInbox: (agentId?: string) => useEvalReviewInbox(agentId),
  useSubmitReview: (agentId?: string) => useSubmitReview(agentId),
  usePromoteReview: (agentId?: string) => usePromoteReview(agentId),
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
});
