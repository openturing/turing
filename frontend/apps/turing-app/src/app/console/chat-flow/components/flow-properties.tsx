import { IconPlus, IconTrash, IconX } from "@tabler/icons-react";
import type { Node } from "@xyflow/react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { useAIAgentSlots } from "@/api/queries/ai-agent-slot.queries";
import { useAiAgent } from "@/api/queries/ai-agent.queries";
import { useChatFlows } from "@/api/queries/chat-flow.queries";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import type { NativeToolGroup } from "@/models/genai/native-tool.model";
import type { TurMcpServer } from "@/models/mcp/mcp-server.model";
import { TurNativeToolService } from "@/services/genai/native-tool.service";
import { TurMcpServerService } from "@/services/mcp/mcp-server.service";

import { ensureSwitchOptions, newSwitchOptionId } from "../chat-flow.serialize";
import type { FlowNodeData, FlowNodeType, FlowSlotOperation, FlowSwitchOption, FlowToolSource } from "../types";

const nativeToolService = new TurNativeToolService();
const mcpServerService = new TurMcpServerService();

/**
 * Right-hand properties panel. Re-renders whenever the selected node changes; all edits flow back
 * through {@code onChange} so the parent keeps the single source of truth for the flow.
 *
 * @since 2026.2.4
 */

interface FlowPropertiesProps {
  readonly node: Node<FlowNodeData> | null;
  readonly onChange: (nodeId: string, patch: Partial<FlowNodeData>) => void;
  readonly onClose: () => void;
  readonly agentId: string;
  /** Id of the chat flow currently being edited — excluded from the Sub Flow picker to prevent self-reference. */
  readonly currentFlowId?: string;
}

const VALIDATION_OPTIONS = ["none", "email", "phone", "url", "number", "date"];
const TOOL_SOURCE_OPTIONS: FlowToolSource[] = ["NATIVE", "MCP"];
const SLOT_OPERATION_OPTIONS: FlowSlotOperation[] = ["SET", "DELETE"];

export function FlowProperties({ node, onChange, onClose, agentId, currentFlowId }: FlowPropertiesProps) {
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

        {(type === "aiQuestion" || type === "functionCall") && (
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

        {type === "aiQuestion" && (
          <>
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

        {type === "switch" && (
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
              />
              <span className="text-[11px] text-muted-foreground">
                {t("chatFlow.properties.switchOptionsHint")}
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
      </div>
    </aside>
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
    case "condition":
      return "chatFlow.palette.condition";
    case "functionCall":
      return "chatFlow.palette.functionCall";
    case "subFlow":
      return "chatFlow.palette.subFlow";
    case "persona":
      return "chatFlow.palette.persona";
    case "switch":
      return "chatFlow.palette.switch";
    case "slot":
      return "chatFlow.palette.slot";
  }
}

interface SwitchOptionsEditorProps {
  readonly options: FlowSwitchOption[];
  readonly onChange: (options: FlowSwitchOption[]) => void;
}

function SwitchOptionsEditor({ options, onChange }: SwitchOptionsEditorProps) {
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
        <div key={option.id} className="flex items-center gap-2">
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
      ))}
    </div>
  );
}
