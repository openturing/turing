import { ROUTES } from "@/app/routes.const";
import { BadgeColorful } from "@/components/badge-colorful";
import { BadgeLocale } from "@/components/badge-locale";
import { GradientButton } from "@/components/ui/gradient-button";
import type { TurSNSiteLocale } from "@/models/sn/sn-site-locale.model.ts";
import { Checkbox } from "@radix-ui/react-checkbox";
import type { ColumnDef } from "@tanstack/react-table";
import { ArrowUpDown } from "lucide-react";

export const columns: ColumnDef<TurSNSiteLocale>[] = [
    {
        id: "select",
        header: ({ table }) => (
            <Checkbox
                checked={
                    table.getIsAllPageRowsSelected() ||
                    (table.getIsSomePageRowsSelected() && "indeterminate")
                }
                onCheckedChange={(value) => table.toggleAllPageRowsSelected(!!value)}
                aria-label="Select all"
            />
        ),
        cell: ({ row }) => (
            <Checkbox
                checked={row.getIsSelected()}
                onCheckedChange={(value) => row.toggleSelected(!!value)}
                aria-label="Select row"
            />
        ),
        enableSorting: false,
        enableHiding: false,
    },
    {
        accessorKey: "language",
        header: ({ column }) => {
            return (
                <GradientButton
                    variant="ghost"
                    onClick={() => column.toggleSorting(column.getIsSorted() === "asc")}
                >
                    Language
                    <ArrowUpDown />
                </GradientButton>
            )
        },
        cell: ({ row }) => (
            <div className="text-left font-medium"><BadgeLocale locale={row.getValue("language")} /></div>
        ),
    },
    {
        accessorKey: "core",
        header: () => <div className="text-left">Core</div>,
        cell: ({ row }) => {
            return <div className="text-left font-medium">
                <BadgeColorful
                    key={row.getValue("core")}
                    text={row.getValue("core")}
                />
            </div>
        },
    },
    {
        accessorKey: "action",
        header: () => <div className="text-right">Action</div>,
        cell: ({ row }) => {
            const locale = row.original;
            return (
                <div className="flex justify-end gap-2">
                    <GradientButton asChild variant="outline" size={"sm"} to={`${ROUTES.SN_INSTANCE}/${locale.turSNSite.id}/locale/${locale.id}`}>
                        Edit
                    </GradientButton>
                    <GradientButton asChild size={"sm"}>
                        <a href={`/sn/${locale.turSNSite.name}?_setlocale=${locale.language}`} target="_blank">
                            Open Search
                        </a>
                    </GradientButton>
                </div>
            )
        },
    }
]