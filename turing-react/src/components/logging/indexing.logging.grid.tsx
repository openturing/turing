import {
    flexRender,
    getCoreRowModel,
    getPaginationRowModel,
    useReactTable,
    type ColumnDef,
} from "@tanstack/react-table";
import { useState, type PropsWithChildren } from "react";

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
import type { TurLoggingIndexing } from "@/models/logging/logging-indexing.model";
import { formatDistanceToNow } from "date-fns";
import { ArrowRightCircle, Ban, CheckCircle2, Clock, Database, FileSearch, Globe, PencilLine, RefreshCcw, Send, Zap } from "lucide-react";
import { NavLink } from "react-router-dom";
import { BadgeColorful } from "../badge-colorful";
import { BadgeLocale } from "../badge-locale";
import { Badge } from "../ui/badge";
import { GradientButton } from "../ui/gradient-button";

interface Props {
    gridItemList: TurLoggingIndexing[];
}

export const columns: ColumnDef<TurLoggingIndexing>[] = [
    {
        accessorKey: "date",
        header: "Date",
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
                addSuffix: true
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
        header: "Source",
        cell: ({ row }) => <div className="font-mono text-sm">{row.getValue("source")}</div>,
    },
    {
        accessorKey: "status",
        header: "Status",
        cell: ({ row }) => {
            const status = String(row.getValue("status"));

            const statusConfig: Record<string, { label: string; icon: any; className: string }> = {
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
        accessorKey: "resultStatus",
        header: "Result Status",
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
        header: "URL",
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
        header: "Environment",
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
        header: "Locale",
        cell: ({ row }) => {
            return (<BadgeLocale locale={row.getValue("locale")} />);
        },
    },
    {
        accessorKey: "sites",
        header: "Sites",
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
    }
];


export const IndexingLoggingGrid: React.FC<PropsWithChildren<Props>> = ({ gridItemList }) => {
    const [pagination, setPagination] = useState({
        pageIndex: 0, // initial page index
        pageSize: 100, // default page size
    });

    const table = useReactTable({
        data: gridItemList,
        columns,
        getCoreRowModel: getCoreRowModel(),
        getPaginationRowModel: getPaginationRowModel(),
        onPaginationChange: setPagination,
        state: {
            pagination,
        },
    });

    return (
        <div className="px-4">
            <Card>
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
                                    <TableCell colSpan={columns.length}>
                                        No results.
                                    </TableCell>
                                </TableRow>
                            )}
                        </TableBody>
                    </Table>
                </div>
                <div className="flex items-center justify-end space-x-2 py-4 px-4 border-t">
                    <div className="flex items-center space-x-2">
                        <p >Rows per page</p>
                        <Select
                            value={`${table.getState().pagination.pageSize}`}
                            onValueChange={(value) => {
                                table.setPageSize(Number(value))
                            }}
                        >
                            <SelectTrigger className="h-8 w-17.5">
                                <SelectValue placeholder={table.getState().pagination.pageSize} />
                            </SelectTrigger>
                            <SelectContent side="top">
                                {[10, 20, 30, 40, 50, 100].map((pageSize) => (
                                    <SelectItem key={pageSize} value={`${pageSize}`}>
                                        {pageSize}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </div>
                    <div className="flex w-25 items-center justify-center text-sm font-medium">
                        Page {table.getState().pagination.pageIndex + 1} of{" "}
                        {table.getPageCount()}
                    </div>
                    <div className="flex items-center space-x-2">
                        <GradientButton
                            variant="outline"
                            size="sm"
                            onClick={() => table.previousPage()}
                            disabled={!table.getCanPreviousPage()}
                            className="h-8 w-8 p-0"
                        >
                            <span>&lt;</span>
                        </GradientButton>
                        <GradientButton
                            variant="outline"
                            size="sm"
                            onClick={() => table.nextPage()}
                            disabled={!table.getCanNextPage()}
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