import { TabsContent } from "@/components/ui/tabs";

import { ChatFlowFunnelPanel } from "./chat-flow.funnel-panel";
import { ChatFlowLintPanel } from "./chat-flow.lint-panel";
import { ChatFlowTriggerConflictsPanel } from "./chat-flow.trigger-conflicts-panel";

interface AnalysisTabProps {
  agentId: string;
  flowId?: string;
}

/**
 * "Analysis" tab — groups the read-only authoring-insight panels that used to
 * sit at the top of the Settings tab: lint warnings ({@link ChatFlowLintPanel}),
 * the per-node funnel ({@link ChatFlowFunnelPanel}), and cross-flow trigger
 * ambiguity ({@link ChatFlowTriggerConflictsPanel}). Each panel still
 * self-hides when it has nothing to show, so the tab is empty on a clean,
 * brand-new flow. Keeping the diagnostics on their own tab declutters the
 * editing form.
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

        {/* T91 — cross-flow trigger ambiguity. Renders nothing until the
            agent has ≥ 2 conflicting trigger descriptions. */}
        <ChatFlowTriggerConflictsPanel agentId={agentId} flowId={flowId} />
      </div>
    </TabsContent>
  );
}
