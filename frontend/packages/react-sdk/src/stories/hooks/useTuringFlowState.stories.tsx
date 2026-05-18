import type { Meta, StoryObj } from "@storybook/react-vite";
import { useEffect, useState } from "react";

/**
 * # useTuringFlowState
 *
 * Reads the active chat-flow state for the visitor's conversation —
 * which flow is driving, which node is the cursor, what guardrail
 * method is enforcing, A/B experiment metadata. Complement to
 * `useTuringSlots`: slots = data captured; flow state = where the
 * engine is in the conversation graph.
 *
 * ## Key Features
 * - `state`: `TurChatConversationState | null` —
 *   `{ conversationId, flowId, flowName, currentNodeId, guardrailMethod,
 *     experimentKey, variantLabel }`
 * - `conversationId`: read from cookie or option override
 * - `isLoading`, `error`: standard request lifecycle
 * - `refresh()`: imperative re-read (rarely needed — `pollInterval`
 *   covers the polling case)
 * - `pollInterval`: refresh cadence in ms (default 0 = no polling)
 *
 * ## Usage
 * ```tsx
 * const { state } = useTuringFlowState({ pollInterval: 2000 });
 * return (
 *   <div>
 *     Flow: {state?.flowName ?? "—"}
 *     <br/>
 *     Cursor: {state?.currentNodeId ?? "—"}
 *     <br/>
 *     Variant: {state?.variantLabel ?? "no A/B"}
 *   </div>
 * );
 * ```
 *
 * ## When to use
 *
 * - **Debug surfaces** (SlotInspector, admin console) that need to show
 *   "the engine is currently on node X of flow Y"
 * - **Variant-aware UI** that branches on `variantLabel` without dragging
 *   the whole flow-state shape through props
 * - **Flow-aware features** — e.g. show a different progress bar when
 *   `flowName === "Programa-Match"` vs `"In-Company Match"`
 *
 * ## About this story
 *
 * The story simulates the polling fetch with state that progresses
 * through a realistic Executive Education flow (`ai-name` → `ai-cargo` →
 * `ai-objetivo` → `ai-area` → `ai-confirma-financas`). Use the Refresh
 * button to advance the cursor manually, or set `simulatedAutoAdvance`
 * to drive it forward on a timer.
 */

interface SimulatedFlowState {
  conversationId: string;
  flowId: string | null;
  flowName: string | null;
  currentNodeId: string | null;
  guardrailMethod: string | null;
  experimentKey: string | null;
  variantLabel: string | null;
}

const FLOW_TIMELINE: ReadonlyArray<{ nodeId: string; label: string }> = [
  { nodeId: "start-1", label: "Início" },
  { nodeId: "persona-marina", label: "Persona Marina" },
  { nodeId: "ai-name", label: "Capturando nome" },
  { nodeId: "ai-cargo", label: "Capturando cargo" },
  { nodeId: "ai-objetivo", label: "Capturando objetivo" },
  { nodeId: "ai-area", label: "Capturando área" },
  { nodeId: "switch-area", label: "Roteando por área" },
  { nodeId: "ai-confirma-financas", label: "Fechamento Finanças" },
  { nodeId: "switch-cta", label: "Escolha do CTA" },
  { nodeId: "end-shared", label: "Fim" },
];

function FlowStateDemo({
  flowName,
  variantLabel,
  guardrailMethod,
  pollInterval,
  simulatedAutoAdvance,
}: {
  flowName: string;
  variantLabel: string;
  guardrailMethod: string;
  pollInterval: number;
  simulatedAutoAdvance: boolean;
}) {
  const [cursor, setCursor] = useState(0);
  const [tick, setTick] = useState(0); // forces refresh

  const state: SimulatedFlowState = {
    conversationId: "demo-conv-abc123",
    flowId: "EE_PROGRAMA_MATCH",
    flowName,
    currentNodeId: FLOW_TIMELINE[cursor]?.nodeId ?? null,
    guardrailMethod,
    experimentKey: "programa-match-persona-2026q1",
    variantLabel,
  };

  useEffect(() => {
    if (!simulatedAutoAdvance) return;
    const handle = globalThis.setInterval(() => {
      setCursor((c) => (c + 1) % FLOW_TIMELINE.length);
    }, Math.max(pollInterval, 800));
    return () => globalThis.clearInterval(handle);
  }, [simulatedAutoAdvance, pollInterval]);

  // Ack the poll-interval prop so consumers see the polling cadence
  // surface in the demo metadata — real hook re-fetches on this timer.
  useEffect(() => {
    if (pollInterval <= 0) return;
    const handle = globalThis.setInterval(() => setTick((t) => t + 1), pollInterval);
    return () => globalThis.clearInterval(handle);
  }, [pollInterval]);

  function manualAdvance() {
    setCursor((c) => Math.min(c + 1, FLOW_TIMELINE.length - 1));
  }
  function manualRewind() {
    setCursor((c) => Math.max(c - 1, 0));
  }
  function refresh() {
    setTick((t) => t + 1);
  }

  return (
    <div style={{ fontFamily: "system-ui, sans-serif", maxWidth: "560px" }}>
      {/* State snapshot card */}
      <div style={{
        border: "1px solid #e2e8f0",
        borderRadius: "12px",
        overflow: "hidden",
        marginBottom: "14px",
      }}>
        <div style={{
          padding: "10px 14px",
          background: "#0f172a",
          color: "white",
          fontSize: "12px",
          fontWeight: 600,
          textTransform: "uppercase",
          letterSpacing: "0.05em",
        }}>
          state · poll tick #{tick}
        </div>
        <div style={{ padding: "12px 14px", fontFamily: "monospace", fontSize: "12px" }}>
          <KV k="conversationId" v={state.conversationId} mono />
          <KV k="flowId" v={state.flowId} mono />
          <KV k="flowName" v={state.flowName} />
          <KV k="currentNodeId" v={state.currentNodeId} mono highlight />
          <KV k="guardrailMethod" v={state.guardrailMethod} mono />
          <KV k="experimentKey" v={state.experimentKey} mono />
          <KV k="variantLabel" v={state.variantLabel} mono />
        </div>
      </div>

      {/* Timeline visualization */}
      <div style={{
        border: "1px solid #e2e8f0",
        borderRadius: "12px",
        overflow: "hidden",
        marginBottom: "14px",
      }}>
        <div style={{
          padding: "10px 14px",
          background: "#f1f5f9",
          fontSize: "12px",
          fontWeight: 600,
          color: "#475569",
          textTransform: "uppercase",
          letterSpacing: "0.05em",
        }}>Cursor through the flow</div>
        <div style={{ padding: "14px" }}>
          {FLOW_TIMELINE.map((step, i) => {
            const isCursor = i === cursor;
            const isPast = i < cursor;
            return (
              <div key={step.nodeId} style={{
                display: "flex",
                alignItems: "center",
                gap: "10px",
                padding: "6px 0",
                opacity: isCursor ? 1 : (isPast ? 0.7 : 0.4),
              }}>
                <div style={{
                  width: 22, height: 22, borderRadius: "50%",
                  background: isCursor ? "#2563eb" : (isPast ? "#22c55e" : "#cbd5e1"),
                  color: "white",
                  display: "inline-flex",
                  alignItems: "center",
                  justifyContent: "center",
                  fontSize: 11,
                  fontWeight: 700,
                }}>{isPast ? "✓" : isCursor ? "▸" : i + 1}</div>
                <code style={{ fontSize: 12, color: "#0f172a" }}>{step.nodeId}</code>
                <span style={{ fontSize: 12, color: "#64748b" }}>· {step.label}</span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Controls */}
      <div style={{ display: "flex", gap: "8px" }}>
        <button type="button" onClick={manualRewind}
                style={{ padding: "8px 14px", borderRadius: 8,
                         border: "1px solid #cbd5e1", background: "white",
                         color: "#475569", fontSize: 12, cursor: "pointer" }}>
          ← Recuar nó
        </button>
        <button type="button" onClick={manualAdvance}
                style={{ padding: "8px 14px", borderRadius: 8,
                         border: "1px solid #cbd5e1", background: "white",
                         color: "#475569", fontSize: 12, cursor: "pointer" }}>
          Avançar nó →
        </button>
        <button type="button" onClick={refresh}
                style={{ padding: "8px 14px", borderRadius: 8, border: "none",
                         background: "#2563eb", color: "white", fontSize: 12,
                         cursor: "pointer", marginLeft: "auto" }}>
          Refresh
        </button>
      </div>
    </div>
  );
}

function KV({ k, v, mono, highlight }: {
  k: string;
  v: string | null;
  mono?: boolean;
  highlight?: boolean;
}) {
  return (
    <div style={{
      display: "flex",
      justifyContent: "space-between",
      padding: "3px 0",
      background: highlight ? "rgba(37, 99, 235, 0.06)" : undefined,
      borderRadius: highlight ? 4 : undefined,
      paddingLeft: highlight ? 6 : 0,
      paddingRight: highlight ? 6 : 0,
    }}>
      <span style={{ color: "#64748b" }}>{k}</span>
      <span style={{
        color: highlight ? "#2563eb" : "#0f172a",
        fontFamily: mono ? "monospace" : "system-ui, sans-serif",
        fontWeight: highlight ? 700 : 400,
      }}>{v ?? "null"}</span>
    </div>
  );
}

const meta: Meta<typeof FlowStateDemo> = {
  title: "Hooks/useTuringFlowState",
  component: FlowStateDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Reads the active chat-flow state — flow identity, current cursor node, guardrail method, A/B experiment metadata. The story walks a realistic Programa-Match cursor through 10 nodes, with manual ◀ / ▶ controls and an auto-advance toggle.",
      },
    },
  },
  argTypes: {
    flowName: {
      description: "Simulated flow name reported by the engine.",
      control: { type: "select" },
      options: [
        "Programa-Match — Educação Executiva",
        "Programa-Match — Educação Executiva (Lucas Alumni)",
        "In-Company Match — Executive Education Corporate",
        "Aula-Relâmpago Executive Education",
      ],
    },
    variantLabel: {
      description: "A/B variant assigned to this conversation.",
      control: { type: "select" },
      options: ["marina-consultora", "lucas-alumni", "camila-account-exec", "null"],
    },
    guardrailMethod: {
      description: "Engine's guardrail strategy.",
      control: { type: "select" },
      options: ["LLM_JUDGE", "HEURISTIC", "STRUCTURED_OUTPUT"],
    },
    pollInterval: {
      description: "Polling cadence (ms). 0 = no polling, refresh manually.",
      control: { type: "range", min: 0, max: 5000, step: 500 },
    },
    simulatedAutoAdvance: {
      description: "Auto-advance the cursor on the poll interval — mimics a fast-running conversation.",
      control: { type: "boolean" },
    },
  },
};

export default meta;
type Story = StoryObj<typeof FlowStateDemo>;

export const MarinaConsultora: Story = {
  name: "Marina variant — manual stepping",
  args: {
    flowName: "Programa-Match — Educação Executiva",
    variantLabel: "marina-consultora",
    guardrailMethod: "LLM_JUDGE",
    pollInterval: 2000,
    simulatedAutoAdvance: false,
  },
};

export const LucasAlumni: Story = {
  name: "Lucas variant — auto-advance every 2s",
  args: {
    flowName: "Programa-Match — Educação Executiva (Lucas Alumni)",
    variantLabel: "lucas-alumni",
    guardrailMethod: "LLM_JUDGE",
    pollInterval: 2000,
    simulatedAutoAdvance: true,
  },
};

export const InCompany: Story = {
  name: "Camila B2B — no A/B label (single-variant flow)",
  args: {
    flowName: "In-Company Match — Executive Education Corporate",
    variantLabel: "camila-account-exec",
    guardrailMethod: "LLM_JUDGE",
    pollInterval: 0,
    simulatedAutoAdvance: false,
  },
};
