import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { TurSNSite } from "@/models/sn/sn-site.model";

/**
 * T817 / §LVIII.4 (Block BJ) — the per-site ranking-lexicon override.
 *
 * The payload contract is the whole point: a site that types nothing must submit an
 * empty value (ranking stays the shipped bundles), and a site that types a bundle
 * must submit it verbatim — the backend is what merges and what forgives a typo, so
 * the form must not "helpfully" validate or rewrite it.
 */

const mutateAsync = vi.fn();

vi.mock("@/api/queries/sn-site.queries", () => ({
  useUpdateSnSite: () => ({ mutateAsync, isPending: false }),
}));

vi.mock("@/api/queries/persona.queries", () => ({
  usePersonas: () => ({ data: [] }),
}));

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

async function submitAndReadPayload() {
  fireEvent.click(screen.getAllByText("forms.formActions.saveChanges")[0]);
  await waitFor(() => expect(mutateAsync).toHaveBeenCalled());
  return mutateAsync.mock.calls[0][0].turSNSiteGenAi;
}

/** The ranking-lexicon textarea, found by its placeholder key. */
function lexiconField() {
  return screen.getByPlaceholderText("forms.snGenai.rankingLexiconPlaceholder");
}

describe("SNSiteGenAiForm — per-site ranking lexicon (Block BJ)", () => {
  beforeEach(() => {
    mutateAsync.mockReset();
    mutateAsync.mockResolvedValue({ id: "site-1" });
  });

  it("renders the ranking-vocabulary field inside the planning card", () => {
    renderForm();

    expect(screen.getByText("forms.snGenai.rankingLexicon")).toBeInTheDocument();
    expect(lexiconField()).toBeInTheDocument();
  });

  it("submits nothing for a site that declares no vocabulary of its own", async () => {
    renderForm();

    const payload = await submitAndReadPayload();

    expect(payload.rankingLexicon).toBe("");
  });

  it("renders the persisted bundle and submits an edited one verbatim", async () => {
    const stored = '{"descWords":["torquiest"]}';
    renderForm({ rankingLexicon: stored });
    expect(lexiconField()).toHaveValue(stored);

    const edited = '{"descWords":["torquiest"],"synonyms":{"torquiest":["torque"]}}';
    fireEvent.change(lexiconField(), { target: { value: edited } });
    const payload = await submitAndReadPayload();

    expect(payload.rankingLexicon).toBe(edited);
  });

  it("does not block a save on an unparseable bundle — the backend forgives it", async () => {
    renderForm();

    fireEvent.change(lexiconField(), { target: { value: "{ not json" } });
    const payload = await submitAndReadPayload();

    expect(payload.rankingLexicon).toBe("{ not json");
  });
});
