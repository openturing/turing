import { useState } from "react";
import { CheckCircle2, Loader2, ShieldCheck } from "lucide-react";
import { fetchPersonaContentFit, type TurContentFit } from "@viglet/turing-sdk";
import { TuringContentFit, TuringPersonaPicker } from "@viglet/turing-react-ui";
import { PLAYGROUND_PERSONAS } from "@/lib/site-content";
import { useTuringDemo } from "@/lib/turing-demo";
import { cn } from "@/lib/utils";

// T638 — "validate this content as persona X". Dogfoods the SDK's
// fetchPersonaContentFit + the headless TuringContentFit component against the
// live demo. Offline it shows a scripted verdict so the tab never looks broken.

const SAMPLE_CONTENT =
  "Our platform leverages best-in-class synergies to operationalize a paradigm-shifting, " +
  "cloud-native fabric that unlocks turnkey, mission-critical value at scale.";

// Scripted offline verdict (keeps the tab alive without a backend).
function scriptedFit(personaId: string): TurContentFit {
  const persona = PLAYGROUND_PERSONAS.find((p) => p.id === personaId) ?? PLAYGROUND_PERSONAS[0];
  return {
    personaId: persona.id,
    personaName: persona.name,
    fitScore: 34,
    summary:
      "Dense with buzzwords and abstract claims; this reader wants concrete, verifiable specifics.",
    fits: ["Signals ambition and scale"],
    misfits: [
      { span: "leverages best-in-class synergies", reason: "jargon", suggestion: "works well together" },
      { span: "paradigm-shifting", reason: "jargon", suggestion: "a genuinely new approach" },
      { span: "turnkey, mission-critical value", reason: "too-complex", suggestion: "ready-to-use, important results" },
    ],
    llmUsed: false,
  };
}

export interface ContentFitDemoProps {
  /** Drop standalone chrome so the playground frames it. */
  bare?: boolean;
}

export function ContentFitDemo({ bare = false }: Readonly<ContentFitDemoProps>) {
  const { client, site, live } = useTuringDemo();
  const [personaId, setPersonaId] = useState<string>(PLAYGROUND_PERSONAS[0].id);
  const [content, setContent] = useState(SAMPLE_CONTENT);
  const [result, setResult] = useState<TurContentFit | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function validate() {
    const text = content.trim();
    if (!text || busy) return;
    setBusy(true);
    setError(null);
    try {
      if (client && site) {
        setResult(await fetchPersonaContentFit(client, site, personaId, text));
      } else {
        // Offline: brief pause then a scripted verdict.
        await new Promise((r) => setTimeout(r, 400));
        setResult(scriptedFit(personaId));
      }
    } catch {
      setError("Couldn't validate this content right now — try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div
      className={cn(
        "flex flex-col overflow-hidden bg-card text-card-foreground",
        // bare: fill the playground frame (fixed height → inner scroll).
        // standalone (persona page): grow with the report instead of scrolling
        // inside a fixed box — min height keeps the empty state from collapsing.
        bare ? "h-full" : "min-h-105 rounded-2xl border border-border shadow-xl"
      )}
    >
      <TuringPersonaPicker
        options={PLAYGROUND_PERSONAS}
        value={personaId}
        onChange={(id) => setPersonaId(id ?? PLAYGROUND_PERSONAS[0].id)}
        labels={{ heading: "Validate as" }}
        classNames={{
          container: "flex flex-wrap items-center gap-1.5 border-b border-border px-4 py-2",
          label: "text-[0.65rem] font-bold uppercase tracking-wide text-muted-foreground/70",
          list: "flex flex-wrap gap-1",
          option:
            "rounded-full border border-border bg-muted px-2.5 py-0.5 text-xs font-medium text-muted-foreground transition-colors hover:border-primary/50 hover:text-foreground data-[active]:border-primary data-[active]:bg-primary/10 data-[active]:text-foreground",
          optionActive: "border-primary bg-primary/10 text-foreground",
        }}
      />

      <div
        className={cn(
          "flex flex-1 flex-col gap-3 px-4 py-4",
          // Only scroll inside a fixed frame (playground). Standalone lets the
          // page scroll so the whole report is visible without a nested scrollbar.
          bare && "overflow-y-auto",
        )}
      >
        <label className="text-[0.65rem] font-bold uppercase tracking-wide text-muted-foreground/70">
          Content to validate
        </label>
        <textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          rows={3}
          maxLength={12000}
          placeholder="Paste a paragraph and see if it lands for this reader…"
          className="w-full shrink-0 resize-none rounded-lg border border-border bg-background/60 px-3 py-2 text-sm outline-none focus:border-primary/50"
          aria-label="Content to validate"
        />
        <div>
          <button
            type="button"
            onClick={() => void validate()}
            disabled={!content.trim() || busy}
            className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3.5 py-2 text-sm font-semibold text-primary-foreground transition-opacity disabled:opacity-40"
          >
            {busy ? <Loader2 className="size-4 animate-spin" /> : <ShieldCheck className="size-4" />}
            Validate content
          </button>
        </div>

        {error && <p className="text-sm text-destructive">{error}</p>}

        {result && (
          <TuringContentFit
            result={result}
            labels={{
              fitsHeading: "What lands",
              misfitsHeading: "What doesn't",
              fallbackNote: "Readability-only score (live AI verdict unavailable).",
            }}
            icons={{ fit: <CheckCircle2 className="mr-1 inline size-3.5 text-emerald-500" /> }}
            classNames={{
              container: "mt-1 space-y-3 rounded-xl border border-border bg-background/50 p-4",
              header: "flex items-center justify-between gap-2",
              personaName: "text-sm font-semibold",
              fitValue:
                "text-sm font-bold data-[fit^='7']:text-emerald-500 data-[fit^='8']:text-emerald-500 data-[fit^='9']:text-emerald-500",
              fitBar: "h-2 w-full overflow-hidden rounded-full bg-muted",
              fitBarFill: "block h-full w-[var(--turing-ui-fit)] rounded-full bg-gradient-to-r from-blue-600 to-indigo-600",
              summary: "text-sm text-muted-foreground",
              fitsHeading: "text-[0.65rem] font-bold uppercase tracking-wide text-emerald-600",
              fits: "space-y-0.5 text-sm",
              fitItem: "text-foreground",
              misfitsHeading: "text-[0.65rem] font-bold uppercase tracking-wide text-amber-600",
              misfits: "space-y-1.5 text-sm",
              misfitItem: "rounded-lg bg-muted/60 p-2",
              misfitSpan: "font-medium text-foreground",
              misfitReason:
                "ml-1.5 rounded bg-amber-500/15 px-1.5 py-0.5 text-[0.65rem] font-semibold uppercase text-amber-700 dark:text-amber-400",
              misfitSuggestion: "mt-0.5 block text-muted-foreground",
              fallbackNote: "text-[0.7rem] italic text-muted-foreground/70",
            }}
          />
        )}

        {!result && !busy && (
          <p className="text-xs text-muted-foreground">
            {live
              ? "See the same content through a persona's eyes — a fit score, what lands, and the exact phrases that miss."
              : "Sample verdict shown offline — connect the live demo for a real AI content-fit check."}
          </p>
        )}
      </div>
    </div>
  );
}
