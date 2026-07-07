import type { Meta, StoryObj } from "@storybook/react-vite";
import { useMemo, useState } from "react";
import type { TurLlmInstance } from "../../core/types";

/**
 * # useTuringLlmInstances
 *
 * Lists the LLM model instances configured on the Turing platform and tracks
 * the picked one. This is the hook you reach for when building a **model
 * picker** — the dropdown / card grid that lets a user choose which model
 * (OpenAI GPT-4o, Anthropic Claude, Google Gemini, a local Ollama model, an
 * Azure deployment, …) drives a chat or RAG session.
 *
 * Under the hood it wraps `GET /api/v2/llm` (the instance listing) plus a
 * lazy context-window probe (`GET /api/v2/llm/{id}/chat/context-info`, or the
 * agent-scoped variant when `agentId` is set), then layers selection state on
 * top so the host component only deals with `selectedInstance` / `select(id)`.
 *
 * ## Key Features
 * - `instances`: the available `TurLlmInstance[]`, already filtered to the
 *   usable ones (`enabled === 1`) when `filterDisabled` is left on.
 * - `selectedId` / `selectedInstance` / `select(id)`: controlled selection,
 *   with the first enabled instance auto-picked on load.
 * - `contextWindow`: the effective token budget for the picked model —
 *   provider probe → admin-configured value → `defaultContextWindow` fallback.
 * - `persistKey`: optional `localStorage` key so the choice survives reloads.
 * - `loaded` / `error`: state flags for skeleton + error rendering.
 *
 * ## Usage
 * ```tsx
 * const { instances, selectedInstance, select, contextWindow, loaded } =
 *   useTuringLlmInstances({ persistKey: "turing.model", filterDisabled: true });
 *
 * if (!loaded) return <Spinner />;
 *
 * return (
 *   <div>
 *     {instances.map((llm) => (
 *       <button
 *         key={llm.id}
 *         onClick={() => select(llm.id)}
 *         data-active={llm.id === selectedInstance?.id}
 *       >
 *         {llm.title} · {llm.turLLMVendor?.title}
 *       </button>
 *     ))}
 *     <p>Context window: {contextWindow.toLocaleString()} tokens</p>
 *   </div>
 * );
 * ```
 *
 * ## When to use
 * - **Model picker UI**: a dropdown or card grid in a chat composer or an
 *   admin "default model" setting.
 * - **Capability hints**: show the context window so a user knows how much they
 *   can paste before truncation.
 * - **Multi-vendor deployments**: surface which providers an org has wired up,
 *   with per-vendor badges so the choice is legible at a glance.
 *
 * ## About this story
 * Fully self-contained — there is **no** real hook, Provider, or network call.
 * A small inline array of mock `TurLlmInstance` objects (one per vendor) feeds
 * a polished picker; clicking a card calls a local `select()` that mirrors the
 * real hook's `selectedId` / `selectedInstance` / `contextWindow` contract.
 * Loading and empty states are shown as separate stories.
 */

// ── Vendor accent palette ──────────────────────────────────────────────
// Each provider gets a distinct accent so the picker reads at a glance.
// The platform's own gradient (#2563eb → #4f46e5) is reserved for the
// currently-selected card's ring + header.
interface VendorTheme {
  readonly label: string;
  readonly accent: string;
  readonly glyph: string;
}

const VENDOR_THEMES: Record<string, VendorTheme> = {
  openai: { label: "OpenAI", accent: "#10a37f", glyph: "✦" },
  anthropic: { label: "Anthropic", accent: "#d97757", glyph: "✳" },
  gemini: { label: "Google Gemini", accent: "#1a73e8", glyph: "✺" },
  ollama: { label: "Ollama", accent: "#64748b", glyph: "◍" },
  azure: { label: "Azure OpenAI", accent: "#0078d4", glyph: "▰" },
};

function vendorTheme(plugin: string | undefined): VendorTheme {
  return (plugin && VENDOR_THEMES[plugin]) || { label: "Custom", accent: "#7c3aed", glyph: "◆" };
}

// ── Mock instances (varied vendors) ────────────────────────────────────
const MOCK_INSTANCES: ReadonlyArray<TurLlmInstance> = [
  {
    id: "llm-gpt4o",
    title: "GPT-4o",
    description: "Flagship multimodal model — fast, broadly capable.",
    enabled: 1,
    contextWindow: 128000,
    modelName: "gpt-4o",
    turLLMVendor: { id: "v-openai", title: "OpenAI", plugin: "openai" },
  },
  {
    id: "llm-claude-sonnet",
    title: "Claude Opus 4.8",
    description: "Strong reasoning + long-context analysis.",
    enabled: 1,
    contextWindow: 200000,
    modelName: "claude-opus-4-8",
    turLLMVendor: { id: "v-anthropic", title: "Anthropic", plugin: "anthropic" },
  },
  {
    id: "llm-gemini-pro",
    title: "Gemini 2.5 Pro",
    description: "Million-token context, native tool use.",
    enabled: 1,
    contextWindow: 1000000,
    modelName: "gemini-2.5-pro",
    turLLMVendor: { id: "v-gemini", title: "Google Gemini", plugin: "gemini" },
  },
  {
    id: "llm-llama-local",
    title: "Llama 3.1 8B (local)",
    description: "Self-hosted via Ollama — no data leaves the box.",
    enabled: 1,
    contextWindow: 32000,
    modelName: "llama3.1:8b",
    turLLMVendor: { id: "v-ollama", title: "Ollama", plugin: "ollama" },
  },
  {
    id: "llm-azure-gpt4",
    title: "Azure GPT-4 Turbo",
    description: "Enterprise deployment in your own tenant.",
    enabled: 1,
    contextWindow: 128000,
    modelName: "gpt-4-turbo",
    turLLMVendor: { id: "v-azure", title: "Azure OpenAI", plugin: "azure" },
  },
];

const DEFAULT_CONTEXT_WINDOW = 128000;

function formatTokens(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(n % 1_000_000 === 0 ? 0 : 1)}M`;
  if (n >= 1000) return `${Math.round(n / 1000)}K`;
  return String(n);
}

// ── Picker demo ────────────────────────────────────────────────────────
function LlmInstancePicker({
  instances,
  loaded,
  dark,
}: {
  instances: ReadonlyArray<TurLlmInstance>;
  loaded: boolean;
  dark: boolean;
}) {
  // Mirror the real hook: auto-pick the first enabled instance once loaded.
  const usable = useMemo(() => instances.filter((i) => i.enabled === 1), [instances]);
  const [selectedId, setSelectedId] = useState<string>(usable[0]?.id ?? "");

  const selectedInstance = usable.find((i) => i.id === selectedId);
  const contextWindow =
    selectedInstance?.contextWindow && selectedInstance.contextWindow > 0
      ? selectedInstance.contextWindow
      : DEFAULT_CONTEXT_WINDOW;

  const palette = dark
    ? {
        bg: "#0a0a0f",
        card: "#16161f",
        cardBorder: "#1e1e2e",
        title: "#f1f5f9",
        muted: "#94a3b8",
        chip: "#1e1e2e",
      }
    : {
        bg: "#ffffff",
        card: "#ffffff",
        cardBorder: "#e2e8f0",
        title: "#0f172a",
        muted: "#64748b",
        chip: "#f1f5f9",
      };

  return (
    <div
      style={{
        fontFamily: "system-ui, sans-serif",
        width: "560px",
        maxWidth: "100%",
        background: palette.bg,
        padding: "20px",
        borderRadius: "16px",
      }}
    >
      {/* Header */}
      <div style={{ marginBottom: "16px" }}>
        <h3
          style={{
            margin: 0,
            fontSize: "16px",
            fontWeight: 700,
            color: palette.title,
            display: "flex",
            alignItems: "center",
            gap: "8px",
          }}
        >
          <span
            style={{
              display: "inline-flex",
              width: 26,
              height: 26,
              borderRadius: "8px",
              alignItems: "center",
              justifyContent: "center",
              background: "linear-gradient(135deg, #2563eb, #4f46e5)",
              color: "white",
              fontSize: "14px",
            }}
          >
            🤖
          </span>
          Choose a model
        </h3>
        <p style={{ margin: "4px 0 0", fontSize: "12px", color: palette.muted }}>
          {loaded
            ? `${usable.length} model${usable.length === 1 ? "" : "s"} available across your providers`
            : "Loading available models…"}
        </p>
      </div>

      {/* Loading skeleton */}
      {!loaded && (
        <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              style={{
                height: "62px",
                borderRadius: "12px",
                border: `1px solid ${palette.cardBorder}`,
                background: dark
                  ? "linear-gradient(90deg, #16161f, #1e1e2e, #16161f)"
                  : "linear-gradient(90deg, #f8fafc, #eef2ff, #f8fafc)",
                backgroundSize: "200% 100%",
                animation: "shimmer 1.4s ease-in-out infinite",
              }}
            />
          ))}
        </div>
      )}

      {/* Empty state */}
      {loaded && usable.length === 0 && (
        <div
          style={{
            padding: "32px 20px",
            textAlign: "center",
            border: `1px dashed ${palette.cardBorder}`,
            borderRadius: "12px",
            color: palette.muted,
          }}
        >
          <div style={{ fontSize: "28px", marginBottom: "8px" }}>🧩</div>
          <div style={{ fontSize: "14px", fontWeight: 600, color: palette.title }}>
            No models configured yet
          </div>
          <div style={{ fontSize: "12px", marginTop: "4px" }}>
            Add an LLM instance in the Turing admin console to enable chat.
          </div>
        </div>
      )}

      {/* Model cards */}
      {loaded && usable.length > 0 && (
        <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
          {usable.map((llm) => {
            const theme = vendorTheme(llm.turLLMVendor?.plugin);
            const active = llm.id === selectedId;
            return (
              <button
                key={llm.id}
                type="button"
                onClick={() => setSelectedId(llm.id)}
                style={{
                  textAlign: "left",
                  display: "flex",
                  alignItems: "center",
                  gap: "12px",
                  padding: "12px 14px",
                  borderRadius: "12px",
                  cursor: "pointer",
                  background: palette.card,
                  border: active
                    ? "2px solid transparent"
                    : `1px solid ${palette.cardBorder}`,
                  backgroundImage: active
                    ? `linear-gradient(${palette.card}, ${palette.card}), linear-gradient(135deg, #2563eb, #4f46e5)`
                    : undefined,
                  backgroundOrigin: active ? "border-box" : undefined,
                  backgroundClip: active ? "padding-box, border-box" : undefined,
                  boxShadow: active ? "0 4px 18px rgba(79, 70, 229, 0.22)" : "none",
                  transition: "box-shadow 120ms ease, transform 120ms ease",
                }}
              >
                {/* Vendor glyph */}
                <span
                  style={{
                    flexShrink: 0,
                    width: 38,
                    height: 38,
                    borderRadius: "10px",
                    display: "inline-flex",
                    alignItems: "center",
                    justifyContent: "center",
                    fontSize: "18px",
                    color: "white",
                    background: theme.accent,
                  }}
                  aria-hidden
                >
                  {theme.glyph}
                </span>

                {/* Title + meta */}
                <span style={{ flex: 1, minWidth: 0 }}>
                  <span
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "8px",
                      marginBottom: "2px",
                    }}
                  >
                    <span style={{ fontSize: "14px", fontWeight: 600, color: palette.title }}>
                      {llm.title}
                    </span>
                    {/* Vendor badge */}
                    <span
                      style={{
                        fontSize: "10px",
                        fontWeight: 600,
                        padding: "2px 8px",
                        borderRadius: "999px",
                        color: theme.accent,
                        background: `${theme.accent}1f`,
                        textTransform: "uppercase",
                        letterSpacing: "0.04em",
                      }}
                    >
                      {theme.label}
                    </span>
                  </span>
                  <span
                    style={{
                      display: "block",
                      fontSize: "12px",
                      color: palette.muted,
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                      whiteSpace: "nowrap",
                    }}
                  >
                    {llm.description}
                  </span>
                  <span
                    style={{
                      display: "inline-block",
                      marginTop: "4px",
                      fontFamily: "monospace",
                      fontSize: "10px",
                      color: palette.muted,
                    }}
                  >
                    {llm.modelName} · {formatTokens(llm.contextWindow ?? 0)} ctx
                  </span>
                </span>

                {/* Selected check */}
                <span
                  style={{
                    flexShrink: 0,
                    width: 22,
                    height: 22,
                    borderRadius: "50%",
                    display: "inline-flex",
                    alignItems: "center",
                    justifyContent: "center",
                    fontSize: "12px",
                    color: "white",
                    background: active
                      ? "linear-gradient(135deg, #2563eb, #4f46e5)"
                      : "transparent",
                    border: active ? "none" : `1.5px solid ${palette.cardBorder}`,
                  }}
                  aria-hidden
                >
                  {active ? "✓" : ""}
                </span>
              </button>
            );
          })}
        </div>
      )}

      {/* Selection summary — mirrors the hook's selectedInstance / contextWindow */}
      {loaded && selectedInstance && (
        <div
          style={{
            marginTop: "16px",
            padding: "12px 14px",
            borderRadius: "10px",
            background: palette.chip,
            fontSize: "12px",
            color: palette.muted,
          }}
        >
          <div>
            <strong style={{ color: palette.title }}>selectedId:</strong>{" "}
            <code>{selectedInstance.id}</code>
          </div>
          <div style={{ marginTop: "2px" }}>
            <strong style={{ color: palette.title }}>contextWindow:</strong>{" "}
            <code>{contextWindow.toLocaleString()}</code> tokens
          </div>
        </div>
      )}

      <style>{`
        @keyframes shimmer {
          0% { background-position: 200% 0; }
          100% { background-position: -200% 0; }
        }
      `}</style>
    </div>
  );
}

const meta: Meta<typeof LlmInstancePicker> = {
  title: "Platform/useTuringLlmInstances",
  component: LlmInstancePicker,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Self-contained model-picker demo for `useTuringLlmInstances`. A mock array of LLM instances (OpenAI, Anthropic, Gemini, Ollama, Azure) feeds a card grid with per-vendor accent badges; clicking a card selects it and updates the `selectedId` / `contextWindow` summary — the same contract the real hook exposes. Loading and empty states are separate stories.",
      },
    },
  },
  argTypes: {
    loaded: {
      description: "When false, render the loading skeleton instead of the cards.",
      control: { type: "boolean" },
    },
    dark: {
      description: "Render against the Viglet dark palette (#0a0a0f background).",
      control: { type: "boolean" },
    },
    instances: {
      description: "Mock TurLlmInstance[] feeding the picker.",
      control: false,
    },
  },
};

export default meta;
type Story = StoryObj<typeof LlmInstancePicker>;

export const ModelPicker: Story = {
  name: "🤖 Model picker (multi-vendor)",
  args: {
    instances: MOCK_INSTANCES,
    loaded: true,
    dark: false,
  },
};

export const DarkPicker: Story = {
  name: "🌙 Model picker (dark)",
  args: {
    instances: MOCK_INSTANCES,
    loaded: true,
    dark: true,
  },
};

export const Loading: Story = {
  name: "⏳ Loading skeleton",
  args: {
    instances: MOCK_INSTANCES,
    loaded: false,
    dark: false,
  },
};

export const Empty: Story = {
  name: "🧩 Empty (no models configured)",
  args: {
    instances: [],
    loaded: true,
    dark: false,
  },
};
