import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { GradientButton } from "@/components/ui/gradient-button";
import { GradientSwitch } from "@/components/ui/gradient-switch";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import type {
  TurConversationState,
  TurSpectatorMessage,
  TurSpectatorWorkspaceEvent,
} from "@/models/spectator/spectator.model";
import { TurSpectatorService } from "@/services/spectator/spectator.service";
import {
  IconBroadcast,
  IconFileText,
  IconRobot,
  IconSteeringWheel,
  IconUser,
} from "@tabler/icons-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";

const service = new TurSpectatorService();

/**
 * T120 — Spectator + Co-pilot mode. An operator opens
 * {@code /console/conversation/{id}?spectate=true} and watches a conversation
 * they did not initiate: the chat transcript, slots, and workspace artifacts
 * stream live over three conversation-scoped SSE buses. Toggling co-pilot mode
 * (the {@code ?manual=true} switch) lets them "take the wheel" — type a reply
 * that becomes the assistant message for the turn (the LLM is skipped) while the
 * flow advances and telemetry record exactly as a normal turn.
 *
 * @since 2026.3.4
 */
export default function ConversationPage() {
  const { conversationId = "" } = useParams<{ conversationId: string }>();
  const [searchParams, setSearchParams] = useSearchParams();

  const [state, setState] = useState<TurConversationState | null>(null);
  const [messages, setMessages] = useState<TurSpectatorMessage[]>([]);
  const [slots, setSlots] = useState<Record<string, string>>({});
  const [artifacts, setArtifacts] = useState<Map<string, TurSpectatorWorkspaceEvent>>(new Map());
  const [connected, setConnected] = useState(false);

  // Co-pilot composer
  const manual = searchParams.get("manual") === "true";
  const [agentId, setAgentId] = useState(searchParams.get("agentId") ?? "");
  const [userMessage, setUserMessage] = useState("");
  const [assistantMessage, setAssistantMessage] = useState("");
  const [sending, setSending] = useState(false);

  const transcriptRef = useRef<HTMLDivElement>(null);

  // Subscribe to the three SSE buses for the lifetime of the conversation id.
  useEffect(() => {
    if (!conversationId) return;
    let cancelled = false;
    service.state(conversationId).then(
      (s) => !cancelled && setState(s),
      () => undefined,
    );
    const messageSource = service.openMessages(
      conversationId,
      (event) => setMessages((prev) => [...prev, event]),
      () => setConnected(false),
    );
    messageSource.onopen = () => setConnected(true);
    const slotSource = service.openSlots(conversationId, (event) => setSlots(event.slots ?? {}));
    const workspaceSource = service.openWorkspace(conversationId, (event) =>
      setArtifacts((prev) => {
        const next = new Map(prev);
        if (event.event === "delete") next.delete(event.key);
        else next.set(event.key, event);
        return next;
      }),
    );
    return () => {
      cancelled = true;
      messageSource.close();
      slotSource.close();
      workspaceSource.close();
    };
  }, [conversationId]);

  // Keep the transcript pinned to the newest turn.
  useEffect(() => {
    transcriptRef.current?.scrollTo({ top: transcriptRef.current.scrollHeight });
  }, [messages]);

  const setManual = useCallback(
    (on: boolean) => {
      const next = new URLSearchParams(searchParams);
      if (on) next.set("manual", "true");
      else next.delete("manual");
      setSearchParams(next, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  const slotEntries = useMemo(() => Object.entries(slots), [slots]);
  const artifactList = useMemo(() => Array.from(artifacts.values()), [artifacts]);

  const onTakeWheel = useCallback(async () => {
    if (!assistantMessage.trim()) {
      toast.error("Type the reply that should become the assistant message.");
      return;
    }
    if (!agentId.trim()) {
      toast.error("Agent id is required to take the wheel.");
      return;
    }
    setSending(true);
    try {
      const result = await service.manualTurn(conversationId, {
        agentId: agentId.trim(),
        flowId: state?.flowId ?? undefined,
        userMessage: userMessage.trim() || undefined,
        assistantMessage: assistantMessage.trim(),
      });
      if (result.ok) {
        toast.success("Manual turn delivered.");
        setUserMessage("");
        setAssistantMessage("");
        // Refresh the header so the post-advance cursor shows.
        service.state(conversationId).then(setState, () => undefined);
      } else {
        toast.error(result.error ?? "Manual turn rejected.");
      }
    } catch {
      toast.error("Could not deliver the manual turn.");
    } finally {
      setSending(false);
    }
  }, [agentId, assistantMessage, conversationId, state?.flowId, userMessage]);

  if (!conversationId) {
    return <div className="p-6 text-muted-foreground">No conversation id.</div>;
  }

  return (
    <div className="flex flex-col h-full gap-4 p-4">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold flex items-center gap-2">
            <IconBroadcast className="text-blue-600" />
            Spectate conversation
          </h2>
          <p className="text-sm text-muted-foreground mt-1 font-mono">{conversationId}</p>
        </div>
        <div className="flex items-center gap-3">
          <Badge variant={connected ? "default" : "secondary"}>
            {connected ? "live" : "connecting…"}
          </Badge>
          {state?.flowName && <Badge variant="outline">flow: {state.flowName}</Badge>}
          {state?.currentNodeId && <Badge variant="outline">node: {state.currentNodeId}</Badge>}
          {state?.suspendedReason && (
            <Badge variant="secondary">⏸ {state.suspendedReason}</Badge>
          )}
          <div className="flex items-center gap-2">
            <IconSteeringWheel className="h-4 w-4 text-muted-foreground" />
            <span className="text-sm">Co-pilot</span>
            <GradientSwitch checked={manual} onCheckedChange={setManual} />
          </div>
        </div>
      </div>

      {/* Three live panes */}
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-4 flex-1 min-h-0">
        {/* Transcript */}
        <Card className="flex flex-col min-h-0 p-0 overflow-hidden">
          <div
            ref={transcriptRef}
            className="flex-1 overflow-auto p-4 space-y-3"
          >
            {messages.length === 0 && (
              <p className="text-sm text-muted-foreground">No messages yet.</p>
            )}
            {messages.map((m, i) => (
              <div
                key={`${m.epochMillis}-${i}`}
                className={`flex gap-2 ${m.role === "user" ? "" : "flex-row-reverse"}`}
              >
                <div className="mt-1 shrink-0">
                  {m.role === "user" ? (
                    <IconUser className="h-5 w-5 text-slate-500" />
                  ) : (
                    <IconRobot className={`h-5 w-5 ${m.manual ? "text-indigo-600" : "text-blue-600"}`} />
                  )}
                </div>
                <div
                  className={`rounded-lg px-3 py-2 max-w-[80%] text-sm whitespace-pre-wrap ${
                    m.role === "user"
                      ? "bg-muted"
                      : "bg-blue-50 dark:bg-blue-950/40"
                  }`}
                >
                  {m.manual && (
                    <Badge variant="secondary" className="mb-1">operator</Badge>
                  )}
                  <div>{m.content}</div>
                </div>
              </div>
            ))}
          </div>

          {/* Co-pilot composer */}
          {manual && (
            <div className="border-t p-3 space-y-2 bg-muted/30">
              <div className="flex items-center gap-2 text-sm font-medium">
                <IconSteeringWheel className="h-4 w-4 text-indigo-600" />
                Take the wheel — your reply becomes the assistant message (LLM skipped)
              </div>
              <Input
                placeholder="Agent id (owns this conversation)"
                value={agentId}
                onChange={(e) => setAgentId(e.target.value)}
              />
              <Textarea
                placeholder="Visitor message being answered (optional — advances the flow)"
                value={userMessage}
                onChange={(e) => setUserMessage(e.target.value)}
                rows={2}
              />
              <Textarea
                placeholder="Your reply (becomes the assistant message)"
                value={assistantMessage}
                onChange={(e) => setAssistantMessage(e.target.value)}
                rows={3}
              />
              <div className="flex justify-end">
                <GradientButton onClick={onTakeWheel} disabled={sending}>
                  <IconSteeringWheel className="mr-2 h-4 w-4" />
                  {sending ? "Delivering…" : "Deliver reply"}
                </GradientButton>
              </div>
            </div>
          )}
        </Card>

        {/* Slots + workspace */}
        <div className="flex flex-col gap-4 min-h-0">
          <Card className="p-4 flex-1 min-h-0 overflow-auto">
            <h3 className="text-sm font-semibold mb-2">Slots ({slotEntries.length})</h3>
            {slotEntries.length === 0 ? (
              <p className="text-xs text-muted-foreground">No slots captured yet.</p>
            ) : (
              <dl className="space-y-1.5">
                {slotEntries.map(([key, value]) => (
                  <div key={key} className="text-xs">
                    <dt className="font-mono text-muted-foreground">{key}</dt>
                    <dd className="wrap-break-word">{value}</dd>
                  </div>
                ))}
              </dl>
            )}
          </Card>
          <Card className="p-4 flex-1 min-h-0 overflow-auto">
            <h3 className="text-sm font-semibold mb-2 flex items-center gap-1">
              <IconFileText className="h-4 w-4" /> Workspace ({artifactList.length})
            </h3>
            {artifactList.length === 0 ? (
              <p className="text-xs text-muted-foreground">No artifacts yet.</p>
            ) : (
              <ul className="space-y-1.5">
                {artifactList.map((a) => (
                  <li key={a.key} className="text-xs flex items-center justify-between gap-2">
                    <span className="font-mono break-all">{a.key}</span>
                    {a.signedUrl && (
                      <Button variant="ghost" size="sm" asChild>
                        <a href={a.signedUrl} target="_blank" rel="noreferrer">open</a>
                      </Button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}
