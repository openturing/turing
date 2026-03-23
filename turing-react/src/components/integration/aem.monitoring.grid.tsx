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
import { type Locale, formatDistanceToNow } from 'date-fns';
import { useDateLocale } from "@/hooks/use-date-locale";
import { ArrowRightCircle, ArrowUpDown, Ban, CheckCircle2, Clock, Database, FileSearch, Globe, PencilLine, RefreshCcw, Send, Zap } from "lucide-react";
import { useTranslation } from "react-i18next";
import { NavLink } from "react-router-dom";
import { BadgeColorful } from "../badge-colorful";
import { BadgeLocale } from "../badge-locale";
import { Badge } from "../ui/badge";
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
        cell: ({ row }) => {
            const status = String(row.getValue("status"));

            const statusConfig: Record<string, { label: string; icon: React.ComponentType<{ className?: string }>; className: string }> = {
                PREPARE_INDEX: {
                    label: "Indexed",
                    icon: FileSearch,
                    className: "bg-blue-500/10 text-blue-600 border-blue-500/20"
                },
                PREPARE_UNCHANGED: {
                    label: "Unchanged",
                    icon: Clock,
                    className: "bg-slate-500/10 text-slate-600 border-slate-500/20"
                },
                PREPARE_REINDEX: {
                    label: "Reindexed",
                    icon: RefreshCcw,
                    className: "bg-cyan-500/10 text-cyan-600 border-cyan-500/20"
                },
                PREPARE_FORCED_REINDEX: {
                    label: "Forced Reindexing",
                    icon: Zap,
                    className: "bg-indigo-500/10 text-indigo-600 border-indigo-500/20"
                },

                RECEIVED_AND_SENT_TO_TURING: {
                    label: "Sent to Turing",
                    icon: Send,
                    className: "bg-purple-500/10 text-purple-600 border-purple-500/20"
                },
                SENT_TO_QUEUE: {
                    label: "In Queue",
                    icon: ArrowRightCircle,
                    className: "bg-orange-500/10 text-orange-600 border-orange-500/20"
                },
                RECEIVED_FROM_QUEUE: {
                    label: "From Queue",
                    icon: Database,
                    className: "bg-amber-500/10 text-amber-600 border-amber-500/20"
                },
                INDEXED: {
                    label: "Indexed",
                    icon: CheckCircle2,
                    className: "bg-emerald-500/10 text-emerald-600 border-emerald-500/20"
                },
                FINISHED: {
                    label: "Finished",
                    icon: CheckCircle2,
                    className: "bg-green-500/10 text-green-600 border-green-500/20"
                },
                DEINDEXED: {
                    label: "Deindexed",
                    icon: Ban,
                    className: "bg-rose-500/10 text-rose-600 border-rose-500/20"
                },
                NOT_PROCESSED: {
                    label: "Not Processed",
                    icon: Clock,
                    className: "bg-gray-500/10 text-gray-600 border-gray-500/20"
                },
                IGNORED: {
                    label: "Ignored",
                    icon: Ban,
                    className: "bg-zinc-500/10 text-zinc-600 border-zinc-500/20"
                },
            };

            const config = statusConfig[status] || {
                label: status,
                icon: Clock,
                className: "bg-gray-100 text-gray-600"
            };

            const Icon = config.icon;

            return (
                <Badge
                    variant="outline"
                    className={`flex w-fit items-center gap-1.5 px-2 py-0.5 font-mono text-[10px] font-bold tracking-tight uppercase ${config.className}`}
                >
                    <Icon className="h-3 w-3" />
                    {config.label}
                </Badge>
            );
        },
    },
    {
        accessorKey: "environment",
        header: t("forms.indexingLoggingGrid.environment"),
        cell: ({ row }) => {
            const env = String(row.getValue("environment")).toUpperCase();

            const isAuthor = env === "AUTHOR";

            return (
                <Badge
                    variant="outline"
                    className={`
            font-mono font-bold gap-1.5 px-3 py-1 transition-all
            ${isAuthor
                            ? "bg-amber-500/10 text-amber-600 border-amber-500/20 hover:bg-amber-500/20 dark:text-amber-400"
                            : "bg-emerald-500/10 text-emerald-600 border-emerald-500/20 hover:bg-emerald-500/20 dark:text-emerald-400"
                        }
          `}
                >
                    {isAuthor ? (
                        <>
                            <PencilLine className="w-3.5 h-3.5" />
                            <span>AUTHOR</span>
                        </>
                    ) : (
                        <>
                            <Globe className="w-3.5 h-3.5" />
                            <span>PUBLISHING</span>
                        </>
                    )}
                </Badge>
            );
        },
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
        cell: ({ row }) => {
            const sites = row.getValue("sites");
            const sitesArray = Array.isArray(sites) ? sites : [];
            return (
                <div className="flex flex-wrap gap-1.5 min-w-37.5">
                    {sitesArray.map((site: string, index: number) => (
                        <BadgeColorful
                            key={`${site}-${index}`}
                            text={site}
                        />
                    ))}
                </div>
            );
        },
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
