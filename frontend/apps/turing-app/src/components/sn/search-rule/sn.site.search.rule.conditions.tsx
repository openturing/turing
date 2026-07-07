"use client"
import { GradientButton } from "@/components/ui/gradient-button"
import { Input } from "@/components/ui/input"
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select"
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table"
import type {
    TurSNSiteSearchRule,
    TurSNSiteSearchRuleCondition,
    TurSNSiteSearchRuleParameter,
    TurSNSiteSearchRuleOperator,
    TurSNSiteSearchRuleLogicOperator,
    TurSNSiteSearchRuleFieldOption,
} from "@/models/sn/sn-site-search-rule.model"
import type { TurSNSiteCustomSort } from "@/models/sn/sn-site-custom-sort.model"
import type { TurSNSiteLocale } from "@/models/sn/sn-site-locale.model"
import { IconCirclePlus, IconFilter, IconTrash, IconX } from "@tabler/icons-react"
import React, { useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import type { UseFormReturn } from "react-hook-form"
import { SNSiteSearchRuleSection, type SNSectionChrome } from "./sn.site.search.rule.section"

const ALL_PARAMETERS: TurSNSiteSearchRuleParameter[] = ["QUERY", "FILTER_QUERY", "SORT", "LOCALE"];
const OPERATORS: TurSNSiteSearchRuleOperator[] = ["EQUALS", "CONTAINS", "STARTS_WITH", "MATCHES_ANY", "IS_EMPTY"];
const LOGIC_OPERATORS: TurSNSiteSearchRuleLogicOperator[] = ["AND", "OR"];
const OR_ONLY_PARAMETERS = new Set<TurSNSiteSearchRuleParameter>(["SORT", "LOCALE"]);
const SORT_PRESETS = ["relevance", "newest", "oldest"] as const;

// ── Sort Value Selector ─────────────────────────────────────────────

function SortValueSelector({ value, onChange, fieldOptions, customSorts, t }: {
    value: string;
    onChange: (val: string) => void;
    fieldOptions: TurSNSiteSearchRuleFieldOption[];
    customSorts: TurSNSiteCustomSort[];
    t: (key: string) => string;
}) {
    const isFieldSort = value.includes(":");
    const isPreset = !isFieldSort && SORT_PRESETS.includes(value as typeof SORT_PRESETS[number]);
    // Any non-empty non-preset non-field value is treated as custom — even when
    // `customSorts` hasn't loaded yet (edit page race), so the mode dropdown
    // doesn't flip to "Preset" + "relevance" while the catalogue is in flight.
    const isCustomSort = !isFieldSort && !isPreset && value !== "";
    // Track explicit user-driven switches (e.g. clicking "Custom Sort" while
    // catalogues are empty) so the mode doesn't snap back to preset when the
    // value becomes "". Derived state takes priority once the value matches.
    const [explicitMode, setExplicitMode] = useState<"preset" | "custom" | "field" | null>(null);
    const derivedMode: "preset" | "custom" | "field" =
        isFieldSort ? "field" : isCustomSort ? "custom" : "preset";
    const mode = explicitMode && !isFieldSort && !isPreset && !isCustomSort
        ? explicitMode
        : derivedMode;

    const fieldName = isFieldSort ? value.split(":")[0] : "";
    const fieldDir = isFieldSort ? (value.split(":")[1] || "asc") : "asc";

    function handleModeChange(newMode: string) {
        const m = newMode as "preset" | "custom" | "field";
        setExplicitMode(m);
        if (m === "preset") onChange("relevance");
        else if (m === "custom") onChange(customSorts.length > 0 ? customSorts[0].name : "");
        else onChange(fieldOptions.length > 0 ? `${fieldOptions[0].name}:asc` : ":asc");
    }

    return (
        <div className="flex items-center gap-2">
            <Select value={mode} onValueChange={handleModeChange}>
                <SelectTrigger className="w-40">
                    <SelectValue />
                </SelectTrigger>
                <SelectContent>
                    <SelectItem value="preset">{t("sn.searchRule.sortMode.preset")}</SelectItem>
                    {customSorts.length > 0 && (
                        <SelectItem value="custom">{t("sn.searchRule.sortMode.customSort")}</SelectItem>
                    )}
                    <SelectItem value="field">{t("sn.searchRule.sortMode.field")}</SelectItem>
                </SelectContent>
            </Select>
            {mode === "preset" && (
                <Select value={isPreset ? value : "relevance"} onValueChange={onChange}>
                    <SelectTrigger className="flex-1"><SelectValue /></SelectTrigger>
                    <SelectContent>
                        {SORT_PRESETS.map((p) => (
                            <SelectItem key={p} value={p}>{t(`sn.searchRule.sortPresets.${p}`)}</SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            )}
            {mode === "custom" && (
                <Select value={isCustomSort ? value : ""} onValueChange={onChange}>
                    <SelectTrigger className="flex-1"><SelectValue placeholder={t("sn.searchRule.sortMode.customSort")} /></SelectTrigger>
                    <SelectContent>
                        {/* Include the current value as a fallback option when the
                            catalogue hasn't loaded yet, so the saved sort still renders
                            instead of showing an empty/placeholder Select. */}
                        {isCustomSort && !customSorts.some((cs) => cs.name === value) && (
                            <SelectItem key={`__current__${value}`} value={value}>{value}</SelectItem>
                        )}
                        {customSorts.map((cs) => (
                            <SelectItem key={cs.id ?? cs.name} value={cs.name}>{cs.name}</SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            )}
            {mode === "field" && (
                <>
                    <Select value={fieldName} onValueChange={(f) => onChange(`${f}:${fieldDir}`)}>
                        <SelectTrigger className="flex-1"><SelectValue placeholder={t("sn.searchRule.field")} /></SelectTrigger>
                        <SelectContent>
                            {fieldOptions.map((opt) => (
                                <SelectItem key={opt.id} value={opt.name}>{opt.name}</SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                    <Select value={fieldDir} onValueChange={(d) => onChange(`${fieldName}:${d}`)}>
                        <SelectTrigger className="w-24"><SelectValue /></SelectTrigger>
                        <SelectContent>
                            <SelectItem value="asc">ASC</SelectItem>
                            <SelectItem value="desc">DESC</SelectItem>
                        </SelectContent>
                    </Select>
                </>
            )}
        </div>
    );
}

// ── Condition Logic Cell ────────────────────────────────────────────

function ConditionLogicCell({ localIdx, isOrOnly, logicOperator, globalIndex, onUpdate, t }: {
    localIdx: number;
    isOrOnly: boolean;
    logicOperator?: TurSNSiteSearchRuleLogicOperator;
    globalIndex: number;
    onUpdate: (idx: number, field: "logicOperator", val: TurSNSiteSearchRuleLogicOperator) => void;
    t: (key: string) => string;
}) {
    if (localIdx === 0) return <span></span>;
    if (isOrOnly) {
        return (
            <span className="text-xs text-muted-foreground px-2 py-1 rounded bg-muted">
                {t("sn.searchRule.logicOperatorValues.OR")}
            </span>
        );
    }
    return (
        <Select
            value={logicOperator || "AND"}
            onValueChange={(val) => onUpdate(globalIndex, "logicOperator", val as TurSNSiteSearchRuleLogicOperator)}
        >
            <SelectTrigger className="w-20"><SelectValue /></SelectTrigger>
            <SelectContent>
                {LOGIC_OPERATORS.map((lo) => (
                    <SelectItem key={lo} value={lo}>{t(`sn.searchRule.logicOperatorValues.${lo}`)}</SelectItem>
                ))}
            </SelectContent>
        </Select>
    );
}

// ── Export: SortValueSelector for Actions ────────────────────────────
export { SortValueSelector };

// ── Main Conditions Component ───────────────────────────────────────

interface Props {
    form: UseFormReturn<TurSNSiteSearchRule>;
    fieldOptions: TurSNSiteSearchRuleFieldOption[];
    customSorts: TurSNSiteCustomSort[];
    siteLocales: TurSNSiteLocale[];
    chrome?: SNSectionChrome;
}

export const SNSiteSearchRuleConditions: React.FC<Props> = ({ form, fieldOptions, customSorts, siteLocales, chrome = "console" }) => {
    const { t } = useTranslation();
    const conditions = form.watch("conditions") || [];

    const usedParameters = useMemo(() => {
        const params = new Set<TurSNSiteSearchRuleParameter>();
        for (const c of conditions) params.add(c.parameter);
        return params;
    }, [conditions]);

    const availableParameters = useMemo(() =>
        ALL_PARAMETERS.filter(p => !usedParameters.has(p)),
        [usedParameters]
    );

    const groupedConditions = useMemo(() => {
        const groups: { parameter: TurSNSiteSearchRuleParameter; items: { condition: TurSNSiteSearchRuleCondition; globalIndex: number }[] }[] = [];
        const groupMap = new Map<TurSNSiteSearchRuleParameter, typeof groups[number]>();
        for (let i = 0; i < conditions.length; i++) {
            const c = conditions[i];
            let group = groupMap.get(c.parameter);
            if (!group) {
                group = { parameter: c.parameter, items: [] };
                groupMap.set(c.parameter, group);
                groups.push(group);
            }
            group.items.push({ condition: c, globalIndex: i });
        }
        return groups;
    }, [conditions]);

    function getDefaultValueForParam(parameter: TurSNSiteSearchRuleParameter): string {
        if (parameter === "SORT") return "relevance";
        if (parameter === "LOCALE") return siteLocales.length > 0 ? siteLocales[0].language : "";
        return "";
    }

    function addConditionGroup(parameter: TurSNSiteSearchRuleParameter) {
        const current = form.getValues("conditions") || [];
        form.setValue("conditions", [...current, {
            parameter, operator: "EQUALS" as const,
            value: getDefaultValueForParam(parameter), logicOperator: "AND" as const,
        }]);
    }

    function addConditionToGroup(parameter: TurSNSiteSearchRuleParameter) {
        const current = form.getValues("conditions") || [];
        const lastIdx = current.map((c, i) => c.parameter === parameter ? i : -1).filter(i => i >= 0).pop();
        const isOrOnly = OR_ONLY_PARAMETERS.has(parameter);
        const newCond: TurSNSiteSearchRuleCondition = {
            parameter, operator: "EQUALS",
            value: getDefaultValueForParam(parameter),
            logicOperator: isOrOnly ? "OR" : "AND",
        };
        if (lastIdx !== undefined) {
            const updated = [...current];
            updated.splice(lastIdx + 1, 0, newCond);
            form.setValue("conditions", updated);
        } else {
            form.setValue("conditions", [...current, newCond]);
        }
    }

    function removeCondition(globalIndex: number) {
        const current = form.getValues("conditions") || [];
        form.setValue("conditions", current.filter((_, i) => i !== globalIndex));
    }

    function removeConditionGroup(parameter: TurSNSiteSearchRuleParameter) {
        const current = form.getValues("conditions") || [];
        form.setValue("conditions", current.filter(c => c.parameter !== parameter));
    }

    function updateCondition<K extends keyof TurSNSiteSearchRuleCondition>(
        globalIndex: number, field: K, val: TurSNSiteSearchRuleCondition[K]
    ) {
        const current = [...(form.getValues("conditions") || [])];
        current[globalIndex] = { ...current[globalIndex], [field]: val };
        form.setValue("conditions", current);
    }

    // ── Render helpers ──────────────────────────────────────────

    function renderValueCells(
        param: TurSNSiteSearchRuleParameter,
        item: { condition: TurSNSiteSearchRuleCondition; globalIndex: number },
    ) {
        if (param === "SORT") {
            return (
                <TableCell colSpan={2}>
                    <SortValueSelector value={item.condition.value || "relevance"}
                        onChange={(val) => updateCondition(item.globalIndex, "value", val)}
                        fieldOptions={fieldOptions} customSorts={customSorts} t={t} />
                </TableCell>
            );
        }
        if (param === "LOCALE") {
            return (
                <TableCell colSpan={2}>
                    <Select value={item.condition.value || ""}
                        onValueChange={(val) => updateCondition(item.globalIndex, "value", val)}>
                        <SelectTrigger><SelectValue placeholder={t("sn.searchRule.locale")} /></SelectTrigger>
                        <SelectContent>
                            {siteLocales.map((loc) => (
                                <SelectItem key={loc.id} value={loc.language}>{loc.language}</SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                </TableCell>
            );
        }
        return (
            <>
                <TableCell>
                    <Select value={item.condition.operator}
                        onValueChange={(val) => updateCondition(item.globalIndex, "operator", val as TurSNSiteSearchRuleOperator)}>
                        <SelectTrigger><SelectValue /></SelectTrigger>
                        <SelectContent>
                            {OPERATORS.map((o) => (
                                <SelectItem key={o} value={o}>{t(`sn.searchRule.operatorValues.${o}`)}</SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                </TableCell>
                <TableCell>
                    <Input value={item.condition.value || ""}
                        onChange={(e) => updateCondition(item.globalIndex, "value", e.target.value)}
                        placeholder={item.condition.operator === "IS_EMPTY" ? "" : "e.g. education"}
                        disabled={item.condition.operator === "IS_EMPTY"} />
                </TableCell>
            </>
        );
    }

    return (
        <SNSiteSearchRuleSection chrome={chrome} icon={IconFilter} tone="amber" title={t("sn.searchRule.conditions")} description={t("sn.searchRule.conditionsDesc")}>
                <div className="space-y-4">
                    {groupedConditions.map((group, groupIdx) => {
                        const hasCustomLayout = group.parameter === "SORT" || group.parameter === "LOCALE";
                        return (
                            <div key={group.parameter} className="border rounded-lg p-4 space-y-3">
                                <div className="flex items-center justify-between">
                                    <div className="flex items-center gap-2">
                                        <span className="font-medium text-sm">
                                            {t(`sn.searchRule.parameterValues.${group.parameter}`)}
                                        </span>
                                        {groupIdx > 0 && (
                                            <span className="text-xs text-muted-foreground px-2 py-0.5 rounded bg-muted">AND</span>
                                        )}
                                    </div>
                                    <GradientButton variant="ghost" size="icon" type="button"
                                        onClick={() => removeConditionGroup(group.parameter)} className="h-7 w-7">
                                        <IconX className="h-4 w-4" />
                                    </GradientButton>
                                </div>
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            {group.items.length > 1 && <TableHead className="w-20"></TableHead>}
                                            {group.parameter === "FILTER_QUERY" && <TableHead>{t("sn.searchRule.field")}</TableHead>}
                                            {hasCustomLayout ? (
                                                <TableHead colSpan={2}>
                                                    {group.parameter === "SORT" ? t("sn.searchRule.sortValue") : t("sn.searchRule.locale")}
                                                </TableHead>
                                            ) : (
                                                <>
                                                    <TableHead>{t("sn.searchRule.operator")}</TableHead>
                                                    <TableHead>{t("sn.searchRule.value")}</TableHead>
                                                </>
                                            )}
                                            <TableHead className="w-16 text-right">{t("forms.common.actions")}</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {group.items.map((item, localIdx) => (
                                            <TableRow key={item.condition.id ?? `cond-${item.globalIndex}`}>
                                                {group.items.length > 1 && (
                                                    <TableCell>
                                                        <ConditionLogicCell localIdx={localIdx}
                                                            isOrOnly={OR_ONLY_PARAMETERS.has(group.parameter)}
                                                            logicOperator={item.condition.logicOperator}
                                                            globalIndex={item.globalIndex}
                                                            onUpdate={updateCondition} t={t} />
                                                    </TableCell>
                                                )}
                                                {group.parameter === "FILTER_QUERY" && (
                                                    <TableCell>
                                                        <Select value={item.condition.fieldName || ""}
                                                            onValueChange={(val) => updateCondition(item.globalIndex, "fieldName", val)}>
                                                            <SelectTrigger><SelectValue placeholder={t("sn.searchRule.field")} /></SelectTrigger>
                                                            <SelectContent>
                                                                {fieldOptions.map((opt) => (
                                                                    <SelectItem key={opt.id} value={opt.name}>{opt.name}</SelectItem>
                                                                ))}
                                                            </SelectContent>
                                                        </Select>
                                                    </TableCell>
                                                )}
                                                {renderValueCells(group.parameter, item)}
                                                <TableCell className="text-right">
                                                    <GradientButton variant="destructive" size="icon" type="button"
                                                        onClick={() => removeCondition(item.globalIndex)}>
                                                        <IconTrash className="h-4 w-4" />
                                                    </GradientButton>
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                                <GradientButton variant="outline" size="sm" type="button" onClick={() => addConditionToGroup(group.parameter)}>
                                    <IconCirclePlus className="h-3 w-3 mr-1" />
                                    {t("sn.searchRule.addCondition")}
                                </GradientButton>
                            </div>
                        );
                    })}
                </div>
                {availableParameters.length > 0 && (
                    <div className="mt-4 flex items-center gap-2">
                        <span className="text-sm text-muted-foreground">{t("sn.searchRule.addGroup")}:</span>
                        {availableParameters.map((p) => (
                            <GradientButton key={p} variant="outline" size="sm" type="button" onClick={() => addConditionGroup(p)}>
                                <IconCirclePlus className="h-3 w-3 mr-1" />
                                {t(`sn.searchRule.parameterValues.${p}`)}
                            </GradientButton>
                        ))}
                    </div>
                )}
        </SNSiteSearchRuleSection>
    );
};
