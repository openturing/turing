"use client"
import { LanguageSelect } from "@/components/language-select";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { IconCirclePlus, IconTrash } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import type { TurLocale } from "@/models/locale/locale.model";
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

interface ItemFormFieldsProps {
  editingItem: TurSNSiteCustomFacetItem;
  isDateField: boolean;
  fieldName: string;
  availableLocales: TurLocale[];
  /** Resets internal `labelEntries` state when this key changes (e.g. open modal, change item). */
  resetKey?: string | number;
  onUpdate: (key: keyof TurSNSiteCustomFacetItem, rawValue: string) => void;
  onUpdateLabels: (labels: Record<string, string>) => void;
}

/**
 * Shared body for the Custom Facet Item editor (label, translations,
 * operator, range values). Used inline by both the legacy modal and the
 * standalone page. Owns the local `labelEntries` state so empty rows (locale
 * not yet picked) survive parent re-renders.
 */
export function CustomFacetItemFormFields({
  editingItem,
  isDateField,
  fieldName,
  availableLocales,
  resetKey,
  onUpdate,
  onUpdateLabels,
}: Readonly<ItemFormFieldsProps>) {
  const { t } = useTranslation();
  const operator = editingItem.operator ?? DEFAULT_OPERATOR;
  const [labelEntries, setLabelEntries] = useState<Array<{ locale: string; label: string }>>(
    () => Object.entries(editingItem.labels ?? {}).map(([locale, label]) => ({ locale, label })),
  );

  useEffect(() => {
    setLabelEntries(
      Object.entries(editingItem.labels ?? {}).map(([locale, label]) => ({ locale, label })),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resetKey]);

  function commitEntries(entries: Array<{ locale: string; label: string }>) {
    setLabelEntries(entries);
    const next: Record<string, string> = {};
    for (const entry of entries) {
      const localeKey = entry.locale.trim();
      if (localeKey) {
        next[localeKey] = entry.label;
      }
    }
    onUpdateLabels(next);
  }

  function addLabelEntry() {
    commitEntries([...labelEntries, { locale: "", label: "" }]);
  }

  function removeLabelEntry(index: number) {
    commitEntries(labelEntries.filter((_, i) => i !== index));
  }

  function updateLabelEntry(index: number, key: "locale" | "label", value: string) {
    commitEntries(
      labelEntries.map((entry, i) => (i === index ? { ...entry, [key]: value } : entry)),
    );
  }

  return (
    <div className="space-y-4 py-2">
      <div className="space-y-2">
        <label className="text-sm font-medium">{t("forms.snCustomFacet.label")}</label>
        <Input
          value={editingItem.label ?? ""}
          onChange={(e) => onUpdate("label", e.target.value)}
          placeholder="e.g., Under $100"
        />
        <div className="mt-4">
          <div className="flex items-center justify-between mb-2">
            <div className="grid gap-1">
              <span className="text-sm font-medium">
                {t("forms.snLabelTranslations.addTranslations")}
              </span>
              <span className="text-xs text-muted-foreground">
                {t("forms.snLabelTranslations.addTranslationsDesc")}
              </span>
            </div>
            <Button type="button" variant="outline" size="sm" onClick={addLabelEntry}>
              <IconCirclePlus className="h-4 w-4 mr-2" />
              {t("forms.snLabelTranslations.addLabel")}
            </Button>
          </div>
          <div className="space-y-2">
            {labelEntries.map((entry, index) => (
              <div key={`${entry.locale}-${index}`} className="flex items-center gap-2">
                <LanguageSelect
                  className="w-1/3"
                  value={entry.locale}
                  onValueChange={(value) => updateLabelEntry(index, "locale", value)}
                  locales={availableLocales}
                  extraLocaleValues={labelEntries.map((it) => it.locale)}
                />
                <Input
                  placeholder={t("forms.snLabelTranslations.enterLabel")}
                  value={entry.label}
                  onChange={(event) => updateLabelEntry(index, "label", event.target.value)}
                  className="flex-1"
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  onClick={() => removeLabelEntry(index)}
                >
                  <IconTrash className="h-4 w-4 text-red-500" />
                </Button>
              </div>
            ))}
          </div>
        </div>
      </div>
      <div className="space-y-2">
        <label className="text-sm font-medium">{t("forms.snCustomFacet.operator")}</label>
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
  );
}
