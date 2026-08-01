import type { Meta, StoryObj } from "@storybook/react-vite";
import { TuringAnswer } from "../../ui/TuringAnswer";

/**
 * # useTuringAnswer / TuringAnswer (T837)
 *
 * The answer-first surface: a streaming grounded answer with inline `[n]`
 * citation anchors (T836), source cards and follow-up chips, in one import.
 * Consumes the T833 streaming endpoint `GET /api/sn/{siteName}/answer`.
 *
 * - **Headless hook** `useTuringAnswer({ siteName })` → `{ answer, sources,
 *   citations, followups, trace, status, error, isStreaming, ask, stop, reset }`.
 *   `ask(query)` opens the SSE stream; `sources` arrive before the first token.
 * - **Component** `TuringAnswer` — on the `TuringCopilot` skinning contract:
 *   every part is class-nameable via `classNames` and it ships ZERO styles. Each
 *   source card carries `id="turing-source-${rank}"`, the target the `[n]`
 *   anchors scroll-link to.
 * - **Vanilla** `createAnswerController(client, config)` ports the same observable
 *   answer state to `@viglet/turing-sdk` for plain-JS / EDS surfaces.
 *
 * ```tsx
 * const { answer, sources, followups, ask } = useTuringAnswer({ siteName: "docs" });
 * // or, batteries-included:
 * <TuringAnswer siteName="docs" classNames={{ answer: "prose", followup: "chip" }} />
 * ```
 *
 * > This story renders the live component — the answer streams only against a
 * > running Turing backend with a RAG-enabled site. Point `siteName` at your site.
 */
const meta: Meta<typeof TuringAnswer> = {
  title: "AI Chat/useTuringAnswer",
  component: TuringAnswer,
  parameters: { layout: "padded" },
};
export default meta;

type Story = StoryObj<typeof TuringAnswer>;

export const Default: Story = {
  args: {
    siteName: "sample",
    placeholder: "Ask a question…",
    labels: { submit: "Ask", loading: "Thinking…", sources: "Sources", followups: "Ask next" },
  },
};
