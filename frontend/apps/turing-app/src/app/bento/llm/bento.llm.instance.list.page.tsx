import {
  useLlmCatalogChanges,
  useLlmDeprecations,
  useLlmInstances,
} from "@/api/queries/llm-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import type {
  TurLlmCatalogChangeNotification,
  TurLlmDeprecatedModelUsage,
} from "@/models/llm/llm-instance.model.ts";
import { IconAlertTriangle, IconCpu2, IconInfoCircle, IconWand } from "@tabler/icons-react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";

/** T782 — amber banner listing instances configured with a deprecated model. */
function DeprecationBanner({ usages }: Readonly<{ usages: TurLlmDeprecatedModelUsage[] }>) {
  const { t } = useTranslation();
  if (usages.length === 0) return null;
  return (
    <div className="flex w-full gap-3 rounded-lg border border-amber-500/40 bg-amber-500/5 p-4 text-amber-700 dark:text-amber-400">
      <IconAlertTriangle className="mt-0.5 size-5 shrink-0" />
      <div className="min-w-0 space-y-1">
        <p className="font-medium">{t("llm.deprecation.title")}</p>
        <p className="text-muted-foreground text-sm">{t("llm.deprecation.description")}</p>
        <ul className="space-y-1 pt-1 text-sm">
          {usages.map((u) => (
            <li key={u.instanceId} className="flex flex-wrap items-center gap-x-2">
              <Link to={`${ROUTES.BENTO_LLM_INSTANCE}/${u.instanceId}`} className="font-medium underline">
                {t("llm.deprecation.item", { instance: u.instanceTitle, model: u.modelName })}
              </Link>
              <span className="rounded-full border border-amber-500/40 px-1.5 py-0.5 text-[11px]">
                {u.deprecated ? t("llm.deprecation.deprecated") : t("llm.deprecation.retired")}
              </span>
              <span className="text-muted-foreground">
                {u.replacementLabel
                  ? t("llm.deprecation.suggested", { model: u.replacementLabel })
                  : t("llm.deprecation.noReplacement")}
              </span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

/** T788 — info banner listing catalog change-feed notifications for in-use models. */
function ChangeFeedBanner({ changes }: Readonly<{ changes: TurLlmCatalogChangeNotification[] }>) {
  const { t } = useTranslation();
  if (changes.length === 0) return null;
  return (
    <div className="flex w-full gap-3 rounded-lg border border-blue-500/40 bg-blue-500/5 p-4 text-blue-700 dark:text-blue-400">
      <IconInfoCircle className="mt-0.5 size-5 shrink-0" />
      <div className="min-w-0 space-y-1">
        <p className="font-medium">{t("llm.changeFeed.title")}</p>
        <p className="text-muted-foreground text-sm">{t("llm.changeFeed.description")}</p>
        <ul className="space-y-1 pt-1 text-sm">
          {changes.map((c) => (
            <li key={`${c.vendorId}:${c.modelId}`} className="flex flex-wrap items-center gap-x-2">
              <span className="font-mono">{c.vendorId}/{c.modelId}</span>
              <span className="rounded-full border border-blue-500/40 px-1.5 py-0.5 text-[11px]">
                {t(`llm.changeFeed.type.${c.type}`)}
              </span>
              {c.replacementCandidates.length > 0 && (
                <span className="text-muted-foreground">
                  {t("llm.changeFeed.replacedBy", { models: c.replacementCandidates.join(", ") })}
                </span>
              )}
              {c.detail && <span className="text-muted-foreground">{c.detail}</span>}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

export default function BentoLLMInstanceListPage() {
  const { t } = useTranslation();
  const { data: llms, isError } = useLlmInstances();
  const { data: deprecations } = useLlmDeprecations();
  const { data: catalogChanges } = useLlmCatalogChanges();
  const error = isError ? t("common.connectionError", { resource: t("llm.title") }) : null;

  return (
    <BentoListPage
      items={llms}
      error={error}
      tryAgainUrl={ROUTES.BENTO_LLM_INSTANCE}
      backTo={ROUTES.BENTO_AREA_GENERATIVE_AI}
      backLabel={t("home.sections.generativeAi.label")}
      heroIcon={IconCpu2}
      tone="indigo"
      title={t("llm.title")}
      subtitle={t("llm.blankDescription")}
      headerAction={
        <div className="flex w-full flex-col gap-3">
          <Link
            to={ROUTES.BENTO_LLM_ADVISOR}
            className="bento-tile bento-tile-clickable inline-flex w-fit items-center gap-1.5 rounded-full border border-border/60 bg-card/60 px-3.5 py-1.5 text-sm backdrop-blur transition-colors hover:text-foreground"
          >
            <IconWand size={16} />
            {t("modelAdvisor.launch")}
          </Link>
          <DeprecationBanner usages={deprecations ?? []} />
          <ChangeFeedBanner changes={catalogChanges ?? []} />
        </div>
      }
      newRoute={`${ROUTES.BENTO_LLM_INSTANCE}/new`}
      newLabel={t("llm.newLanguageModel")}
      newSubtitle="OpenAI · Ollama · Anthropic · Gemini"
      itemKey={(llm) => llm.id}
      emptyTitle={t("llm.blankTitle")}
      emptyDescription={t("llm.blankDescription")}
      listId="languageModel"
      renderTile={(llm, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_LLM_INSTANCE}/${llm.id}`}
          emphasis={emphasis}
          defaultIcon={IconCpu2}
          icon={llm.icon}
          tone="indigo"
          title={llm.title}
          description={llm.description}
          hasStatus
          enabled={llm.enabled}
          meta={
            <>
              <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                {llm.turLLMVendor?.title ?? "—"}
              </span>
              {llm.modelName && <span className="truncate">{llm.modelName}</span>}
            </>
          }
        />
      )}
    />
  );
}
