import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

const useEvalDatasets = vi.fn();
const useEvalGraderStacks = vi.fn();

vi.mock("@/api/queries/eval-studio.queries", () => ({
  useEvalDatasets: () => useEvalDatasets(),
  useEvalGraderStacks: () => useEvalGraderStacks(),
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
});
