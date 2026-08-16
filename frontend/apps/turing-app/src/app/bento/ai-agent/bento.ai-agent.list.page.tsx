import { useAiAgents } from "@/api/queries/ai-agent.queries";
import { useAiActivation } from "@/api/queries/viglet-ai.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage, BentoNoLlmState } from "@/components/bento";
import { IconRobot } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function BentoAIAgentListPage() {
  const { t } = useTranslation();
  const { data: agents, isError } = useAiAgents();
  const { hasEnabledLlm, isResolved } = useAiActivation();
  const error = isError ? t("common.connectionError", { resource: "AI agents" }) : null;

  return (
    <>
    {/*
     * T958 — an agent with no model behind it answers nothing, and the failure
     * lands much later, inside a chat turn. Say it here instead. Kept ABOVE the
     * list rather than replacing it: agents are still worth authoring before a
     * model exists, so the blocker is stated without taking the surface away.
     */}
    {isResolved && !hasEnabledLlm && (
      <BentoNoLlmState description={t("bento.activate.noLlmAgents", {
        defaultValue: "Agents can be authored now, but none of them can answer until a model is configured.",
      })} />
    )}
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
    </>
  );
}
