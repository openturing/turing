import {
    getFilteredRowModel,
    getPaginationRowModel,
    getSortedRowModel,
    getCoreRowModel,
    useReactTable,
    type ColumnDef,
    type SortingState,
} from "@tanstack/react-table";
import { type ReactNode, useState } from "react";
import { Icon } from "@iconify/react";

import { Card } from "@/components/ui/card";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import type { TurGridItem } from "@/models/ui/grid-item";
import React from "react";
import { useTranslation } from "react-i18next";
import { GradientButton } from "./ui/gradient-button";
import { Input } from "./ui/input";
import { IconChevronRight, IconDotsVertical, IconPlus, IconSearch } from "@tabler/icons-react";
import { NavLink, useNavigate } from "react-router-dom";

interface Props {
    gridItemList: TurGridItem[];
    children?: ReactNode;
}

interface NewButtonProps {
    to: string;
    label: string;
}

// Marker component — rendered inside DropdownMenuContent by GridListComponent
const GridListNewButton: React.FC<NewButtonProps> = () => null;
GridListNewButton.displayName = "GridListNewButton";

interface ActionProps {
    children: ReactNode;
}

// Marker component — children rendered inside DropdownMenuContent by GridListComponent
const GridListAction: React.FC<ActionProps> = () => null;
GridListAction.displayName = "GridListAction";

const GRADIENT_PAIRS = [
    "from-blue-500 to-indigo-500",
    "from-emerald-500 to-teal-500",
    "from-violet-500 to-purple-500",
    "from-amber-500 to-orange-500",
    "from-rose-500 to-pink-500",
    "from-cyan-500 to-sky-500",
    "from-fuchsia-500 to-pink-500",
    "from-lime-500 to-emerald-500",
];

function getGradient(name: string): string {
    let hash = 0;
    for (let i = 0; i < name.length; i++) {
        hash = (name.codePointAt(i) ?? 0) + ((hash << 5) - hash);
    }
    return GRADIENT_PAIRS[Math.abs(hash) % GRADIENT_PAIRS.length];
}

function getInitials(name: string): string {
    return name
        .split(/[\s\-_]+/)
        .filter(Boolean)
        .slice(0, 2)
        .map((w) => w[0].toUpperCase())
        .join("");
}

const columns: ColumnDef<TurGridItem>[] = [
    { accessorKey: "name", header: "Name" },
    { accessorKey: "description", header: "Description" },
    { accessorKey: "url", header: "URL" },
];

const GridListComponent: React.FC<Props> = ({ gridItemList, children }) => {
    const { t } = useTranslation();
    const navigate = useNavigate();

    // Extract action definitions from marker children
    const actions: { key: string; render: () => ReactNode }[] = [];
    React.Children.forEach(children, (child) => {
        if (!React.isValidElement(child)) return;
        if ((child.type as any)?.displayName === "GridListNewButton") {
            const { to, label } = child.props as NewButtonProps;
            actions.push({
                key: `new-${label}`,
                render: () => (
                    <DropdownMenuItem onClick={() => navigate(to)}>
                        <IconPlus className="size-4 mr-2" />
                        New {label}
                    </DropdownMenuItem>
                ),
            });
        } else if ((child.type as any)?.displayName === "GridListAction") {
            const { children: actionChildren } = child.props as ActionProps;
            actions.push({
                key: `action-${actions.length}`,
                render: () => <>{actionChildren}</>,
            });
        }
    });
    const [pagination, setPagination] = useState({
        pageIndex: 0,
        pageSize: 12,
    });
    const [sorting, setSorting] = React.useState<SortingState>([]);
    const [globalFilter, setGlobalFilter] = useState("");

    const table = useReactTable({
        data: gridItemList,
        columns,
        onSortingChange: setSorting,
        getCoreRowModel: getCoreRowModel(),
        getPaginationRowModel: getPaginationRowModel(),
        getSortedRowModel: getSortedRowModel(),
        getFilteredRowModel: getFilteredRowModel(),
        state: {
            globalFilter,
            pagination,
            sorting,
        },
        onGlobalFilterChange: setGlobalFilter,
        getColumnCanGlobalFilter: (column) =>
            column.id === "name" || column.id === "description",
        onPaginationChange: setPagination,
    });

    const rows = table.getRowModel().rows;

    return (
        <div className="px-6">
            <div className="flex items-center gap-3 mb-4">
                <div className="relative flex-1 max-w-sm">
                    <IconSearch className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                    <Input
                        placeholder={t("forms.common.search")}
                        value={globalFilter ?? ""}
                        onChange={(e) => setGlobalFilter(e.target.value)}
                        className="pl-9"
                    />
                </div>
                <span className="text-sm text-muted-foreground whitespace-nowrap">
                    {table.getFilteredRowModel().rows.length} {table.getFilteredRowModel().rows.length !== 1 ? t("forms.common.items") : t("forms.common.item")}
                </span>
                {actions.length > 0 && (
                    <div className="ml-auto">
                        <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                                <GradientButton variant="outline" size="sm" className="gap-1.5">
                                    <IconDotsVertical className="size-4" />
                                    <span className="hidden sm:inline">{t("forms.common.actions")}</span>
                                </GradientButton>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                                {actions.map((a) => <React.Fragment key={a.key}>{a.render()}</React.Fragment>)}
                            </DropdownMenuContent>
                        </DropdownMenu>
                    </div>
                )}
            </div>

            {rows.length > 0 ? (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                    {rows.map((row) => {
                        const name: string = row.getValue("name");
                        const description: string = row.getValue("description");
                        const url: string = row.getValue("url");
                        const icon: string | undefined = row.original.icon ?? undefined;
                        const gradient = getGradient(name);
                        const initials = getInitials(name);

                        return (
                            <NavLink key={row.id} to={url} className="group">
                                <Card className="relative overflow-hidden p-0 h-full transition-all duration-200 hover:shadow-lg hover:shadow-blue-500/10 hover:-translate-y-0.5 border-border/60 hover:border-blue-500/30 dark:hover:border-blue-400/30">
                                    <div className={`h-1.5 bg-linear-to-r ${gradient} opacity-70 group-hover:opacity-100 transition-opacity`} />
                                    <div className="p-4 flex items-start gap-3">
                                        <div className={`shrink-0 w-10 h-10 rounded-lg bg-linear-to-br ${gradient} flex items-center justify-center text-white text-sm font-semibold shadow-sm`}>
                                            {icon ? <Icon icon={icon} className="size-5" /> : initials}
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <h3 className="font-medium text-sm truncate group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
                                                {name}
                                            </h3>
                                            {description ? (
                                                <p className="text-xs text-muted-foreground mt-1 line-clamp-2 leading-relaxed">
                                                    {description}
                                                </p>
                                            ) : (
                                                <p className="text-xs text-muted-foreground/50 mt-1 italic">
                                                    {t("forms.common.noDescription")}
                                                </p>
                                            )}
                                        </div>
                                        <IconChevronRight className="h-4 w-4 text-muted-foreground/40 group-hover:text-blue-500 transition-all group-hover:translate-x-0.5 mt-0.5 shrink-0" />
                                    </div>
                                </Card>
                            </NavLink>
                        );
                    })}
                </div>
            ) : (
                <Card className="p-8">
                    <div className="text-center text-muted-foreground">
                        <IconSearch className="h-8 w-8 mx-auto mb-2 opacity-40" />
                        <p className="text-sm">{t("forms.common.noResults")}</p>
                    </div>
                </Card>
            )}

            {table.getPageCount() > 1 && (
                <div className="flex items-center justify-end space-x-2 mt-4">
                    <div className="flex items-center space-x-2">
                        <p className="text-sm text-muted-foreground">{t("forms.common.perPage")}</p>
                        <Select
                            value={`${table.getState().pagination.pageSize}`}
                            onValueChange={(value) => table.setPageSize(Number(value))}
                        >
                            <SelectTrigger className="h-8 w-17.5">
                                <SelectValue placeholder={table.getState().pagination.pageSize} />
                            </SelectTrigger>
                            <SelectContent side="top">
                                {[12, 24, 36, 48].map((pageSize) => (
                                    <SelectItem key={pageSize} value={`${pageSize}`}>
                                        {pageSize}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </div>
                    <div className="flex w-25 items-center justify-center text-sm font-medium">
                        {t("forms.common.pageOf", { index: table.getState().pagination.pageIndex + 1, count: table.getPageCount() })}
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
            )}
        </div>
    );
};

export const GridList = Object.assign(GridListComponent, {
    NewButton: GridListNewButton,
    Action: GridListAction,
});
