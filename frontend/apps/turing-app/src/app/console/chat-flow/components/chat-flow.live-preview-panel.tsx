import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import type { Edge, Node } from "@xyflow/react";
import {
  IconBolt,
  IconRefresh,
  IconX,
} from "@tabler/icons-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import type { FlowFormField, FlowNodeData } from "../types";

/**
 * T95 / §VII.11.e — Live preview of a single node.
 *
 * <p>Shows what the visitor would see for the currently-selected node,
 * with {@code {{slot}}} references interpolated against the mock slots
 * the author types in the textarea at the bottom. Updates synchronously
 * on every keystroke — the spec asks for &lt; 300ms; we deliver instant
 * because everything renders client-side (no backend round-trip, no
 * LLM call). Slot mocks persist in {@code localStorage} keyed by flow
 * id so iterating on a node across sessions does not require re-typing
 * the same name/email/cargo.
 *
 * <p>Complementary to {@link ChatPreview} (whole-flow walk transcript):
 * this panel zooms IN on one node so authors can sanity-check tone /
 * interpolation / option chips without running a real chat turn.
 *
 * @since 2026.3.1
 */

interface LivePreviewPanelProps {
  selectedNode: Node<FlowNodeData> | null;
  nodes: Node<FlowNodeData>[];
  edges: Edge[];
  flowId?: string;
  onClose: () => void;
}

const STORAGE_PREFIX = "turing.chatFlow.livePreviewMocks.";
const NEW_FLOW_KEY = "__new__";

export function ChatFlowLivePreviewPanel({
  selectedNode,
  nodes,
  edges,
  flowId,
  onClose,
}: Readonly<LivePreviewPanelProps>) {
  const { t } = useTranslation();
  const storageKey = STORAGE_PREFIX + (flowId ?? NEW_FLOW_KEY);

  const [mockText, setMockText] = useState<string>(() => loadMocks(storageKey));

  // Persist to localStorage on every change so a refresh / tab switch
  // keeps the author's setup. Same flow id → same mocks. We DON'T sync
  // across tabs (no storage event listener) — that would interrupt
  // typing and the use case is single-author anyway.
  useEffect(() => {
    try {
      window.localStorage.setItem(storageKey, mockText);
    } catch {
      // private window / quota — fail silent. Mocks stay in-memory.
    }
  }, [storageKey, mockText]);

  const mockSlots = useMemo(() => parseMocks(mockText), [mockText]);

  const interpolate = useCallback(
    (text: string | undefined | null) => interpolateSlots(text, mockSlots),
    [mockSlots],
  );

  const nextNode = useMemo(() => {
    if (!selectedNode) return null;
    const edge = edges.find((e) => e.source === selectedNode.id);
    if (!edge) return null;
    return nodes.find((n) => n.id === edge.target) ?? null;
  }, [selectedNode, nodes, edges]);

  const onClear = useCallback(() => setMockText(""), []);

  return (
    <aside className="flex h-full w-96 flex-col border-l bg-background shadow-xl">
      <header className="flex items-center justify-between border-b bg-gradient-to-r from-blue-600 to-indigo-600 px-4 py-3 text-white">
        <div className="flex items-center gap-2">
          <IconBolt className="size-5" />
          <div className="text-sm font-semibold">
            {t("chatFlow.livePreview.title", { defaultValue: "Live preview" })}
          </div>
        </div>
        <Button
          variant="ghost"
          size="icon"
          onClick={onClose}
          className="text-white hover:bg-white/20"
          aria-label={t("chatFlow.livePreview.close", { defaultValue: "Close" })}
        >
          <IconX className="size-4" />
        </Button>
      </header>

      <div className="flex flex-1 flex-col gap-3 overflow-auto bg-muted/30 p-4">
        {selectedNode ? (
          <>
            <NodeHeaderCard node={selectedNode} />
            <NodeRendering
              data={selectedNode.data}
              interpolate={interpolate}
              mockSlots={mockSlots}
              t={t}
            />
            {nextNode && (
              <div className="rounded-md border border-dashed border-border/60 bg-background/50 p-3 text-[11px] text-muted-foreground">
                {t("chatFlow.livePreview.nextHint", {
                  defaultValue: "Next: {{id}} ({{type}})",
                  id: nextNode.id,
                  type: nextNode.data.type,
                })}
              </div>
            )}
          </>
        ) : (
          <div className="mt-8 text-center text-sm text-muted-foreground">
            {t("chatFlow.livePreview.empty", {
              defaultValue:
                "Select a node on the canvas to preview what the visitor sees on it.",
            })}
          </div>
        )}
      </div>

      <footer className="border-t bg-background p-3">
        <div className="mb-2 flex items-center justify-between">
          <label
            htmlFor="chat-flow-live-preview-mocks"
            className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground"
          >
            {t("chatFlow.livePreview.mocksLabel", {
              defaultValue: "Mock slots (one per line: key=value)",
            })}
          </label>
          {mockText.trim().length > 0 && (
            <Button
              variant="ghost"
              size="sm"
              className="h-6 px-2 text-[11px]"
              onClick={onClear}
            >
              <IconRefresh className="mr-1 size-3" />
              {t("chatFlow.livePreview.clearMocks", { defaultValue: "Clear" })}
            </Button>
          )}
        </div>
        <Textarea
          id="chat-flow-live-preview-mocks"
          value={mockText}
          onChange={(event) => setMockText(event.target.value)}
          placeholder={"name=Maria\ncargo_atual=Diretora"}
          rows={4}
          className="font-mono text-xs"
        />
      </footer>
    </aside>
  );
}

function NodeHeaderCard({ node }: Readonly<{ node: Node<FlowNodeData> }>) {
  return (
    <div className="rounded-md border border-border/60 bg-background/60 p-2 text-[11px]">
      <div className="text-muted-foreground">
        <code className="font-mono text-foreground">{node.id}</code>
        {" · "}
        <span className="font-semibold uppercase tracking-wide">
          {node.data.type}
        </span>
      </div>
    </div>
  );
}

function NodeRendering({
  data,
  interpolate,
  mockSlots,
  t,
}: Readonly<{
  data: FlowNodeData;
  interpolate: (text: string | undefined | null) => React.ReactNode;
  mockSlots: Record<string, string>;
  t: (key: string, options?: Record<string, unknown>) => string;
}>) {
  switch (data.type) {
    case "start":
      return (
        <SystemBubble>
          {t("chatFlow.livePreview.startHint", {
            defaultValue: "Conversation begins here.",
          })}
        </SystemBubble>
      );
    case "end":
      return (
        <SystemBubble>
          {t("chatFlow.livePreview.endHint", {
            defaultValue: "Conversation ends here. Slots are recorded as a submission.",
          })}
        </SystemBubble>
      );
    case "aiQuestion":
    case "formCapture": {
      // T107 — a formCapture node with declared fields renders as a native
      // multi-field form instead of a single free-text turn.
      const formFields = data.type === "formCapture" ? (data.formFields ?? []) : [];
      return (
        <>
          <BotBubble>{interpolate(data.aiInstruction || data.label)}</BotBubble>
          {data.inlineOptions && data.inlineOptions.length > 0 && (
            <OptionsRow options={data.inlineOptions} />
          )}
          {formFields.length > 0 ? (
            <FormPreview fields={formFields} t={t} />
          ) : (
            <UserPlaceholder
              outputVariable={data.outputVariable}
              validationRule={data.validationRule}
              t={t}
            />
          )}
        </>
      );
    }
    case "condition": {
      const expr = data.conditionExpression || "(no expression)";
      return (
        <SystemBubble>
          {t("chatFlow.livePreview.conditionEvaluates", {
            defaultValue: "Evaluates: ",
          })}
          <code className="font-mono">{interpolate(expr)}</code>
        </SystemBubble>
      );
    }
    case "switch": {
      const variableValue = data.switchVariable
        ? mockSlots[data.switchVariable]
        : undefined;
      const labels = (data.switchOptions ?? []).map((o) => o.label || o.id);
      const matchedLabel = variableValue
        ? labels.find(
            (label) => label.toLowerCase() === variableValue.toLowerCase(),
          )
        : undefined;
      return (
        <>
          <BotBubble>{interpolate(data.label)}</BotBubble>
          {labels.length > 0 && (
            <OptionsRow options={labels} highlighted={matchedLabel} />
          )}
          {data.switchVariable && (
            <SystemBubble subtle>
              {t("chatFlow.livePreview.switchOn", {
                defaultValue: "Switch on slot ",
              })}
              <code className="font-mono">{data.switchVariable}</code>
              {variableValue
                ? ` = "${variableValue}" → ${matchedLabel ?? "(no match — default branch)"}`
                : t("chatFlow.livePreview.switchUnmocked", {
                    defaultValue: " (slot not mocked — preview cannot resolve the branch)",
                  })}
            </SystemBubble>
          )}
        </>
      );
    }
    case "slot": {
      const op = data.slotOperation || "SET";
      const name = data.slotName || "(no slot)";
      const value =
        op === "DELETE"
          ? "(deletes the slot)"
          : `= ${data.slotValue ? interpolate(data.slotValue) : "(empty)"}`;
      return (
        <SystemBubble>
          {t("chatFlow.livePreview.slotOp", {
            defaultValue: "Internal: ",
          })}
          <code className="font-mono">{name}</code> {op} {value}
        </SystemBubble>
      );
    }
    case "writeSlot": {
      const name = data.outputVariable || "(no slot)";
      const value = data.slotValue
        ? interpolate(data.slotValue)
        : t("chatFlow.livePreview.empty", { defaultValue: "(empty)" });
      return (
        <SystemBubble>
          {t("chatFlow.livePreview.writeSlot", {
            defaultValue: "Internal: writes ",
          })}
          <code className="font-mono">{name}</code> = {value}
        </SystemBubble>
      );
    }
    case "subFlow": {
      const target = data.subFlowName || data.subFlowId || "(not set)";
      return (
        <SystemBubble>
          {t("chatFlow.livePreview.subFlow", {
            defaultValue: "Hands off to sub-flow ",
          })}
          <strong>{target}</strong>
        </SystemBubble>
      );
    }
    case "subFlowSwitch": {
      const varName = data.switchVariable || "(no variable)";
      const routes = (data.switchOptions ?? [])
        .map((o) => `${o.label || "—"} → ${o.subFlowName || o.subFlowId || "(unset)"}`)
        .join(" · ");
      return (
        <SystemBubble>
          {t("chatFlow.livePreview.subFlowSwitch", {
            defaultValue: "Routes by ",
          })}
          <code className="font-mono">{varName}</code>: {routes || "(no options)"}
        </SystemBubble>
      );
    }
    case "persona": {
      const target = data.personaName || data.personaId || "(not set)";
      return (
        <SystemBubble>
          {t("chatFlow.livePreview.persona", {
            defaultValue: "Switches voice to ",
          })}
          <strong>{target}</strong>
        </SystemBubble>
      );
    }
    case "functionCall": {
      const fn = data.functionName || "(no function)";
      return (
        <>
          <SystemBubble>
            {t("chatFlow.livePreview.functionCall", {
              defaultValue: "Calls tool ",
            })}
            <code className="font-mono">{fn}</code>
            {data.toolSource ? ` (${data.toolSource})` : ""}
          </SystemBubble>
          {data.aiInstruction && (
            <BotBubble>{interpolate(data.aiInstruction)}</BotBubble>
          )}
        </>
      );
    }
    default:
      return null;
  }
}

function BotBubble({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <div className="max-w-[90%] self-start rounded-lg border bg-background p-3 text-xs leading-relaxed shadow-sm">
      <div className="mb-1 text-[10px] font-semibold uppercase opacity-70">
        bot
      </div>
      <div className="whitespace-pre-wrap">{children}</div>
    </div>
  );
}

function SystemBubble({
  children,
  subtle = false,
}: Readonly<{ children: React.ReactNode; subtle?: boolean }>) {
  return (
    <div
      className={
        subtle
          ? "max-w-full self-stretch rounded border border-dashed border-border/60 p-2 text-[11px] text-muted-foreground"
          : "max-w-[90%] self-center rounded-lg border border-border/60 bg-muted/60 p-3 text-[11px] italic text-muted-foreground shadow-sm"
      }
    >
      {children}
    </div>
  );
}

function OptionsRow({
  options,
  highlighted,
}: Readonly<{ options: string[]; highlighted?: string }>) {
  return (
    <div className="flex flex-wrap gap-1.5 self-start">
      {options.map((label) => (
        <span
          key={label}
          className={
            label === highlighted
              ? "rounded-full border border-blue-500 bg-blue-500/10 px-2.5 py-1 text-[11px] font-medium text-blue-600 dark:text-blue-300"
              : "rounded-full border border-border/60 bg-background px-2.5 py-1 text-[11px] text-muted-foreground"
          }
        >
          {label}
        </span>
      ))}
    </div>
  );
}

function UserPlaceholder({
  outputVariable,
  validationRule,
  t,
}: Readonly<{
  outputVariable?: string;
  validationRule?: string;
  t: (key: string, options?: Record<string, unknown>) => string;
}>) {
  return (
    <div className="max-w-[90%] self-end rounded-lg border border-dashed border-border/60 bg-background/50 p-3 text-xs italic leading-relaxed text-muted-foreground">
      <div className="mb-1 text-[10px] font-semibold uppercase opacity-70">
        {t("chatFlow.livePreview.user", { defaultValue: "user" })}
      </div>
      {outputVariable
        ? t("chatFlow.livePreview.captureInto", {
            defaultValue: "(visitor reply captured into ",
          })
        : t("chatFlow.livePreview.captureOnly", {
            defaultValue: "(visitor reply, not captured)",
          })}
      {outputVariable && (
        <>
          <code className="not-italic font-mono text-foreground">{outputVariable}</code>)
        </>
      )}
      {validationRule && (
        <div className="mt-1 text-[10px] not-italic">
          {t("chatFlow.livePreview.validation", { defaultValue: "Validation: " })}
          <code className="font-mono">{validationRule}</code>
        </div>
      )}
    </div>
  );
}

/**
 * T107 — renders a {@code formCapture} node's declared fields as a native
 * multi-field form, the way the SDK would surface the `"form"` SSE event.
 */
function FormPreview({
  fields,
  t,
}: Readonly<{
  fields: FlowFormField[];
  t: (key: string, options?: Record<string, unknown>) => string;
}>) {
  return (
    <div className="max-w-[90%] self-end rounded-lg border border-indigo-300 bg-background p-3 dark:border-indigo-800">
      <div className="mb-2 text-[10px] font-semibold uppercase tracking-wide text-indigo-600 dark:text-indigo-300">
        {t("chatFlow.livePreview.form", { defaultValue: "native form" })}
      </div>
      <div className="flex flex-col gap-2">
        {fields.map((field, idx) => (
          // eslint-disable-next-line react/no-array-index-key
          <div key={idx} className="flex flex-col gap-0.5">
            <span className="text-[11px] font-medium text-foreground">
              {field.label || field.name || `field ${idx + 1}`}
              {field.required !== false && <span className="ml-0.5 text-rose-500">*</span>}
            </span>
            {field.type === "select" ? (
              <div className="flex flex-wrap gap-1">
                {(field.options ?? []).map((opt) => (
                  <span
                    key={opt}
                    className="rounded border border-border/60 bg-muted/40 px-2 py-0.5 text-[11px]"
                  >
                    {opt}
                  </span>
                ))}
              </div>
            ) : (
              <div className="rounded border border-dashed border-border/60 bg-background/50 px-2 py-1 text-[11px] italic text-muted-foreground">
                {field.placeholder ||
                  t("chatFlow.livePreview.formInput", {
                    defaultValue: "{{type}} input",
                    type: field.type || "text",
                  })}
              </div>
            )}
            <code className="text-[10px] font-mono text-muted-foreground">→ {field.name}</code>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ─────────────────────────── helpers ─────────────────────────── */

function loadMocks(storageKey: string): string {
  try {
    return window.localStorage.getItem(storageKey) ?? "";
  } catch {
    return "";
  }
}

function parseMocks(text: string): Record<string, string> {
  const out: Record<string, string> = {};
  for (const line of text.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const eq = trimmed.indexOf("=");
    if (eq <= 0) continue;
    const key = trimmed.slice(0, eq).trim();
    const value = trimmed.slice(eq + 1).trim();
    if (key) out[key] = value;
  }
  return out;
}

const SLOT_REFERENCE = /\{\{\s*([\w.-]+)\s*}}/g;

function interpolateSlots(
  text: string | undefined | null,
  mocks: Record<string, string>,
): React.ReactNode {
  if (text == null || text === "") return text;
  // Build a list of React nodes so unresolved {{slot}} can render with a
  // distinct visual hint instead of leaking the literal braces.
  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  for (const match of text.matchAll(SLOT_REFERENCE)) {
    const start = match.index ?? 0;
    if (start > lastIndex) parts.push(text.slice(lastIndex, start));
    const slot = match[1];
    if (slot in mocks) {
      parts.push(
        <span key={`${slot}-${start}`} className="font-semibold text-blue-600 dark:text-blue-400">
          {mocks[slot]}
        </span>,
      );
    } else {
      parts.push(
        <span
          key={`${slot}-${start}`}
          className="rounded bg-amber-100 px-1 font-mono text-[11px] text-amber-900 dark:bg-amber-900/40 dark:text-amber-200"
          title="Slot not mocked"
        >
          {`{{${slot}}}`}
        </span>,
      );
    }
    lastIndex = start + match[0].length;
  }
  if (lastIndex < text.length) parts.push(text.slice(lastIndex));
  return <>{parts}</>;
}
