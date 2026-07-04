import ConversationPage from "@/app/console/conversation/conversation.page";

/**
 * Bento conversation detail — T563. The T120 Spectator + Co-pilot surface is a
 * live three-pane monitor (transcript · slots · workspace) that already renders
 * its own header and uses {@code h-full}; hosting it in the Bento shell only
 * needs a sized full-height wrapper so its internal flex layout resolves. Opened
 * per-conversation from the parked-conversations dashboard or a deep link.
 */
export default function BentoConversationPage() {
  return (
    <div className="h-[calc(100svh-9rem)]">
      <ConversationPage />
    </div>
  );
}
