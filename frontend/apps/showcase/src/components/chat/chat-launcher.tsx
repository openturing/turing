import { AtlasChatPanel } from "@/components/chat/atlas-chat";
import { onAskAtlas } from "@/lib/ask-atlas";
import { IconSparkles } from "@tabler/icons-react";
import { useEffect, useState } from "react";

/**
 * Floating "Ask Atlas" launcher + slide-over chat panel. Must render inside a
 * `TuringProvider` (the storefront supplies it) so the chat hooks resolve the
 * site/config from context. Subscribes to the {@link onAskAtlas} bridge so the
 * "Why #1?" button and the proactive copilot can open it with a preset prompt.
 */
export function ChatLauncher() {
  const [open, setOpen] = useState(false);
  const [pending, setPending] = useState<string | null>(null);

  useEffect(
    () =>
      onAskAtlas((question) => {
        setPending(question);
        setOpen(true);
      }),
    []
  );

  return (
    <>
      {!open && (
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="fixed bottom-5 right-5 z-40 inline-flex items-center gap-2 rounded-full bg-linear-to-br from-primary to-indigo-700 px-4 py-3 text-sm font-medium text-primary-foreground shadow-lg shadow-primary/30 transition-transform hover:scale-105"
        >
          <IconSparkles className="size-5" />
          Ask Atlas
        </button>
      )}
      {open && (
        <>
          <button
            aria-label="Close chat"
            className="fixed inset-0 z-40 bg-black/40 backdrop-blur-sm"
            onClick={() => setOpen(false)}
          />
          <AtlasChatPanel
            onClose={() => setOpen(false)}
            initialMessage={pending}
            onInitialConsumed={() => setPending(null)}
          />
        </>
      )}
    </>
  );
}
