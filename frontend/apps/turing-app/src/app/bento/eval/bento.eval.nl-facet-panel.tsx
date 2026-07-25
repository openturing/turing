import { useState } from "react";
import { useTranslation } from "react-i18next";
import {
  IconAlertTriangle,
  IconCircleCheck,
  IconCircleX,
  IconFilterSearch,
  IconPlayerPlay,
  IconScale,
  IconTrash,
  IconUpload,
} from "@tabler/icons-react";

import {
  useComparePlanningForNLFacetDataset,
  useDeleteNLFacetDataset,
  useImportNLFacetDataset,
  useNLFacetDatasets,
  useNLFacetEvalAvailable,
  useRunNLFacetDataset,
} from "@/api/queries/sn-nl-facet-eval.queries";
import type { TurEvalDataset } from "@/models/eval/eval-studio.model";
import type {
  TurCopilotPlanningComparison,
  TurNLFacetEvalPack,
  TurNLFacetEvalReport,
} from "@/models/sn/sn-nl-facet-eval.model";

/**
 * T601 / §XXXIII.16 — the Eval Studio's NL→Facet tab: the Block R
 * (SN NL→facet) island folded onto the shared eval-dataset model. A pack is
 * imported once as a reusable dataset, listed alongside the chat-flow datasets,
 * and run through the intact Block R scorer with a per-case pass/fail report.
 *
 * @since 2026.3.4
 */
export function BentoEvalNLFacetPanel() {
  const { t } = useTranslation();
  const { data: available } = useNLFacetEvalAvailable();
  const { data: datasets, isError } = useNLFacetDatasets();
  const importDataset = useImportNLFacetDataset();
  const runDataset = useRunNLFacetDataset();
  const comparePlanning = useComparePlanningForNLFacetDataset();
  const deleteDataset = useDeleteNLFacetDataset();

  const [packJson, setPackJson] = useState("");
  const [importError, setImportError] = useState<string | null>(null);
  const [reportByDataset, setReportByDataset] = useState<
    Record<string, TurNLFacetEvalReport>
  >({});
  const [comparisonByDataset, setComparisonByDataset] = useState<
    Record<string, TurCopilotPlanningComparison>
  >({});

  const onImport = () => {
    setImportError(null);
    let pack: TurNLFacetEvalPack;
    try {
      pack = JSON.parse(packJson) as TurNLFacetEvalPack;
    } catch {
      setImportError(
        t("evalStudio.nlFacet.invalidJson", { defaultValue: "Invalid JSON pack" }),
      );
      return;
    }
    importDataset.mutate(pack, {
      onSuccess: () => setPackJson(""),
      onError: () =>
        setImportError(
          t("common.connectionError", { resource: "NL→facet dataset" }),
        ),
    });
  };

  const onRun = (id: string) => {
    runDataset.mutate(id, {
      onSuccess: (report) =>
        setReportByDataset((prev) => ({ ...prev, [id]: report })),
    });
  };

  const onComparePlanning = (id: string) => {
    comparePlanning.mutate(id, {
      onSuccess: (comparison) =>
        setComparisonByDataset((prev) => ({ ...prev, [id]: comparison })),
    });
  };

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        {t("evalStudio.nlFacet.intro", {
          defaultValue:
            "Semantic Navigation NL→facet packs, folded onto the shared eval-dataset model. Import a pack once, then run it through the grounded parser scorer — one Studio, one gate.",
        })}
      </p>

      {available === false && (
        <div className="bento-tile bento-glass rounded-2xl border border-amber-500/40 p-4 text-sm text-amber-700 dark:text-amber-300">
          {t("evalStudio.nlFacet.noLlm", {
            defaultValue:
              "No usable default LLM is configured — the NL→facet parser needs one to run.",
          })}
        </div>
      )}

      {/* Import a pack as a reusable dataset */}
      <div className="bento-tile bento-glass rounded-2xl border border-border/60 p-5">
        <div className="mb-2 flex items-center gap-2">
          <IconUpload size={18} className="text-muted-foreground" />
          <span className="font-medium">
            {t("evalStudio.nlFacet.importTitle", {
              defaultValue: "Import a pack as a dataset",
            })}
          </span>
        </div>
        <textarea
          value={packJson}
          onChange={(e) => setPackJson(e.target.value)}
          rows={6}
          spellCheck={false}
          placeholder='{ "name": "catalog", "index": "products", "cases": [ { "name": "cheap online", "query": "online under 20k", "expect": { "filters": [], "ranges": [] } } ] }'
          className="w-full rounded-xl border border-border/60 bg-card/60 p-3 font-mono text-xs backdrop-blur focus:outline-none focus:ring-2 focus:ring-primary/40"
        />
        {importError && (
          <p className="mt-2 text-sm text-rose-600 dark:text-rose-400">{importError}</p>
        )}
        <button
          type="button"
          onClick={onImport}
          disabled={!packJson.trim() || importDataset.isPending}
          className="bento-tile bento-tile-clickable mt-3 inline-flex items-center gap-1.5 rounded-full border border-primary/40 bg-primary px-4 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
        >
          <IconUpload size={16} />
          {importDataset.isPending
            ? t("common.loading", { defaultValue: "Loading…" })
            : t("evalStudio.nlFacet.importButton", { defaultValue: "Import dataset" })}
        </button>
      </div>

      {/* Saved NL→facet datasets */}
      {isError ? (
        <p className="text-sm text-rose-600 dark:text-rose-400">
          {t("common.connectionError", { resource: "NL→facet datasets" })}
        </p>
      ) : (datasets ?? []).length === 0 ? (
        <div className="bento-tile bento-glass rounded-2xl border border-border/60 p-8 text-center">
          <IconFilterSearch size={28} className="mx-auto mb-3 text-muted-foreground" />
          <p className="font-medium">
            {t("evalStudio.nlFacet.empty", { defaultValue: "No NL→facet datasets yet" })}
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            {t("evalStudio.nlFacet.emptyHint", {
              defaultValue: "Import a pack above to save it as a reusable dataset.",
            })}
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {(datasets ?? []).map((dataset) => (
            <NLFacetDatasetCard
              key={dataset.id}
              dataset={dataset}
              report={reportByDataset[dataset.id]}
              comparison={comparisonByDataset[dataset.id]}
              running={runDataset.isPending && runDataset.variables === dataset.id}
              comparing={
                comparePlanning.isPending && comparePlanning.variables === dataset.id
              }
              onRun={() => onRun(dataset.id)}
              onComparePlanning={() => onComparePlanning(dataset.id)}
              onDelete={() => deleteDataset.mutate(dataset.id)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function NLFacetDatasetCard({
  dataset,
  report,
  comparison,
  running,
  comparing,
  onRun,
  onComparePlanning,
  onDelete,
}: Readonly<{
  dataset: TurEvalDataset;
  report?: TurNLFacetEvalReport;
  comparison?: TurCopilotPlanningComparison;
  running: boolean;
  comparing: boolean;
  onRun: () => void;
  onComparePlanning: () => void;
  onDelete: () => void;
}>) {
  const { t } = useTranslation();
  return (
    <div className="bento-tile bento-glass rounded-2xl border border-border/60 p-5">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <IconFilterSearch size={18} className="text-muted-foreground" />
          <span className="font-medium">{dataset.name}</span>
          <span className="text-xs text-muted-foreground">
            {t("evalStudio.datasetMeta", {
              defaultValue: "{{rows}} rows · v{{version}}",
              rows: dataset.rowCount,
              version: dataset.version,
            })}
          </span>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={onRun}
            disabled={running}
            className="bento-tile bento-tile-clickable inline-flex items-center gap-1.5 rounded-full border border-border/60 bg-card/60 px-3 py-1 text-sm backdrop-blur disabled:opacity-50"
          >
            <IconPlayerPlay size={15} />
            {running
              ? t("evalStudio.nlFacet.running", { defaultValue: "Running…" })
              : t("evalStudio.nlFacet.run", { defaultValue: "Run" })}
          </button>
          <button
            type="button"
            onClick={onComparePlanning}
            disabled={comparing}
            title={t("evalStudio.nlFacet.compareHint", {
              defaultValue:
                "Runs the pack through every copilot planning strategy — expensive: an LLM-assisted row costs up to three LLM calls per case.",
            })}
            className="bento-tile bento-tile-clickable inline-flex items-center gap-1.5 rounded-full border border-border/60 bg-card/60 px-3 py-1 text-sm backdrop-blur disabled:opacity-50"
          >
            <IconScale size={15} />
            {comparing
              ? t("evalStudio.nlFacet.comparing", { defaultValue: "Comparing…" })
              : t("evalStudio.nlFacet.compare", { defaultValue: "Compare planners" })}
          </button>
          <button
            type="button"
            onClick={onDelete}
            aria-label={t("common.delete", { defaultValue: "Delete" })}
            className="bento-tile bento-tile-clickable inline-flex items-center rounded-full border border-border/60 bg-card/60 p-1.5 text-muted-foreground backdrop-blur hover:text-rose-600"
          >
            <IconTrash size={15} />
          </button>
        </div>
      </div>

      {report && (
        <div className="mt-4 space-y-2">
          <div className="flex items-center gap-2 text-sm">
            {report.passed ? (
              <IconCircleCheck size={18} className="text-emerald-600" />
            ) : (
              <IconCircleX size={18} className="text-rose-600" />
            )}
            <span className="font-medium">
              {t("evalStudio.nlFacet.reportSummary", {
                defaultValue: "{{passed}}/{{total}} passed · score {{score}}%",
                passed: report.passedCount,
                total: report.caseCount,
                score: Math.round(report.score * 100),
              })}
            </span>
          </div>
          {report.error && (
            <p className="text-sm text-rose-600 dark:text-rose-400">{report.error}</p>
          )}
          <ul className="space-y-1">
            {report.results.map((c) => (
              <li
                key={c.caseName}
                className="flex items-start gap-2 rounded-lg border border-border/40 bg-card/40 px-3 py-1.5 text-sm"
              >
                {c.passed ? (
                  <IconCircleCheck size={15} className="mt-0.5 shrink-0 text-emerald-600" />
                ) : (
                  <IconCircleX size={15} className="mt-0.5 shrink-0 text-rose-600" />
                )}
                <span className="flex-1">
                  <span className="font-medium">{c.caseName}</span>
                  {c.findings.length > 0 && (
                    <span className="ml-2 text-xs text-muted-foreground">
                      {c.findings.join("; ")}
                    </span>
                  )}
                  {c.ungroundedFields.length > 0 && (
                    <span className="ml-2 text-xs text-rose-600 dark:text-rose-400">
                      {t("evalStudio.nlFacet.ungrounded", {
                        defaultValue: "ungrounded: {{fields}}",
                        fields: c.ungroundedFields.join(", "),
                      })}
                    </span>
                  )}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {comparison && <PlanningComparison comparison={comparison} />}
    </div>
  );
}

/**
 * T821 / §LIX.4 — the copilot planning strategies side by side on the three axes
 * an operator trades off: answer quality, latency and LLM calls.
 *
 * The caveats are rendered as prominently as the table on purpose. The run is
 * plan-only, so HYBRID's row is its deterministic fast-path — showing the numbers
 * without that sentence would read as "HYBRID buys you nothing", which is exactly
 * the wrong conclusion.
 */
function PlanningComparison({
  comparison,
}: Readonly<{ comparison: TurCopilotPlanningComparison }>) {
  const { t } = useTranslation();

  if (comparison.error) {
    return (
      <p className="mt-4 text-sm text-rose-600 dark:text-rose-400">
        {comparison.error}
      </p>
    );
  }

  return (
    <div className="mt-4 space-y-3">
      <div className="flex items-center gap-2 text-sm font-medium">
        <IconScale size={16} className="text-muted-foreground" />
        {t("evalStudio.nlFacet.comparisonTitle", {
          defaultValue: "Planning strategies compared",
        })}
        <span className="text-xs font-normal text-muted-foreground">
          {t("evalStudio.nlFacet.comparisonMeta", {
            defaultValue: "{{cases}} cases · depth {{depth}}",
            cases: comparison.caseCount,
            depth: comparison.maxPasses,
          })}
        </span>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[32rem] text-sm">
          <thead>
            <tr className="text-left text-xs uppercase tracking-wide text-muted-foreground">
              <th className="py-1 pr-3 font-medium">
                {t("evalStudio.nlFacet.colStrategy", { defaultValue: "Strategy" })}
              </th>
              <th className="py-1 pr-3 font-medium">
                {t("evalStudio.nlFacet.colScore", { defaultValue: "Score" })}
              </th>
              <th className="py-1 pr-3 font-medium">
                {t("evalStudio.nlFacet.colPassed", { defaultValue: "Passed" })}
              </th>
              <th className="py-1 pr-3 font-medium">
                {t("evalStudio.nlFacet.colLlmPasses", { defaultValue: "LLM calls" })}
              </th>
              <th className="py-1 font-medium">
                {t("evalStudio.nlFacet.colElapsed", { defaultValue: "Elapsed" })}
              </th>
            </tr>
          </thead>
          <tbody>
            {comparison.strategies.map((outcome) => (
              <tr
                key={outcome.strategy}
                className="border-t border-border/40 align-top"
              >
                <td className="py-2 pr-3">
                  <span className="font-medium">{outcome.strategy}</span>
                  <span className="block text-xs text-muted-foreground">
                    {outcome.note}
                  </span>
                </td>
                <td className="py-2 pr-3 tabular-nums">
                  {Math.round(outcome.score * 100)}%
                </td>
                <td className="py-2 pr-3 tabular-nums">
                  {outcome.passedCount}/{comparison.caseCount}
                </td>
                <td className="py-2 pr-3 tabular-nums">{outcome.llmPasses}</td>
                <td className="py-2 tabular-nums">{outcome.elapsedMillis} ms</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {comparison.caveats.length > 0 && (
        <ul className="space-y-1 rounded-xl border border-amber-500/40 bg-amber-500/5 p-3">
          {comparison.caveats.map((caveat) => (
            <li
              key={caveat}
              className="flex items-start gap-2 text-xs text-amber-700 dark:text-amber-300"
            >
              <IconAlertTriangle size={14} className="mt-0.5 shrink-0" />
              <span>{caveat}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
