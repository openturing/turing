import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";

/**
 * # useTuringFlowChooser
 *
 * Imperatively forces a specific chat-flow for the visitor's conversation,
 * overriding the LLM trigger router — the SDK pairing for the T92
 * `POST /chat/flow-select` endpoint. Use it for deep links
 * (`?flow=in-company` lands directly on the B2B persona) or for UI that
 * lets the visitor pick a track before the conversation starts.
 *
 * ## Key Features
 * - `chooseFlow(flow?)`: pin a flow by UUID or case-insensitive name —
 *   resolves with `{success, pinnedFlowId, pinnedFlowName, reason}`
 * - `status`: `"idle" | "selecting" | "success" | "error"`
 * - `error`: message from the last failed (or unsuccessful) pin
 * - `pinnedFlowId` / `pinnedFlowName`: the flow the engine locked onto
 * - `autoSelectFromParam`: opt-in deep-link auto-pin from a URL query param
 *   (e.g. `"flow"` → reads `?flow=in-company`, pins once on mount)
 * - `mintSession` (default `true`): mints `TUR_SESSION` proactively so the
 *   pin works on a deep-link landing, before the visitor types anything —
 *   the chat hook later reuses the same cookie
 *
 * ## Usage
 * ```tsx
 * // Deep-link auto-pin — URL: https://site/?flow=in-company
 * useTuringFlowChooser({ autoSelectFromParam: "flow" });
 *
 * // Explicit picker
 * const { chooseFlow, status } = useTuringFlowChooser();
 * <button onClick={() => chooseFlow("in-company")} disabled={status === "selecting"}>
 *   Falar com Vendas (B2B)
 * </button>
 * ```
 *
 * ## When to use
 * - **Deep links**: a campaign URL pre-selects the persona so the visitor
 *   never sees the wrong opening line
 * - **Track pickers**: a landing page offers "Talk to sales" vs "Browse
 *   courses" buttons; clicking pins the matching flow before chat opens
 * - **Override the router**: when specificity tie-breaks the wrong way, pin
 *   the intended flow explicitly
 *
 * Pairs with T93 cross-flow slot inheritance — slots already captured carry
 * into the pinned flow. This story uses in-memory state to simulate the
 * network round-trip.
 */

type Status = "idle" | "selecting" | "success" | "error";

interface FlowOption {
  readonly id: string;
  readonly name: string;
  readonly label: string;
  readonly icon: string;
  readonly persona: string;
}

const FLOWS: ReadonlyArray<FlowOption> = [
  {
    id: "1f0a-b2c",
    name: "lead-capture-b2c",
    label: "Cursos para mim",
    icon: "🎓",
    persona: "Marina — consultora de carreira (B2C)",
  },
  {
    id: "2f0b-b2b",
    name: "in-company",
    label: "Falar com Vendas",
    icon: "🏢",
    persona: "Account exec — proposta in-company (B2B)",
  },
  {
    id: "3f0c-edu",
    name: "micro-lesson",
    label: "Aula relâmpago",
    icon: "⚡",
    persona: "Tutor — micro-lição educacional",
  },
];

function FlowChooserDemo({
  failProbability,
  latencyMs,
}: {
  failProbability: number;
  latencyMs: number;
}) {
  const [status, setStatus] = useState<Status>("idle");
  const [error, setError] = useState<string | null>(null);
  const [pinnedFlowName, setPinnedFlowName] = useState<string | null>(null);
  const [pinnedFlowId, setPinnedFlowId] = useState<string | null>(null);
  const [pendingName, setPendingName] = useState<string | null>(null);

  async function chooseFlow(flow: string) {
    const match = FLOWS.find((f) => f.name === flow || f.id === flow);
    setStatus("selecting");
    setError(null);
    setPendingName(flow);
    await new Promise((resolve) => setTimeout(resolve, latencyMs));
    setPendingName(null);
    if (!match) {
      setError(`flow '${flow}' did not resolve`);
      setStatus("error");
      return;
    }
    if (Math.random() < failProbability) {
      setError("Server returned 500 (simulated)");
      setStatus("error");
      return;
    }
    setPinnedFlowId(match.id);
    setPinnedFlowName(match.name);
    setStatus("success");
  }

  const pinned = FLOWS.find((f) => f.name === pinnedFlowName);

  return (
    <div style={{ fontFamily: "system-ui, sans-serif", maxWidth: "560px" }}>
      <div
        style={{
          padding: "12px 14px",
          background: "#f1f5f9",
          borderRadius: "12px",
          fontSize: "14px",
          color: "#334155",
          marginBottom: "12px",
        }}
      >
        Como podemos ajudar? Escolha por onde começar — vamos te conectar com a
        pessoa certa.
      </div>

      {/* Track picker buttons */}
      <div
        style={{
          display: "flex",
          flexWrap: "wrap",
          gap: "8px",
          marginBottom: "16px",
        }}
      >
        {FLOWS.map((flow) => {
          const isPending = pendingName === flow.name;
          const isSelected = pinnedFlowName === flow.name;
          return (
            <button
              key={flow.id}
              type="button"
              disabled={status === "selecting"}
              onClick={() => void chooseFlow(flow.name)}
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: "6px",
                padding: "8px 14px",
                borderRadius: "999px",
                border: isSelected ? "2px solid #2563eb" : "1px solid #cbd5e1",
                background: isSelected ? "#eff6ff" : "white",
                color: "#0f172a",
                fontSize: "13px",
                cursor: status === "selecting" ? "wait" : "pointer",
                opacity: status === "selecting" && !isPending ? 0.5 : 1,
              }}
            >
              <span aria-hidden="true">{flow.icon}</span>
              {flow.label}
              {isPending && (
                <span
                  style={{
                    display: "inline-block",
                    width: "10px",
                    height: "10px",
                    borderRadius: "50%",
                    border: "2px solid #93c5fd",
                    borderTopColor: "#2563eb",
                    animation: "spin 0.8s linear infinite",
                  }}
                />
              )}
            </button>
          );
        })}
      </div>

      {/* Status row */}
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          padding: "10px 14px",
          background: "#f8fafc",
          borderRadius: "10px",
          fontSize: "12px",
          marginBottom: "12px",
        }}
      >
        <span>
          <strong>status:</strong>{" "}
          <code
            style={{
              padding: "2px 6px",
              background:
                status === "success"
                  ? "#dcfce7"
                  : status === "error"
                  ? "#fee2e2"
                  : status === "selecting"
                  ? "#fef3c7"
                  : "#e2e8f0",
              color:
                status === "success"
                  ? "#166534"
                  : status === "error"
                  ? "#991b1b"
                  : status === "selecting"
                  ? "#92400e"
                  : "#475569",
              borderRadius: "4px",
            }}
          >
            {status}
          </code>
        </span>
        <span>
          <strong>pinnedFlowId:</strong> <code>{pinnedFlowId ?? "—"}</code>
        </span>
      </div>

      {error && (
        <div
          style={{
            padding: "10px 14px",
            background: "#fef2f2",
            borderRadius: "8px",
            fontSize: "13px",
            color: "#991b1b",
            marginBottom: "12px",
          }}
        >
          ⚠ {error}
        </div>
      )}

      {/* Pinned persona card */}
      <div
        style={{
          border: "1px solid #e2e8f0",
          borderRadius: "10px",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            padding: "10px 14px",
            background: "#f8fafc",
            borderBottom: "1px solid #e2e8f0",
            fontSize: "12px",
            fontWeight: 600,
            color: "#64748b",
            textTransform: "uppercase",
            letterSpacing: "0.05em",
          }}
        >
          Pinned flow
        </div>
        {!pinned ? (
          <div
            style={{
              padding: "20px",
              textAlign: "center",
              color: "#94a3b8",
              fontSize: "13px",
            }}
          >
            Pick a track to pin a flow — the next chat turn runs that persona
          </div>
        ) : (
          <div style={{ padding: "12px 14px", fontSize: "13px" }}>
            <div style={{ marginBottom: "4px" }}>
              <code style={{ color: "#0f172a" }}>{pinned.name}</code>
            </div>
            <div style={{ color: "#475569" }}>
              {pinned.icon} {pinned.persona}
            </div>
          </div>
        )}
      </div>

      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}

const meta: Meta<typeof FlowChooserDemo> = {
  title: "Hooks/useTuringFlowChooser",
  component: FlowChooserDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Pins a specific chat-flow for the conversation, overriding the LLM trigger router — clicking a track button pins that flow through the SDK before chat starts. This story simulates the network round-trip with configurable latency and failure rate.",
      },
    },
  },
  argTypes: {
    failProbability: {
      description: "Probability the simulated POST fails (0 = never, 1 = always).",
      control: { type: "range", min: 0, max: 1, step: 0.05 },
    },
    latencyMs: {
      description: "Simulated network latency, in milliseconds.",
      control: { type: "range", min: 0, max: 2000, step: 100 },
    },
  },
};

export default meta;
type Story = StoryObj<typeof FlowChooserDemo>;

export const Default: Story = {
  args: {
    failProbability: 0,
    latencyMs: 400,
  },
};

export const HighLatency: Story = {
  name: "High latency (1.5s)",
  args: {
    failProbability: 0,
    latencyMs: 1500,
  },
};

export const ErrorPath: Story = {
  name: "Error path (50% failure)",
  args: {
    failProbability: 0.5,
    latencyMs: 400,
  },
};
