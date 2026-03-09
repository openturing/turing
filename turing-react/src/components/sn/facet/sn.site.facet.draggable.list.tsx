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
import { GradientButton } from '../../ui/gradient-button';

interface SNSiteFacetDraggableListRowProps {
    row: TurSNSiteFacetOrdering;
    siteId: string;
}

const SNSiteFacetDraggableListRow: React.FC<SNSiteFacetDraggableListRowProps> = ({ row, siteId }) => {
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
                    aria-label="Drag to reorder"
                >
                    <GripVertical className="h-5 w-5 text-muted-foreground" />
                </button>
            </TableCell>
            <TableCell className="w-24 font-medium">{row.facetPosition}</TableCell>
            <TableCell className="text-muted-foreground">{row.name}</TableCell>
            <TableCell className="text-muted-foreground text-center"><BadgeColorful text={row.customFacet ? 'Custom Facet' : 'Faceted Field'} className="w-24" /></TableCell>
            <TableCell>{row.facetName}</TableCell>
            <TableCell className="text-muted-foreground">
                <GradientButton
                    asChild
                    variant="outline"
                    size="sm"
                    to={`${ROUTES.SN_INSTANCE}/${siteId}/${row.customFacet ? 'facet/custom' : 'facet/field'
                        }/${row.customFacet ? row.id : row.fieldExtId}`}
                >
                    Edit
                </GradientButton>
            </TableCell>
        </TableRow>
    );
};


interface SNSiteFacetDraggableListProps {
    siteId: string;
    tableData: TurSNSiteFacetOrdering[];
    setTableData: React.Dispatch<React.SetStateAction<TurSNSiteFacetOrdering[]>>;
}
const turSNFacetedFieldService = new TurSNFacetedFieldService();
export const SNSiteFacetDraggableList: React.FC<SNSiteFacetDraggableListProps> = ({ siteId, tableData, setTableData }) => {


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
                toast.success('Facet ordering saved.');
            } catch (error) {
                console.error('Failed to save facet ordering', error);
                toast.error('Failed to save facet ordering.');
            }
        }
    };

    const itemIds = tableData.map(item => item.id);

    return (
        <div className='px-6'>
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
                                <TableHead className="w-24">Position</TableHead>
                                <TableHead>Identifier</TableHead>
                                <TableHead className="text-center">Type</TableHead>
                                <TableHead>Facet Name</TableHead>
                                <TableHead>Action</TableHead>
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