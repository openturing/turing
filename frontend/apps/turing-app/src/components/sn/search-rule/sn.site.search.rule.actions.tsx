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
    TurSNSiteSearchRuleAction,
    TurSNSiteSearchRuleActionType,
    TurSNSiteSearchRuleFieldOption,
} from "@/models/sn/sn-site-search-rule.model"
import type { TurSNSiteCustomSort } from "@/models/sn/sn-site-custom-sort.model"
import type { TurSNSiteFacetOrdering } from "@/models/sn/sn-site-facet-ordering.model"
import { SortValueSelector } from "./sn.site.search.rule.conditions"
import { IconBolt, IconCirclePlus, IconTrash } from "@tabler/icons-react"
import React, { useMemo } from "react"
import { useTranslation } from "react-i18next"
import type { UseFormReturn } from "react-hook-form"
import { SNSiteSearchRuleSection, type SNSectionChrome } from "./sn.site.search.rule.section"

const ACTION_TYPES: TurSNSiteSearchRuleActionType[] = [
    "SET_SORT", "ADD_FACETS", "REMOVE_FACETS",
    "SET_ROWS", "ADD_FILTER_QUERY", "SET_BOOST_QUERY"
];
const SINGLE_USE_ACTION_TYPES = new Set<TurSNSiteSearchRuleActionType>(["SET_SORT", "SET_ROWS"]);

// ── Facet Selector ──────────────────────────────────────────────────
// Shows all available facets (field + custom) as a checkbox list.
// Value is a comma-separated string of facet names.

function FacetSelector({ value, onChange, items, emptyMessage }: {
    value: string;
    onChange: (val: string) => void;
    items: { id: string; name: string; label?: string; badge?: string }[];
    emptyMessage: string;
}) {
    const selectedNames = useMemo(() => new Set(
        value.split(",").map(s => s.trim()).filter(Boolean)
    ), [value]);

    function toggle(name: string) {
        const updated = new Set(selectedNames);
        if (updated.has(name)) {
            updated.delete(name);
        } else {
            updated.add(name);
        }
        onChange(Array.from(updated).join(","));
    }

    if (items.length === 0) {
        return <span className="text-sm text-muted-foreground">{emptyMessage}</span>;
    }

    return (
        <div className="flex flex-wrap gap-1.5">
            {items.map((item) => {
                const selected = selectedNames.has(item.name);
                return (
                    <button
                        key={item.id}
                        type="button"
                        onClick={() => toggle(item.name)}
                        className={`
                            inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium
                            transition-all duration-150 cursor-pointer border
                            ${selected
                                ? "bg-linear-to-r from-blue-600 to-indigo-600 text-white border-transparent shadow-sm"
                                : "bg-transparent text-muted-foreground border-border hover:border-foreground/30 hover:text-foreground"
                            }
                        `}
                    >
                        {item.label || item.name}
                        {item.badge && (
                            <span className={`text-[9px] px-1 rounded ${selected ? "bg-white/20" : "bg-muted"}`}>
                                {item.badge}
                            </span>
                        )}
                    </button>
                );
            })}
        </div>
    );
}

// ── Boost Query Editor ──────────────────────────────────────────────

function BoostQueryEditor({ value, onChange, fieldOptions, t }: {
    value: string;
    onChange: (val: string) => void;
    fieldOptions: TurSNSiteSearchRuleFieldOption[];
    t: (key: string) => string;
}) {
    const match = value.match(/^([^:]*):([^^]*)(?:\^(.*))?$/);
    const field = match ? match[1] : "";
    const queryVal = match ? match[2] : value;
    const weight = match?.[3] ?? "1.0";

    function buildValue(f: string, v: string, w: string) {
        onChange(`${f}:${v}^${w}`);
    }

    return (
        <div className="flex items-center gap-2">
            <Select value={field} onValueChange={(f) => buildValue(f, queryVal, weight)}>
                <SelectTrigger className="w-40">
                    <SelectValue placeholder={t("sn.searchRule.field")} />
                </SelectTrigger>
                <SelectContent>
                    {fieldOptions.map((opt) => (
                        <SelectItem key={opt.id} value={opt.name}>{opt.name}</SelectItem>
                    ))}
                </SelectContent>
            </Select>
            <Input
                value={queryVal}
                onChange={(e) => buildValue(field, e.target.value, weight)}
                placeholder={t("sn.searchRule.value")}
                className="flex-1"
            />
            <Input
                value={weight}
                onChange={(e) => {
                    const w = e.target.value.replace(/[^0-9.]/g, "");
                    buildValue(field, queryVal, w);
                }}
                placeholder={t("sn.searchRule.boostWeight")}
                className="w-24"
                type="text"
                inputMode="decimal"
            />
        </div>
    );
}

// ── Main Actions Component ──────────────────────────────────────────

interface Props {
    form: UseFormReturn<TurSNSiteSearchRule>;
    fieldOptions: TurSNSiteSearchRuleFieldOption[];
    customSorts: TurSNSiteCustomSort[];
    facets: TurSNSiteFacetOrdering[];
    chrome?: SNSectionChrome;
}

export const SNSiteSearchRuleActions: React.FC<Props> = ({ form, fieldOptions, customSorts, facets, chrome = "console" }) => {
    const { t } = useTranslation();
    const actions = form.watch("actions") || [];

    const nonFacetFields = useMemo(() =>
        fieldOptions.filter(f => !f.facet),
        [fieldOptions]
    );

    const usedSingleUseActionTypes = useMemo(() => {
        const used = new Set<TurSNSiteSearchRuleActionType>();
        for (const a of actions) {
            if (SINGLE_USE_ACTION_TYPES.has(a.actionType)) {
                used.add(a.actionType);
            }
        }
        return used;
    }, [actions]);

    function getDefaultValue(actionType: TurSNSiteSearchRuleActionType): string {
        if (actionType === "SET_SORT") return "relevance";
        if (actionType === "SET_BOOST_QUERY" && fieldOptions.length > 0) return `${fieldOptions[0].name}:^1.0`;
        return "";
    }

    function addAction() {
        const current = form.getValues("actions") || [];
        const firstAvailable = ACTION_TYPES.find(at => !usedSingleUseActionTypes.has(at)) || ACTION_TYPES[0];
        form.setValue("actions", [...current, {
            actionType: firstAvailable,
            value: getDefaultValue(firstAvailable),
        }]);
    }

    function removeAction(index: number) {
        const current = form.getValues("actions") || [];
        form.setValue("actions", current.filter((_, i) => i !== index));
    }

    function updateActionField<K extends keyof TurSNSiteSearchRuleAction>(
        index: number, field: K, val: TurSNSiteSearchRuleAction[K]
    ) {
        const current = [...(form.getValues("actions") || [])];
        current[index] = { ...current[index], [field]: val };
        form.setValue("actions", current);
    }

    function renderActionValue(action: TurSNSiteSearchRuleAction, index: number) {
        switch (action.actionType) {
            case "SET_SORT":
                return (
                    <SortValueSelector
                        value={action.value || "relevance"}
                        onChange={(val) => updateActionField(index, "value", val)}
                        fieldOptions={fieldOptions} customSorts={customSorts} t={t}
                    />
                );
            case "SET_ROWS":
                return (
                    <Input
                        value={action.value}
                        onChange={(e) => {
                            const v = e.target.value.replace(/\D/g, "");
                            updateActionField(index, "value", v);
                        }}
                        placeholder="e.g. 20"
                        type="text"
                        inputMode="numeric"
                    />
                );
            case "ADD_FACETS":
                return (
                    <FacetSelector
                        value={action.value || ""}
                        onChange={(val) => updateActionField(index, "value", val)}
                        items={nonFacetFields.map(f => ({ id: f.id, name: f.name, label: f.facetName || f.name }))}
                        emptyMessage={t("sn.searchRule.noNonFacetFields")}
                    />
                );
            case "REMOVE_FACETS":
                return (
                    <FacetSelector
                        value={action.value || ""}
                        onChange={(val) => updateActionField(index, "value", val)}
                        items={facets.map(f => ({
                            id: f.id, name: f.name,
                            label: f.facetName || f.name,
                            badge: f.customFacet ? "CF" : undefined,
                        }))}
                        emptyMessage={t("sn.searchRule.noFacets")}
                    />
                );
            case "SET_BOOST_QUERY":
                return (
                    <BoostQueryEditor
                        value={action.value}
                        onChange={(val) => updateActionField(index, "value", val)}
                        fieldOptions={fieldOptions} t={t}
                    />
                );
            default:
                return (
                    <Input
                        value={action.value}
                        onChange={(e) => updateActionField(index, "value", e.target.value)}
                        placeholder={getActionPlaceholder(action.actionType)}
                    />
                );
        }
    }

    return (
        <SNSiteSearchRuleSection chrome={chrome} icon={IconBolt} tone="violet" title={t("sn.searchRule.actions")} description={t("sn.searchRule.actionsDesc")}>
                <div className="flex items-center justify-between mb-4">
                    <GradientButton variant="outline" type="button" onClick={addAction}>
                        <IconCirclePlus className="h-4 w-4 mr-2" />
                        {t("sn.searchRule.addAction")}
                    </GradientButton>
                </div>
                {actions.length > 0 && (
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead className="w-56">{t("sn.searchRule.actionType")}</TableHead>
                                <TableHead>{t("sn.searchRule.value")}</TableHead>
                                <TableHead className="w-16 text-right">{t("forms.common.actions")}</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {actions.map((action, index) => (
                                <TableRow key={action.id ?? `act-${index}`}>
                                    <TableCell>
                                        <Select
                                            value={action.actionType}
                                            onValueChange={(val) => {
                                                const at = val as TurSNSiteSearchRuleActionType;
                                                updateActionField(index, "actionType", at);
                                                updateActionField(index, "value", getDefaultValue(at));
                                            }}
                                        >
                                            <SelectTrigger><SelectValue /></SelectTrigger>
                                            <SelectContent>
                                                {ACTION_TYPES
                                                    .filter((at) => at === action.actionType || !usedSingleUseActionTypes.has(at))
                                                    .map((at) => (
                                                    <SelectItem key={at} value={at}>
                                                        {t(`sn.searchRule.actionTypeValues.${at}`)}
                                                    </SelectItem>
                                                ))}
                                            </SelectContent>
                                        </Select>
                                    </TableCell>
                                    <TableCell>
                                        {renderActionValue(action, index)}
                                    </TableCell>
                                    <TableCell className="text-right">
                                        <GradientButton variant="destructive" size="icon" type="button" onClick={() => removeAction(index)}>
                                            <IconTrash className="h-4 w-4" />
                                        </GradientButton>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                )}
        </SNSiteSearchRuleSection>
    );
};

function getActionPlaceholder(actionType: TurSNSiteSearchRuleActionType): string {
    switch (actionType) {
        case "SET_SORT": return "";
        case "ADD_FACETS":
        case "REMOVE_FACETS": return "";
        case "SET_ROWS": return "e.g. 20";
        case "ADD_FILTER_QUERY": return "e.g. type:article";
        case "SET_BOOST_QUERY": return "";
    }
}
