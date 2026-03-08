import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { PageHeader } from "@/components/page-header";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
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
import type {
  TurDailyUsageRow,
  TurMonthlySummaryRow,
  TurUsageReport,
} from "@/models/llm/llm-token-usage.model";
import { TurLLMTokenUsageService } from "@/services/llm/llm-token-usage.service";
import { TurLLMInstanceService } from "@/services/llm/llm.service";
import { IconArrowLeft, IconArrowRight, IconChartBar } from "@tabler/icons-react";
import { useCallback, useEffect, useState } from "react";

const service = new TurLLMTokenUsageService();
const llmService = new TurLLMInstanceService();

function formatTokens(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return n.toLocaleString();
}

function getMonthOptions(): { value: string; label: string }[] {
  const options: { value: string; label: string }[] = [];
  const now = new Date();
  for (let i = 0; i < 12; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    const value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
    const label = d.toLocaleDateString(undefined, {
      year: "numeric",
      month: "long",
    });
    options.push({ value, label });
  }
  return options;
}

export default function TokenUsagePage() {
  const monthOptions = getMonthOptions();
  const [selectedMonth, setSelectedMonth] = useState(monthOptions[0].value);
  const [report, setReport] = useState<TurUsageReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [hasEnabledLlm, setHasEnabledLlm] = useState<boolean | null>(null);

  useEffect(() => {
    llmService.query().then((instances) => {
      setHasEnabledLlm(instances.some((i) => i.enabled === 1));
    });
  }, []);

  const load = useCallback((month: string) => {
    setLoading(true);
    service
      .getReport(month)
      .then(setReport)
      .catch(() => setReport(null))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load(selectedMonth);
  }, [selectedMonth, load]);

  const handlePrev = () => {
    const idx = monthOptions.findIndex((o) => o.value === selectedMonth);
    if (idx < monthOptions.length - 1) setSelectedMonth(monthOptions[idx + 1].value);
  };

  const handleNext = () => {
    const idx = monthOptions.findIndex((o) => o.value === selectedMonth);
    if (idx > 0) setSelectedMonth(monthOptions[idx - 1].value);
  };

  const currentIdx = monthOptions.findIndex((o) => o.value === selectedMonth);

  if (hasEnabledLlm === false) {
    return (
      <>
        <PageHeader turIcon={IconChartBar} title={"Token Usage"} />
        <BlankSlate
          icon={IconChartBar}
          title="No Language Model Available"
          description="To view token usage, create and enable at least one LLM instance."
          buttonText="Create LLM Instance"
          urlNew={ROUTES.LLM_INSTANCE + "/new"}
        />
      </>
    );
  }

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Token Usage</h1>
          <p className="text-muted-foreground text-sm">
            Monthly consumption report for LLM instances
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handlePrev}
            disabled={currentIdx >= monthOptions.length - 1}
            className="rounded-md p-1.5 hover:bg-accent disabled:opacity-30"
            title="Previous month"
          >
            <IconArrowLeft className="size-4" />
          </button>
          <Select value={selectedMonth} onValueChange={setSelectedMonth}>
            <SelectTrigger className="w-[180px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {monthOptions.map((o) => (
                <SelectItem key={o.value} value={o.value}>
                  {o.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <button
            onClick={handleNext}
            disabled={currentIdx <= 0}
            className="rounded-md p-1.5 hover:bg-accent disabled:opacity-30"
            title="Next month"
          >
            <IconArrowRight className="size-4" />
          </button>
        </div>
      </div>

      {loading ? (
        <div className="text-muted-foreground py-12 text-center">Loading...</div>
      ) : !report ? (
        <div className="text-muted-foreground py-12 text-center">
          Failed to load usage data.
        </div>
      ) : (
        <>
          {/* Summary Cards */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <SummaryCard title="Total Requests" value={report.totalRequests.toLocaleString()} />
            <SummaryCard
              title="Input Tokens"
              value={formatTokens(report.totalInputTokens)}
            />
            <SummaryCard
              title="Output Tokens"
              value={formatTokens(report.totalOutputTokens)}
            />
            <SummaryCard
              title="Total Tokens"
              value={formatTokens(report.totalTokens)}
            />
          </div>

          {/* Monthly Summary by Model */}
          <Card>
            <CardHeader>
              <CardTitle>Summary by Model</CardTitle>
              <CardDescription>
                Aggregated token usage per LLM instance for the selected month
              </CardDescription>
            </CardHeader>
            <CardContent>
              {report.summary.length === 0 ? (
                <p className="text-muted-foreground py-4 text-center text-sm">
                  No usage data for this period.
                </p>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Instance</TableHead>
                      <TableHead>Vendor</TableHead>
                      <TableHead>Model</TableHead>
                      <TableHead className="text-right">Requests</TableHead>
                      <TableHead className="text-right">Input</TableHead>
                      <TableHead className="text-right">Output</TableHead>
                      <TableHead className="text-right">Total</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {report.summary.map((row: TurMonthlySummaryRow, i: number) => (
                      <TableRow key={i}>
                        <TableCell className="font-medium">
                          {row.instanceTitle}
                        </TableCell>
                        <TableCell>{row.vendorId}</TableCell>
                        <TableCell className="text-muted-foreground">
                          {row.modelName}
                        </TableCell>
                        <TableCell className="text-right">
                          {row.requestCount.toLocaleString()}
                        </TableCell>
                        <TableCell className="text-right">
                          {formatTokens(row.inputTokens)}
                        </TableCell>
                        <TableCell className="text-right">
                          {formatTokens(row.outputTokens)}
                        </TableCell>
                        <TableCell className="text-right font-medium">
                          {formatTokens(row.totalTokens)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>

          {/* Daily Breakdown */}
          <Card>
            <CardHeader>
              <CardTitle>Daily Breakdown</CardTitle>
              <CardDescription>
                Day-by-day token consumption per model
              </CardDescription>
            </CardHeader>
            <CardContent>
              {report.daily.length === 0 ? (
                <p className="text-muted-foreground py-4 text-center text-sm">
                  No usage data for this period.
                </p>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Date</TableHead>
                      <TableHead>Instance</TableHead>
                      <TableHead>Model</TableHead>
                      <TableHead className="text-right">Requests</TableHead>
                      <TableHead className="text-right">Input</TableHead>
                      <TableHead className="text-right">Output</TableHead>
                      <TableHead className="text-right">Total</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {report.daily.map((row: TurDailyUsageRow, i: number) => (
                      <TableRow key={i}>
                        <TableCell>{row.date}</TableCell>
                        <TableCell className="font-medium">
                          {row.instanceTitle}
                        </TableCell>
                        <TableCell className="text-muted-foreground">
                          {row.modelName}
                        </TableCell>
                        <TableCell className="text-right">
                          {row.requestCount.toLocaleString()}
                        </TableCell>
                        <TableCell className="text-right">
                          {formatTokens(row.inputTokens)}
                        </TableCell>
                        <TableCell className="text-right">
                          {formatTokens(row.outputTokens)}
                        </TableCell>
                        <TableCell className="text-right font-medium">
                          {formatTokens(row.totalTokens)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}

function SummaryCard({ title, value }: { title: string; value: string }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardDescription>{title}</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold">{value}</div>
      </CardContent>
    </Card>
  );
}
