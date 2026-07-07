import { useAiAgent } from "@/api/queries/ai-agent.queries";
import {
  useChatFlows,
  useImportChatFlow,
  useImportChatFlowBundle,
} from "@/api/queries/chat-flow.queries";
import { ROUTES } from "@/app/routes.const";
import { buildImportPayloadFromExport } from "@/app/console/chat-flow/chat-flow.import";
import { AiAuthoringTrigger } from "@/components/ai-authoring/ai-authoring-trigger";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { GradientButton } from "@/components/ui/gradient-button";
import { IconSitemap, IconUpload } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useCallback, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

/**
 * Bento chat-flows list for an AI agent — the frosted mosaic (BentoListPage)
 * with a floating "Import JSON" affordance + the AI-authoring trigger. Items,
 * the "new" tile, import redirects and AI authoring all resolve inside /bento.
 */
export default function BentoAIAgentChatFlowListPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { id: agentId } = useParams() as { id: string };
  const { data: flows, isError } = useChatFlows(agentId);
  const { data: agent } = useAiAgent(agentId);
  const error = isError ? t("common.connectionError", { resource: "chat flows" }) : null;
  const base = `${ROUTES.BENTO_AI_AGENT_INSTANCE}/${agentId}/chat-flow`;

  const importMutation = useImportChatFlow();
  const importBundleMutation = useImportChatFlowBundle();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const onImportClick = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  const onImportFile = useCallback(
    async (file: File) => {
      try {
        const text = await file.text();
        const parsed = JSON.parse(text) as unknown;
        if (Array.isArray(parsed)) {
          const bundle = parsed.map(buildImportPayloadFromExport);
          const created = await importBundleMutation.mutateAsync({ agentId, bundle });
          if (created.length === 0) {
            throw new Error("Empty bundle");
          }
          toast.success(
            t("chatFlow.bundleImported", {
              count: created.length,
              defaultValue: `Imported ${created.length} chat flow(s).`,
            }),
          );
          navigate(`${base}/${created[0].id}`);
        } else {
          const payload = buildImportPayloadFromExport(parsed as Parameters<typeof buildImportPayloadFromExport>[0]);
          const created = await importMutation.mutateAsync({ agentId, payload });
          toast.success(t("chatFlow.imported", { name: created.name ?? file.name }));
          navigate(`${base}/${created.id}`);
        }
      } catch (err) {
        console.error("Chat flow import failed", err);
        toast.error(t("chatFlow.importFailed"));
      }
    },
    [agentId, base, importBundleMutation, importMutation, navigate, t],
  );

  return (
    <>
      <BentoListPage
        items={flows}
        error={error}
        tryAgainUrl={base}
        eyebrow={agent?.title ?? t("aiAgent.title")}
        heroIcon={IconSitemap}
        tone="indigo"
        title={t("aiAgent.nav.chatFlow")}
        subtitle={t("chatFlow.description", {
          defaultValue: "Visual flows that orchestrate multi-step conversations.",
        })}
        newRoute={`${base}/new`}
        newLabel={t("chatFlow.newInstance")}
        itemKey={(x) => x.id ?? ""}
        emptyTitle={t("chatFlow.blankTitle")}
        emptyDescription={t("chatFlow.blankDescription")}
        renderTile={(x, emphasis) => (
          <BentoEntityTile
            to={`${base}/${x.id}`}
            emphasis={emphasis}
            defaultIcon={IconSitemap}
            tone="indigo"
            title={x.name}
            description={x.description}
            hasStatus
            enabled={x.enabled}
          />
        )}
      />

      <input
        ref={fileInputRef}
        type="file"
        accept="application/json,.json"
        aria-label={t("chatFlow.actions.importJson")}
        title={t("chatFlow.actions.importJson")}
        className="hidden"
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) {
            void onImportFile(file);
          }
          event.target.value = "";
        }}
      />
      {/* Floating "Import JSON" affordance, above the AI-authoring trigger. */}
      <div className="fixed bottom-24 right-6 z-30">
        <GradientButton variant="outline" size="sm" onClick={onImportClick} className="shadow-lg backdrop-blur">
          <IconUpload className="size-4" />
          {t("chatFlow.actions.importJson")}
        </GradientButton>
      </div>
      <AiAuthoringTrigger to={`${base}/new/ai-chat`} />
    </>
  );
}
