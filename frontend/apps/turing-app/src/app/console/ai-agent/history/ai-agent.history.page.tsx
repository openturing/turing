import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

import { Card } from "@/components/ui/card";
import { GradientButton } from "@/components/ui/gradient-button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { exportToXlsx } from "@/lib/export-xlsx";
import type { TurChatFlow } from "@/models/agent/chat-flow.model";
import type { TurChatFlowSubmission } from "@/models/agent/chat-flow-submission.model";
import { TurChatFlowService } from "@/services/agent/chat-flow.service";
import { IconDownload, IconInbox } from "@tabler/icons-react";

/**
 * Lists every chat-flow submission (run that reached an end node) for the
 * agent in scope. The user picks a flow from the dropdown; the table shows
 * conversationId + completedAt + one column per captured variable. The
 * column set is the union of variable keys across the loaded submissions —
 * variable schemas can differ run-to-run.
 *
 * @since 2026.2.5
 */
const turChatFlowService = new TurChatFlowService();

const formatDateTime = (iso: string) => {
  if (!iso) return "";
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
};

export default function AIAgentHistoryPage() {
  const { id: agentId } = useParams() as { id: string };
  const { t } = useTranslation();

  const [flows, setFlows] = useState<TurChatFlow[]>([]);
  const [selectedFlowId, setSelectedFlowId] = useState<string>("");
  const [submissions, setSubmissions] = useState<TurChatFlowSubmission[]>([]);
  const [loadingFlows, setLoadingFlows] = useState(true);
  const [loadingSubmissions, setLoadingSubmissions] = useState(false);

  // Load flows of this agent once.
  useEffect(() => {
    if (!agentId) return;
    setLoadingFlows(true);
    turChatFlowService
      .query(agentId)
      .then((list) => {
        setFlows(list);
        // Auto-select the first flow so the user has data to look at on
        // landing — most agents will only have one flow anyway.
        if (list.length > 0 && list[0].id) {
          setSelectedFlowId(list[0].id);
        }
      })
      .catch(() => setFlows([]))
      .finally(() => setLoadingFlows(false));
  }, [agentId]);

  // Load submissions whenever the selected flow changes.
  useEffect(() => {
    if (!agentId || !selectedFlowId) {
      setSubmissions([]);
      return;
    }
    setLoadingSubmissions(true);
    turChatFlowService
      .submissions(agentId, selectedFlowId)
      .then(setSubmissions)
      .catch(() => setSubmissions([]))
      .finally(() => setLoadingSubmissions(false));
  }, [agentId, selectedFlowId]);

  // Variable keys are dynamic — take the union of keys across submissions
  // so a row missing one of the columns doesn't drop it from the table.
  const variableKeys = useMemo(() => {
    const set = new Set<string>();
    for (const s of submissions) {
      for (const k of Object.keys(s.variables ?? {})) {
        set.add(k);
      }
    }
    return [...set].sort((a, b) => a.localeCompare(b));
  }, [submissions]);

  const selectedFlowName = flows.find((f) => f.id === selectedFlowId)?.name ?? "chat-flow";

  const onExport = () => {
    if (submissions.length === 0) return;
    const rows = submissions.map((s) => {
      const flat: Record<string, unknown> = {
        conversationId: s.conversationId,
        completedAt: s.completedAt,
        userId: s.userId ?? "",
        status: s.endNodeId === "__abandoned__"
          ? t("aiAgent.history.status.abandoned")
          : t("aiAgent.history.status.completed"),
      };
      for (const k of variableKeys) {
        flat[k] = s.variables?.[k] ?? "";
      }
      return flat;
    });
    const columns = [
      { key: "conversationId", label: t("aiAgent.history.columns.conversation") },
      { key: "completedAt", label: t("aiAgent.history.columns.completedAt") },
      { key: "userId", label: t("aiAgent.history.columns.user") },
      { key: "status", label: t("aiAgent.history.columns.status") },
      ...variableKeys.map((k) => ({ key: k, label: k })),
    ];
    const safeName = selectedFlowName.replace(/[^a-zA-Z0-9-_]+/g, "-").toLowerCase() || "chat-flow";
    exportToXlsx(rows, columns, `${safeName}-submissions-${new Date().toISOString().slice(0, 10)}`);
  };

  const isEmpty = !loadingSubmissions && submissions.length === 0;

  return (
    <div className="px-4 lg:px-6 py-2 pb-8">
      <Card>
        <div className="flex flex-wrap items-center gap-3 p-4 border-b">
          <div className="flex items-center gap-2 min-w-64">
            <span className="text-sm text-muted-foreground whitespace-nowrap">
              {t("aiAgent.history.flowLabel")}:
            </span>
            <Select
              value={selectedFlowId}
              onValueChange={setSelectedFlowId}
              disabled={loadingFlows || flows.length === 0}
            >
              <SelectTrigger className="h-8 text-xs flex-1">
                <SelectValue placeholder={t("aiAgent.history.flowPlaceholder")} />
              </SelectTrigger>
              <SelectContent>
                {flows.map((f) => (
                  <SelectItem key={f.id} value={f.id ?? ""}>
                    {f.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="ml-auto flex items-center gap-3">
            <span className="text-xs text-muted-foreground">
              {t("aiAgent.history.totalSubmissions", { count: submissions.length })}
            </span>
            <GradientButton
              variant="outline"
              size="sm"
              className="h-8"
              disabled={submissions.length === 0}
              onClick={onExport}
            >
              <IconDownload className="h-4 w-4 mr-1" />
              Excel
            </GradientButton>
          </div>
        </div>

        {isEmpty ? (
          <div className="flex flex-col items-center justify-center gap-2 py-16 text-muted-foreground">
            <IconInbox className="h-10 w-10 opacity-50" />
            <p className="text-sm">{t("aiAgent.history.empty")}</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="px-5">
                    {t("aiAgent.history.columns.completedAt")}
                  </TableHead>
                  <TableHead className="px-5">
                    {t("aiAgent.history.columns.user")}
                  </TableHead>
                  <TableHead className="px-5">
                    {t("aiAgent.history.columns.status")}
                  </TableHead>
                  {variableKeys.map((k) => (
                    <TableHead key={k} className="px-5">
                      {k}
                    </TableHead>
                  ))}
                  <TableHead className="px-5">
                    {t("aiAgent.history.columns.conversation")}
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {submissions.map((s, idx) => {
                  const abandoned = s.endNodeId === "__abandoned__";
                  return (
                    <TableRow key={`${s.conversationId}-${s.completedAt}-${idx}`}>
                      <TableCell className="px-5 text-xs">
                        {formatDateTime(s.completedAt)}
                      </TableCell>
                      <TableCell className="px-5 text-sm">
                        {s.userId ?? <span className="text-muted-foreground italic">—</span>}
                      </TableCell>
                      <TableCell className="px-5 text-xs">
                        <span
                          className={
                            abandoned
                              ? "rounded-md bg-amber-500/10 px-2 py-0.5 text-amber-600 dark:text-amber-400"
                              : "rounded-md bg-emerald-500/10 px-2 py-0.5 text-emerald-600 dark:text-emerald-400"
                          }
                        >
                          {abandoned
                            ? t("aiAgent.history.status.abandoned")
                            : t("aiAgent.history.status.completed")}
                        </span>
                      </TableCell>
                      {variableKeys.map((k) => (
                        <TableCell key={k} className="px-5 text-sm">
                          {s.variables?.[k] ?? ""}
                        </TableCell>
                      ))}
                      <TableCell className="px-5 font-mono text-xs text-muted-foreground">
                        {s.conversationId}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
        )}
      </Card>
    </div>
  );
}
