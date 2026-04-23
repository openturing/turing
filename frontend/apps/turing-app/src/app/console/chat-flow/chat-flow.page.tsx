import "@xyflow/react/dist/style.css";

import {
  Background,
  BackgroundVariant,
  Controls,
  MiniMap,
  ReactFlow,
  ReactFlowProvider,
  addEdge,
  useEdgesState,
  useNodesState,
  useReactFlow,
  useViewport,
  type Connection,
  type Edge,
  type Node,
  type NodeMouseHandler,
} from "@xyflow/react";
import { IconDownload, IconEye, IconSitemap } from "@tabler/icons-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { SubPageHeader } from "@/components/sub.page.header";
import { useSidebar } from "@/components/ui/sidebar";

import { ChatPreview } from "./components/chat-preview";
import { FlowProperties } from "./components/flow-properties";
import { FlowSidebar } from "./components/flow-sidebar";
import { nodeTypes } from "./components/flow-nodes";
import type { FlowNodeData, FlowNodeType } from "./types";

/**
 * Chat Flow builder — visual editor for Spring AI conversation flows. Produces a JSON
 * representation of the authored graph that the backend Advisor can execute step by step.
 *
 * @since 2026.2.4
 */

const DEFAULT_LABEL: Record<FlowNodeType, string> = {
  start: "START",
  end: "END",
  aiQuestion: "AI QUESTION",
  condition: "CONDITION",
  functionCall: "FUNCTION CALL",
};

/**
 * Starting zoom level for the canvas. We surface this value to the user as "100%", mapping the
 * raw React Flow zoom to a percentage relative to this baseline (so zooming out reads as &lt; 100%
 * and zooming in as &gt; 100%, regardless of the underlying transform).
 */
const INITIAL_ZOOM = 0.8;
const INITIAL_VIEWPORT = { x: 40, y: 40, zoom: INITIAL_ZOOM };

const INITIAL_NODES: Node<FlowNodeData>[] = [
  {
    id: "start-1",
    type: "start",
    position: { x: 80, y: 240 },
    data: { label: "START", type: "start" },
  },
];

const INITIAL_EDGES: Edge[] = [];

function ChatFlowInner() {
  const { t } = useTranslation();
  const wrapperRef = useRef<HTMLDivElement>(null);
  const { screenToFlowPosition } = useReactFlow();
  const { zoom } = useViewport();
  const zoomPercent = Math.round((zoom / INITIAL_ZOOM) * 100);
  const { setOpen } = useSidebar();
  const autoCollapsedRef = useRef(false);

  /* Auto-collapse the AI Agent's internal sidebar on first render only: the canvas needs the
     extra horizontal space. The ref guard prevents re-collapsing after the user reopens the
     sidebar — shadcn's `setOpen` identity changes whenever `open` flips, which would otherwise
     make a naive dependency-array effect undo the user's expand click. */
  useEffect(() => {
    if (autoCollapsedRef.current) return;
    autoCollapsedRef.current = true;
    setOpen(false);
  }, [setOpen]);

  const [nodes, setNodes, onNodesChange] = useNodesState<Node<FlowNodeData>>(INITIAL_NODES);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>(INITIAL_EDGES);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);

  const onConnect = useCallback(
    (params: Connection) =>
      setEdges((eds) => addEdge({ ...params, type: "smoothstep", animated: false }, eds)),
    [setEdges],
  );

  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = "move";
  }, []);

  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();
      const type = event.dataTransfer.getData("application/x-chatflow-node") as FlowNodeType | "";
      if (!type) return;
      const position = screenToFlowPosition({ x: event.clientX, y: event.clientY });
      const id = `${type}-${crypto.randomUUID().slice(0, 8)}`;
      const newNode: Node<FlowNodeData> = {
        id,
        type,
        position,
        data: { label: DEFAULT_LABEL[type], type },
      };
      setNodes((nds) => nds.concat(newNode));
    },
    [screenToFlowPosition, setNodes],
  );

  const onNodeClick: NodeMouseHandler = useCallback((_event, node) => {
    setSelectedNodeId(node.id);
  }, []);

  const onPaneClick = useCallback(() => setSelectedNodeId(null), []);

  const updateNodeData = useCallback(
    (nodeId: string, patch: Partial<FlowNodeData>) => {
      setNodes((prev) =>
        prev.map((n) =>
          n.id === nodeId
            ? { ...n, data: { ...n.data, ...patch } }
            : n,
        ),
      );
    },
    [setNodes],
  );

  const selectedNode = nodes.find((n) => n.id === selectedNodeId) ?? null;

  const exportJson = useCallback(() => {
    const payload = {
      nodes: nodes.map((n) => ({
        id: n.id,
        type: n.type,
        position: n.position,
        data: n.data,
      })),
      edges: edges.map((e) => ({
        id: e.id,
        source: e.source,
        target: e.target,
        sourceHandle: e.sourceHandle,
        targetHandle: e.targetHandle,
        label: e.label,
      })),
    };
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "chat-flow.json";
    link.click();
    URL.revokeObjectURL(url);
  }, [nodes, edges]);

  return (
    <div className="flex h-full min-h-0 flex-1 flex-col">
      <SubPageHeader
        icon={IconSitemap}
        feature={t("aiAgent.chatFlow.title")}
        name={t("aiAgent.chatFlow.title")}
        description={t("aiAgent.chatFlow.description")}
      >
        <SubPageHeader.Action
          label={t("chatFlow.actions.exportJson")}
          icon={IconDownload}
          onClick={exportJson}
        />
        <SubPageHeader.Action
          label={t("chatFlow.actions.preview")}
          icon={IconEye}
          onClick={() => setPreviewOpen((open) => !open)}
        />
      </SubPageHeader>

      <div className="flex flex-1 overflow-hidden">
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
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onNodeClick={onNodeClick}
            onPaneClick={onPaneClick}
            defaultEdgeOptions={{ type: "smoothstep" }}
            defaultViewport={INITIAL_VIEWPORT}
            fitViewOptions={{ minZoom: INITIAL_ZOOM, maxZoom: INITIAL_ZOOM }}
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
          onChange={updateNodeData}
          onClose={() => setSelectedNodeId(null)}
        />

        {previewOpen && <ChatPreview nodes={nodes} edges={edges} onClose={() => setPreviewOpen(false)} />}
      </div>
    </div>
  );
}

export default function ChatFlowPage() {
  return (
    <ReactFlowProvider>
      <ChatFlowInner />
    </ReactFlowProvider>
  );
}
