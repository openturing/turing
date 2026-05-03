import { ROUTES } from "@/app/routes.const";
import { IconArrowsUpDown, IconChevronDown } from "@tabler/icons-react";
import { BadgeColorful } from "@/components/badge-colorful";
import { BadgeLocale } from "@/components/badge-locale";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { GradientButton } from "@/components/ui/gradient-button";
import { GradientButtonLink } from "@viglet/viglet-design-system/router";
import type { TurSNSiteLocale } from "@/models/sn/sn-site-locale.model.ts";
import { Icon } from "@iconify/react";
import { Checkbox } from "@radix-ui/react-checkbox";
import type { ColumnDef } from "@tanstack/react-table";
export const buildColumns = (t: (key: string) => string, storageEnabled = false): ColumnDef<TurSNSiteLocale>[] => [
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
                    {t("forms.localeTable.language")}
                    <IconArrowsUpDown />
                </GradientButton>
            )
        },
        cell: ({ row }) => (
            <div className="text-left font-medium"><BadgeLocale locale={row.getValue("language")} /></div>
        ),
    },
    {
        accessorKey: "core",
        header: () => <div className="text-left">{t("forms.localeTable.core")}</div>,
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
        header: () => <div className="text-right">{t("forms.localeTable.action")}</div>,
        cell: ({ row }) => {
            const locale = row.original;
            const site = locale.turSNSite;
            const hasTemplate = storageEnabled && !!site.searchTemplate?.trim();
            const searchUrl = `/sn/${site.name}?_setlocale=${locale.language}`;
            const defaultUrl = `/sn/${site.name}?_setlocale=${locale.language}&_embedded=true`;
            return (
                <div className="flex justify-end gap-2">
                    <GradientButtonLink variant="outline" size={"sm"} to={`${ROUTES.SN_INSTANCE}/${site.id}/locale/${locale.id}`}>
                        {t("forms.common.edit")}
                    </GradientButtonLink>
                    {hasTemplate ? (
                        <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                                <GradientButton size={"sm"}>
                                    {t("forms.localeTable.openSearch")}
                                    <IconChevronDown className="size-3.5" />
                                </GradientButton>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                                <DropdownMenuItem asChild>
                                    <a href={searchUrl} target="_blank">
                                        <Icon icon="mdi:card-search" className="size-4" />
                                        {t("forms.localeTable.openSearch")} ({site.searchTemplate})
                                    </a>
                                </DropdownMenuItem>
                                <DropdownMenuItem asChild>
                                    <a href={defaultUrl} target="_blank">
                                        <Icon icon="mdi:card-search-outline" className="size-4" />
                                        {t("forms.localeTable.searchDefault")}
                                    </a>
                                </DropdownMenuItem>
                            </DropdownMenuContent>
                        </DropdownMenu>
                    ) : (
                        <GradientButton asChild size={"sm"}>
                            <a href={searchUrl} target="_blank">
                                {t("forms.localeTable.openSearch")}
                            </a>
                        </GradientButton>
                    )}
                </div>
            )
        },
    }
]