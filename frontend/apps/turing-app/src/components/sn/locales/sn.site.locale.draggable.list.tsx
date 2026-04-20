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
import { GripVertical } from 'lucide-react';
import React from 'react';
import { useTranslation } from 'react-i18next';

import { ROUTES } from '@/app/routes.const';
import { BadgeColorful } from '@/components/badge-colorful';
import { BadgeLocale } from '@/components/badge-locale';
import { Badge } from '@/components/ui/badge';
import { GradientButton } from '@/components/ui/gradient-button';
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
import { toast } from 'sonner';

interface SNSiteLocaleDraggableListRowProps {
    row: TurSNSiteLocale;
    index: number;
}

const SNSiteLocaleDraggableListRow: React.FC<SNSiteLocaleDraggableListRowProps> = ({ row, index }) => {
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

    return (
        <TableRow ref={setNodeRef} style={style}>
            <TableCell className="w-12">
                <button
                    {...attributes}
                    {...listeners}
                    className="p-2 cursor-grab active:cursor-grabbing hover:bg-accent rounded transition-colors"
                    aria-label={t("forms.localeTable.dragToReorder")}
                >
                    <GripVertical className="h-5 w-5 text-muted-foreground" />
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
            <TableCell className="hidden md:table-cell">
                <BadgeColorful text={row.core} />
            </TableCell>
            <TableCell>
                <GradientButton
                    asChild
                    variant="outline"
                    size="sm"
                    to={`${ROUTES.SN_INSTANCE}/${row.turSNSite.id}/locale/${row.id}`}
                >
                    {t("forms.common.edit")}
                </GradientButton>
            </TableCell>
        </TableRow>
    );
};

interface SNSiteLocaleDraggableListProps {
    siteId: string;
    tableData: TurSNSiteLocale[];
    setTableData: React.Dispatch<React.SetStateAction<TurSNSiteLocale[]>>;
    children?: React.ReactNode;
}

const turSNSiteLocaleService = new TurSNSiteLocaleService();

export const SNSiteLocaleDraggableList: React.FC<SNSiteLocaleDraggableListProps> = ({
    siteId,
    tableData,
    setTableData,
    children,
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
                                <TableHead className="hidden md:table-cell">{t("forms.localeTable.core")}</TableHead>
                                <TableHead>{t("forms.localeTable.action")}</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            <SortableContext
                                items={itemIds}
                                strategy={verticalListSortingStrategy}
                            >
                                {tableData.map((row, index) => (
                                    <SNSiteLocaleDraggableListRow key={row.id} row={row} index={index} />
                                ))}
                            </SortableContext>
                        </TableBody>
                    </Table>
                </div>
            </DndContext>
        </div>
    );
};
