import { BrandHeader } from "@/components/brand-header";
import {
  IconActivity,
  IconChartBar,
  IconCoin,
  IconGauge,
  IconPlugConnected,
  IconRobot,
  IconWaveSine,
} from "@tabler/icons-react";
import type { ReactNode } from "react";

/**
 * T456 — "Ops tour" route. A guided map of the operational surfaces the Atlas
 * Store agent exposes once it runs against a real Turing backend. These are
 * admin-console / authenticated endpoints (not anonymous SDK calls), so this
 * page documents each capability, its endpoint, and where to view it live —
 * rather than faking dashboards the offline seed can't populate.
 */
interface OpsCard {
  icon: ReactNode;
  title: string;
  task: string;
  body: string;
  endpoint?: string;
}

const SECTIONS: { heading: string; cards: OpsCard[] }[] = [
  {
    heading: "Chat analytics",
    cards: [
      {
        icon: <IconWaveSine className="size-5" />,
        title: "Sentiment trajectory",
        task: "T87",
        body: "Per-turn sentiment for each conversation, charted over the session so you can see where a chat turned negative.",
        endpoint: "GET /api/.../chat-analytics/… (admin)",
      },
      {
        icon: <IconGauge className="size-5" />,
        title: "Per-tool latency p95",
        task: "T88",
        body: "Raw per-tool latency samples per session; p95 (not summable) computed across tools to spot the slow tool in the loop.",
        endpoint: "GET /chat-analytics/tool-latency",
      },
      {
        icon: <IconActivity className="size-5" />,
        title: "SSE channel debug",
        task: "T90",
        body: "Live count of open slot-stream SSE channels (refcounted) — a real-time view of who's connected.",
        endpoint: "GET /chat-analytics/slot-sse-channels",
      },
    ],
  },
  {
    heading: "Quality & self-improvement",
    cards: [
      {
        icon: <IconChartBar className="size-5" />,
        title: "Agent eval + golden sets",
        task: "Block K",
        body: "Run the agent against golden conversation fixtures; the green baseline gates prompt changes (and the T430 CI gate).",
        endpoint: "turing eval / admin eval runner",
      },
      {
        icon: <IconRobot className="size-5" />,
        title: "Self-tuning suggestions",
        task: "T447",
        body: "A nightly job mines failed conversations, drafts a better system prompt, scores it against the golden sets, and opens a PR-style suggestion — never auto-applied.",
        endpoint: "GET/POST /api/.../agent-suggestions (admin)",
      },
    ],
  },
  {
    heading: "Cost & integration",
    cards: [
      {
        icon: <IconCoin className="size-5" />,
        title: "Cost governance",
        task: "Block L",
        body: "Per-tenant / per-agent / per-stage token spend with budget thresholds and soft caps that downgrade to a cheaper tier near the limit.",
        endpoint: "System Info → AI Spend (admin)",
      },
      {
        icon: <IconPlugConnected className="size-5" />,
        title: "MCP server exposure",
        task: "Block I",
        body: "The Atlas catalog is exposed over Turing's MCP server (list_sites / get_site_fields / search_site), so you can drive the store from Claude Desktop. See mcp/claude-desktop.json.",
        endpoint: "Turing MCP server",
      },
    ],
  },
];

export default function OpsPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <BrandHeader />
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-10 sm:px-6">
        <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">Ops tour</h1>
        <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
          The operational surfaces behind the Atlas Store agent. These light up in
          the Turing admin console when the showcase runs against a real backend;
          this page maps each capability to its endpoint.
        </p>

        {SECTIONS.map((section) => (
          <section key={section.heading} className="mt-8">
            <h2 className="mb-3 text-sm font-bold uppercase tracking-wide text-muted-foreground">
              {section.heading}
            </h2>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {section.cards.map((card) => (
                <div
                  key={card.title}
                  className="flex flex-col gap-2 rounded-xl border border-border/60 bg-card p-4"
                >
                  <div className="flex items-center gap-2 text-primary">
                    {card.icon}
                    <span className="text-sm font-semibold text-foreground">{card.title}</span>
                    <span className="ml-auto rounded-full bg-muted px-2 py-0.5 text-[10px] font-medium text-muted-foreground">
                      {card.task}
                    </span>
                  </div>
                  <p className="text-xs leading-relaxed text-muted-foreground">{card.body}</p>
                  {card.endpoint && (
                    <code className="mt-auto rounded bg-muted px-2 py-1 text-[11px] text-foreground/70">
                      {card.endpoint}
                    </code>
                  )}
                </div>
              ))}
            </div>
          </section>
        ))}

        <a href="#/" className="mt-10 inline-block text-sm text-primary hover:underline">
          ← Back to the storefront
        </a>
      </main>
    </div>
  );
}
