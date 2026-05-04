"use client"
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useTranslation } from "react-i18next";

interface FieldChangeDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Confirmation dialog shown when the user picks a different field for a facet
 * that already has filled-in range values — switching from a numeric to a date
 * field (or vice versa) clears values that don't apply to the new type.
 */
export function CustomFacetFieldChangeDialog({
  open,
  onOpenChange,
  onConfirm,
  onCancel,
}: Readonly<FieldChangeDialogProps>) {
  const { t } = useTranslation();
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("forms.snCustomFacet.changeField")}</DialogTitle>
          <DialogDescription>{t("forms.snCustomFacet.changeFieldDesc")}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onCancel}>
            Cancel
          </Button>
          <Button type="button" onClick={onConfirm}>
            {t("forms.snCustomFacet.continue")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
