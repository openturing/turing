import { ROUTES } from "@/app/routes.const";
import { AiAuthoringLayout } from "@/components/ai-authoring/ai-authoring-layout";
import { AiAuthoringPanel } from "@/components/ai-authoring/ai-authoring-panel";
import { LoadProvider } from "@/components/loading-provider";
import { Button } from "@/components/ui/button";
import { SectionCard } from "@/components/ui/section-card";
import type { BreadcrumbItem } from "@/contexts/breadcrumb.context";
import { useAiAuthoring } from "@/hooks/use-ai-authoring";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useCreateChatFlow } from "@/api/queries/chat-flow.queries";
import type {
  ChatFlowEdgeGeneration,
  ChatFlowGeneration,
  ChatFlowNodeGeneration,
  ChatFlowNodeType,
  TurChatFlow,
} from "@/models/agent/chat-flow.model";
import { TurChatFlowService } from "@/services/agent/chat-flow.service";
import {
  IconArrowRight,
  IconBook2,
  IconBolt,
  IconBulb,
  IconDatabase,
  IconDeviceFloppy,
  IconFlag3,
  IconPlayerPlayFilled,
} from "@tabler/icons-react";
import { useCallback, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

/**
 * Chat Flow AI Authoring page. Mirrors the Intent AI chat — left panel
 * shows a structural preview of the flow being built (nodes +
 * branching) plus the high-level metadata; right panel is the chat.
 *
 * After the user is happy, the "Save & Open in Editor" button persists
 * the flow with a serialized {@code definitionJson} (positions auto-
 * assigned) and navigates to the regular React Flow editor where they
 * can fine-tune visually.
 *
 * @since 2026.2.5
 */
const turChatFlowService = new TurChatFlowService();

const NODE_X_STEP = 260;
const NODE_X_ORIGIN = 80;
const NODE_Y_ORIGIN = 240;

interface ChatFlowAiChatPageProps {
  /** Base route for navigation (defaults to the console AI agent list). */
  readonly baseRoute?: string;
}

export default function ChatFlowAiChatPage({ baseRoute = ROUTES.AI_AGENT_INSTANCE }: ChatFlowAiChatPageProps = {}) {
  const { id: agentId } = useParams() as { id: string };
  const { t } = useTranslation();
  const navigate = useNavigate();

  const [flow, setFlow] = useState<ChatFlowGeneration>(emptyFlow());
  const [saving, setSaving] = useState(false);
  const createMutation = useCreateChatFlow();
  const [breadcrumb] = useState<BreadcrumbItem[] | undefined>([
    { label: t("chatFlow.aiChat.breadcrumb") },
  ]);
  useSubPageBreadcrumb(breadcrumb);

  const flowRef = useRef<ChatFlowGeneration>(flow);
  flowRef.current = flow;

  const getCurrentState = useCallback(() => flowRef.current, []);
  const applyState = useCallback((state: ChatFlowGeneration) => setFlow(state), []);

  const chat = useAiAuthoring<ChatFlowGeneration>({
    endpoint: (req) => turChatFlowService.aiChat(agentId, req),
    getCurrentState,
    applyState,
  });

  const flowListUrl = `${baseRoute}/${agentId}/chat-flow`;

  async function onSave() {
    if (!flow.name?.trim()) {
      toast.error(t("chatFlow.aiChat.nameRequired"));
      return;
    }
    if (!flow.nodes || flow.nodes.length === 0) {
      toast.error(t("chatFlow.aiChat.emptyFlow"));
      return;
    }
    setSaving(true);
    const payload: TurChatFlow = {
      name: flow.name.trim(),
      description: flow.description ?? null,
      enabled: flow.enabled ?? 1,
      guardrailMethod: flow.guardrailMethod ?? "LLM_JUDGE",
      triggerDescription: flow.triggerDescription ?? null,
      triggerMode: flow.triggerMode ?? "ONCE",
      definitionJson: serializeForEditor(flow),
    };
    try {
      const created = await createMutation.mutateAsync({ agentId, flow: payload });
      toast.success(t("chatFlow.aiChat.saved", { name: created.name }));
      navigate(`${flowListUrl}/${created.id}`);
    } catch (error) {
      console.error("Save error", error);
      toast.error(t("chatFlow.aiChat.saveFailed"));
    } finally {
      setSaving(false);
    }
  }

  return (
    <LoadProvider checkIsNotUndefined={flow} error={null} tryAgainUrl={`${flowListUrl}/new/ai-chat`}>
      <AiAuthoringLayout
        chat={
          <AiAuthoringPanel
            chat={chat}
            title={t("chatFlow.aiChat.panelTitle")}
            subtitle={t("chatFlow.aiChat.panelSubtitle")}
            placeholder={t("chatFlow.aiChat.placeholder")}
            emptyState={t("chatFlow.aiChat.empty")}
            enableVoice
          />
        }
      >
        <FlowPreview flow={flow} saving={saving} onSave={onSave} onCancel={() => navigate(flowListUrl)} />
      </AiAuthoringLayout>
    </LoadProvider>
  );
}

/* ───────────────── Preview pane (left) ───────────────── */

interface FlowPreviewProps {
  readonly flow: ChatFlowGeneration;
  readonly saving: boolean;
  readonly onSave: () => void;
  readonly onCancel: () => void;
}

function FlowPreview({ flow, saving, onSave, onCancel }: FlowPreviewProps) {
  const { t } = useTranslation();
  const hasContent = !!flow.nodes && flow.nodes.length > 0;

  return (
    <div className="space-y-4 px-4 lg:px-6 py-6">
      <div className="flex items-start justify-between gap-3 flex-wrap">
        <div className="min-w-0">
          <h1 className="text-xl font-semibold truncate">
            {flow.name?.trim() || t("chatFlow.aiChat.untitled")}
          </h1>
          {flow.description && (
            <p className="text-sm text-muted-foreground mt-1 line-clamp-3">{flow.description}</p>
          )}
        </div>
        <div className="flex items-center gap-2 shrink-0">
          <Button type="button" variant="outline" size="sm" onClick={onCancel} disabled={saving}>
            {t("forms.formActions.cancel")}
          </Button>
          <Button type="button" size="sm" onClick={onSave} disabled={saving || !hasContent} className="gap-2">
            <IconDeviceFloppy className="size-4" />
            {saving ? t("chatFlow.aiChat.saving") : t("chatFlow.aiChat.saveAndOpen")}
          </Button>
        </div>
      </div>

      {!hasContent && (
        <SectionCard variant="blue">
          <SectionCard.Header
            icon={IconBulb}
            title={t("chatFlow.aiChat.tipTitle")}
            description={t("chatFlow.aiChat.tipDesc")}
          />
        </SectionCard>
      )}

      {hasContent && (
        <>
          <FlowMetadataCard flow={flow} />
          <FlowGraphCard flow={flow} />
        </>
      )}
    </div>
  );
}

function FlowMetadataCard({ flow }: { readonly flow: ChatFlowGeneration }) {
  const { t } = useTranslation();
  return (
    <SectionCard variant="violet">
      <SectionCard.Header
        icon={IconBolt}
        title={t("chatFlow.aiChat.metaTitle")}
        description={t("chatFlow.aiChat.metaDesc")}
      />
      <SectionCard.Content>
        <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3 text-sm">
          <MetaRow label={t("chatFlow.aiChat.guardrail")} value={flow.guardrailMethod ?? "—"} />
          <MetaRow label={t("chatFlow.aiChat.triggerMode")} value={flow.triggerMode ?? "—"} />
          <MetaRow
            label={t("chatFlow.aiChat.enabled")}
            value={flow.enabled === 0 ? t("chatFlow.aiChat.no") : t("chatFlow.aiChat.yes")}
          />
          <MetaRow label={t("chatFlow.aiChat.nodeCount")} value={String(flow.nodes?.length ?? 0)} />
          {flow.triggerDescription && (
            <div className="sm:col-span-2">
              <dt className="text-xs uppercase tracking-wide text-muted-foreground">
                {t("chatFlow.aiChat.triggerDescription")}
              </dt>
              <dd className="text-sm mt-1">{flow.triggerDescription}</dd>
            </div>
          )}
        </dl>
      </SectionCard.Content>
    </SectionCard>
  );
}

function MetaRow({ label, value }: { readonly label: string; readonly value: string }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="text-sm mt-1 font-medium">{value}</dd>
    </div>
  );
}

function FlowGraphCard({ flow }: { readonly flow: ChatFlowGeneration }) {
  const { t } = useTranslation();
  const ordered = useMemo(() => orderNodesForPreview(flow.nodes ?? [], flow.edges ?? []), [flow.nodes, flow.edges]);
  return (
    <SectionCard variant="emerald">
      <SectionCard.Header
        icon={IconBook2}
        title={t("chatFlow.aiChat.graphTitle")}
        description={t("chatFlow.aiChat.graphDesc")}
      />
      <SectionCard.Content>
        <ol className="space-y-2">
          {ordered.map((node, index) => (
            <li key={node.id} className="flex items-start gap-3">
              <div className="shrink-0 mt-0.5">
                <NodeIcon type={node.type} />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-xs uppercase tracking-wide font-semibold text-muted-foreground">
                    {index + 1}.
                  </span>
                  <span className="text-sm font-semibold truncate">{node.label || node.id}</span>
                  <NodeTypeBadge type={node.type} />
                </div>
                <NodeDetails node={node} />
                <NodeBranches node={node} edges={flow.edges ?? []} allNodes={flow.nodes ?? []} />
              </div>
            </li>
          ))}
        </ol>
      </SectionCard.Content>
    </SectionCard>
  );
}

function NodeIcon({ type }: { readonly type: ChatFlowNodeType }) {
  const cls = "size-5";
  switch (type) {
    case "start": return <IconPlayerPlayFilled className={`${cls} text-emerald-500`} />;
    case "end": return <IconFlag3 className={`${cls} text-rose-500`} />;
    case "aiQuestion": return <IconBook2 className={`${cls} text-blue-500`} />;
    case "condition": return <IconDatabase className={`${cls} text-amber-500`} />;
    case "functionCall": return <IconBolt className={`${cls} text-orange-500`} />;
  }
}

function NodeTypeBadge({ type }: { readonly type: ChatFlowNodeType }) {
  const palette: Record<ChatFlowNodeType, string> = {
    start: "bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 border-emerald-500/30",
    end: "bg-rose-500/15 text-rose-700 dark:text-rose-300 border-rose-500/30",
    aiQuestion: "bg-blue-500/15 text-blue-700 dark:text-blue-300 border-blue-500/30",
    condition: "bg-amber-500/15 text-amber-700 dark:text-amber-300 border-amber-500/30",
    functionCall: "bg-orange-500/15 text-orange-700 dark:text-orange-300 border-orange-500/30",
  };
  return (
    <span className={`text-[10px] uppercase tracking-wide font-semibold border rounded px-1.5 py-0.5 ${palette[type]}`}>
      {type}
    </span>
  );
}

function NodeDetails({ node }: { readonly node: ChatFlowNodeGeneration }) {
  const rows: Array<[string, string]> = [];
  if (node.aiInstruction) rows.push(["instruction", truncate(node.aiInstruction, 200)]);
  if (node.outputVariable) rows.push(["variable", node.outputVariable]);
  if (node.validationRule && node.validationRule !== "none") rows.push(["validation", node.validationRule]);
  if (node.conditionExpression) rows.push(["if", node.conditionExpression]);
  if (node.toolSource) rows.push(["source", node.toolSource]);
  if (node.functionName) rows.push(["function", node.functionName]);
  if (node.mcpServerId) rows.push(["mcp server", node.mcpServerId]);
  if (rows.length === 0) return null;
  return (
    <dl className="mt-1 text-xs space-y-0.5">
      {rows.map(([k, v]) => (
        <div key={k} className="flex gap-1.5">
          <dt className="text-muted-foreground uppercase tracking-wide shrink-0">{k}:</dt>
          <dd className="min-w-0 break-words">{v}</dd>
        </div>
      ))}
    </dl>
  );
}

function NodeBranches({
  node,
  edges,
  allNodes,
}: {
  readonly node: ChatFlowNodeGeneration;
  readonly edges: ChatFlowEdgeGeneration[];
  readonly allNodes: ChatFlowNodeGeneration[];
}) {
  const outgoing = edges.filter((e) => e.source === node.id);
  if (outgoing.length === 0) return null;
  const labelOf = (id: string) => allNodes.find((n) => n.id === id)?.label || id;
  return (
    <ul className="mt-1 text-xs space-y-0.5">
      {outgoing.map((e, i) => (
        <li key={`${e.source}-${e.target}-${i}`} className="flex items-center gap-1.5 text-muted-foreground">
          <IconArrowRight className="size-3.5 shrink-0" />
          {e.sourceHandle && (
            <span className="text-[10px] uppercase font-semibold tracking-wide rounded px-1 py-0.5 border border-current/30">
              {e.sourceHandle}
            </span>
          )}
          <span className="truncate">{labelOf(e.target)}</span>
        </li>
      ))}
    </ul>
  );
}

/* ───────────────── Helpers ───────────────── */

function emptyFlow(): ChatFlowGeneration {
  return {
    name: "",
    description: "",
    guardrailMethod: "LLM_JUDGE",
    triggerDescription: "",
    triggerMode: "ONCE",
    enabled: 1,
    nodes: [],
    edges: [],
  };
}

/**
 * Topological-ish ordering: walk forward from `start`, breadth-first,
 * preserving discovery order. Nodes not reachable from start are
 * appended at the end so the user can still see them. Used purely for
 * the visual list — the persisted edge graph is the source of truth.
 */
function orderNodesForPreview(
  nodes: ChatFlowNodeGeneration[],
  edges: ChatFlowEdgeGeneration[],
): ChatFlowNodeGeneration[] {
  if (nodes.length === 0) return [];
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const start = nodes.find((n) => n.type === "start") ?? nodes[0];
  const seen = new Set<string>();
  const out: ChatFlowNodeGeneration[] = [];
  const queue = [start.id];
  while (queue.length > 0) {
    const id = queue.shift()!;
    if (seen.has(id)) continue;
    seen.add(id);
    const n = byId.get(id);
    if (!n) continue;
    out.push(n);
    for (const e of edges.filter((e) => e.source === id)) queue.push(e.target);
  }
  for (const n of nodes) if (!seen.has(n.id)) out.push(n);
  return out;
}

/**
 * Serialize the LLM-shape into the form the React Flow editor expects.
 * Adds deterministic positions so the editor renders something
 * intelligible the first time it opens. Users can rearrange visually.
 */
function serializeForEditor(flow: ChatFlowGeneration): string {
  const ordered = orderNodesForPreview(flow.nodes ?? [], flow.edges ?? []);
  const indexById = new Map(ordered.map((n, i) => [n.id, i]));
  const editorNodes = ordered.map((n) => ({
    id: n.id,
    type: n.type,
    position: { x: NODE_X_ORIGIN + (indexById.get(n.id) ?? 0) * NODE_X_STEP, y: NODE_Y_ORIGIN },
    data: {
      label: n.label,
      type: n.type,
      aiInstruction: n.aiInstruction ?? undefined,
      outputVariable: n.outputVariable ?? undefined,
      validationRule: n.validationRule ?? undefined,
      conditionExpression: n.conditionExpression ?? undefined,
      toolSource: n.toolSource ?? undefined,
      functionName: n.functionName ?? undefined,
      mcpServerId: n.mcpServerId ?? undefined,
    },
  }));
  const editorEdges = (flow.edges ?? []).map((e, i) => ({
    id: `e-${e.source}-${e.target}-${i}`,
    source: e.source,
    target: e.target,
    sourceHandle: e.sourceHandle ?? null,
    targetHandle: null,
    label: e.label ?? null,
  }));
  return JSON.stringify({ nodes: editorNodes, edges: editorEdges }, null, 2);
}

function truncate(s: string, max: number): string {
  if (s.length <= max) return s;
  return s.slice(0, max - 1).trimEnd() + "…";
}
