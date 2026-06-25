import { useState } from "react";
import { useParams } from "react-router-dom";

import { useSystemPromptPreview } from "@/api/queries/system-prompt.queries";
import { SystemPromptLivePreview } from "@/components/agent/system-prompt-live-preview";

/**
 * Standalone "Live Preview" page — the assembled system message for a previewed
 * turn, broken down by origin (persona, agent prompt, MCP, chat-flow addendum,
 * RAG …) with a chat-flow selector and the tool schemas the model receives.
 *
 * Although it reflects the agent's system prompt, the preview pulls together
 * persona + chat-flow context, so it lives under the agent's "Chat" sidebar
 * group (below Chat Flow) rather than inside the System Prompt editor.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export default function AIAgentLivePreviewPage() {
  const { id } = useParams() as { id: string };
  // Which chat flow governs the preview (undefined → backend default = first
  // enabled flow). Drives the segment breakdown and the assembled prompt.
  const [flowId, setFlowId] = useState<string | undefined>(undefined);
  const { data: preview, isLoading } = useSystemPromptPreview(id, flowId);

  return (
    <div className="space-y-4 px-4 lg:px-6 py-2 pb-8">
      <SystemPromptLivePreview
        preview={preview}
        isLoading={isLoading}
        flowId={flowId}
        onFlowChange={setFlowId}
      />
    </div>
  );
}
