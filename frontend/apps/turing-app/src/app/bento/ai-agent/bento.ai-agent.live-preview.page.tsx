import { useAiAgent } from "@/api/queries/ai-agent.queries";
import AIAgentLivePreviewContent from "@/app/console/ai-agent/live-preview/ai-agent.live-preview.page";
import { IconEye } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { BentoAgentSubHero } from "./bento.ai-agent.sub-hero";

/**
 * Bento wrapper for the agent's "Live Preview" analysis page — the assembled
 * system message for a previewed turn, broken down by origin. The preview
 * content component is self-contained (no console sidebar chrome), so it is
 * reused verbatim below the shared bento hero.
 */
export default function BentoAIAgentLivePreviewPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const { data: agent } = useAiAgent(id);

  return (
    <>
      <BentoAgentSubHero
        agentId={id}
        agentTitle={agent?.title ?? t("aiAgent.title")}
        icon={IconEye}
        tone="indigo"
        title={t("aiAgent.nav.livePreview")}
        subtitle={t("aiAgent.systemPrompt.preview.description", {
          defaultValue:
            "Everything below is concatenated into one system message before it reaches the model. Each block is tagged with where it comes from.",
        })}
      />
      <AIAgentLivePreviewContent chrome="bento" />
    </>
  );
}
