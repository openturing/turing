import { useState } from "react";
import { ROUTES } from "@/app/routes.const";
import { useTranslation } from "react-i18next";
import {
  IconActivityHeartbeat,
  IconDatabase,
  IconFilterSearch,
  IconFlask2,
  IconHistory,
  IconInbox,
  IconSparkles,
  IconStack2,
  type Icon as TablerIcon,
} from "@tabler/icons-react";

import { useEvalDatasets, useEvalGraderStacks } from "@/api/queries/eval-studio.queries";
import { BentoHero } from "@/components/bento";
import { BentoEvalGeneratePanel } from "./bento.eval.generate-panel";
import { BentoEvalNLFacetPanel } from "./bento.eval.nl-facet-panel";
import { BentoEvalOnlinePanel } from "./bento.eval.online-panel";
import { BentoEvalReviewInbox } from "./bento.eval.review-inbox";
import { BentoEvalRunHistory } from "./bento.eval.run-history";

type EvalTab = "datasets" | "generate" | "stacks" | "nlfacet" | "runs" | "online" | "review";

/**
 * T599 / §XXXIII.14 — Eval Studio. The bento home for the evaluation platform:
 * the reusable, agent-decoupled assets (datasets + grader stacks), the
 * LLM-assisted Generate tab, the per-agent Runs tab (run history + score
 * timeline + per-case drill-down), and the human-review inbox. The flow-editor
 * gate badge links here.
 *
 * @since 2026.3.4
 */
export default function BentoEvalPage() {
  const { t } = useTranslation();
  const [tab, setTab] = useState<EvalTab>("datasets");

  const { data: datasets, isError: datasetsError } = useEvalDatasets();
  const { data: stacks, isError: stacksError } = useEvalGraderStacks();

  const tabs: { id: EvalTab; label: string; icon: TablerIcon }[] = [
    {
      id: "datasets",
      label: t("evalStudio.tabs.datasets", { defaultValue: "Datasets" }),
      icon: IconDatabase,
    },
    {
      id: "generate",
      label: t("evalStudio.tabs.generate", { defaultValue: "Generate" }),
      icon: IconSparkles,
    },
    {
      id: "stacks",
      label: t("evalStudio.tabs.stacks", { defaultValue: "Grader stacks" }),
      icon: IconStack2,
    },
    {
      id: "nlfacet",
      label: t("evalStudio.tabs.nlFacet", { defaultValue: "NL→Facet" }),
      icon: IconFilterSearch,
    },
    {
      id: "runs",
      label: t("evalStudio.tabs.runs", { defaultValue: "Runs" }),
      icon: IconHistory,
    },
    {
      id: "online",
      label: t("evalStudio.tabs.online", { defaultValue: "Online" }),
      icon: IconActivityHeartbeat,
    },
    {
      id: "review",
      label: t("evalStudio.tabs.review", { defaultValue: "Review inbox" }),
      icon: IconInbox,
    },
  ];

  return (
    <div className="mx-auto w-full max-w-7xl px-4 pb-16 md:px-8">
      <BentoHero
        backTo={ROUTES.BENTO_AREA_GENERATIVE_AI}
        backLabel={t("home.sections.generativeAi.label", { defaultValue: "Generative AI" })}
        title={t("evalStudio.title", { defaultValue: "Eval Studio" })}
        subtitle={t("evalStudio.subtitle", {
          defaultValue:
            "Reusable datasets and grader stacks for the evaluation platform — provide a dataset, compose a grader stack, and share them across agents.",
        })}
        leading={
          <div className="flex size-12 items-center justify-center rounded-2xl bg-gradient-to-br from-amber-500 to-rose-500 text-white shadow-lg">
            <IconFlask2 size={24} />
          </div>
        }
      />

      <nav className="mb-6 flex flex-wrap gap-2">
        {tabs.map((entry) => {
          const isActive = tab === entry.id;
          return (
            <button
              key={entry.id}
              type="button"
              onClick={() => setTab(entry.id)}
              className={`bento-tile bento-tile-clickable inline-flex items-center gap-1.5 rounded-full border px-3.5 py-1.5 text-sm backdrop-blur transition-colors ${
                isActive
                  ? "border-primary/40 bg-primary text-primary-foreground"
                  : "border-border/60 bg-card/60 text-muted-foreground hover:text-foreground"
              }`}
            >
              <entry.icon size={16} />
              {entry.label}
            </button>
          );
        })}
      </nav>

      {tab === "datasets" && (
        <AssetGrid
          error={datasetsError}
          items={(datasets ?? []).map((d) => ({
            id: d.id,
            title: d.name,
            description: d.description,
            meta: t("evalStudio.datasetMeta", {
              defaultValue: "{{rows}} rows · v{{version}}",
              rows: d.rowCount,
              version: d.version,
            }),
          }))}
          icon={IconDatabase}
          emptyTitle={t("evalStudio.datasetsEmpty", { defaultValue: "No datasets yet" })}
          emptyDescription={t("evalStudio.datasetsEmptyHint", {
            defaultValue: "Import a dataset (CSV / JSON / JSONL / OpenAI-Evals) via POST /api/eval/dataset/import.",
          })}
          errorLabel={t("common.connectionError", { resource: "datasets" })}
        />
      )}

      {tab === "generate" && <BentoEvalGeneratePanel />}

      {tab === "stacks" && (
        <AssetGrid
          error={stacksError}
          items={(stacks ?? []).map((s) => ({
            id: s.id,
            title: s.name,
            description: s.description,
            meta: t("evalStudio.stackMeta", {
              defaultValue: "{{count}} graders",
              count: s.configs?.length ?? 0,
            }),
          }))}
          icon={IconStack2}
          emptyTitle={t("evalStudio.stacksEmpty", { defaultValue: "No grader stacks yet" })}
          emptyDescription={t("evalStudio.stacksEmptyHint", {
            defaultValue: "Create a reusable grader stack via POST /api/eval/grader-stack and bind it to an eval set.",
          })}
          errorLabel={t("common.connectionError", { resource: "grader stacks" })}
        />
      )}

      {tab === "nlfacet" && <BentoEvalNLFacetPanel />}

      {tab === "runs" && <BentoEvalRunHistory />}

      {tab === "online" && <BentoEvalOnlinePanel />}

      {tab === "review" && <BentoEvalReviewInbox />}
    </div>
  );
}

interface AssetItem {
  id: string;
  title: string;
  description?: string | null;
  meta: string;
}

function AssetGrid({
  items,
  error,
  icon: Icon,
  emptyTitle,
  emptyDescription,
  errorLabel,
}: Readonly<{
  items: AssetItem[];
  error: boolean;
  icon: TablerIcon;
  emptyTitle: string;
  emptyDescription: string;
  errorLabel: string;
}>) {
  if (error) {
    return <p className="text-sm text-rose-600 dark:text-rose-400">{errorLabel}</p>;
  }
  if (items.length === 0) {
    return (
      <div className="bento-tile bento-glass rounded-2xl border border-border/60 p-8 text-center">
        <Icon size={28} className="mx-auto mb-3 text-muted-foreground" />
        <p className="font-medium">{emptyTitle}</p>
        <p className="mt-1 text-sm text-muted-foreground">{emptyDescription}</p>
      </div>
    );
  }
  return (
    <div className="bento-grid grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {items.map((item) => (
        <div
          key={item.id}
          className="bento-tile bento-glass rounded-2xl border border-border/60 p-5"
        >
          <div className="flex items-center gap-2">
            <Icon size={18} className="text-muted-foreground" />
            <span className="font-medium">{item.title}</span>
          </div>
          {item.description && (
            <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">
              {item.description}
            </p>
          )}
          <p className="mt-3 text-xs text-muted-foreground">{item.meta}</p>
        </div>
      ))}
    </div>
  );
}
