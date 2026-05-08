import {
  useChatAnalyticsHealth,
  useChatAnalyticsSessions,
  useChatAnalyticsTranscript,
} from "@/api/queries/chat-analytics.queries";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { TurChatAnalyticsSession } from "@/models/chat-analytics/chat-analytics.model";
import { IconMessageOff } from "@tabler/icons-react";
import { useState } from "react";

const NEUTRAL = "outline";
const SUCCESS = "default";
const DESTRUCTIVE = "destructive";
const SECONDARY = "secondary";

function formatTimestamp(value?: string | null): string {
  if (!value) return "—";
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

function formatDuration(ms?: number | null): string {
  if (!ms || ms <= 0) return "—";
  if (ms < 1000) return `${ms} ms`;
  const seconds = Math.round(ms / 1000);
  if (seconds < 60) return `${seconds} s`;
  const minutes = Math.floor(seconds / 60);
  const remSeconds = seconds % 60;
  return `${minutes}m ${remSeconds}s`;
}

function outcomeVariant(outcome?: string | null): typeof NEUTRAL | typeof SUCCESS | typeof DESTRUCTIVE | typeof SECONDARY {
  switch (outcome) {
    case "COMPLETED":
      return SUCCESS;
    case "ABANDONED":
    case "ERROR":
      return DESTRUCTIVE;
    case "IN_PROGRESS":
      return SECONDARY;
    default:
      return NEUTRAL;
  }
}

function sentimentVariant(sentiment?: string | null) {
  switch (sentiment) {
    case "POSITIVE":
      return SUCCESS;
    case "NEGATIVE":
    case "FRUSTRATED":
      return DESTRUCTIVE;
    case "NEUTRAL":
      return NEUTRAL;
    default:
      return SECONDARY;
  }
}

function goalVariant(goal?: string | null) {
  switch (goal) {
    case "YES":
      return SUCCESS;
    case "NO":
      return DESTRUCTIVE;
    case "PARTIAL":
      return SECONDARY;
    default:
      return NEUTRAL;
  }
}

function MetaRow({ label, value }: Readonly<{ label: string; value?: string | number | null }>) {
  return (
    <div className="grid grid-cols-3 gap-2 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span className="col-span-2 font-mono break-all">{value ?? "—"}</span>
    </div>
  );
}

interface DetailPanelProps {
  conversationId: string | null;
  onClose: () => void;
}

function DetailPanel({ conversationId, onClose }: Readonly<DetailPanelProps>) {
  const { data, isLoading, isError } = useChatAnalyticsTranscript(conversationId);

  return (
    <Sheet open={Boolean(conversationId)} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="right" className="w-full sm:max-w-2xl overflow-y-auto">
        <SheetHeader>
          <SheetTitle>Conversation detail</SheetTitle>
          <SheetDescription className="font-mono text-xs">{conversationId}</SheetDescription>
        </SheetHeader>

        {isLoading && (
          <p className="text-muted-foreground py-12 text-center text-sm">Loading transcript…</p>
        )}
        {isError && (
          <p className="text-destructive py-12 text-center text-sm">Failed to load transcript.</p>
        )}
        {!isLoading && !isError && !data && (
          <p className="text-muted-foreground py-12 text-center text-sm">
            Session not found in the analytics store (it may have been purged).
          </p>
        )}

        {data && (
          <div className="space-y-4 px-4 pb-6">
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Session</CardTitle>
              </CardHeader>
              <CardContent className="space-y-1.5">
                <MetaRow label="Started"   value={formatTimestamp(data.session.startedAt)} />
                <MetaRow label="Completed" value={formatTimestamp(data.session.completedAt)} />
                <MetaRow label="Duration"  value={formatDuration(data.session.durationMs)} />
                <MetaRow label="Outcome"   value={data.session.outcome} />
                <MetaRow label="Agent"     value={data.session.agentTitle ?? data.session.agentId} />
                <MetaRow label="Persona"   value={data.session.personaName ?? data.session.personaId} />
                <MetaRow label="LLM"       value={data.session.llmInstanceId} />
                <MetaRow label="Locale"    value={data.session.locale} />
                <MetaRow label="User"      value={data.session.userId} />
                <MetaRow label="Turns"     value={data.session.totalTurns ?? 0} />
                <MetaRow label="Tokens in"  value={data.session.totalTokensIn ?? 0} />
                <MetaRow label="Tokens out" value={data.session.totalTokensOut ?? 0} />
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-base">AI enrichment</CardTitle>
                <CardDescription>
                  {data.session.processedAt
                    ? `Classified ${formatTimestamp(data.session.processedAt)}`
                    : "Awaiting enrichment — the scheduler runs every 5 minutes."}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                <div className="flex flex-wrap gap-2">
                  {data.session.intentLabel && (
                    <Badge variant={NEUTRAL}>
                      Intent: {data.session.intentLabel}
                      {typeof data.session.intentConfidence === "number" &&
                        ` · ${Math.round(data.session.intentConfidence * 100)}%`}
                    </Badge>
                  )}
                  {data.session.goalAchieved && (
                    <Badge variant={goalVariant(data.session.goalAchieved)}>
                      Goal: {data.session.goalAchieved}
                    </Badge>
                  )}
                  {data.session.sentiment && (
                    <Badge variant={sentimentVariant(data.session.sentiment)}>
                      Sentiment: {data.session.sentiment}
                    </Badge>
                  )}
                </div>
                {data.session.goalSummary && (
                  <div>
                    <p className="text-muted-foreground text-xs">Goal summary</p>
                    <p>{data.session.goalSummary}</p>
                  </div>
                )}
                {data.session.keyTerms && data.session.keyTerms.length > 0 && (
                  <div>
                    <p className="text-muted-foreground text-xs">Key terms</p>
                    <div className="flex flex-wrap gap-1 mt-1">
                      {data.session.keyTerms.map((term) => (
                        <Badge key={term} variant={NEUTRAL}>
                          {term}
                        </Badge>
                      ))}
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="text-base">
                  Transcript ({data.messages.length} {data.messages.length === 1 ? "message" : "messages"})
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {data.messages.length === 0 ? (
                  <p className="text-muted-foreground py-4 text-center text-sm">
                    No messages stored — chat memory may be disabled or already purged.
                  </p>
                ) : (
                  data.messages.map((msg, idx) => (
                    <div
                      // Same conversation may have repeated content; index disambiguates.
                      key={`${msg.timestamp ?? idx}-${idx}`}
                      className="rounded-md border p-3"
                    >
                      <div className="flex items-center justify-between gap-2 mb-1">
                        <Badge variant={msg.role === "assistant" ? SUCCESS : NEUTRAL}>
                          {msg.role ?? "unknown"}
                        </Badge>
                        <span className="text-muted-foreground text-xs">
                          {formatTimestamp(msg.timestamp)}
                        </span>
                      </div>
                      <p className="whitespace-pre-wrap text-sm">{msg.content ?? ""}</p>
                    </div>
                  ))
                )}
              </CardContent>
            </Card>
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}

function SessionRow({
  row,
  onSelect,
}: Readonly<{ row: TurChatAnalyticsSession; onSelect: (id: string) => void }>) {
  return (
    <TableRow className="cursor-pointer" onClick={() => onSelect(row.conversationId)}>
      <TableCell className="font-mono text-xs whitespace-nowrap">
        {formatTimestamp(row.startedAt)}
      </TableCell>
      <TableCell>
        <Badge variant={outcomeVariant(row.outcome)}>{row.outcome ?? "—"}</Badge>
      </TableCell>
      <TableCell>{row.intentLabel ?? "—"}</TableCell>
      <TableCell>
        {row.goalAchieved ? (
          <Badge variant={goalVariant(row.goalAchieved)}>{row.goalAchieved}</Badge>
        ) : (
          "—"
        )}
      </TableCell>
      <TableCell>
        {row.sentiment ? (
          <Badge variant={sentimentVariant(row.sentiment)}>{row.sentiment}</Badge>
        ) : (
          "—"
        )}
      </TableCell>
      <TableCell
        className="text-xs"
        title={row.agentId ?? undefined}
      >
        {row.agentTitle ?? row.agentId ?? "—"}
      </TableCell>
      <TableCell
        className="text-xs"
        title={row.personaId ?? undefined}
      >
        {row.personaName ?? row.personaId ?? "—"}
      </TableCell>
      <TableCell className="text-right">{formatDuration(row.durationMs)}</TableCell>
    </TableRow>
  );
}

export default function ChatAnalyticsPage() {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const { data: health } = useChatAnalyticsHealth();
  const { data: sessions, isLoading, isError } = useChatAnalyticsSessions({ limit: 100 });

  if (health && !health.enabled) {
    return (
      <div className="px-4 lg:px-6">
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-16 text-center">
            <IconMessageOff className="text-muted-foreground size-10" />
            <p className="text-base font-medium">Chat analytics is disabled</p>
            <p className="text-muted-foreground max-w-md text-sm">
              Set <span className="font-mono">turing.logging.engine</span> to{" "}
              <span className="font-mono">mongodb</span> or{" "}
              <span className="font-mono">redis</span> to start collecting chat session analytics.
            </p>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="space-y-6 px-4 lg:px-6">
      <Card>
        <CardHeader>
          <CardTitle>Recent chat sessions</CardTitle>
          <CardDescription>
            Last 100 sessions over the past 7 days. Click any row to drill into the transcript and AI enrichment.
            {health && ` Engine: ${health.engine}.`}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading && (
            <p className="text-muted-foreground py-12 text-center text-sm">Loading sessions…</p>
          )}
          {isError && (
            <p className="text-destructive py-12 text-center text-sm">Failed to load sessions.</p>
          )}
          {!isLoading && !isError && (!sessions || sessions.length === 0) && (
            <p className="text-muted-foreground py-12 text-center text-sm">
              No chat sessions recorded yet — start a conversation in the chat console.
            </p>
          )}
          {sessions && sessions.length > 0 && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Started</TableHead>
                  <TableHead>Outcome</TableHead>
                  <TableHead>Intent</TableHead>
                  <TableHead>Goal</TableHead>
                  <TableHead>Sentiment</TableHead>
                  <TableHead>Agent</TableHead>
                  <TableHead>Persona</TableHead>
                  <TableHead className="text-right">Duration</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {sessions.map((row) => (
                  <SessionRow key={row.conversationId} row={row} onSelect={setSelectedId} />
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <DetailPanel conversationId={selectedId} onClose={() => setSelectedId(null)} />
    </div>
  );
}
