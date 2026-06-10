import { IconForms, IconPlus, IconSparkles, IconTrash, IconX } from "@tabler/icons-react";
import type { Edge, Node } from "@xyflow/react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { useAIAgentSlots } from "@/api/queries/ai-agent-slot.queries";
import { useAiAgent } from "@/api/queries/ai-agent.queries";
import { useChatFlows } from "@/api/queries/chat-flow.queries";
import { useRoutines } from "@/api/queries/routine.queries";
import { useChatWebhooks } from "@/api/queries/webhook.queries";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import type { NativeToolGroup } from "@/models/genai/native-tool.model";
import type { TurMcpServer } from "@/models/mcp/mcp-server.model";
import { TurChatFlowService } from "@/services/agent/chat-flow.service";
import { TurNativeToolService } from "@/services/genai/native-tool.service";
import { TurMcpServerService } from "@/services/mcp/mcp-server.service";

import { ensureSwitchOptions, newSwitchOptionId } from "../chat-flow.serialize";
import { planFormConversion, type FormConversionPlan } from "../chat-flow.form-converter";
import type { FlowCompletionMode, FlowFormField, FlowNodeData, FlowNodeType, FlowNodeVariant, FlowOnJudgeReject, FlowSlotOperation, FlowSwitchOption, FlowToolSource } from "../types";

/** T107 — widget hints offered for a formCapture form field. */
const FORM_FIELD_TYPES = ["text", "email", "tel", "number", "date", "textarea", "select"] as const;

const nativeToolService = new TurNativeToolService();
const mcpServerService = new TurMcpServerService();
const chatFlowService = new TurChatFlowService();

/**
 * Right-hand properties panel. Re-renders whenever the selected node changes; all edits flow back
 * through {@code onChange} so the parent keeps the single source of truth for the flow.
 *
 * @since 2026.2.4
 */

interface FlowPropertiesProps {
  readonly node: Node<FlowNodeData> | null;
  /** Full node list — needed by the T234 "convert run to form" planner. */
  readonly nodes: Node<FlowNodeData>[];
  /** Full edge list — needed by the T234 planner. */
  readonly edges: Edge[];
  readonly onChange: (nodeId: string, patch: Partial<FlowNodeData>) => void;
  /** T234 — replace the whole graph after a form conversion + select a node. */
  readonly onApplyGraph: (nodes: Node<FlowNodeData>[], edges: Edge[], selectId: string | null) => void;
  readonly onClose: () => void;
  readonly agentId: string;
  /** Id of the chat flow currently being edited — excluded from the Sub Flow picker to prevent self-reference. */
  readonly currentFlowId?: string;
}

const VALIDATION_OPTIONS = ["none", "email", "phone", "url", "number", "date"];
const TOOL_SOURCE_OPTIONS: FlowToolSource[] = ["NATIVE", "MCP"];
const SLOT_OPERATION_OPTIONS: FlowSlotOperation[] = ["SET", "DELETE"];
const ON_JUDGE_REJECT_OPTIONS: FlowOnJudgeReject[] = ["reprompt", "advance_with_literal", "block"];
const COMPLETION_MODE_OPTIONS: FlowCompletionMode[] = ["mark_done", "remove"];

export function FlowProperties({ node, nodes, edges, onChange, onApplyGraph, onClose, agentId, currentFlowId }: FlowPropertiesProps) {
  const { t } = useTranslation();
  const [nativeToolGroups, setNativeToolGroups] = useState<NativeToolGroup[]>([]);
  const [mcpServers, setMcpServers] = useState<TurMcpServer[]>([]);
  const { data: chatFlows } = useChatFlows(agentId);
  const subFlowOptions = useMemo(
    () => (chatFlows ?? []).filter((f) => f.id && f.id !== currentFlowId),
    [chatFlows, currentFlowId],
  );
  // Persona-switch nodes can only target a persona that is in the agent's
  // catalog — same allow-list the backend enforces. Loading the agent here
  // is cached by react-query, no extra round-trips per selected node.
  const { data: agent } = useAiAgent(agentId);
  const personaOptions = useMemo(() => agent?.personas ?? [], [agent]);
  // Slots catalogue — outputVariable on askQuestion nodes must reference
  // one of these so captured values land in a stable, typed schema.
  const { data: slots } = useAIAgentSlots(agentId);
  const slotOptions = useMemo(() => slots ?? [], [slots]);
  // Routines catalog — populates the scheduleAgent node's routine dropdown.
  // Deployment-wide, not agent-scoped (same routine usable across agents).
  const { data: routines } = useRoutines();
  const routineOptions = useMemo(
    () => (routines ?? []).filter((r) => r.enabled !== false),
    [routines],
  );
  // Webhook catalog — populates the webhook node's dropdown. Deployment-wide.
  const { data: webhooks } = useChatWebhooks();
  const webhookOptions = useMemo(
    () => (webhooks ?? []).filter((w) => w.enabled !== false),
    [webhooks],
  );

  // T234 — plan for collapsing the run starting at the selected node into a
  // native form. Null when the node isn't the head of a collapsible 2+ chain.
  const [convertOpen, setConvertOpen] = useState(false);
  const formConversionPlan = useMemo<FormConversionPlan | null>(
    () => (node ? planFormConversion(nodes, edges, node.id) : null),
    [node, nodes, edges],
  );

  useEffect(() => {
    nativeToolService.query().then(setNativeToolGroups).catch(() => setNativeToolGroups([]));
    mcpServerService
      .query()
      .then((servers) => setMcpServers(servers.filter((s) => s.enabled === 1)))
      .catch(() => setMcpServers([]));
  }, []);

  if (!node) {
    return (
      <aside className="flex h-full w-80 flex-col border-l bg-muted/20 p-4 text-sm text-muted-foreground">
        {t("chatFlow.properties.empty")}
      </aside>
    );
  }

  const data = node.data as FlowNodeData;
  const type = data.type;
  const update = (patch: Partial<FlowNodeData>) => onChange(node.id, patch);

  return (
    <aside className="flex h-full w-80 flex-col border-l bg-muted/10">
      <header className="flex items-center justify-between border-b px-4 py-3">
        <div>
          <div className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
            {t("chatFlow.properties.header")}
          </div>
          <div className="text-sm font-semibold">{data.label || t(typeLabelKey(type))}</div>
        </div>
        <Button variant="ghost" size="icon" onClick={onClose} aria-label={t("chatFlow.properties.close")}>
          <IconX className="size-4" />
        </Button>
      </header>

      <div className="flex flex-col gap-4 overflow-auto p-4 text-sm">
        <div className="flex flex-col gap-1">
          <Label htmlFor="flow-node-label">{t("chatFlow.properties.stepName")}</Label>
          <Input
            id="flow-node-label"
            value={data.label ?? ""}
            onChange={(e) => update({ label: e.target.value })}
            placeholder={t("chatFlow.properties.stepNamePlaceholder")}
          />
        </div>

        {(type === "aiQuestion" || type === "formCapture" || type === "functionCall") && (
          <div className="flex flex-col gap-1">
            <Label htmlFor="flow-node-instruction">{t("chatFlow.properties.aiInstruction")}</Label>
            <Textarea
              id="flow-node-instruction"
              rows={5}
              value={data.aiInstruction ?? ""}
              onChange={(e) => update({ aiInstruction: e.target.value })}
              placeholder={t("chatFlow.properties.aiInstructionPlaceholder")}
            />
            <span className="text-[11px] text-muted-foreground">
              {t("chatFlow.properties.aiInstructionHint")}
            </span>
          </div>
        )}

        {(type === "aiQuestion" || type === "formCapture") && (
          <>
            {formConversionPlan && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="justify-start gap-2 border-indigo-300 text-indigo-700 dark:border-indigo-800 dark:text-indigo-300"
                onClick={() => setConvertOpen(true)}
              >
                <IconForms className="size-4" />
                {t("chatFlow.properties.convertToForm", {
                  defaultValue: "Convert {{count}} questions to a form",
                  count: formConversionPlan.runLength,
                })}
              </Button>
            )}

            {node && formConversionPlan && (
              <ConvertToFormDialog
                open={convertOpen}
                plan={formConversionPlan}
                agentId={agentId}
                onOpenChange={setConvertOpen}
                onConfirm={(fields) => {
                  // T236 — the dialog may have tidied the field labels; apply
                  // them onto the planned form node before committing the graph.
                  const formNodeId = formConversionPlan.formNode.id;
                  const nextNodes = formConversionPlan.nextNodes.map((n) =>
                    n.id === formNodeId
                      ? { ...n, data: { ...n.data, formFields: fields } }
                      : n,
                  );
                  onApplyGraph(nextNodes, formConversionPlan.nextEdges, formNodeId);
                  setConvertOpen(false);
                }}
              />
            )}

            <div className="flex flex-col gap-1">
              <Label>{t("chatFlow.properties.outputVariable")}</Label>
              <Select
                value={data.outputVariable ?? ""}
                onValueChange={(value) => update({ outputVariable: value || undefined })}
              >
                <SelectTrigger>
                  <SelectValue placeholder={t("chatFlow.properties.outputVariablePlaceholder")} />
                </SelectTrigger>
                <SelectContent>
                  {slotOptions.length === 0 ? (
                    <div className="px-2 py-1.5 text-xs text-muted-foreground">
                      {t("chatFlow.properties.outputVariableEmpty")}
                    </div>
                  ) : (
                    slotOptions.map((slot) => (
                      <SelectItem key={slot.id} value={slot.name}>
                        <span className="font-mono text-[12px]">{slot.name}</span>
                        <span className="ml-2 text-muted-foreground">— {t(`aiAgent.slot.types.${slot.type}`)}</span>
                      </SelectItem>
                    ))
                  )}
                </SelectContent>
              </Select>
              <span className="text-[11px] text-muted-foreground">
                {t("chatFlow.properties.outputVariableHint")}
              </span>
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="flow-node-override" className="flex items-start gap-2 cursor-pointer">
                <Checkbox
                  id="flow-node-override"
                  checked={data.overrideExistingValue ?? false}
                  onCheckedChange={(checked) =>
                    update({ overrideExistingValue: checked === true ? true : undefined })
                  }
                  className="mt-0.5"
                />
                <div className="flex flex-col gap-0.5">
                  <span className="text-sm font-medium">
                    {t("chatFlow.properties.overrideExistingValue")}
                  </span>
                  <span className="text-[11px] text-muted-foreground">
                    {t("chatFlow.properties.overrideExistingValueHint")}
                  </span>
                </div>
              </label>
            </div>

            <div className="flex flex-col gap-1">
              <Label>{t("chatFlow.properties.validationRule")}</Label>
              <Select
                value={data.validationRule ?? "none"}
                onValueChange={(value) => update({ validationRule: value === "none" ? undefined : value })}
              >
                <SelectTrigger>
                  <SelectValue placeholder={t("chatFlow.properties.validationNone")} />
                </SelectTrigger>
                <SelectContent>
                  {VALIDATION_OPTIONS.map((option) => (
                    <SelectItem key={option} value={option}>
                      {t(`chatFlow.properties.validation.${option}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="flex flex-col gap-1">
              <Label htmlFor="flow-node-inline-options">
                {t("chatFlow.properties.inlineOptions", { defaultValue: "Suggested chips (one per line)" })}
              </Label>
              <Textarea
                id="flow-node-inline-options"
                rows={4}
                value={(data.inlineOptions ?? []).join("\n")}
                onChange={(e) => {
                  // Split on newlines, trim, drop empties. Empty result → clear the field entirely
                  // so it doesn't get serialised as `[]` in the export JSON.
                  const labels = e.target.value
                    .split(/\r?\n/)
                    .map((l) => l.trim())
                    .filter((l) => l.length > 0);
                  update({ inlineOptions: labels.length > 0 ? labels : undefined });
                }}
                placeholder={"Sim\nNão\nTalvez"}
              />
              <span className="text-[11px] text-muted-foreground">
                {t("chatFlow.properties.inlineOptionsHint", {
                  defaultValue:
                    "Rendered as clickable chips under the assistant message. Clicking sends the label as the user reply — does not branch the flow.",
                })}
              </span>
            </div>

            {type === "formCapture" && (
              <FormFieldsEditor
                fields={data.formFields ?? []}
                slotOptions={slotOptions}
                onChange={(fields) =>
                  update({ formFields: fields.length > 0 ? fields : undefined })
                }
              />
            )}

            {type === "aiQuestion" && (
              <div className="flex flex-col gap-1">
                <Label>
                  {t("chatFlow.properties.onJudgeReject", {
                    defaultValue: "When the judge rejects the answer",
                  })}
                </Label>
                <Select
                  value={data.onJudgeReject ?? "reprompt"}
                  onValueChange={(value) =>
                    update({
                      // Persist "reprompt" as undefined so it stays
                      // implicit in the export JSON — preserves the
                      // pre-2026.2.31 file shape for flows that don't
                      // opt into either of the other policies.
                      onJudgeReject:
                        value === "reprompt" ? undefined : (value as FlowOnJudgeReject),
                    })
                  }
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {ON_JUDGE_REJECT_OPTIONS.map((option) => (
                      <SelectItem key={option} value={option}>
                        {t(`chatFlow.properties.onJudgeRejectOptions.${option}`, {
                          defaultValue: option,
                        })}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <span className="text-[11px] text-muted-foreground">
                  {t("chatFlow.properties.onJudgeRejectHint", {
                    defaultValue:
                      "Only applies when the agent's guardrailMethod is LLM_JUDGE. Default reprompts the user when the judge produced a substantive redirect.",
                  })}
                </span>
              </div>
            )}

            {type === "aiQuestion" && (
              <div className="flex flex-col gap-1">
                <div className="flex items-center justify-between gap-2">
                  <Label htmlFor="flow-node-tools-enabled">
                    {t("chatFlow.properties.toolsEnabled", {
                      defaultValue: "Allow tool calls on this node",
                    })}
                  </Label>
                  {/*
                    Tri-state mapped onto a boolean Switch:
                      data.toolsEnabled === false → switch OFF (tools stripped)
                      data.toolsEnabled === true / undefined → switch ON (tools allowed)
                    Toggling ON clears the field to undefined so the export JSON
                    stays compact for flows that keep the default behavior.
                  */}
                  <Checkbox
                    id="flow-node-tools-enabled"
                    checked={data.toolsEnabled !== false}
                    onCheckedChange={(checked) =>
                      update({
                        toolsEnabled: checked === false ? false : undefined,
                      })
                    }
                  />
                </div>
                <span className="text-[11px] text-muted-foreground">
                  {t("chatFlow.properties.toolsEnabledHint", {
                    defaultValue:
                      "When off, the agent's tool callbacks (MCP / native / custom) are stripped on this node. Use to gate the LLM from preemptively calling a tool before a slot is captured. Replaces the legacy \"NÃO CHAME NENHUMA TOOL\" sentinel in aiInstruction.",
                  })}
                </span>
              </div>
            )}

            {type === "aiQuestion" && data.toolsEnabled !== false && (
              <div className="flex flex-col gap-1">
                <Label htmlFor="flow-node-required-tools">
                  {t("chatFlow.properties.requiredTools", {
                    defaultValue: "Required tools (one per line)",
                  })}
                </Label>
                <Textarea
                  id="flow-node-required-tools"
                  rows={3}
                  value={(data.requiredTools ?? []).join("\n")}
                  onChange={(e) => {
                    // Mirrors the inlineOptions editor: split on newlines,
                    // trim, drop empties. Empty result → clear the field
                    // entirely so the export JSON stays compact for nodes
                    // that don't pin any tool.
                    const names = e.target.value
                      .split(/\r?\n/)
                      .map((l) => l.trim())
                      .filter((l) => l.length > 0);
                    update({ requiredTools: names.length > 0 ? names : undefined });
                  }}
                  placeholder={"compor_proposta_in_company\nsearch_site"}
                />
                <span className="text-[11px] text-muted-foreground">
                  {t("chatFlow.properties.requiredToolsHint", {
                    defaultValue:
                      "Tool names that must reach the LLM on this node, even when the tool-prefilter (§IV.2 / T29) would otherwise drop them due to low similarity with the user message. Use one name per line — must match the @Tool name, MCP tool name, or custom-tool name.",
                  })}
                </span>
              </div>
            )}

            <NodeAbTestSection data={data} onChange={update} />
          </>
        )}

        {type === "functionCall" && (
          <>
            <div className="flex flex-col gap-1">
              <Label>{t("chatFlow.properties.toolSource")}</Label>
              <Select
                value={data.toolSource ?? "NATIVE"}
                onValueChange={(value) =>
                  update({
                    toolSource: value as FlowToolSource,
                    /* Reset the dependent fields when the source changes so we don't
                       carry an MCP server id into a NATIVE selection (or vice-versa). */
                    functionName: undefined,
                    mcpServerId: undefined,
                  })
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {TOOL_SOURCE_OPTIONS.map((option) => (
                    <SelectItem key={option} value={option}>
                      {t(`chatFlow.properties.toolSourceOptions.${option}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {(data.toolSource ?? "NATIVE") === "NATIVE" ? (
              <div className="flex flex-col gap-1">
                <Label>{t("chatFlow.properties.nativeTool")}</Label>
                <Select
                  value={data.functionName ?? ""}
                  onValueChange={(value) => update({ functionName: value || undefined })}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t("chatFlow.properties.nativeToolPlaceholder")} />
                  </SelectTrigger>
                  <SelectContent>
                    {nativeToolGroups.flatMap((group) =>
                      group.tools.map((tool) => (
                        <SelectItem key={tool.name} value={tool.name}>
                          <span className="font-mono text-[12px]">{tool.name}</span>
                          <span className="ml-2 text-muted-foreground">— {group.title}</span>
                        </SelectItem>
                      )),
                    )}
                  </SelectContent>
                </Select>
                <span className="text-[11px] text-muted-foreground">
                  {t("chatFlow.properties.nativeToolHint")}
                </span>
              </div>
            ) : (
              <div className="flex flex-col gap-1">
                <Label>{t("chatFlow.properties.mcpServer")}</Label>
                <Select
                  value={data.mcpServerId ?? ""}
                  onValueChange={(value) => update({ mcpServerId: value || undefined })}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t("chatFlow.properties.mcpServerPlaceholder")} />
                  </SelectTrigger>
                  <SelectContent>
                    {mcpServers.map((server) => (
                      <SelectItem key={server.id} value={server.id}>
                        {server.title}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <span className="text-[11px] text-muted-foreground">
                  {t("chatFlow.properties.mcpServerHint")}
                </span>
              </div>
            )}

            <ContinueOnFailureField data={data} onChange={update} />

            {/* T72 — per-node A/B of the tool's input JSON template (aiInstruction). */}
            <NodeAbTestSection data={data} onChange={update} />
          </>
        )}

        {type === "webhook" && (
          <>
            <div className="flex flex-col gap-1">
              <Label>{t("chatFlow.properties.webhook.webhook")}</Label>
              <Select
                value={data.functionName ?? ""}
                onValueChange={(value) => update({ functionName: value || undefined })}
              >
                <SelectTrigger>
                  <SelectValue placeholder={t("chatFlow.properties.webhook.webhookPlaceholder")} />
                </SelectTrigger>
                <SelectContent>
                  {webhookOptions.map((w) => (
                    <SelectItem key={w.id} value={w.name}>
                      <span className="font-mono text-[12px]">{w.name}</span>
                      {w.slotTrigger && (
                        <span className="ml-2 text-muted-foreground">— {w.slotTrigger}</span>
                      )}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <span className="text-[11px] text-muted-foreground">
                {webhookOptions.length === 0
                  ? t("chatFlow.properties.webhook.webhookEmpty")
                  : t("chatFlow.properties.webhook.webhookHint")}
              </span>
            </div>

            <ContinueOnFailureField data={data} onChange={update} />
          </>
        )}

        {type === "condition" && (
          <div className="flex flex-col gap-1">
            <Label htmlFor="flow-node-condition">{t("chatFlow.properties.condition")}</Label>
            <Textarea
              id="flow-node-condition"
              rows={3}
              value={data.conditionExpression ?? ""}
              onChange={(e) => update({ conditionExpression: e.target.value })}
              placeholder="output.available == true"
            />
          </div>
        )}

        {type === "scheduleAgent" && (
          <>
            <div className="flex flex-col gap-1">
              <Label>{t("chatFlow.properties.scheduleAgent.routineId")}</Label>
              <Select
                value={data.routineId ?? ""}
                onValueChange={(value) => {
                  const picked = routineOptions.find((r) => r.id === value);
                  update({
                    routineId: value || undefined,
                    routineName: picked?.name,
                  });
                }}
              >
                <SelectTrigger>
                  <SelectValue
                    placeholder={t("chatFlow.properties.scheduleAgent.routineIdPlaceholder")}
                  />
                </SelectTrigger>
                <SelectContent>
                  {routineOptions.map((r) => (
                    <SelectItem key={r.id} value={r.id ?? ""}>
                      <span className="font-mono text-[12px]">{r.name}</span>
                      {r.description && (
                        <span className="ml-2 text-muted-foreground">— {r.description}</span>
                      )}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {routineOptions.length === 0 && (
                <span className="text-[11px] text-muted-foreground">
                  {t("chatFlow.properties.scheduleAgent.routineIdEmpty")}
                </span>
              )}
              {routineOptions.length > 0 && (
                <span className="text-[11px] text-muted-foreground">
                  {t("chatFlow.properties.scheduleAgent.routineIdHint")}
                </span>
              )}
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="flow-node-routine-payload">
                {t("chatFlow.properties.scheduleAgent.payloadTemplate")}
              </Label>
              <Textarea
                id="flow-node-routine-payload"
                rows={3}
                value={data.aiInstruction ?? ""}
                onChange={(e) => update({ aiInstruction: e.target.value })}
                placeholder='{"cargo":"{{cargo}}"}'
              />
              <span className="text-[11px] text-muted-foreground">
                {t("chatFlow.properties.scheduleAgent.payloadTemplateHint")}
              </span>
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="flow-node-routine-output">
                {t("chatFlow.properties.scheduleAgent.outputVariable")}
              </Label>
              <Input
                id="flow-node-routine-output"
                value={data.outputVariable ?? ""}
                onChange={(e) => update({ outputVariable: e.target.value || undefined })}
                placeholder="proposta_pdf_url"
              />
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="flow-node-routine-timeout">
                {t("chatFlow.properties.scheduleAgent.timeoutMs")}
              </Label>
              <Input
                id="flow-node-routine-timeout"
                type="number"
                min={1000}
                step={1000}
                value={data.routineTimeoutMs ?? ""}
                onChange={(e) => {
                  const v = e.target.value;
                  update({ routineTimeoutMs: v === "" ? undefined : Number(v) });
                }}
                placeholder="60000"
              />
              <span className="text-[11px] text-muted-foreground">
                {t("chatFlow.properties.scheduleAgent.timeoutMsHint")}
              </span>
            </div>

            <ContinueOnFailureField data={data} onChange={update} />

            {/* T72 — per-node A/B of the routine payload template (aiInstruction). */}
            <NodeAbTestSection data={data} onChange={update} />
          </>
        )}

        {type === "subFlow" && (
          <div className="flex flex-col gap-1">
            <Label>{t("chatFlow.properties.subFlow")}</Label>
            <Select
              value={data.subFlowId ?? ""}
              onValueChange={(value) => {
                const selected = subFlowOptions.find((f) => f.id === value);
                update({
                  subFlowId: value || undefined,
                  subFlowName: selected?.name ?? undefined,
                });
              }}
            >
              <SelectTrigger>
                <SelectValue placeholder={t("chatFlow.properties.subFlowPlaceholder")} />
              </SelectTrigger>
              <SelectContent>
                {subFlowOptions.length === 0 ? (
                  <div className="px-2 py-1.5 text-xs text-muted-foreground">
                    {t("chatFlow.properties.subFlowEmpty")}
                  </div>
                ) : (
                  subFlowOptions.map((flow) => (
                    <SelectItem key={flow.id} value={flow.id as string}>
                      {flow.name}
                    </SelectItem>
                  ))
                )}
              </SelectContent>
            </Select>
            <span className="text-[11px] text-muted-foreground">
              {t("chatFlow.properties.subFlowHint")}
            </span>
          </div>
        )}

        {(type === "switch" || type === "subFlowSwitch") && (
          <>
            <div className="flex flex-col gap-1">
              <Label>{t("chatFlow.properties.switchVariable")}</Label>
              <Select
                value={data.switchVariable ?? ""}
                onValueChange={(value) => update({ switchVariable: value || undefined })}
              >
                <SelectTrigger>
                  <SelectValue placeholder={t("chatFlow.properties.switchVariablePlaceholder")} />
                </SelectTrigger>
                <SelectContent>
                  {slotOptions.length === 0 ? (
                    <div className="px-2 py-1.5 text-xs text-muted-foreground">
                      {t("chatFlow.properties.switchVariableEmpty")}
                    </div>
                  ) : (
                    slotOptions.map((slot) => (
                      <SelectItem key={slot.id} value={slot.name}>
                        <span className="font-mono text-[12px]">{slot.name}</span>
                        <span className="ml-2 text-muted-foreground">— {t(`aiAgent.slot.types.${slot.type}`)}</span>
                      </SelectItem>
                    ))
                  )}
                </SelectContent>
              </Select>
              <span className="text-[11px] text-muted-foreground">
                {t("chatFlow.properties.switchVariableHint")}
              </span>
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="flow-node-switch-override" className="flex items-start gap-2 cursor-pointer">
                <Checkbox
                  id="flow-node-switch-override"
                  checked={data.overrideExistingValue ?? false}
                  onCheckedChange={(checked) =>
                    update({ overrideExistingValue: checked === true ? true : undefined })
                  }
                  className="mt-0.5"
                />
                <div className="flex flex-col gap-0.5">
                  <span className="text-sm font-medium">
                    {t("chatFlow.properties.overrideExistingValue")}
                  </span>
                  <span className="text-[11px] text-muted-foreground">
                    {t("chatFlow.properties.switchOverrideHint")}
                  </span>
                </div>
              </label>
            </div>

            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between">
                <Label>{t("chatFlow.properties.switchOptions")}</Label>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    const next = [
                      ...ensureSwitchOptions(data),
                      { id: newSwitchOptionId(), label: "" },
                    ];
                    update({ switchOptions: next });
                  }}
                >
                  <IconPlus className="size-4" />
                  {t("chatFlow.properties.switchAddOption")}
                </Button>
              </div>
              <SwitchOptionsEditor
                options={ensureSwitchOptions(data)}
                onChange={(next) => update({ switchOptions: next })}
                subFlowOptions={type === "subFlowSwitch" ? subFlowOptions : undefined}
              />
              <span className="text-[11px] text-muted-foreground">
                {t(type === "subFlowSwitch"
                  ? "chatFlow.properties.subFlowSwitchOptionsHint"
                  : "chatFlow.properties.switchOptionsHint")}
              </span>
            </div>
          </>
        )}

        {type === "persona" && (
          <div className="flex flex-col gap-1">
            <Label>{t("chatFlow.properties.persona")}</Label>
            <Select
              value={data.personaId ?? ""}
              onValueChange={(value) => {
                const selected = personaOptions.find((p) => p.id === value);
                update({
                  personaId: value || undefined,
                  personaName: selected?.name ?? undefined,
                });
              }}
            >
              <SelectTrigger>
                <SelectValue placeholder={t("chatFlow.properties.personaPlaceholder")} />
              </SelectTrigger>
              <SelectContent>
                {personaOptions.length === 0 ? (
                  <div className="px-2 py-1.5 text-xs text-muted-foreground">
                    {t("chatFlow.properties.personaEmpty")}
                  </div>
                ) : (
                  personaOptions.map((p) => (
                    <SelectItem key={p.id} value={p.id}>
                      {p.name}
                    </SelectItem>
                  ))
                )}
              </SelectContent>
            </Select>
            <span className="text-[11px] text-muted-foreground">
              {t("chatFlow.properties.personaHint")}
            </span>
          </div>
        )}

        {type === "slot" && (
          <>
            <div className="flex flex-col gap-1">
              <Label>{t("chatFlow.properties.slotOperation")}</Label>
              <Select
                value={data.slotOperation ?? "SET"}
                onValueChange={(value) =>
                  update({
                    slotOperation: value as FlowSlotOperation,
                    /* When switching to DELETE the value/override become meaningless;
                       drop them so the export JSON stays tight. */
                    ...(value === "DELETE"
                      ? { slotValue: undefined, overrideExistingValue: undefined }
                      : {}),
                  })
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SLOT_OPERATION_OPTIONS.map((option) => (
                    <SelectItem key={option} value={option}>
                      {t(`chatFlow.properties.slotOperationOptions.${option}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <span className="text-[11px] text-muted-foreground">
                {t("chatFlow.properties.slotOperationHint")}
              </span>
            </div>

            <div className="flex flex-col gap-1">
              <Label>{t("chatFlow.properties.slotName")}</Label>
              <Select
                value={data.slotName ?? ""}
                onValueChange={(value) => update({ slotName: value || undefined })}
              >
                <SelectTrigger>
                  <SelectValue placeholder={t("chatFlow.properties.slotNamePlaceholder")} />
                </SelectTrigger>
                <SelectContent>
                  {slotOptions.length === 0 ? (
                    <div className="px-2 py-1.5 text-xs text-muted-foreground">
                      {t("chatFlow.properties.slotNameEmpty")}
                    </div>
                  ) : (
                    slotOptions.map((slot) => (
                      <SelectItem key={slot.id} value={slot.name}>
                        <span className="font-mono text-[12px]">{slot.name}</span>
                        <span className="ml-2 text-muted-foreground">— {t(`aiAgent.slot.types.${slot.type}`)}</span>
                      </SelectItem>
                    ))
                  )}
                </SelectContent>
              </Select>
              <span className="text-[11px] text-muted-foreground">
                {t("chatFlow.properties.slotNameHint")}
              </span>
            </div>

            {(data.slotOperation ?? "SET") === "SET" && (
              <>
                <div className="flex flex-col gap-1">
                  <Label htmlFor="flow-node-slot-value">{t("chatFlow.properties.slotValue")}</Label>
                  <Textarea
                    id="flow-node-slot-value"
                    rows={3}
                    value={data.slotValue ?? ""}
                    onChange={(e) => update({ slotValue: e.target.value || undefined })}
                    placeholder={t("chatFlow.properties.slotValuePlaceholder")}
                  />
                  <span className="text-[11px] text-muted-foreground">
                    {t("chatFlow.properties.slotValueHint")}
                  </span>
                </div>

                <div className="flex flex-col gap-1">
                  <label htmlFor="flow-node-slot-override" className="flex items-start gap-2 cursor-pointer">
                    <Checkbox
                      id="flow-node-slot-override"
                      checked={data.overrideExistingValue ?? false}
                      onCheckedChange={(checked) =>
                        update({ overrideExistingValue: checked === true ? true : undefined })
                      }
                      className="mt-0.5"
                    />
                    <div className="flex flex-col gap-0.5">
                      <span className="text-sm font-medium">
                        {t("chatFlow.properties.overrideExistingValue")}
                      </span>
                      <span className="text-[11px] text-muted-foreground">
                        {t("chatFlow.properties.slotOverrideHint")}
                      </span>
                    </div>
                  </label>
                </div>
              </>
            )}
          </>
        )}

        {type === "writeSlot" && (
          <>
            <div className="flex flex-col gap-1">
              <Label>{t("chatFlow.properties.slotName")}</Label>
              <Select
                value={data.slotName ?? ""}
                onValueChange={(value) => update({ slotName: value || undefined })}
              >
                <SelectTrigger>
                  <SelectValue placeholder={t("chatFlow.properties.slotNamePlaceholder")} />
                </SelectTrigger>
                <SelectContent>
                  {slotOptions.length === 0 ? (
                    <div className="px-2 py-1.5 text-xs text-muted-foreground">
                      {t("chatFlow.properties.slotNameEmpty")}
                    </div>
                  ) : (
                    slotOptions.map((slot) => (
                      <SelectItem key={slot.id} value={slot.name}>
                        <span className="font-mono text-[12px]">{slot.name}</span>
                        <span className="ml-2 text-muted-foreground">— {t(`aiAgent.slot.types.${slot.type}`)}</span>
                      </SelectItem>
                    ))
                  )}
                </SelectContent>
              </Select>
              <span className="text-[11px] text-muted-foreground">
                {t("chatFlow.properties.slotNameHint")}
              </span>
            </div>

            <div className="flex flex-col gap-1">
              <Label htmlFor="flow-node-write-slot-value">{t("chatFlow.properties.slotValue")}</Label>
              <Textarea
                id="flow-node-write-slot-value"
                rows={3}
                value={data.slotValue ?? ""}
                onChange={(e) => update({ slotValue: e.target.value || undefined })}
                placeholder={t("chatFlow.properties.writeSlotValuePlaceholder", {
                  defaultValue: "Hello {{name}}! Welcome to {{area_label}}.",
                })}
              />
              <span className="text-[11px] text-muted-foreground">
                {t("chatFlow.properties.writeSlotValueHint", {
                  defaultValue:
                    "Supports {{slotName}} template references — resolved at write time using the conversation's current variable map. Unknown slots resolve to the empty string.",
                })}
              </span>
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="flow-node-write-slot-override" className="flex items-start gap-2 cursor-pointer">
                <Checkbox
                  id="flow-node-write-slot-override"
                  checked={data.overrideExistingValue ?? false}
                  onCheckedChange={(checked) =>
                    update({ overrideExistingValue: checked === true ? true : undefined })
                  }
                  className="mt-0.5"
                />
                <div className="flex flex-col gap-0.5">
                  <span className="text-sm font-medium">
                    {t("chatFlow.properties.overrideExistingValue")}
                  </span>
                  <span className="text-[11px] text-muted-foreground">
                    {t("chatFlow.properties.slotOverrideHint")}
                  </span>
                </div>
              </label>
            </div>
          </>
        )}

        {type === "planningStep" && (
          <>
            <div className="flex flex-col gap-1">
              <Label htmlFor="flow-node-plan-goal">
                {t("chatFlow.properties.planningGoal", { defaultValue: "Planning goal" })}
              </Label>
              <Textarea
                id="flow-node-plan-goal"
                rows={4}
                value={data.aiInstruction ?? ""}
                onChange={(e) => update({ aiInstruction: e.target.value })}
                placeholder={t("chatFlow.properties.planningGoalPlaceholder", {
                  defaultValue: "Break the user's goal into 3-6 concrete, ordered steps.",
                })}
              />
              <span className="text-[11px] text-muted-foreground">
                {t("chatFlow.properties.planningGoalHint", {
                  defaultValue:
                    "The LLM decomposes this goal into a typed TODO list (id, title, status) written into the plan slot below.",
                })}
              </span>
            </div>

            <div className="flex flex-col gap-1">
              <Label htmlFor="flow-node-plan-slot">
                {t("chatFlow.properties.planSlot", { defaultValue: "Plan slot" })}
              </Label>
              <Input
                id="flow-node-plan-slot"
                value={data.outputVariable ?? ""}
                onChange={(e) => update({ outputVariable: e.target.value || undefined })}
                placeholder="__plan"
              />
              <span className="text-[11px] text-muted-foreground">
                {t("chatFlow.properties.planSlotHint", {
                  defaultValue:
                    "Conversation slot that receives the JSON plan (default __plan). Point a downstream Iterate Plan node at the same slot.",
                })}
              </span>
            </div>

            <div className="flex flex-col gap-1">
              <Label htmlFor="flow-node-plan-schema">
                {t("chatFlow.properties.planSchema", { defaultValue: "Item schema (optional)" })}
              </Label>
              <Textarea
                id="flow-node-plan-schema"
                rows={2}
                value={data.planSchema ?? ""}
                onChange={(e) => update({ planSchema: e.target.value || undefined })}
                placeholder="List<{id: string, title: string, status: 'pending'|'done'}>"
              />
              <span className="text-[11px] text-muted-foreground">
                {t("chatFlow.properties.planSchemaHint", {
                  defaultValue:
                    "Free-form hint steering the JSON item shape. The backend normalizes the model's output to {id, title, status} regardless — leave blank for the default.",
                })}
              </span>
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="flow-node-plan-override" className="flex items-start gap-2 cursor-pointer">
                <Checkbox
                  id="flow-node-plan-override"
                  checked={data.overrideExistingValue ?? false}
                  onCheckedChange={(checked) =>
                    update({ overrideExistingValue: checked === true ? true : undefined })
                  }
                  className="mt-0.5"
                />
                <div className="flex flex-col gap-0.5">
                  <span className="text-sm font-medium">
                    {t("chatFlow.properties.planRegenerate", {
                      defaultValue: "Always regenerate the plan",
                    })}
                  </span>
                  <span className="text-[11px] text-muted-foreground">
                    {t("chatFlow.properties.planRegenerateHint", {
                      defaultValue:
                        "When off (default), a plan already present in the slot is kept across re-entries. When on, the step regenerates the plan every time it runs.",
                    })}
                  </span>
                </div>
              </label>
            </div>
          </>
        )}

        {type === "iteratePlan" && (
          <>
            <div className="flex flex-col gap-1">
              <Label htmlFor="flow-node-iterate-slot">
                {t("chatFlow.properties.planSlot", { defaultValue: "Plan slot" })}
              </Label>
              <Input
                id="flow-node-iterate-slot"
                value={data.outputVariable ?? ""}
                onChange={(e) => update({ outputVariable: e.target.value || undefined })}
                placeholder="__plan"
              />
              <span className="text-[11px] text-muted-foreground">
                {t("chatFlow.properties.iteratePlanSlotHint", {
                  defaultValue:
                    "Slot to read the plan from — must match the Planning Step that produced it (default __plan).",
                })}
              </span>
            </div>

            <div className="flex flex-col gap-1">
              <Label>{t("chatFlow.properties.iteratePlanBody", { defaultValue: "Body sub-flow" })}</Label>
              <Select
                value={data.subFlowId ?? ""}
                onValueChange={(value) => {
                  const selected = subFlowOptions.find((f) => f.id === value);
                  update({
                    subFlowId: value || undefined,
                    subFlowName: selected?.name ?? undefined,
                  });
                }}
              >
                <SelectTrigger>
                  <SelectValue placeholder={t("chatFlow.properties.subFlowPlaceholder")} />
                </SelectTrigger>
                <SelectContent>
                  {subFlowOptions.length === 0 ? (
                    <div className="px-2 py-1.5 text-xs text-muted-foreground">
                      {t("chatFlow.properties.subFlowEmpty")}
                    </div>
                  ) : (
                    subFlowOptions.map((flow) => (
                      <SelectItem key={flow.id} value={flow.id as string}>
                        {flow.name}
                      </SelectItem>
                    ))
                  )}
                </SelectContent>
              </Select>
              <span className="text-[11px] text-muted-foreground">
                {t("chatFlow.properties.iteratePlanBodyHint", {
                  defaultValue:
                    "Runs once per pending item. The current item is exposed to the sub-flow as {{__planItemTitle}} / {{__planItemId}}.",
                })}
              </span>
            </div>

            <div className="flex flex-col gap-1">
              <Label>{t("chatFlow.properties.completionMode", { defaultValue: "On item completion" })}</Label>
              <Select
                value={data.completionMode ?? "mark_done"}
                onValueChange={(value) =>
                  update({
                    // Keep the default ("mark_done") implicit in the export JSON.
                    completionMode:
                      value === "mark_done" ? undefined : (value as FlowCompletionMode),
                  })
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {COMPLETION_MODE_OPTIONS.map((option) => (
                    <SelectItem key={option} value={option}>
                      {t(`chatFlow.properties.completionModeOptions.${option}`, {
                        defaultValue: option === "remove" ? "Remove the item" : "Mark the item done",
                      })}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <span className="text-[11px] text-muted-foreground">
                {t("chatFlow.properties.completionModeHint", {
                  defaultValue:
                    "After the body sub-flow finishes an item: mark it done (keeps an auditable plan) or remove it (queue-drain). Both terminate the loop.",
                })}
              </span>
            </div>
          </>
        )}
      </div>
    </aside>
  );
}

/**
 * Shared try/catch toggle for {@code functionCall} and {@code scheduleAgent}
 * nodes (T49). Renders a checkbox + hint that flips the
 * {@link FlowNodeData.continueOnFailure} flag. When checked, the engine
 * routes a tool/routine failure along the outgoing edge whose
 * {@code sourceHandle} is {@code "failure"} (visualised by T50 as a red
 * edge); when unchecked, the legacy lenient default applies — log + advance
 * to the first edge.
 */
function ContinueOnFailureField({
  data,
  onChange,
}: {
  readonly data: FlowNodeData;
  readonly onChange: (patch: Partial<FlowNodeData>) => void;
}) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor="flow-node-continue-on-failure" className="flex items-start gap-2 cursor-pointer">
        <Checkbox
          id="flow-node-continue-on-failure"
          checked={data.continueOnFailure ?? false}
          onCheckedChange={(checked) =>
            onChange({ continueOnFailure: checked === true ? true : undefined })
          }
          className="mt-0.5"
        />
        <div className="flex flex-col gap-0.5">
          <span className="text-sm font-medium">
            {t("chatFlow.properties.continueOnFailure")}
          </span>
          <span className="text-[11px] text-muted-foreground">
            {t("chatFlow.properties.continueOnFailureHint")}
          </span>
        </div>
      </label>
    </div>
  );
}

/**
 * Per-node A/B test editor (T72). Lets an author A/B a single node's wording
 * without duplicating the whole flow: set a {@code nodeExperimentKey} and add
 * variant arms, each optionally overriding the node's {@code aiInstruction}.
 * The backend assigns one arm per conversation (sticky, weighted by
 * {@code weight}) and swaps the instruction at prompt-build time. A blank
 * override on an arm is the "control" arm (keeps the base instruction).
 *
 * <p>Lives inside {@code definitionJson} on the node — round-trips through the
 * flow export/import for free (no entity column, unlike the flow-level T67
 * fields).
 */
function NodeAbTestSection({
  data,
  onChange,
}: {
  readonly data: FlowNodeData;
  readonly onChange: (patch: Partial<FlowNodeData>) => void;
}) {
  const { t } = useTranslation();
  const experimentKey = data.nodeExperimentKey ?? "";
  const variants = data.nodeVariants ?? [];
  const enabled = experimentKey.trim().length > 0;

  const updateVariant = (idx: number, patch: Partial<FlowNodeVariant>) => {
    const next = variants.map((v, i) => (i === idx ? { ...v, ...patch } : v));
    onChange({ nodeVariants: next });
  };

  return (
    <div className="flex flex-col gap-2 rounded-md border border-dashed border-purple-400/60 bg-purple-50/40 p-3 dark:bg-purple-950/20">
      <div className="flex flex-col gap-1">
        <Label htmlFor="flow-node-experiment-key">
          {t("chatFlow.properties.nodeAbTest.experimentKey", {
            defaultValue: "Per-node A/B experiment key",
          })}
        </Label>
        <Input
          id="flow-node-experiment-key"
          value={experimentKey}
          onChange={(e) => {
            const value = e.target.value;
            onChange({
              nodeExperimentKey: value.trim().length > 0 ? value : undefined,
              // Clearing the key drops the arms too so the export JSON stays
              // compact for nodes that opt back out of the experiment.
              ...(value.trim().length === 0 ? { nodeVariants: undefined } : {}),
            });
          }}
          placeholder="cargo-wording"
        />
        <span className="text-[11px] text-muted-foreground">
          {t("chatFlow.properties.nodeAbTest.experimentKeyHint", {
            defaultValue:
              "Set a key to A/B this single node's instruction. Each conversation is stickily assigned one arm below — no need to duplicate the whole flow. Independent from the flow-level A/B on the Settings tab.",
          })}
        </span>
      </div>

      {enabled && (
        <div className="flex flex-col gap-2">
          <div className="flex items-center justify-between">
            <Label>
              {t("chatFlow.properties.nodeAbTest.variants", { defaultValue: "Variants" })}
            </Label>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() =>
                onChange({ nodeVariants: [...variants, { label: "", weight: 50, aiInstruction: "" }] })
              }
            >
              <IconPlus className="size-4" />
              {t("chatFlow.properties.nodeAbTest.addVariant", { defaultValue: "Add variant" })}
            </Button>
          </div>

          {variants.length === 0 ? (
            <div className="rounded-md border border-dashed bg-muted/30 p-3 text-center text-xs text-muted-foreground">
              {t("chatFlow.properties.nodeAbTest.empty", {
                defaultValue: "No variants yet. Add at least two arms to run the test.",
              })}
            </div>
          ) : (
            variants.map((variant, idx) => (
              <div key={idx} className="flex flex-col gap-1 rounded-md border bg-background/60 p-2">
                <div className="flex items-center gap-2">
                  <Input
                    className="h-8"
                    value={variant.label ?? ""}
                    placeholder={t("chatFlow.properties.nodeAbTest.labelPlaceholder", {
                      defaultValue: "Arm name (e.g. control / punchy)",
                    })}
                    onChange={(e) => updateVariant(idx, { label: e.target.value })}
                  />
                  <Input
                    className="h-8 w-20"
                    type="number"
                    min={0}
                    value={variant.weight ?? ""}
                    placeholder={t("chatFlow.properties.nodeAbTest.weight", { defaultValue: "Weight" })}
                    onChange={(e) =>
                      updateVariant(idx, {
                        weight: e.target.value === "" ? undefined : Number(e.target.value),
                      })
                    }
                  />
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label={t("chatFlow.properties.nodeAbTest.removeVariant", {
                      defaultValue: "Remove variant",
                    })}
                    onClick={() => onChange({ nodeVariants: variants.filter((_, i) => i !== idx) })}
                  >
                    <IconTrash className="size-4 text-destructive" />
                  </Button>
                </div>
                <Textarea
                  rows={3}
                  value={variant.aiInstruction ?? ""}
                  placeholder={t("chatFlow.properties.nodeAbTest.instructionPlaceholder", {
                    defaultValue: "Instruction / template override — leave blank for the control arm (keeps the base text).",
                  })}
                  onChange={(e) => updateVariant(idx, { aiInstruction: e.target.value })}
                />
              </div>
            ))
          )}
          <span className="text-[11px] text-muted-foreground">
            {t("chatFlow.properties.nodeAbTest.variantsHint", {
              defaultValue:
                "Weights are relative (50/50 ≡ 1/1). An arm with weight 0 is paused. A blank instruction override keeps the node's authored wording — use it for the control arm.",
            })}
          </span>
        </div>
      )}
    </div>
  );
}

function typeLabelKey(type: FlowNodeType): string {
  switch (type) {
    case "start":
      return "chatFlow.palette.start";
    case "end":
      return "chatFlow.palette.end";
    case "aiQuestion":
      return "chatFlow.palette.aiQuestion";
    case "formCapture":
      return "chatFlow.palette.formCapture";
    case "condition":
      return "chatFlow.palette.condition";
    case "functionCall":
      return "chatFlow.palette.functionCall";
    case "subFlow":
      return "chatFlow.palette.subFlow";
    case "subFlowSwitch":
      return "chatFlow.palette.subFlowSwitch";
    case "persona":
      return "chatFlow.palette.persona";
    case "switch":
      return "chatFlow.palette.switch";
    case "slot":
      return "chatFlow.palette.slot";
    case "writeSlot":
      return "chatFlow.palette.writeSlot";
    case "webhook":
      return "chatFlow.palette.webhook";
    case "scheduleAgent":
      return "chatFlow.palette.scheduleAgent";
    case "suspend":
      return "chatFlow.palette.suspend";
    case "planningStep":
      return "chatFlow.palette.planningStep";
    case "iteratePlan":
      return "chatFlow.palette.iteratePlan";
  }
}

interface SwitchOptionsEditorProps {
  readonly options: FlowSwitchOption[];
  readonly onChange: (options: FlowSwitchOption[]) => void;
  /**
   * When provided, the editor renders an extra per-row sub-flow picker
   * (T47, for {@code subFlowSwitch} nodes). Each option's matched sub-flow
   * is stored on {@link FlowSwitchOption.subFlowId} with the resolved name
   * cached on {@link FlowSwitchOption.subFlowName}, mirroring the
   * {@code subFlow} node pattern.
   */
  readonly subFlowOptions?: ReadonlyArray<{ readonly id?: string | null; readonly name?: string | null }>;
}

function SwitchOptionsEditor({ options, onChange, subFlowOptions }: SwitchOptionsEditorProps) {
  const { t } = useTranslation();
  if (options.length === 0) {
    return (
      <div className="rounded-md border border-dashed bg-muted/30 p-3 text-center text-xs text-muted-foreground">
        {t("chatFlow.properties.switchOptionsEmpty")}
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-2">
      {options.map((option, idx) => (
        <div key={option.id} className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <Input
              value={option.label}
              placeholder={t("chatFlow.properties.switchOptionPlaceholder", { index: idx + 1 })}
              onChange={(e) => {
                const next = options.map((o) =>
                  o.id === option.id ? { ...o, label: e.target.value } : o,
                );
                onChange(next);
              }}
            />
            <Button
              type="button"
              variant="ghost"
              size="icon"
              aria-label={t("chatFlow.properties.switchRemoveOption")}
              onClick={() => onChange(options.filter((o) => o.id !== option.id))}
            >
              <IconTrash className="size-4 text-destructive" />
            </Button>
          </div>
          {subFlowOptions !== undefined && (
            <Select
              value={option.subFlowId ?? ""}
              onValueChange={(value) => {
                const selected = subFlowOptions.find((f) => f.id === value);
                const next = options.map((o) =>
                  o.id === option.id
                    ? {
                        ...o,
                        subFlowId: value || undefined,
                        subFlowName: selected?.name ?? undefined,
                      }
                    : o,
                );
                onChange(next);
              }}
            >
              <SelectTrigger className="h-8 text-xs">
                <SelectValue placeholder={t("chatFlow.properties.subFlowSwitchOptionPlaceholder")} />
              </SelectTrigger>
              <SelectContent>
                {subFlowOptions.length === 0 ? (
                  <div className="px-2 py-1.5 text-xs text-muted-foreground">
                    {t("chatFlow.properties.subFlowEmpty")}
                  </div>
                ) : (
                  subFlowOptions
                    .filter((f) => f.id != null && f.name != null)
                    .map((f) => (
                      <SelectItem key={f.id as string} value={f.id as string}>
                        {f.name}
                      </SelectItem>
                    ))
                )}
              </SelectContent>
            </Select>
          )}
        </div>
      ))}
    </div>
  );
}

interface ConvertToFormDialogProps {
  readonly open: boolean;
  readonly plan: FormConversionPlan;
  readonly agentId: string;
  readonly onOpenChange: (open: boolean) => void;
  readonly onConfirm: (fields: FlowFormField[]) => void;
}

/**
 * T234 — confirm dialog for collapsing a question run into a native form.
 * Shows the fields the form will carry and why the walk stopped (so a partial
 * collapse — "grabbed 3 of 5" — isn't a surprise). Applying is undoable: the
 * graph only persists when the author hits Save.
 *
 * T236 — an opt-in "Tidy labels with AI" button rewrites the raw labels (which
 * the converter derived from each question's instruction, e.g. "Qual o seu
 * e-mail corporativo?") into short, clean form labels ("E-mail") via the
 * default LLM. The tidied labels are previewed here and only committed when the
 * author confirms; the slot names + widget types are never touched.
 */
function ConvertToFormDialog({ open, plan, agentId, onOpenChange, onConfirm }: ConvertToFormDialogProps) {
  const { t } = useTranslation();
  // Local working copy of the fields so the AI tidy pass can mutate the labels
  // without rebuilding the (memoised) plan. Re-seeded whenever the plan changes.
  const [fields, setFields] = useState<FlowFormField[]>(plan.fields);
  const [tidying, setTidying] = useState(false);
  const [tidyError, setTidyError] = useState<string | null>(null);
  const [tidyNote, setTidyNote] = useState<string | null>(null);

  useEffect(() => {
    setFields(plan.fields);
    setTidyError(null);
    setTidyNote(null);
  }, [plan]);

  const stopReasonText = t(`chatFlow.properties.formConvertStop.${plan.stopReason}`, {
    defaultValue: "the run reached a node that can't be part of a form",
  });

  const handleTidy = async () => {
    setTidying(true);
    setTidyError(null);
    setTidyNote(null);
    try {
      const response = await chatFlowService.tidyFormLabels(agentId, {
        fields: fields.map((f) => ({ name: f.name, label: f.label ?? "", type: f.type })),
      });
      if (!response.success || !response.fields) {
        setTidyError(
          response.error ??
            t("chatFlow.properties.tidyLabelsError", { defaultValue: "Could not tidy labels." }),
        );
        return;
      }
      // Re-pair by slot name (unique per agent) so order drift in the response
      // can't mismatch labels; fall back to the existing label when absent.
      const tidyByName = new Map(response.fields.map((f) => [f.name, f.label]));
      setFields((prev) =>
        prev.map((f) => ({ ...f, label: tidyByName.get(f.name)?.trim() || f.label })),
      );
      setTidyNote(
        t("chatFlow.properties.tidyLabelsDone", { defaultValue: "Labels tidied — review below." }),
      );
    } catch {
      setTidyError(
        t("chatFlow.properties.tidyLabelsError", { defaultValue: "Could not tidy labels." }),
      );
    } finally {
      setTidying(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {t("chatFlow.properties.convertToFormTitle", { defaultValue: "Convert to a native form" })}
          </DialogTitle>
          <DialogDescription>
            {t("chatFlow.properties.convertToFormDescription", {
              defaultValue:
                "These {{count}} consecutive questions become one multi-field form. The collapse stops because {{reason}}.",
              count: plan.runLength,
              reason: stopReasonText,
            })}
          </DialogDescription>
        </DialogHeader>
        <div className="flex items-center justify-between gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="gap-2 border-indigo-300 text-indigo-700 dark:border-indigo-800 dark:text-indigo-300"
            onClick={handleTidy}
            disabled={tidying}
          >
            <IconSparkles className="size-4" />
            {tidying
              ? t("chatFlow.properties.tidyLabelsBusy", { defaultValue: "Tidying labels…" })
              : t("chatFlow.properties.tidyLabels", { defaultValue: "Tidy labels with AI" })}
          </Button>
          {tidyNote && <span className="text-[11px] text-emerald-600 dark:text-emerald-400">{tidyNote}</span>}
          {tidyError && <span className="text-[11px] text-destructive">{tidyError}</span>}
        </div>
        <ul className="max-h-64 space-y-1 overflow-auto rounded-md border bg-muted/30 p-2 text-sm">
          {fields.map((field, idx) => (
            // eslint-disable-next-line react/no-array-index-key
            <li key={idx} className="flex items-center justify-between gap-2">
              <span className="truncate">{field.label || field.name || `field ${idx + 1}`}</span>
              <span className="flex items-center gap-1 text-[11px] text-muted-foreground">
                <span className="rounded bg-background px-1.5 py-0.5">{field.type ?? "text"}</span>
                <code className="font-mono">{field.name || "—"}</code>
              </span>
            </li>
          ))}
        </ul>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            {t("common.cancel", { defaultValue: "Cancel" })}
          </Button>
          <Button onClick={() => onConfirm(fields)}>
            {t("chatFlow.properties.convertToFormConfirm", { defaultValue: "Convert" })}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

interface FormFieldsEditorProps {
  readonly fields: FlowFormField[];
  readonly slotOptions: ReadonlyArray<{ readonly id: string; readonly name: string; readonly type: string }>;
  readonly onChange: (fields: FlowFormField[]) => void;
}

/**
 * T235 — maps a declared slot's type to a sensible default form-field widget.
 * Slot types carry no email/phone semantics (that lives on the field's
 * `validationRule`), so the inference is conservative: TEXT → textarea,
 * INTEGER/FLOAT → number, everything else → text.
 */
function inferFieldType(slotType: string): string {
  switch (slotType) {
    case "TEXT":
      return "textarea";
    case "INTEGER":
    case "FLOAT":
      return "number";
    default:
      return "text";
  }
}

/**
 * T107/T235 — editor for a {@code formCapture} node's native multi-field form.
 * Each row maps to one **declared** conversation slot (picked from the agent's
 * catalog); the SDK renders the whole set as a native form and posts every
 * value back through `POST /chat/form-submit`.
 */
function FormFieldsEditor({ fields, slotOptions, onChange }: FormFieldsEditorProps) {
  const { t } = useTranslation();
  const patch = (idx: number, partial: Partial<FlowFormField>) =>
    onChange(fields.map((f, i) => (i === idx ? { ...f, ...partial } : f)));
  return (
    <div className="flex flex-col gap-2 rounded-md border border-indigo-200 bg-indigo-50/40 p-2 dark:border-indigo-900 dark:bg-indigo-950/20">
      <div className="flex items-center justify-between">
        <Label className="text-xs font-semibold uppercase tracking-wide text-indigo-700 dark:text-indigo-300">
          {t("chatFlow.properties.formFields", { defaultValue: "Form fields (native form)" })}
        </Label>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => onChange([...fields, { name: "", type: "text", required: true }])}
        >
          <IconPlus className="size-4" />
          {t("chatFlow.properties.formFieldAdd", { defaultValue: "Add field" })}
        </Button>
      </div>
      {fields.length === 0 ? (
        <div className="rounded-md border border-dashed bg-muted/30 p-3 text-center text-[11px] text-muted-foreground">
          {t("chatFlow.properties.formFieldsEmpty", {
            defaultValue:
              "No fields yet — this formCapture behaves as a single-field capture. Add fields to render a native multi-field form.",
          })}
        </div>
      ) : (
        fields.map((field, idx) => (
          // eslint-disable-next-line react/no-array-index-key
          <div key={idx} className="flex flex-col gap-1.5 rounded-md border bg-background p-2">
            <div className="flex items-center gap-2">
              <Select
                value={field.name || ""}
                onValueChange={(value) => {
                  const picked = slotOptions.find((s) => s.name === value);
                  // Infer the widget when the field is still on its default
                  // type — don't clobber an author's explicit choice.
                  const inferred =
                    picked && (!field.type || field.type === "text")
                      ? { type: inferFieldType(picked.type) }
                      : {};
                  patch(idx, { name: value, ...inferred });
                }}
              >
                <SelectTrigger className="h-8 font-mono text-[12px]">
                  <SelectValue
                    placeholder={t("chatFlow.properties.formFieldSlotPlaceholder", {
                      defaultValue: "Pick a slot…",
                    })}
                  />
                </SelectTrigger>
                <SelectContent>
                  {slotOptions.length === 0 && !field.name ? (
                    <div className="px-2 py-1.5 text-xs text-muted-foreground">
                      {t("chatFlow.properties.outputVariableEmpty")}
                    </div>
                  ) : (
                    <>
                      {/* Preserve an out-of-catalog (legacy/free-text) value so
                          editing the row doesn't silently drop it. */}
                      {field.name && !slotOptions.some((s) => s.name === field.name) && (
                        <SelectItem value={field.name}>
                          <span className="font-mono text-[12px]">{field.name}</span>
                          <span className="ml-2 text-amber-600">
                            — {t("chatFlow.properties.formFieldUndeclared", {
                              defaultValue: "not declared",
                            })}
                          </span>
                        </SelectItem>
                      )}
                      {slotOptions.map((slot) => (
                        <SelectItem key={slot.id} value={slot.name}>
                          <span className="font-mono text-[12px]">{slot.name}</span>
                          <span className="ml-2 text-muted-foreground">
                            — {t(`aiAgent.slot.types.${slot.type}`)}
                          </span>
                        </SelectItem>
                      ))}
                    </>
                  )}
                </SelectContent>
              </Select>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                aria-label={t("chatFlow.properties.formFieldRemove", { defaultValue: "Remove field" })}
                onClick={() => onChange(fields.filter((_, i) => i !== idx))}
              >
                <IconTrash className="size-4 text-destructive" />
              </Button>
            </div>
            <Input
              className="h-8 text-[12px]"
              value={field.label ?? ""}
              placeholder={t("chatFlow.properties.formFieldLabelPlaceholder", {
                defaultValue: "Label",
              })}
              onChange={(e) => patch(idx, { label: e.target.value || undefined })}
            />
            <div className="flex items-center gap-2">
              <Select
                value={field.type ?? "text"}
                onValueChange={(value) => patch(idx, { type: value })}
              >
                <SelectTrigger className="h-8 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {FORM_FIELD_TYPES.map((ft) => (
                    <SelectItem key={ft} value={ft}>
                      {t(`chatFlow.properties.formFieldType.${ft}`, { defaultValue: ft })}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Select
                value={field.validationRule ?? "none"}
                onValueChange={(value) =>
                  patch(idx, { validationRule: value === "none" ? undefined : value })
                }
              >
                <SelectTrigger className="h-8 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {VALIDATION_OPTIONS.map((option) => (
                    <SelectItem key={option} value={option}>
                      {t(`chatFlow.properties.validation.${option}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            {field.type === "select" && (
              <Textarea
                rows={2}
                className="text-[12px]"
                value={(field.options ?? []).join("\n")}
                placeholder={"Option A\nOption B"}
                onChange={(e) => {
                  const opts = e.target.value
                    .split(/\r?\n/)
                    .map((l) => l.trim())
                    .filter((l) => l.length > 0);
                  patch(idx, { options: opts.length > 0 ? opts : undefined });
                }}
              />
            )}
            <label className="flex items-center gap-2 text-[12px]">
              <Checkbox
                checked={field.required ?? true}
                onCheckedChange={(checked) => patch(idx, { required: checked !== false })}
              />
              {t("chatFlow.properties.formFieldRequired", { defaultValue: "Required" })}
            </label>
          </div>
        ))
      )}
    </div>
  );
}
