import { ROUTES } from "@/app/routes.const";
import { BlankSlate } from "@/components/blank-slate";
import { SubPageHeader } from "@/components/sub.page.header";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { GradientAvatar, GradientAvatarFallback } from "@/components/ui/gradient-avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { TurSNSiteMetricsTerm } from "@/models/sn/sn-site-metrics-term.model";
import { TurSNSiteMetricsService, type TurSNTopTermsPeriod } from "@/services/sn/sn.site.metrics.service";
import { IconArrowDown, IconArrowUp, IconChartBar, IconSearch } from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

const turSNSiteMetricsService = new TurSNSiteMetricsService();
const rows = 50;
const periodOptions: { value: TurSNTopTermsPeriod; label: string }[] = [
  { value: "today", label: "Today" },
  { value: "this-week", label: "This Week" },
  { value: "this-month", label: "This Month" },
  { value: "all-time", label: "All Time" },
];

function isValidPeriod(value: string | undefined): value is TurSNTopTermsPeriod {
  return periodOptions.some((option) => option.value === value);
}

function getTermInitial(term: string): string {
  const trimmed = term.trim();
  if (!trimmed) {
    return "?";
  }
  return trimmed[0].toUpperCase();
}

function formatNumber(value: number | undefined): string {
  if (value === undefined || value === null) {
    return "0";
  }
  return value.toLocaleString();
}

export default function SNSiteTopSearchTermsPage() {
  const { id, period: periodParam } = useParams() as { id: string; period?: string };
  const navigate = useNavigate();
  const [topTerms, setTopTerms] = useState<TurSNSiteMetricsTerm | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const resolvedPeriod = useMemo<TurSNTopTermsPeriod>(() => {
    if (isValidPeriod(periodParam)) {
      return periodParam;
    }
    return "this-month";
  }, [periodParam]);

  useEffect(() => {
    if (!periodParam) {
      return;
    }
    if (!isValidPeriod(periodParam)) {
      navigate(`${ROUTES.SN_INSTANCE}/${id}/top-terms/this-month`, { replace: true });
    }
  }, [periodParam, id, navigate]);

  useEffect(() => {
    if (!id) {
      return;
    }
    setIsLoading(true);
    setError(null);
    turSNSiteMetricsService
      .topTermsByPeriod(id, resolvedPeriod, rows)
      .then(setTopTerms)
      .catch((fetchError) => {
        console.error("Failed to load top search terms", fetchError);
        setError("Unable to load top search terms right now.");
      })
      .finally(() => setIsLoading(false));
  }, [id, resolvedPeriod]);

  const hasResults = (topTerms?.topTerms?.length ?? 0) > 0;
  const variation = topTerms?.variationPeriod ?? 0;

  const renderContent = () => {
    if (isLoading) {
      return (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_280px] px-6">
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
          <Card>
            <CardHeader>
              <Skeleton className="h-6 w-32" />
            </CardHeader>
            <CardContent className="space-y-3">
              <Skeleton className="h-4 w-40" />
              <Skeleton className="h-4 w-28" />
            </CardContent>
          </Card>
        </div>
      );
    }

    if (error) {
      return (
        <Card>
          <CardContent className="py-6 text-sm text-destructive">{error}</CardContent>
        </Card>
      );
    }

    if (!hasResults) {
      return (
        <BlankSlate
          icon={IconChartBar}
          title="No search terms yet."
          description="As soon as users search, the top terms will show up here."
          buttonText=""
        />
      );
    }

    return (
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_280px]">
        <Card>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-20">Rank</TableHead>
                  <TableHead>Term</TableHead>
                  <TableHead className="text-center">Average of results</TableHead>
                  <TableHead className="text-center">Search total</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {topTerms?.topTerms.map((item, index) => (
                  <TableRow key={`${item.term}-${index}`}>
                    <TableCell>
                      <span className="font-medium">{index + 1}</span>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <GradientAvatar className="h-6 w-6">
                          <GradientAvatarFallback variant="default">
                            {getTermInitial(item.term)}
                          </GradientAvatarFallback>
                        </GradientAvatar>
                        <span className="font-medium">{item.term}</span>
                      </div>
                    </TableCell>
                    <TableCell className="text-center">
                      {formatNumber(item.numFound)}
                    </TableCell>
                    <TableCell className="text-center">
                      {formatNumber(item.total)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
        <Card >
          <CardHeader>
            <CardTitle>Statistics</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2 text-sm text-muted-foreground ">
            <div className="flex items-center gap-2 text-foreground">
              <IconSearch className="h-4 w-4 text-muted-foreground" />
              <span>
                <strong>{formatNumber(topTerms?.totalTermsPeriod)}</strong> search terms
              </span>
            </div>
            {variation !== 0 && (
              <div
                className={`flex items-center gap-2 ${variation > 0 ? "text-emerald-600" : "text-rose-600"
                  }`}
              >
                {variation > 0 ? (
                  <IconArrowUp className="h-4 w-4" />
                ) : (
                  <IconArrowDown className="h-4 w-4" />
                )}
                <span>
                  {variation > 0 ? "+" : ""}
                  {variation}%
                </span>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    );
  };

  return (
    <>
      <SubPageHeader
        icon={IconChartBar}
        name="Top Search Terms"
        feature="Top Search Terms"
        description="Top search terms report."
      />
      <div className="px-6">
        <Tabs
          value={resolvedPeriod}
          onValueChange={(value) =>
            navigate(`${ROUTES.SN_INSTANCE}/${id}/top-terms/${value}`)
          }
          className="mb-4"
        >
          <TabsList>
            {periodOptions.map((option) => (
              <TabsTrigger key={option.value} value={option.value}>
                {option.label}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
        {renderContent()}
      </div>
    </>
  );
}
