import { useCustomTools } from "@/api/queries/custom-tool.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { IconBraces } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function BentoCustomToolListPage() {
  const { t } = useTranslation();
  const { data: tools, isError } = useCustomTools();
  const error = isError ? t("common.connectionError", { resource: t("customTool.title") }) : null;

  return (
    <BentoListPage
      items={tools}
      error={error}
      tryAgainUrl={ROUTES.BENTO_CUSTOM_TOOL_INSTANCE}
      backTo={ROUTES.BENTO_AREA_GENERATIVE_AI}
      backLabel={t("home.sections.generativeAi.label")}
      heroIcon={IconBraces}
      tone="slate"
      title={t("customTool.title")}
      subtitle={t("customTool.blankDescription")}
      newRoute={`${ROUTES.BENTO_CUSTOM_TOOL_INSTANCE}/new`}
      newLabel={t("customTool.newCustomTool")}
      itemKey={(tool) => tool.id}
      emptyTitle={t("customTool.blankTitle")}
      emptyDescription={t("customTool.blankDescription")}
      listId="customTool"
      renderTile={(tool, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_CUSTOM_TOOL_INSTANCE}/${tool.id}`}
          emphasis={emphasis}
          defaultIcon={IconBraces}
          icon={tool.icon}
          tone="slate"
          title={tool.title}
          description={tool.description}
          hasStatus
          enabled={tool.enabled}
          meta={tool.returnType && (
            <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 font-mono backdrop-blur">
              {tool.returnType}
            </span>
          )}
        />
      )}
    />
  );
}
