import { IconX } from "@tabler/icons-react";
import type { Node } from "@xyflow/react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import type { NativeToolGroup } from "@/models/genai/native-tool.model";
import type { TurMcpServer } from "@/models/mcp/mcp-server.model";
import { TurNativeToolService } from "@/services/genai/native-tool.service";
import { TurMcpServerService } from "@/services/mcp/mcp-server.service";

import type { FlowNodeData, FlowNodeType, FlowToolSource } from "../types";

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
}

const VALIDATION_OPTIONS = ["none", "email", "phone", "url", "number", "date"];
const TOOL_SOURCE_OPTIONS: FlowToolSource[] = ["NATIVE", "MCP"];

export function FlowProperties({ node, onChange, onClose }: FlowPropertiesProps) {
  const { t } = useTranslation();
  const [nativeToolGroups, setNativeToolGroups] = useState<NativeToolGroup[]>([]);
  const [mcpServers, setMcpServers] = useState<TurMcpServer[]>([]);

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
              <Label htmlFor="flow-node-output">{t("chatFlow.properties.outputVariable")}</Label>
              <Input
                id="flow-node-output"
                value={data.outputVariable ?? ""}
                onChange={(e) => update({ outputVariable: e.target.value })}
                placeholder="user_response"
              />
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
  }
}
