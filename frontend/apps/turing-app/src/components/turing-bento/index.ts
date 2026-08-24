// T995 — Turing's own bento-styled chrome, as opposed to the bento layer itself.
//
// These are drawn in the bento language and composed from the package's
// primitives, but every one of them is this product's commercial offer: what a
// licence buys, what an exhausted quota costs, what pairing with Viglet AI does,
// and what to say on a surface whose whole subject is an AI capability that is
// switched off. Moving them out as shared chrome would put one product's
// business model into every product's console.
//
// They lived in `components/bento` until the shared components left it, at which
// point the directory name was the only thing still claiming they were the layer
// — a reader took the folder for the layer, and so took a product concern for
// shared chrome. The names keep their `Bento` prefix: they *are* bento tiles,
// and a rename would have made a move that only relocates files also read as a
// change in behaviour.
// Every tile here renders `bento-tile`, `bento-glass` or `bento-shell-header`,
// so the sheet is imported here too and not borrowed from `@/components/bento`.
// A page that wants only the no-LLM state — the SN generative-AI page is one —
// now imports this barrel and nothing from that one, and would otherwise render
// the frosted classes with no rule behind them.
import "@viglet/viglet-design-system/bento.css";

export { BentoActivationCard, type BentoActivationCardProps } from "./bento-activation-card";
export { BentoNoLlmState, type BentoNoLlmStateProps } from "./bento-no-llm-state";
export { BentoQuotaBanner } from "./bento-quota-banner";
export {
  BentoVigletActivationDialog,
  type BentoVigletActivationDialogProps,
} from "./bento-viglet-activation-dialog";
export {
  BentoFirstRunTour,
  type BentoFirstRunTourProps,
} from "./bento-first-run-tour";
export {
  BENTO_TOUR_SEEN_KEY,
  hasSeenBentoTour,
} from "./bento-first-run-tour.storage";
