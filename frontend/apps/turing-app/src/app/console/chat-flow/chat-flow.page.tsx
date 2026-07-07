import "@xyflow/react/dist/style.css";

import {
  ReactFlowProvider,
  addEdge,
  reconnectEdge,
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
  IconBolt,
  IconDeviceFloppy,
  IconDownload,
  IconSitemap,
  IconSparkles,
  IconUpload,
} from "@tabler/icons-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
import { useSidebar } from "@/components/ui/sidebar";
import { BentoHero } from "@/components/bento";
import { GradientButton } from "@/components/ui/gradient-button";
import { DialogDelete } from "@/components/dialog.delete";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  type TurChatFlow,
  type TurChatFlowCaptureMode,
  type TurChatFlowGuardrailMethod,
  type TurChatFlowTriggerLanguage,
  type TurChatFlowTriggerMode,
} from "@/models/agent/chat-flow.model";
import { useAIAgentSlots } from "@/api/queries/ai-agent-slot.queries";
import {
  useCreateChatFlow,
  useDeleteChatFlow,
  useImportChatFlow,
  useImportChatFlowBundle,
  useUpdateChatFlow,
} from "@/api/queries/chat-flow.queries";
import { TurChatFlowService } from "@/services/agent/chat-flow.service";

import { buildImportPayloadFromExport } from "./chat-flow.import";
import { ChatFlowAnalysisTab } from "./components/chat-flow.analysis-tab";
import { ChatFlowDiagramTab } from "./components/chat-flow.diagram-tab";
import { ChatFlowExperimentTab } from "./components/chat-flow.experiment-tab";
import { ChatFlowSettingsTab } from "./components/chat-flow.settings-tab";
import { ChatFlowVariantDialog } from "./components/chat-flow.variant-dialog";
import { failureEdgeOverlay } from "./components/flow-edges";
import {
  INITIAL_ZOOM,
  buildDefaultNodeData,
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
  baseRoute: string;
  chrome: "console" | "bento";
}

/**
 * Auto-collapses the AI Agent's console sidebar so the canvas gets the extra
 * horizontal space. Rendered only under the console chrome — the bento shell
 * has no `SidebarProvider`, so `useSidebar()` would throw there. Isolating the
 * hook in a conditionally-mounted child keeps the rule-of-hooks intact.
 */
function AutoCollapseSidebar() {
  const { setOpen } = useSidebar();
  const autoCollapsedRef = useRef(false);
  useEffect(() => {
    if (autoCollapsedRef.current) return;
    autoCollapsedRef.current = true;
    setOpen(false);
  }, [setOpen]);
  return null;
}

function ChatFlowInner({ agentId, flow, isNew, baseRoute, chrome }: InnerProps) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const wrapperRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { screenToFlowPosition } = useReactFlow();
  const { zoom } = useViewport();
  const zoomPercent = Math.round((zoom / INITIAL_ZOOM) * 100);
  const listUrl = `${baseRoute}/${agentId}/chat-flow`;
  const createMutation = useCreateChatFlow();
  const updateMutation = useUpdateChatFlow();
  const deleteMutation = useDeleteChatFlow();
  const importMutation = useImportChatFlow();
  const importBundleMutation = useImportChatFlowBundle();
  const { data: agentSlots } = useAIAgentSlots(agentId);

  const initialGraph = deserializeGraph(flow.definitionJson);
  const [nodes, setNodes, onNodesChange] = useNodesState<Node<FlowNodeData>>(initialGraph.nodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>(initialGraph.edges);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [livePreviewOpen, setLivePreviewOpen] = useState(false);
  // T97 — chat-flow variant generator. Holds the source flow so the dialog
  // remounts cleanly per open; `null` keeps the dialog closed. Disabled on
  // new (unsaved) flows because the LLM needs a persisted source to rewrite.
  const [variantSource, setVariantSource] = useState<TurChatFlow | null>(null);
  const [name, setName] = useState(flow.name ?? "");
  const [description, setDescription] = useState(flow.description ?? "");
  const [guardrailMethod, setGuardrailMethod] = useState<TurChatFlowGuardrailMethod>(
    flow.guardrailMethod ?? "LLM_JUDGE",
  );
  const [captureMode, setCaptureMode] = useState<TurChatFlowCaptureMode>(
    flow.captureMode ?? "VALIDATE_THEN_CAPTURE",
  );
  const [abandonHandoffMessage, setAbandonHandoffMessage] = useState(
    flow.abandonHandoffMessage ?? "",
  );
  const [triggerDescription, setTriggerDescription] = useState(flow.triggerDescription ?? "");
  const [triggerMode, setTriggerMode] = useState<TurChatFlowTriggerMode>(
    flow.triggerMode ?? "ONCE",
  );
  const [triggerLanguage, setTriggerLanguage] = useState<TurChatFlowTriggerLanguage>(
    flow.triggerLanguage ?? "AUTO",
  );
  const [experimentKey, setExperimentKey] = useState(flow.experimentKey ?? "");
  const [variantLabel, setVariantLabel] = useState(flow.variantLabel ?? "");
  const [trafficWeight, setTrafficWeight] = useState<number | null>(flow.trafficWeight ?? null);
  const [banditEnabled, setBanditEnabled] = useState<boolean>(flow.banditEnabled ?? false);
  const [autoPromote, setAutoPromote] = useState<boolean>(flow.autoPromote ?? false);
  const [experimentSuccessMetric, setExperimentSuccessMetric] = useState<string>(
    flow.experimentSuccessMetric ?? "GOAL_ACHIEVED",
  );
  const [slotInheritanceJson, setSlotInheritanceJson] = useState(flow.slotInheritanceJson ?? "");
  const [open, setDialogOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  const onConnect = useCallback(
    (params: Connection) =>
      setEdges((eds) =>
        // T50 — failure-handle connections render as red dashed try/catch edges;
        // the overlay flips `type` to "failure" so the edgeTypes map picks the
        // FailureEdge renderer. Non-failure connections stay on smoothstep.
        addEdge(
          { ...params, animated: false, ...failureEdgeOverlay(params.sourceHandle) },
          eds,
        ),
      ),
    [setEdges],
  );

  const onReconnect = useCallback(
    (oldEdge: Edge, newConnection: Connection) =>
      // Reconnects can move an edge between a regular handle and the failure
      // handle; re-derive the overlay so the visual stays consistent with the
      // new sourceHandle.
      setEdges((eds) =>
        reconnectEdge(oldEdge, newConnection, eds).map((edge) =>
          edge.id === oldEdge.id
            ? { ...edge, ...failureEdgeOverlay(edge.sourceHandle) }
            : edge,
        ),
      ),
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
        data: buildDefaultNodeData(type),
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

  // T234 — replace the whole graph after collapsing a question run into a
  // native form, then select the new form node. Dirty state is implicit:
  // the user still clicks Save to persist.
  const applyGraph = useCallback(
    (nextNodes: Node<FlowNodeData>[], nextEdges: Edge[], selectId: string | null) => {
      setNodes(nextNodes);
      setEdges(nextEdges);
      setSelectedNodeId(selectId);
    },
    [setNodes, setEdges],
  );

  const selectedNode = nodes.find((n) => n.id === selectedNodeId) ?? null;

  const onSave = useCallback(async () => {
    if (!name.trim()) {
      toast.error(t("chatFlow.errors.nameRequired"));
      return;
    }
    setSaving(true);
    const trimmedKey = experimentKey.trim();
    const payload: TurChatFlow = {
      ...flow,
      name: name.trim(),
      description: description.trim() || null,
      definitionJson: serializeGraph(nodes, edges),
      enabled: flow.enabled ?? 1,
      guardrailMethod,
      captureMode,
      abandonHandoffMessage: abandonHandoffMessage.trim() || null,
      triggerDescription: triggerDescription.trim() || null,
      triggerMode,
      triggerLanguage,
      // A/B experiment fields stay coupled: clearing the key wipes
      // label + weight so the backend sees a clean opt-out instead of
      // dangling metadata.
      experimentKey: trimmedKey || null,
      variantLabel: trimmedKey ? (variantLabel.trim() || null) : null,
      trafficWeight: trimmedKey ? trafficWeight : null,
      banditEnabled: trimmedKey ? banditEnabled : null,
      autoPromote: trimmedKey ? autoPromote : null,
      experimentSuccessMetric: trimmedKey ? (experimentSuccessMetric || null) : null,
      slotInheritanceJson: slotInheritanceJson.trim() || null,
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
  }, [name, description, guardrailMethod, captureMode, abandonHandoffMessage, triggerDescription, triggerMode, triggerLanguage,
      experimentKey, variantLabel, trafficWeight, banditEnabled, autoPromote, experimentSuccessMetric,
      slotInheritanceJson,
      nodes, edges, flow, isNew, agentId, listUrl, navigate, t, createMutation, updateMutation]);

  const onExport = useCallback(() => {
    const flowName = name.trim() || flow.name || "chat-flow";
    // Embed only the slots actually referenced by an outputVariable in the graph — keeps the
    // export minimal and avoids leaking the rest of the agent's slot catalogue. Names match the
    // unique-per-agent contract the backend enforces on import.
    const referencedSlotNames = new Set(
      nodes
        .map((n) => n.data?.outputVariable)
        .filter((v): v is string => typeof v === "string" && v.length > 0),
    );
    const slots = (agentSlots ?? [])
      .filter((s) => referencedSlotNames.has(s.name))
      .map(({ id: _id, ...rest }) => rest);
    const payload = {
      name: flowName,
      description: description.trim() || null,
      guardrailMethod,
      triggerDescription: triggerDescription.trim() || null,
      triggerMode,
      slots,
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
  }, [name, description, guardrailMethod, triggerDescription, triggerMode, nodes, edges, flow.name, agentSlots]);

  const onImportFile = useCallback(
    async (file: File) => {
      try {
        const text = await file.text();
        const parsed = JSON.parse(text) as unknown;
        // Importing always creates a brand new flow (the current draft, persisted or not, is left
        // untouched). Two file shapes are accepted: a single flow object (legacy single-import) or
        // an array bundle whose items the backend auto-wires by sub-flow id.
        if (Array.isArray(parsed)) {
          const bundle = parsed.map(buildImportPayloadFromExport);
          const created = await importBundleMutation.mutateAsync({ agentId, bundle });
          if (created.length === 0) {
            throw new Error("Empty bundle");
          }
          toast.success(
            t("chatFlow.bundleImported", {
              count: created.length,
              defaultValue: `Imported ${created.length} chat flow(s).`,
            }),
          );
          navigate(`${listUrl}/${created[0].id}`);
        } else {
          const payload = buildImportPayloadFromExport(
            parsed as Parameters<typeof buildImportPayloadFromExport>[0],
          );
          const created = await importMutation.mutateAsync({ agentId, payload });
          toast.success(t("chatFlow.imported", { name: created.name ?? file.name }));
          navigate(`${listUrl}/${created.id}`);
        }
      } catch (error) {
        console.error("Chat flow import failed", error);
        toast.error(t("chatFlow.importFailed"));
      }
    },
    [agentId, importBundleMutation, importMutation, listUrl, navigate, t],
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

  const actionButtons = (
    <>
      <GradientButton size="sm" onClick={onSave} disabled={saving}>
        <IconDeviceFloppy className="size-4" />
        {saving ? t("chatFlow.actions.saving") : t("chatFlow.actions.save")}
      </GradientButton>
      <GradientButton variant="outline" size="sm" onClick={() => setLivePreviewOpen((o) => !o)}>
        <IconBolt className="size-4" />
        {t("chatFlow.actions.livePreview", { defaultValue: "Live preview" })}
      </GradientButton>
      {!isNew && (
        <GradientButton variant="outline" size="sm" onClick={() => setVariantSource(flow)}>
          <IconSparkles className="size-4" />
          {t("chatFlow.actions.generateVariant", { defaultValue: "Generate variant" })}
        </GradientButton>
      )}
      <GradientButton variant="outline" size="sm" onClick={onExport}>
        <IconDownload className="size-4" />
        {t("chatFlow.actions.exportJson")}
      </GradientButton>
      <GradientButton variant="outline" size="sm" onClick={onImportClick}>
        <IconUpload className="size-4" />
        {t("chatFlow.actions.importJson")}
      </GradientButton>
      {!isNew && (
        <DialogDelete
          feature={t("chatFlow.title")}
          name={flow.name}
          onDelete={onDelete}
          open={open}
          setOpen={setDialogOpen}
        />
      )}
    </>
  );

  return (
    <div className="flex h-full min-h-0 flex-1 flex-col">
      {chrome === "console" && <AutoCollapseSidebar />}
      {chrome === "bento" ? (
        <div className="px-1 pt-1">
          <BentoHero
            eyebrow={
              <Link to={listUrl} className="hover:text-foreground">
                {t("chatFlow.title")}
              </Link>
            }
            leading={
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-indigo-600 to-fuchsia-600 text-white shadow-md">
                <IconSitemap size={24} />
              </span>
            }
            title={flow.name || t("chatFlow.newInstance")}
            subtitle={t("chatFlow.description")}
            trailing={<div className="flex flex-wrap items-center justify-end gap-2">{actionButtons}</div>}
          />
        </div>
      ) : (
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
            label={t("chatFlow.actions.livePreview", {
              defaultValue: "Live preview",
            })}
            icon={IconBolt}
            onClick={() => setLivePreviewOpen((open) => !open)}
          />
          {!isNew && (
            <SubPageHeader.Action
              label={t("chatFlow.actions.generateVariant", {
                defaultValue: "Generate variant",
              })}
              icon={IconSparkles}
              onClick={() => setVariantSource(flow)}
            />
          )}
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
      )}

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
          <TabsTrigger value="experiment">
            {t("chatFlow.tabs.experiment", { defaultValue: "A/B experiment" })}
          </TabsTrigger>
          <TabsTrigger value="analysis">
            {t("chatFlow.tabs.analysis", { defaultValue: "Analysis" })}
          </TabsTrigger>
        </TabsList>

        <ChatFlowSettingsTab
          agentId={agentId}
          name={name}
          setName={setName}
          description={description}
          setDescription={setDescription}
          guardrailMethod={guardrailMethod}
          setGuardrailMethod={setGuardrailMethod}
          captureMode={captureMode}
          setCaptureMode={setCaptureMode}
          abandonHandoffMessage={abandonHandoffMessage}
          setAbandonHandoffMessage={setAbandonHandoffMessage}
          triggerDescription={triggerDescription}
          setTriggerDescription={setTriggerDescription}
          triggerMode={triggerMode}
          setTriggerMode={setTriggerMode}
          triggerLanguage={triggerLanguage}
          setTriggerLanguage={setTriggerLanguage}
          slotInheritanceJson={slotInheritanceJson}
          setSlotInheritanceJson={setSlotInheritanceJson}
        />

        <ChatFlowDiagramTab
          wrapperRef={wrapperRef}
          agentId={agentId}
          currentFlowId={flow.id}
          nodes={nodes}
          edges={edges}
          zoomPercent={zoomPercent}
          selectedNode={selectedNode}
          livePreviewOpen={livePreviewOpen}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          onReconnect={onReconnect}
          onNodeClick={onNodeClick}
          onPaneClick={onPaneClick}
          onDragOver={onDragOver}
          onDrop={onDrop}
          updateNodeData={updateNodeData}
          applyGraph={applyGraph}
          setSelectedNodeId={setSelectedNodeId}
          setLivePreviewOpen={setLivePreviewOpen}
        />

        <ChatFlowExperimentTab
          experimentKey={experimentKey}
          setExperimentKey={setExperimentKey}
          variantLabel={variantLabel}
          setVariantLabel={setVariantLabel}
          trafficWeight={trafficWeight}
          setTrafficWeight={setTrafficWeight}
          banditEnabled={banditEnabled}
          setBanditEnabled={setBanditEnabled}
          autoPromote={autoPromote}
          setAutoPromote={setAutoPromote}
          experimentSuccessMetric={experimentSuccessMetric}
          setExperimentSuccessMetric={setExperimentSuccessMetric}
        />

        <ChatFlowAnalysisTab agentId={agentId} flowId={isNew ? undefined : flow.id} />
      </Tabs>

      <ChatFlowVariantDialog
        source={variantSource}
        agentId={agentId}
        onClose={() => setVariantSource(null)}
      />
    </div>
  );
}

interface ChatFlowPageProps {
  /** Base route for navigation (defaults to the console AI agent list). */
  readonly baseRoute?: string;
  /** Render inside the bento shell (no console sidebar / SubPageHeader). */
  readonly chrome?: "console" | "bento";
}

export default function ChatFlowPage({ baseRoute = ROUTES.AI_AGENT_INSTANCE, chrome = "console" }: ChatFlowPageProps = {}) {
  const { id: agentId, flowId } = useParams() as { id: string; flowId: string };
  const { t } = useTranslation();
  const [flow, setFlow] = useState<TurChatFlow>();
  const [error, setError] = useState<string | null>(null);
  const isNew = flowId === "new";
  const tryAgainUrl = `${baseRoute}/${agentId}/chat-flow/${flowId}`;

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
        guardrailMethod: "LLM_JUDGE",
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
          <ChatFlowInner key={flowId} agentId={agentId} flow={flow} isNew={isNew} baseRoute={baseRoute} chrome={chrome} />
        )}
      </ReactFlowProvider>
    </LoadProvider>
  );
}
