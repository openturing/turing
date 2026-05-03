import { IconGripVertical } from "@tabler/icons-react";
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
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '@/components/ui/table';
import type { TurSNSiteFacetOrdering } from '@/models/sn/sn-site-facet-ordering.model';
import { TurSNFacetedFieldService } from '@/services/sn/sn.faceted.field.service';
import { toast } from 'sonner';
import { BadgeColorful } from '../../badge-colorful';
import { GradientButtonLink } from '@viglet/viglet-design-system/router';

interface SNSiteFacetDraggableListRowProps {
    row: TurSNSiteFacetOrdering;
    siteId: string;
}

const SNSiteFacetDraggableListRow: React.FC<SNSiteFacetDraggableListRowProps> = ({ row, siteId }) => {
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
                    aria-label={t("forms.facetDraggable.dragToReorder")}
                >
                    <IconGripVertical className="h-5 w-5 text-muted-foreground" />
                </button>
            </TableCell>
            <TableCell className="text-muted-foreground">
                <div className="flex items-center gap-2">
                    <span title={row.customFacet ? t("forms.facetDraggable.customFacet") : t("forms.facetDraggable.facetedField")}>
                        <BadgeColorful text={row.customFacet ? 'CF' : 'FF'} className="w-7 text-[10px]" />
                    </span>
                    <span>{row.name}</span>
                </div>
            </TableCell>
            <TableCell className="hidden md:table-cell">{row.facetName}</TableCell>
            <TableCell className="text-muted-foreground">
                <GradientButtonLink
                    variant="outline"
                    size="sm"
                    to={`${ROUTES.SN_INSTANCE}/${siteId}/${row.customFacet ? 'facet/custom' : 'facet/field'
                        }/${row.customFacet ? row.id : row.fieldExtId}`}
                >
                    {t("forms.common.edit")}
                </GradientButtonLink>
            </TableCell>
        </TableRow>
    );
};


interface SNSiteFacetDraggableListProps {
    siteId: string;
    tableData: TurSNSiteFacetOrdering[];
    setTableData: React.Dispatch<React.SetStateAction<TurSNSiteFacetOrdering[]>>;
    children?: React.ReactNode;
}
const turSNFacetedFieldService = new TurSNFacetedFieldService();
export const SNSiteFacetDraggableList: React.FC<SNSiteFacetDraggableListProps> = ({ siteId, tableData, setTableData, children }) => {
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
                    facetPosition: index + 1,
                })
            );

            setTableData(reorderedItems);

            try {
                await turSNFacetedFieldService.saveOrdering(siteId, reorderedItems);
                toast.success(t("forms.facetDraggable.orderSaved"));
            } catch (error) {
                console.error('Failed to save facet ordering', error);
                toast.error(t("forms.facetDraggable.orderFailed"));
            }
        }
    };

    const itemIds = tableData.map(item => item.id);

    return (
        <div className='px-6'>
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
                                <TableHead>{t("forms.integrationManager.identifier")}</TableHead>
                                <TableHead className="hidden md:table-cell">{t("forms.facetDraggable.facetName")}</TableHead>
                                <TableHead>{t("forms.localeTable.action")}</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            <SortableContext
                                items={itemIds}
                                strategy={verticalListSortingStrategy}
                            >
                                {tableData.map((row) => (
                                    <SNSiteFacetDraggableListRow key={row.id} row={row} siteId={siteId} />
                                ))}
                            </SortableContext>
                        </TableBody>
                    </Table>
                </div>
            </DndContext>
        </div>
    );
};