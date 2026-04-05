"use client"
import { ROUTES } from "@/app/routes.const"
import { Form } from "@/components/ui/form"
import { FormActions } from "@/components/ui/form-actions"
import type {
    TurSNSiteSearchRule,
    TurSNSiteSearchRuleFieldOption,
} from "@/models/sn/sn-site-search-rule.model"
import type { TurSNSiteCustomSort } from "@/models/sn/sn-site-custom-sort.model"
import type { TurSNSiteLocale } from "@/models/sn/sn-site-locale.model"
import type { TurSNSiteFacetOrdering } from "@/models/sn/sn-site-facet-ordering.model"
import { TurSNSiteSearchRuleService } from "@/services/sn/sn.site.search.rule.service"
import { TurSNSiteCustomSortService } from "@/services/sn/sn.site.custom.sort.service"
import { TurSNSiteLocaleService } from "@/services/sn/sn.site.locale.service"
import { TurSNFacetedFieldService } from "@/services/sn/sn.faceted.field.service"
import React, { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { SNSiteSearchRuleDetails } from "./sn.site.search.rule.details"
import { SNSiteSearchRuleConditions } from "./sn.site.search.rule.conditions"
import { SNSiteSearchRuleActions } from "./sn.site.search.rule.actions"

const turSNSiteSearchRuleService = new TurSNSiteSearchRuleService();
const turSNSiteCustomSortService = new TurSNSiteCustomSortService();
const turSNSiteLocaleService = new TurSNSiteLocaleService();
const turSNFacetedFieldService = new TurSNFacetedFieldService();

interface Props {
    snSiteId: string;
    value: TurSNSiteSearchRule;
    isNew: boolean;
}

export const SNSiteSearchRuleForm: React.FC<Props> = ({ snSiteId, value, isNew }) => {
    const { t } = useTranslation();
    const form = useForm<TurSNSiteSearchRule>({ defaultValues: value });
    const navigate = useNavigate();
    const urlBase = `${ROUTES.SN_INSTANCE}/${snSiteId}/search-rule`;
    const [fieldOptions, setFieldOptions] = useState<TurSNSiteSearchRuleFieldOption[]>([]);
    const [customSorts, setCustomSorts] = useState<TurSNSiteCustomSort[]>([]);
    const [siteLocales, setSiteLocales] = useState<TurSNSiteLocale[]>([]);
    const [facets, setFacets] = useState<TurSNSiteFacetOrdering[]>([]);

    useEffect(() => {
        form.reset(value);
    }, [value]);

    useEffect(() => {
        turSNSiteSearchRuleService.getFieldOptions(snSiteId).then(setFieldOptions).catch(console.error);
        turSNSiteCustomSortService.query(snSiteId).then(setCustomSorts).catch(console.error);
        turSNSiteLocaleService.query(snSiteId).then(setSiteLocales).catch(console.error);
        turSNFacetedFieldService.query(snSiteId).then(setFacets).catch(console.error);
    }, [snSiteId]);

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
                <SNSiteSearchRuleDetails form={form} />
                <SNSiteSearchRuleConditions
                    form={form}
                    fieldOptions={fieldOptions}
                    customSorts={customSorts}
                    siteLocales={siteLocales}
                />
                <SNSiteSearchRuleActions
                    form={form}
                    fieldOptions={fieldOptions}
                    customSorts={customSorts}
                    facets={facets}
                />
                <FormActions>
                    <FormActions.Cancel onClick={() => navigate(urlBase)} />
                    <FormActions.Submit />
                </FormActions>
            </form>
        </Form>
    );
};
