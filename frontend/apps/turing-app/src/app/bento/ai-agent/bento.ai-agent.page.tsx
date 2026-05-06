import { useAiAgent, useDeleteAiAgent, useUpdateAiAgent } from "@/api/queries/ai-agent.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoActionsMenu, BentoCountTile, BentoHero, BentoHeroIconPicker, BentoInlineEdit, BentoTile } from "@/components/bento";
import { DialogDelete } from "@/components/dialog.delete";
import { LoadProvider } from "@/components/loading-provider";
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts";
import { toast } from "@viglet/viglet-design-system";
import {
  IconBraces,
  IconBulb,
  IconCpu2,
  IconHistory,
  IconRobot,
  IconServer2,
  IconSettings,
  IconSitemap,
  IconSparkles,
  IconTool,
  IconTrash,
} from "@tabler/icons-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams } from "react-router-dom";

const ADMIN_INSTANCE = ROUTES.AI_AGENT_INSTANCE;

export default function BentoAIAgentPage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const isNew = id === "new";

  const { data: agent, isError } = useAiAgent(isNew ? undefined : id);
  const error = isError ? t("common.connectionError", { resource: t("aiAgent.title").toLowerCase() }) : null;

  if (isNew) {
    return <NewAgentBento />;
  }

  return (
    <LoadProvider checkIsNotUndefined={agent} error={error} tryAgainUrl={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/${id}`}>
      {agent && <AgentBento agent={agent} />}
    </LoadProvider>
  );
}

function AgentBento({ agent }: Readonly<{ agent: TurAIAgent }>) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const enabled = agent.enabled === 1;
  const adminBase = `${ADMIN_INSTANCE}/${agent.id}`;

  const updateMutation = useUpdateAiAgent();
  const deleteMutation = useDeleteAiAgent();
  const [deleteOpen, setDeleteOpen] = useState(false);

  /*
   * Dashboard pages have no save bar to host destructive actions, and
   * the legacy SubPage's sidebar trash button doesn't exist in bento
   * either. Surface delete via a `⋮` menu in the hero trailing slot —
   * the standard "more options" location, hidden by default so it
   * doesn't visually compete with the primary content.
   */
  async function onDelete() {
    try {
      if (await deleteMutation.mutateAsync(agent)) {
        toast.success(t("aiAgent.deleted", { name: agent.title }));
        navigate(ROUTES.BENTO_AI_AGENT_INSTANCE);
      } else {
        toast.error(t("aiAgent.notDeleted", { name: agent.title }));
      }
    } catch (err) {
      console.error("Failed to delete AI agent", err);
      toast.error(t("aiAgent.notDeleted", { name: agent.title }));
    }
    setDeleteOpen(false);
  }

  /*
   * Dashboard pages aren't backed by a form, so inline edits in the
   * header (icon, title, description, status) can't "stage" changes
   * waiting for a Save button. Each field commits immediately via the
   * shared update mutation — the payload is a partial update merged
   * into the loaded agent. iOS-style: change it, it's saved.
   */
  async function persistField(patch: Partial<TurAIAgent>) {
    try {
      await updateMutation.mutateAsync({ ...agent, ...patch });
      toast.success(t("forms.common.updated", { name: agent.title, feature: t("aiAgent.title") }));
    } catch (err) {
      console.error("Failed to update AI agent field", err);
      toast.error(t("forms.common.notUpdated", { name: agent.title, feature: t("aiAgent.title") }));
    }
  }

  const counts = useMemo(() => ({
    llm: agent.llmInstances?.length ?? 0,
    mcp: agent.mcpServers?.length ?? 0,
    custom: agent.customTools?.length ?? 0,
    nativeTools: agent.nativeTools ? agent.nativeTools.split(",").filter(Boolean).length : 0,
  }), [agent]);

  return (
    <>
      <BentoHero
        eyebrow={
          <Link to={ROUTES.BENTO_AI_AGENT_INSTANCE} className="hover:text-foreground">
            {t("aiAgent.title")}
          </Link>
        }
        leading={
          <BentoHeroIconPicker
            value={agent.icon}
            onChange={(icon) => persistField({ icon })}
            onClear={() => persistField({ icon: null })}
            defaultIcon={IconRobot}
            tone="blue"
            title={agent.title}
            description={agent.description}
          />
        }
        title={
          <BentoInlineEdit
            value={agent.title}
            onSave={(title) => persistField({ title })}
            placeholder={t("aiAgent.title")}
            className="text-3xl font-semibold tracking-tight md:text-4xl"
            ariaLabel={t("forms.common.title")}
          />
        }
        subtitle={
          <BentoInlineEdit
            value={agent.description ?? ""}
            onSave={(description) => persistField({ description })}
            multiline
            placeholder={t("forms.common.description")}
            className="max-w-2xl text-sm text-muted-foreground"
            ariaLabel={t("forms.common.description")}
          />
        }
        trailing={
          <>
            <button
              type="button"
              onClick={() => persistField({ enabled: enabled ? 0 : 1 })}
              aria-label={t("forms.common.enabled")}
              title={enabled ? "Active — click to disable" : "Idle — click to enable"}
              className={`bento-tile-clickable flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[10px] uppercase tracking-wider transition-colors duration-200 ${
                enabled
                  ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-600 hover:bg-emerald-500/20 dark:text-emerald-400"
                  : "border-border bg-muted text-muted-foreground hover:bg-muted/80"
              }`}
            >
              <span className={`h-1.5 w-1.5 rounded-full ${enabled ? "bg-emerald-500 bento-pulse" : "bg-muted-foreground/60"}`} />
              {enabled ? "Active" : "Idle"}
            </button>
            <BentoActionsMenu
              actions={[
                {
                  label: t("forms.formActions.delete"),
                  icon: IconTrash,
                  tone: "destructive",
                  onSelect: () => setDeleteOpen(true),
                },
              ]}
            />
          </>
        }
      />

      {/*
       * Controlled DialogDelete — its built-in trigger is hidden so the
       * dropdown's "Delete" item is the only entry point. `open`/`setOpen`
       * drive the modal externally.
       */}
      <DialogDelete
        feature={t("aiAgent.title")}
        name={agent.title}
        onDelete={onDelete}
        open={deleteOpen}
        setOpen={setDeleteOpen}
        trigger={<span className="hidden" aria-hidden />}
      />

      <div className="bento-grid grid auto-rows-[minmax(140px,auto)] grid-cols-2 gap-4 md:grid-cols-4 md:gap-5 lg:grid-cols-6">
        {/* HERO: System prompt — large 2x2 frosted tile */}
        <BentoTile
          to={`${adminBase}/settings`}
          icon={IconSettings}
          tone="blue"
          span="col-span-2 row-span-2 md:col-span-2 md:row-span-2 lg:col-span-3 lg:row-span-2"
          eyebrow={t("aiAgent.settings.title")}
          title={agent.systemPrompt ? "System prompt" : "Set up your system prompt"}
        >
          <p className="line-clamp-5 text-sm text-muted-foreground">
            {agent.systemPrompt?.trim() || t("aiAgent.settings.description")}
          </p>
        </BentoTile>

        {/* RAG state */}
        <BentoTile
          to={`${adminBase}/settings`}
          icon={IconSparkles}
          tone="indigo"
          span="col-span-2 row-span-1 md:col-span-2 md:row-span-1 lg:col-span-3 lg:row-span-1"
          eyebrow="RAG"
          title={agent.ragEnabled ? "Retrieval augmented" : "RAG disabled"}
        >
          <p className="text-sm text-muted-foreground">
            {agent.ragEnabled
              ? "Augments answers with retrieved context."
              : "Toggle on to ground answers in your data."}
          </p>
        </BentoTile>

        <BentoCountTile
          to={`${adminBase}/llm`}
          icon={IconCpu2}
          tone="violet"
          label={t("aiAgent.nav.languageModels")}
          count={counts.llm}
          span="col-span-1 row-span-1 lg:col-span-1 lg:row-span-1"
        />
        <BentoCountTile
          to={`${adminBase}/tools`}
          icon={IconTool}
          tone="emerald"
          label={t("aiAgent.nav.nativeTools")}
          count={counts.nativeTools}
          span="col-span-1 row-span-1"
        />
        <BentoCountTile
          to={`${adminBase}/mcp`}
          icon={IconServer2}
          tone="amber"
          label={t("aiAgent.nav.mcpServers")}
          count={counts.mcp}
          span="col-span-1 row-span-1"
        />
        <BentoCountTile
          to={`${adminBase}/custom-tool`}
          icon={IconBraces}
          tone="rose"
          label={t("aiAgent.nav.customTools")}
          count={counts.custom}
          span="col-span-1 row-span-1"
        />

        <BentoTile
          to={`${adminBase}/intent`}
          icon={IconBulb}
          tone="amber"
          span="col-span-2 row-span-1 lg:col-span-2 lg:row-span-1"
          eyebrow={t("aiAgent.nav.chat")}
          title={t("aiAgent.nav.intents")}
        >
          <p className="text-sm text-muted-foreground">
            Predefined intents the agent can recognise and route.
          </p>
        </BentoTile>

        <BentoTile
          to={`${adminBase}/chat-flow`}
          icon={IconSitemap}
          tone="indigo"
          span="col-span-2 row-span-1 lg:col-span-2 lg:row-span-1"
          eyebrow={t("aiAgent.nav.chat")}
          title={t("aiAgent.nav.chatFlow")}
        >
          <p className="text-sm text-muted-foreground">
            Visual flows that orchestrate multi-step conversations.
          </p>
        </BentoTile>

        <BentoTile
          to={`${adminBase}/history`}
          icon={IconHistory}
          tone="slate"
          span="col-span-2 row-span-1 lg:col-span-2 lg:row-span-1"
          eyebrow={t("aiAgent.nav.chat")}
          title={t("aiAgent.nav.history")}
        >
          <p className="text-sm text-muted-foreground">
            Past conversations grouped by completed flow runs.
          </p>
        </BentoTile>
      </div>
    </>
  );
}

function NewAgentBento() {
  const { t } = useTranslation();
  return (
    <>
      <div className="bento-shell-header mb-6 flex flex-col gap-1 md:mb-8">
        <span className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t("aiAgent.title")}</span>
        <h1 className="text-3xl font-semibold tracking-tight md:text-4xl">{t("aiAgent.newInstance")}</h1>
      </div>
      <div className="bento-grid grid auto-rows-[minmax(140px,auto)] grid-cols-2 gap-4 md:grid-cols-4 md:gap-5">
        <BentoTile
          to={`${ADMIN_INSTANCE}/new/settings`}
          icon={IconSettings}
          tone="blue"
          span="col-span-2 row-span-2 md:col-span-4 md:row-span-2"
          eyebrow={t("aiAgent.settings.title")}
          title="Start with the basics"
        >
          <p className="text-sm text-muted-foreground">
            {t("aiAgent.settings.description")}
          </p>
        </BentoTile>
      </div>
    </>
  );
}

