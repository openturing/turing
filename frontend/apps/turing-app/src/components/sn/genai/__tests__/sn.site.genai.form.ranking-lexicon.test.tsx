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
// Hoisted: the form instantiates the service at module scope, so vi.mock's factory
// runs before a plain `const` would have initialised.
const preview = vi.hoisted(() => vi.fn());

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
vi.mock("@/services/sn/sn.ranking-lexicon.service", () => ({
  TurSNRankingLexiconService: class {
    preview = preview;
  },
}));

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
    preview.mockReset();
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

  // T943 / §LVIII.8 — the check. Ranking is fail-open, so this panel is the only
  // place an admin learns a bundle did not parse, or parsed and resolved nothing.

  it("checks the typed bundle against the admin's own sample question", async () => {
    preview.mockResolvedValue({
      valid: true,
      language: "en",
      addedAscWords: [],
      addedDescWords: ["torquiest"],
      addedSortTriggers: [],
      addedSynonyms: ["torquiest → torque"],
      sortResolved: true,
      field: "specs_torque",
      order: "desc",
    });
    renderForm();

    const bundle = '{"descWords":["torquiest"]}';
    fireEvent.change(lexiconField(), { target: { value: bundle } });
    fireEvent.change(
      screen.getByPlaceholderText("forms.snGenai.rankingLexiconSamplePlaceholder"),
      { target: { value: "the torquiest engine" } },
    );
    fireEvent.click(screen.getByText("forms.snGenai.rankingLexiconCheck"));

    // The draft is what gets checked — not the saved site, which does not have it yet.
    await waitFor(() => expect(preview).toHaveBeenCalledWith({
      siteName: "catalog",
      lexicon: bundle,
      query: "the torquiest engine",
    }));
    expect(await screen.findByText("forms.snGenai.rankingLexiconSort")).toBeInTheDocument();
  });

  it("says the bundle did not parse, which nothing else would tell the admin", async () => {
    preview.mockResolvedValue({
      valid: false,
      error: "Unexpected character",
      language: "en",
      addedAscWords: [],
      addedDescWords: [],
      addedSortTriggers: [],
      addedSynonyms: [],
      sortResolved: false,
    });
    renderForm();

    fireEvent.change(lexiconField(), { target: { value: "{ not json" } });
    fireEvent.click(screen.getByText("forms.snGenai.rankingLexiconCheck"));

    expect(await screen.findByText("forms.snGenai.rankingLexiconInvalid")).toBeInTheDocument();
    expect(screen.getByText("Unexpected character")).toBeInTheDocument();
  });

  it("distinguishes a bundle that parsed from one that resolved a sort", async () => {
    // The case a yes/no validator would have called fine: valid JSON, a real added
    // word, and no sort because it bridges to no declared field.
    preview.mockResolvedValue({
      valid: true,
      language: "en",
      addedAscWords: [],
      addedDescWords: ["torquiest"],
      addedSortTriggers: [],
      addedSynonyms: [],
      sortResolved: false,
    });
    renderForm();

    fireEvent.click(screen.getByText("forms.snGenai.rankingLexiconCheck"));

    expect(await screen.findByText("forms.snGenai.rankingLexiconNoSort")).toBeInTheDocument();
  });

  it("shows no result panel until the check has been run", () => {
    renderForm();

    expect(screen.queryByText("forms.snGenai.rankingLexiconNoSort")).not.toBeInTheDocument();
    expect(screen.queryByText("forms.snGenai.rankingLexiconInvalid")).not.toBeInTheDocument();
  });
});
