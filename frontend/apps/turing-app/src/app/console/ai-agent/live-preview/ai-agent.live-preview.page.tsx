import { useState } from "react";
import { useParams } from "react-router-dom";

import {
  useSystemPromptPreview,
  useSystemPromptReplayPreview,
} from "@/api/queries/system-prompt.queries";
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
export default function AIAgentLivePreviewPage({ chrome = "console" }: { chrome?: "console" | "bento" }) {
  const { id } = useParams() as { id: string };
  // Which chat flow governs the preview (undefined → backend default = first
  // enabled flow). Drives the segment breakdown and the assembled prompt.
  const [flowId, setFlowId] = useState<string | undefined>(undefined);
  // T611 — which flow node governs (undefined → entry node) + simulated
  // collected slot values (JSON string) for a mid-conversation preview.
  const [nodeId, setNodeId] = useState<string | undefined>(undefined);
  const [vars, setVars] = useState<string | undefined>(undefined);
  // T612 — a real conversation to replay (undefined → synthesized preview).
  const [replayConversationId, setReplayConversationId] = useState<
    string | undefined
  >(undefined);
  // T618 — which captured past turn to load verbatim (undefined → T612
  // current-state replay). Reset when the conversation id changes.
  const [replayTurnIndex, setReplayTurnIndex] = useState<number | undefined>(
    undefined,
  );
  const replaying = Boolean(replayConversationId?.trim());

  const synthesized = useSystemPromptPreview(id, flowId, nodeId, vars);
  const replay = useSystemPromptReplayPreview(
    id,
    replaying ? replayConversationId : undefined,
    replaying ? replayTurnIndex : undefined,
  );
  const preview = replaying ? replay.data : synthesized.data;
  const isLoading = replaying ? replay.isLoading : synthesized.isLoading;

  // Switching flows invalidates the picked node (node ids are per-flow).
  function handleFlowChange(value: string) {
    setFlowId(value);
    setNodeId(undefined);
    setVars(undefined);
  }

  // Changing the replayed conversation resets the captured-turn selection.
  function handleReplayChange(value: string | undefined) {
    setReplayConversationId(value);
    setReplayTurnIndex(undefined);
  }

  return (
    <div className="space-y-4 px-4 lg:px-6 py-2 pb-8">
      <SystemPromptLivePreview
        preview={preview}
        isLoading={isLoading}
        flowId={flowId}
        onFlowChange={handleFlowChange}
        nodeId={nodeId}
        onNodeChange={setNodeId}
        onVarsChange={setVars}
        replayConversationId={replayConversationId}
        onReplayChange={handleReplayChange}
        replayTurnIndex={replayTurnIndex}
        onReplayTurnChange={setReplayTurnIndex}
        // In bento the hero already shows the "Live Preview" title and reuses
        // this description as its subtitle, so suppress the article's masthead.
        showHeader={chrome !== "bento"}
      />
    </div>
  );
}
