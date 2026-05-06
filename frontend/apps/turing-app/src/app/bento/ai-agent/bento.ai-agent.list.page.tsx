import { useAiAgents } from "@/api/queries/ai-agent.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts";
import { Icon } from "@iconify/react";
import { IconPlus, IconRobot, IconSparkles } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

export default function BentoAIAgentListPage() {
  const { t } = useTranslation();
  const { data: agents, isError } = useAiAgents();
  const error = isError ? t("common.connectionError", { resource: "AI agents" }) : null;

  return (
    <LoadProvider checkIsNotUndefined={agents} error={error} tryAgainUrl={ROUTES.BENTO_AI_AGENT_INSTANCE}>
      <BentoHero
        eyebrow={t("home.sections.generativeAi.label")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-blue-600 to-indigo-600 text-white shadow-md">
            <IconRobot size={24} />
          </span>
        }
        title={t("aiAgent.title")}
        subtitle={t("aiAgent.blankDescription")}
      />

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
    /*
     * Distinguished from content tiles by halved span (2x1) and a
     * dashed-border / no-ornament treatment that reads as a clear
     * "create" affordance rather than another agent card.
     */
    <Link
      to={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/new`}
      className="bento-tile bento-tile-clickable col-span-2 row-span-1 flex flex-col gap-3 rounded-3xl border-2 border-dashed border-border/60 bg-card/30 p-5 backdrop-blur-md hover:border-indigo-500/60 hover:bg-card/50 md:col-span-2 md:row-span-1 lg:col-span-2 lg:row-span-1"
    >
      <div className="flex items-center gap-3">
        <span className="bento-pulse grid h-10 w-10 place-items-center rounded-2xl bg-linear-to-br from-blue-600 to-indigo-600 text-white shadow-md">
          <IconPlus size={20} />
        </span>
        <span className="text-[11px] uppercase tracking-wider text-muted-foreground">New</span>
      </div>
      <div>
        <div className="text-base font-semibold tracking-tight md:text-lg">
          <span className="bg-linear-to-br from-blue-600 to-indigo-600 bg-clip-text text-transparent">
            + AI Agent
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
  const padding = featured ? "p-6 md:p-7" : "p-5";
  const iconChip = featured ? "h-14 w-14" : "h-9 w-9";
  const iconSize = featured ? 28 : 18;
  const titleSize = featured ? "text-xl md:text-2xl" : "text-base md:text-lg";

  return (
    <Link
      to={`${ROUTES.BENTO_AI_AGENT_INSTANCE}/${agent.id}`}
      className={`bento-tile bento-tile-clickable bento-glass group relative flex flex-col ${featured ? "gap-5" : "gap-4"} overflow-hidden ${padding} ${span}`}
      style={{ viewTransitionName: `bento-agent-${agent.id}` } as React.CSSProperties}
    >
      {featured && (
        <>
          <div aria-hidden className="pointer-events-none absolute -bottom-10 -right-10 h-64 w-64 rounded-full bg-linear-to-br from-blue-600 to-indigo-600 opacity-40 blur-2xl dark:opacity-50" />
          <div aria-hidden className="pointer-events-none absolute right-12 top-1/3 h-32 w-32 rounded-full bg-linear-to-tr from-blue-600 to-indigo-600 opacity-20 blur-2xl dark:opacity-30" />
        </>
      )}

      <div className="relative z-1 flex items-center justify-between">
        <span className={`grid place-items-center rounded-2xl bg-linear-to-br from-blue-600 to-indigo-600 text-white shadow-md ${iconChip}`}>
          {/* Render the user-selected Iconify icon when set, otherwise the default. */}
          {agent.icon
            ? <Icon icon={agent.icon} className={featured ? "size-7 text-white" : "size-4.5 text-white"} />
            : <IconRobot size={iconSize} />}
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
      <div className="relative z-1">
        <div className={`font-semibold tracking-tight ${titleSize}`}>
          {agent.title}
        </div>
        {agent.description && (
          <p className="mt-1 text-sm text-muted-foreground">
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
