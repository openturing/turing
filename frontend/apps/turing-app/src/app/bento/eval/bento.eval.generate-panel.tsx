import { useState } from "react";
import { useTranslation } from "react-i18next";
import {
  IconChecks,
  IconDeviceFloppy,
  IconRobot,
  IconSparkles,
  IconTrash,
  IconWand,
} from "@tabler/icons-react";

import { useAiAgents } from "@/api/queries/ai-agent.queries";
import {
  useAugmentDataset,
  useEvalDatasets,
  useGenerateRows,
  useSaveReviewedRows,
} from "@/api/queries/eval-studio.queries";
import type { TurEvalDatasetRowDraft } from "@/models/eval/eval-studio.model";

const OUTCOMES = ["ANY", "CAPTURED", "ABANDONED", "HANDOFF"] as const;

/** Turns a JSON-array-of-strings column into newline-separated editable text. */
function turnsToText(seedTurnsJson?: string | null): string {
  if (!seedTurnsJson) return "";
  try {
    const parsed = JSON.parse(seedTurnsJson);
    return Array.isArray(parsed) ? parsed.join("\n") : "";
  } catch {
    return "";
  }
}

/** Serializes newline-separated turn text back into a JSON string array. */
function textToTurns(text: string): string {
  const turns = text
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
  return JSON.stringify(turns);
}

/**
 * T597 / §XXXIII.12 — LLM-assisted dataset generation & augmentation, always as
 * a human-reviewed draft (never auto-trusted). Synthesize candidate rows from an
 * agent's system prompt / slots / flow goals, or paraphrase an existing dataset's
 * rows into variants; edit/prune the drafts in place, then save them into a new
 * or existing dataset. Nothing persists until "Save reviewed rows".
 *
 * @since 2026.3.4
 */
export function BentoEvalGeneratePanel() {
  const { t } = useTranslation();
  const { data: agents } = useAiAgents();
  const { data: datasets } = useEvalDatasets();

  const generate = useGenerateRows();
  const augment = useAugmentDataset();
  const save = useSaveReviewedRows();

  const [agentId, setAgentId] = useState<string>("");
  const [count, setCount] = useState<string>("10");
  const [augmentDatasetId, setAugmentDatasetId] = useState<string>("");
  const [variants, setVariants] = useState<string>("2");

  const [drafts, setDrafts] = useState<TurEvalDatasetRowDraft[]>([]);
  const [name, setName] = useState<string>("");
  const [targetDatasetId, setTargetDatasetId] = useState<string>("");

  const updateDraft = (index: number, patch: Partial<TurEvalDatasetRowDraft>) => {
    setDrafts((current) =>
      current.map((row, i) => (i === index ? { ...row, ...patch } : row)),
    );
  };
  const removeDraft = (index: number) => {
    setDrafts((current) => current.filter((_, i) => i !== index));
  };

  const runGenerate = () => {
    if (!agentId) return;
    generate.mutate(
      { agentId, count: Math.max(1, Number(count) || 1) },
      { onSuccess: (rows) => setDrafts((current) => [...current, ...rows]) },
    );
  };
  const runAugment = () => {
    if (!augmentDatasetId) return;
    augment.mutate(
      { datasetId: augmentDatasetId, variants: Math.max(1, Number(variants) || 1) },
      { onSuccess: (rows) => setDrafts((current) => [...current, ...rows]) },
    );
  };
  const runSave = () => {
    if (drafts.length === 0) return;
    save.mutate(
      {
        rows: drafts,
        name: name.trim() || undefined,
        datasetId: targetDatasetId || undefined,
      },
      {
        onSuccess: () => {
          setDrafts([]);
          setName("");
          setTargetDatasetId("");
        },
      },
    );
  };

  const busy = generate.isPending || augment.isPending;

  return (
    <div className="space-y-4">
      {/* Sources: generate from an agent, or augment a dataset */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <div className="bento-tile bento-glass space-y-3 rounded-2xl border border-border/60 p-4">
          <div className="flex items-center gap-2">
            <IconRobot size={18} className="text-muted-foreground" />
            <h3 className="text-sm font-semibold">
              {t("evalStudio.generate.fromAgent", { defaultValue: "Generate from an agent" })}
            </h3>
          </div>
          <p className="text-xs text-muted-foreground">
            {t("evalStudio.generate.fromAgentHint", {
              defaultValue:
                "Ground the model on the agent's system prompt, slots and flow goals to synthesize candidate cases.",
            })}
          </p>
          <div className="flex flex-wrap items-end gap-2">
            <select
              aria-label={t("evalStudio.generate.agent", { defaultValue: "Agent" })}
              value={agentId}
              onChange={(event) => setAgentId(event.target.value)}
              className="min-w-48 rounded-lg border border-border/60 bg-card/60 px-3 py-1.5 text-sm"
            >
              <option value="">
                {t("evalStudio.generate.pickAgent", { defaultValue: "Select an agent…" })}
              </option>
              {(agents ?? []).map((agent) => (
                <option key={agent.id} value={agent.id}>
                  {agent.title}
                </option>
              ))}
            </select>
            <input
              type="number"
              min={1}
              aria-label={t("evalStudio.generate.count", { defaultValue: "How many" })}
              value={count}
              onChange={(event) => setCount(event.target.value)}
              className="w-20 rounded-lg border border-border/60 bg-card/60 px-3 py-1.5 text-sm"
            />
            <button
              type="button"
              disabled={!agentId || busy}
              onClick={runGenerate}
              className="inline-flex items-center gap-1.5 rounded-lg bg-gradient-to-br from-amber-500 to-rose-500 px-3.5 py-1.5 text-sm font-medium text-white shadow disabled:opacity-50"
            >
              <IconSparkles size={16} />
              {t("evalStudio.generate.generate", { defaultValue: "Generate" })}
            </button>
          </div>
          {generate.isSuccess && generate.data?.length === 0 && (
            <p className="text-xs text-amber-600 dark:text-amber-400">
              {t("evalStudio.generate.noLlm", {
                defaultValue:
                  "The model returned no rows — attach an enabled LLM to the agent (or set a default) and retry.",
              })}
            </p>
          )}
          {generate.isError && (
            <p className="text-xs text-rose-600 dark:text-rose-400">
              {t("evalStudio.generate.error", { defaultValue: "Generation failed." })}
            </p>
          )}
        </div>

        <div className="bento-tile bento-glass space-y-3 rounded-2xl border border-border/60 p-4">
          <div className="flex items-center gap-2">
            <IconWand size={18} className="text-muted-foreground" />
            <h3 className="text-sm font-semibold">
              {t("evalStudio.generate.augment", { defaultValue: "Augment a dataset" })}
            </h3>
          </div>
          <p className="text-xs text-muted-foreground">
            {t("evalStudio.generate.augmentHint", {
              defaultValue:
                "Paraphrase an existing dataset's rows into fresh phrasings that keep the same expectations.",
            })}
          </p>
          <div className="flex flex-wrap items-end gap-2">
            <select
              aria-label={t("evalStudio.generate.dataset", { defaultValue: "Dataset" })}
              value={augmentDatasetId}
              onChange={(event) => setAugmentDatasetId(event.target.value)}
              className="min-w-48 rounded-lg border border-border/60 bg-card/60 px-3 py-1.5 text-sm"
            >
              <option value="">
                {t("evalStudio.generate.pickDataset", { defaultValue: "Select a dataset…" })}
              </option>
              {(datasets ?? []).map((dataset) => (
                <option key={dataset.id} value={dataset.id}>
                  {dataset.name}
                </option>
              ))}
            </select>
            <input
              type="number"
              min={1}
              aria-label={t("evalStudio.generate.variants", { defaultValue: "Variants / row" })}
              value={variants}
              onChange={(event) => setVariants(event.target.value)}
              className="w-20 rounded-lg border border-border/60 bg-card/60 px-3 py-1.5 text-sm"
            />
            <button
              type="button"
              disabled={!augmentDatasetId || busy}
              onClick={runAugment}
              className="inline-flex items-center gap-1.5 rounded-lg bg-gradient-to-br from-amber-500 to-rose-500 px-3.5 py-1.5 text-sm font-medium text-white shadow disabled:opacity-50"
            >
              <IconWand size={16} />
              {t("evalStudio.generate.augmentAction", { defaultValue: "Augment" })}
            </button>
          </div>
          {augment.isError && (
            <p className="text-xs text-rose-600 dark:text-rose-400">
              {t("evalStudio.generate.augmentError", { defaultValue: "Augmentation failed." })}
            </p>
          )}
        </div>
      </div>

      {/* Review form */}
      {drafts.length === 0 ? (
        <div className="bento-tile bento-glass rounded-2xl border border-border/60 p-8 text-center">
          <IconSparkles size={28} className="mx-auto mb-3 text-muted-foreground" />
          <p className="font-medium">
            {t("evalStudio.generate.reviewEmpty", { defaultValue: "No candidate rows yet" })}
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            {t("evalStudio.generate.reviewEmptyHint", {
              defaultValue:
                "Generate or augment above — candidates land here as an editable draft. Nothing is saved until you review and save.",
            })}
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="flex items-center gap-2 text-sm font-semibold">
              <IconChecks size={16} className="text-muted-foreground" />
              {t("evalStudio.generate.reviewTitle", {
                defaultValue: "Review {{count}} candidate row(s)",
                count: drafts.length,
              })}
            </h3>
            <button
              type="button"
              onClick={() => setDrafts([])}
              className="text-xs text-muted-foreground underline-offset-2 hover:underline"
            >
              {t("evalStudio.generate.discardAll", { defaultValue: "Discard all" })}
            </button>
          </div>

          <ul className="space-y-3">
            {drafts.map((row, index) => (
              <li
                key={index}
                className="bento-tile bento-glass space-y-2 rounded-2xl border border-border/60 p-4"
              >
                <div className="flex items-center gap-2">
                  <input
                    aria-label={t("evalStudio.generate.rowName", { defaultValue: "Case name" })}
                    value={row.name ?? ""}
                    onChange={(event) => updateDraft(index, { name: event.target.value })}
                    placeholder={t("evalStudio.generate.rowNamePlaceholder", {
                      defaultValue: "Case name",
                    })}
                    className="flex-1 rounded-lg border border-border/60 bg-card/60 px-3 py-1.5 text-sm font-medium"
                  />
                  {row.tags && (
                    <span className="rounded-full bg-muted/60 px-2 py-0.5 text-[11px] text-muted-foreground">
                      {row.tags}
                    </span>
                  )}
                  <button
                    type="button"
                    aria-label={t("evalStudio.generate.remove", { defaultValue: "Remove row" })}
                    onClick={() => removeDraft(index)}
                    className="rounded-lg border border-border/60 p-1.5 text-muted-foreground hover:text-rose-600"
                  >
                    <IconTrash size={15} />
                  </button>
                </div>

                <label className="block text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                  {t("evalStudio.generate.turns", { defaultValue: "User turns (one per line)" })}
                </label>
                <textarea
                  value={turnsToText(row.seedTurnsJson)}
                  onChange={(event) =>
                    updateDraft(index, { seedTurnsJson: textToTurns(event.target.value) })
                  }
                  rows={3}
                  className="w-full rounded-lg border border-border/60 bg-card/60 px-3 py-2 text-sm"
                />

                <div className="flex flex-wrap gap-2">
                  <select
                    aria-label={t("evalStudio.generate.outcome", { defaultValue: "Expected outcome" })}
                    value={row.expectedOutcome ?? "ANY"}
                    onChange={(event) => updateDraft(index, { expectedOutcome: event.target.value })}
                    className="rounded-lg border border-border/60 bg-card/60 px-3 py-1.5 text-sm"
                  >
                    {OUTCOMES.map((outcome) => (
                      <option key={outcome} value={outcome}>
                        {outcome}
                      </option>
                    ))}
                  </select>
                  <input
                    aria-label={t("evalStudio.generate.nodeId", { defaultValue: "Expected node id" })}
                    value={row.expectedNodeId ?? ""}
                    onChange={(event) =>
                      updateDraft(index, { expectedNodeId: event.target.value || null })
                    }
                    placeholder={t("evalStudio.generate.nodeIdPlaceholder", {
                      defaultValue: "node id (optional)",
                    })}
                    className="flex-1 rounded-lg border border-border/60 bg-card/60 px-3 py-1.5 text-sm"
                  />
                </div>

                <textarea
                  value={row.rubric ?? ""}
                  onChange={(event) => updateDraft(index, { rubric: event.target.value || null })}
                  rows={2}
                  placeholder={t("evalStudio.generate.rubricPlaceholder", {
                    defaultValue: "Rubric — one checkable assertion (optional)",
                  })}
                  className="w-full rounded-lg border border-border/60 bg-card/60 px-3 py-2 text-sm"
                />
              </li>
            ))}
          </ul>

          {/* Save destination */}
          <div className="bento-tile bento-glass flex flex-wrap items-end gap-2 rounded-2xl border border-border/60 p-4">
            <div>
              <label
                htmlFor="save-target"
                className="mb-1 block text-[11px] font-semibold uppercase tracking-wide text-muted-foreground"
              >
                {t("evalStudio.generate.saveTo", { defaultValue: "Save to" })}
              </label>
              <select
                id="save-target"
                value={targetDatasetId}
                onChange={(event) => setTargetDatasetId(event.target.value)}
                className="min-w-56 rounded-lg border border-border/60 bg-card/60 px-3 py-1.5 text-sm"
              >
                <option value="">
                  {t("evalStudio.generate.newDataset", { defaultValue: "New dataset…" })}
                </option>
                {(datasets ?? []).map((dataset) => (
                  <option key={dataset.id} value={dataset.id}>
                    {t("evalStudio.generate.appendTo", {
                      defaultValue: "Append to {{name}}",
                      name: dataset.name,
                    })}
                  </option>
                ))}
              </select>
            </div>
            {!targetDatasetId && (
              <input
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder={t("evalStudio.generate.namePlaceholder", {
                  defaultValue: "New dataset name",
                })}
                className="flex-1 rounded-lg border border-border/60 bg-card/60 px-3 py-1.5 text-sm"
              />
            )}
            <button
              type="button"
              disabled={save.isPending}
              onClick={runSave}
              className="inline-flex items-center gap-1.5 rounded-lg bg-gradient-to-br from-emerald-600 to-teal-600 px-3.5 py-1.5 text-sm font-medium text-white shadow disabled:opacity-50"
            >
              <IconDeviceFloppy size={16} />
              {t("evalStudio.generate.save", { defaultValue: "Save reviewed rows" })}
            </button>
            {save.isSuccess && (
              <span className="text-xs text-emerald-600 dark:text-emerald-400">
                {t("evalStudio.generate.saved", { defaultValue: "Saved." })}
              </span>
            )}
            {save.isError && (
              <span className="text-xs text-rose-600 dark:text-rose-400">
                {t("evalStudio.generate.saveError", { defaultValue: "Could not save." })}
              </span>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
