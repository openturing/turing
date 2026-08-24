"use client"
import { ROUTES } from "@/app/routes.const"
import { Form } from "@/components/ui/form"
import { BentoFormHero } from "@/components/bento"
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
import { IconGavel } from "@tabler/icons-react"
import { DialogDelete } from "@/components/dialog.delete"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
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
    onDelete?: () => void;
    open?: boolean;
    setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
    /** SN instance base route for save/cancel navigation. Defaults to the
     *  console; the Bento surface passes `ROUTES.BENTO_SN_INSTANCE` (T576). */
    baseRoute?: string;
}

export const SNSiteSearchRuleForm: React.FC<Props> = ({ snSiteId, value, isNew, onDelete, open, setOpen, baseRoute = ROUTES.SN_INSTANCE }) => {
    const { t } = useTranslation();
    const form = useForm<TurSNSiteSearchRule>({ defaultValues: value });
    const navigate = useNavigate();
    const urlBase = `${baseRoute}/${snSiteId}/search-rule`;
    const [fieldOptions, setFieldOptions] = useState<TurSNSiteSearchRuleFieldOption[]>([]);
    const [customSorts, setCustomSorts] = useState<TurSNSiteCustomSort[]>([]);
    const [siteLocales, setSiteLocales] = useState<TurSNSiteLocale[]>([]);
    const [facets, setFacets] = useState<TurSNSiteFacetOrdering[]>([]);

    useEffect(() => {
        form.reset(value);
    }, [form, value]);

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

    // Destructive action — in bento it lives in the hero `trailing` (never in
    // the save bar, which would mount the controlled dialog twice).
    const deleteDialog = onDelete && open !== undefined && setOpen && <DialogDelete feature={t("sn.searchRule.title")} name={value?.name || t("sn.searchRule.newSearchRule")} onDelete={onDelete} open={open} setOpen={setOpen} />;

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-5 pb-8">
                {/* T738: one BentoFormHero owns both halves of the morph.
                    `saveDisabled={false}` keeps Save always-enabled (this
                    form doesn't track dirty state). */}
                  <BentoFormHero
                      backTo={urlBase}
                      backLabel={t("sn.searchRule.title")}
                      leading={
                          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
                              <IconGavel size={24} />
                          </span>
                      }
                      title={value?.name || t("sn.searchRule.newSearchRule")}
                      subtitle={t("sn.searchRule.description")}
                      trailing={deleteDialog}
                      onCancel={() => navigate(urlBase)}
                      saveDisabled={false}
                  />
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
            </form>
        </Form>
    );
};
