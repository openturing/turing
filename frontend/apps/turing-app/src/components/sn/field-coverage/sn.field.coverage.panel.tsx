import { useSnSiteContentFit, useSnSiteFieldCoverage } from "@/api/queries/sn-site.queries";
import { BlankSlate } from "@/components/blank-slate";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { TurSNFieldCoverage } from "@/models/sn/sn-field-coverage.model";
import { IconChartBar, IconDatabase, IconUserSearch } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * The field-coverage + content-fit body (T388 / T472), extracted so both the
 * console page and the Bento sub-surface (T558) render the exact same charts
 * without duplicating the ~230 lines of table/bar/summary markup. Callers own
 * their own header/padding chrome around this.
 */
function formatNumber(value: number | undefined): string {
  if (value === undefined || value === null || value < 0) {
    return "—";
  }
  return value.toLocaleString();
}

/** Red below 50%, amber below 80%, emerald otherwise — surfaces under-filled fields at a glance. */
function coverageColor(percent: number): string {
  if (percent < 50) return "bg-red-500";
  if (percent < 80) return "bg-amber-500";
  return "bg-emerald-500";
}

function CoverageBar({ field }: Readonly<{ field: TurSNFieldCoverage }>) {
  if (!field.supported) {
    return <span className="text-sm text-muted-foreground">—</span>;
  }
  return (
    <div className="flex items-center gap-3">
      <div className="h-2 w-40 overflow-hidden rounded-full bg-muted">
        <div
          className={`h-full rounded-full ${coverageColor(field.coveragePercent)}`}
          style={{ width: `${Math.min(100, Math.max(0, field.coveragePercent))}%` }}
        />
      </div>
      <span className="w-12 text-right text-sm font-medium tabular-nums">
        {field.coveragePercent.toFixed(1)}%
      </span>
    </div>
  );
}

/**
 * T472 — index-time audience content-fit. Shows the share of indexed documents
 * that are "too complex for their audience" (red / amber / green buckets).
 */
function ContentFitCard({ siteId }: Readonly<{ siteId: string }>) {
  const { t } = useTranslation();
  const { data: fit, isLoading } = useSnSiteContentFit(siteId);

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <Skeleton className="h-6 w-48" />
        </CardHeader>
        <CardContent>
          <Skeleton className="h-16 w-full" />
        </CardContent>
      </Card>
    );
  }
  if (!fit) return null;

  const renderBody = () => {
    if (!fit.enabled) {
      return (
        <div className="text-sm text-muted-foreground">
          <p className="font-medium text-foreground">{t("sn.contentFit.disabledTitle")}</p>
          <p>{t("sn.contentFit.disabledHint")}</p>
        </div>
      );
    }
    if (!fit.supported) {
      return <p className="text-sm text-muted-foreground">{t("sn.contentFit.unsupported")}</p>;
    }
    if (fit.scoredDocuments <= 0) {
      return <p className="text-sm text-muted-foreground">{t("sn.contentFit.notScored")}</p>;
    }
    const pct = (n: number) => (fit.scoredDocuments > 0 ? (n * 100) / fit.scoredDocuments : 0);
    const summary = fit.personaName
      ? t("sn.contentFit.tooComplexSummary", {
          percent: fit.tooComplexPercent.toFixed(1),
          persona: fit.personaName,
        })
      : t("sn.contentFit.tooComplexSummaryNoPersona", {
          percent: fit.tooComplexPercent.toFixed(1),
        });
    return (
      <div className="space-y-3">
        <p className="text-sm">
          <span className="font-semibold tabular-nums text-red-600 dark:text-red-400">
            {fit.tooComplexPercent.toFixed(1)}%
          </span>{" "}
          <span className="text-muted-foreground">{summary}</span>
        </p>
        {/* Stacked red/amber/green bar over the scored documents. */}
        <div className="flex h-3 w-full overflow-hidden rounded-full bg-muted">
          <div className="h-full bg-red-500" style={{ width: `${pct(fit.tooComplex)}%` }} />
          <div className="h-full bg-amber-500" style={{ width: `${pct(fit.borderline)}%` }} />
          <div className="h-full bg-emerald-500" style={{ width: `${pct(fit.good)}%` }} />
        </div>
        <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm tabular-nums">
          <span className="flex items-center gap-2">
            <span className="size-2.5 rounded-full bg-red-500" />
            {t("sn.contentFit.tooComplex")}: <strong>{fit.tooComplex.toLocaleString()}</strong>
          </span>
          <span className="flex items-center gap-2">
            <span className="size-2.5 rounded-full bg-amber-500" />
            {t("sn.contentFit.borderline")}: <strong>{fit.borderline.toLocaleString()}</strong>
          </span>
          <span className="flex items-center gap-2">
            <span className="size-2.5 rounded-full bg-emerald-500" />
            {t("sn.contentFit.good")}: <strong>{fit.good.toLocaleString()}</strong>
          </span>
        </div>
        <p className="text-xs text-muted-foreground">
          <strong>{fit.scoredDocuments.toLocaleString()}</strong> {t("sn.contentFit.scored")}{" "}
          {t("sn.contentFit.ofTotal", { total: fit.totalDocuments.toLocaleString() })}
          {fit.personaName && (
            <>
              {" · "}
              {t("sn.contentFit.persona")}: <strong>{fit.personaName}</strong>
            </>
          )}
        </p>
      </div>
    );
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <IconUserSearch className="h-5 w-5 text-muted-foreground" />
          {t("sn.contentFit.title")}
        </CardTitle>
        <p className="text-sm text-muted-foreground">{t("sn.contentFit.description")}</p>
      </CardHeader>
      <CardContent>{renderBody()}</CardContent>
    </Card>
  );
}

/** Coverage + content-fit charts for one SN site. Header/padding is the caller's. */
export function SNFieldCoveragePanel({ siteId }: Readonly<{ siteId: string }>) {
  const { t } = useTranslation();
  const { data: report, isLoading, isError } = useSnSiteFieldCoverage(siteId);

  const renderContent = () => {
    if (isLoading) {
      return (
        <Card>
          <CardHeader>
            <Skeleton className="h-6 w-48" />
          </CardHeader>
          <CardContent className="space-y-2">
            {Array.from({ length: 6 }).map(() => (
              <Skeleton key={crypto.randomUUID()} className="h-8 w-full" />
            ))}
          </CardContent>
        </Card>
      );
    }

    if (isError) {
      return (
        <Card>
          <CardContent className="py-6 text-sm text-destructive">
            {t("common.connectionError", { resource: t("sn.fieldCoverage.title") })}
          </CardContent>
        </Card>
      );
    }

    if (!report || report.fields.length === 0) {
      return (
        <BlankSlate
          icon={IconChartBar}
          title={t("sn.fieldCoverage.empty")}
          description={t("sn.fieldCoverage.emptyDescription")}
          buttonText=""
        />
      );
    }

    return (
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_280px]">
        <Card>
          <CardContent>
            {!report.supported && (
              <p className="mb-4 text-sm text-muted-foreground">
                {t("sn.fieldCoverage.unsupported")}
              </p>
            )}
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("sn.fieldCoverage.field")}</TableHead>
                  <TableHead className="text-center">{t("sn.fieldCoverage.type")}</TableHead>
                  <TableHead>{t("sn.fieldCoverage.coverage")}</TableHead>
                  <TableHead className="text-center">{t("sn.fieldCoverage.documents")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {report.fields.map((field) => (
                  <TableRow key={field.fieldName}>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <span className="font-medium">{field.fieldName}</span>
                        {field.facet && (
                          <Badge variant="secondary">{t("sn.fieldCoverage.facet")}</Badge>
                        )}
                      </div>
                    </TableCell>
                    <TableCell className="text-center">
                      {field.fieldType ? (
                        <Badge variant="outline">{field.fieldType}</Badge>
                      ) : (
                        "—"
                      )}
                    </TableCell>
                    <TableCell>
                      <CoverageBar field={field} />
                    </TableCell>
                    <TableCell className="text-center tabular-nums">
                      {formatNumber(field.presentDocuments)} / {formatNumber(field.totalDocuments)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>{t("sn.fieldCoverage.summary")}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2 text-sm text-muted-foreground">
            <div className="flex items-center gap-2 text-foreground">
              <IconDatabase className="h-4 w-4 text-muted-foreground" />
              <span>
                <strong>{formatNumber(report.totalDocuments)}</strong>{" "}
                {t("sn.fieldCoverage.documentsIndexed")}
              </span>
            </div>
            <div className="flex items-center gap-2 text-foreground">
              <IconChartBar className="h-4 w-4 text-muted-foreground" />
              <span>
                <strong>{report.fields.length}</strong> {t("sn.fieldCoverage.fieldsMeasured")}
              </span>
            </div>
          </CardContent>
        </Card>
      </div>
    );
  };

  return (
    <div className="space-y-6">
      <ContentFitCard siteId={siteId} />
      {renderContent()}
    </div>
  );
}
