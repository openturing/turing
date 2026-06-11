import {
  useParkedConversations,
  useResumeParkedConversation,
} from "@/api/queries/parked-conversation.queries";
import { LoadProvider } from "@/components/loading-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { TurParkedConversation } from "@/models/parked-conversation/parked-conversation.model";
import { IconPlayerPause, IconPlayerPlay, IconRefresh } from "@tabler/icons-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "@viglet/viglet-design-system";

/**
 * T122 / §IX.6.b — admin dashboard listing every conversation suspended on a
 * chat-flow {@code suspend} node (T121): its reason, owning agent/flow, and how
 * long it has been waiting, with a one-click "unblock" that resumes the flow
 * server-side (the operator counterpart to {@code POST .../chat/resume}).
 */
export default function ParkedConversationsPage() {
  const { t } = useTranslation();
  const { data: parked, isError, refetch, isFetching } = useParkedConversations();
  const resumeMutation = useResumeParkedConversation();
  const [resumingId, setResumingId] = useState<string | null>(null);

  const error = isError
    ? t("common.connectionError", { resource: t("parkedConversations.title").toLowerCase() })
    : null;

  async function onUnblock(row: TurParkedConversation) {
    setResumingId(row.conversationId);
    try {
      const result = await resumeMutation.mutateAsync(row.conversationId);
      if (result.wasParked) {
        toast.success(t("parkedConversations.unblocked", { count: result.resumed }));
      } else {
        toast.info(t("parkedConversations.nothingToResume"));
      }
    } catch (e) {
      console.error(e);
      toast.error(t("parkedConversations.notUnblocked"));
    } finally {
      setResumingId(null);
    }
  }

  function formatWaiting(seconds: number): string {
    if (seconds < 60) return t("parkedConversations.waiting.seconds", { count: Math.round(seconds) });
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return t("parkedConversations.waiting.minutes", { count: minutes });
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return t("parkedConversations.waiting.hours", { count: hours });
    const days = Math.floor(hours / 24);
    return t("parkedConversations.waiting.days", { count: days });
  }

  return (
    <LoadProvider checkIsNotUndefined={parked} error={error}>
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-2xl font-bold flex items-center gap-2">
            <IconPlayerPause className="text-blue-600" />
            {t("parkedConversations.title")}
          </h2>
          <p className="text-sm text-muted-foreground mt-1">
            {t("parkedConversations.description")}
          </p>
        </div>
        <Button variant="outline" onClick={() => refetch()} disabled={isFetching}>
          <IconRefresh className="mr-2 h-4 w-4" />
          {t("common.refresh")}
        </Button>
      </div>

      {parked && parked.length === 0 ? (
        <div className="text-center py-12 border border-dashed rounded-lg">
          <IconPlayerPause className="mx-auto h-12 w-12 text-muted-foreground" />
          <h3 className="mt-4 text-lg font-semibold">{t("parkedConversations.blankTitle")}</h3>
          <p className="mt-2 text-sm text-muted-foreground">
            {t("parkedConversations.blankDescription")}
          </p>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("parkedConversations.fields.conversation")}</TableHead>
              <TableHead>{t("parkedConversations.fields.agent")}</TableHead>
              <TableHead>{t("parkedConversations.fields.flow")}</TableHead>
              <TableHead>{t("parkedConversations.fields.reason")}</TableHead>
              <TableHead className="w-36">{t("parkedConversations.fields.waiting")}</TableHead>
              <TableHead className="w-32 text-right">{t("common.actions")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {parked?.map((row) => (
              <TableRow key={`${row.flowId}:${row.conversationId}`}>
                <TableCell className="font-mono text-xs">{row.conversationId}</TableCell>
                <TableCell>{row.agentTitle ?? row.agentId ?? "—"}</TableCell>
                <TableCell className="text-sm">{row.flowName}</TableCell>
                <TableCell>
                  <Badge variant="secondary">{row.reason}</Badge>
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {formatWaiting(row.waitingSeconds)}
                </TableCell>
                <TableCell className="text-right">
                  <Button
                    variant="ghost"
                    size="sm"
                    disabled={resumingId === row.conversationId}
                    onClick={() => onUnblock(row)}>
                    <IconPlayerPlay className="mr-2 h-4 w-4 text-emerald-600" />
                    {t("parkedConversations.unblock")}
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </LoadProvider>
  );
}
