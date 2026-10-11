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
import { useState } from "react";
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
  isDateMath,
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
  onUpdate: (key: keyof TurSNSiteCustomFacetItem, rawValue: string) => void;
  onUpdateLabels: (labels: Record<string, string>) => void;
}

/**
 * Shared body for the Custom Facet Item editor (label, translations,
 * operator, range values). Used inline by both the legacy modal and the
 * standalone page. Owns the local `labelEntries` state so empty rows (locale
 * not yet picked) survive parent re-renders.
 *
 * T1017 — reset by remounting. This used to take a `resetKey` prop and re-seed
 * `labelEntries` from it in an effect, which is React's `key` written by hand
 * and one render late: the old item's rows were on screen for a pass before the
 * effect replaced them. The caller passes `key` now, so the lazy initialiser
 * below is the only thing that seeds this state, and it runs before the first
 * paint of each item.
 */
export function CustomFacetItemFormFields({
  editingItem,
  isDateField,
  fieldName,
  availableLocales,
  onUpdate,
  onUpdateLabels,
}: Readonly<ItemFormFieldsProps>) {
  const { t } = useTranslation();
  const operator = editingItem.operator ?? DEFAULT_OPERATOR;
  const [labelEntries, setLabelEntries] = useState<Array<{ locale: string; label: string }>>(
    () => Object.entries(editingItem.labels ?? {}).map(([locale, label]) => ({ locale, label })),
  );

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
            {isDateField ? (
              <DateBoundInput
                value={editingItem.rangeStartDate}
                onChange={(value) => onUpdate("rangeStartDate", value)}
                placeholder={t("forms.snCustomFacet.startPlaceholder")}
              />
            ) : (
              <Input
                type="number"
                step="any"
                value={editingItem.rangeStart ?? ""}
                onChange={(e) => onUpdate("rangeStart", e.target.value)}
                placeholder={t("forms.snCustomFacet.startPlaceholder")}
              />
            )}
          </div>
          <div className="space-y-2">
            <label className="text-sm font-medium">{t("forms.snCustomFacet.end")}</label>
            {isDateField ? (
              <DateBoundInput
                value={editingItem.rangeEndDate}
                onChange={(value) => onUpdate("rangeEndDate", value)}
                placeholder={t("forms.snCustomFacet.endPlaceholder")}
              />
            ) : (
              <Input
                type="number"
                step="any"
                value={editingItem.rangeEnd ?? ""}
                onChange={(e) => onUpdate("rangeEnd", e.target.value)}
                placeholder={t("forms.snCustomFacet.endPlaceholder")}
              />
            )}
          </div>
        </div>
      )}
      {usesOnlyStartValue(operator) && (
        <div className="space-y-2">
          <label className="text-sm font-medium">{t("forms.common.value")}</label>
          {isDateField ? (
            <DateBoundInput
              value={editingItem.rangeStartDate}
              onChange={(value) => onUpdate("rangeStartDate", value)}
              placeholder={
                operator === "EQUAL"
                  ? t("forms.snCustomFacet.exactValuePlaceholder")
                  : t("forms.snCustomFacet.minimumValuePlaceholder")
              }
            />
          ) : (
            <Input
              type="number"
              step="any"
              value={editingItem.rangeStart ?? ""}
              onChange={(e) => onUpdate("rangeStart", e.target.value)}
              placeholder={
                operator === "EQUAL"
                  ? t("forms.snCustomFacet.exactValuePlaceholder")
                  : t("forms.snCustomFacet.minimumValuePlaceholder")
              }
            />
          )}
        </div>
      )}
      {usesOnlyEndValue(operator) && (
        <div className="space-y-2">
          <label className="text-sm font-medium">{t("forms.common.value")}</label>
          {isDateField ? (
            <DateBoundInput
              value={editingItem.rangeEndDate}
              onChange={(value) => onUpdate("rangeEndDate", value)}
              placeholder={t("forms.snCustomFacet.maximumValuePlaceholder")}
            />
          ) : (
            <Input
              type="number"
              step="any"
              value={editingItem.rangeEnd ?? ""}
              onChange={(e) => onUpdate("rangeEnd", e.target.value)}
              placeholder={t("forms.snCustomFacet.maximumValuePlaceholder")}
            />
          )}
        </div>
      )}
    </div>
  );
}

interface DateBoundInputProps {
  value?: string | null;
  onChange: (rawValue: string) => void;
  placeholder: string;
}

/**
 * T1075 — one date bound of an item: a fixed date, or relative date math
 * (`NOW-7DAYS`) that moves with today, which a "last 7 days" bucket needs.
 * The relative text is kept locally so a half-typed expression is not cleared
 * while the parent only stores values that already read as a bound.
 */
function DateBoundInput({ value, onChange, placeholder }: Readonly<DateBoundInputProps>) {
  const { t } = useTranslation();
  const [relative, setRelative] = useState(() => isDateMath(value));
  const [text, setText] = useState(() => (isDateMath(value) ? value : ""));

  function switchMode(mode: string) {
    const nextRelative = mode === "relative";
    setRelative(nextRelative);
    const next = nextRelative ? t("forms.snCustomFacet.relativeDatePlaceholder") : "";
    setText(next);
    onChange(next);
  }

  return (
    <div className="space-y-1">
      <div className="flex gap-2">
        <Select value={relative ? "relative" : "fixed"} onValueChange={switchMode}>
          <SelectTrigger className="w-32 shrink-0">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="fixed">{t("forms.snCustomFacet.fixedDate")}</SelectItem>
            <SelectItem value="relative">{t("forms.snCustomFacet.relativeDate")}</SelectItem>
          </SelectContent>
        </Select>
        {relative ? (
          <Input
            className="flex-1 font-mono"
            value={text}
            onChange={(e) => {
              setText(e.target.value);
              onChange(e.target.value);
            }}
            placeholder={t("forms.snCustomFacet.relativeDatePlaceholder")}
          />
        ) : (
          <Input
            className="flex-1"
            type="datetime-local"
            value={toDateTimeLocalValue(value)}
            onChange={(e) => onChange(e.target.value)}
            placeholder={placeholder}
          />
        )}
      </div>
      {relative && (
        <p className="text-xs text-muted-foreground">{t("forms.snCustomFacet.relativeDateHint")}</p>
      )}
    </div>
  );
}