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
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useTranslation } from "react-i18next";

import type {
  TurSNSiteCustomFacetItem,
  TurSNSiteCustomFacetOperator,
} from "@/models/sn/sn-site-custom-facet.model";
import {
  DEFAULT_OPERATOR,
  formatItemSummary,
  isBetweenOperator,
  OPERATOR_TRANSLATION_KEYS,
  OPERATOR_VALUES,
  toDateTimeLocalValue,
  usesOnlyEndValue,
  usesOnlyStartValue,
} from "./sn.site.custom.facet.form.utils";

interface ItemModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  editingItem: TurSNSiteCustomFacetItem;
  editingIndex: number | null;
  isDateField: boolean;
  fieldName: string;
  onUpdate: (key: keyof TurSNSiteCustomFacetItem, rawValue: string) => void;
  onSave: () => void;
}

/**
 * Add/edit dialog for a single facet item. The numeric vs. date inputs are
 * driven by the parent's `isDateField` flag so the same modal handles both
 * range types without duplication.
 */
export function CustomFacetItemModal({
  open,
  onOpenChange,
  editingItem,
  editingIndex,
  isDateField,
  fieldName,
  onUpdate,
  onSave,
}: Readonly<ItemModalProps>) {
  const { t } = useTranslation();
  const operator = editingItem.operator ?? DEFAULT_OPERATOR;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>
            {editingIndex !== null
              ? t("forms.snCustomFacet.editItem")
              : t("forms.snCustomFacet.addItemTitle")}
          </DialogTitle>
          <DialogDescription>{t("forms.snCustomFacet.itemDesc")}</DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-2">
          <div className="space-y-2">
            <label className="text-sm font-medium">
              {t("forms.snCustomFacet.label")}
            </label>
            <Input
              value={editingItem.label ?? ""}
              onChange={(e) => onUpdate("label", e.target.value)}
              placeholder="e.g., Under $100"
            />
          </div>
          <div className="space-y-2">
            <label className="text-sm font-medium">
              {t("forms.snCustomFacet.operator")}
            </label>
            <Select
              value={operator}
              onValueChange={(v) => onUpdate("operator", v as TurSNSiteCustomFacetOperator)}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {OPERATOR_VALUES.map((op) => (
                  <SelectItem key={op} value={op}>
                    <div>
                      <span className="font-medium">{t(OPERATOR_TRANSLATION_KEYS[op].label)}</span>
                      <span className="ml-2 text-xs text-muted-foreground">
                        {t(OPERATOR_TRANSLATION_KEYS[op].description)}
                      </span>
                    </div>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <p className="text-xs text-muted-foreground font-mono">
              {formatItemSummary(editingItem, isDateField, fieldName || "x")}
            </p>
          </div>
          {isBetweenOperator(operator) && (
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="text-sm font-medium">{t("forms.snCustomFacet.start")}</label>
                <Input
                  type={isDateField ? "datetime-local" : "number"}
                  step={isDateField ? undefined : "any"}
                  value={
                    isDateField
                      ? toDateTimeLocalValue(editingItem.rangeStartDate)
                      : (editingItem.rangeStart ?? "")
                  }
                  onChange={(e) =>
                    onUpdate(isDateField ? "rangeStartDate" : "rangeStart", e.target.value)
                  }
                  placeholder={t("forms.snCustomFacet.startPlaceholder")}
                />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium">{t("forms.snCustomFacet.end")}</label>
                <Input
                  type={isDateField ? "datetime-local" : "number"}
                  step={isDateField ? undefined : "any"}
                  value={
                    isDateField
                      ? toDateTimeLocalValue(editingItem.rangeEndDate)
                      : (editingItem.rangeEnd ?? "")
                  }
                  onChange={(e) =>
                    onUpdate(isDateField ? "rangeEndDate" : "rangeEnd", e.target.value)
                  }
                  placeholder={t("forms.snCustomFacet.endPlaceholder")}
                />
              </div>
            </div>
          )}
          {usesOnlyStartValue(operator) && (
            <div className="space-y-2">
              <label className="text-sm font-medium">{t("forms.common.value")}</label>
              <Input
                type={isDateField ? "datetime-local" : "number"}
                step={isDateField ? undefined : "any"}
                value={
                  isDateField
                    ? toDateTimeLocalValue(editingItem.rangeStartDate)
                    : (editingItem.rangeStart ?? "")
                }
                onChange={(e) =>
                  onUpdate(isDateField ? "rangeStartDate" : "rangeStart", e.target.value)
                }
                placeholder={
                  operator === "EQUAL"
                    ? t("forms.snCustomFacet.exactValuePlaceholder")
                    : t("forms.snCustomFacet.minimumValuePlaceholder")
                }
              />
            </div>
          )}
          {usesOnlyEndValue(operator) && (
            <div className="space-y-2">
              <label className="text-sm font-medium">{t("forms.common.value")}</label>
              <Input
                type={isDateField ? "datetime-local" : "number"}
                step={isDateField ? undefined : "any"}
                value={
                  isDateField
                    ? toDateTimeLocalValue(editingItem.rangeEndDate)
                    : (editingItem.rangeEnd ?? "")
                }
                onChange={(e) =>
                  onUpdate(isDateField ? "rangeEndDate" : "rangeEnd", e.target.value)
                }
                placeholder={t("forms.snCustomFacet.maximumValuePlaceholder")}
              />
            </div>
          )}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button type="button" onClick={onSave}>
            {editingIndex !== null
              ? t("forms.snCustomFacet.save")
              : t("forms.common.add")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
