import type { Meta, StoryObj } from "@storybook/react-vite";

/**
 * # useTuringExperiment
 *
 * Thin convenience wrapper on top of `useTuringFlowState` that exposes
 * only the A/B experiment fields. Use when an app wants variant-aware
 * UI without dragging the whole flow-state shape through props.
 *
 * ## Key Features
 * - `experimentKey`: id the conversation is in, or `null` outside of A/B
 * - `variantLabel`: variant assigned (`"control"`, `"marina-consultora"`,
 *   `"lucas-alumni"`, …), or `null` outside of A/B
 * - `isLoading`: same as `useTuringFlowState`'s loading flag
 * - `refresh()`: force re-read — rarely needed since variant assignment
 *   is sticky for the conversation's lifetime
 *
 * ## Usage
 * ```tsx
 * const { variantLabel } = useTuringExperiment();
 * return variantLabel === "lucas-alumni"
 *   ? <PeerToneBanner />
 *   : <ConsultantToneBanner />;
 * ```
 *
 * ## When to use
 *
 * - **Variant-aware copy**: hero text, microcopy, CTA labels that should
 *   change per arm
 * - **Variant-aware design**: different layout for the Lucas peer voice
 *   (more casual / first-person UI) vs Marina executive voice
 * - **App-level analytics**: send the variant tag to your own analytics
 *   when the visitor hits a key page event (so non-Executive-Education analytics
 *   can cross-reference the A/B group)
 *
 * ## About this story
 *
 * The story renders 3 example UIs that branch on the variant. Toggle
 * the `variantLabel` control to flip between them in real time —
 * mirrors what the consuming app would do when the engine reassigns
 * a conversation mid-experiment (rare, but supported via `refresh()`).
 */

interface SimulatedExperiment {
  readonly experimentKey: string | null;
  readonly variantLabel: string | null;
  readonly isLoading: boolean;
}

function ExperimentDemo({
  experimentKey,
  variantLabel,
  isLoading,
}: {
  experimentKey: string;
  variantLabel: string;
  isLoading: boolean;
}) {
  const state: SimulatedExperiment = {
    experimentKey: experimentKey === "(none)" ? null : experimentKey,
    variantLabel: variantLabel === "(none)" ? null : variantLabel,
    isLoading,
  };

  return (
    <div style={{ fontFamily: "system-ui, sans-serif", maxWidth: "620px" }}>
      {/* State snapshot */}
      <div style={{
        padding: "12px 14px",
        background: "#f8fafc",
        border: "1px solid #e2e8f0",
        borderRadius: "10px",
        fontFamily: "monospace",
        fontSize: "12px",
        marginBottom: "14px",
        display: "flex",
        gap: "16px",
        flexWrap: "wrap",
      }}>
        <span><strong>experimentKey:</strong>{" "}
          <code style={{ color: state.experimentKey ? "#0f172a" : "#94a3b8" }}>
            {state.experimentKey ?? "null"}
          </code>
        </span>
        <span><strong>variantLabel:</strong>{" "}
          <code style={{
            padding: "1px 6px",
            borderRadius: 4,
            background: variantBackground(state.variantLabel),
            color: variantForeground(state.variantLabel),
            fontWeight: 600,
          }}>
            {state.variantLabel ?? "null"}
          </code>
        </span>
        <span><strong>isLoading:</strong> <code>{String(state.isLoading)}</code></span>
      </div>

      {/* Variant-branched UI demo */}
      {isLoading ? (
        <div style={{
          padding: "24px",
          textAlign: "center",
          color: "#64748b",
          fontSize: 14,
          background: "#f8fafc",
          borderRadius: 12,
        }}>
          Resolving variant…
        </div>
      ) : state.variantLabel === "marina-consultora" ? (
        <MarinaBanner />
      ) : state.variantLabel === "lucas-alumni" ? (
        <LucasBanner />
      ) : state.variantLabel === "camila-account-exec" ? (
        <CamilaBanner />
      ) : (
        <DefaultBanner />
      )}

      <div style={{
        marginTop: 14,
        padding: "10px 14px",
        background: "#fffbeb",
        border: "1px solid #fde68a",
        borderRadius: 8,
        fontSize: 12,
        color: "#854d0e",
      }}>
        <strong>Tip:</strong> Use the <code>variantLabel</code> control
        in the side panel to flip between variants — mirrors what
        <code>refresh()</code> would do if the engine reassigned the
        conversation mid-experiment.
      </div>
    </div>
  );
}

function variantBackground(label: string | null): string {
  if (!label) return "#e2e8f0";
  if (label === "marina-consultora") return "#dbeafe";
  if (label === "lucas-alumni") return "#fef3c7";
  if (label === "camila-account-exec") return "#ede9fe";
  return "#e2e8f0";
}
function variantForeground(label: string | null): string {
  if (!label) return "#475569";
  if (label === "marina-consultora") return "#1e40af";
  if (label === "lucas-alumni") return "#92400e";
  if (label === "camila-account-exec") return "#5b21b6";
  return "#475569";
}

function MarinaBanner() {
  return (
    <div style={{
      padding: 20,
      borderRadius: 14,
      background: "linear-gradient(135deg, #0F2A4F 0%, #1E3A8A 100%)",
      color: "white",
    }}>
      <div style={{ fontSize: 11, fontWeight: 600, opacity: 0.7,
                     textTransform: "uppercase", letterSpacing: "0.08em" }}>
        Variant · consultor sênior
      </div>
      <h3 style={{ margin: "8px 0", fontSize: 22, fontWeight: 700 }}>
        Olá, sou a Marina. Em 4 perguntas monto seu plano.
      </h3>
      <p style={{ margin: 0, fontSize: 14, opacity: 0.9 }}>
        Tom executivo e acolhedor — como um parceiro de carreira sênior.
        Frases curtas, perguntas diretas, recomendações concretas.
      </p>
    </div>
  );
}

function LucasBanner() {
  return (
    <div style={{
      padding: 20,
      borderRadius: 14,
      background: "#fef3c7",
      border: "2px solid #f59e0b",
      color: "#78350f",
    }}>
      <div style={{ fontSize: 11, fontWeight: 600, opacity: 0.7,
                     textTransform: "uppercase", letterSpacing: "0.08em" }}>
        Variant · alumni peer
      </div>
      <h3 style={{ margin: "8px 0", fontSize: 22, fontWeight: 700 }}>
        Oi! Eu sou o Lucas — fiz o MBA de Executive Education em 2024.
      </h3>
      <p style={{ margin: 0, fontSize: 14 }}>
        Aqui eu conto a história. Primeira pessoa, casual, peer-to-peer —
        tipo amigo que já passou pelo caminho contando o que funcionou.
      </p>
    </div>
  );
}

function CamilaBanner() {
  return (
    <div style={{
      padding: 20,
      borderRadius: 14,
      background: "#ede9fe",
      border: "2px solid #8b5cf6",
      color: "#4c1d95",
    }}>
      <div style={{ fontSize: 11, fontWeight: 600, opacity: 0.7,
                     textTransform: "uppercase", letterSpacing: "0.08em" }}>
        Variant · account executive corporate
      </div>
      <h3 style={{ margin: "8px 0", fontSize: 22, fontWeight: 700 }}>
        Camila Saraiva — Account Executive Corporate
      </h3>
      <p style={{ margin: 0, fontSize: 14 }}>
        Tom profissional para comprador B2B (RH / L&D / C-level). Captura
        briefing em 6 turnos, devolve proposta corporativa precificada.
      </p>
    </div>
  );
}

function DefaultBanner() {
  return (
    <div style={{
      padding: 20,
      borderRadius: 14,
      background: "#f1f5f9",
      border: "1px dashed #cbd5e1",
      color: "#475569",
      textAlign: "center",
    }}>
      <div style={{ fontSize: 11, fontWeight: 600,
                     textTransform: "uppercase", letterSpacing: "0.08em",
                     marginBottom: 6 }}>
        No A/B context
      </div>
      <p style={{ margin: 0, fontSize: 14 }}>
        Default UI — no variant assigned. Render this when{" "}
        <code>variantLabel === null</code> (conversations outside any
        running experiment).
      </p>
    </div>
  );
}

const meta: Meta<typeof ExperimentDemo> = {
  title: "Hooks/useTuringExperiment",
  component: ExperimentDemo,
  tags: ["autodocs"],
  parameters: {
    layout: "centered",
    docs: {
      description: {
        component:
          "Thin wrapper over `useTuringFlowState` exposing only the A/B experiment fields. The story renders three variant-specific banners (Marina executive, Lucas peer, Camila B2B) plus a default fallback for null variants — flip the `variantLabel` control to compare.",
      },
    },
  },
  argTypes: {
    experimentKey: {
      description: "Simulated experiment key (or `(none)` for outside-A/B).",
      control: { type: "select" },
      options: [
        "programa-match-persona-2026q1",
        "in-company-persona-2026",
        "(none)",
      ],
    },
    variantLabel: {
      description: "Simulated variant assignment.",
      control: { type: "select" },
      options: [
        "marina-consultora",
        "lucas-alumni",
        "camila-account-exec",
        "(none)",
      ],
    },
    isLoading: {
      description: "Render the loading placeholder (variant unresolved yet).",
      control: { type: "boolean" },
    },
  },
};

export default meta;
type Story = StoryObj<typeof ExperimentDemo>;

export const MarinaConsultora: Story = {
  name: "Marina — executive consultor banner",
  args: {
    experimentKey: "programa-match-persona-2026q1",
    variantLabel: "marina-consultora",
    isLoading: false,
  },
};

export const LucasAlumni: Story = {
  name: "Lucas — alumni peer banner",
  args: {
    experimentKey: "programa-match-persona-2026q1",
    variantLabel: "lucas-alumni",
    isLoading: false,
  },
};

export const CamilaB2B: Story = {
  name: "Camila — account exec B2B banner",
  args: {
    experimentKey: "in-company-persona-2026",
    variantLabel: "camila-account-exec",
    isLoading: false,
  },
};

export const NoExperiment: Story = {
  name: "No A/B context (variantLabel = null)",
  args: {
    experimentKey: "(none)",
    variantLabel: "(none)",
    isLoading: false,
  },
};

export const Loading: Story = {
  name: "Loading — variant unresolved",
  args: {
    experimentKey: "programa-match-persona-2026q1",
    variantLabel: "marina-consultora",
    isLoading: true,
  },
};
