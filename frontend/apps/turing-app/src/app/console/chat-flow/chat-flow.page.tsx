import "@xyflow/react/dist/style.css";

import {
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
import {
  IconDeviceFloppy,
  IconDownload,
  IconEye,
  IconSitemap,
  IconUpload,
} from "@tabler/icons-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useSidebar } from "@/components/ui/sidebar";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  CHAT_FLOW_GUARDRAIL_METHODS,
  CHAT_FLOW_TRIGGER_MODES,
  type TurChatFlow,
  type TurChatFlowGuardrailMethod,
  type TurChatFlowTriggerMode,
} from "@/models/agent/chat-flow.model";
import {
  useCreateChatFlow,
  useDeleteChatFlow,
  useUpdateChatFlow,
} from "@/api/queries/chat-flow.queries";
import { TurChatFlowService } from "@/services/agent/chat-flow.service";

import { ChatFlowDiagramTab } from "./components/chat-flow.diagram-tab";
import { ChatFlowSettingsTab } from "./components/chat-flow.settings-tab";
import {
  DEFAULT_LABEL,
  INITIAL_ZOOM,
  deserializeGraph,
  serializeGraph,
} from "./chat-flow.serialize";
import type { FlowNodeData, FlowNodeType } from "./types";

/**
 * Chat Flow editor — visual builder for Spring AI conversation flows scoped
 * to a single AI agent. The graph (nodes + edges) is serialized into the
 * persisted {@link TurChatFlow} entity so the Phase B advisor can replay it
 * as a state machine.
 *
 * @since 2026.2.5
 */

const turChatFlowService = new TurChatFlowService();

interface InnerProps {
  agentId: string;
  flow: TurChatFlow;
  isNew: boolean;
}

function ChatFlowInner({ agentId, flow, isNew }: InnerProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const wrapperRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { screenToFlowPosition } = useReactFlow();
  const { zoom } = useViewport();
  const zoomPercent = Math.round((zoom / INITIAL_ZOOM) * 100);
  const { setOpen } = useSidebar();
  const autoCollapsedRef = useRef(false);
  const listUrl = `${ROUTES.AI_AGENT_INSTANCE}/${agentId}/chat-flow`;
  const createMutation = useCreateChatFlow();
  const updateMutation = useUpdateChatFlow();
  const deleteMutation = useDeleteChatFlow();

  /* Auto-collapse the AI Agent's internal sidebar on first render only: the canvas needs the
     extra horizontal space. The ref guard prevents re-collapsing after the user reopens the
     sidebar — shadcn's `setOpen` identity changes whenever `open` flips, which would otherwise
     make a naive dependency-array effect undo the user's expand click. */
  useEffect(() => {
    if (autoCollapsedRef.current) return;
    autoCollapsedRef.current = true;
    setOpen(false);
  }, [setOpen]);

  const initialGraph = deserializeGraph(flow.definitionJson);
  const [nodes, setNodes, onNodesChange] = useNodesState<Node<FlowNodeData>>(initialGraph.nodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>(initialGraph.edges);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [name, setName] = useState(flow.name ?? "");
  const [description, setDescription] = useState(flow.description ?? "");
  const [guardrailMethod, setGuardrailMethod] = useState<TurChatFlowGuardrailMethod>(
    flow.guardrailMethod ?? "LLM_JUDGE",
  );
  const [triggerDescription, setTriggerDescription] = useState(flow.triggerDescription ?? "");
  const [triggerMode, setTriggerMode] = useState<TurChatFlowTriggerMode>(
    flow.triggerMode ?? "ONCE",
  );
  const [open, setDialogOpen] = useState(false);
  const [saving, setSaving] = useState(false);

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
        prev.map((n) => (n.id === nodeId ? { ...n, data: { ...n.data, ...patch } } : n)),
      );
    },
    [setNodes],
  );

  const selectedNode = nodes.find((n) => n.id === selectedNodeId) ?? null;

  const onSave = useCallback(async () => {
    if (!name.trim()) {
      toast.error(t("chatFlow.errors.nameRequired"));
      return;
    }
    setSaving(true);
    const payload: TurChatFlow = {
      ...flow,
      name: name.trim(),
      description: description.trim() || null,
      definitionJson: serializeGraph(nodes, edges),
      enabled: flow.enabled ?? 1,
      guardrailMethod,
      triggerDescription: triggerDescription.trim() || null,
      triggerMode,
    };
    try {
      if (isNew) {
        const created = await createMutation.mutateAsync({ agentId, flow: payload });
        toast.success(t("chatFlow.saved", { name: created.name }));
      } else {
        const updated = await updateMutation.mutateAsync({ agentId, flow: payload });
        toast.success(t("chatFlow.updated", { name: updated.name }));
      }
      navigate(listUrl);
    } catch (error) {
      console.error("Chat flow save failed", error);
      toast.error(t("chatFlow.notSaved", { name: payload.name }));
    } finally {
      setSaving(false);
    }
  }, [name, description, guardrailMethod, triggerDescription, triggerMode, nodes, edges, flow, isNew, agentId, listUrl, navigate, t, createMutation, updateMutation]);

  const onExport = useCallback(() => {
    const flowName = name.trim() || flow.name || "chat-flow";
    const payload = {
      name: flowName,
      description: description.trim() || null,
      guardrailMethod,
      triggerDescription: triggerDescription.trim() || null,
      triggerMode,
      graph: {
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
          label: typeof e.label === "string" ? e.label : null,
        })),
      },
    };
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    const safeFileName = flowName.replace(/[^a-zA-Z0-9-_]+/g, "-").toLowerCase() || "chat-flow";
    link.download = `${safeFileName}.chat-flow.json`;
    link.click();
    URL.revokeObjectURL(url);
  }, [name, description, guardrailMethod, triggerDescription, triggerMode, nodes, edges, flow.name]);

  const onImportFile = useCallback(
    async (file: File) => {
      try {
        const text = await file.text();
        const parsed = JSON.parse(text) as {
          name?: string;
          description?: string | null;
          guardrailMethod?: TurChatFlowGuardrailMethod;
          triggerDescription?: string | null;
          triggerMode?: TurChatFlowTriggerMode;
          graph?: { nodes?: unknown; edges?: unknown };
          // Back-compat: pre-Phase-B exports stored nodes/edges at the top level.
          nodes?: unknown;
          edges?: unknown;
        };
        const graphSource =
          parsed.graph && (parsed.graph.nodes || parsed.graph.edges)
            ? parsed.graph
            : { nodes: parsed.nodes, edges: parsed.edges };
        const reHydrated = deserializeGraph(JSON.stringify(graphSource));
        setNodes(reHydrated.nodes);
        setEdges(reHydrated.edges);
        if (typeof parsed.name === "string") setName(parsed.name);
        if (typeof parsed.description === "string") setDescription(parsed.description);
        if (parsed.guardrailMethod && CHAT_FLOW_GUARDRAIL_METHODS.includes(parsed.guardrailMethod)) {
          setGuardrailMethod(parsed.guardrailMethod);
        }
        if (typeof parsed.triggerDescription === "string") setTriggerDescription(parsed.triggerDescription);
        if (parsed.triggerMode && CHAT_FLOW_TRIGGER_MODES.includes(parsed.triggerMode)) {
          setTriggerMode(parsed.triggerMode);
        }
        toast.success(t("chatFlow.imported", { name: parsed.name ?? file.name }));
      } catch (error) {
        console.error("Chat flow import failed", error);
        toast.error(t("chatFlow.importFailed"));
      }
    },
    [setNodes, setEdges, t],
  );

  const onImportClick = useCallback(() => {
    fileInputRef.current?.click();
  }, []);

  const onDelete = useCallback(async () => {
    try {
      if (await deleteMutation.mutateAsync({ agentId, flow })) {
        toast.success(t("chatFlow.deleted", { name: flow.name }));
        navigate(listUrl);
      } else {
        toast.error(t("chatFlow.notDeleted", { name: flow.name }));
      }
    } catch (error) {
      console.error("Chat flow delete failed", error);
      toast.error(t("chatFlow.notDeleted", { name: flow.name }));
    }
    setDialogOpen(false);
  }, [agentId, flow, listUrl, navigate, t, deleteMutation]);

  return (
    <div className="flex h-full min-h-0 flex-1 flex-col">
      <SubPageHeader
        icon={IconSitemap}
        feature={t("chatFlow.title")}
        name={flow.name ?? t("chatFlow.newInstance")}
        description={t("chatFlow.description")}
        urlBase={listUrl}
        onDelete={isNew ? undefined : onDelete}
        open={isNew ? undefined : open}
        setOpen={isNew ? undefined : setDialogOpen}
      >
        <SubPageHeader.Action
          label={saving ? t("chatFlow.actions.saving") : t("chatFlow.actions.save")}
          icon={IconDeviceFloppy}
          onClick={onSave}
          disabled={saving}
        />
        <SubPageHeader.Action
          label={t("chatFlow.actions.preview")}
          icon={IconEye}
          onClick={() => setPreviewOpen((openPreview) => !openPreview)}
        />
        <SubPageHeader.Action
          label={t("chatFlow.actions.exportJson")}
          icon={IconDownload}
          onClick={onExport}
        />
        <SubPageHeader.Action
          label={t("chatFlow.actions.importJson")}
          icon={IconUpload}
          onClick={onImportClick}
        />
      </SubPageHeader>

      <input
        ref={fileInputRef}
        type="file"
        accept="application/json,.json"
        aria-label={t("chatFlow.actions.importJson")}
        title={t("chatFlow.actions.importJson")}
        className="hidden"
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) {
            void onImportFile(file);
          }
          // Reset so the same file can be re-imported.
          event.target.value = "";
        }}
      />

      <Tabs defaultValue="settings" className="flex flex-1 min-h-0 flex-col">
        <TabsList className="mx-4 lg:mx-6 mb-3 self-start">
          <TabsTrigger value="settings">{t("chatFlow.tabs.settings")}</TabsTrigger>
          <TabsTrigger value="diagram">{t("chatFlow.tabs.diagram")}</TabsTrigger>
        </TabsList>

        <ChatFlowSettingsTab
          name={name}
          setName={setName}
          description={description}
          setDescription={setDescription}
          guardrailMethod={guardrailMethod}
          setGuardrailMethod={setGuardrailMethod}
          triggerDescription={triggerDescription}
          setTriggerDescription={setTriggerDescription}
          triggerMode={triggerMode}
          setTriggerMode={setTriggerMode}
        />

        <ChatFlowDiagramTab
          wrapperRef={wrapperRef}
          nodes={nodes}
          edges={edges}
          zoomPercent={zoomPercent}
          selectedNode={selectedNode}
          previewOpen={previewOpen}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          onNodeClick={onNodeClick}
          onPaneClick={onPaneClick}
          onDragOver={onDragOver}
          onDrop={onDrop}
          updateNodeData={updateNodeData}
          setSelectedNodeId={setSelectedNodeId}
          setPreviewOpen={setPreviewOpen}
        />
      </Tabs>
    </div>
  );
}

export default function ChatFlowPage() {
  const { id: agentId, flowId } = useParams() as { id: string; flowId: string };
  const { t } = useTranslation();
  const [flow, setFlow] = useState<TurChatFlow>();
  const [error, setError] = useState<string | null>(null);
  const isNew = flowId === "new";
  const tryAgainUrl = `${ROUTES.AI_AGENT_INSTANCE}/${agentId}/chat-flow/${flowId}`;

  useEffect(() => {
    if (!agentId) return;
    if (isNew) {
      // Leave `id` undefined — the backend's UUID generator preserves any
      // pre-assigned id (including ""), so we must omit it on creation.
      setFlow({
        name: "",
        description: "",
        definitionJson: "",
        enabled: 1,
        guardrailMethod: "HEURISTIC",
        triggerDescription: "",
        triggerMode: "ONCE",
      });
      return;
    }
    turChatFlowService
      .get(agentId, flowId)
      .then(setFlow)
      .catch(() => setError(t("common.connectionError", { resource: t("chatFlow.title").toLowerCase() })));
  }, [agentId, flowId, isNew, t]);

  return (
    <LoadProvider checkIsNotUndefined={flow} error={error} tryAgainUrl={tryAgainUrl}>
      <ReactFlowProvider>
        {flow && (
          /* `key` forces a remount whenever the flow changes (after a save the
             URL flips from `new` to the persisted UUID). Without it, the inner
             `useState` initial values from the previous render would stick. */
          <ChatFlowInner key={flowId} agentId={agentId} flow={flow} isNew={isNew} />
        )}
      </ReactFlowProvider>
    </LoadProvider>
  );
}
