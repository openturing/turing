import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { TurSNSite } from "@/models/sn/sn-site.model";

/**
 * T818–T821 / §LIX (Block BK) — the Copilot Query Planning admin surface.
 *
 * What actually matters here is the payload contract, not the pixels: "Default" must
 * submit `null` (so the site inherits `turing.genai.copilot.planning.*` rather than
 * silently pinning today's strategy), a picked strategy must submit that enum value,
 * and the analysis-depth control must only be usable on a strategy that spends LLM
 * passes.
 */

const mutateAsync = vi.fn();

vi.mock("@/api/queries/sn-site.queries", () => ({
  useUpdateSnSite: () => ({ mutateAsync, isPending: false }),
}));

vi.mock("@/api/queries/persona.queries", () => ({
  usePersonas: () => ({ data: [] }),
}));

// The form eagerly loads reference data on mount; none of it is relevant to the
// planning card, so every service resolves empty.
vi.mock("@/services/agent/ai-agent.service", () => ({
  TurAIAgentService: class {
    query = () => Promise.resolve([]);
  },
}));
vi.mock("@/services/se/se.service", () => ({
  TurSEInstanceService: class {
    query = () => Promise.resolve([]);
  },
}));
vi.mock("@/services/embedding/embedding-model.service", () => ({
  TurEmbeddingModelService: class {
    query = () => Promise.resolve([]);
  },
}));
vi.mock("@/services/system/global-settings.service", () => ({
  TurGlobalSettingsService: class {
    query = () => Promise.resolve({ defaultLlmId: "", defaultAiAgentId: "" });
  },
}));
vi.mock("@/services/sn/sn.service", () => ({
  TurSNSiteService: class {
    getRagCores = () => Promise.resolve([]);
    getActiveReindex = () => Promise.resolve(null);
    subscribeToReindex = () => () => {};
  },
}));
vi.mock("@viglet/turing-react-sdk", () => ({
  postLlmChat: () => Promise.resolve(""),
}));

// eslint-disable-next-line import/first
import { SNSiteGenAiForm } from "../sn.site.genai.form";

function renderForm(genAi: Record<string, unknown> = {}) {
  const site = {
    id: "site-1",
    name: "catalog",
    description: "A catalog",
    turSNSiteGenAi: { id: "genai-1", ...genAi },
  } as unknown as TurSNSite;

  return render(
    <QueryClientProvider client={new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })}>
      <MemoryRouter>
        <SNSiteGenAiForm snSite={site} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

/** The submitted `turSNSiteGenAi` payload from the single save call. */
async function submitAndReadPayload() {
  fireEvent.click(screen.getAllByText("forms.formActions.saveChanges")[0]);
  await waitFor(() => expect(mutateAsync).toHaveBeenCalled());
  return mutateAsync.mock.calls[0][0].turSNSiteGenAi;
}

describe("SNSiteGenAiForm — copilot query planning (Block BK)", () => {
  beforeEach(() => {
    mutateAsync.mockReset();
    mutateAsync.mockResolvedValue({ id: "site-1" });
  });

  it("renders the planning card with all four strategy choices", () => {
    renderForm();

    expect(screen.getByText("forms.snGenai.copilotPlanningTitle")).toBeInTheDocument();
    expect(screen.getByText("forms.snGenai.copilotPlanningDefault")).toBeInTheDocument();
    expect(screen.getByText("forms.snGenai.copilotPlanningDeterministic")).toBeInTheDocument();
    expect(screen.getByText("forms.snGenai.copilotPlanningLlm")).toBeInTheDocument();
    expect(screen.getByText("forms.snGenai.copilotPlanningHybrid")).toBeInTheDocument();
  });

  it("submits null for a site that pins nothing, so the deployment default applies", async () => {
    renderForm();

    const payload = await submitAndReadPayload();

    expect(payload.copilotPlanningStrategy).toBeNull();
    expect(payload.copilotPlanningMaxPasses).toBeNull();
  });

  it("pins the picked strategy on save", async () => {
    renderForm();

    fireEvent.click(screen.getByText("forms.snGenai.copilotPlanningHybrid"));
    const payload = await submitAndReadPayload();

    expect(payload.copilotPlanningStrategy).toBe("HYBRID");
  });

  it("shows the persisted strategy and its own hint when the site already pinned one", () => {
    renderForm({ copilotPlanningStrategy: "LLM_ASSISTED", copilotPlanningMaxPasses: 1 });

    expect(screen.getByText("forms.snGenai.copilotPlanningLlmHint")).toBeInTheDocument();
  });

  it("explains the extra LLM cost only for a strategy that spends passes", () => {
    renderForm();
    expect(screen.queryByText("forms.snGenai.copilotPlanningCostTitle")).not.toBeInTheDocument();

    fireEvent.click(screen.getByText("forms.snGenai.copilotPlanningLlm"));
    expect(screen.getByText("forms.snGenai.copilotPlanningCostTitle")).toBeInTheDocument();
  });

  it("keeps the analysis-depth control disabled until an LLM strategy is picked", () => {
    renderForm();

    // The Select trigger renders the inherit placeholder; disabled on Default...
    const depthTrigger = screen
      .getAllByText("forms.snGenai.copilotPlanningDepthInherit")[0]
      .closest("button");
    expect(depthTrigger).toBeDisabled();

    // ...and on the deterministic strategy, which spends no passes either.
    fireEvent.click(screen.getByText("forms.snGenai.copilotPlanningDeterministic"));
    expect(depthTrigger).toBeDisabled();

    fireEvent.click(screen.getByText("forms.snGenai.copilotPlanningLlm"));
    expect(depthTrigger).toBeEnabled();
  });
});
