"use client"
import { ROUTES } from "@/app/routes.const"
import {
    Form,
    FormControl,
    FormDescription,
    FormField,
    FormItem,
    FormLabel,
    FormMessage,
} from "@/components/ui/form"
import { FormActions } from "@/components/ui/form-actions"
import { GradientButton } from "@/components/ui/gradient-button"
import { GradientSwitch } from "@/components/ui/gradient-switch"
import { Input } from "@/components/ui/input"
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select"
import { SectionCard } from "@/components/ui/section-card"
import { SmartDescription } from "@/components/ui/smart-description"
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
    TurSNSiteSearchRuleAction,
    TurSNSiteSearchRuleParameter,
    TurSNSiteSearchRuleOperator,
    TurSNSiteSearchRuleActionType,
    TurSNSiteSearchRuleLogicOperator,
    TurSNSiteSearchRuleFieldOption,
} from "@/models/sn/sn-site-search-rule.model"
import { TurSNSiteSearchRuleService } from "@/services/sn/sn.site.search.rule.service"
import { IconGavel, IconFilter, IconBolt } from "@tabler/icons-react"
import { PlusCircle, Trash2 } from "lucide-react"
import React, { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"

const turSNSiteSearchRuleService = new TurSNSiteSearchRuleService();

const PARAMETERS: TurSNSiteSearchRuleParameter[] = ["QUERY", "FILTER_QUERY", "SORT", "LOCALE"];
const OPERATORS: TurSNSiteSearchRuleOperator[] = ["EQUALS", "CONTAINS", "STARTS_WITH", "MATCHES_ANY", "IS_EMPTY"];
const ACTION_TYPES: TurSNSiteSearchRuleActionType[] = ["SET_SORT", "SET_FACETS", "SET_ROWS", "ADD_FILTER_QUERY", "SET_BOOST_QUERY"];
const LOGIC_OPERATORS: TurSNSiteSearchRuleLogicOperator[] = ["AND", "OR"];

interface Props {
    snSiteId: string;
    value: TurSNSiteSearchRule;
    isNew: boolean;
}

export const SNSiteSearchRuleForm: React.FC<Props> = ({ snSiteId, value, isNew }) => {
    const { t } = useTranslation();
    const form = useForm<TurSNSiteSearchRule>({
        defaultValues: value,
    });
    const navigate = useNavigate();
    const urlBase = `${ROUTES.SN_INSTANCE}/${snSiteId}/search-rule`;
    const [fieldOptions, setFieldOptions] = useState<TurSNSiteSearchRuleFieldOption[]>([]);

    useEffect(() => {
        form.reset(value);
    }, [value]);

    useEffect(() => {
        turSNSiteSearchRuleService.getFieldOptions(snSiteId).then(setFieldOptions).catch(console.error);
    }, [snSiteId]);

    const conditions = form.watch("conditions") || [];
    const actions = form.watch("actions") || [];

    function addCondition() {
        const current = form.getValues("conditions") || [];
        const newCondition: TurSNSiteSearchRuleCondition = {
            parameter: "QUERY",
            operator: "EQUALS",
            value: "",
            logicOperator: "AND",
        };
        form.setValue("conditions", [...current, newCondition]);
    }

    function removeCondition(index: number) {
        const current = form.getValues("conditions") || [];
        form.setValue("conditions", current.filter((_, i) => i !== index));
    }

    function updateConditionField<K extends keyof TurSNSiteSearchRuleCondition>(
        index: number, field: K, val: TurSNSiteSearchRuleCondition[K]
    ) {
        const current = [...(form.getValues("conditions") || [])];
        current[index] = { ...current[index], [field]: val };
        if (field === "parameter" && val !== "FILTER_QUERY") {
            current[index].fieldName = undefined;
        }
        form.setValue("conditions", current);
    }

    function addAction() {
        const current = form.getValues("actions") || [];
        const newAction: TurSNSiteSearchRuleAction = {
            actionType: "SET_SORT",
            value: "",
        };
        form.setValue("actions", [...current, newAction]);
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

    function isFirstOfParameter(index: number): boolean {
        const param = conditions[index]?.parameter;
        for (let i = 0; i < index; i++) {
            if (conditions[i]?.parameter === param) return false;
        }
        return true;
    }

    async function onSubmit(data: TurSNSiteSearchRule) {
        try {
            if (isNew) {
                const result = await turSNSiteSearchRuleService.create(snSiteId, data);
                if (result) {
                    toast.success(t("sn.searchRule.saved"));
                    navigate(urlBase);
                } else {
                    toast.error(t("sn.searchRule.notSaved"));
                }
            } else {
                const result = await turSNSiteSearchRuleService.update(snSiteId, data);
                if (result) {
                    toast.success(t("sn.searchRule.updated"));
                } else {
                    toast.error(t("sn.searchRule.notUpdated"));
                }
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error(t("forms.common.formSubmitFailed"));
        }
    }

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-8 px-6">
                {/* Details Section */}
                <SectionCard variant="blue">
                    <SectionCard.Header icon={IconGavel} title={t("sn.searchRule.details")} description={t("sn.searchRule.detailsDesc")} />
                    <SectionCard.Content>
                        <FormField
                            control={form.control}
                            name="name"
                            rules={{ required: t("sn.searchRule.nameRequired") }}
                            render={({ field }) => (
                                <FormItem>
                                    <FormLabel>{t("forms.common.name")}</FormLabel>
                                    <FormDescription>
                                        {t("sn.searchRule.nameDesc")}
                                    </FormDescription>
                                    <FormControl>
                                        <Input {...field} placeholder="e.g. browse_education" type="text" />
                                    </FormControl>
                                    <FormMessage />
                                </FormItem>
                            )}
                        />
                        <FormField
                            control={form.control}
                            name="description"
                            render={({ field }) => (
                                <FormItem>
                                    <FormControl>
                                        <SmartDescription
                                            value={field.value || ""}
                                            onChange={field.onChange}
                                            placeholder={t("sn.searchRule.descriptionPlaceholder")}
                                            maxLength={500}
                                            entityType="Search Rule"
                                        >
                                            <SmartDescription.Label>{t("forms.common.description")}</SmartDescription.Label>
                                            <SmartDescription.Description>
                                                {t("sn.searchRule.descriptionDesc")}
                                            </SmartDescription.Description>
                                        </SmartDescription>
                                    </FormControl>
                                    <FormMessage />
                                </FormItem>
                            )}
                        />
                        <div className="grid grid-cols-2 gap-4">
                            <FormField
                                control={form.control}
                                name="position"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{t("sn.searchRule.position")}</FormLabel>
                                        <FormDescription>
                                            {t("sn.searchRule.positionDesc")}
                                        </FormDescription>
                                        <FormControl>
                                            <Input {...field} type="number" min={0}
                                                onChange={(e) => field.onChange(Number.parseInt(e.target.value) || 0)} />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                            <FormField
                                control={form.control}
                                name="enabled"
                                render={({ field }) => (
                                    <FormItem className="flex flex-col">
                                        <FormLabel>{t("sn.searchRule.enabled")}</FormLabel>
                                        <FormDescription>
                                            {t("sn.searchRule.enabledDesc")}
                                        </FormDescription>
                                        <FormControl>
                                            <GradientSwitch
                                                checked={field.value}
                                                onCheckedChange={field.onChange}
                                            />
                                        </FormControl>
                                    </FormItem>
                                )}
                            />
                        </div>
                    </SectionCard.Content>
                </SectionCard>

                {/* Conditions Section */}
                <SectionCard variant="amber">
                    <SectionCard.Header icon={IconFilter} title={t("sn.searchRule.conditions")} description={t("sn.searchRule.conditionsDesc")} />
                    <SectionCard.Content>
                        <div className="flex items-center justify-between mb-4">
                            <GradientButton variant="outline" type="button" onClick={addCondition}>
                                <PlusCircle className="h-4 w-4 mr-2" />
                                {t("sn.searchRule.addCondition")}
                            </GradientButton>
                        </div>
                        {conditions.length > 0 && (
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead className="w-20"></TableHead>
                                        <TableHead>{t("sn.searchRule.parameter")}</TableHead>
                                        <TableHead>{t("sn.searchRule.field")}</TableHead>
                                        <TableHead>{t("sn.searchRule.operator")}</TableHead>
                                        <TableHead>{t("sn.searchRule.value")}</TableHead>
                                        <TableHead className="w-16 text-right">{t("forms.common.actions")}</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {conditions.map((condition, index) => (
                                        <TableRow key={condition.id ?? `cond-${index}`}>
                                            <TableCell>
                                                {!isFirstOfParameter(index) ? (
                                                    <Select
                                                        value={condition.logicOperator || "AND"}
                                                        onValueChange={(val) => updateConditionField(index, "logicOperator", val as TurSNSiteSearchRuleLogicOperator)}
                                                    >
                                                        <SelectTrigger className="w-20">
                                                            <SelectValue />
                                                        </SelectTrigger>
                                                        <SelectContent>
                                                            {LOGIC_OPERATORS.map((lo) => (
                                                                <SelectItem key={lo} value={lo}>
                                                                    {t(`sn.searchRule.logicOperatorValues.${lo}`)}
                                                                </SelectItem>
                                                            ))}
                                                        </SelectContent>
                                                    </Select>
                                                ) : (
                                                    <Badge variant="outline" className="text-xs">
                                                        {t(`sn.searchRule.parameterValues.${condition.parameter}`)}
                                                    </Badge>
                                                )}
                                            </TableCell>
                                            <TableCell>
                                                <Select
                                                    value={condition.parameter}
                                                    onValueChange={(val) => updateConditionField(index, "parameter", val as TurSNSiteSearchRuleParameter)}
                                                >
                                                    <SelectTrigger>
                                                        <SelectValue />
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        {PARAMETERS.map((p) => (
                                                            <SelectItem key={p} value={p}>
                                                                {t(`sn.searchRule.parameterValues.${p}`)}
                                                            </SelectItem>
                                                        ))}
                                                    </SelectContent>
                                                </Select>
                                            </TableCell>
                                            <TableCell>
                                                {condition.parameter === "FILTER_QUERY" ? (
                                                    <Select
                                                        value={condition.fieldName || ""}
                                                        onValueChange={(val) => updateConditionField(index, "fieldName", val)}
                                                    >
                                                        <SelectTrigger>
                                                            <SelectValue placeholder={t("sn.searchRule.field")} />
                                                        </SelectTrigger>
                                                        <SelectContent>
                                                            {fieldOptions.map((opt) => (
                                                                <SelectItem key={opt.id} value={opt.name}>
                                                                    {opt.name}
                                                                </SelectItem>
                                                            ))}
                                                        </SelectContent>
                                                    </Select>
                                                ) : (
                                                    <span className="text-muted-foreground text-sm">-</span>
                                                )}
                                            </TableCell>
                                            <TableCell>
                                                <Select
                                                    value={condition.operator}
                                                    onValueChange={(val) => updateConditionField(index, "operator", val as TurSNSiteSearchRuleOperator)}
                                                >
                                                    <SelectTrigger>
                                                        <SelectValue />
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        {OPERATORS.map((o) => (
                                                            <SelectItem key={o} value={o}>
                                                                {t(`sn.searchRule.operatorValues.${o}`)}
                                                            </SelectItem>
                                                        ))}
                                                    </SelectContent>
                                                </Select>
                                            </TableCell>
                                            <TableCell>
                                                <Input
                                                    value={condition.value || ""}
                                                    onChange={(e) => updateConditionField(index, "value", e.target.value)}
                                                    placeholder={condition.operator === "IS_EMPTY" ? "" : "e.g. education"}
                                                    disabled={condition.operator === "IS_EMPTY"}
                                                />
                                            </TableCell>
                                            <TableCell className="text-right">
                                                <GradientButton
                                                    variant="destructive"
                                                    size="icon"
                                                    type="button"
                                                    onClick={() => removeCondition(index)}
                                                >
                                                    <Trash2 className="h-4 w-4" />
                                                </GradientButton>
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        )}
                    </SectionCard.Content>
                </SectionCard>

                {/* Actions Section */}
                <SectionCard variant="violet">
                    <SectionCard.Header icon={IconBolt} title={t("sn.searchRule.actions")} description={t("sn.searchRule.actionsDesc")} />
                    <SectionCard.Content>
                        <div className="flex items-center justify-between mb-4">
                            <GradientButton variant="outline" type="button" onClick={addAction}>
                                <PlusCircle className="h-4 w-4 mr-2" />
                                {t("sn.searchRule.addAction")}
                            </GradientButton>
                        </div>
                        {actions.length > 0 && (
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead>{t("sn.searchRule.actionType")}</TableHead>
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
                                                    onValueChange={(val) => updateActionField(index, "actionType", val as TurSNSiteSearchRuleActionType)}
                                                >
                                                    <SelectTrigger>
                                                        <SelectValue />
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        {ACTION_TYPES.map((at) => (
                                                            <SelectItem key={at} value={at}>
                                                                {t(`sn.searchRule.actionTypeValues.${at}`)}
                                                            </SelectItem>
                                                        ))}
                                                    </SelectContent>
                                                </Select>
                                            </TableCell>
                                            <TableCell>
                                                <Input
                                                    value={action.value}
                                                    onChange={(e) => updateActionField(index, "value", e.target.value)}
                                                    placeholder={getActionPlaceholder(action.actionType, t)}
                                                />
                                            </TableCell>
                                            <TableCell className="text-right">
                                                <GradientButton
                                                    variant="destructive"
                                                    size="icon"
                                                    type="button"
                                                    onClick={() => removeAction(index)}
                                                >
                                                    <Trash2 className="h-4 w-4" />
                                                </GradientButton>
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        )}
                    </SectionCard.Content>
                </SectionCard>

                {/* Action Footer */}
                <FormActions>
                    <FormActions.Cancel onClick={() => navigate(urlBase)} />
                    <FormActions.Submit />
                </FormActions>
            </form>
        </Form>
    );
};

function getActionPlaceholder(actionType: TurSNSiteSearchRuleActionType, _t: (key: string) => string): string {
    switch (actionType) {
        case "SET_SORT": return "e.g. sort_date, newest";
        case "SET_FACETS": return "e.g. category,type,date";
        case "SET_ROWS": return "e.g. 20";
        case "ADD_FILTER_QUERY": return "e.g. type:article";
        case "SET_BOOST_QUERY": return "e.g. title:featured^2.0";
    }
}
