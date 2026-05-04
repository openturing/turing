"use client"
import { Button } from "@/components/ui/button";
import { TableCell, TableRow } from "@/components/ui/table";
import { CSS } from "@dnd-kit/utilities";
import { useSortable } from "@dnd-kit/sortable";
import { IconAlertTriangle, IconGripVertical, IconPencil, IconTrash } from "@tabler/icons-react";

import type { TurSNSiteCustomFacetItem } from "@/models/sn/sn-site-custom-facet.model";
import { formatItemSummary, operatorLabel } from "./sn.site.custom.facet.form.utils";

interface ItemRowProps {
  item: TurSNSiteCustomFacetItem;
  index: number;
  isDateField: boolean;
  fieldName: string;
  hasOverlap: boolean;
  t: (key: string) => string;
  onEdit: (index: number) => void;
  onRemove: (index: number) => void;
}

export function CustomFacetItemRow({
  item,
  index,
  isDateField,
  fieldName,
  hasOverlap,
  t,
  onEdit,
  onRemove,
}: Readonly<ItemRowProps>) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: item.id || `item-${index}` });

  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.8 : 1,
  };

  const overlapClass = hasOverlap
    ? "bg-amber-50 dark:bg-amber-950/30 border-l-4 border-l-amber-500"
    : "";

  return (
    <TableRow ref={setNodeRef} style={style} className={overlapClass}>
      <TableCell className="w-10">
        <button {...attributes} {...listeners} className="p-2 cursor-grab active:cursor-grabbing">
          <IconGripVertical className="h-4 w-4 text-muted-foreground" />
        </button>
      </TableCell>
      <TableCell className="w-16">
        <div className="flex items-center gap-2">
          <span>{index + 1}</span>
          {hasOverlap && (
            <IconAlertTriangle
              className="h-4 w-4 text-amber-600 dark:text-amber-400"
              aria-label={t("forms.snCustomFacet.overlapIconLabel")}
            />
          )}
        </div>
      </TableCell>
      <TableCell className="font-medium">
        {item.label || (
          <span className="text-muted-foreground italic">
            {t("forms.snCustomFacet.noLabel")}
          </span>
        )}
      </TableCell>
      <TableCell>
        <span className="inline-flex items-center rounded-md bg-muted px-2 py-1 text-xs font-medium">
          {operatorLabel(t, item.operator)}
        </span>
      </TableCell>
      <TableCell className="text-muted-foreground text-sm font-mono">
        {formatItemSummary(item, isDateField, fieldName || "x")}
      </TableCell>
      <TableCell className="w-24 text-right">
        <div className="flex justify-end gap-1">
          <Button type="button" variant="ghost" size="icon" onClick={() => onEdit(index)} title="Edit">
            <IconPencil className="h-4 w-4" />
          </Button>
          <Button type="button" variant="ghost" size="icon" onClick={() => onRemove(index)} title="Remove">
            <IconTrash className="h-4 w-4 text-destructive" />
          </Button>
        </div>
      </TableCell>
    </TableRow>
  );
}
