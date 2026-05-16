import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";

/**
 * # useTuringSlotWriter
 *
 * Imperatively writes a slot on the current chat conversation — the inverse
 * of `useTuringSlots`. Use to skip a chat-flow question when the user picks
 * an option via UI (button, dropdown, autocomplete) instead of typing it
 * into the chat: the next assistant turn sees the slot already filled and
 * the flow advances past the matching `slot` node.
 *
 * ## Key Features
 * - `write(name, value)`: write a single slot — resolves with `{updatedStates}`
 * - `status`: `"idle" | "writing" | "success" | "error"`
 * - `error`: human-readable message from the last failed write
 * - `lastUpdatedStates`: number of chat-flow state rows touched by the last write
 * - Reads `TUR_SESSION` cookie automatically (same conversation as the chat hook)
 * - Returns `updatedStates: 0` when no chat-flow state exists yet — the
 *   caller should send a chat message first to materialize a state
 *
 * ## Usage
 * ```tsx
 * const { write, status } = useTuringSlotWriter();
 *
 * <button
 *   onClick={() => write("area_interesse", "Liderança")}
 *   disabled={status === "writing"}
 * >
 *   Liderança e Gestão
 * </button>
 * ```
 *
 * ## When to use
 * Pair with `useTuringSlots` for two-way binding: read slot values through
 * the polling hook, write them imperatively from button handlers. Most
 * useful when:
 *
 * - **Quick-picks**: chat asks "which area?" and the page offers buttons
 *   for each area; clicking writes the slot directly so the flow skips
 *   the question on the next turn
 * - **Pre-fill from URL**: a deep link sets a slot before the visitor
 *   types anything (`?intent=mba` → `write("interest", "mba")` on mount)
 * - **Skip a step**: a form on the page already collected the slot value;
 *   write it before opening the chat so the flow starts further along
 *
 * This story uses in-memory state to simulate the network round-trip.
 */

type Status = "idle" | "writing" | "success" | "error";

interface SlotRow {
  readonly name: string;
  readonly value: string;
}

function SlotWriterDemo({
  failProbability,
  latencyMs,
}: {
  failProbability: number;
  latencyMs: number;
}) {
  const [slots, setSlots] = useState<SlotRow[]>([]);
  const [status, setStatus] = useState<Status>("idle");
  const [error, setError] = useState<string | null>(null);
  const [lastUpdatedStates, setLastUpdatedStates] = useState(0);
  const [pendingArea, setPendingArea] = useState<string | null>(null);

  async function write(name: string, value: string) {
    setStatus("writing");
    setError(null);
    setPendingArea(value);
    await new Promise((resolve) => setTimeout(resolve, latencyMs));
    if (Math.random() < failProbability) {
      const msg = "Server returned 500 (simulated)";
      setError(msg);
      setStatus("error");
      setPendingArea(null);
      return { updatedStates: 0 };
    }
    setSlots((prev) => {
      const without = prev.filter((s) => s.name !== name);
      return [...without, { name, value }];
    });
    setLastUpdatedStates(1);
    setStatus("success");
    setPendingArea(null);
    return { updatedStates: 1 };
  }

  const AREAS: ReadonlyArray<{ label: string; icon: string; slotValue: string }> = [
    { label: "Finanças", icon: "💰", slotValue: "Finanças & Investimentos" },
    { label: "Liderança", icon: "👥", slotValue: "Liderança & Gestão" },
    { label: "Saúde", icon: "🏥", slotValue: "Saúde" },
    { label: "Tecnologia", icon: "💻", slotValue: "Tecnologia & Dados" },
  ];

  return (
    <div style={{ fontFamily: "system-ui, sans-serif", maxWidth: "560px" }}>
      {/* Simulated chat bubble */}
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
        <strong style={{ color: "#0f172a" }}>Marina:</strong> Em qual dessas áreas
        você quer aprofundar primeiro?
      </div>

      {/* Quick-pick buttons */}
      <div
        style={{
          display: "flex",
          flexWrap: "wrap",
          gap: "8px",
          marginBottom: "16px",
        }}
      >
        {AREAS.map((area) => {
          const isPending = pendingArea === area.slotValue;
          const isSelected = slots.find((s) => s.name === "area")?.value === area.slotValue;
          return (
            <button
              key={area.label}
              type="button"
              disabled={status === "writing"}
              onClick={() => {
                void write("area", area.slotValue);
                void write("area_label", area.label);
              }}
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
                cursor: status === "writing" ? "wait" : "pointer",
                opacity: status === "writing" && !isPending ? 0.5 : 1,
              }}
            >
              <span aria-hidden="true">{area.icon}</span>
              {area.label}
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
                  : status === "writing"
                  ? "#fef3c7"
                  : "#e2e8f0",
              color:
                status === "success"
                  ? "#166534"
                  : status === "error"
                  ? "#991b1b"
                  : status === "writing"
                  ? "#92400e"
                  : "#475569",
              borderRadius: "4px",
            }}
          >
            {status}
          </code>
        </span>
        <span>
          <strong>updatedStates:</strong> <code>{lastUpdatedStates}</code>
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

      {/* Slots table */}
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
          Slots written ({slots.length})
        </div>
        {slots.length === 0 ? (
          <div
            style={{
              padding: "20px",
              textAlign: "center",
              color: "#94a3b8",
              fontSize: "13px",
            }}
          >
            Click an area button to write a slot
          </div>
        ) : (
          slots.map((s) => (
            <div
              key={s.name}
              style={{
                display: "flex",
                justifyContent: "space-between",
                padding: "10px 14px",
                borderBottom: "1px solid #f1f5f9",
                fontSize: "13px",
              }}
            >
              <code style={{ color: "#0f172a" }}>{s.name}</code>
              <span style={{ color: "#475569" }}>{s.value}</span>
            </div>
          ))
        )}
      </div>

      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}

const meta: Meta<typeof SlotWriterDemo> = {
  title: "Hooks/useTuringSlotWriter",
  component: SlotWriterDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Imperatively writes chat-flow slots — clicking a quick-pick button writes the slot through the SDK without sending a chat message. This story simulates the network round-trip with configurable latency and failure rate.",
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
type Story = StoryObj<typeof SlotWriterDemo>;

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
