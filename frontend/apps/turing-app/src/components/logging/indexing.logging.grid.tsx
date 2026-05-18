import {
    flexRender,
    getCoreRowModel,
    useReactTable,
    type ColumnDef,
} from "@tanstack/react-table";
import { useEffect, useMemo, useState, type PropsWithChildren } from "react";
import { useTranslation } from "react-i18next";

import { Card } from "@/components/ui/card";
import {
    GridColumnVisibilityMenu,
    usePersistedColumnVisibility,
} from "@/components/ui/grid-column-visibility-menu";
import { Input } from "@/components/ui/input";
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
import { cn, truncateMiddle } from "@/lib/utils";
import type { TurLoggingIndexing } from "@/models/logging/logging-indexing.model";
import { exportToXlsx } from "@/lib/export-xlsx";
import { IconDownload, IconRefresh, IconSearch } from "@tabler/icons-react";
import { type Locale, formatDistanceToNow } from "date-fns";
import { useDateLocale } from "@/hooks/use-date-locale";
import { NavLink } from "react-router-dom";
import { BadgeAemEnv } from "../badge-aem-env";
import { BadgeIndexingStatus } from "../badge-indexing-status";
import { BadgeSites } from "../badge-sites";
import { BadgeLocale } from "../badge-locale";
import { Badge } from "../ui/badge";
import { GradientButton } from "../ui/gradient-button";

interface Props {
    gridItemList: TurLoggingIndexing[];
    page: number;
    totalPages: number;
    totalElements: number;
    pageSize: number;
    dateFrom: string;
    dateTo: string;
    status: string;
    contentId: string;
    resultStatus: string;
    url: string;
    onPageChange: (page: number) => void;
    onPageSizeChange: (pageSize: number) => void;
    onDateFromChange: (dateFrom: string) => void;
    onDateToChange: (dateTo: string) => void;
    onStatusChange: (status: string) => void;
    onContentIdChange: (contentId: string) => void;
    onResultStatusChange: (resultStatus: string) => void;
    onUrlChange: (url: string) => void;
}

export const buildIndexingColumns = (t: (key: string) => string, dateLocale: Locale): ColumnDef<TurLoggingIndexing>[] => [
    {
        accessorKey: "date",
        header: t("forms.indexingLoggingGrid.date"),
        cell: ({ row }) => {
            const dateValue = new Date(row.getValue("date"));
            const formattedDate = dateValue.toLocaleString(undefined, {
                day: '2-digit',
                month: '2-digit',
                year: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            });

            const timeAgo = formatDistanceToNow(dateValue, {
                addSuffix: true,
                locale: dateLocale,
            });

            return (
                <div className="flex flex-col">
                    <span className="font-mono text-sm">{formattedDate}</span>
                    <span className="text-xs text-muted-foreground italic">
                        ({timeAgo})
                    </span>
                </div>
            );
        },
    },
    {
        accessorKey: "source",
        header: t("forms.indexingLoggingGrid.source"),
        cell: ({ row }) => <div className="font-mono text-sm">{row.getValue("source")}</div>,
    },
    {
        accessorKey: "status",
        header: t("forms.indexingLoggingGrid.status"),
        cell: ({ row }) => (
                <BadgeIndexingStatus status={row.getValue("status")} />
            ),
    },
    {
        accessorKey: "resultStatus",
        header: t("forms.indexingLoggingGrid.resultStatus"),
        cell: ({ row }) => {
            const status = String(row.getValue("resultStatus")).toUpperCase();
            const statusConfig: Record<string, { label: string; className: string }> = {
                SUCCESS: { label: "SUCCESS", className: "bg-green-500/10 text-green-700 hover:bg-green-500/20 border-green-500/20" },
                ERROR: { label: "ERROR", className: "bg-red-500/10 text-red-600 hover:bg-red-500/20 border-red-500/20" },
            };

            const config = statusConfig[status] || { label: status, className: "" };

            return (
                <Badge
                    variant="outline"
                    className={`font-mono font-bold tracking-wider ${config.className}`}
                >
                    {config.label}
                </Badge>
            );
        }
    },
    {
        accessorKey: "url",
        header: t("forms.indexingLoggingGrid.url"),
        cell: ({ row }) => <div className="font-mono text-sm"><NavLink
            to={row.getValue("url")}
            target="_blank"
            rel="noopener noreferrer"
            className={() =>
                cn(
                    "text-blue-600 decoration-2 transition-colors hover:text-blue-800"
                )
            }
        >{truncateMiddle(row.getValue("url"), 50)}</NavLink></div>,

    },
    {
        accessorKey: "environment",
        header: t("forms.indexingLoggingGrid.environment"),
        cell: ({ row }) => (
                <BadgeAemEnv environment={row.getValue("environment")} />
            ),
    },
    {
        accessorKey: "locale",
        header: t("forms.indexingLoggingGrid.locale"),
        cell: ({ row }) => {
            return (<BadgeLocale locale={row.getValue("locale")} />);
        },
    },
    {
        accessorKey: "sites",
        header: t("forms.indexingLoggingGrid.sites"),
        cell: ({ row }) => (
                <BadgeSites sites={row.getValue("sites")} />
            ),
    }
];

const STATUS_OPTIONS = [
    "", "PREPARE_INDEX", "PREPARE_UNCHANGED", "PREPARE_REINDEX", "PREPARE_FORCED_REINDEX",
    "RECEIVED_AND_SENT_TO_TURING", "SENT_TO_QUEUE", "RECEIVED_FROM_QUEUE",
    "INDEXED", "FINISHED", "DEINDEXED", "NOT_PROCESSED", "IGNORED"
];

const RESULT_STATUS_OPTIONS = ["", "SUCCESS", "ERROR"];

export const IndexingLoggingGrid: React.FC<PropsWithChildren<Props>> = ({
    gridItemList, page, totalPages, totalElements, pageSize,
    dateFrom, dateTo, status, contentId, resultStatus, url,
    onPageChange, onPageSizeChange, onDateFromChange, onDateToChange,
    onStatusChange, onContentIdChange, onResultStatusChange, onUrlChange
}) => {
    const { t } = useTranslation();
    const dateLocale = useDateLocale();
    const columns = useMemo(() => buildIndexingColumns(t, dateLocale), [t, dateLocale]);
    const [lastUpdated, setLastUpdated] = useState(Date.now());
    const { columnVisibility, setColumnVisibility, reset: resetColumns } =
        usePersistedColumnVisibility("turing.indexing.grid.columnVisibility");

    const columnLabels: Record<string, string> = {
        date: t("forms.indexingLoggingGrid.date"),
        source: t("forms.indexingLoggingGrid.source"),
        status: t("forms.indexingLoggingGrid.status"),
        resultStatus: t("forms.indexingLoggingGrid.resultStatus"),
        url: t("forms.indexingLoggingGrid.url"),
        environment: t("forms.indexingLoggingGrid.environment"),
        locale: t("forms.indexingLoggingGrid.locale"),
        sites: t("forms.indexingLoggingGrid.sites"),
    };

    useEffect(() => {
        setLastUpdated(Date.now());
    }, [gridItemList]);

    const table = useReactTable({
        data: gridItemList,
        columns,
        getCoreRowModel: getCoreRowModel(),
        manualPagination: true,
        pageCount: totalPages,
        state: {
            pagination: { pageIndex: page, pageSize },
            columnVisibility,
        },
        onColumnVisibilityChange: setColumnVisibility,
    });

    return (
        <div className="px-4">
            <Card>
                <div className="flex flex-wrap items-center gap-3 p-4 border-b">
                    <div className="flex items-center gap-2">
                        <span className="text-sm text-muted-foreground whitespace-nowrap">{t("forms.indexingLoggingGrid.statusFilter")}</span>
                        <Select value={status} onValueChange={onStatusChange}>
                            <SelectTrigger className="h-8 w-44 text-xs font-mono">
                                <SelectValue placeholder="All" />
                            </SelectTrigger>
                            <SelectContent>
                                {STATUS_OPTIONS.map((s) => (
                                    <SelectItem key={s || "all"} value={s || "all"} className="text-xs">
                                        {s || "All"}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="text-sm text-muted-foreground whitespace-nowrap">{t("forms.indexingLoggingGrid.resultFilter")}</span>
                        <Select value={resultStatus} onValueChange={onResultStatusChange}>
                            <SelectTrigger className="h-8 w-28 text-xs font-mono">
                                <SelectValue placeholder="All" />
                            </SelectTrigger>
                            <SelectContent>
                                {RESULT_STATUS_OPTIONS.map((s) => (
                                    <SelectItem key={s || "all"} value={s || "all"} className="text-xs">
                                        {s || "All"}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="text-sm text-muted-foreground whitespace-nowrap">{t("forms.indexingLoggingGrid.from")}</span>
                        <Input
                            type="date"
                            value={dateFrom}
                            onChange={(e) => onDateFromChange(e.target.value)}
                            className="h-8 w-36 text-xs font-mono"
                        />
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="text-sm text-muted-foreground whitespace-nowrap">{t("forms.indexingLoggingGrid.to")}</span>
                        <Input
                            type="date"
                            value={dateTo}
                            onChange={(e) => onDateToChange(e.target.value)}
                            className="h-8 w-36 text-xs font-mono"
                        />
                    </div>
                </div>
                <div className="flex flex-wrap items-center gap-3 p-4 border-b">
                    <div className="flex items-center gap-2 flex-1 min-w-48">
                        <IconSearch className="h-4 w-4 text-muted-foreground" />
                        <Input
                            type="text"
                            placeholder={t("forms.indexingLoggingGrid.searchContentId")}
                            value={contentId}
                            onChange={(e) => onContentIdChange(e.target.value)}
                            className="h-8 text-xs"
                        />
                    </div>
                    <div className="flex items-center gap-2 flex-1 min-w-48">
                        <IconSearch className="h-4 w-4 text-muted-foreground" />
                        <Input
                            type="text"
                            placeholder={t("forms.indexingLoggingGrid.searchUrl")}
                            value={url}
                            onChange={(e) => onUrlChange(e.target.value)}
                            className="h-8 text-xs"
                        />
                    </div>
                    <GridColumnVisibilityMenu
                        table={table}
                        columnLabels={columnLabels}
                        onReset={resetColumns}
                    />
                    <GradientButton
                        variant="outline"
                        size="sm"
                        className="h-8"
                        disabled={gridItemList.length === 0}
                        onClick={() => exportToXlsx(
                            gridItemList as unknown as Record<string, unknown>[],
                            [
                                { key: "date", label: t("forms.indexingLoggingGrid.date") },
                                { key: "source", label: t("forms.indexingLoggingGrid.source") },
                                { key: "status", label: t("forms.indexingLoggingGrid.status") },
                                { key: "resultStatus", label: t("forms.indexingLoggingGrid.resultStatus") },
                                { key: "url", label: t("forms.indexingLoggingGrid.url") },
                                { key: "environment", label: t("forms.indexingLoggingGrid.environment") },
                                { key: "locale", label: t("forms.indexingLoggingGrid.locale") },
                                { key: "sites", label: t("forms.indexingLoggingGrid.sites") },
                                { key: "contentId", label: "Content ID" },
                                { key: "transactionId", label: "Transaction ID" },
                            ],
                            `logging-indexing-${new Date().toISOString().slice(0, 10)}`
                        )}
                    >
                        <IconDownload className="h-4 w-4 mr-1" />
                        Excel
                    </GradientButton>
                </div>
                <div className="rounded-md">
                    <Table>
                        <TableHeader >
                            {table.getHeaderGroups().map((headerGroup) => (
                                <TableRow key={headerGroup.id}>
                                    {headerGroup.headers.map((header) => {
                                        return (
                                            <TableHead key={header.id} className="px-5">
                                                {header.isPlaceholder
                                                    ? null
                                                    : flexRender(
                                                        header.column.columnDef.header,
                                                        header.getContext()
                                                    )}
                                            </TableHead>
                                        );
                                    })}
                                </TableRow>
                            ))}
                        </TableHeader>
                        <TableBody>
                            {table.getRowModel().rows?.length ? (
                                table.getRowModel().rows.map((row) => (
                                    <TableRow
                                        key={row.id}
                                        data-state={row.getIsSelected() && "selected"}
                                    >
                                        {row.getVisibleCells().map((cell) => (
                                            <TableCell key={cell.id} className="px-5 py-3 text-sm">
                                                {flexRender(cell.column.columnDef.cell, cell.getContext())}
                                            </TableCell>
                                        ))}
                                    </TableRow>
                                ))
                            ) : (
                                <TableRow>
                                    <TableCell colSpan={table.getVisibleLeafColumns().length}>
                                        {t("forms.common.noResultsDot")}
                                    </TableCell>
                                </TableRow>
                            )}
                        </TableBody>
                    </Table>
                </div>
                <div className="flex items-center justify-between py-4 px-4 border-t">
                    <div className="flex items-center gap-4 text-sm text-muted-foreground">
                        <span>{t("forms.common.totalRecords", { count: totalElements })}</span>
                        <span className="flex items-center gap-1 text-xs italic">
                            <IconRefresh className="h-3 w-3" />
                            {t("forms.loggingGrid.lastSync", { time: new Date(lastUpdated).toLocaleTimeString() })}
                        </span>
                    </div>
                    <div className="flex items-center space-x-4">
                        <div className="flex items-center space-x-2">
                            <p className="text-sm">{t("forms.common.rowsPerPage")}</p>
                            <Select
                                value={`${pageSize}`}
                                onValueChange={(value) => onPageSizeChange(Number(value))}
                            >
                                <SelectTrigger className="h-8 w-20">
                                    <SelectValue placeholder={pageSize} />
                                </SelectTrigger>
                                <SelectContent side="top">
                                    {[10, 20, 30, 40, 50, 100].map((ps) => (
                                        <SelectItem key={ps} value={`${ps}`}>
                                            {ps}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>
                        <div className="flex w-40 items-center justify-center text-sm font-medium">
                            {t("forms.common.pageOfPages", { page: page + 1, totalPages: totalPages || 1 })}
                        </div>
                        <div className="flex items-center space-x-2">
                            <GradientButton
                                variant="outline"
                                size="sm"
                                onClick={() => onPageChange(0)}
                                disabled={page <= 0}
                                className="h-8 w-8 p-0"
                            >
                                <span>&laquo;</span>
                            </GradientButton>
                            <GradientButton
                                variant="outline"
                                size="sm"
                                onClick={() => onPageChange(page - 1)}
                                disabled={page <= 0}
                                className="h-8 w-8 p-0"
                            >
                                <span>&lt;</span>
                            </GradientButton>
                            <GradientButton
                                variant="outline"
                                size="sm"
                                onClick={() => onPageChange(page + 1)}
                                disabled={page >= totalPages - 1}
                                className="h-8 w-8 p-0"
                            >
                                <span>&gt;</span>
                            </GradientButton>
                            <GradientButton
                                variant="outline"
                                size="sm"
                                onClick={() => onPageChange(totalPages - 1)}
                                disabled={page >= totalPages - 1}
                                className="h-8 w-8 p-0"
                            >
                                <span>&raquo;</span>
                            </GradientButton>
                        </div>
                    </div>
                </div>
            </Card>
        </div >
    );
}
