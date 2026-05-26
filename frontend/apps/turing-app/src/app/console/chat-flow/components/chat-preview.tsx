import type { Edge, Node } from "@xyflow/react";
import { IconMessageChatbot, IconX } from "@tabler/icons-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";

import type { FlowNodeData } from "../types";

/**
 * Mock chat preview. Walks the graph starting from the {@code start} node, picking the first
 * outgoing edge at each step, and renders what the end user would see. Branching is not executed —
 * the condition nodes simply surface their expression. The goal is to help non-technical authors
 * sanity-check the flow without running a real LLM.
 *
 * @since 2026.2.4
 */

interface ChatPreviewProps {
  nodes: Node<FlowNodeData>[];
  edges: Edge[];
  onClose: () => void;
}

export function ChatPreview({ nodes, edges, onClose }: ChatPreviewProps) {
  const { t } = useTranslation();

  const transcript = useMemo(() => buildTranscript(nodes, edges), [nodes, edges]);

  return (
    <aside className="flex h-full w-96 flex-col border-l bg-background shadow-xl">
      <header className="flex items-center justify-between border-b bg-gradient-to-r from-blue-600 to-indigo-600 px-4 py-3 text-white">
        <div className="flex items-center gap-2">
          <IconMessageChatbot className="size-5" />
          <div className="text-sm font-semibold">{t("chatFlow.preview.title")}</div>
        </div>
        <Button variant="ghost" size="icon" onClick={onClose} className="text-white hover:bg-white/20" aria-label={t("chatFlow.preview.close")}>
          <IconX className="size-4" />
        </Button>
      </header>
      <div className="flex flex-1 flex-col gap-3 overflow-auto bg-muted/30 p-4">
        {transcript.length === 0 ? (
          <div className="mt-8 text-center text-sm text-muted-foreground">
            {t("chatFlow.preview.empty")}
          </div>
        ) : (
          transcript.map((entry, index) => (
            <div
              key={index}
              className={`max-w-[85%] rounded-lg border p-3 text-xs leading-relaxed shadow-sm ${
                entry.actor === "bot"
                  ? "self-start bg-background"
                  : "self-end border-transparent bg-blue-500 text-white"
              }`}
            >
              <div className="mb-1 text-[10px] font-semibold uppercase opacity-70">
                {entry.actor === "bot" ? t("chatFlow.preview.bot") : t("chatFlow.preview.user")}
              </div>
              {entry.text}
            </div>
          ))
        )}
      </div>
      <footer className="border-t p-3 text-[11px] text-muted-foreground">
        {t("chatFlow.preview.footer")}
      </footer>
    </aside>
  );
}

interface TranscriptEntry {
  actor: "bot" | "user";
  text: string;
}

function buildTranscript(nodes: Node<FlowNodeData>[], edges: Edge[]): TranscriptEntry[] {
  const start = nodes.find((n) => (n.data as FlowNodeData).type === "start");
  if (!start) return [];
  const outgoing = (id: string) => edges.find((e) => e.source === id);
  const byId = new Map(nodes.map((n) => [n.id, n]));

  const entries: TranscriptEntry[] = [];
  const visited = new Set<string>();
  let current: Node<FlowNodeData> | undefined = start;
  while (current && !visited.has(current.id)) {
    visited.add(current.id);
    const data = current.data as FlowNodeData;
    appendFromNode(entries, data);
    const next = outgoing(current.id);
    current = next ? byId.get(next.target) : undefined;
  }
  return entries;
}

function appendFromNode(entries: TranscriptEntry[], data: FlowNodeData) {
  switch (data.type) {
    case "aiQuestion":
      entries.push({ actor: "bot", text: data.aiInstruction || data.label });
      if (data.outputVariable) {
        entries.push({ actor: "user", text: `<${data.outputVariable}>` });
      }
      break;
    case "functionCall":
      entries.push({ actor: "bot", text: `→ ${data.functionName ?? data.label}` });
      if (data.aiInstruction) {
        entries.push({ actor: "bot", text: data.aiInstruction });
      }
      break;
    case "condition":
      entries.push({ actor: "bot", text: `[${data.label}] ${data.conditionExpression ?? ""}` });
      break;
    case "switch": {
      const labels = (data.switchOptions ?? []).map((o) => o.label || "—").join(" | ");
      entries.push({ actor: "bot", text: `⇆ ${data.label}: ${labels || "(no options)"}` });
      break;
    }
    case "subFlow": {
      const target = data.subFlowName ?? data.subFlowId ?? "(not set)";
      entries.push({ actor: "bot", text: `↳ ${data.label}: ${target}` });
      break;
    }
    case "subFlowSwitch": {
      const routes = (data.switchOptions ?? [])
        .map((o) => `${o.label || "—"} → ${o.subFlowName ?? o.subFlowId ?? "(unset)"}`)
        .join(" | ");
      entries.push({ actor: "bot", text: `↳⇆ ${data.label}: ${routes || "(no options)"}` });
      break;
    }
    case "slot": {
      const op = data.slotOperation ?? "SET";
      const name = data.slotName ?? "(no slot)";
      const detail = op === "DELETE" ? "delete" : `= ${data.slotValue ?? "(empty)"}`;
      entries.push({ actor: "bot", text: `⚙ ${data.label}: ${name} ${detail}` });
      break;
    }
    case "start":
    case "end":
    case "persona":
      break;
  }
}
