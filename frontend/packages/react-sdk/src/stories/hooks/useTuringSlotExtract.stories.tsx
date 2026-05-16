import type { Meta, StoryObj } from "@storybook/react-vite";
import { useState } from "react";

/**
 * # useTuringSlotExtract
 *
 * Imperatively uploads a document (PDF / DOCX / TXT / RTF / HTML — anything
 * Tika auto-detects) and the server runs the agent's LLM in
 * structured-output mode to map the extracted text onto chat-flow slot
 * names. Pairs with `useTuringSlots`: the fields land as actual slot
 * writes, so SSE subscribers reflect them in &lt;100 ms.
 *
 * ## Key Features
 * - `extract(file)`: uploads multipart, returns `{ extracted, slotsWritten, extractedTextChars, error }`
 * - `status`: `"idle" | "uploading" | "success" | "error"`
 * - `error`: human-readable message (both transport errors and server-side
 *   soft errors like "No AI agent configured" surface here)
 * - `lastResult`: last successful extraction, kept for the UI to show "you
 *   uploaded `foo.pdf` and we extracted N fields"
 * - Reads `TUR_SESSION` cookie automatically (same conversation as
 *   `useTuringChat`)
 * - `slotNames` option scopes the LLM to a tight subset of the agent's
 *   slot catalog — cuts cost + latency for known use cases (CV → 4
 *   identity slots only)
 *
 * ## Usage
 * ```tsx
 * const { extract, status, error, lastResult } = useTuringSlotExtract({
 *   slotNames: ["name", "cargo_atual", "objetivo", "area"],
 * });
 *
 * <input
 *   type="file"
 *   accept=".pdf,.docx,.txt"
 *   onChange={async (e) => {
 *     const file = e.target.files?.[0];
 *     if (!file) return;
 *     const result = await extract(file);
 *     console.log("Skipped %d questions", result.slotsWritten);
 *   }}
 * />
 * ```
 *
 * ## When to use
 *
 * - **CV upload** in a career-flow chat: skip 3-5 manual questions
 *   (name / current role / experience) by extracting from the resume
 * - **Invoice intake**: extract supplier, amount, due date from a PDF
 *   the user dropped on the page
 * - **Form pre-fill** from any structured doc: contract / NDA / agreement
 *   PDF → slot values for the next form step
 *
 * The story simulates the upload round-trip with configurable latency
 * and failure rate. It also models a "soft error" path (response 200
 * with `error` field set) so you can test both transport failures and
 * agent-configuration issues.
 */

type Status = "idle" | "uploading" | "success" | "error";

interface SimulatedExtractionResult {
  readonly extracted: Record<string, string>;
  readonly slotsWritten: number;
  readonly extractedTextChars: number;
  readonly error: string | null;
}

const CV_PROFILES: ReadonlyArray<{
  readonly filename: string;
  readonly slots: Record<string, string>;
  readonly chars: number;
}> = [
  {
    filename: "alexandre-oliveira-cv.pdf",
    slots: {
      name: "Alexandre Oliveira",
      cargo_atual: "Gerente sênior numa fintech, há 4 anos",
      objetivo: "Virar CFO de uma scale-up em até 3 anos",
      area: "Finanças & Investimentos",
    },
    chars: 4_812,
  },
  {
    filename: "maria-souza-resume.docx",
    slots: {
      name: "Maria Souza",
      cargo_atual: "Diretora de Marketing, +8 anos em FMCG",
      objetivo: "Liderar transformação digital de uma empresa nacional",
      area: "Liderança & Gestão",
    },
    chars: 6_320,
  },
  {
    filename: "lucas-pereira.pdf",
    slots: {
      name: "Lucas Pereira",
      cargo_atual: "Data Scientist Pleno em healthtech",
      objetivo: "Liderar área de IA aplicada à saúde",
      area: "Tecnologia & Dados",
    },
    chars: 5_140,
  },
];

function SlotExtractDemo({
  failProbability,
  softErrorProbability,
  latencyMs,
}: {
  failProbability: number;
  softErrorProbability: number;
  latencyMs: number;
}) {
  const [status, setStatus] = useState<Status>("idle");
  const [error, setError] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<SimulatedExtractionResult | null>(null);
  const [selectedProfile, setSelectedProfile] = useState(0);

  async function extract(profileIdx: number): Promise<SimulatedExtractionResult> {
    setStatus("uploading");
    setError(null);
    setLastResult(null);
    await new Promise((resolve) => setTimeout(resolve, latencyMs));

    if (Math.random() < failProbability) {
      const msg = "Network error: upload timed out (simulated)";
      setError(msg);
      setStatus("error");
      return { extracted: {}, slotsWritten: 0, extractedTextChars: 0, error: msg };
    }
    if (Math.random() < softErrorProbability) {
      const msg = "No AI agent configured for site 'demo-site'";
      setError(msg);
      setStatus("error");
      return { extracted: {}, slotsWritten: 0, extractedTextChars: 0, error: msg };
    }
    const profile = CV_PROFILES[profileIdx];
    const result: SimulatedExtractionResult = {
      extracted: profile.slots,
      slotsWritten: Object.keys(profile.slots).length,
      extractedTextChars: profile.chars,
      error: null,
    };
    setLastResult(result);
    setStatus("success");
    return result;
  }

  return (
    <div style={{ fontFamily: "system-ui, sans-serif", maxWidth: "560px" }}>
      {/* Profile selector — pretend file picker */}
      <div
        style={{
          padding: "14px",
          border: "2px dashed #cbd5e1",
          borderRadius: "12px",
          marginBottom: "14px",
          background: status === "uploading" ? "#f1f5f9" : "white",
          textAlign: "center",
        }}
      >
        <div style={{ fontSize: "13px", color: "#475569", marginBottom: "10px" }}>
          📎 Escolha um CV simulado para extrair:
        </div>
        <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
          {CV_PROFILES.map((p, i) => (
            <label
              key={p.filename}
              style={{
                display: "flex",
                alignItems: "center",
                gap: "8px",
                padding: "8px 12px",
                background: selectedProfile === i ? "#eff6ff" : "transparent",
                border: selectedProfile === i ? "1px solid #2563eb" : "1px solid #e2e8f0",
                borderRadius: "8px",
                fontSize: "13px",
                cursor: "pointer",
              }}
            >
              <input
                type="radio"
                name="cv"
                checked={selectedProfile === i}
                onChange={() => setSelectedProfile(i)}
                style={{ margin: 0 }}
              />
              <span style={{ fontFamily: "monospace", color: "#0f172a" }}>
                {p.filename}
              </span>
            </label>
          ))}
        </div>
        <button
          type="button"
          onClick={() => {
            void extract(selectedProfile);
          }}
          disabled={status === "uploading"}
          style={{
            marginTop: "12px",
            padding: "10px 18px",
            borderRadius: "8px",
            border: "none",
            background: status === "uploading" ? "#94a3b8" : "#2563eb",
            color: "white",
            fontSize: "13px",
            cursor: status === "uploading" ? "wait" : "pointer",
          }}
        >
          {status === "uploading" ? "Extraindo..." : "Extrair slots do CV"}
        </button>
      </div>

      {/* Status row */}
      <div
        style={{
          display: "flex",
          gap: "10px",
          alignItems: "center",
          padding: "8px 14px",
          background: "#f8fafc",
          borderRadius: "8px",
          fontSize: "12px",
          marginBottom: "10px",
        }}
      >
        <span>
          <strong>status:</strong>{" "}
          <code
            style={{
              padding: "2px 6px",
              borderRadius: "4px",
              background:
                status === "success" ? "#dcfce7"
                : status === "error" ? "#fee2e2"
                : status === "uploading" ? "#fef3c7"
                : "#e2e8f0",
              color:
                status === "success" ? "#166534"
                : status === "error" ? "#991b1b"
                : status === "uploading" ? "#92400e"
                : "#475569",
            }}
          >
            {status}
          </code>
        </span>
        {lastResult && (
          <>
            <span><strong>slotsWritten:</strong> <code>{lastResult.slotsWritten}</code></span>
            <span><strong>extractedTextChars:</strong> <code>{lastResult.extractedTextChars}</code></span>
          </>
        )}
      </div>

      {error && (
        <div
          style={{
            padding: "10px 14px",
            background: "#fef2f2",
            borderRadius: "8px",
            fontSize: "13px",
            color: "#991b1b",
            marginBottom: "10px",
          }}
        >
          ⚠ {error}
        </div>
      )}

      {lastResult && lastResult.slotsWritten > 0 && (
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
              background: "#f0fdf4",
              borderBottom: "1px solid #bbf7d0",
              fontSize: "12px",
              fontWeight: 600,
              color: "#166534",
              textTransform: "uppercase",
              letterSpacing: "0.05em",
            }}
          >
            ✓ Extracted slots ({lastResult.slotsWritten})
          </div>
          {Object.entries(lastResult.extracted).map(([name, value]) => (
            <div
              key={name}
              style={{
                display: "flex",
                justifyContent: "space-between",
                padding: "10px 14px",
                borderBottom: "1px solid #f1f5f9",
                fontSize: "13px",
              }}
            >
              <code style={{ color: "#0f172a" }}>{name}</code>
              <span style={{ color: "#475569", textAlign: "right", maxWidth: "70%" }}>
                {value}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

const meta: Meta<typeof SlotExtractDemo> = {
  title: "Hooks/useTuringSlotExtract",
  component: SlotExtractDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Document-to-slot extraction via Tika + LLM structured output. The visitor uploads a CV / invoice / form and the agent fills slot values automatically — typically replaces 3-5 manual chat-flow questions. This story simulates the upload round-trip and lets you toggle between three CV profiles.",
      },
    },
  },
  argTypes: {
    failProbability: {
      description: "Probability the simulated upload throws a transport error.",
      control: { type: "range", min: 0, max: 1, step: 0.05 },
    },
    softErrorProbability: {
      description:
        "Probability the server responds 200 but with `error` field set (e.g. agent not configured).",
      control: { type: "range", min: 0, max: 1, step: 0.05 },
    },
    latencyMs: {
      description:
        "Simulated total round-trip — covers upload + Tika parse + LLM call.",
      control: { type: "range", min: 0, max: 8000, step: 250 },
    },
  },
};

export default meta;
type Story = StoryObj<typeof SlotExtractDemo>;

export const Default: Story = {
  name: "Happy path (~2s extraction)",
  args: {
    failProbability: 0,
    softErrorProbability: 0,
    latencyMs: 2000,
  },
};

export const SlowLLM: Story = {
  name: "Slow LLM (~5s — gpt-4o cold start)",
  args: {
    failProbability: 0,
    softErrorProbability: 0,
    latencyMs: 5000,
  },
};

export const TransportError: Story = {
  name: "Transport error (50% failure)",
  args: {
    failProbability: 0.5,
    softErrorProbability: 0,
    latencyMs: 1500,
  },
};

export const AgentNotConfigured: Story = {
  name: "Soft error (agent not configured)",
  args: {
    failProbability: 0,
    softErrorProbability: 1,
    latencyMs: 1500,
  },
};
