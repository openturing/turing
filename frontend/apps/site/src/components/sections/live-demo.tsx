import { Container, SectionHeader } from "@/components/brand";
import { ChatDemo } from "@/components/sections/chat-demo";

const kw = "text-[#8b5cf6] dark:text-[#c792ea]";
const str = "text-[#16a34a] dark:text-[#c3e88d]";
const fn = "text-[#2563eb] dark:text-[#82aaff]";
const cm = "text-muted-foreground";

/** #3 — the dogfood reveal: the real SDK code that drives the live widget. */
function SdkCode() {
  return (
    <div className="overflow-hidden rounded-2xl border border-border bg-card shadow-xl">
      <div className="flex items-center gap-2.5 border-b border-border px-4 py-3">
        <span className="size-2.5 rounded-full bg-[#ff5f57]" />
        <span className="size-2.5 rounded-full bg-[#febc2e]" />
        <span className="size-2.5 rounded-full bg-[#28c840]" />
        <span className="ml-1 font-mono text-xs text-muted-foreground">assistant.ts</span>
      </div>
      <pre className="overflow-x-auto bg-muted/50 p-5 font-mono text-[0.8rem] leading-relaxed">
        <code>
          <span className={cm}>{"// The widget on the left runs this — really."}</span>
          {"\n"}
          <span className={kw}>import</span> {"{ createTuringClient, createChatController }"}
          {"\n"}
          {"  "}
          <span className={kw}>from</span> <span className={str}>"@viglet/turing-sdk"</span>;{"\n\n"}
          <span className={kw}>const</span> client = <span className={fn}>createTuringClient</span>({"{"}
          {"\n  "}
          <span className={fn}>baseURL</span>: <span className={str}>"https://demo.turing.viglet.org/api"</span>,
          {"\n"}
          {"});"}
          {"\n\n"}
          <span className={kw}>const</span> chat = <span className={fn}>createChatController</span>(client, {"{"}
          {"\n  "}
          <span className={fn}>site</span>: <span className={str}>"turing-docs"</span>,
          {"\n"}
          {"});"}
          {"\n\n"}
          <span className={cm}>{"// streams tokens + RAG citations"}</span>
          {"\n"}
          chat.<span className={fn}>subscribe</span>((s) ={">"} <span className={fn}>render</span>(s.messages));
          {"\n"}
          chat.<span className={fn}>send</span>(<span className={str}>"Does Turing support self-hosting?"</span>);
        </code>
      </pre>
    </div>
  );
}

export function LiveDemo() {
  return (
    <section id="developers" className="py-20">
      <Container>
        <SectionHeader
          eyebrow="Built with the SDK"
          title="A working assistant — not a mockup"
          description="This page runs on the product's own zero-dependency @viglet/turing-sdk. The assistant below is driven by the exact code beside it."
        />
        <div className="grid items-stretch gap-5 lg:grid-cols-2">
          <SdkCode />
          <ChatDemo />
        </div>
      </Container>
    </section>
  );
}
