import { useAiAgent } from "@/api/queries/ai-agent.queries";
import AIAgentSlotContent from "@/app/console/ai-agent/slot/ai-agent.slot.page";
import { IconLayoutList } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoAgentSubHero } from "./bento.ai-agent.sub-hero";

/**
 * Bento wrapper for the agent's typed-slot catalog. The console slot page owns
 * an inline (sidebar-coupled) SubPageHeader, so it accepts a `header` prop —
 * bento passes its own hero and the console chrome is dropped.
 */
export default function BentoAIAgentSlotPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const { data: agent } = useAiAgent(id);

  return (
    <AIAgentSlotContent
      header={
        <BentoAgentSubHero
          agentId={id}
          agentTitle={agent?.title ?? t("aiAgent.title")}
          icon={IconLayoutList}
          tone="blue"
          title={t("aiAgent.slot.title")}
          subtitle={t("aiAgent.slot.description")}
        />
      }
    />
  );
}
