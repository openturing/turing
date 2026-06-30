import type { Meta, StoryObj } from "@storybook/react-vite";
import { useEffect, useRef, useState } from "react";

/**
 * # useTuringWorkspace
 *
 * Subscribes to an agent's **per-conversation workspace** — the files and
 * artifacts the agent builds while it works (a `report.md` it drafts, a
 * `chart.png` it renders, a `data.csv` it exports). It's an SSE-backed,
 * live-updating file panel: every `put`/`delete` the agent performs pushes a
 * fresh artifact list in under 100ms, with no polling. Each artifact carries a
 * `signedUrl` the visitor can download — the bytes never touch the prompt.
 *
 * Pairs with `useTuringChat`: both key off the same `TUR_SESSION` cookie, so
 * the artifacts you see here belong to the exact conversation the chat is
 * sending messages on. "Show me the files this agent just built for me."
 *
 * ## Key Features
 * - `artifacts`: live, sorted list of `{ key, contentType, size, signedUrl }`
 * - `status`: `"idle" | "loading" | "success" | "error"` stream lifecycle
 * - `conversationId`: the conversation the artifacts were read for (or `null`)
 * - `error`: human-readable message when the SSE channel closes unexpectedly
 * - **Two modes** — site mode (reads site from `<TuringProvider>`, pass
 *   `agentId` to back-fill the initial snapshot) and agent mode (pass
 *   `options.agent` to stream the agent-scoped endpoint with no site/provider)
 * - Auto-reconnects to the conversation as soon as the chat hook mints the
 *   session cookie on the first `send`
 *
 * ## Usage
 * ```tsx
 * const { artifacts, status } = useTuringWorkspace({ agentId });
 *
 * if (status === "error") return <p>Workspace stream closed.</p>;
 * return (
 *   <ul>
 *     {artifacts.map((a) => (
 *       <li key={a.key}>
 *         <a href={a.signedUrl ?? "#"} download>{a.key}</a>{" "}
 *         <small>({a.size} bytes)</small>
 *       </li>
 *     ))}
 *   </ul>
 * );
 * ```
 *
 * ## When to use
 * - **Research / report agents**: the agent gathers sources and writes a
 *   `report.md`; the panel fills up live so the user watches the deliverable
 *   take shape instead of staring at a spinner
 * - **Code-interpreter / data agents**: charts, CSV exports, and generated
 *   files surface as downloadable artifacts the moment they're written
 * - **Long-running tasks**: a side panel of "files built so far" gives
 *   progress feedback during multi-step tool runs
 *
 * ## About this story
 * Fully self-contained — **no real hook, Provider, network, or SSE**. A
 * `setInterval` drips artifacts into local state on a timeline to teach the
 * "live workspace fills up as the agent works" feel: you'll see the agent
 * announce a step, then the matching file appear (and `report.md` grow as it's
 * re-written). File-type icons, human-readable sizes, and a fake Download
 * affordance are all simulated.
 */

type Status = "idle" | "loading" | "success" | "error";

/** Local mirror of the SDK's `TurWorkspaceArtifact` (kept local for TS hygiene). */
interface Artifact {
  readonly key: string;
  readonly contentType: string | null;
  readonly size: number;
}

/** One scripted beat in the agent's run: a status line + the file it writes. */
interface Step {
  readonly label: string;
  readonly artifact: Artifact;
}

const GRADIENT = "linear-gradient(135deg, #2563eb 0%, #4f46e5 100%)";

/** The research agent's timeline — each beat lands ~1.3s after the last. */
const RESEARCH_TIMELINE: ReadonlyArray<Step> = [
  {
    label: "Drafting the outline…",
    artifact: { key: "report.md", contentType: "text/markdown", size: 1_240 },
  },
  {
    label: "Pulling the dataset together…",
    artifact: { key: "data.csv", contentType: "text/csv", size: 48_910 },
  },
  {
    label: "Rendering the trend chart…",
    artifact: { key: "chart.png", contentType: "image/png", size: 312_400 },
  },
  {
    label: "Expanding the report with findings…",
    // Same key → an update; the panel replaces the row, size grows.
    artifact: { key: "report.md", contentType: "text/markdown", size: 18_730 },
  },
  {
    label: "Bundling sources as JSON…",
    artifact: {
      key: "sources.json",
      contentType: "application/json",
      size: 6_502,
    },
  },
];

function iconFor(contentType: string | null, key: string): string {
  if (contentType?.startsWith("image/")) return "🖼️";
  if (contentType === "text/csv" || key.endsWith(".csv")) return "📊";
  if (contentType === "application/json" || key.endsWith(".json")) return "🧩";
  if (contentType === "text/markdown" || key.endsWith(".md")) return "📝";
  return "📄";
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ["KB", "MB", "GB"];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(value < 10 ? 1 : 0)} ${units[unit]}`;
}

interface Theme {
  readonly dark: boolean;
  readonly panelBg: string;
  readonly rowBg: string;
  readonly border: string;
  readonly text: string;
  readonly muted: string;
  readonly chipBg: string;
}

function themeFor(dark: boolean): Theme {
  return dark
    ? {
        dark,
        panelBg: "#0f172a",
        rowBg: "#1e293b",
        border: "#334155",
        text: "#f1f5f9",
        muted: "#94a3b8",
        chipBg: "#334155",
      }
    : {
        dark,
        panelBg: "#ffffff",
        rowBg: "#f8fafc",
        border: "#e2e8f0",
        text: "#0f172a",
        muted: "#64748b",
        chipBg: "#eef2ff",
      };
}

function WorkspaceDemo({
  stepMs,
  dark,
}: {
  stepMs: number;
  dark: boolean;
}) {
  const theme = themeFor(dark);
  const [artifacts, setArtifacts] = useState<readonly Artifact[]>([]);
  const [status, setStatus] = useState<Status>("idle");
  const [stepLabel, setStepLabel] = useState("Waiting for the agent to start…");
  const [done, setDone] = useState(false);
  const [runId, setRunId] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Reset + replay the timeline whenever the run id (or the cadence) changes.
  useEffect(() => {
    setArtifacts([]);
    setDone(false);
    setStatus("loading");
    setStepLabel("Agent connected — starting work…");

    let i = 0;
    timerRef.current = setInterval(() => {
      const step = RESEARCH_TIMELINE[i];
      if (!step) {
        if (timerRef.current) clearInterval(timerRef.current);
        timerRef.current = null;
        setStepLabel("✓ Task complete — artifacts ready to download");
        setDone(true);
        return;
      }
      setStatus("success");
      setStepLabel(step.label);
      setArtifacts((prev) => {
        // Same key → update in place (mirrors a re-`put`); else append.
        const without = prev.filter((a) => a.key !== step.artifact.key);
        return [...without, step.artifact].sort((a, b) =>
          a.key.localeCompare(b.key),
        );
      });
      i++;
    }, stepMs);

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
      timerRef.current = null;
    };
  }, [stepMs, runId]);

  const totalBytes = artifacts.reduce((sum, a) => sum + a.size, 0);

  return (
    <div
      style={{
        fontFamily: "system-ui, sans-serif",
        maxWidth: "460px",
        color: theme.text,
      }}
    >
      {/* Agent banner */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: "10px",
          padding: "12px 16px",
          borderRadius: "12px 12px 0 0",
          background: GRADIENT,
          color: "white",
        }}
      >
        <span style={{ fontSize: "20px" }} aria-hidden="true">
          🤖
        </span>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: "14px", fontWeight: 700 }}>Research Agent</div>
          <div style={{ fontSize: "11px", opacity: 0.85 }}>
            conversation · 7f3a-c20e
          </div>
        </div>
        <code
          style={{
            fontSize: "11px",
            padding: "3px 8px",
            borderRadius: "999px",
            background: "rgba(255,255,255,0.2)",
            color: "white",
          }}
        >
          status: {status}
        </code>
      </div>

      {/* Live step line */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: "8px",
          padding: "10px 16px",
          background: theme.rowBg,
          borderLeft: `1px solid ${theme.border}`,
          borderRight: `1px solid ${theme.border}`,
          fontSize: "12px",
          color: theme.muted,
        }}
      >
        {!done && (
          <span
            style={{
              width: "10px",
              height: "10px",
              borderRadius: "50%",
              border: "2px solid #93c5fd",
              borderTopColor: "#2563eb",
              animation: "tws-spin 0.8s linear infinite",
              flexShrink: 0,
            }}
          />
        )}
        <span>{stepLabel}</span>
      </div>

      {/* Workspace panel — "Files" header */}
      <div
        style={{
          border: `1px solid ${theme.border}`,
          borderTop: "none",
          borderRadius: "0 0 12px 12px",
          overflow: "hidden",
          background: theme.panelBg,
        }}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            padding: "10px 16px",
            borderBottom: `1px solid ${theme.border}`,
            fontSize: "12px",
            fontWeight: 600,
            color: theme.muted,
            textTransform: "uppercase",
            letterSpacing: "0.05em",
          }}
        >
          <span>📁 Workspace · Files ({artifacts.length})</span>
          {artifacts.length > 0 && <span>{formatBytes(totalBytes)} total</span>}
        </div>

        {artifacts.length === 0 ? (
          <div
            style={{
              padding: "28px 16px",
              textAlign: "center",
              color: theme.muted,
              fontSize: "13px",
            }}
          >
            No files yet — the agent will write artifacts here as it works.
          </div>
        ) : (
          artifacts.map((a) => (
            <div
              key={a.key}
              style={{
                display: "flex",
                alignItems: "center",
                gap: "12px",
                padding: "12px 16px",
                borderBottom: `1px solid ${theme.border}`,
                animation: "tws-fade-in 0.4s ease",
              }}
            >
              <span style={{ fontSize: "22px" }} aria-hidden="true">
                {iconFor(a.contentType, a.key)}
              </span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div
                  style={{
                    fontSize: "14px",
                    fontWeight: 600,
                    color: theme.text,
                    whiteSpace: "nowrap",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                  }}
                >
                  {a.key}
                </div>
                <div style={{ fontSize: "11px", color: theme.muted }}>
                  {a.contentType ?? "application/octet-stream"} ·{" "}
                  {formatBytes(a.size)}
                </div>
              </div>
              {/* Fake download affordance — the real artifact carries signedUrl. */}
              <button
                type="button"
                title={`Download ${a.key}`}
                onClick={() => {
                  /* no-op: signedUrl is simulated in this story */
                }}
                style={{
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "6px",
                  padding: "6px 12px",
                  borderRadius: "8px",
                  border: "none",
                  background: GRADIENT,
                  color: "white",
                  fontSize: "12px",
                  fontWeight: 600,
                  cursor: "pointer",
                  flexShrink: 0,
                }}
              >
                ⬇ Download
              </button>
            </div>
          ))
        )}
      </div>

      {/* Replay control */}
      <div style={{ marginTop: "12px", textAlign: "center" }}>
        <button
          type="button"
          onClick={() => setRunId((n) => n + 1)}
          style={{
            padding: "8px 16px",
            borderRadius: "8px",
            border: `1px solid ${theme.border}`,
            background: theme.rowBg,
            color: theme.text,
            fontSize: "13px",
            cursor: "pointer",
          }}
        >
          ↻ Replay the agent run
        </button>
      </div>

      <style>{`
        @keyframes tws-spin { to { transform: rotate(360deg); } }
        @keyframes tws-fade-in {
          from { opacity: 0; transform: translateY(-4px); }
          to { opacity: 1; transform: translateY(0); }
        }
      `}</style>
    </div>
  );
}

const meta: Meta<typeof WorkspaceDemo> = {
  title: "AI & Chat/useTuringWorkspace",
  component: WorkspaceDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Live per-conversation agent workspace — files/artifacts the agent builds (report.md, chart.png, data.csv) stream in over SSE with signed download URLs. This story is fully self-contained: a setInterval drips artifacts into local state on a timeline to demonstrate the 'live workspace fills up as the agent works' feel. No real hook, Provider, network, or SSE.",
      },
    },
  },
  argTypes: {
    stepMs: {
      description:
        "Milliseconds between each simulated artifact write (the agent's working cadence).",
      control: { type: "range", min: 300, max: 2500, step: 100 },
    },
    dark: {
      description: "Render the workspace panel in dark mode.",
      control: { type: "boolean" },
    },
  },
};

export default meta;
type Story = StoryObj<typeof WorkspaceDemo>;

export const ResearchAgent: Story = {
  name: "📁 Research agent fills the workspace",
  args: { stepMs: 1300, dark: false },
};

export const FastAgent: Story = {
  name: "🤖 Fast agent (rapid writes)",
  args: { stepMs: 500, dark: false },
};

export const DarkMode: Story = {
  name: "🌙 Dark mode workspace",
  args: { stepMs: 1300, dark: true },
};
