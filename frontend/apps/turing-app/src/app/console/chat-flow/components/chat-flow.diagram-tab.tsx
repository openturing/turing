import { TabsContent } from "@/components/ui/tabs";
import {
  Background,
  BackgroundVariant,
  Controls,
  MiniMap,
  ReactFlow,
  type Connection,
  type Edge,
  type EdgeChange,
  type Node,
  type NodeChange,
  type NodeMouseHandler,
} from "@xyflow/react";
import { useTranslation } from "react-i18next";

import { ChatFlowLivePreviewPanel } from "./chat-flow.live-preview-panel";
import { edgeTypes } from "./flow-edges";
import { FlowProperties } from "./flow-properties";
import { FlowSidebar } from "./flow-sidebar";
import { nodeTypes } from "./flow-nodes";
import type { FlowNodeData } from "../types";
import { INITIAL_VIEWPORT, INITIAL_ZOOM } from "../chat-flow.serialize";

interface DiagramTabProps {
  wrapperRef: React.RefObject<HTMLDivElement | null>;
  agentId: string;
  currentFlowId?: string;
  nodes: Node<FlowNodeData>[];
  edges: Edge[];
  zoomPercent: number;
  selectedNode: Node<FlowNodeData> | null;
  livePreviewOpen: boolean;
  onNodesChange: (changes: NodeChange<Node<FlowNodeData>>[]) => void;
  onEdgesChange: (changes: EdgeChange<Edge>[]) => void;
  onConnect: (params: Connection) => void;
  onReconnect: (oldEdge: Edge, newConnection: Connection) => void;
  onNodeClick: NodeMouseHandler;
  onPaneClick: () => void;
  onDragOver: (e: React.DragEvent) => void;
  onDrop: (e: React.DragEvent) => void;
  updateNodeData: (nodeId: string, patch: Partial<FlowNodeData>) => void;
  applyGraph: (nodes: Node<FlowNodeData>[], edges: Edge[], selectId: string | null) => void;
  setSelectedNodeId: (id: string | null) => void;
  setLivePreviewOpen: (open: boolean) => void;
}

/**
 * Diagram tab — the React Flow canvas plus the left sidebar (drag sources),
 * the right properties panel, and the optional preview overlay.
 *
 * Kept as `forceMount`-ed by the parent so node positions and unsaved drag
 * state aren't lost when the user toggles to Settings.
 */
export function ChatFlowDiagramTab({
  wrapperRef,
  agentId,
  currentFlowId,
  nodes,
  edges,
  zoomPercent,
  selectedNode,
  livePreviewOpen,
  onNodesChange,
  onEdgesChange,
  onConnect,
  onReconnect,
  onNodeClick,
  onPaneClick,
  onDragOver,
  onDrop,
  updateNodeData,
  applyGraph,
  setSelectedNodeId,
  setLivePreviewOpen,
}: Readonly<DiagramTabProps>) {
  const { t } = useTranslation();
  return (
    <TabsContent
      value="diagram"
      forceMount
      className="flex-1 min-h-0 overflow-hidden data-[state=inactive]:hidden"
    >
      <div className="flex h-full overflow-hidden">
        <FlowSidebar />

        <div
          ref={wrapperRef}
          role="application"
          aria-label={t("chatFlow.title")}
          className="relative flex-1"
          onDragOver={onDragOver}
          onDrop={onDrop}
        >
          <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={nodeTypes}
            edgeTypes={edgeTypes}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onReconnect={onReconnect}
            onNodeClick={onNodeClick}
            onPaneClick={onPaneClick}
            defaultEdgeOptions={{ type: "smoothstep" }}
            defaultViewport={INITIAL_VIEWPORT}
            fitViewOptions={{ minZoom: INITIAL_ZOOM, maxZoom: INITIAL_ZOOM }}
            deleteKeyCode={["Backspace", "Delete"]}
            proOptions={{ hideAttribution: true }}
          >
            <Background variant={BackgroundVariant.Dots} gap={16} size={1} />
            <Controls showInteractive={false} />
            <MiniMap pannable zoomable className="bg-background!" />
          </ReactFlow>
          <div className="pointer-events-none absolute right-3 top-3 rounded-md border bg-background/90 px-2 py-1 text-xs font-medium tabular-nums shadow-sm">
            {t("chatFlow.zoom")}: {zoomPercent}%
          </div>
        </div>

        <FlowProperties
          node={selectedNode}
          nodes={nodes}
          edges={edges}
          onChange={updateNodeData}
          onApplyGraph={applyGraph}
          onClose={() => setSelectedNodeId(null)}
          agentId={agentId}
          currentFlowId={currentFlowId}
        />

        {/* T95 — Live preview. A single surface with two modes: a node-focused
            view (mocked slots, <300ms, no real LLM turn) and a whole-flow
            transcript walk (the consolidated former ChatPreview). */}
        {livePreviewOpen && (
          <ChatFlowLivePreviewPanel
            selectedNode={selectedNode}
            nodes={nodes}
            edges={edges}
            flowId={currentFlowId}
            onClose={() => setLivePreviewOpen(false)}
          />
        )}
      </div>
    </TabsContent>
  );
}
