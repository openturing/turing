import {
    type ColumnDef,
    type ColumnFiltersState,
    flexRender,
    getCoreRowModel,
    getFilteredRowModel,
    getPaginationRowModel,
    getSortedRowModel,
    type SortingState,
    useReactTable,
    type VisibilityState,
} from "@tanstack/react-table"
import * as React from "react"
import { useTranslation } from "react-i18next"

import {
    DropdownMenu,
    DropdownMenuCheckboxItem,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import { GradientButton } from "@/components/ui/gradient-button"
import { GradientButtonLink } from "@viglet/viglet-design-system/router"
import { GradientSwitch } from "@/components/ui/gradient-switch"
import { Input } from "@/components/ui/input"
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table"
import type { TurSNFieldCheck } from "@/models/sn/sn-field-check.model.ts"
import type {
    TurSNFieldRepairPayload,
    TurSNFieldRepairType,
} from "@/models/sn/sn-field-repair.model"
import type { TurSNStatusFields } from "@/models/sn/sn-field-status.model"
import type { TurSNSiteField } from "@/models/sn/sn-site-field.model.ts"
import { TurSNFieldService } from "@/services/sn/sn.field.service"
import { toast } from "@viglet/viglet-design-system"
import { IconAlertTriangle, IconArrowsUpDown, IconChevronDown, IconCircleCheck, IconColumns3Filled, IconLoader2 } from "@tabler/icons-react"
import type { PropsWithChildren } from "react"
import { BadgeFieldType } from "../../badge-field-type"

type StatusFieldDropdownProps = {
    statusField?: TurSNFieldCheck;
    onRepair: (payload: TurSNFieldRepairPayload) => Promise<void>;
    isRepairing: boolean;
};

type RepairRowProps = {
    label: React.ReactNode;
    action: React.ReactNode;
    disabled: boolean;
    onSelect: () => void;
};

/**
 * Single row inside the status dropdown. Uses Radix' `onSelect` so the click
 * is captured by the menu primitive (plain <button> children get their click
 * swallowed by the dropdown's pointer-down dismiss handler). `preventDefault`
 * keeps the menu open while the async repair runs.
 */
function RepairRow({ label, action, disabled, onSelect }: Readonly<RepairRowProps>) {
    return (
        <DropdownMenuItem
            disabled={disabled}
            onSelect={(event) => {
                event.preventDefault();
                if (disabled) return;
                onSelect();
            }}
            className="flex items-center justify-between gap-2 px-2 py-1 text-sm"
        >
            {label}
            <span className="flex items-center gap-1 text-xs text-primary hover:underline">
                {disabled && <IconLoader2 className="h-3 w-3 animate-spin" />}
                {action}
            </span>
        </DropdownMenuItem>
    );
}

function StatusFieldDropdown({ statusField, onRepair, isRepairing }: Readonly<StatusFieldDropdownProps>) {
    const { t } = useTranslation();
    if (!statusField) {
        return null;
    }

    const runRepair = (core: string, repairType: TurSNFieldRepairType) => {
        if (isRepairing) return;
        void onRepair({ id: statusField.id, core, repairType });
    };

    return (
        <DropdownMenu>
            <DropdownMenuTrigger asChild>
                <GradientButton variant="ghost" size="icon" className="h-6 w-6 p-0">
                    <span className="sr-only">{t("forms.fieldGrid.openStatus")}</span>
                    {isRepairing ? (
                        <IconLoader2 className="h-4 w-4 animate-spin text-primary" />
                    ) : statusField.correct ? (
                        <IconCircleCheck className="h-4 w-4 text-emerald-500" />
                    ) : (
                        <IconAlertTriangle className="h-4 w-4 text-rose-500" />
                    )}
                </GradientButton>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" className="w-80">
                {statusField.correct
                    ? statusField.cores.map((core) => (
                        <DropdownMenuItem
                            key={core.name}
                            className="flex items-center gap-2"
                        >
                            <IconCircleCheck className="h-4 w-4 text-emerald-500" />
                            <span>{core.name}</span>
                        </DropdownMenuItem>
                    ))
                    : statusField.cores.map((core) => (
                        <div key={core.name} className="py-1">
                            {core.correct ? (
                                <DropdownMenuItem className="flex items-center gap-2">
                                    <IconCircleCheck className="h-4 w-4 text-emerald-500" />
                                    <span>{core.name}</span>
                                </DropdownMenuItem>
                            ) : (
                                <>
                                    <RepairRow
                                        disabled={isRepairing}
                                        onSelect={() => runRepair(core.name, "REPAIR_ALL")}
                                        label={
                                            <span className="text-xs font-semibold text-muted-foreground">
                                                {core.name}
                                            </span>
                                        }
                                        action={t("forms.fieldGrid.repairAll")}
                                    />
                                    {!core.exists && (
                                        <RepairRow
                                            disabled={isRepairing}
                                            onSelect={() => runRepair(core.name, "SE_CREATE_FIELD")}
                                            label={
                                                <span className="flex items-center gap-2 text-rose-600">
                                                    <IconAlertTriangle className="h-4 w-4" />
                                                    {t("forms.fieldGrid.missingField")}
                                                </span>
                                            }
                                            action={t("forms.fieldGrid.repair")}
                                        />
                                    )}
                                    {core.exists && !statusField.facetIsCorrect && (
                                        <RepairRow
                                            disabled={isRepairing}
                                            onSelect={() => runRepair(core.name, "SN_CHANGE_TYPE")}
                                            label={
                                                <span className="flex items-center gap-2 text-rose-600">
                                                    <IconAlertTriangle className="h-4 w-4" />
                                                    {t("forms.fieldGrid.facetTypeIncorrect")}
                                                </span>
                                            }
                                            action={t("forms.fieldGrid.repair")}
                                        />
                                    )}
                                    {core.exists && !core.multiValuedIsCorrect && (
                                        <RepairRow
                                            disabled={isRepairing}
                                            onSelect={() => runRepair(core.name, "SE_ENABLE_MULTI_VALUE")}
                                            label={
                                                <span className="flex items-center gap-2 text-rose-600">
                                                    <IconAlertTriangle className="h-4 w-4" />
                                                    {t("forms.fieldGrid.multiValuedNotConfigured")}
                                                </span>
                                            }
                                            action={t("forms.fieldGrid.repair")}
                                        />
                                    )}
                                    {core.exists &&
                                        statusField.facetIsCorrect &&
                                        !core.typeIsCorrect && (
                                            <RepairRow
                                                disabled={isRepairing}
                                                onSelect={() => runRepair(core.name, "SE_CHANGE_TYPE")}
                                                label={
                                                    <span className="flex items-center gap-2 text-rose-600">
                                                        <IconAlertTriangle className="h-4 w-4" />
                                                        {t("forms.fieldGrid.usingType", { type: core.type })}
                                                    </span>
                                                }
                                                action={t("forms.fieldGrid.repair")}
                                            />
                                        )}
                                </>
                            )}
                        </div>
                    ))}
            </DropdownMenuContent>
        </DropdownMenu>
    )
}

type FieldToggleKey = "multiValued" | "mlt" | "facet" | "hl" | "enabled";

const buildColumns = (
    statusFieldMap: Map<string, TurSNFieldCheck>,
    onToggle: (fieldId: string, key: FieldToggleKey, checked: boolean) => void,
    isSavingField: (fieldId: string) => boolean,
    onRepair: (payload: TurSNFieldRepairPayload) => Promise<void>,
    isRepairingField: (fieldId: string) => boolean,
    t: (key: string, opts?: Record<string, unknown>) => string
): ColumnDef<TurSNSiteField>[] => [
        {
            accessorKey: "name",
            header: ({ column }) => {
                return (
                    <div className="w-full">
                        <GradientButton
                            variant="ghost"
                            onClick={() => column.toggleSorting(column.getIsSorted() === "asc")}
                        >
                            {t("forms.common.field")}
                            <IconArrowsUpDown />
                        </GradientButton>
                    </div>
                )
            },
            cell: ({ row }) => {
                const statusField = statusFieldMap.get(row.original.id);
                return (
                    <div className="flex items-center gap-2 w-full">
                        <StatusFieldDropdown
                            statusField={statusField}
                            onRepair={onRepair}
                            isRepairing={isRepairingField(row.original.id)}
                        />
                        <div className="text-left font-medium">{row.getValue("name")}</div>
                    </div>
                )
            },
        }, {
            accessorKey: "type",
            header: () => <div className="text-center">{t("forms.fieldGrid.type")}</div>,
            cell: ({ row }) => {
                const typeValue = row.getValue("type");
                const displayType = typeof typeValue === "string" ? typeValue : String(typeValue ?? "");

                return (
                    <div className="flex justify-center">
                        <BadgeFieldType type={displayType} />
                    </div>
                )
            },
        },
        {
            accessorKey: "multiValued",
            header: () => <div className="text-right">{t("forms.fieldGrid.multiValued")}</div>,
            cell: ({ row }) => {
                const multiValued = row.original.multiValued;
                const fieldId = row.original.id;
                return (
                    <div className="text-right font-medium">
                        <GradientSwitch
                            checked={multiValued == 1}
                            onCheckedChange={(checked) =>
                                onToggle(fieldId, "multiValued", checked)
                            }
                            disabled={isSavingField(fieldId)}
                        />
                    </div>
                )
            },
        },
        {
            accessorKey: "mlt",
            header: () => <div className="text-right">{t("forms.fieldGrid.mlt")}</div>,
            cell: ({ row }) => {
                const mlt = row.original.mlt;
                const fieldId = row.original.id;
                return (
                    <div className="text-right font-medium">
                        <GradientSwitch
                            checked={mlt == 1}
                            onCheckedChange={(checked) =>
                                onToggle(fieldId, "mlt", checked)
                            }
                            disabled={isSavingField(fieldId)}
                        />
                    </div>
                )
            },
        },
        {
            accessorKey: "facet",
            header: () => <div className="text-right">{t("forms.fieldGrid.facet")}</div>,
            cell: ({ row }) => {
                const facet = row.original.facet;
                const fieldId = row.original.id;
                return (
                    <div className="text-right font-medium">
                        <GradientSwitch
                            checked={facet == 1}
                            onCheckedChange={(checked) =>
                                onToggle(fieldId, "facet", checked)
                            }
                            disabled={isSavingField(fieldId)}
                        />
                    </div>
                )
            },
        },
        {
            accessorKey: "hl",
            header: () => <div className="text-right">{t("forms.fieldGrid.highlighting")}</div>,
            cell: ({ row }) => {
                const hl = row.original.hl;
                const fieldId = row.original.id;
                return (
                    <div className="text-right font-medium">
                        <GradientSwitch
                            checked={hl == 1}
                            onCheckedChange={(checked) =>
                                onToggle(fieldId, "hl", checked)
                            }
                            disabled={isSavingField(fieldId)}
                        />
                    </div>
                )
            },
        },
        {
            accessorKey: "enabled",
            header: () => <div className="text-right">{t("forms.fieldGrid.enabled")}</div>,
            cell: ({ row }) => {
                const enabled = row.original.enabled;
                const fieldId = row.original.id;
                return (
                    <div className="text-right font-medium">
                        <GradientSwitch
                            checked={enabled == 1}
                            onCheckedChange={(checked) =>
                                onToggle(fieldId, "enabled", checked)
                            }
                            disabled={isSavingField(fieldId)}
                        />
                    </div>
                )
            },
        },
        {
            id: "actions",
            header: () => <div className="text-center">{t("forms.common.actions")}</div>,
            enableHiding: false,
            cell: ({ row }) => {
                return (
                    <div className="text-center">
                        <GradientButtonLink variant="outline" size={"sm"} to={row.original.id}>
                            {t("forms.common.edit")}
                        </GradientButtonLink>
                    </div>
                )
            },
        },
    ]

const turSNFieldService = new TurSNFieldService();
interface Props {
    statusFields: TurSNStatusFields | null;
    id: string;
    data: TurSNSiteField[];
    setSnField: React.Dispatch<React.SetStateAction<TurSNSiteField[]>>;
    onRefreshStatus?: () => Promise<void> | void;
}
export const SNSiteFieldGridList: React.FC<PropsWithChildren<Props>> = ({ statusFields, id, data, setSnField, onRefreshStatus, children }) => {
    const { t } = useTranslation();
    const [sorting, setSorting] = React.useState<SortingState>([])
    const [columnFilters, setColumnFilters] = React.useState<ColumnFiltersState>(
        []
    )
    const isMobile = typeof window !== "undefined" && window.innerWidth < 768;
    const [columnVisibility, setColumnVisibility] =
        React.useState<VisibilityState>(isMobile ? {
            multiValued: false,
            mlt: false,
            facet: false,
            hl: false,
            enabled: false,
        } : {})
    const [savingFieldIds, setSavingFieldIds] = React.useState<Set<string>>(
        new Set()
    );
    const [repairingFieldIds, setRepairingFieldIds] = React.useState<Set<string>>(
        new Set()
    );

    const statusFieldMap = React.useMemo(() => {
        const map = new Map<string, TurSNFieldCheck>();
        statusFields?.fields?.forEach((field) => {
            map.set(field.id, field);
        });
        return map;
    }, [statusFields]);

    const setFieldSaving = React.useCallback((fieldId: string, saving: boolean) => {
        setSavingFieldIds((prev) => {
            const next = new Set(prev);
            if (saving) {
                next.add(fieldId);
            } else {
                next.delete(fieldId);
            }
            return next;
        });
    }, []);

    const isSavingField = React.useCallback(
        (fieldId: string) => savingFieldIds.has(fieldId),
        [savingFieldIds]
    );

    const handleToggle = React.useCallback(
        (fieldId: string, key: FieldToggleKey, checked: boolean) => {
            const currentField = data.find((field) => field.id === fieldId);
            if (!currentField) {
                return;
            }

            const updatedField: TurSNSiteField = {
                ...currentField,
                [key]: checked ? 1 : 0,
            };

            setSnField((prev) =>
                prev.map((field) =>
                    field.id === fieldId ? updatedField : field
                )
            );

            setFieldSaving(fieldId, true);
            turSNFieldService
                .update(id, updatedField)
                .catch((error) => {
                    console.error("Failed to update SN field", error);
                    setSnField((prev) =>
                        prev.map((field) =>
                            field.id === fieldId ? currentField : field
                        )
                    );
                })
                .finally(() => {
                    setFieldSaving(fieldId, false);
                });
        },
        [data, id, setFieldSaving]
    );

    const setFieldRepairing = React.useCallback((fieldId: string, repairing: boolean) => {
        setRepairingFieldIds((prev) => {
            const next = new Set(prev);
            if (repairing) {
                next.add(fieldId);
            } else {
                next.delete(fieldId);
            }
            return next;
        });
    }, []);

    const isRepairingField = React.useCallback(
        (fieldId: string) => repairingFieldIds.has(fieldId),
        [repairingFieldIds]
    );

    const handleRepair = React.useCallback(
        async (payload: TurSNFieldRepairPayload) => {
            setFieldRepairing(payload.id, true);
            try {
                await turSNFieldService.repair(id, payload);
                if (onRefreshStatus) {
                    await onRefreshStatus();
                }
                toast.success(t("forms.fieldGrid.repairSuccess"));
            } catch (error) {
                console.error("Failed to repair SN field", error);
                toast.error(t("forms.fieldGrid.repairError"));
            } finally {
                setFieldRepairing(payload.id, false);
            }
        },
        [id, onRefreshStatus, setFieldRepairing, t]
    );

    const columns = React.useMemo(
        () => buildColumns(statusFieldMap, handleToggle, isSavingField, handleRepair, isRepairingField, t),
        [statusFieldMap, handleToggle, isSavingField, handleRepair, isRepairingField, t]
    );

    const table = useReactTable({
        data,
        columns,
        onSortingChange: setSorting,
        onColumnFiltersChange: setColumnFilters,
        getCoreRowModel: getCoreRowModel(),
        getPaginationRowModel: getPaginationRowModel(),
        getSortedRowModel: getSortedRowModel(),
        getFilteredRowModel: getFilteredRowModel(),
        onColumnVisibilityChange: setColumnVisibility,
        state: {
            sorting,
            columnFilters,
            columnVisibility,
        },
        initialState: {
            pagination: {
                pageSize: 50,
                pageIndex: 0,
            },
        },
    });
    return (
        <div className="px-6">
            <div className="flex items-center py-4">
                <Input
                    placeholder={t("forms.common.filterFields")}
                    value={(table.getColumn("name")?.getFilterValue() as string) ?? ""}
                    onChange={(event) =>
                        table.getColumn("name")?.setFilterValue(event.target.value)
                    }
                    className="max-w-sm"
                />
                <div className="ml-auto flex items-center gap-2">
                    {children}
                    <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                            <GradientButton variant="outline" size="sm" className="hidden md:inline-flex">
                                <IconColumns3Filled /> {t("forms.common.columns")} <IconChevronDown />
                            </GradientButton>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                            {table
                                .getAllColumns()
                                .filter((column) => column.getCanHide())
                                .map((column) => {
                                    return (
                                        <DropdownMenuCheckboxItem
                                            key={column.id}
                                            className="capitalize"
                                            checked={column.getIsVisible()}
                                            onCheckedChange={(value) =>
                                                column.toggleVisibility(value)
                                            }
                                        >
                                            {column.id}
                                        </DropdownMenuCheckboxItem>
                                    )
                                })}
                        </DropdownMenuContent>
                    </DropdownMenu>
                </div>
            </div>
            <div className="overflow-hidden rounded-md border">
                <Table>
                    <TableHeader>
                        {table.getHeaderGroups().map((headerGroup) => (
                            <TableRow key={headerGroup.id}>
                                {headerGroup.headers.map((header) => {
                                    return (
                                        <TableHead key={header.id}>
                                            {header.isPlaceholder
                                                ? null
                                                : flexRender(
                                                    header.column.columnDef.header,
                                                    header.getContext()
                                                )}
                                        </TableHead>
                                    )
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
                                        <TableCell key={cell.id}>
                                            {flexRender(
                                                cell.column.columnDef.cell,
                                                cell.getContext()
                                            )}
                                        </TableCell>
                                    ))}
                                </TableRow>
                            ))
                        ) : (
                            <TableRow>
                                <TableCell
                                    colSpan={columns.length}
                                    className="h-24 text-center"
                                >
                                    {t("forms.common.noResultsDot")}
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
            </div>
            <div className="flex items-center justify-end space-x-2 py-4">
                <div className="text-muted-foreground flex-1 text-sm">
                    {table.getFilteredRowModel().rows.length} {t("forms.common.rows")}.
                </div>
                <div className="space-x-2">
                    <GradientButton
                        variant="outline"
                        size="sm"
                        onClick={() => table.previousPage()}
                        disabled={!table.getCanPreviousPage()}
                    >
                        {t("forms.common.previous")}
                    </GradientButton>
                    <GradientButton
                        variant="outline"
                        size="sm"
                        onClick={() => table.nextPage()}
                        disabled={!table.getCanNextPage()}
                    >
                        {t("forms.common.next")}
                    </GradientButton>
                </div>
            </div>
        </div>

    )
}
