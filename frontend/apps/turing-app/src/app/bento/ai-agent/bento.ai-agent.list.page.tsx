import { useAiAgents } from "@/api/queries/ai-agent.queries";
import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts";
import { IconPlus, IconRobot, IconSparkles } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

export default function BentoAIAgentListPage() {
  const { t } = useTranslation();
  const { data: agents, isError } = useAiAgents();
  const error = isError ? t("common.connectionError", { resource: "AI agents" }) : null;

  return (
    <LoadProvider checkIsNotUndefined={agents} error={error} tryAgainUrl={ROUTES.BENTO_AI_AGENT_INSTANCE}>
      <div className="bento-shell-header mb-6 flex flex-col gap-1 md:mb-8">
        <span className="text-xs uppercase tracking-[0.18em] text-muted-foreground">Bento</span>
        <h1 className="text-3xl font-semibold tracking-tight md:text-4xl">
          {t("aiAgent.title")}
        </h1>
        <p className="max-w-xl text-sm text-muted-foreground">
          {t("aiAgent.blankDescription")}
        </p>
      </div>

      <div className="bento-grid grid auto-rows-[minmax(140px,auto)] grid-cols-2 gap-4 md:grid-cols-4 md:gap-5 lg:grid-cols-6">
        <NewAgentTile />
        {(agents ?? []).map((agent, idx) => (
          <AgentTile key={agent.id} agent={agent} featured={idx === 0} />
        ))}
        {agents?.length === 0 && <EmptyHintTile />}
      </div>
    </LoadProvider>
  );
}

function NewAgentTile() {
  return (
    <Link
      to={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/new`}
      className="bento-tile bento-tile-clickable bento-glass relative col-span-2 row-span-1 flex flex-col justify-between p-5 md:col-span-2 md:row-span-2 lg:col-span-2 lg:row-span-2"
    >
      <div className="flex items-center gap-2">
        <span className="bento-pulse grid h-9 w-9 place-items-center rounded-2xl bg-linear-to-br from-blue-600 to-indigo-600 text-white shadow-md">
          <IconPlus size={18} />
        </span>
        <span className="text-xs uppercase tracking-wider text-muted-foreground">New</span>
      </div>
      <div>
        <div className="text-xl font-semibold tracking-tight md:text-2xl">
          {/* Reuses existing translation key */}
          <span>+ </span>
          <span className="bg-linear-to-br from-blue-600 to-indigo-600 bg-clip-text text-transparent">
            AI Agent
          </span>
        </div>
        <p className="mt-1 text-sm text-muted-foreground">
          Orchestrate LLMs, tools and MCP servers.
        </p>
      </div>
    </Link>
  );
}

function AgentTile({ agent, featured }: Readonly<{ agent: TurAIAgent; featured: boolean }>) {
  const span = featured
    ? "col-span-2 row-span-2 md:col-span-2 md:row-span-2 lg:col-span-2 lg:row-span-2"
    : "col-span-2 row-span-1 md:col-span-2 md:row-span-1 lg:col-span-2 lg:row-span-1";
  const enabled = agent.enabled === 1;

  return (
    <Link
      to={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/${agent.id}`}
      className={`bento-tile bento-tile-clickable bento-glass group relative flex flex-col justify-between p-5 ${span}`}
      style={{ viewTransitionName: `bento-agent-${agent.id}` } as React.CSSProperties}
    >
      <div className="flex items-center justify-between">
        <span className="grid h-9 w-9 place-items-center rounded-2xl bg-linear-to-br from-blue-600 to-indigo-600 text-white shadow-md">
          <IconRobot size={18} />
        </span>
        <span className={`flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[10px] uppercase tracking-wider ${
          enabled
            ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
            : "border-border bg-muted text-muted-foreground"
        }`}>
          <span className={`h-1.5 w-1.5 rounded-full ${enabled ? "bg-emerald-500 bento-pulse" : "bg-muted-foreground/60"}`} />
          {enabled ? "Active" : "Idle"}
        </span>
      </div>
      <div>
        <div className="text-base font-semibold tracking-tight md:text-lg">
          {agent.title}
        </div>
        {agent.description && (
          <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">
            {agent.description}
          </p>
        )}
      </div>
    </Link>
  );
}

function EmptyHintTile() {
  const { t } = useTranslation();
  return (
    <div className="bento-tile bento-glass col-span-2 row-span-2 flex flex-col items-start justify-end p-5 md:col-span-4">
      <IconSparkles size={20} className="mb-3 text-indigo-500" />
      <div className="text-lg font-semibold tracking-tight">{t("aiAgent.blankTitle")}</div>
      <p className="mt-1 max-w-md text-sm text-muted-foreground">{t("aiAgent.blankDescription")}</p>
    </div>
  );
}
