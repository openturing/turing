import { Handle, Position, type NodeProps } from "@xyflow/react";
import {
  IconBook2,
  IconDatabase,
  IconFlag3,
  IconPlayerPlayFilled,
  IconBell,
  IconSparkles,
  IconSubtask,
} from "@tabler/icons-react";

import type { FlowNodeData } from "../types";

/**
 * Custom React Flow node renderers for the Chat Flow builder. Each node type has a distinct
 * visual treatment matching the Components sidebar icons, and exposes source/target handles for
 * wiring edges. AI Question and Function Call cards also expose a short summary so the canvas
 * reads at-a-glance.
 *
 * @since 2026.2.4
 */

type ChatFlowNodeProps = NodeProps & { data: FlowNodeData };

export function StartNode(_props: ChatFlowNodeProps) {
  return (
    <div className="flex size-16 items-center justify-center rounded-full bg-emerald-500 text-white shadow-md ring-2 ring-emerald-600 ring-offset-2 ring-offset-background">
      <IconPlayerPlayFilled className="size-6" />
      <Handle type="source" position={Position.Right} className="size-2.5! bg-emerald-700!" />
    </div>
  );
}

export function EndNode(_props: ChatFlowNodeProps) {
  return (
    <div className="flex size-16 items-center justify-center rounded-full bg-rose-500 text-white shadow-md ring-2 ring-rose-600 ring-offset-2 ring-offset-background">
      <IconFlag3 className="size-6" />
      <Handle type="target" position={Position.Left} className="size-2.5! bg-rose-700!" />
    </div>
  );
}

export function AIQuestionNode({ data }: ChatFlowNodeProps) {
  return (
    <div className="w-64 overflow-hidden rounded-lg border-2 border-blue-500 bg-background shadow-md">
      <div className="flex items-center gap-2 bg-blue-500 px-3 py-2 text-white">
        <IconBook2 className="size-4" />
        <div className="text-xs font-semibold uppercase tracking-wide">{data.label || "AI Question"}</div>
      </div>
      <div className="bg-blue-50 p-3 text-xs leading-relaxed text-blue-900 dark:bg-blue-950/40 dark:text-blue-100">
        <span className="font-semibold">AI Instruction: </span>
        {data.aiInstruction?.trim() || <span className="italic opacity-60">No instruction yet</span>}
      </div>
      <Handle type="target" position={Position.Left} className="size-2.5! bg-blue-600!" />
      <Handle type="source" position={Position.Right} className="size-2.5! bg-blue-600!" />
    </div>
  );
}

export function ConditionNode({ data }: ChatFlowNodeProps) {
  return (
    <div className="relative size-48">
      <div
        className="absolute inset-0 rotate-45 rounded-lg border-2 border-amber-500 bg-amber-50 shadow-md dark:bg-amber-950/40"
        aria-hidden
      />
      <div className="relative flex h-full flex-col items-center justify-center gap-1 p-4 text-center text-amber-900 dark:text-amber-100">
        <IconDatabase className="size-5" />
        <div className="text-xs font-semibold uppercase tracking-wide">{data.label || "Condition"}</div>
        <div className="text-[10px] opacity-80">{data.conditionExpression || data.functionName || "Configure"}</div>
      </div>
      <Handle type="target" position={Position.Left} style={{ left: "-4px" }} className="size-2.5! bg-amber-600!" />
      <Handle type="source" id="yes" position={Position.Right} style={{ right: "-4px", top: "40%" }} className="size-2.5! bg-emerald-500!" />
      <Handle type="source" id="no" position={Position.Bottom} style={{ bottom: "-4px", left: "50%" }} className="size-2.5! bg-rose-500!" />
    </div>
  );
}

export function FunctionCallNode({ data }: ChatFlowNodeProps) {
  const source = data.toolSource ?? "NATIVE";
  const target = source === "MCP" ? data.mcpServerId : data.functionName;
  return (
    <div className="w-64 overflow-hidden rounded-lg border-2 border-orange-500 bg-background shadow-md">
      <div className="flex items-center justify-between gap-2 bg-orange-500 px-3 py-2 text-white">
        <div className="flex items-center gap-2">
          <IconBell className="size-4" />
          <div className="text-xs font-semibold uppercase tracking-wide">{data.label || "Function Call"}</div>
        </div>
        <span className="rounded bg-orange-700/60 px-1.5 py-0.5 text-[10px] font-semibold uppercase">
          {source}
        </span>
      </div>
      <div className="bg-orange-50 p-3 text-xs leading-relaxed text-orange-900 dark:bg-orange-950/40 dark:text-orange-100">
        <div>
          <span className="font-semibold">{source === "MCP" ? "MCP Server: " : "Function: "}</span>
          <code className="rounded bg-orange-200/60 px-1 py-0.5 text-[11px] dark:bg-orange-900/50">
            {target?.trim() || "not set"}
          </code>
        </div>
        {data.aiInstruction && (
          <div className="mt-1 opacity-90">
            <span className="font-semibold">AI Instruction: </span>
            {data.aiInstruction}
          </div>
        )}
      </div>
      <Handle type="target" position={Position.Left} className="size-2.5! bg-orange-600!" />
      <Handle type="source" position={Position.Right} className="size-2.5! bg-orange-600!" />
    </div>
  );
}

export function SubFlowNode({ data }: ChatFlowNodeProps) {
  const subFlowName =
    typeof data.subFlowName === "string" && data.subFlowName.trim().length > 0
      ? data.subFlowName
      : null;
  const target = subFlowName ?? data.subFlowId ?? null;
  return (
    <div className="w-64 overflow-hidden rounded-lg border-2 border-violet-500 bg-background shadow-md">
      <div className="flex items-center gap-2 bg-violet-500 px-3 py-2 text-white">
        <IconSubtask className="size-4" />
        <div className="text-xs font-semibold uppercase tracking-wide">{data.label || "Sub Flow"}</div>
      </div>
      <div className="bg-violet-50 p-3 text-xs leading-relaxed text-violet-900 dark:bg-violet-950/40 dark:text-violet-100">
        <span className="font-semibold">Sub Flow: </span>
        {target ? (
          <code className="rounded bg-violet-200/60 px-1 py-0.5 text-[11px] dark:bg-violet-900/50">
            {target}
          </code>
        ) : (
          <span className="italic opacity-60">not selected</span>
        )}
      </div>
      <Handle type="target" position={Position.Left} className="size-2.5! bg-violet-600!" />
      <Handle type="source" position={Position.Right} className="size-2.5! bg-violet-600!" />
    </div>
  );
}

export function PersonaNode({ data }: ChatFlowNodeProps) {
  // Persona id is just a uuid in the data — without a personas list lookup
  // we can only show "set / not set". The properties panel renders the full
  // dropdown with the human-readable persona name.
  const hasPersona = typeof data.personaId === "string" && data.personaId.trim().length > 0;
  return (
    <div className="w-64 overflow-hidden rounded-lg border-2 border-fuchsia-500 bg-background shadow-md">
      <div className="flex items-center gap-2 bg-fuchsia-500 px-3 py-2 text-white">
        <IconSparkles className="size-4" />
        <div className="text-xs font-semibold uppercase tracking-wide">{data.label || "Persona Switch"}</div>
      </div>
      <div className="bg-fuchsia-50 p-3 text-xs leading-relaxed text-fuchsia-900 dark:bg-fuchsia-950/40 dark:text-fuchsia-100">
        <span className="font-semibold">Switches voice to: </span>
        {hasPersona ? (
          <code className="rounded bg-fuchsia-200/60 px-1 py-0.5 text-[11px] dark:bg-fuchsia-900/50">
            {data.personaId}
          </code>
        ) : (
          <span className="italic opacity-60">not selected</span>
        )}
      </div>
      <Handle type="target" position={Position.Left} className="size-2.5! bg-fuchsia-600!" />
      <Handle type="source" position={Position.Right} className="size-2.5! bg-fuchsia-600!" />
    </div>
  );
}

export const nodeTypes = {
  start: StartNode,
  end: EndNode,
  aiQuestion: AIQuestionNode,
  condition: ConditionNode,
  functionCall: FunctionCallNode,
  subFlow: SubFlowNode,
  persona: PersonaNode,
};
