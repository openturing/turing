import { useLlmInstances } from "@/api/queries/llm-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import type { TurLLMInstance } from "@/models/llm/llm-instance.model.ts";
import { Icon } from "@iconify/react";
import { IconCpu2, IconPlus, IconSparkles } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

export default function BentoLLMInstanceListPage() {
  const { t } = useTranslation();
  const { data: llms, isError } = useLlmInstances();
  const error = isError ? t("common.connectionError", { resource: t("llm.title") }) : null;

  return (
    <LoadProvider checkIsNotUndefined={llms} error={error} tryAgainUrl={ROUTES.BENTO_LLM_INSTANCE}>
      <BentoHero
        eyebrow={t("home.sections.generativeAi.label")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-indigo-600 to-fuchsia-600 text-white shadow-md">
            <IconCpu2 size={24} />
          </span>
        }
        title={t("llm.title")}
        subtitle={t("llm.blankDescription")}
      />

      <div className="bento-grid grid auto-rows-[minmax(140px,auto)] grid-cols-2 gap-4 md:grid-cols-4 md:gap-5 lg:grid-cols-6">
        <NewLlmTile />
        {(llms ?? []).map((llm, idx) => (
          <LlmTile key={llm.id} llm={llm} featured={idx === 0} />
        ))}
        {llms?.length === 0 && <EmptyHintTile />}
      </div>
    </LoadProvider>
  );
}

function NewLlmTile() {
  const { t } = useTranslation();
  return (
    /*
     * Visually distinct from content tiles: half the size (2x1 vs the
     * featured's 2x2), no tonal ornament, and a dashed border. The
     * dashed/empty look reads as "draft / awaiting input" — a universal
     * cue for "create" affordances (App Library, Wallet, Linear).
     */
    <Link
      to={`${ROUTES.BENTO_LLM_INSTANCE}/new`}
      className="bento-tile bento-tile-clickable col-span-2 row-span-1 flex flex-col gap-3 rounded-3xl border-2 border-dashed border-border/60 bg-card/30 p-5 backdrop-blur-md hover:border-indigo-500/60 hover:bg-card/50 md:col-span-2 md:row-span-1 lg:col-span-2 lg:row-span-1"
    >
      <div className="flex items-center gap-3">
        <span className="bento-pulse grid h-10 w-10 place-items-center rounded-2xl bg-linear-to-br from-indigo-600 to-fuchsia-600 text-white shadow-md">
          <IconPlus size={20} />
        </span>
        <span className="text-[11px] uppercase tracking-wider text-muted-foreground">New</span>
      </div>
      <div>
        <div className="text-base font-semibold tracking-tight md:text-lg">
          <span className="bg-linear-to-br from-indigo-600 to-fuchsia-600 bg-clip-text text-transparent">
            {t("llm.newLanguageModel")}
          </span>
        </div>
        <p className="mt-1 text-sm text-muted-foreground">
          OpenAI · Ollama · Anthropic · Gemini
        </p>
      </div>
    </Link>
  );
}

function LlmTile({ llm, featured }: Readonly<{ llm: TurLLMInstance; featured: boolean }>) {
  const span = featured
    ? "col-span-2 row-span-2 md:col-span-2 md:row-span-2 lg:col-span-2 lg:row-span-2"
    : "col-span-2 row-span-1 md:col-span-2 md:row-span-1 lg:col-span-2 lg:row-span-1";
  const enabled = llm.enabled === 1;
  const vendorTitle = llm.turLLMVendor?.title ?? "—";
  const padding = featured ? "p-6 md:p-7" : "p-5";
  const iconChip = featured ? "h-14 w-14" : "h-9 w-9";
  const iconSize = featured ? 28 : 18;
  const titleSize = featured ? "text-xl md:text-2xl" : "text-base md:text-lg";

  return (
    <Link
      to={`${ROUTES.BENTO_LLM_INSTANCE}/${llm.id}`}
      className={`bento-tile bento-tile-clickable bento-glass group relative flex flex-col ${featured ? "gap-5" : "gap-4"} overflow-hidden ${padding} ${span}`}
    >
      {featured && (
        <>
          <div aria-hidden className="pointer-events-none absolute -bottom-10 -right-10 h-64 w-64 rounded-full bg-linear-to-br from-indigo-600 to-fuchsia-600 opacity-40 blur-2xl dark:opacity-50" />
          <div aria-hidden className="pointer-events-none absolute right-12 top-1/3 h-32 w-32 rounded-full bg-linear-to-tr from-indigo-600 to-fuchsia-600 opacity-20 blur-2xl dark:opacity-30" />
        </>
      )}

      <div className="relative z-1 flex items-center justify-between">
        <span className={`grid place-items-center rounded-2xl bg-linear-to-br from-indigo-600 to-fuchsia-600 text-white shadow-md ${iconChip}`}>
          {/* Render the user-selected Iconify icon when set, otherwise the default. */}
          {llm.icon
            ? <Icon icon={llm.icon} className={featured ? "size-7 text-white" : "size-4.5 text-white"} />
            : <IconCpu2 size={iconSize} />}
        </span>
        <span className={`flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[10px] uppercase tracking-wider ${
          enabled
            ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
            : "border-border bg-muted text-muted-foreground"
        }`}>
          <span className={`h-1.5 w-1.5 rounded-full ${enabled ? "bg-emerald-500 bento-pulse" : "bg-muted-foreground/60"}`} />
          {enabled ? "Active" : "Idle"}
        </span>
      </div>
      <div className="relative z-1">
        <div className={`font-semibold tracking-tight ${titleSize}`}>
          {llm.title}
        </div>
        <div className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
          <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
            {vendorTitle}
          </span>
          {llm.modelName && (
            <span className="truncate">{llm.modelName}</span>
          )}
        </div>
        {llm.description && (
          <p className="mt-2 text-sm text-muted-foreground">{llm.description}</p>
        )}
      </div>
    </Link>
  );
}

function EmptyHintTile() {
  const { t } = useTranslation();
  return (
    <div className="bento-tile bento-glass col-span-2 row-span-2 flex flex-col items-start justify-end p-5 md:col-span-4">
      <IconSparkles size={20} className="mb-3 text-indigo-500" />
      <div className="text-lg font-semibold tracking-tight">{t("llm.blankTitle")}</div>
      <p className="mt-1 max-w-md text-sm text-muted-foreground">{t("llm.blankDescription")}</p>
    </div>
  );
}
