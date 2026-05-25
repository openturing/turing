import {
  useImportChatFlow,
  useImportChatFlowBundle,
  useChatFlows,
} from "@/api/queries/chat-flow.queries";
import { ROUTES } from "@/app/routes.const";
import { AiAuthoringTrigger } from "@/components/ai-authoring/ai-authoring-trigger";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { DropdownMenuItem } from "@/components/ui/dropdown-menu";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { IconSitemap, IconUpload } from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useCallback, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

import { buildImportPayloadFromExport } from "./chat-flow.import";
import { ChatFlowTriggerConflictsPanel } from "./components/chat-flow.trigger-conflicts-panel";

/**
 * Lists chat flows that belong to the AI agent currently in the URL.
 *
 * @since 2026.2.5
 */
export default function ChatFlowListPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { id: agentId } = useParams() as { id: string };
  const { data: flows, isError } = useChatFlows(agentId);
  const error = isError ? t("common.connectionError", { resource: "chat flows" }) : null;
  const baseUrl = `${ROUTES.AI_AGENT_INSTANCE}/${agentId}/chat-flow`;
  const importMutation = useImportChatFlow();
  const importBundleMutation = useImportChatFlowBundle();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const gridItemList = useGridAdapter(flows, {
    name: "name",
    description: "description",
    url: (item) => `${baseUrl}/${item.id}`,
  });

  const onImportClick = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  // Shared between the GridList dropdown ("Import from JSON") and the BlankSlate secondary CTA.
  // Two file shapes are supported:
  //   • a single flow object — sent to /import, redirects to the persisted flow's editor.
  //   • an array of flow objects (bundle) — sent to /import-bundle; the backend auto-wires sub-
  //     flow references between items. We redirect to the first persisted flow.
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
          const main = created[0];
          navigate(`${baseUrl}/${main.id}`);
        } else {
          const payload = buildImportPayloadFromExport(parsed as Parameters<typeof buildImportPayloadFromExport>[0]);
          const created = await importMutation.mutateAsync({ agentId, payload });
          toast.success(t("chatFlow.imported", { name: created.name ?? file.name }));
          navigate(`${baseUrl}/${created.id}`);
        }
      } catch (err) {
        console.error("Chat flow import failed", err);
        toast.error(t("chatFlow.importFailed"));
      }
    },
    [agentId, baseUrl, importBundleMutation, importMutation, navigate, t],
  );

  const hiddenFileInput = (
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
  );

  return (
    <LoadProvider checkIsNotUndefined={flows} error={error} tryAgainUrl={baseUrl}>
      {/* T91 — agent-wide conflict overview at the top of the list so authors
          spot heavy trigger-description overlap before opening any flow. */}
      <div className="px-4 lg:px-6">
        <ChatFlowTriggerConflictsPanel agentId={agentId} />
      </div>
      {gridItemList.length > 0 ? (
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${baseUrl}/new`} label={t("chatFlow.title")} />
          <GridList.Action>
            <DropdownMenuItem onClick={onImportClick}>
              <IconUpload className="size-4 mr-2" />
              {t("chatFlow.actions.importJson")}
            </DropdownMenuItem>
          </GridList.Action>
        </GridList>
      ) : (
        <div className="flex flex-col items-center gap-3">
          <BlankSlate
            icon={IconSitemap}
            title={t("chatFlow.blankTitle")}
            description={t("chatFlow.blankDescription")}
            buttonText={t("chatFlow.newInstance")}
            urlNew={`${baseUrl}/new`}
          />
          <button
            type="button"
            onClick={onImportClick}
            className="inline-flex items-center gap-2 rounded-md border border-border/60 bg-background px-3 py-2 text-sm font-medium text-muted-foreground transition hover:border-blue-500/40 hover:text-blue-600 dark:hover:text-blue-400"
          >
            <IconUpload className="size-4" />
            {t("chatFlow.actions.importJson")}
          </button>
        </div>
      )}
      {hiddenFileInput}
      <AiAuthoringTrigger to={`${baseUrl}/new/ai-chat`} />
    </LoadProvider>
  );
}
