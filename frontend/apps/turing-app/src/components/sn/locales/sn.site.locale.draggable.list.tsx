import { IconChevronDown, IconGripVertical } from "@tabler/icons-react";
import {
    closestCenter,
    DndContext,
    type DragEndEvent,
    PointerSensor,
    useSensor,
    useSensors,
} from '@dnd-kit/core';
import {
    arrayMove,
    SortableContext,
    useSortable,
    verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import React from 'react';
import { useTranslation } from 'react-i18next';

import { ROUTES } from '@/app/routes.const';
import { BadgeColorful } from '@/components/badge-colorful';
import { BadgeLocale } from '@/components/badge-locale';
import { Badge } from '@/components/ui/badge';
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { GradientButton } from '@/components/ui/gradient-button';
import { GradientButtonLink } from '@viglet/viglet-design-system/router';
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '@/components/ui/table';
import type { TurSNSiteLocale } from '@/models/sn/sn-site-locale.model';
import { TurSNSiteLocaleService } from '@/services/sn/sn.site.locale.service';
import { Icon } from '@iconify/react';
import { toast } from 'sonner';

interface SNSiteLocaleDraggableListRowProps {
    row: TurSNSiteLocale;
    index: number;
    storageEnabled: boolean;
    baseRoute: string;
}

const SNSiteLocaleDraggableListRow: React.FC<SNSiteLocaleDraggableListRowProps> = ({ row, index, storageEnabled, baseRoute }) => {
    const { t } = useTranslation();
    const {
        attributes,
        listeners,
        setNodeRef,
        transform,
        transition,
        isDragging,
    } = useSortable({ id: row.id });

    const style: React.CSSProperties = {
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.8 : 1,
        zIndex: isDragging ? 1 : 0,
        position: 'relative',
    };

    const site = row.turSNSite;
    const hasTemplate = storageEnabled && !!site.searchTemplate?.trim();
    const searchUrl = `/sn/${site.name}?_setlocale=${row.language}`;
    const defaultUrl = `/sn/${site.name}?_setlocale=${row.language}&_embedded=true`;

    return (
        <TableRow ref={setNodeRef} style={style}>
            <TableCell className="w-12">
                <button
                    {...attributes}
                    {...listeners}
                    className="p-2 cursor-grab active:cursor-grabbing hover:bg-accent rounded transition-colors"
                    aria-label={t("forms.localeTable.dragToReorder")}
                >
                    <IconGripVertical className="h-5 w-5 text-muted-foreground" />
                </button>
            </TableCell>
            <TableCell>
                <div className="flex items-center gap-2">
                    <BadgeLocale locale={row.language} />
                    {index === 0 && (
                        <Badge variant="secondary" className="text-[10px]">
                            {t("forms.localeTable.defaultLanguage")}
                        </Badge>
                    )}
                </div>
            </TableCell>
            <TableCell>
                <BadgeColorful text={row.core} />
            </TableCell>
            <TableCell>
                <div className="flex justify-end gap-2">
                    <GradientButtonLink
                        variant="outline"
                        size="sm"
                        to={`${baseRoute}/${site.id}/locale/${row.id}`}
                    >
                        {t("forms.common.edit")}
                    </GradientButtonLink>
                    {hasTemplate ? (
                        <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                                <GradientButton size="sm">
                                    {t("forms.localeTable.openSearch")}
                                    <IconChevronDown className="size-3.5" />
                                </GradientButton>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                                <DropdownMenuItem asChild>
                                    <a href={searchUrl} target="_blank" rel="noreferrer">
                                        <Icon icon="mdi:card-search" className="size-4" />
                                        {t("forms.localeTable.openSearch")} ({site.searchTemplate})
                                    </a>
                                </DropdownMenuItem>
                                <DropdownMenuItem asChild>
                                    <a href={defaultUrl} target="_blank" rel="noreferrer">
                                        <Icon icon="mdi:card-search-outline" className="size-4" />
                                        {t("forms.localeTable.searchDefault")}
                                    </a>
                                </DropdownMenuItem>
                            </DropdownMenuContent>
                        </DropdownMenu>
                    ) : (
                        <GradientButton asChild size="sm">
                            <a href={searchUrl} target="_blank" rel="noreferrer">
                                {t("forms.localeTable.openSearch")}
                            </a>
                        </GradientButton>
                    )}
                </div>
            </TableCell>
        </TableRow>
    );
};

interface SNSiteLocaleDraggableListProps {
    siteId: string;
    tableData: TurSNSiteLocale[];
    setTableData: React.Dispatch<React.SetStateAction<TurSNSiteLocale[]>>;
    storageEnabled?: boolean;
    children?: React.ReactNode;
    /** SN instance base route for the row edit link. Defaults to the console;
     *  the Bento surface passes `ROUTES.BENTO_SN_INSTANCE` (T576). */
    baseRoute?: string;
}

const turSNSiteLocaleService = new TurSNSiteLocaleService();

export const SNSiteLocaleDraggableList: React.FC<SNSiteLocaleDraggableListProps> = ({
    siteId,
    tableData,
    setTableData,
    storageEnabled = false,
    children,
    baseRoute = ROUTES.SN_INSTANCE,
}) => {
    const { t } = useTranslation();
    const sensors = useSensors(useSensor(PointerSensor));

    const handleDragEnd = async (event: DragEndEvent) => {
        const { active, over } = event;
        if (over && active.id !== over.id) {
            const oldIndex = tableData.findIndex((item) => item.id === active.id);
            const newIndex = tableData.findIndex((item) => item.id === over.id);

            const reorderedItems = arrayMove(tableData, oldIndex, newIndex).map(
                (item, index) => ({
                    ...item,
                    position: index + 1,
                })
            );

            setTableData(reorderedItems);

            try {
                await turSNSiteLocaleService.saveOrdering(siteId, reorderedItems);
                toast.success(t("forms.localeTable.orderSaved"));
            } catch (error) {
                console.error('Failed to save locale ordering', error);
                toast.error(t("forms.localeTable.orderFailed"));
            }
        }
    };

    const itemIds = tableData.map((item) => item.id);

    return (
        <div className="px-6">
            {children && <div className="flex items-center justify-end gap-2 mb-4">{children}</div>}
            <DndContext
                sensors={sensors}
                collisionDetection={closestCenter}
                onDragEnd={handleDragEnd}
            >
                <div className="rounded-md border">
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead className="w-12"></TableHead>
                                <TableHead>{t("forms.localeTable.language")}</TableHead>
                                <TableHead>{t("forms.localeTable.core")}</TableHead>
                                <TableHead className="text-right">{t("forms.localeTable.action")}</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            <SortableContext
                                items={itemIds}
                                strategy={verticalListSortingStrategy}
                            >
                                {tableData.map((row, index) => (
                                    <SNSiteLocaleDraggableListRow key={row.id} row={row} index={index} storageEnabled={storageEnabled} baseRoute={baseRoute} />
                                ))}
                            </SortableContext>
                        </TableBody>
                    </Table>
                </div>
            </DndContext>
        </div>
    );
};
