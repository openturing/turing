import { Handle, Position, type NodeProps } from "@xyflow/react";
import {
  IconArrowRight,
  IconArrowsSplit2,
  IconBook2,
  IconDatabase,
  IconFlag3,
  IconPlayerPlayFilled,
  IconBell,
  IconSparkles,
  IconSubtask,
  IconVariable,
} from "@tabler/icons-react";

import type { FlowNodeData } from "../types";
import { ensureSwitchOptions } from "../chat-flow.serialize";

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
  // Prefer the cached display name — same pattern as SubFlowNode. Falls back to the raw uuid only
  // when the node was wired without going through the properties panel (e.g. legacy imports).
  const personaName =
    typeof data.personaName === "string" && data.personaName.trim().length > 0
      ? data.personaName
      : null;
  const display = personaName ?? (typeof data.personaId === "string" ? data.personaId : null);
  return (
    <div className="w-64 overflow-hidden rounded-lg border-2 border-fuchsia-500 bg-background shadow-md">
      <div className="flex items-center gap-2 bg-fuchsia-500 px-3 py-2 text-white">
        <IconSparkles className="size-4" />
        <div className="text-xs font-semibold uppercase tracking-wide">{data.label || "Persona Switch"}</div>
      </div>
      <div className="bg-fuchsia-50 p-3 text-xs leading-relaxed text-fuchsia-900 dark:bg-fuchsia-950/40 dark:text-fuchsia-100">
        <span className="font-semibold">Switches voice to: </span>
        {display ? (
          <code className="rounded bg-fuchsia-200/60 px-1 py-0.5 text-[11px] dark:bg-fuchsia-900/50">
            {display}
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

export function SwitchNode({ data }: ChatFlowNodeProps) {
  const options = ensureSwitchOptions(data);
  // We make the wrapper itself the positioned ancestor so each handle's `top: Npx` is measured
  // from the wrapper's padding-box (inside the border) — that anchors `right: 0` at the wrapper's
  // content edge, so the default `translate(50%, -50%)` lands the disc half-inside / half-outside
  // the cyan border (5px in, 5px out — same look as AIQuestion / Persona handles).
  //
  // The trade-off: overflow-hidden would clip the half-outside disc once the wrapper is the
  // containing block, so the rows' rectangular corners can't ride on it — `rounded-t-md` on the
  // header and `rounded-b-md` on the last row fill in for the wrapper's rounded border instead.
  // (rounded-md = 6px = rounded-lg 8px − 2px border, matching the inner curve.)
  //
  // Header (h-9, 36px) + each option row (h-9). Handle N centre = 36 + N*36 + 18 from the
  // wrapper's padding-box top — i.e. on the vertical centre of row N.
  const handleTop = (idx: number) => 36 + idx * 36 + 18;
  return (
    <div className="relative w-64 rounded-lg border-2 border-cyan-500 bg-background shadow-md">
      <div className="flex h-9 items-center gap-2 rounded-t-md bg-cyan-500 px-3 text-white">
        <IconArrowsSplit2 className="size-4" />
        <div className="text-xs font-semibold uppercase tracking-wide">{data.label || "Switch"}</div>
      </div>
      {options.length === 0 ? (
        <div className="rounded-b-md bg-cyan-50 px-3 py-2 text-xs italic text-cyan-900/70 dark:bg-cyan-950/40 dark:text-cyan-100/70">
          No options yet
        </div>
      ) : (
        options.map((option, idx) => {
          const isLast = idx === options.length - 1;
          const optionLabel = option.label || `Option ${idx + 1}`;
          return (
            <div
              key={option.id}
              className={`flex h-9 items-center justify-between gap-2 border-t border-cyan-200/70 bg-cyan-50 pl-3 pr-3 text-xs text-cyan-900 dark:border-cyan-900/50 dark:bg-cyan-950/40 dark:text-cyan-100${isLast ? " rounded-b-md" : ""}`}
            >
              <span className="truncate">{optionLabel}</span>
              <IconArrowRight
                className="pointer-events-none size-4 shrink-0 text-cyan-600 dark:text-cyan-400"
                aria-hidden
              />
            </div>
          );
        })
      )}
      {/* Handles live alongside the rows (not inside them) so the row's box-sizing math doesn't
          drift their positions; explicit pixel `top` lands each disc on its row's vertical centre. */}
      {options.map((option, idx) => {
        const optionLabel = option.label || `Option ${idx + 1}`;
        return (
          <Handle
            key={option.id}
            type="source"
            id={option.id}
            position={Position.Right}
            style={{ top: `${handleTop(idx)}px` }}
            className="size-2.5! bg-cyan-600! transition-transform hover:scale-150"
            aria-label={`Drag to connect: ${optionLabel}`}
          />
        );
      })}
      <Handle type="target" position={Position.Left} className="size-2.5! bg-cyan-600!" />
    </div>
  );
}

export function SlotNode({ data }: ChatFlowNodeProps) {
  const operation = data.slotOperation ?? "SET";
  const slotName = typeof data.slotName === "string" && data.slotName.trim().length > 0
    ? data.slotName
    : null;
  const isDelete = operation === "DELETE";
  return (
    <div className="w-64 overflow-hidden rounded-lg border-2 border-sky-500 bg-background shadow-md">
      <div className="flex items-center justify-between gap-2 bg-sky-500 px-3 py-2 text-white">
        <div className="flex items-center gap-2">
          <IconVariable className="size-4" />
          <div className="text-xs font-semibold uppercase tracking-wide">{data.label || "Slot"}</div>
        </div>
        <span className="rounded bg-sky-700/60 px-1.5 py-0.5 text-[10px] font-semibold uppercase">
          {operation}
        </span>
      </div>
      <div className="bg-sky-50 p-3 text-xs leading-relaxed text-sky-900 dark:bg-sky-950/40 dark:text-sky-100">
        <div>
          <span className="font-semibold">Slot: </span>
          {slotName ? (
            <code className="rounded bg-sky-200/60 px-1 py-0.5 text-[11px] dark:bg-sky-900/50">
              {slotName}
            </code>
          ) : (
            <span className="italic opacity-60">not selected</span>
          )}
        </div>
        {!isDelete && (
          <div className="mt-1 opacity-90">
            <span className="font-semibold">Value: </span>
            {data.slotValue && data.slotValue.length > 0 ? (
              <code className="rounded bg-sky-200/60 px-1 py-0.5 text-[11px] dark:bg-sky-900/50">
                {data.slotValue}
              </code>
            ) : (
              <span className="italic opacity-60">empty</span>
            )}
          </div>
        )}
      </div>
      <Handle type="target" position={Position.Left} className="size-2.5! bg-sky-600!" />
      <Handle type="source" position={Position.Right} className="size-2.5! bg-sky-600!" />
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
  switch: SwitchNode,
  slot: SlotNode,
};
