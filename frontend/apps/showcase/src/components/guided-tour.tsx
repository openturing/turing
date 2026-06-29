import { Button } from "@/components/ui/button";
import { askAtlas } from "@/lib/ask-atlas";
import { cn } from "@/lib/utils";
import { IconChevronLeft, IconChevronRight, IconHelpCircle, IconX } from "@tabler/icons-react";
import { useState } from "react";

interface TourStep {
  title: string;
  body: string;
  /** Optional in-app action to demonstrate the step. */
  action?: { label: string; run: () => void };
}

const STEPS: TourStep[] = [
  {
    title: "Welcome to Atlas Store",
    body: "A reference app that stresses the maximum Viglet Turing ES surface — typed catalog, faceted + hybrid search, RAG chat, agents, skills and creative bets. This tour narrates each capability.",
  },
  {
    title: "Faceted search (T452)",
    body: "Search with URL-synced facets, autocomplete, spell-check, category tabs, sort and a 'you may also like' rail on product pages — all driven by the React SDK against a typed 200-product catalog.",
  },
  {
    title: "Ask Atlas — the RAG copilot (T453)",
    body: "The floating 'Ask Atlas' button opens a streaming RAG chat: cited sources, option chips, native forms, slots, multimodal image upload, a workspace panel and rich markdown/html/d2 content.",
    action: { label: "Open the copilot", run: () => askAtlas("What can you help me with?") },
  },
  {
    title: "Agent power (T454)",
    body: "The agent acts on your live cart via client tools (add_to_cart/get_cart), runs a code interpreter to total your basket, shows live tool-call activity, renders answer-as-app comparison widgets, and runs a bundled returns/RMA skill.",
    action: { label: "Compare products", run: () => askAtlas("Compare the top 3 headphones for me") },
  },
  {
    title: "Creative bets (T455)",
    body: "Co-browse (the agent drives this search UI), glass-box 'Why #1?' ranking explanations, an ambient proactive copilot, agent→agent handoff, and an embeddable action widget on a mock partner site.",
    action: { label: "Ask why #1", run: () => askAtlas("Why is the top result ranked first?") },
  },
  {
    title: "Voice, memory & ops (T456)",
    body: "Dictate questions by voice, search by image, see what Atlas remembers about you across sessions (with GDPR delete), and explore the /ops tour mapping analytics, eval, cost governance and MCP exposure.",
  },
];

export function GuidedTour() {
  const [open, setOpen] = useState(() => {
    try {
      return localStorage.getItem("atlas-store-tour-seen") !== "1";
    } catch {
      return false;
    }
  });
  const [step, setStep] = useState(0);

  const close = () => {
    try {
      localStorage.setItem("atlas-store-tour-seen", "1");
    } catch {
      /* ignore */
    }
    setOpen(false);
  };

  if (!open) {
    return (
      <button
        type="button"
        aria-label="Open guided tour"
        onClick={() => {
          setStep(0);
          setOpen(true);
        }}
        className="fixed bottom-5 left-5 z-30 grid size-9 place-items-center rounded-full border border-border bg-card text-muted-foreground shadow hover:text-foreground"
        title="Guided tour"
      >
        <IconHelpCircle className="size-5" />
      </button>
    );
  }

  const current = STEPS[step];
  const isLast = step === STEPS.length - 1;

  return (
    <div className="fixed inset-x-0 bottom-5 z-[60] flex justify-center px-4">
      <div className="w-full max-w-md rounded-2xl border border-border bg-card p-4 shadow-2xl">
        <div className="mb-2 flex items-start justify-between gap-2">
          <h3 className="text-sm font-bold">{current.title}</h3>
          <button aria-label="Close tour" onClick={close} className="rounded-md p-1 hover:bg-accent">
            <IconX className="size-4" />
          </button>
        </div>
        <p className="text-sm leading-relaxed text-muted-foreground">{current.body}</p>

        {current.action && (
          <Button
            size="sm"
            variant="outline"
            className="mt-3"
            onClick={() => {
              current.action!.run();
              close();
            }}
          >
            {current.action.label}
          </Button>
        )}

        <div className="mt-4 flex items-center justify-between">
          <div className="flex gap-1.5">
            {STEPS.map((s, i) => (
              <span
                key={s.title}
                className={cn(
                  "size-1.5 rounded-full",
                  i === step ? "bg-primary" : "bg-muted-foreground/30"
                )}
              />
            ))}
          </div>
          <div className="flex items-center gap-1">
            <Button
              size="sm"
              variant="ghost"
              disabled={step === 0}
              onClick={() => setStep((s) => Math.max(0, s - 1))}
            >
              <IconChevronLeft className="size-4" />
            </Button>
            {isLast ? (
              <Button size="sm" onClick={close}>
                Done
              </Button>
            ) : (
              <Button size="sm" onClick={() => setStep((s) => Math.min(STEPS.length - 1, s + 1))}>
                Next <IconChevronRight className="ml-1 size-4" />
              </Button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
