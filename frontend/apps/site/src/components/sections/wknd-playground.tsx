import { useState } from "react";
import { MessageSquare, Search } from "lucide-react";
import { SearchDemo } from "@/components/sections/search-demo";
import { ChatDemo, type ChatScriptEntry } from "@/components/sections/chat-demo";
import { useTuringDemo } from "@/lib/turing-demo";
import { WKND_DEMO_SITE, WKND_SEARCH_DOCS } from "@/lib/site-content";
import { cn } from "@/lib/utils";

/**
 * Live WKND demo for the AEM page — the same "search it / ask it" playground the
 * home uses, pinned to the seeded `wknd-publish` SN site on the public demo
 * instance. The corpus is the Adobe WKND reference site, indexed live via the
 * Dumont AEM connector; result and citation links resolve on WKND's public
 * publish site (wknd.site). Until the demo instance hosts `wknd-publish` it
 * degrades to the bundled WKND sample corpus (never looks broken).
 */

const SEARCH_PLACEHOLDER = "Search WKND…  (try “surf”, “ski”, “Bali”, “Yosemite”)";

const CHAT_GREETING =
  "Hi! Ask about WKND adventures, travel and magazine stories — answers are grounded in the WKND content Turing indexed from AEM.";

const CHAT_SUGGESTIONS = [
  "Where can I go surfing?",
  "Recommend a ski adventure",
  "What is WKND about?",
];

// Offline scripted answers (keyword-matched) — only used when the live
// `wknd-publish` site isn't reachable, so the widget still demonstrates the flow.
const CHAT_SCRIPT: ChatScriptEntry[] = [
  {
    match: /surf|bali|wave|beach|costa rica/i,
    answer:
      "Try the **Bali Surf Camp** — Nusa Dua's right-handers are famous for big-wave surfing (best for advanced surfers). For cold-water swells, **Arctic Surfing** chases waves inside the Arctic Circle.",
    sources: ["Bali Surf Camp", "Arctic Surfing"],
  },
  {
    match: /ski|snow|mountain|winter|slope|touring/i,
    answer:
      "**Ski Touring Mont Blanc** is backcountry touring across the Chamonix valley, beneath Western Europe's highest peak.",
    sources: ["Ski Touring Mont Blanc"],
  },
  {
    match: /wine|napa|food|beer|portland/i,
    answer:
      "For food & drink, **Napa Wine Tasting** runs cellar-door tastings through California wine country, and **Beervana in Portland** tours Oregon's craft-beer scene.",
    sources: ["Napa Wine Tasting", "Beervana in Portland"],
  },
  {
    match: /wknd|about|who|brand|company/i,
    answer:
      "WKND is a collective of outdoors, music, crafts, adventure-sports and travel enthusiasts who share their experiences.",
    sources: ["About WKND"],
  },
];

const CHAT_FALLBACK = {
  answer:
    "WKND covers surf, ski, cycling, camping and travel adventures worldwide. Ask about a destination or activity — surfing, skiing, wine country — or what WKND is.",
  sources: ["WKND Adventures & Travel"],
};

type Tab = "search" | "ask";

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

export function WkndPlayground() {
  const { live } = useTuringDemo(WKND_DEMO_SITE);
  const [tab, setTab] = useState<Tab>("search");
  const [seed, setSeed] = useState<
    { text: string; nonce: number; id?: string } | undefined
  >();

  const askAbout = (title: string, id?: string) => {
    setSeed((s) => ({ text: `Tell me about: ${title}`, id, nonce: s ? s.nonce + 1 : 1 }));
    setTab("ask");
  };

  return (
    <div className="flex h-[520px] flex-col overflow-hidden rounded-2xl border border-border bg-card shadow-2xl shadow-black/30">
      {/* control bar: tabs · corpus status */}
      <div className="flex items-center gap-2 border-b border-border px-3 py-2">
        <div className="flex gap-0.5 rounded-lg bg-muted p-0.5">
          <TabButton
            active={tab === "search"}
            onClick={() => setTab("search")}
            icon={<Search className="size-3.5" />}
          >
            Search WKND
          </TabButton>
          <TabButton
            active={tab === "ask"}
            onClick={() => setTab("ask")}
            icon={<MessageSquare className="size-3.5" />}
          >
            Ask WKND
          </TabButton>
        </div>

        <span className="ml-auto inline-flex items-center gap-1.5 rounded-full border border-border px-2.5 py-1 text-xs font-medium text-muted-foreground">
          <span
            className={cn(
              "size-1.5 rounded-full",
              live
                ? "bg-emerald-500 shadow-[0_0_6px] shadow-emerald-500"
                : "bg-muted-foreground/40"
            )}
          />
          WKND on AEM · {live ? "live" : "sample"}
        </span>
      </div>

      {/* body — keep both mounted so query + conversation survive a tab switch */}
      <div className="relative flex-1 overflow-hidden">
        <div className={cn("absolute inset-0", tab === "search" ? "block" : "hidden")}>
          <SearchDemo
            bare
            siteOverride={WKND_DEMO_SITE}
            sampleDocs={WKND_SEARCH_DOCS}
            placeholder={SEARCH_PLACEHOLDER}
            onAskAbout={askAbout}
          />
        </div>
        <div className={cn("absolute inset-0", tab === "ask" ? "block" : "hidden")}>
          <ChatDemo
            bare
            seed={seed}
            siteOverride={WKND_DEMO_SITE}
            greeting={CHAT_GREETING}
            suggestions={CHAT_SUGGESTIONS}
            script={CHAT_SCRIPT}
            fallback={CHAT_FALLBACK}
            showPersonas={false}
          />
        </div>
      </div>

      {/* provenance note — the inner demo already shows "Built with …" */}
      <div className="border-t border-border px-4 py-2.5 text-xs text-muted-foreground">
        Adobe WKND content · indexed from AEM via the Dumont connector
      </div>
    </div>
  );
}
