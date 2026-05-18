import {
    flexRender,
    getCoreRowModel,
    useReactTable,
    type ColumnDef,
} from "@tanstack/react-table";
import { createContext, useContext, useEffect, useState, type PropsWithChildren } from "react";
import { useTranslation } from "react-i18next";

import { Card } from "@/components/ui/card";
import {
    GridColumnVisibilityMenu,
    usePersistedColumnVisibility,
} from "@/components/ui/grid-column-visibility-menu";
import { Input } from "@/components/ui/input";
import {
    ResizableHandle,
    ResizablePanel,
    ResizablePanelGroup,
} from "@/components/ui/resizable";
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
import type { TurLoggingGeneral } from "@/models/logging/logging-general.model";
import { exportToXlsx } from "@/lib/export-xlsx";
import { IconDownload, IconFileAlert, IconRefresh, IconSearch, IconX } from "@tabler/icons-react";
import type { Locale } from "date-fns";
import { formatDistanceToNow } from "date-fns/formatDistanceToNow";
import { useDateLocale } from "@/hooks/use-date-locale";
import { Badge } from "../ui/badge";
import { GradientButton } from "../ui/gradient-button";
import { LogHighlighter } from "./logging.hl";

interface Props {
    gridItemList: TurLoggingGeneral[];
    page: number;
    totalPages: number;
    totalElements: number;
    pageSize: number;
    level: string;
    dateFrom: string;
    dateTo: string;
    search: string;
    onPageChange: (page: number) => void;
    onPageSizeChange: (pageSize: number) => void;
    onLevelChange: (level: string) => void;
    onDateFromChange: (dateFrom: string) => void;
    onDateToChange: (dateTo: string) => void;
    onSearchChange: (search: string) => void;
}

type StackTraceContextType = {
    openStackTrace: (message: string, stackTrace: string) => void;
};
const StackTraceContext = createContext<StackTraceContextType>({ openStackTrace: () => { } });

export const buildLoggingColumns = (t: (key: string, opts?: Record<string, unknown>) => string, dateLocale: Locale): ColumnDef<TurLoggingGeneral>[] => [
    {
        accessorKey: "date",
        header: t("forms.loggingGrid.date"),
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
        accessorKey: "clusterNode",
        header: t("forms.loggingGrid.node"),
        cell: ({ row }) => <div className="font-mono text-sm">{row.getValue("clusterNode")}</div>,
    },
    {
        accessorKey: "level",
        header: t("forms.loggingGrid.level"),
        cell: ({ row }) => {
            const level = String(row.getValue("level")).toUpperCase();
            const levelConfig: Record<string, { label: string; className: string }> = {
                INFO: { label: "INFO", className: "bg-blue-500/10 text-blue-500 hover:bg-blue-500/20 border-blue-500/20" },
                WARN: { label: "WARN", className: "bg-yellow-500/10 text-yellow-600 hover:bg-yellow-500/20 border-yellow-500/20" },
                ERROR: { label: "ERROR", className: "bg-red-500/10 text-red-600 hover:bg-red-500/20 border-red-500/20" },
                DEBUG: { label: "DEBUG", className: "bg-purple-500/10 text-purple-500 hover:bg-purple-500/20 border-purple-500/20" },
                TRACE: { label: "TRACE", className: "bg-slate-500/10 text-slate-500 hover:bg-slate-500/20 border-slate-500/20" },
            };

            const config = levelConfig[level] || { label: level, className: "" };

            return (
                <Badge
                    variant="outline"
                    className={`font-mono font-bold tracking-wider ${config.className}`}
                >
                    {config.label}
                </Badge>
            );
        },
    },
    {
        accessorKey: "logger",
        header: t("forms.loggingGrid.logger"),
        cell: ({ row }) => <div className="font-mono text-sm">{truncateLogger(row.getValue("logger"), 40)}</div>,
    },
    {
        accessorKey: "message",
        header: t("forms.loggingGrid.message"),
        size: 500,
        cell: ({ row }) => <MessageCell message={row.getValue("message")} stackTrace={row.original.stackTrace} />,
    }
];

function MessageCell({ message, stackTrace }: { message: string; stackTrace: string }) {
    const { t } = useTranslation();
    const { openStackTrace } = useContext(StackTraceContext);
    const hasStackTrace = stackTrace && stackTrace.trim().length > 0;

    return (
        <div className="font-mono text-sm w-full wrap-break-word whitespace-pre-wrap">
            <div className="flex items-start gap-1.5">
                {hasStackTrace && (
                    <button
                        type="button"
                        onClick={() => openStackTrace(message, stackTrace)}
                        title={t("forms.loggingGrid.viewStackTrace")}
                        className="mt-0.5 shrink-0 text-red-500 hover:text-red-700 cursor-pointer"
                    >
                        <IconFileAlert className="h-4 w-4" />
                    </button>
                )}
                <span><LogHighlighter text={message} /></span>
            </div>
        </div>
    );
}

function truncateLogger(str: string, limite: number): string {
    if (str.length <= limite) {
        return str;
    }

    return "..." + str.slice(-(limite - 3));
}

const LEVEL_OPTIONS = ["", "INFO", "WARN", "ERROR", "DEBUG", "TRACE"];

export const LoggingGrid: React.FC<PropsWithChildren<Props>> = ({
    gridItemList, page, totalPages, totalElements, pageSize,
    level, dateFrom, dateTo, search,
    onPageChange, onPageSizeChange, onLevelChange, onDateFromChange, onDateToChange, onSearchChange
}) => {
    const { t } = useTranslation();
    const dateLocale = useDateLocale();
    const columns = buildLoggingColumns(t, dateLocale);
    const [selectedTrace, setSelectedTrace] = useState<{ message: string; stackTrace: string } | null>(null);
    const [lastUpdated, setLastUpdated] = useState(Date.now());
    const { columnVisibility, setColumnVisibility, reset: resetColumns } =
        usePersistedColumnVisibility("turing.logging.grid.columnVisibility");

    const columnLabels: Record<string, string> = {
        date: t("forms.loggingGrid.date"),
        clusterNode: t("forms.loggingGrid.node"),
        level: t("forms.loggingGrid.level"),
        logger: t("forms.loggingGrid.logger"),
        message: t("forms.loggingGrid.message"),
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

    const gridContent = (
        <div className="h-full overflow-auto">
            <Card>
                <div className="flex flex-wrap items-center gap-3 p-4 border-b">
                    <div className="flex items-center gap-2">
                        <span className="text-sm text-muted-foreground whitespace-nowrap">{t("forms.loggingGrid.level")}:</span>
                        <Select value={level} onValueChange={onLevelChange}>
                            <SelectTrigger className="h-8 w-28 text-xs font-mono">
                                <SelectValue placeholder="All" />
                            </SelectTrigger>
                            <SelectContent>
                                {LEVEL_OPTIONS.map((l) => (
                                    <SelectItem key={l || "all"} value={l || "all"} className="text-xs">
                                        {l || "All"}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="text-sm text-muted-foreground whitespace-nowrap">{t("forms.loggingGrid.from")}</span>
                        <Input
                            type="date"
                            value={dateFrom}
                            onChange={(e) => onDateFromChange(e.target.value)}
                            className="h-8 w-36 text-xs font-mono"
                        />
                    </div>
                    <div className="flex items-center gap-2">
                        <span className="text-sm text-muted-foreground whitespace-nowrap">{t("forms.loggingGrid.to")}</span>
                        <Input
                            type="date"
                            value={dateTo}
                            onChange={(e) => onDateToChange(e.target.value)}
                            className="h-8 w-36 text-xs font-mono"
                        />
                    </div>
                    <div className="flex items-center gap-2 flex-1 min-w-48">
                        <IconSearch className="h-4 w-4 text-muted-foreground" />
                        <Input
                            type="text"
                            placeholder={t("forms.loggingGrid.searchMessage")}
                            value={search}
                            onChange={(e) => onSearchChange(e.target.value)}
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
                                { key: "date", label: t("forms.loggingGrid.date") },
                                { key: "clusterNode", label: t("forms.loggingGrid.node") },
                                { key: "level", label: t("forms.loggingGrid.level") },
                                { key: "logger", label: t("forms.loggingGrid.logger") },
                                { key: "message", label: t("forms.loggingGrid.message") },
                                { key: "stackTrace", label: t("forms.loggingGrid.stackTrace") },
                            ],
                            `logging-${new Date().toISOString().slice(0, 10)}`
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
        </div>
    );

    return (
        <StackTraceContext.Provider value={{ openStackTrace: (message, stackTrace) => setSelectedTrace({ message, stackTrace }) }}>
            <div className="px-4">
                {selectedTrace ? (
                    <ResizablePanelGroup orientation="horizontal" className="min-h-[calc(100vh-14rem)]">
                        <ResizablePanel defaultSize="60" minSize="30">
                            {gridContent}
                        </ResizablePanel>
                        <ResizableHandle withHandle />
                        <ResizablePanel defaultSize="40" minSize="20">
                            <Card className="h-full flex flex-col ml-2 overflow-hidden">
                                <div className="flex items-center justify-between px-4 py-3 border-b bg-muted/30">
                                    <div className="flex flex-col gap-1 min-w-0">
                                        <h3 className="text-sm font-semibold">{t("forms.loggingGrid.stackTrace")}</h3>
                                        <p className="text-xs text-muted-foreground font-mono truncate">
                                            {selectedTrace.message.length > 100
                                                ? selectedTrace.message.slice(0, 100) + "..."
                                                : selectedTrace.message}
                                        </p>
                                    </div>
                                    <button
                                        type="button"
                                        onClick={() => setSelectedTrace(null)}
                                        className="shrink-0 ml-2 p-1 rounded hover:bg-muted cursor-pointer"
                                        title={t("forms.loggingGrid.close")}
                                    >
                                        <IconX className="h-4 w-4" />
                                    </button>
                                </div>
                                <div className="flex-1 overflow-auto p-4">
                                    <pre className="font-mono text-xs whitespace-pre overflow-x-auto">
                                        <LogHighlighter text={`${selectedTrace.message}\n${selectedTrace.stackTrace}`} />
                                    </pre>
                                </div>
                            </Card>
                        </ResizablePanel>
                    </ResizablePanelGroup>
                ) : (
                    gridContent
                )}
            </div>
        </StackTraceContext.Provider>
    );
}
