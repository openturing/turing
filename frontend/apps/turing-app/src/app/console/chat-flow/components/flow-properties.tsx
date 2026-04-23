import { IconX } from "@tabler/icons-react";
import type { Node } from "@xyflow/react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";

import type { FlowNodeData, FlowNodeType } from "../types";

/**
 * Right-hand properties panel. Re-renders whenever the selected node changes; all edits flow back
 * through {@code onChange} so the parent keeps the single source of truth for the flow.
 *
 * @since 2026.2.4
 */

interface FlowPropertiesProps {
  node: Node<FlowNodeData> | null;
  onChange: (nodeId: string, patch: Partial<FlowNodeData>) => void;
  onClose: () => void;
}

const VALIDATION_OPTIONS = ["none", "email", "phone", "url", "number", "date"];

export function FlowProperties({ node, onChange, onClose }: FlowPropertiesProps) {
  const { t } = useTranslation();

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
          <div className="flex flex-col gap-1">
            <Label htmlFor="flow-node-function">{t("chatFlow.properties.functionName")}</Label>
            <Input
              id="flow-node-function"
              value={data.functionName ?? ""}
              onChange={(e) => update({ functionName: e.target.value })}
              placeholder="myTool(argument)"
            />
          </div>
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
