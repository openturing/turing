import {
    flexRender,
    getCoreRowModel,
    useReactTable,
    type ColumnDef,
} from "@tanstack/react-table";
import { useEffect, useMemo, useState, type PropsWithChildren } from "react";

import { Card } from "@/components/ui/card";
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
import type { TurIntegrationIndexing } from "@/models/integration/integration-indexing.model";
import { exportToXlsx } from "@/lib/export-xlsx";
import { type Locale, formatDistanceToNow } from 'date-fns';
import { useDateLocale } from "@/hooks/use-date-locale";
import { ArrowUpDown, Download, RefreshCcw } from "lucide-react";
import { useTranslation } from "react-i18next";
import { NavLink } from "react-router-dom";
import { BadgeAemEnv } from "../badge-aem-env";
import { BadgeIndexingStatus } from "../badge-indexing-status";
import { BadgeLocale } from "../badge-locale";
import { BadgeSites } from "../badge-sites";
import { GradientButton } from "../ui/gradient-button";

interface Props {
    gridItemList: TurIntegrationIndexing[];
    refreshInterval?: number;
    page: number;
    pageSize: number;
    totalElements: number;
    totalPages: number;
    sortBy: string;
    sortDirection: "asc" | "desc";
    onPageChange: (page: number) => void;
    onPageSizeChange: (size: number) => void;
    onSortChange: (field: string, direction: "asc" | "desc") => void;
}

function buildSortableColumns(t: (key: string) => string): Record<string, string> {
    return {
        modificationDate: t("forms.indexingLoggingGrid.date"),
        objectId: t("integration.indexingManager.objectId"),
        status: t("forms.indexingLoggingGrid.status"),
        environment: t("forms.indexingLoggingGrid.environment"),
        locale: t("forms.indexingLoggingGrid.locale"),
    };
}

function buildColumns(t: (key: string) => string, dateLocale: Locale): ColumnDef<TurIntegrationIndexing>[] {
  return [
    {
        accessorKey: "modificationDate",
        header: t("forms.indexingLoggingGrid.date"),
        cell: ({ row }) => {
            const dateValue = new Date(row.getValue("modificationDate"));
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
        accessorKey: "objectId",
        header: t("integration.indexingManager.objectId"),
        cell: ({ row }) => {
            const objectId: string = row.getValue("objectId");
            const locale: string = row.getValue("locale");
            const sites: string[] | undefined = row.original.sites;
            const siteId = Array.isArray(sites) && sites.length > 0 ? sites[0] : "";
            const href = siteId
                ? encodeURI(`/sn/${siteId}/?_setlocale=${locale}&q=id:"${objectId}"`)
                : "";
            return href
                ? <NavLink
                    to={href}
                    target="_blank"
                    rel="noopener noreferrer"
                    className={() =>
                        cn(
                            "text-blue-600 decoration-2 transition-colors hover:text-blue-800"
                        )
                    }
                >{truncateMiddle(objectId, 50)}</NavLink>
                : <div className="font-mono text-sm">{truncateMiddle(objectId, 50)}</div>;
        },
    },
    {
        accessorKey: "status",
        header: t("forms.indexingLoggingGrid.status"),
        cell: ({ row }) => (
                <BadgeIndexingStatus status={row.getValue("status")} />
            ),
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
    },
  ];
}

export const AemMonitoringGrid: React.FC<PropsWithChildren<Props>> = ({
    gridItemList,
    refreshInterval,
    page,
    pageSize,
    totalElements,
    totalPages,
    sortBy,
    sortDirection,
    onPageChange,
    onPageSizeChange,
    onSortChange,
}) => {
    const { t } = useTranslation();
    const dateLocale = useDateLocale();
    const columns = useMemo(() => buildColumns(t, dateLocale), [t, dateLocale]);
    const SORTABLE_COLUMNS = useMemo(() => buildSortableColumns(t), [t]);
    const table = useReactTable({
        data: gridItemList,
        columns,
        getCoreRowModel: getCoreRowModel(),
        manualPagination: true,
        pageCount: totalPages,
        state: {
            pagination: {
                pageIndex: page,
                pageSize,
            },
        },
    });

    const [lastUpdated, setLastUpdated] = useState(Date.now());

    useEffect(() => {
        setLastUpdated(Date.now());
    }, [gridItemList]);

    useEffect(() => {
        if (!refreshInterval) return;
        const tick = setInterval(() => {
            setLastUpdated(Date.now());
        }, refreshInterval);

        return () => clearInterval(tick);
    }, [refreshInterval]);

    const handleSort = (field: string) => {
        if (sortBy === field) {
            onSortChange(field, sortDirection === "asc" ? "desc" : "asc");
        } else {
            onSortChange(field, "desc");
        }
    };

    return (
        <div className="px-6">
            <div className="flex justify-between p-2 items-center text-xs text-muted-foreground">
                <span>{t("forms.common.totalRecords", { count: totalElements.toLocaleString() })}</span>
                <div className="flex items-center gap-2">
                    <RefreshCcw className="h-3 w-3 animate-spin-slow" />
                    {t("forms.loggingGrid.lastSync", { time: new Date(lastUpdated).toLocaleTimeString() })}
                    <GradientButton
                        variant="outline"
                        size="sm"
                        className="h-7 ml-2"
                        disabled={gridItemList.length === 0}
                        onClick={() => exportToXlsx(
                            gridItemList as unknown as Record<string, unknown>[],
                            [
                                { key: "modificationDate", label: t("forms.indexingLoggingGrid.date") },
                                { key: "objectId", label: t("integration.indexingManager.objectId") },
                                { key: "status", label: t("forms.indexingLoggingGrid.status") },
                                { key: "environment", label: t("forms.indexingLoggingGrid.environment") },
                                { key: "locale", label: t("forms.indexingLoggingGrid.locale") },
                                { key: "sites", label: t("forms.indexingLoggingGrid.sites") },
                            ],
                            `monitoring-${new Date().toISOString().slice(0, 10)}`
                        )}
                    >
                        <Download className="h-3.5 w-3.5 mr-1" />
                        Excel
                    </GradientButton>
                </div>
            </div>
            <Card>
                <div className="rounded-md">
                    <Table>
                        <TableHeader>
                            {table.getHeaderGroups().map((headerGroup) => (
                                <TableRow key={headerGroup.id}>
                                    {headerGroup.headers.map((header) => {
                                        const columnId = header.column.id;
                                        const isSortable = columnId in SORTABLE_COLUMNS;
                                        const isActive = sortBy === columnId;
                                        return (
                                            <TableHead
                                                key={header.id}
                                                className={cn("px-5", isSortable && "cursor-pointer select-none hover:bg-muted/50")}
                                                onClick={isSortable ? () => handleSort(columnId) : undefined}
                                            >
                                                <div className="flex items-center gap-1">
                                                    {header.isPlaceholder
                                                        ? null
                                                        : flexRender(
                                                            header.column.columnDef.header,
                                                            header.getContext()
                                                        )}
                                                    {isSortable && (
                                                        <ArrowUpDown className={cn(
                                                            "h-3 w-3 ml-1",
                                                            isActive ? "text-foreground" : "text-muted-foreground/50"
                                                        )} />
                                                    )}
                                                    {isActive && (
                                                        <span className="text-[10px] font-mono text-muted-foreground">
                                                            {sortDirection === "asc" ? "\u2191" : "\u2193"}
                                                        </span>
                                                    )}
                                                </div>
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
                                    <TableCell colSpan={columns.length}>
                                        {t("forms.common.noResultsDot")}
                                    </TableCell>
                                </TableRow>
                            )}
                        </TableBody>
                    </Table>
                </div>
                <div className="flex items-center justify-end space-x-2 py-4 px-4 border-t">
                    <div className="flex items-center space-x-2">
                        <p>{t("forms.common.rowsPerPage")}</p>
                        <Select
                            value={`${pageSize}`}
                            onValueChange={(value) => onPageSizeChange(Number(value))}
                        >
                            <SelectTrigger className="h-8 w-17.5">
                                <SelectValue placeholder={pageSize} />
                            </SelectTrigger>
                            <SelectContent side="top">
                                {[10, 20, 30, 40, 50, 100].map((size) => (
                                    <SelectItem key={size} value={`${size}`}>
                                        {size}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </div>
                    <div className="flex w-25 items-center justify-center text-sm font-medium">
                        {t("forms.common.pageOfPages", { page: page + 1, totalPages: totalPages || 1 })}
                    </div>
                    <div className="flex items-center space-x-2">
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
                    </div>
                </div>
            </Card>
        </div >
    );
}
