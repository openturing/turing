import { useLlmInstances } from "@/api/queries/llm-instance.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { IconCpu2 } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function BentoLLMInstanceListPage() {
  const { t } = useTranslation();
  const { data: llms, isError } = useLlmInstances();
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
