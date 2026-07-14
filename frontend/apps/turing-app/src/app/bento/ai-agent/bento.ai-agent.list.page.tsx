import { useAiAgents } from "@/api/queries/ai-agent.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { IconRobot } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function BentoAIAgentListPage() {
  const { t } = useTranslation();
  const { data: agents, isError } = useAiAgents();
  const error = isError ? t("common.connectionError", { resource: "AI agents" }) : null;

  return (
    <BentoListPage
      items={agents}
      error={error}
      tryAgainUrl={ROUTES.BENTO_AI_AGENT_INSTANCE}
      backTo={ROUTES.BENTO_AREA_GENERATIVE_AI}
      backLabel={t("home.sections.generativeAi.label")}
      heroIcon={IconRobot}
      tone="blue"
      title={t("aiAgent.title")}
      subtitle={t("aiAgent.blankDescription")}
      newRoute={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/new`}
      newLabel={t("aiAgent.newInstance")}
      newSubtitle={t("aiAgent.newSubtitle")}
      itemKey={(agent) => agent.id}
      emptyTitle={t("aiAgent.blankTitle")}
      emptyDescription={t("aiAgent.blankDescription")}
      listId="aiAgent"
      renderTile={(agent, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/${agent.id}`}
          emphasis={emphasis}
          defaultIcon={IconRobot}
          icon={agent.icon}
          tone="blue"
          title={agent.title}
          description={agent.description}
          hasStatus
          enabled={agent.enabled}
          viewTransitionName={`bento-agent-${agent.id}`}
        />
      )}
    />
  );
}
