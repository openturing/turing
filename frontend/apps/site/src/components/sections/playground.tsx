import { useEffect, useState } from "react";
import { Maximize2, MessageSquare, Minimize2, Search, X } from "lucide-react";
import { Container, SectionHeader } from "@/components/brand";
import { SearchDemo } from "@/components/sections/search-demo";
import { ChatDemo } from "@/components/sections/chat-demo";
import { useTuringDemo } from "@/lib/turing-demo";
import { cn } from "@/lib/utils";

type Tab = "search" | "ask";

/**
 * The "Try it live" playground — the site's centerpiece proof.
 *
 * Both tabs run against the SAME live corpus (Turing's own docs, the `turing-docs`
 * dogfood seed): keyword+faceted **search** and cited **RAG chat**, two lenses on
 * one content set. A result's "Ask about this" bridges straight into the chat, so
 * the shared-corpus connection is tangible. The panel is fullscreen-capable.
 */
export function Playground() {
  const { live } = useTuringDemo();
  const [tab, setTab] = useState<Tab>("search");
  const [expanded, setExpanded] = useState(false);
  const [seed, setSeed] = useState<{ text: string; nonce: number } | undefined>();

  // Escape closes fullscreen; lock body scroll while expanded.
  useEffect(() => {
    if (!expanded) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setExpanded(false);
    window.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
    };
  }, [expanded]);

  const askAbout = (title: string) => {
    setSeed({ text: `Tell me about: ${title}`, nonce: seed ? seed.nonce + 1 : 1 });
    setTab("ask");
  };

  const panel = (
    <div
      className={cn(
        "flex flex-col overflow-hidden border border-border bg-card shadow-2xl shadow-black/30",
        expanded
          ? "fixed inset-3 z-50 rounded-2xl sm:inset-6 lg:inset-12"
          : "h-[520px] rounded-2xl"
      )}
    >
      {/* control bar: tabs · corpus · fullscreen */}
      <div className="flex items-center gap-2 border-b border-border px-3 py-2">
        <div className="flex gap-0.5 rounded-lg bg-muted p-0.5">
          <TabButton active={tab === "search"} onClick={() => setTab("search")} icon={<Search className="size-3.5" />}>
            Search it
          </TabButton>
          <TabButton active={tab === "ask"} onClick={() => setTab("ask")} icon={<MessageSquare className="size-3.5" />}>
            Ask it
          </TabButton>
        </div>

        <span className="ml-auto hidden items-center gap-1.5 rounded-full border border-border px-2.5 py-1 text-xs font-medium text-muted-foreground sm:inline-flex">
          <span
            className={cn(
              "size-1.5 rounded-full",
              live ? "bg-emerald-500 shadow-[0_0_6px] shadow-emerald-500" : "bg-muted-foreground/40"
            )}
          />
          Turing docs · {live ? "live" : "sample"}
        </span>

        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          aria-label={expanded ? "Exit fullscreen" : "Expand to fullscreen"}
          title={expanded ? "Exit fullscreen (Esc)" : "Expand to fullscreen"}
          className="flex size-8 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
        >
          {expanded ? <Minimize2 className="size-4" /> : <Maximize2 className="size-4" />}
        </button>
        {expanded && (
          <button
            type="button"
            onClick={() => setExpanded(false)}
            aria-label="Close"
            className="flex size-8 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          >
            <X className="size-4" />
          </button>
        )}
      </div>

      {/* body — keep both mounted so state (query, conversation) survives tab switches */}
      <div className="relative flex-1 overflow-hidden">
        <div className={cn("absolute inset-0", tab === "search" ? "block" : "hidden")}>
          <SearchDemo bare onAskAbout={askAbout} />
        </div>
        <div className={cn("absolute inset-0", tab === "ask" ? "block" : "hidden")}>
          <ChatDemo bare seed={seed} />
        </div>
      </div>
    </div>
  );

  return (
    <section id="playground" className="py-20">
      <Container>
        <SectionHeader
          eyebrow="Try it live"
          title="Search it, or ask it — same content"
          description="Both run on Turing's own zero-dependency SDK, over the exact same corpus (Turing's documentation). Search finds the page; the assistant answers with a cited RAG response that traces back to the very same page. Try a result's “Ask about this”."
        />
        {expanded && (
          <div
            className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm"
            onClick={() => setExpanded(false)}
            aria-hidden
          />
        )}
        {panel}
      </Container>
    </section>
  );
}

function TabButton({
  active,
  onClick,
  icon,
  children,
}: Readonly<{
  active: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  children: React.ReactNode;
}>) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-semibold transition-colors",
        active
          ? "bg-background text-foreground shadow-sm"
          : "text-muted-foreground hover:text-foreground"
      )}
    >
      {icon}
      {children}
    </button>
  );
}
