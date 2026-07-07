import { useIntents } from "@/api/queries/intent.queries";
import { ROUTES } from "@/app/routes.const";
import { useAiAgent } from "@/api/queries/ai-agent.queries";
import { AiAuthoringTrigger } from "@/components/ai-authoring/ai-authoring-trigger";
import { BentoListPage, BentoEntityTile } from "@/components/bento";
import { IconBulb } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * Bento intents list for an AI agent — the frosted mosaic (BentoListPage)
 * instead of the console GridList. Tiles + the "new" tile + the AI-authoring
 * trigger all resolve inside /bento.
 */
export default function BentoAIAgentIntentListPage() {
  const { t } = useTranslation();
  const { id: agentId } = useParams() as { id: string };
  const { data: intents, isError } = useIntents(agentId);
  const { data: agent } = useAiAgent(agentId);
  const error = isError ? t("common.connectionError", { resource: "intents" }) : null;
  const base = `${ROUTES.BENTO_AI_AGENT_INSTANCE}/${agentId}/intent`;

  return (
    <>
      <BentoListPage
        items={intents}
        error={error}
        tryAgainUrl={base}
        eyebrow={agent?.title ?? t("aiAgent.title")}
        heroIcon={IconBulb}
        tone="amber"
        title={t("aiAgent.nav.intents")}
        subtitle={t("intent.description", {
          defaultValue: "Predefined intents the agent can recognise and route.",
        })}
        newRoute={`${base}/new`}
        newLabel={t("intent.newInstance")}
        itemKey={(x) => x.id ?? ""}
        emptyTitle={t("intent.blankTitle")}
        emptyDescription={t("intent.blankDescription")}
        renderTile={(x, emphasis) => (
          <BentoEntityTile
            to={`${base}/${x.id}`}
            emphasis={emphasis}
            defaultIcon={IconBulb}
            icon={x.icon}
            tone="amber"
            title={x.title}
            description={x.description}
            hasStatus
            enabled={x.enabled}
          />
        )}
      />
      <AiAuthoringTrigger to={`${base}/new/ai-chat`} />
    </>
  );
}
