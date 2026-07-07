import { TabsContent } from "@/components/ui/tabs";

import { ChatFlowFunnelPanel } from "./chat-flow.funnel-panel";
import { ChatFlowLintPanel } from "./chat-flow.lint-panel";

interface AnalysisTabProps {
  agentId: string;
  flowId?: string;
}

/**
 * "Analysis" tab — the flow-scoped read-only authoring-insight panels: lint
 * warnings ({@link ChatFlowLintPanel}) and the per-node funnel
 * ({@link ChatFlowFunnelPanel}). Each panel self-hides when it has nothing to
 * show, so the tab is empty on a clean, brand-new flow.
 *
 * <p>Cross-flow trigger ambiguity is agent-scoped (not tied to the flow being
 * edited), so it lives on its own "Trigger ambiguity" page under the agent's
 * "Analysis" sidebar group rather than here.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export function ChatFlowAnalysisTab({ agentId, flowId }: Readonly<AnalysisTabProps>) {
  return (
    <TabsContent value="analysis" className="flex-1 min-h-0 overflow-y-auto">
      <div className="space-y-4 px-4 lg:px-6 py-2 pb-8">
        {/* T94 — lint findings first so ERROR-class issues blocking the
            runtime are unmissable. Renders nothing on a clean flow. */}
        {flowId && <ChatFlowLintPanel agentId={agentId} flowId={flowId} />}

        {/* T85 — per-node funnel: cursor + completion counts so the operator
            spots where conversations stall. Hidden on a brand-new (id-less)
            flow and on flows without any captured states/submissions yet. */}
        {flowId && <ChatFlowFunnelPanel agentId={agentId} flowId={flowId} />}
      </div>
    </TabsContent>
  );
}
